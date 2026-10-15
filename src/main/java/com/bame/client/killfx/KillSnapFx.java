package com.bame.client.killfx;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Thanos Snap (4,2 s): Am Todesort steht das Opfer (Skin und Rüstung, Pixel für Pixel nachgebaut, siehe
 * {@link KillFigureMesh}) und sackt leicht zusammen. Eine ausgefranste Zerfallsgrenze wandert in 2,25 s von den Füßen zum
 * Kopf; darunter ist die Figur weg. An der Grenze lösen sich die Pixel als kleine Flocken und Würfel in der Farbe ihres
 * Skin-Texels, werden zu dunkler Asche, wehen seitlich weg, steigen leicht, wirbeln und verblassen. Leises Rieseln und
 * Wind. Am Ende bleibt nichts zurück.
 */
final class KillSnapFx extends KillWorldEffects.Active {

    /** Kantenlänge eines Skin-Pixels in der Welt (Blöcke). */
    private static final float PIXEL = KillFigureMesh.MODEL_SCALE / 16.0F;
    /** Anteil der Texel, die zu Flocken werden. */
    private static final float FLAKE_FRACTION = 0.6F;
    /** Farbe der Asche (Faktor auf die Texel-Farbe). */
    private static final float ASH_R = 0.24F, ASH_G = 0.22F, ASH_B = 0.21F;

    private final KillVictims.Figure figure;
    private final float bodyYaw;
    /** Windrichtung (Welt, waagerecht) und die Querrichtung dazu. */
    private final float windX, windZ, sideX, sideZ;

    private KillFigureMesh mesh;
    private Identifier meshSkin;
    private final List<Identifier> textures = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    /** Startpunkt und Normale jeder Flocke (beim Ablösen festgehalten), relativ zum Todesort. */
    private boolean[] born;
    private float[] origin;
    private float[] originNormal;
    private boolean windSound, midSound, endSound;

    KillSnapFx(Vec3d pos, Vec3d viewer, KillVictims.Figure figure) {
        super(pos);
        this.figure = figure;
        double dx = pos.x - viewer.x;
        double dz = pos.z - viewer.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) {
            dx = 0.0;
            dz = 1.0;
            len = 1.0;
        }
        dx /= len;
        dz /= len;
        // Figur schaut zum Betrachter
        this.bodyYaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        // Wind: zur Seite (vom Betrachter aus rechts) und etwas vom Betrachter weg, damit die Asche gut zu sehen ist
        double wx = -dz * 0.85 + dx * 0.5;
        double wz = dx * 0.85 + dz * 0.5;
        double wl = Math.sqrt(wx * wx + wz * wz);
        this.windX = (float) (wx / wl);
        this.windZ = (float) (wz / wl);
        this.sideX = -windZ;
        this.sideZ = windX;
    }

    // ==========================================
    // Aufbau der Figur (einmalig, bzw. neu, wenn der Skin nachgeladen wurde)
    // ==========================================

    private void ensureMesh(MinecraftClient mc) {
        SkinTextures skin = figure.skin;
        if (skin == null || skin.body() == null) return;
        Identifier skinTex = skin.body().texturePath();
        if (mesh != null && skinTex.equals(meshSkin)) return;
        KillFigureMesh m = new KillFigureMesh();
        List<Identifier> tex = new ArrayList<>();
        List<Integer> col = new ArrayList<>();
        tex.add(skinTex);
        col.add(-1);
        m.addSkin(0, skin.model() == PlayerSkinType.SLIM, KillFigureTextures.mask(mc, skinTex));
        for (KillFigureTextures.ArmorLayer layer : KillFigureTextures.armorLayers(mc, figure.armor)) {
            int slot = tex.size();
            tex.add(layer.texture());
            col.add(layer.color());
            m.addArmor(slot, layer.armorSlot(), KillFigureTextures.mask(mc, layer.texture()));
        }
        m.prepare(KillFigureMesh.restPose(), bodyYaw, windX, windZ, FLAKE_FRACTION);
        textures.clear();
        textures.addAll(tex);
        colors.clear();
        colors.addAll(col);
        mesh = m;
        meshSkin = skinTex;
        born = new boolean[m.size()];
        origin = new float[m.size() * 3];
        originNormal = new float[m.size() * 3];
    }

    // ==========================================
    // Ticks: Sounds und Staub
    // ==========================================

    @Override
    boolean tick(MinecraftClient mc, ClientWorld world) {
        long ms = elapsedMs();
        if (age == 0) {
            KillWorldEffects.sound(world, pos, SoundEvents.BLOCK_SAND_BREAK, 0.35F, 0.55F);
        }
        if (!windSound && ms >= KillFxTimeline.SNAP_START_MS) {
            windSound = true;
            KillWorldEffects.sound(world, pos, SoundEvents.BLOCK_SAND_IDLE, 0.8F, 0.8F);
            KillWorldEffects.sound(world, pos, SoundEvents.ITEM_BRUSH_BRUSHING_SAND, 0.28F, 0.6F);
        }
        if (ms > KillFxTimeline.SNAP_START_MS && ms < KillFxTimeline.SNAP_END_MS && age % 7 == 0) {
            // leises Rieseln
            KillWorldEffects.sound(world, pos, SoundEvents.ITEM_BRUSH_BRUSHING_SAND, 0.2F, 0.5F + random.nextFloat() * 0.25F);
        }
        if (!midSound && ms >= 1_400L) {
            midSound = true;
            KillWorldEffects.sound(world, pos, SoundEvents.BLOCK_SAND_IDLE, 0.6F, 0.7F);
        }
        if (!endSound && ms >= KillFxTimeline.SNAP_END_MS) {
            endSound = true;
            KillWorldEffects.sound(world, pos, SoundEvents.ITEM_BRUSH_BRUSHING_SAND_COMPLETE, 0.22F, 0.55F);
        }
        // Feiner dunkler Staub an der Zerfallsgrenze (wenige Vanilla-Partikel, weht mit dem Wind)
        float b = KillFxTimeline.snapBoundary(ms);
        if (b > 0.0F && b < 1.95F) {
            double a = random.nextDouble() * Math.PI * 2.0;
            double r = 0.18 + random.nextDouble() * 0.12;
            world.addParticleClient(ParticleTypes.SMOKE, pos.x + Math.cos(a) * r, pos.y + b, pos.z + Math.sin(a) * r,
                    windX * 0.06, 0.012, windZ * 0.06);
            if (age % 2 == 0) {
                world.addParticleClient(ParticleTypes.ASH, pos.x + Math.cos(a + 2.0) * r, pos.y + b + 0.05,
                        pos.z + Math.sin(a + 2.0) * r, windX * 0.1, 0.02, windZ * 0.1);
            }
        }
        return ms < KillFxTimeline.SNAP_TOTAL_MS;
    }

    // ==========================================
    // Zeichnen
    // ==========================================

    @Override
    void submit(MinecraftClient mc, MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d cam, CameraRenderState camState) {
        long ms = elapsedMs();
        if (ms >= KillFxTimeline.SNAP_TOTAL_MS) return;
        ensureMesh(mc);
        KillFigureMesh m = mesh;
        if (m == null) return;
        int light = KillWorldEffects.lightAt(mc, pos);
        float[][] pose = KillFigureMesh.slumpPose(KillFxTimeline.snapSlump(ms));
        float[][] mats = new float[KillFigureMesh.PARTS][12];
        for (int p = 0; p < KillFigureMesh.PARTS; p++) KillFigureMesh.partMatrix(p, pose[p], bodyYaw, mats[p]);
        float boundary = KillFxTimeline.snapBoundary(ms);

        // Neu abgelöste Flocken: Startpunkt festhalten; zählen, was es pro Textur zu zeichnen gibt
        int slots = textures.size();
        boolean[] figureLeft = new boolean[slots];
        boolean[] flakesAlive = new boolean[slots];
        float[] tmp = new float[3];
        for (int i = 0; i < m.size(); i++) {
            int s = m.slot(i);
            if (boundary < m.threshold(i)) {
                figureLeft[s] = true;
                continue;
            }
            if (!m.flake(i)) continue;
            if (!born[i]) {
                born[i] = true;
                float[] mat = mats[m.part(i)];
                m.centerOf(i, mat, tmp);
                origin[i * 3] = tmp[0];
                origin[i * 3 + 1] = tmp[1];
                origin[i * 3 + 2] = tmp[2];
                m.normalOf(i, mat, tmp);
                originNormal[i * 3] = tmp[0];
                originNormal[i * 3 + 1] = tmp[1];
                originNormal[i * 3 + 2] = tmp[2];
            }
            float flakeAge = (ms - m.birthMs(i)) / 1000.0F;
            if (flakeAge < KillFxTimeline.snapFlakeLife(m.seed(i))) flakesAlive[s] = true;
        }

        matrices.push();
        matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        for (int s = 0; s < slots; s++) {
            final int slot = s;
            Identifier tex = textures.get(s);
            int color = colors.get(s);
            if (figureLeft[s]) {
                RenderLayer layer = s == 0 ? RenderLayers.entityCutoutNoCull(tex) : RenderLayers.armorCutoutNoCull(tex);
                queue.submitCustom(matrices, layer, (entry, vc) -> emitFigure(m, entry, vc, slot, boundary, mats, light, color));
            }
            if (flakesAlive[s]) {
                queue.submitCustom(matrices, RenderLayers.entityTranslucent(tex), (entry, vc) -> emitFlakes(m, entry, vc, slot, ms, light, color));
            }
        }
        matrices.pop();
    }

    /** Die noch stehenden Texel; knapp oberhalb der Grenze dunkeln sie nach (verkohlter Saum). */
    private static void emitFigure(KillFigureMesh m, MatrixStack.Entry entry, VertexConsumer vc, int slot, float boundary,
                                   float[][] mats, int light, int color) {
        float[] p = new float[3];
        float[] n = new float[3];
        int cr = (color >> 16) & 255, cg = (color >> 8) & 255, cb = color & 255;
        for (int i = 0; i < m.size(); i++) {
            if (m.slot(i) != slot) continue;
            float d = m.threshold(i) - boundary;
            if (d <= 0.0F) continue;
            float tint = d < KillFxTimeline.SNAP_EDGE_BAND ? 0.3F + 0.7F * (d / KillFxTimeline.SNAP_EDGE_BAND) : 1.0F;
            int r = (int) (cr * tint), g = (int) (cg * tint * 0.97F), b = (int) (cb * tint * 0.93F);
            float[] mat = mats[m.part(i)];
            m.normalOf(i, mat, n);
            for (int c = 0; c < 4; c++) {
                m.corner(i, c, mat, p);
                vc.vertex(entry, p[0], p[1], p[2]).color(r, g, b, 255).texture(m.u(i, c), m.v(i, c))
                        .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, n[0], n[1], n[2]);
            }
        }
    }

    /** Ascheflocken: kleine Plättchen (und einige Würfel) mit der Farbe ihres Texels, die zu Asche werden und verwehen. */
    private void emitFlakes(KillFigureMesh m, MatrixStack.Entry entry, VertexConsumer vc, int slot, long ms, int light, int color) {
        float[] off = new float[3];
        float[] e1 = new float[3], e2 = new float[3], e3 = new float[3];
        int cr = (color >> 16) & 255, cg = (color >> 8) & 255, cb = color & 255;
        for (int i = 0; i < m.size(); i++) {
            if (m.slot(i) != slot || !m.flake(i) || !born[i]) continue;
            long seed = m.seed(i);
            float life = KillFxTimeline.snapFlakeLife(seed);
            float flakeAge = Math.max(0.0F, (ms - m.birthMs(i)) / 1000.0F);
            if (flakeAge >= life) continue;
            KillFxTimeline.snapFlakeOffset(flakeAge, seed, off);
            float push = 0.035F * Math.min(1.0F, flakeAge / 0.25F);
            float x = origin[i * 3] + windX * off[0] + sideX * off[2] + originNormal[i * 3] * push;
            float y = origin[i * 3 + 1] + off[1] + originNormal[i * 3 + 1] * push;
            float z = origin[i * 3 + 2] + windZ * off[0] + sideZ * off[2] + originNormal[i * 3 + 2] * push;
            float ash = KillFxTimeline.snapFlakeAsh(flakeAge);
            int alpha = Math.round(255.0F * KillFxTimeline.snapFlakeAlpha(flakeAge, life));
            if (alpha <= 2) continue;
            int r = Math.round(cr * (1.0F + (ASH_R - 1.0F) * ash));
            int g = Math.round(cg * (1.0F + (ASH_G - 1.0F) * ash));
            int b = Math.round(cb * (1.0F + (ASH_B - 1.0F) * ash));
            float half = PIXEL * 0.5F * KillFxTimeline.snapFlakeScale(flakeAge, life);
            // Taumeln um eine zufällige Achse
            float ax = KillFxTimeline.unit(seed, 11) - 0.5F, ay = KillFxTimeline.unit(seed, 12) - 0.5F, az = KillFxTimeline.unit(seed, 13) - 0.5F;
            float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            if (al < 1.0E-3F) {
                ax = 0.0F;
                ay = 1.0F;
                az = 0.0F;
                al = 1.0F;
            }
            ax /= al;
            ay /= al;
            az /= al;
            float angle = (2.5F + 7.0F * KillFxTimeline.unit(seed, 14)) * flakeAge + KillFxTimeline.unit(seed, 15) * 6.2831855F;
            rotate(ax, ay, az, angle, 1, 0, 0, e1);
            rotate(ax, ay, az, angle, 0, 1, 0, e2);
            rotate(ax, ay, az, angle, 0, 0, 1, e3);
            // UV: der ganze Texel (leicht nach innen, damit keine Nachbarfarbe hineinblutet)
            float u0 = Math.min(Math.min(m.u(i, 0), m.u(i, 1)), m.u(i, 2)), u1 = Math.max(Math.max(m.u(i, 0), m.u(i, 1)), m.u(i, 2));
            float v0 = Math.min(Math.min(m.v(i, 0), m.v(i, 1)), m.v(i, 2)), v1 = Math.max(Math.max(m.v(i, 0), m.v(i, 2)), m.v(i, 3));
            float du = (u1 - u0) * 0.2F, dv = (v1 - v0) * 0.2F;
            u0 += du;
            u1 -= du;
            v0 += dv;
            v1 -= dv;
            boolean cube = KillFxTimeline.unit(seed, 10) < 0.25F;
            if (cube) {
                // Würfel: 6 Seiten
                cubeFace(vc, entry, x, y, z, e1, e2, e3, half, r, g, b, alpha, u0, v0, u1, v1, light);
                cubeFace(vc, entry, x, y, z, e1, e2, neg(e3), half, r, g, b, alpha, u0, v0, u1, v1, light);
                cubeFace(vc, entry, x, y, z, e2, e3, e1, half, r, g, b, alpha, u0, v0, u1, v1, light);
                cubeFace(vc, entry, x, y, z, e2, e3, neg(e1), half, r, g, b, alpha, u0, v0, u1, v1, light);
                cubeFace(vc, entry, x, y, z, e3, e1, e2, half, r, g, b, alpha, u0, v0, u1, v1, light);
                cubeFace(vc, entry, x, y, z, e3, e1, neg(e2), half, r, g, b, alpha, u0, v0, u1, v1, light);
            } else {
                // Flocke: dünnes, doppelseitiges Plättchen
                quad(vc, entry, x, y, z, e1, e2, e3, half, 0.0F, r, g, b, alpha, u0, v0, u1, v1, light);
            }
        }
    }

    private static float[] neg(float[] v) {
        return new float[]{-v[0], -v[1], -v[2]};
    }

    private static void cubeFace(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float z, float[] a, float[] b,
                                 float[] n, float half, int r, int g, int bl, int alpha, float u0, float v0, float u1, float v1, int light) {
        quad(vc, entry, x, y, z, a, b, n, half, half, r, g, bl, alpha, u0, v0, u1, v1, light);
    }

    /** Viereck mit den Kanten a, b (halbe Kantenlänge {@code half}), um {@code offset} entlang n verschoben. */
    private static void quad(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float z, float[] a, float[] b, float[] n,
                             float half, float offset, int r, int g, int bl, int alpha, float u0, float v0, float u1, float v1, int light) {
        float cx = x + n[0] * offset, cy = y + n[1] * offset, cz = z + n[2] * offset;
        float ax = a[0] * half, ay = a[1] * half, az = a[2] * half;
        float bx = b[0] * half, by = b[1] * half, bz = b[2] * half;
        vertex(vc, entry, cx - ax - bx, cy - ay - by, cz - az - bz, r, g, bl, alpha, u0, v0, light, n);
        vertex(vc, entry, cx + ax - bx, cy + ay - by, cz + az - bz, r, g, bl, alpha, u1, v0, light, n);
        vertex(vc, entry, cx + ax + bx, cy + ay + by, cz + az + bz, r, g, bl, alpha, u1, v1, light, n);
        vertex(vc, entry, cx - ax + bx, cy - ay + by, cz - az + bz, r, g, bl, alpha, u0, v1, light, n);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float z, int r, int g, int b, int a,
                               float u, float v, int light, float[] n) {
        vc.vertex(entry, x, y, z).color(r, g, b, a).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light)
                .normal(entry, n[0], n[1], n[2]);
    }

    /** Drehung von (x, y, z) um die normierte Achse (ax, ay, az) um {@code angle} (Rodrigues). */
    private static void rotate(float ax, float ay, float az, float angle, float x, float y, float z, float[] out) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float dot = ax * x + ay * y + az * z;
        float cx = ay * z - az * y, cy = az * x - ax * z, cz = ax * y - ay * x;
        out[0] = x * c + cx * s + ax * dot * (1.0F - c);
        out[1] = y * c + cy * s + ay * dot * (1.0F - c);
        out[2] = z * c + cz * s + az * dot * (1.0F - c);
    }
}
