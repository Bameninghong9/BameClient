package com.bame.client.render;

import com.bame.client.emotes.HighFiveCoordinator;
import com.bame.client.emotes.HighFiveMotion;
import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * Hinweis unter dem Fadenkreuz, wenn man bei einem wartenden High-Five einschlagen kann:
 * {@code [F] Einschlagen mit <Name>} im Caeser-Stil (abgerundeter Chip, Taste in Akzentfarbe). Die Taste ist die
 * tatsächlich gebundene Taste „High-Five annehmen“. Der Chip blendet beim Erscheinen kurz ein (leicht
 * überschwingendes Aufzoomen, kleines Hochgleiten), die Taste pulsiert sanft.
 */
public class HighFivePromptRenderer implements HudRenderCallback {

    /** Einblenden (ms) und Puls der Taste (Hz). */
    private static final float APPEAR_MS = 180.0F;
    private static final float PULSE_HZ = 1.4F;

    private String shownTarget;
    private long appearAt;

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HighFiveCoordinator.Prompt prompt = HighFiveCoordinator.prompt();
        if (mc.player == null || mc.currentScreen != null || mc.options.hudHidden || prompt == null) {
            shownTarget = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (!prompt.targetId().equals(shownTarget)) {
            shownTarget = prompt.targetId();
            appearAt = now;
        }
        float a = Math.min(1.0F, (now - appearAt) / APPEAR_MS);
        float fade = HighFiveMotion.easeInOut(a);
        float scale = 0.85F + 0.15F * HighFiveMotion.easeOutBack(a);
        float pulse = 0.5F + 0.5F * (float) Math.sin((now % 100_000L) / 1000.0 * Math.PI * 2.0 * PULSE_HZ);

        TextRenderer tr = mc.textRenderer;
        String keyName = HighFiveCoordinator.acceptKeyText().getString();
        Text key = CustomGuiUtils.getFontText(keyName);
        Text label = CustomGuiUtils.getFontText("Einschlagen mit ");
        Text name = CustomGuiUtils.getFontText(prompt.targetName());

        int keyW = Math.max(14, tr.getWidth(key) + 8);
        int labelW = tr.getWidth(label);
        int nameW = tr.getWidth(name);
        int h = 18;
        int w = 4 + keyW + 6 + labelW + nameW + 8;
        int x = (context.getScaledWindowWidth() - w) / 2;
        int y = context.getScaledWindowHeight() / 2 + 14 + Math.round((1.0F - fade) * 4.0F);

        float cx = x + w / 2.0F, cy = y + h / 2.0F;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(cx, cy);
        context.getMatrices().scale(scale, scale);
        context.getMatrices().translate(-cx, -cy);

        int accent = GuiTheme.accent();
        CustomGuiUtils.fillUltraRounded(context, x, y, w, h, alpha(0xD0101118, fade), 5.0f);
        CustomGuiUtils.drawUltraRoundedOutline(context, x, y, w, h, GuiTheme.alpha(accent, Math.round(0xB0 * fade)), 5.0f);

        int kx = x + 3;
        int ky = y + 3;
        int keyFill = Math.round((0x50 + 0x40 * pulse) * fade);
        CustomGuiUtils.fillUltraRounded(context, kx, ky, keyW, h - 6, GuiTheme.alpha(accent, keyFill), 3.0f);
        CustomGuiUtils.drawUltraRoundedOutline(context, kx, ky, keyW, h - 6, alpha(accent | 0xFF000000, fade), 3.0f);
        context.drawText(tr, key, kx + (keyW - tr.getWidth(key)) / 2, ky + (h - 6 - 8) / 2 + 1, alpha(0xFFFFFFFF, fade), false);

        int tx = kx + keyW + 6;
        int ty = y + (h - 8) / 2 + 1;
        context.drawText(tr, label, tx, ty, alpha(0xFFB8BECB, fade), false);
        context.drawText(tr, name, tx + labelW, ty, alpha(0xFFFFFFFF, fade), false);

        context.getMatrices().popMatrix();
    }

    /** Deckkraft einer ARGB-Farbe mit {@code f} multiplizieren (mindestens 4, sonst verwirft der Text-Renderer sie). */
    private static int alpha(int argb, float f) {
        int a = Math.max(4, Math.round(((argb >>> 24) & 0xFF) * f));
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
