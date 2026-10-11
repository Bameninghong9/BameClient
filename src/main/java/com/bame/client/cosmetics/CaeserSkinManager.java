package com.bame.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CaeserSkinManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserSkinManager");

    public static class LauncherSkinEntry {
        public final String id;
        public final String name;
        public final String base64Data;
        public final long createdAt;
        public volatile String variant = null;
        private volatile Identifier textureId;
        private volatile SkinTextures skinTextures;
        private volatile PlayerSkinType skinType = PlayerSkinType.WIDE;
        private volatile boolean textureLoaded = false;

        public LauncherSkinEntry(String id, String name, String base64Data, long createdAt, String variant) {
            this.id = id;
            this.name = name;
            this.base64Data = base64Data;
            this.createdAt = createdAt;
            this.variant = variant;
        }

        public LauncherSkinEntry(String id, String name, String base64Data, long createdAt) {
            this(id, name, base64Data, createdAt, null);
        }

        public Identifier getTextureId() {
            return textureId;
        }

        public SkinTextures getSkinTextures() {
            return skinTextures;
        }

        public PlayerSkinType getSkinType() {
            return skinType;
        }

        public boolean isTextureLoaded() {
            return textureLoaded;
        }
    }

    private static final Map<String, LauncherSkinEntry> CUSTOM_SKINS_BY_ID = new ConcurrentHashMap<>();
    private static final List<LauncherSkinEntry> CUSTOM_SKINS_LIST = new CopyOnWriteArrayList<>();
    private static volatile String activeSkinId = "account";
    private static volatile SkinTextures activeSkinTextures = null;
    private static long lastSettingsModified = -1L;
    private static long lastSkinsModified = -1L;
    private static long lastCheckTime = 0L;
    private static volatile String lastUploadedSkinId = null;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static Path getLauncherDir() {
        String appdata = System.getenv("APPDATA");
        if (appdata != null && !appdata.isBlank()) {
            Path p = Path.of(appdata, "Caeser Client");
            if (Files.exists(p)) return p;
        }
        Path winPath = Path.of(System.getProperty("user.home"), "AppData", "Roaming", "Caeser Client");
        if (Files.exists(winPath)) return winPath;

        Path macPath = Path.of(System.getProperty("user.home"), "Library", "Application Support", "Caeser Client");
        if (Files.exists(macPath)) return macPath;

        Path linuxPath = Path.of(System.getProperty("user.home"), ".config", "Caeser Client");
        if (Files.exists(linuxPath)) return linuxPath;

        if (appdata != null && !appdata.isBlank()) {
            return Path.of(appdata, "Caeser Client");
        }
        return winPath;
    }

    public static boolean isSlimSkin(NativeImage image) {
        if (image == null || image.getWidth() < 64 || image.getHeight() < 64) {
            return false;
        }
        int p1 = image.getColorArgb(54, 20);
        int p2 = image.getColorArgb(55, 20);
        int a1 = (p1 >>> 24) & 0xFF;
        int a2 = (p2 >>> 24) & 0xFF;
        return a1 == 0 && a2 == 0;
    }

    public static NativeImage remapSkin(NativeImage image) {
        if (image == null) return null;
        int width = image.getWidth();
        int height = image.getHeight();
        if (width != 64 || (height != 32 && height != 64)) {
            return image;
        }

        boolean isLegacy = (height == 32);
        if (isLegacy) {
            NativeImage expanded = new NativeImage(64, 64, true);
            expanded.copyFrom(image);
            image.close();
            image = expanded;
            image.fillRect(0, 32, 64, 32, 0);

            // Copy right leg to left leg
            image.copyRect(4, 16, 16, 32, 4, 4, true, false);
            image.copyRect(8, 16, 16, 32, 4, 4, true, false);
            image.copyRect(0, 20, 24, 32, 4, 12, true, false);
            image.copyRect(4, 20, 16, 32, 4, 12, true, false);
            image.copyRect(8, 20, 8, 32, 4, 12, true, false);
            image.copyRect(12, 20, 16, 32, 4, 12, true, false);

            // Copy right arm to left arm
            image.copyRect(44, 16, -8, 32, 4, 4, true, false);
            image.copyRect(48, 16, -8, 32, 4, 4, true, false);
            image.copyRect(40, 20, 0, 32, 4, 12, true, false);
            image.copyRect(44, 20, -8, 32, 4, 12, true, false);
            image.copyRect(48, 20, -16, 32, 4, 12, true, false);
            image.copyRect(52, 20, -8, 32, 4, 12, true, false);
        }

        // Vanilla Minecraft stripAlpha: The base layer (head, torso, legs, arms)
        // MUST be fully opaque (alpha = 255) so no transparent holes or floating limbs occur
        stripAlpha(image, 0, 0, 32, 16);    // Head base
        stripAlpha(image, 0, 16, 64, 32);   // Torso, right arm base, right leg base
        stripAlpha(image, 16, 48, 48, 64);  // Left leg base, left arm base

        return image;
    }

    private static void stripAlpha(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                int col = image.getColorArgb(x, y);
                image.setColorArgb(x, y, col | 0xFF000000);
            }
        }
    }

    public static void ensureTextureLoaded(LauncherSkinEntry entry) {
        if (entry == null || (entry.textureLoaded && entry.textureId != null)) return;
        if (entry.base64Data == null || entry.base64Data.isBlank()) return;

        try {
            String raw = entry.base64Data.trim();
            int comma = raw.indexOf(',');
            String b64 = comma >= 0 ? raw.substring(comma + 1) : raw;
            byte[] bytes = Base64.getDecoder().decode(b64.trim());

            NativeImage image = NativeImage.read(bytes);
            if (image == null) return;

            boolean slim = "slim".equalsIgnoreCase(entry.variant)
                    || (entry.variant == null && isSlimSkin(image));
            image = remapSkin(image);

            entry.skinType = slim ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;

            String safeId = entry.id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
            Identifier regId = Identifier.of("caeserclient", "custom_skins/" + safeId);
            entry.textureId = regId;

            MinecraftClient mc = MinecraftClient.getInstance();
            NativeImage finalImage = image;

            Runnable registerAction = () -> {
                try {
                    NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "caeser_skin_" + safeId, finalImage);
                    mc.getTextureManager().registerTexture(regId, tex);
                    entry.textureLoaded = true;

                    SkinTextures acc = getAccountSkinTextures(mc);
                    AssetInfo.TextureAsset cape = (mc.player != null && mc.player.getSkin() != null && mc.player.getSkin().cape() != null)
                            ? mc.player.getSkin().cape()
                            : (acc != null ? acc.cape() : null);
                    AssetInfo.TextureAsset elytra = (mc.player != null && mc.player.getSkin() != null && mc.player.getSkin().elytra() != null)
                            ? mc.player.getSkin().elytra()
                            : (acc != null ? acc.elytra() : null);

                    entry.skinTextures = new SkinTextures(
                            new AssetInfo.TextureAssetInfo(regId, regId),
                            cape,
                            elytra,
                            entry.skinType,
                            true
                    );

                    if (entry.id.equalsIgnoreCase(activeSkinId)) {
                        activeSkinTextures = entry.skinTextures;
                    }
                } catch (Throwable t) {
                    LOGGER.error("Failed to register skin texture for {}", entry.id, t);
                    try {
                        finalImage.close();
                    } catch (Throwable ignored) {}
                }
            };

            if (mc != null) {
                if (mc.isOnThread()) {
                    registerAction.run();
                } else {
                    mc.execute(registerAction);
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to decode custom skin {}", entry.id, t);
        }
    }

    public static synchronized void reloadFromLauncher() {
        Path dir = getLauncherDir();
        Path settingsFile = dir.resolve("settings.json");
        Path skinsFile = dir.resolve("skins.json");

        if (!Files.exists(dir)) return;

        try {
            // 1. Read skins.json
            if (Files.exists(skinsFile)) {
                long mod = Files.getLastModifiedTime(skinsFile).toMillis();
                if (mod != lastSkinsModified) {
                    lastSkinsModified = mod;
                    String content = Files.readString(skinsFile);
                    JsonArray arr = JsonParser.parseString(content).getAsJsonArray();
                    List<LauncherSkinEntry> newEntries = new ArrayList<>();
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
                        String id = obj.has("id") ? obj.get("id").getAsString() : "";
                        String name = obj.has("name") ? obj.get("name").getAsString() : "Custom Skin";
                        String data = obj.has("data") ? obj.get("data").getAsString() : "";
                        long createdAt = obj.has("createdAt") ? obj.get("createdAt").getAsLong() : 0L;
                        String variant = obj.has("variant") ? obj.get("variant").getAsString() : null;
                        if (!id.isEmpty() && !data.isEmpty()) {
                            LauncherSkinEntry entry = CUSTOM_SKINS_BY_ID.computeIfAbsent(id, k -> new LauncherSkinEntry(id, name, data, createdAt, variant));
                            if (!Objects.equals(entry.variant, variant)) {
                                entry.variant = variant;
                                entry.textureLoaded = false;
                            }
                            newEntries.add(entry);
                            ensureTextureLoaded(entry);
                        }
                    }
                    CUSTOM_SKINS_LIST.clear();
                    CUSTOM_SKINS_LIST.addAll(newEntries);
                }
            }

            // 2. Read settings.json
            if (Files.exists(settingsFile)) {
                long mod = Files.getLastModifiedTime(settingsFile).toMillis();
                if (mod != lastSettingsModified) {
                    lastSettingsModified = mod;
                    String content = Files.readString(settingsFile);
                    JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                    if (obj.has("activeSkinId")) {
                        String newActive = obj.get("activeSkinId").getAsString();
                        applyActiveSkinInternal(newActive);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("Failed to reload skins from launcher: {}", t.getMessage());
        }
    }

    private static void applyActiveSkinInternal(String skinId) {
        if (skinId == null || skinId.isBlank() || "account".equalsIgnoreCase(skinId)) {
            activeSkinId = "account";
            activeSkinTextures = null;
            return;
        }

        activeSkinId = skinId;
        if ("steve".equalsIgnoreCase(skinId)) {
            activeSkinTextures = DefaultSkinHelper.getSteve();
            return;
        }

        LauncherSkinEntry entry = CUSTOM_SKINS_BY_ID.get(skinId);
        if (entry != null) {
            ensureTextureLoaded(entry);
            if (entry.skinTextures != null) {
                activeSkinTextures = entry.skinTextures;
            } else {
                String safeId = entry.id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
                Identifier regId = Identifier.of("caeserclient", "custom_skins/" + safeId);
                activeSkinTextures = new SkinTextures(
                        new AssetInfo.TextureAssetInfo(regId, regId),
                        null,
                        null,
                        entry.skinType,
                        true
                );
            }
        } else {
            activeSkinId = "account";
            activeSkinTextures = null;
        }
    }

    public static void syncOnServerJoin() {
        LOGGER.info("[CaeserSkinManager] Server join detected: syncing skin with launcher settings...");
        lastSettingsModified = -1L;
        lastSkinsModified = -1L;
        reloadFromLauncher();
        LOGGER.info("[CaeserSkinManager] Synced active skin: {}", activeSkinId);
        if (hasActiveOverride() && !Objects.equals(lastUploadedSkinId, activeSkinId)) {
            uploadActiveSkinToMojangAsync();
        }
    }

    public static void checkPeriodicSync() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < 1000) return;
        lastCheckTime = now;
        reloadFromLauncher();
    }

    public static void setActiveSkin(String skinId) {
        if (skinId == null || skinId.isBlank()) {
            skinId = "account";
        }
        applyActiveSkinInternal(skinId);

        try {
            Path dir = getLauncherDir();
            Path settingsFile = dir.resolve("settings.json");
            JsonObject obj;
            if (Files.exists(settingsFile)) {
                obj = JsonParser.parseString(Files.readString(settingsFile)).getAsJsonObject();
            } else {
                obj = new JsonObject();
            }
            obj.addProperty("activeSkinId", skinId);
            Files.writeString(settingsFile, obj.toString());
            lastSettingsModified = Files.getLastModifiedTime(settingsFile).toMillis();
            LOGGER.info("[CaeserSkinManager] Saved activeSkinId = {} to settings.json", skinId);
        } catch (Throwable t) {
            LOGGER.warn("Failed to persist activeSkinId to launcher settings.json: {}", t.getMessage());
        }

        uploadActiveSkinToMojangAsync();
    }

    public static void uploadActiveSkinToMojangAsync() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getSession() == null) return;
        String token = mc.getSession().getAccessToken();
        if (token == null || token.isBlank() || token.length() < 20 || "0".equals(token)) {
            LOGGER.info("[CaeserSkinManager] No valid Minecraft session token found, skipping Mojang skin upload.");
            return;
        }

        final String skinId = activeSkinId;
        if (skinId == null || "account".equalsIgnoreCase(skinId)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                if ("steve".equalsIgnoreCase(skinId)) {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.minecraftservices.com/minecraft/profile/skins/active"))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer " + token)
                            .DELETE()
                            .build();

                    HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200 || resp.statusCode() == 204) {
                        lastUploadedSkinId = "steve";
                        LOGGER.info("[CaeserSkinManager] Successfully reset skin to default on Mojang.");
                        com.bame.client.notification.NotificationManager.showInfo("Skin zurückgesetzt", "Skin bei Mojang auf Standard gesetzt.");
                    } else {
                        LOGGER.warn("[CaeserSkinManager] Failed to reset skin on Mojang: HTTP {}", resp.statusCode());
                    }
                    return;
                }

                LauncherSkinEntry entry = CUSTOM_SKINS_BY_ID.get(skinId);
                if (entry == null || entry.base64Data == null || entry.base64Data.isBlank()) {
                    LOGGER.warn("[CaeserSkinManager] Custom skin entry not found for upload: {}", skinId);
                    return;
                }

                String raw = entry.base64Data.trim();
                int comma = raw.indexOf(',');
                String b64 = comma >= 0 ? raw.substring(comma + 1) : raw;
                byte[] bytes = Base64.getDecoder().decode(b64.trim());

                String variant = (entry.getSkinType() == PlayerSkinType.SLIM) ? "slim" : "classic";
                String boundary = "----CaeserSkinBoundary" + System.currentTimeMillis();
                byte[] body = buildMultipartBody(boundary, variant, bytes);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.minecraftservices.com/minecraft/profile/skins"))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200 || resp.statusCode() == 204) {
                    lastUploadedSkinId = skinId;
                    LOGGER.info("[CaeserSkinManager] Successfully uploaded skin {} (variant: {}) to Mojang!", skinId, variant);
                    com.bame.client.notification.NotificationManager.showInfo("Skin synchronisiert", "Dein Skin ist jetzt für alle Spieler sichtbar!");
                } else if (resp.statusCode() == 429) {
                    LOGGER.warn("[CaeserSkinManager] Mojang rate limit reached for skin upload.");
                    com.bame.client.notification.NotificationManager.showInfo("Skin Rate-Limit", "Mojang erlaubt Änderungen nur alle paar Minuten.");
                } else {
                    LOGGER.warn("[CaeserSkinManager] Mojang skin upload failed: HTTP {} - {}", resp.statusCode(), resp.body());
                }
            } catch (Throwable t) {
                LOGGER.warn("[CaeserSkinManager] Error during async Mojang skin upload", t);
            }
        });
    }

    private static byte[] buildMultipartBody(String boundary, String variant, byte[] pngBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            String crlf = "\r\n";
            out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"variant\"" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write((variant + crlf).getBytes(StandardCharsets.UTF_8));

            out.write(("--" + boundary + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"" + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(("Content-Type: image/png" + crlf + crlf).getBytes(StandardCharsets.UTF_8));
            out.write(pngBytes);
            out.write(crlf.getBytes(StandardCharsets.UTF_8));

            out.write(("--" + boundary + "--" + crlf).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {}
        return out.toByteArray();
    }

    public static String getActiveSkinId() {
        return activeSkinId != null ? activeSkinId : "account";
    }

    public static String getActiveSkinName() {
        if ("account".equalsIgnoreCase(activeSkinId)) return "Account Skin";
        if ("steve".equalsIgnoreCase(activeSkinId)) return "Steve";
        LauncherSkinEntry entry = CUSTOM_SKINS_BY_ID.get(activeSkinId);
        if (entry != null) return entry.name;
        return "Custom Skin";
    }

    public static boolean hasActiveOverride() {
        return activeSkinId != null && !"account".equalsIgnoreCase(activeSkinId);
    }

    public static SkinTextures getActiveSkinTextures() {
        if ("steve".equalsIgnoreCase(activeSkinId)) {
            return DefaultSkinHelper.getSteve();
        }
        if (activeSkinTextures != null) {
            return activeSkinTextures;
        }
        LauncherSkinEntry entry = CUSTOM_SKINS_BY_ID.get(activeSkinId);
        if (entry != null) {
            ensureTextureLoaded(entry);
            return entry.getSkinTextures();
        }
        return null;
    }

    public static SkinTextures getAccountSkinTextures(MinecraftClient mc) {
        if (mc == null) mc = MinecraftClient.getInstance();
        if (mc != null && mc.getGameProfile() != null) {
            try {
                if (mc.getSkinProvider() != null) {
                    SkinTextures textures = mc.getSkinProvider().supplySkinTextures(mc.getGameProfile(), true).get();
                    if (textures != null && textures.body() != null) return textures;
                }
            } catch (Throwable ignored) {}
            return DefaultSkinHelper.getSkinTextures(mc.getGameProfile());
        }
        return DefaultSkinHelper.getSteve();
    }

    public static Identifier getSkinTexture(String skinId, MinecraftClient mc) {
        if ("account".equalsIgnoreCase(skinId)) {
            SkinTextures textures = getAccountSkinTextures(mc);
            if (textures != null && textures.body() != null && textures.body().texturePath() != null) {
                return textures.body().texturePath();
            }
            return DefaultSkinHelper.getTexture();
        }
        if ("steve".equalsIgnoreCase(skinId)) {
            return DefaultSkinHelper.getTexture();
        }
        LauncherSkinEntry entry = CUSTOM_SKINS_BY_ID.get(skinId);
        if (entry != null) {
            ensureTextureLoaded(entry);
            if (entry.getTextureId() != null) {
                return entry.getTextureId();
            }
        }
        return DefaultSkinHelper.getTexture();
    }

    public static boolean isSlim(String skinId) {
        if ("account".equalsIgnoreCase(skinId)) {
            SkinTextures textures = getAccountSkinTextures(MinecraftClient.getInstance());
            return textures != null && textures.model() == PlayerSkinType.SLIM;
        }
        if ("steve".equalsIgnoreCase(skinId)) {
            return false;
        }
        LauncherSkinEntry entry = CUSTOM_SKINS_BY_ID.get(skinId);
        return entry != null && entry.getSkinType() == PlayerSkinType.SLIM;
    }

    public static List<LauncherSkinEntry> getCustomSkins() {
        return Collections.unmodifiableList(CUSTOM_SKINS_LIST);
    }
}
