package com.bame.client.cosmetics;

import com.bame.client.shop.CaeserApi;
import com.bame.client.shop.CaeserAuth;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerCapeModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Caeser-Capes (im Launcher gewählt) im Spiel.
 * <ul>
 *   <li>Cape-ID: eigener Spieler aus {@code /v1/me} ({@code capeId}) bzw. dem letzten eigenen Sync-{@code state}
 *       (der neuere Wert gilt); fremde Spieler aus {@link CaeserPlayerSyncManager.PlayerCosmeticsData#cape}.</li>
 *   <li>Textur: Festplatten-Cache ({@code config/bameclient/cape-cache/<id>.png}), sonst Download
 *       ({@code GET /v1/capes/<id>.png}, max. 1 MB, 10 s, höchstens 3 gleichzeitig, das eigene Cape mit Bearer-Token).
 *       Geprüft wird PNG und Größe (2:1, 64×32 bis 1024×512); kaputte Dateien werden für die Sitzung verworfen.</li>
 *   <li>Registrierung als {@link NativeImageBackedTexture} unter {@code bameclient:capes/<id>}, einmal pro ID;
 *       5 Minuten ungenutzte Texturen werden wieder freigegeben.</li>
 * </ul>
 * Alles außer dem Registrieren läuft im Hintergrund; ohne geladene Textur bleibt die {@link SkinTextures} unverändert.
 */
public final class CaeserCapeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserCapes");

    private static final long UNLOAD_AFTER_MS = 5 * 60_000L;
    private static final long TICK_INTERVAL_MS = 30_000L;
    private static final long RETRY_NETWORK_MS = 60_000L;
    private static final long RETRY_HTTP_MS = 5 * 60_000L;
    private static final long DOWNLOAD_WAIT_MS = 12_000L;

    /** Normale Elytra-Textur: für Spieler mit Caeser-Cape, wenn "Cape auf der Elytra zeigen" aus ist. */
    private static final Identifier DEFAULT_ELYTRA_ID = Identifier.ofVanilla("textures/entity/equipment/wings/elytra.png");
    private static final AssetInfo.TextureAsset DEFAULT_ELYTRA = new AssetInfo.TextureAssetInfo(DEFAULT_ELYTRA_ID, DEFAULT_ELYTRA_ID);

    private enum State { LOADING, READY, FAILED }

    private static final class Entry {
        final String id;
        volatile State state = State.LOADING;
        volatile Identifier textureId;
        volatile AssetInfo.TextureAsset asset;
        volatile long lastUsed = System.currentTimeMillis();
        volatile long retryAt = Long.MAX_VALUE;

        Entry(String id) {
            this.id = id;
        }
    }

    private record Wrapped(SkinTextures base, Identifier tex, boolean elytraOption, SkinTextures result) {}

    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Map<UUID, Wrapped> WRAPPED = new ConcurrentHashMap<>();
    private static final Map<UUID, String> UUID32 = new ConcurrentHashMap<>();

    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    /** Drei Threads: höchstens drei Downloads gleichzeitig; Cache lesen/schreiben und Dekodieren ebenfalls hier. */
    private static final ExecutorService IO = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "Caeser-Cape-" + THREAD_IDS.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    // Eigenes Cape (gehört zu ownUuid32); der jeweils neuere Wert aus Wallet bzw. Sync gilt
    private static volatile String ownUuid32;
    private static volatile String ownCapeId;
    private static volatile long ownUpdatedAt;

    private static long lastTickAt;
    private static PlayerCapeModel previewCapeModel;

    private CaeserCapeManager() {}

    // ==========================================
    // Cape-ID
    // ==========================================

    /** Aus {@code Wallet.refresh()} ({@code /v1/me}). Ein Sync-Wert, der nach dem Start der Anfrage kam, hat Vorrang. */
    public static void setOwnCapeFromWallet(String uuid32, String capeId, long requestStartedAt) {
        if (uuid32 == null) return;
        if (uuid32.equals(ownUuid32) && ownUpdatedAt > requestStartedAt) return;
        setOwn(uuid32, capeId);
    }

    /** Aus dem eigenen Sync-{@code state} (auch mit einem Cape in Prüfung). */
    public static void setOwnCapeFromSync(String uuid32, String capeId) {
        if (uuid32 == null) return;
        setOwn(uuid32, capeId);
    }

    private static synchronized void setOwn(String uuid32, String capeId) {
        String id = CapeFiles.normalizeId(capeId);
        boolean changed = !uuid32.equals(ownUuid32) || !java.util.Objects.equals(id, ownCapeId);
        ownUuid32 = uuid32;
        ownCapeId = id;
        ownUpdatedAt = System.currentTimeMillis();
        if (changed) {
            WRAPPED.clear();
            LOGGER.info("Own Caeser cape: {}", id != null ? id : "none");
            if (id != null) {
                // Ein vorher (z. B. ohne Token) fehlgeschlagener Versuch soll für das eigene Cape sofort neu laufen
                Entry e = ENTRIES.get(id);
                if (e != null && e.state == State.FAILED && e.retryAt != Long.MAX_VALUE) ENTRIES.remove(id, e);
            }
        }
    }

    /** Nach einem Ingame-Account-Wechsel: eigenes Cape verwerfen; es wird über Wallet bzw. Sync neu ermittelt. */
    public static synchronized void onAccountChanged() {
        ownUuid32 = null;
        ownCapeId = null;
        ownUpdatedAt = 0L;
        WRAPPED.clear();
    }

    private static String localUuid32() {
        return CaeserAuth.currentSessionUuid32();
    }

    /** Eigene Cape-ID des aktuellen Accounts oder {@code null}. */
    public static String ownCapeId() {
        String local = localUuid32();
        return local != null && local.equals(ownUuid32) ? ownCapeId : null;
    }

    /** Cape-ID eines Spielers (UUID als 32 Hex) oder {@code null}. */
    public static String capeFor(String uuid32) {
        if (uuid32 == null) return null;
        String local = localUuid32();
        if (uuid32.equals(local)) return ownCapeId();
        if (!CosmeticsConfig.capesShowOthers) return null;
        return CaeserPlayerSyncManager.getVerifiedCape(uuid32);
    }

    private static String uuid32(UUID uuid) {
        String s = UUID32.get(uuid);
        if (s == null) {
            if (UUID32.size() > 2048) UUID32.clear();
            s = CaeserApi.uuid32(uuid);
            if (s != null) UUID32.put(uuid, s);
        }
        return s;
    }

    // ==========================================
    // SkinTextures
    // ==========================================

    /**
     * Setzt das Caeser-Cape des Spielers in die {@link SkinTextures}, sofern eine ID bekannt und die Textur geladen
     * ist. Skin und Modell bleiben unverändert. Ohne Caeser-Cape bleibt das Vanilla-Cape. Nie {@code null} für
     * {@code base != null}, wirft nie.
     */
    public static SkinTextures apply(UUID uuid, SkinTextures base) {
        if (base == null || uuid == null) return base;
        try {
            String id = capeFor(uuid32(uuid));
            Identifier tex = id != null ? textureFor(id) : null;
            if (tex == null) return stripForeign(base);
            boolean elytraOption = CosmeticsConfig.capeOnElytra;
            Wrapped w = WRAPPED.get(uuid);
            if (w != null && w.base() == base && w.tex().equals(tex) && w.elytraOption() == elytraOption) {
                return w.result();
            }
            if (w != null && w.result() == base) return base; // schon eingesetzt (doppelter Aufruf)
            Entry entry = ENTRIES.get(id);
            AssetInfo.TextureAsset capeAsset = entry != null && entry.asset != null ? entry.asset : new AssetInfo.TextureAssetInfo(tex, tex);
            AssetInfo.TextureAsset elytra = base.elytra();
            if (elytra == null || isCaeserAsset(elytra)) {
                // Keine eigene Elytra-Textur: Cape-Motiv (Option an) bzw. normale Elytra (Option aus)
                elytra = elytraOption ? capeAsset : DEFAULT_ELYTRA;
            }
            SkinTextures result = new SkinTextures(base.body(), capeAsset, elytra, base.model(), base.secure());
            if (WRAPPED.size() > 512) WRAPPED.clear();
            WRAPPED.put(uuid, new Wrapped(base, tex, elytraOption, result));
            return result;
        } catch (Throwable t) {
            return base;
        }
    }

    /**
     * Entfernt Caeser-Cape-Texturen, die aus einer fremden {@link SkinTextures} übernommen wurden (z. B. Skin-Protect
     * kopiert die Texturen eines anderen Spielers), damit kein fremdes oder bereits freigegebenes Caeser-Cape hängen bleibt.
     */
    private static SkinTextures stripForeign(SkinTextures base) {
        boolean capeOurs = isCaeserAsset(base.cape());
        boolean elytraOurs = isCaeserAsset(base.elytra());
        if (!capeOurs && !elytraOurs) return base;
        return new SkinTextures(base.body(), capeOurs ? null : base.cape(), elytraOurs ? null : base.elytra(),
                base.model(), base.secure());
    }

    /** Stammt die Textur von diesem Manager (Caeser-Cape oder die ersatzweise gesetzte normale Elytra)? */
    public static boolean isCaeserAsset(AssetInfo.TextureAsset asset) {
        if (asset == null) return false;
        if (asset == DEFAULT_ELYTRA) return true;
        Identifier p = asset.texturePath();
        return p != null && "bameclient".equals(p.getNamespace()) && p.getPath().startsWith("capes/");
    }

    // ==========================================
    // Textur laden
    // ==========================================

    /** Textur des eigenen Caeser-Capes (für den Vorschau-Spieler) oder {@code null}; startet das Laden. */
    public static Identifier ownTexture() {
        String id = ownCapeId();
        return id != null ? textureFor(id) : null;
    }

    /** Identifier der geladenen Textur oder {@code null}; startet das Laden im Hintergrund. Render-Thread-tauglich. */
    public static Identifier textureFor(String id) {
        if (id == null) return null;
        long now = System.currentTimeMillis();
        Entry e = ENTRIES.get(id);
        if (e != null) {
            switch (e.state) {
                case READY -> {
                    e.lastUsed = now;
                    return e.textureId;
                }
                case LOADING -> {
                    return null;
                }
                case FAILED -> {
                    if (now < e.retryAt) return null;
                    ENTRIES.remove(id, e);
                }
            }
        }
        Entry fresh = new Entry(id);
        if (ENTRIES.putIfAbsent(id, fresh) == null) {
            try {
                IO.execute(() -> load(fresh));
            } catch (Throwable t) {
                fail(fresh, RETRY_NETWORK_MS);
            }
        }
        return null;
    }

    private static Path cacheDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("bameclient").resolve("cape-cache");
    }

    /** Hintergrund-Thread: Cache, sonst Download; prüfen, dekodieren, dann auf dem Haupt-Thread registrieren. */
    private static void load(Entry entry) {
        String id = entry.id;
        try {
            Path dir = cacheDir();
            byte[] data = CapeFiles.readCached(dir, id);
            boolean fromCache = data != null;
            if (data == null) {
                data = download(entry);
                if (data == null) return; // fail() schon gesetzt
            }
            CapeFiles.PngSize size = CapeFiles.validate(data);
            if (size == null) {
                LOGGER.warn("Discarding invalid Caeser cape {} (not a PNG or wrong size)", id);
                fail(entry, Long.MAX_VALUE);
                return;
            }
            NativeImage image;
            try {
                image = NativeImage.read(data);
            } catch (Throwable t) {
                image = null;
            }
            if (image == null || image.getWidth() != size.width() || image.getHeight() != size.height()) {
                if (image != null) image.close();
                LOGGER.warn("Discarding undecodable Caeser cape {}", id);
                fail(entry, Long.MAX_VALUE);
                return;
            }
            if (!fromCache) CapeFiles.writeCached(dir, id, data);
            register(entry, image);
        } catch (Throwable t) {
            LOGGER.debug("Loading Caeser cape {} failed ({})", id, t.getClass().getSimpleName());
            fail(entry, RETRY_NETWORK_MS);
        }
    }

    private static byte[] download(Entry entry) {
        String id = entry.id;
        if (!CaeserApi.isConfigured()) {
            fail(entry, RETRY_HTTP_MS);
            return null;
        }
        String path = "/v1/capes/" + URLEncoder.encode(id, StandardCharsets.UTF_8) + ".png";
        // Eigenes Cape (evtl. noch in Prüfung) mit Bearer-Token; freigegebene Capes sind öffentlich.
        String token = null;
        if (id.equals(ownCapeId()) && CaeserAuth.canLogin()) {
            try {
                token = CaeserAuth.ensureLoggedIn().get(DOWNLOAD_WAIT_MS, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
                token = null; // ohne Token versuchen (klappt bei freigegebenen Capes)
            }
        }
        try {
            return CaeserApi.getBytes(path, token, CapeFiles.MAX_BYTES).get(DOWNLOAD_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            CaeserApi.ApiException api = CaeserApi.unwrap(t);
            if ("TOO_LARGE".equals(api.code)) {
                LOGGER.warn("Discarding Caeser cape {} (larger than 1 MB)", id);
                fail(entry, Long.MAX_VALUE);
            } else if (api.status == 0) {
                fail(entry, RETRY_NETWORK_MS);
            } else {
                LOGGER.debug("Caeser cape {} not available (HTTP {})", id, api.status);
                fail(entry, RETRY_HTTP_MS);
            }
            return null;
        }
    }

    private static void fail(Entry entry, long retryAfterMs) {
        entry.retryAt = retryAfterMs == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + retryAfterMs;
        entry.state = State.FAILED;
    }

    private static void register(Entry entry, NativeImage image) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            image.close();
            fail(entry, Long.MAX_VALUE);
            return;
        }
        String key = CapeFiles.storageKey(entry.id);
        Identifier texId = Identifier.of("bameclient", "capes/" + key);
        mc.execute(() -> {
            try {
                if (ENTRIES.get(entry.id) != entry) {
                    image.close(); // inzwischen verworfen
                    return;
                }
                mc.getTextureManager().registerTexture(texId, new NativeImageBackedTexture(() -> "caeser_cape_" + key, image));
                entry.textureId = texId;
                entry.asset = new AssetInfo.TextureAssetInfo(texId, texId);
                entry.lastUsed = System.currentTimeMillis();
                entry.state = State.READY;
                WRAPPED.clear();
            } catch (Throwable t) {
                try { image.close(); } catch (Throwable ignored) {}
                LOGGER.warn("Could not register Caeser cape texture {} ({})", entry.id, t.getClass().getSimpleName());
                fail(entry, Long.MAX_VALUE);
            }
        });
    }

    // ==========================================
    // Speicher
    // ==========================================

    /** Haupt-Thread, aus dem Client-Tick: Texturen freigeben, die 5 Minuten von keinem Spieler genutzt wurden. */
    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastTickAt < TICK_INTERVAL_MS) return;
        lastTickAt = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean removed = false;
        for (Entry e : ENTRIES.values()) {
            if (e.state == State.READY && now - e.lastUsed > UNLOAD_AFTER_MS) {
                if (ENTRIES.remove(e.id, e)) {
                    removed = true;
                    try {
                        if (mc != null && e.textureId != null) mc.getTextureManager().destroyTexture(e.textureId);
                    } catch (Throwable t) {
                        LOGGER.debug("Could not free Caeser cape texture ({})", t.getClass().getSimpleName());
                    }
                }
            }
        }
        if (removed) WRAPPED.clear();
    }

    // ==========================================
    // Vorschau-Spieler im Cosmetics-Menü
    // ==========================================

    /**
     * Zeichnet das eigene Caeser-Cape am Vorschau-Spieler (gleiche Matrizen wie das Spielermodell im GUI-Renderer).
     * Ohne geladenes Cape passiert nichts.
     */
    public static void renderPreviewCape(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        try {
            Identifier tex = ownTexture();
            if (tex == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (previewCapeModel == null) {
                if (mc.getLoadedEntityModels() == null) return;
                previewCapeModel = new PlayerCapeModel(mc.getLoadedEntityModels().getModelPart(EntityModelLayers.PLAYER_CAPE));
            }
            ModelPart cape = previewCapeModel.body.getChild("cape");
            cape.resetTransform();
            // Wie Vanilla im Stand: 6° vom Rücken weg (Vanilla dreht um -X, weil das Cape um Y gedreht ist)
            cape.pitch = (float) Math.toRadians(-6.0);
            matrices.push();
            previewCapeModel.render(matrices, vertexConsumers.getBuffer(RenderLayers.entitySolid(tex)), light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        } catch (Throwable t) {
            LOGGER.debug("Preview cape render failed ({})", t.getClass().getSimpleName());
        }
    }
}
