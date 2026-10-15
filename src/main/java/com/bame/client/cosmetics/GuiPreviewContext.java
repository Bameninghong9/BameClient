package com.bame.client.cosmetics;

/**
 * Wird gerade eine Entity als GUI-Vorschau gezeichnet (Spielermodell im Inventar, Kreativ-Inventar, Pferde-Inventar
 * usw.)? Gesetzt von {@code EntityGuiElementRendererMixin} rund um {@code EntityGuiElementRenderer.render}; nur auf
 * dem Render-Thread gelesen. Feature-Renderer nutzen das, um für die Vorschau keine Welt-Partikel zu erzeugen.
 */
public final class GuiPreviewContext {
    private static int depth = 0;

    private GuiPreviewContext() {}

    public static void enter() {
        depth++;
    }

    public static void exit() {
        if (depth > 0) depth--;
    }

    /** Sicherheitsnetz (Client-Tick, nie während des Zeichnens): falls ein Render-Fehler das Verlassen übersprungen hat. */
    public static void reset() {
        depth = 0;
    }

    /** {@code true}, solange ein GUI-Entity-Modell gezeichnet wird (keine Welt-Effekte erzeugen). */
    public static boolean active() {
        return depth > 0;
    }
}
