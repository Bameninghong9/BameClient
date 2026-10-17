package com.bame.client.emotes;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Prüft ohne Minecraft den fliegenden Teppich:
 * <ul>
 *   <li>Emote-Typ: IDs (Shop-ID = Enum-Name), Name und Beschreibung laut Auftrag, Laufen erlaubt, auswählbar.</li>
 *   <li>Kurven-Neigung und Glättung ({@link FlyingCarpetLogic#step}): höchstens ±15°, richtige Richtung, stetig (kein
 *       Sprung zwischen Ticks und beim Interpolieren), über ein paar Ticks geglättet, klingt ab, robust gegen
 *       Yaw-Sprünge über ±180° und ungültige Werte, pro Spieler unabhängig, deterministisch.</li>
 *   <li>Vorderkante: in Fahrt bis ca. 10° nach unten; beim erlaubten Fliegen Steigen hoch, Sinken runter; Flug fremder
 *       Spieler aus der Bewegung, aber nicht bei normalem Sprung oder Fall. Sprung: Durchbiegen und Nachfedern.</li>
 *   <li>Pose ({@link FlyingCarpetMotion}): Hüfte auf dem Teppich (in der Pose am Boden), kein Körperteil im Teppich
 *       (auch beim Erscheinen und beim Auslaufen), Hände auf den Knien bzw. zur Kante, Arme nicht in Rumpf und Beinen,
 *       Schneidersitz gekreuzt, keine Laufbewegung, Kopf folgt dem Blick ohne Sprung, flüssig.</li>
 *   <li>Schwebehöhe (Render-Matrix): im Stand 1,2, in Fahrt weich bis 1,5, beim Schleichen 0,9 Blöcke, ohne
 *       Überschwingen und stetig (Ticks und Interpolation), beim erlaubten Fliegen gleich. Start: Ausrollen und
 *       Hinsetzen am Boden, dann weiches Aufsteigen (Ease-out) ohne Sprung. Ende: erst weich absinken, dann aufstehen
 *       und einrollen, stetig ab der zuletzt gezeigten Lage, keine Beine durch den Teppich.</li>
 *   <li>Nametag und Kamera-Drehpunkt um die Anhebung des Kopfes höher; in der Ego-Perspektive bleibt die Kamera im
 *       ausgeblendeten Kopf (kein Blick in den eigenen Körper); Sichtbarkeits-Box deckt den Reiter ab.</li>
 *   <li>Ausrollen von der Mitte in 0,4 s und Einrollen, stetig, nie nach oben in den Reiter; Teppich und Quasten nie
 *       im Boden; Welle läuft von vorne nach hinten, Schleichen beruhigt; Quasten wehen in Fahrt nach hinten.</li>
 *   <li>Modell und Textur: 24 × 16 × 1 px, Kette seg0..seg5, 4 + 4 Quasten; die Knochen-Mathematik von
 *       BedrockCosmeticModel (blockbenchAxisFix) mit den Werten des Renderers ergibt genau die Gelenke der Logik.</li>
 *   <li>Rein optisch: Der Emote-Code setzt nie Bewegung, Position, Hitbox oder Abilities und sendet keine Pakete
 *       (Quelltext-Prüfung wie beim Hexenbesen); Vorschau ohne Partikel und Geräusche.</li>
 * </ul>
 */
public class FlyingCarpetTest {

    private static int checks = 0;
    private static final double PX = 1.0 / 16.0;
    private static final double KPX = HammockLogic.BLOCKS_PER_PX;
    private static final String ROOT = "src/main/resources/assets/bameclient/";
    private static final String SRC = "src/main/java/com/bame/client/";
    private static final float TICK = FlyingCarpetLogic.TICK_S;

    public static void main(String[] args) {
        emoteType();
        bankDirectionAndLimit();
        bankSmoothAndContinuous();
        bankRobust();
        perPlayerAndDeterministic();
        noseAndFlight();
        jumpFlex();
        seatAndHover();
        riseAndAltitude();
        liftAndFirstPerson();
        noPenetration();
        hands();
        crossedLegsNoWalking();
        headLook();
        poseContinuity();
        unrollAndOutro();
        outroDescent();
        carpetAboveGround();
        waveAndTassels();
        model();
        boneMathMatchesLogic();
        effects();
        purelyVisual();
        System.out.println("FLYING CARPET TESTS PASSED (" + checks + " checks)");
    }

    // ---------------- Hilfen ----------------

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void near(double a, double b, double eps, String what) {
        check(Math.abs(a - b) <= eps, what + ": " + a + " != " + b);
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Ein Tick mit Fahrt {@code h} (Blöcke/Tick) und Drehung {@code yawRate} am Boden. */
    private static void ground(FlyingCarpetLogic.Track tr, float yawRate, double h, long ms) {
        FlyingCarpetLogic.step(tr, yawRate, h, 0.0, true, false, true, false, false, ms);
    }

    private static FlyingCarpetLogic.Track fresh() {
        FlyingCarpetLogic.Track tr = new FlyingCarpetLogic.Track();
        tr.reset(0.0);
        return tr;
    }

    /** Punkte auf allen Flächen eines Körperteils (lokal), etwa alle {@code step} px. */
    private static List<double[]> surface(HammockMotion.Part part, double step) {
        List<double[]> out = new ArrayList<>();
        int nx = Math.max(1, (int) Math.ceil(part.sx / step)), ny = Math.max(1, (int) Math.ceil(part.sy / step));
        int nz = Math.max(1, (int) Math.ceil(part.sz / step));
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= ny; j++) {
                for (int k = 0; k <= nz; k++) {
                    if (i != 0 && i != nx && j != 0 && j != ny && k != 0 && k != nz) continue;
                    out.add(new double[]{part.x0 + part.sx * i / nx, part.y0 + part.sy * j / ny, part.z0 + part.sz * k / nz});
                }
            }
        }
        return out;
    }

    private static final Map<HammockMotion.Part, List<double[]>> SURF = new java.util.EnumMap<>(HammockMotion.Part.class);

    private static List<double[]> surf(HammockMotion.Part part) {
        return SURF.computeIfAbsent(part, p -> surface(p, 1.0));
    }

    private static FlyingCarpetMotion.Pose pose(float t, float speed, float sneak, float hold, boolean slim) {
        FlyingCarpetMotion.Input in = new FlyingCarpetMotion.Input();
        in.t = t;
        in.speed = speed;
        in.sneak = sneak;
        in.hold = hold;
        in.slim = slim;
        FlyingCarpetMotion.Pose p = new FlyingCarpetMotion.Pose();
        FlyingCarpetMotion.sample(in, p);
        return p;
    }

    private static double[] modelPoint(FlyingCarpetMotion.Pose p, HammockMotion.Part part, double[] l) {
        float[] a = FlyingCarpetMotion.partAngles(p, part);
        double[] pv = FlyingCarpetMotion.partPivot(p, part, new double[3]);
        double[] o = new double[3];
        CampfireMotion.rotZYX(l[0], l[1], l[2], a[0], a[1], a[2], o);
        o[0] += pv[0];
        o[1] += pv[1];
        o[2] += pv[2];
        return o;
    }

    private static double[] localIn(FlyingCarpetMotion.Pose p, HammockMotion.Part part, double[] m) {
        float[] a = FlyingCarpetMotion.partAngles(p, part);
        double[] pv = FlyingCarpetMotion.partPivot(p, part, new double[3]);
        double[] o = new double[3];
        CampfireMotion.rotXYZInverse(m[0] - pv[0], m[1] - pv[1], m[2] - pv[2], a[0], a[1], a[2], o);
        return o;
    }

    private static double depthIn(HammockMotion.Part q, double[] l) {
        double dx = Math.min(l[0] - q.x0, q.x0 + q.sx - l[0]), dy = Math.min(l[1] - q.y0, q.y0 + q.sy - l[1]);
        double dz = Math.min(l[2] - q.z0, q.z0 + q.sz - l[2]);
        return Math.min(dx, Math.min(dy, dz));
    }

    private static boolean overCarpet(double[] f, double margin) {
        return Math.abs(f[0]) <= FlyingCarpetLogic.HALF_WIDTH + margin
                && Math.abs(f[2] - FlyingCarpetLogic.CENTER_Z) <= FlyingCarpetLogic.HALF_LENGTH + margin;
    }

    // ---------------- Emote-Typ ----------------

    private static void emoteType() {
        EmoteType e = EmoteType.FLYING_CARPET;
        check(EmoteType.fromId("FLYING_CARPET") == e, "fromId FLYING_CARPET (Shop-ID)");
        check(EmoteType.fromId("flying_carpet") == e, "fromId flying_carpet (Karten-ID)");
        check(EmoteType.fromId("flying-carpet") == e, "fromId flying-carpet");
        check(EmoteType.fromId("FLYINGCARPET") == e, "fromId FLYINGCARPET (Alias)");
        check("FLYING_CARPET".equals(e.name()), "Besitzprüfung über den Enum-Namen FLYING_CARPET");
        check("Fliegender Teppich".equals(e.getDisplayName()), "Name laut Auftrag");
        check("Auf einem fliegenden Teppich durch die Gegend gleiten".equals(e.getDescription()), "Beschreibung laut Auftrag");
        check("\uD83E\uDDDE".equals(e.getIcon()), "Symbol 🧞");
        check(e.allowsWalking(), "Laufen, Sprinten, Springen erlaubt (kein Abbruch bei Bewegung)");
        check(!e.isHidden(), "im Menü und Emote-Rad auswählbar");
        // Karte im Abschnitt EMOTES_SPECIAL mit denselben Texten (Name/Beschreibung aus EmoteType)
        String tab = read(SRC + "gui/tab/CosmeticsTab.java");
        String special = section(tab, "new CosmeticSection(EMOTES_SPECIAL", ")));");
        check(special.contains("emote(com.bame.client.emotes.EmoteType.FLYING_CARPET, \"flying_carpet\")"),
                "Karte flying_carpet unter EMOTES_SPECIAL");
    }

    // ---------------- Kurven-Neigung ----------------

    private static void bankDirectionAndLimit() {
        FlyingCarpetLogic.Track tr = fresh();
        long ms = 0;
        // Rechtskurve (Yaw wächst) in voller Fahrt: rechte Seite nach unten (Rz negativ), höchstens 15°
        for (int i = 0; i < 60; i++) ground(tr, 300.0F, 0.28, ms += 50);
        check(tr.bank < 0.0F, "Rechtskurve: rechte Seite nach unten (" + tr.bank + ")");
        near(tr.bank, -FlyingCarpetLogic.BANK_MAX, 0.3, "starke Kurve: ca. 15°");
        // Linkskurve
        for (int i = 0; i < 60; i++) ground(tr, -300.0F, 0.28, ms += 50);
        near(tr.bank, FlyingCarpetLogic.BANK_MAX, 0.3, "Linkskurve: linke Seite nach unten");
        // Geradeaus: klingt ab
        for (int i = 0; i < 30; i++) ground(tr, 0.0F, 0.28, ms += 50);
        check(Math.abs(tr.bank) < 0.2F, "geradeaus: Neigung klingt ab (" + tr.bank + ")");
        // Sanfte Kurve: proportional (60°/s -> 6° in voller Fahrt)
        for (int i = 0; i < 60; i++) ground(tr, 60.0F, 0.28, ms += 50);
        near(tr.bank, -6.0, 0.4, "sanfte Kurve proportional zur Drehgeschwindigkeit");
        // Im Stand nur ein Teil davon
        FlyingCarpetLogic.Track st = fresh();
        for (int i = 0; i < 60; i++) ground(st, 60.0F, 0.0, ms += 50);
        check(Math.abs(st.bank) < Math.abs(tr.bank) * 0.6F, "im Stand neigt er sich weniger (" + st.bank + ")");
        check(FlyingCarpetLogic.BANK_MAX >= 12.0F && FlyingCarpetLogic.BANK_MAX <= 16.0F, "Roll bis ca. 15°");
    }

    private static void bankSmoothAndContinuous() {
        FlyingCarpetLogic.Track tr = fresh();
        long ms = 0;
        // Sprung des Ziels von 0 auf -15°: nicht sofort, aber nach ca. 0,3 s fast da (über ein paar Frames geglättet)
        ground(tr, 1000.0F, 0.28, ms += 50);
        check(Math.abs(tr.bank) < 0.4F * FlyingCarpetLogic.BANK_MAX, "nach einem Tick erst ein Teil (" + tr.bank + ")");
        for (int i = 0; i < 5; i++) ground(tr, 1000.0F, 0.28, ms += 50);
        check(Math.abs(tr.bank) > 0.8F * FlyingCarpetLogic.BANK_MAX, "nach 0,3 s fast erreicht (" + tr.bank + ")");
        // Wechselnde, ruppige Eingaben: pro Tick höchstens 8°, beim Interpolieren (1 ms) höchstens 0,2°/ms
        Random rnd = new Random(7);
        FlyingCarpetLogic.Track a = fresh();
        double maxTick = 0, maxMs = 0;
        float lastRender = 0.0F;
        for (int i = 0; i < 4000; i++) {
            float rate = (float) ((rnd.nextDouble() - 0.5) * 4000.0);
            if (i % 37 < 10) rate = 0.0F;
            double h = rnd.nextDouble() < 0.5 ? 0.0 : rnd.nextDouble() * 0.6;
            float before = a.bank;
            FlyingCarpetLogic.step(a, rate, h, 0.0, true, rnd.nextBoolean(), true, false, false, ms += 50);
            maxTick = Math.max(maxTick, Math.abs(a.bank - before));
            check(Math.abs(a.bank) <= FlyingCarpetLogic.BANK_MAX + 1e-4, "Neigung begrenzt (" + a.bank + ")");
            for (int k = 0; k < 50; k++) {
                float v = a.bank((k + 1) / 50.0F);
                maxMs = Math.max(maxMs, Math.abs(v - lastRender));
                lastRender = v;
            }
        }
        check(maxTick <= 8.0, "keine Sprünge zwischen Ticks (max " + maxTick + "°)");
        check(maxMs <= 0.2, "stetig beim Interpolieren (max " + maxMs + "°/ms)");
    }

    private static void bankRobust() {
        // Körper-Yaw über ±180°: die Drehgeschwindigkeit wird wie im Koordinator gefaltet, kein Ausschlag
        FlyingCarpetLogic.Track tr = fresh();
        long ms = 0;
        float yaw = 170.0F, last = yaw;
        double max = 0;
        for (int i = 0; i < 200; i++) {
            yaw += 2.0F;
            if (yaw > 180.0F) yaw -= 360.0F;
            float rate = (float) HammockLogic.wrapDegrees(yaw - last) * 20.0F;
            last = yaw;
            ground(tr, rate, 0.28, ms += 50);
            max = Math.max(max, Math.abs(tr.bank));
        }
        check(max <= 4.5, "kein Ausschlag beim Übergang über ±180° (" + max + "°)");
        // Ungültige Werte: keine NaN, Werte bleiben begrenzt
        FlyingCarpetLogic.step(tr, Float.NaN, Double.NaN, Double.POSITIVE_INFINITY, true, false, false, false, false, ms += 50);
        FlyingCarpetLogic.step(tr, Float.POSITIVE_INFINITY, -5.0, Double.NaN, false, true, false, false, false, ms += 50);
        check(Float.isFinite(tr.bank) && Float.isFinite(tr.nose) && Float.isFinite(tr.speed) && Double.isFinite(tr.wave),
                "robust gegen ungültige Eingaben");
        // Ende: Neigung, Kippen und Fahrt klingen ab
        for (int i = 0; i < 30; i++) ground(tr, 900.0F, 0.3, ms += 50);
        for (int i = 0; i < 20; i++) FlyingCarpetLogic.step(tr, 900.0F, 0.3, 0.0, true, true, true, false, true, ms += 50);
        check(Math.abs(tr.bank) < 0.3F && Math.abs(tr.nose) < 0.3F && tr.speed < 0.01F && tr.sneak < 0.01F,
                "beim Auslaufen klingt alles ab");
        // Lange Laufzeit: Phasen bleiben klein (keine Genauigkeitsverluste)
        FlyingCarpetLogic.Track lt = fresh();
        for (int i = 0; i < 200_000; i++) ground(lt, 0.0F, 0.28, ms += 50);
        check(lt.wave < 2.0 * Math.PI * 101.0 && lt.tassel < 2.0 * Math.PI * 101.0, "Phasen bleiben beschränkt");
    }

    private static void perPlayerAndDeterministic() {
        // Zwei Spieler: verschränkt aktualisiert = einzeln aktualisiert (kein gemeinsamer Zustand)
        FlyingCarpetLogic.Track a = fresh(), b = fresh(), a2 = fresh(), b2 = fresh();
        long ms = 1000;
        for (int i = 0; i < 300; i++) {
            ms += 50;
            ground(a, (float) (200 * Math.sin(i * 0.1)), 0.2, ms);
            ground(b, -150.0F, 0.05, ms);
        }
        ms = 1000;
        for (int i = 0; i < 300; i++) ground(a2, (float) (200 * Math.sin(i * 0.1)), 0.2, ms += 50);
        ms = 1000;
        for (int i = 0; i < 300; i++) ground(b2, -150.0F, 0.05, ms += 50);
        check(a.bank == a2.bank && a.nose == a2.nose && a.wave == a2.wave, "Glättung pro Spieler unabhängig (A)");
        check(b.bank == b2.bank && b.nose == b2.nose && b.wave == b2.wave, "Glättung pro Spieler unabhängig (B)");
        check(a.bank != b.bank, "verschiedene Spieler, verschiedene Neigung");
    }

    // ---------------- Vorderkante, Flug, Sprung ----------------

    private static void noseAndFlight() {
        long ms = 0;
        FlyingCarpetLogic.Track tr = fresh();
        for (int i = 0; i < 60; i++) ground(tr, 0.0F, 0.28, ms += 50);
        check(tr.nose < -7.5F && tr.nose > -11.0F, "Sprint: Vorderkante ca. 10° nach unten (" + tr.nose + ")");
        for (int i = 0; i < 60; i++) ground(tr, 0.0F, 0.0, ms += 50);
        check(Math.abs(tr.nose) < 0.2F, "im Stand waagerecht (" + tr.nose + ")");
        // Erlaubtes Fliegen (lokal bekannt): Steigen hebt, Sinken senkt die Vorderkante
        FlyingCarpetLogic.Track f = fresh();
        for (int i = 0; i < 60; i++) FlyingCarpetLogic.step(f, 0.0F, 0.0, 0.375, false, false, true, true, false, ms += 50);
        check(f.flight && f.nose > 8.0F, "Steigen im Flug: vorne hoch (" + f.nose + ")");
        for (int i = 0; i < 60; i++) FlyingCarpetLogic.step(f, 0.0F, 0.0, -0.375, false, false, true, true, false, ms += 50);
        check(f.nose < -8.0F, "Sinken im Flug: vorne runter (" + f.nose + ")");
        check(f.nose >= FlyingCarpetLogic.NOSE_MIN && f.nose <= FlyingCarpetLogic.CLIMB_MAX, "Kippen begrenzt");
        // Normaler Sprung (kein Flug): Steigen ändert die Vorderkante nicht
        FlyingCarpetLogic.Track j = fresh();
        double vy = 0.42;
        FlyingCarpetLogic.step(j, 0.0F, 0.0, 0.0, true, false, true, false, false, ms += 50);
        float maxNose = 0.0F;
        for (int i = 0; i < 12; i++) {
            FlyingCarpetLogic.step(j, 0.0F, 0.0, vy, false, false, true, false, false, ms += 50);
            vy = (vy - 0.08) * 0.98;
            maxNose = Math.max(maxNose, Math.abs(j.nose));
        }
        check(!j.flight && maxNose < 0.01F, "Sprung ohne Flug: keine Steig-Neigung");
        // Fremde Spieler: Flug aus der Bewegung (lange in der Luft, kein Fall), nicht bei Sprung oder Fall
        FlyingCarpetLogic.Track r = fresh();
        boolean anyFlight = false;
        vy = 0.42;
        for (int i = 0; i < 12; i++) {
            FlyingCarpetLogic.step(r, 0.0F, 0.2, vy, false, false, false, false, false, ms += 50);
            anyFlight |= r.flight;
            vy = (vy - 0.08) * 0.98;
        }
        FlyingCarpetLogic.step(r, 0.0F, 0.2, 0.0, true, false, false, false, false, ms += 50);
        check(!anyFlight, "fremder Spieler: normaler Sprung ist kein Flug");
        FlyingCarpetLogic.Track fall = fresh();
        vy = 0.0;
        for (int i = 0; i < 60; i++) {
            FlyingCarpetLogic.step(fall, 0.0F, 0.0, vy, false, false, false, false, false, ms += 50);
            anyFlight |= fall.flight;
            vy = (vy - 0.08) * 0.98;
        }
        check(!anyFlight, "fremder Spieler: Fallen ist kein Flug");
        // Absprung von einer Kante (Sprung, dann tiefer fallen)
        FlyingCarpetLogic.Track ledge = fresh();
        vy = 0.42;
        for (int i = 0; i < 60; i++) {
            FlyingCarpetLogic.step(ledge, 0.0F, 0.2, vy, false, false, false, false, false, ms += 50);
            anyFlight |= ledge.flight;
            vy = (vy - 0.08) * 0.98;
        }
        check(!anyFlight, "fremder Spieler: Sprung über eine Kante ist kein Flug");
        FlyingCarpetLogic.Track hover = fresh();
        for (int i = 0; i < 40; i++) FlyingCarpetLogic.step(hover, 0.0F, 0.3, 0.0, false, false, false, false, false, ms += 50);
        check(hover.flight, "fremder Spieler: Schweben in der Luft wird als erlaubtes Fliegen erkannt");
        for (int i = 0; i < 40; i++) FlyingCarpetLogic.step(hover, 0.0F, 0.0, 0.375, false, false, false, false, false, ms += 50);
        check(hover.flight && hover.nose > 8.0F, "fremder Spieler steigt im Flug: vorne hoch");
        for (int i = 0; i < 40; i++) FlyingCarpetLogic.step(hover, 0.0F, 0.0, -0.375, false, false, false, false, false, ms += 50);
        check(hover.flight && hover.nose < -8.0F, "fremder Spieler sinkt im Flug: vorne runter");
        FlyingCarpetLogic.step(hover, 0.0F, 0.0, 0.0, true, false, false, false, false, ms += 50);
        check(!hover.flight, "Landung beendet den Flug");
    }

    private static void jumpFlex() {
        long ms = 10_000;
        FlyingCarpetLogic.Track tr = fresh();
        FlyingCarpetLogic.step(tr, 0.0F, 0.0, 0.0, true, false, true, false, false, ms);
        // Abheben
        ms += 50;
        FlyingCarpetLogic.step(tr, 0.0F, 0.0, 0.42, false, false, true, false, false, ms);
        check(tr.takeoffMs == ms, "Abheben erkannt");
        long takeoff = ms;
        double vy = 0.33;
        for (int i = 0; i < 10; i++) {
            ms += 50;
            FlyingCarpetLogic.step(tr, 0.0F, 0.0, vy, false, false, true, false, false, ms);
            vy = (vy - 0.08) * 0.98;
        }
        ms += 50;
        FlyingCarpetLogic.step(tr, 0.0F, 0.0, -0.3, true, false, true, false, false, ms);
        check(tr.landMs == ms && tr.landAmp >= 0.25F && tr.landAmp <= 1.0F, "Landung erkannt (Stärke " + tr.landAmp + ")");
        long land = ms;
        // Verlauf: beim Abheben kurz nach unten durchgebogen, nach der Landung Nachfedern, beginnt/endet bei 0
        near(tr.flex(takeoff), 0.0, 1e-6, "Durchbiegen beginnt bei 0");
        float peak = tr.flex(takeoff + (long) (FlyingCarpetLogic.SAG_S * 500));
        check(peak > 0.7F * FlyingCarpetLogic.SAG_DEG, "Abheben: Enden kurz nach unten (" + peak + ")");
        boolean down = false, up = false;
        double maxStep = 0;
        float prev = tr.flex(land);
        near(prev, 0.0, 0.05, "Nachfedern beginnt bei 0");
        for (long t = land + 1; t <= land + 1500; t++) {
            float f = tr.flex(t);
            down |= f > 1.0F;
            up |= f < -0.3F;
            maxStep = Math.max(maxStep, Math.abs(f - prev));
            check(f >= FlyingCarpetLogic.FLEX_MIN && f <= FlyingCarpetLogic.FLEX_MAX, "Durchbiegen begrenzt");
            prev = f;
        }
        check(down && up, "Landung: federt nach (erst nach unten, dann zurück)");
        check(maxStep <= 0.35, "Nachfedern stetig (max " + maxStep + "°/ms)");
        near(tr.flex(land + 1500), 0.0, 1e-6, "klingt aus");
        // Im Flug keine Sprung-Ereignisse
        FlyingCarpetLogic.Track fl = fresh();
        FlyingCarpetLogic.step(fl, 0.0F, 0.0, 0.0, true, false, true, true, false, ms += 50);
        FlyingCarpetLogic.step(fl, 0.0F, 0.0, 0.42, false, false, true, true, false, ms += 50);
        check(fl.takeoffMs == FlyingCarpetLogic.NO_EVENT, "beim Fliegen kein Durchbiegen");
        // Teppich-Form: positives Durchbiegen senkt beide Enden (vorne nur nach unten)
        FlyingCarpetLogic.Shape sh = new FlyingCarpetLogic.Shape();
        FlyingCarpetLogic.shape(1.0F, 1.0F, 0.0F, 0.0F, 0.0, 0.0, 10.0F, sh);
        FlyingCarpetLogic.Shape flat = new FlyingCarpetLogic.Shape();
        FlyingCarpetLogic.shape(1.0F, 1.0F, 0.0F, 0.0F, 0.0, 0.0, 0.0F, flat);
        check(sh.jy[0] < flat.jy[0] - 0.3 && sh.jy[6] < flat.jy[6] - 0.5, "Durchbiegen: beide Enden tiefer");
        FlyingCarpetLogic.shape(1.0F, 1.0F, 0.0F, 0.0F, 0.0, 0.0, -10.0F, sh);
        check(sh.jy[0] <= flat.jy[0] + 1e-9, "Rückfedern hebt die Vorderkante nie (Füße)");
    }

    // ---------------- Pose ----------------

    private static void seatAndHover() {
        // Pose: der Teppich liegt am Boden, die Hüfte darauf; die Schwebehöhe kommt als Verschiebung der Render-Matrix
        // für Reiter und Teppich gemeinsam dazu (nichts kann ineinander geraten)
        near(FlyingCarpetMotion.HIP_SIT_Y, FlyingCarpetLogic.GROUND_TOP_Y + FlyingCarpetMotion.SEAT_REL, 1e-12, "Hüfte auf dem Teppich");
        check(FlyingCarpetMotion.HIP_SIT_Y < FlyingCarpetMotion.HIP_LOCAL_Y - 0.1, "sitzt tiefer als stehend");
        double worstLeg = 1e9, bestLeg = -1e9;
        for (boolean slim : new boolean[]{false, true}) {
            for (float speed : new float[]{0.0F, 0.5F, 1.0F}) {
                for (float sneak : new float[]{0.0F, 1.0F}) {
                    for (float hold : new float[]{0.0F, 0.5F, 1.0F}) {
                        for (int ms = (int) (FlyingCarpetMotion.INTRO * 1000); ms <= 14_000; ms += 97) {
                            FlyingCarpetMotion.Pose p = pose(ms / 1000.0F, speed, sneak, hold, slim);
                            near(p.hipY, FlyingCarpetMotion.HIP_SIT_Y, 1e-9, "Hüfte auf Sitzhöhe");
                            near(p.carpetTop, FlyingCarpetLogic.GROUND_TOP_Y, 1e-12, "Teppich in der Pose am Boden");
                            // Oberschenkel liegen auf dem Teppich (Unterseite 0..0,5 px darüber)
                            for (HammockMotion.Part leg : new HammockMotion.Part[]{HammockMotion.Part.RIGHT_LEG, HammockMotion.Part.LEFT_LEG}) {
                                double low = 1e9;
                                for (double[] l : surf(leg)) low = Math.min(low, FlyingCarpetMotion.partToFrame(p, leg, l[0], l[1], l[2], new double[3])[1]);
                                double gap = (low - p.carpetTop) / KPX;
                                worstLeg = Math.min(worstLeg, gap);
                                bestLeg = Math.max(bestLeg, gap);
                            }
                        }
                    }
                }
            }
        }
        check(worstLeg >= -0.01, "Beine nicht im Teppich (" + worstLeg + " px)");
        check(bestLeg <= 0.6, "Hüfte sitzt auf dem Teppich (Abstand " + bestLeg + " px)");

        // Ziel der Schwebehöhe (Unterseite über dem Boden): im Stand 1,2, in voller Fahrt 1,5, beim Schleichen 0,9
        near(FlyingCarpetLogic.hoverTarget(0.0F, 0.0F), 1.2, 1e-6, "schwebt im Stand ca. 1,2 Blöcke hoch");
        near(FlyingCarpetLogic.hoverTarget(1.0F, 0.0F), 1.5, 1e-6, "in voller Fahrt ca. 1,5 Blöcke");
        near(FlyingCarpetLogic.hoverTarget(0.0F, 1.0F), 0.9, 1e-6, "beim Schleichen etwas tiefer (ca. 0,9)");
        float prevTarget = -1.0F;
        for (int i = 0; i <= 100; i++) {
            float h = FlyingCarpetLogic.hoverTarget(i / 100.0F, 0.0F);
            check(h >= prevTarget - 1e-6, "Höhe steigt mit dem Tempo");
            prevTarget = h;
        }
        // Glättung im Tick: steigt in Fahrt weich (nach ca. 0,8 s fast oben), ohne Überschwingen
        FlyingCarpetLogic.Track tr = fresh();
        long ms = 0;
        for (int i = 0; i < 40; i++) ground(tr, 0.0F, 0.0, ms += 50);
        near(tr.alt, 1.2, 1e-3, "im Stand 1,2");
        double maxTick = 0.0;
        float last = tr.alt, at08 = 0.0F;
        for (int i = 1; i <= 60; i++) {
            ground(tr, 0.0F, 0.28, ms += 50);
            maxTick = Math.max(maxTick, Math.abs(tr.alt - last));
            check(tr.alt >= last - 1e-6 && tr.alt <= 1.5F + 1e-6F, "steigt in Fahrt ohne Überschwingen (" + tr.alt + ")");
            last = tr.alt;
            if (i == 16) at08 = tr.alt;
        }
        check(at08 > 1.42F && at08 < 1.5F, "in Fahrt nach 0,8 s fast auf 1,5 (" + at08 + ")");
        near(tr.alt, 1.5, 0.005, "volle Fahrt: 1,5");
        for (int i = 0; i < 60; i++) {
            float before = tr.alt;
            FlyingCarpetLogic.step(tr, 0.0F, 0.0, 0.0, true, true, true, false, false, ms += 50);
            maxTick = Math.max(maxTick, Math.abs(tr.alt - before));
            check(tr.alt >= 0.9F - 1e-6F && tr.alt <= before + 1e-6F, "Schleichen: sinkt weich ohne Unterschwingen");
        }
        near(tr.alt, 0.9, 0.005, "Schleichen: 0,9");
        check(maxTick <= 0.07, "kein Sprung zwischen Ticks (" + maxTick + " Blöcke)");
        // Ruppige Eingaben: bleibt zwischen 0,9 und 1,5, stetig zwischen Ticks und beim Interpolieren
        Random rnd = new Random(11);
        FlyingCarpetLogic.Track r = fresh();
        double maxT = 0.0, maxMs = 0.0;
        float lastRender = r.alt;
        for (int i = 0; i < 4000; i++) {
            float before = r.alt;
            double h = rnd.nextDouble() < 0.4 ? 0.0 : rnd.nextDouble() * 0.6;
            FlyingCarpetLogic.step(r, (float) ((rnd.nextDouble() - 0.5) * 2000.0), h, 0.0, true, rnd.nextInt(4) == 0,
                    true, false, false, ms += 50);
            check(r.alt >= 0.9F - 1e-4F && r.alt <= 1.5F + 1e-4F, "Höhe bleibt zwischen 0,9 und 1,5 (" + r.alt + ")");
            maxT = Math.max(maxT, Math.abs(r.alt - before));
            for (int k = 0; k < 50; k++) {
                float v = r.alt((k + 1) / 50.0F);
                maxMs = Math.max(maxMs, Math.abs(v - lastRender));
                lastRender = v;
            }
        }
        check(maxT <= 0.07, "keine Höhensprünge zwischen Ticks (max " + maxT + ")");
        check(maxMs <= 0.0015, "stetig beim Interpolieren (max " + maxMs + " Blöcke/ms)");
        // Beim Auslaufen bleibt die Glättung stehen (das Absinken kommt aus dem Auslaufen)
        float frozen = r.alt;
        FlyingCarpetLogic.step(r, 0.0F, 0.0, 0.0, true, false, true, false, true, ms += 50);
        near(r.alt, frozen, 1e-6, "Auslaufen: Höhe der Glättung bleibt");
        // Erlaubtes Fliegen: dieselbe Höhe relativ zum Spieler
        FlyingCarpetLogic.Track g = fresh(), fl = fresh();
        for (int i = 0; i < 40; i++) {
            ms += 50;
            FlyingCarpetLogic.step(g, 0.0F, 0.1, 0.0, true, false, true, false, false, ms);
            FlyingCarpetLogic.step(fl, 0.0F, 0.1, 0.0, false, false, true, true, false, ms);
        }
        near(fl.alt, g.alt, 1e-6, "beim erlaubten Fliegen dieselbe Höhe relativ zum Spieler");
    }

    private static void riseAndAltitude() {
        // Start: Ausrollen und Hinsetzen am Boden, dann in 0,6..0,8 s weich (Ease-out) auf die Schwebehöhe
        check(FlyingCarpetMotion.RISE_START >= FlyingCarpetMotion.SIT_END - 1e-6, "steigt erst, wenn der Spieler sitzt");
        check(FlyingCarpetMotion.RISE_START >= FlyingCarpetMotion.UNROLL, "nach dem Ausrollen am Boden");
        double dur = FlyingCarpetMotion.RISE_END - FlyingCarpetMotion.RISE_START;
        check(dur >= 0.6 - 1e-6 && dur <= 0.8 + 1e-6, "Aufsteigen in ca. 0,6..0,8 s (" + dur + ")");
        near(FlyingCarpetMotion.rise(0.0F), 0.0, 0.0, "am Anfang am Boden");
        near(FlyingCarpetMotion.rise(FlyingCarpetMotion.RISE_START), 0.0, 1e-9, "Aufsteigen beginnt bei 0");
        near(FlyingCarpetMotion.rise(FlyingCarpetMotion.RISE_END), 1.0, 1e-9, "Aufsteigen endet bei 1");
        near(FlyingCarpetMotion.rise(9.0F), 1.0, 0.0, "danach oben");
        near(FlyingCarpetMotion.rise(Float.NaN), 0.0, 0.0, "robust gegen ungültige Zeit");
        float mid = FlyingCarpetMotion.RISE_START + 0.5F * (float) dur;
        check(FlyingCarpetMotion.rise(mid) > 0.6F, "Ease-out: zu Beginn schneller, am Ende langsam (" + FlyingCarpetMotion.rise(mid) + ")");
        double prev = 0.0, maxD = 0.0;
        for (int ms = 0; ms <= 2000; ms++) {
            double r = FlyingCarpetMotion.rise(ms / 1000.0F);
            check(r >= prev - 1e-7, "steigt nur");
            maxD = Math.max(maxD, r - prev);
            prev = r;
        }
        check(maxD < 0.003, "ohne Ruck (max " + maxD + " pro ms)");
        check(FlyingCarpetMotion.rise(FlyingCarpetMotion.RISE_START + 0.01F) < 0.003F, "weicher Beginn (Steigung 0)");
        check(1.0F - FlyingCarpetMotion.rise(FlyingCarpetMotion.RISE_END - 0.01F) < 0.001F, "weiches Ende (Steigung 0)");
        // Angewendete Höhe samt Schweben: am Boden 0 und ohne Neigung, dann stetig auf die Schwebehöhe
        FlyingCarpetMotion.Pose tp = new FlyingCarpetMotion.Pose();
        FlyingCarpetLogic.Tilt tl = new FlyingCarpetLogic.Tilt();
        double prevDy = 0.0, maxStep = 0.0;
        for (float alt : new float[]{0.9F, 1.2F, 1.5F}) {
            for (int ms = 0; ms <= 3000; ms++) {
                float t = ms / 1000.0F;
                FlyingCarpetMotion.timeline(t, tp);
                double a = FlyingCarpetMotion.altitude(t, alt, 0.0F, 0.0F, tp);
                FlyingCarpetMotion.tilt(t, 0.3F, 0.0F, 10.0F, -5.0F, a, tl);
                if (t <= FlyingCarpetMotion.RISE_START) {
                    check(tl.dy == 0.0 && tl.bank == 0.0F && tl.nose == 0.0F, "am Boden: weder Höhe noch Neigung");
                }
                if (ms > 0) maxStep = Math.max(maxStep, Math.abs(tl.dy - prevDy));
                prevDy = tl.dy;
            }
            near(tl.alt, alt, 1e-6, "nach dem Start auf der Schwebehöhe " + alt);
        }
        check(maxStep < 0.0045, "Aufsteigen ohne Sprung (max " + maxStep + " Blöcke/ms)");
        // Schweben wie bisher: ±0,04 Blöcke mit 0,5 Hz um die Schwebehöhe
        double lo = 9, hi = -9;
        for (int ms = 1300; ms < 5300; ms += 5) {
            float t = ms / 1000.0F;
            FlyingCarpetMotion.timeline(t, tp);
            FlyingCarpetMotion.tilt(t, 0.0F, 0.0F, 0.0F, 0.0F, FlyingCarpetMotion.altitude(t, 1.2F, 0.0F, 0.0F, tp), tl);
            lo = Math.min(lo, tl.dy - tl.alt);
            hi = Math.max(hi, tl.dy - tl.alt);
        }
        near(hi, 0.04, 0.002, "schwebt bis 0,04 nach oben");
        near(lo, -0.04, 0.002, "und 0,04 nach unten");
        FlyingCarpetMotion.tilt(1.3F, 0.0F, 0.0F, 0.0F, 0.0F, 1.2, tl);
        double a13 = tl.dy;
        FlyingCarpetMotion.tilt(3.3F, 0.0F, 0.0F, 0.0F, 0.0F, 1.2, tl);
        near(tl.dy, a13, 1e-6, "Periode 2 s (0,5 Hz)");
        // Am Boden beim Erscheinen noch keine Bewegung (auch bei voller Fahrt und Neigung)
        FlyingCarpetMotion.tilt(0.2F, 1.0F, 1.0F, 15.0F, -9.0F, 0.0, tl);
        check(tl.bank == 0.0F && tl.nose == 0.0F && tl.dy == 0.0, "beim Erscheinen am Boden noch ohne Neigung");
    }

    /** Kopf-Drehpunkt (Hals) im Rahmen. */
    private static double neckY(FlyingCarpetMotion.Pose p) {
        return FlyingCarpetMotion.partToFrame(p, HammockMotion.Part.HEAD, 0.0, 0.0, 0.0, new double[3])[1];
    }

    private static void liftAndFirstPerson() {
        FlyingCarpetLogic.Tilt tl = new FlyingCarpetLogic.Tilt();
        double standingNeck = neckY(pose(0.0F, 0.0F, 0.0F, 0.0F, false));
        double headSize = 8.0 * KPX;
        double maxLiftStep = 0.0, idleLift = 0.0, fastLift = 0.0, worstAbove = -9.0, worstOff = 0.0, maxTop = -9.0;
        for (float speed : new float[]{0.0F, 1.0F}) {
            for (float sneak : new float[]{0.0F, 1.0F}) {
                if (speed > 0.0F && sneak > 0.0F) continue; // Schleichen ist langsam
                float alt = FlyingCarpetLogic.hoverTarget(speed, sneak);
                double prevLift = 0.0;
                for (int ms = 0; ms <= 6000; ms++) {
                    float t = ms / 1000.0F;
                    FlyingCarpetMotion.Pose p = pose(t, speed, sneak, speed, false);
                    double a = FlyingCarpetMotion.altitude(t, alt, sneak, 0.0F, p);
                    FlyingCarpetMotion.tilt(t, speed, sneak, 0.0F, 0.0F, a, tl);
                    double lift = FlyingCarpetMotion.headLift(tl.dy, p.hipY, 1.0F);
                    // Nametag und Kamera-Drehpunkt gehen mit dem Kopf mit: nie weniger (Name nie im Kopf), höchstens
                    // um das Vorbeugen mehr
                    double moved = neckY(p) + tl.dy - standingNeck;
                    if (lift > 0.0) {
                        worstAbove = Math.max(worstAbove, moved - lift);
                        worstOff = Math.max(worstOff, lift - moved);
                    } else {
                        check(moved <= 1e-6, "kein Anheben, solange der Kopf nicht höher ist (" + moved + ")");
                    }
                    if (ms > 0) maxLiftStep = Math.max(maxLiftStep, Math.abs(lift - prevLift));
                    prevLift = lift;
                    if (ms == 5000 && speed == 0.0F && sneak == 0.0F) idleLift = lift;
                    if (ms == 5000 && speed > 0.0F) fastLift = lift;
                    // Höchster Punkt (Kopf oben) mit stärkster Neigung: in der Sichtbarkeits-Box
                    if (ms % 50 == 0) {
                        for (float bank : new float[]{-15.0F, 15.0F}) {
                            FlyingCarpetMotion.tilt(t, speed, sneak, bank, FlyingCarpetLogic.CLIMB_MAX, a, tl);
                            for (double[] l : new double[][]{{-4, -8, -4}, {4, -8, -4}, {-4, -8, 4}, {4, -8, 4}}) {
                                double[] f = FlyingCarpetMotion.partToFrame(p, HammockMotion.Part.HEAD, l[0], l[1], l[2], new double[3]);
                                maxTop = Math.max(maxTop, FlyingCarpetLogic.tiltPoint(tl, f[0], f[1], f[2], new double[3])[1]);
                            }
                        }
                    }
                }
            }
        }
        check(worstAbove <= 1e-6, "Nametag/Kamera mindestens so weit angehoben wie der Kopf (" + worstAbove + ")");
        check(worstOff <= 0.11, "Nametag/Kamera bleiben nah am Kopf (Vorbeugen, max " + worstOff + ")");
        check(maxLiftStep < 0.0045, "Anhebung stetig (max " + maxLiftStep + " Blöcke/ms)");
        near(idleLift, 1.2 - FlyingCarpetMotion.SEAT_DROP, 0.045, "im Stand: Name ca. 0,7 höher");
        near(fastLift, 1.5 - FlyingCarpetMotion.SEAT_DROP, 0.045, "in voller Fahrt: Name ca. 1,0 höher");
        check(maxTop <= 1.8 + FlyingCarpetLogic.ALT_FAST + 0.3, "Sichtbarkeits-Box reicht über den Kopf (" + maxTop + ")");
        // Beim Auslaufen geht die Anhebung mit dem Reiter weich zurück (siehe outroDescent), beim Mischen mit Vanilla 0
        near(FlyingCarpetMotion.headLift(0.0, FlyingCarpetMotion.HIP_SIT_Y, 0.0F), 0.0, 0.0, "ohne Teppich keine Anhebung");
        check(Double.isFinite(FlyingCarpetMotion.headLift(Double.NaN, 0.2, 1.0F)), "robust");

        // Ego-Perspektive: die Kamera bleibt auf Augenhöhe (1,62, geduckt 1,27); der ausgeblendete Kopf bleibt an der
        // Kamera, also nie Blick in den eigenen Körper; über die volle Höhe wird weich gemischt
        double worstBelow = 9.0, worstInside = 9.0;
        for (float speed : new float[]{0.0F, 1.0F}) {
            for (float sneak : new float[]{0.0F, 1.0F}) {
                float alt = FlyingCarpetLogic.hoverTarget(speed, sneak);
                double eye = 1.62 - FlyingCarpetMotion.SNEAK_EYE_DROP * sneak;
                for (int ms = 0; ms <= 6000; ms += 3) {
                    float t = ms / 1000.0F;
                    FlyingCarpetMotion.Pose p = pose(t, speed, sneak, speed, false);
                    double a = FlyingCarpetMotion.altitude(t, alt, sneak, 1.0F, p);
                    FlyingCarpetMotion.tilt(t, speed, sneak, 0.0F, 0.0F, a, tl);
                    double neck = neckY(p) + tl.dy;
                    // geduckt beim Start: der Reiter steht in den ersten 0,6 s noch aufrecht (Kamera schon tiefer, wie bisher)
                    if (sneak == 0.0F || t >= FlyingCarpetMotion.SIT_END) worstBelow = Math.min(worstBelow, eye - neck);
                    if (t >= FlyingCarpetMotion.INTRO) worstInside = Math.min(worstInside, neck + headSize - eye);
                    // Mischen: stetig und nie höher als ohne Begrenzung
                    double half = FlyingCarpetMotion.altitude(t, alt, sneak, 0.5F, p);
                    double full = FlyingCarpetMotion.altitude(t, alt, sneak, 0.0F, p);
                    check(half <= full + 1e-9 && half >= a - 1e-9, "Ego-Perspektive weich gemischt");
                }
            }
        }
        check(worstBelow >= -0.045, "Ego-Perspektive: Kamera nie im eigenen Körper (" + worstBelow + ")");
        check(worstInside >= 0.05, "Ego-Perspektive: beim Schweben bleibt die Kamera im ausgeblendeten Kopf (" + worstInside + ")");
    }

    /** Kein Körperteil im Teppich: über der Teppichfläche liegt jeder Punkt über dessen Oberseite (auch beim Erscheinen). */
    private static void noPenetration() {
        double worst = 1e9;
        String where = "";
        for (boolean slim : new boolean[]{false, true}) {
            for (float speed : new float[]{0.0F, 1.0F}) {
                for (float sneak : new float[]{0.0F, 1.0F}) {
                    for (float hold : new float[]{0.0F, 0.5F, 1.0F}) {
                        for (int ms = 0; ms <= 6000; ms += (ms < 1000 ? 5 : 113)) {
                            FlyingCarpetMotion.Pose p = pose(ms / 1000.0F, speed, sneak, hold, slim);
                            for (HammockMotion.Part part : HammockMotion.Part.values()) {
                                for (double[] l : surf(part)) {
                                    double[] f = FlyingCarpetMotion.partToFrame(p, part, l[0], l[1], l[2], new double[3]);
                                    if (!overCarpet(f, 0.0)) continue;
                                    double d = (f[1] - p.carpetTop) / KPX;
                                    if (d < worst) {
                                        worst = d;
                                        where = part + " bei " + ms + " ms (s=" + speed + ", h=" + hold + ")";
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        check(worst >= -0.05, "kein Körperteil im Teppich (tiefstens " + worst + " px, " + where + ")");
        // Arme nicht in Rumpf und Beinen (Schulter und aufliegende Hand ausgenommen bzw. begrenzt)
        double armBody = -1e9, armLeg = -1e9, handLeg = -1e9;
        for (boolean slim : new boolean[]{false, true}) {
            for (float speed : new float[]{0.0F, 0.5F, 1.0F}) {
                for (float sneak : new float[]{0.0F, 1.0F}) {
                    for (float hold : new float[]{0.0F, 0.25F, 0.5F, 0.75F, 1.0F}) {
                        FlyingCarpetMotion.Pose p = pose(5.0F, speed, sneak, hold, slim);
                        for (HammockMotion.Part a : new HammockMotion.Part[]{HammockMotion.Part.RIGHT_ARM, HammockMotion.Part.LEFT_ARM}) {
                            for (double[] l : surface(a, 0.5)) {
                                if (l[1] < 2.5) continue; // Schulter (wie Vanilla)
                                double[] m = modelPoint(p, a, l);
                                for (HammockMotion.Part b : HammockMotion.Part.values()) {
                                    if (b == a || b == HammockMotion.Part.RIGHT_ARM || b == HammockMotion.Part.LEFT_ARM) continue;
                                    double d = depthIn(b, localIn(p, b, m));
                                    if (b == HammockMotion.Part.BODY || b == HammockMotion.Part.HEAD) armBody = Math.max(armBody, d);
                                    else if (l[1] > 7.5) handLeg = Math.max(handLeg, d);
                                    else armLeg = Math.max(armLeg, d);
                                }
                            }
                        }
                    }
                }
            }
        }
        check(armBody <= 0.7, "Arme kaum im Rumpf (" + armBody + " px)");
        check(armLeg <= 0.7, "Arme höchstens streifend an Knie und Fuß (" + armLeg + " px)");
        check(handLeg <= 1.2, "Hand liegt auf dem Knie auf, höchstens leicht eingesunken (" + handLeg + " px)");
    }

    private static void hands() {
        for (boolean slim : new boolean[]{false, true}) {
            for (float sneak : new float[]{0.0F, 1.0F}) {
                // Im Stand: Hände locker auf den Knien (Handende auf der Oberseite der Beine)
                FlyingCarpetMotion.Pose p = pose(5.0F, 0.0F, sneak, 0.0F, slim);
                check(p.rKneeGap > -1.2F && p.rKneeGap < 0.3F, "rechte Hand auf dem Knie (" + p.rKneeGap + " px)");
                check(p.lKneeGap > -1.2F && p.lKneeGap < 0.3F, "linke Hand auf dem Knie (" + p.lKneeGap + " px)");
                // In Fahrt: rechte Hand zur Vorderkante, linke hält sich an der Seite
                FlyingCarpetMotion.Pose m = pose(5.0F, 1.0F, sneak, 1.0F, slim);
                check(m.rHoldGap < 2.9F, "rechte Hand greift zur Vorderkante (Rest " + m.rHoldGap + " px)");
                check(m.lHoldGap < 2.0F, "linke Hand an der Seitenkante (Rest " + m.lHoldGap + " px)");
                double[] s = FlyingCarpetMotion.shoulder(m, true, new double[3]);
                double[] tgt = FlyingCarpetMotion.frameToModel(FlyingCarpetMotion.HOLD_X, m.carpetTop + FlyingCarpetMotion.HAND_LIFT * KPX, FlyingCarpetMotion.HOLD_Z, new double[3]);
                double[] dir = new double[3];
                CampfireMotion.rotZYX(0.0, 1.0, 0.0, m.rArmPitch, m.rArmYaw, m.rArmRoll, dir);
                double[] v = {tgt[0] - s[0], tgt[1] - s[1], tgt[2] - s[2]};
                double len = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
                double ang = Math.toDegrees(Math.acos((dir[0] * v[0] + dir[1] * v[1] + dir[2] * v[2]) / len));
                check(ang < 1.0, "Arm zeigt genau auf die Vorderkante (" + ang + "°)");
                // Vorbeugen: im Stand leicht, in Fahrt mehr („leicht nach vorne geneigt“)
                double idleLean = Math.toDegrees(p.lean), moveLean = Math.toDegrees(m.lean);
                check(idleLean > 3.0 && idleLean < 14.0, "im Stand aufrecht (" + idleLean + "°)");
                check(moveLean > 18.0 && moveLean < 38.0, "in Fahrt leicht nach vorne geneigt (" + moveLean + "°)");
            }
        }
        // Atmen: Brustkorb und Vorbeugen schwingen leicht
        double lo = 9, hi = -9, lo2 = 9, hi2 = -9;
        for (int ms = 2000; ms < 8000; ms += 20) {
            FlyingCarpetMotion.Pose p = pose(ms / 1000.0F, 0.0F, 0.0F, 0.0F, false);
            lo = Math.min(lo, p.chestScale);
            hi = Math.max(hi, p.chestScale);
            lo2 = Math.min(lo2, p.lean);
            hi2 = Math.max(hi2, p.lean);
        }
        check(hi - lo > 0.02 && hi < 1.05, "leichtes Atmen");
        check(Math.toDegrees(hi2 - lo2) > 1.0 && Math.toDegrees(hi2 - lo2) < 3.0, "Oberkörper atmet leicht mit");
        // Schleichen: duckt sich etwas (mehr Vorbeugen, Kopf tiefer)
        FlyingCarpetMotion.Pose a = pose(5.0F, 0.0F, 0.0F, 0.0F, false), b = pose(5.0F, 0.0F, 1.0F, 0.0F, false);
        check(b.lean > a.lean && b.headPitch > a.headPitch, "Schleichen: duckt sich etwas");
    }

    private static void crossedLegsNoWalking() {
        FlyingCarpetMotion.Pose p = pose(5.0F, 0.0F, 0.0F, 0.0F, false);
        double[] rFoot = FlyingCarpetMotion.partToFrame(p, HammockMotion.Part.RIGHT_LEG, 0, 12, 0, new double[3]);
        double[] lFoot = FlyingCarpetMotion.partToFrame(p, HammockMotion.Part.LEFT_LEG, 0, 12, 0, new double[3]);
        check(rFoot[0] < -0.1 && lFoot[0] > 0.1, "Schneidersitz: Beine überkreuzt (rechter Fuß links, linker rechts)");
        check(rFoot[2] < -0.35 && lFoot[2] < -0.35, "Füße vorne");
        check(overCarpet(rFoot, -0.02) && overCarpet(lFoot, -0.02), "Füße auf dem Teppich");
        check(Math.toDegrees(p.rLegPitch) < -85 && Math.toDegrees(p.rLegPitch) > -100, "rechtes Bein ca. −90° angewinkelt");
        check(Math.toDegrees(p.lLegPitch) < -85 && Math.toDegrees(p.lLegPitch) > -100, "linkes Bein ca. −90° angewinkelt");
        near(Math.toDegrees(p.rLegYaw), -35.0, 3.0, "rechtes Bein nach innen");
        near(Math.toDegrees(p.lLegYaw), 35.0, 3.0, "linkes Bein nach innen");
        // Keine Laufbewegung: Beine hängen weder von der Geschwindigkeit noch von der Zeit ab
        for (int ms = 1000; ms < 20_000; ms += 61) {
            for (float speed : new float[]{0.0F, 0.37F, 1.0F}) {
                FlyingCarpetMotion.Pose q = pose(ms / 1000.0F, speed, 0.0F, speed, false);
                check(q.rLegPitch == p.rLegPitch && q.lLegPitch == p.lLegPitch && q.rLegYaw == p.rLegYaw
                        && q.lLegYaw == p.lLegYaw, "keine Laufbewegung der Beine");
            }
        }
        // Quelltext: die Pose nutzt weder Schwung noch Fortschritt der Beine (limbSwing*)
        String motion = stripComments(read(SRC + "emotes/FlyingCarpetMotion.java"));
        check(!motion.contains("limbSwing"), "Pose ohne Laufanimation (kein limbSwing)");
        String anim = stripComments(section(read(SRC + "emotes/EmoteAnimator.java"), "// Fliegender Teppich", "private static float clampF"));
        check(!anim.contains("limbSwingAnimationProgress"), "keine Laufbewegung im Animator");
    }

    private static void headLook() {
        FlyingCarpetMotion.Input in = new FlyingCarpetMotion.Input();
        FlyingCarpetMotion.Pose p = new FlyingCarpetMotion.Pose();
        in.t = 5.0F;
        in.look = true;
        in.lookYaw = 45.0F;
        in.lookPitch = 10.0F;
        FlyingCarpetMotion.sample(in, p);
        near(Math.toDegrees(p.headYaw), 45.0, 6.0, "Kopf folgt deutlichem Umschauen");
        // Blick einmal ganz herum: kein Sprung (auch über ±180°)
        double prev = Double.NaN, max = 0;
        for (int d = -540; d <= 540; d++) {
            in.lookYaw = d;
            FlyingCarpetMotion.sample(in, p);
            double y = Math.toDegrees(p.headYaw);
            if (!Double.isNaN(prev)) max = Math.max(max, Math.abs(y - prev));
            prev = y;
            check(Math.abs(y) <= 75.0, "Kopf dreht nicht über die Schulter");
        }
        check(max <= 3.0, "Kopf folgt ohne Sprung (max " + max + "° pro Grad)");
        // Im Stand ohne Blick: schaut sich ab und zu langsam um
        in.look = false;
        double lo = 1e9, hi = -1e9, maxRate = 0;
        prev = Double.NaN;
        for (int ms = 1000; ms <= 60_000; ms += 10) {
            in.t = ms / 1000.0F;
            FlyingCarpetMotion.sample(in, p);
            double y = Math.toDegrees(p.headYaw);
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
            if (!Double.isNaN(prev)) maxRate = Math.max(maxRate, Math.abs(y - prev) / 0.01);
            prev = y;
        }
        check(hi - lo > 30.0 && hi < 45.0 && lo > -45.0, "schaut sich um (" + lo + ".." + hi + ")");
        check(maxRate < 60.0, "langsam (max " + maxRate + "°/s)");
        // Kurven-Neigung: Kopf gleicht teilweise aus
        in.bank = -15.0F;
        in.t = 5.0F;
        FlyingCarpetMotion.sample(in, p);
        check(p.headRoll > 0.0F && Math.toDegrees(p.headRoll) < 10.0, "Kopf gleicht die Kurve leicht aus");
    }

    private static void poseContinuity() {
        FlyingCarpetMotion.Input in = new FlyingCarpetMotion.Input();
        FlyingCarpetMotion.Pose prev = new FlyingCarpetMotion.Pose(), cur = new FlyingCarpetMotion.Pose();
        double worst = 0;
        String where = "";
        for (int variant = 0; variant < 3; variant++) {
            boolean first = true;
            for (int ms = 0; ms <= 16_000; ms++) {
                float t = ms / 1000.0F;
                in.t = t;
                // weich wechselnde Eingaben (wie interpoliert): Fahrt, Halten, Schleichen, Neigung, Blick
                in.speed = (float) (0.5 + 0.5 * Math.sin(t * 0.9 + variant));
                in.hold = FlyingCarpetLogic.easeInOut((in.speed - 0.1F) / 0.3F);
                in.sneak = variant == 1 ? (float) (0.5 + 0.5 * Math.sin(t * 0.7)) : 0.0F;
                in.bank = (float) (15.0 * Math.sin(t * 1.3));
                in.look = variant == 2;
                in.lookYaw = (float) (80.0 * Math.sin(t * 0.5));
                in.lookPitch = (float) (30.0 * Math.sin(t * 0.8));
                FlyingCarpetMotion.sample(in, cur);
                if (!first) {
                    for (HammockMotion.Part part : HammockMotion.Part.values()) {
                        for (double[] l : new double[][]{{part.x0, part.y0, part.z0}, {part.x0 + part.sx, part.y0 + part.sy, part.z0 + part.sz},
                                {part.x0, part.y0 + part.sy, part.z0 + part.sz}}) {
                            double d = dist(FlyingCarpetMotion.partToFrame(prev, part, l[0], l[1], l[2], new double[3]),
                                    FlyingCarpetMotion.partToFrame(cur, part, l[0], l[1], l[2], new double[3]));
                            if (d > worst) {
                                worst = d;
                                where = part + " bei " + ms + " ms (Variante " + variant + ")";
                            }
                        }
                    }
                }
                first = false;
                FlyingCarpetMotion.Pose sw = prev;
                prev = cur;
                cur = sw;
            }
        }
        check(worst < 0.012, "Körper bewegt sich flüssig (max " + worst + " Blöcke/ms, " + where + ")");
        // deterministisch
        FlyingCarpetMotion.Pose a = pose(7.3F, 0.4F, 0.2F, 0.6F, true), b = pose(7.3F, 0.4F, 0.2F, 0.6F, true);
        check(a.rArmPitch == b.rArmPitch && a.headYaw == b.headYaw && a.drop == b.drop, "gleiche Eingaben, gleiche Pose");
    }

    // ---------------- Ausrollen, Auslaufen ----------------

    private static void unrollAndOutro() {
        FlyingCarpetLogic.Shape sh = new FlyingCarpetLogic.Shape();
        FlyingCarpetMotion.Pose p = new FlyingCarpetMotion.Pose();
        // Start: eingerollt, klein; nach 0,4 s flach
        FlyingCarpetMotion.timeline(0.0F, p);
        FlyingCarpetLogic.shape(p.unroll, p.rise, 0.0F, 0.0F, 0.0, 0.0, 0.0F, sh);
        check(Math.abs(sh.seg[0]) > 200.0F && Math.abs(sh.seg[5]) > 200.0F, "beim Start eingerollt");
        near(sh.scale, FlyingCarpetLogic.SCALE_ROLLED, 1e-6, "eingerollt kleiner");
        near(FlyingCarpetMotion.UNROLL, 0.4, 0.05, "rollt sich in ca. 0,4 s aus");
        FlyingCarpetMotion.timeline(FlyingCarpetMotion.UNROLL, p);
        FlyingCarpetLogic.shape(p.unroll, 0.0F, 0.0F, 0.0F, 0.0, 0.0, 0.0F, sh);
        for (int k = 0; k < FlyingCarpetLogic.SEGMENTS; k++) near(sh.seg[k], 0.0, 1e-4, "nach 0,4 s flach (seg" + k + ")");
        near(sh.scale, 1.0, 1e-6, "volle Größe");
        // Von der Mitte aus: das innere Gelenk streckt sich zuerst
        for (float u = 0.05F; u < 1.0F; u += 0.05F) {
            check(FlyingCarpetLogic.curl(u, 0) <= FlyingCarpetLogic.curl(u, 1) + 1e-6
                    && FlyingCarpetLogic.curl(u, 1) <= FlyingCarpetLogic.curl(u, 2) + 1e-6, "von der Mitte aus ausgerollt (" + u + ")");
        }
        // Stetig (1 ms) und nie nach oben in den Reiter (eingerollte Enden liegen unter dem Teppich)
        double maxStep = 0, maxUp = -1e9;
        double[] py = new double[7], pz = new double[7];
        boolean first = true;
        for (int ms = 0; ms <= 1000; ms++) {
            FlyingCarpetMotion.timeline(ms / 1000.0F, p);
            FlyingCarpetLogic.shape(p.unroll, p.rise, 0.3F, 0.0F, ms * 0.004, ms * 0.003, 0.0F, sh);
            for (int k = 0; k <= 6; k++) {
                double y = sh.jy[k] * sh.scale, z = sh.jz[k] * sh.scale;
                if (!first) maxStep = Math.max(maxStep, Math.hypot(y - py[k], z - pz[k]));
                py[k] = y;
                pz[k] = z;
            }
            // Einrollung allein (ohne Welle): Enden bleiben unter der Teppichebene
            FlyingCarpetLogic.shape(p.unroll, 0.0F, 0.0F, 0.0F, 0.0, 0.0, 0.0F, sh);
            for (int k = 0; k <= 6; k++) maxUp = Math.max(maxUp, sh.jy[k]);
            first = false;
        }
        check(maxStep < 0.25, "Ausrollen flüssig (max " + maxStep + " px/ms)");
        check(maxUp < 0.3, "eingerollte Enden nie über dem Teppich (" + maxUp + " px)");
        // Ablauf: liegt erst am Boden (ausrollen, hinsetzen), steigt dann mit dem sitzenden Spieler auf. Eingerollt steckt
        // die Rolle bis zu ihrer Oberseite im Boden und taucht beim Ausrollen auf (keine Füße in der Rolle)
        FlyingCarpetMotion.timeline(0.0F, p);
        near(p.carpetMid + 0.5 * FlyingCarpetLogic.THICK_PX * PX * FlyingCarpetLogic.scaleAt(0.0F), 0.0, 1e-9,
                "eingerollt: Oberseite der Rolle auf Bodenhöhe");
        near(p.carpetTop, FlyingCarpetMotion.FEET_Y, 1e-9, "der Spieler steht dann noch auf dem Boden");
        double prevMid = p.carpetMid;
        for (int ms = 0; ms <= (int) (FlyingCarpetMotion.UNROLL * 1000); ms++) {
            FlyingCarpetMotion.timeline(ms / 1000.0F, p);
            check(p.carpetMid >= prevMid - 1e-12 && p.carpetMid - prevMid < 0.002, "taucht stetig auf");
            prevMid = p.carpetMid;
        }
        near(p.carpetMid, FlyingCarpetLogic.GROUND_MID_Y, 1e-9, "ausgerollt flach am Boden");
        FlyingCarpetMotion.timeline(0.2F, p);
        check(p.rise == 0.0F, "beim Ausrollen noch am Boden");
        FlyingCarpetMotion.timeline(FlyingCarpetMotion.INTRO, p);
        near(p.rise, 1.0, 1e-9, "nach dem Start auf Schwebehöhe");
        FlyingCarpetMotion.timeline(0.0F, p);
        near(p.hipY, FlyingCarpetMotion.HIP_LOCAL_Y, 1e-9, "beim Start steht der Spieler noch");
        // Auslaufen: erst weich absinken (noch flach), dann zur Mitte einrollen, kleiner werden, verschwinden
        FlyingCarpetMotion.Outro o = new FlyingCarpetMotion.Outro();
        FlyingCarpetMotion.outro(0.0F, 5.0F, o);
        check(o.visible && o.unroll == 1.0F && o.shrink == 1.0F && o.rider == 1.0F && o.descent == 1.0F,
                "Auslaufen beginnt flach, in der Luft, Reiter sitzt");
        FlyingCarpetMotion.outro(FlyingCarpetMotion.OUTRO_DESCENT, 5.0F, o);
        check(o.descent == 0.0F && o.unroll == 1.0F && o.rider == 1.0F, "erst gelandet, noch flach, Reiter sitzt noch");
        FlyingCarpetMotion.outro(FlyingCarpetMotion.OUTRO_ROLL_END, 5.0F, o);
        check(o.unroll < 1e-6F, "zusammengerollt");
        FlyingCarpetMotion.outro(FlyingCarpetMotion.OUTRO_STAND_END, 5.0F, o);
        check(o.rider == 0.0F, "Reiter steht wieder (Vanilla-Pose)");
        FlyingCarpetMotion.outro(FlyingCarpetMotion.OUTRO - 0.001F, 5.0F, o);
        check(o.shrink < 0.65F, "wird kleiner");
        FlyingCarpetMotion.outro(FlyingCarpetMotion.OUTRO, 5.0F, o);
        check(!o.visible, "verschwindet");
        check(FlyingCarpetMotion.OUTRO_SPARKLE <= FlyingCarpetMotion.OUTRO && FlyingCarpetMotion.OUTRO <= 1.5F,
                "Funkeln beim Verschwinden");
        double[] prevO = null;
        double oMax = 0;
        for (int ms = 0; ms < (int) (FlyingCarpetMotion.OUTRO * 1000); ms++) {
            // Ende mitten im Ausrollen (0,3 s): beginnt halb ausgerollt
            FlyingCarpetMotion.outro(ms / 1000.0F, 0.3F, o);
            double[] cur = {o.descent, o.rider, o.seatT, o.unroll, o.shrink};
            if (prevO != null) for (int i = 0; i < cur.length; i++) oMax = Math.max(oMax, Math.abs(cur[i] - prevO[i]));
            prevO = cur;
        }
        check(oMax < 0.01, "Auslaufen stetig (auch wenn es beim Ausrollen endet; max " + oMax + " pro ms)");
    }

    /** Pose mit Anteil {@code w} der Teppich-Pose, Rest neutral im Stand (so mischt der Animator mit Vanilla). */
    private static FlyingCarpetMotion.Pose blendNeutral(FlyingCarpetMotion.Pose p, float w) {
        FlyingCarpetMotion.Pose q = new FlyingCarpetMotion.Pose();
        q.slim = p.slim;
        q.carpetTop = p.carpetTop;
        q.hipY = p.hipY;
        q.drop = p.drop * w;
        q.neckDY = p.neckDY * w;
        q.neckDZ = p.neckDZ * w;
        q.shoulderDY = p.shoulderDY * w;
        q.shoulderDZ = p.shoulderDZ * w;
        q.lean = p.lean * w;
        q.headPitch = p.headPitch * w;
        q.headYaw = p.headYaw * w;
        q.headRoll = p.headRoll * w;
        q.rArmPitch = p.rArmPitch * w;
        q.rArmYaw = p.rArmYaw * w;
        q.rArmRoll = p.rArmRoll * w;
        q.lArmPitch = p.lArmPitch * w;
        q.lArmYaw = p.lArmYaw * w;
        q.lArmRoll = p.lArmRoll * w;
        q.rLegPitch = p.rLegPitch * w;
        q.rLegYaw = p.rLegYaw * w;
        q.lLegPitch = p.lLegPitch * w;
        q.lLegYaw = p.lLegYaw * w;
        return q;
    }

    /** Höchste Oberseite des Teppichs über der Stelle {@code z} (Rahmen, Blöcke, ohne Neigung) oder −∞. */
    private static double carpetTopAt(FlyingCarpetLogic.Shape sh, double mid, double scale, double z) {
        double best = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < FlyingCarpetLogic.SEGMENTS; k++) {
            double a = Math.toRadians(sh.seg[k]);
            double ny = Math.cos(a), nz = Math.sin(a);
            double y1 = mid + (sh.jy[k] + 0.5 * ny) * PX * scale, z1 = FlyingCarpetLogic.CENTER_Z + (sh.jz[k] + 0.5 * nz) * PX * scale;
            double y2 = mid + (sh.jy[k + 1] + 0.5 * ny) * PX * scale, z2 = FlyingCarpetLogic.CENTER_Z + (sh.jz[k + 1] + 0.5 * nz) * PX * scale;
            if (Math.abs(z2 - z1) < 1e-9 || z < Math.min(z1, z2) || z > Math.max(z1, z2)) continue;
            best = Math.max(best, y1 + (y2 - y1) * (z - z1) / (z2 - z1));
        }
        return best;
    }

    private static void outroDescent() {
        // Reihenfolge: erst weich absinken, dann aufstehen und zusammenrollen
        check(FlyingCarpetMotion.OUTRO_STAND_START >= FlyingCarpetMotion.OUTRO_DESCENT - 1e-6, "steht erst auf, wenn der Teppich gelandet ist");
        check(FlyingCarpetMotion.OUTRO_ROLL_START >= FlyingCarpetMotion.OUTRO_DESCENT - 1e-6, "rollt sich erst am Boden zusammen");
        FlyingCarpetMotion.Outro o = new FlyingCarpetMotion.Outro();
        FlyingCarpetLogic.Tilt at = new FlyingCarpetLogic.Tilt(), ot = new FlyingCarpetLogic.Tilt();
        FlyingCarpetLogic.Shape sh = new FlyingCarpetLogic.Shape(), sh2 = new FlyingCarpetLogic.Shape();
        FlyingCarpetMotion.Input in = new FlyingCarpetMotion.Input();
        double worstPen = 1e9, maxDy = 0.0, maxBody = 0.0, maxJoint = 0.0;
        String where = "";
        for (float tEnd : new float[]{0.05F, 0.3F, 0.7F, 1.0F, 2.0F, 6.3F}) {
            for (float speed : new float[]{0.0F, 1.0F}) {
                float w0 = FlyingCarpetMotion.rise(tEnd);
                FlyingCarpetMotion.Pose tp = new FlyingCarpetMotion.Pose();
                FlyingCarpetMotion.timeline(tEnd, tp);
                float alt = FlyingCarpetLogic.hoverTarget(speed, 0.0F);
                FlyingCarpetMotion.tilt(tEnd, speed, 0.0F, 9.0F, -4.0F, FlyingCarpetMotion.altitude(tEnd, alt, 0.0F, 0.0F, tp), at);
                double dy0 = at.dy;
                in.t = tEnd;
                in.speed = speed;
                in.hold = speed;
                in.sneak = 0.0F;
                in.bank = 9.0F;
                in.slim = speed > 0.0F;
                in.look = false;
                // Übergang: erstes Bild des Auslaufens = letztes Bild des Emotes (Lage, Neigung, Pose, Teppich)
                FlyingCarpetMotion.outro(0.0F, tEnd, o);
                FlyingCarpetMotion.outroTilt(o, dy0, w0, 9.0F, -4.0F, ot);
                near(ot.dy, at.dy, 1e-9, "Auslaufen beginnt auf der gezeigten Höhe");
                near(ot.bank, at.bank, 1e-5, "Auslaufen beginnt mit der gezeigten Neigung");
                near(ot.nose, at.nose, 1e-5, "Auslaufen beginnt mit dem gezeigten Kippen");
                FlyingCarpetMotion.Pose a = new FlyingCarpetMotion.Pose(), b = new FlyingCarpetMotion.Pose();
                FlyingCarpetMotion.sample(in, a);
                FlyingCarpetMotion.sampleOutro(in, o, tEnd, 0.0F, w0, b);
                double d0 = 0.0;
                for (HammockMotion.Part part : HammockMotion.Part.values()) {
                    for (double[] l : new double[][]{{part.x0, part.y0, part.z0}, {part.x0 + part.sx, part.y0 + part.sy, part.z0 + part.sz}}) {
                        d0 = Math.max(d0, dist(FlyingCarpetMotion.partToFrame(a, part, l[0], l[1], l[2], new double[3]),
                                FlyingCarpetMotion.partToFrame(b, part, l[0], l[1], l[2], new double[3])));
                    }
                }
                check(d0 < 1e-6, "Reiter springt beim Ende nicht (" + d0 + ", Ende bei " + tEnd + " s)");
                FlyingCarpetLogic.shape(a.unroll, a.rise, speed, 0.0F, 1.0, 2.0, 0.0F, sh);
                FlyingCarpetLogic.shape(o.unroll, w0 * o.descent, speed, 0.0F, 1.0, 2.0, 0.0F, sh2);
                for (int k = 0; k <= FlyingCarpetLogic.SEGMENTS; k++) {
                    near(sh2.jy[k] * sh2.scale, sh.jy[k] * sh.scale, 1e-6, "Teppich springt beim Ende nicht (jy" + k + ")");
                    near(sh2.jz[k] * sh2.scale, sh.jz[k] * sh.scale, 1e-6, "Teppich springt beim Ende nicht (jz" + k + ")");
                }
                // Verlauf in 1-ms-Schritten
                float unroll0 = o.unroll;
                double prevDy = dy0;
                List<double[]> prevPts = null;
                double[] prevJ = null;
                for (int ms = 0; ms <= (int) (FlyingCarpetMotion.OUTRO * 1000) + 20; ms++) {
                    float age = ms / 1000.0F;
                    FlyingCarpetMotion.outro(age, tEnd, o);
                    float bank = 9.0F * (float) Math.exp(-age * 8.0), nose = -4.0F * (float) Math.exp(-age * 8.0);
                    FlyingCarpetMotion.outroTilt(o, dy0, w0, bank, nose, ot);
                    check(ot.dy >= 0.0 && ot.dy <= dy0 + 1e-12, "sinkt nur nach unten bis zum Boden");
                    if (age >= FlyingCarpetMotion.OUTRO_DESCENT) {
                        check(ot.dy == 0.0 && ot.bank == 0.0F && ot.nose == 0.0F, "nach dem Absinken am Boden und gerade");
                    }
                    if (o.rider < 1.0F) check(ot.dy < 1e-9, "aufstehen erst nach dem Absinken");
                    if (o.unroll < unroll0 - 1e-6F) check(ot.dy < 1e-9, "zusammenrollen erst am Boden");
                    maxDy = Math.max(maxDy, Math.abs(ot.dy - prevDy));
                    prevDy = ot.dy;
                    if (age >= FlyingCarpetMotion.OUTRO) {
                        check(!o.visible && o.rider == 0.0F, "am Ende Vanilla-Pose, Teppich weg");
                        continue;
                    }
                    // Reiter (mit Vanilla gemischt) und Teppich stetig
                    in.speed = speed * (float) Math.exp(-age * 7.0);
                    in.hold = in.speed;
                    FlyingCarpetMotion.Pose q = new FlyingCarpetMotion.Pose();
                    FlyingCarpetMotion.sampleOutro(in, o, tEnd, age, w0, q);
                    FlyingCarpetMotion.Pose m = blendNeutral(q, o.rider);
                    List<double[]> pts = new ArrayList<>();
                    for (HammockMotion.Part part : HammockMotion.Part.values()) {
                        for (double[] l : new double[][]{{part.x0, part.y0, part.z0}, {part.x0 + part.sx, part.y0 + part.sy, part.z0 + part.sz},
                                {part.x0, part.y0 + part.sy, part.z0 + part.sz}}) {
                            pts.add(FlyingCarpetMotion.partToFrame(m, part, l[0], l[1], l[2], new double[3]));
                        }
                    }
                    if (prevPts != null) for (int i = 0; i < pts.size(); i++) maxBody = Math.max(maxBody, dist(pts.get(i), prevPts.get(i)));
                    prevPts = pts;
                    float dyn = w0 * o.descent;
                    FlyingCarpetLogic.shape(o.unroll, dyn, in.speed, 0.0F, 1.0 + age * 3.0, 2.0 + age * 5.0, 0.0F, sh);
                    double scale = sh.scale * o.shrink;
                    double[] j = new double[2 * (FlyingCarpetLogic.SEGMENTS + 1)];
                    for (int k = 0; k <= FlyingCarpetLogic.SEGMENTS; k++) {
                        j[2 * k] = sh.jy[k] * scale;
                        j[2 * k + 1] = sh.jz[k] * scale;
                    }
                    if (prevJ != null) for (int i = 0; i < j.length; i++) maxJoint = Math.max(maxJoint, Math.abs(j[i] - prevJ[i]));
                    prevJ = j;
                    // Keine Beine (und kein anderes Körperteil) durch den Teppich: alle 5 ms die ganze Oberfläche
                    if (ms % 5 != 0) continue;
                    for (HammockMotion.Part part : HammockMotion.Part.values()) {
                        for (double[] l : surf(part)) {
                            double[] f = FlyingCarpetMotion.partToFrame(m, part, l[0], l[1], l[2], new double[3]);
                            if (Math.abs(f[0]) > FlyingCarpetLogic.HALF_WIDTH * scale) continue;
                            double top = carpetTopAt(sh, FlyingCarpetLogic.GROUND_MID_Y - o.sink, scale, f[2]);
                            if (top == Double.NEGATIVE_INFINITY) continue;
                            double d = (f[1] - top) / KPX;
                            if (d < worstPen) {
                                worstPen = d;
                                where = part + " bei " + ms + " ms (Ende " + tEnd + " s, Fahrt " + speed + ")";
                            }
                        }
                    }
                }
            }
        }
        check(maxDy < 0.0045, "weiches Absinken (max " + maxDy + " Blöcke/ms)");
        check(maxBody < 0.012, "Reiter bewegt sich beim Auslaufen flüssig (max " + maxBody + " Blöcke/ms)");
        check(maxJoint < 0.25, "Teppich rollt sich flüssig ein (max " + maxJoint + " px/ms)");
        check(worstPen >= -0.05, "keine Beine durch den Teppich beim Auslaufen (tiefstens " + worstPen + " px, " + where + ")");
    }

    /** Ecken der Teppich-Segmente im Rahmen (mit Neigung): {x, y, z}. */
    private static List<double[]> carpetCorners(FlyingCarpetLogic.Shape sh, double mid, FlyingCarpetLogic.Tilt tl) {
        List<double[]> out = new ArrayList<>();
        for (int k = 0; k < FlyingCarpetLogic.SEGMENTS; k++) {
            double a = Math.toRadians(sh.seg[k]);
            double ny = Math.cos(a), nz = Math.sin(a); // Normale (oben) des Segments in (y, z)
            for (int e = k; e <= k + 1; e++) {
                for (double side : new double[]{-0.5, 0.5}) {
                    double y = sh.jy[e] + side * ny, z = sh.jz[e] + side * nz;
                    for (double x : new double[]{-FlyingCarpetLogic.HALF_WIDTH, FlyingCarpetLogic.HALF_WIDTH}) {
                        double[] f = {x, mid + y * PX * sh.scale, FlyingCarpetLogic.CENTER_Z + z * PX * sh.scale};
                        out.add(FlyingCarpetLogic.tiltPoint(tl, f[0], f[1], f[2], new double[3]));
                    }
                }
            }
        }
        return out;
    }

    private static void carpetAboveGround() {
        FlyingCarpetLogic.Shape sh = new FlyingCarpetLogic.Shape();
        FlyingCarpetLogic.Tilt tl = new FlyingCarpetLogic.Tilt();
        FlyingCarpetMotion.Pose tp = new FlyingCarpetMotion.Pose();
        double low = 1e9, tasselLow = 1e9, hoverLow = 1e9;
        String where = "", tWhere = "";
        // Ausgerollt (ab 0,4 s): am Boden, beim Aufsteigen und beim Schweben, mit stärkster Neigung, Welle, Durchbiegen
        for (float speed : new float[]{0.0F, 0.5F, 1.0F}) {
            for (float sneak : new float[]{0.0F, 1.0F}) {
                if (sneak > 0.0F && speed > 0.3F) continue; // Schleichen ist langsam
                float alt = FlyingCarpetLogic.hoverTarget(speed, sneak);
                for (float bank : new float[]{-15.0F, 0.0F, 15.0F}) {
                    for (float nose : new float[]{FlyingCarpetLogic.noseTarget(speed, false, 0.0), FlyingCarpetLogic.NOSE_MIN, FlyingCarpetLogic.CLIMB_MAX}) {
                        for (float flex : new float[]{FlyingCarpetLogic.FLEX_MIN, 0.0F, FlyingCarpetLogic.FLEX_MAX}) {
                            for (int phase = 0; phase < 8; phase++) {
                                double w = phase * Math.PI / 4.0;
                                for (int ms = 400; ms <= 3000; ms += (ms < 1400 ? 10 : 125)) {
                                    float t = ms / 1000.0F;
                                    FlyingCarpetMotion.timeline(t, tp);
                                    FlyingCarpetMotion.tilt(t, speed, sneak, bank, nose, FlyingCarpetMotion.altitude(t, alt, sneak, 0.0F, tp), tl);
                                    FlyingCarpetLogic.shape(tp.unroll, tp.rise, speed, sneak, w, w * 1.3, flex, sh);
                                    for (double[] c : carpetCorners(sh, FlyingCarpetLogic.GROUND_MID_Y, tl)) {
                                        if (c[1] < low) {
                                            low = c[1];
                                            where = "t=" + t + " s=" + speed + " k=" + sneak + " bank=" + bank + " nose=" + nose + " flex=" + flex;
                                        }
                                        if (t >= FlyingCarpetMotion.INTRO) hoverLow = Math.min(hoverLow, c[1]);
                                    }
                                    // Quastenspitzen (hinten und vorne): am Boden flach, beim Schweben hängend
                                    for (int i = 0; i < FlyingCarpetLogic.TASSELS; i++) {
                                        for (boolean back : new boolean[]{true, false}) {
                                            double[] tip = tasselTip(sh, back, i);
                                            double[] f = {tip[0], FlyingCarpetLogic.GROUND_MID_Y + tip[1] * PX, FlyingCarpetLogic.CENTER_Z + tip[2] * PX};
                                            double y = FlyingCarpetLogic.tiltPoint(tl, f[0], f[1], f[2], new double[3])[1];
                                            if (y < tasselLow) {
                                                tasselLow = y;
                                                tWhere = "t=" + t + " s=" + speed + " k=" + sneak + " bank=" + bank + " nose=" + nose + " flex=" + flex;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        check(low >= -1e-9, "Teppich nie im Boden, auch beim Aufsteigen (tiefstens " + low + ", " + where + ")");
        check(tasselLow >= -1e-9, "Quasten nie im Boden (tiefstens " + tasselLow + ", " + tWhere + ")");
        check(hoverLow > 0.4, "schwebt deutlich über dem Boden (tiefster Punkt " + hoverLow + ")");
    }

    /** Spitze einer Quaste im Teppich-Raum: {x Rahmen (Blöcke), y px, z px}. */
    private static double[] tasselTip(FlyingCarpetLogic.Shape sh, boolean back, int i) {
        int edge = back ? FlyingCarpetLogic.SEGMENTS : 0;
        double a = Math.toRadians(back ? sh.backAbs[i] : sh.frontAbs[i]);
        double len = FlyingCarpetLogic.TASSEL_LEN_PX;
        // Rx(a)·(0,0,±1): y = ∓sin a, z = ±cos a
        double dy = back ? -Math.sin(a) * len : Math.sin(a) * len, dz = back ? Math.cos(a) * len : -Math.cos(a) * len;
        return new double[]{-FlyingCarpetLogic.TASSEL_X[i] * PX, sh.jy[edge] + dy, sh.jz[edge] + dz};
    }

    private static void waveAndTassels() {
        check(FlyingCarpetLogic.waveAmp(1.0F, 0.0F) > 1.8F * FlyingCarpetLogic.waveAmp(0.0F, 0.0F), "Welle in Fahrt stärker");
        check(FlyingCarpetLogic.waveHz(1.0F, 0.0F) > 2.5F * FlyingCarpetLogic.waveHz(0.0F, 0.0F), "Welle in Fahrt schneller");
        check(FlyingCarpetLogic.waveHz(0.0F, 0.0F) < 0.6F && FlyingCarpetLogic.waveAmp(0.0F, 0.0F) > 1.0F, "im Stand langsame Welle");
        check(FlyingCarpetLogic.waveAmp(0.2F, 1.0F) < FlyingCarpetLogic.waveAmp(0.2F, 0.0F)
                && FlyingCarpetLogic.waveHz(0.2F, 1.0F) < FlyingCarpetLogic.waveHz(0.2F, 0.0F), "Schleichen: ruhigere Welle");
        // Welle läuft von vorne nach hinten: Maximum der Neigung kommt bei weiter hinten liegenden Segmenten später
        FlyingCarpetLogic.Shape sh = new FlyingCarpetLogic.Shape();
        double[] best = new double[3], at = new double[3];
        java.util.Arrays.fill(best, -1e9);
        for (int i = 0; i < 1000; i++) {
            double phase = i * 2.0 * Math.PI / 1000.0;
            FlyingCarpetLogic.shape(1.0F, 1.0F, 1.0F, 0.0F, phase, 0.0, 0.0F, sh);
            for (int j = 0; j < 3; j++) {
                double up = -sh.seg[3 + j]; // Erhebung nach hinten
                if (up > best[j]) {
                    best[j] = up;
                    at[j] = phase;
                }
            }
        }
        check(at[1] > at[0] && at[2] > at[1], "Welle läuft nach hinten (" + at[0] + ", " + at[1] + ", " + at[2] + ")");
        // Unter Reiter und Füßen nie nach oben (vordere Segmente, Gelenke J0..J3 höchstens auf Ruhehöhe)
        double maxUp = -1e9;
        for (int i = 0; i < 400; i++) {
            for (float speed : new float[]{0.0F, 1.0F}) {
                for (float flex : new float[]{FlyingCarpetLogic.FLEX_MIN, 0.0F, FlyingCarpetLogic.FLEX_MAX}) {
                    FlyingCarpetLogic.shape(1.0F, 1.0F, speed, 0.0F, i * 0.05, i * 0.07, flex, sh);
                    for (int k = 0; k <= FlyingCarpetLogic.ANCHOR; k++) maxUp = Math.max(maxUp, sh.jy[k]);
                    // Gesäß liegt bis 0,4 px hinter dem Anker auf seg3
                    maxUp = Math.max(maxUp, -0.4 * Math.sin(Math.toRadians(sh.seg[3])));
                }
            }
        }
        check(maxUp <= 0.1, "unter dem Reiter wellt sich der Teppich nur nach unten (" + maxUp + " px)");
        // Hinten deutlich sichtbar
        double lo = 1e9, hi = -1e9;
        for (int i = 0; i < 400; i++) {
            FlyingCarpetLogic.shape(1.0F, 1.0F, 1.0F, 0.0F, i * 0.05, 0.0, 0.0F, sh);
            lo = Math.min(lo, sh.jy[6]);
            hi = Math.max(hi, sh.jy[6]);
        }
        check(hi - lo > 1.0, "Hinterkante wellt sich sichtbar (" + (hi - lo) + " px)");
        // Quasten: hängen im Stand, hinten wehen sie in voller Fahrt nach hinten
        FlyingCarpetLogic.shape(1.0F, 1.0F, 0.0F, 0.0F, 0.0, 0.0, 0.0F, sh);
        for (int i = 0; i < FlyingCarpetLogic.TASSELS; i++) {
            check(sh.backAbs[i] > 55.0F, "hintere Quasten hängen im Stand (" + sh.backAbs[i] + ")");
            check(sh.frontAbs[i] < -55.0F, "vordere Quasten hängen im Stand (" + sh.frontAbs[i] + ")");
        }
        // Am Boden (ausgerollt, noch nicht aufgestiegen bzw. gelandet) liegen sie flach neben dem Teppich
        FlyingCarpetLogic.shape(1.0F, 0.0F, 1.0F, 0.0F, 0.7, 1.9, 0.0F, sh);
        for (int i = 0; i < FlyingCarpetLogic.TASSELS; i++) {
            check(Math.abs(sh.backAbs[i]) < 1e-6F && Math.abs(sh.frontAbs[i]) < 1e-6F, "Quasten am Boden flach");
        }
        double maxBlown = -1e9;
        for (int k = 0; k < 200; k++) {
            FlyingCarpetLogic.shape(1.0F, 1.0F, 1.0F, 0.0F, 0.0, k * 0.1, 0.0F, sh);
            for (int i = 0; i < FlyingCarpetLogic.TASSELS; i++) maxBlown = Math.max(maxBlown, sh.backAbs[i]);
        }
        check(maxBlown < 30.0, "hintere Quasten wehen in Fahrt nach hinten (" + maxBlown + "°)");
        // Quasten schwingen (Phase ändert die Winkel)
        FlyingCarpetLogic.shape(1.0F, 1.0F, 0.5F, 0.0F, 0.0, 0.0, 0.0F, sh);
        float b0 = sh.backAbs[0];
        FlyingCarpetLogic.shape(1.0F, 1.0F, 0.5F, 0.0F, 0.0, 1.5, 0.0F, sh);
        check(Math.abs(sh.backAbs[0] - b0) > 2.0F, "Quasten schwingen");
        // Phase der Welle bleibt beim Wechsel der Geschwindigkeit stetig
        FlyingCarpetLogic.Track tr = fresh();
        double prev = tr.wave, maxStep = 0;
        long ms = 0;
        for (int i = 0; i < 400; i++) {
            ground(tr, 0.0F, (i / 20) % 2 == 0 ? 0.0 : 0.3, ms += 50);
            maxStep = Math.max(maxStep, tr.wave - prev);
            prev = tr.wave;
        }
        check(maxStep <= 2.0 * Math.PI * FlyingCarpetLogic.WAVE_FAST_HZ * TICK + 1e-9, "Phase stetig (keine Sprünge)");
    }

    // ---------------- Modell und Textur ----------------

    private record Face(String dir, double[] uv, double[] size) {}

    private record MCube(double[] o, double[] s, List<Face> faces) {}

    private static final class MBone {
        String name, parent;
        double[] pivot;
        final List<MCube> cubes = new ArrayList<>();
    }

    private static Map<String, MBone> bones;

    private static Map<String, MBone> bones() {
        if (bones != null) return bones;
        String text = read(ROOT + "emotes/flying_carpet/flying_carpet.geo.json");
        Map<String, MBone> out = new java.util.LinkedHashMap<>();
        Pattern name = Pattern.compile("\"name\":\\s*\"([^\"]+)\"");
        Pattern parent = Pattern.compile("\"parent\":\\s*\"([^\"]+)\"");
        Pattern pivot = Pattern.compile("^\\s*\"pivot\":\\s*\\[([^\\]]*)\\]");
        Pattern cube = Pattern.compile("\\{\"origin\":\\s*\\[([^\\]]*)\\],\\s*\"size\":\\s*\\[([^\\]]*)\\],\\s*\"uv\":\\s*\\{(.*)\\}\\}");
        Pattern face = Pattern.compile("\"(north|south|east|west|up|down)\":\\s*\\{\"uv\":\\s*\\[([^\\]]*)\\],\\s*\"uv_size\":\\s*\\[([^\\]]*)\\]\\}");
        MBone cur = null;
        for (String line : text.split("\n")) {
            Matcher m = name.matcher(line);
            if (m.find()) {
                cur = new MBone();
                cur.name = m.group(1);
                out.put(cur.name, cur);
                continue;
            }
            if (cur == null) continue;
            m = parent.matcher(line);
            if (m.find()) cur.parent = m.group(1);
            m = pivot.matcher(line);
            if (m.find()) cur.pivot = nums(m.group(1));
            m = cube.matcher(line);
            if (m.find()) {
                List<Face> faces = new ArrayList<>();
                Matcher f = face.matcher(m.group(3));
                while (f.find()) faces.add(new Face(f.group(1), nums(f.group(2)), nums(f.group(3))));
                cur.cubes.add(new MCube(nums(m.group(1)), nums(m.group(2)), faces));
            }
        }
        bones = out;
        return out;
    }

    private static void model() {
        Map<String, MBone> b = bones();
        check(b.size() == FlyingCarpetLogic.SEGMENTS + 2 * FlyingCarpetLogic.TASSELS, "6 Segmente und 8 Quasten (" + b.size() + ")");
        double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9, minY = 1e9, maxY = -1e9;
        for (int k = 0; k < FlyingCarpetLogic.SEGMENTS; k++) {
            MBone s = b.get("seg" + k);
            check(s != null, "Knochen seg" + k);
            check(k == 0 ? s.parent == null : ("seg" + (k - 1)).equals(s.parent), "seg" + k + " hängt am vorherigen");
            near(s.pivot[2], -0.5 * FlyingCarpetLogic.LENGTH_PX + k * FlyingCarpetLogic.SEG_PX, 1e-9, "Gelenk vorne an seg" + k);
            near(s.pivot[1], 0.5 * FlyingCarpetLogic.THICK_PX, 1e-9, "Gelenk auf halber Dicke");
            check(s.cubes.size() == 1, "ein Würfel pro Segment");
            MCube c = s.cubes.get(0);
            near(c.s()[2], FlyingCarpetLogic.SEG_PX, 1e-9, "Segment 4 px lang");
            near(c.o()[2], s.pivot[2], 1e-9, "Segment beginnt am Gelenk");
            minX = Math.min(minX, c.o()[0]);
            maxX = Math.max(maxX, c.o()[0] + c.s()[0]);
            minY = Math.min(minY, c.o()[1]);
            maxY = Math.max(maxY, c.o()[1] + c.s()[1]);
            minZ = Math.min(minZ, c.o()[2]);
            maxZ = Math.max(maxZ, c.o()[2] + c.s()[2]);
        }
        near(maxX - minX, 16.0, 1e-9, "Teppich 16 px breit (1 Block)");
        near(maxZ - minZ, 24.0, 1e-9, "Teppich 24 px lang (1,5 Blöcke)");
        near(maxY - minY, 1.0, 1e-9, "Teppich 1 px dick");
        near(minX + maxX, 0.0, 1e-9, "mittig");
        near(minZ + maxZ, 0.0, 1e-9, "mittig (Anker in der Mitte)");
        for (int i = 0; i < FlyingCarpetLogic.TASSELS; i++) {
            MBone tf = b.get("tf" + i), tb = b.get("tb" + i);
            check(tf != null && "seg0".equals(tf.parent), "vordere Quaste " + i + " an seg0");
            check(tb != null && "seg5".equals(tb.parent), "hintere Quaste " + i + " an seg5");
            near(tf.pivot[0], FlyingCarpetLogic.TASSEL_X[i], 1e-9, "Quaste vorne quer verteilt");
            near(tb.pivot[0], FlyingCarpetLogic.TASSEL_X[i], 1e-9, "Quaste hinten quer verteilt");
            near(tf.pivot[2], -12.0, 1e-9, "Quaste an der Vorderkante");
            near(tb.pivot[2], 12.0, 1e-9, "Quaste an der Hinterkante");
            double reach = 0.0;
            for (MCube c : tb.cubes) reach = Math.max(reach, c.o()[2] + c.s()[2] - 12.0);
            near(reach, FlyingCarpetLogic.TASSEL_LEN_PX, 1e-9, "Quastenlänge wie in der Logik");
        }
        // Textur 64x32, alle UV-Flächen innerhalb und deckend
        String text = read(ROOT + "emotes/flying_carpet/flying_carpet.geo.json");
        Matcher tw = Pattern.compile("\"texture_width\":\\s*(\\d+),\\s*\"texture_height\":\\s*(\\d+)").matcher(text);
        check(tw.find() && "64".equals(tw.group(1)) && "32".equals(tw.group(2)), "Texturgröße 64x32 im Modell");
        BufferedImage img;
        try {
            img = ImageIO.read(new File(ROOT + "textures/entity/flying_carpet.png"));
        } catch (IOException e) {
            throw new AssertionError("Textur fehlt: " + e.getMessage());
        }
        check(img != null && img.getWidth() == 64 && img.getHeight() == 32, "Textur 64x32");
        int faces = 0, cubes = 0;
        for (MBone bone : b.values()) {
            for (MCube c : bone.cubes) {
                cubes++;
                check(c.faces().size() == 6, "alle 6 Flächen mit UV");
                for (Face f : c.faces()) {
                    int u = (int) f.uv()[0], v = (int) f.uv()[1];
                    int us = (int) Math.ceil(f.size()[0]), vs = (int) Math.ceil(f.size()[1]);
                    check(u >= 0 && v >= 0 && u + us <= 64 && v + vs <= 32, "UV innerhalb der Textur");
                    for (int x = u; x < u + us; x++) {
                        for (int y = v; y < v + vs; y++) {
                            if ((img.getRGB(x, y) >>> 24) < 255) throw new AssertionError("Textur-Loch bei " + x + "," + y);
                        }
                    }
                    faces++;
                }
            }
        }
        check(faces == cubes * 6, "Flächen gezählt (" + faces + ")");
        // Persischer Stil: Oberseite überwiegend tiefes Rot, goldener Rand, Ornament in der Mitte, dunkelblaue Akzente
        int red = 0, blue = 0, total = 0, goldRim = 0, rim = 0;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 24; y++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
                total++;
                if (r > 70 && r > 2 * g && r > 2 * bl) red++;
                if (bl > r && bl > g && bl > 40) blue++;
                if (x == 0 || y == 0 || x == 15 || y == 23) {
                    rim++;
                    if (r > 120 && g > 80 && bl < 80) goldRim++;
                }
            }
        }
        check(red >= total * 0.3, "überwiegend tiefes Rot (" + red + "/" + total + ")");
        check(goldRim == rim, "goldener Rand");
        check(blue >= total * 0.08, "dunkelblaue Akzente (" + blue + ")");
        int center = img.getRGB(7, 11), field = img.getRGB(4, 9);
        check(center != field, "Ornament in der Mitte");
        // Laden mit blockbenchAxisFix im Renderer
        String rend = read(SRC + "emotes/EmotePropFeatureRenderer.java");
        check(rend.contains("emotes/flying_carpet/flying_carpet.geo.json") && rend.contains("textures/entity/flying_carpet.png"),
                "Renderer lädt Modell und Textur");
        check(stripComments(section(rend, "// Fliegender Teppich", "Wurfversatz (Modellraum)")).contains("loadProp(FLYING_CARPET_MODEL)"),
                "über loadProp (blockbenchAxisFix = true)");
    }

    // ---------------- Knochen-Mathematik wie BedrockCosmeticModel ----------------

    private static double[] mat() {
        return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    private static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
                r[i * 4 + j] = s;
            }
        }
        return r;
    }

    private static double[] tr(double x, double y, double z) {
        double[] m = mat();
        m[3] = x;
        m[7] = y;
        m[11] = z;
        return m;
    }

    private static double[] rot(int axis, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return switch (axis) {
            case 0 -> new double[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
            case 1 -> new double[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
            default -> new double[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        };
    }

    private static double[] apply(double[] m, double x, double y, double z) {
        return new double[]{m[0] * x + m[1] * y + m[2] * z + m[3], m[4] * x + m[5] * y + m[6] * z + m[7],
                m[8] * x + m[9] * y + m[10] * z + m[11]};
    }

    /** Wie BedrockCosmeticModel.renderBone mit blockbenchAxisFix: T(p + t) · Rz(−rz) · Ry(ry) · Rx(−rx) · T(−p). */
    private static double[] boneMat(double[] parent, MBone b, double animRx, double[] animT) {
        double[] p = b.pivot;
        double[] m = tr(p[0] + animT[0], p[1] + animT[1], p[2] + animT[2]);
        m = mul(m, rot(2, -0.0));
        m = mul(m, rot(1, 0.0));
        m = mul(m, rot(0, -animRx));
        m = mul(m, tr(-p[0], -p[1], -p[2]));
        return mul(parent, m);
    }

    private static void boneMathMatchesLogic() {
        Map<String, MBone> b = bones();
        FlyingCarpetLogic.Shape sh = new FlyingCarpetLogic.Shape();
        double worst = 0.0, worstTassel = 0.0;
        Random rnd = new Random(3);
        for (int n = 0; n < 300; n++) {
            float unroll = n < 100 ? n / 100.0F : 1.0F;
            FlyingCarpetLogic.shape(unroll, (float) rnd.nextDouble(), (float) rnd.nextDouble(), (float) rnd.nextDouble(),
                    rnd.nextDouble() * 50.0, rnd.nextDouble() * 50.0, (float) ((rnd.nextDouble() - 0.4) * 20.0), sh);
            // Werte wie im Renderer (drawFlyingCarpet): relativ zum vorherigen Segment, negiert (blockbenchAxisFix)
            double[] animRx = new double[FlyingCarpetLogic.SEGMENTS];
            float prev = 0.0F;
            for (int k = 0; k < FlyingCarpetLogic.SEGMENTS; k++) {
                animRx[k] = -(sh.seg[k] - prev);
                prev = sh.seg[k];
            }
            double[] t0 = {0.0, sh.jy[0], sh.jz[0] + 0.5 * FlyingCarpetLogic.LENGTH_PX};
            double[] m = mat();
            double[][] segMat = new double[FlyingCarpetLogic.SEGMENTS][];
            for (int k = 0; k < FlyingCarpetLogic.SEGMENTS; k++) {
                MBone s = b.get("seg" + k);
                m = boneMat(m, s, animRx[k], k == 0 ? t0 : new double[3]);
                segMat[k] = m;
                // Gelenk J_k = Drehpunkt von seg_k nach seiner Transformation
                double[] j = apply(m, s.pivot[0], s.pivot[1], s.pivot[2]);
                worst = Math.max(worst, Math.abs(j[1] - (0.5 + sh.jy[k])) + Math.abs(j[2] - sh.jz[k]) + Math.abs(j[0]));
            }
            double[] end = apply(segMat[5], 0.0, 0.5, 12.0);
            worst = Math.max(worst, Math.abs(end[1] - (0.5 + sh.jy[6])) + Math.abs(end[2] - sh.jz[6]));
            // Quasten: Richtung im Rahmen = absoluter Winkel der Logik
            for (int i = 0; i < FlyingCarpetLogic.TASSELS; i++) {
                for (boolean back : new boolean[]{false, true}) {
                    MBone tb = b.get((back ? "tb" : "tf") + i);
                    double[] tm = boneMat(segMat[back ? 5 : 0], tb, back ? -sh.back[i] : -sh.front[i], new double[3]);
                    double[] p0 = apply(tm, tb.pivot[0], tb.pivot[1], tb.pivot[2]);
                    double[] p1 = apply(tm, tb.pivot[0], tb.pivot[1], tb.pivot[2] + (back ? 1.0 : -1.0));
                    double dy = p1[1] - p0[1], dz = p1[2] - p0[2];
                    double ang = back ? Math.toDegrees(Math.atan2(-dy, dz)) : Math.toDegrees(Math.atan2(dy, -dz));
                    double want = back ? sh.backAbs[i] : sh.frontAbs[i];
                    worstTassel = Math.max(worstTassel, Math.abs(HammockLogic.wrapDegrees(ang - want)));
                }
            }
        }
        check(worst < 1e-4, "Knochen ergeben genau die Gelenke der Logik (Abweichung " + worst + " px)");
        check(worstTassel < 1e-3, "Quasten zeigen wie in der Logik (Abweichung " + worstTassel + "°)");
        // Der Renderer setzt die Werte genau so
        String r = stripComments(section(read(SRC + "emotes/EmotePropFeatureRenderer.java"), "// Fliegender Teppich", "Wurfversatz (Modellraum)"));
        check(r.contains("a[i] = -(sh.seg[i] - prev);"), "Renderer: Segmente relativ und negiert");
        check(r.contains("a[n] = (float) sh.jy[0];") && r.contains("a[n + 1] = (float) (sh.jz[0] + 0.5 * FlyingCarpetLogic.LENGTH_PX);"),
                "Renderer: seg0 verschoben (Anker bleibt in der Mitte)");
        check(r.contains("a[n + 2 + i] = -sh.front[i];") && r.contains("a[n + 2 + k4 + i] = -sh.back[i];"), "Renderer: Quasten negiert");
        check(r.contains("carpet.setBoneTranslation(FC_SEGMENTS[0], 0.0F, a[n], a[n + 1]);"), "Renderer: Verschiebung an seg0");
        check(r.contains("carpet.resetAllAnimations();") && r.indexOf("submitCustom") < r.indexOf("carpet.resetAllAnimations();"),
                "Knochenwerte erst im Zeichen-Callback (geteiltes Modell, verzögertes Zeichnen)");
    }

    // ---------------- Effekte ----------------

    private static void effects() {
        near(FlyingCarpetLogic.TRAIL_EVERY * TICK, 0.15, 1e-6, "Funkelspur alle ca. 0,15 s");
        check(FlyingCarpetLogic.IDLE_SPARK_CHANCE <= 0.05F, "im Stand nur selten ein Funke");
        near(FlyingCarpetLogic.FX_RANGE, 32.0, 1e-9, "Partikel bis 32 Blöcke");
        near(FlyingCarpetLogic.SOUND_RANGE, 24.0, 1e-9, "Geräusche bis 24 Blöcke");
        check(FlyingCarpetLogic.WIND_FAST <= 0.15F && FlyingCarpetLogic.WIND_IDLE < FlyingCarpetLogic.WIND_FAST,
                "Wind sehr leise, lauter mit der Geschwindigkeit");
        check(FlyingCarpetLogic.WIND_PITCH >= 1.4F, "Wind mit hohem Pitch");
        String co = stripComments(read(SRC + "emotes/FlyingCarpetCoordinator.java"));
        check(co.contains("ParticleTypes.WAX_ON") && co.contains("SoundEvents.ITEM_ELYTRA_FLYING"), "WAX_ON-Funken, Elytra-Wind");
        check(co.contains("SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME") && co.contains("BLOCK_NOTE_BLOCK_CHIME"), "magisches Pling beim Start");
        check(co.contains("FX_RANGE * FX_RANGE") && co.contains("SOUND_RANGE * SOUND_RANGE"), "Reichweiten werden geprüft");
        // Vorschau ohne Partikel und Geräusche: Zeichnen (Animator, Renderer) erzeugt keine Effekte
        String anim = stripComments(section(read(SRC + "emotes/EmoteAnimator.java"), "// Fliegender Teppich", "private static float clampF"));
        String rend = stripComments(section(read(SRC + "emotes/EmotePropFeatureRenderer.java"), "// Fliegender Teppich", "Wurfversatz (Modellraum)"));
        for (String s : new String[]{anim, rend}) {
            check(!s.contains("addParticle") && !s.contains("playSound") && !s.contains("getSoundManager"),
                    "beim Zeichnen keine Partikel/Geräusche (Vorschau)");
        }
        // Effekte nur im Tick für Spieler in der Welt (die Vorschau hat keine Welt-Spieler)
        check(co.indexOf("private static void effects(") > co.indexOf("public static void tick("), "Effekte aus dem Tick");
        // Funkeln beim Aufsteigen; Funkelspur und Funken sitzen am höheren Teppich (mit Schwebehöhe und Neigung)
        String fx = section(co, "private static void effects(", "private static void sparkle(");
        check(fx.contains("t < FlyingCarpetMotion.RISE_END") && fx.contains("ParticleTypes.WAX_ON"), "Funkeln beim Aufsteigen");
        check(fx.contains("tiltedToWorld(p, tl, fx, FlyingCarpetLogic.GROUND_MID_Y - 0.02, fz)"), "Funkelspur am schwebenden Teppich");
        check(co.contains("applied(v, FX_TILT);") && co.contains("effects(world, p, st, v.t, FX_TILT, d2, mode);"),
                "Partikel mit derselben Höhe wie gezeichnet");
    }

    // ---------------- Rein optisch ----------------

    private static final String[] FORBIDDEN = {
            "setPos", "setPosition", "updatePosition", "refreshPositionAndAngles", "teleport", "setVelocity", "addVelocity",
            ".move(", "setBoundingBox", "setOnGround", "flying =", "allowFlying", "networkHandler", "sendPacket",
            "PlayerMoveC2SPacket", "setYaw(", "setBodyYaw", "setHeadYaw", "setPitch(", "fallDistance", "noClip",
            // zusätzlich: Abilities, Pakete, Eingaben, Attribute, Pose/Hitbox, eigene Sync-Nachrichten
            "setFlying", "sendAbilities", "UpdatePlayerAbilities", "C2SPacket", "getNetworkHandler",
            "ClientPlayNetworkHandler", "setSprinting", "setSneaking", "jump(", ".input.", "setMovementSpeed",
            "getAttributeInstance", "EntityAttributes", "setStepHeight", "setPose(", "calculateDimensions", "setNoGravity",
            "CaeserSyncClient", "lanEnvelope", "JsonObject", "broadcastLocalEmote"};

    private static void purelyVisual() {
        List<String> sources = new ArrayList<>();
        sources.add(read(SRC + "emotes/FlyingCarpetCoordinator.java"));
        sources.add(read(SRC + "emotes/FlyingCarpetLogic.java"));
        sources.add(read(SRC + "emotes/FlyingCarpetMotion.java"));
        sources.add(section(read(SRC + "emotes/EmoteAnimator.java"), "// Fliegender Teppich", "private static float clampF"));
        sources.add(section(read(SRC + "emotes/EmotePropFeatureRenderer.java"), "// Fliegender Teppich", "Wurfversatz (Modellraum)"));
        String module = read(SRC + "module/EmoteModule.java");
        sources.add(section(module, "// Fliegender Teppich: nur an Land", "// Hexenbesen"));
        sources.add(section(module, "// Fliegender Teppich: Glättung", "CaeserPlayerSyncManager.broadcastLocalEmote(type, false);"));
        String cam = read(SRC + "emotes/EmoteCamera.java");
        sources.add(section(cam, "// Fliegender Teppich: Drehpunkt", "if (!isPushUpCamera(focused) || !(focused instanceof LivingEntity living))"));
        String mixin = read(SRC + "mixin/PlayerEntityRendererMixin.java");
        sources.add(section(mixin, "// Fliegender Teppich: Nametag", "\n    }"));
        Pattern assignFlying = Pattern.compile("\\bflying\\s*=(?!=)");
        for (String s : sources) {
            String code = stripComments(s);
            for (String bad : FORBIDDEN) check(!code.contains(bad), "Emote-Code ändert nichts am Spieler/Server: " + bad);
            check(!assignFlying.matcher(code).find(), "Emote-Code schaltet nie Fliegen ein");
            check(!code.contains("abilities."), "keine Abilities-Felder");
        }
        // Transformation nur auf die Render-Matrix; die Höhe ist eine Verschiebung in setupTransforms
        String anim = section(read(SRC + "emotes/EmoteAnimator.java"), "// Fliegender Teppich", "private static float clampF");
        check(anim.contains("MatrixStack matrices") && anim.contains("matrices.translate") && anim.contains("matrices.multiply"),
                "nur Render-Matrix");
        check(anim.contains("matrices.translate(0.0F, (float) tl.dy, 0.0F);"), "Schwebehöhe nur als Verschiebung der Render-Matrix");
        // Was mit der Höhe mitgeht: Nametag (nur Render-State), Kamera-Drehpunkt (Mindestneigung nur bei den anderen
        // Emote-Kameras), Sichtbarkeits-Box; der Vanilla-Schatten bleibt unverändert am Boden
        String coAll = read(SRC + "emotes/FlyingCarpetCoordinator.java");
        check(mixin.contains("FlyingCarpetCoordinator.liftNameLabel(state);"), "Nametag wird mit angehoben");
        check(coAll.contains("state.nameLabelPos = state.nameLabelPos.add(0.0, up, 0.0);"), "Nametag nur im Render-State");
        check(cam.contains("FlyingCarpetCoordinator.localHeadLift(player)")
                && cam.contains("if (!isEmoteCamera(mc.getCameraEntity())) return pitch;"), "Kamera: nur der Drehpunkt höher");
        check(section(coAll, "public static Box cullBox(", "\n    }").contains("stretch(0.0, (FlyingCarpetLogic.ALT_FAST + 0.3) * s, 0.0)"),
                "Sichtbarkeits-Box nach oben vergrößert");
        check(read(SRC + "mixin/EntityRendererMixin.java").contains("FlyingCarpetCoordinator.cullBox(player)"), "Box wird genutzt");
        for (String s : sources) check(!stripComments(s).toLowerCase().contains("shadow"), "Vanilla-Schatten bleibt am Boden");
        // Fehlstart ohne Action-Bar-Text (leises Zischen wie beim Hexenbesen)
        String startBlock = stripComments(section(module, "// Fliegender Teppich: nur an Land", "// Hexenbesen"));
        check(startBlock.contains("playStartDeniedSound(client);") && !startBlock.contains("sendMessage"),
                "Fehlstart: nur leises Geräusch, kein Action-Bar-Text");
        // Koordinator liest den Spieler nur
        String co = stripComments(read(SRC + "emotes/FlyingCarpetCoordinator.java"));
        check(!co.contains("client.player."), "keine Schreibzugriffe über client.player");
        check(co.contains("getAbilities().flying;") && !co.contains("getAbilities().flying ="), "Fliegen wird nur gelesen");
        // Ende über das vorhandene STOP (EmoteModule), keine eigenen Nachrichten
        check(co.contains("EmoteModule.stopEmote()"), "endet über das vorhandene emote-STOP");
        // Abbruch bei Angriff/Item wie bei den anderen laufenden Emotes (onTick unverändert, allowsWalking)
        String onTick = stripComments(section(module, "public static void onTick(", "\n}"));
        check(onTick.contains("isUsingItem()") && onTick.contains("handSwinging") && !onTick.contains("FLYING_CARPET"),
                "Abbruch bei Item/Angriff über EmoteModule.onTick (unverändert)");
        // Start nur an Land oder beim erlaubten Fliegen; Ende bei Wasser, Lava, Leiter, Reiten, Schlafen, Elytra, Zuschauer
        String must = section(co, "public static boolean mustEnd(", "}");
        for (String cond : new String[]{"isSwimming()", "isSubmergedInWater()", "isInLava()", "isClimbing()", "hasVehicle()",
                "isSleeping()", "isGliding()", "isSpectator()"}) {
            check(must.contains(cond), "endet automatisch: " + cond);
        }
        String start = section(co, "public static boolean canStart(", "}\n");
        check(start.contains("isOnGround()") && start.contains("getAbilities().flying") && start.contains("mustEnd(p)"),
                "Start an Land oder beim erlaubten Fliegen");
        // Logik ohne Minecraft-Klassen
        for (String f : new String[]{"FlyingCarpetLogic.java", "FlyingCarpetMotion.java"}) {
            check(!read(SRC + "emotes/" + f).contains("import net.minecraft"), f + " ohne Minecraft-Klassen");
        }
    }

    // ---------------- Kleinkram ----------------

    private static String section(String text, String from, String to) {
        int a = text.indexOf(from);
        check(a >= 0, "Abschnitt gefunden: " + from);
        int b = text.indexOf(to, a + from.length());
        check(b > a, "Abschnittsende gefunden: " + to);
        return text.substring(a, b);
    }

    private static String stripComments(String s) {
        return s.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    private static String read(String file) {
        try {
            return Files.readString(Path.of(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new AssertionError("Datei fehlt: " + file + " (" + e.getMessage() + ")");
        }
    }

    private static double[] nums(String s) {
        String[] parts = s.split(",");
        double[] out = new double[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Double.parseDouble(parts[i].trim());
        return out;
    }

}
