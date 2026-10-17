#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

// Shader Fog (Caeser Client), jedes Bild: liest den gebackenen Himmel (Sampler0, Panorama aus shader_fog_bake) für
// die Blickrichtung und ergänzt scharfe Sterne, Sternschnuppen und den Ringplaneten. Werte aus DynamicTransforms:
//   ColorModulator.a = Deckkraft, ModelOffset = (Animationszeit, Tageshelligkeit 0..1, Modus 0 Nebula / 1 Bloom /
//   2 Saturn / 3 Drain), TextureMat[0].w = Planetengröße, TextureMat[1].x = Horizont im Nebel

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec3 skyDir;

out vec4 fragColor;

const float PI = 3.14159265;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// 3D-Wertrauschen mit einem Texturzugriff (siehe shader_fog_bake.fsh)
float noise1(vec3 x) {
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    vec2 uv = p.xy + vec2(37.0, 17.0) * p.z + f.xy;
    vec2 s = textureLod(Sampler1, (uv + 0.5) / 256.0, 0.0).xy;
    return mix(s.x, s.y, f.z);
}

float fbm3(vec3 p) {
    float sum = 0.5 * noise1(p);
    sum += 0.25 * noise1(p * 2.03 + vec3(17.1, 9.2, 3.7));
    sum += 0.125 * noise1(p * 4.12 + vec3(3.4, 21.7, 8.8));
    return sum / 0.875;
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

// Ringplanet: Schnitt der Blickrichtung mit Kugel und Ringscheibe (Planet in 100 Blöcken Entfernung)
vec4 saturn(vec3 d, float size) {
    vec3 fwd = normalize(vec3(-0.45, 0.62, -0.64));
    vec3 c = fwd * 100.0;
    float radius = 13.0 * size;
    // Weit weg vom Planeten (mehr als der Ring) gibt es nichts zu zeichnen
    float b = dot(d, c);
    float closest = length(cross(d, c));
    if (b <= 0.0 || closest > radius * 2.6) return vec4(0.0);

    vec3 n = normalize(vec3(0.28, 1.0, 0.16));
    // Licht von vorne oben links (vom Betrachter aus): der Planet ist fast ganz beleuchtet, Schatten rechts unten
    vec3 right = normalize(cross(fwd, vec3(0.0, 1.0, 0.0)));
    vec3 up = normalize(cross(right, fwd));
    vec3 light = normalize(-fwd * 0.9 + up * 0.55 - right * 0.45);

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
    float halo = exp(-max(closest - radius, 0.0) / (radius * 0.16)) * 0.6;
    col += vec3(1.0, 0.82, 0.55) * halo;
    a = max(a, halo);
    if (ringAlpha > 0.0 && tRing > tPlanet) {
        col = mix(col, ringCol, ringAlpha);
        a = max(a, ringAlpha);
    }
    if (tPlanet < 1e8) {
        vec3 p = d * tPlanet;
        vec3 nn = normalize(p - c);
        float diff = clamp(dot(nn, light), 0.0, 1.0);
        float lat = dot(nn, n);
        float turb = fbm3(nn * 4.0);
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

void main() {
    vec3 d = normalize(skyDir);
    float t = ModelOffset.x;
    float day = ModelOffset.y;
    float mode = ModelOffset.z;
    float size = TextureMat[0][3];

    // Blickrichtung -> Panorama-Koordinate (Gegenstück zu shader_fog_bake.fsh)
    vec2 uv = vec2(atan(d.z, d.x) * (0.5 / PI) + 0.5, acos(clamp(d.y, -1.0, 1.0)) / PI);
    vec3 col = textureLod(Sampler0, uv, 0.0).rgb;

    if (mode < 0.5) {
        col *= mix(0.12, 1.0, day);
    } else if (mode > 1.5 && mode < 2.5) {
        col += vec3(starLayer(d, 320.0, 0.012, t) + 1.4 * starLayer(d, 110.0, 0.01, t));
        col += vec3(meteor(d, t));
        vec4 planet = saturn(d, size);
        col = col * (1.0 - planet.a) + planet.rgb;
    }

    // Horizont geht in den Nebel über, damit die ferne Landschaft nahtlos anschließt
    float horizon = 1.0 - smoothstep(-0.04, 0.20, d.y);
    col = mix(col, FogColor.rgb, horizon * TextureMat[1][0] * FogColor.a);

    fragColor = vec4(col, ColorModulator.a);
}
