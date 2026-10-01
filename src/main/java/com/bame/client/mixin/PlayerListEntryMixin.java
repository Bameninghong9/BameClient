package com.bame.client.mixin;

import com.bame.client.module.SkinProtectModule;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {
    @Shadow public abstract GameProfile getProfile();

    @Inject(method = "getSkinTextures", at = @At("HEAD"), cancellable = true)
    private void onGetSkinTextures(CallbackInfoReturnable<SkinTextures> cir) {
        if (SkinProtectModule.enabled) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && this.getProfile() != null && mc.player.getUuid().equals(this.getProfile().id())) {
                cir.setReturnValue(SkinProtectModule.getCurrentSkin());
            }
        }
    }
}
