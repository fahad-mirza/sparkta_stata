/*
 * byfilter_probe.js -- v3.6.0-t2j fix9a: drive every filters() dropdown value on the
 * HarnessByFilter pages (by() panels) and compare each panel's datasets against an
 * INDEPENDENT recomputation from the SFI fixture (verify/sfi-stub/Data.java rows).
 *
 * Usage:  node verify/byfilter_probe.js <dir> [--verbose] [--embedded] [--glob <regex>]
 *   default    : harness pages (bf*.html); truth = the fixture rows below
 *   --embedded : Stata-made pages (test_byfilter_v360.do -> r_bf_*.html); truth = the page's
 *                own embedded columns (_sAgg.column) + index arrays, aggregated here
 *                independently of the page's filter code
 * Reports, per page x dropdown value: JS errors, dataset shape drift, wrong numbers,
 * stale panel n badges. Exit 1 on any FAIL. ASCII only. verify/ only.
 */
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');

// Fixture copy (must match verify/sfi-stub/com/stata/sfi/Data.java ROWS)
const NA = null;
const ROWS = [
  [4099,22,2930,0,3],[4749,17,3350,0,3],[3799,22,2640,0,NA],[4816,20,3250,0,3],[7827,15,4080,0,4],
  [5788,18,3670,0,3],[4453,26,2230,0,NA],[5189,20,3280,0,3],[10372,16,3880,0,3],[4082,19,3400,0,3],
  [11385,14,4330,0,3],[14500,14,3900,0,2],[15906,21,4290,0,3],[3299,29,2110,0,3],[5705,16,3690,0,4],
  [4504,22,3180,0,3],[5104,22,3220,0,2],[3667,24,2750,0,1],[3955,19,3430,0,4],[3984,30,2120,0,1],
  [9690,17,2830,1,5],[6295,23,2070,1,4],[9735,25,2200,1,5],[6229,23,2130,1,4],[4589,35,2050,1,4],
  [5079,25,2240,1,4],[8129,21,2750,1,5],[4296,25,2650,1,4],[5799,18,2410,1,3],[4499,28,2350,1,4]
];
const COL = { price: 0, mpg: 1, weight: 2, foreign: 3, rep78: 4 };
const val = (r, v) => ROWS[r][COL[v]];

function stat(vals, s) {
  const a = vals.filter(v => v !== null && v !== undefined).slice().sort((x, y) => x - y);
  const n = a.length; if (n === 0) return null;
  const sum = a.reduce((p, c) => p + c, 0);
  if (s === 'sum') return sum;
  if (s === 'count') return n;
  if (s === 'min') return a[0];
  if (s === 'max') return a[n - 1];
  if (s === 'median') return pct(a, 50);
  return sum / n;
}
function pct(a, p) { // Stata default percentile
  const n = a.length, i = n * p / 100, fi = Math.floor(i);
  if (Math.abs(i - fi) < 1e-9) { const k = fi; if (k < 1) return a[0]; if (k >= n) return a[n - 1]; return (a[k - 1] + a[k]) / 2; }
  let c = Math.ceil(i); if (c < 1) c = 1; if (c > n) c = n; return a[c - 1];
}
const near = (a, b, tol) => (a === null && b === null) || (a !== null && b !== null && Math.abs(a - b) <= (tol || 1e-6) * Math.max(1, Math.abs(b)));
function multisetEq(got, exp, tol) {
  const g = got.filter(v => v !== null && v !== undefined && !Number.isNaN(v)).slice().sort((x, y) => x - y);
  const e = exp.filter(v => v !== null).slice().sort((x, y) => x - y);
  if (g.length !== e.length) return { ok: false, why: 'count ' + g.length + ' vs expected ' + e.length };
  for (let i = 0; i < g.length; i++) if (!near(g[i], e[i], tol)) return { ok: false, why: 'value ' + g[i] + ' vs expected ' + e[i] };
  return { ok: true };
}
// label text -> matcher over a fixture column
function catMatcher(varName, text) {
  if (text === '(Missing)' || text === 'Missing' || text === '.') return r => val(r, varName) === null;
  const num = parseFloat(text);
  if (!Number.isNaN(num)) return r => val(r, varName) === num;
  const lab = { Domestic: 0, Foreign: 1 };
  if (lab[text] !== undefined) return r => val(r, varName) === lab[text];
  return null;
}

async function probePage(browser, file, verbose, embedded) {
  const page = await browser.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push('pageerror: ' + e.message));
  page.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  await page.goto('file://' + file, { waitUntil: 'load' });
  await page.waitForTimeout(600);

  const meta = await page.evaluate(() => {
    const m = window._smeta || {};
    const sel = Array.from(document.querySelectorAll("select[id^='fsel_']")).map(s => ({
      id: s.id, name: s.id.slice(5),
      options: Array.from(s.options).map(o => ({ value: o.value, text: o.textContent.trim() }))
    }));
    const panels = [];
    for (let i = 0; i < 40; i++) { if (!document.getElementById('chart_by_' + i)) break; panels.push(i); }
    const sliders = (m.sliders || []).map(s => ({ name: s.name, min: s.min, max: s.max }));
    return { type: m.chartType, plotVars: m.plotVars || [], overVar: m.overVar || null, byVar: m.byVar || null,
             byGroups: m.byGroups || [], stat: m.stat || 'mean', overLabels: m.overLabels || [],
             histBins: m.histBins || null, histType: m.histType || null, selects: sel, panels: panels, sliders: sliders,
             stack100: !!(m.stack100) };
  });
  const snap = () => page.evaluate(() => {
    const out = [];
    for (let i = 0; i < 40; i++) {
      const cv = document.getElementById('chart_by_' + i); if (!cv) break;
      const ch = (window.Chart && Chart.getChart) ? Chart.getChart(cv) : null;
      const wrap = cv.closest ? cv.closest('.chart-wrapper') : null;
      const nEl = wrap ? wrap.querySelector('.panel-n') : null;
      let dsets = [];
      if (ch && ch.data && ch.data.datasets) {
        dsets = ch.data.datasets.map(d => ({
          label: d.label, type: d.type || null, len: (d.data || []).length,
          data: (d.data || []).map(v => (v === null || v === undefined) ? null
                 : (typeof v === 'number') ? v
                 : (typeof v === 'object') ? { x: v.x, y: v.y, median: v.median, yMin: v.yMin, yMax: v.yMax, r: v.r } : String(v))
        }));
      }
      // fix9d: violin entries the chart is animating to (gi = global over index or -1, vi = plot var index)
      const vt = window['_vTarget_' + i]; let vio = null;
      if (typeof vt === 'function') { const arr = vt() || []; vio = arr.map(e => ({ label: e.label, gi: (e.gi != null ? e.gi : -1), vi: (e.vi != null ? e.vi : 0), n: e.n, median: e.median, mean: e.mean })); }
      out.push({ i: i, hasChart: !!ch, dsets: dsets, nBadge: nEl ? nEl.textContent : null, edges: window['_binEdges_chart_by_' + i] || null, labels: (ch && ch.data) ? ch.data.labels : [], violin: vio });
    }
    const nObs = document.getElementById('_nObs');
    return { panels: out, nObs: nObs ? nObs.textContent : null };
  });

  meta.stack100 = meta.stack100 || (/stack(h)?100/.test(path.basename(file)) && !!meta.overVar);   // no-over stack100 renders as plain aggregates
  // ---- truth source -------------------------------------------------------------------
  // T.n rows; T.val(r, v); T.optPred(fvar, optValue, optText); T.panelPred(i); T.groupPred(g)
  let T;
  if (embedded) {
    const emb = await page.evaluate((vars) => {
      const m = window._smeta || {}; const cols = {};
      vars.forEach(v => { const c = (window._sAgg && _sAgg.column) ? _sAgg.column(v) : null; cols[v] = c ? Array.from(c).map(x => (x === null || x === undefined || (typeof x === 'number' && isNaN(x))) ? null : x) : null; });
      const si = {}; Object.keys(window._si || {}).forEach(k => { si[k] = Array.from(window._si[k]); });
      const sc = {}; Object.keys(window._sc || {}).forEach(k => { sc[k] = Array.from(window._sc[k]).map(String); });
      return { n: m.nObs || 0, cols: cols, si: si, sc: sc };
    }, Array.from(new Set([].concat(meta.plotVars, meta.overVar ? [meta.overVar] : [], meta.byVar ? [meta.byVar] : [], meta.selects.map(x => x.name), meta.sliders.map(x => x.name)))));
    const markoutEmb = false;
    T = {
      n: emb.n, markout: markoutEmb,
      val: (r, v) => (emb.cols[v] ? emb.cols[v][r] : null),
      optPred: (fvar, optValue, optText) => { const k = parseInt(optValue, 10); if (Number.isNaN(k)) return null; return r => emb.si[fvar] && emb.si[fvar][r] === k; },
      panelPred: i => { const bv = meta.byVar; if (!bv) return () => true; const lbl = String(meta.byGroups[i]); return r => { const k = emb.si[bv] ? emb.si[bv][r] : undefined; return k !== undefined && k >= 0 && emb.sc[bv][k] === lbl; }; },
      groupPred: g => { const ov = meta.overVar; return r => emb.si[ov] && emb.si[ov][r] === g; }
    };
  } else {
    const markout = !/_sm\.html$/.test(file);   // the harness emulates the ado markout except on the showmissing page
    T = {
      n: ROWS.length, markout: markout,
      val: val,
      optPred: (fvar, optValue, optText) => catMatcher(fvar, optText),
      panelPred: i => meta.byVar ? catMatcher(meta.byVar, String(meta.byGroups[i])) : (() => true),
      groupPred: g => catMatcher(meta.overVar, String(meta.overLabels[g]))
    };
  }
  const fails = [];
  const base = await snap();
  if (errors.length) fails.push('load: ' + errors.join(' | '));
  if (!meta.byGroups.length) fails.push('no _smeta.byGroups');
  base.panels.forEach(p => { if (!p.hasChart) fails.push('panel ' + p.i + ' has no Chart instance'); });

  // panel -> row predicate from byGroups label
  const panelPred = meta.byGroups.map((lbl, i) => T.panelPred(i));

  // selections to drive: every option of every select (one select at a time), plus a slider case
  const drives = [];
  for (const s of meta.selects) for (const o of s.options) drives.push({ kind: 'sel', select: s, option: o });
  for (const sl of meta.sliders) drives.push({ kind: 'slider', slider: sl });

  for (const d of drives) {
    errors.length = 0;
    let rowPred, tag;
    if (d.kind === 'sel') {
      // reset all selects to All, then set this one
      await page.evaluate(({ sid, v }) => {
        document.querySelectorAll("select[id^='fsel_']").forEach(s => { if (s.querySelector("option[value='__ALL__']")) s.value = '__ALL__'; });
        const s = document.getElementById(sid); s.value = v; try { if (typeof _onFilterChange === 'function') _onFilterChange(); } catch (e) { window.__spkErr = 'thrown in _onFilterChange: ' + e.message; }
      }, { sid: d.select.id, v: d.option.value });
      const m = d.option.value === '__ALL__' ? (() => true) : T.optPred(d.select.name, d.option.value, d.option.text);
      if (!m) { fails.push('cannot interpret option "' + d.option.text + '" of ' + d.select.name); continue; }
      rowPred = m; tag = d.select.name + '=' + d.option.text;
    } else {
      const lo = d.slider.min + (d.slider.max - d.slider.min) * 0.3, hi = d.slider.min + (d.slider.max - d.slider.min) * 0.7;
      await page.evaluate(({ sn, lo, hi }) => {
        document.querySelectorAll("select[id^='fsel_']").forEach(s => { if (s.querySelector("option[value='__ALL__']")) s.value = '__ALL__'; });
        const l = document.getElementById('slo_' + sn), h = document.getElementById('shi_' + sn);
        l.value = lo; h.value = hi; try { if (typeof _onFilterChange === 'function') _onFilterChange(); } catch (e) { window.__spkErr = 'thrown in _onFilterChange: ' + e.message; }
      }, { sn: d.slider.name, lo: lo, hi: hi });
      rowPred = r => { const v = T.val(r, d.slider.name); return v !== null && v >= lo && v <= hi; };
      tag = 'slider ' + d.slider.name + ' [' + lo.toFixed(0) + ',' + hi.toFixed(0) + ']';
    }
    await page.waitForTimeout(150);
    const thrown = await page.evaluate(() => { const e = window.__spkErr; window.__spkErr = null; return e || null; });
    if (thrown) errors.push(thrown);
    const cur = await snap();
    if (errors.length) { fails.push(tag + ': JS ' + errors.join(' | ')); continue; }

    const allRows = []; for (let r = 0; r < T.n; r++) if (rowPred(r) && !(T.markout && T.val(r, 'rep78') === null)) allRows.push(r);
    if (cur.nObs !== null && cur.nObs !== allRows.length + ' obs') fails.push(tag + ': _nObs "' + cur.nObs + '" expected "' + allRows.length + ' obs"');

    for (const p of cur.panels) {
      const b = base.panels[p.i];
      const pred = panelPred[p.i];
      if (!pred) { fails.push(tag + ': panel ' + p.i + ' byGroup "' + meta.byGroups[p.i] + '" not interpretable'); continue; }
      const R = allRows.filter(pred);
      const pt = 'panel ' + p.i + ' (' + meta.byGroups[p.i] + ')';
      if (p.nBadge !== null && p.nBadge.replace(/\s+/g, '') !== ('n=' + R.length)) fails.push(tag + ' ' + pt + ': badge "' + p.nBadge + '" expected n = ' + R.length);
      const t = meta.type;
      const isViolin = t === 'violin' || t === 'hviolinplot';
      const isPoints = t === 'scatter' || t === 'bubble';
      if (!isViolin) {
        if (p.dsets.length !== b.dsets.length) { fails.push(tag + ' ' + pt + ': dataset count ' + p.dsets.length + ' was ' + b.dsets.length); continue; }
        if (!isPoints) for (let k = 0; k < p.dsets.length; k++) if (p.dsets[k].len !== b.dsets[k].len) { fails.push(tag + ' ' + pt + ': dataset ' + k + ' "' + p.dsets[k].label + '" length ' + p.dsets[k].len + ' was ' + b.dsets[k].len); }
      }
      // over-groups: from overLabels (label text -> rows)
      const groups = meta.overVar ? meta.overLabels.map((l, g) => ({ lbl: l, m: T.groupPred(g) })) : [{ lbl: null, m: () => true }];
      if (groups.some(g => !g.m)) { fails.push(tag + ' ' + pt + ': over label not interpretable'); continue; }
      const groupRows = groups.map(g => R.filter(g.m));

      if (isViolin) {
        // fix9d: EVERY violin entry (group x variable) must carry the filtered n / median / mean
        // of its own rows -- before fix9d only the first plot variable was recomputed (BF40)
        if (!p.violin) { fails.push(tag + ' ' + pt + ': no _vTarget_' + p.i + ' (violin entries not exposed)'); continue; }
        if (p.violin.length !== b.violin.length) { fails.push(tag + ' ' + pt + ': violin count ' + p.violin.length + ' was ' + b.violin.length); continue; }
        p.violin.forEach((e, k) => {
          const rows = e.gi >= 0 ? (groupRows[e.gi] || []) : R;
          const v = meta.plotVars[e.vi] || meta.plotVars[0];
          const vals = rows.map(r => T.val(r, v)).filter(x => x !== null).sort((x, y) => x - y);
          const en = vals.length, emed = en ? pct(vals, 50) : null, emean = en ? stat(vals, 'mean') : null;
          if ((e.n || 0) !== en) fails.push(tag + ' ' + pt + ': violin ' + k + ' "' + e.label + '" n ' + e.n + ' expected ' + en);
          else if (en && (!near(e.median, emed) || !near(e.mean, emean))) fails.push(tag + ' ' + pt + ': violin ' + k + ' "' + e.label + '" median/mean ' + e.median + '/' + e.mean + ' expected ' + emed + '/' + emean);
        });
      }
      if (['bar', 'hbar', 'line', 'area'].indexOf(t) >= 0 && !meta.stack100 && !isViolin) {
        const exp = [];
        for (const v of meta.plotVars) for (const gr of groupRows) exp.push(stat(gr.map(r => T.val(r, v)), meta.stat));
        const got = []; p.dsets.forEach(ds => ds.data.forEach(x => { if (typeof x === 'number') got.push(x); else if (x && typeof x === 'object' && typeof x.y === 'number') got.push(x.y); }));
        const c = multisetEq(got, exp, 1e-6); if (!c.ok) fails.push(tag + ' ' + pt + ': values ' + c.why);
      } else if (meta.stack100) {
        // every category column sums to 100 (or is empty)
        const len = p.dsets.length ? p.dsets[0].len : 0;
        for (let j = 0; j < len; j++) {
          let s = 0, any = false; p.dsets.forEach(ds => { const x = ds.data[j]; const y = (typeof x === 'number') ? x : (x && x.y); if (typeof y === 'number') { s += y; any = true; } });
          if (any && Math.abs(s - 100) > 1e-6 && Math.abs(s - 1) > 1e-6) fails.push(tag + ' ' + pt + ': stack100 column ' + j + ' sums to ' + s.toFixed(3));
        }
        // shares: for each var (column) the panel's shown groups (dataset labels) split 100
        const exp = [];
        if (meta.overVar) {
          const shown = p.dsets.map(ds => String(ds.label));
          const gRows = groups.filter(g => shown.indexOf(String(g.lbl)) >= 0).map(g => R.filter(g.m));
          meta.plotVars.forEach(v => { const st = gRows.map(gr => stat(gr.map(r => T.val(r, v)), meta.stat)); const tot = st.reduce((a, c) => a + (c === null ? 0 : Math.abs(c)), 0); st.forEach(x => exp.push(x === null ? null : (tot > 0 ? 100 * x / tot : 0))); });
        } else {
          const st = meta.plotVars.map(v => stat(R.map(r => T.val(r, v)), meta.stat)); const tot = st.reduce((a, c) => a + (c === null ? 0 : Math.abs(c)), 0) || 1;
          st.forEach(x => exp.push(x === null ? null : 100 * x / tot));
        }
        const got = []; p.dsets.forEach(ds => ds.data.forEach(x => { const y = (typeof x === 'number') ? x : (x && x.y); if (typeof y === 'number') got.push(y); }));
        const c = multisetEq(got, exp, 1e-4); if (!c.ok) fails.push(tag + ' ' + pt + ': stack100 shares ' + c.why);
      } else if (t === 'scatter' || t === 'bubble') {
        const xv = meta.plotVars[1], yv = meta.plotVars[0];
        const expN = R.filter(r => T.val(r, xv) !== null && T.val(r, yv) !== null).length;
        let gotN = 0; p.dsets.forEach(ds => { if (ds.type === 'line') return; ds.data.forEach(x => { if (x && typeof x === 'object' && typeof x.x === 'number' && typeof x.y === 'number' && (x.r === undefined || x.r !== null)) gotN++; }); });
        // fit line datasets are type 'line' and excluded above; scatter datasets carry no type or 'scatter'/'bubble'
        const scatterDs = p.dsets.filter(ds => ds.type !== 'line');
        gotN = 0; scatterDs.forEach(ds => ds.data.forEach(x => { if (x && typeof x === 'object' && typeof x.x === 'number') gotN++; }));
        if (gotN !== expN) fails.push(tag + ' ' + pt + ': ' + gotN + ' points, expected ' + expN);
      } else if (t === 'pie' || t === 'donut') {
        const sums = meta.plotVars.map(v => stat(R.map(r => T.val(r, v)), 'sum'));
        const tot = sums.reduce((a, c) => a + (c || 0), 0);
        const exp = R.length === 0 ? meta.plotVars.map(() => 0) : (meta.stat === 'sum' ? sums : sums.map(s => tot > 0 ? 100 * s / tot : null));
        const got = (p.dsets[0] ? p.dsets[0].data : []).map(x => (typeof x === 'number') ? x : null);
        const c = multisetEq(got, exp, 1e-4); if (!c.ok) fails.push(tag + ' ' + pt + ': slices ' + c.why);
      } else if (t === 'cibar' || t === 'ciline') {
        const exp = []; meta.plotVars.forEach(pv => groupRows.forEach(gr => { const vals = gr.map(r => T.val(r, pv)).filter(v => v !== null); exp.push(vals.length >= 2 ? stat(vals, 'mean') : null); }));
        let got = [];
        if (t === 'cibar') p.dsets.forEach(ds => ds.data.forEach(x => got.push((x && typeof x === 'object') ? (typeof x.y === 'number' ? x.y : null) : (typeof x === 'number' ? x : null))));
        else { const stride = Math.round(p.dsets.length / meta.plotVars.length) || 3; for (let vi = 0; vi < meta.plotVars.length; vi++) { const ds0 = p.dsets[vi * stride]; if (ds0) ds0.data.forEach(x => got.push((typeof x === 'number') ? x : (x && typeof x.y === 'number') ? x.y : null)); } }
        const c = multisetEq(got, exp, 1e-6); if (!c.ok) fails.push(tag + ' ' + pt + ': means ' + c.why);
      } else if (t === 'histogram') {
        const vals = R.map(r => T.val(r, meta.plotVars[0])).filter(v => v !== null);
        const got = (p.dsets[0] ? p.dsets[0].data : []).map(x => (typeof x === 'number') ? x : (x && typeof x.y === 'number') ? x.y : 0);
        const edges = p.edges;
        if (edges && edges.length >= 2) {
          const counts = new Array(edges.length - 1).fill(0);
          // same binning as the render and the filter (floor over an equal width, clamped):
          // an edge-by-edge scan puts a value that sits on an edge in the other bin (nlsw88 wage)
          const w = (edges[edges.length - 1] - edges[0]) / counts.length, n = vals.length;
          vals.forEach(v => { let k = Math.floor((v - edges[0]) / w); if (k < 0) k = 0; if (k >= counts.length) k = counts.length - 1; counts[k]++; });
          const exp = counts.map(c => meta.histType === 'frequency' ? c : meta.histType === 'fraction' ? (n ? c / n : 0) : (n ? c / (n * w) : 0));
          let bad = null; for (let j = 0; j < exp.length; j++) if (!near(got[j] || 0, exp[j], 1e-4)) { bad = 'bin ' + j + ' ' + got[j] + ' vs ' + exp[j]; break; }
          if (got.length !== exp.length) bad = 'bins ' + got.length + ' vs ' + exp.length;
          if (bad) fails.push(tag + ' ' + pt + ': histogram ' + bad);
        } else {
          const s = got.reduce((a, c) => a + c, 0);
          if ((vals.length === 0) !== (s === 0)) fails.push(tag + ' ' + pt + ': histogram mass ' + s + ' for n=' + vals.length);
        }
      } else if (t === 'boxplot' || t === 'hboxplot') {
        const exp = []; for (const v of meta.plotVars) for (const gr of groupRows) { const vals = gr.map(r => T.val(r, v)).filter(x => x !== null); exp.push(vals.length ? stat(vals, 'median') : null); }
        const got = []; p.dsets.forEach(ds => ds.data.forEach(x => { if (x && typeof x === 'object' && typeof x.median === 'number') got.push(x.median); }));
        const c = multisetEq(got, exp, 1e-6); if (!c.ok) fails.push(tag + ' ' + pt + ': box medians ' + c.why);
      }
    }
  }
  await page.close();
  return { fails, meta };
}

(async () => {
  const dir = process.argv[2]; const verbose = process.argv.includes('--verbose');
  const embedded = process.argv.includes('--embedded');
  const gi = process.argv.indexOf('--glob');
  const pat = gi >= 0 ? new RegExp(process.argv[gi + 1]) : (embedded ? /^r_bf_.*\.html$/ : /^bf\d+.*\.html$/);
  const files = fs.readdirSync(dir).filter(f => pat.test(f)).sort();
  const browser = await chromium.launch(process.env.SPK_CHROME ? { executablePath: process.env.SPK_CHROME } : {});
  let nFail = 0;
  for (const f of files) {
    const { fails, meta } = await probePage(browser, path.join(dir, f), verbose, embedded);
    const uniq = Array.from(new Set(fails));
    const head = (uniq.length ? 'FAIL ' : 'ok   ') + f + '  [' + meta.type + (meta.overVar ? ' over ' + meta.overVar : '') + ' by ' + meta.byVar + '; filters ' + meta.selects.map(s => s.name).join(',') + (meta.sliders.length ? '; sliders ' + meta.sliders.map(s => s.name).join(',') : '') + ']';
    console.log(head);
    const show = verbose ? uniq : uniq.slice(0, 4);
    show.forEach(x => console.log('      ' + x));
    if (!verbose && uniq.length > 4) console.log('      ... ' + (uniq.length - 4) + ' more');
    if (uniq.length) nFail++;
  }
  await browser.close();
  console.log(nFail + ' of ' + files.length + ' pages FAIL');
  process.exit(nFail ? 1 : 0);
})();
