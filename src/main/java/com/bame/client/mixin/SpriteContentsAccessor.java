package com.bame.client.mixin;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Custom Fire: Original-Pixel (alle Animationsbilder, je Mipmap-Stufe) einer Sprite-Textur. */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
    @Accessor("mipmapLevelsImages")
    NativeImage[] caeser$getMipmapLevelsImages();
}
