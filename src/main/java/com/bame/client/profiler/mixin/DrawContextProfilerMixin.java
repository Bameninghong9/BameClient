package com.bame.client.profiler.mixin;

import com.bame.client.gui.GuiProfiler;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Profiler only (applied with -Dcaeser.guiProfile=true): counts every fill; all public fill overloads and fillGradient end here. */
@Mixin(DrawContext.class)
public abstract class DrawContextProfilerMixin {
    @Inject(method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/texture/TextureSetup;IIIIILjava/lang/Integer;)V", at = @At("HEAD"))
    private void bameclient$countFill(CallbackInfo ci) {
        GuiProfiler.countFill();
    }
}
