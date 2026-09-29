package com.bame.client.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.Optional;

public class TargetHudModule {
    public static boolean enabled = false;
    public static boolean expanded = false;
    public static boolean showHearts = true;
    public static boolean showArmor = false;
    public static boolean playersOnly = false;
    public static int keyBind = -1;
    public static int hudX = -1;
    public static int hudY = -1;
    public static float scale = 1.0f;
    public static int bgMode = 0; // 0 = Dark, 1 = Transparent, 2 = Blur, 3 = Outline
    public static int outlineColor = 0xFFFFFFFF;
    public static int bgColor = 0xD012161E;
    public static int customWidth = -1;
    public static int customHeight = -1;

    public static LivingEntity currentTarget = null;
    public static long lastTargetTime = 0;

    public static void onClientTick(MinecraftClient client) {
        if (client.world == null) {
            currentTarget = null;
            return;
        }

        if (enabled) {
            LivingEntity target = findTarget(client, 20.0);
            if (target != null) {
                currentTarget = target;
                lastTargetTime = System.currentTimeMillis();
            }
        }
    }

    public static LivingEntity findTarget(MinecraftClient client, double maxDistance) {
        if (client.player == null || client.world == null) return null;

        if (client.targetedEntity instanceof LivingEntity living) {
            if (!playersOnly || living instanceof PlayerEntity) {
                return living;
            }
        }

        Vec3d cameraPos = client.player.getCameraPosVec(1.0F);
        Vec3d rotation = client.player.getRotationVec(1.0F);
        Vec3d reachVec = cameraPos.add(rotation.multiply(maxDistance));
        Box searchBox = client.player.getBoundingBox().stretch(rotation.multiply(maxDistance)).expand(2.0, 2.0, 2.0);

        BlockHitResult blockHit = client.world.raycast(new RaycastContext(
                cameraPos, reachVec,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                client.player
        ));
        double maxDistSq = maxDistance * maxDistance;
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            maxDistSq = cameraPos.squaredDistanceTo(blockHit.getPos());
        }

        LivingEntity bestEntity = null;
        double closestDistSq = maxDistSq;

        for (Entity entity : client.world.getOtherEntities(client.player, searchBox, e -> !e.isSpectator() && e.canHit() && e instanceof LivingEntity)) {
            if (playersOnly && !(entity instanceof PlayerEntity)) continue;

            Box box = entity.getBoundingBox().expand(0.3D);
            Optional<Vec3d> hit = box.raycast(cameraPos, reachVec);
            if (hit.isPresent()) {
                double distSq = cameraPos.squaredDistanceTo(hit.get());
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    bestEntity = (LivingEntity) entity;
                }
            }
        }
        return bestEntity;
    }
}
