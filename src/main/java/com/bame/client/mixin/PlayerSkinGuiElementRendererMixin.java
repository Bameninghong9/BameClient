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
        com.bame.client.util.Prof.push("caeser_gui_skin_preview");
        try {
            caeser$renderCosmetics(state, matrices);
        } finally {
            com.bame.client.util.Prof.pop();
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private void caeser$renderCosmetics(PlayerSkinGuiElementRenderState state, MatrixStack matrices) {
        String cardCosmetic = CosmeticsTab.CARD_MODEL_MAP.get(state.playerModel());
        if (cardCosmetic != null) {
            CaeserCosmeticsRenderer.renderCardCosmeticStandalone(matrices, this.vertexConsumers, cardCosmetic, 15728880);
        } else if (CosmeticsTab.isSkinCardModel(state.playerModel())) {
            // It's a skin preview card: do not render cosmetics on it, just show the clean skin!
        } else {
            boolean panel = CosmeticsTab.isPanelModel(state.playerModel());
            if (panel) {
                // Vorschau-Spieler im Cosmetics-Menü: eigenes Caeser-Cape
                com.bame.client.cosmetics.CaeserCapeManager.renderPreviewCape(matrices, this.vertexConsumers, 15728880);
            }
            String w =CosmeticsTab.previewWings != null ? CosmeticsTab.previewWings : CosmeticsConfig.wings;
            String h = CosmeticsTab.previewHead != null ? CosmeticsTab.previewHead : CosmeticsConfig.head;
            String f = CosmeticsTab.previewFace != null ? CosmeticsTab.previewFace : CosmeticsConfig.face;
            String p = CosmeticsTab.previewPet != null ? CosmeticsTab.previewPet : CosmeticsConfig.pet;
            String bk = CosmeticsTab.previewBack != null ? CosmeticsTab.previewBack : CosmeticsConfig.back;
            // Rücken-Item steht ab, wenn am Vorschau-Spieler das eigene Cape gezeichnet wird
            boolean capeShown = panel && com.bame.client.cosmetics.CaeserCapeManager.ownTexture() != null;
            CaeserCosmeticsRenderer.renderCosmeticsDirect(matrices, this.vertexConsumers, state.playerModel(), 15728880, w, h, f, p, bk, capeShown);
        }
    }
}
