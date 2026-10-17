package com.bame.client.gui.search;

/**
 * Tasten-Entscheidung der Container-Suche (reine Logik ohne Minecraft/LWJGL, getestet in {@code src/plannerTest}).
 * Tastencodes und Modifier sind die GLFW-Werte.
 *
 * <ul>
 *     <li>Feld fokussiert: Esc geht an Vanilla (schließt das Fenster), Enter/Tab entfokussieren, alle anderen Tasten
 *     gehen nur ans Feld und lösen keine Inventar-Aktion aus (E schließt nicht, 1–9 tauschen nicht, Q droppt nicht,
 *     F tauscht nicht in die Offhand).</li>
 *     <li>Feld nicht fokussiert: Strg+F (Mac: Cmd+F) fokussiert, alles andere wie Vanilla.</li>
 *     <li>Feld unsichtbar, aber noch als fokussiert markiert: Fokus weg, Taste an Vanilla.</li>
 * </ul>
 */
public final class ContainerSearchKeys {
    public static final int KEY_F = 70;
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_ENTER = 257;
    public static final int KEY_TAB = 258;
    public static final int KEY_KP_ENTER = 335;

    public static final int MOD_SHIFT = 1;
    public static final int MOD_CONTROL = 2;
    public static final int MOD_ALT = 4;
    public static final int MOD_SUPER = 8;

    /** Taste geht normal an den Screen (Vanilla). */
    public static final int PASS = 0;
    /** Feld fokussieren, Taste verbrauchen. */
    public static final int FOCUS = 1;
    /** Fokus vom Feld nehmen, Taste verbrauchen. */
    public static final int UNFOCUS = 2;
    /** Fokus vom Feld nehmen, Taste trotzdem an Vanilla. */
    public static final int UNFOCUS_PASS = 3;
    /** Taste ans Feld (Bearbeiten), sonst nichts auslösen. */
    public static final int EDIT = 4;

    private ContainerSearchKeys() {}

    public static int decide(boolean focused, boolean visible, int key, int modifiers) {
        if (focused && !visible) return UNFOCUS_PASS;
        if (focused) {
            if (key == KEY_ESCAPE) return PASS;
            if (key == KEY_ENTER || key == KEY_KP_ENTER || key == KEY_TAB) return UNFOCUS;
            return EDIT;
        }
        if (visible && isFocusShortcut(key, modifiers)) return FOCUS;
        return PASS;
    }

    /** Strg+F bzw. Cmd+F ohne Umschalt/Alt. */
    public static boolean isFocusShortcut(int key, int modifiers) {
        return key == KEY_F
                && (modifiers & (MOD_CONTROL | MOD_SUPER)) != 0
                && (modifiers & (MOD_SHIFT | MOD_ALT)) == 0;
    }
}
