/* Act 3 — the AI break (beats 40-48): reveal, AI reply, approval, variations. */
(function () {
  const { div, seg, lerp, tf } = L;
  const S = window.SHOTS;

  /** 4-point sparkle star as inline SVG. */
  function star(parent, size, color) {
    const e = div('abs', parent, `<svg viewBox="-50 -50 100 100" style="width:${size}px;height:${size}px;overflow:visible">
      <path d="M0,-50 C6,-10 10,-6 50,0 C10,6 6,10 0,50 C-6,10 -10,6 -50,0 C-10,-6 -6,-10 0,-50Z" fill="${color}"/></svg>`);
    L.css(e, { marginLeft: -size / 2 + 'px', marginTop: -size / 2 + 'px', filter: `drop-shadow(0 0 ${size / 5}px ${color})` });
    return e;
  }

  function chatBg(root, tint) {
    const bg = div('layer', root);
    bg.style.background = `radial-gradient(ellipse 85% 55% at 50% 58%, ${tint}, rgba(0,0,0,0) 70%), #0e0f13`;
  }

  // ───────────────────────────── aiReveal (40-42) ──
  S.aiReveal = {
    build(root, ctx) {
      const bg = div('layer', root);
      bg.style.background = '#000';
      ctx.glow = div('abs', root);
      L.css(ctx.glow, { left: '-160px', top: '360px', width: '1400px', height: '1200px', borderRadius: '50%',
        background: 'radial-gradient(ellipse, rgba(138,125,255,.32), rgba(66,209,202,.12) 40%, rgba(0,0,0,0) 70%)' });
      ctx.seed = star(root, 120, '#ffffff');
      L.css(ctx.seed, { left: '540px', top: '960px' });
      ctx.pre = L.kinetic(root, 'mono-l dim', [{ text: 'Og så…', at: 0.25 }], { style: 'rise' });
      ctx.pre.root.style.top = '640px';
      ctx.ai = div('abs grad', root, 'AI.');
      L.css(ctx.ai, { left: 0, right: 0, top: '720px', textAlign: 'center', font: '1000 420px/1 var(--sans)', letterSpacing: '-0.06em',
        backgroundSize: '200% 100%' });
      const rnd = L.rng(41);
      ctx.sparks = Array.from({ length: 16 }, (_, i) => {
        const c = ['#ffffff', '#00E676', '#42D1CA', '#B388FF'][i % 4];
        const sz = 30 + rnd() * 70;
        const e = star(root, sz, c);
        const a = rnd() * Math.PI * 2, d = 260 + rnd() * 420;
        return { e, x: 540 + Math.cos(a) * d, y: 930 + Math.sin(a) * d * 0.8, t: 1 + rnd() * 0.5, spin: (rnd() - 0.5) * 180 };
      });
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      FX.pulse = false;
      // the CRT dot becomes a spark
      const s0 = L.outBack(seg(lb, 0, 0.3), 2);
      const s1 = seg(lb, 0.85, 1.05);
      tf(ctx.seed, { s: s0 * (1 + 0.15 * Math.sin(lb * 9)) * (1 + 6 * L.inExpo(s1)), r: 45 * lb, o: 1 - seg(lb, 1, 1.1) });
      ctx.pre.render(lb, 0.9, 0.2);
      const a = seg(lb, 1, 1.3);
      tf(ctx.ai, { s: lerp(1.6, 1, L.outQuart(a)) * (1 + 0.03 * Math.sin(lb * 4)), o: seg(lb, 1, 1.06), blur: lerp(24, 0, L.outCubic(a)) });
      ctx.ai.style.backgroundPosition = `${(100 - 100 * seg(lb, 1, 2)).toFixed(1)}% 0`;
      ctx.ai.style.filter = `drop-shadow(0 0 ${(30 + 50 * L.decay(lb, 1, 4)).toFixed(0)}px rgba(138,125,255,.55))` + (a < 1 ? ` blur(${lerp(24, 0, L.outCubic(a)).toFixed(1)}px)` : '');
      tf(ctx.glow, { o: seg(lb, 0.8, 1.2) * (0.8 + 0.2 * Math.sin(lb * 5)) });
      FX.flash = Math.max(FX.flash, 0.5 * L.decay(lb, 1, 10));
      ctx.sparks.forEach((sp) => {
        const k = seg(lb, sp.t, sp.t + 0.6);
        const on = k > 0 && k < 1;
        L.show(sp.e, on);
        if (on) {
          L.css(sp.e, { left: sp.x + 'px', top: sp.y + 'px' });
          tf(sp.e, { s: Math.sin(k * Math.PI), r: sp.spin * k });
        }
      });
      if (lb > 1.75) SH.zoomThroughOut(ctx.root, seg(lb, 1.75, 2), 540, 940);
    },
  };

  // ───────────────────────────── aiReply (42-44) ──
  S.aiReply = {
    pre: 0.25,
    build(root, ctx) {
      chatBg(root, 'rgba(138,125,255,.20)');
      const head = div('abs', root);
      L.css(head, { left: '70px', top: '520px', display: 'flex', alignItems: 'center', gap: '28px' });
      head.innerHTML = `<div style="width:124px;height:124px;border-radius:50%;background:linear-gradient(140deg,#FF4081,#7C4DFF);display:flex;align-items:center;justify-content:center;font:800 62px var(--sans);color:#fff">M</div>
        <div><div style="font:750 66px var(--sans)">Mor</div><div class="mono-s dim" style="font-size:30px">sms · nu</div></div>`;
      ctx.inB = div('abs', root);
      L.css(ctx.inB, { left: '70px', top: '730px', transformOrigin: '0 100%' });
      UI.bubble(ctx.inB, 'Kommer du til middag lørdag?', 'in', 'big');
      ctx.out = div('abs', root);
      L.css(ctx.out, { right: '70px', top: '1010px', textAlign: 'right', transformOrigin: '100% 100%' });
      ctx.tag = div('', ctx.out, `${L.icon('auto_awesome', 34, '#B388FF')}<span style="margin-left:10px">AI · Gemini</span>`);
      L.css(ctx.tag, { display: 'inline-flex', alignItems: 'center', font: '600 30px var(--mono)', color: '#B388FF', marginBottom: '16px' });
      L.el('br', null, ctx.out);
      ctx.bub = UI.bubble(ctx.out, '', 'ghost', 'big');
      L.css(ctx.bub, { minWidth: '210px', textAlign: 'left', backgroundSize: '220% 100%' });
      ctx.sparks = [0, 1, 2].map((i) => {
        const e = star(root, 46 - i * 10, ['#ffffff', '#B388FF', '#00E676'][i]);
        L.css(e, { left: [990, 760, 1030][i] + 'px', top: [1000, 980, 1190][i] + 'px' });
        return e;
      });
      ctx.caps = SH.caps(root, [{ at: 0, cls: 'h-l', words: [['AI svarer ', 0], ['for dig.', 0.3, 'grad']] }]);
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      if (lb < 0.3) SH.zoomThroughIn(ctx.root, seg(lb, -0.25, 0.3));
      FX.pulse = false;
      const a = seg(lb, 0, 0.25);
      tf(ctx.inB, { s: lerp(0.4, 1, L.outBack(a, 2.2)), o: seg(lb, 0, 0.05) });
      // thinking shimmer → the reply writes itself → it goes green when sent
      const think = seg(lb, 0.5, 0.65);
      tf(ctx.out, { s: lerp(0.5, 1, L.outBack(think, 2)), o: seg(lb, 0.5, 0.55) });
      const REPLY = 'Klart! Jeg tager dessert med.';
      if (lb < 1) {
        const dots = [0, 1, 2].map((i) => `<span style="opacity:${(0.35 + 0.65 * Math.max(0, Math.sin((lb * 8 - i * 0.8) * Math.PI))).toFixed(2)}">●</span>`);
        ctx.bub.innerHTML = dots.join(' ');
      } else {
        ctx.bub.textContent = L.typed(REPLY, seg(lb, 1, 1.7));
      }
      ctx.bub.style.backgroundPosition = `${(100 * ((lb * 0.9) % 1)).toFixed(1)}% 0`;
      const sent = seg(lb, 1.75, 1.85);
      // toggle longhands only: resetting the shorthand would also wipe the shimmer's background-size
      ctx.bub.style.backgroundImage = sent > 0 ? 'none' : '';
      ctx.bub.style.backgroundColor = sent > 0 ? '#00E676' : '';
      ctx.bub.style.color = sent > 0 ? '#04140b' : '';
      ctx.sparks.forEach((e, i) => {
        const k = seg(lb, 0.55 + i * 0.12, 1.4 + i * 0.12);
        tf(e, { s: Math.sin(k * Math.PI), r: 120 * k, o: k > 0 && k < 1 ? 1 : 0 });
      });
      ctx.caps(lb);
      // card flip into the approval shot
      if (lb > 1.75) {
        const f = L.inCubic(seg(lb, 1.75, 2));
        ctx.root.style.transformOrigin = '540px 960px';
        ctx.root.style.transform = `perspective(2200px) rotateY(${(-90 * f).toFixed(2)}deg)`;
        ctx.root.style.filter = `brightness(${(1 - 0.5 * f).toFixed(2)})`;
      }
    },
  };

  // ───────────────────────────── aiApprove (44-46) ──
  S.aiApprove = {
    pre: 0.25,
    build(root, ctx) {
      const bg = div('layer', root);
      bg.innerHTML = '<div class="app-bg" style="filter:blur(30px) brightness(.38) saturate(1.3);inset:-60px"></div>';
      ctx.n = div('notif', root);
      ctx.n.style.top = '600px';
      ctx.n.innerHTML = `<div class="top"><img src="../assets/robot.png"><span>Automatiq · nu</span></div>
        <div class="title">AI message ready: Mor</div>
        <div class="text">Klart! Jeg tager dessert med.</div>
        <div class="btns"><span class="send">SEND</span><span style="color:#ccc">DISCARD</span></div>`;
      ctx.sendBtn = ctx.n.querySelector('.send');
      ctx.done = div('abs', root, `${L.icon('check_circle', 64, '#00E676')}<span style="margin-left:18px">Sendt til Mor</span>`);
      L.css(ctx.done, { left: 0, right: 0, top: '1060px', display: 'flex', justifyContent: 'center', alignItems: 'center',
        font: '700 54px var(--sans)', color: '#00E676' });
      // the two AI sending modes, as named in the editor
      const modes = (ctx.modes = div('abs', root));
      L.css(modes, { left: '70px', right: '70px', top: '1260px', display: 'flex', flexDirection: 'column', gap: '22px' });
      ctx.mode = ['Approve before sending', 'Send automatically and notify'].map((t) => {
        const m = div('', modes, `<span class="rd"></span><span>${t}</span>`);
        L.css(m, { display: 'flex', alignItems: 'center', gap: '22px', padding: '24px 30px', borderRadius: '40px 14px 40px 14px',
          background: 'rgba(20,22,28,.82)', font: '600 38px var(--sans)', color: '#ddd', border: '2px solid rgba(255,255,255,.08)' });
        const rd = m.querySelector('.rd');
        L.css(rd, { width: '34px', height: '34px', borderRadius: '50%', border: '4px solid #888', flex: 'none' });
        return { m, rd };
      });
      ctx.touch = SH.touch(root);
      ctx.caps = SH.caps(root, [{ at: 0, cls: 'h-l', words: [['Du har det', 0, null, true], ['sidste ord.', 0.3, 'green']] }]);
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      FX.pulse = false;
      if (lb < 0.25) {
        const f = 1 - L.outCubic(seg(lb, -0.25, 0.25));
        ctx.root.style.transformOrigin = '540px 960px';
        ctx.root.style.transform = `perspective(2200px) rotateY(${(90 * f).toFixed(2)}deg)`;
      }
      const d = L.outBack(seg(lb, 0, 0.35), 1.4);
      const collapse = L.inCubic(seg(lb, 1.25, 1.5));
      tf(ctx.n, { y: lerp(-900, 0, d) - 140 * collapse, s: 1 - 0.1 * collapse, o: 1 - collapse });
      const tapped = lb >= 1;
      ctx.sendBtn.style.color = tapped ? '#04140b' : '#00E676';
      ctx.sendBtn.style.background = tapped ? '#00E676' : 'transparent';
      ctx.sendBtn.style.padding = '6px 18px';
      ctx.sendBtn.style.margin = '-6px -18px';
      ctx.sendBtn.style.borderRadius = '18px';
      ctx.touch(lb, 1, 225, 890);
      const dn = seg(lb, 1.25, 1.5);
      tf(ctx.done, { s: lerp(0.5, 1, L.outBack(dn, 2.5)), o: dn });
      ctx.mode.forEach(({ m, rd }, i) => {
        const k = seg(lb, 0.3 + i * 0.12, 0.6 + i * 0.12);
        tf(m, { x: lerp(i ? 200 : -200, 0, L.outCubic(k)), o: k });
        const sel = i === 0 ? lb < 1.5 : lb >= 1.5;
        rd.style.borderColor = sel ? '#00E676' : '#888';
        rd.style.background = sel ? 'radial-gradient(circle, #00E676 45%, transparent 50%)' : 'transparent';
        m.style.borderColor = sel ? 'rgba(0,230,118,.55)' : 'rgba(255,255,255,.08)';
      });
      ctx.caps(lb);
      if (lb > 1.75) SH.zoomThroughOut(ctx.root, seg(lb, 1.75, 2), 540, 1000);
    },
  };

  // ───────────────────────────── aiVary (46-48) ──
  const VARIANTS = [
    ['man', "Morn skat – ha' en fantastisk dag!", -4],
    ['tir', 'Godmorgen! Tænker på dig i dag.', 3],
    ['ons', "Hej smukke, ha' en super dag!", -2],
  ];
  S.aiVary = {
    pre: 0.25,
    build(root, ctx) {
      chatBg(root, 'rgba(0,230,118,.14)');
      const cam = (ctx.cam = div('layer', root));
      ctx.base = div('abs', cam);
      L.css(ctx.base, { left: '70px', right: '70px', top: '560px' });
      ctx.base.innerHTML = `<div class="mono-s" style="color:#00BCD4;margin-bottom:16px;display:flex;align-items:center;gap:12px">${L.icon('schedule', 34, '#00BCD4')}<span>07:00 · hver morgen · original</span></div>`;
      const b = UI.bubble(ctx.base, 'Godmorgen skat! Hav en god dag.', 'in', 'big');
      L.css(b, { opacity: 0.75, fontSize: '48px', whiteSpace: 'nowrap' });
      ctx.cards = VARIANTS.map(([day, text, rot], i) => {
        const c = div('abs', cam);
        L.css(c, { right: '60px', top: 880 + i * 215 + 'px', textAlign: 'right', transformOrigin: '100% 50%' });
        const tag = div('', c, `${L.icon('auto_awesome', 30, '#B388FF')}<span style="margin-left:8px">${day} · AI-variation</span>`);
        L.css(tag, { display: 'inline-flex', alignItems: 'center', font: '600 26px var(--mono)', color: '#B388FF', marginBottom: '8px' });
        L.el('br', null, c);
        const bb = UI.bubble(c, text, 'out', 'big');
        L.css(bb, { fontSize: '46px', padding: '22px 34px', maxWidth: '960px', whiteSpace: 'nowrap' });
        return { c, rot };
      });
      ctx.caps = SH.caps(root, [{ at: 0, cls: 'h-l', words: [['Ny tekst ', 0], ['hver gang.', 0.3, 'green']] }]);
    },
    render(lb, ctx) {
      SH.reset(ctx.root);
      FX.pulse = false;
      if (lb < 0.3) SH.zoomThroughIn(ctx.root, seg(lb, -0.25, 0.3));
      const rush = seg(lb, 1.5, 2);
      ctx.cards.forEach(({ c, rot }, i) => {
        const k = seg(lb, i * 0.5, i * 0.5 + 0.3);
        tf(c, { x: lerp(700, 0, L.outBack(k, 1.3)), r: lerp(rot * 3, rot, L.outCubic(k)) * (1 - rush), o: seg(k, 0, 0.1) });
      });
      // everything rushes into the lens; the gap goes silent; drop B lands on the white flash
      const z = L.inExpo(rush);
      ctx.cam.style.transformOrigin = '540px 1000px';
      ctx.cam.style.transform = `scale(${(1 + 3.5 * z).toFixed(4)})`;
      ctx.cam.style.filter = z > 0.01 ? `blur(${(14 * z).toFixed(1)}px)` : 'none';
      ctx.cam.style.opacity = (1 - seg(rush, 0.7, 1)).toFixed(3);
      FX.dark = Math.max(FX.dark, 0.6 * seg(lb, 1.5, 2));
      ctx.caps(lb);
    },
  };
})();
