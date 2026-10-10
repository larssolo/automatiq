#!/usr/bin/env node
/*
 * Renders the Automatiq promo: frames from scene/index.html (headless Chromium via Playwright),
 * soundtrack from audio/soundtrack.py, both locked to timeline.js, muxed by ffmpeg.
 *
 *   node render.cjs                      full render, 9:16 → out/automatiq-promo-60s-1080x1920.mp4
 *   node render.cjs --format 16x9        full render, 16:9 → out/automatiq-promo-60s-1920x1080.mp4
 *   node render.cjs --cut 15             the 15 s core-function cut → out/automatiq-promo-15s-<size>.mp4
 *   node render.cjs --preview 6,9.5,30   PNG stills at those seconds → build/preview/
 *   node render.cjs --sheet 0:60:1.5     contact sheet (from:to:step seconds) → build/sheet.png
 *   options: --format 9x16|16x9  --cut 15  --fps 60  --workers 4  --crf 22  --from 0 --to 60  --no-audio
 *            --keep-frames  --out name.mp4
 */
const fs = require('fs');
const path = require('path');
const http = require('http');
const { spawnSync } = require('child_process');

let chromium;
try {
  ({ chromium } = require('playwright'));
} catch {
  // fall back to a global install (npm i -g playwright)
  const g = spawnSync('npm', ['root', '-g'], { encoding: 'utf8' }).stdout.trim();
  ({ chromium } = require(path.join(g, 'playwright')));
}

const PROMO = __dirname;
const REPO = path.resolve(PROMO, '..');
const BUILD = path.join(PROMO, 'build');
const OUT = path.join(PROMO, 'out');
const CUT15 = process.argv.includes('--cut') && process.argv[process.argv.indexOf('--cut') + 1] === '15';
const TL = CUT15 ? require('./timeline-short.js') : require('./timeline.js');

const args = process.argv.slice(2);
const opt = (name, def) => {
  const i = args.indexOf('--' + name);
  if (i < 0) return def;
  const v = args[i + 1];
  return v === undefined || v.startsWith('--') ? true : v;
};
const FPS = Number(opt('fps', 60));
const WORKERS = Number(opt('workers', 4));
const SCALE = Number(opt('scale', 1));
const FORMAT = String(opt('format', '9x16')).toLowerCase();
if (!['9x16', '16x9'].includes(FORMAT)) throw new Error('--format must be 9x16 or 16x9');
const LAND = FORMAT === '16x9';
const VIEW = LAND ? { width: 1920, height: 1080 } : { width: 1080, height: 1920 };

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png',
  '.webp': 'image/webp', '.woff2': 'font/woff2', '.svg': 'image/svg+xml', '.json': 'application/json' };

function urlQuery() {
  const q = [LAND && 'f=land', CUT15 && 'cut=15'].filter(Boolean).join('&');
  return q ? '?' + q : '';
}

function serve() {
  return new Promise((resolve) => {
    const srv = http.createServer((req, res) => {
      const p = path.normalize(decodeURIComponent(req.url.split('?')[0]));
      const file = path.join(REPO, p);
      if (!file.startsWith(REPO) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
        res.writeHead(404);
        return res.end();
      }
      res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
      fs.createReadStream(file).pipe(res);
    });
    srv.listen(0, '127.0.0.1', () => resolve(srv));
  });
}

async function openPage(browser, url, scale) {
  const page = await browser.newPage({ viewport: VIEW, deviceScaleFactor: scale });
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto(url);
  await page.evaluate(() => window.sceneReady);
  if (errors.length) throw new Error('scene errors:\n' + errors.join('\n'));
  return page;
}

async function renderFrames(times, dir, type, scale) {
  fs.mkdirSync(dir, { recursive: true });
  const srv = await serve();
  const url = `http://127.0.0.1:${srv.address().port}/promo/scene/index.html${urlQuery()}`;
  const browser = await chromium.launch({ args: ['--font-render-hinting=none', '--force-color-profile=srgb'] });
  const pages = await Promise.all(Array.from({ length: Math.min(WORKERS, times.length) }, () => openPage(browser, url, scale)));
  let next = 0, done = 0;
  const t0 = Date.now();
  await Promise.all(pages.map(async (page) => {
    while (next < times.length) {
      const i = next++;
      await page.evaluate((t) => window.renderFrame(t), times[i].t);
      const file = path.join(dir, times[i].name + (type === 'png' ? '.png' : '.jpg'));
      await page.screenshot(type === 'png' ? { path: file, type: 'png' } : { path: file, type: 'jpeg', quality: 96 });
      done++;
      if (done % 120 === 0 || done === times.length) {
        const el = (Date.now() - t0) / 1000;
        process.stdout.write(`\r  frames ${done}/${times.length}  ${(done / el).toFixed(1)} fps  eta ${Math.round((times.length - done) / (done / el))}s   `);
      }
    }
  }));
  process.stdout.write('\n');
  await browser.close();
  srv.close();
}

function run(cmd, a, opts = {}) {
  const r = spawnSync(cmd, a, Object.assign({ stdio: 'inherit' }, opts));
  if (r.status !== 0) throw new Error(`${cmd} failed (${r.status})`);
}

(async () => {
  fs.mkdirSync(BUILD, { recursive: true });
  fs.writeFileSync(path.join(BUILD, 'timeline.json'), JSON.stringify(TL, null, 1));

  if (opt('preview')) {
    const ts = String(opt('preview')).split(',').map(Number);
    await renderFrames(ts.map((t) => ({ t, name: 't' + t.toFixed(3).padStart(7, '0') })), path.join(BUILD, 'preview'), 'png', SCALE);
    console.log('previews in', path.join(BUILD, 'preview'));
    return;
  }
  if (opt('sheet')) {
    const [a, b, st] = String(opt('sheet')).split(':').map(Number);
    const dir = path.join(BUILD, 'sheet');
    fs.rmSync(dir, { recursive: true, force: true });
    const ts = [];
    for (let t = a; t <= b + 1e-9; t += st) ts.push({ t, name: String(ts.length).padStart(4, '0') });
    await renderFrames(ts, dir, 'png', 0.25);
    const cols = Number(opt('cols', 8));
    run('ffmpeg', ['-y', '-loglevel', 'error', '-framerate', '1', '-i', path.join(dir, '%04d.png'), '-vf',
      `tile=${cols}x${Math.ceil(ts.length / cols)}:padding=6:color=0x333333`, '-frames:v', '1', path.join(BUILD, 'sheet.png')]);
    console.log('sheet:', path.join(BUILD, 'sheet.png'), `(${ts.length} frames, ${a}..${b}s step ${st})`);
    return;
  }

  // ── full render
  const from = Number(opt('from', 0)), to = Number(opt('to', TL.DURATION));
  const n = Math.round((to - from) * FPS);
  const framesDir = path.join(BUILD, 'frames');
  fs.rmSync(framesDir, { recursive: true, force: true });
  const times = Array.from({ length: n }, (_, i) => ({ t: from + i / FPS, name: String(i).padStart(5, '0') }));
  console.log(`rendering ${n} frames @ ${FPS} fps with ${WORKERS} workers`);
  await renderFrames(times, framesDir, 'jpeg', 1);

  const wav = path.join(BUILD, 'soundtrack.wav');
  const withAudio = !opt('no-audio');
  if (withAudio) run('python3', [path.join(PROMO, 'audio', 'soundtrack.py'), path.join(BUILD, 'timeline.json'), wav]);

  fs.mkdirSync(OUT, { recursive: true });
  const out = path.join(OUT, opt('out', `automatiq-promo-${CUT15 ? '15s' : '60s'}-${LAND ? '1920x1080' : '1080x1920'}.mp4`));
  const ff = ['-y', '-loglevel', 'error', '-stats', '-framerate', String(FPS), '-i', path.join(framesDir, '%05d.jpg')];
  if (withAudio) ff.push('-ss', String(from), '-t', String(to - from), '-i', wav);
  ff.push('-vf', 'scale=out_color_matrix=bt709:out_range=tv,format=yuv420p',
    '-c:v', 'libx264', '-preset', 'slow', '-crf', String(opt('crf', 22)), '-profile:v', 'high', '-level', '4.2',
    '-colorspace', 'bt709', '-color_primaries', 'bt709', '-color_trc', 'bt709', '-color_range', 'tv',
    '-g', String(FPS), '-movflags', '+faststart');
  if (withAudio) ff.push('-c:a', 'aac', '-b:a', '256k', '-ar', '48000');
  ff.push('-metadata', 'title=Automatiq — SMS på autopilot', out);
  run('ffmpeg', ff);
  if (!opt('keep-frames')) fs.rmSync(framesDir, { recursive: true, force: true });
  console.log('done:', out);
})().catch((e) => {
  console.error(e);
  process.exit(1);
});
