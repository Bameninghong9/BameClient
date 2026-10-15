package com.bame.client.cosmetics;

import com.bame.client.util.FastIds;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prüft ohne Minecraft die pro Frame laufenden Hilfen der Tab-Liste und der Cosmetics-Renderer:
 * <ul>
 *   <li>{@link FastIds#uuid32} liefert exakt dasselbe wie {@code uuid.toString().replace("-", "").toLowerCase()}.</li>
 *   <li>{@link FastIds#cleanUsername} liefert exakt dasselbe wie die frühere Regex-Variante.</li>
 *   <li>Die kombinierte Badge-Abfrage (eine UUID-Umwandlung, lokale UUID zwischengespeichert) entscheidet wie die alte
 *       Kombination aus {@code isCaeserPlayer} und {@code getPlayerBadgePrefix}.</li>
 * </ul>
 * Danach eine kleine Messung: 100 Tab-Einträge x 1000 Frames, alt gegen neu (nur Ausgabe, keine Zeit-Grenzen).
 */
public class TabListPerfTest {

    private static int checks = 0;

    public static void main(String[] args) {
        uuidEquality();
        cleanUsernameEquality();
        badgeDecisionEquality();
        benchmark();
        System.out.println("TAB LIST PERF TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Alte Implementierungen (Referenz, unverändert übernommen)
    // ------------------------------------------------------------------------------------------------------------

    static String oldUuid32(UUID uuid) {
        if (uuid == null) return null;
        return uuid.toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    static String oldCleanUsername(String raw) {
        if (raw == null) return null;
        String clean = raw.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        int spaceIdx = clean.lastIndexOf(' ');
        if (spaceIdx >= 0) {
            clean = clean.substring(spaceIdx + 1);
        }
        return clean.toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------------------------------------------------------

    private static void uuidEquality() {
        Random r = new Random(42);
        check(FastIds.uuid32(null) == null, "null uuid");
        UUID[] special = {
                new UUID(0L, 0L), new UUID(-1L, -1L), new UUID(Long.MIN_VALUE, Long.MAX_VALUE),
                new UUID(0x0123456789abcdefL, 0xfedcba9876543210L), UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")
        };
        for (UUID u : special) check(FastIds.uuid32(u).equals(oldUuid32(u)), "uuid32 " + u);
        for (int i = 0; i < 20_000; i++) {
            UUID u = i % 2 == 0 ? UUID.randomUUID() : new UUID(r.nextLong(), r.nextLong());
            check(FastIds.uuid32(u).equals(oldUuid32(u)), "uuid32 " + u);
            check(FastIds.uuid32Uncached(u).equals(oldUuid32(u)), "uuid32Uncached " + u);
            // zweiter Aufruf kommt aus dem Cache und liefert denselben Wert
            check(FastIds.uuid32(u).equals(oldUuid32(u)), "uuid32 (cache) " + u);
        }
    }

    private static void cleanUsernameEquality() {
        String[] names = {
                null, "", " ", "Notch", "notch", "NOTCH", "§6Notch", "§6§lNotch§r", "[VIP] Notch", "§c[Admin] §fBame_123",
                "  spaced  ", "a b c", "§", "§§", "§x", "§zName", "Name§", "§6", "Ünïcödé", "İstanbul", "x §4y", "\tTab\t",
                "§AUpper§KCase§OCodes§R", "Multi  Space  Name", "trailing ", " leading"
        };
        for (String n : names) {
            check(java.util.Objects.equals(FastIds.cleanUsername(n), oldCleanUsername(n)), "cleanUsername '" + n + "'");
        }
        Random r = new Random(7);
        String alphabet = "abcXYZ019_ §6klmorKLMORzZ[]";
        for (int i = 0; i < 20_000; i++) {
            int len = r.nextInt(24);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < len; j++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
            String n = sb.toString();
            check(FastIds.cleanUsername(n).equals(oldCleanUsername(n)), "cleanUsername '" + n + "'");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Badge-Entscheidung: Nachbau der Map-Logik aus CaeserPlayerSyncManager (ohne Minecraft)
    // ------------------------------------------------------------------------------------------------------------

    private static final class World {
        final Map<String, String> cosmetics = new ConcurrentHashMap<>(); // uuid32 -> tabBadge
        final Set<String> caeserPlayers = ConcurrentHashMap.newKeySet();
        final Map<String, Boolean> emotes = new ConcurrentHashMap<>();
        UUID local;
        boolean enabled = true;
        // neue Variante: lokale UUID zwischengespeichert
        Object[] localCache = new Object[2];

        static String symbolForRemote(String remoteBadge) {
            if (remoteBadge != null && "none".equalsIgnoreCase(remoteBadge.trim())) return null;
            return "§6§lC §r";
        }

        // alt: isCaeserPlayer(uuid, name) && getPlayerBadgePrefix(uuid, name) != null
        boolean oldShows(UUID uuid) {
            String key = oldUuid32(uuid);
            boolean isCaeser = key.equals(oldUuid32(local))
                    || caeserPlayers.contains(key) || cosmetics.containsKey(key) || emotes.containsKey(key);
            if (!isCaeser) return false;
            if (!enabled) return false;
            String key2 = oldUuid32(uuid);
            String badge = key2.equals(oldUuid32(local)) ? (enabled ? "§6§lC §r" : null) : symbolForRemote(cosmetics.get(key2));
            return badge != null && !badge.isEmpty();
        }

        String localUuid32() {
            Object[] c = localCache;
            if (c[0] == local || local.equals(c[0])) return (String) c[1];
            String hex = FastIds.uuid32(local);
            localCache = new Object[]{local, hex};
            return hex;
        }

        // neu: CaeserPlayerSyncManager.showsTabBadge
        boolean newShows(UUID uuid) {
            if (!enabled) return false;
            String key = FastIds.uuid32(uuid);
            if (key.equals(localUuid32())) return true;
            String data = cosmetics.get(key);
            if (data == null && !caeserPlayers.contains(key) && !emotes.containsKey(key)) return false;
            return symbolForRemote(data) != null;
        }
    }

    private static World world(int players, long seed) {
        Random r = new Random(seed);
        World w = new World();
        w.local = new UUID(r.nextLong(), r.nextLong());
        String[] badges = {"c_bold", "none", "c", "crown", "NONE", " none ", "fire"};
        for (int i = 0; i < players; i++) {
            UUID u = new UUID(r.nextLong(), r.nextLong());
            String k = oldUuid32(u);
            switch (i % 5) {
                case 0 -> w.cosmetics.put(k, badges[r.nextInt(badges.length)]);
                case 1 -> w.caeserPlayers.add(k);
                case 2 -> w.emotes.put(k, true);
                case 3 -> { w.caeserPlayers.add(k); w.cosmetics.put(k, badges[r.nextInt(badges.length)]); }
                default -> { } // kein Caeser-Spieler
            }
        }
        return w;
    }

    private static UUID[] entries(World w, int n, long seed) {
        Random r = new Random(seed);
        UUID[] out = new UUID[n];
        Map<String, UUID> known = new HashMap<>();
        // Bekannte Spieler über ihre Schlüssel rekonstruieren
        for (String k : w.cosmetics.keySet()) known.put(k, parse(k));
        for (String k : w.caeserPlayers) known.put(k, parse(k));
        for (String k : w.emotes.keySet()) known.put(k, parse(k));
        UUID[] pool = known.values().toArray(new UUID[0]);
        for (int i = 0; i < n; i++) {
            if (i == 0) out[i] = w.local;
            else if (i % 3 == 0 || pool.length == 0) out[i] = new UUID(r.nextLong(), r.nextLong());
            else out[i] = pool[r.nextInt(pool.length)];
        }
        return out;
    }

    private static UUID parse(String hex32) {
        return new UUID(Long.parseUnsignedLong(hex32.substring(0, 16), 16), Long.parseUnsignedLong(hex32.substring(16), 16));
    }

    private static void badgeDecisionEquality() {
        for (long seed = 1; seed <= 20; seed++) {
            World w = world(150, seed);
            UUID[] list = entries(w, 100, seed * 31);
            for (boolean enabled : new boolean[]{true, false}) {
                w.enabled = enabled;
                for (UUID u : list) {
                    check(w.oldShows(u) == w.newShows(u), "badge decision seed " + seed + " enabled " + enabled + " " + u);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------------------

    private static volatile long sink;

    private static void benchmark() {
        World w = world(150, 99);
        UUID[] list = entries(w, 100, 1234);
        String[] names = new String[100];
        for (int i = 0; i < names.length; i++) names[i] = (i % 4 == 0 ? "§6[VIP] " : "") + "Player_" + i;
        int frames = 1000;

        for (int round = 0; round < 6; round++) { // Aufwärmen (JIT)
            runOld(w, list, names, frames / 4);
            runNew(w, list, names, frames / 4);
        }
        long tOld = Long.MAX_VALUE, tNew = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            long a = System.nanoTime();
            runOld(w, list, names, frames);
            long b = System.nanoTime();
            runNew(w, list, names, frames);
            long c = System.nanoTime();
            tOld = Math.min(tOld, b - a);
            tNew = Math.min(tNew, c - b);
        }
        double perFrameOld = tOld / 1000.0 / frames;
        double perFrameNew = tNew / 1000.0 / frames;
        System.out.printf(Locale.ROOT,
                "Tab-Liste 100 Eintraege x %d Frames (Badge-Entscheidung + cleanUsername + Cape-uuid32): alt %.1f ms (%.2f us/Frame), neu %.1f ms (%.2f us/Frame), Faktor %.1fx%n",
                frames, tOld / 1e6, perFrameOld, tNew / 1e6, perFrameNew, (double) tOld / Math.max(1, tNew));

        // Allokation pro Frame (ThreadMXBean, nur dieser Thread)
        java.lang.management.ThreadMXBean base = java.lang.management.ManagementFactory.getThreadMXBean();
        if (base instanceof com.sun.management.ThreadMXBean tmx && tmx.isThreadAllocatedMemorySupported()) {
            tmx.setThreadAllocatedMemoryEnabled(true);
            long tid = Thread.currentThread().threadId();
            for (int warm = 0; warm < 3; warm++) runRound1(w, list, names, frames / 4);
            long a = tmx.getThreadAllocatedBytes(tid);
            runOld(w, list, names, frames);
            long b = tmx.getThreadAllocatedBytes(tid);
            runRound1(w, list, names, frames);
            long r1 = tmx.getThreadAllocatedBytes(tid);
            runNew(w, list, names, frames);
            long c = tmx.getThreadAllocatedBytes(tid);
            System.out.printf(Locale.ROOT,
                    "Tab-Liste Allokation pro Frame (100 Eintraege): original %.0f B, vorher (Runde 1, ohne uuid32-Cache) %.0f B, jetzt %.0f B%n",
                    (b - a) / (double) frames, (r1 - b) / (double) frames, (c - r1) / (double) frames);
            check(c - r1 <= r1 - b, "neue Variante allokiert nicht mehr als vorher");
        }
    }

    private static void runOld(World w, UUID[] list, String[] names, int frames) {
        long acc = 0;
        for (int f = 0; f < frames; f++) {
            for (int i = 0; i < list.length; i++) {
                if (w.oldShows(list[i])) acc++;
                acc += oldCleanUsername(names[i]).length();
                acc += oldUuid32(list[i]).length(); // Cosmetics-/Emote-Lookup im Renderer (resolveKey)
            }
        }
        sink += acc;
    }

    private static void runNew(World w, UUID[] list, String[] names, int frames) {
        long acc = 0;
        for (int f = 0; f < frames; f++) {
            for (int i = 0; i < list.length; i++) {
                if (w.newShows(list[i])) acc++;
                acc += FastIds.cleanUsername(names[i]).length();
                acc += FastIds.uuid32(list[i]).length();
            }
        }
        sink += acc;
    }

    /** Stand nach Runde 1: FastIds ohne Cache (eine Allokation pro Umwandlung). */
    private static void runRound1(World w, UUID[] list, String[] names, int frames) {
        long acc = 0;
        for (int f = 0; f < frames; f++) {
            for (int i = 0; i < list.length; i++) {
                String key = FastIds.uuid32Uncached(list[i]);
                if (w.enabled && (key.equals(w.localUuid32()) || w.cosmetics.containsKey(key) || w.caeserPlayers.contains(key))) acc++;
                acc += FastIds.cleanUsername(names[i]).length();
                acc += FastIds.uuid32Uncached(list[i]).length();
            }
        }
        sink += acc;
    }
}
