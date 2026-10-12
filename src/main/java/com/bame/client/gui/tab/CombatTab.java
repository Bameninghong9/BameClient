package com.bame.client.gui.tab;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.BameClientScreen;
import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import com.bame.client.module.*;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class CombatTab implements ClientTab {
    private boolean listeningAutoClicker = false;
    private boolean listeningReachDisplay = false;
    private boolean listeningHitColor = false;
    private boolean listeningLowShield = false;
    private boolean listeningHitboxes = false;

    private boolean draggingCps = false;
    private boolean draggingAutoClickerDelay = false;
    private boolean draggingHitColorAlpha = false;
    private boolean draggingLowShieldHeight = false;
    private boolean draggingLowShieldTotem = false;
    private boolean draggingHitboxAlpha = false;
    private boolean draggingHitboxWidth = false;

    @Override
    public String getCategory() {
        return "Combat";
    }

    public boolean autoClickerVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "autoclicker".contains(q) || "auto clicker".contains(q) || "cps".contains(q);
        return true;
    }

    public boolean reachDisplayVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "reachdisplay".contains(q) || "reach display".contains(q) || "reach".contains(q);
        return true;
    }

    public boolean hitColorVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "hitcolor".contains(q) || "hit color".contains(q) || "color".contains(q) || "hurt".contains(q);
        return true;
    }

    public boolean lowShieldVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "lowshield".contains(q) || "low shield".contains(q) || "shield".contains(q) || "totem".contains(q);
        return true;
    }

    public boolean customHitboxesVisible(String search) {
        String q = search.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) return "customhitboxes".contains(q) || "custom hitboxes".contains(q) || "hitbox".contains(q) || "hitboxes".contains(q) || "f3+b".contains(q) || "esp".contains(q);
        return true;
    }

    @Override
    public boolean isVisible(String search) {
        if (search.isEmpty()) return true;
        return autoClickerVisible(search) || reachDisplayVisible(search) || hitColorVisible(search)
                || lowShieldVisible(search) || customHitboxesVisible(search);
    }

    public int getAutoClickerHeight() {
        return AutoClickerModule.expanded ? 304 : 50;
    }

    public int getReachDisplayHeight() {
        return ReachDisplayModule.expanded ? 148 : 50;
    }

    public int getHitColorHeight() {
        return HitColorModule.expanded ? 148 : 50;
    }

    public int getLowShieldHeight() {
        return LowShieldModule.expanded ? 148 : 50;
    }

    public int getCustomHitboxesHeight() {
        return CustomHitboxesModule.expanded ? 252 : 50;
    }

    @Override
    public void layout(BameClientScreen screen, int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
    }

    @Override
    public void calculateHeight(BameClientScreen screen, int columnWidth, ColumnTracker tracker, String search) {
        if (autoClickerVisible(search)) tracker.leftY += getAutoClickerHeight() + 12;
        if (reachDisplayVisible(search)) tracker.leftY += getReachDisplayHeight() + 12;

        if (hitColorVisible(search)) tracker.rightY += getHitColorHeight() + 12;
        if (lowShieldVisible(search)) tracker.rightY += getLowShieldHeight() + 12;
        if (customHitboxesVisible(search)) tracker.rightY += getCustomHitboxesHeight() + 12;
    }

    @Override
    public void render(BameClientScreen screen, DrawContext c, int mx, int my, float delta,
                       int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        // Left Column: AutoClicker, ReachDisplay
        if (autoClickerVisible(search)) {
            int h = getAutoClickerHeight();
            if (screen.isVisibleRow(tracker.leftY, h)) {
                renderAutoClicker(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            }
            tracker.leftY += h + 12;
        }
        if (reachDisplayVisible(search)) {
            int h = getReachDisplayHeight();
            if (screen.isVisibleRow(tracker.leftY, h)) {
                renderReachDisplay(screen, c, mx, my, delta, leftX, tracker.leftY, columnWidth);
            }
            tracker.leftY += h + 12;
        }

        // Right Column: HitColor, LowShield, CustomHitboxes
        if (hitColorVisible(search)) {
            int h = getHitColorHeight();
            if (screen.isVisibleRow(tracker.rightY, h)) {
                renderHitColor(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            }
            tracker.rightY += h + 12;
        }
        if (lowShieldVisible(search)) {
            int h = getLowShieldHeight();
            if (screen.isVisibleRow(tracker.rightY, h)) {
                renderLowShield(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            }
            tracker.rightY += h + 12;
        }
        if (customHitboxesVisible(search)) {
            int h = getCustomHitboxesHeight();
            if (screen.isVisibleRow(tracker.rightY, h)) {
                renderCustomHitboxes(screen, c, mx, my, delta, rightX, tracker.rightY, columnWidth);
            }
            tracker.rightY += h + 12;
        }
    }

    private void renderAutoClicker(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getAutoClickerHeight();
        screen.moduleCard(c, x, y, w, h, AutoClickerModule.enabled, AutoClickerModule.expanded,
                "AutoClicker", "Automatically clicks when holding", "NEW", "combat", mx, my);

        if (!AutoClickerModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningAutoClicker ? "..." : screen.formatKey(AutoClickerModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Mode:", x + 14, curY + 4, 0xFFD4D8E0);
        screen.modeButton(c, "CPS", x + 54, curY + 2, 38, 18, mx, my, AutoClickerModule.mode == 0);
        screen.modeButton(c, "Delay", x + 96, curY + 2, 44, 18, mx, my, AutoClickerModule.mode == 1);
        screen.modeButton(c, "Cooldown", x + 144, curY + 2, 58, 18, mx, my, AutoClickerModule.mode == 2);
        curY += 26;

        if (AutoClickerModule.mode == 0) {
            screen.text(c, "CPS: " + AutoClickerModule.cps, x + 14, curY + 4, 0xFFD4D8E0);
            int sx = x + 72;
            int sw = w - 86;
            CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
            float cpsNorm = Math.clamp((AutoClickerModule.cps - 1) / 19.0f, 0f, 1f);
            int fill = Math.round(sw * cpsNorm);
            if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
            CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        } else if (AutoClickerModule.mode == 1) {
            String delayText = String.format(Locale.US, "%.1fs", AutoClickerModule.delaySeconds);
            screen.text(c, "Delay: " + delayText, x + 14, curY + 4, 0xFFD4D8E0);
            int sx = x + 88;
            int sw = w - 102;
            CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
            float delayNorm = Math.clamp((AutoClickerModule.delaySeconds - 0.1f) / 4.9f, 0f, 1f);
            int fill = Math.round(sw * delayNorm);
            if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
            CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        } else {
            screen.text(c, "100% Full Charge (1.9+ Cooldown)", x + 14, curY + 4, 0xFF8E95A4);
        }
        curY += 26;

        screen.text(c, "Hold Mouse", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, AutoClickerModule.holdMouse, mx, my, delta);
        curY += 26;

        screen.text(c, "Only on Target", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, AutoClickerModule.onlyOnTarget, mx, my, delta);
        curY += 26;

        screen.text(c, "Random Jitter", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, AutoClickerModule.randomJitter, mx, my, delta);
        curY += 26;

        screen.text(c, "Weapon Only", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, AutoClickerModule.weaponOnly, mx, my, delta);
        curY += 26;

        screen.text(c, "Button:", x + 14, curY + 4, 0xFFD4D8E0);
        screen.modeButton(c, "Left", x + 60, curY + 2, 44, 18, mx, my, AutoClickerModule.button == 0);
        screen.modeButton(c, "Right", x + 108, curY + 2, 48, 18, mx, my, AutoClickerModule.button == 1);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderReachDisplay(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getReachDisplayHeight();
        screen.moduleCard(c, x, y, w, h, ReachDisplayModule.enabled, ReachDisplayModule.expanded,
                "ReachDisplay", "Shows hit distance when attacking", "NEW", "combat", mx, my);

        if (!ReachDisplayModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningReachDisplay ? "..." : screen.formatKey(ReachDisplayModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Display Mode:", x + 14, curY + 4, 0xFFD4D8E0);
        curY += 20;

        screen.modeButton(c, "Above Crosshair", x + 14, curY, 96, 18, mx, my, ReachDisplayModule.mode == 0);
        screen.modeButton(c, "HUD Element", x + 114, curY, 78, 18, mx, my, ReachDisplayModule.mode == 1);
        curY += 26;

        if (ReachDisplayModule.mode == 1) {
            screen.button(c, "Edit HUD", x + 14, curY, 50, 16, mx, my);
        }
        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderHitColor(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getHitColorHeight();
        screen.moduleCard(c, x, y, w, h, HitColorModule.enabled, HitColorModule.expanded,
                "HitColor", "Custom hurt effect color and opacity", "NEW", "combat", mx, my);

        if (!HitColorModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningHitColor ? "..." : screen.formatKey(HitColorModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Color:", x + 14, curY + 4, 0xFFD4D8E0);
        int colBtnX = x + 56;
        int colBtnSize = 16;
        boolean hoverCol = screen.inside(mx, my, colBtnX, curY + 2, colBtnSize, colBtnSize);
        c.fill(colBtnX, curY + 2, colBtnX + colBtnSize, curY + 2 + colBtnSize, 0xFF000000 | (HitColorModule.color & 0xFFFFFF));
        CustomGuiUtils.drawUltraRoundedOutline(c, colBtnX, curY + 2, colBtnSize, colBtnSize, hoverCol ? 0xFFFFFFFF : 0xFF353C4D, 2);
        curY += 26;

        int pct = Math.round(HitColorModule.alpha * 100);
        screen.text(c, "Opacity: " + pct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int sx = x + 88;
        int sw = w - 102;
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float alphaNorm = (HitColorModule.alpha - 0.1f) / 0.9f;
        int fill = Math.round(sw * Math.clamp(alphaNorm, 0f, 1f));
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderLowShield(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getLowShieldHeight();
        screen.moduleCard(c, x, y, w, h, LowShieldModule.enabled, LowShieldModule.expanded,
                "LowShield", "Lowers shield and totem viewmodel height", "NEW", "combat", mx, my);

        if (!LowShieldModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningLowShield ? "..." : screen.formatKey(LowShieldModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        int sx = x + 84;
        int sw = w - 98;

        screen.text(c, "Shield: " + LowShieldModule.heightPercent + "%", x + 14, curY + 4, 0xFFD4D8E0);
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float hNorm = (LowShieldModule.heightPercent - 1) / 99.0f;
        int fill = Math.round(sw * Math.clamp(hNorm, 0f, 1f));
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        screen.text(c, "Totem: " + LowShieldModule.totemSizePercent + "%", x + 14, curY + 4, 0xFFD4D8E0);
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        float tNorm = (LowShieldModule.totemSizePercent - 10) / 90.0f;
        int tfill = Math.round(sw * Math.clamp(tNorm, 0f, 1f));
        if (tfill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, tfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + tfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    private void renderCustomHitboxes(BameClientScreen screen, DrawContext c, int mx, int my, float delta, int x, int y, int w) {
        int h = getCustomHitboxesHeight();
        screen.moduleCard(c, x, y, w, h, CustomHitboxesModule.enabled, CustomHitboxesModule.expanded,
                "Hitboxes", "Custom colored hitboxes & view vectors", "NEW", "combat", mx, my);

        if (!CustomHitboxesModule.expanded) return;

        int curY = y + 54;
        screen.text(c, "KeyBind:", x + 14, curY + 4, 0xFF8E95A4);
        String kb = listeningHitboxes ? "..." : screen.formatKey(CustomHitboxesModule.keyBind);
        screen.button(c, kb, x + 64, curY, 48, 16, mx, my);
        curY += 26;

        screen.text(c, "Color:", x + 14, curY + 4, 0xFFD4D8E0);
        int colBtnX = x + 56;
        int colBtnSize = 16;
        boolean hoverCol = screen.inside(mx, my, colBtnX, curY + 2, colBtnSize, colBtnSize);
        c.fill(colBtnX, curY + 2, colBtnX + colBtnSize, curY + 2 + colBtnSize, 0xFF000000 | (CustomHitboxesModule.color & 0xFFFFFF));
        CustomGuiUtils.drawUltraRoundedOutline(c, colBtnX, curY + 2, colBtnSize, colBtnSize, hoverCol ? 0xFFFFFFFF : 0xFF353C4D, 2);
        curY += 26;

        int pct = Math.round(CustomHitboxesModule.fillOpacity * 100);
        screen.text(c, "Fill: " + pct + "%", x + 14, curY + 4, 0xFFD4D8E0);
        int sx = x + 72;
        int sw = w - 86;
        CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, sw, 4, 0xFF303442, 2);
        int fill = Math.round(sw * Math.clamp(CustomHitboxesModule.fillOpacity, 0f, 1f));
        if (fill > 0) CustomGuiUtils.fillUltraRounded(c, sx, curY + 6, fill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, sx + fill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        String widthText = String.format(Locale.US, "%.1f", CustomHitboxesModule.lineWidth);
        screen.text(c, "Width: " + widthText, x + 14, curY + 4, 0xFFD4D8E0);
        int wx = x + 82;
        int ww = w - 96;
        CustomGuiUtils.fillUltraRounded(c, wx, curY + 6, ww, 4, 0xFF303442, 2);
        float widthNorm = (CustomHitboxesModule.lineWidth - 1.0f) / 4.0f;
        int wfill = Math.round(ww * Math.clamp(widthNorm, 0f, 1f));
        if (wfill > 0) CustomGuiUtils.fillUltraRounded(c, wx, curY + 6, wfill, 4, GuiTheme.accent(), 2);
        CustomGuiUtils.fillUltraRounded(c, wx + wfill - 3, curY + 4, 7, 8, 0xFFFFFFFF, 4);
        curY += 26;

        screen.text(c, "Target:", x + 14, curY + 4, 0xFFD4D8E0);
        screen.modeButton(c, "All", x + 60, curY + 2, 34, 18, mx, my, CustomHitboxesModule.targetMode == 0);
        screen.modeButton(c, "Players", x + 98, curY + 2, 50, 18, mx, my, CustomHitboxesModule.targetMode == 1);
        screen.modeButton(c, "Mobs", x + 152, curY + 2, 44, 18, mx, my, CustomHitboxesModule.targetMode == 2);
        curY += 26;

        screen.text(c, "Eye Height Line", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, CustomHitboxesModule.showEyeHeight, mx, my, delta);
        curY += 26;

        screen.text(c, "View Direction", x + 14, curY + 4, 0xFFD4D8E0);
        screen.toggle(c, x + w - 38, curY + 2, CustomHitboxesModule.showViewVector, mx, my, delta);
        curY += 26;

        screen.button(c, "Reset", x + w - 58, curY, 46, 16, mx, my);
    }

    @Override
    public boolean mouseClicked(BameClientScreen screen, Click click, boolean twice,
                                int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        double mx = click.x();
        double my = click.y();

        // 1. AutoClicker
        if (autoClickerVisible(search)) {
            int y = tracker.leftY;
            int h = getAutoClickerHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        AutoClickerModule.enabled = !AutoClickerModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("AutoClicker", AutoClickerModule.enabled);
                    },
                    () -> AutoClickerModule.expanded = !AutoClickerModule.expanded)) {
                return true;
            }
            if (AutoClickerModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningAutoClicker = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + 54, curY + 2, 38, 18)) { AutoClickerModule.mode = 0; BameClientConfig.save(); return true; }
                if (screen.inside(mx, my, leftX + 96, curY + 2, 44, 18)) { AutoClickerModule.mode = 1; BameClientConfig.save(); return true; }
                if (screen.inside(mx, my, leftX + 144, curY + 2, 58, 18)) { AutoClickerModule.mode = 2; BameClientConfig.save(); return true; }
                curY += 26;

                if (AutoClickerModule.mode == 0) {
                    int sx = leftX + 72;
                    int sw = columnWidth - 86;
                    if (screen.inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                        draggingCps = true;
                        float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                        AutoClickerModule.cps = Math.max(1, Math.min(20, Math.round(1 + fval * 19.0f)));
                        BameClientConfig.save();
                        return true;
                    }
                } else if (AutoClickerModule.mode == 1) {
                    int sx = leftX + 88;
                    int sw = columnWidth - 102;
                    if (screen.inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                        draggingAutoClickerDelay = true;
                        float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                        AutoClickerModule.delaySeconds = Math.round((0.1f + fval * 4.9f) * 10.0f) / 10.0f;
                        BameClientConfig.save();
                        return true;
                    }
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) {
                    AutoClickerModule.holdMouse = !AutoClickerModule.holdMouse;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) {
                    AutoClickerModule.onlyOnTarget = !AutoClickerModule.onlyOnTarget;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) {
                    AutoClickerModule.randomJitter = !AutoClickerModule.randomJitter;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 38, curY + 2, 26, 14)) {
                    AutoClickerModule.weaponOnly = !AutoClickerModule.weaponOnly;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, leftX + 60, curY + 2, 44, 18)) { AutoClickerModule.button = 0; BameClientConfig.save(); return true; }
                if (screen.inside(mx, my, leftX + 108, curY + 2, 48, 18)) { AutoClickerModule.button = 1; BameClientConfig.save(); return true; }
                curY += 26;

                if (screen.inside(mx, my, leftX + columnWidth - 58, curY, 46, 16)) {
                    AutoClickerModule.resetToDefault();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        // 2. ReachDisplay
        if (reachDisplayVisible(search)) {
            int y = tracker.leftY;
            int h = getReachDisplayHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, leftX, y, columnWidth,
                    () -> {
                        ReachDisplayModule.enabled = !ReachDisplayModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Reach Display", ReachDisplayModule.enabled);
                    },
                    () -> ReachDisplayModule.expanded = !ReachDisplayModule.expanded)) {
                return true;
            }
            if (ReachDisplayModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, leftX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningReachDisplay = true;
                    return true;
                }
                curY += 26;

                curY += 20; // past Display Mode text
                if (screen.inside(mx, my, leftX + 14, curY, 96, 18)) { ReachDisplayModule.mode = 0; BameClientConfig.save(); return true; }
                if (screen.inside(mx, my, leftX + 114, curY, 78, 18)) { ReachDisplayModule.mode = 1; BameClientConfig.save(); return true; }
                curY += 26;

                if (ReachDisplayModule.mode == 1 && screen.inside(mx, my, leftX + 14, curY, 50, 16)) {
                    screen.openHudEditor();
                    return true;
                }
                if (screen.inside(mx, my, leftX + columnWidth - 58, curY, 46, 16)) {
                    ReachDisplayModule.resetToDefault();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.leftY += h + 12;
        }

        // 3. HitColor
        if (hitColorVisible(search)) {
            int y = tracker.rightY;
            int h = getHitColorHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        HitColorModule.enabled = !HitColorModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Hit Color", HitColorModule.enabled);
                    },
                    () -> HitColorModule.expanded = !HitColorModule.expanded)) {
                return true;
            }
            if (HitColorModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningHitColor = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + 56, curY + 2, 16, 16)) {
                    screen.openModalColorPicker(1, HitColorModule.color);
                    return true;
                }
                curY += 26;

                int sx = rightX + 88;
                int sw = columnWidth - 102;
                if (screen.inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                    draggingHitColorAlpha = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    HitColorModule.alpha = Math.round((0.1f + fval * 0.9f) * 100.0f) / 100.0f;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    HitColorModule.resetToDefault();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        // 4. LowShield
        if (lowShieldVisible(search)) {
            int y = tracker.rightY;
            int h = getLowShieldHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        LowShieldModule.enabled = !LowShieldModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Low Shield", LowShieldModule.enabled);
                    },
                    () -> LowShieldModule.expanded = !LowShieldModule.expanded)) {
                return true;
            }
            if (LowShieldModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningLowShield = true;
                    return true;
                }
                curY += 26;

                int sx = rightX + 84;
                int sw = columnWidth - 98;
                if (screen.inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                    draggingLowShieldHeight = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    LowShieldModule.heightPercent = Math.max(1, Math.min(100, Math.round(1 + fval * 99.0f)));
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                    draggingLowShieldTotem = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    LowShieldModule.totemSizePercent = Math.max(10, Math.min(100, Math.round(10 + fval * 90.0f)));
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    LowShieldModule.resetToDefault();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        // 5. CustomHitboxes
        if (customHitboxesVisible(search)) {
            int y = tracker.rightY;
            int h = getCustomHitboxesHeight();
            if (screen.handleCardHeaderClick((int) mx, (int) my, rightX, y, columnWidth,
                    () -> {
                        CustomHitboxesModule.enabled = !CustomHitboxesModule.enabled;
                        com.bame.client.notification.NotificationManager.showModuleToggle("Hitboxes", CustomHitboxesModule.enabled);
                    },
                    () -> CustomHitboxesModule.expanded = !CustomHitboxesModule.expanded)) {
                return true;
            }
            if (CustomHitboxesModule.expanded) {
                int curY = y + 54;
                if (screen.inside(mx, my, rightX + 64, curY, 48, 16)) {
                    clearKeyListeners();
                    listeningHitboxes = true;
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + 56, curY + 2, 16, 16)) {
                    screen.openModalColorPicker(2, CustomHitboxesModule.color);
                    return true;
                }
                curY += 26;

                int sx = rightX + 72;
                int sw = columnWidth - 86;
                if (screen.inside(mx, my, sx - 4, curY + 2, sw + 8, 14)) {
                    draggingHitboxAlpha = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    CustomHitboxesModule.fillOpacity = Math.round(fval * 100.0f) / 100.0f;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                int wx = rightX + 82;
                int ww = columnWidth - 96;
                if (screen.inside(mx, my, wx - 4, curY + 2, ww + 8, 14)) {
                    draggingHitboxWidth = true;
                    float fval = (float) Math.clamp((mx - wx) / (double) ww, 0.0, 1.0);
                    CustomHitboxesModule.lineWidth = Math.round((1.0f + fval * 4.0f) * 10.0f) / 10.0f;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + 60, curY + 2, 34, 18)) { CustomHitboxesModule.targetMode = 0; BameClientConfig.save(); return true; }
                if (screen.inside(mx, my, rightX + 98, curY + 2, 50, 18)) { CustomHitboxesModule.targetMode = 1; BameClientConfig.save(); return true; }
                if (screen.inside(mx, my, rightX + 152, curY + 2, 44, 18)) { CustomHitboxesModule.targetMode = 2; BameClientConfig.save(); return true; }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 38, curY + 2, 26, 14)) {
                    CustomHitboxesModule.showEyeHeight = !CustomHitboxesModule.showEyeHeight;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 38, curY + 2, 26, 14)) {
                    CustomHitboxesModule.showViewVector = !CustomHitboxesModule.showViewVector;
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                if (screen.inside(mx, my, rightX + columnWidth - 58, curY, 46, 16)) {
                    CustomHitboxesModule.resetToDefault();
                    BameClientConfig.save();
                    return true;
                }
            }
            tracker.rightY += h + 12;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(BameClientScreen screen, Click click, double deltaX, double deltaY) {
        double mx = click.x();
        int columnWidth = screen.getColumnWidth();
        int leftX = screen.getContentLeftX();
        int rightX = screen.getContentRightX();

        if (draggingCps) {
            int sx = leftX + 72;
            int sw = columnWidth - 86;
            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
            AutoClickerModule.cps = Math.max(1, Math.min(20, Math.round(1 + fval * 19.0f)));
            return true;
        }
        if (draggingAutoClickerDelay) {
            int sx = leftX + 88;
            int sw = columnWidth - 102;
            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
            AutoClickerModule.delaySeconds = Math.round((0.1f + fval * 4.9f) * 10.0f) / 10.0f;
            return true;
        }
        if (draggingHitColorAlpha) {
            int sx = rightX + 88;
            int sw = columnWidth - 102;
            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
            HitColorModule.alpha = Math.round((0.1f + fval * 0.9f) * 100.0f) / 100.0f;
            return true;
        }
        if (draggingLowShieldHeight) {
            int sx = rightX + 84;
            int sw = columnWidth - 98;
            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
            LowShieldModule.heightPercent = Math.max(1, Math.min(100, Math.round(1 + fval * 99.0f)));
            return true;
        }
        if (draggingLowShieldTotem) {
            int sx = rightX + 84;
            int sw = columnWidth - 98;
            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
            LowShieldModule.totemSizePercent = Math.max(10, Math.min(100, Math.round(10 + fval * 90.0f)));
            return true;
        }
        if (draggingHitboxAlpha) {
            int sx = rightX + 72;
            int sw = columnWidth - 86;
            float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
            CustomHitboxesModule.fillOpacity = Math.round(fval * 100.0f) / 100.0f;
            return true;
        }
        if (draggingHitboxWidth) {
            int wx = rightX + 82;
            int ww = columnWidth - 96;
            float fval = (float) Math.clamp((mx - wx) / (double) ww, 0.0, 1.0);
            CustomHitboxesModule.lineWidth = Math.round((1.0f + fval * 4.0f) * 10.0f) / 10.0f;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(BameClientScreen screen, Click click) {
        boolean released = draggingCps || draggingAutoClickerDelay || draggingHitColorAlpha
                || draggingLowShieldHeight || draggingLowShieldTotem || draggingHitboxAlpha || draggingHitboxWidth;
        draggingCps = false;
        draggingAutoClickerDelay = false;
        draggingHitColorAlpha = false;
        draggingLowShieldHeight = false;
        draggingLowShieldTotem = false;
        draggingHitboxAlpha = false;
        draggingHitboxWidth = false;
        if (released) {
            BameClientConfig.save();
        }
        return released;
    }

    @Override
    public boolean keyPressed(BameClientScreen screen, KeyInput input) {
        int key = input.key();
        if (listeningAutoClicker) {
            AutoClickerModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningAutoClicker = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningReachDisplay) {
            ReachDisplayModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningReachDisplay = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningHitColor) {
            HitColorModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningHitColor = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningLowShield) {
            LowShieldModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningLowShield = false;
            BameClientConfig.save();
            return true;
        }
        if (listeningHitboxes) {
            CustomHitboxesModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningHitboxes = false;
            BameClientConfig.save();
            return true;
        }
        return false;
    }

    private void clearKeyListeners() {
        listeningAutoClicker = false;
        listeningReachDisplay = false;
        listeningHitColor = false;
        listeningLowShield = false;
        listeningHitboxes = false;
    }
}
