#!/usr/bin/env python3
"""Synthesises the robot sound effects for the TextFox film and mixes them over the soundtrack.

    python3 sfx.py <cues.json> <music.mp3> <out.wav>

cues.json comes from render.js (window.FILM.CUES in film.html): [[seconds, sound, gain], ...].
Every sound is generated here (FM bleeps, servo, chirps, impacts), so there are no sample licences to track.
"""
import json, subprocess, sys
import numpy as np

SR = 44100
rng = np.random.default_rng(42)


def t(d): return np.arange(int(d * SR)) / SR


def env(n, a=.005, r=.08, shape=1.0):
    e = np.ones(n); A = max(1, int(a * SR)); Rn = max(1, min(n, int(r * SR)))
    e[:A] = np.linspace(0, 1, A); e[-Rn:] *= np.linspace(1, 0, Rn) ** shape
    return e


def sweep(f0, f1, d, wave='sine', curve='exp'):
    x = t(d); k = x / d
    f = f0 * (f1 / f0) ** k if curve == 'exp' else f0 + (f1 - f0) * k
    ph = 2 * np.pi * np.cumsum(f) / SR
    if wave == 'square': return np.tanh(3 * np.sin(ph))
    if wave == 'saw': return 2 * ((ph / (2 * np.pi)) % 1) - 1
    return np.sin(ph)


def fm(f, d, ratio=2.0, index=2.0):
    x = t(d); return np.sin(2 * np.pi * f * x + index * np.sin(2 * np.pi * f * ratio * x))


def lowpass(x, fc):
    fc = np.broadcast_to(np.asarray(fc, float), x.shape)
    a = 1 - np.exp(-2 * np.pi * fc / SR); y = np.zeros_like(x); s = 0.0
    for i in range(len(x)): s += a[i] * (x[i] - s); y[i] = s
    return y


def crush(x, bits=6, hold=3):
    q = 2 ** bits; y = np.round(x * q) / q
    return np.repeat(y[::hold], hold)[:len(x)]


def noise(d): return rng.standard_normal(int(d * SR))


# ---------------------------------------------------------------- sounds
def blip():
    f = rng.choice([1320, 1480, 1760, 1980, 2217]); d = .075
    return fm(f, d, 1.5, 1.2) * env(int(d * SR), .002, .05) * .55


def boot():
    d = .55; s = sweep(180, 1900, d, 'square') * .35 + sweep(90, 950, d) * .4
    return crush(s * env(int(d * SR), .01, .25), 5, 4) * .5


def servo():
    d = 1.9; x = t(d)
    f = 150 + 110 * np.clip(x / 1.4, 0, 1) + 8 * np.sin(2 * np.pi * 9 * x)
    ph = 2 * np.pi * np.cumsum(f) / SR; s = 2 * ((ph / (2 * np.pi)) % 1) - 1
    s = lowpass(s, 900 + 900 * np.clip(x / 1.4, 0, 1)) * env(len(x), .12, .45)
    for c in np.arange(.1, 1.7, .23): s[int(c * SR):int(c * SR) + 300] += noise(300 / SR) * .25 * env(300, .0005, .006)
    return s * .45


def clunk():
    d = .35; x = t(d); s = np.sin(2 * np.pi * 70 * x * (1 - .3 * x)) * np.exp(-x * 14)
    s[:600] += lowpass(noise(600 / SR), 2500) * np.linspace(1, 0, 600) * .9
    return s * .9


def type_():
    s = np.zeros(int(.8 * SR))
    for k in range(15):
        i = int((k * .05 + rng.uniform(0, .015)) * SR); n = 350
        c = noise(n / SR); c = c - lowpass(c, 1800); s[i:i + n] += c * env(n, .0005, .008) * rng.uniform(.25, .4)
    return s


def whoosh(d=1.0, up=True):
    x = t(d); k = x / d; fc = 300 + 5200 * (k ** 1.6 if up else (1 - k) ** 1.6)
    s = lowpass(noise(d), fc) * np.sin(np.pi * k) ** 1.5
    return s * .55


def send():
    a = sweep(520, 1500, .11) * env(int(.11 * SR), .003, .05)
    b = fm(1900, .07, 2, .8) * env(int(.07 * SR), .002, .05)
    return np.concatenate([a, np.zeros(int(.03 * SR)), b]) * .5


def ding():
    d = .7; x = t(d)
    s = (np.sin(2 * np.pi * 1760 * x) + .5 * np.sin(2 * np.pi * 2637 * x) + .2 * np.sin(2 * np.pi * 3520 * x)) * np.exp(-x * 7)
    return s * env(len(x), .002, .1) * .28


def tick():
    n = int(.018 * SR); return np.sin(2 * np.pi * 2600 * t(.018)) * env(n, .0005, .015) * .3


def ring():
    d = .9; x = t(d); s = (np.sin(2 * np.pi * 880 * x) + np.sin(2 * np.pi * 960 * x)) * (.5 + .5 * np.sign(np.sin(2 * np.pi * 18 * x)))
    return crush(s * env(len(x), .01, .2), 6, 2) * .12


def zap():
    a = sweep(2600, 260, .16, 'square') * env(int(.16 * SR), .002, .06)
    b = lowpass(noise(.06), 4000) * env(int(.06 * SR), .001, .05)
    return np.concatenate([b * .5, a * .45])


def think():
    s = np.zeros(int(.5 * SR))
    for k in range(9):
        f = rng.choice([900, 1100, 1350, 1600, 2000, 2400]); d = rng.uniform(.025, .05)
        b = fm(f, d, rng.uniform(1, 3), 1.5) * env(int(d * SR), .002, .02); i = int(k * .052 * SR)
        s[i:i + len(b)] += b * .3
    return s


def chime():
    s = np.zeros(int(1.2 * SR))
    for k, f in enumerate([1318.5, 1568, 1975.5]):
        x = t(.9); b = (np.sin(2 * np.pi * f * x) + .3 * np.sin(2 * np.pi * f * 2 * x)) * np.exp(-x * 5) * env(len(x), .003, .1)
        i = int(k * .09 * SR); s[i:i + len(b)] += b * .2
    return s


def click():
    n = int(.012 * SR); c = noise(.012); return (c - lowpass(c, 2000)) * env(n, .0005, .01) * .35


def impact():
    d = 1.6; x = t(d)
    sub = np.sin(2 * np.pi * (55 - 20 * np.clip(x, 0, 1)) * x) * np.exp(-x * 3)
    hit = lowpass(noise(d), 3000) * np.exp(-x * 18)
    shimmer = sum(np.sin(2 * np.pi * f * x + rng.uniform(0, 6)) for f in [2093, 2637, 3136, 3951]) * np.exp(-x * 2.2) * .06
    s = sub * .9 + hit * .5 + shimmer
    pre = whoosh(.45, True) * .7
    return np.concatenate([pre, s])


def powerdown():
    d = 1.0; s = sweep(1400, 70, d, 'square') * .3 + sweep(700, 35, d) * .4
    return crush(s * env(int(d * SR), .01, .5), 5, 5) * .5


SOUNDS = {'blip': blip, 'boot': boot, 'servo': servo, 'clunk': clunk, 'type': type_, 'swoosh': lambda: whoosh(.7), 'whoosh': whoosh,
          'send': send, 'ding': ding, 'tick': tick, 'ring': ring, 'zap': zap, 'think': think, 'chime': chime, 'click': click,
          'impact': impact, 'powerdown': powerdown}
PRE = {'impact': .45}                                                   # sounds whose hit sits after a lead-in


def reverb(x, d=1.4, wet=.22):
    n = int(d * SR); ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR * 4.5); ir = lowpass(ir, 5000); ir /= np.abs(ir).sum() ** .5 * 6
    m = len(x) + n; L = 1 << (m - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, L) * np.fft.rfft(ir, L), L)[:len(x)]
    return x + wet * y


def main():
    cues, music, out = sys.argv[1:4]
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', music, '-ac', '2', '-ar', str(SR), '-f', 'f32le', '-'], capture_output=True, check=True).stdout
    mus = np.frombuffer(raw, np.float32).reshape(-1, 2).astype(np.float64)
    n = len(mus); L = np.zeros(n); R = np.zeros(n)
    for tm, name, gain in json.load(open(cues)):
        s = SOUNDS[name]() * gain
        start = int((tm - PRE.get(name, 0)) * SR)
        if start >= n: continue
        s = s[:n - max(0, start)]
        if start < 0: s = s[-start:]; start = 0
        pan = rng.uniform(-.25, .25) if name in ('blip', 'think', 'tick', 'send') else 0
        L[start:start + len(s)] += s * (1 - pan); R[start:start + len(s)] += s * (1 + pan)
    sfx = np.stack([reverb(L), reverb(R)], 1)
    sfx *= .62
    # gentle sidechain: dip the music a little under the effects
    level = np.abs(sfx).max(1); k = int(.05 * SR); level = np.convolve(level, np.ones(k) / k, 'same')
    duck = 1 - np.clip(level * 1.4, 0, .25)
    mix = mus * .86 * duck[:, None] + sfx
    mix = np.tanh(mix * 1.1) / np.tanh(1.1)                               # soft limiter
    pcm = (np.clip(mix, -1, 1) * 32767).astype('<i2')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', '-', out], input=pcm.tobytes(), check=True)
    print('mixed', len(json.load(open(cues))), 'cues →', out)


if __name__ == '__main__':
    main()
