package com.bame.client.emotes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Prüft ohne Minecraft die Regeln aus {@link HighFiveLogic} (Paarung, Klatsch-Zeitpunkt, Phasen, Zielpunkt, Armwinkel)
 * und die Geometrie aus {@link PushUpMotion} (Hände und Füße am Boden, nichts im Boden, flüssige Bewegung).
 */
public class HighFivePushUpTest {

    private static int checks = 0;

    public static void main(String[] args) {
        facingPair();
        backToBack();
        distanceAndHeightLimits();
        threePlayersExactlyOnePair();
        tieBreakBySmallerUuid();
        startDifference();
        clapTimeRules();
        phases();
        targetAndAim();
        acceptTargetSelection();
        acceptIgnoresTargetsBehind();
        acceptLimitsAndTolerance();
        acceptDeterminism();
        acceptNoTarget();
        acceptTimingAndPrompt();
        hiddenAcceptEmote();
        modelWorldRoundTrip();
        handsMeetAtClap();
        waitingPose();
        motionContinuity();
        motionEndsNeutral();
        pushUpGeometry();
        pushUpTimeline();
        pushUpCamera();
        System.out.println("HIGH FIVE / PUSH UPS TESTS PASSED (" + checks + " checks)");
    }

    // ---------------- Hilfen ----------------

    private static final String ID_A = "0000000000000000000000000000000a";
    private static final String ID_B = "0000000000000000000000000000000b";
    private static final String ID_C = "0000000000000000000000000000000c";

    /** Spieler bei (x, z), schaut auf den Punkt (lx, lz). */
    private static HighFiveLogic.Player p(String id, double x, double z, double lx, double lz, long start) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(lx - x), lz - z));
        return new HighFiveLogic.Player(id, x, 64.0, z, yaw, yaw, start);
    }

    private static HighFiveLogic.Player withYaw(String id, double x, double z, float yaw, long start) {
        return new HighFiveLogic.Player(id, x, 64.0, z, yaw, yaw, start);
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void near(double a, double b, double eps, String what) {
        check(Math.abs(a - b) <= eps, what + ": " + a + " != " + b);
    }

    /** Ergebnis darf nicht von der Eingabereihenfolge abhängen. */
    private static List<HighFiveLogic.Pair> pairsAllOrders(List<HighFiveLogic.Player> players) {
        List<HighFiveLogic.Pair> first = HighFiveLogic.findPairs(players);
        Random rnd = new Random(7);
        for (int i = 0; i < 20; i++) {
            List<HighFiveLogic.Player> shuffled = new ArrayList<>(players);
            Collections.shuffle(shuffled, rnd);
            List<HighFiveLogic.Pair> other = HighFiveLogic.findPairs(shuffled);
            check(describe(other).equals(describe(first)), "Paare hängen von der Reihenfolge ab");
        }
        return first;
    }

    private static String describe(List<HighFiveLogic.Pair> pairs) {
        StringBuilder sb = new StringBuilder();
        for (HighFiveLogic.Pair pair : pairs) sb.append(pair.a().id()).append('+').append(pair.b().id()).append(';');
        return sb.toString();
    }

    // ---------------- High-Five ----------------

    private static void facingPair() {
        HighFiveLogic.Player a = p(ID_A, 0, 0, 0, 1.2, 1000);
        HighFiveLogic.Player b = p(ID_B, 0, 1.2, 0, 0, 1500);
        List<HighFiveLogic.Pair> pairs = pairsAllOrders(List.of(b, a));
        check(pairs.size() == 1, "gegenüber: genau ein Paar");
        check(pairs.get(0).a().id().equals(ID_A) && pairs.get(0).b().id().equals(ID_B), "a hat die kleinere ID");

        // leicht schräg (30°) geht noch, 60° nicht mehr
        HighFiveLogic.Player a30 = withYaw(ID_A, 0, 0, 30.0F, 1000);
        check(HighFiveLogic.canPair(a30, b), "30° daneben ist noch ok");
        HighFiveLogic.Player a60 = withYaw(ID_A, 0, 0, 60.0F, 1000);
        check(!HighFiveLogic.canPair(a60, b), "60° daneben ist zu viel");
    }

    private static void backToBack() {
        HighFiveLogic.Player a = withYaw(ID_A, 0, 0, 180.0F, 1000);  // schaut nach -Z
        HighFiveLogic.Player b = withYaw(ID_B, 0, 1.0, 0.0F, 1000);  // schaut nach +Z
        check(HighFiveLogic.findPairs(List.of(a, b)).isEmpty(), "Rücken an Rücken: kein Paar");
        // nur einer schaut zum anderen
        HighFiveLogic.Player a2 = p(ID_A, 0, 0, 0, 1.0, 1000);
        check(HighFiveLogic.findPairs(List.of(a2, b)).isEmpty(), "einseitig: kein Paar");
    }

    private static void distanceAndHeightLimits() {
        check(HighFiveLogic.findPairs(List.of(p(ID_A, 0, 0, 0, 2.5, 0), p(ID_B, 0, 2.5, 0, 0, 0))).isEmpty(), "2,5 Blöcke: zu weit");
        check(HighFiveLogic.findPairs(List.of(p(ID_A, 0, 0, 0, 0.4, 0), p(ID_B, 0, 0.4, 0, 0, 0))).isEmpty(), "0,4 Blöcke: zu nah");
        check(HighFiveLogic.findPairs(List.of(p(ID_A, 0, 0, 0, 2.2, 0), p(ID_B, 0, 2.2, 0, 0, 0))).size() == 1, "2,2 Blöcke: gerade noch");
        HighFiveLogic.Player low = p(ID_A, 0, 0, 0, 1.0, 0);
        HighFiveLogic.Player high = new HighFiveLogic.Player(ID_B, 0, 65.0, 1.0, 180.0F, 180.0F, 0);
        check(HighFiveLogic.findPairs(List.of(low, high)).isEmpty(), "1 Block Höhenunterschied: kein Paar");
    }

    private static void threePlayersExactlyOnePair() {
        // A schaut nach +Z; B steht 1,0 vor A, C 1,82 schräg vor A. Beide schauen zu A.
        HighFiveLogic.Player a = withYaw(ID_A, 0, 0, 0.0F, 1000);
        HighFiveLogic.Player b = p(ID_B, 0, 1.0, 0, 0, 1200);
        HighFiveLogic.Player c = p(ID_C, 0.3, 1.8, 0, 0, 1100);
        List<HighFiveLogic.Pair> pairs = pairsAllOrders(List.of(c, a, b));
        check(pairs.size() == 1, "drei Spieler: genau ein Paar");
        check(describe(pairs).equals(ID_A + "+" + ID_B + ";"), "A und B sind sich am nächsten");

        // Drei im Dreieck, alle schauen zur Mitte: höchstens ein Paar, jeder höchstens einmal
        double r = 0.8;
        List<HighFiveLogic.Player> tri = new ArrayList<>();
        String[] ids = {ID_C, ID_A, ID_B};
        for (int i = 0; i < 3; i++) {
            double ang = Math.toRadians(120 * i + 3 * i); // leicht ungleich
            tri.add(p(ids[i], Math.cos(ang) * r, Math.sin(ang) * r, 0, 0, 1000 + i));
        }
        List<HighFiveLogic.Pair> triPairs = pairsAllOrders(tri);
        check(triPairs.size() == 1, "Dreieck: genau ein Paar, " + describe(triPairs));
    }

    private static void tieBreakBySmallerUuid() {
        // A in der Mitte; B und C gleich weit, beide schauen zu A und nicht zueinander
        HighFiveLogic.Player a = withYaw(ID_A.replace('a', 'f'), 0, 0, 0.0F, 1000); // A hat hier die größte ID
        HighFiveLogic.Player b = p(ID_B, -0.5, 1.0, 0, 0, 1000);
        HighFiveLogic.Player c = p(ID_C, 0.5, 1.0, 0, 0, 1000);
        List<HighFiveLogic.Pair> pairs = pairsAllOrders(List.of(c, a, b));
        check(pairs.size() == 1, "Gleichstand: ein Paar");
        check(pairs.get(0).a().id().equals(ID_B), "Gleichstand: kleinere UUID (B) gewinnt");
    }

    private static void startDifference() {
        HighFiveLogic.Player a = p(ID_A, 0, 0, 0, 1.2, 0);
        HighFiveLogic.Player b = p(ID_B, 0, 1.2, 0, 0, 8_500);
        check(!HighFiveLogic.canPair(a, b), "Starts mehr als 8 s auseinander");
        check(HighFiveLogic.isWaiting(0, 8_000) && !HighFiveLogic.isWaiting(0, 8_001), "Warten höchstens 8 s");
    }

    private static void clapTimeRules() {
        check(HighFiveLogic.clapTime(1_000, 1_500, 1_600) == 1_850, "max(start) + 0,35 s");
        check(HighFiveLogic.clapTime(1_500, 1_000, 1_850) == 1_850, "genau jetzt ist nicht vergangen");
        check(HighFiveLogic.clapTime(1_000, 1_500, 3_000) == 3_250, "Nachzügler: Erkennung + 0,25 s");
    }

    private static void phases() {
        long c = 10_000;
        check(HighFiveLogic.phase(c, c - 300) == HighFiveLogic.Phase.WAITING, "warten");
        check(HighFiveLogic.phase(c, c - 250) == HighFiveLogic.Phase.REACH, "ausholen 0,25 s vorher");
        check(HighFiveLogic.phase(c, c) == HighFiveLogic.Phase.FOLLOW, "klatsch -> nachschwingen");
        check(HighFiveLogic.phase(c, c + 499) == HighFiveLogic.Phase.FOLLOW, "nachschwingen 0,5 s");
        check(HighFiveLogic.phase(c, c + 500) == HighFiveLogic.Phase.LOWER, "arm runter");
        check(HighFiveLogic.phase(c, c + 800) == HighFiveLogic.Phase.ENDED, "ende bei +0,8 s");
    }

    private static void targetAndAim() {
        // A bei z=0 schaut nach +Z, B bei z=1,2 schaut nach -Z
        HighFiveLogic.Player a = withYaw(ID_A, 0, 0, 0.0F, 0);
        HighFiveLogic.Player b = withYaw(ID_B, 0, 1.2, 180.0F, 0);
        double[] sa = HighFiveLogic.rightShoulder(a.x(), a.y(), a.z(), a.bodyYaw());
        near(sa[0], -5 * HighFiveLogic.BLOCKS_PER_PX, 1e-9, "rechte Schulter von A liegt bei -X (Westen)");
        double[] t = HighFiveLogic.target(a, b);
        near(t[0], 0.0, 1e-9, "Zielpunkt mittig zwischen den rechten Schultern");
        near(t[2], 0.6, 1e-9, "Zielpunkt in der Mitte");
        near(t[1], sa[1] + HighFiveLogic.TARGET_ABOVE_SHOULDER, 1e-9, "0,35 über Schulterhöhe");
        check(Math.abs(HighFiveLogic.target(b, a)[0] - t[0]) < 1e-9, "Zielpunkt für beide gleich");

        // Im Modellraum von A: vor (z < 0) und über (y < Schulter) der Schulter, rechte Hand zur Mitte (x > -5)
        double[] m = HighFiveLogic.worldToModel(t[0], t[1], t[2], a.x(), a.y(), a.z(), a.bodyYaw());
        check(m[2] < 0 && m[1] < HighFiveLogic.SHOULDER_Y_PX && m[0] > HighFiveLogic.SHOULDER_X_PX, "Ziel vor/über A");
        // Gedrehter Spieler: gleiches Ergebnis im eigenen Modellraum
        double[] mb = HighFiveLogic.worldToModel(t[0], t[1], t[2], b.x(), b.y(), b.z(), b.bodyYaw());
        near(mb[0], m[0], 1e-6, "symmetrisch x");
        near(mb[1], m[1], 1e-6, "symmetrisch y");
        near(mb[2], m[2], 1e-6, "symmetrisch z");

        // Armwinkel zeigen wirklich zum Ziel
        double[] shoulder = {HighFiveLogic.SHOULDER_X_PX, HighFiveLogic.SHOULDER_Y_PX, 0};
        float[] aim = HighFiveLogic.aimArm(shoulder, m);
        double[] dir = HighFiveLogic.armDirection(aim[0], aim[1]);
        double[] want = {m[0] - shoulder[0], m[1] - shoulder[1], m[2] - shoulder[2]};
        double len = Math.sqrt(want[0] * want[0] + want[1] * want[1] + want[2] * want[2]);
        for (int i = 0; i < 3; i++) near(dir[i], want[i] / len, 1e-5, "Armrichtung " + i);
        check(aim[0] < -Math.PI / 2, "Arm zeigt nach oben vorne");

        // Hände treffen sich ungefähr: Abstand Hand (10 px Armlänge) zum Ziel klein gegenüber dem Abstand
        double handDist = len - 10.0;
        check(handDist * HighFiveLogic.BLOCKS_PER_PX < 0.25, "Hand nahe am Treffpunkt bei 1,2 Blöcken");

        check(HighFiveLogic.leanDeg(1.2) == 0.0F && HighFiveLogic.leanDeg(2.2) == HighFiveLogic.MAX_LEAN_DEG, "Vorlehnen ab 1,3");
    }

    // ---------------- Einschlagen per Taste ----------------

    private static final String ID_D = "0000000000000000000000000000000d";

    private static void acceptTargetSelection() {
        // A wartet (schaut irgendwohin, z. B. weg von B), B steht 1,2 vor A und schaut zu A
        HighFiveLogic.Player a = withYaw(ID_A, 0, 0, 180.0F, 1000);
        HighFiveLogic.Player b = p(ID_B, 0, 1.2, 0, 0, 2000);
        check(HighFiveLogic.canAccept(b, a, false), "B schaut zu A: einschlagen möglich, A muss nicht zu B schauen");
        check(HighFiveLogic.findAcceptTarget(b, List.of(a), 2000, false) == a, "Ziel ist A");

        // Zwei Wartende vor B: der nähere gewinnt
        HighFiveLogic.Player near = withYaw(ID_C, 0.2, 0.2, 0.0F, 1000);   // 1,02 von B
        HighFiveLogic.Player far = withYaw(ID_A, 0, -0.6, 0.0F, 1000);     // 1,8 von B
        HighFiveLogic.Player target = HighFiveLogic.findAcceptTarget(b, List.of(far, near), 2000, false);
        check(target != null && target.id().equals(ID_C), "der nähere Wartende wird gewählt");

        // Gleich weit: kleinere UUID
        HighFiveLogic.Player left = withYaw(ID_D, -0.3, 0.2, 0.0F, 1000);
        HighFiveLogic.Player right = withYaw(ID_C, 0.3, 0.2, 0.0F, 1000);
        HighFiveLogic.Player tie = HighFiveLogic.findAcceptTarget(b, List.of(left, right), 2000, false);
        check(tie != null && tie.id().equals(ID_C), "Gleichstand: kleinere UUID");
        HighFiveLogic.Player tie2 = HighFiveLogic.findAcceptTarget(b, List.of(right, left), 2000, false);
        check(tie2 != null && tie2.id().equals(ID_C), "Gleichstand unabhängig von der Reihenfolge");
    }

    private static void acceptIgnoresTargetsBehind() {
        // B schaut nach +Z; A steht hinter B (−Z)
        HighFiveLogic.Player b = withYaw(ID_B, 0, 0, 0.0F, 2000);
        HighFiveLogic.Player behind = withYaw(ID_A, 0, -1.2, 0.0F, 1000);
        check(!HighFiveLogic.canAccept(b, behind, false), "Ziel hinter B: nein");
        check(!HighFiveLogic.canAccept(b, behind, true), "Ziel hinter B: auch tolerant nein");
        check(HighFiveLogic.findAcceptTarget(b, List.of(behind), 2000, false) == null, "kein Ziel hinter B");
        // seitlich 60° daneben: nein, 40°: ja
        HighFiveLogic.Player side60 = withYaw(ID_A, -Math.sin(Math.toRadians(60)) * 1.2, Math.cos(Math.toRadians(60)) * 1.2, 0.0F, 1000);
        HighFiveLogic.Player side40 = withYaw(ID_A, -Math.sin(Math.toRadians(40)) * 1.2, Math.cos(Math.toRadians(40)) * 1.2, 0.0F, 1000);
        check(!HighFiveLogic.canAccept(b, side60, false), "60° neben dem Blick: nein");
        check(HighFiveLogic.canAccept(b, side40, false), "40° neben dem Blick: ja");
        // ein Wartender vor B wird gewählt, auch wenn ein näherer hinter B steht
        HighFiveLogic.Player front = withYaw(ID_C, 0, 2.0, 0.0F, 1000);
        HighFiveLogic.Player closeBehind = withYaw(ID_A, 0, -0.8, 0.0F, 1000);
        HighFiveLogic.Player t = HighFiveLogic.findAcceptTarget(b, List.of(closeBehind, front), 2000, false);
        check(t != null && t.id().equals(ID_C), "Ziel vor B statt näherem hinter B");
    }

    private static void acceptLimitsAndTolerance() {
        HighFiveLogic.Player b = withYaw(ID_B, 0, 0, 0.0F, 2000);
        check(!HighFiveLogic.canAccept(b, withYaw(ID_A, 0, 2.5, 0.0F, 1000), false), "2,5 Blöcke: zu weit");
        check(!HighFiveLogic.canAccept(b, withYaw(ID_A, 0, 0.4, 0.0F, 1000), false), "0,4 Blöcke: zu nah");
        check(HighFiveLogic.canAccept(b, withYaw(ID_A, 0, 2.2, 0.0F, 1000), false), "2,2 Blöcke: gerade noch");
        check(!HighFiveLogic.canAccept(b, new HighFiveLogic.Player(ID_A, 0, 65.0, 1.0, 0.0F, 0.0F, 1000), false), "1 Block höher: nein");
        check(!HighFiveLogic.canAccept(b, b, false), "nicht bei sich selbst");
        // Andere Clients sind etwas großzügiger (verzögerte Positionen), aber nicht beliebig
        check(HighFiveLogic.canAccept(b, withYaw(ID_A, 0, 2.5, 0.0F, 1000), true), "tolerant: 2,5 Blöcke ok");
        check(!HighFiveLogic.canAccept(b, withYaw(ID_A, 0, 3.0, 0.0F, 1000), true), "tolerant: 3 Blöcke zu weit");
        HighFiveLogic.Player side55 = withYaw(ID_A, -Math.sin(Math.toRadians(55)) * 1.2, Math.cos(Math.toRadians(55)) * 1.2, 0.0F, 1000);
        check(!HighFiveLogic.canAccept(b, side55, false) && HighFiveLogic.canAccept(b, side55, true), "tolerant: 55° ok, streng nicht");
        // Wartender, dessen 8 s zum Einschlag-Zeitpunkt vorbei sind, ist kein Ziel
        HighFiveLogic.Player old = withYaw(ID_A, 0, 1.2, 180.0F, 0);
        check(HighFiveLogic.findAcceptTarget(b, List.of(old), 8_001, false) == null, "abgelaufenes Warten: kein Ziel");
        check(HighFiveLogic.findAcceptTarget(b, List.of(old), 7_999, false) == old, "noch wartend: Ziel");
    }

    private static void acceptDeterminism() {
        // Viele Wartende um B herum: jedes Mischen der Eingabe ergibt dasselbe Ziel
        HighFiveLogic.Player b = withYaw(ID_B, 0, 0, 0.0F, 5000);
        List<HighFiveLogic.Player> waiting = new ArrayList<>();
        Random rnd = new Random(42);
        for (int i = 0; i < 12; i++) {
            String id = String.format("%032x", 0x100 + i);
            waiting.add(withYaw(id, (rnd.nextDouble() - 0.5) * 4, (rnd.nextDouble() - 0.3) * 4, rnd.nextFloat() * 360F, 1000 + i));
        }
        // zwei exakt gleich weit vor B (Gleichstand)
        waiting.add(withYaw("00000000000000000000000000000fff", 0.4, 1.0, 0.0F, 1000));
        waiting.add(withYaw("00000000000000000000000000000ffe", -0.4, 1.0, 0.0F, 1000));
        HighFiveLogic.Player first = HighFiveLogic.findAcceptTarget(b, waiting, 5000, false);
        check(first != null, "es gibt ein Ziel");
        for (int i = 0; i < 30; i++) {
            List<HighFiveLogic.Player> shuffled = new ArrayList<>(waiting);
            Collections.shuffle(shuffled, rnd);
            HighFiveLogic.Player other = HighFiveLogic.findAcceptTarget(b, shuffled, 5000, false);
            check(other != null && other.id().equals(first.id()), "Ziel hängt von der Reihenfolge ab");
        }
        // Gleiche Daten auf einem anderen Client (tolerant) ergeben bei klarer Lage dasselbe Ziel
        HighFiveLogic.Player tolerant = HighFiveLogic.findAcceptTarget(b, waiting, 5000, true);
        check(tolerant != null, "tolerant: es gibt ein Ziel");
    }

    private static void acceptNoTarget() {
        HighFiveLogic.Player b = withYaw(ID_B, 0, 0, 0.0F, 2000);
        check(HighFiveLogic.findAcceptTarget(b, List.of(), 2000, false) == null, "niemand wartet: kein Ziel");
        check(HighFiveLogic.findAcceptTarget(b, List.of(withYaw(ID_A, 5, 5, 0.0F, 1000)), 2000, false) == null,
                "Wartender zu weit weg: kein Ziel");
    }

    private static void acceptTimingAndPrompt() {
        check(HighFiveLogic.acceptClapTime(10_000, 10_050) == 10_300, "Klatsch 0,3 s nach dem Einschlagen");
        check(HighFiveLogic.acceptClapTime(10_000, 10_400) == 10_650, "spät empfangen: Erkennung + 0,25 s");
        check(HighFiveLogic.canPrompt(0, 7_500) && !HighFiveLogic.canPrompt(0, 7_501), "Hinweis nur bis 0,5 s vor Ablauf");
        check(!HighFiveLogic.canPrompt(5_000, 3_000), "Hinweis nicht vor dem Start");
        // Ablauf mit den Phasen: Klatsch, danach Ende nach 0,8 s
        long clap = HighFiveLogic.acceptClapTime(10_000, 10_000);
        check(HighFiveLogic.phase(clap, 10_000) == HighFiveLogic.Phase.WAITING, "direkt nach dem Druck: Arm hebt sich");
        check(HighFiveLogic.phase(clap, clap + 800) == HighFiveLogic.Phase.ENDED, "Ende 0,8 s nach dem Klatsch");
    }

    private static void hiddenAcceptEmote() {
        check(EmoteType.HIGH_FIVE_ACCEPT.isHidden(), "Annehmen ist versteckt");
        check(!EmoteType.HIGH_FIVE.isHidden() && !EmoteType.PUSH_UPS.isHidden(), "normale Emotes sind sichtbar");
        check(EmoteType.fromId("HIGH_FIVE_ACCEPT") == EmoteType.HIGH_FIVE_ACCEPT, "Sync-ID HIGH_FIVE_ACCEPT");
        check(EmoteType.fromId("HIGH_FIVE") == EmoteType.HIGH_FIVE, "HIGH_FIVE bleibt HIGH_FIVE");
        check(EmoteType.fromId("highfive") == EmoteType.HIGH_FIVE, "Kurzform bleibt HIGH_FIVE");
        check(EmoteType.HIGH_FIVE_ACCEPT.allowsWalking(), "Einschlagen im Gehen erlaubt");
    }

    // ---------------- High-Five: Bewegung (HighFiveMotion) ----------------

    private static void modelWorldRoundTrip() {
        Random rnd = new Random(3);
        for (int i = 0; i < 50; i++) {
            double x = rnd.nextDouble() * 10, y = 64 + rnd.nextDouble(), z = rnd.nextDouble() * 10;
            float yaw = rnd.nextFloat() * 720F - 360F;
            double[] w = {x + rnd.nextDouble() - 0.5, y + rnd.nextDouble() * 2, z + rnd.nextDouble() - 0.5};
            double[] m = HighFiveLogic.worldToModel(w[0], w[1], w[2], x, y, z, yaw);
            double[] back = HighFiveLogic.modelToWorld(m[0], m[1], m[2], x, y, z, yaw, 1.0);
            for (int k = 0; k < 3; k++) near(back[k], w[k], 1e-9, "Modell <-> Welt " + k);
        }
    }

    /** Arm-Kette unabhängig nachgerechnet: Drehpunkt + Rz·Ry·Rx·Ry(twist)·(local) wie ModelPart.applyTransform/rotate. */
    private static double[] fk(HighFiveMotion.Pose p, double[] local) {
        double[][] m = mul(mul(mul(rz(p.rArmRoll), ry(p.rArmYaw)), rx(p.rArmPitch)), ry(p.rArmTwist));
        double[] v = {local[0], local[1] * p.rArmScale, local[2]};
        return new double[]{
                p.rArmX + m[0][0] * v[0] + m[0][1] * v[1] + m[0][2] * v[2],
                p.rArmY + m[1][0] * v[0] + m[1][1] * v[1] + m[1][2] * v[2],
                p.rArmZ + m[2][0] * v[0] + m[2][1] * v[1] + m[2][2] * v[2]};
    }

    private static double[] fkPalm(HighFiveMotion.Pose p) {
        return fk(p, new double[]{HighFiveMotion.PALM_INNER_PX, HighFiveMotion.PALM_ALONG_PX, 0});
    }

    private static double[][] rx(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{1, 0, 0}, {0, c, -s}, {0, s, c}};
    }

    private static double[][] ry(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{c, 0, s}, {0, 1, 0}, {-s, 0, c}};
    }

    private static double[][] rz(double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return new double[][]{{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
    }

    private static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                for (int k = 0; k < 3; k++) r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    /** Eingabe für Spieler {@code self} mit Partner {@code other} am Treffpunkt {@code t}. */
    private static HighFiveMotion.Input input(HighFiveLogic.Player self, HighFiveLogic.Player other, double[] t,
                                              HighFiveMotion.Role role, boolean slim, double tauMs, float emoteT,
                                              double leadMs) {
        HighFiveMotion.Input in = new HighFiveMotion.Input();
        in.role = role;
        in.t = emoteT;
        in.paired = true;
        in.tauMs = tauMs;
        in.leadMs = leadMs;
        in.target = HighFiveLogic.worldToModel(t[0], t[1], t[2], self.x(), self.y(), self.z(), self.bodyYaw());
        double[] pm = HighFiveLogic.worldToModel(other.x(), other.y(), other.z(), self.x(), self.y(), self.z(), self.bodyYaw());
        in.partnerX = pm[0];
        in.partnerZ = pm[2];
        in.partnerDistance = HighFiveLogic.horizontalDistance(self, other);
        if (slim) {
            in.rightShoulder = new double[]{-5, 2.5, 0};
            in.leftShoulder = new double[]{5, 2.5, 0};
        }
        return in;
    }

    private static void handsMeetAtClap() {
        double ppx = HighFiveLogic.BLOCKS_PER_PX;
        double worst = 0, worstFacing = 1, maxShift = 0, minScale = 9, maxScale = 0;
        int cases = 0;
        double[] dists = {0.8, 1.0, 1.2, 1.5, 1.8, 2.0};
        double[] lines = {0, 37, 140, -100};
        double[] offs = {-44.5, -20, 0, 20, 44.5};
        HighFiveMotion.Role[][] roles = {
                {HighFiveMotion.Role.WAITER, HighFiveMotion.Role.ACCEPTER},
                {HighFiveMotion.Role.WAITER, HighFiveMotion.Role.WAITER}};
        for (double d : dists) for (double line : lines) for (double offA : offs) for (double offB : offs) {
            double lr = Math.toRadians(line);
            double bx = -Math.sin(lr) * d, bz = Math.cos(lr) * d;
            float yawA = (float) (line + offA);
            float yawB = (float) (line + 180 + offB);
            HighFiveLogic.Player a = new HighFiveLogic.Player(ID_A, 0, 64, 0, yawA, yawA, 0);
            HighFiveLogic.Player b = new HighFiveLogic.Player(ID_B, bx, 64, bz, yawB, yawB, 0);
            check(HighFiveLogic.canPair(a, b), "Testaufbau: Paar möglich");
            double[] t = HighFiveLogic.target(a, b);
            for (HighFiveMotion.Role[] rp : roles) for (boolean slim : new boolean[]{false, true}) {
                HighFiveMotion.Pose pa = new HighFiveMotion.Pose(), pb = new HighFiveMotion.Pose();
                HighFiveMotion.sample(input(a, b, t, rp[0], slim, 0, 3.0F, 2000), pa);
                HighFiveMotion.sample(input(b, a, t, rp[1], slim, 0, 0.3F, 300), pb);
                double[] ma = fkPalm(pa), mb = fkPalm(pb);
                double[] mpa = HighFiveMotion.palmPoint(pa);
                check(dist(ma, mpa) < 1e-6, "palmPoint stimmt mit der Arm-Kette überein");
                double[] wa = HighFiveLogic.modelToWorld(ma[0], ma[1], ma[2], a.x(), a.y(), a.z(), a.bodyYaw(), 1.0);
                double[] wb = HighFiveLogic.modelToWorld(mb[0], mb[1], mb[2], b.x(), b.y(), b.z(), b.bodyYaw(), 1.0);
                double ea = dist(wa, t) / ppx, eb = dist(wb, t) / ppx;
                worst = Math.max(worst, Math.max(ea, eb));
                String where = "d=" + d + " line=" + line + " offA=" + offA + " offB=" + offB + " slim=" + slim + " " + rp[1];
                check(ea <= 1.5, "Hand A trifft den Treffpunkt (" + ea + " px) " + where);
                check(eb <= 1.5, "Hand B trifft den Treffpunkt (" + eb + " px) " + where);
                // Handflächen zeigen zueinander
                double[] na = fk(pa, new double[]{HighFiveMotion.PALM_INNER_PX + 1, HighFiveMotion.PALM_ALONG_PX, 0});
                double[] nb = fk(pb, new double[]{HighFiveMotion.PALM_INNER_PX + 1, HighFiveMotion.PALM_ALONG_PX, 0});
                double[] wna = HighFiveLogic.modelToWorld(na[0], na[1], na[2], a.x(), a.y(), a.z(), a.bodyYaw(), 1.0);
                double[] wnb = HighFiveLogic.modelToWorld(nb[0], nb[1], nb[2], b.x(), b.y(), b.z(), b.bodyYaw(), 1.0);
                // Ohne Handgelenk steht die Normale senkrecht zum Arm: waagerecht genau gegenläufig, und jede zeigt zum Partner
                double[] nA = {(wna[0] - wa[0]) / ppx, (wna[1] - wa[1]) / ppx, (wna[2] - wa[2]) / ppx};
                double[] nB = {(wnb[0] - wb[0]) / ppx, (wnb[1] - wb[1]) / ppx, (wnb[2] - wb[2]) / ppx};
                double hA = Math.hypot(nA[0], nA[2]), hB = Math.hypot(nB[0], nB[2]);
                double dotH = (nA[0] * nB[0] + nA[2] * nB[2]) / (hA * hB);
                check(dotH < -0.9, "Handflächen waagerecht gegenläufig (" + dotH + ") " + where);
                double toB = (nA[0] * bx + nA[2] * bz) / d, toA = -(nB[0] * bx + nB[2] * bz) / d;
                worstFacing = Math.min(worstFacing, Math.min(toB, toA));
                check(toB > 0.2 && toA > 0.2, "Handflächen zeigen zum Partner (" + toB + ", " + toA + ") " + where);
                // Arm höchstens leicht gestreckt/gestaucht, Schritt begrenzt
                check(pa.rArmScale >= HighFiveMotion.ARM_SCALE_MIN - 1e-6 && pa.rArmScale <= HighFiveMotion.ARM_SCALE_MAX + 1e-6, "Armlänge A");
                check(Math.hypot(pa.legDX, pa.legDZ) <= HighFiveMotion.SHIFT_MAX_PX + 1e-6, "Schritt A");
                maxShift = Math.max(maxShift, Math.max(Math.hypot(pa.legDX, pa.legDZ), Math.hypot(pb.legDX, pb.legDZ)));
                minScale = Math.min(minScale, Math.min(pa.rArmScale, pb.rArmScale));
                maxScale = Math.max(maxScale, Math.max(pa.rArmScale, pb.rArmScale));
                cases++;
            }
        }
        System.out.println("  High-Five: " + cases + " Kontakt-Fälle, größter Abstand Hand-Treffpunkt " + String.format("%.3f", worst) + " px, Handfläche zum Partner min. " + String.format("%.2f", worstFacing)
                + String.format(", Schritt max. %.1f px, Armlänge %.2f..%.2f", maxShift, minScale, maxScale));
    }

    private static void waitingPose() {
        HighFiveMotion.Input in = new HighFiveMotion.Input();
        in.role = HighFiveMotion.Role.WAITER;
        HighFiveMotion.Pose p = new HighFiveMotion.Pose();
        for (float t = 0.5F; t < 8.0F; t += 0.05F) {
            in.t = t;
            HighFiveMotion.sample(in, p);
            double[] palm = fkPalm(p);
            double minUp = HighFiveMotion.comeOn(t) > 0 ? -3.0 : -5.0; // beim Winken etwas tiefer
            check(palm[1] < minUp, "Hand beim Warten hoch erhoben: y=" + palm[1] + " t=" + t);
            double[] n = HighFiveMotion.palmNormal(p.rArmPitch, p.rArmYaw, p.rArmRoll, p.rArmTwist);
            check(n[2] < -0.6, "Handfläche zeigt beim Warten nach vorne: " + n[2] + " t=" + t);
        }
        // „Komm schon“-Wink kommt regelmäßig
        check(HighFiveMotion.comeOn(HighFiveMotion.COME_ON_FROM_S + HighFiveMotion.COME_ON_LEN_S / 2) > 0.99F, "Wink");
        check(HighFiveMotion.comeOn(0.5F) == 0.0F, "kein Wink direkt am Anfang");
    }

    /** Alle Kanäle der Pose (für Sprungprüfung). */
    private static float[] channels(HighFiveMotion.Pose p) {
        return new float[]{p.bodyX, p.bodyY, p.bodyZ, p.headX, p.headY, p.headZ, p.rArmX, p.rArmY, p.rArmZ,
                p.lArmX, p.lArmY, p.lArmZ, p.legDX, p.legDY, p.legDZ,
                p.bodyPitch, p.bodyYaw, p.bodyRoll, p.headPitchAdd, p.headRoll, p.rArmPitch, p.rArmYaw, p.rArmRoll,
                p.rArmTwist, p.lArmPitch, p.lArmYaw, p.lArmRoll, p.rLegPitch, p.lLegPitch, p.rArmScale, p.partnerFocus};
    }

    private static final int POSITION_CHANNELS = 15;

    /** Eingabe zur Zeit {@code ms} relativ zum Klatsch. */
    private interface Scenario {
        HighFiveMotion.Input at(double ms);
    }

    /** Tastet eine Zeitleiste in 1-ms-Schritten ab und prüft, dass nichts springt (auch nicht beim Erkennen des Paars). */
    private static void continuity(String name, Scenario s, double fromMs, double toMs) {
        HighFiveMotion.Pose p = new HighFiveMotion.Pose();
        float[] prev = null;
        double[] prevPalm = null;
        double maxAngle = 0, maxPos = 0, maxPalm = 0;
        for (double ms = fromMs; ms <= toMs; ms += 1.0) {
            HighFiveMotion.sample(s.at(ms), p);
            float[] ch = channels(p);
            double[] palm = fkPalm(p);
            if (prev != null) {
                for (int i = 0; i < ch.length; i++) {
                    double dlt = Math.abs(ch[i] - prev[i]);
                    if (i < POSITION_CHANNELS) maxPos = Math.max(maxPos, dlt);
                    else maxAngle = Math.max(maxAngle, dlt);
                    double lim = i < POSITION_CHANNELS ? 0.25 : 0.05;
                    check(dlt < lim, name + ": Kanal " + i + " springt bei " + ms + " ms um " + dlt);
                }
                double dp = dist(palm, prevPalm);
                maxPalm = Math.max(maxPalm, dp);
                check(dp < 0.6, name + ": Hand springt bei " + ms + " ms um " + dp + " px");
            }
            prev = ch;
            prevPalm = palm;
        }
        System.out.println(String.format("  %s: max. pro ms %.4f rad, %.3f px (Teile), %.3f px (Hand)", name, maxAngle, maxPos, maxPalm));
    }

    private static void motionContinuity() {
        HighFiveLogic.Player a = withYaw(ID_A, 0, 0, 10.0F, 0);
        HighFiveLogic.Player b = withYaw(ID_B, 0.2, 1.6, 175.0F, 0);
        double[] t = HighFiveLogic.target(a, b);

        // Wartender, Paar lange vorher bekannt; Emote-Start 2,5 s vor dem Klatsch
        continuity("Wartender", ms -> input(a, b, t, HighFiveMotion.Role.WAITER, false, ms, (float) ((ms + 2500) / 1000.0), 3000),
                -2500, 1000);
        // Paar erst 180 ms vor dem Klatsch erkannt (Einschlagen kam bei diesem Client spät an): vorher ungepaart
        continuity("Wartender, spät erkannt", ms -> {
            HighFiveMotion.Input in = input(a, b, t, HighFiveMotion.Role.WAITER, false, ms, (float) ((ms + 4000) / 1000.0), 180);
            in.paired = ms >= -180;
            return in;
        }, -1000, 1000);
        // Einschlagender, lokal: Start 300 ms vor dem Klatsch
        continuity("Einschlagender", ms -> input(b, a, t, HighFiveMotion.Role.ACCEPTER, false, ms, (float) ((ms + 300) / 1000.0), 300),
                -300, 1000);
        // Einschlagender bei einem anderen Client: Start 600 ms vorher, erst 250 ms vorher erkannt
        continuity("Einschlagender, spät erkannt", ms -> {
            HighFiveMotion.Input in = input(b, a, t, HighFiveMotion.Role.ACCEPTER, true, ms, (float) ((ms + 600) / 1000.0), 250);
            in.paired = ms >= -250;
            return in;
        }, -600, 1000);
        // Weiter Abstand (Ausfallschritt) und nah
        HighFiveLogic.Player far = withYaw(ID_B, 0, 2.0, 180.0F, 0);
        double[] tf = HighFiveLogic.target(a, far);
        continuity("Weit (2,0)", ms -> input(a, far, tf, HighFiveMotion.Role.WAITER, false, ms, (float) ((ms + 2500) / 1000.0), 3000),
                -400, 1000);
        HighFiveLogic.Player close = withYaw(ID_B, 0, 0.7, 180.0F, 0);
        double[] tc = HighFiveLogic.target(a, close);
        continuity("Nah (0,7)", ms -> input(close, a, tc, HighFiveMotion.Role.ACCEPTER, false, ms, (float) ((ms + 300) / 1000.0), 300),
                -300, 1000);
        // Vorschau über drei Zyklen (inklusive Übergang von einem Zyklus zum nächsten)
        continuity("Vorschau", ms -> {
            HighFiveMotion.Input in = new HighFiveMotion.Input();
            in.role = HighFiveMotion.Role.PREVIEW;
            in.t = (float) (ms / 1000.0);
            return in;
        }, 0, 3 * HighFiveMotion.PREVIEW_PERIOD_S * 1000);
        // Einschlagen ohne Partner: Arm hebt sich und sinkt nach 1 s wieder
        continuity("Einschlagen ohne Partner", ms -> {
            HighFiveMotion.Input in = new HighFiveMotion.Input();
            in.role = HighFiveMotion.Role.ACCEPTER;
            in.t = (float) (ms / 1000.0);
            return in;
        }, 0, 2000);
    }

    private static void motionEndsNeutral() {
        HighFiveLogic.Player a = withYaw(ID_A, 0, 0, 0.0F, 0);
        HighFiveLogic.Player b = withYaw(ID_B, 0, 1.9, 180.0F, 0);
        double[] t = HighFiveLogic.target(a, b);
        HighFiveMotion.Pose p = new HighFiveMotion.Pose();
        float[] rest = {0, 0, 0, 0, 0, 0, -5, 2, 0, 5, 2, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0};
        for (HighFiveMotion.Role role : new HighFiveMotion.Role[]{HighFiveMotion.Role.WAITER, HighFiveMotion.Role.ACCEPTER}) {
            for (double ms : new double[]{HighFiveMotion.END_MS, HighFiveMotion.END_MS + 50}) {
                HighFiveMotion.sample(input(a, b, t, role, false, ms, 3.0F, 3000), p);
                float[] ch = channels(p);
                for (int i = 0; i < ch.length; i++) near(ch[i], rest[i], 1e-4, role + ": neutral am Ende, Kanal " + i);
            }
        }
        // Am Start: Grundhaltung (kein Sprung beim Beginn des Emotes)
        HighFiveMotion.Input in = new HighFiveMotion.Input();
        in.t = 0;
        HighFiveMotion.sample(in, p);
        near(p.rArmPitch, 0, 1e-6, "Warten beginnt in der Grundhaltung");
        in.role = HighFiveMotion.Role.ACCEPTER;
        HighFiveMotion.sample(in, p);
        near(p.rArmPitch, 0, 1e-6, "Einschlagen beginnt in der Grundhaltung");
        // Ausholen: Hand zuerst weiter weg vom Ziel, dann Schwung; Rückprall nach dem Kontakt; Hüpfer im Nachschwung
        double[] mt = HighFiveLogic.worldToModel(t[0], t[1], t[2], a.x(), a.y(), a.z(), a.bodyYaw());
        HighFiveMotion.sample(input(a, b, t, HighFiveMotion.Role.WAITER, false, -HighFiveLogic.REACH_MS - 1, 3.0F, 3000), p);
        double before = dist(fkPalm(p), mt);
        HighFiveMotion.sample(input(a, b, t, HighFiveMotion.Role.WAITER, false, -HighFiveMotion.SWING_MS, 3.0F, 3000), p);
        double cocked = dist(fkPalm(p), mt);
        check(cocked > before, "Ausholen zieht die Hand zurück: " + cocked + " > " + before);
        HighFiveMotion.sample(input(a, b, t, HighFiveMotion.Role.WAITER, false, 60, 3.0F, 3000), p);
        double recoil = dist(fkPalm(p), mt);
        check(recoil > 0.8 && recoil < 5.0, "Rückprall nach dem Kontakt: " + recoil + " px");
        HighFiveMotion.sample(input(a, b, t, HighFiveMotion.Role.WAITER, false, 320, 3.0F, 3000), p);
        check(p.legDY < -1.0F, "kleiner Hüpfer im Nachschwung");
    }

    // ---------------- Liegestütze ----------------

    private static void pushUpCamera() {
        // Start: Drehpunkt im Stand (Rumpfmitte + Anhebung), Ende des Einstiegs: tief und vor den Füßen
        float[] start = PushUpMotion.cameraPivot(0.0F);
        near(start[0], PushUpMotion.CAMERA_PIVOT_UP_PX * PushUpMotion.BLOCKS_PER_PX + PushUpMotion.CAMERA_PIVOT_LIFT, 1e-4, "Start im Stand");
        float[] plank = PushUpMotion.cameraPivot(PushUpMotion.ENTRY + 0.2F);
        check(plank[0] > 0.3F && plank[0] < 1.0F, "Drehpunkt knapp über dem liegenden Rumpf: " + plank[0]);
        check(plank[1] > 0.5F && plank[1] < 1.2F, "Drehpunkt über der Rumpfmitte vor den Füßen: " + plank[1]);
        // weit unter der Augenhöhe im Stand (1,62)
        check(plank[0] < 1.62F - 0.7F, "deutlich unter Augenhöhe");
        // wippt nicht mit jeder Wiederholung
        float[] down = PushUpMotion.cameraPivot(PushUpMotion.repTime(3, 0.6F));
        near(down[0], plank[0], 1e-5, "Kamera wippt nicht (Höhe)");
        near(down[1], plank[1], 1e-5, "Kamera wippt nicht (vorne)");
        // Übergang weich und vollständig
        near(PushUpMotion.cameraBlend(0.0F), 0, 1e-6, "Übergang beginnt bei 0");
        near(PushUpMotion.cameraBlend(PushUpMotion.CAMERA_BLEND_TIME), 1, 1e-6, "Übergang endet bei 1");
        float prev = 0;
        for (int i = 1; i <= 120; i++) {
            float t = i / 120.0F;
            float[] p = PushUpMotion.cameraPivot(t);
            float[] q = PushUpMotion.cameraPivot(t - 1 / 120.0F);
            check(Math.abs(p[0] - q[0]) < 0.05 && Math.abs(p[1] - q[1]) < 0.05, "Drehpunkt springt bei t=" + t);
            float bl = PushUpMotion.cameraBlend(t);
            check(bl >= prev - 1e-6, "Übergang monoton");
            prev = bl;
        }
    }

    private static void pushUpGeometry() {
        for (int i = 0; i <= 100; i++) {
            float d = i / 100.0F;
            float tilt = PushUpMotion.bodyTiltDeg(d);
            float[] shoulder = PushUpMotion.tiltedPoint(PushUpMotion.SHOULDER_UP_PX, 0, tilt);
            near(shoulder[0], PushUpMotion.shoulderHeightPx(d), 1e-3, "Schulterhöhe bei Tiefe " + d);

            // Fußspitze bleibt am Boden, Ferse und die ganze Vorderseite des Körpers liegen nicht im Boden
            float[] toe = PushUpMotion.tiltedPoint(0, PushUpMotion.TOE_FWD_PX, tilt);
            near(toe[0], 0, 1e-4, "Fußspitze am Boden");
            check(PushUpMotion.tiltedPoint(0, -2, tilt)[0] >= 0, "Ferse über dem Boden");
            for (float up = 0; up <= 32; up += 1) {
                check(PushUpMotion.tiltedPoint(up, PushUpMotion.TOE_FWD_PX, tilt)[0] >= -1e-4, "Körpervorderseite im Boden");
            }
            // Kopf (Vorderseite des 8x8-Kopfs) nicht im Boden
            check(PushUpMotion.tiltedPoint(24, 4, tilt)[0] > 0, "Kopf über dem Boden");
        }

        // Hände: oben genau am Boden, unten am Boden und höchstens ~1,5 px verschoben
        PushUpMotion.Pose pose = new PushUpMotion.Pose();
        float[] hands = new float[2];
        float[] top = handOnGround(0.0F, hands);
        near(top[0], 0, 1e-3, "Hand oben am Boden");
        float[] bottom = handOnGround(1.0F, hands);
        near(bottom[0], 0, 0.05, "Hand unten am Boden");
        check(Math.abs(bottom[1] - top[1]) < 1.5, "Hand bleibt ungefähr an derselben Stelle: " + (bottom[1] - top[1]));

        // Unten 5,5 px tiefer; Kippung zwischen 60° und 90°
        near(PushUpMotion.shoulderHeightPx(0) - PushUpMotion.shoulderHeightPx(1), 5.5, 1e-4, "5,5 px tiefer");
        check(PushUpMotion.bodyTiltDeg(0) > 60 && PushUpMotion.bodyTiltDeg(1) < 90, "Kippwinkel plausibel");

        // Pose oben (nach dem Einstieg): Arme senkrecht zum Boden, symmetrisch gespreizt
        PushUpMotion.sample(PushUpMotion.ENTRY + PushUpMotion.REP - 0.05F, pose);
        near(pose.depth, 0, 1e-6, "oben gehalten");
        near(Math.toDegrees(pose.rightArmPitch), -PushUpMotion.bodyTiltDeg(0), 1e-3, "Arm senkrecht zum Boden");
        near(pose.rightArmYaw, -pose.leftArmYaw, 1e-6, "Arme symmetrisch");
        double[] dir = HighFiveLogic.armDirection(pose.rightArmPitch, pose.rightArmYaw);
        near(Math.toDegrees(Math.asin(-dir[0])), PushUpMotion.ARM_SPLAY_DEG, 1e-3, "rechter Arm 8° nach außen");
        near(pose.armScale, 1.0, 1e-6, "oben gestreckt");
    }

    /** Hand der rechten Seite (in der Körperebene, ohne Abspreizen) bei Tiefe d: {Höhe, vorne} in px. */
    private static float[] handOnGround(float d, float[] out) {
        float tilt = PushUpMotion.bodyTiltDeg(d);
        float[] shoulder = PushUpMotion.tiltedPoint(PushUpMotion.SHOULDER_UP_PX, 0, tilt);
        double back = Math.toRadians(PushUpMotion.ARM_BACK_DEG * d);
        double scale = PushUpMotion.shoulderHeightPx(d) / (PushUpMotion.ARM_REACH_PX * Math.cos(back));
        double len = PushUpMotion.ARM_REACH_PX * scale;
        out[0] = (float) (shoulder[0] - len * Math.cos(back));
        out[1] = (float) (shoulder[1] - len * Math.sin(back));
        return new float[]{out[0], out[1]};
    }

    private static void pushUpTimeline() {
        PushUpMotion.Pose pose = new PushUpMotion.Pose();
        PushUpMotion.sample(0.0F, pose);
        near(pose.tiltDeg, 0, 1e-6, "Start im Stand");
        PushUpMotion.sample(PushUpMotion.ENTRY, pose);
        near(pose.tiltDeg, PushUpMotion.bodyTiltDeg(0), 1e-3, "nach 0,4 s in der Plank");

        // Schleife: 0,55 runter, 0,1 halten, 0,55 hoch, 0,1 halten
        float s = PushUpMotion.ENTRY;
        near(PushUpMotion.depth(s + 0.55F), 1, 1e-4, "unten nach 0,55 s");
        near(PushUpMotion.depth(s + 0.6F), 1, 1e-6, "unten halten");
        near(PushUpMotion.depth(s + 1.2F), 0, 1e-4, "oben nach 1,2 s");
        near(PushUpMotion.depth(s + 1.25F), 0, 1e-6, "oben halten");
        check(PushUpMotion.completedReps(s + 1.29F) == 0 && PushUpMotion.completedReps(s + 1.31F) == 1, "Wiederholungen zählen");
        check(PushUpMotion.completedReps(s + 13 * 1.3F + 0.01F) == 13, "13 Wiederholungen");

        // Stetigkeit: keine Sprünge pro Frame (1/120 s)
        float prevTilt = 0, prevArm = 0, prevScale = 1;
        for (int i = 0; i <= 120 * 8; i++) {
            float t = i / 120.0F;
            PushUpMotion.sample(t, pose);
            if (i > 0) {
                check(Math.abs(pose.tiltDeg - prevTilt) < 3.0, "Kippung springt bei t=" + t);
                check(Math.abs(pose.rightArmPitch - prevArm) < 0.06, "Arm springt bei t=" + t);
                check(Math.abs(pose.armScale - prevScale) < 0.03, "Armlänge springt bei t=" + t);
            }
            prevTilt = pose.tiltDeg;
            prevArm = pose.rightArmPitch;
            prevScale = pose.armScale;
        }
    }
}
