package com.bame.client.mixin;

import com.bame.client.module.ZoomModule;
import com.bame.client.playerwheel.WorldProjection;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    /**
     * Spieler-Auswahlrad (Chat offen): die Projektionsmatrix der Welt dieses Bilds merken (FOV mit Zoom, Seitenverhältnis,
     * Wackeln), damit der Strahl durch die Maus genau das Gezeichnete trifft. Unverändert durchgereicht; ohne Allokation.
     * {@code require = 0}: fehlt die Stelle (andere Mod), nutzt das Picking eine einfache Perspektive aus der FOV-Einstellung.
     */
    @ModifyArg(method = "renderWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/RawProjectionMatrix;set(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
            require = 0)
    private Matrix4f caeser$captureWorldProjection(Matrix4f projection) {
        WorldProjection.capture(projection);
        return projection;
    }

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        double targetZoom = ZoomModule.getTargetZoom();
        if (ZoomModule.mode == 1) { // Instant
            ZoomModule.currentZoom = targetZoom;
        } else { // Smooth
            double factor = 0.16;
            ZoomModule.currentZoom += (targetZoom - ZoomModule.currentZoom) * factor;
            if (Math.abs(ZoomModule.currentZoom - targetZoom) < 0.001) {
                ZoomModule.currentZoom = targetZoom;
            }
        }
        if (ZoomModule.currentZoom != 1.0) {
            cir.setReturnValue((float) (cir.getReturnValue() * ZoomModule.currentZoom));
        }
    }
}
