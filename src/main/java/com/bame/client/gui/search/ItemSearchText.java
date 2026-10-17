package com.bame.client.gui.search;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Text-Normalisierung der Container-Suche ({@link ContainerSearch}). Reine Java-Logik ohne Minecraft-Klassen,
 * getestet in {@code src/plannerTest} (Aufgabe {@code containerSearchTest}).
 *
 * <ul>
 *     <li>{@link #clean}: Formatierungscodes ({@code §} + Zeichen) entfernen, Unicode-NFKC (Vollbreite, Ligaturen,
 *     geschützte Leerzeichen), Kapitälchen aus Server-Menüs („ᴅɪᴀᴍᴏɴᴅ“) zu normalen Buchstaben, Kleinbuchstaben
 *     ({@link Locale#ROOT}).</li>
 *     <li>{@link #foldPlain}: Akzente und Umlaute ohne Zusatz (ä→a, é→e, ß→ss).</li>
 *     <li>{@link #foldExpanded}: deutsche Umschreibung (ä→ae, ö→oe, ü→ue, ß→ss), übrige Akzente wie
 *     {@link #foldPlain}. Damit findet „kuerbis“ wie „kurbis“ und „kürbis“ den „Kürbis“.</li>
 *     <li>{@link #matchesWord}: ein Suchwort passt am Wortanfang (Wortgrenze = Zeichen davor ist kein Buchstabe und
 *     keine Ziffer, z. B. Leerzeichen, {@code _}, {@code :}) oder – wenn erlaubt und ab 3 Zeichen – irgendwo.</li>
 * </ul>
 * Reine ASCII-Texte (englische Namen, IDs) werden ohne Kopie durchgereicht.
 */
public final class ItemSearchText {
    /** Ab dieser Länge (Zeichen des Suchworts) darf ein Wort auch mitten im Namen stehen. */
    public static final int ANYWHERE_MIN_LENGTH = 3;

    private ItemSearchText() {}

    /** Formatierungscodes entfernen, NFKC, Kapitälchen → Buchstaben, Kleinbuchstaben. {@code null} wird leer. */
    public static String clean(String text) {
        if (text == null || text.isEmpty()) return "";
        String s = stripFormatting(text);
        if (!isAscii(s)) {
            s = Normalizer.normalize(s, Normalizer.Form.NFKC);
            s = mapSmallCaps(s);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /** Variante ohne Zusatzbuchstaben: ä→a, ö→o, ü→u, é→e, ß→ss, æ→ae, œ→oe, ø→o … (erwartet {@link #clean}-Text). */
    public static String foldPlain(String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) return "";
        if (isAscii(cleaned)) return cleaned;
        StringBuilder sb = new StringBuilder(cleaned.length() + 4);
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            switch (c) {
                case 'ß', 'ẞ' -> sb.append("ss"); // ß, ẞ
                case 'æ' -> sb.append("ae");           // æ
                case 'œ' -> sb.append("oe");           // œ
                case 'ø' -> sb.append('o');            // ø
                case 'ł' -> sb.append('l');            // ł
                case 'đ', 'ð' -> sb.append('d');  // đ, ð
                case 'ı' -> sb.append('i');            // ı (türkisches i ohne Punkt)
                case 'þ' -> sb.append("th");           // þ
                default -> sb.append(c);
            }
        }
        String decomposed = Normalizer.normalize(sb, Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) out.append(c);
        }
        // Nicht-lateinische Schriften (z. B. Hangul) wieder zusammensetzen; Akzente sind schon entfernt
        return Normalizer.normalize(out, Normalizer.Form.NFC);
    }

    /** Variante mit deutscher Umschreibung: ä→ae, ö→oe, ü→ue, sonst wie {@link #foldPlain}. */
    public static String foldExpanded(String cleaned) {
        if (cleaned == null || cleaned.isEmpty()) return "";
        if (isAscii(cleaned)) return cleaned;
        if (cleaned.indexOf('ä') < 0 && cleaned.indexOf('ö') < 0 && cleaned.indexOf('ü') < 0) {
            return foldPlain(cleaned);
        }
        StringBuilder sb = new StringBuilder(cleaned.length() + 4);
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            switch (c) {
                case 'ä' -> sb.append("ae"); // ä
                case 'ö' -> sb.append("oe"); // ö
                case 'ü' -> sb.append("ue"); // ü
                default -> sb.append(c);
            }
        }
        return foldPlain(sb.toString());
    }

    /**
     * Passt {@code word} in {@code text}? Am Wortanfang immer; irgendwo nur, wenn {@code anywhere} gesetzt ist (der
     * Aufrufer prüft die Mindestlänge {@link #ANYWHERE_MIN_LENGTH} am ursprünglichen Suchwort). Leeres Wort passt.
     */
    public static boolean matchesWord(String text, String word, boolean anywhere) {
        if (word == null || word.isEmpty()) return true;
        if (text == null || text.length() < word.length()) return false;
        if (anywhere) return text.contains(word);
        return containsAtWordStart(text, word);
    }

    /** Steht {@code word} irgendwo in {@code text} direkt nach einer Wortgrenze (oder am Anfang)? */
    public static boolean containsAtWordStart(String text, String word) {
        if (word == null || word.isEmpty()) return true;
        if (text == null) return false;
        for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + 1)) {
            if (i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1))) return true;
        }
        return false;
    }

    /** Formatierungscodes ({@code §} und das folgende Zeichen) entfernen; ohne {@code §} wird nichts kopiert. */
    static String stripFormatting(String s) {
        int first = s.indexOf('§');
        if (first < 0) return s;
        StringBuilder sb = new StringBuilder(s.length());
        sb.append(s, 0, first);
        for (int i = first; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++; // Code-Zeichen überspringen
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x80) return false;
        }
        return true;
    }

    /** Kapitälchen (in Server-Menüs beliebt) auf normale Buchstaben abbilden; ohne Kapitälchen keine Kopie. */
    static String mapSmallCaps(String s) {
        int first = -1;
        for (int i = 0; i < s.length(); i++) {
            if (smallCap(s.charAt(i)) != null) {
                first = i;
                break;
            }
        }
        if (first < 0) return s;
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append(s, 0, first);
        for (int i = first; i < s.length(); i++) {
            char c = s.charAt(i);
            String mapped = smallCap(c);
            if (mapped != null) sb.append(mapped);
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String smallCap(char c) {
        return switch (c) {
            case 'ᴀ' -> "a";
            case 'ᴁ', 'ᴂ' -> "ae";
            case 'ʙ', 'ᴃ' -> "b";
            case 'ᴄ' -> "c";
            case 'ᴅ', 'ᴆ' -> "d";
            case 'ᴇ', 'ᴈ' -> "e";
            case 'ꜰ' -> "f";
            case 'ɢ', 'ʛ', 'ᴦ' -> "g";
            case 'ʜ' -> "h";
            case 'ɪ' -> "i";
            case 'ᴊ' -> "j";
            case 'ᴋ' -> "k";
            case 'ʟ', 'ᴌ', 'ᴧ' -> "l";
            case 'ᴍ' -> "m";
            case 'ɴ', 'ᴎ' -> "n";
            case 'ᴏ', 'ᴐ', 'ᴑ', 'ᴒ', 'ᴓ' -> "o";
            case 'ɶ', 'ᴔ' -> "oe";
            case 'ᴘ', 'ᴨ', 'ᴩ' -> "p";
            case 'ꞯ' -> "q";
            case 'ʀ', 'ʁ', 'ᴙ', 'ᴚ', 'ᴫ' -> "r";
            case 'ꜱ' -> "s";
            case 'ᴛ' -> "t";
            case 'ᴜ', 'ᴝ', 'ᴞ' -> "u";
            case 'ᴠ' -> "v";
            case 'ᴡ' -> "w";
            case 'ʏ' -> "y";
            case 'ᴢ' -> "z";
            default -> null;
        };
    }
}
