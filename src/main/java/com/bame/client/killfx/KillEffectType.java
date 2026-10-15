package com.bame.client.killfx;

import java.util.Locale;

/**
 * Die Kill-Effekte des Jeton-Shops (Kategorie {@code KILLEFFECTS}). Die IDs sind identisch mit den Item-IDs im Backend
 * und mit dem Feld {@code effect} der Sync-Nachricht {@code killfx}. Reines Java (ohne Minecraft), damit die Logik-Tests
 * es benutzen können.
 */
public enum KillEffectType {
    TOTEM("KILL_TOTEM", "Totem", "The classic totem pops up on your screen", true),
    LIGHTNING("KILL_LIGHTNING", "Lightning", "A lightning bolt strikes your victim", true),
    GRAVE("KILL_GRAVE", "Grave", "A gravestone with your victim's name appears", false),
    COFFIN("KILL_COFFIN", "Coffin", "A wooden coffin rises from the earth and closes", false),
    ANVIL("KILL_ANVIL", "Anvil", "An anvil drops on your victim's head", false),
    SNAP("KILL_SNAP", "Thanos Snap", "Your victim crumbles to dust and blows away", false),
    HELLGATE("KILL_HELLGATE", "Hell Gate", "Demonic hands drag your victim into a burning gate", false);
    // Entfernt: KILL_FIREWORK, KILL_SOUL, KILL_CONFETTI (fromId liefert dafür null -> werden ignoriert bzw. als "none" geladen)

    /** Shop-Kategorie im Backend und interne Unterkategorie der Cosmetics-Ansicht. */
    public static final String CATEGORY = "KILLEFFECTS";

    public final String id;
    public final String displayName;
    public final String description;
    /** Hat einen Bildschirm-Teil (nur für den Täter). */
    public final boolean hasScreenPart;

    KillEffectType(String id, String displayName, String description, boolean hasScreenPart) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.hasScreenPart = hasScreenPart;
    }

    /** Effekt zu einer ID (Groß-/Kleinschreibung egal), sonst {@code null} (z. B. {@code none} oder unbekannt). */
    public static KillEffectType fromId(String id) {
        if (id == null) return null;
        String key = id.trim().toUpperCase(Locale.ROOT);
        for (KillEffectType t : values()) {
            if (t.id.equals(key)) return t;
        }
        return null;
    }

    /** Effekte mit Opfer-Figur (Skin und Rüstung des Opfers). */
    public boolean usesFigure() {
        return this == ANVIL || this == SNAP || this == HELLGATE;
    }
}
