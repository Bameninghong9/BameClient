package com.bame.client.mixin;

import com.bame.client.module.NameProtectModule;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class PlayerEntityMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void onGetDisplayName(CallbackInfoReturnable<Text> cir) {
        if (NameProtectModule.enabled && cir.getReturnValue() != null) {
            String real = NameProtectModule.getRealUsername();
            PlayerEntity self = (PlayerEntity) (Object) this;
            if (real != null && self.getNameForScoreboard().equalsIgnoreCase(real)) {
                cir.setReturnValue(NameProtectModule.protect(cir.getReturnValue()));
            }
        }
    }

    @Inject(method = "getName", at = @At("RETURN"), cancellable = true)
    private void onGetName(CallbackInfoReturnable<Text> cir) {
        if (NameProtectModule.enabled && cir.getReturnValue() != null) {
            String real = NameProtectModule.getRealUsername();
            PlayerEntity self = (PlayerEntity) (Object) this;
            if (real != null && self.getNameForScoreboard().equalsIgnoreCase(real)) {
                cir.setReturnValue(NameProtectModule.protect(cir.getReturnValue()));
            }
        }
    }
}
