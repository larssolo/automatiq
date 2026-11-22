#!/usr/bin/env python3
"""Short score for the vertical clips, synthesised (no samples). Reuses the film's instruments.

    python3 score.py <seconds> <out.wav> [seed] [groove_from_seconds]

80 BPM, one bar = 3 s, the groove starts on the first beat (a short clip has no time for a slow intro).
The seed transposes the key, so a week of clips doesn't all sound the same.
"""
import importlib.util, pathlib, subprocess, sys
import numpy as np

here = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("film_music", here.parent / "film" / "music.py")
M = importlib.util.module_from_spec(spec); spec.loader.exec_module(M)
SR, BAR = M.SR, M.BAR


def main():
    dur, out = float(sys.argv[1]), sys.argv[2]
    seed = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    gf = float(sys.argv[4]) if len(sys.argv) > 4 else 0.0           # the beat and the bells start here; before it only pads and a riser
    shift = [0, 2, -2, 3, 5, -4, 7, 1][seed % 8]
    M.rng = np.random.default_rng(seed + 11)
    N = int((dur + 1) * SR); L = np.zeros(N); R = np.zeros(N)

    def put(sig, t, g=1., pan=0.):
        i = int(t * SR)
        if i >= N: return
        j = min(N, i + len(sig)); sig = sig[:j - i] * g
        L[i:j] += sig * (1 - pan); R[i:j] += sig * (1 + pan)

    for b in range(int(dur / BAR) + 1):
        t = b * BAR; ci = b % 4
        for n in M.CHORDS[ci]: put(M.pad(M.mid(n + shift), BAR + 1.6), t, .05, M.rng.uniform(-.3, .3))
        put(M.pad(M.mid(M.ROOTS[ci] + shift), BAR + 1.2) * .9, t, .1)
        notes = M.CHORDS[ci][1:] + [M.CHORDS[ci][2] + 12]
        for k in range(8):
            if t + k * BAR / 8 < max(0, gf - 1.2): continue
            put(M.bell(M.mid(notes[[0, 2, 1, 3, 2, 4, 1, 3][k]] + 12 + shift)), t + k * BAR / 8, .05 * (.7 if k % 2 else 1), [-.35, .35][k % 2])
        for k in range(4):
            if t + k * .75 >= gf - .01: put(M.kick(), t + k * .75, .17 if k % 2 == 0 else .09)
        for k in range(8):
            if t + k * .375 >= gf - .01: put(M.shaker(), t + k * .375 + .02, .022 if k % 2 else .011, .2)
    if gf > 0:                                                      # riser into the first beat
        d = min(1.5, gf); n = int(d * SR); x = M.rng.standard_normal(n); k = np.linspace(0, 1, n)
        x = np.cumsum(x) * (1 - k * .9); x -= np.convolve(x, np.ones(64) / 64, 'same'); x /= np.abs(x).max() + 1e-9
        put(x * k ** 2.5 * .12, gf - d)
    mix = np.stack([L, R], 1)[:int(dur * SR)]
    fade = int(1.8 * SR); mix[-fade:] *= np.linspace(1, 0, fade)[:, None] ** 1.5
    mix /= np.abs(mix).max() + 1e-9; mix *= .7
    pcm = (mix * 32767).astype('<i2')
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 's16le', '-ar', str(SR), '-ac', '2', '-i', '-', out], input=pcm.tobytes(), check=True)
    print('score', dur, 's, seed', seed, '→', out)


if __name__ == '__main__':
    main()
