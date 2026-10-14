package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class ShieldBreakerModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;

    // Modes:
    // 0 = AUTOMATIC (switches to axe & hits as soon as opponent blocks within reach)
    // 1 = ON_CLICK (intercepts your attack when clicking on a blocking opponent)
    public static int mode = 0;

    public static boolean switchBack = true;
    public static int switchDelay = 1; // 1, 2, or 3 ticks delay for safety
    public static double reach = 3.2; // 2.5 to 4.5 blocks

    private static boolean wasKeyBindPressed = false;
    private static boolean wasAttackKeyPressed = false;

    // State machine:
    // 0 = IDLE
    // 1 = SWITCHED_TO_AXE (waiting delay ticks before hitting)
    // 2 = HIT_EXECUTED (waiting delay ticks before switching back)
    private static int state = 0;
    private static int delayTimer = 0;
    private static int originalSlot = -1;
    private static int swappedInvSlot = -1;
    private static int cachedTempHotbarSlot = -1;
    private static Entity currentTarget = null;
    private static int timeoutTicks = 0;

    public static void onTick(MinecraftClient client) {
        // Keybind toggle
        // Versteckt (Geheim-Menü): Taste nur aktiv, wenn entsperrt oder das Modul schon läuft
        boolean pressed = BameClientConfig.hiddenModuleHotkeyAllowed(enabled) && keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            com.bame.client.notification.NotificationManager.showModuleToggle("Shield Breaker", enabled);
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;

        if (client.player == null || client.world == null) {
            cleanup(client);
            return;
        }

        ClientPlayerEntity player = client.player;

        // Active sequence handling
        if (state > 0) {
            timeoutTicks++;
            if (timeoutTicks > 20 || !player.isAlive()) {
                cleanup(client);
                return;
            }

            if (delayTimer > 0) {
                delayTimer--;
                return;
            }

            int syncId = player.playerScreenHandler.syncId;

            // STATE 1: Axe is selected in hotbar, execute the shield-breaking attack!
            if (state == 1) {
                if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isRemoved()) {
                    currentTarget = findBlockingTarget(client, player);
                }

                if (currentTarget != null && client.interactionManager != null) {
                    double dist = player.getEyePos().distanceTo(currentTarget.getBoundingBox().getCenter());
                    if (dist <= reach + 0.5) {
                        client.attackCooldown = 0;
                        client.interactionManager.attackEntity(player, currentTarget);
                        player.swingHand(Hand.MAIN_HAND);
                        CpsModule.registerClick(false);

                        state = 2;
                        delayTimer = Math.max(1, switchDelay);
                        return;
                    }
                }

                cleanup(client);
                return;
            }

            // STATE 2: Hit done, restore original slot!
            if (state == 2) {
                if (swappedInvSlot != -1 && cachedTempHotbarSlot != -1 && client.interactionManager != null) {
                    client.interactionManager.clickSlot(syncId, swappedInvSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                    swappedInvSlot = -1;
                }

                if (switchBack && originalSlot != -1) {
                    selectHotbarSlot(client, player, originalSlot);
                }

                state = 0;
                delayTimer = 0;
                originalSlot = -1;
                cachedTempHotbarSlot = -1;
                currentTarget = null;
                timeoutTicks = 0;
                return;
            }
        }

        if (!enabled || client.currentScreen != null || !player.isAlive()) {
            return;
        }

        // Trigger condition check
        boolean attackDown = client.options != null && client.options.attackKey.isPressed();
        boolean trigger = false;

        if (mode == 0) {
            // AUTOMATIC mode: triggers whenever looking at a blocking player
            trigger = true;
        } else if (mode == 1) {
            // ON_CLICK mode: triggers when attack key is freshly pressed
            if (attackDown && !wasAttackKeyPressed) {
                trigger = true;
            }
        }
        wasAttackKeyPressed = attackDown;

        if (!trigger) return;

        Entity target = findBlockingTarget(client, player);
        if (target == null) return;

        // If player already holds an axe in hand, hit directly without switching
        ItemStack held = player.getMainHandStack();
        if (held.isIn(ItemTags.AXES)) {
            if (client.interactionManager != null) {
                client.attackCooldown = 0;
                client.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
                CpsModule.registerClick(false);
            }
            return;
        }

        // Search for an axe in the hotbar (0..8)
        int axeHotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isIn(ItemTags.AXES)) {
                axeHotbarSlot = i;
                break;
            }
        }

        originalSlot = player.getInventory().getSelectedSlot();
        int syncId = player.playerScreenHandler.syncId;

        if (axeHotbarSlot != -1) {
            // Axe is in hotbar: switch active slot directly!
            selectHotbarSlot(client, player, axeHotbarSlot);
        } else {
            // Search in main inventory (slots 9..35)
            int axeInvSlot = -1;
            for (int i = 9; i < 36; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.isIn(ItemTags.AXES)) {
                    axeInvSlot = i;
                    break;
                }
            }
            if (axeInvSlot == -1) {
                return; // No axe found
            }

            cachedTempHotbarSlot = originalSlot;
            if (client.interactionManager != null) {
                client.interactionManager.clickSlot(syncId, axeInvSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                swappedInvSlot = axeInvSlot;
            }
            selectHotbarSlot(client, player, cachedTempHotbarSlot);
        }

        currentTarget = target;
        state = 1;
        delayTimer = Math.max(0, switchDelay);
        timeoutTicks = 0;
    }

    public static Entity findBlockingTarget(MinecraftClient client, ClientPlayerEntity player) {
        Entity target = null;
        if (client.targetedEntity != null) {
            target = client.targetedEntity;
        } else if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.ENTITY
                && client.crosshairTarget instanceof EntityHitResult eHit) {
            target = eHit.getEntity();
        }

        if (target instanceof PlayerEntity opponent && opponent.isAlive() && !opponent.isSpectator()) {
            if (opponent == player) return null;
            // Target must be actively blocking with a shield!
            if (!opponent.isBlocking()) return null;

            double dist = player.getEyePos().distanceTo(opponent.getBoundingBox().getCenter());
            if (dist <= reach) {
                return opponent;
            }
        }
        return null;
    }

    private static void selectHotbarSlot(MinecraftClient client, ClientPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        if (client.interactionManager instanceof ClientPlayerInteractionManagerAccessor accessor) {
            accessor.invokeSyncSelectedSlot();
        } else if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    public static void cleanup(MinecraftClient client) {
        if (client != null && client.player != null && client.interactionManager != null) {
            ClientPlayerEntity player = client.player;
            int syncId = player.playerScreenHandler.syncId;
            if (swappedInvSlot != -1 && cachedTempHotbarSlot != -1) {
                client.interactionManager.clickSlot(syncId, swappedInvSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
            }
            if (switchBack && originalSlot != -1) {
                selectHotbarSlot(client, player, originalSlot);
            }
        }
        state = 0;
        delayTimer = 0;
        originalSlot = -1;
        swappedInvSlot = -1;
        cachedTempHotbarSlot = -1;
        currentTarget = null;
        timeoutTicks = 0;
    }

    public static void resetToDefault() {
        enabled = false;
        keyBind = -1;
        expanded = false;
        mode = 0;
        switchBack = true;
        switchDelay = 1;
        reach = 3.2;
        cleanup(MinecraftClient.getInstance());
        BameClientConfig.save();
    }
}
