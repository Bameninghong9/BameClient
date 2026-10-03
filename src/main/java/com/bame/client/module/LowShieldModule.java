package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

public class LowShieldModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;

    public static int heightPercent = 50; // 1 to 100%
    public static int totemSizePercent = 50; // 10 to 100%

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
        heightPercent = 50;
        totemSizePercent = 50;
    }
}
