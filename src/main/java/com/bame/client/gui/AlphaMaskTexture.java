package com.bame.client.gui;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

/**
 * A small white texture with a fixed alpha mask, drawn tinted through {@code RenderPipelines.GUI_TEXTURED}
 * (output = vertex colour with alpha multiplied by the texel alpha). Created lazily on the render thread the first
 * time {@link #id()} is called; if the texture was closed or replaced in the {@link TextureManager} it is rebuilt.
 * NativeImageBackedTexture is not a ReloadableTexture, so a resource reload (F3+T) leaves it untouched; it uses
 * NEAREST filtering without mipmaps, so every texel maps 1:1 onto a GUI pixel.
 */
public final class AlphaMaskTexture {
    private final Identifier id;
    private final int width;
    private final int height;
    private final Supplier<int[]> alphaSupplier;

    private NativeImageBackedTexture texture;
    private GpuTexture gpuTexture;
    private TextureManager owner;

    /** {@code alphaSupplier} returns width * height alpha values (0-255, row-major); it is only called when (re)building. */
    public AlphaMaskTexture(Identifier id, int width, int height, Supplier<int[]> alphaSupplier) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.alphaSupplier = alphaSupplier;
    }

    /** Makes sure the texture is registered and returns its id. Render thread only. */
    public Identifier id() {
        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        if (texture == null || owner != textureManager || gpuTexture.isClosed()) {
            int[] alpha = alphaSupplier.get();
            NativeImage image = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setColorArgb(x, y, (alpha[y * width + x] << 24) | 0x00FFFFFF);
                }
            }
            texture = new NativeImageBackedTexture(id::toString, image);
            textureManager.registerTexture(id, texture);
            gpuTexture = texture.getGlTexture();
            owner = textureManager;
        }
        return id;
    }
}
