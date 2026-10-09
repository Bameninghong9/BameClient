package com.bame.client.mixin;

import com.bame.client.cosmetics.CaeserCosmeticsRenderer;
import com.bame.client.cosmetics.CosmeticsConfig;
import com.bame.client.gui.tab.CosmeticsTab;
import net.minecraft.client.gui.render.PlayerSkinGuiElementRenderer;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.PlayerSkinGuiElementRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerSkinGuiElementRenderer.class)
public abstract class PlayerSkinGuiElementRendererMixin extends SpecialGuiElementRenderer<PlayerSkinGuiElementRenderState> {

    public PlayerSkinGuiElementRendererMixin(VertexConsumerProvider.Immediate vertexConsumers) {
        super(vertexConsumers);
    }

    @Inject(
        method = "render(Lnet/minecraft/client/gui/render/state/special/PlayerSkinGuiElementRenderState;Lnet/minecraft/client/util/math/MatrixStack;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;draw()V")
    )
    private void renderCosmeticsOnSkinWidget(PlayerSkinGuiElementRenderState state, MatrixStack matrices, CallbackInfo ci) {
        String cardCosmetic = CosmeticsTab.CARD_MODEL_MAP.get(state.playerModel());
        if (cardCosmetic != null) {
            CaeserCosmeticsRenderer.renderCardCosmeticStandalone(matrices, this.vertexConsumers, cardCosmetic, 15728880);
        } else {
            String w = CosmeticsTab.previewWings != null ? CosmeticsTab.previewWings : CosmeticsConfig.wings;
            String h = CosmeticsTab.previewHead != null ? CosmeticsTab.previewHead : CosmeticsConfig.head;
            String p = CosmeticsTab.previewPet != null ? CosmeticsTab.previewPet : CosmeticsConfig.pet;
            CaeserCosmeticsRenderer.renderCosmeticsDirect(matrices, this.vertexConsumers, state.playerModel(), 15728880, w, h, p);
        }
    }
}
