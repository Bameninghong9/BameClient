package com.bame.client.module;

/**
 * Verschiebbare Vanilla-Hotbar (HUD-Editor, Element „Hotbar“). Position als Versatz zur Vanilla-Verankerung unten
 * mittig in skalierten GUI-Pixeln, Skalierung um denselben Punkt. Versatz 0/0 bei 100 % = exakt die Vanilla-Position.
 * Umsetzung: {@link com.bame.client.render.HotbarHud}, Rechnung: {@link com.bame.client.render.HotbarLayout}.
 */
public final class HotbarModule {
    private HotbarModule() {}

    public static int offsetX = 0;
    public static int offsetY = 0;
    /** 0.5-1.5 (50-150 %). */
    public static float scale = 1.0f;
    /**
     * Herzen, Rüstung, Hunger, Luft, Reittier-Leben und der Name des gewählten Items wandern mit. Aus: nur Hotbar,
     * Offhand, Angriffsanzeige und Erfahrungsleiste/Level bewegen sich, der Rest bleibt an der Vanilla-Stelle.
     */
    public static boolean moveStatusBars = true;

    /** „Reset Positions“ im HUD-Editor: zurück an die Vanilla-Stelle (Skalierung bleibt wie bei den anderen Elementen). */
    public static void resetPosition() {
        offsetX = 0;
        offsetY = 0;
    }

    /** Position und Größe zurück auf Vanilla. */
    public static void resetLayout() {
        resetPosition();
        scale = 1.0f;
    }
}
