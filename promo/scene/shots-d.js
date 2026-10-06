/* Act 4 — drop B: eight features, two beats each, every cut a different transition (beats 48-64). */
(function () {
  const { div, seg, lerp, tf } = L;
  const S = window.SHOTS;

  function bg(root, css) {
    const b = div('layer', root);
    b.style.background = css;
    return b;
  }
  function feature(root, title, sub, titleCls = 'h-xl') {
    return SH.caps(root, [
      { at: 0, cls: titleCls, words: [[title, 0]] },
      { at: 0.2, cls: 'mono-m dim', style: 'rise', y: titleCls === 'h-xl' ? 168 : 140, words: [[sub, 0]] },
    ], { top: 220 });
  }
  /** Cube rotation helpers: the faces stay hinged on their shared edge. dir +1 = content moves left. */
  function cubeOut(root, p) {
    const q = L.inOutCubic(p);
    root.style.transformOrigin = '100% 50%';
    root.style.transform = `perspective(1700px) translateX(${(-1080 * q).toFixed(1)}px) rotateY(${(-90 * q).toFixed(2)}deg)`;
    root.style.filter = `brightness(${(1 - 0.6 * q).toFixed(2)})`;
  }
  function cubeIn(root, p) {
    const q = 1 - L.inOutCubic(p);
    root.style.transformOrigin = '0% 50%';
    root.style.transform = `perspective(1700px) translateX(${(1080 * q).toFixed(1)}px) rotateY(${(90 * q).toFixed(2)}deg)`;
    root.style.filter = `brightness(${(1 - 0.6 * q).toFixed(2)})`;
  }
  const card = (parent, css) => {
    const c = div('abs', parent);
    L.css(c, Object.assign({ borderRadius: '56px 18px 56px 18px', background: 'rgba(24,26,31,.94)',
      border: '2px solid rgba(255,255,255,.07)', boxShadow: '0 30px 80px rgba(0,0,0,.5)' }, css));
    return c;
  };

  // ───────────────────────────── featDelivered (48-50) · drop B lands here ──
  S.featDelivered = {
    build(root, ctx) {
      bg(root, 'radial-gradient(ellipse 80% 50% at 50% 55%, rgba(0,230,118,.18), rgba(0,0,0,0) 70%), #0d0d0d');
      ctx.big = div('abs', root);
      L.css(ctx.big, { left: '340px', top: '560px', width: '400px', height: '400px' });
      ctx.one = div('layer', ctx.big, L.icon('check', 400, '#00E676'));
      ctx.two = div('layer', ctx.big, L.icon('done_all', 400, '#00E676'));
      ctx.big.style.filter = 'drop-shadow(0 0 40px rgba(0,230,118,.6))';
      const c = card(root, { left: '70px', right: '70px', top: '1040px', padding: '40px 46px' });
      c.innerHTML = `<div style="display:flex;justify-content:space-between;font:700 32px var(--mono)"><span style="color:#00E676">SUCCESS</span><span style="color:#999">16:30</span></div>
        <div style="margin-top:14px;font:500 40px var(--mono);color:#f0f0f0">På vej hjem</div>
        <div style="margin-top:10px;font:400 38px var(--sans);color:#d0d0d0">Hej Sofie! Er på vej hjem nu.</div>
        <div class="st" style="margin-top:18px;font:700 36px var(--sans);color:#00E676;height:44px"></div>`;
      ctx.st = c.querySelector('.st');
      ctx.card = c;
      ctx.caps = feature(root, 'Leveret.', 'Du ser, når den lander.');
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      FX.flash = Math.max(FX.flash, 0.95 * L.decay(lb, 0, 8));
      FX.shake = Math.max(FX.shake, 1.1 * L.decay(lb, 0, 5));
      FX.rgb = Math.max(FX.rgb, 0.7 * L.decay(lb, 0, 7));
      const a = seg(lb, 0.5, 0.65), b = seg(lb, 1, 1.15);
      tf(ctx.one, { s: L.outBack(a, 3) * (1 - b), o: a * (1 - b) });
      tf(ctx.two, { s: b > 0 ? L.outBack(b, 3) : 0, o: b });
      tf(ctx.big, { s: 1 + 0.1 * L.decay(lb, 1, 6) });
      ctx.st.innerHTML = lb < 0.5 ? '' : lb < 1 ? '<span style="color:#aaa">Sent ✓</span>' : 'Delivered ✓✓';
      tf(ctx.card, { y: lerp(200, 0, L.outBack(seg(lb, 0, 0.35), 1.2)), o: seg(lb, 0, 0.1) });
      ctx.caps(lb);
      if (lb > 1.75) SH.whipOut(ctx.root, seg(lb, 1.75, 2), -1, 'fd');
    },
  };

  // ───────────────────────────── featQuiet (50-52) ──
  S.featQuiet = {
    pre: 0.25,
    build(root, ctx) {
      ctx.night = bg(root, 'linear-gradient(#060b1f, #0d1640 60%, #1a1446)');
      ctx.dawn = bg(root, 'linear-gradient(#141a3a, #4a2a4a 55%, #c76b3f)');
      const rnd = L.rng(9);
      ctx.stars = Array.from({ length: 40 }, () => {
        const s = div('abs', root);
        const z = 2 + rnd() * 4;
        L.css(s, { left: rnd() * 1080 + 'px', top: rnd() * 1300 + 'px', width: z + 'px', height: z + 'px', borderRadius: '50%', background: '#fff' });
        return { s, ph: rnd() * 6 };
      });
      // day/night wheel
      ctx.wheel = div('abs', root);
      L.css(ctx.wheel, { left: '90px', top: '1180px', width: '900px', height: '900px', borderRadius: '50%',
        border: '3px solid rgba(255,255,255,.12)' });
      const moon = div('abs', ctx.wheel, L.icon('dark_mode', 230, '#DDE3FF'));
      L.css(moon, { left: '335px', top: '-115px', filter: 'drop-shadow(0 0 40px rgba(200,210,255,.7))' });
      const sun = div('abs', ctx.wheel, L.icon('wb_sunny', 250, '#FFB300'));
      L.css(sun, { left: '325px', top: '775px', transform: 'rotate(180deg)', filter: 'drop-shadow(0 0 60px rgba(255,179,0,.9))' });
      ctx.inB = div('abs', root);
      L.css(ctx.inB, { left: '70px', top: '540px', transformOrigin: '0 100%' });
      UI.bubble(ctx.inB, 'Sover du?', 'in', 'big');
      ctx.hold = div('', ctx.inB, `${L.icon('pause', 30, '#FFB300')}<span style="margin-left:8px">23:14 · venter til 07:00</span>`);
      L.css(ctx.hold, { display: 'flex', alignItems: 'center', font: '600 28px var(--mono)', color: '#FFB300', marginTop: '12px' });
      ctx.outB = div('abs', root);
      L.css(ctx.outB, { right: '70px', top: '790px', textAlign: 'right', transformOrigin: '100% 100%' });
      UI.bubble(ctx.outB, 'Godmorgen! Så den først nu.', 'out', 'big');
      const st = div('stamp', ctx.outB, '07:00 · Delivered ✓✓');
      st.style.color = '#00E676';
      ctx.caps = SH.caps(root, [
        { at: 0, cls: 'h-xl', words: [['Stille timer.', 0]] },
        { at: 0.2, cls: 'mono-m', style: 'rise', y: 168, words: [['22:00 – 07:00', 0, 'amber']] },
      ], { top: 220 });
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.25) SH.whipIn(ctx.root, seg(lb, -0.25, 0.25), -1, 'fq');
      const turn = L.inOutCubic(seg(lb, 0.7, 1.25));
      ctx.wheel.style.transform = `rotate(${(180 * turn).toFixed(2)}deg)`;
      ctx.dawn.style.opacity = turn.toFixed(3);
      ctx.stars.forEach(({ s, ph }) => (s.style.opacity = ((1 - turn) * (0.4 + 0.6 * Math.abs(Math.sin(lb * 3 + ph)))).toFixed(2)));
      tf(ctx.inB, { s: lerp(0.4, 1, L.outBack(seg(lb, 0.1, 0.35), 2)), o: seg(lb, 0.1, 0.15) });
      tf(ctx.outB, { s: lerp(0.4, 1, L.outBack(seg(lb, 1.25, 1.5), 2)), o: seg(lb, 1.25, 1.3) });
      ctx.caps(lb);
    },
  };

  // ───────────────────────────── featWidgets (52-54) ──
  S.featWidgets = {
    pre: 0.25,
    build(root, ctx) {
      root.innerHTML = '<div class="app-bg" style="filter:blur(22px) brightness(.6) saturate(1.2);inset:-60px"></div>';
      const ws = [['Kommer lidt for sent', 'Last: 08:42 ✓'], ['Godnat skat', 'Tap to send'], ['Ringer tilbage', 'Last: 12/7 ✓']];
      ctx.ws = ws.map(([n, sub], i) => {
        const w = UI.widget(root, n, sub, 3.6);
        L.css(w.el, { position: 'absolute', left: '110px', width: '860px', top: 640 + i * 200 + 'px' });
        return w;
      });
      const row = div('abs', root);
      L.css(row, { left: '110px', right: '110px', top: '1290px', display: 'flex', justifyContent: 'space-between' });
      ['#FF7043', '#42A5F5', '#FFCA28', '#AB47BC'].forEach((c) => {
        const e = div('', row);
        L.css(e, { width: '160px', height: '160px', borderRadius: '46px', background: `linear-gradient(145deg, ${c}, ${SH.hexA(c, 0.5)})`, opacity: 0.7 });
      });
      ctx.touch = SH.touch(root);
      ctx.caps = feature(root, 'Widgets.', 'Ét tryk fra hjemmeskærmen.');
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.2) SH.sliceIn(ctx.root, seg(lb, -0.25, 0.2), 8);
      ctx.ws.forEach((w, i) => {
        const k = seg(lb, -0.1 + i * 0.1, 0.25 + i * 0.1);
        tf(w.el, { y: lerp(80, 0, L.outBack(k, 1.6)), o: k, s: i === 1 ? 1 - 0.05 * Math.sin(Math.PI * seg(lb, 0.5, 0.7)) : 1 });
      });
      const w = ctx.ws[1];
      w.sub.innerHTML = lb < 0.75 ? 'Tap to send' : 'Last: 22:30 <b style="color:#00E676">✓</b>';
      w.el.style.boxShadow = `0 18px 50px rgba(0,0,0,.45), 0 0 ${(90 * L.decay(lb, 0.75, 4)).toFixed(0)}px rgba(0,230,118,.6)`;
      ctx.touch(lb, 0.5, 540, 900);
      ctx.caps(lb);
      if (lb > 1.75) SH.zoomThroughOut(ctx.root, seg(lb, 1.75, 2), 540, 900);
    },
  };

  // ───────────────────────────── featFolders (54-56) ──
  const FOLDER = { x: 110, y: 1180, w: 860, dp: 2.4 };
  S.featFolders = {
    pre: 0.25,
    build(root, ctx) {
      bg(root, 'radial-gradient(ellipse 80% 50% at 50% 60%, rgba(233,30,99,.16), rgba(0,0,0,0) 70%), #0d0d0d');
      const dp = FOLDER.dp;
      const f = (ctx.folder = div('abs', root));
      L.css(f, { left: FOLDER.x + 'px', top: FOLDER.y + 'px', width: FOLDER.w + 'px', height: 88 * dp + 'px', filter: 'drop-shadow(0 24px 40px rgba(0,0,0,.5))' });
      const tab = div('abs', f);
      L.css(tab, { left: 0, top: 0, width: FOLDER.w * 0.38 + 'px', height: 12 * dp + 1 + 'px', background: 'rgba(233,30,99,.55)',
        borderRadius: `${8 * dp}px 0 0 0`, clipPath: `polygon(0 0, calc(100% - ${16 * dp}px) 0, 100% 100%, 0 100%)` });
      const body = div('abs', f);
      L.css(body, { left: 0, right: 0, top: 12 * dp + 'px', height: 76 * dp + 'px', borderRadius: `0 ${6 * dp}px ${18 * dp}px ${6 * dp}px`,
        background: 'linear-gradient(90deg, rgba(233,30,99,.16), rgba(233,30,99,.03)), rgba(26,26,26,.95)', display: 'flex', alignItems: 'center', overflow: 'hidden' });
      body.innerHTML = `<div style="width:${4 * dp}px;align-self:stretch;background:#E91E63"></div>
        <div style="margin-left:${12 * dp}px">${L.icon('folder', 20 * dp, '#E91E63')}</div>
        <div style="margin-left:${12 * dp}px;flex:1"><div style="font:500 ${15 * dp}px var(--mono);color:#f0f0f0">Familie</div>
        <div class="cnt" style="margin-top:${3 * dp}px;font:400 ${12 * dp}px var(--sans);color:#aaa"></div></div>
        <div class="chev" style="width:${36 * dp}px;height:${36 * dp}px;border-radius:50%;background:rgba(233,30,99,.16);display:flex;align-items:center;justify-content:center;margin-right:${20 * dp}px">${L.icon('play_arrow', 18 * dp, '#E91E63')}</div>`;
      ctx.cnt = body.querySelector('.cnt');
      ctx.chev = body.querySelector('.chev');
      const ms = [['Godnat skat', '22:30 · Every day', '#3F51B5'], ['Ringer tilbage', 'Missed call', '#00BCD4'], ['Mors fødselsdag', '08:00 · 2026-11-14 (once)', '#FF9800']];
      ctx.cards = ms.map(([n, sm, ac], i) => {
        const w = div('abs', root);
        w.style.setProperty('--dp', '2.3px');
        L.css(w, { left: '130px', width: '820px', top: 560 + i * 196 + 'px' });
        UI.macroCard(w, { name: n, summary: sm, accent: ac, enabled: true });
        return w;
      });
      // the same macros as folder members once it opens: indented 16dp, veins in the folder colour
      ctx.members = ms.map(([n, sm, ac], i) => {
        const w = div('abs', root);
        w.style.setProperty('--dp', '2.3px');
        L.css(w, { left: FOLDER.x + 16 * dp + 'px', width: FOLDER.w - 16 * dp + 'px', top: 560 + 88 * dp + 22 + i * (76 * 2.3 + 18) + 'px' });
        const mc = UI.macroCard(w, { name: n, summary: sm, accent: ac, enabled: true });
        mc.veinEl.style.background = '#E91E63';
        return w;
      });
      ctx.caps = feature(root, 'Mapper.', 'Saml makroerne ét sted.');
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.3) SH.zoomThroughIn(ctx.root, seg(lb, -0.25, 0.3));
      let inFolder = 0;
      ctx.cards.forEach((w, i) => {
        const k = L.inBack(seg(lb, 0.3 + i * 0.25, 0.5 + i * 0.25), 1.2); // lands on the 16th grid
        if (lb >= 0.5 + i * 0.25) inFolder++;
        const dy = FOLDER.y + 40 - (560 + i * 196);
        tf(w, { y: dy * k, s: 1 - 0.55 * k, o: (1 - seg(k, 0.8, 1)) * seg(lb, -0.2 + i * 0.08, 0.05 + i * 0.08), r: (i - 1) * 6 * k });
      });
      ctx.cnt.textContent = `${inFolder} macro${inFolder === 1 ? '' : 's'} · ${inFolder} live`;
      const bump = [0.5, 0.75, 1.0].reduce((a, t) => a + 0.06 * L.decay(lb, t, 9), 0);
      // …then the folder rises and opens like an accordion
      const open = L.outCubic(seg(lb, 1.25, 1.5));
      tf(ctx.folder, { s: 1 + bump, y: lerp(120, 0, L.outBack(seg(lb, -0.1, 0.3), 1.5)) + (560 - FOLDER.y) * open });
      ctx.chev.style.transform = `rotate(${(90 * open).toFixed(1)}deg)`;
      ctx.members.forEach((w, i) => {
        const k = seg(lb, 1.35 + i * 0.08, 1.6 + i * 0.08);
        tf(w, { y: lerp(-70, 0, L.outCubic(k)), o: k });
      });
      ctx.caps(lb);
      // glitch hand-off to the health check
      if (lb > 1.75) {
        FX.glitch = Math.max(FX.glitch, seg(lb, 1.75, 2));
        FX.glitchSeed = 11;
        FX.rgb = Math.max(FX.rgb, seg(lb, 1.75, 2));
      }
    },
  };

  // ───────────────────────────── featHealth (56-58) ──
  S.featHealth = {
    build(root, ctx) {
      bg(root, 'radial-gradient(ellipse 80% 50% at 50% 60%, rgba(0,230,118,.12), rgba(0,0,0,0) 70%), #0d0d0d');
      const c = card(root, { left: '70px', right: '70px', top: '560px', padding: '40px 44px 30px' });
      c.innerHTML = `<div style="font:600 34px var(--mono);color:#f0f0f0;letter-spacing:.02em;margin-bottom:12px">System</div>`;
      const rows = [
        ['alarm', 'Exact alarms', 'Allowed — fires on time'],
        ['bolt', 'Battery optimisation', "Unrestricted — won't be deferred"],
        ['notifications', 'Notifications', 'Enabled'],
        ['history', 'Last background check', 'Ran 16:02'],
      ];
      ctx.rows = rows.map(([ic, t, d]) => {
        const r = div('', c);
        L.css(r, { display: 'flex', alignItems: 'center', gap: '26px', padding: '20px 0', borderTop: '1px solid rgba(255,255,255,.07)' });
        r.innerHTML = `${L.icon(ic, 46, '#9aa')}<div style="flex:1"><div style="font:600 38px var(--sans);color:#f0f0f0">${t}</div>
          <div style="font:400 29px var(--sans);color:#a8a8a8;margin-top:4px">${d}</div></div>`;
        const ok = div('', r, L.icon('check_circle', 58, '#00E676'));
        return { r, ok };
      });
      const n = div('', c, `<span style="color:#00BCD4">●</span> Morgen-check-in → <b style="color:#f0f0f0">i morgen 07:00</b>`);
      L.css(n, { marginTop: '18px', font: '500 30px var(--mono)', color: '#aaa' });
      ctx.next = n;
      ctx.card = c;
      ctx.caps = feature(root, 'Health-tjek.', 'Alt kører – og du kan se det.', 'h-l');
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.15) {
        FX.glitch = Math.max(FX.glitch, 1 - seg(lb, 0, 0.15));
        FX.glitchSeed = 12;
      }
      tf(ctx.card, { y: lerp(60, 0, L.outCubic(seg(lb, 0, 0.3))), o: seg(lb, 0, 0.1) });
      ctx.rows.forEach(({ r, ok }, i) => {
        const k = seg(lb, 0.25 * (i + 1), 0.25 * (i + 1) + 0.15);
        tf(ok, { s: L.outBack(k, 3), o: k });
        r.style.opacity = (0.45 + 0.55 * seg(lb, 0.25 * (i + 1) - 0.05, 0.25 * (i + 1))).toFixed(2);
      });
      ctx.next.style.opacity = seg(lb, 1.25, 1.4).toFixed(2);
      ctx.caps(lb);
    },
  };

  // ───────────────────────────── featReboot (58-60) ──
  S.featReboot = {
    pre: 0.25,
    build(root, ctx) {
      bg(root, '#0b0c0e');
      const vis = (ctx.vis = div('layer', root));
      ctx.glow = div('abs', vis);
      L.css(ctx.glow, { left: '140px', top: '560px', width: '800px', height: '800px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(0,230,118,.30), rgba(0,230,118,0) 65%)' });
      const ns = 'http://www.w3.org/2000/svg';
      const svg = document.createElementNS(ns, 'svg');
      svg.setAttribute('viewBox', '0 0 600 600');
      L.css(svg, { position: 'absolute', left: '240px', top: '660px', width: '600px', height: '600px' });
      svg.innerHTML = `<circle cx="300" cy="300" r="250" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="20"/>
        <circle class="arc" cx="300" cy="300" r="250" fill="none" stroke="#00E676" stroke-width="20" stroke-linecap="round" stroke-dasharray="1571" stroke-dashoffset="1571" transform="rotate(-90 300 300)"/>`;
      vis.appendChild(svg);
      ctx.arc = svg.querySelector('.arc');
      ctx.svg = svg;
      ctx.pw = div('abs', vis, L.icon('power_settings_new', 260, '#f0f0f0'));
      L.css(ctx.pw, { left: '410px', top: '830px' });
      ctx.chips = ['07:00', '08:30', '16:30', '22:00'].map((t, i) => {
        const c = div('abs chip', vis, `${L.icon('alarm', 38, '#00E676')}<span>${t}</span>`);
        const pos = [[90, 600], [730, 600], [90, 1220], [730, 1220]][i];
        L.css(c, { left: pos[0] + 'px', top: pos[1] + 'px', background: 'rgba(0,230,118,.12)', border: '2px solid rgba(0,230,118,.5)',
          color: '#e8ffe8', font: '600 36px var(--mono)' });
        return c;
      });
      ctx.re = div('abs mono-m', vis, '✓ 4 alarmer genarmeret');
      L.css(ctx.re, { left: 0, right: 0, top: '1390px', textAlign: 'center', color: '#00E676' });
      ctx.caps = SH.caps(root, [
        { at: 0, cls: 'h-l', words: [['Overlever', 0, null, true], ['genstart.', 0.25, 'green']] },
      ], { top: 200 });
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.25) SH.irisIn(ctx.root, seg(lb, -0.25, 0.25), 540, 960);
      // power down … then everything re-arms
      const off = lb >= 0 && lb < 0.75;
      const up = seg(lb, 0.75, 1.0);
      ctx.vis.style.filter = off ? `brightness(${(1 - 0.55 * seg(lb, 0, 0.2)).toFixed(2)}) saturate(0.3)` : 'none';
      tf(ctx.pw, { r: off ? -360 * L.inOutCubic(seg(lb, 0.05, 0.75)) : 0, s: 1 + 0.15 * L.decay(lb, 0.75, 6) });
      ctx.pw.querySelector('svg').style.fill = lb >= 0.75 ? '#00E676' : '#8a8a8a';
      ctx.arc.setAttribute('stroke-dashoffset', (1571 * (1 - L.outCubic(up))).toFixed(1));
      tf(ctx.glow, { o: up, s: 0.8 + 0.3 * up });
      ctx.chips.forEach((c, i) => {
        const k = seg(lb, 0.9 + i * 0.1, 1.05 + i * 0.1);
        tf(c, { s: L.outBack(k, 2.5), o: k });
      });
      tf(ctx.re, { y: lerp(30, 0, L.outCubic(seg(lb, 1.3, 1.5))), o: seg(lb, 1.3, 1.45) });
      ctx.caps(lb);
      if (lb > 1.75) SH.pushOut(ctx.root, seg(lb, 1.75, 2), -1, 'fr');
    },
  };

  // ───────────────────────────── featVars (60-62) ──
  const VARS = [['{modtager}', 'Sofie'], ['{ugedag}', 'tirsdag'], ['{tid}', '16:30']];
  S.featVars = {
    pre: 0.25,
    build(root, ctx) {
      bg(root, 'radial-gradient(ellipse 80% 50% at 50% 55%, rgba(0,230,118,.14), rgba(0,0,0,0) 70%), #0d0d0d');
      const c = card(root, { left: '70px', right: '70px', top: '640px', padding: '48px 50px', font: '500 54px/1.45 var(--sans)', color: '#f0f0f0' });
      c.innerHTML = 'Hej <span class="v0"></span>! Det er <span class="v1"></span> kl. <span class="v2"></span> – jeg er på vej.';
      ctx.vs = [0, 1, 2].map((i) => c.querySelector('.v' + i));
      ctx.card = c;
      const row = div('abs', root);
      L.css(row, { left: '60px', right: '60px', top: '1180px', display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: '18px' });
      ctx.chips = ['{dato}', '{tid}', '{ugedag}', '{navn}', '{modtager}', '{afsender}'].map((t) => {
        const ch = div('', row, t);
        L.css(ch, { padding: '14px 24px', borderRadius: '28px 10px 28px 10px', background: 'rgba(0,230,118,.12)', border: '2px solid rgba(0,230,118,.45)',
          font: '600 36px var(--mono)', color: '#00E676' });
        return ch;
      });
      ctx.caps = feature(root, 'Smarte variabler.', 'Udfyldes, når den sender.', 'h-l');
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.25) SH.pushIn(ctx.root, seg(lb, -0.25, 0.25), -1, 'fv');
      const d = seg(lb, 0.5, 1.0);
      ctx.vs.forEach((v, i) => {
        const [tok, val] = VARS[i];
        if (d <= 0) v.innerHTML = `<span class="tok">${tok}</span>`;
        else v.innerHTML = `<b style="color:#00E676">${L.scramble(val, seg(d, i * 0.15, 0.7 + i * 0.15), 5 + i)}</b>`;
      });
      ctx.chips.forEach((ch, i) => {
        const k = seg(lb, 0.05 + i * 0.07, 0.25 + i * 0.07);
        tf(ch, { s: L.outBack(k, 2.5), o: k });
      });
      ctx.caps(lb);
      if (lb > 1.75) cubeOut(ctx.root, seg(lb, 1.75, 2));
    },
  };

  // ───────────────────────────── featBackup (62-64) ──
  S.featBackup = {
    pre: 0.25,
    build(root, ctx) {
      bg(root, 'radial-gradient(ellipse 80% 50% at 50% 55%, rgba(66,209,202,.16), rgba(0,0,0,0) 70%), #0d0d0d');
      const c = (ctx.file = card(root, { left: '120px', right: '120px', top: '600px', padding: '40px 46px' }));
      c.innerHTML = `<div style="display:flex;align-items:center;gap:18px;font:600 32px var(--mono);color:#42D1CA;margin-bottom:22px">${L.icon('upload_file', 48, '#42D1CA')}automatiq-backup.json</div>
        <pre style="margin:0;font:500 34px/1.5 var(--mono);color:#ddd">{
  <span style="color:#82AAFF">"macros"</span>: [ <span style="color:#F78C6C">4</span> ],
  <span style="color:#82AAFF">"folders"</span>: [ <span style="color:#F78C6C">1</span> ],
  <span style="color:#82AAFF">"settings"</span>: { … }
}</pre>`;
      ctx.ok = div('abs', root, `${L.icon('check_circle', 60, '#00E676')}<span style="margin-left:16px">Eksporteret</span>`);
      L.css(ctx.ok, { left: 0, right: 0, top: '1180px', display: 'flex', justifyContent: 'center', alignItems: 'center', font: '700 50px var(--sans)', color: '#00E676' });
      ctx.arrow = div('abs', root, L.icon('upload_file', 150, '#42D1CA'));
      L.css(ctx.arrow, { left: '465px', top: '1300px' });
      ctx.caps = feature(root, 'Backup.', 'Alt med – i én JSON-fil.');
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.25) cubeIn(ctx.root, seg(lb, -0.25, 0.25));
      tf(ctx.file, { y: lerp(40, 0, L.outCubic(seg(lb, 0, 0.3))), r: lerp(-3, 0, L.outCubic(seg(lb, 0, 0.3))) });
      const up = seg(lb, 0.5, 0.95);
      tf(ctx.arrow, { y: -260 * L.inCubic(up), o: (1 - up) * seg(lb, 0.2, 0.35), s: 1 - 0.4 * up });
      const k = seg(lb, 1, 1.2);
      tf(ctx.ok, { s: L.outBack(k, 2.5), o: k });
      ctx.caps(lb);
      // collapse into the trust section
      if (lb > 1.75) {
        const q = L.inCubic(seg(lb, 1.75, 2));
        ctx.root.style.transformOrigin = '540px 960px';
        ctx.root.style.transform = `scale(${(1 - 0.75 * q).toFixed(4)})`;
        ctx.root.style.opacity = (1 - q).toFixed(3);
        ctx.root.style.filter = `blur(${(10 * q).toFixed(1)}px)`;
      }
    },
  };
})();
