package com.bame.client.mixin;

import com.bame.client.module.LowShieldModule;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", shift = At.Shift.AFTER))
    private void onRenderFirstPersonItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                         float swingProgress, ItemStack item, float equipProgress,
                                         MatrixStack matrices, OrderedRenderCommandQueue renderQueue, int light,
                                         CallbackInfo ci) {
        if (LowShieldModule.enabled && item != null && item.isOf(Items.SHIELD)) {
            float factor = LowShieldModule.heightPercent / 100.0f; // 0.01 to 1.0
            // Translate down
            matrices.translate(0.0f, -factor * 0.35f, 0.0f);
            // Slight scale reduction for clearer view
            float s = 1.0f - factor * 0.20f;
            matrices.scale(s, s, s);
        }

        if (com.bame.client.module.HandPositionModule.shouldApply(item)) {
            boolean isMain = (hand == Hand.MAIN_HAND);
            if (isMain || com.bame.client.module.HandPositionModule.applyToOffhand) {
                float x = com.bame.client.module.HandPositionModule.posX;
                float y = com.bame.client.module.HandPositionModule.posY;
                float z = com.bame.client.module.HandPositionModule.posZ;
                float s = com.bame.client.module.HandPositionModule.scale;

                if (!isMain) {
                    x = -x;
                }

                if (x != 0.0f || y != 0.0f || z != 0.0f) {
                    matrices.translate(x, y, z);
                }
                if (s != 1.0f) {
                    matrices.scale(s, s, s);
                }
            }
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
            at = @At("HEAD"))
    private void onRenderItem(net.minecraft.entity.LivingEntity entity, ItemStack stack,
                             net.minecraft.item.ItemDisplayContext renderMode, MatrixStack matrices,
                             OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        if (LowShieldModule.enabled && renderMode.isFirstPerson() && stack != null && stack.isOf(Items.TOTEM_OF_UNDYING)) {
            float factor = LowShieldModule.totemSizePercent / 100.0f; // 0.10 to 1.0
            if (factor < 1.0f) {
                matrices.scale(factor, factor, factor);
            }
        }
    }
}
