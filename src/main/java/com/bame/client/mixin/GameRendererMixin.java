package com.bame.client.mixin;

import com.bame.client.module.ZoomModule;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onGetFov(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        double targetZoom = ZoomModule.enabled ? 0.25 : 1.0;
        if (ZoomModule.mode == 1) { // Instant
            ZoomModule.currentZoom = targetZoom;
        } else { // Smooth
            double factor = 0.1;
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
