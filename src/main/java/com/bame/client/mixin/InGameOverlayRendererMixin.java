package com.bame.client.mixin;

import com.bame.client.module.CustomFireModule;
import com.bame.client.render.CustomFireOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Fire: eigenes Feuer am Bildschirm; das Vanilla-Feuer wird dabei (Standard) ausgeblendet. */
@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void caeser$customFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        if (!CustomFireModule.enabled) return;
        if (CustomFireModule.hideOverlay(MinecraftClient.getInstance())) {
            ci.cancel();
            return;
        }
        boolean drawn = CustomFireOverlay.render(matrices, vertexConsumers);
        if (drawn && CustomFireModule.hideVanillaFire) ci.cancel();
    }
}
