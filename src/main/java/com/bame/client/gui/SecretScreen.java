package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.module.AggroPearlModule;
import com.bame.client.module.AutoCartModule;
import com.bame.client.module.AutoMaceModule;
import com.bame.client.module.DTapModule;
import com.bame.client.module.FakeScoreboardModule;
import com.bame.client.module.NameProtectModule;
import com.bame.client.module.PearlCatchModule;
import com.bame.client.module.PearlPredictionModule;
import com.bame.client.module.SkinProtectModule;
import com.bame.client.module.TriggerbotModule;
import com.bame.client.module.ShieldBreakerModule;
import com.bame.client.render.BlurRenderer;
import com.bame.client.sound.ClientSoundManager;
import com.bame.client.wallpaper.WallpaperManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class SecretScreen extends Screen {
    private final Screen parent;
    private final AmbientLighting ambient = new AmbientLighting();
    private boolean listeningCombo = false;
    private boolean listeningPearlKey = false;
    private boolean listeningCartKey = false;
    private boolean listeningMaceKey = false;
    private boolean listeningDTapKey = false;
    private boolean listeningCatchKey = false;
    private boolean listeningAggroKey = false;
    private boolean listeningNameKey = false;
    private boolean listeningFakeKey = false;
    private boolean listeningSkinKey = false;
    private boolean listeningTriggerbotKey = false;
    private boolean listeningShieldBreakerKey = false;
    private boolean draggingMaceHeight = false;
    private boolean draggingDTapDelay = false;
    private boolean draggingCatchDelay = false;
    private boolean draggingAggroDelay = false;
    private boolean draggingTriggerbotCooldown = false;
    private boolean draggingTriggerbotRange = false;
    private boolean draggingShieldBreakerRange = false;
    private long openTime = System.currentTimeMillis();

    private double scroll = 0;
    private boolean scrollDragging = false;
    private double scrollGrab = 0;

    private CustomTextFieldWidget searchField;
    private CustomTextFieldWidget fakeMoneyField;
    private CustomTextFieldWidget fakeStarsField;
    private CustomTextFieldWidget fakeKillsField;
    private CustomTextFieldWidget fakeDeathsField;
    private CustomTextFieldWidget fakeTimeField;

    private CustomTextFieldWidget nameProtectAliasField;
    private CustomTextFieldWidget skinSearchWidget;
    private PlayerSkinWidget skinPreviewWidget;

    private int pw, ph, px, py;
    private PlayerSkinWidget leftSkinWidget;

    public SecretScreen(Screen parent) {
        super(Text.literal("Secret Menu"));
        this.parent = parent;
    }

    private void geometry() {
        if (BameClientConfig.threeDGui) {
            int minLeftSpace = Math.min(230, (int) (width * 0.28));
            pw = Math.clamp((int) (width * 0.64), 540, 680);
            pw = Math.min(pw, width - minLeftSpace - 24);
            pw = Math.min(pw, width - 40);
            ph = Math.clamp((int) (height * 0.68), 340, 420);
            ph = Math.min(ph, height - 40);
            px = width - pw - Math.max(16, (int) (width * 0.025));
        } else {
            pw = Math.clamp(width - 40, 600, 680);
            ph = Math.clamp(height - 40, 320, 390);
            px = (width - pw) / 2;
        }
        py = (height - ph) / 2;
        updateLeftSkinGeometry();
    }

    private void initLeftSkinWidget() {
        if (client == null || client.getLoadedEntityModels() == null) return;
        int leftW = Math.max(100, px - 20);
        int skinW = Math.clamp(leftW, 130, 200);
        int skinH = Math.clamp(ph - 50, 220, 290);
        int skinX = Math.max(10, (px - skinW) / 2);
        int skinY = py + (ph - skinH) / 2 + 16;

        leftSkinWidget = new PlayerSkinWidget(skinW, skinH, client.getLoadedEntityModels(), () -> {
            if (SkinProtectModule.enabled) {
                return SkinProtectModule.getCurrentSkin();
            }
            return (client.player != null) ? client.player.getSkin() : net.minecraft.client.util.DefaultSkinHelper.getSteve();
        });
        leftSkinWidget.setPosition(skinX, skinY);
        setSkinWidgetInitialRotation(leftSkinWidget, -22.0f);
    }

    private void updateLeftSkinGeometry() {
        if (!BameClientConfig.threeDGui) return;
        if (leftSkinWidget == null) {
            initLeftSkinWidget();
            return;
        }
        int leftW = Math.max(100, px - 20);
        int skinW = Math.clamp(leftW, 130, 200);
        int skinH = Math.clamp(ph - 50, 220, 290);
        int skinX = Math.max(10, (px - skinW) / 2);
        int skinY = py + (ph - skinH) / 2 + 16;
        leftSkinWidget.setDimensionsAndPosition(skinW, skinH, skinX, skinY);
    }

    private void setSkinWidgetInitialRotation(PlayerSkinWidget widget, float yRot) {
        if (widget == null) return;
        try {
            for (java.lang.reflect.Field f : PlayerSkinWidget.class.getDeclaredFields()) {
                if (f.getType() == float.class && (f.getName().equals("yRotation") || f.getName().contains("yRot"))) {
                    f.setAccessible(true);
                    f.setFloat(widget, yRot);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private void renderLeftSkin(DrawContext c, int mx, int my, float delta, float p) {
        if (!BameClientConfig.threeDGui || px < 100) return;
        if (leftSkinWidget == null) initLeftSkinWidget();
        if (leftSkinWidget == null) return;

        int leftCenterX = px / 2;

        // Player Name Badge (NameProtect integration: alias when enabled, own name when disabled)
        String displayName = (client != null && client.player != null) ? client.player.getName().getString() : "Player";
        if (NameProtectModule.enabled) {
            displayName = NameProtectModule.getProtectedName(displayName);
        }
        int nameW = textRenderer.getWidth(CustomGuiUtils.getFontText(displayName));
        int badgeW = nameW + 16;
        int badgeH = 15;
        int badgeX = leftCenterX - badgeW / 2;
        int badgeY = py + 12;
        CustomGuiUtils.fillUltraRounded(c, badgeX, badgeY, badgeW, badgeH, 0xD0131722, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, badgeX, badgeY, badgeW, badgeH, GuiTheme.alpha(GuiTheme.accent(), 110), 4);
        c.drawText(textRenderer, CustomGuiUtils.getFontText(displayName), badgeX + 8, badgeY + 4, GuiTheme.accent(), false);

        // Pedestal & Drop Shadow under feet
        int skinW = leftSkinWidget.getWidth();
        int skinH = leftSkinWidget.getHeight();
        int pedW = Math.min(130, (int) (skinW * 0.72));
        int pedX = leftCenterX - pedW / 2;
        int pedY = leftSkinWidget.getY() + skinH - 14;

        CustomGuiUtils.fillUltraRounded(c, pedX - 10, pedY + 2, pedW + 24, 10, 0x45000000, 4);
        CustomGuiUtils.fillUltraRounded(c, pedX, pedY, pedW, 6, 0x30000000 | (GuiTheme.accent() & 0x00FFFFFF), 3);
        CustomGuiUtils.drawUltraRoundedOutline(c, pedX, pedY, pedW, 6, GuiTheme.alpha(GuiTheme.accent(), 85), 3);

        // 3D Player Model
        leftSkinWidget.render(c, mx, my, delta);
    }

    @Override
    protected void init() {
        super.init();
        geometry();
        initLeftSkinWidget();
        int gap = 14;
        int cardW = (pw - 28 - gap) / 2;
        int labelW = 44;
        int fieldW = cardW - labelW - 28;

        // Search bar replacing "Zurück" button
        searchField = new CustomTextFieldWidget(0, 0, 120, 18, Text.literal("Search"));
        searchField.setPlaceholder("Suchen...");
        addSelectableChild(searchField);

        fakeMoneyField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Money"));
        fakeMoneyField.setText(FakeScoreboardModule.money != null ? FakeScoreboardModule.money : "$12,450");
        fakeMoneyField.setPlaceholder("e.g. $12,450");
        fakeMoneyField.setChangedListener(s -> { FakeScoreboardModule.money = s; BameClientConfig.save(); });
        addSelectableChild(fakeMoneyField);

        fakeStarsField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Stars"));
        fakeStarsField.setText(FakeScoreboardModule.stars != null ? FakeScoreboardModule.stars : "★ 5");
        fakeStarsField.setPlaceholder("e.g. ★ 5");
        fakeStarsField.setChangedListener(s -> { FakeScoreboardModule.stars = s; BameClientConfig.save(); });
        addSelectableChild(fakeStarsField);

        fakeKillsField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Kills"));
        fakeKillsField.setText(FakeScoreboardModule.kills != null ? FakeScoreboardModule.kills : "128");
        fakeKillsField.setPlaceholder("e.g. 128");
        fakeKillsField.setChangedListener(s -> { FakeScoreboardModule.kills = s; BameClientConfig.save(); });
        addSelectableChild(fakeKillsField);

        fakeDeathsField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Deaths"));
        fakeDeathsField.setText(FakeScoreboardModule.deaths != null ? FakeScoreboardModule.deaths : "12");
        fakeDeathsField.setPlaceholder("e.g. 12");
        fakeDeathsField.setChangedListener(s -> { FakeScoreboardModule.deaths = s; BameClientConfig.save(); });
        addSelectableChild(fakeDeathsField);

        fakeTimeField = new CustomTextFieldWidget(0, 0, fieldW, 18, Text.literal("Time"));
        fakeTimeField.setText(FakeScoreboardModule.playtime != null ? FakeScoreboardModule.playtime : "42h");
        fakeTimeField.setPlaceholder("e.g. 42h");
        fakeTimeField.setChangedListener(s -> { FakeScoreboardModule.playtime = s; BameClientConfig.save(); });
        addSelectableChild(fakeTimeField);

        nameProtectAliasField = new CustomTextFieldWidget(0, 0, cardW - 54 - 14, 18, Text.literal("Alias"));
        nameProtectAliasField.setText(NameProtectModule.alias != null ? NameProtectModule.alias : "You");
        nameProtectAliasField.setPlaceholder("z.B. You");
        nameProtectAliasField.setChangedListener(s -> { NameProtectModule.alias = s; BameClientConfig.save(); });
        addSelectableChild(nameProtectAliasField);

        skinSearchWidget = new CustomTextFieldWidget(0, 0, 100, 18, Text.literal("Player Name"));
        skinSearchWidget.setPlaceholder("Spielername...");
        addSelectableChild(skinSearchWidget);

        if (client != null && client.getLoadedEntityModels() != null) {
            int prevPad = 12;
            int prevW = cardW - prevPad * 2;
            int prevH = 135;
            skinPreviewWidget = new PlayerSkinWidget(prevW, prevH, client.getLoadedEntityModels(), SkinProtectModule::getCurrentSkin);
        }
    }

    private boolean pearlVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "pearl prediction".contains(q) || "pearl".contains(q) || "prediction".contains(q) || "landing".contains(q) || "throw".contains(q);
    }

    private boolean autoCartVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "autocart".contains(q) || "auto cart".contains(q) || "cart".contains(q) || "tnt".contains(q) || "minecart".contains(q) || "flame".contains(q) || "bow".contains(q) || "rail".contains(q) || "schiene".contains(q) || "slot".contains(q) || "switch".contains(q) || "automatic".contains(q) || "automatisch".contains(q);
    }

    private boolean autoMaceVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "auto mace".contains(q) || "automace".contains(q) || "mace".contains(q) || "keule".contains(q) || "smash".contains(q) || "anticheat".contains(q) || "slot switch".contains(q) || "delay".contains(q);
    }

    private boolean dTapVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "dtap".contains(q) || "d-tap".contains(q) || "double tap".contains(q) || "doubletap".contains(q) || "crystal".contains(q) || "cpvp".contains(q) || "end crystal".contains(q) || "totem".contains(q) || "pop".contains(q);
    }

    private boolean pearlCatchVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "pearl catch".contains(q) || "pearlcatch".contains(q) || "catch".contains(q) || "wind pearl".contains(q) || "wind charge".contains(q) || "wind".contains(q) || "boost".contains(q) || "pearl".contains(q);
    }

    private boolean aggroPearlVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "aggro pearl".contains(q) || "aggropearl".contains(q) || "aggro".contains(q) || "counter pearl".contains(q) || "hunter".contains(q) || "pearl".contains(q);
    }

    private boolean nameProtectVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "name protect".contains(q) || "name".contains(q) || "protect".contains(q) || "alias".contains(q);
    }

    private boolean fakeScoreboardVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "fake scoreboard".contains(q) || "fake".contains(q) || "scoreboard".contains(q) || "stats".contains(q) || "money".contains(q) || "kills".contains(q) || "deaths".contains(q);
    }

    private boolean skinProtectVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "skinprotect".contains(q) || "skin protect".contains(q) || "skin".contains(q) || "protect".contains(q);
    }

    private boolean triggerbotVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "triggerbot".contains(q) || "trigger bot".contains(q) || "trigger".contains(q) || "autoclick".contains(q) || "cooldown".contains(q) || "cps".contains(q) || "combat".contains(q);
    }

    private boolean shieldBreakerVisible() {
        String q = searchField != null ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return "shield breaker".contains(q) || "shieldbreaker".contains(q) || "shield".contains(q) || "schild".contains(q) || "axe".contains(q) || "axt".contains(q) || "breaker".contains(q);
    }

    private int getPearlHeight() {
        return PearlPredictionModule.expanded ? 168 : 46;
    }

    private int getAutoCartHeight() {
        return AutoCartModule.expanded ? 158 : 46;
    }

    private int getAutoMaceHeight() {
        return AutoMaceModule.expanded ? 240 : 46;
    }

    private int getDTapHeight() {
        return DTapModule.expanded ? 164 : 46;
    }

    private int getTriggerbotHeight() {
        if (!TriggerbotModule.expanded) return 46;
        return TriggerbotModule.showDetails ? 316 : 206;
    }

    private int getPearlCatchHeight() {
        return PearlCatchModule.expanded ? 190 : 46;
    }

    private int getAggroPearlHeight() {
        return AggroPearlModule.expanded ? 164 : 46;
    }

    private int getNameProtectHeight() {
        return NameProtectModule.expanded ? 104 : 46;
    }

    private int getFakeScoreboardHeight() {
        return FakeScoreboardModule.expanded ? 208 : 46;
    }

    private int getSkinProtectHeight() {
        return SkinProtectModule.expanded ? 275 : 46;
    }

    private int getShieldBreakerHeight() {
        return ShieldBreakerModule.expanded ? 186 : 46;
    }

    private void resetFakeScoreboard() {
        FakeScoreboardModule.hudX = -1;
        FakeScoreboardModule.hudY = -1;
        FakeScoreboardModule.scale = 1.0f;
        FakeScoreboardModule.customWidth = -1;
        FakeScoreboardModule.customHeight = -1;
        FakeScoreboardModule.bgMode = 0;
        FakeScoreboardModule.outlineColor = 0xFFFFFFFF;
        FakeScoreboardModule.money = "$12,450";
        FakeScoreboardModule.stars = "★ 5";
        FakeScoreboardModule.kills = "128";
        FakeScoreboardModule.deaths = "12";
        FakeScoreboardModule.playtime = "42h";
        if (fakeMoneyField != null) fakeMoneyField.setText(FakeScoreboardModule.money);
        if (fakeStarsField != null) fakeStarsField.setText(FakeScoreboardModule.stars);
        if (fakeKillsField != null) fakeKillsField.setText(FakeScoreboardModule.kills);
        if (fakeDeathsField != null) fakeDeathsField.setText(FakeScoreboardModule.deaths);
        if (fakeTimeField != null) fakeTimeField.setText(FakeScoreboardModule.playtime);
        BameClientConfig.save();
    }

    private void resetNameProtect() {
        NameProtectModule.enabled = false;
        NameProtectModule.keyBind = -1;
        NameProtectModule.alias = "You";
        if (nameProtectAliasField != null) nameProtectAliasField.setText("You");
        BameClientConfig.save();
    }

    private void resetSkinProtect() {
        SkinProtectModule.reset();
        if (skinSearchWidget != null) skinSearchWidget.setText("");
        BameClientConfig.save();
    }

    private void applySkinSearch() {
        if (skinSearchWidget == null) return;
        String name = skinSearchWidget.getText().trim();
        if (name.isEmpty()) return;
        SkinProtectModule.setSkinByName(name, success -> {
            if (success) {
                ClientSoundManager.playClick();
                BameClientConfig.save();
            }
        });
    }

    private void unfocus() {
        if (searchField != null) searchField.setFocused(false);
        if (fakeMoneyField != null) fakeMoneyField.setFocused(false);
        if (fakeStarsField != null) fakeStarsField.setFocused(false);
        if (fakeKillsField != null) fakeKillsField.setFocused(false);
        if (fakeDeathsField != null) fakeDeathsField.setFocused(false);
        if (fakeTimeField != null) fakeTimeField.setFocused(false);
        if (nameProtectAliasField != null) nameProtectAliasField.setFocused(false);
        if (skinSearchWidget != null) skinSearchWidget.setFocused(false);
        setFocused(null);
    }

    private int contentHeight() {
        int leftH = 0;
        if (pearlVisible()) leftH += getPearlHeight() + 12;
        if (autoCartVisible()) leftH += getAutoCartHeight() + 12;
        if (autoMaceVisible()) leftH += getAutoMaceHeight() + 12;
        if (dTapVisible()) leftH += getDTapHeight() + 12;
        if (triggerbotVisible()) leftH += getTriggerbotHeight() + 12;

        int rightH = 0;
        if (pearlCatchVisible()) rightH += getPearlCatchHeight() + 12;
        if (aggroPearlVisible()) rightH += getAggroPearlHeight() + 12;
        if (nameProtectVisible()) rightH += getNameProtectHeight() + 12;
        if (fakeScoreboardVisible()) rightH += getFakeScoreboardHeight() + 12;
        if (skinProtectVisible()) rightH += getSkinProtectHeight() + 12;
        if (shieldBreakerVisible()) rightH += getShieldBreakerHeight() + 12;

        return Math.max(leftH, rightH);
    }

    private double maxScroll(int ch) {
        return Math.max(0, contentHeight() - ch);
    }

    private int thumbHeight(int ch) {
        return Math.max(24, (int) (ch * (ch / (double) Math.max(ch, contentHeight()))));
    }

    private int thumbY(int cy, int ch) {
        return cy + (int) ((ch - thumbHeight(ch)) * (scroll / Math.max(1, maxScroll(ch))));
    }

    private void dragScroll(double my, int cy, int ch) {
        double track = ch - thumbHeight(ch);
        if (track <= 0) return;
        double p = Math.clamp((my - cy - scrollGrab) / track, 0.0, 1.0);
        scroll = p * maxScroll(ch);
    }

    @Override
    public void close() {
        BameClientConfig.save();
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    protected void applyBlur(DrawContext c) {
        if (client == null) return;
        BlurRenderer.ensureUpdated(client);
        BlurRenderer.drawBlurBox(c, 0, 0, width, height);
        c.fill(0, 0, width, height, 0x550B0E14);
    }

    private void box(DrawContext c, int x, int y, int w, int h, int color) {
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, color, 6);
    }

    private void card(DrawContext c, int x, int y, int w, int h, boolean enabled) {
        int bg = enabled ? 0xFF161822 : 0xFF12141C;
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, bg, 6.0f);
        int border = enabled ? GuiTheme.alpha(GuiTheme.accent(), 175) : 0xFF232733;
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, border, 6.0f);
    }

    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void text(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, CustomGuiUtils.getFontText(s), x, y, color, false);
    }

    private void button(DrawContext c, String label, int x, int y, int w, int h, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, hover ? 0xFF252A34 : 0xFF181C24, 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, hover ? GuiTheme.accent() : 0xFF292D36, 4);
        int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
        text(c, label, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFD4D8E0);
    }

    private void modeButton(DrawContext c, String label, int x, int y, int w, int h, int mx, int my, boolean selected) {
        boolean hover = inside(mx, my, x, y, w, h);
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, selected ? GuiTheme.alpha(GuiTheme.accent(), 80) : (hover ? 0xFF252A34 : 0xFF181C24), 4);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, selected ? GuiTheme.accent() : (hover ? 0xFF606575 : 0xFF292D36), 4);
        int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
        text(c, label, x + (w - tw) / 2, y + (h - 8) / 2, selected ? 0xFFFFFFFF : 0xFFD4D8E0);
    }

    private void toggle(DrawContext c, int x, int y, boolean on, int mx, int my, float delta) {
        new CustomToggleWidget(x, y, 26, 14, on).render(c, mx, my, delta);
    }

    /** Tasten-Knopf mit Konfliktanzeige (rot + "Also: Modul"), wie im Hauptmenü. */
    private void keybindButton(DrawContext c, String id, int key, boolean listening, int x, int y, int w, int h, int mx, int my, int cardW) {
        String label = listening ? "..." : formatKey(key);
        String conflict = listening ? null : KeybindConflicts.conflictFor(id, key);
        int hintX = x + w + 6;
        int maxHintW = cardW - 158; // Karte beginnt 60 px links vom Knopf, rechts sitzt der Schalter
        String hint = listening ? "Esc = none" : (conflict != null ? "Also: " + conflict : null);
        if (conflict == null) {
            button(c, label, x, y, w, h, mx, my);
        } else {
            boolean hover = inside(mx, my, x, y, w, h);
            CustomGuiUtils.fillUltraRounded(c, x, y, w, h, hover ? 0xFF3A1C22 : 0xFF2A161B, 4);
            CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, 0xFFEF4444, 4);
            int tw = textRenderer.getWidth(CustomGuiUtils.getFontText(label));
            text(c, label, x + (w - tw) / 2, y + (h - 8) / 2, 0xFFFCA5A5);
        }
        if (hint != null && maxHintW >= 12) {
            String t = hint;
            if (textRenderer.getWidth(CustomGuiUtils.getFontText(t)) > maxHintW) {
                while (t.length() > 1 && textRenderer.getWidth(CustomGuiUtils.getFontText(t + "...")) > maxHintW) {
                    t = t.substring(0, t.length() - 1);
                }
                t = t + "...";
            }
            text(c, t, hintX, y + (h - 8) / 2, conflict != null ? 0xFFF87171 : 0xFF8E95A4);
        }
    }

    private boolean anyModuleKeyListening() {
        return listeningPearlKey || listeningCartKey || listeningMaceKey || listeningDTapKey || listeningCatchKey
                || listeningAggroKey || listeningNameKey || listeningFakeKey || listeningSkinKey
                || listeningTriggerbotKey || listeningShieldBreakerKey;
    }

    private void clearModuleKeyListening() {
        listeningPearlKey = false;
        listeningCartKey = false;
        listeningMaceKey = false;
        listeningDTapKey = false;
        listeningCatchKey = false;
        listeningAggroKey = false;
        listeningNameKey = false;
        listeningFakeKey = false;
        listeningSkinKey = false;
        listeningTriggerbotKey = false;
        listeningShieldBreakerKey = false;
    }

    private String formatKey(int key) {
        if (key < 0) return "None";
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) return "RSHIFT";
        if (key == GLFW.GLFW_KEY_LEFT_SHIFT) return "LSHIFT";
        if (key == GLFW.GLFW_KEY_RIGHT_CONTROL) return "RCTRL";
        if (key == GLFW.GLFW_KEY_LEFT_CONTROL) return "LCTRL";
        if (key == GLFW.GLFW_KEY_RIGHT_ALT) return "RALT";
        if (key == GLFW.GLFW_KEY_LEFT_ALT) return "LALT";
        String s = GLFW.glfwGetKeyName(key, 0);
        if (s != null) return s.toUpperCase(Locale.ROOT);
        return InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString().toUpperCase(Locale.ROOT);
    }

    public static String getComboString() {
        StringBuilder sb = new StringBuilder();
        if (BameClientConfig.secretRequireCtrl) sb.append("Ctrl + ");
        if (BameClientConfig.secretRequireAlt) sb.append("Alt + ");
        if (BameClientConfig.secretRequireShift) sb.append("Shift + ");
        int key = BameClientConfig.secretKey;
        if (key <= 0) {
            sb.append("None");
        } else {
            String name = GLFW.glfwGetKeyName(key, 0);
            if (name != null && !name.isEmpty()) {
                sb.append(name.toUpperCase(Locale.ROOT));
            } else {
                switch (key) {
                    case GLFW.GLFW_KEY_F1 -> sb.append("F1");
                    case GLFW.GLFW_KEY_F2 -> sb.append("F2");
                    case GLFW.GLFW_KEY_F3 -> sb.append("F3");
                    case GLFW.GLFW_KEY_F4 -> sb.append("F4");
                    case GLFW.GLFW_KEY_F5 -> sb.append("F5");
                    case GLFW.GLFW_KEY_F6 -> sb.append("F6");
                    case GLFW.GLFW_KEY_F7 -> sb.append("F7");
                    case GLFW.GLFW_KEY_F8 -> sb.append("F8");
                    case GLFW.GLFW_KEY_F9 -> sb.append("F9");
                    case GLFW.GLFW_KEY_F10 -> sb.append("F10");
                    case GLFW.GLFW_KEY_F11 -> sb.append("F11");
                    case GLFW.GLFW_KEY_F12 -> sb.append("F12");
                    case GLFW.GLFW_KEY_TAB -> sb.append("TAB");
                    case GLFW.GLFW_KEY_SPACE -> sb.append("SPACE");
                    case GLFW.GLFW_KEY_RIGHT_SHIFT -> sb.append("RSHIFT");
                    case GLFW.GLFW_KEY_LEFT_SHIFT -> sb.append("LSHIFT");
                    case GLFW.GLFW_KEY_RIGHT_CONTROL -> sb.append("RCTRL");
                    case GLFW.GLFW_KEY_LEFT_CONTROL -> sb.append("LCTRL");
                    case GLFW.GLFW_KEY_RIGHT_ALT -> sb.append("RALT");
                    case GLFW.GLFW_KEY_LEFT_ALT -> sb.append("LALT");
                    default -> sb.append(InputUtil.Type.KEYSYM.createFromCode(key).getLocalizedText().getString().toUpperCase(Locale.ROOT));
                }
            }
        }
        return sb.toString();
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        if (BameClientConfig.frostedBlur && !BameClientConfig.threeDGui) {
            applyBlur(context);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        geometry();
        float progress = Math.min(1.0f, (System.currentTimeMillis() - openTime) / 300.0f);
        float p = 1.0f - (float) Math.pow(1.0f - progress, 4);

        ThreeDGuiManager.setupFrame(px, py, pw, ph, width, height, mouseX, mouseY, delta);
        double[] localM = ThreeDGuiManager.screenToLocal(mouseX, mouseY);
        int lmx = (int) Math.round(localM[0]);
        int lmy = (int) Math.round(localM[1]);

        int dimAlpha;
        if (BameClientConfig.threeDGui) {
            dimAlpha = (int) ((BameClientConfig.seeThrough ? 0x30 : 0x48) * p);
        } else {
            dimAlpha = (int) ((BameClientConfig.seeThrough ? 0x44 : 0x78) * p);
        }
        if (dimAlpha > 0) {
            context.fill(0, 0, width, height, (dimAlpha << 24) | 0x000000);
        }

        if (BameClientConfig.threeDGui) {
            renderLeftSkin(context, mouseX, mouseY, delta, p);
            ThreeDGuiManager.apply3D(context);
        }

        mouseX = lmx;
        mouseY = lmy;



        // Window background
        int bg = BameClientConfig.seeThrough ? 0xD810121A : 0xF710121A;
        CustomGuiUtils.fillUltraRounded(context, px, py, pw, ph, bg, 10.0f);

        // Custom Wallpaper
        if (BameClientConfig.customWallpaper) {
            WallpaperManager.render(context, px, py, pw, ph, p);
        }

        // Window outline
        CustomGuiUtils.drawUltraRoundedOutline(context, px, py, pw, ph, 0xFF242834, 10.0f);
        CustomGuiUtils.drawUltraRoundedOutline(context, px + 1, py + 1, pw - 2, ph - 2, 0x1AFFFFFF, 9.0f);
        if (BameClientConfig.threeDGui) {
            context.fill(px + 12, py, px + pw - 12, py + 1, 0x30FFFFFF);
            context.fill(px + pw / 4, py, px + (pw * 3) / 4, py + 1, GuiTheme.alpha(GuiTheme.accent(), 85));
        }

        // Ambient lighting
        if (!BameClientConfig.threeDGui && BameClientConfig.ambientBackground) {
            ambient.render(context, px + 1, py + 1, pw - 2, ph - 2);
        }

        // Header
        CustomGuiUtils.drawCLogo(context, px + 14, py + 14, 20);
        text(context, "CAESER SECRET", px + 40, py + 20, 0xFFFFFFFF);

        String badge = "SECRET MENU";
        int badgeW = textRenderer.getWidth(CustomGuiUtils.getFontText(badge)) + 12;
        CustomGuiUtils.fillUltraRounded(context, px + 142, py + 16, badgeW, 16, GuiTheme.alpha(GuiTheme.accent(), 40), 4);
        CustomGuiUtils.drawUltraRoundedOutline(context, px + 142, py + 16, badgeW, 16, GuiTheme.accent(), 4);
        text(context, badge, px + 148, py + 20, GuiTheme.accent());

        // Quick switch button for secret bind right next to [ SECRET MENU ]
        int bindBtnX = px + 142 + badgeW + 8;
        String comboStr = getComboString();
        String bindLabel = listeningCombo ? "> Tasten drücken... <" : ("Bind: " + comboStr);
        int bindBtnW = textRenderer.getWidth(CustomGuiUtils.getFontText(bindLabel)) + 14;
        int bindBtnH = 16;
        int bindBtnY = py + 16;
        modeButton(context, bindLabel, bindBtnX, bindBtnY, bindBtnW, bindBtnH, mouseX, mouseY, listeningCombo);

        // Search bar replacing "Zurück" button
        int searchW = 120;
        int searchX = px + pw - searchW - 14;
        int searchY = py + 15;
        if (searchField != null) {
            searchField.setX(searchX);
            searchField.setY(searchY);
            searchField.setWidth(searchW);
            searchField.setHeight(18);
            searchField.visible = true;
            searchField.active = true;
            searchField.render(context, mouseX, mouseY, delta);
        }

        // Header separator
        context.fill(px + 10, py + 42, px + pw - 10, py + 43, 0xFF222632);

        // Viewport dimensions
        int cx = px + 14;
        int cy = py + 48;
        int cw = pw - 28;
        int ch = ph - 56;

        scroll = Math.clamp(scroll, 0, maxScroll(ch));
        int baseY = cy - (int) scroll;

        int gap = 14;
        int totalCardsW = cw - (maxScroll(ch) > 0 ? 8 : 0);
        int cardW = (totalCardsW - gap) / 2;
        int card1X = cx;
        int card2X = card1X + cardW + gap;

        // Content clipping
        boolean needScissor = maxScroll(ch) > 0;
        if (needScissor) {
            context.enableScissor(cx - 4, py + 44, cx + cw + 8, py + ph - 8);
        }

        int leftY = baseY;

        // ==================== CARD 1: Pearl Prediction ====================
        if (pearlVisible()) {
            int c1H = getPearlHeight();
            card(context, card1X, leftY, cardW, c1H, PearlPredictionModule.enabled);

            text(context, "Pearl Prediction", card1X + 12, leftY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card1X + 12, leftY + 29, 0xFF8E95A4);

            keybindButton(context, "PearlPredictionModule", PearlPredictionModule.keyBind, listeningPearlKey, card1X + 60, leftY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card1X + cardW - 38, leftY + 12, PearlPredictionModule.enabled, mouseX, mouseY, delta);

            if (PearlPredictionModule.expanded) {
                context.fill(card1X + 8, leftY + 46, card1X + cardW - 8, leftY + 47, 0xFF292D36);

                int curY = leftY + 54;
                // Row 1: Enemy Only
                text(context, "Enemy Only", card1X + 14, curY + 4, 0xFFD4D8E0);
                toggle(context, card1X + cardW - 38, curY + 2, PearlPredictionModule.enemyOnly, mouseX, mouseY, delta);
                curY += 26;

                // Row 2: Landing Box
                text(context, "Landing Box", card1X + 14, curY + 4, 0xFFD4D8E0);
                toggle(context, card1X + cardW - 38, curY + 2, PearlPredictionModule.landingBox, mouseX, mouseY, delta);
                curY += 26;

                // Row 3: Throw Preview
                text(context, "Throw Preview", card1X + 14, curY + 4, 0xFFD4D8E0);
                toggle(context, card1X + cardW - 38, curY + 2, PearlPredictionModule.throwPreview, mouseX, mouseY, delta);
                curY += 26;

                // Row 4: Reset Button
                button(context, "Reset", card1X + cardW - 58, curY, 46, 16, mouseX, mouseY);
            }
            leftY += c1H + 12;
        }

        // ==================== CARD 2: AutoCart ====================
        if (autoCartVisible()) {
            int acH = getAutoCartHeight();
            card(context, card1X, leftY, cardW, acH, AutoCartModule.enabled);

            text(context, "Auto Cart", card1X + 12, leftY + 12, 0xFFE2E5ED);

            text(context, "KeyBind:", card1X + 12, leftY + 29, 0xFF8E95A4);

            keybindButton(context, "AutoCartModule", AutoCartModule.keyBind, listeningCartKey, card1X + 60, leftY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card1X + cardW - 38, leftY + 12, AutoCartModule.enabled, mouseX, mouseY, delta);

            if (AutoCartModule.expanded) {
                context.fill(card1X + 8, leftY + 46, card1X + cardW - 8, leftY + 47, 0xFF292D36);

                int rowY = leftY + 54;
                text(context, "Modus", card1X + 14, rowY + 5, 0xFFD4D8E0);

                int btnW = 74;
                int btnH = 18;
                int btn2X = card1X + cardW - btnW - 14;
                int btn1X = btn2X - btnW - 6;

                modeButton(context, "Slot Switch", btn1X, rowY, btnW, btnH, mouseX, mouseY, AutoCartModule.mode == AutoCartModule.CartMode.SLOT_SWITCH);
                modeButton(context, "Automatisch", btn2X, rowY, btnW, btnH, mouseX, mouseY, AutoCartModule.mode == AutoCartModule.CartMode.AUTOMATIC);

                rowY += 26;
                text(context, "Switch Back", card1X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card1X + cardW - 38, rowY + 2, AutoCartModule.switchBack, mouseX, mouseY, delta);

                rowY += 26;
                text(context, "Switch Delay", card1X + 14, rowY + 4, 0xFFD4D8E0);
                String delayLabel = AutoCartModule.switchDelay + (AutoCartModule.switchDelay == 1 ? " Tick" : " Ticks");
                button(context, delayLabel, card1X + cardW - 64, rowY, 52, 16, mouseX, mouseY);

                rowY += 26;
                button(context, "Reset", card1X + cardW - 58, rowY, 46, 16, mouseX, mouseY);
            }
            leftY += acH + 12;
        }

        // ==================== CARD 3: Auto Mace ====================
        if (autoMaceVisible()) {
            int amH = getAutoMaceHeight();
            card(context, card1X, leftY, cardW, amH, AutoMaceModule.enabled);

            text(context, "Auto Mace", card1X + 12, leftY + 12, 0xFFE2E5ED);

            text(context, "KeyBind:", card1X + 12, leftY + 29, 0xFF8E95A4);

            keybindButton(context, "AutoMaceModule", AutoMaceModule.keyBind, listeningMaceKey, card1X + 60, leftY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card1X + cardW - 38, leftY + 12, AutoMaceModule.enabled, mouseX, mouseY, delta);

            if (AutoMaceModule.expanded) {
                context.fill(card1X + 8, leftY + 46, card1X + cardW - 8, leftY + 47, 0xFF292D36);

                int rowY = leftY + 54;
                text(context, "Modus", card1X + 14, rowY + 5, 0xFFD4D8E0);

                int btnW = 74;
                int btnH = 18;
                int btn2X = card1X + cardW - btnW - 14;
                int btn1X = btn2X - btnW - 6;

                modeButton(context, "Automatisch", btn1X, rowY, btnW, btnH, mouseX, mouseY, AutoMaceModule.mode == AutoMaceModule.MaceMode.AUTOMATIC);
                modeButton(context, "Bei Klick", btn2X, rowY, btnW, btnH, mouseX, mouseY, AutoMaceModule.mode == AutoMaceModule.MaceMode.ON_CLICK);

                rowY += 26;
                text(context, "Min. Höhe: " + AutoMaceModule.getHeightLabel(), card1X + 14, rowY + 4, 0xFFD4D8E0);
                int sx = card1X + cardW - 100 - 14;
                int sw = 100;
                CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, sw, 4, 0xFF303442, 2);
                float hNorm = (float) ((AutoMaceModule.minFallDistance - 1.0) / 9.0);
                int fill = Math.round(sw * Math.clamp(hNorm, 0f, 1f));
                if (fill > 0) CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, fill, 4, GuiTheme.accent(), 2);
                CustomGuiUtils.fillUltraRounded(context, sx + fill - 3, rowY + 4, 7, 8, 0xFFFFFFFF, 4);

                rowY += 26;
                text(context, "Switch Delay", card1X + 14, rowY + 4, 0xFFD4D8E0);
                String delayLabel = AutoMaceModule.switchDelay + (AutoMaceModule.switchDelay == 1 ? " Tick" : " Ticks");
                button(context, delayLabel, card1X + cardW - 64, rowY, 52, 16, mouseX, mouseY);

                rowY += 26;
                text(context, "Switch Back", card1X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card1X + cardW - 38, rowY + 2, AutoMaceModule.switchBack, mouseX, mouseY, delta);

                rowY += 26;
                text(context, "Cooldown Check", card1X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card1X + cardW - 38, rowY + 2, AutoMaceModule.cooldownCheck, mouseX, mouseY, delta);

                rowY += 26;
                text(context, "Nur Spieler", card1X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card1X + cardW - 38, rowY + 2, AutoMaceModule.onlyPlayers, mouseX, mouseY, delta);

                rowY += 26;
                button(context, "Reset", card1X + cardW - 58, rowY, 46, 16, mouseX, mouseY);
            }
            leftY += amH + 12;
        }

        // ==================== CARD 4: D-Tap ====================
        if (dTapVisible()) {
            int dtH = getDTapHeight();
            card(context, card1X, leftY, cardW, dtH, DTapModule.enabled);

            text(context, "D-Tap", card1X + 12, leftY + 12, 0xFFE2E5ED);

            text(context, "KeyBind:", card1X + 12, leftY + 29, 0xFF8E95A4);

            keybindButton(context, "DTapModule", DTapModule.keyBind, listeningDTapKey, card1X + 60, leftY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card1X + cardW - 38, leftY + 12, DTapModule.enabled, mouseX, mouseY, delta);

            if (DTapModule.expanded) {
                context.fill(card1X + 8, leftY + 46, card1X + cardW - 8, leftY + 47, 0xFF292D36);

                int rowY = leftY + 54;
                text(context, "Modus", card1X + 14, rowY + 5, 0xFFD4D8E0);

                int btnW = 54;
                int btnH = 18;
                int btn3X = card1X + cardW - btnW - 14;
                int btn2X = btn3X - btnW - 4;
                int btn1X = btn2X - btnW - 4;

                modeButton(context, "Taste", btn1X, rowY, btnW, btnH, mouseX, mouseY, DTapModule.mode == DTapModule.DTapMode.KEYBIND);
                modeButton(context, "Klick", btn2X, rowY, btnW, btnH, mouseX, mouseY, DTapModule.mode == DTapModule.DTapMode.ON_CLICK);
                modeButton(context, "Auto", btn3X, rowY, btnW, btnH, mouseX, mouseY, DTapModule.mode == DTapModule.DTapMode.AUTOMATIC);

                rowY += 26;
                text(context, "Tap Delay: " + DTapModule.tapDelay + (DTapModule.tapDelay == 1 ? " Tick" : " Ticks"), card1X + 14, rowY + 4, 0xFFD4D8E0);
                int sx = card1X + cardW - 100 - 14;
                int sw = 100;
                CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, sw, 4, 0xFF303442, 2);
                float tNorm = (float) ((DTapModule.tapDelay - 1) / 9.0);
                int fill = Math.round(sw * Math.clamp(tNorm, 0f, 1f));
                if (fill > 0) CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, fill, 4, GuiTheme.accent(), 2);
                CustomGuiUtils.fillUltraRounded(context, sx + fill - 3, rowY + 4, 7, 8, 0xFFFFFFFF, 4);

                rowY += 26;
                text(context, "Switch Back", card1X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card1X + cardW - 38, rowY + 2, DTapModule.switchBack, mouseX, mouseY, delta);

                rowY += 26;
                button(context, "Reset", card1X + cardW - 58, rowY, 46, 16, mouseX, mouseY);
            }
            leftY += dtH + 12;
        }

        // ==================== CARD 5: Triggerbot ====================
        if (triggerbotVisible()) {
            int tbH = getTriggerbotHeight();
            card(context, card1X, leftY, cardW, tbH, TriggerbotModule.enabled);

            text(context, "Triggerbot", card1X + 12, leftY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card1X + 12, leftY + 29, 0xFF8E95A4);

            keybindButton(context, "TriggerbotModule", TriggerbotModule.keyBind, listeningTriggerbotKey, card1X + 60, leftY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card1X + cardW - 38, leftY + 12, TriggerbotModule.enabled, mouseX, mouseY, delta);

            if (TriggerbotModule.expanded) {
                context.fill(card1X + 8, leftY + 46, card1X + cardW - 8, leftY + 47, 0xFF292D36);

                // --- 1-Klick Profile ---
                int rowY = leftY + 52;
                text(context, "Profile (1-Klick)", card1X + 14, rowY + 1, 0xFF8E95A4);

                int btnW = (cardW - 28 - 6) / 2;
                int btnH = 17;
                int b1X = card1X + 14;
                int b2X = b1X + btnW + 6;

                int activePreset = TriggerbotModule.getActivePreset();
                rowY += 14;
                modeButton(context, "1.9+ PvP", b1X, rowY, btnW, btnH, mouseX, mouseY, activePreset == 0);
                modeButton(context, "Legit (LMB)", b2X, rowY, btnW, btnH, mouseX, mouseY, activePreset == 1);

                rowY += 21;
                modeButton(context, "1.8 CPS", b1X, rowY, btnW, btnH, mouseX, mouseY, activePreset == 2);
                modeButton(context, "Test-Modus", b2X, rowY, btnW, btnH, mouseX, mouseY, activePreset == 3);

                // --- Kern-Schalter: Auslöser ---
                rowY += 23;
                text(context, "Auslöser", card1X + 14, rowY + 4, 0xFFD4D8E0);
                int optW = 76;
                int optH = 16;
                int opt2X = card1X + cardW - optW - 14;
                int opt1X = opt2X - optW - 4;
                modeButton(context, "Auto", opt1X, rowY, optW, optH, mouseX, mouseY, !TriggerbotModule.holdAttackKey);
                modeButton(context, "Halten (LMB)", opt2X, rowY, optW, optH, mouseX, mouseY, TriggerbotModule.holdAttackKey);

                // --- Kern-Schalter: Ziel ---
                rowY += 22;
                text(context, "Ziel", card1X + 14, rowY + 4, 0xFFD4D8E0);
                modeButton(context, "Spieler", opt1X, rowY, optW, optH, mouseX, mouseY, TriggerbotModule.playersOnly);
                modeButton(context, "Alle (Mobs)", opt2X, rowY, optW, optH, mouseX, mouseY, !TriggerbotModule.playersOnly);

                // --- Reichweite Slider ---
                rowY += 22;
                String rLabel = String.format(java.util.Locale.ROOT, "%.1f", TriggerbotModule.range);
                text(context, "Reichweite: " + rLabel, card1X + 14, rowY + 4, 0xFFD4D8E0);
                int sx = card1X + cardW - 100 - 14;
                int sw = 100;
                CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, sw, 4, 0xFF303442, 2);
                float rNorm = (float) ((TriggerbotModule.range - 2.5) / 2.0);
                int fill = Math.round(sw * Math.clamp(rNorm, 0f, 1f));
                if (fill > 0) CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, fill, 4, GuiTheme.accent(), 2);
                CustomGuiUtils.fillUltraRounded(context, sx + fill - 3, rowY + 4, 7, 8, 0xFFFFFFFF, 4);

                // --- Details Button ---
                rowY += 22;
                String detLabel = TriggerbotModule.showDetails ? "Weniger Optionen ▲" : "Feintuning / Details ▼";
                button(context, detLabel, card1X + 14, rowY, cardW - 28, 16, mouseX, mouseY);

                // --- Details Content ---
                if (TriggerbotModule.showDetails) {
                    rowY += 22;
                    text(context, "Modus", card1X + 14, rowY + 4, 0xFFD4D8E0);
                    modeButton(context, "Cooldown", opt1X, rowY, optW, optH, mouseX, mouseY, TriggerbotModule.mode == 0);
                    modeButton(context, "CPS", opt2X, rowY, optW, optH, mouseX, mouseY, TriggerbotModule.mode == 1);

                    rowY += 22;
                    if (TriggerbotModule.mode == 0) {
                        text(context, "Charge: " + Math.round(TriggerbotModule.cooldownThreshold * 100) + "%", card1X + 14, rowY + 4, 0xFFD4D8E0);
                        CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, sw, 4, 0xFF303442, 2);
                        float cNorm = (TriggerbotModule.cooldownThreshold - 0.8f) / 0.2f;
                        int cFill = Math.round(sw * Math.clamp(cNorm, 0f, 1f));
                        if (cFill > 0) CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, cFill, 4, GuiTheme.accent(), 2);
                        CustomGuiUtils.fillUltraRounded(context, sx + cFill - 3, rowY + 4, 7, 8, 0xFFFFFFFF, 4);
                    } else {
                        text(context, "CPS: " + TriggerbotModule.cps, card1X + 14, rowY + 4, 0xFFD4D8E0);
                        CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, sw, 4, 0xFF303442, 2);
                        float cNorm = (TriggerbotModule.cps - 1) / 19.0f;
                        int cFill = Math.round(sw * Math.clamp(cNorm, 0f, 1f));
                        if (cFill > 0) CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, cFill, 4, GuiTheme.accent(), 2);
                        CustomGuiUtils.fillUltraRounded(context, sx + cFill - 3, rowY + 4, 7, 8, 0xFFFFFFFF, 4);
                    }

                    rowY += 22;
                    text(context, "Nur mit Waffen", card1X + 14, rowY + 4, 0xFFD4D8E0);
                    toggle(context, card1X + cardW - 38, rowY + 2, TriggerbotModule.weaponOnly, mouseX, mouseY, delta);

                    rowY += 22;
                    text(context, "Human Jitter", card1X + 14, rowY + 4, 0xFFD4D8E0);
                    toggle(context, card1X + cardW - 38, rowY + 2, TriggerbotModule.randomJitter, mouseX, mouseY, delta);

                    rowY += 22;
                    button(context, "Reset", card1X + cardW - 58, rowY, 46, 16, mouseX, mouseY);
                }
            }
            leftY += tbH + 12;
        }

        int rightY = baseY;

        // ==================== CARD: Pearl Catch ====================
        if (pearlCatchVisible()) {
            int pcH = getPearlCatchHeight();
            card(context, card2X, rightY, cardW, pcH, PearlCatchModule.enabled);

            text(context, "Pearl Catch", card2X + 12, rightY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card2X + 12, rightY + 29, 0xFF8E95A4);

            keybindButton(context, "PearlCatchModule", PearlCatchModule.keyBind, listeningCatchKey, card2X + 60, rightY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card2X + cardW - 38, rightY + 12, PearlCatchModule.enabled, mouseX, mouseY, delta);

            if (PearlCatchModule.expanded) {
                context.fill(card2X + 8, rightY + 46, card2X + cardW - 8, rightY + 47, 0xFF292D36);

                int rowY = rightY + 54;
                text(context, "Delay: " + PearlCatchModule.timingDelay + (PearlCatchModule.timingDelay == 1 ? " Tick" : " Ticks"), card2X + 14, rowY + 4, 0xFFD4D8E0);
                int sx = card2X + cardW - 100 - 14;
                int sw = 100;
                CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, sw, 4, 0xFF303442, 2);
                float tNorm = (float) ((PearlCatchModule.timingDelay - 1) / 4.0);
                int fill = Math.round(sw * Math.clamp(tNorm, 0f, 1f));
                if (fill > 0) CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, fill, 4, GuiTheme.accent(), 2);
                CustomGuiUtils.fillUltraRounded(context, sx + fill - 3, rowY + 4, 7, 8, 0xFFFFFFFF, 4);

                rowY += 26;
                text(context, "Auto Aim (Himmel)", card2X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card2X + cardW - 38, rowY + 2, PearlCatchModule.autoAim, mouseX, mouseY, delta);

                rowY += 26;
                text(context, "Switch to Mace", card2X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card2X + cardW - 38, rowY + 2, PearlCatchModule.switchToMace, mouseX, mouseY, delta);

                rowY += 26;
                text(context, "Switch Back", card2X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card2X + cardW - 38, rowY + 2, PearlCatchModule.switchBack, mouseX, mouseY, delta);

                rowY += 26;
                button(context, "Reset", card2X + cardW - 58, rowY, 46, 16, mouseX, mouseY);
            }
            rightY += pcH + 12;
        }

        // ==================== CARD: Aggro Pearl ====================
        if (aggroPearlVisible()) {
            int apH = getAggroPearlHeight();
            card(context, card2X, rightY, cardW, apH, AggroPearlModule.enabled);

            text(context, "Aggro Pearl", card2X + 12, rightY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card2X + 12, rightY + 29, 0xFF8E95A4);

            keybindButton(context, "AggroPearlModule", AggroPearlModule.keyBind, listeningAggroKey, card2X + 60, rightY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card2X + cardW - 38, rightY + 12, AggroPearlModule.enabled, mouseX, mouseY, delta);

            if (AggroPearlModule.expanded) {
                context.fill(card2X + 8, rightY + 46, card2X + cardW - 8, rightY + 47, 0xFF292D36);

                int rowY = rightY + 54;
                text(context, "Throw Delay: " + AggroPearlModule.throwDelay + (AggroPearlModule.throwDelay == 1 ? " Tick" : " Ticks"), card2X + 14, rowY + 4, 0xFFD4D8E0);
                int sx = card2X + cardW - 100 - 14;
                int sw = 100;
                CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, sw, 4, 0xFF303442, 2);
                float tNorm = (float) (AggroPearlModule.throwDelay / 5.0);
                int fill = Math.round(sw * Math.clamp(tNorm, 0f, 1f));
                if (fill > 0) CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, fill, 4, GuiTheme.accent(), 2);
                CustomGuiUtils.fillUltraRounded(context, sx + fill - 3, rowY + 4, 7, 8, 0xFFFFFFFF, 4);

                rowY += 26;
                text(context, "Auto Aim", card2X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card2X + cardW - 38, rowY + 2, AggroPearlModule.autoAim, mouseX, mouseY, delta);

                rowY += 26;
                text(context, "Switch Back", card2X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card2X + cardW - 38, rowY + 2, AggroPearlModule.switchBack, mouseX, mouseY, delta);

                rowY += 26;
                button(context, "Reset", card2X + cardW - 58, rowY, 46, 16, mouseX, mouseY);
            }
            rightY += apH + 12;
        }

        // ==================== CARD 4: Name Protect ====================
        if (nameProtectVisible()) {
            int c2H = getNameProtectHeight();
            card(context, card2X, rightY, cardW, c2H, NameProtectModule.enabled);

            text(context, "Name Protect", card2X + 12, rightY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card2X + 12, rightY + 29, 0xFF8E95A4);

            keybindButton(context, "NameProtectModule", NameProtectModule.keyBind, listeningNameKey, card2X + 60, rightY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card2X + cardW - 38, rightY + 12, NameProtectModule.enabled, mouseX, mouseY, delta);

            if (NameProtectModule.expanded) {
                context.fill(card2X + 8, rightY + 46, card2X + cardW - 8, rightY + 47, 0xFF292D36);

                int curY = rightY + 54;
                text(context, "Alias:", card2X + 14, curY + 4, 0xFFD4D8E0);
                int aliasFieldX = card2X + 54;
                int aliasFieldW = cardW - 54 - 14;
                if (nameProtectAliasField != null) {
                    nameProtectAliasField.setX(aliasFieldX);
                    nameProtectAliasField.setY(curY);
                    nameProtectAliasField.setWidth(aliasFieldW);
                    nameProtectAliasField.setHeight(18);
                    nameProtectAliasField.visible = true;
                    nameProtectAliasField.active = curY + 18 > cy && curY < cy + ch;
                    nameProtectAliasField.render(context, mouseX, mouseY, delta);
                }
                curY += 26;

                button(context, "Reset", card2X + cardW - 58, curY, 46, 16, mouseX, mouseY);
            } else {
                if (nameProtectAliasField != null) nameProtectAliasField.visible = nameProtectAliasField.active = false;
            }
            rightY += c2H + 12;
        } else {
            if (nameProtectAliasField != null) nameProtectAliasField.visible = nameProtectAliasField.active = false;
        }

        // ==================== CARD 4: Fake Scoreboard ====================
        if (fakeScoreboardVisible()) {
            int c3H = getFakeScoreboardHeight();
            card(context, card2X, rightY, cardW, c3H, FakeScoreboardModule.enabled);

            text(context, "Fake Scoreboard", card2X + 12, rightY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card2X + 12, rightY + 29, 0xFF8E95A4);

            keybindButton(context, "FakeScoreboardModule", FakeScoreboardModule.keyBind, listeningFakeKey, card2X + 60, rightY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card2X + cardW - 38, rightY + 12, FakeScoreboardModule.enabled, mouseX, mouseY, delta);

            int labelW = 44;
            int fieldW = cardW - labelW - 28;
            if (FakeScoreboardModule.expanded) {
                context.fill(card2X + 8, rightY + 46, card2X + cardW - 8, rightY + 47, 0xFF292D36);

                int fieldX = card2X + 14 + labelW;
                int curY = rightY + 54;

                text(context, "Money:", card2X + 14, curY + 4, 0xFFD4D8E0);
                if (fakeMoneyField != null) {
                    fakeMoneyField.setX(fieldX);
                    fakeMoneyField.setY(curY);
                    fakeMoneyField.setWidth(fieldW);
                    fakeMoneyField.visible = true;
                    fakeMoneyField.active = curY + 18 > cy && curY < cy + ch;
                    fakeMoneyField.render(context, mouseX, mouseY, delta);
                }
                curY += 24;

                text(context, "Stars:", card2X + 14, curY + 4, 0xFFD4D8E0);
                if (fakeStarsField != null) {
                    fakeStarsField.setX(fieldX);
                    fakeStarsField.setY(curY);
                    fakeStarsField.setWidth(fieldW);
                    fakeStarsField.visible = true;
                    fakeStarsField.active = curY + 18 > cy && curY < cy + ch;
                    fakeStarsField.render(context, mouseX, mouseY, delta);
                }
                curY += 24;

                text(context, "Kills:", card2X + 14, curY + 4, 0xFFD4D8E0);
                if (fakeKillsField != null) {
                    fakeKillsField.setX(fieldX);
                    fakeKillsField.setY(curY);
                    fakeKillsField.setWidth(fieldW);
                    fakeKillsField.visible = true;
                    fakeKillsField.active = curY + 18 > cy && curY < cy + ch;
                    fakeKillsField.render(context, mouseX, mouseY, delta);
                }
                curY += 24;

                text(context, "Deaths:", card2X + 14, curY + 4, 0xFFD4D8E0);
                if (fakeDeathsField != null) {
                    fakeDeathsField.setX(fieldX);
                    fakeDeathsField.setY(curY);
                    fakeDeathsField.setWidth(fieldW);
                    fakeDeathsField.visible = true;
                    fakeDeathsField.active = curY + 18 > cy && curY < cy + ch;
                    fakeDeathsField.render(context, mouseX, mouseY, delta);
                }
                curY += 24;

                text(context, "Time:", card2X + 14, curY + 4, 0xFFD4D8E0);
                if (fakeTimeField != null) {
                    fakeTimeField.setX(fieldX);
                    fakeTimeField.setY(curY);
                    fakeTimeField.setWidth(fieldW);
                    fakeTimeField.visible = true;
                    fakeTimeField.active = curY + 18 > cy && curY < cy + ch;
                    fakeTimeField.render(context, mouseX, mouseY, delta);
                }
                curY += 26;

                button(context, "Edit HUD", card2X + 14, curY, 52, 16, mouseX, mouseY);
                button(context, "Reset", card2X + cardW - 58, curY, 46, 16, mouseX, mouseY);
            } else {
                if (fakeMoneyField != null) fakeMoneyField.visible = fakeMoneyField.active = false;
                if (fakeStarsField != null) fakeStarsField.visible = fakeStarsField.active = false;
                if (fakeKillsField != null) fakeKillsField.visible = fakeKillsField.active = false;
                if (fakeDeathsField != null) fakeDeathsField.visible = fakeDeathsField.active = false;
                if (fakeTimeField != null) fakeTimeField.visible = fakeTimeField.active = false;
            }
            rightY += c3H + 12;
        } else {
            if (fakeMoneyField != null) fakeMoneyField.visible = fakeMoneyField.active = false;
            if (fakeStarsField != null) fakeStarsField.visible = fakeStarsField.active = false;
            if (fakeKillsField != null) fakeKillsField.visible = fakeKillsField.active = false;
            if (fakeDeathsField != null) fakeDeathsField.visible = fakeDeathsField.active = false;
            if (fakeTimeField != null) fakeTimeField.visible = fakeTimeField.active = false;
        }

        // ==================== CARD 5: SkinProtect ====================
        if (skinProtectVisible()) {
            int c4H = getSkinProtectHeight();
            card(context, card2X, rightY, cardW, c4H, SkinProtectModule.enabled);

            text(context, "Skin Protect", card2X + 12, rightY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card2X + 12, rightY + 29, 0xFF8E95A4);

            keybindButton(context, "SkinProtectModule", SkinProtectModule.keyBind, listeningSkinKey, card2X + 60, rightY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card2X + cardW - 38, rightY + 12, SkinProtectModule.enabled, mouseX, mouseY, delta);

            if (SkinProtectModule.expanded) {
                context.fill(card2X + 8, rightY + 46, card2X + cardW - 8, rightY + 47, 0xFF292D36);

                // Preview box
                int prevPad = 12;
                int prevX = card2X + prevPad;
                int prevY = rightY + 52;
                int prevW = cardW - prevPad * 2;
                int prevH = 135;
                box(context, prevX, prevY, prevW, prevH, 0xFF0E1117);
                CustomGuiUtils.drawUltraRoundedOutline(context, prevX, prevY, prevW, prevH, 0xFF232733);

                if (skinPreviewWidget == null && client != null && client.getLoadedEntityModels() != null) {
                    skinPreviewWidget = new PlayerSkinWidget(prevW, prevH, client.getLoadedEntityModels(), SkinProtectModule::getCurrentSkin);
                }
                if (skinPreviewWidget != null) {
                    skinPreviewWidget.setDimensionsAndPosition(prevW, prevH, prevX, prevY);
                    context.enableScissor(Math.max(cx, prevX), Math.max(cy, prevY), Math.min(cx + cw, prevX + prevW), Math.min(cy + ch, prevY + prevH));
                    skinPreviewWidget.render(context, mouseX, mouseY, delta);
                    context.disableScissor();
                }

                int curY = prevY + prevH + 9;

                // Row 1: Search player name field + Set button
                int pad = 12;
                int searchX2 = card2X + pad;
                int btnW = 38;
                int sFieldW = (cardW - pad * 2) - btnW - 6;
                if (skinSearchWidget != null) {
                    skinSearchWidget.setX(searchX2);
                    skinSearchWidget.setY(curY);
                    skinSearchWidget.setWidth(sFieldW);
                    skinSearchWidget.setHeight(18);
                    skinSearchWidget.visible = true;
                    skinSearchWidget.active = curY + 18 > cy && curY < cy + ch;
                    skinSearchWidget.render(context, mouseX, mouseY, delta);
                }
                button(context, "Set", searchX2 + sFieldW + 6, curY, btnW, 18, mouseX, mouseY);
                curY += 24;

                // Row 2: Skin name label + Shuffle button
                int maxTextW = cardW - 28 - 62;
                context.enableScissor(Math.max(cx, card2X + 14), Math.max(cy, curY), Math.min(cx + cw, card2X + 14 + maxTextW), Math.min(cy + ch, curY + 20));
                text(context, "Skin: " + SkinProtectModule.getCurrentSkinName(), card2X + 14, curY + 4, 0xFFD4D8E0);
                context.disableScissor();

                button(context, "Shuffle", card2X + cardW - 70, curY, 56, 16, mouseX, mouseY);
                curY += 26;

                // Row 3: Reset button
                button(context, "Reset", card2X + cardW - 58, curY, 46, 16, mouseX, mouseY);
            } else {
                if (skinSearchWidget != null) skinSearchWidget.visible = skinSearchWidget.active = false;
            }
            rightY += c4H + 12;
        } else {
            if (skinSearchWidget != null) skinSearchWidget.visible = skinSearchWidget.active = false;
        }

        // ==================== CARD 6: Shield Breaker ====================
        if (shieldBreakerVisible()) {
            int sbH = getShieldBreakerHeight();
            card(context, card2X, rightY, cardW, sbH, ShieldBreakerModule.enabled);

            text(context, "Shield Breaker", card2X + 12, rightY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card2X + 12, rightY + 29, 0xFF8E95A4);

            keybindButton(context, "ShieldBreakerModule", ShieldBreakerModule.keyBind, listeningShieldBreakerKey, card2X + 60, rightY + 25, 48, 16, mouseX, mouseY, cardW);
            toggle(context, card2X + cardW - 38, rightY + 12, ShieldBreakerModule.enabled, mouseX, mouseY, delta);

            if (ShieldBreakerModule.expanded) {
                context.fill(card2X + 8, rightY + 46, card2X + cardW - 8, rightY + 47, 0xFF292D36);

                int rowY = rightY + 54;
                text(context, "Modus", card2X + 14, rowY + 5, 0xFFD4D8E0);

                int btnW = 74;
                int btnH = 18;
                int btn2X = card2X + cardW - btnW - 14;
                int btn1X = btn2X - btnW - 6;

                modeButton(context, "Automatisch", btn1X, rowY, btnW, btnH, mouseX, mouseY, ShieldBreakerModule.mode == 0);
                modeButton(context, "Bei Klick", btn2X, rowY, btnW, btnH, mouseX, mouseY, ShieldBreakerModule.mode == 1);

                rowY += 26;
                String rLabel = String.format(java.util.Locale.ROOT, "%.1f Blöcke", ShieldBreakerModule.reach);
                text(context, "Reichweite: " + rLabel, card2X + 14, rowY + 4, 0xFFD4D8E0);
                int sx = card2X + cardW - 100 - 14;
                int sw = 100;
                CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, sw, 4, 0xFF303442, 2);
                float rNorm = (float) ((ShieldBreakerModule.reach - 2.5) / 2.0);
                int fill = Math.round(sw * Math.clamp(rNorm, 0f, 1f));
                if (fill > 0) CustomGuiUtils.fillUltraRounded(context, sx, rowY + 6, fill, 4, GuiTheme.accent(), 2);
                CustomGuiUtils.fillUltraRounded(context, sx + fill - 3, rowY + 4, 7, 8, 0xFFFFFFFF, 4);

                rowY += 26;
                text(context, "Switch Delay", card2X + 14, rowY + 4, 0xFFD4D8E0);
                String delayLabel = ShieldBreakerModule.switchDelay + (ShieldBreakerModule.switchDelay == 1 ? " Tick" : " Ticks");
                button(context, delayLabel, card2X + cardW - 64, rowY, 52, 16, mouseX, mouseY);

                rowY += 26;
                text(context, "Switch Back", card2X + 14, rowY + 4, 0xFFD4D8E0);
                toggle(context, card2X + cardW - 38, rowY + 2, ShieldBreakerModule.switchBack, mouseX, mouseY, delta);

                rowY += 26;
                button(context, "Reset", card2X + cardW - 58, rowY, 46, 16, mouseX, mouseY);
            }
            rightY += sbH + 12;
        }

        // Close scissor
        if (needScissor) {
            context.disableScissor();
        }

        // Scrollbar
        if (maxScroll(ch) > 0) {
            int sbX = cx + cw + 4;
            CustomGuiUtils.fillUltraRounded(context, sbX, cy, 4, ch, 0x33000000, 2);
            CustomGuiUtils.fillUltraRounded(context, sbX, thumbY(cy, ch), 4, thumbHeight(ch), scrollDragging ? GuiTheme.accent() : 0x88A0AAB8, 2);
        }

        if (BameClientConfig.threeDGui) {
            ThreeDGuiManager.pop3D(context);
        }

        com.bame.client.notification.NotificationManager.render(context, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean twice) {
        geometry();
        if (BameClientConfig.threeDGui && leftSkinWidget != null) {
            if (inside(click.x(), click.y(), leftSkinWidget.getX(), leftSkinWidget.getY(), leftSkinWidget.getWidth(), leftSkinWidget.getHeight())) {
                return true;
            }
        }

        if (BameClientConfig.threeDGui) {
            double[] localM = ThreeDGuiManager.screenToLocal(click.x(), click.y());
            click = new Click(localM[0], localM[1], click.buttonInfo());
        }
        double mx = click.x();
        double my = click.y();

        String badge = "SECRET MENU";
        int badgeW = textRenderer.getWidth(CustomGuiUtils.getFontText(badge)) + 12;

        // Secret bind quick switch button
        String comboStr = getComboString();
        String bindLabel = listeningCombo ? "> Tasten drücken... <" : ("Bind: " + comboStr);
        int bindBtnW = textRenderer.getWidth(CustomGuiUtils.getFontText(bindLabel)) + 14;
        int bindBtnX = px + 142 + badgeW + 8;
        int bindBtnY = py + 16;
        int bindBtnH = 16;

        if (inside(mx, my, bindBtnX, bindBtnY, bindBtnW, bindBtnH)) {
            if (click.button() == 1) { // Right click -> Reset to default (Ctrl + Shift + K)
                BameClientConfig.secretRequireShift = BameClientConfig.DEFAULT_SECRET_SHIFT;
                BameClientConfig.secretRequireCtrl = BameClientConfig.DEFAULT_SECRET_CTRL;
                BameClientConfig.secretRequireAlt = BameClientConfig.DEFAULT_SECRET_ALT;
                BameClientConfig.secretKey = BameClientConfig.DEFAULT_SECRET_KEY;
                listeningCombo = false;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            } else if (click.button() == 0) {
                listeningCombo = !listeningCombo;
                if (listeningCombo) {
                    listeningPearlKey = false;
                    listeningCartKey = false;
                    listeningMaceKey = false;
                    listeningDTapKey = false;
                    listeningCatchKey = false;
                    listeningAggroKey = false;
                    listeningNameKey = false;
                    listeningFakeKey = false;
                    listeningSkinKey = false;
                }
                ClientSoundManager.playClick();
                return true;
            }
        }

        // Search Field clicked
        if (searchField != null && searchField.mouseClicked(click, twice)) {
            unfocus();
            searchField.setFocused(true);
            setFocused(searchField);
            return true;
        }

        if (click.button() != 0) return false;

        int cx = px + 14;
        int cy = py + 48;
        int cw = pw - 28;
        int ch = ph - 56;

        // Scrollbar dragging start
        if (maxScroll(ch) > 0 && (inside(mx, my, cx + cw - 2, cy, 14, ch) || inside(mx, my, px + pw - 14, cy, 12, ch))) {
            unfocus();
            scrollDragging = true;
            scrollGrab = (inside(mx, my, cx + cw - 2, thumbY(cy, ch), 14, thumbHeight(ch)) || inside(mx, my, px + pw - 14, thumbY(cy, ch), 12, thumbHeight(ch))) ? my - thumbY(cy, ch) : thumbHeight(ch) / 2.0;
            dragScroll(my, cy, ch);
            return true;
        }

        if (!inside(mx, my, cx, cy, cw, ch)) {
            unfocus();
            return false;
        }

        int gap = 14;
        int totalCardsW = cw - (maxScroll(ch) > 0 ? 8 : 0);
        int cardW = (totalCardsW - gap) / 2;
        int card1X = cx;
        int card2X = card1X + cardW + gap;

        int baseY = cy - (int) scroll;
        int leftY = baseY;

        // ==================== Card 1: Pearl Prediction ====================
        if (pearlVisible()) {
            int c1H = getPearlHeight();
            // Main toggle
            if (inside(mx, my, card1X + cardW - 38, leftY + 12, 26, 14)) {
                PearlPredictionModule.enabled = !PearlPredictionModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Pearl Prediction", PearlPredictionModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card1X + 60, leftY + 25, 48, 16)) {
                listeningPearlKey = true;
                listeningCombo = false;
                listeningCartKey = false;
                listeningMaceKey = false;
                listeningDTapKey = false;
                listeningCatchKey = false;
                listeningAggroKey = false;
                listeningNameKey = false;
                listeningFakeKey = false;
                listeningSkinKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card1X, leftY, cardW, 46)) {
                PearlPredictionModule.expanded = !PearlPredictionModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (PearlPredictionModule.expanded) {
                int curY = leftY + 54;
                // Enemy Only toggle
                if (inside(mx, my, card1X + cardW - 38, curY + 2, 26, 14)) {
                    PearlPredictionModule.enemyOnly = !PearlPredictionModule.enemyOnly;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                // Landing Box toggle
                if (inside(mx, my, card1X + cardW - 38, curY + 2, 26, 14)) {
                    PearlPredictionModule.landingBox = !PearlPredictionModule.landingBox;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                // Throw Preview toggle
                if (inside(mx, my, card1X + cardW - 38, curY + 2, 26, 14)) {
                    PearlPredictionModule.throwPreview = !PearlPredictionModule.throwPreview;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                // Reset button
                if (inside(mx, my, card1X + cardW - 58, curY, 46, 16)) {
                    PearlPredictionModule.resetToDefault();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            leftY += c1H + 12;
        }

        // ==================== Card 2: AutoCart ====================
        if (autoCartVisible()) {
            int acH = getAutoCartHeight();
            // Main toggle
            if (inside(mx, my, card1X + cardW - 38, leftY + 12, 26, 14)) {
                AutoCartModule.enabled = !AutoCartModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Auto Cart", AutoCartModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card1X + 60, leftY + 25, 48, 16)) {
                listeningCartKey = true;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningMaceKey = false;
                listeningDTapKey = false;
                listeningCatchKey = false;
                listeningAggroKey = false;
                listeningNameKey = false;
                listeningFakeKey = false;
                listeningSkinKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card1X, leftY, cardW, 46)) {
                AutoCartModule.expanded = !AutoCartModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (AutoCartModule.expanded) {
                int rowY = leftY + 54;
                int btnW = 74;
                int btnH = 18;
                int btn2X = card1X + cardW - btnW - 14;
                int btn1X = btn2X - btnW - 6;

                // Mode: Slot Switch
                if (inside(mx, my, btn1X, rowY, btnW, btnH)) {
                    AutoCartModule.mode = AutoCartModule.CartMode.SLOT_SWITCH;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                // Mode: Automatisch
                if (inside(mx, my, btn2X, rowY, btnW, btnH)) {
                    AutoCartModule.mode = AutoCartModule.CartMode.AUTOMATIC;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Switch Back toggle
                if (inside(mx, my, card1X + cardW - 38, rowY + 2, 26, 14)) {
                    AutoCartModule.switchBack = !AutoCartModule.switchBack;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                rowY += 26;

                // Switch Delay button (cycles 1 -> 2 -> 1 tick)
                if (inside(mx, my, card1X + cardW - 64, rowY, 52, 16)) {
                    AutoCartModule.switchDelay = (AutoCartModule.switchDelay == 1) ? 2 : 1;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                rowY += 26;

                // Reset button
                if (inside(mx, my, card1X + cardW - 58, rowY, 46, 16)) {
                    AutoCartModule.resetToDefault();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            leftY += acH + 12;
        }

        // ==================== Card 3: Auto Mace ====================
        if (autoMaceVisible()) {
            int amH = getAutoMaceHeight();
            // Main toggle
            if (inside(mx, my, card1X + cardW - 38, leftY + 12, 26, 14)) {
                AutoMaceModule.enabled = !AutoMaceModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Auto Mace", AutoMaceModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card1X + 60, leftY + 25, 48, 16)) {
                listeningMaceKey = true;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningCartKey = false;
                listeningDTapKey = false;
                listeningCatchKey = false;
                listeningAggroKey = false;
                listeningNameKey = false;
                listeningFakeKey = false;
                listeningSkinKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card1X, leftY, cardW, 46)) {
                AutoMaceModule.expanded = !AutoMaceModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (AutoMaceModule.expanded) {
                int rowY = leftY + 54;
                int btnW = 74;
                int btnH = 18;
                int btn2X = card1X + cardW - btnW - 14;
                int btn1X = btn2X - btnW - 6;

                // Mode: Automatisch
                if (inside(mx, my, btn1X, rowY, btnW, btnH)) {
                    AutoMaceModule.mode = AutoMaceModule.MaceMode.AUTOMATIC;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                // Mode: Bei Klick
                if (inside(mx, my, btn2X, rowY, btnW, btnH)) {
                    AutoMaceModule.mode = AutoMaceModule.MaceMode.ON_CLICK;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Min. Höhe slider (1.0 to 10.0 blocks)
                int sx = card1X + cardW - 100 - 14;
                int sw = 100;
                if (inside(mx, my, sx - 4, rowY + 2, sw + 8, 14)) {
                    draggingMaceHeight = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    AutoMaceModule.minFallDistance = Math.round((1.0 + fval * 9.0) * 10.0) / 10.0;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Switch Delay button (cycles 1 -> 2 -> 3 -> 1 tick)
                if (inside(mx, my, card1X + cardW - 64, rowY, 52, 16)) {
                    if (AutoMaceModule.switchDelay == 1) AutoMaceModule.switchDelay = 2;
                    else if (AutoMaceModule.switchDelay == 2) AutoMaceModule.switchDelay = 3;
                    else AutoMaceModule.switchDelay = 1;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Switch Back toggle
                if (inside(mx, my, card1X + cardW - 38, rowY + 2, 26, 14)) {
                    AutoMaceModule.switchBack = !AutoMaceModule.switchBack;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Cooldown Check toggle
                if (inside(mx, my, card1X + cardW - 38, rowY + 2, 26, 14)) {
                    AutoMaceModule.cooldownCheck = !AutoMaceModule.cooldownCheck;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Nur Spieler toggle
                if (inside(mx, my, card1X + cardW - 38, rowY + 2, 26, 14)) {
                    AutoMaceModule.onlyPlayers = !AutoMaceModule.onlyPlayers;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Reset button
                if (inside(mx, my, card1X + cardW - 58, rowY, 46, 16)) {
                    AutoMaceModule.resetToDefault();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            leftY += amH + 12;
        }

        // ==================== Card 4: D-Tap (Double Tap CPvP) ====================
        if (dTapVisible()) {
            int dtH = getDTapHeight();
            // Main toggle
            if (inside(mx, my, card1X + cardW - 38, leftY + 12, 26, 14)) {
                DTapModule.enabled = !DTapModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("D-Tap", DTapModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card1X + 60, leftY + 25, 48, 16)) {
                listeningDTapKey = true;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningCartKey = false;
                listeningMaceKey = false;
                listeningCatchKey = false;
                listeningAggroKey = false;
                listeningNameKey = false;
                listeningFakeKey = false;
                listeningSkinKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card1X, leftY, cardW, 46)) {
                DTapModule.expanded = !DTapModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (DTapModule.expanded) {
                int rowY = leftY + 54;
                int btnW = 54;
                int btnH = 18;
                int btn3X = card1X + cardW - btnW - 14;
                int btn2X = btn3X - btnW - 4;
                int btn1X = btn2X - btnW - 4;

                // Mode: Taste
                if (inside(mx, my, btn1X, rowY, btnW, btnH)) {
                    DTapModule.mode = DTapModule.DTapMode.KEYBIND;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                // Mode: Klick
                if (inside(mx, my, btn2X, rowY, btnW, btnH)) {
                    DTapModule.mode = DTapModule.DTapMode.ON_CLICK;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                // Mode: Auto
                if (inside(mx, my, btn3X, rowY, btnW, btnH)) {
                    DTapModule.mode = DTapModule.DTapMode.AUTOMATIC;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Tap Delay slider (1 to 10 ticks)
                int sx = card1X + cardW - 100 - 14;
                int sw = 100;
                if (inside(mx, my, sx - 4, rowY + 2, sw + 8, 14)) {
                    draggingDTapDelay = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    DTapModule.tapDelay = Math.max(1, Math.min(10, Math.round(1 + fval * 9.0f)));
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Switch Back toggle
                if (inside(mx, my, card1X + cardW - 38, rowY + 2, 26, 14)) {
                    DTapModule.switchBack = !DTapModule.switchBack;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Reset button
                if (inside(mx, my, card1X + cardW - 58, rowY, 46, 16)) {
                    DTapModule.resetToDefault();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            leftY += dtH + 12;
        }

        // ==================== Card 5: Triggerbot ====================
        if (triggerbotVisible()) {
            int tbH = getTriggerbotHeight();
            // Main toggle
            if (inside(mx, my, card1X + cardW - 38, leftY + 12, 26, 14)) {
                TriggerbotModule.enabled = !TriggerbotModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Triggerbot", TriggerbotModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card1X + 60, leftY + 25, 48, 16)) {
                listeningTriggerbotKey = true;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningCartKey = false;
                listeningMaceKey = false;
                listeningDTapKey = false;
                listeningCatchKey = false;
                listeningAggroKey = false;
                listeningNameKey = false;
                listeningFakeKey = false;
                listeningSkinKey = false;
                listeningShieldBreakerKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card1X, leftY, cardW, 46)) {
                TriggerbotModule.expanded = !TriggerbotModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (TriggerbotModule.expanded) {
                // Profile row 1 & 2:
                int btnW = (cardW - 28 - 6) / 2;
                int btnH = 17;
                int b1X = card1X + 14;
                int b2X = b1X + btnW + 6;

                int rowY = leftY + 52 + 14;
                // Preset 0: 1.9+ PvP
                if (inside(mx, my, b1X, rowY, btnW, btnH)) {
                    TriggerbotModule.applyPreset(0);
                    ClientSoundManager.playClick();
                    return true;
                }
                // Preset 1: Legit (LMB)
                if (inside(mx, my, b2X, rowY, btnW, btnH)) {
                    TriggerbotModule.applyPreset(1);
                    ClientSoundManager.playClick();
                    return true;
                }

                rowY += 21;
                // Preset 2: 1.8 CPS
                if (inside(mx, my, b1X, rowY, btnW, btnH)) {
                    TriggerbotModule.applyPreset(2);
                    ClientSoundManager.playClick();
                    return true;
                }
                // Preset 3: Test-Modus
                if (inside(mx, my, b2X, rowY, btnW, btnH)) {
                    TriggerbotModule.applyPreset(3);
                    ClientSoundManager.playClick();
                    return true;
                }

                // Auslöser
                rowY += 23;
                int optW = 76;
                int optH = 16;
                int opt2X = card1X + cardW - optW - 14;
                int opt1X = opt2X - optW - 4;
                if (inside(mx, my, opt1X, rowY, optW, optH)) {
                    TriggerbotModule.holdAttackKey = false;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, opt2X, rowY, optW, optH)) {
                    TriggerbotModule.holdAttackKey = true;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                // Ziel
                rowY += 22;
                if (inside(mx, my, opt1X, rowY, optW, optH)) {
                    TriggerbotModule.playersOnly = true;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                if (inside(mx, my, opt2X, rowY, optW, optH)) {
                    TriggerbotModule.playersOnly = false;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                // Reichweite slider (2.5 to 4.5 blocks)
                rowY += 22;
                int sx = card1X + cardW - 100 - 14;
                int sw = 100;
                if (inside(mx, my, sx - 4, rowY + 2, sw + 8, 14)) {
                    draggingTriggerbotRange = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    TriggerbotModule.range = Math.round((2.5 + fval * 2.0) * 10.0) / 10.0;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                // Details / Feintuning Toggle Button
                rowY += 22;
                if (inside(mx, my, card1X + 14, rowY, cardW - 28, 16)) {
                    TriggerbotModule.showDetails = !TriggerbotModule.showDetails;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                if (TriggerbotModule.showDetails) {
                    // Modus: Cooldown / CPS
                    rowY += 22;
                    if (inside(mx, my, opt1X, rowY, optW, optH)) {
                        TriggerbotModule.mode = 0;
                        ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }
                    if (inside(mx, my, opt2X, rowY, optW, optH)) {
                        TriggerbotModule.mode = 1;
                        ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }

                    // Charge / CPS slider
                    rowY += 22;
                    if (inside(mx, my, sx - 4, rowY + 2, sw + 8, 14)) {
                        draggingTriggerbotCooldown = true;
                        float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                        if (TriggerbotModule.mode == 0) {
                            TriggerbotModule.cooldownThreshold = Math.round((0.8f + fval * 0.2f) * 100.0f) / 100.0f;
                        } else {
                            TriggerbotModule.cps = Math.max(1, Math.min(20, Math.round(1 + fval * 19.0f)));
                        }
                        ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }

                    // Nur Waffen toggle
                    rowY += 22;
                    if (inside(mx, my, card1X + cardW - 38, rowY + 2, 26, 14)) {
                        TriggerbotModule.weaponOnly = !TriggerbotModule.weaponOnly;
                        ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }

                    // Human Jitter toggle
                    rowY += 22;
                    if (inside(mx, my, card1X + cardW - 38, rowY + 2, 26, 14)) {
                        TriggerbotModule.randomJitter = !TriggerbotModule.randomJitter;
                        ClientSoundManager.playClick();
                        BameClientConfig.save();
                        return true;
                    }

                    // Reset button
                    rowY += 22;
                    if (inside(mx, my, card1X + cardW - 58, rowY, 46, 16)) {
                        TriggerbotModule.resetToDefault();
                        ClientSoundManager.playClick();
                        return true;
                    }
                }
            }
            leftY += tbH + 12;
        }

        int rightY = baseY;

        // ==================== Card: Pearl Catch ====================
        if (pearlCatchVisible()) {
            int pcH = getPearlCatchHeight();
            // Main toggle
            if (inside(mx, my, card2X + cardW - 38, rightY + 12, 26, 14)) {
                PearlCatchModule.enabled = !PearlCatchModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Pearl Catch", PearlCatchModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card2X + 60, rightY + 25, 48, 16)) {
                listeningCatchKey = true;
                listeningAggroKey = false;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningCartKey = false;
                listeningMaceKey = false;
                listeningDTapKey = false;
                listeningNameKey = false;
                listeningFakeKey = false;
                listeningSkinKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card2X, rightY, cardW, 46)) {
                PearlCatchModule.expanded = !PearlCatchModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (PearlCatchModule.expanded) {
                int rowY = rightY + 54;
                // Delay slider (1 to 5 ticks)
                int sx = card2X + cardW - 100 - 14;
                int sw = 100;
                if (inside(mx, my, sx - 4, rowY + 2, sw + 8, 14)) {
                    draggingCatchDelay = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    PearlCatchModule.timingDelay = Math.max(1, Math.min(5, Math.round(1 + fval * 4.0f)));
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Auto Aim toggle
                if (inside(mx, my, card2X + cardW - 38, rowY + 2, 26, 14)) {
                    PearlCatchModule.autoAim = !PearlCatchModule.autoAim;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Switch to Mace toggle
                if (inside(mx, my, card2X + cardW - 38, rowY + 2, 26, 14)) {
                    PearlCatchModule.switchToMace = !PearlCatchModule.switchToMace;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Switch Back toggle
                if (inside(mx, my, card2X + cardW - 38, rowY + 2, 26, 14)) {
                    PearlCatchModule.switchBack = !PearlCatchModule.switchBack;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Reset button
                if (inside(mx, my, card2X + cardW - 58, rowY, 46, 16)) {
                    PearlCatchModule.resetToDefault();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            rightY += pcH + 12;
        }

        // ==================== Card: Aggro Pearl ====================
        if (aggroPearlVisible()) {
            int apH = getAggroPearlHeight();
            // Main toggle
            if (inside(mx, my, card2X + cardW - 38, rightY + 12, 26, 14)) {
                AggroPearlModule.enabled = !AggroPearlModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Aggro Pearl", AggroPearlModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card2X + 60, rightY + 25, 48, 16)) {
                listeningAggroKey = true;
                listeningCatchKey = false;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningCartKey = false;
                listeningMaceKey = false;
                listeningDTapKey = false;
                listeningNameKey = false;
                listeningFakeKey = false;
                listeningSkinKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card2X, rightY, cardW, 46)) {
                AggroPearlModule.expanded = !AggroPearlModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (AggroPearlModule.expanded) {
                int rowY = rightY + 54;
                // Throw Delay slider (0 to 5 ticks)
                int sx = card2X + cardW - 100 - 14;
                int sw = 100;
                if (inside(mx, my, sx - 4, rowY + 2, sw + 8, 14)) {
                    draggingAggroDelay = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    AggroPearlModule.throwDelay = Math.max(0, Math.min(5, Math.round(fval * 5.0f)));
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Auto Aim toggle
                if (inside(mx, my, card2X + cardW - 38, rowY + 2, 26, 14)) {
                    AggroPearlModule.autoAim = !AggroPearlModule.autoAim;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Switch Back toggle
                if (inside(mx, my, card2X + cardW - 38, rowY + 2, 26, 14)) {
                    AggroPearlModule.switchBack = !AggroPearlModule.switchBack;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Reset button
                if (inside(mx, my, card2X + cardW - 58, rowY, 46, 16)) {
                    AggroPearlModule.resetToDefault();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            rightY += apH + 12;
        }

        // ==================== Card 4: Name Protect ====================
        if (nameProtectVisible()) {
            int c2H = getNameProtectHeight();
            // Main toggle
            if (inside(mx, my, card2X + cardW - 38, rightY + 12, 26, 14)) {
                NameProtectModule.enabled = !NameProtectModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Name Protect", NameProtectModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card2X + 60, rightY + 25, 48, 16)) {
                listeningNameKey = true;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningCartKey = false;
                listeningMaceKey = false;
                listeningDTapKey = false;
                listeningCatchKey = false;
                listeningAggroKey = false;
                listeningFakeKey = false;
                listeningSkinKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card2X, rightY, cardW, 46)) {
                NameProtectModule.expanded = !NameProtectModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (NameProtectModule.expanded) {
                int curY = rightY + 54;
                // Alias Field
                if (nameProtectAliasField != null && nameProtectAliasField.mouseClicked(click, twice)) {
                    unfocus();
                    nameProtectAliasField.setFocused(true);
                    setFocused(nameProtectAliasField);
                    return true;
                }
                curY += 26;

                // Reset button
                if (inside(mx, my, card2X + cardW - 58, curY, 46, 16)) {
                    resetNameProtect();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            rightY += c2H + 12;
        }

        // ==================== Card 4: Fake Scoreboard ====================
        if (fakeScoreboardVisible()) {
            int c3H = getFakeScoreboardHeight();
            // Main toggle
            if (inside(mx, my, card2X + cardW - 38, rightY + 12, 26, 14)) {
                FakeScoreboardModule.enabled = !FakeScoreboardModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Fake Scoreboard", FakeScoreboardModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card2X + 60, rightY + 25, 48, 16)) {
                listeningFakeKey = true;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningCartKey = false;
                listeningMaceKey = false;
                listeningDTapKey = false;
                listeningCatchKey = false;
                listeningAggroKey = false;
                listeningNameKey = false;
                listeningSkinKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card2X, rightY, cardW, 46)) {
                FakeScoreboardModule.expanded = !FakeScoreboardModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (FakeScoreboardModule.expanded) {
                if (fakeMoneyField != null && fakeMoneyField.mouseClicked(click, twice)) {
                    unfocus();
                    fakeMoneyField.setFocused(true);
                    setFocused(fakeMoneyField);
                    return true;
                }
                if (fakeStarsField != null && fakeStarsField.mouseClicked(click, twice)) {
                    unfocus();
                    fakeStarsField.setFocused(true);
                    setFocused(fakeStarsField);
                    return true;
                }
                if (fakeKillsField != null && fakeKillsField.mouseClicked(click, twice)) {
                    unfocus();
                    fakeKillsField.setFocused(true);
                    setFocused(fakeKillsField);
                    return true;
                }
                if (fakeDeathsField != null && fakeDeathsField.mouseClicked(click, twice)) {
                    unfocus();
                    fakeDeathsField.setFocused(true);
                    setFocused(fakeDeathsField);
                    return true;
                }
                if (fakeTimeField != null && fakeTimeField.mouseClicked(click, twice)) {
                    unfocus();
                    fakeTimeField.setFocused(true);
                    setFocused(fakeTimeField);
                    return true;
                }

                int actionY = rightY + 54 + 5 * 24 + 2;
                // Edit HUD button
                if (inside(mx, my, card2X + 14, actionY, 52, 16)) {
                    ClientSoundManager.playClick();
                    if (this.client != null) {
                        this.client.setScreen(new HudEditorScreen(this));
                    }
                    return true;
                }

                // Reset button
                if (inside(mx, my, card2X + cardW - 58, actionY, 46, 16)) {
                    resetFakeScoreboard();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            rightY += c3H + 12;
        }

        // ==================== Card 5: SkinProtect ====================
        if (skinProtectVisible()) {
            int c4H = getSkinProtectHeight();
            // Main toggle
            if (inside(mx, my, card2X + cardW - 38, rightY + 12, 26, 14)) {
                SkinProtectModule.enabled = !SkinProtectModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Skin Protect", SkinProtectModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card2X + 60, rightY + 25, 48, 16)) {
                listeningSkinKey = true;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningCartKey = false;
                listeningMaceKey = false;
                listeningDTapKey = false;
                listeningCatchKey = false;
                listeningAggroKey = false;
                listeningNameKey = false;
                listeningFakeKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card2X, rightY, cardW, 46)) {
                SkinProtectModule.expanded = !SkinProtectModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (SkinProtectModule.expanded) {
                int prevH = 135;
                int curY = rightY + 52 + prevH + 9;
                int pad = 12;
                int searchX = card2X + pad;
                int btnW = 38;
                int sFieldW = (cardW - pad * 2) - btnW - 6;

                // Search Field
                if (skinSearchWidget != null && skinSearchWidget.mouseClicked(click, twice)) {
                    unfocus();
                    skinSearchWidget.setFocused(true);
                    setFocused(skinSearchWidget);
                    return true;
                }

                // Set button
                if (inside(mx, my, searchX + sFieldW + 6, curY, btnW, 18)) {
                    applySkinSearch();
                    return true;
                }
                curY += 24;

                // Shuffle button
                if (inside(mx, my, card2X + cardW - 70, curY, 56, 16)) {
                    SkinProtectModule.shuffle();
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }
                curY += 26;

                // Reset button
                if (inside(mx, my, card2X + cardW - 58, curY, 46, 16)) {
                    resetSkinProtect();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            rightY += c4H + 12;
        }

        // ==================== Card 6: Shield Breaker ====================
        if (shieldBreakerVisible()) {
            int sbH = getShieldBreakerHeight();
            // Main toggle
            if (inside(mx, my, card2X + cardW - 38, rightY + 12, 26, 14)) {
                ShieldBreakerModule.enabled = !ShieldBreakerModule.enabled;
                com.bame.client.notification.NotificationManager.showModuleToggle("Shield Breaker", ShieldBreakerModule.enabled);
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            // Keybind button
            if (inside(mx, my, card2X + 60, rightY + 25, 48, 16)) {
                listeningShieldBreakerKey = true;
                listeningCombo = false;
                listeningPearlKey = false;
                listeningCartKey = false;
                listeningMaceKey = false;
                listeningDTapKey = false;
                listeningCatchKey = false;
                listeningAggroKey = false;
                listeningNameKey = false;
                listeningFakeKey = false;
                listeningSkinKey = false;
                listeningTriggerbotKey = false;
                ClientSoundManager.playClick();
                return true;
            }

            // Header expand click
            if (inside(mx, my, card2X, rightY, cardW, 46)) {
                ShieldBreakerModule.expanded = !ShieldBreakerModule.expanded;
                ClientSoundManager.playClick();
                BameClientConfig.save();
                return true;
            }

            if (ShieldBreakerModule.expanded) {
                int rowY = rightY + 54;
                int btnW = 74;
                int btnH = 18;
                int btn2X = card2X + cardW - btnW - 14;
                int btn1X = btn2X - btnW - 6;

                // Mode: Automatisch
                if (inside(mx, my, btn1X, rowY, btnW, btnH)) {
                    ShieldBreakerModule.mode = 0;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                // Mode: Bei Klick
                if (inside(mx, my, btn2X, rowY, btnW, btnH)) {
                    ShieldBreakerModule.mode = 1;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Reichweite slider (2.5 to 4.5 blocks)
                int sx = card2X + cardW - 100 - 14;
                int sw = 100;
                if (inside(mx, my, sx - 4, rowY + 2, sw + 8, 14)) {
                    draggingShieldBreakerRange = true;
                    float fval = (float) Math.clamp((mx - sx) / (double) sw, 0.0, 1.0);
                    ShieldBreakerModule.reach = Math.round((2.5 + fval * 2.0) * 10.0) / 10.0;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Switch Delay button (cycles 1 -> 2 -> 3 -> 1 tick)
                if (inside(mx, my, card2X + cardW - 64, rowY, 52, 16)) {
                    if (ShieldBreakerModule.switchDelay == 1) ShieldBreakerModule.switchDelay = 2;
                    else if (ShieldBreakerModule.switchDelay == 2) ShieldBreakerModule.switchDelay = 3;
                    else ShieldBreakerModule.switchDelay = 1;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Switch Back toggle
                if (inside(mx, my, card2X + cardW - 38, rowY + 2, 26, 14)) {
                    ShieldBreakerModule.switchBack = !ShieldBreakerModule.switchBack;
                    ClientSoundManager.playClick();
                    BameClientConfig.save();
                    return true;
                }

                rowY += 26;
                // Reset button
                if (inside(mx, my, card2X + cardW - 58, rowY, 46, 16)) {
                    ShieldBreakerModule.resetToDefault();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            rightY += sbH + 12;
        }

        unfocus();
        return super.mouseClicked(click, twice);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        geometry();
        double[] localM = ThreeDGuiManager.screenToLocal(mouseX, mouseY);
        double mx = localM[0];
        double my = localM[1];
        int ch = ph - 56;
        if (inside(mx, my, px, py, pw, ph)) {
            scroll = Math.clamp(scroll - vertical * 26, 0, maxScroll(ch));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        geometry();
        if (BameClientConfig.threeDGui && leftSkinWidget != null) {
            if (inside(click.x(), click.y(), leftSkinWidget.getX(), leftSkinWidget.getY(), leftSkinWidget.getWidth(), leftSkinWidget.getHeight())) {
                leftSkinWidget.mouseDragged(click, dx, dy);
                return true;
            }
        }

        if (BameClientConfig.threeDGui) {
            double[] localM = ThreeDGuiManager.screenToLocal(click.x(), click.y());
            click = new Click(localM[0], localM[1], click.buttonInfo());
        }

        int cx = px + 14;
        int cy = py + 48;
        int cw = pw - 28;
        int ch = ph - 56;

        if (scrollDragging) {
            dragScroll(click.y(), cy, ch);
            return true;
        }
        if (draggingMaceHeight) {
            int gap = 14;
            int cardW = (cw - gap) / 2;
            int card1X = cx;
            int sx = card1X + cardW - 100 - 14;
            int sw = 100;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            AutoMaceModule.minFallDistance = Math.round((1.0 + fval * 9.0) * 10.0) / 10.0;
            return true;
        }
        if (draggingDTapDelay) {
            int gap = 14;
            int cardW = (cw - gap) / 2;
            int card1X = cx;
            int sx = card1X + cardW - 100 - 14;
            int sw = 100;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            DTapModule.tapDelay = Math.max(1, Math.min(10, Math.round(1 + fval * 9.0f)));
            return true;
        }
        if (draggingCatchDelay) {
            int gap = 14;
            int cardW = (cw - gap) / 2;
            int card2X = cx + cardW + gap;
            int sx = card2X + cardW - 100 - 14;
            int sw = 100;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            PearlCatchModule.timingDelay = Math.max(1, Math.min(5, Math.round(1 + fval * 4.0f)));
            return true;
        }
        if (draggingAggroDelay) {
            int gap = 14;
            int cardW = (cw - gap) / 2;
            int card2X = cx + cardW + gap;
            int sx = card2X + cardW - 100 - 14;
            int sw = 100;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            AggroPearlModule.throwDelay = Math.max(0, Math.min(5, Math.round(fval * 5.0f)));
            return true;
        }
        if (draggingTriggerbotCooldown) {
            int gap = 14;
            int cardW = (cw - gap) / 2;
            int card1X = cx;
            int sx = card1X + cardW - 100 - 14;
            int sw = 100;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            if (TriggerbotModule.mode == 0) {
                TriggerbotModule.cooldownThreshold = Math.round((0.8f + fval * 0.2f) * 100.0f) / 100.0f;
            } else {
                TriggerbotModule.cps = Math.max(1, Math.min(20, Math.round(1 + fval * 19.0f)));
            }
            return true;
        }
        if (draggingTriggerbotRange) {
            int gap = 14;
            int cardW = (cw - gap) / 2;
            int card1X = cx;
            int sx = card1X + cardW - 100 - 14;
            int sw = 100;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            TriggerbotModule.range = Math.round((2.5 + fval * 2.0) * 10.0) / 10.0;
            return true;
        }
        if (draggingShieldBreakerRange) {
            int gap = 14;
            int cardW = (cw - gap) / 2;
            int card2X = cx + cardW + gap;
            int sx = card2X + cardW - 100 - 14;
            int sw = 100;
            float fval = (float) Math.clamp(((click.x() - sx) / (double) sw), 0.0, 1.0);
            ShieldBreakerModule.reach = Math.round((2.5 + fval * 2.0) * 10.0) / 10.0;
            return true;
        }
        if (skinPreviewWidget != null && inside(click.x(), click.y(), cx, cy, cw, ch) && inside(click.x(), click.y(), skinPreviewWidget.getX(), skinPreviewWidget.getY(), skinPreviewWidget.getWidth(), skinPreviewWidget.getHeight())) {
            skinPreviewWidget.mouseDragged(click, dx, dy);
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        scrollDragging = false;
        if (draggingMaceHeight) {
            draggingMaceHeight = false;
            BameClientConfig.save();
        }
        if (draggingDTapDelay) {
            draggingDTapDelay = false;
            BameClientConfig.save();
        }
        if (draggingCatchDelay) {
            draggingCatchDelay = false;
            BameClientConfig.save();
        }
        if (draggingAggroDelay) {
            draggingAggroDelay = false;
            BameClientConfig.save();
        }
        if (draggingTriggerbotCooldown) {
            draggingTriggerbotCooldown = false;
            BameClientConfig.save();
        }
        if (draggingTriggerbotRange) {
            draggingTriggerbotRange = false;
            BameClientConfig.save();
        }
        if (draggingShieldBreakerRange) {
            draggingShieldBreakerRange = false;
            BameClientConfig.save();
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        // Wartet ein Modul auf eine Taste, bekommt es die Taste zuerst (Escape = Belegung entfernen);
        // danach werden alle Warte-Zustände zurückgesetzt, damit kein Flag hängen bleibt
        if (anyModuleKeyListening()) {
            boolean handled = handleKeyPressed(input);
            clearModuleKeyListening();
            if (input.key() != GLFW.GLFW_KEY_ESCAPE) KeybindConflicts.notifyIfKeyShared(input.key());
            return handled;
        }
        return handleKeyPressed(input);
    }

    private boolean handleKeyPressed(KeyInput input) {
        int key = input.key();

        if (listeningTriggerbotKey) {
            TriggerbotModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningTriggerbotKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningShieldBreakerKey) {
            ShieldBreakerModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningShieldBreakerKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningAggroKey) {
            AggroPearlModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningAggroKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningCatchKey) {
            PearlCatchModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningCatchKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningPearlKey) {
            PearlPredictionModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningPearlKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningCartKey) {
            AutoCartModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningCartKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningMaceKey) {
            AutoMaceModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningMaceKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningDTapKey) {
            DTapModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningDTapKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningNameKey) {
            NameProtectModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningNameKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningFakeKey) {
            FakeScoreboardModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningFakeKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningSkinKey) {
            SkinProtectModule.keyBind = (key == GLFW.GLFW_KEY_ESCAPE) ? -1 : key;
            listeningSkinKey = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (listeningCombo) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                listeningCombo = false;
                ClientSoundManager.playClick();
                return true;
            }

            if (key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT ||
                key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL ||
                key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT) {
                return true;
            }

            int mods = input.modifiers();
            boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
            boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean alt = (mods & GLFW.GLFW_MOD_ALT) != 0;
            if (this.client != null && this.client.getWindow() != null) {
                long handle = this.client.getWindow().getHandle();
                if (!shift) shift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                if (!ctrl) ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
                if (!alt) alt = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
            }

            BameClientConfig.secretRequireShift = shift;
            BameClientConfig.secretRequireCtrl = ctrl;
            BameClientConfig.secretRequireAlt = alt;
            BameClientConfig.secretKey = key;

            listeningCombo = false;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (searchField != null && searchField.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                searchField.setFocused(false);
                setFocused(null);
                return true;
            }
            if (searchField.keyPressed(input)) return true;
        }

        if (nameProtectAliasField != null && nameProtectAliasField.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                nameProtectAliasField.setFocused(false);
                setFocused(null);
                return true;
            }
            if (nameProtectAliasField.keyPressed(input)) return true;
        }

        if (skinSearchWidget != null && skinSearchWidget.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER) {
                applySkinSearch();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                skinSearchWidget.setFocused(false);
                setFocused(null);
                return true;
            }
            if (skinSearchWidget.keyPressed(input)) return true;
        }

        if (fakeMoneyField != null && fakeMoneyField.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { fakeMoneyField.setFocused(false); setFocused(null); return true; }
            if (fakeMoneyField.keyPressed(input)) return true;
        }
        if (fakeStarsField != null && fakeStarsField.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { fakeStarsField.setFocused(false); setFocused(null); return true; }
            if (fakeStarsField.keyPressed(input)) return true;
        }
        if (fakeKillsField != null && fakeKillsField.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { fakeKillsField.setFocused(false); setFocused(null); return true; }
            if (fakeKillsField.keyPressed(input)) return true;
        }
        if (fakeDeathsField != null && fakeDeathsField.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { fakeDeathsField.setFocused(false); setFocused(null); return true; }
            if (fakeDeathsField.keyPressed(input)) return true;
        }
        if (fakeTimeField != null && fakeTimeField.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { fakeTimeField.setFocused(false); setFocused(null); return true; }
            if (fakeTimeField.keyPressed(input)) return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (searchField != null && searchField.isFocused()) return searchField.charTyped(input);
        if (nameProtectAliasField != null && nameProtectAliasField.isFocused()) return nameProtectAliasField.charTyped(input);
        if (skinSearchWidget != null && skinSearchWidget.isFocused()) return skinSearchWidget.charTyped(input);
        if (fakeMoneyField != null && fakeMoneyField.isFocused()) return fakeMoneyField.charTyped(input);
        if (fakeStarsField != null && fakeStarsField.isFocused()) return fakeStarsField.charTyped(input);
        if (fakeKillsField != null && fakeKillsField.isFocused()) return fakeKillsField.charTyped(input);
        if (fakeDeathsField != null && fakeDeathsField.isFocused()) return fakeDeathsField.charTyped(input);
        if (fakeTimeField != null && fakeTimeField.isFocused()) return fakeTimeField.charTyped(input);
        return super.charTyped(input);
    }
}
