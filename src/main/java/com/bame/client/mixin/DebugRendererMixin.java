package com.bame.client.mixin;

import com.bame.client.module.CustomHitboxesModule;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.debug.DebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(Frustum frustum, double cameraX, double cameraY, double cameraZ, float tickDelta, CallbackInfo ci) {
        if (CustomHitboxesModule.shouldRender()) {
            CustomHitboxesModule.render(frustum, tickDelta);
        }
    }
}
