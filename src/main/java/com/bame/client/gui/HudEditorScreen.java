package com.bame.client.gui;

import com.bame.client.module.CpsModule;
import com.bame.client.module.FpsModule;
import com.bame.client.module.FakeScoreboardModule;
import com.bame.client.module.KeyStrokesModule;
import com.bame.client.module.KeyStrokesModule.KeyStroke;
import com.bame.client.module.PingModule;
import com.bame.client.module.ServerInfoModule;
import com.bame.client.render.FakeScoreboardRenderer;
import com.bame.client.render.KeyStrokesRenderer;
import com.bame.client.render.StatusHudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class HudEditorScreen extends Screen {
    private final Screen parent;
    
    private String draggingTarget = NoneTarget; // "keystrokes", "fps", "ping", "cps", "serverInfo", "fakeScoreboard"
    private String resizingTarget = NoneTarget;
    private static final String NoneTarget = "none";
    private KeyStroke draggingKey = null;
    
    private int dragOffsetX, dragOffsetY;
    private int startX;
    private float startScale;

    private int snapLineX = -1;
    private int snapLineY = -1;

    private String popupTarget = null;
    private int popupX, popupY;
    private static final int POPUP_W = 120;
    private static final int POPUP_H = 114;

    private void openPopup(String target, int mx, int my) {
        this.popupTarget = target;
        this.popupX = Math.max(10, Math.min(width - POPUP_W - 10, mx));
        this.popupY = Math.max(10, Math.min(height - POPUP_H - 10, my));
    }

    private int getBgMode(String target) {
        return switch (target) {
            case "fps" -> FpsModule.bgMode;
            case "ping" -> PingModule.bgMode;
            case "cps" -> CpsModule.bgMode;
            case "keystrokes" -> KeyStrokesModule.bgMode;
            case "serverInfo" -> ServerInfoModule.bgMode;
            case "fakeScoreboard" -> FakeScoreboardModule.bgMode;
            default -> 0;
        };
    }

    private void setBgMode(String target, int mode) {
        switch (target) {
            case "fps" -> FpsModule.bgMode = mode;
            case "ping" -> PingModule.bgMode = mode;
            case "cps" -> CpsModule.bgMode = mode;
            case "keystrokes" -> KeyStrokesModule.bgMode = mode;
            case "serverInfo" -> ServerInfoModule.bgMode = mode;
            case "fakeScoreboard" -> FakeScoreboardModule.bgMode = mode;
        }
        com.bame.client.BameClientConfig.save();
    }

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

        // 5. Render Server Info Module
        if (ServerInfoModule.enabled) {
            int w = StatusHudRenderer.getServerInfoWidth(client); int h = 18;
            float s = ServerInfoModule.scale;
            int x = ServerInfoModule.hudX;
            if (x == -1) {
                x = width - (int)(w * s) - 10;
                ServerInfoModule.hudX = x;
            }
            int y = ServerInfoModule.hudY;
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            StatusHudRenderer.renderServerInfo(context, x, y, s);
            if (hover || draggingTarget.equals("serverInfo") || resizingTarget.equals("serverInfo")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 6. Render Fake Scoreboard Module
        if (FakeScoreboardModule.enabled) {
            int w = FakeScoreboardRenderer.getWidth(client); int h = FakeScoreboardRenderer.getHeight();
            float s = FakeScoreboardModule.scale;
            int x = FakeScoreboardModule.hudX;
            if (x == -1) {
                x = width - (int)(w * s) - 10;
                FakeScoreboardModule.hudX = x;
            }
            int y = FakeScoreboardModule.hudY;
            if (y == -1) {
                y = (height - (int)(h * s)) / 2;
                FakeScoreboardModule.hudY = y;
            }
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            FakeScoreboardRenderer.render(context, x, y, s);
            if (hover || draggingTarget.equals("fakeScoreboard") || resizingTarget.equals("fakeScoreboard")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        if (snapLineX != -1) context.fill(snapLineX, 0, snapLineX + 1, height, 0xFF00FF00);
        if (snapLineY != -1) context.fill(0, snapLineY, width, snapLineY + 1, 0xFF00FF00);

        context.drawText(client.textRenderer, "Press ESC to save and return", width / 2 - 70, 10, 0xFFFFFFFF, true);

        // Render right-click popup on top of everything
        if (popupTarget != null) {
            CustomGuiUtils.fillUltraRounded(context, popupX, popupY, POPUP_W, POPUP_H, 0xF5141822, 6);
            CustomGuiUtils.drawUltraRoundedOutline(context, popupX, popupY, POPUP_W, POPUP_H, GuiTheme.accent(), 6);

            String title = switch (popupTarget) {
                case "fps" -> "FPS";
                case "ping" -> "Ping";
                case "cps" -> "CPS";
                case "keystrokes" -> "KeyStrokes";
                case "serverInfo" -> "Server Info";
                case "fakeScoreboard" -> "Scoreboard";
                default -> "HUD";
            };
            context.drawText(client.textRenderer, CustomGuiUtils.getFontText(title + " BG"), popupX + 8, popupY + 8, 0xFFFFFFFF, false);
            context.drawText(client.textRenderer, Text.literal("×"), popupX + POPUP_W - 14, popupY + 6, 0xFF8E95A4, false);

            int curBg = getBgMode(popupTarget);
            String[] bgs = {"Dark", "Transparent", "Rainbow", "Theme"};
            for (int i = 0; i < 4; i++) {
                int by = popupY + 24 + i * 21;
                boolean selected = (curBg == i);
                boolean hover = inside(mouseX, mouseY, popupX + 8, by, POPUP_W - 16, 18);
                int fill = selected ? GuiTheme.alpha(GuiTheme.accent(), 90) : (hover ? 0xFF252A34 : 0xFF181C24);
                int outline = selected ? GuiTheme.accent() : (hover ? 0xFF606575 : 0xFF292D36);
                CustomGuiUtils.fillUltraRounded(context, popupX + 8, by, POPUP_W - 16, 18, fill, 4);
                CustomGuiUtils.drawUltraRoundedOutline(context, popupX + 8, by, POPUP_W - 16, 18, outline, 4);
                int tw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(bgs[i]));
                context.drawText(client.textRenderer, CustomGuiUtils.getFontText(bgs[i]), popupX + 8 + (POPUP_W - 16 - tw) / 2, by + 5, selected ? 0xFFFFFFFF : 0xFFD4D8E0, false);
            }
        }
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

        if (popupTarget != null) {
            if (click.button() == 0) {
                if (inside(mouseX, mouseY, popupX + POPUP_W - 18, popupY + 4, 16, 16)) {
                    popupTarget = null;
                    return true;
                }
                for (int i = 0; i < 4; i++) {
                    int by = popupY + 24 + i * 21;
                    if (inside(mouseX, mouseY, popupX + 8, by, POPUP_W - 16, 18)) {
                        setBgMode(popupTarget, i);
                        return true;
                    }
                }
            }
            popupTarget = null;
            return true;
        }

        // Right-click: Open Background context popup!
        if (click.button() == 1) {
            if (FpsModule.enabled) {
                int w = StatusHudRenderer.getFpsWidth(client); int h = 18; float s = FpsModule.scale;
                if (inside(mouseX, mouseY, FpsModule.hudX - 2, FpsModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("fps", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (PingModule.enabled) {
                int w = StatusHudRenderer.getPingWidth(client); int h = 18; float s = PingModule.scale;
                if (inside(mouseX, mouseY, PingModule.hudX - 2, PingModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("ping", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (CpsModule.enabled) {
                int w = StatusHudRenderer.getCpsWidth(client); int h = 18; float s = CpsModule.scale;
                if (inside(mouseX, mouseY, CpsModule.hudX - 2, CpsModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("cps", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (KeyStrokesModule.enabled) {
                float s = KeyStrokesModule.scale;
                int minX = getKeyMinX(); int minY = getKeyMinY();
                int maxX = getKeyMaxX(); int maxY = getKeyMaxY();
                int gw = maxX - minX; int gh = maxY - minY;
                if (inside(mouseX, mouseY, KeyStrokesModule.hudX + (minX - 4) * s, KeyStrokesModule.hudY + (minY - 4) * s, (gw + 8) * s, (gh + 8) * s)) {
                    openPopup("keystrokes", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (ServerInfoModule.enabled) {
                int w = StatusHudRenderer.getServerInfoWidth(client); int h = 18; float s = ServerInfoModule.scale;
                if (inside(mouseX, mouseY, ServerInfoModule.hudX - 2, ServerInfoModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("serverInfo", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (FakeScoreboardModule.enabled) {
                int w = FakeScoreboardRenderer.getWidth(client); int h = FakeScoreboardRenderer.getHeight(); float s = FakeScoreboardModule.scale;
                int x = FakeScoreboardModule.hudX;
                if (x == -1) x = width - (int)(w * s) - 10;
                int y = FakeScoreboardModule.hudY;
                if (y == -1) y = (height - (int)(h * s)) / 2;
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("fakeScoreboard", (int)mouseX, (int)mouseY); return true;
                }
            }
            return false;
        }

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

        // 5. Check Server Info
        if (ServerInfoModule.enabled) {
            int w = StatusHudRenderer.getServerInfoWidth(client); int h = 18; float s = ServerInfoModule.scale;
            int x = ServerInfoModule.hudX;
            if (x == -1) {
                x = width - (int)(w * s) - 10;
                ServerInfoModule.hudX = x;
            }
            int y = ServerInfoModule.hudY;
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
                ServerInfoModule.enabled = false; return true;
            }
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y + h * s - 10 * s, 14 * s, 14 * s)) {
                resizingTarget = "serverInfo"; startX = (int)mouseX; startScale = ServerInfoModule.scale; return true;
            }
            if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                draggingTarget = "serverInfo"; dragOffsetX = (int)mouseX - x; dragOffsetY = (int)mouseY - y; return true;
            }
        }

        // 6. Check Fake Scoreboard
        if (FakeScoreboardModule.enabled) {
            int w = FakeScoreboardRenderer.getWidth(client); int h = FakeScoreboardRenderer.getHeight(); float s = FakeScoreboardModule.scale;
            int x = FakeScoreboardModule.hudX;
            if (x == -1) {
                x = width - (int)(w * s) - 10;
                FakeScoreboardModule.hudX = x;
            }
            int y = FakeScoreboardModule.hudY;
            if (y == -1) {
                y = (height - (int)(h * s)) / 2;
                FakeScoreboardModule.hudY = y;
            }
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
                FakeScoreboardModule.enabled = false; return true;
            }
            if (inside(mouseX, mouseY, x + w * s - 10 * s, y + h * s - 10 * s, 14 * s, 14 * s)) {
                resizingTarget = "fakeScoreboard"; startX = (int)mouseX; startScale = FakeScoreboardModule.scale; return true;
            }
            if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                draggingTarget = "fakeScoreboard"; dragOffsetX = (int)mouseX - x; dragOffsetY = (int)mouseY - y; return true;
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
                case "serverInfo" -> ServerInfoModule.scale = newScale;
                case "fakeScoreboard" -> FakeScoreboardModule.scale = newScale;
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
                case "serverInfo" -> { gw = (int)(StatusHudRenderer.getServerInfoWidth(client) * ServerInfoModule.scale); gh = (int)(18 * ServerInfoModule.scale); s = ServerInfoModule.scale; }
                case "fakeScoreboard" -> { gw = (int)(FakeScoreboardRenderer.getWidth(client) * FakeScoreboardModule.scale); gh = (int)(FakeScoreboardRenderer.getHeight() * FakeScoreboardModule.scale); s = FakeScoreboardModule.scale; }
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
                case "serverInfo" -> { ServerInfoModule.hudX = newX; ServerInfoModule.hudY = newY; }
                case "fakeScoreboard" -> { FakeScoreboardModule.hudX = newX; FakeScoreboardModule.hudY = newY; }
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

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (popupTarget != null) {
                popupTarget = null;
                return true;
            }
        }
        return super.keyPressed(input);
    }
}
