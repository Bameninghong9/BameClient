package com.bame.client.mixin;

import com.bame.client.module.FreelookModule;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Redirect(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V")
    )
    private void onSetRotation(Camera camera, float yaw, float pitch) {
        if (FreelookModule.isActive()) {
            this.setRotation(FreelookModule.getYaw(), FreelookModule.getPitch());
        } else {
            this.setRotation(yaw, pitch);
        }
    }
}
