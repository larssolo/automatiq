/* Shared, dependency-free behaviour for every page. */
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const DA = document.documentElement.lang === 'da';

  // nav gets a solid background once the page has scrolled
  const nav = $('.nav');
  if (nav) { const set = () => nav.classList.toggle('solid', scrollY > 24); set(); addEventListener('scroll', set, {passive: true}); }

  // step timelines end exactly in the last step's number
  const steps = [...document.querySelectorAll('.steps')];
  const fitSteps = () => steps.forEach(ol => { const li = ol.lastElementChild; if (!li) return; const pad = parseFloat(getComputedStyle(li).paddingTop), n = parseFloat(getComputedStyle(li, '::before').height) || 48; ol.style.setProperty('--end', (ol.offsetHeight - li.offsetTop - pad - n / 2) + 'px'); });
  if (steps.length) { fitSteps(); addEventListener('resize', fitSteps); document.fonts && document.fonts.ready.then(fitSteps); }

  // sharing: native share sheet where available, otherwise the plain links; copy-link with feedback
  document.querySelectorAll('[data-share]').forEach(b => {
    if (!navigator.share) return;
    b.hidden = false;
    b.addEventListener('click', () => navigator.share({title: document.title, text: b.dataset.text, url: b.dataset.share}).catch(() => {}));
  });
  document.querySelectorAll('[data-copy]').forEach(b => b.addEventListener('click', async () => {
    const label = b.querySelector('span');
    try { await navigator.clipboard.writeText(b.dataset.copy); label.textContent = DA ? 'Link kopieret ✓' : 'Link copied ✓'; }
    catch { label.textContent = b.dataset.copy; }
    b.classList.add('copied'); setTimeout(() => { label.textContent = b.dataset.label; b.classList.remove('copied'); }, 2200);
  }));

  // film: our play button starts it with sound; native controls take over after that
  const film = $('#filmVideo');
  if (film) {
    const frame = film.parentElement;
    frame.querySelector('.film-play').addEventListener('click', () => { film.play().catch(() => {}); });
    film.addEventListener('play', () => frame.classList.add('playing'));
    film.addEventListener('pause', () => { if (!film.seeking) frame.classList.remove('playing'); });
    film.addEventListener('ended', () => frame.classList.remove('playing'));
  }

  // lightbox videos: a thumbnail opens a <dialog> and plays with sound; Esc, ×, or a click outside closes and pauses
  document.querySelectorAll('[data-lightbox]').forEach(btn => {
    const dlg = document.getElementById(btn.dataset.lightbox); if (!dlg || !dlg.showModal) return;
    const v = dlg.querySelector('video');
    btn.addEventListener('click', () => { dlg.showModal(); v.play().catch(() => {}); });
    dlg.querySelector('.lb-close').addEventListener('click', () => dlg.close());
    dlg.addEventListener('click', e => { if (e.target === dlg) dlg.close(); });
    dlg.addEventListener('close', () => { v.pause(); btn.focus(); });
  });

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
    const V = DA ? ['Godmorgen! ☀️ Hav en dejlig onsdag.', 'Godmorgen, mor! ☀️ Jeg håber, du får en rolig og glad onsdag.', 'Op med humøret ☀️ Håber din onsdag er kommet godt fra start!', 'Godmorgen! ☀️ Her er lidt solskin til din onsdag.']
      : ['Good morning! ☀️ Have a lovely Wednesday.', 'Morning, Mom! ☀️ Wishing you a calm and happy Wednesday.', 'Rise and shine ☀️ Hope your Wednesday is off to a great start!', 'Good morning! ☀️ Sending a little sunshine for your Wednesday.'];
    let i = 0, timer = null; const n = $('#rwn');
    const next = () => {
      i = (i + 1) % V.length; n.textContent = `variant ${i + 1} / ${V.length}`;
      if (reduce) { rw.textContent = V[i]; return; }
      rw.animate([{opacity: 1}, {opacity: 0}], {duration: 220, fill: 'forwards'}).onfinish = () => { rw.textContent = V[i]; rw.animate([{opacity: 0, transform: 'translateY(6px)'}, {opacity: 1, transform: 'none'}], {duration: 380, easing: 'cubic-bezier(.2,.8,.2,1)', fill: 'forwards'}); };
    };
    new IntersectionObserver(([e]) => { clearInterval(timer); if (e.isIntersecting && !reduce) timer = setInterval(next, 2800); }, {threshold: .4}).observe(rw);
    const notes = DA ? {segA: 'En notifikation lader dig gennemse, rette eller kassere teksten, før den sendes.', segB: 'Teksten sendes af sig selv, og en notifikation viser præcis, hvad der blev sendt.'}
      : {segA: 'A notification lets you review, edit or discard the text before it goes out.', segB: 'The text goes out on its own, and a notification shows exactly what was sent.'};
    ['segA', 'segB'].forEach(id => $('#' + id)?.addEventListener('click', () => {
      ['segA', 'segB'].forEach(o => $('#' + o).setAttribute('aria-pressed', String(o === id)));
      $('#segNote').textContent = notes[id];
    }));
  }
})();
