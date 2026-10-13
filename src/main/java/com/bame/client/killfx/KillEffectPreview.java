package com.bame.client.killfx;

import com.bame.client.gui.CustomGuiUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Kleine gezeichnete Mini-Animationen der Kill-Effekte für die Karten im Cosmetics-Menü (reines 2D, keine Welt).
 * Jede Animation läuft als Schleife; {@code timeMs} ist die laufende Systemzeit.
 */
public final class KillEffectPreview {

    /** Vanilla-Textur des Totems der Unsterblichkeit. */
    private static final Identifier TOTEM_TEXTURE = Identifier.ofVanilla("textures/item/totem_of_undying.png");
    private static final int TOTEM_GREEN = 0xFF5ED43A;
    private static final int TOTEM_GOLD = 0xFFF2D04A;

    private KillEffectPreview() {}

    /** Zeichnet die Vorschau in das Rechteck (x, y, w, h). */
    public static void draw(DrawContext c, KillEffectType type, int x, int y, int w, int h, long timeMs) {
        if (type == null || w < 20 || h < 20) return;
        // Bodenlinie
        int groundY = y + h - 6;
        c.fill(x + 8, groundY, x + w - 8, groundY + 1, 0x40FFFFFF);
        switch (type) {
            case TOTEM -> totem(c, x, y, w, h, groundY, timeMs);
            case LIGHTNING -> lightning(c, x, y, w, h, groundY, timeMs);
            case GRAVE -> grave(c, x, y, w, h, groundY, timeMs);
            case COFFIN -> coffin(c, x, y, w, h, groundY, timeMs);
            case ANVIL -> anvil(c, x, y, w, h, groundY, timeMs);
        }
    }

    private static float phase(long timeMs, long periodMs) {
        return (timeMs % periodMs) / (float) periodMs;
    }

    private static int withAlpha(int argb, float a) {
        int alpha = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 255) * a)));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static void dot(DrawContext c, float cx, float cy, int size, int color) {
        int x0 = Math.round(cx - size / 2.0F);
        int y0 = Math.round(cy - size / 2.0F);
        c.fill(x0, y0, x0 + size, y0 + size, color);
    }

    /**
     * Standard-Totem ploppt hoch (mit Überschwingen), schwebt, verblasst; dazu ein Ausbruch aus grünen und goldenen
     * Totem-Partikeln wie bei einem Vanilla-Totem-Pop.
     */
    private static void totem(DrawContext c, int x, int y, int w, int h, int groundY, long timeMs) {
        float t = phase(timeMs, 2400L);
        int cx = x + w / 2;
        float size;
        float alpha = 1.0F;
        if (t < 0.22F) {
            float p = t / 0.22F;
            float back = 1.0F + 2.2F * (float) Math.pow(p - 1.0F, 3) + 1.2F * (float) Math.pow(p - 1.0F, 2);
            size = 34.0F * Math.max(0.05F, back);
        } else {
            size = 34.0F + (float) Math.sin((t - 0.22F) * 12.0F) * 1.5F;
        }
        if (t > 0.8F) alpha = 1.0F - (t - 0.8F) / 0.2F;
        int s = Math.max(2, Math.round(Math.min(size, h - 14.0F)));
        float rise = t < 0.22F ? t / 0.22F : 1.0F;
        int ty = Math.round(groundY - 4 - s - (h - s - 16) * 0.35F * rise);
        // Totem-Partikel: fliegen aus der Mitte nach außen, sinken leicht und verblassen (hinter dem Totem)
        if (t > 0.04F && t < 0.85F) {
            float p = (t - 0.04F) / 0.81F;
            float ease = 1.0F - (1.0F - p) * (1.0F - p);
            float centerY = ty + s / 2.0F;
            float fade = p < 0.7F ? 1.0F : 1.0F - (p - 0.7F) / 0.3F;
            for (int i = 0; i < 22; i++) {
                long hash = (i * 2654435761L) ^ 0x5DEECE66DL;
                double a = ((hash & 0xFFFF) / 65535.0) * Math.PI * 2;
                float speed = 0.55F + ((hash >> 16) & 0xFF) / 255.0F * 0.45F;
                float dist = ease * speed * Math.min(w, h * 1.4F) * 0.48F;
                float px = cx + (float) Math.cos(a) * dist;
                float py = centerY + (float) Math.sin(a) * dist * 0.75F + p * p * 8.0F;
                int col = (i % 3 == 0) ? TOTEM_GOLD : TOTEM_GREEN;
                dot(c, px, py, (i % 4 == 0) ? 3 : 2, withAlpha(col, fade));
            }
        }
        int color = withAlpha(0xFFFFFFFF, alpha);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, TOTEM_TEXTURE, cx - s / 2, ty, 0.0F, 0.0F, s, s, 16, 16, 16, 16, color);
    }

    /** Blitz schlägt ein: zwei kurze Aufblitzer, Funken am Boden. */
    private static void lightning(DrawContext c, int x, int y, int w, int h, int groundY, long timeMs) {
        float t = phase(timeMs, 2000L);
        int cx = x + w / 2;
        boolean visible = (t > 0.10F && t < 0.20F) || (t > 0.26F && t < 0.36F);
        // Gewitterwolke (immer sichtbar)
        CustomGuiUtils.fillUltraRounded(c, cx - 18, y + 4, 36, 8, visible ? 0xC07C6A9C : 0x90475569, 4.0f);
        CustomGuiUtils.fillUltraRounded(c, cx - 9, y + 1, 18, 7, visible ? 0xC07C6A9C : 0x90475569, 3.5f);
        if (visible) {
            c.fill(x + 2, y + 2, x + w - 2, groundY, 0x18FFFFFF);
            long seed = t < 0.22F ? 7L : 13L;
            java.util.Random r = new java.util.Random(seed);
            float px = cx, py = y + 4;
            int segments = 6;
            float step = (groundY - py) / segments;
            for (int i = 0; i < segments; i++) {
                float nx = i == segments - 1 ? cx : cx + (r.nextFloat() - 0.5F) * 22.0F;
                float ny = py + step;
                CustomGuiUtils.drawSmoothLine(c, px, py, nx, ny, 3.0F, 0x80B07CFF);
                CustomGuiUtils.drawSmoothLine(c, px, py, nx, ny, 1.2F, 0xFFF4EEFF);
                if (i == 2) {
                    float bx = nx + (r.nextBoolean() ? 10 : -10), by = ny + step * 0.8F;
                    CustomGuiUtils.drawSmoothLine(c, nx, ny, bx, by, 1.0F, 0xC0E6DAFF);
                }
                px = nx;
                py = ny;
            }
        }
        if (t > 0.12F && t < 0.6F) {
            float p = (t - 0.12F) / 0.48F;
            for (int i = 0; i < 8; i++) {
                double a = Math.PI + Math.PI * (i + 0.5) / 8.0;
                float d = 4.0F + p * 16.0F;
                dot(c, cx + (float) Math.cos(a) * d, groundY + (float) Math.sin(a) * d * 0.7F + p * p * 10.0F, 2,
                        withAlpha(0xFFE6DAFF, 1.0F - p));
            }
        }
    }

    /** Grabstein taucht auf, steht, sinkt in den Boden. */
    private static void grave(DrawContext c, int x, int y, int w, int h, int groundY, long timeMs) {
        float t = phase(timeMs, 3000L);
        int gw = Math.min(30, w / 3);
        int gh = Math.min(38, h - 14);
        float offset;
        if (t < 0.1F) offset = gh * (1.0F - t / 0.1F);
        else if (t < 0.72F) offset = 0.0F;
        else if (t < 0.92F) offset = gh * ((t - 0.72F) / 0.2F);
        else offset = gh;
        int gx = x + (w - gw) / 2;
        int gy = groundY - gh + Math.round(offset);
        c.enableScissor(x, y, x + w, groundY);
        CustomGuiUtils.fillUltraRounded(c, gx, gy, gw, gh + 4, 0xFF8E909A, Math.min(10.0F, gw / 2.0F));
        CustomGuiUtils.fillUltraRounded(c, gx + 2, gy + 2, gw - 4, gh, 0xFFB3B5BF, Math.min(9.0F, gw / 2.0F - 1));
        MinecraftClient mc = MinecraftClient.getInstance();
        Text rip = CustomGuiUtils.getFontText("RIP");
        int tw = mc.textRenderer.getWidth(rip);
        c.drawText(mc.textRenderer, rip, gx + (gw - tw) / 2, gy + 8, 0xFF3A3B42, false);
        c.fill(gx + 6, gy + 20, gx + gw - 6, gy + 21, 0xFF6A6C74);
        c.disableScissor();
        // Erdhügel
        c.fill(gx - 4, groundY - 1, gx + gw + 4, groundY + 1, 0xFF5A4632);
    }
    /**
     * Sarg (Seitenansicht): steigt mit offenem Deckel (rote Innenseite) aus dem Boden, der Deckel klappt zu, kurze
     * Pause, dann versinkt er. Erdkrümel beim Auftauchen und Versinken. Zeiten wie im Spiel ({@link KillFxTimeline}).
     */
    private static void coffin(DrawContext c, int x, int y, int w, int h, int groundY, long timeMs) {
        long period = KillFxTimeline.COFFIN_TOTAL_MS + 500L;
        long ms = timeMs % period;
        int cw = Math.min(56, w - 16);
        int bodyH = Math.max(8, Math.min(13, h / 5));
        int cx = x + w / 2;
        int left = cx - cw / 2;
        float burial = ms < KillFxTimeline.COFFIN_TOTAL_MS ? KillFxTimeline.coffinBurial(ms) : 1.0F;
        float lidDeg = KillFxTimeline.coffinLidDeg(ms);
        int lidLen = Math.min(22, h - bodyH - 16);
        int maxDepth = ms < KillFxTimeline.COFFIN_RISE_MS ? bodyH + lidLen + 4 : bodyH + 5;
        int off = Math.round(burial * maxDepth);
        int top = groundY - bodyH + off;

        c.enableScissor(x, y, x + w, groundY);
        // Deckel (hinten, am Scharnier): offen steht er hoch und zeigt die rote Innenseite
        double rad = Math.toRadians(lidDeg);
        int lidUp = (int) Math.round(Math.sin(rad) * lidLen);
        if (lidDeg > 8.0F) {
            // Der hochgeklappte Deckel zeigt die klassische Sechseck-Form des Sargs (Kopfende links, Schulter breiter)
            int lx = left + 1, lw = cw - 2;
            float midY = top - 1 - lidUp / 2.0F;
            for (int i = 0; i < lw; i++) {
                float t = i / (float) (lw - 1);
                float half = t < 0.23F ? 0.7F + 0.3F * (t / 0.23F) : 1.0F - 0.4F * ((t - 0.23F) / 0.77F);
                int hh = Math.max(1, Math.round(half * lidUp / 2.0F));
                int y0 = Math.round(midY - hh), y1 = Math.round(midY + hh);
                boolean edge = i == 0 || i == lw - 1;
                c.fill(lx + i, y0, lx + i + 1, y1, 0xFF3F2716);
                if (!edge && y1 - y0 > 3) {
                    c.fill(lx + i, y0 + 1, lx + i + 1, y1 - 1, 0xFF8A1424);
                }
            }
            // Knöpfe der Polsterung
            for (int i = lx + 6; i < lx + lw - 5; i += 6) {
                if (lidUp > 8) c.fill(i, Math.round(midY), i + 1, Math.round(midY) + 1, 0xFF4E0A14);
            }
            // offenes Innenleben über der Vorderwand
            c.fill(left + 2, top - 2, left + cw - 2, top, 0xFF5E0D18);
        }
        // Vorderwand (Sockel dunkler, Kanten dunkler, Maserung)
        c.fill(left, top, left + cw, top + bodyH, 0xFF5C3A22);
        c.fill(left, top, left + cw, top + 1, 0xFF3F2716);
        c.fill(left, top + bodyH - 3, left + cw, top + bodyH, 0xFF3F2716);
        c.fill(left - 1, top + bodyH - 2, left + cw + 1, top + bodyH, 0xFF2E1C10);
        for (int i = 0; i < 3; i++) {
            int gy = top + 3 + i * 3;
            if (gy < top + bodyH - 3) c.fill(left + 3 + i * 5, gy, left + cw - 6 - i * 3, gy + 1, 0xFF4E3019);
        }
        // Eck-Beschläge und Griffe (Messing)
        c.fill(left, top, left + 2, top + bodyH, 0xFF45454C);
        c.fill(left + cw - 2, top, left + cw, top + bodyH, 0xFF45454C);
        for (int i = 1; i <= 3; i++) {
            int hx = left + cw * i / 4 - 3;
            c.fill(hx, top + bodyH / 2 - 1, hx + 6, top + bodyH / 2 + 1, 0xFFB8923E);
        }
        // Geschlossen: Deckelkante mit kleinem Kreuz
        if (lidDeg <= 8.0F) {
            int lt = top - 3;
            c.fill(left - 1, lt, left + cw + 1, top, 0xFF3F2716);
            c.fill(left + 1, lt, left + cw - 1, lt + 1, 0xFF6E4529);
            int crx = left + cw / 4;
            c.fill(crx - 2, lt - 1, crx + 3, lt, 0xFF9A7A3A);
        }
        c.disableScissor();

        // Erdhügel und Krümel beim Aufbrechen bzw. Versinken
        c.fill(left - 5, groundY - 1, left + 4, groundY + 1, 0xFF5A4632);
        c.fill(left + cw - 4, groundY - 1, left + cw + 5, groundY + 1, 0xFF5A4632);
        boolean rising = ms < KillFxTimeline.COFFIN_RISE_MS;
        boolean sinking = ms >= KillFxTimeline.COFFIN_SINK_START_MS && ms < KillFxTimeline.COFFIN_TOTAL_MS;
        if (rising || sinking) {
            float p = rising ? ms / (float) KillFxTimeline.COFFIN_RISE_MS
                    : (ms - KillFxTimeline.COFFIN_SINK_START_MS) / (float) (KillFxTimeline.COFFIN_TOTAL_MS - KillFxTimeline.COFFIN_SINK_START_MS);
            for (int i = 0; i < 10; i++) {
                long hash = (i * 2654435761L) ^ 0x9E3779B9L;
                float px = left + ((hash & 0xFFFF) / 65535.0F) * cw;
                float up = 4.0F + ((hash >> 16) & 0xFF) / 255.0F * 8.0F;
                float py = groundY - 2 - up * (float) Math.sin(p * Math.PI);
                dot(c, px, py, 2, withAlpha(0xFF6B5134, 1.0F - p * 0.6F));
            }
        }
    }

    /**
     * Amboss: eine kleine Spielerfigur (Steve in Lederrüstung) schaut nach oben, der Schatten wächst, der Amboss fällt
     * beschleunigt herab und drückt sie platt (Sternchen), hüpft kurz, dann versinken beide. Zeiten wie im Spiel.
     */
    private static void anvil(DrawContext c, int x, int y, int w, int h, int groundY, long timeMs) {
        long period = KillFxTimeline.ANVIL_TOTAL_MS + 500L;
        long ms = timeMs % period;
        boolean done = ms >= KillFxTimeline.ANVIL_TOTAL_MS;
        int cx = x + w / 2;
        // Maßstab: Figur ~ 1,85 Blöcke hoch
        float unit = Math.max(8.0F, Math.min(15.0F, (h - 12) / 3.4F));
        float sink = done ? 99.0F : KillFxTimeline.anvilSink(ms) * unit;

        // Schatten am Boden
        float shadow = done ? 0.0F : KillFxTimeline.shadowStrength(ms);
        if (shadow > 0.01F) {
            int sr = Math.round(KillFxTimeline.shadowRadius(ms) * unit * 1.6F);
            c.fill(cx - sr, groundY - 1, cx + sr, groundY + 1, withAlpha(0xFF000000, shadow * 0.8F));
        }

        c.enableScissor(x, y, x + w, groundY);
        // Figur
        float pop = done ? 0.0F : KillFxTimeline.figurePop(ms);
        if (pop > 0.01F) {
            float sy = KillFxTimeline.squashY(ms) * pop;
            float sxz = KillFxTimeline.squashXZ(ms) * pop;
            float fh = KillFxTimeline.FIGURE_HEIGHT * unit * sy;
            float fw = 0.6F * unit * sxz;
            float baseY = groundY + sink;
            float headH = fh * 0.26F, bodyH = fh * 0.37F, legH = fh - headH - bodyH;
            float raise = KillFxTimeline.armRaise(ms);
            float splat = KillFxTimeline.squashAmount(ms);
            int fx0 = Math.round(cx - fw / 2), fx1 = Math.round(cx + fw / 2);
            // Beine (Lederhose)
            c.fill(fx0, Math.round(baseY - legH), fx1, Math.round(baseY), 0xFF7A4A2A);
            c.fill(cx, Math.round(baseY - legH), cx + 1, Math.round(baseY), 0xFF5A3418);
            // Arme: seitlich, vor Schreck gehoben, platt zur Seite gespreizt
            float armW = fw * 0.42F;
            float shoulderY = baseY - legH - bodyH;
            if (splat > 0.5F) {
                c.fill(Math.round(fx0 - armW * 1.6F), Math.round(baseY - legH - bodyH * 0.6F), fx0, Math.round(baseY - legH - bodyH * 0.2F), 0xFF8A5A34);
                c.fill(fx1, Math.round(baseY - legH - bodyH * 0.6F), Math.round(fx1 + armW * 1.6F), Math.round(baseY - legH - bodyH * 0.2F), 0xFF8A5A34);
            } else {
                float lift = raise * bodyH * 0.9F;
                c.fill(Math.round(fx0 - armW), Math.round(shoulderY - lift), fx0, Math.round(shoulderY + bodyH - lift), 0xFF8A5A34);
                c.fill(fx1, Math.round(shoulderY - lift), Math.round(fx1 + armW), Math.round(shoulderY + bodyH - lift), 0xFF8A5A34);
                // Hände
                c.fill(Math.round(fx0 - armW), Math.round(shoulderY - lift - 2), fx0, Math.round(shoulderY - lift), 0xFFC69680);
                c.fill(fx1, Math.round(shoulderY - lift - 2), Math.round(fx1 + armW), Math.round(shoulderY - lift), 0xFFC69680);
            }
            // Körper (Lederharnisch)
            c.fill(fx0, Math.round(shoulderY), fx1, Math.round(baseY - legH), 0xFF8A5A34);
            // Kopf (Steve) mit Lederkappe
            float hw = fw * 0.82F;
            int hx0 = Math.round(cx - hw / 2), hx1 = Math.round(cx + hw / 2);
            int hy0 = Math.round(shoulderY - headH), hy1 = Math.round(shoulderY);
            c.fill(hx0, hy0, hx1, hy1, 0xFFC69680);
            c.fill(hx0, hy0, hx1, Math.round(hy0 + headH * 0.35F), 0xFF7A4A2A);
            if (splat < 0.5F) {
                // Augen (schauen beim Fallen nach oben)
                int ey = Math.round(hy0 + headH * (KillFxTimeline.lookUp(ms) > 0.5F ? 0.45F : 0.6F));
                c.fill(hx0 + 1, ey, hx0 + 2, ey + 1, 0xFF3B2A7A);
                c.fill(hx1 - 2, ey, hx1 - 1, ey + 1, 0xFF3B2A7A);
            }
        }
        // Amboss
        if (!done && KillFxTimeline.anvilVisible(ms)) {
            int aw = Math.round(unit * 1.15F);
            int ah = Math.round(unit * 0.95F);
            // Fallstrecke in die Karte gestaucht (8 Blöcke passen nicht hinein), ab dem Kopf maßstabsgetreu
            float figTop = KillFxTimeline.FIGURE_HEIGHT * unit;
            float avail = Math.max(10.0F, (groundY - y) - figTop - ah - 2);
            float above = KillFxTimeline.anvilHeight(ms) - KillFxTimeline.FIGURE_HEIGHT;
            float bottom = above > 0.0F ? figTop + above / KillFxTimeline.ANVIL_DROP_HEIGHT * avail : KillFxTimeline.anvilHeight(ms) * unit;
            float ay = groundY + sink - bottom;
            int ax = cx - aw / 2;
            int ab = Math.round(ay);
            int at = ab - ah;
            c.fill(ax, at, ax + aw, at + ah / 3, 0xFF4A4A52);                                 // Oberteil
            c.fill(ax - 2, at, ax, at + ah / 5, 0xFF4A4A52);                                  // Horn
            c.fill(ax + aw / 4, at + ah / 3, ax + aw - aw / 4, ab - ah / 4, 0xFF3A3A40);      // Hals
            c.fill(ax + aw / 8, ab - ah / 4, ax + aw - aw / 8, ab, 0xFF45454C);               // Fuß
            c.fill(ax, at, ax + aw, at + 1, 0xFF6A6A74);
        }
        c.disableScissor();

        // Sternchen nach dem Aufprall
        if (!done && ms >= KillFxTimeline.ANVIL_IMPACT_MS && ms < KillFxTimeline.ANVIL_SINK_START_MS) {
            float p = (ms - KillFxTimeline.ANVIL_IMPACT_MS) / (float) (KillFxTimeline.ANVIL_SINK_START_MS - KillFxTimeline.ANVIL_IMPACT_MS);
            float sy = groundY - KillFxTimeline.anvilRestHeight() * unit - unit * 1.1F;
            for (int i = 0; i < 4; i++) {
                double a = p * Math.PI * 3.0 + i * Math.PI / 2.0;
                float sx = cx + (float) Math.cos(a) * unit * 0.9F;
                float syy = sy + (float) Math.sin(a) * unit * 0.25F;
                dot(c, sx, syy, 3, withAlpha(0xFFF2D04A, 1.0F - p * 0.5F));
                dot(c, sx, syy, 1, 0xFFFFFFFF);
            }
            if (p < 0.25F) {
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI + Math.PI * (i + 0.5) / 8.0;
                    float d = 4.0F + p * 4.0F * 14.0F;
                    dot(c, cx + (float) Math.cos(a) * d, groundY - 1 + (float) Math.sin(a) * d * 0.4F, 2,
                            withAlpha(0xFF8A7A66, 1.0F - p * 4.0F));
                }
            }
        }
    }
}
