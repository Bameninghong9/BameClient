package com.bame.client.module;

import com.google.common.collect.LinkedHashMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class SkinProtectModule {
    public static boolean enabled = false;
    public static boolean expanded = true;
    public static int keyBind = -1;
    public static int currentSkinIndex = 0;
    public static volatile RealSkinEntry currentSkinEntry = null;
    public static String targetInitialSkinName = null;

    public static class RealSkinEntry {
        public final String name;
        public final UUID uuid;
        private volatile SkinTextures textures;

        public RealSkinEntry(String name, UUID uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        public RealSkinEntry(String name, UUID uuid, SkinTextures textures) {
            this.name = name;
            this.uuid = uuid;
            this.textures = textures;
        }

        public boolean hasCustomTextures() {
            return textures != null;
        }

        public SkinTextures getTextures() {
            if (textures != null) {
                return textures;
            }
            return DefaultSkinHelper.getSkinTextures(uuid);
        }

        public void setTextures(SkinTextures textures) {
            if (textures != null) {
                this.textures = textures;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RealSkinEntry that = (RealSkinEntry) o;
            return Objects.equals(uuid, that.uuid) || name.equalsIgnoreCase(that.name);
        }

        @Override
        public int hashCode() {
            return uuid != null ? uuid.hashCode() : name.toLowerCase(Locale.ROOT).hashCode();
        }
    }

    private static final List<RealSkinEntry> FAMOUS_SKINS = new ArrayList<>();
    public static final List<RealSkinEntry> CUSTOM_SEARCHED_SKINS = new CopyOnWriteArrayList<>();
    private static final Set<UUID> FETCHED_UUIDS = ConcurrentHashMap.newKeySet();
    private static boolean initialized = false;

    private static final ExecutorService SKIN_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "SkinProtect-Fetcher");
        t.setDaemon(true);
        return t;
    });

    private static void registerFamous(String name, String uuidStr) {
        FAMOUS_SKINS.add(new RealSkinEntry(name, UUID.fromString(uuidStr)));
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        registerFamous("Dream", "ec70bcaf-702f-4bb8-b48d-276fa52a780c");
        registerFamous("SteGi", "c9e390ee-bb1f-46b7-84b8-a8a0f6c5aaad");
        registerFamous("Technoblade", "b876ec32-e396-476b-a115-8438d83c67d4");
        registerFamous("BastiGHG", "f6f3a530-6c39-4098-96a0-6bdf4f3afc70");
        registerFamous("TommyInnit", "e80e8194-323e-4142-9851-5e1bcb8a3508");
        registerFamous("GeorgeNotFound", "bd3dd5a4-0438-4699-b2fd-36f518154b41");
        registerFamous("Sapnap", "c66f7c8a-ed0c-4469-90b0-421d8ff7ca49");
        registerFamous("DanTDM", "77cc85ae-388a-46ec-a535-9e2ffef71b29");
        registerFamous("CaptainSparklez", "5f820c39-5883-4392-b174-3125ac05e38c");
        registerFamous("Grian", "5f8eb73b-25be-4c5a-a50f-d27d65e30ca0");
        registerFamous("Mumbo", "ac224782-efff-4296-b08c-dbde8e47abdb");
        registerFamous("Skeppy", "8e176c5a-c26d-4c14-8efe-77b598b8b3ea");
        registerFamous("BadBoyHalo", "26bdff37-fec8-48f1-980f-66bf69ee751c");
        registerFamous("PhilzA", "f327197b-e9dd-4491-82cd-6cef1d59346e");
        registerFamous("FitMC", "a3be358a-966f-4043-bfab-2acbfe03e051");
        registerFamous("Etho", "93b459be-ce4f-4700-b457-c1aa91b3b687");
        registerFamous("BdoubleO100", "7163fbce-39ac-4a02-b836-a991c45d2dd1");
        registerFamous("ilmango", "52ea9354-99ed-4b06-bec2-331e7c0f6f57");
        registerFamous("Notch", "069a79f4-44e9-4726-a5be-fca90e38aaf5");
        registerFamous("jeb_", "853c80ef-3c37-49fd-aa49-938b674adae6");
        registerFamous("Dinnerbone", "61699b2e-d327-4a01-9f1e-0ea8c3f06bc6");
        registerFamous("WadZee", "0906b58b-ef58-4dec-8815-0fe30897a1fd");
        registerFamous("veni", "e8fa6a05-1c45-4029-bb51-de77818619d6");
        registerFamous("CastCrafter", "459dbcee-d3ea-4abc-903c-fdcae3065dd3");
        registerFamous("Paluten", "6e61a2a9-3cb7-4687-9013-f275dd7357bd");
        registerFamous("GermanLetsPlay", "2d76acc5-46c7-47ea-83e4-2ea1fd1285de");
        registerFamous("PEWDIEPIE", "68ff6f93-f818-47f7-a445-1bd3a67dde37");

        if (targetInitialSkinName != null && !targetInitialSkinName.trim().isEmpty()) {
            setSkinByName(targetInitialSkinName, null);
        } else if (currentSkinEntry == null && !FAMOUS_SKINS.isEmpty()) {
            currentSkinEntry = FAMOUS_SKINS.get(0);
        }
        preloadFamousSkins();
    }

    public static void preloadFamousSkins() {
        SKIN_EXECUTOR.submit(() -> {
            for (RealSkinEntry entry : FAMOUS_SKINS) {
                if (!entry.hasCustomTextures()) {
                    fetchSkinAsync(entry);
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ignored) {}
                }
            }
        });
    }

    public static void addCustomSkin(String name, UUID uuid) {
        RealSkinEntry entry = new RealSkinEntry(name, uuid);
        if (!CUSTOM_SEARCHED_SKINS.contains(entry)) {
            CUSTOM_SEARCHED_SKINS.add(entry);
            fetchSkinAsync(entry);
        }
    }

    public static void setSkinByName(String name, Consumer<Boolean> callback) {
        if (name == null || name.trim().isEmpty()) {
            if (callback != null) callback.accept(false);
            return;
        }
        String targetName = name.trim();

        // 1. Check if already in the list
        List<RealSkinEntry> all = getAllAvailableEntries();
        for (int i = 0; i < all.size(); i++) {
            RealSkinEntry e = all.get(i);
            if (e.name.equalsIgnoreCase(targetName)) {
                currentSkinEntry = e;
                currentSkinIndex = i;
                if (!e.hasCustomTextures()) {
                    fetchSkinAsync(e);
                }
                com.bame.client.BameClientConfig.save();
                if (callback != null) callback.accept(true);
                return;
            }
        }

        // 2. Query UUID and skin asynchronously
        SKIN_EXECUTOR.submit(() -> {
            UUID foundUuid = null;
            String properName = targetName;

            MinecraftClient client = MinecraftClient.getInstance();

            // Check if online player matches
            if (client != null && client.getNetworkHandler() != null) {
                Collection<PlayerListEntry> playerList = client.getNetworkHandler().getPlayerList();
                if (playerList != null) {
                    for (PlayerListEntry ple : playerList) {
                        if (ple.getProfile() != null && targetName.equalsIgnoreCase(ple.getProfile().name())) {
                            foundUuid = ple.getProfile().id();
                            properName = ple.getProfile().name();
                            break;
                        }
                    }
                }
            }

            // Check Mojang GameProfileRepository
            if (foundUuid == null && client != null && client.getApiServices() != null && client.getApiServices().profileRepository() != null) {
                try {
                    Optional<com.mojang.authlib.yggdrasil.response.NameAndId> opt =
                        client.getApiServices().profileRepository().findProfileByName(targetName);
                    if (opt.isPresent()) {
                        foundUuid = opt.get().id();
                        properName = opt.get().name();
                    }
                } catch (Throwable ignored) {}
            }

            // Direct HTTP request to Mojang API
            if (foundUuid == null) {
                try {
                    URL url = URI.create("https://api.mojang.com/users/profiles/minecraft/" + targetName).toURL();
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(4000);
                    conn.setReadTimeout(4000);
                    if (conn.getResponseCode() == 200) {
                        try (InputStream in = conn.getInputStream()) {
                            String jsonStr = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                            com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                            if (obj.has("id")) {
                                String rawId = obj.get("id").getAsString();
                                foundUuid = UUID.fromString(rawId.replaceFirst(
                                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
                            }
                            if (obj.has("name")) {
                                properName = obj.get("name").getAsString();
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // Fallback: offline player UUID
            if (foundUuid == null) {
                foundUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName).getBytes(StandardCharsets.UTF_8));
            }

            RealSkinEntry newEntry = new RealSkinEntry(properName, foundUuid);
            if (!CUSTOM_SEARCHED_SKINS.contains(newEntry)) {
                CUSTOM_SEARCHED_SKINS.add(newEntry);
            }

            fetchSkinAsync(newEntry);
            currentSkinEntry = newEntry;

            List<RealSkinEntry> updatedList = getAllAvailableEntries();
            for (int i = 0; i < updatedList.size(); i++) {
                if (updatedList.get(i).equals(newEntry)) {
                    currentSkinIndex = i;
                    break;
                }
            }

            com.bame.client.BameClientConfig.save();
            if (callback != null) {
                callback.accept(true);
            }
        });
    }

    public static void fetchSkinAsync(RealSkinEntry entry) {
        if (entry.hasCustomTextures()) return;
        if (!FETCHED_UUIDS.add(entry.uuid)) return;

        SKIN_EXECUTOR.submit(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null) {
                    FETCHED_UUIDS.remove(entry.uuid);
                    return;
                }

                GameProfile fullProfile = null;

                // 1. Try Mojang Session Service
                if (client.getApiServices() != null && client.getApiServices().sessionService() != null) {
                    try {
                        ProfileResult result = client.getApiServices().sessionService().fetchProfile(entry.uuid, false);
                        if (result != null && result.profile() != null) {
                            fullProfile = result.profile();
                        }
                    } catch (Throwable ignored) {}
                }

                // 2. Direct HTTP Fallback if session service didn't return textures
                if (fullProfile == null || fullProfile.properties().get("textures").isEmpty()) {
                    try {
                        String cleanUuid = entry.uuid.toString().replace("-", "");
                        URL url = URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + cleanUuid).toURL();
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(4000);
                        if (conn.getResponseCode() == 200) {
                            try (InputStream in = conn.getInputStream()) {
                                String jsonStr = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                                com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(jsonStr).getAsJsonObject();
                                if (obj.has("properties")) {
                                    com.google.gson.JsonArray props = obj.getAsJsonArray("properties");
                                    for (com.google.gson.JsonElement el : props) {
                                        com.google.gson.JsonObject prop = el.getAsJsonObject();
                                        if ("textures".equals(prop.get("name").getAsString())) {
                                            String value = prop.get("value").getAsString();
                                            String sig = prop.has("signature") ? prop.get("signature").getAsString() : null;
                                            PropertyMap map = new PropertyMap(LinkedHashMultimap.create());
                                            map.put("textures", new Property("textures", value, sig));
                                            fullProfile = new GameProfile(entry.uuid, entry.name, map);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                if (fullProfile != null && client.getSkinProvider() != null) {
                    client.getSkinProvider().fetchSkinTextures(fullProfile).thenAccept(opt -> {
                        if (opt != null && opt.isPresent()) {
                            entry.setTextures(opt.get());
                        } else {
                            FETCHED_UUIDS.remove(entry.uuid);
                        }
                    });
                } else {
                    FETCHED_UUIDS.remove(entry.uuid);
                }
            } catch (Throwable t) {
                FETCHED_UUIDS.remove(entry.uuid);
            }
        });
    }

    public static List<RealSkinEntry> getAllAvailableEntries() {
        if (!initialized) init();

        List<RealSkinEntry> result = new ArrayList<>(FAMOUS_SKINS);
        result.addAll(CUSTOM_SEARCHED_SKINS);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getNetworkHandler() != null) {
            Collection<PlayerListEntry> playerList = client.getNetworkHandler().getPlayerList();
            if (playerList != null) {
                for (PlayerListEntry ple : playerList) {
                    if (ple.getProfile() != null && ple.getProfile().name() != null) {
                        UUID uuid = ple.getProfile().id();
                        if (client.player != null && uuid.equals(client.player.getUuid())) {
                            continue;
                        }
                        SkinTextures textures = ple.getSkinTextures();
                        RealSkinEntry onlineEntry = new RealSkinEntry(ple.getProfile().name(), uuid, textures);
                        if (!result.contains(onlineEntry)) {
                            result.add(onlineEntry);
                        }
                    }
                }
            }
        }

        return result;
    }

    public static RealSkinEntry getCurrentEntry() {
        if (currentSkinEntry == null) {
            if (!initialized) init();
            if (currentSkinEntry == null) {
                currentSkinEntry = !FAMOUS_SKINS.isEmpty() ? FAMOUS_SKINS.get(0)
                        : new RealSkinEntry("Steve", UUID.nameUUIDFromBytes("Steve".getBytes()), DefaultSkinHelper.getSteve());
            }
        }

        if (!currentSkinEntry.hasCustomTextures()) {
            fetchSkinAsync(currentSkinEntry);
        }

        return currentSkinEntry;
    }

    public static SkinTextures getCurrentSkin() {
        return getCurrentEntry().getTextures();
    }

    public static String getCurrentSkinName() {
        return getCurrentEntry().name;
    }

    public static void shuffle() {
        List<RealSkinEntry> list = getAllAvailableEntries();
        if (list.isEmpty()) return;
        if (list.size() == 1) {
            currentSkinEntry = list.get(0);
            return;
        }
        RealSkinEntry prev = currentSkinEntry;
        RealSkinEntry next;
        int attempts = 0;
        do {
            next = list.get(ThreadLocalRandom.current().nextInt(list.size()));
            attempts++;
        } while (next.equals(prev) && attempts < 10);

        currentSkinEntry = next;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equals(currentSkinEntry)) {
                currentSkinIndex = i;
                break;
            }
        }

        if (!currentSkinEntry.hasCustomTextures()) {
            fetchSkinAsync(currentSkinEntry);
        }
        com.bame.client.BameClientConfig.save();
    }

    public static void reset() {
        enabled = false;
        keyBind = -1;
        currentSkinIndex = 0;
        if (!FAMOUS_SKINS.isEmpty()) {
            currentSkinEntry = FAMOUS_SKINS.get(0);
        }
        com.bame.client.BameClientConfig.save();
    }
}
