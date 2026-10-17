package com.bame.client.playerwheel;

/**
 * Prüft ohne Minecraft die Geometrie des Spieler-Auswahlrads ({@link WheelLayout}): Lage der Segmente (wie im
 * Emote-Rad: erstes oben, gleichmäßig verteilt), Treffer auf Segmente (Kästchen, Rand, Winkel im Ring), Mitte und
 * leere Bereiche ohne Treffer, keine Überlappungen, „Maus weg“ und Begrenzung auf den Bildschirm.
 * Gradle-Aufgabe {@code playerWheelTest} (läuft mit {@code check}/{@code build}).
 */
public final class WheelLayoutTest {
    private static int checks = 0;

    private static final int[][] SCREENS = {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {854, 480}, {1280, 720}, {1920, 1080}};
    private static final int PLATE_H = 18;

    public static void main(String[] args) {
        segmentPositions();
        hitTests();
        sectorLimits();
        noOverlap();
        awayDetection();
        clamping();
        System.out.println("PLAYER WHEEL TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void segmentPositions() {
        WheelLayout l = new WheelLayout();
        for (int n = 1; n <= 8; n++) {
            l.compute(n, 30, PLATE_H);
            check(l.count() == n, "segment count " + n);
            check(l.segmentDx(0) == 0 && l.segmentDy(0) == -WheelLayout.RADIUS, "first segment on top (n " + n + ")");
            for (int i = 0; i < n; i++) {
                double d = Math.hypot(l.segmentDx(i), l.segmentDy(i));
                check(Math.abs(d - WheelLayout.RADIUS) <= 1.0, "segment on the radius (n " + n + ", i " + i + ")");
            }
        }
        l.compute(4, 30, PLATE_H);
        check(l.segmentDx(1) == WheelLayout.RADIUS && l.segmentDy(1) == 0, "clockwise: second of four on the right");
        check(l.segmentDx(2) == 0 && l.segmentDy(2) == WheelLayout.RADIUS, "third of four at the bottom");
        check(l.compute(0, 30, PLATE_H).count() == 1, "at least one segment");
    }

    private static void hitTests() {
        WheelLayout l = new WheelLayout();
        double[][] centres = {{200, 150}, {200.4, 149.6}, {57, 300}, {640.5, 360.5}};
        for (int n = 1; n <= 8; n++) {
            l.compute(n, 30, PLATE_H);
            for (double[] c : centres) {
                double cx = c[0], cy = c[1];
                String ctx = " [n " + n + " centre " + cx + "/" + cy + "]";
                for (int i = 0; i < n; i++) {
                    int x = l.slotX(cx, i), y = l.slotY(cy, i);
                    int s = WheelLayout.SLOT;
                    check(l.segmentAt(x + s / 2.0, y + s / 2.0, cx, cy) == i, "slot centre hits its segment" + ctx);
                    check(l.segmentAt(x + 1, y + 1, cx, cy) == i, "slot corner hits" + ctx);
                    check(l.segmentAt(x + s - 1, y + s - 1, cx, cy) == i, "opposite corner hits" + ctx);
                    check(l.segmentAt(x - WheelLayout.HIT_PAD + 0.5, y + s / 2.0, cx, cy) == i, "padding counts" + ctx);
                }
                check(l.segmentAt(cx, cy, cx, cy) == -1, "centre (head and name) never triggers an action" + ctx);
                check(l.onPlate(cx + 29, cy - 17, cx, cy) && l.segmentAt(cx + 29, cy - 17, cx, cy) == -1, "plate corner is no segment" + ctx);
                double far = l.outerRadius() + 5;
                for (int a = 0; a < 16; a++) {
                    double ang = a * Math.PI / 8;
                    check(l.segmentAt(cx + Math.cos(ang) * far, cy + Math.sin(ang) * far, cx, cy) == -1, "beyond the outer ring no hit" + ctx);
                }
            }
        }
    }

    /** Winkel-Auswahl nur nahe an einem Segment: bei einem Segment löst ein Klick unter der Mitte nichts aus. */
    private static void sectorLimits() {
        WheelLayout l = new WheelLayout().compute(1, 30, PLATE_H);
        double cx = 300, cy = 200;
        check(l.segmentAt(cx, cy + 50, cx, cy) == -1, "single segment: below the centre is empty");
        check(l.segmentAt(cx - 50, cy, cx, cy) == -1 && l.segmentAt(cx + 50, cy, cx, cy) == -1, "single segment: left/right are empty");
        double inside = -Math.PI / 2 + WheelLayout.MAX_SECTOR_HALF - 0.02;
        check(l.segmentAt(cx + Math.cos(inside) * 75, cy + Math.sin(inside) * 75, cx, cy) == 0, "beside the box but in its direction: hit");
        double outside = -Math.PI / 2 + WheelLayout.MAX_SECTOR_HALF + 0.08;
        double ox = cx + Math.cos(outside) * 75, oy = cy + Math.sin(outside) * 75;
        int x = l.slotX(cx, 0), y = l.slotY(cy, 0);
        boolean inBox = ox >= x - WheelLayout.HIT_PAD && ox <= x + WheelLayout.SLOT + WheelLayout.HIT_PAD
                && oy >= y - WheelLayout.HIT_PAD && oy <= y + WheelLayout.SLOT + WheelLayout.HIT_PAD;
        check(inBox || l.segmentAt(ox, oy, cx, cy) == -1, "outside the sector: no hit");
        check(l.segmentAt(cx + 10, cy - 20, cx, cy) == -1, "inner dead zone: no hit");

        WheelLayout two = new WheelLayout().compute(2, 30, PLATE_H);
        check(two.segmentAt(cx, cy + 75, cx, cy) == 1, "two segments: bottom");
        check(two.segmentAt(cx + 75, cy, cx, cy) == -1, "two segments: right side is between them");

        WheelLayout eight = new WheelLayout().compute(8, 30, PLATE_H);
        for (int i = 0; i < 8; i++) {
            double a = eight.angle(i) + 0.05;
            double d = WheelLayout.RADIUS + WheelLayout.SLOT * 0.75;
            check(eight.segmentAt(cx + Math.cos(a) * d, cy + Math.sin(a) * d, cx, cy) == i, "eight segments: direction picks the nearest " + i);
        }
        check(WheelLayout.normalizeAngle(3 * Math.PI) == Math.PI || Math.abs(Math.abs(WheelLayout.normalizeAngle(3 * Math.PI)) - Math.PI) < 1e-12,
                "angle normalisation");
    }

    private static void noOverlap() {
        WheelLayout l = new WheelLayout();
        for (int n = 1; n <= 8; n++) {
            for (int plateHalfW : new int[]{20, 30}) {
                l.compute(n, plateHalfW, PLATE_H);
                int s = WheelLayout.SLOT;
                for (int i = 0; i < n; i++) {
                    int xi = l.slotX(0, i), yi = l.slotY(0, i);
                    // Kästchen überdeckt die Mittelplatte nicht
                    boolean plate = xi < plateHalfW && -plateHalfW < xi + s && yi < PLATE_H && -PLATE_H < yi + s;
                    check(!plate, "slot does not cover the centre plate (n " + n + ", i " + i + ")");
                    for (int j = i + 1; j < n; j++) {
                        int xj = l.slotX(0, j), yj = l.slotY(0, j);
                        boolean overlap = xi < xj + s && xj < xi + s && yi < yj + s && yj < yi + s;
                        check(!overlap, "slots do not overlap (n " + n + ", " + i + "/" + j + ")");
                    }
                }
                if (n <= 4) {
                    // Beschriftung unter jedem Kästchen (bis 64 px breit) bleibt neben/über der Mittelplatte
                    for (int i = 0; i < n; i++) {
                        int lx0 = l.segmentDx(i) - 32, lx1 = l.segmentDx(i) + 32;
                        int ly0 = l.slotY(0, i) + s + WheelLayout.LABEL_GAP, ly1 = ly0 + WheelLayout.LABEL_H;
                        boolean overlap = lx0 < plateHalfW && -plateHalfW < lx1 && ly0 < PLATE_H && -PLATE_H < ly1;
                        check(!overlap, "label does not cover the centre plate (n " + n + ", i " + i + ")");
                    }
                }
            }
        }
        // ein Segment (heute): Beschriftung „SAVE SKIN“ liegt zwischen Kästchen und Mittelplatte mit Abstand
        l.compute(1, 30, PLATE_H);
        int labelBottom = l.slotY(0, 0) + WheelLayout.SLOT + WheelLayout.LABEL_GAP + WheelLayout.LABEL_H;
        check(labelBottom <= -PLATE_H - 4, "single segment: label clearly above the plate");
    }

    private static void awayDetection() {
        WheelLayout l = new WheelLayout().compute(1, 30, PLATE_H);
        double cx = 100, cy = 100;
        check(!l.isAway(cx, cy, cx, cy), "centre is not away");
        int x = l.slotX(cx, 0), y = l.slotY(cy, 0);
        check(!l.isAway(x, y, cx, cy), "slot corner is not away");
        double r = l.outerRadius() + WheelLayout.AWAY_MARGIN;
        check(!l.isAway(cx + r - 1, cy, cx, cy), "just inside the margin");
        check(l.isAway(cx + r + 1, cy, cx, cy), "just outside the margin closes");
        check(l.outerRadius() >= Math.hypot(WheelLayout.SLOT / 2.0, WheelLayout.RADIUS + WheelLayout.SLOT / 2.0), "outer ring covers the slot corners");
    }

    private static void clamping() {
        WheelLayout l = new WheelLayout();
        int reserve = 18;
        for (int n = 1; n <= 4; n++) {
            l.compute(n, 34, PLATE_H);
            for (int[] screen : SCREENS) {
                int w = screen[0], h = screen[1];
                for (double mx = -60; mx <= w + 60; mx += 13.7) {
                    for (double my = -60; my <= h + 60; my += 11.3) {
                        double cx = l.clampCenterX(mx, w);
                        double cy = l.clampCenterY(my, h, reserve);
                        String c = " [n " + n + " " + w + "x" + h + " mouse " + mx + "/" + my + "]";
                        check(cx - l.extentLeft() >= WheelLayout.SCREEN_MARGIN - 1e-9, "left edge inside" + c);
                        check(cx + l.extentRight() <= w - WheelLayout.SCREEN_MARGIN + 1e-9, "right edge inside" + c);
                        check(cy - l.extentTop() >= WheelLayout.SCREEN_MARGIN - 1e-9, "top edge inside" + c);
                        check(cy + l.extentBottom() <= h - reserve - WheelLayout.SCREEN_MARGIN + 1e-9, "stays above the chat input" + c);
                        boolean fitsX = mx - l.extentLeft() >= WheelLayout.SCREEN_MARGIN && mx + l.extentRight() <= w - WheelLayout.SCREEN_MARGIN;
                        if (fitsX) check(cx == mx, "x unchanged when the wheel fits" + c);
                        boolean fitsY = my - l.extentTop() >= WheelLayout.SCREEN_MARGIN && my + l.extentBottom() <= h - reserve - WheelLayout.SCREEN_MARGIN;
                        if (fitsY) check(cy == my, "y unchanged when the wheel fits" + c);
                    }
                }
            }
        }
        // Zu kleiner Bildschirm: mittig eingepasst, keine Ausnahme
        l.compute(1, 34, PLATE_H);
        double cx = l.clampCenterX(10, 50);
        double cy = l.clampCenterY(10, 40, reserve);
        check(Double.isFinite(cx) && Double.isFinite(cy), "tiny screen handled");
    }
}
