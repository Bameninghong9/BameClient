package com.bame.client.gui;

import com.bame.client.module.CustomCrosshairModule;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Dependency-free pixel checks for the GUI draw-call reduction, run by the guiGeometryTest Gradle task.
 *
 * <p>Rounded rectangles: the texture-corner path ({@link RoundedRectGeometry#fill}/{@link RoundedRectGeometry#outline})
 * is rasterised in software next to the original per-pixel path and every pixel is compared, for all corner radii
 * (1-16 and fractional/oversized values), sizes 0-40 x 0-40 (pills, odd sizes, 3x3 ...) and several colour alphas.
 * Blending model: GUI pipelines use straight-alpha TRANSLUCENT blending; a fill contributes alpha A/255, a tinted
 * texel T contributes (T/255) * (A/255) (position_tex_color: texture * vertex colour).
 *
 * <p>Crosshair: run-length merging covers exactly the same cells as one fill per cell; the preset cache matches.
 */
public final class GuiGeometryTest {
    private static final int CANVAS = 44;
    private static final int ORIGIN = 2;

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Software rasteriser: one white channel over a black background. */
    private static final class Canvas implements RoundedRectGeometry.Target<Object> {
        final double[] value = new double[CANVAS * CANVAS];
        final Map<Long, int[]> textures;
        int elements;
        // pixels may only be touched inside [-1, w + 1) x [-1, h + 1) of the rectangle being drawn
        int limitW, limitH;

        Canvas(Map<Long, int[]> textures) {
            this.textures = textures;
        }

        void reset(int w, int h) {
            for (int py = 0; py < Math.min(CANVAS, ORIGIN + limitH + 1); py++) {
                java.util.Arrays.fill(value, py * CANVAS, py * CANVAS + Math.min(CANVAS, ORIGIN + limitW + 1), 0.0);
            }
            limitW = w;
            limitH = h;
            elements = 0;
        }

        private void blend(int px, int py, double alpha) {
            check(px >= ORIGIN - 1 && py >= ORIGIN - 1 && px < ORIGIN + limitW + 1 && py < ORIGIN + limitH + 1,
                    "pixel outside the rectangle: " + (px - ORIGIN) + "," + (py - ORIGIN));
            int i = py * CANVAS + px;
            value[i] = alpha + value[i] * (1.0 - alpha);
        }

        @Override
        public void fill(Object ctx, int x1, int y1, int x2, int y2, int color) {
            elements++;
            // DrawContext.fill accepts both corner orders
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            double alpha = ((color >>> 24) & 0xFF) / 255.0;
            if (alpha == 0) return;
            for (int py = minY; py < maxY; py++) {
                for (int px = minX; px < maxX; px++) {
                    blend(ORIGIN + px, ORIGIN + py, alpha);
                }
            }
        }

        @Override
        public void corner(Object ctx, boolean outline, int ir, int x, int y, int u, int v, int w, int h, int color) {
            elements++;
            int size = 2 * ir;
            check(u >= 0 && v >= 0 && w >= 0 && h >= 0 && u + w <= size && v + h <= size, "UV region outside corner texture");
            int[] tex = textures.computeIfAbsent(((long) ir << 1) | (outline ? 1 : 0),
                    k -> RoundedRectGeometry.buildCornerAlpha(ir, outline));
            check(tex.length == size * size, "corner texture size");
            double colorAlpha = ((color >>> 24) & 0xFF) / 255.0;
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    double alpha = tex[(v + j) * size + (u + i)] / 255.0 * colorAlpha;
                    if (alpha == 0) continue; // fragment shader discards alpha 0
                    blend(ORIGIN + x + i, ORIGIN + y + j, alpha);
                }
            }
        }
    }

    public static void main(String[] args) {
        testRoundedRects();
        testCrosshairRuns();
        testPresetCache();
        testGridLineMask();
        System.out.println("ALL GUI GEOMETRY TESTS PASSED!");
    }

    static void testRoundedRects() {
        Map<Long, int[]> textures = new HashMap<>();
        float[] radii = {0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f, 13f, 14f, 15f, 16f, 20f};
        int[] colors = {0xFFFFFFFF, 0xF7FFFFFF, 0xD0FFFFFF, 0x80FFFFFF, 0x45FFFFFF, 0x14FFFFFF, 0x01FFFFFF};
        double maxTranslucentDiff = 0, maxRegularDiff = 0;
        int maxFillElements = 0, maxOutlineElements = 0, cases = 0;
        Canvas reference = new Canvas(textures);
        Canvas fast = new Canvas(textures);

        for (float radius : radii) {
            for (int w = 0; w <= 40; w++) {
                for (int h = 0; h <= 40; h++) {
                    for (int color : colors) {
                        for (int outline = 0; outline < 2; outline++) {
                            reference.reset(w, h);
                            fast.reset(w, h);
                            if (outline == 1) {
                                RoundedRectGeometry.outlinePerPixel(reference, null, 0, 0, w, h, color, radius);
                                RoundedRectGeometry.outline(fast, null, 0, 0, w, h, color, radius);
                            } else {
                                RoundedRectGeometry.fillPerPixel(reference, null, 0, 0, w, h, color, radius);
                                RoundedRectGeometry.fill(fast, null, 0, 0, w, h, color, radius);
                            }
                            boolean opaque = (color >>> 24) == 0xFF;
                            int ir = RoundedRectGeometry.cornerRadius(radius, w, h);
                            boolean shared = w <= 2 * ir || h <= 2 * ir;
                            for (int py = 0; py < ORIGIN + h + 1; py++) for (int px = 0; px < ORIGIN + w + 1; px++) {
                                int i = py * CANVAS + px;
                                double diff = Math.abs(reference.value[i] - fast.value[i]);
                                if (opaque) {
                                    check(diff < 1e-9, "opaque mismatch r=" + radius + " " + w + "x" + h + (outline == 1 ? " outline" : " fill")
                                            + " pixel " + (i % CANVAS - ORIGIN) + "," + (i / CANVAS - ORIGIN)
                                            + " expected " + reference.value[i] + " got " + fast.value[i]);
                                } else {
                                    maxTranslucentDiff = Math.max(maxTranslucentDiff, diff);
                                    if (!shared) maxRegularDiff = Math.max(maxRegularDiff, diff);
                                    // below 1/255 per draw; a pixel is drawn up to 4 times when width and height are both 2 * radius - 1
                                    check(diff <= 4.0 / 255.0 + 1e-9, "translucent deviation too large r=" + radius + " " + w + "x" + h
                                            + " color " + Integer.toHexString(color) + (outline == 1 ? " outline" : " fill"));
                                }
                            }
                            if (w >= 2 * ir - 1 && h >= 2 * ir - 1) {
                                if (outline == 1) maxOutlineElements = Math.max(maxOutlineElements, fast.elements);
                                else maxFillElements = Math.max(maxFillElements, fast.elements);
                            }
                            cases++;
                        }
                    }
                }
            }
        }
        check(maxFillElements <= 9, "fill uses too many elements: " + maxFillElements);
        check(maxOutlineElements <= 8, "outline uses too many elements: " + maxOutlineElements);
        System.out.printf(java.util.Locale.ROOT,
                "Rounded rects: %d cases, opaque colours pixel-identical, translucent max deviation %.3f/255 "
                        + "(%.3f/255 when width and height > 2 * radius), max elements fill %d / outline %d%n",
                cases, maxTranslucentDiff * 255.0, maxRegularDiff * 255.0, maxFillElements, maxOutlineElements);
    }

    static void testCrosshairRuns() {
        Random random = new Random(42);
        int size = CustomCrosshairModule.GRID_SIZE;
        for (int iteration = 0; iteration < 2000; iteration++) {
            boolean[][] grid = new boolean[size][size];
            double density = random.nextDouble();
            for (int r = 0; r < size; r++) for (int c = 0; c < size; c++) grid[r][c] = random.nextDouble() < density;
            for (boolean value : new boolean[]{true, false}) {
                int[][] covered = new int[size][size];
                int[] lastEnd = {-1, -1};
                CustomCrosshairModule.forEachRun(grid, value, (row, c0, c1) -> {
                    check(c0 < c1, "empty run");
                    check(c0 > 0 ? grid[row][c0 - 1] != value : true, "run not maximal on the left");
                    check(c1 < size ? grid[row][c1] != value : true, "run not maximal on the right");
                    for (int c = c0; c < c1; c++) covered[row][c]++;
                });
                for (int r = 0; r < size; r++) {
                    for (int c = 0; c < size; c++) {
                        check(covered[r][c] == (grid[r][c] == value ? 1 : 0), "run coverage differs from per-cell fills");
                    }
                }
            }
        }
        System.out.println("Crosshair runs: same cells as one fill per cell (2000 random grids)");
    }

    static void testPresetCache() {
        int size = CustomCrosshairModule.GRID_SIZE;
        for (int i = -1; i <= CustomCrosshairModule.PRESET_COUNT; i++) {
            boolean[][] copy = CustomCrosshairModule.getPresetTemplate(i);
            boolean[][] fromRuns = new boolean[size][size];
            CustomCrosshairModule.forEachPresetRun(i, (row, c0, c1) -> {
                for (int c = c0; c < c1; c++) fromRuns[row][c] = true;
            });
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) check(copy[r][c] == fromRuns[r][c], "preset runs differ from template " + i);
            }
            // callers may modify their copy without touching the cache
            for (boolean[] row : copy) java.util.Arrays.fill(row, true);
            boolean[][] again = CustomCrosshairModule.getPresetTemplate(i);
            check(again != copy, "template copy must be a new array");
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) check(again[r][c] == fromRuns[r][c], "preset cache was modified through a copy");
            }
        }
        // preset 1 (plus) as a spot check against the hand-written shape
        boolean[][] plus = CustomCrosshairModule.getPresetTemplate(1);
        for (int i = 0; i < size; i++) {
            check(plus[7][i] == (i >= 2 && i <= 12), "plus preset row");
            check(plus[i][7] == (i >= 2 && i <= 12), "plus preset column");
        }
        System.out.println("Crosshair presets: cached templates, copies and runs consistent");
    }

    /** The editor grid lines: each cell's bottom row and right column (minus its last pixel) drawn once per pixel. */
    static void testGridLineMask() {
        int cell = 7, pixels = 15 * cell;
        int[][] count = new int[pixels][pixels];
        for (int r = 0; r < 15; r++) {
            for (int c = 0; c < 15; c++) {
                int cx = c * cell, cy = r * cell;
                for (int x = cx; x < cx + cell; x++) count[cy + cell - 1][x]++;   // old bottom line fill
                for (int y = cy; y < cy + cell - 1; y++) count[y][cx + cell - 1]++; // old right line fill
            }
        }
        for (int y = 0; y < pixels; y++) {
            for (int x = 0; x < pixels; x++) {
                boolean mask = x % cell == cell - 1 || y % cell == cell - 1; // RenderTab GRID_LINES mask
                check(count[y][x] == (mask ? 1 : 0), "grid line mask differs at " + x + "," + y);
            }
        }
        System.out.println("Crosshair grid lines: texture mask equals the old per-cell line fills");
    }
}
