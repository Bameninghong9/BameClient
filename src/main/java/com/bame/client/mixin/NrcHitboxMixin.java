package com.bame.client.mixin;

import com.bame.client.module.CustomHitboxesModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "gg.norisk.client.v2.modules.hitbox.HitBox", remap = false)
public class NrcHitboxMixin {
    @Inject(method = "isRenderActive", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onIsRenderActive(CallbackInfoReturnable<Boolean> cir) {
        if (CustomHitboxesModule.enabled) {
            cir.setReturnValue(false);
        }
    }
}
