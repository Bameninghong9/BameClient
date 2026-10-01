package com.bame.client.mixin;

import com.bame.client.module.BlockOutlineModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
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
}
