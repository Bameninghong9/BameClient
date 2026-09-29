package com.bame.client.module;

import com.bame.client.BameClientConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

public class CustomHitboxesModule {
    public static boolean enabled = false;
    public static int keyBind = -1;
    public static boolean expanded = false;

    public static int color = 0xFF3B82F6; // Accent blue by default
    public static float fillOpacity = 0.20f; // 0.0 to 1.0
    public static float lineWidth = 2.0f; // 1.0 to 5.0
    public static int targetMode = 0; // 0 = All, 1 = Players, 2 = Mobs
    public static boolean showEyeHeight = true;
    public static boolean showViewVector = true;

    private static boolean wasKeyBindPressed = false;

    public static void onTick(MinecraftClient client) {
        boolean pressed = keyBind != -1 && client.getWindow() != null && client.currentScreen == null
                && InputUtil.isKeyPressed(client.getWindow(), keyBind);
        if (pressed && !wasKeyBindPressed) {
            enabled = !enabled;
            BameClientConfig.save();
        }
        wasKeyBindPressed = pressed;
    }

    public static void render(Frustum frustum, float tickDelta) {
        if (!enabled) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;

        for (Entity entity : client.world.getEntities()) {
            if (entity.isInvisible()) continue;
            if (entity == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()) continue;
            if (frustum != null && !frustum.isVisible(entity.getBoundingBox())) continue;

            // Target filtering
            if (targetMode == 1 && !(entity instanceof PlayerEntity)) continue;
            if (targetMode == 2 && (!(entity instanceof LivingEntity) || entity instanceof PlayerEntity)) continue;

            Vec3d pos = entity.getEntityPos();
            Vec3d lerped = entity.getLerpedPos(tickDelta);
            Vec3d offset = lerped.subtract(pos);
            Box box = entity.getBoundingBox().offset(offset);

            int strokeColor = 0xFF000000 | (color & 0xFFFFFF);
            int alphaInt = Math.round(fillOpacity * 255.0f);
            int fillColor = (alphaInt << 24) | (color & 0xFFFFFF);

            DrawStyle style;
            if (alphaInt > 0) {
                style = DrawStyle.filledAndStroked(fillColor, lineWidth, strokeColor);
            } else {
                style = DrawStyle.stroked(strokeColor, lineWidth);
            }

            GizmoDrawing.box(box, style);

            // EnderDragon body parts
            if (entity instanceof EnderDragonEntity dragon) {
                for (EnderDragonPart part : dragon.getBodyParts()) {
                    Vec3d partPos = part.getEntityPos();
                    Vec3d partLerped = part.getLerpedPos(tickDelta);
                    Vec3d partOffset = partLerped.subtract(partPos);
                    Box partBox = part.getBoundingBox().offset(partOffset);
                    GizmoDrawing.box(partBox, style);
                }
            }

            // Eye height line
            if (showEyeHeight && entity instanceof LivingEntity) {
                double eyeY = box.minY + entity.getStandingEyeHeight();
                Box eyeBox = new Box(box.minX, eyeY - 0.01, box.minZ, box.maxX, eyeY + 0.01, box.maxZ);
                GizmoDrawing.box(eyeBox, DrawStyle.stroked(0xFFFF0000, lineWidth));
            }

            // View direction arrow
            if (showViewVector) {
                Vec3d eyeVec = lerped.add(0.0, entity.getStandingEyeHeight(), 0.0);
                Vec3d lookVec = entity.getRotationVec(tickDelta).multiply(2.0).add(eyeVec);
                GizmoDrawing.arrow(eyeVec, lookVec, 0xFF0000FF, lineWidth);
            }
        }
    }

    public static void resetToDefault() {
        color = 0xFF3B82F6;
        fillOpacity = 0.20f;
        lineWidth = 2.0f;
        targetMode = 0;
        showEyeHeight = true;
        showViewVector = true;
    }
}
