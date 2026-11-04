/* Shot registry + helpers shared by every shot (captions, transitions, touch indicator, tiles).
 * A shot is { pre, post, build(root, ctx), render(lb, ctx) } where lb is the local beat
 * (0 at the shot's in-point). The shot's root transform is reserved for transitions; shots move
 * their own content through an inner "cam" layer. */
(function () {
  window.SHOTS = {};
  const SH = {};

  // ── Stage format. Portrait (1080x1920) is the original composition; landscape (1920x1080) re-composes
  // it: shots build their visuals in the portrait "design space" and SH.vis() places that space into
  // the right-hand column (see style.css). Portrait code paths are unchanged.
  SH.land = window.STAGE.land;
  SH.W = window.STAGE.W;
  SH.H = window.STAGE.H;
  SH.CX = SH.W / 2;
  SH.CY = SH.H / 2;
  /** Transition travel distances (the portrait values are the originals). */
  const K = SH.land ? { whip: 2200, push: 1120, persp: 2600 } : { whip: 1250, push: 2000, persp: 1700 };
  SH.K = K;

  /**
   * Placement of a design-space point (dx, dy) at screen point `to`, scaled by s. Landscape only;
   * `to` defaults to the centre of the right-hand column.
   */
  SH.place = (dx, dy, s, to = [SH.W * 0.75, SH.H / 2]) => ({ d: [dx, dy], to, s });
  /** The container a shot should put its visuals in: the shot root in portrait, a 1080x1920 design
   *  canvas transformed into place in landscape. Backgrounds and full-bleed layers stay on the root. */
  SH.vis = (parent, place) => {
    if (!SH.land || !place) return parent;
    const w = L.div('abs', parent);
    L.css(w, {
      left: 0, top: 0, width: '1080px', height: '1920px', transformOrigin: '0 0',
      transform: `translate(${place.to[0] - place.s * place.d[0]}px, ${place.to[1] - place.s * place.d[1]}px) scale(${place.s})`,
    });
    return w;
  };
  /** Screen position of a design-space point (identity in portrait). */
  SH.map = (place, x, y) => (SH.land && place
    ? [place.to[0] + place.s * (x - place.d[0]), place.to[1] + place.s * (y - place.d[1])]
    : [x, y]);

  /**
   * Caption groups at the top of the frame. groups: [{ at, out, cls, style, words: [[html, dt, cls]] }]
   * Each group slams in word by word (dt = offset in beats from `at`) and leaves at `out`.
   */
  const capBoxes = [];
  SH.caps = (root, groups, opts = {}) => {
    const box = L.div('cap shadow-txt', root);
    if (opts.top != null) box.style.top = opts.top + 'px';
    const ks = groups.map((g) => {
      const k = L.kinetic(
        box,
        g.cls || 'h-l',
        g.words.map(([text, dt, cls, br]) => ({ text, at: g.at + (dt || 0), cls, br })),
        { style: g.style || 'slam' }
      );
      k.root.style.top = (g.y || 0) + 'px';
      return { k, g };
    });
    if (SH.land) capBoxes.push({ root, box, ks, dy: opts.dy || 0 });
    return (lb) => {
      ks.forEach(({ k, g }) => {
        // `out` = gone by then: the exit runs just before it, so it never overlaps the next slam
        const dur = g.exitDur || 0.16;
        const exitAt = g.out == null ? Infinity : g.out - dur;
        const visible = lb >= g.at - 0.01 && lb < (g.out == null ? Infinity : g.out);
        L.show(k.root, visible);
        if (visible) k.render(lb, exitAt, dur);
      });
    };
  };

  /**
   * Landscape: centre every caption block vertically in the left column. Needs real font metrics, so
   * it runs once after the fonts have loaded (main.js). A block is the group stack of one shot: the
   * title plus any sub-line below it (offset by g.y).
   */
  SH.relayout = () => {
    capBoxes.forEach(({ root, box, ks, dy }) => {
      const prev = root.style.display;
      root.style.display = 'block';
      let bottom = 0;
      ks.forEach(({ k, g }) => {
        k.root.style.display = '';
        bottom = Math.max(bottom, (g.y || 0) + k.root.offsetHeight);
      });
      root.style.display = prev;
      box.style.top = Math.round((SH.H - bottom) / 2 + dy) + 'px';
    });
  };

  /** A finger: soft white disc that lands at `at`, plus a ripple ring. */
  SH.touch = (parent) => {
    const wrap = L.div('abs', parent);
    const disc = L.div('abs', wrap);
    const ring = L.div('abs', wrap);
    L.css(disc, { width: '120px', height: '120px', marginLeft: '-60px', marginTop: '-60px', borderRadius: '50%',
      background: 'radial-gradient(circle, rgba(255,255,255,.55), rgba(255,255,255,.18) 60%, rgba(255,255,255,0) 72%)' });
    L.css(ring, { width: '120px', height: '120px', marginLeft: '-60px', marginTop: '-60px', borderRadius: '50%',
      border: '5px solid rgba(255,255,255,.85)' });
    return (lb, at, x, y) => {
      L.css(wrap, { left: x + 'px', top: y + 'px' });
      const k = lb - at;
      const on = k > -0.3 && k < 0.7;
      L.show(wrap, on);
      if (!on) return;
      const inP = L.seg(k, -0.3, 0);
      const outP = L.seg(k, 0.25, 0.6);
      L.tf(disc, { s: L.lerp(1.5, k < 0 ? 1 : L.lerp(0.82, 1, L.seg(k, 0, 0.2)), L.outCubic(inP)), o: inP * (1 - outP) });
      const r = L.seg(k, 0, 0.55);
      L.tf(ring, { s: 0.6 + 2.6 * L.outCubic(r), o: k < 0 ? 0 : (1 - r) * 0.9 });
    };
  };

  /** Rounded leaf tile with a Material icon. */
  SH.tile = (parent, iconName, color, size = 200, bgA = 0.18) => {
    const t = L.div('tile abs', parent, L.icon(iconName, Math.round(size * 0.52), color));
    L.css(t, {
      width: size + 'px', height: size + 'px', marginLeft: -size / 2 + 'px', marginTop: -size / 2 + 'px',
      borderRadius: `${size * 0.32}px ${size * 0.11}px ${size * 0.32}px ${size * 0.11}px`,
      background: `linear-gradient(140deg, ${hexA(color, bgA + 0.1)}, ${hexA(color, bgA * 0.35)})`,
      boxShadow: `0 0 0 2px ${hexA(color, 0.35)} inset, 0 20px 60px rgba(0,0,0,.45), 0 0 50px ${hexA(color, 0.18)}`,
    });
    return t;
  };

  function hexA(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }
  SH.hexA = hexA;

  // The eight trigger types, in the editor's dropdown order (TriggerType.entries)
  SH.TRIGGERS = [
    { id: 'SCHEDULED', label: 'Scheduled', icon: 'schedule', color: '#00BCD4' },
    { id: 'MANUAL', label: 'Manual', icon: 'touch_app', color: '#00E676' },
    { id: 'INCOMING', label: 'Auto-reply', icon: 'sms', color: '#B388FF' },
    { id: 'LOCATION', label: 'Location', icon: 'location_on', color: '#FF9800' },
    { id: 'CHARGING', label: 'On charging', icon: 'bolt', color: '#FFB300' },
    { id: 'BLUETOOTH', label: 'On Bluetooth', icon: 'bluetooth', color: '#448AFF' },
    { id: 'WIFI', label: 'On Wi-Fi', icon: 'wifi', color: '#42D1CA' },
    { id: 'MISSED_CALL', label: 'Missed call', icon: 'phone_missed', color: '#FF4081' },
  ];

  /** Lower-third chip naming the trigger (TriggerType label as in the editor) + counter. */
  SH.trigChip = (root) => {
    const c = L.div('tchip', root);
    const ic = L.div('', c);
    const lab = L.el('span', '', c);
    const num = L.el('span', 'n', c);
    let last = -1;
    return (lb, idx) => {
      if (idx !== last) {
        const t = SH.TRIGGERS[idx];
        ic.innerHTML = L.icon(t.icon, 44, t.color);
        lab.textContent = t.label;
        num.textContent = String(SH.TRIG_ORDER.indexOf(t.id) + 1).padStart(2, '0') + '/08';
        c.style.borderColor = SH.hexA(t.color, 0.55);
        last = idx;
      }
      const k = L.seg(lb, 0.1, 0.4);
      c.style.transform = `translateX(${SH.land ? 0 : '-50%'}) translateY(${(40 * (1 - L.outBack(k, 2))).toFixed(1)}px)`;
      c.style.opacity = k.toFixed(3);
    };
  };
  // Order the film shows them in
  SH.TRIG_ORDER = ['SCHEDULED', 'MANUAL', 'INCOMING', 'LOCATION', 'MISSED_CALL', 'CHARGING', 'BLUETOOTH', 'WIFI'];

  // ── Transitions on a shot root (p: 0..1)
  SH.whipOut = (root, p, dir = -1, key = 'w') => {
    const q = L.inCubic(p);
    root.style.transform = `translateX(${dir * K.whip * q}px) skewX(${-dir * 8 * q}deg)`;
    root.style.filter = FX.dirBlur(key + 'o', 70 * Math.sin(Math.min(1, p * 1.2) * Math.PI * 0.5), 0) || 'none';
  };
  SH.whipIn = (root, p, dir = -1, key = 'w') => {
    const q = 1 - L.outCubic(p);
    root.style.transform = `translateX(${-dir * K.whip * q}px) skewX(${dir * 8 * q}deg)`;
    root.style.filter = FX.dirBlur(key + 'i', 70 * q, 0) || 'none';
  };
  SH.pushOut = (root, p, dir = -1, key = 'p') => {
    const q = L.inCubic(p);
    root.style.transform = `translateY(${dir * K.push * q}px)`;
    root.style.filter = FX.dirBlur(key + 'o', 0, 60 * q) || 'none';
  };
  SH.pushIn = (root, p, dir = -1, key = 'p') => {
    const q = 1 - L.outCubic(p);
    root.style.transform = `translateY(${-dir * K.push * q}px)`;
    root.style.filter = FX.dirBlur(key + 'i', 0, 60 * q) || 'none';
  };
  SH.zoomThroughOut = (root, p, ox = SH.CX, oy = SH.CY) => {
    const q = L.inExpo(p);
    root.style.transformOrigin = `${ox}px ${oy}px`;
    root.style.transform = `scale(${1 + 5 * q})`;
    root.style.opacity = (1 - L.seg(p, 0.55, 1)).toFixed(3);
    root.style.filter = q > 0.02 ? `blur(${(18 * q).toFixed(1)}px)` : 'none';
  };
  SH.zoomThroughIn = (root, p) => {
    const q = 1 - L.outExpo(p);
    root.style.transformOrigin = `${SH.CX}px ${SH.CY}px`;
    root.style.transform = `scale(${1 - 0.6 * q})`;
    root.style.opacity = L.seg(p, 0, 0.35).toFixed(3);
    root.style.filter = q > 0.02 ? `blur(${(16 * q).toFixed(1)}px)` : 'none';
  };
  /** Horizontal strips sliding in from alternating sides, staggered top to bottom. */
  SH.sliceIn = (root, p, n = 8) => {
    const props = ['maskImage', 'maskSize', 'maskPosition', 'maskRepeat', 'webkitMaskImage', 'webkitMaskSize',
      'webkitMaskPosition', 'webkitMaskRepeat'];
    if (p >= 1) {
      props.forEach((k) => (root.style[k] = ''));
      return;
    }
    // One solid mask layer per strip (layers add up), so the strips need no polygon gymnastics.
    const h = SH.H / n;
    const img = [], size = [], pos = [];
    for (let i = 0; i < n; i++) {
      const lp = L.outCubic(L.seg(p, i * 0.05, 0.6 + i * 0.05));
      const w = Math.max(0, SH.W * lp);
      img.push('linear-gradient(#000,#000)');
      size.push(`${w.toFixed(1)}px ${Math.ceil(h) + 1}px`);
      pos.push(`${i % 2 === 0 ? 0 : (SH.W - w).toFixed(1)}px ${Math.floor(i * h)}px`);
    }
    root.style.maskImage = root.style.webkitMaskImage = img.join(',');
    root.style.maskSize = root.style.webkitMaskSize = size.join(',');
    root.style.maskPosition = root.style.webkitMaskPosition = pos.join(',');
    root.style.maskRepeat = root.style.webkitMaskRepeat = 'no-repeat';
  };
  SH.irisIn = (root, p, x = SH.CX, y = SH.CY) => {
    if (p >= 1) { root.style.clipPath = 'none'; return; }
    root.style.clipPath = `circle(${(2300 * L.inOutCubic(p)).toFixed(1)}px at ${x}px ${y}px)`;
  };
  SH.reset = (root) => {
    root.style.transform = 'none';
    root.style.filter = 'none';
    root.style.opacity = 1;
    root.style.clipPath = 'none';
    SH.sliceIn(root, 1);
  };

  window.SH = SH;
})();
