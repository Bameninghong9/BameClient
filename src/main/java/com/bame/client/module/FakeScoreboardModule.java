package com.bame.client.module;

import net.minecraft.client.MinecraftClient;

public class FakeScoreboardModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;
    public static int hudX = -1;
    public static int hudY = 100;
    public static float scale = 1.0f;
    public static int bgMode = 0; // 0 = Dark, 1 = Transparent, 2 = Rainbow, 3 = Theme

    public static String money = "670T";
    public static String stars = "2.5K";
    public static String kills = "5283";
    public static String deaths = "2983";
    public static String playtime = "10d 22h";

    public static String getDisplayName() {
        if (NameProtectModule.alias != null && !NameProtectModule.alias.trim().isEmpty()) {
            return NameProtectModule.alias.trim();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            return client.player.getName().getString();
        }
        return "VELTR0";
    }
}
