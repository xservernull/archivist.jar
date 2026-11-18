#version 150

// neverforgetyou sky (#117). Note: never name a function noise1..noise4, they are GLSL built-ins (#127). Procedural, a pure function of view direction and GameTime.
// Gentle and dreamy, not horror: deep navy, many soft stars, a faint milky way,
// slow aurora curtains (green / teal, a little purple), a pale moon.
// GameTime runs 0..1 over 24000 ticks; every animated term uses a whole number of cycles per
// GameTime period, so the loop is seamless. UNVERIFIED: not compiled or run in game.

uniform float GameTime;

in vec3 dir;
out vec4 fragColor;

const float TAU = 6.2831853;
const vec3 HORIZON = vec3(0.137, 0.173, 0.302); // biome fog #232C4D
const vec3 ZENITH  = vec3(0.035, 0.060, 0.150); // a little deeper than the biome sky #182742
const vec3 MOON_DIR = normalize(vec3(-0.35, 0.75, 0.45));

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash11(i.x + i.y * 57.0);
    float b = hash11(i.x + 1.0 + i.y * 57.0);
    float c = hash11(i.x + (i.y + 1.0) * 57.0);
    float d = hash11(i.x + 1.0 + (i.y + 1.0) * 57.0);
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * vnoise(p);
        p = p * 2.03 + 17.0;
        a *= 0.5;
    }
    return v;
}

// One layer of stars: the direction is scaled onto a 3D grid, one star per cell at a random spot.
vec3 starLayer(vec3 d, float scale, float size, float chance, float layer) {
    vec3 p = d * scale;
    vec3 cell = floor(p);
    vec3 local = fract(p) - 0.5;
    float h = hash31(cell + layer * 7.7);
    if (h > chance) return vec3(0.0);
    vec3 jitter = vec3(hash31(cell + 1.3), hash31(cell + 2.9), hash31(cell + 4.1)) - 0.5;
    float dist = length(local - jitter * 0.7);
    float core = 1.0 - smoothstep(0.0, size, dist);
    float twinkle = 0.65 + 0.35 * sin(GameTime * TAU * floor(150.0 + h * 250.0) + h * TAU);
    // mostly white, some warm, some cool
    float t = hash31(cell + 9.1);
    vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.92, 0.78), t);
    return tint * core * twinkle * (0.5 + 1.5 * hash31(cell + 5.5));
}

vec3 milkyWay(vec3 d) {
    vec3 n = normalize(vec3(0.35, 0.25, 1.0));
    float band = dot(d, n);
    float width = exp(-band * band * 14.0);
    vec2 uv = vec2(atan(d.x, d.z) * 3.0, d.y * 6.0);
    float cloud = fbm(uv * 1.6) * fbm(uv * 3.1 + 5.0);
    float lanes = smoothstep(0.25, 0.7, fbm(uv * 2.4 + 11.0));
    float glow = width * (0.25 + cloud * 1.2) * (1.0 - 0.5 * lanes);
    return mix(vec3(0.20, 0.24, 0.42), vec3(0.50, 0.42, 0.62), cloud) * glow * 0.55;
}

// Aurora: a few drifting curtains in azimuth / elevation, vertical rays inside each.
vec3 aurora(vec3 d) {
    if (d.y < 0.02) return vec3(0.0);
    float az = atan(d.x, d.z);
    float el = d.y;
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float drift = GameTime * TAU * (2.0 + fi);
        float wave = sin(az * (2.0 + fi) + drift + fbm(vec2(az * 1.5 + fi * 4.0, 0.0)) * 3.0);
        float center = 0.22 + 0.14 * fi + 0.07 * wave;
        float below = smoothstep(center - 0.03, center + 0.01, el);      // sharp lower edge
        float above = exp(-max(el - center, 0.0) * (5.0 - fi));          // soft fade upward
        float rays = 0.55 + 0.45 * vnoise(vec2(az * 38.0 + fi * 13.0, el * 2.0 + GameTime * TAU * 3.0));
        float shimmer = 0.75 + 0.25 * sin(GameTime * TAU * (60.0 + 20.0 * fi) + az * 5.0);
        float amount = below * above * rays * shimmer;
        vec3 low = vec3(0.15, 0.95, 0.55);   // green at the bottom of the curtain
        vec3 high = mix(vec3(0.20, 0.75, 0.90), vec3(0.65, 0.35, 0.95), fi * 0.5); // teal, then purple
        vec3 color = mix(low, high, clamp((el - center) * 4.0, 0.0, 1.0));
        sum += color * amount * (0.55 - 0.12 * fi);
    }
    // keep the aurora off the horizon
    return sum * smoothstep(0.02, 0.18, d.y);
}

void main() {
    vec3 d = normalize(dir);

    float up = clamp(d.y, 0.0, 1.0);
    vec3 col = mix(HORIZON, ZENITH, pow(up, 0.55));
    if (d.y < 0.0) col = mix(HORIZON, HORIZON * 0.6, clamp(-d.y * 4.0, 0.0, 1.0));

    float aboveHorizon = smoothstep(-0.02, 0.12, d.y);

    vec3 stars = vec3(0.0);
    stars += starLayer(d, 120.0, 0.10, 0.22, 1.0) * 0.9;   // dense, small
    stars += starLayer(d, 55.0, 0.14, 0.18, 2.0) * 1.2;    // medium
    stars += starLayer(d, 22.0, 0.20, 0.10, 3.0) * 2.0;    // rare, bright
    col += (stars + milkyWay(d)) * aboveHorizon;

    col += aurora(d);

    // pale moon with a soft halo
    float m = dot(d, MOON_DIR);
    float disc = smoothstep(0.9994, 0.9998, m);
    float halo = pow(max(m, 0.0), 80.0) * 0.35 + pow(max(m, 0.0), 12.0) * 0.06;
    col += vec3(0.88, 0.92, 1.0) * disc + vec3(0.45, 0.55, 0.85) * halo;

    fragColor = vec4(col, 1.0);
}
