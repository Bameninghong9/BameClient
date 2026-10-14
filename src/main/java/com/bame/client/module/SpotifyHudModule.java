package com.bame.client.module;

public class SpotifyHudModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static int hudX = 10;
    public static int hudY = 180;
    public static float scale = 1.0f;
    /** Hintergrund aus dem HUD-Editor: 0 = Transparent, 1 = Tooltip, 2 = Dark (bei Card: Cover-Tönung), 3 = Outline. */
    public static int bgMode = 0;
    public static int outlineColor = 0xFFFFFFFF;
    public static boolean autoHide = false; // Hide when nothing is playing
    public static boolean expanded = false;
    public static int customWidth = -1;
    public static int customHeight = -1;

    /**
     * Aussehen des HUD: {@link #STYLE_CLASSIC} oder {@link #STYLE_CARD}. Die Zahlenwerte entsprechen den früher
     * gespeicherten (0 = Classic, 2 = Card), damit alte Konfigurationen ohne Umrechnung passen; die entfernten Stile
     * Compact (1) und Minimal (3) werden über {@link #normalizeStyle(int)} auf Classic abgebildet.
     */
    public static int style = 0;
    public static final int STYLE_CLASSIC = 0, STYLE_CARD = 2;

    /** Akzentfarbe von Fortschrittsbalken und Knöpfen: 0 = Theme, 1 = Cover-Farbe. */
    public static int accentMode = 0;
    public static final String[] ACCENT_NAMES = {"Theme", "Cover"};

    /** Gespeicherten bzw. alten Stilwert auf einen gültigen Stil abbilden (Compact/Minimal waren am ehesten Classic). */
    public static int normalizeStyle(int value) {
        return value == STYLE_CARD ? STYLE_CARD : STYLE_CLASSIC;
    }

    public static int style() {
        return normalizeStyle(style);
    }

    public static String styleName() {
        return style() == STYLE_CARD ? "Card" : "Classic";
    }

    public static String accentName() {
        return ACCENT_NAMES[accentMode == 1 ? 1 : 0];
    }

    /**
     * Wechselt zwischen Classic und Card; eigene Größe aus dem HUD-Editor wird zurückgesetzt, weil jeder Stil andere
     * Maße hat. Beim Wechsel auf Card mit "Transparent" wird einmal "Dark" (Cover-Tönung) gesetzt, damit die Karte als
     * Karte erscheint; danach ist der Hintergrund im HUD-Editor frei wählbar.
     */
    public static void cycleStyle() {
        style = style() == STYLE_CARD ? STYLE_CLASSIC : STYLE_CARD;
        if (style == STYLE_CARD && bgMode == 0) bgMode = 2;
        customWidth = -1;
        customHeight = -1;
    }
}
