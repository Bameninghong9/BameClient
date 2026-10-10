package com.bame.client.gui;

import com.bame.client.BameClientConfig;
import com.bame.client.notification.Notification;
import com.bame.client.notification.NotificationManager;
import com.bame.client.util.ScreenshotHelper;
import com.bame.client.wallpaper.WallpaperManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public class GalleryScreen extends Screen {
    private final Screen parent;
    private final List<ScreenshotItem> items = new ArrayList<>();
    private final List<ScreenshotItem> filteredItems = new ArrayList<>();

    private static class CachedThumb {
        final Identifier id;
        final int width;
        final int height;

        CachedThumb(Identifier id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }
    }

    private static final Map<String, CachedThumb> textureCache = new HashMap<>();
    private static final Map<String, NativeImageBackedTexture> nativeTextures = new HashMap<>();

    private int px, py, pw, ph;
    private double scroll = 0;
    private double maxScroll = 0;
    private long openTime = System.currentTimeMillis();

    private CustomTextFieldWidget searchField;
    private CustomTextFieldWidget renameField;
    private ScreenshotItem renamingItem = null;
    private ScreenshotItem previewItem = null;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static class ScreenshotItem {
        public final File file;
        public String displayName;
        public final long lastModified;
        public final long sizeBytes;
        public final String formattedDate;
        public final String formattedSize;
        public Identifier textureId = null;
        public int imgW = 0;
        public int imgH = 0;
        public boolean loadAttempted = false;

        public ScreenshotItem(File file) {
            this.file = file;
            this.displayName = file.getName();
            this.lastModified = file.lastModified();
            this.sizeBytes = file.length();
            this.formattedDate = DATE_FORMAT.format(new Date(this.lastModified));
            this.formattedSize = formatBytes(this.sizeBytes);
        }

        private static String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1048576) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
            return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        }
    }

    public GalleryScreen(Screen parent) {
        super(Text.literal("Screenshot Gallery"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        loadScreenshots();

        int searchW = 160;
        int searchH = 22;
        searchField = new CustomTextFieldWidget(0, 0, searchW, searchH, Text.literal(""));
        searchField.setPlaceholder("Search screenshots …");
        searchField.setChangedListener(text -> applyFilter());

        renameField = new CustomTextFieldWidget(0, 0, 240, 24, Text.literal(""));
        renameField.setPlaceholder("Enter new name …");
    }

    public void loadScreenshots() {
        items.clear();
        File runDir = MinecraftClient.getInstance().runDirectory;
        File dir = new File(runDir, "screenshots");
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File f : files) {
                    ScreenshotItem item = new ScreenshotItem(f);
                    CachedThumb cached = textureCache.get(f.getAbsolutePath());
                    if (cached != null) {
                        item.textureId = cached.id;
                        item.imgW = cached.width;
                        item.imgH = cached.height;
                        item.loadAttempted = true;
                    }
                    items.add(item);
                }
            }
        }
        applyFilter();
    }

    private void applyFilter() {
        filteredItems.clear();
        String query = (searchField != null) ? searchField.getText().trim().toLowerCase() : "";
        for (ScreenshotItem item : items) {
            if (query.isEmpty() || item.displayName.toLowerCase().contains(query) || item.formattedDate.contains(query)) {
                filteredItems.add(item);
            }
        }
        scroll = 0;
    }

    private void ensureTexture(ScreenshotItem item) {
        if (item.textureId != null || item.loadAttempted) return;
        item.loadAttempted = true;

        String key = item.file.getAbsolutePath();
        CachedThumb cached = textureCache.get(key);
        if (cached != null) {
            item.textureId = cached.id;
            item.imgW = cached.width;
            item.imgH = cached.height;
            return;
        }

        try (FileInputStream in = new FileInputStream(item.file)) {
            NativeImage img = NativeImage.read(in);
            if (img != null) {
                item.imgW = img.getWidth();
                item.imgH = img.getHeight();
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "caeser_screen_" + Math.abs(key.hashCode()), img);
                Identifier id = Identifier.of("bameclient", "gallery_" + Math.abs(key.hashCode()));
                MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
                textureCache.put(key, new CachedThumb(id, item.imgW, item.imgH));
                nativeTextures.put(key, tex);
                item.textureId = id;
            }
        } catch (Throwable ignored) {}
    }

    private void geometry() {
        pw = Math.min(840, width - 40);
        ph = Math.min(520, height - 40);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        geometry();

        float progress = Math.min(1.0f, (System.currentTimeMillis() - openTime) / 350.0f);
        float p = 1.0f - (float) Math.pow(1.0f - progress, 4);

        // Dim background
        int dimAlpha = (int) (0x65 * p);
        c.fill(0, 0, width, height, (dimAlpha << 24));

        // Window background
        CustomGuiUtils.fillUltraRounded(c, px, py, pw, ph, 0xF511131A, 8.0f);

        // Wallpaper overlay
        if (BameClientConfig.customWallpaper) {
            WallpaperManager.render(c, px, py, pw, ph, p);
        }

        int accent = GuiTheme.accent();
        CustomGuiUtils.drawUltraRoundedOutline(c, px, py, pw, ph, 0xFF242834, 8.0f);

        // Header
        int headerH = 46;
        c.fill(px + 1, py + headerH - 1, px + pw - 1, py + headerH, 0xFF232530);

        CustomGuiUtils.drawCameraIcon(c, px + 16, py + 14, accent);
        int titleY = py + 19;
        c.drawText(textRenderer, CustomGuiUtils.getFontText("GALLERY"), px + 38, titleY, 0xFFFFFFFF, false);
        String countStr = "(" + filteredItems.size() + (filteredItems.size() == 1 ? " Screenshot" : " Screenshots") + ")";
        c.drawText(textRenderer, CustomGuiUtils.getFontText(countStr), px + 38 + textRenderer.getWidth(CustomGuiUtils.getFontText("GALLERY")) + 8, titleY, 0xFF8E95A4, false);

        // Search Field in Header
        int searchX = px + pw - 340;
        int searchY = py + 12;
        if (searchField != null) {
            searchField.setX(searchX);
            searchField.setY(searchY);
            searchField.render(c, mx, my, delta);
        }

        // Action Buttons: [ Open Folder ] & [ Refresh ]
        int btnW = 76;
        int btnH = 22;
        int btnFolderX = px + pw - 170;
        int btnFolderY = py + 12;
        boolean hoverFolder = inside(mx, my, btnFolderX, btnFolderY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btnFolderX, btnFolderY, btnW, btnH, hoverFolder ? 0xFF20232E : 0xFF14161F, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, btnFolderX, btnFolderY, btnW, btnH, hoverFolder ? accent : 0xFF2F3545, 4.0f);
        int twFolder = textRenderer.getWidth(CustomGuiUtils.getFontText("Folder ↗"));
        c.drawText(textRenderer, CustomGuiUtils.getFontText("Folder ↗"), btnFolderX + (btnW - twFolder) / 2, btnFolderY + 6, hoverFolder ? 0xFFFFFFFF : 0xFF9CA3AF, false);

        int btnRefX = px + pw - 86;
        int btnRefY = py + 12;
        int refW = 68;
        boolean hoverRef = inside(mx, my, btnRefX, btnRefY, refW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btnRefX, btnRefY, refW, btnH, hoverRef ? 0xFF20232E : 0xFF14161F, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, btnRefX, btnRefY, refW, btnH, hoverRef ? accent : 0xFF2F3545, 4.0f);
        int twRef = textRenderer.getWidth(CustomGuiUtils.getFontText("Refresh ⟳"));
        c.drawText(textRenderer, CustomGuiUtils.getFontText("Refresh ⟳"), btnRefX + (refW - twRef) / 2, btnRefY + 6, hoverRef ? 0xFFFFFFFF : 0xFF9CA3AF, false);

        // Content Area: Grid of Screenshots
        int contentX = px + 12;
        int contentY = py + headerH + 10;
        int contentW = pw - 24;
        int contentH = ph - headerH - 52;

        c.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        int cols = 3;
        int gap = 12;
        int cardW = (contentW - (cols - 1) * gap) / cols;
        int cardH = 175;

        int totalRows = (int) Math.ceil((double) filteredItems.size() / cols);
        int totalContentHeight = totalRows * (cardH + gap);
        maxScroll = Math.max(0, totalContentHeight - contentH);
        scroll = Math.clamp(scroll, 0, maxScroll);

        if (filteredItems.isEmpty()) {
            String emptyMsg = items.isEmpty() ? "No screenshots yet! Press F2 ingame to capture your adventures." : "No screenshots match your search.";
            int ew = textRenderer.getWidth(CustomGuiUtils.getFontText(emptyMsg));
            c.drawText(textRenderer, CustomGuiUtils.getFontText(emptyMsg), px + (pw - ew) / 2, py + ph / 2 - 10, 0xFF6B7280, false);
        } else {
            for (int i = 0; i < filteredItems.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int cx = contentX + col * (cardW + gap);
                int cy = contentY + row * (cardH + gap) - (int) scroll;

                // Culling
                if (cy + cardH < contentY || cy > contentY + contentH) {
                    continue;
                }

                ScreenshotItem item = filteredItems.get(i);
                ensureTexture(item);

                boolean cardHover = inside(mx, my, cx, cy, cardW, cardH);
                int cardBg = cardHover ? 0xFF1A1D27 : 0xFF14161F;
                int cardBorder = cardHover ? accent : 0xFF262A37;

                CustomGuiUtils.fillUltraRounded(c, cx, cy, cardW, cardH, cardBg, 6.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, cx, cy, cardW, cardH, cardBorder, 6.0f);

                // Thumbnail Container
                int thumbH = 104;
                int thumbY = cy + 6;
                int thumbX = cx + 6;
                int thumbW = cardW - 12;

                c.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, 0xFF0D0E14);
                if (item.textureId != null && item.imgW > 0 && item.imgH > 0) {
                    c.drawTexture(
                        RenderPipelines.GUI_TEXTURED,
                        item.textureId,
                        thumbX, thumbY,
                        0.0f, 0.0f,
                        thumbW, thumbH,
                        item.imgW, item.imgH,
                        item.imgW, item.imgH,
                        0xFFFFFFFF
                    );
                } else {
                    int twLoad = textRenderer.getWidth(CustomGuiUtils.getFontText("Loading …"));
                    c.drawText(textRenderer, CustomGuiUtils.getFontText("Loading …"), thumbX + (thumbW - twLoad) / 2, thumbY + thumbH / 2 - 4, 0xFF4B5563, false);
                }

                // Info Section
                String nameDisplay = item.displayName;
                if (textRenderer.getWidth(CustomGuiUtils.getFontText(nameDisplay)) > cardW - 16) {
                    nameDisplay = textRenderer.trimToWidth(nameDisplay, cardW - 24) + "…";
                }
                c.drawText(textRenderer, CustomGuiUtils.getFontText(nameDisplay), cx + 8, cy + 115, 0xFFE2E8F0, false);

                String meta = item.formattedDate + " · " + item.formattedSize;
                c.drawText(textRenderer, CustomGuiUtils.getFontText(meta), cx + 8, cy + 127, 0xFF717A8C, false);

                // Action Buttons Bar: [ ᴄᴏᴘʏ ] [ ᴏᴘᴇɴ ] [ ʀᴇɴᴀᴍᴇ ] [ ᴅᴇʟ ]
                int btnActY = cy + 144;
                int actH = 22;
                int bGap = 4;
                int bW = (cardW - 12 - 3 * bGap) / 4;

                // [ ᴄᴏᴘʏ ]
                int bCopyX = cx + 6;
                int bCopyW = bW;
                boolean hoverCopy = inside(mx, my, bCopyX, btnActY, bCopyW, actH);
                CustomGuiUtils.fillUltraRounded(c, bCopyX, btnActY, bCopyW, actH, hoverCopy ? 0xFF143820 : 0xFF12151D, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, bCopyX, btnActY, bCopyW, actH, hoverCopy ? 0xFF22C55E : 0xFF2A3040, 4.0f);
                int twCopy = textRenderer.getWidth(CustomGuiUtils.getFontText("ᴄᴏᴘʏ"));
                c.drawText(textRenderer, CustomGuiUtils.getFontText("ᴄᴏᴘʏ"), bCopyX + (bCopyW - twCopy) / 2, btnActY + 6, hoverCopy ? 0xFF22C55E : 0xFF8E95A4, false);

                // [ ᴏᴘᴇɴ ]
                int bOpenX = bCopyX + bCopyW + bGap;
                int bOpenW = bW;
                boolean hoverOpen = inside(mx, my, bOpenX, btnActY, bOpenW, actH);
                CustomGuiUtils.fillUltraRounded(c, bOpenX, btnActY, bOpenW, actH, hoverOpen ? 0xFF352D12 : 0xFF12151D, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, bOpenX, btnActY, bOpenW, actH, hoverOpen ? 0xFFFACC15 : 0xFF2A3040, 4.0f);
                int twOpen = textRenderer.getWidth(CustomGuiUtils.getFontText("ᴏᴘᴇɴ"));
                c.drawText(textRenderer, CustomGuiUtils.getFontText("ᴏᴘᴇɴ"), bOpenX + (bOpenW - twOpen) / 2, btnActY + 6, hoverOpen ? 0xFFFACC15 : 0xFF8E95A4, false);

                // [ ʀᴇɴᴀᴍᴇ ]
                int bRenX = bOpenX + bOpenW + bGap;
                int bRenW = bW;
                boolean hoverRen = inside(mx, my, bRenX, btnActY, bRenW, actH);
                CustomGuiUtils.fillUltraRounded(c, bRenX, btnActY, bRenW, actH, hoverRen ? 0xFF1A2A3E : 0xFF12151D, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, bRenX, btnActY, bRenW, actH, hoverRen ? 0xFF38BDF8 : 0xFF2A3040, 4.0f);
                int twRen = textRenderer.getWidth(CustomGuiUtils.getFontText("ʀᴇɴᴀᴍᴇ"));
                c.drawText(textRenderer, CustomGuiUtils.getFontText("ʀᴇɴᴀᴍᴇ"), bRenX + (bRenW - twRen) / 2, btnActY + 6, hoverRen ? 0xFF38BDF8 : 0xFF8E95A4, false);

                // [ ᴅᴇʟ ]
                int bDelX = bRenX + bRenW + bGap;
                int bDelW = (cx + cardW - 6) - bDelX;
                boolean hoverDel = inside(mx, my, bDelX, btnActY, bDelW, actH);
                CustomGuiUtils.fillUltraRounded(c, bDelX, btnActY, bDelW, actH, hoverDel ? 0xFF3E1A22 : 0xFF12151D, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, bDelX, btnActY, bDelW, actH, hoverDel ? 0xFFEF4444 : 0xFF2A3040, 4.0f);
                int twDel = textRenderer.getWidth(CustomGuiUtils.getFontText("ᴅᴇʟ"));
                c.drawText(textRenderer, CustomGuiUtils.getFontText("ᴅᴇʟ"), bDelX + (bDelW - twDel) / 2, btnActY + 6, hoverDel ? 0xFFEF4444 : 0xFF8E95A4, false);
            }
        }

        c.disableScissor();

        // Footer Bar: [ ← Back ]
        int footerY = py + ph - 38;
        c.fill(px + 1, footerY - 1, px + pw - 1, footerY, 0xFF232530);

        int backW = 96;
        int backH = 26;
        int backX = px + 14;
        int backY = footerY + 6;
        boolean hoverBack = inside(mx, my, backX, backY, backW, backH);
        CustomGuiUtils.fillUltraRounded(c, backX, backY, backW, backH, hoverBack ? 0xFF222634 : 0xFF141620, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, backX, backY, backW, backH, hoverBack ? accent : 0xFF2D3344, 4.0f);
        int twBack = textRenderer.getWidth(CustomGuiUtils.getFontText("← Back"));
        c.drawText(textRenderer, CustomGuiUtils.getFontText("← Back"), backX + (backW - twBack) / 2, backY + 8, hoverBack ? 0xFFFFFFFF : 0xFF9CA3AF, false);

        // Modal: Fullscreen Image Preview
        if (previewItem != null) {
            renderPreviewModal(c, mx, my, delta);
        }

        // Modal: Rename Dialog
        if (renamingItem != null) {
            renderRenameModal(c, mx, my, delta);
        }

        NotificationManager.render(c, delta);
    }

    private void renderPreviewModal(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0xD9000000);

        int maxW = Math.min(width - 80, 840);
        int maxH = Math.min(height - 100, 520);

        ensureTexture(previewItem);
        if (previewItem.textureId != null && previewItem.imgW > 0 && previewItem.imgH > 0) {
            float imgAspect = (float) previewItem.imgW / (float) previewItem.imgH;
            float targetW = maxW;
            float targetH = targetW / imgAspect;
            if (targetH > maxH) {
                targetH = maxH;
                targetW = targetH * imgAspect;
            }

            int imgX = (width - (int) targetW) / 2;
            int imgY = (height - (int) targetH) / 2;

            CustomGuiUtils.fillUltraRounded(c, imgX - 6, imgY - 32, (int) targetW + 12, (int) targetH + 68, 0xF512141D, 8.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, imgX - 6, imgY - 32, (int) targetW + 12, (int) targetH + 68, 0xFF353B4E, 8.0f);

            // Title
            c.drawText(textRenderer, CustomGuiUtils.getFontText(previewItem.displayName), imgX + 6, imgY - 20, 0xFFFFFFFF, false);

            c.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                previewItem.textureId,
                imgX, imgY,
                0.0f, 0.0f,
                (int) targetW, (int) targetH,
                previewItem.imgW, previewItem.imgH,
                previewItem.imgW, previewItem.imgH,
                0xFFFFFFFF
            );

            // Modal Controls
            int ctrlY = imgY + (int) targetH + 8;
            int bW = 86;
            int bH = 24;

            // [ ᴄᴏᴘʏ ]
            boolean hCopy = inside(mx, my, imgX, ctrlY, bW, bH);
            CustomGuiUtils.fillUltraRounded(c, imgX, ctrlY, bW, bH, hCopy ? 0xFF143820 : 0xFF1A1D27, 4.0f);
            int twCopy = textRenderer.getWidth(CustomGuiUtils.getFontText("ᴄᴏᴘʏ"));
            c.drawText(textRenderer, CustomGuiUtils.getFontText("ᴄᴏᴘʏ"), imgX + (bW - twCopy) / 2, ctrlY + 7, hCopy ? 0xFF22C55E : 0xFF9CA3AF, false);

            // [ ᴏᴘᴇɴ ]
            boolean hOpen = inside(mx, my, imgX + bW + 8, ctrlY, bW, bH);
            CustomGuiUtils.fillUltraRounded(c, imgX + bW + 8, ctrlY, bW, bH, hOpen ? 0xFF352D12 : 0xFF1A1D27, 4.0f);
            int twOpen = textRenderer.getWidth(CustomGuiUtils.getFontText("ᴏᴘᴇɴ"));
            c.drawText(textRenderer, CustomGuiUtils.getFontText("ᴏᴘᴇɴ"), imgX + bW + 8 + (bW - twOpen) / 2, ctrlY + 7, hOpen ? 0xFFFACC15 : 0xFF9CA3AF, false);

            // [ Close ]
            int closeX = imgX + (int) targetW - bW;
            boolean hClose = inside(mx, my, closeX, ctrlY, bW, bH);
            CustomGuiUtils.fillUltraRounded(c, closeX, ctrlY, bW, bH, hClose ? 0xFF2E3345 : 0xFF1A1D27, 4.0f);
            int twClose = textRenderer.getWidth(CustomGuiUtils.getFontText("Close"));
            c.drawText(textRenderer, CustomGuiUtils.getFontText("Close"), closeX + (bW - twClose) / 2, ctrlY + 7, hClose ? 0xFFFFFFFF : 0xFF9CA3AF, false);
        }
    }

    private void renderRenameModal(DrawContext c, int mx, int my, float delta) {
        c.fill(0, 0, width, height, 0xB0000000);

        int modalW = 320;
        int modalH = 135;
        int modalX = (width - modalW) / 2;
        int modalY = (height - modalH) / 2;

        CustomGuiUtils.fillUltraRounded(c, modalX, modalY, modalW, modalH, 0xF5141620, 8.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, modalX, modalY, modalW, modalH, GuiTheme.accent(), 8.0f);

        c.drawText(textRenderer, CustomGuiUtils.getFontText("RENAME SCREENSHOT"), modalX + 16, modalY + 14, 0xFFFFFFFF, false);
        c.drawText(textRenderer, CustomGuiUtils.getFontText("Enter a new name:"), modalX + 16, modalY + 30, 0xFF8E95A4, false);

        if (renameField != null) {
            renameField.setX(modalX + 16);
            renameField.setY(modalY + 50);
            renameField.render(c, mx, my, delta);
        }

        // Action Buttons: [ Save ] and [ Cancel ]
        int btnW = 86;
        int btnH = 24;
        int saveX = modalX + modalW - 16 - btnW;
        int cancelX = saveX - btnW - 8;
        int btnY = modalY + modalH - 34;

        boolean hoverSave = inside(mx, my, saveX, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, saveX, btnY, btnW, btnH, hoverSave ? GuiTheme.accent() : 0xFF1E212E, 4.0f);
        int twSave = textRenderer.getWidth(CustomGuiUtils.getFontText("Save"));
        c.drawText(textRenderer, CustomGuiUtils.getFontText("Save"), saveX + (btnW - twSave) / 2, btnY + 7, 0xFFFFFFFF, false);

        boolean hoverCancel = inside(mx, my, cancelX, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, cancelX, btnY, btnW, btnH, hoverCancel ? 0xFF242838 : 0xFF141620, 4.0f);
        int twCancel = textRenderer.getWidth(CustomGuiUtils.getFontText("Cancel"));
        c.drawText(textRenderer, CustomGuiUtils.getFontText("Cancel"), cancelX + (btnW - twCancel) / 2, btnY + 7, hoverCancel ? 0xFFFFFFFF : 0xFF8E95A4, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean twice) {
        int mx = (int) click.x();
        int my = (int) click.y();

        if (previewItem != null) {
            int maxW = Math.min(width - 80, 840);
            int maxH = Math.min(height - 100, 520);
            if (previewItem.imgW > 0 && previewItem.imgH > 0) {
                float imgAspect = (float) previewItem.imgW / (float) previewItem.imgH;
                float targetW = maxW;
                float targetH = targetW / imgAspect;
                if (targetH > maxH) {
                    targetH = maxH;
                    targetW = targetH * imgAspect;
                }
                int imgX = (width - (int) targetW) / 2;
                int imgY = (height - (int) targetH) / 2;
                int ctrlY = imgY + (int) targetH + 8;
                int bW = 86;
                int bH = 24;

                if (inside(mx, my, imgX, ctrlY, bW, bH)) {
                    ScreenshotHelper.copyToClipboard(previewItem.file.getAbsolutePath());
                    NotificationManager.add(new Notification("Gallery", "✓ Copied to clipboard!", Notification.Type.ENABLED));
                    return true;
                }
                if (inside(mx, my, imgX + bW + 8, ctrlY, bW, bH)) {
                    ScreenshotHelper.openScreenshot(previewItem.file.getAbsolutePath());
                    return true;
                }
                int closeX = imgX + (int) targetW - bW;
                if (inside(mx, my, closeX, ctrlY, bW, bH) || !inside(mx, my, imgX - 6, imgY - 32, (int) targetW + 12, (int) targetH + 68)) {
                    previewItem = null;
                    return true;
                }
            } else {
                previewItem = null;
            }
            return true;
        }

        if (renamingItem != null) {
            int modalW = 320; int modalH = 135;
            int modalX = (width - modalW) / 2; int modalY = (height - modalH) / 2;
            int btnW = 86; int btnH = 24;
            int saveX = modalX + modalW - 16 - btnW;
            int cancelX = saveX - btnW - 8;
            int btnY = modalY + modalH - 34;

            if (renameField != null && renameField.mouseClicked(click, twice)) {
                return true;
            }
            if (inside(mx, my, saveX, btnY, btnW, btnH)) {
                saveRename();
                return true;
            }
            if (inside(mx, my, cancelX, btnY, btnW, btnH) || !inside(mx, my, modalX, modalY, modalW, modalH)) {
                renamingItem = null;
                return true;
            }
            return true;
        }

        if (searchField != null && searchField.mouseClicked(click, twice)) {
            return true;
        }

        // Header Action: Open Folder
        int btnFolderX = px + pw - 170;
        int btnFolderY = py + 12;
        if (inside(mx, my, btnFolderX, btnFolderY, 76, 22)) {
            File runDir = MinecraftClient.getInstance().runDirectory;
            File dir = new File(runDir, "screenshots");
            if (!dir.exists()) dir.mkdirs();
            ScreenshotHelper.openScreenshot(dir.getAbsolutePath());
            return true;
        }

        // Header Action: Refresh
        int btnRefX = px + pw - 86;
        int btnRefY = py + 12;
        if (inside(mx, my, btnRefX, btnRefY, 68, 22)) {
            loadScreenshots();
            NotificationManager.add(new Notification("Gallery", "Screenshots refreshed", Notification.Type.INFO));
            return true;
        }

        // Footer Action: Back
        int backX = px + 14;
        int backY = py + ph - 32;
        if (inside(mx, my, backX, backY, 96, 26)) {
            close();
            return true;
        }

        // Grid Cards
        int contentX = px + 12;
        int contentY = py + 46 + 10;
        int contentW = pw - 24;
        int contentH = ph - 46 - 52;
        int cols = 3; int gap = 12;
        int cardW = (contentW - (cols - 1) * gap) / cols;
        int cardH = 175;

        if (inside(mx, my, contentX, contentY, contentW, contentH)) {
            for (int i = 0; i < filteredItems.size(); i++) {
                int col = i % cols;
                int row = i / cols;
                int cx = contentX + col * (cardW + gap);
                int cy = contentY + row * (cardH + gap) - (int) scroll;

                if (cy + cardH < contentY || cy > contentY + contentH) continue;

                ScreenshotItem item = filteredItems.get(i);
                int btnActY = cy + 144;
                int actH = 22;
                int bGap = 4;
                int bW = (cardW - 12 - 3 * bGap) / 4;

                // Click Thumbnail -> Open Preview
                if (inside(mx, my, cx + 6, cy + 6, cardW - 12, 104)) {
                    previewItem = item;
                    return true;
                }

                // [ ᴄᴏᴘʏ ]
                int bCopyX = cx + 6;
                int bCopyW = bW;
                if (inside(mx, my, bCopyX, btnActY, bCopyW, actH)) {
                    ScreenshotHelper.copyToClipboard(item.file.getAbsolutePath());
                    NotificationManager.add(new Notification("Gallery", "✓ Copied to clipboard!", Notification.Type.ENABLED));
                    return true;
                }

                // [ ᴏᴘᴇɴ ]
                int bOpenX = bCopyX + bCopyW + bGap;
                int bOpenW = bW;
                if (inside(mx, my, bOpenX, btnActY, bOpenW, actH)) {
                    ScreenshotHelper.openScreenshot(item.file.getAbsolutePath());
                    return true;
                }

                // [ ʀᴇɴᴀᴍᴇ ]
                int bRenX = bOpenX + bOpenW + bGap;
                int bRenW = bW;
                if (inside(mx, my, bRenX, btnActY, bRenW, actH)) {
                    renamingItem = item;
                    String base = item.displayName;
                    if (base.toLowerCase().endsWith(".png")) {
                        base = base.substring(0, base.length() - 4);
                    }
                    if (renameField != null) {
                        renameField.setText(base);
                        renameField.setFocused(true);
                    }
                    return true;
                }

                // [ ᴅᴇʟ ]
                int bDelX = bRenX + bRenW + bGap;
                int bDelW = (cx + cardW - 6) - bDelX;
                if (inside(mx, my, bDelX, btnActY, bDelW, actH)) {
                    ScreenshotHelper.deleteScreenshot(item.file.getAbsolutePath());
                    textureCache.remove(item.file.getAbsolutePath());
                    NativeImageBackedTexture tex = nativeTextures.remove(item.file.getAbsolutePath());
                    if (tex != null) {
                        try { tex.close(); } catch (Throwable ignored) {}
                    }
                    items.remove(item);
                    filteredItems.remove(item);
                    NotificationManager.add(new Notification("Gallery", "Screenshot deleted", Notification.Type.DISABLED));
                    return true;
                }
            }
        }

        return super.mouseClicked(click, twice);
    }

    private void saveRename() {
        if (renamingItem == null || renameField == null) return;
        String newName = renameField.getText().trim();
        if (newName.isEmpty()) {
            renamingItem = null;
            return;
        }

        if (!newName.toLowerCase().endsWith(".png")) {
            newName += ".png";
        }

        File target = new File(renamingItem.file.getParentFile(), newName);
        if (target.exists() && !target.equals(renamingItem.file)) {
            NotificationManager.add(new Notification("Gallery", "A file with this name already exists!", Notification.Type.DISABLED));
            return;
        }

        String oldPath = renamingItem.file.getAbsolutePath();
        boolean ok = renamingItem.file.renameTo(target);
        if (ok) {
            CachedThumb cached = textureCache.remove(oldPath);
            NativeImageBackedTexture tex = nativeTextures.remove(oldPath);
            if (cached != null) {
                textureCache.put(target.getAbsolutePath(), cached);
            }
            if (tex != null) {
                nativeTextures.put(target.getAbsolutePath(), tex);
            }
            NotificationManager.add(new Notification("Gallery", "Renamed to " + newName, Notification.Type.ENABLED));
            renamingItem = null;
            loadScreenshots();
        } else {
            NotificationManager.add(new Notification("Gallery", "Failed to rename screenshot", Notification.Type.DISABLED));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (previewItem == null && renamingItem == null) {
            scroll = Math.clamp(scroll - vertical * 26, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (previewItem != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                previewItem = null;
                return true;
            }
        }

        if (renamingItem != null) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                renamingItem = null;
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
                saveRename();
                return true;
            }
            if (renameField != null && renameField.keyPressed(input)) {
                return true;
            }
        }

        if (searchField != null && searchField.isFocused()) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                searchField.setFocused(false);
                return true;
            }
            if (searchField.keyPressed(input)) {
                return true;
            }
        }

        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (renamingItem != null && renameField != null && renameField.isFocused()) {
            return renameField.charTyped(input);
        }
        if (searchField != null && searchField.isFocused()) {
            return searchField.charTyped(input);
        }
        return super.charTyped(input);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
