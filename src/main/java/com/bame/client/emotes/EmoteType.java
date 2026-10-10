package com.bame.client.emotes;

public enum EmoteType {
    NONE("Keins", "NONE", "", "Keine Animation"),
    WAVE("Winken", "WAVE", "\uD83D\uDC4B", "Freundlich mit der Hand winken"),
    CLAP("Klatschen", "CLAP", "\uD83D\uDC4F", "Begeistert applaudieren"),
    DANCE("Tanz", "DANCE", "\uD83D\uDC83", "Rhythmischer Caeser Groove"),
    SIT("Sitzen", "SIT", "\uD83E\uDE91", "Gemütlich auf den Boden setzen"),
    T_POSE("T-Pose", "TPOSE", "🧍", "Arme waagerecht ausstrecken"),
    JUGGLE("Jonglieren", "JUGGLE", "⚽", "Fußball mit den Füßen jonglieren"),
    ZOMBIE("Zombie", "ZOMBIE", "🧟", "Arme wie ein Zombie nach vorne"),
    HEADSPIN("Kopfdrehen", "HEADSPIN", "🔄", "Humorvolles Kopfkreisen"),
    STOP("Beenden", "STOP", "\u23F9", "Aktuelle Animation beenden");

    private final String nameDe;
    private final String nameEn;
    private final String icon;
    private final String description;

    EmoteType(String nameDe, String nameEn, String icon, String description) {
        this.nameDe = nameDe;
        this.nameEn = nameEn;
        this.icon = icon;
        this.description = description;
    }

    public String getNameDe() {
        return nameDe;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public boolean allowsWalking() {
        return this == T_POSE || this == WAVE || this == CLAP || this == HEADSPIN
                || this == ZOMBIE || this == DANCE || this == JUGGLE;
    }
}
