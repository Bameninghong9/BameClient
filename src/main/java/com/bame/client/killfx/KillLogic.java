package com.bame.client.killfx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reine Kill-Logik ohne Minecraft (testbar): Treffer-Gedächtnis, Zeitfenster, Abklingzeiten, Totem-Ausschluss und die
 * Regeln für Kill-Nachrichten im Chat. Alle Zeiten werden als Millisekunden übergeben.
 * <ul>
 *   <li>Ein Tod zählt nur innerhalb von {@link #HIT_WINDOW_MS} nach dem letzten eigenen Treffer.</li>
 *   <li>Opfer sind standardmäßig nur Spieler; Mobs nur, wenn die Option an ist.</li>
 *   <li>Pro Opfer höchstens ein Effekt in {@link #VICTIM_COOLDOWN_MS}.</li>
 *   <li>Ein kurz zuvor gesehener Totem-Pop des Opfers verhindert den Effekt.</li>
 * </ul>
 */
public final class KillLogic {

    public static final long HIT_WINDOW_MS = 8_000L;
    public static final long VICTIM_COOLDOWN_MS = 3_000L;
    public static final long TEST_COOLDOWN_MS = 3_000L;
    /** Der Sync-Server nimmt höchstens 1 killfx pro 2 s an. */
    public static final long SEND_INTERVAL_MS = 2_000L;
    /** So lange nach einem Totem-Pop zählt ein gemeldeter Tod des Opfers nicht. */
    public static final long TOTEM_GRACE_MS = 1_500L;
    public static final double PROJECTILE_HIT_RADIUS = 1.5;

    /** Wörter einer Kill-Nachricht (eines davon muss vorkommen, Groß-/Kleinschreibung egal). */
    public static final List<String> CHAT_KEYWORDS = List.of("killed", "slain", "eliminated", "getötet", "☠", "⚔");

    private static final Pattern VICTIM_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-orA-FK-OR]");
    private static final Pattern BY_WORD = Pattern.compile("(?<![\\p{L}0-9_])(by|von)(?![\\p{L}0-9_])", Pattern.CASE_INSENSITIVE);

    /** Ein gemerkter eigener Treffer. */
    public static final class Hit {
        public final UUID uuid;
        public int entityId;
        public String name;
        public boolean player;
        public long time;
        /** Letzte bekannte Position (für Kills aus dem Chat, wenn das Opfer schon verschwunden ist). */
        public double x, y, z;
        public boolean hasPos;

        public void setPos(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.hasPos = true;
        }

        Hit(UUID uuid) {
            this.uuid = uuid;
        }
    }

    public enum Decision { TRIGGER, NO_HIT, MOB_DISABLED, TOTEM, COOLDOWN }

    private final Map<UUID, Hit> hits = new HashMap<>();
    private final Map<UUID, Long> lastEffect = new HashMap<>();
    private final Map<UUID, Long> lastTotem = new HashMap<>();

    /** Treffer merken (bzw. Zeitstempel erneuern). */
    public void recordHit(UUID uuid, int entityId, String name, boolean player, long now) {
        if (uuid == null) return;
        Hit hit = hits.computeIfAbsent(uuid, Hit::new);
        hit.entityId = entityId;
        if (name != null && !name.isBlank()) hit.name = name;
        hit.player = player;
        hit.time = now;
    }

    /** Treffer auf dieses Opfer innerhalb des Zeitfensters, sonst {@code null}. */
    public Hit recentHit(UUID uuid, long now) {
        if (uuid == null) return null;
        Hit hit = hits.get(uuid);
        if (hit == null || now - hit.time > HIT_WINDOW_MS || now < hit.time) return null;
        return hit;
    }

    /** Alle Treffer im Zeitfenster (Kopie). */
    public List<Hit> recentHits(long now) {
        List<Hit> out = new ArrayList<>();
        for (Hit h : hits.values()) {
            if (now - h.time <= HIT_WINDOW_MS && now >= h.time) out.add(h);
        }
        return out;
    }

    public void recordTotemPop(UUID uuid, long now) {
        if (uuid != null) lastTotem.put(uuid, now);
    }

    public boolean totemPoppedRecently(UUID uuid, long now) {
        Long t = uuid != null ? lastTotem.get(uuid) : null;
        return t != null && now - t >= 0 && now - t <= TOTEM_GRACE_MS;
    }

    /**
     * Prüft einen erkannten Tod. Bei {@link Decision#TRIGGER} wird die Abklingzeit des Opfers gesetzt und der Treffer
     * verbraucht.
     */
    public Decision onDeath(UUID uuid, boolean mobsEnabled, long now) {
        Hit hit = recentHit(uuid, now);
        if (hit == null) return Decision.NO_HIT;
        return decide(hit, mobsEnabled, now);
    }

    private Decision decide(Hit hit, boolean mobsEnabled, long now) {
        if (!hit.player && !mobsEnabled) return Decision.MOB_DISABLED;
        if (totemPoppedRecently(hit.uuid, now)) return Decision.TOTEM;
        Long last = lastEffect.get(hit.uuid);
        if (last != null && now - last >= 0 && now - last < VICTIM_COOLDOWN_MS) return Decision.COOLDOWN;
        lastEffect.put(hit.uuid, now);
        hits.remove(hit.uuid);
        return Decision.TRIGGER;
    }

    /**
     * Kill-Nachricht im Chat (Minigames ohne echten Tod): Liefert das Opfer, wenn die Nachricht zu einem eigenen Treffer
     * auf einen Spieler (innerhalb von 8 s) passt und der Effekt ausgelöst werden darf, sonst {@code null}.
     */
    public Hit onChatMessage(String message, String ownName, long now) {
        if (message == null || ownName == null || ownName.isBlank()) return null;
        for (Hit hit : recentHits(now)) {
            if (!hit.player || hit.name == null) continue;
            if (!chatMatches(message, hit.name, ownName)) continue;
            if (decide(hit, false, now) == Decision.TRIGGER) return hit;
        }
        return null;
    }

    /**
     * Enthält die Nachricht den Namen des Opfers, den eigenen Namen (jeweils als ganzes Wort) und eines der
     * {@link #CHAT_KEYWORDS}? Zusätzlich wird "&lt;eigener Name&gt; ... by/von ... &lt;Opfer&gt;" verworfen: dann wurde man
     * selbst vom Opfer getötet.
     */
    public static boolean chatMatches(String message, String victimName, String ownName) {
        if (message == null || victimName == null || ownName == null) return false;
        if (victimName.isBlank() || ownName.isBlank() || victimName.equalsIgnoreCase(ownName)) return false;
        String msg = FORMATTING.matcher(message).replaceAll("");
        String lower = msg.toLowerCase(Locale.ROOT);
        boolean keyword = false;
        for (String k : CHAT_KEYWORDS) {
            if (lower.contains(k.toLowerCase(Locale.ROOT))) {
                keyword = true;
                break;
            }
        }
        if (!keyword) return false;
        int victimIdx = wordIndex(msg, victimName);
        int ownIdx = wordIndex(msg, ownName);
        if (victimIdx < 0 || ownIdx < 0) return false;
        Matcher by = BY_WORD.matcher(msg);
        if (by.find()) {
            int byIdx = by.start();
            if (ownIdx < byIdx && victimIdx > byIdx) return false;
        }
        return true;
    }

    /** Position des Namens als ganzes Wort (Minecraft-Namenszeichen als Wortgrenze), sonst -1. */
    static int wordIndex(String text, String name) {
        Pattern p = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        return m.find() ? m.start() : -1;
    }

    /**
     * Fernkampf: Ein eigenes Projektil verschwindet bzw. ist in höchstens 1,5 Blöcken an einem Lebewesen, dessen
     * Schadens-Zeit gerade frisch gesetzt wurde (höchstens 2 Ticks alt).
     */
    public static boolean isProjectileHit(double squaredDistanceToBox, int hurtTime, int maxHurtTime) {
        if (squaredDistanceToBox < 0 || squaredDistanceToBox > PROJECTILE_HIT_RADIUS * PROJECTILE_HIT_RADIUS) return false;
        if (maxHurtTime <= 0 || hurtTime <= 0) return false;
        return hurtTime >= maxHurtTime - 2;
    }

    /** Gültiger Opfername für die Sync-Nachricht, sonst {@code null}. */
    public static String sanitizeVictimName(String name) {
        if (name == null) return null;
        String n = FORMATTING.matcher(name).replaceAll("").trim();
        return VICTIM_NAME.matcher(n).matches() ? n : null;
    }

    /** Alte Einträge entfernen. */
    public void prune(long now) {
        hits.values().removeIf(h -> now - h.time > HIT_WINDOW_MS || now < h.time);
        lastEffect.values().removeIf(t -> now - t > VICTIM_COOLDOWN_MS || now < t);
        lastTotem.values().removeIf(t -> now - t > TOTEM_GRACE_MS || now < t);
    }

    public int hitCount() {
        return hits.size();
    }

    public Iterator<Hit> hitIterator() {
        return hits.values().iterator();
    }

    public void clear() {
        hits.clear();
        lastEffect.clear();
        lastTotem.clear();
    }

    /** Einfache Abklingzeit (Testen-Knopf, Sende-Limit). */
    public static final class Cooldown {
        private final long intervalMs;
        private long last;
        private boolean used;

        public Cooldown(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public boolean ready(long now) {
            return !used || now - last >= intervalMs || now < last;
        }

        /** Gibt {@code true} zurück und startet die Abklingzeit, wenn sie abgelaufen ist. */
        public boolean tryAcquire(long now) {
            if (!ready(now)) return false;
            used = true;
            last = now;
            return true;
        }

        public long remaining(long now) {
            return ready(now) ? 0L : intervalMs - (now - last);
        }
    }
}
