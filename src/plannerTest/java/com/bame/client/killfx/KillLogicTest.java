package com.bame.client.killfx;

import java.util.UUID;

/**
 * Prüft ohne Minecraft die Kill-Logik aus {@link KillLogic}: 8-s-Trefferfenster, Abklingzeit pro Opfer, Mob-Schalter,
 * Totem-Ausschluss, Chat-Regeln, Fernkampf-Kriterium, Opfernamen, die Abklingzeiten für Testen und Senden sowie die
 * Zeitverläufe und Geometrie von Sarg, Amboss, Thanos Snap und Hell Gate.
 */
public class KillLogicTest {

    private static int checks = 0;

    public static void main(String[] args) {
        effectTypes();
        hitWindow();
        victimCooldown();
        mobToggle();
        totemExclusion();
        chatRules();
        chatAttribution();
        projectile();
        victimNames();
        cooldowns();
        prune();
        coffinTimeline();
        anvilTimeline();
        armorFallback();
        expiringCache();
        snapTimeline();
        snapMesh();
        hellGateTimeline();
        noScreenText();
        System.out.println("KILLFX TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private static void effectTypes() {
        check(KillEffectType.values().length == 7, "7 effects");
        check(KillEffectType.fromId("kill_snap") == KillEffectType.SNAP && KillEffectType.fromId("KILL_HELLGATE") == KillEffectType.HELLGATE, "snap/hellgate ids");
        check("Thanos Snap".equals(KillEffectType.SNAP.displayName) && "Hell Gate".equals(KillEffectType.HELLGATE.displayName), "snap/hellgate names");
        check(KillEffectType.SNAP.usesFigure() && KillEffectType.HELLGATE.usesFigure() && KillEffectType.ANVIL.usesFigure()
                && !KillEffectType.COFFIN.usesFigure() && !KillEffectType.TOTEM.usesFigure(), "figure effects");
        check(KillEffectType.fromId("kill_coffin") == KillEffectType.COFFIN, "coffin id");
        check(KillEffectType.fromId("KILL_ANVIL") == KillEffectType.ANVIL, "anvil id");
        check("Coffin".equals(KillEffectType.COFFIN.displayName) && "Anvil".equals(KillEffectType.ANVIL.displayName), "new english names");
        check(!KillEffectType.COFFIN.hasScreenPart && !KillEffectType.ANVIL.hasScreenPart, "coffin/anvil world only");
        check(KillEffectType.fromId("kill_totem") == KillEffectType.TOTEM, "case-insensitive id");
        check(KillEffectType.fromId(" KILL_GRAVE ") == KillEffectType.GRAVE, "trimmed id");
        check(KillEffectType.fromId("none") == null, "none is no effect");
        check(KillEffectType.fromId("ANGEL") == null, "foreign id unknown");
        check(KillEffectType.fromId(null) == null, "null id");
        // Entfernte Effekte sind unbekannt (Sync-Nachrichten werden ignoriert, Config lädt sie als "none")
        for (String removed : new String[]{"KILL_FIREWORK", "KILL_SOUL", "KILL_CONFETTI"}) {
            check(KillEffectType.fromId(removed) == null, "removed id " + removed);
        }
        check(KillEffectType.TOTEM.hasScreenPart && KillEffectType.LIGHTNING.hasScreenPart, "screen parts");
        check(!KillEffectType.GRAVE.hasScreenPart, "world only");
        String[] ids = {"KILL_TOTEM", "KILL_LIGHTNING", "KILL_GRAVE", "KILL_COFFIN", "KILL_ANVIL", "KILL_SNAP", "KILL_HELLGATE"};
        for (String id : ids) check(KillEffectType.fromId(id) != null && KillEffectType.fromId(id).id.equals(id), "protocol id " + id);
        check("Totem".equals(KillEffectType.TOTEM.displayName) && "Lightning".equals(KillEffectType.LIGHTNING.displayName)
                && "Grave".equals(KillEffectType.GRAVE.displayName), "english names");
    }

    private static void hitWindow() {
        KillLogic l = new KillLogic();
        check(l.onDeath(A, false, 1000) == KillLogic.Decision.NO_HIT, "death without hit");
        l.recordHit(A, 5, "Steve", true, 1000);
        check(l.recentHit(A, 1000 + 8000) != null, "hit valid at exactly 8 s");
        check(l.recentHit(A, 1000 + 8001) == null, "hit expired after 8 s");
        check(l.onDeath(A, false, 1000 + 8001) == KillLogic.Decision.NO_HIT, "late death ignored");
        l.recordHit(A, 5, "Steve", true, 9000);
        check(l.onDeath(A, false, 12000) == KillLogic.Decision.TRIGGER, "death within window after re-hit");
        check(l.recentHit(A, 12000) == null, "hit consumed after trigger");
        check(l.onDeath(B, false, 12000) == KillLogic.Decision.NO_HIT, "other victim not hit");
        check(l.recentHit(A, 500) == null, "time going backwards is no hit");
    }

    private static void victimCooldown() {
        KillLogic l = new KillLogic();
        l.recordHit(A, 1, "Steve", true, 0);
        check(l.onDeath(A, false, 100) == KillLogic.Decision.TRIGGER, "first trigger");
        l.recordHit(A, 1, "Steve", true, 200);
        check(l.onDeath(A, false, 300) == KillLogic.Decision.COOLDOWN, "second within 3 s blocked");
        check(l.onDeath(A, false, 3099) == KillLogic.Decision.COOLDOWN, "still blocked at 2.999 s");
        check(l.onDeath(A, false, 3100) == KillLogic.Decision.TRIGGER, "allowed again after 3 s");
        // Ein anderes Opfer ist unabhängig
        l.recordHit(B, 2, "Alex", true, 3100);
        check(l.onDeath(B, false, 3150) == KillLogic.Decision.TRIGGER, "other victim independent");
    }

    private static void mobToggle() {
        KillLogic l = new KillLogic();
        l.recordHit(A, 1, "Zombie", false, 0);
        check(l.onDeath(A, false, 100) == KillLogic.Decision.MOB_DISABLED, "mob blocked by default");
        check(l.onDeath(A, true, 200) == KillLogic.Decision.TRIGGER, "mob allowed with option");
        l.recordHit(B, 2, "Steve", true, 0);
        check(l.onDeath(B, false, 100) == KillLogic.Decision.TRIGGER, "player always allowed");
    }

    private static void totemExclusion() {
        KillLogic l = new KillLogic();
        l.recordHit(A, 1, "Steve", true, 0);
        l.recordTotemPop(A, 100);
        check(l.onDeath(A, false, 150) == KillLogic.Decision.TOTEM, "death right after totem pop ignored");
        check(l.recentHit(A, 150) != null, "hit kept after totem (real death may follow)");
        check(l.onDeath(A, false, 100 + KillLogic.TOTEM_GRACE_MS + 1) == KillLogic.Decision.TRIGGER, "real death later counts");
        KillLogic l2 = new KillLogic();
        l2.recordHit(A, 1, "Steve", true, 0);
        l2.recordTotemPop(A, 100);
        check(l2.onChatMessage("Steve was killed by Bame", "Bame", 200) == null, "chat after totem pop ignored");
    }

    private static void chatRules() {
        check(KillLogic.chatMatches("Steve was killed by Bame", "Steve", "Bame"), "killed by");
        check(KillLogic.chatMatches("Bame slain Steve", "Steve", "Bame"), "slain");
        check(KillLogic.chatMatches("§cSteve §7was eliminated by §aBame§7.", "Steve", "Bame"), "formatting stripped");
        check(KillLogic.chatMatches("Steve wurde von Bame getötet", "Steve", "Bame"), "german");
        check(KillLogic.chatMatches("☠ Steve » Bame", "Steve", "Bame"), "skull symbol");
        check(KillLogic.chatMatches("Bame ⚔ Steve", "Steve", "Bame"), "sword symbol");
        check(KillLogic.chatMatches("STEVE WAS SLAIN BY BAME", "Steve", "Bame"), "case-insensitive");
        check(!KillLogic.chatMatches("Steve was killed by Alex", "Steve", "Bame"), "own name missing");
        check(!KillLogic.chatMatches("Bame killed a zombie", "Steve", "Bame"), "victim missing");
        check(!KillLogic.chatMatches("Steve and Bame are friends", "Steve", "Bame"), "keyword missing");
        check(!KillLogic.chatMatches("Steve123 was killed by Bame", "Steve", "Bame"), "victim only as whole word");
        check(!KillLogic.chatMatches("Steve was killed by Bame_2", "Steve", "Bame"), "own name only as whole word");
        check(!KillLogic.chatMatches("Bame was killed by Steve", "Steve", "Bame"), "we were killed by the victim");
        check(!KillLogic.chatMatches("Bame wurde von Steve getötet", "Steve", "Bame"), "we were killed (german)");
        check(!KillLogic.chatMatches("Steve was killed by Steve", "Steve", "Steve"), "same name");
        check(!KillLogic.chatMatches(null, "Steve", "Bame"), "null message");
        check(KillLogic.chatMatches("Steve (Bame's kill) eliminated", "Steve", "Bame"), "possessive own name");
    }

    private static void chatAttribution() {
        KillLogic l = new KillLogic();
        check(l.onChatMessage("Steve was killed by Bame", "Bame", 0) == null, "no hit, no chat kill");
        l.recordHit(A, 1, "Steve", true, 0);
        check(l.onChatMessage("Steve was killed by Bame", "Bame", 8001) == null, "chat after 8 s ignored");
        l.recordHit(A, 1, "Steve", true, 10_000);
        KillLogic.Hit hit = l.onChatMessage("Steve was killed by Bame", "Bame", 12_000);
        check(hit != null && hit.uuid.equals(A), "chat kill attributed");
        l.recordHit(A, 1, "Steve", true, 12_100);
        check(l.onChatMessage("Steve was killed by Bame", "Bame", 12_200) == null, "chat kill respects 3 s cooldown");
        check(l.onDeath(A, false, 12_300) == KillLogic.Decision.COOLDOWN, "death after chat kill within 3 s blocked");
        KillLogic m = new KillLogic();
        m.recordHit(B, 2, "Zombie", false, 0);
        check(m.onChatMessage("Zombie was killed by Bame", "Bame", 100) == null, "chat never for mobs");
    }

    private static void projectile() {
        check(KillLogic.isProjectileHit(0.0, 10, 10), "fresh hurt at hitbox");
        check(KillLogic.isProjectileHit(2.25, 9, 10), "1.5 blocks, hurt one tick old");
        check(KillLogic.isProjectileHit(1.0, 8, 10), "hurt two ticks old");
        check(!KillLogic.isProjectileHit(2.26, 10, 10), "too far");
        check(!KillLogic.isProjectileHit(0.5, 7, 10), "hurt too old");
        check(!KillLogic.isProjectileHit(0.5, 0, 10), "not hurt");
        check(!KillLogic.isProjectileHit(0.5, 0, 0), "never hurt");
    }

    private static void victimNames() {
        check("Steve".equals(KillLogic.sanitizeVictimName("Steve")), "plain name");
        check("Bame_09".equals(KillLogic.sanitizeVictimName("§aBame_09")), "formatting removed");
        check(KillLogic.sanitizeVictimName("Steve Jobs") == null, "space rejected");
        check(KillLogic.sanitizeVictimName("ABCDEFGHIJKLMNOPQ") == null, "17 chars rejected");
        check("ABCDEFGHIJKLMNOP".equals(KillLogic.sanitizeVictimName("ABCDEFGHIJKLMNOP")), "16 chars ok");
        check(KillLogic.sanitizeVictimName("") == null, "empty rejected");
        check(KillLogic.sanitizeVictimName(null) == null, "null rejected");
        check(KillLogic.sanitizeVictimName("Zombie") != null, "mob name fine");
        check(KillLogic.sanitizeVictimName("Zombie-Villager") == null, "dash rejected");
    }

    private static void cooldowns() {
        KillLogic.Cooldown test = new KillLogic.Cooldown(KillLogic.TEST_COOLDOWN_MS);
        check(test.tryAcquire(1000), "first test allowed");
        check(!test.tryAcquire(2000), "test blocked within 3 s");
        check(test.remaining(2000) == 2000, "remaining 2 s");
        check(!test.tryAcquire(3999), "blocked at 2.999 s");
        check(test.tryAcquire(4000), "allowed after 3 s");
        KillLogic.Cooldown send = new KillLogic.Cooldown(KillLogic.SEND_INTERVAL_MS);
        check(send.tryAcquire(0), "first send");
        check(!send.tryAcquire(1999), "send blocked within 2 s");
        check(send.tryAcquire(2000), "send allowed after 2 s");
        check(send.ready(4000), "ready later");
    }

    private static void prune() {
        KillLogic l = new KillLogic();
        l.recordHit(A, 1, "Steve", true, 0);
        l.recordHit(B, 2, "Alex", true, 5000);
        l.prune(9000);
        check(l.hitCount() == 1, "old hit pruned");
        check(l.recentHit(B, 9000) != null, "fresh hit kept");
        l.clear();
        check(l.hitCount() == 0, "cleared");
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1.0E-3F;
    }

    private static void coffinTimeline() {
        check(KillFxTimeline.COFFIN_TOTAL_MS <= 3_500L, "coffin at most 3.5 s");
        check(near(KillFxTimeline.coffinBurial(0), 1.0F), "coffin starts buried");
        check(near(KillFxTimeline.coffinBurial(KillFxTimeline.COFFIN_RISE_MS), 0.0F), "coffin up after rise");
        float prev = 2.0F;
        for (long t = 0; t <= KillFxTimeline.COFFIN_RISE_MS; t += 50) {
            float b = KillFxTimeline.coffinBurial(t);
            check(b <= prev + 1.0E-6F, "coffin rises monotonically at " + t);
            prev = b;
        }
        check(near(KillFxTimeline.coffinBurial(KillFxTimeline.COFFIN_SINK_START_MS - 1), 0.0F), "coffin stands until sinking");
        check(KillFxTimeline.coffinBurial(2_700) > 0.0F && KillFxTimeline.coffinBurial(2_700) < 1.0F, "coffin sinking");
        check(near(KillFxTimeline.coffinBurial(KillFxTimeline.COFFIN_TOTAL_MS), 1.0F), "coffin buried at end");
        // Deckel: offen, schließt bis zum Klack, federt kurz, dann zu
        check(near(KillFxTimeline.coffinLidDeg(0), KillFxTimeline.COFFIN_LID_OPEN_DEG), "lid open at start");
        check(near(KillFxTimeline.coffinLidDeg(KillFxTimeline.COFFIN_LID_START_MS), KillFxTimeline.COFFIN_LID_OPEN_DEG), "lid open until closing");
        check(KillFxTimeline.coffinLidDeg(1_200) < KillFxTimeline.COFFIN_LID_OPEN_DEG && KillFxTimeline.coffinLidDeg(1_200) > 0.0F, "lid closing");
        check(KillFxTimeline.coffinLidDeg(1_300) - KillFxTimeline.coffinLidDeg(1_350) > KillFxTimeline.coffinLidDeg(1_050) - KillFxTimeline.coffinLidDeg(1_100),
                "lid accelerates (falls shut)");
        check(KillFxTimeline.COFFIN_KLACK_MS >= 1_000L && KillFxTimeline.COFFIN_KLACK_MS <= 1_400L, "klack between 1.0 and 1.4 s");
        check(KillFxTimeline.coffinLidDeg(KillFxTimeline.COFFIN_KLACK_MS + 80) <= 5.0F, "small rebound only");
        check(near(KillFxTimeline.coffinLidDeg(1_700), 0.0F), "lid closed after klack");
        check(near(KillFxTimeline.coffinLidDeg(3_000), 0.0F), "lid stays closed while sinking");
        for (long t = 0; t <= 3_400; t += 25) {
            check(Math.abs(KillFxTimeline.coffinWobbleDeg(t)) <= 4.0F, "wobble small at " + t);
        }
        check(near(KillFxTimeline.coffinWobbleDeg(1_800), 0.0F), "no wobble during pause");
        check(near(KillFxTimeline.coffinMound(100), 0.5F) && near(KillFxTimeline.coffinMound(1_500), 1.0F)
                && near(KillFxTimeline.coffinMound(KillFxTimeline.COFFIN_TOTAL_MS), 0.0F), "mound grows and vanishes");
    }

    private static void anvilTimeline() {
        check(KillFxTimeline.ANVIL_TOTAL_MS <= 3_000L, "anvil at most 3 s");
        check(near(KillFxTimeline.figurePop(0), 0.0F) && near(KillFxTimeline.figurePop(KillFxTimeline.ANVIL_POP_MS), 1.0F), "figure pops in");
        check(KillFxTimeline.figurePop(150) > 1.0F, "pop overshoots a little");
        check(!KillFxTimeline.anvilVisible(300) && KillFxTimeline.anvilVisible(400) && !KillFxTimeline.anvilVisible(KillFxTimeline.ANVIL_TOTAL_MS),
                "anvil visible from 0.4 s");
        check(near(KillFxTimeline.anvilHeight(400), KillFxTimeline.FIGURE_HEIGHT + KillFxTimeline.ANVIL_DROP_HEIGHT), "anvil starts 8 blocks above the head");
        // beschleunigter Fall: in der zweiten Hälfte mehr Strecke als in der ersten
        float h0 = KillFxTimeline.anvilHeight(400), h1 = KillFxTimeline.anvilHeight(750), h2 = KillFxTimeline.anvilHeight(1_099);
        check(h0 - h1 < h1 - h2, "anvil accelerates");
        check(Math.abs(KillFxTimeline.anvilHeight(KillFxTimeline.ANVIL_IMPACT_MS) - KillFxTimeline.FIGURE_HEIGHT) < 0.01F, "impact on the head");
        check(near(KillFxTimeline.squashY(1_000), 1.0F) && near(KillFxTimeline.squashXZ(1_000), 1.0F), "not squashed before impact");
        check(near(KillFxTimeline.squashY(1_300), KillFxTimeline.SQUASH_Y), "squashed flat (y 0.12)");
        check(Math.abs(KillFxTimeline.squashXZ(1_800) - KillFxTimeline.SQUASH_XZ) < 0.01F, "squashed wide (xz 1.35)");
        check(KillFxTimeline.squashAmount(KillFxTimeline.ANVIL_IMPACT_MS + KillFxTimeline.ANVIL_SQUASH_MS) == 1.0F, "squash within 80 ms");
        // Amboss liegt danach auf der platten Figur, mit kleinem Hüpfer
        float rest = KillFxTimeline.anvilRestHeight();
        check(near(rest, KillFxTimeline.FIGURE_HEIGHT * KillFxTimeline.SQUASH_Y), "anvil rests on squashed figure");
        check(KillFxTimeline.anvilHeight(1_280) > rest + 0.1F, "anvil bounces");
        check(KillFxTimeline.anvilHeight(1_280) <= rest + KillFxTimeline.ANVIL_BOUNCE_HEIGHT + 1.0E-4F, "bounce is small");
        check(near(KillFxTimeline.anvilHeight(1_700), rest), "anvil settles");
        // gehalten (~0,8 s), dann versinken beide
        check(KillFxTimeline.ANVIL_SINK_START_MS - KillFxTimeline.ANVIL_IMPACT_MS >= 800L, "held squashed about 0.8 s");
        check(near(KillFxTimeline.anvilSink(1_900), 0.0F) && KillFxTimeline.anvilSink(2_300) > 0.0F
                && near(KillFxTimeline.anvilSink(KillFxTimeline.ANVIL_TOTAL_MS), KillFxTimeline.ANVIL_SINK_DEPTH), "sink at the end");
        check(KillFxTimeline.ANVIL_SINK_DEPTH > KillFxTimeline.anvilRestHeight() + 1.0F, "anvil fully below ground at the end");
        // Pose
        check(near(KillFxTimeline.armRaise(300), 0.0F) && near(KillFxTimeline.armRaise(1_000), 1.0F) && near(KillFxTimeline.armRaise(1_200), 0.0F),
                "arms raised just before impact");
        check(KillFxTimeline.lookUp(800) > 0.9F && near(KillFxTimeline.lookUp(200), 0.0F), "looks up at the anvil");
        // Schatten wächst
        check(KillFxTimeline.shadowRadius(1_000) > KillFxTimeline.shadowRadius(500), "shadow grows");
        check(KillFxTimeline.shadowStrength(1_000) > KillFxTimeline.shadowStrength(500), "shadow darkens");
        check(near(KillFxTimeline.shadowStrength(KillFxTimeline.ANVIL_TOTAL_MS), 0.0F), "shadow gone at end");
    }

    private static void armorFallback() {
        check(KillFxTimeline.useLeatherFallback(false, false, false, false), "no armor -> leather");
        check(!KillFxTimeline.useLeatherFallback(true, false, false, false), "helmet only stays");
        check(!KillFxTimeline.useLeatherFallback(false, true, false, false), "chestplate only stays");
        check(!KillFxTimeline.useLeatherFallback(false, false, true, false), "leggings only stay");
        check(!KillFxTimeline.useLeatherFallback(false, false, false, true), "boots only stay");
        check(!KillFxTimeline.useLeatherFallback(true, true, true, true), "full armor stays");
    }

    private static void expiringCache() {
        ExpiringCache<String> c = new ExpiringCache<>(10_000L, 3);
        c.put("Steve", "iron", 0);
        check("iron".equals(c.get("steve", 5_000)), "name lookup case-insensitive");
        check("iron".equals(c.get("STEVE", 10_000)), "valid at exactly 10 s");
        check(c.get("Steve", 10_001) == null, "expired after 10 s");
        c.put("Steve", "diamond", 9_000);
        check("diamond".equals(c.get("Steve", 15_000)), "refresh renews and replaces");
        check(c.get("Steve", 8_000) == null, "time going backwards is no hit");
        check(c.get("Alex", 0) == null && c.get(null, 0) == null, "unknown / null");
        c.put("A", "1", 100);
        c.put("B", "2", 200);
        c.put("C", "3", 300); // voll (Steve, A, B): der älteste Eintrag (A, Zeit 100) fliegt raus
        check(c.size() == 3, "max size kept");
        check(c.get("A", 400) == null && "3".equals(c.get("C", 400)), "oldest evicted");
        c.prune(20_000);
        check(c.size() == 0, "pruned");
        c.put("", "x", 0);
        c.put("X", null, 0);
        check(c.size() == 0, "empty key / null value ignored");
    }

    // ==========================================
    // Thanos Snap
    // ==========================================

    private static void snapTimeline() {
        check(KillFxTimeline.SNAP_TOTAL_MS <= 4_500L, "snap at most 4.5 s");
        long travel = KillFxTimeline.SNAP_END_MS - KillFxTimeline.SNAP_START_MS;
        check(travel >= 2_000L && travel <= 2_500L, "boundary travels feet to head in 2-2.5 s");
        check(near(KillFxTimeline.snapBoundary(0), KillFxTimeline.SNAP_BOUNDARY_FROM), "boundary starts below the feet");
        check(near(KillFxTimeline.snapBoundary(KillFxTimeline.SNAP_END_MS), KillFxTimeline.SNAP_BOUNDARY_TO), "boundary above the head at the end");
        float prev = -99.0F;
        for (long t = 0; t <= KillFxTimeline.SNAP_TOTAL_MS; t += 10) {
            float b = KillFxTimeline.snapBoundary(t);
            check(b >= prev - 1.0E-6F, "boundary monotonic (feet to head) at " + t);
            prev = b;
        }
        check(KillFxTimeline.snapBoundary(1_400) > 0.4F && KillFxTimeline.snapBoundary(1_400) < 1.5F, "boundary mid-body halfway");
        // Umkehrung: Zerfallszeitpunkt passt zur Grenze
        for (float thr = -0.2F; thr <= 2.2F; thr += 0.05F) {
            long birth = KillFxTimeline.snapBirthMs(thr);
            check(KillFxTimeline.snapBoundary(birth) >= thr - 1.0E-4F, "boundary reached at birth " + thr);
            if (birth > KillFxTimeline.SNAP_START_MS + 1) {
                check(KillFxTimeline.snapBoundary(birth - 2) < thr, "not reached before birth " + thr);
            }
        }
        // Schwelle bleibt im Bereich der Grenze (nichts zerfällt vor dem Start, alles bis zum Ende)
        check(KillFxTimeline.snapThreshold(0.0F, -0.5F, 1.0F) > KillFxTimeline.SNAP_BOUNDARY_FROM, "lowest threshold above start");
        check(KillFxTimeline.snapThreshold(1.95F, 0.5F, -1.0F) < KillFxTimeline.SNAP_BOUNDARY_TO, "highest threshold below end");
        // Flocken
        long seed = 12345L;
        float life = KillFxTimeline.snapFlakeLife(seed);
        check(life >= KillFxTimeline.SNAP_FLAKE_LIFE_MIN && life <= KillFxTimeline.SNAP_FLAKE_LIFE_MAX, "flake life range");
        check(KillFxTimeline.SNAP_END_MS + (long) (KillFxTimeline.SNAP_FLAKE_LIFE_MAX * 1000) <= KillFxTimeline.SNAP_TOTAL_MS,
                "last flake gone before the effect ends (nothing left)");
        check(near(KillFxTimeline.snapFlakeAlpha(0.0F, life), 1.0F) && near(KillFxTimeline.snapFlakeAlpha(life, life), 0.0F), "flake fades out");
        check(KillFxTimeline.snapFlakeAlpha(life * 0.9F, life) < KillFxTimeline.snapFlakeAlpha(life * 0.6F, life), "flake fading");
        check(near(KillFxTimeline.snapFlakeAsh(0.0F), 0.0F) && near(KillFxTimeline.snapFlakeAsh(0.6F), 1.0F), "flake turns to ash");
        float[] o = new float[3];
        for (long sd = 1; sd < 200; sd += 7) {
            float l = KillFxTimeline.snapFlakeLife(sd);
            float prevAlong = -1.0F;
            for (float a = 0.0F; a <= l; a += 0.05F) {
                KillFxTimeline.snapFlakeOffset(a, sd, o);
                check(o[0] >= prevAlong - 1.0E-6F, "flake blows downwind monotonically");
                prevAlong = o[0];
                check(Math.abs(o[2]) < 0.4F, "swirl stays small");
            }
            KillFxTimeline.snapFlakeOffset(l, sd, o);
            check(o[0] > 0.8F, "flake blown away (" + o[0] + ")");
            check(o[1] > 0.0F, "flake rises slightly");
            check(o[1] < o[0], "flake mostly sideways, not straight up");
        }
        check(near(KillFxTimeline.snapSlump(0), 0.0F) && KillFxTimeline.snapSlump(2_000) > 0.5F && KillFxTimeline.snapSlump(5_000) <= 1.0F, "slump");
        check(KillFxTimeline.unit(1L, 0) != KillFxTimeline.unit(2L, 0), "unit hash varies");
    }

    private static void snapMesh() {
        KillFigureMesh wide = new KillFigureMesh();
        wide.addSkin(0, false, null);
        // Grundschicht + zweite Schicht: Kopf 384, Körper 352, je Arm/Bein 224 -> 1632 je Schicht
        check(wide.size() == 3264, "wide skin texel count " + wide.size());
        KillFigureMesh slim = new KillFigureMesh();
        slim.addSkin(0, true, null);
        check(slim.size() == 3264 - 4 * 32, "slim skin texel count " + slim.size());
        // Maske: unsichtbare Texel entfallen
        KillFigureMesh masked = new KillFigureMesh();
        masked.addSkin(0, false, (x, y) -> y < 16 && x < 32);
        check(masked.size() == 384, "mask keeps only the head base layer (" + masked.size() + ")");

        // Geometrie: Füße am Boden, Hutschicht oben; Gesicht vorne und nicht gespiegelt
        float[][] mats = new float[KillFigureMesh.PARTS][12];
        float[][] rest = KillFigureMesh.restPose();
        for (int p = 0; p < KillFigureMesh.PARTS; p++) KillFigureMesh.partMatrix(p, rest[p], 0.0F, mats[p]);
        float[] c = new float[3], n = new float[3];
        float minH = 99, maxH = -99;
        int face = -1;
        for (int i = 0; i < wide.size(); i++) {
            wide.centerOf(i, mats[wide.part(i)], c);
            minH = Math.min(minH, c[1]);
            maxH = Math.max(maxH, c[1]);
            float u = (wide.u(i, 0) + wide.u(i, 2)) * 0.5F * 64.0F, v = (wide.v(i, 0) + wide.v(i, 2)) * 0.5F * 64.0F;
            if (Math.abs(u - 8.5F) < 0.01F && Math.abs(v - 8.5F) < 0.01F) face = i;
        }
        check(minH > -0.02F && minH < 0.05F, "feet on the ground (pants layer 0.25 px below) (" + minH + ")");
        check(maxH > 1.85F && maxH < 1.95F, "head top ~1.9 blocks (" + maxH + ")");
        check(face >= 0 && wide.part(face) == KillFigureMesh.HEAD, "face texel found on the head");
        wide.centerOf(face, mats[KillFigureMesh.HEAD], c);
        wide.normalOf(face, mats[KillFigureMesh.HEAD], n);
        check(Math.abs(c[1] - 1.8466F) < 0.01F, "top face row at eye height (" + c[1] + ")");
        check(n[2] > 0.99F, "face looks south (+z) with bodyYaw 0");
        check(Math.abs(c[0] + 0.205F) < 0.01F && Math.abs(c[2] - 0.234F) < 0.01F,
                "texture column 8 on the viewer's left (not mirrored): " + c[0] + "/" + c[2]);

        // Rüstung: eigene Textur-Ebene, etwas außerhalb des Körpers
        KillFigureMesh armored = new KillFigureMesh();
        armored.addSkin(0, false, null);
        int before = armored.size();
        armored.addArmor(1, 1, null);
        armored.addArmor(2, 3, null);
        check(armored.size() > before, "armor texels added");
        boolean slotsOk = true;
        for (int i = before; i < armored.size(); i++) slotsOk &= armored.slot(i) == 1 || armored.slot(i) == 2;
        check(slotsOk, "armor texels on their own texture slots");

        // Zerfall
        float windX = 1.0F, windZ = 0.0F;
        armored.prepare(rest, 0.0F, windX, windZ, 0.6F);
        int flakes = armored.flakeCount();
        check(flakes > 300 && flakes <= KillFxTimeline.SNAP_MAX_FLAKES, "flake count bounded (" + flakes + ")");
        int prevDissolved = -1;
        for (long t = 0; t <= KillFxTimeline.SNAP_TOTAL_MS; t += 25) {
            int dissolved = 0;
            for (int i = 0; i < armored.size(); i++) if (armored.dissolved(i, t)) dissolved++;
            check(dissolved >= prevDissolved, "dissolve monotonic at " + t);
            if (t <= KillFxTimeline.SNAP_START_MS) check(dissolved == 0, "nothing dissolved before the start (" + t + ")");
            if (t >= KillFxTimeline.SNAP_END_MS) check(dissolved == armored.size(), "fully dissolved at the end (" + t + ")");
            prevDissolved = dissolved;
        }
        long maxBottom = 0, minTop = Long.MAX_VALUE;
        float bandMin = 99, bandMax = -99;
        float leeSum = 0, windSum = 0;
        int leeN = 0, windN = 0;
        for (int i = 0; i < armored.size(); i++) {
            float hgt = armored.height(i);
            if (hgt < 0.3F) maxBottom = Math.max(maxBottom, armored.birthMs(i));
            if (hgt > 1.6F) minTop = Math.min(minTop, armored.birthMs(i));
            check(armored.birthMs(i) >= KillFxTimeline.SNAP_START_MS && armored.birthMs(i) <= KillFxTimeline.SNAP_END_MS, "birth in range");
            check(armored.dissolved(i, armored.birthMs(i)), "dissolved at its birth time");
            if (hgt > 0.95F && hgt < 1.0F) {
                float d = armored.threshold(i) - hgt;
                bandMin = Math.min(bandMin, d);
                bandMax = Math.max(bandMax, d);
                armored.centerOf(i, mats[armored.part(i)], c);
                if (c[0] > 0.1F) {
                    leeSum += d;
                    leeN++;
                } else if (c[0] < -0.1F) {
                    windSum += d;
                    windN++;
                }
            }
        }
        check(maxBottom < minTop, "feet dissolve before the head (" + maxBottom + " < " + minTop + ")");
        check(bandMax - bandMin > 0.1F, "frayed edge, not a clean cut (" + (bandMax - bandMin) + ")");
        check(bandMax - bandMin <= KillFxTimeline.SNAP_FRAY + 2 * KillFxTimeline.SNAP_WIND_LEAD + 0.01F, "fray bounded");
        check(leeN > 0 && windN > 0 && leeSum / leeN < windSum / windN, "downwind side crumbles a little earlier");
    }

    // ==========================================
    // Hell Gate
    // ==========================================

    private static void hellGateTimeline() {
        check(KillFxTimeline.HELLGATE_TOTAL_MS >= 3_000L && KillFxTimeline.HELLGATE_TOTAL_MS <= 4_000L, "hell gate 3-4 s");
        check(KillFxTimeline.HELLGATE_HANDS >= 3 && KillFxTimeline.HELLGATE_HANDS <= 5, "3-5 hands");
        // Tor: baut sich in ~0,5 s auf, steht, knallt zu
        check(near(KillFxTimeline.gateRadius(0), 0.0F), "gate closed at start");
        check(KillFxTimeline.gateRadius(250) > 0.5F * KillFxTimeline.HELLGATE_RADIUS, "gate opening");
        check(KillFxTimeline.gateRadius(KillFxTimeline.HELLGATE_OPEN_MS) >= 0.95F * KillFxTimeline.HELLGATE_RADIUS, "gate open after 0.5 s");
        for (long t = KillFxTimeline.HELLGATE_OPEN_MS; t < KillFxTimeline.HELLGATE_CLOSE_START_MS; t += 50) {
            check(Math.abs(KillFxTimeline.gateRadius(t) - KillFxTimeline.HELLGATE_RADIUS) < 0.05F, "gate stays open at " + t);
        }
        check(KillFxTimeline.gateRadius(2_850) < 0.6F * KillFxTimeline.HELLGATE_RADIUS, "gate closing fast");
        check(near(KillFxTimeline.gateRadius(KillFxTimeline.HELLGATE_BANG_MS), 0.0F) && near(KillFxTimeline.gateRadius(3_500), 0.0F), "gate shut after bang");
        check(KillFxTimeline.HELLGATE_BANG_MS - KillFxTimeline.HELLGATE_CLOSE_START_MS <= 300L, "closing is quick");
        check(near(KillFxTimeline.gateGlow(0), 0.0F) && KillFxTimeline.gateGlow(1_000) > 0.7F, "runes glow");
        check(near(KillFxTimeline.bangFlash(KillFxTimeline.HELLGATE_BANG_MS - 1), 0.0F) && near(KillFxTimeline.bangFlash(KillFxTimeline.HELLGATE_BANG_MS), 1.0F)
                && near(KillFxTimeline.bangFlash(KillFxTimeline.HELLGATE_BANG_MS + 300), 0.0F), "flash at the bang");
        check(near(KillFxTimeline.scorchAlpha(KillFxTimeline.HELLGATE_BANG_MS - 1), 0.0F) && KillFxTimeline.scorchAlpha(KillFxTimeline.HELLGATE_BANG_MS + 100) > 0.5F
                && near(KillFxTimeline.scorchAlpha(KillFxTimeline.HELLGATE_TOTAL_MS), 0.0F), "scorch mark fades");

        // Versinken: monoton, erst nach dem Zupacken, am Ende ganz unter dem Boden
        float prev = -1.0F;
        for (long t = 0; t <= KillFxTimeline.HELLGATE_TOTAL_MS; t += 10) {
            float s = KillFxTimeline.hellSink(t);
            check(s >= prev - 1.0E-6F, "sink monotonic at " + t);
            prev = s;
        }
        check(near(KillFxTimeline.hellSink(KillFxTimeline.HELLGATE_PULL_START_MS), 0.0F), "no sinking before the pull");
        check(KillFxTimeline.hellSink(KillFxTimeline.HELLGATE_PULL_END_MS) >= KillFxTimeline.FIGURE_HEIGHT + 0.3F, "figure fully under the ground");
        check(KillFxTimeline.HELLGATE_PULL_END_MS <= KillFxTimeline.HELLGATE_CLOSE_START_MS, "gate closes after the figure is gone");
        check(near(KillFxTimeline.hellStruggle(KillFxTimeline.HELLGATE_PULL_END_MS), 0.0F) && near(KillFxTimeline.hellStruggle(1_000), 1.0F), "struggle");
        for (long t = 0; t <= KillFxTimeline.HELLGATE_TOTAL_MS; t += 20) check(Math.abs(KillFxTimeline.hellTiltDeg(t)) <= 6.01F, "tilt small");

        // Hände: kommen aus dem Schlund, erreichen die Figur, halten sie fest und verschwinden mit ihr
        float[] root = new float[3], hand = new float[3], grab = new float[3], elbow = new float[3];
        for (float base : new float[]{0.0F, 1.3F, 4.0F}) {
            for (int i = 0; i < KillFxTimeline.HELLGATE_HANDS; i++) {
                long start = KillFxTimeline.handStartMs(i), grabMs = KillFxTimeline.handGrabMs(i);
                check(start >= KillFxTimeline.HELLGATE_OPEN_MS - 100L, "hands after the gate opens");
                check(grabMs <= KillFxTimeline.HELLGATE_PULL_START_MS, "all hands grabbed before the pull");
                check(near(KillFxTimeline.handReach(i, start), 0.0F) && near(KillFxTimeline.handReach(i, grabMs), 1.0F), "reach 0 -> 1");
                KillFxTimeline.hellHandPos(i, start, base, hand);
                check(hand[1] < 0.0F, "hand starts below the ground");
                check(Math.hypot(hand[0], hand[2]) < KillFxTimeline.HELLGATE_RADIUS, "hand starts inside the gate");
                KillFxTimeline.hellHandPos(i, grabMs, base, hand);
                KillFxTimeline.hellGrabPoint(i, grabMs, base, grab);
                float dist = (float) Math.sqrt((hand[0] - grab[0]) * (hand[0] - grab[0]) + (hand[1] - grab[1]) * (hand[1] - grab[1])
                        + (hand[2] - grab[2]) * (hand[2] - grab[2]));
                check(dist < 1.0E-4F, "hand reaches the figure (" + dist + ")");
                check(Math.hypot(grab[0], grab[2]) <= 0.3F && grab[1] > 0.0F && grab[1] < KillFxTimeline.FIGURE_HEIGHT, "grab point on the body");
                check(KillFxTimeline.handCurl(i, grabMs + KillFxTimeline.HELLGATE_GRIP_MS) >= 0.99F && KillFxTimeline.handCurl(i, start + 100) < 0.2F,
                        "fingers close on grab");
                for (long t = grabMs; t <= KillFxTimeline.HELLGATE_PULL_END_MS; t += 50) {
                    KillFxTimeline.hellHandPos(i, t, base, hand);
                    KillFxTimeline.hellGrabPoint(i, t, base, grab);
                    check(near(hand[0], grab[0]) && near(hand[1], grab[1]) && near(hand[2], grab[2]), "hand holds on while pulling");
                }
                KillFxTimeline.hellHandPos(i, KillFxTimeline.HELLGATE_PULL_END_MS, base, hand);
                check(hand[1] < -0.5F, "hand dragged under the ground with the figure");
                // Der Arm kommt immer innerhalb des Tors aus dem Boden
                KillFxTimeline.hellHandRoot(i, base, root);
                check(root[1] < -1.0F, "shoulder deep in the pit");
                for (long t = start; t < KillFxTimeline.HELLGATE_CLOSE_START_MS; t += 20) {
                    KillFxTimeline.hellHandPos(i, t, base, hand);
                    KillFxTimeline.hellElbow(i, base, root, hand, elbow);
                    float limit = KillFxTimeline.gateRadius(t) * 0.9F;
                    checkCrossing(root, elbow, limit, "upper arm " + i + " at " + t);
                    checkCrossing(elbow, hand, limit, "forearm " + i + " at " + t);
                    float upper = (float) Math.sqrt(sq(elbow[0] - root[0]) + sq(elbow[1] - root[1]) + sq(elbow[2] - root[2]));
                    check(upper < 2.5F, "arm length plausible");
                }
            }
        }
    }

    private static float sq(float v) {
        return v * v;
    }

    /** Schneidet die Strecke a-b den Boden (y = 0), muss der Schnittpunkt innerhalb des Radius liegen. */
    private static void checkCrossing(float[] a, float[] b, float limit, String what) {
        if ((a[1] > 0.0F) == (b[1] > 0.0F)) return;
        float t = a[1] / (a[1] - b[1]);
        float x = a[0] + (b[0] - a[0]) * t, z = a[2] + (b[2] - a[2]) * t;
        check(Math.hypot(x, z) < limit, what + " leaves the ground inside the gate (" + Math.hypot(x, z) + " < " + limit + ")");
    }

    /** Kill-Effekte zeigen nie Text: weder Bildschirm-Teil noch Chat/Action-Bar/Text-Zeichnen in den neuen Effekten. */
    private static void noScreenText() {
        check(!KillEffectType.SNAP.hasScreenPart && !KillEffectType.HELLGATE.hasScreenPart, "snap/hellgate world only");
        String[] files = {"KillSnapFx.java", "KillHellGateFx.java", "KillFigureMesh.java", "KillFigureTextures.java"};
        String[] forbidden = {"setOverlayMessage", "sendMessage", "addMessage", "inGameHud", "drawText", "textRenderer", "Text.literal",
                "NotificationManager", "showFloatingItem", "KillEffectHud"};
        for (String f : files) {
            java.nio.file.Path p = java.nio.file.Paths.get("src/main/java/com/bame/client/killfx", f);
            String src;
            try {
                src = new String(java.nio.file.Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException e) {
                throw new AssertionError("cannot read " + p.toAbsolutePath());
            }
            for (String bad : forbidden) check(!src.contains(bad), f + " must not use " + bad);
        }
    }
}
