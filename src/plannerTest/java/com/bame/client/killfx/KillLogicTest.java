package com.bame.client.killfx;

import java.util.UUID;

/**
 * Prüft ohne Minecraft die Kill-Logik aus {@link KillLogic}: 8-s-Trefferfenster, Abklingzeit pro Opfer, Mob-Schalter,
 * Totem-Ausschluss, Chat-Regeln, Fernkampf-Kriterium, Opfernamen und die Abklingzeiten für Testen und Senden.
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
        System.out.println("KILLFX TESTS PASSED (" + checks + " checks)");
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static final UUID A = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID B = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private static void effectTypes() {
        check(KillEffectType.values().length == 5, "5 effects");
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
        String[] ids = {"KILL_TOTEM", "KILL_LIGHTNING", "KILL_GRAVE", "KILL_COFFIN", "KILL_ANVIL"};
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
}
