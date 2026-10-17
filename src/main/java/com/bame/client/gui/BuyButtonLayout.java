package com.bame.client.gui;

import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Kauf-Button („Pille“ mit Jeton und Preis) unten mittig auf den Karten nicht gekaufter Items im Cosmetics-Menü:
 * reine Rechnung ohne Minecraft (Preis-Text, Platzierung, Treffer-Test), getestet in {@code src/plannerTest}
 * (Gradle-Aufgabe {@code buyButtonTest}). Alle Werte in GUI-Pixeln; Positionen relativ zur linken oberen Kartenecke.
 *
 * <p>Platzierung: Die Pille sitzt {@link #BOTTOM_MARGIN} über der Kartenunterkante, waagerecht mittig. Passt der volle
 * Preis nicht, wird schrittweise kompakter gezeichnet (engere Innenabstände, „1,5k“ statt „1.500“, zuletzt ohne
 * Jeton). Passt auch das nicht, gibt es keinen Button (dann bleibt nur das Schloss, die Karte selbst öffnet weiter die
 * Kauf-Bestätigung). Die Pille samt Hover-Leuchten bleibt immer in der Karte und unter der Kopfzeile (Name, Schloss).
 */
public final class BuyButtonLayout {
    /** Höhe der Pille. */
    public static final int HEIGHT = 14;
    /** Abstand zur Kartenunterkante. */
    public static final int BOTTOM_MARGIN = 4;
    /** Mindestabstand zum linken bzw. rechten Kartenrand. */
    public static final int SIDE_MARGIN = 4;
    /** So weit ragt das Leuchten beim Hovern über die Pille hinaus. */
    public static final int GLOW = 2;
    /** Kopfzeile der Karte mit Name, Schloss und Stift-Knopf (y 0 bis hier, exklusiv). */
    public static final int TOP_RESERVED = 18;
    /** Abstand zwischen Vorschau-Unterkante und Pille. */
    public static final int PREVIEW_GAP = 2;
    /** So viel Höhe muss zwischen Kopfzeile und Pille für die Vorschau bleiben, sonst kein Button. */
    public static final int MIN_PREVIEW_HEIGHT = 24;

    /** Innenabstand links/rechts (normal, eng, nur Preis), Jeton-Größe und Abstand Jeton → Preis (normal bzw. eng). */
    public static final int PAD = 6, PAD_TIGHT = 4, PAD_MIN = 3;
    public static final int COIN = 10, COIN_TIGHT = 8;
    public static final int GAP = 3, GAP_TIGHT = 2;
    /** Oberkante der Schrift in der Pille (8 px hohe Ziffern, mittig). */
    public static final int TEXT_DY = 3;

    /** Varianten von breit nach schmal. */
    public static final int VARIANT_FULL = 0;
    public static final int VARIANT_FULL_TIGHT = 1;
    public static final int VARIANT_COMPACT = 2;
    public static final int VARIANT_COMPACT_TIGHT = 3;
    public static final int VARIANT_TEXT_ONLY = 4;

    /** Ergebnis des Treffer-Tests auf einer Karte. */
    public static final int HIT_NONE = 0;
    public static final int HIT_CARD = 1;
    public static final int HIT_BUTTON = 2;

    /** Kill-Effekt-Karte: Abstand des „Test“-Knopfs zum rechten bzw. unteren Rand (wie bisher). */
    public static final int TEST_RIGHT = 5;
    public static final int TEST_BOTTOM = 4;

    private BuyButtonLayout() {}

    /**
     * Lage der Pille und ihres Inhalts (alles relativ zur Karte).
     *
     * @param compactLabel kompakten Preis („1,5k“) statt des vollen zeichnen
     * @param coinSize     Größe des Jetons, 0 = ohne Jeton
     */
    public record Placement(int x, int y, int w, int h, int variant, boolean compactLabel,
                            int coinX, int coinY, int coinSize, int textX, int textY, int textW) {
        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        /** Liegt der Punkt (relativ zur Karte) auf der Pille? Rechte/untere Kante exklusiv. */
        public boolean contains(double relX, double relY) {
            return relX >= x && relX < x + w && relY >= y && relY < y + h;
        }
    }

    /**
     * Platziert die Pille auf einer Karte.
     *
     * @param fullTextW    Breite des vollen Preises („1.500“)
     * @param compactTextW Breite des kompakten Preises („1,5k“); ≤ 0 oder ≥ fullTextW = keine kürzere Form
     * @return Lage oder {@code null}, wenn auf der Karte kein Button Platz hat
     */
    public static Placement place(int cardW, int cardH, int fullTextW, int compactTextW) {
        if (cardW <= 0 || cardH <= 0 || fullTextW <= 0) return null;
        int y = cardH - BOTTOM_MARGIN - HEIGHT;
        // Kopfzeile (inkl. Leuchten) frei lassen und Platz für die Vorschau übrig behalten
        if (y - PREVIEW_GAP - TOP_RESERVED < MIN_PREVIEW_HEIGHT || y - GLOW < TOP_RESERVED) return null;
        int maxW = cardW - 2 * SIDE_MARGIN;
        boolean shorter = compactTextW > 0 && compactTextW < fullTextW;

        Placement p = tryPlace(cardW, y, maxW, VARIANT_FULL, false, fullTextW, PAD, COIN, GAP);
        if (p == null) p = tryPlace(cardW, y, maxW, VARIANT_FULL_TIGHT, false, fullTextW, PAD_TIGHT, COIN_TIGHT, GAP_TIGHT);
        if (p == null && shorter) p = tryPlace(cardW, y, maxW, VARIANT_COMPACT, true, compactTextW, PAD, COIN, GAP);
        if (p == null && shorter) {
            p = tryPlace(cardW, y, maxW, VARIANT_COMPACT_TIGHT, true, compactTextW, PAD_TIGHT, COIN_TIGHT, GAP_TIGHT);
        }
        if (p == null) {
            p = tryPlace(cardW, y, maxW, VARIANT_TEXT_ONLY, shorter, shorter ? compactTextW : fullTextW, PAD_MIN, 0, 0);
        }
        return p;
    }

    private static Placement tryPlace(int cardW, int y, int maxW, int variant, boolean compact, int textW,
                                      int pad, int coin, int gap) {
        int contentW = (coin > 0 ? coin + gap : 0) + textW;
        int w = Math.max(HEIGHT, pad + contentW + pad);
        if (w > maxW) return null;
        int x = (cardW - w) / 2;
        int contentX = x + (w - contentW) / 2;
        int coinX = coin > 0 ? contentX : 0;
        int coinY = coin > 0 ? y + (HEIGHT - coin) / 2 : 0;
        int textX = coin > 0 ? contentX + coin + gap : contentX;
        return new Placement(x, y, w, HEIGHT, variant, compact, coinX, coinY, coin, textX, y + TEXT_DY, textW);
    }

    /**
     * Treffer-Test auf einer Karte (Punkt relativ zur Karte): Pille vor Karte, damit ein Klick auf den Button nicht
     * zusätzlich die Karte auslöst.
     */
    public static int hitTarget(Placement button, double relX, double relY, int cardW, int cardH) {
        if (relX < 0 || relY < 0 || relX >= cardW || relY >= cardH) return HIT_NONE;
        if (button != null && button.contains(relX, relY)) return HIT_BUTTON;
        return HIT_CARD;
    }

    /**
     * Oberkante des „Test“-Knopfs (rechts unten) auf einer Kill-Effekt-Karte. Ohne Pille wie bisher: eine Zeile höher
     * (−15), wenn er nicht neben das linke Element ({@code leftW}, z. B. EQUIPPED) passt. Mit Pille: gleiche Zeile,
     * wenn er rechts neben die Pille samt Leuchten passt, sonst direkt über der Pille.
     */
    public static int testButtonY(int cardW, int cardH, int testW, int testH, Placement pill, int leftW) {
        int y = cardH - testH - TEST_BOTTOM;
        if (pill != null) {
            int testX = cardW - testW - TEST_RIGHT;
            return pill.right() + GLOW + 1 > testX ? pill.y() - PREVIEW_GAP - testH : y;
        }
        boolean compact = leftW > 0 && 5 + leftW + 6 + testW + TEST_RIGHT > cardW;
        return compact ? y - 15 : y;
    }

    /** Unterkante einer Vorschau (relativ zur Karte): mit Pille darüber, sonst unverändert. */
    public static int previewBottom(Placement pill, int defaultBottom) {
        return pill == null ? defaultBottom : Math.min(defaultBottom, pill.y() - PREVIEW_GAP);
    }

    /**
     * Maßstab für einen verkleinerten Vorschau-Bereich (oben gleich, unten {@code bottom} statt {@code defaultBottom}):
     * proportional kleiner, damit die Vorschau wie bisher in ihren Bereich passt und nichts abgeschnitten wird.
     */
    public static float fitScale(float scale, int top, int defaultBottom, int bottom) {
        int oldH = defaultBottom - top;
        int newH = bottom - top;
        if (oldH <= 0 || newH >= oldH) return scale;
        return scale * Math.max(0, newH) / oldH;
    }

    // ==========================================
    // Preis-Text
    // ==========================================

    /** Tausender- und Dezimaltrennzeichen. */
    public record Separators(char grouping, char decimal) {}

    public static final Separators ENGLISH = new Separators(',', '.');
    public static final Separators DOT_COMMA = new Separators('.', ',');
    public static final Separators SPACE_COMMA = new Separators(' ', ',');

    /** Ganze Zahl mit Tausendertrennzeichen („1.500“ bzw. „1,500“). */
    public static String formatGrouped(int value, char grouping) {
        long v = value;
        boolean negative = v < 0;
        String digits = Long.toString(Math.abs(v));
        int len = digits.length();
        StringBuilder sb = new StringBuilder(len + len / 3 + 1);
        if (negative) sb.append('-');
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) sb.append(grouping);
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    /**
     * Kompakte Form für schmale Karten: unter 1000 unverändert, sonst „1,5k“, „12k“, „1,2M“, „2,1B“. Abgeschnitten
     * statt gerundet (zeigt nie mehr als den echten Preis); eine Nachkommastelle nur unter 10 der Einheit.
     */
    public static String formatCompact(int value, char decimal) {
        long v = Math.abs((long) value);
        StringBuilder sb = new StringBuilder(8);
        if (value < 0) sb.append('-');
        if (v < 1_000L) return sb.append(v).toString();
        long unit;
        char suffix;
        if (v >= 1_000_000_000L) {
            unit = 1_000_000_000L;
            suffix = 'B';
        } else if (v >= 1_000_000L) {
            unit = 1_000_000L;
            suffix = 'M';
        } else {
            unit = 1_000L;
            suffix = 'k';
        }
        long whole = v / unit;
        sb.append(whole);
        if (whole < 10) {
            long tenth = (v % unit) * 10 / unit;
            if (tenth > 0) sb.append(decimal).append(tenth);
        }
        return sb.append(suffix).toString();
    }

    /** Hat die Java-Laufzeit Gebietsschema-Daten (CLDR)? Sonst greift {@link #fallbackSeparators}. */
    private static final boolean JDK_LOCALE_DATA = hasLocaleData();

    private static boolean hasLocaleData() {
        try {
            return DecimalFormatSymbols.getInstance(Locale.GERMANY).getDecimalSeparator() == ',';
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Trennzeichen zur Spielsprache (Minecraft-Code wie {@code de_de}, {@code en_us}, {@code fr_fr}). Nur Zeichen, die
     * die Menü-Schrift sicher hat: Punkt, Komma, Leerzeichen (statt schmalem Leerzeichen) und Apostroph (Schweiz).
     * Unbekannte Sprachen bzw. Schriften mit eigenen Zeichen: englische Schreibweise.
     */
    public static Separators separatorsFor(String languageCode) {
        Locale locale = localeOf(languageCode);
        if (locale == null) return ENGLISH;
        if (JDK_LOCALE_DATA) {
            try {
                DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
                Separators s = normalize(symbols.getGroupingSeparator(), symbols.getDecimalSeparator());
                if (s != null) return s;
            } catch (Throwable ignored) {
                // unten: feste Tabelle
            }
        }
        return fallbackSeparators(locale.getLanguage());
    }

    /** Minecraft-Sprachcode → Locale ({@code de_de} → de-DE), {@code null} bei leerem Code. */
    static Locale localeOf(String code) {
        if (code == null) return null;
        String c = code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        int us = c.indexOf('_');
        String language = us < 0 ? c : c.substring(0, us);
        String country = us < 0 ? "" : c.substring(us + 1);
        if (language.isEmpty()) return null;
        return country.isEmpty() ? Locale.of(language) : Locale.of(language, country.toUpperCase(Locale.ROOT));
    }

    // Trennzeichen aus den Gebietsschema-Daten, die auf sichere Zeichen abgebildet werden
    private static final char SPACE = ' ';
    private static final char NBSP = (char) 0x00A0;
    private static final char NARROW_NBSP = (char) 0x202F;
    private static final char FIGURE_SPACE = (char) 0x2007;
    private static final char PUNCTUATION_SPACE = (char) 0x2008;
    private static final char THIN_SPACE = (char) 0x2009;
    private static final char APOSTROPHE = '\'';
    private static final char RIGHT_QUOTE = (char) 0x2019;
    private static final char MODIFIER_APOSTROPHE = (char) 0x02BC;

    /** Bringt Trennzeichen auf die sicheren Zeichen; {@code null}, wenn das nicht geht. */
    static Separators normalize(char grouping, char decimal) {
        char g = switch (grouping) {
            case '.', ',' -> grouping;
            case SPACE, NBSP, NARROW_NBSP, FIGURE_SPACE, PUNCTUATION_SPACE, THIN_SPACE -> SPACE;
            case APOSTROPHE, RIGHT_QUOTE, MODIFIER_APOSTROPHE -> APOSTROPHE; // Schweiz
            default -> 0;
        };
        char d = (decimal == '.' || decimal == ',') ? decimal : 0;
        if (g == 0 || d == 0 || g == d) return null;
        return new Separators(g, d);
    }

    /** Feste Tabelle, falls die Laufzeit keine Gebietsschema-Daten mitbringt (nur Sprache, ohne Land). */
    static Separators fallbackSeparators(String language) {
        if (language == null) return ENGLISH;
        return switch (language) {
            case "de", "nl", "it", "es", "pt", "da", "tr", "id", "el", "ro", "hr", "sl", "sr", "vi", "is", "ca", "gl",
                 "eu", "az", "mk", "bs" -> DOT_COMMA;
            case "fr", "ru", "pl", "cs", "sk", "sv", "fi", "nb", "no", "nn", "uk", "hu", "bg", "lt", "lv", "et", "be",
                 "kk" -> SPACE_COMMA;
            default -> ENGLISH;
        };
    }
}
