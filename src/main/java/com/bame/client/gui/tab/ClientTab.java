package com.bame.client.gui.tab;

import com.bame.client.gui.BameClientScreen;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

public interface ClientTab {
    String getCategory();

    boolean isVisible(String search);

    void layout(BameClientScreen screen, int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search);

    void calculateHeight(BameClientScreen screen, int columnWidth, ColumnTracker tracker, String search);

    void render(BameClientScreen screen, DrawContext context, int mouseX, int mouseY, float delta,
                int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search);

    boolean mouseClicked(BameClientScreen screen, Click click, boolean twice,
                         int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search);

    /**
     * Rechtsklick im Inhaltsbereich der Mod-Ansicht (z. B. Zyklus-Knopf einen Schritt zurück). Wie bei
     * {@link #mouseClicked} teilen sich alle Tabs denselben {@code tracker}: wer nicht reagiert, muss ihn um seine
     * Kartenhöhen weiterschieben (Standard: {@link #calculateHeight}, gleiche Reihenfolge wie beim Zeichnen).
     */
    default boolean mouseRightClicked(BameClientScreen screen, Click click,
                                      int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        calculateHeight(screen, columnWidth, tracker, search);
        return false;
    }

    boolean mouseDragged(BameClientScreen screen, Click click, double deltaX, double deltaY);

    boolean mouseReleased(BameClientScreen screen, Click click);

    boolean keyPressed(BameClientScreen screen, KeyInput input);

    default boolean charTyped(BameClientScreen screen, CharInput input) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return false;
    }

    /** Wartet der Tab gerade auf eine Taste für eine Modul-Belegung? Dann bekommt er die nächste Taste zuerst. */
    default boolean isListeningForKey() {
        return false;
    }

    /** Setzt alle "wartet auf Taste"-Zustände zurück. */
    default void cancelKeyListening() {
    }

    /** Hat der Tab ein fokussiertes Textfeld (dann keine Tastenkombinationen auswerten)? */
    default boolean hasFocusedTextField() {
        return false;
    }
}
