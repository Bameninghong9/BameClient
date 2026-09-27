package com.bame.client.module;

import net.minecraft.client.MinecraftClient;

public class FullbrightModule {
    public static boolean enabled = false;
    public static float intensity = 1.0f; // 0.0 to 1.0
    public static int keyBind = -1;
    private static boolean wasKeyBindPressed;

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
            && net.minecraft.client.util.InputUtil.isKeyPressed(client.getWindow(),keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            com.bame.client.BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
    }
}
