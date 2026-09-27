package com.bame.client.mixin;

import com.bame.client.module.FullbrightModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SimpleOption.class)
public class SimpleOptionMixin {
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private void onGetValue(CallbackInfoReturnable<Object> cir) {
        if (FullbrightModule.enabled && cir.getReturnValue() instanceof Double) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.options != null) {
                if ((Object) this == client.options.getGamma()) {
                    double current = (Double) cir.getReturnValue();
                    double target = FullbrightModule.intensity * 10.0; // 10.0 is very bright
                    if (target > current) {
                        cir.setReturnValue(Math.max(current, target));
                    }
                }
            }
        }
    }
}
