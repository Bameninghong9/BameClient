package com.bame.client.mixin;

import com.bame.client.module.ShaderFogModule;
import com.bame.client.render.ShaderFogRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.MoonPhase;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shader Fog: Shader-Himmel bzw. eigene Himmelsfarbe, ausgeblendete Sonne/Mond im Oberwelt-Himmel. */
@Mixin(SkyRendering.class)
public class SkyRenderingMixin {
    /** Hellster Kanal des Vanilla-Taghimmels (0x78A7FF): Bezug für den Tag-Nacht-Wechsel. */
    private static final int DAY_SKY_MAX = 255;

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void caeser$shaderFogSky(ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (!ShaderFogModule.enabled || state.skybox != DimensionType.Skybox.OVERWORLD) return;
        if (ShaderFogModule.mode == ShaderFogModule.Mode.COLOR) {
            state.skyColor = ShaderFogModule.shade(ShaderFogModule.rgb(), state.skyColor, DAY_SKY_MAX, ShaderFogModule.dayNightCycle);
        }
        if (ShaderFogModule.stars > 0.0f) state.starBrightness = Math.max(state.starBrightness, ShaderFogModule.stars);
    }

    /** Nach der Himmelsscheibe: der animierte Shader-Himmel (nur in der Oberwelt, das End hat keine Himmelsscheibe). */
    @Inject(method = "renderTopSky", at = @At("TAIL"))
    private void caeser$shaderSky(int color, CallbackInfo ci) {
        if (ShaderFogModule.enabled && ShaderFogModule.mode.shader) ShaderFogRenderer.drawSky(ShaderFogModule.mode, color);
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
    private void caeser$hideSunset(MatrixStack matrices, float solarAngle, int color, CallbackInfo ci) {
        if (ShaderFogModule.enabled && (ShaderFogModule.hideSunMoon || ShaderFogModule.mode.shader)) ci.cancel();
    }

    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void caeser$hideSun(float alpha, MatrixStack matrices, CallbackInfo ci) {
        if (ShaderFogModule.enabled && ShaderFogModule.hideSunMoon) ci.cancel();
    }

    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void caeser$hideMoon(MoonPhase moonPhase, float alpha, MatrixStack matrices, CallbackInfo ci) {
        if (ShaderFogModule.enabled && ShaderFogModule.hideSunMoon) ci.cancel();
    }
}
