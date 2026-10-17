package com.bame.client.mixin;

import com.bame.client.render.HideArmorHooks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * „Hide Armor“: {@code ArmorFeatureRenderer.render} zeichnet die vier Slots über {@code renderArmor}; jeder Aufruf
 * wird umhüllt und für ausgeblendete Slots von Spielern übersprungen. Der Render-State (Ausrüstung) bleibt
 * unverändert, damit Capes, Cosmetics und alle anderen Renderer dieselben Daten wie in Vanilla sehen.
 */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    @WrapOperation(
            method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/feature/ArmorFeatureRenderer;renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V")
    )
    private void caeser$hideArmorPiece(ArmorFeatureRenderer<?, ?, ?> instance, MatrixStack matrices, OrderedRenderCommandQueue queue,
                                       ItemStack stack, EquipmentSlot slot, int light, BipedEntityRenderState state,
                                       Operation<Void> original) {
        if (HideArmorHooks.hidesArmor(state, slot)) return;
        original.call(instance, matrices, queue, stack, slot, light, state);
    }
}
