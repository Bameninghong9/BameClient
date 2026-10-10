package com.bame.client.emotes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class EmoteAnimationLoader {

    private static final Map<String, KeyframeAnimation> CACHE = new HashMap<>();

    public static final String JUGGLE_ANIMATION_PATH = "emotes/football_freestyle/football_freestyle.animation.json";

    public static KeyframeAnimation getOrLoad(String path) {
        if (CACHE.containsKey(path)) {
            return CACHE.get(path);
        }

        KeyframeAnimation anim = load(path);
        if (anim != null) {
            CACHE.put(path, anim);
        }
        return anim;
    }

    public static KeyframeAnimation getJuggleAnimation() {
        return getOrLoad(JUGGLE_ANIMATION_PATH);
    }

    public static KeyframeAnimation load(String path) {
        // 1. Try Minecraft resource manager
        try {
            Identifier id = Identifier.of("bameclient", path);
            Optional<net.minecraft.resource.Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().getInputStream()) {
                    return parse(in);
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Fallback to classpath stream
        try (InputStream in = EmoteAnimationLoader.class.getResourceAsStream("/assets/bameclient/" + path)) {
            if (in != null) {
                return parse(in);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    public static KeyframeAnimation parse(InputStream in) {
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.has("animations")) return null;

            JsonObject animations = root.getAsJsonObject("animations");
            for (Map.Entry<String, JsonElement> animEntry : animations.entrySet()) {
                JsonObject animObj = animEntry.getValue().getAsJsonObject();
                String name = animEntry.getKey();

                boolean loop = animObj.has("loop") && animObj.get("loop").getAsBoolean();
                float length = animObj.has("animation_length") ? animObj.get("animation_length").getAsFloat() : 1.0F;

                KeyframeAnimation anim = new KeyframeAnimation(name, length, loop);

                if (animObj.has("bones")) {
                    JsonObject bones = animObj.getAsJsonObject("bones");
                    for (Map.Entry<String, JsonElement> boneEntry : bones.entrySet()) {
                        String boneName = boneEntry.getKey();
                        BoneTrack track = anim.getOrCreateBone(boneName);
                        JsonObject boneObj = boneEntry.getValue().getAsJsonObject();

                        if (boneObj.has("rotation")) {
                            parseChannel(boneObj.get("rotation"), track.rotationFrames);
                        }
                        if (boneObj.has("position")) {
                            parseChannel(boneObj.get("position"), track.positionFrames);
                        }
                    }
                }

                return anim; // Return first animation
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return null;
    }

    private static void parseChannel(JsonElement elem, java.util.List<Keyframe> list) {
        if (!elem.isJsonObject()) return;
        JsonObject obj = elem.getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            try {
                float time = Float.parseFloat(entry.getKey());
                JsonElement val = entry.getValue();

                float x = 0.0F;
                float y = 0.0F;
                float z = 0.0F;
                String lerpMode = "catmullrom";

                if (val.isJsonArray()) {
                    JsonArray arr = val.getAsJsonArray();
                    if (arr.size() >= 3) {
                        x = arr.get(0).getAsFloat();
                        y = arr.get(1).getAsFloat();
                        z = arr.get(2).getAsFloat();
                    }
                } else if (val.isJsonObject()) {
                    JsonObject valObj = val.getAsJsonObject();
                    if (valObj.has("lerp_mode")) {
                        lerpMode = valObj.get("lerp_mode").getAsString();
                    }

                    if (valObj.has("post")) {
                        JsonElement post = valObj.get("post");
                        if (post.isJsonArray()) {
                            JsonArray arr = post.getAsJsonArray();
                            if (arr.size() >= 3) {
                                x = arr.get(0).getAsFloat();
                                y = arr.get(1).getAsFloat();
                                z = arr.get(2).getAsFloat();
                            }
                        } else if (post.isJsonObject() && post.getAsJsonObject().has("vector")) {
                            JsonArray arr = post.getAsJsonObject().getAsJsonArray("vector");
                            if (arr.size() >= 3) {
                                x = arr.get(0).getAsFloat();
                                y = arr.get(1).getAsFloat();
                                z = arr.get(2).getAsFloat();
                            }
                        }
                    } else if (valObj.has("vector")) {
                        JsonArray arr = valObj.getAsJsonArray("vector");
                        if (arr.size() >= 3) {
                            x = arr.get(0).getAsFloat();
                            y = arr.get(1).getAsFloat();
                            z = arr.get(2).getAsFloat();
                        }
                    }
                }

                list.add(new Keyframe(time, x, y, z, lerpMode));
            } catch (Throwable ignored) {
            }
        }

        list.sort((a, b) -> Float.compare(a.time, b.time));
    }
}
