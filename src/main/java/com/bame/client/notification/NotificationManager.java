package com.bame.client.notification;

import com.bame.client.BameClientConfig;
import com.bame.client.gui.CustomGuiUtils;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotificationManager implements HudRenderCallback {
    private static final List<Notification> active = new CopyOnWriteArrayList<>();

    private static final Identifier TOAST_CIRCLE = Identifier.of("bameclient", "textures/gui/icons/toast_circle.png");
    private static final Identifier TOAST_CHECK = Identifier.of("bameclient", "textures/gui/icons/toast_check.png");
    private static final Identifier TOAST_CROSS = Identifier.of("bameclient", "textures/gui/icons/toast_cross.png");
    private static final Identifier TOAST_INFO = Identifier.of("bameclient", "textures/gui/icons/toast_info.png");

    public static void add(Notification notification) {
        if (!BameClientConfig.notifications) return;

        // Ensure maximum 2 notifications stacked at any time
        while (active.size() >= 2) {
            active.remove(0);
        }
        active.add(notification);
    }

    public static void showModuleToggle(String moduleName, boolean enabled) {
        String title = enabled ? "Module Enabled" : "Module Disabled";
        String message = moduleName + (enabled ? " enabled" : " disabled");
        add(new Notification(title, message, enabled ? Notification.Type.ENABLED : Notification.Type.DISABLED));
    }

    public static void showInfo(String title, String message) {
        add(new Notification(title, message, Notification.Type.INFO));
    }

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;
        if (client.currentScreen == null) {
            render(drawContext, tickCounter != null ? tickCounter.getTickProgress(true) : 1.0f);
        }
    }

    public static void render(DrawContext context, float delta) {
        if (active.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getWindow() == null) return;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        TextRenderer tr = client.textRenderer;
        if (tr == null) return;

        int margin = 12;
        int cardH = 34;
        int spacing = 6;

        for (int i = 0; i < active.size(); i++) {
            Notification notif = active.get(i);
            if (notif == null) continue;

            int targetIndex = i;
            int targetY = screenH - margin - cardH - (targetIndex * (cardH + spacing));

            if (notif.getCurrentY() < 0) {
                notif.setCurrentY(targetY);
            } else {
                float cy = notif.getCurrentY();
                cy += (targetY - cy) * Math.min(1.0f, Math.max(0.1f, delta * 0.4f));
                notif.setCurrentY(cy);
            }

            // Animate slide
            float sp = notif.getSlideProgress();
            boolean expiring = notif.isExpired() || notif.getRemainingRatio() <= 0.08f;
            if (expiring) {
                sp = Math.max(0.0f, sp - Math.max(0.05f, delta * 0.22f));
            } else {
                sp = Math.min(1.0f, sp + Math.max(0.05f, delta * 0.25f));
            }
            notif.setSlideProgress(sp);

            if (notif.isExpired() && sp <= 0.02f) {
                active.remove(notif);
                continue;
            }

            // Modern TrueType typography (Segoe UI)
            Text titleText = CustomGuiUtils.getFontText(notif.getTitle());
            Text msgText = CustomGuiUtils.getFontText(notif.getMessage());
            int twTitle = tr.getWidth(titleText);
            int twMsg = tr.getWidth(msgText);
            int cardW = Math.max(140, Math.max(twTitle, twMsg) + 48);

            float eased = easeOutCubic(sp);
            int cardX = screenW - (int) ((cardW + margin) * eased);
            int cardY = Math.round(notif.getCurrentY());

            int alpha = Math.clamp(Math.round(250 * eased), 0, 250);
            if (alpha <= 5) continue;

            // Modern layered soft drop shadow
            CustomGuiUtils.fillUltraRounded(context, cardX + 1, cardY + 2, cardW, cardH, ((alpha * 35 / 255) << 24) | 0x000000, 8.0f);
            CustomGuiUtils.fillUltraRounded(context, cardX, cardY + 1, cardW, cardH, ((alpha * 60 / 255) << 24) | 0x000000, 7.5f);

            // Modern slate card background & subtle border
            int bg = (alpha << 24) | 0x11141C;
            int border = ((alpha * 125 / 255) << 24) | 0x2A3140;
            CustomGuiUtils.fillUltraRounded(context, cardX, cardY, cardW, cardH, bg, 7.0f);
            CustomGuiUtils.drawUltraRoundedOutline(context, cardX, cardY, cardW, cardH, border, 7.0f);

            // Smooth anti-aliased circular badge (bilinear filtered texture)
            int iconSize = 20;
            int iconX = cardX + 8;
            int iconY = cardY + (cardH - iconSize) / 2;
            int iconBg = (alpha << 24) | (notif.getType().color & 0x00FFFFFF);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, TOAST_CIRCLE, iconX, iconY, 0.0f, 0.0f, iconSize, iconSize, 128, 128, 128, 128, iconBg);

            // Crisp vector checkmark / cross icon
            Identifier symbolTex = switch (notif.getType()) {
                case ENABLED -> TOAST_CHECK;
                case DISABLED -> TOAST_CROSS;
                case INFO -> TOAST_INFO;
            };
            int symbolSize = 11;
            int symbolX = iconX + (iconSize - symbolSize) / 2;
            int symbolY = iconY + (iconSize - symbolSize) / 2;
            int symbolColor = (alpha << 24) | 0x00FFFFFF;
            context.drawTexture(RenderPipelines.GUI_TEXTURED, symbolTex, symbolX, symbolY, 0.0f, 0.0f, symbolSize, symbolSize, 128, 128, 128, 128, symbolColor);

            // Title & message text with modern TrueType font
            int textX = cardX + 34;
            int textAlpha = (alpha << 24) | 0x00FFFFFF;
            context.drawText(tr, titleText, textX, cardY + 5, textAlpha, false);

            int subColor = ((alpha * 215 / 255) << 24) | 0x0094A3B8;
            context.drawText(tr, msgText, textX, cardY + 17, subColor, false);

            // Inset capsule progress bar
            int barMargin = 8;
            int barTrackW = cardW - (barMargin * 2);
            int barY = cardY + cardH - 4;
            int trackColor = ((alpha * 35 / 255) << 24) | 0x00FFFFFF;
            CustomGuiUtils.fillUltraRounded(context, cardX + barMargin, barY, barTrackW, 2, trackColor, 1.0f);

            float remaining = notif.getRemainingRatio();
            int barW = Math.max(0, Math.round(barTrackW * remaining));
            if (barW > 0) {
                int barColor = (alpha << 24) | (notif.getType().color & 0x00FFFFFF);
                CustomGuiUtils.fillUltraRounded(context, cardX + barMargin, barY, barW, 2, barColor, 1.0f);
            }
        }
    }

    private static float easeOutCubic(float x) {
        float inv = 1.0f - x;
        return 1.0f - inv * inv * inv;
    }
}
