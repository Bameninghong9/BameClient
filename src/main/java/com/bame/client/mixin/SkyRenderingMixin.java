package com.bame.client.mixin;

import com.bame.client.module.CustomSkyModule;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.state.SkyRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Sky: Himmelsfarbe und Sterne im Oberwelt-Himmel. */
@Mixin(SkyRendering.class)
public class SkyRenderingMixin {
    /** Hellster Kanal des Vanilla-Taghimmels (0x78A7FF): Bezug für den Tag-Nacht-Wechsel. */
    private static final int DAY_SKY_MAX = 255;

    @Inject(method = "updateRenderState", at = @At("TAIL"))
    private void caeser$customSky(ClientWorld world, float tickProgress, Camera camera, SkyRenderState state, CallbackInfo ci) {
        if (!CustomSkyModule.enabled || state.skybox != DimensionType.Skybox.OVERWORLD) return;
        state.skyColor = CustomSkyModule.shade(CustomSkyModule.skyRgb(), state.skyColor, DAY_SKY_MAX);
        if (CustomSkyModule.stars > 0.0f) state.starBrightness = Math.max(state.starBrightness, CustomSkyModule.stars);
    }
}
