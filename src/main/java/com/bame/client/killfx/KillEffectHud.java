package com.bame.client.killfx;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * Bildschirm-Teil der Kill-Effekte (nur für den Täter): kurzes, halbtransparentes weißes Aufblitzen am Bildschirmrand
 * (Blitz, höchstens 150 ms, die Mitte bleibt frei). Kill-Effekte zeigen nie Text auf dem Bildschirm.
 */
public final class KillEffectHud implements HudRenderCallback {

    private static final long FLASH_MS = 140L;

    private static long flashStart = -1L;

    static void flash() {
        flashStart = System.currentTimeMillis();
    }

    static void clear() {
        flashStart = -1L;
    }

    @Override
    public void onHudRender(DrawContext c, RenderTickCounter tickCounter) {
        if (flashStart < 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null) return;
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        long now = System.currentTimeMillis();

        if (flashStart >= 0) {
            long t = now - flashStart;
            if (t > FLASH_MS || t < 0) {
                flashStart = -1L;
            } else {
                float strength = 1.0F - t / (float) FLASH_MS;
                drawEdgeFlash(c, w, h, strength);
            }
        }

    }

    /** Weißer Rand, nach innen auslaufend (Mitte bleibt frei), maximal halbtransparent. */
    private static void drawEdgeFlash(DrawContext c, int w, int h, float strength) {
        int steps = 16;
        int depthX = Math.max(8, w / 9);
        int depthY = Math.max(8, h / 9);
        for (int i = 0; i < steps; i++) {
            float f = 1.0F - i / (float) steps;
            int alpha = (int) (0x70 * strength * f * f);
            if (alpha <= 0) continue;
            int color = (alpha << 24) | 0xFFFFFF;
            int ox = depthX * i / steps, ox2 = depthX * (i + 1) / steps;
            int oy = depthY * i / steps, oy2 = depthY * (i + 1) / steps;
            c.fill(ox, oy, w - ox, oy2, color);                // oben
            c.fill(ox, h - oy2, w - ox, h - oy, color);        // unten
            c.fill(ox, oy2, ox2, h - oy2, color);              // links
            c.fill(w - ox2, oy2, w - ox, h - oy2, color);      // rechts
        }
    }
}
