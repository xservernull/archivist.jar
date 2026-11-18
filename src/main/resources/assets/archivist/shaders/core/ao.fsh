#version 150

// Half-resolution ambient occlusion (see docs/HISTORY_ARCHIVE.md, "Spec: half-resolution ambient occlusion").
// Reads depth only. Output: occlusion in the red channel, already multiplied by Ao.x (strength) and the
// distance fade; 0 for sky, for pixels beyond Ao.z blocks and when AO is off. Every pixel is written (no discard).
// Half-res pixel h stands for the full-res pixel 2*h: its depth is texelFetch(DepthSampler, 2*h), and look.fsh
// (upsampleAo) uses the very same mapping. REBUILT in #128 from the spec and look.fsh: the original file was lost.

uniform sampler2D DepthSampler;
uniform mat4 InverseProjMat;
uniform vec3 Ao; // strength (0 = off), radius in blocks, fade distance in blocks

in vec2 texCoord;
out vec4 fragColor;

const float SKY_DEPTH = 0.9999;
const float MAX_SCREEN_RADIUS = 0.05; // in screen heights (uv units); near pixels would read too wide an area

// 12 samples on a golden-angle spiral inside the unit disc, rotated per pixel by a hash angle
const vec2 AO_DIRS[12] = vec2[12](
    vec2(0.2041, 0.0000), vec2(-0.2607, 0.2388), vec2(0.0399, -0.4547), vec2(0.3286, 0.4286),
    vec2(-0.6030, -0.1067), vec2(0.5712, -0.3634), vec2(-0.1911, 0.7107), vec2(-0.3644, -0.7016),
    vec2(0.7906, 0.2887), vec2(-0.8224, 0.3395), vec2(0.3965, -0.8472), vec2(0.2930, 0.9341));

float hash(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

vec3 viewPos(vec2 uv, float depth) {
    vec4 v = InverseProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return v.xyz / v.w;
}

void main() {
    ivec2 full = textureSize(DepthSampler, 0);
    ivec2 f = min(ivec2(gl_FragCoord.xy) * 2, full - 1);
    vec2 uv = (vec2(f) + 0.5) / vec2(full);
    float depth = texelFetch(DepthSampler, f, 0).r;

    // position and normal first, at the top level (dFdx / dFdy must not sit in a divergent branch)
    vec3 p = viewPos(uv, depth);
    vec3 n = normalize(cross(dFdx(p), dFdy(p)));
    if (dot(n, p) > 0.0) n = -n; // face the camera

    float dist = length(p);
    if (Ao.x <= 0.0 || depth >= SKY_DEPTH || dist >= Ao.z || any(isnan(n))) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // radius: Ao.y blocks seen from this distance, in uv units (1/InverseProjMat[1][1] is the projection's y scale)
    float aspect = float(full.x) / float(full.y);
    float screenR = min(0.5 * Ao.y / (dist * InverseProjMat[1][1]), MAX_SCREEN_RADIUS);
    vec2 scale = vec2(screenR / aspect, screenR);

    float angle = hash(vec2(f)) * 6.2831853;
    float ca = cos(angle);
    float sa = sin(angle);

    int count = dist > 0.35 * Ao.z ? 6 : 12; // far pixels: half the samples, every other spiral point
    int stride = 12 / count;
    float reach = 2.5 * Ao.y;
    float occ = 0.0;
    for (int i = 0; i < count; i++) {
        vec2 d = AO_DIRS[i * stride];
        vec2 r = vec2(d.x * ca - d.y * sa, d.x * sa + d.y * ca);
        vec2 suv = uv + r * scale;
        float sd = textureLod(DepthSampler, suv, 0.0).r;
        if (sd >= SKY_DEPTH) continue; // sky samples count for nothing
        vec3 diff = viewPos(suv, sd) - p;
        float len = length(diff);
        if (len > reach) continue;     // too far away to be a neighbour
        // in front of the surface along its normal = something sticks out here = occlusion
        if (dot(diff, n) > 0.04 * Ao.y) occ += 1.0 - smoothstep(Ao.y, reach, len);
    }
    occ /= float(count);

    float fade = 1.0 - smoothstep(0.5 * Ao.z, Ao.z, dist);
    fragColor = vec4(clamp(occ * Ao.x * fade, 0.0, 1.0), 0.0, 0.0, 1.0);
}
