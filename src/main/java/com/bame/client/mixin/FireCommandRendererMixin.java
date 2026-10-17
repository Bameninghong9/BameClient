package com.bame.client.mixin;

import com.bame.client.module.CustomFireModule;
import com.bame.client.render.CustomFireRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.FireCommandRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.texture.AtlasManager;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Fire: Feuer auf brennenden Entities in der eigenen Farbe. */
@Mixin(FireCommandRenderer.class)
public class FireCommandRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/entity/state/EntityRenderState;Lorg/joml/Quaternionf;Lnet/minecraft/client/texture/AtlasManager;)V",
            at = @At("HEAD"), cancellable = true)
    private void caeser$customEntityFire(MatrixStack.Entry entry, VertexConsumerProvider vertexConsumers, EntityRenderState state,
                                         Quaternionf rotation, AtlasManager atlasManager, CallbackInfo ci) {
        if (CustomFireModule.enabled && CustomFireModule.entityFire
                && CustomFireRenderer.renderEntityFire(entry, vertexConsumers, state, rotation)) {
            ci.cancel();
        }
    }
}
