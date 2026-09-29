package com.bame.client.render;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.module.SpotifyHudModule;
import com.bame.client.spotify.SpotifyService;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;

public class SpotifyHudRenderer {
    public static final int WIDTH = 214;
    public static final int HEIGHT = 56;

    public static int getWidth() {
        return SpotifyHudModule.customWidth > 0 ? Math.max(160, SpotifyHudModule.customWidth) : WIDTH;
    }

    public static int getHeight() {
        return SpotifyHudModule.customHeight > 0 ? Math.max(48, SpotifyHudModule.customHeight) : HEIGHT;
    }

    public static String formatTime(int totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m, s);
    }

    public static void render(DrawContext c, int x, int y, float scale, boolean isPreview) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Make sure background service is running when enabled
        if (SpotifyHudModule.enabled) {
            SpotifyService.start();
        }

        SpotifyService.checkTextureUpdate();

        boolean hasMedia = SpotifyService.hasMedia;
        boolean isPlaying = SpotifyService.isPlaying;

        // Auto-hide when not previewing and not playing
        if (!isPreview && SpotifyHudModule.autoHide && !isPlaying) {
            return;
        }

        int w = getWidth();
        int h = getHeight();

        c.getMatrices().pushMatrix();
        c.getMatrices().translate((float) x, (float) y);
        c.getMatrices().scale(scale, scale);

        boolean shadow = (SpotifyHudModule.bgMode == 0 || SpotifyHudModule.bgMode == 3);

        // 1. Background Box
        StatusHudRenderer.drawBoxBg(c, 0, 0, w, h, SpotifyHudModule.bgMode, SpotifyHudModule.outlineColor);

        // 2. Album Cover
        int coverX = 6;
        int coverY = 6;
        int coverSize = Math.max(24, Math.min(32, h - 18));

        if (SpotifyService.hasCoverTexture) {
            c.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                SpotifyService.COVER_ID,
                coverX, coverY,
                0.0f, 0.0f,
                coverSize, coverSize,
                SpotifyService.coverWidth, SpotifyService.coverHeight,
                SpotifyService.coverWidth, SpotifyService.coverHeight,
                0xFFFFFFFF
            );
            CustomGuiUtils.drawUltraRoundedOutline(c, coverX, coverY, coverSize, coverSize, 0x33FFFFFF, 3);
        } else {
            CustomGuiUtils.fillUltraRounded(c, coverX, coverY, coverSize, coverSize, 0x44252A34, 3);
            CustomGuiUtils.drawUltraRoundedOutline(c, coverX, coverY, coverSize, coverSize, 0x338E95A4, 3);
            int tw = client.textRenderer.getWidth("♫");
            c.drawText(client.textRenderer, "♫", coverX + (coverSize - tw) / 2, coverY + (coverSize - 8) / 2, 0xFF8E95A4, shadow);
        }

        // 3. Text Section
        int textX = coverX + coverSize + 8;
        int maxTextW = Math.max(20, w - textX - 6);

        String title = (isPreview && !hasMedia) ? "I Am the Highway" : SpotifyService.title;
        if (title.isEmpty()) title = "Nothing Playing";
        String artist = (isPreview && !hasMedia) ? "Audioslave" : SpotifyService.artist;
        String album = (isPreview && !hasMedia) ? "Audioslave" : SpotifyService.album;

        String trimmedTitle = client.textRenderer.trimToWidth(title, maxTextW);
        c.drawText(client.textRenderer, CustomGuiUtils.getFontText(trimmedTitle), textX, 5, 0xFFFFFFFF, shadow);

        if (!artist.isEmpty()) {
            String trimmedArtist = client.textRenderer.trimToWidth(artist, maxTextW);
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(trimmedArtist), textX, 15, 0xFFB0B8C5, shadow);
        }

        if (!album.isEmpty() && h >= 54) {
            String trimmedAlbum = client.textRenderer.trimToWidth(album, maxTextW);
            c.drawText(client.textRenderer, CustomGuiUtils.getFontText(trimmedAlbum), textX, 25, 0xFF7E8494, shadow);
        }

        // 4. Progress Bar & Times (Full width matching mockup)
        float pos = (isPreview && !hasMedia) ? 21.0f : SpotifyService.getInterpolatedPosition();
        float dur = (isPreview && !hasMedia) ? 334.9f : SpotifyService.duration;

        int barX = 6;
        int barW = Math.max(30, w - 12);
        int barY = h - 15;

        // Background track line
        CustomGuiUtils.fillUltraRounded(c, barX, barY, barW, 2, 0x44FFFFFF, 1);

        // Filled progress line
        float prog = dur > 0 ? Math.clamp(pos / dur, 0f, 1f) : 0f;
        int fillW = (int) (barW * prog);
        if (fillW > 0) {
            CustomGuiUtils.fillUltraRounded(c, barX, barY, fillW, 2, 0xFF5B6CFF, 1);
        }

        // Thumb knob (white circle)
        int thumbX = barX + fillW;
        CustomGuiUtils.fillUltraRounded(c, thumbX - 3, barY - 2, 6, 6, 0xFFFFFFFF, 3);

        // Times row directly underneath the slider
        int timeY = barY + 4;
        String leftTime = formatTime((int) pos);
        String rightTime = formatTime((int) dur);

        c.drawText(client.textRenderer, CustomGuiUtils.getFontText(leftTime), barX, timeY, 0xFF8E95A4, shadow);

        int rightTimeW = client.textRenderer.getWidth(CustomGuiUtils.getFontText(rightTime));
        c.drawText(client.textRenderer, CustomGuiUtils.getFontText(rightTime), barX + barW - rightTimeW, timeY, 0xFF8E95A4, shadow);

        c.getMatrices().popMatrix();
    }
}
