package com.bame.client.emotes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reine Rechenregeln des High-Five (ohne Minecraft-Klassen, im plannerTest geprüft): Paarung, Klatsch-Zeitpunkt,
 * Phasen, Zielpunkt und Armwinkel. Jeder Client wendet dieselben Regeln auf dieselben Daten an (Emote-Startzeiten vom
 * Sync-Server, Positionen von Minecraft), daher entstehen überall dieselben Paare.
 * <p>
 * Koordinaten: Welt in Blöcken; Yaw wie Minecraft (0 = Süden/+Z, 90 = Westen/-X). Modellraum in Pixeln wie
 * {@code PlayerEntityModel}: +Y nach unten, -Z nach vorne, -X zur rechten Hand des Spielers, Ursprung 24.016 px über
 * den Füßen.
 */
public final class HighFiveLogic {

    private HighFiveLogic() {}

    public static final double MIN_DIST = 0.6;
    public static final double MAX_DIST = 2.2;
    public static final double MAX_DY = 0.8;
    public static final double MAX_FACING_DEG = 45.0;
    /** WARTEN dauert höchstens so lange (ms); danach ist man kein Kandidat mehr. */
    public static final long WAIT_MS = 8_000L;
    public static final long MAX_START_DIFF_MS = 8_000L;
    public static final long CLAP_DELAY_MS = 350L;
    public static final long LATE_DELAY_MS = 250L;
    public static final long REACH_MS = 250L;
    public static final long FOLLOW_MS = 500L;
    public static final long END_MS = 800L;
    /** Ab diesem Abstand lehnt sich der Oberkörper nach vorne. */
    public static final double LEAN_FROM_DIST = 1.3;
    public static final float MAX_LEAN_DEG = 30.0F;
    /** Zielpunkt: so weit über der Schulterhöhe (Blöcke). */
    public static final double TARGET_ABOVE_SHOULDER = 0.35;

    /** Einschlagen per Taste: Klatsch so lange nach dem Start des Annehmen-Emotes (ms). */
    public static final long ACCEPT_CLAP_DELAY_MS = 300L;
    /** Hinweis [F] nur, solange der Wartende noch mindestens so lange wartet (ms). */
    public static final long PROMPT_MIN_REMAINING_MS = 500L;
    /** Andere Clients prüfen das Einschlagen eines fremden Spielers etwas großzügiger (Positionen leicht verzögert). */
    public static final double ACCEPT_TOLERANCE_DIST = 0.4;
    public static final double ACCEPT_TOLERANCE_DY = 0.3;
    public static final double ACCEPT_TOLERANCE_DEG = 20.0;
    /** So lange nach dem Start sucht ein fremdes Annehmen-Emote noch sein Ziel (ms). */
    public static final long ACCEPT_MATCH_WINDOW_MS = 1_000L;

    public static final double BLOCKS_PER_PX = 0.9375 / 16.0;
    /** Modell-Ursprung (Halsansatz) über den Füßen, px. */
    public static final double MODEL_ORIGIN_UP_PX = 24.016;
    /** Rechter Schulter-Drehpunkt im Modell (breite Arme). */
    public static final double SHOULDER_X_PX = -5.0, SHOULDER_Y_PX = 2.0;

    public enum Phase { WAITING, REACH, FOLLOW, LOWER, ENDED }

    /** Ein wartender Spieler. {@code id} = UUID als 32 Hex-Zeichen (klein), {@code startMs} in lokaler Zeit. */
    public record Player(String id, double x, double y, double z, float lookYaw, float bodyYaw, long startMs) {}

    /** Ein Paar; {@code a} hat immer die kleinere ID. */
    public record Pair(Player a, Player b) {}

    // ------------------------------------------------------------------
    // Paarung
    // ------------------------------------------------------------------

    public static boolean isWaiting(long startMs, long now) {
        return now >= startMs - 1_000L && now - startMs <= WAIT_MS;
    }

    public static double horizontalDistance(Player a, Player b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    /** Blickrichtung von {@code from} zeigt mit höchstens ±45° auf {@code to}. */
    public static boolean faces(Player from, Player to) {
        return faces(from, to, MAX_FACING_DEG);
    }

    private static boolean faces(Player from, Player to, double maxDeg) {
        double dx = to.x() - from.x();
        double dz = to.z() - from.z();
        if (dx * dx + dz * dz < 1.0E-8) return false;
        double yawTo = Math.toDegrees(Math.atan2(-dx, dz));
        return Math.abs(wrapDegrees(yawTo - from.lookYaw())) <= maxDeg;
    }

    public static boolean canPair(Player a, Player b) {
        if (a.id().equals(b.id())) return false;
        double d = horizontalDistance(a, b);
        if (d < MIN_DIST || d > MAX_DIST) return false;
        if (Math.abs(a.y() - b.y()) > MAX_DY) return false;
        if (Math.abs(a.startMs() - b.startMs()) > MAX_START_DIFF_MS) return false;
        return faces(a, b) && faces(b, a);
    }

    /**
     * Gegenseitig nächste mögliche Partner. Bei gleichem Abstand gewinnt die kleinere ID. Die Reihenfolge der Eingabe
     * spielt keine Rolle (es wird nach ID sortiert); jeder Spieler kommt höchstens in einem Paar vor.
     */
    public static List<Pair> findPairs(List<Player> candidates) {
        List<Player> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(Player::id));
        List<Pair> pairs = new ArrayList<>();
        for (Player p : sorted) {
            Player best = nearest(p, sorted);
            if (best == null || p.id().compareTo(best.id()) >= 0) continue;
            Player back = nearest(best, sorted);
            if (back != null && back.id().equals(p.id())) {
                pairs.add(new Pair(p, best));
            }
        }
        return pairs;
    }

    private static Player nearest(Player p, List<Player> sorted) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player o : sorted) {
            if (!canPair(p, o)) continue;
            double d = horizontalDistance(p, o);
            // sortiert nach ID: bei gleichem Abstand bleibt der erste (kleinere ID) stehen
            if (d < bestDist) {
                best = o;
                bestDist = d;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------
    // Einschlagen per Taste (nur einer braucht das High-Five)
    // ------------------------------------------------------------------

    /**
     * Kann {@code accepter} bei {@code target} (wartet mit High-Five) einschlagen? Gleiche Abstände wie beim Paaren,
     * aber nur der Annehmende muss zum Ziel schauen. {@code tolerant}: etwas großzügiger für fremde Annehmende, deren
     * Position/Blick hier leicht verzögert ankommt (der Annehmende hat bei sich schon streng geprüft).
     */
    public static boolean canAccept(Player accepter, Player target, boolean tolerant) {
        if (accepter.id().equals(target.id())) return false;
        double tolDist = tolerant ? ACCEPT_TOLERANCE_DIST : 0.0;
        double d = horizontalDistance(accepter, target);
        if (d < MIN_DIST - tolDist || d > MAX_DIST + tolDist) return false;
        if (Math.abs(accepter.y() - target.y()) > MAX_DY + (tolerant ? ACCEPT_TOLERANCE_DY : 0.0)) return false;
        return faces(accepter, target, MAX_FACING_DEG + (tolerant ? ACCEPT_TOLERANCE_DEG : 0.0));
    }

    /** Darf der [F]-Hinweis für einen Wartenden (Start {@code startMs}) noch erscheinen? */
    public static boolean canPrompt(long startMs, long now) {
        return isWaiting(startMs, now) && now - startMs <= WAIT_MS - PROMPT_MIN_REMAINING_MS;
    }

    /**
     * Ziel des Einschlagens: der nächste Wartende (High-Five, zur Zeit {@code at} noch wartend), bei dem
     * {@link #canAccept} gilt. Gleicher Abstand: kleinere ID. Unabhängig von der Reihenfolge der Eingabe.
     *
     * @return Ziel oder {@code null}
     */
    public static Player findAcceptTarget(Player accepter, List<Player> waiting, long at, boolean tolerant) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : waiting) {
            if (!isWaiting(p.startMs(), at)) continue;
            if (!canAccept(accepter, p, tolerant)) continue;
            double d = horizontalDistance(accepter, p);
            if (best == null || d < bestDist || (d == bestDist && p.id().compareTo(best.id()) < 0)) {
                best = p;
                bestDist = d;
            }
        }
        return best;
    }

    /** Start des Annehmen-Emotes + 0,3 s; liegt das schon zurück (spät empfangen): Erkennung + 0,25 s. */
    public static long acceptClapTime(long acceptStart, long detectedAt) {
        long clap = acceptStart + ACCEPT_CLAP_DELAY_MS;
        return clap < detectedAt ? detectedAt + LATE_DELAY_MS : clap;
    }

    /** {@code max(startA, startB) + 0,35 s}; liegt das schon zurück: Erkennungszeitpunkt + 0,25 s. */
    public static long clapTime(long startA, long startB, long detectedAt) {
        long clap = Math.max(startA, startB) + CLAP_DELAY_MS;
        return clap < detectedAt ? detectedAt + LATE_DELAY_MS : clap;
    }

    public static Phase phase(long clapTime, long now) {
        if (now < clapTime - REACH_MS) return Phase.WAITING;
        if (now < clapTime) return Phase.REACH;
        if (now < clapTime + FOLLOW_MS) return Phase.FOLLOW;
        if (now < clapTime + END_MS) return Phase.LOWER;
        return Phase.ENDED;
    }

    // ------------------------------------------------------------------
    // Zielpunkt und Armwinkel
    // ------------------------------------------------------------------

    /** Rechte Schulter in Weltkoordinaten (Füße bei x/y/z, Körper-Yaw in Grad). */
    public static double[] rightShoulder(double x, double y, double z, float bodyYaw) {
        double yaw = Math.toRadians(bodyYaw);
        // rechte Hand: (-cos, 0, -sin)
        double side = -SHOULDER_X_PX * BLOCKS_PER_PX;
        double up = (MODEL_ORIGIN_UP_PX - SHOULDER_Y_PX) * BLOCKS_PER_PX;
        return new double[]{x - Math.cos(yaw) * side, y + up, z - Math.sin(yaw) * side};
    }

    /** Mittelpunkt der rechten Schultern, 0,35 Blöcke über Schulterhöhe. */
    public static double[] target(Player a, Player b) {
        double[] sa = rightShoulder(a.x(), a.y(), a.z(), a.bodyYaw());
        double[] sb = rightShoulder(b.x(), b.y(), b.z(), b.bodyYaw());
        return new double[]{(sa[0] + sb[0]) * 0.5, (sa[1] + sb[1]) * 0.5 + TARGET_ABOVE_SHOULDER, (sa[2] + sb[2]) * 0.5};
    }

    /** Weltpunkt in Modell-Pixel des Spielers (Füße bei x/y/z, Körper-Yaw in Grad). */
    public static double[] worldToModel(double px, double py, double pz, double x, double y, double z, float bodyYaw) {
        double dx = px - x, dy = py - y, dz = pz - z;
        double yaw = Math.toRadians(bodyYaw);
        double fwd = -Math.sin(yaw) * dx + Math.cos(yaw) * dz;
        double right = -Math.cos(yaw) * dx - Math.sin(yaw) * dz;
        return new double[]{-right / BLOCKS_PER_PX, MODEL_ORIGIN_UP_PX - dy / BLOCKS_PER_PX, -fwd / BLOCKS_PER_PX};
    }

    /** Gegenstück zu {@link #worldToModel}: Modell-Pixel des Spielers in Weltkoordinaten ({@code scale} = Spieler-Skalierung). */
    public static double[] modelToWorld(double mx, double my, double mz, double x, double y, double z, float bodyYaw,
                                        double scale) {
        double right = -mx * BLOCKS_PER_PX * scale;
        double fwd = -mz * BLOCKS_PER_PX * scale;
        double up = (MODEL_ORIGIN_UP_PX - my) * BLOCKS_PER_PX * scale;
        double yaw = Math.toRadians(bodyYaw);
        double dx = -Math.sin(yaw) * fwd - Math.cos(yaw) * right;
        double dz = Math.cos(yaw) * fwd - Math.sin(yaw) * right;
        return new double[]{x + dx, y + up, z + dz};
    }

    /**
     * Armwinkel (pitch, yaw im Bogenmaß, roll = 0), sodass ein Arm am Drehpunkt {@code from} (Modell-px) Richtung
     * {@code to} zeigt. ModelPart dreht in der Reihenfolge Z·Y·X; der Arm zeigt ungedreht entlang +Y.
     */
    public static float[] aimArm(double[] from, double[] to) {
        double vx = to[0] - from[0], vy = to[1] - from[1], vz = to[2] - from[2];
        double len = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (len < 1.0E-6) return new float[]{0.0F, 0.0F};
        vx /= len;
        vy /= len;
        vz /= len;
        // R = Ry(yaw)·Rx(pitch): (0,1,0) -> (sin p · sin y, cos p, sin p · cos y)
        double pitch = -Math.acos(Math.max(-1.0, Math.min(1.0, vy)));
        double sinP = Math.sin(pitch);
        double yaw = Math.abs(sinP) < 1.0E-6 ? 0.0 : Math.atan2(vx / sinP, vz / sinP);
        return new float[]{(float) pitch, (float) yaw};
    }

    /** Richtung eines Arms (Einheitsvektor im Modellraum) aus pitch/yaw, Gegenstück zu {@link #aimArm}. */
    public static double[] armDirection(float pitch, float yaw) {
        double sp = Math.sin(pitch);
        return new double[]{sp * Math.sin(yaw), Math.cos(pitch), sp * Math.cos(yaw)};
    }

    /** Vorlehnen (Grad) je nach waagerechtem Abstand zum Partner. */
    public static float leanDeg(double partnerDistance) {
        if (partnerDistance <= LEAN_FROM_DIST) return 0.0F;
        double f = (partnerDistance - LEAN_FROM_DIST) / (MAX_DIST - LEAN_FROM_DIST);
        return (float) (Math.min(1.0, f) * MAX_LEAN_DEG);
    }

    public static double wrapDegrees(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }
}
