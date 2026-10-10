/* Act 1 — hook, logo drop, the app, the macro editor (beats 0-24). */
(function () {
  const { div, seg, lerp, tf } = L;
  const S = window.SHOTS;
  const LAND = SH.land;

  // ───────────────────────────── hookSame (0-4, lingers until the implosion) ──
  const HOOK_TIMES = [-0.1, 1, 2, 2.5, 3, 3.25, 3.5, 3.75];
  const HOOK_STAMPS = ['man 16:30', 'tir 16:31', 'ons 16:29', 'tor 16:30', 'fre 16:32', 'man 16:30', 'tir 16:31', 'ons 16:30'];
  // Landscape: the bubble pile climbs the right-hand column; DOT is where it collapses (the middle of that column).
  const HOOK_PLACE = SH.place(990, 1628, 0.78, [1830, 960]);
  const DOT = LAND ? { x: 490, y: 1090 } : { x: 540, y: 1180 };
  S.hookSame = {
    post: 3.2,
    build(root, ctx) {
      ctx.cam = div('layer', SH.vis(root, HOOK_PLACE));
      ctx.stack = div('layer', ctx.cam);
      // fade the pile out as it climbs under the caption
      ctx.stack.style.maskImage = ctx.stack.style.webkitMaskImage =
        'linear-gradient(to bottom, transparent 560px, #000 900px)';
      ctx.items = HOOK_TIMES.map((at, i) => {
        const w = div('abs', ctx.stack);
        L.css(w, { right: '90px', top: '0px', width: '620px', textAlign: 'right', transformOrigin: '100% 100%' });
        const b = UI.bubble(w, 'På vej hjem nu!', 'out', 'big');
        L.css(b, { width: '560px', textAlign: 'left' });
        div('stamp', w, HOOK_STAMPS[i] + '  ✓✓');
        return { w, at };
      });
      ctx.caps = SH.caps(root, [
        { at: -0.12, out: 1, cls: 'h-xl', words: [['Samme SMS.', 0]] },
        { at: 1, out: 2, cls: 'h-xl', words: [['Samme tid.', 0]] },
        { at: 2, out: 4, cls: 'h-xl', exitDur: 0.2, words: [['Hver.', 0, null, true], ['Eneste.', 0.5, null, true], ['Dag.', 1, 'green']] },
      ], { top: 210 });
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      const t = Math.min(lb, 3.999); // the pile freezes when the glitch hits
      const H = 204;
      const baseY = 1600;
      const n = ctx.items.length;
      // camera creeps in as the repetition accelerates
      tf(ctx.cam, { s: 1 + 0.05 * L.inQuad(seg(t, 0, 4)), r: -0.6 * seg(t, 0, 4) });
      ctx.items.forEach((it, i) => {
        let lift = 0;
        for (let k = i + 1; k < n; k++) lift += L.outBack(seg(t, ctx.items[k].at, ctx.items[k].at + 0.22), 1.4);
        const k0 = t - it.at;
        if (k0 < 0) { it.w.style.opacity = 0; return; }
        const pop = L.outBack(seg(k0, 0, 0.25), 2.2);
        let x = 0, y = baseY - 150 - H * lift, s = lerp(0.4, 1, pop), o = seg(k0, 0, 0.06), r = 0, blur = 0;
        // implosion into the pulse dot (beats 6-7)
        const q = L.inCubic(seg(lb, 5.95 + (n - 1 - i) * 0.05, 6.75 + (n - 1 - i) * 0.05));
        if (q > 0) {
          const cx = 1080 - 90 - 280, cy = y + 80; // item centre
          x = (DOT.x - cx) * q;
          y += (DOT.y - cy) * q;
          s *= 1 - 0.95 * q;
          r = (i % 2 ? 25 : -25) * q;
          blur = 10 * q;
          o *= 1 - seg(q, 0.85, 1);
        }
        tf(it.w, { x, y, s, r, o, blur });
      });
      // freeze-frame colour drain after the glitch
      const g = seg(lb, 4, 4.3);
      ctx.stack.style.filter = g > 0 ? `grayscale(${(0.85 * g).toFixed(2)}) brightness(${(1 - 0.35 * g).toFixed(2)})` : 'none';
      ctx.caps(lb);
    },
  };

  // ───────────────────────────── hookWhatIf (4-8) ──
  S.hookWhatIf = {
    build(root, ctx) {
      ctx.caps = SH.caps(root, [
        { at: 0, out: 3, cls: 'h-l', words: [['Hvad ', 0], ['hvis ', 0.5], ['den', 1, null, true], ['sendte', 1.5, null, true], ['sig selv?', 2, 'green']] },
      ], { top: 230 });
      // gravity well: rings collapsing into the dot
      const v = SH.vis(root, HOOK_PLACE);
      ctx.rings = [0, 1, 2, 3].map(() => {
        const r = div('abs', v);
        L.css(r, { left: DOT.x - 400 + 'px', top: DOT.y - 400 + 'px', width: '800px', height: '800px', borderRadius: '50%',
          border: '3px solid rgba(0,230,118,.55)' });
        return r;
      });
      ctx.dot = div('abs', v);
      L.css(ctx.dot, { left: DOT.x - 40 + 'px', top: DOT.y - 40 + 'px', width: '80px', height: '80px', borderRadius: '50%',
        background: 'radial-gradient(circle, #b9ffd9 0%, #00e676 45%, #00e676 60%, rgba(0,230,118,0) 72%)' });
      ctx.halo = div('abs', v);
      L.css(ctx.halo, { left: DOT.x - 300 + 'px', top: DOT.y - 300 + 'px', width: '600px', height: '600px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(0,230,118,.45) 0%, rgba(0,230,118,.12) 35%, rgba(0,230,118,0) 70%)' });
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      // glitch freeze on the downbeat
      const gk = seg(lb, 0, 0.5);
      if (lb < 0.5) {
        FX.glitch = Math.max(FX.glitch, 1 - gk);
        FX.glitchSeed = 3;
        FX.rgb = Math.max(FX.rgb, 1 - gk);
        FX.shake = Math.max(FX.shake, 0.6 * (1 - gk));
      }
      ctx.caps(lb);
      // rings fall inward during the suck (beats 6-7.5)
      ctx.rings.forEach((r, i) => {
        const ph = (lb - 1.8 - i * 0.25) / 0.9;
        const on = lb > 1.8 && lb < 3.6 && ph > 0;
        L.show(r, on);
        if (on) {
          const f = ph % 1;
          tf(r, { s: lerp(1.6, 0.05, L.inCubic(f)), o: Math.sin(f * Math.PI) * 0.8 });
        }
      });
      // the pulse dot: appears, heart-beats faster, inhales before the drop
      const appear = L.outBack(seg(lb, 2.0, 2.8), 2);
      const beat = [2.5, 3, 3.25].reduce((a, b) => a + 0.45 * L.decay(lb, b, 10), 0);
      const inhale = L.inCubic(seg(lb, 3.5, 4));
      const s = Math.max(0, appear * (1 + beat) * (1 - 0.75 * inhale));
      tf(ctx.dot, { s, o: seg(lb, 2, 2.15) });
      tf(ctx.halo, { s: s * (1.0 + 0.6 * inhale), o: seg(lb, 2, 2.3) * (0.6 + 0.4 * inhale + beat) });
      FX.dark = Math.max(FX.dark, 0.25 * seg(lb, 2.5, 3.5));
    },
  };

  // ───────────────────────────── logo (8-12) ──
  const ROBOT = LAND ? { x: 600, y: 540, size: 500 } : { x: 540, y: 760, size: 400 };
  const PUPIL = { x: ROBOT.x - ROBOT.size / 2 + 0.399 * ROBOT.size, y: ROBOT.y - ROBOT.size / 2 + 0.342 * ROBOT.size };
  S.logo = {
    build(root, ctx) {
      const cam = (ctx.cam = div('layer', root));
      ctx.glow = div('abs', cam);
      L.css(ctx.glow, { left: ROBOT.x - 520 + 'px', top: ROBOT.y - 520 + 'px', width: '1040px', height: '1040px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(66,209,202,.42) 0%, rgba(66,209,202,.10) 40%, rgba(66,209,202,0) 70%)' });
      ctx.rings = ['#00E676', '#42D1CA'].map((c) => {
        const r = div('abs', cam);
        L.css(r, { left: ROBOT.x - 120 + 'px', top: ROBOT.y - 120 + 'px', width: '240px', height: '240px', borderRadius: '50%',
          border: `10px solid ${c}`, boxShadow: `0 0 40px ${c}` });
        return r;
      });
      const rnd = L.rng(77);
      ctx.parts = Array.from({ length: 42 }, () => {
        const p = div('abs', cam);
        const sz = 6 + rnd() * 16;
        const col = ['#00E676', '#42D1CA', '#FFB300', '#ffffff', '#8a7dff'][Math.floor(rnd() * 5)];
        L.css(p, { left: ROBOT.x + 'px', top: ROBOT.y + 'px', width: sz + 'px', height: sz + 'px', marginLeft: -sz / 2 + 'px',
          marginTop: -sz / 2 + 'px', borderRadius: rnd() < 0.5 ? '50%' : '3px', background: col, boxShadow: `0 0 12px ${col}` });
        const a = rnd() * Math.PI * 2;
        const v = 500 + rnd() * 900;
        return { p, vx: Math.cos(a) * v, vy: Math.sin(a) * v, spin: (rnd() - 0.5) * 720 };
      });
      ctx.robot = UI.robot(cam, ROBOT.size);
      L.css(ctx.robot, { left: ROBOT.x - ROBOT.size / 2 + 'px', top: ROBOT.y - ROBOT.size / 2 + 'px',
        boxShadow: '0 30px 90px rgba(0,0,0,.55), 0 0 0 8px rgba(66,209,202,.18)' });
      // wordmark: pulse dot + mono letters
      const wm = (ctx.wm = div('abs', cam));
      L.css(wm, LAND
        ? { left: '930px', right: '40px', top: '350px', display: 'flex', justifyContent: 'flex-start', alignItems: 'center' }
        : { left: 0, right: 0, top: '1040px', display: 'flex', justifyContent: 'center', alignItems: 'center' });
      ctx.wdot = div('', wm);
      L.css(ctx.wdot, { width: '34px', height: '34px', borderRadius: '50%', background: '#00E676', marginRight: '30px',
        boxShadow: '0 0 30px rgba(0,230,118,.9)' });
      ctx.letters = [...'automatiq'].map((ch) => {
        const s = L.el('span', 'kw', wm, ch);
        L.css(s, { font: `500 ${LAND ? 124 : 120}px/1 var(--mono)`, letterSpacing: '-0.02em' });
        return s;
      });
      ctx.tag = L.kinetic(cam, 'h-l shadow-txt', [{ text: 'SMS på ', at: 1 }, { text: 'autopilot.', at: 1.5, cls: 'grad' }]);
      ctx.tag.root.style.top = LAND ? '520px' : '1220px';
      ctx.sub = L.kinetic(cam, 'mono-s dim', [{ text: 'android  ·  kører lokalt  ·  ai-klar', at: 2.1 }], { style: 'rise' });
      ctx.sub.root.style.top = LAND ? '690px' : '1400px';
      if (LAND) [ctx.tag.root, ctx.sub.root].forEach((e) => L.css(e, { left: '930px', right: '40px', textAlign: 'left' }));
      ctx.purple = div('layer', root);
      ctx.purple.style.background = '#9586EA';
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      // impact
      FX.flash = Math.max(FX.flash, 0.95 * L.decay(lb, 0, 9));
      FX.shake = Math.max(FX.shake, 1.3 * L.decay(lb, 0, 4.5));
      FX.rgb = Math.max(FX.rgb, 0.8 * L.decay(lb, 0, 7));
      ctx.rings.forEach((r, i) => {
        const p = seg(lb, i * 0.08, 1.3 + i * 0.1);
        tf(r, { s: 0.2 + 9 * L.outExpo(p), o: (1 - p) * (lb >= i * 0.08 ? 1 : 0) });
        r.style.borderWidth = lerp(14, 2, p).toFixed(1) + 'px';
      });
      ctx.parts.forEach((pt) => {
        const k = Math.max(0, lb);
        const d = (1 - Math.exp(-3.2 * k)) / 3.2;
        tf(pt.p, { x: pt.vx * d, y: pt.vy * d + 120 * k * k, r: pt.spin * k, o: 1 - seg(lb, 0.4, 1.6) });
      });
      const sp = L.spring(lb, 1.5, 5.2);
      tf(ctx.robot, { s: Math.max(0, sp), r: lerp(-18, 0, Math.min(1, sp)) });
      const kickGlow = 0.85 + 0.15 * Math.cos(lb * Math.PI);
      tf(ctx.glow, { s: Math.max(0, sp) * kickGlow, o: seg(lb, 0, 0.3) });
      // wordmark letters cascade on 32nds
      const dk = lb - 0.25;
      tf(ctx.wdot, { s: L.outBack(seg(dk, 0, 0.2), 3), o: seg(dk, 0, 0.05) });
      ctx.letters.forEach((s, i) => {
        const k = dk - 0.075 * (i + 1);
        tf(s, { y: lerp(40, 0, L.outBack(seg(k, 0, 0.18), 2.5)), o: seg(k, 0, 0.04) });
      });
      // pupil zoom-through (beats 11-12)
      const z = seg(lb, 3, 4);
      const zoom = 1 + 30 * L.inExpo(z);
      ctx.cam.style.transformOrigin = `${PUPIL.x}px ${PUPIL.y}px`;
      ctx.cam.style.transform = zoom > 1.001 ? `scale(${zoom.toFixed(4)})` : 'none';
      const fadeOthers = 1 - seg(z, 0, 0.35);
      ctx.tag.render(lb);
      ctx.sub.render(lb);
      [ctx.wm, ctx.tag.root, ctx.sub.root, ctx.glow].forEach((e) => (e.style.opacity = (fadeOthers * (e === ctx.glow ? seg(lb, 0, 0.3) : 1)).toFixed(3)));
      ctx.purple.style.opacity = seg(z, 0.72, 0.96).toFixed(3);
      if (z > 0) FX.pulse = false;
    },
  };

  // 15 s cut: the whole logo sequence in half the time (impact → wordmark → tagline → pupil zoom). It starts
  // 0.2 beats into the impact, so the very first frame (the cover frame on most platforms) already shows the
  // robot instead of a white flash; the impact sound still lands on frame 0.
  S.logoShort = Object.assign({}, S.logo, { render(lb, ctx) { S.logo.render(lb * 2 + 0.2, ctx); } });

  // ───────────────────────────── appList (12-16) ──
  const PHONE = { dp: 2.15, left: (1080 - 372 * 2.15) / 2, top: 420 };
  const LIST_PLACE = SH.place(540, 1271, 0.58); // the whole phone fits the right column
  const scr = (xdp, ydp) => ({ x: PHONE.left + 6 * PHONE.dp + xdp * PHONE.dp, y: PHONE.top + 6 * PHONE.dp + ydp * PHONE.dp });
  const LIST_MACROS = [
    { name: 'Morgen-check-in', summary: '07:00 · Every 2 weeks · Mon · Wed', accent: '#00BCD4', enabled: true },
    { name: 'På vej hjem', summary: 'On departure · 150 m', accent: '#FF9800', enabled: true },
    { name: 'Ringer tilbage', summary: 'Missed call', accent: '#E91E63', enabled: true },
    { name: 'Ferie-autosvar', summary: 'Auto-reply', accent: '#9C27B0', enabled: false, ai: true },
  ];
  S.appList = {
    build(root, ctx) {
      const cam = (ctx.cam = div('layer', SH.vis(root, LIST_PLACE)));
      ctx.phoneWrap = div('layer', cam);
      const ph = UI.phone(ctx.phoneWrap, PHONE.dp);
      L.css(ph.el, { left: PHONE.left + 'px', top: PHONE.top + 'px' });
      ctx.ph = ph;
      ctx.ui = UI.listScreen(ph.screen, LIST_MACROS, { time: '16:29' });
      ctx.touch = SH.touch(cam);
      ctx.blob = div('abs', cam);
      const f = 56 * PHONE.dp;
      const c = scr(180, 656);
      L.css(ctx.blob, { left: c.x - f / 2 + 'px', top: c.y - f / 2 + 'px', width: f + 'px', height: f + 'px',
        borderRadius: '46% 25% 46% 25%', background: '#00E676' });
      ctx.blobC = c;
      ctx.caps = SH.caps(root, [
        { at: 0.5, out: 2, cls: 'h-l', words: [['Byg en ', 0], ['makro.', 0.25, 'green']] },
        { at: 2, out: 3.5, cls: 'h-l', words: [['Den klarer ', 0], ['resten.', 0.25, 'green']] },
      ], { top: 150 });
      ctx.purple = div('layer', root);
      ctx.purple.style.background = '#9586EA';
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      const p = L.spring(lb, 1.05, 4.4);
      const s = lerp(2.4, 1, p);
      ctx.phoneWrap.style.transformOrigin = '540px 1120px';
      ctx.phoneWrap.style.transform = `perspective(2400px) rotateX(${lerp(28, 0, p).toFixed(2)}deg) rotate(${lerp(-9, 0, p).toFixed(2)}deg) scale(${s.toFixed(4)})`;
      ctx.purple.style.opacity = (1 - seg(lb, 0, 0.38)).toFixed(3);
      // tilt down to the FAB before it gets tapped
      ctx.cam.style.transform = LAND ? 'none' : `translateY(${(-300 * L.inOutCubic(seg(lb, 2.7, 3.0))).toFixed(1)}px)`;
      const breath = 0.5 + 0.5 * Math.cos(Math.PI * lb);
      ctx.ui.dot.style.opacity = (0.35 + 0.65 * breath).toFixed(3);
      ctx.ui.cards.forEach((c, i) => {
        const k = lb - (0.5 + 0.5 * i);
        const q = L.outBack(seg(k, 0, 0.35), 1.6);
        tf(c.wrap, { x: lerp(260, 0, q), o: seg(k, 0, 0.1), r: lerp(4, 0, q) });
        const alive = i < 3 || lb >= 2.5;
        c.vein(alive, breath);
      });
      // the fourth macro gets switched on
      const sw = L.spring(lb - 2.5, 2.2, 7);
      ctx.ui.cards[3].sw.set(lb < 2.5 ? 0 : sw);
      ctx.ui.live.textContent = lb < 2.55 ? '3 of 4 live' : '4 of 4 live';
      const swp = scr(8 + 276, 88 + 3 * 84 + 38);
      const fab = ctx.blobC;
      ctx.touch(lb, lb < 2.9 ? 2.5 : 3, lb < 2.9 ? swp.x : fab.x, lb < 2.9 ? swp.y : fab.y);
      // FAB press + blob takeover (beats 15.5-16)
      const press = lb >= 3 ? 1 - 0.12 * Math.sin(Math.PI * seg(lb, 3, 3.3)) : 1;
      ctx.ui.fab.style.transform = `scale(${press.toFixed(3)})`;
      const b = seg(lb, 3.45, 4);
      L.show(ctx.blob, b > 0);
      if (b > 0) tf(ctx.blob, { s: 1 + 42 * L.inExpo(b), r: 40 * b });
      ctx.caps(lb);
    },
  };

  // ───────────────────────────── editor (16-24) ──
  const ED = { dp: 2.65, left: 64, width: 952 };
  const ED_PLACE = SH.place(540, 1080, 0.82); // the focused field stays at the middle of the right column
  const FOCUS = [
    [0, 108], [1.75, 108], [2.1, 330], [3.75, 330], [4.1, 440], [5.35, 440], [5.7, 600], [6.7, 600], [6.95, 238],
  ];
  const MSG = 'Hej {modtager}! Er på vej hjem nu.';
  /**
   * The macro editor. cfg: speed (editor beats per film beat), wipe (colour of the full-screen layer that
   * wipes up at the start) and caps (caption groups, in editor beats).
   */
  const makeEditor = (cfg) => ({
    build(root, ctx) {
      const v = SH.vis(root, ED_PLACE);
      const cam = (ctx.cam = div('layer', v));
      cam.style.maskImage = cam.style.webkitMaskImage = 'linear-gradient(to bottom, transparent 400px, #000 600px)';
      const panel = (ctx.panel = div('abs', cam));
      panel.style.setProperty('--dp', ED.dp + 'px');
      L.css(panel, { left: ED.left + 'px', width: ED.width + 'px', height: 'calc(700 * var(--dp))', top: 0,
        borderRadius: '70px 22px 70px 22px', overflow: 'hidden', boxShadow: '0 40px 120px rgba(0,0,0,.6)' });
      div('app-bg', panel);
      div('app-dim', panel).style.background = 'rgba(8,10,24,.45)';
      const d = (v) => `calc(${v} * var(--dp))`;
      const P = (html, x, y, extra = {}) => {
        const e = div('abs', panel, html);
        L.css(e, Object.assign({ left: d(x), top: d(y) }, extra));
        return e;
      };
      // top app bar
      P('Cancel', 16, 22, { font: `500 ${d(15)} var(--sans)`, color: '#00E676' });
      P('New Macro', 92, 18, { font: `500 ${d(21)} var(--sans)`, color: '#f0f0f0' });
      P('Save', 312, 22, { font: `600 ${d(15)} var(--sans)`, color: '#00E676' });
      // name
      ctx.name = P('<span class="lbl">Name</span><span class="val"></span><span class="caret"></span>', 16, 80,
        { width: d(328) });
      ctx.name.className = 'abs field focus';
      ctx.nameVal = ctx.name.querySelector('.val');
      ctx.nameCaret = ctx.name.querySelector('.caret');
      // widget colour row
      P('Widget color', 16, 152, { font: `500 ${d(14)} var(--sans)`, color: '#f0f0f0' });
      const cols = ['#F44336', '#E91E63', '#9C27B0', '#3F51B5', '#00BCD4', '#4CAF50', '#FF9800', '#009688'];
      cols.forEach((c, i) => {
        const dot = P(i === 6 ? L.icon('check', d(16), '#fff') : '', 16 + i * 40, 176, {
          width: d(30), height: d(30), borderRadius: '50%', background: c, display: 'flex', alignItems: 'center',
          justifyContent: 'center', boxShadow: i === 6 ? `0 0 0 ${d(2)} #fff` : 'none' });
        return dot;
      });
      // trigger dropdown
      ctx.trig = P(`<span class="lbl">Trigger</span>${L.icon('location_on', d(20), '#FF9800')}<span style="margin-left:${d(10)}">Location</span>
        <span style="margin-left:auto">${L.icon('arrow_drop_down', d(26), '#ccc')}</span>`, 16, 222, { width: d(328), display: 'flex', alignItems: 'center' });
      ctx.trig.className = 'abs field';
      // recipients
      P('Recipients', 16, 296, { font: `500 ${d(14)} var(--sans)`, color: '#f0f0f0' });
      ctx.chips = [['S', 'Sofie', '#FF4081'], ['M', 'Mor', '#00BCD4']].map(([ini, name, col], i) => {
        const c = P(`<span style="width:${d(26)};height:${d(26)};border-radius:50%;background:${col};display:flex;align-items:center;justify-content:center;font:700 ${d(13)} var(--sans);color:#000">${ini}</span>
          <span style="margin:0 ${d(8)}">${name}</span>${L.icon('close', d(16), '#bbb')}`, 16 + i * 128, 322, {
          display: 'flex', alignItems: 'center', padding: `${d(5)} ${d(10)} ${d(5)} ${d(5)}`, borderRadius: d(20),
          background: 'rgba(255,255,255,.10)', border: `${d(1)} solid rgba(255,255,255,.22)`, font: `500 ${d(15)} var(--sans)`,
          color: '#f0f0f0', transformOrigin: '20% 50%' });
        return c;
      });
      ctx.addRec = P(`${L.icon('add', d(18), '#00E676')}<span style="margin-left:${d(6)}">Add recipient</span>`, 16, 366,
        { display: 'flex', alignItems: 'center', font: `500 ${d(14)} var(--sans)`, color: '#00E676' });
      // message
      ctx.msg = P('<span class="lbl">Message</span><span class="val"></span><span class="caret"></span>', 16, 410,
        { width: d(328), minHeight: d(88), lineHeight: 1.35 });
      ctx.msg.className = 'abs field';
      ctx.msgVal = ctx.msg.querySelector('.val');
      ctx.msgCaret = ctx.msg.querySelector('.caret');
      ctx.helper = P('', 20, 504, { font: `400 ${d(11.5)} var(--sans)`, color: '#b5b5b5', width: d(320) });
      // preview
      ctx.preview = P(`<div style="font:500 ${d(11)} var(--mono);color:#9aa;letter-spacing:.04em">Preview (right now)</div>
        <div class="l1" style="margin-top:${d(8)}"></div><div class="l2" style="margin-top:${d(6)}"></div>`, 16, 548, {
        width: d(328), padding: d(14), borderRadius: `${d(14)} ${d(5)} ${d(14)} ${d(5)}`, background: 'rgba(0,0,0,.35)',
        border: `${d(1)} solid rgba(0,230,118,.35)`, font: `400 ${d(15)}/1.35 var(--sans)`, color: '#f0f0f0' });
      ctx.l1 = ctx.preview.querySelector('.l1');
      ctx.l2 = ctx.preview.querySelector('.l2');
      // dropdown menu
      ctx.menu = P('', 16, 282, { width: d(250), padding: `${d(6)} 0`, borderRadius: d(12), background: '#2a2c31',
        boxShadow: `0 ${d(10)} ${d(30)} rgba(0,0,0,.6)`, transformOrigin: '50% 0' });
      ctx.menuItems = SH.TRIGGERS.map((t) => {
        const it = div('', ctx.menu, `${L.icon(t.icon, d(22), t.color)}<span style="margin-left:${d(14)}">${t.label}</span>`);
        L.css(it, { display: 'flex', alignItems: 'center', height: d(46), padding: `0 ${d(16)}`, font: `400 ${d(15.5)} var(--sans)`, color: '#f0f0f0' });
        return it;
      });
      ctx.touch = SH.touch(v);
      ctx.green = div('layer', root);
      ctx.green.style.background = cfg.wipe;
      ctx.caps = SH.caps(root, cfg.caps, { top: 170 });
    },
    render(lbIn, ctx) {
      const lb = lbIn * cfg.speed;
      SH.reset(ctx.root);
      const dp = ED.dp;
      // camera: keep the active field around y≈1080
      let fy = FOCUS[0][1];
      for (let i = 0; i < FOCUS.length - 1; i++) {
        const [a, ya] = FOCUS[i], [b, yb] = FOCUS[i + 1];
        if (lb >= a && lb <= b) fy = lerp(ya, yb, L.inOutCubic(seg(lb, a, b)));
        else if (lb > b) fy = yb;
      }
      const whip = L.bump(lb, 6.7, 6.95);
      const panelTop = 1080 - fy * dp;
      const drift = Math.sin(lb * 0.8) * 2;
      ctx.panel.style.transform = `perspective(2600px) translateY(${panelTop.toFixed(1)}px) rotateX(${(3 + drift * 0.5).toFixed(2)}deg) rotateY(${(-4 + drift).toFixed(2)}deg) scale(${lerp(1.12, 1, L.outCubic(seg(lb, 0, 0.8))).toFixed(4)})`;
      ctx.panel.style.filter = whip > 0.05 ? FX.dirBlur('edw', 0, 50 * whip) : 'none';
      // blob hand-off: green wipes up off the editor
      const gw = L.inOutCubic(seg(lb, 0, 0.45));
      ctx.green.style.transform = `translateY(${(-(SH.H + 80) * gw).toFixed(1)}px)`;
      L.show(ctx.green, gw < 1);
      // name typing
      const NAME = 'På vej hjem';
      ctx.nameVal.textContent = L.typed(NAME, seg(lb, 0.25, 1.25));
      const nameFocus = lb < 1.9;
      ctx.name.classList.toggle('focus', nameFocus);
      ctx.nameCaret.style.opacity = nameFocus && (lb < 1.25 || Math.floor(lb * 2) % 2 === 0) ? 1 : 0;
      // recipient chips
      ctx.chips.forEach((c, i) => {
        const k = lb - (2 + 0.5 * i);
        tf(c, { s: L.outBack(seg(k, 0, 0.3), 2.4), o: seg(k, 0, 0.05) });
      });
      // message typing with live token chips
      const typed = L.typed(MSG, seg(lb, 4.25, 5.25));
      ctx.msgVal.innerHTML = tokenize(typed);
      const msgFocus = lb >= 4 && lb < 5.6;
      ctx.msg.classList.toggle('focus', msgFocus);
      ctx.msgCaret.style.opacity = msgFocus && (lb < 5.25 || Math.floor(lb * 2) % 2 === 0) ? 1 : 0;
      ctx.helper.innerHTML = `${typed.length} chars · variables: {dato} {tid} {ugedag} {navn} <b style="color:#00E676">{modtager}</b>`;
      // preview + decode of the recipient names: resolved and sharp from 6.25 until the whip at 6.7
      const pv = seg(lb, 5.5, 5.8);
      tf(ctx.preview, { y: lerp(40, 0, L.outCubic(pv)), o: pv });
      const dcd = seg(lb, 5.75, 6.25);
      ctx.l1.innerHTML = `Hej <b style="color:#00E676">${L.scramble('Sofie', dcd, 3)}</b>! Er på vej hjem nu.`;
      ctx.l2.innerHTML = `Hej <b style="color:#00E676">${L.scramble('Mor', dcd, 9)}</b>! Er på vej hjem nu.`;
      // trigger dropdown: tap → cascade → items fly into the next shot
      ctx.trig.classList.toggle('focus', lb >= 7);
      const panelY = panelTop;
      ctx.touch(lb, 7, ED.left + 300 * dp, panelY + 250 * dp);
      const m = seg(lb, 7.0, 7.12);
      L.show(ctx.menu, lb >= 7);
      tf(ctx.menu, { sy: L.outCubic(m), o: m });
      const fly = seg(lb, 7.5, 8);
      ctx.menuItems.forEach((it, i) => {
        const k = seg(lb, 7.05 + i * 0.05, 7.2 + i * 0.05);
        const f = L.inCubic(fly);
        const ang = (i / 8) * Math.PI * 2;
        tf(it, { x: lerp(-30, 0, L.outCubic(k)) + Math.cos(ang) * 260 * f, y: Math.sin(ang) * 200 * f, s: 1 + 1.6 * f,
          o: k * (1 - seg(fly, 0.6, 1)), blur: 8 * f });
      });
      if (fly > 0) {
        ctx.panel.style.filter = `blur(${(6 * fly).toFixed(1)}px) brightness(${(1 - 0.6 * fly).toFixed(2)})`;
        ctx.menu.style.background = `rgba(42,44,49,${(1 - fly).toFixed(2)})`;
      }
      ctx.caps(lb);
    },
  });

  S.editor = makeEditor({
    speed: 1,
    wipe: '#00E676', // the FAB blob from the previous shot fills the screen green
    caps: [
      { at: 0, out: 2, cls: 'h-l', words: [['Giv den ', 0.1], ['et navn.', 0.35, 'green']] },
      { at: 2, out: 4, cls: 'h-l', words: [['Vælg ', 0], ['modtagere.', 0.25, 'green']] },
      { at: 4, out: 5.5, cls: 'h-l', words: [['Skriv ', 0], ['beskeden.', 0.25, 'green']] },
      { at: 5.5, out: 7, cls: 'h-m', words: [['Personlig til', 0, null, true], ['hver modtager.', 0.25, 'green']] },
    ],
  });
  // 15 s cut: the same editor at double speed, entered from the logo's purple pupil zoom, with a lead-in to
  // the trigger shots that follow ("Den sender, når…" → "…klokken slår.").
  S.editorShort = makeEditor({
    speed: 2,
    wipe: '#9586EA',
    caps: [
      { at: 0, out: 4, cls: 'h-l', words: [['Byg en ', 0], ['makro.', 0.25, 'green']] },
      { at: 4, out: 8, cls: 'h-l', words: [['Den sender,', 0, null, true], ['når…', 0.3, 'green']] },
    ],
  });

  function tokenize(s) {
    const esc = (t) => t.replace(/&/g, '&amp;').replace(/</g, '&lt;');
    return s.replace(/\{[a-zæøå]*\}?/g, (m) =>
      m.endsWith('}') ? `<span class="tok">${esc(m)}</span>` : `<span style="color:#00E676;font-family:var(--mono)">${esc(m)}</span>`
    );
  }
})();
