package com.bame.client.render;

import com.bame.client.gui.CustomGuiUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public class BlurRenderer {
    private static SimpleFramebuffer downsampledFb1 = null;
    private static SimpleFramebuffer downsampledFb2 = null;
    private static int lastWidth = 0;
    private static int lastHeight = 0;
    private static long lastUpdateTime = 0;

    public static void ensureUpdated(MinecraftClient client) {
        long now = System.currentTimeMillis();
        // Update at most once every 16ms (approx 60fps)
        if (now - lastUpdateTime < 15 && downsampledFb2 != null) {
            return;
        }
        lastUpdateTime = now;

        if (client.getFramebuffer() == null) return;
        int fbW = client.getFramebuffer().textureWidth;
        int fbH = client.getFramebuffer().textureHeight;
        if (fbW <= 0 || fbH <= 0) return;

        int downW1 = Math.max(1, fbW / 4);
        int downH1 = Math.max(1, fbH / 4);
        int downW2 = Math.max(1, fbW / 8);
        int downH2 = Math.max(1, fbH / 8);

        if (downsampledFb1 == null || lastWidth != fbW || lastHeight != fbH) {
            if (downsampledFb1 != null) downsampledFb1.delete();
            if (downsampledFb2 != null) downsampledFb2.delete();
            downsampledFb1 = new SimpleFramebuffer("bame_blur1", downW1, downH1, false);
            downsampledFb2 = new SimpleFramebuffer("bame_blur2", downW2, downH2, false);
            lastWidth = fbW;
            lastHeight = fbH;
        }

        try {
            // Blit 1: Full screen -> 1/4 size
            client.getFramebuffer().drawBlit(downsampledFb1.getColorAttachmentView());
            // Blit 2: 1/4 size -> 1/8 size (multi-pass downsample creates smooth blur)
            downsampledFb1.drawBlit(downsampledFb2.getColorAttachmentView());
        } catch (Throwable t) {
            // Fallback gracefully if graphics pipeline encounters issues
        }
    }

    public static void drawBlurBox(DrawContext context, int x, int y, int w, int h) {
        MinecraftClient client = MinecraftClient.getInstance();
        ensureUpdated(client);

        if (downsampledFb2 != null) {
            try {
                GpuTextureView view = downsampledFb2.getColorAttachmentView();
                GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);

                Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
                // Transform local (x, y) and (x + w, y + h) into screen coordinates
                float sx1 = pose.m00 * x + pose.m10 * y + pose.m20;
                float sy1 = pose.m01 * x + pose.m11 * y + pose.m21;
                float sx2 = pose.m00 * (x + w) + pose.m10 * (y + h) + pose.m20;
                float sy2 = pose.m01 * (x + w) + pose.m11 * (y + h) + pose.m21;

                float sw = (float) client.getWindow().getScaledWidth();
                float sh = (float) client.getWindow().getScaledHeight();

                float u1 = Math.clamp(Math.min(sx1, sx2) / sw, 0f, 1f);
                float u2 = Math.clamp(Math.max(sx1, sx2) / sw, 0f, 1f);
                // In OpenGL framebuffers, top is v=1.0 and bottom is v=0.0
                float v1 = Math.clamp(1.0f - Math.min(sy1, sy2) / sh, 0f, 1f);
                float v2 = Math.clamp(1.0f - Math.max(sy1, sy2) / sh, 0f, 1f);

                // Add blurred background quad
                context.state.addSimpleElement(new TexturedQuadGuiElementRenderState(
                    RenderPipelines.GUI_TEXTURED,
                    TextureSetup.of(view, sampler),
                    pose,
                    x, y, x + w, y + h,
                    u1, v1, u2, v2,
                    0xFFFFFFFF,
                    context.scissorStack.peekLast()
                ));
            } catch (Throwable ignored) {
            }
        }

        // Frosted glass tint overlay (matches Bild 3 perfectly!)
        CustomGuiUtils.fillUltraRounded(context, x, y, w, h, 0x4B121824, 4);
        // Frosted glass edge outline
        CustomGuiUtils.drawUltraRoundedOutline(context, x, y, w, h, 0x30FFFFFF, 4);
    }
}
