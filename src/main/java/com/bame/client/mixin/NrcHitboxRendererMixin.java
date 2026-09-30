package com.bame.client.mixin;

import com.bame.client.module.CustomHitboxesModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "gg.norisk.client.v2.modules.hitbox.HitBox_v1_21_11", remap = false)
public class NrcHitboxRendererMixin {
    @Inject(method = "method_23109", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onRender(CallbackInfo ci) {
        if (CustomHitboxesModule.enabled) {
            ci.cancel();
        }
    }
}
