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
    public enum CartMode {
        SLOT_SWITCH("Slot Switch"),
        AUTOMATIC("Automatisch");

        private final String displayName;

        CartMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;
    public static boolean switchBack = true;
    public static CartMode mode = CartMode.SLOT_SWITCH;

    private static boolean wasKeyBindPressed = false;
    private static long lastCartTime = 0;

    // Slot switch tick state machine
    private static int switchStep = 0; // 0 = idle, 1 = cart placement tick, 2 = restore bow tick
    private static int originalBowSlot = -1;
    private static BlockPos targetRailPos = null;
    private static int cachedTempHotbarSlot = -1;

    public static void onTick(MinecraftClient client) {
        // Keybind toggle
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;

        // Process Slot Switch tick progression
        if (switchStep > 0) {
            if (client.player == null || client.world == null || client.interactionManager == null) {
                switchStep = 0;
                originalBowSlot = -1;
                targetRailPos = null;
                return;
            }

            ClientPlayerEntity player = client.player;
            int syncId = player.playerScreenHandler.syncId;

            if (switchStep == 1) {
                // TICK 1: Place TNT Minecart on top of the rail
                if (targetRailPos != null) {
                    int cartSlot = findItemSlot(player, AutoCartModule::isTntCartItem);
                    if (cartSlot != -1) {
                        boolean swappedCart = false;
                        int activeCartHotbar = cartSlot;
                        if (cartSlot >= 9) {
                            client.interactionManager.clickSlot(syncId, cartSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                            activeCartHotbar = cachedTempHotbarSlot;
                            swappedCart = true;
                        }

                        selectHotbarSlot(client, player, activeCartHotbar);

                        BlockHitResult cartHit = new BlockHitResult(
                                Vec3d.ofBottomCenter(targetRailPos).add(0, 0.1, 0),
                                Direction.UP,
                                targetRailPos,
                                false
                        );
                        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, cartHit);
                        player.swingHand(Hand.MAIN_HAND);

                        if (swappedCart) {
                            client.interactionManager.clickSlot(syncId, cartSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                        }
                    }
                }
                switchStep = 2; // Advance to restore bow step
                return;
            }

            if (switchStep == 2) {
                // TICK 2: Restore original bow slot
                if (switchBack && originalBowSlot != -1) {
                    selectHotbarSlot(client, player, originalBowSlot);
                }
                switchStep = 0;
                originalBowSlot = -1;
                targetRailPos = null;
            }
        }
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

        // 1. Crosshair targeting a block within reach
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

        // 2. Crosshair targeting an entity
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

        // 3. Raycast along player look vector
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

        // 4. Horizontal ground check along look vector
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

        // 5. Fallback: at player feet
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
        if (switchStep != 0) return; // Sequence already executing
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.world == null || client.interactionManager == null) return;

        long now = System.currentTimeMillis();
        if (now - lastCartTime < 250) return;
        lastCartTime = now;

        int railSlot = findItemSlot(player, AutoCartModule::isRailItem);
        int cartSlot = findItemSlot(player, AutoCartModule::isTntCartItem);
        if (railSlot == -1 || cartSlot == -1) return;

        BlockPos railPos = findPlacementPos(client, player);
        if (railPos == null) return;

        if (mode == CartMode.SLOT_SWITCH) {
            // ==================== MODE: SLOT SWITCH ====================
            // Visibly and actively switches hotbar slots across game ticks!
            originalBowSlot = player.getInventory().getSelectedSlot();
            targetRailPos = railPos;

            // Pick temporary hotbar slot
            cachedTempHotbarSlot = (originalBowSlot == 8) ? 7 : (originalBowSlot + 1) % 9;
            for (int i = 0; i < 9; i++) {
                if (i != originalBowSlot && player.getInventory().getStack(i).isEmpty()) {
                    cachedTempHotbarSlot = i;
                    break;
                }
            }

            int syncId = player.playerScreenHandler.syncId;
            boolean alreadyHasRail = client.world.getBlockState(railPos).isIn(BlockTags.RAILS);

            if (!alreadyHasRail) {
                // Step 0: Switch to Rail slot & place rail
                boolean swappedRail = false;
                int activeRailHotbar = railSlot;
                if (railSlot >= 9) {
                    client.interactionManager.clickSlot(syncId, railSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                    activeRailHotbar = cachedTempHotbarSlot;
                    swappedRail = true;
                }

                selectHotbarSlot(client, player, activeRailHotbar);

                BlockHitResult railHit = new BlockHitResult(
                        Vec3d.ofBottomCenter(railPos),
                        Direction.UP,
                        railPos.down(),
                        false
                );
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, railHit);
                player.swingHand(Hand.MAIN_HAND);

                if (client.world.getBlockState(railPos).isAir() || client.world.getBlockState(railPos).isReplaceable()) {
                    client.world.setBlockState(railPos, Blocks.RAIL.getDefaultState());
                }

                if (swappedRail) {
                    client.interactionManager.clickSlot(syncId, railSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                }
                switchStep = 1; // TNT cart will be placed on next tick
            } else {
                // Rail already present, place cart immediately on next tick
                switchStep = 1;
            }
        } else {
            // ==================== MODE: AUTOMATIC ====================
            // Fully automatic instant placement in same tick without disturbing active slot visually
            executeInstantAutomatic(client, player, railSlot, cartSlot, railPos);
        }
    }

    private static void executeInstantAutomatic(MinecraftClient client, ClientPlayerEntity player, int railSlot, int cartSlot, BlockPos railPos) {
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

        // 1. Place Rail
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

            if (client.world.getBlockState(railPos).isAir() || client.world.getBlockState(railPos).isReplaceable()) {
                client.world.setBlockState(railPos, Blocks.RAIL.getDefaultState());
            }

            if (swappedRail) {
                client.interactionManager.clickSlot(syncId, railSlot, tempHotbarSlot, SlotActionType.SWAP, player);
            }
        }

        // 2. Place TNT Minecart
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

        // 3. Switch back to bow
        if (switchBack) {
            selectHotbarSlot(client, player, originalSlot);
        }
    }

    public static void resetToDefault() {
        enabled = false;
        keyBind = -1;
        expanded = false;
        switchBack = true;
        mode = CartMode.SLOT_SWITCH;
        switchStep = 0;
        originalBowSlot = -1;
        targetRailPos = null;
        BameClientConfig.save();
    }
}
