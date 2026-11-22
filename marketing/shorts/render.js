// Renders one short frame by frame. Usage: node render.js <clipId> <outDir> [from] [to] [step]
// Serve the repo root on :8766 first (python3 -m http.server 8766).
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const [id, out, from = 0, to, step = 1] = process.argv.slice(2);
  fs.mkdirSync(out, { recursive: true });
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  const errs = []; p.on('pageerror', e => errs.push(e.message)); p.on('console', m => m.type() === 'error' && errs.push(m.text()));
  await p.goto(`http://localhost:8766/marketing/shorts/clip.html?c=${id}`);
  await p.waitForFunction(() => window.READY, null, { timeout: 60000 }).catch(() => {});
  if (errs.length) console.log(errs.join('\n'));
  const film = await p.evaluate(() => window.FILM);
  if (!film) { console.log('page did not start'); process.exit(1); }
  fs.writeFileSync(out + '/cues.json', JSON.stringify(film.CUES));
  fs.writeFileSync(out + '/meta.json', JSON.stringify({ end: film.END, fps: film.FPS }));
  const last = to !== undefined && to !== '-' ? +to : Math.ceil(film.END * film.FPS);
  for (let f = +from; f <= last; f += +step) {
    await p.evaluate(t => window.renderAt(t), f / film.FPS);
    await p.screenshot({ path: `${out}/f${String(f).padStart(5, '0')}.jpg`, type: 'jpeg', quality: 90 });
    if (f % 150 === 0) console.log(id, 'frame', f, '/', last);
  }
  console.log(id, errs.length ? 'errors: ' + errs.length : 'done');
  await b.close();
})();
