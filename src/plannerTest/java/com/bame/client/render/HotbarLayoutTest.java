package com.bame.client.render;

/**
 * Prüft ohne Minecraft die Layout-Rechnung der verschiebbaren Vanilla-Hotbar ({@link HotbarLayout}): Standard ist
 * exakt die Vanilla-Position, Begrenzung an allen vier Rändern bei verschiedenen Bildschirmgrößen und Skalierungen,
 * Skalierung um den Punkt unten mittig, Einrasten und robuste Werte aus kaputten Dateien.
 * Gradle-Aufgabe {@code hotbarLayoutTest} (läuft mit {@code check}/{@code build}).
 */
public final class HotbarLayoutTest {
    private static int checks = 0;

    /** Skalierte GUI-Größen: kleinste Vanilla-Größe, ungerade Breiten/Höhen, übliche Fenster bei GUI-Skalierung 1-4. */
    private static final int[][] SCREENS = {
            {320, 240}, {321, 241}, {427, 240}, {480, 270}, {512, 288}, {640, 360}, {683, 384},
            {853, 480}, {854, 480}, {960, 540}, {1280, 720}, {1366, 768}, {1920, 1080}, {2560, 1440}
    };
    private static final float[] SCALES = {0.5f, 0.66f, 0.75f, 1.0f, 1.1f, 1.25f, 1.5f};
    private static final boolean[] BOOLS = {true, false};

    public static void main(String[] args) {
        groupEdges();
        defaultIsVanilla();
        clampAllEdges();
        insideValuesUnchanged();
        oversizedGroupIsCentred();
        scalePivotIsBottomCentre();
        scaleClamp();
        snapping();
        sanitize();
        storedOffsetSurvivesSmallWindow();
        System.out.println("HOTBAR LAYOUT TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static String ctx(int w, int h, float s, boolean offhandLeft, boolean indicator, boolean status) {
        return " [screen " + w + "x" + h + ", scale " + s + ", offhandLeft " + offhandLeft
                + ", indicator " + indicator + ", statusBars " + status + "]";
    }

    /** Vanilla-Geometrie: Hotbar 182 px (+1 px Auswahlrahmen), Offhand 29 px, Angriffsanzeige, Level, Rüstungsreihe. */
    private static void groupEdges() {
        check(HotbarLayout.left(true, true) == -120, "offhand left: left edge at the offhand slot (91 + 29)");
        check(HotbarLayout.left(true, false) == -120, "offhand left without indicator");
        check(HotbarLayout.right(true, true) == 115, "right-handed attack indicator ends at 91 + 6 + 18");
        check(HotbarLayout.right(true, false) == 92, "right edge = hotbar + selection frame overhang");
        check(HotbarLayout.right(false, true) == 120, "offhand right: right edge at the offhand slot");
        check(HotbarLayout.right(false, false) == 120, "offhand right without indicator");
        check(HotbarLayout.left(false, true) == -113, "left-handed attack indicator starts at 91 + 22");
        check(HotbarLayout.left(false, false) == -92, "left edge = hotbar + selection frame overhang");
        check(HotbarLayout.top(true) == -49, "with status bars the group reaches the armor/air row");
        check(HotbarLayout.top(false) == -36, "without status bars the group ends at the level number");
        check(HotbarLayout.anchorX(321) == 160, "anchor x uses the same integer division as InGameHud");
        check(HotbarLayout.anchorY(241) == 241, "anchor y is the bottom edge");
    }

    /** Versatz 0/0 bei 100 % = Vanilla: gültige Position auf jedem Bildschirm, Abbildung ist die Identität. */
    private static void defaultIsVanilla() {
        check(HotbarLayout.isDefault(0, 0, 1.0f), "0/0 at 100 % is the vanilla layout");
        check(!HotbarLayout.isDefault(1, 0, 1.0f), "x offset is not default");
        check(!HotbarLayout.isDefault(0, -1, 1.0f), "y offset is not default");
        check(!HotbarLayout.isDefault(0, 0, 1.2f), "120 % is not default");
        check(HotbarLayout.isDefault(0, 0, Float.NaN), "broken scale falls back to 100 % (= default)");
        for (int[] screen : SCREENS) {
            int w = screen[0], h = screen[1];
            for (boolean offhandLeft : BOOLS) for (boolean indicator : BOOLS) for (boolean status : BOOLS) {
                int left = HotbarLayout.left(offhandLeft, indicator);
                int right = HotbarLayout.right(offhandLeft, indicator);
                int top = HotbarLayout.top(status);
                String c = ctx(w, h, 1.0f, offhandLeft, indicator, status);
                check(HotbarLayout.clampOffsetX(0, w, 1.0f, left, right) == 0, "vanilla x is always allowed" + c);
                check(HotbarLayout.clampOffsetY(0, h, 1.0f, top, HotbarLayout.BOTTOM) == 0, "vanilla y is always allowed" + c);
                HotbarLayout.Box box = HotbarLayout.box(0, 0, 1.0f, w, h, left, right, top, HotbarLayout.BOTTOM);
                check(box.x() == w / 2 + left && box.right() == w / 2 + right, "default box x matches vanilla" + c);
                check(box.y() == h + top && box.bottom() == h, "default box sits on the bottom edge" + c);
            }
            // Identität für die echten Vanilla-Koordinaten (Hotbar, Auswahl, Offhand, Leiste, Level, Herzen, Item-Name)
            int cx = w / 2;
            int[] xs = {cx - 91, cx - 92, cx + 91, cx - 120, cx + 120, (w - 182) / 2, cx - 91 + 8 * 9, 0, w};
            int[] ys = {h - 22, h - 23, h - 19, h - 29, h - 35, h - 39, h - 49, h - 59, 0, h};
            for (int x : xs) check(HotbarLayout.mapX(x, w, 0, 1.0f) == x, "default maps x " + x + " onto itself on " + w);
            for (int y : ys) check(HotbarLayout.mapY(y, h, 0, 1.0f) == y, "default maps y " + y + " onto itself on " + h);
        }
    }

    /** Extreme Versätze werden an jedem Rand so begrenzt, dass die ganze Gruppe im Bild bleibt und genau anliegt. */
    private static void clampAllEdges() {
        int[] extremes = {-100000, -5000, -1000, 1000, 5000, 100000};
        for (int[] screen : SCREENS) {
            int w = screen[0], h = screen[1];
            for (float s : SCALES) for (boolean offhandLeft : BOOLS) for (boolean indicator : BOOLS) for (boolean status : BOOLS) {
                int left = HotbarLayout.left(offhandLeft, indicator);
                int right = HotbarLayout.right(offhandLeft, indicator);
                int top = HotbarLayout.top(status);
                double groupW = (double) s * (right - left);
                double groupH = (double) s * (HotbarLayout.BOTTOM - top);
                // passt nur knapp nicht in ganzen Pixeln: eigener Test (oversizedGroupIsCentred)
                if (groupW > w - 1 || groupH > h - 1) continue;
                String c = ctx(w, h, s, offhandLeft, indicator, status);

                int minX = HotbarLayout.clampOffsetX(Integer.MIN_VALUE / 2, w, s, left, right);
                int maxX = HotbarLayout.clampOffsetX(Integer.MAX_VALUE / 2, w, s, left, right);
                int minY = HotbarLayout.clampOffsetY(Integer.MIN_VALUE / 2, h, s, top, HotbarLayout.BOTTOM);
                int maxY = HotbarLayout.clampOffsetY(Integer.MAX_VALUE / 2, h, s, top, HotbarLayout.BOTTOM);

                // links: linke Kante bei 0 (höchstens 1 px Rundung nach innen, 1/1000 px Float-Toleranz)
                double leftEdge = HotbarLayout.mapX(w / 2 + left, w, minX, s);
                check(leftEdge >= -1e-3 && leftEdge < 1.0, "left edge touches x = 0, got " + leftEdge + c);
                // rechts: rechte Kante bei der Breite
                double rightEdge = HotbarLayout.mapX(w / 2 + right, w, maxX, s);
                check(rightEdge <= w + 1e-3 && rightEdge > w - 1.0, "right edge touches x = width, got " + rightEdge + c);
                // oben: Oberkante bei 0
                double topEdge = HotbarLayout.mapY(h + top, h, minY, s);
                check(topEdge >= -1e-3 && topEdge < 1.0, "top edge touches y = 0, got " + topEdge + c);
                // unten: nicht unter den Rand, unten anliegend = Vanilla-Höhe
                check(maxY == 0, "bottom limit is the vanilla bottom edge (offset 0)" + c);
                double bottomEdge = HotbarLayout.mapY(h, h, maxY, s);
                check(Math.abs(bottomEdge - h) < 1e-6, "bottom edge on the screen edge" + c);

                for (int e : extremes) {
                    int dx = HotbarLayout.clampOffsetX(e, w, s, left, right);
                    int dy = HotbarLayout.clampOffsetY(e, h, s, top, HotbarLayout.BOTTOM);
                    HotbarLayout.Box box = HotbarLayout.box(dx, dy, s, w, h, left, right, top, HotbarLayout.BOTTOM);
                    check(box.x() >= 0 && box.right() <= w, "clamped box inside horizontally (" + e + ") " + box + c);
                    check(box.y() >= 0 && box.bottom() <= h, "clamped box inside vertically (" + e + ") " + box + c);
                    check(HotbarLayout.clampOffsetX(dx, w, s, left, right) == dx, "x clamp is idempotent" + c);
                    check(HotbarLayout.clampOffsetY(dy, h, s, top, HotbarLayout.BOTTOM) == dy, "y clamp is idempotent" + c);
                }
            }
        }
    }

    /** Werte, bei denen die Gruppe schon ganz im Bild ist, bleiben unverändert (kein Springen beim Ziehen). */
    private static void insideValuesUnchanged() {
        int w = 960, h = 540;
        int left = HotbarLayout.left(true, true), right = HotbarLayout.right(true, true), top = HotbarLayout.top(true);
        int[][] offsets = {{0, 0}, {10, -20}, {-300, -100}, {300, -400}, {-355, -10}, {360, -491}};
        for (int[] o : offsets) {
            check(HotbarLayout.clampOffsetX(o[0], w, 1.0f, left, right) == o[0], "inside x stays " + o[0]);
            check(HotbarLayout.clampOffsetY(o[1], h, 1.0f, top, HotbarLayout.BOTTOM) == o[1], "inside y stays " + o[1]);
        }
        // genau an den Grenzen (960x540, 100 %): x in [-360, 365], y in [-491, 0]
        check(HotbarLayout.clampOffsetX(-360, w, 1.0f, left, right) == -360, "left limit itself is allowed");
        check(HotbarLayout.clampOffsetX(-361, w, 1.0f, left, right) == -360, "one past the left limit is clamped");
        check(HotbarLayout.clampOffsetX(365, w, 1.0f, left, right) == 365, "right limit itself is allowed");
        check(HotbarLayout.clampOffsetX(366, w, 1.0f, left, right) == 365, "one past the right limit is clamped");
        check(HotbarLayout.clampOffsetY(-491, h, 1.0f, top, HotbarLayout.BOTTOM) == -491, "top limit itself is allowed");
        check(HotbarLayout.clampOffsetY(-492, h, 1.0f, top, HotbarLayout.BOTTOM) == -491, "one past the top limit is clamped");
        check(HotbarLayout.clampOffsetY(1, h, 1.0f, top, HotbarLayout.BOTTOM) == 0, "below the bottom edge is clamped");
    }

    /** Gruppe breiter als das Fenster (winziges Fenster, 150 %): mittig, an beiden Seiten steht gleich viel über. */
    private static void oversizedGroupIsCentred() {
        int[][] tiny = {{200, 120}, {300, 200}, {320, 240}, {333, 60}};
        for (int[] screen : tiny) {
            int w = screen[0], h = screen[1];
            for (boolean offhandLeft : BOOLS) for (boolean indicator : BOOLS) for (boolean status : BOOLS) {
                int left = HotbarLayout.left(offhandLeft, indicator);
                int right = HotbarLayout.right(offhandLeft, indicator);
                int top = HotbarLayout.top(status);
                float s = 1.5f;
                String c = ctx(w, h, s, offhandLeft, indicator, status);
                if (s * (right - left) > w) {
                    for (int request : new int[] {-9999, 0, 9999}) {
                        int dx = HotbarLayout.clampOffsetX(request, w, s, left, right);
                        double overLeft = -HotbarLayout.mapX(w / 2 + left, w, dx, s);
                        double overRight = HotbarLayout.mapX(w / 2 + right, w, dx, s) - w;
                        check(overLeft > 0 && overRight > 0, "oversized group overflows both sides" + c);
                        check(Math.abs(overLeft - overRight) <= 1.0, "oversized group is centred (" + overLeft + " vs " + overRight + ")" + c);
                    }
                }
                if (s * (HotbarLayout.BOTTOM - top) > h) {
                    int dy = HotbarLayout.clampOffsetY(0, h, s, top, HotbarLayout.BOTTOM);
                    double overTop = -HotbarLayout.mapY(h + top, h, dy, s);
                    double overBottom = HotbarLayout.mapY(h, h, dy, s) - h;
                    check(Math.abs(overTop - overBottom) <= 1.0, "oversized group is centred vertically" + c);
                }
            }
        }
    }

    /** Skaliert wird um den Punkt unten mittig: dieser Punkt bleibt beim Versatz, alles andere rückt proportional. */
    private static void scalePivotIsBottomCentre() {
        for (int[] screen : SCREENS) {
            int w = screen[0], h = screen[1];
            for (float s : SCALES) {
                for (int dx : new int[] {-50, 0, 37}) {
                    check(HotbarLayout.mapX(w / 2, w, dx, s) == w / 2 + dx, "anchor x stays at the offset for scale " + s);
                }
                for (int dy : new int[] {-80, 0}) {
                    check(HotbarLayout.mapY(h, h, dy, s) == h + dy, "anchor y stays at the offset for scale " + s);
                }
                double hotbarLeft = HotbarLayout.mapX(w / 2 - 91, w, 0, s);
                check(Math.abs(hotbarLeft - (w / 2 - 91 * HotbarLayout.clampScale(s))) < 1e-4, "hotbar width scales with " + s);
                double levelY = HotbarLayout.mapY(h - 35, h, 0, s);
                check(Math.abs(levelY - (h - 35 * HotbarLayout.clampScale(s))) < 1e-4, "group height scales with " + s);
            }
        }
    }

    private static void scaleClamp() {
        check(HotbarLayout.clampScale(0.1f) == 0.5f, "below 50 % is clamped");
        check(HotbarLayout.clampScale(0.5f) == 0.5f, "50 % is allowed");
        check(HotbarLayout.clampScale(1.0f) == 1.0f, "100 % stays");
        check(HotbarLayout.clampScale(1.5f) == 1.5f, "150 % is allowed");
        check(HotbarLayout.clampScale(9.0f) == 1.5f, "above 150 % is clamped");
        check(HotbarLayout.clampScale(-3.0f) == 0.5f, "negative scale is clamped");
        check(HotbarLayout.clampScale(Float.NaN) == 1.0f, "NaN falls back to 100 %");
        check(HotbarLayout.clampScale(Float.POSITIVE_INFINITY) == 1.0f, "+inf falls back to 100 %");
        check(HotbarLayout.clampScale(Float.NEGATIVE_INFINITY) == 1.0f, "-inf falls back to 100 %");
        // eine kaputte Skalierung darf die Begrenzung nicht aushebeln
        int dx = HotbarLayout.clampOffsetX(100000, 640, Float.NaN, -120, 115);
        check(dx == HotbarLayout.clampOffsetX(100000, 640, 1.0f, -120, 115), "NaN scale clamps like 100 %");
    }

    /** Einrasten wie bei den anderen Elementen: waagerecht mittig (= Vanilla-x) und Gruppenmitte auf Bildschirmmitte. */
    private static void snapping() {
        check(HotbarLayout.snapCenterX(4) == 0 && HotbarLayout.snapCenterX(-4) == 0, "close to the centre snaps to vanilla x");
        check(HotbarLayout.snapCenterX(5) == 5 && HotbarLayout.snapCenterX(-5) == -5, "5 px away does not snap");
        check(HotbarLayout.snapCenterX(0) == 0, "centre stays centre");
        for (int[] screen : SCREENS) {
            int w = screen[0], h = screen[1];
            for (float s : SCALES) for (boolean status : BOOLS) {
                int top = HotbarLayout.top(status);
                // Versatz, bei dem die Gruppenmitte genau auf der Bildschirmmitte liegt, plus/minus 3 px
                double half = (double) HotbarLayout.clampScale(s) * top / 2.0;
                double exact = h / 2 - h - half;
                for (int delta = -3; delta <= 3; delta++) {
                    int requested = (int) Math.round(exact) + delta;
                    int snapped = HotbarLayout.snapCenterY(requested, h, s, top, HotbarLayout.BOTTOM);
                    double centre = h + snapped + half;
                    check(Math.abs(centre - h / 2) <= 0.5 + 1e-4, "group centre snaps onto the screen centre on " + w + "x" + h + " scale " + s);
                }
                int far = (int) Math.round(exact) + 40;
                check(HotbarLayout.snapCenterY(far, h, s, top, HotbarLayout.BOTTOM) == far, "far from the centre does not snap");
            }
        }
    }

    private static void sanitize() {
        check(HotbarLayout.sanitizeOffset(0) == 0, "0 stays");
        check(HotbarLayout.sanitizeOffset(-250) == -250, "normal values stay");
        check(HotbarLayout.sanitizeOffset(Integer.MAX_VALUE) == HotbarLayout.MAX_OFFSET, "huge positive offset is limited");
        check(HotbarLayout.sanitizeOffset(Integer.MIN_VALUE) == -HotbarLayout.MAX_OFFSET, "huge negative offset is limited");
    }

    /**
     * Gespeichert wird der Versatz zur Mitte unten: auf einem kleinen Fenster wird nur beim Zeichnen begrenzt, auf dem
     * großen Fenster kommt die alte Stelle zurück; in der Mitte gespeicherte Hotbars bleiben bei jeder Größe mittig.
     */
    private static void storedOffsetSurvivesSmallWindow() {
        int left = HotbarLayout.left(true, true), right = HotbarLayout.right(true, true), top = HotbarLayout.top(true);
        int storedX = 700, storedY = -900;
        int smallX = HotbarLayout.clampOffsetX(storedX, 427, 1.0f, left, right);
        int smallY = HotbarLayout.clampOffsetY(storedY, 240, 1.0f, top, HotbarLayout.BOTTOM);
        check(smallX < storedX && smallY > storedY, "small window clamps the drawn position");
        check(HotbarLayout.clampOffsetX(storedX, 1920, 1.0f, left, right) == storedX, "large window shows the stored x again");
        check(HotbarLayout.clampOffsetY(storedY, 1080, 1.0f, top, HotbarLayout.BOTTOM) == storedY, "large window shows the stored y again");
        for (int[] screen : SCREENS) {
            int w = screen[0], h = screen[1];
            check(HotbarLayout.clampOffsetX(0, w, 1.0f, left, right) == 0, "centred hotbar stays centred on " + w + "x" + h);
            double hotbarCentre = HotbarLayout.mapX(w / 2, w, 0, 1.0f);
            check(hotbarCentre == w / 2, "hotbar centre is the screen centre on " + w);
        }
    }
}
