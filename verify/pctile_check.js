#!/usr/bin/env node
/*
 * pctile_check.js -- verify/pctile_check.js (v3.6.0-t2j)
 *
 * Guards STATISTICAL PARITY WITH STATA for percentiles (the package's core
 * promise). Two checks, no Stata needed:
 *   1. The percentile ALGORITHM (Stata default: i=n*p/100; integer -> avg(x[i],
 *      x[i+1]); else x[ceil(i)]; no interpolation) reproduces hand-verified Stata
 *      results, and is NOT the old interpolating altdef-like formula.
 *   2. If a rendered overall-boxplot page is present (h_s1_box_overall.html from
 *      the harness, price from the 30-row SFI fixture), its emitted Q1/median/Q3
 *      equal Stata's default (4296 / 5091.5 / 7827) -- i.e. DatasetBuilder.java
 *      really produces Stata numbers end-to-end.
 *
 * Usage: node verify/pctile_check.js [<harness_out_dir>]
 */
'use strict';
const fs = require('fs');
const path = require('path');

function stataPctile(sortedIn, p) {
  const s = sortedIn.slice().sort((a, b) => a - b);
  const n = s.length;
  if (n === 0) return null;
  if (n === 1) return s[0];
  const i = n * p / 100.0, fi = Math.floor(i);
  if (Math.abs(i - fi) < 1e-9) {
    const k = fi;
    if (k < 1) return s[0];
    if (k >= n) return s[n - 1];
    return (s[k - 1] + s[k]) / 2.0;
  }
  let c = Math.ceil(i); if (c < 1) c = 1; if (c > n) c = n;
  return s[c - 1];
}

let fail = 0;

// 1. Known Stata results
const known = [
  { x: [1,2,3,4,5,6,7,8,9,10], q: [25, 3], },
  { x: [1,2,3,4,5,6,7,8,9,10], q: [50, 5.5], },
  { x: [1,2,3,4,5,6,7,8,9,10], q: [75, 8], },
  { x: [10,20,30,40], q: [50, 25], },        // n=4: i=2 int -> (20+30)/2
  { x: [10,20,30,40], q: [25, 20], },        // i=1 int -> (10+20)/2 = 15? check below
];
// n=4, p25: i=1 integer -> (x[1]+x[2])/2 = (10+20)/2 = 15
known[4].q[1] = 15;
for (const t of known) {
  const got = stataPctile(t.x, t.q[0]);
  if (Math.abs(got - t.q[1]) > 1e-9) { console.log(`  FAIL pctile(${t.x}, ${t.q[0]}) = ${got}, expected ${t.q[1]}`); fail++; }
}
// regression guard: the OLD altdef-like h=(n+1)p/100 interpolation must NOT match
// Stata here (else the test is toothless). For 1..10 p25 it gave 2.75, not 3.
(function () {
  const s = [1,2,3,4,5,6,7,8,9,10], n = 10, h = (n + 1) * 25 / 100.0, lo = Math.floor(h), hi = Math.ceil(h);
  const old = s[lo - 1] + (h - Math.floor(h)) * (s[hi - 1] - s[lo - 1]);
  if (Math.abs(old - 3) < 1e-9) { console.log('  FAIL regression guard: altdef formula also gives 3 -- test toothless'); fail++; }
})();

// 2. End-to-end: overall boxplot page from the harness
const dir = process.argv[2];
if (dir) {
  const p = path.join(dir, 'h_s1_box_overall.html');
  if (fs.existsSync(p)) {
    const h = fs.readFileSync(p, 'utf8');
    // fixture price column, n=30
    const price = [4099,4749,3799,4816,7827,5788,4453,5189,10372,4082,11385,14500,15906,3299,5705,4504,5104,3667,3955,3984,9690,6295,9735,6229,4589,5079,8129,4296,5799,4499];
    const want = { q1: stataPctile(price, 25), median: stataPctile(price, 50), q3: stataPctile(price, 75) };
    // the boxplot dataset emits q1/median/q3 as numbers in the data object
    for (const [lbl, v] of [['Q1', want.q1], ['median', want.median], ['Q3', want.q3]]) {
      // match the value as a standalone number token in the page
      const re = new RegExp('(^|[^\\d.])' + String(v).replace('.', '\\.') + '($|[^\\d])');
      if (!re.test(h)) { console.log(`  FAIL h_s1 boxplot: ${lbl}=${v} (Stata default) not found in page`); fail++; }
    }
    if (!fail) console.log('  overall boxplot Q1/median/Q3 = Stata default (4296 / 5091.5 / 7827)');
  }
}

console.log('pctile_check: ' + (fail === 0 ? 'PASS -- percentiles match Stata default' : fail + ' FAIL'));
process.exit(fail ? 1 : 0);
