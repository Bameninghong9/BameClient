package com.bame.client.mixin;

import com.bame.client.module.NoFogModule;
import com.mojang.blaze3d.buffers.Std140Builder;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.world.ClientWorld;

@Mixin(value = FogRenderer.class, priority = 500)
public class FogRendererMixin {
    @ModifyVariable(
        method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"),
        ordinal = 0
    )
    private FogData onModifyFogData(FogData fogData, Camera camera, int fogMode, RenderTickCounter tickCounter, float viewDistance, ClientWorld world) {
        if (!shaderFogHaze(camera, world) && NoFogModule.shouldDisableFog(camera, world)) {
            fogData.environmentalStart = 500000.0f;
            fogData.environmentalEnd = 1000000.0f;
            fogData.renderDistanceStart = 500000.0f;
            fogData.renderDistanceEnd = 1000000.0f;
            fogData.skyEnd = 1000000.0f;
            fogData.cloudEnd = 1000000.0f;
        }
        return fogData;
    }

    /** Shader Fog greift nur in der Oberwelt an der Luft (nicht unter Wasser, in Lava oder Pulverschnee). */
    private static boolean shaderFogApplies(Camera camera, ClientWorld world) {
        return com.bame.client.module.ShaderFogModule.enabled && world != null && camera != null
                && world.getDimension().skybox() == net.minecraft.world.dimension.DimensionType.Skybox.OVERWORLD
                && camera.getSubmersionType() == net.minecraft.block.enums.CameraSubmersionType.NONE;
    }

    /** Shader Fog mit Dunst (Dichte über 0): dann wird der Nebel nicht von No Fog abgeschaltet. */
    private static boolean shaderFogHaze(Camera camera, ClientWorld world) {
        return com.bame.client.module.ShaderFogModule.fogDensity > 0.0f && shaderFogApplies(camera, world);
    }

    /**
     * Shader Fog: Dunst in der eigenen Farbe. Helligkeit wie Vanilla, damit Nacht, Blindheit und Gewitter dunkel
     * bleiben. Der Vektor ist auch die Hintergrundfarbe des Bildes, deshalb geht der Horizont nahtlos über.
     */
    @ModifyVariable(
        method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
        at = @At("STORE"), ordinal = 0
    )
    private Vector4f caeser$skyFogColor(Vector4f color, Camera camera, int viewDistance, RenderTickCounter tickCounter, float f, ClientWorld world) {
        if (color == null || !com.bame.client.module.ShaderFogModule.colorFog || !shaderFogApplies(camera, world)) return color;
        int vanilla = 0xFF000000 | (Math.clamp(Math.round(color.x * 255f), 0, 255) << 16)
                | (Math.clamp(Math.round(color.y * 255f), 0, 255) << 8) | Math.clamp(Math.round(color.z * 255f), 0, 255);
        int shaded = com.bame.client.module.ShaderFogModule.shade(com.bame.client.module.ShaderFogModule.fogColor & 0xFFFFFF, vanilla, 255, true);
        color.set(((shaded >> 16) & 255) / 255f, ((shaded >> 8) & 255) / 255f, (shaded & 255) / 255f, color.w);
        return color;
    }

    @Inject(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void onApplyFogBuffer(ByteBuffer byteBuffer, int position, Vector4f fogColor, float environmentalStart, float environmentalEnd, float renderDistanceStart, float renderDistanceEnd, float skyEnd, float cloudEnd, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        boolean haze = client.gameRenderer != null && shaderFogHaze(client.gameRenderer.getCamera(), client.world);
        if (haze) {
            // Shader Fog (hat in der Oberwelt an der Luft Vorrang vor No Fog): Dunst über der ganzen Landschaft
            // (atmosphärischer Nebel ab der Kamera, bei voller Dichte ab 70 % der Sichtweite deckend) und ein
            // weicherer Übergang am Rand der Sichtweite. Hier gesetzt, weil die Werte vorher schon ausgelesen sind.
            float density = Math.clamp(com.bame.client.module.ShaderFogModule.fogDensity, 0.0f, 1.0f);
            byteBuffer.position(position);
            Std140Builder.intoBuffer(byteBuffer)
                    .putVec4(fogColor)
                    .putFloat(Math.min(environmentalStart, 0.0f))
                    .putFloat(Math.min(environmentalEnd, renderDistanceEnd * (3.0f - 2.3f * density)))
                    .putFloat(Math.min(renderDistanceStart, renderDistanceEnd * (1.0f - 0.85f * density)))
                    .putFloat(renderDistanceEnd)
                    .putFloat(skyEnd)
                    .putFloat(cloudEnd);
            ci.cancel();
            return;
        }
        if (NoFogModule.shouldDisableFog()) {
            byteBuffer.position(position);
            Std140Builder builder = Std140Builder.intoBuffer(byteBuffer);
            builder.putVec4(fogColor);
            builder.putFloat(500000.0f);
            builder.putFloat(1000000.0f);
            builder.putFloat(500000.0f);
            builder.putFloat(1000000.0f);
            builder.putFloat(1000000.0f);
            builder.putFloat(1000000.0f);
            ci.cancel();
        }
    }
}
