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

                float pitchRot = com.bame.client.module.HandPositionModule.pitch;
                float yawRot = com.bame.client.module.HandPositionModule.yaw;
                float rollRot = com.bame.client.module.HandPositionModule.roll;

                if (!isMain) {
                    yawRot = -yawRot;
                    rollRot = -rollRot;
                }

                if (pitchRot != 0.0f) {
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(pitchRot));
                }
                if (yawRot != 0.0f) {
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(yawRot));
                }
                if (rollRot != 0.0f) {
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(rollRot));
                }
            }
        }
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void onSwingArm(float swingProgress, MatrixStack matrices, int i, net.minecraft.util.Arm arm, CallbackInfo ci) {
        if (com.bame.client.module.HandPositionModule.enabled && com.bame.client.module.HandPositionModule.swingStyle != 0) {
            int style = com.bame.client.module.HandPositionModule.swingStyle;
            float sin = net.minecraft.util.math.MathHelper.sin(swingProgress * (float) Math.PI);
            float sinSqrt = net.minecraft.util.math.MathHelper.sin(net.minecraft.util.math.MathHelper.sqrt(swingProgress) * (float) Math.PI);

            switch (style) {
                case 1 -> { // Stab / Thrust
                    matrices.translate((float) i * -0.15f * sin, 0.08f * sin, -0.65f * sin);
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0f - 25.0f * sin)));
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-25.0f * sin));
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float) i * (-15.0f * sinSqrt)));
                }
                case 2 -> { // Side Swipe
                    matrices.translate((float) i * (-0.55f * sinSqrt), 0.05f * sin, -0.25f * sin);
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (35.0f + 65.0f * sinSqrt)));
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float) i * (-45.0f * sinSqrt)));
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(15.0f * sin));
                }
                case 3 -> { // Punch / Bashing
                    matrices.translate((float) i * (-0.12f * sin), -0.05f * sin, -0.55f * sin);
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees((float) i * (45.0f - 10.0f * sin)));
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(20.0f * sin));
                    matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float) i * (-10.0f * sin)));
                }
            }
            ci.cancel();
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
