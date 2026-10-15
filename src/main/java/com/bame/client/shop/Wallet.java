package com.bame.client.shop;

import com.bame.client.BameClientConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Jeton-Kontostand, Besitz und Shop-Katalog des lokalen Spielers.
 * <p>
 * Der Zustand ist ein unveränderlicher {@link Snapshot}, der nur auf dem Haupt-Thread ausgetauscht wird.
 * Der letzte bekannte Besitz und der Katalog (ohne Token und ohne Kontostand) liegen pro UUID in einer kleinen
 * Cache-Datei, damit Freischaltungen auch offline bzw. im Einzelspieler funktionieren.
 */
public final class Wallet {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserWallet");

    public enum Status { NOT_CONFIGURED, LOADING, OK, OFFLINE }

    public record ShopItem(String id, String category, String name, int price, boolean active) {}

    /** {@code jetons == null}: Kontostand unbekannt (z. B. nur aus dem Cache geladen). */
    public record Snapshot(String uuid, Integer jetons, Set<String> owned, boolean isAdmin,
                           Map<String, ShopItem> catalog, boolean hasData) {
        static Snapshot empty(String uuid) {
            return new Snapshot(uuid, null, Set.of(), false, Map.of(), false);
        }
    }

    private static volatile Snapshot snapshot = Snapshot.empty(null);
    private static volatile Status status = CaeserApi.isConfigured() ? Status.LOADING : Status.NOT_CONFIGURED;
    private static volatile String lastError;
    private static volatile long lastRefreshAt;
    private static final AtomicBoolean REFRESHING = new AtomicBoolean(false);
    private static int generation;

    private Wallet() {}

    // ==========================================
    // Lesen (alle Threads)
    // ==========================================

    /** Letzter leerer Stand (unveränderlich); pro Frame mehrfach gebraucht, wenn kein Stand zum Account passt. */
    private static volatile Snapshot emptySnapshot = Snapshot.empty(null);

    /** Aktueller Stand, nur wenn er zum aktuellen Account gehört; sonst leer. */
    public static Snapshot get() {
        Snapshot s = snapshot;
        String cur = CaeserAuth.currentSessionUuid32();
        if (cur == null || !cur.equals(s.uuid())) {
            Snapshot empty = emptySnapshot;
            if (!java.util.Objects.equals(empty.uuid(), cur)) {
                empty = Snapshot.empty(cur);
                emptySnapshot = empty;
            }
            return empty;
        }
        return s;
    }

    public static Status status() {
        return status;
    }

    public static String lastError() {
        return lastError;
    }

    /** {@code true}, wenn der Kontostand aktuell vom Server bekannt ist. */
    public static boolean isOnline() {
        return status == Status.OK && get().jetons() != null;
    }

    public static boolean hasData() {
        return get().hasData();
    }

    /** Besitzt der lokale Spieler das Item (laut {@code owned}) oder ist es laut Katalog gratis? */
    public static boolean isOwned(String modId) {
        String id = CaeserApi.itemId(modId);
        if (id == null) return true;
        Snapshot s = get();
        if (s.owned().contains(id)) return true;
        ShopItem item = s.catalog().get(id);
        return item != null && item.price() == 0 && item.active();
    }

    public static ShopItem item(String modId) {
        String id = CaeserApi.itemId(modId);
        return id == null ? null : get().catalog().get(id);
    }

    // ==========================================
    // Laden
    // ==========================================

    /** Beim Spielstart: Cache des aktuellen Accounts laden. */
    public static void init() {
        loadCacheForCurrentAccount();
    }

    /** Lädt Shop und Kontostand neu, sofern der letzte Abruf länger als {@code maxAgeMs} her ist. */
    public static void refreshIfOlderThan(long maxAgeMs) {
        if (System.currentTimeMillis() - lastRefreshAt >= maxAgeMs) refresh();
    }

    /** Lädt Katalog ({@code /v1/shop}) und Konto ({@code /v1/me}) asynchron neu. Höchstens ein Abruf gleichzeitig. */
    public static void refresh() {
        if (!CaeserApi.isConfigured()) {
            status = Status.NOT_CONFIGURED;
            return;
        }
        if (!CaeserAuth.canLogin()) {
            // Ohne gültige Session kein Konto, Katalog trotzdem holen (für Preise)
            lastRefreshAt = System.currentTimeMillis();
            fetchCatalogOnly();
            status = Status.OFFLINE;
            lastError = CaeserAuth.MSG_NO_SESSION;
            return;
        }
        if (!REFRESHING.compareAndSet(false, true)) return;
        lastRefreshAt = System.currentTimeMillis();
        final int gen = generation;
        final String uuid = CaeserAuth.currentSessionUuid32();
        final long startedAt = System.currentTimeMillis();
        if (status != Status.OK) status = Status.LOADING;

        CompletableFuture<JsonObject> shop = CaeserApi.get("/v1/shop", null);
        CompletableFuture<JsonObject> me = CaeserAuth.authed(tok -> CaeserApi.get("/v1/me", tok));

        shop.handle((s, e) -> e == null ? s : null)
                .thenCombine(me.handle((m, e) -> new Object[]{m, e}), (shopJson, meRes) -> new Object[]{shopJson, meRes[0], meRes[1]})
                .whenComplete((res, err) -> {
                    REFRESHING.set(false);
                    MinecraftClient.getInstance().execute(com.bame.client.util.Prof.task("caeser_task_wallet_refresh", () -> {
                        if (gen != generation || uuid == null || !uuid.equals(CaeserAuth.currentSessionUuid32())) return;
                        if (err != null || res == null) {
                            markOffline(CaeserApi.MSG_UNREACHABLE);
                            return;
                        }
                        JsonObject shopJson = (JsonObject) res[0];
                        JsonObject meJson = (JsonObject) res[1];
                        Throwable meErr = (Throwable) res[2];
                        Map<String, ShopItem> catalog = shopJson != null ? parseCatalog(shopJson) : null;
                        if (meJson != null) {
                            applyMe(uuid, meJson, catalog);
                            applyCapeId(uuid, meJson, startedAt);
                        } else {
                            if (catalog != null) applyCatalog(uuid, catalog);
                            markOffline(meErr != null ? CaeserApi.unwrap(meErr).userMessage() : CaeserApi.MSG_UNREACHABLE);
                        }
                    }));
                });
    }

    private static void fetchCatalogOnly() {
        final String uuid = CaeserAuth.currentSessionUuid32();
        final int gen = generation;
        CaeserApi.get("/v1/shop", null).whenComplete((json, err) -> {
            if (err != null || json == null) return;
            Map<String, ShopItem> catalog = parseCatalog(json);
            MinecraftClient.getInstance().execute(com.bame.client.util.Prof.task("caeser_task_wallet_catalog", () -> {
                if (gen != generation) return;
                applyCatalog(uuid, catalog);
            }));
        });
    }

    private static void markOffline(String msg) {
        status = Status.OFFLINE;
        lastError = msg;
        Snapshot s = snapshot;
        // Kontostand ist nicht mehr gesichert aktuell; Besitz bleibt (Cache)
        snapshot = new Snapshot(s.uuid(), null, s.owned(), s.isAdmin(), s.catalog(), s.hasData());
    }

    private static void applyMe(String uuid, JsonObject me, Map<String, ShopItem> newCatalog) {
        Snapshot old = snapshot;
        Map<String, ShopItem> catalog = newCatalog != null ? newCatalog
                : (uuid.equals(old.uuid()) ? old.catalog() : Map.of());
        Integer jetons = me.has("jetons") && me.get("jetons").isJsonPrimitive() ? me.get("jetons").getAsInt() : null;
        boolean isAdmin = me.has("isAdmin") && me.get("isAdmin").isJsonPrimitive() && me.get("isAdmin").getAsBoolean();
        Set<String> owned = parseOwned(me.get("owned"));
        setSnapshot(new Snapshot(uuid, jetons, owned, isAdmin, catalog, true));
        status = Status.OK;
        lastError = null;
        saveCacheAsync(snapshot);
    }

    /**
     * Eigenes gewähltes Caeser-Cape aus {@code /v1/me} ({@code capeId}, auch in Prüfung). Fehlt das Feld (älteres
     * Backend), gilt {@code null}. Fallback zum Sync-{@code state}; ein neuerer Sync-Wert hat Vorrang.
     */
    private static void applyCapeId(String uuid, JsonObject me, long requestStartedAt) {
        try {
            com.bame.client.cosmetics.CaeserCapeManager.setOwnCapeFromWallet(uuid, optStr(me, "capeId"), requestStartedAt);
        } catch (Throwable t) {
            LOGGER.debug("Could not apply capeId ({})", t.getClass().getSimpleName());
        }
    }

    private static void applyCatalog(String uuid, Map<String, ShopItem> catalog) {
        Snapshot old = snapshot;
        if (uuid == null || !uuid.equals(old.uuid())) {
            setSnapshot(new Snapshot(uuid, null, Set.of(), false, catalog, true));
        } else {
            setSnapshot(new Snapshot(uuid, old.jetons(), old.owned(), old.isAdmin(), catalog, true));
        }
        saveCacheAsync(snapshot);
    }

    /** Ergebnis eines Kaufs übernehmen ({@code { jetons, owned }}). Haupt-Thread. */
    static void applyPurchase(String uuid, JsonObject res) {
        Snapshot old = snapshot;
        if (uuid == null || !uuid.equals(old.uuid())) return;
        Integer jetons = res.has("jetons") && res.get("jetons").isJsonPrimitive() ? res.get("jetons").getAsInt() : old.jetons();
        Set<String> owned = res.has("owned") ? parseOwned(res.get("owned")) : old.owned();
        setSnapshot(new Snapshot(uuid, jetons, owned, old.isAdmin(), old.catalog(), true));
        saveCacheAsync(snapshot);
    }

    private static void setSnapshot(Snapshot next) {
        Snapshot prev = snapshot;
        snapshot = next;
        if (!prev.owned().equals(next.owned()) || !prev.catalog().equals(next.catalog())) {
            // Besitz geändert: gefilterten Sync-State neu senden
            try {
                com.bame.client.cosmetics.CaeserPlayerSyncManager.markStateDirty();
            } catch (Throwable ignored) {}
        }
        for (Runnable l : LISTENERS) {
            try { l.run(); } catch (Throwable ignored) {}
        }
    }

    private static final List<Runnable> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Wird auf dem Haupt-Thread nach jeder Änderung des Stands aufgerufen. */
    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    /** Nach einem Account-Wechsel: alles verwerfen und den Cache des neuen Accounts laden. Haupt-Thread. */
    public static void onAccountChanged() {
        generation++;
        REFRESHING.set(false);
        lastRefreshAt = 0L;
        lastError = null;
        status = CaeserApi.isConfigured() ? Status.LOADING : Status.NOT_CONFIGURED;
        setSnapshot(Snapshot.empty(CaeserAuth.currentSessionUuid32()));
        loadCacheForCurrentAccount();
    }

    // ==========================================
    // Kaufen
    // ==========================================

    public record PurchaseResult(boolean success, String message, int status, String code, Integer missing) {}

    /**
     * Kauft ein Item. Pro Aufruf eine neue {@code requestId}; bei Netzwerkfehler genau ein Wiederholungsversuch mit
     * derselben {@code requestId}. Das Ergebnis wird auf dem Haupt-Thread geliefert.
     */
    public static void buy(String modId, Consumer<PurchaseResult> callback) {
        String itemId = CaeserApi.itemId(modId);
        final String uuid = CaeserAuth.currentSessionUuid32();
        if (itemId == null || uuid == null || !CaeserApi.isConfigured()) {
            MinecraftClient.getInstance().execute(() -> callback.accept(
                    new PurchaseResult(false, CaeserApi.MSG_UNREACHABLE, 0, "NOT_CONFIGURED", null)));
            return;
        }
        final String requestId = UUID.randomUUID().toString();
        JsonObject body = new JsonObject();
        body.addProperty("itemId", itemId);
        body.addProperty("requestId", requestId);

        attemptBuy(body).handle((res, err) -> {
            if (err == null) return CompletableFuture.completedFuture(res);
            CaeserApi.ApiException api = CaeserApi.unwrap(err);
            if (api.isNetworkError() && !"NO_SESSION".equals(api.code) && !"LOGIN_BACKOFF".equals(api.code)
                    && !api.code.startsWith("MOJANG")) {
                return attemptBuy(body); // gleiche requestId: das Backend bucht nicht doppelt ab
            }
            return CompletableFuture.<JsonObject>failedFuture(api);
        }).thenCompose(f -> f).whenComplete((res, err) -> MinecraftClient.getInstance().execute(() -> {
            if (err == null && res != null) {
                applyPurchase(uuid, res);
                status = Status.OK;
                callback.accept(new PurchaseResult(true, null, 200, null, null));
                refresh();
                return;
            }
            CaeserApi.ApiException api = CaeserApi.unwrap(err);
            Integer missing = null;
            if (api.status == 402 && api.body != null && api.body.has("missing") && api.body.get("missing").isJsonPrimitive()) {
                try { missing = api.body.get("missing").getAsInt(); } catch (Throwable ignored) {}
            }
            if (api.status == 402 || api.status == 409) {
                lastRefreshAt = 0L;
                refresh();
            }
            callback.accept(new PurchaseResult(false, api.userMessage(), api.status, api.code, missing));
        }));
    }

    private static CompletableFuture<JsonObject> attemptBuy(JsonObject body) {
        return CaeserAuth.authed(tok -> CaeserApi.post("/v1/shop/buy", body, tok));
    }

    private static String optStr(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) return null;
        return o.get(key).getAsString();
    }

    // ==========================================
    // Parsen
    // ==========================================

    private static Map<String, ShopItem> parseCatalog(JsonObject json) {
        Map<String, ShopItem> map = new LinkedHashMap<>();
        if (json != null && json.has("items") && json.get("items").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("items")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                try {
                    String id = optStr(o, "id");
                    if (id == null || id.isBlank()) continue;
                    id = id.trim().toUpperCase(Locale.ROOT);
                    String cat = optStr(o, "category");
                    String name = optStr(o, "name");
                    int price = o.has("price") && o.get("price").isJsonPrimitive() ? Math.max(0, o.get("price").getAsInt()) : 0;
                    boolean active = !o.has("active") || parseBool(o.get("active"));
                    map.put(id, new ShopItem(id, cat != null ? cat.toUpperCase(Locale.ROOT) : "", name != null ? name : id, price, active));
                } catch (Throwable ignored) {}
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static boolean parseBool(JsonElement el) {
        if (el == null || !el.isJsonPrimitive()) return false;
        if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
        if (el.getAsJsonPrimitive().isNumber()) return el.getAsInt() != 0;
        return "true".equalsIgnoreCase(el.getAsString()) || "1".equals(el.getAsString());
    }

    private static Set<String> parseOwned(JsonElement el) {
        Set<String> set = new LinkedHashSet<>();
        if (el != null && el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) {
                if (e.isJsonPrimitive()) {
                    String id = e.getAsString().trim().toUpperCase(Locale.ROOT);
                    if (!id.isEmpty()) set.add(id);
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    // ==========================================
    // Cache-Datei (nur owned + Katalog, ohne Token und ohne Kontostand)
    // ==========================================

    private static Path cacheDir() {
        return BameClientConfig.BASE_CONFIG_DIR.resolve("shop_cache");
    }

    private static Path cacheFile(String uuid32) {
        return cacheDir().resolve(uuid32 + ".json");
    }

    private static void loadCacheForCurrentAccount() {
        String uuid = CaeserAuth.currentSessionUuid32();
        if (uuid == null) return;
        try {
            Path file = cacheFile(uuid);
            if (!Files.isRegularFile(file) || Files.size(file) > 256 * 1024) return;
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!uuid.equals(CaeserApi.uuid32(optStr(root, "uuid")))) return;
            Set<String> owned = parseOwned(root.get("owned"));
            JsonObject catalogWrap = new JsonObject();
            if (root.has("catalog") && root.get("catalog").isJsonArray()) {
                catalogWrap.add("items", root.getAsJsonArray("catalog"));
            }
            Map<String, ShopItem> catalog = parseCatalog(catalogWrap);
            Snapshot cur = snapshot;
            if (cur.hasData() && uuid.equals(cur.uuid())) return; // Server war schneller
            setSnapshot(new Snapshot(uuid, null, owned, false, catalog, true));
        } catch (Throwable t) {
            LOGGER.warn("Could not read shop cache ({})", t.getClass().getSimpleName());
        }
    }

    private static void saveCacheAsync(Snapshot s) {
        if (s == null || s.uuid() == null) return;
        JsonObject root = new JsonObject();
        root.addProperty("uuid", s.uuid());
        JsonArray owned = new JsonArray();
        for (String id : s.owned()) owned.add(id);
        root.add("owned", owned);
        JsonArray catalog = new JsonArray();
        for (ShopItem item : s.catalog().values()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", item.id());
            o.addProperty("category", item.category());
            o.addProperty("name", item.name());
            o.addProperty("price", item.price());
            o.addProperty("active", item.active());
            catalog.add(o);
        }
        root.add("catalog", catalog);
        root.addProperty("savedAt", System.currentTimeMillis());
        final String content = root.toString();
        final String uuid = s.uuid();
        CaeserApi.WORKER.execute(() -> writeAtomic(cacheFile(uuid), content));
    }

    private static synchronized void writeAtomic(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            LOGGER.warn("Could not write shop cache ({})", t.getClass().getSimpleName());
        }
    }
}
