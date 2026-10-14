package com.bame.client.mixin;

import com.bame.client.gui.Panel3D;
import com.bame.client.gui.ThreeDGuiManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Modell-Vorschauen (Spieler-Skins, Entities) ignorieren in 1.21.11 die GUI-Matrix: ihr Rechteck wird unverändert
 * übernommen. Solange das schwebende 3D-Panel transformiert gezeichnet wird, wird das Rechteck hier in den geneigten
 * Raum umgerechnet (Hüllrechteck) und die Modellgröße mitskaliert, damit Vorschauen mit dem Panel mitwandern.
 * Ohne aktives Panel (3D GUI aus, andere Bildschirme) bleibt alles unverändert.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextPanel3DMixin {
    @Unique
    private static boolean caeser$panelReentry = false;

    @Inject(method = "addPlayerSkin", at = @At("HEAD"), cancellable = true)
    private void caeser$panelPlayerSkin(PlayerEntityModel playerModel, Identifier texture, float scale, float xRotation,
                                        float yRotation, float yPivot, int x1, int y1, int x2, int y2, CallbackInfo ci) {
        Panel3D panel = ThreeDGuiManager.pushedPanel();
        if (panel == null || caeser$panelReentry) return;
        int[] r = panel.boundsOnScreen(x1, y1, x2, y2);
        caeser$panelReentry = true;
        try {
            ((DrawContext) (Object) this).addPlayerSkin(playerModel, texture, scale * panel.uniformScale(), xRotation,
                    yRotation, yPivot, r[0], r[1], r[2], r[3]);
        } finally {
            caeser$panelReentry = false;
        }
        ci.cancel();
    }

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void caeser$panelEntity(EntityRenderState entityState, float scale, Vector3f translation, Quaternionf rotation,
                                    Quaternionf overrideCameraAngle, int x1, int y1, int x2, int y2, CallbackInfo ci) {
        Panel3D panel = ThreeDGuiManager.pushedPanel();
        if (panel == null || caeser$panelReentry) return;
        int[] r = panel.boundsOnScreen(x1, y1, x2, y2);
        caeser$panelReentry = true;
        try {
            ((DrawContext) (Object) this).addEntity(entityState, scale * panel.uniformScale(), translation, rotation,
                    overrideCameraAngle, r[0], r[1], r[2], r[3]);
        } finally {
            caeser$panelReentry = false;
        }
        ci.cancel();
    }
}
