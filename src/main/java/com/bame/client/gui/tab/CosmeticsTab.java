package com.bame.client.gui.tab;

import com.bame.client.cosmetics.CaeserCosmeticsRenderer;
import com.bame.client.cosmetics.CosmeticsConfig;
import com.bame.client.cosmetics.PlayerSkinResolver;
import com.bame.client.gui.BameClientScreen;
import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.CustomTextFieldWidget;
import com.bame.client.gui.GuiTheme;
import com.bame.client.module.SkinProtectModule;
import com.bame.client.sound.ClientSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CosmeticsTab implements ClientTab {

    public static String subCategory = "ALL"; // "ALL", "WINGS", "HEAD", "PET"
    private static final String[] SUB_CATS = {"ALL", "WINGS", "HEAD", "PET"};

    // Preview state (what is shown on the 3D player right now)
    public static String previewWings = null;
    public static String previewHead = null;
    public static String previewPet = null;

    public static void resetPreview() {
        previewWings = null;
        previewHead = null;
        previewPet = null;
    }

    // Card standalone 3D models (each has visible=false on all body parts)
    public static final Map<PlayerEntityModel, String> CARD_MODEL_MAP = new IdentityHashMap<>();
    private static final Map<String, PlayerEntityModel> ITEM_CARD_MODELS = new HashMap<>();

    // 3D Player Preview models (wide & slim)
    private static PlayerEntityModel previewWideModel = null;
    private static PlayerEntityModel previewSlimModel = null;

    // 3D Player Preview Viewport State (Smooth Dragging & Zoom)
    private static float previewPitch = -5.0f;
    private static float previewYaw = 25.0f;
    private static float previewZoom = 0.72f;
    private static final float DEFAULT_ZOOM = 0.72f;
    private static final float DEFAULT_PITCH = -5.0f;
    private static final float DEFAULT_YAW = 25.0f;

    private boolean previewDragging = false;
    private int lastPreviewX, lastPreviewY, lastPreviewW, lastPreviewH;

    // Pet custom player field
    private final CustomTextFieldWidget playerPetField;

    // Cosmetic Item Definition
    public static class CosmeticItem {
        public final String id;
        public final String category; // "WINGS", "HEAD", "PET"
        public final String name;
        public final String badge;
        public final int badgeColor;
        public final String desc;

        public CosmeticItem(String id, String category, String name, String badge, int badgeColor, String desc) {
            this.id = id;
            this.category = category;
            this.name = name;
            this.badge = badge;
            this.badgeColor = badgeColor;
            this.desc = desc;
        }

        public boolean isEquipped() {
            return switch (category) {
                case "WINGS" -> id.equalsIgnoreCase(CosmeticsConfig.wings);
                case "HEAD" -> id.equalsIgnoreCase(CosmeticsConfig.head);
                case "PET" -> id.equalsIgnoreCase(CosmeticsConfig.pet);
                default -> false;
            };
        }

        public boolean isSelected() {
            return switch (category) {
                case "WINGS" -> id.equalsIgnoreCase(previewWings != null ? previewWings : CosmeticsConfig.wings);
                case "HEAD" -> id.equalsIgnoreCase(previewHead != null ? previewHead : CosmeticsConfig.head);
                case "PET" -> id.equalsIgnoreCase(previewPet != null ? previewPet : CosmeticsConfig.pet);
                default -> false;
            };
        }

        public void selectForPreview() {
            switch (category) {
                case "WINGS" -> previewWings = id;
                case "HEAD" -> previewHead = id;
                case "PET" -> previewPet = id;
            }
        }

        public void toggleEquipped(String customPlayerInput) {
            switch (category) {
                case "WINGS" -> {
                    if (isEquipped()) {
                        CosmeticsConfig.setWings("none");
                        previewWings = "none";
                    } else {
                        CosmeticsConfig.setWings(id);
                        previewWings = id;
                    }
                }
                case "HEAD" -> {
                    if (isEquipped()) {
                        CosmeticsConfig.setHead("none");
                        previewHead = "none";
                    } else {
                        CosmeticsConfig.setHead(id);
                        previewHead = id;
                    }
                }
                case "PET" -> {
                    if (isEquipped()) {
                        CosmeticsConfig.setPet("none", CosmeticsConfig.petCustomPlayer);
                        previewPet = "none";
                    } else {
                        String name = "custom".equalsIgnoreCase(id) ? customPlayerInput : "";
                        CosmeticsConfig.setPet(id, name);
                        if ("custom".equalsIgnoreCase(id) && !name.isEmpty()) {
                            PlayerSkinResolver.resolve(name);
                        }
                        previewPet = id;
                    }
                }
            }
        }
    }

    private static final List<CosmeticItem> ITEMS = List.of(
            new CosmeticItem("angel", "WINGS", "Gold Wings", "LEGENDARY", 0xFFF59E0B, "Goldene 3D-Flügel mit Shimmer-Animation"),
            new CosmeticItem("dragon", "WINGS", "Dragon Wings", "MYTHIC", 0xFFA855F7, "Enderdrachen-Flügel mit Flügelschlag"),
            new CosmeticItem("halo", "HEAD", "Angel Halo", "HOLY", 0xFFFACC15, "Schwebender leuchtender Heiligenschein"),
            new CosmeticItem("horns", "HEAD", "Demon Horns", "DARK", 0xFFEF4444, "Symmetrische 3D Dämonenhörner"),
            new CosmeticItem("self", "PET", "Mini-Me", "CHIBI", 0xFF38BDF8, "Dein eigener Skin auf deiner Schulter"),
            new CosmeticItem("custom", "PET", "Spieler-Skin", "CUSTOM", 0xFF10B981, "Skin eines beliebigen Spielernamens")
    );

    public CosmeticsTab() {
        this.playerPetField = new CustomTextFieldWidget(0, 0, 130, 20, Text.literal("Player Name"));
        this.playerPetField.setPlaceholder("Spielername...");
        this.playerPetField.setDrawsBackground(true);
        if (CosmeticsConfig.petCustomPlayer != null && !CosmeticsConfig.petCustomPlayer.isEmpty()) {
            this.playerPetField.setText(CosmeticsConfig.petCustomPlayer);
        }
    }

    private static synchronized PlayerEntityModel getCardModel(MinecraftClient mc, String cosmeticId) {
        if (mc.getLoadedEntityModels() == null) return null;
        PlayerEntityModel model = ITEM_CARD_MODELS.get(cosmeticId);
        if (model == null) {
            model = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
            // Hide all skin parts so only the cosmetic renders
            model.head.visible = false;
            model.hat.visible = false;
            model.body.visible = false;
            model.rightArm.visible = false;
            model.leftArm.visible = false;
            model.rightLeg.visible = false;
            model.leftLeg.visible = false;
            ITEM_CARD_MODELS.put(cosmeticId, model);
            CARD_MODEL_MAP.put(model, cosmeticId);
        }
        return model;
    }

    private static synchronized PlayerEntityModel getPreviewModel(MinecraftClient mc, boolean slim) {
        if (mc.getLoadedEntityModels() == null) return null;
        if (slim) {
            if (previewSlimModel == null) {
                net.minecraft.client.render.entity.model.EntityModelLayer slimLayer =
                        new net.minecraft.client.render.entity.model.EntityModelLayer(Identifier.ofVanilla("player_slim"), "main");
                previewSlimModel = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(slimLayer), true);
            }
            return previewSlimModel;
        } else {
            if (previewWideModel == null) {
                previewWideModel = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
            }
            return previewWideModel;
        }
    }

    @Override
    public String getCategory() {
        return "Cosmetics";
    }

    @Override
    public boolean isVisible(String search) {
        if (search == null || search.isEmpty()) return true;
        String q = search.toLowerCase(Locale.ROOT);
        return "cosmetics".contains(q) || "flügel".contains(q) || "wings".contains(q)
                || "kopf".contains(q) || "head".contains(q) || "halo".contains(q)
                || "horns".contains(q) || "pet".contains(q) || "begleiter".contains(q);
    }

    private List<CosmeticItem> getFilteredItems(String search) {
        List<CosmeticItem> list = new ArrayList<>();
        String q = (search != null) ? search.trim().toLowerCase(Locale.ROOT) : "";
        for (CosmeticItem item : ITEMS) {
            if (!"ALL".equalsIgnoreCase(subCategory) && !item.category.equalsIgnoreCase(subCategory)) {
                continue;
            }
            if (!q.isEmpty()) {
                boolean match = item.name.toLowerCase(Locale.ROOT).contains(q)
                        || item.category.toLowerCase(Locale.ROOT).contains(q)
                        || item.desc.toLowerCase(Locale.ROOT).contains(q);
                if (!match) continue;
            }
            list.add(item);
        }
        return list;
    }

    private int cardHeight(CosmeticItem item) {
        return "custom".equalsIgnoreCase(item.id) ? 148 : 122;
    }

    @Override
    public void layout(BameClientScreen screen, int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (!isVisible(search)) {
            playerPetField.visible = false;
            playerPetField.active = false;
            return;
        }

        int totalW = columnWidth * 2 + 16;
        int startY = Math.max(tracker.leftY, tracker.rightY);
        int cardStartY = startY + 36;

        boolean showPreview = totalW >= 380;
        int previewW = showPreview ? Math.clamp((int) (totalW * 0.35f), 150, 195) : 0;
        int cardsAreaW = showPreview ? (totalW - previewW - 12) : totalW;
        int colW = (cardsAreaW - 10) / 2;

        List<CosmeticItem> list = getFilteredItems(search);
        int curLeftY = cardStartY;
        int curRightY = cardStartY;

        for (int i = 0; i < list.size(); i++) {
            CosmeticItem item = list.get(i);
            boolean isLeft = (i % 2 == 0);
            int cardX = isLeft ? leftX : leftX + colW + 10;
            int cardY = isLeft ? curLeftY : curRightY;
            int h = cardHeight(item);

            if ("custom".equalsIgnoreCase(item.id)) {
                playerPetField.setX(cardX + 8);
                playerPetField.setY(cardY + 116);
                playerPetField.setWidth(colW - 16);
                playerPetField.setHeight(22);
                playerPetField.visible = true;
                playerPetField.active = true;
            }

            if (isLeft) curLeftY += h + 10;
            else curRightY += h + 10;
        }

        tracker.leftY = curLeftY;
        tracker.rightY = curRightY;
    }

    @Override
    public void calculateHeight(BameClientScreen screen, int columnWidth, ColumnTracker tracker, String search) {
        if (!isVisible(search)) return;
        int startY = Math.max(tracker.leftY, tracker.rightY);
        int cardStartY = startY + 36;
        List<CosmeticItem> list = getFilteredItems(search);
        int curLeftY = cardStartY;
        int curRightY = cardStartY;
        for (int i = 0; i < list.size(); i++) {
            CosmeticItem item = list.get(i);
            int h = cardHeight(item);
            if (i % 2 == 0) curLeftY += h + 10;
            else curRightY += h + 10;
        }
        tracker.leftY = curLeftY;
        tracker.rightY = curRightY;
    }

    @Override
    public void render(BameClientScreen screen, DrawContext c, int mx, int my, float delta,
                       int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (!isVisible(search)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        int totalW = columnWidth * 2 + 16;
        int startY = Math.max(tracker.leftY, tracker.rightY);

        // 1. TOP SUB-CATEGORIES BAR (ALL | WINGS | HEAD | PET)
        int catBarY = startY;
        int catTabW = (totalW - 130) / SUB_CATS.length;
        for (int i = 0; i < SUB_CATS.length; i++) {
            String cat = SUB_CATS[i];
            int tx = leftX + i * catTabW;
            boolean active = cat.equalsIgnoreCase(subCategory);
            boolean hover = screen.inside(mx, my, tx, catBarY, catTabW - 4, 24);

            int bg = active ? GuiTheme.alpha(GuiTheme.accent(), 85) : (hover ? 0xFF242A38 : 0xFF161A24);
            int border = active ? GuiTheme.accent() : (hover ? 0xFF4A556B : 0xFF252D3D);
            CustomGuiUtils.fillUltraRounded(c, tx, catBarY, catTabW - 4, 24, bg, 5.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, tx, catBarY, catTabW - 4, 24, border, 5.0f);

            String label = switch (cat) {
                case "ALL" -> "Alle";
                case "WINGS" -> "🪽 Wings";
                case "HEAD" -> "👑 Head";
                case "PET" -> "🐾 Pet";
                default -> cat;
            };
            int tw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(label));
            int tc = active ? 0xFFFFFFFF : 0xFFB6BDCC;
            screen.text(c, label, tx + (catTabW - 4 - tw) / 2, catBarY + 8, tc);
        }

        // Top-Right: Quick Unequip All Button
        int resetW = 120;
        int resetX = leftX + totalW - resetW;
        boolean hoverReset = screen.inside(mx, my, resetX, catBarY, resetW, 24);
        CustomGuiUtils.fillUltraRounded(c, resetX, catBarY, resetW, 24, hoverReset ? 0xFF352028 : 0xFF1C151B, 5.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, resetX, catBarY, resetW, 24, hoverReset ? 0xFFF43F5E : 0xFF3A242B, 5.0f);
        screen.text(c, "✕ Alle ablegen", resetX + 16, catBarY + 8, hoverReset ? 0xFFFCA5A5 : 0xFFE2E8F0);

        // 2. COSMETICS ITEM GRID + RIGHT 3D PLAYER PREVIEW
        int cardStartY = startY + 36;
        boolean showPreview = totalW >= 380;
        int previewW = showPreview ? Math.clamp((int) (totalW * 0.35f), 150, 195) : 0;
        int cardsAreaW = showPreview ? (totalW - previewW - 12) : totalW;
        int colW = (cardsAreaW - 10) / 2;

        List<CosmeticItem> list = getFilteredItems(search);
        int curLeftY = cardStartY;
        int curRightY = cardStartY;

        for (int i = 0; i < list.size(); i++) {
            CosmeticItem item = list.get(i);
            boolean isLeft = (i % 2 == 0);
            int cardX = isLeft ? leftX : leftX + colW + 10;
            int cardY = isLeft ? curLeftY : curRightY;
            int h = cardHeight(item);

            renderCosmeticCard(screen, c, mc, item, cardX, cardY, colW, h, mx, my, delta);

            if (isLeft) curLeftY += h + 10;
            else curRightY += h + 10;
        }

        // 3. RIGHT 3D PLAYER PREVIEW PANEL (Smooth, lag-free dragging + zoomable!)
        if (showPreview) {
            int previewX = leftX + totalW - previewW;
            int previewY = cardStartY;
            int totalGridH = Math.max(curLeftY, curRightY) - cardStartY;
            int previewH = Math.max(280, Math.min(380, Math.max(totalGridH - 10, 300)));

            renderPreviewPanel(screen, c, mc, previewX, previewY, previewW, previewH, mx, my, delta);
            tracker.rightY = Math.max(curRightY, previewY + previewH + 10);
        } else {
            tracker.rightY = curRightY;
        }

        tracker.leftY = curLeftY;
    }

    private void renderCosmeticCard(BameClientScreen screen, DrawContext c, MinecraftClient mc,
                                    CosmeticItem item, int x, int y, int w, int h, int mx, int my, float delta) {
        boolean equipped = item.isEquipped();
        boolean selected = item.isSelected();
        boolean hover = screen.inside(mx, my, x, y, w, h);

        // Card Base (Clean modern NoRisk style)
        int bg = equipped ? 0xFF141926 : (selected ? 0xFF161928 : (hover ? 0xFF141622 : 0xFF0F1118));
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, bg, 7.0f);

        int border = equipped ? GuiTheme.accent() : (selected ? 0xFF94A3B8 : (hover ? 0xFF3E485C : 0xFF1F2432));
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, border, 7.0f);

        if (equipped) {
            // Neon accent top glow line
            c.fill(x + 8, y + 1, x + w - 8, y + 2, GuiTheme.accent());
        }

        // Header: Item Name
        int titleColor = equipped ? 0xFFFFFFFF : (hover ? 0xFFE2E8F0 : 0xFFCBD5E1);
        screen.text(c, item.name, x + 8, y + 7, titleColor);

        // Header Right: Rarity Badge / Equipped Checkmark
        if (equipped) {
            String eqText = "✓ AKTIV";
            int eqW = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(eqText)) + 8;
            int eqX = x + w - eqW - 8;
            CustomGuiUtils.fillUltraRounded(c, eqX, y + 5, eqW, 13, 0x4010B981, 3.5f);
            CustomGuiUtils.drawUltraRoundedOutline(c, eqX, y + 5, eqW, 13, 0xFF10B981, 3.5f);
            screen.text(c, eqText, eqX + 4, y + 7, 0xFF34D399);
        } else {
            int badgeW = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(item.badge)) + 8;
            int badgeX = x + w - badgeW - 8;
            CustomGuiUtils.fillUltraRounded(c, badgeX, y + 5, badgeW, 13, 0x25000000 | (item.badgeColor & 0x00FFFFFF), 3.5f);
            CustomGuiUtils.drawUltraRoundedOutline(c, badgeX, y + 5, badgeW, 13, item.badgeColor, 3.5f);
            screen.text(c, item.badge, badgeX + 4, y + 7, item.badgeColor);
        }

        // PREVIEW VIEWPORT (Large 3D Bedrock Model rendering live!)
        int prevX = x + 7;
        int prevY = y + 22;
        int prevW = w - 14;
        int prevH = 88;
        CustomGuiUtils.fillUltraRounded(c, prevX, prevY, prevW, prevH, 0xFF0A0C13, 5.0f);
        int prevBorder = equipped ? GuiTheme.alpha(GuiTheme.accent(), 140) : (hover ? 0xFF2A3144 : 0xFF161A26);
        CustomGuiUtils.drawUltraRoundedOutline(c, prevX, prevY, prevW, prevH, prevBorder, 5.0f);


        // Draw live 3D Bedrock Model inside the card preview box!
        PlayerEntityModel cardModel = getCardModel(mc, item.id);
        if (cardModel != null) {
            float cardScale = 0.97f * prevH / 2.125f;
            float rotY = (float) (Math.sin((System.currentTimeMillis() % 10000L) / 1000.0f * 1.5) * 6.5f);
            c.addPlayerSkin(cardModel, DefaultSkinHelper.getTexture(),
                    cardScale, -4.0f, rotY, -1.0625f,
                    prevX + 2, prevY + 2, prevX + prevW - 2, prevY + prevH - 2);
        }

        // Optional custom player name input field for Spieler-Skin Pet
        if ("custom".equalsIgnoreCase(item.id)) {
            playerPetField.render(c, mx, my, delta);
        }
    }

    private void renderPreviewPanel(BameClientScreen screen, DrawContext c, MinecraftClient mc,
                                    int px, int py, int pw, int ph, int mx, int my, float delta) {
        this.lastPreviewX = px;
        this.lastPreviewY = py;
        this.lastPreviewW = pw;
        this.lastPreviewH = ph;

        // Container
        CustomGuiUtils.fillUltraRounded(c, px, py, pw, ph, 0xD811141E, 8.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, px, py, pw, ph, GuiTheme.alpha(GuiTheme.accent(), 130), 8.0f);
        c.fill(px + 12, py + 1, px + pw - 12, py + 2, GuiTheme.accent());

        // Header Title
        String headerText = "3D VORSCHAU";
        int htw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(headerText));
        screen.text(c, headerText, px + (pw - htw) / 2, py + 7, GuiTheme.accent());

        // Active items chip badges
        int badgeY = py + 20;
        String curW = previewWings != null ? previewWings : CosmeticsConfig.wings;
        String curH = previewHead != null ? previewHead : CosmeticsConfig.head;
        String curP = previewPet != null ? previewPet : CosmeticsConfig.pet;

        if (!"none".equalsIgnoreCase(curW)) {
            String name = "angel".equalsIgnoreCase(curW) ? "🪽 GOLD WINGS" : "🪽 DRAGON WINGS";
            int nw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(name));
            screen.text(c, name, px + (pw - nw) / 2, badgeY, 0xFFFDE047);
            badgeY += 10;
        }
        if (!"none".equalsIgnoreCase(curH)) {
            String name = "halo".equalsIgnoreCase(curH) ? "👑 ANGEL HALO" : "😈 DEMON HORNS";
            int nw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(name));
            screen.text(c, name, px + (pw - nw) / 2, badgeY, 0xFFFCA5A5);
            badgeY += 10;
        }
        if (!"none".equalsIgnoreCase(curP)) {
            String name = "self".equalsIgnoreCase(curP) ? "🐾 MINI-ME" : "🐾 PET";
            int nw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(name));
            screen.text(c, name, px + (pw - nw) / 2, badgeY, 0xFFBAE6FD);
            badgeY += 10;
        }

        // Viewport bounds
        int skinW = pw - 16;
        int skinH = ph - 68;
        int skinX = px + 8;
        int skinY = py + 32;

        // Pedestal under feet
        int pedW = Math.min(100, (int) (skinW * 0.70f));
        int pedX = px + (pw - pedW) / 2;
        int pedY = py + ph - 38;
        CustomGuiUtils.fillUltraRounded(c, pedX - 6, pedY + 2, pedW + 12, 7, 0x40000000, 3);
        CustomGuiUtils.fillUltraRounded(c, pedX, pedY, pedW, 4, 0x35000000 | (GuiTheme.accent() & 0x00FFFFFF), 3);
        CustomGuiUtils.drawUltraRoundedOutline(c, pedX, pedY, pedW, 4, GuiTheme.alpha(GuiTheme.accent(), 85), 3);

        // 3D Player Live Direct Render (with Headroom for Halo & Horns + custom Zoom!)
        SkinTextures textures = null;
        if (SkinProtectModule.enabled) {
            textures = SkinProtectModule.getCurrentSkin();
        } else if (mc.player != null) {
            textures = mc.player.getSkin();
        }
        if (textures == null) {
            textures = DefaultSkinHelper.getSkinTextures(Util.NIL_UUID);
        }

        boolean slim = (textures != null && textures.model() == PlayerSkinType.SLIM);
        PlayerEntityModel model = getPreviewModel(mc, slim);
        Identifier skinTexture = (textures != null && textures.body() != null)
                ? textures.body().texturePath()
                : DefaultSkinHelper.getTexture();

        if (model != null) {
            // Base scale leaves room for halo and horns at top
            float baseScale = (0.72f * (float) skinH / 2.125f) * previewZoom;
            c.addPlayerSkin(model, skinTexture, baseScale, previewPitch, previewYaw, -1.0625f,
                    skinX, skinY, skinX + skinW, skinY + skinH);
        }

        // BOTTOM CONTROLS TOOLBAR: [◀] [ − ] [ 100% ] [ + ] [▶] [ ⟲ ]
        int ctrlY = py + ph - 30;
        int btnH = 15;
        int btnW = 16;
        int totalControlsW = btnW * 4 + 36 + 6; // 4 small buttons + 1 zoom label + gaps
        int startBtnX = px + (pw - totalControlsW) / 2;

        // 1. Rotate Left [◀]
        int btnRotLeftX = startBtnX;
        boolean hoverRotL = screen.inside(mx, my, btnRotLeftX, ctrlY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btnRotLeftX, ctrlY, btnW, btnH, hoverRotL ? GuiTheme.accent() : 0x80181C26, 3.0f);
        screen.text(c, "◀", btnRotLeftX + 4, ctrlY + 4, hoverRotL ? 0xFFFFFFFF : 0xFFCBD5E1);

        // 2. Zoom Out [ − ]
        int btnZoomOutX = btnRotLeftX + btnW + 3;
        boolean hoverZoomOut = screen.inside(mx, my, btnZoomOutX, ctrlY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btnZoomOutX, ctrlY, btnW, btnH, hoverZoomOut ? GuiTheme.accent() : 0x80181C26, 3.0f);
        screen.text(c, "−", btnZoomOutX + 5, ctrlY + 4, hoverZoomOut ? 0xFFFFFFFF : 0xFFCBD5E1);

        // 3. Zoom Percent Display [ 100% ] (Click resets zoom)
        int zoomLblW = 34;
        int zoomLblX = btnZoomOutX + btnW + 3;
        boolean hoverZoomLbl = screen.inside(mx, my, zoomLblX, ctrlY, zoomLblW, btnH);
        CustomGuiUtils.fillUltraRounded(c, zoomLblX, ctrlY, zoomLblW, btnH, hoverZoomLbl ? 0x90273142 : 0x60141822, 3.0f);
        String zoomStr = Math.round((previewZoom / DEFAULT_ZOOM) * 100.0f) + "%";
        int zw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(zoomStr));
        screen.text(c, zoomStr, zoomLblX + (zoomLblW - zw) / 2, ctrlY + 4, hoverZoomLbl ? 0xFFFFFFFF : 0xFF94A3B8);

        // 4. Zoom In [ + ]
        int btnZoomInX = zoomLblX + zoomLblW + 3;
        boolean hoverZoomIn = screen.inside(mx, my, btnZoomInX, ctrlY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btnZoomInX, ctrlY, btnW, btnH, hoverZoomIn ? GuiTheme.accent() : 0x80181C26, 3.0f);
        screen.text(c, "+", btnZoomInX + 5, ctrlY + 4, hoverZoomIn ? 0xFFFFFFFF : 0xFFCBD5E1);

        // 5. Rotate Right [▶]
        int btnRotRightX = btnZoomInX + btnW + 3;
        boolean hoverRotR = screen.inside(mx, my, btnRotRightX, ctrlY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btnRotRightX, ctrlY, btnW, btnH, hoverRotR ? GuiTheme.accent() : 0x80181C26, 3.0f);
        screen.text(c, "▶", btnRotRightX + 5, ctrlY + 4, hoverRotR ? 0xFFFFFFFF : 0xFFCBD5E1);

        // 6. Reset View [ ⟲ ]
        int btnResetX = btnRotRightX + btnW + 3;
        boolean hoverReset = screen.inside(mx, my, btnResetX, ctrlY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, btnResetX, ctrlY, btnW, btnH, hoverReset ? 0xFFDC2626 : 0x80181C26, 3.0f);
        screen.text(c, "⟲", btnResetX + 4, ctrlY + 4, hoverReset ? 0xFFFFFFFF : 0xFF94A3B8);

        // Bottom hint text
        String hint = "Scrollen = Zoom • Ziehen = Drehen";
        int hw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(hint));
        screen.text(c, hint, px + (pw - hw) / 2, py + ph - 12, 0xFF475569);
    }

    @Override
    public boolean mouseClicked(BameClientScreen screen, Click click, boolean twice,
                                int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (!isVisible(search)) return false;

        double mx = click.x();
        double my = click.y();
        int totalW = columnWidth * 2 + 16;
        int startY = Math.max(tracker.leftY, tracker.rightY);

        // 1. Top Sub-categories Click
        int catBarY = startY;
        int catTabW = (totalW - 130) / SUB_CATS.length;
        for (int i = 0; i < SUB_CATS.length; i++) {
            int tx = leftX + i * catTabW;
            if (screen.inside(mx, my, tx, catBarY, catTabW - 4, 24)) {
                subCategory = SUB_CATS[i];
                ClientSoundManager.playClick();
                return true;
            }
        }

        // Quick Unequip All Button Click
        int resetW = 120;
        int resetX = leftX + totalW - resetW;
        if (screen.inside(mx, my, resetX, catBarY, resetW, 24)) {
            CosmeticsConfig.setWings("none");
            CosmeticsConfig.setHead("none");
            CosmeticsConfig.setPet("none", "");
            previewWings = "none";
            previewHead = "none";
            previewPet = "none";
            ClientSoundManager.playClick();
            return true;
        }

        // 2. Right Preview Panel Controls Click
        boolean showPreview = totalW >= 380;
        int previewW = showPreview ? Math.clamp((int) (totalW * 0.35f), 150, 195) : 0;
        if (showPreview) {
            int previewX = leftX + totalW - previewW;
            int previewY = startY + 36;
            int previewH = lastPreviewH > 0 ? lastPreviewH : 300;
            int ctrlY = previewY + previewH - 30;
            int btnH = 15;
            int btnW = 16;
            int totalControlsW = btnW * 4 + 36 + 6;
            int startBtnX = previewX + (previewW - totalControlsW) / 2;

            int btnRotLeftX = startBtnX;
            int btnZoomOutX = btnRotLeftX + btnW + 3;
            int zoomLblW = 34;
            int zoomLblX = btnZoomOutX + btnW + 3;
            int btnZoomInX = zoomLblX + zoomLblW + 3;
            int btnRotRightX = btnZoomInX + btnW + 3;
            int btnResetX = btnRotRightX + btnW + 3;

            // Rotate left
            if (screen.inside(mx, my, btnRotLeftX, ctrlY, btnW, btnH)) {
                previewYaw += 25.0f;
                ClientSoundManager.playClick();
                return true;
            }
            // Zoom out
            if (screen.inside(mx, my, btnZoomOutX, ctrlY, btnW, btnH)) {
                previewZoom = Math.clamp(previewZoom - 0.12f, 0.35f, 2.0f);
                ClientSoundManager.playClick();
                return true;
            }
            // Reset zoom
            if (screen.inside(mx, my, zoomLblX, ctrlY, zoomLblW, btnH)) {
                previewZoom = DEFAULT_ZOOM;
                ClientSoundManager.playClick();
                return true;
            }
            // Zoom in
            if (screen.inside(mx, my, btnZoomInX, ctrlY, btnW, btnH)) {
                previewZoom = Math.clamp(previewZoom + 0.12f, 0.35f, 2.0f);
                ClientSoundManager.playClick();
                return true;
            }
            // Rotate right
            if (screen.inside(mx, my, btnRotRightX, ctrlY, btnW, btnH)) {
                previewYaw -= 25.0f;
                ClientSoundManager.playClick();
                return true;
            }
            // Reset view
            if (screen.inside(mx, my, btnResetX, ctrlY, btnW, btnH)) {
                previewYaw = DEFAULT_YAW;
                previewPitch = DEFAULT_PITCH;
                previewZoom = DEFAULT_ZOOM;
                ClientSoundManager.playClick();
                return true;
            }

            // Click inside preview panel to begin smooth drag rotation
            if (screen.inside(mx, my, previewX, previewY, previewW, previewH)) {
                previewDragging = true;
                return true;
            }
        }

        // 3. Item Cards Click (No separate buttons! Click card to equip/toggle!)
        int cardStartY = startY + 36;
        int cardsAreaW = showPreview ? (totalW - previewW - 12) : totalW;
        int colW = (cardsAreaW - 10) / 2;

        List<CosmeticItem> list = getFilteredItems(search);
        int curLeftY = cardStartY;
        int curRightY = cardStartY;

        for (int i = 0; i < list.size(); i++) {
            CosmeticItem item = list.get(i);
            boolean isLeft = (i % 2 == 0);
            int cardX = isLeft ? leftX : leftX + colW + 10;
            int cardY = isLeft ? curLeftY : curRightY;
            int h = cardHeight(item);

            // If Spieler-Skin Pet text field is clicked:
            if ("custom".equalsIgnoreCase(item.id)) {
                if (playerPetField.mouseClicked(click, twice)) {
                    return true;
                }
            }

            // Clicking ANYWHERE on the card toggles equipment and selects preview!
            if (screen.inside(mx, my, cardX, cardY, colW, h)) {
                String input = playerPetField.getText().trim();
                item.toggleEquipped(input);
                item.selectForPreview();
                ClientSoundManager.playClick();
                return true;
            }

            if (isLeft) curLeftY += h + 10;
            else curRightY += h + 10;
        }

        tracker.leftY = curLeftY;
        tracker.rightY = curRightY;
        return false;
    }

    @Override
    public boolean mouseDragged(BameClientScreen screen, Click click, double deltaX, double deltaY) {
        if (previewDragging) {
            previewYaw += (float) deltaX * 1.5f;
            previewPitch = Math.clamp(previewPitch - (float) deltaY * 1.5f, -65.0f, 65.0f);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(BameClientScreen screen, Click click) {
        previewDragging = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Zoom 3D Player smoothly when hovering over the preview box!
        if (lastPreviewW > 0 && lastPreviewH > 0) {
            if (mouseX >= lastPreviewX && mouseX <= lastPreviewX + lastPreviewW
                    && mouseY >= lastPreviewY && mouseY <= lastPreviewY + lastPreviewH) {
                previewZoom = Math.clamp(previewZoom + (float) verticalAmount * 0.08f, 0.35f, 2.0f);
                return true; // consumed scroll!
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(BameClientScreen screen, KeyInput input) {
        if (playerPetField.visible && playerPetField.isFocused()) {
            if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                String inputName = playerPetField.getText().trim();
                CosmeticsConfig.setPet("custom", inputName);
                if (!inputName.isEmpty()) {
                    PlayerSkinResolver.resolve(inputName);
                }
                previewPet = "custom";
                ClientSoundManager.playClick();
                return true;
            }
            return playerPetField.keyPressed(input);
        }
        return false;
    }

    @Override
    public boolean charTyped(BameClientScreen screen, CharInput input) {
        if (playerPetField.visible && playerPetField.isFocused()) {
            boolean typed = playerPetField.charTyped(input);
            if (typed) {
                String inputName = playerPetField.getText().trim();
                CosmeticsConfig.setPetCustomPlayer(inputName);
                if (!inputName.isEmpty()) {
                    PlayerSkinResolver.resolve(inputName);
                }
            }
            return typed;
        }
        return false;
    }
}
