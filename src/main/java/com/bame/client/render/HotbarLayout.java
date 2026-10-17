package com.bame.client.render;

/**
 * Reine Layout-Rechnung der verschiebbaren Vanilla-Hotbar-Gruppe (keine Minecraft-Klassen, getestet in
 * {@code src/plannerTest}, Aufgabe {@code hotbarLayoutTest}).
 *
 * <p>Alle Werte sind skalierte GUI-Pixel. Verankerung wie in Vanilla: unten mittig ({@code x = Breite / 2},
 * {@code y = Höhe}). Gespeichert wird nur der Versatz zu dieser Verankerung, damit die Hotbar bei anderer Fenstergröße
 * oder GUI-Skalierung an derselben Stelle relativ zur Bildschirmmitte unten bleibt. Skaliert wird um denselben Punkt:
 * ein Vanilla-Punkt {@code p} landet bei {@code anker + versatz + skalierung * (p - anker)}. Versatz 0/0 bei 100 % ist
 * damit exakt die Vanilla-Position.
 *
 * <p>Die Gruppe wird als Rechteck relativ zur Verankerung beschrieben (unskaliert, links/oben negativ). Beim Zeichnen
 * wird der Versatz so begrenzt, dass dieses Rechteck ganz im Bild bleibt; der gespeicherte Wert bleibt unverändert,
 * damit die Position zurückkommt, wenn das Fenster wieder größer wird.
 */
public final class HotbarLayout {
    private HotbarLayout() {}

    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 1.5f;
    /** Gespeicherte Versätze außerhalb dieses Bereichs stammen aus einer kaputten Datei und werden begrenzt. */
    public static final int MAX_OFFSET = 4096;
    /** Einrast-Abstand wie bei den anderen HUD-Elementen im Editor. */
    public static final int SNAP_DISTANCE = 5;

    // Vanilla-Geometrie relativ zur Verankerung (InGameHud.renderHotbar / renderStatusBars, Bar)
    /** Hotbar 182 px breit, Auswahlrahmen steht je Seite 1 px über. */
    public static final int HOTBAR_HALF_WIDTH = 92;
    /** Offhand-Feld (29 px) direkt neben der Hotbar: bis 91 + 29 px von der Mitte. Platz ist immer reserviert. */
    public static final int OFFHAND_OUTER = 120;
    /** Angriffsanzeige im Hotbar-Modus rechts: x = Mitte + 91 + 6, 18 px breit. */
    public static final int ATTACK_INDICATOR_OUTER_RIGHT = 115;
    /** Angriffsanzeige im Hotbar-Modus links (Linkshänder): x = Mitte - 91 - 22, 18 px breit. */
    public static final int ATTACK_INDICATOR_OUTER_LEFT = 113;
    /** Oberkante der Level-Zahl über der Erfahrungsleiste (Höhe - 35, 1 px Kontur). */
    public static final int CORE_TOP = -36;
    /** Oberkante der Rüstungs-/Luftreihe über Herzen und Hunger (Höhe - 49). */
    public static final int STATUS_TOP = -49;
    /** Unterkante: die Hotbar schließt in Vanilla bündig mit dem Bildschirmrand ab. */
    public static final int BOTTOM = 0;

    private static final double EPS = 1e-3;

    /** Rechteck der Gruppe auf dem Bildschirm in ganzen Pixeln (nach außen gerundet). */
    public record Box(int x, int y, int w, int h) {
        public int right() { return x + w; }
        public int bottom() { return y + h; }
    }

    /**
     * Linke Kante der Gruppe relativ zur Mitte.
     *
     * @param offhandLeft     Offhand-Feld links (Rechtshänder, Vanilla-Standard)
     * @param attackIndicator Angriffsanzeige im Hotbar-Modus (sitzt auf der Seite gegenüber dem Offhand-Feld)
     */
    public static int left(boolean offhandLeft, boolean attackIndicator) {
        if (offhandLeft) return -OFFHAND_OUTER;
        return attackIndicator ? -ATTACK_INDICATOR_OUTER_LEFT : -HOTBAR_HALF_WIDTH;
    }

    /** Rechte Kante der Gruppe relativ zur Mitte (siehe {@link #left}). */
    public static int right(boolean offhandLeft, boolean attackIndicator) {
        if (!offhandLeft) return OFFHAND_OUTER;
        return attackIndicator ? ATTACK_INDICATOR_OUTER_RIGHT : HOTBAR_HALF_WIDTH;
    }

    /** Oberkante der Gruppe relativ zum unteren Rand: mit Statusleisten bis zur Rüstungsreihe, sonst bis zur Level-Zahl. */
    public static int top(boolean moveStatusBars) {
        return moveStatusBars ? STATUS_TOP : CORE_TOP;
    }

    public static int anchorX(int screenW) {
        return screenW / 2; // gleiche Ganzzahl-Division wie InGameHud
    }

    public static int anchorY(int screenH) {
        return screenH;
    }

    /** Skalierung auf 50-150 % begrenzen; NaN/unendlich (kaputte Datei) wird 100 %. */
    public static float clampScale(float scale) {
        if (Float.isNaN(scale) || Float.isInfinite(scale)) return 1.0f;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    public static int sanitizeOffset(int offset) {
        return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, offset));
    }

    /** Vanilla-Position und -Größe: dann wird gar nichts umgerechnet (keine Matrix-Arbeit). */
    public static boolean isDefault(int offsetX, int offsetY, float scale) {
        return offsetX == 0 && offsetY == 0 && clampScale(scale) == 1.0f;
    }

    /** Horizontaler Versatz, begrenzt so, dass die Gruppe {@code [left, right]} ganz im Bild bleibt. */
    public static int clampOffsetX(int offsetX, int screenW, float scale, int left, int right) {
        return clampAxis(offsetX, anchorX(screenW), screenW, clampScale(scale), left, right);
    }

    /** Vertikaler Versatz, begrenzt so, dass die Gruppe {@code [top, bottom]} ganz im Bild bleibt. */
    public static int clampOffsetY(int offsetY, int screenH, float scale, int top, int bottom) {
        return clampAxis(offsetY, anchorY(screenH), screenH, clampScale(scale), top, bottom);
    }

    private static int clampAxis(int offset, int anchor, int screen, float scale, int min, int max) {
        double lo = Math.ceil(-anchor - (double) scale * min - EPS);
        double hi = Math.floor(screen - anchor - (double) scale * max + EPS);
        if (lo > hi) {
            // Gruppe größer als der Bildschirm: mittig, an beiden Seiten steht gleich viel über
            return (int) Math.round(screen / 2.0 - anchor - (double) scale * (min + max) / 2.0);
        }
        return (int) Math.max(lo, Math.min(offset, hi));
    }

    /** Rechteck der Gruppe für einen (schon begrenzten) Versatz. */
    public static Box box(int offsetX, int offsetY, float scale, int screenW, int screenH,
                          int left, int right, int top, int bottom) {
        double s = clampScale(scale);
        double x0 = anchorX(screenW) + offsetX + s * left;
        double x1 = anchorX(screenW) + offsetX + s * right;
        double y0 = anchorY(screenH) + offsetY + s * top;
        double y1 = anchorY(screenH) + offsetY + s * bottom;
        int ix0 = (int) Math.floor(x0 + EPS);
        int iy0 = (int) Math.floor(y0 + EPS);
        int ix1 = (int) Math.ceil(x1 - EPS);
        int iy1 = (int) Math.ceil(y1 - EPS);
        return new Box(ix0, iy0, ix1 - ix0, iy1 - iy0);
    }

    /** Wohin ein Vanilla-x wandert (dieselbe Rechnung wie die GUI-Matrix beim Zeichnen). */
    public static double mapX(double vanillaX, int screenW, int offsetX, float scale) {
        int a = anchorX(screenW);
        return a + offsetX + clampScale(scale) * (vanillaX - a);
    }

    /** Wohin ein Vanilla-y wandert (dieselbe Rechnung wie die GUI-Matrix beim Zeichnen). */
    public static double mapY(double vanillaY, int screenH, int offsetY, float scale) {
        int a = anchorY(screenH);
        return a + offsetY + clampScale(scale) * (vanillaY - a);
    }

    /** Einrasten in der Bildschirmmitte: Hotbar waagerecht mittig = Vanilla-x (Versatz 0). */
    public static int snapCenterX(int offsetX) {
        return Math.abs(offsetX) < SNAP_DISTANCE ? 0 : offsetX;
    }

    /** Einrasten: Mitte der Gruppe auf der waagerechten Bildschirmmitte (wie die anderen Elemente). */
    public static int snapCenterY(int offsetY, int screenH, float scale, int top, int bottom) {
        int centreY = screenH / 2;
        double groupCentre = anchorY(screenH) + offsetY + (double) clampScale(scale) * (top + bottom) / 2.0;
        if (Math.abs(groupCentre - centreY) < SNAP_DISTANCE) {
            return (int) Math.round(offsetY + (centreY - groupCentre));
        }
        return offsetY;
    }
}
