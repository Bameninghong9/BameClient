package com.bame.client.mixin;

import com.bame.client.cosmetics.CaeserSkinManager;
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
            if (self.getUuid().equals(mc.player.getUuid())) {
                if (SkinProtectModule.enabled) {
                    cir.setReturnValue(SkinProtectModule.getCurrentSkin());
                } else if (CaeserSkinManager.hasActiveOverride()) {
                    SkinTextures custom = CaeserSkinManager.getActiveSkinTextures();
                    if (custom != null) {
                        cir.setReturnValue(custom);
                    }
                }
            }
        }
    }
}
