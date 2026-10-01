package com.bame.client.mixin;

import com.bame.client.module.ItemSizeModule;
import com.bame.client.render.ItemEntityRenderStateAccessor;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Inject(
        method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
        at = @At("TAIL")
    )
    private void onUpdateRenderState(ItemEntity entity, ItemEntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (state instanceof ItemEntityRenderStateAccessor accessor) {
            accessor.bame$setItem(entity.getStack().getItem());
        }
    }

    @Inject(
        method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER)
    )
    private void onRenderPush(ItemEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        if (ItemSizeModule.enabled) {
            Item item = state instanceof ItemEntityRenderStateAccessor accessor ? accessor.bame$getItem() : null;
            if (ItemSizeModule.matches(item)) {
                float s = ItemSizeModule.scale;
                if (ItemSizeModule.yOffset != 0.0f) {
                    matrices.translate(0.0f, ItemSizeModule.yOffset, 0.0f);
                }
                matrices.scale(s, s, s);
            }
        }
    }
}
