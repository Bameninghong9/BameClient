package com.bame.client.render;

import com.bame.client.gui.AlphaMaskTexture;
import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import com.bame.client.module.SpotifyHudModule;
import com.bame.client.spotify.SpotifyService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Spotify-/Medien-HUD in zwei Stilen (Classic, Card), immer mit Steuerknöpfen (Zurück, Play/Pause, Weiter).
 * Beide Stile zeichnen den Hintergrund nach dem Modus aus dem HUD-Editor ({@link SpotifyHudModule#bgMode}).
 * Die Knopf-Rechtecke des zuletzt gezeichneten (echten, nicht Editor-) Frames werden in skalierten GUI-Koordinaten
 * gemerkt, damit sie bei offenem Chat angeklickt werden können ({@link #handleClick(double, double)}).
 */
public class SpotifyHudRenderer {
    public static final int WIDTH = 214;
    public static final int HEIGHT = 64;

    public static final int BTN_PREV = 0, BTN_PLAY = 1, BTN_NEXT = 2;

    // ---- Knopf-Trefferflächen (Bildschirm-Koordinaten, skaliert); nur Render-Thread ----
    private static final float[][] HIT = new float[3][4];
    private static boolean hitValid = false;
    private static long hitFrameMs = 0;
    private static int pressedButton = -1;
    private static long pressedAtMs = 0;

    // ---- Vektor-Icons als weiße Alpha-Masken (4x4 überabgetastet, damit sie auch skaliert glatt wirken) ----
    private static final int ICON_RES = 40;
    private static final int CIRCLE_RES = 72;
    private static final AlphaMaskTexture CIRCLE = mask("circle", CIRCLE_RES, (u, v) -> {
        double dx = u - 0.5, dy = v - 0.5;
        return dx * dx + dy * dy <= 0.25;
    });
    private static final AlphaMaskTexture PLAY = mask("play", ICON_RES, (u, v) ->
        inTriangle(u, v, 0.20, 0.08, 0.20, 0.92, 0.92, 0.50));
    private static final AlphaMaskTexture PAUSE = mask("pause", ICON_RES, (u, v) ->
        v >= 0.08 && v <= 0.92 && ((u >= 0.16 && u <= 0.40) || (u >= 0.60 && u <= 0.84)));
    private static final AlphaMaskTexture NEXT = mask("next", ICON_RES, (u, v) ->
        inTriangle(u, v, 0.08, 0.10, 0.08, 0.90, 0.72, 0.50) || (u >= 0.74 && u <= 0.90 && v >= 0.10 && v <= 0.90));
    private static final AlphaMaskTexture PREV = mask("prev", ICON_RES, (u, v) ->
        inTriangle(u, v, 0.92, 0.10, 0.92, 0.90, 0.28, 0.50) || (u >= 0.10 && u <= 0.26 && v >= 0.10 && v <= 0.90));

    private interface Shape {
        boolean contains(double u, double v);
    }

    private static AlphaMaskTexture mask(String name, int res, Shape shape) {
        return new AlphaMaskTexture(Identifier.of("bameclient", "dynamic/spotify_" + name), res, res, () -> {
            int[] alpha = new int[res * res];
            final int ss = 4;
            for (int y = 0; y < res; y++) {
                for (int x = 0; x < res; x++) {
                    int hits = 0;
                    for (int sy = 0; sy < ss; sy++) {
                        for (int sx = 0; sx < ss; sx++) {
                            double u = (x + (sx + 0.5) / ss) / res;
                            double v = (y + (sy + 0.5) / ss) / res;
                            if (shape.contains(u, v)) hits++;
                        }
                    }
                    alpha[y * res + x] = hits * 255 / (ss * ss);
                }
            }
            return alpha;
        });
    }

    private static boolean inTriangle(double px, double py, double ax, double ay, double bx, double by, double cx, double cy) {
        double d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by);
        double d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy);
        double d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0;
        boolean pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static void drawMask(DrawContext c, AlphaMaskTexture mask, int res, int x, int y, int size, int color) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED, mask.id(), x, y, 0.0f, 0.0f, size, size, res, res, res, res, color);
    }

    // ---- Maße je Stil (Steuerknöpfe sind immer sichtbar) ----

    private static boolean card() {
        return SpotifyHudModule.style() == SpotifyHudModule.STYLE_CARD;
    }

    private static int naturalWidth() {
        return card() ? 180 : WIDTH;
    }

    private static int naturalHeight() {
        return card() ? 110 : HEIGHT;
    }

    private static int minWidth() {
        return card() ? 140 : 160;
    }

    private static int minHeight() {
        return card() ? naturalHeight() : 60;
    }

    public static int getWidth() {
        return SpotifyHudModule.customWidth > 0 ? Math.max(minWidth(), SpotifyHudModule.customWidth) : naturalWidth();
    }

    public static int getHeight() {
        return SpotifyHudModule.customHeight > 0 ? Math.max(minHeight(), SpotifyHudModule.customHeight) : naturalHeight();
    }

    /** Zeit-Texte bis 100 Minuten: einmal erzeugt statt String.format zweimal pro Frame. Nur Render-Thread. */
    private static final String[] TIME_CACHE = new String[6000];

    public static String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        if (totalSeconds < TIME_CACHE.length) {
            String cached = TIME_CACHE[totalSeconds];
            if (cached == null) {
                cached = formatTimeUncached(totalSeconds);
                TIME_CACHE[totalSeconds] = cached;
            }
            return cached;
        }
        return formatTimeUncached(totalSeconds);
    }

    private static String formatTimeUncached(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m, s);
    }

    // ---- Klick-Steuerung (Chat offen) ----

    /** Knopf unter dem Mauszeiger (skalierte GUI-Koordinaten) oder -1. */
    public static int buttonAt(double mx, double my) {
        if (!hitValid || !SpotifyHudModule.enabled || System.currentTimeMillis() - hitFrameMs > 500) return -1;
        for (int i = 0; i < 3; i++) {
            float[] r = HIT[i];
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) return i;
        }
        return -1;
    }

    /** Führt den Knopf unter dem Mauszeiger aus; {@code true}, wenn ein Knopf getroffen wurde (Klick verbrauchen). */
    public static boolean handleClick(double mx, double my) {
        int b = buttonAt(mx, my);
        if (b < 0) return false;
        pressedButton = b;
        pressedAtMs = System.currentTimeMillis();
        SpotifyService.control(switch (b) {
            case BTN_PREV -> SpotifyService.Command.PREVIOUS;
            case BTN_NEXT -> SpotifyService.Command.NEXT;
            default -> SpotifyService.Command.PLAY_PAUSE;
        });
        return true;
    }

    // ---- Zeichnen ----

    /** Pro Frame gesammelte Werte, damit die Stil-Methoden schlank bleiben. */
    private static final class Frame {
        DrawContext c;
        TextRenderer tr;
        int x, y, w, h;
        float scale;
        boolean preview, shadow, playing;
        String title, artist, album;
        float pos, dur, prog;
        int accent;
        int hover = -1;
    }

    public static void render(DrawContext c, int x, int y, float scale, boolean isPreview) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Make sure background service is running when enabled (Start im Hintergrund, gedrosselt)
        if (SpotifyHudModule.enabled) {
            SpotifyService.ensureRunning();
        }

        SpotifyService.checkTextureUpdate();

        if (!isPreview) hitValid = false;

        boolean hasMedia = SpotifyService.hasMedia;
        boolean isPlaying = SpotifyService.isPlaying;

        // Auto-hide when not previewing and not playing
        if (!isPreview && SpotifyHudModule.autoHide && !isPlaying) {
            return;
        }

        Frame f = new Frame();
        f.c = c;
        f.tr = client.textRenderer;
        f.x = x;
        f.y = y;
        f.w = getWidth();
        f.h = getHeight();
        f.scale = scale;
        f.preview = isPreview;
        boolean demo = isPreview && !hasMedia;
        f.playing = demo || isPlaying;
        f.title = demo ? "I Am the Highway" : SpotifyService.title;
        if (f.title.isEmpty()) f.title = "Nothing Playing";
        f.artist = demo ? "Audioslave" : SpotifyService.artist;
        f.album = demo ? "Audioslave" : SpotifyService.album;
        f.pos = demo ? 21.0f : SpotifyService.getInterpolatedPosition();
        f.dur = demo ? 334.9f : SpotifyService.duration;
        f.prog = f.dur > 0 ? Math.clamp(f.pos / f.dur, 0f, 1f) : 0f;
        f.accent = accentColor();

        if (!isPreview && client.currentScreen instanceof ChatScreen && !client.mouse.isCursorLocked()) {
            f.hover = buttonAt(client.mouse.getScaledX(client.getWindow()), client.mouse.getScaledY(client.getWindow()));
        }

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        if (card()) renderCard(f);
        else renderClassic(f);

        c.getMatrices().popMatrix();
    }

    /** Akzent für Balken/Hover: Theme-Hauptfarbe oder (Option) aufgehellte Durchschnittsfarbe des Covers. */
    // Letzte Umrechnung der Cover-Farbe (ändert sich nur mit dem Cover); nur Render-Thread
    private static int accentFor = 0;
    private static int accentCached = 0;
    private static boolean accentValid = false;

    private static int accentColor() {
        if (SpotifyHudModule.accentMode == 1 && SpotifyService.hasCoverTexture) {
            int rgb = SpotifyService.coverColor;
            if (accentValid && accentFor == rgb) return accentCached;
            float[] hsb = java.awt.Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            // Durchschnittsfarben sind oft matt: Sättigung und Helligkeit anheben, damit der Balken leuchtet
            float sat = hsb[1] < 0.08f ? hsb[1] : Math.max(hsb[1], 0.55f);
            float bri = Math.max(hsb[2], 0.85f);
            accentCached = 0xFF000000 | (java.awt.Color.HSBtoRGB(hsb[0], sat, bri) & 0xFFFFFF);
            accentFor = rgb;
            accentValid = true;
            return accentCached;
        }
        return 0xFF000000 | GuiTheme.accent();
    }

    // Classic: Cover links, Titel/Interpret/Album, Balken mit Zeiten, Knöpfe mittig zwischen den Zeiten
    private static void renderClassic(Frame f) {
        f.shadow = SpotifyHudModule.bgMode == 0 || SpotifyHudModule.bgMode == 3;
        StatusHudRenderer.drawBoxBg(f.c, 0, 0, f.w, f.h, SpotifyHudModule.bgMode, SpotifyHudModule.outlineColor);

        int rowH = 16;
        int barY = f.h - rowH - 6;
        int coverSize = Math.max(20, Math.min(32, barY - 10));
        drawCover(f, 6, 6, coverSize, 4);

        int textX = 6 + coverSize + 8;
        int maxTextW = Math.max(20, f.w - textX - 6);
        drawText(f, 0, f.title, textX, 7, maxTextW, 0xFFFFFFFF);
        if (!f.artist.isEmpty()) drawText(f, 1, f.artist, textX, 18, maxTextW, 0xFFB0B8C5);
        if (!f.album.isEmpty() && barY >= 40) drawText(f, 2, f.album, textX, 29, maxTextW, 0xFF7E8494);

        int barX = 6;
        int barW = Math.max(30, f.w - 12);
        drawBar(f, barX, barY, barW, 2, true);

        int rowY = barY + 5;
        drawTimes(f, barX, barW, rowY + (rowH - 8) / 2);
        drawControls(f, f.w / 2, rowY + rowH / 2, 14, 8, 18);
    }

    // Card: großes Cover, Balken, Zeiten, Knöpfe darunter. Der Hintergrund folgt dem Modus aus dem HUD-Editor;
    // "Dark" ist bei Card die abgerundete, in der Cover-Farbe getönte Karte.
    private static void renderCard(Frame f) {
        drawCardBg(f);

        int pad = 8;
        int coverSize = 48;
        drawCover(f, pad, pad, coverSize, 6);

        int textX = pad + coverSize + 9;
        int maxTextW = Math.max(20, f.w - textX - pad);
        drawText(f, 0, f.title, textX, pad + 6, maxTextW, 0xFFFFFFFF);
        if (!f.artist.isEmpty()) drawText(f, 1, f.artist, textX, pad + 18, maxTextW, 0xFFC4CAD4);
        if (!f.album.isEmpty()) drawText(f, 2, f.album, textX, pad + 30, maxTextW, 0xFF8A91A0);

        int barY = pad + coverSize + 10;
        int barW = f.w - 2 * pad;
        drawBar(f, pad, barY, barW, 3, true);
        drawTimes(f, pad, barW, barY + 7);

        drawControls(f, f.w / 2, barY + 7 + 8 + 13, 20, 10, 28);
    }

    private static final int CARD_RADIUS = 8;

    /** Card-Hintergrund je Modus: 0 Transparent, 1 Tooltip, 2 Cover-Tönung, 3 nur Umriss (eigene Farbe). */
    private static void drawCardBg(Frame f) {
        DrawContext c = f.c;
        int mode = SpotifyHudModule.bgMode;
        f.shadow = mode == 0 || mode == 3;
        switch (mode) {
            case 2 -> {
                int tint = SpotifyService.hasCoverTexture ? SpotifyService.coverColor : 0xFF2A2F3A;
                int bg = GuiTheme.blend(0xFF0E1014, tint, 0.30f);
                CustomGuiUtils.fillUltraRounded(c, 0, 0, f.w, f.h, GuiTheme.alpha(bg, 0xE8), CARD_RADIUS);
                // Sanfter Lichtschein in der oberen Hälfte, damit die Karte nicht flach wirkt
                CustomGuiUtils.fillUltraRounded(c, 0, 0, f.w, f.h / 2, GuiTheme.alpha(tint, 0x18), CARD_RADIUS);
                CustomGuiUtils.drawUltraRoundedOutline(c, 0, 0, f.w, f.h, 0x22FFFFFF, CARD_RADIUS);
            }
            case 3 -> CustomGuiUtils.drawUltraRoundedOutline(c, 0, 0, f.w, f.h, SpotifyHudModule.outlineColor, CARD_RADIUS);
            default -> StatusHudRenderer.drawBoxBg(c, 0, 0, f.w, f.h, mode, SpotifyHudModule.outlineColor);
        }
    }

    // ---- Bausteine ----

    private static void drawCover(Frame f, int x, int y, int size, int radius) {
        DrawContext c = f.c;
        if (SpotifyService.hasCoverTexture) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, SpotifyService.COVER_ID, x, y, 0.0f, 0.0f, size, size,
                SpotifyService.coverWidth, SpotifyService.coverHeight,
                SpotifyService.coverWidth, SpotifyService.coverHeight, 0xFFFFFFFF);
            CustomGuiUtils.drawUltraRoundedOutline(c, x, y, size, size, 0x33FFFFFF, radius);
        } else {
            CustomGuiUtils.fillUltraRounded(c, x, y, size, size, 0x66252A34, radius);
            CustomGuiUtils.drawUltraRoundedOutline(c, x, y, size, size, 0x338E95A4, radius);
            int tw = f.tr.getWidth("♫");
            c.drawText(f.tr, "♫", x + (size - tw) / 2, y + (size - 8) / 2, 0xFF8E95A4, false);
        }
    }

    private static void drawBar(Frame f, int x, int y, int w, int thickness, boolean thumb) {
        DrawContext c = f.c;
        float r = thickness / 2.0f;
        CustomGuiUtils.fillUltraRounded(c, x, y, w, thickness, 0x38FFFFFF, r);
        int fillW = Math.round(w * f.prog);
        if (fillW > 0) CustomGuiUtils.fillUltraRounded(c, x, y, fillW, thickness, f.accent, r);
        if (thumb) {
            int ts = thickness + 4;
            drawMask(c, CIRCLE, CIRCLE_RES, x + fillW - ts / 2, y + thickness / 2 - ts / 2, ts, 0xFFFFFFFF);
        }
    }

    private static void drawTimes(Frame f, int barX, int barW, int y) {
        Text left = CustomGuiUtils.getFontText(formatTime((int) f.pos));
        Text right = CustomGuiUtils.getFontText(formatTime((int) f.dur));
        f.c.drawText(f.tr, left, barX, y, 0xFF8E95A4, f.shadow);
        f.c.drawText(f.tr, right, barX + barW - f.tr.getWidth(right), y, 0xFF8E95A4, f.shadow);
    }

    /** Zurück, Play/Pause (weißer Kreis) und Weiter, zentriert um (cx, cy) in lokalen HUD-Koordinaten. */
    private static void drawControls(Frame f, int cx, int cy, int playSize, int iconSize, int gap) {
        DrawContext c = f.c;
        long now = System.currentTimeMillis();
        boolean pressFx = !f.preview && now - pressedAtMs < 140;

        int[] centers = {cx - gap, cx, cx + gap};
        for (int i = 0; i < 3; i++) {
            boolean hovered = f.hover == i;
            boolean pressed = pressFx && pressedButton == i;
            int bx = centers[i];
            if (i == BTN_PLAY) {
                int size = playSize + (hovered && !pressed ? 2 : 0) - (pressed ? 2 : 0);
                if (hovered) {
                    int halo = size + 6;
                    drawMask(c, CIRCLE, CIRCLE_RES, bx - halo / 2, cy - halo / 2, halo, GuiTheme.alpha(f.accent, 0x40));
                }
                drawMask(c, CIRCLE, CIRCLE_RES, bx - size / 2, cy - size / 2, size, 0xFFFFFFFF);
                int glyph = Math.max(4, Math.round(size * 0.44f));
                drawMask(c, f.playing ? PAUSE : PLAY, ICON_RES, bx - glyph / 2, cy - glyph / 2, glyph, 0xFF121418);
                record(f, i, bx - playSize / 2 - 2, cy - playSize / 2 - 2, playSize + 4, playSize + 4);
            } else {
                if (hovered) {
                    int halo = iconSize + 8;
                    drawMask(c, CIRCLE, CIRCLE_RES, bx - halo / 2, cy - halo / 2, halo, 0x26FFFFFF);
                }
                int col = pressed ? f.accent : (hovered ? 0xFFFFFFFF : 0xFFC6CBD4);
                int size = pressed ? iconSize - 1 : iconSize;
                drawMask(c, i == BTN_PREV ? PREV : NEXT, ICON_RES, bx - size / 2, cy - size / 2, size, col);
                record(f, i, bx - iconSize / 2 - 4, cy - iconSize / 2 - 4, iconSize + 8, iconSize + 8);
            }
        }
        if (!f.preview) {
            hitValid = true;
            hitFrameMs = now;
        }
    }

    private static void record(Frame f, int idx, int lx, int ly, int lw, int lh) {
        if (f.preview) return;
        float[] r = HIT[idx];
        r[0] = f.x + lx * f.scale;
        r[1] = f.y + ly * f.scale;
        r[2] = lw * f.scale;
        r[3] = lh * f.scale;
    }

    // Gekürzte Texte je Zeile zwischenspeichern: neu berechnet wird nur, wenn sich Text oder Breite ändern
    private static final String[] FIT_IN = new String[3];
    private static final int[] FIT_W = new int[3];
    private static final Text[] FIT_OUT = new Text[3];

    private static void drawText(Frame f, int slot, String s, int x, int y, int maxW, int color) {
        if (s == null || s.isEmpty()) return;
        if (FIT_OUT[slot] == null || FIT_W[slot] != maxW || !s.equals(FIT_IN[slot])) {
            FIT_IN[slot] = s;
            FIT_W[slot] = maxW;
            FIT_OUT[slot] = fit(f.tr, s, maxW);
        }
        f.c.drawText(f.tr, FIT_OUT[slot], x, y, color, f.shadow);
    }

    private static Text fit(TextRenderer tr, String s, int maxW) {
        String norm = CustomGuiUtils.normalizeFancyText(s);
        Text full = Text.literal(norm).setStyle(CustomGuiUtils.SANS_STYLE);
        if (tr.getWidth(full) <= maxW) return full;
        String ell = "...";
        int lo = 0, hi = norm.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (tr.getWidth(Text.literal(norm.substring(0, mid).stripTrailing() + ell).setStyle(CustomGuiUtils.SANS_STYLE)) <= maxW) lo = mid;
            else hi = mid - 1;
        }
        return Text.literal(norm.substring(0, lo).stripTrailing() + ell).setStyle(CustomGuiUtils.SANS_STYLE);
    }
}
