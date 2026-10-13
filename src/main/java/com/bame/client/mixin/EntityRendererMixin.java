package com.bame.client.mixin;

import com.bame.client.emotes.CampfireCoordinator;
import com.bame.client.emotes.HammockCoordinator;
import com.bame.client.emotes.PicnicCoordinator;
import com.bame.client.emotes.SwingCoordinator;
import com.bame.client.module.NameProtectModule;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void onUpdateRenderState(T entity, S state, float tickDelta, CallbackInfo ci) {
        if (NameProtectModule.enabled && state.displayName != null) {
            state.displayName = NameProtectModule.protect(state.displayName);
        }
    }

    /**
     * Lagerfeuer: Der Gastgeber zeichnet das Feuer im eigenen Modellraum mit. Liegt nur das Feuer im Sichtfeld, würde
     * Vanilla den Spieler samt Feuer wegschneiden; dann zählt zusätzlich die Box des Feuers. Alle anderen Entities und
     * Spieler ohne Feuer bleiben unverändert. Gleiches gilt für die Hängematte (Pfosten bis 1,9 Blöcke neben dem Spieler),
     * die Schaukel (Gerüst; der zweite Spieler wird am Sitz gezeichnet) und das Picknick (Decke und Korb).
     */
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void caeser$campfireCulling(T entity, Frustum frustum, double x, double y, double z,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !(entity instanceof PlayerEntity player)) return;
        Box fire = CampfireCoordinator.fireCullBox(player);
        if (fire == null) fire = HammockCoordinator.cullBox(player);
        if (fire == null) fire = SwingCoordinator.cullBox(player);
        if (fire == null) fire = PicnicCoordinator.cullBox(player);
        if (fire != null && entity.shouldRender(x, y, z) && frustum.isVisible(fire)) {
            cir.setReturnValue(true);
        }
    }
}
