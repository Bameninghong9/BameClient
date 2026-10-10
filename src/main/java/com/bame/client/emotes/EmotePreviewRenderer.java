package com.bame.client.emotes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class EmotePreviewRenderer {

    public static void drawPlayerPreview(
            DrawContext context,
            int x1, int y1, int x2, int y2,
            int size,
            float lookAtX, float lookAtY,
            LivingEntity entity,
            EmoteType emote,
            float animTime
    ) {
        if (entity == null || context == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderManager dispatcher = client.getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
        if (renderer == null) return;

        EntityRenderState renderState = renderer.getAndUpdateRenderState(entity, 1.0F);
        if (renderState == null) return;

        float cx = (float)(x1 + x2) / 2.0F;
        float cy = (float)(y1 + y2) / 2.0F;
        float yawDiff = (float) Math.atan((cx - lookAtX) / 40.0F);
        float pitchDiff = (float) Math.atan((cy - lookAtY) / 40.0F);

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf pitchQuat = new Quaternionf().rotateX(pitchDiff * 20.0F * 0.017453292F);
        rotation.mul(pitchQuat);

        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyYaw = 180.0F + yawDiff * 20.0F;
            livingState.relativeHeadYaw = yawDiff * 20.0F;
            livingState.pitch = -pitchDiff * 20.0F;
            if (livingState.baseScale > 0.0F) {
                livingState.width /= livingState.baseScale;
                livingState.height /= livingState.baseScale;
            }
            livingState.baseScale = 1.0F;
            livingState.limbSwingAmplitude = 0.0F;
            livingState.limbSwingAnimationProgress = 0.0F;
        }

        if (renderState instanceof net.minecraft.client.render.entity.state.ArmedEntityRenderState armedState) {
            armedState.handSwingProgress = 0.0F;
        }

        if (renderState instanceof EmotePreviewHolder holder) {
            holder.bame$setPreviewEmote(emote);
            holder.bame$setPreviewTime(animTime);
        }

        renderState.light = 0x00F000F0;
        if (renderState.shadowPieces != null) {
            renderState.shadowPieces.clear();
        }
        renderState.outlineColor = 0;

        Vector3f translation = new Vector3f(0.0F, renderState.height / 2.0F + 0.0625F, 0.0F);
        context.addEntity(renderState, (float) size, translation, rotation, null, x1, y1, x2, y2);
    }
}
