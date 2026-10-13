package com.bame.client.mixin;

import com.bame.client.killfx.KillDetector;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kill-Effekte: Entity-Status 3 (Tod) und 35 (Totem) für die Kill-Erkennung mitlesen. Status 35 wird vom
 * Netzwerk-Handler selbst behandelt und erreicht {@code LivingEntity.handleStatus} nicht, daher hier. Nur lesend, die
 * Vanilla-Verarbeitung bleibt unverändert. Läuft nach {@code forceMainThread} (also auf dem Haupt-Thread).
 */
@Mixin(ClientPlayNetworkHandler.class)
public class KillFxEntityStatusMixin {
    @Inject(method = "onEntityStatus", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/packet/s2c/play/EntityStatusS2CPacket;getEntity(Lnet/minecraft/world/World;)Lnet/minecraft/entity/Entity;"))
    private void caeser$killFxEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;
            Entity entity = packet.getEntity(mc.world);
            if (entity != null) KillDetector.onEntityStatus(entity, packet.getStatus());
        } catch (Throwable ignored) {}
    }
}
