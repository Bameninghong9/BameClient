package com.bame.client.shop;

import com.google.gson.JsonObject;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.ForcedUsernameChangeException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserBannedException;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Anmeldung beim Caeser-Backend über das Mojang-Session-Verfahren:
 * {@code POST /v1/auth/challenge} → {@code joinServer} (authlib, beim Mojang-Session-Server) → {@code POST /v1/auth/verify}.
 * <p>
 * Das Minecraft-Access-Token geht nur an den Mojang-Session-Server (über authlib), nie an das Backend.
 * Das Backend-Session-Token liegt ausschließlich in diesem Objekt im Speicher und wird nie geloggt oder gespeichert.
 */
public final class CaeserAuth {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserAuth");

    public static final String MSG_NO_SESSION = "Keine gültige Minecraft-Sitzung (Offline-Modus).";
    private static final long FAILED_RETRY_MS = 30_000L;

    private static final Object LOCK = new Object();
    private static volatile String token;          // nur im Speicher
    private static volatile String tokenUuid;      // 32 Hex, zu welchem Account das Token gehört
    private static volatile boolean admin;
    private static volatile String lastError;
    private static volatile long lastFailureAt;
    private static CompletableFuture<String> inFlight; // höchstens eine Anmeldung gleichzeitig (LOCK)
    private static final AtomicInteger GENERATION = new AtomicInteger();

    private CaeserAuth() {}

    public static boolean isLoggedIn() {
        String t = token;
        return t != null && tokenUuid != null && tokenUuid.equals(currentSessionUuid32());
    }

    public static boolean isAdmin() {
        return isLoggedIn() && admin;
    }

    /** Letzter Anmeldefehler als deutscher Text (für Tooltip), sonst {@code null}. */
    public static String lastError() {
        return lastError;
    }

    /** Token für Bearer-Aufrufe, nur wenn es zum aktuellen Account gehört. Nicht loggen! */
    static String currentToken() {
        return isLoggedIn() ? token : null;
    }

    /** UUID der aktuellen Minecraft-Session (32 Hex) oder {@code null}. */
    public static String currentSessionUuid32() {
        try {
            Session s = MinecraftClient.getInstance().getSession();
            UUID u = s != null ? s.getUuidOrNull() : null;
            if (u == null) return null;
            // Wird pro Frame mehrfach gebraucht (Freischalt-Prüfungen): Umwandlung zwischenspeichern
            Object[] cached = uuidCache;
            if (cached[0] == u) return (String) cached[1];
            String hex = CaeserApi.uuid32(u);
            uuidCache = new Object[]{u, hex};
            return hex;
        } catch (Throwable t) {
            return null;
        }
    }

    private static volatile Object[] uuidCache = new Object[]{null, null};

    /**
     * Prüft, ob überhaupt angemeldet werden darf: Backend eingerichtet, kein Offline-Dev-Modus, echte (v4-)UUID und
     * vorhandenes Access-Token.
     */
    public static boolean canLogin() {
        if (!CaeserApi.isConfigured()) return false;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.isOfflineDeveloperMode()) return false;
            Session s = client.getSession();
            if (s == null) return false;
            UUID uuid = s.getUuidOrNull();
            if (uuid == null || uuid.version() != 4) return false;
            String access = s.getAccessToken();
            if (access == null || access.isBlank() || "0".equals(access) || "FabricMC".equals(access)) return false;
            String name = s.getUsername();
            return name != null && !name.isBlank();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Anmelden, falls nötig. Liefert das Token oder schlägt mit {@link CaeserApi.ApiException} fehl. */
    public static CompletableFuture<String> ensureLoggedIn() {
        String t = currentToken();
        if (t != null) return CompletableFuture.completedFuture(t);
        return login(false);
    }

    /**
     * Startet eine Anmeldung (höchstens eine gleichzeitig). Nach einem Fehlschlag wird 30 s lang nicht automatisch
     * erneut versucht, außer {@code force} ist gesetzt (Spielstart, Account-Wechsel).
     */
    public static CompletableFuture<String> login(boolean force) {
        synchronized (LOCK) {
            if (inFlight != null && !inFlight.isDone()) return inFlight;
            if (!canLogin()) {
                lastError = CaeserApi.isConfigured() ? MSG_NO_SESSION : null;
                return CompletableFuture.failedFuture(new CaeserApi.ApiException(0, "NO_SESSION",
                        CaeserApi.isConfigured() ? MSG_NO_SESSION : CaeserApi.MSG_UNREACHABLE, null));
            }
            if (!force && System.currentTimeMillis() - lastFailureAt < FAILED_RETRY_MS) {
                String msg = lastError != null ? lastError : CaeserApi.MSG_UNREACHABLE;
                return CompletableFuture.failedFuture(new CaeserApi.ApiException(0, "LOGIN_BACKOFF", msg, null));
            }
            int gen = GENERATION.get();
            inFlight = doLogin(gen);
            return inFlight;
        }
    }

    private static CompletableFuture<String> doLogin(int gen) {
        MinecraftClient client = MinecraftClient.getInstance();
        Session session = client.getSession();
        final UUID uuid = session.getUuidOrNull();
        final String uuid32 = CaeserApi.uuid32(uuid);
        final String name = session.getUsername();

        JsonObject challengeBody = new JsonObject();
        challengeBody.addProperty("name", name);

        return CaeserApi.post("/v1/auth/challenge", challengeBody, null)
                .thenApplyAsync(ch -> {
                    String challengeId = str(ch, "challengeId");
                    String serverId = str(ch, "serverId");
                    if (challengeId == null || serverId == null) {
                        throw new CaeserApi.ApiException(500, "BAD_RESPONSE", "Ungültige Antwort vom Caeser-Server.", null);
                    }
                    // Blockierender Aufruf beim Mojang-Session-Server, läuft im Hintergrund-Pool.
                    joinMojang(client, uuid, serverId);
                    return challengeId;
                }, CaeserApi.WORKER)
                .thenCompose(challengeId -> {
                    JsonObject verifyBody = new JsonObject();
                    verifyBody.addProperty("challengeId", challengeId);
                    return CaeserApi.post("/v1/auth/verify", verifyBody, null);
                })
                .handle((res, err) -> {
                    if (err != null) {
                        CaeserApi.ApiException api = CaeserApi.unwrap(err);
                        if (gen == GENERATION.get()) {
                            lastError = api.status == 401 ? "Anmeldung beim Caeser-Server fehlgeschlagen." : api.userMessage();
                            lastFailureAt = System.currentTimeMillis();
                        }
                        LOGGER.warn("Caeser login failed ({} {})", api.status, api.code);
                        throw api;
                    }
                    String newToken = str(res, "token");
                    String verifiedUuid = CaeserApi.uuid32(str(res, "uuid"));
                    if (newToken == null || newToken.isEmpty() || verifiedUuid == null || !verifiedUuid.equals(uuid32)) {
                        lastError = "Anmeldung beim Caeser-Server fehlgeschlagen.";
                        lastFailureAt = System.currentTimeMillis();
                        LOGGER.warn("Caeser login failed (unexpected verify response)");
                        throw new CaeserApi.ApiException(401, "AUTH_FAILED", lastError, null);
                    }
                    if (gen != GENERATION.get() || !uuid32.equals(currentSessionUuid32())) {
                        // Account wurde inzwischen gewechselt: Ergebnis verwerfen
                        throw new CaeserApi.ApiException(0, "STALE", CaeserApi.MSG_UNREACHABLE, null);
                    }
                    token = newToken;
                    tokenUuid = uuid32;
                    admin = res.has("isAdmin") && res.get("isAdmin").isJsonPrimitive() && res.get("isAdmin").getAsBoolean();
                    lastError = null;
                    lastFailureAt = 0L;
                    LOGGER.info("Logged in to Caeser backend as {}", name);
                    MinecraftClient.getInstance().execute(CaeserAuth::onLoggedIn);
                    return newToken;
                });
    }

    /** Ruft authlib-joinServer auf und übersetzt Fehler in deutsche Meldungen. Loggt nur den Fehlertyp. */
    private static void joinMojang(MinecraftClient client, UUID uuid, String serverId) {
        try {
            MinecraftSessionService service = client.getApiServices().sessionService();
            String accessToken = client.getSession().getAccessToken();
            service.joinServer(uuid, accessToken, serverId);
        } catch (AuthenticationUnavailableException e) {
            throw mojangError("MOJANG_UNAVAILABLE", "Mojang-Anmeldeserver gerade nicht erreichbar.", e);
        } catch (InvalidCredentialsException e) {
            throw mojangError("MOJANG_INVALID_SESSION", "Deine Minecraft-Sitzung ist abgelaufen. Bitte melde dich im Launcher neu an.", e);
        } catch (UserBannedException e) {
            throw mojangError("MOJANG_BANNED", "Dein Account ist für den Mehrspielermodus gesperrt.", e);
        } catch (ForcedUsernameChangeException e) {
            throw mojangError("MOJANG_NAME_CHANGE", "Du musst zuerst deinen Minecraft-Namen ändern.", e);
        } catch (InsufficientPrivilegesException e) {
            throw mojangError("MOJANG_NO_MULTIPLAYER", "Dein Account darf den Mehrspielermodus nicht nutzen.", e);
        } catch (AuthenticationException e) {
            throw mojangError("MOJANG_FAILED", "Anmeldung bei Mojang fehlgeschlagen.", e);
        } catch (RuntimeException e) {
            throw mojangError("MOJANG_FAILED", "Anmeldung bei Mojang fehlgeschlagen.", e);
        }
    }

    private static CaeserApi.ApiException mojangError(String code, String msg, Throwable cause) {
        // Nur der Typ wird geloggt; die Meldung könnte Kontodaten enthalten.
        LOGGER.warn("Mojang joinServer failed: {}", cause.getClass().getSimpleName());
        return new CaeserApi.ApiException(0, code, msg, null);
    }

    private static void onLoggedIn() {
        try {
            Wallet.refresh();
        } catch (Throwable t) {
            LOGGER.warn("Wallet refresh after login failed ({})", t.getClass().getSimpleName());
        }
        try {
            CaeserSyncClient.reconnect();
        } catch (Throwable t) {
            LOGGER.warn("Sync reconnect after login failed ({})", t.getClass().getSimpleName());
        }
    }

    /** Verwirft das Token (z. B. nach 401). */
    public static void invalidate() {
        synchronized (LOCK) {
            token = null;
            tokenUuid = null;
            admin = false;
        }
    }

    /** Nach einem Ingame-Account-Wechsel: altes Token verwerfen und mit dem neuen Account anmelden. */
    public static void onAccountChanged() {
        synchronized (LOCK) {
            GENERATION.incrementAndGet();
            token = null;
            tokenUuid = null;
            admin = false;
            lastError = null;
            lastFailureAt = 0L;
            inFlight = null;
        }
        login(true).exceptionally(t -> null);
    }

    /**
     * Führt eine Anfrage mit Bearer-Token aus. Bei {@code 401} wird genau einmal neu angemeldet und wiederholt.
     */
    public static CompletableFuture<JsonObject> authed(Function<String, CompletableFuture<JsonObject>> call) {
        return ensureLoggedIn().thenCompose(tok -> call.apply(tok)
                .handle((res, err) -> {
                    if (err == null) return CompletableFuture.completedFuture(res);
                    CaeserApi.ApiException api = CaeserApi.unwrap(err);
                    if (api.status != 401) {
                        return CompletableFuture.<JsonObject>failedFuture(api);
                    }
                    // Token abgelaufen bzw. ungültig: genau eine neue Anmeldung und ein Wiederholungsversuch
                    invalidate();
                    return login(true).thenCompose(call);
                })
                .thenCompose(f -> f));
    }

    private static String str(JsonObject o, String key) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) return null;
        return o.get(key).getAsString();
    }
}
