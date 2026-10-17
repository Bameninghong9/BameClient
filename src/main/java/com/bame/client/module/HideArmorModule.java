package com.bame.client.module;

/**
 * „Hide Armor“ (Mod-Menü → Visuals): blendet Rüstung von Spielern nur in der eigenen Ansicht aus. Rein clientseitig
 * über die Feature-Renderer ({@code ArmorFeatureRendererMixin}, {@code HeadFeatureRendererMixin},
 * {@code ElytraFeatureRendererMixin}); Inventar, Ausrüstung, Render-State-Daten und Pakete bleiben unverändert.
 * Caeser-Cosmetics, Capes, Hitboxen und Namensschilder sind eigene Renderer und bleiben unberührt. Gilt nur für
 * Spieler (nicht für Rüstungsständer, Mannequins oder Mobs).
 *
 * <p>Die Entscheidung ({@link #hidesSlot}, {@link #hidesHeadItem}, {@link #hidesElytra}) ist reine Logik ohne
 * Minecraft-Klassen.
 */
public final class HideArmorModule {
    private HideArmorModule() {}

    /** Slot-Nummern (wie im Armor HUD): Helm, Brust, Hose, Schuhe. */
    public static final int SLOT_HEAD = 0;
    public static final int SLOT_CHEST = 1;
    public static final int SLOT_LEGS = 2;
    public static final int SLOT_FEET = 3;

    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;

    /** Eigene Rüstung ausblenden (sichtbar in der 3rd-Person-Ansicht). */
    public static boolean self = true;
    /** Rüstung anderer Spieler ausblenden. */
    public static boolean others = false;
    public static boolean helmet = true;
    public static boolean chestplate = true;
    public static boolean leggings = true;
    public static boolean boots = true;
    /** Köpfe, geschnitzte Kürbisse und andere Items im Helm-Slot weiter zeigen (nur echte Helme ausblenden). */
    public static boolean keepHeads = true;
    /** Elytra weiter zeigen, auch wenn der Brust-Slot ausgeblendet wird. */
    public static boolean keepElytra = true;
    /** Auch in der Spielervorschau des Inventars (und anderen GUI-Vorschauen) ausblenden. */
    public static boolean inventoryPreview = false;

    /** „Reset“ der Karte: Optionen auf Standard (an/aus und Taste bleiben). */
    public static void resetOptions() {
        self = true;
        others = false;
        helmet = true;
        chestplate = true;
        leggings = true;
        boots = true;
        keepHeads = true;
        keepElytra = true;
        inventoryPreview = false;
    }

    /**
     * Wird das Rüstungsteil im Slot ausgeblendet?
     *
     * @param slot       {@link #SLOT_HEAD} … {@link #SLOT_FEET}
     * @param isSelf     gehört der Render-State zum eigenen Spieler?
     * @param guiPreview wird gerade eine GUI-Vorschau gezeichnet (Inventar)?
     */
    public static boolean hidesSlot(int slot, boolean isSelf, boolean guiPreview) {
        if (!enabled) return false;
        if (guiPreview && !inventoryPreview) return false;
        if (isSelf ? !self : !others) return false;
        return switch (slot) {
            case SLOT_HEAD -> helmet;
            case SLOT_CHEST -> chestplate;
            case SLOT_LEGS -> leggings;
            case SLOT_FEET -> boots;
            default -> false;
        };
    }

    /** Köpfe/Kürbisse/Items im Helm-Slot (eigener Vanilla-Renderer) ausblenden? Nur wenn der Helm-Slot aus ist. */
    public static boolean hidesHeadItem(boolean isSelf, boolean guiPreview) {
        return !keepHeads && hidesSlot(SLOT_HEAD, isSelf, guiPreview);
    }

    /** Elytra (eigener Vanilla-Renderer) ausblenden? Nur wenn der Brust-Slot aus ist. */
    public static boolean hidesElytra(boolean isSelf, boolean guiPreview) {
        return !keepElytra && hidesSlot(SLOT_CHEST, isSelf, guiPreview);
    }
}
