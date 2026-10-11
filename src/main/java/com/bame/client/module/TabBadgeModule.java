package com.bame.client.module;

public class TabBadgeModule {
    public static boolean enabled = true;
    public static boolean expanded = false;
    public static String badgeType = "c"; // "c", "c_bracket", "c_bold", "bolt", "crown", "fire", "star"
    public static int keyBind = -1;

    public static final String[] BADGE_TYPES = {"c", "c_bracket", "c_bold", "bolt", "crown", "fire", "star"};

    public static String getBadgeName(String type) {
        return switch (type != null ? type.toLowerCase(java.util.Locale.ROOT) : "c") {
            case "c" -> "Caeser (ⓒ)";
            case "c_bracket" -> "Caeser ([C])";
            case "c_bold" -> "Caeser Bold (C)";
            case "bolt" -> "Bolt (⚡)";
            case "crown" -> "Crown (👑)";
            case "fire" -> "Fire (🔥)";
            case "star" -> "Star (✦)";
            default -> "Caeser (ⓒ)";
        };
    }

    public static String getBadgeSymbol() {
        if (!enabled) return null;
        return switch (badgeType != null ? badgeType.toLowerCase(java.util.Locale.ROOT) : "c") {
            case "c" -> "§6ⓒ ";
            case "c_bracket" -> "§6[§eC§6] ";
            case "c_bold" -> "§6§lC §r";
            case "bolt" -> "§b⚡ ";
            case "crown" -> "§e👑 ";
            case "fire" -> "§6🔥 ";
            case "star" -> "§e✦ ";
            case "none" -> null;
            default -> "§6ⓒ ";
        };
    }
}
