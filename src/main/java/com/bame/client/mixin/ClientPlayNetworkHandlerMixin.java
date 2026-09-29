package com.bame.client.mixin;

import com.bame.client.module.InvMoveModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onCloseScreen", at = @At("HEAD"), cancellable = true)
    private void onOnCloseScreen(CloseScreenS2CPacket packet, CallbackInfo ci) {
        if (InvMoveModule.enabled && InvMoveModule.shouldMove()) {
            ci.cancel();
        }
    }
}
