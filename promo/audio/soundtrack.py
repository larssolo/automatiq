#!/usr/bin/env python3
"""Synthesises the promo soundtrack: an 80 BPM trap-style track in C minor plus every sound
effect cue from timeline.js, mixed and mastered to a 48 kHz / 24-bit WAV.

    python3 soundtrack.py build/timeline.json build/soundtrack.wav

All timing comes from the timeline (beats on an 80 BPM grid), so the music, the effects and the
picture share one clock.
"""
import json
import os
import re
import subprocess
import sys
import tempfile

import numpy as np
from scipy.io import wavfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dsp import (SR, bp, convolve, db, env_adsr, env_exp, fade, fm, follower, hp, limiter, lp,  # noqa: E402
                 midi_hz, noise, nsamp, pan, pingpong, reverb_ir, saw_blep, secs, square_blep, sweep)

TL = json.load(open(sys.argv[1]))
OUT = sys.argv[2]
BEAT = TL['BEAT']
DUR = TL['DURATION']
N = nsamp(DUR)
TAIL = nsamp(3.0)


def S(b):
    """beats → seconds"""
    return b * BEAT


SPLIT = S(40)  # the tape stop: everything that starts before beat 40 winds down with it


class Track:
    def __init__(self, name):
        self.name = name
        self.pre = np.zeros((2, N + TAIL))
        self.post = np.zeros((2, N + TAIL))

    @property
    def buf(self):
        return self.pre + self.post

    def add(self, x, at, gain=1.0, p=0.0):
        """Mix x (mono or stereo) in at `at` seconds."""
        if x.ndim == 1:
            x = pan(x, p)
        dst = self.pre if at < SPLIT - 1e-6 else self.post
        i = int(round(at * SR))
        if i < 0:
            x = x[:, -i:]
            i = 0
        j = min(dst.shape[1], i + x.shape[1])
        if j > i:
            dst[:, i:j] += gain * x[:, : j - i]


def put(out, x, i):
    """Overlap-add x into out at sample i, clipped to out's length (1-D or (2, n))."""
    if i >= out.shape[-1]:
        return out
    m = min(x.shape[-1], out.shape[-1] - i)
    out[..., i:i + m] += x[..., :m]
    return out


# ═════════════════════════════════ instruments ════════════════════════════════
def kick(vel=1.0):
    n = nsamp(0.55)
    t = secs(n)
    f = 44 + 120 * np.exp(-t / 0.032)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.26)
    click = hp(noise(n, 1), 1500) * np.exp(-t / 0.004) * 0.6
    y = np.tanh(1.7 * (body + click))
    return fade(y * vel, 0.0005, 0.03)


def snare(vel=1.0):
    n = nsamp(0.45)
    t = secs(n)
    body = np.sin(2 * np.pi * np.cumsum(180 + 40 * np.exp(-t / 0.02)) / SR) * np.exp(-t / 0.07)
    nz = noise(n, 2)
    rattle = bp(nz, 1800, 9000) * np.exp(-t / 0.16)
    # clap: three tight bursts then a tail
    clap = np.zeros(n)
    for k, off in enumerate((0.0, 0.009, 0.019)):
        i = nsamp(off)
        m = n - i
        clap[i:] += np.exp(-secs(m) / (0.006 if k < 2 else 0.11))
    clap = bp(noise(n, 3), 900, 3500) * clap
    y = 0.55 * body + 0.6 * rattle + 0.9 * clap
    return fade(np.tanh(1.3 * y) * vel, 0.0003, 0.02)


def hat(vel=1.0, open_=False, seed=0):
    n = nsamp(0.32 if open_ else 0.07)
    t = secs(n)
    metal = sum(square_blep(f * 1.7, n) for f in (205.3, 304.4, 369.6, 522.7, 540.0, 800.0))
    nz = noise(n, 10 + seed % 7)
    y = hp(0.5 * metal / 6 + 0.8 * nz, 7000, order=4)
    y = y * np.exp(-t / (0.12 if open_ else 0.022))
    return fade(y * vel, 0.0005, 0.005)


def bass_line(notes, total_n):
    """808: notes = [(start_s, end_s, midi, glide_from_midi or None)] → mono buffer."""
    f = np.zeros(total_n)
    a = np.zeros(total_n)
    for (s0, s1, m, g) in notes:
        i0, i1 = nsamp(s0), min(total_n, nsamp(s1))
        if i1 <= i0:
            continue
        n = i1 - i0
        t = secs(n)
        target = midi_hz(m)
        if g is not None:
            start = midi_hz(g)
            ff = target + (start - target) * np.exp(-t / 0.045)
        else:
            ff = target * (1 + 0.9 * np.exp(-t / 0.012))  # punch
        f[i0:i1] = ff
        env = np.exp(-t / 0.75)
        env[: nsamp(0.002)] *= np.linspace(0, 1, nsamp(0.002))
        rel = min(n, nsamp(0.035))
        env[-rel:] *= np.linspace(1, 0, rel)
        a[i0:i1] = env
    ph = np.cumsum(f) / SR
    y = np.sin(2 * np.pi * ph) * a
    y = np.tanh(1.9 * y) * 0.8 + 0.35 * np.sin(2 * np.pi * ph) * a  # harmonics for small speakers
    return lp(y, 1100)


def pad_chord(midis, n, cutoff=2400, attack=0.25, release=0.5, seed=0):
    out = np.zeros((2, n))
    r = np.random.default_rng(100 + seed)
    for k, m in enumerate(midis):
        f0 = midi_hz(m)
        for d in (-11, -4, 4, 11):
            det = 2 ** (d / 1200)
            v = saw_blep(f0 * det, n, phase0=r.uniform())
            out += pan(v, np.clip(d / 12 + (k - len(midis) / 2) * 0.08, -1, 1))
    out = lp(out, cutoff, order=2) * env_adsr(n, attack, 0.3, 0.85, release)
    return out / (4 * len(midis)) * 2.2


def pluck(m, dur=0.9, bright=1.0):
    n = nsamp(dur)
    y = 0.75 * fm(midi_hz(m), n, ratio=2.0, index=2.6 * bright, index_tau=0.09, amp_tau=0.28)
    y += 0.35 * fm(midi_hz(m + 12), n, ratio=3.5, index=1.2 * bright, index_tau=0.05, amp_tau=0.16)
    return fade(y, 0.001, 0.05)


def lead_note(m, dur):
    n = nsamp(dur)
    t = secs(n)
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.5 * t) * np.clip((t - 0.15) / 0.2, 0, 1)
    f = midi_hz(m) * vib
    y = 0.5 * saw_blep(f * 2 ** (-6 / 1200)) + 0.5 * saw_blep(f * 2 ** (6 / 1200))
    y = lp(y, 3200) * env_adsr(n, 0.02, 0.2, 0.7, 0.15)
    return y


def bell(m, dur=1.2, ratio=3.5, index=2.2, tau=0.5):
    n = nsamp(dur)
    return fade(fm(midi_hz(m), n, ratio=ratio, index=index, index_tau=0.25, amp_tau=tau), 0.001, 0.05)


# ═════════════════════════════════ arrangement ════════════════════════════════
C, Ab, Eb, Bb = 'Cm9', 'Abmaj9', 'Ebmaj7', 'Bbadd9'
VOICING = {
    'Cm9': [48, 51, 55, 58, 62],
    'Abmaj9': [44, 48, 51, 55, 58],
    'Ebmaj7': [51, 55, 58, 62],
    'Bbadd9': [46, 50, 53, 60],
}
ROOT = {'Cm9': 36, 'Abmaj9': 32, 'Ebmaj7': 39, 'Bbadd9': 34}
LOOP = [C, Ab, Eb, Bb]
# pluck hook, one bar per chord: (16th step, midi)
HOOK = {
    'Cm9': [(0, 79), (3, 75), (6, 79), (8, 82), (10, 79), (12, 77), (14, 75)],
    'Abmaj9': [(0, 84), (3, 82), (6, 79), (8, 75), (11, 77), (12, 79)],
    'Ebmaj7': [(0, 82), (3, 79), (6, 82), (8, 86), (10, 82), (12, 79), (14, 77)],
    'Bbadd9': [(0, 77), (3, 74), (6, 77), (8, 79), (10, 82), (14, 84)],
}
LEAD = {
    'Cm9': [(0, 8, 84), (8, 4, 82), (12, 4, 79)],
    'Abmaj9': [(0, 12, 80), (12, 4, 79)],
    'Ebmaj7': [(0, 8, 79), (8, 8, 82)],
    'Bbadd9': [(0, 8, 77), (8, 8, 74)],
}


# one chord per bar (20 bars); bar 18 (outro) changes chord every beat, handled separately
CHORDS = [C, Ab, C, Ab, Eb, Bb, C, Ab, Eb, Bb, Ab, Bb, C, Ab, Eb, Bb, Ab, Bb, C, C]


def chord_at(bar):
    return CHORDS[bar]


kick_t, snare_t, hat_t, bass_t, pad_t, pluck_t, lead_t = (Track(n) for n in
                                                           ('kick', 'snare', 'hats', 'bass', 'pad', 'pluck', 'lead'))
bass_notes = []
kick_times = []


def groove_bar(bar, hats_level=1.0, snare_on=True, rolls=True):
    b0 = bar * 4
    root = ROOT[chord_at(bar)]
    pat = TL['GROOVE']['kick'][bar % 2]
    hits = [b0 + s / 4 for s in pat]
    for k, h in enumerate(hits):
        kick_t.add(kick(1.0 if k == 0 else 0.85), S(h))
        kick_times.append(S(h))
        nxt = hits[k + 1] if k + 1 < len(hits) else b0 + 4
        glide = None
        m = root
        if bar % 2 == 1 and k == len(hits) - 1:  # octave slide up at the end of the phrase
            m, glide = root + 12, root
        bass_notes.append((S(h), S(nxt), m, glide))
    if snare_on:
        for s in TL['GROOVE']['snare']:
            snare_t.add(snare(1.0), S(b0 + s / 4))
    if hats_level <= 0:
        return
    # hats: 8ths everywhere; even bars end on an open hat, odd bars add 16ths on the last beat,
    # and every fourth bar closes with a 32nd-triplet roll (the trap signature)
    roll_bar = rolls and bar % 4 == 3
    sixteenths = rolls and bar % 2 == 1 and not roll_bar
    for step in range(16):
        if roll_bar and step >= 14:
            continue
        if bar % 2 == 0 and step == 14:
            hat_t.add(hat(0.5 * hats_level, open_=True, seed=3), S(b0 + 3.5), p=0.2)
            continue
        if step % 2 == 1 and not (sixteenths and step >= 12):
            continue
        vel = (0.9 if step % 4 == 0 else 0.62) * hats_level
        hat_t.add(hat(vel, seed=step + bar), S(b0 + step / 4), p=0.15 * np.sin(step))
    if roll_bar:
        for k in range(6):
            hat_t.add(hat((0.45 + 0.09 * k) * hats_level, seed=k), S(b0 + 3.5 + k * (0.5 / 6)), p=-0.3 + 0.12 * k)


def pad_bar(bar, beats=4, cutoff=2400, gain=1.0, at=0.0):
    ch = chord_at(bar)
    n = nsamp(S(beats) + 0.6)
    pad_t.add(pad_chord(VOICING[ch], n, cutoff=cutoff, seed=bar), S(bar * 4 + at), gain)


def hook_bar(bar, gain=1.0, bright=1.0, transpose=0):
    ch = chord_at(bar)
    for step, m in HOOK[ch]:
        pluck_t.add(pluck(m + transpose, bright=bright), S(bar * 4 + step / 4), gain, p=0.25 * np.sin(step))


def lead_bar(bar, gain=1.0):
    ch = chord_at(bar)
    for step, length, m in LEAD[ch]:
        lead_t.add(lead_note(m, S(length / 4)), S(bar * 4 + step / 4), gain, p=-0.1)


def snare_roll(b_from, b_to, start_div=2, end_div=8, v0=0.25, v1=1.0):
    """Accelerating roll from 8ths to 32nds with a velocity ramp."""
    b = b_from
    while b < b_to - 1e-9:
        p = (b - b_from) / (b_to - b_from)
        div = start_div if p < 0.34 else (4 if p < 0.67 else end_div)
        snare_t.add(snare(v0 + (v1 - v0) * p) * 0.8, S(b), p=0.0)
        b += 1 / div


# intro (bars 0-1): filtered pad + hook + ticking hats; roll into the drop
for bar in (0, 1):
    pad_bar(bar, cutoff=1300, gain=1.7)
    hook_bar(bar, gain=0.85, bright=0.8)
    for k in range(8):
        hat_t.add(hat(0.55 if k % 2 == 0 else 0.38, seed=k), S(bar * 4 + k * 0.5), p=0.3 if k % 2 else -0.3)
for b in (0, 2, 4):  # soft heartbeat kicks under the hook; beat 4 lands with the glitch
    kick_t.add(lp(kick(0.7), 2500), S(b))
snare_roll(6.0, 7.5)

# drop A (bars 2-9), tape stop applied later on beats 39.25-40
for bar in range(2, 10):
    groove_bar(bar)
    pad_bar(bar)
    hook_bar(bar)

# break (bars 10-11): lush pad + glassy arpeggio, build in bar 11
pad_bar(10, cutoff=3200, gain=1.7)
pad_bar(11, cutoff=3600, gain=1.5)
for k, m in enumerate([68, 72, 75, 79, 82, 79, 75, 72]):
    pluck_t.add(bell(m + 12, 1.4, ratio=2.0, index=1.4, tau=0.6), S(40 + k * 0.5), 0.7, p=0.4 * np.sin(k))
for k, m in enumerate([70, 74, 77, 82, 86, 82, 77, 74]):
    pluck_t.add(bell(m + 12, 1.0, ratio=2.0, index=1.4, tau=0.4), S(44 + k * 0.5), 0.55, p=0.4 * np.sin(k))
for k in range(16):
    hat_t.add(hat(0.25 + 0.03 * k, seed=k), S(44 + k * 0.25), p=0.2 * np.sin(k))
snare_roll(44.0, 47.5, v0=0.2)

# drop B (bars 12-15): everything + lead
for bar in range(12, 16):
    groove_bar(bar)
    pad_bar(bar)
    hook_bar(bar, gain=1.0, bright=1.15)
    lead_bar(bar, gain=0.75)

# trust (bars 16-17): muffled groove, build at the end
for bar in (16, 17):
    groove_bar(bar, hats_level=0.0, snare_on=bar == 16, rolls=False)
    pad_bar(bar, cutoff=1400)
    hook_bar(bar, gain=0.6, bright=0.7)
snare_roll(70.0, 71.5, v0=0.25)

# outro (bar 18): a chord per beat under the four recap hits
for k, ch in enumerate(LOOP):
    b = 72 + k
    kick_t.add(kick(1.0), S(b))
    kick_times.append(S(b))
    bass_notes.append((S(b), S(b + 1), ROOT[ch], None))
    pad_t.add(pad_chord(VOICING[ch], nsamp(S(1) + 0.4), cutoff=3000, attack=0.01, release=0.25, seed=40 + k), S(b), 1.15)
    for step in range(4):
        hat_t.add(hat(0.8 if step == 0 else 0.55, seed=step), S(b + step / 4))
snare_t.add(snare(1.0), S(73))
snare_t.add(snare(1.0), S(75))
for k in range(4):
    snare_t.add(snare(0.35 + 0.15 * k), S(75.5 + k * 0.125))
for step, m in HOOK[C]:
    pluck_t.add(pluck(m, bright=1.2), S(72 + step / 4))

# end (bar 19): the last chord rings out, a three-note signature, a final sub
kick_t.add(kick(1.0), S(76))
bass_notes.append((S(76), S(79.6), 36, None))
pad_t.add(pad_chord(VOICING[C] + [67, 74], nsamp(S(4)), cutoff=3800, attack=0.005, release=1.2, seed=99), S(76), 1.3)
for b, m in ((77, 79), (77.5, 75), (78, 72)):
    pluck_t.add(bell(m, 2.0, ratio=2.0, index=1.6, tau=0.9), S(b), 0.6)

bass_t.pre += pan(bass_line([n for n in bass_notes if n[0] < SPLIT - 1e-6], N + TAIL), 0)
bass_t.post += pan(bass_line([n for n in bass_notes if n[0] >= SPLIT - 1e-6], N + TAIL), 0)

# ═════════════════════════════════ sound effects ══════════════════════════════
fx_t = Track('sfx')


def whoosh(dur, f_lo=400, f_hi=4200, direction=0, up=False, seed=0):
    n = nsamp(dur)
    t = np.linspace(0, 1, n)
    shape = np.sin(np.pi * t) ** 1.5
    if up:
        fc = f_lo * (f_hi / f_lo) ** t
    else:
        fc = f_lo + (f_hi - f_lo) * np.sin(np.pi * t)
    nz = noise(n, 200 + seed)
    y = sweep(nz, 'bp', fc, q=1.6) * shape * 2.2
    if direction:
        return pan(y, direction * (t * 2 - 1) * 0.9)
    return np.stack([y, sweep(noise(n, 300 + seed), 'bp', fc, q=1.6) * shape * 2.2])


def impact(size=1.0):
    n = nsamp(3.2)
    t = secs(n)
    sub = np.sin(2 * np.pi * np.cumsum(30 + 60 * np.exp(-t / 0.15)) / SR) * np.exp(-t / (0.9 * size))
    crack = hp(noise(n, 5), 300) * np.exp(-t / 0.05)
    crash = hp(noise(n, 6), 3500) * np.exp(-t / (0.9 * size))
    mono = np.tanh(1.6 * sub) * 1.0 + 0.55 * crack + 0.35 * crash
    st = pan(mono, 0)
    st = st + 0.5 * convolve(pan(0.5 * crack + crash * 0.3, 0), IR_BIG)
    return st


def riser(dur):
    n = nsamp(dur)
    t = np.linspace(0, 1, n)
    fc = 300 * (9000 / 300) ** (t ** 1.4)
    nz = sweep(noise(n, 11), 'bp', fc, q=1.2)
    tone = sum(saw_blep(midi_hz(m) * 2 ** (t * 1.0), n) for m in (60, 67, 72)) / 3
    tone = sweep(tone, 'lp', fc * 0.6 + 200, q=0.9)
    env = t ** 2.2
    y = (1.6 * nz + 0.35 * tone) * env
    return np.stack([y * (1 - 0.15 * t), np.roll(y, 37) * (1 + 0.15 * t)])


def rev_cymbal(dur):
    n = nsamp(dur)
    t = secs(n)
    c = hp(noise(n, 12), 5000, order=4) * np.exp(-t / (dur * 0.35))
    c = c[::-1]
    return np.stack([c, np.roll(c, 23)]) * 0.9


def pop(pitch=0):
    n = nsamp(0.12)
    t = secs(n)
    f0 = 420 * 2 ** (pitch * 2 / 12)
    f = f0 + f0 * 1.2 * (1 - np.exp(-t / 0.02))
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.045)
    return fade(y, 0.0008, 0.01)


def tick():
    n = nsamp(0.03)
    t = secs(n)
    return fade(np.sin(2 * np.pi * 2600 * t) * np.exp(-t / 0.006) + 0.3 * hp(noise(n, 13), 4000) * np.exp(-t / 0.002), 0.0002, 0.003)


def key_click(seed):
    n = nsamp(0.045)
    t = secs(n)
    r = np.random.default_rng(seed)
    y = bp(noise(n, 40 + seed), 1800 + 1500 * r.random(), 7000) * np.exp(-t / 0.008)
    y += 0.4 * np.sin(2 * np.pi * (240 + 80 * r.random()) * t) * np.exp(-t / 0.012)
    return fade(y * (0.7 + 0.3 * r.random()), 0.0002, 0.004)


def blip(f=1600, dur=0.05):
    n = nsamp(dur)
    return fade(square_blep(f, n) * np.exp(-secs(n) / (dur * 0.4)) * 0.6, 0.0005, 0.005)


def tone_seq(freqs, step, dur, wave='sine', tau=0.08):
    n = nsamp(dur)
    out = np.zeros(nsamp(step * (len(freqs) - 1)) + n + 1)
    for k, f in enumerate(freqs):
        x = np.sin(2 * np.pi * f * secs(n)) if wave == 'sine' else square_blep(f, n) * 0.5
        x = x * np.exp(-secs(n) / tau)
        i = int(round(step * k * SR))
        put(out, fade(x, 0.001, 0.01), i)
    return out


def sparkle(dur):
    out = np.zeros((2, nsamp(dur + 1.5)))
    notes = [84, 86, 87, 91, 94, 96, 98, 99, 103]
    for k, m in enumerate(notes):
        b = bell(m, 1.2, ratio=3.0, index=1.0, tau=0.35)
        i = nsamp(dur * 0.6 * k / len(notes))
        put(out, pan(b, np.sin(k * 1.7) * 0.7) * 0.5, i)
    sh = hp(noise(out.shape[1], 14), 6000) * np.exp(-secs(out.shape[1]) / 0.5) * 0.25
    out += np.stack([sh, np.roll(sh, 50)])
    return out + 0.6 * convolve(out, IR_BIG)


def shimmer(dur):
    n = nsamp(dur)
    t = secs(n)
    y = sum(np.sin(2 * np.pi * f * t) for f in (3100, 3720, 4650, 5580)) / 4
    y *= 0.5 + 0.5 * np.sin(2 * np.pi * 14 * t)
    y = y * np.sin(np.pi * np.linspace(0, 1, n)) * 0.5 + 0.15 * hp(noise(n, 15), 7000) * np.sin(np.pi * np.linspace(0, 1, n))
    return np.stack([y, np.roll(y, 41)])


def buzz(dur):
    n = nsamp(dur)
    t = secs(n)
    gate = (((t / BEAT) % 0.5) < 0.45).astype(float)
    gate = lp(gate, 60)
    y = np.tanh(3 * np.sin(2 * np.pi * 165 * t) + 0.6 * np.sin(2 * np.pi * 333 * t))
    y = lp(y, 900) * gate * (0.8 + 0.2 * np.sin(2 * np.pi * 9 * t))
    return y


def crt():
    n = nsamp(0.5)
    t = secs(n)
    whine = np.sin(2 * np.pi * np.cumsum(15000 - 13000 * (t / 0.5)) / SR) * np.exp(-t / 0.12) * 0.25
    zap = hp(noise(n, 16), 2000) * np.exp(-t / 0.03)
    thump = np.sin(2 * np.pi * np.cumsum(120 * np.exp(-t / 0.1) + 30) / SR) * np.exp(-t / 0.12)
    return whine + 0.6 * zap + 0.8 * thump


def tape_stop_sfx(dur):
    """A low motor wind-down layered under the musical tape stop."""
    n = nsamp(dur)
    t = np.linspace(0, 1, n)
    f = 120 * (1 - t) ** 2 + 20
    y = np.tanh(2 * np.sin(2 * np.pi * np.cumsum(f) / SR)) * (1 - t) * 0.35
    return lp(y, 600)


def suck(dur):
    n = nsamp(dur)
    t = np.linspace(0, 1, n)
    fc = 5000 * (200 / 5000) ** t
    y = sweep(noise(n, 17), 'bp', fc, q=1.4) * t ** 2 * 2.0
    return np.stack([y, np.roll(y, 29)])


def heartbeat():
    out = np.zeros(nsamp(0.4))
    for off, a in ((0.0, 1.0), (0.14, 0.7)):
        n = nsamp(0.15)
        t = secs(n)
        x = np.sin(2 * np.pi * np.cumsum(55 + 40 * np.exp(-t / 0.02)) / SR) * np.exp(-t / 0.05) * a
        i = nsamp(off)
        put(out, x, i)
    return np.tanh(1.5 * out)


def hit():
    n = nsamp(0.6)
    t = secs(n)
    body = np.sin(2 * np.pi * np.cumsum(60 + 90 * np.exp(-t / 0.03)) / SR) * np.exp(-t / 0.12)
    crack = bp(noise(n, 18), 800, 6000) * np.exp(-t / 0.04)
    y = np.tanh(1.4 * (body + 0.7 * crack))
    st = pan(y, 0)
    return st + 0.35 * convolve(pan(crack * 0.6, 0), IR_SMALL)


def powerdown():
    n = nsamp(0.6)
    t = secs(n)
    f = 900 * np.exp(-t / 0.12) + 50
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.25) * 0.7


def powerup():
    n = nsamp(0.5)
    t = secs(n)
    f = 80 * (1500 / 80) ** np.clip(t / 0.35, 0, 1)
    y = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.clip(t / 0.3, 0, 1) * np.exp(-np.clip(t - 0.35, 0, None) / 0.05) * 0.6
    return y + np.pad(bell(91, 0.4, tau=0.15), (nsamp(0.33), 0))[:n] * 0.5


def downlifter(dur):
    n = nsamp(dur)
    t = np.linspace(0, 1, n)
    fc = 8000 * (150 / 8000) ** t
    y = sweep(noise(n, 19), 'bp', fc, q=1.0) * (1 - t) ** 1.5 * 1.8
    sub = np.sin(2 * np.pi * np.cumsum(90 * (1 - t) + 35) / SR) * (1 - t) * 0.5
    return np.stack([y + sub, np.roll(y, 31) + sub])


def thud():
    n = nsamp(0.4)
    t = secs(n)
    y = np.sin(2 * np.pi * np.cumsum(48 + 70 * np.exp(-t / 0.03)) / SR) * np.exp(-t / 0.13)
    return np.tanh(1.8 * y) + 0.3 * bp(noise(n, 20), 1500, 5000) * np.exp(-t / 0.01)


def decode(dur, seed=0):
    r = np.random.default_rng(300 + seed)
    out = np.zeros(nsamp(dur + 0.1))
    k = 0
    for tt in np.arange(0, dur, BEAT / 16):
        x = blip(1200 + 2800 * r.random(), 0.035) * (0.7 + 0.3 * r.random())
        i = nsamp(tt)
        put(out, x, i)
        k += 1
    return out


def glitch(dur, seed=0):
    r = np.random.default_rng(400 + seed)
    n = nsamp(dur)
    out = np.zeros(n)
    g = nsamp(BEAT / 8)
    for i in range(0, n, g):
        f = 150 + 1800 * r.random()
        seg_n = min(g, n - i)
        x = square_blep(f, seg_n) * 0.6 + noise(seg_n, int(r.integers(0, 1e6))) * 0.4
        x = np.round(x * 6) / 6  # crush
        x[: seg_n // 2] *= r.random() < 0.8
        out[i:i + seg_n] = x * (0.6 + 0.4 * r.random())
    out = lp(out, 7000)
    return np.stack([out, np.roll(out, 61)]) * 0.55


def slice_fx(dur):
    out = np.zeros((2, nsamp(dur + 0.3)))
    for k in range(8):
        w = whoosh(0.09, 1500, 7000, direction=1 if k % 2 else -1, seed=k)
        i = nsamp(dur * k / 8)
        put(out, w * 0.7, i)
    return out


def flick():
    n = nsamp(0.08)
    t = secs(n)
    return bp(noise(n, 21), 2000, 6000) * np.exp(-t / 0.015) * 1.2 + 0.4 * np.sin(2 * np.pi * 180 * t) * np.exp(-t / 0.02)


def ding(m):
    return bell(m, 1.0, ratio=3.5, index=1.8, tau=0.45)


def chime(dur):
    out = np.zeros(nsamp(dur + 1.5))
    for k, m in enumerate([72, 79, 84, 88]):
        b = bell(m, 2.0, ratio=2.0, index=0.9, tau=0.9)
        i = nsamp(k * 0.09)
        put(out, b * 0.4, i)
    return out


def layer(*xs):
    """Sum sounds of different lengths (mono → centred stereo), padded to the longest."""
    st = [x if x.ndim == 2 else pan(x, 0) for x in xs]
    n = max(x.shape[1] for x in st)
    return sum(np.pad(x, ((0, 0), (0, n - x.shape[1]))) for x in st)


IR_SMALL = reverb_ir(0.9, damp_hz=7000, predelay=0.01, seed=3)
IR_BIG = reverb_ir(2.6, damp_hz=5000, predelay=0.03, seed=4)
IR_PAD = reverb_ir(3.4, damp_hz=4500, predelay=0.04, seed=5)

LEVEL = {  # dB, relative — tuned so every cue sits at or above the music it plays over (see README)
    'pop': -6, 'glitch': -4, 'riser': -12, 'suck': -10, 'heartbeat': -5, 'revCymbal': -11, 'impact': -3,
    'typeRun': -3, 'shimmer': -5, 'zoomIn': -6, 'whoosh': -5, 'tick': -4, 'switchOn': -3, 'tap': -2,
    'blob': -7, 'decode': -2, 'tickRun': -8, 'hit': -5, 'ding': -7, 'send': -7, 'success': -6,
    'ping': -7, 'blip': -8, 'buzz': -5, 'missed': -6, 'plug': -4, 'btBlip': -7, 'wifi': -7,
    'tapeStop': -9, 'crt': -6, 'sparkle': -7, 'flip': -6, 'notify': -6, 'flick': -1, 'check': -6,
    'chime': -8, 'slice': -5, 'powerDown': -7, 'powerUp': -6, 'downlifter': -9, 'thud': -4,
}


def sfx(cue):
    t = cue['t']
    L = cue.get('len', 0) * BEAT
    if t == 'pop':
        return pop(cue.get('pitch', 0))
    if t == 'glitch':
        return glitch(L, seed=int(cue['b'] * 4))
    if t == 'riser':
        return riser(L)
    if t == 'suck':
        return suck(L)
    if t == 'heartbeat':
        return heartbeat()
    if t == 'revCymbal':
        return rev_cymbal(L)
    if t == 'impact':
        return impact(cue.get('size', 1.0))
    if t == 'typeRun':
        n = cue.get('n', 8)
        out = np.zeros(nsamp(L + 0.1))
        r = np.random.default_rng(int(cue['b'] * 10))
        for k in range(n):
            x = key_click(k + int(cue['b'] * 100))
            i = nsamp(L * (k + 0.3 * r.random()) / n)
            put(out, x, i)
        return out
    if t == 'tickRun':
        n = cue.get('n', 8)
        out = np.zeros(nsamp(L + 0.05))
        for k in range(n):
            x = tick() * (0.6 + 0.4 * k / n)
            i = nsamp(L * k / n)
            put(out, x, i)
        return out
    if t == 'shimmer':
        return shimmer(L)
    if t == 'zoomIn':
        return whoosh(L, 200, 6000, up=True, seed=int(cue['b']))
    if t == 'whoosh':
        return whoosh(L, 300, 3800, direction=cue.get('dir', 0), up=bool(cue.get('up')), seed=int(cue['b']))
    if t == 'tick':
        return tick()
    if t in ('switchOn', 'tap'):
        return layer(tick() * 0.8, np.concatenate([np.zeros(nsamp(0.012)), tick()]) * 0.5)
    if t == 'blob':
        n = nsamp(L)
        tt = secs(n)
        bl = np.sin(2 * np.pi * np.cumsum(180 + 500 * (tt / L) ** 2) / SR) * np.sin(np.pi * tt / L) * 0.6
        return layer(bl, whoosh(L, 200, 3000, up=True, seed=5) * 0.8)
    if t == 'decode':
        return decode(L, seed=int(cue['b']))
    if t == 'hit':
        return hit()
    if t == 'ding':
        return ding([84, 87, 91][cue.get('note', 0)])
    if t == 'send':
        return whoosh(0.28, 900, 7000, up=True, seed=int(cue['b'] * 3))
    if t == 'success':
        return tone_seq([midi_hz(91), midi_hz(96)], 0.07, 0.35, tau=0.12)
    if t == 'ping':
        p = np.sin(2 * np.pi * 1250 * secs(nsamp(0.9))) * env_exp(nsamp(0.9), 0.22, 0.004)
        return pan(p, 0) + 0.5 * convolve(pan(p, 0), IR_SMALL)
    if t == 'blip':
        return blip(1700, 0.06)
    if t == 'buzz':
        return buzz(L)
    if t == 'missed':
        return tone_seq([midi_hz(76), midi_hz(72)], 0.12, 0.2, tau=0.08)
    if t == 'plug':
        n = nsamp(0.25)
        tt = secs(n)
        th = np.sin(2 * np.pi * np.cumsum(70 + 60 * np.exp(-tt / 0.02)) / SR) * np.exp(-tt / 0.06)
        return th + 0.5 * hp(noise(n, 22), 3000) * np.exp(-tt / 0.006) + 0.2 * hp(noise(n, 23), 6000) * np.exp(-tt / 0.05)
    if t == 'btBlip':
        return tone_seq([midi_hz(83), midi_hz(90)], 0.07, 0.08, tau=0.03)
    if t == 'wifi':
        return tone_seq([midi_hz(79), midi_hz(84), midi_hz(88)], 0.05, 0.08, tau=0.03)
    if t == 'tapeStop':
        return tape_stop_sfx(L)
    if t == 'crt':
        return crt()
    if t == 'sparkle':
        return sparkle(L)
    if t == 'flip':
        return layer(whoosh(L, 600, 5000, direction=-1, seed=8), flick() * 0.6)
    if t == 'notify':
        return tone_seq([midi_hz(84), midi_hz(91)], 0.11, 0.5, tau=0.18)
    if t == 'flick':
        return flick()
    if t == 'check':
        return layer(tone_seq([midi_hz(88 + 3 * cue.get('pitch', 0))], 0.0, 0.18, tau=0.05), flick() * 0.35)
    if t == 'chime':
        return chime(L)
    if t == 'slice':
        return slice_fx(L)
    if t == 'powerDown':
        return powerdown()
    if t == 'powerUp':
        return powerup()
    if t == 'downlifter':
        return downlifter(L)
    if t == 'thud':
        return thud()
    raise ValueError('unknown sfx ' + t)


for cue in TL['SFX']:
    # 1 ms in / 4 ms out: risers still slam shut into the pre-drop silence, without a digital click
    x = fade(sfx(cue), 0.001, 0.004)
    fx_t.add(x, S(cue['b']), db(LEVEL[cue['t']]), p=0.0)

# ═════════════════════════════════ mix ════════════════════════════════════════
def duck_curve(times, depth=0.55, release=0.16):
    """Sidechain gain from kick times (instant duck, exponential recovery)."""
    g = np.ones(N + TAIL)
    for tk in times:
        i = nsamp(tk)
        n = min(len(g) - i, nsamp(0.6))
        if n <= 0:
            continue
        a = min(n, nsamp(0.004))
        shape = 1 - depth * np.exp(-secs(n) / release)
        shape[:a] = np.linspace(1, shape[a], a) if a > 1 else shape[:a]
        g[i:i + n] = np.minimum(g[i:i + n], shape)
    return g


duck = duck_curve(kick_times)


def mixdown(part):
    g = lambda tr: getattr(tr, part)  # noqa: E731
    m = np.zeros((2, N + TAIL))
    m += g(kick_t) * db(-3)
    m += g(snare_t) * db(-3.5)
    m += g(hat_t) * db(-8)
    m += g(bass_t) * db(-6)
    padx = hp(g(pad_t), 220)  # leave the low end to the 808; kills the 250-500 Hz mud build-up
    m += (padx * db(-14) + convolve(padx, IR_PAD) * db(-20)) * duck
    pl = g(pluck_t)
    m += (pl * db(-11) + pingpong(pl.mean(axis=0), BEAT * 0.75, 0.38) * db(-17) + convolve(pl, IR_BIG) * db(-20)) * duck
    m += (g(lead_t) * db(-17) + convolve(g(lead_t), IR_BIG) * db(-22)) * duck
    m += convolve(g(snare_t), IR_SMALL) * db(-17)
    return m


if os.environ.get('DUMP_STEMS'):
    np.savez_compressed(os.environ['DUMP_STEMS'], **{t.name: t.buf[:, :N].astype(np.float32) for t in
                        (kick_t, snare_t, hat_t, bass_t, pad_t, pluck_t, lead_t, fx_t)})

music_pre = mixdown('pre')
music_post = mixdown('post')


def span(b0, b1):
    # exact rounding: nsamp() clamps to >= 1 sample, which would shift a span starting at beat 0
    return int(round(S(b0) * SR)), int(round(S(b1) * SR))


def lowpass_span(x, b0, b1, points, xfade=0.01):
    """One continuous low-pass sweep over [b0, b1); points = [(beat, cutoff_hz), …] (log-interpolated).
    Filtering starts `xfade` s early and crossfades in, so entering the span cannot click."""
    i0, i1 = span(b0, b1)
    m = nsamp(xfade) if i0 > 0 else 0
    a = i0 - m
    bt = np.arange(a, i1) / SR / BEAT
    pb = np.array([p[0] for p in points])
    pf = np.log(np.array([p[1] for p in points], float))
    fc = np.exp(np.interp(bt, pb, pf))
    y = sweep(x[:, a:i1], 'lp', fc, q=0.75)
    if m:
        ramp = np.linspace(0, 1, m)
        y[:, :m] = x[:, a:i0] * (1 - ramp) + y[:, :m] * ramp
    x[:, a:i1] = y


# tape stop: the groove winds down to nothing on beats 39.25-40; nothing from before survives it
i0, i1 = span(39.25, 40)
seg = music_pre[:, i0:i1].copy()
m = i1 - i0
pos = np.clip(np.cumsum(np.linspace(1, 0, m)), 0, m - 1)
for ch in range(2):
    music_pre[ch, i0:i1] = np.interp(pos, np.arange(m), seg[ch]) * np.linspace(1, 0.15, m) ** 0.7
f = nsamp(0.03)  # land on true silence, not on a step
music_pre[:, i1 - f:i1] *= np.linspace(1, 0, f)
music_pre[:, i1:] = 0
music = music_pre + music_post

# intro opens up; the trust section is heard as through a wall, then bursts open
lowpass_span(music, 0, 7.5, [(0, 1200), (7.5, 9000)])
lowpass_span(music, 64, 71.5, [(64, 650), (70, 1100), (71.5, 12000)])

# pre-drop gaps: hard silence for the music (5 ms fades)
for g0, g1 in TL['GAPS']:
    a, b = span(g0, g1)
    f = nsamp(0.005)
    music[:, a - f:a] *= np.linspace(1, 0, f)
    music[:, a:b] = 0
    music[:, b:b + f] *= np.linspace(0, 1, f)

# outro: fade the tail into silence by 60.0 s
f0, f1 = nsamp(S(78.4)), N
music[:, f0:f1] *= np.linspace(1, 0, f1 - f0) ** 1.5
music[:, f1:] = 0

fx = fx_t.buf.copy()
fx[:, f0:f1] *= np.linspace(1, 0, f1 - f0) ** 1.2
fx[:, f1:] = 0

# the music bows under the effects (sidechain from the SFX bus, above 200 Hz, max ~-5 dB)
fx_env = follower(hp(fx.mean(axis=0), 200), attack=0.004, release=0.18)
mu_env = follower(hp(music.mean(axis=0), 200), attack=0.01, release=0.25)
fx_duck = np.clip(1 - 0.75 * fx_env / (fx_env + mu_env + 1e-6), 0.56, 1.0)
music = music * fx_duck

mix = music + fx * db(-1)
if os.environ.get('DUMP_BUSES'):
    np.savez_compressed(os.environ['DUMP_BUSES'], music=music[:, :N].astype(np.float32), fx=(fx * db(-1))[:, :N].astype(np.float32))
mix = hp(mix, 28)
mix = mix[:, :N]


def measure(x):
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        path = tmp.name
    wavfile.write(path, SR, (np.clip(x, -1, 1).T * 32767).astype(np.int16))
    r = subprocess.run(['ffmpeg', '-hide_banner', '-nostats', '-i', path, '-af', 'ebur128=peak=true', '-f', 'null', '-'],
                       capture_output=True, text=True)
    os.unlink(path)
    txt = r.stderr
    i = float(re.findall(r'I:\s+(-?[\d.]+) LUFS', txt)[-1])
    tp = float(re.findall(r'Peak:\s+(-?[\d.]+) dBFS', txt)[-1])
    return i, tp


TARGET = -14.0
mix = mix / np.max(np.abs(mix)) * 0.5
for _ in range(4):
    out = limiter(mix, ceiling_db=-1.2)
    lufs, tp = measure(out)
    if abs(lufs - TARGET) < 0.25:
        break
    mix *= db(TARGET - lufs)
out = limiter(mix, ceiling_db=-1.2)
lufs, tp = measure(out)
print(f'soundtrack: {lufs:.1f} LUFS integrated, true peak {tp:.1f} dBFS, {out.shape[1] / SR:.2f} s')
wavfile.write(OUT, SR, np.clip(out, -1, 1).T.astype(np.float32))
