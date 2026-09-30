#!/usr/bin/env node
/*
 * js_check.js -- validate sparkta HTML output without a browser (v3.6.0-s8b)
 *
 * For every *.html in <dir>:
 *   1. extract inline <script> blocks (CDN <script src> are skipped)
 *   2. run them in a VM with a mock Chart / document / window
 *      -> catches SyntaxError, ReferenceError, undefined-variable bugs
 *   3. for every chart config captured:
 *        - structural checks (type, labels, datasets, data lengths)
 *        - invoke each plugin hook (beforeDraw, beforeDatasetsDraw,
 *          afterDatasetsDraw, afterDraw) with a mock chart -> catches
 *          runtime errors inside our hand-written plugins
 *        - invoke tooltip title/label callbacks for every point
 *   4. apply optional expectations from expectations.json (regex on filename)
 *
 * Usage:  node js_check.js <dir> [expectations.json]
 * Exit 0 = all files pass, 1 = failures. Prints a summary table + JSON report.
 * ASCII only.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const dir = process.argv[2] || '.';
const expFile = process.argv[3] || path.join(__dirname, 'expectations.json');
let expectations = [];
if (fs.existsSync(expFile)) expectations = JSON.parse(fs.readFileSync(expFile, 'utf8'));

// ---------------------------------------------------------------- mocks ----
function mockCtx() {
  const noop = () => {};
  const ctx = {};
  ['save','restore','beginPath','closePath','moveTo','lineTo','stroke','fill','fillRect','strokeRect',
   'fillText','strokeText','setLineDash','arc','rect','clip','translate','rotate','scale','clearRect',
   'ellipse','roundRect','setTransform','resetTransform','arcTo','quadraticCurveTo','bezierCurveTo','drawImage','putImageData','fillRect','strokeRect'].forEach(m => ctx[m] = noop);
  ctx.getImageData = () => ({ data: new Uint8ClampedArray(4) });
  ctx.measureText = (t) => ({ width: String(t).length * 6.5 });
  ctx.createLinearGradient = () => ({ addColorStop: noop });
  ctx.canvas = { width: 800, height: 470, getBoundingClientRect: () => ({left:0,top:0,width:800,height:470}) };
  ctx.font=''; ctx.fillStyle=''; ctx.strokeStyle=''; ctx.lineWidth=1; ctx.textAlign=''; ctx.textBaseline=''; ctx.globalAlpha=1;
  return ctx;
}
function mockScale(min, max, isCat, nCat) {
  const px = (v) => {
    if (v === null || v === undefined || (typeof v === 'number' && isNaN(v))) return NaN;
    if (isCat) return 60 + Number(v) * (700 / Math.max(nCat, 1));
    return 60 + (Number(v) - min) / ((max - min) || 1) * 700;
  };
  return { min, max, left: 60, right: 760, top: 20, bottom: 420, width: 700, height: 400,
           getPixelForValue: px, getValueForPixel: (p) => min + (p - 60) / 700 * (max - min),
           getPixelForTick: (i) => px(i), ticks: [], options: { grid: {}, ticks: {} }, type: isCat ? 'category' : 'linear', id: 'x' };
}
function mockChartInstance(cfg) {
  const ds = (cfg.data && cfg.data.datasets) || [];
  const labels = (cfg.data && cfg.data.labels) || [];
  let ymin = Infinity, ymax = -Infinity, xmin = Infinity, xmax = -Infinity, numericX = false;
  ds.forEach(d => (d.data || []).forEach(p => {
    if (p === null || p === undefined) return;
    if (typeof p === 'object') {
      if (p.y != null) { ymin = Math.min(ymin, p.y); ymax = Math.max(ymax, p.y); }
      if (p.x != null) { xmin = Math.min(xmin, p.x); xmax = Math.max(xmax, p.x); numericX = true; }
    } else { ymin = Math.min(ymin, p); ymax = Math.max(ymax, p); }
  }));
  if (!isFinite(ymin)) { ymin = 0; ymax = 1; }
  if (!isFinite(xmin)) { xmin = 0; xmax = Math.max(labels.length - 1, 1); }
  const sx = (cfg.options && cfg.options.scales && cfg.options.scales.x) || {};
  const linear = sx.type === 'linear' || numericX;
  const horiz = cfg.options && cfg.options.indexAxis === 'y';
  const x = mockScale(linear ? xmin : 0, linear ? xmax : Math.max(labels.length - 1, 1), !linear, labels.length);
  const y = mockScale(ymin, ymax, false, 0); y.id = 'y';
  const inst = {
    ctx: mockCtx(), chartArea: { left: 60, top: 20, right: 760, bottom: 420, width: 700, height: 400 },
    scales: horiz ? { x: y, y: x } : { x, y }, data: cfg.data, options: cfg.options || {}, config: cfg,
    canvas: { width: 800, height: 470 }, width: 800, height: 470,
    getDatasetMeta: (i) => ({ data: ((ds[i] && ds[i].data) || []).map((p, j) => ({ x: x.getPixelForValue(typeof p==='object'&&p?p.x:j), y: y.getPixelForValue(typeof p==='object'&&p?p.y:p), $context:{raw:p} })), hidden: false, type: cfg.type }),
    isDatasetVisible: () => true, update: () => {}, draw: () => {}, resize: () => {}, destroy: () => {},
    legend: { legendItems: [] }, tooltip: {}, boxes: [], _metasets: [] };
  return inst;
}

// ------------------------------------------------------------- checking ----
function checkFile(file) {
  const html = fs.readFileSync(file, 'utf8');
  const res = { file: path.basename(file), errors: [], warnings: [], charts: 0, info: {} };
  const scripts = [];
  const re = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi;
  let m; while ((m = re.exec(html)) !== null) {
    const body = m[1];
    // offline mode inlines the vendored libraries (Chart.js, plugins). They all
    // start with a /*! license banner; sparkta's own scripts never do. Skip them
    // so the real Chart.js cannot replace the mock recorder.
    scripts.push(body);
  }
  if (scripts.length === 0) { res.errors.push('no inline <script> found'); return res; }

  const charts = [];
  const all = scripts.join('\n');
  const elements = {};
  const doc = {
    getElementById: (id) => elements[id] || (elements[id] = { id, getContext: () => mockCtx(), style: {}, innerHTML: '', appendChild: () => {}, querySelector: () => null, querySelectorAll: () => [], classList: { add(){}, remove(){}, toggle(){}, contains(){return false;} }, setAttribute(){}, getAttribute(){return null;}, addEventListener(){}, parentNode: null, previousElementSibling: null, children: [], textContent: '', value: '', checked: false, width: 800, height: 470 }),
    querySelectorAll: () => [], querySelector: () => null, createElement: () => doc.getElementById('_new' + Math.random()),
    createElementNS: () => doc.getElementById('_ns' + Math.random()), body: doc_body(), addEventListener() {}, readyState: 'complete', documentElement: { style: {} }
  };
  function doc_body() { return { appendChild() {}, style: {}, classList: { add(){}, remove(){}, toggle(){}, contains(){return false;} }, querySelectorAll: () => [] }; }
  function Chart(el, cfg) { this.el = el; this.config = cfg; this.data = cfg.data; this.options = cfg.options; charts.push(cfg); this.update = () => {}; this.destroy = () => {}; this.resize = () => {}; this.render = () => {}; this.stop = () => {}; this.reset = () => {}; this.getDatasetMeta = (i) => ({ data: [], hidden: false }); this.isDatasetVisible = () => true; this.hide = () => {}; this.show = () => {}; this.setDatasetVisibility = () => {}; this.toBase64Image = () => ''; this.canvas = { toBlob() {} }; this.ctx = mockCtx(); this.chartArea = { left:60, top:20, right:760, bottom:420 }; this.scales = { x: mockScale(0,1,true,1), y: mockScale(0,1,false,0) }; }
  Chart.defaults = { plugins: { legend: { labels: { generateLabels: () => [] } } }, font: {}, color: '#000' };
  Chart.getChart = () => charts.length ? Object.assign({}, mockChartInstance(charts[0])) : undefined;
  Chart.register = () => {}; Chart.registry = { plugins: { get: () => null } };
  const sandbox = {
    Chart, document: doc, window: { addEventListener() {}, devicePixelRatio: 1, innerWidth: 1200, setTimeout: (f) => f(), requestAnimationFrame: (f) => f() },
    console: { log(){}, warn(){}, error(){} }, setTimeout: (f) => { try { f(); } catch (e) { res.errors.push('setTimeout cb: ' + e.message); } }, clearTimeout(){},
    requestAnimationFrame: (f) => f(), IntersectionObserver: undefined, MutationObserver: undefined, navigator: { userAgent: 'node' },
    Intl, Math, JSON, Object, Array, String, Number, Boolean, Date, RegExp, Error, parseFloat, parseInt, isNaN, isFinite, encodeURIComponent, decodeURIComponent, Blob: function(){}, URL: { createObjectURL: () => '', revokeObjectURL(){} },
    // fix9h-e: pages with more than 200 rows embed numeric columns as base64 Float64 buffers
    // (DataEmbedder PLAIN_JSON_THRESHOLD) and the engine decodes them with atob() on the first
    // filter refresh -- the vm sandbox had no atob, so every large filter page reported
    // "filter refresh threw: atob is not defined" (first seen on the fix9g 100k pages)
    atob: (b64) => Buffer.from(b64, 'base64').toString('binary'), btoa: (bin) => Buffer.from(bin, 'binary').toString('base64')
  };
  sandbox.window = sandbox; sandbox.self = sandbox; sandbox.globalThis = sandbox;
  sandbox.addEventListener = () => {}; sandbox.devicePixelRatio = 1; sandbox.innerWidth = 1200;
  vm.createContext(sandbox);
  // offline HTML inlines the real Chart.js UMD, whose wrapper does global.Chart = factory().
  // Make the mock non-writable so that assignment is a silent no-op (sloppy mode).
  // accessor with a no-op setter: safe in both sloppy and strict mode
  vm.runInContext("(function(C){Object.defineProperty(globalThis,'Chart',{get:function(){return C;},set:function(){},configurable:false});})(Chart);", sandbox);
  // t2c: vendored libraries are recognised by their license banner OR by their UMD wrapper
  // (the boxplot build has no banner). They must never run against the mock Chart.
  const isLib = src => /^\s*\/\*[!*]/.test(src) || /^\s*!function\([a-z],[a-z]\)\{"object"==typeof exports/.test(src);   // vendored: license banner (/*! or /**) or UMD wrapper; sparkta scripts never start so
  scripts.forEach((src, si) => {
    if (isLib(src)) { res.info.embeddedLibs = (res.info.embeddedLibs || 0) + 1; return; }
    try { vm.runInContext(src, sandbox, { filename: res.file + '#script' + si, timeout: 5000 }); }
    catch (e) { res.errors.push(`script${si}: ${e.name}: ${e.message}` + (e.stack ? ' @ ' + (e.stack.split('\n')[0] || '') : '')); }
  });
  res.charts = charts.length;
  if (charts.length === 0) { res.errors.push('no new Chart(...) executed'); return res; }

  charts.forEach((cfg, ci) => {
    const tag = charts.length > 1 ? `chart${ci}: ` : '';
    if (!cfg.type) res.errors.push(tag + 'missing type');
    const ds = (cfg.data && cfg.data.datasets) || [];
    const labels = (cfg.data && cfg.data.labels) || [];
    if (!Array.isArray(ds) || ds.length === 0) res.errors.push(tag + 'no datasets');
    ds.forEach((d, di) => {
      if (!Array.isArray(d.data)) { res.errors.push(tag + `dataset ${di}: data not array`); return; }
      if (labels.length && d.data.length !== labels.length && !['pie','doughnut','scatter','bubble','boxplot','violin'].includes(cfg.type))
        res.warnings.push(tag + `dataset ${di} (${d.label}): ${d.data.length} pts vs ${labels.length} labels`);
      d.data.forEach((p, pi) => {
        if (p && typeof p === 'object' && !Array.isArray(p)) {
          if ('x' in p && p.x === undefined) res.errors.push(tag + `dataset ${di} point ${pi}: x undefined`);
          if ('y' in p && p.y === undefined) res.errors.push(tag + `dataset ${di} point ${pi}: y undefined`);
        }
      });
    });
    // info -- the FIRST chart on the page is canonical (panel 0 on by() pages); later panels
    // may legitimately have fewer categories (t2c: r_by_line_styled_key reported panel 1)
    if (res.info.type !== undefined) return;
    const sx = (cfg.options && cfg.options.scales && cfg.options.scales.x) || {};
    res.info.type = cfg.type; res.info.numericX = sx.type === 'linear';
    res.info.nDatasets = ds.length; res.info.datasetLabels = ds.map(d => d.label);
    res.info.labels = labels.slice(0, 20);
    res.info.plugins = (cfg.plugins || []).map(p => p.id);
    res.info.indexAxis = cfg.options && cfg.options.indexAxis;
    // s8n: richer extraction for intent_check.py
    const sc = (cfg.options && cfg.options.scales) || {};
    res.info.axisTitles = {};
    for (const k of Object.keys(sc)) { const t = sc[k] && sc[k].title; if (t && t.display && t.text) res.info.axisTitles[k] = String(t.text); }
    res.info.stacked = Object.values(sc).some(a => a && a.stacked) || ds.some(d => d.stack);
    res.info.xType = sx.type || 'category';
    res.info.showLine = ds.map(d => d.showLine);
    res.info.fill = ds.map(d => d.fill);
    res.info.borderDash = ds.map(d => Array.isArray(d.borderDash) ? d.borderDash.join('-') : null);
    res.info.colors = ds.map(d => Array.isArray(d.backgroundColor) ? d.backgroundColor.slice(0,6) : d.backgroundColor);
    res.info.nPoints = ds.map(d => (d.data || []).length);
    res.info.nNull = ds.map(d => (d.data || []).filter(v => v === null || (v && typeof v === 'object' && (v.y === null || v.x === null))).length);
    res.info.legendDisplay = !(cfg.options && cfg.options.plugins && cfg.options.plugins.legend && cfg.options.plugins.legend.display === false);
    // reference / pexline values from plugin source
    res.info.refValues = [];
    (cfg.plugins || []).forEach(p => { const srcp = Object.values(p).filter(f => typeof f === 'function').map(f => f.toString()).join(' '); const m = srcp.match(/getPixelForValue\((-?[\d.]+)\)/g); if (m && /Zero|Pex|mpZ|Pexline/.test(p.id)) res.info.refValues.push({ id: p.id, v: m.map(x => parseFloat(x.replace(/.*\(/, ''))) }); });
    // value-axis range vs data(+CI) extent, so intent_check can judge plotmargin()
    {
      const horiz2 = cfg.options && cfg.options.indexAxis === 'y';
      const va = horiz2 ? sc.x : sc.y;
      let dmin = Infinity, dmax = -Infinity;
      ds.forEach(d => (d.data || []).forEach(p => { let v = p; if (p && typeof p === 'object' && !Array.isArray(p)) v = horiz2 ? p.x : p.y; if (typeof v === 'number' && isFinite(v)) { dmin = Math.min(dmin, v); dmax = Math.max(dmax, v); } }));
      (cfg.plugins || []).forEach(p => { const srcp = Object.values(p).filter(f => typeof f === 'function').map(f => f.toString()).join(' '); (srcp.match(/\b(?:lo|hi|lower|upper)\w*\s*=\s*\[([^\]]*)\]/g) || []).forEach(a => a.replace(/^[^\[]*\[|\]$/g, '').split(',').forEach(t => { const x = parseFloat(t); if (isFinite(x)) { dmin = Math.min(dmin, x); dmax = Math.max(dmax, x); } })); });
      if (va && typeof va.min === 'number' && typeof va.max === 'number' && isFinite(dmin)) { res.info.valueAxis = { min: va.min, max: va.max }; res.info.dataExtent = { min: dmin, max: dmax }; res.info.axisUse = +((dmax - dmin) / (va.max - va.min)).toFixed(3); }
    }
    // coefficient tooltip data if present (b values -> sort-order checks)
    const cpTip = all.match(/var _cpTip=(\[[\s\S]*?\]);/);
    if (cpTip) { try { res.info.cpTip = eval(cpTip[1]).map(d => ({ n: d.n, b: d.b, p: d.p })); } catch (e) {} }
    // plugins: exercise every hook
    const inst = mockChartInstance(cfg);
    (cfg.plugins || []).forEach(p => {
      ['beforeInit','afterInit','beforeUpdate','afterUpdate','beforeLayout','afterLayout','beforeDraw','beforeDatasetsDraw','afterDatasetsDraw','afterDraw','afterRender'].forEach(h => {
        if (typeof p[h] === 'function') {
          try { p[h](inst, {}, {}); } catch (e) { res.errors.push(tag + `plugin ${p.id}.${h}: ${e.name}: ${e.message}`); }
        }
      });
    });
    // tooltips
    const tt = cfg.options && cfg.options.plugins && cfg.options.plugins.tooltip && cfg.options.plugins.tooltip.callbacks;
    if (tt) {
      const samples = [];
      ds.forEach((d, di) => (d.data || []).forEach((p, pi) => {
        const isRadial = ['pie','doughnut','polarArea'].includes(cfg.type);
        const parsed = isRadial ? p : ((p && typeof p === 'object' && !Array.isArray(p)) ? { x: p.x, y: p.y } : { x: pi, y: p });
        samples.push({ datasetIndex: di, dataIndex: pi, dataset: d, parsed, raw: p, label: labels[pi], chart: inst, formattedValue: String(p) });
      }));
      let tErr = 0;
      samples.forEach(s => {
        try { if (tt.title) { const t = tt.title([s]); res.info.tooltipTitleSample = res.info.tooltipTitleSample || t; } }
        catch (e) { if (tErr++ < 3) res.errors.push(tag + `tooltip.title: ${e.message}`); }
        try { if (tt.label) { const l = tt.label(s); res.info.tooltipLabelSample = res.info.tooltipLabelSample || l; } }
        catch (e) { if (tErr++ < 3) res.errors.push(tag + `tooltip.label: ${e.message}`); }
      });
    }
    // legend generateLabels
    const gl = cfg.options && cfg.options.plugins && cfg.options.plugins.legend && cfg.options.plugins.legend.labels && cfg.options.plugins.legend.labels.generateLabels;
    if (typeof gl === 'function') { try { gl(inst); } catch (e) { res.errors.push(tag + `legend.generateLabels: ${e.message}`); } }
    // afterBuildTicks
    ['x','y'].forEach(ax => { const s = cfg.options && cfg.options.scales && cfg.options.scales[ax]; if (s && typeof s.afterBuildTicks === 'function') { try { s.afterBuildTicks({ ticks: [] }); } catch (e) { res.errors.push(tag + `scales.${ax}.afterBuildTicks: ${e.message}`); } } });
  });

  // static smells in emitted JS
  res.info.h1 = ((html.match(/<h1[^>]*>([^<]*)<\/h1>/) || [])[1] || '').trim();
  res.info.subtitle = ((html.match(/class='subtitle2'[^>]*>([^<]*)</) || [])[1] || '').replace(/&bull;/g, '-').trim();
  res.info.bodyBg = ((html.match(/body\{background:(#[0-9a-fA-F]{6})/) || [])[1] || '').toLowerCase();
  res.info.nCharts = charts.length;
  res.info.tableRows = (html.match(/<tr[^>]*>\s*<td[^>]*>[^<]*<\/td>\s*<td[^>]*>[^<]*/g) || []).map(t => t.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim());
  res.info.hasStatsPanel = /id='statsPanel'/.test(html);
  res.info.hasFilterUi = /_applyFilter/.test(html);
  res.info.hasDownload = /Export:<\/span><button/.test(html) || /toBase64Image/.test(html);
  // s9a: exercise the filter refresh path once (stats panel + chart rebuild) so bugs in
  // _sparkta_updateStatsBadges / _updateSpk surface here instead of in the browser
  try {
    if (typeof sandbox._applyFilter === 'function') { sandbox._applyFilter(); res.info.filterRefreshRan = true; }
    else if (typeof sandbox._sparkta_updateStatsBadges === 'function') { sandbox._sparkta_updateStatsBadges([]); res.info.filterRefreshRan = true; }
  } catch (e) { res.errors.push('filter refresh threw: ' + e.message); }
  // static smells: only inspect the CHART CONFIG literal (the JSON-like block passed
  // to new Chart), with string literals blanked, so regex literals / stats-panel
  // helper code do not trigger false positives.
  const cfgStart = all.indexOf('new Chart(');
  const cfgSrc = cfgStart >= 0 ? all.slice(cfgStart, all.indexOf('\n});', cfgStart) + 4) : '';
  const blanked = cfgSrc.replace(/'(?:\\.|[^'\\])*'/g, "''").replace(/"(?:\\.|[^"\\])*"/g, '""');
  if (/\{x:,|,y:\}|,y:,/.test(blanked)) res.errors.push('empty x/y coordinate emitted in dataset');
  if (/\[,|,,/.test(blanked)) res.errors.push('empty array element in chart config');
  if (/,\s*[\]}]/.test(blanked)) res.warnings.push('trailing comma in chart config (legal JS, invalid JSON)');
  if (/:\s*[,]/.test(blanked)) res.errors.push('empty property value in chart config');
  if (/[^\x00-\x7F]/.test(html)) res.warnings.push('non-ASCII byte in HTML');

  // expectations
  expectations.forEach(ex => {
    if (!new RegExp(ex.file).test(res.file)) return;
    const i = res.info;
    if (ex.type && i.type !== ex.type) res.errors.push(`expect type=${ex.type} got ${i.type}`);
    if (ex.numericX !== undefined && !!i.numericX !== ex.numericX) res.errors.push(`expect numericX=${ex.numericX} got ${i.numericX}`);
    if (ex.nDatasets !== undefined && i.nDatasets !== ex.nDatasets) res.errors.push(`expect nDatasets=${ex.nDatasets} got ${i.nDatasets}`);
    (ex.plugins || []).forEach(p => { if (!(i.plugins || []).some(q => new RegExp(p).test(q))) res.errors.push(`expect plugin /${p}/ missing (have ${i.plugins})`); });
    (ex.noPlugins || []).forEach(p => { if ((i.plugins || []).some(q => new RegExp(p).test(q))) res.errors.push(`plugin /${p}/ must NOT be present`); });
    if (ex.labelsInclude) ex.labelsInclude.forEach(l => { if (!(i.labels || []).map(String).includes(String(l))) res.errors.push(`expect label ${l} in ${JSON.stringify(i.labels)}`); });
    if (ex.datasetLabels) { const got = (i.datasetLabels || []).join('|'); if (got !== ex.datasetLabels.join('|')) res.errors.push(`expect datasets ${ex.datasetLabels} got ${i.datasetLabels}`); }
    if (ex.tooltipTitleRegex && !(new RegExp(ex.tooltipTitleRegex).test(String(i.tooltipTitleSample)))) res.errors.push(`tooltip title "${i.tooltipTitleSample}" !~ /${ex.tooltipTitleRegex}/`);
    if (ex.tooltipLabelRegex && !(new RegExp(ex.tooltipLabelRegex).test(String(i.tooltipLabelSample)))) res.errors.push(`tooltip label "${i.tooltipLabelSample}" !~ /${ex.tooltipLabelRegex}/`);
    if (ex.htmlIncludes) ex.htmlIncludes.forEach(s => { if (!html.includes(s)) res.errors.push(`expect HTML to include "${s}"`); });
  });
  return res;
}

// ----------------------------------------------------------------- main ----
const files = fs.readdirSync(dir).filter(f => f.toLowerCase().endsWith('.html')).sort();
if (files.length === 0) { console.error('no .html files in ' + dir); process.exit(2); }
const results = files.map(f => checkFile(path.join(dir, f)));
let bad = 0;
const pad = (s, n) => (String(s) + ' '.repeat(n)).slice(0, n);
console.log(pad('FILE', 34) + pad('TYPE', 12) + pad('X', 5) + pad('DS', 3) + pad('PLUGINS', 34) + 'STATUS');
console.log('-'.repeat(100));
results.forEach(r => {
  const st = r.errors.length ? 'FAIL' : (r.warnings.length ? 'WARN' : 'OK');
  if (r.errors.length) bad++;
  console.log(pad(r.file, 34) + pad(r.info.type || '-', 12) + pad(r.info.numericX ? 'num' : 'cat', 5) + pad(r.info.nDatasets || 0, 3) + pad((r.info.plugins || []).join(','), 34) + st);
  r.errors.forEach(e => console.log('     ERROR  ' + e));
  r.warnings.forEach(w => console.log('     warn   ' + w));
});
console.log('-'.repeat(100));
console.log(`${results.length} files, ${results.length - bad} pass, ${bad} fail`);
fs.writeFileSync(path.join(dir, '_js_check_report.json'), JSON.stringify(results, null, 1));
process.exit(bad ? 1 : 0);
