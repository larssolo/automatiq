/* Act 2 — "Den sender, når…": the eight triggers, one per two beats (beats 24-40). */
(function () {
  const { div, seg, lerp, tf } = L;
  const S = window.SHOTS;
  const T = SH.TRIGGERS;
  const LAND = SH.land;
  const BGX = LAND ? '74%' : '50%'; // landscape: the glow sits behind the right-hand column

  function darkBg(root, glow) {
    const bg = div('layer', root);
    bg.style.background = `radial-gradient(ellipse 80% 55% at ${BGX} 52%, ${SH.hexA(glow, 0.22)} 0%, ${SH.hexA(glow, 0.04)} 55%, rgba(0,0,0,0) 80%), #0d0d0d`;
    return bg;
  }

  // ───────────────────────────── trigIntro (24-26) ──
  S.trigIntro = {
    pre: 0.2,
    build(root, ctx) {
      ctx.tiles = T.map((t) => SH.tile(root, t.icon, t.color, 150));
      ctx.k = L.kinetic(root, 'h-xl shadow-txt', [
        { text: 'Den sender,', at: 0, br: true },
        { text: 'når…', at: 1, cls: 'green' },
      ]);
      ctx.k.root.style.top = LAND ? '420px' : '800px';
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      const spin = 14 * lb + 120 * L.inCubic(seg(lb, 1.3, 2));
      const out = seg(lb, 1.5, 2);
      const RX = LAND ? 1.7 : 1, RY = LAND ? 0.88 : 1; // landscape: the orbit is an ellipse
      ctx.tiles.forEach((t, i) => {
        const a = ((-90 + i * 45 + spin) * Math.PI) / 180;
        const k = seg(lb, -0.2 + i * 0.04, 0.2 + i * 0.04);
        const R = lerp(700, 420, L.outCubic(k));
        let x = SH.CX + Math.cos(a) * R * RX, y = SH.CY + Math.sin(a) * R * 1.05 * RY, s = L.outBack(k, 1.8), o = seg(k, 0, 0.2), blur = 0;
        if (i === 0) {
          // the clock tile dives into the camera → next shot
          const z = L.inExpo(out);
          x = lerp(x, SH.CX, L.inOutCubic(out));
          y = lerp(y, SH.CY, L.inOutCubic(out));
          s *= 1 + 16 * z;
          blur = 6 * z;
        } else {
          const f = L.inCubic(out);
          x += Math.cos(a) * 500 * f * RX;
          y += Math.sin(a) * 500 * f * RY;
          o *= 1 - f;
        }
        L.css(t, { left: x + 'px', top: y + 'px' });
        tf(t, { s, o, blur, r: lerp(-30, 0, L.outCubic(k)) });
      });
      ctx.k.render(lb, 1.5, 0.3);
    },
  };

  // ───────────────────────────── trigScheduled (26-28) ──
  const SCH_PLACE = SH.place(540, 1000, 0.88);
  S.trigScheduled = {
    build(root, ctx) {
      darkBg(root, '#00BCD4');
      const cam = (ctx.cam = div('layer', SH.vis(root, SCH_PLACE)));
      const ns = 'http://www.w3.org/2000/svg';
      const svg = document.createElementNS(ns, 'svg');
      svg.setAttribute('width', 760); svg.setAttribute('height', 760); svg.setAttribute('viewBox', '0 0 760 760');
      L.css(svg, { position: 'absolute', left: '160px', top: '500px', overflow: 'visible' });
      svg.innerHTML = `<defs><linearGradient id="clk" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#00BCD4"/><stop offset="1" stop-color="#00E676"/></linearGradient></defs>
        <circle cx="380" cy="380" r="300" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="24"/>
        ${Array.from({ length: 12 }, (_, i) => {
          const a = (i / 12) * Math.PI * 2;
          return `<line x1="${380 + Math.sin(a) * 248}" y1="${380 - Math.cos(a) * 248}" x2="${380 + Math.sin(a) * 268}" y2="${380 - Math.cos(a) * 268}" stroke="rgba(255,255,255,.35)" stroke-width="${i % 3 ? 4 : 8}" stroke-linecap="round"/>`;
        }).join('')}
        <circle class="arc" cx="380" cy="380" r="300" fill="none" stroke="url(#clk)" stroke-width="24" stroke-linecap="round" transform="rotate(-90 380 380)" stroke-dasharray="1885" stroke-dashoffset="1885" style="filter:drop-shadow(0 0 18px rgba(0,230,118,.6))"/>`;
      cam.appendChild(svg);
      ctx.svg = svg;
      ctx.arc = svg.querySelector('.arc');
      // rolling digits
      const dg = (ctx.digits = div('abs', cam));
      L.css(dg, { left: 0, right: 0, top: '772px', display: 'flex', justifyContent: 'center', font: '600 196px/1 var(--mono)', letterSpacing: '-0.04em' });
      ctx.cols = [['0', '0'], ['6', '7'], [':', ':'], ['5', '0'], ['9', '0']].map(([a, b]) => {
        const win = div('', dg);
        L.css(win, { height: '200px', overflow: 'hidden', position: 'relative' });
        const col = div('', win, `<div>${a}</div><div style="color:#00E676">${b}</div>`);
        L.css(col, { lineHeight: '200px' });
        return { col, roll: a !== b };
      });
      // weekday pills
      const row = div('abs', cam);
      L.css(row, { left: 0, right: 0, top: '1340px', display: 'flex', justifyContent: 'center', gap: '14px' });
      ctx.pills = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d, i) => {
        const on = [0, 2, 4].includes(i);
        const p = div('', row, d);
        L.css(p, { width: '118px', height: '76px', borderRadius: '24px 8px 24px 8px', display: 'flex', alignItems: 'center',
          justifyContent: 'center', font: '600 30px var(--mono)', background: on ? '#00E676' : 'rgba(255,255,255,.08)',
          color: on ? '#04140b' : '#9a9a9a' });
        return p;
      });
      ctx.every = div('abs mono-m', cam, '↻ Every 2 weeks');
      L.css(ctx.every, { left: 0, right: 0, top: '1450px', textAlign: 'center', color: '#00BCD4' });
      ctx.caps = SH.caps(root, [{ at: 0, cls: 'h-l', words: [['…klokken ', 0], ['slår.', 0.25, 'green']] }]);
      ctx.chip = SH.trigChip(root);
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      // continue the dive: everything settles from big to rest
      const z = L.outExpo(seg(lb, 0, 0.45));
      ctx.cam.style.transformOrigin = '540px 880px';
      ctx.cam.style.transform = `scale(${lerp(2.6, 1, z).toFixed(4)})`;
      ctx.cam.style.filter = z < 0.98 ? `blur(${(10 * (1 - z)).toFixed(1)}px)` : 'none';
      const prog = lerp(0.68, 1, L.inOutCubic(seg(lb, 0, 1)));
      ctx.arc.setAttribute('stroke-dashoffset', (1885 * (1 - prog)).toFixed(1));
      const ding = L.decay(lb, 1, 5);
      tf(ctx.svg, { s: 1 + 0.06 * ding });
      ctx.svg.style.filter = `drop-shadow(0 0 ${(20 + 50 * ding).toFixed(0)}px rgba(0,230,118,${(0.25 + 0.5 * ding).toFixed(2)}))`;
      ctx.cols.forEach((c, i) => {
        if (!c.roll) return;
        const r = L.outBack(seg(lb, 0.72 + i * 0.05, 0.98 + i * 0.05), 1.6);
        c.col.style.transform = `translateY(${(-200 * r).toFixed(1)}px)`;
      });
      ctx.pills.forEach((p, i) => {
        const k = seg(lb, 0.1 + i * 0.06, 0.3 + i * 0.06);
        tf(p, { y: lerp(40, 0, L.outBack(k, 2)), o: k });
      });
      const ev = seg(lb, 0.55, 0.8);
      tf(ctx.every, { y: lerp(30, 0, L.outCubic(ev)), o: ev });
      FX.flash = Math.max(FX.flash, 0.18 * L.decay(lb, 1, 9));
      ctx.caps(lb);
      ctx.chip(lb, 0);
      if (lb > 1.75) SH.whipOut(ctx.root, seg(lb, 1.75, 2), -1, 'sch');
    },
  };

  // ───────────────────────────── trigManual (28-30) ──
  const MAN_PLACE = SH.place(540, 980, 0.92);
  S.trigManual = {
    pre: 0.25,
    build(root, ctx) {
      const bg = div('layer', root);
      bg.innerHTML = '<div class="app-bg" style="filter:blur(26px) brightness(.55) saturate(1.2);inset:-60px"></div>';
      const cam = (ctx.cam = div('layer', SH.vis(root, MAN_PLACE)));
      // a home screen: one row of icons, the widget, the dock
      const icons = ['#FF7043', '#42A5F5', '#FFCA28', '#AB47BC', '#26A69A', '#EC407A', '#7E57C2', '#66BB6A'];
      icons.forEach((c, i) => {
        const e = div('abs', cam);
        const x = 120 + (i % 4) * 230, y = i < 4 ? (LAND ? 640 : 560) : (LAND ? 1330 : 1740);
        L.css(e, { left: x + 'px', top: y + 'px', width: '150px', height: '150px', borderRadius: '44px',
          background: `linear-gradient(145deg, ${c}, ${SH.hexA(c, 0.55)})`, opacity: 0.55 });
      });
      ctx.w = UI.widget(cam, 'Kommer lidt for sent', 'Tap to send', 4.4);
      L.css(ctx.w.el, { position: 'absolute', left: '100px', width: '880px', top: '880px', transformOrigin: '50% 50%' });
      ctx.send = ctx.w.el.firstChild;
      ctx.touch = SH.touch(cam);
      ctx.caps = SH.caps(root, [{ at: 0, cls: 'h-l', words: [['…du trykker', 0, null, true], ['én gang.', 0.3, 'green']] }]);
      ctx.chip = SH.trigChip(root);
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.25) SH.whipIn(ctx.root, seg(lb, -0.25, 0.25), -1, 'man');
      const press = 1 - 0.05 * Math.sin(Math.PI * seg(lb, 0.5, 0.75));
      tf(ctx.w.el, { s: press });
      // send arrow leaves, a fresh one returns
      const fly = seg(lb, 0.55, 0.9);
      const back = seg(lb, 1.0, 1.2);
      if (back > 0) tf(ctx.send, { s: L.outBack(back, 2), o: back });
      else tf(ctx.send, { x: 260 * L.inCubic(fly), y: -140 * L.inCubic(fly), o: 1 - fly });
      ctx.w.sub.innerHTML = lb < 1 ? 'Tap to send' : 'Last: 08:42 <b style="color:#00E676">✓</b>';
      if (lb >= 1) tf(ctx.w.sub, { s: 1 + 0.12 * L.decay(lb, 1, 8) });
      ctx.w.el.style.boxShadow = `0 18px 50px rgba(0,0,0,.45), 0 0 ${(80 * L.decay(lb, 1, 4)).toFixed(0)}px rgba(0,230,118,.55)`;
      ctx.touch(lb, 0.5, 540, 1000);
      ctx.caps(lb);
      ctx.chip(lb, 1);
      if (lb > 1.75) SH.pushOut(ctx.root, seg(lb, 1.75, 2), -1, 'man');
    },
  };

  // ───────────────────────────── trigReply (30-32) ──
  const REP_PLACE = SH.place(540, 885, 0.9);
  S.trigReply = {
    pre: 0.25,
    build(root, ctx) {
      const bg = div('layer', root);
      bg.style.background = `radial-gradient(ellipse 90% 60% at ${BGX} 60%, rgba(179,136,255,.16), rgba(0,0,0,0) 70%), #101116`;
      const cam = (ctx.cam = div('layer', SH.vis(root, REP_PLACE)));
      const head = div('abs', cam);
      L.css(head, { left: '70px', top: '520px', display: 'flex', alignItems: 'center', gap: '28px' });
      head.innerHTML = `<div style="width:124px;height:124px;border-radius:50%;background:linear-gradient(140deg,#42D1CA,#1e88e5);display:flex;align-items:center;justify-content:center;font:800 62px var(--sans);color:#06202a">J</div>
        <div><div style="font:750 66px var(--sans)">Jonas</div><div class="mono-s dim" style="font-size:30px">sms · nu</div></div>`;
      ctx.inB = div('abs', cam);
      L.css(ctx.inB, { left: '70px', top: '730px', transformOrigin: '0 100%' });
      UI.bubble(ctx.inB, 'Er du ledig i aften?', 'in', 'big');
      ctx.dots = div('abs', cam);
      L.css(ctx.dots, { right: '70px', top: '1010px', transformOrigin: '100% 100%' });
      const db = UI.bubble(ctx.dots, '<span class="d">●</span> <span class="d">●</span> <span class="d">●</span>', 'ghost', 'big');
      db.style.letterSpacing = '0.1em';
      ctx.dotEls = [...db.querySelectorAll('.d')];
      ctx.outB = div('abs', cam);
      L.css(ctx.outB, { right: '70px', top: '960px', transformOrigin: '100% 100%', textAlign: 'right' });
      ctx.tag = div('', ctx.outB, `${L.icon('bolt', 30, '#00E676')}<span style="margin-left:8px">auto-reply</span>`);
      L.css(ctx.tag, { display: 'inline-flex', alignItems: 'center', font: '600 28px var(--mono)', color: '#00E676', marginBottom: '14px' });
      L.el('br', null, ctx.outB);
      UI.bubble(ctx.outB, 'Er til træning – ringer kl. 19!', 'out', 'big');
      ctx.caps = SH.caps(root, [{ at: 0, cls: 'h-l', words: [['…nogen skriver', 0, null, true], ['til dig.', 0.3, 'green']] }]);
      ctx.chip = SH.trigChip(root);
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.25) SH.pushIn(ctx.root, seg(lb, -0.25, 0.25), -1, 'rep');
      const a = seg(lb, 0, 0.25);
      tf(ctx.inB, { s: lerp(0.4, 1, L.outBack(a, 2.2)), o: seg(lb, 0, 0.05) });
      const typing = lb >= 0.45 && lb < 1.0;
      L.show(ctx.dots, typing);
      if (typing) {
        tf(ctx.dots, { s: L.outBack(seg(lb, 0.45, 0.6), 2) });
        ctx.dotEls.forEach((d, i) => (d.style.opacity = (0.35 + 0.65 * Math.max(0, Math.sin((lb * 8 - i * 0.8) * Math.PI))).toFixed(2)));
      }
      const b = seg(lb, 1, 1.25);
      tf(ctx.outB, { s: lerp(0.4, 1, L.outBack(b, 2.2)), o: seg(lb, 1, 1.05) });
      ctx.caps(lb);
      ctx.chip(lb, 2);
      if (lb > 1.75) SH.zoomThroughOut(ctx.root, seg(lb, 1.75, 2), ...SH.map(REP_PLACE, 700, 1100));
    },
  };

  // ───────────────────────────── trigLocation (32-34) ──
  // Full-bleed map in both formats. Portrait: fence in the middle; landscape: fence in the right-hand
  // column, a dark gradient from the left keeps the caption legible.
  S.trigLocation = {
    pre: 0.25,
    build(root, ctx) {
      const cam = (ctx.cam = div('layer', root));
      cam.style.background = '#0e1114';
      const ns = 'http://www.w3.org/2000/svg';
      const svg = document.createElementNS(ns, 'svg');
      // street grid, river and a park, drawn rotated so the blocks are never axis-aligned
      const M = LAND
        ? { w: 2600, h: 1800, left: -340, top: -360, cx: 1300, cy: 900, x0: -300, x1: 2900, y0: -300, y1: 2100,
            river: 'M -300 1180 C 400 980, 1000 1380, 1600 1100 S 2400 900, 2900 1060', park: [1560, 240, 420, 330] }
        : { w: 1600, h: 2400, left: -260, top: -240, cx: 800, cy: 1200, x0: -200, x1: 1900, y0: -300, y1: 2700,
            river: 'M -200 1700 C 300 1500, 700 1950, 1100 1700 S 1700 1500, 1900 1650', park: [1060, 520, 340, 300] };
      svg.setAttribute('width', M.w); svg.setAttribute('height', M.h); svg.setAttribute('viewBox', `0 0 ${M.w} ${M.h}`);
      L.css(svg, { position: 'absolute', left: M.left + 'px', top: M.top + 'px' });
      const rnd = L.rng(5);
      let g = `<g transform="rotate(-14 ${M.cx} ${M.cy})">`;
      g += `<path d="${M.river}" stroke="#10283a" stroke-width="90" fill="none"/>`;
      g += `<rect x="${M.park[0]}" y="${M.park[1]}" width="${M.park[2]}" height="${M.park[3]}" rx="40" fill="#10251a"/>`;
      for (let x = M.x0; x < M.x1; x += 150 + Math.floor(rnd() * 60)) {
        g += `<line x1="${x}" y1="${M.y0}" x2="${x}" y2="${M.y1}" stroke="#1d232b" stroke-width="${rnd() < 0.25 ? 26 : 12}"/>`;
      }
      for (let y = M.y0; y < M.y1; y += 150 + Math.floor(rnd() * 70)) {
        g += `<line x1="${M.x0 - 100}" y1="${y}" x2="${M.x1}" y2="${y}" stroke="#1d232b" stroke-width="${rnd() < 0.25 ? 26 : 12}"/>`;
      }
      g += '</g>';
      svg.innerHTML = g;
      cam.appendChild(svg);
      const C = (ctx.C = LAND ? { x: 1340, y: 540, r: 260 } : { x: 520, y: 1030, r: 250 });
      ctx.fence = div('abs', cam);
      L.css(ctx.fence, { left: C.x - C.r + 'px', top: C.y - C.r + 'px', width: 2 * C.r + 'px', height: 2 * C.r + 'px', borderRadius: '50%',
        border: '6px solid #00E676', background: 'rgba(0,230,118,.10)' });
      ctx.pings = [0, 1].map(() => {
        const p = div('abs', cam);
        L.css(p, { left: C.x - C.r + 'px', top: C.y - C.r + 'px', width: 2 * C.r + 'px', height: 2 * C.r + 'px', borderRadius: '50%',
          border: '4px solid rgba(0,230,118,.9)' });
        return p;
      });
      ctx.label = div('abs', cam, `${L.icon('my_location', 30, '#00E676')}<span style="margin-left:10px">Kontor · 150 m</span>`);
      L.css(ctx.label, { left: C.x - 190 + 'px', top: C.y - C.r - 86 + 'px', display: 'flex', alignItems: 'center', padding: '12px 22px',
        borderRadius: '30px', background: 'rgba(10,14,12,.85)', border: '2px solid rgba(0,230,118,.5)', font: '600 30px var(--mono)', color: '#e8e8e8' });
      ctx.pin = div('abs', cam, L.icon('location_on', 120, '#FF9800'));
      L.css(ctx.pin, { left: C.x - 60 + 'px', top: C.y - 112 + 'px', filter: 'drop-shadow(0 10px 18px rgba(0,0,0,.6))', transformOrigin: '50% 100%' });
      ctx.me = div('abs', cam);
      L.css(ctx.me, { width: '44px', height: '44px', marginLeft: '-22px', marginTop: '-22px', borderRadius: '50%', background: '#fff',
        border: '8px solid #448AFF', boxShadow: '0 0 0 18px rgba(68,138,255,.25), 0 0 40px rgba(68,138,255,.8)' });
      ctx.msg = div('abs', cam);
      L.css(ctx.msg, { transformOrigin: '100% 100%' });
      UI.bubble(ctx.msg, 'På vej hjem nu!', 'out');
      ctx.scrim = div('layer', root);
      ctx.scrim.style.background = LAND
        ? 'linear-gradient(to right, rgba(13,13,13,.94) 0%, rgba(13,13,13,.8) 36%, rgba(13,13,13,0) 62%)'
        : 'linear-gradient(to bottom, rgba(13,13,13,.85), rgba(13,13,13,0) 40%)';
      ctx.caps = SH.caps(root, [{ at: 0, cls: 'h-l', words: [['…du forlader', 0, null, true], ['kontoret.', 0.3, 'green']] }]);
      ctx.chip = SH.trigChip(root);
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.3) SH.zoomThroughIn(ctx.root, seg(lb, -0.25, 0.3));
      const C = ctx.C;
      ctx.cam.style.transformOrigin = `${C.x}px ${C.y}px`;
      ctx.cam.style.transform = `scale(${(1.22 - 0.08 * seg(lb, 0, 2)).toFixed(4)}) rotate(${(-1.5 + 1.5 * seg(lb, 0, 2)).toFixed(2)}deg)`;
      tf(ctx.pin, { y: lerp(-200, 0, L.outBack(seg(lb, -0.1, 0.25), 2)), o: seg(lb, -0.1, 0) });
      ctx.pings.forEach((p, i) => {
        const k = seg(lb, i, i + 0.9);
        L.show(p, lb >= i);
        tf(p, { s: 0.2 + 1.2 * L.outCubic(k), o: 1 - k });
      });
      // walking out of the fence; crossing exactly on beat 33
      const dir = ((LAND ? 16 : 28) * Math.PI) / 180;
      const r = C.r * Math.max(0, lb);
      const mx = C.x + Math.cos(dir) * r, my = C.y + Math.sin(dir) * r;
      L.css(ctx.me, { left: mx + 'px', top: my + 'px' });
      const hit = L.decay(lb, 1, 5);
      ctx.fence.style.borderColor = lb >= 1 ? '#FFB300' : '#00E676';
      ctx.fence.style.boxShadow = `0 0 ${(90 * hit).toFixed(0)}px rgba(255,179,0,${(0.8 * hit).toFixed(2)}), inset 0 0 ${(60 * hit).toFixed(0)}px rgba(255,179,0,${(0.5 * hit).toFixed(2)})`;
      L.css(ctx.msg, { left: mx - 400 + 'px', top: my - 215 + 'px' });
      const m = seg(lb, 1.15, 1.4);
      tf(ctx.msg, { s: lerp(0.3, 1, L.outBack(m, 2.2)), o: seg(lb, 1.15, 1.2) });
      FX.flash = Math.max(FX.flash, 0.12 * hit);
      ctx.caps(lb);
      ctx.chip(lb, 3);
    },
  };

  // ───────────────────────────── trigMissed (34-36) ──
  const MIS_PLACE = SH.place(540, 1040, 0.92);
  S.trigMissed = {
    build(root, ctx) {
      darkBg(root, '#FF4081');
      const cam = (ctx.cam = div('layer', SH.vis(root, MIS_PLACE)));
      const card = (ctx.card = div('abs', cam));
      L.css(card, { left: 0, right: 0, top: '600px', height: '900px' });
      ctx.waves = [0, 1, 2].map(() => {
        const w = div('abs', card);
        L.css(w, { left: '400px', top: '60px', width: '280px', height: '280px', borderRadius: '50%', border: '4px solid rgba(255,64,129,.7)' });
        return w;
      });
      ctx.av = div('abs', card, 'M');
      L.css(ctx.av, { left: '400px', top: '60px', width: '280px', height: '280px', borderRadius: '50%', display: 'flex', alignItems: 'center',
        justifyContent: 'center', font: '800 140px var(--sans)', color: '#fff', background: 'linear-gradient(140deg,#FF4081,#7C4DFF)',
        boxShadow: '0 30px 80px rgba(0,0,0,.5)' });
      ctx.name = div('abs h-m', card, 'Mor');
      L.css(ctx.name, { left: 0, right: 0, top: '380px', textAlign: 'center' });
      ctx.status = div('abs mono-m', card, '');
      L.css(ctx.status, { left: 0, right: 0, top: '490px', textAlign: 'center', display: 'flex', justifyContent: 'center', alignItems: 'center', gap: '14px' });
      ctx.btns = div('abs', card);
      L.css(ctx.btns, { left: '210px', right: '210px', top: '640px', display: 'flex', justifyContent: 'space-between' });
      ctx.btns.innerHTML = `<div style="width:150px;height:150px;border-radius:50%;background:#FF5252;display:flex;align-items:center;justify-content:center">${L.icon('call_end', 76, '#fff')}</div>
        <div style="width:150px;height:150px;border-radius:50%;background:#00C853;display:flex;align-items:center;justify-content:center">${L.icon('call', 72, '#fff')}</div>`;
      ctx.reply = div('abs', cam);
      L.css(ctx.reply, { right: '80px', top: '1240px', textAlign: 'right', transformOrigin: '100% 100%' });
      const tag = div('', ctx.reply, `${L.icon('bolt', 30, '#00E676')}<span style="margin-left:8px">auto · missed call</span>`);
      L.css(tag, { display: 'inline-flex', alignItems: 'center', font: '600 28px var(--mono)', color: '#00E676', marginBottom: '14px' });
      L.el('br', null, ctx.reply);
      UI.bubble(ctx.reply, 'Kan ikke tage den – ringer tilbage!', 'out');
      ctx.caps = SH.caps(root, [{ at: 0, cls: 'h-l', words: [['…du misser', 0, null, true], ['et opkald.', 0.3, 'green']] }]);
      ctx.chip = SH.trigChip(root);
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      FX.shake = Math.max(FX.shake, 0.7 * L.decay(lb, 0, 6));
      FX.flash = Math.max(FX.flash, 0.3 * L.decay(lb, 0, 12));
      const ringing = lb < 1;
      const buzz = ringing && ((lb >= 0 && lb < 0.45) || (lb >= 0.5 && lb < 0.95));
      const jx = buzz ? L.noise(lb * 90) * 12 : 0, jr = buzz ? L.noise(lb * 70 + 3) * 1.6 : 0;
      tf(ctx.card, { x: jx, r: jr });
      ctx.waves.forEach((w, i) => {
        const ph = ((lb * 2 + i / 3) % 1 + 1) % 1;
        L.show(w, ringing);
        tf(w, { s: 1 + 0.9 * ph, o: (1 - ph) * 0.9 });
      });
      ctx.av.style.filter = lb >= 1 ? `grayscale(${(0.7 * seg(lb, 1, 1.2)).toFixed(2)})` : 'none';
      ctx.status.innerHTML = ringing
        ? '<span style="color:#aaa">ringer…</span>'
        : `${L.icon('phone_missed', 44, '#FF5252')}<span style="color:#FF5252">Ubesvaret opkald</span>`;
      if (!ringing) tf(ctx.status, { s: 1 + 0.15 * L.decay(lb, 1, 8) });
      const bo = 1 - seg(lb, 1, 1.25);
      tf(ctx.btns, { o: bo, y: 40 * (1 - bo), s: ringing ? 1 + 0.04 * Math.sin(lb * Math.PI * 4) : 1 });
      const rp = seg(lb, 1.5, 1.75);
      tf(ctx.reply, { s: lerp(0.4, 1, L.outBack(rp, 2.2)), o: seg(lb, 1.5, 1.55) });
      ctx.caps(lb);
      ctx.chip(lb, 7);
    },
  };

  // ───────────────────────────── trigStates (36-38) ──
  const STA_PLACE = SH.place(540, 1070, 1.0);
  S.trigStates = {
    build(root, ctx) {
      darkBg(root, '#448AFF');
      const v = SH.vis(root, STA_PLACE);
      const items = [['bolt', '#FFB300', 'Oplader'], ['bluetooth', '#448AFF', 'Bluetooth'], ['wifi', '#42D1CA', 'Wi-Fi']];
      ctx.line = div('abs', v);
      L.css(ctx.line, { left: '220px', width: '640px', top: '1017px', height: '6px', borderRadius: '3px',
        background: 'linear-gradient(90deg,#FFB300,#448AFF,#42D1CA)', transformOrigin: '0 50%' });
      ctx.items = items.map(([ic, col, label], i) => {
        const x = 220 + i * 320;
        const t = SH.tile(v, ic, col, 250, 0.22);
        L.css(t, { left: x + 'px', top: '1020px' });
        const l = div('abs', v, label);
        L.css(l, { left: x - 200 + 'px', width: '400px', top: '1190px', textAlign: 'center', font: '700 50px var(--sans)', color: col });
        return { t, l };
      });
      ctx.caps = SH.caps(root, [{ at: 0, cls: 'h-l', words: [['…du kobler ', 0], ['til.', 0.25, 'green']] }]);
      ctx.chip = SH.trigChip(root);
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      FX.flash = Math.max(FX.flash, 0.14 * Math.max(L.decay(lb, 0, 14), L.decay(lb, 0.5, 14), L.decay(lb, 1, 14)) + 0.2 * L.decay(lb, 1.5, 12));
      const all = L.decay(lb, 1.5, 5);
      ctx.items.forEach(({ t, l }, i) => {
        const k = lb - i * 0.5;
        const p = seg(k, 0, 0.3);
        const flick = k >= 0 && k < 0.2 ? (Math.floor(k * 40) % 2 ? 0.4 : 1) : 1;
        tf(t, { s: (k < 0 ? 0 : lerp(1.7, 1, L.outQuart(p))) * (1 + 0.12 * all), o: seg(k, 0, 0.05) * flick, blur: lerp(12, 0, L.outCubic(p)) * (k >= 0 ? 1 : 0) });
        tf(l, { y: lerp(30, 0, L.outCubic(seg(k, 0.05, 0.3))), o: seg(k, 0.05, 0.2) });
      });
      tf(ctx.line, { sx: L.outCubic(seg(lb, 1.5, 1.75)), o: 0.9 });
      ctx.caps(lb);
      ctx.chip(lb, lb < 0.5 ? 4 : lb < 1 ? 5 : 6);
      if (lb > 1.75) SH.zoomThroughOut(ctx.root, seg(lb, 1.75, 2), ...SH.map(STA_PLACE, 540, 1020));
    },
  };

  // ───────────────────────────── trigOutro (38-40): tape stop + CRT off ──
  S.trigOutro = {
    pre: 0.25,
    build(root, ctx) {
      const cam = (ctx.cam = div('layer', root));
      ctx.tiles = T.map((t) => SH.tile(cam, t.icon, t.color, 130));
      ctx.k = L.kinetic(cam, 'h-l shadow-txt', [
        { text: 'Otte triggere.', at: -0.1, br: true },
        { text: 'Nul stress.', at: 1, cls: 'green' },
      ]);
      ctx.k.root.style.top = LAND ? '440px' : '850px';
      ctx.line = div('abs', root);
      L.css(ctx.line, { left: 0, right: 0, top: SH.CY - 3 + 'px', height: '6px', background: '#fff', boxShadow: '0 0 30px #fff, 0 0 80px #9fffd0' });
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.3) SH.zoomThroughIn(ctx.root, seg(lb, -0.25, 0.3));
      // tape stop: time itself slows down over the last beat
      const slow = seg(lb, 1.25, 2);
      const tt = lb < 1.25 ? lb : 1.25 + 0.75 * (1 - Math.pow(1 - slow, 2)) * 0.5;
      const conv = L.inCubic(seg(tt, 1.2, 1.6));
      ctx.tiles.forEach((t, i) => {
        const a = ((-90 + i * 45 + 30 * tt) * Math.PI) / 180;
        const k = seg(tt, -0.22 + i * 0.03, 0.05 + i * 0.03);
        const R = lerp(lerp(640, 430, L.outCubic(k)), 0, conv);
        const RX = LAND ? 1.65 : 1, RY = LAND ? 0.82 : 1;
        L.css(t, { left: SH.CX + Math.cos(a) * R * RX + 'px', top: SH.CY + Math.sin(a) * R * 1.08 * RY + 'px' });
        tf(t, { s: L.outBack(k, 1.8) * (1 - 0.8 * conv), o: seg(k, 0, 0.2) * (1 - conv) });
      });
      ctx.k.render(tt);
      if (lb > 1.2) {
        FX.pulse = false;
        FX.worldFilter += ` saturate(${(1 - 0.8 * slow).toFixed(2)})`;
      }
      // CRT power-off: squash to a line, then to a dot
      const a1 = seg(lb, 1.7, 1.82), a2 = seg(lb, 1.82, 1.96);
      L.show(ctx.line, a1 > 0 && lb < 2);
      if (a1 > 0) {
        ctx.cam.style.transformOrigin = `${SH.CX}px ${SH.CY}px`;
        ctx.cam.style.transform = `scaleY(${Math.max(0.004, 1 - L.inCubic(a1)).toFixed(4)})`;
        ctx.cam.style.filter = `brightness(${(1 + 3 * a1).toFixed(2)})`;
        tf(ctx.line, { sx: Math.max(0.002, 1 - L.inQuart(a2)), sy: 1 + 2 * a1, o: a1 * (1 - seg(lb, 1.96, 2)) });
      }
      FX.dark = Math.max(FX.dark, 0.85 * a2);
    },
  };
})();
