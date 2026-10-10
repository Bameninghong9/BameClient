package com.bame.client.emotes;

import com.bame.client.module.EmoteModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

public class EmoteAnimator {

    private static boolean wasEmoting = false;

    public static void apply(PlayerEntityModel model, PlayerEntityRenderState state) {
        if (!EmoteModule.enabled || model == null || state == null) return;

        // Check if this render call is a GUI preview
        if (state instanceof EmotePreviewHolder holder && holder.bame$getPreviewEmote() != null) {
            EmoteType previewEmote = holder.bame$getPreviewEmote();
            float previewTime = holder.bame$getPreviewTime();
            applyEmotePose(model, previewEmote, previewTime, true, state);
            syncLayers(model);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        boolean isClientPlayer = client.player != null && client.player.getId() == state.id;

        // Apply active emote for the local player
        if (isClientPlayer && EmoteModule.isEmoting()) {
            wasEmoting = true;
            EmoteType emote = EmoteModule.getCurrentEmote();
            float t = EmoteModule.getElapsedSeconds();
            applyEmotePose(model, emote, t, false, state);
        } else {
            if (wasEmoting) {
                wasEmoting = false;
                resetAllParts(model);
            }
            if (EmoteModule.smoothAnimations) {
            // Smooth Natural Animations (Laufen & Idle & Schlagen)
            float limbIntensity = state.limbSwingAmplitude;
            float limbProgress = state.limbSwingAnimationProgress;

            if (limbIntensity > 0.02f) {
                // Subtle natural body sway when walking
                float sway = (float) (Math.sin(limbProgress) * 0.045f * Math.min(limbIntensity, 1.0f));
                model.body.roll += sway;
                model.head.roll -= sway * 0.4f;

                // Subtle sprint lean forward
                if (limbIntensity > 0.6f) {
                    model.body.pitch += 0.06f;
                }
            } else {
                // Subtle idle breathing
                float breath = (float) (Math.sin(state.age * 0.08) * 0.015);
                model.body.pitch += breath;
                model.rightArm.roll += breath * 0.4f;
                model.leftArm.roll -= breath * 0.4f;
            }

            // Smooth swing arc
            if (state.handSwingProgress > 0.0f) {
                float swing = state.handSwingProgress;
                model.rightArm.roll += (float) (Math.sin(swing * Math.PI) * 0.22f);
            }
        }
        }

        syncLayers(model);
    }

    public static void applyEmotePose(PlayerEntityModel model, EmoteType emote, float t, boolean isPreview, PlayerEntityRenderState state) {
        if (model == null || emote == null) return;

        boolean isMoving = state != null && state.limbSwingAmplitude > 0.02f;

        switch (emote) {
            case T_POSE -> {
                // Classic T-Pose: both arms horizontal to the sides
                model.rightArm.pitch = 0.0f;
                model.rightArm.yaw = 0.0f;
                model.rightArm.roll = (float) Math.toRadians(90);

                model.leftArm.pitch = 0.0f;
                model.leftArm.yaw = 0.0f;
                model.leftArm.roll = (float) Math.toRadians(-90);

                model.body.roll = 0.0f;
                model.head.roll = 0.0f;

                if (isPreview) {
                    model.head.pitch = 0.0f;
                    model.head.yaw = 0.0f;
                    model.body.pitch = 0.0f;
                    model.body.yaw = 0.0f;
                    model.rightLeg.pitch = 0.0f;
                    model.leftLeg.pitch = 0.0f;
                }
            }
            case JUGGLE -> {
                KeyframeAnimation anim = EmoteAnimationLoader.getJuggleAnimation();
                if (anim != null) {
                    float[] rot = new float[3];
                    float[] pos = new float[3];

                    resetAllParts(model);

                    anim.sample("bipedHead", t, rot, pos);
                    applyBone(model.head, rot, pos);

                    anim.sample("bipedBody", t, rot, pos);
                    applyBone(model.body, rot, pos);

                    anim.sample("bipedRightArm", t, rot, pos);
                    applyBone(model.rightArm, rot, pos);

                    anim.sample("bipedLeftArm", t, rot, pos);
                    applyBone(model.leftArm, rot, pos);

                    anim.sample("bipedRightLeg", t, rot, pos);
                    applyBone(model.rightLeg, rot, pos);

                    anim.sample("bipedLeftLeg", t, rot, pos);
                    applyBone(model.leftLeg, rot, pos);
                }
            }
            case WAVE -> {
                // Right arm raised up and waving
                model.rightArm.pitch = (float) Math.toRadians(-150);
                model.rightArm.yaw = (float) Math.toRadians(-15);
                model.rightArm.roll = (float) (Math.sin(t * 8.0) * 0.35 - 0.2);

                if (isPreview) {
                    model.head.roll = 0.12f;
                    model.head.pitch = -0.05f;
                    model.leftArm.pitch = 0.0f;
                    model.leftArm.roll = 0.0f;
                    model.rightLeg.pitch = 0.0f;
                    model.leftLeg.pitch = 0.0f;
                } else {
                    model.head.roll += 0.12f;
                }
            }
            case CLAP -> {
                // Both arms brought together and clapping
                model.rightArm.pitch = (float) Math.toRadians(-80);
                model.leftArm.pitch = (float) Math.toRadians(-80);
                float clap = (float) (Math.abs(Math.sin(t * 12.0)) * 0.28);
                model.rightArm.yaw = -0.42f - clap;
                model.leftArm.yaw = 0.42f + clap;
                model.rightArm.roll = 0.05f;
                model.leftArm.roll = -0.05f;

                if (isPreview) {
                    model.head.pitch = (float) (Math.sin(t * 12.0) * 0.05);
                    model.rightLeg.pitch = 0.0f;
                    model.leftLeg.pitch = 0.0f;
                } else {
                    model.head.pitch += (float) (Math.sin(t * 12.0) * 0.05);
                }
            }
            case HEADSPIN -> {
                // Humorous head tilt & swivel
                float headYaw = (float) (Math.sin(t * 7.0) * 0.85);
                float headRoll = (float) (Math.cos(t * 7.0) * 0.25);

                if (isPreview) {
                    model.head.yaw = headYaw;
                    model.head.roll = headRoll;
                    model.rightLeg.pitch = 0.0f;
                    model.leftLeg.pitch = 0.0f;
                } else {
                    model.head.yaw += headYaw;
                    model.head.roll += headRoll;
                }
            }
            case ZOMBIE -> {
                // Zombie arms straight forward
                model.rightArm.pitch = (float) Math.toRadians(-90);
                model.rightArm.yaw = (float) Math.toRadians(5);
                model.leftArm.pitch = (float) Math.toRadians(-90);
                model.leftArm.yaw = (float) Math.toRadians(-5);

                if (isPreview) {
                    model.rightLeg.pitch = 0.0f;
                    model.leftLeg.pitch = 0.0f;
                }
            }
            case DANCE -> {
                // Rhythmic groove
                float wave = (float) Math.sin(t * 6.0);
                float cosWave = (float) Math.cos(t * 6.0);

                model.body.yaw += wave * 0.22f;
                model.body.roll += cosWave * 0.08f;

                model.rightArm.pitch = (float) (Math.toRadians(-110) + wave * 0.45);
                model.leftArm.pitch = (float) (Math.toRadians(-110) - wave * 0.45);
                model.rightArm.roll = wave * 0.35f;
                model.leftArm.roll = -wave * 0.35f;

                model.head.roll += -wave * 0.15f;
                model.head.pitch += Math.abs(cosWave) * 0.12f;

                if (!isMoving || isPreview) {
                    model.body.originY += Math.abs(wave) * 1.5f;
                }
                if (isPreview) {
                    model.rightLeg.pitch = 0.0f;
                    model.leftLeg.pitch = 0.0f;
                }
            }
            case SIT -> {
                // Lower body & legs forward
                float yOffset = 8.5f;
                model.body.originY += yOffset;
                model.head.originY += yOffset;
                model.rightArm.originY += yOffset;
                model.leftArm.originY += yOffset;
                model.rightLeg.originY += yOffset;
                model.leftLeg.originY += yOffset;

                model.rightLeg.pitch = (float) Math.toRadians(-85);
                model.leftLeg.pitch = (float) Math.toRadians(-85);
                model.rightLeg.yaw = (float) Math.toRadians(12);
                model.leftLeg.yaw = (float) Math.toRadians(-12);

                model.rightArm.pitch = (float) Math.toRadians(-35);
                model.leftArm.pitch = (float) Math.toRadians(-35);
                model.rightArm.yaw = (float) Math.toRadians(-10);
                model.leftArm.yaw = (float) Math.toRadians(10);
            }
            default -> {}
        }
    }

    private static void resetAllParts(PlayerEntityModel model) {
        if (model == null) return;
        model.head.resetTransform();
        model.body.resetTransform();
        model.rightArm.resetTransform();
        model.leftArm.resetTransform();
        model.rightLeg.resetTransform();
        model.leftLeg.resetTransform();
    }

    private static void applyBone(ModelPart part, float[] rot, float[] pos) {
        if (part == null) return;
        if (rot != null) {
            part.pitch = (float) Math.toRadians(rot[0]);
            part.yaw = (float) Math.toRadians(rot[1]);
            part.roll = (float) Math.toRadians(rot[2]);
        }
        if (pos != null) {
            part.originX += pos[0];
            part.originY -= pos[1];
            part.originZ += pos[2];
        }
    }

    private static void syncLayers(PlayerEntityModel model) {
        copy(model.leftArm, model.leftSleeve);
        copy(model.rightArm, model.rightSleeve);
        copy(model.leftLeg, model.leftPants);
        copy(model.rightLeg, model.rightPants);
        copy(model.body, model.jacket);
        copy(model.head, model.hat);
    }

    private static void copy(ModelPart src, ModelPart dst) {
        if (src == null || dst == null) return;
        dst.pitch = src.pitch;
        dst.yaw = src.yaw;
        dst.roll = src.roll;
        dst.originX = src.originX;
        dst.originY = src.originY;
        dst.originZ = src.originZ;
    }
}
