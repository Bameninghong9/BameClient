package com.bame.client.render;

import com.bame.client.module.CustomCrosshairModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class CustomCrosshairRenderer {

    public static void render(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;

        int cx = context.getScaledWindowWidth() / 2;
        int cy = context.getScaledWindowHeight() / 2;

        renderAt(context, cx, cy, 1, CustomCrosshairModule.color);
    }

    public static void renderAt(DrawContext context, int centerX, int centerY, int pixelSize, int color) {
        int startX = centerX - CustomCrosshairModule.CENTER * pixelSize;
        int startY = centerY - CustomCrosshairModule.CENTER * pixelSize;

        for (int r = 0; r < CustomCrosshairModule.GRID_SIZE; r++) {
            for (int c = 0; c < CustomCrosshairModule.GRID_SIZE; c++) {
                if (CustomCrosshairModule.grid[r][c]) {
                    context.fill(
                        startX + c * pixelSize,
                        startY + r * pixelSize,
                        startX + (c + 1) * pixelSize,
                        startY + (r + 1) * pixelSize,
                        color
                    );
                }
            }
        }
    }
}
