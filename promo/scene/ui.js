/* Builders for the app's own UI, recreated from the Compose sources so the film shows Automatiq as
 * it actually looks: leaf-cut macro cards with a breathing accent vein, the green/amber
 * ThemedSwitch, the mono "automatiq" wordmark with its pulse dot, the blob FAB and the
 * leaf-clipped bottom bar, all on the user's grainy blue-violet background. */
(function () {
  const { div, el, icon } = L;
  const UI = {};

  // Material 500 accents (util/CardColors.kt)
  UI.C = {
    red: '#F44336', pink: '#E91E63', purple: '#9C27B0', indigo: '#3F51B5', cyan: '#00BCD4',
    green: '#4CAF50', lime: '#CDDC39', orange: '#FF9800', deepOrange: '#FF5722', teal: '#009688',
  };

  UI.switch = (parent, checked) => {
    const sw = div('sw', parent);
    const th = div('th', sw);
    const api = {
      el: sw,
      /** p: 0 = off (amber, thumb left) .. 1 = on (green, thumb right). Spring-ish overshoot ok. */
      set(p) {
        const q = Math.max(0, Math.min(1, p));
        sw.style.background = mix('#FFB300', '#00E676', q);
        th.style.transform = `translateX(calc(${p * 18}  * var(--dp)))`;
      },
    };
    api.set(checked ? 1 : 0);
    return api;
  };

  UI.macroCard = (parent, m) => {
    const c = div('mcard', parent);
    c.style.setProperty('--accent', m.accent);
    const vein = div('vein', c);
    const body = div('body', c);
    const name = div('name', body, m.name);
    const sum = div('sum', body, m.summary);
    let sw = null;
    if (m.ai) div('slot', c, '<span class="ai">AI</span>');
    const slot = div('slot', c);
    sw = UI.switch(slot, m.enabled);
    div('handle', c, icon('drag_handle', '58%', 'currentColor'));
    const api = {
      el: c, veinEl: vein, body, name, sum, sw,
      /** breath 0..1 → vein alpha 0.45..1 while armed, 0.35 at rest (CardVisuals.kt rule) */
      vein(alive, breath) {
        vein.style.opacity = alive ? (0.45 + 0.55 * breath).toFixed(3) : 0.35;
        body.style.opacity = alive ? 1 : 0.5;
      },
    };
    api.vein(m.enabled, 1);
    return api;
  };

  UI.statusBar = (parent, time = '16:30') => {
    div('statusbar', parent, `<span>${time}</span><span class="icons">${icon('wifi', 'calc(15 * var(--dp))', '#f2f2f2')}${icon(
      'battery_charging_full', 'calc(15 * var(--dp))', '#f2f2f2')}</span>`);
  };

  /** Macro list screen. Returns refs for animation. */
  UI.listScreen = (screen, macros, opts = {}) => {
    div('app-bg', screen);
    div('app-dim', screen);
    UI.statusBar(screen, opts.time || '16:30');
    const row = div('wm-row', screen);
    const dot = div('pdot', row);
    div('wm', row, 'automatiq');
    const live = div('wm-live', row, '');
    div('wm-search', row, icon('search', 'calc(19 * var(--dp))', '#aaa'));
    const list = div('abs', screen);
    L.css(list, { left: 'calc(8 * var(--dp))', right: 'calc(8 * var(--dp))', top: 'calc(88 * var(--dp))' });
    const cards = macros.map((m, i) => {
      const w = div('abs', list);
      L.css(w, { left: 0, right: 0, top: `calc(${i * 84} * var(--dp))` });
      return Object.assign(UI.macroCard(w, m), { wrap: w });
    });
    const fab = div('fab', screen, icon('add', 'calc(26 * var(--dp))', '#000'));
    L.css(fab, { left: 'calc(152 * var(--dp))', bottom: 'calc(96 * var(--dp))' });
    const nav = div('navbar', screen, `
      <div class="item on"><div class="pill">${icon('bolt', 'calc(24 * var(--dp))')}</div>Macros</div>
      <div class="item"><div class="pill">${icon('history', 'calc(24 * var(--dp))')}</div>Log</div>
      <div class="item"><div class="pill">${icon('tune', 'calc(24 * var(--dp))')}</div>Settings</div>`);
    return { dot, live, cards, fab, nav, list };
  };

  UI.phone = (parent, dp = 2.1) => {
    const ph = div('phone', parent);
    ph.style.setProperty('--dp', dp + 'px');
    const screen = div('screen', ph);
    div('punch', screen);
    return { el: ph, screen, w: 372 * dp, h: 792 * dp };
  };

  UI.robot = (parent, size) => {
    const r = div('robot', parent, '<img src="../assets/robot.png" alt="">');
    L.css(r, { width: size + 'px', height: size + 'px' });
    return r;
  };

  UI.bubble = (parent, text, side = 'out', extra = '') => {
    const b = div('bubble ' + side + ' ' + extra, parent, text);
    return b;
  };

  UI.widget = (parent, name, sub, dp = 3) => {
    const w = div('widget', parent);
    w.style.setProperty('--dp', dp + 'px');
    el('div', null, w, icon('send', 'calc(22 * var(--dp))', '#00E676'));
    const col = div('', w);
    col.style.marginLeft = 'calc(10 * var(--dp))';
    const n = div('w-name', col, name);
    const s = div('w-sub', col, sub);
    return { el: w, name: n, sub: s };
  };

  // color helpers
  function hex(c) {
    const n = parseInt(c.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  function mix(a, b, p) {
    const A = hex(a), B = hex(b);
    const m = A.map((v, i) => Math.round(v + (B[i] - v) * p));
    return `rgb(${m[0]},${m[1]},${m[2]})`;
  }
  UI.mix = mix;

  window.UI = UI;
})();
