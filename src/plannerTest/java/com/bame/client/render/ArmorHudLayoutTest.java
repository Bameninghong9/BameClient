package com.bame.client.render;

/**
 * Prüft ohne Minecraft die reine Rechnung des Armor HUD ({@link ArmorHudLayout}): Optionen und Zyklus, Prozent- und
 * Zahlen-Formatierung, Vanilla-Balken, Farbverlauf, Warnschwelle und Puls, Maße je Stil (alles im Element, nichts
 * überlappt) sowie freie Position und Hotbar-Anker inklusive Begrenzung am Bildschirmrand.
 * Gradle-Aufgabe {@code armorHudTest} (läuft mit {@code check}/{@code build}).
 */
public final class ArmorHudLayoutTest {
    private static int checks = 0;

    private static final int[][] SCREENS = {
            {320, 240}, {321, 241}, {427, 240}, {480, 270}, {640, 360}, {683, 384}, {854, 480}, {960, 540},
            {1280, 720}, {1920, 1080}
    };
    private static final float[] HOTBAR_SCALES = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f};
    private static final int[] TEXT_WIDTHS = {0, 9, 15, 20, 23, 31, 47, 60};
    private static final boolean[] BOOLS = {true, false};

    public static void main(String[] args) {
        options();
        percentRules();
        texts();
        vanillaBar();
        colorGradient();
        warning();
        metricsInsideAndNoOverlap();
        metricsSpecifics();
        freePlacement();
        hotbarEdgesMatchHotbarLayout();
        anchorPlacement();
        System.out.println("ARMOR HUD TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void options() {
        check(ArmorHudLayout.STYLE_DEFAULT == ArmorHudLayout.STYLE_VERTICAL, "default style is the old vertical look");
        check(ArmorHudLayout.DUR_DEFAULT == ArmorHudLayout.DUR_PERCENT, "default durability is percent (old look)");
        check(ArmorHudLayout.sanitizeStyle(-1) == ArmorHudLayout.STYLE_DEFAULT, "negative style -> default");
        check(ArmorHudLayout.sanitizeStyle(99) == ArmorHudLayout.STYLE_DEFAULT, "unknown style -> default");
        check(ArmorHudLayout.sanitizeStyle(ArmorHudLayout.STYLE_MINIMAL) == ArmorHudLayout.STYLE_MINIMAL, "valid style kept");
        check(ArmorHudLayout.sanitizeDurability(6) == ArmorHudLayout.DUR_DEFAULT, "unknown durability mode -> default");
        check(ArmorHudLayout.sanitizeDurability(ArmorHudLayout.DUR_OFF) == ArmorHudLayout.DUR_OFF, "valid durability kept");
        check(ArmorHudLayout.sanitizeOrder(2) == ArmorHudLayout.ORDER_HELMET_FIRST, "unknown order -> helmet first");
        check(ArmorHudLayout.sanitizeAnchor(3) == ArmorHudLayout.ANCHOR_FREE, "unknown anchor -> free");
        check(ArmorHudLayout.sanitizeAnchor(ArmorHudLayout.ANCHOR_HOTBAR_RIGHT) == ArmorHudLayout.ANCHOR_HOTBAR_RIGHT, "valid anchor kept");

        check(ArmorHudLayout.sanitizeThreshold(20) == 20, "default threshold kept");
        check(ArmorHudLayout.sanitizeThreshold(0) == 10, "threshold lower limit");
        check(ArmorHudLayout.sanitizeThreshold(99) == 50, "threshold upper limit");
        check(ArmorHudLayout.sanitizeThreshold(22) == 20, "threshold rounds to 5 steps (down)");
        check(ArmorHudLayout.sanitizeThreshold(23) == 25, "threshold rounds to 5 steps (up)");
        for (int t = -50; t <= 150; t++) {
            int s = ArmorHudLayout.sanitizeThreshold(t);
            check(s >= 10 && s <= 50 && s % 5 == 0, "threshold " + t + " -> " + s + " valid");
            check(ArmorHudLayout.sanitizeThreshold(s) == s, "threshold sanitize is idempotent");
        }

        int[] counts = {ArmorHudLayout.STYLE_COUNT, ArmorHudLayout.DUR_COUNT, ArmorHudLayout.ORDER_COUNT, ArmorHudLayout.ANCHOR_COUNT};
        for (int count : counts) {
            for (int v = 0; v < count; v++) {
                int next = ArmorHudLayout.cycle(v, count, false);
                int prev = ArmorHudLayout.cycle(v, count, true);
                check(next == (v + 1) % count, "cycle forward " + v + "/" + count);
                check(prev == (v + count - 1) % count, "cycle backward (right click) " + v + "/" + count);
                check(ArmorHudLayout.cycle(next, count, true) == v, "back undoes forward");
            }
            check(ArmorHudLayout.cycle(-7, count, false) == 1 % count, "invalid value counts as 0 (forward)");
            check(ArmorHudLayout.cycle(count + 3, count, true) == count - 1, "invalid value counts as 0 (backward)");
        }
        check("Slots".equals(ArmorHudLayout.styleName(0)) && "Vertical".equals(ArmorHudLayout.styleName(1))
                && "Compact".equals(ArmorHudLayout.styleName(2)) && "Minimal".equals(ArmorHudLayout.styleName(3)), "style names");
        check("Vanilla".equals(ArmorHudLayout.durabilityName(0)) && "Percent".equals(ArmorHudLayout.durabilityName(1))
                && "Numbers".equals(ArmorHudLayout.durabilityName(2)) && "Numbers/Max".equals(ArmorHudLayout.durabilityName(3))
                && "Bar + Percent".equals(ArmorHudLayout.durabilityName(4)) && "Off".equals(ArmorHudLayout.durabilityName(5)),
                "durability names");
        check("Free".equals(ArmorHudLayout.anchorName(0)) && "Hotbar left".equals(ArmorHudLayout.anchorName(1))
                && "Hotbar right".equals(ArmorHudLayout.anchorName(2)), "anchor names");
        check("Helmet first".equals(ArmorHudLayout.orderName(0)) && "Boots first".equals(ArmorHudLayout.orderName(1)), "order names");

        check(ArmorHudLayout.showsText(ArmorHudLayout.DUR_PERCENT) && ArmorHudLayout.showsText(ArmorHudLayout.DUR_NUMBERS)
                && ArmorHudLayout.showsText(ArmorHudLayout.DUR_NUMBERS_MAX) && ArmorHudLayout.showsText(ArmorHudLayout.DUR_BAR_PERCENT),
                "text modes");
        check(!ArmorHudLayout.showsText(ArmorHudLayout.DUR_VANILLA) && !ArmorHudLayout.showsText(ArmorHudLayout.DUR_OFF), "no text");
        check(ArmorHudLayout.showsBar(ArmorHudLayout.DUR_VANILLA) && ArmorHudLayout.showsBar(ArmorHudLayout.DUR_BAR_PERCENT), "bar modes");
        check(!ArmorHudLayout.showsBar(ArmorHudLayout.DUR_PERCENT) && !ArmorHudLayout.showsBar(ArmorHudLayout.DUR_NUMBERS)
                && !ArmorHudLayout.showsBar(ArmorHudLayout.DUR_NUMBERS_MAX) && !ArmorHudLayout.showsBar(ArmorHudLayout.DUR_OFF), "no bar");
    }

    private static void percentRules() {
        check(ArmorHudLayout.percent(363, 363) == 100, "full = 100");
        check(ArmorHudLayout.percent(0, 363) == 0, "broken = 0");
        check(ArmorHudLayout.percent(1, 363) == 1, "almost broken never shows 0%");
        check(ArmorHudLayout.percent(362, 363) == 99, "scratched never shows 100%");
        check(ArmorHudLayout.percent(1, 2031) == 1, "1/2031 shows 1%");
        check(ArmorHudLayout.percent(2030, 2031) == 99, "2030/2031 shows 99%");
        check(ArmorHudLayout.percent(231, 363) == 64, "231/363 = 63.6 -> 64");
        check(ArmorHudLayout.percent(316, 363) == 87, "316/363 = 87.05 -> 87");
        check(ArmorHudLayout.percent(5, 10) == 50, "exact half");
        check(ArmorHudLayout.percent(10, 0) == 100, "no maximum: treated as full");
        check(ArmorHudLayout.percent(-5, 100) == 0, "negative remaining = 0");
        check(ArmorHudLayout.percent(150, 100) == 100, "over maximum = 100");
        int[] maxima = {1, 2, 3, 7, 55, 64, 100, 165, 240, 363, 407, 432, 481, 528, 592, 1561, 2031, 99999};
        for (int max : maxima) {
            int last = -1;
            for (int r = 0; r <= max; r += Math.max(1, max / 500)) {
                int p = ArmorHudLayout.percent(r, max);
                check(p >= last, "percent is monotonic for max " + max + " at " + r);
                check(p >= 0 && p <= 100, "percent in range");
                if (r > 0 && r < max) check(p >= 1 && p <= 99, "partial durability stays within 1..99 (" + r + "/" + max + ")");
                last = p;
            }
            check(ArmorHudLayout.percent(max, max) == 100, "full at max " + max);
        }
        check(ArmorHudLayout.remaining(0, 363) == 363, "remaining undamaged");
        check(ArmorHudLayout.remaining(132, 363) == 231, "remaining after 132 damage");
        check(ArmorHudLayout.remaining(400, 363) == 0, "remaining clamps at 0");
        check(ArmorHudLayout.remaining(-5, 363) == 363, "remaining clamps at max");
        check(ArmorHudLayout.remaining(3, 0) == 0, "remaining without maximum");
        check(Math.abs(ArmorHudLayout.fraction(231, 363) - 231 / 363.0) < 1e-12, "fraction");
        check(ArmorHudLayout.fraction(1, 0) == 1.0, "fraction without maximum = full");
    }

    private static void texts() {
        check("87%".equals(ArmorHudLayout.durabilityText(ArmorHudLayout.DUR_PERCENT, 316, 363)), "percent text");
        check("100%".equals(ArmorHudLayout.durabilityText(ArmorHudLayout.DUR_BAR_PERCENT, 363, 363)), "bar+percent text");
        check("231".equals(ArmorHudLayout.durabilityText(ArmorHudLayout.DUR_NUMBERS, 231, 363)), "numbers text");
        check("231/363".equals(ArmorHudLayout.durabilityText(ArmorHudLayout.DUR_NUMBERS_MAX, 231, 363)), "numbers/max text");
        check("0/2031".equals(ArmorHudLayout.durabilityText(ArmorHudLayout.DUR_NUMBERS_MAX, 0, 2031)), "numbers/max broken");
        check(ArmorHudLayout.durabilityText(ArmorHudLayout.DUR_VANILLA, 231, 363) == null, "vanilla bar has no text");
        check(ArmorHudLayout.durabilityText(ArmorHudLayout.DUR_OFF, 231, 363) == null, "off has no text");
        check("0%".equals(ArmorHudLayout.percentText(-3)) && "100%".equals(ArmorHudLayout.percentText(140)), "percent text clamps");
        // Cache: wiederholte Aufrufe liefern dieselbe Instanz (keine Allokation pro Bild)
        check(ArmorHudLayout.percentText(42) == ArmorHudLayout.percentText(42), "percent text cached");
        check(ArmorHudLayout.numberText(231) == ArmorHudLayout.numberText(231), "number text cached");
        check(ArmorHudLayout.durabilityText(ArmorHudLayout.DUR_NUMBERS, 9999, 9999) == ArmorHudLayout.numberText(9999), "numbers use the cache");
        check("123456".equals(ArmorHudLayout.numberText(123456)), "large numbers still formatted");
        check("-4".equals(ArmorHudLayout.numberText(-4)), "negative numbers formatted");
        for (int n = 0; n < 3000; n += 7) check(Integer.toString(n).equals(ArmorHudLayout.numberText(n)), "number text " + n);
        check(ArmorHudLayout.labelWidth(0) == 0 && ArmorHudLayout.labelWidth(-3) == 0, "empty label has no width");
        check(ArmorHudLayout.labelWidth(25) == 17, "label width scales by 0.68 (\"100%\" = 25 px -> 17 px)");
        check(ArmorHudLayout.labelWidth(20) == 14, "label width rounds up");
        check(ArmorHudLayout.labelWidth(1) == 1, "tiny label keeps 1 px");

        // längster möglicher Text: bestimmt die Breite, damit das HUD beim Abnutzen nicht springt
        check("100%".equals(ArmorHudLayout.widestDurabilityText(ArmorHudLayout.DUR_PERCENT, 363)), "widest percent");
        check("100%".equals(ArmorHudLayout.widestDurabilityText(ArmorHudLayout.DUR_BAR_PERCENT, 7)), "widest bar+percent");
        check("363".equals(ArmorHudLayout.widestDurabilityText(ArmorHudLayout.DUR_NUMBERS, 363)), "widest numbers = maximum");
        check("363/363".equals(ArmorHudLayout.widestDurabilityText(ArmorHudLayout.DUR_NUMBERS_MAX, 363)), "widest numbers/max");
        check(ArmorHudLayout.widestDurabilityText(ArmorHudLayout.DUR_VANILLA, 363) == null
                && ArmorHudLayout.widestDurabilityText(ArmorHudLayout.DUR_OFF, 363) == null, "no text, no width");
        for (int mode : new int[]{ArmorHudLayout.DUR_PERCENT, ArmorHudLayout.DUR_NUMBERS, ArmorHudLayout.DUR_NUMBERS_MAX, ArmorHudLayout.DUR_BAR_PERCENT}) {
            for (int max : new int[]{1, 9, 10, 99, 100, 363, 999, 1000, 2031, 9999, 10000}) {
                int widest = ArmorHudLayout.widestDurabilityText(mode, max).length();
                for (int r = 0; r <= max; r += Math.max(1, max / 300)) {
                    check(ArmorHudLayout.durabilityText(mode, r, max).length() <= widest,
                            "no label is longer than the widest one (mode " + mode + ", " + r + "/" + max + ")");
                }
            }
        }
    }

    /** Unabhängige Nachrechnung der Vanilla-Formeln (Item.getItemBarStep / getItemBarColor, MathHelper.hsvToRgb). */
    private static void vanillaBar() {
        check(ArmorHudLayout.vanillaBarStep(0, 363) == 13, "undamaged = full bar");
        check(ArmorHudLayout.vanillaBarStep(363, 363) == 0, "broken = empty bar");
        check(ArmorHudLayout.vanillaBarStep(500, 363) == 0, "over-damaged clamps to 0");
        int[] maxima = {7, 55, 132, 363, 432, 2031};
        for (int max : maxima) {
            int lastStep = 14;
            int lastRed = -1;
            int lastGreen = 256;
            for (int d = 0; d <= max; d++) {
                int expected = Math.max(0, Math.min(13, Math.round(13.0f - d * 13.0f / max)));
                int step = ArmorHudLayout.vanillaBarStep(d, max);
                check(step == expected, "vanilla bar step " + d + "/" + max);
                check(step <= lastStep, "bar shrinks with damage");
                lastStep = step;
                int rgb = ArmorHudLayout.vanillaBarColor(d, max);
                int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
                check((rgb & 0xFF000000) == 0, "vanilla bar color has no alpha (like getItemBarColor)");
                check(b == 0, "vanilla bar color has no blue");
                check(r >= lastRed, "red grows with damage " + d + "/" + max);
                check(g <= lastGreen, "green shrinks with damage " + d + "/" + max);
                lastRed = r;
                lastGreen = g;
            }
            check(ArmorHudLayout.vanillaBarColor(0, max) == 0x00FF00, "undamaged = pure green (" + max + ")");
            check(ArmorHudLayout.vanillaBarColor(max, max) == 0xFF0000, "broken = pure red (" + max + ")");
        }
        int half = ArmorHudLayout.vanillaBarColor(50, 100);
        check((half >> 16 & 0xFF) >= 254 && (half >> 8 & 0xFF) >= 254 && (half & 0xFF) == 0, "half = yellow");
        check(ArmorHudLayout.hsvToRgb(0f, 1f, 1f) == 0xFF0000, "hsv red");
        check(ArmorHudLayout.hsvToRgb(1f / 3f, 1f, 1f) == 0x00FF00, "hsv green");
        check(ArmorHudLayout.hsvToRgb(2f / 3f, 1f, 1f) == 0x0000FF, "hsv blue");
        check(ArmorHudLayout.hsvToRgb(0.5f, 0f, 1f) == 0xFFFFFF, "hsv white");
        check(ArmorHudLayout.hsvToRgb(0.25f, 1f, 0f) == 0x000000, "hsv black");
        check(ArmorHudLayout.barFill(14, 0, 100) == 0, "own bar empty when broken");
        check(ArmorHudLayout.barFill(14, 100, 100) == 14, "own bar full when undamaged");
        check(ArmorHudLayout.barFill(14, 1, 1000) == 1, "own bar shows at least 1 px while something is left");
        check(ArmorHudLayout.barFill(14, 999, 1000) == 13, "own bar is not full while damaged");
        check(ArmorHudLayout.barFill(14, 50, 100) == 7, "own bar half");
        check(ArmorHudLayout.barFill(0, 50, 100) == 0, "zero-width bar");
        for (int r = 0; r <= 363; r++) {
            int fill = ArmorHudLayout.barFill(22, r, 363);
            check(fill >= 0 && fill <= 22, "own bar fill in range");
        }
    }

    private static void colorGradient() {
        check(ArmorHudLayout.durabilityColor(100, 100) == ArmorHudLayout.COLOR_GOOD, "full = green");
        check(ArmorHudLayout.durabilityColor(50, 100) == ArmorHudLayout.COLOR_MID, "half = yellow");
        check(ArmorHudLayout.durabilityColor(0, 100) == ArmorHudLayout.COLOR_BAD, "empty = red");
        check(ArmorHudLayout.durabilityColor(5, 0) == ArmorHudLayout.COLOR_GOOD, "no maximum = green");
        int lastR = 0x55, lastG = 0xFF;
        for (int r = 1000; r >= 0; r--) {
            int c = ArmorHudLayout.durabilityColor(r, 1000);
            int cr = c >> 16 & 0xFF, cg = c >> 8 & 0xFF, cb = c & 0xFF;
            check((c >>> 24) == 0xFF, "gradient is opaque");
            check(cb == 0x55, "gradient keeps the Minecraft text blue");
            check(cr >= lastR, "red grows from green to yellow to red");
            check(cg <= lastG, "green shrinks towards red");
            if (r >= 500) check(cg == 0xFF, "upper half stays fully green-channel (green -> yellow)");
            if (r <= 500) check(cr == 0xFF, "lower half stays fully red-channel (yellow -> red)");
            lastR = cr;
            lastG = cg;
        }
        check(ArmorHudLayout.lerpColor(0x00000000, 0xFFFFFFFF, 0.5) == 0x80808080, "lerp rounds per channel");
        check(ArmorHudLayout.lerpColor(0xFF102030, 0xFF405060, 2.0) == 0xFF405060, "lerp clamps t");
    }

    private static void warning() {
        // angezeigter Prozentwert strikt unter der Schwelle
        check(ArmorHudLayout.isLow(19, 100, 20), "19% < 20% warns");
        check(!ArmorHudLayout.isLow(20, 100, 20), "20% at 20% does not warn");
        check(!ArmorHudLayout.isLow(100, 100, 50), "full never warns");
        check(ArmorHudLayout.isLow(0, 100, 10), "broken warns");
        check(ArmorHudLayout.isLow(1, 2031, 10), "almost broken warns");
        check(!ArmorHudLayout.isLow(5, 0, 50), "items without durability never warn");
        check(ArmorHudLayout.isLow(24, 100, 23), "threshold 23 is sanitized to 25");
        check(ArmorHudLayout.isLow(9, 100, 3), "threshold 3 is sanitized to 10: 9% warns");
        check(!ArmorHudLayout.isLow(10, 100, 3), "threshold 3 is sanitized to 10: 10% does not warn");
        check(ArmorHudLayout.isLow(9, 100, 10), "9% < 10% warns");
        for (int t = 10; t <= 50; t += 5) {
            for (int max : new int[]{55, 363, 2031}) {
                for (int r = 0; r <= max; r++) {
                    boolean low = ArmorHudLayout.isLow(r, max, t);
                    check(low == (ArmorHudLayout.percent(r, max) < t), "warn matches the displayed percent");
                }
            }
        }
        check(ArmorHudLayout.warnPulse(0) < 1e-6, "pulse starts at 0");
        check(Math.abs(ArmorHudLayout.warnPulse(ArmorHudLayout.WARN_PERIOD_MS / 2) - 1f) < 1e-6, "pulse peaks at half period");
        for (long t = -5000; t < 5000; t += 37) {
            float p = ArmorHudLayout.warnPulse(t);
            check(p >= 0f && p <= 1f, "pulse in 0..1");
            check(Math.abs(p - ArmorHudLayout.warnPulse(t + ArmorHudLayout.WARN_PERIOD_MS)) < 1e-5, "pulse is periodic");
            int oa = ArmorHudLayout.warnOutlineColor(p) >>> 24;
            int fa = ArmorHudLayout.warnFillColor(p) >>> 24;
            check(oa >= 0x50 && oa <= 0xF0, "outline alpha stays subtle..visible");
            check(fa >= 0x12 && fa <= 0x42, "fill alpha stays subtle");
        }
        check((ArmorHudLayout.warnOutlineColor(5f) >>> 24) == 0xF0, "pulse values are clamped");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Maße je Stil
    // ------------------------------------------------------------------------------------------------------------

    /** Für alle Stile/Modi/Anzahlen/Textbreiten: alles liegt im Element, Icons und Beschriftungen überlappen nicht. */
    private static void metricsInsideAndNoOverlap() {
        ArmorHudLayout.Metrics m = new ArmorHudLayout.Metrics();
        for (int style = 0; style < ArmorHudLayout.STYLE_COUNT; style++) {
            for (int mode = 0; mode < ArmorHudLayout.DUR_COUNT; mode++) {
                for (int n = 1; n <= 7; n++) {
                    for (int tw : TEXT_WIDTHS) {
                        for (boolean bg : BOOLS) {
                            m.compute(style, mode, n, tw, bg);
                            String c = " [style " + style + ", mode " + mode + ", n " + n + ", text " + tw + ", bg " + bg + "]";
                            check(m.width > 0 && m.height > 0, "positive size" + c);
                            int lw = style == ArmorHudLayout.STYLE_VERTICAL ? tw : ArmorHudLayout.labelWidth(tw);
                            int lh = style == ArmorHudLayout.STYLE_VERTICAL ? ArmorHudLayout.TEXT_H : ArmorHudLayout.LABEL_H;
                            for (int i = 0; i < n; i++) {
                                int ix = m.iconX(i), iy = m.iconY(i);
                                check(ix >= 0 && iy >= 0 && ix + 16 <= m.width && iy + 16 <= m.height, "icon " + i + " inside" + c);
                                check(m.markX(i) >= 0 && m.markY(i) >= 0 && m.markX(i) + m.markW() <= m.width
                                        && m.markY(i) + m.markH() <= m.height, "warn mark " + i + " inside" + c);
                                if (m.text) {
                                    int tx = m.textX(i, lw), ty = m.textY(i);
                                    check(tx >= 0 && tx + lw <= m.width, "text " + i + " inside horizontally" + c);
                                    check(ty >= 0 && ty + lh <= m.height, "text " + i + " inside vertically" + c);
                                    if (style != ArmorHudLayout.STYLE_VERTICAL) {
                                        boolean above = ty + lh <= iy;
                                        boolean below = ty >= iy + 16;
                                        check(above || below, "small label does not cover the icon" + c);
                                    } else {
                                        check(tx >= ix + 16, "vertical text right of the icon" + c);
                                    }
                                    if (i > 0) {
                                        int prevRight = m.textX(i - 1, lw) + lw;
                                        if (style != ArmorHudLayout.STYLE_VERTICAL) {
                                            check(tx - prevRight >= ArmorHudLayout.LABEL_SPACING, "labels of neighbours keep their spacing" + c);
                                        }
                                    }
                                }
                                if (m.bar) {
                                    int bx = m.barX(i), by = m.barY(i);
                                    check(bx >= 0 && by >= 0 && bx + m.barW() <= m.width && by + m.barH() <= m.height, "bar inside" + c);
                                    if (m.vanillaBar) {
                                        check(bx == ix + 2 && by == iy + 13 && m.barW() == 13 && m.barH() == 2, "vanilla bar geometry" + c);
                                    }
                                }
                                if (i > 0) {
                                    if (style == ArmorHudLayout.STYLE_VERTICAL) {
                                        check(iy >= m.iconY(i - 1) + 16, "rows do not overlap" + c);
                                        check(ix == m.iconX(i - 1), "rows share the icon column" + c);
                                    } else {
                                        check(ix >= m.iconX(i - 1) + 16, "icons do not overlap" + c);
                                        check(iy == m.iconY(i - 1), "icons share one row" + c);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void metricsSpecifics() {
        ArmorHudLayout.Metrics m = new ArmorHudLayout.Metrics();

        // Vertical: Zeilen 20 px, 2 px oben/unten, Icon bei x = 4, Text 4 px rechts vom Icon
        m.compute(ArmorHudLayout.STYLE_VERTICAL, ArmorHudLayout.DUR_PERCENT, 4, 23, false);
        check(m.height == 4 * 20 + 4, "vertical height");
        check(m.width == 4 + 16 + 4 + 23 + 4, "vertical width with text");
        check(m.iconX(0) == 4 && m.iconY(0) == 4 && m.iconY(1) == 24, "vertical icon positions");
        check(m.textX(0, 23) == 24 && m.textY(0) == 2 + 6, "vertical text centred in the row");
        m.compute(ArmorHudLayout.STYLE_VERTICAL, ArmorHudLayout.DUR_OFF, 4, 23, false);
        check(m.width == 24 && !m.text && !m.bar, "vertical without durability = icon column");
        m.compute(ArmorHudLayout.STYLE_VERTICAL, ArmorHudLayout.DUR_VANILLA, 4, 23, false);
        check(m.width == 24 && m.vanillaBar && m.bar, "vertical vanilla: bar in the icon, no text column");
        m.compute(ArmorHudLayout.STYLE_VERTICAL, ArmorHudLayout.DUR_BAR_PERCENT, 2, 9, false);
        check(m.textAreaW == ArmorHudLayout.V_BAR_MIN_W && m.barW() == ArmorHudLayout.V_BAR_MIN_W && !m.vanillaBar,
                "vertical bar+percent: bar under the text with a minimum width");
        check(m.barY(0) >= m.textY(0) + ArmorHudLayout.TEXT_H, "vertical bar below the text");

        // Slots: Raster wie die Vanilla-Hotbar (20 px, 22 hoch, Item bei 3/3), Beschriftung über der Leiste
        m.compute(ArmorHudLayout.STYLE_SLOTS, ArmorHudLayout.DUR_VANILLA, 4, 0, false);
        check(m.width == 4 * 20 + 2 && m.height == 22, "slots = hotbar geometry");
        check(m.iconX(0) == 3 && m.iconY(0) == 3 && m.iconX(1) == 23, "slot items like vanilla hotbar items");
        check(m.frameY == 0, "no label row: bar starts at the top");
        m.compute(ArmorHudLayout.STYLE_SLOTS, ArmorHudLayout.DUR_PERCENT, 4, 25, false);
        check(m.pitch == 20 && m.height == 22 + ArmorHudLayout.LABEL_H + ArmorHudLayout.LABEL_GAP,
                "\"100%\" labels (25 px) keep the 20 px hotbar grid");
        check(m.frameY + 22 == m.height, "slot bar ends at the bottom edge (flush with the hotbar when anchored)");
        m.compute(ArmorHudLayout.STYLE_SLOTS, ArmorHudLayout.DUR_NUMBERS, 4, 22, false);
        check(m.pitch == 20, "four-digit numbers (2031 = 22 px) keep the hotbar grid");
        m.compute(ArmorHudLayout.STYLE_SLOTS, ArmorHudLayout.DUR_NUMBERS_MAX, 4, 47, false);
        check(m.pitch == ArmorHudLayout.labelWidth(47) + ArmorHudLayout.LABEL_SPACING, "wide labels widen the slots");
        m.compute(ArmorHudLayout.STYLE_SLOTS, ArmorHudLayout.DUR_VANILLA, 4, 0, true);
        check(m.width == 82 + 2 * ArmorHudLayout.S_BG_PAD && m.height == 22 + 2 * ArmorHudLayout.S_BG_PAD, "background padding");

        // Compact: 17 px Raster, symmetrischer Rand, dünner Balken direkt unter dem Icon
        m.compute(ArmorHudLayout.STYLE_COMPACT, ArmorHudLayout.DUR_VANILLA, 4, 0, false);
        check(m.width == 2 + 3 * 17 + 16 + 2, "compact width");
        check(m.iconX(0) == 2 && m.width - (m.iconX(3) + 16) == 2, "compact margins symmetric");
        check(!m.vanillaBar && m.barY(0) == m.iconY(0) + 16 + 1 && m.barW() == 14 && m.barH() == 2, "compact thin bar under the icon");
        check(m.height == 2 + 16 + 3 + 2, "compact height with bar");
        m.compute(ArmorHudLayout.STYLE_COMPACT, ArmorHudLayout.DUR_OFF, 4, 0, false);
        check(m.height == 20 && !m.bar, "compact height icons only");
        m.compute(ArmorHudLayout.STYLE_COMPACT, ArmorHudLayout.DUR_PERCENT, 4, 25, false);
        check(m.bar && m.text, "compact always shows its thin bar (text modes add the label below)");
        check(m.height == 2 + 16 + 3 + 1 + 7 + 2 && m.textY(0) >= m.barY(0) + m.barH(), "compact label below the bar");
        check(m.pitch == 20, "compact \"100%\" labels need 20 px per icon");
        m.compute(ArmorHudLayout.STYLE_COMPACT, ArmorHudLayout.DUR_NUMBERS, 4, 17, false);
        check(m.bar && m.text && m.pitch == ArmorHudLayout.C_PITCH, "compact numbers (3 digits) stay dense");

        // Minimal: ohne Hintergrund, Zahl darunter
        m.compute(ArmorHudLayout.STYLE_MINIMAL, ArmorHudLayout.DUR_PERCENT, 4, 15, true);
        check(m.height == 1 + 16 + 1 + 7 + 1, "minimal height with label");
        check(m.textY(0) == m.iconY(0) + 16 + 1, "minimal label right below the icon");
        check(m.pad == 0, "minimal never pads for a background");
        m.compute(ArmorHudLayout.STYLE_MINIMAL, ArmorHudLayout.DUR_VANILLA, 3, 0, false);
        check(m.vanillaBar && m.barY(0) == m.iconY(0) + 13, "minimal vanilla bar inside the icon");

        // ungültige Werte
        m.compute(42, 42, -3, -10, false);
        check(m.style == ArmorHudLayout.STYLE_DEFAULT && m.mode == ArmorHudLayout.DUR_DEFAULT && m.count == 0, "metrics sanitize input");
    }

    // ------------------------------------------------------------------------------------------------------------
    // Position
    // ------------------------------------------------------------------------------------------------------------

    private static void freePlacement() {
        check(ArmorHudLayout.scaledSize(56, 1.1f) == 62, "scaled size rounds up like clampHudX");
        check(ArmorHudLayout.scaledSize(56, Float.NaN) == 56, "NaN scale counts as 100 %");
        check(ArmorHudLayout.scaledSize(56, -2f) == 56, "negative scale counts as 100 %");
        for (int[] screen : SCREENS) {
            int w = screen[0], h = screen[1];
            int[] sizes = {0, 1, 24, 82, 108, w - 1, w, w + 50};
            for (int sw : sizes) {
                for (int sh : new int[]{0, 22, 84, h, h + 9}) {
                    int ax = ArmorHudLayout.freeX(-1, w, sw);
                    int ay = ArmorHudLayout.freeY(-1, h, sh);
                    if (sw <= w) {
                        check(ax >= 0 && ax + sw <= w, "auto x inside");
                        check(Math.abs((w - (ax + sw)) - ax) <= 1, "auto x centred");
                    } else {
                        check(ax == 0, "too wide: left edge");
                    }
                    if (sh <= h - ArmorHudLayout.AUTO_BOTTOM_GAP) {
                        check(ay + sh == h - ArmorHudLayout.AUTO_BOTTOM_GAP, "auto y above the status bars");
                    }
                    if (sh <= h) check(ay >= 0 && ay + sh <= h, "auto y inside");
                    for (int stored : new int[]{-100000, -2, 0, 5, w / 2, w - 3, 100000}) {
                        int x = ArmorHudLayout.freeX(stored, w, sw);
                        int y = ArmorHudLayout.freeY(stored, h, sh);
                        if (sw <= w) check(x >= 0 && x + sw <= w, "stored x clamped inside");
                        if (sh <= h) check(y >= 0 && y + sh <= h, "stored y clamped inside");
                        if (stored >= 0 && stored + sw <= w) check(x == stored, "inside values unchanged");
                        check(ArmorHudLayout.clampToScreen(x, sw, w) == x || sw > w, "clamp idempotent");
                    }
                }
            }
        }
    }

    /** Die Kanten fürs Anheften sind genau das Rechteck, das die Hotbar selbst benutzt ({@link HotbarLayout#box}). */
    private static void hotbarEdgesMatchHotbarLayout() {
        for (int[] screen : SCREENS) {
            int w = screen[0], h = screen[1];
            for (float hs : HOTBAR_SCALES) {
                for (boolean offhandLeft : BOOLS) for (boolean indicator : BOOLS) {
                    int left = HotbarLayout.left(offhandLeft, indicator);
                    int right = HotbarLayout.right(offhandLeft, indicator);
                    for (int rawX : new int[]{-100000, -300, -1, 0, 1, 77, 300, 100000}) {
                        for (int rawY : new int[]{-100000, -200, -7, 0, 50}) {
                            int ox = HotbarLayout.clampOffsetX(rawX, w, hs, left, right);
                            int oy = HotbarLayout.clampOffsetY(rawY, h, hs, HotbarLayout.top(true), HotbarLayout.BOTTOM);
                            HotbarLayout.Box box = HotbarLayout.box(ox, oy, hs, w, h, left, right, ArmorHudLayout.HOTBAR_ROW_TOP, HotbarLayout.BOTTOM);
                            String c = " [" + w + "x" + h + " hotbar " + hs + " off " + ox + "/" + oy + "]";
                            check(ArmorHudLayout.hotbarLeftEdge(w, ox, hs, left) == box.x(), "left edge = hotbar box" + c);
                            check(ArmorHudLayout.hotbarRightEdge(w, ox, hs, right) == box.right(), "right edge = hotbar box" + c);
                            check(ArmorHudLayout.hotbarBottomEdge(h, oy, hs) == box.bottom(), "bottom edge = hotbar box" + c);
                        }
                    }
                }
            }
            // Vanilla-Lage: genau die Vanilla-Pixel (Offhand links: 91 + 29 px, rechts Hotbar + Auswahlrahmen)
            check(ArmorHudLayout.hotbarLeftEdge(w, 0, 1f, HotbarLayout.left(true, false)) == w / 2 - 120, "vanilla left edge incl. offhand");
            check(ArmorHudLayout.hotbarRightEdge(w, 0, 1f, HotbarLayout.right(true, false)) == w / 2 + 92, "vanilla right edge");
            check(ArmorHudLayout.hotbarBottomEdge(h, 0, 1f) == h, "vanilla bottom = screen edge");
        }
    }

    private static void anchorPlacement() {
        // einfache Fälle
        check(ArmorHudLayout.anchoredX(ArmorHudLayout.ANCHOR_HOTBAR_LEFT, 360, 572, 82, 960, 4) == 360 - 4 - 82, "left of the hotbar");
        check(ArmorHudLayout.anchoredX(ArmorHudLayout.ANCHOR_HOTBAR_RIGHT, 360, 572, 82, 960, 4) == 576, "right of the hotbar");
        check(ArmorHudLayout.anchoredX(ArmorHudLayout.ANCHOR_HOTBAR_LEFT, 40, 252, 82, 960, 4) == 256, "no room left: flips right");
        check(ArmorHudLayout.anchoredX(ArmorHudLayout.ANCHOR_HOTBAR_RIGHT, 700, 912, 82, 960, 4) == 700 - 4 - 82, "no room right: flips left");
        check(ArmorHudLayout.anchoredX(ArmorHudLayout.ANCHOR_HOTBAR_LEFT, 50, 300, 320, 360, 4) == 0, "no room anywhere: clamped (left)");
        check(ArmorHudLayout.anchoredX(ArmorHudLayout.ANCHOR_HOTBAR_RIGHT, 50, 300, 320, 360, 4) == 360 - 320, "no room anywhere: clamped (right)");
        check(ArmorHudLayout.anchoredY(540, 22, 540) == 518, "bottom flush with the hotbar");
        check(ArmorHudLayout.anchoredY(540, 600, 540) == 0, "taller than the screen: top edge");
        check(ArmorHudLayout.anchoredY(30, 84, 540) == 0, "hotbar near the top: clamped");
        check(Math.abs(ArmorHudLayout.anchoredScale(1.0f, 1.5f) - 1.5f) < 1e-6, "anchor follows the hotbar size");
        check(Math.abs(ArmorHudLayout.anchoredScale(2.0f, 0.5f) - 1.0f) < 1e-6, "own scale times hotbar scale");
        check(Math.abs(ArmorHudLayout.anchoredScale(Float.NaN, 9f) - 1.5f) < 1e-6, "broken values sanitized");
        check(ArmorHudLayout.anchorGap(1.0f) == 4 && ArmorHudLayout.anchorGap(0.5f) == 2 && ArmorHudLayout.anchorGap(1.5f) == 6, "gap scales");

        // alle Bildschirme, Hotbar-Größen, Versätze, Offhand-Seiten und HUD-Größen
        int[][] hudSizes = {{82, 22}, {71, 23}, {24, 84}, {51, 144}, {108, 30}, {150, 40}};
        float[] armorScales = {0.5f, 1.0f, 1.5f, 2.0f};
        for (int[] screen : SCREENS) {
            int w = screen[0], h = screen[1];
            for (float hs : HOTBAR_SCALES) {
                for (boolean offhandLeft : BOOLS) for (boolean indicator : BOOLS) {
                    int left = HotbarLayout.left(offhandLeft, indicator);
                    int right = HotbarLayout.right(offhandLeft, indicator);
                    for (int rawX : new int[]{-100000, -250, 0, 120, 100000}) {
                        for (int rawY : new int[]{-100000, -150, 0}) {
                            int ox = HotbarLayout.clampOffsetX(rawX, w, hs, left, right);
                            int oy = HotbarLayout.clampOffsetY(rawY, h, hs, HotbarLayout.top(true), HotbarLayout.BOTTOM);
                            int hl = ArmorHudLayout.hotbarLeftEdge(w, ox, hs, left);
                            int hr = ArmorHudLayout.hotbarRightEdge(w, ox, hs, right);
                            int hb = ArmorHudLayout.hotbarBottomEdge(h, oy, hs);
                            int gap = ArmorHudLayout.anchorGap(hs);
                            for (int[] size : hudSizes) for (float as : armorScales) {
                                float s = ArmorHudLayout.anchoredScale(as, hs);
                                int sw = ArmorHudLayout.scaledSize(size[0], s);
                                int sh = ArmorHudLayout.scaledSize(size[1], s);
                                for (int anchor = ArmorHudLayout.ANCHOR_HOTBAR_LEFT; anchor <= ArmorHudLayout.ANCHOR_HOTBAR_RIGHT; anchor++) {
                                    int x = ArmorHudLayout.anchoredX(anchor, hl, hr, sw, w, gap);
                                    int y = ArmorHudLayout.anchoredY(hb, sh, h);
                                    String c = " [" + w + "x" + h + " hotbar " + hs + " off " + ox + "/" + oy + " hud " + sw + "x" + sh
                                            + " anchor " + anchor + " offhandLeft " + offhandLeft + "]";
                                    if (sw <= w) check(x >= 0 && x + sw <= w, "anchored x inside the screen" + c);
                                    if (sh <= h) check(y >= 0 && y + sh <= h, "anchored y inside the screen" + c);
                                    boolean leftFits = hl - gap - sw >= 0;
                                    boolean rightFits = hr + gap + sw <= w;
                                    if (leftFits || rightFits) {
                                        // passt auf einer Seite: kein Überlappen mit der Hotbar-Zeile (inkl. Offhand-Feld)
                                        check(x + sw <= hl - gap || x >= hr + gap, "no overlap with the hotbar row" + c);
                                        boolean preferred = anchor == ArmorHudLayout.ANCHOR_HOTBAR_LEFT ? leftFits : rightFits;
                                        if (preferred) {
                                            check(anchor == ArmorHudLayout.ANCHOR_HOTBAR_LEFT ? x + sw + gap == hl : x == hr + gap,
                                                    "sits on the requested side, gap exact" + c);
                                        }
                                    }
                                    if (sh <= hb) check(y + sh == hb, "bottom flush with the hotbar" + c);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
