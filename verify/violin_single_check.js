// verify/violin_single_check.js (t2j fix9d) -- SINGLE-PAGE violin filter check.
// usage: SPK_CHROME=<chrome> node verify/violin_single_check.js page1.html [page2.html ...]
// Opens each page headless, selects EVERY value of the first filter dropdown and compares
// every violin entry's n and median (read from window._vTarget(), the entries the chart is
// animating to) with a recomputation from the page's own embedded columns -- so it works on
// Stata-made pages as well as harness pages. by() pages are covered by byfilter_probe.js.
// A page that refreshes only the first plot variable FAILS here (proven on fix9c).
const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch({ executablePath: process.env.SPK_CHROME });
  for (const f of process.argv.slice(2)) {
    const p = await b.newPage(); const errs = []; p.on('pageerror', e => errs.push(e.message));
    await p.goto('file://' + f, { waitUntil: 'load' }); await p.waitForTimeout(1200);
    const res = await p.evaluate(() => {
      const m = window._smeta; const sel = document.querySelector("select[id^='fsel_']"); const out = [];
      const pct = (a, q) => { const n = a.length, i = n * q / 100, fi = Math.floor(i); if (Math.abs(i - fi) < 1e-9) { const k = fi; if (k < 1) return a[0]; if (k >= n) return a[n - 1]; return (a[k - 1] + a[k]) / 2; } let c = Math.ceil(i); if (c < 1) c = 1; if (c > n) c = n; return a[c - 1]; };
      const col = v => _sAgg.column(v); const fv = sel.id.slice(5);
      for (const o of Array.from(sel.options)) {
        sel.value = o.value; _onFilterChange();
        if (typeof window._vTarget !== "function") return { dbg: [o.value, Object.keys(window).filter(x => /^_v/.test(x)), typeof _vTarget] }; const tgt = window._vTarget(); const k = parseInt(o.value, 10);
        tgt.forEach((e, j) => {
          const rows = []; for (let r = 0; r < m.nObs; r++) { if (o.value !== '__ALL__' && _si[fv][r] !== k) continue; if (e.gi >= 0 && _si[m.overVar][r] !== e.gi) continue; rows.push(r); }
          const v = m.plotVars[e.vi]; const vals = rows.map(r => col(v)[r]).filter(x => x !== null && x !== undefined && !isNaN(x)).sort((a, b) => a - b);
          const ok = (e.n || 0) === vals.length && (!vals.length || Math.abs(e.median - pct(vals, 50)) < 1e-6);
          out.push({ opt: o.textContent.trim(), entry: j, label: e.label, n: e.n, expN: vals.length, ok });
        });
      }
      return { entries: window._vTarget().length, plotVars: m.plotVars, over: m.overVar, out };
    });
    const bad = res.out.filter(x => !x.ok);
    console.log(require('path').basename(f), 'entries', res.entries, 'vars', res.plotVars, 'over', res.over, 'checks', res.out.length, 'FAIL', bad.length, errs.length ? errs : '');
    bad.slice(0, 3).forEach(x => console.log('   ', JSON.stringify(x)));
    await p.close();
  }
  await b.close();
})();
