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

    private static long lastCheck = 0;
    private static long lastModified = -1;

    public static void setWings(String type) {
        wings = (type == null || type.isBlank()) ? "none" : type;
        save();
    }

    public static void setHead(String type) {
        head = (type == null || type.isBlank()) ? "none" : type;
        save();
    }

    public static void setPet(String type, String customPlayer) {
        pet = (type == null || type.isBlank()) ? "none" : type;
        if (customPlayer != null) {
            petCustomPlayer = customPlayer.trim();
        }
        save();
    }

    public static void setPetCustomPlayer(String customPlayer) {
        petCustomPlayer = (customPlayer != null) ? customPlayer.trim() : "";
        save();
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
            root.add("pet", p);

            Files.writeString(file, root.toString());
            lastModified = Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void checkReload() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1200) return;
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
        } catch (Exception ignored) {}
    }
}
