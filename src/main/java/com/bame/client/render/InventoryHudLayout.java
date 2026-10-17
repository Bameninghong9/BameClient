package com.bame.client.render;

/**
 * Reine Rechnung der Inventar-Vorschau („Inventory HUD“, keine Minecraft-Klassen, getestet in {@code src/plannerTest},
 * Aufgabe {@code inventoryHudTest}): Slot-Stile, Maße und Item-Positionen des 3x9-Rasters (optional mit Hotbar-Reihe),
 * Zuordnung Zeile/Spalte zu den Slots des Spieler-Inventars, Deckkraft und Position auf dem Bildschirm inklusive
 * Begrenzung am Rand.
 *
 * <p>Alle Maße sind unskalierte GUI-Pixel des HUD-Elements (vor {@code InventoryHudModule.scale}); Positionen auf dem
 * Bildschirm sind skalierte GUI-Pixel.
 */
public final class InventoryHudLayout {
    private InventoryHudLayout() {}

    // ------------------------------------------------------------------------------------------------------------
    // Optionen (gespeicherte Werte aus alten/kaputten Dateien werden auf den Standard gesetzt)
    // ------------------------------------------------------------------------------------------------------------

    /** Slots im Hotbar-Look: 20-px-Raster wie die Vanilla-Hotbar (gleiche Zeichnung wie Armor HUD „Slots“). */
    public static final int STYLE_HOTBAR = 0;
    /** Nur Items ohne Slot-Hintergrund, 18-px-Raster wie im Vanilla-Inventar. */
    public static final int STYLE_ITEMS = 1;
    public static final int STYLE_COUNT = 2;
    public static final int STYLE_DEFAULT = STYLE_HOTBAR;
    private static final String[] STYLE_NAMES = {"Hotbar", "Items only"};

    /** Deckkraft von Hintergrund und Slots: 10-100 % in 5er-Schritten. */
    public static final float OPACITY_MIN = 0.10f;
    public static final float OPACITY_MAX = 1.0f;
    public static final int OPACITY_STEP_PERCENT = 5;
    public static final float OPACITY_DEFAULT = 1.0f;

    public static int sanitizeStyle(int style) {
        return style >= 0 && style < STYLE_COUNT ? style : STYLE_DEFAULT;
    }

    public static String styleName(int style) {
        return STYLE_NAMES[sanitizeStyle(style)];
    }

    /** Zyklus-Knopf: Linksklick einen Schritt vor, Rechtsklick einen zurück (ungültige Werte zählen als 0). */
    public static int cycle(int value, int count, boolean backwards) {
        int v = value >= 0 && value < count ? value : 0;
        return backwards ? (v + count - 1) % count : (v + 1) % count;
    }

    /** Auf 10-100 % begrenzen und auf den nächsten 5-%-Schritt runden; NaN/unendlich wird der Standard. */
    public static float sanitizeOpacity(float opacity) {
        if (!Float.isFinite(opacity)) return OPACITY_DEFAULT;
        float o = Math.max(OPACITY_MIN, Math.min(OPACITY_MAX, opacity));
        int percent = Math.round(o * 100f / OPACITY_STEP_PERCENT) * OPACITY_STEP_PERCENT;
        percent = Math.max(Math.round(OPACITY_MIN * 100f), Math.min(100, percent));
        return percent / 100f;
    }

    /** Deckkraft als ganze Prozent (für die Beschriftung des Reglers). */
    public static int opacityPercent(float opacity) {
        return Math.round(sanitizeOpacity(opacity) * 100f);
    }

    /** Regler-Stellung 0..1 aus der Deckkraft. */
    public static float opacitySliderValue(float opacity) {
        return (sanitizeOpacity(opacity) - OPACITY_MIN) / (OPACITY_MAX - OPACITY_MIN);
    }

    /** Deckkraft aus der Regler-Stellung 0..1 (gerundet auf 5-%-Schritte). */
    public static float opacityFromSlider(double fraction) {
        double f = Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : 1.0;
        return sanitizeOpacity((float) (OPACITY_MIN + f * (OPACITY_MAX - OPACITY_MIN)));
    }

    /** ARGB-Farbe mit der Deckkraft multipliziert (nur Alpha, gerundet; Farbe bleibt gleich). */
    public static int applyOpacity(int argb, float opacity) {
        float o = Float.isFinite(opacity) ? Math.max(0f, Math.min(1f, opacity)) : 1f;
        int a = (argb >>> 24) & 0xFF;
        int scaled = Math.round(a * o);
        return (Math.max(0, Math.min(255, scaled)) << 24) | (argb & 0x00FFFFFF);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Raster
    // ------------------------------------------------------------------------------------------------------------

    public static final int COLUMNS = 9;
    public static final int MAIN_ROWS = 3;
    /** Erster Slot des Hauptinventars im Spieler-Inventar (0-8 sind die Hotbar). */
    public static final int MAIN_FIRST_SLOT = 9;
    /** Slots im Raster: 27 ohne, 36 mit Hotbar-Reihe. */
    public static final int MAX_SLOTS = (MAIN_ROWS + 1) * COLUMNS;

    public static final int ITEM = 16;
    /** Hotbar-Look: Feld 20 px wie die Vanilla-Hotbar, 1 px Außenrand, Item 2 px im Feld. */
    public static final int H_CELL = 20;
    public static final int H_BORDER = 1;
    public static final int H_ITEM_OFFSET = 2;
    /** Nur Items: Feld 18 px wie im Vanilla-Inventar, Item 1 px im Feld. */
    public static final int I_CELL = 18;
    public static final int I_ITEM_OFFSET = 1;
    /** Abstand zwischen Hauptinventar und Hotbar-Reihe (wie im Vanilla-Inventar). */
    public static final int HOTBAR_GAP = 4;
    /** Abstand zum Hintergrund, wenn einer gezeichnet wird. */
    public static final int BG_PAD = 3;

    /** Zeilen im Raster (Hotbar-Reihe als vierte Zeile unten). */
    public static int rows(boolean includeHotbar) {
        return MAIN_ROWS + (includeHotbar ? 1 : 0);
    }

    /** Slot im Spieler-Inventar für Zeile/Spalte: Zeilen 0-2 = Slots 9-35 (wie im Inventar), Zeile 3 = Hotbar 0-8. */
    public static int slotIndex(int row, int col) {
        return row < MAIN_ROWS ? MAIN_FIRST_SLOT + row * COLUMNS + col : col;
    }

    /**
     * Maße und Positionen für einen Stil. Wird einmal pro Bild berechnet und wiederverwendet (keine Allokation pro
     * Bild). Alle Werte in unskalierten Element-Pixeln, Ursprung oben links.
     */
    public static final class Metrics {
        public int style;
        public boolean hotbar;
        /** Rand um das Raster (nur mit sichtbarem Hintergrund). */
        public int pad;
        /** Abstand der Felder (Hotbar-Look 20, nur Items 18). */
        public int cell;
        /** Natürliche Größe des Elements. */
        public int width;
        public int height;
        /** Linke obere Ecke des Hauptrasters (Hotbar-Look: inklusive Außenrand). */
        public int gridX;
        public int gridY;
        /** Breite des Rasters und Höhe des Hauptrasters (3 Zeilen). */
        public int gridW;
        public int mainH;
        /** Oberkante und Höhe der Hotbar-Reihe (nur mit {@link #hotbar}). */
        public int hotbarY;
        public int hotbarH;

        public Metrics compute(int style, boolean includeHotbar, boolean bgVisible) {
            this.style = sanitizeStyle(style);
            this.hotbar = includeHotbar;
            this.pad = bgVisible ? BG_PAD : 0;
            this.gridX = pad;
            this.gridY = pad;
            if (this.style == STYLE_ITEMS) {
                cell = I_CELL;
                gridW = COLUMNS * I_CELL;
                mainH = MAIN_ROWS * I_CELL;
                hotbarH = I_CELL;
            } else {
                cell = H_CELL;
                gridW = COLUMNS * H_CELL + 2 * H_BORDER;
                mainH = MAIN_ROWS * H_CELL + 2 * H_BORDER;
                hotbarH = H_CELL + 2 * H_BORDER;
            }
            hotbarY = gridY + mainH + HOTBAR_GAP;
            width = gridW + 2 * pad;
            height = (hotbar ? hotbarY + hotbarH : gridY + mainH) + pad;
            return this;
        }

        /** Linke Kante des Feldes {@code col} (Hotbar-Look innerhalb des Außenrands). */
        public int cellX(int col) {
            return style == STYLE_ITEMS ? gridX + col * I_CELL : gridX + H_BORDER + col * H_CELL;
        }

        /** Oberkante der Felder in Zeile {@code row} (Zeile 3 = Hotbar-Reihe). */
        public int cellY(int row) {
            int top = row < MAIN_ROWS ? gridY : hotbarY;
            int r = row < MAIN_ROWS ? row : 0;
            return style == STYLE_ITEMS ? top + r * I_CELL : top + H_BORDER + r * H_CELL;
        }

        /** Linke obere Ecke des 16x16-Items im Feld. */
        public int itemX(int col) {
            return cellX(col) + (style == STYLE_ITEMS ? I_ITEM_OFFSET : H_ITEM_OFFSET);
        }

        public int itemY(int row) {
            return cellY(row) + (style == STYLE_ITEMS ? I_ITEM_OFFSET : H_ITEM_OFFSET);
        }

        public int rows() {
            return InventoryHudLayout.rows(hotbar);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Position auf dem Bildschirm
    // ------------------------------------------------------------------------------------------------------------

    /** Abstand zum Bildschirmrand bei automatischer Position (unten rechts). */
    public static final int AUTO_MARGIN = 2;

    /** Skalierung aus Datei/Editor: NaN, unendlich oder nicht positiv wird 100 %. */
    public static float sanitizeScale(float scale) {
        return Float.isFinite(scale) && scale > 0f ? scale : 1.0f;
    }

    /** Skalierte Größe wie {@code StatusHudRenderer.clampHudX} (aufgerundet). */
    public static int scaledSize(int size, float scale) {
        return (int) Math.ceil(size * sanitizeScale(scale));
    }

    /** Begrenzt eine Position so, dass das Element ganz im Bild bleibt (zu groß: am Rand 0). */
    public static int clampToScreen(int pos, int scaledSize, int screen) {
        return Math.max(0, Math.min(pos, screen - scaledSize));
    }

    /** Ohne gespeicherte Position (-1): unten rechts. */
    public static int autoX(int screenW, int scaledW) {
        return screenW - scaledW - AUTO_MARGIN;
    }

    public static int autoY(int screenH, int scaledH) {
        return screenH - scaledH - AUTO_MARGIN;
    }

    /** Freie Position: gespeicherter Wert oder -1 = automatisch, immer ganz im Bild (gespeicherter Wert bleibt). */
    public static int freeX(int storedX, int screenW, int scaledW) {
        return clampToScreen(storedX == -1 ? autoX(screenW, scaledW) : storedX, scaledW, screenW);
    }

    public static int freeY(int storedY, int screenH, int scaledH) {
        return clampToScreen(storedY == -1 ? autoY(screenH, scaledH) : storedY, scaledH, screenH);
    }
}
