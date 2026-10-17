package com.bame.client.playerwheel;

/**
 * Reine Geometrie des Spieler-Auswahlrads (keine Minecraft-Klassen, getestet in {@code src/plannerTest}, Aufgabe
 * {@code playerWheelTest}): Lage der Segmente um die Mitte (wie im Emote-Rad: gleichmäßig verteilt, erstes Segment
 * oben), Treffer eines Mausklicks, „Maus weg“, und Begrenzung des Rads auf den Bildschirm.
 *
 * <p>Alle Werte sind GUI-Pixel. Eine Instanz wird einmal pro Öffnen bzw. Bild neu berechnet ({@link #compute}),
 * nicht pro Abfrage erzeugt.
 */
public final class WheelLayout {
    /** Kantenlänge eines Segment-Kästchens. */
    public static final int SLOT = 40;
    /** Abstand Mitte → Mitte eines Segments (bei 1-8 Segmenten überdeckt kein Kästchen die Mittelplatte). */
    public static final int RADIUS = 62;
    /** Beschriftung unter dem Kästchen (3 px Abstand + 8 px Schrift + Schatten). */
    public static final int LABEL_GAP = 3;
    public static final int LABEL_H = 9;
    /** Zusätzliche Trefferfläche rund um ein Kästchen. */
    public static final int HIT_PAD = 4;
    /** Innerer toter Bereich für die Winkel-Auswahl (Mitte mit Kopf und Name). */
    public static final int INNER_RADIUS = 30;
    /** Winkel-Auswahl höchstens so weit neben der Richtung eines Segments (bei einem Segment nicht ringsum). */
    public static final double MAX_SECTOR_HALF = Math.PI / 5.0;
    /** „Maus weg“: so weit außerhalb des äußeren Rings schließt das Rad. */
    public static final int AWAY_MARGIN = 18;
    /** Mindestabstand zum Bildschirmrand. */
    public static final int SCREEN_MARGIN = 4;

    private int count;
    private int plateHalfW;
    private int plateHalfH;
    /** Ausdehnung um die Mitte (für die Begrenzung): links/rechts/oben/unten. */
    private int extentLeft;
    private int extentRight;
    private int extentTop;
    private int extentBottom;
    private double outerRadius;

    /**
     * Neu berechnen.
     *
     * @param segments   Anzahl der Segmente (mindestens 1)
     * @param plateHalfW halbe Breite der Mittelplatte (Kopf + Name)
     * @param plateHalfH halbe Höhe der Mittelplatte
     */
    public WheelLayout compute(int segments, int plateHalfW, int plateHalfH) {
        this.count = Math.max(1, segments);
        this.plateHalfW = Math.max(0, plateHalfW);
        this.plateHalfH = Math.max(0, plateHalfH);
        int left = this.plateHalfW, right = this.plateHalfW, top = this.plateHalfH, bottom = this.plateHalfH;
        double outer = Math.hypot(this.plateHalfW, this.plateHalfH);
        int half = SLOT / 2;
        for (int i = 0; i < count; i++) {
            int dx = segmentDx(i);
            int dy = segmentDy(i);
            left = Math.max(left, half - dx);
            right = Math.max(right, dx + half);
            top = Math.max(top, half - dy);
            bottom = Math.max(bottom, dy + half + LABEL_GAP + LABEL_H);
            // äußerster Punkt: Kästchen-Ecke bzw. Beschriftung
            outer = Math.max(outer, Math.hypot(Math.abs(dx) + half, Math.abs(dy) + half));
            outer = Math.max(outer, Math.hypot(Math.abs(dx), dy + half + LABEL_GAP + LABEL_H));
        }
        this.extentLeft = left;
        this.extentRight = right;
        this.extentTop = top;
        this.extentBottom = bottom;
        this.outerRadius = outer;
        return this;
    }

    public int count() {
        return count;
    }

    /** Winkel des Segments {@code i} (Bogenmaß, 0 = rechts, -PI/2 = oben), gleichmäßig verteilt ab oben. */
    public double angle(int i) {
        return -Math.PI / 2.0 + i * 2.0 * Math.PI / count;
    }

    /** Versatz der Segment-Mitte zur Rad-Mitte (gerundet wie im Emote-Rad). */
    public int segmentDx(int i) {
        return (int) Math.round(Math.cos(angle(i)) * RADIUS);
    }

    public int segmentDy(int i) {
        return (int) Math.round(Math.sin(angle(i)) * RADIUS);
    }

    /** Linke obere Ecke des Kästchens {@code i} bei Rad-Mitte {@code cx}. */
    public int slotX(double cx, int i) {
        return (int) Math.round(cx) + segmentDx(i) - SLOT / 2;
    }

    public int slotY(double cy, int i) {
        return (int) Math.round(cy) + segmentDy(i) - SLOT / 2;
    }

    /** Liegt der Punkt auf der Mittelplatte? */
    public boolean onPlate(double mx, double my, double cx, double cy) {
        return Math.abs(mx - Math.round(cx)) <= plateHalfW && Math.abs(my - Math.round(cy)) <= plateHalfH;
    }

    /**
     * Segment unter der Maus oder -1. Zuerst das Kästchen selbst, dann der Rand {@link #HIT_PAD} darum (bei
     * überlappenden Rändern vieler Segmente gewinnt die nächste Mitte), sonst die Richtung im Ring zwischen
     * {@link #INNER_RADIUS} und dem äußeren Rand, höchstens {@code min(PI / n, MAX_SECTOR_HALF)} neben dem Segment.
     * Die Mittelplatte trifft nie ein Segment.
     */
    public int segmentAt(double mx, double my, double cx, double cy) {
        for (int i = 0; i < count; i++) {
            int x = slotX(cx, i);
            int y = slotY(cy, i);
            if (mx >= x && mx <= x + SLOT && my >= y && my <= y + SLOT) return i;
        }
        if (onPlate(mx, my, cx, cy)) return -1;
        int padded = -1;
        double paddedDist = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int x = slotX(cx, i);
            int y = slotY(cy, i);
            if (mx >= x - HIT_PAD && mx <= x + SLOT + HIT_PAD && my >= y - HIT_PAD && my <= y + SLOT + HIT_PAD) {
                double d = Math.hypot(mx - (x + SLOT / 2.0), my - (y + SLOT / 2.0));
                if (d < paddedDist) {
                    paddedDist = d;
                    padded = i;
                }
            }
        }
        if (padded >= 0) return padded;
        double dx = mx - Math.round(cx);
        double dy = my - Math.round(cy);
        double dist = Math.hypot(dx, dy);
        if (dist < INNER_RADIUS || dist > outerRadius) return -1;
        double a = Math.atan2(dy, dx);
        double halfSector = Math.min(Math.PI / count, MAX_SECTOR_HALF);
        int best = -1;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            double diff = Math.abs(normalizeAngle(a - angle(i)));
            if (diff <= halfSector && diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    /** Ist die Maus so weit weg, dass das Rad schließen soll? */
    public boolean isAway(double mx, double my, double cx, double cy) {
        return Math.hypot(mx - Math.round(cx), my - Math.round(cy)) > outerRadius + AWAY_MARGIN;
    }

    public double outerRadius() {
        return outerRadius;
    }

    /**
     * Rad-Mitte so begrenzen, dass das ganze Rad (Segmente, Beschriftungen, Mittelplatte) im Bild bleibt und unten
     * {@code bottomReserve} Pixel (Chat-Eingabe) frei lässt. Ist der Bildschirm zu klein, wird mittig eingepasst.
     */
    public double clampCenterX(double x, int screenW) {
        return clampAxis(x, extentLeft, extentRight, 0, screenW);
    }

    public double clampCenterY(double y, int screenH, int bottomReserve) {
        return clampAxis(y, extentTop, extentBottom, 0, screenH - Math.max(0, bottomReserve));
    }

    private static double clampAxis(double v, int before, int after, int lo, int hi) {
        double min = lo + SCREEN_MARGIN + before;
        double max = hi - SCREEN_MARGIN - after;
        if (min > max) return Math.round((lo + hi) / 2.0 + (before - after) / 2.0);
        return Math.max(min, Math.min(max, v));
    }

    public int extentLeft() {
        return extentLeft;
    }

    public int extentRight() {
        return extentRight;
    }

    public int extentTop() {
        return extentTop;
    }

    public int extentBottom() {
        return extentBottom;
    }

    /** Winkel auf -PI..PI. */
    static double normalizeAngle(double a) {
        double r = a % (2.0 * Math.PI);
        if (r > Math.PI) r -= 2.0 * Math.PI;
        if (r < -Math.PI) r += 2.0 * Math.PI;
        return r;
    }
}
