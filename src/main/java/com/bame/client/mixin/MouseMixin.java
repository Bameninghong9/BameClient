package com.bame.client.mixin;

import com.bame.client.module.FreelookModule;
import com.bame.client.module.ZoomModule;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ZoomModule.isZooming() && ZoomModule.scrollZoom) {
            ZoomModule.onMouseScroll(vertical);
            ci.cancel();
        }
    }

    @Redirect(
        method = "updateMouse",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V")
    )
    private void onUpdateMouse(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
        if (FreelookModule.isActive()) {
            FreelookModule.onMouseTurn(cursorDeltaX, cursorDeltaY);
        } else {
            if (ZoomModule.isZooming() && ZoomModule.currentZoom < 0.99) {
                cursorDeltaX *= ZoomModule.currentZoom;
                cursorDeltaY *= ZoomModule.currentZoom;
            }
            player.changeLookDirection(cursorDeltaX, cursorDeltaY);
        }
    }
}
