package com.bame.client.render;

import com.bame.client.module.KeyStrokesModule;
import com.bame.client.gui.CustomGuiUtils;
import net.minecraft.client.gl.RenderPipelines;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

public class KeyStrokesRenderer implements HudRenderCallback {
    private static final Identifier TOOLTIP_BG = Identifier.ofVanilla("tooltip/background");
    private static final Identifier TOOLTIP_FRAME = Identifier.ofVanilla("tooltip/frame");

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!com.bame.client.module.ShowHudModule.enabled || !KeyStrokesModule.enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        float s = KeyStrokesModule.scale;

        // Clamp the drawn position (render-time only) so the whole key layout stays on screen.
        int drawX = KeyStrokesModule.hudX;
        int drawY = KeyStrokesModule.hudY;
        if (!KeyStrokesModule.keys.isEmpty()) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
            for (KeyStrokesModule.KeyStroke key : KeyStrokesModule.keys) {
                minX = Math.min(minX, key.relX);
                minY = Math.min(minY, key.relY);
                maxX = Math.max(maxX, key.relX + key.width);
                maxY = Math.max(maxY, key.relY + key.height);
            }
            int screenW = client.getWindow().getScaledWidth();
            int screenH = client.getWindow().getScaledHeight();
            int loX = (int) Math.ceil(-minX * s);
            int hiX = (int) Math.floor(screenW - maxX * s);
            int loY = (int) Math.ceil(-minY * s);
            int hiY = (int) Math.floor(screenH - maxY * s);
            drawX = Math.max(loX, Math.min(drawX, hiX));
            drawY = Math.max(loY, Math.min(drawY, hiY));
        }

        drawContext.getMatrices().pushMatrix();
        drawContext.getMatrices().translate((float)drawX, (float)drawY);
        drawContext.getMatrices().scale(s, s);

        int chromaColor = 0;
        if (KeyStrokesModule.bgMode == 2) {
            long time = System.currentTimeMillis();
            float hue = (time % 3000L) / 3000.0f;
            chromaColor = 0xD0000000 | (java.awt.Color.HSBtoRGB(hue, 0.75f, 0.9f) & 0xFFFFFF);
        }

        for (KeyStrokesModule.KeyStroke key : KeyStrokesModule.keys) {
            drawKey(drawContext, key.getBinding(), key.relX, key.relY, key.width, key.height, key.name, KeyStrokesModule.bgMode, KeyStrokesModule.bgColor, chromaColor);
        }
        
        drawContext.getMatrices().popMatrix();
    }

    private void drawKey(DrawContext context, KeyBinding key, int x, int y, int w, int h, String name, int bgMode, int bgColor, int chromaColor) {
        boolean pressed = key != null && key.isPressed();
        int textColor = pressed ? 0xFF000000 : 0xFFFFFFFF;
        
        if (pressed) {
            CustomGuiUtils.fillUltraRounded(context, x, y, w, h, 0x88FFFFFF, 3);
        } else {
            StatusHudRenderer.drawBoxBg(context, x, y, w, h, bgMode, KeyStrokesModule.outlineColor);
        }

        
        MinecraftClient client = MinecraftClient.getInstance();
        int textWidth = client.textRenderer.getWidth(name);
        context.drawText(client.textRenderer, name, x + (w - textWidth) / 2, y + (h - 8) / 2, textColor, true);
    }
}
