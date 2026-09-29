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
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
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
    }
}
