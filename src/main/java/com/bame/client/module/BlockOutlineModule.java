package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

public class BlockOutlineModule {
    public static boolean enabled = true;
    public static int keyBind = -1;
    public static boolean expanded = false;

    public static int color = 0xFFB23CEE; // Accent purple/violet default
    public static float lineWidth = 2.5f; // 1.0 to 6.0
    public static boolean chroma = false;
    public static float opacity = 1.0f; // 0.2 to 1.0

    private static boolean wasKeyBindPressed = false;

    public static int getOutlineColor() {
        int baseColor;
        if (chroma) {
            float hue = (float) ((System.currentTimeMillis() % 3000L) / 3000.0);
            baseColor = java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f);
        } else {
            baseColor = color;
        }
        int alpha = Math.clamp(Math.round(opacity * 255.0f), 10, 255);
        return (alpha << 24) | (baseColor & 0x00FFFFFF);
    }

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
        color = 0xFFB23CEE;
        lineWidth = 2.5f;
        chroma = false;
        opacity = 1.0f;
    }
}
