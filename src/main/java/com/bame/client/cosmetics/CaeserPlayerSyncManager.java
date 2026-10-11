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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiplayer synchronization of cosmetics and emotes between all players running Caeser Client.
 * Uses both high-speed local peer sync (UDP Multicast on LAN / same-PC) and public MQTT (broker.hivemq.com)
 * for seamless internet sync across all servers.
 */
public class CaeserPlayerSyncManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserPlayerSync");
    private static final String TOPIC = "caeser/sync/global";

    // Unique ID per running Minecraft game instance to prevent echo while allowing dual-instance on same PC
    public static final String LOCAL_INSTANCE_ID = UUID.randomUUID().toString();

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
            return Math.max(0.0f, (System.currentTimeMillis() - startTime) / 1000.0f);
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

        // 1. High-speed local peer sync (UDP Multicast) for same PC / LAN
        CaeserLocalPeerSync.start(payload -> handleIncomingMessage(TOPIC, payload));

        // 2. Global MQTT sync for internet multiplayer
        String clientId = "Caeser_" + LOCAL_INSTANCE_ID.substring(0, 8);
        mqttClient = new CaeserMqttClient("broker.hivemq.com", 1883, clientId, TOPIC, CaeserPlayerSyncManager::handleIncomingMessage);
        mqttClient.start();

        LOGGER.info("Caeser Player Sync Manager initialized with instance ID {}", LOCAL_INSTANCE_ID);
    }

    public static String cleanUsername(String raw) {
        if (raw == null) return null;
        String clean = raw.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        int spaceIdx = clean.lastIndexOf(' ');
        if (spaceIdx >= 0) {
            clean = clean.substring(spaceIdx + 1);
        }
        return clean.toLowerCase(Locale.ROOT);
    }

    public static PlayerCosmeticsData getCosmetics(String username) {
        String key = cleanUsername(username);
        if (key == null || key.isBlank()) return null;
        return COSMETICS_MAP.get(key);
    }

    public static PlayerEmoteData getActiveEmote(String username) {
        String key = cleanUsername(username);
        if (key == null || key.isBlank()) return null;
        PlayerEmoteData data = EMOTE_MAP.get(key);
        if (data != null && data.isExpired()) {
            EMOTE_MAP.remove(key);
            return null;
        }
        return data;
    }

    public static void onServerJoin() {
        // Request existing players to send their cosmetics, and broadcast ours
        sendRequestSync();
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

    public static void sendRequestSync() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;
        String localUsername = mc.getSession().getUsername();
        if (localUsername == null || localUsername.isBlank()) return;

        try {
            JsonObject json = new JsonObject();
            json.addProperty("v", 1);
            json.addProperty("type", "REQUEST_SYNC");
            json.addProperty("instanceId", LOCAL_INSTANCE_ID);
            json.addProperty("name", localUsername);

            String payload = json.toString();
            CaeserLocalPeerSync.broadcast(payload);
            if (mqttClient != null) {
                mqttClient.publish(TOPIC, payload);
            }
        } catch (Throwable ignored) {}
    }

    public static void broadcastLocalCosmetics() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;
        String localUsername = mc.getSession().getUsername();
        if (localUsername == null || localUsername.isBlank()) return;

        try {
            JsonObject json = new JsonObject();
            json.addProperty("v", 1);
            json.addProperty("type", "COSMETICS");
            json.addProperty("instanceId", LOCAL_INSTANCE_ID);
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

            String payload = json.toString();
            CaeserLocalPeerSync.broadcast(payload);
            if (mqttClient != null) {
                mqttClient.publish(TOPIC, payload);
            }
        } catch (Throwable t) {
            LOGGER.debug("Failed to broadcast local cosmetics: {}", t.getMessage());
        }
    }

    public static void broadcastLocalEmote(EmoteType emote, boolean isStop) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return;
        String localUsername = mc.getSession().getUsername();
        if (localUsername == null || localUsername.isBlank()) return;

        try {
            JsonObject json = new JsonObject();
            json.addProperty("v", 1);
            json.addProperty("type", "EMOTE");
            json.addProperty("instanceId", LOCAL_INSTANCE_ID);
            json.addProperty("name", localUsername);
            json.addProperty("emote", (emote != null) ? emote.name() : "STOP");
            json.addProperty("action", isStop ? "STOP" : "PLAY");
            json.addProperty("time", System.currentTimeMillis());

            String payload = json.toString();
            CaeserLocalPeerSync.broadcast(payload);
            if (mqttClient != null) {
                mqttClient.publish(TOPIC, payload);
            }
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
            String senderInstanceId = root.has("instanceId") ? root.get("instanceId").getAsString() : "";

            // Ignore our own echo (using unique instance ID so multiple local instances can communicate)
            if (LOCAL_INSTANCE_ID.equals(senderInstanceId)) {
                return;
            }

            if ("REQUEST_SYNC".equalsIgnoreCase(type)) {
                broadcastLocalCosmetics();
                return;
            }

            if (name.isBlank()) return;
            String key = cleanUsername(name);
            if (key == null || key.isBlank()) return;

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
                        // Always start locally at receipt time for seamless async animation playback
                        emData.startTime = System.currentTimeMillis();
                        EMOTE_MAP.put(key, emData);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Error processing sync message: {}", t.getMessage());
        }
    }
}
