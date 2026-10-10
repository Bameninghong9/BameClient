package com.bame.client.emotes;

public class Keyframe {
    public final float time;
    public final float x;
    public final float y;
    public final float z;
    public final String lerpMode;

    public Keyframe(float time, float x, float y, float z, String lerpMode) {
        this.time = time;
        this.x = x;
        this.y = y;
        this.z = z;
        this.lerpMode = lerpMode != null ? lerpMode : "catmullrom";
    }
}
