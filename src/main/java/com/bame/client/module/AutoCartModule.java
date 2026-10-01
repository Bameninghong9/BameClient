package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.function.Predicate;

public class AutoCartModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;
    public static boolean switchBack = true;

    private static boolean wasKeyBindPressed = false;
    private static long lastCartTime = 0;

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
    }

    public static boolean isFlameBow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (!(stack.getItem() instanceof BowItem)) return false;
        if (!stack.hasEnchantments()) return false;
        for (var entry : stack.getEnchantments().getEnchantments()) {
            if (entry.matchesKey(Enchantments.FLAME) || entry.getIdAsString().contains("flame")) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRailItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == Items.RAIL || item == Items.POWERED_RAIL || item == Items.DETECTOR_RAIL || item == Items.ACTIVATOR_RAIL;
    }

    public static boolean isTntCartItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() == Items.TNT_MINECART;
    }

    public static int findItemSlot(PlayerEntity player, Predicate<ItemStack> predicate) {
        PlayerInventory inv = player.getInventory();
        // Check hotbar first (0-8)
        for (int i = 0; i < 9; i++) {
            if (predicate.test(inv.getStack(i))) {
                return i;
            }
        }
        // Then main inventory (9-35)
        for (int i = 9; i < 36; i++) {
            if (predicate.test(inv.getStack(i))) {
                return i;
            }
        }
        return -1;
    }

    public static boolean canPlaceRailAt(World world, BlockPos pos) {
        if (world == null || pos == null) return false;
        BlockState state = world.getBlockState(pos);
        if (!state.isAir() && !state.isReplaceable()) return false;
        BlockPos downPos = pos.down();
        BlockState downState = world.getBlockState(downPos);
        return downState.isSideSolidFullSquare(world, downPos, Direction.UP) || downState.isOpaqueFullCube();
    }

    public static BlockPos findPlacementPos(MinecraftClient client, ClientPlayerEntity player) {
        World world = client.world;
        if (world == null || player == null) return null;

        // 1. Crosshair block target if aiming at a block within reach
        if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos base = hit.getBlockPos();
            BlockPos candidate = (hit.getSide() == Direction.UP) ? base.up() : base.offset(hit.getSide());
            if (player.getEyePos().distanceTo(Vec3d.ofCenter(candidate)) <= 5.0) {
                if (canPlaceRailAt(world, candidate)) {
                    return candidate;
                }
            }
        }

        // 2. Check ground in front of player along horizontal look vector
        Vec3d look = player.getRotationVector();
        Vec3d horiz = new Vec3d(look.x, 0, look.z);
        if (horiz.lengthSquared() > 0.001) {
            horiz = horiz.normalize();
            for (double d = 1.0; d <= 3.5; d += 0.8) {
                Vec3d check = player.getEyePos().add(horiz.multiply(d));
                BlockPos floorPos = BlockPos.ofFloored(check);
                for (int dy = 0; dy >= -3; dy--) {
                    BlockPos candidate = floorPos.up(dy);
                    if (canPlaceRailAt(world, candidate)) {
                        if (player.getEyePos().distanceTo(Vec3d.ofCenter(candidate)) <= 5.0) {
                            return candidate;
                        }
                    }
                }
            }
        }

        // 3. Fallback: at player's feet
        BlockPos feet = player.getBlockPos();
        if (canPlaceRailAt(world, feet)) {
            return feet;
        }

        return null;
    }

    public static void onStopUsingItem(PlayerEntity player) {
        if (!enabled) return;
        if (!(player instanceof ClientPlayerEntity clientPlayer)) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player != player) return;

        ItemStack active = player.getActiveItem();
        if (!isFlameBow(active)) return;

        int useTime = player.getItemUseTime();
        if (useTime < 3) return;

        long now = System.currentTimeMillis();
        if (now - lastCartTime < 250) return;
        lastCartTime = now;

        placeAutoCart(client, clientPlayer);
    }

    public static void placeAutoCart(MinecraftClient client, ClientPlayerEntity player) {
        if (client.interactionManager == null || client.world == null) return;

        int railSlot = findItemSlot(player, AutoCartModule::isRailItem);
        int cartSlot = findItemSlot(player, AutoCartModule::isTntCartItem);
        if (railSlot == -1 || cartSlot == -1) return;

        BlockPos railPos = findPlacementPos(client, player);
        if (railPos == null) return;
        BlockPos basePos = railPos.down();

        int originalSlot = player.getInventory().getSelectedSlot();
        int tempHotbarSlot = (originalSlot == 8) ? 7 : originalSlot + 1;
        for (int i = 0; i < 9; i++) {
            if (i != originalSlot && player.getInventory().getStack(i).isEmpty()) {
                tempHotbarSlot = i;
                break;
            }
        }

        int syncId = player.playerScreenHandler.syncId;
        boolean swappedRail = false;
        int currentRailHotbar = railSlot;
        if (railSlot >= 9) {
            client.interactionManager.clickSlot(syncId, railSlot, tempHotbarSlot, SlotActionType.SWAP, player);
            currentRailHotbar = tempHotbarSlot;
            swappedRail = true;
        }

        player.getInventory().setSelectedSlot(currentRailHotbar);
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(currentRailHotbar));
        }

        BlockHitResult railHit = new BlockHitResult(
            Vec3d.ofCenter(basePos).add(0, 0.5, 0),
            Direction.UP,
            basePos,
            false
        );
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, railHit);
        player.swingHand(Hand.MAIN_HAND);

        // Find TNT minecart again in case slot index shifted
        int actualCartSlot = findItemSlot(player, AutoCartModule::isTntCartItem);
        boolean swappedCart = false;
        if (actualCartSlot != -1) {
            int currentCartHotbar = actualCartSlot;
            if (actualCartSlot >= 9) {
                client.interactionManager.clickSlot(syncId, actualCartSlot, tempHotbarSlot, SlotActionType.SWAP, player);
                currentCartHotbar = tempHotbarSlot;
                swappedCart = true;
            }

            player.getInventory().setSelectedSlot(currentCartHotbar);
            if (player.networkHandler != null) {
                player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(currentCartHotbar));
            }

            BlockHitResult cartHit = new BlockHitResult(
                Vec3d.ofCenter(railPos).add(0, 0.5, 0),
                Direction.UP,
                railPos,
                false
            );
            client.interactionManager.interactBlock(player, Hand.MAIN_HAND, cartHit);
            player.swingHand(Hand.MAIN_HAND);

            if (swappedCart) {
                client.interactionManager.clickSlot(syncId, actualCartSlot, tempHotbarSlot, SlotActionType.SWAP, player);
            }
        }

        if (swappedRail && !swappedCart) {
            client.interactionManager.clickSlot(syncId, railSlot, tempHotbarSlot, SlotActionType.SWAP, player);
        }

        if (switchBack) {
            player.getInventory().setSelectedSlot(originalSlot);
            if (player.networkHandler != null) {
                player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(originalSlot));
            }
        }
    }

    public static void resetToDefault() {
        enabled = false;
        keyBind = -1;
        expanded = false;
        switchBack = true;
        BameClientConfig.save();
    }
}
