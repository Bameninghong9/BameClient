package com.bame.client.mixin;

import com.bame.client.render.HideArmorHooks;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * „Hide Armor“ mit ausgeschaltetem „Keep heads &amp; pumpkins“: Köpfe, geschnitzte Kürbisse und andere Items im
 * Helm-Slot zeichnet Vanilla hier (nicht im ArmorFeatureRenderer); bei ausgeblendetem Helm-Slot entfällt das für
 * Spieler. Mobs und Rüstungsständer sind nicht betroffen ({@link HideArmorHooks}).
 */
@Mixin(HeadFeatureRenderer.class)
public abstract class HeadFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/LivingEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void caeser$hideHeadItem(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, LivingEntityRenderState state,
                                     float limbAngle, float limbDistance, CallbackInfo ci) {
        if (HideArmorHooks.hidesHeadItem(state)) ci.cancel();
    }
}
