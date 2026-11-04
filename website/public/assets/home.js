window.TF = window.TF || {boom: 0};
/* Hero: the fox sends the day's texts; they join two tilted orbits and circle it. Plain JS, no libraries. */
(() => {
  const stage = document.getElementById('foxStage'), layer = document.getElementById('bubbles');
  if (!stage || !layer) return;
  const MSGS = [
    ['08:00', 'Good morning ☀️', '--amber'], ['09:00', 'Take your vitamins 💊', '--amber'], ['10:41', 'Back Monday 👋', '--teal'],
    ['11:27', 'Sorry I missed your call', '--green'], ['17:40', 'Running 10 min late!', '--rose'], ['20:15', 'Bedtime in 15 🌙', '--indigo'],
  ];
  // two rings in units of the stage width: radius x/y, tilt (rad), angular speed (rad/s)
  const RINGS = [{rx: .44, ry: .25, tilt: -9 * Math.PI / 180, w: .15}, {rx: .33, ry: .19, tilt: 13 * Math.PI / 180, w: -.2}];
  // five evenly spaced slots that circle together, alternating between the two rings, so texts never pile up
  const SLOTS = 5, W = .15, reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const live = []; let next = 0, phase = .35, size = stage.offsetWidth, cursor = 0;
  addEventListener('resize', () => { size = stage.offsetWidth; });

  function make(i) {
    const [t, txt, c] = MSGS[i % MSGS.length];
    const el = document.createElement('div');
    el.className = 'sms'; el.style.setProperty('--c', `var(${c})`);
    el.innerHTML = `<b>${txt}</b><small>${t} · <span>✓✓ delivered</span></small>`;
    layer.appendChild(el); return el;
  }
  function add(slot, born) {
    live.filter(b => b.slot === slot && !b.die).forEach(b => b.die = performance.now());
    live.push({el: make(next++), slot, ring: RINGS[0], spin: (Math.random() - .5) * 140, dir: .75 + Math.random() * .6, born, die: 0});
  }
  function place(b, now) {
    const {rx, ry, tilt} = b.ring, S = size; b.a = phase + b.slot * 2 * Math.PI / SLOTS;
    let x, y;
    if (TF.project3d) ({x, y} = TF.project3d(b.a));              // 3D orbit is live: follow its real perspective
    else {
      const nx = S < 520 ? .7 : 1;                               // narrower orbit on phones
      x = Math.cos(b.a) * rx * nx * S; y = Math.sin(b.a) * ry * S;
      [x, y] = [x * Math.cos(tilt) - y * Math.sin(tilt), x * Math.sin(tilt) + y * Math.cos(tilt)];
    }
    b.w2 = b.w2 || b.el.offsetWidth / 2; const lim = S / 2 - b.w2 * .8; x = Math.max(-lim, Math.min(lim, x));   // keep texts inside the stage
    const depth = (Math.sin(b.a) + 1) / 2;                      // 0 = behind the fox, 1 = in front
    let k = 1, o = .35 + .65 * depth;
    if (b.born) { const p = Math.min(1, (now - b.born) / 900); k = 1 - Math.pow(1 - p, 3); }   // fly out of the fox
    if (b.die) { const p = Math.min(1, (now - b.die) / 600); o *= 1 - p; if (p >= 1) { b.el.remove(); b.gone = true; } }
    const B = TF.boom, f = 1 + B * B * 3.2 * b.dir;            // scroll explosion: texts are flung outwards and spin
    const s = (.72 + .28 * depth) * (b.born ? .4 + .6 * k : 1) * (1 + B * .5);
    b.el.style.transform = `translate(-50%,-50%) translate(${(x * k * f).toFixed(1)}px,${(y * k * f - B * 60).toFixed(1)}px) rotate(${(b.spin * B).toFixed(1)}deg) scale(${s.toFixed(3)})`;
    b.el.style.opacity = (o * (b.born ? k : 1) * (1 - B * B)).toFixed(3);
    b.el.style.zIndex = depth > .5 ? 3 : 1;
    b.el.parentNode && (b.el.style.filter = depth < .35 ? 'blur(.6px)' : '');
  }
  // a small ball rides the orbit, faster along the front (bottom) than the back (top)
  const orb = document.createElement('i'); orb.className = 'orb'; layer.appendChild(orb);
  let oa = 0;
  function placeOrb(dt) {
    const {rx, ry, tilt} = RINGS[0], S = size, nx = S < 520 ? .7 : 1;
    oa += dt * (.55 + .45 * Math.sin(oa)) * 1.25;              // sin > 0 is the front half
    let x = Math.cos(oa) * rx * nx * S, y = Math.sin(oa) * ry * S;
    [x, y] = [x * Math.cos(tilt) - y * Math.sin(tilt), x * Math.sin(tilt) + y * Math.cos(tilt)];
    const depth = (Math.sin(oa) + 1) / 2, B = TF.boom;
    orb.style.transform = `translate(-50%,-50%) translate(${(x * (1 + B * 1.6)).toFixed(1)}px,${(y * (1 + B * 1.6)).toFixed(1)}px) scale(${(.6 + .5 * depth).toFixed(3)})`;
    orb.style.opacity = ((.45 + .55 * depth) * (1 - B)).toFixed(3);
    orb.style.zIndex = depth > .5 ? 3 : 1;
  }
  // start complete: five messages already in orbit
  for (let i = 0; i < SLOTS; i++) add(i, 0);
  if (reduce) { live.forEach(b => place(b, 0)); placeOrb(0); return; }

  let last = performance.now(), visible = true, timer = 0;
  new IntersectionObserver(([e]) => { visible = e.isIntersecting; }).observe(stage);
  function send() {
    stage.classList.remove('typing'); void stage.offsetWidth; stage.classList.add('typing');
    TF.onSend && TF.onSend();
    const slot = cursor++ % SLOTS;                                          // replace the oldest text
    setTimeout(() => { add(slot, performance.now()); TF.onLaunch && TF.onLaunch(); }, 1000);
  }
  (function tick(now) {
    const dt = Math.min(.05, (now - last) / 1000); last = now;
    if (visible && !document.hidden) {
      phase += W * dt; for (const b of live) place(b, now); placeOrb(dt);
      for (let i = live.length - 1; i >= 0; i--) if (live[i].gone) live.splice(i, 1);
      timer += dt; if (timer > 3.4) { timer = 0; send(); }
    }
    requestAnimationFrame(tick);
  })(last);
})();

/* Home page motion: scroll drives one day, 06:59 → 23:00. Requires gsap, ScrollTrigger, SplitText. */
(() => {
  const EVENTS = [
    {t: 480,  k: 'Scheduled',   c: '--amber',  title: 'Good morning, Mom',     desc: 'Set for 08:00 on weekdays. Gemini rewrites it every morning, so it never reads the same twice.', sms: true},
    {t: 540,  k: 'Reminder',    c: '--amber',  title: 'Pills',                 desc: 'Not every macro sends a text. This one just reminds you.', sms: false},
    {t: 641,  k: 'Auto-reply',  c: '--teal',   title: 'Out of office',         desc: 'A text came in while you were in a meeting. TextFox answered it for you.', sms: true},
    {t: 687,  k: 'Missed call', c: '--green',  title: 'Missed call text-back', desc: "You couldn't pick up. The caller got a text a few seconds later.", sms: true},
    {t: 1060, k: 'One tap',     c: '--rose',   title: 'Running late',          desc: 'One tap on the home-screen widget, and a ready-made text goes out.', sms: true},
    {t: 1215, k: 'Scheduled',   c: '--indigo', title: "Kids' bedtime",         desc: 'Every other week, Monday to Thursday. Set up once, still running.', sms: true},
    {t: 1350, k: 'Sound mode',  c: '--indigo', title: 'Quiet mode',            desc: 'At 22:30 the phone switches to Do Not Disturb. Macros can do that too.', sms: false},
  ];
  const START = 419, END = 1380;
  const $ = s => document.querySelector(s);
  const day = $('#day'); if (!day) return;
  const hh = $('#hh'), mm = $('#mm'), sclock = $('#sclock'), evk = $('#evk'), evt = $('#evt'), evd = $('#evd');
  const sentEl = $('#sent'), cAll = $('#cAll'), cOk = $('#cOk'), fill = $('#fill'), empty = $('#empty'), track = $('#track');
  const evDot = $('.ev-k');
  const cards = []; document.querySelectorAll('.lw').forEach(el => cards[+el.dataset.i] = el);

  const pct = m => ((m - 360) / 1080) * 100;
  for (let h = 6; h <= 24; h += 2) { const t = document.createElement('i'); t.className = 'tick'; t.style.left = pct(h * 60) + '%'; t.innerHTML = '<span>' + String(h).padStart(2, '0') + '</span>'; track.appendChild(t); }
  const dots = EVENTS.map(e => { const d = document.createElement('i'); d.className = 'dot on'; d.style.left = pct(e.t) + '%'; d.style.setProperty('--c', `var(${e.c})`); track.appendChild(d); return d; });

  const fmt = m => String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
  const INTRO = {k: '06:59 · before the alarm', title: 'Your phone is asleep. So are you.', desc: 'Everything that follows happens without you opening the app.', c: '--dim'};
  const OUTRO = {k: '23:00 · end of day', title: 'Five texts sent. You typed none.', desc: 'That was one ordinary day with TextFox.', c: '--green'};

  let lastMin = -1, lastIdx = -99, skyTl = null;
  function setLabel(idx, animate) {
    const e = idx < 0 ? INTRO : idx >= EVENTS.length ? OUTRO : {...EVENTS[idx], k: fmt(EVENTS[idx].t) + ' · ' + EVENTS[idx].k};
    evk.textContent = e.k; evt.textContent = e.title; evd.textContent = e.desc;
    evDot.style.color = `var(${e.c})`;
    if (animate) gsap.fromTo([evt, evd], {y: 14, opacity: 0}, {y: 0, opacity: 1, duration: .45, stagger: .05, ease: 'power3.out', overwrite: true});
  }
  function render(m, animate = true) {
    const mi = Math.round(m); if (mi === lastMin) return; lastMin = mi;
    const s = fmt(mi); const [H, M] = s.split(':');
    hh.textContent = H; mm.textContent = M; sclock.textContent = s;
    let idx = -1, sent = 0, n = 0;
    EVENTS.forEach((e, i) => { const on = mi >= e.t; cards[i].classList.toggle('on', on); dots[i].classList.toggle('on', on); if (on) { idx = i; n++; if (e.sms) sent++; } });
    if (mi >= END) idx = EVENTS.length;
    sentEl.textContent = sent; cAll.textContent = n; cOk.textContent = n;
    empty.classList.toggle('off', n > 0);
    fill.style.transform = `scaleX(${Math.max(0, (mi - 360) / 1080)})`;
    if (skyTl) skyTl.seek(Math.min(Math.max(mi - START, 0), END - START));
    if (idx !== lastIdx) { lastIdx = idx; setLabel(idx, animate); }
  }

  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (!window.gsap || !window.ScrollTrigger || reduce) return;   // the HTML already shows the complete day
  gsap.registerPlugin(ScrollTrigger, SplitText);
  ScrollTrigger.config({ignoreMobileResize: true});

  const sky = $('.sky');
  const SK = [[419, 'rgba(255,128,32,.42)', 'rgba(0,230,118,.04)'], [540, 'rgba(255,179,0,.30)', 'rgba(0,230,118,.16)'], [720, 'rgba(0,230,118,.26)', 'rgba(66,209,202,.18)'], [1040, 'rgba(255,179,0,.26)', 'rgba(66,209,202,.20)'], [1200, 'rgba(123,136,255,.26)', 'rgba(66,209,202,.12)'], [1380, 'rgba(70,80,190,.16)', 'rgba(0,230,118,.05)']];
  gsap.set(sky, {'--a': SK[0][1], '--b': SK[0][2]});
  skyTl = gsap.timeline({paused: true});
  for (let i = 1; i < SK.length; i++) skyTl.to(sky, {'--a': SK[i][1], '--b': SK[i][2], duration: SK[i][0] - SK[i - 1][0], ease: 'none'});
  render(START, false);

  // scroll progress → minute: the clock runs between events and holds at each one
  const pts = [[0, START]]; let u = 0, prev = START;
  EVENTS.forEach(e => { u += .55 + (e.t - prev) / 420; pts.push([u, e.t]); u += .9; pts.push([u, e.t]); prev = e.t; });
  u += .7; pts.push([u, END]); u += .7; pts.push([u, END]);
  pts.forEach(p => p[0] /= u);
  const minuteAt = p => { for (let i = 1; i < pts.length; i++) if (p <= pts[i][0]) { const [u0, m0] = pts[i - 1], [u1, m1] = pts[i]; return u1 === u0 ? m1 : m0 + (m1 - m0) * (p - u0) / (u1 - u0); } return END; };

  const mmq = gsap.matchMedia();
  mmq.add({mob: '(max-width: 820px)', desk: '(min-width: 821px)'}, ctx => {
    const mob = ctx.conditions.mob, D = mob ? .45 : 1, state = {p: 0};
    ScrollTrigger.create({
      trigger: day, start: 'top top', end: () => '+=' + Math.round(innerHeight * (mob ? 5 : 6.5)), pin: true, anticipatePin: 1,
      onUpdate: s => gsap.to(state, {p: s.progress, duration: .3, ease: 'power2.out', overwrite: true, onUpdate: () => render(minuteAt(state.p))}),
    });
    gsap.fromTo('.day .phone', {y: 120 * D, opacity: .3}, {y: 0, opacity: 1, ease: 'none', scrollTrigger: {trigger: day, start: 'top bottom', end: 'top top', scrub: true}});
    // the hero illustration explodes at the speed you scroll
    const boom = gsap.timeline({scrollTrigger: {trigger: '.hero', start: 'top top', end: 'bottom 20%', scrub: .4, onUpdate: s => { TF.boom = s.progress; }}});
    boom.to('.fox-wrap .fox', {scale: 2.6, rotate: -14, yPercent: -30, opacity: 0, ease: 'power2.in'}, 0)   // .fox-wrap keeps its CSS float
        .to('.fox-glow', {scale: 3.4, opacity: 0, ease: 'power1.in'}, 0)
        .to('.fox-stage .rings', {scaleX: mob ? 1.54 : 2.2, scaleY: 2.2, rotate: 25, opacity: 0, ease: 'power2.in'}, 0)
        .to('.fox-stage', {y: -60 * D, ease: 'none'}, 0);
    gsap.to('.hero-copy', {y: -90 * D, opacity: .1, ease: 'none', scrollTrigger: {trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true}});
    document.querySelectorAll('[data-words]').forEach(h => SplitText.create(h, {type: 'words', mask: 'words', autoSplit: true, onSplit: self => gsap.from(self.words, {yPercent: 115, stagger: .05, ease: 'none', scrollTrigger: {trigger: h, start: 'top 90%', end: 'top 55%', scrub: true}})}));
    gsap.fromTo('#tgrid .tcard', {y: 80 * D, autoAlpha: 0}, {y: 0, autoAlpha: 1, stagger: .1, ease: 'none', scrollTrigger: {trigger: '#tgrid', start: 'top 92%', end: 'top 50%', scrub: true}});   // hover uses `translate`, so this tween owns transform alone
    // setup timeline: the line fills and each step lights up as you scroll through it
    document.querySelectorAll('.steps').forEach(ol => {
      ol.classList.add('js');
      gsap.fromTo(ol, {'--p': 0}, {'--p': 1, ease: 'none', scrollTrigger: {trigger: ol, start: 'top 78%', end: 'bottom 72%', scrub: true}});
      ol.querySelectorAll('li').forEach(li => ScrollTrigger.create({trigger: li, start: 'top 78%', toggleClass: {targets: li, className: 'lit'}, end: 'max'}));
    });
    document.querySelectorAll('[data-rise]').forEach(el => gsap.from(el, {y: 70 * D, opacity: 0, ease: 'none', scrollTrigger: {trigger: el, start: 'top 95%', end: 'top 65%', scrub: true}}));
    gsap.fromTo('#panel', {clipPath: 'inset(12% 8% 12% 8% round 64px)'}, {clipPath: 'inset(0% 0% 0% 0% round 40px)', ease: 'none', scrollTrigger: {trigger: '#panel', start: 'top 95%', end: 'top 45%', scrub: true}});
  });

  SplitText.create('#h1', {type: 'words', mask: 'words', onSplit: self => gsap.from(self.words, {yPercent: 110, duration: 1.1, ease: 'power4.out', stagger: .07, delay: .6})});   // rises as the burst retracts
  gsap.from(['.hero .kicker', '.hero .lede', '.hero .cta-row', '.hero .jump'], {y: 24, opacity: 0, duration: .9, ease: 'power3.out', stagger: .08, delay: .85});
})();
