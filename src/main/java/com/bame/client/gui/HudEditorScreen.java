package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.module.*;
import com.bame.client.module.KeyStrokesModule.KeyStroke;
import com.bame.client.render.ArmorHudLayout;
import com.bame.client.render.ArmorHudRenderer;
import com.bame.client.render.FakeScoreboardRenderer;
import com.bame.client.render.HotbarHud;
import com.bame.client.render.HotbarLayout;
import com.bame.client.render.InventoryHudLayout;
import com.bame.client.render.InventoryHudRenderer;
import com.bame.client.render.KeyStrokesRenderer;
import com.bame.client.render.ScoreboardRenderer;
import com.bame.client.render.SpotifyHudRenderer;
import com.bame.client.render.StatusHudRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.awt.Color;

public class HudEditorScreen extends Screen {
    private final Screen parent;

    private String draggingTarget = NoneTarget; // "keystrokes", "fps", "ping", "cps", "serverInfo", "fakeScoreboard", "scoreboard", "clock", "coordinates", "potions", "targetHud", "armorHud", "inventoryHud", "spotifyHud", "hotbar"
    private String resizingTarget = NoneTarget;
    private static final String NoneTarget = "none";
    private KeyStroke draggingKey = null;
    
    private int dragOffsetX, dragOffsetY;
    private int startX, startY;
    private int startW, startH;
    private float startScale;
    private int resizeMode = 0; // 0 = corner (diagonal), 1 = right (side), 2 = bottom (vertical)

    private int snapLineX = -1;
    private int snapLineY = -1;

    private String snapDockTarget = null;
    private int snapDockIndex = -1;
    private String clickedDockedSegment = null;
    private int clickStartX = 0, clickStartY = 0;
    private int initialServerInfoX = 0, initialServerInfoY = 0;
    private boolean isDetached = false;

    private String popupTarget = null;
    private int popupX, popupY;
    private static final int POPUP_W = 120;
    private static final int POPUP_H = 114;

    private String activeColorPickerTarget = null;
    private int colorPickerX, colorPickerY;
    private int colorPickerDrag = -1; // 0 = SV, 1 = Hue, 2 = Alpha
    private float cpHue = 0f, cpSat = 1f, cpVal = 1f, cpAlpha = 1f;
    private static final int CP_W = 148;
    private static final int CP_H = 118;

    // Vanilla-Hotbar-Gruppe (HotbarModule): ziehen, Ecke = skalieren, Rechtsklick = kleines Menü
    private static final String HOTBAR_TARGET = "hotbar";
    private boolean hotbarPopupOpen = false;
    private int hotbarPopupX, hotbarPopupY;
    private static final int HOTBAR_POPUP_W = 160;
    private static final int HOTBAR_POPUP_H = 72;

    // Armor HUD ("armorHud"): Position frei oder am Hotbar-Anker (dann nicht ziehbar, nur Hinweis), Größe wie die anderen
    private static final String ARMOR_TARGET = "armorHud";
    // Inventory HUD ("inventoryHud"): wie die anderen Elemente (frei, Begrenzung am Rand, Einrasten); Platzierung aus dem Renderer
    private static final String INVENTORY_TARGET = "inventoryHud";
    /** Effektive Größe / eigene Größe beim Start einer Größenänderung (am Anker: Hotbar-Größe, sonst 1). */
    private float armorScaleFactor = 1.0f;
    /** Klick auf das angeheftete HUD: Anker-Hinweis bis zu diesem Zeitpunkt zeigen. */
    private long armorHintUntil = 0L;

    private boolean isDockableTarget(String target) {
        return target.equals("fps") || target.equals("ping") || target.equals("serverInfo_name") || target.equals("serverInfo_server") || target.equals("serverInfo_time");
    }

    private String getDockedSegmentAt(double mouseX, double mouseY) {
        if (!ServerInfoModule.enabled) return null;
        java.util.List<String> active = ServerInfoModule.getActiveDockedElements();
        if (active.isEmpty()) return null;
        int x = ServerInfoModule.hudX;
        int y = ServerInfoModule.hudY;
        float s = ServerInfoModule.scale;
        int w = StatusHudRenderer.getServerInfoWidth(client);
        int h = 18;
        if (!inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
            return null;
        }
        double relX = (mouseX - x) / s;
        int curX = 8;
        for (int i = 0; i < active.size(); i++) {
            String elem = active.get(i);
            int elemW = StatusHudRenderer.getElementWidth(client, elem);
            int segEnd = curX + elemW + (i < active.size() - 1 ? 16 : 8);
            if (relX >= curX - 4 && relX <= segEnd) {
                return elem;
            }
            curX = segEnd;
        }
        return active.get(active.size() - 1);
    }

    private void openPopup(String target, int mx, int my) {
        this.popupTarget = target;
        this.activeColorPickerTarget = null;
        this.hotbarPopupOpen = false;
        this.popupX = Math.max(10, Math.min(width - POPUP_W - 10, mx));
        this.popupY = Math.max(10, Math.min(height - POPUP_H - 10, my));
    }

    private int getBgMode(String target) {
        return switch (target) {
            case "fps" -> FpsModule.bgMode;
            case "ping" -> PingModule.bgMode;
            case "cps" -> CpsModule.bgMode;
            case "keystrokes" -> KeyStrokesModule.bgMode;
            case "serverInfo", "serverInfo_name", "serverInfo_server", "serverInfo_time" -> ServerInfoModule.bgMode;
            case "fakeScoreboard" -> FakeScoreboardModule.bgMode;
            case "scoreboard" -> ScoreboardModule.bgMode;
            case "clock" -> ClockModule.bgMode;
            case "coordinates" -> CoordinatesModule.bgMode;
            case "potions" -> PotionsModule.bgMode;
            case "targetHud" -> TargetHudModule.bgMode;
            case "armorHud" -> ArmorHudModule.bgMode;
            case "inventoryHud" -> InventoryHudModule.bgMode;
            case "spotifyHud" -> SpotifyHudModule.bgMode;
            case "reachDisplay" -> ReachDisplayModule.bgMode;
            default -> 0;
        };
    }

    private void setBgMode(String target, int mode) {
        switch (target) {
            case "fps" -> FpsModule.bgMode = mode;
            case "ping" -> PingModule.bgMode = mode;
            case "cps" -> CpsModule.bgMode = mode;
            case "keystrokes" -> KeyStrokesModule.bgMode = mode;
            case "serverInfo", "serverInfo_name", "serverInfo_server", "serverInfo_time" -> ServerInfoModule.bgMode = mode;
            case "fakeScoreboard" -> FakeScoreboardModule.bgMode = mode;
            case "scoreboard" -> ScoreboardModule.bgMode = mode;
            case "clock" -> ClockModule.bgMode = mode;
            case "coordinates" -> CoordinatesModule.bgMode = mode;
            case "potions" -> PotionsModule.bgMode = mode;
            case "targetHud" -> TargetHudModule.bgMode = mode;
            case "armorHud" -> ArmorHudModule.bgMode = mode;
            case "inventoryHud" -> InventoryHudModule.bgMode = mode;
            case "spotifyHud" -> SpotifyHudModule.bgMode = mode;
            case "reachDisplay" -> ReachDisplayModule.bgMode = mode;
        }
        com.bame.client.BameClientConfig.save();
    }

    private int getOutlineColor(String target) {
        return switch (target) {
            case "fps" -> FpsModule.outlineColor;
            case "ping" -> PingModule.outlineColor;
            case "cps" -> CpsModule.outlineColor;
            case "keystrokes" -> KeyStrokesModule.outlineColor;
            case "serverInfo", "serverInfo_name", "serverInfo_server", "serverInfo_time" -> ServerInfoModule.outlineColor;
            case "fakeScoreboard" -> FakeScoreboardModule.outlineColor;
            case "scoreboard" -> ScoreboardModule.outlineColor;
            case "clock" -> ClockModule.outlineColor;
            case "coordinates" -> CoordinatesModule.outlineColor;
            case "potions" -> PotionsModule.outlineColor;
            case "targetHud" -> TargetHudModule.outlineColor;
            case "armorHud" -> ArmorHudModule.outlineColor;
            case "inventoryHud" -> InventoryHudModule.outlineColor;
            case "spotifyHud" -> SpotifyHudModule.outlineColor;
            case "reachDisplay" -> ReachDisplayModule.outlineColor;
            default -> 0xFFFFFFFF;
        };
    }

    private void setOutlineColor(String target, int color) {
        switch (target) {
            case "fps" -> FpsModule.outlineColor = color;
            case "ping" -> PingModule.outlineColor = color;
            case "cps" -> CpsModule.outlineColor = color;
            case "keystrokes" -> KeyStrokesModule.outlineColor = color;
            case "serverInfo", "serverInfo_name", "serverInfo_server", "serverInfo_time" -> ServerInfoModule.outlineColor = color;
            case "fakeScoreboard" -> FakeScoreboardModule.outlineColor = color;
            case "scoreboard" -> ScoreboardModule.outlineColor = color;
            case "clock" -> ClockModule.outlineColor = color;
            case "coordinates" -> CoordinatesModule.outlineColor = color;
            case "potions" -> PotionsModule.outlineColor = color;
            case "targetHud" -> TargetHudModule.outlineColor = color;
            case "armorHud" -> ArmorHudModule.outlineColor = color;
            case "inventoryHud" -> InventoryHudModule.outlineColor = color;
            case "spotifyHud" -> SpotifyHudModule.outlineColor = color;
            case "reachDisplay" -> ReachDisplayModule.outlineColor = color;
        }
        com.bame.client.BameClientConfig.save();
    }

    private void setTargetScale(String target, float scale) {
        switch (target) {
            case "keystrokes" -> KeyStrokesModule.scale = scale;
            case "fps" -> FpsModule.scale = scale;
            case "ping" -> PingModule.scale = scale;
            case "cps" -> CpsModule.scale = scale;
            case "serverInfo" -> ServerInfoModule.scale = scale;
            case "fakeScoreboard" -> FakeScoreboardModule.scale = scale;
            case "scoreboard" -> ScoreboardModule.scale = scale;
            case "clock" -> ClockModule.scale = scale;
            case "coordinates" -> CoordinatesModule.scale = scale;
            case "potions" -> PotionsModule.scale = scale;
            case "targetHud" -> TargetHudModule.scale = scale;
            case "armorHud" -> ArmorHudModule.scale = scale;
            case "inventoryHud" -> InventoryHudModule.scale = scale;
            case "spotifyHud" -> SpotifyHudModule.scale = scale;
            case "reachDisplay" -> ReachDisplayModule.scale = scale;
        }
    }

    private void setTargetCustomWidth(String target, int width) {
        switch (target) {
            case "fps" -> FpsModule.customWidth = width;
            case "ping" -> PingModule.customWidth = width;
            case "cps" -> CpsModule.customWidth = width;
            case "serverInfo" -> ServerInfoModule.customWidth = width;
            case "fakeScoreboard" -> FakeScoreboardModule.customWidth = width;
            case "scoreboard" -> ScoreboardModule.customWidth = width;
            case "clock" -> ClockModule.customWidth = width;
            case "coordinates" -> CoordinatesModule.customWidth = width;
            case "potions" -> PotionsModule.customWidth = width;
            case "targetHud" -> TargetHudModule.customWidth = width;
            case "armorHud" -> ArmorHudModule.customWidth = width;
            case "inventoryHud" -> InventoryHudModule.customWidth = width;
            case "spotifyHud" -> SpotifyHudModule.customWidth = width;
            case "reachDisplay" -> ReachDisplayModule.customWidth = width;
        }
    }

    private void setTargetCustomHeight(String target, int height) {
        switch (target) {
            case "fps" -> FpsModule.customHeight = height;
            case "ping" -> PingModule.customHeight = height;
            case "cps" -> CpsModule.customHeight = height;
            case "serverInfo" -> ServerInfoModule.customHeight = height;
            case "fakeScoreboard" -> FakeScoreboardModule.customHeight = height;
            case "scoreboard" -> ScoreboardModule.customHeight = height;
            case "clock" -> ClockModule.customHeight = height;
            case "coordinates" -> CoordinatesModule.customHeight = height;
            case "potions" -> PotionsModule.customHeight = height;
            case "targetHud" -> TargetHudModule.customHeight = height;
            case "armorHud" -> ArmorHudModule.customHeight = height;
            case "inventoryHud" -> InventoryHudModule.customHeight = height;
            case "spotifyHud" -> SpotifyHudModule.customHeight = height;
            case "reachDisplay" -> ReachDisplayModule.customHeight = height;
        }
    }

    private void openColorPicker(String target) {
        this.activeColorPickerTarget = target;
        int col = getOutlineColor(target);
        float[] hsv = Color.RGBtoHSB((col >> 16) & 255, (col >> 8) & 255, col & 255, null);
        this.cpHue = hsv[0];
        this.cpSat = hsv[1];
        this.cpVal = hsv[2];
        this.cpAlpha = ((col >>> 24) & 255) / 255f;

        if (popupX + POPUP_W + 6 + CP_W <= width - 10) {
            this.colorPickerX = popupX + POPUP_W + 6;
        } else {
            this.colorPickerX = Math.max(10, popupX - CP_W - 6);
        }
        this.colorPickerY = Math.max(10, Math.min(height - CP_H - 10, popupY));
    }

    private void updateColorPicker(double mx, double my) {
        if (activeColorPickerTarget == null) return;
        int svX = colorPickerX + 46; int svY = colorPickerY + 22; int svW = 92; int svH = 30;
        int barX = colorPickerX + 10; int barW = 128;
        int hueY = colorPickerY + 58; int alphaY = colorPickerY + 72;

        if (colorPickerDrag == 0) {
            cpSat = (float) Math.clamp((mx - svX) / (double)(svW - 1), 0.0, 1.0);
            cpVal = 1.0f - (float) Math.clamp((my - svY) / (double)(svH - 1), 0.0, 1.0);
        } else if (colorPickerDrag == 1) {
            cpHue = (float) Math.clamp((mx - barX) / (double)(barW - 1), 0.0, 1.0);
        } else if (colorPickerDrag == 2) {
            cpAlpha = (float) Math.clamp((mx - barX) / (double)(barW - 1), 0.0, 1.0);
        }

        int rgb = Color.HSBtoRGB(cpHue, cpSat, cpVal) & 0xFFFFFF;
        int a = Math.round(cpAlpha * 255) & 0xFF;
        int newColor = (a << 24) | rgb;
        setOutlineColor(activeColorPickerTarget, newColor);
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

    // "Reset Positions" button (top center, below the ESC hint)
    private static final String RESET_LABEL = "Reset Positions";
    private static final int RESET_BTN_H = 18;
    private static final int RESET_BTN_Y = 24;

    private int getResetBtnW() {
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(RESET_LABEL)) + 24;
    }

    private int getResetBtnX() {
        return (width - getResetBtnW()) / 2;
    }

    private void renderResetButton(DrawContext context, int mouseX, int mouseY) {
        int bw = getResetBtnW();
        int bx = getResetBtnX();
        boolean hover = inside(mouseX, mouseY, bx, RESET_BTN_Y, bw, RESET_BTN_H);
        int fill = hover ? 0xFF252A34 : 0xFF181C24;
        int outline = hover ? GuiTheme.accent() : 0xFF292D36;
        CustomGuiUtils.fillUltraRounded(context, bx, RESET_BTN_Y, bw, RESET_BTN_H, fill, 4);
        CustomGuiUtils.drawUltraRoundedOutline(context, bx, RESET_BTN_Y, bw, RESET_BTN_H, outline, 4);
        int tw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(RESET_LABEL));
        context.drawText(client.textRenderer, CustomGuiUtils.getFontText(RESET_LABEL), bx + (bw - tw) / 2, RESET_BTN_Y + 5, hover ? 0xFFFFFFFF : 0xFFD4D8E0, false);
    }

    /**
     * Resets the positions of all HUD elements (and sub-element positions such as the KeyStrokes
     * key layout and the standalone Server Info segments) to the module field defaults.
     * Scale, size, background and docking state are left unchanged.
     */
    public static void resetAllPositions() {
        KeyStrokesModule.resetPosition();
        FpsModule.hudX = 10;             FpsModule.hudY = 10;
        PingModule.hudX = 10;            PingModule.hudY = 35;
        CpsModule.hudX = 10;             CpsModule.hudY = 60;
        ClockModule.hudX = 10;           ClockModule.hudY = 82;
        CoordinatesModule.hudX = 10;     CoordinatesModule.hudY = 106;
        PotionsModule.hudX = 10;         PotionsModule.hudY = 130;
        SpotifyHudModule.hudX = 10;      SpotifyHudModule.hudY = 180;
        ReachDisplayModule.hudX = 10;    ReachDisplayModule.hudY = 200;
        ServerInfoModule.hudX = -1;      ServerInfoModule.hudY = 10;   // -1 = top-right
        ServerInfoModule.nameX = 10;     ServerInfoModule.nameY = 10;
        ServerInfoModule.serverX = 10;   ServerInfoModule.serverY = 32;
        ServerInfoModule.timeX = 10;     ServerInfoModule.timeY = 54;
        TargetHudModule.hudX = -1;       TargetHudModule.hudY = -1;    // -1 = auto (centered, bottom)
        ArmorHudModule.hudX = -1;        ArmorHudModule.hudY = -1;     // -1 = auto (centered, bottom)
        InventoryHudModule.hudX = -1;    InventoryHudModule.hudY = -1; // -1 = auto (bottom right)
        FakeScoreboardModule.hudX = -1;  FakeScoreboardModule.hudY = -1; // -1 = auto (right, centered)
        ScoreboardModule.hudX = -1;      ScoreboardModule.hudY = -1;   // -1 = auto (right, centered)
        HotbarModule.resetPosition();                                  // offset 0/0 = exact vanilla position
        BameClientConfig.save();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Darken background slightly
        context.fill(0, 0, width, height, 0x44000000);

        // Draw snap lines if active (snapLineX/Y are set while dragging and cleared on the next drag step / release)
        if (!draggingTarget.equals(NoneTarget) || draggingKey != null) {
            int cx = width / 2;
            int cy = height / 2;
            context.fill(cx, 0, cx + 1, height, 0x44FFFFFF);
            context.fill(0, cy, width, cy + 1, 0x44FFFFFF);
        }

        // 0. Vanilla hotbar group first (bottom layer): the mod's own HUD elements are drawn on top of it
        renderHotbarElement(context, mouseX, mouseY);

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
        if (FpsModule.enabled && (!ServerInfoModule.enabled || !ServerInfoModule.isDocked("fps"))) {
            int w = StatusHudRenderer.getFpsWidth(client); int h = StatusHudRenderer.getFpsHeight();
            float s = FpsModule.scale;
            int x = FpsModule.hudX; int y = FpsModule.hudY;
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);
            
            StatusHudRenderer.renderFps(context, x, y, s);
            if (hover || draggingTarget.equals("fps") || resizingTarget.equals("fps")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 3. Render Ping Module
        if (PingModule.enabled && (!ServerInfoModule.enabled || !ServerInfoModule.isDocked("ping"))) {
            int w = StatusHudRenderer.getPingWidth(client); int h = StatusHudRenderer.getPingHeight();
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
            int w = StatusHudRenderer.getCpsWidth(client); int h = StatusHudRenderer.getCpsHeight();
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
            java.util.List<String> activeDocked = ServerInfoModule.getActiveDockedElements();
            if (!activeDocked.isEmpty()) {
                int w = StatusHudRenderer.getServerInfoWidth(client); int h = StatusHudRenderer.getServerInfoHeight();
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

                if (snapDockTarget != null && snapDockTarget.equals("serverInfo")) {
                    CustomGuiUtils.drawUltraRoundedOutline(context, x - 4, y - 4, (int)(w * s) + 8, (int)(h * s) + 8, 0xFF00E5FF, 6);
                }
            }

            // Standalone name if undocked
            if (ServerInfoModule.showName && !ServerInfoModule.isDocked("name")) {
                int w = StatusHudRenderer.getElementWidth(client, "name") + 16; int h = 18;
                float s = ServerInfoModule.scale;
                int x = ServerInfoModule.nameX; int y = ServerInfoModule.nameY;
                boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);
                StatusHudRenderer.renderStandaloneName(context, x, y, s);
                if (hover || draggingTarget.equals("serverInfo_name")) {
                    drawBoundingControls(context, x, y, w, h, s);
                }
            }

            // Standalone server if undocked
            if (ServerInfoModule.showServer && !ServerInfoModule.isDocked("server")) {
                int w = StatusHudRenderer.getElementWidth(client, "server") + 16; int h = 18;
                float s = ServerInfoModule.scale;
                int x = ServerInfoModule.serverX; int y = ServerInfoModule.serverY;
                boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);
                StatusHudRenderer.renderStandaloneServer(context, x, y, s);
                if (hover || draggingTarget.equals("serverInfo_server")) {
                    drawBoundingControls(context, x, y, w, h, s);
                }
            }

            // Standalone time if undocked
            if (ServerInfoModule.showTime && !ServerInfoModule.isDocked("time")) {
                int w = StatusHudRenderer.getElementWidth(client, "time") + 16; int h = 18;
                float s = ServerInfoModule.scale;
                int x = ServerInfoModule.timeX; int y = ServerInfoModule.timeY;
                boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);
                StatusHudRenderer.renderStandaloneTime(context, x, y, s);
                if (hover || draggingTarget.equals("serverInfo_time")) {
                    drawBoundingControls(context, x, y, w, h, s);
                }
            }
        }

        // 6. Render Fake Scoreboard Module
        if (FakeScoreboardModule.enabled) {
            int w = FakeScoreboardRenderer.getWidth(client); int h = FakeScoreboardRenderer.getHeight();
            float s = FakeScoreboardModule.scale;
            int x = FakeScoreboardModule.hudX;
            if (x == -1) {
                x = width - (int)(w * s) - 3;
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

        // 6b. Render Real Scoreboard Module
        if (ScoreboardModule.enabled) {
            int w = ScoreboardRenderer.getWidth(client); int h = ScoreboardRenderer.getHeight(client);
            float s = ScoreboardModule.scale;
            int x = ScoreboardModule.hudX;
            if (x == -1) {
                x = width - (int)(w * s) - 3;
                ScoreboardModule.hudX = x;
            }
            int y = ScoreboardModule.hudY;
            if (y == -1) {
                y = (height - (int)(h * s)) / 2;
                ScoreboardModule.hudY = y;
            }
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            ScoreboardRenderer.render(context, null, x, y, s, true);
            if (hover || draggingTarget.equals("scoreboard") || resizingTarget.equals("scoreboard")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 7. Render Clock Module
        if (ClockModule.enabled) {
            int w = StatusHudRenderer.getClockWidth(client); int h = StatusHudRenderer.getClockHeight();
            float s = ClockModule.scale;
            int x = ClockModule.hudX; int y = ClockModule.hudY;
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            StatusHudRenderer.renderClock(context, x, y, s);
            if (hover || draggingTarget.equals("clock") || resizingTarget.equals("clock")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 8. Render Coordinates Module
        if (CoordinatesModule.enabled) {
            int w = StatusHudRenderer.getCoordinatesWidth(client); int h = StatusHudRenderer.getCoordinatesHeight();
            float s = CoordinatesModule.scale;
            int x = CoordinatesModule.hudX; int y = CoordinatesModule.hudY;
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            StatusHudRenderer.renderCoordinates(context, x, y, s);
            if (hover || draggingTarget.equals("coordinates") || resizingTarget.equals("coordinates")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 9. Render Potions Module
        if (PotionsModule.enabled) {
            int w = StatusHudRenderer.getPotionsWidth(client, true); int h = StatusHudRenderer.getPotionsHeight(client, true);
            float s = PotionsModule.scale;
            int x = PotionsModule.hudX; int y = PotionsModule.hudY;
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            StatusHudRenderer.renderPotions(context, x, y, s, true);
            if (hover || draggingTarget.equals("potions") || resizingTarget.equals("potions")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 10. Render Target HUD Module
        if (TargetHudModule.enabled) {
            int w = StatusHudRenderer.getTargetHudWidth(); int h = StatusHudRenderer.getTargetHudHeight();
            float s = TargetHudModule.scale;
            int x = TargetHudModule.hudX;
            if (x == -1) {
                x = (width - (int)(w * s)) / 2;
                TargetHudModule.hudX = x;
            }
            int y = TargetHudModule.hudY;
            if (y == -1) {
                y = height - 120;
                TargetHudModule.hudY = y;
            }
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            StatusHudRenderer.renderTargetHud(context, x, y, s, true);
            if (hover || draggingTarget.equals("targetHud") || resizingTarget.equals("targetHud")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 11. Render Armor HUD Module (same placement as in-game: free position or hotbar anchor, clamped to the screen)
        if (ArmorHudModule.enabled) {
            ArmorHudRenderer.place(width, height, true);
            int x = ArmorHudRenderer.placedX(); int y = ArmorHudRenderer.placedY();
            int w = ArmorHudRenderer.boxWidth(); int h = ArmorHudRenderer.boxHeight();
            float s = ArmorHudRenderer.placedScale();
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            ArmorHudRenderer.render(context, x, y, s, true);
            if (hover || draggingTarget.equals(ARMOR_TARGET) || resizingTarget.equals(ARMOR_TARGET)) {
                drawBoundingControls(context, x, y, w, h, s);
            }
            if (ArmorHudModule.isAnchored() && (hover || System.currentTimeMillis() < armorHintUntil)) {
                drawArmorAnchorHint(context, x, y, w, h, s);
            }
        }

        // 12. Render Spotify HUD Module
        if (SpotifyHudModule.enabled) {
            int w = SpotifyHudRenderer.getWidth(); int h = SpotifyHudRenderer.getHeight();
            float s = SpotifyHudModule.scale;
            int x = SpotifyHudModule.hudX;
            if (x == -1) {
                x = 10;
                SpotifyHudModule.hudX = x;
            }
            int y = SpotifyHudModule.hudY;
            if (y == -1) {
                y = 180;
                SpotifyHudModule.hudY = y;
            }
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            SpotifyHudRenderer.render(context, x, y, s, true);
            if (hover || draggingTarget.equals("spotifyHud") || resizingTarget.equals("spotifyHud")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 13. Render Reach Display Module
        if (ReachDisplayModule.enabled && ReachDisplayModule.mode == 1) {
            int w = StatusHudRenderer.getReachDisplayWidth(client); int h = StatusHudRenderer.getReachDisplayHeight();
            float s = ReachDisplayModule.scale;
            int x = ReachDisplayModule.hudX; int y = ReachDisplayModule.hudY;
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            StatusHudRenderer.renderReachDisplay(context, x, y, s);
            if (hover || draggingTarget.equals("reachDisplay") || resizingTarget.equals("reachDisplay")) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        // 14. Render Inventory HUD (same placement as in-game, placeholders if the inventory is empty)
        if (InventoryHudModule.enabled) {
            InventoryHudRenderer.place(width, height, true);
            int x = InventoryHudRenderer.placedX(); int y = InventoryHudRenderer.placedY();
            int w = InventoryHudRenderer.boxWidth(); int h = InventoryHudRenderer.boxHeight();
            float s = InventoryHudRenderer.placedScale();
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s);

            InventoryHudRenderer.render(context, x, y, s, true);
            if (hover || draggingTarget.equals(INVENTORY_TARGET) || resizingTarget.equals(INVENTORY_TARGET)) {
                drawBoundingControls(context, x, y, w, h, s);
            }
        }

        if (snapLineX != -1) context.fill(snapLineX, 0, snapLineX + 1, height, 0xFF00FF00);
        if (snapLineY != -1) context.fill(0, snapLineY, width, snapLineY + 1, 0xFF00FF00);

        context.drawText(client.textRenderer, "Press ESC to save and return", width / 2 - 70, 10, 0xFFFFFFFF, true);
        renderResetButton(context, mouseX, mouseY);

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
                case "fakeScoreboard" -> "Fake Scoreboard";
                case "scoreboard" -> "Scoreboard";
                case "clock" -> "Clock";
                case "coordinates" -> "Coordinates";
                case "potions" -> "Potions";
                case "targetHud" -> "Target HUD";
                case "armorHud" -> "Armor HUD";
                case "inventoryHud" -> "Inventory HUD";
                case "spotifyHud" -> "Spotify HUD";
                default -> "HUD";
            };
            context.drawText(client.textRenderer, CustomGuiUtils.getFontText(title + " BG"), popupX + 8, popupY + 8, 0xFFFFFFFF, false);
            context.drawText(client.textRenderer, Text.literal("×"), popupX + POPUP_W - 14, popupY + 6, 0xFF8E95A4, false);

            int curBg = getBgMode(popupTarget);
            // Spotify-Card zeichnet bei "Dark" die in der Cover-Farbe getönte Karte
            boolean coverTint = "spotifyHud".equals(popupTarget) && SpotifyHudModule.style() == SpotifyHudModule.STYLE_CARD;
            String[] bgs = {"Transparent", "Tooltip", coverTint ? "Cover Tint" : "Dark", "Outline"};
            for (int i = 0; i < 4; i++) {
                int by = popupY + 24 + i * 21;
                boolean selected = (curBg == i);

                if (i == 3 && selected) {
                    // Outline selected: button is slightly narrower, color square displayed on the right (Bild 3)
                    int btnW = POPUP_W - 16 - 22;
                    boolean hover = inside(mouseX, mouseY, popupX + 8, by, btnW, 18);
                    int fill = GuiTheme.alpha(GuiTheme.accent(), 90);
                    int outline = GuiTheme.accent();
                    CustomGuiUtils.fillUltraRounded(context, popupX + 8, by, btnW, 18, fill, 4);
                    CustomGuiUtils.drawUltraRoundedOutline(context, popupX + 8, by, btnW, 18, outline, 4);
                    int tw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(bgs[i]));
                    context.drawText(client.textRenderer, CustomGuiUtils.getFontText(bgs[i]), popupX + 8 + (btnW - tw) / 2, by + 5, 0xFFFFFFFF, false);

                    // Color square button (Bild 3)
                    int sqX = popupX + POPUP_W - 24;
                    int sqY = by + 1;
                    int sqSize = 16;
                    int col = getOutlineColor(popupTarget);
                    context.fill(sqX, sqY, sqX + sqSize, sqY + sqSize, col);
                    CustomGuiUtils.drawRoundedOutline(context, sqX - 1, sqY - 1, sqSize + 2, sqSize + 2, 0xFFFFFFFF);
                } else {
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

        if (hotbarPopupOpen) {
            renderHotbarPopup(context, mouseX, mouseY);
        }

        // Render Color Picker modal if active (Bild 4)
        if (activeColorPickerTarget != null) {
            renderColorPicker(context, mouseX, mouseY);
        }
    }

    private void renderColorPicker(DrawContext context, int mouseX, int mouseY) {
        CustomGuiUtils.fillUltraRounded(context, colorPickerX, colorPickerY, CP_W, CP_H, 0xFA151820, 6);
        CustomGuiUtils.drawUltraRoundedOutline(context, colorPickerX, colorPickerY, CP_W, CP_H, 0xFF353C4D, 6);

        // Title
        context.drawText(client.textRenderer, CustomGuiUtils.getFontText("COLOR PICKER"), colorPickerX + 10, colorPickerY + 8, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, Text.literal("×"), colorPickerX + CP_W - 14, colorPickerY + 6, 0xFF8E95A4, false);

        int curCol = getOutlineColor(activeColorPickerTarget);

        // 1. Preview box (Top Left)
        int prevX = colorPickerX + 10;
        int prevY = colorPickerY + 22;
        int prevSize = 30;
        context.fill(prevX, prevY, prevX + prevSize, prevY + prevSize, curCol);
        CustomGuiUtils.drawRoundedOutline(context, prevX - 1, prevY - 1, prevSize + 2, prevSize + 2, 0xFFFFFFFF);

        // 2. SV Box (Top Right)
        int svX = colorPickerX + 46;
        int svY = colorPickerY + 22;
        int svW = 92;
        int svH = 30;
        for (int i = 0; i < svW; i++) {
            float s = i / (float)(svW - 1);
            int top = Color.HSBtoRGB(cpHue, s, 1.0f);
            context.fillGradient(svX + i, svY, svX + i + 1, svY + svH, top, 0xFF000000);
        }
        CustomGuiUtils.drawRoundedOutline(context, svX - 1, svY - 1, svW + 2, svH + 2, 0xFF353C4D);

        // SV Handle cursor (small hollow white square)
        int curSvX = svX + Math.round(cpSat * (svW - 1));
        int curSvY = svY + Math.round((1.0f - cpVal) * (svH - 1));
        context.fill(curSvX - 2, curSvY - 2, curSvX + 3, curSvY + 3, 0xAA000000);
        context.fill(curSvX - 2, curSvY - 2, curSvX + 3, curSvY - 1, 0xFFFFFFFF);
        context.fill(curSvX - 2, curSvY + 2, curSvX + 3, curSvY + 3, 0xFFFFFFFF);
        context.fill(curSvX - 2, curSvY - 1, curSvX - 1, curSvY + 2, 0xFFFFFFFF);
        context.fill(curSvX + 2, curSvY - 1, curSvX + 3, curSvY + 2, 0xFFFFFFFF);

        // 3. Hue Rainbow Bar
        int barX = colorPickerX + 10;
        int barW = 128;
        int hueY = colorPickerY + 58;
        int barH = 8;
        for (int i = 0; i < barW; i++) {
            int rgb = Color.HSBtoRGB(i / (float)(barW - 1), 1.0f, 1.0f);
            context.fill(barX + i, hueY, barX + i + 1, hueY + barH, rgb);
        }
        CustomGuiUtils.drawRoundedOutline(context, barX - 1, hueY - 1, barW + 2, barH + 2, 0xFF353C4D);

        // Hue indicator
        int curHueX = barX + Math.round(cpHue * (barW - 1));
        context.fill(curHueX - 2, hueY - 1, curHueX + 3, hueY + barH + 1, 0xFFFFFFFF);
        context.fill(curHueX - 1, hueY, curHueX + 2, hueY + barH, Color.HSBtoRGB(cpHue, 1.0f, 1.0f));

        // 4. Alpha Bar
        int alphaY = colorPickerY + 72;
        for (int i = 0; i < barW; i += 4) {
            for (int j = 0; j < barH; j += 4) {
                int col = ((i / 4 + j / 4) % 2 == 0) ? 0xFFAAAAAA : 0xFF555555;
                context.fill(barX + i, alphaY + j, barX + Math.min(barW, i + 4), alphaY + Math.min(barH, j + 4), col);
            }
        }
        int curRgb = Color.HSBtoRGB(cpHue, cpSat, cpVal) & 0xFFFFFF;
        for (int i = 0; i < barW; i++) {
            int a = Math.round(255 * i / (float)(barW - 1));
            context.fill(barX + i, alphaY, barX + i + 1, alphaY + barH, (a << 24) | curRgb);
        }
        CustomGuiUtils.drawRoundedOutline(context, barX - 1, alphaY - 1, barW + 2, barH + 2, 0xFF353C4D);

        // Alpha indicator
        int curAlphaX = barX + Math.round(cpAlpha * (barW - 1));
        context.fill(curAlphaX - 2, alphaY - 1, curAlphaX + 3, alphaY + barH + 1, 0xFFFFFFFF);
        context.fill(curAlphaX - 1, alphaY, curAlphaX + 2, alphaY + barH, 0xFF12161E);

        // 5. Hex Box
        int hexY = colorPickerY + 86;
        int hexH = 20;
        CustomGuiUtils.fillUltraRounded(context, barX, hexY, barW, hexH, 0x550B0E14, 4);
        CustomGuiUtils.drawUltraRoundedOutline(context, barX, hexY, barW, hexH, 0xFF505768, 4);
        String hex = String.format(java.util.Locale.ROOT, "#%02X%02X%02X%02X", (curCol >> 16) & 0xFF, (curCol >> 8) & 0xFF, curCol & 0xFF, (curCol >>> 24) & 0xFF);
        context.drawText(client.textRenderer, CustomGuiUtils.getFontText(hex), barX + 8, hexY + 6, 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Vanilla hotbar group ("hotbar"): preview at the in-game position, always-visible frame with label, corner = scale
    // ------------------------------------------------------------------------------------------------------------

    private boolean insideHotbar(double mx, double my, HotbarLayout.Box b) {
        return inside(mx, my, b.x() - 2, b.y() - 2, b.w() + 4, b.h() + 4);
    }

    /** Scale handle in the bottom-right corner, inside the frame (the hotbar sits on the bottom edge by default). */
    private boolean insideHotbarCorner(double mx, double my, HotbarLayout.Box b) {
        return inside(mx, my, b.right() - 10, b.bottom() - 10, 12, 12);
    }

    private void renderHotbarElement(DrawContext context, int mouseX, int mouseY) {
        HotbarHud.renderPreview(context, width, height);

        HotbarLayout.Box b = HotbarHud.currentBox(width, height);
        boolean hover = insideHotbar(mouseX, mouseY, b);
        boolean active = hover || draggingTarget.equals(HOTBAR_TARGET) || resizingTarget.equals(HOTBAR_TARGET);
        int x0 = b.x() - 2, y0 = b.y() - 2, w0 = b.w() + 4, h0 = b.h() + 4;
        if (active) {
            context.fill(x0, y0, x0 + w0, y0 + h0, 0x22FFFFFF);
        }
        // Frame always visible, so the group can be found and grabbed even with an empty hotbar
        context.drawStrokedRectangle(x0, y0, w0, h0, active ? 0xCCFFFFFF : 0x55FFFFFF);

        float s = HotbarLayout.clampScale(HotbarModule.scale);
        String label = s == 1.0f ? "Hotbar" : "Hotbar " + Math.round(s * 100) + "%";
        Text labelText = CustomGuiUtils.getFontText(label);
        int labelY = y0 - 10 >= 0 ? y0 - 10 : y0 + h0 + 2;
        context.drawText(client.textRenderer, labelText, x0 + 2, labelY, active ? 0xFFFFFFFF : 0xB0FFFFFF, true);
        if (hover && draggingTarget.equals(NoneTarget) && resizingTarget.equals(NoneTarget) && !hotbarPopupOpen) {
            int hintX = x0 + 2 + client.textRenderer.getWidth(labelText) + 6;
            context.drawText(client.textRenderer, CustomGuiUtils.getFontText("Right-click: options"), hintX, labelY, 0xFF8E95A4, true);
        }

        if (active) {
            // Corner chevron (scale), same look as the other elements but inside the frame
            int rx = b.right(), by = b.bottom();
            context.fill(rx - 8, by - 2, rx, by, 0xFFFFFFFF);
            context.fill(rx - 2, by - 8, rx, by, 0xFFFFFFFF);
        }
    }

    private void openHotbarPopup(int mx, int my) {
        this.popupTarget = null;
        this.activeColorPickerTarget = null;
        this.hotbarPopupOpen = true;
        this.hotbarPopupX = Math.max(10, Math.min(width - HOTBAR_POPUP_W - 10, mx));
        this.hotbarPopupY = Math.max(10, Math.min(height - HOTBAR_POPUP_H - 10, my));
    }

    private void renderHotbarPopup(DrawContext context, int mouseX, int mouseY) {
        int x = hotbarPopupX, y = hotbarPopupY, w = HOTBAR_POPUP_W;
        CustomGuiUtils.fillUltraRounded(context, x, y, w, HOTBAR_POPUP_H, 0xF5141822, 6);
        CustomGuiUtils.drawUltraRoundedOutline(context, x, y, w, HOTBAR_POPUP_H, GuiTheme.accent(), 6);
        context.drawText(client.textRenderer, CustomGuiUtils.getFontText("Hotbar"), x + 8, y + 8, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, Text.literal("×"), x + w - 14, y + 6, 0xFF8E95A4, false);

        // Row 1: hearts, armor, hunger, air, mount health and item name move with the hotbar (default on)
        int rowY = y + 24;
        boolean hoverRow = inside(mouseX, mouseY, x + 8, rowY, w - 16, 18);
        CustomGuiUtils.fillUltraRounded(context, x + 8, rowY, w - 16, 18, hoverRow ? 0xFF252A34 : 0xFF181C24, 4);
        CustomGuiUtils.drawUltraRoundedOutline(context, x + 8, rowY, w - 16, 18, hoverRow ? 0xFF606575 : 0xFF292D36, 4);
        context.drawText(client.textRenderer, CustomGuiUtils.getFontText("Move Status Bars"), x + 14, rowY + 5, 0xFFD4D8E0, false);
        CustomToggleWidget.draw(context, x + w - 12 - 26, rowY + 2, 26, 14, HotbarModule.moveStatusBars);

        // Row 2: back to vanilla position and size
        int resetY = y + 46;
        boolean hoverReset = inside(mouseX, mouseY, x + 8, resetY, w - 16, 18);
        CustomGuiUtils.fillUltraRounded(context, x + 8, resetY, w - 16, 18, hoverReset ? 0xFF252A34 : 0xFF181C24, 4);
        CustomGuiUtils.drawUltraRoundedOutline(context, x + 8, resetY, w - 16, 18, hoverReset ? GuiTheme.accent() : 0xFF292D36, 4);
        Text resetText = CustomGuiUtils.getFontText("Reset Hotbar");
        int tw = client.textRenderer.getWidth(resetText);
        context.drawText(client.textRenderer, resetText, x + (w - tw) / 2, resetY + 5, hoverReset ? 0xFFFFFFFF : 0xFFD4D8E0, false);
    }

    /** Clicks while the hotbar popup is open; a click outside closes it (like the background popup). */
    private boolean handleHotbarPopupClick(double mouseX, double mouseY, int button) {
        int x = hotbarPopupX, y = hotbarPopupY, w = HOTBAR_POPUP_W;
        if (button == 0 && inside(mouseX, mouseY, x, y, w, HOTBAR_POPUP_H)) {
            if (inside(mouseX, mouseY, x + w - 18, y + 4, 16, 16)) {
                hotbarPopupOpen = false;
                return true;
            }
            if (inside(mouseX, mouseY, x + 8, y + 24, w - 16, 18)) {
                HotbarModule.moveStatusBars = !HotbarModule.moveStatusBars;
                BameClientConfig.save();
                return true;
            }
            if (inside(mouseX, mouseY, x + 8, y + 46, w - 16, 18)) {
                HotbarModule.resetLayout();
                BameClientConfig.save();
                return true;
            }
            return true;
        }
        hotbarPopupOpen = false;
        return true;
    }

    /** Left click on the hotbar group: corner = scale, frame = drag. Checked last (large, sits under other elements). */
    private boolean checkHotbarControls(double mouseX, double mouseY) {
        HotbarLayout.Box b = HotbarHud.currentBox(width, height);
        if (insideHotbarCorner(mouseX, mouseY, b)) {
            resizingTarget = HOTBAR_TARGET;
            resizeMode = 0;
            startX = (int) mouseX;
            startY = (int) mouseY;
            startScale = HotbarLayout.clampScale(HotbarModule.scale);
            return true;
        }
        if (insideHotbar(mouseX, mouseY, b)) {
            draggingTarget = HOTBAR_TARGET;
            // Grab relative to the offset actually drawn (the stored one may be clamped on this screen size)
            dragOffsetX = (int) mouseX - HotbarHud.effectiveOffsetX(width);
            dragOffsetY = (int) mouseY - HotbarHud.effectiveOffsetY(height);
            return true;
        }
        return false;
    }

    /** Drag: snap to the screen centre like the other elements, keep the whole group on screen, store as offset. */
    private void dragHotbar(double mouseX, double mouseY) {
        float s = HotbarLayout.clampScale(HotbarModule.scale);
        int left = HotbarHud.groupLeft(), right = HotbarHud.groupRight(), top = HotbarHud.groupTop();
        int dx = (int) mouseX - dragOffsetX;
        int dy = (int) mouseY - dragOffsetY;

        int snappedX = HotbarLayout.snapCenterX(dx);           // centred = vanilla x
        if (snappedX == 0) snapLineX = width / 2;
        int snappedY = HotbarLayout.snapCenterY(dy, height, s, top, HotbarLayout.BOTTOM);
        if (snappedY != dy) snapLineY = height / 2;

        HotbarModule.offsetX = HotbarLayout.clampOffsetX(snappedX, width, s, left, right);
        HotbarModule.offsetY = HotbarLayout.clampOffsetY(snappedY, height, s, top, HotbarLayout.BOTTOM);
    }

    /** Corner drag: scale 50-150 % around the bottom-centre anchor, snaps to 100 % (vanilla size). */
    private void resizeHotbar(double mouseX, double mouseY) {
        float diff = (float) ((mouseX - startX) + (mouseY - startY)) / 1.414f;
        float newScale = HotbarLayout.clampScale(startScale + diff / 100.0f);
        if (Math.abs(newScale - 1.0f) < 0.03f) newScale = 1.0f;
        HotbarModule.scale = HotbarLayout.clampScale(Math.round(newScale * 100f) / 100f);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Armor HUD ("armorHud"): placement from ArmorHudRenderer (free or hotbar anchor), anchored = not draggable
    // ------------------------------------------------------------------------------------------------------------

    /** Hint above (or below) the anchored Armor HUD: it follows the hotbar and can only be moved with Anchor: Free. */
    private void drawArmorAnchorHint(DrawContext context, int x, int y, int w, int h, float s) {
        Text line1 = CustomGuiUtils.getFontText("Anchored to the hotbar (" + ArmorHudLayout.anchorName(ArmorHudModule.anchor) + ")");
        Text line2 = CustomGuiUtils.getFontText("Set Anchor to Free in the menu to move it");
        int boxBottom = y + (int) Math.ceil(h * s);
        int textY = y - 24 >= 0 ? y - 24 : boxBottom + 4;
        int tw = Math.max(client.textRenderer.getWidth(line1), client.textRenderer.getWidth(line2));
        int textX = Math.max(2, Math.min(width - tw - 2, x));
        context.drawText(client.textRenderer, line1, textX, textY, 0xFFFFFFFF, true);
        context.drawText(client.textRenderer, line2, textX, textY + 11, 0xFF8E95A4, true);
    }

    /**
     * Left click on the Armor HUD: X = hide, corner = scale, side/bottom pill = minimum size, body = drag (free) or
     * anchor hint (hotbar anchor). Same hit areas as {@link #checkControls}, scaled with the effective size.
     */
    private boolean checkArmorControls(double mx, double my) {
        ArmorHudRenderer.place(width, height, true);
        int x = ArmorHudRenderer.placedX(), y = ArmorHudRenderer.placedY();
        int w = ArmorHudRenderer.boxWidth(), h = ArmorHudRenderer.boxHeight();
        float s = ArmorHudRenderer.placedScale();
        float own = ArmorHudLayout.sanitizeScale(ArmorHudModule.scale);

        if (inside(mx, my, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
            ArmorHudModule.enabled = false;
            BameClientConfig.save();
            return true;
        }
        int mode = -1;
        if (inside(mx, my, x + w * s - 8 * s, y + h * s - 8 * s, 12 * s, 12 * s)) mode = 0;
        else if (inside(mx, my, x + w * s - 6 * s, y + (h * s) / 2 - 10 * s, 14 * s, 20 * s)) mode = 1;
        else if (inside(mx, my, x + (w * s) / 2 - 10 * s, y + h * s - 6 * s, 20 * s, 14 * s)) mode = 2;
        if (mode >= 0) {
            resizingTarget = ARMOR_TARGET;
            resizeMode = mode;
            startX = (int) mx;
            startY = (int) my;
            startScale = own;
            armorScaleFactor = s / own;
            startW = w;
            startH = h;
            return true;
        }
        if (inside(mx, my, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
            if (ArmorHudModule.isAnchored()) {
                armorHintUntil = System.currentTimeMillis() + 2500L;
                return true;
            }
            draggingTarget = ARMOR_TARGET;
            dragOffsetX = (int) mx - x;
            dragOffsetY = (int) my - y;
            return true;
        }
        return false;
    }

    /** Resize like the other elements, but the stored scale is the Armor HUD's own (the anchor multiplies the hotbar size). */
    private void resizeArmor(double mouseX, double mouseY) {
        float factor = Math.max(0.01f, armorScaleFactor);
        if (resizeMode == 0) {
            float diff = (float) ((mouseX - startX) + (mouseY - startY)) / 1.414f;
            ArmorHudModule.scale = Math.max(0.5f, Math.min(3.0f, startScale + diff / 100.0f / factor));
        } else if (resizeMode == 1) {
            float deltaW = (float) (mouseX - startX) / Math.max(0.01f, startScale * factor);
            ArmorHudModule.customWidth = Math.max(20, Math.round(startW + deltaW));
        } else if (resizeMode == 2) {
            float deltaH = (float) (mouseY - startY) / Math.max(0.01f, startScale * factor);
            ArmorHudModule.customHeight = Math.max(14, Math.round(startH + deltaH));
        }
    }

    private void drawBoundingControls(DrawContext context, int x, int y, int w, int h, float scale) {
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)x, (float)y);
        context.getMatrices().scale(scale, scale);

        context.fill(-2, -2, w + 2, h + 2, 0x22FFFFFF);
        // X button (top right)
        context.getMatrices().pushMatrix();
        context.getMatrices().translate((float)(w - 2), 2f);
        context.getMatrices().rotate((float)Math.toRadians(45));
        context.fill(-4, -1, 4, 1, 0xFFFF5555);
        context.fill(-1, -4, 1, 4, 0xFFFF5555);
        context.getMatrices().popMatrix();

        // Corner resize chevron (bottom right)
        context.fill(w - 5, h + 1, w + 3, h + 3, 0xFFFFFFFF);
        context.fill(w + 1, h - 5, w + 3, h + 3, 0xFFFFFFFF);

        // Right edge (side) handle pill
        context.fill(w + 1, h / 2 - 4, w + 3, h / 2 + 4, 0xFFFFFFFF);

        // Bottom edge handle pill
        context.fill(w / 2 - 4, h + 1, w / 2 + 4, h + 3, 0xFFFFFFFF);

        context.getMatrices().popMatrix();
    }

    private int snap(int value, int target) {
        if (Math.abs(value - target) < 5) return target;
        return value;
    }

    private boolean checkControls(double mx, double my, String target, int x, int y, int w, int h, float s, Runnable onClose) {
        // 1. Close button (top right)
        if (inside(mx, my, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
            onClose.run();
            com.bame.client.BameClientConfig.save();
            return true;
        }
        // 2. Corner resize (bottom right)
        if (inside(mx, my, x + w * s - 8 * s, y + h * s - 8 * s, 12 * s, 12 * s)) {
            resizingTarget = target;
            resizeMode = 0;
            startX = (int)mx;
            startY = (int)my;
            startScale = s;
            startW = w;
            startH = h;
            return true;
        }
        // 3. Right edge (side) resize - center pill at (w, h / 2)
        if (inside(mx, my, x + w * s - 6 * s, y + (h * s) / 2 - 10 * s, 14 * s, 20 * s)) {
            resizingTarget = target;
            resizeMode = 1;
            startX = (int)mx;
            startY = (int)my;
            startScale = s;
            startW = w;
            startH = h;
            return true;
        }
        // 4. Bottom edge resize - center pill at (w / 2, h)
        if (inside(mx, my, x + (w * s) / 2 - 10 * s, y + h * s - 6 * s, 20 * s, 14 * s)) {
            resizingTarget = target;
            resizeMode = 2;
            startX = (int)mx;
            startY = (int)my;
            startScale = s;
            startW = w;
            startH = h;
            return true;
        }
        // 5. Body drag
        if (inside(mx, my, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
            draggingTarget = target;
            dragOffsetX = (int)mx - x;
            dragOffsetY = (int)my - y;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.gui.Click click, boolean twice) {
        double mouseX = click.x(); double mouseY = click.y();

        // 0. Hotbar popup (right-click menu of the vanilla hotbar group)
        if (hotbarPopupOpen) {
            return handleHotbarPopupClick(mouseX, mouseY, click.button());
        }

        // 1. Color Picker interaction
        if (activeColorPickerTarget != null) {
            if (inside(mouseX, mouseY, colorPickerX, colorPickerY, CP_W, CP_H)) {
                // Close button
                if (inside(mouseX, mouseY, colorPickerX + CP_W - 16, colorPickerY + 4, 14, 14)) {
                    activeColorPickerTarget = null;
                    return true;
                }
                // SV Box
                int svX = colorPickerX + 46; int svY = colorPickerY + 22; int svW = 92; int svH = 30;
                if (inside(mouseX, mouseY, svX, svY, svW, svH)) {
                    colorPickerDrag = 0;
                    updateColorPicker(mouseX, mouseY);
                    return true;
                }
                // Hue Bar
                int barX = colorPickerX + 10; int barW = 128;
                int hueY = colorPickerY + 58; int barH = 8;
                if (inside(mouseX, mouseY, barX - 2, hueY - 2, barW + 4, barH + 4)) {
                    colorPickerDrag = 1;
                    updateColorPicker(mouseX, mouseY);
                    return true;
                }
                // Alpha Bar
                int alphaY = colorPickerY + 72;
                if (inside(mouseX, mouseY, barX - 2, alphaY - 2, barW + 4, barH + 4)) {
                    colorPickerDrag = 2;
                    updateColorPicker(mouseX, mouseY);
                    return true;
                }
                return true;
            } else if (popupTarget != null && inside(mouseX, mouseY, popupX, popupY, POPUP_W, POPUP_H)) {
                // Inside popup
            } else {
                activeColorPickerTarget = null;
            }
        }

        // 2. Popup interaction
        if (popupTarget != null) {
            if (click.button() == 0) {
                if (inside(mouseX, mouseY, popupX + POPUP_W - 18, popupY + 4, 16, 16)) {
                    popupTarget = null;
                    activeColorPickerTarget = null;
                    return true;
                }
                int curBg = getBgMode(popupTarget);
                for (int i = 0; i < 4; i++) {
                    int by = popupY + 24 + i * 21;
                    if (i == 3 && curBg == 3) {
                        int btnW = POPUP_W - 16 - 22;
                        if (inside(mouseX, mouseY, popupX + 8, by, btnW, 18)) {
                            setBgMode(popupTarget, 3);
                            openColorPicker(popupTarget);
                            return true;
                        }
                        int sqX = popupX + POPUP_W - 24;
                        int sqY = by + 1;
                        if (inside(mouseX, mouseY, sqX - 2, sqY - 2, 20, 20)) {
                            openColorPicker(popupTarget);
                            return true;
                        }
                    } else {
                        if (inside(mouseX, mouseY, popupX + 8, by, POPUP_W - 16, 18)) {
                            setBgMode(popupTarget, i);
                            if (i == 3) {
                                openColorPicker(popupTarget);
                            }
                            return true;
                        }
                    }
                }
            }
            if (activeColorPickerTarget == null) {
                popupTarget = null;
            }
            return true;
        }

        // "Reset Positions" button
        if (click.button() == 0 && inside(mouseX, mouseY, getResetBtnX(), RESET_BTN_Y, getResetBtnW(), RESET_BTN_H)) {
            resetAllPositions();
            return true;
        }

        // Right-click: Open Background context popup!
        if (click.button() == 1) {
            if (FpsModule.enabled && (!ServerInfoModule.enabled || !ServerInfoModule.isDocked("fps"))) {
                int w = StatusHudRenderer.getFpsWidth(client); int h = StatusHudRenderer.getFpsHeight(); float s = FpsModule.scale;
                if (inside(mouseX, mouseY, FpsModule.hudX - 2, FpsModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("fps", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (PingModule.enabled && (!ServerInfoModule.enabled || !ServerInfoModule.isDocked("ping"))) {
                int w = StatusHudRenderer.getPingWidth(client); int h = StatusHudRenderer.getPingHeight(); float s = PingModule.scale;
                if (inside(mouseX, mouseY, PingModule.hudX - 2, PingModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("ping", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (CpsModule.enabled) {
                int w = StatusHudRenderer.getCpsWidth(client); int h = StatusHudRenderer.getCpsHeight(); float s = CpsModule.scale;
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
                java.util.List<String> active = ServerInfoModule.getActiveDockedElements();
                if (!active.isEmpty()) {
                    int w = StatusHudRenderer.getServerInfoWidth(client); int h = StatusHudRenderer.getServerInfoHeight(); float s = ServerInfoModule.scale;
                    if (inside(mouseX, mouseY, ServerInfoModule.hudX - 2, ServerInfoModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                        openPopup("serverInfo", (int)mouseX, (int)mouseY); return true;
                    }
                }
                if (ServerInfoModule.showName && !ServerInfoModule.isDocked("name")) {
                    int w = StatusHudRenderer.getElementWidth(client, "name") + 16; int h = 18; float s = ServerInfoModule.scale;
                    if (inside(mouseX, mouseY, ServerInfoModule.nameX - 2, ServerInfoModule.nameY - 2, (w + 4) * s, (h + 4) * s)) {
                        openPopup("serverInfo", (int)mouseX, (int)mouseY); return true;
                    }
                }
                if (ServerInfoModule.showServer && !ServerInfoModule.isDocked("server")) {
                    int w = StatusHudRenderer.getElementWidth(client, "server") + 16; int h = 18; float s = ServerInfoModule.scale;
                    if (inside(mouseX, mouseY, ServerInfoModule.serverX - 2, ServerInfoModule.serverY - 2, (w + 4) * s, (h + 4) * s)) {
                        openPopup("serverInfo", (int)mouseX, (int)mouseY); return true;
                    }
                }
                if (ServerInfoModule.showTime && !ServerInfoModule.isDocked("time")) {
                    int w = StatusHudRenderer.getElementWidth(client, "time") + 16; int h = 18; float s = ServerInfoModule.scale;
                    if (inside(mouseX, mouseY, ServerInfoModule.timeX - 2, ServerInfoModule.timeY - 2, (w + 4) * s, (h + 4) * s)) {
                        openPopup("serverInfo", (int)mouseX, (int)mouseY); return true;
                    }
                }
            }
            if (FakeScoreboardModule.enabled) {
                int w = FakeScoreboardRenderer.getWidth(client); int h = FakeScoreboardRenderer.getHeight(); float s = FakeScoreboardModule.scale;
                int x = FakeScoreboardModule.hudX; if (x == -1) x = width - (int)(w * s) - 3;
                int y = FakeScoreboardModule.hudY; if (y == -1) y = (height - (int)(h * s)) / 2;
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("fakeScoreboard", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (ScoreboardModule.enabled) {
                int w = ScoreboardRenderer.getWidth(client); int h = ScoreboardRenderer.getHeight(client); float s = ScoreboardModule.scale;
                int x = ScoreboardModule.hudX; if (x == -1) x = width - (int)(w * s) - 3;
                int y = ScoreboardModule.hudY; if (y == -1) y = (height - (int)(h * s)) / 2;
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("scoreboard", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (ClockModule.enabled) {
                int w = StatusHudRenderer.getClockWidth(client); int h = StatusHudRenderer.getClockHeight(); float s = ClockModule.scale;
                if (inside(mouseX, mouseY, ClockModule.hudX - 2, ClockModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("clock", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (CoordinatesModule.enabled) {
                int w = StatusHudRenderer.getCoordinatesWidth(client); int h = StatusHudRenderer.getCoordinatesHeight(); float s = CoordinatesModule.scale;
                if (inside(mouseX, mouseY, CoordinatesModule.hudX - 2, CoordinatesModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("coordinates", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (PotionsModule.enabled) {
                int w = StatusHudRenderer.getPotionsWidth(client, true); int h = StatusHudRenderer.getPotionsHeight(client, true); float s = PotionsModule.scale;
                if (inside(mouseX, mouseY, PotionsModule.hudX - 2, PotionsModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("potions", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (TargetHudModule.enabled) {
                int w = StatusHudRenderer.getTargetHudWidth(); int h = StatusHudRenderer.getTargetHudHeight(); float s = TargetHudModule.scale;
                int x = TargetHudModule.hudX; if (x == -1) x = (width - (int)(w * s)) / 2;
                int y = TargetHudModule.hudY; if (y == -1) y = height - 120;
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("targetHud", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (ArmorHudModule.enabled) {
                ArmorHudRenderer.place(width, height, true);
                int w = ArmorHudRenderer.boxWidth(); int h = ArmorHudRenderer.boxHeight(); float s = ArmorHudRenderer.placedScale();
                int x = ArmorHudRenderer.placedX(); int y = ArmorHudRenderer.placedY();
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup(ARMOR_TARGET, (int)mouseX, (int)mouseY); return true;
                }
            }
            if (SpotifyHudModule.enabled) {
                int w = SpotifyHudRenderer.getWidth(); int h = SpotifyHudRenderer.getHeight(); float s = SpotifyHudModule.scale;
                int x = SpotifyHudModule.hudX; if (x == -1) x = 10;
                int y = SpotifyHudModule.hudY; if (y == -1) y = 180;
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("spotifyHud", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (ReachDisplayModule.enabled && ReachDisplayModule.mode == 1) {
                int w = StatusHudRenderer.getReachDisplayWidth(client); int h = StatusHudRenderer.getReachDisplayHeight(); float s = ReachDisplayModule.scale;
                if (inside(mouseX, mouseY, ReachDisplayModule.hudX - 2, ReachDisplayModule.hudY - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup("reachDisplay", (int)mouseX, (int)mouseY); return true;
                }
            }
            if (InventoryHudModule.enabled) {
                InventoryHudRenderer.place(width, height, true);
                int w = InventoryHudRenderer.boxWidth(); int h = InventoryHudRenderer.boxHeight(); float s = InventoryHudRenderer.placedScale();
                int x = InventoryHudRenderer.placedX(); int y = InventoryHudRenderer.placedY();
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    openPopup(INVENTORY_TARGET, (int)mouseX, (int)mouseY); return true;
                }
            }
            if (insideHotbar(mouseX, mouseY, HotbarHud.currentBox(width, height))) {
                openHotbarPopup((int)mouseX, (int)mouseY); return true;
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

            // Close button
            if (inside(mouseX, mouseY, drawX + maxX * s - 10 * s, drawY + minY * s - 4 * s, 14 * s, 14 * s)) {
                KeyStrokesModule.enabled = false; com.bame.client.BameClientConfig.save(); return true;
            }
            // Corner resize
            if (inside(mouseX, mouseY, drawX + maxX * s - 8 * s, drawY + maxY * s - 8 * s, 12 * s, 12 * s)) {
                resizingTarget = "keystrokes"; resizeMode = 0; startX = (int)mouseX; startY = (int)mouseY; startScale = s; return true;
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
        if (FpsModule.enabled && (!ServerInfoModule.enabled || !ServerInfoModule.isDocked("fps"))) {
            int w = StatusHudRenderer.getFpsWidth(client); int h = StatusHudRenderer.getFpsHeight(); float s = FpsModule.scale;
            if (checkControls(mouseX, mouseY, "fps", FpsModule.hudX, FpsModule.hudY, w, h, s, () -> FpsModule.enabled = false)) return true;
        }

        // 3. Check Ping
        if (PingModule.enabled && (!ServerInfoModule.enabled || !ServerInfoModule.isDocked("ping"))) {
            int w = StatusHudRenderer.getPingWidth(client); int h = StatusHudRenderer.getPingHeight(); float s = PingModule.scale;
            if (checkControls(mouseX, mouseY, "ping", PingModule.hudX, PingModule.hudY, w, h, s, () -> PingModule.enabled = false)) return true;
        }

        // 4. Check CPS
        if (CpsModule.enabled) {
            int w = StatusHudRenderer.getCpsWidth(client); int h = StatusHudRenderer.getCpsHeight(); float s = CpsModule.scale;
            if (checkControls(mouseX, mouseY, "cps", CpsModule.hudX, CpsModule.hudY, w, h, s, () -> CpsModule.enabled = false)) return true;
        }

        // 5. Check Server Info
        if (ServerInfoModule.enabled) {
            java.util.List<String> active = ServerInfoModule.getActiveDockedElements();
            if (!active.isEmpty()) {
                int w = StatusHudRenderer.getServerInfoWidth(client); int h = StatusHudRenderer.getServerInfoHeight(); float s = ServerInfoModule.scale;
                int x = ServerInfoModule.hudX;
                if (x == -1) {
                    x = width - (int)(w * s) - 10;
                    ServerInfoModule.hudX = x;
                }
                int y = ServerInfoModule.hudY;
                if (inside(mouseX, mouseY, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
                    ServerInfoModule.enabled = false;
                    if (ServerInfoModule.isDocked("fps")) FpsModule.enabled = false;
                    if (ServerInfoModule.isDocked("ping")) PingModule.enabled = false;
                    com.bame.client.BameClientConfig.save();
                    return true;
                }
                if (inside(mouseX, mouseY, x + w * s - 8 * s, y + h * s - 8 * s, 12 * s, 12 * s)) {
                    resizingTarget = "serverInfo"; resizeMode = 0; startX = (int)mouseX; startY = (int)mouseY; startScale = s; startW = w; startH = h; return true;
                }
                if (inside(mouseX, mouseY, x + w * s - 6 * s, y + (h * s) / 2 - 10 * s, 14 * s, 20 * s)) {
                    resizingTarget = "serverInfo"; resizeMode = 1; startX = (int)mouseX; startY = (int)mouseY; startScale = s; startW = w; startH = h; return true;
                }
                if (inside(mouseX, mouseY, x + (w * s) / 2 - 10 * s, y + h * s - 6 * s, 20 * s, 14 * s)) {
                    resizingTarget = "serverInfo"; resizeMode = 2; startX = (int)mouseX; startY = (int)mouseY; startScale = s; startW = w; startH = h; return true;
                }
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    String seg = getDockedSegmentAt(mouseX, mouseY);
                    clickedDockedSegment = seg;
                    clickStartX = (int)mouseX;
                    clickStartY = (int)mouseY;
                    initialServerInfoX = ServerInfoModule.hudX;
                    initialServerInfoY = ServerInfoModule.hudY;
                    isDetached = false;
                    draggingTarget = "serverInfo";
                    dragOffsetX = (int)mouseX - x;
                    dragOffsetY = (int)mouseY - y;
                    return true;
                }
            }

            // Standalone name
            if (ServerInfoModule.showName && !ServerInfoModule.isDocked("name")) {
                int w = StatusHudRenderer.getElementWidth(client, "name") + 16; int h = 18; float s = ServerInfoModule.scale;
                int x = ServerInfoModule.nameX; int y = ServerInfoModule.nameY;
                if (inside(mouseX, mouseY, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
                    ServerInfoModule.dock("name"); com.bame.client.BameClientConfig.save(); return true;
                }
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    draggingTarget = "serverInfo_name"; dragOffsetX = (int)mouseX - x; dragOffsetY = (int)mouseY - y; return true;
                }
            }

            // Standalone server
            if (ServerInfoModule.showServer && !ServerInfoModule.isDocked("server")) {
                int w = StatusHudRenderer.getElementWidth(client, "server") + 16; int h = 18; float s = ServerInfoModule.scale;
                int x = ServerInfoModule.serverX; int y = ServerInfoModule.serverY;
                if (inside(mouseX, mouseY, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
                    ServerInfoModule.dock("server"); com.bame.client.BameClientConfig.save(); return true;
                }
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    draggingTarget = "serverInfo_server"; dragOffsetX = (int)mouseX - x; dragOffsetY = (int)mouseY - y; return true;
                }
            }

            // Standalone time
            if (ServerInfoModule.showTime && !ServerInfoModule.isDocked("time")) {
                int w = StatusHudRenderer.getElementWidth(client, "time") + 16; int h = 18; float s = ServerInfoModule.scale;
                int x = ServerInfoModule.timeX; int y = ServerInfoModule.timeY;
                if (inside(mouseX, mouseY, x + w * s - 10 * s, y - 4 * s, 14 * s, 14 * s)) {
                    ServerInfoModule.dock("time"); com.bame.client.BameClientConfig.save(); return true;
                }
                if (inside(mouseX, mouseY, x - 2, y - 2, (w + 4) * s, (h + 4) * s)) {
                    draggingTarget = "serverInfo_time"; dragOffsetX = (int)mouseX - x; dragOffsetY = (int)mouseY - y; return true;
                }
            }
        }

        // 6. Check Fake Scoreboard
        if (FakeScoreboardModule.enabled) {
            int w = FakeScoreboardRenderer.getWidth(client); int h = FakeScoreboardRenderer.getHeight(); float s = FakeScoreboardModule.scale;
            int x = FakeScoreboardModule.hudX;
            if (x == -1) { x = width - (int)(w * s) - 3; FakeScoreboardModule.hudX = x; }
            int y = FakeScoreboardModule.hudY;
            if (y == -1) { y = (height - (int)(h * s)) / 2; FakeScoreboardModule.hudY = y; }
            if (checkControls(mouseX, mouseY, "fakeScoreboard", x, y, w, h, s, () -> FakeScoreboardModule.enabled = false)) return true;
        }

        // 6b. Check Real Scoreboard
        if (ScoreboardModule.enabled) {
            int w = ScoreboardRenderer.getWidth(client); int h = ScoreboardRenderer.getHeight(client); float s = ScoreboardModule.scale;
            int x = ScoreboardModule.hudX;
            if (x == -1) { x = width - (int)(w * s) - 3; ScoreboardModule.hudX = x; }
            int y = ScoreboardModule.hudY;
            if (y == -1) { y = (height - (int)(h * s)) / 2; ScoreboardModule.hudY = y; }
            if (checkControls(mouseX, mouseY, "scoreboard", x, y, w, h, s, () -> ScoreboardModule.enabled = false)) return true;
        }

        // 7. Check Clock
        if (ClockModule.enabled) {
            int w = StatusHudRenderer.getClockWidth(client); int h = StatusHudRenderer.getClockHeight(); float s = ClockModule.scale;
            if (checkControls(mouseX, mouseY, "clock", ClockModule.hudX, ClockModule.hudY, w, h, s, () -> ClockModule.enabled = false)) return true;
        }

        // 8. Check Coordinates
        if (CoordinatesModule.enabled) {
            int w = StatusHudRenderer.getCoordinatesWidth(client); int h = StatusHudRenderer.getCoordinatesHeight(); float s = CoordinatesModule.scale;
            if (checkControls(mouseX, mouseY, "coordinates", CoordinatesModule.hudX, CoordinatesModule.hudY, w, h, s, () -> CoordinatesModule.enabled = false)) return true;
        }

        // 9. Check Potions
        if (PotionsModule.enabled) {
            int w = StatusHudRenderer.getPotionsWidth(client, true); int h = StatusHudRenderer.getPotionsHeight(client, true); float s = PotionsModule.scale;
            if (checkControls(mouseX, mouseY, "potions", PotionsModule.hudX, PotionsModule.hudY, w, h, s, () -> PotionsModule.enabled = false)) return true;
        }

        // 10. Check Target HUD
        if (TargetHudModule.enabled) {
            int w = StatusHudRenderer.getTargetHudWidth(); int h = StatusHudRenderer.getTargetHudHeight(); float s = TargetHudModule.scale;
            int x = TargetHudModule.hudX;
            if (x == -1) { x = (width - (int)(w * s)) / 2; TargetHudModule.hudX = x; }
            int y = TargetHudModule.hudY;
            if (y == -1) { y = height - 120; TargetHudModule.hudY = y; }
            if (checkControls(mouseX, mouseY, "targetHud", x, y, w, h, s, () -> {
                TargetHudModule.enabled = false;
                TargetHudModule.showHearts = false;
                TargetHudModule.showArmor = false;
            })) return true;
        }

        // 11. Check Armor HUD (free: like the others; hotbar anchor: resize only, body click shows the anchor hint)
        if (ArmorHudModule.enabled && checkArmorControls(mouseX, mouseY)) return true;

        // 12. Check Spotify HUD
        if (SpotifyHudModule.enabled) {
            int w = SpotifyHudRenderer.getWidth(); int h = SpotifyHudRenderer.getHeight(); float s = SpotifyHudModule.scale;
            int x = SpotifyHudModule.hudX;
            if (x == -1) { x = 10; SpotifyHudModule.hudX = x; }
            int y = SpotifyHudModule.hudY;
            if (y == -1) { y = 180; SpotifyHudModule.hudY = y; }
            if (checkControls(mouseX, mouseY, "spotifyHud", x, y, w, h, s, () -> SpotifyHudModule.enabled = false)) return true;
        }

        // 13. Check Reach Display
        if (ReachDisplayModule.enabled && ReachDisplayModule.mode == 1) {
            int w = StatusHudRenderer.getReachDisplayWidth(client); int h = StatusHudRenderer.getReachDisplayHeight(); float s = ReachDisplayModule.scale;
            if (checkControls(mouseX, mouseY, "reachDisplay", ReachDisplayModule.hudX, ReachDisplayModule.hudY, w, h, s, () -> ReachDisplayModule.enabled = false)) return true;
        }

        // 13b. Check Inventory HUD (placement from the renderer: stored or auto bottom right, clamped to the screen)
        if (InventoryHudModule.enabled) {
            InventoryHudRenderer.place(width, height, true);
            int w = InventoryHudRenderer.boxWidth(); int h = InventoryHudRenderer.boxHeight(); float s = InventoryHudRenderer.placedScale();
            if (checkControls(mouseX, mouseY, INVENTORY_TARGET, InventoryHudRenderer.placedX(), InventoryHudRenderer.placedY(), w, h, s,
                    () -> InventoryHudModule.enabled = false)) return true;
        }

        // 14. Check vanilla hotbar group (last: it is large and drawn below the other elements)
        if (checkHotbarControls(mouseX, mouseY)) return true;

        return super.mouseClicked(click, twice);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.gui.Click click, double deltaX, double deltaY) {
        double mouseX = click.x(); double mouseY = click.y();

        if (colorPickerDrag >= 0 && activeColorPickerTarget != null) {
            updateColorPicker(mouseX, mouseY);
            return true;
        }

        // Each drag step recomputes the snap guides; render() only draws them
        snapLineX = -1;
        snapLineY = -1;

        if (resizingTarget.equals(HOTBAR_TARGET)) {
            resizeHotbar(mouseX, mouseY);
            return true;
        }

        if (resizingTarget.equals(ARMOR_TARGET)) {
            resizeArmor(mouseX, mouseY);
            return true;
        }

        if (!resizingTarget.equals(NoneTarget)) {
            if (resizeMode == 0) { // corner chevron -> scale proportionally
                float diff = (float)((mouseX - startX) + (mouseY - startY)) / 1.414f;
                float newScale = Math.max(0.5f, Math.min(3.0f, startScale + diff / 100.0f));
                setTargetScale(resizingTarget, newScale);
            } else if (resizeMode == 1) { // right side pill -> width only
                float deltaW = (float)(mouseX - startX) / Math.max(0.01f, startScale);
                int newWidth = Math.max(20, Math.round(startW + deltaW));
                setTargetCustomWidth(resizingTarget, newWidth);
            } else if (resizeMode == 2) { // bottom middle pill -> height only
                float deltaH = (float)(mouseY - startY) / Math.max(0.01f, startScale);
                int newHeight = Math.max(14, Math.round(startH + deltaH));
                setTargetCustomHeight(resizingTarget, newHeight);
            }
            return true;
        }

        if (draggingTarget.equals(HOTBAR_TARGET)) {
            dragHotbar(mouseX, mouseY);
            return true;
        }

        if (draggingKey != null) {
            float s = KeyStrokesModule.scale;
            int newX = (int)((mouseX - dragOffsetX - KeyStrokesModule.hudX) / s);
            int newY = (int)((mouseY - dragOffsetY - KeyStrokesModule.hudY) / s);
            // Keep the dragged key inside the screen
            int minRelX = (int)Math.ceil(-KeyStrokesModule.hudX / s);
            int maxRelX = (int)Math.floor((width - KeyStrokesModule.hudX) / s) - draggingKey.width;
            int minRelY = (int)Math.ceil(-KeyStrokesModule.hudY / s);
            int maxRelY = (int)Math.floor((height - KeyStrokesModule.hudY) / s) - draggingKey.height;
            int relX = Math.round(newX / 2f) * 2;
            int relY = Math.round(newY / 2f) * 2;
            draggingKey.relX = Math.max(minRelX, Math.min(relX, maxRelX));
            draggingKey.relY = Math.max(minRelY, Math.min(relY, maxRelY));
            return true;
        }

        // Check if pulling apart a docked segment from Server Info
        if (draggingTarget.equals("serverInfo") && !isDetached && clickedDockedSegment != null && ServerInfoModule.dockedElements.size() > 1) {
            int dx = (int)mouseX - clickStartX;
            int dy = (int)mouseY - clickStartY;
            if (Math.abs(dy) > 12 || Math.abs(dx) > 35) {
                isDetached = true;
                String seg = clickedDockedSegment;
                ServerInfoModule.undock(seg);
                ServerInfoModule.hudX = initialServerInfoX;
                ServerInfoModule.hudY = initialServerInfoY;

                if (seg.equals("fps")) {
                    FpsModule.enabled = true;
                    int elemW = (int)(StatusHudRenderer.getFpsWidth(client) * FpsModule.scale);
                    FpsModule.hudX = (int)mouseX - elemW / 2;
                    FpsModule.hudY = (int)mouseY - (int)(9 * FpsModule.scale);
                    draggingTarget = "fps";
                    dragOffsetX = elemW / 2;
                    dragOffsetY = (int)(9 * FpsModule.scale);
                } else if (seg.equals("ping")) {
                    PingModule.enabled = true;
                    int elemW = (int)(StatusHudRenderer.getPingWidth(client) * PingModule.scale);
                    PingModule.hudX = (int)mouseX - elemW / 2;
                    PingModule.hudY = (int)mouseY - (int)(9 * PingModule.scale);
                    draggingTarget = "ping";
                    dragOffsetX = elemW / 2;
                    dragOffsetY = (int)(9 * PingModule.scale);
                } else if (seg.equals("name")) {
                    int elemW = (int)((StatusHudRenderer.getElementWidth(client, "name") + 16) * ServerInfoModule.scale);
                    ServerInfoModule.nameX = (int)mouseX - elemW / 2;
                    ServerInfoModule.nameY = (int)mouseY - (int)(9 * ServerInfoModule.scale);
                    draggingTarget = "serverInfo_name";
                    dragOffsetX = elemW / 2;
                    dragOffsetY = (int)(9 * ServerInfoModule.scale);
                } else if (seg.equals("server")) {
                    int elemW = (int)((StatusHudRenderer.getElementWidth(client, "server") + 16) * ServerInfoModule.scale);
                    ServerInfoModule.serverX = (int)mouseX - elemW / 2;
                    ServerInfoModule.serverY = (int)mouseY - (int)(9 * ServerInfoModule.scale);
                    draggingTarget = "serverInfo_server";
                    dragOffsetX = elemW / 2;
                    dragOffsetY = (int)(9 * ServerInfoModule.scale);
                } else if (seg.equals("time")) {
                    int elemW = (int)((StatusHudRenderer.getElementWidth(client, "time") + 16) * ServerInfoModule.scale);
                    ServerInfoModule.timeX = (int)mouseX - elemW / 2;
                    ServerInfoModule.timeY = (int)mouseY - (int)(9 * ServerInfoModule.scale);
                    draggingTarget = "serverInfo_time";
                    dragOffsetX = elemW / 2;
                    dragOffsetY = (int)(9 * ServerInfoModule.scale);
                }
            }
        }

        if (!draggingTarget.equals(NoneTarget)) {
            int newX = (int)mouseX - dragOffsetX;
            int newY = (int)mouseY - dragOffsetY;

            int gw = 60; int gh = 18; float s = 1.0f;
            switch (draggingTarget) {
                case "keystrokes" -> { gw = (int)((getKeyMaxX() - getKeyMinX()) * KeyStrokesModule.scale); gh = (int)((getKeyMaxY() - getKeyMinY()) * KeyStrokesModule.scale); s = KeyStrokesModule.scale; }
                case "fps" -> { gw = (int)(StatusHudRenderer.getFpsWidth(client) * FpsModule.scale); gh = (int)(StatusHudRenderer.getFpsHeight() * FpsModule.scale); s = FpsModule.scale; }
                case "ping" -> { gw = (int)(StatusHudRenderer.getPingWidth(client) * PingModule.scale); gh = (int)(StatusHudRenderer.getPingHeight() * PingModule.scale); s = PingModule.scale; }
                case "cps" -> { gw = (int)(StatusHudRenderer.getCpsWidth(client) * CpsModule.scale); gh = (int)(StatusHudRenderer.getCpsHeight() * CpsModule.scale); s = CpsModule.scale; }
                case "serverInfo" -> { gw = (int)(StatusHudRenderer.getServerInfoWidth(client) * ServerInfoModule.scale); gh = (int)(StatusHudRenderer.getServerInfoHeight() * ServerInfoModule.scale); s = ServerInfoModule.scale; }
                case "serverInfo_name" -> { gw = (int)((StatusHudRenderer.getElementWidth(client, "name") + 16) * ServerInfoModule.scale); gh = (int)(18 * ServerInfoModule.scale); s = ServerInfoModule.scale; }
                case "serverInfo_server" -> { gw = (int)((StatusHudRenderer.getElementWidth(client, "server") + 16) * ServerInfoModule.scale); gh = (int)(18 * ServerInfoModule.scale); s = ServerInfoModule.scale; }
                case "serverInfo_time" -> { gw = (int)((StatusHudRenderer.getElementWidth(client, "time") + 16) * ServerInfoModule.scale); gh = (int)(18 * ServerInfoModule.scale); s = ServerInfoModule.scale; }
                case "fakeScoreboard" -> { gw = (int)(FakeScoreboardRenderer.getWidth(client) * FakeScoreboardModule.scale); gh = (int)(FakeScoreboardRenderer.getHeight() * FakeScoreboardModule.scale); s = FakeScoreboardModule.scale; }
                case "scoreboard" -> { gw = (int)(ScoreboardRenderer.getWidth(client) * ScoreboardModule.scale); gh = (int)(ScoreboardRenderer.getHeight(client) * ScoreboardModule.scale); s = ScoreboardModule.scale; }
                case "clock" -> { gw = (int)(StatusHudRenderer.getClockWidth(client) * ClockModule.scale); gh = (int)(StatusHudRenderer.getClockHeight() * ClockModule.scale); s = ClockModule.scale; }
                case "coordinates" -> { gw = (int)(StatusHudRenderer.getCoordinatesWidth(client) * CoordinatesModule.scale); gh = (int)(StatusHudRenderer.getCoordinatesHeight(client) * CoordinatesModule.scale); s = CoordinatesModule.scale; }
                case "potions" -> { gw = (int)(StatusHudRenderer.getPotionsWidth(client, true) * PotionsModule.scale); gh = (int)(StatusHudRenderer.getPotionsHeight(client, true) * PotionsModule.scale); s = PotionsModule.scale; }
                case "targetHud" -> { gw = (int)(StatusHudRenderer.getTargetHudWidth() * TargetHudModule.scale); gh = (int)(StatusHudRenderer.getTargetHudHeight() * TargetHudModule.scale); s = TargetHudModule.scale; }
                case "armorHud" -> {
                    ArmorHudRenderer.place(width, height, true);
                    s = ArmorHudRenderer.placedScale();
                    gw = ArmorHudLayout.scaledSize(ArmorHudRenderer.boxWidth(), s);
                    gh = ArmorHudLayout.scaledSize(ArmorHudRenderer.boxHeight(), s);
                }
                case "inventoryHud" -> {
                    InventoryHudRenderer.place(width, height, true);
                    s = InventoryHudRenderer.placedScale();
                    gw = InventoryHudLayout.scaledSize(InventoryHudRenderer.boxWidth(), s);
                    gh = InventoryHudLayout.scaledSize(InventoryHudRenderer.boxHeight(), s);
                }
                case "spotifyHud" -> { gw = (int)(SpotifyHudRenderer.getWidth() * SpotifyHudModule.scale); gh = (int)(SpotifyHudRenderer.getHeight() * SpotifyHudModule.scale); s = SpotifyHudModule.scale; }
                case "reachDisplay" -> { gw = (int)(StatusHudRenderer.getReachDisplayWidth(client) * ReachDisplayModule.scale); gh = (int)(StatusHudRenderer.getReachDisplayHeight() * ReachDisplayModule.scale); s = ReachDisplayModule.scale; }
            }

            int centerX = width / 2; int centerY = height / 2;
            int snapX = snap(newX + gw / 2, centerX);
            if (snapX == centerX) { newX = centerX - gw / 2; snapLineX = centerX; }
            int snapY = snap(newY + gh / 2, centerY);
            if (snapY == centerY) { newY = centerY - gh / 2; snapLineY = centerY; }

            if (draggingTarget.equals("keystrokes")) {
                // KeyStrokes: hudX/hudY is the layout origin, the visible keys span [min, max] * scale
                float ks = KeyStrokesModule.scale;
                int loX = (int)Math.ceil(-getKeyMinX() * ks), hiX = (int)Math.floor(width - getKeyMaxX() * ks);
                int loY = (int)Math.ceil(-getKeyMinY() * ks), hiY = (int)Math.floor(height - getKeyMaxY() * ks);
                newX = Math.max(loX, Math.min(newX, hiX));
                newY = Math.max(loY, Math.min(newY, hiY));
            } else {
                newX = Math.max(0, Math.min(newX, width - gw));
                newY = Math.max(0, Math.min(newY, height - gh));
            }

            // Snap docking check
            snapDockTarget = null;
            snapDockIndex = -1;
            if (ServerInfoModule.enabled && isDockableTarget(draggingTarget)) {
                int siX = ServerInfoModule.hudX;
                int siY = ServerInfoModule.hudY;
                float siScale = ServerInfoModule.scale;
                int siW = (int)(StatusHudRenderer.getServerInfoWidth(client) * siScale);
                int siH = (int)(18 * siScale);

                int draggedCenterX = newX + gw / 2;
                int draggedCenterY = newY + gh / 2;
                boolean nearY = Math.abs(draggedCenterY - (siY + siH / 2)) < 24 * siScale;
                boolean nearLeft = Math.abs(newX + gw - siX) < 30 * siScale;
                boolean nearRight = Math.abs(newX - (siX + siW)) < 30 * siScale;
                boolean insideBar = inside(draggedCenterX, draggedCenterY, siX - 10, siY - 10, siW + 20, siH + 20);

                if (nearY && (nearLeft || nearRight || insideBar)) {
                    snapDockTarget = "serverInfo";
                    if (nearLeft || draggedCenterX < siX + siW / 3) {
                        snapDockIndex = 0;
                    } else {
                        snapDockIndex = ServerInfoModule.dockedElements.size();
                    }
                }
            }

            switch (draggingTarget) {
                case "keystrokes" -> { KeyStrokesModule.hudX = newX; KeyStrokesModule.hudY = newY; }
                case "fps" -> { FpsModule.hudX = newX; FpsModule.hudY = newY; }
                case "ping" -> { PingModule.hudX = newX; PingModule.hudY = newY; }
                case "cps" -> { CpsModule.hudX = newX; CpsModule.hudY = newY; }
                case "serverInfo" -> { ServerInfoModule.hudX = newX; ServerInfoModule.hudY = newY; }
                case "serverInfo_name" -> { ServerInfoModule.nameX = newX; ServerInfoModule.nameY = newY; }
                case "serverInfo_server" -> { ServerInfoModule.serverX = newX; ServerInfoModule.serverY = newY; }
                case "serverInfo_time" -> { ServerInfoModule.timeX = newX; ServerInfoModule.timeY = newY; }
                case "fakeScoreboard" -> { FakeScoreboardModule.hudX = newX; FakeScoreboardModule.hudY = newY; }
                case "scoreboard" -> { ScoreboardModule.hudX = newX; ScoreboardModule.hudY = newY; }
                case "clock" -> { ClockModule.hudX = newX; ClockModule.hudY = newY; }
                case "coordinates" -> { CoordinatesModule.hudX = newX; CoordinatesModule.hudY = newY; }
                case "potions" -> { PotionsModule.hudX = newX; PotionsModule.hudY = newY; }
                case "targetHud" -> { TargetHudModule.hudX = newX; TargetHudModule.hudY = newY; }
                case "armorHud" -> { ArmorHudModule.hudX = newX; ArmorHudModule.hudY = newY; }
                case "inventoryHud" -> { InventoryHudModule.hudX = newX; InventoryHudModule.hudY = newY; }
                case "spotifyHud" -> { SpotifyHudModule.hudX = newX; SpotifyHudModule.hudY = newY; }
                case "reachDisplay" -> { ReachDisplayModule.hudX = newX; ReachDisplayModule.hudY = newY; }
            }
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.gui.Click click) {
        snapLineX = -1;
        snapLineY = -1;
        if (colorPickerDrag >= 0) {
            colorPickerDrag = -1;
            com.bame.client.BameClientConfig.save();
        }
        if (!resizingTarget.equals(NoneTarget) || !draggingTarget.equals(NoneTarget)) {
            com.bame.client.BameClientConfig.save();
        }
        if (snapDockTarget != null && snapDockTarget.equals("serverInfo")) {
            String elem = switch (draggingTarget) {
                case "fps" -> "fps";
                case "ping" -> "ping";
                case "serverInfo_name" -> "name";
                case "serverInfo_server" -> "server";
                case "serverInfo_time" -> "time";
                default -> null;
            };
            if (elem != null) {
                ServerInfoModule.dock(snapDockIndex, elem);
                if (elem.equals("fps")) FpsModule.enabled = true;
                if (elem.equals("ping")) PingModule.enabled = true;
                BameClientConfig.save();
            }
            snapDockTarget = null;
            snapDockIndex = -1;
        }
        draggingTarget = NoneTarget;
        resizingTarget = NoneTarget;
        draggingKey = null;
        clickedDockedSegment = null;
        isDetached = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (hotbarPopupOpen) {
                hotbarPopupOpen = false;
                return true;
            }
            if (activeColorPickerTarget != null) {
                activeColorPickerTarget = null;
                return true;
            }
            if (popupTarget != null) {
                popupTarget = null;
                return true;
            }
        }
        return super.keyPressed(input);
    }
}
