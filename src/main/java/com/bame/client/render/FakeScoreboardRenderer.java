package com.bame.client.render;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.module.FakeScoreboardModule;
import com.bame.client.module.ShowHudModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class FakeScoreboardRenderer implements HudRenderCallback {

    private static final String[] SWORD_PIXELS = {
        "......##",
        ".....##.",
        "....##..",
        "...##...",
        ".###....",
        "##.#....",
        ".#.#....",
        "#......."
    };

    private static final String[] SKULL_PIXELS = {
        ".######.",
        "########",
        "##.##.##",
        "##.##.##",
        "########",
        ".######.",
        ".#.##.#.",
        "..####.."
    };

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
        if (client.options.hudHidden || !ShowHudModule.enabled || !FakeScoreboardModule.enabled) return;

        int w = getWidth(client);
        int x = FakeScoreboardModule.hudX;
        if (x == -1) {
            x = client.getWindow().getScaledWidth() - (int)(w * FakeScoreboardModule.scale) - 10;
        }
        render(context, x, FakeScoreboardModule.hudY, FakeScoreboardModule.scale);
    }

    public static int getWidth(MinecraftClient client) {
        if (client == null) client = MinecraftClient.getInstance();
        String name = FakeScoreboardModule.getDisplayName();
        int maxW = client.textRenderer.getWidth(name);
        maxW = Math.max(maxW, client.textRenderer.getWidth("$ " + FakeScoreboardModule.money));
        maxW = Math.max(maxW, client.textRenderer.getWidth("★ " + FakeScoreboardModule.stars));
        maxW = Math.max(maxW, 11 + client.textRenderer.getWidth(FakeScoreboardModule.kills));
        maxW = Math.max(maxW, 11 + client.textRenderer.getWidth(FakeScoreboardModule.deaths));
        maxW = Math.max(maxW, 11 + client.textRenderer.getWidth(FakeScoreboardModule.playtime));
        return maxW + 16;
    }

    public static int getHeight() {
        return 74;
    }

    public static void render(DrawContext c, int x, int y, float scale) {
        MinecraftClient client = MinecraftClient.getInstance();
        int w = getWidth(client);
        int h = getHeight();

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        int bgMode = FakeScoreboardModule.bgMode;
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

        boolean shadow = (bgMode == 1);
        int startX = 8;
        int curY = 6;

        // Line 1: Player Name (White)
        String name = FakeScoreboardModule.getDisplayName();
        c.drawText(client.textRenderer, name, startX, curY, 0xFFFFFFFF, shadow);
        curY += 11;

        // Line 2: Money ($ in Green, value in White)
        c.drawText(client.textRenderer, "$", startX, curY, 0xFF55FF55, shadow);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.money, startX + client.textRenderer.getWidth("$"), curY, 0xFFFFFFFF, shadow);
        curY += 11;

        // Line 3: Stars (★ in Purple, value in White)
        c.drawText(client.textRenderer, "★", startX, curY, 0xFFFF55FF, shadow);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.stars, startX + client.textRenderer.getWidth("★"), curY, 0xFFFFFFFF, shadow);
        curY += 11;

        // Line 4: Kills (Sword in Red, value in White)
        drawPixelIcon(c, SWORD_PIXELS, startX, curY, 0xFFFF2222);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.kills, startX + 11, curY, 0xFFFFFFFF, shadow);
        curY += 11;

        // Line 5: Deaths (Skull in Orange, value in White)
        drawPixelIcon(c, SKULL_PIXELS, startX, curY, 0xFFFF7700);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.deaths, startX + 11, curY, 0xFFFFFFFF, shadow);
        curY += 11;

        // Line 6: Playtime (Clock in Yellow, value in White)
        drawPixelIcon(c, CLOCK_PIXELS, startX, curY, 0xFFFFDD00);
        c.drawText(client.textRenderer, " " + FakeScoreboardModule.playtime, startX + 11, curY, 0xFFFFFFFF, shadow);

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
