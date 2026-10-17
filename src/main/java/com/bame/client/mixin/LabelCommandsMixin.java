package com.bame.client.mixin;

import com.bame.client.module.NametagsModule;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Nametags: Größe. Die Unterkante bleibt an der Vanilla-Stelle, größere Nametags wachsen nach oben. */
@Mixin(targets = "net.minecraft.client.render.command.LabelCommandRenderer$Commands")
public class LabelCommandsMixin {
    /** Höhe einer Textzeile (9 px) in Weltkoordinaten bei Vanilla-Größe 0.025. */
    private static final float LINE_HEIGHT = 9.0F * 0.025F;

    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"))
    private void caeser$nametagScale(MatrixStack matrices, float x, float y, float z) {
        if (NametagsModule.enabled && NametagsModule.scale != 1.0f) {
            float s = NametagsModule.scale;
            matrices.translate(0.0F, (s - 1.0F) * LINE_HEIGHT, 0.0F);
            matrices.scale(x * s, y * s, z * s);
        } else {
            matrices.scale(x, y, z);
        }
    }
}
