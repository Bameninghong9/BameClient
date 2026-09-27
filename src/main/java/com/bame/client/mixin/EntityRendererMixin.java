package com.bame.client.mixin;

import com.bame.client.module.NameProtectModule;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void onUpdateRenderState(T entity, S state, float tickDelta, CallbackInfo ci) {
        if (NameProtectModule.enabled && state.displayName != null) {
            state.displayName = NameProtectModule.protect(state.displayName);
        }
    }
}
