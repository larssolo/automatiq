"""Small DSP toolkit for the promo soundtrack: oscillators, envelopes, filters, reverb, limiter.

Everything is plain numpy + scipy.signal and deterministic (seeded noise), so the soundtrack is
bit-identical between renders.
"""
import numpy as np
from scipy import signal

SR = 48000
_rng = np.random.default_rng(80)


def secs(n):
    return np.arange(n) / SR


def nsamp(seconds):
    return max(1, int(round(seconds * SR)))


def noise(n, seed=None):
    r = np.random.default_rng(seed) if seed is not None else _rng
    return r.uniform(-1, 1, n)


def midi_hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


# ── envelopes ────────────────────────────────────────────────────────────────
def env_exp(n, tau, attack=0.001):
    t = secs(n)
    e = np.exp(-t / tau)
    a = nsamp(attack)
    if a > 1:
        e[:a] *= np.linspace(0, 1, a)
    return e


def env_adsr(n, a, d, s, r):
    """Gate length = n minus release; all times in seconds."""
    na, nd, nr = nsamp(a), nsamp(d), nsamp(r)
    ns = max(0, n - na - nd - nr)
    e = np.concatenate([
        np.linspace(0, 1, na, endpoint=False),
        np.linspace(1, s, nd, endpoint=False),
        np.full(ns, s),
        np.linspace(s, 0, nr),
    ])
    return e[:n] if len(e) >= n else np.pad(e, (0, n - len(e)))


def fade(x, fin=0.002, fout=0.01):
    x = x.copy()
    a, b = nsamp(fin), nsamp(fout)
    if a > 1:
        x[..., :a] *= np.linspace(0, 1, a)
    if b > 1:
        x[..., -b:] *= np.linspace(1, 0, b)
    return x


# ── oscillators ──────────────────────────────────────────────────────────────
def saw_blep(freq, n=None, phase0=0.0):
    """Band-limited sawtooth (polyBLEP), freq scalar or per-sample array."""
    f = np.asarray(freq, float)
    if n is not None:
        f = np.broadcast_to(f, (n,))
    dt = f / SR
    p = (phase0 + np.cumsum(dt)) % 1.0
    y = 2 * p - 1
    m1 = p < dt
    t1 = p[m1] / dt[m1]
    y[m1] -= t1 + t1 - t1 * t1 - 1
    m2 = p > 1 - dt
    t2 = (p[m2] - 1) / dt[m2]
    y[m2] -= t2 * t2 + t2 + t2 + 1
    return y


def square_blep(freq, n):
    return 0.5 * (saw_blep(freq, n) - saw_blep(freq, n, phase0=0.5))


def fm(freq, n, ratio=2.0, index=3.0, index_tau=0.12, amp_tau=0.4, attack=0.002):
    """Two-operator FM pluck/bell."""
    t = secs(n)
    I = index * np.exp(-t / index_tau)
    mod = np.sin(2 * np.pi * freq * ratio * t)
    car = np.sin(2 * np.pi * freq * t + I * mod)
    return car * env_exp(n, amp_tau, attack)


# ── filters ──────────────────────────────────────────────────────────────────
def _sos(kind, fc, order=2):
    nyq = SR / 2
    if kind == 'bp':
        lo, hi = fc
        return signal.butter(order, [max(10, lo) / nyq, min(hi, nyq * 0.98) / nyq], 'bandpass', output='sos')
    return signal.butter(order, min(fc, nyq * 0.98) / nyq, {'lp': 'lowpass', 'hp': 'highpass'}[kind], output='sos')


def lp(x, fc, order=2):
    return signal.sosfilt(_sos('lp', fc, order=order), x, axis=-1)


def hp(x, fc, order=2):
    return signal.sosfilt(_sos('hp', fc, order=order), x, axis=-1)


def bp(x, lo, hi, order=2):
    return signal.sosfilt(_sos('bp', (lo, hi), order=order), x, axis=-1)


def biquad_coeffs(kind, fc, q):
    """RBJ cookbook biquad as a single SOS row."""
    w0 = 2 * np.pi * min(max(fc, 20.0), SR * 0.45) / SR
    cw, sw = np.cos(w0), np.sin(w0)
    alpha = sw / (2 * q)
    if kind == 'lp':
        b = [(1 - cw) / 2, 1 - cw, (1 - cw) / 2]
    elif kind == 'hp':
        b = [(1 + cw) / 2, -(1 + cw), (1 + cw) / 2]
    else:  # band-pass, constant 0 dB peak
        b = [alpha, 0, -alpha]
    a = [1 + alpha, -2 * cw, 1 - alpha]
    return np.array([[b[0] / a[0], b[1] / a[0], b[2] / a[0], 1.0, a[1] / a[0], a[2] / a[0]]])


def sweep(x, kind, fc_curve, q=0.8, block=128):
    """Time-varying biquad: fc_curve is per-sample cutoff (Hz). Mono or (2, n) input."""
    x = np.atleast_2d(x)
    out = np.zeros_like(x)
    for ch in range(x.shape[0]):
        zi = np.zeros((1, 2))
        for i in range(0, x.shape[1], block):
            sos = biquad_coeffs(kind, float(fc_curve[min(i + block // 2, len(fc_curve) - 1)]), q)
            out[ch, i:i + block], zi = signal.sosfilt(sos, x[ch, i:i + block], zi=zi)
    return out if out.shape[0] > 1 else out[0]


# ── space ────────────────────────────────────────────────────────────────────
def reverb_ir(seconds=2.4, damp_hz=6000, predelay=0.02, seed=7, width=1.0):
    n = nsamp(seconds)
    r = np.random.default_rng(seed)
    t = secs(n)
    env = np.exp(-6.9 * t / seconds)  # -60 dB at `seconds`
    ir = np.stack([r.uniform(-1, 1, n), r.uniform(-1, 1, n)]) * env
    # high frequencies die faster: blend a low-passed copy in over time
    lo = lp(ir, damp_hz, order=1)
    mixc = np.clip(t / (seconds * 0.5), 0, 1)
    ir = ir * (1 - mixc) + lo * mixc
    if width < 1:
        mid = ir.mean(axis=0)
        ir = mid + (ir - mid) * width
    ir = np.pad(ir, ((0, 0), (nsamp(predelay), 0)))
    return ir / np.sqrt(np.sum(ir ** 2) / 2)


def convolve(x, ir):
    """x: (2, n) → (2, n) wet signal (tail truncated to len(x))."""
    out = np.zeros_like(x)
    for ch in range(2):
        out[ch] = signal.fftconvolve(x[ch], ir[ch])[: x.shape[1]]
    return out


def pingpong(x, delay_s, feedback=0.35, taps=6, hp_hz=300, lp_hz=6000):
    """Stereo ping-pong delay on a mono input → (2, n) wet."""
    n = len(x)
    d = nsamp(delay_s)
    wet = np.zeros((2, n))
    src = lp(hp(x, hp_hz), lp_hz)
    g = 1.0
    for k in range(1, taps + 1):
        g *= feedback if k > 1 else 1.0
        off = d * k
        if off >= n:
            break
        wet[k % 2, off:] += g * src[: n - off]
    return wet


# ── dynamics ─────────────────────────────────────────────────────────────────
def follower(x, attack=0.003, release=0.12):
    """Peak envelope of mono x (one-pole attack/release, vectorised in blocks)."""
    a = np.exp(-1 / (attack * SR))
    r = np.exp(-1 / (release * SR))
    ax = np.abs(x)
    # down-sample for speed, then interpolate back
    hop = 32
    m = ax[: len(ax) // hop * hop].reshape(-1, hop).max(axis=1)
    e = np.zeros_like(m)
    prev = 0.0
    aa, rr = a ** hop, r ** hop
    for i, v in enumerate(m):
        prev = aa * prev + (1 - aa) * v if v > prev else rr * prev + (1 - rr) * v
        e[i] = prev
    env = np.repeat(e, hop)
    return np.pad(env, (0, len(x) - len(env)), mode='edge')


def limiter(x, ceiling_db=-1.0, lookahead=0.004, release=0.08):
    """Look-ahead brick-wall peak limiter on (2, n), with 4x oversampled peak detection."""
    ceil = 10 ** (ceiling_db / 20)
    up = signal.resample_poly(x, 4, 1, axis=1)
    peak = np.abs(up).max(axis=0).reshape(-1, 4).max(axis=1)[: x.shape[1]]
    if len(peak) < x.shape[1]:
        peak = np.pad(peak, (0, x.shape[1] - len(peak)))
    need = np.minimum(1.0, ceil / np.maximum(peak, 1e-9))
    la = nsamp(lookahead)
    from scipy.ndimage import minimum_filter1d, uniform_filter1d
    # h[n] = min(need) over [n-la, n+la]; a causal moving average of length la over h is then
    # <= need at every peak (all averaged values already hold the reduction) yet ramps smoothly.
    h = minimum_filter1d(need, size=2 * la + 1)
    ma = uniform_filter1d(h, size=la, origin=(la - 1) // 2)
    g = np.minimum(ma, h)
    # release: gain may fall instantly (it is already smooth) but recovers exponentially
    hop = 16
    rel = np.exp(-hop / (release * SR))
    blocks = g[: len(g) // hop * hop].reshape(-1, hop).min(axis=1)
    out = np.empty_like(blocks)
    cur = 1.0
    for i, v in enumerate(blocks):
        cur = v if v < cur else rel * cur + (1 - rel) * v
        out[i] = cur
    env = np.repeat(out, hop)
    env = np.pad(env, (0, len(g) - len(env)), constant_values=env[-1] if len(env) else 1.0)
    return x * np.minimum(env, g)


def pan(x, p):
    """Equal-power pan of mono x; p in [-1, 1] (scalar or per-sample)."""
    a = (np.asarray(p) + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)])


def db(v):
    return 10 ** (v / 20)
