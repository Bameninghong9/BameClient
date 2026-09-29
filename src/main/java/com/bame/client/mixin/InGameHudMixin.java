package com.bame.client.mixin;

import com.bame.client.module.FakeScoreboardModule;
import com.bame.client.module.ShowHudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebarTick(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (FakeScoreboardModule.enabled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderScoreboardSidebarObjective(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (FakeScoreboardModule.enabled) {
            ci.cancel();
            return;
        }
        if (!com.bame.client.module.ScoreboardModule.enabled) {
            ci.cancel();
            return;
        }
        ci.cancel();
        com.bame.client.render.ScoreboardRenderer.render(context, objective, com.bame.client.module.ScoreboardModule.hudX, com.bame.client.module.ScoreboardModule.hudY, com.bame.client.module.ScoreboardModule.scale, false);
    }

    @Inject(method = "renderStatusEffectOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ShowHudModule.enabled && com.bame.client.module.PotionsModule.enabled) {
            ci.cancel();
        }
    }

    @Inject(method = "renderCrosshair(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (com.bame.client.module.CustomCrosshairModule.enabled) {
            ci.cancel();
            com.bame.client.render.CustomCrosshairRenderer.render(context);
        }
    }
}

