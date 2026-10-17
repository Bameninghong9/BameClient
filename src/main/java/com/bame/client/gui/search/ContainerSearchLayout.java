package com.bame.client.gui.search;

/**
 * Position der Container-Suchleiste (reine Rechnung, getestet in {@code src/plannerTest}). Alle Werte in skalierten
 * GUI-Pixeln.
 *
 * <p>Reihenfolge:
 * <ol>
 *     <li>{@link #BELOW}: mittig direkt unter dem Container-Fenster (unter der Hotbar-Reihe des GUIs).</li>
 *     <li>{@link #ABOVE}: mittig direkt über dem Fenster, wenn unten der Platz fehlt (kleines Fenster, hohe
 *     GUI-Skalierung).</li>
 *     <li>{@link #RIGHT} / {@link #LEFT}: neben dem Fenster, unten bündig, wenn weder unten noch oben Platz ist (z. B.
 *     große Truhe bei 240 px Bildschirmhöhe); dann darf die Leiste schmaler werden (bis {@link #MIN_WIDTH}).</li>
 *     <li>{@link #OVERLAP}: letzter Ausweg bei winzigen Fenstern – am unteren Bildschirmrand, notfalls über dem
 *     unteren Rahmen des Fensters.</li>
 * </ol>
 * Die Leiste bleibt immer vollständig auf dem Bildschirm (Rand {@link #MARGIN}).
 */
public final class ContainerSearchLayout {
    public static final int PREFERRED_WIDTH = 132;
    /** Schmalste Leiste neben dem Fenster (passt auch bei 320 px Bildschirmbreite neben eine große Truhe). */
    public static final int MIN_WIDTH = 64;
    public static final int HEIGHT = 16;
    /** Abstand zum Fenster. */
    public static final int GAP = 3;
    /** Mindestabstand zum Bildschirmrand. */
    public static final int MARGIN = 2;

    public static final int BELOW = 0;
    public static final int ABOVE = 1;
    public static final int RIGHT = 2;
    public static final int LEFT = 3;
    public static final int OVERLAP = 4;

    private ContainerSearchLayout() {}

    public record Placement(int x, int y, int width, int height, int mode) {
        public boolean contains(double px, double py) {
            return px >= this.x && px < this.x + this.width && py >= this.y && py < this.y + this.height;
        }
    }

    /**
     * @param screenW Breite des Screens
     * @param screenH Höhe des Screens
     * @param guiX    linke Kante des Container-Fensters ({@code HandledScreen.x})
     * @param guiY    obere Kante ({@code HandledScreen.y})
     * @param guiW    Breite ({@code backgroundWidth})
     * @param guiH    Höhe ({@code backgroundHeight})
     */
    public static Placement place(int screenW, int screenH, int guiX, int guiY, int guiW, int guiH) {
        int sw = Math.max(0, screenW);
        int sh = Math.max(0, screenH);
        int centeredW = Math.max(1, Math.min(PREFERRED_WIDTH, sw - 2 * MARGIN));
        int centeredX = clamp(guiX + (guiW - centeredW) / 2, MARGIN, sw - MARGIN - centeredW);

        int belowY = guiY + guiH + GAP;
        if (belowY + HEIGHT + MARGIN <= sh) {
            return new Placement(centeredX, belowY, centeredW, HEIGHT, BELOW);
        }
        int aboveY = guiY - GAP - HEIGHT;
        if (aboveY >= MARGIN) {
            return new Placement(centeredX, aboveY, centeredW, HEIGHT, ABOVE);
        }

        int sideY = clamp(guiY + guiH - HEIGHT, MARGIN, sh - MARGIN - HEIGHT);
        int rightX = guiX + guiW + GAP;
        int rightSpace = sw - MARGIN - rightX;
        if (rightSpace >= MIN_WIDTH) {
            return new Placement(rightX, sideY, Math.min(PREFERRED_WIDTH, rightSpace), HEIGHT, RIGHT);
        }
        int leftSpace = guiX - GAP - MARGIN;
        if (leftSpace >= MIN_WIDTH) {
            int w = Math.min(PREFERRED_WIDTH, leftSpace);
            return new Placement(guiX - GAP - w, sideY, w, HEIGHT, LEFT);
        }

        int overlapY = Math.max(0, Math.min(belowY, sh - MARGIN - HEIGHT));
        return new Placement(centeredX, overlapY, centeredW, HEIGHT, OVERLAP);
    }

    /** Klemmt auf [min, max]; ist der Bereich leer (max &lt; min), gewinnt min. */
    static int clamp(int value, int min, int max) {
        if (max < min) return min;
        return Math.max(min, Math.min(max, value));
    }
}
