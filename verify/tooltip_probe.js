#!/usr/bin/env node
/*
 * tooltip_probe.js -- open an offline sparkta HTML in a real browser, drive the
 * Chart.js tooltip to a specific (datasetIndex,pointIndex), and report:
 *   - the point's pixel position on the canvas
 *   - the tooltip caret (caretX/caretY) and its rendered box (x,y,width,height)
 *   - the tooltip body lines (title + labels)
 *   - the caret->point offset in px
 * Also writes a screenshot with the tooltip visible.
 * Usage: node tooltip_probe.js <file.html> [dsIndex] [ptIndex] [outPng]
 * ASCII only.
 */
'use strict';
const { chromium } = require('playwright');
const path = require('path');

(async () => {
  const file = process.argv[2];
  const ds = parseInt(process.argv[3] || '0', 10);
  const pt = parseInt(process.argv[4] || '0', 10);
  const outPng = process.argv[5] || (file.replace(/\.html$/, '') + '_tip.png');
  const url = 'file://' + path.resolve(file);

  const browser = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const page = await browser.newPage({ viewport: { width: 1100, height: 800 }, deviceScaleFactor: 2 });
  const logs = [];
  page.on('console', m => logs.push(m.text()));
  await page.goto(url, { waitUntil: 'networkidle' });
  await page.waitForTimeout(900);

  // Step 1: find the point pixel and canvas bounding box in the page
  const geo = await page.evaluate(({ ds, pt }) => {
    function findChart() {
      const cs = document.querySelectorAll('canvas');
      for (const c of cs) { const ch = window.Chart && window.Chart.getChart && window.Chart.getChart(c); if (ch) return { ch, c }; }
      return null;
    }
    const f = findChart();
    if (!f) return { error: 'no chart' };
    const chart = f.ch;
    const rect = f.c.getBoundingClientRect();
    const meta = chart.getDatasetMeta(ds);
    const el = meta && meta.data && meta.data[pt];
    let point = null;
    if (el) { const p = el.getProps(['x', 'y'], true); point = { x: p.x, y: p.y }; }
    // Chart.js element x/y are already in CSS px relative to the canvas
    const pageXY = point ? { x: rect.left + point.x, y: rect.top + point.y } : null;
    return { point, pageXY, rect: { l: rect.left, t: rect.top, w: rect.width, h: rect.height } };
  }, { ds, pt });

  if (geo && geo.pageXY) {
    await page.mouse.move(geo.pageXY.x, geo.pageXY.y, { steps: 4 });
    await page.waitForTimeout(400);
  }

  const result = await page.evaluate(({ ds, pt, geo }) => {
    function findChart() {
      const cs = document.querySelectorAll('canvas');
      for (const c of cs) { const ch = window.Chart && window.Chart.getChart && window.Chart.getChart(c); if (ch) return ch; }
      return null;
    }
    const chart = findChart();
    if (!chart) return { error: 'no chart' };
    const meta = chart.getDatasetMeta(ds);
    const el = meta && meta.data && meta.data[pt];
    let point = null;
    if (el) { const p = el.getProps(['x', 'y'], true); point = { x: p.x, y: p.y }; }
    const tt = chart.tooltip;
    const box = { x: tt.x, y: tt.y, w: tt.width, h: tt.height, caretX: tt.caretX, caretY: tt.caretY };
    const lines = [];
    if (tt.title) tt.title.forEach(t => lines.push('TITLE: ' + t));
    if (tt.body) tt.body.forEach(b => (b.lines || []).forEach(l => lines.push('LINE: ' + l)));
    const off = point ? Math.round(Math.hypot(box.caretX - point.x, box.caretY - point.y)) : null;
    // report scales domain for context
    const ys = chart.scales.y ? { min: chart.scales.y.min, max: chart.scales.y.max } : null;
    const nds = chart.data.datasets.length;
    const ttOpt = chart.options.plugins && chart.options.plugins.tooltip ? {
      mode: chart.options.plugins.tooltip.mode, position: chart.options.plugins.tooltip.position, intersect: chart.options.plugins.tooltip.intersect
    } : null;
    const interOpt = chart.options.interaction || null;
    return { type: chart.config.type, nds, point, box, lines, caretToPointPx: off, ys, ttOpt, interOpt, opacity: tt.opacity };
  }, { ds, pt, geo });

  await page.screenshot({ path: outPng });
  console.log(JSON.stringify(result, null, 2));
  console.log('SCREENSHOT: ' + outPng);
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
