package com.bame.client.emotes;

import com.bame.client.cosmetics.CaeserPlayerSyncManager;
import com.bame.client.module.EmoteModule;
import com.bame.client.shop.CaeserApi;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Koordiniert das High-Five. Wird einmal pro Client-Tick aktualisiert ({@link #tick}); {@link EmoteAnimator} fragt nur
 * ab ({@link #view}). Jeder Client wendet dieselben Regeln ({@link HighFiveLogic}) auf dieselben Daten an (Emote-
 * Startzeiten vom Sync-Server, Positionen von Minecraft). Es gibt keine eigenen Netzwerknachrichten.
 * <p>
 * Zwei Wege zu einem Paar:
 * <ul>
 *   <li><b>Einschlagen per Taste</b> (Hauptweg): A spielt HIGH_FIVE und wartet. B (braucht kein High-Five) steht in der
 *       Nähe und schaut zu A und sieht „[F] Einschlagen mit A“. Drückt B die Taste, spielt B das versteckte
 *       Gratis-Emote HIGH_FIVE_ACCEPT; jeder Client paart es mit dem nächsten wartenden High-Five in B's Blickrichtung
 *       (gleicher Abstand: kleinere UUID).</li>
 *   <li><b>Zwei Wartende</b>, die sich gegenüberstehen und anschauen, klatschen weiterhin automatisch.</li>
 * </ul>
 * Ablauf eines Paars: WARTEN → AUSHOLEN (0,25 s vor {@code clapTime}) → KLATSCH (Partikel + Sound am Treffpunkt) →
 * NACHSCHWINGEN (0,5 s) → ENDE bei {@code clapTime + 0,8 s}. Ein erkanntes Paar bleibt bis zum Ende bestehen.
 * Die Bewegung innerhalb dieser Phasen (Ausholen, Schwung, Rückprall, Nachschwung) beschreibt {@link HighFiveMotion}.
 */
public final class HighFiveCoordinator {

    private HighFiveCoordinator() {}

    /**
     * Abfrage für den Renderer. {@code lockedAt}: seit wann dieser Client das Paar kennt (lokale Zeit; nur zum weichen
     * Einblenden, wenn das Paar erst kurz vor dem Klatsch erkannt wurde). {@code partnerX/Y/Z}: Füße des Partners.
     */
    public record View(String partnerId, long clapTime, double targetX, double targetY, double targetZ,
                       double partnerDistance, long lockedAt, double partnerX, double partnerY, double partnerZ) {}

    /** Hinweis für den lokalen Spieler: bei wem er gerade einschlagen kann. */
    public record Prompt(String targetId, String targetName) {}

    private static final class Lock {
        final String a, b;
        final long startA, startB;
        final EmoteType emoteA, emoteB;
        final long clapTime;
        /** Seit wann dieser Client das Paar kennt (lokale Zeit). */
        final long lockedAt = System.currentTimeMillis();
        double tx, ty, tz;
        double distance;
        /** Füße von A und B. */
        double ax, ay, az, bx, by, bz;
        String nameA, nameB;
        boolean clapped;

        Lock(HighFiveLogic.Player pa, EmoteType emoteA, HighFiveLogic.Player pb, EmoteType emoteB, long clapTime) {
            this.a = pa.id();
            this.b = pb.id();
            this.startA = pa.startMs();
            this.startB = pb.startMs();
            this.emoteA = emoteA;
            this.emoteB = emoteB;
            this.clapTime = clapTime;
            update(pa, pb);
        }

        void update(HighFiveLogic.Player pa, HighFiveLogic.Player pb) {
            double[] t = HighFiveLogic.target(pa, pb);
            tx = t[0];
            ty = t[1];
            tz = t[2];
            distance = HighFiveLogic.horizontalDistance(pa, pb);
            ax = pa.x();
            ay = pa.y();
            az = pa.z();
            bx = pb.x();
            by = pb.y();
            bz = pb.z();
        }

        /**
         * Gemeinsamer Zufalls-Startwert für Tonhöhen/Partikel: aus IDs und den Serverzeiten der beiden Emote-Starts
         * (fremde aus der emote-Nachricht, das eigene aus deren Echo vom Sync-Server; überall dieselbe Zahl). Vorher
         * wurden die lokal umgerechneten Startzeiten verwendet, die je Client um den geschätzten Uhrenversatz abwichen.
         * Weiter auf 100 ms gerundet wegen der Rückfälle ohne exakte Zeit (Schätzung bis zum Echo, LAN). Wird erst beim
         * Klatsch berechnet; dann ist das Echo normalerweise schon da.
         */
        long seed() {
            long sa = CaeserPlayerSyncManager.sharedStartTime(a, emoteA, startA);
            long sb = CaeserPlayerSyncManager.sharedStartTime(b, emoteB, startB);
            return (a + b).hashCode() * 31L + (sa / 100L) * 17L + (sb / 100L);
        }

        boolean has(String id) {
            return a.equals(id) || b.equals(id);
        }

        String partnerOf(String id) {
            return a.equals(id) ? b : a;
        }
    }

    /** Erfasster Spieler in der aktuellen Welt (für diesen Tick) mit High-Five oder Einschlagen. */
    private record Seen(PlayerEntity entity, HighFiveLogic.Player player, EmoteType emote, boolean local) {}

    private static final List<Lock> LOCKS = new ArrayList<>();
    /** Beendetes High-Five/Einschlagen: Startzeit des Emotes, das nicht mehr gezeigt bzw. gepaart wird, und Ende. */
    private record Finished(long startMs, long endedAt) {}

    /** Beendete High-Fives pro UUID; so lange gemerkt, bis ein fremdes Emote sicher abgelaufen ist (10 s). */
    private static final Map<String, Finished> FINISHED = new HashMap<>();
    private static final long FINISHED_KEEP_MS = 15_000L;
    /** Zuletzt gezeigter Warte-Hinweis pro Spieler (Sekunde seit Emote-Start). */
    private static final Map<String, Long> LAST_HINT = new HashMap<>();
    private static ClientWorld lastWorld;
    /** Spieler aus dem letzten Tick (der Tastendruck wird vor dem nächsten Tick ausgewertet). */
    private static Map<String, Seen> lastSeen = new TreeMap<>();
    private static Prompt prompt;

    private static KeyBinding acceptKey;

    /** Blickziel pro Spieler (Partner bzw. beim Warten der nächste Spieler in der Nähe), pro Tick geglättet. */
    private static final class Look {
        double px, py, pz, pw;
        double x, y, z, w;
    }

    private static final Map<String, Look> LOOKS = new HashMap<>();
    private static long lastTickNanos = System.nanoTime();
    /** Wartende schauen zum nächsten Spieler in diesem Umkreis (Blöcke). */
    private static final double LOOK_RANGE = 4.0;

    /** Klatsch etwas vor dem Tick auslösen, damit Klang und Partikel im Mittel genau beim Kontakt kommen (ms). */
    private static final long CLAP_EARLY_MS = 25L;
    /** „Belohnungs“-Glitzern (Klang + Funken) so lange nach dem Klatsch (ms). */
    private static final long REWARD_DELAY_MS = 130L;
    /** Partikel des Klatsch-Rings (in der Ebene senkrecht zur Linie zwischen den Spielern). */
    private static final int CLAP_RING = 14;

    private record Pending(long at, double x, double y, double z, long seed) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    // ------------------------------------------------------------------
    // Taste
    // ------------------------------------------------------------------

    /**
     * Eigene Taste „High-Five annehmen“ (Standard F, Kategorie Caeser Client), in den Steuerungs-Optionen änderbar.
     * Liegt sie wie im Standard auf derselben Taste wie „Gegenstand mit Zweithand tauschen“, wird nur der Tastendruck,
     * der wirklich einschlägt, dem Hand-Tausch weggenommen (siehe {@link #handleAcceptKey}).
     */
    public static void registerKeyBinding() {
        if (acceptKey != null) return;
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("bameclient", "caeser"));
        acceptKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.bameclient.high_five_accept", GLFW.GLFW_KEY_F, category));
    }

    /** Anzeigetext der gebundenen Taste, z. B. „F“. */
    public static Text acceptKeyText() {
        return acceptKey != null ? acceptKey.getBoundKeyLocalizedText() : Text.literal("F");
    }

    /**
     * Am Anfang von {@code MinecraftClient.handleInputEvents} (Mixin), also vor der Vanilla-Auswertung von „Hände
     * tauschen“. Nur wenn wirklich eingeschlagen wird und beide Aktionen auf derselben Taste liegen, werden die
     * Hand-Tausch-Drücke dieses Ticks clientseitig verbraucht; sonst bleibt die Taste völlig unverändert.
     */
    public static void handleAcceptKey(MinecraftClient client) {
        if (acceptKey == null) return;
        boolean pressed = false;
        while (acceptKey.wasPressed()) pressed = true;
        if (!pressed || prompt == null || client.player == null) return;
        if (!tryAcceptLocal(client)) return;
        if (client.options != null && acceptKey.equals(client.options.swapHandsKey)) {
            while (client.options.swapHandsKey.wasPressed()) {
                // verbraucht: Vanilla schickt für diesen Tastendruck kein SWAP_ITEM_WITH_OFFHAND
            }
        }
    }

    /** Aktueller Hinweis „[Taste] Einschlagen mit …“ oder {@code null}. */
    public static Prompt prompt() {
        return prompt;
    }

    // ------------------------------------------------------------------
    // Abfragen
    // ------------------------------------------------------------------

    public static View view(PlayerEntityRenderState state) {
        String id = CaeserApi.uuid32(CaeserPlayerSyncManager.getRenderStateUuid(state));
        return id != null ? view(id) : null;
    }

    public static View view(String uuid32) {
        if (uuid32 == null) return null;
        for (Lock lock : LOCKS) {
            if (lock.has(uuid32)) {
                boolean isA = lock.a.equals(uuid32);
                return new View(lock.partnerOf(uuid32), lock.clapTime, lock.tx, lock.ty, lock.tz, lock.distance,
                        lock.lockedAt, isA ? lock.bx : lock.ax, isA ? lock.by : lock.ay, isA ? lock.bz : lock.az);
            }
        }
        return null;
    }

    /**
     * Blickziel für den Kopf: {x, y, z (Augen), Gewicht 0..1}, zwischen zwei Ticks interpoliert, oder {@code null}.
     */
    public static double[] look(PlayerEntityRenderState state) {
        String id = CaeserApi.uuid32(CaeserPlayerSyncManager.getRenderStateUuid(state));
        Look l = id != null ? LOOKS.get(id) : null;
        if (l == null) return null;
        double f = Math.max(0.0, Math.min(1.0, (System.nanoTime() - lastTickNanos) / 50_000_000.0));
        return new double[]{l.px + (l.x - l.px) * f, l.py + (l.y - l.py) * f, l.pz + (l.z - l.pz) * f,
                l.pw + (l.w - l.pw) * f};
    }

    /** Ist dieses High-Five bzw. Einschlagen (Spieler + Startzeit) schon beendet? Dann wird es nicht mehr gezeigt. */
    public static boolean isFinished(String uuid32, long startMs) {
        if (uuid32 == null) return false;
        Finished finished = FINISHED.get(uuid32);
        return finished != null && finished.startMs() == startMs;
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world != lastWorld) {
            LOCKS.clear();
            FINISHED.clear();
            LAST_HINT.clear();
            LOOKS.clear();
            PENDING.clear();
            lastSeen = new TreeMap<>();
            prompt = null;
            lastWorld = world;
        }
        if (world == null || client.player == null) {
            prompt = null;
            return;
        }

        long now = System.currentTimeMillis();

        // 1. Alle Spieler mit High-Five oder Einschlagen, sortiert nach UUID (keine Abhängigkeit von der Map-Reihenfolge)
        Map<String, Seen> seen = new TreeMap<>();
        for (PlayerEntity p : world.getPlayers()) {
            String id = CaeserApi.uuid32(p.getUuid());
            if (id == null) continue;
            boolean local = p == client.player;
            EmoteAnimator.ActiveEmote active = EmoteAnimator.resolveActiveEmote(p.getUuid(), local);
            if (active == null) continue;
            if (active.emote() != EmoteType.HIGH_FIVE && active.emote() != EmoteType.HIGH_FIVE_ACCEPT) continue;
            if (isFinished(id, active.startMs())) continue;
            seen.put(id, new Seen(p, player(p, id, active.startMs()), active.emote(), local));
        }
        lastSeen = seen;

        // 2. Gesperrte Paare fortschreiben
        for (Iterator<Lock> it = LOCKS.iterator(); it.hasNext(); ) {
            Lock lock = it.next();
            Seen sa = seen.get(lock.a);
            Seen sb = seen.get(lock.b);
            boolean aOk = sa != null && sa.emote() == lock.emoteA && sa.player().startMs() == lock.startA;
            boolean bOk = sb != null && sb.emote() == lock.emoteB && sb.player().startMs() == lock.startB;
            HighFiveLogic.Phase phase = HighFiveLogic.phase(lock.clapTime, now);

            if (phase == HighFiveLogic.Phase.ENDED) {
                finish(client, lock, sa, sb, now);
                it.remove();
                continue;
            }
            // Vor dem Klatschen: Spieler weg oder Emote beendet → Paar verwerfen. Danach läuft es bis zum Ende.
            if (!lock.clapped && (!aOk || !bOk)) {
                it.remove();
                continue;
            }
            if (aOk && bOk) {
                lock.update(sa.player(), sb.player());
            }
            if (sa != null) lock.nameA = sa.entity().getName().getString();
            if (sb != null) lock.nameB = sb.entity().getName().getString();

            if (!lock.clapped && now >= lock.clapTime - CLAP_EARLY_MS) {
                lock.clapped = true;
                spawnClap(client, world, lock, sa, sb, now);
            }
        }
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now < p.at()) continue;
            it.remove();
            spawnReward(world, p);
        }

        // 3. Fremde Einschlagen-Emotes mit ihrem Ziel paaren (das eigene wird schon beim Tastendruck gepaart).
        //    Reihenfolge: wer zuerst eingeschlagen hat, dann UUID – so gewinnt bei zwei Annehmenden überall derselbe.
        List<Seen> accepters = new ArrayList<>();
        for (Seen s : seen.values()) {
            if (s.emote() == EmoteType.HIGH_FIVE_ACCEPT && !s.local() && !isLocked(s.player().id())) accepters.add(s);
        }
        accepters.sort(Comparator.comparingLong((Seen s) -> s.player().startMs()).thenComparing(s -> s.player().id()));
        for (Seen acc : accepters) {
            long acceptStart = acc.player().startMs();
            if (now - acceptStart > HighFiveLogic.ACCEPT_MATCH_WINDOW_MS) continue; // kein Ziel gefunden: nichts zeigen
            HighFiveLogic.Player target =
                    HighFiveLogic.findAcceptTarget(acc.player(), waitingCandidates(seen), acceptStart, true);
            if (target == null) continue;
            Lock lock = new Lock(target, EmoteType.HIGH_FIVE, acc.player(), EmoteType.HIGH_FIVE_ACCEPT,
                    HighFiveLogic.acceptClapTime(acceptStart, now));
            lock.nameA = seen.get(target.id()).entity().getName().getString();
            lock.nameB = acc.entity().getName().getString();
            LOCKS.add(lock);
        }

        // 4. Zwei Wartende, die sich gegenüberstehen und anschauen, klatschen weiterhin automatisch
        for (HighFiveLogic.Pair pair : HighFiveLogic.findPairs(waitingCandidates(seen))) {
            long clap = HighFiveLogic.clapTime(pair.a().startMs(), pair.b().startMs(), now);
            Lock lock = new Lock(pair.a(), EmoteType.HIGH_FIVE, pair.b(), EmoteType.HIGH_FIVE, clap);
            lock.nameA = seen.get(lock.a).entity().getName().getString();
            lock.nameB = seen.get(lock.b).entity().getName().getString();
            LOCKS.add(lock);
        }

        // 5. Warte-Hinweis (alle 1 s über dem Kopf, aus der Emote-Zeit berechnet) und Zeitlimits des lokalen Spielers
        for (Seen s : seen.values()) {
            String id = s.player().id();
            Lock lock = lockOf(id);
            if (s.emote() == EmoteType.HIGH_FIVE_ACCEPT) {
                // Eigenes Einschlagen ohne Paar (das Ziel hat vorher aufgehört): still beenden
                if (s.local() && lock == null && EmoteModule.getCurrentEmote() == EmoteType.HIGH_FIVE_ACCEPT) {
                    EmoteModule.stopEmote();
                }
                continue;
            }
            boolean waiting = lock == null || HighFiveLogic.phase(lock.clapTime, now) == HighFiveLogic.Phase.WAITING;
            long second = (now - s.player().startMs()) / 1000L;
            if (waiting && second >= 0 && second * 1000L <= HighFiveLogic.WAIT_MS) {
                Long last = LAST_HINT.get(id);
                if (last == null || last != second) {
                    LAST_HINT.put(id, second);
                    spawnWaitingHint(world, s.entity(), (now - s.player().startMs()) / 1000.0F, second);
                }
            }
            if (s.local() && lock == null && now - s.player().startMs() > HighFiveLogic.WAIT_MS) {
                EmoteModule.stopEmote();
            }
        }
        LAST_HINT.keySet().retainAll(seen.keySet());
        updateLooks(world, seen);

        // 6. Hinweis „[F] Einschlagen mit …“ für den lokalen Spieler
        Seen target = localAcceptTarget(client, seen, now);
        prompt = target != null ? new Prompt(target.player().id(), target.entity().getName().getString()) : null;

        // 7. Aufräumen: beendete Einträge nach Ablauf vergessen (ein neues High-Five hat ohnehin eine neue Startzeit)
        FINISHED.values().removeIf(f -> now - f.endedAt() > FINISHED_KEEP_MS);
    }

    private static HighFiveLogic.Player player(PlayerEntity p, String id, long startMs) {
        return new HighFiveLogic.Player(id, p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getBodyYaw(), startMs);
    }

    /** Wartende High-Five-Spieler ohne Paar (die Startzeit prüft HighFiveLogic). */
    private static List<HighFiveLogic.Player> waitingCandidates(Map<String, Seen> seen) {
        List<HighFiveLogic.Player> out = new ArrayList<>();
        for (Seen s : seen.values()) {
            if (s.emote() != EmoteType.HIGH_FIVE || isLocked(s.player().id())) continue;
            out.add(s.player());
        }
        return out;
    }

    /** Wartender, bei dem der lokale Spieler jetzt einschlagen kann (strenge Regeln), oder {@code null}. */
    private static Seen localAcceptTarget(MinecraftClient client, Map<String, Seen> seen, long now) {
        PlayerEntity self = client.player;
        if (self == null || !EmoteModule.enabled || self.isSpectator() || !self.isAlive() || self.isSleeping()) return null;
        String selfId = CaeserApi.uuid32(self.getUuid());
        if (selfId == null || isLocked(selfId)) return null;
        // Nicht während eines Emotes, bei dem man stillhalten muss (Sitzen, Liegestütze), oder beim Einschlagen selbst
        if (EmoteModule.isEmoting()) {
            EmoteType current = EmoteModule.getCurrentEmote();
            if (current == EmoteType.HIGH_FIVE_ACCEPT || !current.allowsWalking()) return null;
        }
        List<HighFiveLogic.Player> candidates = new ArrayList<>();
        for (HighFiveLogic.Player p : waitingCandidates(seen)) {
            if (p.id().equals(selfId) || !HighFiveLogic.canPrompt(p.startMs(), now)) continue;
            candidates.add(p);
        }
        if (candidates.isEmpty()) return null;
        HighFiveLogic.Player target = HighFiveLogic.findAcceptTarget(player(self, selfId, now), candidates, now, false);
        return target != null ? seen.get(target.id()) : null;
    }

    /**
     * Einschlagen: Ziel erneut streng prüfen, Annehmen-Emote starten (geht über den Sync an alle) und sofort paaren.
     * Ohne gültiges Ziel passiert nichts und es wird nichts gesendet.
     */
    private static boolean tryAcceptLocal(MinecraftClient client) {
        long now = System.currentTimeMillis();
        Seen target = localAcceptTarget(client, lastSeen, now);
        if (target == null) return false;
        String selfId = CaeserApi.uuid32(client.player.getUuid());
        if (EmoteModule.getCurrentEmote() == EmoteType.HIGH_FIVE) {
            // Selbst gerade am Warten: dieses High-Five ist damit beendet und wird nicht mehr gepaart
            FINISHED.put(selfId, new Finished(EmoteModule.getStartTimeMillis(), now));
        }
        long start = EmoteModule.playHighFiveAccept();
        if (start == 0L) return false;
        Lock lock = new Lock(target.player(), EmoteType.HIGH_FIVE, player(client.player, selfId, start),
                EmoteType.HIGH_FIVE_ACCEPT, HighFiveLogic.acceptClapTime(start, now));
        lock.nameA = target.entity().getName().getString();
        lock.nameB = client.player.getName().getString();
        LOCKS.add(lock);
        prompt = null;
        return true;
    }

    private static void finish(MinecraftClient client, Lock lock, Seen sa, Seen sb, long now) {
        FINISHED.put(lock.a, new Finished(lock.startA, now));
        FINISHED.put(lock.b, new Finished(lock.startB, now));
        Seen local = sa != null && sa.local() ? sa : (sb != null && sb.local() ? sb : null);
        if (local == null || client.player == null) return;
        // Nur beenden, wenn noch dasselbe Emote läuft
        long localStart = local == sa ? lock.startA : lock.startB;
        EmoteType localEmote = local == sa ? lock.emoteA : lock.emoteB;
        if (EmoteModule.getCurrentEmote() == localEmote && EmoteModule.getStartTimeMillis() == localStart) {
            EmoteModule.stopEmote();
        }
    }

    private static boolean isLocked(String id) {
        return lockOf(id) != null;
    }

    private static Lock lockOf(String id) {
        for (Lock lock : LOCKS) {
            if (lock.has(id)) return lock;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Blick, Hinweis, Klatsch-Effekte (nur Darstellung, nur lokal)
    // ------------------------------------------------------------------

    /**
     * Blickziele fortschreiben: gepaart → Augen des Partners; wartend → nächster Spieler im Umkreis von 4 Blöcken.
     * Ziel und Gewicht werden pro Tick geglättet, der Renderer interpoliert zwischen den Ticks.
     */
    private static void updateLooks(ClientWorld world, Map<String, Seen> seen) {
        for (Seen s : seen.values()) {
            String id = s.player().id();
            Look l = LOOKS.computeIfAbsent(id, k -> new Look());
            double[] want = null;
            Lock lock = lockOf(id);
            if (lock != null) {
                Seen partner = seen.get(lock.partnerOf(id));
                if (partner != null) {
                    want = eye(partner.entity());
                } else {
                    boolean isA = lock.a.equals(id);
                    want = new double[]{isA ? lock.bx : lock.ax, (isA ? lock.by : lock.ay) + 1.62, isA ? lock.bz : lock.az};
                }
            } else if (s.emote() == EmoteType.HIGH_FIVE) {
                PlayerEntity near = nearestPlayer(world, s.entity());
                if (near != null) want = eye(near);
            }
            l.px = l.x;
            l.py = l.y;
            l.pz = l.z;
            l.pw = l.w;
            if (want != null) {
                if (l.w <= 1.0E-3) {
                    l.x = l.px = want[0];
                    l.y = l.py = want[1];
                    l.z = l.pz = want[2];
                } else {
                    l.x += (want[0] - l.x) * 0.5;
                    l.y += (want[1] - l.y) * 0.5;
                    l.z += (want[2] - l.z) * 0.5;
                }
                l.w = Math.min(1.0, l.w + 0.25);
            } else {
                l.w = Math.max(0.0, l.w - 0.2);
            }
        }
        LOOKS.keySet().retainAll(seen.keySet());
        lastTickNanos = System.nanoTime();
    }

    private static double[] eye(PlayerEntity e) {
        return new double[]{e.getX(), e.getEyeY(), e.getZ()};
    }

    private static PlayerEntity nearestPlayer(ClientWorld world, PlayerEntity self) {
        PlayerEntity best = null;
        double bestSq = LOOK_RANGE * LOOK_RANGE;
        for (PlayerEntity p : world.getPlayers()) {
            if (p == self || p.isSpectator() || !p.isAlive() || p.isInvisible()) continue;
            double sq = p.squaredDistanceTo(self);
            if (sq < bestSq) {
                bestSq = sq;
                best = p;
            }
        }
        return best;
    }

    /**
     * Warte-Hinweis (einmal pro Sekunde): kleines Glitzern direkt über der erhobenen Hand (Position aus derselben
     * Wartepose wie im Renderer), jede zweite Sekunde zusätzlich ein Lichtpunkt über dem Kopf.
     */
    private static void spawnWaitingHint(ClientWorld world, PlayerEntity e, float t, long second) {
        HighFiveMotion.Input in = new HighFiveMotion.Input();
        in.role = HighFiveMotion.Role.WAITER;
        in.t = t;
        HighFiveMotion.Pose pose = new HighFiveMotion.Pose();
        HighFiveMotion.sample(in, pose);
        double[] palm = HighFiveMotion.palmPoint(pose);
        // 2,5 px über der Hand (Modell: +Y nach unten)
        double[] w = HighFiveLogic.modelToWorld(palm[0], palm[1] - 2.5, palm[2], e.getX(), e.getY(), e.getZ(),
                e.getBodyYaw(), e.getScale());
        java.util.Random r = new java.util.Random(e.getUuid().hashCode() * 31L + second);
        world.addParticleClient(ParticleTypes.END_ROD, w[0], w[1], w[2], 0.0, 0.012, 0.0);
        for (int i = 0; i < 2; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            world.addParticleClient(ParticleTypes.ELECTRIC_SPARK,
                    w[0] + Math.cos(a) * 0.08, w[1] + r.nextDouble() * 0.08, w[2] + Math.sin(a) * 0.08,
                    Math.cos(a) * 0.08, 0.12, Math.sin(a) * 0.08);
        }
        if (second % 2L == 0L) {
            world.addParticleClient(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getHeight() + 0.35, e.getZ(),
                    0.0, 0.02, 0.0);
        }
    }

    /**
     * Klatsch am Treffpunkt: heller Blitz, Funkenring in der Ebene senkrecht zur Linie zwischen den Spielern, ein paar
     * aufsteigende Glitzer; geschichteter Klatsch-Sound (Krit + Snare + Hi-Hat + leiser Schlag). Tonhöhen und Streuung
     * aus einem gemeinsamen Startwert (IDs + Startzeiten), daher klingt es überall gleich. Für die Beteiligten
     * zusätzlich ein kleiner Kamera-Ruck.
     */
    private static void spawnClap(MinecraftClient client, ClientWorld world, Lock lock, Seen sa, Seen sb, long now) {
        double x = lock.tx, y = lock.ty, z = lock.tz;
        java.util.Random r = new java.util.Random(lock.seed());

        double ux = lock.bx - lock.ax, uz = lock.bz - lock.az;
        double ul = Math.hypot(ux, uz);
        if (ul < 1.0E-6) {
            ux = 1.0;
            uz = 0.0;
        } else {
            ux /= ul;
            uz /= ul;
        }
        // Waagerechte Achse der Ringebene (senkrecht zur Linie A–B); die zweite Achse ist senkrecht nach oben
        double sx = -uz, sz = ux;

        world.addParticleClient(TintedParticleEffect.create(ParticleTypes.FLASH, 0xB0FFF2D6), x, y, z, 0.0, 0.0, 0.0);

        double phase = r.nextDouble() * Math.PI * 2.0;
        for (int i = 0; i < CLAP_RING; i++) {
            double a = phase + Math.PI * 2.0 * i / CLAP_RING;
            double up = Math.cos(a), side = Math.sin(a);
            double sp = 0.95 + r.nextDouble() * 0.3;
            // ELECTRIC_SPARK nimmt 1/4 der Geschwindigkeit
            world.addParticleClient(ParticleTypes.ELECTRIC_SPARK, x, y, z, sx * side * sp, up * sp, sz * side * sp);
            if (i % 2 == 0) {
                // CRIT nimmt 0,4 der Geschwindigkeit (+ etwas Zufall)
                world.addParticleClient(ParticleTypes.CRIT, x, y, z, sx * side * 0.55, up * 0.55, sz * side * 0.55);
            }
        }
        for (int i = 0; i < 5; i++) {
            world.addParticleClient(ParticleTypes.END_ROD,
                    x + (r.nextDouble() - 0.5) * 0.2, y + (r.nextDouble() - 0.5) * 0.15, z + (r.nextDouble() - 0.5) * 0.2,
                    (r.nextDouble() - 0.5) * 0.03, 0.03 + r.nextDouble() * 0.04, (r.nextDouble() - 0.5) * 0.03);
        }
        for (int i = 0; i < 3; i++) {
            world.addParticleClient(ParticleTypes.FIREWORK, x, y, z,
                    (r.nextDouble() - 0.5) * 0.12, (r.nextDouble() - 0.3) * 0.12, (r.nextDouble() - 0.5) * 0.12);
        }

        // Kein eigenes clap.ogg (kein Vorbis-Encoder): kurze Vanilla-Klänge übereinander
        float v = (float) (r.nextGaussian() * 0.04);
        world.playSoundClient(x, y, z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.75F, 1.6F + v, false);
        world.playSoundClient(x, y, z, SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value(), SoundCategory.PLAYERS, 0.6F, 1.8F + v, false);
        world.playSoundClient(x, y, z, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.PLAYERS, 0.45F, 1.25F + v, false);
        world.playSoundClient(x, y, z, SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.35F, 1.9F, false);
        PENDING.add(new Pending(now + REWARD_DELAY_MS, x, y, z, lock.seed()));

        Seen local = sa != null && sa.local() ? sa : (sb != null && sb.local() ? sb : null);
        if (local != null && client.player != null) {
            EmoteCamera.kick();
        }
    }

    /** Kurz nach dem Klatsch: heller Glitzer-Klang und ein paar aufsteigende Funken. */
    private static void spawnReward(ClientWorld world, Pending p) {
        java.util.Random r = new java.util.Random(p.seed() * 7L + 1L);
        float v = (float) (r.nextGaussian() * 0.05);
        world.playSoundClient(p.x(), p.y(), p.z(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.9F, 1.5F + v, false);
        world.playSoundClient(p.x(), p.y(), p.z(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.25F, 1.45F + v, false);
        for (int i = 0; i < 3; i++) {
            world.addParticleClient(ParticleTypes.WAX_ON,
                    p.x() + (r.nextDouble() - 0.5) * 0.35, p.y() + 0.1 + r.nextDouble() * 0.2, p.z() + (r.nextDouble() - 0.5) * 0.35,
                    0.0, 3.0 + r.nextDouble() * 2.0, 0.0);
        }
    }
}
