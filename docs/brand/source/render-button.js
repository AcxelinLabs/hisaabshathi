// Draws docs/brand/new-button.png (the README's download button) for a version:
//   node docs/brand/source/render-button.js 0.9.13
// Needs Playwright with Chromium (npm i playwright; npx playwright install chromium).
const { chromium } = require('playwright');
const path = require('path');
(async () => {
  const version = process.argv[2];
  if (!version) { console.error('usage: render-button.js <version>'); process.exit(1); }
  const b = await chromium.launch();
  const p = await b.newPage({ deviceScaleFactor: 2, viewport: { width: 600, height: 200 } });
  await p.goto('file://' + path.join(__dirname, 'download-button.html') + '?v=' + encodeURIComponent(version));
  await p.evaluate(() => document.fonts.ready);
  await p.locator('#button').screenshot({ path: path.join(__dirname, '..', 'new-button.png'), omitBackground: true });
  await b.close();
  console.log('docs/brand/new-button.png: v' + version);
})();
