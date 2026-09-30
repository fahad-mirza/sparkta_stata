#!/usr/bin/env node
/*
 * sweep_probe.js -- v3.6.0-t2j fix8q QC sweep, prong A (visual/behavioural).
 *
 * For every sparkta HTML page in a directory, opens it in headless Chromium and,
 * for EVERY Chart.js chart on the page:
 *   1. waits for the animation to settle (animation false-positive lesson, run2)
 *   2. hovers the centre of up to 3 elements per dataset -> expects the tooltip to
 *      FIRE (opacity 1) and records title + body
 *   3. hovers an empty spot inside the plot area that is >= 25 px from every
 *      element -> expects the tooltip to HIDE (intersect:true dismiss rule)
 *   4. moves the mouse off the canvas -> expects the tooltip to HIDE
 *   5. captures the page console for JS errors
 *   6. records the chart box size before/after a viewport resize (aspect lock)
 * Violin pages use their own HTML div tooltip (tooltip:{enabled:false}); the probe
 * reads that div instead.
 * Writes <outdir>/_sweep_report.json + one PNG per page (post-animation).
 * Usage: node verify/sweep_probe.js <dir> [filter-substring]
 * ASCII only.
 */
'use strict';
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

async function probePage(browser, file) {
  const page = await browser.newPage({ viewport: { width: 1100, height: 800 } });
  const errors = [];
  page.on('pageerror', e => errors.push(String(e.message || e)));
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()); });
  await page.goto('file://' + path.resolve(file), { waitUntil: 'load' });
  await page.waitForTimeout(1800);   // let grow animations finish (violin RAF engine ~1.2s)

  const canvasIds = await page.evaluate(() => Array.from(document.querySelectorAll('canvas')).filter(c => window.Chart && window.Chart.getChart(c)).map(c => c.id));
  const result = { file: path.basename(file), errors, charts: [] };
  for (const cid of canvasIds) {
  // fix8q: scroll each panel into view first -- by() panels below the fold cannot be hovered
  await page.evaluate((cid) => document.getElementById(cid).scrollIntoView({ block: 'center' }), cid);
  await page.waitForTimeout(250);
  const charts = await page.evaluate((cid) => {
    const out = [];
    [document.getElementById(cid)].forEach(c => {
      const ch = window.Chart && window.Chart.getChart(c);
      if (!ch) return;
      const r = c.getBoundingClientRect();
      const ca = ch.chartArea;
      const els = [];
      ch.data.datasets.forEach((ds, di) => {
        const meta = ch.getDatasetMeta(di);
        if (meta.hidden || !meta.data) return;
        const n = meta.data.length;
        const picks = n <= 3 ? [...Array(n).keys()] : [0, Math.floor(n / 2), n - 1];
        picks.forEach(i => {
          const e = meta.data[i];
          if (!e) return;
          let x = e.x, y = e.y, w = 0, h = 0;
          const p = e.getProps ? e.getProps(['x', 'y', 'base', 'width', 'height', 'horizontal'], true) : e;
          if (p.base !== undefined && p.width !== undefined) {
            // bar: centre of the body
            if (p.horizontal) { x = (p.x + p.base) / 2; y = p.y; w = Math.abs(p.x - p.base); h = p.height; }
            else { x = p.x; y = (p.y + p.base) / 2; w = p.width; h = Math.abs(p.y - p.base); }
          }
          if (e.startAngle !== undefined) {
            // arc: mid-angle at mid-radius
            const a = (e.startAngle + e.endAngle) / 2, rr = (e.innerRadius + e.outerRadius) / 2;
            x = e.x + Math.cos(a) * rr; y = e.y + Math.sin(a) * rr;
          }
          if (Number.isFinite(x) && Number.isFinite(y)) els.push({ di, i, x, y, w, h, label: ds.label });
        });
      });
      out.push({ id: c.id, type: ch.config.type, left: r.left, top: r.top, width: r.width, height: r.height,
        ca: ca ? { l: ca.left, t: ca.top, r: ca.right, b: ca.bottom } : null, els,
        tooltipEnabled: !!(ch.options.plugins && ch.options.plugins.tooltip && ch.options.plugins.tooltip.enabled !== false),
        interaction: ch.options.interaction ? { mode: ch.options.interaction.mode, intersect: ch.options.interaction.intersect } : null,
        tip: ch.options.plugins && ch.options.plugins.tooltip ? { mode: ch.options.plugins.tooltip.mode, intersect: ch.options.plugins.tooltip.intersect } : null,
        allEls: (function () { const a = []; ch.data.datasets.forEach((d, di) => { const m = ch.getDatasetMeta(di); if (m.data) m.data.forEach(e => a.push([e.x, e.y])); }); return a; })(),
        // every bar body (not just the sampled ones) so the empty-spot scan never lands on a bar
        allBars: (function () { const a = []; ch.data.datasets.forEach((d, di) => { const m = ch.getDatasetMeta(di); if (!m.data) return; m.data.forEach(e => { const p = e.getProps ? e.getProps(['x', 'y', 'base', 'width', 'height', 'horizontal'], true) : e; if (p.base === undefined || p.width === undefined) return; if (p.horizontal) a.push([(p.x + p.base) / 2, p.y, Math.abs(p.x - p.base), p.height]); else a.push([p.x, (p.y + p.base) / 2, p.width, Math.abs(p.y - p.base)]); }); }); return a; })(),
        isArcChart: ch.config.type === 'pie' || ch.config.type === 'doughnut'
      });
    });
    return out;
  }, cid);

  async function readTip(cid) {
    return page.evaluate((cid) => {
      const ch = window.Chart.getChart(document.getElementById(cid));
      const t = ch && ch.tooltip;
      const vd = Array.from(document.querySelectorAll('div')).filter(d => d.style && d.style.position === 'fixed' && d.style.zIndex === '9999');
      const vdShown = vd.some(d => d.style.display !== 'none');
      return { op: t ? t.opacity : null, title: t && t.title ? t.title.join('|') : '', body: t && t.body ? t.body.map(b => b.lines.join(' ')).join(' / ') : '', violinDiv: vdShown, violinText: vdShown ? vd.map(d => d.textContent).join(' ') : '' };
    }, cid);
  }

  for (const ch of charts) {
    const rep = { id: ch.id, type: ch.type, interaction: ch.interaction, tip: ch.tip, tooltipEnabled: ch.tooltipEnabled, hits: [], misses: [] };
    const isViolin = !ch.tooltipEnabled;
    // Chart.js animates tooltip opacity over ~400 ms: wait it out before reading (fix8q lesson)
    const SETTLE = 350;
    for (const e of ch.els) {
      if (isViolin) {
        // violin: invisible frame bars sit at height 0; the HTML tooltip keys off the category
        // centre x and a cursor value inside [min,max] -> scan y through the plot area
        let fired = false, text = '';
        for (let gy = 0.05; gy < 1 && !fired; gy += 0.08) {
          const py = ch.ca.t + (ch.ca.b - ch.ca.t) * gy, px = e.x;
          await page.mouse.move(ch.left + (ch.type === 'bar' && ch.ca && e.w === 0 ? px : px), ch.top + py);
          await page.waitForTimeout(60);
          const t = await readTip(ch.id);
          if (t.violinDiv) { fired = true; text = t.violinText.slice(0, 60); }
        }
        rep.hits.push({ ds: e.di, i: e.i, label: e.label, fired, title: text, body: '' });
        continue;
      }
      await page.mouse.move(ch.left + e.x, ch.top + e.y);
      await page.waitForTimeout(SETTLE);
      const t = await readTip(ch.id);
      const fired = t.op > 0.5;
      rep.hits.push({ ds: e.di, i: e.i, label: e.label, fired, title: t.title, body: t.body.slice(0, 90) });
    }
    // empty spot: scan a grid inside the chart area for a point >= 25px from every element
    let empty = null;
    if (ch.ca) {
      outer: for (let gy = 0.1; gy < 1; gy += 0.1) for (let gx = 0.1; gx < 1; gx += 0.1) {
        const px = ch.ca.l + (ch.ca.r - ch.ca.l) * gx, py = ch.ca.t + (ch.ca.b - ch.ca.t) * gy;
        let ok = true;
        for (const [ex, ey] of ch.allEls) { if (Math.hypot(ex - px, ey - py) < 25) { ok = false; break; } }
        // bars/arcs: also stay clear of bar bodies
        for (const [bx, by, bw, bh] of ch.allBars) { if (Math.abs(px - bx) < bw / 2 + 10 && Math.abs(py - by) < bh / 2 + 10) { ok = false; break; } }
        if (ch.isArcChart) { ok = false; }   // a pie fills its plot area: use the off-canvas check only
        if (ok) { empty = [px, py]; break outer; }
      }
    }
    if (empty) {
      await page.mouse.move(ch.left + empty[0], ch.top + empty[1]);
      await page.waitForTimeout(SETTLE);
      const t = await readTip(ch.id);
      rep.misses.push({ where: 'empty-in-plot', shown: isViolin ? t.violinDiv : t.op > 0.5, title: t.title });
    } else rep.misses.push({ where: 'empty-in-plot', shown: null, note: 'no empty spot found (dense chart)' });
    await page.mouse.move(2, 2);
    await page.waitForTimeout(SETTLE);
    const t2 = await readTip(ch.id);
    rep.misses.push({ where: 'off-canvas', shown: isViolin ? t2.violinDiv : t2.op > 0.5 });
    rep.box = { w: ch.width, h: ch.height };
    result.charts.push(rep);
  }
  }
  // aspect-lock: resize the viewport and check the chart box follows (no collapse, no growth loop)
  await page.setViewportSize({ width: 900, height: 700 });
  await page.waitForTimeout(500);
  const after = await page.evaluate(() => Array.from(document.querySelectorAll('canvas')).map(c => { const r = c.getBoundingClientRect(); return [c.id, Math.round(r.width), Math.round(r.height)]; }));
  await page.setViewportSize({ width: 1100, height: 800 });
  await page.waitForTimeout(500);
  const back = await page.evaluate(() => Array.from(document.querySelectorAll('canvas')).map(c => { const r = c.getBoundingClientRect(); return [c.id, Math.round(r.width), Math.round(r.height)]; }));
  result.resize = { after, back };
  await page.screenshot({ path: file.replace(/\.html$/, '.png'), fullPage: false });
  await page.close();
  return result;
}

(async () => {
  const dir = process.argv[2];
  const filt = process.argv[3] || '';
  const files = fs.readdirSync(dir).filter(f => f.endsWith('.html') && f.includes(filt)).sort();
  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const all = [];
  for (const f of files) {
    try { all.push(await probePage(browser, path.join(dir, f))); }
    catch (e) { all.push({ file: f, fatal: String(e) }); }
    const r = all[all.length - 1];
    const bad = [];
    if (r.fatal) bad.push('FATAL ' + r.fatal);
    if (r.errors && r.errors.length) bad.push('JSERR ' + r.errors.length);
    (r.charts || []).forEach(c => {
      const nf = c.hits.filter(h => !h.fired).length;
      if (nf) bad.push(c.id + ': ' + nf + '/' + c.hits.length + ' elements did NOT fire');
      c.misses.forEach(m => { if (m.shown === true) bad.push(c.id + ': tooltip STILL SHOWN at ' + m.where); });
    });
    console.log((bad.length ? 'WARN ' : 'ok   ') + f.padEnd(36) + (bad.length ? bad.join('; ') : ''));
  }
  fs.writeFileSync(path.join(dir, '_sweep_report.json'), JSON.stringify(all, null, 1));
  await browser.close();
})();
