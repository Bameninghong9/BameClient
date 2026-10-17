package com.bame.client.skin;

import com.bame.client.account.LauncherBridge;
import com.bame.client.notification.Notification;
import com.bame.client.notification.NotificationManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * „Save skin“ im Spieler-Auswahlrad: liest auf dem Render-Thread nur die textures-Property des Spielers und erledigt
 * alles Weitere in einem eigenen Hintergrund-Thread (nichts blockiert das Spiel):
 * <ol>
 *     <li>Base64-JSON der textures-Property → Skin-Adresse und Modell (slim/classic),</li>
 *     <li>nur {@code https://textures.minecraft.net/texture/…} ({@link SkinUrls}),</li>
 *     <li>PNG laden (10 s Zeitlimit insgesamt, höchstens 1 MB) und prüfen ({@link SkinPng}: echtes PNG, 64x64/64x32),</li>
 *     <li>an den Launcher ({@link LauncherBridge#importSkin}), sonst lokal unter {@code config/bameclient/saved-skins}.</li>
 * </ol>
 * Meldungen über {@link NotificationManager} (auf dem Haupt-Thread). Keine Tokens in Logs oder Toasts.
 */
public final class SkinSaver {
    private SkinSaver() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserSkinSaver");
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(10);
    /** Schutz vor Doppelklicks (der Launcher erlaubt 10 Importe pro Minute, auch fehlerhafte zählen). */
    private static final long MIN_INTERVAL_MS = 1500L;
    private static final long TOAST_MS = 4500L;

    private static final Set<String> IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static volatile long lastStartMs = 0L;
    private static volatile HttpClient http;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CaeserClient-SkinSaver");
        t.setDaemon(true);
        return t;
    });

    /** Vom Auswahlrad (Render-Thread). */
    public static void save(AbstractClientPlayerEntity player) {
        if (player == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        String name = player.getGameProfile().name();
        String textures = texturesValue(client, player);
        String key = name.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (IN_FLIGHT.contains(key)) {
            toast(Notification.Type.INFO, "Skin von " + name + " wird schon gespeichert …");
            return;
        }
        if (now - lastStartMs < MIN_INTERVAL_MS) {
            toast(Notification.Type.INFO, "Bitte kurz warten");
            return;
        }
        if (!IN_FLIGHT.add(key)) return;
        lastStartMs = now;
        try {
            EXECUTOR.execute(() -> {
                try {
                    run(name, textures);
                } catch (Throwable t) {
                    LOGGER.warn("Saving skin failed: {}", t.getClass().getSimpleName());
                    toast(Notification.Type.DISABLED, "Skin von " + name + " konnte nicht gespeichert werden");
                } finally {
                    IN_FLIGHT.remove(key);
                }
            });
        } catch (RejectedExecutionException e) {
            IN_FLIGHT.remove(key);
        }
    }

    /** textures-Property: zuerst aus der Spielerliste (vom Server gesendetes Profil), sonst vom Spieler selbst. */
    private static String texturesValue(MinecraftClient client, AbstractClientPlayerEntity player) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        PlayerListEntry entry = handler != null ? handler.getPlayerListEntry(player.getUuid()) : null;
        String value = entry != null ? texturesValue(entry.getProfile()) : null;
        return value != null ? value : texturesValue(player.getGameProfile());
    }

    private static String texturesValue(GameProfile profile) {
        if (profile == null) return null;
        for (Property property : profile.properties().get("textures")) {
            String v = property.value();
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Hintergrund-Thread
    // ------------------------------------------------------------------------------------------------------------

    private record SkinInfo(String url, String model) {}

    private static void run(String name, String textures) {
        SkinInfo info = textures != null ? parseTextures(textures) : null;
        if (info == null || info.url() == null) {
            toast(Notification.Type.DISABLED, SkinImportReply.noMojangSkinMessage(name));
            return;
        }
        String url = SkinUrls.normalize(info.url());
        if (url == null) {
            toast(Notification.Type.DISABLED, "Skin von " + name + " liegt nicht bei Mojang – nicht gespeichert");
            return;
        }
        byte[] png;
        try {
            png = download(url);
        } catch (TooLargeException e) {
            toast(Notification.Type.DISABLED, "Skin von " + name + " ist größer als 1 MB – nicht gespeichert");
            return;
        } catch (Exception e) {
            LOGGER.debug("Skin download failed: {}", e.getClass().getSimpleName());
            toast(Notification.Type.DISABLED, "Skin von " + name + " konnte nicht geladen werden");
            return;
        }
        SkinPng.Result check = SkinPng.check(png);
        if (!check.ok()) {
            toast(Notification.Type.DISABLED, "Ungültige Skin-Datei von " + name + ": " + check.reason());
            return;
        }
        String variant = SkinUrls.variant(info.model());

        LauncherBridge.SkinImportResult result = LauncherBridge.importSkin(name, png, variant);
        switch (result.status()) {
            case CREATED -> toast(Notification.Type.ENABLED, SkinImportReply.savedMessage(name));
            case DUPLICATE -> toast(Notification.Type.INFO, SkinImportReply.duplicateMessage(name));
            case ERROR -> toast(Notification.Type.DISABLED, result.message() != null ? result.message() : SkinImportReply.MSG_UNREACHABLE);
            case TOO_LARGE -> saveLocally(name, png, SkinImportReply.MSG_FALLBACK_TOO_LARGE);
            default -> saveLocally(name, png, SkinImportReply.MSG_FALLBACK);
        }
    }

    /** Base64-JSON der textures-Property → Adresse und Modell des Skins ({@code url == null}: Standard-Skin). */
    private static SkinInfo parseTextures(String base64) {
        try {
            String json = new String(Base64.getDecoder().decode(base64.trim()), StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonObject()) return null;
            JsonElement textures = root.getAsJsonObject().get("textures");
            if (textures == null || !textures.isJsonObject()) return new SkinInfo(null, null);
            JsonElement skin = textures.getAsJsonObject().get("SKIN");
            if (skin == null || !skin.isJsonObject()) return new SkinInfo(null, null);
            JsonObject s = skin.getAsJsonObject();
            String url = string(s, "url");
            String model = null;
            JsonElement metadata = s.get("metadata");
            if (metadata != null && metadata.isJsonObject()) model = string(metadata.getAsJsonObject(), "model");
            return new SkinInfo(url, model);
        } catch (Exception e) {
            return null;
        }
    }

    private static String string(JsonObject o, String key) {
        JsonElement el = o.get(key);
        return el != null && el.isJsonPrimitive() ? el.getAsString() : null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Download (Zeitlimit für alles zusammen, Größenlimit schon beim Lesen)
    // ------------------------------------------------------------------------------------------------------------

    private static final class TooLargeException extends IOException {
        TooLargeException() {
            super("skin too large", null);
        }
    }

    private static HttpClient http() {
        HttpClient c = http;
        if (c == null) {
            synchronized (SkinSaver.class) {
                c = http;
                if (c == null) {
                    c = HttpClient.newBuilder()
                            .connectTimeout(DOWNLOAD_TIMEOUT)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                    http = c;
                }
            }
        }
        return c;
    }

    private static byte[] download(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(DOWNLOAD_TIMEOUT)
                .header("Accept", "image/png")
                .GET()
                .build();
        CompletableFuture<HttpResponse<byte[]>> future = http().sendAsync(req, info -> {
            if (info.statusCode() != 200) return HttpResponse.BodySubscribers.replacing((byte[]) null);
            long declared = info.headers().firstValueAsLong("Content-Length").orElse(-1L);
            if (declared > SkinPng.MAX_BYTES) return HttpResponse.BodySubscribers.replacing((byte[]) null);
            return new CappedBody(SkinPng.MAX_BYTES);
        });
        HttpResponse<byte[]> resp;
        try {
            resp = future.get(DOWNLOAD_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TooLargeException tooLarge) throw tooLarge;
            throw e;
        }
        if (resp.statusCode() != 200) throw new IOException("HTTP " + resp.statusCode());
        long declared = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (declared > SkinPng.MAX_BYTES) throw new TooLargeException();
        byte[] body = resp.body();
        if (body == null) throw new IOException("empty body");
        return body;
    }

    /** Sammelt den Body und bricht ab, sobald mehr als {@code cap} Bytes kommen. */
    private static final class CappedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int cap;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
        private Flow.Subscription subscription;

        CappedBody(int cap) {
            this.cap = cap;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) return;
            for (ByteBuffer buffer : items) {
                int n = buffer.remaining();
                if (out.size() + (long) n > cap) {
                    subscription.cancel();
                    result.completeExceptionally(new TooLargeException());
                    return;
                }
                byte[] chunk = new byte[n];
                buffer.get(chunk);
                out.write(chunk, 0, n);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(out.toByteArray());
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Rückfall: lokal speichern
    // ------------------------------------------------------------------------------------------------------------

    /** {@code config/bameclient/saved-skins/<Name>.png}, bei Kollision {@code <Name>_2.png} usw. (nie überschreiben). */
    private static void saveLocally(String name, byte[] png, String successMessage) {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("bameclient").resolve("saved-skins");
        String base = SkinFileNames.sanitize(name);
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            // Gleicher Skin schon gespeichert? Dann keine Kopie anlegen.
            for (int n = 1; n <= SkinFileNames.MAX_NUMBER; n++) {
                Path existing = dir.resolve(SkinFileNames.fileName(base, n));
                if (!Files.exists(existing)) break;
                if (Files.size(existing) == png.length && Arrays.equals(Files.readAllBytes(existing), png)) {
                    toast(Notification.Type.INFO, SkinImportReply.alreadySavedLocallyMessage(name));
                    return;
                }
            }
            tmp = Files.createTempFile(dir, base, ".tmp");
            Files.write(tmp, png);
            for (int n = 1; n <= SkinFileNames.MAX_NUMBER; n++) {
                Path target = dir.resolve(SkinFileNames.fileName(base, n));
                try {
                    // ohne REPLACE_EXISTING: schlägt fehl, wenn die Datei existiert (keine Lücke zwischen Prüfen und Schreiben)
                    Files.move(tmp, target);
                    tmp = null;
                    toast(Notification.Type.INFO, successMessage);
                    return;
                } catch (FileAlreadyExistsException e) {
                    // nächste Nummer
                }
            }
            toast(Notification.Type.DISABLED, SkinImportReply.MSG_UNREACHABLE + " – zu viele gespeicherte Skins mit diesem Namen");
        } catch (IOException e) {
            LOGGER.warn("Saving skin locally failed: {}", e.getClass().getSimpleName());
            toast(Notification.Type.DISABLED, SkinImportReply.MSG_UNREACHABLE + " – Skin konnte nicht gespeichert werden");
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {}
            }
        }
    }

    private static void toast(Notification.Type type, String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        client.execute(() -> NotificationManager.add(new Notification(SkinImportReply.TOAST_TITLE, message, type, TOAST_MS)));
    }
}
