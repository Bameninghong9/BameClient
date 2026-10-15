package com.bame.client.gui;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.cosmetics.CosmeticsConfig;
import com.bame.client.emotes.EmoteUnlockManager;
import com.bame.client.gui.tab.CosmeticsTab;
import com.bame.client.notification.Notification;
import com.bame.client.notification.NotificationManager;
import com.bame.client.shop.CaeserApi;
import com.bame.client.shop.CaeserAuth;
import com.bame.client.shop.Wallet;
import com.bame.client.sound.ClientSoundManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;


/**
 * Jeton-Shop in der Cosmetics-Ansicht: Kontostand-Chip und Kauf-Bestätigung.
 * Gezeichnet im Caeser-Design (CustomGuiUtils/GuiTheme), alle Netzwerkaufrufe laufen über {@link Wallet} asynchron.
 */
public final class ShopOverlay {
    public static final int GOLD = 0xFFF5B301;
    private static final int GOLD_DARK = 0xFFB7791F;
    private static final int GOLD_TEXT = 0xFFFACC15;

    private static final long OPEN_REFRESH_MS = 60_000L;

    // Chip
    private static int chipX, chipY, chipW, chipH;
    private static boolean chipVisible;

    // Kauf-Dialog
    private static CosmeticsTab.CosmeticItem buyItem;
    private static boolean buyBusy;
    private static String buyError;

    // Hinweis "gehören dir noch nicht" einmal pro Öffnen
    private static boolean hintPending;
    private static long lastOpenRefreshAt;

    private ShopOverlay() {}

    // ==========================================
    // Lebenszyklus
    // ==========================================

    /** Die Cosmetics-Ansicht wurde gerade geöffnet. */
    public static void onCosmeticsViewOpened() {
        lastOpenRefreshAt = System.currentTimeMillis();
        Wallet.refresh();
        hintPending = true;
        checkHint();
    }

    /** Jeden Frame, solange die Cosmetics-Ansicht offen ist: alle 60 s neu laden, Hinweis nach dem ersten Laden. */
    public static void tickOpen() {
        long now = System.currentTimeMillis();
        if (now - lastOpenRefreshAt >= OPEN_REFRESH_MS) {
            lastOpenRefreshAt = now;
            Wallet.refresh();
        }
        if (hintPending) checkHint();
    }

    private static void checkHint() {
        if (!Wallet.hasData()) return; // ohne Daten wäre alles "gesperrt": dann nicht falsch warnen
        hintPending = false;
        if (hasEquippedUnowned()) {
            NotificationManager.add(new Notification("Hinweis",
                    "Einige ausgerüstete Cosmetics gehören dir noch nicht", Notification.Type.INFO, 4500));
        }
    }

    /** Ausgerüstet (Config, auch vom Launcher gesetzt), aber nicht besessen? */
    public static boolean hasEquippedUnowned() {
        if (!EmoteUnlockManager.isCosmeticUnlocked("WINGS", CosmeticsConfig.wings)) return true;
        if (!EmoteUnlockManager.isCosmeticUnlocked("HEAD", CosmeticsConfig.head)) return true;
        if (!EmoteUnlockManager.isCosmeticUnlocked("FACE", CosmeticsConfig.face)) return true;
        if (!EmoteUnlockManager.isCosmeticUnlocked("BACK", CosmeticsConfig.back)) return true;
        for (String acc : CosmeticsConfig.accessories) {
            if (!EmoteUnlockManager.isCosmeticUnlocked("ACCESSORIES", acc)) return true;
        }
        if (!EmoteUnlockManager.isCosmeticUnlocked("KILLEFFECTS", CosmeticsConfig.killEffect)) return true;
        return false;
    }

    public static void close() {
        buyItem = null;
        buyBusy = false;
        buyError = null;
    }

    public static boolean isModalOpen() {
        return buyItem != null;
    }

    // ==========================================
    // Kontostand-Chip
    // ==========================================

    /**
     * Zeichnet den Chip rechtsbündig bis {@code rightX}. Gibt die linke Kante (inkl. ADMIN-Hinweis) zurück.
     */
    public static int renderChip(BameClientScreen screen, DrawContext c, int rightX, int y, int h, int mx, int my) {
        Wallet.Snapshot s = Wallet.get();
        String amount;
        if (Wallet.isOnline()) {
            amount = String.valueOf(s.jetons());
        } else if (Wallet.status() == Wallet.Status.LOADING && CaeserApi.isConfigured()) {
            amount = "…";
        } else {
            amount = "–";
        }
        int tw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(amount));
        int w = 10 + 11 + 5 + tw + 10;
        int x = rightX - w;
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, 0xFF141722, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, 0xFF3A3424, 4.0f);
        drawCoin(c, x + 10, y + (h - 11) / 2, 11);
        screen.text(c, amount, x + 10 + 11 + 5, y + (h - 8) / 2, Wallet.isOnline() ? 0xFFFFFFFF : 0xFF94A3B8);

        chipX = x;
        chipY = y;
        chipW = w;
        chipH = h;
        chipVisible = true;

        int left = x;
        if (Wallet.isOnline() && s.isAdmin()) {
            String admin = "ADMIN";
            int aw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(admin)) + 10;
            int ax = x - aw - 6;
            int ah = 13;
            int ay = y + (h - ah) / 2;
            CustomGuiUtils.fillUltraRounded(c, ax, ay, aw, ah, GuiTheme.alpha(GuiTheme.accent(), 50), 3.0f);
            CustomGuiUtils.drawUltraRoundedOutline(c, ax, ay, aw, ah, GuiTheme.accent(), 3.0f);
            screen.text(c, admin, ax + 5, ay + 3, 0xFFFFFFFF);
            left = ax;
        }
        return left;
    }

    /** Kleine gezeichnete Jeton-Münze. */
    public static void drawCoin(DrawContext c, int x, int y, int size) {
        float r = size / 2.0f;
        CustomGuiUtils.fillUltraRounded(c, x, y, size, size, GOLD_DARK, r);
        CustomGuiUtils.fillUltraRounded(c, x + 1, y + 1, size - 2, size - 2, GOLD, r - 1);
        int inner = Math.max(3, size - 6);
        CustomGuiUtils.drawUltraRoundedOutline(c, x + 3, y + 3, inner, inner, GOLD_DARK, inner / 2.0f);
        c.fill(x + size / 2 - 1, y + 2, x + size / 2, y + 3, 0xFFFFF3B0);
    }

    // ==========================================
    // Kauf
    // ==========================================

    /** Öffnet die Kauf-Bestätigung für ein gesperrtes Item (die Vorschau macht der Aufrufer). */
    public static void openBuy(CosmeticsTab.CosmeticItem item) {
        if (item == null) return;
        if (buyItem != null && buyBusy) return;
        buyItem = item;
        buyBusy = false;
        buyError = null;
    }

    private record BuyLayout(int x, int y, int w, int h, int cancelX, int buyX, int btnY, int btnW, int btnH) {}

    private static BuyLayout buyLayout(BameClientScreen screen, String title) {
        int tw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(title));
        int w = Math.max(260, tw + 40);
        int h = 112;
        int x = (screen.width - w) / 2;
        int y = (screen.height - h) / 2;
        int btnH = 22;
        int btnW = (w - 32 - 10) / 2;
        int btnY = y + h - 34;
        int cancelX = x + 16;
        int buyX = cancelX + btnW + 10;
        return new BuyLayout(x, y, w, h, cancelX, buyX, btnY, btnW, btnH);
    }

    private static String buyTitle(CosmeticsTab.CosmeticItem item, Wallet.ShopItem shopItem) {
        String name = displayName(item, shopItem);
        return shopItem != null ? name + " für ◆ " + shopItem.price() + " kaufen?" : name + " kaufen?";
    }

    private static String displayName(CosmeticsTab.CosmeticItem item, Wallet.ShopItem shopItem) {
        if (item != null && item.name != null && !item.name.isBlank()) return item.name;
        return shopItem != null ? shopItem.name() : "Item";
    }

    /** Kann gekauft werden? Sonst {@code null} = ja, sonst Grund. */
    private static String buyBlocker(Wallet.ShopItem shopItem) {
        if (!Wallet.isOnline()) return "Shop gerade nicht erreichbar";
        if (shopItem == null) return "Dieses Item gibt es im Shop nicht.";
        if (!shopItem.active()) return "Dieses Item ist gerade nicht erhältlich.";
        Integer jetons = Wallet.get().jetons();
        if (jetons == null) return "Shop gerade nicht erreichbar";
        if (jetons < shopItem.price()) return "Dir fehlen ◆ " + (shopItem.price() - jetons) + " Jetons";
        return null;
    }

    private static void renderBuy(BameClientScreen screen, DrawContext c, int mx, int my) {
        CosmeticsTab.CosmeticItem item = buyItem;
        Wallet.ShopItem shopItem = Wallet.item(item.id);
        String title = buyTitle(item, shopItem);
        BuyLayout L = buyLayout(screen, title);

        c.fill(0, 0, screen.width, screen.height, 0xA8000000);
        CustomGuiUtils.fillUltraRounded(c, L.x, L.y, L.w, L.h, 0xFA0F131C, 7.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, L.x, L.y, L.w, L.h, 0xFF283448, 7.0f);
        c.fill(L.x + 12, L.y + 1, L.x + L.w - 12, L.y + 2, GOLD);

        int tw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(title));
        screen.text(c, title, L.x + (L.w - tw) / 2, L.y + 16, 0xFFFFFFFF);

        String blocker = buyBlocker(shopItem);
        String line;
        int lineColor;
        if (buyError != null) {
            line = buyError;
            lineColor = 0xFFF87171;
        } else if (blocker == null) {
            int rest = Wallet.get().jetons() - shopItem.price();
            line = "Kontostand danach: ◆ " + rest;
            lineColor = 0xFF94A3B8;
        } else {
            line = blocker;
            lineColor = blocker.startsWith("Dir fehlen") ? 0xFFF87171 : 0xFF94A3B8;
        }
        int lw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(line));
        screen.text(c, line, L.x + (L.w - lw) / 2, L.y + 36, lineColor);

        // Abbrechen
        boolean hoverCancel = screen.inside(mx, my, L.cancelX, L.btnY, L.btnW, L.btnH);
        CustomGuiUtils.fillUltraRounded(c, L.cancelX, L.btnY, L.btnW, L.btnH, hoverCancel ? 0xFF242A38 : 0xFF141722, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, L.cancelX, L.btnY, L.btnW, L.btnH, hoverCancel ? 0xFF475569 : 0xFF262E3E, 4.0f);
        String cancel = "Abbrechen";
        int cw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(cancel));
        screen.text(c, cancel, L.cancelX + (L.btnW - cw) / 2, L.btnY + 7, hoverCancel ? 0xFFFFFFFF : 0xFF94A3B8);

        // Kaufen (ausgegraut, wenn nicht möglich)
        boolean enabled = blocker == null && !buyBusy;
        boolean hoverBuy = enabled && screen.inside(mx, my, L.buyX, L.btnY, L.btnW, L.btnH);
        int bg = buyBusy ? GuiTheme.alpha(GuiTheme.accent(), 85)
                : (enabled ? (hoverBuy ? GuiTheme.accent() : GuiTheme.alpha(GuiTheme.accent(), 85)) : 0xFF1A1D26);
        int border = enabled || buyBusy ? GuiTheme.accent() : 0xFF262E3E;
        CustomGuiUtils.fillUltraRounded(c, L.buyX, L.btnY, L.btnW, L.btnH, bg, 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, L.buyX, L.btnY, L.btnW, L.btnH, border, 4.0f);
        if (buyBusy) {
            drawSpinner(c, L.buyX + L.btnW / 2, L.btnY + L.btnH / 2);
        } else {
            String buy = "Kaufen";
            int bw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(buy));
            screen.text(c, buy, L.buyX + (L.btnW - bw) / 2, L.btnY + 7, enabled ? 0xFFFFFFFF : 0xFF4B5563);
        }
    }

    /** Lade-Kreisel aus 8 Punkten. */
    private static void drawSpinner(DrawContext c, int cx, int cy) {
        long t = System.currentTimeMillis();
        int active = (int) ((t / 90L) % 8);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8.0;
            int px = cx + (int) Math.round(Math.cos(a) * 5) - 1;
            int py = cy + (int) Math.round(Math.sin(a) * 5) - 1;
            int age = (i - active + 8) % 8;
            int alpha = 255 - age * 28;
            c.fill(px, py, px + 2, py + 2, (Math.max(40, alpha) << 24) | 0xFFFFFF);
        }
    }

    private static boolean handleBuyClick(BameClientScreen screen, double mx, double my) {
        CosmeticsTab.CosmeticItem item = buyItem;
        Wallet.ShopItem shopItem = Wallet.item(item.id);
        BuyLayout L = buyLayout(screen, buyTitle(item, shopItem));

        if (screen.inside(mx, my, L.cancelX, L.btnY, L.btnW, L.btnH) || !screen.inside(mx, my, L.x, L.y, L.w, L.h)) {
            buyItem = null;
            buyError = null;
            ClientSoundManager.playClick();
            return true;
        }
        if (screen.inside(mx, my, L.buyX, L.btnY, L.btnW, L.btnH)) {
            if (buyBusy || buyBlocker(shopItem) != null) return true; // Doppelklicks bzw. ausgegraut ignorieren
            buyBusy = true;
            buyError = null;
            ClientSoundManager.playClick();
            final CosmeticsTab.CosmeticItem target = item;
            final String name = displayName(item, shopItem);
            Wallet.buy(item.id, result -> {
                if (buyItem == target) buyBusy = false;
                if (result.success()) {
                    if (buyItem == target) buyItem = null;
                    NotificationManager.add(new Notification("Jeton-Shop", name + " gekauft!", Notification.Type.ENABLED, 3000));
                    CaeserPlayerSyncManager.markStateDirty();
                } else if (buyItem == target) {
                    String msg = result.message() != null ? result.message() : CaeserApi.MSG_UNREACHABLE;
                    if (result.status() == 402 && result.missing() != null) {
                        msg = msg + " (Dir fehlen ◆ " + result.missing() + " Jetons)";
                    }
                    buyError = msg;
                } else {
                    NotificationManager.add(new Notification("Jeton-Shop",
                            result.message() != null ? result.message() : CaeserApi.MSG_UNREACHABLE, Notification.Type.DISABLED, 3000));
                }
            });
            return true;
        }
        return true;
    }

    // ==========================================
    // Overlay (über allem) + Eingaben
    // ==========================================

    /** Nach dem restlichen Inhalt zeichnen: Dialoge bzw. Tooltip des Chips. */
    public static void renderOverlay(BameClientScreen screen, DrawContext c, int mx, int my) {
        if (buyItem != null) {
            renderBuy(screen, c, mx, my);
        } else if (chipVisible && !Wallet.isOnline() && mx >= chipX && mx < chipX + chipW && my >= chipY && my < chipY + chipH) {
            renderTooltip(screen, c, mx, my);
        }
    }

    private static void renderTooltip(BameClientScreen screen, DrawContext c, int mx, int my) {
        String line1 = "Shop gerade nicht erreichbar";
        String line2 = CaeserApi.isConfigured() ? (CaeserAuth.lastError() != null ? CaeserAuth.lastError() : Wallet.lastError()) : null;
        if (line2 != null && line2.equals(line1)) line2 = null;
        int w1 = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(line1));
        int w2 = line2 != null ? screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(line2)) : 0;
        int w = Math.max(w1, w2) + 12;
        int h = line2 != null ? 28 : 16;
        int x = Math.min(mx + 8, screen.width - w - 4);
        int y = my + 12;
        CustomGuiUtils.fillUltraRounded(c, x, y, w, h, 0xF0131722, 3.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, x, y, w, h, 0xFF3A3424, 3.0f);
        screen.text(c, line1, x + 6, y + 4, 0xFFE2E8F0);
        if (line2 != null) screen.text(c, line2, x + 6, y + 16, 0xFF94A3B8);
    }

    public static boolean handleModalClick(BameClientScreen screen, double mx, double my) {
        if (buyItem != null) return handleBuyClick(screen, mx, my);
        return false;
    }

    /** ESC schließt den Dialog; solange ein Dialog offen ist, gehen keine Tasten an das Menü. */
    public static boolean handleKey(KeyInput input) {
        if (!isModalOpen()) return false;
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            buyItem = null;
            buyError = null;
        }
        return true;
    }

    /** Kleines Preis-Badge („◆ 250“) unten links auf der Karte eines gesperrten Items. */
    public static void renderPriceBadge(BameClientScreen screen, DrawContext c, String itemId, int cardX, int cardY, int cardW, int cardH) {
        Wallet.ShopItem item = Wallet.item(itemId);
        if (item == null || item.price() <= 0) return;
        String label = "◆ " + item.price();
        int tw = screen.getTextRenderer().getWidth(CustomGuiUtils.getFontText(label));
        int bw = tw + 10;
        int bh = 12;
        int bx = cardX + 5;
        int by = cardY + cardH - bh - 4;
        CustomGuiUtils.fillUltraRounded(c, bx, by, bw, bh, 0xE0141018, 3.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, bx, by, bw, bh, 0xFF5C4A1A, 3.0f);
        screen.text(c, label, bx + 5, by + 2, GOLD_TEXT);
    }
}
