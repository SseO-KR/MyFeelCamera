#version 300 es

precision highp float;

uniform sampler2D uSourceTexture;
uniform highp sampler3D uLut;

uniform float uLutStrength;
uniform float uGrainAmount;
uniform float uGrainScale;
uniform float uVignetteStrength;
uniform vec3 uVignetteColor;
uniform float uOutputAspectRatio; // output width / output height

in vec2 vTextureCoord;
in vec2 vEffectCoord;
out vec4 fragColor;

const float LUT_SIZE = 32.0;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 applyFilm(vec3 sourceColor) {
    vec3 color = clamp(sourceColor, 0.0, 1.0);

    float lutScale = (LUT_SIZE - 1.0) / LUT_SIZE;
    float lutOffset = 0.5 / LUT_SIZE;
    vec3 lutCoord = color * lutScale + lutOffset;
    vec3 lutColor = texture(uLut, lutCoord).rgb;
    color = mix(color, lutColor, clamp(uLutStrength, 0.0, 1.0));

    float safeScale = max(uGrainScale, 1.0);
    vec2 grainCell = floor(vEffectCoord * safeScale);
    float grain = hash12(grainCell) * 2.0 - 1.0;
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    float grainMask = 1.0 - luma;
    color += grain * uGrainAmount * grainMask;

    float aspect = max(uOutputAspectRatio, 0.0001);
    vec2 p = vEffectCoord - 0.5;
    p.x *= aspect;
    float dist = length(p);
    float edge = smoothstep(0.20, 0.70, dist);
    float vigBlend = clamp(edge * uVignetteStrength, 0.0, 1.0);
    color = mix(color, uVignetteColor, vigBlend);

    return clamp(color, 0.0, 1.0);
}

void main() {
    vec4 source = texture(uSourceTexture, vTextureCoord);
    fragColor = vec4(applyFilm(source.rgb), source.a);
}
