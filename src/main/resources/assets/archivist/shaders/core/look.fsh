#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform sampler2D BloomSampler; // blurred bright parts of the picture (quarter size), see bloom.fsh
uniform sampler2D AoSampler;    // half-resolution occlusion in the red channel, see ao.fsh (only read when Ao.x > 0)
uniform mat4 InverseProjMat;
uniform float GameTime; // vanilla fills it; Quiet Place freezes it, so the grain stands still there

uniform float Darkness; // flashlight: 0 = off, 1 = pitch black outside the light
uniform vec2 Beam;      // screen radius (1.0 = screen height) where the light starts / stops fading
uniform vec2 Reach;     // distance in blocks where the light starts / stops fading
uniform vec3 Tint;
uniform float Saturation;
uniform float Contrast;
uniform vec3 HazeColor;
uniform vec2 Fog;       // haze start / end in blocks, end 0 = off
uniform float Vignette;
uniform float Grain;
uniform float Bloom;     // glow strength, 0 = off
uniform vec3 Ao;        // ambient occlusion: strength (0 = off), only .x and .z are used here (radius is used by ao.fsh)
uniform float Flicker;  // brightness wobble amplitude, 0 = off
uniform float Breathe;  // vignette breathing amplitude, 0 = off
uniform float Warp;     // far terrain smears sideways, 0 = off

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

// how strongly a depth difference lowers an AO neighbour's weight in the upsample (bigger = sharper edges, more 2x2 blockiness)
const float AO_EDGE = 75.0;

// distance along the view axis for a depth value. The z and w rows of the inverse projection do not depend on x and y
// (symmetric perspective), so x = y = 0 is enough; only used to compare depths with each other.
float viewZ(float depth) {
    vec4 v = InverseProjMat * vec4(0.0, 0.0, depth * 2.0 - 1.0, 1.0);
    return abs(v.z / v.w);
}

// Depth-aware upsample of the half-resolution AO. Half-res pixel h stands for the full-res pixel 2*h (see ao.fsh),
// so full-res pixel f lies between h0 = floor(f/2) and h0 + 1. Each of the (up to) four neighbours is weighted by its
// bilinear weight divided by how different its depth is from this pixel's, so AO never leaks across a depth edge.
float upsampleAo(ivec2 f, float depth) {
    ivec2 full = textureSize(DepthSampler, 0);
    ivec2 hs = textureSize(AoSampler, 0);
    vec2 t = vec2(f) * 0.5;
    ivec2 h0 = ivec2(floor(t));
    vec2 fr = t - vec2(h0); // 0 or 0.5: an even pixel sits exactly on a half-res pixel
    float z0 = viewZ(depth);
    float sum = 0.0;
    float wsum = 0.0;
    for (int j = 0; j < 2; j++) {
        for (int i = 0; i < 2; i++) {
            float bw = (i == 0 ? 1.0 - fr.x : fr.x) * (j == 0 ? 1.0 - fr.y : fr.y);
            if (bw <= 0.0) continue;
            ivec2 h = min(h0 + ivec2(i, j), hs - 1);
            float zi = viewZ(texelFetch(DepthSampler, min(h * 2, full - 1), 0).r);
            float w = bw / (abs(zi - z0) / max(z0, 0.1) * AO_EDGE + 0.001);
            sum += w * texelFetch(AoSampler, h, 0).r;
            wsum += w;
        }
    }
    return sum / wsum;
}

void main() {
    vec2 size = vec2(textureSize(DiffuseSampler, 0));

    // (retro looks run this whole shader on a small target, see PostFx, so nothing is snapped here)
    vec2 uv = texCoord;

    // distance from the screen centre, in screen heights
    float r = length((uv - 0.5) * vec2(size.x / size.y, 1.0));

    // distance from the camera in blocks (sky = far away)
    float depth = texture(DepthSampler, uv).r;
    bool sky = depth >= 0.9999;
    vec4 view = InverseProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 p = view.xyz / view.w;
    float dist = sky ? 1.0e4 : length(p);

    // warp: far terrain (not the sky) smears sideways. Whole cycles per GameTime wrap (150 = 8 s), so it never jumps.
    vec2 cuv = uv;
    if (Warp > 0.0 && !sky) {
        float amount = Warp * smoothstep(30.0, 200.0, dist);
        cuv.x = clamp(uv.x + amount * 0.02 * sin(uv.y * 60.0 + GameTime * 6.2831853 * 150.0), 0.0, 1.0);
    }
    vec3 c = texture(DiffuseSampler, cuv).rgb;

    // ambient occlusion: computed at half resolution by ao.fsh (strength and distance fade already applied),
    // upsampled here without leaking across depth edges
    if (Ao.x > 0.0 && !sky && dist < Ao.z) c *= 1.0 - upsampleAo(ivec2(gl_FragCoord.xy), depth);

    // haze: far terrain fades to a colour (the sky is left alone)
    if (Fog.y > 0.0 && !sky) c = mix(c, HazeColor, smoothstep(Fog.x, Fog.y, dist));

    // colour grade
    c = mix(vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), c, Saturation) * Tint;
    c = (c - 0.5) * Contrast + 0.5;

    // flicker: lamp-like brightness wobble (two sines with whole cycles per GameTime wrap: 50 and 20 ticks)
    if (Flicker > 0.0) c *= 1.0 + Flicker * (0.6 * sin(GameTime * 6.2831853 * 480.0) + 0.4 * sin(GameTime * 6.2831853 * 1200.0));

    // flashlight
    if (Darkness > 0.0) {
        float light = (1.0 - smoothstep(Beam.x, Beam.y, r)) * (1.0 - smoothstep(Reach.x, Reach.y, dist));
        c *= mix(1.0 - Darkness, 1.0, light);
    }

    // bloom: added after the flashlight, so bright things still glow in the dark
    if (Bloom > 0.0) c += texture(BloomSampler, uv).rgb * Bloom;

    // frame: darker edges, film grain (a new pattern every tick)
    // (the vignette can breathe: strength swings slowly, 150 cycles per GameTime wrap = 8 s)
    float vig = clamp(Vignette * (1.0 + Breathe * sin(GameTime * 6.2831853 * 150.0)), 0.0, 1.0);
    c *= 1.0 - vig * smoothstep(0.4, 1.0, r);
    if (Grain > 0.0) {
        float tick = mod(floor(GameTime * 24000.0), 64.0);
        c += (hash(gl_FragCoord.xy + tick * 17.0) - 0.5) * Grain;
    }

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
