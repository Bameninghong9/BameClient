package com.bame.client.gui.tab;

import com.bame.client.cosmetics.CaeserCosmeticsRenderer;
import com.bame.client.cosmetics.CaeserSkinManager;
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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CosmeticsTab implements ClientTab {

    // Interne IDs bleiben "HEAD" und "PETS" (Config-Schlüssel head/pet), angezeigt werden "HAT" und "PET".
    public static String subCategory = "ALL"; // "ALL", "WINGS", "HEAD", "PETS", "ACCESSORIES", "FACE", "EMOTES", "SKINS"
    private static final String[] SUB_CATS = {"ALL", "WINGS", "HEAD", "PETS", "ACCESSORIES", "FACE", "EMOTES", "SKINS"};
    public static int assigningToWheelSlot = -1;

    // ==========================================
    // Seitenleiste: einmal definiert, für Zeichnen UND Klicken genutzt
    // ==========================================
    public record SidebarEntry(String id, String label, int indent, boolean isHeader) {}
    public record SidebarRow(SidebarEntry entry, int y, int height) {}

    public static final String DROPDOWN_ID = "COSMETICS";
    /** Aufgeklappt-Zustand des COSMETICS-Dropdowns; bleibt erhalten, solange das Spiel läuft. */
    public static boolean cosmeticsExpanded = true;

    private static final List<SidebarEntry> SIDEBAR_ENTRIES = List.of(
            new SidebarEntry("ALL", "✦ All", 0, false),
            new SidebarEntry(DROPDOWN_ID, "COSMETICS", 0, true),
            new SidebarEntry("WINGS", "WINGS", 1, false),
            new SidebarEntry("HEAD", "HAT", 1, false),
            new SidebarEntry("PETS", "PET", 1, false),
            new SidebarEntry("ACCESSORIES", "ACCESSORIES", 1, false),
            new SidebarEntry("FACE", "FACE", 1, false),
            new SidebarEntry("EMOTES", "💃 Emotes", 0, false)
    );

    /**
     * Berechnet die sichtbaren Seitenleisten-Zeilen zwischen top und bottom. Passt die normale Höhe nicht,
     * werden Zeilen und Abstände kompakter, damit nichts über den Zurück-Knopf ragt.
     */
    public static List<SidebarRow> sidebarRows(int top, int bottom) {
        List<SidebarEntry> visible = new ArrayList<>();
        for (SidebarEntry e : SIDEBAR_ENTRIES) {
            if (e.indent() == 0 || cosmeticsExpanded) visible.add(e);
        }
        int[][] sizes = {{26, 16, 6}, {20, 14, 4}, {16, 12, 2}}; // {Hauptzeile, Unterzeile, Abstand}
        int[] chosen = sizes[sizes.length - 1];
        for (int[] s : sizes) {
            if (sidebarHeight(visible, s) <= bottom - top) { chosen = s; break; }
        }
        List<SidebarRow> rows = new ArrayList<>();
        int y = top;
        SidebarEntry prev = null;
        for (SidebarEntry e : visible) {
            if (prev != null && !(e.indent() > 0 && (prev.indent() > 0 || prev.isHeader()))) y += chosen[2];
            int h = e.indent() > 0 ? chosen[1] : chosen[0];
            rows.add(new SidebarRow(e, y, h));
            y += h;
            prev = e;
        }
        return rows;
    }

    private static int sidebarHeight(List<SidebarEntry> visible, int[] s) {
        int total = 0;
        SidebarEntry prev = null;
        for (SidebarEntry e : visible) {
            if (prev != null && !(e.indent() > 0 && (prev.indent() > 0 || prev.isHeader()))) total += s[2];
            total += e.indent() > 0 ? s[1] : s[0];
            prev = e;
        }
        return total;
    }

    /** Reagiert auf einen Klick in eine Seitenleisten-Zeile (Dropdown auf/zu oder Kategorie wählen). */
    public static void onSidebarClick(SidebarEntry entry) {
        if (entry.isHeader()) {
            cosmeticsExpanded = !cosmeticsExpanded;
            // Zugeklappt: Inhalt zeigt wieder alle Cosmetics
            if (!cosmeticsExpanded && isDropdownCategory(subCategory)) subCategory = "ALL";
        } else {
            subCategory = entry.id();
        }
    }

    public static boolean isDropdownCategory(String id) {
        for (SidebarEntry e : SIDEBAR_ENTRIES) {
            if (e.indent() > 0 && e.id().equalsIgnoreCase(id)) return true;
        }
        return false;
    }

    // Preview state (what is shown on the 3D player right now)
    public static String previewWings = null;
    public static String previewHead = null;
    public static String previewFace = null;
    public static String previewPet = null;
    public static String previewSkin = null;

    public static void resetPreview() {
        previewWings = "none";
        previewHead = "none";
        previewFace = "none";
        previewPet = "none";
        previewSkin = null;
        customizingAccessory = null;
    }

    // Card standalone 3D models (each has visible=false on all body parts)
    public static final Map<PlayerEntityModel, String> CARD_MODEL_MAP = new IdentityHashMap<>();
    private static final Map<String, PlayerEntityModel> ITEM_CARD_MODELS = new HashMap<>();

    // Skin card models (all body parts visible, no extra accessories rendered on them)
    public static final Set<PlayerEntityModel> SKIN_CARD_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String, PlayerEntityModel> SKIN_CARD_MODELS_CACHE = new HashMap<>();

    public static boolean isSkinCardModel(PlayerEntityModel model) {
        return model != null && SKIN_CARD_MODELS.contains(model);
    }

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

    // Layout constants (4 items per row, matching NoRisk Client)
    public static final int COLS = 4;
    public static final int CARD_HEIGHT = 112;
    public static final int HEADER_HEIGHT = 22;
    public static final int CARD_GAP = 8;

    // Custom Skin Modal & Accessory Editing
    public static String customizingAccessory = null; // "self", "custom", or null
    public static boolean customSkinModalOpen = false;
    public static final CustomTextFieldWidget modalPlayerField =
            new CustomTextFieldWidget(0, 0, 248, 20, Text.literal("Player Name"));

    static {
        modalPlayerField.setPlaceholder("Enter username...");
        modalPlayerField.setDrawsBackground(true);
        if (CosmeticsConfig.petCustomPlayer != null && !CosmeticsConfig.petCustomPlayer.isEmpty()) {
            modalPlayerField.setText(CosmeticsConfig.petCustomPlayer);
        }
    }

    // Cosmetic Item Definition
    public static class CosmeticItem {
        public final String id;
        public final String category; // "WINGS", "HEAD", "FACE", "ACCESSORIES", "PETS", "EMOTES", "SKINS"
        public final String name;
        public final String desc;

        public CosmeticItem(String id, String category, String name, String desc) {
            this.id = id;
            this.category = category;
            this.name = name;
            this.desc = desc;
        }

        public boolean isUnlocked() {
            return com.bame.client.emotes.EmoteUnlockManager.isCosmeticUnlocked(category, id);
        }

        public boolean isEquipped() {
            if (!isUnlocked()) return false;
            return switch (category) {
                case "WINGS" -> id.equalsIgnoreCase(CosmeticsConfig.wings);
                case "HEAD" -> id.equalsIgnoreCase(CosmeticsConfig.head);
                case "FACE" -> id.equalsIgnoreCase(CosmeticsConfig.face);
                case "ACCESSORIES", "PETS", "PET" -> CosmeticsConfig.isAccessoryEquipped(id);
                case "EMOTES" -> CosmeticsConfig.isEmoteEquipped(id);
                case "SKINS" -> id.equalsIgnoreCase(CaeserSkinManager.getActiveSkinId());
                default -> false;
            };
        }

        public boolean isSelected() {
            return switch (category) {
                case "WINGS" -> id.equalsIgnoreCase(previewWings != null ? previewWings : CosmeticsConfig.wings);
                case "HEAD" -> id.equalsIgnoreCase(previewHead != null ? previewHead : CosmeticsConfig.head);
                case "FACE" -> id.equalsIgnoreCase(previewFace != null ? previewFace : CosmeticsConfig.face);
                case "ACCESSORIES", "PETS", "PET" -> CosmeticsConfig.isAccessoryEquipped(id);
                case "EMOTES" -> CosmeticsConfig.isEmoteEquipped(id);
                case "SKINS" -> id.equalsIgnoreCase(previewSkin != null ? previewSkin : CaeserSkinManager.getActiveSkinId());
                default -> false;
            };
        }

        public void selectForPreview() {
            switch (category) {
                case "WINGS" -> previewWings = id;
                case "HEAD" -> previewHead = id;
                case "FACE" -> previewFace = id;
                case "ACCESSORIES", "PETS", "PET" -> previewPet = id;
                case "SKINS" -> previewSkin = id;
            }
        }

        public void toggleEquipped() {
            if (!isUnlocked()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                return;
            }
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
                case "FACE" -> {
                    // höchstens ein Gesichts-Cosmetic; erneuter Klick legt es ab
                    if (isEquipped()) {
                        CosmeticsConfig.setFace("none");
                        previewFace = "none";
                    } else {
                        CosmeticsConfig.setFace(id);
                        previewFace = id;
                    }
                }
                case "ACCESSORIES", "PETS", "PET" -> {
                    CosmeticsConfig.toggleAccessory(id);
                    if (CosmeticsConfig.isAccessoryEquipped(id)) {
                        customizingAccessory = id;
                        previewPet = id;
                    } else {
                        if (id.equalsIgnoreCase(customizingAccessory)) {
                            customizingAccessory = null;
                        }
                        previewPet = CosmeticsConfig.accessories.isEmpty() ? "none" : CosmeticsConfig.accessories.iterator().next();
                    }
                }
                case "EMOTES" -> {
                    CosmeticsConfig.toggleEmote(id);
                    com.bame.client.emotes.EmoteType emType = com.bame.client.emotes.EmoteType.fromId(id);
                    if (emType != null) {
                        if (CosmeticsConfig.isEmoteEquipped(id)) {
                            com.bame.client.module.EmoteModule.playEmote(emType);
                        } else {
                            com.bame.client.module.EmoteModule.stopEmote();
                        }
                    }
                }
                case "SKINS" -> {
                    SkinProtectModule.enabled = false;
                    if (isEquipped() && !"account".equalsIgnoreCase(id)) {
                        CaeserSkinManager.setActiveSkin("account");
                        previewSkin = "account";
                    } else {
                        CaeserSkinManager.setActiveSkin(id);
                        previewSkin = id;
                    }
                }
            }
        }
    }

    public static class CosmeticSection {
        public final String title;
        public final String category;
        public final List<CosmeticItem> items;

        public CosmeticSection(String title, String category, List<CosmeticItem> items) {
            this.title = title;
            this.category = category;
            this.items = items;
        }
    }

    public List<CosmeticSection> getAllSections() {
        List<CosmeticSection> sections = new ArrayList<>();
        sections.add(new CosmeticSection("WINGS", "WINGS", List.of(
                new CosmeticItem("angel", "WINGS", "Gold Wings", "Golden wings with shimmer animation"),
                new CosmeticItem("dragon", "WINGS", "Dragon Wings", "Ender dragon wings with flapping animation")
        )));
        sections.add(new CosmeticSection("HAT", "HEAD", List.of(
                new CosmeticItem("halo", "HEAD", "Angel Halo", "Floating glowing angel halo"),
                new CosmeticItem("horns", "HEAD", "Demon Horns", "Symmetric 3D demon horns"),
                new CosmeticItem("witch_hat", "HEAD", "Witch Hat", "Pointy witch hat with a bent tip"),
                new CosmeticItem("straw_hat", "HEAD", "Straw Hat", "Wide-brimmed summer straw hat")
        )));
        sections.add(new CosmeticSection("ACCESSORIES", "ACCESSORIES", List.of(
                new CosmeticItem("minime_sit", "ACCESSORIES", "MiniMe Sit", "Miniature player sitting on shoulder"),
                new CosmeticItem("minime_head", "ACCESSORIES", "MiniMe Head", "Miniature player sitting on top of head"),
                new CosmeticItem("minime_hand", "ACCESSORIES", "MiniMe Hand", "Miniature player holding onto hand"),
                new CosmeticItem("minime_chain", "ACCESSORIES", "MiniMe Chain", "Miniature player hanging on a gold chain")
        )));
        sections.add(new CosmeticSection("PET", "PETS", List.of(
                new CosmeticItem("baby_dragon", "PETS", "Baby Dragon", "Sits on your shoulder and breathes tiny smoke puffs"),
                new CosmeticItem("jelly_slime", "PETS", "Jelly Slime", "Squash & stretch physics, sits on your head or shoulder")
        )));
        sections.add(new CosmeticSection("FACE", "FACE", List.of(
                new CosmeticItem("sunglasses", "FACE", "Sunglasses", "Cool black shades"),
                new CosmeticItem("skull_mask", "FACE", "Skull Mask", "Spooky skull face mask")
        )));
        sections.add(new CosmeticSection("EMOTES", "EMOTES", List.of(
                new CosmeticItem("t_pose", "EMOTES", "T-Pose", "Free Emote • T-Pose"),
                new CosmeticItem("sit", "EMOTES", "Sitzen", "Free Emote • Sit down"),
                new CosmeticItem("wheelchair", "EMOTES", "Rollstuhl", "Sports wheelchair with rotating wheels"),
                new CosmeticItem("guitar", "EMOTES", "E-Gitarre", "Rockstar electric guitar solo"),
                new CosmeticItem("basketball", "EMOTES", "Basketball", "Dribble, crossover and finger spin"),
                new CosmeticItem("juggle", "EMOTES", "Jonglieren", "Juggle soccer ball with feet"),
                new CosmeticItem("coin_flip", "EMOTES", "Münzwurf", "Flip gold coin heads or tails"),
                new CosmeticItem("dance", "EMOTES", "Tanz", "Groovy dance moves")
        )));

        // SKINS from Caeser Client Launcher
        List<CosmeticItem> skinItems = new ArrayList<>();
        skinItems.add(new CosmeticItem("account", "SKINS", "Account Skin", "Default Minecraft Account Skin"));
        skinItems.add(new CosmeticItem("steve", "SKINS", "Steve", "Classic Steve Skin"));
        for (CaeserSkinManager.LauncherSkinEntry entry : CaeserSkinManager.getCustomSkins()) {
            skinItems.add(new CosmeticItem(entry.id, "SKINS", entry.name, "Custom skin from Caeser Client"));
        }
        sections.add(new CosmeticSection("SKINS", "SKINS", skinItems));

        return sections;
    }

    public CosmeticsTab() {
        if (customizingAccessory == null && !"none".equalsIgnoreCase(CosmeticsConfig.pet)) {
            customizingAccessory = CosmeticsConfig.pet;
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

    public static synchronized PlayerEntityModel getSkinCardModel(MinecraftClient mc, String skinId, boolean slim) {
        if (mc.getLoadedEntityModels() == null) return null;
        PlayerEntityModel model = SKIN_CARD_MODELS_CACHE.get(skinId);
        if (model == null) {
            if (slim) {
                net.minecraft.client.render.entity.model.EntityModelLayer slimLayer =
                        new net.minecraft.client.render.entity.model.EntityModelLayer(Identifier.ofVanilla("player_slim"), "main");
                model = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(slimLayer), true);
            } else {
                model = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
            }
            SKIN_CARD_MODELS_CACHE.put(skinId, model);
            SKIN_CARD_MODELS.add(model);
        }
        model.head.visible = true;
        model.hat.visible = true;
        model.body.visible = true;
        model.rightArm.visible = true;
        model.leftArm.visible = true;
        model.rightLeg.visible = true;
        model.leftLeg.visible = true;
        return model;
    }

    public static synchronized PlayerEntityModel getPreviewModel(MinecraftClient mc, boolean slim) {
        if (mc.getLoadedEntityModels() == null) return null;
        PlayerEntityModel model;
        if (slim) {
            if (previewSlimModel == null) {
                net.minecraft.client.render.entity.model.EntityModelLayer slimLayer =
                        new net.minecraft.client.render.entity.model.EntityModelLayer(Identifier.ofVanilla("player_slim"), "main");
                previewSlimModel = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(slimLayer), true);
            }
            model = previewSlimModel;
        } else {
            if (previewWideModel == null) {
                previewWideModel = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
            }
            model = previewWideModel;
        }
        if (model != null) {
            model.head.visible = true;
            model.hat.visible = true;
            model.body.visible = true;
            model.rightArm.visible = true;
            model.leftArm.visible = true;
            model.rightLeg.visible = true;
            model.leftLeg.visible = true;
        }
        return model;
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
                || "horns".contains(q) || "pet".contains(q) || "accessories".contains(q)
                || "begleiter".contains(q) || "mini".contains(q) || "skin".contains(q)
                || "emote".contains(q) || "emotes".contains(q)
                || "hat".contains(q) || "hut".contains(q) || "face".contains(q) || "gesicht".contains(q)
                || "brille".contains(q) || "maske".contains(q);
    }

    // Zusätzliche Suchbegriffe (DE/EN) pro Kategorie
    private static String searchKeywords(String category) {
        return switch (category) {
            case "HEAD" -> "hat hut kopf head";
            case "FACE" -> "face gesicht brille maske glasses mask";
            case "PETS" -> "pet pets begleiter";
            default -> "";
        };
    }

    // Anzeigename eines Hut- oder Gesichts-Cosmetics für die Liste im Vorschaufenster
    private static String equippedLabel(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "halo" -> "ANGEL HALO";
            case "horns" -> "DEMON HORNS";
            case "witch_hat" -> "WITCH HAT";
            case "straw_hat" -> "STRAW HAT";
            case "sunglasses" -> "SUNGLASSES";
            case "skull_mask" -> "SKULL MASK";
            default -> id.toUpperCase(Locale.ROOT);
        };
    }

    private List<CosmeticSection> getActiveSections(String search) {
        List<CosmeticSection> result = new ArrayList<>();
        String q = (search != null) ? search.trim().toLowerCase(Locale.ROOT) : "";

        for (CosmeticSection sec : getAllSections()) {
            if ("ALL".equalsIgnoreCase(subCategory) && sec.category.equalsIgnoreCase("SKINS") && q.isEmpty()) {
                continue;
            }
            if (!"ALL".equalsIgnoreCase(subCategory) && !sec.category.equalsIgnoreCase(subCategory)) {
                continue;
            }
            List<CosmeticItem> filteredItems = new ArrayList<>();
            for (CosmeticItem item : sec.items) {
                if (!q.isEmpty()) {
                    boolean match = item.name.toLowerCase(Locale.ROOT).contains(q)
                            || item.category.toLowerCase(Locale.ROOT).contains(q)
                            || item.desc.toLowerCase(Locale.ROOT).contains(q)
                            || sec.title.toLowerCase(Locale.ROOT).contains(q)
                            || searchKeywords(item.category).contains(q);
                    if (!match) continue;
                }
                filteredItems.add(item);
            }
            if (!filteredItems.isEmpty()) {
                result.add(new CosmeticSection(sec.title, sec.category, filteredItems));
            }
        }
        return result;
    }

    @Override
    public void layout(BameClientScreen screen, int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (!isVisible(search)) return;

        int startY = Math.max(tracker.leftY, tracker.rightY);
        int curY = startY;

        List<CosmeticSection> activeSections = getActiveSections(search);
        boolean isAllMode = "ALL".equalsIgnoreCase(subCategory);

        for (CosmeticSection sec : activeSections) {
            if (isAllMode) {
                curY += HEADER_HEIGHT;
            }
            int rows = (sec.items.size() + COLS - 1) / COLS;
            curY += rows * (CARD_HEIGHT + CARD_GAP);
        }

        tracker.leftY = curY;
        tracker.rightY = curY;
    }

    @Override
    public void calculateHeight(BameClientScreen screen, int columnWidth, ColumnTracker tracker, String search) {
        if (!isVisible(search)) return;
        int startY = Math.max(tracker.leftY, tracker.rightY);
        int curY = startY;
        List<CosmeticSection> activeSections = getActiveSections(search);

        boolean isAllMode = "ALL".equalsIgnoreCase(subCategory);
        if (assigningToWheelSlot >= 0 && "EMOTES".equalsIgnoreCase(subCategory)) {
            curY += 24;
        }
        for (CosmeticSection sec : activeSections) {
            if (isAllMode) {
                curY += HEADER_HEIGHT;
            }
            int rows = (sec.items.size() + COLS - 1) / COLS;
            curY += rows * (CARD_HEIGHT + CARD_GAP);
        }

        tracker.leftY = curY;
        tracker.rightY = curY;
    }

    @Override
    public void render(BameClientScreen screen, DrawContext c, int mx, int my, float delta,
                       int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (!isVisible(search)) return;

        CaeserSkinManager.checkPeriodicSync();
        CosmeticsConfig.checkReload();

        MinecraftClient mc = MinecraftClient.getInstance();
        int totalW = columnWidth * 2 + 16;
        int startY = Math.max(tracker.leftY, tracker.rightY);
        int cardStartY = startY;

        boolean showPreview = totalW >= 380;
        int previewW = showPreview ? Math.clamp((int) (totalW * 0.35f), 160, 210) : 0;
        int cardsAreaW = showPreview ? (totalW - previewW - 14) : totalW;
        int colW = (cardsAreaW - (COLS - 1) * CARD_GAP) / COLS;

        List<CosmeticSection> activeSections = getActiveSections(search);
        int curY = cardStartY;
        boolean isAllMode = "ALL".equalsIgnoreCase(subCategory);
        if (assigningToWheelSlot >= 0 && "EMOTES".equalsIgnoreCase(subCategory)) {
            int bannerH = 18;
            int slotNum = (assigningToWheelSlot % 8) + 1;
            int pageNum = (assigningToWheelSlot / 8) + 1;
            String hint = "★ Wähle ein freigeschaltetes Emote für Slot " + slotNum + " (Seite " + pageNum + ")";
            CustomGuiUtils.fillUltraRounded(c, leftX, curY, cardsAreaW, bannerH, GuiTheme.alpha(GuiTheme.accent(), 45), 4.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, leftX, curY, cardsAreaW, bannerH, GuiTheme.accent(), 4.0f);
            screen.text(c, hint, leftX + 8, curY + 4, 0xFFFFFFFF);
            curY += bannerH + 6;
        }

        for (CosmeticSection sec : activeSections) {
            if (isAllMode) {
                renderSectionHeader(screen, c, sec.title, leftX, curY, cardsAreaW);
                curY += HEADER_HEIGHT;
            }

            for (int i = 0; i < sec.items.size(); i++) {
                CosmeticItem item = sec.items.get(i);
                int col = i % COLS;
                int row = i / COLS;
                int cardX = leftX + col * (colW + CARD_GAP);
                int cardY = curY + row * (CARD_HEIGHT + CARD_GAP);

                renderCosmeticCard(screen, c, mc, item, cardX, cardY, colW, CARD_HEIGHT, mx, my, delta);
            }
            int rows = (sec.items.size() + COLS - 1) / COLS;
            curY += rows * (CARD_HEIGHT + CARD_GAP);
        }

        // 3. RIGHT 3D PLAYER PREVIEW (Fixed in viewport, matching NoRisk Client)
        if (showPreview) {
            int previewX = leftX + totalW - previewW;
            int previewY = screen.getCy();
            int previewH = screen.getCh() - 4;

            renderPreviewPanel(screen, c, mc, previewX, previewY, previewW, previewH, mx, my, delta);
        }

        tracker.leftY = curY;
        tracker.rightY = curY;
    }

    private void renderSectionHeader(BameClientScreen screen, DrawContext c, String title, int x, int y, int w) {
        int textW = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(title));
        int lineW = Math.max(16, (w - textW - 24) / 2);
        int lineY = y + 7;
        int lineColor = 0x303E4C66;

        // Left dividing line
        c.fill(x + 4, lineY, x + 4 + lineW, lineY + 1, lineColor);
        // Section Title
        int textX = x + 4 + lineW + 8;
        screen.text(c, title, textX, y + 3, 0xFF94A3B8);
        // Right dividing line
        c.fill(textX + textW + 8, lineY, x + w - 4, lineY + 1, lineColor);
    }

    private void renderCosmeticCard(BameClientScreen screen, DrawContext c, MinecraftClient mc,
                                    CosmeticItem item, int x, int y, int w, int h, int mx, int my, float delta) {
        boolean equipped = item.isEquipped();
        boolean hover = screen.inside(mx, my, x, y, w, h);
        boolean unlocked = item.isUnlocked();

        // 1. Single Clean Card Surface (4 per row matching NoRisk)
        int bg = equipped ? 0xFF131724 : (hover ? 0xFF161A28 : (unlocked ? 0xFF0E111A : 0xFF0A0D15));
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, bg, 6.0f);

        int border = equipped ? GuiTheme.accent() : (hover ? (unlocked ? 0xFF3E4860 : 0xFF2A344A) : 0xFF1E2436);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, border, 6.0f);

        if (equipped) {
            // Neon accent top glow line
            c.fill(x + 6, y + 1, x + w - 6, y + 2, GuiTheme.accent());
        }

        // 2. Card Header
        int titleColor = equipped ? 0xFFFFFFFF : (hover ? 0xFFF1F5F9 : (unlocked ? 0xFFCBD5E1 : 0xFF64748B));
        screen.text(c, item.name, x + 6, y + 6, titleColor);

        // Top Right: Lock icon (🔒 closed if locked, 🔓 open if unlocked)
        int lockIconSize = 13;
        int lockX = x + w - lockIconSize - 5;
        int lockY = y + 5;
        if (!unlocked) {
            CustomGuiUtils.drawLockIcon(c, lockX + 2, lockY + 1, true, 0xFFEF4444);
        } else {
            CustomGuiUtils.drawLockIcon(c, lockX + 2, lockY + 1, false, 0xFF22C55E);
        }

        // Pencil Icon Button [ ✎ ] on top right of accessory/pet card (placed to the left of the lock icon)
        boolean isAccessory = "ACCESSORIES".equalsIgnoreCase(item.category) || "PETS".equalsIgnoreCase(item.category);
        if (isAccessory) {
            int pencilBtnSize = 13;
            int pencilX = lockX - pencilBtnSize - 4;
            int pencilY = y + 5;
            boolean hoverPencil = screen.inside(mx, my, pencilX, pencilY, pencilBtnSize, pencilBtnSize);
            boolean isCustomizing = item.id.equalsIgnoreCase(customizingAccessory);

            int pBg = isCustomizing ? GuiTheme.alpha(GuiTheme.accent(), 80) : (hoverPencil ? 0xFF2A3448 : 0xFF181C26);
            int pBorder = isCustomizing ? GuiTheme.accent() : (hoverPencil ? 0xFF4A5568 : 0xFF283042);
            CustomGuiUtils.fillUltraRounded(c, pencilX, pencilY, pencilBtnSize, pencilBtnSize, pBg, 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, pencilX, pencilY, pencilBtnSize, pencilBtnSize, pBorder, 3.0f);
            screen.text(c, "✎", pencilX + 3, pencilY + 2, isCustomizing || hoverPencil ? 0xFFFFFFFF : 0xFF94A3B8);
        }

        // 3. Live 3D Bedrock Model / Skin Model / Emote Model
        if ("SKINS".equalsIgnoreCase(item.category)) {
            boolean slim = CaeserSkinManager.isSlim(item.id);
            PlayerEntityModel skinCardModel = getSkinCardModel(mc, item.id, slim);
            if (skinCardModel != null) {
                Identifier skinTex = CaeserSkinManager.getSkinTexture(item.id, mc);
                float cardScale = 0.84f * (h - 22) / 2.125f;
                float rotY = (float) (Math.sin((System.currentTimeMillis() % 10000L) / 1000.0f * 1.5) * 8.0f);
                int prevBottom = y + h - 16;
                c.addPlayerSkin(skinCardModel, skinTex,
                        cardScale, -4.0f, rotY, -1.0625f,
                        x + 2, y + 18, x + w - 2, prevBottom);
            }
        } else if ("EMOTES".equalsIgnoreCase(item.category)) {
            com.bame.client.emotes.EmoteType emType = com.bame.client.emotes.EmoteType.fromId(item.id);
            if (mc.player != null && emType != null) {
                float animTime = (System.currentTimeMillis() % 100000L) / 1000.0f;
                com.bame.client.emotes.EmotePreviewRenderer.drawPlayerPreview(
                        c,
                        x + 2, y + 14,
                        x + w - 2, y + h - 14,
                        28,
                        mx, my,
                        mc.player,
                        emType,
                        animTime
                );
            } else {
                PlayerEntityModel pModel = getPreviewModel(mc, false);
                if (pModel != null) {
                    Identifier skinTex = (mc.player != null && mc.player.getSkin() != null)
                            ? mc.player.getSkin().body().texturePath()
                            : DefaultSkinHelper.getTexture();
                    float cardScale = 0.74f * (h - 22) / 2.125f;
                    float rotY = (float) (Math.sin((System.currentTimeMillis() % 10000L) / 1000.0f * 1.5) * 10.0f);
                    int prevBottom = y + h - 16;
                    c.addPlayerSkin(pModel, skinTex,
                            cardScale, -4.0f, rotY, -1.0625f,
                            x + 2, y + 18, x + w - 2, prevBottom);
                }
            }
        } else {
            PlayerEntityModel cardModel = getCardModel(mc, item.id);
            if (cardModel != null) {
                float cardScale = 0.84f * (h - 22) / 2.125f;
                float rotY = (float) (Math.sin((System.currentTimeMillis() % 10000L) / 1000.0f * 1.5) * 4.0f);
                int prevBottom = y + h - 16;
                Identifier cardTex = ("ACCESSORIES".equalsIgnoreCase(item.category) || "PETS".equalsIgnoreCase(item.category) || "PET".equalsIgnoreCase(item.category))
                        ? CaeserCosmeticsRenderer.getPetSkinDirect(item.id)
                        : DefaultSkinHelper.getTexture();
                c.addPlayerSkin(cardModel, cardTex,
                        cardScale, -4.0f, rotY, -1.0625f,
                        x + 2, y + 18, x + w - 2, prevBottom);
            }
        }

        // 4. Equipped Status Badge at Bottom of Card
        if (equipped) {
            String eqText = "✓ Equipped";
            if ("EMOTES".equalsIgnoreCase(item.category)) {
                int slot = CosmeticsConfig.getSlotForEmote(item.id);
                if (slot >= 0) {
                    eqText = "✓ Slot " + ((slot % 8) + 1);
                }
            }
            int eqW = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(eqText)) + 6;
            int eqX = x + (w - eqW) / 2;
            int eqY = y + h - 14;
            CustomGuiUtils.fillUltraRounded(c, eqX, eqY, eqW, 11, 0x4010B981, 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, eqX, eqY, eqW, 11, 0xFF10B981, 3.0f);
            screen.text(c, eqText, eqX + 3, eqY + 2, 0xFF34D399);
        } else if (!unlocked) {
            String lockText = "🔒 Gesperrt";
            int ltW = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(lockText)) + 6;
            int ltX = x + (w - ltW) / 2;
            int ltY = y + h - 14;
            CustomGuiUtils.fillUltraRounded(c, ltX, ltY, ltW, 11, 0x40EF4444, 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, ltX, ltY, ltW, 11, 0xFFEF4444, 3.0f);
            screen.text(c, lockText, ltX + 3, ltY + 2, 0xFFFCA5A5);
        }
    }

    private void renderPreviewPanel(BameClientScreen screen, DrawContext c, MinecraftClient mc,
                                    int px, int py, int pw, int ph, int mx, int my, float delta) {
        this.lastPreviewX = px;
        this.lastPreviewY = py;
        this.lastPreviewW = pw;
        this.lastPreviewH = ph;

        // Container (Subtle dark background, No heavy cyan borders!)
        CustomGuiUtils.fillUltraRounded(c, px, py, pw, ph, 0x850E111A, 8.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, px, py, pw, ph, 0x35283246, 8.0f);

        // Top-Right: Equipped Items List in English (All equipped accessories + wings + head, matching NoRisk Bild 3/4)
        String curW = previewWings != null ? previewWings : CosmeticsConfig.wings;
        String curH = previewHead != null ? previewHead : CosmeticsConfig.head;
        String curF = previewFace != null ? previewFace : CosmeticsConfig.face;

        List<String> equippedList = new ArrayList<>();
        if (!"none".equalsIgnoreCase(curW)) {
            equippedList.add("angel".equalsIgnoreCase(curW) ? "GOLD WINGS" : "DRAGON WINGS");
        }
        if (!"none".equalsIgnoreCase(curH)) {
            equippedList.add(equippedLabel(curH));
        }
        if (curF != null && !"none".equalsIgnoreCase(curF)) {
            equippedList.add(equippedLabel(curF));
        }
        if (CosmeticsConfig.accessories != null) {
            for (String acc : CosmeticsConfig.accessories) {
                switch (acc.toLowerCase(java.util.Locale.ROOT)) {
                    case "minime_sit", "self", "custom" -> equippedList.add("MINIME SIT");
                    case "minime_head" -> equippedList.add("MINIME HEAD");
                    case "minime_hand" -> equippedList.add("MINIME HAND");
                    case "minime_chain" -> equippedList.add("MINIME CHAIN");
                    case "baby_dragon" -> equippedList.add("BABY DRAGON");
                    case "jelly_slime" -> equippedList.add("JELLY SLIME");
                }
            }
        }
        if (CaeserSkinManager.hasActiveOverride()) {
            equippedList.add(CaeserSkinManager.getActiveSkinName().toUpperCase(Locale.ROOT));
        }
        if (CosmeticsConfig.equippedEmotes != null) {
            for (String em : CosmeticsConfig.equippedEmotes) {
                com.bame.client.emotes.EmoteType emType = com.bame.client.emotes.EmoteType.fromId(em);
                if (emType != null) {
                    equippedList.add("EMOTE: " + emType.getNameEn().toUpperCase(Locale.ROOT));
                }
            }
        }

        int listY = py + 12;
        if (equippedList.isEmpty()) {
            String noneText = "NO COSMETICS";
            int nw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(noneText));
            screen.text(c, noneText, px + pw - nw - 12, listY, 0xFF475569);
        } else {
            for (String eqName : equippedList) {
                int nw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(eqName));
                screen.text(c, eqName, px + pw - nw - 12, listY, 0xFFF1F5F9);
                listY += 12;
            }
        }

        // Viewport bounds
        int skinW = pw - 16;
        int skinH = ph - (customizingAccessory != null ? 74 : 52);
        int skinX = px + 8;
        int skinY = py + 20;

        // Pedestal / Subtle shadow under feet
        int pedW = Math.min(90, (int) (skinW * 0.65f));
        int pedX = px + (pw - pedW) / 2;
        int pedY = py + skinH + 12;
        CustomGuiUtils.fillUltraRounded(c, pedX - 6, pedY + 2, pedW + 12, 6, 0x30000000, 3);
        CustomGuiUtils.fillUltraRounded(c, pedX, pedY, pedW, 3, 0x25000000 | (GuiTheme.accent() & 0x00FFFFFF), 3);

        // 3D Player Live Direct Render
        SkinTextures textures = null;
        if (SkinProtectModule.enabled) {
            textures = SkinProtectModule.getCurrentSkin();
        } else if (previewSkin != null) {
            if ("account".equalsIgnoreCase(previewSkin)) {
                textures = CaeserSkinManager.getAccountSkinTextures(mc);
            } else if ("steve".equalsIgnoreCase(previewSkin)) {
                textures = DefaultSkinHelper.getSteve();
            } else {
                for (CaeserSkinManager.LauncherSkinEntry entry : CaeserSkinManager.getCustomSkins()) {
                    if (entry.id.equalsIgnoreCase(previewSkin)) {
                        textures = entry.getSkinTextures();
                        break;
                    }
                }
            }
        } else if (CaeserSkinManager.hasActiveOverride()) {
            textures = CaeserSkinManager.getActiveSkinTextures();
        } else {
            textures = CaeserSkinManager.getAccountSkinTextures(mc);
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
            float baseScale = (0.74f * (float) skinH / 2.125f) * previewZoom;
            c.addPlayerSkin(model, skinTexture, baseScale, previewPitch, previewYaw, -1.0625f,
                    skinX, skinY, skinX + skinW, skinY + skinH);
        }

        // Waist Rotation Arrows (Matching NoRisk Bild 1)
        int waistY = py + (skinH / 2);
        int btnSize = 18;

        // Left waist button [<]
        int btnLeftX = px + 8;
        boolean hoverLeft = screen.inside(mx, my, btnLeftX, waistY, btnSize, btnSize);
        CustomGuiUtils.fillUltraRounded(c, btnLeftX, waistY, btnSize, btnSize, hoverLeft ? 0xB0253046 : 0x50121722, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, btnLeftX, waistY, btnSize, btnSize, hoverLeft ? GuiTheme.accent() : 0x35384458, 4.0f);
        screen.text(c, "<", btnLeftX + 5, waistY + 4, hoverLeft ? 0xFFFFFFFF : 0xFFCBD5E1);

        // Right waist button [>]
        int btnRightX = px + pw - btnSize - 8;
        boolean hoverRight = screen.inside(mx, my, btnRightX, waistY, btnSize, btnSize);
        CustomGuiUtils.fillUltraRounded(c, btnRightX, waistY, btnSize, btnSize, hoverRight ? 0xB0253046 : 0x50121722, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, btnRightX, waistY, btnSize, btnSize, hoverRight ? GuiTheme.accent() : 0x35384458, 4.0f);
        screen.text(c, ">", btnRightX + 6, waistY + 4, hoverRight ? 0xFFFFFFFF : 0xFFCBD5E1);

        // [ CUSTOM SKIN ] or [ PET SETTINGS ] Button (Shown when pencil clicked / accessory selected, matching NoRisk Bild 5)
        if (customizingAccessory != null) {
            int csBtnW = pw - 24;
            int csBtnH = 22;
            int csBtnX = px + 12;
            int csBtnY = py + ph - 44;
            boolean hoverCs = screen.inside(mx, my, csBtnX, csBtnY, csBtnW, csBtnH);

            CustomGuiUtils.fillUltraRounded(c, csBtnX, csBtnY, csBtnW, csBtnH, hoverCs ? 0xFF1E283D : 0xFF121622, 4.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, csBtnX, csBtnY, csBtnW, csBtnH, hoverCs ? GuiTheme.accent() : 0xFF2A364E, 4.0f);
            boolean isPet = "baby_dragon".equalsIgnoreCase(customizingAccessory) || "jelly_slime".equalsIgnoreCase(customizingAccessory);
            String btnText = isPet ? "PET SETTINGS" : "CUSTOM SKIN";
            int twCs = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(btnText));
            screen.text(c, btnText, csBtnX + (csBtnW - twCs) / 2, csBtnY + 6, hoverCs ? 0xFFFFFFFF : 0xFFCBD5E1);
        }

        // Bottom Controls: Reset view button [⟲] & Navigation hint
        int resetBtnW = 16;
        int resetBtnH = 15;
        int resetBtnX = px + 10;
        int resetBtnY = py + ph - 19;
        boolean hoverReset = screen.inside(mx, my, resetBtnX, resetBtnY, resetBtnW, resetBtnH);
        CustomGuiUtils.fillUltraRounded(c, resetBtnX, resetBtnY, resetBtnW, resetBtnH, hoverReset ? 0x90DC2626 : 0x40181C26, 3.0f);
        screen.text(c, "⟲", resetBtnX + 4, resetBtnY + 3, hoverReset ? 0xFFFFFFFF : 0xFF64748B);

        String hint = "Drag = Rotate • Scroll = Zoom";
        int hw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(hint));
        screen.text(c, hint, px + (pw - hw) / 2 + 8, py + ph - 15, 0xFF475569);
    }

    // ==========================================
    // CUSTOM SKIN MODAL WINDOW (Matching Request)
    // ==========================================
    public static void renderCustomSkinModal(BameClientScreen screen, DrawContext c, int mx, int my, float delta) {
        if (!customSkinModalOpen) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        String targetAcc = (customizingAccessory != null && !customizingAccessory.isEmpty()) ? customizingAccessory.toLowerCase(java.util.Locale.ROOT) : "minime_sit";
        boolean isPet = "baby_dragon".equals(targetAcc) || "jelly_slime".equals(targetAcc);
        boolean hasSide = "minime_sit".equals(targetAcc) || "minime_hand".equals(targetAcc);

        int modalW = 280;
        int modalH = hasSide ? 270 : 250;
        int modalX = (screen.width - modalW) / 2;
        int modalY = (screen.height - modalH) / 2;

        // Dark dim overlay
        c.fill(0, 0, screen.width, screen.height, 0xA8000000);

        // Modal Frame
        CustomGuiUtils.fillUltraRounded(c, modalX, modalY, modalW, modalH, 0xFA0F131C, 7.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, modalX, modalY, modalW, modalH, 0xFF283448, 7.0f);
        // Top accent line
        c.fill(modalX + 12, modalY + 1, modalX + modalW - 12, modalY + 2, GuiTheme.accent());

        // Header Title
        String accTitle = switch (targetAcc) {
            case "minime_head" -> "MINIME HEAD";
            case "minime_hand" -> "MINIME HAND";
            case "minime_chain" -> "MINIME CHAIN";
            case "baby_dragon" -> "BABY DRAGON";
            case "jelly_slime" -> "JELLY SLIME";
            default -> "MINIME SIT";
        };
        screen.text(c, "CUSTOMIZE • " + accTitle, modalX + 16, modalY + 12, 0xFFFFFFFF);

        // Close button [✕]
        int closeX = modalX + modalW - 24;
        int closeY = modalY + 10;
        boolean hoverClose = screen.inside(mx, my, closeX - 2, closeY - 2, 16, 16);
        screen.text(c, "✕", closeX, closeY, hoverClose ? 0xFFF43F5E : 0xFF8E95A4);

        int boxY;
        int boxH;

        if (hasSide) {
            // Row 1: Side selection buttons [ LEFT ] [ RIGHT ] [ BOTH ]
            int sideY = modalY + 30;
            screen.text(c, "SIDE:", modalX + 16, sideY + 5, 0xFF94A3B8);

            String currentSide = CosmeticsConfig.getAccessorySide(targetAcc);
            String[] sides = {"left", "right", "both"};
            String[] sideLabels = {"LEFT", "RIGHT", "BOTH"};
            int btnW = 54;
            int btnH = 18;
            int startBtnX = modalX + 68;

            for (int i = 0; i < 3; i++) {
                int bx = startBtnX + i * (btnW + 6);
                boolean active = sides[i].equalsIgnoreCase(currentSide);
                boolean hover = screen.inside(mx, my, bx, sideY, btnW, btnH);
                int bg = active ? GuiTheme.accent() : (hover ? 0xFF253046 : 0xFF141824);
                int border = active ? GuiTheme.accent() : (hover ? 0xFF4A5568 : 0xFF262E3E);
                CustomGuiUtils.fillUltraRounded(c, bx, sideY, btnW, btnH, bg, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, bx, sideY, btnW, btnH, border, 4.0f);
                int tw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(sideLabels[i]));
                screen.text(c, sideLabels[i], bx + (btnW - tw) / 2, sideY + 5, active ? 0xFFFFFFFF : 0xFFCBD5E1);
            }

            // Row 2: Username Text Field Widget
            int fieldX = modalX + 16;
            int fieldY = modalY + 54;
            int fieldW = modalW - 32;
            int fieldH = 20;
            modalPlayerField.setX(fieldX);
            modalPlayerField.setY(fieldY);
            modalPlayerField.setWidth(fieldW);
            modalPlayerField.setHeight(fieldH);
            modalPlayerField.render(c, mx, my, delta);

            boxY = modalY + 80;
            boxH = modalH - 122;
        } else if (isPet) {
            // Placement Selection Buttons: [ L. SHOULDER ] [ R. SHOULDER ] [ HEAD ] [ FLOAT ]
            int placeY = modalY + 30;
            screen.text(c, "PLACE:", modalX + 14, placeY + 5, 0xFF94A3B8);

            String currentPlace = CosmeticsConfig.getPetPlacement(targetAcc);
            String[] places = {"left_shoulder", "right_shoulder", "head", "floating"};
            String[] placeLabels = {"L. SHOULDER", "R. SHOULDER", "HEAD", "FLOAT"};
            int[] widths = {56, 56, 42, 44};
            int startBtnX = modalX + 58;

            int curBx = startBtnX;
            for (int i = 0; i < 4; i++) {
                int bw = widths[i];
                boolean active = places[i].equalsIgnoreCase(currentPlace);
                boolean hover = screen.inside(mx, my, curBx, placeY, bw, 18);
                int bg = active ? GuiTheme.accent() : (hover ? 0xFF253046 : 0xFF141824);
                int border = active ? GuiTheme.accent() : (hover ? 0xFF4A5568 : 0xFF262E3E);
                CustomGuiUtils.fillUltraRounded(c, curBx, placeY, bw, 18, bg, 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, curBx, placeY, bw, 18, border, 4.0f);
                int tw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(placeLabels[i]));
                screen.text(c, placeLabels[i], curBx + (bw - tw) / 2, placeY + 5, active ? 0xFFFFFFFF : 0xFFCBD5E1);
                curBx += bw + 4;
            }

            boxY = modalY + 54;
            boxH = modalH - 96;
        } else {
            // Default MiniMe (Head / Chain)
            int fieldX = modalX + 16;
            int fieldY = modalY + 32;
            int fieldW = modalW - 32;
            int fieldH = 20;
            modalPlayerField.setX(fieldX);
            modalPlayerField.setY(fieldY);
            modalPlayerField.setWidth(fieldW);
            modalPlayerField.setHeight(fieldH);
            modalPlayerField.render(c, mx, my, delta);

            boxY = modalY + 58;
            boxH = modalH - 100;
        }

        // 3D Showcase Box
        int boxX = modalX + 16;
        int boxW = modalW - 32;
        CustomGuiUtils.fillUltraRounded(c, boxX, boxY, boxW, boxH, 0x60090B10, 5.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, boxX, boxY, boxW, boxH, 0xFF1C2230, 5.0f);

        // Render live 3D player skin or pet preview
        String inputName = modalPlayerField.getText().trim();
        Identifier skinTex = (!isPet && !inputName.isEmpty())
                ? PlayerSkinResolver.resolve(inputName)
                : (mc.player != null && mc.player.getSkin() != null ? mc.player.getSkin().body().texturePath() : DefaultSkinHelper.getTexture());

        PlayerEntityModel pModel = getPreviewModel(mc, false);
        if (pModel != null) {
            float modelScale = 0.82f * (float) boxH / 2.125f;
            float rotY = (float) ((System.currentTimeMillis() % 6000L) / 6000.0f * 360.0f);
            c.addPlayerSkin(pModel, skinTex, modelScale, -4.0f, rotY, -1.0625f,
                    boxX + 4, boxY + 4, boxX + boxW - 4, boxY + boxH - 4);
        }

        // Action Buttons at Bottom
        int btnY = modalY + modalH - 34;
        int btnH = 22;
        int btnW = (modalW - 32 - 10) / 2;
        int cancelX = modalX + 16;
        int applyX = cancelX + btnW + 10;

        // CANCEL Button
        boolean hoverCancel = screen.inside(mx, my, cancelX, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, cancelX, btnY, btnW, btnH, hoverCancel ? 0xFF242A38 : 0xFF141722, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, cancelX, btnY, btnW, btnH, hoverCancel ? 0xFF475569 : 0xFF262E3E, 4.0f);
        int twCancel = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText("CANCEL"));
        screen.text(c, "CANCEL", cancelX + (btnW - twCancel) / 2, btnY + 6, hoverCancel ? 0xFFFFFFFF : 0xFF94A3B8);

        // APPLY Button
        boolean hoverApply = screen.inside(mx, my, applyX, btnY, btnW, btnH);
        CustomGuiUtils.fillUltraRounded(c, applyX, btnY, btnW, btnH, hoverApply ? GuiTheme.accent() : GuiTheme.alpha(GuiTheme.accent(), 85), 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, applyX, btnY, btnW, btnH, GuiTheme.accent(), 4.0f);
        int twApply = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText("APPLY"));
        screen.text(c, "APPLY", applyX + (btnW - twApply) / 2, btnY + 6, 0xFFFFFFFF);
    }

    public static void applyModalSkin() {
        String targetAcc = (customizingAccessory != null && !customizingAccessory.isEmpty()) ? customizingAccessory.toLowerCase(java.util.Locale.ROOT) : "minime_sit";
        boolean isPet = "baby_dragon".equals(targetAcc) || "jelly_slime".equals(targetAcc);
        if (!isPet) {
            String name = modalPlayerField.getText().trim();
            CosmeticsConfig.setAccessoryCustomPlayer(targetAcc, name);
            if (!name.isEmpty()) {
                PlayerSkinResolver.resolve(name);
            }
        }
        if (!CosmeticsConfig.isAccessoryEquipped(targetAcc)) {
            CosmeticsConfig.toggleAccessory(targetAcc);
            previewPet = targetAcc;
        }
        CosmeticsConfig.save();
        customSkinModalOpen = false;
        ClientSoundManager.playClick();
    }

    public static boolean handleModalClick(BameClientScreen screen, Click click, boolean twice) {
        if (!customSkinModalOpen) return false;
        double mx = click.x();
        double my = click.y();
        String targetAcc = (customizingAccessory != null && !customizingAccessory.isEmpty()) ? customizingAccessory.toLowerCase(java.util.Locale.ROOT) : "minime_sit";
        boolean isPet = "baby_dragon".equals(targetAcc) || "jelly_slime".equals(targetAcc);
        boolean hasSide = "minime_sit".equals(targetAcc) || "minime_hand".equals(targetAcc);

        int modalW = 280;
        int modalH = hasSide ? 270 : 250;
        int modalX = (screen.width - modalW) / 2;
        int modalY = (screen.height - modalH) / 2;

        // Click outside modal frame closes it
        if (!screen.inside(mx, my, modalX, modalY, modalW, modalH)) {
            customSkinModalOpen = false;
            return true;
        }

        // Close button [✕]
        int closeX = modalX + modalW - 24;
        int closeY = modalY + 10;
        if (screen.inside(mx, my, closeX - 4, closeY - 4, 20, 20)) {
            customSkinModalOpen = false;
            ClientSoundManager.playClick();
            return true;
        }

        // Side selection clicks (LEFT, RIGHT, BOTH)
        if (hasSide) {
            int sideY = modalY + 30;
            String[] sides = {"left", "right", "both"};
            int btnW = 54;
            int btnH = 18;
            int startBtnX = modalX + 68;
            for (int i = 0; i < 3; i++) {
                int bx = startBtnX + i * (btnW + 6);
                if (screen.inside(mx, my, bx, sideY, btnW, btnH)) {
                    CosmeticsConfig.setAccessorySide(targetAcc, sides[i]);
                    ClientSoundManager.playClick();
                    return true;
                }
            }

            int fieldX = modalX + 16;
            int fieldY = modalY + 54;
            int fieldW = modalW - 32;
            int fieldH = 20;
            if (screen.inside(mx, my, fieldX, fieldY, fieldW, fieldH)) {
                modalPlayerField.mouseClicked(click, twice);
                return true;
            } else {
                modalPlayerField.setFocused(false);
            }
        } else if (isPet) {
            int placeY = modalY + 30;
            String[] places = {"left_shoulder", "right_shoulder", "head", "floating"};
            int[] widths = {56, 56, 42, 44};
            int curBx = modalX + 58;
            for (int i = 0; i < 4; i++) {
                int bw = widths[i];
                if (screen.inside(mx, my, curBx, placeY, bw, 18)) {
                    CosmeticsConfig.setPetPlacement(targetAcc, places[i]);
                    ClientSoundManager.playClick();
                    return true;
                }
                curBx += bw + 4;
            }
        } else {
            int fieldX = modalX + 16;
            int fieldY = modalY + 32;
            int fieldW = modalW - 32;
            int fieldH = 20;
            if (screen.inside(mx, my, fieldX, fieldY, fieldW, fieldH)) {
                modalPlayerField.mouseClicked(click, twice);
                return true;
            } else {
                modalPlayerField.setFocused(false);
            }
        }

        // Bottom Buttons
        int btnY = modalY + modalH - 34;
        int btnH = 22;
        int btnW = (modalW - 32 - 10) / 2;
        int cancelX = modalX + 16;
        int applyX = cancelX + btnW + 10;

        if (screen.inside(mx, my, cancelX, btnY, btnW, btnH)) {
            customSkinModalOpen = false;
            ClientSoundManager.playClick();
            return true;
        }

        if (screen.inside(mx, my, applyX, btnY, btnW, btnH)) {
            applyModalSkin();
            return true;
        }

        return true;
    }

    public static boolean handleModalKeyPressed(BameClientScreen screen, KeyInput input) {
        if (!customSkinModalOpen) return false;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            customSkinModalOpen = false;
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ENTER) {
            applyModalSkin();
            return true;
        }
        return modalPlayerField.keyPressed(input);
    }

    public static boolean handleModalCharTyped(BameClientScreen screen, CharInput input) {
        if (!customSkinModalOpen) return false;
        boolean typed = modalPlayerField.charTyped(input);
        if (typed) {
            String name = modalPlayerField.getText().trim();
            if (!name.isEmpty()) {
                PlayerSkinResolver.resolve(name);
            }
        }
        return typed;
    }

    @Override
    public boolean mouseClicked(BameClientScreen screen, Click click, boolean twice,
                                int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (!isVisible(search)) return false;

        double mx = click.x();
        double my = click.y();
        int totalW = columnWidth * 2 + 16;
        int startY = Math.max(tracker.leftY, tracker.rightY);

        // 1. Right Preview Panel Controls Click
        boolean showPreview = totalW >= 380;
        int previewW = showPreview ? Math.clamp((int) (totalW * 0.35f), 160, 210) : 0;
        if (showPreview) {
            int previewX = leftX + totalW - previewW;
            int previewY = screen.getCy();
            int previewH = screen.getCh() - 4;

            int skinH = previewH - (customizingAccessory != null ? 74 : 52);
            int waistY = previewY + (skinH / 2);
            int btnSize = 18;
            int btnLeftX = previewX + 8;
            int btnRightX = previewX + previewW - btnSize - 8;

            // Waist Left [<] (Rotate clockwise)
            if (screen.inside(mx, my, btnLeftX, waistY, btnSize, btnSize)) {
                previewYaw += 30.0f;
                ClientSoundManager.playClick();
                return true;
            }
            // Waist Right [>] (Rotate counter-clockwise)
            if (screen.inside(mx, my, btnRightX, waistY, btnSize, btnSize)) {
                previewYaw -= 30.0f;
                ClientSoundManager.playClick();
                return true;
            }

            // [ CUSTOM SKIN ] Button Click
            if (customizingAccessory != null) {
                int csBtnW = previewW - 24;
                int csBtnH = 22;
                int csBtnX = previewX + 12;
                int csBtnY = previewY + previewH - 44;
                if (screen.inside(mx, my, csBtnX, csBtnY, csBtnW, csBtnH)) {
                    customSkinModalOpen = true;
                    String current = CosmeticsConfig.getAccessoryCustomPlayer(customizingAccessory);
                    modalPlayerField.setText(current != null ? current : "");
                    modalPlayerField.setFocused(true);
                    ClientSoundManager.playClick();
                    return true;
                }
            }

            // Reset view button [⟲]
            int resetBtnW = 16;
            int resetBtnH = 15;
            int resetBtnX = previewX + 10;
            int resetBtnY = previewY + previewH - 19;
            if (screen.inside(mx, my, resetBtnX, resetBtnY, resetBtnW, resetBtnH)) {
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

        // 2. Item Cards Click
        int cardStartY = startY;
        int cardsAreaW = showPreview ? (totalW - previewW - 14) : totalW;
        int colW = (cardsAreaW - (COLS - 1) * CARD_GAP) / COLS;

        List<CosmeticSection> activeSections = getActiveSections(search);
        int curY = cardStartY;
        boolean isAllMode = "ALL".equalsIgnoreCase(subCategory);
        if (assigningToWheelSlot >= 0 && "EMOTES".equalsIgnoreCase(subCategory)) {
            curY += 24;
        }

        for (CosmeticSection sec : activeSections) {
            if (isAllMode) {
                curY += HEADER_HEIGHT;
            }

            for (int i = 0; i < sec.items.size(); i++) {
                CosmeticItem item = sec.items.get(i);
                int col = i % COLS;
                int row = i / COLS;
                int cardX = leftX + col * (colW + CARD_GAP);
                int cardY = curY + row * (CARD_HEIGHT + CARD_GAP);

                // Clicking anywhere on the card toggles equipment!
                if (screen.inside(mx, my, cardX, cardY, colW, CARD_HEIGHT)) {
                    if (!item.isUnlocked()) {
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (mc.player != null) {
                            mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        }
                        com.bame.client.notification.NotificationManager.add(
                            new com.bame.client.notification.Notification(
                                "🔒 Gesperrt",
                                "Du hast \"" + item.name + "\" noch nicht freigeschaltet!",
                                com.bame.client.notification.Notification.Type.DISABLED,
                                2800
                            )
                        );
                        return true;
                    }

                    // Pencil Button Click on Accessory or Pet Card
                    if ("ACCESSORIES".equalsIgnoreCase(item.category) || "PETS".equalsIgnoreCase(item.category)) {
                        int pencilBtnSize = 13;
                        int pencilX = cardX + colW - pencilBtnSize - 19;
                        int pencilY = cardY + 5;
                        if (screen.inside(mx, my, pencilX, pencilY, pencilBtnSize, pencilBtnSize)) {
                            customizingAccessory = item.id;
                            customSkinModalOpen = true;
                            String current = CosmeticsConfig.getAccessoryCustomPlayer(item.id);
                            modalPlayerField.setText(current != null ? current : "");
                            modalPlayerField.setFocused(true);
                            ClientSoundManager.playClick();
                            return true;
                        }
                    }

                    // Assigning to specific wheel slot (from clicking [+] in emote wheel)
                    if (assigningToWheelSlot >= 0 && "EMOTES".equalsIgnoreCase(item.category)) {
                        int targetSlot = assigningToWheelSlot;
                        assigningToWheelSlot = -1;
                        CosmeticsConfig.setEmoteAtSlot(targetSlot, item.id);
                        ClientSoundManager.playClick();
                        int slotNum = (targetSlot % 8) + 1;
                        int pageNum = (targetSlot / 8) + 1;
                        com.bame.client.notification.NotificationManager.add(
                            new com.bame.client.notification.Notification(
                                "✓ Ausgerüstet",
                                item.name + " auf Slot " + slotNum + " (Seite " + pageNum + ") gesetzt!",
                                com.bame.client.notification.Notification.Type.ENABLED,
                                2500
                            )
                        );
                        return true;
                    }

                    item.toggleEquipped();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
            int rows = (sec.items.size() + COLS - 1) / COLS;
            curY += rows * (CARD_HEIGHT + CARD_GAP);
        }

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
        // Zoom 3D Player smoothly when hovering over the preview panel
        if (lastPreviewW > 0 && lastPreviewH > 0) {
            if (mouseX >= lastPreviewX && mouseX <= lastPreviewX + lastPreviewW
                    && mouseY >= lastPreviewY && mouseY <= lastPreviewY + lastPreviewH) {
                previewZoom = Math.clamp(previewZoom + (float) verticalAmount * 0.08f, 0.35f, 2.0f);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(BameClientScreen screen, KeyInput input) {
        return false;
    }

    @Override
    public boolean charTyped(BameClientScreen screen, CharInput input) {
        return false;
    }
}
