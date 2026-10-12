package com.bame.client.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the anti-aliased rounded rectangles drawn by {@link CustomGuiUtils}. Contains no Minecraft classes so the
 * pixel output can be checked offline (see GuiGeometryTest in the plannerTest source set).
 *
 * <p>The corners are drawn as small textured quads whose alpha values come from {@link CornerData}; the straight parts are
 * a handful of plain fills. That gives at most ~8 GUI elements per rectangle instead of one element per edge pixel.
 * The old per-pixel code is kept as {@link #fillPerPixel} / {@link #outlinePerPixel}: it is the reference for the test and
 * the fallback for degenerate sizes (width or height smaller than {@code 2 * radius - 1}, e.g. width 0).
 */
public final class RoundedRectGeometry {
    private RoundedRectGeometry() {
    }

    /** Receives the draw operations. {@code C} is the draw context (a {@code DrawContext} in game). */
    public interface Target<C> {
        void fill(C ctx, int x1, int y1, int x2, int y2, int color);

        /**
         * Draws the region (u, v, w, h) of the (2 * ir) x (2 * ir) corner texture at (x, y), tinted with {@code color}
         * (colour multiplied with the white texel, alpha multiplied with the texel alpha).
         * The texture holds the top-left corner in its top-left quadrant and mirrored copies in the other three quadrants.
         */
        void corner(C ctx, boolean outline, int ir, int x, int y, int u, int v, int w, int h, int color);
    }

    // Precomputed corner geometry data for ultra-fast zero-alloc rendering with anti-aliasing
    static final class CornerData {
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

    public static final int MAX_RADIUS = 16;

    private static final CornerData[] CACHED_CORNERS = new CornerData[20];
    static {
        for (int i = 1; i <= MAX_RADIUS; i++) {
            CACHED_CORNERS[i] = new CornerData((float) i);
        }
    }

    private static CornerData getCornerData(float radius, int width, int height) {
        float r = Math.min(radius, Math.min(width / 2.0f, height / 2.0f));
        int ir = Math.clamp(Math.round(r), 1, MAX_RADIUS);
        CornerData cd = CACHED_CORNERS[ir];
        return cd != null ? cd : CACHED_CORNERS[4];
    }

    /** Integer corner radius actually used for a rectangle of this size. */
    public static int cornerRadius(float radius, int width, int height) {
        return getCornerData(radius, width, height).ir;
    }

    /**
     * Alpha values (0-255) of the corner texture for radius {@code ir}: (2 * ir) x (2 * ir), row-major. The top-left
     * quadrant is the top-left corner exactly as the per-pixel code draws it (solid from {@code solidStart}, anti-aliased
     * edge pixels with {@code (int) (255 * alpha)}); the other quadrants are its mirror images.
     */
    public static int[] buildCornerAlpha(int ir, boolean outline) {
        CornerData cd = CACHED_CORNERS[ir];
        int[] quadrant = new int[ir * ir];
        if (outline) {
            for (int i = 0; i < cd.outlineCx.length; i++) {
                quadrant[cd.outlineCy[i] * ir + cd.outlineCx[i]] = (int) (255 * cd.outlineAlpha[i]);
            }
        } else {
            for (int cy = 0; cy < ir; cy++) {
                for (int cx = cd.solidStart[cy]; cx < ir; cx++) {
                    quadrant[cy * ir + cx] = 255;
                }
            }
            for (int i = 0; i < cd.fillEdgeCx.length; i++) {
                quadrant[cd.fillEdgeCy[i] * ir + cd.fillEdgeCx[i]] = (int) (255 * cd.fillEdgeAlpha[i]);
            }
        }

        int size = 2 * ir;
        int[] alpha = new int[size * size];
        for (int cy = 0; cy < ir; cy++) {
            for (int cx = 0; cx < ir; cx++) {
                int a = quadrant[cy * ir + cx];
                alpha[cy * size + cx] = a;
                alpha[cy * size + (size - 1 - cx)] = a;
                alpha[(size - 1 - cy) * size + cx] = a;
                alpha[(size - 1 - cy) * size + (size - 1 - cx)] = a;
            }
        }
        return alpha;
    }

    public static <C> void fill(Target<C> t, C ctx, int x, int y, int width, int height, int color, float radius) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;

        CornerData cd = getCornerData(radius, width, height);
        int ir = cd.ir;
        if (width < 2 * ir - 1 || height < 2 * ir - 1) {
            fillPerPixel(t, ctx, x, y, width, height, color, radius);
            return;
        }
        int size = 2 * ir;

        // Central body (full width) and the straight strips between the corners
        if (height > 2 * ir) {
            t.fill(ctx, x, y + ir, x + width, y + height - ir, color);
        }
        if (width > 2 * ir) {
            t.fill(ctx, x + ir, y, x + width - ir, y + ir, color);
            t.fill(ctx, x + ir, y + height - ir, x + width - ir, y + height, color);
        }

        // With width == 2 * ir - 1 the left and right corners share the middle column. The per-pixel code covers the
        // solid part of that column once (one span per row) but its anti-aliased pixels twice (left and right loop), so
        // the right-hand corners leave that column out and its non-solid rows are drawn a second time below.
        // A shared middle row (height == 2 * ir - 1) needs nothing special: there the per-pixel code also draws twice.
        int rightW = width == 2 * ir - 1 ? ir - 1 : ir;
        t.corner(ctx, false, ir, x, y, 0, 0, ir, ir, color);
        t.corner(ctx, false, ir, x, y + height - ir, 0, ir, ir, ir, color);
        if (rightW > 0) {
            t.corner(ctx, false, ir, x + width - rightW, y, size - rightW, 0, rightW, ir, color);
            t.corner(ctx, false, ir, x + width - rightW, y + height - ir, size - rightW, ir, rightW, ir, color);
        }
        if (rightW < ir) {
            int edgeRows = 0;
            while (edgeRows < ir && cd.solidStart[edgeRows] == ir) edgeRows++;
            if (edgeRows > 0) {
                t.corner(ctx, false, ir, x + ir - 1, y, ir - 1, 0, 1, edgeRows, color);
                t.corner(ctx, false, ir, x + ir - 1, y + height - edgeRows, ir - 1, size - edgeRows, 1, edgeRows, color);
            }
        }
    }

    public static <C> void outline(Target<C> t, C ctx, int x, int y, int width, int height, int color, float radius) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;

        CornerData cd = getCornerData(radius, width, height);
        int ir = cd.ir;
        if (width < 2 * ir - 1 || height < 2 * ir - 1) {
            outlinePerPixel(t, ctx, x, y, width, height, color, radius);
            return;
        }

        // Draw straight edges (identical to the per-pixel version)
        t.fill(ctx, x + ir, y, x + width - ir, y + 1, color);
        t.fill(ctx, x + ir, y + height - 1, x + width - ir, y + height, color);
        t.fill(ctx, x, y + ir, x + 1, y + height - ir, color);
        t.fill(ctx, x + width - 1, y + ir, x + width, y + height - ir, color);

        // Corners; a shared middle column/row is drawn twice, exactly like the per-pixel loop does
        t.corner(ctx, true, ir, x, y, 0, 0, ir, ir, color);
        t.corner(ctx, true, ir, x + width - ir, y, ir, 0, ir, ir, color);
        t.corner(ctx, true, ir, x, y + height - ir, 0, ir, ir, ir, color);
        t.corner(ctx, true, ir, x + width - ir, y + height - ir, ir, ir, ir, ir, color);
    }

    /** The original pixel-by-pixel implementation (reference and fallback for degenerate sizes). */
    public static <C> void fillPerPixel(Target<C> t, C ctx, int x, int y, int width, int height, int color, float radius) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;
        int rgb = color & 0x00FFFFFF;

        CornerData cd = getCornerData(radius, width, height);
        int ir = cd.ir;

        // Draw horizontal solid spans across both corners and center
        for (int cy = 0; cy < ir; cy++) {
            int ss = cd.solidStart[cy];
            if (ss < width - ss) {
                t.fill(ctx, x + ss, y + cy, x + width - ss, y + cy + 1, color);
                t.fill(ctx, x + ss, y + height - 1 - cy, x + width - ss, y + height - cy, color);
            }
        }

        // Draw central body
        if (y + ir < y + height - ir) {
            t.fill(ctx, x, y + ir, x + width, y + height - ir, color);
        }

        // Draw anti-aliased corner edge pixels
        for (int i = 0; i < cd.fillEdgeCx.length; i++) {
            int cx = cd.fillEdgeCx[i];
            int cy = cd.fillEdgeCy[i];
            int newColor = (((int) (a * cd.fillEdgeAlpha[i])) << 24) | rgb;

            t.fill(ctx, x + cx, y + cy, x + cx + 1, y + cy + 1, newColor);
            t.fill(ctx, x + width - 1 - cx, y + cy, x + width - cx, y + cy + 1, newColor);
            t.fill(ctx, x + cx, y + height - 1 - cy, x + cx + 1, y + height - cy, newColor);
            t.fill(ctx, x + width - 1 - cx, y + height - 1 - cy, x + width - cx, y + height - cy, newColor);
        }
    }

    /** The original pixel-by-pixel implementation (reference and fallback for degenerate sizes). */
    public static <C> void outlinePerPixel(Target<C> t, C ctx, int x, int y, int width, int height, int color, float radius) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) return;
        int rgb = color & 0x00FFFFFF;

        CornerData cd = getCornerData(radius, width, height);
        int ir = cd.ir;

        // Draw straight edges
        t.fill(ctx, x + ir, y, x + width - ir, y + 1, color);
        t.fill(ctx, x + ir, y + height - 1, x + width - ir, y + height, color);
        t.fill(ctx, x, y + ir, x + 1, y + height - ir, color);
        t.fill(ctx, x + width - 1, y + ir, x + width, y + height - ir, color);

        // Draw outline corners
        for (int i = 0; i < cd.outlineCx.length; i++) {
            int cx = cd.outlineCx[i];
            int cy = cd.outlineCy[i];
            int newColor = (((int) (a * cd.outlineAlpha[i])) << 24) | rgb;

            t.fill(ctx, x + cx, y + cy, x + cx + 1, y + cy + 1, newColor);
            t.fill(ctx, x + width - 1 - cx, y + cy, x + width - cx, y + cy + 1, newColor);
            t.fill(ctx, x + cx, y + height - 1 - cy, x + cx + 1, y + height - cy, newColor);
            t.fill(ctx, x + width - 1 - cx, y + height - 1 - cy, x + width - cx, y + height - cy, newColor);
        }
    }
}
