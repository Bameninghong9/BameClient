package com.bame.client.wallpaper;

import com.bame.client.BameClientConfig;
import com.bame.client.sound.ClientSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hintergrundbild der Menüs. Datei lesen, dekodieren, hochskalieren und weichzeichnen passiert in einem
 * Hintergrund-Thread; der Render-Thread registriert nur noch die fertige Textur (GPU-Upload). Bis dahin bleibt das
 * bisherige Bild (bzw. keins) sichtbar – das Öffnen des Menüs wartet nicht mehr auf die Festplatte.
 */
public class WallpaperManager {
    public static final Identifier WALLPAPER_ID = Identifier.of("caeserclient", "custom_wallpaper");
    private static final List<String> wallpapers = new ArrayList<>();
    private static NativeImageBackedTexture texture = null;
    private static String currentLoadedFile = "";
    private static float currentLoadedBlur = -1f;
    private static int imageW = 0;
    private static int imageH = 0;
    private static boolean hasTexture = false;

    /** Zuletzt dekodierte Datei (unveränderlich, darf zwischen Threads geteilt werden). */
    private record Decoded(String file, int w, int h, int[] argb) {}
    /** Ergebnis eines Hintergrund-Auftrags; {@code listing != null}, wenn die Ordnerliste neu gelesen wurde. */
    private record Prepared(long gen, String file, float blur, NativeImage image, List<String> listing, String chosen) {}

    private static volatile Decoded decoded = null;
    private static final AtomicReference<Prepared> ready = new AtomicReference<>();
    private static volatile long requestGen = 0L;      // schreibt nur der Render-Thread
    private static boolean loading = false;            // nur Render-Thread
    private static String requestedFile = null;        // nur Render-Thread
    private static float requestedBlur = -1f;          // nur Render-Thread
    private static long missingCheckedAt = 0L;         // nur Render-Thread: kein Bild gefunden, nicht jeden Frame neu suchen
    private static final long MISSING_RETRY_MS = 2000L;

    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CaeserClient-Wallpaper");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    public static Path getWallpaperDir() {
        Path dir = BameClientConfig.BASE_CONFIG_DIR.resolve("wallpapers");
        if (!Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (Exception ignored) {}
        }
        return dir;
    }

    public static void openFolder() {
        ClientSoundManager.playClick();
        Path dir = getWallpaperDir();
        try {
            Util.getOperatingSystem().open(dir.toFile());
        } catch (Exception e) {
            try {
                java.awt.Desktop.getDesktop().open(dir.toFile());
            } catch (Exception ignored) {}
        }
    }

    public static List<String> getWallpapers() {
        if (wallpapers.isEmpty()) {
            refreshWallpapers();
        }
        return wallpapers;
    }

    public static void refreshWallpapers() {
        applyListing(listWallpaperFiles());
    }

    /** Dateinamen der Bilder im Ordner, sortiert (darf im Hintergrund laufen). */
    private static List<String> listWallpaperFiles() {
        List<String> out = new ArrayList<>();
        Path dir = getWallpaperDir();
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".bmp");
                }).forEach(p -> out.add(p.getFileName().toString()));
            } catch (Exception ignored) {}
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Gleiche Auswahlregel wie bisher: unbekannte Auswahl = erstes Bild, leerer Ordner = keine Auswahl. */
    private static String chooseFrom(List<String> listing, String selected) {
        if (listing.isEmpty()) return "";
        if (selected == null || !listing.contains(selected)) return listing.get(0);
        return selected;
    }

    /** Render-Thread: Ordnerliste und Auswahl übernehmen. */
    private static void applyListing(List<String> listing) {
        wallpapers.clear();
        wallpapers.addAll(listing);
        BameClientConfig.selectedWallpaper = chooseFrom(listing, BameClientConfig.selectedWallpaper);
    }

    public static void nextWallpaper() {
        ClientSoundManager.playClick();
        refreshWallpapers();
        if (wallpapers.isEmpty()) return;
        int idx = wallpapers.indexOf(BameClientConfig.selectedWallpaper);
        idx = (idx + 1) % wallpapers.size();
        BameClientConfig.selectedWallpaper = wallpapers.get(idx);
        BameClientConfig.save();
        updateTexture(true);
    }

    public static void previousWallpaper() {
        ClientSoundManager.playClick();
        refreshWallpapers();
        if (wallpapers.isEmpty()) return;
        int idx = wallpapers.indexOf(BameClientConfig.selectedWallpaper);
        idx = (idx - 1 + wallpapers.size()) % wallpapers.size();
        BameClientConfig.selectedWallpaper = wallpapers.get(idx);
        BameClientConfig.save();
        updateTexture(true);
    }

    public static boolean hasWallpaper() {
        return hasTexture;
    }

    /** Render-Thread: Bild schon vorbereiten (z. B. beim Öffnen des Hubs), damit es beim Wechsel ins Panel bereitsteht. */
    public static void prefetch() {
        if (BameClientConfig.customWallpaper) updateTexture(false);
    }

    /**
     * Render-Thread. Prüft, ob das gewählte Bild (bzw. die Weichzeichnung) geladen ist, und stößt sonst das Laden im
     * Hintergrund an; ein fertiges Ergebnis wird hier als Textur registriert. {@code force} lädt auch bei
     * unveränderter Auswahl neu.
     */
    public static void updateTexture(boolean force) {
        applyReady();

        String targetName = BameClientConfig.selectedWallpaper == null ? "" : BameClientConfig.selectedWallpaper;
        float targetBlur = BameClientConfig.wallpaperBlur;

        boolean fileChanged = !targetName.equals(currentLoadedFile);
        boolean blurChanged = Math.abs(targetBlur - currentLoadedBlur) > 0.02f;

        if (!force && !fileChanged && !blurChanged && hasTexture) {
            return;
        }
        boolean sameRequest = targetName.equals(requestedFile)
                && Math.abs(targetBlur - requestedBlur) <= 0.02f;
        if (!force && loading && sameRequest) return; // läuft schon
        if (!force && !hasTexture && !loading && sameRequest
                && System.currentTimeMillis() - missingCheckedAt < MISSING_RETRY_MS) {
            return; // eben erst nichts gefunden
        }

        final long gen = ++requestGen;
        final String name = targetName;
        final float blur = targetBlur;
        requestedFile = targetName;
        requestedBlur = targetBlur;
        loading = true;
        try {
            LOADER.execute(() -> prepare(gen, name, blur));
        } catch (Exception e) {
            loading = false;
        }
    }

    /** Render-Thread: fertiges Ergebnis übernehmen (Textur registrieren bzw. leeren). */
    private static void applyReady() {
        Prepared p = ready.getAndSet(null);
        if (p == null) return;
        if (p.gen() != requestGen) { // überholt
            if (p.image() != null) p.image().close();
            return;
        }
        loading = false;
        if (p.listing() != null) {
            wallpapers.clear();
            wallpapers.addAll(p.listing());
            BameClientConfig.selectedWallpaper = p.chosen();
            requestedFile = p.chosen();
        }
        if (p.image() == null) {
            clearTextureOnly();
            missingCheckedAt = System.currentTimeMillis();
            return;
        }
        try {
            imageW = p.image().getWidth();
            imageH = p.image().getHeight();
            if (texture != null) {
                try { texture.close(); } catch (Exception ignored) {}
            }
            texture = new NativeImageBackedTexture(() -> "custom_wallpaper", p.image());
            MinecraftClient.getInstance().getTextureManager().registerTexture(WALLPAPER_ID, texture);
            hasTexture = true;
            currentLoadedFile = p.file();
            currentLoadedBlur = p.blur();
        } catch (Exception e) {
            e.printStackTrace();
            clearTexture();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Hintergrund-Thread
    // ------------------------------------------------------------------------------------------------

    private static void prepare(long gen, String targetName, float targetBlur) {
        if (gen != requestGen) return; // schon überholt, neuerer Auftrag folgt
        NativeImage result = null;
        List<String> listing = null;
        String chosen = targetName;
        try {
            Path file = (targetName != null && !targetName.isEmpty()) ? getWallpaperDir().resolve(targetName) : null;
            if (file == null || !Files.exists(file)) {
                listing = listWallpaperFiles();
                chosen = chooseFrom(listing, targetName);
                file = (chosen != null && !chosen.isEmpty()) ? getWallpaperDir().resolve(chosen) : null;
            }
            if (file != null && Files.exists(file)) {
                Decoded d = decoded;
                if (d == null || !d.file().equals(chosen)) {
                    d = decode(file, chosen);
                    if (d != null) decoded = d;
                }
                if (d != null && gen == requestGen) {
                    result = targetBlur > 0.01f ? createBlurredImage(d, targetBlur) : toImage(d);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        Prepared old = ready.getAndSet(new Prepared(gen, chosen, targetBlur, result, listing, chosen));
        if (old != null && old.image() != null) old.image().close();
    }

    private static Decoded decode(Path file, String name) throws Exception {
        // 1. Try Java standard ImageIO (supports JPG, JPEG, PNG, BMP natively)
        BufferedImage bImg = null;
        try {
            bImg = ImageIO.read(file.toFile());
        } catch (Throwable ignored) {}

        if (bImg != null) {
            int w = bImg.getWidth();
            int h = bImg.getHeight();

            // If image is a low-res thumbnail, upscale with bicubic filtering for smooth quality
            if (w < 1280 || h < 720) {
                float scale = Math.min(8.0f, Math.max(1280.0f / w, 720.0f / h));
                if (scale > 1.0f) {
                    int newW = Math.round(w * scale);
                    int newH = Math.round(h * scale);
                    BufferedImage scaled = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g2d = scaled.createGraphics();
                    g2d.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2d.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.drawImage(bImg, 0, 0, newW, newH, null);
                    g2d.dispose();
                    bImg = scaled;
                    w = newW;
                    h = newH;
                }
            }

            int[] pixels = new int[w * h];
            bImg.getRGB(0, 0, w, h, pixels, 0, w);
            return new Decoded(name, w, h, pixels);
        }
        // 2. Fallback to Minecraft NativeImage.read
        try (InputStream in = new FileInputStream(file.toFile()); NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth();
            int h = img.getHeight();
            int[] pixels = new int[w * h];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pixels[y * w + x] = img.getColorArgb(x, y);
                }
            }
            return new Decoded(name, w, h, pixels);
        }
    }

    private static NativeImage toImage(Decoded d) {
        NativeImage copy = new NativeImage(d.w(), d.h(), false);
        int[] px = d.argb();
        for (int y = 0; y < d.h(); y++) {
            int row = y * d.w();
            for (int x = 0; x < d.w(); x++) {
                copy.setColorArgb(x, y, px[row + x]);
            }
        }
        return copy;
    }

    private static NativeImage createBlurredImage(Decoded src, float blur) {
        int origW = src.w();
        int origH = src.h();
        int[] px = src.argb();
        int downscale = 1 + Math.round(blur * 10.0f);
        int targetW = Math.max(8, origW / downscale);
        int targetH = Math.max(8, origH / downscale);

        NativeImage small = new NativeImage(targetW, targetH, false);
        for (int y = 0; y < targetH; y++) {
            int srcY = Math.min(origH - 1, (y * origH) / targetH);
            for (int x = 0; x < targetW; x++) {
                int srcX = Math.min(origW - 1, (x * origW) / targetW);
                small.setColorArgb(x, y, px[srcY * origW + srcX]);
            }
        }

        int passes = 1 + Math.round(blur * 2.0f);
        for (int p = 0; p < passes; p++) {
            boxBlur(small, targetW, targetH);
        }

        return small;
    }

    private static void boxBlur(NativeImage img, int w, int h) {
        // Horizontal pass
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) row[x] = img.getColorArgb(x, y);
            for (int x = 0; x < w; x++) {
                int x0 = Math.max(0, x - 1);
                int x1 = x;
                int x2 = Math.min(w - 1, x + 1);
                img.setColorArgb(x, y, avg3(row[x0], row[x1], row[x2]));
            }
        }
        // Vertical pass
        int[] col = new int[h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) col[y] = img.getColorArgb(x, y);
            for (int y = 0; y < h; y++) {
                int y0 = Math.max(0, y - 1);
                int y1 = y;
                int y2 = Math.min(h - 1, y + 1);
                img.setColorArgb(x, y, avg3(col[y0], col[y1], col[y2]));
            }
        }
    }

    private static int avg3(int c1, int c2, int c3) {
        int a = (((c1 >> 24) & 0xFF) + ((c2 >> 24) & 0xFF) + ((c3 >> 24) & 0xFF)) / 3;
        int r = (((c1 >> 16) & 0xFF) + ((c2 >> 16) & 0xFF) + ((c3 >> 16) & 0xFF)) / 3;
        int g = (((c1 >> 8) & 0xFF) + ((c2 >> 8) & 0xFF) + ((c3 >> 8) & 0xFF)) / 3;
        int b = ((c1 & 0xFF) + (c2 & 0xFF) + (c3 & 0xFF)) / 3;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Render-Thread: Textur und Bild-Cache verwerfen; laufende Aufträge werden ignoriert. */
    public static void clearTexture() {
        requestGen++;
        loading = false;
        requestedFile = null;
        requestedBlur = -1f;
        decoded = null;
        clearTextureOnly();
    }

    private static void clearTextureOnly() {
        if (texture != null) {
            try { texture.close(); } catch (Exception ignored) {}
            texture = null;
        }
        hasTexture = false;
        currentLoadedFile = "";
        currentLoadedBlur = -1f;
    }

    public static void render(DrawContext c, int x, int y, int w, int h, float alphaProgress) {
        render(c, x, y, w, h, alphaProgress, false, 0f, 0f);
    }

    /**
     * Wie {@link #render(DrawContext, int, int, int, int, float)}; mit {@code parallax} wird ein um 4 % vergrößerter
     * Ausschnitt gezeigt und um {@code parX}/{@code parY} (-1..1) innerhalb des Randes verschoben (3D-GUI-Parallax).
     * Nur die UV-Koordinaten ändern sich, die Textur bleibt dieselbe.
     */
    public static void render(DrawContext c, int x, int y, int w, int h, float alphaProgress, boolean parallax, float parX, float parY) {
        if (!BameClientConfig.customWallpaper) return;
        updateTexture(false);
        if (!hasTexture || imageW <= 0 || imageH <= 0) return;

        float boxAspect = (float) w / (float) h;
        float imageAspect = (float) imageW / (float) imageH;

        float u = 0.0f;
        float v = 0.0f;
        float regionW = imageW;
        float regionH = imageH;

        if (boxAspect > imageAspect) {
            regionH = imageW / boxAspect;
            v = (imageH - regionH) / 2.0f;
        } else {
            regionW = imageH * boxAspect;
            u = (imageW - regionW) / 2.0f;
        }

        if (parallax) {
            float zoomedW = regionW * 0.96f;
            float zoomedH = regionH * 0.96f;
            u += (regionW - zoomedW) * 0.5f * (1.0f + Math.clamp(parX, -1.0f, 1.0f));
            v += (regionH - zoomedH) * 0.5f * (1.0f + Math.clamp(parY, -1.0f, 1.0f));
            regionW = zoomedW;
            regionH = zoomedH;
        }

        u = Math.max(0.0f, Math.min((float)imageW - 1.0f, u));
        v = Math.max(0.0f, Math.min((float)imageH - 1.0f, v));
        int regW = Math.max(1, Math.min(imageW, Math.round(regionW)));
        int regH = Math.max(1, Math.min(imageH, Math.round(regionH)));

        float brightness = Math.clamp(BameClientConfig.wallpaperBrightness, 0.1f, 1.0f);
        int tintVal = Math.round(brightness * 255.0f);
        int tintAlpha = Math.round(alphaProgress * 255.0f);
        int tintColor = (tintAlpha << 24) | (tintVal << 16) | (tintVal << 8) | tintVal;

        c.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            WALLPAPER_ID,
            x, y,
            u, v,
            w, h,
            regW, regH,
            imageW, imageH,
            tintColor
        );
    }
}
