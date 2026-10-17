package com.bame.client.mixin;

import com.bame.client.module.BlockOutlineModule;
import com.bame.client.module.ShaderFogModule;
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

    /**
     * Shader Fog: Wolkenfarbe (Helligkeit und Alpha folgen den Vanilla-Wolken, also auch nachts dunkler). Ohne eigene
     * Farbe werden die Wolken bei Weltraum und Tiefsee dunkel, damit sie nicht weiß vor dem dunklen Himmel stehen.
     */
    @ModifyArg(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderClouds(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/option/CloudRenderMode;Lnet/minecraft/util/math/Vec3d;JFIF)V"),
            index = 5)
    private int caeser$cloudColor(int color) {
        if (!ShaderFogModule.enabled) return color;
        if (ShaderFogModule.customClouds) return ShaderFogModule.shade(ShaderFogModule.cloudColor & 0xFFFFFF, color, 255, true);
        return switch (ShaderFogModule.style) {
            case SPACE -> ShaderFogModule.shade(0x3A3F4E, color, 255, true);
            case DEEP_SEA -> ShaderFogModule.shade(0x3D5E6C, color, 255, true);
            case COLOR -> color;
        };
    }
}
