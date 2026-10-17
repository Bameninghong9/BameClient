package com.bame.client.gui.search;

import java.util.ArrayList;
import java.util.List;

/**
 * Suchtext der Container-Suche (reine Java-Logik, getestet in {@code src/plannerTest}).
 *
 * <p>Regeln:
 * <ul>
 *     <li>Groß-/Kleinschreibung egal, Formatierungscodes und Akzente egal; Umlaute passen als ä=a und ä=ae
 *     ({@link ItemSearchText}).</li>
 *     <li>Mehrere Wörter (durch Leerzeichen getrennt) sind UND-verknüpft; jedes Wort darf in einem anderen Feld
 *     desselben Items passen (z. B. „diamond sharp“: Name + Verzauberung).</li>
 *     <li>Name (angezeigter Name, bei umbenannten Items zusätzlich der Grundname) und Registry-ID-Pfad
 *     ({@code golden_apple}): ein Wort darin beginnt mit dem Suchwort, oder das Suchwort (ab 3 Zeichen) steht
 *     irgendwo darin. Der Namespace ({@code minecraft:}) zählt nur, wenn das Suchwort einen Doppelpunkt enthält.</li>
 *     <li>Verzauberungen und Lore (nur mit {@code extras}): nur am Wortanfang.</li>
 *     <li>Inhalt (nur mit {@code contents}): ein Container-Item passt auch, wenn eines der enthaltenen Items allein
 *     auf den ganzen Suchtext passt.</li>
 *     <li>Ein leerer Suchtext passt auf alles ({@link #isEmpty()}: dann wird nichts ausgegraut).</li>
 * </ul>
 */
public final class ItemSearchQuery {
    public static final ItemSearchQuery EMPTY = new ItemSearchQuery("", new String[0], new String[0], new boolean[0], new boolean[0]);

    private final String normalized;
    private final String[] plain;
    private final String[] expanded;
    /** Darf das Wort mitten im Namen/in der ID stehen (ab {@link ItemSearchText#ANYWHERE_MIN_LENGTH} Zeichen)? */
    private final boolean[] anywhere;
    private final boolean[] hasColon;

    private ItemSearchQuery(String normalized, String[] plain, String[] expanded, boolean[] anywhere, boolean[] hasColon) {
        this.normalized = normalized;
        this.plain = plain;
        this.expanded = expanded;
        this.anywhere = anywhere;
        this.hasColon = hasColon;
    }

    public static ItemSearchQuery parse(String raw) {
        String cleaned = ItemSearchText.clean(raw);
        List<String> words = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= cleaned.length(); i++) {
            boolean separator = i == cleaned.length() || Character.isWhitespace(cleaned.charAt(i)) || Character.isSpaceChar(cleaned.charAt(i));
            if (separator) {
                if (start >= 0) {
                    words.add(cleaned.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        if (words.isEmpty()) return EMPTY;

        int n = words.size();
        String[] plain = new String[n];
        String[] expanded = new String[n];
        boolean[] anywhere = new boolean[n];
        boolean[] hasColon = new boolean[n];
        for (int i = 0; i < n; i++) {
            String word = words.get(i);
            plain[i] = ItemSearchText.foldPlain(word);
            String exp = ItemSearchText.foldExpanded(word);
            expanded[i] = exp.equals(plain[i]) ? plain[i] : exp; // gleiche Instanz = keine zweite Prüfung
            anywhere[i] = word.codePointCount(0, word.length()) >= ItemSearchText.ANYWHERE_MIN_LENGTH;
            hasColon[i] = word.indexOf(':') >= 0;
        }
        return new ItemSearchQuery(String.join(" ", words), plain, expanded, anywhere, hasColon);
    }

    public boolean isEmpty() {
        return this.plain.length == 0;
    }

    /** Normalisierte Wörter, mit einem Leerzeichen verbunden (gleicher Text = gleiches Ergebnis). */
    public String normalized() {
        return this.normalized;
    }

    public int wordCount() {
        return this.plain.length;
    }

    /**
     * Passt das Item auf den Suchtext?
     *
     * @param extras   Verzauberungen und Lore mit durchsuchen
     * @param contents Inhalt von Shulker-Boxen/Bundles mit durchsuchen (auch verschachtelt, soweit gebaut)
     */
    public boolean matches(ItemSearchEntry entry, boolean extras, boolean contents) {
        if (this.isEmpty()) return true;
        if (entry == null) return false;
        if (this.matchesSelf(entry, extras)) return true;
        if (contents) {
            for (ItemSearchEntry child : entry.children) {
                if (this.matches(child, extras, true)) return true;
            }
        }
        return false;
    }

    private boolean matchesSelf(ItemSearchEntry entry, boolean extras) {
        for (int w = 0; w < this.plain.length; w++) {
            if (!this.wordMatches(entry, w, extras)) return false;
        }
        return true;
    }

    private boolean wordMatches(ItemSearchEntry entry, int w, boolean extras) {
        String p = this.plain[w];
        String x = this.expanded[w];
        boolean any = this.anywhere[w];
        for (ItemSearchEntry.Field field : entry.nameFields) {
            if (field.matches(p, x, any)) return true;
        }
        if (ItemSearchText.matchesWord(entry.idPath, p, any)) return true;
        if (this.hasColon[w] && ItemSearchText.matchesWord(entry.fullId, p, any)) return true;
        if (extras) {
            for (ItemSearchEntry.Field field : entry.extraFields) {
                if (field.matches(p, x, false)) return true;
            }
        }
        return false;
    }
}
