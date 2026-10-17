package com.bame.client.module;

/**
 * „Container Search“ (Mod-Menü → Misc): Suchleiste unter Truhen, Fässern, Endertruhen, Shulker-Boxen, Trichtern,
 * Spendern/Werfern (auch Server-Menüs im Truhen-GUI), optional im eigenen Inventar. Nicht passende Slots werden
 * ausgegraut. Umsetzung in {@code gui/search/ContainerSearch}; hier nur die Einstellungen (reine Felder, gespeichert
 * in {@code Misc/container_search.properties}, fehlende Schlüssel = Standardwerte).
 */
public final class ContainerSearchModule {
    private ContainerSearchModule() {}

    public static boolean enabled = true;
    public static boolean expanded = false;

    /** Suchleiste auch im eigenen Inventar (E). */
    public static boolean playerInventory = false;
    /** Verzauberungen und Lore-Zeilen mit durchsuchen (praktisch in Server-Shops). */
    public static boolean searchLoreEnchantments = true;
    /** Inhalt von Shulker-Boxen und Bundles mit durchsuchen (passender Inhalt = Treffer). */
    public static boolean searchShulkers = true;
    /** Passende Items mit dezentem Rahmen in der Akzentfarbe markieren. */
    public static boolean highlightMatches = true;
    /** Suchtext beim Schließen merken und beim nächsten Öffnen wieder einsetzen. */
    public static boolean rememberSearch = false;

    /** „Reset“ der Karte: Optionen auf Standard (an/aus bleibt). */
    public static void resetOptions() {
        playerInventory = false;
        searchLoreEnchantments = true;
        searchShulkers = true;
        highlightMatches = true;
        rememberSearch = false;
    }

    /** Einstellungen, von denen die aufbereiteten Item-Daten abhängen (Wechsel = Cache neu aufbauen). */
    public static int dataSignature() {
        return (searchLoreEnchantments ? 1 : 0) | (searchShulkers ? 2 : 0);
    }
}
