package com.bame.client.gui;

import com.bame.client.module.CpsModule;
import com.bame.client.module.FpsModule;
import com.bame.client.module.KeyStrokesModule;
import com.bame.client.module.KeyStrokesModule.KeyStroke;
import com.bame.client.module.PingModule;
import com.bame.client.render.KeyStrokesRenderer;
import com.bame.client.render.StatusHudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class HudEditorScreen extends Screen {
    private final Screen parent;
    
    private String draggingTarget = NoneTarget; // "keystrokes", "fps", "ping", "cps"
    private String resizingTarget = NoneTarget;
    private static final String NoneTarget = "none";
    private KeyStroke draggingKey = null;
    
    private int dragOffsetX, dragOffsetY;
    private int startX;
    private float startScale;

    private int snapLineX = -1;
    private int snapLineY = -1;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    public void close() {
        com.bame.client.BameClientConfig.save();
        if (client != null) {
            client.setScreen(parent);
        }
    }

    // KeyStrokes bounds helper
    private int getKeyMinX() { int min = Integer.MAX_VALUE; for (KeyStroke k : KeyStrokesModule.keys) min = Math.min(min, k.relX); return min == Integer.MAX_VALUE ? 0 : min; }
    private int getKeyMinY() { int min = Integer.MAX_VALUE; for (KeyStroke k : KeyStrokesModule.keys) min = Math.min(min, k.relY); return min == Integer.MAX_VALUE ? 0 : min; }
    private int getKeyMaxX() { int max = Integer.MIN_VALUE; for (KeyStroke k : KeyStrokesModule.keys) max = Math.max(max, k.relX + k.width); return max == Integer.MIN_VALUE ? 0 : max; }
    private int getKeyMaxY() { int max = Integer.MIN_VALUE; for (KeyStroke k : KeyStrokesModule.keys) max = Math.max(max, k.relY + k.height); return max == Integer.MIN_VALUE ? 0 : max; }

    private boolean inside(double mx, double my, double x, double y, double w, double h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Darken background slightly
        context.fill(0, 0, width, height, 0x44000000);

        snapLineX = -1;
        snapLineY = -1;

        // Draw snap lines if active
        if (!draggingTarget.equals(NoneTarget) || draggingKey != null) {
            int cx = width / 2;
            int cy = height / 2;
            context.fill(cx, 0, cx + 1, height, 0x44FFFFFF);
            context.fill(0, cy, width, cy + 1, 0x44FFFFFF);
        }

        // 1. Render KeyStrokes
        if (KeyStrokesModule.enabled) {
            int minX = getKeyMinX(); int minY = getKeyMinY();
            int maxX = getKeyMaxX(); int maxY = getKeyMaxY();
            int gw = maxX - minX; int gh = maxY - minY;
            float s = KeyStrokesModule.scale;
            int drawX = KeyStrokesModule.hudX; int drawY = KeyStrokesModule.hudY;

            boolean hover = inside(mouseX, mouseY, drawX + (minX - 4)*s, drawY + (minY - 4)*s, (gw + 8) * s, (gh + 8) * s);
            
            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float)drawX, (float)drawY);
            context.getMatrices().scale(s, s);

            if (!KeyStrokesModule.keys.isEmpty() && (hover || draggingTarget.equals("keystrokes") || resizingTarget.equals("keystrokes") || draggingKey != null)) {
                context.fill(minX - 4, minY - 4, maxX + 4, maxY + 4, 0x22FFFFFF);
                // X button
                context.getMatrices().pushMatrix();
                context.getMatrices().translate((float)(maxX - 2), (float)(minY + 2));
                context.getMatrices().rotate((float)Math.toRadians(45));
                context.fill(-4, -1, 4, 1, 0xFFFF5555);
                context.fill(-1, -4, 1, 4, 0xFFFF5555);
                context.getMatrices().popMatrix();
                // Resize chevron
                context.fill(maxX - 4, maxY + 2, maxX + 4, maxY + 4, 0xFFFFFFFF);
                context.fill(maxX + 2, maxY - 4, maxX + 4, maxY + 4, 0xFFFFFFFF);
            }

            for (KeyStroke k : KeyStrokesModule.keys) {
                boolean hoverKey = inside(mouseX, mouseY, drawX + k.relX * s, drawY + k.relY * s, k.width * s, k.height * s);
                int color = hoverKey ? 0x88AAAAAA : 0x88000000;
                context.fill(k.relX, k.relY, k.relX + k.width, k.relY + k.height, color);
                int tw = client.textRenderer.getWidth(k.name);
                context.drawText(client.textRenderer, k.name, k.relX + (k.width - tw) / 2, k.relY + (k.height - 8) / 2, 0xFFFFFFFF, false);
                if (hoverKey) context.fill(k.relX + 2, k.relY + 4, k.relX + 7, k.relY + 5, 0xFFFF5555);
            }
            context.getMatrices().popMatrix();
        }

        // 2. Render FPS Module
        if (FpsModule.enabled) {
            int w = StatusHudRenderer.getFpsWidth(client); int h = 18;
            float s = FpsModule.scale;
            int x = FpsModule.hudX; int y = FpsModule.hudY;
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);
            
            StatusHudRenderer.renderFps(context, x, y, s);
            if (hover || draggingTarget.equals("fps") || resizingTarget.equals("fps")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 3. Render Ping Module
        if (PingModule.enabled) {
            int w = StatusHudRenderer.getPingWidth(client); int h = 18;
            float s = PingModule.scale;
            int x = PingModule.hudX; int y = PingModule.hudY;
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);
            
            StatusHudRenderer.renderPing(context, x, y, s);
            if (hover || draggingTarget.equals("ping") || resizingTarget.equals("ping")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 4. Render CPS Module
        if (CpsModule.enabled) {
            int w = StatusHudRenderer.getCpsWidth(client); int h = 18;
            float s = CpsModule.scale;
            int x = CpsModule.hudX; int y = CpsModule.hudY;
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);
            
            StatusHudRenderer.renderCps(context, x, y, s);
            if (hover || draggingTarget.equals("cps") || resizingTarget.equals("cps")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        if (snapLineX != -1) context.fill(snapLineX, 0, snapLineX + 1, height, 0xFF00FF00);
        if (snapLineY != -1) context.fill(0, snapLineY, width, snapLineY + 1, 0xFF00FF00);

        context.drawText(client.textRenderer, "Press ESC to save and return", width / 2 - 70, 10, 0xFFFFFFFF, true);
    }

    private void drawBoundingControls(DrawContext context, int x, int y, int w, int h, float scale) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)x, (float)y);
        context.getMatrices().scale(scale, scale);

        context.fill(-2, -2, w + 2, h + 2, 0x22FFFFFF);
        // X button
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)(w - 2), 2f);
        context.getMatrices().rotate((float)Math.toRadians(45));
        context.fill(-4, -1, 4, 1, 0xFFFF5555);
        context.fill(-1, -4, 1, 4, 0xFFFF5555);
        context.getMatrices().popMatrix();

        // Resize chevron
        context.fill(w - 4, h + 2, w + 4, h + 4, 0xFFFFFFFF);
        context.fill(w + 2, h - 4, w + 4, h + 4, 0xFFFFFFFF);

        context.getMatrices().popMatrix();
    }

    private int snap(int value, int target) {
        if (Math.abs(value - target) < 5) return target;
        return value;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean twice) {
        double mouseX = click.x(); double mouseY = click.y();
        if (click.button() != 0) return false;

        // 1. Check KeyStrokes
        if (KeyStrokesModule.enabled) {
            float s = KeyStrokesModule.scale;
            int minX = getKeyMinX(); int minY = getKeyMinY();
            int maxX = getKeyMaxX(); int maxY = getKeyMaxY();
            int gw = maxX - minX; int gh = maxY - minY;
            int drawX = KeyStrokesModule.hudX; int drawY = KeyStrokesModule.hudY;

            if (inside(mouseX, mouseY, drawX + maxX * s - 10 * s, drawY + minY * s - 4 * s, 14 * s, 14 * s)) {
                KeyStrokesModule.enabled = false; return true;
            }
            if (inside(mouseX, mouseY, drawX + maxX * s - 10 * s, drawY + maxY * s - 10 * s, 14 * s, 14 * s)) {
                resizingTarget = "keystrokes"; startX = (int)mouseX; startScale = KeyStrokesModule.scale; return true;
            }
            for (int i = 0; i < KeyStrokesModule.keys.size(); i++) {
                KeyStroke k = KeyStrokesModule.keys.get(i);
                if (inside(mouseX, mouseY, drawX + k.relX * s, drawY + k.relY * s, k.width * s, k.height * s)) {
                    if (inside(mouseX, mouseY, drawX + k.relX * s, drawY + k.relY * s, 10 * s, 10 * s)) {
                        KeyStrokesModule.keys.remove(i); return true;
                    }
                    draggingKey = k;
                    dragOffsetX = (int)mouseX - (int)(drawX + k.relX * s);
                    dragOffsetY = (int)mouseY - (int)(drawY + k.relY * s);
                    return true;
                }
            }
            if (inside(mouseX, mouseY, drawX + (minX - 4) * s, drawY + (minY - 4) * s, (gw + 8) * s, (gh + 8) * s)) {
                draggingTarget = "keystrokes";
                dragOffsetX = (int)mouseX - drawX; dragOffsetY = (int)mouseY - drawY;
                return true;
            }
        }

        // 2. Check FPS
        if (FpsModule.enabled) {
            int w = StatusHudRenderer.getFpsWidth(client); int h = 18; float s = FpsModule.scale;
            int x = FpsModule.hudX; int y = FpsModule.hudY;
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
                FpsModule.enabled = false; return true;
            }
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y + h * s - 10 * s, 14 * s, 14 * s)) {
                resizingTarget = "fps"; startX = (int)mouseX; startScale = FpsModule.scale; return true;
            }
            if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                draggingTarget = "fps"; dragOffsetX = (int)mouseX - x; dragOffsetY = (int)mouseY - y; return true;
            }
        }

        // 3. Check Ping
        if (PingModule.enabled) {
            int w = StatusHudRenderer.getPingWidth(client); int h = 18; float s = PingModule.scale;
            int x = PingModule.hudX; int y = PingModule.hudY;
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
                PingModule.enabled = false; return true;
            }
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y + h * s - 10 * s, 14 * s, 14 * s)) {
                resizingTarget = "ping"; startX = (int)mouseX; startScale = PingModule.scale; return true;
            }
            if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                draggingTarget = "ping"; dragOffsetX = (int)mouseX - x; dragOffsetY = (int)mouseY - y; return true;
            }
        }

        // 4. Check CPS
        if (CpsModule.enabled) {
            int w = StatusHudRenderer.getCpsWidth(client); int h = 18; float s = CpsModule.scale;
            int x = CpsModule.hudX; int y = CpsModule.hudY;
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
                CpsModule.enabled = false; return true;
            }
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y + h * s - 10 * s, 14 * s, 14 * s)) {
                resizingTarget = "cps"; startX = (int)mouseX; startScale = CpsModule.scale; return true;
            }
            if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                draggingTarget = "cps"; dragOffsetX = (int)mouseX - x; dragOffsetY = (int)mouseY - y; return true;
            }
        }

        return super.mouseClicked(click, twice);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        double mouseX = click.x(); double mouseY = click.y();

        if (!resizingTarget.equals(NoneTarget)) {
            float diff = (float)(mouseX - startX);
            float newScale = Math.max(0.5f, Math.min(3.0f, startScale + diff / 100.0f));
            switch (resizingTarget) {
                case "keystrokes" -> KeyStrokesModule.scale = newScale;
                case "fps" -> FpsModule.scale = newScale;
                case "ping" -> PingModule.scale = newScale;
                case "cps" -> CpsModule.scale = newScale;
            }
            return true;
        }

        if (draggingKey != null) {
            float s = KeyStrokesModule.scale;
            int newX = (int)((mouseX - dragOffsetX - KeyStrokesModule.hudX) / s);
            int newY = (int)((mouseY - dragOffsetY - KeyStrokesModule.hudY) / s);
            draggingKey.relX = Math.round(newX / 2f) * 2;
            draggingKey.relY = Math.round(newY / 2f) * 2;
            return true;
        }

        if (!draggingTarget.equals(NoneTarget)) {
            int newX = (int)mouseX - dragOffsetX;
            int newY = (int)mouseY - dragOffsetY;

            int gw = 60; int gh = 18; float s = 1.0f;
            switch (draggingTarget) {
                case "keystrokes" -> { gw = (int)((getKeyMaxX() - getKeyMinX()) * KeyStrokesModule.scale); gh = (int)((getKeyMaxY() - getKeyMinY()) * KeyStrokesModule.scale); s = KeyStrokesModule.scale; }
                case "fps" -> { gw = (int)(StatusHudRenderer.getFpsWidth(client) * FpsModule.scale); gh = (int)(18 * FpsModule.scale); s = FpsModule.scale; }
                case "ping" -> { gw = (int)(StatusHudRenderer.getPingWidth(client) * PingModule.scale); gh = (int)(18 * PingModule.scale); s = PingModule.scale; }
                case "cps" -> { gw = (int)(StatusHudRenderer.getCpsWidth(client) * CpsModule.scale); gh = (int)(18 * CpsModule.scale); s = CpsModule.scale; }
            }

            int centerX = width / 2; int centerY = height / 2;
            int snapX = snap(newX + gw / 2, centerX);
            if (snapX == centerX) { newX = centerX - gw / 2; snapLineX = centerX; }
            int snapY = snap(newY + gh / 2, centerY);
            if (snapY == centerY) { newY = centerY - gh / 2; snapLineY = centerY; }

            newX = Math.max(0, Math.min(newX, width - gw));
            newY = Math.max(0, Math.min(newY, height - gh));

            switch (draggingTarget) {
                case "keystrokes" -> { KeyStrokesModule.hudX = newX; KeyStrokesModule.hudY = newY; }
                case "fps" -> { FpsModule.hudX = newX; FpsModule.hudY = newY; }
                case "ping" -> { PingModule.hudX = newX; PingModule.hudY = newY; }
                case "cps" -> { CpsModule.hudX = newX; CpsModule.hudY = newY; }
            }
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        draggingTarget = NoneTarget;
        resizingTarget = NoneTarget;
        draggingKey = null;
        return super.mouseReleased(click);
    }
}
