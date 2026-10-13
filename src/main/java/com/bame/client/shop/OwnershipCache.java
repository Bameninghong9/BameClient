package com.bame.client.shop;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Besitz fremder Spieler über {@code POST /v1/owners}. Wird nur für den LAN-Fallback gebraucht; Daten vom Sync-Server
 * sind dort schon bereinigt.
 * <ul>
 *   <li>gebündelt: max. 100 UUIDs pro Anfrage, höchstens eine Anfrage alle 2 s</li>
 *   <li>Gültigkeit 5 Minuten (danach wird neu angefragt, der alte Stand gilt bis dahin weiter)</li>
 *   <li>unbekannt → {@code null}: dann werden die Cosmetics des Spielers nicht gezeigt</li>
 * </ul>
 */
public final class OwnershipCache {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserOwnership");

    private static final int BATCH = 100;
    private static final long MIN_INTERVAL_MS = 2_000L;
    private static final long TTL_MS = 5 * 60_000L;
    private static final long STALE_LIMIT_MS = 15 * 60_000L;
    private static final long FAILURE_BACKOFF_MS = 15_000L;
    private static final int MAX_PENDING = 1_000;

    private record Entry(Set<String> owned, long fetchedAt) {}

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    private static volatile long nextRequestAt;
    private static volatile boolean inFlight;

    private OwnershipCache() {}

    /**
     * Besitz eines Spielers (Item-IDs GROSS) oder {@code null}, wenn unbekannt. Fehlende bzw. abgelaufene Einträge
     * werden zum Abruf vorgemerkt.
     */
    public static Set<String> getOwned(String uuid32) {
        String key = CaeserApi.uuid32(uuid32);
        if (key == null) return null;
        Entry e = CACHE.get(key);
        long now = System.currentTimeMillis();
        if (e == null || now - e.fetchedAt() > TTL_MS) {
            if (CaeserApi.isConfigured() && PENDING.size() < MAX_PENDING) PENDING.add(key);
        }
        if (e == null || now - e.fetchedAt() > STALE_LIMIT_MS) return null;
        return e.owned();
    }

    /** Version des Eintrags (für Caches der Aufrufer), 0 wenn unbekannt. */
    public static long version(String uuid32) {
        Entry e = uuid32 != null ? CACHE.get(uuid32) : null;
        return e != null ? e.fetchedAt() : 0L;
    }

    public static boolean owns(String uuid32, String modId) {
        String id = CaeserApi.itemId(modId);
        if (id == null) return true;
        Set<String> owned = getOwned(uuid32);
        return owned != null && owned.contains(id);
    }

    /** Vom Client-Tick aufgerufen: schickt höchstens eine gebündelte Anfrage alle 2 s. */
    public static void tick() {
        if (inFlight || PENDING.isEmpty() || !CaeserApi.isConfigured()) return;
        long now = System.currentTimeMillis();
        if (now < nextRequestAt) return;

        List<String> batch = new ArrayList<>(BATCH);
        for (String uuid : PENDING) {
            batch.add(uuid);
            if (batch.size() >= BATCH) break;
        }
        if (batch.isEmpty()) return;
        batch.forEach(PENDING::remove);

        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        batch.forEach(arr::add);
        body.add("uuids", arr);

        inFlight = true;
        nextRequestAt = now + MIN_INTERVAL_MS;
        CaeserApi.post("/v1/owners", body, null).whenComplete((json, err) -> MinecraftClient.getInstance().execute(() -> {
            inFlight = false;
            if (err != null || json == null) {
                nextRequestAt = System.currentTimeMillis() + FAILURE_BACKOFF_MS;
                // erneut vormerken (alter Stand bleibt bis zum Ablauf gültig)
                for (String u : batch) if (PENDING.size() < MAX_PENDING) PENDING.add(u);
                LOGGER.debug("Ownership lookup failed: {}", CaeserApi.unwrap(err).code);
                return;
            }
            long fetchedAt = System.currentTimeMillis();
            JsonObject owners = json.has("owners") && json.get("owners").isJsonObject() ? json.getAsJsonObject("owners") : new JsonObject();
            for (String u : batch) {
                Set<String> set = new LinkedHashSet<>();
                JsonElement el = owners.get(u);
                if (el == null) {
                    // Backend liefert die UUID evtl. in anderer Schreibweise
                    for (Map.Entry<String, JsonElement> me : owners.entrySet()) {
                        if (u.equals(CaeserApi.uuid32(me.getKey()))) { el = me.getValue(); break; }
                    }
                }
                if (el != null && el.isJsonArray()) {
                    for (JsonElement id : el.getAsJsonArray()) {
                        if (id.isJsonPrimitive()) set.add(id.getAsString().trim().toUpperCase(Locale.ROOT));
                    }
                }
                CACHE.put(u, new Entry(Collections.unmodifiableSet(set), fetchedAt));
            }
            if (CACHE.size() > 5_000) {
                long cutoff = fetchedAt - STALE_LIMIT_MS;
                CACHE.entrySet().removeIf(en -> en.getValue().fetchedAt() < cutoff);
            }
        }));
    }

    public static void clear() {
        CACHE.clear();
        PENDING.clear();
    }
}
