package com.bame.client.gui;

import net.minecraft.client.gui.DrawContext;

public class CustomGuiUtils {
    public static void fillRounded(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void drawRoundedOutline(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void fillSuperRounded(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 2, y, x + width - 2, y + height, color);
        context.fill(x, y + 2, x + 2, y + height - 2, color);
        context.fill(x + width - 2, y + 2, x + width, y + height - 2, color);
        
        context.fill(x + 1, y + 1, x + 2, y + 2, color);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    public static void fillUltraRounded(DrawContext context, int x, int y, int width, int height, int color) {
        fillUltraRounded(context, x, y, width, height, color, 8.0f);
    }

    public static void fillUltraRounded(DrawContext context, int x, int y, int width, int height, int color, float radius) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;
        int rgb = color & 0x00FFFFFF;

        float r = Math.min(radius, Math.min(width / 2.0f, height / 2.0f));
        int ir = (int) Math.ceil(r);
        
        // Draw the inner cross (solid)
        context.fill(x + ir, y, x + width - ir, y + height, color);
        context.fill(x, y + ir, x + ir, y + height - ir, color);
        context.fill(x + width - ir, y + ir, x + width, y + height - ir, color);
        
        // Draw the 4 rounded corners with smooth SDF-like alpha transition
        for (int cy = 0; cy < ir; cy++) {
            for (int cx = 0; cx < ir; cx++) {
                float dx = r - cx - 0.5f;
                float dy = r - cy - 0.5f;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float alphaMult = Math.max(0.0f, Math.min(1.0f, r + 0.5f - dist));
                
                if (alphaMult > 0.0f) {
                    int newA = (int) (a * alphaMult);
                    int newColor = (newA << 24) | rgb;
                    
                    context.fill(x + cx, y + cy, x + cx + 1, y + cy + 1, newColor);
                    context.fill(x + width - 1 - cx, y + cy, x + width - cx, y + cy + 1, newColor);
                    context.fill(x + cx, y + height - 1 - cy, x + cx + 1, y + height - cy, newColor);
                    context.fill(x + width - 1 - cx, y + height - 1 - cy, x + width - cx, y + height - cy, newColor);
                }
            }
        }
    }

    public static void drawUltraRoundedOutline(DrawContext context, int x, int y, int width, int height, int color) {
        drawUltraRoundedOutline(context, x, y, width, height, color, 8.0f);
    }

    public static void drawUltraRoundedOutline(DrawContext context, int x, int y, int width, int height, int color, float radius) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;
        int rgb = color & 0x00FFFFFF;

        float r = Math.min(radius, Math.min(width / 2.0f, height / 2.0f));
        int ir = (int) Math.ceil(r);
        float thickness = 1.0f;
        
        // Draw straight edges
        context.fill(x + ir, y, x + width - ir, y + 1, color);
        context.fill(x + ir, y + height - 1, x + width - ir, y + height, color);
        context.fill(x, y + ir, x + 1, y + height - ir, color);
        context.fill(x + width - 1, y + ir, x + width, y + height - ir, color);
        
        // Draw corners
        for (int cy = 0; cy < ir; cy++) {
            for (int cx = 0; cx < ir; cx++) {
                float dx = r - cx - 0.5f;
                float dy = r - cy - 0.5f;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                
                // Outline SDF logic
                float distToEdge = Math.abs(dist - r);
                float alphaMult = Math.max(0.0f, Math.min(1.0f, 0.5f + (thickness / 2.0f) - distToEdge));
                
                if (alphaMult > 0.0f) {
                    int newA = (int) (a * alphaMult);
                    int newColor = (newA << 24) | rgb;
                    
                    context.fill(x + cx, y + cy, x + cx + 1, y + cy + 1, newColor);
                    context.fill(x + width - 1 - cx, y + cy, x + width - cx, y + cy + 1, newColor);
                    context.fill(x + cx, y + height - 1 - cy, x + cx + 1, y + height - cy, newColor);
                    context.fill(x + width - 1 - cx, y + height - 1 - cy, x + width - cx, y + height - cy, newColor);
                }
            }
        }
    }

    public static void drawSmoothRing(DrawContext context, int cx, int cy, float r, float thickness, int color) {
        int ir = (int) Math.ceil(r + thickness);
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int rgb = color & 0x00FFFFFF;
        
        for (int y = -ir; y <= ir; y++) {
            for (int x = -ir; x <= ir; x++) {
                float dist = (float) Math.hypot(x, y);
                float distToEdge = Math.abs(dist - r);
                float alphaMult = Math.max(0.0f, Math.min(1.0f, 0.5f + (thickness / 2.0f) - distToEdge));
                if (alphaMult > 0.0f) {
                    int newA = (int) (a * alphaMult);
                    int newColor = (newA << 24) | rgb;
                    context.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, newColor);
                }
            }
        }
    }

    public static void drawSmoothLine(DrawContext context, float x1, float y1, float x2, float y2, float thickness, int color) {
        int minX = (int) Math.floor(Math.min(x1, x2) - thickness - 1);
        int maxX = (int) Math.ceil(Math.max(x1, x2) + thickness + 1);
        int minY = (int) Math.floor(Math.min(y1, y2) - thickness - 1);
        int maxY = (int) Math.ceil(Math.max(y1, y2) + thickness + 1);
        
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        int rgb = color & 0x00FFFFFF;
        
        float l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
        if (l2 == 0) return;
        
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float t = Math.max(0, Math.min(1, ((x - x1) * (x2 - x1) + (y - y1) * (y2 - y1)) / l2));
                float projX = x1 + t * (x2 - x1);
                float projY = y1 + t * (y2 - y1);
                float dist = (float) Math.hypot(x - projX, y - projY);
                
                float alphaMult = Math.max(0.0f, Math.min(1.0f, 0.5f + (thickness / 2.0f) - dist));
                if (alphaMult > 0.0f) {
                    int newA = (int) (a * alphaMult);
                    int newColor = (newA << 24) | rgb;
                    context.fill(x, y, x + 1, y + 1, newColor);
                }
            }
        }
    }

    private static final java.util.Map<String, net.minecraft.util.Identifier> ICONS = java.util.Map.of(
        "search", net.minecraft.util.Identifier.of("bameclient", "textures/gui/icons/search.png"),
        "combat", net.minecraft.util.Identifier.of("bameclient", "textures/gui/icons/combat.png"),
        "movement", net.minecraft.util.Identifier.of("bameclient", "textures/gui/icons/movement.png"),
        "visuals", net.minecraft.util.Identifier.of("bameclient", "textures/gui/icons/visuals.png"),
        "misc", net.minecraft.util.Identifier.of("bameclient", "textures/gui/icons/misc.png"),
        "world", net.minecraft.util.Identifier.of("bameclient", "textures/gui/icons/world.png"),
        "settings", net.minecraft.util.Identifier.of("bameclient", "textures/gui/icons/settings.png"),
        "reset", net.minecraft.util.Identifier.of("bameclient", "textures/gui/icons/reset.png"));

    private static void drawIcon(DrawContext context, String name, int x, int y, int color) {
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
            ICONS.get(name), x, y, 0.0f, 0.0f, 12, 12, 96, 96, 96, 96, color);
    }

    public static void drawSearchIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "search", x, y, color); }
    public static void drawCombatIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "combat", x, y, color); }
    public static void drawMovementIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "movement", x, y, color); }
    public static void drawVisualsIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "visuals", x, y, color); }
    public static void drawMiscIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "misc", x, y, color); }
    public static void drawGlobeIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "world", x, y, color); }
    public static void drawGearIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "settings", x, y, color); }
    public static void drawResetIcon(DrawContext c, int x, int y, int color) { drawIcon(c, "reset", x, y, color); }

    public static net.minecraft.text.MutableText getFontText(String text) {
        return net.minecraft.text.Text.literal(text).setStyle(net.minecraft.text.Style.EMPTY.withFont(new net.minecraft.text.StyleSpriteSource.Font(net.minecraft.util.Identifier.of("bameclient", "sans"))));
    }
}
