#!/usr/bin/env node
/*
 * bigscatter_probe.js -- v3.6.0 fix9g: large-scatter oracle (hover-latency + fidelity probe).
 *
 * Opens each sparkta HTML page (offline, self-contained) in headless Chromium and reports:
 *   load_ms     goto() -> the Chart instance exists (JSON parsed, 100k elements built)
 *   first_ms    goto() -> the first completed chart draw
 *   anim_draws  number of chart draws in the 2.5 s settle window after the first draw
 *   anim_ms     main-thread ms spent inside those draws (the load animation cost)
 *   hover_draw_ms  ms spent in the chart draw(s) that ONE dispatched mousemove over a point
 *               triggers (Chart.js throttles DOM events to requestAnimationFrame, so the
 *               hit-test + hover render land in the frames after the dispatch; 1.5 s window)
 *   hover_draws draws triggered by that mousemove; hover2_* = a second move 3 px away
 *   hover_settled_ms  ms from the dispatch to the end of the last of those draws
 *   tip         tooltip opacity read >= 400 ms after a real mouse.move onto the point
 *               (the fix8q lesson: the tooltip opacity animates ~400 ms)
 *   points      elements per dataset (chart.getDatasetMeta(i).data.length)
 *   big         datasets flagged _spkBig (fix9g large-data mode) and the plugin's bitmap state
 *   filter      for a page with a filter <select>: ms of one change + points after
 * It screenshots the FIRST canvas at rest (mouse parked off-canvas, tooltip hidden) to
 * <out>/<page>.png and, with --ref <dir>, compares that PNG pixel-for-pixel against
 * <ref>/<page>.png (same viewport / device ratio). The fix9g bitmap reproduces the pre-fix
 * render up to 8-bit compositing rounding: the translucent markers (alpha 0.85 fill,
 * 0.1 border) are composited onto a transparent bitmap and then onto the page, so a few
 * per cent of the pixels in the overlap zones differ by 1-2/255 (measured max 15/255 on
 * 100k points). Pass rule: max channel delta <= --maxd (default 16) AND differing pixels
 * <= --frac (default 0.05) of the canvas; a real regression (wrong order, size, ratio or
 * position) shows as deltas of 100+ over 15-25 per cent of the pixels.
 *
 * Usage: node verify/bigscatter_probe.js <page.html | dir> [--out <dir>] [--ref <dir>] [--dpr 1.5]
 *        [--maxd 16] [--frac 0.05]
 * Exit 1 on any JS error or a pixel mismatch outside the pass rule.
 * ASCII only. verify/ only.
 */
'use strict';
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { PNG } = require('pngjs');

const argv = process.argv.slice(2);
function opt(name, dflt) { const i = argv.indexOf(name); return i >= 0 ? argv[i + 1] : dflt; }
const target = argv.find(a => !a.startsWith('--') && a !== opt('--out') && a !== opt('--ref') && a !== opt('--dpr') && a !== opt('--maxd') && a !== opt('--frac'));
const outDir = opt('--out', path.join(path.dirname(target || '.'), '_probe'));
const refDir = opt('--ref', null);
const dpr = parseFloat(opt('--dpr', '1.5'));
const maxdTol = parseInt(opt('--maxd', '16'), 10);
const fracTol = parseFloat(opt('--frac', '0.05'));
fs.mkdirSync(outDir, { recursive: true });

const files = fs.statSync(target).isDirectory()
  ? fs.readdirSync(target).filter(f => f.endsWith('.html')).sort().map(f => path.join(target, f))
  : [target];

// Hook Chart.prototype.draw as soon as Chart.js defines window.Chart, so the load
// animation's draws are timed too (an instance patch after load would miss them).
const initScript = `
(function(){
  window.__spkDraws = [];
  var _C;
  Object.defineProperty(window, 'Chart', {
    configurable: true,
    get: function(){ return _C; },
    set: function(v){
      _C = v;
      try {
        var d = v.prototype.draw;
        v.prototype.draw = function(){ var t0 = performance.now(); var r = d.apply(this, arguments);
          window.__spkDraws.push({ t: t0, ms: performance.now() - t0 }); return r; };
        window.__spkUpdates = [];
        var u = v.prototype.update;
        v.prototype.update = function(){ var t0 = performance.now(); var r = u.apply(this, arguments);
          window.__spkUpdates.push({ t: t0, ms: performance.now() - t0 }); return r; };
      } catch (e) {}
    }
  });
})();`;

function pixelDiff(aPath, bPath) {
  const a = PNG.sync.read(fs.readFileSync(aPath)), b = PNG.sync.read(fs.readFileSync(bPath));
  if (a.width !== b.width || a.height !== b.height) return { size: `${a.width}x${a.height} vs ${b.width}x${b.height}`, diff: -1 };
  let diff = 0, maxd = 0; const hist = { '1-2': 0, '3-4': 0, '5-8': 0, '9-16': 0, '17+': 0 };
  for (let i = 0; i < a.data.length; i += 4) {
    const d = Math.max(Math.abs(a.data[i] - b.data[i]), Math.abs(a.data[i + 1] - b.data[i + 1]), Math.abs(a.data[i + 2] - b.data[i + 2]), Math.abs(a.data[i + 3] - b.data[i + 3]));
    if (d > 0) { diff++; if (d > maxd) maxd = d; hist[d <= 2 ? '1-2' : d <= 4 ? '3-4' : d <= 8 ? '5-8' : d <= 16 ? '9-16' : '17+']++; }
  }
  const total = a.width * a.height;
  return { diff, maxd, total, frac: Math.round(diff / total * 10000) / 10000, hist, pass: maxd <= maxdTol && diff / total <= fracTol };
}

(async () => {
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium-1194/chrome-linux/chrome' });
  let rc = 0;
  const rows = [];
  for (const file of files) {
    const name = path.basename(file, '.html');
    const page = await browser.newPage({ viewport: { width: 1100, height: 800 }, deviceScaleFactor: dpr });
    const errors = [];
    page.on('pageerror', e => errors.push(String(e && e.message || e)));
    page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
    await page.addInitScript(initScript);
    const t0 = Date.now();
    await page.goto('file://' + path.resolve(file), { waitUntil: 'load' });
    await page.waitForFunction(() => { const c = document.querySelector('canvas'); return !!(c && window.Chart && window.Chart.getChart && window.Chart.getChart(c)); }, null, { timeout: 60000 });
    const loadMs = Date.now() - t0;
    await page.waitForFunction(() => window.__spkDraws.length > 0, null, { timeout: 60000 });
    const firstMs = Date.now() - t0;
    await page.waitForTimeout(2500);   // let the load animation (if any) run out

    const info = await page.evaluate(() => {
      const c = document.querySelector('canvas'); const ch = Chart.getChart(c);
      const draws = window.__spkDraws;
      const points = ch.data.datasets.map((d, i) => (ch.getDatasetMeta(i).data || []).length);
      const big = ch.data.datasets.map(d => !!d._spkBig);
      const anim = ch.options.animation;
      // pick a hover target: the 500th point of dataset 0 (deterministic; inside the plot)
      const meta = ch.getDatasetMeta(0); const el = meta.data[Math.min(500, meta.data.length - 1)];
      const p = el.getProps(['x', 'y'], true); const r = c.getBoundingClientRect();
      const st = (window._spkBigState && window._spkBigState(ch)) || null;
      return { animDraws: draws.length, animMs: Math.round(draws.reduce((s, d) => s + d.ms, 0)),
               updateMs: Math.round((window.__spkUpdates || []).reduce((s, d) => s + d.ms, 0)),
               points, big, anim: anim === false ? 'off' : JSON.stringify(anim),
               target: { x: r.left + p.x, y: r.top + p.y }, bigState: st,
               normalized: ch.data.datasets.map(d => !!d.normalized) };
    });

    // ONE synchronous mousemove over the point: hit-test + hover render on the main thread
    await page.evaluate(() => { window.__spkDraws.length = 0; });
    // Chart.js throttles DOM events to requestAnimationFrame, so the hover cost lands in the
    // draw(s) that follow the dispatch: measure the 1.5 s window after it (draw count + ms).
    const hoverAt = async (dx, dy) => {
      await page.evaluate(({ x, y }) => { window.__spkDraws.length = 0; window.__spkT0 = performance.now();
        document.querySelector('canvas').dispatchEvent(new MouseEvent('mousemove', { clientX: x, clientY: y, bubbles: true })); }, { x: info.target.x + dx, y: info.target.y + dy });
      await page.waitForTimeout(1500);
      return page.evaluate(() => { const d = window.__spkDraws; return { draws: d.length, ms: Math.round(d.reduce((s, x) => s + x.ms, 0) * 10) / 10,
        first_after: d.length ? Math.round(d[0].t - window.__spkT0) : -1, last_end: d.length ? Math.round(d[d.length - 1].t + d[d.length - 1].ms - window.__spkT0) : -1 }; });
    };
    const hover = await hoverAt(0, 0);
    const hover2 = await hoverAt(3, 2);   // steady state: the pointer travelling over the cloud
    // real mouse hover -> tooltip
    await page.mouse.move(info.target.x, info.target.y, { steps: 3 });
    await page.waitForTimeout(450);
    const readTip = () => page.evaluate(() => { const ch = Chart.getChart(document.querySelector('canvas')); const t = ch.tooltip; return { op: t ? Math.round(t.opacity * 100) / 100 : -1, title: t && t.title ? t.title.join('|') : '', body: t && t.body ? t.body.map(b => b.lines.join(' ')).join('|') : '' }; });
    const tip = await readTip();
    await page.waitForTimeout(1500);
    const tipLate = await readTip();   // after a slow page has caught up
    tip.op_late = tipLate.op; if (!tip.body) tip.body = tipLate.body;
    // park the mouse off-canvas so the screenshot is at rest
    await page.mouse.move(2, 2); await page.waitForTimeout(600);

    // filter change (if the page has a filter select)
    let filt = null;
    const hasSel = await page.$('select');
    if (hasSel) {
      filt = await page.evaluate(() => {
        const sel = document.querySelector('select'); const opts = Array.from(sel.options).map(o => o.value);
        const v = opts.find(o => o !== sel.value && o !== '' && o !== '__all__') || opts[opts.length - 1];
        const t0 = performance.now(); sel.value = v; sel.dispatchEvent(new Event('change', { bubbles: true }));
        const ms = performance.now() - t0;
        const ch = Chart.getChart(document.querySelector('canvas'));
        return { value: v, ms: Math.round(ms), points: ch.data.datasets.map((d, i) => (ch.getDatasetMeta(i).data || []).length) };
      });
      await page.waitForTimeout(800);
      filt.after = await page.evaluate(() => { const ch = Chart.getChart(document.querySelector('canvas')); return { points: ch.data.datasets.map((d, i) => (ch.getDatasetMeta(i).data || []).length), bigState: (window._spkBigState && window._spkBigState(ch)) || null }; });
      // restore the first option for the screenshot
      await page.evaluate(() => { const sel = document.querySelector('select'); sel.selectedIndex = 0; sel.dispatchEvent(new Event('change', { bubbles: true })); });
      await page.waitForTimeout(1200);
    }

    const shot = path.join(outDir, name + '.png');
    const canvas = await page.$('canvas');
    await canvas.screenshot({ path: shot });
    let px = null;
    if (refDir) {
      const ref = path.join(refDir, name + '.png');
      if (fs.existsSync(ref)) { px = pixelDiff(ref, shot); if (px.diff < 0 || !px.pass) rc = 1; }
      else px = { missing: true };
    }
    if (errors.length) rc = 1;
    const row = { page: name, load_ms: loadMs, first_ms: firstMs, anim_draws: info.animDraws, anim_ms: info.animMs,
      hover_draw_ms: hover.ms, hover_draws: hover.draws, hover_settled_ms: hover.last_end, hover2_draw_ms: hover2.ms, hover2_draws: hover2.draws,
      update_ms: info.updateMs,
      tip: tip.op, tip_title: tip.title, tip_body: tip.body, points: info.points.join('+'), big: info.big.join('/'),
      normalized: info.normalized.join('/'), anim: info.anim, bigState: info.bigState, filter: filt, pixels: px, errors };
    rows.push(row);
    console.log(JSON.stringify(row));
    await page.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(outDir, 'probe_results.json'), JSON.stringify(rows, null, 1));
  console.log('\nbigscatter_probe: ' + rows.length + ' page(s), rc=' + rc);
  process.exit(rc);
})();
