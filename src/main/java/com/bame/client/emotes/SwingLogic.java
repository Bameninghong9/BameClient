package com.bame.client.emotes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

/**
 * Geometrie und Sitzplatz-Regeln der Schaukel (ohne Minecraft-Klassen, im swingPicnicTest geprüft).
 * <p>
 * Koordinaten im <b>Schaukel-Rahmen</b> S (Blöcke, gleiche Achsen wie {@link HammockLogic}): Ursprung an den Füßen des
 * Spielers, der das Gerüst aufgebaut hat (beim Start, Boden), +Y oben, +X nach rechts und +Z nach hinten bezogen auf
 * seinen Körper-Yaw beim Start. Sitz 0 hängt genau dort, wo er stand; Sitz 1 hängt {@link #SEAT_SPACING} rechts daneben.
 * Geschaukelt wird nach vorne/hinten (um die X-Achse durch die Seilhaken unter dem Balken).
 * <p>
 * Sitzplatz-Regeln (jeder Client wendet sie auf dieselben Daten an, keine eigenen Nachrichten): Wer SWING startet und
 * höchstens {@link #JOIN_RADIUS} Blöcke (waagerecht, Höhe ±{@link #MAX_DY}) von einem freien Sitz eines vorhandenen
 * Gerüsts entfernt ist, setzt sich dorthin; sonst entsteht ein neues Gerüst. Höchstens zwei Spieler pro Gerüst. Das
 * Gerüst bleibt stehen, solange einer der beiden schaukelt; Besitzer (zeichnet Gerüst und freie Sitze) ist der mit dem
 * frühesten Start (UUID bei Gleichstand).
 */
public final class SwingLogic {

    private SwingLogic() {}

    // ------------------------------------------------------------------
    // Gerüst (Blöcke)
    // ------------------------------------------------------------------

    /** Abstand der beiden Sitze (Mitte zu Mitte) und Mitte des Gerüsts (x). */
    public static final double SEAT_SPACING = 1.1;
    public static final double CENTER_X = SEAT_SPACING / 2.0;
    /** A-Beine: Fuß so weit links/rechts der Gerüstmitte (leicht nach außen gespreizt) und vor/hinter dem Balken. */
    public static final double LEG_FOOT_X = 1.5, LEG_TOP_X = 1.36, LEG_FOOT_Z = 0.95;
    /** Halbe Dicke der Beine (Blöcke, Modell 3 px). */
    public static final double LEG_HALF = 0.09;
    /** Balken: Mitte auf dieser Höhe, halbe Dicke, halbe Länge (Blöcke). */
    public static final double BEAM_Y = 2.72, BEAM_HALF = 0.11, BEAM_HALF_LEN = 1.58;
    /** Schaukelachse = Seilhaken unter dem Balken. */
    public static final double PIVOT_Y = BEAM_Y - BEAM_HALF - 0.04;
    /** Seile: so weit links/rechts der Sitzmitte (= Handmitte des Spielermodells, 6 px · 0,9375 / 16). */
    public static final double ROPE_X = 0.35;
    /** Sitzbrett: Oberseite in Ruhe, halbe Breite (x), halbe Tiefe (z), Dicke. */
    public static final double SEAT_TOP_Y = 0.58, SEAT_HALF_W = 0.42, SEAT_HALF_D = 0.17, SEAT_THICK = 0.07;
    /** Seillänge (Haken bis Sitz-Oberseite). */
    public static final double ROPE_LENGTH = PIVOT_Y - SEAT_TOP_Y;

    /** Mitte von Sitz {@code seat} (0 oder 1) im Rahmen (x). */
    public static double seatX(int seat) {
        return seat * SEAT_SPACING;
    }

    /** Punkt (x, y, z) im Rahmen um die Schaukelachse drehen (Grad, positiv = Sitz schwingt nach vorne, -Z). */
    public static double[] swing(double x, double y, double z, double deg, double[] out) {
        double a = Math.toRadians(deg);
        double c = Math.cos(a), s = Math.sin(a);
        double dy = y - PIVOT_Y;
        out[0] = x;
        out[1] = PIVOT_Y + dy * c - z * s;
        out[2] = dy * s + z * c;
        return out;
    }

    // ------------------------------------------------------------------
    // Platzprüfung
    // ------------------------------------------------------------------

    /** Grundfläche (x) des Gerüsts: Beine links, beide Sitze, Mitte, Beine rechts. */
    private static final double[] FOOT_X = {CENTER_X - LEG_FOOT_X, 0.0, CENTER_X, SEAT_SPACING, CENTER_X + LEG_FOOT_X};
    private static final double[] FOOT_Z = {-LEG_FOOT_Z, 0.0, LEG_FOOT_Z};
    private static final double[] BOX_HEIGHTS = {0.05, 1.0, 2.0, BEAM_Y};
    /** Schwungbereich vor/hinter den Sitzen auf Sitz- und Kopfhöhe. */
    public static final double ARC_Z = 1.5;
    private static final double[] ARC_HEIGHTS = {0.9, 1.6};

    /**
     * Prüfpunkte im Rahmen: {x, y, z, needsGround}. An jedem Punkt muss Luft sein (keine Kollision, keine Flüssigkeit);
     * unter den vier Bein-Füßen ({@code needsGround = 1}) ein fester Block. Gedeckt: Gerüst (ca. 3 breit, 2 tief, 3 hoch)
     * und der Schwungbereich ±{@link #ARC_Z} vor/hinter beiden Sitzen.
     */
    public static double[][] checkPointsFrame() {
        List<double[]> out = new ArrayList<>();
        for (double x : FOOT_X) {
            boolean leg = Math.abs(Math.abs(x - CENTER_X) - LEG_FOOT_X) < 1.0E-9;
            for (double z : FOOT_Z) {
                for (double h : BOX_HEIGHTS) {
                    boolean ground = leg && Math.abs(z) > 0.5 && h < 0.5;
                    out.add(new double[]{x, h, z, ground ? 1.0 : 0.0});
                }
            }
        }
        for (int seat = 0; seat < 2; seat++) {
            for (double z : new double[]{-ARC_Z, ARC_Z}) {
                for (double h : ARC_HEIGHTS) out.add(new double[]{seatX(seat), h, z, 0.0});
            }
        }
        return out.toArray(new double[0][]);
    }

    /** Prüfpunkte in Weltkoordinaten ({@link #checkPointsFrame} am Startpunkt mit Start-Yaw). */
    public static double[][] checkPoints(double x, double y, double z, float yaw) {
        double[][] f = checkPointsFrame();
        double[][] out = new double[f.length][];
        for (int i = 0; i < f.length; i++) {
            double[] w = HammockLogic.frameToWorld(f[i][0], f[i][1], f[i][2], x, y, z, yaw);
            out[i] = new double[]{w[0], w[1], w[2], f[i][3]};
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Sitzplätze
    // ------------------------------------------------------------------

    /** Freier Sitz in diesem Umkreis (waagerecht, nur beim eigenen Start geprüft): hinsetzen. */
    public static final double JOIN_RADIUS = 2.0;
    /** Andere Clients sehen die Startposition leicht anders: großzügiger, damit sie dieselbe Zuordnung treffen. */
    public static final double JOIN_RADIUS_SYNC = 2.3;
    /** Höchster Höhenunterschied Spieler–Sitz (Blöcke). */
    public static final double MAX_DY = 0.8;
    /** Freier Sitz näher als das, aber weiter als {@link #JOIN_RADIUS}: „Geh näher an die Schaukel“. */
    public static final double NEAR_RADIUS = 3.5;
    /** Zwei Gerüste dürfen sich nicht überschneiden: Mindestabstand der Mitten (waagerecht). */
    public static final double FRAME_CLEARANCE = 3.2;

    /** Ein Spieler mit aktivem Schaukel-Emote. {@code yaw}: Körper-Yaw beim Start (bzw. beim ersten Sehen). */
    public record Participant(String id, long startMs, double x, double y, double z, float yaw) {}

    /** Ein Spieler auf einem Sitz. Position/Yaw: seine Füße und sein Körper-Yaw beim Start. */
    public static final class Member {
        public final String id;
        public final long startMs;
        public final double x, y, z;
        public final float startYaw;
        public final int seat;

        Member(Participant p, int seat) {
            this.id = p.id();
            this.startMs = p.startMs();
            this.x = p.x();
            this.y = p.y();
            this.z = p.z();
            this.startYaw = p.yaw();
            this.seat = seat;
        }

        boolean sameEmote(Participant p) {
            return p != null && p.id().equals(id) && p.startMs() == startMs;
        }
    }

    /** Ein Schaukelgerüst. Position und Ausrichtung werden beim Entstehen festgehalten und ändern sich nicht mehr. */
    public static final class SwingSet {
        public final double x, y, z;
        public final float yaw;
        /** Startzeit des Spielers, der es aufgebaut hat (zum Einblenden). */
        public final long createdStartMs;
        public final Member[] seats = new Member[2];

        SwingSet(double x, double y, double z, float yaw, long createdStartMs) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.createdStartMs = createdStartMs;
        }

        /** Besitzer: frühester Start, bei Gleichstand die kleinere UUID; {@code null} ohne Spieler. */
        public Member owner() {
            Member best = null;
            for (Member m : seats) {
                if (m == null) continue;
                if (best == null || m.startMs < best.startMs || (m.startMs == best.startMs && m.id.compareTo(best.id) < 0)) {
                    best = m;
                }
            }
            return best;
        }

        public boolean isEmpty() {
            return seats[0] == null && seats[1] == null;
        }

        public int memberCount() {
            return (seats[0] != null ? 1 : 0) + (seats[1] != null ? 1 : 0);
        }

        /** Welt-Position (Boden) der Mitte von Sitz {@code seat}. */
        public double[] seatWorld(int seat) {
            return HammockLogic.frameToWorld(seatX(seat), 0.0, 0.0, x, y, z, yaw);
        }

        /** Welt-Position (Boden) der Gerüstmitte. */
        public double[] centerWorld() {
            return HammockLogic.frameToWorld(CENTER_X, 0.0, 0.0, x, y, z, yaw);
        }

        public Member memberById(String id) {
            for (Member m : seats) if (m != null && m.id.equals(id)) return m;
            return null;
        }
    }

    /** Ergebnis von {@link #update}: neue und aufgelöste Gerüste, Spieler, die aufgestanden sind. */
    public static final class Changes {
        public final List<SwingSet> created = new ArrayList<>();
        public final List<SwingSet> removed = new ArrayList<>();
        public final List<Member> left = new ArrayList<>();
        /** Gerüst, von dem der Spieler mit gleichem Index in {@link #left} aufgestanden ist. */
        public final List<SwingSet> leftFrom = new ArrayList<>();
    }

    /** Freier Sitz eines Gerüsts mit Abstand. */
    public record FreeSeat(SwingSet set, int seat, double distance) {}

    private static final Comparator<SwingSet> SET_ORDER = Comparator.comparingLong((SwingSet s) -> s.createdStartMs)
            .thenComparingDouble(s -> s.x).thenComparingDouble(s -> s.z);

    /**
     * Nächster freier Sitz höchstens {@code radius} (waagerecht) und {@link #MAX_DY} (senkrecht) entfernt. Gleicher
     * Abstand: älteres Gerüst, dann Sitz 0.
     *
     * @param ignoreId Gerüste, deren einziger Spieler dieser ist, zählen nicht (eigenes, altes Emote)
     */
    public static FreeSeat nearestFreeSeat(List<SwingSet> sets, double x, double y, double z, double radius,
                                           String ignoreId) {
        FreeSeat best = null;
        for (SwingSet s : sets) {
            if (s.isEmpty()) continue;
            if (ignoreId != null && s.memberCount() == 1 && s.memberById(ignoreId) != null) continue;
            if (Math.abs(y - s.y) > MAX_DY) continue;
            for (int seat = 0; seat < 2; seat++) {
                if (s.seats[seat] != null) continue;
                double[] w = s.seatWorld(seat);
                double d = Math.hypot(x - w[0], z - w[2]);
                if (d > radius + 1.0E-9) continue;
                if (best == null || d < best.distance() - 1.0E-9
                        || (Math.abs(d - best.distance()) <= 1.0E-9 && SET_ORDER.compare(s, best.set()) < 0)) {
                    best = new FreeSeat(s, seat, d);
                }
            }
        }
        return best;
    }

    public enum StartResult {
        /** Freier Sitz in Reichweite: hinsetzen, kein neues Gerüst. */
        JOIN,
        /** Neues Gerüst (Platz in der Welt prüft der Aufrufer). */
        NEW,
        /** Freier Sitz in der Nähe, aber weiter als {@link #JOIN_RADIUS}. */
        TOO_FAR,
        /** Gerüst nebenan ist voll und ein neues würde es überschneiden. */
        OCCUPIED
    }

    /** Prüfung beim eigenen Start (der Aufrufer prüft bei {@link StartResult#NEW} noch den Platz in der Welt). */
    public static StartResult checkStart(List<SwingSet> sets, String selfId, double x, double y, double z, float yaw) {
        FreeSeat free = nearestFreeSeat(sets, x, y, z, NEAR_RADIUS, selfId);
        if (free != null) return free.distance() <= JOIN_RADIUS + 1.0E-9 ? StartResult.JOIN : StartResult.TOO_FAR;
        if (overlapsExisting(sets, selfId, x, y, z, yaw)) return StartResult.OCCUPIED;
        return StartResult.NEW;
    }

    /** Steht ein volles Gerüst (beide Sitze belegt) mit einem Sitz innerhalb von {@link #JOIN_RADIUS} hier? */
    public static boolean nearFullSet(List<SwingSet> sets, double x, double y, double z) {
        for (SwingSet s : sets) {
            if (s.memberCount() < 2 || Math.abs(y - s.y) > MAX_DY) continue;
            for (int seat = 0; seat < 2; seat++) {
                double[] w = s.seatWorld(seat);
                if (Math.hypot(x - w[0], z - w[2]) <= JOIN_RADIUS + 1.0E-9) return true;
            }
        }
        return false;
    }

    /** Würde ein neues Gerüst an dieser Stelle ein vorhandenes (anderer Spieler) überschneiden? */
    public static boolean overlapsExisting(List<SwingSet> sets, String selfId, double x, double y, double z, float yaw) {
        double[] c = HammockLogic.frameToWorld(CENTER_X, 0.0, 0.0, x, y, z, yaw);
        for (SwingSet s : sets) {
            if (s.isEmpty()) continue;
            if (selfId != null && s.memberCount() == 1 && s.memberById(selfId) != null) continue;
            if (Math.abs(y - s.y) > 2.5) continue;
            double[] o = s.centerWorld();
            if (Math.hypot(c[0] - o[0], c[2] - o[2]) < FRAME_CLEARANCE) return true;
        }
        return false;
    }

    /**
     * Gerüste fortschreiben (einmal pro Tick, auf jedem Client gleich):
     * <ol>
     *   <li>Spieler, deren Emote (UUID + Startzeit) nicht mehr läuft, stehen auf; ihr Sitz wird frei.</li>
     *   <li>Gerüste ohne Spieler verschwinden.</li>
     *   <li>Neue Spieler in der Reihenfolge Startzeit, dann UUID: freier Sitz innerhalb von
     *       {@link #JOIN_RADIUS_SYNC} → hinsetzen, sonst neues Gerüst (Sitz 0 an ihrer Position).</li>
     * </ol>
     */
    public static Changes update(List<SwingSet> sets, Collection<Participant> active) {
        Changes changes = new Changes();
        TreeMap<String, Participant> byId = new TreeMap<>();
        for (Participant p : active) byId.put(p.id(), p);

        java.util.Set<String> assigned = new java.util.HashSet<>();
        for (Iterator<SwingSet> it = sets.iterator(); it.hasNext(); ) {
            SwingSet s = it.next();
            for (int i = 0; i < 2; i++) {
                Member m = s.seats[i];
                if (m != null && !m.sameEmote(byId.get(m.id))) {
                    s.seats[i] = null;
                    changes.left.add(m);
                    changes.leftFrom.add(s);
                }
            }
            if (s.isEmpty()) {
                it.remove();
                changes.removed.add(s);
                continue;
            }
            for (Member m : s.seats) if (m != null) assigned.add(m.id);
        }

        List<Participant> fresh = new ArrayList<>();
        for (Participant p : byId.values()) if (!assigned.contains(p.id())) fresh.add(p);
        fresh.sort(Comparator.comparingLong(Participant::startMs).thenComparing(Participant::id));
        for (Participant p : fresh) {
            FreeSeat free = nearestFreeSeat(sets, p.x(), p.y(), p.z(), JOIN_RADIUS_SYNC, null);
            if (free != null) {
                free.set().seats[free.seat()] = new Member(p, free.seat());
            } else {
                SwingSet created = new SwingSet(p.x(), p.y(), p.z(), p.yaw(), p.startMs());
                created.seats[0] = new Member(p, 0);
                sets.add(created);
                changes.created.add(created);
            }
        }
        return changes;
    }

    /** Gerüst, auf dem dieser Spieler mit diesem Emote (Startzeit) sitzt, oder {@code null}. */
    public static SwingSet setOf(List<SwingSet> sets, String id, long startMs) {
        for (SwingSet s : sets) {
            Member m = s.memberById(id);
            if (m != null && m.startMs == startMs) return s;
        }
        return null;
    }
}
