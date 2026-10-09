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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CaeserSkinManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserSkinManager");

    public static class LauncherSkinEntry {
        public final String id;
        public final String name;
        public final String base64Data;
        public final long createdAt;
        private volatile Identifier textureId;
        private volatile SkinTextures skinTextures;
        private volatile PlayerSkinType skinType = PlayerSkinType.WIDE;
        private volatile boolean textureLoaded = false;

        public LauncherSkinEntry(String id, String name, String base64Data, long createdAt) {
            this.id = id;
            this.name = name;
            this.base64Data = base64Data;
            this.createdAt = createdAt;
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

            if (image.getWidth() == 64 && image.getHeight() == 32) {
                NativeImage expanded = new NativeImage(image.getFormat(), 64, 64, true);
                expanded.copyRect(image, 0, 0, 0, 0, 64, 32, false, false);
                image.close();
                image = expanded;
            }

            boolean slim = isSlimSkin(image);
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
                        if (!id.isEmpty() && !data.isEmpty()) {
                            LauncherSkinEntry entry = CUSTOM_SKINS_BY_ID.computeIfAbsent(id, k -> new LauncherSkinEntry(id, name, data, createdAt));
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
