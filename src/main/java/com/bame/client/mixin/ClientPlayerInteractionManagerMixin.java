package com.bame.client.mixin;

import com.bame.client.module.AutoToolModule;
import com.bame.client.module.DurabilityGuardModule;
import com.bame.client.module.ReachDisplayModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (player != null) {
            ItemStack stack = player.getMainHandStack();
            if (DurabilityGuardModule.shouldCancel(stack)) {
                DurabilityGuardModule.triggerWarning(stack);
                ci.cancel();
                return;
            }
        }
        if (ReachDisplayModule.enabled && target != null) {
            ReachDisplayModule.onAttack(target);
        }
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (AutoToolModule.enabled && pos != null) {
            AutoToolModule.onAttackBlock(pos);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            ItemStack stack = client.player.getMainHandStack();
            if (DurabilityGuardModule.shouldCancel(stack)) {
                DurabilityGuardModule.triggerWarning(stack);
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void onUpdateBlockBreakingProgress(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (AutoToolModule.enabled && pos != null) {
            AutoToolModule.onAttackBlock(pos);
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            ItemStack stack = client.player.getMainHandStack();
            if (DurabilityGuardModule.shouldCancel(stack)) {
                DurabilityGuardModule.triggerWarning(stack);
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
