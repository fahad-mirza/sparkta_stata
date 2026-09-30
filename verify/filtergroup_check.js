#!/usr/bin/env node
/*
 * filtergroup_check.js -- verify/filtergroup_check.js (v3.6.0-t2j)
 *
 * Guards F1: the filtered over-group matching for cibar / ciline / boxplot /
 * violin must group rows by CATEGORY INDEX via _si[overVar] (the same path
 * _sAgg.aggregate uses for the initial render), NOT by string-comparing a raw
 * _sd numeric code to the (value-labeled / relabeled) group label.
 *
 * With a value-labeled over var (foreign: 0=Domestic, 1=Foreign), _sd holds
 * the codes {0,1} while overLabels holds {"Domestic","Foreign"}. The old
 * raw-_sd compare (sdz(0) === "Domestic") matched NOTHING, so every over-group
 * went empty on filter (blank chart). This check loads the REAL embedded data
 * from the rendered over-foreign+filter pages and asserts:
 *   - NEW grouping (_si index)  -> >= 2 groups populated (n > 0)   [the fix]
 *   - OLD grouping (raw _sd)    ->    0 groups populated           [the bug]
 * If the old grouping ever populates a group here the test is toothless (the
 * fixture would no longer reproduce the bug), which is also failed.
 *
 * Usage: node verify/filtergroup_check.js <harness_out_dir>
 */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');

function sdz(v) { const s = String(v); return s.endsWith('.0') ? s.slice(0, -2) : s; }

// Pull _sd/_si/_sc/_smeta out of a rendered page by running ONLY the data
// script(s) in a permissive sandbox with a no-op Chart. The data block is a
// plain-JSON assignment for small N (the harness fixture is N=30 < 200).
function extractData(html) {
  const scripts = [];
  const re = /<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi;
  let m; while ((m = re.exec(html)) !== null) scripts.push(m[1]);
  const isLib = src => /^\s*\/\*[!*]/.test(src) ||
    /^\s*!function\([a-z],[a-z]\)\{"object"==typeof exports/.test(src);
  const sandbox = {
    window: {}, document: { getElementById: () => null, addEventListener() {}, querySelectorAll: () => [], querySelector: () => null },
    console: { log() {}, warn() {}, error() {} }, setTimeout: () => {}, clearTimeout() {},
    requestAnimationFrame: () => {}, Math, JSON, Object, Array, String, Number, Boolean, Date, RegExp,
    parseFloat, parseInt, isNaN, isFinite, atob: (b) => Buffer.from(b, 'base64').toString('binary'),
    Float64Array, Uint8Array, ArrayBuffer,
    Chart: function () { this.update = () => {}; this.destroy = () => {}; },
  };
  sandbox.window = sandbox; sandbox.self = sandbox; sandbox.globalThis = sandbox;
  sandbox.Chart.getChart = () => undefined; sandbox.Chart.register = () => {};
  sandbox.Chart.defaults = { plugins: { legend: { labels: {} } }, font: {}, color: '#000' };
  vm.createContext(sandbox);
  for (let si = 0; si < scripts.length; si++) {
    if (isLib(scripts[si])) continue;
    try { vm.runInContext(scripts[si], sandbox, { timeout: 5000 }); }
    catch (e) { /* later chart scripts may throw in this bare sandbox; data assigns run first */ }
  }
  return { _sd: sandbox._sd, _si: sandbox._si, _sc: sandbox._sc, _smeta: sandbox._smeta };
}

function checkPage(file) {
  const html = fs.readFileSync(file, 'utf8');
  const d = extractData(html);
  const errs = [];
  if (!d._smeta) { return ['no _smeta embedded']; }
  const ov = d._smeta.overVar;
  const labels = d._smeta.overLabels || [];
  if (!ov) return ['no overVar (scenario mis-specified)'];
  if (labels.length < 2) return ['overLabels has < 2 groups: ' + JSON.stringify(labels)];
  const si = d._si && d._si[ov];
  const sd = d._sd && d._sd[ov];
  if (!si) errs.push('_si[' + ov + '] missing');
  if (!sd) errs.push('_sd[' + ov + '] missing');
  if (errs.length) return errs;

  const nRows = si.length;
  // NEW: index grouping (the fix)
  let newPop = 0;
  for (let gi = 0; gi < labels.length; gi++) {
    let n = 0;
    for (let r = 0; r < nRows; r++) if (si[r] === gi) n++;
    if (n > 0) newPop++;
  }
  // OLD: raw _sd code vs label string (the bug)
  let oldPop = 0;
  for (let gi = 0; gi < labels.length; gi++) {
    let n = 0;
    for (let r = 0; r < nRows; r++) {
      const v = sd[r];
      if (v !== null && v !== undefined && sdz(v) === labels[gi]) n++;
    }
    if (n > 0) oldPop++;
  }
  if (newPop < 2) errs.push(`_si grouping populated only ${newPop} group(s) (expected >= 2) -- fix not effective`);
  if (oldPop !== 0) errs.push(`raw-_sd grouping populated ${oldPop} group(s) -- fixture no longer reproduces the bug (toothless)`);
  if (!errs.length) console.log(`  ${path.basename(file)}: _si grouping ${newPop}/${labels.length} groups populated; raw-_sd grouping 0 (bug reproduced+fixed)`);
  return errs;
}

const dir = process.argv[2];
let fail = 0, checked = 0;
if (dir) {
  const targets = fs.readdirSync(dir).filter(f => /_lblover_filter\.html$/.test(f)).sort();
  for (const f of targets) {
    checked++;
    const errs = checkPage(path.join(dir, f));
    for (const e of errs) { console.log('  FAIL ' + f + ': ' + e); fail++; }
  }
}
if (checked === 0) console.log('  (no *_lblover_filter.html pages found -- F1 scenarios not rendered)');
console.log('filtergroup_check: ' + (fail === 0 && checked > 0 ? 'PASS -- over-group filter matches by _si index (F1)' : (checked === 0 ? 'SKIP' : fail + ' FAIL')));
process.exit(fail ? 1 : 0);
