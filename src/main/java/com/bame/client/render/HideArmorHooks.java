package com.bame.client.render;

import com.bame.client.cosmetics.GuiPreviewContext;
import com.bame.client.module.HideArmorModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;

/**
 * Entscheidung für „Hide Armor“ aus dem Render-State (aufgerufen von den Feature-Renderer-Mixins, nur Render-Thread,
 * ohne Allokation). Nur echte Spieler: der Render-State eines Spielers ist ein {@link PlayerEntityRenderState} mit
 * {@code entityType == PLAYER} (Mannequins teilen den Renderer, haben aber einen anderen Typ; Rüstungsständer und Mobs
 * haben andere Render-States). „Selbst“ = Entity-ID des eigenen Spielers, auch in der Inventar-Vorschau.
 */
public final class HideArmorHooks {
    private HideArmorHooks() {}

    private static final int NOT_A_PLAYER = -1;
    private static final int OTHER = 0;
    private static final int SELF = 1;

    private static int owner(EntityRenderState state) {
        if (!(state instanceof PlayerEntityRenderState player) || player.entityType != EntityType.PLAYER) return NOT_A_PLAYER;
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && player.id == client.player.getId() ? SELF : OTHER;
    }

    /** Rüstungsteil (ArmorFeatureRenderer) im Slot überspringen? */
    public static boolean hidesArmor(EntityRenderState state, EquipmentSlot slot) {
        if (!HideArmorModule.enabled) return false;
        int owner = owner(state);
        if (owner == NOT_A_PLAYER) return false;
        int index = switch (slot) {
            case HEAD -> HideArmorModule.SLOT_HEAD;
            case CHEST -> HideArmorModule.SLOT_CHEST;
            case LEGS -> HideArmorModule.SLOT_LEGS;
            case FEET -> HideArmorModule.SLOT_FEET;
            default -> -1;
        };
        return index >= 0 && HideArmorModule.hidesSlot(index, owner == SELF, GuiPreviewContext.active());
    }

    /** Kopf/Kürbis/Item im Helm-Slot (HeadFeatureRenderer) überspringen? */
    public static boolean hidesHeadItem(EntityRenderState state) {
        if (!HideArmorModule.enabled || HideArmorModule.keepHeads) return false;
        int owner = owner(state);
        return owner != NOT_A_PLAYER && HideArmorModule.hidesHeadItem(owner == SELF, GuiPreviewContext.active());
    }

    /** Elytra (ElytraFeatureRenderer) überspringen? */
    public static boolean hidesElytra(EntityRenderState state) {
        if (!HideArmorModule.enabled || HideArmorModule.keepElytra) return false;
        int owner = owner(state);
        return owner != NOT_A_PLAYER && HideArmorModule.hidesElytra(owner == SELF, GuiPreviewContext.active());
    }
}
