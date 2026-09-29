package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.mixin.OverlayTextureAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.InputUtil;

public class HitColorModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;

    public static int color = 0xFFFF2222; // RGB default
    public static float alpha = 0.65f; // Opacity 0.1 to 1.0

    private static boolean wasKeyBindPressed = false;

    public static void apply() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.gameRenderer != null) {
            updateTexture(client.gameRenderer.getOverlayTexture());
        }
    }

    public static void updateTexture(OverlayTexture overlayTexture) {
        if (overlayTexture == null) return;
        try {
            NativeImageBackedTexture tex = ((OverlayTextureAccessor) overlayTexture).getTexture();
            if (tex == null) return;
            NativeImage img = tex.getImage();
            if (img == null) return;

            int targetColor = -1308622848; // Vanilla Minecraft hurt color (0xB2FF0000 in ARGB)
            if (enabled) {
                int a = Math.round(alpha * 255.0f) & 0xFF;
                int rgb = color & 0x00FFFFFF;
                targetColor = (a << 24) | rgb;
            }

            for (int i = 0; i < 8; i++) {
                for (int j = 0; j < 16; j++) {
                    img.setColorArgb(j, i, targetColor);
                }
            }
            tex.upload();
        } catch (Throwable t) {
            // Ignored if called during early startup
        }
    }

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            apply();
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
    }

    public static void resetToDefault() {
        color = 0xFFFF2222;
        alpha = 0.65f;
        apply();
    }
}
