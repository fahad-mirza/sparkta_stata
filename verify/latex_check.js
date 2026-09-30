#!/usr/bin/env node
/*
 * latex_check.js -- verify/latex_check.js (v3.6.0-t2h, batch 2a)
 *
 * For every post-estimation HTML in a directory (or a single file), load the
 * page's own scripts in a mock-DOM VM (the same technique as js_check.js),
 * then call the PubTable exporters the page defines:
 *     _spkPubLatex(true)   -- standalone-compilable booktabs LaTeX
 *     _spkPubMarkdown()    -- Copy = Markdown
 *     _spkPubTidyCsv()     -- tidy long CSV
 * Writes each LaTeX to a temp .tex, compiles it with pdflatex, and fails on
 * any compile error. Also does light structural checks on the Markdown/CSV
 * (pipe-table header + separator row; CSV header line). ASCII only.
 *
 * Usage: node verify/latex_check.js <dir-or-file> [--keep]
 * Requires: node, pdflatex (texlive-latex-base + texlive-latex-extra for
 *           booktabs + threeparttable). Exit 0 = all pass.
 */
'use strict';
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const os = require('os');
const cp = require('child_process');

const target = process.argv[2];
const keep = process.argv.includes('--keep');
if (!target) { console.error('usage: latex_check.js <dir-or-file>'); process.exit(2); }

function listHtml(t) {
  const st = fs.statSync(t);
  if (st.isFile()) return [t];
  return fs.readdirSync(t).filter(f => f.endsWith('.html')).map(f => path.join(t, f)).sort();
}

// -- minimal mock DOM, mirroring verify/js_check.js's stubs -------------------
function mockEl(id) {
  return {
    id, innerHTML: '', textContent: '', value: '', checked: false, hidden: false,
    style: {}, className: '', width: 800, height: 470,
    getContext: () => ({}), setAttribute() {}, getAttribute() { return null; },
    appendChild() {}, removeChild() {}, addEventListener() {}, click() {},
    querySelector: () => null, querySelectorAll: () => [],
    classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } },
    parentNode: null, nextElementSibling: null, previousElementSibling: null,
    children: [], select() {}
  };
}
function makeSandbox() {
  const els = {};
  const doc = {
    getElementById: (id) => els[id] || (els[id] = mockEl(id)),
    createElement: () => mockEl('_new'), createElementNS: () => mockEl('_ns'),
    querySelector: () => null, querySelectorAll: () => [],
    body: { appendChild() {}, removeChild() {}, style: {},
            classList: { add() {}, remove() {}, toggle() {}, contains() { return false; } } },
    documentElement: { style: {}, getAttribute: () => null, setAttribute() {} },
    addEventListener() {}, readyState: 'complete'
  };
  const sandbox = {
    document: doc, console,
    navigator: { clipboard: { writeText() { return { then() {} }; } } },
    URL: { createObjectURL: () => 'blob:x', revokeObjectURL() {} },
    Blob: function () {}, setTimeout: (f) => { try { f(); } catch (e) {} },
    requestAnimationFrame: (f) => f(), Chart: function () {},
  };
  sandbox.window = sandbox; sandbox.self = sandbox; sandbox.globalThis = sandbox;
  // window-level stubs so no script block aborts (mirrors verify/js_check.js)
  sandbox.addEventListener = () => {}; sandbox.removeEventListener = () => {};
  sandbox.devicePixelRatio = 1; sandbox.innerWidth = 1200; sandbox.innerHeight = 800;
  sandbox.matchMedia = () => ({ matches: false, addEventListener() {}, addListener() {} });
  sandbox.getComputedStyle = () => ({ getPropertyValue: () => '' });
  sandbox.IntersectionObserver = function () { this.observe = () => {}; this.disconnect = () => {}; };
  sandbox.MutationObserver = function () { this.observe = () => {}; this.disconnect = () => {}; };
  sandbox.Chart.register = () => {}; sandbox.Chart.getChart = () => null;
  sandbox.Chart.defaults = { plugins: { legend: { labels: {} } } };
  vm.createContext(sandbox);
  return sandbox;
}

// Extract <script>...</script> bodies (skip src= and vendored /*! banners big libs)
function scripts(html) {
  const out = [];
  const re = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  let m;
  while ((m = re.exec(html)) !== null) {
    if (/\bsrc\s*=/.test(m[1])) continue;
    const body = m[2];
    // skip vendored libraries (Chart.js UMD etc.) identified by banner
    if (/^\s*\/\*!/.test(body) || /webpackUniversalModuleDefinition/.test(body)) continue;
    out.push(body);
  }
  return out;
}

const files = listHtml(target).filter(f => {
  const h = fs.readFileSync(f, 'utf8');
  // require the data-object ASSIGNMENT, not a mere reference: a notable page
  // still mentions window.__tblData inside the detailed exporters but has no
  // publication table, and must not be checked (or counted as a failure).
  return /window\.__tblData\s*=\s*\{/.test(h) || /window\.__tblMargins\s*=\s*\{/.test(h);
});

if (!files.length) { console.log('latex_check: no post-estimation pages found'); process.exit(0); }

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'spk_tex_'));
let pass = 0, fail = 0;
const fails = [];

for (const f of files) {
  const base = path.basename(f, '.html');
  const html = fs.readFileSync(f, 'utf8');
  const sb = makeSandbox();
  let ok = true, why = '';
  for (const s of scripts(html)) {
    try { vm.runInContext(s, sb, { timeout: 5000 }); }
    catch (e) { /* ignore chart-script errors unrelated to the table */ }
  }
  // must expose the exporters
  if (typeof sb._spkPubLatex !== 'function') { fail++; fails.push(base + ' (no _spkPubLatex)'); continue; }
  let tex = '', md = '', csv = '';
  try { tex = sb._spkPubLatex(true); } catch (e) { ok = false; why = 'latex threw: ' + e.message; }
  try { md = sb._spkPubMarkdown(); } catch (e) { ok = false; why = 'markdown threw: ' + e.message; }
  try { csv = sb._spkPubTidyCsv(); } catch (e) { ok = false; why = 'csv threw: ' + e.message; }
  if (ok) {
    // structural checks
    if (!/\\begin\{document\}/.test(tex) || !/\\bottomrule/.test(tex)) { ok = false; why = 'latex missing structure'; }
    const mdlines = md.split('\n').filter(x => x.trim());
    if (mdlines.length < 2 || mdlines[0].indexOf('|') < 0 || !/^\s*\|[:\- |]+\|\s*$/.test(mdlines[1])) { ok = false; why = 'markdown not a pipe table'; }
    if (csv.split('\n')[0].indexOf(',') < 0) { ok = false; why = 'csv header missing commas'; }
  }
  if (ok) {
    // compile the LaTeX
    const tf = path.join(tmp, base + '.tex');
    fs.writeFileSync(tf, tex);
    const r = cp.spawnSync('pdflatex', ['-interaction=nonstopmode', '-halt-on-error', '-output-directory', tmp, tf],
      { encoding: 'utf8', timeout: 60000 });
    if (r.status !== 0 || !fs.existsSync(path.join(tmp, base + '.pdf'))) {
      ok = false;
      const log = (r.stdout || '').split('\n').filter(l => l.startsWith('!')).slice(0, 3).join(' | ');
      why = 'pdflatex failed: ' + (log || ('status ' + r.status));
    }
  }
  if (ok) { pass++; console.log('  OK   ' + base); }
  else { fail++; fails.push(base + ' -- ' + why); console.log('  FAIL ' + base + ' -- ' + why); }
}

if (!keep) { try { fs.rmSync(tmp, { recursive: true, force: true }); } catch (e) {} }
else console.log('  (kept ' + tmp + ')');
console.log('latex_check: ' + files.length + ' page(s), ' + pass + ' pass, ' + fail + ' fail');
process.exit(fail ? 1 : 0);
