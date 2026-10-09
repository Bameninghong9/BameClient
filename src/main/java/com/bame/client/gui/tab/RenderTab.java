package com.bame.client.gui.tab;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.BameClientScreen;
import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import com.bame.client.gui.HudEditorScreen;
import com.bame.client.module.*;
import com.bame.client.render.CustomCrosshairRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RenderTab implements ClientTab {
    private boolean fullbrightExpanded = false;
    private boolean listeningFullbright = false;
    private boolean listeningShowHud = false;
    private boolean listeningZoom = false;
    private boolean listeningSpotify = false;
    private boolean listeningScoreboard = false;
    private boolean listeningCrosshair = false;
    private boolean listeningBlockOutline = false;
    private boolean listeningHandPosition = false;
    private boolean listeningItemSize = false;

    private boolean draggingFullbright = false;
    private boolean draggingBlockOutlineWidth = false;
    private boolean draggingBlockOutlineOpacity = false;
    private boolean draggingHandX = false;
    private boolean draggingHandY = false;
    private boolean draggingHandZ = false;
    private boolean draggingHandScale = false;
    private boolean draggingHandPitch = false;
    private boolean draggingHandYaw = false;
    private boolean draggingHandRoll = false;
    private boolean draggingItemScale = false;
    private boolean draggingItemYOffset = false;

    private int gridDragMode = -1;
    private int lastEditedCol = -1;
    private int lastEditedRow = -1;

    @Override
    public String getCategory() {
        return "Visuals";
    }

    public boolean fullbrightVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "fullbright".contains(q) || "brightness".contains(q) || "helligkeit".contains(q) || "gamma".contains(q) || "visuals".contains(q);
        return true;
    }

    public boolean zoomVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "zoom".contains(q) || "optifine".contains(q) || "herankommen".contains(q) || "visuals".contains(q);
        return true;
    }

    public boolean showHudVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            return "show hud".contains(q) || "hud".contains(q) || "fps".contains(q) || "ping".contains(q) ||
                    "cps".contains(q) || "keystrokes".contains(q) || "server info".contains(q) || "name protect".contains(q) ||
                    "clock".contains(q) || "coordinates".contains(q) || "potions".contains(q) || "target hud".contains(q) || "armor hud".contains(q);
        }
        return true;
    }

    public boolean scoreboardVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "scoreboard".contains(q);
        return true;
    }

    public boolean spotifyHudVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "spotify hud".contains(q) || "spotify".contains(q) || "music".contains(q) || "song".contains(q);
        return true;
    }

    public boolean customCrosshairVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "custom crosshair".contains(q) || "crosshair".contains(q) || "fadenkreuz".contains(q);
        return true;
    }

    public boolean blockOutlineVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "blockoutline".contains(q) || "block outline".contains(q) || "outline".contains(q) || "glow".contains(q);
        return true;
    }

    public boolean handPositionVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "handposition".contains(q) || "hand position".contains(q) || "item position".contains(q) || "hand".contains(q) || "position".contains(q) || "offset".contains(q) || "viewmodel".contains(q) || "doom".contains(q) || "waffe".contains(q) || "schwert".contains(q) || "mining".contains(q) || "abbau".contains(q) || "anim".contains(q) || "animation".contains(q);
        return true;
    }

    public boolean itemSizeVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "itemsize".contains(q) || "item size".contains(q) || "item scale".contains(q) || "size".contains(q) || "größe".contains(q) || "blocks".contains(q) || "items".contains(q);
        return true;
    }

    @Override
    public boolean isVisible(String search) {
        if (search.isEmpty()) return true;
        return fullbrightVisible(search) || zoomVisible(search) || showHudVisible(search)
                || scoreboardVisible(search) || spotifyHudVisible(search) || customCrosshairVisible(search)
                || blockOutlineVisible(search) || handPositionVisible(search) || itemSizeVisible(search);
    }

    public int getShowHudHeight() {
        if (!ShowHudModule.expanded) return 50;
        int extra = (ServerInfoModule.enabled ? 24 : 0) + (TargetHudModule.expanded ? 3 * 26 : 0) + (CoordinatesModule.expanded ? 8 * 26 : 0) + 28 + 10;
        return 50 + 26 + 8 + 10 * 26 + extra;
    }

    public int getScoreboardHeight() {
        return ScoreboardModule.expanded ? 104 : 50;
    }

    public int getBlockOutlineHeight() {
        return BlockOutlineModule.expanded ? 208 : 50;
    }

    public int getHandPositionHeight() {
        return HandPositionModule.expanded ? 280 : 50;
    }

    public int getFullbrightHeight() {
        return fullbrightExpanded ? 118 : 50;
    }

    public int getZoomHeight() {
        return ZoomModule.expanded ? 206 : 50;
    }

    public int getSpotifyHudHeight() {
        return SpotifyHudModule.expanded ? 130 : 50;
    }

    public int getCustomCrosshairHeight() {
        return CustomCrosshairModule.expanded ? 244 : 50;
    }

    public int getItemSizeHeight() {
        if (!ItemSizeModule.expanded) return 50;
        int itemRows = (ItemSizeModule.selectedItems.size() + 1) / 2;
        return 200 + (itemRows * 24);
    }

    @Override
    public void layout(BameClientScreen screen, int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
    }

    @Override
    public void calculateHeight(BameClientScreen screen, int columnWidth, ColumnTracker tracker, String search) {
        if (showHudVisible(search)) tracker.leftY += getShowHudHeight() + 12;
        if (scoreboardVisible(search)) tracker.leftY += getScoreboardHeight() + 12;
        if (blockOutlineVisible(search)) tracker.leftY += getBlockOutlineHeight() + 12;
        if (handPositionVisible(search)) tracker.leftY += getHandPositionHeight() + 12;

        if (fullbrightVisible(search)) tracker.rightY += getFullbrightHeight() + 12;
        if (zoomVisible(search)) tracker.rightY += getZoomHeight() + 12;
        if (spotifyHudVisible(search)) tracker.rightY += getSpotifyHudHeight() + 12;
        if (customCrosshairVisible(search)) tracker.rightY += getCustomCrosshairHeight() + 12;
        if (itemSizeVisible(search)) tracker.rightY += getItemSizeHeight() + 12;
    }

    @Override
    public void render(BameClientScreen screen, DrawContext c, int mx, int my, float delta,
                       int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        // Left Column
        if (showHudVisible(search)) {
            renderShowHud(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            tracker.leftY += getShowHudHeight() + 12;
        }
        if (scoreboardVisible(search)) {
            renderScoreboard(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            tracker.leftY += getScoreboardHeight() + 12;
        }
        if (blockOutlineVisible(search)) {
            renderBlockOutline(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            tracker.leftY += getBlockOutlineHeight() + 12;
        }
        if (handPositionVisible(search)) {
            renderHandPosition(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            tracker.leftY += getHandPositionHeight() + 12;
        }

        // Right Column
        if (fullbrightVisible(search)) {
            renderFullbright(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            tracker.rightY += getFullbrightHeight() + 12;
        }
        if (zoomVisible(search)) {
            renderZoom(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            tracker.rightY += getZoomHeight() + 12;
        }
        if (spotifyHudVisible(search)) {
            renderSpotifyHud(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            tracker.rightY += getSpotifyHudHeight() + 12;
        }
        if (customCrosshairVisible(search)) {
            renderCustomCrosshair(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            tracker.rightY += getCustomCrosshairHeight() + 12;
        }
        if (itemSizeVisible(search)) {
            renderItemSize(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            tracker.rightY += getItemSizeHeight() + 12;
        }
    }

    private void renderFullbright(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getFullbrightHeight();
        screen.moduleCard(c, x, y, w, h, FullbrightModule.enabled, fullbrightExpanded,
                "Fullbright", "Brightens the world like night vision", "NEW", "visuals", mx, my);

        if (!fullbrightExpanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningFullbright ? "..." : screen.formatKey(FullbrightModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Intensity", x + 14, curY + 4, 0xFFD4D8E0);
        int sx = x + 14;
        int sw = w - 64;
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 18, sw, 4, 0xFF303442, 2);

        float val = FullbrightModule.intensity;
        int fill = Math.round(sw * val);
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 18, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 16, 7, 8, 0xFFFFFFFF, 4);

        screen.text(c, Math.round(val * 100) + "%", sx + sw + 8, curY + 14, 0xFFD4D8E0);
    }

    private void renderShowHud(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getShowHudHeight();
        screen.moduleCard(c, x, y, w, h, ShowHudModule.enabled, ShowHudModule.expanded,
                "Show HUD", "On-screen widgets: FPS, CPS, Armor, etc.", "NEW", "visuals", mx, my);

        if (!ShowHudModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningShowHud ? "..." : screen.formatKey(ShowHudModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        // 1. Clock
        screen.text(c, "Clock", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, ClockModule.enabled, mx, my, delta);
        curY += 26;

        // 2. Coordinates
        boolean coordHover = screen.inside(mx, my, x + 8, curY, w - 50, 22);
        if (coordHover) {
            CustomGuiUtils.fillUltraRounded(c, x + 8, curY, w - 50, 22, 0x14FFFFFF, 4);
        }
        screen.text(c, (CoordinatesModule.expanded ? "- " : "+ ") + "Coordinates", x + 14, curY + 5, CoordinatesModule.expanded ? GuiTheme.accent() : 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, CoordinatesModule.enabled, mx, my, delta);
        curY += 26;

        if (CoordinatesModule.expanded) {
            screen.text(c, "Layout", x + 24, curY + 4, 0xFFB8BCC6);
            screen.button(c, CoordinatesModule.getLayoutName(), x + w - 76, curY, 64, 16, mx, my);
            curY += 26;

            screen.text(c, "Style", x + 24, curY + 4, 0xFFB8BCC6);
            screen.button(c, CoordinatesModule.getStyleName(), x + w - 76, curY, 64, 16, mx, my);
            curY += 26;

            screen.text(c, "Show X", x + 24, curY + 5, 0xFFB8BCC6);
            screen.toggle(c, x + w - 38, curY + 3, CoordinatesModule.showX, mx, my, delta);
            curY += 26;

            screen.text(c, "Show Y", x + 24, curY + 5, 0xFFB8BCC6);
            screen.toggle(c, x + w - 38, curY + 3, CoordinatesModule.showY, mx, my, delta);
            curY += 26;

            screen.text(c, "Show Z", x + 24, curY + 5, 0xFFB8BCC6);
            screen.toggle(c, x + w - 38, curY + 3, CoordinatesModule.showZ, mx, my, delta);
            curY += 26;

            screen.text(c, "Decimals (.0)", x + 24, curY + 5, 0xFFB8BCC6);
            screen.toggle(c, x + w - 38, curY + 3, CoordinatesModule.decimals, mx, my, delta);
            curY += 26;

            screen.text(c, "Nether Coords", x + 24, curY + 5, 0xFFB8BCC6);
            screen.toggle(c, x + w - 38, curY + 3, CoordinatesModule.showNether, mx, my, delta);
            curY += 26;

            screen.text(c, "Direction", x + 24, curY + 5, 0xFFB8BCC6);
            screen.toggle(c, x + w - 38, curY + 3, CoordinatesModule.showDirection, mx, my, delta);
            curY += 26;
        }

        // 3. Potions
        screen.text(c, "Potions", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, PotionsModule.enabled, mx, my, delta);
        curY += 26;

        // 4. Target HUD
        boolean thHover = screen.inside(mx, my, x + 8, curY, w - 16, 22);
        if (thHover) {
            CustomGuiUtils.fillUltraRounded(c, x + 8, curY, w - 16, 22, 0x14FFFFFF, 4);
        }
        screen.text(c, (TargetHudModule.expanded ? "- " : "+ ") + "Target HUD", x + 14, curY + 5, TargetHudModule.expanded ? GuiTheme.accent() : 0xFFD4D8E0);
        curY += 26;

        if (TargetHudModule.expanded) {
            screen.text(c, "Show Hearts", x + 24, curY + 5, 0xFFB8BCC6);
            screen.toggle(c, x + w - 38, curY + 3, TargetHudModule.showHearts, mx, my, delta);
            curY += 26;

            screen.text(c, "Show Armor", x + 24, curY + 5, 0xFFB8BCC6);
            screen.toggle(c, x + w - 38, curY + 3, TargetHudModule.showArmor, mx, my, delta);
            curY += 26;

            screen.text(c, "Target Players Only", x + 24, curY + 5, 0xFFB8BCC6);
            screen.toggle(c, x + w - 38, curY + 3, TargetHudModule.playersOnly, mx, my, delta);
            curY += 26;
        }

        // 5. Armor HUD
        screen.text(c, "Armor HUD", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, ArmorHudModule.enabled, mx, my, delta);
        curY += 26;

        // 6. KeyStrokes
        screen.text(c, "KeyStrokes", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, KeyStrokesModule.enabled, mx, my, delta);
        curY += 26;

        // 7. CPS
        screen.text(c, "CPS", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, CpsModule.enabled, mx, my, delta);
        curY += 26;

        // 8. FPS
        screen.text(c, "FPS", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, FpsModule.enabled, mx, my, delta);
        curY += 26;

        // 9. Ping
        screen.text(c, "Ping", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, PingModule.enabled, mx, my, delta);
        curY += 26;

        // 10. Server Info
        screen.text(c, "Server Info", x + 14, curY + 5, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 3, ServerInfoModule.enabled, mx, my, delta);
        curY += 26;

        if (ServerInfoModule.enabled) {
            screen.modeButton(c, "Name", x + 14, curY + 2, 44, 18, mx, my, ServerInfoModule.showName);
            screen.modeButton(c, "Server", x + 62, curY + 2, 48, 18, mx, my, ServerInfoModule.showServer);
            screen.modeButton(c, "Time", x + 114, curY + 2, 42, 18, mx, my, ServerInfoModule.showTime);
            curY += 24;
        }

        curY += 4;
        screen.button(c, "Edit HUD", x + 14, curY, 50, 16, mx, my);
        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderScoreboard(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getScoreboardHeight();
        screen.moduleCard(c, x, y, w, h, ScoreboardModule.enabled, ScoreboardModule.expanded,
                "Scoreboard", "Toggles vanilla sidebar scoreboard", "NEW", "visuals", mx, my);

        if (!ScoreboardModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningScoreboard ? "..." : screen.formatKey(ScoreboardModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.button(c, "Edit HUD", x + 14, curY, 50, 16, mx, my);
        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderZoom(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getZoomHeight();
        screen.moduleCard(c, x, y, w, h, ZoomModule.enabled, ZoomModule.expanded,
                "Zoom", "OptiFine-style smooth FOV zoom", "NEW", "visuals", mx, my);

        if (!ZoomModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningZoom ? "..." : screen.formatKey(ZoomModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Mode", x + 14, curY + 4, 0xFFD4D8E0);
        screen.button(c, ZoomModule.type == 0 ? "Hold" : "Toggle", x + w - 58, curY, 46, 16, mx, my);
        curY += 26;

        screen.text(c, "Animation", x + 14, curY + 4, 0xFFD4D8E0);
        screen.button(c, ZoomModule.mode == 0 ? "Smooth" : "Instant", x + w - 68, curY, 56, 16, mx, my);
        curY += 26;

        screen.text(c, "Scroll Zoom", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, ZoomModule.scrollZoom, mx, my, delta);
        curY += 26;

        screen.text(c, "Default Zoom", x + 14, curY + 4, 0xFFD4D8E0);
        screen.button(c, ZoomModule.getDefaultZoomName(), x + w - 48, curY, 36, 16, mx, my);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderSpotifyHud(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getSpotifyHudHeight();
        screen.moduleCard(c, x, y, w, h, SpotifyHudModule.enabled, SpotifyHudModule.expanded,
                "Spotify HUD", "Displays currently playing Spotify track", "NEW", "music", mx, my);

        if (!SpotifyHudModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningSpotify ? "..." : screen.formatKey(SpotifyHudModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Auto-Hide (wenn pausiert)", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, SpotifyHudModule.autoHide, mx, my, delta);
        curY += 24;

        screen.button(c, "Edit HUD", x + 14, curY, 50, 16, mx, my);
        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderCustomCrosshair(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getCustomCrosshairHeight();
        screen.moduleCard(c, x, y, w, h, CustomCrosshairModule.enabled, CustomCrosshairModule.expanded,
                "Crosshair", "Custom shape & color crosshair editor", "NEW", "visuals", mx, my);

        if (!CustomCrosshairModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningCrosshair ? "..." : screen.formatKey(CustomCrosshairModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);

        int gridY = y + 78;
        int gridX = x + 12;
        int cellSize = 7;
        int gridW = 15 * cellSize;
        int gridH = 15 * cellSize;

        CustomGuiUtils.fillUltraRounded(c, gridX - 1, gridY - 1, gridW + 2, gridH + 2, 0xFF14171E, 3);
        CustomGuiUtils.drawUltraRoundedOutline(c, gridX - 1, gridY - 1, gridW + 2, gridH + 2, 0xFF353C4D, 3);

        for (int r = 0; r < 15; r++) {
            for (int col = 0; col < 15; col++) {
                int cellX = gridX + col * cellSize;
                int cellY = gridY + r * cellSize;
                boolean active = CustomCrosshairModule.grid[r][col];
                int cellBg = active ? CustomCrosshairModule.color : 0xFF16181F;
                c.fill(cellX, cellY, cellX + cellSize, cellY + cellSize, cellBg);
                c.fill(cellX, cellY + cellSize - 1, cellX + cellSize, cellY + cellSize, 0x1EFFFFFF);
                c.fill(cellX + cellSize - 1, cellY, cellX + cellSize, cellY + cellSize - 1, 0x1EFFFFFF);

                if (col == 7 && r == 7) {
                    int blue = 0xFF3B82F6;
                    c.fill(cellX, cellY, cellX + cellSize, cellY + 1, blue);
                    c.fill(cellX, cellY + cellSize - 1, cellX + cellSize, cellY + cellSize, blue);
                    c.fill(cellX, cellY + 1, cellX + 1, cellY + cellSize - 1, blue);
                    c.fill(cellX + cellSize - 1, cellY + 1, cellX + cellSize, cellY + cellSize - 1, blue);
                }
            }
        }

        int prevX = gridX + gridW + 12;
        int prevW = Math.max(50, w - gridW - 24);
        int prevH = gridH;
        CustomGuiUtils.fillUltraRounded(c, prevX, gridY, prevW, prevH, 0x880D1117, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, prevX, gridY, prevW, prevH, 0xFF2A2E39, 4);

        int pcx = prevX + prevW / 2;
        int pcy = gridY + prevH / 2;
        CustomCrosshairRenderer.renderAt(c, pcx, pcy, 1, CustomCrosshairModule.color);

        int pY = gridY + gridH + 8;
        screen.text(c, "PRESETS", x + 12, pY + 4, 0xFF8E95A4);
        int pBtnSize = 17;
        int pGap = 2;
        int pStartX = x + 58;
        for (int i = 0; i < 9; i++) {
            int bx = pStartX + i * (pBtnSize + pGap);
            int by = pY;
            boolean hoverP = screen.inside(mx, my, bx, by, pBtnSize, pBtnSize);
            c.fill(bx, by, bx + pBtnSize, by + pBtnSize, 0xFF151820);
            CustomGuiUtils.drawUltraRoundedOutline(c, bx, by, pBtnSize, pBtnSize, hoverP ? GuiTheme.accent() : 0xFF2A2E39, 2);
            boolean[][] tmpl = CustomCrosshairModule.getPresetTemplate(i);
            for (int r = 0; r < 15; r++) {
                for (int col = 0; col < 15; col++) {
                    if (tmpl[r][col]) {
                        c.fill(bx + 1 + col, by + 1 + r, bx + 2 + col, by + 2 + r, 0xFFFFFFFF);
                    }
                }
            }
        }

        int actY = pY + 23;
        screen.text(c, "Color:", x + 12, actY + 4, 0xFFD4D8E0);
        int colBtnX = x + 44;
        int colBtnY = actY;
        int colBtnSize = 16;
        boolean hoverCol = screen.inside(mx, my, colBtnX, colBtnY, colBtnSize, colBtnSize);
        c.fill(colBtnX, colBtnY, colBtnX + colBtnSize, colBtnY + colBtnSize, CustomCrosshairModule.color);
        CustomGuiUtils.drawUltraRoundedOutline(c, colBtnX, colBtnY, colBtnSize, colBtnSize, hoverCol ? 0xFFFFFFFF : 0xFF353C4D, 2);

        screen.button(c, "Clear", x + 68, actY, 38, 16, mx, my);
        screen.button(c, "Reset", x + w - 58, actY, 46, 16, mx, my);
    }

    private void renderBlockOutline(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getBlockOutlineHeight();
        screen.moduleCard(c, x, y, w, h, BlockOutlineModule.enabled, BlockOutlineModule.expanded,
                "Block Outline", "Custom outline and glow on targeted block", "NEW", "visuals", mx, my);

        if (!BlockOutlineModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningBlockOutline ? "..." : screen.formatKey(BlockOutlineModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Color:", x + 14, curY + 4, 0xFFD4D8E0);
        int colBtnX = x + 56;
        int colBtnSize = 16;
        boolean hoverCol = screen.inside(mx, my, colBtnX, curY + 2, colBtnSize, colBtnSize);
        c.fill(colBtnX, curY + 2, colBtnX + colBtnSize, curY + 2 + colBtnSize, 0xFF000000 | (BlockOutlineModule.color & 0xFFFFFF));
        CustomGuiUtils.drawUltraRoundedOutline(c, colBtnX, curY + 2, colBtnSize, colBtnSize, hoverCol ? 0xFFFFFFFF : 0xFF353C4D, 2);
        curY += 26;

        screen.text(c, "Chroma (RGB)", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, BlockOutlineModule.chroma, mx, my, delta);
        curY += 26;

        String widthText = String.format(Locale.US, "%.1f", BlockOutlineModule.lineWidth);
        screen.text(c, "Width: " + widthText, x + 14, curY + 4, 0xFFD4D8E0);
        int wx = x + 82;
        int ww = w - 96;
        CustomGuiUtils.fillUltraRounded(c, wx, curY + 6, ww, 4, 0xFF303442, 2);
        float widthNorm = (BlockOutlineModule.lineWidth - 1.0f) / 5.0f;
        int wfill = Math.round(ww * Math.clamp(widthNorm, 0f, 1f));
        if (wfill > 0) CustomGuiUtils.fillUltraRounded(c, wx, curY + 6, wfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, wx + wfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        int pct = Math.round(BlockOutlineModule.opacity * 100);
        screen.text(c, "Opacity: " + pct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int ox = x + 90;
        int ow = w - 104;
        CustomGuiUtils.fillUltraRounded(c, ox, curY + 6, ow, 4, 0xFF303442, 2);
        float opNorm = (BlockOutlineModule.opacity - 0.2f) / 0.8f;
        int ofill = Math.round(ow * Math.clamp(opNorm, 0f, 1f));
        if (ofill > 0) CustomGuiUtils.fillUltraRounded(c, ox, curY + 6, ofill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, ox + ofill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderHandPosition(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getHandPositionHeight();
        screen.moduleCard(c, x, y, w, h, HandPositionModule.enabled, HandPositionModule.expanded,
                "Hand Position", "Customize main hand and offhand viewmodels", "NEW", "visuals", mx, my);

        if (!HandPositionModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningHandPosition ? "..." : screen.formatKey(HandPositionModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        String[] swingStyles = {"Slash", "Thrust", "Swipe", "Punch"};
        int sBtnW = (w - 24 - 9) / 4;
        for (int i = 0; i < 4; i++) {
            screen.modeButton(c, swingStyles[i], x + 12 + i * (sBtnW + 3), curY, sBtnW, 16, mx, my, HandPositionModule.swingStyle == i);
        }
        curY += 22;

        String[] pNames = {"Default", "Lowered", "Doom", "Small"};
        int pBtnW = (w - 24 - 9) / 4;
        for (int i = 0; i < 4; i++) {
            screen.button(c, pNames[i], x + 12 + i * (pBtnW + 3), curY, pBtnW, 16, mx, my);
        }
        curY += 24;

        int colGap = 10;
        int colW = (w - 24 - colGap) / 2;
        int c1X = x + 12;
        int c2X = c1X + colW + colGap;

        screen.text(c, "POSITION", c1X + 1, curY + 2, 0xFF8E95A4);
        screen.text(c, "ROTATION", c2X + 1, curY + 2, 0xFF8E95A4);
        curY += 14;

        // X Offset & Pitch
        String xStr = String.format(Locale.US, "%.2f", HandPositionModule.posX);
        screen.text(c, "X Offset", c1X, curY, 0xFFD4D8E0);
        int xValW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(CustomGuiUtils.getFontText(xStr));
        screen.text(c, xStr, c1X + colW - xValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, colW, 4, 0xFF303442, 2);
        float xNorm = (HandPositionModule.posX - (-0.80f)) / 1.60f;
        int xFill = Math.round(colW * Math.clamp(xNorm, 0f, 1f));
        if (xFill > 0) CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, xFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c1X + xFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);

        String pitchStr = Math.round(HandPositionModule.pitch) + "°";
        screen.text(c, "Pitch (Tilt)", c2X, curY, 0xFFD4D8E0);
        int pitchValW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(CustomGuiUtils.getFontText(pitchStr));
        screen.text(c, pitchStr, c2X + colW - pitchValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, colW, 4, 0xFF303442, 2);
        float pitchNorm = (HandPositionModule.pitch - (-180f)) / 360f;
        int pitchFill = Math.round(colW * Math.clamp(pitchNorm, 0f, 1f));
        if (pitchFill > 0) CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, pitchFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c2X + pitchFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);
        curY += 23;

        // Y Offset & Yaw
        String yStr = String.format(Locale.US, "%.2f", HandPositionModule.posY);
        screen.text(c, "Y Offset", c1X, curY, 0xFFD4D8E0);
        int yValW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(CustomGuiUtils.getFontText(yStr));
        screen.text(c, yStr, c1X + colW - yValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, colW, 4, 0xFF303442, 2);
        float yNorm = (HandPositionModule.posY - (-0.80f)) / 1.60f;
        int yFill = Math.round(colW * Math.clamp(yNorm, 0f, 1f));
        if (yFill > 0) CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, yFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c1X + yFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);

        String yawStr = Math.round(HandPositionModule.yaw) + "°";
        screen.text(c, "Yaw (Turn)", c2X, curY, 0xFFD4D8E0);
        int yawValW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(CustomGuiUtils.getFontText(yawStr));
        screen.text(c, yawStr, c2X + colW - yawValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, colW, 4, 0xFF303442, 2);
        float yawNorm = (HandPositionModule.yaw - (-180f)) / 360f;
        int yawFill = Math.round(colW * Math.clamp(yawNorm, 0f, 1f));
        if (yawFill > 0) CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, yawFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c2X + yawFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);
        curY += 23;

        // Z Offset & Roll
        String zStr = String.format(Locale.US, "%.2f", HandPositionModule.posZ);
        screen.text(c, "Z Offset", c1X, curY, 0xFFD4D8E0);
        int zValW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(CustomGuiUtils.getFontText(zStr));
        screen.text(c, zStr, c1X + colW - zValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, colW, 4, 0xFF303442, 2);
        float zNorm = (HandPositionModule.posZ - (-0.80f)) / 1.60f;
        int zFill = Math.round(colW * Math.clamp(zNorm, 0f, 1f));
        if (zFill > 0) CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, zFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c1X + zFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);

        String rollStr = Math.round(HandPositionModule.roll) + "°";
        screen.text(c, "Roll (Angle)", c2X, curY, 0xFFD4D8E0);
        int rollValW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(CustomGuiUtils.getFontText(rollStr));
        screen.text(c, rollStr, c2X + colW - rollValW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, colW, 4, 0xFF303442, 2);
        float rollNorm = (HandPositionModule.roll - (-180f)) / 360f;
        int rollFill = Math.round(colW * Math.clamp(rollNorm, 0f, 1f));
        if (rollFill > 0) CustomGuiUtils.fillUltraRounded(c, c2X, curY + 11, rollFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c2X + rollFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);
        curY += 23;

        // Scale & Offhand
        int scalePct = Math.round(HandPositionModule.scale * 100);
        screen.text(c, "Item Scale", c1X, curY, 0xFFD4D8E0);
        String sPctStr = scalePct + "%";
        int sPctW = net.minecraft.client.MinecraftClient.getInstance().textRenderer.getWidth(CustomGuiUtils.getFontText(sPctStr));
        screen.text(c, sPctStr, c1X + colW - sPctW, curY, 0xFF8E95A4);
        CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, colW, 4, 0xFF303442, 2);
        float sNorm = (HandPositionModule.scale - 0.30f) / 1.20f;
        int sFill = Math.round(colW * Math.clamp(sNorm, 0f, 1f));
        if (sFill > 0) CustomGuiUtils.fillUltraRounded(c, c1X, curY + 11, sFill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, c1X + sFill - 3, curY + 9, 7, 8, 0xFFFFFFFF, 4);

        screen.text(c, "Offhand", c2X, curY + 2, 0xFFD4D8E0);
        screen.toggle(c, c2X + colW - 28, curY, HandPositionModule.applyToOffhand, mx, my, delta);
        curY += 23;

        // Weapons only & Mining anim
        screen.text(c, "Weapons Only", c1X, curY + 2, 0xFFD4D8E0);
        screen.toggle(c, c1X + colW - 28, curY, HandPositionModule.weaponsOnly, mx, my, delta);

        screen.text(c, "Mining Anim", c2X, curY + 2, 0xFFD4D8E0);
        screen.toggle(c, c2X + colW - 28, curY, HandPositionModule.miningAnimation, mx, my, delta);
        curY += 21;

        screen.button(c, "Reset", c2X + colW - 46, curY, 46, 16, mx, my);
    }

    private void renderItemSize(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getItemSizeHeight();
        screen.moduleCard(c, x, y, w, h, ItemSizeModule.enabled, ItemSizeModule.expanded,
                "Item Size", "Scale and offset in-hand item models", "NEW", "visuals", mx, my);

        if (!ItemSizeModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningItemSize ? "..." : screen.formatKey(ItemSizeModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        int scalePct = Math.round(ItemSizeModule.scale * 100);
        screen.text(c, "Scale: " + scalePct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int sx = x + 88;
        int sw = w - 102;
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float scaleNorm = (ItemSizeModule.scale - 0.25f) / 3.75f;
        int sfill = Math.round(sw * Math.clamp(scaleNorm, 0f, 1f));
        if (sfill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + sfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        String yStr = String.format(Locale.US, "%.2f", ItemSizeModule.yOffset);
        screen.text(c, "Y-Offset: " + yStr, x + 14, curY + 4, 0xFFD4D8E0);
        int yx = x + 96;
        int yw = w - 110;
        CustomGuiUtils.fillUltraRounded(c, yx, curY + 6, yw, 4, 0xFF303442, 2);
        float yNorm = (ItemSizeModule.yOffset - (-0.2f)) / 1.2f;
        int yfill = Math.round(yw * Math.clamp(yNorm, 0f, 1f));
        if (yfill > 0) CustomGuiUtils.fillUltraRounded(c, yx, curY + 6, yfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, yx + yfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        screen.text(c, "Blocks", x + 14, curY, 0xFFABB1BE);
        int boxX = x + 14;
        int boxY = curY + 12;
        int boxW = w - 28;
        int boxH = 22;
        boolean boxHovered = screen.inside(mx, my, boxX, boxY, boxW, boxH);
        CustomGuiUtils.fillUltraRounded(c, boxX, boxY, boxW, boxH, boxHovered ? 0xFF181C26 : 0xFF12151D, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, boxX, boxY, boxW, boxH, boxHovered ? GuiTheme.accent() : 0xFF2A2E3D, 4);

        int count = ItemSizeModule.selectedItems.size();
        String countText = count > 0 ? count + " item(s) selected" : "All items (Click to filter)";
        screen.text(c, countText, boxX + 8, boxY + 7, 0xFFE2E5ED);

        curY += 12 + 22 + 8;

        List<String> selList = new ArrayList<>(ItemSizeModule.selectedItems);
        int colW = (w - 28 - 6) / 2;
        for (int i = 0; i < selList.size(); i += 2) {
            for (int col = 0; col < 2; col++) {
                int idx = i + col;
                if (idx >= selList.size()) break;
                String itemId = selList.get(idx);
                int colX = x + 14 + (col * (colW + 6));

                Item item = null;
                try {
                    item = Registries.ITEM.get(Identifier.of(itemId));
                } catch (Exception ignored) {}

                ItemStack stack = (item != null && item != Items.AIR) ? new ItemStack(item) : ItemStack.EMPTY;
                if (!stack.isEmpty()) {
                    c.drawItem(stack, colX, curY + 2);
                }

                String itemName = (!stack.isEmpty()) ? stack.getName().getString() : itemId;
                int nameX = colX + 18;
                int nameMaxW = colW - 36;
                c.enableScissor(nameX, curY, nameX + nameMaxW, curY + 22);
                screen.text(c, itemName, nameX, curY + 6, 0xFFE2E5ED);
                c.disableScissor();

                int trashX = colX + colW - 16;
                int trashY = curY + 2;
                boolean trashHover = screen.inside(mx, my, trashX, trashY, 16, 16);
                CustomGuiUtils.fillUltraRounded(c, trashX, trashY, 16, 16, trashHover ? 0xFF3A1C20 : 0xFF181C24, 3);
                CustomGuiUtils.drawUltraRoundedOutline(c, trashX, trashY, 16, 16, trashHover ? 0xFFE55757 : 0xFF292D36, 3);
                CustomGuiUtils.drawTrashIcon(c, trashX, trashY, trashHover ? 0xFFE55757 : 0xFF8E95A4);
            }
            curY += 24;
        }

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void clearKeyListeners() {
        listeningFullbright = false;
        listeningShowHud = false;
        listeningZoom = false;
        listeningSpotify = false;
        listeningScoreboard = false;
        listeningCrosshair = false;
        listeningBlockOutline = false;
        listeningHandPosition = false;
        listeningItemSize = false;
    }

    @Override
    public boolean mouseClicked(BameClientScreen screen, Click click, boolean twice,
                                int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        double mx = click.x();
        double my = click.y();

        // 1. Show HUD
        if (showHudVisible(search)) {
            int y = tracker.leftY;
            int h = getShowHudHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        ShowHudModule.enabled = !ShowHudModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("HUD", ShowHudModule.enabled);
                    },
                    () -> ShowHudModule.expanded = !ShowHudModule.expanded)) {
                return true;
            }
            if (ShowHudModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningShowHud = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                    ClockModule.enabled = !ClockModule.enabled;
                    com.bame.client.notification.NotificationManager.showModuleToggle("Clock HUD", ClockModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + 8, curY, columnWidth - 50, 22)) {
                    CoordinatesModule.expanded = !CoordinatesModule.expanded;
                    BameClientConfig.save();
                    return true;
                }
                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                    CoordinatesModule.enabled = !CoordinatesModule.enabled;
                    com.bame.client.notification.NotificationManager.showModuleToggle("Coordinates", CoordinatesModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (CoordinatesModule.expanded) {
                    if (screen.inside(mx, my, leftX + columnWidth - 76, curY, 64, 16)) {
                        CoordinatesModule.cycleLayout();
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (screen.inside(mx, my, leftX + columnWidth - 76, curY, 64, 16)) {
                        CoordinatesModule.cycleStyle();
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                        CoordinatesModule.showX = !CoordinatesModule.showX;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                        CoordinatesModule.showY = !CoordinatesModule.showY;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                        CoordinatesModule.showZ = !CoordinatesModule.showZ;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                        CoordinatesModule.decimals = !CoordinatesModule.decimals;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                        CoordinatesModule.showNether = !CoordinatesModule.showNether;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                        CoordinatesModule.showDirection = !CoordinatesModule.showDirection;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;
                }

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                    PotionsModule.enabled = !PotionsModule.enabled;
                    com.bame.client.notification.NotificationManager.showModuleToggle("Potions HUD", PotionsModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + 8, curY, columnWidth - 16, 22)) {
                    TargetHudModule.expanded = !TargetHudModule.expanded;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (TargetHudModule.expanded) {
                    if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                        TargetHudModule.showHearts = !TargetHudModule.showHearts;
                        TargetHudModule.enabled = TargetHudModule.showHearts || TargetHudModule.showArmor;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Target HUD", TargetHudModule.enabled);
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                        TargetHudModule.showArmor = !TargetHudModule.showArmor;
                        TargetHudModule.enabled = TargetHudModule.showHearts || TargetHudModule.showArmor;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Target HUD", TargetHudModule.enabled);
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;

                    if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                        TargetHudModule.playersOnly = !TargetHudModule.playersOnly;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 26;
                }

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                    ArmorHudModule.enabled = !ArmorHudModule.enabled;
                    com.bame.client.notification.NotificationManager.showModuleToggle("Armor HUD", ArmorHudModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                    KeyStrokesModule.enabled = !KeyStrokesModule.enabled;
                    com.bame.client.notification.NotificationManager.showModuleToggle("KeyStrokes", KeyStrokesModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                    CpsModule.enabled = !CpsModule.enabled;
                    com.bame.client.notification.NotificationManager.showModuleToggle("CPS", CpsModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                    FpsModule.enabled = !FpsModule.enabled;
                    com.bame.client.notification.NotificationManager.showModuleToggle("FPS", FpsModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                    PingModule.enabled = !PingModule.enabled;
                    com.bame.client.notification.NotificationManager.showModuleToggle("Ping", PingModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 3, 26, 14)) {
                    ServerInfoModule.enabled = !ServerInfoModule.enabled;
                    com.bame.client.notification.NotificationManager.showModuleToggle("Server Info", ServerInfoModule.enabled);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (ServerInfoModule.enabled) {
                    if (screen.inside(mx, my, leftX + 14, curY + 2, 44, 18)) {
                        ServerInfoModule.showName = !ServerInfoModule.showName;
                        BameClientConfig.save();
                        return true;
                    }
                    if (screen.inside(mx, my, leftX + 62, curY + 2, 48, 18)) {
                        ServerInfoModule.showServer = !ServerInfoModule.showServer;
                        BameClientConfig.save();
                        return true;
                    }
                    if (screen.inside(mx, my, leftX + 114, curY + 2, 42, 18)) {
                        ServerInfoModule.showTime = !ServerInfoModule.showTime;
                        BameClientConfig.save();
                        return true;
                    }
                    curY += 24;
                }

                curY += 4;
                if (screen.inside(mx, my, leftX + 14, curY, 50, 16)) {
                    screen.openHudEditor();
                    return true;
                }
                if (screen.inside(mx, my, leftX + columnWidth - 58, curY, 46, 16)) {
                    resetShowHud();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        // 2. Scoreboard
        if (scoreboardVisible(search)) {
            int y = tracker.leftY;
            int h = getScoreboardHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        ScoreboardModule.enabled = !ScoreboardModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Scoreboard", ScoreboardModule.enabled);
                    },
                    () -> ScoreboardModule.expanded = !ScoreboardModule.expanded)) {
                return true;
            }
            if (ScoreboardModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningScoreboard = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + 14, curY, 50, 16)) {
                    screen.openHudEditor();
                    return true;
                }
                if (screen.inside(mx, my, leftX + columnWidth - 58, curY, 46, 16)) {
                    resetScoreboard();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        // 3. Block Outline
        if (blockOutlineVisible(search)) {
            int y = tracker.leftY;
            int h = getBlockOutlineHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        BlockOutlineModule.enabled = !BlockOutlineModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Block Outline", BlockOutlineModule.enabled);
                    },
                    () -> BlockOutlineModule.expanded = !BlockOutlineModule.expanded)) {
                return true;
            }
            if (BlockOutlineModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningBlockOutline = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + 56, curY + 2, 16, 16)) {
                    screen.openModalColorPicker(3, BlockOutlineModule.color);
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) {
                    BlockOutlineModule.chroma = !BlockOutlineModule.chroma;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                int wx = leftX + 82;
                int ww = columnWidth - 96;
                if (screen.inside(mx, my, wx - 4, curY + 2, ww + 8, 14)) {
                    draggingBlockOutlineWidth = true;
                    float fval = (float) Math.clamp((mx - wx) / (double) ww, 0.0, 1.0);
                    BlockOutlineModule.lineWidth = Math.max(1.0f, Math.round((1.0f + fval * 5.0f) * 10f) / 10f);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                int ox = leftX + 90;
                int ow = columnWidth - 104;
                if (screen.inside(mx, my, ox - 4, curY + 2, ow + 8, 14)) {
                    draggingBlockOutlineOpacity = true;
                    float fval = (float) Math.clamp((mx - ox) / (double) ow, 0.0, 1.0);
                    BlockOutlineModule.opacity = Math.max(0.2f, Math.round((0.2f + fval * 0.8f) * 100f) / 100f);
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 58, curY, 46, 16)) {
                    resetBlockOutline();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        // 4. Hand Position
        if (handPositionVisible(search)) {
            int y = tracker.leftY;
            int h = getHandPositionHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        HandPositionModule.enabled = !HandPositionModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Hand Position", HandPositionModule.enabled);
                        com.bame.client.sound.ClientSoundManager.playClick();
                    },
                    () -> {
                        HandPositionModule.expanded = !HandPositionModule.expanded;
                        com.bame.client.sound.ClientSoundManager.playClick();
                    })) {
                return true;
            }
            if (HandPositionModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningHandPosition = true;
                    return true;
                }
                curY += 26;

                int sBtnW = (columnWidth - 24 - 9) / 4;
                for (int i = 0; i < 4; i++) {
                    if (screen.inside(mx, my, leftX + 12 + i * (sBtnW + 3), curY, sBtnW, 16)) {
                        HandPositionModule.swingStyle = i;
                        com.bame.client.sound.ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }
                }
                curY += 22;

                int pBtnW = (columnWidth - 24 - 9) / 4;
                for (int i = 0; i < 4; i++) {
                    if (screen.inside(mx, my, leftX + 12 + i * (pBtnW + 3), curY, pBtnW, 16)) {
                        HandPositionModule.applyPreset(i);
                        com.bame.client.sound.ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }
                }
                curY += 24;

                int colGap = 10;
                int colW = (columnWidth - 24 - colGap) / 2;
                int c1X = leftX + 12;
                int c2X = c1X + colW + colGap;

                curY += 14;

                if (screen.inside(mx, my, c1X - 2, curY + 6, colW + 4, 14)) {
                    draggingHandX = true;
                    float fval = (float) Math.clamp((mx - c1X) / (double) colW, 0.0, 1.0);
                    HandPositionModule.posX = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
                    BameClientConfig.save();
                    return true;
                }
                if (screen.inside(mx, my, c2X - 2, curY + 6, colW + 4, 14)) {
                    draggingHandPitch = true;
                    float fval = (float) Math.clamp((mx - c2X) / (double) colW, 0.0, 1.0);
                    HandPositionModule.pitch = Math.round(-180f + fval * 360f);
                    BameClientConfig.save();
                    return true;
                }
                curY += 23;

                if (screen.inside(mx, my, c1X - 2, curY + 6, colW + 4, 14)) {
                    draggingHandY = true;
                    float fval = (float) Math.clamp((mx - c1X) / (double) colW, 0.0, 1.0);
                    HandPositionModule.posY = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
                    BameClientConfig.save();
                    return true;
                }
                if (screen.inside(mx, my, c2X - 2, curY + 6, colW + 4, 14)) {
                    draggingHandYaw = true;
                    float fval = (float) Math.clamp((mx - c2X) / (double) colW, 0.0, 1.0);
                    HandPositionModule.yaw = Math.round(-180f + fval * 360f);
                    BameClientConfig.save();
                    return true;
                }
                curY += 23;

                if (screen.inside(mx, my, c1X - 2, curY + 6, colW + 4, 14)) {
                    draggingHandZ = true;
                    float fval = (float) Math.clamp((mx - c1X) / (double) colW, 0.0, 1.0);
                    HandPositionModule.posZ = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
                    BameClientConfig.save();
                    return true;
                }
                if (screen.inside(mx, my, c2X - 2, curY + 6, colW + 4, 14)) {
                    draggingHandRoll = true;
                    float fval = (float) Math.clamp((mx - c2X) / (double) colW, 0.0, 1.0);
                    HandPositionModule.roll = Math.round(-180f + fval * 360f);
                    BameClientConfig.save();
                    return true;
                }
                curY += 23;

                if (screen.inside(mx, my, c1X - 2, curY + 6, colW + 4, 14)) {
                    draggingHandScale = true;
                    float fval = (float) Math.clamp((mx - c1X) / (double) colW, 0.0, 1.0);
                    HandPositionModule.scale = Math.round((0.30f + fval * 1.20f) * 100f) / 100f;
                    BameClientConfig.save();
                    return true;
                }
                if (screen.inside(mx, my, c2X + colW - 28, curY, 26, 14)) {
                    HandPositionModule.applyToOffhand = !HandPositionModule.applyToOffhand;
                    com.bame.client.sound.ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                curY += 23;

                if (screen.inside(mx, my, c1X + colW - 28, curY, 26, 14)) {
                    HandPositionModule.weaponsOnly = !HandPositionModule.weaponsOnly;
                    com.bame.client.sound.ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                if (screen.inside(mx, my, c2X + colW - 28, curY, 26, 14)) {
                    HandPositionModule.miningAnimation = !HandPositionModule.miningAnimation;
                    com.bame.client.sound.ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                curY += 21;

                if (screen.inside(mx, my, c2X + colW - 46, curY, 46, 16)) {
                    resetHandPosition();
                    com.bame.client.sound.ClientSoundManager.playClick();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        // Right Column
        // 5. Fullbright
        if (fullbrightVisible(search)) {
            int y = tracker.rightY;
            int h = getFullbrightHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        FullbrightModule.enabled = !FullbrightModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Fullbright", FullbrightModule.enabled);
                    },
                    () -> fullbrightExpanded = !fullbrightExpanded)) {
                return true;
            }
            if (fullbrightExpanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningFullbright = true;
                    return true;
                }
                curY += 26;

                int sx = rightX + 14;
                int sw = columnWidth - 64;
                if (screen.inside(mx, my, sx, curY + 12, sw, 14)) {
                    draggingFullbright = true;
                    float fval = (float) Math.clamp(((mx - sx) / (double) sw), 0.0, 1.0);
                    FullbrightModule.intensity = fval;
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        // 6. Zoom
        if (zoomVisible(search)) {
            int y = tracker.rightY;
            int h = getZoomHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        ZoomModule.enabled = !ZoomModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Zoom", ZoomModule.enabled);
                    },
                    () -> ZoomModule.expanded = !ZoomModule.expanded)) {
                return true;
            }
            if (ZoomModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningZoom = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    ZoomModule.type = (ZoomModule.type == 0) ? 1 : 0;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 68, curY, 56, 16)) {
                    ZoomModule.mode = (ZoomModule.mode == 0) ? 1 : 0;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 38, curY + 2, 26, 14)) {
                    ZoomModule.scrollZoom = !ZoomModule.scrollZoom;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 48, curY, 36, 16)) {
                    ZoomModule.cycleDefaultZoom();
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    ZoomModule.type = 0;
                    ZoomModule.mode = 0;
                    ZoomModule.scrollZoom = true;
                    ZoomModule.defaultLevel = 1;
                    ZoomModule.keyBind = GLFW.GLFW_KEY_C;
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        // 7. Spotify HUD
        if (spotifyHudVisible(search)) {
            int y = tracker.rightY;
            int h = getSpotifyHudHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        SpotifyHudModule.enabled = !SpotifyHudModule.enabled;
                        if (SpotifyHudModule.enabled) {
                            com.bame.client.spotify.SpotifyService.start();
                        }
                        com.bame.client.notification.NotificationManager.showModuleToggle("Spotify HUD", SpotifyHudModule.enabled);
                    },
                    () -> SpotifyHudModule.expanded = !SpotifyHudModule.expanded)) {
                return true;
            }
            if (SpotifyHudModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningSpotify = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 38, curY + 2, 26, 14)) {
                    SpotifyHudModule.autoHide = !SpotifyHudModule.autoHide;
                    BameClientConfig.save();
                    return true;
                }
                curY += 24;

                if (screen.inside(mx, my, rightX + 14, curY, 50, 16)) {
                    screen.openHudEditor();
                    return true;
                }
                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    resetSpotifyHud();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        // 8. Custom Crosshair
        if (customCrosshairVisible(search)) {
            int y = tracker.rightY;
            int h = getCustomCrosshairHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        CustomCrosshairModule.enabled = !CustomCrosshairModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Crosshair", CustomCrosshairModule.enabled);
                    },
                    () -> CustomCrosshairModule.expanded = !CustomCrosshairModule.expanded)) {
                return true;
            }
            if (CustomCrosshairModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningCrosshair = true;
                    return true;
                }

                int gridY = y + 78;
                int gridX = rightX + 12;
                if (screen.inside(mx, my, gridX, gridY, 105, 105)) {
                    int col = (int) ((mx - gridX) / 7);
                    int row = (int) ((my - gridY) / 7);
                    if (col >= 0 && col < 15 && row >= 0 && row < 15) {
                        boolean newState = !CustomCrosshairModule.grid[row][col];
                        CustomCrosshairModule.grid[row][col] = newState;
                        gridDragMode = newState ? 1 : 0;
                        lastEditedCol = col;
                        lastEditedRow = row;
                        BameClientConfig.save();
                        return true;
                    }
                }

                int pY = gridY + 105 + 8;
                int pStartX = rightX + 58;
                for (int i = 0; i < 9; i++) {
                    int bx = pStartX + i * 19;
                    if (screen.inside(mx, my, bx, pY, 17, 17)) {
                        CustomCrosshairModule.applyPreset(i);
                        BameClientConfig.save();
                        return true;
                    }
                }

                int actY = pY + 23;
                if (screen.inside(mx, my, rightX + 44, actY, 16, 16)) {
                    screen.openModalColorPicker(0, CustomCrosshairModule.color);
                    return true;
                }

                if (screen.inside(mx, my, rightX + 68, actY, 38, 16)) {
                    CustomCrosshairModule.clearGrid();
                    BameClientConfig.save();
                    return true;
                }

                if (screen.inside(mx, my, rightX + columnWidth - 58, actY, 46, 16)) {
                    resetCustomCrosshair();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        // 9. Item Size
        if (itemSizeVisible(search)) {
            int y = tracker.rightY;
            int h = getItemSizeHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        ItemSizeModule.enabled = !ItemSizeModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Item Size", ItemSizeModule.enabled);
                    },
                    () -> ItemSizeModule.expanded = !ItemSizeModule.expanded)) {
                return true;
            }
            if (ItemSizeModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningItemSize = true;
                    return true;
                }
                curY += 26;

                int sx = rightX + 88;
                int sw = columnWidth - 102;
                if (screen.inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                    draggingItemScale = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    ItemSizeModule.scale = Math.round((0.25f + fval * 3.75f) * 100f) / 100f;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                int yx = rightX + 96;
                int yw = columnWidth - 110;
                if (screen.inside(mx, my, yx - 4, curY + 2, yw + 8, 14)) {
                    draggingItemYOffset = true;
                    float fval = (float) Math.clamp((mx - yx) / (double) yw, 0.0, 1.0);
                    ItemSizeModule.yOffset = Math.round((-0.2f + fval * 1.2f) * 100f) / 100f;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                int boxX = rightX + 14;
                int boxY = curY + 12;
                int boxW = columnWidth - 28;
                int boxH = 22;
                if (screen.inside(mx, my, boxX, boxY, boxW, boxH)) {
                    screen.openItemModal();
                    return true;
                }
                curY += 12 + 22 + 8;

                String itemToRemove = null;
                List<String> selList = new ArrayList<>(ItemSizeModule.selectedItems);
                int colW = (columnWidth - 28 - 6) / 2;
                for (int i = 0; i < selList.size(); i += 2) {
                    for (int col = 0; col < 2; col++) {
                        int idx = i + col;
                        if (idx >= selList.size()) break;
                        int colX = rightX + 14 + (col * (colW + 6));
                        int trashX = colX + colW - 16;
                        int trashY = curY + 2;
                        if (screen.inside(mx, my, trashX, trashY, 16, 16)) {
                            itemToRemove = selList.get(idx);
                            break;
                        }
                    }
                    if (itemToRemove != null) break;
                    curY += 24;
                }

                if (itemToRemove != null) {
                    ItemSizeModule.selectedItems.remove(itemToRemove);
                    BameClientConfig.save();
                    return true;
                }

                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    resetItemSize();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(BameClientScreen screen, Click click, double deltaX, double deltaY) {
        int columnWidth = screen.getColumnWidth();
        int leftX = screen.getContentLeftX();
        int rightX = screen.getContentRightX();

        if (gridDragMode >= 0) {
            ColumnTracker tracker = new ColumnTracker(screen.baseY());
            if (fullbrightVisible("")) tracker.rightY += getFullbrightHeight() + 12;
            if (zoomVisible("")) tracker.rightY += getZoomHeight() + 12;
            if (spotifyHudVisible("")) tracker.rightY += getSpotifyHudHeight() + 12;

            int gridX = rightX + 12;
            int gridY = tracker.rightY + 78;
            int col = (int) ((click.x() - gridX) / 7.0);
            int row = (int) ((click.y() - gridY) / 7.0);
            if (col >= 0 && col < 15 && row >= 0 && row < 15) {
                if (col != lastEditedCol || row != lastEditedRow) {
                    CustomCrosshairModule.grid[row][col] = (gridDragMode == 1);
                    lastEditedCol = col;
                    lastEditedRow = row;
                }
            }
            return true;
        }

        if (draggingFullbright) {
            int sx = rightX + 14;
            int sw = columnWidth - 64;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            FullbrightModule.intensity = fval;
            return true;
        }

        if (draggingBlockOutlineWidth) {
            int wx = leftX + 82;
            int ww = columnWidth - 96;
            float fval = (float) Math.clamp(((click.x() - wx) / (double) ww), 0.0, 1.0);
            BlockOutlineModule.lineWidth = Math.max(1.0f, Math.round((1.0f + fval * 5.0f) * 10f) / 10f);
            return true;
        }

        if (draggingBlockOutlineOpacity) {
            int ox = leftX + 90;
            int ow = columnWidth - 104;
            float fval = (float) Math.clamp(((click.x() - ox) / (double) ow), 0.0, 1.0);
            BlockOutlineModule.opacity = Math.max(0.2f, Math.round((0.2f + fval * 0.8f) * 100f) / 100f);
            return true;
        }

        if (draggingHandX) {
            int colGap = 10;
            int colW = (columnWidth - 24 - colGap) / 2;
            int c1X = leftX + 12;
            float fval = (float) Math.clamp(((click.x() - c1X) / (double) colW), 0.0, 1.0);
            HandPositionModule.posX = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
            return true;
        }

        if (draggingHandY) {
            int colGap = 10;
            int colW = (columnWidth - 24 - colGap) / 2;
            int c1X = leftX + 12;
            float fval = (float) Math.clamp(((click.x() - c1X) / (double) colW), 0.0, 1.0);
            HandPositionModule.posY = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
            return true;
        }

        if (draggingHandZ) {
            int colGap = 10;
            int colW = (columnWidth - 24 - colGap) / 2;
            int c1X = leftX + 12;
            float fval = (float) Math.clamp(((click.x() - c1X) / (double) colW), 0.0, 1.0);
            HandPositionModule.posZ = Math.round((-0.80f + fval * 1.60f) * 100f) / 100f;
            return true;
        }

        if (draggingHandScale) {
            int colGap = 10;
            int colW = (columnWidth - 24 - colGap) / 2;
            int c1X = leftX + 12;
            float fval = (float) Math.clamp(((click.x() - c1X) / (double) colW), 0.0, 1.0);
            HandPositionModule.scale = Math.round((0.30f + fval * 1.20f) * 100f) / 100f;
            return true;
        }

        if (draggingHandPitch) {
            int colGap = 10;
            int colW = (columnWidth - 24 - colGap) / 2;
            int c2X = leftX + 12 + colW + colGap;
            float fval = (float) Math.clamp(((click.x() - c2X) / (double) colW), 0.0, 1.0);
            HandPositionModule.pitch = Math.round(-180f + fval * 360f);
            return true;
        }

        if (draggingHandYaw) {
            int colGap = 10;
            int colW = (columnWidth - 24 - colGap) / 2;
            int c2X = leftX + 12 + colW + colGap;
            float fval = (float) Math.clamp(((click.x() - c2X) / (double) colW), 0.0, 1.0);
            HandPositionModule.yaw = Math.round(-180f + fval * 360f);
            return true;
        }

        if (draggingHandRoll) {
            int colGap = 10;
            int colW = (columnWidth - 24 - colGap) / 2;
            int c2X = leftX + 12 + colW + colGap;
            float fval = (float) Math.clamp(((click.x() - c2X) / (double) colW), 0.0, 1.0);
            HandPositionModule.roll = Math.round(-180f + fval * 360f);
            return true;
        }

        if (draggingItemScale) {
            int sx = rightX + 88;
            int sw = columnWidth - 102;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            ItemSizeModule.scale = Math.round((0.25f + fval * 3.75f) * 100f) / 100f;
            return true;
        }

        if (draggingItemYOffset) {
            int yx = rightX + 96;
            int yw = columnWidth - 110;
            float fval = (float) Math.clamp(((click.x() - yx) / (double) yw), 0.0, 1.0);
            ItemSizeModule.yOffset = Math.round((-0.2f + fval * 1.2f) * 100f) / 100f;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(BameClientScreen screen, Click click) {
        if (gridDragMode >= 0) {
            gridDragMode = -1;
            lastEditedCol = -1;
            lastEditedRow = -1;
            BameClientConfig.save();
        }

        boolean handled = draggingFullbright || draggingBlockOutlineWidth || draggingBlockOutlineOpacity
                || draggingHandX || draggingHandY || draggingHandZ || draggingHandScale
                || draggingHandPitch || draggingHandYaw || draggingHandRoll
                || draggingItemScale || draggingItemYOffset;

        draggingFullbright = false;
        draggingBlockOutlineWidth = false;
        draggingBlockOutlineOpacity = false;
        draggingHandX = false;
        draggingHandY = false;
        draggingHandZ = false;
        draggingHandScale = false;
        draggingHandPitch = false;
        draggingHandYaw = false;
        draggingHandRoll = false;
        draggingItemScale = false;
        draggingItemYOffset = false;

        if (handled) {
            BameClientConfig.save();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(BameClientScreen screen, KeyInput input) {
        if (listeningFullbright) {
            FullbrightModule.keyBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningFullbright = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningShowHud) {
            ShowHudModule.keyBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningShowHud = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningZoom) {
            ZoomModule.keyBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningZoom = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningSpotify) {
            SpotifyHudModule.keyBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningSpotify = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningScoreboard) {
            ScoreboardModule.keyBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningScoreboard = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningCrosshair) {
            CustomCrosshairModule.keyBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningCrosshair = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningBlockOutline) {
            BlockOutlineModule.keyBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningBlockOutline = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningHandPosition) {
            HandPositionModule.keyBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningHandPosition = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningItemSize) {
            ItemSizeModule.keyBind = input.key() == GLFW.GLFW_KEY_ESCAPE ? -1 : input.key();
            listeningItemSize = false;
            BameClientConfig.save();
            return true;
        }
        return false;
    }

    private void resetShowHud() {
        ClockModule.enabled = false;
        CoordinatesModule.enabled = false;
        CoordinatesModule.expanded = false;
        CoordinatesModule.layoutMode = 0;
        CoordinatesModule.style = 0;
        CoordinatesModule.showX = true;
        CoordinatesModule.showY = true;
        CoordinatesModule.showZ = true;
        CoordinatesModule.decimals = false;
        CoordinatesModule.showNether = false;
        CoordinatesModule.showDirection = false;
        PotionsModule.enabled = false;
        TargetHudModule.enabled = false;
        TargetHudModule.expanded = false;
        TargetHudModule.showHearts = true;
        TargetHudModule.showArmor = false;
        TargetHudModule.playersOnly = false;
        KeyStrokesModule.enabled = false;
        CpsModule.enabled = false;
        FpsModule.enabled = false;
        PingModule.enabled = false;
        ServerInfoModule.enabled = false;
        ServerInfoModule.showName = true;
        ServerInfoModule.showServer = true;
        ServerInfoModule.showTime = true;
        ServerInfoModule.dockedElements = new ArrayList<>(List.of("name", "server", "time"));
        ServerInfoModule.nameX = 10;
        ServerInfoModule.nameY = 10;
        ServerInfoModule.serverX = 10;
        ServerInfoModule.serverY = 32;
        ServerInfoModule.timeX = 10;
        ServerInfoModule.timeY = 54;
        BameClientConfig.save();
    }

    private void resetScoreboard() {
        ScoreboardModule.hudX = -1;
        ScoreboardModule.hudY = -1;
        ScoreboardModule.scale = 1.0f;
        ScoreboardModule.customWidth = -1;
        ScoreboardModule.customHeight = -1;
        ScoreboardModule.bgMode = 0;
        ScoreboardModule.outlineColor = 0xFFFFFFFF;
        BameClientConfig.save();
    }

    private void resetSpotifyHud() {
        SpotifyHudModule.hudX = 20;
        SpotifyHudModule.hudY = 20;
        SpotifyHudModule.scale = 1.0f;
        SpotifyHudModule.customWidth = -1;
        SpotifyHudModule.customHeight = -1;
        SpotifyHudModule.autoHide = false;
        SpotifyHudModule.bgMode = 0;
        SpotifyHudModule.outlineColor = 0xFFFFFFFF;
        BameClientConfig.save();
    }

    private void resetCustomCrosshair() {
        CustomCrosshairModule.resetToDefault();
        BameClientConfig.save();
    }

    private void resetBlockOutline() {
        BlockOutlineModule.resetToDefault();
        BameClientConfig.save();
    }

    private void resetHandPosition() {
        HandPositionModule.resetToDefault();
        BameClientConfig.save();
    }

    private void resetItemSize() {
        ItemSizeModule.resetToDefault();
        BameClientConfig.save();
    }
}
