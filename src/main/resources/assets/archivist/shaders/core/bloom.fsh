#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;
uniform vec2 BlurStep;  // blur pass: uv distance between taps along the blur direction. (0, 0) = bright pass
uniform vec2 Threshold; // bright pass: brightness where the glow starts, and how wide the soft start is

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 sum;
    if (BlurStep == vec2(0.0)) {
        // bright pass + shrink: average a 4x4 block of screen pixels, keeping only the bright ones
        // (sky is skipped, so only things in the world glow; the colour is read first, the depth only for bright pixels)
        vec2 texel = 1.0 / vec2(textureSize(DiffuseSampler, 0));
        sum = vec3(0.0);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                vec2 uv = texCoord + (vec2(x, y) - 1.5) * texel;
                vec3 c = texture(DiffuseSampler, uv).rgb;
                float peak = max(c.r, max(c.g, c.b));
                if (peak <= Threshold.x) continue;                  // not bright: no glow, and no depth read needed
                if (texture(DepthSampler, uv).r >= 0.9999) continue;
                sum += c * smoothstep(Threshold.x, Threshold.x + Threshold.y, peak);
            }
        }
        sum /= 16.0;
    } else {
        // 9-tap gaussian along one direction
        sum = texture(DiffuseSampler, texCoord).rgb * 0.227027;
        sum += (texture(DiffuseSampler, texCoord + BlurStep).rgb + texture(DiffuseSampler, texCoord - BlurStep).rgb) * 0.1945946;
        sum += (texture(DiffuseSampler, texCoord + BlurStep * 2.0).rgb + texture(DiffuseSampler, texCoord - BlurStep * 2.0).rgb) * 0.1216216;
        sum += (texture(DiffuseSampler, texCoord + BlurStep * 3.0).rgb + texture(DiffuseSampler, texCoord - BlurStep * 3.0).rgb) * 0.054054;
        sum += (texture(DiffuseSampler, texCoord + BlurStep * 4.0).rgb + texture(DiffuseSampler, texCoord - BlurStep * 4.0).rgb) * 0.016216;
    }
    fragColor = vec4(sum, 1.0);
}
