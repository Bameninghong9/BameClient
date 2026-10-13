package com.bame.client.emotes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft ohne Minecraft die Regeln aus {@link CampfireLogic} (Variante pro Durchgang, Runden, Gastgeber, Beitreten,
 * Feuerposition) und die Zeitleiste aus {@link CampfireMotion} (flüssig in 1-ms-Schritten inkl. Schleifennähte,
 * Marshmallow über dem Feuer bzw. am Mund, Farben).
 */
public class CampfireTest {

    private static int checks = 0;

    public static void main(String[] args) {
        variantDeterministic();
        variantSplit();
        variantRounding();
        variantExactServerTime();
        ownEmoteEcho();
        firePosition();
        hostSelectionAndTieBreak();
        joinWithinRadius();
        startCheck();
        hostLeavesNextHost();
        firePositionStable();
        orderIndependent();
        twoSeparateFires();
        passTimeline();
        poseContinuity();
        seamsMatch();
        mallowOverFire();
        mallowAtMouth();
        mallowStates();
        colors();
        crossedEvents();
        propModels();
        mallowAboveFlames();
        System.out.println("CAMPFIRE TESTS PASSED (" + checks + " checks)");
    }

    // ---------------- Hilfen ----------------

    private static final String ID_A = "0000000000000000000000000000000a";
    private static final String ID_B = "0000000000000000000000000000000b";
    private static final String ID_C = "0000000000000000000000000000000c";

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void near(double a, double b, double eps, String what) {
        check(Math.abs(a - b) <= eps, what + ": " + a + " != " + b);
    }

    private static CampfireLogic.Participant part(String id, long start, double x, double z, float yaw) {
        return new CampfireLogic.Participant(id, start, x, 64.0, z, yaw);
    }

    // ---------------- Variante ----------------

    private static void variantDeterministic() {
        for (int pass = 0; pass < 50; pass++) {
            boolean a = CampfireLogic.burns(ID_A, 1_700_000_123_456L, pass);
            boolean b = CampfireLogic.burns(ID_A, 1_700_000_123_456L, pass);
            check(a == b, "gleiche Eingaben, gleiche Variante (Durchgang " + pass + ")");
        }
        // Die Durchgänge unterscheiden sich (sonst wäre es keine Abwechslung)
        int burnt = 0;
        for (int pass = 0; pass < 40; pass++) if (CampfireLogic.burns(ID_B, 123_400L, pass)) burnt++;
        check(burnt > 0 && burnt < 40, "Varianten wechseln über die Durchgänge: " + burnt);
    }

    private static void variantSplit() {
        Random r = new Random(42);
        int n = 40_000, burnt = 0;
        for (int i = 0; i < n; i++) {
            String id = String.format("%016x%016x", r.nextLong(), r.nextLong());
            long start = 1_700_000_000_000L + (long) (r.nextDouble() * 1.0E9);
            if (CampfireLogic.burns(id, start, r.nextInt(30))) burnt++;
        }
        double share = burnt / (double) n;
        check(share > 0.28 && share < 0.32, "ca. 30 % verbrennen: " + share);
        // auch pro Spieler über viele Durchgänge
        int b2 = 0;
        for (int pass = 0; pass < 10_000; pass++) if (CampfireLogic.burns(ID_C, 987_654_321L, pass)) b2++;
        check(b2 > 2_700 && b2 < 3_300, "ca. 30 % über die Durchgänge: " + b2);
    }

    private static void variantRounding() {
        // Zeitbasis auf 1 s gerundet: Abweichungen innerhalb derselben Sekunde ändern nichts
        check(CampfireLogic.SEED_ROUNDING_MS == 1_000L, "1-s-Raster");
        check(CampfireLogic.roundedStart(10_001L) == CampfireLogic.roundedStart(10_499L), "Rundung 1");
        check(CampfireLogic.roundedStart(9_500L) == CampfireLogic.roundedStart(10_499L), "Rundung 2");
        check(CampfireLogic.roundedStart(10_500L) != CampfireLogic.roundedStart(10_499L), "Rundung 3");
        for (int pass = 0; pass < 30; pass++) {
            check(CampfireLogic.burns(ID_A, 55_520L, pass) == CampfireLogic.burns(ID_A, 55_540L, pass),
                    "gleiche Variante bei 20 ms Abweichung");
            check(CampfireLogic.burns(ID_A, 55_100L, pass) == CampfireLogic.burns(ID_A, 55_250L, pass),
                    "gleiche Variante bei 150 ms Abweichung (Schätzung vor dem Echo)");
        }
        // andere UUID bzw. andere Startzeit: andere Folge
        int diff = 0;
        for (int pass = 0; pass < 200; pass++) {
            if (CampfireLogic.burns(ID_A, 55_500L, pass) != CampfireLogic.burns(ID_B, 55_500L, pass)) diff++;
        }
        check(diff > 30, "UUID geht in den Zufall ein: " + diff);
    }

    /**
     * Alle Clients haben dieselbe exakte Serverzeit (Beobachter: emote-Nachricht, der Spieler selbst: deren Echo). Ihre
     * lokalen Startzeiten unterscheiden sich (Uhrenversatz, Laufzeit) – für die Variante zählt nur die Serverzeit.
     */
    private static void variantExactServerTime() {
        Random r = new Random(7);
        for (int i = 0; i < 2_000; i++) {
            String id = String.format("%016x%016x", r.nextLong(), r.nextLong());
            long server = 1_760_000_000_000L + (long) (r.nextDouble() * 1.0E9); // beliebige ms, auch nahe an Rastergrenzen
            for (int pass = 0; pass < 8; pass++) {
                boolean watcher = CampfireLogic.burns(id, server, pass);
                boolean self = CampfireLogic.burns(id, server, pass); // eigener Client nach dem Echo
                check(watcher == self, "exakte Serverzeit: überall dieselbe Variante");
            }
        }
        // Rückfall-Schätzung genau an einer Rastergrenze darf abweichen; mit Echo ist das ausgeschlossen
        long server = 1_760_000_000_500L; // genau auf der Grenze
        int differs = 0;
        for (int pass = 0; pass < 64; pass++) {
            if (CampfireLogic.burns(ID_A, server, pass) != CampfireLogic.burns(ID_A, server - 40L, pass)) differs++;
        }
        check(differs > 0, "Schätzung über die Rastergrenze hinweg kann abweichen (daher das Echo): " + differs);
        // Wie viele Schätzungen mit 80 ms Fehler treffen dieselbe Variante wie die exakte Zeit? (vorher 100-ms-Raster)
        int same = 0, n = 20_000;
        for (int i = 0; i < n; i++) {
            long s = 1_760_000_000_000L + (long) (r.nextDouble() * 1.0E9);
            if (CampfireLogic.roundedStart(s) == CampfireLogic.roundedStart(s - 80L)) same++;
        }
        check(same > n * 0.9, "1-s-Raster: Schätzung mit 80 ms Fehler fast immer gleich: " + same + "/" + n);
    }

    private static void ownEmoteEcho() {
        long start = 5_000_000L;
        // ohne Uhrenschätzung: nur kurz nach dem Start
        check(EmoteEchoLogic.accepts(start, start + 120L, 9_999_000L, false, 0L), "Echo nach 120 ms");
        check(EmoteEchoLogic.accepts(start, start + 4_900L, 9_999_000L, false, 0L), "Echo nach 4,9 s");
        check(!EmoteEchoLogic.accepts(start, start + 5_100L, 9_999_000L, false, 0L), "zu spät: veraltet");
        check(!EmoteEchoLogic.accepts(start, start - 5L, 9_999_000L, false, 0L), "vor dem Start empfangen: früheres PLAY");
        check(!EmoteEchoLogic.accepts(start, start + 100L, 0L, false, 0L), "ohne Serverzeit");
        check(!EmoteEchoLogic.accepts(0L, 100L, 9_999_000L, false, 0L), "kein lokales Emote");
        // mit Uhrenschätzung: Serverzeit muss zur geschätzten Serverzeit des Starts passen
        long est = 9_999_000L;
        check(EmoteEchoLogic.accepts(start, start + 150L, est + 60L, true, est), "passend (Laufzeit)");
        check(EmoteEchoLogic.accepts(start, start + 1_400L, est + 1_250L, true, est), "passend (Sende-Limit)");
        check(EmoteEchoLogic.accepts(start, start + 100L, est - 200L, true, est), "Schätzung etwas zu spät");
        check(!EmoteEchoLogic.accepts(start, start + 100L, est - 2_000L, true, est), "Echo eines früheren Starts");
        check(!EmoteEchoLogic.accepts(start, start + 100L, est + 4_000L, true, est), "Serverzeit passt nicht");
    }

    // ---------------- Runden ----------------

    private static void firePosition() {
        double[] f = CampfireLogic.firePosition(0.0, 64.0, 0.0, 0.0F);
        near(f[0], 0.0, 1e-9, "Yaw 0: x");
        near(f[2], 1.3, 1e-9, "Yaw 0: +Z (Süden)");
        f = CampfireLogic.firePosition(10.0, 64.0, 10.0, 90.0F);
        near(f[0], 8.7, 1e-9, "Yaw 90: -X (Westen)");
        near(f[2], 10.0, 1e-9, "Yaw 90: z");
        near(CampfireLogic.yawTo(0, 0, 0, 5), 0.0, 1e-4, "yawTo Süden");
        near(CampfireLogic.yawTo(0, 0, -5, 0), 90.0, 1e-4, "yawTo Westen");
    }

    private static void hostSelectionAndTieBreak() {
        List<CampfireLogic.Round> rounds = new ArrayList<>();
        CampfireLogic.update(rounds, List.of(part(ID_B, 1_000L, 0, 0, 0)));
        CampfireLogic.update(rounds, List.of(part(ID_B, 1_000L, 0, 0, 0), part(ID_A, 5_000L, 1.5, 1.3, 90)));
        check(rounds.size() == 1, "eine Runde");
        check(rounds.get(0).host().id.equals(ID_B), "frühester Start ist Gastgeber");

        // Gleichstand: kleinere UUID
        List<CampfireLogic.Round> r2 = new ArrayList<>();
        CampfireLogic.update(r2, List.of(part(ID_B, 2_000L, 0, 0, 0), part(ID_A, 2_000L, 1.0, 1.3, 90)));
        check(r2.size() == 1, "Gleichstand: eine Runde");
        check(r2.get(0).host().id.equals(ID_A), "Gleichstand: kleinere UUID ist Gastgeber");
        // Feuer kommt vom zuerst zugeordneten (gleiche Startzeit -> kleinere UUID): A bei (1, 0) schaut nach Westen
        near(r2.get(0).fireX, 1.0 - 1.3, 1e-9, "Feuer vor dem Gastgeber");
    }

    private static void joinWithinRadius() {
        List<CampfireLogic.Round> rounds = new ArrayList<>();
        CampfireLogic.update(rounds, List.of(part(ID_A, 1_000L, 0, 0, 0)));
        CampfireLogic.Round r = rounds.get(0);
        // B 2,8 Blöcke vom Feuer (andere Clients sind großzügig: bis 3 Blöcke) -> setzt sich dazu
        CampfireLogic.update(rounds, List.of(part(ID_A, 1_000L, 0, 0, 0), part(ID_B, 3_000L, 2.8, 1.3, 0)));
        check(rounds.size() == 1 && r.members.size() == 2, "B setzt sich ans Feuer");
        CampfireLogic.Member b = r.members.get(ID_B);
        // B schaut zum Feuer (Feuer bei (0, 1.3), B bei (2.8, 1.3): Blick nach Westen = 90)
        near(b.anchorYaw, 90.0, 1e-3, "B schaut zum Feuer");
        near(b.startYaw, 0.0, 1e-6, "Start-Yaw von B gemerkt");
        // C 3,2 Blöcke weg -> eigenes Feuer
        CampfireLogic.update(rounds, List.of(part(ID_A, 1_000L, 0, 0, 0), part(ID_B, 3_000L, 2.8, 1.3, 0),
                part(ID_C, 4_000L, -3.2, 1.3, 180)));
        check(rounds.size() == 2, "C außerhalb von 3 Blöcken: neues Feuer");
        CampfireLogic.Round rc = CampfireLogic.roundOf(rounds, ID_C, 4_000L);
        check(rc != null && rc != r && rc.host().id.equals(ID_C), "C ist Gastgeber seines Feuers");
        // Höhenunterschied: kein gemeinsames Feuer
        List<CampfireLogic.Round> r3 = new ArrayList<>();
        CampfireLogic.update(r3, List.of(part(ID_A, 1_000L, 0, 0, 0),
                new CampfireLogic.Participant(ID_B, 2_000L, 1.0, 64.0 + 3.0, 1.3, 0)));
        check(r3.size() == 2, "anderes Stockwerk: eigenes Feuer");
    }

    private static void startCheck() {
        List<CampfireLogic.Round> rounds = new ArrayList<>();
        CampfireLogic.update(rounds, List.of(part(ID_A, 1_000L, 0, 0, 0))); // Feuer bei (0, 1.3)
        check(CampfireLogic.checkStart(rounds, ID_B, 2.0, 64.0, 1.3) == CampfireLogic.StartResult.JOIN, "2,0 Blöcke: dazusetzen");
        check(CampfireLogic.checkStart(rounds, ID_B, 2.2, 64.0, 1.3) == CampfireLogic.StartResult.JOIN, "2,2 Blöcke: gerade noch");
        check(CampfireLogic.checkStart(rounds, ID_B, 2.5, 64.0, 1.3) == CampfireLogic.StartResult.TOO_FAR, "2,5 Blöcke: zu weit");
        check(CampfireLogic.checkStart(rounds, ID_B, 3.0, 64.0, 1.3) == CampfireLogic.StartResult.TOO_FAR, "3,0 Blöcke: zu weit");
        check(CampfireLogic.checkStart(rounds, ID_B, 3.3, 64.0, 1.3) == CampfireLogic.StartResult.NEW_FIRE, "3,3 Blöcke: eigenes Feuer");
        check(CampfireLogic.checkStart(new ArrayList<>(), ID_B, 0, 64, 0) == CampfireLogic.StartResult.NEW_FIRE, "kein Feuer");
        // Die eigene alte Runde (nur man selbst) zählt nicht
        check(CampfireLogic.checkStart(rounds, ID_A, 0.0, 64.0, 0.0) == CampfireLogic.StartResult.NEW_FIRE, "eigene Runde zählt nicht");
    }

    private static void hostLeavesNextHost() {
        List<CampfireLogic.Round> rounds = new ArrayList<>();
        CampfireLogic.Participant a = part(ID_A, 1_000L, 0, 0, 0);
        CampfireLogic.Participant b = part(ID_B, 3_000L, 1.5, 1.3, 90);
        CampfireLogic.Participant c = part(ID_C, 2_000L, -1.5, 1.3, -90);
        CampfireLogic.update(rounds, List.of(a));
        CampfireLogic.update(rounds, List.of(a, b, c));
        CampfireLogic.Round r = rounds.get(0);
        check(r.members.size() == 3 && r.isBig(), "drei Teilnehmer, großes Feuer");
        double fx = r.fireX, fz = r.fireZ;
        // A steht auf: C (Start 2000) ist der nächste nach Startzeit
        CampfireLogic.Changes ch = CampfireLogic.update(rounds, List.of(b, c));
        check(ch.removed.isEmpty() && rounds.size() == 1 && rounds.get(0) == r, "Feuer bleibt");
        check(r.host().id.equals(ID_C), "nächster Gastgeber nach Startzeit");
        check(!r.isBig(), "zwei Teilnehmer: normales Feuer");
        near(r.fireX, fx, 0, "Feuer bleibt an seiner Position (x)");
        near(r.fireZ, fz, 0, "Feuer bleibt an seiner Position (z)");
        // C startet neu (neue Startzeit) -> gilt als neuer Teilnehmer, B wird Gastgeber
        CampfireLogic.Participant c2 = part(ID_C, 9_000L, -1.5, 1.3, -90);
        CampfireLogic.update(rounds, List.of(b, c2));
        check(rounds.size() == 1 && r.host().id.equals(ID_B), "neu gestartetes Emote verliert den Vorrang");
        // alle weg -> Runde aufgelöst
        ch = CampfireLogic.update(rounds, List.of());
        check(rounds.isEmpty() && ch.removed.size() == 1, "alle aufgestanden: Feuer verschwindet");
    }

    private static void firePositionStable() {
        List<CampfireLogic.Round> rounds = new ArrayList<>();
        Random rnd = new Random(3);
        CampfireLogic.update(rounds, List.of(part(ID_A, 1_000L, 0, 0, 30)));
        CampfireLogic.Round r = rounds.get(0);
        double fx = r.fireX, fy = r.fireY, fz = r.fireZ;
        for (int i = 0; i < 200; i++) {
            // leicht zitternde Positionen/Yaws (Interpolation), Teilnehmer kommen und gehen
            List<CampfireLogic.Participant> ps = new ArrayList<>();
            ps.add(part(ID_A, 1_000L, rnd.nextGaussian() * 0.05, rnd.nextGaussian() * 0.05, 30 + (float) rnd.nextGaussian() * 20));
            if (i % 3 != 0) ps.add(part(ID_B, 2_000L + i, 1.0, 1.0, 0));
            CampfireLogic.update(rounds, ps);
            check(rounds.size() == 1 && rounds.get(0) == r, "Runde bleibt dieselbe");
            check(r.fireX == fx && r.fireY == fy && r.fireZ == fz, "Feuerposition unverändert");
        }
    }

    private static void orderIndependent() {
        List<CampfireLogic.Participant> ps = List.of(
                part(ID_C, 3_000L, 0.8, 1.0, 10), part(ID_A, 1_000L, 0, 0, 0), part(ID_B, 1_000L, 0.3, 0.2, 5),
                part("0000000000000000000000000000000d", 2_000L, 10, 10, 0),
                part("0000000000000000000000000000000e", 2_500L, 11, 10.5, 0));
        String first = null;
        Random rnd = new Random(9);
        for (int i = 0; i < 30; i++) {
            List<CampfireLogic.Participant> shuffled = new ArrayList<>(ps);
            Collections.shuffle(shuffled, rnd);
            List<CampfireLogic.Round> rounds = new ArrayList<>();
            CampfireLogic.update(rounds, shuffled);
            String d = describe(rounds);
            if (first == null) first = d;
            check(d.equals(first), "Runden hängen nicht von der Reihenfolge ab: " + d + " vs " + first);
        }
        check(first.split(";").length == 2, "zwei Runden: " + first);
    }

    private static String describe(List<CampfireLogic.Round> rounds) {
        List<String> parts = new ArrayList<>();
        for (CampfireLogic.Round r : rounds) {
            parts.add(String.format("%.3f,%.3f:%s:%s", r.fireX, r.fireZ, r.host().id, r.members.keySet()));
        }
        Collections.sort(parts);
        return String.join(";", parts);
    }

    private static void twoSeparateFires() {
        List<CampfireLogic.Round> rounds = new ArrayList<>();
        CampfireLogic.update(rounds, List.of(part(ID_A, 1_000L, 0, 0, 0), part(ID_B, 1_500L, 20, 0, 0)));
        check(rounds.size() == 2, "weit auseinander: zwei Feuer");
        for (CampfireLogic.Round r : rounds) check(r.members.size() == 1, "je ein Teilnehmer");
    }

    // ---------------- Zeitleiste ----------------

    private static void passTimeline() {
        check(CampfireMotion.pass(0.0F) == 0 && CampfireMotion.pass(13.999F) == 0, "erster Durchgang");
        check(CampfireMotion.pass(14.0F) == 1, "zweiter Durchgang ab 14 s");
        near(CampfireMotion.passTime(14.0F), CampfireMotion.INTRO, 1e-5, "Folgedurchgang beginnt beim Rösten");
        check(CampfireMotion.pass(14.0F + CampfireMotion.PASS) == 2, "dritter Durchgang");
        near(CampfireMotion.passTime(14.0F + CampfireMotion.PASS + 1.0F), CampfireMotion.INTRO + 1.0F, 1e-4, "Zeit im dritten");
        CampfireMotion.Pose p = new CampfireMotion.Pose();
        CampfireMotion.sample(0.0F, false, p);
        near(p.sit, 0.0, 1e-6, "Start: steht noch");
        near(p.propScale, 0.0, 1e-6, "Start: Props noch unsichtbar");
        CampfireMotion.sample(0.6F, false, p);
        near(p.sit, 1.0, 1e-6, "nach 0,6 s sitzt er");
        near(p.propScale, 1.0, 1e-6, "nach 0,6 s volle Größe");
        CampfireMotion.sample(20.0F, false, p);
        near(p.sit, 1.0, 1e-6, "Folgedurchgang: kein erneutes Hinsetzen");
    }

    /** Werte der Pose und der daraus berechneten Stock-/Arm-/Kopfgeometrie (Feuer 1,3 Blöcke geradeaus). */
    private static double[] solved(float t, boolean burns, CampfireMotion.Pose p, CampfireMotion.Stick s) {
        CampfireMotion.sample(t, burns, p);
        double[] fire = new double[3];
        CampfireMotion.defaultFireTarget(fire);
        double[] target = new double[3];
        CampfireMotion.stickTarget(p, fire, target);
        float rx = -CampfireMotion.SHOULDER_X, ry = CampfireMotion.SHOULDER_Y_WIDE + p.armDY, rz = p.bodyDZ;
        CampfireMotion.aimStick(rx, ry, rz, p.rArmPitch, p.rArmYaw, p.rArmRoll, CampfireMotion.GRIP_X_WIDE,
                target[0], target[1], target[2], s);
        float[] left = new float[2];
        CampfireMotion.leftArm(p, CampfireMotion.SHOULDER_X, ry, rz, s.mallow, left);
        float[] head = new float[2];
        CampfireMotion.lookAt(0.0F, p.bodyDY, p.bodyDZ, s.mallow, head);
        return new double[]{p.rArmPitch, p.rArmYaw, p.rArmRoll, left[0], left[1], p.lArmRoll, p.lean, p.headPitchAdd,
                p.headRollAdd, head[0] + p.headPitchAdd, head[1], p.bodyDY, p.bodyDZ, p.armDY, p.legPitch,
                s.mallow[0], s.mallow[1], s.mallow[2], s.grip[0], s.grip[1], s.grip[2], p.stickTwistDeg * Math.PI / 180.0};
    }

    /** Größte erlaubte Änderung pro 1 ms: Winkel (rad) bzw. Pixel. */
    private static final double MAX_ANGLE_STEP = 0.03;
    private static final double MAX_PX_STEP = 0.35;
    private static final boolean[] IS_PX = {false, false, false, false, false, false, false, false, false, false, false,
            true, true, true, false, true, true, true, true, true, true, false};

    private static void poseContinuity() {
        CampfireMotion.Pose p = new CampfireMotion.Pose();
        CampfireMotion.Stick s = new CampfireMotion.Stick();
        // Mehrere Startwerte: Varianten pro Durchgang gemischt (A/B wechseln an den Nähten)
        String[] ids = {ID_A, ID_B, ID_C};
        long[] starts = {1_000L, 77_700L, 1_234_500L};
        int seamsAB = 0;
        for (int k = 0; k < ids.length; k++) {
            float end = 14.0F + 4 * CampfireMotion.PASS;
            double[] prev = null;
            float maxA = 0, maxP = 0;
            for (int ms = 0; ms <= (int) (end * 1000); ms++) {
                float t = ms / 1000.0F;
                int pass = CampfireMotion.pass(t);
                boolean burns = CampfireLogic.burns(ids[k], starts[k], pass);
                double[] cur = solved(t, burns, p, s);
                if (prev != null) {
                    for (int i = 0; i < cur.length; i++) {
                        double d = Math.abs(cur[i] - prev[i]);
                        if (IS_PX[i]) maxP = (float) Math.max(maxP, d);
                        else maxA = (float) Math.max(maxA, d);
                        check(d <= (IS_PX[i] ? MAX_PX_STEP : MAX_ANGLE_STEP),
                                "Sprung bei t=" + t + " Kanal " + i + ": " + d + " (Variante " + (burns ? "B" : "A") + ")");
                    }
                }
                prev = cur;
            }
            for (int pass = 1; pass < 5; pass++) {
                if (CampfireLogic.burns(ids[k], starts[k], pass) != CampfireLogic.burns(ids[k], starts[k], pass - 1)) seamsAB++;
            }
            System.out.println("  Stetigkeit " + ids[k].substring(28) + ": max. Winkelschritt " + maxA + " rad/ms, max. " + maxP + " px/ms");
        }
        check(seamsAB > 0, "mindestens eine Naht mit Variantenwechsel geprüft");
        // Beide Varianten einzeln über viele Durchgänge (inkl. Naht B->B und A->A)
        for (boolean burns : new boolean[]{false, true}) {
            double[] prev = null;
            for (int ms = 0; ms <= 45_000; ms++) {
                double[] cur = solved(ms / 1000.0F, burns, p, s);
                if (prev != null) {
                    for (int i = 0; i < cur.length; i++) {
                        check(Math.abs(cur[i] - prev[i]) <= (IS_PX[i] ? MAX_PX_STEP : MAX_ANGLE_STEP),
                                "Sprung (nur " + (burns ? "B" : "A") + ") bei " + ms + " ms, Kanal " + i);
                    }
                }
                prev = cur;
            }
        }
    }

    private static void seamsMatch() {
        CampfireMotion.Pose p = new CampfireMotion.Pose();
        CampfireMotion.Stick s = new CampfireMotion.Stick();
        // Ende eines Durchgangs == Anfang des nächsten, unabhängig von der Variante
        for (int pass = 1; pass <= 3; pass++) {
            float seam = 14.0F + (pass - 1) * CampfireMotion.PASS;
            for (boolean before : new boolean[]{false, true}) {
                for (boolean after : new boolean[]{false, true}) {
                    double[] a = solved(seam - 0.0005F, before, p, s);
                    double[] b = solved(seam + 0.0005F, after, p, s);
                    for (int i = 0; i < a.length; i++) {
                        near(a[i], b[i], IS_PX[i] ? 0.05 : 0.01, "Naht " + pass + " (" + before + "->" + after + ") Kanal " + i);
                    }
                }
            }
        }
        // Hinsetzen endet genau in der Röst-Pose wie ein Folgedurchgang beginnt
        double[] introEnd = solved(CampfireMotion.INTRO, false, p, s);
        double[] next = solved(14.0F, true, p, s);
        for (int i = 0; i < introEnd.length; i++) {
            if (i == 6 || i == 7 || i == 9 || i == 21) continue; // Atmen und Drehen hängen von der Gesamtzeit ab
            near(introEnd[i], next[i], IS_PX[i] ? 0.3 : 0.03, "Röst-Pose Kanal " + i);
        }
    }

    private static void mallowOverFire() {
        CampfireMotion.Pose p = new CampfireMotion.Pose();
        CampfireMotion.Stick s = new CampfireMotion.Stick();
        double[] fire = new double[3];
        CampfireMotion.defaultFireTarget(fire);
        double ground = CampfireMotion.MODEL_ORIGIN_UP_PX;
        double halfBlockPx = 0.5 / CampfireMotion.BLOCKS_PER_PX;
        for (float t = 1.0F; t < 6.0F; t += 0.25F) {
            solved(t, false, p, s);
            double dx = s.mallow[0] - fire[0], dz = s.mallow[2] - fire[2];
            check(Math.abs(dx) < halfBlockPx * 0.6 && Math.abs(dz) < halfBlockPx * 0.6,
                    "Marshmallow über den Flammen bei t=" + t + ": dx=" + dx + " dz=" + dz);
            double height = (ground - s.mallow[1]) * CampfireMotion.BLOCKS_PER_PX;
            check(height > 0.6 && height < 1.2, "Höhe über dem Boden " + height);
            check(s.stretch <= 1.05F, "Gastgeber: Stock (fast) nicht gestreckt: " + s.stretch);
        }
        // Teilnehmer 2,2 Blöcke vom Feuer: Stock wird gestreckt, Marshmallow trotzdem über dem Feuer
        double far = 2.2 / CampfireMotion.BLOCKS_PER_PX;
        double[] farFire = {0.0, fire[1], -far};
        double[] farTarget = farFire.clone();
        CampfireMotion.pullTowardPlayer(farTarget);
        CampfireMotion.sample(3.0F, false, CampfireMotion.farness(2.2), p);
        double[] target = new double[3];
        CampfireMotion.stickTarget(p, farTarget, target);
        float pitch = p.rArmPitch;
        CampfireMotion.aimStick(-CampfireMotion.SHOULDER_X, CampfireMotion.SHOULDER_Y_WIDE + p.armDY, p.bodyDZ, pitch,
                p.rArmYaw, p.rArmRoll, CampfireMotion.GRIP_X_WIDE, target[0], target[1], target[2], s);
        check(s.stretch > 1.0F && s.stretch <= CampfireMotion.MAX_STRETCH, "gestreckt: " + s.stretch);
        double dzFar = Math.abs(s.mallow[2] - farFire[2]) * CampfireMotion.BLOCKS_PER_PX;
        check(dzFar < 0.3, "auch bei 2,2 Blöcken über dem Feuer: " + dzFar);
    }

    private static void mallowAtMouth() {
        CampfireMotion.Pose p = new CampfireMotion.Pose();
        CampfireMotion.Stick s = new CampfireMotion.Stick();
        solved(10.0F, false, p, s);
        double mouthY = p.bodyDY - 1.5, mouthZ = p.bodyDZ - 4.5;
        double d = Math.sqrt(s.mallow[0] * s.mallow[0] + Math.pow(s.mallow[1] - mouthY, 2) + Math.pow(s.mallow[2] - mouthZ, 2));
        check(d < 2.5, "Marshmallow beim Essen am Mund: " + d + " px");
        check(s.slide <= 0.0F && s.stretch == 1.0F, "Stock rutscht durch die Hand statt zu strecken");
    }

    private static void mallowStates() {
        CampfireMotion.Pose p = new CampfireMotion.Pose();
        CampfireMotion.sample(0.6F, false, p);
        near(p.toast, 0.0, 1e-6, "weiß zu Beginn");
        CampfireMotion.sample(4.3F, false, p);
        check(p.toast > 0.4 && p.toast < 0.6, "halb geröstet: " + p.toast);
        CampfireMotion.sample(8.0F, false, p);
        near(p.toast, 1.0, 1e-5, "goldbraun nach 8 s");
        check(p.burnt == 0.0F && p.burning == 0.0F, "A verbrennt nicht");
        CampfireMotion.sample(10.2F, false, p);
        check(p.mallowScale > 0.5F && p.mallowScale < 0.6F, "nach dem ersten Bissen kleiner: " + p.mallowScale);
        CampfireMotion.sample(11.0F, false, p);
        near(p.mallowScale, 0.0, 1e-6, "aufgegessen");
        CampfireMotion.sample(13.0F, false, p);
        near(p.mallowScale, 1.0, 1e-6, "neuer Marshmallow");
        near(p.toast, 0.0, 1e-6, "neuer ist weiß");

        CampfireMotion.sample(7.0F, true, p);
        check(p.burning > 0.9F, "B brennt");
        CampfireMotion.sample(8.4F, true, p);
        check(p.burning == 0.0F && p.burnt > 0.99F, "ausgepustet und schwarz");
        CampfireMotion.sample(10.0F, true, p);
        near(p.mallowScale, 0.0, 1e-6, "ins Feuer geworfen");
        CampfireMotion.sample(13.5F, true, p);
        check(p.burnt == 0.0F && p.toast == 0.0F && p.mallowScale == 1.0F, "B: neuer weißer Marshmallow");

        // Farbwechsel beim Aufstecken nur, solange der Marshmallow unsichtbar ist
        for (boolean burns : new boolean[]{false, true}) {
            CampfireMotion.sample(CampfireMotion.ATTACH - 0.001F, burns, p);
            check(p.mallowScale < 0.01F, "vor dem Aufstecken unsichtbar");
            CampfireMotion.sample(CampfireMotion.ATTACH + 0.001F, burns, p);
            check(p.mallowScale < 0.1F, "wächst nach dem Aufstecken erst");
        }
    }

    private static void colors() {
        int white = CampfireMotion.mallowColor(0.0F, 0.0F);
        int gold = CampfireMotion.mallowColor(0.5F, 0.0F);
        int brown = CampfireMotion.mallowColor(1.0F, 0.0F);
        int black = CampfireMotion.mallowColor(0.6F, 1.0F);
        check(lum(white) > lum(gold) && lum(gold) > lum(brown) && lum(brown) > lum(black), "weiß > hellgold > goldbraun > schwarz");
        check((white >>> 24) == 0xFF && (black >>> 24) == 0xFF, "deckend");
        check(lum(black) < 50, "verbrannt ist fast schwarz");
        check(((brown >> 16) & 0xFF) > ((brown) & 0xFF) + 80, "goldbraun ist warm");
    }

    private static int lum(int c) {
        return (((c >> 16) & 0xFF) * 3 + ((c >> 8) & 0xFF) * 6 + (c & 0xFF)) / 10;
    }

    // ---------------- eigene Modelle (Stamm, Feuer, Flammen) ----------------

    private static final String MODEL_DIR = "src/main/resources/assets/bameclient/emotes/campfire/";

    /** Würfel aus einer .geo.json (vom Generator eine Zeile pro Würfel) samt Knochenname. */
    private record Cube(String bone, double[] origin, double[] size, double[] pivot, double[] rotation) {}

    private static List<Cube> loadCubes(String file) {
        String text;
        try {
            text = Files.readString(Path.of(MODEL_DIR + file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Modell fehlt: " + file + " (" + e.getMessage() + ")");
        }
        List<Cube> out = new ArrayList<>();
        Pattern bone = Pattern.compile("\"name\":\\s*\"([^\"]+)\"");
        Pattern cube = Pattern.compile("\\{\"origin\":\\s*\\[([^\\]]*)\\],\\s*\"size\":\\s*\\[([^\\]]*)\\]"
                + "(?:,\\s*\"pivot\":\\s*\\[([^\\]]*)\\],\\s*\"rotation\":\\s*\\[([^\\]]*)\\])?");
        String currentBone = null;
        for (String line : text.split("\n")) {
            Matcher b = bone.matcher(line);
            if (b.find()) currentBone = b.group(1);
            Matcher c = cube.matcher(line);
            if (c.find()) {
                out.add(new Cube(currentBone, nums(c.group(1)), nums(c.group(2)),
                        c.group(3) != null ? nums(c.group(3)) : null, c.group(4) != null ? nums(c.group(4)) : null));
            }
        }
        check(!out.isEmpty(), "Würfel gelesen: " + file);
        return out;
    }

    private static double[] nums(String s) {
        String[] p = s.split(",");
        double[] v = new double[p.length];
        for (int i = 0; i < p.length; i++) v[i] = Double.parseDouble(p[i].trim());
        return v;
    }

    /** Ecken eines Würfels im Knochenraum (gleiche Drehung wie BedrockCosmeticModel mit blockbenchAxisFix). */
    private static List<double[]> corners(Cube c) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double[] p = {c.origin()[0] + ((i & 1) != 0 ? c.size()[0] : 0), c.origin()[1] + ((i & 2) != 0 ? c.size()[1] : 0),
                    c.origin()[2] + ((i & 4) != 0 ? c.size()[2] : 0)};
            if (c.rotation() != null) {
                double[] pv = c.pivot();
                double x = p[0] - pv[0], y = p[1] - pv[1], z = p[2] - pv[2];
                double a = Math.toRadians(-c.rotation()[0]);           // X (Vorzeichen wie blockbenchAxisFix)
                double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
                double b = Math.toRadians(c.rotation()[1]);            // Y
                double x2 = x * Math.cos(b) + z1 * Math.sin(b), z2 = -x * Math.sin(b) + z1 * Math.cos(b);
                double g = Math.toRadians(-c.rotation()[2]);           // Z
                double x3 = x2 * Math.cos(g) - y1 * Math.sin(g), y3 = x2 * Math.sin(g) + y1 * Math.cos(g);
                p = new double[]{x3 + pv[0], y3 + pv[1], z2 + pv[2]};
            }
            out.add(p);
        }
        return out;
    }

    private static void propModels() {
        double px = 1.0 / 16.0;
        // Sitzstamm: Oberseite genau auf der bisherigen Sitzhöhe (0,52 Blöcke), Boden bei 0, Länge 0,9, Tiefe 0,575
        check(CampfireMotion.LOG_HEIGHT == 0.52F, "Sitzhöhe unverändert 0,52 Blöcke");
        double top = -1e9, bottom = 1e9, maxX = 0, maxZ = 0;
        for (Cube c : loadCubes("seat_log.geo.json")) {
            for (double[] p : corners(c)) {
                top = Math.max(top, p[1]);
                bottom = Math.min(bottom, p[1]);
                maxX = Math.max(maxX, Math.abs(p[0]));
                if (c.rotation() == null) maxZ = Math.max(maxZ, Math.abs(p[2])); // ohne Aststummel
            }
        }
        near(top * px, CampfireMotion.LOG_HEIGHT, 1e-4, "Stamm-Oberseite = Sitzhöhe");
        near(bottom, 0.0, 1e-6, "Stamm liegt auf dem Boden");
        near(maxX * 2 * px, CampfireMotion.LOG_LENGTH, 1e-4, "Stamm-Länge");
        near(maxZ * 2 * px, CampfireMotion.LOG_DEPTH, 1e-4, "Stamm-Tiefe");
        // Sitzfläche: die Oberseite ist breit genug für die Oberschenkel (mind. 4 px tief)
        double seatDepth = 0;
        for (Cube c : loadCubes("seat_log.geo.json")) {
            if (c.rotation() == null && Math.abs(c.origin()[1] + c.size()[1] - top) < 1e-6) seatDepth = Math.max(seatDepth, c.size()[2]);
        }
        check(seatDepth >= 4.0, "Sitzfläche oben: " + seatDepth + " px");

        // Feuer: Steinring höchstens FIRE_RADIUS, rund 1 Block Grundfläche; Knochen wie im Renderer
        double r = 0, fireTop = 0, fireBottom = 1e9;
        java.util.Set<String> fireBones = new java.util.HashSet<>();
        int stones = 0;
        for (Cube c : loadCubes("campfire.geo.json")) {
            fireBones.add(c.bone());
            if ("heat".equals(c.bone())) {
                // Hitzeschein: flache Ebene knapp über dem Asche-Bett (translucent, ohne Tiefenschreiben)
                check(c.size()[1] == 0.0 && c.rotation() == null && c.origin()[1] > 0.25 && c.origin()[1] < 0.5,
                        "Hitzeschein flach über der Asche");
            }
            // Steine: im Boden versunkene, gedrehte Würfel im äußeren Ring (Hauptkörper und Buckel)
            if ("base".equals(c.bone()) && c.origin()[1] < 0.0 && c.size()[1] < 3.5 && c.rotation() != null
                    && Math.hypot(c.pivot()[0], c.pivot()[2]) > 5.0) stones++;
            for (double[] p : corners(c)) {
                r = Math.max(r, Math.hypot(p[0], p[2]));
                fireTop = Math.max(fireTop, p[1]);
                fireBottom = Math.min(fireBottom, p[1]);
            }
        }
        check(fireBones.equals(java.util.Set.of("base", "embers", "embers_b", "heat")),
                "Knochen base, embers, embers_b, heat: " + fireBones);
        check(stones >= 10, "Steinring aus mind. 10 Steinen: " + stones);
        check(r * px <= CampfireMotion.FIRE_RADIUS && r * px >= 0.42, "Feuer-Radius " + r * px);
        check(fireTop * px < 0.6 && fireBottom > -1.0, "Feuer-Unterbau flach: " + fireTop * px + " / " + fireBottom);

        // Flammen: Ebenen bis FLAME_PLANE_TOP, innerhalb des Steinrings; sichtbare Flammen darunter.
        // Jede Ebene ist ein eigener Knochen (f0..f7), der Renderer gibt jedem einen eigenen Frame-Versatz.
        double flameTop = 0, flameR = 0, flameBottom = 1e9;
        java.util.Set<String> flameBones = new java.util.HashSet<>();
        for (Cube c : loadCubes("campfire_flames.geo.json")) {
            check(c.size()[2] == 0.0, "Flammen sind Ebenen");
            flameBones.add(c.bone());
            for (double[] p : corners(c)) {
                flameTop = Math.max(flameTop, p[1]);
                flameBottom = Math.min(flameBottom, p[1]);
                flameR = Math.max(flameR, Math.hypot(p[0], p[2]));
            }
        }
        check(flameBones.size() == CampfireMotion.FLAME_PLANES, "Flammen-Knochen: " + flameBones);
        for (int i = 0; i < CampfireMotion.FLAME_PLANES; i++) check(flameBones.contains("f" + i), "Flammen-Knochen f" + i);
        near(flameTop * px, CampfireMotion.FLAME_PLANE_TOP, 1e-4, "Oberkante Flammen-Ebenen");
        check(CampfireMotion.FLAME_VISIBLE_TOP <= CampfireMotion.FLAME_PLANE_TOP, "sichtbare Flammen in den Ebenen");
        check(flameBottom > 0.0, "Flammen über dem Boden");
        check(flameR < r, "Flammen innerhalb des Steinrings: " + flameR + " < " + r);
    }

    /** Marshmallow hängt beim Rösten über den Flammenspitzen und über der Feuermitte (normales Feuer). */
    private static void mallowAboveFlames() {
        CampfireMotion.Pose p = new CampfireMotion.Pose();
        CampfireMotion.Stick s = new CampfireMotion.Stick();
        double ground = CampfireMotion.MODEL_ORIGIN_UP_PX;
        double halfMallow = 1.5 * CampfireMotion.BLOCKS_PER_PX; // Würfel 3 px, mit dem Spieler skaliert
        double fireZ = -CampfireLogic.FIRE_DISTANCE / CampfireMotion.BLOCKS_PER_PX;
        double minBottom = 1e9;
        for (float t = 0.8F; t < 6.0F; t += 0.05F) {
            solved(t, false, p, s);
            double bottom = (ground - s.mallow[1]) * CampfireMotion.BLOCKS_PER_PX - halfMallow;
            minBottom = Math.min(minBottom, bottom);
            double horiz = Math.hypot(s.mallow[0], s.mallow[2] - fireZ) * CampfireMotion.BLOCKS_PER_PX;
            check(horiz < CampfireMotion.FIRE_RADIUS * 0.6, "Marshmallow über der Feuermitte: " + horiz);
        }
        check(minBottom > CampfireMotion.FLAME_VISIBLE_TOP,
                "Marshmallow über den Flammen: Unterkante " + minBottom + " > " + CampfireMotion.FLAME_VISIBLE_TOP);
        System.out.println("  Marshmallow-Unterkante beim Rösten mind. " + String.format("%.3f", minBottom)
                + " Blöcke, Flammen bis " + CampfireMotion.FLAME_VISIBLE_TOP);
    }

    private static void crossedEvents() {
        // Ereignis bei Durchgangszeit 9,85 im Durchgang 0 und 1
        check(CampfireMotion.crossed(9.8F, 9.9F, 0, 9.85F), "Ereignis im ersten Durchgang");
        float abs1 = 14.0F + (9.85F - CampfireMotion.INTRO);
        check(CampfireMotion.crossed(abs1 - 0.05F, abs1 + 0.01F, 1, 9.85F), "Ereignis im zweiten Durchgang");
        check(!CampfireMotion.crossed(abs1 + 0.01F, abs1 + 0.06F, 1, 9.85F), "nicht doppelt");
        check(!CampfireMotion.crossed(14.0F, 14.1F, 1, 0.0F), "Hinsetz-Puff nur im ersten Durchgang");
    }
}
