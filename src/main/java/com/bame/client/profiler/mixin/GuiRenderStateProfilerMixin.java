package com.bame.client.profiler.mixin;

import com.bame.client.gui.GuiProfiler;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Profiler only (applied with -Dcaeser.guiProfile=true): counts all simple GUI elements (fills, textured quads, sprites). */
@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateProfilerMixin {
    @Inject(method = "addSimpleElement", at = @At("HEAD"))
    private void bameclient$countElement(CallbackInfo ci) {
        GuiProfiler.countElement();
    }
}
