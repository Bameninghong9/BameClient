package com.bame.client.emotes;

import com.bame.client.module.EmoteModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Kamera während der eigenen Liegestütze (nur lokal, nur Darstellung). Wird vom {@code CameraMixin} in
 * {@code Camera.update} abgefragt; andere Emotes sind nicht betroffen.
 * <p>
 * Problem vorher: In der Ego-Perspektive blieb die Kamera auf Augenhöhe im Stand, während der Körper flach am Boden
 * lag; man schaute aus nächster Nähe von oben auf den eigenen Rücken. Jetzt:
 * <ul>
 *   <li>Ego-Perspektive: für die Dauer der Liegestütze eine normale Kamera von hinten (Vanilla-Abstand), deren
 *       Drehpunkt auf den liegenden Rumpf abgesenkt ist; mindestens 20° Blick nach unten. Der Übergang zoomt in
 *       0,6 s heraus; am Ende springt die Kamera wie gewohnt zurück in die Ego-Perspektive.</li>
 *   <li>Selbst gewählte 3rd-Person (hinten/vorne): gleicher abgesenkter Drehpunkt, mindestens 10° Neigung, damit die
 *       Kamera nicht unter den Boden zielt.</li>
 * </ul>
 * Die Hängematte nutzt dieselbe Kamera: aus der Ego-Perspektive eine 3rd-Person-Kamera, Drehpunkt über dem liegenden
 * Körper (fest in der Welt, schaukelt nicht mit), Übergang 0,8 s, mindestens 15° bzw. 8° Neigung. Schaukel und
 * Picknick ebenso: Drehpunkt über dem Sitz bzw. über dem sitzenden Spieler (folgt nicht dem Schaukeln). Beim Hexenbesen
 * liegt der Drehpunkt fest über der Mitte der Kreisbahn (die Kamera kreist nicht mit, die echte Position bleibt).
 * Der fliegende Teppich hat keine eigene Kamera: in der selbst gewählten 3rd-Person liegt nur der Drehpunkt um so viel
 * höher, wie der Kopf des schwebenden Reiters über seiner normalen Höhe ist (nie über eine Decke hinaus).
 */
public final class EmoteCamera {

    private EmoteCamera() {}

    /** Laufen gerade die eigenen Liegestütze und ist der lokale Spieler die Kamera? */
    public static boolean isPushUpCamera(Entity focused) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return focused != null && focused == mc.player && EmoteModule.isEmoting()
                && EmoteModule.getCurrentEmote() == EmoteType.PUSH_UPS;
    }

    /** Läuft gerade die eigene Hängematte, Schaukel oder das Picknick und ist der lokale Spieler die Kamera? */
    public static boolean isHammockCamera(Entity focused) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return focused != null && focused == mc.player && EmoteModule.isEmoting() && isPropEmote(EmoteModule.getCurrentEmote());
    }

    /** Emotes mit weltfesten Props und Kamera wie bei der Hängematte. */
    private static boolean isPropEmote(EmoteType e) {
        return e == EmoteType.HAMMOCK || e == EmoteType.SWING || e == EmoteType.PICNIC || e == EmoteType.WITCH_BROOM;
    }

    /** Liegestütze oder Hängematte mit eigener Kameraführung? */
    private static boolean isEmoteCamera(Entity focused) {
        return isPushUpCamera(focused) || isHammockCamera(focused);
    }

    /** Ego-Perspektive gewählt, aber während der Liegestütze bzw. Hängematte eine 3rd-Person-Kamera erzwungen? */
    public static boolean forcesThirdPerson() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.options != null && mc.options.getPerspective().isFirstPerson() && isEmoteCamera(mc.getCameraEntity());
    }

    /** Übergang 0..1 ab Emote-Start. */
    public static float blend() {
        float t = EmoteModule.getElapsedSeconds();
        return isPropEmote(EmoteModule.getCurrentEmote()) ? HammockMotion.cameraBlend(t) : PushUpMotion.cameraBlend(t);
    }

    /**
     * Ist die Kamera schon weit genug vom Kopf weg, dass der Kopf des eigenen Modells gezeigt werden soll? Sonst würde
     * man in den ersten Frames des Herauszoomens in den eigenen Kopf schauen.
     */
    public static boolean showsOwnHead() {
        return forcesThirdPerson() && blend() > 0.35F;
    }

    /** Faktor für den Kamera-Abstand: aus der Ego-Perspektive weich herauszoomen, sonst unverändert. */
    public static float distanceFactor(Entity focused) {
        if (!isEmoteCamera(focused)) return 1.0F;
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.options.getPerspective().isFirstPerson() ? blend() : 1.0F;
    }

    /**
     * Abgesenkter Drehpunkt in Weltkoordinaten, schon mit dem Übergang von der bisherigen Kameraposition gemischt, oder
     * {@code null}, wenn nichts zu tun ist.
     */
    public static Vec3d pivot(Entity focused, Vec3d current, float tickProgress) {
        if (isHammockCamera(focused)) {
            // Hängematte: fester Punkt über dem liegenden Körper (Welt), unabhängig vom Körper-Yaw und vom Schaukeln
            var self = MinecraftClient.getInstance().player;
            EmoteType e = EmoteModule.getCurrentEmote();
            double[] h = e == EmoteType.SWING ? SwingCoordinator.localCameraPivot(self)
                    : e == EmoteType.PICNIC ? PicnicCoordinator.localCameraPivot(self)
                    : e == EmoteType.WITCH_BROOM ? WitchBroomCoordinator.localCameraPivot(self)
                    : HammockCoordinator.localCameraPivot(self);
            if (h == null) return null;
            float hb = blend();
            return new Vec3d(MathHelper.lerp(hb, current.x, h[0]), MathHelper.lerp(hb, current.y, h[1]),
                    MathHelper.lerp(hb, current.z, h[2]));
        }
        // Fliegender Teppich: Drehpunkt um die Anhebung des Kopfes höher, damit der schwebende Spieler im Bild bleibt
        var player = MinecraftClient.getInstance().player;
        if (focused != null && focused == player && !isPushUpCamera(focused)) {
            double lift = FlyingCarpetCoordinator.localHeadLift(player);
            if (lift > 1.0E-4) {
                // Der Kopf des Reiters duckt sich beim Schleichen nicht: von der normalen Augenhöhe aus anheben
                double eye = MathHelper.lerp(tickProgress, focused.lastY, focused.getY())
                        + player.getDimensions(EntityPose.STANDING).eyeHeight();
                double y = Math.max(current.y, eye + lift);
                // Nie über eine Decke hinaus (sonst stünde die Kamera im Block)
                HitResult hit = focused.getEntityWorld().raycast(new RaycastContext(current,
                        new Vec3d(current.x, y + 0.25, current.z), RaycastContext.ShapeType.VISUAL,
                        RaycastContext.FluidHandling.NONE, focused));
                if (hit.getType() != HitResult.Type.MISS) y = Math.min(y, Math.max(current.y, hit.getPos().y - 0.25));
                if (y > current.y + 1.0E-4) return new Vec3d(current.x, y, current.z);
            }
            return null;
        }
        if (!isPushUpCamera(focused) || !(focused instanceof LivingEntity living)) return null;
        float t = EmoteModule.getElapsedSeconds();
        float b = PushUpMotion.cameraBlend(t);
        float[] p = PushUpMotion.cameraPivot(t);
        double scale = living.getScale();
        double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(tickProgress, living.lastBodyYaw, living.bodyYaw));
        double x = MathHelper.lerp(tickProgress, focused.lastX, focused.getX()) - Math.sin(yaw) * p[1] * scale;
        double y = MathHelper.lerp(tickProgress, focused.lastY, focused.getY()) + p[0] * scale;
        double z = MathHelper.lerp(tickProgress, focused.lastZ, focused.getZ()) + Math.cos(yaw) * p[1] * scale;
        return new Vec3d(MathHelper.lerp(b, current.x, x), MathHelper.lerp(b, current.y, y), MathHelper.lerp(b, current.z, z));
    }

    // ------------------------------------------------------------------
    // High-Five: kleiner Kamera-Ruck beim eigenen Klatsch
    // ------------------------------------------------------------------

    /** Dauer (ms) und Stärke (Grad) des Rucks. */
    public static final long KICK_MS = 320L;
    public static final float KICK_DEG = 1.1F;
    private static long kickStart;

    /** Beim eigenen High-Five-Klatsch aufgerufen (nur der lokale Spieler, nur Darstellung). */
    public static void kick() {
        kickStart = System.currentTimeMillis();
    }

    /**
     * Versatz {yaw, pitch} in Grad für die Kamera oder {@code null}. Gedämpfte Schwingung; skaliert mit der
     * Vanilla-Barrierefreiheits-Option „Neigung bei Schaden“ (0 % = aus).
     */
    public static float[] kickOffset() {
        if (kickStart == 0L) return null;
        long d = System.currentTimeMillis() - kickStart;
        if (d < 0L || d >= KICK_MS) {
            kickStart = 0L;
            return null;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        double strength = mc.options != null ? mc.options.getDamageTiltStrength().getValue() : 1.0;
        if (strength <= 0.001) return null;
        double s = d / 1000.0;
        double env = Math.exp(-d / 90.0) * (1.0 - d / (double) KICK_MS) * strength * KICK_DEG;
        float pitch = (float) (-Math.sin(s * Math.PI * 2.0 * 11.0) * env);
        float yaw = (float) (Math.sin(s * Math.PI * 2.0 * 8.0) * env * 0.45);
        return new float[]{yaw, pitch};
    }

    /** Neigung der Kamera (Grad, positiv = nach unten) mit Mindestwert, weich eingeblendet. */
    public static float pitch(float pitch) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Nur Liegestütze und Prop-Emotes mit eigener Kamera (beim fliegenden Teppich ist nur der Drehpunkt angehoben)
        if (!isEmoteCamera(mc.getCameraEntity())) return pitch;
        boolean first = mc.options.getPerspective().isFirstPerson();
        float min;
        if (isPropEmote(EmoteModule.getCurrentEmote())) {
            min = first ? HammockMotion.CAMERA_MIN_PITCH_FROM_FIRST_PERSON : HammockMotion.CAMERA_MIN_PITCH_THIRD_PERSON;
        } else {
            min = first ? PushUpMotion.CAMERA_MIN_PITCH_FROM_FIRST_PERSON : PushUpMotion.CAMERA_MIN_PITCH_THIRD_PERSON;
        }
        if (pitch >= min) return pitch;
        return MathHelper.lerp(blend(), pitch, min);
    }
}
