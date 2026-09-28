package com.bame.client.render;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.module.FakeScoreboardModule;
import com.bame.client.module.ShowHudModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class FakeScoreboardRenderer implements HudRenderCallback {

    private static final String[] CLOCK_PIXELS = {
        "..####..",
        ".#....#.",
        "#...#..#",
        "#...#..#",
        "#...##.#",
        "#......#",
        ".#....#.",
        "..####.."
    };

    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || !FakeScoreboardModule.enabled) return;

        int w = getWidth(client);
        int h = getHeight();
        int x = FakeScoreboardModule.hudX;
        if (x == -1) {
            x = client.getWindow().getScaledWidth() - (int)(w * FakeScoreboardModule.scale) - 3;
        }
        int y = FakeScoreboardModule.hudY;
        if (y == -1) {
            y = (client.getWindow().getScaledHeight() - (int)(h * FakeScoreboardModule.scale)) / 2;
        }
        render(context, x, y, FakeScoreboardModule.scale);
    }

    public static int getWidth(MinecraftClient client) {
        if (client == null) client = MinecraftClient.getInstance();
        String name = FakeScoreboardModule.getDisplayName();
        int maxW = client.textRenderer.getWidth(name);
        maxW = Math.max(maxW, client.textRenderer.getWidth("$") + client.textRenderer.getWidth(" " + FakeScoreboardModule.money));
        maxW = Math.max(maxW, client.textRenderer.getWidth("★") + client.textRenderer.getWidth(" " + FakeScoreboardModule.stars));
        int swordW = Math.max(9, client.textRenderer.getWidth("🗡"));
        maxW = Math.max(maxW, swordW + client.textRenderer.getWidth(" " + FakeScoreboardModule.kills));
        int skullW = Math.max(9, client.textRenderer.getWidth("☠"));
        maxW = Math.max(maxW, skullW + client.textRenderer.getWidth(" " + FakeScoreboardModule.deaths));
        maxW = Math.max(maxW, 9 + client.textRenderer.getWidth(" " + FakeScoreboardModule.playtime));
        return maxW + 6;
    }

    public static int getHeight() {
        return 62;
    }

    public static void render(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = getWidth(client);
        int h = getHeight();

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        int bgMode = FakeScoreboardModule.bgMode;
        if (bgMode == 0) { // Dark (Classic Vanilla Scoreboard)
            c.fill(0, 0, w, 11, 0x60000000);
            c.fill(0, 11, w, h, 0x40000000);
        } else if (bgMode == 1) { // Transparent
            // No background fill
        } else if (bgMode == 2) { // Rainbow
            long time = System.currentTimeMillis();
            float hue = (time % 3000L) / 3000.0f;
            int rgb = java.awt.Color.HSBtoRGB(hue, 0.75f, 0.9f);
            c.fill(0, 0, w, 11, 0x80000000 | (rgb & 0xFFFFFF));
            c.fill(0, 11, w, h, 0x50000000 | (rgb & 0xFFFFFF));
        } else if (bgMode == 3) { // Theme
            int accent = com.bame.client.gui.GuiTheme.accent();
            c.fill(0, 0, w, 11, 0x80000000 | (accent & 0xFFFFFF));
            c.fill(0, 11, w, h, 0x50000000 | (accent & 0xFFFFFF));
        }

        int startX = 3;
        int curY = 1;

        // Line 1: Player Name / Title (Centered with drop shadow)
        String name = FakeScoreboardModule.getDisplayName();
        int nameW = client.textRenderer.getWidth(name);
        int nameX = (w - nameW) / 2;
        c.drawText(client.textRenderer, name, Math.max(3, nameX), curY, 0xFFFFFFFF, true);
        curY += 11;

        // Line 2: Money ($ in Green, value in White, with drop shadow)
        c.drawText(client.textRenderer, "$", startX, curY, 0xFF55FF55, true);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.money, startX + client.textRenderer.getWidth("$"), curY, 0xFFFFFFFF, true);
        curY += 10;

        // Line 3: Stars (★ in Purple, value in White, with drop shadow)
        c.drawText(client.textRenderer, "★", startX, curY, 0xFFFF55FF, true);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.stars, startX + client.textRenderer.getWidth("★"), curY, 0xFFFFFFFF, true);
        curY += 10;

        // Line 4: Kills (🗡 in Red, value in White, with drop shadow)
        c.drawText(client.textRenderer, "🗡", startX, curY, 0xFFFF2222, true);
        int swordW = Math.max(9, client.textRenderer.getWidth("🗡"));
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.kills, startX + swordW, curY, 0xFFFFFFFF, true);
        curY += 10;

        // Line 5: Deaths (☠ in Orange, value in White, with drop shadow)
        c.drawText(client.textRenderer, "☠", startX, curY, 0xFFFF7700, true);
        int skullW = Math.max(9, client.textRenderer.getWidth("☠"));
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.deaths, startX + skullW, curY, 0xFFFFFFFF, true);
        curY += 10;

        // Line 6: Playtime (Clock in Yellow, value in White, with drop shadow)
        drawPixelIcon(c, CLOCK_PIXELS, startX + 1, curY + 1, 0x80000000); // shadow
        drawPixelIcon(c, CLOCK_PIXELS, startX, curY, 0xFFFFDD00);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.playtime, startX + 9, curY, 0xFFFFFFFF, true);

        c.getMatrices().popMatrix();
    }

    private static void drawPixelIcon(DrawContext c, String[] pattern, int px, int py, int color) {
        for (int r = 0; r < pattern.length; r++) {
            String line = pattern[r];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == '#') {
                    c.fill(px + col, py + r, px + col + 1, py + r + 1, color);
                }
            }
        }
    }
}
