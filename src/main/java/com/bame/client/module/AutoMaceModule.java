package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.function.Predicate;

public class AutoMaceModule {
    public enum MaceMode {
        AUTOMATIC("Automatisch"),
        ON_CLICK("Bei Klick");

        private final String displayName;

        MaceMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;
    public static MaceMode mode = MaceMode.AUTOMATIC;
    public static int switchDelay = 2; // 1, 2, or 3 ticks (default 2 for AntiCheat safety)
    public static boolean switchBack = true;
    public static boolean cooldownCheck = true; // wait for attack cooldown (full smash damage + AntiCheat safe)
    public static boolean onlyPlayers = true;
    public static double minFallDistance = 1.5; // vanilla requirement for smash attack

    private static boolean wasKeyBindPressed = false;
    private static boolean wasAttackKeyPressed = false;
    private static long lastSmashTime = 0;

    // Slot switch tick state machine: 0 = IDLE, 1 = SWITCHED_WAIT_ATTACK, 2 = HIT_WAIT_SWITCH_BACK
    private static int state = 0;
    private static int delayTimer = 0;
    private static int originalSlot = -1;
    private static int swappedInvSlot = -1;
    private static int cachedTempHotbarSlot = -1;
    private static Entity currentTarget = null;
    private static int timeoutTicks = 0;

    public static void onTick(MinecraftClient client) {
        // Keybind toggle
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;

        if (client.player == null || client.world == null) {
            cleanupSequence(client);
            return;
        }

        ClientPlayerEntity player = client.player;

        // Process active sequence
        if (state > 0) {
            timeoutTicks++;
            if (timeoutTicks > 25) { // 1.25s safety timeout
                cleanupSequence(client);
                return;
            }

            // If player landed on ground without hitting or died, abort cleanly
            if ((player.isOnGround() && player.fallDistance < 0.2) || !player.isAlive()) {
                cleanupSequence(client);
                return;
            }

            // Delay countdown for humanized / AntiCheat timing
            if (delayTimer > 0) {
                delayTimer--;
                return;
            }

            int syncId = player.playerScreenHandler.syncId;

            // STATE 1: Waiting to perform the hit after Mace switch
            if (state == 1) {
                // AntiCheat cooldown check: ensure attack cooldown is charged for maximum smash damage
                if (cooldownCheck && player.getAttackCooldownProgress(0.5f) < 0.85f) {
                    if (timeoutTicks < 8) {
                        return; // Wait up to a few ticks for full charge
                    }
                }

                // Check if target is still valid and in range
                if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isRemoved()) {
                    currentTarget = findTarget(client, player);
                }

                if (currentTarget != null) {
                    double reach = player.getEntityInteractionRange() + 0.3;
                    Vec3d eyePos = player.getEyePos();
                    Vec3d targetCenter = currentTarget.getEntityPos().add(0, currentTarget.getHeight() / 2.0, 0);

                    if (eyePos.distanceTo(targetCenter) <= reach + 0.8) {
                        // Legitimate attack execution
                        client.interactionManager.attackEntity(player, currentTarget);
                        player.swingHand(Hand.MAIN_HAND);
                        CpsModule.registerClick(false);
                        lastSmashTime = System.currentTimeMillis();

                        // Advance to State 2: wait before switching back
                        state = 2;
                        delayTimer = Math.max(1, switchDelay);
                        return;
                    }
                }

                // If not reached yet but still falling, keep waiting
                if (player.getVelocity().y < 0 && !player.isOnGround()) {
                    return;
                } else {
                    cleanupSequence(client);
                    return;
                }
            }

            // STATE 2: Waiting delay after hit, then restore original slot
            if (state == 2) {
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
                currentTarget = null;
                timeoutTicks = 0;
                return;
            }
        }

        if (!enabled || client.currentScreen != null || !player.isAlive()) {
            return;
        }

        // Check if player is falling (1.21 Smash condition: fallDistance > 1.5, not gliding, falling down)
        boolean isFalling = player.fallDistance >= minFallDistance
                && player.getVelocity().y < -0.05
                && !player.isGliding()
                && !player.isOnGround()
                && !player.isTouchingWater()
                && !player.isInLava()
                && !player.isClimbing();

        if (!isFalling) {
            wasAttackKeyPressed = client.options.attackKey.isPressed();
            return;
        }

        // Check if recently smashed (cooldown between smash jumps)
        long now = System.currentTimeMillis();
        if (now - lastSmashTime < 350) {
            wasAttackKeyPressed = client.options.attackKey.isPressed();
            return;
        }

        // Trigger condition check depending on Mode
        boolean trigger = false;
        if (mode == MaceMode.AUTOMATIC) {
            // In automatic mode, triggers when falling towards an entity
            trigger = true;
        } else if (mode == MaceMode.ON_CLICK) {
            // In on-click mode, triggers when player attacks while falling
            boolean attackDown = client.options.attackKey.isPressed();
            if (attackDown && !wasAttackKeyPressed) {
                trigger = true;
            }
            wasAttackKeyPressed = attackDown;
        }

        if (!trigger) {
            return;
        }

        // Find candidate target
        Entity target = findTarget(client, player);
        if (target == null) {
            return;
        }

        // Distance check: don't switch slot if target is still 10 blocks away
        double reach = player.getEntityInteractionRange() + 0.3;
        double currentDist = player.getEyePos().distanceTo(target.getEntityPos().add(0, target.getHeight() / 2.0, 0));
        if (currentDist > reach + 1.8) {
            return; // Wait until player gets closer on descent
        }

        // Check if player already holds a Mace in hand
        ItemStack heldStack = player.getMainHandStack();
        if (heldStack.getItem() == Items.MACE) {
            // Already holding Mace, simply hit when close enough!
            if (currentDist <= reach + 0.5) {
                if (!cooldownCheck || player.getAttackCooldownProgress(0.5f) >= 0.85f) {
                    client.interactionManager.attackEntity(player, target);
                    player.swingHand(Hand.MAIN_HAND);
                    CpsModule.registerClick(false);
                    lastSmashTime = now;
                }
            }
            return;
        }

        // Find Mace in inventory
        int maceSlot = findItemSlot(player, stack -> stack.getItem() == Items.MACE);
        if (maceSlot == -1) {
            return; // No Mace found
        }

        originalSlot = player.getInventory().getSelectedSlot();
        int syncId = player.playerScreenHandler.syncId;

        if (maceSlot < 9) {
            // Mace is in hotbar
            selectHotbarSlot(client, player, maceSlot);
        } else {
            // Mace is in main inventory (9-35), swap into selected hotbar slot
            cachedTempHotbarSlot = originalSlot;
            client.interactionManager.clickSlot(syncId, maceSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
            swappedInvSlot = maceSlot;
            selectHotbarSlot(client, player, cachedTempHotbarSlot);
        }

        currentTarget = target;
        state = 1; // Advanced to wait for switch delay & attack
        delayTimer = Math.max(1, switchDelay);
        timeoutTicks = 0;
    }

    public static Entity findTarget(MinecraftClient client, ClientPlayerEntity player) {
        if (client.targetedEntity instanceof LivingEntity living && isValidTarget(living, player)) {
            return living;
        }

        if (client.crosshairTarget instanceof EntityHitResult eHit && eHit.getEntity() instanceof LivingEntity living && isValidTarget(living, player)) {
            return living;
        }

        double maxReach = player.getEntityInteractionRange() + 0.5;
        Vec3d eyePos = player.getEyePos();
        Vec3d lookVec = player.getRotationVec(1.0f);

        Box searchBox = player.getBoundingBox().expand(maxReach + 1.0);
        List<Entity> candidates = client.world.getOtherEntities(player, searchBox, e -> e instanceof LivingEntity living && isValidTarget(living, player));

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;

        for (Entity candidate : candidates) {
            Box box = candidate.getBoundingBox().expand(0.15);
            var hit = box.raycast(eyePos, eyePos.add(lookVec.multiply(maxReach + 0.5)));
            if (hit.isPresent()) {
                double d = eyePos.distanceTo(hit.get());
                if (d < closestDist && d <= maxReach) {
                    closestDist = d;
                    closest = candidate;
                }
            } else {
                Vec3d toEntity = candidate.getEntityPos().add(0, candidate.getHeight() / 2.0, 0).subtract(eyePos);
                double dist = toEntity.length();
                if (dist <= maxReach) {
                    double dot = lookVec.dotProduct(toEntity.normalize());
                    if (dot > 0.65) { // within ~49 degrees of crosshair
                        if (dist < closestDist) {
                            closestDist = dist;
                            closest = candidate;
                        }
                    }
                }
            }
        }

        return closest;
    }

    private static boolean isValidTarget(LivingEntity target, ClientPlayerEntity player) {
        if (target == null || !target.isAlive() || target.isRemoved()) return false;
        if (target == player) return false;
        if (onlyPlayers && !(target instanceof PlayerEntity)) return false;
        if (target instanceof PlayerEntity other && (other.isCreative() || other.isSpectator())) return false;
        return true;
    }

    private static int findItemSlot(PlayerEntity player, Predicate<ItemStack> predicate) {
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
        currentTarget = null;
        timeoutTicks = 0;
    }

    public static void resetToDefault() {
        enabled = false;
        keyBind = -1;
        expanded = false;
        mode = MaceMode.AUTOMATIC;
        switchDelay = 2; // Default 2 Ticks for AntiCheat safety
        switchBack = true;
        cooldownCheck = true;
        onlyPlayers = true;
        minFallDistance = 1.5;
        state = 0;
        delayTimer = 0;
        originalSlot = -1;
        swappedInvSlot = -1;
        cachedTempHotbarSlot = -1;
        currentTarget = null;
        timeoutTicks = 0;
        BameClientConfig.save();
    }
}
