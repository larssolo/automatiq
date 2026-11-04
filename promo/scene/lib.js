/* Deterministic animation toolkit. Nothing here reads the wall clock: every value is a pure
 * function of the beat position handed in by renderFrame(t), so any frame can be rendered in any
 * order (the renderer splits the film across parallel workers). */
(function () {
  const L = {};

  L.clamp = (v, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
  L.lerp = (a, b, p) => a + (b - a) * p;
  /** Progress of x through [a, b], clamped to 0..1. */
  L.seg = (x, a, b) => L.clamp((x - a) / (b - a));
  /** Map x from [a, b] to [c, d] (clamped) through an optional easing. */
  L.map = (x, a, b, c, d, ease) => L.lerp(c, d, (ease || L.linear)(L.seg(x, a, b)));

  // ── Easings (p in 0..1)
  L.linear = (p) => p;
  L.inQuad = (p) => p * p;
  L.outQuad = (p) => 1 - (1 - p) * (1 - p);
  L.inCubic = (p) => p * p * p;
  L.outCubic = (p) => 1 - Math.pow(1 - p, 3);
  L.inOutCubic = (p) => (p < 0.5 ? 4 * p * p * p : 1 - Math.pow(-2 * p + 2, 3) / 2);
  L.outQuart = (p) => 1 - Math.pow(1 - p, 4);
  L.inQuart = (p) => p * p * p * p;
  L.outQuint = (p) => 1 - Math.pow(1 - p, 5);
  L.inOutQuint = (p) => (p < 0.5 ? 16 * p ** 5 : 1 - Math.pow(-2 * p + 2, 5) / 2);
  L.outExpo = (p) => (p >= 1 ? 1 : 1 - Math.pow(2, -10 * p));
  L.inExpo = (p) => (p <= 0 ? 0 : Math.pow(2, 10 * p - 10));
  L.inOutExpo = (p) =>
    p <= 0 ? 0 : p >= 1 ? 1 : p < 0.5 ? Math.pow(2, 20 * p - 10) / 2 : (2 - Math.pow(2, -20 * p + 10)) / 2;
  L.outBack = (p, s = 1.70158) => 1 + (s + 1) * Math.pow(p - 1, 3) + s * Math.pow(p - 1, 2);
  L.inBack = (p, s = 1.70158) => (s + 1) * p * p * p - s * p * p;
  L.outSine = (p) => Math.sin((p * Math.PI) / 2);
  L.inOutSine = (p) => -(Math.cos(Math.PI * p) - 1) / 2;

  /** Under-damped spring settling from 0 to 1. `t` in beats since release. */
  L.spring = (t, freq = 2.2, damp = 5.5) => {
    if (t <= 0) return 0;
    return 1 - Math.exp(-damp * t) * Math.cos(2 * Math.PI * freq * t);
  };
  /** Exponential decay envelope that starts at 1 when x == at and falls with `rate` per beat. */
  L.decay = (x, at, rate = 6) => (x < at ? 0 : Math.exp(-(x - at) * rate));
  /** Smooth bump: 0 → 1 → 0 across [a, b]. */
  L.bump = (x, a, b) => {
    const p = L.seg(x, a, b);
    return Math.sin(p * Math.PI);
  };

  // ── Deterministic randomness (mulberry32) and value noise
  L.rng = (seed) => {
    let a = seed >>> 0;
    return () => {
      a = (a + 0x6d2b79f5) >>> 0;
      let t = a;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  };
  L.hash = (n) => {
    const s = Math.sin(n * 127.1 + 311.7) * 43758.5453123;
    return s - Math.floor(s);
  };
  L.noise = (x) => {
    const i = Math.floor(x);
    const f = x - i;
    const u = f * f * (3 - 2 * f);
    return L.lerp(L.hash(i), L.hash(i + 1), u) * 2 - 1;
  };

  // ── DOM
  L.el = (tag, cls, parent, html) => {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (html != null) e.innerHTML = html;
    if (parent) parent.appendChild(e);
    return e;
  };
  L.div = (cls, parent, html) => L.el('div', cls, parent, html);
  L.css = (e, styles) => {
    Object.assign(e.style, styles);
    return e;
  };
  L.icon = (name, size, color, cls) => {
    const path = window.ICONS[name];
    if (!path) throw new Error('missing icon ' + name);
    // size may be a number (px) or any CSS length such as calc(22 * var(--dp)); SVG presentation
    // attributes reject calc(), so it always goes through the style attribute.
    const s = typeof size === 'number' ? size + 'px' : size;
    return `<svg class="ic ${cls || ''}" viewBox="0 0 24 24" style="width:${s};height:${s}" fill="${
      color || 'currentColor'
    }">${path}</svg>`;
  };

  /** Write transform/opacity/filter in one go. Only keys present are applied. Units: px, deg. */
  L.tf = (e, o) => {
    let t = '';
    if (o.persp) t += `perspective(${o.persp}px) `;
    if (o.x || o.y || o.z) t += `translate3d(${o.x || 0}px,${o.y || 0}px,${o.z || 0}px) `;
    if (o.rx) t += `rotateX(${o.rx}deg) `;
    if (o.ry) t += `rotateY(${o.ry}deg) `;
    if (o.r) t += `rotate(${o.r}deg) `;
    if (o.s != null && o.s !== 1) t += `scale(${o.s}) `;
    if (o.sx != null || o.sy != null) t += `scale(${o.sx == null ? 1 : o.sx},${o.sy == null ? 1 : o.sy}) `;
    if (o.skx) t += `skewX(${o.skx}deg) `;
    e.style.transform = t || 'none';
    if (o.o != null) e.style.opacity = o.o <= 0.001 ? 0 : o.o >= 0.999 ? 1 : o.o.toFixed(3);
    if (o.blur != null || o.f != null) {
      let f = '';
      if (o.blur > 0.05) f += `blur(${o.blur.toFixed(2)}px) `;
      if (o.f) f += o.f;
      e.style.filter = f || 'none';
    }
    return e;
  };
  L.show = (e, on) => {
    const v = on ? '' : 'none';
    if (e.style.display !== v) e.style.display = v;
  };

  /**
   * Kinetic type: words/lines that slam in on given beats.
   * items: [{ text, at, cls }] — `at` in local beats. Returns { root, render(lb, exitAt) }.
   */
  L.kinetic = (parent, cls, items, opts = {}) => {
    const root = L.div('kin ' + (cls || ''), parent);
    const nodes = items.map((it) => {
      const n = L.el('span', 'kw ' + (it.cls || ''), root, it.text);
      if (it.br) L.el('br', null, root);
      return { n, it };
    });
    const style = opts.style || 'slam';
    return {
      root,
      render(lb, exitAt = Infinity, exitDur = 0.35) {
        const ex = L.seg(lb, exitAt, exitAt + exitDur);
        nodes.forEach(({ n, it }, i) => {
          const k = lb - it.at;
          if (k < 0) {
            n.style.opacity = 0;
            return;
          }
          let s = 1, y = 0, blur = 0, o = 1, r = 0;
          if (style === 'slam') {
            const p = L.seg(k, 0, 0.32);
            s = L.lerp(1.55, 1, L.outQuart(p));
            blur = L.lerp(14, 0, L.outCubic(p));
            o = L.seg(k, 0, 0.08);
          } else if (style === 'rise') {
            const p = L.seg(k, 0, 0.45);
            y = L.lerp(70, 0, L.outQuint(p));
            blur = L.lerp(10, 0, L.outCubic(p));
            o = L.seg(k, 0, 0.18);
          } else if (style === 'drop') {
            const p = L.seg(k, 0, 0.5);
            y = L.lerp(-90, 0, L.outBack(p, 2.2));
            r = L.lerp(-6, 0, L.outCubic(p));
            o = L.seg(k, 0, 0.1);
          }
          if (ex > 0) {
            const q = L.inCubic(ex);
            y += -60 * q - i * 6 * q;
            o *= 1 - q;
            blur += 12 * q;
          }
          L.tf(n, { y, s, r, o, blur });
        });
      },
    };
  };

  /** Typewriter: reveal `full` progressively; returns visible string at progress p (0..1). */
  L.typed = (full, p) => full.slice(0, Math.round(full.length * L.clamp(p)));

  /** Decode/scramble effect: characters settle left→right from random glyphs. */
  L.scramble = (target, p, seed = 1) => {
    const glyphs = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789{}#$%&*<>/';
    let s = '';
    for (let i = 0; i < target.length; i++) {
      const settle = (i + 1) / target.length;
      if (p >= settle || target[i] === ' ') s += target[i];
      else s += glyphs[Math.floor(L.hash(seed * 31 + i * 7 + Math.floor(p * 24)) * glyphs.length)];
    }
    return s;
  };

  window.L = L;
})();
