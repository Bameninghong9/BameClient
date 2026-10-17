package com.bame.client.mixin;

import com.bame.client.module.NametagsModule;
import net.minecraft.client.render.command.LabelCommandRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Nametags: Hintergrundfarbe/-deckkraft und Textschatten (gilt für die durchscheinende und die normale Schicht). */
@Mixin(LabelCommandRenderer.class)
public class LabelCommandRendererMixin {
    private static final String DRAW = "Lnet/minecraft/client/font/TextRenderer;draw(Lnet/minecraft/text/Text;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)V";

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = DRAW), index = 8)
    private int caeser$nametagBackground(int backgroundColor) {
        return NametagsModule.enabled ? NametagsModule.background(backgroundColor) : backgroundColor;
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = DRAW), index = 4)
    private boolean caeser$nametagShadow(boolean shadow) {
        return shadow || (NametagsModule.enabled && NametagsModule.textShadow);
    }
}
