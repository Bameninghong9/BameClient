package com.bame.client.mixin;

import com.bame.client.render.FireRecolor;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom Fire: merkt sich die Feuer-Animationen, sobald der Atlas sie anlegt (Start und jedes Neuladen). */
@Mixin(SpriteContents.class)
public class SpriteContentsMixin {
    @Inject(method = "createAnimator", at = @At("RETURN"))
    private void caeser$trackFire(GpuBufferSlice bufferSlice, int animationInfoSize, CallbackInfoReturnable<SpriteContents.Animator> cir) {
        SpriteContents.Animator animator = cir.getReturnValue();
        if (animator != null) FireRecolor.register((SpriteContents) (Object) this, animator);
    }
}
