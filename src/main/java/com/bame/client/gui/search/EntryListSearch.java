package com.bame.client.gui.search;

import com.bame.client.gui.CustomGuiUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Shared helpers for the Key Binds / Statistics search bars. */
public final class EntryListSearch {
    private static final int NO_RESULTS_COLOR = 0xFF8E95A4;

    private EntryListSearch() {}

    /** Shows the given entries (a filtered view of the full list) and scrolls back to the top. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void showEntries(EntryListWidget<?> list, List<?> entries) {
        list.setFocused((Element) null);
        list.setScrollY(0.0);
        ((EntryListWidget) list).replaceEntries((Collection) entries);
        list.setScrollY(0.0);
    }

    public static void drawNoResults(DrawContext context, String message, int centerX, int centerY) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        Text text = CustomGuiUtils.getFontText(message);
        context.drawText(tr, text, centerX - tr.getWidth(text) / 2, centerY - 4, NO_RESULTS_COLOR, false);
    }

    /** Draws the "no results" hint in the middle of the list if a query is active and nothing matches. */
    public static void drawNoResultsIfEmpty(DrawContext context, EntryListWidget<?> list, SearchFieldWidget field, String message) {
        if (list == null || field == null || !field.visible) return;
        if (field.getText().trim().isEmpty() || !list.children().isEmpty()) return;
        drawNoResults(context, message, list.getX() + list.getWidth() / 2, list.getY() + list.getHeight() / 2);
    }

    /**
     * Common key handling for a search field inside a screen. Returns {@code null} if the key should be processed
     * by vanilla as usual, otherwise the value the screen's {@code keyPressed} should return.
     * <ul>
     *     <li>Ctrl+F focuses the field</li>
     *     <li>ESC with text in the field clears it first (the screen stays open)</li>
     *     <li>while the field is focused, keys go to the field and trigger nothing else
     *     (Tab navigation and unhandled Ctrl shortcuts, e.g. Ctrl+Tab for tabs, still work)</li>
     * </ul>
     */
    public static Boolean handleKey(Screen screen, SearchFieldWidget field, KeyInput input) {
        if (field == null || !field.visible || !field.active) return null;

        if (input.key() == GLFW.GLFW_KEY_F && input.hasCtrlOrCmd() && !input.hasShift() && !input.hasAlt()) {
            screen.setFocused(field);
            return Boolean.TRUE;
        }

        if (input.isEscape()) {
            if (!field.getText().isEmpty()) {
                field.setText("");
                return Boolean.TRUE;
            }
            return null;
        }

        if (field.isFocused() && screen.getFocused() == field) {
            if (input.isTab()) return null;
            if (field.keyPressed(input)) return Boolean.TRUE;
            if (input.hasCtrlOrCmd()) return null;
            return Boolean.TRUE;
        }
        return null;
    }

    /** Removes the focus from the search field (e.g. while a key binding waits for a key). */
    public static void unfocus(Screen screen, SearchFieldWidget field) {
        if (field == null) return;
        if (screen.getFocused() == field) {
            screen.setFocused(null);
        }
        if (field.isFocused()) field.setFocused(false);
    }

    public static boolean isGerman() {
        try {
            String lang = MinecraftClient.getInstance().getLanguageManager().getLanguage();
            return lang != null && lang.toLowerCase(Locale.ROOT).startsWith("de");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String keybindsPlaceholder() {
        return isGerman() ? "Keybinds suchen…" : "Search keybinds…";
    }

    public static String keybindsNoResults() {
        return isGerman() ? "Keine Keybinds gefunden" : "No keybinds found";
    }

    public static String statsPlaceholder() {
        return isGerman() ? "Statistiken suchen…" : "Search statistics…";
    }

    public static String statsNoResults() {
        return isGerman() ? "Keine Statistiken gefunden" : "No statistics found";
    }
}
