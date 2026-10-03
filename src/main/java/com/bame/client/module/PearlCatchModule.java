package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.function.Predicate;

public class PearlCatchModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;
    public static int timingDelay = 2; // 1 to 5 ticks between pearl and wind charge (2 ticks is optimal)
    public static boolean autoAim = true; // Automatically aims up at -82° pitch for perfect trajectory
    public static boolean switchToMace = true; // Automatically switches to Mace after throw
    public static boolean switchBack = true; // Falls keine Mace, zurück zum vorherigen Slot

    private static boolean wasKeyBindPressed = false;
    private static boolean wasUseKeyPressed = false;

    // State machine:
    // 0 = IDLE
    // 1 = WAITING_WIND_CHARGE (delay countdown)
    // 2 = FINISH_AND_RESTORE (swapping back / switching to Mace)
    private static int state = 0;
    private static int delayTimer = 0;
    private static int originalSlot = -1;
    private static float originalPitch = 0f;
    private static int swappedPearlInvSlot = -1;
    private static int swappedWindInvSlot = -1;
    private static int cachedTempHotbarSlot = -1;
    private static int timeoutTicks = 0;

    public static void onTick(MinecraftClient client) {
        // Keybind trigger
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            triggerPearlCatch(client);
        }
        wasKeyBindPressed = pressed;

        if (client.player == null || client.world == null) {
            cleanupSequence(client);
            return;
        }

        ClientPlayerEntity player = client.player;

        // Auto boost when manually throwing pearl upwards while enabled
        boolean usePressed = client.options != null && client.options.useKey.isPressed();
        if (enabled && state == 0 && usePressed && !wasUseKeyPressed && client.currentScreen == null) {
            if (player.getMainHandStack().isOf(Items.ENDER_PEARL) || player.getOffHandStack().isOf(Items.ENDER_PEARL)) {
                if (player.getPitch() < -50.0f) {
                    triggerFollowUpWindCharge(client);
                }
            }
        }
        wasUseKeyPressed = usePressed;

        if (state > 0) {
            timeoutTicks++;
            if (timeoutTicks > 40) { // 2s safety timeout
                cleanupSequence(client);
                return;
            }

            int syncId = player.playerScreenHandler.syncId;

            // STATE 1: Waiting delay, then throw Wind Charge!
            if (state == 1) {
                if (delayTimer > 0) {
                    delayTimer--;
                    return;
                }

                if (autoAim) {
                    player.setPitch(-82.0f);
                }

                // Find Wind Charge
                int windSlot = findItemSlot(player, stack -> stack.isOf(Items.WIND_CHARGE));
                if (windSlot != -1) {
                    if (windSlot < 9) {
                        selectHotbarSlot(client, player, windSlot);
                    } else {
                        cachedTempHotbarSlot = originalSlot != -1 ? originalSlot : player.getInventory().getSelectedSlot();
                        client.interactionManager.clickSlot(syncId, windSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                        swappedWindInvSlot = windSlot;
                        selectHotbarSlot(client, player, cachedTempHotbarSlot);
                    }

                    // Throw Wind Charge!
                    client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                    player.swingHand(Hand.MAIN_HAND);
                    CpsModule.registerClick(false);
                }

                state = 2;
                delayTimer = 1; // 1 tick buffer
                return;
            }

            // STATE 2: Restore slots / switch to Mace
            if (state == 2) {
                if (delayTimer > 0) {
                    delayTimer--;
                    return;
                }

                // Restore swapped slots if any
                if (swappedWindInvSlot != -1 && cachedTempHotbarSlot != -1) {
                    client.interactionManager.clickSlot(syncId, swappedWindInvSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                    swappedWindInvSlot = -1;
                }
                if (swappedPearlInvSlot != -1 && cachedTempHotbarSlot != -1) {
                    client.interactionManager.clickSlot(syncId, swappedPearlInvSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                    swappedPearlInvSlot = -1;
                }

                // Switch to Mace if desired and available
                boolean switchedMace = false;
                if (switchToMace) {
                    int maceSlot = findItemSlot(player, stack -> stack.isOf(Items.MACE));
                    if (maceSlot != -1) {
                        if (maceSlot < 9) {
                            selectHotbarSlot(client, player, maceSlot);
                            switchedMace = true;
                        }
                    }
                }

                // Otherwise switch back to original slot
                if (!switchedMace && switchBack && originalSlot != -1) {
                    selectHotbarSlot(client, player, originalSlot);
                }

                // Restore original pitch if autoAim was used
                if (autoAim) {
                    player.setPitch(originalPitch);
                }

                state = 0;
                delayTimer = 0;
                originalSlot = -1;
                cachedTempHotbarSlot = -1;
                timeoutTicks = 0;
            }
        }
    }

    public static void triggerPearlCatch(MinecraftClient client) {
        if (state != 0 || client.player == null || client.world == null || client.interactionManager == null) return;
        ClientPlayerEntity player = client.player;

        // Verify we have both Ender Pearl and Wind Charge
        int pearlSlot = findItemSlot(player, stack -> stack.isOf(Items.ENDER_PEARL));
        int windSlot = findItemSlot(player, stack -> stack.isOf(Items.WIND_CHARGE));
        if (pearlSlot == -1 || windSlot == -1) {
            return; // Missing ingredients!
        }

        originalSlot = player.getInventory().getSelectedSlot();
        originalPitch = player.getPitch();

        if (autoAim) {
            player.setPitch(-82.0f);
        }

        int syncId = player.playerScreenHandler.syncId;

        // Switch to Ender Pearl
        if (pearlSlot < 9) {
            selectHotbarSlot(client, player, pearlSlot);
        } else {
            cachedTempHotbarSlot = originalSlot;
            client.interactionManager.clickSlot(syncId, pearlSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
            swappedPearlInvSlot = pearlSlot;
            selectHotbarSlot(client, player, cachedTempHotbarSlot);
        }

        // Throw Ender Pearl!
        client.interactionManager.interactItem(player, Hand.MAIN_HAND);
        player.swingHand(Hand.MAIN_HAND);
        CpsModule.registerClick(false);

        // Advance to waiting for Wind Charge
        state = 1;
        delayTimer = Math.max(1, timingDelay);
        timeoutTicks = 0;
    }

    public static void triggerFollowUpWindCharge(MinecraftClient client) {
        if (state != 0 || client.player == null || client.world == null || client.interactionManager == null) return;
        ClientPlayerEntity player = client.player;

        int windSlot = findItemSlot(player, stack -> stack.isOf(Items.WIND_CHARGE));
        if (windSlot == -1) return;

        originalSlot = player.getInventory().getSelectedSlot();
        originalPitch = player.getPitch();

        // Pearl is already thrown by user, immediately start timer for Wind Charge
        state = 1;
        delayTimer = Math.max(1, timingDelay);
        timeoutTicks = 0;
    }

    private static int findItemSlot(PlayerEntity player, Predicate<ItemStack> predicate) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (predicate.test(inv.getStack(i))) return i;
        }
        for (int i = 9; i < 36; i++) {
            if (predicate.test(inv.getStack(i))) return i;
        }
        return -1;
    }

    private static void selectHotbarSlot(MinecraftClient client, ClientPlayerEntity player, int slot) {
        player.getInventory().setSelectedSlot(slot);
        if (client.interactionManager instanceof ClientPlayerInteractionManagerAccessor accessor) {
            accessor.invokeSyncSelectedSlot();
        } else if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }
    }

    public static void cleanupSequence(MinecraftClient client) {
        if (client != null && client.player != null && client.interactionManager != null) {
            ClientPlayerEntity player = client.player;
            int syncId = player.playerScreenHandler.syncId;
            if (swappedWindInvSlot != -1 && cachedTempHotbarSlot != -1) {
                client.interactionManager.clickSlot(syncId, swappedWindInvSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
            }
            if (swappedPearlInvSlot != -1 && cachedTempHotbarSlot != -1) {
                client.interactionManager.clickSlot(syncId, swappedPearlInvSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
            }
            if (switchBack && originalSlot != -1) {
                selectHotbarSlot(client, player, originalSlot);
            }
            if (autoAim && state > 0) {
                player.setPitch(originalPitch);
            }
        }
        state = 0;
        delayTimer = 0;
        originalSlot = -1;
        swappedPearlInvSlot = -1;
        swappedWindInvSlot = -1;
        cachedTempHotbarSlot = -1;
        timeoutTicks = 0;
    }

    public static void resetToDefault() {
        enabled = false;
        keyBind = -1;
        expanded = false;
        timingDelay = 2;
        autoAim = true;
        switchToMace = true;
        switchBack = true;
        state = 0;
        delayTimer = 0;
        originalSlot = -1;
        swappedPearlInvSlot = -1;
        swappedWindInvSlot = -1;
        cachedTempHotbarSlot = -1;
        timeoutTicks = 0;
        BameClientConfig.save();
    }
}
