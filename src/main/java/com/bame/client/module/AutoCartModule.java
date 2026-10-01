package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
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
        if (state.isIn(BlockTags.RAILS)) {
            return true;
        }
        if (!state.isAir() && !state.isReplaceable()) {
            return false;
        }
        return Blocks.RAIL.getDefaultState().canPlaceAt(world, pos);
    }

    public static BlockPos findPlacementPos(MinecraftClient client, ClientPlayerEntity player) {
        World world = client.world;
        if (world == null || player == null) return null;

        double maxReach = player.getBlockInteractionRange();
        if (maxReach < 4.5) maxReach = 4.5;

        // 1. Crosshair targeting a block within reach (as demonstrated in the video at 7:21)
        if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos base = hit.getBlockPos();
            BlockPos candidate = (hit.getSide() == Direction.UP) ? base.up() : base.offset(hit.getSide());
            if (player.getEyePos().distanceTo(Vec3d.ofCenter(candidate)) <= maxReach + 0.5) {
                if (canPlaceRailAt(world, candidate)) {
                    return candidate;
                }
                if (hit.getSide() != Direction.UP && canPlaceRailAt(world, base.up())) {
                    return base.up();
                }
            }
        }

        // 2. Crosshair targeting an entity (e.g. enemy player or mob in combat)
        if (client.crosshairTarget instanceof EntityHitResult entityHit && entityHit.getType() == HitResult.Type.ENTITY) {
            Entity target = entityHit.getEntity();
            if (target != null) {
                BlockPos feet = target.getBlockPos();
                if (player.getEyePos().distanceTo(Vec3d.ofCenter(feet)) <= maxReach + 0.5) {
                    if (canPlaceRailAt(world, feet)) {
                        return feet;
                    }
                    if (canPlaceRailAt(world, feet.down())) {
                        return feet.down();
                    }
                }
            }
        }

        // 3. Raycast along player's look vector up to maxReach
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);
        Vec3d endPos = eyePos.add(lookVec.multiply(maxReach));
        BlockHitResult rayHit = world.raycast(new RaycastContext(
                eyePos,
                endPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player
        ));
        if (rayHit != null && rayHit.getType() == HitResult.Type.BLOCK) {
            BlockPos base = rayHit.getBlockPos();
            BlockPos candidate = (rayHit.getSide() == Direction.UP) ? base.up() : base.offset(rayHit.getSide());
            if (canPlaceRailAt(world, candidate)) {
                return candidate;
            }
        }

        // 4. Horizontal floor check along look direction
        Vec3d horiz = new Vec3d(lookVec.x, 0, lookVec.z);
        if (horiz.lengthSquared() > 0.001) {
            horiz = horiz.normalize();
            for (double d = 3.5; d >= 1.0; d -= 0.5) {
                Vec3d check = eyePos.add(horiz.multiply(d));
                BlockPos floorPos = BlockPos.ofFloored(check);
                for (int dy = 0; dy >= -3; dy--) {
                    BlockPos candidate = floorPos.up(dy);
                    if (canPlaceRailAt(world, candidate) && eyePos.distanceTo(Vec3d.ofCenter(candidate)) <= maxReach + 0.5) {
                        return candidate;
                    }
                }
            }
        }

        // 5. Fallback: at player's feet
        BlockPos feet = player.getBlockPos();
        if (canPlaceRailAt(world, feet)) {
            return feet;
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

    public static void onFlameBowShot(ClientPlayerEntity player) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.interactionManager == null) return;

        long now = System.currentTimeMillis();
        if (now - lastCartTime < 200) return;
        lastCartTime = now;

        int railSlot = findItemSlot(player, AutoCartModule::isRailItem);
        int cartSlot = findItemSlot(player, AutoCartModule::isTntCartItem);
        if (railSlot == -1 || cartSlot == -1) return;

        BlockPos railPos = findPlacementPos(client, player);
        if (railPos == null) return;
        BlockPos basePos = railPos.down();

        int originalSlot = player.getInventory().getSelectedSlot();
        int tempHotbarSlot = (originalSlot == 8) ? 7 : (originalSlot + 1) % 9;
        for (int i = 0; i < 9; i++) {
            if (i != originalSlot && player.getInventory().getStack(i).isEmpty()) {
                tempHotbarSlot = i;
                break;
            }
        }

        int syncId = player.playerScreenHandler.syncId;

        // 1. PLACE RAIL (only if there isn't already a rail there)
        boolean alreadyHasRail = client.world.getBlockState(railPos).isIn(BlockTags.RAILS);
        if (!alreadyHasRail) {
            boolean swappedRail = false;
            int activeRailHotbar = railSlot;
            if (railSlot >= 9) {
                client.interactionManager.clickSlot(syncId, railSlot, tempHotbarSlot, SlotActionType.SWAP, player);
                activeRailHotbar = tempHotbarSlot;
                swappedRail = true;
            }

            selectHotbarSlot(client, player, activeRailHotbar);

            BlockHitResult railHit = new BlockHitResult(
                    Vec3d.ofBottomCenter(railPos),
                    Direction.UP,
                    basePos,
                    false
            );
            client.interactionManager.interactBlock(player, Hand.MAIN_HAND, railHit);
            player.swingHand(Hand.MAIN_HAND);

            // Predict rail in client world so subsequent cart placement recognizes it instantly
            if (client.world.getBlockState(railPos).isAir() || client.world.getBlockState(railPos).isReplaceable()) {
                client.world.setBlockState(railPos, Blocks.RAIL.getDefaultState());
            }

            if (swappedRail) {
                client.interactionManager.clickSlot(syncId, railSlot, tempHotbarSlot, SlotActionType.SWAP, player);
            }
        }

        // 2. PLACE TNT MINECART ON THE RAIL
        int actualCartSlot = findItemSlot(player, AutoCartModule::isTntCartItem);
        if (actualCartSlot != -1) {
            boolean swappedCart = false;
            int activeCartHotbar = actualCartSlot;
            if (actualCartSlot >= 9) {
                client.interactionManager.clickSlot(syncId, actualCartSlot, tempHotbarSlot, SlotActionType.SWAP, player);
                activeCartHotbar = tempHotbarSlot;
                swappedCart = true;
            }

            selectHotbarSlot(client, player, activeCartHotbar);

            BlockHitResult cartHit = new BlockHitResult(
                    Vec3d.ofBottomCenter(railPos).add(0, 0.1, 0),
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

        // 3. SWITCH BACK TO ORIGINAL BOW SLOT
        if (switchBack) {
            selectHotbarSlot(client, player, originalSlot);
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
