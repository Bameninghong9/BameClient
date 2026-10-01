package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class FreelookModule {
    public static boolean enabled = true;
    public static int keyBind = GLFW.GLFW_KEY_V;
    public static boolean expanded = false;

    public static boolean toggleMode = false; // false = Hold, true = Toggle
    public static boolean invertPitch = false;
    public static float sensitivity = 1.0f;

    public static boolean active = false;
    public static float cameraYaw = 0.0f;
    public static float cameraPitch = 0.0f;

    private static Perspective originalPerspective = null;
    private static boolean wasKeyPressed = false;

    public static boolean isActive() {
        return enabled && active;
    }

    public static float getYaw() {
        return cameraYaw;
    }

    public static float getPitch() {
        return cameraPitch;
    }

    public static void onMouseTurn(double dx, double dy) {
        if (!isActive()) return;
        cameraYaw += (float) (dx * 0.15 * sensitivity);
        cameraPitch += (float) ((invertPitch ? -dy : dy) * 0.15 * sensitivity);
        cameraPitch = MathHelper.clamp(cameraPitch, -90.0f, 90.0f);
    }

    public static void start(MinecraftClient client) {
        if (!enabled || active || client.player == null) return;
        active = true;
        originalPerspective = client.options.getPerspective();
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        cameraYaw = client.player.getYaw();
        cameraPitch = client.player.getPitch();
    }

    public static void stop(MinecraftClient client) {
        if (!active) return;
        active = false;
        if (client != null && client.options != null && originalPerspective != null) {
            client.options.setPerspective(originalPerspective);
        }
        originalPerspective = null;
    }

    public static void onTick(MinecraftClient client) {
        if (client == null || client.player == null || client.getWindow() == null) {
            if (active) stop(client);
            return;
        }

        if (client.currentScreen != null) {
            if (active) stop(client);
            return;
        }

        if (!enabled || keyBind == -1) {
            if (active) stop(client);
            return;
        }

        boolean pressed = InputUtil.isKeyPressed(client.getWindow(), keyBind);

        if (!toggleMode) {
            if (pressed && !active) {
                start(client);
            } else if (!pressed && active) {
                stop(client);
            }
        } else {
            if (pressed && !wasKeyPressed) {
                if (active) {
                    stop(client);
                } else {
                    start(client);
                }
            }
        }
        wasKeyPressed = pressed;

        if (active && client.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            stop(client);
        }
    }

    public static void resetToDefault() {
        toggleMode = false;
        invertPitch = false;
        sensitivity = 1.0f;
    }
}
