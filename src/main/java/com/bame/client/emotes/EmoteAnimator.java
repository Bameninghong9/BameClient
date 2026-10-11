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
            if (client.options != null && client.options.getPerspective().isFirstPerson()) {
                model.head.visible = false;
                model.hat.visible = false;
            } else {
                model.head.visible = true;
                model.hat.visible = true;
            }
        } else {
            model.head.visible = true;
            model.hat.visible = true;
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
            case BASKETBALL -> {
                KeyframeAnimation anim = EmoteAnimationLoader.getBasketballAnimation();
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
            case GUITAR -> {
                resetAllParts(model);
                float loopT = t % 7.0f;

                // Rock stance (legs spread for balance)
                model.rightLeg.yaw = (float) Math.toRadians(12);
                model.leftLeg.yaw = (float) Math.toRadians(-12);
                model.rightLeg.pitch = (float) Math.toRadians(8);
                model.leftLeg.pitch = (float) Math.toRadians(-8);

                if (loopT < 3.8f) {
                    // Phase 1: Heavy Metal Standing Shred & Headbang
                    float headbang = (float) Math.sin(loopT * 10.0) * 0.32f;
                    model.head.pitch = headbang + 0.12f;
                    model.body.pitch = headbang * 0.25f;

                    // Left hand gripping guitar neck (angled up and across chest)
                    model.leftArm.pitch = (float) Math.toRadians(-62) + (float) Math.sin(loopT * 4.0) * 0.08f;
                    model.leftArm.yaw = (float) Math.toRadians(38);
                    model.leftArm.roll = (float) Math.toRadians(-24);

                    // Right hand strumming / picking over guitar body
                    float strum = (float) Math.sin(loopT * 26.0) * 0.18f;
                    model.rightArm.pitch = (float) Math.toRadians(-48);
                    model.rightArm.yaw = (float) Math.toRadians(-22);
                    model.rightArm.roll = (float) Math.toRadians(20) + strum;
                } else if (loopT < 5.8f) {
                    // Phase 2: Epic Knee Slide! (All body parts drop down synchronously)
                    float slideP = (loopT - 3.8f) / 2.0f;
                    float slideFactor = (float) Math.sin(slideP * Math.PI); // 0 -> 1 -> 0

                    float dropY = slideFactor * 8.5f;
                    model.body.originY += dropY;
                    model.head.originY += dropY;
                    model.rightArm.originY += dropY;
                    model.leftArm.originY += dropY;
                    model.rightLeg.originY += dropY;
                    model.leftLeg.originY += dropY;

                    // Fold legs back for knee slide
                    model.rightLeg.pitch = (float) Math.toRadians(78) * slideFactor;
                    model.leftLeg.pitch = (float) Math.toRadians(78) * slideFactor;
                    model.rightLeg.yaw = (float) Math.toRadians(8);
                    model.leftLeg.yaw = (float) Math.toRadians(-8);

                    // Body arched back, head tilted to the sky
                    model.body.pitch = (float) Math.toRadians(-32) * slideFactor;
                    model.head.pitch = (float) Math.toRadians(-45) * slideFactor;

                    // Continuous shredding during slide
                    model.leftArm.pitch = (float) Math.toRadians(-70);
                    model.leftArm.yaw = (float) Math.toRadians(42);
                    model.leftArm.roll = (float) Math.toRadians(-26);

                    float strum = (float) Math.sin(loopT * 28.0) * 0.22f;
                    model.rightArm.pitch = (float) Math.toRadians(-46);
                    model.rightArm.yaw = (float) Math.toRadians(-26);
                    model.rightArm.roll = (float) Math.toRadians(22) + strum;
                } else {
                    // Phase 3: Windmill Strum Finish & Power Chord!
                    float windP = (loopT - 5.8f) / 1.2f;
                    model.head.pitch = (float) Math.toRadians(-12);
                    model.body.pitch = (float) Math.toRadians(6);

                    // Left arm firmly holds neck
                    model.leftArm.pitch = (float) Math.toRadians(-62);
                    model.leftArm.yaw = (float) Math.toRadians(38);
                    model.leftArm.roll = (float) Math.toRadians(-24);

                    if (windP < 0.65f) {
                        // 360-degree windmill swing on right arm
                        float angle = windP / 0.65f * (float) (Math.PI * 2.0);
                        model.rightArm.pitch = (float) (-Math.cos(angle) * 2.4f - 0.4f);
                        model.rightArm.roll = (float) (Math.sin(angle) * 0.6f + 0.2f);
                        model.rightArm.yaw = (float) Math.toRadians(-18);
                    } else {
                        // Power chord sustain hold
                        model.rightArm.pitch = (float) Math.toRadians(-52);
                        model.rightArm.yaw = (float) Math.toRadians(-20);
                        model.rightArm.roll = (float) Math.toRadians(24);
                    }
                }
            }
            case COIN_FLIP -> {
                // Münzwurf (one-shot up to 3.8s)
                float loopT = Math.min(t, 3.8f);
                if (loopT < 0.4f) {
                    // Thumb flick
                    float fp = loopT / 0.4f;
                    model.rightArm.pitch = -0.75f - fp * 0.35f;
                    model.rightArm.yaw = -0.15f;
                    model.rightArm.roll = 0.20f;
                    model.leftArm.pitch = 0.0f;
                    model.head.pitch = 0.0f;
                } else if (loopT < 2.0f) {
                    // Coin in air: head tilts back watching flight
                    float flightP = (loopT - 0.4f) / 1.6f;
                    model.head.pitch = -0.55f * (float) Math.sin(flightP * Math.PI);

                    model.leftArm.pitch = -0.80f;
                    model.leftArm.yaw = 0.35f;
                    model.leftArm.roll = -0.15f;

                    model.rightArm.pitch = -0.60f;
                    model.rightArm.yaw = -0.10f;
                    model.rightArm.roll = 0.15f;
                } else if (loopT < 2.5f) {
                    // Catch on back of hand & slap
                    model.leftArm.pitch = -0.82f;
                    model.leftArm.yaw = 0.32f;
                    model.leftArm.roll = -0.15f;

                    model.rightArm.pitch = -0.85f;
                    model.rightArm.yaw = 0.28f;
                    model.rightArm.roll = 0.45f;
                    model.head.pitch = 0.15f;
                } else {
                    // Peek at coin
                    model.leftArm.pitch = -0.82f;
                    model.leftArm.yaw = 0.32f;
                    model.leftArm.roll = -0.15f;

                    model.rightArm.pitch = -0.82f;
                    model.rightArm.yaw = 0.12f;
                    model.rightArm.roll = 0.35f;

                    model.head.pitch = 0.38f + (float) Math.sin((loopT - 2.5f) * 6.0) * 0.06f;
                }

                if (isPreview) {
                    model.rightLeg.pitch = 0.0f;
                    model.leftLeg.pitch = 0.0f;
                }
            }
            case WHEELCHAIR -> {
                resetAllParts(model);

                // Sit cleanly on top of the elevated wheelchair seat cushion (matching Bild 2)
                float seatOffsetY = 1.0f;
                float seatOffsetZ = 1.5f;
                model.body.originY += seatOffsetY;
                model.head.originY += seatOffsetY;
                model.rightArm.originY += seatOffsetY;
                model.leftArm.originY += seatOffsetY;
                model.rightLeg.originY += seatOffsetY;
                model.leftLeg.originY += seatOffsetY;

                model.body.originZ += seatOffsetZ;
                model.head.originZ += seatOffsetZ;
                model.rightArm.originZ += seatOffsetZ;
                model.leftArm.originZ += seatOffsetZ;
                model.rightLeg.originZ += seatOffsetZ;
                model.leftLeg.originZ += seatOffsetZ;

                // Legs forward and bent comfortably, resting right on the elevated footplates
                model.rightLeg.pitch = (float) Math.toRadians(-74);
                model.leftLeg.pitch = (float) Math.toRadians(-74);
                model.rightLeg.yaw = (float) Math.toRadians(5);
                model.leftLeg.yaw = (float) Math.toRadians(-5);

                // Movement driving vs resting
                float limbAmp = state != null ? state.limbSwingAmplitude : 0.0f;
                float limbProg = state != null ? state.limbSwingAnimationProgress : 0.0f;
                boolean driving = isMoving || (limbAmp > 0.04f);

                if (driving) {
                    // Wheelchair push stroke (greifen, drücken, ausholen) - smooth & calm pace
                    float pushCycle = limbProg * 0.75f;
                    float pushSin = (float) Math.sin(pushCycle);
                    float pushCos = (float) Math.cos(pushCycle);

                    // Arms push down and forward along the handrims
                    model.rightArm.pitch = (float) Math.toRadians(-18) + pushSin * 0.28f;
                    model.leftArm.pitch = (float) Math.toRadians(-18) + pushSin * 0.28f;

                    model.rightArm.yaw = (float) Math.toRadians(-12) - pushCos * 0.06f;
                    model.leftArm.yaw = (float) Math.toRadians(12) + pushCos * 0.06f;

                    model.rightArm.roll = (float) Math.toRadians(14);
                    model.leftArm.roll = (float) Math.toRadians(-14);

                    // Dynamic athletic body lean during push strokes
                    float bodyLean = Math.max(0.0f, pushSin) * 0.10f;
                    model.body.pitch = 0.05f + bodyLean;
                    model.head.pitch = -0.03f + bodyLean * 0.4f;
                } else {
                    // Relaxed sitting pose, hands resting on the top of the Greifreifen
                    float breath = (float) (Math.sin(t * 2.5) * 0.02f);
                    model.body.pitch = breath;
                    model.head.pitch = -breath * 0.5f;

                    model.rightArm.pitch = (float) Math.toRadians(-16) + breath * 0.3f;
                    model.leftArm.pitch = (float) Math.toRadians(-16) + breath * 0.3f;

                    model.rightArm.yaw = (float) Math.toRadians(-12);
                    model.leftArm.yaw = (float) Math.toRadians(12);

                    model.rightArm.roll = (float) Math.toRadians(12);
                    model.leftArm.roll = (float) Math.toRadians(-12);
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
        model.head.visible = true;
        model.hat.visible = true;
        model.head.resetTransform();
        model.body.resetTransform();
        model.rightArm.resetTransform();
        model.leftArm.resetTransform();
        model.rightLeg.resetTransform();
        model.leftLeg.resetTransform();

        // In Minecraft 1.17+, outer layers (sleeves, pants, jacket, hat) are children of their respective
        // parent body parts. Reset their local transforms to ensure zero displacement!
        if (model.leftSleeve != null) model.leftSleeve.resetTransform();
        if (model.rightSleeve != null) model.rightSleeve.resetTransform();
        if (model.leftPants != null) model.leftPants.resetTransform();
        if (model.rightPants != null) model.rightPants.resetTransform();
        if (model.jacket != null) model.jacket.resetTransform();
        if (model.hat != null) model.hat.resetTransform();
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
        if (model == null) return;
        model.hat.visible = model.head.visible;
        // In modern Minecraft, sleeves, pants, jacket and hat are already children of their parent
        // parts (leftArm, rightArm, body, etc.). They inherit the parent's transforms automatically!
        // Never copy parent origins onto children or they get doubled and displaced into space.
        if (model.leftSleeve != null) model.leftSleeve.resetTransform();
        if (model.rightSleeve != null) model.rightSleeve.resetTransform();
        if (model.leftPants != null) model.leftPants.resetTransform();
        if (model.rightPants != null) model.rightPants.resetTransform();
        if (model.jacket != null) model.jacket.resetTransform();
        if (model.hat != null) model.hat.resetTransform();
    }
}
