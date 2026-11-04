/* Shared, dependency-free behaviour for every page. */
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

  // nav gets a solid background once the page has scrolled
  const nav = $('.nav');
  if (nav) { const set = () => nav.classList.toggle('solid', scrollY > 24); set(); addEventListener('scroll', set, {passive: true}); }

  // step timelines end exactly in the last step's number
  const steps = [...document.querySelectorAll('.steps')];
  const fitSteps = () => steps.forEach(ol => { const li = ol.lastElementChild; if (!li) return; const pad = parseFloat(getComputedStyle(li).paddingTop), n = parseFloat(getComputedStyle(li, '::before').height) || 48; ol.style.setProperty('--end', (ol.offsetHeight - li.offsetTop - pad - n / 2) + 'px'); });
  if (steps.length) { fitSteps(); addEventListener('resize', fitSteps); document.fonts && document.fonts.ready.then(fitSteps); }

  // intro video: load and play only while visible; never autoplay under reduced motion
  const vid = $('video[data-lazy]');
  if (vid) {
    if (reduce) { vid.controls = true; vid.removeAttribute('autoplay'); }
    else new IntersectionObserver(([e]) => {
      if (e.isIntersecting) { if (vid.preload === 'none') { vid.preload = 'auto'; vid.load(); } vid.play().catch(() => { vid.controls = true; }); }
      else vid.pause();
    }, {threshold: .35}).observe(vid);
  }

  // AI variation demo
  const rw = $('#rw');
  if (rw) {
    const V = ['Good morning! ☀️ Have a lovely Wednesday.', 'Morning, Mom! ☀️ Wishing you a calm and happy Wednesday.', 'Rise and shine ☀️ Hope your Wednesday is off to a great start!', 'Good morning! ☀️ Sending a little sunshine for your Wednesday.'];
    let i = 0, timer = null; const n = $('#rwn');
    const next = () => {
      i = (i + 1) % V.length; n.textContent = `variant ${i + 1} / ${V.length}`;
      if (reduce) { rw.textContent = V[i]; return; }
      rw.animate([{opacity: 1}, {opacity: 0}], {duration: 220, fill: 'forwards'}).onfinish = () => { rw.textContent = V[i]; rw.animate([{opacity: 0, transform: 'translateY(6px)'}, {opacity: 1, transform: 'none'}], {duration: 380, easing: 'cubic-bezier(.2,.8,.2,1)', fill: 'forwards'}); };
    };
    new IntersectionObserver(([e]) => { clearInterval(timer); if (e.isIntersecting && !reduce) timer = setInterval(next, 2800); }, {threshold: .4}).observe(rw);
    const notes = {segA: 'A notification lets you review, edit or discard the text before it goes out.', segB: 'The text goes out on its own, and a notification shows exactly what was sent.'};
    ['segA', 'segB'].forEach(id => $('#' + id)?.addEventListener('click', () => {
      ['segA', 'segB'].forEach(o => $('#' + o).setAttribute('aria-pressed', String(o === id)));
      $('#segNote').textContent = notes[id];
    }));
  }
})();
