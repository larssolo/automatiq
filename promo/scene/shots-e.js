/* Act 5 — trust, recap, end card (beats 64-80). */
(function () {
  const { div, seg, lerp, tf } = L;
  const S = window.SHOTS;

  // ───────────────────────────── trust (64-72) ──
  const TP = { dp: 1.5, top: 640 };
  S.trust = {
    pre: 0.25,
    build(root, ctx) {
      const cam = (ctx.cam = div('layer', root));
      ctx.shield = div('abs', cam);
      L.css(ctx.shield, { left: '90px', top: '520px', width: '900px', height: '1500px', borderRadius: '50%',
        background: 'radial-gradient(ellipse, rgba(0,230,118,.22), rgba(0,230,118,.05) 45%, rgba(0,0,0,0) 70%)' });
      ctx.phoneWrap = div('layer', cam);
      const ph = UI.phone(ctx.phoneWrap, TP.dp);
      ctx.phX = (1080 - 372 * TP.dp) / 2;
      L.css(ph.el, { left: ctx.phX + 'px', top: TP.top + 'px' });
      ctx.ui = UI.listScreen(ph.screen, [
        { name: 'Morgen-check-in', summary: '07:00 · Every 2 weeks · Mon · Wed', accent: '#00BCD4', enabled: true },
        { name: 'På vej hjem', summary: 'On departure · 150 m', accent: '#FF9800', enabled: true },
        { name: 'Ringer tilbage', summary: 'Missed call', accent: '#E91E63', enabled: true },
        { name: 'Ferie-autosvar', summary: 'Auto-reply', accent: '#9C27B0', enabled: true, ai: true },
      ], { time: '16:30' });
      ctx.ui.live.textContent = '4 of 4 live';
      ctx.lock = div('abs', cam, L.icon('lock', 120, '#00E676'));
      L.css(ctx.lock, { left: '480px', top: '520px', width: '120px', height: '120px', filter: 'drop-shadow(0 0 30px rgba(0,230,118,.7))' });
      ctx.noAcc = SH.tile(cam, 'person_off', '#FF5252', 210, 0.22);
      L.css(ctx.noAcc, { left: '190px', top: '1180px' });
      ctx.noSrv = SH.tile(cam, 'cloud_off', '#FF5252', 210, 0.22);
      L.css(ctx.noSrv, { left: '890px', top: '1180px' });
      // the AI opt-in switch, the app's own green/amber ThemedSwitch at poster size
      ctx.aiBox = div('abs', cam);
      ctx.aiBox.style.setProperty('--dp', '6px');
      L.css(ctx.aiBox, { left: '190px', right: '190px', top: '1560px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '34px',
        padding: '26px 34px', borderRadius: '56px 18px 56px 18px', background: 'rgba(20,22,26,.92)', border: '2px solid rgba(255,255,255,.08)' });
      div('', ctx.aiBox, `${L.icon('auto_awesome', 54, '#B388FF')}`);
      div('', ctx.aiBox, 'AI').style.font = '700 50px var(--sans)';
      ctx.sw = UI.switch(ctx.aiBox, false);
      ctx.caps = SH.caps(root, [
        { at: 0, out: 2, cls: 'h-l', words: [['Alt kører', 0, null, true], ['på din telefon.', 0.4, 'green']] },
        { at: 2, out: 3, cls: 'h-xl', words: [['Ingen konto.', 0]] },
        { at: 3, out: 4, cls: 'h-xl', words: [['Ingen server.', 0]] },
        { at: 4, out: 6, cls: 'h-l', words: [['AI kun,', 0, null, true], ['hvis du vil.', 0.3, 'grad']] },
      ], { top: 210 });
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      FX.pulse = false;
      // enter: everything collapses in from the backup shot
      const e = L.outCubic(seg(lb, -0.25, 0.45));
      const breathe = 0.5 + 0.5 * Math.cos(Math.PI * lb);
      ctx.ui.dot.style.opacity = (0.35 + 0.65 * breathe).toFixed(2);
      ctx.ui.cards.forEach((c) => c.vein(true, breathe));
      // the final build: the phone spins and dives at the lens
      const spin = L.inOutCubic(seg(lb, 6, 7.4));
      const dive = L.inExpo(seg(lb, 6.6, 7.5));
      ctx.phoneWrap.style.transformOrigin = '540px 1250px';
      ctx.phoneWrap.style.transform = `perspective(2400px) rotateY(${(360 * spin + 8 * Math.sin(lb * 0.7)).toFixed(2)}deg) rotateX(${(4 * Math.sin(lb * 0.5)).toFixed(2)}deg) scale(${(lerp(0.7, 1, e) * (1 + 3 * dive)).toFixed(4)})`;
      ctx.phoneWrap.style.opacity = e.toFixed(3);
      ctx.phoneWrap.style.filter = dive > 0.02 ? `blur(${(10 * dive).toFixed(1)}px)` : 'none';
      tf(ctx.shield, { s: 0.9 + 0.1 * breathe, o: e * (1 - dive) });
      tf(ctx.lock, { s: L.outBack(seg(lb, 0.6, 0.9), 2.5) * (1 - dive), o: seg(lb, 0.6, 0.7) * (1 - seg(lb, 5.8, 6.2)) });
      [[ctx.noAcc, 2], [ctx.noSrv, 3]].forEach(([t, at]) => {
        const k = seg(lb, at, at + 0.3);
        tf(t, { s: lerp(1.8, 1, L.outQuart(k)) * (1 - seg(lb, 5.8, 6.2)), o: seg(lb, at, at + 0.05) * (1 - seg(lb, 5.8, 6.2)), r: lerp(-20, -6, L.outCubic(k)) });
      });
      const ai = seg(lb, 4, 4.3);
      tf(ctx.aiBox, { y: lerp(80, 0, L.outBack(ai, 1.6)), o: ai * (1 - seg(lb, 5.8, 6.2)) });
      ctx.sw.set(lb < 4.5 ? 0 : L.spring(lb - 4.5, 2.2, 7));
      ctx.caps(lb);
      FX.dark = Math.max(FX.dark, 0.55 * seg(lb, 7.5, 7.55));
    },
  };

  // ───────────────────────────── recap (72-76) ──
  const HITS = [
    { word: 'Planlæg.', bg: '#00E676', fg: '#04140b' },
    { word: 'Svar.', bg: '#0d0d0d', fg: '#f0f0f0' },
    { word: 'Automatisér.', bg: '#42D1CA', fg: '#06201f' },
    { word: 'Slap af.', bg: 'img', fg: '#ffffff' },
  ];
  S.recap = {
    build(root, ctx) {
      ctx.frames = HITS.map((h, i) => {
        const f = div('layer', root);
        if (h.bg === 'img') f.innerHTML = '<div class="app-bg"></div><div class="layer" style="background:rgba(10,8,40,.25)"></div>';
        else f.style.background = h.bg;
        const vis = div('layer', f);
        if (i === 0) {
          vis.innerHTML = `<svg viewBox="0 0 600 600" style="position:absolute;left:140px;top:560px;width:800px;height:800px">
            <circle cx="300" cy="300" r="270" fill="none" stroke="rgba(0,0,0,.18)" stroke-width="26"/>
            <circle cx="300" cy="300" r="270" fill="none" stroke="#04140b" stroke-width="26" stroke-linecap="round" stroke-dasharray="1696" stroke-dashoffset="420" transform="rotate(-90 300 300)"/></svg>`;
        } else if (i === 1) {
          const a = div('abs', vis);
          L.css(a, { left: '70px', top: '470px' });
          UI.bubble(a, 'Er du ledig?', 'in', 'big');
          const b = div('abs', vis);
          L.css(b, { right: '70px', top: '1260px' });
          UI.bubble(b, 'Ringer kl. 19!', 'out', 'big');
        } else if (i === 2) {
          const r = UI.robot(vis, 380);
          L.css(r, { left: '350px', top: '440px', boxShadow: '0 30px 80px rgba(0,0,0,.35)' });
        } else {
          const row = div('abs', vis);
          row.style.setProperty('--dp', '4px');
          L.css(row, { left: 0, right: 0, top: '560px', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '24px' });
          row.innerHTML = '<div class="pdot" style="width:40px;height:40px"></div><span style="font:500 76px var(--mono);color:#fff">4 of 4 live</span>';
          ctx.dot = row.firstChild;
        }
        const w = div('abs', f, h.word);
        L.css(w, { left: 0, right: 0, top: i === 2 ? '930px' : '880px', textAlign: 'center', color: h.fg,
          font: `950 ${i === 2 ? 150 : 190}px/1 var(--sans)`, letterSpacing: '-0.05em' });
        return { f, w, vis };
      });
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      const i = Math.min(3, Math.max(0, Math.floor(lb)));
      ctx.frames.forEach((fr, j) => L.show(fr.f, j === i));
      const k = lb - i;
      const fr = ctx.frames[i];
      tf(fr.w, { s: lerp(1.35, 1, L.outQuart(seg(k, 0, 0.25))), blur: lerp(10, 0, L.outCubic(seg(k, 0, 0.2))) });
      tf(fr.vis, { s: lerp(1.15, 1, L.outCubic(seg(k, 0, 0.5))), r: (i % 2 ? 1.5 : -1.5) * (1 - L.outCubic(seg(k, 0, 0.5))) });
      FX.flash = Math.max(FX.flash, (i === 0 ? 0.9 : 0.35) * L.decay(k, 0, i === 0 ? 8 : 12));
      FX.shake = Math.max(FX.shake, (i === 0 ? 0.9 : 0.4) * L.decay(k, 0, 6));
      if (i === 3 && ctx.dot) ctx.dot.style.opacity = (0.4 + 0.6 * Math.abs(Math.cos(Math.PI * lb))).toFixed(2);
      // last beat: push in towards the logo hit
      if (lb > 3.5) {
        const z = L.inCubic(seg(lb, 3.5, 4));
        ctx.root.style.transform = `scale(${(1 + 0.35 * z).toFixed(4)})`;
        ctx.root.style.filter = `blur(${(6 * z).toFixed(1)}px) brightness(${(1 + 0.8 * z).toFixed(2)})`;
      }
    },
  };

  // ───────────────────────────── endCard (76-80) ──
  const ER = { x: 540, y: 690, size: 340 };
  S.endCard = {
    build(root, ctx) {
      const cam = (ctx.cam = div('layer', root));
      ctx.glow = div('abs', cam);
      L.css(ctx.glow, { left: ER.x - 560 + 'px', top: ER.y - 560 + 'px', width: '1120px', height: '1120px', borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(66,209,202,.36), rgba(0,230,118,.08) 45%, rgba(0,0,0,0) 70%)' });
      ctx.rings = ['#00E676', '#42D1CA', '#8a7dff'].map((c) => {
        const r = div('abs', cam);
        L.css(r, { left: ER.x - 120 + 'px', top: ER.y - 120 + 'px', width: '240px', height: '240px', borderRadius: '50%',
          border: `8px solid ${c}`, boxShadow: `0 0 40px ${c}` });
        return r;
      });
      ctx.robot = UI.robot(cam, ER.size);
      L.css(ctx.robot, { left: ER.x - ER.size / 2 + 'px', top: ER.y - ER.size / 2 + 'px',
        boxShadow: '0 30px 90px rgba(0,0,0,.55), 0 0 0 8px rgba(66,209,202,.18)' });
      const wm = (ctx.wm = div('abs', cam));
      L.css(wm, { left: 0, right: 0, top: '930px', display: 'flex', justifyContent: 'center', alignItems: 'center' });
      ctx.wdot = div('', wm);
      L.css(ctx.wdot, { width: '32px', height: '32px', borderRadius: '50%', background: '#00E676', marginRight: '28px',
        boxShadow: '0 0 30px rgba(0,230,118,.9)' });
      ctx.letters = [...'automatiq'].map((ch) => {
        const s = L.el('span', 'kw', wm, ch);
        L.css(s, { font: '500 116px/1 var(--mono)', letterSpacing: '-0.02em' });
        return s;
      });
      ctx.tag = L.kinetic(cam, 'h-l shadow-txt', [{ text: 'SMS på ', at: 1 }, { text: 'autopilot.', at: 1.25, cls: 'grad' }]);
      ctx.tag.root.style.top = '1100px';
      ctx.spec = L.kinetic(cam, 'mono-s dim', [{ text: 'android 8.0+  ·  kører lokalt  ·  ai via gemini', at: 1.6 }], { style: 'rise' });
      ctx.spec.root.style.top = '1290px';
      ctx.url = L.kinetic(cam, 'mono-m green', [{ text: 'larssohl.dk', at: 2.1 }], { style: 'rise' });
      ctx.url.root.style.top = '1420px';
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      FX.flash = Math.max(FX.flash, 0.95 * L.decay(lb, 0, 7));
      FX.shake = Math.max(FX.shake, 1.2 * L.decay(lb, 0, 4.5));
      FX.rgb = Math.max(FX.rgb, 0.7 * L.decay(lb, 0, 6));
      ctx.cam.style.transformOrigin = '540px 900px';
      ctx.cam.style.transform = `scale(${(1 + 0.035 * seg(lb, 0, 4)).toFixed(4)})`;
      ctx.rings.forEach((r, i) => {
        const p = seg(lb, i * 0.1, 1.4 + i * 0.1);
        tf(r, { s: 0.2 + 9 * L.outExpo(p), o: (1 - p) * (lb >= i * 0.1 ? 1 : 0) });
        r.style.borderWidth = lerp(12, 2, p).toFixed(1) + 'px';
      });
      const sp = L.spring(lb, 1.4, 5);
      const beat = 0.06 * (L.decay(lb, 2, 6) + L.decay(lb, 3, 6));
      tf(ctx.robot, { s: Math.max(0, sp) * (1 + beat), r: lerp(-14, 0, Math.min(1, sp)) });
      tf(ctx.glow, { s: Math.max(0, sp) * (0.9 + 0.1 * Math.cos(lb * Math.PI)), o: seg(lb, 0, 0.3) });
      const dk = lb - 0.25;
      const hb = 0.5 * (L.decay(lb, 2, 7) + L.decay(lb, 3, 7));
      tf(ctx.wdot, { s: L.outBack(seg(dk, 0, 0.2), 3) * (1 + hb), o: seg(dk, 0, 0.05) });
      ctx.letters.forEach((s, i) => {
        const k = dk - 0.075 * (i + 1);
        tf(s, { y: lerp(40, 0, L.outBack(seg(k, 0, 0.18), 2.5)), o: seg(k, 0, 0.04) });
      });
      ctx.tag.render(lb);
      ctx.spec.render(lb);
      ctx.url.render(lb);
    },
  };
})();
