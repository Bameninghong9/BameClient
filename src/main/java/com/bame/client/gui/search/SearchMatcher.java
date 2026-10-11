package com.bame.client.gui.search;

import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/**
 * Shared search logic for the Key Binds and Statistics search bars.
 * <ul>
 *     <li>query is trimmed and lower-cased ({@link Locale#ROOT}); an empty query matches everything</li>
 *     <li>multiple words are AND-combined: every word has to occur somewhere in the text</li>
 *     <li>formatting codes are stripped; umlauts are compared tolerantly (ä→a, ö→o, ü→u, ß→ss)</li>
 * </ul>
 */
public final class SearchMatcher {
    private static final SearchMatcher EMPTY = new SearchMatcher(new String[0]);

    private final String[] words;
    private final String[] foldedWords;

    private SearchMatcher(String[] words) {
        this.words = words;
        this.foldedWords = new String[words.length];
        for (int i = 0; i < words.length; i++) {
            this.foldedWords[i] = fold(words[i]);
        }
    }

    public static SearchMatcher of(String query) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) return EMPTY;
        return new SearchMatcher(normalized.split("\s+"));
    }

    public boolean isEmpty() {
        return this.words.length == 0;
    }

    /** Compares against the displayed (translated) text. */
    public boolean matches(Text text) {
        if (this.isEmpty()) return true;
        return text != null && this.matches(text.getString());
    }

    public boolean matches(String text) {
        if (this.isEmpty()) return true;
        if (text == null) return false;
        String lower = normalize(text);
        if (lower.isEmpty()) return false;
        String folded = fold(lower);
        for (int i = 0; i < this.words.length; i++) {
            if (!lower.contains(this.words[i]) && !folded.contains(this.foldedWords[i])) {
                return false;
            }
        }
        return true;
    }

    static String normalize(String text) {
        if (text == null) return "";
        String stripped = Formatting.strip(text);
        if (stripped == null) return "";
        // Non-breaking spaces (used by some translations) count as normal spaces
        return stripped.replace('\u00A0', ' ').replace('\u202F', ' ').trim().toLowerCase(Locale.ROOT);
    }

    static String fold(String lower) {
        if (lower.isEmpty()) return lower;
        StringBuilder sb = new StringBuilder(lower.length() + 4);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            switch (c) {
                case '\u00E4' -> sb.append('a'); // ä
                case '\u00F6' -> sb.append('o'); // ö
                case '\u00FC' -> sb.append('u'); // ü
                case '\u00DF', '\u1E9E' -> sb.append("ss"); // ß, ẞ
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
