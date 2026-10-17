#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Shader Fog (Caeser Client): animierter Himmel. Werte aus DynamicTransforms:
//   ColorModulator.rgb = Effektfarbe, ColorModulator.a = Deckkraft
//   ModelOffset = (Zeit in Sekunden, Tageshelligkeit 0..1, Modus 0 Nebula / 1 Bloom / 2 Saturn / 3 Drain)
//   TextureMat[0] = (Geschwindigkeit, Skalierung, Intensität, Planetengröße), TextureMat[1].x = Horizont im Nebel

in vec3 skyDir;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = hash13(i);
    float n100 = hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += amp * noise3(p);
        p = p * 2.03 + vec3(17.1, 9.2, 3.7);
        amp *= 0.5;
    }
    return sum / 0.96875;
}

// Sterne: in einem Raster um die Kugel trägt jede Zelle mit kleiner Wahrscheinlichkeit einen Stern
float starLayer(vec3 d, float density, float chance, float t) {
    vec3 p = d * density;
    vec3 cell = floor(p);
    float h = hash13(cell);
    if (h > chance) return 0.0;
    float b = h / chance;
    float r = length(fract(p) - 0.5);
    float twinkle = 0.75 + 0.25 * sin(t * (1.5 + 3.0 * b) + h * 80.0);
    return smoothstep(0.42, 0.0, r) * (0.35 + 0.65 * b) * twinkle;
}

// Sternschnuppe: alle paar Sekunden eine helle Bahn über den oberen Himmel
float meteor(vec3 d, float t) {
    if (d.y < 0.05) return 0.0;
    vec2 uv = d.xz / (1.0 + d.y);
    float period = 6.5;
    float k = floor(t / period);
    float s = fract(t / period);
    float life = 0.18;
    if (s > life) return 0.0;
    float a = hash13(vec3(k, 3.1, 7.7)) * 6.2831853;
    vec2 start = vec2(cos(a), sin(a)) * (0.25 + 0.35 * hash13(vec3(k, 1.7, 2.3)));
    vec2 dir = normalize(vec2(cos(a + 2.4), sin(a + 2.4)));
    vec2 head = start + dir * (s / life) * 0.9;
    vec2 tail = head - dir * 0.22;
    vec2 pa = uv - tail;
    vec2 ba = head - tail;
    float hseg = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    float dist = length(pa - ba * hseg);
    float fade = sin(3.14159 * s / life);
    return smoothstep(0.004, 0.0, dist) * hseg * fade * 1.4;
}

vec3 nebula(vec3 d, float t, float scale, float intensity, vec3 tint) {
    vec3 p = d * scale * 0.35;
    vec3 q = vec3(fbm(p + vec3(0.0, 0.0, t * 0.05)),
                  fbm(p + vec3(5.2, 1.3, -t * 0.04)),
                  fbm(p + vec3(2.1, 7.7, t * 0.03)));
    vec3 r = vec3(fbm(p + 3.0 * q + vec3(1.7, 9.2, t * 0.06)),
                  fbm(p + 3.0 * q + vec3(8.3, 2.8, -t * 0.05)),
                  fbm(p + 3.0 * q + vec3(4.4, 6.1, t * 0.04)));
    float f = fbm(p + 2.6 * r);
    vec3 deep = tint * 0.62;
    vec3 bright = mix(tint, vec3(1.0), 0.18) * 1.12;
    vec3 col = mix(deep, bright, smoothstep(0.30, 0.75, f));
    float wisps = smoothstep(0.60, 0.90, f + 0.15 * (r.x - 0.5));
    col = mix(col, vec3(1.0), wisps * (0.35 + 0.50 * clamp(intensity, 0.0, 1.0)));
    return col;
}

vec3 bloom(vec3 d, float t, float scale, float intensity, vec3 tint) {
    vec3 p = d * scale * 0.25;
    float n = fbm(p + vec3(0.0, t * 0.04, 0.0));
    float n2 = fbm(p * 1.6 + vec3(t * 0.03, 0.0, 3.0));
    float field = n * 2.2 + n2 * 0.9;
    float iso = abs(fract(field) - 0.5);
    float mask = smoothstep(0.42, 0.68, fbm(p * 0.7 + vec3(9.0, t * 0.02, 1.0)));
    float line = exp(-iso * 70.0) * mask;
    float glow = exp(-iso * 10.0) * mask;
    vec3 col = vec3(0.004, 0.008, 0.02) + tint * 0.04;
    col += tint * glow * (0.35 + 0.65 * intensity);
    col += mix(tint, vec3(1.0), 0.6) * line * (0.6 + 0.8 * intensity);
    return col;
}

// Ringplanet: Schnitt der Blickrichtung mit Kugel und Ringscheibe (Planet in 100 Blöcken Entfernung)
vec4 saturn(vec3 d, float size) {
    vec3 fwd = normalize(vec3(-0.45, 0.62, -0.64));
    vec3 c = fwd * 100.0;
    float radius = 13.0 * size;
    vec3 n = normalize(vec3(0.28, 1.0, 0.16));
    // Licht von vorne oben links (vom Betrachter aus): der Planet ist fast ganz beleuchtet, Schatten rechts unten
    vec3 right = normalize(cross(fwd, vec3(0.0, 1.0, 0.0)));
    vec3 up = normalize(cross(right, fwd));
    vec3 light = normalize(-fwd * 0.9 + up * 0.55 - right * 0.45);

    float b = dot(d, c);
    float disc = b * b - (dot(c, c) - radius * radius);
    float tPlanet = disc > 0.0 ? b - sqrt(disc) : 1e9;

    float tRing = 1e9;
    float ringAlpha = 0.0;
    vec3 ringCol = vec3(0.0);
    float denom = dot(d, n);
    if (abs(denom) > 1e-4) {
        float tr = dot(c, n) / denom;
        if (tr > 0.0) {
            vec3 hit = d * tr;
            float rr = length(hit - c) / radius;
            if (rr > 1.25 && rr < 2.35) {
                float x = (rr - 1.25) / 1.1;
                float bands = 0.55 + 0.45 * sin(x * 140.0) * sin(x * 37.0 + 1.0);
                float gap = smoothstep(0.0, 0.015, abs(x - 0.62) - 0.025);
                float edge = smoothstep(0.0, 0.04, x) * smoothstep(1.0, 0.94, x);
                ringAlpha = clamp((0.25 + 0.75 * bands) * gap * edge * 0.9, 0.0, 1.0);
                // Schatten des Planeten auf dem Ring
                vec3 toPlanet = c - hit;
                float sb = dot(toPlanet, light);
                float shadow = 1.0;
                if (sb > 0.0) {
                    float sd = length(toPlanet - light * sb);
                    shadow = smoothstep(radius * 0.92, radius * 1.05, sd) * 0.75 + 0.25;
                }
                ringCol = vec3(0.86, 0.83, 0.78) * (0.55 + 0.45 * bands) * shadow;
                tRing = tr;
            }
        }
    }

    vec3 col = vec3(0.0);
    float a = 0.0;
    float closest = length(cross(d, c));
    if (b > 0.0) {
        float halo = exp(-max(closest - radius, 0.0) / (radius * 0.16)) * 0.6;
        col += vec3(1.0, 0.82, 0.55) * halo;
        a = max(a, halo);
    }
    if (ringAlpha > 0.0 && tRing > tPlanet) {
        col = mix(col, ringCol, ringAlpha);
        a = max(a, ringAlpha);
    }
    if (tPlanet < 1e8) {
        vec3 p = d * tPlanet;
        vec3 nn = normalize(p - c);
        float diff = clamp(dot(nn, light), 0.0, 1.0);
        float lat = dot(nn, n);
        float turb = fbm(nn * 4.0);
        float band = 0.5 + 0.3 * sin(lat * 24.0 + turb * 2.5) + 0.2 * sin(lat * 61.0 + 1.3);
        vec3 pc = mix(vec3(0.62, 0.42, 0.22), vec3(1.0, 0.92, 0.74), clamp(band, 0.0, 1.0));
        pc *= 0.12 + 1.0 * diff;
        float rim = pow(1.0 - clamp(dot(nn, -d), 0.0, 1.0), 3.0);
        pc += vec3(1.0, 0.85, 0.6) * rim * 0.35 * (0.3 + diff);
        col = pc;
        a = 1.0;
    }
    if (ringAlpha > 0.0 && tRing <= tPlanet) {
        col = mix(col, ringCol, ringAlpha);
        a = max(a, ringAlpha);
    }
    return vec4(col, a);
}

vec3 space(vec3 d, float t, float scale, float intensity, vec3 tint, float size) {
    float neb = fbm(d * scale * 0.45 + vec3(0.0, 0.0, t * 0.01));
    float wisps = smoothstep(0.55, 0.95, neb);
    vec3 col = vec3(0.004, 0.005, 0.008);
    col += vec3(0.07, 0.075, 0.085) * wisps * (0.4 + intensity);
    col += tint * 0.025 * wisps;
    col += vec3(starLayer(d, 320.0, 0.012, t) + 1.4 * starLayer(d, 110.0, 0.01, t));
    col += vec3(meteor(d, t));
    vec4 planet = saturn(d, size);
    col = col * (1.0 - planet.a) + planet.rgb;
    return col;
}

vec3 drain(vec3 d, float t, float scale, float intensity, vec3 tint) {
    float r = acos(clamp(d.y, -1.0, 1.0));
    float ang = atan(d.z, d.x);
    float swirl = ang + r * 2.2 - t * 0.12;
    vec3 p = vec3(cos(swirl) * r, sin(swirl) * r, r * 0.6 - t * 0.04) * scale * 0.55;
    float n = fbm(p);
    float n2 = fbm(p * 2.4 + vec3(4.0, 1.0, t * 0.05));
    vec3 col = tint * (0.10 + 0.08 * n) + vec3(0.008, 0.015, 0.024);
    col += tint * pow(n2, 3.0) * (0.15 + 0.7 * intensity);
    return col;
}

void main() {
    vec3 d = normalize(skyDir);
    float t = ModelOffset.x * TextureMat[0][0];
    float day = ModelOffset.y;
    float mode = ModelOffset.z;
    float scale = max(TextureMat[0][1], 0.1);
    float intensity = TextureMat[0][2];
    float size = TextureMat[0][3];
    vec3 tint = ColorModulator.rgb;

    vec3 col;
    if (mode < 0.5) {
        col = nebula(d, t, scale, intensity, tint) * mix(0.12, 1.0, day);
    } else if (mode < 1.5) {
        col = bloom(d, t, scale, intensity, tint);
    } else if (mode < 2.5) {
        col = space(d, t, scale, intensity, tint, size);
    } else {
        col = drain(d, t, scale, intensity, tint);
    }

    // Horizont geht in den Nebel über, damit die ferne Landschaft nahtlos anschließt
    float horizon = 1.0 - smoothstep(-0.04, 0.20, d.y);
    col = mix(col, FogColor.rgb, horizon * TextureMat[1][0] * FogColor.a);

    fragColor = vec4(col, ColorModulator.a);
}
