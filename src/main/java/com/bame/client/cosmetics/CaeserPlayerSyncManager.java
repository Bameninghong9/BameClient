package com.bame.client.cosmetics;

import com.bame.client.emotes.EmoteType;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiplayer synchronization of cosmetics and emotes between all players running Caeser Client.
 * Connects automatically to public peer sync and broadcasts loadouts & active animations.
 */
public class CaeserPlayerSyncManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserPlayerSync");
    private static final String TOPIC = "caeser/sync/global";

    public static class PlayerCosmeticsData {
        public String username;
        public String wings = "none";
        public String head = "none";
        public String pet = "none";
        public String petCustomPlayer = "";
        public final Set<String> accessories = ConcurrentHashMap.newKeySet();
        public long lastUpdated = System.currentTimeMillis();

        public boolean hasAny() {
            return !"none".equalsIgnoreCase(wings) ||
                   !"none".equalsIgnoreCase(head) ||
                   !"none".equalsIgnoreCase(pet) ||
                   !accessories.isEmpty();
        }
    }

    public static class PlayerEmoteData {
        public String username;
        public EmoteType emote = EmoteType.NONE;
        public long startTime = 0L;

        public float getElapsedSeconds() {
            if (emote == null || emote == EmoteType.NONE || emote == EmoteType.STOP) return 0.0f;
            return (System.currentTimeMillis() - startTime) / 1000.0f;
        }

        public boolean isExpired() {
            if (emote == null || emote == EmoteType.NONE || emote == EmoteType.STOP) return true;
            if (emote == EmoteType.COIN_FLIP && getElapsedSeconds() > 4.0f) return true;
            if (emote == EmoteType.WAVE && getElapsedSeconds() > 3.5f) return true;
            // For looping/continuous emotes, safety timeout of 90 seconds
            return getElapsedSeconds() > 90.0f;
        }
    }

    private static final Map<String, PlayerCosmeticsData> COSMETICS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, PlayerEmoteData> EMOTE_MAP = new ConcurrentHashMap<>();

    private static CaeserMqttClient mqttClient;
    private static long lastHeartbeat = 0L;
    private static volatile boolean initialized = false;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        String clientId = "Caeser_" + (System.currentTimeMillis() % 10000000L);
        mqttClient = new CaeserMqttClient("broker.hivemq.com", 1883, clientId, TOPIC, CaeserPlayerSyncManager::handleIncomingMessage);
        mqttClient.start();
        LOGGER.info("Caeser Player Sync Manager initialized.");
    }

    public static PlayerCosmeticsData getCosmetics(String username) {
        if (username == null || username.isBlank()) return null;
        return COSMETICS_MAP.get(username.toLowerCase(Locale.ROOT));
    }

    public static PlayerEmoteData getActiveEmote(String username) {
        if (username == null || username.isBlank()) return null;
        PlayerEmoteData data = EMOTE_MAP.get(username.toLowerCase(Locale.ROOT));
        if (data != null && data.isExpired()) {
            EMOTE_MAP.remove(username.toLowerCase(Locale.ROOT));
            return null;
        }
        return data;
    }

    public static void onServerJoin() {
        // Send initial cosmetics announcement upon joining a world
        broadcastLocalCosmetics();
    }

    public static void tick(MinecraftClient client) {
        if (client == null || client.player == null) return;

        long now = System.currentTimeMillis();
        // Send periodic heartbeat every 20 seconds so newly joined players immediately receive our cosmetics
        if (now - lastHeartbeat > 20_000L) {
            lastHeartbeat = now;
            broadcastLocalCosmetics();
        }
    }

    public static void broadcastLocalCosmetics() {
        if (mqttClient == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;
        String localUsername = mc.getSession().getUsername();
        if (localUsername == null || localUsername.isBlank()) return;

        try {
            JsonObject json = new JsonObject();
            json.addProperty("v", 1);
            json.addProperty("type", "COSMETICS");
            json.addProperty("name", localUsername);
            json.addProperty("wings", CosmeticsConfig.wings != null ? CosmeticsConfig.wings : "none");
            json.addProperty("head", CosmeticsConfig.head != null ? CosmeticsConfig.head : "none");
            json.addProperty("pet", CosmeticsConfig.pet != null ? CosmeticsConfig.pet : "none");
            json.addProperty("petCustom", CosmeticsConfig.petCustomPlayer != null ? CosmeticsConfig.petCustomPlayer : "");

            JsonArray accArray = new JsonArray();
            for (String acc : CosmeticsConfig.accessories) {
                accArray.add(acc);
            }
            json.add("acc", accArray);

            mqttClient.publish(TOPIC, json.toString());
        } catch (Throwable t) {
            LOGGER.debug("Failed to broadcast local cosmetics: {}", t.getMessage());
        }
    }

    public static void broadcastLocalEmote(EmoteType emote, boolean isStop) {
        if (mqttClient == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;
        String localUsername = mc.getSession().getUsername();
        if (localUsername == null || localUsername.isBlank()) return;

        try {
            JsonObject json = new JsonObject();
            json.addProperty("v", 1);
            json.addProperty("type", "EMOTE");
            json.addProperty("name", localUsername);
            json.addProperty("emote", (emote != null) ? emote.name() : "STOP");
            json.addProperty("action", isStop ? "STOP" : "PLAY");
            json.addProperty("time", System.currentTimeMillis());

            mqttClient.publish(TOPIC, json.toString());
        } catch (Throwable t) {
            LOGGER.debug("Failed to broadcast local emote: {}", t.getMessage());
        }
    }

    private static void handleIncomingMessage(String topic, String payload) {
        if (payload == null || payload.isBlank()) return;

        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            String type = root.has("type") ? root.get("type").getAsString() : "";
            String name = root.has("name") ? root.get("name").getAsString() : "";

            if (name.isBlank()) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getSession() != null && name.equalsIgnoreCase(mc.getSession().getUsername())) {
                // Ignore our own echo from the broker
                return;
            }

            String key = name.toLowerCase(Locale.ROOT);

            if ("COSMETICS".equalsIgnoreCase(type)) {
                PlayerCosmeticsData data = COSMETICS_MAP.computeIfAbsent(key, k -> {
                    PlayerCosmeticsData d = new PlayerCosmeticsData();
                    d.username = name;
                    return d;
                });

                data.wings = root.has("wings") ? root.get("wings").getAsString() : "none";
                data.head = root.has("head") ? root.get("head").getAsString() : "none";
                data.pet = root.has("pet") ? root.get("pet").getAsString() : "none";
                data.petCustomPlayer = root.has("petCustom") ? root.get("petCustom").getAsString() : "";
                data.lastUpdated = System.currentTimeMillis();

                data.accessories.clear();
                if (root.has("acc") && root.get("acc").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("acc")) {
                        data.accessories.add(el.getAsString().toLowerCase(Locale.ROOT));
                    }
                }
            } else if ("EMOTE".equalsIgnoreCase(type)) {
                String action = root.has("action") ? root.get("action").getAsString() : "PLAY";
                String emoteStr = root.has("emote") ? root.get("emote").getAsString() : "";

                if ("STOP".equalsIgnoreCase(action) || "STOP".equalsIgnoreCase(emoteStr)) {
                    EMOTE_MAP.remove(key);
                } else {
                    EmoteType emType = EmoteType.fromId(emoteStr);
                    if (emType != null && emType != EmoteType.NONE && emType != EmoteType.STOP) {
                        PlayerEmoteData emData = new PlayerEmoteData();
                        emData.username = name;
                        emData.emote = emType;
                        emData.startTime = root.has("time") ? root.get("time").getAsLong() : System.currentTimeMillis();
                        EMOTE_MAP.put(key, emData);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Error processing sync message: {}", t.getMessage());
        }
    }
}
