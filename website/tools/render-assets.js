// Renders PNG icons and the Open Graph image from HTML. Run: node website/tools/render-assets.js
const { chromium } = require('playwright');
const path = require('path');
const PUB = path.join(__dirname, '..', 'public');
const fox = require('fs').readFileSync(path.join(PUB, 'favicon.svg'), 'utf8');
(async () => {
  const b = await chromium.launch();
  for (const [name, size] of [['favicon-48.png', 48], ['apple-touch-icon.png', 180], ['icon-192.png', 192], ['icon-512.png', 512]]) {
    const p = await b.newPage({ viewport: { width: size, height: size } });
    // apple touch icon gets a full-bleed square (iOS rounds it itself)
    const svg = name.startsWith('apple') ? fox.replace('rx="15"', 'rx="0"') : fox;
    await p.setContent(`<style>html,body{margin:0;background:transparent}svg{display:block;width:${size}px;height:${size}px}</style>${svg}`);
    await p.screenshot({ path: path.join(PUB, name), omitBackground: true });
    await p.close();
  }
  const p = await b.newPage({ viewport: { width: 1200, height: 630 } });
  await p.goto('file://' + path.join(__dirname, 'og.html'));
  await p.waitForTimeout(500);
  await p.screenshot({ path: path.join(PUB, 'og', 'textfox-og.png') });
  await b.close();
})();
