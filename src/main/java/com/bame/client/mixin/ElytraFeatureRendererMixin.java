package com.bame.client.mixin;

import com.bame.client.render.HideArmorHooks;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * „Hide Armor“ mit ausgeschaltetem „Keep elytra“: die Elytra zeichnet Vanilla in einem eigenen Renderer; bei
 * ausgeblendetem Brust-Slot entfällt sie für Spieler ({@link HideArmorHooks}).
 */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void caeser$hideElytra(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, BipedEntityRenderState state,
                                   float limbAngle, float limbDistance, CallbackInfo ci) {
        if (HideArmorHooks.hidesElytra(state)) ci.cancel();
    }
}
