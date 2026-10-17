package com.bame.client.module;

import com.bame.client.render.InventoryHudLayout;

/**
 * Inventar-Vorschau „Inventory HUD“ (Mod-Menü → Visuals → „Show HUD“ → Zeile „Inventory HUD“, aufklappbar). Zeichnen:
 * {@link com.bame.client.render.InventoryHudRenderer}, Rechnung: {@link InventoryHudLayout}. Position, Größe und
 * Hintergrund wie die anderen HUD-Elemente im HUD-Editor („Edit HUD“).
 */
public final class InventoryHudModule {
    private InventoryHudModule() {}

    public static boolean enabled = false;
    /** -1 = automatisch (unten rechts). */
    public static int hudX = -1;
    public static int hudY = -1;
    /** Standard etwas kleiner als die anderen Elemente: die Vorschau soll dezent bleiben. */
    public static float scale = 0.75f;
    public static int bgMode = 0; // 0 = Transparent, 1 = Tooltip, 2 = Dark, 3 = Outline (wie StatusHudRenderer.drawBoxBg)
    public static int outlineColor = 0xFFFFFFFF;
    public static int customWidth = -1;
    public static int customHeight = -1;

    /** Einstellungen in der Show-HUD-Karte aufgeklappt. */
    public static boolean expanded = false;
    /** Zusätzlich die Hotbar-Reihe unter dem Hauptinventar. */
    public static boolean includeHotbar = false;
    /** Ausblenden, solange ein Inventar/Container offen ist (dort sieht man das Inventar ohnehin). */
    public static boolean hideWhenInventoryOpen = true;
    /** {@link InventoryHudLayout#STYLE_HOTBAR} oder {@link InventoryHudLayout#STYLE_ITEMS}. */
    public static int slotStyle = InventoryHudLayout.STYLE_DEFAULT;
    /** Deckkraft von Hintergrund und Slots (10-100 %); Items zeichnet Minecraft immer deckend. */
    public static float opacity = InventoryHudLayout.OPACITY_DEFAULT;

    /** „Reset“ der Show-HUD-Karte: Darstellungs-Optionen auf Standard (Position, Größe und Hintergrund bleiben). */
    public static void resetOptions() {
        expanded = false;
        includeHotbar = false;
        hideWhenInventoryOpen = true;
        slotStyle = InventoryHudLayout.STYLE_DEFAULT;
        opacity = InventoryHudLayout.OPACITY_DEFAULT;
    }
}
