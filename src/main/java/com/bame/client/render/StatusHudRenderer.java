package com.bame.client.render;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.module.CpsModule;
import com.bame.client.module.FpsModule;
import com.bame.client.module.NameProtectModule;
import com.bame.client.module.PingModule;
import com.bame.client.module.ServerInfoModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class StatusHudRenderer implements HudRenderCallback {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || !com.bame.client.module.ShowHudModule.enabled) return;

        if (FpsModule.enabled) {
            renderFps(context, FpsModule.hudX, FpsModule.hudY, FpsModule.scale);
        }

        if (PingModule.enabled) {
            renderPing(context, PingModule.hudX, PingModule.hudY, PingModule.scale);
        }

        if (CpsModule.enabled) {
            renderCps(context, CpsModule.hudX, CpsModule.hudY, CpsModule.scale);
        }

        if (ServerInfoModule.enabled) {
            int w = getServerInfoWidth(client);
            int x = ServerInfoModule.hudX;
            if (x == -1) {
                x = client.getWindow().getScaledWidth() - (int)(w * ServerInfoModule.scale) - 10;
            }
            renderServerInfo(context, x, ServerInfoModule.hudY, ServerInfoModule.scale);
        }
    }

    public static int getFpsWidth(MinecraftClient client) {
        String text = client.getCurrentFps() + " FPS";
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    public static int getPingWidth(MinecraftClient client) {
        int ping = getPingValue(client);
        String text = ping + " ms";
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    public static int getCpsWidth(MinecraftClient client) {
        int l = CpsModule.getLeftCps();
        int r = CpsModule.getRightCps();
        String text = l + " | " + r + " CPS";
        return client.textRenderer.getWidth(CustomGuiUtils.getFontText(text)) + 16;
    }

    private static int getPingValue(MinecraftClient client) {
        if (client.getNetworkHandler() != null && client.player != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
            if (entry != null) {
                return entry.getLatency();
            }
        }
        return 0;
    }

    public static void renderFps(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = client.getCurrentFps() + " FPS";
        renderPill(c, x, y, scale, text, FpsModule.bgMode, FpsModule.bgColor);
    }

    public static void renderPing(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        int ping = getPingValue(client);
        String text = ping + " ms";
        renderPill(c, x, y, scale, text, PingModule.bgMode, PingModule.bgColor);
    }

    public static void renderCps(DrawContext c, int x, int y, float scale) {
        int l = CpsModule.getLeftCps();
        int r = CpsModule.getRightCps();
        String text = l + " | " + r + " CPS";
        renderPill(c, x, y, scale, text, CpsModule.bgMode, CpsModule.bgColor);
    }

    private static void renderPill(DrawContext c, int x, int y, float scale, String text, int bgMode, int bgColor) {
        MinecraftClient client = MinecraftClient.getInstance();
        int textWidth = client.textRenderer.getWidth(CustomGuiUtils.getFontText(text));
        int w = textWidth + 16;
        int h = 18;

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        int fill = 0xD012161E;
        int outline = 0xFF292D36;

        if (bgMode == 1) { // Transparent
            fill = 0;
            outline = 0;
        } else if (bgMode == 2) { // Rainbow
            long time = System.currentTimeMillis();
            float hue = (time % 3000L) / 3000.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.75f, 0.9f);
            fill = 0xD0000000 | (rgb & 0xFFFFFF);
            outline = 0xFF000000 | (rgb & 0xFFFFFF);
        } else if (bgMode == 3) { // Theme
            int accent = com.bame.client.gui.GuiTheme.accent();
            fill = 0xB0000000 | (accent & 0xFFFFFF);
            outline = accent;
        }

        if (fill != 0) {
            CustomGuiUtils.fillUltraRounded(c, 0, 0, w, h, fill, 4);
        }
        if (outline != 0) {
            CustomGuiUtils.drawUltraRoundedOutline(c, 0, 0, w, h, outline, 4);
        }
        c.drawText(client.textRenderer, CustomGuiUtils.getFontText(text), 8, (h - 8) / 2, 0xFFFFFFFF, bgMode == 1);

        c.getMatrices().popMatrix();
    }

    public static int getServerInfoWidth(MinecraftClient client) {
        if (!ServerInfoModule.showName && !ServerInfoModule.showServer && !ServerInfoModule.showTime) {
            return 40;
        }
        int curX = 8;
        if (ServerInfoModule.showName) {
            String name = client.player != null ? client.player.getName().getString() : (client.getSession() != null ? client.getSession().getUsername() : "Player");
            if (NameProtectModule.enabled) name = NameProtectModule.getProtectedName(name);
            int nw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(name));
            curX += 8 + 5 + nw + 8;
            if (ServerInfoModule.showServer || ServerInfoModule.showTime) curX += 8;
        }
        if (ServerInfoModule.showServer) {
            String server = (client.getCurrentServerEntry() != null) ? client.getCurrentServerEntry().address : "Singleplayer";
            int sw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(server));
            curX += 9 + 5 + sw + 8;
            if (ServerInfoModule.showTime) curX += 8;
        }
        if (ServerInfoModule.showTime) {
            String time = LocalTime.now().format(TIME_FORMATTER);
            int tw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(time));
            curX += 8 + 5 + tw + 8;
        }
        return curX;
    }

    public static void drawUserIcon(DrawContext c, int x, int y, int color) {
        c.fill(x + 2, y, x + 6, y + 1, color);
        c.fill(x + 1, y + 1, x + 7, y + 3, color);
        c.fill(x + 2, y + 3, x + 6, y + 4, color);
        c.fill(x + 2, y + 5, x + 6, y + 6, color);
        c.fill(x + 1, y + 6, x + 7, y + 7, color);
        c.fill(x, y + 7, x + 8, y + 8, color);
    }

    public static void drawCloudIcon(DrawContext c, int x, int y, int color) {
        c.fill(x + 4, y, x + 6, y + 1, color);
        c.fill(x + 3, y + 1, x + 7, y + 2, color);
        c.fill(x + 2, y + 2, x + 8, y + 3, color);
        c.fill(x + 1, y + 3, x + 8, y + 4, color);
        c.fill(x, y + 4, x + 9, y + 5, color);
        c.fill(x, y + 5, x + 9, y + 6, color);
        c.fill(x + 1, y + 6, x + 8, y + 7, color);
    }

    public static void drawClockIcon(DrawContext c, int x, int y, int color) {
        c.fill(x + 2, y, x + 6, y + 1, color);
        c.fill(x + 1, y + 1, x + 2, y + 2, color);
        c.fill(x + 6, y + 1, x + 7, y + 2, color);
        c.fill(x, y + 2, x + 1, y + 6, color);
        c.fill(x + 7, y + 2, x + 8, y + 6, color);
        c.fill(x + 1, y + 6, x + 2, y + 7, color);
        c.fill(x + 6, y + 6, x + 7, y + 7, color);
        c.fill(x + 2, y + 7, x + 6, y + 8, color);
        c.fill(x + 3, y + 2, x + 4, y + 4, color);
        c.fill(x + 4, y + 3, x + 6, y + 4, color);
    }

    public static void renderServerInfo(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = getServerInfoWidth(client);
        int h = 18;

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        int fill = 0xD012161E;
        int outline = 0xFF292D36;

        if (ServerInfoModule.bgMode == 1) { // Transparent
            fill = 0;
            outline = 0;
        } else if (ServerInfoModule.bgMode == 2) { // Rainbow
            long curTime = System.currentTimeMillis();
            float hue = (curTime % 3000L) / 3000.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.75f, 0.9f);
            fill = 0xD0000000 | (rgb & 0xFFFFFF);
            outline = 0xFF000000 | (rgb & 0xFFFFFF);
        } else if (ServerInfoModule.bgMode == 3) { // Theme
            int accent = com.bame.client.gui.GuiTheme.accent();
            fill = 0xB0000000 | (accent & 0xFFFFFF);
            outline = accent;
        }

        if (fill != 0) {
            CustomGuiUtils.fillUltraRounded(c, 0, 0, w, h, fill, 4);
        }
        if (outline != 0) {
            CustomGuiUtils.drawUltraRoundedOutline(c, 0, 0, w, h, outline, 4);
        }

        int textY = (h - 8) / 2;
        boolean shadow = ServerInfoModule.bgMode == 1;
        int curX = 8;

        if (ServerInfoModule.showName) {
            String name = client.player != null ? client.player.getName().getString() : (client.getSession() != null ? client.getSession().getUsername() : "Player");
            if (NameProtectModule.enabled) name = NameProtectModule.getProtectedName(name);
            int nw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(name));
            drawUserIcon(c, curX, 5, 0xFFFFFFFF);
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(name), curX + 8 + 5, textY, 0xFFFFFFFF, shadow);
            curX += 8 + 5 + nw + 8;
            if (ServerInfoModule.showServer || ServerInfoModule.showTime) {
                c.fill(curX - 4, 3, curX - 3, h - 3, 0x338E95A4);
                curX += 8;
            }
        }

        if (ServerInfoModule.showServer) {
            String server = (client.getCurrentServerEntry() != null) ? client.getCurrentServerEntry().address : "Singleplayer";
            int sw = client.textRenderer.getWidth(CustomGuiUtils.getFontText(server));
            drawCloudIcon(c, curX, 5, 0xFFFFFFFF);
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(server), curX + 9 + 5, textY, 0xFFFFFFFF, shadow);
            curX += 9 + 5 + sw + 8;
            if (ServerInfoModule.showTime) {
                c.fill(curX - 4, 3, curX - 3, h - 3, 0x338E95A4);
                curX += 8;
            }
        }

        if (ServerInfoModule.showTime) {
            String time = LocalTime.now().format(TIME_FORMATTER);
            drawClockIcon(c, curX, 5, 0xFFFFFFFF);
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(time), curX + 8 + 5, textY, 0xFFFFFFFF, shadow);
        }

        c.getMatrices().popMatrix();
    }
}
