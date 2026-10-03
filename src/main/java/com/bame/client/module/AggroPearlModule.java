package com.bame.client.module;

import com.bame.client.BameClientConfig;
import com.bame.client.mixin.ClientPlayerInteractionManagerAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class AggroPearlModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;
    public static int throwDelay = 1; // 0 to 5 ticks delay after enemy throws pearl
    public static boolean autoAim = true; // Automatically aims at the calculated trajectory
    public static boolean switchBack = true; // Switch back to original slot after throwing

    private static boolean wasKeyBindPressed = false;

    // Track processed enemy pearl entity IDs so we don't counter the same pearl repeatedly
    private static final Set<Integer> processedPearlIds = new HashSet<>();

    // State machine:
    // 0 = IDLE
    // 1 = WAITING_DELAY
    // 2 = FINISH_AND_RESTORE
    private static int state = 0;
    private static int delayTimer = 0;
    private static int originalSlot = -1;
    private static float originalPitch = 0f;
    private static float originalYaw = 0f;
    private static int swappedPearlInvSlot = -1;
    private static int cachedTempHotbarSlot = -1;
    private static Vec3d pendingTargetPos = null;
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

        // Clean up processed pearl IDs if set gets large
        if (processedPearlIds.size() > 50) {
            processedPearlIds.clear();
        }

        // 1. Scan for newly thrown enemy pearls if enabled and idle
        if (enabled && state == 0 && client.world != null) {
            for (Entity entity : client.world.getEntities()) {
                if (entity instanceof EnderPearlEntity pearl && pearl.isAlive()) {
                    if (pearl.getOwner() != player && !processedPearlIds.contains(pearl.getId())) {
                        // Only target fresh pearls (age <= 10 ticks)
                        if (pearl.age <= 10) {
                            processedPearlIds.add(pearl.getId());
                            // Calculate where the enemy pearl lands
                            Vec3d targetLanding = simulatePearlLanding(client.world, pearl.getEntityPos(), pearl.getVelocity(), pearl);
                            if (targetLanding != null) {
                                triggerAggroPearl(client, targetLanding);
                                break;
                            }
                        }
                    }
                }
            }
        }

        // 2. Active sequence
        if (state > 0) {
            timeoutTicks++;
            if (timeoutTicks > 40) { // 2s safety timeout
                cleanupSequence(client);
                return;
            }

            int syncId = player.playerScreenHandler.syncId;

            // STATE 1: Waiting throw delay, then aim & throw pearl!
            if (state == 1) {
                if (delayTimer > 0) {
                    delayTimer--;
                    return;
                }

                if (pendingTargetPos == null) {
                    cleanupSequence(client);
                    return;
                }

                // Check for Ender Pearl
                int pearlSlot = findItemSlot(player, stack -> stack.isOf(Items.ENDER_PEARL));
                if (pearlSlot == -1) {
                    cleanupSequence(client);
                    return;
                }

                // Calculate Yaw & Pitch to reach target landing pos
                float[] angles = solveLaunchAngles(client.world, player, pendingTargetPos);
                float targetYaw = angles[0];
                float targetPitch = angles[1];

                originalSlot = player.getInventory().getSelectedSlot();
                originalPitch = player.getPitch();
                originalYaw = player.getYaw();

                if (autoAim) {
                    player.setYaw(targetYaw);
                    player.setPitch(targetPitch);
                }

                // Switch to pearl
                if (pearlSlot < 9) {
                    selectHotbarSlot(client, player, pearlSlot);
                } else {
                    cachedTempHotbarSlot = originalSlot != -1 ? originalSlot : player.getInventory().getSelectedSlot();
                    client.interactionManager.clickSlot(syncId, pearlSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                    swappedPearlInvSlot = pearlSlot;
                    selectHotbarSlot(client, player, cachedTempHotbarSlot);
                }

                // Throw pearl
                client.interactionManager.interactItem(player, Hand.MAIN_HAND);
                player.swingHand(Hand.MAIN_HAND);
                CpsModule.registerClick(false);

                state = 2;
                delayTimer = 1; // 1 tick buffer
                return;
            }

            // STATE 2: Restore slots & pitch/yaw
            if (state == 2) {
                if (delayTimer > 0) {
                    delayTimer--;
                    return;
                }

                if (swappedPearlInvSlot != -1 && cachedTempHotbarSlot != -1) {
                    client.interactionManager.clickSlot(syncId, swappedPearlInvSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
                    swappedPearlInvSlot = -1;
                }

                if (switchBack && originalSlot != -1) {
                    selectHotbarSlot(client, player, originalSlot);
                }

                if (autoAim) {
                    player.setYaw(originalYaw);
                    player.setPitch(originalPitch);
                }

                state = 0;
                delayTimer = 0;
                originalSlot = -1;
                cachedTempHotbarSlot = -1;
                pendingTargetPos = null;
                timeoutTicks = 0;
            }
        }
    }

    public static void triggerAggroPearl(MinecraftClient client, Vec3d targetPos) {
        if (state != 0 || client.player == null || targetPos == null) return;
        ClientPlayerEntity player = client.player;

        // Verify player has Ender Pearl
        int pearlSlot = findItemSlot(player, stack -> stack.isOf(Items.ENDER_PEARL));
        if (pearlSlot == -1) return;

        pendingTargetPos = targetPos;
        state = 1;
        delayTimer = Math.max(0, throwDelay);
        timeoutTicks = 0;
    }

    public static float[] solveLaunchAngles(ClientWorld world, ClientPlayerEntity player, Vec3d targetPos) {
        Vec3d eyePos = new Vec3d(player.getX(), player.getEyeY() - 0.1, player.getZ());
        double dx = targetPos.x - eyePos.x;
        double dz = targetPos.z - eyePos.z;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        float bestPitch = -43.0f;
        double bestDistSq = Double.MAX_VALUE;

        // Pass 1: Coarse search (5° steps from -75° to +15°)
        for (float p = -75.0f; p <= 15.0f; p += 5.0f) {
            Vec3d launchVel = getLaunchVelocity(player, targetYaw, p);
            Vec3d simulatedLand = simulatePearlLanding(world, eyePos, launchVel, player, false);
            if (simulatedLand != null) {
                double distSq = simulatedLand.squaredDistanceTo(targetPos);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestPitch = p;
                }
            }
        }

        // Pass 2: Medium refinement (1.5° steps around best)
        float mediumPitch = bestPitch;
        float startP = Math.max(-85.0f, bestPitch - 4.0f);
        float endP = Math.min(25.0f, bestPitch + 4.0f);
        for (float p = startP; p <= endP; p += 1.5f) {
            Vec3d launchVel = getLaunchVelocity(player, targetYaw, p);
            Vec3d simulatedLand = simulatePearlLanding(world, eyePos, launchVel, player, false);
            if (simulatedLand != null) {
                double distSq = simulatedLand.squaredDistanceTo(targetPos);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    mediumPitch = p;
                }
            }
        }

        // Pass 3: Fine refinement (0.5° steps around medium)
        float finePitch = mediumPitch;
        startP = Math.max(-85.0f, mediumPitch - 1.0f);
        endP = Math.min(25.0f, mediumPitch + 1.0f);
        for (float p = startP; p <= endP; p += 0.5f) {
            Vec3d launchVel = getLaunchVelocity(player, targetYaw, p);
            Vec3d simulatedLand = simulatePearlLanding(world, eyePos, launchVel, player, false);
            if (simulatedLand != null) {
                double distSq = simulatedLand.squaredDistanceTo(targetPos);
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    finePitch = p;
                }
            }
        }

        return new float[]{targetYaw, finePitch};
    }

    private static Vec3d getLaunchVelocity(ClientPlayerEntity player, float targetYaw, float pitch) {
        float f = 0.017453292F;
        float vx = -MathHelper.sin(targetYaw * f) * MathHelper.cos(pitch * f);
        float vy = -MathHelper.sin(pitch * f);
        float vz = MathHelper.cos(targetYaw * f) * MathHelper.cos(pitch * f);
        Vec3d dir = new Vec3d(vx, vy, vz).normalize();
        Vec3d launchVel = dir.multiply(1.5);
        Vec3d pVel = player.getVelocity();
        return launchVel.add(pVel.x, player.isOnGround() ? 0.0 : pVel.y, pVel.z);
    }

    public static Vec3d simulatePearlLanding(ClientWorld world, Vec3d startPos, Vec3d startVel, Entity ignoreEntity) {
        return simulatePearlLanding(world, startPos, startVel, ignoreEntity, true);
    }

    public static Vec3d simulatePearlLanding(ClientWorld world, Vec3d startPos, Vec3d startVel, Entity ignoreEntity, boolean checkEntities) {
        if (world == null) return null;
        Vec3d pos = startPos;
        Vec3d vel = startVel;
        MinecraftClient client = MinecraftClient.getInstance();

        for (int step = 0; step < 140; step++) {
            if (pos.y < -64.0) break; // Falling into the void

            Vec3d nextPos = pos.add(vel);

            RaycastContext raycastContext = new RaycastContext(
                    pos, nextPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    ignoreEntity != null ? ignoreEntity : client.player
            );
            BlockHitResult blockHit = world.raycast(raycastContext);
            Vec3d stepEnd = (blockHit != null && blockHit.getType() != HitResult.Type.MISS) ? blockHit.getPos() : nextPos;

            if (checkEntities) {
                Box stepBox = new Box(pos, stepEnd).expand(0.8);
                EntityHitResult entityHit = ProjectileUtil.getEntityCollision(
                        world,
                        ignoreEntity != null ? ignoreEntity : client.player,
                        pos, stepEnd, stepBox,
                        e -> !e.isSpectator() && e.canHit() && e != (ignoreEntity != null ? (ignoreEntity instanceof EnderPearlEntity ep ? ep.getOwner() : ignoreEntity) : client.player),
                        0.0f
                );

                if (entityHit != null) {
                    return entityHit.getPos();
                }
            }

            if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
                return blockHit.getPos();
            }

            pos = nextPos;
            vel = vel.multiply(0.99).subtract(0, 0.03, 0);
        }
        return pos;
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
            if (swappedPearlInvSlot != -1 && cachedTempHotbarSlot != -1) {
                client.interactionManager.clickSlot(syncId, swappedPearlInvSlot, cachedTempHotbarSlot, SlotActionType.SWAP, player);
            }
            if (switchBack && originalSlot != -1) {
                selectHotbarSlot(client, player, originalSlot);
            }
            if (autoAim && state > 0) {
                player.setYaw(originalYaw);
                player.setPitch(originalPitch);
            }
        }
        state = 0;
        delayTimer = 0;
        originalSlot = -1;
        swappedPearlInvSlot = -1;
        cachedTempHotbarSlot = -1;
        pendingTargetPos = null;
        timeoutTicks = 0;
    }

    public static void resetToDefault() {
        enabled = false;
        keyBind = -1;
        expanded = false;
        throwDelay = 1;
        autoAim = true;
        switchBack = true;
        state = 0;
        delayTimer = 0;
        originalSlot = -1;
        swappedPearlInvSlot = -1;
        cachedTempHotbarSlot = -1;
        pendingTargetPos = null;
        timeoutTicks = 0;
        processedPearlIds.clear();
        BameClientConfig.save();
    }
}
