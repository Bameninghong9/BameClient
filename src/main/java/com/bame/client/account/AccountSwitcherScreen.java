package com.bame.client.account;

import com.bame.client.gui.CustomGuiUtils;
import com.bame.client.gui.GuiTheme;
import com.bame.client.sound.ClientSoundManager;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.session.Session;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Kleines Account-Fenster (Dropdown unter dem Account-Knopf im Multiplayer-Menü).
 * Das Multiplayer-Menü bleibt im Hintergrund sichtbar und wird nur leicht abgedunkelt.
 */
public class AccountSwitcherScreen extends Screen {
    private static final int PANEL_W = 220;
    private static final int HEADER_H = 22;
    private static final int ROW_H = 22;
    private static final int MAX_VISIBLE_ROWS = 5;
    private static final int ADD_H = 20;
    private static final long OPEN_ANIM_MS = 120L;
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long LOGIN_TIMEOUT_MS = 5L * 60L * 1000L;
    private static final long LOGIN_GRACE_MS = 3000L;

    private static final String MSG_LOGIN_HINT = "Melde dich im Fenster des Caeser Client an. (Alt+Tab, falls du es nicht siehst)";
    private static final String MSG_LOGIN_TIMEOUT = "Die Anmeldung hat zu lange gedauert. Bitte versuche es erneut.";
    private static final String MSG_LOGIN_CANCELLED = "Die Anmeldung wurde abgebrochen.";
    private static final String MSG_IN_WORLD = "Account-Wechsel geht nur im Menü, nicht während du in einer Welt bist.";
    private static final String MSG_SKIN_SYNC_FAILED = "Skin konnte nicht übertragen werden";
    /** Hinweis-Farbe (Orange): der Wechsel hat geklappt, nur der Skin-Upload nicht. */
    private static final int WARNING_COLOR = 0xFFF59E0B;

    private record Row(String id, String name, UUID uuid, boolean active) {}

    private final Screen parent;
    private final int anchorX, anchorY, anchorW, anchorH;

    private long openTime = 0L;
    private boolean initialized = false;
    private volatile boolean closed = false;

    private final List<Row> rows = new ArrayList<>();
    private boolean loadingList = false;
    private String busyId = null;
    private double scroll = 0.0;

    private String status = null;
    private boolean statusError = false;
    private boolean statusSpinner = false;
    /** Optionaler Zusatz hinter {@link #status}, in Orange gezeichnet. */
    private String statusWarning = null;

    private boolean loginActive = false;
    private boolean loginSeenRunning = false;
    private boolean pollInFlight = false;
    private long loginStart = 0L;
    private long lastPoll = 0L;
    private String lastKnownAddedId = null;
    private String preLoginAddedId = null;

    // Layout (in render berechnet, für Klicks wiederverwendet)
    private int px, py, ph, listY, listH, addX, addY, addW;

    public AccountSwitcherScreen(Screen parent, int anchorX, int anchorY, int anchorW, int anchorH) {
        super(Text.literal("Accounts"));
        this.parent = parent;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorW = anchorW;
        this.anchorH = anchorH;
    }

    @Override
    protected void init() {
        if (openTime == 0L) openTime = System.currentTimeMillis();
        if (initialized) return;
        initialized = true;

        setCurrentOnly();
        if (LauncherBridge.isAvailable()) {
            reloadList();
        } else {
            setStatus(LauncherBridge.MSG_NOT_AVAILABLE, false, false);
        }
    }

    // ------------------------------------------------------------------
    // Daten
    // ------------------------------------------------------------------

    private static String currentId() {
        Session s = client().getSession();
        UUID u = s != null ? s.getUuidOrNull() : null;
        return u != null ? u.toString().replace("-", "").toLowerCase(Locale.ROOT) : "";
    }

    private static net.minecraft.client.MinecraftClient client() {
        return net.minecraft.client.MinecraftClient.getInstance();
    }

    private void setCurrentOnly() {
        rows.clear();
        Session s = client().getSession();
        if (s != null) {
            rows.add(new Row(currentId(), s.getUsername(), s.getUuidOrNull(), true));
        }
        scroll = 0.0;
    }

    private void applyList(LauncherBridge.AccountList list) {
        rows.clear();
        String current = currentId();
        for (LauncherBridge.AccountInfo a : list.accounts()) {
            UUID uuid = null;
            try {
                uuid = LauncherBridge.parseUuid(a.id());
            } catch (Throwable ignored) {}
            // Maßgeblich ist, mit welchem Account das Spiel gerade wirklich läuft.
            rows.add(new Row(a.id(), a.name(), uuid, a.id().equals(current)));
        }
        if (rows.isEmpty()) setCurrentOnly();
        clampScroll();
        lastKnownAddedId = list.login().lastAddedId();
    }

    private void reloadList() {
        loadingList = true;
        LauncherBridge.fetchAccounts(list -> {
            if (closed) return;
            loadingList = false;
            applyList(list);
            // Läuft im Launcher noch eine Anmeldung (z. B. Fenster zwischendurch geschlossen), weiter abfragen.
            if (list.login().running() && !loginActive && busyId == null) {
                beginLoginPolling(list.login().lastAddedId(), true);
            }
        }, err -> {
            if (closed) return;
            loadingList = false;
            setCurrentOnly();
            setStatus(err, true, false);
        });
    }

    private void setStatus(String text, boolean error, boolean spinner) {
        this.status = text;
        this.statusError = error;
        this.statusSpinner = spinner;
        this.statusWarning = null;
    }

    private boolean isBusy() {
        return busyId != null || loginActive || SessionSwitcher.isSwitching();
    }

    // ------------------------------------------------------------------
    // Aktionen
    // ------------------------------------------------------------------

    private void switchTo(Row row) {
        if (row == null || row.active() || isBusy() || !LauncherBridge.isAvailable()) return;
        if (client().world != null) {
            setStatus(MSG_IN_WORLD, true, false);
            return;
        }
        busyId = row.id();
        setStatus(null, false, false);
        LauncherBridge.switchTo(row.id(), success -> {
            busyId = null;
            setStatus("Angemeldet als " + success.name(), false, false);
            if (LauncherBridge.SKIN_SYNC_FAILED.equals(success.skinSyncStatus())) {
                String detail = success.skinSyncError();
                statusWarning = MSG_SKIN_SYNC_FAILED + (detail != null && !detail.isBlank() ? ": " + detail : ".");
            }
            if (!closed) reloadList();
        }, err -> {
            busyId = null;
            setStatus(err, true, false);
        });
    }

    private void startLogin() {
        if (!LauncherBridge.isAvailable() || isBusy()) return;
        final String before = lastKnownAddedId;
        loginActive = true; // sperrt weitere Klicks, bis der Launcher geantwortet hat
        setStatus(MSG_LOGIN_HINT, false, true);
        LauncherBridge.startLogin(() -> {
            if (closed) return;
            beginLoginPolling(before, false);
        }, err -> {
            loginActive = false;
            if (closed) return;
            setStatus(err, true, false);
        });
    }

    private void beginLoginPolling(String addedIdBefore, boolean alreadyRunning) {
        loginActive = true;
        loginSeenRunning = alreadyRunning;
        pollInFlight = false;
        loginStart = System.currentTimeMillis();
        lastPoll = loginStart;
        preLoginAddedId = addedIdBefore;
        setStatus(MSG_LOGIN_HINT, false, true);
    }

    @Override
    public void tick() {
        try {
            if (parent != null) parent.tick();
        } catch (Throwable ignored) {}

        if (!loginActive || pollInFlight || closed) return;
        long now = System.currentTimeMillis();
        if (loginStart == 0L || now - lastPoll < POLL_INTERVAL_MS) return;
        if (now - loginStart > LOGIN_TIMEOUT_MS) {
            loginActive = false;
            setStatus(MSG_LOGIN_TIMEOUT, true, false);
            return;
        }
        lastPoll = now;
        pollInFlight = true;
        LauncherBridge.fetchAccounts(list -> {
            pollInFlight = false;
            if (closed || !loginActive) return;
            applyList(list);
            LauncherBridge.LoginState login = list.login();
            if (login.running()) {
                loginSeenRunning = true;
                return;
            }
            boolean newResult = login.error() != null || !Objects.equals(login.lastAddedId(), preLoginAddedId);
            if (!loginSeenRunning && !newResult && System.currentTimeMillis() - loginStart < LOGIN_GRACE_MS) {
                return; // Launcher hat die Anmeldung evtl. noch nicht als laufend gemeldet
            }
            finishLogin(login);
        }, err -> {
            pollInFlight = false;
            if (closed || !loginActive) return;
            loginActive = false;
            setStatus(err, true, false);
        });
    }

    private void finishLogin(LauncherBridge.LoginState login) {
        loginActive = false;
        if (login.error() != null) {
            setStatus(login.error(), true, false);
            return;
        }
        String added = login.lastAddedId();
        if (added == null) {
            setStatus(MSG_LOGIN_CANCELLED, false, false);
            return;
        }
        Row target = null;
        for (Row r : rows) {
            if (r.id().equals(added)) {
                target = r;
                break;
            }
        }
        if (added.equals(currentId())) {
            Session s = client().getSession();
            setStatus("Angemeldet als " + (s != null ? s.getUsername() : ""), false, false);
            return;
        }
        if (target == null) {
            target = new Row(added, "Neuer Account", null, false);
        }
        switchTo(target);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private float openProgress() {
        float t = Math.min(1.0f, (System.currentTimeMillis() - openTime) / (float) OPEN_ANIM_MS);
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    private static int fade(int color, float alpha) {
        int a = (color >>> 24) & 0xFF;
        return ((int) (a * alpha) << 24) | (color & 0xFFFFFF);
    }

    private List<OrderedText> statusLines(int maxWidth) {
        if (status == null || status.isBlank()) return List.of();
        if (statusWarning == null || statusWarning.isBlank()) {
            return textRenderer.wrapLines(CustomGuiUtils.getFontText(status), maxWidth);
        }
        // Hauptmeldung in der normalen Statusfarbe, Zusatz in Orange; der zusammengesetzte Text wird gemeinsam umbrochen.
        Text combined = Text.empty()
                .append(CustomGuiUtils.getFontText(status + " – "))
                .append(CustomGuiUtils.getFontText(statusWarning).copy().styled(s -> s.withColor(WARNING_COLOR & 0xFFFFFF)));
        return textRenderer.wrapLines(combined, maxWidth);
    }

    private int visibleRows() {
        return Math.max(1, Math.min(MAX_VISIBLE_ROWS, rows.size()));
    }

    private int maxScroll() {
        return Math.max(0, rows.size() * ROW_H - visibleRows() * ROW_H);
    }

    private void clampScroll() {
        scroll = Math.max(0.0, Math.min(maxScroll(), scroll));
    }

    private void layout() {
        px = Math.max(4, Math.min(anchorX, width - PANEL_W - 4));
        py = anchorY + anchorH + 4;
        listY = py + HEADER_H + 3;
        listH = visibleRows() * ROW_H;
        addX = px + 8;
        addW = PANEL_W - 16;
        addY = listY + listH + 6;
        int statusTextW = PANEL_W - 20 - (statusSpinner ? 14 : 0);
        int lines = statusLines(statusTextW).size();
        int bottom = addY + ADD_H + 8;
        if (lines > 0) bottom += lines * 10 + 2;
        ph = bottom - py;
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        // Das Multiplayer-Menü zeichnet seinen eigenen Hintergrund (Panorama + Blur, genau einmal pro Frame).
        if (parent != null) {
            try {
                parent.renderBackground(c, -1, -1, delta);
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        if (parent != null) {
            try {
                parent.render(c, -1, -1, delta);
            } catch (Throwable ignored) {}
        }
        super.render(c, mx, my, delta);

        float p = openProgress();
        c.fill(0, 0, width, height, fade(0x50000000, p));

        layout();
        int accent = GuiTheme.accent();

        c.getMatrices().pushMatrix();
        float scale = 0.94f + 0.06f * p;
        c.getMatrices().translate((float) px, (float) py);
        c.getMatrices().scale(scale, scale);
        c.getMatrices().translate((float) -px, (float) -py);

        // Panel
        CustomGuiUtils.fillUltraRounded(c, px, py, PANEL_W, ph, fade(GuiTheme.alpha(GuiTheme.surface(), 0xF4), p), 8.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, px, py, PANEL_W, ph, fade(GuiTheme.alpha(accent, 0x80), p), 8.0f);

        // Kopfzeile
        c.drawText(textRenderer, Text.literal("ACCOUNTS"), px + 10, py + 8, fade(0xFFFFFFFF, p), false);
        boolean hoverClose = inside(mx, my, px + PANEL_W - 20, py + 4, 16, 16);
        if (hoverClose) {
            CustomGuiUtils.fillUltraRounded(c, px + PANEL_W - 20, py + 4, 16, 16, fade(GuiTheme.alpha(accent, 0x30), p), 4.0f);
        }
        int closeW = textRenderer.getWidth("×");
        c.drawText(textRenderer, Text.literal("×"), px + PANEL_W - 12 - closeW / 2, py + 8, fade(hoverClose ? 0xFFFFFFFF : 0xFF8E95A4, p), false);
        c.fill(px + 8, py + HEADER_H, px + PANEL_W - 8, py + HEADER_H + 1, fade(0xFF232530, p));

        // Liste
        renderList(c, mx, my, p, accent);

        // Hinzufügen-Knopf
        boolean addEnabled = LauncherBridge.isAvailable() && !isBusy();
        boolean hoverAdd = addEnabled && inside(mx, my, addX, addY, addW, ADD_H);
        int addBg;
        int addBorder;
        int addText;
        if (!LauncherBridge.isAvailable()) {
            addBg = 0xFF15161C;
            addBorder = 0xFF23252E;
            addText = 0xFF4B5060;
        } else {
            addBg = hoverAdd ? GuiTheme.alpha(accent, 0x40) : GuiTheme.alpha(accent, 0x1E);
            addBorder = hoverAdd ? accent : GuiTheme.alpha(accent, 0x90);
            addText = addEnabled ? (hoverAdd ? 0xFFFFFFFF : accent) : GuiTheme.alpha(accent, 0x80);
        }
        CustomGuiUtils.fillUltraRounded(c, addX, addY, addW, ADD_H, fade(addBg, p), 4.0f);
        CustomGuiUtils.drawUltraRoundedOutline(c, addX, addY, addW, ADD_H, fade(addBorder, p), 4.0f);
        Text addLabel = CustomGuiUtils.getFontText("+ ACCOUNT HINZUFÜGEN");
        int addLabelW = textRenderer.getWidth(addLabel);
        c.drawText(textRenderer, addLabel, addX + (addW - addLabelW) / 2, addY + (ADD_H - 8) / 2, fade(addText, p), false);

        // Statuszeile
        int statusTextW = PANEL_W - 20 - (statusSpinner ? 14 : 0);
        List<OrderedText> lines = statusLines(statusTextW);
        if (!lines.isEmpty()) {
            int sy = addY + ADD_H + 6;
            int sx = px + 10;
            if (statusSpinner) {
                drawSpinner(c, sx + 5, sy + 4, fade(accent, p));
                sx += 14;
            }
            int color = statusError ? 0xFFF87171 : 0xFF9CA3AF;
            for (OrderedText line : lines) {
                c.drawText(textRenderer, line, sx, sy, fade(color, p), false);
                sy += 10;
            }
        }

        c.getMatrices().popMatrix();
    }

    private void renderList(DrawContext c, int mx, int my, float p, int accent) {
        int rowX = px + 6;
        int rowW = PANEL_W - 12;
        boolean scrollable = rows.size() > MAX_VISIBLE_ROWS;
        if (scrollable) rowW -= 4;
        boolean mouseInList = inside(mx, my, px, listY, PANEL_W, listH);

        c.enableScissor(px + 2, listY, px + PANEL_W - 2, listY + listH);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int ry = listY + i * ROW_H - (int) Math.round(scroll);
            if (ry + ROW_H < listY || ry > listY + listH) continue;

            boolean busyRow = row.id().equals(busyId);
            boolean clickable = !row.active() && !isBusy() && LauncherBridge.isAvailable();
            boolean hover = mouseInList && inside(mx, my, rowX, ry + 1, rowW, ROW_H - 2);

            if (busyRow) {
                CustomGuiUtils.fillUltraRounded(c, rowX, ry + 1, rowW, ROW_H - 2, fade(GuiTheme.alpha(accent, 0x22), p), 4.0f);
            } else if (hover && clickable) {
                CustomGuiUtils.fillUltraRounded(c, rowX, ry + 1, rowW, ROW_H - 2, fade(0xFF242630, p), 4.0f);
                CustomGuiUtils.drawUltraRoundedOutline(c, rowX, ry + 1, rowW, ROW_H - 2, fade(GuiTheme.alpha(accent, 0x90), p), 4.0f);
            } else if (row.active()) {
                CustomGuiUtils.fillUltraRounded(c, rowX, ry + 1, rowW, ROW_H - 2, fade(GuiTheme.alpha(accent, 0x14), p), 4.0f);
            }

            AccountHeads.draw(c, row.uuid(), rowX + 4, ry + 3, 16, fade(0xFFFFFFFF, p));

            // rechter Bereich: Haken + AKTIV bzw. Lade-Kreisel
            int rightW = 0;
            if (row.active()) {
                Text aktiv = CustomGuiUtils.getFontText("AKTIV");
                int aw = textRenderer.getWidth(aktiv);
                int checkX = rowX + rowW - 6 - 10;
                drawCheck(c, checkX, ry + 6, fade(accent, p));
                c.drawText(textRenderer, aktiv, checkX - 4 - aw, ry + 7, fade(accent, p), false);
                rightW = 10 + 4 + aw + 6;
            } else if (busyRow) {
                drawSpinner(c, rowX + rowW - 11, ry + ROW_H / 2, fade(accent, p));
                rightW = 18;
            }

            int nameX = rowX + 26;
            int nameMax = rowX + rowW - rightW - 4 - nameX;
            Text nameText = CustomGuiUtils.getFontText(row.name());
            if (textRenderer.getWidth(nameText) > nameMax) {
                String trimmed = textRenderer.trimToWidth(nameText, Math.max(0, nameMax - textRenderer.getWidth(CustomGuiUtils.getFontText("…")))).getString() + "…";
                nameText = CustomGuiUtils.getFontText(trimmed);
            }
            int nameColor = row.active() ? 0xFFFFFFFF : (hover && clickable ? 0xFFFFFFFF : 0xFFD4D8E0);
            if (!LauncherBridge.isAvailable() && !row.active()) nameColor = 0xFF6B7280;
            c.drawText(textRenderer, nameText, nameX, ry + 7, fade(nameColor, p), false);
        }
        c.disableScissor();

        if (scrollable) {
            int trackX = px + PANEL_W - 7;
            c.fill(trackX, listY + 2, trackX + 2, listY + listH - 2, fade(0xFF1E2028, p));
            int total = rows.size() * ROW_H;
            int thumbH = Math.max(12, (listH - 4) * listH / total);
            int maxS = maxScroll();
            int thumbY = listY + 2 + (int) ((listH - 4 - thumbH) * (maxS > 0 ? scroll / maxS : 0));
            c.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, fade(GuiTheme.alpha(accent, 0xC0), p));
        }

        if (loadingList && LauncherBridge.isAvailable()) {
            drawSpinner(c, px + PANEL_W - 34, py + HEADER_H / 2, fade(0xFF8E95A4, p));
        }
    }

    /** Kleiner Haken (10×8). */
    private static void drawCheck(DrawContext c, int x, int y, int color) {
        CustomGuiUtils.drawSmoothLine(c, x + 1.0f, y + 4.5f, x + 4.0f, y + 7.5f, 1.6f, color);
        CustomGuiUtils.drawSmoothLine(c, x + 4.0f, y + 7.5f, x + 9.0f, y + 1.0f, 1.6f, color);
    }

    /** Lade-Kreisel: 8 Punkte, die im Kreis umlaufen. */
    private static void drawSpinner(DrawContext c, int cx, int cy, int color) {
        int baseAlpha = (color >>> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        int head = (int) ((System.currentTimeMillis() / 90L) % 8L);
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI * 2.0 * i / 8.0;
            int dx = (int) Math.round(Math.cos(ang) * 4.0);
            int dy = (int) Math.round(Math.sin(ang) * 4.0);
            int age = (head - i + 8) % 8;
            float f = 1.0f - age / 8.0f;
            int a = (int) (baseAlpha * (0.15f + 0.85f * f));
            c.fill(cx + dx - 1, cy + dy - 1, cx + dx + 1, cy + dy + 1, (a << 24) | rgb);
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    // ------------------------------------------------------------------
    // Eingabe
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        layout();

        if (!inside(mx, my, px, py, PANEL_W, ph)) {
            close();
            return true;
        }
        if (click.button() != 0) return true;

        if (inside(mx, my, px + PANEL_W - 20, py + 4, 16, 16)) {
            ClientSoundManager.playClick();
            close();
            return true;
        }

        if (inside(mx, my, px, listY, PANEL_W, listH)) {
            int idx = (int) ((my - listY + scroll) / ROW_H);
            if (idx >= 0 && idx < rows.size()) {
                Row row = rows.get(idx);
                if (!row.active() && !isBusy() && LauncherBridge.isAvailable()) {
                    ClientSoundManager.playClick();
                    switchTo(row);
                }
            }
            return true;
        }

        if (inside(mx, my, addX, addY, addW, ADD_H)) {
            if (LauncherBridge.isAvailable() && !isBusy()) {
                ClientSoundManager.playClick();
                startLogin();
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (rows.size() > MAX_VISIBLE_ROWS && inside(mx, my, px, listY, PANEL_W, listH)) {
            scroll -= vertical * ROW_H;
            clampScroll();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void resize(int width, int height) {
        if (parent != null) {
            try {
                parent.resize(width, height);
            } catch (Throwable ignored) {}
        }
        super.resize(width, height);
    }

    @Override
    public void close() {
        closed = true;
        loginActive = false;
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void removed() {
        // Fenster geschlossen: laufende Abfragen nicht weiterführen
        closed = true;
        loginActive = false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
