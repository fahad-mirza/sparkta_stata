#!/usr/bin/env node
/*
 * pixel_check.js -- render every chart with the REAL Chart.js on a real canvas
 * (node-canvas) and inspect pixels. Closes the blind spot that js_check has:
 * js_check proves plugins run; this proves what they drew.
 *
 * Checks per chart:
 *   PX-OUTSIDE  chart-coloured pixels outside chartArea (+2px tolerance):
 *               plugins must clip; nothing may paint over axes/labels
 *   PX-EMPTY    plot area has no non-background pixels at all (blank chart)
 *   PX-CLIPPED  (info) fraction of plot area painted
 *
 * Usage: node pixel_check.js <dir_with_html> [--strict]
 * Needs: npm i canvas chart.js@4.4.0 (see verify/README.md "pixel stage").
 * ASCII only.
 */
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');
let createCanvas, Chart;
let BasicPlatform;
try { ({ createCanvas } = require('canvas')); const CJ = require('chart.js'); Chart = CJ.Chart; BasicPlatform = CJ.BasicPlatform; Chart.register(...CJ.registerables);
  try { const BP = require('@sgratzl/chartjs-chart-boxplot'); Chart.register(BP.BoxPlotController, BP.BoxAndWiskers, BP.ViolinController, BP.Violin); } catch (e) {}
  try { const EB = require('chartjs-chart-error-bars'); Chart.register(EB.BarWithErrorBarsController, EB.BarWithErrorBar, EB.LineWithErrorBarsController, EB.PointWithErrorBar, EB.ScatterWithErrorBarsController); } catch (e) {} }
catch (e) { console.log('pixel_check: node-canvas / chart.js not installed -- stage skipped (' + e.message + ')'); process.exit(0); }

const dir = process.argv[2] || '.'; const strict = process.argv.includes('--strict');
const W = 800, H = 500;

function extractConfig(html) {
  const scripts = []; const re = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi; let m;
  const isLib = src => /^\s*\/\*[!*]/.test(src) || /^\s*!function\([a-z],[a-z]\)\{"object"==typeof exports/.test(src);   // vendored: license banner (/*! or /**) or UMD wrapper; sparkta scripts never start so
  while ((m = re.exec(html)) !== null) if (!isLib(m[1])) scripts.push(m[1]);
  let cfg = null; const noop = () => {};
  const canvas = createCanvas(W, H);
  const el = () => ({ getContext: () => canvas.getContext('2d'), style: {}, classList: { add: noop, remove: noop, toggle: noop, contains: () => false }, appendChild: noop, setAttribute: noop, getAttribute: () => null, addEventListener: noop, querySelector: () => null, querySelectorAll: () => [], innerHTML: '', textContent: '', width: W, height: H, children: [], previousElementSibling: null, parentNode: null });
  function FakeChart(e, c) { cfg = c; this.update = noop; this.destroy = noop; this.resize = noop; this.render = noop; this.stop = noop; this.ctx = canvas.getContext('2d'); this.chartArea = { left: 0, top: 0, right: W, bottom: H }; this.scales = {}; this.getDatasetMeta = () => ({ data: [], hidden: false }); this.data = c.data; this.options = c.options; }
  FakeChart.defaults = { plugins: { legend: { labels: { generateLabels: () => [] } } }, font: { size: 12 }, color: '#666' };
  FakeChart.register = noop; FakeChart.getChart = () => undefined; FakeChart.registry = { plugins: { get: () => null } };
  const sb = { Chart: FakeChart, document: { getElementById: el, querySelector: () => null, querySelectorAll: () => [], createElement: el, createElementNS: el, body: el(), addEventListener: noop, readyState: 'complete', documentElement: { style: {} } }, console: { log: noop, warn: noop, error: noop }, setTimeout: f => { try { f(); } catch (e) {} }, clearTimeout: noop, requestAnimationFrame: f => f(), Intl, Math, JSON, Object, Array, String, Number, Boolean, Date, RegExp, parseFloat, parseInt, isNaN, isFinite, navigator: { userAgent: 'node' }, IntersectionObserver: undefined, MutationObserver: undefined, Blob: function () {}, URL: { createObjectURL: () => '', revokeObjectURL: noop } };
  sb.window = sb; sb.self = sb; sb.globalThis = sb; sb.addEventListener = noop; sb.devicePixelRatio = 1; sb.innerWidth = 1200;
  vm.createContext(sb);
  vm.runInContext("(function(C){Object.defineProperty(globalThis,'Chart',{get:function(){return C;},set:function(){},configurable:false});})(Chart);", sb);
  scripts.forEach(s => { try { vm.runInContext(s, sb, { timeout: 5000 }); } catch (e) {} });
  return rerealm(cfg);
}
// t2d: objects created inside the vm sandbox belong to another realm; Chart.js's option
// resolver rejects them (Object.getPrototypeOf(x) !== Object.prototype), which silently
// dropped every legend whose labels object came from the page. Rebuild plain objects and
// arrays in this realm; functions and primitives are kept by reference.
function rerealm(v, seen = new Map()) {
  if (v === null || typeof v !== 'object') return v;
  if (seen.has(v)) return seen.get(v);
  if (Array.isArray(v) || Object.prototype.toString.call(v) === '[object Array]') { const a = []; seen.set(v, a); for (const x of v) a.push(rerealm(x, seen)); return a; }
  const tag = Object.prototype.toString.call(v);
  if (tag !== '[object Object]') return v;   // canvases, dates, typed arrays, ...
  const o = {}; seen.set(v, o); for (const k of Object.keys(v)) o[k] = rerealm(v[k], seen); return o;
}

function render(cfg) {
  const canvas = createCanvas(W, H);
  cfg.options = cfg.options || {}; cfg.options.responsive = false; cfg.options.animation = false;
  cfg.options.maintainAspectRatio = false; cfg.platform = BasicPlatform;
  // inset the plot area so anything a plugin draws beyond it lands ON the canvas (in the
  // browser the page is taller; a leak that falls off a tight canvas would go unnoticed)
  cfg.options.layout = { padding: 80 };
  // node-canvas has no font metrics for some fonts; Chart.js copes.
  let chart = new Chart(canvas.getContext('2d'), cfg);
  if (!isFinite(chart.chartArea.top)) {   // node-canvas cannot measure custom legend items; fall back to default items
    chart.destroy(); if (cfg.options.plugins && cfg.options.plugins.legend && cfg.options.plugins.legend.labels) delete cfg.options.plugins.legend.labels.generateLabels;
    chart = new Chart(canvas.getContext('2d'), cfg);
  }
  chart.draw();
  const area = Object.assign({}, chart.chartArea);   // read pixels BEFORE destroy() -- destroy clears the canvas
  const legend = chart.legend && chart.legend.legendItems && chart.legend.legendItems.length ? { left: chart.legend.left, top: chart.legend.top, right: chart.legend.right, bottom: chart.legend.bottom } : null;
  return { canvas, area, legend };
}

function seriesColours(cfg) {
  const out = []; const push = c => { if (typeof c === 'string') { const m = c.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/); if (m) out.push([+m[1], +m[2], +m[3]]); else if (/^#[0-9a-f]{6}$/i.test(c)) out.push([parseInt(c.slice(1,3),16), parseInt(c.slice(3,5),16), parseInt(c.slice(5,7),16)]); } };
  (cfg.data.datasets || []).forEach(d => { [d.backgroundColor, d.borderColor].forEach(c => Array.isArray(c) ? c.forEach(push) : push(c)); });
  return out;
}
function analyse(canvas, area, bg, legend, cfg) {
  const cols = seriesColours(cfg);
  const nearSeries = (r, g, b) => cols.some(c => Math.abs(c[0]-r) < 40 && Math.abs(c[1]-g) < 40 && Math.abs(c[2]-b) < 40);
  let maxR = 0; (cfg.data.datasets || []).forEach(d => { const rr = typeof d.pointRadius === 'number' ? d.pointRadius : 3; if (rr > maxR) maxR = rr; if (cfg.type === 'bubble') maxR = Math.max(maxR, 25); });
  const tol = 2 + maxR;   // markers legitimately straddle the plot edge
  const ctx = canvas.getContext('2d'); const img = ctx.getImageData(0, 0, W, H).data;
  const isBg = (r, g, b, a) => a === 0 || (Math.abs(r - bg[0]) < 8 && Math.abs(g - bg[1]) < 8 && Math.abs(b - bg[2]) < 8);
  const isGrey = (r, g, b) => Math.abs(r - g) < 14 && Math.abs(g - b) < 14;   // axes, grid, text
  let outside = 0, inside = 0, insideTotal = 0;
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = (y * W + x) * 4; const r = img[i], g = img[i + 1], b = img[i + 2], a = img[i + 3];
    const inArea = x >= area.left - tol && x <= area.right + tol && y >= area.top - tol && y <= area.bottom + tol;
    if (inArea) { insideTotal++; if (!isBg(r, g, b, a)) inside++; continue; }
    if (legend && x >= legend.left - 2 && x <= legend.right + 2 && y >= legend.top - 2 && y <= legend.bottom + 2) continue;   // legend swatches are legitimately coloured
    if (!isBg(r, g, b, a) && !isGrey(r, g, b) && nearSeries(r, g, b)) outside++;   // SERIES-coloured ink outside the plot = a plugin leaked
  }
  return { outside, insideFrac: insideTotal ? inside / insideTotal : 0 };
}

const files = fs.readdirSync(dir).filter(f => f.endsWith('.html')).sort();
let bad = 0; const pad = (s, n) => (String(s) + ' '.repeat(n)).slice(0, n);
console.log(pad('FILE', 36) + pad('outside px', 11) + pad('inside %', 9) + 'STATUS');
for (const f of files) {
  const html = fs.readFileSync(path.join(dir, f), 'utf8');
  const cfg = extractConfig(html);
  if (!cfg) { console.log(pad(f, 36) + 'no config'); continue; }
  const bgm = html.match(/\.chart-wrapper\{background:(#[0-9a-fA-F]{6})/); const bgHex = bgm ? bgm[1] : '#ffffff';
  const bg = [1, 3, 5].map(i => parseInt(bgHex.slice(i, i + 2), 16));
  let res;
  try { const { canvas, area, legend } = render(cfg); res = analyse(canvas, area, bg, legend, cfg); if (process.env.PIXEL_PNG) fs.writeFileSync(path.join(dir, f.replace('.html', '.png')), canvas.toBuffer('image/png')); }
  catch (e) { console.log(pad(f, 36) + 'RENDER ERROR ' + e.message.slice(0, 80)); bad++; continue; }
  const findings = [];
  if (res.outside > 40) findings.push(`PX-OUTSIDE ${res.outside}px of coloured ink outside the plot area`);
  if (res.insideFrac < 0.002) findings.push('PX-EMPTY plot area is blank');
  if (findings.length) bad++;
  console.log(pad(f, 36) + pad(res.outside, 11) + pad((100 * res.insideFrac).toFixed(1), 9) + (findings.length ? 'FAIL  ' + findings.join('; ') : 'OK'));
}
console.log(`${files.length} files, ${files.length - bad} pass, ${bad} fail`);
process.exit(strict && bad ? 1 : 0);
