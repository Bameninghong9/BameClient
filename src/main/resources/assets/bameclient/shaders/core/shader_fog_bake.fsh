#version 330

#moj_import <minecraft:dynamictransforms.glsl>

// Shader Fog (Caeser Client), Backen: berechnet die aufwendigen Teile des Himmels für alle Blickrichtungen in eine
// Panorama-Textur. Läuft höchstens 30-mal pro Sekunde, nicht in jedem Bild. Werte aus DynamicTransforms:
//   ColorModulator.rgb = Effektfarbe, ModelOffset = (Animationszeit, -, Modus 0 Nebula / 1 Bloom / 2 Saturn / 3 Drain)
//   TextureMat[0] = (Geschwindigkeit, Skalierung, Intensität, Planetengröße)

uniform sampler2D Sampler1;

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;

// 3D-Wertrauschen mit einem Texturzugriff: R/G = Rauschen A in Ebene z und z+1, B/A = Rauschen B (unabhängig)
vec2 noise2(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = p.xy + vec2(37.0, 17.0) * p.z + f.xy;
    vec4 s = textureLod(Sampler1, (uv + 0.5) / 256.0, 0.0);
    return mix(s.xz, s.yw, f.z);
}

float fbm(vec3 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float total = 0.0;
    for (int i = 0; i < octaves; i++) {
        sum += amp * noise2(p).x;
        total += amp;
        p = p * 2.03 + vec3(17.1, 9.2, 3.7);
        amp *= 0.5;
    }
    return sum / total;
}

// Zwei unabhängige fbm-Werte zum Preis von einem
vec2 fbm2(vec3 p, int octaves) {
    vec2 sum = vec2(0.0);
    float amp = 0.5;
    float total = 0.0;
    for (int i = 0; i < octaves; i++) {
        sum += amp * noise2(p);
        total += amp;
        p = p * 2.03 + vec3(17.1, 9.2, 3.7);
        amp *= 0.5;
    }
    return sum / total;
}

vec3 nebula(vec3 d, float t, float scale, float intensity, vec3 tint) {
    vec3 p = d * scale * 0.35;
    vec3 q = vec3(fbm2(p + vec3(0.0, 0.0, t * 0.05), 4), fbm(p + vec3(2.1, 7.7, t * 0.03), 4));
    vec3 r = vec3(fbm2(p + 3.0 * q + vec3(1.7, 9.2, t * 0.06), 4), fbm(p + 3.0 * q + vec3(4.4, 6.1, t * 0.04), 4));
    float f = fbm(p + 2.6 * r, 5);
    vec3 deep = tint * 0.62;
    vec3 bright = mix(tint, vec3(1.0), 0.18) * 1.12;
    vec3 col = mix(deep, bright, smoothstep(0.30, 0.75, f));
    float wisps = smoothstep(0.60, 0.90, f + 0.15 * (r.x - 0.5));
    return mix(col, vec3(1.0), wisps * (0.35 + 0.50 * clamp(intensity, 0.0, 1.0)));
}

vec3 bloom(vec3 d, float t, float scale, float intensity, vec3 tint) {
    vec3 p = d * scale * 0.25;
    vec2 nm = fbm2(p + vec3(0.0, t * 0.04, 0.0), 5);
    float n2 = fbm(p * 1.6 + vec3(t * 0.03, 0.0, 3.0), 4);
    float field = nm.x * 2.2 + n2 * 0.9;
    float iso = abs(fract(field) - 0.5);
    float mask = smoothstep(0.42, 0.68, fbm(p * 0.7 + vec3(9.0, t * 0.02, 1.0), 3));
    float line = exp(-iso * 70.0) * mask;
    float glow = exp(-iso * 10.0) * mask;
    vec3 col = vec3(0.004, 0.008, 0.02) + tint * 0.04;
    col += tint * glow * (0.35 + 0.65 * intensity);
    col += mix(tint, vec3(1.0), 0.6) * line * (0.6 + 0.8 * intensity);
    return col;
}

// Hintergrund für Saturn: dunkler Weltraum mit schwachen Nebelschleiern (Sterne und Planet kommen pro Pixel dazu)
vec3 spaceBackground(vec3 d, float t, float scale, float intensity, vec3 tint) {
    float neb = fbm(d * scale * 0.45 + vec3(0.0, 0.0, t * 0.01), 5);
    float wisps = smoothstep(0.55, 0.95, neb);
    vec3 col = vec3(0.004, 0.005, 0.008);
    col += vec3(0.07, 0.075, 0.085) * wisps * (0.4 + intensity);
    col += tint * 0.025 * wisps;
    return col;
}

vec3 drain(vec3 d, float t, float scale, float intensity, vec3 tint) {
    float r = acos(clamp(d.y, -1.0, 1.0));
    float ang = atan(d.z, d.x);
    float swirl = ang + r * 2.2 - t * 0.12;
    vec3 p = vec3(cos(swirl) * r, sin(swirl) * r, r * 0.6 - t * 0.04) * scale * 0.55;
    vec2 n = fbm2(p, 4);
    float n2 = fbm(p * 2.4 + vec3(4.0, 1.0, t * 0.05), 4);
    vec3 col = tint * (0.10 + 0.08 * n.x) + vec3(0.008, 0.015, 0.024);
    col += tint * pow(n2, 3.0) * (0.15 + 0.7 * intensity);
    return col;
}

void main() {
    // Panorama-Koordinate -> Blickrichtung (Gegenstück zu shader_fog.fsh)
    float lon = (texCoord.x - 0.5) * 2.0 * PI;
    float theta = texCoord.y * PI;
    vec3 d = vec3(sin(theta) * cos(lon), cos(theta), sin(theta) * sin(lon));

    float t = ModelOffset.x;
    float mode = ModelOffset.z;
    float scale = max(TextureMat[0][1], 0.1);
    float intensity = TextureMat[0][2];
    vec3 tint = ColorModulator.rgb;

    vec3 col;
    if (mode < 0.5) {
        col = nebula(d, t, scale, intensity, tint);
    } else if (mode < 1.5) {
        col = bloom(d, t, scale, intensity, tint);
    } else if (mode < 2.5) {
        col = spaceBackground(d, t, scale, intensity, tint);
    } else {
        col = drain(d, t, scale, intensity, tint);
    }
    fragColor = vec4(col, 1.0);
}
