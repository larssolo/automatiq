/* Shared, dependency-free behaviour for every page. */
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const DA = document.documentElement.lang === 'da';

  // nav gets a solid background once the page has scrolled
  const nav = $('.nav');
  if (nav) { const set = () => nav.classList.toggle('solid', scrollY > 24); set(); addEventListener('scroll', set, {passive: true}); }

  // mobile menu: the burger opens the nav as a panel; a link, Esc or a wider screen closes it
  const mb = $('.menu-btn'), mn = $('#mnav');
  if (nav && mb && mn) {
    const open = on => { mn.hidden = !on; mb.setAttribute('aria-expanded', on); nav.classList.toggle('open', on); const l = mb.getAttribute('aria-label'); mb.setAttribute('aria-label', mb.dataset.close); mb.dataset.close = l; };
    mb.addEventListener('click', () => open(mn.hidden));
    mn.addEventListener('click', e => { if (e.target.closest('a')) open(false); });
    addEventListener('keydown', e => { if (e.key === 'Escape' && !mn.hidden) { open(false); mb.focus(); } });
    matchMedia('(min-width:901px)').addEventListener('change', e => { if (e.matches && !mn.hidden) open(false); });
  }

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

  // copy a block of text (creators page)
  document.querySelectorAll('[data-copy-from]').forEach(b => b.addEventListener('click', async () => {
    const label = b.querySelector('span'), text = document.getElementById(b.dataset.copyFrom).textContent;
    try { await navigator.clipboard.writeText(text); label.textContent = DA ? 'Kopieret ✓' : 'Copied ✓'; } catch { return; }
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
  // thank-you pages: a bad confirmation link swaps in the error copy
  if (/[?&](error|fejl)=1/.test(location.search)) document.querySelectorAll('[data-bad]').forEach(el => { el.innerHTML = el.dataset.bad; });

  // early-bird signup: every [data-signup] link opens the popup; the form posts to /api/subscribe
  const eb = document.getElementById('early-bird');
  if (eb && eb.showModal) {
    const form = eb.querySelector('form'), ok = eb.querySelector('.su-ok'), err = eb.querySelector('.su-err'), go = form.querySelector('[type=submit]');
    const M = JSON.parse(eb.dataset.msg); let opened = 0, from = null;
    const open = el => { from = el; opened = Date.now(); form.hidden = false; ok.hidden = true; err.hidden = true; go.disabled = false; go.textContent = M.go; if (!eb.open) eb.showModal(); };
    document.querySelectorAll('[data-signup]').forEach(a => a.addEventListener('click', e => { e.preventDefault(); const m = document.getElementById('mnav'); if (m && !m.hidden) m.hidden = true; open(a); }));
    if (location.hash === '#early-bird') open(null);
    eb.querySelector('.lb-close').addEventListener('click', () => eb.close());
    eb.querySelector('[data-su-close]').addEventListener('click', () => eb.close());
    eb.addEventListener('click', e => { if (e.target === eb) eb.close(); });
    eb.addEventListener('close', () => { if (location.hash === '#early-bird') history.replaceState(null, '', location.pathname + location.search); if (from && from.focus) from.focus(); });
    form.addEventListener('submit', async e => {
      e.preventDefault(); err.hidden = true;
      const f = new FormData(form), email = String(f.get('email')).trim();
      if (!String(f.get('name')).trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email) || !f.get('consent')) { err.textContent = M.bad; err.hidden = false; return; }
      go.disabled = true; go.textContent = M.busy;
      try {
        const r = await fetch('/api/subscribe', {method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify({name: f.get('name'), email, website: f.get('website'), consent: true, lang: eb.dataset.lang, t: opened})});
        if (!r.ok) throw 0;
        form.hidden = true; ok.hidden = false; ok.focus(); form.reset();
      } catch { err.textContent = M.err; err.hidden = false; go.disabled = false; go.textContent = M.go; }
    });
  }
})();
