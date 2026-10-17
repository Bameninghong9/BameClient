package com.bame.client.emotes;

public enum EmoteType {
    NONE("None", "NONE", "", "No animation"),
    WAVE("Wave", "WAVE", "\uD83D\uDC4B", "Give a friendly wave"),
    SIT("Sit", "SIT", "\uD83E\uDE91", "Free emote • Sit down on the ground"),
    T_POSE("T-Pose", "TPOSE", "🧍", "Free emote • Arms straight out"),
    JUGGLE("Juggle", "JUGGLE", "⚽", "Juggle a soccer ball with your feet"),
    BASKETBALL("Basketball", "BASKETBALL", "🏀", "Dribble, crossover and finger spin"),
    WHEELCHAIR("Wheelchair", "WHEELCHAIR", "♿", "Sports wheelchair with spinning wheels"),
    ZOMBIE("Zombie", "ZOMBIE", "🧟", "Arms forward like a zombie"),
    BUTTERFLY("Butterfly", "BUTTERFLY", "🔪", "Butterfly knife inspect: flip, spin & toss"),
    PUSH_UPS("Push-Ups", "PUSHUPS", "💪", "Push-ups from the plank position"),
    HIGH_FIVE("High-Five", "HIGHFIVE", "✋", "Raise your hand: nearby players high-five you with [F]"),
    /**
     * Verstecktes Gratis-Emote (Backend-Item HIGH_FIVE_ACCEPT): wird nur gespielt, wenn man bei einem wartenden
     * High-Five-Spieler die Einschlagen-Taste drückt. Nicht im Menü, im Emote-Rad oder im Shop.
     */
    HIGH_FIVE_ACCEPT("High-Five (accept)", "HIGHFIVEACCEPT", "✋", "Accept a waiting high-five"),
    CAMPFIRE("Campfire", "CAMPFIRE", "🔥", "Sit by the campfire and roast marshmallows"),
    HAMMOCK("Hammock", "HAMMOCK", "🏝", "Relax in a hammock and sway gently"),
    SWING("Swing", "SWING", "🎠", "Swing high on a wooden swing set — friends can take the second seat"),
    PICNIC("Picnic", "PICNIC", "🧺", "Sit on a picnic blanket with a sandwich and a drink"),
    /** Halloween: auf einem Hexenbesen in kleinen Kreisen schweben (rein optisch, die echte Position bleibt). */
    WITCH_BROOM("Witch Broom", "WITCHBROOM", "🧹", "Hover in small circles on a witch's broom with a sparkle trail"),
    /**
     * Im Schneidersitz auf einem schwebenden Teppich; Laufen, Sprinten, Springen und Schleichen bleiben unverändert (rein
     * optisch, kein Fliegen). Name und Beschreibung laut Auftrag auf Deutsch.
     */
    FLYING_CARPET("Fliegender Teppich", "FLYINGCARPET", "🧞", "Auf einem fliegenden Teppich durch die Gegend gleiten"),
    STOP("Stop", "STOP", "\u23F9", "Stop the current animation");

    /** Englischer Anzeigename für Spieler (Menü-Karten, Emote-Rad, Kauf-Dialog). Backend-ID bleibt {@link #name()}. */
    private final String displayName;
    /** Zusätzlicher Bezeichner (z. B. "PUSHUPS"), den {@link #fromId} akzeptiert; wird nicht angezeigt. */
    private final String alias;
    private final String icon;
    /** Kurze englische Beschreibung (Menü-Karten/Suche). */
    private final String description;

    EmoteType(String displayName, String alias, String icon, String description) {
        this.displayName = displayName;
        this.alias = alias;
        this.icon = icon;
        this.description = description;
    }

    /** Englischer Anzeigename, z. B. "Push-Ups". */
    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public boolean allowsWalking() {
        return this == T_POSE || this == WAVE || this == BASKETBALL
                || this == WHEELCHAIR || this == ZOMBIE || this == JUGGLE || this == BUTTERFLY || this == HIGH_FIVE
                || this == HIGH_FIVE_ACCEPT || this == FLYING_CARPET;
    }

    /** Interne Emotes: nicht auswählbar (Menü, Emote-Rad, Shop), werden nur vom Client selbst ausgelöst. */
    public boolean isHidden() {
        return this == HIGH_FIVE_ACCEPT;
    }

    public static EmoteType fromId(String id) {
        if (id == null || id.isBlank()) return null;
        String clean = id.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "_");
        for (EmoteType type : values()) {
            if (type.name().equalsIgnoreCase(clean) || type.alias.equalsIgnoreCase(clean) || type.name().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                return type;
            }
        }
        return null;
    }
}
