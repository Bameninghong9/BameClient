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
        int naturalW = maxW + 14;
        return FakeScoreboardModule.customWidth > 0 ? Math.max(naturalW, FakeScoreboardModule.customWidth) : naturalW;
    }

    public static int getHeight() {
        int naturalH = 70;
        return FakeScoreboardModule.customHeight > 0 ? Math.max(naturalH, FakeScoreboardModule.customHeight) : naturalH;
    }

    public static void render(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = getWidth(client);
        int h = getHeight();

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        StatusHudRenderer.drawBoxBg(c, 0, 0, w, h, FakeScoreboardModule.bgMode, FakeScoreboardModule.outlineColor);
        if (FakeScoreboardModule.bgMode == 1 || FakeScoreboardModule.bgMode == 2) {
            c.fill(3, 14, w - 3, 15, 0x44FFFFFF);
        }

        int startX = 6;
        int curY = 4;
        int spacing = (h > 70) ? (h - 70) / 5 : 0;

        // Line 1: Player Name / Title (Centered with drop shadow)
        String name = FakeScoreboardModule.getDisplayName();
        int nameW = client.textRenderer.getWidth(name);
        int nameX = (w - nameW) / 2;
        c.drawText(client.textRenderer, name, Math.max(startX, nameX), curY, 0xFFFFFFFF, true);
        curY += 12 + spacing;

        // Line 2: Money ($ in Green, value in White, with drop shadow)
        c.drawText(client.textRenderer, "$", startX, curY, 0xFF55FF55, true);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.money, startX + client.textRenderer.getWidth("$"), curY, 0xFFFFFFFF, true);
        curY += 10 + spacing;

        // Line 3: Stars (★ in Purple, value in White, with drop shadow)
        c.drawText(client.textRenderer, "★", startX, curY, 0xFFFF55FF, true);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.stars, startX + client.textRenderer.getWidth("★"), curY, 0xFFFFFFFF, true);
        curY += 10 + spacing;

        // Line 4: Kills (🗡 in Red, value in White, with drop shadow)
        c.drawText(client.textRenderer, "🗡", startX, curY, 0xFFFF2222, true);
        int swordW = Math.max(9, client.textRenderer.getWidth("🗡"));
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.kills, startX + swordW, curY, 0xFFFFFFFF, true);
        curY += 10 + spacing;

        // Line 5: Deaths (☠ in Orange, value in White, with drop shadow)
        c.drawText(client.textRenderer, "☠", startX, curY, 0xFFFF7700, true);
        int skullW = Math.max(9, client.textRenderer.getWidth("☠"));
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.deaths, startX + skullW, curY, 0xFFFFFFFF, true);
        curY += 10 + spacing;

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
