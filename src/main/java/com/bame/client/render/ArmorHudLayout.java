package com.bame.client.render;

/**
 * Reine Rechnung des Armor HUD (keine Minecraft-Klassen, getestet in {@code src/plannerTest}, Aufgabe
 * {@code armorHudTest}): Haltbarkeit als Prozent/Zahlen/Vanilla-Balken, Farbverlauf Grün-Gelb-Rot, Warnschwelle,
 * Maße und Positionen je Stil ({@link Metrics}), freie Position und Anker an der Hotbar inklusive Begrenzung am
 * Bildschirmrand.
 *
 * <p>Alle Maße sind unskalierte GUI-Pixel des HUD-Elements (vor {@code ArmorHudModule.scale}); Positionen auf dem
 * Bildschirm sind skalierte GUI-Pixel.
 */
public final class ArmorHudLayout {
    private ArmorHudLayout() {}

    // ------------------------------------------------------------------------------------------------------------
    // Optionen (gespeicherte Ganzzahlen; unbekannte Werte aus alten/kaputten Dateien werden auf den Standard gesetzt)
    // ------------------------------------------------------------------------------------------------------------

    /** Items in eigenen Slot-Kästchen im Hotbar-Look, waagerecht. */
    public static final int STYLE_SLOTS = 0;
    /** Untereinander: Icon, Haltbarkeit rechts daneben (entspricht dem bisherigen Armor HUD, deshalb Standard). */
    public static final int STYLE_VERTICAL = 1;
    /** Nur Icons dicht nebeneinander, dünner Balken unter jedem Icon. */
    public static final int STYLE_COMPACT = 2;
    /** Nur Icons ohne Hintergrund, kleine Zahl darunter. */
    public static final int STYLE_MINIMAL = 3;
    public static final int STYLE_COUNT = 4;
    public static final int STYLE_DEFAULT = STYLE_VERTICAL;
    private static final String[] STYLE_NAMES = {"Slots", "Vertical", "Compact", "Minimal"};

    /** Vanilla-Haltbarkeitsbalken (wie im Inventar, Vanilla-Farbverlauf). */
    public static final int DUR_VANILLA = 0;
    public static final int DUR_PERCENT = 1;
    /** Verbleibende Haltbarkeit, z. B. „231“. */
    public static final int DUR_NUMBERS = 2;
    /** Verbleibend/Maximum, z. B. „231/363“. */
    public static final int DUR_NUMBERS_MAX = 3;
    public static final int DUR_BAR_PERCENT = 4;
    public static final int DUR_OFF = 5;
    public static final int DUR_COUNT = 6;
    public static final int DUR_DEFAULT = DUR_PERCENT;
    private static final String[] DUR_NAMES = {"Vanilla", "Percent", "Numbers", "Numbers/Max", "Bar + Percent", "Off"};

    public static final int ORDER_HELMET_FIRST = 0;
    public static final int ORDER_BOOTS_FIRST = 1;
    public static final int ORDER_COUNT = 2;
    private static final String[] ORDER_NAMES = {"Helmet first", "Boots first"};

    public static final int ANCHOR_FREE = 0;
    public static final int ANCHOR_HOTBAR_LEFT = 1;
    public static final int ANCHOR_HOTBAR_RIGHT = 2;
    public static final int ANCHOR_COUNT = 3;
    private static final String[] ANCHOR_NAMES = {"Free", "Hotbar left", "Hotbar right"};

    /** Warnschwelle in Prozent: 10-50 in 5er-Schritten, Standard 20. */
    public static final int THRESHOLD_MIN = 10;
    public static final int THRESHOLD_MAX = 50;
    public static final int THRESHOLD_STEP = 5;
    public static final int THRESHOLD_DEFAULT = 20;

    public static int sanitizeStyle(int style) {
        return style >= 0 && style < STYLE_COUNT ? style : STYLE_DEFAULT;
    }

    public static int sanitizeDurability(int mode) {
        return mode >= 0 && mode < DUR_COUNT ? mode : DUR_DEFAULT;
    }

    public static int sanitizeOrder(int order) {
        return order >= 0 && order < ORDER_COUNT ? order : ORDER_HELMET_FIRST;
    }

    public static int sanitizeAnchor(int anchor) {
        return anchor >= 0 && anchor < ANCHOR_COUNT ? anchor : ANCHOR_FREE;
    }

    /** Auf 10-50 begrenzen und auf den nächsten 5er-Schritt runden. */
    public static int sanitizeThreshold(int threshold) {
        int t = Math.max(THRESHOLD_MIN, Math.min(THRESHOLD_MAX, threshold));
        int steps = Math.round((t - THRESHOLD_MIN) / (float) THRESHOLD_STEP);
        return THRESHOLD_MIN + steps * THRESHOLD_STEP;
    }

    /** Zyklus-Knopf: Linksklick einen Schritt vor, Rechtsklick einen zurück (ungültige Werte zählen als 0). */
    public static int cycle(int value, int count, boolean backwards) {
        int v = value >= 0 && value < count ? value : 0;
        return backwards ? (v + count - 1) % count : (v + 1) % count;
    }

    public static String styleName(int style) {
        return STYLE_NAMES[sanitizeStyle(style)];
    }

    public static String durabilityName(int mode) {
        return DUR_NAMES[sanitizeDurability(mode)];
    }

    public static String orderName(int order) {
        return ORDER_NAMES[sanitizeOrder(order)];
    }

    public static String anchorName(int anchor) {
        return ANCHOR_NAMES[sanitizeAnchor(anchor)];
    }

    /** Zeigt der Modus einen Text (Prozent oder Zahlen)? */
    public static boolean showsText(int mode) {
        return mode == DUR_PERCENT || mode == DUR_NUMBERS || mode == DUR_NUMBERS_MAX || mode == DUR_BAR_PERCENT;
    }

    /** Zeigt der Modus einen Balken? */
    public static boolean showsBar(int mode) {
        return mode == DUR_VANILLA || mode == DUR_BAR_PERCENT;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Haltbarkeit
    // ------------------------------------------------------------------------------------------------------------

    /** Verbleibende Haltbarkeit aus Schaden und Maximum (auf 0..max begrenzt). */
    public static int remaining(int damage, int max) {
        if (max <= 0) return 0;
        return Math.max(0, Math.min(max, max - damage));
    }

    /** Anteil 0..1 (ohne Maximum: 1 = voll). */
    public static double fraction(int remaining, int max) {
        if (max <= 0) return 1.0;
        return Math.max(0.0, Math.min(1.0, remaining / (double) max));
    }

    /**
     * Prozent der verbleibenden Haltbarkeit. 100 nur unbeschädigt, 0 nur wenn nichts mehr übrig ist; dazwischen
     * gerundet und auf 1..99 begrenzt (ein fast kaputtes Item zeigt nie „0%“, ein angekratztes nie „100%“).
     */
    public static int percent(int remaining, int max) {
        if (max <= 0) return 100;
        if (remaining <= 0) return 0;
        if (remaining >= max) return 100;
        long p = Math.round(remaining * 100.0 / max);
        return (int) Math.max(1, Math.min(99, p));
    }

    /** Vanilla {@code Item.getItemBarStep}: Länge des farbigen Balkens 0..13. */
    public static int vanillaBarStep(int damage, int max) {
        if (max <= 0) return 13;
        return Math.max(0, Math.min(13, Math.round(13.0f - damage * 13.0f / max)));
    }

    /** Vanilla {@code Item.getItemBarColor}: Farbton von Grün (voll) über Gelb nach Rot, RGB ohne Alpha. */
    public static int vanillaBarColor(int damage, int max) {
        if (max <= 0) return hsvToRgb(1.0f / 3.0f, 1.0f, 1.0f);
        float f = Math.max(0.0f, (max - (float) damage) / max);
        return hsvToRgb(f / 3.0f, 1.0f, 1.0f);
    }

    /** Wie {@code MathHelper.hsvToRgb} (gleiche Float-Rechnung, Alpha 0). */
    public static int hsvToRgb(float hue, float saturation, float value) {
        int i = (int) (hue * 6.0f) % 6;
        float f = hue * 6.0f - i;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - f * saturation);
        float t = value * (1.0f - (1.0f - f) * saturation);
        float r, g, b;
        switch (i) {
            case 0 -> { r = value; g = t; b = p; }
            case 1 -> { r = q; g = value; b = p; }
            case 2 -> { r = p; g = value; b = t; }
            case 3 -> { r = p; g = q; b = value; }
            case 4 -> { r = t; g = p; b = value; }
            case 5 -> { r = value; g = p; b = q; }
            default -> throw new IllegalArgumentException("hue " + hue);
        }
        int ri = Math.max(0, Math.min(255, (int) (r * 255.0f)));
        int gi = Math.max(0, Math.min(255, (int) (g * 255.0f)));
        int bi = Math.max(0, Math.min(255, (int) (b * 255.0f)));
        return ri << 16 | gi << 8 | bi;
    }

    /** Textfarben des Verlaufs (Minecraft-Grün, -Gelb, -Rot). */
    public static final int COLOR_GOOD = 0xFF55FF55;
    public static final int COLOR_MID = 0xFFFFFF55;
    public static final int COLOR_BAD = 0xFFFF5555;

    /** Textfarbe nach Haltbarkeit: voll Grün, halb Gelb, leer Rot, dazwischen stufenlos. */
    public static int durabilityColor(int remaining, int max) {
        double f = fraction(remaining, max);
        return f >= 0.5 ? lerpColor(COLOR_MID, COLOR_GOOD, (f - 0.5) * 2.0) : lerpColor(COLOR_BAD, COLOR_MID, f * 2.0);
    }

    /** Lineare Mischung zweier ARGB-Farben je Kanal (t 0..1, gerundet). */
    public static int lerpColor(int from, int to, double t) {
        double k = Math.max(0.0, Math.min(1.0, t));
        int a = (int) Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * k);
        int r = (int) Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * k);
        int g = (int) Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * k);
        int b = (int) Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** Liegt die Haltbarkeit unter der Warnschwelle? Maßgeblich ist der angezeigte Prozentwert (strikt kleiner). */
    public static boolean isLow(int remaining, int max, int threshold) {
        return max > 0 && percent(remaining, max) < sanitizeThreshold(threshold);
    }

    /** Dauer eines Puls-Durchlaufs der Warnung (dezent, gut 1 s). */
    public static final int WARN_PERIOD_MS = 1200;

    /** Puls der Warnung 0..1 (Kosinus, beginnt bei 0), periodisch mit {@link #WARN_PERIOD_MS}. */
    public static float warnPulse(long timeMs) {
        double phase = Math.floorMod(timeMs, (long) WARN_PERIOD_MS) / (double) WARN_PERIOD_MS;
        return (float) (0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI));
    }

    /** Roter Rahmen der Warnung: Deckkraft pulsiert zwischen 0x50 und 0xF0. */
    public static int warnOutlineColor(float pulse) {
        int a = 0x50 + Math.round(Math.max(0f, Math.min(1f, pulse)) * 0xA0);
        return a << 24 | 0xFF4545;
    }

    /** Leichte rote Füllung hinter dem Item: Deckkraft pulsiert zwischen 0x12 und 0x42. */
    public static int warnFillColor(float pulse) {
        int a = 0x12 + Math.round(Math.max(0f, Math.min(1f, pulse)) * 0x30);
        return a << 24 | 0xFF3030;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Texte (Prozent fest, Zahlen bis 9999 einmal erzeugt; „Zahlen/Max“ baut der Renderer nur bei Änderung neu)
    // ------------------------------------------------------------------------------------------------------------

    private static final String[] PERCENT_TEXT = new String[101];
    static {
        for (int i = 0; i <= 100; i++) PERCENT_TEXT[i] = i + "%";
    }
    private static final String[] NUMBER_TEXT = new String[10000];

    public static String percentText(int pct) {
        return PERCENT_TEXT[Math.max(0, Math.min(100, pct))];
    }

    /** Zahl als Text; 0..9999 aus dem Cache (Render-Thread), größere Werte werden neu erzeugt. */
    public static String numberText(int n) {
        if (n >= 0 && n < NUMBER_TEXT.length) {
            String s = NUMBER_TEXT[n];
            if (s == null) {
                s = Integer.toString(n);
                NUMBER_TEXT[n] = s;
            }
            return s;
        }
        return Integer.toString(n);
    }

    /** Haltbarkeits-Text für einen Modus, {@code null} wenn der Modus keinen Text zeigt. */
    public static String durabilityText(int mode, int remaining, int max) {
        return switch (mode) {
            case DUR_PERCENT, DUR_BAR_PERCENT -> percentText(percent(remaining, max));
            case DUR_NUMBERS -> numberText(remaining);
            case DUR_NUMBERS_MAX -> numberText(remaining) + "/" + numberText(max);
            default -> null;
        };
    }

    /**
     * Längster Text, den ein Item mit diesem Maximum im Modus zeigen kann („100%“, das Maximum bzw. „Max/Max“). Danach
     * richtet der Renderer die Breite der Text-Spalte bzw. der Felder aus, damit das HUD beim Abnutzen nicht schmaler
     * wird und am Hotbar-Anker nicht springt. {@code null} ohne Text.
     */
    public static String widestDurabilityText(int mode, int max) {
        return switch (mode) {
            case DUR_PERCENT, DUR_BAR_PERCENT -> percentText(100);
            case DUR_NUMBERS -> numberText(Math.max(0, max));
            case DUR_NUMBERS_MAX -> numberText(Math.max(0, max)) + "/" + numberText(Math.max(0, max));
            default -> null;
        };
    }

    // ------------------------------------------------------------------------------------------------------------
    // Maße je Stil
    // ------------------------------------------------------------------------------------------------------------

    public static final int ICON = 16;
    /**
     * Kleine Beschriftung unter/über den Icons (Slots, Compact, Minimal). 0,68: „100%“ (25 px) wird 17 px breit und
     * passt mit {@link #LABEL_SPACING} genau ins 20-px-Raster der Hotbar.
     */
    public static final float LABEL_SCALE = 0.68f;
    /** Höhe einer Beschriftungszeile (8 px Schrift * 0,68 plus Schatten, aufgerundet). */
    public static final int LABEL_H = 7;
    public static final int LABEL_GAP = 1;
    /** Mindestabstand zwischen den Beschriftungen benachbarter Felder. */
    public static final int LABEL_SPACING = 3;
    /** Schrifthöhe des normal großen Texts im Stil Vertical. */
    public static final int TEXT_H = 8;

    public static final int V_ROW_H = 20;
    public static final int V_PAD_X = 4;
    public static final int V_PAD_Y = 2;
    public static final int V_TEXT_GAP = 4;
    /** Mindestbreite des Balkens unter dem Prozentwert („Bar + Percent“ im Stil Vertical). */
    public static final int V_BAR_MIN_W = 22;

    /** Slot-Raster wie die Vanilla-Hotbar: 20 px pro Feld, Leiste 22 px hoch, 1 px Außenrand. */
    public static final int S_CELL = 20;
    public static final int S_H = 22;
    /** Abstand der Slot-Leiste zum Hintergrund, wenn ein Hintergrund gezeichnet wird. */
    public static final int S_BG_PAD = 3;

    public static final int C_PITCH = 17;
    public static final int C_PAD = 2;
    public static final int C_BAR_H = 2;
    public static final int C_BAR_GAP = 1;
    /** Breite des dünnen Balkens unter dem Icon im Stil Compact. */
    public static final int C_BAR_W = 14;

    public static final int M_PITCH = 18;
    /** 1 px Rand: die Warn-Markierung reicht 1 px über das Icon hinaus. */
    public static final int M_PAD = 1;

    /** Breite einer Beschriftung im Element (Textbreite * {@link #LABEL_SCALE}, aufgerundet). */
    public static int labelWidth(int textW) {
        return textW <= 0 ? 0 : (int) Math.ceil(textW * LABEL_SCALE - 1e-4);
    }

    /**
     * Maße und Positionen eines Stils für {@code count} Einträge. Wird einmal pro Bild berechnet und wiederverwendet
     * (keine Allokation pro Bild). Alle Werte in unskalierten Element-Pixeln, Ursprung oben links.
     */
    public static final class Metrics {
        public int style;
        public int mode;
        public int count;
        /** Haltbarkeits-Text sichtbar. */
        public boolean text;
        /** Haltbarkeits-Balken sichtbar. */
        public boolean bar;
        /** Balken in Vanilla-Geometrie (im Icon unten, 13x2 mit schwarzem Hintergrund wie im Inventar). */
        public boolean vanillaBar;
        /** Natürliche Größe des Elements. */
        public int width;
        public int height;
        /** Abstand der Felder (waagerecht) bzw. Zeilenhöhe (Vertical). */
        public int pitch;
        /** Vertical: Breite der Text-Spalte. */
        public int textAreaW;
        /** Waagerechte Stile: breiteste Beschriftung (skaliert). */
        public int labelW;
        /** Slots: Rand um die Leiste (nur mit sichtbarem Hintergrund). */
        public int pad;
        /** Slots: linke obere Ecke der Leiste und ihre Breite. */
        public int frameX;
        public int frameY;
        public int frameW;
        /** Compact/Minimal: halbe Breite des breitesten Feldinhalts (Icon oder Beschriftung). */
        public int half;

        public Metrics compute(int style, int mode, int count, int maxTextW, boolean bgVisible) {
            this.style = sanitizeStyle(style);
            this.mode = sanitizeDurability(mode);
            this.count = Math.max(0, count);
            this.text = showsText(this.mode);
            this.bar = showsBar(this.mode);
            int tw = text ? Math.max(0, maxTextW) : 0;
            textAreaW = 0;
            labelW = 0;
            pad = 0;
            frameX = frameY = frameW = 0;
            half = ICON / 2;
            int n = this.count;
            switch (this.style) {
                case STYLE_SLOTS -> {
                    vanillaBar = true;
                    labelW = labelWidth(tw);
                    pitch = Math.max(S_CELL, labelW + LABEL_SPACING);
                    pad = bgVisible ? S_BG_PAD : 0;
                    int labelRow = text ? LABEL_H + LABEL_GAP : 0;
                    frameX = pad;
                    frameY = pad + labelRow;
                    frameW = n * pitch + 2;
                    width = frameW + 2 * pad;
                    height = S_H + labelRow + 2 * pad;
                }
                case STYLE_COMPACT -> {
                    // Kennzeichen des Stils: dünner Balken unter jedem Icon (außer bei „Off“), Text kommt darunter dazu
                    vanillaBar = false;
                    bar = this.mode != DUR_OFF;
                    labelW = labelWidth(tw);
                    pitch = Math.max(C_PITCH, labelW + LABEL_SPACING);
                    half = (Math.max(ICON, labelW) + 1) / 2;
                    int barRow = bar ? C_BAR_GAP + C_BAR_H : 0;
                    int labelRow = text ? LABEL_GAP + LABEL_H : 0;
                    width = n == 0 ? 2 * C_PAD : 2 * C_PAD + (n - 1) * pitch + 2 * half;
                    height = 2 * C_PAD + ICON + barRow + labelRow;
                }
                case STYLE_MINIMAL -> {
                    vanillaBar = true;
                    labelW = labelWidth(tw);
                    pitch = Math.max(M_PITCH, labelW + LABEL_SPACING);
                    half = (Math.max(ICON, labelW) + 1) / 2;
                    int labelRow = text ? LABEL_GAP + LABEL_H : 0;
                    width = n == 0 ? 2 * M_PAD : 2 * M_PAD + (n - 1) * pitch + 2 * half;
                    height = 2 * M_PAD + ICON + labelRow;
                }
                default -> { // STYLE_VERTICAL
                    // Vanilla: Balken im Icon; Bar + Percent: Balken unter dem Prozentwert in der Text-Spalte
                    vanillaBar = this.mode == DUR_VANILLA;
                    pitch = V_ROW_H;
                    textAreaW = text ? Math.max(tw, this.mode == DUR_BAR_PERCENT ? V_BAR_MIN_W : 0) : 0;
                    width = textAreaW > 0 ? V_PAD_X + ICON + V_TEXT_GAP + textAreaW + V_PAD_X : V_PAD_X + ICON + V_PAD_X;
                    height = n * V_ROW_H + 2 * V_PAD_Y;
                }
            }
            return this;
        }

        private boolean horizontal() {
            return style != STYLE_VERTICAL;
        }

        /** Mitte des Feldes {@code i} (waagerechte Stile). */
        public int centerX(int i) {
            return switch (style) {
                case STYLE_SLOTS -> cellX(i) + pitch / 2;
                case STYLE_COMPACT -> C_PAD + half + i * pitch;
                case STYLE_MINIMAL -> M_PAD + half + i * pitch;
                default -> V_PAD_X + ICON / 2;
            };
        }

        public int iconX(int i) {
            return switch (style) {
                case STYLE_SLOTS -> cellX(i) + (pitch - S_CELL) / 2 + 2;
                case STYLE_COMPACT, STYLE_MINIMAL -> centerX(i) - ICON / 2;
                default -> V_PAD_X;
            };
        }

        public int iconY(int i) {
            return switch (style) {
                case STYLE_SLOTS -> frameY + 3;
                case STYLE_COMPACT -> C_PAD;
                case STYLE_MINIMAL -> M_PAD;
                default -> rowY(i) + (V_ROW_H - ICON) / 2;
            };
        }

        /** Vertical: Oberkante der Zeile {@code i}. */
        public int rowY(int i) {
            return V_PAD_Y + i * V_ROW_H;
        }

        /** Slots: linke Kante des Feldes {@code i} in der Leiste (innerhalb des 1-px-Außenrands). */
        public int cellX(int i) {
            return frameX + 1 + i * pitch;
        }

        /**
         * Bereich, den die Warnung markiert: Slots das Slot-Innere, Vertical die ganze Zeile, Compact/Minimal das
         * Icon mit 1 px Rand. Reihenfolge der Werte: x, y, Breite, Höhe.
         */
        public int markX(int i) {
            return switch (style) {
                case STYLE_SLOTS -> iconX(i);
                case STYLE_VERTICAL -> 1;
                default -> iconX(i) - 1;
            };
        }

        public int markY(int i) {
            return switch (style) {
                case STYLE_SLOTS -> iconY(i);
                case STYLE_VERTICAL -> rowY(i);
                default -> iconY(i) - 1;
            };
        }

        public int markW() {
            return switch (style) {
                case STYLE_SLOTS -> ICON + 1;
                case STYLE_VERTICAL -> Math.max(ICON, width - 2);
                default -> ICON + 2;
            };
        }

        public int markH() {
            return switch (style) {
                case STYLE_SLOTS -> ICON + 1;
                case STYLE_VERTICAL -> V_ROW_H;
                default -> ICON + 2;
            };
        }

        /** Vertical: linke Kante des Texts. Waagerechte Stile: Beschriftung zentriert über {@link #centerX}. */
        public int textX(int i, int labelWidth) {
            if (!horizontal()) return V_PAD_X + ICON + V_TEXT_GAP;
            return centerX(i) - labelWidth / 2;
        }

        /** Oberkante des Texts (Vertical normal groß, sonst die kleine Beschriftung). */
        public int textY(int i) {
            return switch (style) {
                case STYLE_SLOTS -> pad; // Beschriftung über der Leiste: die Leiste schließt bündig mit der Hotbar ab
                case STYLE_COMPACT -> C_PAD + ICON + (bar ? C_BAR_GAP + C_BAR_H : 0) + LABEL_GAP;
                case STYLE_MINIMAL -> M_PAD + ICON + LABEL_GAP;
                default -> rowY(i) + (mode == DUR_BAR_PERCENT ? 4 : (V_ROW_H - TEXT_H) / 2);
            };
        }

        public int barX(int i) {
            if (vanillaBar) return iconX(i) + 2;
            if (style == STYLE_COMPACT) return iconX(i) + 1;
            return V_PAD_X + ICON + V_TEXT_GAP;
        }

        public int barY(int i) {
            if (vanillaBar) return iconY(i) + 13;
            if (style == STYLE_COMPACT) return C_PAD + ICON + C_BAR_GAP;
            return rowY(i) + 14;
        }

        public int barW() {
            if (vanillaBar) return 13;
            if (style == STYLE_COMPACT) return C_BAR_W;
            return textAreaW;
        }

        public int barH() {
            return vanillaBar ? 2 : (style == STYLE_COMPACT ? C_BAR_H : 2);
        }
    }

    /** Gefüllte Länge eines eigenen Balkens (nicht Vanilla): mindestens 1 px, solange noch etwas übrig ist. */
    public static int barFill(int barW, int remaining, int max) {
        if (barW <= 0) return 0;
        if (max <= 0) return barW;
        if (remaining <= 0) return 0;
        if (remaining >= max) return barW;
        int fill = (int) Math.round(barW * (remaining / (double) max));
        return Math.max(1, Math.min(barW - 1, fill));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Position auf dem Bildschirm
    // ------------------------------------------------------------------------------------------------------------

    /** Freie Position ohne gespeicherten Wert (-1): waagerecht mittig, unten über Herzen und Rüstungsleiste. */
    public static final int AUTO_BOTTOM_GAP = 52;
    /** Abstand zur Hotbar beim Anker (bei 100 % Hotbar-Größe). */
    public static final int ANCHOR_GAP = 4;
    /** Vanilla-Hotbar: Oberkante des Auswahlrahmens relativ zur Verankerung unten. */
    public static final int HOTBAR_ROW_TOP = -23;

    /** Skalierte Größe wie {@code StatusHudRenderer.clampHudX} (aufgerundet). */
    public static int scaledSize(int size, float scale) {
        return (int) Math.ceil(size * sanitizeScale(scale));
    }

    /** Skalierung aus Datei/Editor: NaN, unendlich oder nicht positiv wird 100 %. */
    public static float sanitizeScale(float scale) {
        return Float.isFinite(scale) && scale > 0f ? scale : 1.0f;
    }

    /** Begrenzt eine Position so, dass das Element ganz im Bild bleibt (zu groß: am Rand 0). */
    public static int clampToScreen(int pos, int scaledSize, int screen) {
        return Math.max(0, Math.min(pos, screen - scaledSize));
    }

    public static int autoX(int screenW, int scaledW) {
        return (screenW - scaledW) / 2;
    }

    public static int autoY(int screenH, int scaledH) {
        return screenH - AUTO_BOTTOM_GAP - scaledH;
    }

    /** Freie Position: gespeicherter Wert oder -1 = automatisch, immer ganz im Bild. */
    public static int freeX(int storedX, int screenW, int scaledW) {
        return clampToScreen(storedX == -1 ? autoX(screenW, scaledW) : storedX, scaledW, screenW);
    }

    public static int freeY(int storedY, int screenH, int scaledH) {
        return clampToScreen(storedY == -1 ? autoY(screenH, scaledH) : storedY, scaledH, screenH);
    }

    /** Linke Kante der Hotbar-Zeile auf dem Bildschirm (wie {@code HotbarLayout.box}, abgerundet). */
    public static int hotbarLeftEdge(int screenW, int offsetX, float hotbarScale, int groupLeft) {
        double x = HotbarLayout.anchorX(screenW) + offsetX + (double) HotbarLayout.clampScale(hotbarScale) * groupLeft;
        return (int) Math.floor(x + 1e-3);
    }

    /** Rechte Kante der Hotbar-Zeile auf dem Bildschirm (aufgerundet). */
    public static int hotbarRightEdge(int screenW, int offsetX, float hotbarScale, int groupRight) {
        double x = HotbarLayout.anchorX(screenW) + offsetX + (double) HotbarLayout.clampScale(hotbarScale) * groupRight;
        return (int) Math.ceil(x - 1e-3);
    }

    /** Unterkante der Hotbar auf dem Bildschirm (bei Vanilla-Position der Bildschirmrand). */
    public static int hotbarBottomEdge(int screenH, int offsetY, float hotbarScale) {
        double y = HotbarLayout.anchorY(screenH) + offsetY + (double) HotbarLayout.clampScale(hotbarScale) * HotbarLayout.BOTTOM;
        return (int) Math.ceil(y - 1e-3);
    }

    /** Abstand zur Hotbar, wächst mit der Hotbar-Größe (mindestens 1 px). */
    public static int anchorGap(float hotbarScale) {
        return Math.max(1, Math.round(ANCHOR_GAP * HotbarLayout.clampScale(hotbarScale)));
    }

    /** Größe beim Anker: eigene Skalierung mal Hotbar-Größe, damit das HUD zur Hotbar passt. */
    public static float anchoredScale(float armorScale, float hotbarScale) {
        return sanitizeScale(armorScale) * HotbarLayout.clampScale(hotbarScale);
    }

    /**
     * Waagerechte Position am Hotbar-Anker: links bzw. rechts neben der Hotbar-Zeile (inklusive Offhand-Feld und
     * Angriffsanzeige) mit {@code gap} Abstand. Passt das Element auf der gewünschten Seite nicht ins Bild, kommt es
     * auf die andere Seite; passt es auf keiner, wird es am Rand begrenzt.
     */
    public static int anchoredX(int anchor, int hotbarLeft, int hotbarRight, int scaledW, int screenW, int gap) {
        int leftX = hotbarLeft - gap - scaledW;
        int rightX = hotbarRight + gap;
        boolean leftFits = leftX >= 0;
        boolean rightFits = rightX + scaledW <= screenW;
        if (anchor == ANCHOR_HOTBAR_RIGHT) {
            if (rightFits) return rightX;
            if (leftFits) return leftX;
            return clampToScreen(rightX, scaledW, screenW);
        }
        if (leftFits) return leftX;
        if (rightFits) return rightX;
        return clampToScreen(leftX, scaledW, screenW);
    }

    /** Senkrechte Position am Hotbar-Anker: unten bündig mit der Hotbar, ganz im Bild. */
    public static int anchoredY(int hotbarBottom, int scaledH, int screenH) {
        return clampToScreen(hotbarBottom - scaledH, scaledH, screenH);
    }
}
