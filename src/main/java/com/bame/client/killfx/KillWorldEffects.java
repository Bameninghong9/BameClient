package com.bame.client.killfx;

import com.bame.client.cosmetics.BedrockCosmeticModel;
import com.bame.client.cosmetics.CosmeticsConfig;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Welt-Teil der Kill-Effekte, rein auf dem Client: Partikel und Sounds pro Tick, Grabstein und Blitz über den
 * Welt-Render-Hook. Kein Paket an den Minecraft-Server, keine Entities, keine Wirkung auf das Spiel.
 * <p>
 * Alle Effekte dauern höchstens 3 s (Ausnahmen laut Auftrag: der Grabstein steht 5 s und sinkt dann 0,8 s ein, der
 * Sarg dauert 3,2 s). Sarg und Amboss (mit Opfer-Figur) werden vor den Entities in die Befehlsliste der Welt gegeben
 * ({@link #submit}), Grabstein und Blitz am Ende des Haupt-Durchgangs gezeichnet ({@link #render}).
 * Partikelmengen sind moderat (das Totem entspricht genau einem Vanilla-Totem-Pop), gleichzeitig laufen höchstens
 * {@link #MAX_ACTIVE}.
 */
final class KillWorldEffects {

    private static final int MAX_ACTIVE = 8;
    private static final List<Active> ACTIVE = new ArrayList<>();

    private static final String GRAVE_MODEL_PATH = "assets/bameclient/killfx/grave.geo.json";
    private static final Identifier GRAVE_TEXTURE = Identifier.of("bameclient", "textures/entity/kill_grave.png");
    private static BedrockCosmeticModel graveModel;
    private static boolean graveLoaded;

    private static final String COFFIN_MODEL_PATH = "assets/bameclient/killfx/coffin.geo.json";
    private static final Identifier COFFIN_TEXTURE = Identifier.of("bameclient", "textures/entity/kill_coffin.png");
    private static final Identifier SHADOW_TEXTURE = Identifier.ofVanilla("textures/misc/shadow.png");
    private static BedrockCosmeticModel coffinModel;
    private static boolean coffinLoaded;

    private KillWorldEffects() {}

    /** Startet den Welt-Teil eines Effekts. */
    static void spawn(KillEffectType type, Vec3d pos, String victimName, KillVictims.Figure figure) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || type == null || pos == null) return;
        Active a = switch (type) {
            case TOTEM -> new TotemFx(pos);
            case LIGHTNING -> new LightningFx(pos);
            case GRAVE -> new GraveFx(groundAt(mc.world, pos), victimName, viewerPos(mc));
            case COFFIN -> new CoffinFx(groundAt(mc.world, pos), viewerPos(mc));
            case ANVIL -> new AnvilFx(groundAt(mc.world, pos), viewerPos(mc),
                    figure != null ? figure : KillVictims.figure(null, victimName, true));
        };
        if (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.remove(0);
        ACTIVE.add(a);
    }

    static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        if (world == null) {
            ACTIVE.clear();
            return;
        }
        for (Iterator<Active> it = ACTIVE.iterator(); it.hasNext(); ) {
            Active a = it.next();
            boolean alive;
            try {
                alive = a.tick(mc, world);
            } catch (Throwable t) {
                alive = false;
            }
            a.age++;
            if (!alive) it.remove();
        }
    }

    static void clear() {
        ACTIVE.clear();
    }

    static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    /** Welt-Render-Hook (WorldRenderEvents.END_MAIN): Grabsteine und Blitze. */
    static void render(WorldRenderContext context) {
        if (ACTIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrices();
        if (consumers == null || matrices == null) return;
        Vec3d cam = context.worldState().cameraRenderState.pos;
        boolean drew = false;
        for (Active a : ACTIVE) {
            try {
                drew |= a.render(mc, matrices, consumers, cam);
            } catch (Throwable ignored) {}
        }
        if (drew && consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
    }

    /**
     * Welt-Render-Hook (WorldRenderEvents.BEFORE_ENTITIES): Sarg und Amboss samt Opfer-Figur werden in die
     * Befehlsliste der Welt gegeben und zusammen mit den Entities gezeichnet (Tiefe, Licht, Rüstungs-Glanz wie Vanilla).
     */
    static void submit(WorldRenderContext context) {
        if (ACTIVE.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        OrderedRenderCommandQueue queue = context.commandQueue();
        MatrixStack matrices = context.matrices();
        if (queue == null || matrices == null || context.worldState() == null) return;
        CameraRenderState camState = context.worldState().cameraRenderState;
        if (camState == null) return;
        for (Active a : ACTIVE) {
            try {
                a.submit(mc, matrices, queue, camState.pos, camState);
            } catch (Throwable ignored) {}
        }
    }

    // ==========================================
    // Hilfen
    // ==========================================

    private static Vec3d viewerPos(MinecraftClient mc) {
        if (mc.gameRenderer != null && mc.gameRenderer.getCamera() != null) return mc.gameRenderer.getCamera().getCameraPos();
        return mc.player != null ? mc.player.getEyePos() : Vec3d.ZERO;
    }

    /** Boden unter der Position (bis 4 Blöcke tief), damit der Grabstein nicht in der Luft steht. */
    private static Vec3d groundAt(ClientWorld world, Vec3d pos) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        int startY = (int) Math.floor(pos.y + 0.01);
        for (int dy = 0; dy <= 4; dy++) {
            m.set(Math.floor(pos.x), startY - dy - 1, Math.floor(pos.z));
            BlockState state = world.getBlockState(m);
            if (!state.getCollisionShape(world, m).isEmpty()) {
                double top = m.getY() + state.getCollisionShape(world, m).getMax(net.minecraft.util.math.Direction.Axis.Y);
                return new Vec3d(pos.x, Math.min(top, pos.y + 0.01), pos.z);
            }
        }
        return pos;
    }

    private static void sound(ClientWorld world, Vec3d pos, SoundEvent event, float volume, float pitch) {
        if (!CosmeticsConfig.killFxSounds || event == null) return;
        world.playSoundClient(pos.x, pos.y, pos.z, event, SoundCategory.PLAYERS, volume, pitch, false);
    }

    // ==========================================
    // Effekte
    // ==========================================

    abstract static class Active {
        final Vec3d pos;
        final long startMs = System.currentTimeMillis();
        final Random random = new Random();
        int age;

        Active(Vec3d pos) {
            this.pos = pos;
        }

        /** Ein Client-Tick; {@code false} = fertig. */
        abstract boolean tick(MinecraftClient mc, ClientWorld world);

        /** Gibt Modelle in die Befehlsliste der Welt (vor den Entities). */
        void submit(MinecraftClient mc, MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d cam, CameraRenderState camState) {
        }

        /** Zeichnet in der Welt; {@code true}, wenn etwas gezeichnet wurde. */
        boolean render(MinecraftClient mc, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam) {
            return false;
        }

        long elapsedMs() {
            return System.currentTimeMillis() - startMs;
        }
    }

    /**
     * Totem: wie ein echter Totem-Pop in Vanilla. Totem-Sound mit normaler Lautstärke/Tonhöhe und 30 Ticks lang
     * Totem-Partikel aus dem Körper des Opfers (nachgebaut nach {@code EmitterParticle}: bis zu 16 Versuche pro Tick
     * innerhalb einer Kugel, Spieler-Hitbox 0,6 x 1,8).
     */
    static final class TotemFx extends Active {
        private static final int TICKS = 30;
        private static final double WIDTH = 0.6;
        private static final double HEIGHT = 1.8;

        TotemFx(Vec3d pos) {
            super(pos);
        }

        @Override
        boolean tick(MinecraftClient mc, ClientWorld world) {
            if (age == 0) sound(world, pos, SoundEvents.ITEM_TOTEM_USE, 1.0F, 1.0F);
            if (age < TICKS) {
                for (int i = 0; i < 16; i++) {
                    double dx = random.nextDouble() * 2.0 - 1.0;
                    double dy = random.nextDouble() * 2.0 - 1.0;
                    double dz = random.nextDouble() * 2.0 - 1.0;
                    if (dx * dx + dy * dy + dz * dz > 1.0) continue;
                    world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING,
                            pos.x + WIDTH * dx / 4.0, pos.y + HEIGHT * (0.5 + dy / 4.0), pos.z + WIDTH * dz / 4.0,
                            dx, dy + 0.2, dz);
                }
            }
            return age < TICKS;
        }
    }

    /** Blitz: eigener, kurzer Blitz (24 Blöcke, lila getönt) ohne Feuer und ohne Himmels-Aufhellung, leiser Donner. */
    static final class LightningFx extends Active {
        private final long seedA = random.nextLong();
        private final long seedB = random.nextLong();

        LightningFx(Vec3d pos) {
            super(pos);
        }

        @Override
        boolean tick(MinecraftClient mc, ClientWorld world) {
            if (age == 0) {
                sound(world, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, 0.45F, 1.35F);
                sound(world, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5F, 1.2F);
                for (int i = 0; i < 18; i++) {
                    world.addParticleClient(ParticleTypes.ELECTRIC_SPARK, pos.x + (random.nextDouble() - 0.5) * 0.8,
                            pos.y + 0.1 + random.nextDouble() * 0.6, pos.z + (random.nextDouble() - 0.5) * 0.8,
                            (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.6);
                }
                for (int i = 0; i < 6; i++) {
                    world.addParticleClient(ParticleTypes.SMOKE, pos.x + (random.nextDouble() - 0.5) * 0.6, pos.y + 0.1,
                            pos.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.03, 0);
                }
            }
            return elapsedMs() < 450L;
        }

        @Override
        boolean render(MinecraftClient mc, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam) {
            long ms = elapsedMs();
            // Zwei kurze Aufblitzer wie ein echter Blitz, insgesamt unter 0,4 s
            boolean visible = ms < 140L || (ms >= 200L && ms < 360L);
            if (!visible) return false;
            long seed = ms < 140L ? seedA : seedB;
            matrices.push();
            matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            // Vanilla-Geometrie (8 Segmente à 16 Einheiten), auf 24 Blöcke Höhe und schmaler skaliert
            matrices.scale(0.35F, 0.1875F, 0.35F);
            VertexConsumer vc = consumers.getBuffer(RenderLayers.lightning());
            drawBolt(matrices.peek().getPositionMatrix(), vc, seed);
            matrices.pop();
            return true;
        }

        /** Nach {@code LightningEntityRenderer}: gleicher Aufbau, aber eigene (lila) Farbe. */
        private static void drawBolt(Matrix4f matrix, VertexConsumer vc, long seed) {
            float[] fs = new float[8];
            float[] gs = new float[8];
            float f = 0.0F;
            float g = 0.0F;
            net.minecraft.util.math.random.Random random = net.minecraft.util.math.random.Random.create(seed);
            for (int i = 7; i >= 0; i--) {
                fs[i] = f;
                gs[i] = g;
                f += random.nextInt(11) - 5;
                g += random.nextInt(11) - 5;
            }
            float h = f;
            float j = g;
            float red = 0.62F, green = 0.36F, blue = 0.95F;
            for (int i = 0; i < 4; i++) {
                net.minecraft.util.math.random.Random rx = net.minecraft.util.math.random.Random.create(seed);
                for (int jx = 0; jx < 3; jx++) {
                    int k = 7;
                    int l = 0;
                    if (jx > 0) k = 7 - jx;
                    if (jx > 0) l = k - 2;
                    float hx = fs[k] - h;
                    float m = gs[k] - j;
                    for (int n = k; n >= l; n--) {
                        float o = hx;
                        float p = m;
                        if (jx == 0) {
                            hx += rx.nextInt(11) - 5;
                            m += rx.nextInt(11) - 5;
                        } else {
                            hx += rx.nextInt(31) - 15;
                            m += rx.nextInt(31) - 15;
                        }
                        float u = 0.1F + i * 0.2F;
                        if (jx == 0) u *= n * 0.1F + 1.0F;
                        float v = 0.1F + i * 0.2F;
                        if (jx == 0) v *= (n - 1.0F) * 0.1F + 1.0F;
                        branch(matrix, vc, hx, m, n, o, p, red, green, blue, u, v, false, false, true, false);
                        branch(matrix, vc, hx, m, n, o, p, red, green, blue, u, v, true, false, true, true);
                        branch(matrix, vc, hx, m, n, o, p, red, green, blue, u, v, true, true, false, true);
                        branch(matrix, vc, hx, m, n, o, p, red, green, blue, u, v, false, true, false, false);
                    }
                }
            }
        }

        private static void branch(Matrix4f matrix, VertexConsumer buffer, float x1, float z1, int y, float x2, float z2,
                                   float red, float green, float blue, float offset2, float offset1,
                                   boolean shiftEast1, boolean shiftSouth1, boolean shiftEast2, boolean shiftSouth2) {
            buffer.vertex(matrix, x1 + (shiftEast1 ? offset1 : -offset1), y * 16, z1 + (shiftSouth1 ? offset1 : -offset1)).color(red, green, blue, 0.35F);
            buffer.vertex(matrix, x2 + (shiftEast1 ? offset2 : -offset2), (y + 1) * 16, z2 + (shiftSouth1 ? offset2 : -offset2)).color(red, green, blue, 0.35F);
            buffer.vertex(matrix, x2 + (shiftEast2 ? offset2 : -offset2), (y + 1) * 16, z2 + (shiftSouth2 ? offset2 : -offset2)).color(red, green, blue, 0.35F);
            buffer.vertex(matrix, x1 + (shiftEast2 ? offset1 : -offset1), y * 16, z1 + (shiftSouth2 ? offset1 : -offset1)).color(red, green, blue, 0.35F);
        }
    }

    /** Grabstein mit dem Namen des Opfers: taucht kurz auf, steht 5 s, sinkt dann in 0,8 s in den Boden. */
    static final class GraveFx extends Active {
        private static final long RISE_MS = 300L;
        private static final long STAND_MS = 5_000L;
        private static final long SINK_MS = 800L;
        private static final float HEIGHT = 14.0F / 16.0F;
        private final String name;
        private final float yawRad;
        private boolean sinkSound;

        GraveFx(Vec3d pos, String name, Vec3d viewer) {
            super(pos);
            this.name = name;
            double dx = viewer.x - pos.x;
            double dz = viewer.z - pos.z;
            this.yawRad = (dx * dx + dz * dz) < 1.0E-4 ? 0.0F : (float) Math.atan2(dx, dz);
        }

        @Override
        boolean tick(MinecraftClient mc, ClientWorld world) {
            if (age == 0) {
                sound(world, pos, SoundEvents.BLOCK_STONE_PLACE, 0.7F, 0.8F);
                for (int i = 0; i < 6; i++) {
                    world.addParticleClient(ParticleTypes.POOF, pos.x + (random.nextDouble() - 0.5) * 0.8, pos.y + 0.1,
                            pos.z + (random.nextDouble() - 0.5) * 0.8, 0, 0.02, 0);
                }
            }
            if (!sinkSound && elapsedMs() >= STAND_MS) {
                sinkSound = true;
                sound(world, pos, SoundEvents.BLOCK_GRAVEL_BREAK, 0.5F, 0.7F);
            }
            return elapsedMs() < STAND_MS + SINK_MS;
        }

        @Override
        boolean render(MinecraftClient mc, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam) {
            BedrockCosmeticModel model = grave();
            if (model == null) return false;
            long ms = elapsedMs();
            float offset;
            if (ms < RISE_MS) {
                float p = ms / (float) RISE_MS;
                offset = -HEIGHT * (1.0F - p) * (1.0F - p);
            } else if (ms < STAND_MS) {
                offset = 0.0F;
            } else {
                float p = Math.min(1.0F, (ms - STAND_MS) / (float) SINK_MS);
                offset = -(HEIGHT + 0.05F) * p * p;
            }
            ClientWorld world = mc.world;
            int light = world != null ? WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(pos.x, pos.y + 0.5, pos.z)) : 0x00F000F0;

            matrices.push();
            matrices.translate(pos.x - cam.x, pos.y - cam.y + offset, pos.z - cam.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yawRad));

            // Bedrock-Modell: 1 px = 1/16 Block, Boden bei y = 0, Vorderseite (south) zeigt nach +Z (zum Betrachter)
            matrices.push();
            matrices.scale(1.0F, -1.0F, 1.0F);
            matrices.translate(0.0F, -24.0F / 16.0F, 0.0F);
            RenderLayer layer = RenderLayers.entityCutoutNoCull(GRAVE_TEXTURE);
            model.render(matrices.peek(), consumers.getBuffer(layer), light, 0, 1, false, 0xFFFFFFFF, null);
            matrices.pop();

            // Name des Opfers auf der Vorderseite (wie Schilder-Text), unter dem eingravierten "RIP"
            if (name != null && !name.isEmpty()) {
                TextRenderer tr = mc.textRenderer;
                int w = Math.max(1, tr.getWidth(name));
                float s = Math.min(0.0155F, 0.53F / w);
                matrices.push();
                matrices.translate(0.0F, 4.7F / 16.0F, 1.5F / 16.0F + 0.004F);
                matrices.scale(s, -s, s);
                tr.draw(name, -w / 2.0F, -4.0F, 0xFF2E2F36, false, matrices.peek().getPositionMatrix(), consumers,
                        TextRenderer.TextLayerType.POLYGON_OFFSET, 0, light);
                matrices.pop();
            }
            matrices.pop();
            return true;
        }
    }

    // ==========================================
    // Sarg
    // ==========================================

    /**
     * Sarg (3,2 s): Der Boden bricht auf (Block-Partikel des Untergrunds, Erdklumpen), ein Holzsarg steigt mit offenem
     * Deckel aus der Erde, der Deckel fällt mit einem dumpfen "Klack" zu, kurze Pause, dann versinkt er wieder. Die
     * Längsseite zeigt zum Betrachter, das Scharnier liegt hinten (der offene Sarg ist von vorne einsehbar).
     */
    static final class CoffinFx extends Active {
        private static final float SCALE = 0.9F;
        /** Höhe des geschlossenen Sargs (Blöcke). */
        private static final float HEIGHT = 7.9F / 16.0F * SCALE;
        /** Starttiefe: auch der offene Deckel steckt anfangs ganz im Boden. */
        private static final float RISE_DEPTH = 1.05F;
        private final float yawRad;
        /** Welt-Richtungen der lokalen X- (Breite, zum Betrachter = -X) und Z-Achse (Länge). */
        private final double xAx, xAz, zAx, zAz;
        private final BlockState particleState;
        private final BlockState clumpState;
        private final float[][] clumps;
        private boolean klack, bell, sinkFx;

        CoffinFx(Vec3d pos, Vec3d viewer) {
            super(pos);
            double dx = viewer.x - pos.x;
            double dz = viewer.z - pos.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0E-3) {
                dx = 0.0;
                dz = 1.0;
                len = 1.0;
            }
            dx /= len;
            dz /= len;
            this.yawRad = (float) Math.atan2(dz, -dx);
            this.xAx = -dx;
            this.xAz = -dz;
            this.zAx = dz;
            this.zAz = -dx;
            ClientWorld world = MinecraftClient.getInstance().world;
            this.particleState = groundState(world, pos);
            this.clumpState = clumpState(particleState);
            // Erdklumpen um den Sarg (lokal x, z, Größe, Drehung)
            this.clumps = new float[][]{
                    {0.44F, -0.62F, 0.22F, 0.3F}, {0.47F, 0.05F, 0.18F, 1.1F}, {0.42F, 0.66F, 0.24F, 2.0F},
                    {-0.45F, -0.55F, 0.20F, 0.7F}, {-0.43F, 0.18F, 0.25F, 1.6F}, {-0.46F, 0.72F, 0.17F, 2.6F},
                    {0.12F, -1.02F, 0.21F, 0.9F}, {-0.16F, -1.0F, 0.16F, 2.2F}, {0.05F, 1.02F, 0.22F, 1.3F}
            };
        }

        private Vec3d local(double lx, double ly, double lz) {
            return new Vec3d(pos.x + lx * xAx + lz * zAx, pos.y + ly, pos.z + lx * xAz + lz * zAz);
        }

        private void dirt(ClientWorld world, int count, double spreadX, double spreadZ, double up) {
            BlockStateParticleEffect effect = new BlockStateParticleEffect(ParticleTypes.BLOCK, particleState);
            for (int i = 0; i < count; i++) {
                Vec3d p = local((random.nextDouble() - 0.5) * 2.0 * spreadX, 0.05, (random.nextDouble() - 0.5) * 2.0 * spreadZ);
                world.addParticleClient(effect, p.x, p.y, p.z, (random.nextDouble() - 0.5) * 0.15, up * (0.5 + random.nextDouble()),
                        (random.nextDouble() - 0.5) * 0.15);
            }
        }

        @Override
        boolean tick(MinecraftClient mc, ClientWorld world) {
            long ms = elapsedMs();
            if (age == 0) {
                // Der Boden bricht auf
                sound(world, pos, SoundEvents.BLOCK_ROOTED_DIRT_BREAK, 0.9F, 0.7F);
                sound(world, pos, SoundEvents.BLOCK_GRAVEL_BREAK, 0.6F, 0.6F);
                dirt(world, 26, 0.38, 0.95, 0.18);
            }
            if (ms < KillFxTimeline.COFFIN_RISE_MS && age % 2 == 0) dirt(world, 4, 0.36, 0.9, 0.12);
            if (!klack && ms >= KillFxTimeline.COFFIN_KLACK_MS) {
                klack = true;
                // Dumpfer Holz-"Klack" des zufallenden Deckels
                sound(world, pos, SoundEvents.BLOCK_WOODEN_TRAPDOOR_CLOSE, 1.0F, 0.55F);
                sound(world, pos, SoundEvents.BLOCK_CHEST_CLOSE, 0.35F, 0.6F);
                // feiner Holzstaub an der Deckelkante (kleine Partikel, verdeckt nichts)
                BlockStateParticleEffect dust = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.getDefaultState());
                for (int i = 0; i < 8; i++) {
                    Vec3d p = local(-0.3, HEIGHT, (random.nextDouble() - 0.5) * 1.6);
                    world.addParticleClient(dust, p.x, p.y, p.z, xAx * 0.04, 0.05, xAz * 0.04);
                }
            }
            if (!bell && ms >= KillFxTimeline.COFFIN_BELL_MS) {
                bell = true;
                sound(world, pos, SoundEvents.BLOCK_BELL_USE, 0.22F, 0.5F);
            }
            if (!sinkFx && ms >= KillFxTimeline.COFFIN_SINK_START_MS) {
                sinkFx = true;
                sound(world, pos, SoundEvents.BLOCK_ROOTED_DIRT_BREAK, 0.7F, 0.6F);
            }
            if (ms >= KillFxTimeline.COFFIN_SINK_START_MS && ms < KillFxTimeline.COFFIN_TOTAL_MS && age % 2 == 0) {
                dirt(world, 3, 0.36, 0.9, 0.1);
            }
            return ms < KillFxTimeline.COFFIN_TOTAL_MS;
        }

        @Override
        void submit(MinecraftClient mc, MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d cam, CameraRenderState camState) {
            BedrockCosmeticModel model = coffin();
            long ms = elapsedMs();
            if (ms >= KillFxTimeline.COFFIN_TOTAL_MS) return;
            int light = lightAt(mc, pos);
            float burial = KillFxTimeline.coffinBurial(ms);
            float depth = ms < KillFxTimeline.COFFIN_RISE_MS ? RISE_DEPTH * burial : (HEIGHT + 0.06F) * burial;

            matrices.push();
            matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(yawRad));

            // Erdklumpen (bleiben am Boden, halb eingegraben)
            float mound = KillFxTimeline.coffinMound(ms);
            if (mound > 0.01F) {
                for (float[] c : clumps) {
                    matrices.push();
                    matrices.translate(c[0], 0.0F, c[1]);
                    matrices.multiply(RotationAxis.POSITIVE_Y.rotation(c[3]));
                    float s = c[2] * mound;
                    matrices.scale(s, s * 0.8F, s);
                    matrices.translate(-0.5F, -0.55F, -0.5F);
                    queue.submitBlock(matrices, clumpState, light, OverlayTexture.DEFAULT_UV, 0);
                    matrices.pop();
                }
            }

            if (model != null) {
                matrices.push();
                matrices.translate(0.0F, -depth, 0.0F);
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(KillFxTimeline.coffinWobbleDeg(ms)));
                matrices.scale(SCALE, SCALE, SCALE);
                // Bedrock-Modell: 1 px = 1/16 Block, Boden bei y = 0
                matrices.scale(1.0F, -1.0F, 1.0F);
                matrices.translate(0.0F, -24.0F / 16.0F, 0.0F);
                float lid = KillFxTimeline.coffinLidDeg(ms);
                queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(COFFIN_TEXTURE), (entry, vc) -> {
                    model.setBoneRotation("lid", 0.0F, 0.0F, lid);
                    model.render(entry, vc, light, 0, 1, false, 0xFFFFFFFF, null);
                    model.setBoneRotation("lid", 0.0F, 0.0F, 0.0F);
                });
                matrices.pop();
            }
            matrices.pop();
        }
    }

    // ==========================================
    // Amboss
    // ==========================================

    /**
     * Amboss (2,6 s): Am Todesort erscheint das Opfer (Vanilla-Spielermodell mit seinem Skin und der Rüstung beim Tod,
     * ohne Rüstung in Leder), schaut erschrocken nach oben, ein Amboss fällt aus 8 Blöcken auf seinen Kopf und drückt
     * es cartoon-artig platt; danach versinken beide im Boden. Keine Entity, kein Namensschild.
     */
    static final class AnvilFx extends Active {
        private final KillVictims.Figure figure;
        private final KillFigureState state = new KillFigureState();
        private final float bodyYaw;
        private final BlockState anvil;
        private final BlockState particleState;
        private boolean impact, sinkFx;

        AnvilFx(Vec3d pos, Vec3d viewer, KillVictims.Figure figure) {
            super(pos);
            this.figure = figure;
            double dx = viewer.x - pos.x;
            double dz = viewer.z - pos.z;
            // Figur schaut zum Betrachter
            this.bodyYaw = (dx * dx + dz * dz) < 1.0E-4 ? 0.0F : (float) Math.toDegrees(Math.atan2(-dx, dz));
            // Lange Seite (mit Horn) quer zum Blick des Betrachters
            this.anvil = Blocks.ANVIL.getDefaultState().with(AnvilBlock.FACING, Direction.fromHorizontalDegrees(bodyYaw));
            this.particleState = groundState(MinecraftClient.getInstance().world, pos);
            state.x = pos.x;
            state.y = pos.y;
            state.z = pos.z;
            state.capeVisible = true;
        }

        @Override
        boolean tick(MinecraftClient mc, ClientWorld world) {
            long ms = elapsedMs();
            if (age == 0) {
                for (int i = 0; i < 3; i++) {
                    world.addParticleClient(ParticleTypes.POOF, pos.x + (random.nextDouble() - 0.5) * 0.6, pos.y + 0.05,
                            pos.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.01, 0);
                }
            }
            if (!impact && ms >= KillFxTimeline.ANVIL_IMPACT_MS) {
                impact = true;
                sound(world, pos, SoundEvents.BLOCK_ANVIL_LAND, 0.9F, 0.85F);
                BlockStateParticleEffect effect = new BlockStateParticleEffect(ParticleTypes.BLOCK, particleState);
                for (int i = 0; i < 18; i++) {
                    double a = random.nextDouble() * Math.PI * 2.0;
                    double v = 0.12 + random.nextDouble() * 0.12;
                    world.addParticleClient(effect, pos.x + Math.cos(a) * 0.4, pos.y + 0.05, pos.z + Math.sin(a) * 0.4,
                            Math.cos(a) * v, 0.15 + random.nextDouble() * 0.15, Math.sin(a) * v);
                }
                for (int i = 0; i < 3; i++) {
                    double a = random.nextDouble() * Math.PI * 2.0;
                    world.addParticleClient(ParticleTypes.POOF, pos.x + Math.cos(a) * 0.7, pos.y + 0.05, pos.z + Math.sin(a) * 0.7,
                            Math.cos(a) * 0.08, 0.0, Math.sin(a) * 0.08);
                }
                // "Sternchen" über dem Plattgedrückten
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI * 2.0 * i / 10.0;
                    world.addParticleClient(ParticleTypes.CRIT, pos.x + Math.cos(a) * 0.5, pos.y + 0.45, pos.z + Math.sin(a) * 0.5,
                            Math.cos(a) * 0.25, 0.2, Math.sin(a) * 0.25);
                }
            }
            if (!sinkFx && ms >= KillFxTimeline.ANVIL_SINK_START_MS) {
                sinkFx = true;
                sound(world, pos, SoundEvents.BLOCK_GRAVEL_BREAK, 0.5F, 0.6F);
                BlockStateParticleEffect effect = new BlockStateParticleEffect(ParticleTypes.BLOCK, particleState);
                for (int i = 0; i < 10; i++) {
                    world.addParticleClient(effect, pos.x + (random.nextDouble() - 0.5) * 1.0, pos.y + 0.05,
                            pos.z + (random.nextDouble() - 0.5) * 1.0, 0, 0.1, 0);
                }
            }
            return ms < KillFxTimeline.ANVIL_TOTAL_MS;
        }

        @Override
        void submit(MinecraftClient mc, MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d cam, CameraRenderState camState) {
            long ms = elapsedMs();
            if (ms >= KillFxTimeline.ANVIL_TOTAL_MS) return;
            float sink = KillFxTimeline.anvilSink(ms);
            int light = lightAt(mc, pos);

            // Schatten des Ambosses (und der Figur) auf dem Boden
            float shadow = KillFxTimeline.shadowStrength(ms);
            float figureShadow = KillFxTimeline.figurePop(ms) > 0.0F ? 0.45F * (1.0F - Math.min(1.0F, sink / 0.6F)) : 0.0F;
            float alpha = Math.max(shadow, figureShadow);
            if (alpha > 0.01F) {
                float r = Math.max(KillFxTimeline.shadowRadius(ms), 0.45F * KillFxTimeline.squashXZ(ms));
                matrices.push();
                matrices.translate(pos.x - cam.x, pos.y - cam.y + 0.015, pos.z - cam.z);
                int a = Math.round(Math.min(1.0F, alpha) * 255.0F);
                queue.submitCustom(matrices, RenderLayers.entityShadow(SHADOW_TEXTURE), (entry, vc) -> {
                    shadowVertex(vc, entry, -r, -r, 0.0F, 0.0F, a);
                    shadowVertex(vc, entry, -r, r, 0.0F, 1.0F, a);
                    shadowVertex(vc, entry, r, r, 1.0F, 1.0F, a);
                    shadowVertex(vc, entry, r, -r, 1.0F, 0.0F, a);
                });
                matrices.pop();
            }

            // Opfer-Figur (Vanilla-Spielermodell + Rüstung), plattgedrückt per Skalierung
            float pop = KillFxTimeline.figurePop(ms);
            if (pop > 0.001F) {
                KillFigureState st = state;
                st.skinTextures = figure.skin;
                st.equippedHeadStack = figure.armor[0];
                st.equippedChestStack = figure.armor[1];
                st.equippedLegsStack = figure.armor[2];
                st.equippedFeetStack = figure.armor[3];
                st.bodyYaw = bodyYaw;
                st.relativeHeadYaw = 0.0F;
                st.pitch = -40.0F * KillFxTimeline.lookUp(ms);
                st.light = light;
                st.age = ms / 50.0F;
                st.armRaise = KillFxTimeline.armRaise(ms);
                st.splat = KillFxTimeline.squashAmount(ms);
                st.squaredDistanceToCamera = pos.squaredDistanceTo(cam);
                float sy = KillFxTimeline.squashY(ms) * pop;
                float sxz = KillFxTimeline.squashXZ(ms) * pop;
                matrices.push();
                matrices.translate(pos.x - cam.x, pos.y - cam.y - sink, pos.z - cam.z);
                matrices.scale(sxz, sy, sxz);
                mc.getEntityRenderDispatcher().render(st, camState, 0.0, 0.0, 0.0, matrices, queue);
                matrices.pop();
            }

            // Amboss (Vanilla-Blockmodell)
            if (KillFxTimeline.anvilVisible(ms)) {
                float y = KillFxTimeline.anvilHeight(ms) - sink;
                int anvilLight = lightAt(mc, new Vec3d(pos.x, pos.y + Math.max(0.0F, y), pos.z));
                matrices.push();
                matrices.translate(pos.x - cam.x, pos.y - cam.y + y, pos.z - cam.z);
                matrices.translate(-0.5F, 0.0F, -0.5F);
                queue.submitBlock(matrices, anvil, anvilLight, OverlayTexture.DEFAULT_UV, 0);
                matrices.pop();
            }
        }

        private static void shadowVertex(VertexConsumer vc, MatrixStack.Entry entry, float x, float z, float u, float v, int alpha) {
            vc.vertex(entry, x, 0.0F, z).color(255, 255, 255, alpha).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                    .light(0x00F000F0).normal(entry, 0.0F, 1.0F, 0.0F);
        }
    }

    // ==========================================
    // Hilfen für Sarg und Amboss
    // ==========================================

    private static int lightAt(MinecraftClient mc, Vec3d p) {
        ClientWorld world = mc.world;
        return world != null ? WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(p.x, p.y + 0.5, p.z)) : 0x00F000F0;
    }

    /** Block unter dem Todesort (für Partikel); Luft bzw. unbekannt → Erde. */
    private static BlockState groundState(ClientWorld world, Vec3d pos) {
        if (world == null) return Blocks.DIRT.getDefaultState();
        BlockState s = world.getBlockState(BlockPos.ofFloored(pos.x, pos.y - 0.05, pos.z));
        if (s == null || s.isAir() || s.getRenderType() != BlockRenderType.MODEL) return Blocks.DIRT.getDefaultState();
        return s;
    }

    /** Erdklumpen: bei Gras/Erd-Böden grobe Erde, bei anderen vollen Blöcken der Boden selbst (Sand, Stein ...). */
    private static BlockState clumpState(BlockState ground) {
        if (ground == null) return Blocks.COARSE_DIRT.getDefaultState();
        if (ground.isOf(Blocks.GRASS_BLOCK) || ground.isOf(Blocks.DIRT) || ground.isOf(Blocks.PODZOL) || ground.isOf(Blocks.MYCELIUM)
                || ground.isOf(Blocks.DIRT_PATH) || ground.isOf(Blocks.FARMLAND) || ground.isOf(Blocks.ROOTED_DIRT)) {
            return Blocks.COARSE_DIRT.getDefaultState();
        }
        if (ground.isOpaque() && ground.isSolid() && !ground.hasBlockEntity()) return ground.getBlock().getDefaultState();
        return Blocks.COARSE_DIRT.getDefaultState();
    }

    private static BedrockCosmeticModel coffin() {
        if (!coffinLoaded) {
            coffinLoaded = true;
            coffinModel = BedrockCosmeticModel.load(COFFIN_MODEL_PATH);
            if (coffinModel != null) coffinModel.blockbenchAxisFix = true;
        }
        return coffinModel;
    }

    private static BedrockCosmeticModel grave() {
        if (!graveLoaded) {
            graveLoaded = true;
            graveModel = BedrockCosmeticModel.load(GRAVE_MODEL_PATH);
            if (graveModel != null) graveModel.blockbenchAxisFix = true;
        }
        return graveModel;
    }
}
