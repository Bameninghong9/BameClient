package com.bame.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomGuiUtils {
    public static void fillRounded(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - 1, color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public static void drawRoundedOutline(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y, x + width - 1, y + 1, color);
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, color);
        context.fill(x, y + 1, x + 1, y + height - ir(1), color);
        context.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    private static int ir(float r) { return (int) Math.ceil(r); }

    public static void fillSuperRounded(DrawContext context, int x, int y, int width, int height, int color) {
        context.fill(x + 2, y, x + width - 2, y + height, color);
        context.fill(x, y + 2, x + 2, y + height - 2, color);
        context.fill(x + width - 2, y + 2, x + width, y + height - 2, color);
        context.fill(x + 1, y + 1, x + 2, y + 2, color);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    // Precomputed corner geometry data for ultra-fast zero-alloc rendering with anti-aliasing
    private static class CornerData {
        final int ir;
        final int[] solidStart;
        final int[] fillEdgeCx;
        final int[] fillEdgeCy;
        final float[] fillEdgeAlpha;
        final int[] outlineCx;
        final int[] outlineCy;
        final float[] outlineAlpha;

        CornerData(float r) {
            this.ir = (int) Math.ceil(r);
            this.solidStart = new int[ir];
            List<Integer> fCx = new ArrayList<>();
            List<Integer> fCy = new ArrayList<>();
            List<Float> fA = new ArrayList<>();
            List<Integer> oCx = new ArrayList<>();
            List<Integer> oCy = new ArrayList<>();
            List<Float> oA = new ArrayList<>();

            float thickness = 1.0f;

            for (int cy = 0; cy < ir; cy++) {
                int firstSolid = ir;
                for (int cx = 0; cx < ir; cx++) {
                    float dx = r - cx - 0.5f;
                    float dy = r - cy - 0.5f;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);
                    float fillAlpha = Math.max(0.0f, Math.min(1.0f, r + 0.5f - dist));

                    if (fillAlpha >= 0.999f) {
                        if (firstSolid == ir) firstSolid = cx;
                    } else if (fillAlpha > 0.001f) {
                        fCx.add(cx);
                        fCy.add(cy);
                        fA.add(fillAlpha);
                    }

                    float distToEdge = Math.abs(dist - r);
                    float outlineAlpha = Math.max(0.0f, Math.min(1.0f, 0.5f + (thickness / 2.0f) - distToEdge));
                    if (outlineAlpha > 0.001f) {
                        oCx.add(cx);
                        oCy.add(cy);
                        oA.add(outlineAlpha);
                    }
                }
                solidStart[cy] = firstSolid;
            }

            this.fillEdgeCx = fCx.stream().mapToInt(Integer::intValue).toArray();
            this.fillEdgeCy = fCy.stream().mapToInt(Integer::intValue).toArray();
            this.fillEdgeAlpha = new float[fA.size()];
            for (int i = 0; i < fA.size(); i++) fillEdgeAlpha[i] = fA.get(i);

            this.outlineCx = oCx.stream().mapToInt(Integer::intValue).toArray();
            this.outlineCy = oCy.stream().mapToInt(Integer::intValue).toArray();
            this.outlineAlpha = new float[oA.size()];
            for (int i = 0; i < oA.size(); i++) outlineAlpha[i] = oA.get(i);
        }
    }

    private static final CornerData[] CACHED_CORNERS = new CornerData[20];
    static {
        for (int i = 1; i <= 16; i++) {
            CACHED_CORNERS[i] = new CornerData((float) i);
        }
    }

    private static CornerData getCornerData(float radius, int width, int height) {
        float r = Math.min(radius, Math.min(width / 2.0f, height / 2.0f));
        int ir = Math.clamp(Math.round(r), 1, 16);
        CornerData cd = CACHED_CORNERS[ir];
        return cd != null ? cd : CACHED_CORNERS[4];
    }

    public static void fillUltraRounded(DrawContext context, int x, int y, int width, int height, int color) {
        fillUltraRounded(context, x, y, width, height, color, 8.0f);
    }

    public static void fillUltraRounded(DrawContext context, int x, int y, int width, int height, int color, float radius) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;
        int rgb = color & 0x00FFFFFF;

        CornerData cd = getCornerData(radius, width, height);
        int ir = cd.ir;

        // Draw horizontal solid spans across both corners and center
        for (int cy = 0; cy < ir; cy++) {
            int ss = cd.solidStart[cy];
            if (ss < width - ss) {
                context.fill(x + ss, y + cy, x + width - ss, y + cy + 1, color);
                context.fill(x + ss, y + height - 1 - cy, x + width - ss, y + height - cy, color);
            }
        }

        // Draw central body
        if (y + ir < y + height - ir) {
            context.fill(x, y + ir, x + width, y + height - ir, color);
        }

        // Draw anti-aliased corner edge pixels
        for (int i = 0; i < cd.fillEdgeCx.length; i++) {
            int cx = cd.fillEdgeCx[i];
            int cy = cd.fillEdgeCy[i];
            int newColor = (((int) (a * cd.fillEdgeAlpha[i])) << 24) | rgb;

            context.fill(x + cx, y + cy, x + cx + 1, y + cy + 1, newColor);
            context.fill(x + width - 1 - cx, y + cy, x + width - cx, y + cy + 1, newColor);
            context.fill(x + cx, y + height - 1 - cy, x + cx + 1, y + height - cy, newColor);
            context.fill(x + width - 1 - cx, y + height - 1 - cy, x + width - cx, y + height - cy, newColor);
        }
    }

    public static void drawUltraRoundedOutline(DrawContext context, int x, int y, int width, int height, int color) {
        drawUltraRoundedOutline(context, x, y, width, height, color, 8.0f);
    }

    public static void drawUltraRoundedOutline(DrawContext context, int x, int y, int width, int height, int color, float radius) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;
        int rgb = color & 0x00FFFFFF;

        CornerData cd = getCornerData(radius, width, height);
        int ir = cd.ir;

        // Draw straight edges
        context.fill(x + ir, y, x + width - ir, y + 1, color);
        context.fill(x + ir, y + height - 1, x + width - ir, y + height, color);
        context.fill(x, y + ir, x + 1, y + height - ir, color);
        context.fill(x + width - 1, y + ir, x + width, y + height - ir, color);

        // Draw outline corners
        for (int i = 0; i < cd.outlineCx.length; i++) {
            int cx = cd.outlineCx[i];
            int cy = cd.outlineCy[i];
            int newColor = (((int) (a * cd.outlineAlpha[i])) << 24) | rgb;

            context.fill(x + cx, y + cy, x + cx + 1, y + cy + 1, newColor);
            context.fill(x + width - 1 - cx, y + cy, x + width - cx, y + cy + 1, newColor);
            context.fill(x + cx, y + height - 1 - cy, x + cx + 1, y + height - cy, newColor);
            context.fill(x + width - 1 - cx, y + height - 1 - cy, x + width - cx, y + height - cy, newColor);
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

    private static final Map<String, Identifier> ICONS = Map.of(
        "search", Identifier.of("bameclient", "textures/gui/icons/search.png"),
        "combat", Identifier.of("bameclient", "textures/gui/icons/combat.png"),
        "movement", Identifier.of("bameclient", "textures/gui/icons/movement.png"),
        "visuals", Identifier.of("bameclient", "textures/gui/icons/visuals.png"),
        "misc", Identifier.of("bameclient", "textures/gui/icons/misc.png"),
        "world", Identifier.of("bameclient", "textures/gui/icons/world.png"),
        "settings", Identifier.of("bameclient", "textures/gui/icons/settings.png"),
        "reset", Identifier.of("bameclient", "textures/gui/icons/reset.png"));

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

    private static final Identifier C_LOGO = Identifier.of("bameclient", "textures/gui/c_logo.png");
    public static void drawCLogo(DrawContext context, int x, int y, int size) {
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
            C_LOGO, x, y, 0.0f, 0.0f, size, size, 34, 34, 34, 34, 0xFFFFFFFF);
    }

    private static final Style SANS_STYLE = Style.EMPTY
        .withFont(new StyleSpriteSource.Font(Identifier.of("bameclient", "sans")));
    private static final Map<String, Text> TEXT_CACHE = new ConcurrentHashMap<>();

    public static Text getFontText(String text) {
        if (text == null) return Text.empty();
        return TEXT_CACHE.computeIfAbsent(text, t -> Text.literal(t).setStyle(SANS_STYLE));
    }
}
