package com.bame.client.cosmetics;

import com.bame.client.BameClientConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

public class CosmeticsConfig {
    public static String wings = "none";
    public static String head = "none";
    public static String pet = "none";
    public static String petCustomPlayer = "";
    public static final java.util.Set<String> accessories = new java.util.LinkedHashSet<>();
    public static final java.util.Set<String> equippedEmotes = new java.util.LinkedHashSet<>();
    public static final String[] wheelSlots = new String[40];

    public static String getEmoteAtSlot(int slot) {
        if (slot < 0 || slot >= wheelSlots.length) return null;
        return wheelSlots[slot];
    }

    public static int getSlotForEmote(String id) {
        if (id == null || id.isBlank()) return -1;
        String key = id.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < wheelSlots.length; i++) {
            if (key.equals(wheelSlots[i])) return i;
        }
        return -1;
    }

    public static void setEmoteAtSlot(int slot, String id) {
        if (slot < 0 || slot >= wheelSlots.length) return;
        String clean = (id == null || id.isBlank()) ? null : id.toLowerCase(java.util.Locale.ROOT);
        if (clean != null) {
            for (int i = 0; i < wheelSlots.length; i++) {
                if (clean.equals(wheelSlots[i])) {
                    wheelSlots[i] = null;
                }
            }
        }
        wheelSlots[slot] = clean;
        syncEquippedEmotesFromSlots();
        save();
    }

    public static void clearSlot(int slot) {
        if (slot >= 0 && slot < wheelSlots.length) {
            wheelSlots[slot] = null;
            syncEquippedEmotesFromSlots();
            save();
        }
    }

    public static void syncEquippedEmotesFromSlots() {
        equippedEmotes.clear();
        for (String s : wheelSlots) {
            if (s != null && !s.isBlank()) {
                equippedEmotes.add(s);
            }
        }
    }

    public static boolean isEmoteEquipped(String id) {
        if (id == null || id.isBlank()) return false;
        String key = id.toLowerCase(java.util.Locale.ROOT);
        for (String s : wheelSlots) {
            if (key.equals(s)) return true;
        }
        return equippedEmotes.contains(key);
    }

    public static void toggleEmote(String id) {
        if (id == null || id.isBlank()) return;
        String key = id.toLowerCase(java.util.Locale.ROOT);
        int currentSlot = getSlotForEmote(key);
        if (currentSlot >= 0) {
            wheelSlots[currentSlot] = null;
        } else {
            int empty = -1;
            for (int i = 0; i < wheelSlots.length; i++) {
                if (wheelSlots[i] == null) {
                    empty = i;
                    break;
                }
            }
            if (empty >= 0) {
                wheelSlots[empty] = key;
            }
        }
        syncEquippedEmotesFromSlots();
        save();
    }

    private static long lastCheck = 0;
    private static long lastModified = -1;

    static {
        load();
    }

    public static synchronized void load() {
        lastCheck = 0;
        lastModified = -1;
        checkReload();
    }

    public static void setWings(String type) {
        wings = (type == null || type.isBlank()) ? "none" : type;
        save();
    }

    public static void setHead(String type) {
        head = (type == null || type.isBlank()) ? "none" : type;
        save();
    }

    public static boolean isAccessoryEquipped(String id) {
        if (id == null || id.isBlank()) return false;
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if ("self".equals(key) || "custom".equals(key)) key = "minime_sit";
        return accessories.contains(key);
    }

    public static void toggleAccessory(String id) {
        if (id == null || id.isBlank()) return;
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if ("self".equals(key) || "custom".equals(key)) key = "minime_sit";
        if (accessories.contains(key)) {
            accessories.remove(key);
        } else {
            accessories.add(key);
        }
        pet = accessories.isEmpty() ? "none" : accessories.iterator().next();
        save();
    }

    public static void setPet(String type, String customPlayer) {
        pet = (type == null || type.isBlank()) ? "none" : type;
        if (customPlayer != null) {
            petCustomPlayer = customPlayer.trim();
        }
        accessories.clear();
        if (!"none".equalsIgnoreCase(pet)) {
            String key = pet.toLowerCase(java.util.Locale.ROOT);
            if ("self".equals(key) || "custom".equals(key)) key = "minime_sit";
            accessories.add(key);
        }
        save();
    }

    public static final java.util.Map<String, String> accessoryCustomPlayers = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<String, String> accessorySides = new java.util.concurrent.ConcurrentHashMap<>();
    public static final java.util.Map<String, String> petPlacements = new java.util.concurrent.ConcurrentHashMap<>();

    public static String getAccessorySide(String id) {
        if (id == null) return "right";
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if ("minime_hand".equals(key)) {
            return accessorySides.getOrDefault(key, "left");
        }
        return accessorySides.getOrDefault(key, "right");
    }

    public static void setAccessorySide(String id, String side) {
        if (id == null) return;
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if (side == null || side.isBlank()) {
            accessorySides.remove(key);
        } else {
            accessorySides.put(key, side.toLowerCase(java.util.Locale.ROOT));
        }
        save();
    }

    public static String getPetPlacement(String id) {
        if (id == null) return "right_shoulder";
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if ("jelly_slime".equals(key)) {
            return petPlacements.getOrDefault(key, "head");
        }
        return petPlacements.getOrDefault(key, "right_shoulder");
    }

    public static void setPetPlacement(String id, String placement) {
        if (id == null) return;
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if (placement == null || placement.isBlank()) {
            petPlacements.remove(key);
        } else {
            petPlacements.put(key, placement.toLowerCase(java.util.Locale.ROOT));
        }
        save();
    }

    public static String getAccessoryCustomPlayer(String id) {
        if (id == null) return "";
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if ("self".equals(key) || "custom".equals(key)) key = "minime_sit";
        String val = accessoryCustomPlayers.get(key);
        if (val != null) return val;
        if ("minime_sit".equals(key) && petCustomPlayer != null) return petCustomPlayer;
        return "";
    }

    public static void setAccessoryCustomPlayer(String id, String customPlayer) {
        if (id == null) return;
        String key = id.toLowerCase(java.util.Locale.ROOT);
        if ("self".equals(key) || "custom".equals(key)) key = "minime_sit";
        if (customPlayer == null || customPlayer.isBlank()) {
            accessoryCustomPlayers.remove(key);
            if ("minime_sit".equals(key)) petCustomPlayer = "";
        } else {
            accessoryCustomPlayers.put(key, customPlayer.trim());
            if ("minime_sit".equals(key)) petCustomPlayer = customPlayer.trim();
        }
        save();
    }

    public static void setPetCustomPlayer(String customPlayer) {
        setAccessoryCustomPlayer("minime_sit", customPlayer);
    }

    public static void save() {
        try {
            Path file = BameClientConfig.BASE_CONFIG_DIR.resolve("cosmetics.json");
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            JsonObject root = new JsonObject();

            JsonObject w = new JsonObject();
            w.addProperty("type", wings);
            w.addProperty("id", wings);
            root.add("wings", w);

            JsonObject h = new JsonObject();
            h.addProperty("type", head);
            h.addProperty("id", head);
            root.add("head", h);

            JsonObject p = new JsonObject();
            p.addProperty("type", pet);
            p.addProperty("customPlayer", petCustomPlayer != null ? petCustomPlayer : "");
            p.addProperty("id", pet);
            com.google.gson.JsonArray pList = new com.google.gson.JsonArray();
            for (String acc : accessories) {
                pList.add(acc);
            }
            p.add("list", pList);
            root.add("pet", p);

            com.google.gson.JsonArray accArr = new com.google.gson.JsonArray();
            for (String acc : accessories) {
                accArr.add(acc);
            }
            root.add("accessories", accArr);

            JsonObject accPlayers = new JsonObject();
            for (java.util.Map.Entry<String, String> entry : accessoryCustomPlayers.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    accPlayers.addProperty(entry.getKey(), entry.getValue());
                }
            }
            root.add("accessoryCustomPlayers", accPlayers);

            JsonObject sidesObj = new JsonObject();
            for (java.util.Map.Entry<String, String> entry : accessorySides.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    sidesObj.addProperty(entry.getKey(), entry.getValue());
                }
            }
            root.add("accessorySides", sidesObj);

            JsonObject placementsObj = new JsonObject();
            for (java.util.Map.Entry<String, String> entry : petPlacements.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isBlank()) {
                    placementsObj.addProperty(entry.getKey(), entry.getValue());
                }
            }
            root.add("petPlacements", placementsObj);

            com.google.gson.JsonArray emotesArr = new com.google.gson.JsonArray();
            for (String em : equippedEmotes) {
                emotesArr.add(em);
            }
            root.add("emotes", emotesArr);

            com.google.gson.JsonArray wheelArr = new com.google.gson.JsonArray();
            for (String slotEmote : wheelSlots) {
                if (slotEmote == null) {
                    wheelArr.add(com.google.gson.JsonNull.INSTANCE);
                } else {
                    wheelArr.add(slotEmote);
                }
            }
            root.add("wheel_slots", wheelArr);

            Files.writeString(file, root.toString());
            lastModified = Files.getLastModifiedTime(file).toMillis();

            // Also keep launcher settings.json synchronized
            try {
                Path launcherDir = CaeserSkinManager.getLauncherDir();
                if (launcherDir != null) {
                    Path launcherSettings = launcherDir.resolve("settings.json");
                    if (Files.exists(launcherSettings)) {
                        JsonObject launcherObj = JsonParser.parseString(Files.readString(launcherSettings)).getAsJsonObject();
                        launcherObj.add("cosmetics", root);
                        Files.writeString(launcherSettings, launcherObj.toString());
                    }
                }
            } catch (Throwable ignored) {}

            try {
                CaeserPlayerSyncManager.broadcastLocalCosmetics();
            } catch (Throwable ignored) {}
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void checkReload() {
        long now = System.currentTimeMillis();
        if (lastCheck != 0 && now - lastCheck < 1000) return;
        lastCheck = now;

        try {
            Path file = BameClientConfig.BASE_CONFIG_DIR.resolve("cosmetics.json");
            JsonObject root = null;
            if (Files.exists(file)) {
                long mod = Files.getLastModifiedTime(file).toMillis();
                if (mod == lastModified) return;
                lastModified = mod;
                String content = Files.readString(file);
                root = JsonParser.parseString(content).getAsJsonObject();
            } else {
                // Fallback to launcher settings.json if local cosmetics.json doesn't exist yet
                Path launcherSettings = CaeserSkinManager.getLauncherDir().resolve("settings.json");
                if (Files.exists(launcherSettings)) {
                    JsonObject lRoot = JsonParser.parseString(Files.readString(launcherSettings)).getAsJsonObject();
                    if (lRoot.has("cosmetics") && lRoot.get("cosmetics").isJsonObject()) {
                        root = lRoot.getAsJsonObject("cosmetics");
                    }
                }
            }
            if (root == null) return;

            if (root.has("wings")) {
                JsonObject w = root.getAsJsonObject("wings");
                wings = w.has("type") ? w.get("type").getAsString() : "none";
            }
            if (root.has("head")) {
                JsonObject h = root.getAsJsonObject("head");
                head = h.has("type") ? h.get("type").getAsString() : "none";
            }
            if (root.has("pet")) {
                JsonObject p = root.getAsJsonObject("pet");
                pet = p.has("type") ? p.get("type").getAsString() : "none";
                petCustomPlayer = p.has("customPlayer") ? p.get("customPlayer").getAsString() : "";
            }
            if (root.has("accessories") && root.get("accessories").isJsonArray()) {
                accessories.clear();
                for (com.google.gson.JsonElement el : root.getAsJsonArray("accessories")) {
                    String a = el.getAsString().toLowerCase(java.util.Locale.ROOT);
                    if ("self".equals(a) || "custom".equals(a)) a = "minime_sit";
                    accessories.add(a);
                }
                if (!accessories.isEmpty()) {
                    pet = accessories.iterator().next();
                }
            } else if (root.has("pet")) {
                JsonObject p = root.getAsJsonObject("pet");
                accessories.clear();
                if (p.has("list") && p.get("list").isJsonArray()) {
                    for (com.google.gson.JsonElement el : p.getAsJsonArray("list")) {
                        String a = el.getAsString().toLowerCase(java.util.Locale.ROOT);
                        if ("self".equals(a) || "custom".equals(a)) a = "minime_sit";
                        accessories.add(a);
                    }
                } else if (!"none".equalsIgnoreCase(pet)) {
                    String a = pet.toLowerCase(java.util.Locale.ROOT);
                    if ("self".equals(a) || "custom".equals(a)) a = "minime_sit";
                    accessories.add(a);
                }
            }
            if (accessories.isEmpty() && pet != null && !"none".equalsIgnoreCase(pet)) {
                String a = pet.toLowerCase(java.util.Locale.ROOT);
                if ("self".equals(a) || "custom".equals(a)) a = "minime_sit";
                accessories.add(a);
            }
            if (root.has("accessoryCustomPlayers") && root.get("accessoryCustomPlayers").isJsonObject()) {
                accessoryCustomPlayers.clear();
                JsonObject obj = root.getAsJsonObject("accessoryCustomPlayers");
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        String k = e.getKey().toLowerCase(java.util.Locale.ROOT);
                        if ("self".equals(k) || "custom".equals(k)) k = "minime_sit";
                        accessoryCustomPlayers.put(k, e.getValue().getAsString());
                    }
                }
                if (accessoryCustomPlayers.containsKey("minime_sit")) {
                    petCustomPlayer = accessoryCustomPlayers.get("minime_sit");
                }
            } else if (petCustomPlayer != null && !petCustomPlayer.isBlank()) {
                accessoryCustomPlayers.put("minime_sit", petCustomPlayer);
            }

            if (root.has("accessorySides") && root.get("accessorySides").isJsonObject()) {
                accessorySides.clear();
                JsonObject obj = root.getAsJsonObject("accessorySides");
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        accessorySides.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue().getAsString());
                    }
                }
            }

            if (root.has("petPlacements") && root.get("petPlacements").isJsonObject()) {
                petPlacements.clear();
                JsonObject obj = root.getAsJsonObject("petPlacements");
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> e : obj.entrySet()) {
                    if (e.getValue().isJsonPrimitive()) {
                        petPlacements.put(e.getKey().toLowerCase(java.util.Locale.ROOT), e.getValue().getAsString());
                    }
                }
            }

            if (root.has("wheel_slots") && root.get("wheel_slots").isJsonArray()) {
                java.util.Arrays.fill(wheelSlots, null);
                com.google.gson.JsonArray arr = root.getAsJsonArray("wheel_slots");
                for (int i = 0; i < Math.min(arr.size(), wheelSlots.length); i++) {
                    com.google.gson.JsonElement el = arr.get(i);
                    if (el != null && !el.isJsonNull() && el.isJsonPrimitive()) {
                        wheelSlots[i] = el.getAsString().toLowerCase(java.util.Locale.ROOT);
                    }
                }
                syncEquippedEmotesFromSlots();
            } else if (root.has("emotes") && root.get("emotes").isJsonArray()) {
                equippedEmotes.clear();
                java.util.Arrays.fill(wheelSlots, null);
                int slotIdx = 0;
                for (com.google.gson.JsonElement el : root.getAsJsonArray("emotes")) {
                    if (el.isJsonPrimitive()) {
                        String em = el.getAsString().toLowerCase(java.util.Locale.ROOT);
                        equippedEmotes.add(em);
                        if (slotIdx < wheelSlots.length) {
                            wheelSlots[slotIdx++] = em;
                        }
                    }
                }
            }

            for (String user : accessoryCustomPlayers.values()) {
                if (user != null && !user.isBlank()) {
                    PlayerSkinResolver.resolve(user);
                }
            }
        } catch (Exception ignored) {}
    }
}
