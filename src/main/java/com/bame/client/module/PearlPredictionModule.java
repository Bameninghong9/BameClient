package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

public class PearlPredictionModule {
    public static boolean enabled = true;
    public static int keyBind = -1;
    public static boolean expanded = true;

    public static boolean enemyOnly = false;
    public static boolean landingBox = true;
    public static boolean throwPreview = true;

    private static boolean wasKeyBindPressed = false;

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
    }

    public static void resetToDefault() {
        enemyOnly = false;
        landingBox = true;
        throwPreview = true;
        BameClientConfig.save();
    }
}
