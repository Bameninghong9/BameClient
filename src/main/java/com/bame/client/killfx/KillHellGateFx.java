package com.bame.client.killfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Hell Gate (3,9 s): Unter dem Opfer öffnet sich in 0,5 s ein brennender Runenkreis (drehender Runenring, glühender
 * Rand, Flammen und Glut) mit einem dunklen, glühenden Schlund (Strudel, in die Tiefe laufende Ringe, aufsteigende Glut
 * und Rauch). Fünf rote Dämonenarme brechen nacheinander hervor, packen die zappelnde Figur (Vanilla-Spielermodell mit
 * Skin und Rüstung) und ziehen sie ruckartig in den Schlund. Dann schließt sich das Tor schnell mit einem Knall
 * (Lichtblitz, Funkenring, Rauchstoß); zurück bleibt ein verkohlter Brandfleck, der verblasst.
 */
final class KillHellGateFx extends KillWorldEffects.Active {

    private static final Identifier RUNES = Identifier.of("bameclient", "textures/entity/kill_hellgate_runes.png");
    private static final Identifier PIT = Identifier.of("bameclient", "textures/entity/kill_hellgate_pit.png");
    private static final Identifier SWIRL = Identifier.of("bameclient", "textures/entity/kill_hellgate_swirl.png");
    private static final Identifier RING = Identifier.of("bameclient", "textures/entity/kill_hellgate_ring.png");
    private static final Identifier GLOW = Identifier.of("bameclient", "textures/entity/kill_hellgate_glow.png");
    private static final Identifier ARM = Identifier.of("bameclient", "textures/entity/kill_hellgate_arm.png");
    private static final Identifier ARM_GLOW = Identifier.of("bameclient", "textures/entity/kill_hellgate_arm_glow.png");
    private static final Identifier SHADOW = Identifier.ofVanilla("textures/misc/shadow.png");
    private static final int FULL_BRIGHT = 0x00F000F0;

    private final KillVictims.Figure figure;
    private final KillFigureState state = new KillFigureState();
    private final float bodyYaw;
    /** Drehung des Hand-Kranzes (zufällig pro Effekt). */
    private final float baseRad;
    /** Achse senkrecht zur Blickrichtung (zum Kippen der Figur zur Seite). */
    private final float tiltAxisX, tiltAxisZ;
    private final boolean[] emerged = new boolean[KillFxTimeline.HELLGATE_HANDS];
    private final boolean[] grabbed = new boolean[KillFxTimeline.HELLGATE_HANDS];
    private boolean growl, pullSfx, closeSfx, bang;

    KillHellGateFx(Vec3d pos, Vec3d viewer, KillVictims.Figure figure) {
        super(pos);
        this.figure = figure;
        double dx = viewer.x - pos.x;
        double dz = viewer.z - pos.z;
        this.bodyYaw = (dx * dx + dz * dz) < 1.0E-4 ? 0.0F : (float) Math.toDegrees(Math.atan2(-dx, dz));
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) {
            dx = 0.0;
            dz = 1.0;
            len = 1.0;
        }
        this.tiltAxisX = (float) (dx / len);
        this.tiltAxisZ = (float) (dz / len);
        this.baseRad = random.nextFloat() * (float) (Math.PI * 2.0);
        state.x = pos.x;
        state.y = pos.y;
        state.z = pos.z;
        state.capeVisible = true;
    }

    // ==========================================
    // Ticks: Sounds und Partikel
    // ==========================================

    @Override
    boolean tick(MinecraftClient mc, ClientWorld world) {
        long ms = elapsedMs();
        if (age == 0) {
            // Das Tor reißt auf
            KillWorldEffects.sound(world, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7F, 0.55F);
            KillWorldEffects.sound(world, pos, SoundEvents.ITEM_FIRECHARGE_USE, 0.45F, 0.6F);
            for (int i = 0; i < 14; i++) {
                double a = Math.PI * 2.0 * i / 14.0;
                world.addParticleClient(ParticleTypes.FLAME, pos.x + Math.cos(a) * 0.3, pos.y + 0.05, pos.z + Math.sin(a) * 0.3,
                        Math.cos(a) * 0.09, 0.03, Math.sin(a) * 0.09);
            }
        }
        if (ms < KillFxTimeline.HELLGATE_BANG_MS) {
            float r = KillFxTimeline.gateRadius(ms);
            if (r > 0.1F) {
                // Flammen am Rand, Glut und Rauch aus dem Schlund
                for (int i = 0; i < 2; i++) {
                    double a = random.nextDouble() * Math.PI * 2.0;
                    world.addParticleClient(ParticleTypes.FLAME, pos.x + Math.cos(a) * r * 0.95, pos.y + 0.05, pos.z + Math.sin(a) * r * 0.95,
                            0.0, 0.015 + random.nextDouble() * 0.03, 0.0);
                }
                double a = random.nextDouble() * Math.PI * 2.0;
                double rr = Math.sqrt(random.nextDouble()) * r * 0.7;
                world.addParticleClient(ParticleTypes.SMALL_FLAME, pos.x + Math.cos(a) * rr, pos.y + 0.05, pos.z + Math.sin(a) * rr,
                        0.0, 0.05 + random.nextDouble() * 0.04, 0.0);
                if (age % 2 == 0) {
                    world.addParticleClient(ParticleTypes.SMOKE, pos.x + Math.cos(a + 1.5) * rr * 0.6, pos.y + 0.05,
                            pos.z + Math.sin(a + 1.5) * rr * 0.6, 0.0, 0.05, 0.0);
                }
                if (age % 6 == 0) {
                    world.addParticleClient(ParticleTypes.LAVA, pos.x + Math.cos(a + 3.0) * rr, pos.y + 0.05, pos.z + Math.sin(a + 3.0) * rr, 0, 0, 0);
                }
            }
            if (age % 20 == 10) KillWorldEffects.sound(world, pos, SoundEvents.BLOCK_FIRE_AMBIENT, 0.6F, 0.7F);
        }
        float[] p = new float[3];
        for (int i = 0; i < KillFxTimeline.HELLGATE_HANDS; i++) {
            if (!emerged[i] && ms >= KillFxTimeline.handStartMs(i) + 90L) {
                emerged[i] = true;
                // Arm bricht durch: Glut und Rauch an der Austrittsstelle
                float a = KillFxTimeline.handAngle(i, baseRad);
                double ex = pos.x + Math.cos(a) * 0.5, ez = pos.z + Math.sin(a) * 0.5;
                for (int k = 0; k < 5; k++) {
                    world.addParticleClient(ParticleTypes.FLAME, ex + (random.nextDouble() - 0.5) * 0.2, pos.y + 0.05,
                            ez + (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.05, 0.06 + random.nextDouble() * 0.05,
                            (random.nextDouble() - 0.5) * 0.05);
                }
                world.addParticleClient(ParticleTypes.LARGE_SMOKE, ex, pos.y + 0.1, ez, 0.0, 0.04, 0.0);
                KillWorldEffects.sound(world, pos, SoundEvents.BLOCK_LAVA_POP, 0.6F, 0.55F + random.nextFloat() * 0.2F);
                if (!growl) {
                    growl = true;
                    // tiefes, dämonisches Grollen (leise)
                    KillWorldEffects.sound(world, pos, SoundEvents.ENTITY_GHAST_SCREAM, 0.16F, 0.42F);
                }
            }
            if (!grabbed[i] && ms >= KillFxTimeline.handGrabMs(i)) {
                grabbed[i] = true;
                KillFxTimeline.hellHandPos(i, ms, baseRad, p);
                for (int k = 0; k < 3; k++) {
                    world.addParticleClient(ParticleTypes.SMALL_FLAME, pos.x + p[0], pos.y + p[1], pos.z + p[2],
                            (random.nextDouble() - 0.5) * 0.04, 0.03, (random.nextDouble() - 0.5) * 0.04);
                }
                if (i % 2 == 0) KillWorldEffects.sound(world, pos, SoundEvents.ENTITY_BLAZE_BURN, 0.25F, 0.5F);
            }
        }
        if (!pullSfx && ms >= KillFxTimeline.HELLGATE_PULL_START_MS) {
            pullSfx = true;
            KillWorldEffects.sound(world, pos, SoundEvents.BLOCK_LAVA_AMBIENT, 0.9F, 0.7F);
            KillWorldEffects.sound(world, pos, SoundEvents.BLOCK_SOUL_SAND_BREAK, 0.6F, 0.5F);
        }
        if (ms > KillFxTimeline.HELLGATE_PULL_START_MS && ms < KillFxTimeline.HELLGATE_PULL_END_MS && age % 3 == 0) {
            // Funken und Rauch um die versinkende Figur
            double a = random.nextDouble() * Math.PI * 2.0;
            world.addParticleClient(ParticleTypes.FLAME, pos.x + Math.cos(a) * 0.32, pos.y + 0.05, pos.z + Math.sin(a) * 0.32, 0.0, 0.04, 0.0);
            world.addParticleClient(ParticleTypes.SMOKE, pos.x + Math.cos(a + 2.0) * 0.3, pos.y + 0.1, pos.z + Math.sin(a + 2.0) * 0.3, 0.0, 0.05, 0.0);
        }
        if (!closeSfx && ms >= KillFxTimeline.HELLGATE_CLOSE_START_MS) {
            closeSfx = true;
            KillWorldEffects.sound(world, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), 0.6F, 0.8F);
        }
        if (!bang && ms >= KillFxTimeline.HELLGATE_BANG_MS) {
            bang = true;
            // Knall: nicht zu laut, Explosion + Wither-Schuss tief
            KillWorldEffects.sound(world, pos, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), 0.45F, 1.25F);
            KillWorldEffects.sound(world, pos, SoundEvents.ENTITY_WITHER_SHOOT, 0.3F, 0.6F);
            world.addParticleClient(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.25, pos.z, 0.0, 0.0, 0.0);
            for (int i = 0; i < 28; i++) {
                double a = Math.PI * 2.0 * i / 28.0;
                double v = 0.2 + random.nextDouble() * 0.06;
                world.addParticleClient(ParticleTypes.FLAME, pos.x + Math.cos(a) * 0.4, pos.y + 0.08, pos.z + Math.sin(a) * 0.4,
                        Math.cos(a) * v, 0.02, Math.sin(a) * v);
            }
            for (int i = 0; i < 12; i++) {
                world.addParticleClient(ParticleTypes.LAVA, pos.x + (random.nextDouble() - 0.5) * 0.8, pos.y + 0.1,
                        pos.z + (random.nextDouble() - 0.5) * 0.8, 0, 0, 0);
            }
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2.0 * i / 10.0;
                world.addParticleClient(ParticleTypes.LARGE_SMOKE, pos.x + Math.cos(a) * 0.5, pos.y + 0.1, pos.z + Math.sin(a) * 0.5,
                        Math.cos(a) * 0.06, 0.06, Math.sin(a) * 0.06);
            }
        }
        if (ms >= KillFxTimeline.HELLGATE_BANG_MS && ms < KillFxTimeline.HELLGATE_TOTAL_MS && age % 3 == 0) {
            // Brandfleck qualmt nach
            double a = random.nextDouble() * Math.PI * 2.0;
            double rr = Math.sqrt(random.nextDouble()) * 0.8;
            world.addParticleClient(ParticleTypes.SMOKE, pos.x + Math.cos(a) * rr, pos.y + 0.05, pos.z + Math.sin(a) * rr, 0.0, 0.03, 0.0);
        }
        return ms < KillFxTimeline.HELLGATE_TOTAL_MS;
    }

    // ==========================================
    // Zeichnen
    // ==========================================

    @Override
    void submit(MinecraftClient mc, MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d cam, CameraRenderState camState) {
        long ms = elapsedMs();
        if (ms >= KillFxTimeline.HELLGATE_TOTAL_MS) return;
        int light = KillWorldEffects.lightAt(mc, pos);
        // Der Schlund leuchtet: Figur und Arme sind rot-orange angestrahlt
        int lit = LightmapTextureManager.pack(Math.max(LightmapTextureManager.getBlockLightCoordinates(light), 13),
                LightmapTextureManager.getSkyLightCoordinates(light));

        matrices.push();
        matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);

        // Tor
        float radius = KillFxTimeline.gateRadius(ms);
        float glow = KillFxTimeline.gateGlow(ms);
        if (radius > 0.01F) {
            float spin = KillFxTimeline.gateSpinDeg(ms);
            disc(queue, matrices, RenderLayers.entityTranslucent(PIT), radius * 0.97F, 0.015F, spin * 0.3F, 0xFFFFFFFF, FULL_BRIGHT);
            disc(queue, matrices, RenderLayers.eyes(SWIRL), radius * 0.84F, 0.03F, -ms * 0.12F, argb(glow * 0.85F, 255, 255, 255), FULL_BRIGHT);
            // Ringe, die in die Tiefe laufen (Tunnel-Eindruck)
            for (int k = 0; k < 3; k++) {
                float s = ((ms / 800.0F) + k / 3.0F) % 1.0F;
                float a = (float) Math.sin(s * Math.PI) * 0.6F * glow;
                disc(queue, matrices, RenderLayers.eyes(RING), radius * 0.92F * (1.0F - s * 0.8F), 0.035F + k * 0.002F, 0.0F,
                        argb(a, 255, 96, 24), FULL_BRIGHT);
            }
            disc(queue, matrices, RenderLayers.eyes(RING), radius * 1.16F, 0.02F, 0.0F, argb(0.75F * glow, 255, 84, 12), FULL_BRIGHT);
            disc(queue, matrices, RenderLayers.eyes(RUNES), radius, 0.045F, spin, argb(glow, 255, 235, 210), FULL_BRIGHT);
        }

        // Lichtblitz und Funkenring beim Zuschlagen
        float flash = KillFxTimeline.bangFlash(ms);
        if (flash > 0.01F) {
            disc(queue, matrices, RenderLayers.eyes(GLOW), 0.6F + 2.0F * (1.0F - flash), 0.05F, 0.0F, argb(flash, 255, 200, 130), FULL_BRIGHT);
            disc(queue, matrices, RenderLayers.eyes(RING), 0.4F + 2.8F * (1.0F - flash), 0.055F, 0.0F, argb(flash, 255, 150, 50), FULL_BRIGHT);
            billboard(queue, matrices, cam, RenderLayers.eyes(GLOW), 0.6F, 0.5F + 1.4F * flash, argb(flash * 0.9F, 255, 210, 150));
        }

        // Brandfleck (verkohlt, anfangs mit glimmendem Rand)
        float scorch = KillFxTimeline.scorchAlpha(ms);
        if (scorch > 0.01F) {
            int a = Math.round(scorch * 255.0F);
            float r = 1.05F;
            queue.submitCustom(matrices, RenderLayers.entityShadow(SHADOW), (entry, vc) -> {
                flatVertex(vc, entry, -r, 0.012F, -r, 0.0F, 0.0F, 0xFFFFFF | (a << 24), FULL_BRIGHT);
                flatVertex(vc, entry, -r, 0.012F, r, 0.0F, 1.0F, 0xFFFFFF | (a << 24), FULL_BRIGHT);
                flatVertex(vc, entry, r, 0.012F, r, 1.0F, 1.0F, 0xFFFFFF | (a << 24), FULL_BRIGHT);
                flatVertex(vc, entry, r, 0.012F, -r, 1.0F, 0.0F, 0xFFFFFF | (a << 24), FULL_BRIGHT);
            });
            float ember = Math.max(0.0F, 1.0F - (ms - KillFxTimeline.HELLGATE_BANG_MS) / 600.0F);
            if (ember > 0.01F) {
                disc(queue, matrices, RenderLayers.eyes(PIT), 0.85F, 0.016F, 0.0F, argb(ember * 0.55F, 255, 255, 255), FULL_BRIGHT);
            }
        }

        if (ms < KillFxTimeline.HELLGATE_BANG_MS) {
            submitFigure(mc, matrices, queue, camState, ms, lit, cam);
            submitArms(matrices, queue, ms, lit);
        }
        matrices.pop();
    }

    /** Opfer-Figur (Vanilla-Spielermodell mit Rüstung), zappelt und wird in den Schlund gezogen. */
    private void submitFigure(MinecraftClient mc, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camState,
                              long ms, int light, Vec3d cam) {
        float sink = KillFxTimeline.hellSink(ms);
        if (sink >= KillFxTimeline.FIGURE_HEIGHT + 0.3F) return;
        float pop = KillFxTimeline.figurePop(ms);
        if (pop <= 0.001F) return;
        KillFigureState st = state;
        st.skinTextures = figure.skin;
        st.equippedHeadStack = figure.armor[0];
        st.equippedChestStack = figure.armor[1];
        st.equippedLegsStack = figure.armor[2];
        st.equippedFeetStack = figure.armor[3];
        st.bodyYaw = bodyYaw;
        float struggle = KillFxTimeline.hellStruggle(ms);
        st.relativeHeadYaw = 25.0F * struggle * (float) Math.sin(ms * 0.009);
        st.pitch = -25.0F * struggle;
        st.light = light;
        st.age = ms / 50.0F;
        st.armRaise = 0.0F;
        st.splat = 0.0F;
        st.flail = struggle;
        st.flailPhase = ms / 1000.0F;
        st.squaredDistanceToCamera = pos.squaredDistanceTo(cam);
        matrices.push();
        matrices.translate(0.0F, -sink, 0.0F);
        float tilt = KillFxTimeline.hellTiltDeg(ms);
        if (Math.abs(tilt) > 0.01F) {
            // seitliches Kippen (um die Blickachse des Betrachters)
            matrices.multiply(new org.joml.Quaternionf().rotationAxis((float) Math.toRadians(tilt), tiltAxisX, 0.0F, tiltAxisZ));
        }
        matrices.scale(pop, pop, pop);
        mc.getEntityRenderDispatcher().render(st, camState, 0.0, 0.0, 0.0, matrices, queue);
        matrices.pop();
    }

    // ==========================================
    // Dämonenarme
    // ==========================================

    private void submitArms(MatrixStack matrices, OrderedRenderCommandQueue queue, long ms, int light) {
        boolean any = false;
        for (int i = 0; i < KillFxTimeline.HELLGATE_HANDS; i++) any |= KillFxTimeline.handReach(i, ms) > 0.0F;
        if (!any) return;
        float pulse = 0.75F + 0.25F * (float) Math.sin(ms * 0.02);
        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(ARM), (entry, vc) -> drawArms(entry, vc, ms, light, 0xFFFFFFFF, 0.0F));
        queue.submitCustom(matrices, RenderLayers.eyes(ARM_GLOW), (entry, vc) -> drawArms(entry, vc, ms, FULL_BRIGHT,
                argb(pulse, 255, 255, 255), 0.004F));
    }

    private void drawArms(MatrixStack.Entry entry, VertexConsumer vc, long ms, int light, int color, float inflate) {
        float[] root = new float[3], hand = new float[3], elbow = new float[3];
        for (int i = 0; i < KillFxTimeline.HELLGATE_HANDS; i++) {
            if (KillFxTimeline.handReach(i, ms) <= 0.0F) continue;
            KillFxTimeline.hellHandRoot(i, baseRad, root);
            KillFxTimeline.hellHandPos(i, ms, baseRad, hand);
            KillFxTimeline.hellElbow(i, baseRad, root, hand, elbow);
            float a = KillFxTimeline.handAngle(i, baseRad);
            // Oberarm, Unterarm und Dorn am Ellbogen
            float[] up = {0.0F, 1.0F, 0.0F};
            segment(vc, entry, root, elbow, 0.105F + inflate, 0.09F + inflate, up, 0.0F, 0.0F, 0.75F, 1.0F, color, light);
            segment(vc, entry, elbow, hand, 0.09F + inflate, 0.06F + inflate, up, 0.0F, 0.0F, 0.75F, 1.0F, color, light);
            float[] out = sub(elbow, mid(root, hand));
            normalize(out);
            float[] spikeTip = {elbow[0] + out[0] * 0.17F, elbow[1] + out[1] * 0.17F + 0.04F, elbow[2] + out[2] * 0.17F};
            segment(vc, entry, elbow, spikeTip, 0.035F + inflate, 0.004F, up, 0.75F, 0.0F, 1.0F, 0.5F, color, light);
            hand(vc, entry, i, a, elbow, hand, KillFxTimeline.handCurl(i, ms), color, light, inflate);
        }
    }

    /** Hand: Handfläche zur Figur, vier Krallenfinger und Daumen; die Finger schließen sich beim Zupacken. */
    private static void hand(VertexConsumer vc, MatrixStack.Entry entry, int index, float angle, float[] elbow, float[] wrist,
                             float curl, int color, int light, float inflate) {
        float[] d = sub(wrist, elbow);
        normalize(d);
        // Handfläche zeigt zur Körperachse der Figur
        float[] n = {-(float) Math.cos(angle), 0.0F, -(float) Math.sin(angle)};
        float dot = n[0] * d[0] + n[1] * d[1] + n[2] * d[2];
        n[0] -= d[0] * dot;
        n[1] -= d[1] * dot;
        n[2] -= d[2] * dot;
        if (!normalize(n)) {
            n[0] = 0.0F;
            n[1] = 0.0F;
            n[2] = 1.0F;
        }
        float[] s = cross(d, n);
        normalize(s);
        float palmLen = 0.17F;
        float[] palmEnd = add(wrist, scale(d, palmLen));
        box(vc, entry, wrist, palmEnd, s, n, 0.075F + inflate, 0.032F + inflate, 0.088F + inflate, 0.028F + inflate,
                0.0F, 0.0F, 0.75F, 0.5F, color, light);
        float bend = 0.2F + curl * 1.2F;
        float spread = (1.0F - curl) * 0.22F;
        for (int f = 0; f < 4; f++) {
            float across = -0.07F + f * 0.0467F;
            float[] base = add(palmEnd, scale(s, across));
            float splay = (f - 1.5F) / 1.5F * spread;
            finger(vc, entry, base, d, n, s, splay, bend, new float[]{0.1F, 0.085F, 0.07F}, 0.024F + inflate, color, light);
        }
        // Daumen seitlich an der Handfläche
        float side = (index % 2 == 0) ? 1.0F : -1.0F;
        float[] tBase = add(add(wrist, scale(d, 0.06F)), scale(s, 0.09F * side));
        float[] tDir = add(add(scale(d, 0.45F), scale(s, 0.7F * side)), scale(n, 0.35F));
        normalize(tDir);
        float[] tSide = cross(tDir, n);
        normalize(tSide);
        finger(vc, entry, tBase, tDir, n, tSide, 0.0F, 0.15F + curl * 0.9F, new float[]{0.08F, 0.07F, 0.06F}, 0.026F + inflate, color, light);
    }

    /** Ein Finger aus zwei Gliedern und einer schwarzen Kralle, die sich zur Handfläche (n) krümmen. */
    private static void finger(VertexConsumer vc, MatrixStack.Entry entry, float[] base, float[] d, float[] n, float[] s,
                               float splay, float bend, float[] lengths, float width, int color, int light) {
        float[] p = base;
        float phi = 0.0F;
        for (int k = 0; k < 3; k++) {
            phi += k == 0 ? bend * 0.6F : bend;
            float[] dir = add(add(scale(d, (float) Math.cos(phi)), scale(n, (float) Math.sin(phi))), scale(s, splay));
            normalize(dir);
            float[] q = add(p, scale(dir, lengths[k]));
            boolean claw = k == 2;
            float w0 = claw ? width * 0.8F : width * (1.0F - 0.12F * k);
            float w1 = claw ? 0.003F : width * (0.88F - 0.12F * k);
            if (claw) {
                box(vc, entry, p, q, s, n, w0, w0, w1, w1, 0.75F, 0.0F, 1.0F, 0.5F, color, light);
            } else {
                box(vc, entry, p, q, s, n, w0, w0, w1, w1, 0.0F, 0.5F, 0.75F, 1.0F, color, light);
            }
            p = q;
        }
    }

    /** Armsegment von a nach b mit quadratischem, sich verjüngendem Querschnitt (halbe Breiten wa, wb). */
    private static void segment(VertexConsumer vc, MatrixStack.Entry entry, float[] a, float[] b, float wa, float wb, float[] hint,
                                float u0, float v0, float u1, float v1, int color, int light) {
        float[] d = sub(b, a);
        if (!normalize(d)) return;
        float[] s = cross(d, hint);
        if (!normalize(s)) {
            s = cross(d, new float[]{1.0F, 0.0F, 0.0F});
            normalize(s);
        }
        float[] t = cross(s, d);
        normalize(t);
        box(vc, entry, a, b, s, t, wa, wa, wb, wb, u0, v0, u1, v1, color, light);
    }

    /**
     * Quader von a nach b: Querschnitt entlang s (halbe Breite) und t (halbe Dicke), am Ende b mit eigenen Maßen
     * (Verjüngung). UV: u über den Umfang, v entlang der Länge.
     */
    private static void box(VertexConsumer vc, MatrixStack.Entry entry, float[] a, float[] b, float[] s, float[] t,
                            float sa, float ta, float sb, float tb, float u0, float v0, float u1, float v1, int color, int light) {
        float[][] ca = new float[4][], cb = new float[4][];
        float[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (int k = 0; k < 4; k++) {
            ca[k] = add(a, add(scale(s, signs[k][0] * sa), scale(t, signs[k][1] * ta)));
            cb[k] = add(b, add(scale(s, signs[k][0] * sb), scale(t, signs[k][1] * tb)));
        }
        float[] d = sub(b, a);
        normalize(d);
        // vier Seiten
        for (int k = 0; k < 4; k++) {
            int k2 = (k + 1) % 4;
            float[] nrm = add(scale(s, signs[k][0] + signs[k2][0]), scale(t, signs[k][1] + signs[k2][1]));
            normalize(nrm);
            float uu0 = u0 + (u1 - u0) * k / 4.0F, uu1 = u0 + (u1 - u0) * (k + 1) / 4.0F;
            vert(vc, entry, ca[k], uu0, v1, color, light, nrm);
            vert(vc, entry, ca[k2], uu1, v1, color, light, nrm);
            vert(vc, entry, cb[k2], uu1, v0, color, light, nrm);
            vert(vc, entry, cb[k], uu0, v0, color, light, nrm);
        }
        // Enden
        float[] nb = d, na = scale(d, -1.0F);
        float um = (u0 + u1) * 0.5F, vm = v0 + (v1 - v0) * 0.1F;
        float du = (u1 - u0) * 0.1F;
        vert(vc, entry, cb[0], um - du, vm, color, light, nb);
        vert(vc, entry, cb[1], um + du, vm, color, light, nb);
        vert(vc, entry, cb[2], um + du, vm + du, color, light, nb);
        vert(vc, entry, cb[3], um - du, vm + du, color, light, nb);
        vert(vc, entry, ca[3], um - du, vm, color, light, na);
        vert(vc, entry, ca[2], um + du, vm, color, light, na);
        vert(vc, entry, ca[1], um + du, vm + du, color, light, na);
        vert(vc, entry, ca[0], um - du, vm + du, color, light, na);
    }

    private static void vert(VertexConsumer vc, MatrixStack.Entry entry, float[] p, float u, float v, int color, int light, float[] n) {
        vc.vertex(entry, p[0], p[1], p[2]).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(entry, n[0], n[1], n[2]);
    }

    // ==========================================
    // Flächen am Boden
    // ==========================================

    /** Waagerechte, gedrehte Scheibe (Quadrat mit runder Textur) über dem Boden. */
    private static void disc(OrderedRenderCommandQueue queue, MatrixStack matrices, RenderLayer layer, float radius, float y,
                             float rotDeg, int color, int light) {
        matrices.push();
        matrices.translate(0.0F, y, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotDeg));
        float r = radius;
        queue.submitCustom(matrices, layer, (entry, vc) -> {
            flatVertex(vc, entry, -r, 0.0F, -r, 0.0F, 0.0F, color, light);
            flatVertex(vc, entry, -r, 0.0F, r, 0.0F, 1.0F, color, light);
            flatVertex(vc, entry, r, 0.0F, r, 1.0F, 1.0F, color, light);
            flatVertex(vc, entry, r, 0.0F, -r, 1.0F, 0.0F, color, light);
        });
        matrices.pop();
    }

    /** Senkrechtes, zum Betrachter gedrehtes Leucht-Viereck (Lichtblitz). */
    private void billboard(OrderedRenderCommandQueue queue, MatrixStack matrices, Vec3d cam, RenderLayer layer, float y, float size, int color) {
        double dx = cam.x - pos.x, dz = cam.z - pos.z;
        float yaw = (float) Math.atan2(dx, dz);
        matrices.push();
        matrices.translate(0.0F, y, 0.0F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yaw));
        float h = size;
        queue.submitCustom(matrices, layer, (entry, vc) -> {
            vc.vertex(entry, -h, -h, 0.0F).color(color).texture(0.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(entry, 0, 0, 1);
            vc.vertex(entry, h, -h, 0.0F).color(color).texture(1.0F, 1.0F).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(entry, 0, 0, 1);
            vc.vertex(entry, h, h, 0.0F).color(color).texture(1.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(entry, 0, 0, 1);
            vc.vertex(entry, -h, h, 0.0F).color(color).texture(0.0F, 0.0F).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT).normal(entry, 0, 0, 1);
        });
        matrices.pop();
    }

    private static void flatVertex(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float z, float u, float v, int color, int light) {
        vc.vertex(entry, x, y, z).color(color).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0F, 1.0F, 0.0F);
    }

    private static int argb(float alpha, int r, int g, int b) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ==========================================
    // Vektoren
    // ==========================================

    private static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] scale(float[] a, float s) {
        return new float[]{a[0] * s, a[1] * s, a[2] * s};
    }

    private static float[] mid(float[] a, float[] b) {
        return new float[]{(a[0] + b[0]) * 0.5F, (a[1] + b[1]) * 0.5F, (a[2] + b[2]) * 0.5F};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static boolean normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len < 1.0E-6F) return false;
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
        return true;
    }
}
