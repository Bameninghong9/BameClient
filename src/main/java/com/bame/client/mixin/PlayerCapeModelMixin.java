package com.bame.client.mixin;

import com.bame.client.cosmetics.CaeserCosmeticsRenderer;
import net.minecraft.client.render.entity.model.PlayerCapeModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cape mit Rücken-Item (Slot BACK) oder Schmetterlingsflügeln: das Cape hängt ruhig wie im Stand (6 Grad) und schwingt
 * beim Laufen nicht aus. So bleibt es zwischen Rücken und Item (das dann etwas absteht) und schneidet nicht hindurch.
 * Die drei Felder steuern nur den Cape-Schwung (Vanilla setzt sie jedes Bild neu).
 */
@Mixin(PlayerCapeModel.class)
public abstract class PlayerCapeModelMixin {

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("HEAD"))
    private void caeser$calmCapeForBackItems(PlayerEntityRenderState state, CallbackInfo ci) {
        if (state != null && CaeserCosmeticsRenderer.calmsCape(state)) {
            state.field_53536 = 0.0F;
            state.field_53537 = 0.0F;
            state.field_53538 = 0.0F;
        }
    }
}
