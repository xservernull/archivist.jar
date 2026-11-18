#version 150

// childhoodhome sky: one fixed, quiet sunset. Procedural, a pure function of view direction and GameTime.
// The sun sits just above the horizon (SUN_DIR), the sky goes from gold at the horizon through rose and
// violet to dusk blue overhead, long stretched clouds are lit orange from below, a few faint stars show high up.
// Never name a function noise1..noise4, they are GLSL built-ins. GameTime runs 0..1 over 24000 ticks; the only
// animated term is a sine of it (one cycle), so the loop is seamless. UNVERIFIED: not compiled or run in game.

uniform float GameTime;

in vec3 dir;
out vec4 fragColor;

const float TAU = 6.2831853;
const vec3 SUN_DIR = normalize(vec3(0.55, 0.07, -0.83));
const vec3 HORIZON = vec3(1.00, 0.66, 0.34);
const vec3 MID     = vec3(0.90, 0.45, 0.50);
const vec3 UPPER   = vec3(0.45, 0.30, 0.58);
const vec3 ZENITH  = vec3(0.17, 0.19, 0.44);
const vec3 BELOW   = vec3(0.55, 0.30, 0.30);

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

void main() {
    vec3 d = normalize(dir);
    float h = d.y;
    float sunDot = max(dot(d, SUN_DIR), 0.0);

    // vertical gradient
    vec3 col;
    if (h >= 0.0) {
        col = mix(HORIZON, MID, smoothstep(0.0, 0.18, h));
        col = mix(col, UPPER, smoothstep(0.12, 0.45, h));
        col = mix(col, ZENITH, smoothstep(0.40, 1.0, h));
    } else {
        col = mix(HORIZON * 0.8, BELOW, smoothstep(0.0, 0.25, -h));
    }

    // warm wash toward the sun, strongest near the horizon
    col += vec3(1.0, 0.45, 0.15) * pow(sunDot, 3.0) * 0.35 * exp(-abs(h) * 3.0);

    // faint stars high up
    vec3 p = d * 140.0;
    vec3 cell = floor(p);
    vec3 local = fract(p) - 0.5;
    float star = step(hash31(cell), 0.012) * smoothstep(0.18, 0.0, length(local));
    col += vec3(1.0, 0.95, 0.90) * star * smoothstep(0.45, 0.9, h) * 0.6;

    // sun glow and disc
    col += vec3(1.0, 0.60, 0.25) * pow(sunDot, 12.0) * 0.6;
    col += vec3(1.0, 0.85, 0.55) * pow(sunDot, 120.0) * 1.2;
    col = mix(col, vec3(1.0, 0.95, 0.80), smoothstep(0.9990, 0.9996, sunDot));

    // long clouds, lit from below
    if (h > 0.01) {
        float wob = sin(GameTime * TAU) * 0.35;
        vec2 uv = d.xz / (h + 0.12) * 0.9 + vec2(wob, 0.0);
        float n = fbm(uv * vec2(1.0, 2.2) + 3.0);
        float c = smoothstep(0.48, 0.72, n);
        c *= smoothstep(0.01, 0.10, h) * (1.0 - smoothstep(0.55, 0.95, h));
        float lit = clamp(pow(sunDot, 2.0) * 1.4 + 0.25 * exp(-h * 4.0), 0.0, 1.0);
        vec3 cloud = mix(vec3(0.38, 0.22, 0.45), vec3(1.0, 0.58, 0.36), lit);
        cloud += vec3(1.0, 0.80, 0.50) * pow(sunDot, 24.0) * 0.4;
        col = mix(col, cloud, c * 0.85);
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
