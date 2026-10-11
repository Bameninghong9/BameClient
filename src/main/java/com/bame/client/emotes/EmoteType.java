package com.bame.client.emotes;

public enum EmoteType {
    NONE("Keins", "NONE", "", "Keine Animation"),
    WAVE("Winken", "WAVE", "\uD83D\uDC4B", "Freundlich mit der Hand winken"),
    GUITAR("E-Gitarre", "GUITAR", "🎸", "Rockstar Solo & Kniefall-Slide"),
    DANCE("Tanz", "DANCE", "\uD83D\uDC83", "Rhythmischer Caeser Groove"),
    SIT("Sitzen", "SIT", "\uD83E\uDE91", "Gemütlich auf den Boden setzen"),
    T_POSE("T-Pose", "TPOSE", "🧍", "Arme waagerecht ausstrecken"),
    JUGGLE("Jonglieren", "JUGGLE", "⚽", "Fußball mit den Füßen jonglieren"),
    BASKETBALL("Basketball", "BASKETBALL", "🏀", "Dribbeln, Crossover & Finger-Spin"),
    COIN_FLIP("Münzwurf", "COIN_FLIP", "🪙", "Goldmünze werfen & Kopf oder Zahl"),
    WHEELCHAIR("Rollstuhl", "WHEELCHAIR", "♿", "Realistischer Sport-Rollstuhl mit drehenden Rädern & Fahren"),
    ZOMBIE("Zombie", "ZOMBIE", "🧟", "Arme wie ein Zombie nach vorne"),
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
        return this == T_POSE || this == WAVE || this == GUITAR || this == BASKETBALL
                || this == COIN_FLIP || this == WHEELCHAIR || this == ZOMBIE || this == DANCE || this == JUGGLE;
    }

    public static EmoteType fromId(String id) {
        if (id == null || id.isBlank()) return null;
        String clean = id.trim().toUpperCase(java.util.Locale.ROOT).replace("-", "_");
        for (EmoteType type : values()) {
            if (type.name().equalsIgnoreCase(clean) || type.nameEn.equalsIgnoreCase(clean) || type.name().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                return type;
            }
        }
        return null;
    }
}
