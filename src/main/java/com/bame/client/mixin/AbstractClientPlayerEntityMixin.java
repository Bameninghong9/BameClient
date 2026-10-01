package com.bame.client.mixin;

import com.bame.client.module.SkinProtectModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void onGetSkin(CallbackInfoReturnable<SkinTextures> cir) {
        if (SkinProtectModule.enabled) {
            AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && self.getUuid().equals(mc.player.getUuid())) {
                cir.setReturnValue(SkinProtectModule.getCurrentSkin());
            }
        }
    }
}
