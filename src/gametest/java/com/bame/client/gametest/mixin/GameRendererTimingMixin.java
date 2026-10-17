package com.bame.client.gametest.mixin;

import com.bame.client.render.GpuTimings;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Nur im Test: GPU-Zeit eines ganzen Bildes (Welt und HUD). */
@Mixin(GameRenderer.class)
public class GameRendererTimingMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void caeserTest$frameBegin(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        GpuTimings.begin("frame");
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void caeserTest$frameEnd(RenderTickCounter tickCounter, boolean tick, CallbackInfo ci) {
        GpuTimings.end("frame");
    }
}
