package com.bame.client.emotes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * Reine Rechenregeln des Lagerfeuers (ohne Minecraft-Klassen, im plannerTest geprüft): Feuerposition, Lagerfeuer-Runden
 * (Zuordnung, Gastgeber, Auflösen) und die zufällige Variante pro Durchgang. Jeder Client wendet dieselben Regeln auf
 * dieselben Daten an (Emote-Startzeiten vom Sync-Server, Positionen von Minecraft); es gibt keine eigenen Nachrichten.
 * <p>
 * Koordinaten: Welt in Blöcken; Yaw wie Minecraft (0 = Süden/+Z, 90 = Westen/-X).
 */
public final class CampfireLogic {

    private CampfireLogic() {}

    /** Feuer so weit vor dem Spieler (Blöcke, waagerecht). */
    public static final double FIRE_DISTANCE = 1.3;
    /** Ein vorhandenes Feuer in diesem Umkreis (waagerecht) wird mitbenutzt statt ein neues zu entzünden. */
    public static final double JOIN_RADIUS = 3.0;
    /** Weiter weg reicht der Stock nicht (nur beim eigenen Start geprüft; andere Clients sind großzügiger). */
    public static final double MAX_REACH = 2.2;
    /** Höchster Höhenunterschied zwischen Spieler und Feuer für eine gemeinsame Runde (Blöcke). */
    public static final double MAX_DY = 1.5;
    /** Anteil der Durchgänge, in denen der Marshmallow verbrennt (Variante B). */
    public static final double BURN_CHANCE = 0.3;
    /** Ab so vielen Teilnehmern brennt das Feuer größer. */
    public static final int BIG_FIRE_MEMBERS = 3;

    /** Ein Spieler mit aktivem Lagerfeuer-Emote. {@code yaw}: Körper-Yaw beim Start (bzw. beim ersten Sehen). */
    public record Participant(String id, long startMs, double x, double y, double z, float yaw) {}

    /** Ein Teilnehmer einer Runde. {@code anchorYaw}: Blickrichtung zum Feuer, {@code startYaw}: Körper-Yaw beim Start. */
    public static final class Member {
        public final String id;
        public final long startMs;
        public final double x, y, z;
        public final float startYaw;
        public final float anchorYaw;

        Member(Participant p, float anchorYaw) {
            this.id = p.id();
            this.startMs = p.startMs();
            this.x = p.x();
            this.y = p.y();
            this.z = p.z();
            this.startYaw = p.yaw();
            this.anchorYaw = anchorYaw;
        }

        boolean sameEmote(Participant p) {
            return p != null && p.id().equals(id) && p.startMs() == startMs;
        }
    }

    /**
     * Eine Lagerfeuer-Runde. Die Feuerposition wird beim Entstehen festgehalten und ändert sich danach nicht mehr.
     * {@code fireYaw}: Ausrichtung des Feuer-Modells (Blickrichtung des ersten Gastgebers), {@code createdStartMs}:
     * Startzeit dieses ersten Gastgebers (zum Einblenden des Feuers).
     */
    public static final class Round {
        public final double fireX, fireY, fireZ;
        public final float fireYaw;
        public final long createdStartMs;
        /** Teilnehmer nach UUID sortiert (keine Abhängigkeit von der Reihenfolge, in der sie gesehen wurden). */
        public final TreeMap<String, Member> members = new TreeMap<>();

        Round(double fireX, double fireY, double fireZ, float fireYaw, long createdStartMs) {
            this.fireX = fireX;
            this.fireY = fireY;
            this.fireZ = fireZ;
            this.fireYaw = fireYaw;
            this.createdStartMs = createdStartMs;
        }

        /** Gastgeber: frühester Start, bei Gleichstand die kleinere UUID. {@code null} ohne Teilnehmer. */
        public Member host() {
            Member best = null;
            for (Member m : members.values()) {
                if (best == null || m.startMs < best.startMs || (m.startMs == best.startMs && m.id.compareTo(best.id) < 0)) {
                    best = m;
                }
            }
            return best;
        }

        public boolean isBig() {
            return members.size() >= BIG_FIRE_MEMBERS;
        }

        public double horizontalDistanceTo(double x, double z) {
            return Math.hypot(x - fireX, z - fireZ);
        }
    }

    /** Ergebnis von {@link #update}: neu entstandene und aufgelöste Runden. */
    public static final class Changes {
        public final List<Round> created = new ArrayList<>();
        public final List<Round> removed = new ArrayList<>();
    }

    // ------------------------------------------------------------------
    // Geometrie
    // ------------------------------------------------------------------

    /** Feuerposition {x, y, z} 1,3 Blöcke vor den Füßen in Richtung {@code yaw}. */
    public static double[] firePosition(double x, double y, double z, float yaw) {
        double r = Math.toRadians(yaw);
        return new double[]{x - Math.sin(r) * FIRE_DISTANCE, y, z + Math.cos(r) * FIRE_DISTANCE};
    }

    /** Yaw (Grad, Minecraft-Konvention), mit dem man von (x, z) auf (tx, tz) schaut. */
    public static float yawTo(double x, double z, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
    }

    public static double wrapDegrees(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    // ------------------------------------------------------------------
    // Runden
    // ------------------------------------------------------------------

    /**
     * Nächste Runde, deren Feuer höchstens {@code radius} (waagerecht) und {@link #MAX_DY} (senkrecht) entfernt ist.
     * Gleicher Abstand: früher entstandene Runde (Startzeit des ersten Gastgebers), dann Feuerposition.
     *
     * @param ignoreId Runden, deren einziger Teilnehmer dieser Spieler ist, zählen nicht (eigenes, altes Emote)
     */
    public static Round nearestRound(List<Round> rounds, double x, double y, double z, double radius, String ignoreId) {
        Round best = null;
        double bestD = Double.MAX_VALUE;
        for (Round r : rounds) {
            if (ignoreId != null && r.members.size() == 1 && r.members.containsKey(ignoreId)) continue;
            if (r.members.isEmpty()) continue;
            if (Math.abs(y - r.fireY) > MAX_DY) continue;
            double d = r.horizontalDistanceTo(x, z);
            if (d > radius + 1.0E-9) continue;
            if (best == null || d < bestD - 1.0E-9 || (Math.abs(d - bestD) <= 1.0E-9 && ROUND_ORDER.compare(r, best) < 0)) {
                best = r;
                bestD = d;
            }
        }
        return best;
    }

    private static final Comparator<Round> ROUND_ORDER = Comparator.comparingLong((Round r) -> r.createdStartMs)
            .thenComparingDouble(r -> r.fireX).thenComparingDouble(r -> r.fireZ);

    public enum StartResult { NEW_FIRE, JOIN, TOO_FAR }

    /** Prüfung beim eigenen Start: neues Feuer, ans vorhandene setzen, oder zu weit weg (Stock reicht nicht). */
    public static StartResult checkStart(List<Round> rounds, String selfId, double x, double y, double z) {
        Round r = nearestRound(rounds, x, y, z, JOIN_RADIUS, selfId);
        if (r == null) return StartResult.NEW_FIRE;
        return r.horizontalDistanceTo(x, z) <= MAX_REACH + 1.0E-9 ? StartResult.JOIN : StartResult.TOO_FAR;
    }

    /**
     * Runden fortschreiben (einmal pro Tick, auf jedem Client gleich):
     * <ol>
     *   <li>Teilnehmer, deren Emote (UUID + Startzeit) nicht mehr läuft, verlassen ihre Runde.</li>
     *   <li>Runden ohne Teilnehmer lösen sich auf (Feuer verschwindet).</li>
     *   <li>Neue Teilnehmer in der Reihenfolge Startzeit, dann UUID: Ist ein Feuer im Umkreis von 3 Blöcken, setzen sie
     *       sich dazu (Blick zum Feuer), sonst entsteht eine neue Runde mit Feuer 1,3 Blöcke vor ihnen.</li>
     * </ol>
     * Eine Zuordnung bleibt bestehen, bis das Emote endet; der Gastgeber ({@link Round#host()}) wird jedes Mal neu
     * bestimmt, daher übernimmt nach dem Aufstehen des Gastgebers der nächste nach Startzeit.
     */
    public static Changes update(List<Round> rounds, Collection<Participant> active) {
        Changes changes = new Changes();
        TreeMap<String, Participant> byId = new TreeMap<>();
        for (Participant p : active) byId.put(p.id(), p);

        // 1. + 2. Beendete Emotes entfernen, leere Runden auflösen
        java.util.Set<String> assigned = new java.util.HashSet<>();
        for (Iterator<Round> it = rounds.iterator(); it.hasNext(); ) {
            Round r = it.next();
            r.members.values().removeIf(m -> !m.sameEmote(byId.get(m.id)));
            if (r.members.isEmpty()) {
                it.remove();
                changes.removed.add(r);
                continue;
            }
            assigned.addAll(r.members.keySet());
        }

        // 3. Neue Teilnehmer zuordnen
        List<Participant> fresh = new ArrayList<>();
        for (Participant p : byId.values()) {
            if (!assigned.contains(p.id())) fresh.add(p);
        }
        fresh.sort(Comparator.comparingLong(Participant::startMs).thenComparing(Participant::id));
        for (Participant p : fresh) {
            Round r = nearestRound(rounds, p.x(), p.y(), p.z(), JOIN_RADIUS, null);
            if (r != null) {
                r.members.put(p.id(), new Member(p, yawTo(p.x(), p.z(), r.fireX, r.fireZ)));
            } else {
                double[] f = firePosition(p.x(), p.y(), p.z(), p.yaw());
                Round created = new Round(f[0], f[1], f[2], p.yaw(), p.startMs());
                created.members.put(p.id(), new Member(p, p.yaw()));
                rounds.add(created);
                changes.created.add(created);
            }
        }
        return changes;
    }

    /** Runde, in der dieser Spieler mit diesem Emote (Startzeit) sitzt, oder {@code null}. */
    public static Round roundOf(List<Round> rounds, String id, long startMs) {
        for (Round r : rounds) {
            Member m = r.members.get(id);
            if (m != null && m.startMs == startMs) return r;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Zufall pro Durchgang
    // ------------------------------------------------------------------

    /**
     * Rundung der Zeitbasis für den Zufall (ms). Alle Clients bekommen inzwischen dieselbe exakte Serverzeit (Beobachter
     * aus der emote-Nachricht, der Spieler selbst aus deren Echo); deren Rundung ist überall gleich. Gerundet wird nur
     * wegen der Rückfälle ohne exakte Zeit (eigenes Emote bis zum Echo bzw. mit älterem Server, LAN): Eine Schätzung, die
     * um die Laufzeit (typisch 20–150 ms) daneben liegt, landet mit 1-s-Raster fast immer im selben Wert (mit 100 ms
     * wich sie oft ab).
     */
    public static final long SEED_ROUNDING_MS = 1_000L;

    /** Zeitbasis auf {@link #SEED_ROUNDING_MS} gerundet (gemeinsamer Teil des Zufalls). */
    public static long roundedStart(long startMs) {
        return Math.floorDiv(startMs + SEED_ROUNDING_MS / 2L, SEED_ROUNDING_MS);
    }

    /**
     * Verbrennt der Marshmallow in diesem Durchgang (Variante B, ca. 30 %)? Deterministisch aus UUID, gerundeter
     * gemeinsamer Zeitbasis (Serverzeit des Emote-Starts) und Durchgangsnummer; alle Clients mit denselben Eingaben
     * sehen dieselbe Variante.
     */
    public static boolean burns(String id, long startMs, int pass) {
        return unit(seed(id, startMs, pass)) < BURN_CHANCE;
    }

    /** Zahl in [0, 1) aus den Eingaben (gleichmäßig verteilt). */
    public static double unit(long seed) {
        return (mix(seed) >>> 11) * 0x1.0p-53;
    }

    public static long seed(String id, long startMs, int pass) {
        long h = 0x9E3779B97F4A7C15L;
        if (id != null) {
            for (int i = 0; i < id.length(); i++) h = (h ^ id.charAt(i)) * 0x100000001B3L;
        }
        h = mix(h ^ roundedStart(startMs) * 0xC2B2AE3D27D4EB4FL);
        return mix(h + pass * 0x165667B19E3779F9L);
    }

    /** SplitMix64-Finalizer. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
