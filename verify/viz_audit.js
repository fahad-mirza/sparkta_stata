#!/usr/bin/env node
/*
 * viz_audit.js -- audit sparkta HTML output against verify/VIZ_STANDARD.md (v1)
 *
 * Usage: node viz_audit.js <dir> [--strict]
 *   Reads each *.html, extracts the Chart.js config (same mock approach as
 *   js_check.js), and evaluates every rule in VIZ_STANDARD.md. Prints a scored
 *   table, writes <dir>/_viz_audit_report.json. Exit 1 only with --strict and
 *   at least one ERROR-level finding. ASCII only.
 */
'use strict';
const fs = require('fs'), path = require('path'), vm = require('vm');
const dir = process.argv[2] || '.';
const strict = process.argv.includes('--strict');

// ------------------------------------------------------------- colour utils
function parseColor(c) {
  if (!c || typeof c !== 'string') return null;
  c = c.trim();
  let m;
  if ((m = c.match(/^#([0-9a-f]{3})$/i))) return [parseInt(m[1][0]+m[1][0],16), parseInt(m[1][1]+m[1][1],16), parseInt(m[1][2]+m[1][2],16), 1];
  if ((m = c.match(/^#([0-9a-f]{6})([0-9a-f]{2})?$/i))) return [parseInt(m[1].slice(0,2),16), parseInt(m[1].slice(2,4),16), parseInt(m[1].slice(4,6),16), m[2] ? parseInt(m[2],16)/255 : 1];
  if ((m = c.match(/^rgba?\(([^)]+)\)$/i))) { const p = m[1].split(',').map(Number); return [p[0], p[1], p[2], p.length > 3 ? p[3] : 1]; }
  const named = { navy:[0,0,128], maroon:[128,0,0], darkgreen:[0,100,0], black:[0,0,0], white:[255,255,255], red:[255,0,0], blue:[0,0,255], green:[0,128,0], orange:[255,165,0], gray:[128,128,128], grey:[128,128,128], purple:[128,0,128], teal:[0,128,128], gold:[255,215,0] };
  if (named[c.toLowerCase()]) return [...named[c.toLowerCase()], 1];
  return null;
}
function blend(fg, bg) { const a = fg[3] == null ? 1 : fg[3]; return [0,1,2].map(i => Math.round(fg[i]*a + bg[i]*(1-a))); }
function lum([r,g,b]) { const f = v => { v /= 255; return v <= 0.03928 ? v/12.92 : Math.pow((v+0.055)/1.055, 2.4); }; return 0.2126*f(r)+0.7152*f(g)+0.0722*f(b); }
function contrast(a, b) { const l1 = lum(a), l2 = lum(b); return (Math.max(l1,l2)+0.05)/(Math.min(l1,l2)+0.05); }
function rgb2lab([r,g,b]) {
  let R=r/255,G=g/255,B=b/255; const f=v=>v>0.04045?Math.pow((v+0.055)/1.055,2.4):v/12.92; R=f(R);G=f(G);B=f(B);
  let X=(R*0.4124+G*0.3576+B*0.1805)/0.95047, Y=(R*0.2126+G*0.7152+B*0.0722), Z=(R*0.0193+G*0.1192+B*0.9505)/1.08883;
  const g2=v=>v>0.008856?Math.cbrt(v):(7.787*v+16/116); X=g2(X);Y=g2(Y);Z=g2(Z);
  return [116*Y-16, 500*(X-Y), 200*(Y-Z)];
}
function dE76(a, b) { const A=rgb2lab(a), B=rgb2lab(b); return Math.sqrt((A[0]-B[0])**2+(A[1]-B[1])**2+(A[2]-B[2])**2); }
// Vienot/Brettel/Mollon (1999) deuteranopia simulation in linear RGB
function simDeutan([r,g,b]) {
  const lin = v => { v/=255; return v<=0.04045? v/12.92 : Math.pow((v+0.055)/1.055,2.4); };
  const gam = v => { v = v<=0.0031308 ? 12.92*v : 1.055*Math.pow(v,1/2.4)-0.055; return Math.max(0,Math.min(255,Math.round(v*255))); };
  const R=lin(r),G=lin(g),B=lin(b);
  const M=[[0.367322,0.860646,-0.227968],[0.280085,0.672501,0.047413],[-0.011820,0.042940,0.968881]];
  return [gam(M[0][0]*R+M[0][1]*G+M[0][2]*B), gam(M[1][0]*R+M[1][1]*G+M[1][2]*B), gam(M[2][0]*R+M[2][1]*G+M[2][2]*B)];
}

// ------------------------------------------------------------- extraction
function extractConfigs(html, res) {
  const scripts = []; const re = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi; let m;
  while ((m = re.exec(html)) !== null) scripts.push(m[1]);
  const charts = []; const noop = () => {};
  const ctx = new Proxy({}, { get: (t, k) => (k === 'measureText' ? (s => ({ width: String(s).length * 6.5 })) : (typeof k === 'string' && k === 'canvas') ? { width: 800, height: 470 } : noop) });
  const el = () => ({ getContext: () => ctx, style: {}, classList: { add(){}, remove(){}, toggle(){}, contains(){return false;} }, appendChild(){}, setAttribute(){}, getAttribute(){return null;}, addEventListener(){}, querySelector(){return null;}, querySelectorAll(){return [];}, innerHTML:'', textContent:'', width:800, height:470, children:[], previousElementSibling:null });
  const document = { getElementById: el, querySelector: () => null, querySelectorAll: () => [], createElement: el, createElementNS: el, body: el(), addEventListener(){}, readyState:'complete', documentElement:{style:{}} };
  function Chart(e, cfg) { charts.push(cfg); this.config = cfg; this.data = cfg.data; this.options = cfg.options; this.update=noop; this.destroy=noop; this.resize=noop; this.render=noop; this.stop=noop; this.ctx = ctx; this.chartArea={left:60,top:20,right:760,bottom:420}; this.scales={}; this.getDatasetMeta=()=>({data:[],hidden:false}); }
  Chart.defaults = { plugins: { legend: { labels: { generateLabels: () => [] } } }, font: { size: 12 }, color: '#666' };
  Chart.register = noop; Chart.getChart = () => undefined; Chart.registry = { plugins: { get: () => null } };
  const sb = { Chart, document, console: { log: noop, warn: noop, error: noop }, setTimeout: f => { try { f(); } catch (e) {} }, clearTimeout: noop, requestAnimationFrame: f => f(), Intl, Math, JSON, Object, Array, String, Number, Boolean, Date, RegExp, parseFloat, parseInt, isNaN, isFinite, navigator: { userAgent: 'node' }, IntersectionObserver: undefined, MutationObserver: undefined, Blob: function(){}, URL: { createObjectURL: () => '', revokeObjectURL: noop } };
  sb.window = sb; sb.self = sb; sb.globalThis = sb; sb.addEventListener = noop; sb.devicePixelRatio = 1; sb.innerWidth = 1200;
  vm.createContext(sb);
  vm.runInContext("(function(C){Object.defineProperty(globalThis,'Chart',{get:function(){return C;},set:function(){},configurable:false});})(Chart);", sb);
  scripts.forEach(src => { try { vm.runInContext(src, sb, { timeout: 5000 }); } catch (e) { res.notes.push('script error: ' + e.message); } });
  return charts;
}

// ------------------------------------------------------------- audit
function audit(file) {
  const html = fs.readFileSync(file, 'utf8');
  const res = { file: path.basename(file), findings: [], notes: [], score: 100, info: {} };
  const add = (id, sev, msg, ref) => res.findings.push({ id, sev, msg, ref });
  const charts = extractConfigs(html, res);
  if (!charts.length) { add('RENDER', 'ERROR', 'no chart config could be executed', '--'); return finish(res); }
  const bgMatch = html.match(/body\{background:(#[0-9a-fA-F]{6}|rgba?\([^)]*\))/); const bg = parseColor(bgMatch ? bgMatch[1] : '#ffffff') || [255,255,255,1];
  const plotBgMatch = html.match(/\.chart-wrapper\{background:(#[0-9a-fA-F]{6}|rgba?\([^)]*\))/); const plotBg = parseColor(plotBgMatch ? plotBgMatch[1] : '#ffffff') || bg;
  const isPostEst = /type=(coefplot|eventstudy|marginsplot)/.test(html);
  const peType = (html.match(/type=(coefplot|eventstudy|marginsplot)/) || [])[1];
  const title = (html.match(/<h1[^>]*>([^<]*)<\/h1>/) || [])[1] || '';
  res.info.title = title.trim();

  charts.forEach((cfg, ci) => {
    const tag = charts.length > 1 ? `[chart ${ci}] ` : '';
    const type = cfg.type; const ds = (cfg.data && cfg.data.datasets) || []; const labels = (cfg.data && cfg.data.labels) || [];
    const opts = cfg.options || {}; const scales = opts.scales || {}; const plugins = (cfg.plugins || []).map(p => p.id);
    const horiz = opts.indexAxis === 'y';
    const valueAxis = horiz ? (scales.x || {}) : (scales.y || {});
    const catAxis = horiz ? (scales.y || {}) : (scales.x || {});
    const isRadial = ['pie','doughnut','polarArea'].includes(type);
    const isDistrib = /boxplot|violin/i.test(type) || plugins.some(p => /Violin|bpInline|customViolin/i.test(p));
    const isStacked = Object.values(scales).some(sc => sc && sc.stacked) || ds.some(d => d.stack);
    const dataSeries = ds.filter(d => !(d._ci || /CI|band|lower|upper/i.test(String(d.label || '')) && d.pointRadius === 0));
    res.info.type = type; res.info.nSeries = dataSeries.length; res.info.plugins = plugins;

    // numeric extent of plotted values (+ CI arrays inside plugin source if present)
    let vmin = Infinity, vmax = -Infinity, n = 0, hasNull = false;
    const vals = [];
    ds.forEach(d => (d.data || []).forEach(p => {
      let v = p; if (p && typeof p === 'object' && !Array.isArray(p)) v = horiz ? p.x : p.y;
      if (Array.isArray(p)) { p.forEach(q => { if (typeof q === 'number') { vmin = Math.min(vmin,q); vmax = Math.max(vmax,q); } }); return; }
      if (v === null || v === undefined) { hasNull = true; return; }
      if (typeof v === 'number' && isFinite(v)) { vmin = Math.min(vmin,v); vmax = Math.max(vmax,v); n++; vals.push(v); }
    }));
    (cfg.plugins || []).forEach(p => {
      const src = Object.values(p).filter(f => typeof f === 'function').map(f => f.toString()).join('\n');
      // CI arrays in our plugins are named lo/hi, loA/hiA, lom0/him0, lo2m1/hi2m1 ...
      const arr = src.match(/\b(?:lo|hi|lower|upper)\w*\s*=\s*\[([^\]]*)\]/g) || [];
      arr.forEach(a => a.replace(/^[^\[]*\[|\]$/g, '').split(',').forEach(t => { const x = parseFloat(t); if (isFinite(x)) { vmin = Math.min(vmin,x); vmax = Math.max(vmax,x); } }));
    });

    // AX-ZERO: bars must include zero
    const isBar = type === 'bar' || ds.some(d => d.type === 'bar');
    if (isBar && !isPostEst && !isDistrib) {
      const min = valueAxis.min, baz = valueAxis.beginAtZero;
      if ((typeof min === 'number' && min > 0) || baz === false) add('AX-ZERO', 'ERROR', tag + `bar value axis starts at ${min != null ? min : 'auto'} (beginAtZero=${baz}) -- bar lengths misrepresent values`, 'WIL19 ch.17');
    }
    // AX-CLIP / AX-WASTE
    if (typeof valueAxis.min === 'number' && typeof valueAxis.max === 'number' && isFinite(vmin) && !isDistrib && !isStacked) {
      const range = valueAxis.max - valueAxis.min, span = vmax - vmin;
      const cropRequested = /plotmargin=[^ >]*-/.test(html);
      if (vmin < valueAxis.min - 1e-9 || vmax > valueAxis.max + 1e-9) add('AX-CLIP', cropRequested ? 'WARN' : 'ERROR', tag + `data [${vmin.toFixed(3)}, ${vmax.toFixed(3)}] exceeds axis [${valueAxis.min}, ${valueAxis.max}]` + (cropRequested ? ' (requested via negative plotmargin)' : ''), 'WIL19');
      else if (range > 0 && span / range < 0.5) add('AX-WASTE', 'WARN', tag + `data uses ${(100*span/range).toFixed(0)}% of the value axis`, 'TUF01');
      res.info.axisUse = range > 0 ? +(span / range).toFixed(2) : null;
    }
    // AX-TITLE for estimate charts
    if (isPostEst || /ErrorBars|ciline|cibar/.test(type + html.slice(0, 400))) {
      const t = valueAxis.title && valueAxis.title.display && valueAxis.title.text;
      if (!t) add('AX-TITLE', 'WARN', tag + 'value axis of an estimate chart has no title (what quantity? which units?)', 'WIL19, SJ');
    }
    if (valueAxis.type === 'logarithmic' && isBar) add('AX-LOGBAR', 'INFO', tag + 'log scale on bars: bar lengths are no longer proportional', 'CM84');

    // aspect ratio + banking (line charts)
    const aspect = opts.aspectRatio || 2;
    res.info.aspect = aspect;
    if (aspect < 1.2 || aspect > 2.2) add('ASP-RANGE', 'INFO', tag + `aspect ratio ${aspect} outside 1.2-2.2`, 'CLV93');
    if (type === 'line' && !isPostEst) {
      const d0 = dataSeries[0] && dataSeries[0].data || [];
      const ys = d0.map(p => (p && typeof p === 'object') ? p.y : p).filter(v => typeof v === 'number');
      if (ys.length >= 4 && isFinite(vmin) && vmax > vmin) {
        let slopes = [];
        for (let i = 1; i < ys.length; i++) slopes.push(Math.abs((ys[i]-ys[i-1])/(vmax-vmin)) * (ys.length-1) / aspect);
        slopes.sort((a,b)=>a-b); const med = slopes[Math.floor(slopes.length/2)];
        if (med > 0 && (med < 0.3 || med > 3)) add('ASP-BANK', 'INFO', tag + `median segment slope ~${med.toFixed(2)} (45deg banking = 1); try aspect(${(aspect*med).toFixed(1)})`, 'CLV93');
      }
    }
    // category density
    if (!isRadial && !horiz && catAxis.type !== 'linear' && labels.length) {
      const width = isPostEst ? 800 : 1100;
      const maxLen = Math.max(...labels.map(l => String(l).length));
      const need = labels.length * (maxLen * 6.5 + 10);
      const rot = catAxis.ticks && (catAxis.ticks.maxRotation || catAxis.ticks.minRotation);
      if (need > width && !rot) add('CAT-DENSE', 'WARN', tag + `${labels.length} labels x ~${maxLen} chars need ~${Math.round(need)}px of ${width}px; use xtickangle() or a horizontal chart`, 'SCH21');
    }
    // CAT-SORT: nominal, unsorted bars
    if (isBar && !isPostEst && dataSeries.length === 1 && labels.length >= 4) {
      const nominal = labels.every(l => typeof l === 'string' && !/^\d+(\.\d+)?$/.test(l) && !/=|^(low|medium|high|q[1-5]|none|some|all)$/i.test(l));
      const v = (dataSeries[0].data || []).map(p => (p && typeof p === 'object') ? (horiz ? p.x : p.y) : p);
      if (nominal && v.every(x => typeof x === 'number')) {
        const asc = v.every((x,i) => i === 0 || x >= v[i-1]), desc = v.every((x,i) => i === 0 || x <= v[i-1]);
        if (!asc && !desc) add('CAT-SORT', 'INFO', tag + 'nominal categories are not sorted by value; sort unless the order is meaningful', 'WIL19 ch.6');
      }
    }
    // series count / pies
    if (dataSeries.length > 8) add('SER-MANY', 'WARN', tag + `${dataSeries.length} series on one panel`, 'FEW12, WIL19 ch.19');
    if (isRadial) {
      const slices = (ds[0] && ds[0].data || []).filter(v => typeof v === 'number');
      if (slices.length > 6) add('PIE-MANY', 'WARN', tag + `${slices.length} slices; angle judgements are inaccurate -- prefer bar`, 'CM84');
      const tot = slices.reduce((a,b)=>a+b,0); const small = slices.filter(s => tot && s/tot < 0.03).length;
      if (small) add('PIE-SMALL', 'INFO', tag + `${small} slice(s) below 3% of total`, 'CM84');
    }
    // legend
    const leg = opts.plugins && opts.plugins.legend; const legOn = !leg || leg.display !== false;
    const colorByCat = ds.length === 1 && Array.isArray(ds[0].backgroundColor) && ds[0].backgroundColor.length > 1;
    const inlineLegend = plugins.some(p => /Legend/i.test(p));
    if (dataSeries.length > 1 && !legOn && !inlineLegend && !isRadial) add('LEG-MISS', 'WARN', tag + `${dataSeries.length} series but legend hidden`, 'FEW12');
    if (dataSeries.length === 1 && legOn && !colorByCat && !isRadial && !isPostEst) add('LEG-REDUN', 'INFO', tag + 'single series with a legend (non-data ink)', 'TUF01, FEW12');
    // colours
    const thinMark = ['line','scatter','bubble','lineWithErrorBars'].includes(type) && !ds.some(d => d.fill === true || d.fill === 'origin');
    const cols = [];
    // colour of the MARK the reader must see: border for lines/points, fill for bars
    const dataSeriesForColour = dataSeries.filter(d => !/\((lfit|qfit|lowess|exp|log|power|ma)\)|CI \((upper|lower)\)/i.test(String(d.label || '')));
    dataSeriesForColour.forEach(d => { const c = Array.isArray(d.backgroundColor) ? d.backgroundColor : [d.backgroundColor]; const bc = Array.isArray(d.borderColor) ? d.borderColor : [d.borderColor]; const pick = thinMark ? (bc[0] || c[0]) : (c[0] || bc[0]); (colorByCat ? c : [pick]).forEach(x => { const p = parseColor(x); if (p) cols.push(blend(p, plotBg)); }); });
    const uniq = []; cols.forEach(c => { if (!uniq.some(u => u.join() === c.join())) uniq.push(c); });
    res.info.nColors = uniq.length;
    const bgThresh = thinMark ? 3 : 1.5;
    const lowBg = [], sim = [], cvd = [];
    if (!isRadial) for (let i = 0; i < uniq.length; i++) {
      const c = contrast(uniq[i], plotBg); if (c < bgThresh) lowBg.push(`rgb(${uniq[i]}) ${c.toFixed(2)}:1`);
      for (let j = i + 1; j < uniq.length; j++) {
        const d = dE76(uniq[i], uniq[j]);
        if (d < 20) sim.push(`${i}/${j} dE${d.toFixed(0)}`);
        else if (dE76(simDeutan(uniq[i]), simDeutan(uniq[j])) < 15) cvd.push(`${i}/${j}`);
      }
    }
    if (lowBg.length) add('COL-BG', 'WARN', tag + `${lowBg.length} series colour(s) below ${bgThresh}:1 against the plot background: ${lowBg.slice(0,3).join('; ')}`, 'WCAG 1.4.11');
    if (sim.length) add('COL-DIST', 'WARN', tag + `series colour pairs too similar (dE76 < 20): ${sim.slice(0,4).join(', ')}`, 'WIL19 ch.19');
    if (cvd.length) add('COL-CVD', 'WARN', tag + `colour pairs merge under deuteranopia: ${cvd.slice(0,4).join(', ')} -- consider theme(cblind1)`, 'OKI08, WCAG 1.4.1');
    // text contrast + font sizes
    const tickCol = (catAxis.ticks && catAxis.ticks.color) || (valueAxis.ticks && valueAxis.ticks.color);
    const tc = parseColor(tickCol); if (tc && contrast(blend(tc, plotBg), plotBg) < 4.5) add('TXT-CONTR', 'WARN', tag + `axis text contrast ${contrast(blend(tc, plotBg), plotBg).toFixed(2)}:1 (< 4.5:1)`, 'WCAG 1.4.3');
    const sizes = []; JSON.stringify(opts, (k, v) => { if (k === 'size' && typeof v === 'number') sizes.push(v); if (k === 'font' && v && typeof v.size === 'number') sizes.push(v.size); return v; });
    const small = sizes.filter(s => s < 11); if (small.length) add('TXT-SMALL', 'WARN', tag + `font size(s) ${[...new Set(small)].join(',')}px below 11px`, 'WCAG, SCH21');
    // grid
    const gcol = parseColor((catAxis.grid && catAxis.grid.color) || (valueAxis.grid && valueAxis.grid.color));
    if (gcol && gcol[3] > 0.25 && !(catAxis.grid && catAxis.grid.display === false)) add('GRID-HEAVY', 'INFO', tag + `gridline alpha ${gcol[3]} > 0.25`, 'TUF01');
    // CI / reference on estimate charts
    if (isPostEst) {
      const ciShown = plugins.some(p => /^(cp(M)?(Whisker|Band|Area|Inner)|cpBar(Whisker|Area)|mp(W|A)\d|mpW_L2)/.test(p)) || ds.some(d => /CI/i.test(String(d.label)));
      if (!ciShown && !/<!-- sparkta [^>]* noci/.test(html)) add('CI-MISS', 'WARN', tag + 'estimate chart shows no uncertainty (CI)', 'SJ');
      const ref = plugins.some(p => /Zero|Pex|mpZ/.test(p));
      if (!ref) add('REF-MISS', 'INFO', tag + 'no reference line at the null value', 'SJ');
      if (hasNull) {
        const tt = opts.plugins && opts.plugins.tooltip && opts.plugins.tooltip.callbacks && opts.plugins.tooltip.callbacks.label;
        const src = tt ? tt.toString() : '';
        if (!/estimable|omitted/.test(src)) add('NULL-TIP', 'INFO', tag + 'null points present but tooltip does not explain them', 'SCH21');
      }
    }
    // ---- encoding consistency (s8k): what the legend/key CLAIMS must match what is DRAWN
    const pl = (cfg.plugins || []);
    const src = (id) => { const p = pl.find(q => q.id === id); return p ? Object.values(p).filter(f => typeof f === 'function').map(f => f.toString()).join('\n') : ''; };
    const hasBand = plugins.some(p => /^mpA\d/.test(p)), hasWhisk = plugins.some(p => /^mpW\d/.test(p));
    const htmlKey = (html.match(/<div class='spk-shared-key spk-elements-key'[\s\S]*?<\/div>/) || [''])[0];
    const legSrc = src('mpLegend') || htmlKey;
    if (legSrc) {
      const claimsBand = /\((outer )?band( \+ whisker)?\)|% CI \(band\)|inner band/.test(legSrc);
      if (claimsBand !== hasBand) add('ENC-LEGEND', 'ERROR', tag + `CI key ${claimsBand ? 'shows a band' : 'shows whiskers'} for the outer CI but the chart draws ${hasBand ? 'a band' : 'whiskers'}`, 'VIZ_STANDARD: key must match encoding');
    }
    const innerBand = plugins.some(p => /^mpA_L2_/.test(p)), innerWhisk = plugins.some(p => /^mpW_L2_/.test(p));
    const overlay = hasBand && hasWhisk && innerBand && innerWhisk;   // ciwhiskers: both encodings on both levels
    if (!overlay && hasBand && innerWhisk && !innerBand) add('ENC-MIXED', 'ERROR', tag + 'outer CI is a band but the inner CI is a whisker; nested levels must share one encoding', 'VIZ_STANDARD');
    if (!overlay && hasWhisk && !hasBand && innerBand) add('ENC-MIXED', 'ERROR', tag + 'outer CI is a whisker but the inner CI is a band', 'VIZ_STANDARD');
    // t2c: the HTML elements key lists band and whisker as separate ci entries (a swatch and a whisker glyph)
    const keyItems = [...htmlKey.matchAll(/<span class='k' data-role='ci'[^>]*>([\s\S]*?)<\/span>/g)].map(m => m[1]);
    const keyHasBand = keyItems.some(t => /class='sw'|\(band\)/.test(t)), keyHasWhisk = keyItems.some(t => /<svg[^>]*>(?:[^<]*<line[^>]*>){3}/.test(t) || (!/band/.test(t) && /<svg/.test(t)));
    const keyBoth = keyHasBand && keyHasWhisk;
    if (legSrc && overlay && !/\+ whisker/.test(legSrc) && !keyBoth) add('ENC-LEGEND', 'ERROR', tag + 'bands and whiskers are both drawn but the key mentions only one', 'VIZ_STANDARD');
    // a CI ribbon (closed polygon) between categories that are not connected asserts a continuity the chart does not draw
    const showLine = ds.some(d => d.showLine === true || (type === 'line' && d.showLine !== false && d.borderWidth > 0));
    const numX = ((scales.x || {}).type === 'linear');
    pl.filter(p => /^mpA(\d|_L2_)/.test(p.id)).forEach(p => {
      const ps = src(p.id);
      if (/closePath\(\)/.test(ps) && !showLine && !numX) add('ENC-RIBBON', 'ERROR', tag + `${p.id}: CI ribbon across unconnected categories`, 'WIL19 ch.9');
    });
    // whisker + inner whisker must differ in width, else the two levels are indistinguishable
    const w0 = (src('mpW0').match(/lineWidth=([\d.]+)/) || [])[1], w2 = (src('mpW_L2_0').match(/lineWidth=([\d.]+)/) || [])[1];
    if (w0 && w2 && Math.abs(parseFloat(w0) - parseFloat(w2)) < 0.5) add('ENC-LEVELS', 'ERROR', tag + `outer and inner whiskers have the same width (${w0}px)`, 'VIZ_STANDARD');
    // s8r: ticks must be anchored to the data on post-est value axes: first tick <= data min, last >= data max
    if (isPostEst && isFinite(vmin) && valueAxis && typeof valueAxis.afterBuildTicks === 'function') {
      const tm = valueAxis.afterBuildTicks.toString().match(/for\(var v=([-\d.e]+);v<=([-\d.e]+);/);
      // t2j fix8w: marginsplot follows Stata -- the AXIS BOUNDS hug the data and the ticks are the
      // nice-step multiples inside them (ticks need not reach the extremes); coefplot/eventstudy
      // still anchor the ticks. Either form spans the data, so accept ticks OR bounds.
      const bMin = valueAxis && typeof valueAxis.min === 'number' ? valueAxis.min : NaN, bMax = valueAxis && typeof valueAxis.max === 'number' ? valueAxis.max : NaN;
      const boundsSpan = isFinite(bMin) && isFinite(bMax) && bMin <= vmin + 1e-9 && bMax >= vmax - 1e-9;
      if (tm && !boundsSpan) { const t0 = parseFloat(tm[1]), t1 = parseFloat(tm[2]); if (t0 > vmin + 1e-9 || t1 < vmax - 1e-9) add('AX-ANCHOR', 'WARN', tag + `ticks ${t0}..${t1} do not span the data ${vmin.toFixed(3)}..${vmax.toFixed(3)} and neither do the axis bounds`, 'VIZ_STANDARD (data-anchored ticks)'); }
    }
    // raw padded bounds shown as tick labels
    ['x','y'].forEach(ax => { const sc = scales[ax]; if (sc && typeof sc.min === 'number' && typeof sc.max === 'number' && !(sc.ticks && sc.ticks.includeBounds === false) && isPostEst) add('AX-BOUNDS', 'INFO', tag + `${ax} axis will label its padded bounds (${sc.min}, ${sc.max}) as ticks`, 'TUF01'); });

    // s9f: a log axis must show at least 3 tick labels (narrow ranges lost all labels)
    ['x','y'].forEach(ax => { const sc = scales[ax]; if (sc && sc.type === 'logarithmic' && sc.ticks && typeof sc.ticks.callback === 'function' && typeof sc.min === 'number') {
      const mn = sc.min, mx = typeof sc.max === 'number' ? sc.max : (ax === (horiz ? 'x' : 'y') ? vmax : mn * 1000);
      const ticks = []; for (let e = Math.floor(Math.log10(mn)); e <= Math.ceil(Math.log10(mx)); e++) for (const m of [1,2,3,4,5,6,7,8,9]) { const v = m * Math.pow(10, e); if (v >= mn && v <= mx) ticks.push({ value: v }); }
      let labelled = 0; try { ticks.forEach((tk, i) => { if (sc.ticks.callback(tk.value, i, ticks)) labelled++; }); } catch (e) {}
      if (ticks.length && labelled < 3) add('AX-LOGTICKS', 'WARN', tag + `${ax} log axis labels only ${labelled} tick(s) between ${mn} and ${mx}`, 'WIL19'); } });
    // s9d: data labels above bars need headroom, else the top label hits the legend (user screenshot)
    const dlOn = opts.plugins && opts.plugins.datalabels && opts.plugins.datalabels.display !== false;
    if (dlOn && (isBar || type === 'line') && !isPostEst && !isStacked) {
      const hasRoom = (valueAxis.grace !== undefined) || (typeof valueAxis.max === 'number' && isFinite(vmax) && valueAxis.max > vmax * 1.05);
      if (!hasRoom) add('AX-HEADROOM', 'WARN', tag + 'data labels are on but the value axis has no headroom above the tallest bar', 'SCH21');
    }
    // clutter
    const dl = opts.plugins && opts.plugins.datalabels; if (dl && dl.display !== false && n > 20) add('CLUTTER', 'INFO', tag + `data labels on ${n} points`, 'TUF01, SCH21');
  });
  if (!res.info.title) add('TITLE', 'INFO', 'chart has no title', 'SCH21');
  const kb = Buffer.byteLength(html); if (kb > 2 * 1024 * 1024) add('SIZE-HTML', 'INFO', `HTML is ${(kb/1048576).toFixed(1)} MB`, '--');
  return finish(res);
}
function finish(res) {
  const w = { ERROR: 25, WARN: 8, INFO: 2 };
  res.score = Math.max(0, 100 - res.findings.reduce((s, f) => s + w[f.sev], 0));
  res.grade = res.score >= 90 ? 'A' : res.score >= 75 ? 'B' : res.score >= 60 ? 'C' : 'D';
  return res;
}

// ------------------------------------------------------------- main
const files = fs.readdirSync(dir).filter(f => f.toLowerCase().endsWith('.html')).sort();
if (!files.length) { console.error('no html in ' + dir); process.exit(2); }
const results = files.map(f => audit(path.join(dir, f)));
const pad = (s, n) => (String(s) + ' '.repeat(n)).slice(0, n);
console.log(pad('FILE', 34) + pad('TYPE', 9) + pad('SER', 4) + pad('AXIS%', 6) + pad('SCORE', 6) + pad('G', 2) + 'FINDINGS');
console.log('-'.repeat(110));
let nErr = 0; const tally = {};
results.forEach(r => {
  const ids = r.findings.map(f => f.id + (f.sev === 'ERROR' ? '!' : f.sev === 'WARN' ? '*' : '')).join(' ');
  console.log(pad(r.file, 34) + pad(r.info.type || '-', 9) + pad(r.info.nSeries == null ? '-' : r.info.nSeries, 4) + pad(r.info.axisUse == null ? '-' : Math.round(r.info.axisUse * 100), 6) + pad(r.score, 6) + pad(r.grade, 2) + ids);
  r.findings.forEach(f => { if (f.sev === 'ERROR') nErr++; tally[f.id] = (tally[f.id] || 0) + 1; });
});
console.log('-'.repeat(110));
const avg = results.reduce((s, r) => s + r.score, 0) / results.length;
console.log(`${results.length} files, mean score ${avg.toFixed(1)}, ERROR-level findings: ${nErr}`);
console.log('most frequent: ' + Object.entries(tally).sort((a,b)=>b[1]-a[1]).slice(0,8).map(([k,v]) => `${k} x${v}`).join(', '));
console.log('legend: ! = ERROR, * = WARN, plain = INFO. Rules: verify/VIZ_STANDARD.md');
fs.writeFileSync(path.join(dir, '_viz_audit_report.json'), JSON.stringify(results, null, 1));
process.exit(strict && nErr ? 1 : 0);
