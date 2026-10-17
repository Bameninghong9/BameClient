package com.bame.client.playerwheel;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.skin.SkinSaver;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;

import java.util.List;
import java.util.function.Consumer;

/**
 * Ein Segment des Spieler-Auswahlrads: Beschriftung, Symbol und Aktion für den gewählten Spieler. Neue Aktionen
 * (z. B. „Copy name“, „Add friend“) kommen einfach in {@link #ALL}; Layout, Treffer und Zeichnen passen sich an.
 *
 * @param label  englische Beschriftung (wird wie im Emote-Rad in Großbuchstaben unter das Kästchen gezeichnet)
 * @param icon   zeichnet das Symbol zentriert um ({@code cx}, {@code cy}) in der übergebenen Farbe (ARGB)
 * @param action läuft auf dem Render-Thread; längere Arbeit selbst in den Hintergrund verlagern
 */
public record PlayerWheelAction(String label, Icon icon, Consumer<AbstractClientPlayerEntity> action) {

    @FunctionalInterface
    public interface Icon {
        void draw(DrawContext context, int cx, int cy, int color);
    }

    /** Skin des Spielers in die Skin-Bibliothek des Launchers (sonst lokal) speichern. */
    public static final PlayerWheelAction SAVE_SKIN = new PlayerWheelAction("Save skin", PlayerWheelAction::drawSaveSkinIcon, SkinSaver::save);

    /** Reihenfolge im Rad (erstes Segment oben, weitere im Uhrzeigersinn). */
    public static final List<PlayerWheelAction> ALL = List.of(SAVE_SKIN);

    /** Shirt-Symbol mit kleinem Pfeil nach unten (Download). */
    private static void drawSaveSkinIcon(DrawContext c, int cx, int cy, int color) {
        CustomGuiUtils.drawShirtIcon(c, cx - 9, cy - 13, color);
        int ax = cx - 3;
        int ay = cy + 6;
        c.fill(ax + 3, ay, ax + 4, ay + 4, color);      // Schaft
        c.fill(ax + 1, ay + 3, ax + 6, ay + 4, color);  // Spitze
        c.fill(ax + 2, ay + 4, ax + 5, ay + 5, color);
        c.fill(ax + 3, ay + 5, ax + 4, ay + 6, color);
        c.fill(ax, ay + 7, ax + 7, ay + 8, color);      // Ablage
    }
}
