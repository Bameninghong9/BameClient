package com.bame.client.killfx;

import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.EntityType;

/**
 * Render-Zustand der Opfer-Figur unter dem Amboss: ein normaler Spieler-Render-Zustand (Vanilla-Spielermodell samt
 * Rüstungs-Feature), aber ohne Entity, ohne Namensschild und mit eigener Pose. Wird nur über den
 * {@code EntityRenderManager} in die Befehlsliste der Welt gegeben, nie in die Entity-Liste der Welt.
 */
public final class KillFigureState extends PlayerEntityRenderState {

    /** Keine echte Entity-ID (damit die Figur nie für den lokalen Spieler gehalten wird). */
    static final int FIGURE_ID = -0x4B1F1;

    /** Arme vor Schreck gehoben (0..1). */
    public float armRaise;
    /** Plattgedrückt (0..1): Arme und Beine zur Seite. */
    public float splat;

    KillFigureState() {
        this.entityType = EntityType.PLAYER;
        this.id = FIGURE_ID;
        this.width = 0.6F;
        this.height = 1.8F;
        this.standingEyeHeight = 1.62F;
        this.displayName = null;
        this.playerName = null;
        this.nameLabelPos = null;
    }

    /** Aus dem PlayerEntityModel-Mixin (nach setAngles, gilt auch für die Rüstungsmodelle). */
    public static void applyPose(PlayerEntityModel model, PlayerEntityRenderState state) {
        if (!(state instanceof KillFigureState s) || model == null) return;
        float r = s.armRaise;
        if (r > 0.0F) {
            // Arme seitlich leicht nach oben ("oh nein"), das Gesicht bleibt frei
            model.rightArm.pitch = lerp(r, model.rightArm.pitch, -0.5F);
            model.leftArm.pitch = lerp(r, model.leftArm.pitch, -0.5F);
            model.rightArm.roll = lerp(r, model.rightArm.roll, 1.05F);
            model.leftArm.roll = lerp(r, model.leftArm.roll, -1.05F);
            model.rightArm.yaw = lerp(r, model.rightArm.yaw, 0.0F);
            model.leftArm.yaw = lerp(r, model.leftArm.yaw, 0.0F);
        }
        float f = s.splat;
        if (f > 0.0F) {
            // Platt: Arme und Beine gespreizt
            model.rightArm.pitch = lerp(f, model.rightArm.pitch, 0.0F);
            model.leftArm.pitch = lerp(f, model.leftArm.pitch, 0.0F);
            model.rightArm.yaw = lerp(f, model.rightArm.yaw, 0.0F);
            model.leftArm.yaw = lerp(f, model.leftArm.yaw, 0.0F);
            model.rightArm.roll = lerp(f, model.rightArm.roll, 1.25F);
            model.leftArm.roll = lerp(f, model.leftArm.roll, -1.25F);
            model.rightLeg.roll = lerp(f, model.rightLeg.roll, 0.35F);
            model.leftLeg.roll = lerp(f, model.leftLeg.roll, -0.35F);
            model.rightLeg.pitch = lerp(f, model.rightLeg.pitch, 0.0F);
            model.leftLeg.pitch = lerp(f, model.leftLeg.pitch, 0.0F);
        }
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }
}
