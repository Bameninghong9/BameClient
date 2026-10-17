package com.bame.client.module;

import com.bame.client.render.ArmorHudLayout;

/**
 * Armor HUD (Mod-Menü → Visuals → „Show HUD“ → Zeile „Armor HUD“, aufklappbar). Zeichnen:
 * {@link com.bame.client.render.ArmorHudRenderer}, Rechnung: {@link ArmorHudLayout}.
 *
 * <p>Standardwerte entsprechen dem bisherigen Aussehen (untereinander, Prozent, farbig, leere Slots ausgeblendet),
 * damit alte Einstellungen ohne die neuen Schlüssel unverändert aussehen.
 */
public class ArmorHudModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static int hudX = -1;
    public static int hudY = -1;
    public static float scale = 1.0f;
    public static int bgMode = 0; // 0 = Transparent, 1 = Tooltip, 2 = Dark, 3 = Outline (StatusHudRenderer.drawBoxBg)
    public static int outlineColor = 0xFFFFFFFF;
    public static int bgColor = 0xD012161E;
    public static int customWidth = -1;
    public static int customHeight = -1;

    /** Einstellungen in der Show-HUD-Karte aufgeklappt. */
    public static boolean expanded = false;
    /** {@link ArmorHudLayout#STYLE_SLOTS} … {@link ArmorHudLayout#STYLE_MINIMAL}. */
    public static int style = ArmorHudLayout.STYLE_DEFAULT;
    /** {@link ArmorHudLayout#DUR_VANILLA} … {@link ArmorHudLayout#DUR_OFF}. */
    public static int durabilityMode = ArmorHudLayout.DUR_DEFAULT;
    /** Text und Balken von Grün über Gelb nach Rot (aus: weißer Text, Balken in der Theme-Farbe). */
    public static boolean colorByDurability = true;
    /** Item unter der Schwelle: dezent pulsierender roter Rahmen. */
    public static boolean lowWarning = true;
    /** Warnschwelle in Prozent (10-50, 5er-Schritte). */
    public static int lowThreshold = ArmorHudLayout.THRESHOLD_DEFAULT;
    public static boolean showHelmet = true;
    public static boolean showChestplate = true;
    public static boolean showLeggings = true;
    public static boolean showBoots = true;
    public static boolean showMainHand = false;
    public static boolean showOffHand = false;
    /** Pfeilanzahl im Inventar, solange ein Bogen oder eine Armbrust in der Hand ist (nur Anzeige). */
    public static boolean showArrows = false;
    /** Leere Slots ausblenden (aus: leere Felder mit Vanilla-Platzhalter-Symbol). */
    public static boolean hideEmpty = true;
    /** {@link ArmorHudLayout#ORDER_HELMET_FIRST} oder {@link ArmorHudLayout#ORDER_BOOTS_FIRST}. */
    public static int order = ArmorHudLayout.ORDER_HELMET_FIRST;
    /** {@link ArmorHudLayout#ANCHOR_FREE} oder links/rechts an der (verschiebbaren) Hotbar. */
    public static int anchor = ArmorHudLayout.ANCHOR_FREE;

    /** „Reset“ der Show-HUD-Karte: Darstellungs-Optionen auf Standard (Position, Größe und Hintergrund bleiben). */
    public static void resetOptions() {
        expanded = false;
        style = ArmorHudLayout.STYLE_DEFAULT;
        durabilityMode = ArmorHudLayout.DUR_DEFAULT;
        colorByDurability = true;
        lowWarning = true;
        lowThreshold = ArmorHudLayout.THRESHOLD_DEFAULT;
        showHelmet = true;
        showChestplate = true;
        showLeggings = true;
        showBoots = true;
        showMainHand = false;
        showOffHand = false;
        showArrows = false;
        hideEmpty = true;
        order = ArmorHudLayout.ORDER_HELMET_FIRST;
        anchor = ArmorHudLayout.ANCHOR_FREE;
    }

    /** Klebt das HUD an der Hotbar (dann im HUD-Editor nicht frei ziehbar)? */
    public static boolean isAnchored() {
        return ArmorHudLayout.sanitizeAnchor(anchor) != ArmorHudLayout.ANCHOR_FREE;
    }
}
