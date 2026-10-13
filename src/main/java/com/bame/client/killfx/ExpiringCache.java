package com.bame.client.killfx;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Kleiner Cache mit Ablaufzeit (ohne Minecraft, testbar): ein Eintrag gilt {@code ttlMs} lang nach dem letzten
 * {@link #put}. Schlüssel werden ohne Groß-/Kleinschreibung verglichen (Spielernamen).
 */
public final class ExpiringCache<V> {

    private record Entry<V>(V value, long time) {}

    private final long ttlMs;
    private final int maxSize;
    private final Map<String, Entry<V>> map = new HashMap<>();

    public ExpiringCache(long ttlMs, int maxSize) {
        this.ttlMs = ttlMs;
        this.maxSize = maxSize;
    }

    private static String key(String k) {
        return k == null ? null : k.trim().toLowerCase(Locale.ROOT);
    }

    /** Speichert bzw. erneuert einen Eintrag. Ist der Cache voll, wird der älteste verworfen. */
    public void put(String k, V value, long now) {
        String key = key(k);
        if (key == null || key.isEmpty() || value == null) return;
        if (!map.containsKey(key) && map.size() >= maxSize) {
            String oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<String, Entry<V>> e : map.entrySet()) {
                if (e.getValue().time() < oldestTime) {
                    oldestTime = e.getValue().time();
                    oldest = e.getKey();
                }
            }
            if (oldest != null) map.remove(oldest);
        }
        map.put(key, new Entry<>(value, now));
    }

    /** Gültiger Eintrag, sonst {@code null} (abgelaufen bzw. Zeit läuft rückwärts). */
    public V get(String k, long now) {
        String key = key(k);
        if (key == null) return null;
        Entry<V> e = map.get(key);
        if (e == null || now - e.time() > ttlMs || now < e.time()) return null;
        return e.value();
    }

    /** Abgelaufene Einträge entfernen. */
    public void prune(long now) {
        map.values().removeIf(e -> now - e.time() > ttlMs || now < e.time());
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }
}
