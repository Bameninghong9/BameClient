package com.bame.client.mixin;

import com.bame.client.module.BlockOutlineModule;
import com.bame.client.module.CustomSkyModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int modifyBlockOutlineColor(int color) {
        if (BlockOutlineModule.enabled) {
            return BlockOutlineModule.getOutlineColor();
        }
        return color;
    }

    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modifyBlockOutlineWidth(float lineWidth) {
        if (BlockOutlineModule.enabled) {
            return BlockOutlineModule.lineWidth;
        }
        return lineWidth;
    }

    /** Custom Sky: eigene Wolkenfarbe (Helligkeit und Alpha folgen den Vanilla-Wolken, also auch nachts dunkler). */
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderClouds(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/option/CloudRenderMode;Lnet/minecraft/util/math/Vec3d;JFIF)V"),
            index = 5)
    private int caeser$cloudColor(int color) {
        if (!CustomSkyModule.enabled || !CustomSkyModule.customClouds) return color;
        return CustomSkyModule.shade(CustomSkyModule.cloudColor & 0xFFFFFF, color, 255);
    }
}
