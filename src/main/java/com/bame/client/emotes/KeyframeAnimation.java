package com.bame.client.emotes;

import java.util.HashMap;
import java.util.Map;

public class KeyframeAnimation {
    public final String name;
    public final float length;
    public final boolean loop;
    public final Map<String, BoneTrack> bones = new HashMap<>();

    public KeyframeAnimation(String name, float length, boolean loop) {
        this.name = name;
        this.length = length;
        this.loop = loop;
    }

    public BoneTrack getBone(String boneName) {
        return bones.get(boneName);
    }

    public BoneTrack getOrCreateBone(String boneName) {
        return bones.computeIfAbsent(boneName, BoneTrack::new);
    }

    public void sample(String boneName, float t, float[] rotOut, float[] posOut) {
        BoneTrack track = bones.get(boneName);
        if (track != null) {
            if (rotOut != null) track.sampleRotation(t, loop, length, rotOut);
            if (posOut != null) track.samplePosition(t, loop, length, posOut);
        } else {
            if (rotOut != null) {
                rotOut[0] = 0.0F; rotOut[1] = 0.0F; rotOut[2] = 0.0F;
            }
            if (posOut != null) {
                posOut[0] = 0.0F; posOut[1] = 0.0F; posOut[2] = 0.0F;
            }
        }
    }
}
