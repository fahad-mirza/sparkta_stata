#!/usr/bin/env node
/*
 * svg_check.js -- run each download-enabled page's OWN _spkChartToSvg() headlessly (s9l) and
 * verify the result is a real vector: parses as SVG, has paths and text, and (when rsvg-convert
 * is installed) rasterises without error. Skips pages without the export toolbar.
 * Usage: node svg_check.js <dir>   Needs: canvas, chart.js, @xmldom/xmldom (see README). ASCII only.
 */
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm'), cp = require('child_process');
// t1m: browsers have Path2D and Chart.js takes a different drawing branch when it exists --
// the branch that lost every line dataset in browser-made exports. Define it BEFORE chart.js loads.
global.Path2D = class { constructor() {} moveTo() {} lineTo() {} closePath() {} bezierCurveTo() {} quadraticCurveTo() {} arc() {} rect() {} };
let createCanvas, CJ, xmldom;
try { ({ createCanvas } = require('canvas')); CJ = require('chart.js'); xmldom = require('@xmldom/xmldom'); }
catch (e) { console.log('svg_check: deps missing -- stage skipped (' + e.message + ')'); process.exit(0); }
const Chart = CJ.Chart; Chart.platforms = { BasicPlatform: CJ.BasicPlatform }; Chart.register(...CJ.registerables);
try { const BP = require('@sgratzl/chartjs-chart-boxplot'); Chart.register(BP.BoxPlotController, BP.BoxAndWiskers, BP.ViolinController, BP.Violin); } catch (e) {}
// t2j fix8w: the toolbar is on every page now, so cibar/ciline pages reach this check -- register the error-bar controllers
try { const EB = require('chartjs-chart-error-bars'); Chart.register(...Object.values(EB).filter(v => v && (v.id || v.prototype))); } catch (e) {}
const src = fs.readFileSync(path.join(__dirname, 'pixel_check.js'), 'utf8');
const rerealm = eval('(' + src.match(/function rerealm[\s\S]*?\n}\n/)[0] + ')');
const extractConfig = eval('(' + src.match(/function extractConfig[\s\S]*?\n}\n/)[0] + ')');
const dir = process.argv[2] || '.'; const W = 800, H = 500;
const files = fs.readdirSync(dir).filter(f => f.endsWith('.html')).sort();
let n = 0, bad = 0;
for (const f of files) {
  const html = fs.readFileSync(path.join(dir, f), 'utf8');
  if (!/function _spkChartToSvg/.test(html)) continue;
  // t2j fix8w: by() grids emit one config per panel over shared page state; extractConfig() takes a
  // single config and the node sandbox cannot resolve the panel slices, so the line test misfires.
  // Their exports are verified in a real browser (scratch svgreal.js: every series colour present).
  if (/id='chart_by_0'/.test(html) || /id="chart_by_0"/.test(html)) { console.log('SKIP ' + f.padEnd(40) + ' by() grid -- real-browser export check'); continue; }
  n++;
  try {
    const doc = new xmldom.DOMImplementation().createDocument('http://www.w3.org/2000/svg', 'svg', null);
    const document = { createElementNS: (ns, t) => doc.createElementNS(ns, t), createElement: t => t === 'canvas' ? createCanvas(10, 10) : doc.createElement(t), createTextNode: t => doc.createTextNode(t), querySelector: () => null, querySelectorAll: () => [] };
    const sb = { document, Chart, console: { log() {}, warn() {}, error() {} }, XMLSerializer: xmldom.XMLSerializer, addEventListener() {}, Path2D: global.Path2D }; sb.window = sb; sb.self = sb; vm.createContext(sb);
    const scripts = [...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m => m[1]);
    const c2s = scripts.find(t => /C2S/.test(t) && t.length > 20000); if (!c2s) throw new Error('canvas2svg not inlined');
    vm.runInContext(c2s, sb);
    vm.runInContext(html.match(/function _spkKeyItems[\s\S]*?\nfunction _spkDownload/)[0].replace(/\nfunction _spkDownload$/, ''), sb);
    const cfg = extractConfig(html); cfg.options.responsive = false; cfg.options.animation = false; cfg.options.maintainAspectRatio = false; cfg.platform = CJ.BasicPlatform;
    const inst = new Chart(createCanvas(W, H).getContext('2d'), cfg); inst.canvas.offsetWidth = W; inst.canvas.offsetHeight = H;
    const svg = sb._spkChartToSvg(inst, [{ role: 'ci', color: '#4e79a7', label: 'key', kind: 'whisker', dash: null, shape: null, border: '', lw: 1.5 }]);
    const paths = (svg.match(/<path/g) || []).length, text = (svg.match(/<text/g) || []).length;
    let ras = 'n/a';
    try { const out = path.join(dir, f.replace('.html', '.svg')); fs.writeFileSync(out, svg); cp.execSync('rsvg-convert -w 200 "' + out + '" -o "' + out.replace('.svg', '_svg.png') + '"', { stdio: 'pipe' }); ras = 'ok'; } catch (e) { ras = e.message.includes('ENOENT') ? 'n/a' : 'FAIL'; }
    // t1m: every line dataset must leave a stroked path in its own colour
    const toRgb = c => { const m = String(c || '').match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/); if (m) return `rgb(${m[1]},${m[2]},${m[3]})`; if (/^#[0-9a-f]{6}$/i.test(c)) return `rgb(${parseInt(c.slice(1,3),16)},${parseInt(c.slice(3,5),16)},${parseInt(c.slice(5,7),16)})`; return null; };
    const lineDs = (cfg.data.datasets || []).filter(d => d && (d.type === 'line' || cfg.type === 'line' || d.showLine) && d.showLine !== false && d.borderWidth !== 0 && d.data && d.data.length > 1 && !/CI \((upper|lower)\)/.test(d.label || ''));
    // a real polyline has (n-1) L (or C, when tension > 0) commands; a re-stroked leftover has one or two
    const missingLines = lineDs.filter(d => { const c = toRgb(Array.isArray(d.borderColor) ? d.borderColor[0] : d.borderColor); if (!c) return false;
      const re = new RegExp('<path[^>]*stroke="' + c.replace(/[()]/g, '\\$&') + '"[^>]*d="([^"]*)"', 'g'); let m, best = 0;
      while ((m = re.exec(svg)) !== null) { const n = (m[1].match(/ [LC] /g) || []).length; if (n > best) best = n; }
      return best < Math.min(3, d.data.length - 1); }).map(d => d.label);
    const wh = (svg.match(/width="(\d+)" height="(\d+)"/) || []); const hasMargin = wh.length > 2 && +wh[1] > W && +wh[2] > H;   // t1j: margins around the chart
    const ok = paths > 5 && text > 0 && ras !== 'FAIL' && hasMargin && missingLines.length === 0;
    if (!ok) bad++;
    console.log((ok ? 'OK   ' : 'FAIL ') + f.padEnd(40) + ` paths=${paths} text=${text} rsvg=${ras} size=${wh[1]}x${wh[2]} margins=${hasMargin}` + (missingLines.length ? ' MISSING LINES: ' + missingLines.join(', ') : ''));
  } catch (e) { bad++; console.log('FAIL ' + f.padEnd(40) + ' ' + e.message.slice(0, 100)); }
}
console.log(`svg_check: ${n} download-enabled page(s), ${n - bad} pass, ${bad} fail`);
process.exit(bad ? 1 : 0);
