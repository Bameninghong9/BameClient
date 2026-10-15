package com.bame.client.mixin;

import com.bame.client.emotes.EmoteAnimator;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityModel.class)
public class PlayerEntityModelMixin {
    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void onSetAngles(PlayerEntityRenderState state, CallbackInfo ci) {
        com.bame.client.util.Prof.push("caeser_emote_pose");
        try {
            EmoteAnimator.apply((PlayerEntityModel) (Object) this, state);
            // Kill-Effekt Amboss: Pose der Opfer-Figur (nur für deren eigenen Render-Zustand)
            com.bame.client.killfx.KillFigureState.applyPose((PlayerEntityModel) (Object) this, state);
        } finally {
            com.bame.client.util.Prof.pop();
        }
    }
}
