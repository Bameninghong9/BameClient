package com.bame.client.cosmetics;

import com.bame.client.emotes.EmoteEchoLogic;
import com.bame.client.emotes.EmotePreviewHolder;
import com.bame.client.emotes.EmoteType;
import com.bame.client.emotes.EmoteUnlockManager;
import com.bame.client.shop.CaeserApi;
import com.bame.client.shop.CaeserSyncClient;
import com.bame.client.shop.OwnershipCache;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Synchronisiert Cosmetics und Emotes zwischen Caeser-Client-Spielern.
 * <ul>
 *   <li>Hauptweg: eigener WebSocket des Caeser-Backends ({@link CaeserSyncClient}). Der Server bereinigt jeden State
 *       gegen den Besitz des Absenders, daher werden diese Daten direkt angezeigt.</li>
 *   <li>Fallback (nur wenn der Sync-Server nicht verbunden ist): LAN/Same-PC über {@link CaeserLocalPeerSync}.
 *       Diese Daten werden vor der Anzeige immer gegen den {@link OwnershipCache} gefiltert.</li>
 * </ul>
 * Alle Maps sind nach UUID (32 Hex, klein) geschlüsselt. Der Name dient nur zur Anzeige und als Rückfall-Index.
 * Schreibzugriffe passieren auf dem Haupt-Thread.
 */
public class CaeserPlayerSyncManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserPlayerSync");

    // Eindeutige ID pro laufender Spielinstanz (Echo-Schutz im LAN, instanceId im hello)
    public static final String LOCAL_INSTANCE_ID = UUID.randomUUID().toString();

    private static final Set<String> VALID_BADGES = Set.of("c", "c_bracket", "c_bold", "bolt", "crown", "fire", "star", "none");
    private static final Pattern PET_CUSTOM = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final Set<String> ACC_IDS = Set.of("minime_sit", "minime_head", "minime_hand", "minime_chain", "baby_dragon", "jelly_slime");

    private static final long STATE_CHECK_MS = 1_000L;
    private static final long STATE_MIN_INTERVAL_MS = 1_100L;   // Server: max. 1 State/s
    private static final long STATE_HEARTBEAT_MS = 60_000L;
    private static final long LAN_HEARTBEAT_MS = 20_000L;
    private static final long WATCH_CHECK_MS = 1_000L;
    private static final long WATCH_MIN_INTERVAL_MS = 5_000L;
    /** Neue Spieler in der Tab-Liste schneller beobachten, damit ihre Emotes nicht verpasst werden. */
    private static final long WATCH_ADD_MIN_INTERVAL_MS = 1_000L;
    /** Server: max. 2 Emote-Nachrichten pro Sekunde; mit Reserve, damit kein PLAY/STOP verworfen wird. */
    private static final int EMOTE_MAX_PER_WINDOW = 2;
    private static final long EMOTE_WINDOW_MS = 1_150L;
    /** Sicherheitsgrenze für dauerhafte Emotes ohne STOP (gleich der Nachlieferungsgrenze des Servers). */
    private static final float LOOPING_EMOTE_MAX_SECONDS = 30.0f * 60.0f;
    private static final int WATCH_MAX = 200;
    private static final long LAN_ENTRY_TTL_MS = 120_000L;

    public static class PlayerCosmeticsData {
        public String uuid;
        public String username;
        public String wings = "none";
        public String head = "none";
        public String face = "none";
        public String pet = "none";
        public String petCustomPlayer = "";
        public String tabBadge = "c";
        public final Set<String> accessories = ConcurrentHashMap.newKeySet();
        public long lastUpdated = System.currentTimeMillis();
        /** {@code true}: vom Sync-Server bereinigt. {@code false}: LAN, muss gegen den OwnershipCache gefiltert werden. */
        public boolean verified;

        private PlayerCosmeticsData filteredCache;
        private long filteredOwnershipVersion = -1;
        private long filteredStamp = -1;

        public boolean hasAny() {
            return !"none".equalsIgnoreCase(wings) ||
                   !"none".equalsIgnoreCase(head) ||
                   !"none".equalsIgnoreCase(face) ||
                   !"none".equalsIgnoreCase(pet) ||
                   !accessories.isEmpty();
        }
    }

    public static class PlayerEmoteData {
        public String uuid;
        public String username;
        public EmoteType emote = EmoteType.NONE;
        public long startTime = 0L;
        /** Startzeit in Serverzeit (Zeitstempel der emote-Nachricht), 0 im LAN. Gemeinsame Basis für Zufall pro Emote. */
        public long serverTime = 0L;
        public boolean verified;

        public float getElapsedSeconds() {
            if (emote == null || emote == EmoteType.NONE || emote == EmoteType.STOP) return 0.0f;
            return Math.max(0.0f, (System.currentTimeMillis() - startTime) / 1000.0f);
        }

        public boolean isExpired() {
            if (emote == null || emote == EmoteType.NONE || emote == EmoteType.STOP) return true;
            if (emote == EmoteType.WAVE && getElapsedSeconds() > 3.5f) return true;
            // High-Five endet spätestens nach 8 s Warten + Einschlagen; Sicherheitsgrenze, falls das STOP fehlt
            if (emote == EmoteType.HIGH_FIVE && getElapsedSeconds() > 10.0f) return true;
            // Einschlagen dauert unter 1,5 s
            if (emote == EmoteType.HIGH_FIVE_ACCEPT && getElapsedSeconds() > 3.0f) return true;
            // Dauerhafte Emotes (Sitzen, Rollstuhl ...) laufen bis zum STOP. Früher endeten sie nach 90 s für alle
            // anderen, obwohl der Spieler weiter saß; jetzt nur noch eine großzügige Sicherheitsgrenze.
            return getElapsedSeconds() > LOOPING_EMOTE_MAX_SECONDS;
        }
    }

    private static final Map<String, PlayerCosmeticsData> COSMETICS_MAP = new ConcurrentHashMap<>();
    private static final Map<String, PlayerEmoteData> EMOTE_MAP = new ConcurrentHashMap<>();
    private static final Map<String, String> NAME_INDEX = new ConcurrentHashMap<>(); // cleanName -> uuid32
    private static final Set<String> CAESER_PLAYERS = ConcurrentHashMap.newKeySet(); // uuid32

    private static volatile boolean initialized = false;

    // Sendezustand (Haupt-Thread)
    private static volatile boolean stateDirty = true;
    private static String lastStateSent = null;
    private static long lastStateSentAt = 0L;
    private static long lastStateCheckAt = 0L;
    private static long lastLanSentAt = 0L;
    private static boolean watchDirty = true;
    private static List<String> lastWatchSent = null;
    private static long lastWatchSentAt = 0L;
    private static long lastWatchCheckAt = 0L;
    private static EmoteType lastPlayedEmote = null;
    private static long serverClockOffset = Long.MAX_VALUE; // min(lokal - Server) über empfangene Emotes
    // Echo des eigenen Emotes (Haupt-Thread): Emote, lokaler Start und maßgebliche Serverzeit
    private static EmoteType ownEchoEmote = null;
    private static long ownEchoLocalStart = 0L;
    private static long ownEchoServerTime = 0L;
    // Emote-Nachrichten an den Server: Sendezeiten im Fenster und höchstens eine wartende (jüngste) Nachricht
    private static final java.util.ArrayDeque<Long> EMOTE_SEND_TIMES = new java.util.ArrayDeque<>();
    private static JsonObject pendingEmoteMessage = null;
    private static long lastLanCleanupAt = 0L;

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // LAN/Same-PC-Fallback. Empfang auf dem Listener-Thread, Verarbeitung auf dem Haupt-Thread.
        CaeserLocalPeerSync.start(payload -> {
            if (CaeserSyncClient.isConnected()) return;
            MinecraftClient.getInstance().execute(() -> handleLanMessage(payload));
        });

        LOGGER.info("Caeser Player Sync Manager initialized with instance ID {}", LOCAL_INSTANCE_ID);
    }

    public static String cleanUsername(String raw) {
        if (raw == null) return null;
        String clean = raw.replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();
        int spaceIdx = clean.lastIndexOf(' ');
        if (spaceIdx >= 0) {
            clean = clean.substring(spaceIdx + 1);
        }
        return clean.toLowerCase(Locale.ROOT);
    }

    // ==========================================
    // Identität lokal / Render-State
    // ==========================================

    /** UUID des Spielers zu einem Render-State: aus dem State (Mixin) oder über die Entity-ID in der Welt. */
    public static UUID getRenderStateUuid(PlayerEntityRenderState state) {
        if (state == null) return null;
        if (state instanceof EmotePreviewHolder holder && holder.caeser$getPlayerUuid() != null) {
            return holder.caeser$getPlayerUuid();
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null) {
            Entity e = mc.world.getEntityById(state.id);
            if (e instanceof PlayerEntity pe) return pe.getUuid();
        }
        return null;
    }

    private static String localUuid32() {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            if (mc.player != null) return CaeserApi.uuid32(mc.player.getUuid());
            if (mc.getSession() != null) return CaeserApi.uuid32(mc.getSession().getUuidOrNull());
        } catch (Throwable ignored) {}
        return null;
    }

    /** Ist dieser Render-State der lokale Spieler? Entity-ID, sonst UUID, sonst (Vorschau) Name. */
    public static boolean isLocalPlayer(PlayerEntityRenderState state) {
        if (state == null) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && state.id == mc.player.getId()) return true;
        if (state instanceof EmotePreviewHolder holder && holder.caeser$getPlayerUuid() != null) {
            String local = localUuid32();
            return local != null && local.equals(CaeserApi.uuid32(holder.caeser$getPlayerUuid()));
        }
        String rawName = CaeserCosmeticsRenderer.getPlayerUsername(state);
        if (rawName != null && mc.getSession() != null) {
            String cleanStateName = cleanUsername(rawName);
            String cleanLocalName = cleanUsername(mc.getSession().getUsername());
            return cleanStateName != null && cleanStateName.equalsIgnoreCase(cleanLocalName);
        }
        return false;
    }

    private static String resolveKey(PlayerEntityRenderState state) {
        UUID uuid = getRenderStateUuid(state);
        if (uuid != null) return CaeserApi.uuid32(uuid);
        String name = CaeserCosmeticsRenderer.getPlayerUsername(state);
        return keyForName(name);
    }

    private static String keyForName(String username) {
        String clean = cleanUsername(username);
        if (clean == null || clean.isBlank()) return null;
        return NAME_INDEX.get(clean);
    }

    // ==========================================
    // Lesen (Renderer, Tab-Liste)
    // ==========================================

    /** Cosmetics eines fremden Spielers, bereits gegen den Besitz geprüft; {@code null} wenn keine bzw. unbekannt. */
    public static PlayerCosmeticsData getCosmetics(PlayerEntityRenderState state) {
        return getCosmeticsByKey(resolveKey(state));
    }

    public static PlayerCosmeticsData getCosmetics(UUID uuid) {
        return getCosmeticsByKey(CaeserApi.uuid32(uuid));
    }

    /** Rückfall über den Namen (nur, wenn keine UUID verfügbar ist). */
    public static PlayerCosmeticsData getCosmetics(String username) {
        return getCosmeticsByKey(keyForName(username));
    }

    private static PlayerCosmeticsData getCosmeticsByKey(String key) {
        if (key == null) return null;
        PlayerCosmeticsData data = COSMETICS_MAP.get(key);
        if (data == null) return null;
        return data.verified ? data : filterLan(data);
    }

    public static PlayerEmoteData getActiveEmote(PlayerEntityRenderState state) {
        return getEmoteByKey(resolveKey(state));
    }

    public static PlayerEmoteData getActiveEmote(UUID uuid) {
        return getEmoteByKey(CaeserApi.uuid32(uuid));
    }

    public static PlayerEmoteData getActiveEmote(String username) {
        return getEmoteByKey(keyForName(username));
    }

    private static PlayerEmoteData getEmoteByKey(String key) {
        if (key == null) return null;
        PlayerEmoteData data = EMOTE_MAP.get(key);
        if (data == null) return null;
        if (data.isExpired()) {
            EMOTE_MAP.remove(key, data);
            return null;
        }
        if (!data.verified && !OwnershipCache.owns(key, data.emote.name())) return null;
        return data;
    }

    /** LAN-Daten nur mit bestätigtem Besitz; Ergebnis wird pro Datenstand und Besitz-Version zwischengespeichert. */
    private static PlayerCosmeticsData filterLan(PlayerCosmeticsData d) {
        Set<String> owned = OwnershipCache.getOwned(d.uuid);
        if (owned == null) return null;
        long ver = OwnershipCache.version(d.uuid);
        if (d.filteredCache != null && d.filteredOwnershipVersion == ver && d.filteredStamp == d.lastUpdated) {
            return d.filteredCache;
        }
        PlayerCosmeticsData f = new PlayerCosmeticsData();
        f.uuid = d.uuid;
        f.username = d.username;
        f.verified = false;
        f.lastUpdated = d.lastUpdated;
        f.wings = ownedOrNone(owned, d.wings);
        f.head = ownedOrNone(owned, d.head);
        f.face = ownedOrNone(owned, d.face);
        f.pet = ownedOrNone(owned, d.pet);
        f.petCustomPlayer = d.petCustomPlayer;
        f.tabBadge = d.tabBadge;
        for (String acc : d.accessories) {
            String id = CaeserApi.itemId(acc);
            if (id != null && owned.contains(id)) f.accessories.add(acc);
        }
        d.filteredCache = f;
        d.filteredOwnershipVersion = ver;
        d.filteredStamp = d.lastUpdated;
        return f;
    }

    private static String ownedOrNone(Set<String> owned, String value) {
        String id = CaeserApi.itemId(value);
        return id != null && owned.contains(id) ? value : "none";
    }

    public static boolean isCaeserPlayer(UUID uuid, String username) {
        String key = CaeserApi.uuid32(uuid);
        if (key == null) return isCaeserPlayer(username);
        if (key.equals(localUuid32())) return true;
        return CAESER_PLAYERS.contains(key) || COSMETICS_MAP.containsKey(key) || EMOTE_MAP.containsKey(key);
    }

    public static boolean isCaeserPlayer(String username) {
        if (username == null || username.isBlank()) return false;
        String clean = cleanUsername(username);
        if (clean == null || clean.isBlank()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getSession() != null && clean.equalsIgnoreCase(cleanUsername(mc.getSession().getUsername()))) {
            return true;
        }
        String key = NAME_INDEX.get(clean);
        return key != null && (CAESER_PLAYERS.contains(key) || COSMETICS_MAP.containsKey(key) || EMOTE_MAP.containsKey(key));
    }

    public static String getPlayerBadgePrefix(UUID uuid, String username) {
        if (!com.bame.client.module.TabBadgeModule.enabled) return null;
        String key = CaeserApi.uuid32(uuid);
        if (key == null) return getPlayerBadgePrefix(username);
        if (key.equals(localUuid32())) {
            return com.bame.client.module.TabBadgeModule.getBadgeSymbol();
        }
        return badgeSymbol(COSMETICS_MAP.get(key));
    }

    public static String getPlayerBadgePrefix(String username) {
        if (!com.bame.client.module.TabBadgeModule.enabled) return null;
        if (username == null || username.isBlank()) return null;
        String clean = cleanUsername(username);
        if (clean == null || clean.isBlank()) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getSession() != null && clean.equalsIgnoreCase(cleanUsername(mc.getSession().getUsername()))) {
            return com.bame.client.module.TabBadgeModule.getBadgeSymbol();
        }
        String key = NAME_INDEX.get(clean);
        return badgeSymbol(key != null ? COSMETICS_MAP.get(key) : null);
    }

    /** Nur noch ein Design: jedes Badge außer {@code none} wird als Caeser Bold gezeigt (auch von älteren Clients). */
    private static String badgeSymbol(PlayerCosmeticsData data) {
        return com.bame.client.module.TabBadgeModule.symbolForRemote(data != null ? data.tabBadge : null);
    }

    // ==========================================
    // Lokaler State (nur Besessenes)
    // ==========================================

    private static String ownedLocal(String category, String value) {
        if (value == null || value.isBlank()) return "none";
        return EmoteUnlockManager.isCosmeticUnlocked(category, value) ? value : "none";
    }

    /** State des lokalen Spielers; nicht besessene Items werden wie {@code none} behandelt. */
    public static JsonObject buildLocalState() {
        JsonObject state = new JsonObject();
        state.addProperty("wings", ownedLocal("WINGS", CosmeticsConfig.wings));
        state.addProperty("head", ownedLocal("HEAD", CosmeticsConfig.head));
        state.addProperty("face", ownedLocal("FACE", CosmeticsConfig.face));

        String pet = CosmeticsConfig.pet != null ? CosmeticsConfig.pet.toLowerCase(Locale.ROOT) : "none";
        if ("self".equals(pet) || "custom".equals(pet)) pet = "minime_sit";
        state.addProperty("pet", ownedLocal(isPetId(pet) ? "PETS" : "ACCESSORIES", pet));

        String petCustom = CosmeticsConfig.petCustomPlayer != null ? CosmeticsConfig.petCustomPlayer.trim() : "";
        state.addProperty("petCustom", PET_CUSTOM.matcher(petCustom).matches() ? petCustom : "");

        JsonArray acc = new JsonArray();
        for (String a : CosmeticsConfig.accessories) {
            String id = a != null ? a.toLowerCase(Locale.ROOT) : "";
            if (!ACC_IDS.contains(id)) continue;
            if (EmoteUnlockManager.isCosmeticUnlocked(isPetId(id) ? "PETS" : "ACCESSORIES", id)) acc.add(id);
        }
        state.add("acc", acc);

        // Einziges Badge: c_bold, ausgeschaltet none (beides vom Backend erlaubt)
        state.addProperty("tabBadge", com.bame.client.module.TabBadgeModule.syncValue());
        return state;
    }

    private static boolean isPetId(String id) {
        return "baby_dragon".equalsIgnoreCase(id) || "jelly_slime".equalsIgnoreCase(id);
    }

    /** Ausgerüstete Cosmetics bzw. Besitz haben sich geändert: State bald neu senden. */
    public static void markStateDirty() {
        stateDirty = true;
    }

    /** Bisheriger Name; Aufrufer (CosmeticsConfig, RenderTab) melden damit eine Änderung. */
    public static void broadcastLocalCosmetics() {
        markStateDirty();
    }

    public static void broadcastLocalEmote(EmoteType emote, boolean isStop) {
        MinecraftClient mc = MinecraftClient.getInstance();
        EmoteType target;
        if (isStop || emote == null || emote == EmoteType.STOP || emote == EmoteType.NONE) {
            // Der Server prüft den Besitz des Emotes, daher beim Stoppen das zuletzt gespielte Emote nennen
            target = lastPlayedEmote;
            lastPlayedEmote = null;
            isStop = true;
            if (target == null) return;
        } else {
            target = emote;
            lastPlayedEmote = emote;
        }

        if (CaeserSyncClient.isConnected()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "emote");
            json.addProperty("emote", target.name());
            json.addProperty("action", isStop ? "STOP" : "PLAY");
            sendEmoteMessage(json);
            return;
        }

        // LAN-Fallback
        if (mc.getSession() == null) return;
        try {
            JsonObject json = lanEnvelope("EMOTE");
            if (json == null) return;
            json.addProperty("emote", isStop ? "STOP" : target.name());
            json.addProperty("action", isStop ? "STOP" : "PLAY");
            json.addProperty("time", System.currentTimeMillis());
            CaeserLocalPeerSync.broadcast(json.toString());
        } catch (Throwable t) {
            LOGGER.debug("Failed to broadcast local emote: {}", t.getMessage());
        }
    }

    /**
     * Emote-Nachricht senden, ohne das Server-Limit (2/s) zu überschreiten: Der Server verwirft überzählige Nachrichten
     * stillschweigend, dann sahen andere ein gestartetes Emote nie oder ein beendetes ewig. Ist das Limit erreicht,
     * wartet nur die jüngste Nachricht (sie beschreibt den aktuellen Zustand vollständig) und geht im nächsten Tick raus.
     */
    private static void sendEmoteMessage(JsonObject json) {
        long now = System.currentTimeMillis();
        pruneEmoteSendTimes(now);
        if (pendingEmoteMessage == null && EMOTE_SEND_TIMES.size() < EMOTE_MAX_PER_WINDOW) {
            if (CaeserSyncClient.send(json)) EMOTE_SEND_TIMES.addLast(now);
            return;
        }
        pendingEmoteMessage = json;
    }

    private static void flushPendingEmote(long now) {
        if (pendingEmoteMessage == null) return;
        if (!CaeserSyncClient.isConnected()) {
            pendingEmoteMessage = null;
            return;
        }
        pruneEmoteSendTimes(now);
        if (EMOTE_SEND_TIMES.size() >= EMOTE_MAX_PER_WINDOW) return;
        if (CaeserSyncClient.send(pendingEmoteMessage)) EMOTE_SEND_TIMES.addLast(now);
        pendingEmoteMessage = null;
    }

    private static void pruneEmoteSendTimes(long now) {
        while (!EMOTE_SEND_TIMES.isEmpty() && now - EMOTE_SEND_TIMES.peekFirst() >= EMOTE_WINDOW_MS) {
            EMOTE_SEND_TIMES.pollFirst();
        }
    }

    // ==========================================
    // Lebenszyklus
    // ==========================================

    public static void onServerJoin() {
        stateDirty = true;
        lastStateSentAt = 0L;
        watchDirty = true;
        lastWatchSentAt = 0L;
        CaeserSyncClient.ensureConnected();
        if (!CaeserSyncClient.isConnected()) {
            sendRequestSync();
            lastLanSentAt = 0L;
        }
    }

    /** Server verlassen: leere Beobachtungsliste senden, fremde Daten verwerfen. */
    public static void onServerLeave() {
        if (CaeserSyncClient.isConnected() && (lastWatchSent == null || !lastWatchSent.isEmpty())) {
            sendWatch(List.of());
        }
        lastWatchSent = List.of();
        lastWatchSentAt = System.currentTimeMillis();
        lastPlayedEmote = null;
        pendingEmoteMessage = null;
        clearRemote();
    }

    /** Nach einem Ingame-Account-Wechsel. */
    public static void onAccountChanged() {
        stateDirty = true;
        lastStateSent = null;
        lastStateSentAt = 0L;
        watchDirty = true;
        lastWatchSent = null;
        lastWatchSentAt = 0L;
        lastPlayedEmote = null;
        lastLanSentAt = 0L;
        ownEchoEmote = null;
        ownEchoLocalStart = 0L;
        ownEchoServerTime = 0L;
        clearRemote();
        sendRequestSync();
    }

    /** Vom Sync-Client nach {@code welcome} (Haupt-Thread). */
    public static void onSyncWelcome() {
        // Server-Daten ersetzen alles Bisherige (inkl. LAN); States und laufende Emotes kommen nach "watch".
        clearRemote();
        stateDirty = true;
        lastStateSentAt = 0L;
        watchDirty = true;
        lastWatchSent = null;
        lastWatchSentAt = 0L;
        // Neue Verbindung: Uhrenversatz neu schätzen (die lokale Uhr kann sich inzwischen verstellt haben)
        serverClockOffset = Long.MAX_VALUE;
        EMOTE_SEND_TIMES.clear();
        pendingEmoteMessage = null;
        // Der Server merkt sich unser laufendes Emote pro Verbindung: nach einem Reconnect erneut melden
        // (nicht das kurze Einschlagen: es würde beim Partner ein neues High-Five auslösen)
        if (com.bame.client.module.EmoteModule.isEmoting()
                && com.bame.client.module.EmoteModule.getCurrentEmote() != EmoteType.HIGH_FIVE_ACCEPT) {
            lastPlayedEmote = null;
            broadcastLocalEmote(com.bame.client.module.EmoteModule.getCurrentEmote(), false);
        }
    }

    /** Verbindung zum Sync-Server verloren (Haupt-Thread). LAN übernimmt bis zum Reconnect. */
    public static void onSyncDisconnected() {
        lastLanSentAt = 0L;
        lastStateSent = null;
    }

    private static void clearRemote() {
        COSMETICS_MAP.clear();
        EMOTE_MAP.clear();
        CAESER_PLAYERS.clear();
        NAME_INDEX.clear();
    }

    public static void tick(MinecraftClient client) {
        if (client == null) return;
        OwnershipCache.tick();

        long now = System.currentTimeMillis();
        boolean sync = CaeserSyncClient.isConnected();
        flushPendingEmote(now);

        // 1. State: bei Änderung (auch durch den Launcher), sonst alle 60 s
        if (now - lastStateCheckAt >= STATE_CHECK_MS) {
            lastStateCheckAt = now;
            CosmeticsConfig.checkReload();
            String current = buildLocalState().toString();
            if (!current.equals(lastStateSent)) stateDirty = true;

            if (sync) {
                boolean due = stateDirty || now - lastStateSentAt >= STATE_HEARTBEAT_MS;
                if (due && now - lastStateSentAt >= STATE_MIN_INTERVAL_MS) {
                    JsonObject msg = new JsonObject();
                    msg.addProperty("type", "state");
                    msg.add("state", JsonParser.parseString(current));
                    if (CaeserSyncClient.send(msg)) {
                        lastStateSent = current;
                        lastStateSentAt = now;
                        stateDirty = false;
                    }
                }
            } else if (client.player != null) {
                // LAN-Fallback: Heartbeat alle 20 s bzw. bei Änderung
                if (stateDirty || now - lastLanSentAt >= LAN_HEARTBEAT_MS) {
                    broadcastLanCosmetics(current);
                    lastLanSentAt = now;
                    lastStateSent = current;
                    stateDirty = false;
                }
            }
        }

        // 2. Watch: Tab-Liste (nur v4-UUIDs), bei Änderung höchstens alle 5 s
        if (sync && now - lastWatchCheckAt >= WATCH_CHECK_MS) {
            lastWatchCheckAt = now;
            List<String> watch = collectWatchList(client);
            if (watch != null) {
                boolean changed = watchDirty || !watch.equals(lastWatchSent);
                // Neue Spieler schnell nachmelden: der Server verteilt Emotes nur an Beobachter (und liefert ein laufendes
                // Emote beim watch nach); wenn nur Spieler weggefallen sind, reicht der 5-s-Abstand
                boolean added = lastWatchSent == null || !lastWatchSent.containsAll(watch);
                long minInterval = added ? WATCH_ADD_MIN_INTERVAL_MS : WATCH_MIN_INTERVAL_MS;
                if (changed && now - lastWatchSentAt >= minInterval) {
                    if (sendWatch(watch)) {
                        lastWatchSent = watch;
                        lastWatchSentAt = now;
                        watchDirty = false;
                    }
                }
            }
        }

        // 3. Alte LAN-Einträge aufräumen
        if (now - lastLanCleanupAt >= 10_000L) {
            lastLanCleanupAt = now;
            for (Iterator<Map.Entry<String, PlayerCosmeticsData>> it = COSMETICS_MAP.entrySet().iterator(); it.hasNext(); ) {
                PlayerCosmeticsData d = it.next().getValue();
                if (!d.verified && now - d.lastUpdated > LAN_ENTRY_TTL_MS) it.remove();
            }
            EMOTE_MAP.entrySet().removeIf(e -> e.getValue().isExpired());
        }
    }

    /** UUIDs aus der Tab-Liste (ohne uns selbst), nur Version 4; Spieler in der Nähe zuerst. {@code null} ohne Server. */
    private static List<String> collectWatchList(MinecraftClient client) {
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return null;
        String self = localUuid32();
        Set<String> result = new LinkedHashSet<>();
        if (client.world != null) {
            for (PlayerEntity p : client.world.getPlayers()) {
                if (result.size() >= WATCH_MAX) break;
                UUID u = p.getUuid();
                if (u == null || u.version() != 4) continue;
                if (handler.getPlayerListEntry(u) == null) continue;
                String k = CaeserApi.uuid32(u);
                if (k != null && !k.equals(self)) result.add(k);
            }
        }
        for (PlayerListEntry entry : handler.getPlayerList()) {
            if (result.size() >= WATCH_MAX) break;
            if (entry == null || entry.getProfile() == null) continue;
            UUID u = entry.getProfile().id();
            if (u == null || u.version() != 4) continue;
            String k = CaeserApi.uuid32(u);
            if (k != null && !k.equals(self)) result.add(k);
        }
        List<String> sorted = new ArrayList<>(result);
        java.util.Collections.sort(sorted);
        return List.copyOf(sorted);
    }

    private static boolean sendWatch(List<String> uuids) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "watch");
        JsonArray arr = new JsonArray();
        uuids.forEach(arr::add);
        msg.add("uuids", arr);
        return CaeserSyncClient.send(msg);
    }

    // ==========================================
    // Empfang vom Sync-Server (Haupt-Thread)
    // ==========================================

    public static void applyServerState(String uuid32, String name, JsonObject state) {
        if (uuid32 == null || state == null || uuid32.equals(localUuid32())) return;
        PlayerCosmeticsData data = new PlayerCosmeticsData();
        data.uuid = uuid32;
        data.username = name;
        data.verified = true;
        readState(data, state);
        COSMETICS_MAP.put(uuid32, data);
        CAESER_PLAYERS.add(uuid32);
        indexName(name, uuid32);
    }

    public static void applyServerEmote(String uuid32, String name, String emote, String action, long serverTime, long receivedAt) {
        if (uuid32 == null) return;
        if (uuid32.equals(localUuid32())) {
            // Echo des eigenen Emotes: nur die maßgebliche Serverzeit merken (keine fremden Daten, keine zweite Animation)
            applyOwnEmoteEcho(emote, action, serverTime, receivedAt);
            return;
        }
        CAESER_PLAYERS.add(uuid32);
        indexName(name, uuid32);
        if ("STOP".equalsIgnoreCase(action) || "STOP".equalsIgnoreCase(emote)) {
            EMOTE_MAP.remove(uuid32);
            return;
        }
        EmoteType type = EmoteType.fromId(emote);
        if (type == null || type == EmoteType.NONE || type == EmoteType.STOP) return;
        long start = startTimeFromServer(serverTime, receivedAt);
        PlayerEmoteData existing = EMOTE_MAP.get(uuid32);
        if (existing != null && existing.verified && existing.emote == type && Math.abs(existing.startTime - start) < 2_000L) {
            // Dasselbe PLAY noch einmal (z. B. beim watch nachgeliefert): Startzeit nicht verschieben
            if (name != null) existing.username = name;
            return;
        }
        PlayerEmoteData data = new PlayerEmoteData();
        data.uuid = uuid32;
        data.username = name;
        data.emote = type;
        data.verified = true;
        data.startTime = start;
        data.serverTime = Math.max(0L, serverTime);
        EMOTE_MAP.put(uuid32, data);
    }

    /**
     * Startzeit aus der Serverzeit ableiten. Der Uhrenversatz wird als kleinster beobachteter Wert von
     * (Empfangszeit lokal − Serverzeit) geschätzt; so werden verspätet ankommende Nachrichten korrekt fortgesetzt,
     * ohne dass lokale und Server-Uhr übereinstimmen müssen.
     */
    private static long startTimeFromServer(long serverTime, long receivedAt) {
        if (serverTime <= 0) return receivedAt;
        long diff = receivedAt - serverTime;
        if (diff < serverClockOffset) serverClockOffset = diff;
        long start = serverTime + serverClockOffset;
        return Math.min(start, receivedAt);
    }

    /**
     * Echo des eigenen Emotes (der Sync-Server schickt jedes verteilte PLAY/STOP auch an die Verbindungen des Spielers
     * selbst). Passt es zum laufenden lokalen Emote (gleiches Emote, kurz nach dem lokalen Start, Serverzeit passt zur
     * Schätzung; siehe {@link EmoteEchoLogic}), wird seine Serverzeit für diesen Start gemerkt. STOP und veraltete Echos
     * werden ignoriert; ältere Server ohne Echo ändern nichts (dann bleibt es bei der Schätzung).
     */
    private static void applyOwnEmoteEcho(String emote, String action, long serverTime, long receivedAt) {
        if ("STOP".equalsIgnoreCase(action) || "STOP".equalsIgnoreCase(emote)) return;
        EmoteType type = EmoteType.fromId(emote);
        if (type == null || type == EmoteType.NONE || type == EmoteType.STOP) return;
        if (!com.bame.client.module.EmoteModule.isEmoting() || com.bame.client.module.EmoteModule.getCurrentEmote() != type) {
            return;
        }
        long localStart = com.bame.client.module.EmoteModule.getStartTimeMillis();
        // Schon ein Echo für diesen Start: nicht mehr ändern (der Zufall soll mitten im Emote nicht umspringen)
        if (ownEchoEmote == type && ownEchoLocalStart == localStart) return;
        if (EmoteEchoLogic.accepts(localStart, receivedAt, serverTime, hasServerClock(), estimateServerTime(localStart))) {
            ownEchoEmote = type;
            ownEchoLocalStart = localStart;
            ownEchoServerTime = serverTime;
        }
    }

    /**
     * Maßgebliche Serverzeit des eigenen Emotes {@code type} mit lokalem Start {@code localStartMs} (aus dem Echo des
     * Sync-Servers), oder 0, solange (noch) kein passendes Echo angekommen ist.
     */
    public static long ownEmoteServerTime(EmoteType type, long localStartMs) {
        if (localStartMs <= 0L || type == null) return 0L;
        return ownEchoEmote == type && ownEchoLocalStart == localStartMs ? ownEchoServerTime : 0L;
    }

    /**
     * Gemeinsame Zeitbasis (Serverzeit, ms) eines Emotes für deterministischen Zufall auf allen Clients.
     * <ul>
     *   <li>eigenes Emote: Serverzeit aus dem Echo, bis dahin die Schätzung {@link #estimateServerTime}</li>
     *   <li>fremdes Emote: Zeitstempel der emote-Nachricht ({@link PlayerEmoteData#serverTime}), im LAN die lokale
     *       Startzeit</li>
     * </ul>
     *
     * @param localStartMs Startzeit des Emotes in lokaler Zeit (wie {@code ActiveEmote.startMs})
     */
    public static long sharedStartTime(String uuid32, EmoteType type, long localStartMs) {
        if (uuid32 == null) return localStartMs;
        if (uuid32.equals(localUuid32())) {
            long exact = ownEmoteServerTime(type, localStartMs);
            return exact > 0L ? exact : estimateServerTime(localStartMs);
        }
        PlayerEmoteData data = EMOTE_MAP.get(uuid32);
        if (data != null && data.startTime == localStartMs && data.serverTime > 0L) return data.serverTime;
        return localStartMs;
    }

    /**
     * Lokale Zeit (ms) grob in Serverzeit umrechnen (mit dem geschätzten Uhrenversatz); ohne Schätzung unverändert.
     * Rückfall für das eigene Emote, bis das Echo mit dem Server-Zeitstempel da ist.
     */
    public static long estimateServerTime(long localMs) {
        long offset = serverClockOffset;
        return offset == Long.MAX_VALUE ? localMs : localMs - offset;
    }

    /** Wurde der Uhrenversatz zum Sync-Server schon geschätzt? */
    public static boolean hasServerClock() {
        return serverClockOffset != Long.MAX_VALUE;
    }

    public static void applyServerLeave(String uuid32) {
        if (uuid32 == null) return;
        COSMETICS_MAP.remove(uuid32);
        EMOTE_MAP.remove(uuid32);
        CAESER_PLAYERS.remove(uuid32);
        NAME_INDEX.values().removeIf(uuid32::equals);
    }

    private static void indexName(String name, String uuid32) {
        String clean = cleanUsername(name);
        if (clean != null && !clean.isBlank()) NAME_INDEX.put(clean, uuid32);
    }

    private static void readState(PlayerCosmeticsData data, JsonObject root) {
        data.tabBadge = sanitizeBadge(str(root, "tabBadge", "c"));
        data.wings = str(root, "wings", "none");
        data.head = str(root, "head", "none");
        // Ältere Clients senden kein "face"
        data.face = str(root, "face", "none");
        data.pet = str(root, "pet", "none");
        String petCustom = str(root, "petCustom", "");
        data.petCustomPlayer = PET_CUSTOM.matcher(petCustom).matches() ? petCustom : "";
        data.lastUpdated = System.currentTimeMillis();
        data.accessories.clear();
        if (root.has("acc") && root.get("acc").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("acc")) {
                if (el.isJsonPrimitive()) data.accessories.add(el.getAsString().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static String sanitizeBadge(String raw) {
        String b = raw != null ? raw.toLowerCase(Locale.ROOT) : "c";
        return VALID_BADGES.contains(b) ? b : "c";
    }

    private static String str(JsonObject o, String key, String def) {
        if (o == null || !o.has(key) || !o.get(key).isJsonPrimitive()) return def;
        String s = o.get(key).getAsString();
        return s != null ? s : def;
    }

    // ==========================================
    // LAN-Fallback
    // ==========================================

    private static JsonObject lanEnvelope(String type) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getSession() == null) return null;
        String name = mc.getSession().getUsername();
        String uuid = localUuid32();
        if (name == null || name.isBlank() || uuid == null) return null;
        JsonObject json = new JsonObject();
        json.addProperty("v", 2);
        json.addProperty("type", type);
        json.addProperty("instanceId", LOCAL_INSTANCE_ID);
        json.addProperty("name", name);
        json.addProperty("uuid", uuid);
        return json;
    }

    public static void sendRequestSync() {
        if (CaeserSyncClient.isConnected()) return;
        try {
            JsonObject json = lanEnvelope("REQUEST_SYNC");
            if (json != null) CaeserLocalPeerSync.broadcast(json.toString());
        } catch (Throwable ignored) {}
    }

    private static void broadcastLanCosmetics(String stateJson) {
        try {
            JsonObject json = lanEnvelope("COSMETICS");
            if (json == null) return;
            JsonObject state = JsonParser.parseString(stateJson).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : state.entrySet()) {
                json.add(e.getKey(), e.getValue());
            }
            CaeserLocalPeerSync.broadcast(json.toString());
        } catch (Throwable t) {
            LOGGER.debug("Failed to broadcast local cosmetics: {}", t.getMessage());
        }
    }

    private static void handleLanMessage(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > 8192) return;
        if (CaeserSyncClient.isConnected()) return; // nur Fallback
        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            String type = str(root, "type", "");
            String name = str(root, "name", "");
            String senderInstanceId = str(root, "instanceId", "");

            // Eigenes Echo ignorieren (Instanz-ID, damit zwei Instanzen auf einem PC funktionieren)
            if (LOCAL_INSTANCE_ID.equals(senderInstanceId)) return;

            String uuid = CaeserApi.uuid32(str(root, "uuid", null));
            if (uuid == null) uuid = uuidFromTabList(name); // ältere Clients ohne UUID
            if (uuid == null || uuid.equals(localUuid32())) {
                if ("REQUEST_SYNC".equalsIgnoreCase(type)) lastLanSentAt = 0L;
                return;
            }

            CAESER_PLAYERS.add(uuid);
            indexName(name, uuid);

            if ("REQUEST_SYNC".equalsIgnoreCase(type)) {
                lastLanSentAt = 0L; // beim nächsten Tick antworten
                return;
            }

            if ("COSMETICS".equalsIgnoreCase(type)) {
                PlayerCosmeticsData data = new PlayerCosmeticsData();
                data.uuid = uuid;
                data.username = name;
                data.verified = false;
                readState(data, root);
                COSMETICS_MAP.put(uuid, data);
                OwnershipCache.getOwned(uuid); // Besitz vormerken
            } else if ("EMOTE".equalsIgnoreCase(type)) {
                String action = str(root, "action", "PLAY");
                String emoteStr = str(root, "emote", "");
                if ("STOP".equalsIgnoreCase(action) || "STOP".equalsIgnoreCase(emoteStr)) {
                    EMOTE_MAP.remove(uuid);
                } else {
                    EmoteType emType = EmoteType.fromId(emoteStr);
                    if (emType != null && emType != EmoteType.NONE && emType != EmoteType.STOP) {
                        PlayerEmoteData emData = new PlayerEmoteData();
                        emData.uuid = uuid;
                        emData.username = name;
                        emData.emote = emType;
                        emData.verified = false;
                        // Im LAN lokal zum Empfangszeitpunkt starten
                        emData.startTime = System.currentTimeMillis();
                        EMOTE_MAP.put(uuid, emData);
                        OwnershipCache.getOwned(uuid);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("Error processing LAN sync message: {}", t.getMessage());
        }
    }

    private static String uuidFromTabList(String name) {
        if (name == null || name.isBlank()) return null;
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) return null;
        PlayerListEntry entry = handler.getPlayerListEntry(name);
        if (entry == null || entry.getProfile() == null) return null;
        return CaeserApi.uuid32(entry.getProfile().id());
    }
}
