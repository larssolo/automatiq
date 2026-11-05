// Renders a film page frame by frame. Usage: node render.js <outDir> [from] [to] [step] [page, default film.html]
// Serve the repo root on :8766 first (python3 -m http.server 8766).
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [out, from = 0, to, step = 1, page = 'film.html'] = process.argv.slice(2);
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const p = await b.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'error' && errs.push(m.text()));
  await p.goto('http://localhost:8766/marketing/film/' + page);
  await p.waitForFunction(() => window.READY, null, { timeout: 60000 }).catch(() => {});
  if (errs.length) { console.log(errs.join('\n')); }
  const film = await p.evaluate(() => window.FILM);
  fs.writeFileSync(out + '/cues.json', JSON.stringify(film.CUES));
  const last = to !== undefined && to !== '-' ? +to : Math.ceil(film.END * film.FPS);
  for (let f = +from; f <= last; f += +step) {
    await p.evaluate(t => window.renderAt(t), f / film.FPS);
    await p.screenshot({ path: `${out}/f${String(f).padStart(5, '0')}.jpg`, type: 'jpeg', quality: 92 });
    if (f % 150 === 0) console.log('frame', f, '/', last);
  }
  console.log(errs.length ? 'errors: ' + errs.length : 'done');
  await b.close();
})();
