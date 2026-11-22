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
  // early bird: a popup glitches in after load, stays 4 s, then folds into the "Early Bird" nav chip; any [data-eb] opens the how-to
  const pop = document.getElementById('ebPop'), dlg = document.getElementById('ebDlg'), chip = document.getElementById('ebChip');
  if (pop && dlg && chip && dlg.showModal) {
    if (Date.now() > Date.UTC(2026, 11, 31, 22, 0)) { pop.remove(); chip.remove(); document.querySelectorAll('.eb-link').forEach(l => l.remove()); }
    else {
      let seen = false; try { seen = !!sessionStorage.getItem('eb'); } catch {}
      let folded = false, timer = 0;
      const fold = () => {
        if (folded) return; folded = true; clearTimeout(timer); try { sessionStorage.setItem('eb', '1'); } catch {}
        const show = () => { pop.hidden = true; chip.classList.add('on'); if (!reduce) chip.animate([{transform: 'scale(1.35)', boxShadow: '0 0 0 0 rgba(0,230,118,.7)'}, {transform: 'scale(1)', boxShadow: '0 0 0 14px rgba(0,230,118,0)'}], {duration: 900, easing: 'ease-out'}); };
        if (reduce || pop.hidden) return show();
        const a = pop.getBoundingClientRect(), c = chip.getBoundingClientRect();
        const dx = c.left + c.width / 2 - (a.left + a.width / 2), dy = c.top + c.height / 2 - (a.top + a.height / 2), k = Math.max(.08, c.width / a.width);
        pop.classList.remove('glitch');
        pop.animate([{transform: 'none', opacity: 1}, {transform: `translate(${dx}px,${dy}px) scale(${k})`, opacity: 0}], {duration: 650, easing: 'cubic-bezier(.6,0,.3,1)', fill: 'forwards'}).onfinish = show;
      };
      const popIn = () => { pop.hidden = false; pop.classList.add('glitch'); timer = setTimeout(fold, 4000); };
      if (seen || reduce) chip.classList.add('on');
      else { const go = () => setTimeout(popIn, 700); document.readyState === 'complete' ? go() : addEventListener('load', go, {once: true}); }
      let from = null;
      document.querySelectorAll('[data-eb]').forEach(el => el.addEventListener('click', e => {
        e.preventDefault(); from = el; const m = document.getElementById('mnav'); if (m && !m.hidden) m.hidden = true;
        fold(); dlg.showModal();
      }));
      dlg.querySelector('.lb-close').addEventListener('click', () => dlg.close());
      dlg.addEventListener('click', e => { if (e.target === dlg) dlg.close(); });
      dlg.addEventListener('close', () => { if (from && from.isConnected && from.offsetParent) from.focus(); else chip.focus(); });
    }
  }
})();
