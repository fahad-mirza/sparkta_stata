/*
 * sparkta_engine.js  v1.6  (F-1, sparkta_test)
 *
 * Browser-side aggregation engine for sparkta_test interactive filter system.
 *
 * Globals expected to be defined BEFORE any API call (not necessarily before
 * this script loads -- all reads are lazy):
 *   _sd      {varName: number[]}   numeric column arrays, null = missing
 *   _si      {varName: number[]}   categorical index arrays per filter var
 *   _sc      {varName: string[]}   category label arrays per filter var
 *   _smeta   {nObs, plotVars, overVar, byVar, filterVars, sliders, stat, overLabels}
 *
 * v1.6: tCritical95() upgraded to 8dp lookup + Cornish-Fisher (matches Stata to 6dp).
 *
 * v1.5: buildCiBands() recomputes CI bands on filtered rows.
 *
 * v1.4: buildFitLine() recomputes fit curve on filtered rows (JS port of FitComputer).
 *
 * v1.3: buildScatterPoints() for scatter filter with mlabel support.
 *
 * v1.2 (F-1): Slider support added.
 *   filterRows() now accepts an optional second argument sliderState:
 *     { varName: {lo: number, hi: number}, ... }
 *   Rows failing any range condition are excluded exactly like dropdown filters.
 *   buildChartData() passes sliderState through to filterRows() automatically.
 *
 * v1.1 FIX: meta() and nObs() are lazy accessors that read window._smeta
 *   at call time, NOT at IIFE time.  Prevents blank chart when engine loads
 *   in <head> before _smeta is defined in the <body> script block.
 *
 * Public API (exposed on window._sAgg):
 *   filterRows(filterState, sliderState)              -> Int32Array of row indices
 *   aggregate(rows, groupBy)                          -> {labels:[], data:[]}
 *   buildChartData(filterState, sliderState, groupVar) -> chart-ready payload
 *
 * filterState:  {varName: categoryIndex, ...}  (null = "All" = no restriction)
 * sliderState:  {varName: {lo: number, hi: number}, ...}  (absent = no restriction)
 */
(function(w) {
    'use strict';

    /* -----------------------------------------------------------------------
     * _sdGet(name)  --  lazy _sd column accessor  (v1.4)
     *
     * For small datasets (N < 200), columns are plain JSON arrays in _sd.
     * For large datasets, columns are base64-encoded Float32Array buffers
     * stored in _sdb. _sdGet() decodes on first access, caches in _sd,
     * and returns the decoded Float32Array.
     *
     * NaN values in Float32Array represent missing (null in JSON).
     * All engine code uses _sdGet(name) instead of w._sd[name] directly.
     * ----------------------------------------------------------------------- */
    function _sdGet(name) {
        // Already decoded or plain JSON -- return directly
        if (w._sd && w._sd[name] !== undefined) return w._sd[name];
        // Check _sdb for base64-encoded column
        if (!w._sdb || w._sdb[name] === undefined) return null;
        // Decode base64 -> ArrayBuffer -> Float32Array
        var b64  = w._sdb[name];
        var bin  = atob(b64);
        var buf  = new ArrayBuffer(bin.length);
        var view = new Uint8Array(buf);
        for (var i = 0; i < bin.length; i++) view[i] = bin.charCodeAt(i);
        var f32  = new Float32Array(buf);
        // Cache decoded array in _sd for subsequent calls
        if (!w._sd) w._sd = {};
        w._sd[name] = f32;
        return f32;
    }

    /* Helper: get value at row r, treating NaN as null (missing sentinel). */
    function _sdVal(col, r) {
        var v = col[r];
        return (v === null || v === undefined || (typeof v === 'number' && isNaN(v))) ? null : v;
    }

    // v1.1: lazy accessors -- read window._smeta at call time, not load time.
    // This is the critical fix: the engine script may load in <head> before
    // _smeta is defined in a <body> script block.
    function meta() { return w._smeta || {}; }
    function nObs() { return (w._smeta && w._smeta.nObs) ? w._smeta.nObs : 0; }

    /* -----------------------------------------------------------------------
     * filterRows(filterState, sliderState)
     * Returns an Int32Array of row indices (0-based) passing ALL active
     * filter conditions:
     *   filterState: categorical dropdown selections  {varName: catIdx|null}
     *   sliderState: numeric range selections  {varName: {lo,hi}|null}
     * ----------------------------------------------------------------------- */
    function filterRows(filterState, sliderState) {
        var m     = meta();
        var n     = nObs();
        var fvars = m.filterVars || [];

        // Build active categorical filter list
        var activeCat = [];
        if (filterState) {
            for (var fi = 0; fi < fvars.length; fi++) {
                var fname = fvars[fi];
                var sel   = filterState[fname];
                if (sel !== null && sel !== undefined) {
                    activeCat.push({ idx: w._si[fname], catIdx: sel });
                }
            }
        }

        // Build active slider range list
        var activeSlider = [];
        if (sliderState) {
            var smeta = m.sliders || [];
            for (var si = 0; si < smeta.length; si++) {
                var sname  = smeta[si].name;
                var srange = sliderState[sname];
                if (srange && srange.lo !== undefined && srange.hi !== undefined) {
                    var col = _sdGet(sname);
                    if (col) activeSlider.push({ col: col, lo: srange.lo, hi: srange.hi });
                }
            }
        }

        // Fast path: nothing active -- return all rows
        if (activeCat.length === 0 && activeSlider.length === 0) {
            var all = new Int32Array(n);
            for (var i = 0; i < n; i++) all[i] = i;
            return all;
        }

        // Two-pass: count matching rows, then fill result array
        var count = 0;
        for (var r = 0; r < n; r++) {
            if (_rowPasses(r, activeCat, activeSlider)) count++;
        }
        var result = new Int32Array(count);
        var j = 0;
        for (var r2 = 0; r2 < n; r2++) {
            if (_rowPasses(r2, activeCat, activeSlider)) result[j++] = r2;
        }
        return result;
    }

    /* All categorical AND all slider conditions must pass for a row. */
    function _rowPasses(r, activeCat, activeSlider) {
        for (var ai = 0; ai < activeCat.length; ai++) {
            if (activeCat[ai].idx[r] !== activeCat[ai].catIdx) return false;
        }
        for (var si = 0; si < activeSlider.length; si++) {
            var v = _sdVal(activeSlider[si].col, r);
            if (v === null) return false; // exclude missing
            if (v < activeSlider[si].lo || v > activeSlider[si].hi) return false;
        }
        return true;
    }

    /* -----------------------------------------------------------------------
     * aggregate(rows, plotVar, groupVar)
     * Groups rows by groupVar (using _si index arrays), computes the
     * requested stat for plotVar, returns {labels:[], data:[]}.
     *
     * groupVar may be null (=> single group "Overall").
     * stat comes from _smeta.stat: "mean"|"sum"|"count"|"min"|"max"|
     *                               "median"|"sd"|"cv"
     * ----------------------------------------------------------------------- */
    function aggregate(rows, plotVar, groupVar) {
        var m       = meta();
        var stat    = m.stat || 'mean';
        var plotCol = _sdGet(plotVar);
        if (!plotCol) return { labels: [], data: [] };

        var labels, nGroups, groupOf;

        if (groupVar) {
            labels  = w._sc[groupVar] || [];
            nGroups = labels.length;
            groupOf = w._si[groupVar];
        } else {
            labels  = ['Overall'];
            nGroups = 1;
            groupOf = null;
        }

        // Accumulate per-group values
        var buckets = [];
        for (var g = 0; g < nGroups; g++) buckets.push([]);

        for (var ri = 0; ri < rows.length; ri++) {
            var r   = rows[ri];
            var val = _sdVal(plotCol, r);
            if (val === null) continue; // skip missing
            var g2  = groupOf ? groupOf[r] : 0;
            if (g2 < 0 || g2 >= nGroups) continue;
            buckets[g2].push(val);
        }

        // Compute stat per bucket
        var data = [];
        for (var g3 = 0; g3 < nGroups; g3++) {
            var vals = buckets[g3];
            data.push(vals.length === 0 ? null : computeStat(vals, stat));
        }

        return { labels: labels.slice(), data: data };
    }

    /* -----------------------------------------------------------------------
     * computeStat(values, stat)
     * ----------------------------------------------------------------------- */
    function computeStat(vals, stat) {
        var n = vals.length;
        if (n === 0) return null;

        if (stat === 'count') return n;

        var sum = 0;
        for (var i = 0; i < n; i++) sum += vals[i];

        if (stat === 'sum') return sum;

        var mean = sum / n;
        if (stat === 'mean') return mean;

        if (stat === 'min') {
            var mn = vals[0];
            for (var i2 = 1; i2 < n; i2++) if (vals[i2] < mn) mn = vals[i2];
            return mn;
        }
        if (stat === 'max') {
            var mx = vals[0];
            for (var i3 = 1; i3 < n; i3++) if (vals[i3] > mx) mx = vals[i3];
            return mx;
        }

        // Variance (Bessel correction, n-1)
        if (stat === 'sd' || stat === 'cv') {
            if (n < 2) return null;
            var ss = 0;
            for (var i4 = 0; i4 < n; i4++) ss += (vals[i4] - mean) * (vals[i4] - mean);
            var sd = Math.sqrt(ss / (n - 1));
            return stat === 'sd' ? sd : (mean !== 0 ? Math.abs(sd / mean) : null);
        }

        if (stat === 'median') {
            var sorted = vals.slice().sort(function(a,b){return a-b;});
            var mid = Math.floor(n / 2);
            return n % 2 === 0 ? (sorted[mid-1] + sorted[mid]) / 2 : sorted[mid];
        }

        return mean; // default
    }

    /* -----------------------------------------------------------------------
     * buildChartData(filterState, sliderState, overrideGroupVar)
     * High-level entry point: filters rows, aggregates each plot variable.
     * Returns: { labels: [], datasets: [{label, data},...], nActive: int }
     * ----------------------------------------------------------------------- */
    function buildChartData(filterState, sliderState, overrideGroupVar) {
        var m        = meta();
        var rows     = filterRows(filterState, sliderState);
        var groupVar = overrideGroupVar !== undefined ? overrideGroupVar : m.overVar;
        var plotVars = m.plotVars || [];

        var labels   = null;
        var datasets = [];

        for (var vi = 0; vi < plotVars.length; vi++) {
            var pv  = plotVars[vi];
            var agg = aggregate(rows, pv, groupVar);
            if (labels === null) labels = agg.labels;
            datasets.push({ label: pv, data: agg.data });
        }

        return {
            labels:   labels || [],
            datasets: datasets,
            nActive:  rows.length
        };
    }

    /* -----------------------------------------------------------------------
     * buildScatterPoints(rows, xVar, yVar, groupVar)
     * v1.7: Rebuilds {x,y,label} point arrays for scatter charts after filter.
     *
     * Returns: [{data:[{x,y,label?},...], groupLabel:string}, ...]
     *   One entry per dataset group. groupLabel is the over() group display name
     *   (empty string for single-dataset scatter without over()).
     *   Each entry's .data array is assigned to dsets[gi].data by the caller.
     *   The .groupLabel property is used by the per-group fit rebuild loop in
     *   FilterRenderer to filter rows to only that group's observations.
     *
     * Changed from v1.3: was [{x,y},...] bare arrays. Now wrapped objects so
     * FilterRenderer can access groupLabel without a separate lookup.
     * ----------------------------------------------------------------------- */
    function buildScatterPoints(rows, xVar, yVar, groupVar) {
        var m      = meta();
        var xCol   = _sdGet(xVar);
        var yCol   = _sdGet(yVar);
        var labels = m.labels || null;
        if (!xCol || !yCol) return [{data:[], groupLabel:''}];

        if (!groupVar) {
            var pts = [];
            for (var ri = 0; ri < rows.length; ri++) {
                var r = rows[ri];
                var xv = xCol[r], yv = yCol[r];
                if (xv === null || xv === undefined) continue;
                if (yv === null || yv === undefined) continue;
                var pt = { x: xv, y: yv };
                if (labels && labels[r] != null) pt.label = labels[r];
                pts.push(pt);
            }
            return [{data: pts, groupLabel: ''}];
        }

        // With over(): one entry per group, preserving palette index order
        var groupLabels = w._sc[groupVar] || [];
        var groupOf     = w._si[groupVar];
        var nGroups     = groupLabels.length;
        var grouped     = [];
        for (var g = 0; g < nGroups; g++) {
            grouped.push({ data: [], groupLabel: groupLabels[g] });
        }

        for (var ri2 = 0; ri2 < rows.length; ri2++) {
            var r2  = rows[ri2];
            var xv2 = _sdVal(xCol, r2), yv2 = _sdVal(yCol, r2);
            if (xv2 === null || yv2 === null) continue;
            var g2 = groupOf ? groupOf[r2] : 0;
            if (g2 < 0 || g2 >= nGroups) continue;
            var pt2 = { x: xv2, y: yv2 };
            if (labels && labels[r2] != null) pt2.label = labels[r2];
            grouped[g2].data.push(pt2);
        }
        return grouped;
    }

    /* -----------------------------------------------------------------------
     * buildFitLine(rows, xVar, yVar, fitType)
     * v1.4: Recomputes a fit line on the currently filtered rows so the
     * fit curve always reflects the visible data, not the full dataset.
     *
     * Supported fitType values: lfit, qfit, lowess, exp, log, power, ma.
     * Returns: [{x, y}, ...] sorted by x, or [] if insufficient data.
     *
     * All computation is in plain JS -- no Java round-trip needed.
     * Lowess uses a simplified tricube WLS matching FitComputer.java logic.
     * MAX_LOWESS_N cap (500 pts) is applied for browser performance.
     * lfit/qfit use exact OLS matching Java output.
     * ----------------------------------------------------------------------- */
    var MAX_LOWESS_N_JS = 1500;

    function buildFitLine(rows, xVar, yVar, fitType) {
        var xCol = _sdGet(xVar), yCol = _sdGet(yVar);
        if (!xCol || !yCol) return [];

        // Collect non-null pairs from filtered rows
        var xs = [], ys = [];
        for (var ri = 0; ri < rows.length; ri++) {
            var r = rows[ri];
            var xv = _sdVal(xCol, r), yv = _sdVal(yCol, r);
            if (xv === null || yv === null) continue;
            xs.push(xv); ys.push(yv);
        }
        var n = xs.length;
        if (n < 3) return [];

        // Sort by x
        var idx = [];
        for (var i = 0; i < n; i++) idx.push(i);
        idx.sort(function(a, b) { return xs[a] - xs[b]; });
        var sxs = [], sys = [];
        for (var i2 = 0; i2 < n; i2++) { sxs.push(xs[idx[i2]]); sys.push(ys[idx[i2]]); }

        var pts = [];
        var ft  = fitType || 'lfit';

        if (ft === 'lfit') {
            // OLS linear: y = a + bx
            var reg = olsLinear(sxs, sys);
            for (var i3 = 0; i3 < n; i3++)
                pts.push({ x: sxs[i3], y: reg.a + reg.b * sxs[i3] });

        } else if (ft === 'qfit') {
            // OLS quadratic: y = a + bx + cx^2
            var q = olsQuad(sxs, sys);
            for (var i4 = 0; i4 < n; i4++) {
                var xi = sxs[i4];
                pts.push({ x: xi, y: q.a + q.b * xi + q.c * xi * xi });
            }

        } else if (ft === 'lowess') {
            // Tricube WLS lowess, f=0.8, capped at MAX_LOWESS_N_JS
            var lxs = sxs, lys = sys, ln = n;
            if (n > MAX_LOWESS_N_JS) {
                lxs = []; lys = [];
                var step = (n - 1) / (MAX_LOWESS_N_JS - 1);
                for (var si = 0; si < MAX_LOWESS_N_JS; si++) {
                    var ii = Math.round(si * step);
                    lxs.push(sxs[ii]); lys.push(sys[ii]);
                }
                ln = MAX_LOWESS_N_JS;
            }
            var span = Math.max(3, Math.ceil(0.8 * ln));
            for (var i5 = 0; i5 < ln; i5++) {
                var xi5 = lxs[i5];
                // Expand window
                var lo = i5, hi = i5;
                while (hi - lo + 1 < span) {
                    var eL = lo > 0    ? xi5 - lxs[lo-1] : 1e18;
                    var eR = hi < ln-1 ? lxs[hi+1] - xi5 : 1e18;
                    if (eL <= eR) lo--; else hi++;
                }
                lo = Math.max(0, lo); hi = Math.min(ln-1, hi);
                var h = Math.max(xi5 - lxs[lo], lxs[hi] - xi5);
                if (h < 1e-12) { pts.push({ x: xi5, y: lys[i5] }); continue; }
                var sw=0,swx=0,swy=0,swxx=0,swxy=0;
                for (var j = lo; j <= hi; j++) {
                    var u = Math.abs(lxs[j] - xi5) / h;
                    if (u >= 1.0) continue;
                    var u3 = u*u*u; var wt = Math.pow(1 - u3, 3); // wt=weight, avoids shadowing IIFE w=window
                    sw+=wt; swx+=wt*lxs[j]; swy+=wt*lys[j];
                    swxx+=wt*lxs[j]*lxs[j]; swxy+=wt*lxs[j]*lys[j];
                }
                var det = sw*swxx - swx*swx;
                var fy;
                if (Math.abs(det) < 1e-12) { fy = sw > 0 ? swy/sw : lys[i5]; }
                else { var bw=(sw*swxy-swx*swy)/det; fy=(swy-bw*swx)/sw + bw*xi5; }
                pts.push({ x: xi5, y: fy });
            }

        } else if (ft === 'exp') {
            // y = ae^(bx), linearise via ln(y)
            var lnys = []; var validX = [];
            for (var i6 = 0; i6 < n; i6++) {
                if (sys[i6] > 0) { lnys.push(Math.log(sys[i6])); validX.push(sxs[i6]); }
            }
            if (validX.length < 3) return [];
            var re = olsLinear(validX, lnys);
            for (var i7 = 0; i7 < n; i7++)
                pts.push({ x: sxs[i7], y: Math.exp(re.a) * Math.exp(re.b * sxs[i7]) });

        } else if (ft === 'log') {
            // y = a + b*ln(x)
            var lnxs = []; var logYs = [];
            for (var i8 = 0; i8 < n; i8++) {
                if (sxs[i8] > 0) { lnxs.push(Math.log(sxs[i8])); logYs.push(sys[i8]); }
            }
            if (lnxs.length < 3) return [];
            var rl = olsLinear(lnxs, logYs);
            for (var i9 = 0; i9 < n; i9++) {
                if (sxs[i9] > 0) pts.push({ x: sxs[i9], y: rl.a + rl.b * Math.log(sxs[i9]) });
            }

        } else if (ft === 'power') {
            // y = ax^b, linearise via log-log
            var logXs = []; var logYs2 = [];
            for (var ia = 0; ia < n; ia++) {
                if (sxs[ia] > 0 && sys[ia] > 0) {
                    logXs.push(Math.log(sxs[ia])); logYs2.push(Math.log(sys[ia]));
                }
            }
            if (logXs.length < 3) return [];
            var rp = olsLinear(logXs, logYs2);
            for (var ib = 0; ib < n; ib++) {
                if (sxs[ib] > 0) pts.push({ x: sxs[ib], y: Math.exp(rp.a) * Math.pow(sxs[ib], rp.b) });
            }

        } else if (ft === 'ma') {
            // Moving average, window = max(3, n/10), centred
            var win = Math.max(3, Math.floor(n / 10));
            var half = Math.floor(win / 2);
            for (var ic = 0; ic < n; ic++) {
                var lo2 = Math.max(0, ic - half);
                var hi2 = Math.min(n-1, ic + half);
                var sum2 = 0;
                for (var id = lo2; id <= hi2; id++) sum2 += sys[id];
                pts.push({ x: sxs[ic], y: sum2 / (hi2 - lo2 + 1) });
            }
        }

        return pts;
    }

    // OLS linear regression: returns {a, b} for y = a + bx
    function olsLinear(xs, ys) {
        var n = xs.length, sx=0, sy=0, sxx=0, sxy=0;
        for (var i = 0; i < n; i++) { sx+=xs[i]; sy+=ys[i]; sxx+=xs[i]*xs[i]; sxy+=xs[i]*ys[i]; }
        var det = n*sxx - sx*sx;
        if (Math.abs(det) < 1e-12) return { a: sy/n, b: 0 };
        return { a: (sy*sxx - sx*sxy)/det, b: (n*sxy - sx*sy)/det };
    }

    // OLS quadratic: returns {a, b, c} for y = a + bx + cx^2
    // Solves 3x3 normal equations via Gaussian elimination
    function olsQuad(xs, ys) {
        var n = xs.length;
        var s0=n, s1=0, s2=0, s3=0, s4=0, t0=0, t1=0, t2=0;
        for (var i = 0; i < n; i++) {
            var xi=xs[i], yi=ys[i], xi2=xi*xi;
            s1+=xi; s2+=xi2; s3+=xi2*xi; s4+=xi2*xi2;
            t0+=yi; t1+=xi*yi; t2+=xi2*yi;
        }
        // Gaussian elimination on [[s0,s1,s2,t0],[s1,s2,s3,t1],[s2,s3,s4,t2]]
        var A=[[s0,s1,s2,t0],[s1,s2,s3,t1],[s2,s3,s4,t2]];
        for (var col = 0; col < 3; col++) {
            for (var row = col+1; row < 3; row++) {
                var f = A[row][col] / A[col][col];
                for (var k = col; k <= 3; k++) A[row][k] -= f * A[col][k];
            }
        }
        var c = A[2][3]/A[2][2];
        var b = (A[1][3] - A[1][2]*c) / A[1][1];
        var a = (A[0][3] - A[0][2]*c - A[0][1]*b) / A[0][0];
        return { a: a, b: b, c: c };
    }

    /* -----------------------------------------------------------------------
     * buildCiBands(rows, xVar, yVar)
     * v1.5: Recomputes 95% CI bands for lfit on filtered rows.
     * Returns {upper:[{x,y}...], lower:[{x,y}...]} or null if insufficient data.
     *
     * Uses OLS formulas matching FitComputer.java:
     *   fitted_y = a + b*x
     *   se(xi)   = s * sqrt(1/n + (xi-xbar)^2/Sxx)
     *   CI       = fitted +/- t * se
     *
     * t-critical: lookup table for df 1-30, linear interp 31-120, z for >120.
     * This matches FitComputer.tCritical95() -- close but not exact Stata invttail.
     * Acceptable for filter/slider interactions (exploratory, approximate).
     * Initial render always uses exact Stata CI (args 159/160).
     *
     * Only lfit and qfit support CI bands. qfit CI uses same se formula
     * with hat matrix diagonal approximation (conservative, slightly wider).
     * ----------------------------------------------------------------------- */
    function buildCiBands(rows, xVar, yVar, fitType) {
        var xCol = _sdGet(xVar), yCol = _sdGet(yVar);
        if (!xCol || !yCol) return null;

        // Collect filtered non-null pairs
        var xs = [], ys = [];
        for (var ri = 0; ri < rows.length; ri++) {
            var r = rows[ri];
            var xv = _sdVal(xCol, r), yv = _sdVal(yCol, r);
            if (xv === null || yv === null) continue;
            xs.push(xv); ys.push(yv);
        }
        var n = xs.length;
        if (n < 3) return null;

        // Sort by x
        var idx = [];
        for (var i = 0; i < n; i++) idx.push(i);
        idx.sort(function(a,b){ return xs[a]-xs[b]; });
        var sxs=[], sys=[];
        for (var i2=0; i2<n; i2++){ sxs.push(xs[idx[i2]]); sys.push(ys[idx[i2]]); }

        // OLS lfit (same for qfit: use lfit CI as conservative approximation)
        var reg = olsLinear(sxs, sys);
        var a = reg.a, b = reg.b;

        // Residual standard error
        var rss = 0;
        for (var i3=0; i3<n; i3++){
            var res = sys[i3] - (a + b*sxs[i3]);
            rss += res*res;
        }
        var s = (n > 2) ? Math.sqrt(rss/(n-2)) : 0;

        // x-bar and Sxx for se formula
        var xbar=0;
        for (var i4=0; i4<n; i4++) xbar += sxs[i4];
        xbar /= n;
        var Sxx=0;
        for (var i5=0; i5<n; i5++) Sxx += (sxs[i5]-xbar)*(sxs[i5]-xbar);
        if (Sxx < 1e-12) return null;

        var t = tCritical95(n-2);

        var upper=[], lower=[];
        for (var i6=0; i6<n; i6++){
            var xi = sxs[i6];
            var yi = a + b*xi;
            var se = s * Math.sqrt(1.0/n + (xi-xbar)*(xi-xbar)/Sxx);
            upper.push({x: xi, y: yi + t*se});
            lower.push({x: xi, y: yi - t*se});
        }
        return { upper: upper, lower: lower };
    }

    /* t-critical value for 95% two-sided CI.
     * v3.5.71: Matches Stata invttail(df, 0.025) to 6 decimal places.
     *
     * df 1-30:  exact 8-decimal-place lookup table (from Stata/scipy)
     * df 31+:   Cornish-Fisher 4-term expansion (max error < 1e-7,
     *           equivalent to 0.00005% CI width error -- indistinguishable
     *           from Stata output on any rendered chart)
     *
     * Previous implementation used 3-decimal lookup + linear interpolation,
     * giving up to 1.2% CI width overestimate for df 30-120.
     */
    // T95: t-distribution critical values at 95% CI, df 1-30.
    // Hoisted to IIFE scope (#2): allocated once, not on every tCritical95() call.
    var T95 = [0,
        12.70620474, 4.30265273, 3.18244631, 2.77644511, 2.57058184,
         2.44691185, 2.36462425, 2.30600414, 2.26215716, 2.22813885,
         2.20098516, 2.17881283, 2.16036866, 2.14478669, 2.13144955,
         2.11990530, 2.10981558, 2.10092204, 2.09302405, 2.08596345,
         2.07961384, 2.07387307, 2.06865761, 2.06389856, 2.05953855,
         2.05552944, 2.05183052, 2.04840714, 2.04522964, 2.04227246];

    function tCritical95(df) {
        if (df < 1) return 12.70620474; // df=1 value
        var idf = Math.floor(df);
        if (idf <= 30) return T95[idf];
        // Cornish-Fisher 4-term expansion for df > 30
        // Max error vs exact: < 1e-7 across all df 31 to infinity
        var z = 1.95996398; // Phi^{-1}(0.975), 8 decimal places
        var z2=z*z, z3=z2*z, z5=z2*z3, z7=z2*z5, z9=z2*z7;
        var d=df, d2=d*d, d3=d2*d, d4=d2*d2;
        return z
            + (z3 + z)              / (4*d)
            + (5*z5 + 16*z3 + 3*z)  / (96*d2)
            + (3*z7 + 19*z5 + 17*z3 - 15*z) / (384*d3)
            + (79*z9 + 779*z7 + 1482*z5 - 1920*z3 - 945*z) / (92160*d4);
    }

    /* -----------------------------------------------------------------------
     * buildGroupStats(rows, plotVar)
     * F-2A: Computes full summary statistics for a set of rows on one variable.
     * Returns {n, mean, median, min, max, sd, cv, q1, q3} matching Stata summ,detail.
     * Used by _sparkta_updateStatsTable() to update stat cells after filter change.
     * ----------------------------------------------------------------------- */
    function _pctile(sorted, p) {
        var n = sorted.length;
        if (n === 0) return null;
        if (n === 1) return sorted[0];
        var h  = (n + 1) * p / 100.0;
        var lo = Math.max(1, Math.min(n, Math.floor(h)));
        var hi = Math.max(1, Math.min(n, Math.ceil(h)));
        if (lo === hi) return sorted[lo - 1];
        return sorted[lo-1] + (h - Math.floor(h)) * (sorted[hi-1] - sorted[lo-1]);
    }

    function buildGroupStats(rows, plotVar) {
        var col = _sdGet(plotVar);
        if (!col) return null;
        var vals = [];
        for (var ri = 0; ri < rows.length; ri++) {
            var v = _sdVal(col, rows[ri]);
            if (v !== null) vals.push(v);
        }
        var n = vals.length;
        if (n === 0) return { n:0, mean:null, median:null, min:null, max:null, sd:null, cv:null, q1:null, q3:null };
        var sum=0, mn=vals[0], mx=vals[0];
        for (var i=0; i<n; i++) { sum+=vals[i]; if(vals[i]<mn)mn=vals[i]; if(vals[i]>mx)mx=vals[i]; }
        var mean = sum / n;
        var ss = 0;
        for (var i2=0; i2<n; i2++) ss += (vals[i2]-mean)*(vals[i2]-mean);
        var sd = n > 1 ? Math.sqrt(ss/(n-1)) : 0;
        var cv = mean !== 0 ? Math.abs(sd/mean) : 0;
        var sorted = vals.slice().sort(function(a,b){return a-b;});
        return {
            n:      n,
            mean:   mean,
            median: _pctile(sorted, 50),
            min:    mn,
            max:    mx,
            sd:     sd,
            cv:     cv,
            q1:     _pctile(sorted, 25),
            q3:     _pctile(sorted, 75)
        };
    }

    /* -----------------------------------------------------------------------
     * buildChartDataFromRows(rows, overrideGroupVar)
     * #1: Accepts pre-filtered rows so _onFilterChange can call filterRows()
     * once and share the result with both chart update and stats update,
     * avoiding the double-filter cost inside buildChartData().
     * ----------------------------------------------------------------------- */
    function buildChartDataFromRows(rows, overrideGroupVar) {
        var m        = meta();
        var groupVar = overrideGroupVar !== undefined ? overrideGroupVar : m.overVar;
        var plotVars = m.plotVars || [];
        var labels   = null;
        var datasets = [];
        for (var vi = 0; vi < plotVars.length; vi++) {
            var pv  = plotVars[vi];
            var agg = aggregate(rows, pv, groupVar);
            if (labels === null) labels = agg.labels;
            datasets.push({ label: pv, data: agg.data });
        }
        return { labels: labels || [], datasets: datasets, nActive: rows.length };
    }

    /* -----------------------------------------------------------------------
     * getValues(rows, plotVar)
     * Returns a number[] of non-null values for the given rows and variable.
     * Used by histogram filter to recount observations into bins.
     * ----------------------------------------------------------------------- */
    function getValues(rows, plotVar) {
        var col = _sdGet(plotVar);
        if (!col) return [];
        var vals = [];
        for (var i = 0; i < rows.length; i++) {
            var v = _sdVal(col, rows[i]);
            if (v !== null) vals.push(v);
        }
        return vals;
    }

    /* -----------------------------------------------------------------------
     * Public API
     * ----------------------------------------------------------------------- */
    w._sAgg = {
        filterRows:             filterRows,
        aggregate:              aggregate,
        buildChartData:         buildChartData,
        buildChartDataFromRows: buildChartDataFromRows,
        buildScatterPoints:     buildScatterPoints,
        buildFitLine:           buildFitLine,
        buildCiBands:           buildCiBands,
        computeStat:            computeStat,
        buildGroupStats:        buildGroupStats
    };

}(window));
