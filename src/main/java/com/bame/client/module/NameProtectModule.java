package com.bame.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import java.util.Optional;

public class NameProtectModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static String alias = "You";
    public static boolean expanded = false;

    public static String getAlias() {
        return (alias != null && !alias.trim().isEmpty()) ? alias.trim() : "You";
    }

    public static String getRealUsername() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getSession() != null) {
            return client.getSession().getUsername();
        }
        return null;
    }

    public static String getProtectedName(String realName) {
        if (!enabled) return realName;
        return getAlias();
    }

    public static String protect(String text) {
        if (!enabled || text == null) return text;
        String realName = getRealUsername();
        if (realName == null || realName.isEmpty() || !text.contains(realName)) return text;
        return text.replace(realName, getAlias());
    }

    public static Text protect(Text text) {
        if (!enabled || text == null) return text;
        String realName = getRealUsername();
        if (realName == null || realName.isEmpty()) return text;
        String str = text.getString();
        if (!str.contains(realName)) return text;

        String safeAlias = getAlias();
        MutableText result = Text.empty();
        text.visit((style, asString) -> {
            if (asString.contains(realName)) {
                result.append(Text.literal(asString.replace(realName, safeAlias)).setStyle(style));
            } else {
                result.append(Text.literal(asString).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
