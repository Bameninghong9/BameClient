package com.bame.client.mixin;

import com.bame.client.emotes.EmoteAnimator;
import com.bame.client.emotes.EmotePreviewHolder;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.PlayerLikeEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    // Kein eigenes addFeature(EmotePropFeatureRenderer) mehr: der Prop-Renderer wird bereits in BameClient ueber
    // LivingEntityFeatureRendererRegistrationCallback fuer jeden PlayerEntityRenderer registriert. Die doppelte
    // Registrierung hat jedes Emote-Prop zweimal an derselben Stelle gezeichnet (bei transluzenten Teilen sichtbar).

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void onUpdateRenderState(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (state instanceof EmotePreviewHolder holder) {
            // UUID für den Cosmetic-Sync (Maps nach UUID); Mannequins o. Ä. bekommen keine
            holder.caeser$setPlayerUuid(entity instanceof PlayerEntity player ? player.getUuid() : null);
            if (entity instanceof PlayerEntity pe && pe.getGameProfile() != null && pe.getGameProfile().name() != null) {
                holder.caeser$setPlayerUsername(pe.getGameProfile().name());
            } else if (entity != null && entity.getName() != null) {
                holder.caeser$setPlayerUsername(entity.getName().getString());
            }
        }
        // Lagerfeuer: Spieler wird vom Feuer angestrahlt (nur Spieler mit CAMPFIRE, kein echtes Licht in der Welt)
        EmoteAnimator.applyCampfireLight(state);
    }

    /**
     * Liegestütze: den ganzen Spieler nach den Vanilla-Körpertransformationen (Yaw, Schwimmen, Schlafen ...) um die
     * Fußspitzen nach vorne kippen. Läuft vor scale(-1,-1,1) und dem Modell; der Nametag wird außerhalb dieser Matrix
     * gezeichnet (EntityRenderer.render nach dem pop()) und bleibt daher aufrecht.
     */
    @Inject(method = "setupTransforms(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;FF)V", at = @At("RETURN"))
    private void caeser$pushUpTilt(PlayerEntityRenderState state, MatrixStack matrices, float bodyYaw, float baseHeight, CallbackInfo ci) {
        EmoteAnimator.applyBodyTilt(state, matrices);
    }
}
