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
    gsap.to('.hero-ghost', {y: 120 * D, ease: 'none', scrollTrigger: {trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true}});
    gsap.to('.hero-orbit', {y: -160 * D, scale: .8, ease: 'none', scrollTrigger: {trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true}});
    gsap.to('.hero-copy', {y: -90 * D, opacity: .1, ease: 'none', scrollTrigger: {trigger: '.hero', start: 'top top', end: 'bottom top', scrub: true}});
    document.querySelectorAll('[data-words]').forEach(h => SplitText.create(h, {type: 'words', mask: 'words', autoSplit: true, onSplit: self => gsap.from(self.words, {yPercent: 115, stagger: .05, ease: 'none', scrollTrigger: {trigger: h, start: 'top 90%', end: 'top 55%', scrub: true}})}));
    gsap.from('#tgrid .tcard', {y: 80 * D, opacity: 0, stagger: .1, ease: 'none', scrollTrigger: {trigger: '#tgrid', start: 'top 92%', end: 'top 50%', scrub: true}});
    document.querySelectorAll('[data-rise]').forEach(el => gsap.from(el, {y: 70 * D, opacity: 0, ease: 'none', scrollTrigger: {trigger: el, start: 'top 95%', end: 'top 65%', scrub: true}}));
    gsap.fromTo('#panel', {clipPath: 'inset(12% 8% 12% 8% round 64px)'}, {clipPath: 'inset(0% 0% 0% 0% round 40px)', ease: 'none', scrollTrigger: {trigger: '#panel', start: 'top 95%', end: 'top 45%', scrub: true}});
  });

  SplitText.create('#h1', {type: 'words', mask: 'words', onSplit: self => gsap.from(self.words, {yPercent: 110, duration: 1.1, ease: 'power4.out', stagger: .07, delay: .1})});
  gsap.from(['.hero .kicker', '.hero .lede', '.hero .cta-row', '.hero .jump'], {y: 24, opacity: 0, duration: .9, ease: 'power3.out', stagger: .08, delay: .4});
})();
