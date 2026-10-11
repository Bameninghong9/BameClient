package com.bame.client.emotes;

import com.bame.client.cosmetics.CaeserSkinManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages remote permissions and unlocks for emotes.
 * Fetches authoritatively from GitHub (NoRisk Client style), with local caching for offline play.
 */
public class EmoteUnlockManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserEmoteUnlock");

    private static final String PRIMARY_URL = "https://raw.githubusercontent.com/Bameninghong9/Caeser-Client/main/config/emote_unlocks.json";
    private static final String FALLBACK_URL = "https://raw.githubusercontent.com/Bameninghong9/BameClient/main/config/emote_unlocks.json";

    public static class UnlockData {
        public final Set<String> admins = new HashSet<>();
        public final Set<String> defaultUnlocked = new HashSet<>();
        public final Map<String, Set<String>> playerEmotes = new HashMap<>();

        public UnlockData() {
            // Default built-in admins
            admins.add("bameninghong");
            admins.add("bamen");
            admins.add("bameninghong9");

            // Free standard emotes for everyone
            defaultUnlocked.add("T_POSE");
            defaultUnlocked.add("SIT");
        }
    }

    private static volatile UnlockData currentData = new UnlockData();
    private static volatile long lastFetchTime = 0L;
    private static final AtomicBoolean isFetching = new AtomicBoolean(false);

    public static void init() {
        // Load cached file first for instant startup availability
        loadFromCache();
        // Trigger background fetch from GitHub
        fetchAsync();
    }

    /**
     * Periodically check and refresh if more than 30 seconds have passed.
     * Non-blocking (runs in daemon thread pool).
     */
    public static void checkRefresh() {
        if (System.currentTimeMillis() - lastFetchTime > 30_000L) {
            fetchAsync();
        }
    }

    public static void forceRefresh() {
        fetchAsync();
    }

    private static void fetchAsync() {
        if (!isFetching.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String jsonStr = downloadString(PRIMARY_URL);
                if (jsonStr == null || jsonStr.isBlank()) {
                    jsonStr = downloadString(FALLBACK_URL);
                }

                if (jsonStr != null && !jsonStr.isBlank()) {
                    UnlockData parsed = parseJson(jsonStr);
                    if (parsed != null) {
                        currentData = parsed;
                        lastFetchTime = System.currentTimeMillis();
                        saveToCache(jsonStr);
                        LOGGER.info("Successfully synced emote unlocks from remote GitHub repository.");
                        return;
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("Could not fetch remote emote unlocks: {}", t.getMessage());
            } finally {
                isFetching.set(false);
            }
        });
    }

    private static String downloadString(String urlStr) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "CaeserClient/1.0 (Minecraft)")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static UnlockData parseJson(String jsonStr) {
        try {
            JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
            UnlockData data = new UnlockData();

            if (root.has("admins") && root.get("admins").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("admins");
                for (JsonElement el : arr) {
                    if (el.isJsonPrimitive()) {
                        String clean = cleanKey(el.getAsString());
                        if (!clean.isEmpty()) data.admins.add(clean);
                    }
                }
            }

            if (root.has("default_unlocked") && root.get("default_unlocked").isJsonArray()) {
                data.defaultUnlocked.clear();
                JsonArray arr = root.getAsJsonArray("default_unlocked");
                for (JsonElement el : arr) {
                    if (el.isJsonPrimitive()) {
                        String clean = el.getAsString().trim().toUpperCase(Locale.ROOT);
                        if (!clean.isEmpty()) data.defaultUnlocked.add(clean);
                    }
                }
            }

            if (root.has("players") && root.get("players").isJsonObject()) {
                JsonObject playersObj = root.getAsJsonObject("players");
                for (String pKey : playersObj.keySet()) {
                    String cleanPKey = cleanKey(pKey);
                    if (cleanPKey.isEmpty()) continue;

                    Set<String> set = new HashSet<>();
                    JsonElement el = playersObj.get(pKey);
                    if (el.isJsonArray()) {
                        for (JsonElement item : el.getAsJsonArray()) {
                            if (item.isJsonPrimitive()) {
                                set.add(item.getAsString().trim().toUpperCase(Locale.ROOT));
                            }
                        }
                    } else if (el.isJsonPrimitive()) {
                        set.add(el.getAsString().trim().toUpperCase(Locale.ROOT));
                    }
                    data.playerEmotes.put(cleanPKey, set);
                }
            }

            return data;
        } catch (Throwable t) {
            LOGGER.error("Failed to parse emote unlocks JSON", t);
            return null;
        }
    }

    private static String cleanKey(String str) {
        if (str == null) return "";
        return str.trim().toLowerCase(Locale.ROOT).replace("-", "");
    }

    private static Path getCachePath() {
        try {
            Path launcherDir = CaeserSkinManager.getLauncherDir();
            if (launcherDir != null) {
                return launcherDir.resolve("emote_unlocks.json");
            }
        } catch (Throwable ignored) {}
        return Path.of("emote_unlocks.json");
    }

    private static void saveToCache(String jsonStr) {
        try {
            Path p = getCachePath();
            if (p.getParent() != null && !Files.exists(p.getParent())) {
                Files.createDirectories(p.getParent());
            }
            Files.writeString(p, jsonStr, StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
    }

    private static void loadFromCache() {
        try {
            Path p = getCachePath();
            if (Files.exists(p)) {
                String content = Files.readString(p, StandardCharsets.UTF_8);
                UnlockData parsed = parseJson(content);
                if (parsed != null) {
                    currentData = parsed;
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Checks if the currently active Minecraft player has unlocked the given emote.
     */
    public static boolean isEmoteUnlocked(EmoteType emote) {
        if (emote == null || emote == EmoteType.NONE || emote == EmoteType.STOP) {
            return true;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        String username = null;
        String uuidStr = null;

        if (client.player != null) {
            username = client.player.getName().getString();
            if (client.player.getUuid() != null) {
                uuidStr = client.player.getUuid().toString();
            }
        } else if (client.getSession() != null) {
            username = client.getSession().getUsername();
            try {
                UUID u = client.getSession().getUuidOrNull();
                if (u != null) uuidStr = u.toString();
            } catch (Throwable ignored) {}
        }

        return isEmoteUnlocked(emote, username, uuidStr);
    }

    public static boolean isEmoteUnlocked(EmoteType emote, String username, String uuidStr) {
        if (emote == null || emote == EmoteType.NONE || emote == EmoteType.STOP) {
            return true;
        }

        UnlockData data = currentData;
        String emoteName = emote.name().toUpperCase(Locale.ROOT);

        // 1. Check default free unlocked emotes
        if (data.defaultUnlocked.contains(emoteName) ||
            data.defaultUnlocked.contains("*") ||
            data.defaultUnlocked.contains("ALL")) {
            return true;
        }

        String cleanUser = cleanKey(username);
        String cleanUuid = cleanKey(uuidStr);

        // 2. Check Admin role (Admins always have 100% of all emotes unlocked)
        if (!cleanUser.isEmpty() && data.admins.contains(cleanUser)) {
            return true;
        }
        if (!cleanUuid.isEmpty() && data.admins.contains(cleanUuid)) {
            return true;
        }

        // 3. Check player-specific unlocks by username
        if (!cleanUser.isEmpty()) {
            Set<String> unlocked = data.playerEmotes.get(cleanUser);
            if (unlocked != null) {
                if (unlocked.contains("*") || unlocked.contains("ALL") || unlocked.contains(emoteName)) {
                    return true;
                }
            }
        }

        // 4. Check player-specific unlocks by UUID
        if (!cleanUuid.isEmpty()) {
            Set<String> unlocked = data.playerEmotes.get(cleanUuid);
            if (unlocked != null) {
                if (unlocked.contains("*") || unlocked.contains("ALL") || unlocked.contains(emoteName)) {
                    return true;
                }
            }
        }

        return false;
    }
}
