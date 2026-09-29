package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.Optional;

public class ReachDisplayModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;

    // 0 = Above Crosshair, 1 = HUD Element
    public static int mode = 0;

    public static double lastReach = 0.0;
    public static long lastHitTime = 0;

    // HUD element position and styling (for mode == 1)
    public static int hudX = 10;
    public static int hudY = 200;
    public static float scale = 1.0f;
    public static int bgMode = 0; // 0=dark, 1=transparent, 2=outline
    public static int outlineColor = 0xFFFFFFFF;
    public static int customWidth = -1;
    public static int customHeight = -1;

    private static boolean wasKeyBindPressed = false;

    public static void onAttack(Entity target) {
        if (target == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d eyePos = client.player.getEyePos();
        Box box = target.getBoundingBox().expand(target.getTargetingMargin());
        Vec3d lookVec = client.player.getRotationVec(1.0f);
        Vec3d endPos = eyePos.add(lookVec.multiply(6.0));
        Optional<Vec3d> hit = box.raycast(eyePos, endPos);

        double dist;
        if (hit.isPresent()) {
            dist = eyePos.distanceTo(hit.get());
        } else {
            dist = eyePos.distanceTo(box.getCenter());
        }

        lastReach = dist;
        lastHitTime = System.currentTimeMillis();
    }

    public static String getReachString() {
        if (lastReach <= 0.0) return "0.00 Blocks";
        return String.format(Locale.ROOT, "%.2f Blocks", lastReach);
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
        mode = 0;
        lastReach = 0.0;
        lastHitTime = 0;
        hudX = 10;
        hudY = 200;
        scale = 1.0f;
        bgMode = 0;
        outlineColor = 0xFFFFFFFF;
        customWidth = -1;
        customHeight = -1;
    }
}
