package com.bame.client.account;

import com.bame.client.mixin.MinecraftClientSessionAccessor;
import com.bame.client.mixin.RealmsClientAccessor;
import com.bame.client.mixin.SplashTextResourceSupplierAccessor;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.SocialInteractionsManager;
import net.minecraft.client.realms.RealmsClient;
import net.minecraft.client.realms.RealmsPeriodicCheckers;
import net.minecraft.client.session.ProfileKeys;
import net.minecraft.client.session.Session;
import net.minecraft.client.session.report.AbuseReportContext;
import net.minecraft.client.session.report.ReporterEnvironment;
import net.minecraft.client.session.telemetry.TelemetryManager;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Tauscht die Minecraft-Session im laufenden Spiel aus.
 * <p>
 * Ablauf: Alles, was Netzwerk braucht (UserApiService, Nutzer-Eigenschaften, eigenes Profil), wird im Hintergrund
 * vorbereitet. Danach werden auf dem Haupt-Thread die übrigen Objekte erzeugt; erst wenn alles fehlerfrei erstellt
 * wurde, werden die Felder in {@link MinecraftClient} auf einen Schlag ersetzt. Schlägt vorher etwas fehl, bleibt die
 * alte Session vollständig erhalten.
 */
public final class SessionSwitcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserSessionSwitcher");

    private static final String MSG_IN_WORLD = "Account-Wechsel geht nur im Menü, nicht während du in einer Welt bist.";
    private static final String MSG_BUSY = "Ein Account-Wechsel läuft bereits.";
    private static final String MSG_FAILED = "Account-Wechsel fehlgeschlagen. Du bleibst mit dem bisherigen Account angemeldet.";

    private static final AtomicBoolean SWITCHING = new AtomicBoolean(false);

    private SessionSwitcher() {}

    /** Alles, was im Hintergrund vorbereitet wird. */
    private record Prepared(Session session, UserApiService userApiService,
                            UserApiService.UserProperties userProperties, ProfileResult profileResult) {}

    public static boolean isSwitching() {
        return SWITCHING.get();
    }

    /** Muss auf dem Haupt-Thread aufgerufen werden. Das Ergebnis wird nur im Speicher gehalten. */
    static void switchSession(LauncherBridge.SwitchResult result, Consumer<LauncherBridge.SwitchSuccess> onSuccess, Consumer<String> onError) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            onError.accept(MSG_IN_WORLD);
            return;
        }
        if (!SWITCHING.compareAndSet(false, true)) {
            onError.accept(MSG_BUSY);
            return;
        }

        final Session session;
        try {
            UUID uuid = LauncherBridge.parseUuid(result.id);
            session = new Session(result.name, uuid, result.accessToken,
                    Optional.ofNullable(result.xuid), Optional.ofNullable(result.clientId));
        } catch (Throwable t) {
            SWITCHING.set(false);
            onError.accept(MSG_FAILED);
            return;
        }

        final boolean offline = client.isOfflineDeveloperMode();
        CompletableFuture.supplyAsync(() -> prepare(client, session, offline), Util.getDownloadWorkerExecutor())
                .whenComplete((prepared, err) -> client.execute(() -> {
                    try {
                        if (err != null || prepared == null) {
                            LOGGER.warn("Account switch preparation failed: {}", err != null ? err.getClass().getSimpleName() : "null");
                            onError.accept(MSG_FAILED);
                            return;
                        }
                        String error = apply(client, prepared, offline);
                        if (error != null) {
                            onError.accept(error);
                            return;
                        }
                        onAccountChanged();
                        applyLauncherSkinSync(result);
                        onSuccess.accept(new LauncherBridge.SwitchSuccess(prepared.session().getUsername(),
                                result.skinSyncStatus, result.skinSyncError));
                    } finally {
                        SWITCHING.set(false);
                    }
                }));
    }

    /**
     * Übernimmt das Skin-Sync-Ergebnis des Launchers, nachdem {@link #onAccountChanged()} den Upload-Status
     * zurückgesetzt hat. Bei {@code uploaded}/{@code unchanged}/{@code pending} hat der Launcher den Skin bereits
     * übertragen (bzw. überträgt ihn gerade); bei {@code failed}/{@code skipped} oder ohne {@code skinSync} lädt der
     * Mod wie bisher beim Server-Beitritt selbst hoch.
     */
    private static void applyLauncherSkinSync(LauncherBridge.SwitchResult result) {
        String status = result.skinSyncStatus;
        if (result.skinSyncSkinId == null) return;
        if (!LauncherBridge.SKIN_SYNC_UPLOADED.equals(status)
                && !LauncherBridge.SKIN_SYNC_UNCHANGED.equals(status)
                && !LauncherBridge.SKIN_SYNC_PENDING.equals(status)) {
            return;
        }
        try {
            com.bame.client.cosmetics.CaeserSkinManager.markUploadedByLauncher(result.skinSyncSkinId);
        } catch (Throwable t) {
            LOGGER.warn("Failed to apply launcher skin sync after account switch ({})", t.getClass().getSimpleName());
        }
    }

    /**
     * Läuft im Hintergrund. Entspricht dem, was der {@link MinecraftClient}-Konstruktor (1.21.11) für die Session tut:
     * <ul>
     *   <li>{@code new YggdrasilAuthenticationService(networkProxy)} bzw. offline {@code UserApiService.OFFLINE}</li>
     *   <li>{@code createUserApiService(authService, runArgs)} → {@code authService.createUserApiService(accessToken)}</li>
     *   <li>{@code userPropertiesFuture} → {@code userApiService.fetchProperties()}, bei Fehler {@code OFFLINE_PROPERTIES}</li>
     *   <li>{@code gameProfileFuture} → {@code apiServices.sessionService().fetchProfile(uuid, true)}</li>
     * </ul>
     */
    private static Prepared prepare(MinecraftClient client, Session session, boolean offline) {
        UserApiService userApi;
        if (offline) {
            userApi = UserApiService.OFFLINE;
        } else {
            YggdrasilAuthenticationService authService = new YggdrasilAuthenticationService(client.getNetworkProxy());
            userApi = authService.createUserApiService(session.getAccessToken());
        }

        UserApiService.UserProperties properties;
        try {
            properties = userApi.fetchProperties();
        } catch (AuthenticationException e) {
            LOGGER.warn("Failed to fetch user properties for new session ({})", e.getClass().getSimpleName());
            properties = UserApiService.OFFLINE_PROPERTIES;
        }

        ProfileResult profile = null;
        if (!offline) {
            try {
                profile = client.getApiServices().sessionService().fetchProfile(session.getUuidOrNull(), true);
            } catch (Throwable t) {
                LOGGER.warn("Failed to fetch game profile for new session ({})", t.getClass().getSimpleName());
            }
            // Skin des neuen Accounts schon vorladen, damit Kopf/Skin nach dem Wechsel sofort stimmen.
            if (profile != null && profile.profile() != null) {
                try {
                    client.getSkinProvider().fetchSkinTextures(profile.profile()).get(10, TimeUnit.SECONDS);
                } catch (Throwable ignored) {}
            }
        }
        return new Prepared(session, userApi, properties, profile);
    }

    /**
     * Läuft auf dem Haupt-Thread. Erzeugt zuerst alle restlichen Objekte (wie im Konstruktor) und setzt erst danach
     * alle Felder. Gibt bei Fehler eine Meldung zurück, sonst {@code null}.
     */
    private static String apply(MinecraftClient client, Prepared p, boolean offline) {
        if (client.world != null) {
            return MSG_IN_WORLD;
        }

        Session session = p.session();
        UserApiService userApi = p.userApiService();

        ProfileKeys profileKeys;
        SocialInteractionsManager social;
        AbuseReportContext abuseReportContext;
        TelemetryManager telemetry = null;
        RealmsClient realmsClient;
        RealmsPeriodicCheckers realmsCheckers;
        try {
            profileKeys = offline ? ProfileKeys.MISSING : ProfileKeys.create(userApi, session, client.runDirectory.toPath());
            social = new SocialInteractionsManager(client, userApi);
            abuseReportContext = AbuseReportContext.create(ReporterEnvironment.ofIntegratedServer(), userApi);
            telemetry = new TelemetryManager(client, userApi, session);
            realmsClient = RealmsClientAccessor.caeser$create(session.getSessionId(), session.getUsername(), client);
            realmsCheckers = new RealmsPeriodicCheckers(realmsClient);
        } catch (Throwable t) {
            LOGGER.warn("Failed to build session objects for account switch ({})", t.getClass().getSimpleName());
            if (telemetry != null) {
                try { telemetry.close(); } catch (Throwable ignored) {}
            }
            return MSG_FAILED;
        }

        TelemetryManager oldTelemetry = client.getTelemetryManager();

        // Ab hier nur noch Zuweisungen: die neue Session wird vollständig auf einmal aktiv.
        MinecraftClientSessionAccessor acc = (MinecraftClientSessionAccessor) client;
        acc.caeser$setSession(session);
        acc.caeser$setUserApiService(userApi);
        acc.caeser$setUserPropertiesFuture(CompletableFuture.completedFuture(p.userProperties()));
        acc.caeser$setGameProfileFuture(CompletableFuture.completedFuture(p.profileResult()));
        acc.caeser$setProfileKeys(profileKeys);
        acc.caeser$setSocialInteractionsManager(social);
        acc.caeser$setAbuseReportContext(abuseReportContext);
        acc.caeser$setTelemetryManager(telemetry);
        acc.caeser$setRealmsPeriodicCheckers(realmsCheckers);
        RealmsClientAccessor.caeser$setInstance(realmsClient);
        try {
            if (client.getSplashTextLoader() != null) {
                ((SplashTextResourceSupplierAccessor) client.getSplashTextLoader()).caeser$setSession(session);
            }
        } catch (Throwable ignored) {}

        if (oldTelemetry != null && oldTelemetry != telemetry) {
            try { oldTelemetry.close(); } catch (Throwable ignored) {}
        }

        LOGGER.info("Switched account to {}", session.getUsername());
        return null;
    }

    /**
     * Aktualisiert alle Mod-Teile, die Name oder UUID des lokalen Spielers verwenden.
     * Jeder Schritt ist einzeln abgesichert, damit ein Fehler die anderen nicht verhindert.
     */
    public static void onAccountChanged() {
        // Benutzerbezogene Cosmetics-Konfiguration (cosmetics_<name>.json) für den neuen Account laden
        try {
            com.bame.client.cosmetics.CosmeticsConfig.load();
        } catch (Throwable t) {
            LOGGER.warn("Failed to reload cosmetics config after account switch", t);
        }
        // Jeton-Shop: Sync-Verbindung der alten Identität sofort trennen, Wallet leeren (Cache des neuen Accounts
        // laden) und neu beim Caeser-Backend anmelden. Nach der Anmeldung folgen Wallet-Refresh und neues "hello".
        try {
            com.bame.client.shop.CaeserSyncClient.onAccountChanged();
        } catch (Throwable t) {
            LOGGER.warn("Failed to close cosmetic sync after account switch ({})", t.getClass().getSimpleName());
        }
        try {
            com.bame.client.shop.Wallet.onAccountChanged();
        } catch (Throwable t) {
            LOGGER.warn("Failed to reset wallet after account switch ({})", t.getClass().getSimpleName());
        }
        try {
            com.bame.client.shop.CaeserAuth.onAccountChanged();
        } catch (Throwable t) {
            LOGGER.warn("Failed to re-login to Caeser backend after account switch ({})", t.getClass().getSimpleName());
        }
        // Account-Skin (Cape/Elytra) in eigenen Skins und Upload-Status zurücksetzen
        try {
            com.bame.client.cosmetics.CaeserSkinManager.onAccountChanged();
        } catch (Throwable t) {
            LOGGER.warn("Failed to reset skin cache after account switch", t);
        }
        // Eigenes Caeser-Cape verwerfen; nach der neuen Anmeldung kommt es über /v1/me bzw. den Sync-State neu
        try {
            com.bame.client.cosmetics.CaeserCapeManager.onAccountChanged();
        } catch (Throwable t) {
            LOGGER.warn("Failed to reset Caeser cape after account switch ({})", t.getClass().getSimpleName());
        }
        // Köpfe neu laden (eigener Kopf gehört jetzt zu einem anderen Account)
        try {
            AccountHeads.clear();
        } catch (Throwable ignored) {}
        // Player-Sync: fremde Daten verwerfen, State unter der neuen Identität neu senden (LAN-Fallback sofort)
        try {
            com.bame.client.cosmetics.CaeserPlayerSyncManager.onAccountChanged();
        } catch (Throwable t) {
            LOGGER.warn("Failed to re-announce player sync after account switch", t);
        }
        try {
            MinecraftClient.getInstance().updateWindowTitle();
        } catch (Throwable ignored) {}
    }
}
