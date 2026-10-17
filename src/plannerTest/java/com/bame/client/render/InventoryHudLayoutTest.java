package com.bame.client.render;

import java.util.HashSet;
import java.util.Set;

/**
 * Prüft ohne Minecraft die Rechnung der Inventar-Vorschau ({@link InventoryHudLayout}): Optionen, Deckkraft,
 * Zuordnung Zeile/Spalte → Inventar-Slot, Maße je Stil (alle Items im Element, nichts überlappt, Raster wie Vanilla)
 * sowie Position (automatisch unten rechts, frei, Begrenzung an allen Rändern).
 * Gradle-Aufgabe {@code inventoryHudTest} (läuft mit {@code check}/{@code build}).
 */
public final class InventoryHudLayoutTest {
    private static int checks = 0;

    private static final int[][] SCREENS = {
            {320, 240}, {321, 241}, {427, 240}, {480, 270}, {640, 360}, {683, 384}, {854, 480}, {960, 540},
            {1280, 720}, {1920, 1080}
    };
    private static final float[] SCALES = {0.5f, 0.66f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f};
    private static final boolean[] BOOLS = {true, false};
    private static final int[] STYLES = {InventoryHudLayout.STYLE_HOTBAR, InventoryHudLayout.STYLE_ITEMS};

    public static void main(String[] args) {
        options();
        opacity();
        slotMapping();
        metricsInsideAndNoOverlap();
        metricsSpecifics();
        placement();
        System.out.println("INVENTORY HUD TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void options() {
        check(InventoryHudLayout.STYLE_DEFAULT == InventoryHudLayout.STYLE_HOTBAR, "default style is the hotbar look");
        check(InventoryHudLayout.sanitizeStyle(-1) == InventoryHudLayout.STYLE_DEFAULT, "negative style -> default");
        check(InventoryHudLayout.sanitizeStyle(2) == InventoryHudLayout.STYLE_DEFAULT, "unknown style -> default");
        check(InventoryHudLayout.sanitizeStyle(InventoryHudLayout.STYLE_ITEMS) == InventoryHudLayout.STYLE_ITEMS, "valid style kept");
        check("Hotbar".equals(InventoryHudLayout.styleName(0)) && "Items only".equals(InventoryHudLayout.styleName(1)), "style names");
        check("Hotbar".equals(InventoryHudLayout.styleName(99)), "unknown style shows the default name");
        int count = InventoryHudLayout.STYLE_COUNT;
        for (int v = 0; v < count; v++) {
            check(InventoryHudLayout.cycle(v, count, false) == (v + 1) % count, "cycle forward " + v);
            check(InventoryHudLayout.cycle(v, count, true) == (v + count - 1) % count, "cycle backward " + v);
        }
        check(InventoryHudLayout.cycle(-3, count, false) == 1, "invalid value counts as 0");
    }

    private static void opacity() {
        check(InventoryHudLayout.sanitizeOpacity(Float.NaN) == InventoryHudLayout.OPACITY_DEFAULT, "NaN -> default");
        check(InventoryHudLayout.sanitizeOpacity(Float.POSITIVE_INFINITY) == InventoryHudLayout.OPACITY_DEFAULT, "inf -> default");
        check(InventoryHudLayout.sanitizeOpacity(0f) == 0.10f, "lower limit 10 %");
        check(InventoryHudLayout.sanitizeOpacity(-2f) == 0.10f, "negative -> 10 %");
        check(InventoryHudLayout.sanitizeOpacity(5f) == 1.0f, "upper limit 100 %");
        check(InventoryHudLayout.sanitizeOpacity(0.42f) == 0.40f, "rounds to 5 % steps (down)");
        check(InventoryHudLayout.sanitizeOpacity(0.43f) == 0.45f, "rounds to 5 % steps (up)");
        for (int i = -100; i <= 1200; i++) {
            float o = i / 1000f;
            float s = InventoryHudLayout.sanitizeOpacity(o);
            int pct = Math.round(s * 100f);
            check(s >= 0.10f && s <= 1.0f, "opacity in range " + o);
            check(pct % 5 == 0 && Math.abs(s * 100f - pct) < 1e-3, "opacity on a 5 % step " + o + " -> " + s);
            check(InventoryHudLayout.sanitizeOpacity(s) == s, "sanitize is idempotent " + s);
            check(InventoryHudLayout.opacityPercent(o) == pct, "percent label " + o);
        }
        check(InventoryHudLayout.opacityFromSlider(0.0) == 0.10f, "slider left = 10 %");
        check(InventoryHudLayout.opacityFromSlider(1.0) == 1.0f, "slider right = 100 %");
        check(InventoryHudLayout.opacityFromSlider(-3.0) == 0.10f && InventoryHudLayout.opacityFromSlider(9.0) == 1.0f, "slider clamped");
        check(InventoryHudLayout.opacityFromSlider(Double.NaN) == 1.0f, "slider NaN -> 100 %");
        for (int pct = 10; pct <= 100; pct += 5) {
            float o = pct / 100f;
            float slider = InventoryHudLayout.opacitySliderValue(o);
            check(slider >= 0f && slider <= 1f, "slider value in range " + pct);
            check(InventoryHudLayout.opacityFromSlider(slider) == InventoryHudLayout.sanitizeOpacity(o), "slider round trip " + pct);
        }
        // Deckkraft auf Farben: nur Alpha skaliert, Farbe bleibt
        check(InventoryHudLayout.applyOpacity(0xFF123456, 1f) == 0xFF123456, "full opacity unchanged");
        check(InventoryHudLayout.applyOpacity(0xFF123456, 0.5f) == 0x80123456, "half opacity");
        check(InventoryHudLayout.applyOpacity(0x80FFFFFF, 0.5f) == 0x40FFFFFF, "half of half");
        check((InventoryHudLayout.applyOpacity(0xFFABCDEF, 0f) >>> 24) == 0, "zero opacity");
        check(InventoryHudLayout.applyOpacity(0xFFABCDEF, Float.NaN) == 0xFFABCDEF, "NaN opacity leaves the colour");
        int[] colors = {0xFFFFFFFF, 0x880D1117, 0x28FFFFFF, 0x00123456, 0xC0050505, 0x7F7F7F7F};
        for (int color : colors) {
            int last = -1;
            for (int i = 0; i <= 100; i++) {
                int c = InventoryHudLayout.applyOpacity(color, i / 100f);
                int a = c >>> 24;
                check((c & 0x00FFFFFF) == (color & 0x00FFFFFF), "rgb preserved " + Integer.toHexString(color));
                check(a <= (color >>> 24), "never more opaque than the original");
                check(a >= last, "alpha monotonic in opacity");
                last = a;
            }
        }
    }

    private static void slotMapping() {
        check(InventoryHudLayout.rows(false) == 3 && InventoryHudLayout.rows(true) == 4, "row counts");
        check(InventoryHudLayout.MAX_SLOTS == 36, "36 slots with hotbar");
        int expected = 9;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                check(InventoryHudLayout.slotIndex(row, col) == expected, "main slot " + row + "/" + col);
                expected++;
            }
        }
        for (int col = 0; col < 9; col++) {
            check(InventoryHudLayout.slotIndex(3, col) == col, "hotbar row maps to slots 0-8");
        }
        for (boolean hotbar : BOOLS) {
            Set<Integer> seen = new HashSet<>();
            for (int row = 0; row < InventoryHudLayout.rows(hotbar); row++) {
                for (int col = 0; col < 9; col++) seen.add(InventoryHudLayout.slotIndex(row, col));
            }
            check(seen.size() == (hotbar ? 36 : 27), "all slots unique (hotbar " + hotbar + ")");
            for (int s : seen) check(s >= (hotbar ? 0 : 9) && s <= 35, "slot index in the player inventory " + s);
        }
    }

    private static void metricsInsideAndNoOverlap() {
        InventoryHudLayout.Metrics m = new InventoryHudLayout.Metrics();
        for (int style : STYLES) {
            for (boolean hotbar : BOOLS) {
                for (boolean bg : BOOLS) {
                    m.compute(style, hotbar, bg);
                    String c = " [style " + style + " hotbar " + hotbar + " bg " + bg + "]";
                    int rows = m.rows();
                    check(rows == (hotbar ? 4 : 3), "rows" + c);
                    check(m.pad == (bg ? InventoryHudLayout.BG_PAD : 0), "padding only with background" + c);
                    check(m.width == m.gridW + 2 * m.pad, "width = grid + padding" + c);
                    int bottom = hotbar ? m.hotbarY + m.hotbarH : m.gridY + m.mainH;
                    check(m.height == bottom + m.pad, "height = content + padding" + c);
                    int n = rows * 9;
                    int[] xs = new int[n];
                    int[] ys = new int[n];
                    int k = 0;
                    for (int row = 0; row < rows; row++) {
                        for (int col = 0; col < 9; col++) {
                            int ix = m.itemX(col);
                            int iy = m.itemY(row);
                            check(ix >= 0 && iy >= 0 && ix + 16 <= m.width && iy + 16 <= m.height, "item inside the element" + c);
                            check(ix >= m.gridX && ix + 16 <= m.gridX + m.gridW, "item inside the grid horizontally" + c);
                            if (row < 3) check(iy >= m.gridY && iy + 16 <= m.gridY + m.mainH, "main item inside the main grid" + c);
                            else check(iy >= m.hotbarY && iy + 16 <= m.hotbarY + m.hotbarH, "hotbar item inside the hotbar row" + c);
                            xs[k] = ix;
                            ys[k] = iy;
                            k++;
                        }
                    }
                    for (int a = 0; a < n; a++) {
                        for (int b = a + 1; b < n; b++) {
                            boolean overlap = xs[a] < xs[b] + 16 && xs[b] < xs[a] + 16 && ys[a] < ys[b] + 16 && ys[b] < ys[a] + 16;
                            check(!overlap, "items do not overlap" + c);
                        }
                    }
                    int cell = style == InventoryHudLayout.STYLE_ITEMS ? 18 : 20;
                    check(m.cell == cell, "cell pitch" + c);
                    for (int col = 0; col < 8; col++) check(m.cellX(col + 1) - m.cellX(col) == cell, "column pitch" + c);
                    for (int row = 0; row < 2; row++) check(m.cellY(row + 1) - m.cellY(row) == cell, "row pitch" + c);
                    if (hotbar) {
                        check(m.itemY(3) - (m.itemY(2) + 16) >= InventoryHudLayout.HOTBAR_GAP, "gap above the hotbar row" + c);
                        check(m.hotbarY - (m.gridY + m.mainH) == InventoryHudLayout.HOTBAR_GAP, "hotbar row 4 px below" + c);
                    }
                    if (style == InventoryHudLayout.STYLE_HOTBAR) {
                        // Vanilla-Hotbar: 182 px breit, Item bei 3 + 20 * Spalte, 3 px unter der Sprite-Oberkante
                        check(m.gridW == 182, "hotbar sprite width" + c);
                        for (int col = 0; col < 9; col++) check(m.itemX(col) - m.gridX == 3 + 20 * col, "vanilla item x" + c);
                        for (int row = 0; row < 3; row++) check(m.itemY(row) - (m.gridY + 20 * row) == 3, "vanilla item y in sprite row" + c);
                        if (hotbar) check(m.itemY(3) - m.hotbarY == 3 && m.hotbarH == 22, "hotbar row like the vanilla hotbar" + c);
                        check(m.mainH == 3 * 20 + 2, "rows share the 2 px border" + c);
                    }
                }
            }
        }
    }

    private static void metricsSpecifics() {
        InventoryHudLayout.Metrics m = new InventoryHudLayout.Metrics();
        m.compute(InventoryHudLayout.STYLE_HOTBAR, false, false);
        check(m.width == 182 && m.height == 62, "hotbar look 3x9: 182x62");
        m.compute(InventoryHudLayout.STYLE_HOTBAR, true, false);
        check(m.width == 182 && m.height == 88, "hotbar look with hotbar row: 182x88");
        m.compute(InventoryHudLayout.STYLE_ITEMS, false, false);
        check(m.width == 162 && m.height == 54, "items only 3x9: 162x54");
        m.compute(InventoryHudLayout.STYLE_ITEMS, true, false);
        check(m.width == 162 && m.height == 76, "items only with hotbar row: 162x76");
        m.compute(InventoryHudLayout.STYLE_HOTBAR, true, true);
        check(m.width == 188 && m.height == 94, "background adds 3 px on each side");
        m.compute(7, false, false);
        check(m.style == InventoryHudLayout.STYLE_HOTBAR && m.width == 182, "unknown style is drawn as the default");
        // wiederverwendbar: gleiche Instanz, anderer Stil → keine Reste
        m.compute(InventoryHudLayout.STYLE_ITEMS, false, true);
        check(m.cell == 18 && m.pad == 3 && m.width == 168 && m.height == 60, "reused metrics fully recomputed");
    }

    private static void placement() {
        check(InventoryHudLayout.sanitizeScale(Float.NaN) == 1f && InventoryHudLayout.sanitizeScale(0f) == 1f
                && InventoryHudLayout.sanitizeScale(-2f) == 1f && InventoryHudLayout.sanitizeScale(Float.POSITIVE_INFINITY) == 1f,
                "broken scale -> 100 %");
        check(InventoryHudLayout.sanitizeScale(0.75f) == 0.75f, "valid scale kept");
        InventoryHudLayout.Metrics m = new InventoryHudLayout.Metrics();
        int[] stored = {-100000, -500, -2, -1, 0, 1, 7, 100, 333, 900, 5000, 100000};
        for (int[] screen : SCREENS) {
            int w = screen[0], h = screen[1];
            for (float s : SCALES) {
                for (int style : STYLES) {
                    for (boolean hotbar : BOOLS) {
                        m.compute(style, hotbar, true);
                        int sw = InventoryHudLayout.scaledSize(m.width, s);
                        int sh = InventoryHudLayout.scaledSize(m.height, s);
                        check(sw == (int) Math.ceil(m.width * s) && sh == (int) Math.ceil(m.height * s), "scaled size rounds up");
                        String c = " [" + w + "x" + h + " scale " + s + " style " + style + " hotbar " + hotbar + "]";
                        int ax = InventoryHudLayout.freeX(-1, w, sw);
                        int ay = InventoryHudLayout.freeY(-1, h, sh);
                        if (sw + InventoryHudLayout.AUTO_MARGIN <= w) {
                            check(ax + sw == w - InventoryHudLayout.AUTO_MARGIN, "auto: right edge with margin" + c);
                        }
                        if (sh + InventoryHudLayout.AUTO_MARGIN <= h) {
                            check(ay + sh == h - InventoryHudLayout.AUTO_MARGIN, "auto: bottom edge with margin" + c);
                        }
                        for (int sx : stored) {
                            int x = InventoryHudLayout.freeX(sx, w, sw);
                            if (sw <= w) {
                                check(x >= 0 && x + sw <= w, "x inside the screen" + c + " stored " + sx);
                                if (sx != -1 && sx >= 0 && sx + sw <= w) check(x == sx, "valid stored x unchanged" + c);
                            } else {
                                check(x == 0, "too wide: left edge" + c);
                            }
                        }
                        for (int sy : stored) {
                            int y = InventoryHudLayout.freeY(sy, h, sh);
                            if (sh <= h) {
                                check(y >= 0 && y + sh <= h, "y inside the screen" + c + " stored " + sy);
                                if (sy != -1 && sy >= 0 && sy + sh <= h) check(y == sy, "valid stored y unchanged" + c);
                            } else {
                                check(y == 0, "too tall: top edge" + c);
                            }
                        }
                    }
                }
            }
        }
    }
}
