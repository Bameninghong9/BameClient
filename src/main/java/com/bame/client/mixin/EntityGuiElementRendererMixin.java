package com.bame.client.mixin;

import com.bame.client.cosmetics.GuiPreviewContext;
import net.minecraft.client.gui.render.EntityGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.EntityGuiElementRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Markiert das Zeichnen von Entity-Vorschauen in Menüs (z. B. Spieler im Inventar). Die Cosmetics-Feature-Renderer
 * laufen dabei mit; über {@link GuiPreviewContext} erzeugen sie für diese Vorschau keine Welt-Partikel.
 */
@Mixin(EntityGuiElementRenderer.class)
public abstract class EntityGuiElementRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/gui/render/state/special/EntityGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("HEAD"))
    private void caeser$enterGuiPreview(EntityGuiElementRenderState state, MatrixStack matrices, CallbackInfo ci) {
        GuiPreviewContext.enter();
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/render/state/special/EntityGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
            at = @At("RETURN"))
    private void caeser$exitGuiPreview(EntityGuiElementRenderState state, MatrixStack matrices, CallbackInfo ci) {
        GuiPreviewContext.exit();
    }
}
