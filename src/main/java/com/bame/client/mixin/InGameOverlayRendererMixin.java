package com.bame.client.mixin;

import com.bame.client.module.CustomFireModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Fire: Höhe und Deckkraft des Feuers am Bildschirm (die Farbe kommt aus der umgefärbten Feuer-Textur). */
@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void caeser$hideFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, CallbackInfo ci) {
        if (CustomFireModule.enabled && CustomFireModule.hideOverlay(MinecraftClient.getInstance())) ci.cancel();
    }

    @ModifyArg(method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"), index = 1)
    private static float caeser$fireHeight(float y) {
        return CustomFireModule.enabled ? CustomFireModule.overlayYOffset() : y;
    }

    @ModifyArg(method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;color(FFFF)Lnet/minecraft/client/render/VertexConsumer;"), index = 3)
    private static float caeser$fireOpacity(float alpha) {
        return CustomFireModule.enabled ? Math.clamp(CustomFireModule.opacity, 0.1f, 1.0f) : alpha;
    }
}
