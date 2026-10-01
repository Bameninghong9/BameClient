package com.bame.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ZoomModule {
    public static boolean enabled = true;
    public static int keyBind = GLFW.GLFW_KEY_C;
    public static int mode = 0; // 0 = Smooth, 1 = Instant
    public static int type = 0; // 0 = Hold, 1 = Toggle
    public static boolean scrollZoom = true;
    public static int defaultLevel = 1; // 0 = 2x, 1 = 3x, 2 = 4x, 3 = 6x, 4 = 8x
    public static boolean expanded = false;

    // Zoom state
    public static double currentZoom = 1.0;
    public static double scrollOffset = 0.0;
    public static boolean toggleActive = false;
    private static boolean wasPressed = false;

    public static double getBaseZoomFactor() {
        switch (defaultLevel) {
            case 0: return 0.50;  // 2x
            case 1: return 0.33;  // 3x
            case 2: return 0.25;  // 4x
            case 3: return 0.16;  // 6x
            case 4: return 0.125; // 8x
            default: return 0.33;
        }
    }

    public static String getDefaultZoomName() {
        switch (defaultLevel) {
            case 0: return "2x";
            case 1: return "3x";
            case 2: return "4x";
            case 3: return "6x";
            case 4: return "8x";
            default: return "3x";
        }
    }

    public static void cycleDefaultZoom() {
        defaultLevel = (defaultLevel + 1) % 5;
        scrollOffset = 0.0;
    }

    public static void onTick(MinecraftClient client) {
        if (!enabled || keyBind == -1 || keyBind == GLFW.GLFW_KEY_UNKNOWN) {
            toggleActive = false;
            wasPressed = false;
            scrollOffset = 0.0;
            return;
        }

        if (client.getWindow() == null || client.currentScreen != null) {
            if (type == 0) {
                scrollOffset = 0.0;
            }
            return;
        }

        boolean pressed = InputUtil.isKeyPressed(client.getWindow(), keyBind);

        if (type == 0) { // Hold mode
            if (!pressed && wasPressed) {
                scrollOffset = 0.0;
            }
        } else { // Toggle mode
            if (pressed && !wasPressed) {
                toggleActive = !toggleActive;
                if (!toggleActive) {
                    scrollOffset = 0.0;
                }
            }
        }
        wasPressed = pressed;
    }

    public static boolean isZooming() {
        if (!enabled || keyBind == -1 || keyBind == GLFW.GLFW_KEY_UNKNOWN) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null || client.currentScreen != null) return false;

        if (type == 0) {
            return InputUtil.isKeyPressed(client.getWindow(), keyBind);
        } else {
            return toggleActive;
        }
    }

    public static void onMouseScroll(double vertical) {
        if (!scrollZoom) return;
        scrollOffset += vertical * 0.04;
        double base = getBaseZoomFactor();
        double target = base - scrollOffset;
        if (target < 0.03) {
            scrollOffset = base - 0.03;
        } else if (target > 0.85) {
            scrollOffset = base - 0.85;
        }
    }

    public static double getTargetZoom() {
        if (!isZooming()) {
            return 1.0;
        }
        double base = getBaseZoomFactor();
        double target = base - scrollOffset;
        return Math.max(0.03, Math.min(0.85, target));
    }
}
