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
    /**
     * Eine Zeile der Seitenleiste. {@code parent}: Dropdown-Kopf, unter dem die Unterzeile hängt (null bei Köpfen).
     * Unterzeilen tragen die Abschnitts-ID ({@link CosmeticSection#id}), zu der gesprungen wird.
     */
    public record SidebarEntry(String id, String label, int indent, boolean isHeader, String parent) {}
    public record SidebarRow(SidebarEntry entry, int y, int height) {}

    /** Dropdown-Kopf COSMETICS: Gesamtansicht aller Cosmetics (interne Unterkategorie "ALL", ohne Emotes/Skins). */
    public static final String DROPDOWN_ID = "COSMETICS";
    /** Dropdown-Kopf EMOTES: eigene Emote-Ansicht (Unterkategorie "EMOTES"), gruppiert in Abschnitte. */
    public static final String EMOTES_DROPDOWN_ID = "EMOTES";
    /** Kopfzeile KILLEFFECTS: ohne Unterzeilen, ein Klick öffnet die Kill-Effekt-Ansicht (Unterkategorie "KILLEFFECTS"). */
    public static final String KILLEFFECTS_DROPDOWN_ID = com.bame.client.killfx.KillEffectType.CATEGORY;
    /** Aufgeklappt-Zustand der Dropdowns; bleibt erhalten, solange das Spiel läuft. */
    public static boolean cosmeticsExpanded = true;
    public static boolean emotesExpanded = true;

    // Abschnitte der Emote-Ansicht (IDs für Sprung, Scroll-Spy und Seitenleiste)
    public static final String EMOTES_BASIC = "EMOTES_BASIC";
    public static final String EMOTES_SPORT = "EMOTES_SPORT";
    public static final String EMOTES_PARTNER = "EMOTES_PARTNER";
    public static final String EMOTES_SPECIAL = "EMOTES_SPECIAL";

    private static final List<SidebarEntry> SIDEBAR_ENTRIES = List.of(
            new SidebarEntry(DROPDOWN_ID, "COSMETICS", 0, true, null),
            new SidebarEntry("WINGS", "WINGS", 1, false, DROPDOWN_ID),
            new SidebarEntry("HEAD", "HAT", 1, false, DROPDOWN_ID),
            new SidebarEntry("PETS", "PET", 1, false, DROPDOWN_ID),
            new SidebarEntry("ACCESSORIES", "ACCESSORIES", 1, false, DROPDOWN_ID),
            new SidebarEntry("FACE", "FACE", 1, false, DROPDOWN_ID),
            new SidebarEntry(EMOTES_DROPDOWN_ID, "EMOTES", 0, true, null),
            new SidebarEntry(EMOTES_BASIC, "BASIC", 1, false, EMOTES_DROPDOWN_ID),
            new SidebarEntry(EMOTES_SPORT, "SPORT", 1, false, EMOTES_DROPDOWN_ID),
            new SidebarEntry(EMOTES_PARTNER, "PARTNER", 1, false, EMOTES_DROPDOWN_ID),
            new SidebarEntry(EMOTES_SPECIAL, "SPECIAL", 1, false, EMOTES_DROPDOWN_ID),
            new SidebarEntry(KILLEFFECTS_DROPDOWN_ID, "KILLEFFECTS", 0, true, null)
    );

    /** Ist das Dropdown mit dieser Kopf-ID aufgeklappt? (KILLEFFECTS hat keine Unterzeilen: "offen" = Ansicht aktiv) */
    public static boolean isExpanded(String dropdownId) {
        if (KILLEFFECTS_DROPDOWN_ID.equals(dropdownId)) return isViewActive(dropdownId);
        return EMOTES_DROPDOWN_ID.equals(dropdownId) ? emotesExpanded : cosmeticsExpanded;
    }

    /** Zeigt der Inhalt gerade die Ansicht dieses Dropdowns (Cosmetics-Gesamtansicht, Emotes bzw. Kill-Effekte)? */
    public static boolean isViewActive(String dropdownId) {
        if (KILLEFFECTS_DROPDOWN_ID.equals(dropdownId)) return KILLEFFECTS_DROPDOWN_ID.equalsIgnoreCase(subCategory);
        return EMOTES_DROPDOWN_ID.equals(dropdownId)
                ? "EMOTES".equalsIgnoreCase(subCategory)
                : "ALL".equalsIgnoreCase(subCategory);
    }

    /** Ist die Kill-Effekt-Ansicht offen? */
    public static boolean isKillEffectsView() {
        return KILLEFFECTS_DROPDOWN_ID.equalsIgnoreCase(subCategory);
    }

    /** Ist diese Unterzeile die aktuell markierte (Scroll-Spy in der zugehörigen Ansicht)? */
    public static boolean isSidebarEntryActive(SidebarEntry entry) {
        return entry.parent() != null && isViewActive(entry.parent())
                && entry.id().equalsIgnoreCase(currentScrolledCategory);
    }

    /** Öffnet die Emote-Ansicht (z. B. aus dem Emote-Rad) und klappt ihr Dropdown auf. */
    public static void openEmotesView() {
        subCategory = "EMOTES";
        emotesExpanded = true;
        currentScrolledCategory = EMOTES_BASIC;
        pinnedSection = null;
    }

    /**
     * Berechnet die sichtbaren Seitenleisten-Zeilen zwischen top und bottom. Passt die normale Höhe nicht,
     * werden Zeilen und Abstände kompakter, damit nichts über den Zurück-Knopf ragt.
     */
    public static List<SidebarRow> sidebarRows(int top, int bottom) {
        List<SidebarEntry> visible = new ArrayList<>();
        for (SidebarEntry e : SIDEBAR_ENTRIES) {
            if (e.parent() == null || isExpanded(e.parent())) visible.add(e);
        }
        int[][] sizes = {{26, 16, 6}, {20, 14, 4}, {16, 12, 2}, {14, 10, 1}}; // {Hauptzeile, Unterzeile, Abstand}
        int[] chosen = sizes[sizes.length - 1];
        boolean fits = false;
        for (int[] s : sizes) {
            if (sidebarHeight(visible, s) <= bottom - top) { chosen = s; fits = true; break; }
        }
        if (!fits) {
            // Sehr kleines Fenster: Unterzeilen nicht aktiver Ansichten weglassen, damit nichts über den Zurück-Knopf ragt
            visible.removeIf(e -> e.parent() != null && !isViewActive(e.parent()));
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

    /**
     * Reagiert auf einen Klick in eine Seitenleisten-Zeile und setzt den Scroll-Stand.
     * <ul>
     *   <li>Kopf, dessen Ansicht nicht offen ist: Ansicht öffnen (nach oben scrollen) und Dropdown aufklappen.</li>
     *   <li>Kopf der bereits offenen Ansicht: nur Dropdown auf-/zuklappen (Scroll-Stand bleibt).</li>
     *   <li>Unterzeile: zugehörige Ansicht öffnen und zum Abschnitt springen.</li>
     * </ul>
     */
    public static void onSidebarClick(BameClientScreen screen, SidebarEntry entry, String search) {
        if (entry.isHeader() && KILLEFFECTS_DROPDOWN_ID.equals(entry.id())) {
            // Ohne Unterzeilen: Ansicht öffnen (nach oben scrollen); ist sie schon offen, passiert nichts
            if (isViewActive(entry.id())) return;
            subCategory = KILLEFFECTS_DROPDOWN_ID;
            currentScrolledCategory = KILLEFFECTS_DROPDOWN_ID;
            pinnedSection = null;
            screen.setScroll(0);
            return;
        }
        if (entry.isHeader()) {
            boolean emotes = EMOTES_DROPDOWN_ID.equals(entry.id());
            if (isViewActive(entry.id())) {
                if (emotes) emotesExpanded = !emotesExpanded;
                else cosmeticsExpanded = !cosmeticsExpanded;
                return;
            }
            subCategory = emotes ? "EMOTES" : "ALL";
            if (emotes) emotesExpanded = true;
            else cosmeticsExpanded = true;
            currentScrolledCategory = emotes ? EMOTES_BASIC : "WINGS";
            pinnedSection = null;
            screen.setScroll(0);
            return;
        }
        subCategory = EMOTES_DROPDOWN_ID.equals(entry.parent()) ? "EMOTES" : "ALL";
        String sectionId = entry.id().toUpperCase(Locale.ROOT);
        currentScrolledCategory = sectionId;
        Integer targetY = computeOffsets(search, null, null).get(sectionId);
        screen.setScroll(targetY != null ? targetY : 0);
        // Wird der Sprung am Listenende begrenzt, bleibt der geklickte Abschnitt markiert, bis gescrollt wird
        pinnedSection = targetY != null ? sectionId : null;
        pinnedScroll = screen.getScroll();
    }

    /** Vom Seitenleisten-Klick gewählter Abschnitt; gilt, solange der Scroll-Stand unverändert ist. */
    private static String pinnedSection = null;
    private static double pinnedScroll = 0;

    // Preview state (what is shown on the 3D player right now - null defaults to currently equipped!)
    public static String previewWings = null;
    public static String previewHead = null;
    public static String previewFace = null;
    public static String previewPet = null;
    public static String previewSkin = null;

    public static void resetPreview() {
        previewWings = null;
        previewHead = null;
        previewFace = null;
        previewPet = null;
        previewSkin = null;
        customizingAccessory = null;
    }

    // Scroll-Spy & Quick Jump to Item
    public static final Map<String, Integer> SECTION_Y_MAP = new HashMap<>();
    public static final Map<String, Integer> ITEM_Y_MAP = new HashMap<>();
    public static String currentScrolledCategory = "WINGS";

    public record EquippedPreviewItem(String id, String label, String category) {}
    public record EquippedBadgeClick(String itemId, int x, int y, int w, int h) {}
    private static final List<EquippedBadgeClick> equippedBadgeClicks = new ArrayList<>();

    public static void scrollToItem(BameClientScreen screen, String itemId) {
        if (screen == null || itemId == null) return;
        // Angelegte Cosmetics stehen in der Cosmetics-Gesamtansicht; Positionen für diese Ansicht neu berechnen
        // (ITEM_Y_MAP stammt vom letzten Frame und kann noch die Emote-Ansicht beschreiben)
        subCategory = "ALL";
        pinnedSection = null;
        Map<String, Integer> items = new HashMap<>();
        computeOffsets(screen.getSearchQuery(), null, items);
        Integer targetY = items.get(itemId.toLowerCase(Locale.ROOT));
        if (targetY != null) {
            screen.setScroll(targetY - 24);
        }
        screen.layout();
    }

    /** Werden Abschnittsüberschriften gezeigt? (Cosmetics-Gesamtansicht und Emote-Ansicht) */
    private static boolean showsSectionHeaders() {
        return "ALL".equalsIgnoreCase(subCategory) || "EMOTES".equalsIgnoreCase(subCategory);
    }

    /** Höhe des Hinweisbanners beim Zuweisen eines Emote-Rad-Slots (inkl. Abstand), sonst 0. */
    private static int wheelBannerHeight() {
        return (assigningToWheelSlot >= 0 && "EMOTES".equalsIgnoreCase(subCategory)) ? 24 : 0;
    }

    /** Höhe aller Leisten über den Karten (Emote-Rad-Hinweis bzw. Kill-Effekt-Einstellungen, inkl. Abstand). */
    private static int topBannerHeight() {
        return wheelBannerHeight() + (isKillEffectsView() ? killSettingsHeight : 0)
                + (isCapeSettingsView() ? capeSettingsHeight : 0);
    }

    // ==========================================
    // Caeser-Capes: Hinweis und Schalter oben in der Cosmetics-Gesamtansicht
    // ==========================================
    private static final String CAPE_HINT = "Capes wählst du im Caeser-Launcher unter ‚Capes‘ aus.";
    private static final List<KillToggle> CAPE_TOGGLES = List.of(
            new KillToggle("Caeser-Capes anderer Spieler anzeigen", () -> CosmeticsConfig.capesShowOthers,
                    () -> CosmeticsConfig.capesShowOthers = !CosmeticsConfig.capesShowOthers),
            new KillToggle("Cape auf der Elytra zeigen", () -> CosmeticsConfig.capeOnElytra,
                    () -> CosmeticsConfig.capeOnElytra = !CosmeticsConfig.capeOnElytra)
    );
    private static final int CAPE_HINT_H = 14;
    /** Zuletzt berechnete Höhe des Cape-Panels (hängt von der Breite ab); inkl. 8 px Abstand zu den Karten. */
    private static int capeSettingsHeight = 8 + 12 + CAPE_HINT_H + 16;
    private static final List<KillToggleHit> capeToggleHits = new ArrayList<>();

    /** Cape-Einstellungen stehen in der Cosmetics-Gesamtansicht ("ALL"). */
    private static boolean isCapeSettingsView() {
        return "ALL".equalsIgnoreCase(subCategory);
    }

    /** Fließ-Layout der Cape-Schalter unter dem Hinweis: x/y relativ zum Panel; gibt die Panel-Höhe zurück. */
    private static int layoutCapeToggles(net.minecraft.client.font.TextRenderer tr, int areaW, int[][] out) {
        int pad = 6;
        int top = pad + CAPE_HINT_H;
        int x = pad, row = 0;
        for (int i = 0; i < CAPE_TOGGLES.size(); i++) {
            int lw = tr.getWidth(CustomGuiUtils.getFontText(CAPE_TOGGLES.get(i).label()));
            int itemW = KILL_TOGGLE_W + 5 + lw + 14;
            if (x > pad && x + itemW > areaW - pad) {
                x = pad;
                row++;
            }
            if (out != null) out[i] = new int[]{x, top + row * KILL_ROW_H};
            x += itemW;
        }
        return top + pad + (row + 1) * KILL_ROW_H - 4;
    }

    private static void updateCapeSettingsHeight(BameClientScreen screen, int columnWidth) {
        if (screen == null || screen.getTextRenderer() == null) return;
        capeSettingsHeight = layoutCapeToggles(screen.getTextRenderer(), cardsAreaWidth(columnWidth), null) + 8;
    }

    private static void renderCapeSettings(BameClientScreen screen, DrawContext c, int x, int y, int w, int mx, int my) {
        int[][] pos = new int[CAPE_TOGGLES.size()][];
        int panelH = layoutCapeToggles(screen.getTextRenderer(), w, pos);
        CustomGuiUtils.fillUltraRounded(c, x, y, w, panelH, 0xFF0E111A, 5.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, panelH, 0xFF1E2436, 5.0f);
        screen.text(c, ellipsize(screen, CAPE_HINT, w - 12), x + 6, y + 7, 0xFF94A3B8);
        capeToggleHits.clear();
        for (int i = 0; i < CAPE_TOGGLES.size(); i++) {
            KillToggle t = CAPE_TOGGLES.get(i);
            int tx = x + pos[i][0];
            int ty = y + pos[i][1];
            boolean on = t.get().getAsBoolean();
            com.bame.client.gui.CustomToggleWidget.draw(c, tx, ty + 1, KILL_TOGGLE_W, KILL_TOGGLE_H, on);
            int lw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(t.label()));
            boolean hover = screen.inside(mx, my, tx, ty - 1, KILL_TOGGLE_W + 5 + lw, KILL_ROW_H - 2);
            screen.text(c, t.label(), tx + KILL_TOGGLE_W + 5, ty + 2, hover ? 0xFFFFFFFF : (on ? 0xFFCBD5E1 : 0xFF8E95A4));
            capeToggleHits.add(new KillToggleHit(t, tx, ty - 1, KILL_TOGGLE_W + 5 + lw, KILL_ROW_H - 2));
        }
    }

    // ==========================================
    // Kill-Effekte: Einstellungen oben in der Ansicht
    // ==========================================
    private record KillToggle(String label, java.util.function.BooleanSupplier get, Runnable flip) {}
    private record KillToggleHit(KillToggle toggle, int x, int y, int w, int h) {}
    private record KillTestHit(com.bame.client.killfx.KillEffectType type, int x, int y, int w, int h) {}

    private static final List<KillToggle> KILL_TOGGLES = List.of(
            new KillToggle("Show other players' effects", () -> CosmeticsConfig.killFxShowOthers,
                    () -> CosmeticsConfig.killFxShowOthers = !CosmeticsConfig.killFxShowOthers),
            new KillToggle("Also trigger on mobs", () -> CosmeticsConfig.killFxMobs,
                    () -> CosmeticsConfig.killFxMobs = !CosmeticsConfig.killFxMobs),
            new KillToggle("Screen effect", () -> CosmeticsConfig.killFxScreen,
                    () -> CosmeticsConfig.killFxScreen = !CosmeticsConfig.killFxScreen),
            new KillToggle("Sounds", () -> CosmeticsConfig.killFxSounds,
                    () -> CosmeticsConfig.killFxSounds = !CosmeticsConfig.killFxSounds),
            new KillToggle("Detect kills from chat", () -> CosmeticsConfig.killFxChat,
                    () -> CosmeticsConfig.killFxChat = !CosmeticsConfig.killFxChat)
    );
    private static final int KILL_TOGGLE_W = 20;
    private static final int KILL_TOGGLE_H = 10;
    private static final int KILL_ROW_H = 16;
    /** Zuletzt berechnete Höhe der Einstellungen (hängt von der Breite ab); inkl. 8 px Abstand zu den Karten. */
    private static int killSettingsHeight = 8 + 12 + 2 * KILL_ROW_H;
    private static final List<KillToggleHit> killToggleHits = new ArrayList<>();
    private static final List<KillTestHit> killTestHits = new ArrayList<>();

    /** Fließ-Layout der Schalter: x/y relativ zum Panel; gibt die Panel-Höhe zurück. */
    private static int layoutKillToggles(net.minecraft.client.font.TextRenderer tr, int areaW, int[][] out) {
        int pad = 6;
        int x = pad, row = 0;
        for (int i = 0; i < KILL_TOGGLES.size(); i++) {
            int lw = tr.getWidth(CustomGuiUtils.getFontText(KILL_TOGGLES.get(i).label()));
            int itemW = KILL_TOGGLE_W + 5 + lw + 14;
            if (x > pad && x + itemW > areaW - pad) {
                x = pad;
                row++;
            }
            if (out != null) out[i] = new int[]{x, pad + row * KILL_ROW_H};
            x += itemW;
        }
        return pad * 2 + (row + 1) * KILL_ROW_H - 4;
    }

    private static int cardsAreaWidth(int columnWidth) {
        int totalW = columnWidth * 2 + 16;
        boolean showPreview = totalW >= 380;
        int previewW = showPreview ? Math.clamp((int) (totalW * 0.35f), 160, 210) : 0;
        return showPreview ? (totalW - previewW - 14) : totalW;
    }

    /** Aktualisiert die Höhe der Einstellungen für die aktuelle Breite. */
    private static void updateKillSettingsHeight(BameClientScreen screen, int columnWidth) {
        if (screen == null || screen.getTextRenderer() == null) return;
        killSettingsHeight = layoutKillToggles(screen.getTextRenderer(), cardsAreaWidth(columnWidth), null) + 8;
    }

    private static void renderKillSettings(BameClientScreen screen, DrawContext c, int x, int y, int w, int mx, int my) {
        int[][] pos = new int[KILL_TOGGLES.size()][];
        int panelH = layoutKillToggles(screen.getTextRenderer(), w, pos);
        CustomGuiUtils.fillUltraRounded(c, x, y, w, panelH, 0xFF0E111A, 5.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, panelH, 0xFF1E2436, 5.0f);
        killToggleHits.clear();
        for (int i = 0; i < KILL_TOGGLES.size(); i++) {
            KillToggle t = KILL_TOGGLES.get(i);
            int tx = x + pos[i][0];
            int ty = y + pos[i][1];
            boolean on = t.get().getAsBoolean();
            com.bame.client.gui.CustomToggleWidget.draw(c, tx, ty + 1, KILL_TOGGLE_W, KILL_TOGGLE_H, on);
            int lw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(t.label()));
            boolean hover = screen.inside(mx, my, tx, ty - 1, KILL_TOGGLE_W + 5 + lw, KILL_ROW_H - 2);
            screen.text(c, t.label(), tx + KILL_TOGGLE_W + 5, ty + 2, hover ? 0xFFFFFFFF : (on ? 0xFFCBD5E1 : 0xFF8E95A4));
            killToggleHits.add(new KillToggleHit(t, tx, ty - 1, KILL_TOGGLE_W + 5 + lw, KILL_ROW_H - 2));
        }
    }

    /**
     * Abstand (px ab Inhaltsbeginn) aller Abschnitte und Items der aktuellen Ansicht, so wie {@link #render} sie
     * zeichnet. Gibt die Abschnitts-Map zurück; {@code sectionsOut}/{@code itemsOut} werden befüllt, falls gesetzt.
     */
    public static Map<String, Integer> computeOffsets(String search, Map<String, Integer> sectionsOut, Map<String, Integer> itemsOut) {
        return computeOffsets(getActiveSections(search), sectionsOut, itemsOut);
    }

    private static Map<String, Integer> computeOffsets(List<CosmeticSection> active, Map<String, Integer> sectionsOut, Map<String, Integer> itemsOut) {
        Map<String, Integer> sections = sectionsOut != null ? sectionsOut : new HashMap<>();
        int relY = topBannerHeight();
        boolean headers = showsSectionHeaders();
        for (CosmeticSection sec : active) {
            sections.put(sec.id.toUpperCase(Locale.ROOT), relY);
            if (headers) relY += HEADER_HEIGHT;
            if (itemsOut != null) {
                for (int i = 0; i < sec.items.size(); i++) {
                    itemsOut.put(sec.items.get(i).id.toLowerCase(Locale.ROOT), relY + (i / COLS) * (CARD_HEIGHT + CARD_GAP));
                }
            }
            int rows = (sec.items.size() + COLS - 1) / COLS;
            relY += rows * (CARD_HEIGHT + CARD_GAP);
        }
        return sections;
    }

    /** Überschrift des Inhalts oben in der Leiste (z. B. "HAT" oder "EMOTES • SPORT"). */
    public static String currentViewTitle() {
        if ("SKINS".equalsIgnoreCase(subCategory)) return "SKINS";
        if (isKillEffectsView()) return "KILLEFFECTS";
        boolean emotes = "EMOTES".equalsIgnoreCase(subCategory);
        String sub = switch (currentScrolledCategory == null ? "" : currentScrolledCategory) {
            case "WINGS" -> emotes ? null : "WINGS";
            case "HEAD" -> emotes ? null : "HAT";
            case "ACCESSORIES" -> emotes ? null : "ACCESSORIES";
            case "PETS" -> emotes ? null : "PET";
            case "FACE" -> emotes ? null : "FACE";
            case EMOTES_BASIC -> "BASIC";
            case EMOTES_SPORT -> "SPORT";
            case EMOTES_PARTNER -> "PARTNER";
            case EMOTES_SPECIAL -> "SPECIAL";
            default -> null;
        };
        boolean emoteSection = currentScrolledCategory != null && currentScrolledCategory.startsWith("EMOTES_");
        if (emotes || emoteSection) return sub != null ? "EMOTES • " + sub : "EMOTES";
        return sub != null ? sub : "ALL COSMETICS";
    }

    private static String equippedAccLabel(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "minime_sit", "self", "custom" -> "MINIME SIT";
            case "minime_head" -> "MINIME HEAD";
            case "minime_hand" -> "MINIME HAND";
            case "minime_chain" -> "MINIME CHAIN";
            case "baby_dragon" -> "BABY DRAGON";
            case "jelly_slime" -> "JELLY SLIME";
            default -> id.toUpperCase(Locale.ROOT);
        };
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
                case "KILLEFFECTS" -> id.equalsIgnoreCase(CosmeticsConfig.killEffect);
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
                case "KILLEFFECTS" -> id.equalsIgnoreCase(CosmeticsConfig.killEffect);
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
                case "KILLEFFECTS" -> {
                    // höchstens ein Kill-Effekt; erneuter Klick legt ihn ab
                    CosmeticsConfig.setKillEffect(isEquipped() ? "none" : id);
                }
            }
        }
    }

    public static class CosmeticSection {
        /** Abschnitts-ID für Sprung/Scroll-Spy: bei Cosmetics die Kategorie, bei Emotes die Gruppe (EMOTES_SPORT ...). */
        public final String id;
        public final String title;
        public final String category;
        public final List<CosmeticItem> items;

        public CosmeticSection(String title, String category, List<CosmeticItem> items) {
            this(category, title, category, items);
        }

        public CosmeticSection(String id, String title, String category, List<CosmeticItem> items) {
            this.id = id;
            this.title = title;
            this.category = category;
            this.items = items;
        }
    }

    public static List<CosmeticSection> getAllSections() {
        List<CosmeticSection> sections = new ArrayList<>();
        sections.add(new CosmeticSection("WINGS", "WINGS", List.of(
                new CosmeticItem("angel", "WINGS", "Gold Wings", "Golden wings with shimmer animation"),
                new CosmeticItem("dragon", "WINGS", "Dragon Wings", "Ender dragon wings with flapping animation")
        )));
        sections.add(new CosmeticSection("HAT", "HEAD", List.of(
                new CosmeticItem("halo", "HEAD", "Angel Halo", "Floating glowing angel halo"),
                new CosmeticItem("horns", "HEAD", "Demon Horns", "Symmetric 3D demon horns"),
                new CosmeticItem("witch_hat", "HEAD", "Witch Hat", "Pointy witch hat with a bent tip"),
                new CosmeticItem("straw_hat", "HEAD", "Straw Hat", "Wide-brimmed summer straw hat"),
                new CosmeticItem("crown", "HEAD", "Crown", "Golden crown with sparkling gemstones"),
                new CosmeticItem("headphones", "HEAD", "Headphones", "Gaming headset with a logo glowing in your theme color"),
                new CosmeticItem("viking_helmet", "HEAD", "Viking Helmet", "Weathered iron helmet with rivets and two horns"),
                new CosmeticItem("propeller_cap", "HEAD", "Propeller Cap", "Colorful cap whose propeller spins faster as you walk"),
                new CosmeticItem("cat_ears", "HEAD", "Cat Ears", "Twitching cat ears that flatten while sneaking")
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
        // Emotes: Kategorie bleibt "EMOTES" (Freischaltung/Ausrüsten), gruppiert in eigene Abschnitte
        sections.add(new CosmeticSection(EMOTES_BASIC, "BASIC", "EMOTES", List.of(
                emote(com.bame.client.emotes.EmoteType.T_POSE, "t_pose"),
                emote(com.bame.client.emotes.EmoteType.SIT, "sit")
        )));
        sections.add(new CosmeticSection(EMOTES_SPORT, "SPORT", "EMOTES", List.of(
                emote(com.bame.client.emotes.EmoteType.BASKETBALL, "basketball"),
                emote(com.bame.client.emotes.EmoteType.PUSH_UPS, "push_ups"),
                emote(com.bame.client.emotes.EmoteType.WHEELCHAIR, "wheelchair")
        )));
        sections.add(new CosmeticSection(EMOTES_PARTNER, "PARTNER", "EMOTES", List.of(
                emote(com.bame.client.emotes.EmoteType.HIGH_FIVE, "high_five")
        )));
        sections.add(new CosmeticSection(EMOTES_SPECIAL, "SPECIAL", "EMOTES", List.of(
                emote(com.bame.client.emotes.EmoteType.JUGGLE, "juggle"),
                emote(com.bame.client.emotes.EmoteType.CAMPFIRE, "campfire"),
                emote(com.bame.client.emotes.EmoteType.HAMMOCK, "hammock"),
                emote(com.bame.client.emotes.EmoteType.SWING, "swing"),
                emote(com.bame.client.emotes.EmoteType.PICNIC, "picnic"),
                emote(com.bame.client.emotes.EmoteType.BUTTERFLY, "butterfly")
        )));

        // Kill-Effekte (Jeton-Shop-Kategorie KILLEFFECTS): eigene Ansicht, höchstens einer ausgerüstet
        List<CosmeticItem> killItems = new ArrayList<>();
        for (com.bame.client.killfx.KillEffectType t : com.bame.client.killfx.KillEffectType.values()) {
            killItems.add(new CosmeticItem(t.id, KILLEFFECTS_DROPDOWN_ID, t.displayName, t.description));
        }
        sections.add(new CosmeticSection(KILLEFFECTS_DROPDOWN_ID, "KILLEFFECTS", KILLEFFECTS_DROPDOWN_ID, killItems));

        // SKINS from Caeser Client Launcher
        List<CosmeticItem> skinItems = new ArrayList<>();
        skinItems.add(new CosmeticItem("account", "SKINS", "Account Skin", "Default Minecraft Account Skin"));
        skinItems.add(new CosmeticItem("steve", "SKINS", "Steve", "Classic Steve Skin"));
        for (CaeserSkinManager.LauncherSkinEntry entry : CaeserSkinManager.getCustomSkins()) {
            skinItems.add(new CosmeticItem(entry.id, "SKINS", entry.name, "Custom skin from Caeser Client"));
        }
        sections.add(new CosmeticSection("SKINS", "SKINS", skinItems));

        return hideInactiveUnowned(sections);
    }

    /** Emote-Karte mit englischem Namen und Beschreibung aus {@link com.bame.client.emotes.EmoteType}. */
    private static CosmeticItem emote(com.bame.client.emotes.EmoteType type, String id) {
        return new CosmeticItem(id, "EMOTES", type.getDisplayName(), type.getDescription());
    }

    /** Jeton-Shop: inaktive Items (active == false), die man nicht besitzt, werden ausgeblendet. */
    private static List<CosmeticSection> hideInactiveUnowned(List<CosmeticSection> sections) {
        List<CosmeticSection> result = new ArrayList<>(sections.size());
        for (CosmeticSection sec : sections) {
            if ("SKINS".equalsIgnoreCase(sec.category)) {
                result.add(sec);
                continue;
            }
            List<CosmeticItem> visible = new ArrayList<>(sec.items.size());
            for (CosmeticItem item : sec.items) {
                com.bame.client.shop.Wallet.ShopItem shopItem = com.bame.client.shop.Wallet.item(item.id);
                if (shopItem != null && !shopItem.active() && !item.isUnlocked()) continue;
                visible.add(item);
            }
            if (!visible.isEmpty()) result.add(new CosmeticSection(sec.id, sec.title, sec.category, visible));
        }
        return result;
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

    // Eigene Modelle für den Vorschau-Spieler rechts (nur er zeigt das eigene Caeser-Cape, nicht z. B. das MiniMe-Fenster)
    private static PlayerEntityModel panelWideModel = null;
    private static PlayerEntityModel panelSlimModel = null;

    /** Ist das das Modell des Vorschau-Spielers im Cosmetics-Menü? */
    public static boolean isPanelModel(PlayerEntityModel model) {
        return model != null && (model == panelWideModel || model == panelSlimModel);
    }

    private static synchronized PlayerEntityModel getPanelModel(MinecraftClient mc, boolean slim) {
        if (mc.getLoadedEntityModels() == null) return null;
        if (slim) {
            if (panelSlimModel == null) {
                net.minecraft.client.render.entity.model.EntityModelLayer slimLayer =
                        new net.minecraft.client.render.entity.model.EntityModelLayer(Identifier.ofVanilla("player_slim"), "main");
                panelSlimModel = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(slimLayer), true);
            }
            return panelSlimModel;
        }
        if (panelWideModel == null) {
            panelWideModel = new PlayerEntityModel(mc.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER), false);
        }
        return panelWideModel;
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
                || "brille".contains(q) || "maske".contains(q)
                || "killeffects".contains(q) || "kill effects".contains(q) || "killeffekte".contains(q)
                || "totem".contains(q) || "lightning".contains(q) || "blitz".contains(q)
                || "gravestone".contains(q) || "tombstone".contains(q) || "grabstein".contains(q);
    }

    // Zusätzliche Suchbegriffe (DE/EN) pro Kategorie
    private static String searchKeywords(String category) {
        return switch (category) {
            case "HEAD" -> "hat hut kopf head";
            case "FACE" -> "face gesicht brille maske glasses mask";
            case "PETS" -> "pet pets begleiter";
            case "KILLEFFECTS" -> "kill kills killeffects kill effects killeffekte effekt effect totem lightning bolt grave gravestone tombstone blitz grabstein coffin casket sarg anvil amboss";
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
            case "crown" -> "CROWN";
            case "headphones" -> "HEADPHONES";
            case "viking_helmet" -> "VIKING HELMET";
            case "propeller_cap" -> "PROPELLER CAP";
            case "cat_ears" -> "CAT EARS";
            case "sunglasses" -> "SUNGLASSES";
            case "skull_mask" -> "SKULL MASK";
            default -> id.toUpperCase(Locale.ROOT);
        };
    }

    private static List<CosmeticSection> getActiveSections(String search) {
        List<CosmeticSection> result = new ArrayList<>();
        String q = (search != null) ? search.trim().toLowerCase(Locale.ROOT) : "";

        for (CosmeticSection sec : getAllSections()) {
            // Skins und Emotes nur in ihrer eigenen Kategorie (bzw. bei einer Suche) zeigen, nicht unter "Alle"
            if ("ALL".equalsIgnoreCase(subCategory) && q.isEmpty()
                    && (sec.category.equalsIgnoreCase("SKINS") || sec.category.equalsIgnoreCase("EMOTES")
                    || sec.category.equalsIgnoreCase(KILLEFFECTS_DROPDOWN_ID))) {
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
                result.add(new CosmeticSection(sec.id, sec.title, sec.category, filteredItems));
            }
        }
        return result;
    }

    @Override
    public void layout(BameClientScreen screen, int leftX, int rightX, int columnWidth, ColumnTracker tracker, String search) {
        if (!isVisible(search)) return;

        int startY = Math.max(tracker.leftY, tracker.rightY);
        int curY = startY;

        if (isKillEffectsView()) updateKillSettingsHeight(screen, columnWidth);
        if (isCapeSettingsView()) updateCapeSettingsHeight(screen, columnWidth);
        List<CosmeticSection> activeSections = getActiveSections(search);
        boolean headers = showsSectionHeaders();
        curY += topBannerHeight();

        for (CosmeticSection sec : activeSections) {
            if (headers) {
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
        if (isKillEffectsView()) updateKillSettingsHeight(screen, columnWidth);
        if (isCapeSettingsView()) updateCapeSettingsHeight(screen, columnWidth);
        List<CosmeticSection> activeSections = getActiveSections(search);

        boolean headers = showsSectionHeaders();
        curY += topBannerHeight();
        for (CosmeticSection sec : activeSections) {
            if (headers) {
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
        boolean headers = showsSectionHeaders();
        boolean isAllMode = "ALL".equalsIgnoreCase(subCategory);

        // Update coordinate mappings for scroll-spy and quick jump
        SECTION_Y_MAP.clear();
        ITEM_Y_MAP.clear();
        computeOffsets(activeSections, SECTION_Y_MAP, ITEM_Y_MAP);
        double currentScroll = screen.getScroll();
        if (headers) {
            // Scroll-Spy: letzter Abschnitt, dessen Anfang oben erreicht ist; ein per Seitenleiste angesprungener
            // Abschnitt bleibt markiert, solange nicht gescrollt wurde (auch wenn der Sprung am Ende begrenzt war)
            String detected = null;
            for (CosmeticSection sec : activeSections) {
                String id = sec.id.toUpperCase(Locale.ROOT);
                if (detected == null || currentScroll + 35 >= SECTION_Y_MAP.getOrDefault(id, 0)) detected = id;
            }
            if (pinnedSection != null && Math.abs(currentScroll - pinnedScroll) < 0.5
                    && SECTION_Y_MAP.containsKey(pinnedSection)) {
                detected = pinnedSection;
            } else {
                pinnedSection = null;
            }
            if (detected != null) currentScrolledCategory = detected;
        }

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

        killTestHits.clear();
        if (isKillEffectsView()) {
            updateKillSettingsHeight(screen, columnWidth);
            renderKillSettings(screen, c, leftX, curY, cardsAreaW, mx, my);
            curY += killSettingsHeight;
        }
        capeToggleHits.clear();
        if (isCapeSettingsView()) {
            updateCapeSettingsHeight(screen, columnWidth);
            renderCapeSettings(screen, c, leftX, curY, cardsAreaW, mx, my);
            curY += capeSettingsHeight;
        }

        for (CosmeticSection sec : activeSections) {
            if (headers) {
                // In der Cosmetics-Ansicht (nur bei einer Suche) Emote-Gruppen als "EMOTES • SPORT" kennzeichnen
                String title = (isAllMode && "EMOTES".equalsIgnoreCase(sec.category)) ? "EMOTES • " + sec.title : sec.title;
                renderSectionHeader(screen, c, title, leftX, curY, cardsAreaW);
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

    /** "Test"-Knopf unten rechts auf einer Kill-Effekt-Karte. */
    private static int[] killTestButton(BameClientScreen screen, int x, int y, int w, int h) {
        int bw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText("Test")) + 10;
        int bh = 13;
        return new int[]{x + w - bw - 5, y + h - bh - 4, bw, bh};
    }

    private static String ellipsize(BameClientScreen screen, String s, int maxW) {
        if (screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(s)) <= maxW) return s;
        String cut = s;
        while (cut.length() > 1 && screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(cut + "…")) > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    /** Bricht die Beschreibung in höchstens {@code maxLines} Zeilen um (letzte Zeile mit "…" gekürzt). */
    private static List<String> wrapDescription(BameClientScreen screen, String text, int maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank() || maxLines <= 0) return out;
        String[] words = text.trim().split("\\s+");
        String line = "";
        for (int i = 0; i < words.length; i++) {
            String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
            if (screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(candidate)) <= maxW) {
                line = candidate;
                continue;
            }
            if (out.size() == maxLines - 1) {
                // letzte erlaubte Zeile: Rest anhängen und kürzen
                String rest = String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
                out.add(ellipsize(screen, line.isEmpty() ? rest : line + " " + rest, maxW));
                return out;
            }
            if (!line.isEmpty()) out.add(line);
            line = words[i];
        }
        if (!line.isEmpty()) out.add(line);
        out.replaceAll(s -> ellipsize(screen, s, maxW));
        return out;
    }

    /** Kill-Effekt-Karte: Name, Schloss, animierte Vorschau, Beschreibung, Preis bzw. EQUIPPED und "Test". */
    private void renderKillEffectCard(BameClientScreen screen, DrawContext c, CosmeticItem item,
                                      int x, int y, int w, int h, int mx, int my) {
        com.bame.client.killfx.KillEffectType type = com.bame.client.killfx.KillEffectType.fromId(item.id);
        boolean equipped = item.isEquipped();
        boolean unlocked = item.isUnlocked();
        // Breite links unten (Preis bzw. EQUIPPED): passt "Test" nicht daneben, kommt es eine Zeile höher
        String eq = "EQUIPPED";
        int leftW = 0;
        if (!unlocked) {
            com.bame.client.shop.Wallet.ShopItem shopItem = com.bame.client.shop.Wallet.item(item.id);
            if (shopItem != null && shopItem.price() > 0) {
                leftW = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText("◆ " + shopItem.price())) + 10;
            }
        } else if (equipped) {
            leftW = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(eq)) + 10;
        }
        int[] tb = killTestButton(screen, x, y, w, h);
        boolean compact = leftW > 0 && 5 + leftW + 6 + tb[2] + 5 > w;
        if (compact) tb[1] -= 15;
        boolean hoverTest = screen.inside(mx, my, tb[0], tb[1], tb[2], tb[3]);
        boolean hover = screen.inside(mx, my, x, y, w, h) && !hoverTest;

        int bg = equipped ? 0xFF131724 : (hover ? 0xFF161A28 : (unlocked ? 0xFF0E111A : 0xFF0A0D15));
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, bg, 6.0f);
        int border = equipped ? GuiTheme.accent() : (hover ? (unlocked ? 0xFF3E4860 : 0xFF2A344A) : 0xFF1E2436);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, border, 6.0f);
        if (equipped) c.fill(x + 6, y + 1, x + w - 6, y + 2, GuiTheme.accent());

        int titleColor = equipped ? 0xFFFFFFFF : (hover ? 0xFFF1F5F9 : (unlocked ? 0xFFCBD5E1 : 0xFF64748B));
        screen.text(c, ellipsize(screen, item.name, w - 26), x + 6, y + 6, titleColor);
        int lockX = x + w - 13 - 5;
        CustomGuiUtils.drawLockIcon(c, lockX + 2, y + 6, !unlocked, unlocked ? 0xFF22C55E : 0xFFEF4444);

        // Beschreibung: bis zu zwei Zeilen (schmale Karte: eine Zeile über dem Testen-Knopf)
        int descColor = unlocked ? 0xFF94A3B8 : 0xFF64748B;
        int bottomRowsTop = compact ? y + h - 31 : y + h - 18;
        List<String> lines = wrapDescription(screen, item.desc, w - 12, compact ? 1 : 2);
        int descTop = bottomRowsTop - 2 - lines.size() * 10;
        for (int i = 0; i < lines.size(); i++) {
            screen.text(c, lines.get(i), x + 6, descTop + i * 10, descColor);
        }

        // Animierte Vorschau
        int pvTop = y + 18;
        int pvBottom = descTop - 2;
        if (pvBottom - pvTop > 16) {
            c.enableScissor(x + 2, pvTop, x + w - 2, pvBottom);
            com.bame.client.killfx.KillEffectPreview.draw(c, type, x + 4, pvTop, w - 8, pvBottom - pvTop,
                    System.currentTimeMillis());
            c.disableScissor();
        }

        // Unten links: Preis (gesperrt) bzw. EQUIPPED
        if (!unlocked) {
            com.bame.client.gui.ShopOverlay.renderPriceBadge(screen, c, item.id, x, y, w, h);
        } else if (equipped) {
            int ew = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(eq)) + 10;
            int ex = x + 5, ey = y + h - 16;
            CustomGuiUtils.fillUltraRounded(c, ex, ey, ew, 12, GuiTheme.alpha(GuiTheme.accent(), 60), 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, ex, ey, ew, 12, GuiTheme.accent(), 3.0f);
            screen.text(c, eq, ex + 5, ey + 2, 0xFFFFFFFF);
        }

        // Unten rechts: Test (auch für nicht gekaufte Effekte)
        boolean ready = com.bame.client.killfx.KillEffects.testReady();
        int tbBg = hoverTest && ready ? GuiTheme.alpha(GuiTheme.accent(), 90) : 0xFF181C26;
        int tbBorder = hoverTest && ready ? GuiTheme.accent() : 0xFF2A344A;
        CustomGuiUtils.fillUltraRounded(c, tb[0], tb[1], tb[2], tb[3], tbBg, 3.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, tb[0], tb[1], tb[2], tb[3], tbBorder, 3.0f);
        screen.text(c, "Test", tb[0] + 5, tb[1] + 3, ready ? (hoverTest ? 0xFFFFFFFF : 0xFFCBD5E1) : 0xFF4B5563);
        if (type != null) killTestHits.add(new KillTestHit(type, tb[0], tb[1], tb[2], tb[3]));
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
        if (KILLEFFECTS_DROPDOWN_ID.equalsIgnoreCase(item.category)) {
            renderKillEffectCard(screen, c, item, x, y, w, h, mx, my);
            return;
        }
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

        // Jeton-Shop: gesperrte Items zeigen ihren Preis (besessene bzw. gratis Items nicht)
        if (!unlocked && !"SKINS".equalsIgnoreCase(item.category)) {
            com.bame.client.gui.ShopOverlay.renderPriceBadge(screen, c, item.id, x, y, w, h);
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

        List<EquippedPreviewItem> equippedItems = new ArrayList<>();
        if (curW != null && !"none".equalsIgnoreCase(curW)) {
            equippedItems.add(new EquippedPreviewItem(curW, "angel".equalsIgnoreCase(curW) ? "GOLD WINGS" : "DRAGON WINGS", "WINGS"));
        }
        if (curH != null && !"none".equalsIgnoreCase(curH)) {
            equippedItems.add(new EquippedPreviewItem(curH, equippedLabel(curH), "HEAD"));
        }
        if (curF != null && !"none".equalsIgnoreCase(curF)) {
            equippedItems.add(new EquippedPreviewItem(curF, equippedLabel(curF), "FACE"));
        }
        if (CosmeticsConfig.accessories != null) {
            for (String acc : CosmeticsConfig.accessories) {
                String cat = ("baby_dragon".equalsIgnoreCase(acc) || "jelly_slime".equalsIgnoreCase(acc)) ? "PETS" : "ACCESSORIES";
                equippedItems.add(new EquippedPreviewItem(acc, equippedAccLabel(acc), cat));
            }
        }

        equippedBadgeClicks.clear();
        int listY = py + 10;
        if (equippedItems.isEmpty()) {
            String noneText = "NO COSMETICS";
            int nw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(noneText));
            screen.text(c, noneText, px + pw - nw - 12, listY, 0xFF475569);
        } else {
            for (EquippedPreviewItem eqItem : equippedItems) {
                int nw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(eqItem.label()));
                int badgeW = nw + 10;
                int badgeH = 15;
                int badgeX = px + pw - badgeW - 10;
                int badgeY = listY;
                boolean hoverBadge = screen.inside(mx, my, badgeX, badgeY, badgeW, badgeH);

                int bgCol = hoverBadge ? GuiTheme.alpha(GuiTheme.accent(), 60) : 0x50141926;
                int borderCol = hoverBadge ? GuiTheme.accent() : 0x303E4C66;
                CustomGuiUtils.fillUltraRounded(c, badgeX, badgeY, badgeW, badgeH, bgCol, 3.5f);
                CustomGuiUtils.drawUltraRoundedOutline(c, badgeX, badgeY, badgeW, badgeH, borderCol, 3.5f);
                screen.text(c, eqItem.label(), badgeX + 5, badgeY + 4, hoverBadge ? 0xFFFFFFFF : 0xFFE2E8F0);

                equippedBadgeClicks.add(new EquippedBadgeClick(eqItem.id(), badgeX, badgeY, badgeW, badgeH));
                listY += 18;
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
        PlayerEntityModel model = getPanelModel(mc, slim);
        Identifier skinTexture = (textures != null && textures.body() != null)
                ? textures.body().texturePath()
                : DefaultSkinHelper.getTexture();

        if (model != null) {
            float baseScale = (0.74f * (float) skinH / 2.125f) * previewZoom;
            float dynamicPivotY = -1.0625f + (previewZoom - DEFAULT_ZOOM) * 0.35f;
            c.addPlayerSkin(model, skinTexture, baseScale, previewPitch, previewYaw, dynamicPivotY,
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

            // Equipped items click in top right: Jump directly to clicked cosmetic card!
            for (EquippedBadgeClick badge : equippedBadgeClicks) {
                if (screen.inside(mx, my, badge.x, badge.y, badge.w, badge.h)) {
                    scrollToItem(screen, badge.itemId);
                    ClientSoundManager.playClick();
                    return true;
                }
            }

            // Click inside preview panel to begin smooth drag rotation
            if (screen.inside(mx, my, previewX, previewY, previewW, previewH)) {
                previewDragging = true;
                return true;
            }
        }

        // Kill-Effekte: Schalter oben in der Ansicht (Positionen vom letzten Frame)
        if (isKillEffectsView()) {
            for (KillToggleHit hit : killToggleHits) {
                if (screen.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.toggle().flip().run();
                    CosmeticsConfig.save();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
        }
        // Caeser-Capes: Schalter oben in der Cosmetics-Gesamtansicht (Positionen vom letzten Frame)
        if (isCapeSettingsView()) {
            for (KillToggleHit hit : capeToggleHits) {
                if (screen.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.toggle().flip().run();
                    CosmeticsConfig.save();
                    ClientSoundManager.playClick();
                    return true;
                }
            }
        }
        // Kill-Effekte: "Test" (auch für nicht gekaufte Effekte; schließt das Menü)
        for (KillTestHit hit : killTestHits) {
            if (screen.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) {
                ClientSoundManager.playClick();
                com.bame.client.killfx.KillEffects.test(hit.type());
                return true;
            }
        }

        // 2. Item Cards Click
        int cardStartY = startY;
        int cardsAreaW = showPreview ? (totalW - previewW - 14) : totalW;
        int colW = (cardsAreaW - (COLS - 1) * CARD_GAP) / COLS;

        List<CosmeticSection> activeSections = getActiveSections(search);
        int curY = cardStartY;
        boolean headers = showsSectionHeaders();
        curY += topBannerHeight();

        for (CosmeticSection sec : activeSections) {
            if (headers) {
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
                    if (KILLEFFECTS_DROPDOWN_ID.equalsIgnoreCase(item.category)) {
                        // Gesperrt: Kauf-Popup des Jeton-Shops; besessen: ausrüsten bzw. ablegen
                        if (!item.isUnlocked()) com.bame.client.gui.ShopOverlay.openBuy(item);
                        else item.toggleEquipped();
                        ClientSoundManager.playClick();
                        return true;
                    }
                    if (!item.isUnlocked()) {
                        // Jeton-Shop: Vorschau geht immer, dazu die Kauf-Bestätigung
                        item.selectForPreview();
                        com.bame.client.gui.ShopOverlay.openBuy(item);
                        ClientSoundManager.playClick();
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
            previewYaw += (float) deltaX * 1.0f;
            previewPitch = Math.clamp(previewPitch - (float) deltaY * 1.0f, -50.0f, 50.0f);
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
                float zoomDelta = (float) verticalAmount * 0.05f;
                previewZoom = Math.clamp(previewZoom + zoomDelta, 0.50f, 1.30f);
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
