package com.bame.client.mixin;

import com.bame.client.util.WindowTitleHelper;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "getWindowTitle", at = @At("HEAD"), cancellable = true)
    private void caeser$customWindowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(WindowTitleHelper.formatWindowTitle());
    }
}
