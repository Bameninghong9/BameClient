package com.bame.client.emotes;

import java.util.ArrayList;
import java.util.List;

public class BoneTrack {
    public final String boneName;
    public final List<Keyframe> rotationFrames = new ArrayList<>();
    public final List<Keyframe> positionFrames = new ArrayList<>();

    public BoneTrack(String boneName) {
        this.boneName = boneName;
    }

    public void sampleRotation(float t, boolean loop, float animLength, float[] out) {
        sample(rotationFrames, t, loop, animLength, out);
    }

    public void samplePosition(float t, boolean loop, float animLength, float[] out) {
        sample(positionFrames, t, loop, animLength, out);
    }

    private static void sample(List<Keyframe> frames, float t, boolean loop, float animLength, float[] out) {
        if (frames.isEmpty()) {
            out[0] = 0.0F;
            out[1] = 0.0F;
            out[2] = 0.0F;
            return;
        }

        if (frames.size() == 1) {
            Keyframe single = frames.get(0);
            out[0] = single.x;
            out[1] = single.y;
            out[2] = single.z;
            return;
        }

        if (loop && animLength > 0.0F) {
            t = t % animLength;
            if (t < 0.0F) t += animLength;
        }

        int size = frames.size();
        if (t <= frames.get(0).time) {
            Keyframe first = frames.get(0);
            out[0] = first.x;
            out[1] = first.y;
            out[2] = first.z;
            return;
        }

        if (t >= frames.get(size - 1).time) {
            Keyframe last = frames.get(size - 1);
            out[0] = last.x;
            out[1] = last.y;
            out[2] = last.z;
            return;
        }

        int i = 0;
        for (int k = 0; k < size - 1; k++) {
            if (t >= frames.get(k).time && t <= frames.get(k + 1).time) {
                i = k;
                break;
            }
        }

        Keyframe p1 = frames.get(i);
        Keyframe p2 = frames.get(i + 1);

        float dt = p2.time - p1.time;
        float alpha = dt > 1e-6F ? (t - p1.time) / dt : 0.0F;

        if ("linear".equalsIgnoreCase(p1.lerpMode)) {
            out[0] = p1.x + (p2.x - p1.x) * alpha;
            out[1] = p1.y + (p2.y - p1.y) * alpha;
            out[2] = p1.z + (p2.z - p1.z) * alpha;
            return;
        }

        Keyframe p0;
        if (i > 0) {
            p0 = frames.get(i - 1);
        } else if (loop) {
            p0 = frames.get(size - 1);
        } else {
            p0 = p1;
        }

        Keyframe p3;
        if (i + 2 < size) {
            p3 = frames.get(i + 2);
        } else if (loop) {
            p3 = frames.get(0);
        } else {
            p3 = p2;
        }

        out[0] = catmullRom(alpha, p0.x, p1.x, p2.x, p3.x);
        out[1] = catmullRom(alpha, p0.y, p1.y, p2.y, p3.y);
        out[2] = catmullRom(alpha, p0.z, p1.z, p2.z, p3.z);
    }

    private static float catmullRom(float t, float p0, float p1, float p2, float p3) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * (
                (2.0F * p1) +
                (-p0 + p2) * t +
                (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 +
                (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3
        );
    }
}
