package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;

public class AutoToolModule {
    public static boolean enabled = true;
    public static int keyBind = -1;
    public static boolean expanded = false;

    public static boolean switchBack = true;

    private static int previousSlot = -1;
    private static boolean isMining = false;
    private static int miningTicks = 0;
    private static boolean wasKeyBindPressed = false;

    public static void onAttackBlock(BlockPos pos) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        BlockState state = client.world.getBlockState(pos);
        if (state.isAir() || state.getHardness(client.world, pos) < 0) return;

        int bestSlot = -1;
        float bestSpeed = 1.0f;
        int currentSlot = client.player.getInventory().getSelectedSlot();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            float speed = stack.getMiningSpeedMultiplier(state);
            if (stack.isSuitableFor(state)) {
                speed *= 1.5f;
            }

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        if (bestSlot != -1 && bestSlot != currentSlot) {
            if (previousSlot == -1) {
                previousSlot = currentSlot;
            }
            client.player.getInventory().setSelectedSlot(bestSlot);
            if (client.player.networkHandler != null) {
                client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(bestSlot));
            }
        }

        isMining = true;
        miningTicks = 0;
    }

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;

        if (isMining) {
            miningTicks++;
            boolean leftClick = client.options.attackKey.isPressed();
            if (!leftClick || client.interactionManager == null || !client.interactionManager.isBreakingBlock()) {
                if (miningTicks > 5) {
                    if (switchBack && previousSlot != -1 && client.player != null) {
                        client.player.getInventory().setSelectedSlot(previousSlot);
                        if (client.player.networkHandler != null) {
                            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                        }
                    }
                    previousSlot = -1;
                    isMining = false;
                }
            } else {
                miningTicks = 0;
            }
        }
    }

    public static void resetToDefault() {
        switchBack = true;
    }
}
