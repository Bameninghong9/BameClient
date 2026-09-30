package com.bame.client.mixin;

import com.bame.client.module.CustomHitboxesModule;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.world.debug.DebugDataStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
public class EntityHitboxDebugRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(double cameraX, double cameraY, double cameraZ, DebugDataStore dataStore, Frustum frustum, float tickDelta, CallbackInfo ci) {
        if (CustomHitboxesModule.enabled) {
            ci.cancel();
        }
    }

    @Inject(method = "drawHitbox", at = @At("HEAD"), cancellable = true)
    private void onDrawHitbox(Entity entity, float tickProgress, boolean inLocalServer, CallbackInfo ci) {
        if (CustomHitboxesModule.enabled) {
            ci.cancel();
        }
    }
}
