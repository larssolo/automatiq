/* Stage, shot scheduler and global effects. Exposes:
 *   window.renderFrame(t)  — draw the film at time t (seconds); deterministic
 *   window.sceneReady      — Promise resolved once fonts and images are decoded */
(function () {
  const TL = window.TIMELINE;
  const { W, H } = window.STAGE;
  const backdrop = document.getElementById('backdrop');
  const world = document.getElementById('world');
  const flash = document.getElementById('flash');
  const grain = document.getElementById('grain');
  const glitchLayer = document.getElementById('glitch');
  const svgNS = 'http://www.w3.org/2000/svg';
  const filterDefs = document.getElementById('fxdefs');

  // ── Grain: a seeded noise tile, shifted every frame
  (function makeGrain() {
    const c = document.createElement('canvas');
    c.width = c.height = 256;
    const g = c.getContext('2d');
    const img = g.createImageData(256, 256);
    const rnd = L.rng(1234);
    for (let i = 0; i < img.data.length; i += 4) {
      const v = Math.floor(rnd() * 255);
      img.data[i] = img.data[i + 1] = img.data[i + 2] = v;
      img.data[i + 3] = 255;
    }
    g.putImageData(img, 0, 0);
    grain.style.backgroundImage = `url(${c.toDataURL()})`;
  })();

  // ── Backdrop aurora (StaticBackground.kt AuroraOverlay, sped up for the film)
  const glows = [
    ['#00E676', 0.16, 1500],
    ['#42D1CA', 0.13, 1600],
    ['#FFB300', 0.07, 900],
  ].map(([color, a, size]) => {
    const g = L.div('glow', backdrop);
    L.css(g, {
      width: size + 'px', height: size + 'px', marginLeft: -size / 2 + 'px', marginTop: -size / 2 + 'px',
      background: `radial-gradient(circle, ${hexA(color, a)} 0%, ${hexA(color, 0)} 70%)`,
    });
    return g;
  });
  function hexA(hex, a) {
    const n = parseInt(hex.slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
  }

  // ── Directional blur filters, reused by key
  const blurFilters = {};
  function dirBlur(key, sx, sy) {
    let f = blurFilters[key];
    if (!f) {
      const filter = document.createElementNS(svgNS, 'filter');
      filter.setAttribute('id', 'mb_' + key);
      filter.setAttribute('x', '-20%');
      filter.setAttribute('y', '-20%');
      filter.setAttribute('width', '140%');
      filter.setAttribute('height', '140%');
      const gb = document.createElementNS(svgNS, 'feGaussianBlur');
      filter.appendChild(gb);
      filterDefs.appendChild(filter);
      f = blurFilters[key] = gb;
    }
    f.setAttribute('stdDeviation', `${Math.max(0, sx).toFixed(1)} ${Math.max(0, sy).toFixed(1)}`);
    return sx > 0.3 || sy > 0.3 ? `url(#mb_${key})` : '';
  }
  const rgbR = document.getElementById('rgbR');
  const rgbB = document.getElementById('rgbB');

  // ── Effects accumulated by shots during a frame, applied at the end
  const FX = {
    reset() {
      this.flash = 0;
      this.shake = 0;
      this.zoom = 1;
      this.rgb = 0;
      this.glitch = 0;
      this.glitchSeed = 1;
      this.worldFilter = '';
      this.dark = 0;
      this.pulse = true;
    },
    dirBlur,
  };
  window.FX = FX;

  // Kick/snare times (beats) derived from the shared groove, used for the camera pulse.
  const kicks = [];
  TL.SECTIONS.forEach((s) => {
    if (!['dropA', 'dropB', 'outro'].includes(s.id)) return;
    for (let b = s.from; b < s.to; b += 4) {
      const bar = Math.floor(b / 4);
      const pat = TL.GROOVE.kick[bar % 2];
      pat.forEach((step) => kicks.push(b + step / 4));
    }
  });
  function kickEnv(bt) {
    let e = 0;
    for (const k of kicks) {
      if (k > bt) break;
      e = Math.max(e, Math.exp(-(bt - k) * 9));
    }
    return e;
  }
  kicks.sort((a, b) => a - b);
  const inGap = (bt) => TL.GAPS.some(([a, b]) => bt >= a && bt < b);

  // ── Shots
  const shots = TL.SHOTS.map(([id, a, b]) => {
    let def = window.SHOTS[id];
    if (!def) {
      // placeholder while a shot is being written: shows its id so previews still render
      console.warn('placeholder shot', id);
      def = {
        build: (root) => L.css(L.div('center mono-l', root, id), { transform: 'translate(-50%,-50%)' }),
        render: () => {},
      };
    }
    const root = L.div('shot', world);
    root.dataset.id = id;
    const ctx = { len: b - a, root };
    def.build(root, ctx);
    root.style.display = 'none';
    return { id, a, b, def, root, ctx };
  });

  function applyGlitch(bt) {
    glitchLayer.innerHTML = '';
    if (FX.glitch <= 0.01) return;
    const rnd = L.rng(FX.glitchSeed * 7919 + Math.floor(bt * 24));
    const n = 5 + Math.floor(FX.glitch * 9);
    for (let i = 0; i < n; i++) {
      const y0 = rnd() * H;
      const h = 14 + rnd() * 160 * FX.glitch;
      const clone = world.cloneNode(true);
      clone.removeAttribute('id');
      clone.style.clipPath = `inset(${y0.toFixed(0)}px 0 ${Math.max(0, H - y0 - h).toFixed(0)}px 0)`;
      clone.style.transform = `${world.style.transform} translateX(${((rnd() - 0.5) * 220 * FX.glitch).toFixed(1)}px)`;
      const tint = rnd();
      clone.style.filter =
        tint < 0.33 ? 'hue-rotate(110deg) saturate(2.4)' : tint < 0.6 ? 'invert(1) hue-rotate(180deg)' : 'saturate(3) brightness(1.4)';
      glitchLayer.appendChild(clone);
    }
  }

  window.renderFrame = function (t) {
    const bt = t / TL.BEAT;
    FX.reset();
    // backdrop aurora drift
    const d = bt * 0.05;
    L.tf(glows[0], { x: W * (0.15 + 0.22 * Math.sin(d * 1.3)), y: H * (0.12 + 0.12 * Math.cos(d)) });
    L.tf(glows[1], { x: W * (0.88 - 0.2 * Math.sin(d * 0.9)), y: H * (0.82 - 0.1 * Math.cos(d * 1.2)) });
    L.tf(glows[2], { x: W * (0.12 + 0.1 * Math.cos(d * 1.1)), y: H * (0.66 + 0.08 * Math.sin(d)) });

    for (const s of shots) {
      const pre = s.def.pre || 0;
      const post = s.def.post || 0;
      const vis = bt >= s.a - pre && bt < s.b + post;
      L.show(s.root, vis);
      if (vis) s.def.render(bt - s.a, Object.assign(s.ctx, { bt }));
    }

    // camera: kick pulse + shake + zoom
    const pulse = FX.pulse && !inGap(bt) ? kickEnv(bt) * 0.014 : 0;
    const sh = FX.shake;
    const sx = sh ? L.noise(bt * 31.7) * 26 * sh : 0;
    const sy = sh ? L.noise(bt * 27.3 + 9) * 26 * sh : 0;
    const sr = sh ? L.noise(bt * 19.1 + 4) * 1.2 * sh : 0;
    L.tf(world, { x: sx, y: sy, r: sr, s: FX.zoom * (1 + pulse) });

    let wf = FX.worldFilter;
    if (FX.rgb > 0.05) {
      rgbR.setAttribute('dx', (FX.rgb * 14).toFixed(1));
      rgbB.setAttribute('dx', (-FX.rgb * 14).toFixed(1));
      wf += ' url(#rgbsplit)';
    }
    if (FX.dark > 0) wf += ` brightness(${(1 - FX.dark).toFixed(3)})`;
    world.style.filter = wf.trim() || 'none';

    flash.style.opacity = Math.min(1, FX.flash).toFixed(3);
    const gi = Math.floor(t * 30); // grain refreshes at 30 Hz: keeps the texture alive at half the bitrate cost
    grain.style.transform = `translate(${Math.floor(L.hash(gi) * 64) - 32}px,${Math.floor(L.hash(gi + 0.5) * 64) - 32}px)`;
    applyGlitch(bt);
  };

  window.sceneReady = (async () => {
    await document.fonts.ready;
    await Promise.all(
      ['900 100px "DM Sans"', '500 40px "JetBrains Mono"', '700 40px "JetBrains Mono"'].map((f) => document.fonts.load(f))
    );
    const imgs = [...document.images];
    await Promise.all(imgs.map((i) => (i.decode ? i.decode().catch(() => {}) : null)));
    // bg_static.webp is a CSS background: force-load it once
    await new Promise((res) => {
      const im = new Image();
      im.onload = im.onerror = res;
      im.src = '../../app/src/main/res/drawable-nodpi/bg_static.webp';
    });
    SH.relayout(); // caption geometry depends on the real font metrics, so it runs after fonts load
    window.renderFrame(0);
    return true;
  })();
})();
