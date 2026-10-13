package com.bame.client.mixin;

import com.bame.client.emotes.EmoteCamera;
import com.bame.client.module.FreelookModule;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    private Vec3d pos;

    @Shadow
    private float pitch;

    @Shadow
    private float yaw;

    @Redirect(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V")
    )
    private void onSetRotation(Camera camera, float yaw, float pitch) {
        if (FreelookModule.isActive()) {
            this.setRotation(FreelookModule.getYaw(), FreelookModule.getPitch());
        } else {
            this.setRotation(yaw, pitch);
        }
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void onIsThirdPerson(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (com.bame.client.module.EmoteModule.isEmoting()) {
            cir.setReturnValue(true);
        }
    }

    /** Liegestütze aus der Ego-Perspektive: Kamera wie 3rd-Person von hinten führen (nur Darstellung, siehe EmoteCamera). */
    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean caeser$pushUpThirdPerson(boolean thirdPerson) {
        return thirdPerson || EmoteCamera.forcesThirdPerson();
    }

    /** Liegestütze: Drehpunkt auf den liegenden Rumpf absenken und eine Mindestneigung nach unten setzen. */
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private void caeser$pushUpPivot(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                    float tickProgress, CallbackInfo ci) {
        Vec3d pivot = EmoteCamera.pivot(focusedEntity, this.pos, tickProgress);
        if (pivot == null) return;
        this.setPos(pivot);
        float p = EmoteCamera.pitch(this.pitch);
        if (p != this.pitch) this.setRotation(this.yaw, p);
    }

    /** High-Five: kleiner Kamera-Ruck beim eigenen Klatsch (nur Darstellung, ändert nicht die Blickrichtung des Spielers). */
    @Inject(method = "update", at = @At("TAIL"))
    private void caeser$highFiveKick(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                     float tickProgress, CallbackInfo ci) {
        float[] kick = EmoteCamera.kickOffset();
        if (kick == null) return;
        this.setRotation(this.yaw + kick[0], this.pitch + kick[1]);
    }

    /** Liegestütze aus der Ego-Perspektive: beim Start weich auf den normalen Abstand herauszoomen. */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private float caeser$pushUpDistance(float distance) {
        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        return distance * EmoteCamera.distanceFactor(mc.getCameraEntity());
    }
}
