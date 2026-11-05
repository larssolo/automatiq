#!/usr/bin/env python3
"""Calm ambient score for the TextFox tutorial film, synthesised (no samples).

    python3 music.py <seconds> <out.wav>

80 BPM, one bar = 3 s, the same grid as key.html (first bar at 0.5 s). Fmaj9 → Am7 → Cmaj9 → G6sus,
soft pads, a sine sub, a bell arpeggio from bar 2, a light pulse from bar 4, a riser into bar 10
(the switch to the phone) and a warm landing for the outro at bar 14.
"""
import subprocess, sys
import numpy as np

SR = 44100
BAR, B0 = 3.0, .5
rng = np.random.default_rng(3)
mid = lambda n: 440 * 2 ** ((n - 69) / 12)
CHORDS = [[53, 57, 60, 64, 67], [57, 60, 64, 67, 71], [48, 55, 59, 62, 64], [55, 59, 62, 64, 69]]   # F, Am, C, G voicings
ROOTS = [41, 45, 36, 43]


def adsr(n, a, r):
    e = np.ones(n); A, R = int(a * SR), int(r * SR)
    e[:A] = np.linspace(0, 1, A) ** 2; e[-R:] *= np.linspace(1, 0, R) ** 2
    return e


def pad(f, d):
    t = np.arange(int(d * SR)) / SR; s = 0
    for k, w in [(1, 1), (2, .35), (3, .16), (4, .08), (5, .04)]:
        for det in (-.12, .12):
            s = s + w * np.sin(2 * np.pi * f * k * (1 + det / 100 * k) * t + rng.uniform(0, 6))
    return s * (1 + .15 * np.sin(2 * np.pi * .23 * t)) * adsr(len(t), 1.2, 1.4)


def bell(f, d=1.6):
    t = np.arange(int(d * SR)) / SR
    return (np.sin(2 * np.pi * f * t + 1.2 * np.exp(-t * 6) * np.sin(2 * np.pi * f * 3.5 * t)) * np.exp(-t * 3.2)) * adsr(len(t), .004, .3)


def kick():
    t = np.arange(int(.5 * SR)) / SR
    return np.sin(2 * np.pi * (48 + 60 * np.exp(-t * 30)) * t) * np.exp(-t * 7)


def shaker():
    n = int(.09 * SR); x = rng.standard_normal(n); x = np.diff(x, prepend=0)
    x = np.convolve(x, np.ones(3) / 3, 'same')                              # take the fizz off
    a = np.minimum(1, np.arange(n) / (.004 * SR))
    return x * a * np.exp(-np.arange(n) / SR * 45)


def main():
    dur, out = float(sys.argv[1]), sys.argv[2]
    N = int(dur * SR) + SR; L = np.zeros(N); R = np.zeros(N)
    def put(sig, t, g=1., pan=0.):
        i = int(t * SR); j = min(N, i + len(sig)); sig = sig[:j - i] * g
        L[i:j] += sig * (1 - pan); R[i:j] += sig * (1 + pan)
    bars = int((dur - B0) / BAR) + 1
    for b in range(-1, bars):
        t = B0 + b * BAR; ci = (b % 4 + 4) % 4
        if t < -1: continue
        tt = max(0, t - .4)
        for n in CHORDS[ci]: put(pad(mid(n), BAR + 1.6), tt, .045 if b < 14 else .05, rng.uniform(-.3, .3))
        put(pad(mid(ROOTS[ci]), BAR + 1.2) * .9, tt, .09)
        if 2 <= b < 15:
            notes = CHORDS[ci][1:] + [CHORDS[ci][2] + 12]
            for k in range(8):
                f = mid(notes[[0, 2, 1, 3, 2, 4, 1, 3][k]] + 12)
                put(bell(f), t + k * BAR / 8, .05 * (.7 if k % 2 else 1), [-.35, .35][k % 2])
        if 4 <= b < 14:
            for k in range(4):
                put(kick(), t + k * .75, .18 if k % 2 == 0 else .1)
            for k in range(8):
                put(shaker(), t + k * .375 + .02, .022 if k % 2 else .011, .2)
    # riser into the phone (bar 10) and a soft swell into the outro (bar 14)
    for at, d in [(B0 + 10 * BAR, 2.4), (B0 + 14 * BAR, 2.0)]:
        n = int(d * SR); x = rng.standard_normal(n); k = np.linspace(0, 1, n)
        x = np.cumsum(x) * (1 - k * .9); x -= np.convolve(x, np.ones(64) / 64, 'same'); x /= np.abs(x).max() + 1e-9
        put(x * k ** 2.5 * .12, at - d)
    mix = np.stack([L, R], 1)[:int(dur * SR)]
    fade = int(2.5 * SR); mix[-fade:] *= np.linspace(1, 0, fade)[:, None] ** 1.5
    mix /= np.abs(mix).max() + 1e-9; mix *= .7
    pcm = (mix * 32767).astype('<i2')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', '-', out], input=pcm.tobytes(), check=True)
    print('music', dur, 's →', out)


if __name__ == '__main__':
    main()
