package com.vibeactions.ui.common

import android.opengl.GLES30
import android.util.Log

/** Fullscreen-quad pass-through. */
internal const val VERTEX_SHADER = """#version 300 es
in vec4 a_position;
void main() {
    gl_Position = a_position;
}
"""

/**
 * Animated gradient fragment shader — ported in spirit from shadergradient's cosmic/plane look:
 * classic Perlin noise (Ashima/Gustavson), domain-warped fbm for the flowing "plane", plus a
 * holographic interference / iridescence sheen on top. Colours come from three uniforms so the
 * Settings presets recolour the same shader.
 */
internal const val FRAGMENT_SHADER = """#version 300 es
precision highp float;

uniform float u_time;
uniform vec2  u_resolution;
uniform vec3  u_color1;
uniform vec3  u_color2;
uniform vec3  u_color3;

out vec4 fragColor;

vec4 permute(vec4 x) { return mod(((x * 34.0) + 1.0) * x, 289.0); }
vec2 fade(vec2 t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }

// Classic Perlin noise in 2D.
float cnoise(vec2 P) {
    vec4 Pi = floor(P.xyxy) + vec4(0.0, 0.0, 1.0, 1.0);
    vec4 Pf = fract(P.xyxy) - vec4(0.0, 0.0, 1.0, 1.0);
    Pi = mod(Pi, 289.0);
    vec4 ix = Pi.xzxz;
    vec4 iy = Pi.yyww;
    vec4 fx = Pf.xzxz;
    vec4 fy = Pf.yyww;
    vec4 i = permute(permute(ix) + iy);
    vec4 gx = 2.0 * fract(i * 0.0243902439) - 1.0;
    vec4 gy = abs(gx) - 0.5;
    vec4 tx = floor(gx + 0.5);
    gx = gx - tx;
    vec2 g00 = vec2(gx.x, gy.x);
    vec2 g10 = vec2(gx.y, gy.y);
    vec2 g01 = vec2(gx.z, gy.z);
    vec2 g11 = vec2(gx.w, gy.w);
    vec4 norm = 1.79284291400159 - 0.85373472095314 *
        vec4(dot(g00, g00), dot(g01, g01), dot(g10, g10), dot(g11, g11));
    g00 *= norm.x; g01 *= norm.y; g10 *= norm.z; g11 *= norm.w;
    float n00 = dot(g00, vec2(fx.x, fy.x));
    float n10 = dot(g10, vec2(fx.y, fy.y));
    float n01 = dot(g01, vec2(fx.z, fy.z));
    float n11 = dot(g11, vec2(fx.w, fy.w));
    vec2 fade_xy = fade(Pf.xy);
    vec2 n_x = mix(vec2(n00, n01), vec2(n10, n11), fade_xy.x);
    float n_xy = mix(n_x.x, n_x.y, fade_xy.y);
    return 2.3 * n_xy;
}

// Fractal Brownian motion (4 octaves).
float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * cnoise(p);
        p *= 2.0;
        a *= 0.5;
    }
    return v;
}

void main() {
    vec2 uv = gl_FragCoord.xy / u_resolution.xy;
    vec2 p = uv;
    p.x *= u_resolution.x / u_resolution.y; // aspect-correct

    float t = u_time * 0.06;

    // Domain-warped noise — the slowly flowing "plane".
    vec2 q = vec2(fbm(p * 2.0 + vec2(0.0, t)),
                  fbm(p * 2.0 + vec2(5.2, t * 1.3)));
    float n = fbm(p * 3.0 + q * 1.5 + vec2(t * 0.5, -t * 0.7));
    n = n * 0.5 + 0.5;

    // Three-stop gradient driven by vertical position + noise.
    float g = clamp(uv.y + (n - 0.5) * 0.9, 0.0, 1.0);
    vec3 col = mix(u_color1, u_color2, smoothstep(0.0, 0.6, g));
    col = mix(col, u_color3, smoothstep(0.45, 1.0, g));

    // Holographic interference + iridescence sheen.
    float interf = sin(p.x * 8.0 + p.y * 6.0 + n * 6.2831 + t * 4.0);
    float iri = 0.5 + 0.5 * interf;
    vec3 irid = 0.5 + 0.5 * cos(6.2831 * (vec3(0.0, 0.33, 0.67) + iri + n));
    col += irid * 0.08 * iri;

    // Soft vignette for depth.
    float vig = smoothstep(1.3, 0.3, length(uv - 0.5));
    col *= mix(0.85, 1.0, vig);

    fragColor = vec4(col, 1.0);
}
"""

internal fun compileShader(type: Int, source: String): Int {
    val shader = GLES30.glCreateShader(type)
    GLES30.glShaderSource(shader, source)
    GLES30.glCompileShader(shader)
    val status = IntArray(1)
    GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
    if (status[0] == 0) {
        Log.e("GradientShaders", "Shader compile failed: ${GLES30.glGetShaderInfoLog(shader)}")
        GLES30.glDeleteShader(shader)
        return 0
    }
    return shader
}

internal fun linkProgram(vertex: Int, fragment: Int): Int {
    if (vertex == 0 || fragment == 0) return 0
    val program = GLES30.glCreateProgram()
    GLES30.glAttachShader(program, vertex)
    GLES30.glAttachShader(program, fragment)
    GLES30.glLinkProgram(program)
    val status = IntArray(1)
    GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
    if (status[0] == 0) {
        Log.e("GradientShaders", "Program link failed: ${GLES30.glGetProgramInfoLog(program)}")
        GLES30.glDeleteProgram(program)
        return 0
    }
    return program
}
