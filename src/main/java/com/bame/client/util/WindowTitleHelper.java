package com.bame.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

public class WindowTitleHelper {
    private static String cachedProfileName = null;
    private static String cachedProfileVersion = null;

    public static String getProfileName() {
        if (cachedProfileName != null) return cachedProfileName;

        // 1. Try system property
        String prop = System.getProperty("caeser.profile.name");
        if (prop != null && !prop.trim().isEmpty()) {
            cachedProfileName = prop.trim();
            return cachedProfileName;
        }

        // 2. Try config/CaeserClient/profile.json
        try {
            File runDir = MinecraftClient.getInstance().runDirectory;
            if (runDir != null) {
                File profileJson = new File(runDir, "config/CaeserClient/profile.json");
                if (profileJson.exists()) {
                    try (FileReader reader = new FileReader(profileJson, StandardCharsets.UTF_8)) {
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj.has("name") && !obj.get("name").getAsString().trim().isEmpty()) {
                            cachedProfileName = obj.get("name").getAsString().trim();
                            return cachedProfileName;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        cachedProfileName = "";
        return cachedProfileName;
    }

    public static String getProfileVersion() {
        if (cachedProfileVersion != null) return cachedProfileVersion;

        String prop = System.getProperty("caeser.profile.version");
        if (prop != null && !prop.trim().isEmpty()) {
            cachedProfileVersion = prop.trim();
            return cachedProfileVersion;
        }

        try {
            File runDir = MinecraftClient.getInstance().runDirectory;
            if (runDir != null) {
                File profileJson = new File(runDir, "config/CaeserClient/profile.json");
                if (profileJson.exists()) {
                    try (FileReader reader = new FileReader(profileJson, StandardCharsets.UTF_8)) {
                        JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                        if (obj.has("version") && !obj.get("version").getAsString().trim().isEmpty()) {
                            cachedProfileVersion = obj.get("version").getAsString().trim();
                            return cachedProfileVersion;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            cachedProfileVersion = MinecraftClient.getInstance().getGameVersion();
        } catch (Throwable t) {
            cachedProfileVersion = "1.21.11";
        }
        return cachedProfileVersion;
    }

    public static String formatWindowTitle() {
        String ver = getProfileVersion();
        String name = getProfileName();
        if (name != null && !name.isEmpty()) {
            return "Caeser Client " + ver + " | " + name;
        }
        return "Caeser Client " + ver;
    }
}
