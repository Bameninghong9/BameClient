package com.bame.client.emotes;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.module.EmoteModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;

import java.util.UUID;

public class EmoteAnimator {

    private static boolean wasEmoting = false;

    public static String getPlayerUsername(PlayerEntityRenderState state) {
        if (state == null) return null;
        if (state instanceof EmotePreviewHolder holder && holder.caeser$getPlayerUsername() != null) {
            return holder.caeser$getPlayerUsername();
        }
        if (state.playerName != null) {
            return state.playerName.getString();
        }
        return null;
    }

    /**
     * Aktives Emote eines gerenderten Spielers: GUI-Vorschau, lokaler Spieler ({@link EmoteModule}) oder fremder
     * Caeser-Spieler ({@link CaeserPlayerSyncManager#getActiveEmote(PlayerEntityRenderState)}).
     *
     * @param startMs Startzeit in lokaler Zeit (0 in der Vorschau)
     */
    public record ActiveEmote(EmoteType emote, float t, long startMs, boolean preview, boolean local) {}

    /** Welches Emote für diesen Render-State gerade läuft und seit wann; {@code null} ohne Emote. */
    public static ActiveEmote resolveActiveEmote(PlayerEntityRenderState state) {
        if (!EmoteModule.enabled || state == null) return null;
        if (state instanceof EmotePreviewHolder holder && holder.bame$getPreviewEmote() != null) {
            return new ActiveEmote(holder.bame$getPreviewEmote(), holder.bame$getPreviewTime(), 0L, true, false);
        }
        if (CaeserPlayerSyncManager.isLocalPlayer(state)) return localActiveEmote();
        // Lookup über die UUID, nur besessene Emotes: vom Sync-Server bereinigt bzw. im LAN gegen den OwnershipCache geprüft
        return remoteActiveEmote(CaeserPlayerSyncManager.getActiveEmote(state));
    }

    /** Wie {@link #resolveActiveEmote(PlayerEntityRenderState)}, für eine Spieler-Entity (Tick-Logik). */
    public static ActiveEmote resolveActiveEmote(UUID uuid, boolean isLocal) {
        if (!EmoteModule.enabled) return null;
        if (isLocal) return localActiveEmote();
        return uuid != null ? remoteActiveEmote(CaeserPlayerSyncManager.getActiveEmote(uuid)) : null;
    }

    private static ActiveEmote localActiveEmote() {
        if (!EmoteModule.isEmoting()) return null;
        return new ActiveEmote(EmoteModule.getCurrentEmote(), EmoteModule.getElapsedSeconds(),
                EmoteModule.getStartTimeMillis(), false, true);
    }

    private static ActiveEmote remoteActiveEmote(CaeserPlayerSyncManager.PlayerEmoteData data) {
        if (data == null || data.isExpired()) return null;
        // Beendetes High-Five/Einschlagen nicht weiter zeigen, auch wenn das STOP noch unterwegs ist
        if ((data.emote == EmoteType.HIGH_FIVE || data.emote == EmoteType.HIGH_FIVE_ACCEPT)
                && HighFiveCoordinator.isFinished(data.uuid, data.startTime)) return null;
        return new ActiveEmote(data.emote, data.getElapsedSeconds(), data.startTime, false, false);
    }

    public static void apply(PlayerEntityModel model, PlayerEntityRenderState state) {
        if (!EmoteModule.enabled || model == null || state == null) return;

        ActiveEmote active = resolveActiveEmote(state);

        // Check if this render call is a GUI preview
        if (active != null && active.preview()) {
            resetAllParts(model);
            applyEmotePose(model, active.emote(), active.t(), true, state);
            syncLayers(model);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        // Apply active emote for the local player OR remote Caeser Client player
        if (active != null && active.local()) {
            resetAllParts(model);
            wasEmoting = true;
            applyEmotePose(model, active.emote(), active.t(), false, state);
            // Ego-Perspektive: Kopf ausblenden (Kamera steckt darin), außer bei der Liegestütz-Kamera von hinten
            if (client.options != null && client.options.getPerspective().isFirstPerson()
                    && !EmoteCamera.showsOwnHead()) {
                model.head.visible = false;
                model.hat.visible = false;
            } else {
                model.head.visible = true;
                model.hat.visible = true;
            }
        } else if (active != null) {
            resetAllParts(model);
            applyEmotePose(model, active.emote(), active.t(), false, state);
            model.head.visible = true;
            model.hat.visible = true;
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
            // Auslaufen des fliegenden Teppichs: der Reiter sitzt noch, bis der Teppich gelandet ist, dann steht er auf
            applyFlyingCarpetOutro(model, state);
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
            case BUTTERFLY -> {
                // Balisong-Routine (Valorant/CS-Stil; Zeitleiste in ButterflyKnifeAnimation, Messer im Prop-Renderer)
                ButterflyKnifeAnimation.Pose pose = new ButterflyKnifeAnimation.Pose();
                ButterflyKnifeAnimation.sample(t, pose);

                model.rightArm.pitch = pose.rightArmPitch;
                model.rightArm.yaw = pose.rightArmYaw;
                model.rightArm.roll = pose.rightArmRoll;

                model.leftArm.pitch = pose.leftArmPitch;
                model.leftArm.yaw = pose.leftArmYaw;
                model.leftArm.roll = pose.leftArmRoll;

                model.head.pitch = pose.headPitch;
                model.head.yaw = pose.headYaw;
                model.head.roll = pose.headRoll;
                model.body.pitch = pose.bodyPitch;
                model.body.yaw = pose.bodyYaw;
                model.body.roll = pose.bodyRoll;

                // Beim Gehen Beine und linken Arm wie Vanilla mitschwingen lassen (resetAllParts setzt sie sonst starr)
                if (!isPreview && isMoving) {
                    float limbAmp = Math.min(state.limbSwingAmplitude, 1.0f);
                    float limbProg = state.limbSwingAnimationProgress;
                    float swing = (float) Math.cos(limbProg * 0.6662f);
                    model.rightLeg.pitch = swing * 1.4f * limbAmp * 0.5f;
                    model.leftLeg.pitch = -swing * 1.4f * limbAmp * 0.5f;
                    model.leftArm.pitch += swing * limbAmp * 0.5f;
                }
            }
            case PUSH_UPS -> {
                // Plank + Liegestütze; die Kippung des ganzen Körpers kommt aus applyBodyTilt (Render-Matrix)
                PushUpMotion.Pose pose = new PushUpMotion.Pose();
                PushUpMotion.sample(t, pose);

                model.rightArm.pitch = pose.rightArmPitch;
                model.rightArm.yaw = pose.rightArmYaw;
                model.leftArm.pitch = pose.leftArmPitch;
                model.leftArm.yaw = pose.leftArmYaw;
                // Unten kürzere Arme: deutet gebeugte Ellbogen an, die Hände bleiben am Boden
                model.rightArm.yScale = pose.armScale;
                model.leftArm.yScale = pose.armScale;

                // Kopf leicht angehoben, Blick nach vorne; Rumpf und Beine gerade und geschlossen
                model.head.pitch = pose.headPitch;
                model.head.yaw = 0.0F;
            }
            case HIGH_FIVE -> applyHighFive(model, t, isPreview, state, false);
            case HIGH_FIVE_ACCEPT -> applyHighFive(model, t, isPreview, state, !isPreview);
            case CAMPFIRE -> applyCampfire(model, t, isPreview, state);
            case HAMMOCK -> applyHammock(model, t, isPreview, state);
            case SWING -> applySwing(model, t, isPreview, state);
            case PICNIC -> applyPicnic(model, t, isPreview, state);
            case WITCH_BROOM -> applyWitchBroom(model, t, isPreview, state);
            case FLYING_CARPET -> applyFlyingCarpet(model, t, isPreview, state);
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

    // ------------------------------------------------------------------
    // Liegestütze: Kippung des ganzen Körpers
    // ------------------------------------------------------------------

    /**
     * Aufgerufen am Ende von {@code PlayerEntityRenderer.setupTransforms} (Mixin): Bei Liegestützen wird der ganze
     * Spieler um die Fußspitzen nach vorne gekippt (Gesicht nach unten). Modell, Cosmetics und Props folgen, weil sie in
     * derselben Matrix gezeichnet werden; der Nametag wird erst nach dem {@code pop()} gezeichnet und bleibt aufrecht.
     */
    public static void applyBodyTilt(PlayerEntityRenderState state, MatrixStack matrices) {
        if (state == null || matrices == null) return;
        ActiveEmote active = resolveActiveEmote(state);
        if (active != null && active.emote() == EmoteType.CAMPFIRE) {
            applyCampfireYaw(state, matrices, active);
            return;
        }
        if (active != null && active.emote() == EmoteType.HAMMOCK) {
            applyHammockTransform(state, matrices, active);
            return;
        }
        if (active != null && active.emote() == EmoteType.SWING) {
            applySwingTransform(state, matrices, active);
            return;
        }
        if (active != null && active.emote() == EmoteType.PICNIC) {
            applyPicnicTransform(state, matrices, active);
            return;
        }
        if (active != null && active.emote() == EmoteType.WITCH_BROOM) {
            applyWitchBroomTransform(state, matrices, active);
            return;
        }
        if (active != null && active.emote() == EmoteType.FLYING_CARPET) {
            applyFlyingCarpetTransform(state, matrices, active);
            return;
        }
        if (active == null) {
            // Auslaufen des fliegenden Teppichs: Reiter und Teppich sinken noch weich zu Boden
            applyFlyingCarpetOutroTransform(state, matrices);
            return;
        }
        if (active.emote() != EmoteType.PUSH_UPS) return;
        // Vanilla-Posen mit eigener Drehung nicht überlagern
        if (state.isGliding || state.leaningPitch > 0.0F || state.usingRiptide || state.deathTime > 0.0F
                || state.hasVehicle || state.isInPose(EntityPose.SLEEPING)) {
            return;
        }

        PushUpMotion.Pose pose = new PushUpMotion.Pose();
        PushUpMotion.sample(active.t(), pose);
        if (pose.tiltDeg <= 0.01F) return;

        if (active.preview()) {
            // Vorschau: schräg von der Seite zeigen und den liegenden Körper in die Mitte des Rahmens schieben
            float b = pose.blend;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(65.0F * b));
            matrices.translate(0.0F, 0.55F * b, 0.85F * b);
        }

        // Drehpunkt: Fußspitzen am Boden (vorne = -Z)
        float toeZ = -PushUpMotion.TOE_FWD_PX * PushUpMotion.BLOCKS_PER_PX;
        matrices.translate(0.0F, 0.0F, toeZ);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pose.tiltDeg));
        matrices.translate(0.0F, 0.0F, -toeZ);
    }

    // ------------------------------------------------------------------
    // Lagerfeuer
    // ------------------------------------------------------------------

    /** Vorschau: so weit gedreht (Grad), dass Stock und Feuer seitlich zu sehen sind; Verschiebung und Verkleinerung. */
    private static final float CAMPFIRE_PREVIEW_YAW = 65.0F;
    private static final float CAMPFIRE_PREVIEW_BACK = 0.6F;
    private static final float CAMPFIRE_PREVIEW_SCALE = 0.78F;

    private static final CampfireMotion.Pose CF_POSE = new CampfireMotion.Pose();
    private static final CampfireMotion.Stick CF_STICK = new CampfireMotion.Stick();
    private static final double[] CF_FIRE = new double[3];
    private static final double[] CF_TARGET = new double[3];
    private static final float[] CF_ANGLES = new float[2];

    /**
     * Feste Ausrichtung beim Lagerfeuer (am Ende von {@code setupTransforms}): Der Spieler wird mit dem beim Start
     * gemerkten Yaw gerendert, nicht mit dem aktuellen Körper-Yaw. Stamm und Feuer (im selben Modellraum gezeichnet)
     * bleiben dadurch fest an ihrem Platz, auch wenn sich der Körper beim Umschauen mitdreht. In der Vorschau: schräg
     * von der Seite, Spieler und Feuer im Rahmen.
     */
    private static void applyCampfireYaw(PlayerEntityRenderState state, MatrixStack matrices, ActiveEmote active) {
        if (state.isGliding || state.leaningPitch > 0.0F || state.usingRiptide || state.deathTime > 0.0F
                || state.hasVehicle || state.isInPose(EntityPose.SLEEPING)) {
            return;
        }
        if (active.preview()) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(CAMPFIRE_PREVIEW_YAW));
            matrices.translate(0.0F, 0.0F, CAMPFIRE_PREVIEW_BACK);
            matrices.scale(CAMPFIRE_PREVIEW_SCALE, CAMPFIRE_PREVIEW_SCALE, CAMPFIRE_PREVIEW_SCALE);
            return;
        }
        CampfireCoordinator.Frame frame = CampfireCoordinator.frame(state, active);
        if (frame == null || Math.abs(frame.counterYaw) < 1.0E-3F) return;
        // setupTransforms hat um (180 - bodyYaw) gedreht; zusätzlich (bodyYaw - renderYaw) ergibt (180 - renderYaw)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(frame.counterYaw));
    }

    /**
     * Heller Licht-Wert für Spieler mit Lagerfeuer (aus dem Renderer-Mixin nach {@code updateRenderState}): Das Feuer
     * strahlt Spieler, Stamm und Stock an. Nur für Spieler mit CAMPFIRE, andere Spieler und Emotes bleiben unverändert.
     */
    public static void applyCampfireLight(PlayerEntityRenderState state) {
        if (!EmoteModule.enabled || state == null) return;
        ActiveEmote active = resolveActiveEmote(state);
        if (active == null || active.emote() != EmoteType.CAMPFIRE || active.preview()) return;
        float strength = CampfireMotion.pass(active.t()) == 0
                ? CampfireMotion.easeInOut(active.t() / CampfireMotion.INTRO) : 1.0F;
        state.light = CampfireCoordinator.fireLight(state.light, strength, state.id * 7);
    }

    /**
     * Lagerfeuer: auf dem Stamm sitzen (Versatz wie WHEELCHAIR), rechter Arm hält den Stock zum Feuer, linker Arm auf
     * dem Knie, Kopf schaut zum Marshmallow (beim Umschauen folgt er dem Blick). Zeitleiste in {@link CampfireMotion},
     * Stock, Stamm und Feuer im Prop-Renderer.
     */
    private static void applyCampfire(PlayerEntityModel model, float t, boolean isPreview, PlayerEntityRenderState state) {
        ActiveEmote active = state != null ? resolveActiveEmote(state) : null;
        CampfireCoordinator.Frame frame = active != null ? CampfireCoordinator.frame(state, active) : null;
        double fireDistance;
        float counterYaw = 0.0F;
        if (frame != null) {
            CF_FIRE[0] = frame.fireTarget[0];
            CF_FIRE[1] = frame.fireTarget[1];
            CF_FIRE[2] = frame.fireTarget[2];
            fireDistance = frame.fireDistance;
            counterYaw = frame.counterYaw;
        } else {
            CampfireMotion.defaultFireTarget(CF_FIRE);
            fireDistance = CampfireLogic.FIRE_DISTANCE;
        }
        CampfireMotion.Pose p = CF_POSE;
        CampfireMotion.sample(t, CampfireCoordinator.burns(frame, t), CampfireMotion.farness(fireDistance), p);

        model.body.originY += p.bodyDY;
        model.body.originZ += p.bodyDZ;
        model.head.originY += p.bodyDY;
        model.head.originZ += p.bodyDZ;
        model.rightArm.originY += p.armDY;
        model.rightArm.originZ += p.bodyDZ;
        model.leftArm.originY += p.armDY;
        model.leftArm.originZ += p.bodyDZ;
        model.rightLeg.originY += p.legDY;
        model.rightLeg.originZ += p.legDZ;
        model.leftLeg.originY += p.legDY;
        model.leftLeg.originZ += p.legDZ;

        model.body.pitch = p.lean;
        model.rightLeg.pitch = p.legPitch;
        model.leftLeg.pitch = p.legPitch;
        model.rightLeg.yaw = p.legYaw;
        model.leftLeg.yaw = -p.legYaw;

        model.rightArm.pitch = p.rArmPitch;
        model.rightArm.yaw = p.rArmYaw;
        model.rightArm.roll = p.rArmRoll;

        // Stock zum Ziel ausrichten (gleiche Rechnung wie im Renderer), daraus Marshmallow für Kopf und linke Hand
        boolean slim = model.rightArm.getDefaultTransform().y() > 2.25F;
        CampfireMotion.stickTarget(p, CF_FIRE, CF_TARGET);
        CampfireMotion.aimStick(model.rightArm.originX, model.rightArm.originY, model.rightArm.originZ,
                model.rightArm.pitch, model.rightArm.yaw, model.rightArm.roll,
                slim ? CampfireMotion.GRIP_X_SLIM : CampfireMotion.GRIP_X_WIDE,
                CF_TARGET[0], CF_TARGET[1], CF_TARGET[2], CF_STICK);

        CampfireMotion.leftArm(p, model.leftArm.originX, model.leftArm.originY, model.leftArm.originZ, CF_STICK.mallow,
                CF_ANGLES);
        model.leftArm.pitch = CF_ANGLES[0];
        model.leftArm.yaw = CF_ANGLES[1];
        model.leftArm.roll = p.lArmRoll;

        // Kopf: zum Marshmallow; schaut der Spieler deutlich woanders hin, folgt der Kopf dem Blick
        CampfireMotion.lookAt(model.head.originX, model.head.originY, model.head.originZ, CF_STICK.mallow, CF_ANGLES);
        float lookPitch = 0.0F, lookYaw = 0.0F;
        float follow = 1.0F - p.sit;
        // Schaut der Spieler nach hinten, kehrt der Kopf weich zurück (kein Umspringen von +75° auf -75° bei ±180°)
        float back = 1.0F;
        if (!isPreview && state != null) {
            lookYaw = (float) HighFiveLogic.wrapDegrees(state.relativeHeadYaw + counterYaw);
            lookPitch = state.pitch;
            back = HammockMotion.lookBackFade(lookYaw);
            float off = Math.max(Math.abs(lookYaw) - 20.0F, 0.0F) / 30.0F;
            float up = Math.max(-lookPitch - 25.0F, 0.0F) / 25.0F;
            follow = Math.max(follow, CampfireMotion.easeInOut(Math.max(off, up)) * back);
        }
        float headPitch = lerp(CF_ANGLES[0], clampF(lookPitch, -50.0F, 50.0F) * DEG, follow);
        float headYaw = lerp(CF_ANGLES[1], clampF(lookYaw, -75.0F, 75.0F) * back * DEG, follow);
        model.head.pitch = headPitch + p.headPitchAdd;
        model.head.yaw = headYaw + p.headYawAdd;
        model.head.roll = p.headRollAdd;
    }

    // ------------------------------------------------------------------
    // Hängematte
    // ------------------------------------------------------------------

    /** Vorschau: so gedreht, dass man von schräg hinten oben auf die liegende Seite schaut, verkleinert und mittig. */
    private static final float HAMMOCK_PREVIEW_YAW = 200.0F;
    private static final float HAMMOCK_PREVIEW_TILT = -22.0F;
    private static final float HAMMOCK_PREVIEW_SCALE = 0.55F;
    private static final float HAMMOCK_PREVIEW_LIFT = 0.6F;

    private static final HammockMotion.Pose HM_POSE = new HammockMotion.Pose();

    /**
     * Wird die Hängematten-Transformation für diesen Render-State angewendet? Vanilla-Posen mit eigener Drehung (Gleiten,
     * Schwimmen, Tod, Reiten, Schlafen) bleiben unverändert. Renderer und Animator fragen dasselbe, damit die Props die
     * Transformation genau dann herausrechnen, wenn sie angewendet wurde.
     */
    public static boolean hammockTransformApplies(PlayerEntityRenderState state) {
        return state != null && !(state.isGliding || state.leaningPitch > 0.0F || state.usingRiptide || state.deathTime > 0.0F
                || state.hasVehicle || state.isInPose(EntityPose.SLEEPING));
    }

    /**
     * Hängematte (am Ende von {@code setupTransforms}): feste Ausrichtung wie beim Lagerfeuer (Gegendrehung auf den
     * Start-Yaw), Versatz zum Startpunkt, dann den ganzen Spieler in die Liegelage drehen und mit dem Tuch schaukeln
     * (siehe {@link HammockMotion}). Modell, Cosmetics und Props folgen; der Nametag bleibt aufrecht. In der Vorschau
     * zusätzlich gedreht, geneigt und verkleinert, damit Pfosten und Tuch in den Rahmen passen.
     */
    private static void applyHammockTransform(PlayerEntityRenderState state, MatrixStack matrices, ActiveEmote active) {
        if (!hammockTransformApplies(state)) return;
        HammockCoordinator.Frame frame = HammockCoordinator.frame(state, active);
        if (frame == null) return;
        float scale = state.baseScale > 0.0F ? state.baseScale : 1.0F;
        if (frame.preview) {
            matrices.translate(0.0F, HAMMOCK_PREVIEW_LIFT, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(HAMMOCK_PREVIEW_TILT));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(HAMMOCK_PREVIEW_YAW));
            matrices.scale(HAMMOCK_PREVIEW_SCALE, HAMMOCK_PREVIEW_SCALE, HAMMOCK_PREVIEW_SCALE);
        } else {
            // setupTransforms hat um (180 - bodyYaw) gedreht; zusätzlich (bodyYaw - renderYaw) ergibt (180 - renderYaw)
            if (Math.abs(frame.counterYaw) > 1.0E-3F) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(frame.counterYaw));
            }
            matrices.translate(frame.off[0] / scale, frame.off[1] / scale, frame.off[2] / scale);
        }
        HammockMotion.sample(frame.t, 0.0F, 0.0F, false, scale, HM_POSE);
        pushHammockBody(matrices, HM_POSE, scale);
    }

    /**
     * Körpertransformation {@code T} aus {@link HammockMotion} im Raum von {@code setupTransforms} (Längen in Blöcken
     * geteilt durch die Spieler-Skalierung): {@code Tr(Achse) · Rx(Schaukeln) · Tr(-Achse) · Tr(Hüfte) · Ry · Rx ·
     * Tr(-Hüfte im Stand)}.
     */
    public static void pushHammockBody(MatrixStack m, HammockMotion.Pose p, float scale) {
        float ay = (float) (HammockLogic.AXIS_Y / scale);
        m.translate(0.0F, ay, 0.0F);
        if (p.swayDeg != 0.0F) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.swayDeg));
        m.translate(0.0F, -ay, 0.0F);
        m.translate((float) (p.hipX / scale), (float) (p.hipY / scale), (float) (p.hipZ / scale));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.lieYawDeg));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.liePitchDeg));
        m.translate(0.0F, (float) -HammockMotion.HIP_LOCAL_Y, 0.0F);
    }

    /** Umkehrung von {@link #pushHammockBody} (für Props, die fest bzw. nur mit dem Tuch stehen sollen). */
    public static void popHammockBody(MatrixStack m, HammockMotion.Pose p, float scale) {
        float ay = (float) (HammockLogic.AXIS_Y / scale);
        m.translate(0.0F, (float) HammockMotion.HIP_LOCAL_Y, 0.0F);
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-p.liePitchDeg));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-p.lieYawDeg));
        m.translate((float) (-p.hipX / scale), (float) (-p.hipY / scale), (float) (-p.hipZ / scale));
        m.translate(0.0F, ay, 0.0F);
        if (p.swayDeg != 0.0F) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-p.swayDeg));
        m.translate(0.0F, -ay, 0.0F);
    }

    /**
     * Hängematte: Winkel der Körperteile aus {@link HammockMotion} (liegen, Hand hinter dem Kopf, Arm auf dem Bauch,
     * Knie angewinkelt, Atmen, Strecken). Der Kopf schaut gemächlich umher und folgt dem Blick, wenn der Spieler sich
     * deutlich umschaut (bis ±70°). Die Liegelage selbst kommt aus {@link #applyBodyTilt}.
     */
    private static void applyHammock(PlayerEntityModel model, float t, boolean isPreview, PlayerEntityRenderState state) {
        float lookYaw = 0.0F, lookPitch = 0.0F;
        boolean look = false;
        if (!isPreview && state != null) {
            ActiveEmote active = resolveActiveEmote(state);
            HammockCoordinator.Frame frame = active != null ? HammockCoordinator.frame(state, active) : null;
            float counterYaw = frame != null ? frame.counterYaw : 0.0F;
            lookYaw = (float) HammockLogic.wrapDegrees(state.relativeHeadYaw + counterYaw);
            lookPitch = state.pitch;
            look = true;
        }
        HammockMotion.Pose p = HM_POSE;
        HammockMotion.sample(t, lookYaw, lookPitch, look, p);
        model.head.pitch = p.headPitch;
        model.head.yaw = p.headYaw;
        model.head.roll = p.headRoll;
        model.body.zScale = p.chestScale;
        model.rightArm.pitch = p.rArmPitch;
        model.rightArm.yaw = p.rArmYaw;
        model.rightArm.roll = p.rArmRoll;
        model.leftArm.pitch = p.lArmPitch;
        model.leftArm.yaw = p.lArmYaw;
        model.leftArm.roll = p.lArmRoll;
        model.rightLeg.pitch = p.rLegPitch;
        model.rightLeg.yaw = p.rLegYaw;
        model.rightLeg.roll = p.rLegRoll;
        model.leftLeg.pitch = p.lLegPitch;
        model.leftLeg.yaw = p.lLegYaw;
        model.leftLeg.roll = p.lLegRoll;
    }

    // ------------------------------------------------------------------
    // Schaukel
    // ------------------------------------------------------------------

    /** Vorschau: schräg von vorne, Gerüst mittig, verkleinert (Gerüst ca. 3 × 2,9 Blöcke). */
    private static final float SWING_PREVIEW_YAW = 32.0F;
    private static final float SWING_PREVIEW_TILT = -12.0F;
    private static final float SWING_PREVIEW_SCALE = 0.6F;
    private static final float SWING_PREVIEW_LIFT = 0.12F;

    private static final SwingMotion.Pose SW_POSE = new SwingMotion.Pose();

    /**
     * Schaukel (am Ende von {@code setupTransforms}): Gegendrehung auf die Ausrichtung des Gerüsts, Versatz zum Gerüst
     * (zweiter Sitz: der Spieler wird nur in der Darstellung zum Sitz versetzt), dann Hinsetzen, Lehnen und Schaukeln um
     * die Achse unter dem Balken ({@link SwingMotion}). Props und Cosmetics folgen, der Nametag bleibt am Spieler.
     */
    private static void applySwingTransform(PlayerEntityRenderState state, MatrixStack matrices, ActiveEmote active) {
        if (!hammockTransformApplies(state)) return;
        SwingCoordinator.Frame frame = SwingCoordinator.frame(state, active);
        if (frame == null) return;
        float scale = state.baseScale > 0.0F ? state.baseScale : 1.0F;
        if (frame.preview) {
            matrices.translate(0.0F, SWING_PREVIEW_LIFT, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(SWING_PREVIEW_TILT));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(SWING_PREVIEW_YAW));
            matrices.scale(SWING_PREVIEW_SCALE, SWING_PREVIEW_SCALE, SWING_PREVIEW_SCALE);
            matrices.translate((float) -SwingLogic.CENTER_X, 0.0F, 0.0F);
        } else {
            if (Math.abs(frame.counterYaw) > 1.0E-3F) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(frame.counterYaw));
            }
            matrices.translate(frame.off[0] / scale, frame.off[1] / scale, frame.off[2] / scale);
        }
        SwingMotion.sample(frame.t, frame.seat, frame.standFeet, frame.turn0, 0.0F, 0.0F, false, scale, SW_POSE);
        pushSwingBody(matrices, SW_POSE, scale);
    }

    /** {@code Tr(Achse) · Rx(Schaukeln) · Tr(-Achse) · Tr(Hüfte) · Ry(Drehen) · Rx(Lehnen) · Tr(-Hüfte im Stand)}. */
    public static void pushSwingBody(MatrixStack m, SwingMotion.Pose p, float scale) {
        float py = (float) (SwingLogic.PIVOT_Y / scale);
        m.translate(0.0F, py, 0.0F);
        if (p.swingDeg != 0.0F) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.swingDeg));
        m.translate(0.0F, -py, 0.0F);
        m.translate((float) (p.hipX / scale), (float) (p.hipY / scale), (float) (p.hipZ / scale));
        if (p.turnDeg != 0.0F) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.turnDeg));
        if (p.leanDeg != 0.0F) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.leanDeg));
        m.translate(0.0F, (float) -SwingMotion.HIP_LOCAL_Y, 0.0F);
    }

    /** Umkehrung von {@link #pushSwingBody}. */
    public static void popSwingBody(MatrixStack m, SwingMotion.Pose p, float scale) {
        float py = (float) (SwingLogic.PIVOT_Y / scale);
        m.translate(0.0F, (float) SwingMotion.HIP_LOCAL_Y, 0.0F);
        if (p.leanDeg != 0.0F) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-p.leanDeg));
        if (p.turnDeg != 0.0F) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-p.turnDeg));
        m.translate((float) (-p.hipX / scale), (float) (-p.hipY / scale), (float) (-p.hipZ / scale));
        m.translate(0.0F, py, 0.0F);
        if (p.swingDeg != 0.0F) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-p.swingDeg));
        m.translate(0.0F, -py, 0.0F);
    }

    /** Schaukel: Winkel der Körperteile (Hände an den Seilen, pumpende Beine, Kopf schaut nach vorne bzw. folgt dem Blick). */
    private static void applySwing(PlayerEntityModel model, float t, boolean isPreview, PlayerEntityRenderState state) {
        int seat = 0;
        double[] feet = null;
        float turn0 = 0.0F, lookYaw = 0.0F, lookPitch = 0.0F;
        boolean look = false;
        float scale = 1.0F;
        if (state != null) {
            ActiveEmote active = resolveActiveEmote(state);
            SwingCoordinator.Frame frame = active != null ? SwingCoordinator.frame(state, active) : null;
            if (frame != null) {
                seat = frame.seat;
                feet = frame.standFeet;
                turn0 = frame.turn0;
                if (!isPreview) {
                    lookYaw = (float) HammockLogic.wrapDegrees(state.relativeHeadYaw + frame.counterYaw);
                    lookPitch = state.pitch;
                    look = true;
                }
            }
            if (!isPreview && state.baseScale > 0.0F) scale = state.baseScale;
        }
        SwingMotion.Pose p = SW_POSE;
        SwingMotion.sample(t, seat, feet, turn0, lookYaw, lookPitch, look, scale, p);
        model.head.pitch = p.headPitch;
        model.head.yaw = p.headYaw;
        model.head.roll = p.headRoll;
        model.rightArm.pitch = p.rArmPitch;
        model.rightArm.yaw = p.rArmYaw;
        model.rightArm.roll = p.rArmRoll;
        model.leftArm.pitch = p.lArmPitch;
        model.leftArm.yaw = p.lArmYaw;
        model.leftArm.roll = p.lArmRoll;
        model.rightLeg.pitch = p.rLegPitch;
        model.rightLeg.yaw = p.rLegYaw;
        model.rightLeg.roll = p.rLegRoll;
        model.leftLeg.pitch = p.lLegPitch;
        model.leftLeg.yaw = p.lLegYaw;
        model.leftLeg.roll = p.lLegRoll;
    }

    // ------------------------------------------------------------------
    // Picknick
    // ------------------------------------------------------------------

    /** Vorschau: von schräg vorne oben auf Decke und Korb, verkleinert. */
    private static final float PICNIC_PREVIEW_YAW = 28.0F;
    private static final float PICNIC_PREVIEW_TILT = -24.0F;
    private static final float PICNIC_PREVIEW_SCALE = 1.05F;
    private static final float PICNIC_PREVIEW_LIFT = 0.5F;

    private static final PicnicMotion.Pose PC_POSE = new PicnicMotion.Pose();

    /** Picknick (am Ende von {@code setupTransforms}): Gegendrehung und Versatz wie bei der Hängematte, dann absenken. */
    private static void applyPicnicTransform(PlayerEntityRenderState state, MatrixStack matrices, ActiveEmote active) {
        if (!hammockTransformApplies(state)) return;
        PicnicCoordinator.Frame frame = PicnicCoordinator.frame(state, active);
        if (frame == null) return;
        float scale = state.baseScale > 0.0F ? state.baseScale : 1.0F;
        if (frame.preview) {
            matrices.translate(0.0F, PICNIC_PREVIEW_LIFT, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(PICNIC_PREVIEW_TILT));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(PICNIC_PREVIEW_YAW));
            matrices.scale(PICNIC_PREVIEW_SCALE, PICNIC_PREVIEW_SCALE, PICNIC_PREVIEW_SCALE);
            matrices.translate(0.0F, 0.0F, (float) -PicnicLogic.BLANKET_CZ);
        } else {
            if (Math.abs(frame.counterYaw) > 1.0E-3F) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(frame.counterYaw));
            }
            matrices.translate(frame.off[0] / scale, frame.off[1] / scale, frame.off[2] / scale);
        }
        PicnicMotion.sample(frame.t, PC_POSE);
        matrices.translate(0.0F, (float) (-PC_POSE.drop / scale), 0.0F);
    }

    /**
     * Picknick: Schneidersitz, Vorbeugen um die Hüfte (Drehpunkte versetzt wie beim Lagerfeuer), Arme zeigen auf Korb,
     * Mund, Flasche bzw. Ameise; Kopf schaut umher und folgt dem Blick ({@link PicnicMotion}).
     */
    private static void applyPicnic(PlayerEntityModel model, float t, boolean isPreview, PlayerEntityRenderState state) {
        String id = null;
        long seed = 0L;
        float lookYaw = 0.0F, lookPitch = 0.0F;
        boolean look = false;
        if (state != null) {
            ActiveEmote active = resolveActiveEmote(state);
            PicnicCoordinator.Frame frame = active != null ? PicnicCoordinator.frame(state, active) : null;
            if (frame != null) {
                id = frame.id;
                seed = frame.seedMs;
                if (!isPreview) {
                    lookYaw = (float) HammockLogic.wrapDegrees(state.relativeHeadYaw + frame.counterYaw);
                    lookPitch = state.pitch;
                    look = true;
                }
            }
        }
        PicnicMotion.Pose p = PC_POSE;
        PicnicMotion.sample(t, id, seed, true, lookYaw, lookPitch, look, p);
        model.body.originY += p.neckDY;
        model.body.originZ += p.neckDZ;
        model.head.originY += p.neckDY;
        model.head.originZ += p.neckDZ;
        model.rightArm.originY += p.shoulderDY;
        model.rightArm.originZ += p.shoulderDZ;
        model.leftArm.originY += p.shoulderDY;
        model.leftArm.originZ += p.shoulderDZ;
        model.body.pitch = p.lean;
        model.body.zScale = p.chestScale;
        model.head.pitch = p.headPitch;
        model.head.yaw = p.headYaw;
        model.head.roll = p.headRoll;
        model.rightArm.pitch = p.rArmPitch;
        model.rightArm.yaw = p.rArmYaw;
        model.rightArm.roll = p.rArmRoll;
        model.leftArm.pitch = p.lArmPitch;
        model.leftArm.yaw = p.lArmYaw;
        model.leftArm.roll = p.lArmRoll;
        model.rightLeg.pitch = p.rLegPitch;
        model.rightLeg.yaw = p.rLegYaw;
        model.leftLeg.pitch = p.lLegPitch;
        model.leftLeg.yaw = p.lLegYaw;
    }

    // ------------------------------------------------------------------
    // Hexenbesen
    // ------------------------------------------------------------------

    /** Vorschau: Besen schwebt auf der Stelle (schräg von vorne, siehe WitchBroomMotion), leicht von oben, verkleinert. */
    private static final float BROOM_PREVIEW_TILT = -10.0F;
    private static final float BROOM_PREVIEW_SCALE = 0.78F;
    private static final float BROOM_PREVIEW_LIFT = 0.12F;

    private static final WitchBroomMotion.Pose WB_POSE = new WitchBroomMotion.Pose();

    /**
     * Hexenbesen (am Ende von {@code setupTransforms}): Gegendrehung auf die Start-Ausrichtung und Versatz zum
     * Startpunkt wie bei der Hängematte, dann den ganzen Spieler auf den Besen setzen und mit ihm auf der Kreisbahn
     * bewegen ({@link WitchBroomMotion}). <b>Nur Darstellung:</b> Position und Hitbox des Spielers bleiben, der Nametag
     * bleibt am echten Spieler.
     */
    private static void applyWitchBroomTransform(PlayerEntityRenderState state, MatrixStack matrices, ActiveEmote active) {
        if (!hammockTransformApplies(state)) return;
        WitchBroomCoordinator.Frame frame = WitchBroomCoordinator.frame(state, active);
        if (frame == null) return;
        float scale = state.baseScale > 0.0F ? state.baseScale : 1.0F;
        if (frame.preview) {
            matrices.translate(0.0F, BROOM_PREVIEW_LIFT, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(BROOM_PREVIEW_TILT));
            matrices.scale(BROOM_PREVIEW_SCALE, BROOM_PREVIEW_SCALE, BROOM_PREVIEW_SCALE);
        } else {
            if (Math.abs(frame.counterYaw) > 1.0E-3F) {
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(frame.counterYaw));
            }
            matrices.translate(frame.off[0] / scale, frame.off[1] / scale, frame.off[2] / scale);
        }
        WitchBroomMotion.sample(frame.t, 0.0F, 0.0F, false, frame.preview, false, scale, WB_POSE);
        pushBroomRider(matrices, WB_POSE);
    }

    /** {@code T = Tr(q) · Ry(riderHeading) · Rz(riderBank) · Rx(riderPitch) · Tr(-E)} (siehe {@link WitchBroomMotion}). */
    public static void pushBroomRider(MatrixStack m, WitchBroomMotion.Pose p) {
        m.translate((float) p.qx, (float) p.qy, (float) p.qz);
        if (p.riderHeading != 0.0F) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.riderHeading));
        if (p.riderBank != 0.0F) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.riderBank));
        if (p.riderPitch != 0.0F) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.riderPitch));
        m.translate(0.0F, (float) -WitchBroomMotion.EDGE_Y, (float) -WitchBroomMotion.EDGE_Z);
    }

    /** Umkehrung von {@link #pushBroomRider} (für den Besen, der fest auf der Bahn liegt). */
    public static void popBroomRider(MatrixStack m, WitchBroomMotion.Pose p) {
        m.translate(0.0F, (float) WitchBroomMotion.EDGE_Y, (float) WitchBroomMotion.EDGE_Z);
        if (p.riderPitch != 0.0F) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-p.riderPitch));
        if (p.riderBank != 0.0F) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-p.riderBank));
        if (p.riderHeading != 0.0F) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-p.riderHeading));
        m.translate((float) -p.qx, (float) -p.qy, (float) -p.qz);
    }

    /**
     * Hexenbesen: Sitz-Pose aus {@link WitchBroomMotion} (Beine gespreizt nach vorne, Hüftgelenke etwas nach außen,
     * Hände per Rückwärtskinematik am Stiel, Schultern beim Greifen leicht vor), Kopf schaut voraus bzw. folgt dem Blick.
     */
    private static void applyWitchBroom(PlayerEntityModel model, float t, boolean isPreview, PlayerEntityRenderState state) {
        float lookYaw = 0.0F, lookPitch = 0.0F;
        boolean look = false;
        float scale = 1.0F;
        if (state != null) {
            if (!isPreview) {
                ActiveEmote active = resolveActiveEmote(state);
                WitchBroomCoordinator.Frame frame = active != null ? WitchBroomCoordinator.frame(state, active) : null;
                float counterYaw = frame != null ? frame.counterYaw : 0.0F;
                lookYaw = (float) HammockLogic.wrapDegrees(state.relativeHeadYaw + counterYaw);
                lookPitch = state.pitch;
                look = true;
            }
            if (state.baseScale > 0.0F) scale = state.baseScale;
        }
        boolean slim = model.rightArm.getDefaultTransform().y() > 2.25F;
        WitchBroomMotion.Pose p = WB_POSE;
        WitchBroomMotion.sample(t, lookYaw, lookPitch, look, isPreview, slim, scale, p);
        model.rightArm.originY += p.shoulderDY;
        model.rightArm.originZ += p.shoulderDZ;
        model.leftArm.originY += p.shoulderDY;
        model.leftArm.originZ += p.shoulderDZ;
        model.rightLeg.originX -= p.legShift;
        model.leftLeg.originX += p.legShift;
        model.head.pitch = p.headPitch;
        model.head.yaw = p.headYaw;
        model.head.roll = p.headRoll;
        model.rightArm.pitch = p.rArmPitch;
        model.rightArm.yaw = p.rArmYaw;
        model.rightArm.roll = p.rArmRoll;
        model.leftArm.pitch = p.lArmPitch;
        model.leftArm.yaw = p.lArmYaw;
        model.leftArm.roll = p.lArmRoll;
        model.rightLeg.pitch = p.rLegPitch;
        model.rightLeg.yaw = p.rLegYaw;
        model.rightLeg.roll = p.rLegRoll;
        model.leftLeg.pitch = p.lLegPitch;
        model.leftLeg.yaw = p.lLegYaw;
        model.leftLeg.roll = p.lLegRoll;
    }

    // ------------------------------------------------------------------
    // High-Five
    // ------------------------------------------------------------------

    private static final float DEG = (float) (Math.PI / 180.0);
    /** Augenhöhe für den Blick zum Partner (Blöcke, vor der Spieler-Skalierung). */
    private static final float HF_EYE_HEIGHT = 1.62F;

    /**
     * High-Five und Einschlagen (HIGH_FIVE_ACCEPT). Die Bewegung selbst (Warten, Ausholen, Schwung, Kontakt am
     * gemeinsamen Zielpunkt, Rückprall, Faust-Pumpe mit Hüpfer, Ausklingen) kommt aus {@link HighFiveMotion} und hängt
     * nur von Emote-Zeit und Klatsch-Zeitpunkt ab. Hier werden nur die Werte auf das Modell übertragen und der Kopf zum
     * Partner (bzw. beim Warten zum nächsten Spieler in der Nähe) gedreht.
     *
     * @param accept Einschlagen: kein Warten, der Arm geht direkt in die Ausholpose und schwingt zum Treffpunkt
     */
    private static void applyHighFive(PlayerEntityModel model, float t, boolean isPreview, PlayerEntityRenderState state,
                                      boolean accept) {
        HighFiveMotion.Input in = new HighFiveMotion.Input();
        in.role = isPreview ? HighFiveMotion.Role.PREVIEW
                : (accept ? HighFiveMotion.Role.ACCEPTER : HighFiveMotion.Role.WAITER);
        in.t = t;
        in.bodyRest = new double[]{model.body.originX, model.body.originY, model.body.originZ};
        in.headRest = new double[]{model.head.originX, model.head.originY, model.head.originZ};
        in.rightShoulder = new double[]{model.rightArm.originX, model.rightArm.originY, model.rightArm.originZ};
        in.leftShoulder = new double[]{model.leftArm.originX, model.leftArm.originY, model.leftArm.originZ};

        if (!isPreview && state != null) {
            HighFiveCoordinator.View view = HighFiveCoordinator.view(state);
            if (view != null) {
                long now = System.currentTimeMillis();
                in.paired = true;
                in.tauMs = now - view.clapTime();
                in.leadMs = view.clapTime() - view.lockedAt();
                in.target = HighFiveLogic.worldToModel(view.targetX(), view.targetY(), view.targetZ(),
                        state.x, state.y, state.z, state.bodyYaw);
                double[] partner = HighFiveLogic.worldToModel(view.partnerX(), view.partnerY(), view.partnerZ(),
                        state.x, state.y, state.z, state.bodyYaw);
                in.partnerX = partner[0];
                in.partnerZ = partner[2];
                in.partnerDistance = view.partnerDistance();
            }
        }

        HighFiveMotion.Pose pose = new HighFiveMotion.Pose();
        HighFiveMotion.sample(in, pose);

        model.body.originX = pose.bodyX;
        model.body.originY = pose.bodyY;
        model.body.originZ = pose.bodyZ;
        model.body.pitch = pose.bodyPitch;
        model.body.yaw = pose.bodyYaw;
        model.body.roll = pose.bodyRoll;

        model.head.originX = pose.headX;
        model.head.originY = pose.headY;
        model.head.originZ = pose.headZ;

        model.rightArm.originX = pose.rArmX;
        model.rightArm.originY = pose.rArmY;
        model.rightArm.originZ = pose.rArmZ;
        model.rightArm.pitch = pose.rArmPitch;
        model.rightArm.yaw = pose.rArmYaw;
        model.rightArm.roll = pose.rArmRoll;
        model.rightArm.yScale = pose.rArmScale;
        if (pose.rArmTwist != 0.0F) {
            // Drehung um die eigene Armachse (lokal, nach pitch/yaw/roll): Handfläche zum Partner bzw. nach vorne
            model.rightArm.rotate(new Quaternionf().rotationY(pose.rArmTwist));
        }

        model.leftArm.originX = pose.lArmX;
        model.leftArm.originY = pose.lArmY;
        model.leftArm.originZ = pose.lArmZ;
        model.leftArm.pitch = pose.lArmPitch;
        model.leftArm.yaw = pose.lArmYaw;
        model.leftArm.roll = pose.lArmRoll;

        model.rightLeg.originX += pose.legDX;
        model.rightLeg.originY += pose.legDY;
        model.rightLeg.originZ += pose.legDZ;
        model.leftLeg.originX += pose.legDX;
        model.leftLeg.originY += pose.legDY;
        model.leftLeg.originZ += pose.legDZ;
        model.rightLeg.pitch = pose.rLegPitch;
        model.leftLeg.pitch = pose.lLegPitch;

        if (isPreview) {
            model.head.pitch = -0.05F + pose.headPitchAdd;
            model.head.yaw = 0.0F;
            model.head.roll = pose.headRoll;
            return;
        }
        if (state == null) return;

        // Kopf: wie Vanilla, aber zum Partner bzw. zum nächsten Spieler in der Nähe gedreht (weich ein-/ausgeblendet)
        float headPitch = state.pitch * DEG;
        float headYaw = state.relativeHeadYaw * DEG;
        double[] look = HighFiveCoordinator.look(state);
        if (look != null && look[3] > 0.001) {
            double dx = look[0] - state.x;
            double dz = look[2] - state.z;
            double dy = look[1] - (state.y + HF_EYE_HEIGHT * state.baseScale);
            float yawWorld = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = (float) clampF((float) HighFiveLogic.wrapDegrees(yawWorld - state.bodyYaw), -75.0F, 75.0F);
            float pitchWorld = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            float w = (float) look[3];
            // Nach dem Klatsch löst sich der Blick mit dem Ausklingen wieder (sonst springt der Kopf am Ende)
            if (in.paired && in.tauMs > 0.0) w *= pose.partnerFocus;
            headYaw = lerp(headYaw, rel * DEG, w);
            headPitch = lerp(headPitch, clampF(pitchWorld, -50.0F, 50.0F) * DEG, w);
        }
        model.head.pitch = headPitch - pose.lean * 0.6F + pose.headPitchAdd;
        model.head.yaw = headYaw;
        model.head.roll = pose.headRoll;

        if (state.limbSwingAmplitude > 0.02F) {
            // Gehen mit erhobener Hand: Beine und linker Arm schwingen wie Vanilla
            float limbAmp = Math.min(state.limbSwingAmplitude, 1.0F);
            float swing = (float) Math.cos(state.limbSwingAnimationProgress * 0.6662F);
            model.rightLeg.pitch += swing * 1.4F * limbAmp * 0.5F;
            model.leftLeg.pitch += -swing * 1.4F * limbAmp * 0.5F;
            model.leftArm.pitch += swing * limbAmp * 0.5F;
        }
    }

    // ------------------------------------------------------------------
    // Fliegender Teppich
    // ------------------------------------------------------------------

    /**
     * Vorschau (Menü, Emote-Rad): schräg von vorne und leicht von oben, damit Muster und Neigung zu sehen sind; schwebt
     * dort nur {@link FlyingCarpetLogic#PREVIEW_ALT} hoch, damit alles im Rahmen bleibt.
     */
    private static final float CARPET_PREVIEW_YAW = -35.0F;
    private static final float CARPET_PREVIEW_TILT = -16.0F;
    private static final float CARPET_PREVIEW_SCALE = 0.8F;
    private static final float CARPET_PREVIEW_LIFT = 0.02F;
    /** Vanilla zeichnet schleichende Spieler 2 px tiefer; beim Teppich übernimmt das ein weiches Absinken. */
    private static final float VANILLA_SNEAK_DROP = 2.0F / 16.0F;

    private static final FlyingCarpetMotion.Input FC_IN = new FlyingCarpetMotion.Input();
    private static final FlyingCarpetMotion.Pose FC_POSE = new FlyingCarpetMotion.Pose();
    private static final FlyingCarpetLogic.Tilt FC_TILT = new FlyingCarpetLogic.Tilt();

    /**
     * Fliegender Teppich (am Ende von {@code setupTransforms}): Schwebehöhe (beim Start weich aufsteigend, dann je nach
     * Tempo bzw. Schleichen weich geglättet), Schweben und die Neigung von Teppich und Reiter (Kurve, Vorderkante,
     * Steigen/Sinken beim erlaubten Fliegen) als Verschiebung und Drehung um die Mitte des Teppichs; Teppich und
     * Cosmetics werden in derselben Matrix gezeichnet und gehen mit. <b>Nur Darstellung:</b> Position, Bewegung und
     * Hitbox des Spielers bleiben unverändert; der Vanilla-Schatten bleibt am Boden, den Nametag hebt der Koordinator
     * im Render-State mit an.
     */
    private static void applyFlyingCarpetTransform(PlayerEntityRenderState state, MatrixStack matrices, ActiveEmote active) {
        if (!hammockTransformApplies(state)) return;
        FlyingCarpetLogic.Tilt tl = FC_TILT;
        FlyingCarpetCoordinator.View v = FlyingCarpetCoordinator.tilt(state, active, tl);
        if (v.preview) {
            matrices.translate(0.0F, CARPET_PREVIEW_LIFT, 0.0F);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(CARPET_PREVIEW_TILT));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(CARPET_PREVIEW_YAW));
            matrices.scale(CARPET_PREVIEW_SCALE, CARPET_PREVIEW_SCALE, CARPET_PREVIEW_SCALE);
        } else if (state.isInSneakingPose) {
            matrices.translate(0.0F, VANILLA_SNEAK_DROP, 0.0F);
        }
        pushCarpetTilt(matrices, tl);
    }

    /**
     * Nach dem Ende: Reiter und Teppich sinken von der zuletzt gezeigten Höhe weich zu Boden, die Neigung klingt ab
     * ({@link FlyingCarpetMotion#outroTilt}); erst danach steht der Reiter auf.
     */
    private static void applyFlyingCarpetOutroTransform(PlayerEntityRenderState state, MatrixStack matrices) {
        if (!hammockTransformApplies(state)) return;
        FlyingCarpetCoordinator.OutroView o = FlyingCarpetCoordinator.outro(state);
        if (o == null) return;
        // Solange der Reiter sitzt, wie im Emote nicht 2 px tiefer (beim Aufstehen weich zurück zu Vanilla)
        if (state.isInSneakingPose) matrices.translate(0.0F, VANILLA_SNEAK_DROP * o.motion.rider, 0.0F);
        FlyingCarpetLogic.Tilt tl = FC_TILT;
        FlyingCarpetMotion.outroTilt(o.motion, o.dy0, o.w0, o.bank, o.nose, tl);
        pushCarpetTilt(matrices, tl);
    }

    /** {@code Tr(0, dy, 0) · Tr(P) · Rz(bank) · Rx(nose) · Tr(−P)} (siehe {@link FlyingCarpetLogic.Tilt}). */
    private static void pushCarpetTilt(MatrixStack matrices, FlyingCarpetLogic.Tilt tl) {
        if (tl.dy != 0.0) matrices.translate(0.0F, (float) tl.dy, 0.0F);
        if (tl.bank != 0.0F || tl.nose != 0.0F) {
            matrices.translate(0.0F, (float) tl.pivotY, (float) tl.pivotZ);
            if (tl.bank != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(tl.bank));
            if (tl.nose != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tl.nose));
            matrices.translate(0.0F, (float) -tl.pivotY, (float) -tl.pivotZ);
        }
    }

    /**
     * Fliegender Teppich: Schneidersitz (alle Teile per {@code originY} abgesenkt, Hüfte auf dem Teppich), Vorbeugen um
     * die Hüfte, im Stand Hände auf den Knien, in Fahrt rechte Hand an der Vorderkante und linke an der Seitenkante;
     * der Kopf schaut sich ab und zu um und folgt dem Blick. Die Beine machen keine Laufbewegung
     * ({@link FlyingCarpetMotion}).
     */
    private static void applyFlyingCarpet(PlayerEntityModel model, float t, boolean isPreview, PlayerEntityRenderState state) {
        // Vanilla-Posen mit eigener Drehung (Gleiten, Schwimmen, Reiten ...) bis zum STOP nicht überlagern
        if (!isPreview && state != null && !hammockTransformApplies(state)) return;
        FlyingCarpetMotion.Input in = FC_IN;
        in.t = t;
        in.slim = model.rightArm.getDefaultTransform().y() > 2.25F;
        ActiveEmote active = state != null ? resolveActiveEmote(state) : null;
        if (active != null && active.emote() == EmoteType.FLYING_CARPET) {
            FlyingCarpetCoordinator.View v = FlyingCarpetCoordinator.view(state, active);
            in.speed = v.speed;
            in.sneak = v.sneak;
            in.hold = v.hold;
            in.bank = v.bank;
        } else {
            in.speed = 0.0F;
            in.sneak = 0.0F;
            in.hold = 0.0F;
            in.bank = isPreview ? FlyingCarpetMotion.previewBank(t) : 0.0F;
        }
        in.look = !isPreview && state != null;
        in.lookYaw = in.look ? state.relativeHeadYaw : 0.0F;
        in.lookPitch = in.look ? state.pitch : 0.0F;
        FlyingCarpetMotion.Pose p = FC_POSE;
        FlyingCarpetMotion.sample(in, p);
        poseFlyingCarpet(model, p);
    }

    private static final float[] FC_VANILLA = new float[36];

    /**
     * Auslaufen (kein Emote mehr, aus {@link #apply}): solange der Teppich noch schwebt bzw. sinkt, sitzt der Reiter
     * weiter im Schneidersitz (keine Beine durch den Teppich); danach steht er auf und geht dabei weich in die
     * Vanilla-Pose über (Laufen, Blick, Arme), ohne Sprung am Ende.
     */
    private static void applyFlyingCarpetOutro(PlayerEntityModel model, PlayerEntityRenderState state) {
        if (!hammockTransformApplies(state)) return;
        FlyingCarpetCoordinator.OutroView o = FlyingCarpetCoordinator.outro(state);
        if (o == null || !(o.motion.rider > 0.0F)) return;
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        float[] v = FC_VANILLA;
        for (int i = 0; i < parts.length; i++) {
            ModelPart m = parts[i];
            v[6 * i] = m.pitch;
            v[6 * i + 1] = m.yaw;
            v[6 * i + 2] = m.roll;
            v[6 * i + 3] = m.originX;
            v[6 * i + 4] = m.originY;
            v[6 * i + 5] = m.originZ;
        }
        float zScale = model.body.zScale;
        resetAllParts(model);
        FlyingCarpetMotion.Input in = FC_IN;
        in.t = o.tEnd + o.age;
        in.slim = model.rightArm.getDefaultTransform().y() > 2.25F;
        in.speed = o.speed;
        in.sneak = o.sneak;
        in.hold = o.hold;
        in.bank = o.bank;
        in.look = true;
        in.lookYaw = state.relativeHeadYaw;
        in.lookPitch = state.pitch;
        FlyingCarpetMotion.Pose p = FC_POSE;
        FlyingCarpetMotion.sampleOutro(in, o.motion, o.tEnd, o.age, o.w0, p);
        poseFlyingCarpet(model, p);
        float rider = o.motion.rider;
        for (int i = 0; i < parts.length; i++) {
            ModelPart m = parts[i];
            // Arme: beim Schlagen bzw. Benutzen eines Items weich die Vanilla-Bewegung (die Schultern bleiben am Rumpf)
            float w = i == 2 || i == 3 ? rider * (1.0F - o.arm) : rider;
            m.pitch = lerp(v[6 * i], m.pitch, w);
            m.yaw = lerp(v[6 * i + 1], m.yaw, w);
            m.roll = lerp(v[6 * i + 2], m.roll, w);
            m.originX = lerp(v[6 * i + 3], m.originX, rider);
            m.originY = lerp(v[6 * i + 4], m.originY, rider);
            m.originZ = lerp(v[6 * i + 5], m.originZ, rider);
        }
        model.body.zScale = lerp(zScale, model.body.zScale, rider);
    }

    /** Pose aus {@link FlyingCarpetMotion} auf das zurückgesetzte Modell übertragen. */
    private static void poseFlyingCarpet(PlayerEntityModel model, FlyingCarpetMotion.Pose p) {
        float drop = p.drop;
        model.body.originY += drop + p.neckDY;
        model.body.originZ += p.neckDZ;
        model.head.originY += drop + p.neckDY;
        model.head.originZ += p.neckDZ;
        model.rightArm.originY += drop + p.shoulderDY;
        model.rightArm.originZ += p.shoulderDZ;
        model.leftArm.originY += drop + p.shoulderDY;
        model.leftArm.originZ += p.shoulderDZ;
        model.rightLeg.originY += drop;
        model.leftLeg.originY += drop;

        model.body.pitch = p.lean;
        model.body.zScale = p.chestScale;
        model.head.pitch = p.headPitch;
        model.head.yaw = p.headYaw;
        model.head.roll = p.headRoll;
        model.rightArm.pitch = p.rArmPitch;
        model.rightArm.yaw = p.rArmYaw;
        model.rightArm.roll = p.rArmRoll;
        model.leftArm.pitch = p.lArmPitch;
        model.leftArm.yaw = p.lArmYaw;
        model.leftArm.roll = p.lArmRoll;
        model.rightLeg.pitch = p.rLegPitch;
        model.rightLeg.yaw = p.rLegYaw;
        model.leftLeg.pitch = p.lLegPitch;
        model.leftLeg.yaw = p.lLegYaw;
    }

    private static float clampF(float x, float lo, float hi) {
        return x < lo ? lo : (x > hi ? hi : x);
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
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
