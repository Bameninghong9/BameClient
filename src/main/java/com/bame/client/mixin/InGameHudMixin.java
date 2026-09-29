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

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
    private void onRenderHudTail(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (com.bame.client.module.ReachDisplayModule.enabled && com.bame.client.module.ReachDisplayModule.mode == 0) {
            long elapsed = System.currentTimeMillis() - com.bame.client.module.ReachDisplayModule.lastHitTime;
            if (elapsed < 3000 && com.bame.client.module.ReachDisplayModule.lastReach > 0) {
                float alpha = 1.0f;
                if (elapsed > 2000) {
                    alpha = 1.0f - (float)(elapsed - 2000) / 1000.0f;
                }
                int a = (int)(alpha * 255.0f);
                if (a > 5) {
                    net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                    if (client != null && client.textRenderer != null && client.getWindow() != null) {
                        String str = com.bame.client.module.ReachDisplayModule.getReachString();
                        int tw = client.textRenderer.getWidth(com.bame.client.gui.CustomGuiUtils.getFontText(str));
                        int cx = client.getWindow().getScaledWidth() / 2 - tw / 2;
                        int cy = client.getWindow().getScaledHeight() / 2 - 18;
                        int col = (a << 24) | 0x00FFFFFF;
                        context.drawText(client.textRenderer, com.bame.client.gui.CustomGuiUtils.getFontText(str), cx, cy, col, true);
                    }
                }
            }
        }
    }
}

