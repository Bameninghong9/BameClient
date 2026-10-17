package com.bame.client.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Custom Fire: die GPU-Texturen der einzelnen Animationsbilder, aus denen der Atlas jeden Tick kopiert wird. */
@Mixin(targets = "net.minecraft.client.texture.SpriteContents$Animator")
public interface SpriteAnimatorAccessor {
    @Accessor("textureViewsByFrame")
    Int2ObjectMap<GpuTextureView> caeser$getTextureViewsByFrame();
}
