package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.module.PearlPredictionModule;
import com.bame.client.render.BlurRenderer;
import com.bame.client.sound.ClientSoundManager;
import com.bame.client.wallpaper.WallpaperManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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
    private long openTime = System.currentTimeMillis();

    public SecretScreen(Screen parent) {
        super(Text.literal("Secret Menu"));
        this.parent = parent;
    }

    @Override
    public void close() {
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

        int pw = Math.clamp(width - 40, 560, 640);
        int ph = Math.clamp(height - 40, 250, 280);
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

        // Close / Zurück button
        button(context, "Zurück", px + pw - 64, py + 14, 50, 18, mouseX, mouseY);

        // Header separator
        context.fill(px + 10, py + 42, px + pw - 10, py + 43, 0xFF292D36);

        // Cards layout
        int gap = 14;
        int cardW = (pw - 28 - gap) / 2;
        int card1X = px + 14;
        int card2X = card1X + cardW + gap;
        int cardY = py + 52;

        // ==================== CARD 1: Pearl Prediction ====================
        int card1H = PearlPredictionModule.expanded ? 168 : 46;
        box(context, card1X, cardY, cardW, card1H, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        CustomGuiUtils.drawUltraRoundedOutline(context, card1X, cardY, cardW, card1H, 0xFF292D36, 6);

        text(context, "Pearl Prediction", card1X + 12, cardY + 12, 0xFFE2E5ED);
        text(context, "KeyBind:", card1X + 12, cardY + 29, 0xFF8E95A4);

        String kb = listeningPearlKey ? "..." : formatKey(PearlPredictionModule.keyBind);
        button(context, kb, card1X + 60, cardY + 25, 48, 16, mouseX, mouseY);
        toggle(context, card1X + cardW - 38, cardY + 12, PearlPredictionModule.enabled, mouseX, mouseY, delta);

        if (PearlPredictionModule.expanded) {
            context.fill(card1X + 8, cardY + 46, card1X + cardW - 8, cardY + 47, 0xFF292D36);

            int curY = cardY + 54;
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

        // ==================== CARD 2: Secret Unlock Keybind ====================
        int card2H = 168;
        box(context, card2X, cardY, cardW, card2H, GuiTheme.alpha(GuiTheme.surface(), BameClientConfig.seeThrough ? 210 : 255));
        CustomGuiUtils.drawUltraRoundedOutline(context, card2X, cardY, cardW, card2H, 0xFF292D36, 6);

        text(context, "Secret Unlock Bind", card2X + 12, cardY + 12, 0xFFE2E5ED);
        text(context, "Tastenkombination", card2X + 12, cardY + 29, 0xFF8E95A4);

        int actW = textRenderer.getWidth(CustomGuiUtils.getFontText("ACTIVE")) + 10;
        CustomGuiUtils.fillUltraRounded(context, card2X + cardW - actW - 12, cardY + 12, actW, 16, GuiTheme.alpha(GuiTheme.accent(), 40), 4);
        text(context, "ACTIVE", card2X + cardW - actW - 7, cardY + 16, GuiTheme.accent());

        context.fill(card2X + 8, cardY + 46, card2X + cardW - 8, cardY + 47, 0xFF292D36);

        text(context, "Kombination zum Entsperren:", card2X + 14, cardY + 56, 0xFFD4D8E0);

        int btnW = cardW - 28;
        int btnH = 26;
        int btnX = card2X + 14;
        int btnY = cardY + 74;
        String comboLabel = listeningCombo ? "> Tasten drücken... <" : getComboString();
        modeButton(context, comboLabel, btnX, btnY, btnW, btnH, mouseX, mouseY, listeningCombo);

        text(context, listeningCombo ? "Drücke z.B. Ctrl + C (ESC: Stop)" : "Klicken, um Bind zu ändern", card2X + 14, cardY + 106, 0xFF7F8694);
        text(context, "Standard: Ctrl + C", card2X + 14, cardY + 122, 0xFF7F8694);

        button(context, "Default", card2X + cardW - 58, cardY + 140, 46, 16, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(Click click, boolean twice) {
        if (click.button() != 0) return false;
        double mx = click.x();
        double my = click.y();

        int pw = Math.clamp(width - 40, 560, 640);
        int ph = Math.clamp(height - 40, 250, 280);
        int px = (width - pw) / 2;
        int py = (height - ph) / 2;

        // Close button
        if (inside(mx, my, px + pw - 64, py + 14, 50, 18)) {
            ClientSoundManager.playClick();
            close();
            return true;
        }

        int gap = 14;
        int cardW = (pw - 28 - gap) / 2;
        int card1X = px + 14;
        int card2X = card1X + cardW + gap;
        int cardY = py + 52;

        // Card 1: Pearl Prediction
        // Main toggle
        if (inside(mx, my, card1X + cardW - 38, cardY + 12, 26, 14)) {
            PearlPredictionModule.enabled = !PearlPredictionModule.enabled;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        // Keybind button
        if (inside(mx, my, card1X + 60, cardY + 25, 48, 16)) {
            listeningPearlKey = true;
            listeningCombo = false;
            ClientSoundManager.playClick();
            return true;
        }

        // Header expand click
        if (inside(mx, my, card1X, cardY, cardW, 46)) {
            PearlPredictionModule.expanded = !PearlPredictionModule.expanded;
            ClientSoundManager.playClick();
            BameClientConfig.save();
            return true;
        }

        if (PearlPredictionModule.expanded) {
            int curY = cardY + 54;
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

        // Card 2: Secret Unlock Bind
        int btnW = cardW - 28;
        int btnH = 26;
        int btnX = card2X + 14;
        int btnY = cardY + 74;

        if (inside(mx, my, btnX, btnY, btnW, btnH)) {
            listeningCombo = true;
            listeningPearlKey = false;
            ClientSoundManager.playClick();
            return true;
        }

        // Reset to default combo (Ctrl + C)
        if (inside(mx, my, card2X + cardW - 58, cardY + 140, 46, 16)) {
            BameClientConfig.secretRequireShift = false;
            BameClientConfig.secretRequireCtrl = true;
            BameClientConfig.secretRequireAlt = false;
            BameClientConfig.secretKey = GLFW.GLFW_KEY_C;
            BameClientConfig.save();
            ClientSoundManager.playClick();
            return true;
        }

        return super.mouseClicked(click, twice);
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

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }
}
