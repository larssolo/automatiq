package com.vibeactions.ui.common

internal const val VERTEX_SHADER_SRC = """#version 300 es
in vec2 aPosition;
out vec2 vPos;
void main() {
    vPos = aPosition;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}"""

// Adapted from https://github.com/ruucm/shadergradient (cosmic/plane shaders)
// Perlin noise + holographic interference ported to GLSL ES 3.0 for a 2D fullscreen quad.
internal const val FRAGMENT_SHADER_SRC = """#version 300 es
precision highp float;

in vec2 vPos;
out vec4 fragColor;

uniform float uTime;
uniform float uSpeed;
uniform float uNoiseDensity;
uniform float uNoiseStrength;
uniform vec3 uColor1;
uniform vec3 uColor2;
uniform vec3 uColor3;

// Classic Perlin noise — ported from shadergradient/src/shaders/cosmic/plane/vertex.glsl
vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
vec3 fade3(vec3 t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }

float cnoise(vec3 P) {
    vec3 Pi0 = floor(P);
    vec3 Pi1 = Pi0 + vec3(1.0);
    Pi0 = mod289(Pi0);
    Pi1 = mod289(Pi1);
    vec3 Pf0 = fract(P);
    vec3 Pf1 = Pf0 - vec3(1.0);
    vec4 ix = vec4(Pi0.x, Pi1.x, Pi0.x, Pi1.x);
    vec4 iy = vec4(Pi0.yy, Pi1.yy);
    vec4 iz0 = vec4(Pi0.z);
    vec4 iz1 = vec4(Pi1.z);
    vec4 ixy = permute(permute(ix) + iy);
    vec4 ixy0 = permute(ixy + iz0);
    vec4 ixy1 = permute(ixy + iz1);
    vec4 gx0 = ixy0 * (1.0 / 7.0);
    vec4 gy0 = fract(floor(gx0) * (1.0 / 7.0)) - 0.5;
    gx0 = fract(gx0);
    vec4 gz0 = vec4(0.5) - abs(gx0) - abs(gy0);
    vec4 sz0 = step(gz0, vec4(0.0));
    gx0 -= sz0 * (step(0.0, gx0) - 0.5);
    gy0 -= sz0 * (step(0.0, gy0) - 0.5);
    vec4 gx1 = ixy1 * (1.0 / 7.0);
    vec4 gy1 = fract(floor(gx1) * (1.0 / 7.0)) - 0.5;
    gx1 = fract(gx1);
    vec4 gz1 = vec4(0.5) - abs(gx1) - abs(gy1);
    vec4 sz1 = step(gz1, vec4(0.0));
    gx1 -= sz1 * (step(0.0, gx1) - 0.5);
    gy1 -= sz1 * (step(0.0, gy1) - 0.5);
    vec3 g000 = vec3(gx0.x, gy0.x, gz0.x);
    vec3 g100 = vec3(gx0.y, gy0.y, gz0.y);
    vec3 g010 = vec3(gx0.z, gy0.z, gz0.z);
    vec3 g110 = vec3(gx0.w, gy0.w, gz0.w);
    vec3 g001 = vec3(gx1.x, gy1.x, gz1.x);
    vec3 g101 = vec3(gx1.y, gy1.y, gz1.y);
    vec3 g011 = vec3(gx1.z, gy1.z, gz1.z);
    vec3 g111 = vec3(gx1.w, gy1.w, gz1.w);
    vec4 norm0 = taylorInvSqrt(vec4(dot(g000,g000), dot(g010,g010), dot(g100,g100), dot(g110,g110)));
    g000 *= norm0.x; g010 *= norm0.y; g100 *= norm0.z; g110 *= norm0.w;
    vec4 norm1 = taylorInvSqrt(vec4(dot(g001,g001), dot(g011,g011), dot(g101,g101), dot(g111,g111)));
    g001 *= norm1.x; g011 *= norm1.y; g101 *= norm1.z; g111 *= norm1.w;
    float n000 = dot(g000, Pf0);
    float n100 = dot(g100, vec3(Pf1.x, Pf0.y, Pf0.z));
    float n010 = dot(g010, vec3(Pf0.x, Pf1.y, Pf0.z));
    float n110 = dot(g110, vec3(Pf1.x, Pf1.y, Pf0.z));
    float n001 = dot(g001, vec3(Pf0.x, Pf0.y, Pf1.z));
    float n101 = dot(g101, vec3(Pf1.x, Pf0.y, Pf1.z));
    float n011 = dot(g011, vec3(Pf0.x, Pf1.y, Pf1.z));
    float n111 = dot(g111, Pf1);
    vec3 fade_xyz = fade3(Pf0);
    vec4 n_z = mix(vec4(n000, n100, n010, n110), vec4(n001, n101, n011, n111), fade_xyz.z);
    vec2 n_yz = mix(n_z.xy, n_z.zw, fade_xyz.y);
    return 2.2 * mix(n_yz.x, n_yz.y, fade_xyz.x);
}

// Value noise helper used for shimmer
float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123); }
float noise2D(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), u.x),
               mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), u.x), u.y);
}

void main() {
    float t = uTime * uSpeed;
    vec3 pos3d = vec3(vPos, 0.0);

    // Multi-layer cosmic noise (vertex shader logic adapted for 2D)
    float cosmicWave = cnoise(pos3d * uNoiseDensity * 0.5 + vec3(t * 0.3, t * 0.2, t * 0.4));
    float n1 = cnoise(pos3d * uNoiseDensity * 2.0 + vec3(t * 0.8, 0.0, 0.0));
    float n2 = cnoise(pos3d * uNoiseDensity * 0.3 + vec3(t * 0.2, 0.0, 0.0)) * 0.5;
    float n3 = cnoise(pos3d * uNoiseDensity * 4.0 + vec3(t * 1.2, 0.0, 0.0)) * 0.25;
    float combinedNoise = (n1 + n2 + n3) * uNoiseStrength;

    // Holographic interference patterns
    float i1 = sin(vPos.x * 20.0 + t * 3.0) * cos(vPos.y * 15.0 + t * 2.0);
    float i2 = sin(vPos.x * 35.0 + t * 4.0) * sin(vPos.y * 30.0 + t * 3.5);
    float holoPat = (i1 + i2 * 0.5) / 1.5;

    float shimmer = noise2D(vPos * 40.0 + vec2(t * 2.0)) * 0.3;
    float cosmicGlow = noise2D(vPos * 8.0 + vec2(t * 0.5)) * 0.5;

    // Iridescent color shift
    vec3 holoShift = vec3(
        sin(vPos.x * 10.0 + t * 2.0) * 0.06,
        sin(vPos.x * 10.0 + t * 2.0 + 2.094) * 0.06,
        sin(vPos.x * 10.0 + t * 2.0 + 4.188) * 0.06
    );

    // Three-color gradient with noise displacement
    float gx = smoothstep(-1.0, 1.0, vPos.x + holoPat * 0.5 + combinedNoise * 0.3);
    float gy = smoothstep(-1.0, 1.0, vPos.y + cosmicWave * 0.4);
    vec3 base = mix(mix(uColor1, uColor2, gx), uColor3, gy * 0.7 + shimmer * 0.3);

    vec3 color = base + holoShift;
    color += vec3(cosmicGlow * 0.15, shimmer * 0.10, (cosmicGlow + shimmer) * 0.08);

    float irid = sin(vPos.x * 25.0 + t * 3.0) * cos(vPos.y * 20.0 + t * 2.5) * 0.06;
    color += vec3(irid * 0.15, irid * 0.25, irid * 0.35);

    fragColor = vec4(color, 1.0);
}"""
