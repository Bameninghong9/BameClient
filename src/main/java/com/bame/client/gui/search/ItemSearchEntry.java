package com.bame.client.gui.search;

import java.util.List;
import java.util.Locale;

/**
 * Durchsuchbare Daten eines Items für die Container-Suche, schon normalisiert ({@link ItemSearchText}). Reine
 * Java-Logik: {@link ContainerSearch} baut die Einträge aus {@code ItemStack}s (Name, Grundname, Registry-ID,
 * Verzauberungen, Lore, Inhalt von Shulker-Boxen/Bundles), die Tests aus einfachen Strings.
 *
 * <p>Ein Eintrag wird pro Slot-Inhalt einmal gebaut und im {@link SlotMatchCache} gehalten; neue Suchtexte prüfen
 * nur noch die fertigen Felder ({@link ItemSearchQuery#matches}).
 */
public final class ItemSearchEntry {
    /** Obergrenze für gemerkten Inhalt (Shulker/Bundle), damit riesige Mod-Container nichts ausbremsen. */
    public static final int MAX_CHILDREN = 256;
    private static final Field[] NO_FIELDS = new Field[0];
    private static final ItemSearchEntry[] NO_CHILDREN = new ItemSearchEntry[0];

    /** Ein normalisierter Text in beiden Umlaut-Varianten. */
    static final class Field {
        final String plain;
        final String expanded;

        Field(String raw) {
            String cleaned = ItemSearchText.clean(raw);
            this.plain = ItemSearchText.foldPlain(cleaned);
            String exp = ItemSearchText.foldExpanded(cleaned);
            this.expanded = exp.equals(this.plain) ? this.plain : exp;
        }

        /** {@code wordExpanded} ist dieselbe Instanz wie {@code wordPlain}, wenn beide gleich sind ({@link ItemSearchQuery}). */
        boolean matches(String wordPlain, String wordExpanded, boolean anywhere) {
            if (ItemSearchText.matchesWord(this.plain, wordPlain, anywhere)) return true;
            if (this.expanded == this.plain && wordExpanded == wordPlain) return false;
            return ItemSearchText.matchesWord(this.expanded, wordExpanded, anywhere);
        }
    }

    /** Angezeigter Name und (bei umbenannten Items) der Grundname: Wortanfang oder ab 3 Zeichen irgendwo. */
    final Field[] nameFields;
    /** Pfad der Registry-ID ohne Namespace, z. B. {@code golden_apple} (gleiche Regel wie der Name). */
    final String idPath;
    /** Vollständige ID, z. B. {@code minecraft:golden_apple} (nur für Suchwörter mit {@code :}). */
    final String fullId;
    /** Verzauberungen und Lore: nur am Wortanfang (Shop-Lore wie „Price: …“ soll nicht überall treffen). */
    final Field[] extraFields;
    /** Inhalt (Shulker-Box, Bundle), schon als eigene Einträge. */
    final ItemSearchEntry[] children;

    private ItemSearchEntry(Field[] nameFields, String idPath, String fullId, Field[] extraFields, ItemSearchEntry[] children) {
        this.nameFields = nameFields;
        this.idPath = idPath;
        this.fullId = fullId;
        this.extraFields = extraFields;
        this.children = children;
    }

    /**
     * @param name     angezeigter Name (Custom-Name oder lokalisierter Item-Name)
     * @param baseName lokalisierter Item-Name, falls {@code name} ein Custom-Name ist (sonst {@code null})
     * @param id       Registry-ID wie {@code minecraft:golden_apple} (ohne Namespace = {@code minecraft})
     * @param extras   Verzauberungen und Lore-Zeilen (oder {@code null})
     * @param children Inhalt als eigene Einträge (oder {@code null})
     */
    public static ItemSearchEntry of(String name, String baseName, String id, List<String> extras, List<ItemSearchEntry> children) {
        Field[] names;
        if (baseName != null && !baseName.isEmpty() && !baseName.equals(name)) {
            names = new Field[]{new Field(name), new Field(baseName)};
        } else {
            names = new Field[]{new Field(name)};
        }

        String full = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        int colon = full.indexOf(':');
        String path = colon >= 0 ? full.substring(colon + 1) : full;
        if (colon < 0 && !full.isEmpty()) full = "minecraft:" + full;

        Field[] extraFields = NO_FIELDS;
        if (extras != null && !extras.isEmpty()) {
            int count = 0;
            Field[] tmp = new Field[extras.size()];
            for (String line : extras) {
                if (line == null || line.isBlank()) continue;
                tmp[count++] = new Field(line);
            }
            extraFields = count == tmp.length ? tmp : java.util.Arrays.copyOf(tmp, count);
        }

        ItemSearchEntry[] kids = NO_CHILDREN;
        if (children != null && !children.isEmpty()) {
            int n = Math.min(children.size(), MAX_CHILDREN);
            int count = 0;
            ItemSearchEntry[] tmp = new ItemSearchEntry[n];
            for (int i = 0; i < n; i++) {
                ItemSearchEntry child = children.get(i);
                if (child != null) tmp[count++] = child;
            }
            kids = count == n ? tmp : java.util.Arrays.copyOf(tmp, count);
        }
        return new ItemSearchEntry(names, path, full, extraFields, kids);
    }

    public boolean hasChildren() {
        return this.children.length > 0;
    }

    public int childCount() {
        return this.children.length;
    }
}
