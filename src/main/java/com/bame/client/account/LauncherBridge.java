package com.bame.client.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lokale "Account-Brücke" zum Caeser Client Launcher.
 * <p>
 * Der Launcher setzt beim Spielstart {@code CAESER_BRIDGE_URL} und {@code CAESER_BRIDGE_TOKEN}.
 * Alle Aufrufe laufen asynchron; Ergebnisse werden per {@link MinecraftClient#execute(Runnable)} auf den
 * Haupt-Thread zurückgegeben. Das Bridge-Token und Access-Tokens werden nie geloggt oder gespeichert.
 */
public final class LauncherBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("CaeserAccountBridge");

    public static final String MSG_UNREACHABLE = "Der Caeser Client Launcher ist nicht erreichbar.";
    public static final String MSG_NOT_AVAILABLE = "Account-Wechsel geht nur, wenn Minecraft über den Caeser Client Launcher gestartet wurde.";
    public static final String MSG_LOGIN_RUNNING = "Es läuft bereits eine Anmeldung.";
    private static final String MSG_INVALID_RESPONSE = "Ungültige Antwort vom Caeser Client Launcher.";

    private static final Pattern URL_PATTERN = Pattern.compile("^http://127\\.0\\.0\\.1:([0-9]{1,5})$");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final Pattern ID_PATTERN = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String BASE_URL;
    private static final String TOKEN;

    static {
        String url = null;
        String token = null;
        try {
            String rawUrl = System.getenv("CAESER_BRIDGE_URL");
            String rawToken = System.getenv("CAESER_BRIDGE_TOKEN");
            if (rawUrl != null && rawToken != null) {
                Matcher m = URL_PATTERN.matcher(rawUrl);
                if (m.matches() && TOKEN_PATTERN.matcher(rawToken).matches()) {
                    int port = Integer.parseInt(m.group(1));
                    if (port >= 1 && port <= 65535) {
                        url = rawUrl;
                        token = rawToken;
                    }
                }
            }
        } catch (Throwable ignored) {}
        BASE_URL = url;
        TOKEN = token;
        if (BASE_URL == null) {
            LOGGER.info("Caeser account bridge not available (game was not started by the Caeser Client Launcher).");
        }
    }

    private static volatile HttpClient httpClient;
    /** null = noch unbekannt, sonst Ergebnis der letzten Anfrage an die Bridge. */
    private static volatile Boolean lastReachable = null;

    private LauncherBridge() {}

    public record AccountInfo(String id, String name, boolean active) {}

    public record LoginState(boolean running, String error, String lastAddedId) {}

    public record AccountList(List<AccountInfo> accounts, LoginState login) {}

    /** Mögliche Werte von {@code skinSync.status} in der Antwort von {@code POST /v1/accounts/switch}. */
    public static final String SKIN_SYNC_UPLOADED = "uploaded";
    public static final String SKIN_SYNC_UNCHANGED = "unchanged";
    public static final String SKIN_SYNC_SKIPPED = "skipped";
    public static final String SKIN_SYNC_PENDING = "pending";
    public static final String SKIN_SYNC_FAILED = "failed";

    /**
     * Ergebnis eines erfolgreichen Account-Wechsels für die Oberfläche (ohne Access-Token).
     *
     * @param skinSyncStatus einer der {@code SKIN_SYNC_*}-Werte oder {@code null} (älterer Launcher/ungültig)
     * @param skinSyncError  bereinigte Fehlermeldung des Launchers oder {@code null}
     */
    public record SwitchSuccess(String name, String skinSyncStatus, String skinSyncError) {}

    /**
     * Antwort von {@code POST /v1/accounts/switch}. Enthält das Access-Token und bleibt deshalb nur im Speicher;
     * {@link #toString()} gibt bewusst kein Token aus.
     */
    static final class SwitchResult {
        final String id;
        final String name;
        final String accessToken;
        final String xuid;
        final String clientId;
        /** Skin-Sync des Launchers; jeweils {@code null}, wenn {@code skinSync} fehlt oder ungültig ist. */
        final String skinSyncStatus;
        final String skinSyncSkinId;
        final String skinSyncError;

        SwitchResult(String id, String name, String accessToken, String xuid, String clientId,
                     String skinSyncStatus, String skinSyncSkinId, String skinSyncError) {
            this.id = id;
            this.name = name;
            this.accessToken = accessToken;
            this.xuid = xuid;
            this.clientId = clientId;
            this.skinSyncStatus = skinSyncStatus;
            this.skinSyncSkinId = skinSyncSkinId;
            this.skinSyncError = skinSyncError;
        }

        @Override
        public String toString() {
            return "SwitchResult[id=" + id + ", name=" + name + ", skinSync=" + skinSyncStatus
                    + (skinSyncSkinId != null ? ", skinId=" + skinSyncSkinId : "") + "]";
        }
    }

    /** Fehler mit einer Meldung, die dem Spieler direkt angezeigt werden darf. */
    private static final class BridgeException extends RuntimeException {
        BridgeException(String message) {
            super(message, null, false, false);
        }
    }

    public static boolean isAvailable() {
        return BASE_URL != null && TOKEN != null;
    }

    /** true = Bridge hat zuletzt geantwortet, false = nicht erreichbar/nicht verfügbar, null = noch unbekannt. */
    public static Boolean getLastReachable() {
        if (!isAvailable()) return Boolean.FALSE;
        return lastReachable;
    }

    /** Prüft im Hintergrund, ob die Bridge antwortet (für den Status-Punkt am Knopf). */
    public static void refreshReachability() {
        if (!isAvailable()) return;
        fetchAccounts(list -> {}, err -> {});
    }

    public static void fetchAccounts(Consumer<AccountList> onSuccess, Consumer<String> onError) {
        if (!isAvailable()) {
            deliver(() -> onError.accept(MSG_NOT_AVAILABLE));
            return;
        }
        send(request("/v1/accounts").GET().build(), (status, body) -> {
            if (status != 200) throw new BridgeException(errorMessage(status, body));
            return parseAccounts(body);
        }, onSuccess, onError);
    }

    /**
     * Holt beim Launcher die Anmeldedaten für {@code id} und übergibt sie sofort an den {@link SessionSwitcher}.
     * Das Ergebnis (inkl. Access-Token) wird nirgends gespeichert.
     *
     * @param onSuccess erhält Namen des neuen Accounts und das Skin-Sync-Ergebnis des Launchers (ohne Token)
     */
    public static void switchTo(String id, Consumer<SwitchSuccess> onSuccess, Consumer<String> onError) {
        if (!isAvailable()) {
            deliver(() -> onError.accept(MSG_NOT_AVAILABLE));
            return;
        }
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            deliver(() -> onError.accept(MSG_INVALID_RESPONSE));
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("id", id);
        HttpRequest req = request("/v1/accounts/switch")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();
        send(req, (status, body) -> {
            if (status != 200) throw new BridgeException(errorMessage(status, body));
            return parseSwitch(body);
        }, result -> SessionSwitcher.switchSession(result, onSuccess, onError), onError);
    }

    /** Startet im Launcher die Microsoft-Anmeldung für einen neuen Account. */
    public static void startLogin(Runnable onStarted, Consumer<String> onError) {
        if (!isAvailable()) {
            deliver(() -> onError.accept(MSG_NOT_AVAILABLE));
            return;
        }
        HttpRequest req = request("/v1/accounts/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .build();
        send(req, (status, body) -> {
            if (status == 409) throw new BridgeException(MSG_LOGIN_RUNNING);
            if (status != 202 && status != 200) throw new BridgeException(errorMessage(status, body));
            return Boolean.TRUE;
        }, ok -> onStarted.run(), onError);
    }

    // ------------------------------------------------------------------
    // Skin speichern (Spieler-Auswahlrad im Chat)
    // ------------------------------------------------------------------

    /** Body-Limit des Launchers nur für {@code POST /v1/skins/import}. */
    public static final int SKIN_IMPORT_MAX_BODY = 512 * 1024;

    public enum SkinImportStatus {
        /** 201: neu in der Skin-Bibliothek. */
        CREATED,
        /** 200 mit {@code duplicate:true}: schon vorhanden (kommt auch bei voller Bibliothek). */
        DUPLICATE,
        /** Fehler-Antwort des Launchers; {@code message} ist anzeigbar. */
        ERROR,
        /** Keine Verbindung (oder älterer Launcher ohne diese Route): lokal speichern. */
        UNREACHABLE,
        /** Nicht über den Launcher gestartet (Umgebungsvariablen fehlen): lokal speichern. */
        NOT_AVAILABLE,
        /** Anfrage wäre größer als das Body-Limit: gar nicht erst senden, lokal speichern. */
        TOO_LARGE
    }

    /** Ergebnis von {@link #importSkin}; {@code message} nur bei {@link SkinImportStatus#ERROR} gesetzt (ohne Token). */
    public record SkinImportResult(SkinImportStatus status, String message) {}

    /**
     * {@code POST /v1/skins/import} mit {@code { name, data: "data:image/png;base64,...", variant }}. Synchron: nur aus
     * einem Hintergrund-Thread aufrufen. Jede Anfrage zählt fürs Rate-Limit des Launchers (10/Minute), deshalb keine
     * automatischen Wiederholungen – außer genau einmal bei einem reinen Verbindungsfehler (Anfrage kam nicht an).
     * Das Token steht nur im Authorization-Header und wird nie geloggt.
     */
    public static SkinImportResult importSkin(String name, byte[] png, String variant) {
        if (!isAvailable()) return new SkinImportResult(SkinImportStatus.NOT_AVAILABLE, null);
        JsonObject payload = new JsonObject();
        payload.addProperty("name", name);
        payload.addProperty("data", com.bame.client.skin.SkinUrls.pngDataUrl(png));
        payload.addProperty("variant", variant);
        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        if (body.length > SKIN_IMPORT_MAX_BODY) return new SkinImportResult(SkinImportStatus.TOO_LARGE, null);
        HttpRequest req = request("/v1/skins/import")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> resp;
        try {
            resp = sendRetryingConnectOnce(req);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastReachable = Boolean.FALSE;
            return new SkinImportResult(SkinImportStatus.UNREACHABLE, null);
        } catch (Exception e) {
            lastReachable = Boolean.FALSE;
            LOGGER.debug("Skin import request failed: {}", e.getClass().getSimpleName());
            return new SkinImportResult(SkinImportStatus.UNREACHABLE, null);
        }
        lastReachable = Boolean.TRUE;
        int status = resp.statusCode();
        String respBody = resp.body();
        if (status == 200 || status == 201) {
            Boolean duplicate = null;
            try {
                JsonElement el = JsonParser.parseString(respBody);
                if (el.isJsonObject() && el.getAsJsonObject().has("duplicate")
                        && el.getAsJsonObject().get("duplicate").isJsonPrimitive()) {
                    duplicate = el.getAsJsonObject().get("duplicate").getAsBoolean();
                }
            } catch (Throwable ignored) {}
            boolean dup = com.bame.client.skin.SkinImportReply.isDuplicate(status, duplicate);
            return new SkinImportResult(dup ? SkinImportStatus.DUPLICATE : SkinImportStatus.CREATED, null);
        }
        if (status == 404) {
            // Älterer Launcher ohne diese Route: wie "nicht verbunden" behandeln (Skin lokal sichern)
            return new SkinImportResult(SkinImportStatus.UNREACHABLE, null);
        }
        String launcherMessage = null;
        try {
            if (respBody != null && !respBody.isBlank()) {
                JsonElement el = JsonParser.parseString(respBody);
                if (el.isJsonObject()) launcherMessage = getString(el.getAsJsonObject(), "error");
            }
        } catch (Throwable ignored) {}
        String retryAfter = resp.headers().firstValue("Retry-After").orElse(null);
        return new SkinImportResult(SkinImportStatus.ERROR,
                com.bame.client.skin.SkinImportReply.errorMessage(status, launcherMessage, retryAfter));
    }

    /** Senden; nur wenn die Verbindung gar nicht zustande kam, genau ein zweiter Versuch (zählt nicht doppelt). */
    private static HttpResponse<String> sendRetryingConnectOnce(HttpRequest req) throws java.io.IOException, InterruptedException {
        try {
            return client().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
            Thread.sleep(400L);
            return client().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------
    // Intern
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface ResponseParser<T> {
        T parse(int status, String body);
    }

    private static HttpClient client() {
        HttpClient c = httpClient;
        if (c == null) {
            synchronized (LauncherBridge.class) {
                c = httpClient;
                if (c == null) {
                    c = HttpClient.newBuilder()
                            .connectTimeout(TIMEOUT)
                            .proxy(HttpClient.Builder.NO_PROXY)
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .version(HttpClient.Version.HTTP_1_1)
                            .build();
                    httpClient = c;
                }
            }
        }
        return c;
    }

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + TOKEN)
                .header("Accept", "application/json");
    }

    private static <T> void send(HttpRequest req, ResponseParser<T> parser, Consumer<T> onSuccess, Consumer<String> onError) {
        try {
            client().sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .handle((resp, err) -> {
                        if (err != null) {
                            lastReachable = Boolean.FALSE;
                            Throwable cause = unwrap(err);
                            LOGGER.debug("Account bridge request failed: {}", cause.getClass().getSimpleName());
                            deliver(() -> onError.accept(MSG_UNREACHABLE));
                            return null;
                        }
                        lastReachable = Boolean.TRUE;
                        T result;
                        try {
                            result = parser.parse(resp.statusCode(), resp.body());
                        } catch (BridgeException e) {
                            String msg = e.getMessage();
                            deliver(() -> onError.accept(msg));
                            return null;
                        } catch (Throwable t) {
                            deliver(() -> onError.accept(MSG_INVALID_RESPONSE));
                            return null;
                        }
                        deliver(() -> onSuccess.accept(result));
                        return null;
                    });
        } catch (Throwable t) {
            lastReachable = Boolean.FALSE;
            deliver(() -> onError.accept(MSG_UNREACHABLE));
        }
    }

    private static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof java.util.concurrent.ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }

    private static void deliver(Runnable r) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                LOGGER.warn("Account bridge callback failed: {}", t.getClass().getSimpleName());
            }
        });
    }

    /** Meldung aus {@code { "error": "..." }}, sonst eine allgemeine deutsche Meldung. */
    private static String errorMessage(int status, String body) {
        try {
            if (body != null && !body.isBlank()) {
                JsonElement el = JsonParser.parseString(body);
                if (el.isJsonObject()) {
                    String msg = getString(el.getAsJsonObject(), "error");
                    if (msg != null && !msg.isBlank()) return sanitize(msg);
                }
            }
        } catch (Throwable ignored) {}
        if (status == 401 || status == 403) return "Der Launcher hat die Anfrage abgelehnt.";
        if (status == 404) return "Dieser Account ist im Launcher nicht mehr gespeichert.";
        if (status == 429) return "Bitte warte kurz und versuche es erneut.";
        if (status == 502) return "Der Account konnte nicht angemeldet werden.";
        return "Der Launcher hat mit einem Fehler geantwortet (HTTP " + status + ").";
    }

    private static String sanitize(String msg) {
        String clean = msg.replaceAll("[\\p{Cntrl}§]", " ").trim();
        return clean.length() > 200 ? clean.substring(0, 200) + "…" : clean;
    }

    private static AccountList parseAccounts(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        List<AccountInfo> accounts = new ArrayList<>();
        if (root.has("accounts") && root.get("accounts").isJsonArray()) {
            JsonArray arr = root.getAsJsonArray("accounts");
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String id = normalizeId(getString(o, "id"));
                String name = getString(o, "name");
                if (id == null || name == null || name.isBlank()) continue;
                boolean active = o.has("active") && o.get("active").isJsonPrimitive() && o.get("active").getAsBoolean();
                accounts.add(new AccountInfo(id, sanitize(name), active));
            }
        }
        LoginState login = new LoginState(false, null, null);
        if (root.has("login") && root.get("login").isJsonObject()) {
            JsonObject l = root.getAsJsonObject("login");
            boolean running = l.has("running") && l.get("running").isJsonPrimitive() && l.get("running").getAsBoolean();
            String error = getString(l, "error");
            String lastAdded = normalizeId(getString(l, "lastAddedId"));
            login = new LoginState(running, error != null && !error.isBlank() ? sanitize(error) : null, lastAdded);
        }
        return new AccountList(Collections.unmodifiableList(accounts), login);
    }

    private static SwitchResult parseSwitch(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        String id = normalizeId(getString(root, "id"));
        String name = getString(root, "name");
        String token = getString(root, "accessToken");
        if (id == null || name == null || name.isBlank() || token == null || token.isBlank()) {
            throw new BridgeException(MSG_INVALID_RESPONSE);
        }
        String syncStatus = null;
        String syncSkinId = null;
        String syncError = null;
        try {
            // Optional (ältere Launcher senden es nicht); Fehler hier dürfen den Wechsel nie scheitern lassen.
            if (root.has("skinSync") && root.get("skinSync").isJsonObject()) {
                JsonObject sync = root.getAsJsonObject("skinSync");
                syncStatus = normalizeSkinSyncStatus(getString(sync, "status"));
                if (syncStatus != null) {
                    syncSkinId = normalizeSkinId(getString(sync, "skinId"));
                    String err = getString(sync, "error");
                    syncError = err != null && !err.isBlank() ? sanitize(err) : null;
                }
            }
        } catch (Throwable t) {
            syncStatus = null;
            syncSkinId = null;
            syncError = null;
        }
        return new SwitchResult(id, name.trim(), token, blankToNull(getString(root, "xuid")), blankToNull(getString(root, "clientId")),
                syncStatus, syncSkinId, syncError);
    }

    /** Bekannte {@code skinSync.status}-Werte, sonst {@code null}. */
    private static String normalizeSkinSyncStatus(String status) {
        if (status == null) return null;
        String s = status.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case SKIN_SYNC_UPLOADED, SKIN_SYNC_UNCHANGED, SKIN_SYNC_SKIPPED, SKIN_SYNC_PENDING, SKIN_SYNC_FAILED -> s;
            default -> null;
        };
    }

    /** Skin-ID des Launchers (z. B. "steve" oder eine eigene Skin-ID) unverändert übernehmen, wenn sie plausibel ist. */
    private static String normalizeSkinId(String skinId) {
        if (skinId == null || skinId.isBlank() || skinId.length() > 128) return null;
        for (int i = 0; i < skinId.length(); i++) {
            if (Character.isISOControl(skinId.charAt(i))) return null;
        }
        return skinId;
    }

    private static String normalizeId(String id) {
        if (id == null) return null;
        String clean = id.replace("-", "").trim().toLowerCase(Locale.ROOT);
        return ID_PATTERN.matcher(clean).matches() ? clean : null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String getString(JsonObject o, String key) {
        if (o == null || !o.has(key)) return null;
        JsonElement el = o.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return null;
        return el.getAsString();
    }

    /** Wandelt eine UUID ohne Bindestriche (32 Hex-Zeichen) in eine {@link java.util.UUID}. */
    static java.util.UUID parseUuid(String id) {
        String n = normalizeId(id);
        if (n == null) throw new IllegalArgumentException("invalid id");
        return new java.util.UUID(Long.parseUnsignedLong(n.substring(0, 16), 16), Long.parseUnsignedLong(n.substring(16), 16));
    }
}
