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

            Files.writeString(file, root.toString());
            lastModified = Files.getLastModifiedTime(file).toMillis();
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
            if (!Files.exists(file)) return;
            long mod = Files.getLastModifiedTime(file).toMillis();
            if (mod == lastModified) return;
            lastModified = mod;

            String content = Files.readString(file);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();
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

            for (String user : accessoryCustomPlayers.values()) {
                if (user != null && !user.isBlank()) {
                    PlayerSkinResolver.resolve(user);
                }
            }
        } catch (Exception ignored) {}
    }
}
