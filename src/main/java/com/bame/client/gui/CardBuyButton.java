package com.bame.client.gui;

import com.bame.client.gui.tab.CosmeticsTab;
import com.bame.client.shop.Wallet;
import com.bame.client.sound.ClientSoundManager;
import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Kauf-Button auf den Karten nicht gekaufter Items im Cosmetics-Menü (Cosmetics, Emotes, Kill-Effekte): Pille mit
 * Jeton und Preis unten mittig, Geometrie aus {@link BuyButtonLayout}. Ein Klick öffnet nur die vorhandene
 * Kauf-Bestätigung ({@link ShopOverlay#openBuy}), damit nichts aus Versehen gekauft wird; er löst die Karte nicht aus.
 * <ul>
 *   <li>Nur wenn das Item nicht besessen ist, der Katalog einen Preis kennt und der Shop erreichbar ist (sonst bleibt
 *       nur das Schloss).</li>
 *   <li>Zu wenig Jetons: gedimmt, Preis rot, Tooltip „Dir fehlen ◆ X Jetons“. Ein Klick öffnet trotzdem die
 *       Bestätigung (dort steht derselbe Hinweis, „Kaufen“ ist ausgegraut) – wie bisher ein Klick auf die Karte.</li>
 *   <li>Kauf läuft: drei pulsierende Punkte statt Jeton und Preis; Klicks werden geschluckt.</li>
 * </ul>
 * Preis-Texte, Breiten und Platzierung liegen pro Item im Zwischenspeicher und werden nur neu berechnet, wenn sich
 * Wallet-Stand, Spielsprache oder Kartengröße ändern (keine Allokationen pro Frame für unveränderte Karten).
 */
public final class CardBuyButton {
    private static final int GOLD = ShopOverlay.GOLD;
    private static final int GOLD_DARK = ShopOverlay.GOLD_DARK;

    // Farben: normal, Hover, zu wenig Jetons
    private static final int BG = 0xFF1A160E;
    private static final int BG_HOVER = 0xFF2D2513;
    private static final int TEXT = 0xFFFDE68A;
    private static final int TEXT_HOVER = 0xFFFFFFFF;
    private static final int POOR_BG = 0xFF121319;
    private static final int POOR_BG_HOVER = 0xFF1B1C24;
    private static final int POOR_BORDER = 0xFF3A3327;
    private static final int POOR_BORDER_HOVER = 0xFF5C4B2B;
    private static final int POOR_TEXT = 0xFFF87171;
    private static final int POOR_TEXT_HOVER = 0xFFFCA5A5;
    private static final int POOR_COIN_SHADE = 0x990B0D12;

    /** Klick-Feedback: so lange leuchtet die Pille nach einem Klick auf. */
    private static final long PRESS_FLASH_NS = 260_000_000L;

    /** Zwischenspeicher pro Item-ID (Karten werden jeden Frame neu erzeugt, die IDs bleiben). */
    private static final class Entry {
        Wallet.Snapshot snapshot;
        String language;
        boolean purchasable;
        int price = -1;
        String fullLabel;
        String compactLabel;
        int fullW;
        int compactW;
        boolean insufficient;
        String missingText;
        int missingW;
        int layoutW = -1;
        int layoutH = -1;
        BuyButtonLayout.Placement placement;
    }

    private static final Map<String, Entry> CACHE = new HashMap<>();

    // Tooltip des in diesem Frame gehoverten Buttons; über allem gezeichnet (ShopOverlay.renderOverlay)
    private static String tooltip;
    private static int tooltipW;

    private static String pressedId;
    private static long pressedAt;

    private CardBuyButton() {}

    /** Hover-Zustand verwerfen (Anfang jedes Frames der Cosmetics-Ansicht und nach dem Overlay). */
    public static void resetHover() {
        tooltip = null;
    }

    /**
     * Lage des Buttons auf dieser Karte oder {@code null}, wenn sie keinen hat (besessen, Skin, kein Preis im Katalog,
     * Shop nicht erreichbar oder Karte zu schmal).
     */
    public static BuyButtonLayout.Placement placement(BameClientScreen screen, CosmeticsTab.CosmeticItem item,
                                                      boolean unlocked, int cardW, int cardH) {
        if (unlocked || item == null || item.id == null || "SKINS".equalsIgnoreCase(item.category)) return null;
        if (!Wallet.isOnline()) return null; // Shop/Katalog nicht erreichbar: kein Button, das Schloss bleibt
        Entry e = entry(screen, item.id);
        if (!e.purchasable) return null;
        if (e.layoutW != cardW || e.layoutH != cardH) {
            e.placement = BuyButtonLayout.place(cardW, cardH, e.fullW, e.compactW);
            e.layoutW = cardW;
            e.layoutH = cardH;
        }
        return e.placement;
    }

    private static Entry entry(BameClientScreen screen, String id) {
        Entry e = CACHE.get(id);
        if (e == null) {
            e = new Entry();
            CACHE.put(id, e);
        }
        Wallet.Snapshot snapshot = Wallet.get();
        String language = ShopOverlay.currentLanguage();
        if (e.snapshot != snapshot || !Objects.equals(e.language, language)) {
            refresh(screen, e, id, snapshot, language);
        }
        return e;
    }

    private static void refresh(BameClientScreen screen, Entry e, String id, Wallet.Snapshot snapshot, String language) {
        boolean languageChanged = !Objects.equals(e.language, language);
        e.snapshot = snapshot;
        e.language = language;
        Wallet.ShopItem shopItem = Wallet.item(id);
        e.purchasable = shopItem != null && shopItem.active() && shopItem.price() > 0;
        if (!e.purchasable) {
            e.insufficient = false;
            e.missingText = null;
            return;
        }
        int price = shopItem.price();
        if (price != e.price || languageChanged || e.fullLabel == null) {
            BuyButtonLayout.Separators sep = ShopOverlay.separators();
            e.price = price;
            e.fullLabel = BuyButtonLayout.formatGrouped(price, sep.grouping());
            e.compactLabel = BuyButtonLayout.formatCompact(price, sep.decimal());
            e.fullW = width(screen, e.fullLabel);
            e.compactW = width(screen, e.compactLabel);
            e.layoutW = -1; // neu platzieren
        }
        Integer jetons = snapshot.jetons();
        e.insufficient = jetons != null && jetons < price;
        e.missingText = e.insufficient ? ShopOverlay.missingJetonsText(price - jetons) : null;
        e.missingW = e.missingText != null ? width(screen, e.missingText) : 0;
    }

    private static int width(BameClientScreen screen, String s) {
        return screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(s));
    }

    /** Liegt die Maus auf dem Button (nur im sichtbaren Inhaltsbereich, nicht unter einem Dialog)? */
    public static boolean isHovered(BameClientScreen screen, BuyButtonLayout.Placement p, int cardX, int cardY,
                                    int mx, int my) {
        if (p == null || ShopOverlay.isModalOpen() || CosmeticsTab.customSkinModalOpen) return false;
        return inContentArea(screen, my) && p.contains(mx - cardX, my - cardY);
    }

    /** Sichtbarer Inhaltsbereich (= Scissor der Karten): verdeckte Buttons unter der Kopfleiste reagieren nicht. */
    private static boolean inContentArea(BameClientScreen screen, double my) {
        return my >= screen.getCy() - 2 && my < screen.getCy() + screen.getCh();
    }

    /** Zeichnet den Button (nach der Vorschau, damit er oben liegt). */
    public static void render(BameClientScreen screen, DrawContext c, CosmeticsTab.CosmeticItem item,
                              BuyButtonLayout.Placement p, int cardX, int cardY, boolean hovered) {
        if (p == null) return;
        Entry e = CACHE.get(item.id);
        if (e == null || !e.purchasable) return;
        int x = cardX + p.x();
        int y = cardY + p.y();
        int w = p.w();
        int h = p.h();
        float r = h / 2.0f;
        boolean pending = ShopOverlay.isPurchasePending(item.id);
        boolean poor = e.insufficient && !pending;
        boolean lit = hovered && !poor && !pending;

        int bg;
        int border;
        int text;
        if (poor) {
            bg = hovered ? POOR_BG_HOVER : POOR_BG;
            border = hovered ? POOR_BORDER_HOVER : POOR_BORDER;
            text = hovered ? POOR_TEXT_HOVER : POOR_TEXT;
        } else {
            bg = lit ? BG_HOVER : BG;
            border = lit ? GOLD : GOLD_DARK;
            text = lit ? TEXT_HOVER : TEXT;
        }
        float flash = 0.0f;
        if (item.id.equals(pressedId)) {
            flash = Math.clamp(1.0f - (System.nanoTime() - pressedAt) / (float) PRESS_FLASH_NS, 0.0f, 1.0f);
        }
        if (flash > 0.0f) {
            bg = GuiTheme.blend(bg, GOLD, 0.45f * flash);
            border = GuiTheme.blend(border, 0xFFFFF1B8, flash);
        }

        // Leichtes Leuchten beim Hovern (ragt GLOW px über die Pille hinaus, bleibt laut Layout in der Karte)
        if (lit || flash > 0.0f) {
            CustomGuiUtils.drawUltraRoundedOutline(c, x - 2, y - 2, w + 4, h + 4, GuiTheme.alpha(GOLD, 0x22), r + 2);
            CustomGuiUtils.drawUltraRoundedOutline(c, x - 1, y - 1, w + 2, h + 2, GuiTheme.alpha(GOLD, 0x5A), r + 1);
        }
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, bg, r);
        if (!poor) {
            // dezenter Glanz an der Oberkante
            int ir = (int) r;
            c.fill(x + ir, y + 1, x + w - ir, y + 2, lit ? 0x30FFFFFF : 0x16FFFFFF);
        }
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, border, r);

        if (pending) {
            drawDots(c, x + w / 2, y + h / 2, System.currentTimeMillis());
            return;
        }
        if (p.coinSize() > 0) {
            int cx = cardX + p.coinX();
            int cy = cardY + p.coinY();
            ShopOverlay.drawCoin(c, cx, cy, p.coinSize());
            if (poor) CustomGuiUtils.fillUltraRounded(c, cx, cy, p.coinSize(), p.coinSize(), POOR_COIN_SHADE, p.coinSize() / 2.0f);
        }
        screen.text(c, p.compactLabel() ? e.compactLabel : e.fullLabel, cardX + p.textX(), cardY + p.textY(), text);

        if (hovered && poor && e.missingText != null) {
            tooltip = e.missingText;
            tooltipW = e.missingW;
        }
    }

    /** Kauf läuft: drei nacheinander pulsierende Punkte. */
    private static void drawDots(DrawContext c, int cx, int cy, long now) {
        for (int i = 0; i < 3; i++) {
            float k = (float) (0.5 + 0.5 * Math.sin(now / 140.0 - i * 0.9));
            int dx = cx - 5 + i * 4;
            int dy = cy - 1 - (k > 0.8f ? 1 : 0);
            c.fill(dx, dy, dx + 2, dy + 2, GuiTheme.alpha(GOLD, 90 + (int) (165 * k)));
        }
    }

    /**
     * Klick auf eine Karte: Trifft er den Button, öffnet sich die Kauf-Bestätigung (läuft der Kauf schon, passiert
     * nichts) und die Karte wird nicht ausgelöst. {@code true} = Klick verbraucht.
     */
    public static boolean handleClick(BameClientScreen screen, CosmeticsTab.CosmeticItem item,
                                      int cardX, int cardY, int cardW, int cardH, double mx, double my) {
        if (item == null || ShopOverlay.isModalOpen() || item.isUnlocked()) return false;
        BuyButtonLayout.Placement p = placement(screen, item, false, cardW, cardH);
        if (p == null || !inContentArea(screen, my)
                || BuyButtonLayout.hitTarget(p, mx - cardX, my - cardY, cardW, cardH) != BuyButtonLayout.HIT_BUTTON) {
            return false;
        }
        if (!ShopOverlay.isPurchasePending(item.id)) {
            pressedId = item.id;
            pressedAt = System.nanoTime();
            ShopOverlay.openBuy(item);
            ClientSoundManager.playClick();
        }
        return true;
    }

    /** Tooltip „Dir fehlen ◆ X Jetons“ neben der Maus (über allem, kein Dialog offen). */
    public static void renderTooltip(BameClientScreen screen, DrawContext c, int mx, int my) {
        if (tooltip == null) return;
        int w = tooltipW + 12;
        int h = 16;
        int x = Math.max(4, Math.min(mx + 8, screen.width - w - 4));
        int y = my + 12;
        if (y + h > screen.height - 4) y = my - h - 6;
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, 0xF0131722, 3.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, 0xFF3A3424, 3.0f);
        screen.text(c, tooltip, x + 6, y + 4, POOR_TEXT);
    }
}
