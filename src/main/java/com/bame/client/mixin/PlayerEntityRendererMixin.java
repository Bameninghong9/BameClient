package com.bame.client.mixin;

import com.bame.client.emotes.EmotePreviewHolder;
import com.bame.client.emotes.EmotePropFeatureRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        PlayerEntityRenderer renderer = (PlayerEntityRenderer) (Object) this;
        ((LivingEntityRendererAccessor) renderer).invokeAddFeature(new EmotePropFeatureRenderer(renderer));
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void onUpdateRenderState(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (state instanceof EmotePreviewHolder holder) {
            if (entity instanceof PlayerEntity pe && pe.getGameProfile() != null && pe.getGameProfile().name() != null) {
                holder.caeser$setPlayerUsername(pe.getGameProfile().name());
            } else if (entity != null && entity.getName() != null) {
                holder.caeser$setPlayerUsername(entity.getName().getString());
            }
        }
    }
}
