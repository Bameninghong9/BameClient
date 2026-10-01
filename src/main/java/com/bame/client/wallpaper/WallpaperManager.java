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

public class WallpaperManager {
    public static final Identifier WALLPAPER_ID = Identifier.of("caeserclient", "custom_wallpaper");
    private static final List<String> wallpapers = new ArrayList<>();
    private static NativeImageBackedTexture texture = null;
    private static NativeImage rawImage = null;
    private static String currentLoadedFile = "";
    private static float currentLoadedBlur = -1f;
    private static int imageW = 0;
    private static int imageH = 0;
    private static boolean hasTexture = false;

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
        wallpapers.clear();
        Path dir = getWallpaperDir();
        if (Files.exists(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".bmp");
                }).forEach(p -> wallpapers.add(p.getFileName().toString()));
            } catch (Exception ignored) {}
        }
        Collections.sort(wallpapers, String.CASE_INSENSITIVE_ORDER);

        if (wallpapers.isEmpty()) {
            BameClientConfig.selectedWallpaper = "";
        } else if (BameClientConfig.selectedWallpaper == null || !wallpapers.contains(BameClientConfig.selectedWallpaper)) {
            BameClientConfig.selectedWallpaper = wallpapers.get(0);
        }
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

    public static void updateTexture(boolean force) {
        String targetName = BameClientConfig.selectedWallpaper;
        float targetBlur = BameClientConfig.wallpaperBlur;

        Path file = (targetName != null && !targetName.isEmpty()) ? getWallpaperDir().resolve(targetName) : null;
        if (file == null || !Files.exists(file)) {
            refreshWallpapers();
            targetName = BameClientConfig.selectedWallpaper;
            file = (targetName != null && !targetName.isEmpty()) ? getWallpaperDir().resolve(targetName) : null;
        }

        if (file == null || !Files.exists(file)) {
            clearTexture();
            return;
        }

        boolean fileChanged = !targetName.equals(currentLoadedFile);
        boolean blurChanged = Math.abs(targetBlur - currentLoadedBlur) > 0.02f;

        if (!force && !fileChanged && !blurChanged && hasTexture) {
            return;
        }

        try {
            if (fileChanged || rawImage == null) {
                if (rawImage != null) {
                    try { rawImage.close(); } catch (Exception ignored) {}
                    rawImage = null;
                }

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

                    rawImage = new NativeImage(w, h, false);
                    int[] pixels = new int[w * h];
                    bImg.getRGB(0, 0, w, h, pixels, 0, w);
                    for (int y = 0; y < h; y++) {
                        int rowOffset = y * w;
                        for (int x = 0; x < w; x++) {
                            rawImage.setColorArgb(x, y, pixels[rowOffset + x]);
                        }
                    }
                } else {
                    // 2. Fallback to Minecraft NativeImage.read
                    try (InputStream in = new FileInputStream(file.toFile())) {
                        rawImage = NativeImage.read(in);
                    }
                }
            }

            if (rawImage == null) {
                clearTexture();
                return;
            }

            NativeImage imageToUpload;
            if (targetBlur > 0.01f) {
                imageToUpload = createBlurredImage(rawImage, targetBlur);
            } else {
                imageToUpload = copyImage(rawImage);
            }

            if (imageToUpload != null) {
                imageW = imageToUpload.getWidth();
                imageH = imageToUpload.getHeight();
                if (texture != null) {
                    try { texture.close(); } catch (Exception ignored) {}
                }
                texture = new NativeImageBackedTexture(() -> "custom_wallpaper", imageToUpload);
                MinecraftClient.getInstance().getTextureManager().registerTexture(WALLPAPER_ID, texture);
                hasTexture = true;
                currentLoadedFile = targetName;
                currentLoadedBlur = targetBlur;
            }
        } catch (Exception e) {
            e.printStackTrace();
            clearTexture();
        }
    }

    private static NativeImage copyImage(NativeImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        NativeImage copy = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                copy.setColorArgb(x, y, src.getColorArgb(x, y));
            }
        }
        return copy;
    }

    private static NativeImage createBlurredImage(NativeImage src, float blur) {
        int origW = src.getWidth();
        int origH = src.getHeight();
        int downscale = 1 + Math.round(blur * 10.0f);
        int targetW = Math.max(8, origW / downscale);
        int targetH = Math.max(8, origH / downscale);

        NativeImage small = new NativeImage(targetW, targetH, false);
        for (int y = 0; y < targetH; y++) {
            int srcY = Math.min(origH - 1, (y * origH) / targetH);
            for (int x = 0; x < targetW; x++) {
                int srcX = Math.min(origW - 1, (x * origW) / targetW);
                small.setColorArgb(x, y, src.getColorArgb(srcX, srcY));
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

    public static void clearTexture() {
        if (texture != null) {
            try { texture.close(); } catch (Exception ignored) {}
            texture = null;
        }
        if (rawImage != null) {
            try { rawImage.close(); } catch (Exception ignored) {}
            rawImage = null;
        }
        hasTexture = false;
        currentLoadedFile = "";
        currentLoadedBlur = -1f;
    }

    public static void render(DrawContext c, int x, int y, int w, int h, float alphaProgress) {
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
