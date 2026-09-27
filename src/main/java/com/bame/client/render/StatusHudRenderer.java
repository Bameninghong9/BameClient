package com.bame.client.render;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.module.CpsModule;
import com.bame.client.module.FpsModule;
import com.bame.client.module.PingModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;

public class StatusHudRenderer implements HudRenderCallback {
    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        if (FpsModule.enabled) {
            renderFps(context, FpsModule.hudX, FpsModule.hudY, FpsModule.scale);
        }

        if (PingModule.enabled) {
            renderPing(context, PingModule.hudX, PingModule.hudY, PingModule.scale);
        }

        if (CpsModule.enabled) {
            renderCps(context, CpsModule.hudX, CpsModule.hudY, CpsModule.scale);
        }
    }

    public static int getFpsWidth(MinecraftClient client) {
        String text = client.getCurrentFps() + " FPS";
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    public static int getPingWidth(MinecraftClient client) {
        int ping = getPingValue(client);
        String text = ping + " ms";
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    public static int getCpsWidth(MinecraftClient client) {
        int l = CpsModule.getLeftCps();
        int r = CpsModule.getRightCps();
        String text = l + " | " + r + " CPS";
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    private static int getPingValue(MinecraftClient client) {
        if (client.getNetworkHandler() != null && client.player != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (entry != null) {
                return entry.getLatency();
            }
        }
        return 0;
    }

    public static void renderFps(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = client.getCurrentFps() + " FPS";
        renderPill(c, x, y, scale, text, FpsModule.bgMode, FpsModule.bgColor);
    }

    public static void renderPing(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        int ping = getPingValue(client);
        String text = ping + " ms";
        renderPill(c, x, y, scale, text, PingModule.bgMode, PingModule.bgColor);
    }

    public static void renderCps(DrawContext c, int x, int y, float scale) {
        int l = CpsModule.getLeftCps();
        int r = CpsModule.getRightCps();
        String text = l + " | " + r + " CPS";
        renderPill(c, x, y, scale, text, CpsModule.bgMode, CpsModule.bgColor);
    }

    private static void renderPill(DrawContext c, int x, int y, float scale, String text, int bgMode, int bgColor) {
        MinecraftClient client = MinecraftClient.getInstance();
        int textWidth = client.textRenderer.getWidth(CustomGuiUtils.getFontText(text));
        int w = textWidth + 16;
        int h = 18;

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        int fill = 0xD012161E;
        int outline = 0xFF292D36;

        if (bgMode == 1) { // Transparent
            fill = 0x00000000;
            outline = 0x44FFFFFF;
        } else if (bgMode == 2) { // Color
            fill = bgColor;
            outline = 0xFF292D36;
        } else if (bgMode == 3) { // Chroma
            long time = System.currentTimeMillis();
            float hue = (time % 3000L) / 3000.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.75f, 0.9f);
            fill = 0xD0000000 | (rgb & 0xFFFFFF);
            outline = 0xFF000000 | (rgb & 0xFFFFFF);
        }

        if (fill != 0) {
            CustomGuiUtils.fillUltraRounded(c, 0, 0, w, h, fill, 4);
        }
        CustomGuiUtils.drawUltraRoundedOutline(c, 0, 0, w, h, outline, 4);
        c.drawText(client.textRenderer, CustomGuiUtils.getFontText(text), 8, (h - 8) / 2, 0xFFFFFFFF, false);

        c.getMatrices().popMatrix();
    }
}
