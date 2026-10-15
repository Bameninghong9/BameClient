package com.bame.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class PlayerSkinResolver {
    private static final Map<String, Identifier> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    /*
     * resolve() läuft pro Frame (MiniMe-/Pet-Skins mit eigenem Spielernamen im Renderer und in der Inventar-Vorschau).
     * Früher: Regex pro Aufruf, Suche durch die ganze Tab-Liste pro Frame und nach einem fehlgeschlagenen Download
     * sofort der nächste (Mojang-API-Anfragen in Dauerschleife). Jetzt: Schlüssel zwischengespeichert, Tab-Liste
     * höchstens alle 2 s je Name, nach einem Fehlschlag frühestens nach 60 s ein neuer Download.
     */
    private static final Pattern KEY_INVALID = Pattern.compile("[^a-z0-9_.-]");
    private static final Map<String, String> KEYS = new ConcurrentHashMap<>();
    private static final Map<String, Long> NEXT_TAB_SCAN = new ConcurrentHashMap<>();
    private static final Map<String, Long> RETRY_AT = new ConcurrentHashMap<>();
    private static final long TAB_SCAN_INTERVAL_MS = 2_000L;
    private static final long RETRY_AFTER_FAILURE_MS = 60_000L;
    private static final int MAX_TRACKED = 512;

    private static String keyFor(String clean) {
        String key = KEYS.get(clean);
        if (key == null) {
            if (KEYS.size() > MAX_TRACKED) KEYS.clear();
            key = KEY_INVALID.matcher(clean.toLowerCase(Locale.ROOT)).replaceAll("_");
            KEYS.put(clean, key);
        }
        return key;
    }

    public static Identifier resolve(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return DefaultSkinHelper.getTexture();
        }
        String clean = playerName.trim();
        String key = keyFor(clean);

        Identifier cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return DefaultSkinHelper.getTexture();
        long now = System.currentTimeMillis();

        // 1. Check if player is present in current world/server tab list (gedrosselt je Name)
        Long nextScan = NEXT_TAB_SCAN.get(key);
        if (mc.getNetworkHandler() != null && (nextScan == null || now >= nextScan)) {
            if (NEXT_TAB_SCAN.size() > MAX_TRACKED) NEXT_TAB_SCAN.clear();
            NEXT_TAB_SCAN.put(key, now + TAB_SCAN_INTERVAL_MS);
            for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                if (entry.getProfile() != null && clean.equalsIgnoreCase(entry.getProfile().name())) {
                    if (entry.getSkinTextures() != null && entry.getSkinTextures().body() != null) {
                        Identifier id = entry.getSkinTextures().body().texturePath();
                        CACHE.put(key, id);
                        return id;
                    }
                }
            }
        }

        // 2. Fetch skin in background thread (nach einem Fehlschlag erst nach einer Pause erneut)
        Long retryAt = RETRY_AT.get(key);
        if ((retryAt == null || now >= retryAt) && PENDING.add(key)) {
            CompletableFuture.runAsync(() -> {
                boolean ok = false;
                try {
                    String skinUrl = fetchSkinUrlFromMojang(clean);
                    if (skinUrl == null || skinUrl.isEmpty()) {
                        skinUrl = "https://minotar.net/skin/" + clean;
                    }

                    HttpURLConnection conn = (HttpURLConnection) new URI(skinUrl).toURL().openConnection();
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);
                    conn.setRequestProperty("User-Agent", "CaeserClient/1.0");

                    if (conn.getResponseCode() == 200) {
                        try (InputStream in = conn.getInputStream()) {
                            NativeImage rawImage = NativeImage.read(in);
                            if (rawImage != null) {
                                NativeImage image = CaeserSkinManager.remapSkin(rawImage);
                                ok = true;
                                mc.execute(() -> {
                                    try {
                                        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "skin_" + key, image);
                                        Identifier regId = Identifier.of("caeserclient", "skins/" + key);
                                        mc.getTextureManager().registerTexture(regId, texture);
                                        CACHE.put(key, regId);
                                    } catch (Throwable t) {
                                        image.close();
                                    }
                                });
                            }
                        }
                    }
                } catch (Throwable ignored) {
                } finally {
                    if (ok) {
                        RETRY_AT.remove(key);
                    } else {
                        if (RETRY_AT.size() > MAX_TRACKED) RETRY_AT.clear();
                        RETRY_AT.put(key, System.currentTimeMillis() + RETRY_AFTER_FAILURE_MS);
                    }
                    PENDING.remove(key);
                }
            });
        }

        return DefaultSkinHelper.getTexture();
    }

    private static String fetchSkinUrlFromMojang(String username) {
        try {
            // 1. Get UUID from Mojang username API
            URL profileUrl = new URI("https://api.mojang.com/users/profiles/minecraft/" + username).toURL();
            HttpURLConnection conn = (HttpURLConnection) profileUrl.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "CaeserClient/1.0");
            if (conn.getResponseCode() != 200) return null;

            String uuid;
            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                if (!obj.has("id")) return null;
                uuid = obj.get("id").getAsString();
            }

            // 2. Get session profile with encoded skin textures
            URL sessionUrl = new URI("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid).toURL();
            HttpURLConnection sessionConn = (HttpURLConnection) sessionUrl.openConnection();
            sessionConn.setConnectTimeout(4000);
            sessionConn.setReadTimeout(4000);
            sessionConn.setRequestProperty("User-Agent", "CaeserClient/1.0");
            if (sessionConn.getResponseCode() != 200) return null;

            try (InputStreamReader reader = new InputStreamReader(sessionConn.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject profileObj = JsonParser.parseReader(reader).getAsJsonObject();
                if (!profileObj.has("properties")) return null;
                JsonArray props = profileObj.getAsJsonArray("properties");
                for (JsonElement propElem : props) {
                    JsonObject prop = propElem.getAsJsonObject();
                    if ("textures".equals(prop.get("name").getAsString())) {
                        String b64 = prop.get("value").getAsString();
                        byte[] decoded = Base64.getDecoder().decode(b64);
                        JsonObject texJson = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
                        if (texJson.has("textures")) {
                            JsonObject textures = texJson.getAsJsonObject("textures");
                            if (textures.has("SKIN")) {
                                JsonObject skin = textures.getAsJsonObject("SKIN");
                                if (skin.has("url")) {
                                    return skin.get("url").getAsString();
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void clearCache() {
        CACHE.clear();
        PENDING.clear();
        NEXT_TAB_SCAN.clear();
        RETRY_AT.clear();
    }
}
