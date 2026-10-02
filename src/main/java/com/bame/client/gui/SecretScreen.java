package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.module.AutoCartModule;
import com.bame.client.module.AutoMaceModule;
import com.bame.client.module.FakeScoreboardModule;
import com.bame.client.module.NameProtectModule;
import com.bame.client.module.PearlPredictionModule;
import com.bame.client.module.SkinProtectModule;
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
    private boolean listeningNameKey = false;
    private boolean listeningFakeKey = false;
    private boolean listeningSkinKey = false;
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

    public SecretScreen(Screen parent) {
        super(Text.literal("Secret Menu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        int pw = Math.clamp(width - 40, 600, 680);
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

    private int getPearlHeight() {
        return PearlPredictionModule.expanded ? 168 : 46;
    }

    private int getAutoCartHeight() {
        return AutoCartModule.expanded ? 158 : 46;
    }

    private int getAutoMaceHeight() {
        return AutoMaceModule.expanded ? 210 : 46;
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

        int rightH = 0;
        if (nameProtectVisible()) rightH += getNameProtectHeight() + 12;
        if (fakeScoreboardVisible()) rightH += getFakeScoreboardHeight() + 12;
        if (skinProtectVisible()) rightH += getSkinProtectHeight() + 12;

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
        if (BameClientConfig.frostedBlur) {
            applyBlur(context);
        } else {
            context.fill(0, 0, this.width, this.height, 0x99000000);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        int pw = Math.clamp(width - 40, 600, 680);
        int ph = Math.clamp(height - 40, 320, 390);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;

        float progress = Math.min(1.0f, (System.currentTimeMillis() - openTime) / 300.0f);
        float p = 1.0f - (float) Math.pow(1.0f - progress, 4);

        // Window background
        int bg = GuiTheme.alpha(GuiTheme.current().background(), BameClientConfig.seeThrough ? 205 : 255);
        box(context, px, py, pw, ph, bg);

        // Custom Wallpaper
        if (BameClientConfig.customWallpaper) {
            WallpaperManager.render(context, px, py, pw, ph, p);
        }

        // Window outline
        CustomGuiUtils.drawUltraRoundedOutline(context, px, py, pw, ph, 0xFF292D36, 8.0f);

        // Ambient lighting
        if (BameClientConfig.ambientBackground) {
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
        context.fill(px + 10, py + 42, px + pw - 10, py + 43, 0xFF292D36);

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
        context.enableScissor(cx, cy, cx + cw, cy + ch);

        int leftY = baseY;

        // ==================== CARD 1: Pearl Prediction ====================
        if (pearlVisible()) {
            int c1H = getPearlHeight();
            box(context, card1X, leftY, cardW, c1H, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
            CustomGuiUtils.drawUltraRoundedOutline(context, card1X, leftY, cardW, c1H, 0xFF292D36, 6);

            text(context, "Pearl Prediction", card1X + 12, leftY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card1X + 12, leftY + 29, 0xFF8E95A4);

            String kb1 = listeningPearlKey ? "..." : formatKey(PearlPredictionModule.keyBind);
            button(context, kb1, card1X + 60, leftY + 25, 48, 16, mouseX, mouseY);
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
            box(context, card1X, leftY, cardW, acH, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
            CustomGuiUtils.drawUltraRoundedOutline(context, card1X, leftY, cardW, acH, 0xFF292D36, 6);

            text(context, "AutoCart", card1X + 12, leftY + 12, 0xFFE2E5ED);

            // Subtitle badge showing active mode
            String modeBadge = AutoCartModule.mode.getDisplayName();
            text(context, modeBadge, card1X + 68, leftY + 12, 0xFF8E95A4);

            text(context, "KeyBind:", card1X + 12, leftY + 29, 0xFF8E95A4);

            String kbCart = listeningCartKey ? "..." : formatKey(AutoCartModule.keyBind);
            button(context, kbCart, card1X + 60, leftY + 25, 48, 16, mouseX, mouseY);
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
            box(context, card1X, leftY, cardW, amH, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
            CustomGuiUtils.drawUltraRoundedOutline(context, card1X, leftY, cardW, amH, 0xFF292D36, 6);

            text(context, "Auto Mace", card1X + 12, leftY + 12, 0xFFE2E5ED);

            // Subtitle badge showing active mode
            String modeBadge = AutoMaceModule.mode.getDisplayName();
            text(context, modeBadge, card1X + 74, leftY + 12, 0xFF8E95A4);

            text(context, "KeyBind:", card1X + 12, leftY + 29, 0xFF8E95A4);

            String kbMace = listeningMaceKey ? "..." : formatKey(AutoMaceModule.keyBind);
            button(context, kbMace, card1X + 60, leftY + 25, 48, 16, mouseX, mouseY);
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

        int rightY = baseY;

        // ==================== CARD 4: Name Protect ====================
        if (nameProtectVisible()) {
            int c2H = getNameProtectHeight();
            box(context, card2X, rightY, cardW, c2H, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
            CustomGuiUtils.drawUltraRoundedOutline(context, card2X, rightY, cardW, c2H, 0xFF292D36, 6);

            text(context, "Name Protect", card2X + 12, rightY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card2X + 12, rightY + 29, 0xFF8E95A4);

            String kbName = listeningNameKey ? "..." : formatKey(NameProtectModule.keyBind);
            button(context, kbName, card2X + 60, rightY + 25, 48, 16, mouseX, mouseY);
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
            box(context, card2X, rightY, cardW, c3H, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
            CustomGuiUtils.drawUltraRoundedOutline(context, card2X, rightY, cardW, c3H, 0xFF292D36, 6);

            text(context, "Fake Scoreboard", card2X + 12, rightY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card2X + 12, rightY + 29, 0xFF8E95A4);

            String kb2 = listeningFakeKey ? "..." : formatKey(FakeScoreboardModule.keyBind);
            button(context, kb2, card2X + 60, rightY + 25, 48, 16, mouseX, mouseY);
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
            box(context, card2X, rightY, cardW, c4H, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
            CustomGuiUtils.drawUltraRoundedOutline(context, card2X, rightY, cardW, c4H, 0xFF292D36, 6);

            text(context, "SkinProtect", card2X + 12, rightY + 12, 0xFFE2E5ED);
            text(context, "KeyBind:", card2X + 12, rightY + 29, 0xFF8E95A4);

            String kbSkin = listeningSkinKey ? "..." : formatKey(SkinProtectModule.keyBind);
            button(context, kbSkin, card2X + 60, rightY + 25, 48, 16, mouseX, mouseY);
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

        // Close scissor
        context.disableScissor();

        // Scrollbar
        if (maxScroll(ch) > 0) {
            int sbX = px + pw - 9;
            CustomGuiUtils.fillUltraRounded(context, sbX, cy, 4, ch, 0x33000000, 2);
            CustomGuiUtils.fillUltraRounded(context, sbX, thumbY(cy, ch), 4, thumbHeight(ch), scrollDragging ? GuiTheme.accent() : 0x88A0AAB8, 2);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean twice) {
        double mx = click.x();
        double my = click.y();

        int pw = Math.clamp(width - 40, 600, 680);
        int ph = Math.clamp(height - 40, 320, 390);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;

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
            if (click.button() == 1) { // Right click -> Reset to default Ctrl + C
                BameClientConfig.secretRequireShift = false;
                BameClientConfig.secretRequireCtrl = true;
                BameClientConfig.secretRequireAlt = false;
                BameClientConfig.secretKey = GLFW.GLFW_KEY_C;
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
        if (maxScroll(ch) > 0 && inside(mx, my, px + pw - 14, cy, 12, ch)) {
            unfocus();
            scrollDragging = true;
            scrollGrab = inside(mx, my, px + pw - 14, thumbY(cy, ch), 12, thumbHeight(ch)) ? my - thumbY(cy, ch) : thumbHeight(ch) / 2.0;
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

        int rightY = baseY;

        // ==================== Card 4: Name Protect ====================
        if (nameProtectVisible()) {
            int c2H = getNameProtectHeight();
            // Main toggle
            if (inside(mx, my, card2X + cardW - 38, rightY + 12, 26, 14)) {
                NameProtectModule.enabled = !NameProtectModule.enabled;
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

        unfocus();
        return super.mouseClicked(click, twice);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int pw = Math.clamp(width - 40, 600, 680);
        int ph = Math.clamp(height - 40, 320, 390);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        int ch = ph - 56;
        if (inside(mouseX, mouseY, px, py, pw, ph)) {
            scroll = Math.clamp(scroll - vertical * 26, 0, maxScroll(ch));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        int pw = Math.clamp(width - 40, 600, 680);
        int ph = Math.clamp(height - 40, 320, 390);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;
        int cx = px + 14;
        int cy = py + 48;
        int cw = pw - 28;
        int ch = ph - 56;

        if (scrollDragging) {
            dragScroll(click.y(), cy, ch);
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
        return super.mouseReleased(click);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();

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
