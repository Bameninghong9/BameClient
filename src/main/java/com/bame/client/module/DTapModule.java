package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.Predicate;

public class DTapModule {
    public enum DTapMode {
        KEYBIND("Taste"),
        ON_CLICK("Bei Klick"),
        AUTOMATIC("Automatisch");

        private final String displayName;

        DTapMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;
    public static DTapMode mode = DTapMode.KEYBIND;
    public static int tapDelay = 1; // 1 to 10 ticks delay between crystal 1 and crystal 2
    public static boolean switchBack = true;

    private static boolean wasKeyBindPressed = false;
    private static boolean wasUseKeyPressed = false;
    private static long lastDTapTime = 0;

    // State machine:
    // 0 = IDLE
    // 1 = WAITING_BREAK_CRYSTAL_1
    // 2 = WAITING_DELAY_BETWEEN_TAPS
    // 3 = WAITING_BREAK_CRYSTAL_2
    // 4 = FINISH_AND_SWITCH_BACK
    private static int state = 0;
    private static int delayTimer = 0;
    private static int originalSlot = -1;
    private static int swappedInvSlot = -1;
    private static int cachedTempHotbarSlot = -1;
    private static BlockPos targetObsidianPos = null;
    private static int timeoutTicks = 0;

    public static void onTick(MinecraftClient client) {
        // Keybind toggle / trigger
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            if (mode == DTapMode.KEYBIND) {
                // In Keybind mode: pressing the key executes the Double Tap macro!
                triggerDTap(client);
            } else {
                enabled = !enabled;
                BameClientConfig.save();
            }
        }
        wasKeyBindPressed = pressed;

        if (client.player == null || client.world == null) {
            cleanupSequence(client);
            return;
        }

        ClientPlayerEntity player = client.player;

        // Process active D-Tap sequence
        if (state > 0) {
            timeoutTicks++;
            if (timeoutTicks > 30) { // 1.5s timeout safety
                cleanupSequence(client);
                return;
            }

            int syncId = player.playerScreenHandler.syncId;

            // STATE 1: Break Crystal 1 (pop totem)
            if (state == 1) {
                EndCrystalEntity crystal = findCrystalNear(client, targetObsidianPos);
                if (crystal != null) {
                    client.interactionManager.attackEntity(player, crystal);
                    player.swingHand(Hand.MAIN_HAND);
                    CpsModule.registerClick(false);

                    // Advance to delay before placing Crystal 2
                    state = 2;
                    delayTimer = Math.max(1, tapDelay);
                    return;
                } else if (timeoutTicks > 10) {
                    cleanupSequence(client);
                    return;
                }
                return;
            }

            // STATE 2: Delay countdown, then place Crystal 2
            if (state == 2) {
                if (delayTimer > 0) {
                    delayTimer--;
                    return;
                }

                // Place Crystal 2 on the Obsidian
                if (targetObsidianPos != null) {
                    BlockHitResult hit = new BlockHitResult(
                            Vec3d.ofBottomCenter(targetObsidianPos).add(0, 1.0, 0),
                            Direction.UP,
                            targetObsidianPos,
                            false
                    );
                    client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                    player.swingHand(Hand.MAIN_HAND);

                    state = 3; // Advance to wait for Crystal 2 spawn & break
                    timeoutTicks = 0;
                    return;
                } else {
                    cleanupSequence(client);
                    return;
                }
            }

            // STATE 3: Break Crystal 2 (kill hit)
            if (state == 3) {
                EndCrystalEntity crystal = findCrystalNear(client, targetObsidianPos);
                if (crystal != null) {
                    client.interactionManager.attackEntity(player, crystal);
                    player.swingHand(Hand.MAIN_HAND);
                    CpsModule.registerClick(false);

                    state = 4; // Advance to switch back
                    delayTimer = 1; // 1 tick buffer
                    return;
                } else if (timeoutTicks > 10) {
                    cleanupSequence(client);
                    return;
                }
                return;
            }

            // STATE 4: Restore original slot & cleanup
            if (state == 4) {
                if (delayTimer > 0) {
                    delayTimer--;
                    return;
                }

                if (swappedInvSlot != -1 && cachedTempHotbarSlot != -1) {
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
                targetObsidianPos = null;
                timeoutTicks = 0;
                lastDTapTime = System.currentTimeMillis();
                return;
            }
        }

        if (!enabled || client.currentScreen != null || !player.isAlive()) {
            return;
        }

        // On-Click mode: trigger when placing crystal
        if (mode == DTapMode.ON_CLICK) {
            boolean useDown = client.options.useKey.isPressed();
            if (useDown && !wasUseKeyPressed) {
                ItemStack mainHand = player.getMainHandStack();
                if (mainHand.isOf(Items.END_CRYSTAL)) {
                    triggerDTap(client);
                }
            }
            wasUseKeyPressed = useDown;
        } else if (mode == DTapMode.AUTOMATIC) {
            long now = System.currentTimeMillis();
            if (now - lastDTapTime > 500) {
                BlockPos obs = findObsidianTarget(client, player);
                if (obs != null && hasEnemyNearby(client, player, obs)) {
                    triggerDTap(client);
                }
            }
        }
    }

    public static void triggerDTap(MinecraftClient client) {
        if (state != 0 || client.player == null || client.world == null) return;
        ClientPlayerEntity player = client.player;

        // Find target Obsidian block
        BlockPos obsPos = findObsidianTarget(client, player);
        if (obsPos == null) {
            return;
        }

        // Check if block above obsidian is clear
        if (!client.world.getBlockState(obsPos.up()).isAir() && !client.world.getBlockState(obsPos.up()).isReplaceable()) {
            return;
        }

        // Find End Crystal item
        int crystalSlot = findItemSlot(player, stack -> stack.isOf(Items.END_CRYSTAL));
        if (crystalSlot == -1) {
            return; // No End Crystals in inventory!
        }

        originalSlot = player.getInventory().getSelectedSlot();
        int syncId = player.playerScreenHandler.syncId;

        // Switch to End Crystal
        if (crystalSlot < 9) {
            selectHotbarSlot(client, player, crystalSlot);
        } else {
            cachedTempHotbarSlot = originalSlot;
            client.interactionManager.clickSlot(syncId, crystalSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
            swappedInvSlot = crystalSlot;
            selectHotbarSlot(client, player, cachedTempHotbarSlot);
        }

        targetObsidianPos = obsPos;

        // Place Crystal 1 on the Obsidian block
        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofBottomCenter(obsPos).add(0, 1.0, 0),
                Direction.UP,
                obsPos,
                false
        );
        client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);

        state = 1; // Wait for Crystal 1 to appear and break it!
        timeoutTicks = 0;
    }

    public static BlockPos findObsidianTarget(MinecraftClient client, ClientPlayerEntity player) {
        double maxReach = player.getBlockInteractionRange();
        if (maxReach < 4.5) maxReach = 4.5;

        // 1. Crosshair target is an Obsidian or Bedrock block
        if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            if (isCrystalBase(client, pos)) {
                if (player.getEyePos().distanceTo(Vec3d.ofCenter(pos)) <= maxReach + 0.5) {
                    return pos;
                }
            }
        }

        // 2. Raycast along look direction
        Vec3d eye = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d end = eye.add(look.multiply(maxReach));
        BlockHitResult ray = client.world.raycast(new net.minecraft.world.RaycastContext(
                eye, end,
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                player
        ));
        if (ray != null && ray.getType() == HitResult.Type.BLOCK && isCrystalBase(client, ray.getBlockPos())) {
            return ray.getBlockPos();
        }

        // 3. Search ground around player within reach
        BlockPos playerPos = player.getBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    BlockPos candidate = playerPos.add(dx, dy, dz);
                    if (isCrystalBase(client, candidate) && eye.distanceTo(Vec3d.ofCenter(candidate)) <= maxReach) {
                        if (client.world.getBlockState(candidate.up()).isAir() || client.world.getBlockState(candidate.up()).isReplaceable()) {
                            return candidate;
                        }
                    }
                }
            }
        }

        return null;
    }

    private static boolean isCrystalBase(MinecraftClient client, BlockPos pos) {
        if (client.world == null || pos == null) return false;
        var bState = client.world.getBlockState(pos);
        return bState.isOf(Blocks.OBSIDIAN) || bState.isOf(Blocks.BEDROCK);
    }

    private static EndCrystalEntity findCrystalNear(MinecraftClient client, BlockPos pos) {
        if (client.world == null || pos == null) return null;
        Box box = new Box(pos).expand(1.5, 2.5, 1.5);
        List<Entity> entities = client.world.getOtherEntities(client.player, box, e -> e instanceof EndCrystalEntity && e.isAlive());
        if (!entities.isEmpty()) {
            return (EndCrystalEntity) entities.get(0);
        }
        return null;
    }

    private static boolean hasEnemyNearby(MinecraftClient client, ClientPlayerEntity player, BlockPos pos) {
        if (client.world == null || pos == null) return false;
        Box box = new Box(pos).expand(6.0);
        List<Entity> list = client.world.getOtherEntities(player, box, e -> e instanceof PlayerEntity other && other.isAlive() && !other.isCreative() && !other.isSpectator());
        return !list.isEmpty();
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
        targetObsidianPos = null;
        timeoutTicks = 0;
    }

    public static void resetToDefault() {
        enabled = false;
        keyBind = -1;
        expanded = false;
        mode = DTapMode.KEYBIND;
        tapDelay = 1;
        switchBack = true;
        state = 0;
        delayTimer = 0;
        originalSlot = -1;
        swappedInvSlot = -1;
        cachedTempHotbarSlot = -1;
        targetObsidianPos = null;
        timeoutTicks = 0;
        BameClientConfig.save();
    }
}
