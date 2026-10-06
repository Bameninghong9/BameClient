package com.bame.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Human-like camera aiming and legitimate mining simulation.
 * - Smooth camera easing capped at realistic human speeds.
 * - Angle deltas strictly quantized to the Minecraft mouse sensitivity GCD (bypasses GCD / BadPackets anti-cheat checks).
 * - Strictly validates crosshair line-of-sight before sending attack/break packets (bypasses Raycast / FastBreak anti-cheat checks).
 */
public final class LegitMinerAim {

    private LegitMinerAim() {}

    /**
     * Calculates the exact Mouse Sensitivity GCD according to Minecraft's camera logic.
     */
    public static double getGcd(MinecraftClient client) {
        if (client == null || client.options == null) return 0.001;
        double sens = client.options.getMouseSensitivity().getValue() * 0.6000000238418579 + 0.20000000298023224;
        double gcd = sens * sens * sens * 1.2;
        return Math.max(0.0001, gcd);
    }

    /**
     * Smoothly rotates the player towards targetYaw and targetPitch with human-like easing
     * and strictly quantizes deltas to the client's mouse sensitivity GCD.
     * Returns true if the rotation has settled within tolerance.
     */
    public static boolean rotateTowards(MinecraftClient client, float targetYaw, float targetPitch, float maxTurnSpeed) {
        if (client == null || client.player == null) return true;

        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();

        float diffYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float clampedTargetPitch = MathHelper.clamp(targetPitch, -90.0f, 90.0f);
        float diffPitch = clampedTargetPitch - currentPitch;

        float dist = (float) Math.hypot(diffYaw, diffPitch);
        if (dist < 0.01f) {
            return true;
        }

        // Smooth human easing: turn speed scales with distance, but capped at maxTurnSpeed per tick.
        float speed = Math.min(maxTurnSpeed, Math.max(1.2f, dist * 0.45f));
        float factor = Math.min(1.0f, speed / dist);

        float rawStepYaw = diffYaw * factor;
        float rawStepPitch = diffPitch * factor;

        double gcd = getGcd(client);
        long countX = Math.round(rawStepYaw / gcd);
        long countY = Math.round(rawStepPitch / gcd);

        if (countX == 0 && Math.abs(diffYaw) > gcd * 0.4) {
            countX = diffYaw > 0 ? 1 : -1;
        }
        if (countY == 0 && Math.abs(diffPitch) > gcd * 0.4) {
            countY = diffPitch > 0 ? 1 : -1;
        }

        float stepYaw = (float) (countX * gcd);
        float stepPitch = (float) (countY * gcd);

        client.player.setYaw(currentYaw + stepYaw);
        client.player.setPitch(MathHelper.clamp(currentPitch + stepPitch, -90.0f, 90.0f));

        float remainingDist = (float) Math.hypot(
            MathHelper.wrapDegrees(targetYaw - client.player.getYaw()),
            clampedTargetPitch - client.player.getPitch()
        );
        return remainingDist < 1.5f;
    }

    /**
     * Computes target yaw and pitch to look at a block, optionally aiming at a specific face center.
     */
    public static float[] calculateTargetRotation(MinecraftClient client, BlockPos pos, Direction side) {
        if (client == null || client.player == null) return new float[]{0, 0};
        Vec3d eye = client.player.getEyePos();
        Vec3d target = pos.toCenterPos();
        if (side != null) {
            target = target.add(side.getOffsetX() * 0.42, side.getOffsetY() * 0.42, side.getOffsetZ() * 0.42);
        }
        Vec3d delta = target.subtract(eye);
        double horizontal = Math.hypot(delta.x, delta.z);
        float yaw = horizontal < 0.05 ? client.player.getYaw() : (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return new float[]{yaw, pitch};
    }

    /**
     * Checks if the player's crosshair is legitimately aiming at the target block pos.
     */
    public static BlockHitResult getCrosshairHitOnBlock(MinecraftClient client, BlockPos targetPos) {
        if (client == null || client.crosshairTarget == null) return null;
        if (client.crosshairTarget.getType() == HitResult.Type.BLOCK && client.crosshairTarget instanceof BlockHitResult blockHit) {
            if (blockHit.getBlockPos().equals(targetPos)) {
                return blockHit;
            }
        }
        return null;
    }

    /**
     * Performs a single tick of legit mining:
     * 1. Smoothly rotates towards the block face.
     * 2. Checks if crosshair has reached the block.
     * 3. Only presses attackKey and updates block breaking progress when the crosshair is on the block!
     * Returns true if currently mining the block, false if still aiming.
     */
    public static boolean legitMineTick(MinecraftClient client, BlockPos targetPos, Direction preferredSide) {
        if (client == null || client.player == null || client.interactionManager == null) return false;

        float[] rot = calculateTargetRotation(client, targetPos, preferredSide);
        rotateTowards(client, rot[0], rot[1], 18.0f);

        BlockHitResult hit = getCrosshairHitOnBlock(client, targetPos);
        if (hit != null) {
            client.interactionManager.updateBlockBreakingProgress(targetPos, hit.getSide());
            client.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }
}
