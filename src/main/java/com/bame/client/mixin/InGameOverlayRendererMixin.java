package com.bame.client.mixin;

import com.bame.client.module.CustomFireModule;
import com.bame.client.render.CustomFireRenderer;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Fire: eigenes Bildschirm-Feuer (Farbe, Höhe, Deckkraft) statt Vanilla. */
@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void caeser$customFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        if (CustomFireModule.enabled && CustomFireRenderer.renderOverlay(matrices, vertexConsumers)) ci.cancel();
    }
}
