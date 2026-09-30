package com.dashboard_test.html;

import com.dashboard_test.data.*;
import java.util.*;

/**
 * FilterRenderer  (F-0 rewrite, sparkta)
 *
 * Replaces the old combinatorial pre-computation approach with a thin
 * wrapper that:
 *  1. Builds the filter dropdown UI (unchanged visual contract).
 *  2. Emits the embedded row-level data block via DataEmbedder.
 *  3. Emits a compact _applyFilter() JS function that calls _sAgg (sparkta_engine).
 *
 * The old buildFilterData / buildFilterScript / buildFilterDataByPanels /
 * buildFilterScriptByPanels methods are removed.  HtmlGenerator is updated
 * to call the new single-path methods.
 *
 * For backward compat with HtmlGenerator.java (which still checks
 * data.hasFilter1() and data.hasBy()), we keep the hasFilter() check on
 * the DataSet level.  No changes needed in HtmlGenerator call sites.
 */
class FilterRenderer {

    private final DashboardOptions o;
    private final HtmlGenerator    gen;
    private final DatasetBuilder   dsb;
    private final ChartRenderer    cr;

    FilterRenderer(DashboardOptions o, HtmlGenerator gen, DatasetBuilder dsb, ChartRenderer cr) {
        this.o   = o;
        this.gen = gen;
        this.dsb = dsb;
        this.cr  = cr;
    }

    // -------------------------------------------------------------------------
    // buildFilterUi -- filter dropdown bar (unchanged from v3.5.x)
    // -------------------------------------------------------------------------

    String buildFilterUi(DataSet data) {
        if (!data.hasFilter() && !data.hasSliders()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='filter-bar' id='filterBar'>\n");

        // -- Categorical dropdown filters (unchanged) -------------------------
        for (Variable fv : data.getFilterVariables()) {
            boolean showMissing = getShowMissing(fv, data);
            List<String> cats = DataSet.uniqueValues(fv, o.chart.sortgroups, showMissing);
            String fname = fv.getName();
            String flabel = fv.getDisplayName();

            sb.append("  <label>").append(escHtml(flabel)).append("</label>\n");
            sb.append("  <select id='fsel_").append(fname)
              .append("' onchange='_onFilterChange()'>\n");
            if (!o.style.noAllFilter) sb.append("    <option value='__ALL__'>All</option>\n");

            List<String> catLabels = buildCategoryList(fv, showMissing);
            for (int ci = 0; ci < catLabels.size(); ci++) {
                String cat = catLabels.get(ci);
                sb.append("    <option value='").append(ci).append("'>")
                  .append(escHtml(cat)).append("</option>\n");
            }
            sb.append("  </select>\n");
        }

        // -- Dual-handle range sliders (F-1) ----------------------------------
        for (Variable sv : data.getSliderVariables()) {
            String sname  = sv.getName();
            String slabel = sv.getDisplayName();
            // Slider config is read from _smeta.sliders at runtime;
            // we emit the container and thumb elements here.
            sb.append("  <div class='sld-group' id='sgrp_").append(sname).append("'>\n");
            sb.append("    <label class='sld-label'>").append(escHtml(slabel)).append("</label>\n");
            sb.append("    <div class='sld-track-wrap'>\n");
            // Low handle
            sb.append("      <input type='range' class='sld-thumb sld-lo' ")
              .append("id='slo_").append(sname).append("' ")
              .append("oninput='_onSliderInput(this,\"").append(escHtml(sname)).append("\",true)' ")
              .append("onchange='_onFilterChange()'>\n");
            // High handle
            sb.append("      <input type='range' class='sld-thumb sld-hi' ")
              .append("id='shi_").append(sname).append("' ")
              .append("oninput='_onSliderInput(this,\"").append(escHtml(sname)).append("\",false)' ")
              .append("onchange='_onFilterChange()'>\n");
            // Track fill bar
            sb.append("      <div class='sld-fill' id='sfill_").append(sname).append("'></div>\n");
            sb.append("    </div>\n");
            // Live value display: lo -- hi
            sb.append("    <span class='sld-vals'>");
            sb.append("<span id='sval_lo_").append(sname).append("'></span>");
            sb.append(" &ndash; ");
            sb.append("<span id='sval_hi_").append(sname).append("'></span>");
            sb.append("</span>\n");
            sb.append("  </div>\n");
        }

        sb.append("  <span id='_nObs' style='margin-left:12px;font-size:0.85em;opacity:0.7;'></span>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // buildDataBlock -- the _sd/_si/_sc/_smeta JS globals (F-0)
    // -------------------------------------------------------------------------

    String buildDataBlock(DataSet data) {
        if (!data.hasAnyFilter()) return "";
        DataEmbedder emb = new DataEmbedder(data, o, gen);
        return emb.buildDataBlock();
    }

    // -------------------------------------------------------------------------
    // buildApplyFilterScript -- compact _applyFilter + _onFilterChange (F-0)
    //
    // The old per-slice buildFilterScript was ~120 lines of combinatorial JS.
    // This version is ~30 lines that call _sAgg.buildChartData().
    // -------------------------------------------------------------------------

    String buildApplyFilterScript(DataSet data) {
        if (!data.hasFilter() && !data.hasSliders()) return "";

        StringBuilder sb = new StringBuilder();

        // -- Slider init: runs once on page load, reads _smeta.sliders -------
        if (data.hasSliders()) {
            sb.append("(function() {\n");
            sb.append("  var sm = (window._smeta && window._smeta.sliders) ? window._smeta.sliders : [];\n");
            sb.append("  for (var i = 0; i < sm.length; i++) {\n");
            sb.append("    var s    = sm[i];\n");
            sb.append("    var lo   = document.getElementById('slo_'   + s.name);\n");
            sb.append("    var hi   = document.getElementById('shi_'   + s.name);\n");
            sb.append("    var vlo  = document.getElementById('sval_lo_' + s.name);\n");
            sb.append("    var vhi  = document.getElementById('sval_hi_' + s.name);\n");
            sb.append("    if (!lo || !hi) continue;\n");
            sb.append("    lo.min = s.min; lo.max = s.max; lo.step = s.step; lo.value = s.min;\n");
            sb.append("    hi.min = s.min; hi.max = s.max; hi.step = s.step; hi.value = s.max;\n");
            sb.append("    if (vlo) vlo.textContent = _fmtNum(s.min);\n");
            sb.append("    if (vhi) vhi.textContent = _fmtNum(s.max);\n");
            sb.append("    _updateSliderFill(s.name);\n");
            sb.append("  }\n");
            sb.append("})();\n");

            // Slider input handler: enforces lo <= hi and updates fill + labels
            sb.append("function _onSliderInput(el, sname, isLo) {\n");
            sb.append("  var lo  = document.getElementById('slo_' + sname);\n");
            sb.append("  var hi  = document.getElementById('shi_' + sname);\n");
            sb.append("  var vlo = document.getElementById('sval_lo_' + sname);\n");
            sb.append("  var vhi = document.getElementById('sval_hi_' + sname);\n");
            sb.append("  if (!lo || !hi) return;\n");
            // Enforce lo <= hi: clamp the moved thumb
            sb.append("  if (isLo && parseFloat(lo.value) > parseFloat(hi.value)) lo.value = hi.value;\n");
            sb.append("  if (!isLo && parseFloat(hi.value) < parseFloat(lo.value)) hi.value = lo.value;\n");
            sb.append("  if (vlo) vlo.textContent = _fmtNum(parseFloat(lo.value));\n");
            sb.append("  if (vhi) vhi.textContent = _fmtNum(parseFloat(hi.value));\n");
            sb.append("  _updateSliderFill(sname);\n");
            sb.append("}\n");

            // Fill track between the two thumbs
            sb.append("function _updateSliderFill(sname) {\n");
            sb.append("  var lo   = document.getElementById('slo_' + sname);\n");
            sb.append("  var hi   = document.getElementById('shi_' + sname);\n");
            sb.append("  var fill = document.getElementById('sfill_' + sname);\n");
            sb.append("  if (!lo || !hi || !fill) return;\n");
            sb.append("  var mn = parseFloat(lo.min); var mx = parseFloat(lo.max);\n");
            sb.append("  var range = mx - mn || 1;\n");
            sb.append("  var left  = (parseFloat(lo.value) - mn) / range * 100;\n");
            sb.append("  var right = (parseFloat(hi.value) - mn) / range * 100;\n");
            sb.append("  fill.style.left  = left  + '%';\n");
            sb.append("  fill.style.width = (right - left) + '%';\n");
            sb.append("}\n");

            // Number formatter: integers shown without decimals, else 2 dp
            sb.append("function _fmtNum(v) {\n");
            sb.append("  return (v === Math.floor(v)) ? v.toFixed(0) : v.toFixed(2);\n");
            sb.append("}\n");
        }

        // -- _onFilterChange: reads dropdowns + sliders, calls engine --------
        sb.append("function _onFilterChange() {\n");
        sb.append("  var catState = {};\n");

        for (Variable fv : data.getFilterVariables()) {
            String fname = fv.getName();
            sb.append("  (function(fn){\n");
            sb.append("    var sel = document.getElementById('fsel_' + fn);\n");
            sb.append("    var v   = sel ? sel.value : (sel&&sel.options[0]?sel.options[0].value:'__ALL__');\n");
            sb.append("    catState[fn] = (v === '__ALL__') ? null : parseInt(v, 10);\n");
            sb.append("  })(").append(DataEmbedder.jsStr(fname)).append(");\n");
        }

        // Collect slider state from all slider elements
        sb.append("  var sldState = {};\n");
        if (data.hasSliders()) {
            sb.append("  var sm = (window._smeta && window._smeta.sliders) ? window._smeta.sliders : [];\n");
            sb.append("  for (var si = 0; si < sm.length; si++) {\n");
            sb.append("    var sn = sm[si].name;\n");
            sb.append("    var lo = document.getElementById('slo_' + sn);\n");
            sb.append("    var hi = document.getElementById('shi_' + sn);\n");
            sb.append("    if (lo && hi) sldState[sn] = { lo: parseFloat(lo.value), hi: parseFloat(hi.value) };\n");
            sb.append("  }\n");
        }

        // v3.5.65: scatter uses buildScatterPoints() to preserve {x,y,label} format.
        // v3.5.67: scatter also recomputes fit line on filtered rows via buildFitLine().
        // Bar/line/area etc. continue to use buildChartData() aggregation path.
        boolean isScatter = o.type.equals("scatter") || o.type.equals("bubble");
        if (isScatter) {
            // Scatter: rebuild {x,y[,label]} point arrays per dataset group.
            // xVar = second varlist item (x-axis), yVar = first (y-axis).
            // Matches Stata convention: scatter y x.
            String xVar = ""; String yVar = "";
            if (data.getNumericVariables().size() >= 2) {
                yVar = DataEmbedder.jsStr(data.getNumericVariables().get(0).getName());
                xVar = DataEmbedder.jsStr(data.getNumericVariables().get(1).getName());
            }
            String overArg = data.hasOver() ?
                DataEmbedder.jsStr(data.getOverVariable().getName()) : "null";
            String fitType = DataEmbedder.jsStr(o.chart.fit);
            // F2 (t2j): bubble carries a 3rd numeric var (size). On filter the radius
            // must be re-attached on the SAME fixed scale the initial render used
            // (r = 5 + 35*(raw-rmin)/rspan, rmin/rspan from the FULL data), otherwise
            // filtered bubbles lost their size (scatter path emitted only {x,y}).
            boolean isBubble = o.type.equals("bubble") && data.getNumericVariables().size() >= 3;
            // nScatterDs = number of scatter datasets (1 without over, nGroups with over)
            // Fit datasets always follow scatter datasets in the datasets array.
            // The engine needs to know where scatter ends and fit begins.
            sb.append("  var rows = _sAgg.filterRows(catState, sldState);\n");
            if (isBubble) {
                Variable rvar = data.getNumericVariables().get(2);
                double rmin = Double.MAX_VALUE, rmax = -Double.MAX_VALUE;
                for (Object v : rvar.getValues()) {
                    if (v instanceof Number) {
                        double d = ((Number) v).doubleValue();
                        if (d < rmin) rmin = d;
                        if (d > rmax) rmax = d;
                    }
                }
                double rspan = (rmax - rmin == 0) ? 1 : rmax - rmin;
                if (rmin == Double.MAX_VALUE) { rmin = 0; rspan = 1; }
                String rVar = DataEmbedder.jsStr(rvar.getName());
                sb.append("  var ptGroups = _sAgg.buildBubblePoints(rows,").append(xVar).append(",").append(yVar)
                  .append(",").append(rVar).append(",").append(rmin).append(",").append(rspan).append(",").append(overArg).append(");\n");
            } else {
                sb.append("  var ptGroups = _sAgg.buildScatterPoints(rows,").append(xVar).append(",").append(yVar).append(",").append(overArg).append(");\n");
            }
            sb.append("  if (!_mainChart) return;\n");
            sb.append("  var dsets = _mainChart.data.datasets;\n");
            sb.append("  for (var di = 0; di < ptGroups.length && di < dsets.length; di++) {\n");
            sb.append("    dsets[di].data = ptGroups[di].data;\n");
            sb.append("  }\n");
            // Recompute fit line (and CI bands if fitci) on filtered rows.
            // v3.5.70: CI bands are also updated so they move with the filter.
            // Without this fix, CI bands were frozen at full-dataset values
            // while the fit line moved -- visually wrong and misleading.
            if (!o.chart.fit.isEmpty()) {
                // Dataset layout depends on whether over() is active:
                //   No over():   [scatter, ciUpper?, ciLower?, fitLine]
                //   With over(): [scatter_g0..gN-1, fitLine_g0..gN-1]
                // fitci with over() is blocked in ado validation.
                boolean fitHasOver = !o.over.isEmpty();
                if (fitHasOver) {
                    // Per-group fit rebuild. Dataset stride per group:
                    //   fitci=false -> stride=1: [fitLine_gi]
                    //   fitci=true  -> stride=3: [ciUpper_gi, ciLower_gi, fitLine_gi]
                    // base = ptGroups.length (scatter datasets come first).
                    String overVar = DataEmbedder.jsStr(data.getOverVariable().getName());
                    int stride = o.chart.fitci ? 3 : 1;
                    sb.append("  var _fitStride = ").append(stride).append(";\n");
                    // Use window._sc/_si directly -- 'w' is local to the engine IIFE
                    // and not available in _onFilterChange global scope.
                    sb.append("  var _ovSc = (window._sc && window._sc[").append(overVar).append("]) || [];\n");
                    sb.append("  var _ovSi = (window._si && window._si[").append(overVar).append("]) || [];\n");
                    sb.append("  for (var _fgi = 0; _fgi < ptGroups.length; _fgi++) {\n");
                    sb.append("    var _fgLabel = ptGroups[_fgi].groupLabel;\n");
                    sb.append("    var _fgRows = rows.filter(function(r){\n");
                    sb.append("      var gIdx = _ovSi[r];\n");
                    sb.append("      var gLbl = (gIdx !== undefined && gIdx >= 0) ? _ovSc[gIdx] : null;\n");
                    sb.append("      return gLbl === _fgLabel;\n");
                    sb.append("    });\n");
                    sb.append("    var _fgBase = ptGroups.length + _fgi * _fitStride;\n");
                    if (o.chart.fitci) {
                        // Rebuild CI bands for this group
                        sb.append("    var _fgCi = _sAgg.buildCiBands(_fgRows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(",").append(o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel).append(");\n");   // t2j fix8 (r2): pass cilevel to fit CI recompute
                        sb.append("    if (_fgCi) {\n");
                        sb.append("      var _fgCiUp = dsets[_fgBase];\n");
                        sb.append("      var _fgCiLo = dsets[_fgBase + 1];\n");
                        sb.append("      if (_fgCiUp) _fgCiUp.data = _fgCi.upper;\n");
                        sb.append("      if (_fgCiLo) _fgCiLo.data = _fgCi.lower;\n");
                        sb.append("    }\n");
                    }
                    // Rebuild fit line (always last within stride)
                    int fitOffset2 = o.chart.fitci ? 2 : 0;
                    sb.append("    var _fgPts = _sAgg.buildFitLine(_fgRows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(");\n");
                    sb.append("    var _fgDs = dsets[_fgBase + ").append(fitOffset2).append("];\n");
                    sb.append("    if (_fgDs) _fgDs.data = _fgPts;\n");
                    sb.append("  }\n");
                } else {
                    // Single fit line (no over()) -- original logic unchanged
                    sb.append("  var fitPts = _sAgg.buildFitLine(rows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(");\n");
                    // Dataset layout: [scatter, ciUpper?, ciLower?, fitLine]
                    int fitOffset = o.chart.fitci ? 2 : 0;
                    sb.append("  var fitDs = dsets[ptGroups.length + ").append(fitOffset).append("];\n");
                    sb.append("  if (fitDs) fitDs.data = fitPts;\n");
                    if (o.chart.fitci) {
                        sb.append("  var ciBands = _sAgg.buildCiBands(rows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(",").append(o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel).append(");\n");   // t2j fix8 (r2): pass cilevel to fit CI recompute
                        sb.append("  if (ciBands) {\n");
                        sb.append("    var ciUpDs = dsets[ptGroups.length];\n");
                        sb.append("    var ciLoDs = dsets[ptGroups.length + 1];\n");
                        sb.append("    if (ciUpDs) ciUpDs.data = ciBands.upper;\n");
                        sb.append("    if (ciLoDs) ciLoDs.data = ciBands.lower;\n");
                        sb.append("  }\n");
                    }
                }
            }
            sb.append("  _mainChart.update('none');\n");
            sb.append("  var nc = document.getElementById('_nObs');\n");
            sb.append("  if (nc) nc.textContent = rows.length + ' obs';\n");
            sb.append("  _sparkta_updateStatsBadges(rows);\n");
        } else {
            boolean isCiChart  = o.type.equals("cibar") || o.type.equals("ciline");
            boolean isHistogram = o.type.equals("histogram");
            boolean isBoxViolin = o.type.equals("boxplot") || o.type.equals("hboxplot")
                                || o.type.equals("violin")  || o.type.equals("hviolinplot");
            boolean isPie = o.type.equals("pie") || o.type.equals("donut");
            // fix9a: stack100 without over() is RENDERED as plain aggregates (ChartRenderer
            // picks numDatasets; only over()+stack100 goes through overDatasets100), so the
            // filter must not rewrite it into shares -- route no-over to the generic branch
            boolean isStacked100 = o.chart.stack100 && data.hasOver();
            if (isCiChart) {
                // cibar/ciline: barWithErrorBars cannot be updated with .update().
                // Must destroy and reinit via _initChart() with fresh {y,yMin,yMax} data.
                // JS recomputes CI bounds using buildGroupStats() n/mean/sd + tCritCI().
                // tCritCI(df,level): full lookup for 90/95/99% matching Stata ci means.
                sb.append("  var rows = _sAgg.filterRows(catState, sldState);\n");
                // t2j: use the engine's exact tCrit (Stata invttail, any level) so the
                // filtered CI matches the initial render and Stata for cilevel() outside
                // {90,95,99}. (Was a 90/95/99-only table + Cornish-Fisher.) Stray debug
                // console.log removed.
                sb.append("  function _tCritCI(df,level) { return (_sAgg && _sAgg.tCrit) ? _sAgg.tCrit(df, level) : 1.96; }\n");
                // Build CI labels and datasets from filtered rows
                sb.append("  var _ciMeta = window._smeta || {};\n");
                sb.append("  var _ciOver = _ciMeta.overVar;\n");
                sb.append("  var _ciVars = _ciMeta.plotVars || [];\n");
                sb.append("  var _ciLvl  = _ciMeta.cilevel || 95;\n");
                sb.append("  var _ciOvLbls = _ciMeta.overLabels || [];\n");
                // F1 (t2j): match rows to over-groups by CATEGORY INDEX via _si (the
                // proven-correct path _sAgg.aggregate uses), not by string-comparing a
                // raw _sd numeric code to the (possibly value-labeled / relabeled) group
                // label. The non-missing prefix of _sc/_si aligns with overLabels by
                // position, so row r is in group _gi iff _si[overVar][r] === _gi. The old
                // raw-_sd compare only worked when codes equalled label text (e.g. rep78)
                // and silently mis-grouped value-labeled over vars (e.g. foreign).
                sb.append("  var _ciSiOv = (window._si && _ciOver) ? window._si[_ciOver] : null;\n");
                if (o.type.equals("cibar")) {
                // Single pass over ALL groups: keeps x-positions and colors aligned.
                // Groups with n<2 get {y:null} placeholder (bar hidden, position kept).
                // This matches Stata: all x-axis labels shown, empty bar for n<2 groups.
                sb.append("  var _ciDsets = [];\n");
                sb.append("  for (var _vi=0; _vi < _ciVars.length; _vi++) {\n");
                sb.append("    var _ciData = [];\n");
                sb.append("    for (var _ci=0; _ci < _ciOvLbls.length; _ci++) {\n");
                sb.append("      var _ciGi = _ci;\n");
                sb.append("      var _ciGrpR2 = rows.filter(function(r){\n");
                sb.append("        if (!_ciSiOv) return true;\n");
                sb.append("        return _ciSiOv[r] === _ciGi;\n");
                sb.append("      });\n");
                sb.append("      var _gs = _sAgg.buildGroupStats(_ciGrpR2, _ciVars[_vi]);\n");
                sb.append("      if (!_gs || _gs.n < 2) { _ciData.push({y:null,yMin:null,yMax:null,n:0}); continue; }\n");
                sb.append("      var _se = _gs.sd / Math.sqrt(_gs.n);\n");
                sb.append("      var _hw = _tCritCI(_gs.n - 1, _ciLvl) * _se;\n");
                sb.append("      _ciData.push({y:_gs.mean, yMin:_gs.mean-_hw, yMax:_gs.mean+_hw, n:_gs.n});\n");
                sb.append("    }\n");
                sb.append("    var _tmplDs = (typeof _initDatasets !== 'undefined' && _initDatasets[_vi]) ? _initDatasets[_vi] : {};\n");
                sb.append("    _ciDsets.push(Object.assign({}, _tmplDs, {data: _ciData}));\n");
                sb.append("  }\n");
                sb.append("  _initChart(_ciOvLbls.slice(), _ciDsets);\n");
                } else {
                    // ciline: 3 datasets per plotVar in ciLineDatasets() order [mean, upper, lower]
                    // For simplicity reuse existing dataset styles, just update data arrays
                    sb.append("  if (_mainChart && _ciOvLbls.length > 0) {\n");
                    sb.append("    var _ciNDs = _mainChart.data.datasets.length;\n");
                    sb.append("    var _ciVarN = _ciVars.length;\n");
                    sb.append("    var _ciStride = (_ciNDs > 0 && _ciVarN > 0) ? Math.round(_ciNDs / _ciVarN) : 3;\n");
                    sb.append("    for (var _vii=0; _vii < _ciVarN; _vii++) {\n");
                    sb.append("      var _upData=[],_loData=[],_mnData=[];\n");
                    sb.append("      for (var _vjj=0; _vjj < _ciOvLbls.length; _vjj++) {\n");  // t2i: was _ciValidIdx (never declared -> ReferenceError broke ciline filtering; Astra). Iterate all over-groups like cibar.
                    sb.append("        var _ciGj = _vjj;\n");
                    sb.append("        var _ciGrpR2 = rows.filter(function(r){\n");   // F1: index match via _si
                    sb.append("          if (!_ciSiOv) return true;\n");
                    sb.append("          return _ciSiOv[r] === _ciGj;\n");
                    sb.append("        });\n");
                    sb.append("        var _gs2 = _sAgg.buildGroupStats(_ciGrpR2, _ciVars[_vii]);\n");
                    sb.append("        if (!_gs2 || _gs2.n < 2) { _upData.push(null); _loData.push(null); _mnData.push(null); continue; }\n");
                    sb.append("        var _se2 = _gs2.sd / Math.sqrt(_gs2.n);\n");
                    sb.append("        var _hw2 = _tCritCI(_gs2.n - 1, _ciLvl) * _se2;\n");
                    sb.append("        _upData.push(_gs2.mean + _hw2);\n");
                    sb.append("        _loData.push(_gs2.mean - _hw2);\n");
                    sb.append("        _mnData.push(_gs2.mean);\n");
                    sb.append("      }\n");
                    sb.append("      var _base = _vii * _ciStride;\n");
                    // t2j fix8 (deep-dive r3): ciLineDatasets() builds each plotVar's three
                    // datasets in the order [mean (order:0), upper, lower] (DatasetBuilder
                    // ~985/1004/1012). The filter recompute previously wrote them as
                    // [upper, lower, mean], so ANY filter change (even "All") rotated the
                    // series -- the mean line jumped to the upper-CI value and the band
                    // broke. Write each array into its matching slot.
                    sb.append("      if (_mainChart.data.datasets[_base])   _mainChart.data.datasets[_base].data   = _mnData;\n");
                    sb.append("      if (_mainChart.data.datasets[_base+1]) _mainChart.data.datasets[_base+1].data = _upData;\n");
                    sb.append("      if (_mainChart.data.datasets[_base+2]) _mainChart.data.datasets[_base+2].data = _loData;\n");
                    sb.append("    }\n");
                    sb.append("    _mainChart.data.labels = _ciOvLbls.slice();\n");  // t2i: was _ciLabels (undeclared)
                    sb.append("    _mainChart.update('none');\n");
                    sb.append("  }\n");
                }
                sb.append("  var nc = document.getElementById('_nObs');\n");
                sb.append("  if (nc) nc.textContent = rows.length + ' obs';\n");
            } else if (isHistogram) {
                // Histogram filter: bin edges are fixed; recount observations per bin
                // from filtered rows using histBins edges embedded in _smeta.
                sb.append("  var rows = _sAgg.filterRows(catState, sldState);\n");
                sb.append("  var _hm = window._smeta || {};\n");
                sb.append("  var _hBins = _hm.histBins;\n");
                sb.append("  var _hType = _hm.histType || 'density';\n");
                sb.append("  var _hBW   = _hm.histBinWidth || 1;\n");
                sb.append("  if (!_hBins || _hBins.length < 2) return;\n");
                sb.append("  var _hN    = _hBins.length - 1;\n");  // number of bins
                // Get the plot variable column
                sb.append("  var _hPv   = (_hm.plotVars && _hm.plotVars[0]) ? _hm.plotVars[0] : null;\n");
                sb.append("  if (!_hPv) return;\n");
                // Count filtered rows into bins
                sb.append("  var _hCnts = new Array(_hN).fill(0);\n");
                sb.append("  var _hMin  = _hBins[0];\n");
                sb.append("  var _hVals = _sAgg.getValues(rows, _hPv);\n");
                sb.append("  for (var _hi=0; _hi < _hVals.length; _hi++) {\n");
                sb.append("    var _hv = _hVals[_hi];\n");
                sb.append("    var _hb = Math.min(_hN-1, Math.max(0, Math.floor((_hv - _hMin) / _hBW)));\n");
                sb.append("    _hCnts[_hb]++;\n");
                sb.append("  }\n");
                // Convert counts to y-values based on histType.
                // S6: normalize fraction/density by NON-MISSING N (_hVals.length),
                // not rows.length -- rows may include obs missing the plot var, and
                // Stata's histogram density/fraction divides by the count of
                // non-missing values (matches ChartRenderer initial render).
                sb.append("  var _hTotal = _hVals.length || 1;\n");
                sb.append("  var _hYVals = _hCnts.map(function(c) {\n");
                sb.append("    if (_hType === 'frequency') return c;\n");
                sb.append("    if (_hType === 'fraction')  return c / _hTotal;\n");
                sb.append("    return c / (_hTotal * _hBW);\n");  // density (default)
                sb.append("  });\n");
                sb.append("  if (!_mainChart) return;\n");
                sb.append("  _mainChart.data.datasets[0].data = _hYVals;\n");
                sb.append("  _mainChart.update('none');\n");
                sb.append("  var nc = document.getElementById('_nObs');\n");
                sb.append("  if (nc) nc.textContent = rows.length + ' obs';\n");
            } else if (isBoxViolin) {
                // Boxplot/violin filter: must destroy+reinit since plugin uses
                // pre-computed stats objects (boxplot) or raw number[] (violin).
                sb.append("  var rows = _sAgg.filterRows(catState, sldState);\n");
                sb.append("  var _bm   = window._smeta || {};\n");
                sb.append("  var _bOv  = _bm.overVar;\n");
                sb.append("  var _bPv  = (_bm.plotVars && _bm.plotVars[0]) ? _bm.plotVars[0] : null;\n");
                sb.append("  var _bLbls= _bm.overLabels || [];\n");
                sb.append("  if (!_bPv) return;\n");
                // t2i: violin is created inside _initChart and never declares _mainChart
                // in this scope, so referencing _mainChart here threw ReferenceError and
                // broke violin filtering (Astra). Resolve the live instance defensively
                // (boxplot's _mainChart if present, else by canvas id).
                sb.append("  var _bChart = (typeof _mainChart !== 'undefined' && _mainChart) ? _mainChart : ((window.Chart && Chart.getChart) ? Chart.getChart('mainChart') : null);\n");
                sb.append("  var _bIsViolin = (_bm.chartType === 'violin' || _bm.chartType === 'hviolinplot');\n");
                // Boxplot whisker fence k -- read from first dataset's options if available
                // t2j: carry the user's whiskerfence(k) into the filter refresh
                // (was hardcoded 1.5 -> filtered boxplot ignored whiskerfence()).
                double _wf = 1.5;
                try { _wf = Double.parseDouble(o.stats.whiskerfence.trim()); } catch (Exception ignore) {}
                sb.append("  var _bK = ").append(_wf).append(";\n");
                // JS boxplot stats helper -- v3.6.0-t2j: Stata DEFAULT percentile
                // (i=n*p/100; integer -> avg(x[i],x[i+1]); else x[ceil(i)]), matching
                // DatasetBuilder.percentile and the engine _pctile (was altdef-like).
                sb.append("  function _bPctile(sorted, p) {\n");
                sb.append("    var n=sorted.length; if(n===0)return null; if(n===1)return sorted[0];\n");
                sb.append("    var i=n*p/100, fi=Math.floor(i);\n");
                sb.append("    if(Math.abs(i-fi)<1e-9){var k=fi; if(k<1)return sorted[0]; if(k>=n)return sorted[n-1]; return (sorted[k-1]+sorted[k])/2;}\n");
                sb.append("    var c=Math.ceil(i); if(c<1)c=1; if(c>n)c=n; return sorted[c-1];\n");
                sb.append("  }\n");
                sb.append("  function _bStats(vals) {\n");
                sb.append("    if(!vals||vals.length===0)return null;\n");
                sb.append("    var s=vals.slice().sort(function(a,b){return a-b;});\n");
                sb.append("    var n=s.length,sum=0; for(var i=0;i<n;i++)sum+=s[i];\n");
                sb.append("    var mean=sum/n,q1=_bPctile(s,25),q3=_bPctile(s,75),median=_bPctile(s,50);\n");
                sb.append("    var iqr=q3-q1, wLo=s[0], wHi=s[n-1];\n");
                sb.append("    for(var j=0;j<n;j++){if(s[j]>=q1-_bK*iqr){wLo=s[j];break;}}\n");
                sb.append("    for(var j2=n-1;j2>=0;j2--){if(s[j2]<=q3+_bK*iqr){wHi=s[j2];break;}}\n");
                sb.append("    var outs=[];\n");
                sb.append("    for(var k2=0;k2<n;k2++){if(s[k2]<q1-_bK*iqr||s[k2]>q3+_bK*iqr)outs.push(s[k2]);}\n");
                sb.append("    return {min:wLo,q1:q1,median:median,mean:mean,q3:q3,max:wHi,outliers:outs};\n");
                sb.append("  }\n");
                // Collect filtered non-missing values per over-group.
                // t2j: group iteration is driven by the LABEL set (_bLbls), not by the
                // live dataset count. For violin, _vAnimateTo collapses the chart to a
                // single anchor dataset, so iterating _bChart.data.datasets would see
                // only 1 group on the SECOND filter and drop the rest.
                sb.append("  var _bGroups = (_bLbls && _bLbls.length) ? _bLbls.slice() : [null];\n");
                // F1 (t2j): group by category INDEX via _si (matches _sAgg.aggregate),
                // not by comparing a raw _sd code to the group label -- the latter
                // mis-grouped value-labeled over vars (e.g. foreign 0/1 vs Domestic/Foreign).
                sb.append("  var _bSiOv = (_bOv && window._si) ? window._si[_bOv] : null;\n");
                sb.append("  var _bGroupVals = [];\n");
                sb.append("  for (var _bdi=0; _bdi < _bGroups.length; _bdi++) {\n");
                sb.append("    var _bVals = [];\n");
                sb.append("    for (var _bri=0; _bri < rows.length; _bri++) {\n");
                sb.append("      var _br = rows[_bri];\n");
                if (data.hasOver()) {
                    sb.append("      if (_bSiOv && _bSiOv[_br] !== _bdi) continue;\n");
                }
                sb.append("      var _bvArr = _sAgg.getValues([_br], _bPv);\n");
                sb.append("      if (_bvArr.length > 0) _bVals.push(_bvArr[0]);\n");
                sb.append("    }\n");
                sb.append("    _bGroupVals.push(_bVals);\n");
                sb.append("  }\n");
                // Violin: recompute the KDE shape + box overlay from filtered values via
                // the IIFE's _vFilter (tweens through _vAnimateTo). Boxplot: reinit with
                // fresh stats objects (the plugin caches pre-computed stats).
                // t2j fix9d: violin recomputes EVERY entry (group x variable) -- _vFilter takes
                // a (gi, vi) -> values function and walks its own _vOrigData; gi is the global
                // over index (matches _bSiOv), -1 without over(). _bGroupVals (first variable
                // only) stays for the boxplot branch below.
                sb.append("  var _bPvs = (_bm.plotVars && _bm.plotVars.length) ? _bm.plotVars : [_bPv];\n");
                sb.append("  if (_bIsViolin) {\n");
                sb.append("    if (typeof window._vFilter === 'function') window._vFilter(function(_gi, _vi) {\n");
                sb.append("      var _pv = _bPvs[_vi] || _bPv, _rr = rows;\n");
                sb.append("      if (_gi >= 0 && _bSiOv) { _rr = []; for (var _q = 0; _q < rows.length; _q++) { if (_bSiOv[rows[_q]] === _gi) _rr.push(rows[_q]); } }\n");
                sb.append("      return _sAgg.getValues(_rr, _pv);\n");
                sb.append("    }, _bK);\n");
                sb.append("  } else {\n");
                sb.append("    var _bNewDsets = [];\n");
                sb.append("    var _bExDsets = (_bChart && _bChart.data.datasets) ? _bChart.data.datasets : [];\n");
                // t2j fix8f (issue #9): single-variable over() boxplots use the
                // colorByGroup layout = ONE dataset whose data[] holds one box PER GROUP
                // (see DatasetBuilder.boxplotDatasets). The old rebuild iterated by dataset
                // count and read only _bGroupVals[0], emitting a length-1 data[] -- so every
                // over-group past index 0 (e.g. Foreign, the last group) was dropped on ANY
                // filter, even when it still had observations. Rebuild driven by the GROUP
                // set so the box count keeps matching _bChart.data.labels.
                sb.append("    if (_bExDsets.length === 1 && _bGroups.length > 1) {\n");
                sb.append("      var _bData0 = [];\n");
                sb.append("      for (var _bgi=0; _bgi < _bGroupVals.length; _bgi++) _bData0.push(_bStats(_bGroupVals[_bgi]));\n");
                sb.append("      _bNewDsets.push(Object.assign({}, _bExDsets[0], {data: _bData0}));\n");
                sb.append("    } else {\n");
                sb.append("      for (var _bxi=0; _bxi < _bExDsets.length; _bxi++) {\n");
                sb.append("        var _bgv = _bGroupVals[_bxi] || [];\n");
                sb.append("        _bNewDsets.push(Object.assign({}, _bExDsets[_bxi], {data: [_bStats(_bgv)]}));\n");
                sb.append("      }\n");
                sb.append("    }\n");
                sb.append("    if (typeof _initChart === 'function' && _bChart) {\n");
                sb.append("      _initChart(_bChart.data.labels, _bNewDsets);\n");
                sb.append("    } else if (_bChart) {\n");
                sb.append("      for (var _bui=0; _bui < _bNewDsets.length; _bui++) {\n");
                sb.append("        if (_bChart.data.datasets[_bui]) _bChart.data.datasets[_bui].data = _bNewDsets[_bui].data;\n");
                sb.append("      }\n");
                sb.append("      _bChart.update('none');\n");
                sb.append("    }\n");
                sb.append("  }\n");
                sb.append("  var nc = document.getElementById('_nObs');\n");
                sb.append("  if (nc) nc.textContent = rows.length + ' obs';\n");
            } else if (isPie) {
                // F3 (t2j): pie/donut slice values are SUM (mode1 multi-var / mode2
                // one var + over) or COUNT (mode3 no var + over) -- independent of stat();
                // the chart stores percentages by default (usePct) with raw values in
                // _spkPieRaw for the tooltip. The generic mean-aggregate path diverged
                // (mode2) and produced nothing for mode3 (no plot var -> frozen slices).
                sb.append("  var rows = _sAgg.filterRows(catState, sldState);\n");
                sb.append("  if (!_mainChart) return;\n");
                boolean usePct = !o.stats.stat.equals("sum");
                boolean pieHasOver = data.hasOver();
                List<Variable> pnv = data.getNumericVariables();
                boolean pieMode3 = pnv.isEmpty() && pieHasOver;   // frequency count by group
                boolean pieMode2 = !pnv.isEmpty() && pieHasOver;  // one var summed per group
                // mode1 = multi-var, no over (each var one slice = sum over rows)
                sb.append("  var _pieRaw = [];\n");
                if (pieMode3) {
                    String ovN = DataEmbedder.jsStr(data.getOverVariable().getName());
                    sb.append("  var _pSi = (window._si && window._si[").append(ovN).append("]) || null;\n");
                    sb.append("  var _pSc = (window._sc && window._sc[").append(ovN).append("]) || [];\n");
                    sb.append("  for (var _pg=0; _pg<_pSc.length; _pg++) _pieRaw.push(0);\n");
                    sb.append("  if (_pSi) for (var _pr=0; _pr<rows.length; _pr++) { var _pgi=_pSi[rows[_pr]]; if(_pgi>=0 && _pgi<_pieRaw.length) _pieRaw[_pgi]++; }\n");
                } else if (pieMode2) {
                    String ovN = DataEmbedder.jsStr(data.getOverVariable().getName());
                    String pv0 = DataEmbedder.jsStr(pnv.get(0).getName());
                    sb.append("  var _pSi = (window._si && window._si[").append(ovN).append("]) || null;\n");
                    sb.append("  var _pSc = (window._sc && window._sc[").append(ovN).append("]) || [];\n");
                    sb.append("  var _pCol = _sAgg.column(").append(pv0).append(");\n");
                    sb.append("  for (var _pg=0; _pg<_pSc.length; _pg++) _pieRaw.push(0);\n");
                    sb.append("  if (_pSi && _pCol) for (var _pr=0; _pr<rows.length; _pr++) { var _rr=rows[_pr]; var _pgi=_pSi[_rr]; var _pv=_pCol[_rr]; if(_pgi>=0 && _pgi<_pieRaw.length && _pv!==null && _pv!==undefined && !isNaN(_pv)) _pieRaw[_pgi]+=_pv; }\n");
                } else {
                    // mode1: one slice per numeric var = sum across filtered rows
                    sb.append("  var _pCols = [");
                    for (int i = 0; i < pnv.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append("_sAgg.column(").append(DataEmbedder.jsStr(pnv.get(i).getName())).append(")");
                    }
                    sb.append("];\n");
                    sb.append("  for (var _pc=0; _pc<_pCols.length; _pc++) { var _ps=0, _c=_pCols[_pc]; if(_c) for (var _pr=0; _pr<rows.length; _pr++) { var _pv=_c[rows[_pr]]; if(_pv!==null && _pv!==undefined && !isNaN(_pv)) _ps+=_pv; } _pieRaw.push(_ps); }\n");
                }
                sb.append("  var _pieTot=0; for (var _pt=0; _pt<_pieRaw.length; _pt++) _pieTot+=Math.abs(_pieRaw[_pt]); if(_pieTot===0)_pieTot=1;\n");
                sb.append("  var _pieData = _pieRaw.map(function(v){ return ").append(usePct ? "100*v/_pieTot" : "v").append("; });\n");
                sb.append("  if (typeof _spkPieRaw !== 'undefined' && _spkPieRaw) { _spkPieRaw.length=0; for (var _pk=0; _pk<_pieRaw.length; _pk++) _spkPieRaw.push(_pieRaw[_pk]); }\n");
                sb.append("  if (_mainChart.data.datasets[0]) { _mainChart.data.datasets[0].data = _pieData; _mainChart.data.datasets[0]._spkRaw = _pieRaw.slice(); }\n");   // fix9a: keep the dataset raw store in step
                sb.append("  _mainChart.update('none');\n");
                sb.append("  var nc = document.getElementById('_nObs');\n");
                sb.append("  if (nc) nc.textContent = rows.length + ' obs';\n");
            } else if (isStacked100) {
                // F4 (t2j): 100%-stacked bars renormalize so each stacked column sums to
                // 100%. The initial render pre-normalizes (per-var column total across
                // groups, CASE A over(); per total across vars, CASE B no over()); the
                // generic filter path emitted raw aggregates in a scrambled layout.
                sb.append("  var rows = _sAgg.filterRows(catState, sldState);\n");
                sb.append("  if (!_mainChart) return;\n");
                String stat = DataEmbedder.jsStr(o.stats.stat);
                List<Variable> snv = data.getNumericVariables();
                sb.append("  var _s100Cols = [");
                for (int i = 0; i < snv.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("_sAgg.column(").append(DataEmbedder.jsStr(snv.get(i).getName())).append(")");
                }
                sb.append("];\n");
                sb.append("  var _s100Stat = ").append(stat).append(";\n");
                if (data.hasOver()) {
                    // CASE A: raw[vi][gi] over filtered rows; colTotal[vi] = sum_gi |raw|;
                    // datasets are per-GROUP (gi), each holding one value per var (vi).
                    String ovN = DataEmbedder.jsStr(data.getOverVariable().getName());
                    sb.append("  var _sSi = (window._si && window._si[").append(ovN).append("]) || null;\n");
                    sb.append("  var _sSc = (window._sc && window._sc[").append(ovN).append("]) || [];\n");
                    sb.append("  var _nG=_sSc.length, _nV=_s100Cols.length;\n");
                    sb.append("  var _buk=[]; for (var _v=0;_v<_nV;_v++){ _buk.push([]); for (var _g=0;_g<_nG;_g++) _buk[_v].push([]); }\n");
                    sb.append("  if (_sSi) for (var _r=0;_r<rows.length;_r++){ var _row=rows[_r]; var _gi=_sSi[_row]; if(_gi<0||_gi>=_nG)continue; for (var _v2=0;_v2<_nV;_v2++){ var _c=_s100Cols[_v2]; if(!_c)continue; var _val=_c[_row]; if(_val!==null&&_val!==undefined&&!isNaN(_val)) _buk[_v2][_gi].push(_val); } }\n");
                    sb.append("  var _raw=[]; for (var _v3=0;_v3<_nV;_v3++){ _raw.push([]); for (var _g2=0;_g2<_nG;_g2++){ var _b=_buk[_v3][_g2]; _raw[_v3].push(_b.length? _sAgg.computeStat(_b,_s100Stat) : NaN); } }\n");
                    sb.append("  var _colT=[]; for (var _v4=0;_v4<_nV;_v4++){ var _t=0; for (var _g3=0;_g3<_nG;_g3++){ var _rv=_raw[_v4][_g3]; if(!isNaN(_rv)) _t+=Math.abs(_rv); } _colT.push(_t); }\n");
                    // datasets[gi].data[vi] = raw[vi][gi]/colTotal[vi]*100
                    sb.append("  for (var _g4=0;_g4<_nG && _g4<_mainChart.data.datasets.length;_g4++){ var _d=[]; for (var _v5=0;_v5<_nV;_v5++){ var _rv2=_raw[_v5][_g4]; _d.push(isNaN(_rv2)?null:(_colT[_v5]>0?_rv2/_colT[_v5]*100:0)); } _mainChart.data.datasets[_g4].data=_d; }\n");
                } else {
                    // CASE B: one dataset per var, single data point = raw[vi]/totalAllVars*100
                    sb.append("  var _nV=_s100Cols.length; var _rawB=[];\n");
                    sb.append("  for (var _v=0;_v<_nV;_v++){ var _c=_s100Cols[_v], _b=[]; if(_c) for (var _r=0;_r<rows.length;_r++){ var _val=_c[rows[_r]]; if(_val!==null&&_val!==undefined&&!isNaN(_val)) _b.push(_val); } _rawB.push(_b.length? _sAgg.computeStat(_b,_s100Stat):NaN); }\n");
                    sb.append("  var _totB=0; for (var _v2=0;_v2<_nV;_v2++){ if(!isNaN(_rawB[_v2])) _totB+=Math.abs(_rawB[_v2]); } if(_totB===0)_totB=1;\n");
                    sb.append("  for (var _v3=0;_v3<_nV && _v3<_mainChart.data.datasets.length;_v3++){ var _rv=_rawB[_v3]; _mainChart.data.datasets[_v3].data=[isNaN(_rv)?null:_rv/_totB*100]; }\n");
                }
                sb.append("  _mainChart.update('none');\n");
                sb.append("  var nc = document.getElementById('_nObs');\n");
                sb.append("  if (nc) nc.textContent = rows.length + ' obs';\n");
            } else {
                // Filter once; pass rows to buildChartDataFromRows (avoids double filter, #1).
                sb.append("  var rows = _sAgg.filterRows(catState, sldState);\n");
                sb.append("  var result = _sAgg.buildChartDataFromRows(rows);\n");
                sb.append("  if (!_mainChart) return;\n");
                sb.append("  _mainChart.data.labels   = result.labels;\n");
                sb.append("  for (var di = 0; di < result.datasets.length && di < _mainChart.data.datasets.length; di++) {\n");
                sb.append("    _mainChart.data.datasets[di].data = result.datasets[di].data;\n");
                sb.append("  }\n");
                sb.append("  _mainChart.update('none');\n");
                sb.append("  var nc = document.getElementById('_nObs');\n");
                sb.append("  if (nc) nc.textContent = result.nActive + ' obs';\n");
            }
        }
        // F-2: update stats panel N badges to reflect filtered row counts.
        // querySelector('#g0 .grp-badge') finds the badge inside each group div.
        // g0=Overall, g1..gN=over groups, gN+1..=by groups (matches StatsRenderer bi order).
        sb.append("  _sparkta_updateStatsBadges(rows);\n");
        sb.append("}\n");

        sb.append("var _applyFilter = _onFilterChange;\n");
        // IO2 (t2j): with noallfilter there is no "All" option, so the dropdown starts
        // on category 0 -- but the initial chart is server-rendered from the FULL data,
        // contradicting the selection. Fire the filter once after load so the chart
        // matches the selected category from the start.
        if (o.style.noAllFilter && data.hasFilter()) {
            sb.append("if(typeof _applyFilter==='function'){setTimeout(function(){try{_applyFilter();}catch(e){}},0);}\n");
        }
        sb.append(_buildStatsBadgeHelper(data));

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Backward-compat facades called by HtmlGenerator
    // These map old 4-method API onto the new single-path API.
    // -------------------------------------------------------------------------

    /**
     * _buildStatsBadgeHelper -- F-2: emits _sparkta_updateStatsBadges(rows) JS function.
     *
     * Updates the N badge in each stats panel group header to reflect the
     * filtered row count. Uses the existing DOM IDs (g0=Overall, g1..=over groups,
     * gN+1..=by groups) emitted by StatsRenderer.buildStatsBlock().
     *
     * querySelector('#g0 .grp-badge') finds the badge span inside each group div.
     * Zero changes to StatsRenderer -- works with existing DOM structure.
     */
    /**
     * _buildStatsBadgeHelper -- F-2A: emits stats update JS functions.
     *
     * Emits helper functions FIRST, then _sparkta_updateStatsBadges which calls them.
     * This ordering eliminates any risk of nesting: all functions are top-level siblings.
     *
     * Functions emitted (in order):
     *   _fmtStat, _cvClass, _updateSpk, _updateStatsGroup  (helpers)
     *   _sparkta_updateStatsBadges(rows)                    (main, calls helpers)
     */
    private String _buildStatsBadgeHelper(DataSet data) {
        // t2j fix4 (ported from Astra rc1): _onFilterChange calls
        // _sparkta_updateStatsBadges(rows) unconditionally, so under nostats it must
        // still be DEFINED or every filter/slider change throws a ReferenceError.
        // Emit a no-op instead of nothing.
        if (o.chart.nostats) return "function _sparkta_updateStatsBadges(rows) {}\n";
        StringBuilder sb = new StringBuilder();

        // --- Helpers first (defined before _sparkta_updateStatsBadges calls them) ---

        sb.append("function _fmtStat(v) {\n");
        sb.append("  if (v === null || v === undefined) return '--';\n");
        sb.append("  var av = Math.abs(v);\n");
        sb.append("  if (av === 0) return '0';\n");
        sb.append("  if (av >= 1000) return v.toLocaleString(undefined,{minimumFractionDigits:0,maximumFractionDigits:2});\n");
        sb.append("  if (av >= 1) return parseFloat(v.toFixed(2)).toString();\n");
        sb.append("  return parseFloat(v.toPrecision(4)).toString();\n");
        sb.append("}\n");

        sb.append("function _cvClass(cv) {\n");
        sb.append("  return cv < 0.15 ? 'cv cv-low' : cv < 0.35 ? 'cv cv-med' : 'cv cv-high';\n");
        sb.append("}\n");

        // t2j fix6: honor whiskerfence(k) in the sparkline outlier coloring on filter (was a
        // hardcoded 1.5, disagreeing with the boxplot under whiskerfence(3)).
        String _wfkJs = "1.5";
        try { double _k = Double.parseDouble(o.stats.whiskerfence.trim()); if (_k > 0) _wfkJs = String.valueOf(_k); } catch (Exception ignore) {}
        sb.append("function _updateSpk(gi, vi, s, mn, mx, vals) {\n");
        sb.append("  if (s === null) return;\n");
        sb.append("  var range = mx - mn || 1;\n");
        sb.append("  function px(v) { return Math.round(2 + (v - mn) / range * 106); }\n");
        // s9a (user-found): the dot cloud and range labels were static build-time SVG and
        // never followed the filter. Rebuild them from the filtered values; hide at n=0.
        sb.append("  var mxl = document.getElementById('spk_'+gi+'_'+vi+'_mx'); if (mxl) mxl.textContent = s.n ? _fmtStat(s.max) : '--';\n");
        sb.append("  var mnl = document.getElementById('spk_'+gi+'_'+vi+'_mn'); if (mnl) mnl.textContent = s.n ? _fmtStat(s.min) : '--';\n");
        sb.append("  var iqrEl0 = document.getElementById('spk_'+gi+'_'+vi+'_iqr'); var svg = iqrEl0 ? iqrEl0.parentNode : null;\n");
        sb.append("  if (svg) {\n");
        sb.append("    Array.prototype.slice.call(svg.querySelectorAll('.spk-dot')).forEach(function(d){ d.parentNode.removeChild(d); });\n");
        sb.append("    var ph = svg.nextElementSibling; var cy = (ph && ph.getAttribute('data-cy')) || '14';\n");
        sb.append("    if (ph && ph.className === 'spark-ph') { ph.setAttribute('data-in',''); ph.setAttribute('data-out',''); }\n");
        sb.append("    ['iqr','mean','med'].forEach(function(t){ var e=document.getElementById('spk_'+gi+'_'+vi+'_'+t); if (e) e.style.display = s.n ? '' : 'none'; });\n");
        sb.append("    if (s.n && vals) {\n");
        sb.append("      var q1 = s.q1, q3 = s.q3, iqr = (q3 !== null && q1 !== null) ? (q3 - q1) : 0, lo = q1 - " + _wfkJs + "*iqr, hi = q3 + " + _wfkJs + "*iqr;\n");
        sb.append("      var mxDots = 400, step = vals.length > mxDots ? vals.length / mxDots : 1;\n");
        sb.append("      for (var k = 0; k < vals.length; k += step) { var v = vals[Math.floor(k)]; var c = document.createElementNS('http://www.w3.org/2000/svg','circle');\n");
        sb.append("        var out = (v < lo || v > hi); c.setAttribute('cx', px(v)); c.setAttribute('cy', cy); c.setAttribute('r','2.2'); c.setAttribute('class','spk-dot');\n");
        sb.append("        c.setAttribute('fill', out ? '#e74c3c' : '#4e79a7'); c.setAttribute('opacity', out ? '.8' : '.55'); svg.appendChild(c); }\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("  var iqrEl = document.getElementById('spk_'+gi+'_'+vi+'_iqr');\n");
        sb.append("  if (iqrEl && s.q1 !== null && s.q3 !== null) {\n");
        sb.append("    var x1=px(s.q1), x2=px(s.q3);\n");
        sb.append("    iqrEl.setAttribute('x', x1);\n");
        sb.append("    iqrEl.setAttribute('width', Math.max(2, x2-x1));\n");
        sb.append("  }\n");
        sb.append("  var mEl = document.getElementById('spk_'+gi+'_'+vi+'_mean');\n");
        sb.append("  if (mEl && s.mean !== null) { var mx2=px(s.mean); mEl.setAttribute('x1',mx2); mEl.setAttribute('x2',mx2); }\n");
        sb.append("  var mdEl = document.getElementById('spk_'+gi+'_'+vi+'_med');\n");
        sb.append("  if (mdEl && s.median !== null) { var md2=px(s.median); mdEl.setAttribute('x1',md2); mdEl.setAttribute('x2',md2); }\n");
        sb.append("}\n");

        sb.append("function _updateStatsGroup(gi, groupRows, plotVars) {\n");
        sb.append("  for (var vi=0; vi<plotVars.length; vi++) {\n");
        sb.append("    var s = _sAgg.buildGroupStats(groupRows, plotVars[vi]);\n");
        sb.append("    if (!s) continue;\n");
        sb.append("    var pfx = 'stc_'+gi+'_'+vi+'_';\n");
        sb.append("    var nb = document.getElementById(pfx+'n'); if (nb) nb.textContent = s.n;\n");
        sb.append("    var mc = document.getElementById(pfx+'m'); if (mc) { mc.textContent = _fmtStat(s.mean); mc.setAttribute('data-v', s.mean !== null ? s.mean : ''); }\n");
        sb.append("    var md = document.getElementById(pfx+'md'); if (md) { md.textContent = _fmtStat(s.median); md.setAttribute('data-v', s.median !== null ? s.median : ''); }\n");
        sb.append("    var mn = document.getElementById(pfx+'mn'); if (mn) { mn.textContent = _fmtStat(s.min); mn.setAttribute('data-v', s.min !== null ? s.min : ''); }\n");
        sb.append("    var mx = document.getElementById(pfx+'mx'); if (mx) { mx.textContent = _fmtStat(s.max); mx.setAttribute('data-v', s.max !== null ? s.max : ''); }\n");
        sb.append("    var sdEl = document.getElementById(pfx+'sd');\n");
        sb.append("    if (sdEl) {\n");
        sb.append("      var cvBadge = document.getElementById(pfx+'cvb');\n");
        sb.append("      if (s.n >= 2 && s.sd !== null) {\n");
        sb.append("        var cvPct = s.cv !== null ? Math.round(s.cv*100) : 0;\n");
        sb.append("        if (cvBadge) { cvBadge.textContent = 'CV '+cvPct+'%'; cvBadge.className = _cvClass(s.cv !== null ? s.cv : 0); cvBadge.style.display=''; }\n");
        sb.append("        sdEl.textContent = _fmtStat(s.sd) + ' '; sdEl.setAttribute('data-v', s.sd);\n");
        sb.append("        if (cvBadge) sdEl.appendChild(cvBadge);\n");
        sb.append("      } else {\n");   // s9a: n < 2 -> no dispersion; never leave a stale value
        sb.append("        sdEl.textContent = '--'; sdEl.setAttribute('data-v', ''); if (cvBadge) { cvBadge.style.display='none'; sdEl.appendChild(cvBadge); }\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("    var dmn = s.min !== null ? s.min : 0, dmx = s.max !== null ? s.max : 0;\n");
        sb.append("    var col = (window._sAgg && _sAgg.column) ? _sAgg.column(plotVars[vi]) : null; var vals = [];\n");
        sb.append("    if (col) for (var ri = 0; ri < groupRows.length; ri++) { var vv = col[groupRows[ri]]; if (vv !== null && vv !== undefined && !isNaN(vv)) vals.push(vv); }\n");
        sb.append("    _updateSpk(gi, vi, s, dmn, dmx, vals);\n");
        sb.append("  }\n");
        sb.append("}\n");

        // --- Main function: _sparkta_updateStatsBadges(rows) ---
        // Calls helpers defined above. Clean open/close -- no conditional branches
        // that could leave the brace unclosed.
        sb.append("function _sparkta_updateStatsBadges(rows) {\n");
        sb.append("  function _setBadge(idx, n) {\n");
        sb.append("    var b = document.getElementById('sbadge_' + idx);\n");
        sb.append("    if (b) b.textContent = 'N=' + n;\n");
        sb.append("  }\n");
        sb.append("  var _sPVars = (window._smeta && window._smeta.plotVars) || [];\n");
        // g0 = Overall
        sb.append("  _setBadge(0, rows.length);\n");
        sb.append("  _updateStatsGroup(0, rows, _sPVars);\n");

        // over() groups: g1, g2, ... -- O(N) single-pass bucket (#7)
        if (data.hasOver()) {
            String ovVar = DataEmbedder.jsStr(data.getOverVariable().getName());
            sb.append("  var _bOvSi = (window._si && window._si[").append(ovVar).append("]) || [];\n");
            sb.append("  var _bOvLbls = (window._smeta && window._smeta.overLabels) || [];\n");
            // Single pass: bucket rows by _si index (integer comparison, no string lookup)
            sb.append("  var _bOBkts = [];\n");
            sb.append("  for (var _bOi=0; _bOi<_bOvLbls.length; _bOi++) _bOBkts[_bOi]=[];\n");
            sb.append("  for (var _bOr=0; _bOr<rows.length; _bOr++) {\n");
            sb.append("    var _bOgi = _bOvSi[rows[_bOr]];\n");
            sb.append("    if (_bOgi !== undefined && _bOgi >= 0 && _bOgi < _bOBkts.length) _bOBkts[_bOgi].push(rows[_bOr]);\n");
            sb.append("  }\n");
            sb.append("  for (var _bOi2=0; _bOi2<_bOvLbls.length; _bOi2++) {\n");
            sb.append("    _setBadge(_bOi2 + 1, _bOBkts[_bOi2].length);\n");
            sb.append("    _updateStatsGroup(_bOi2 + 1, _bOBkts[_bOi2], _sPVars);\n");
            sb.append("  }\n");
        }

        // by() groups -- O(N) single-pass bucket (#7)
        if (data.hasBy()) {
            String byVar = DataEmbedder.jsStr(data.getByVariable().getName());
            int overCount = data.hasOver()
                ? DataSet.uniqueValues(data.getOverVariable(),
                                       o.chart.sortgroups, o.showmissingOver).size()
                : 0;
            sb.append("  var _bBySi = (window._si && window._si[").append(byVar).append("]) || [];\n");
            sb.append("  var _bByGrps = (window._smeta && window._smeta.byGroups) || [];\n");
            sb.append("  var _bByOff = ").append(overCount + 1).append(";\n");
            sb.append("  var _bByBkts = [];\n");
            sb.append("  for (var _bBi=0; _bBi<_bByGrps.length; _bBi++) _bByBkts[_bBi]=[];\n");
            sb.append("  for (var _bBr=0; _bBr<rows.length; _bBr++) {\n");
            sb.append("    var _bBygi = _bBySi[rows[_bBr]];\n");
            sb.append("    if (_bBygi !== undefined && _bBygi >= 0 && _bBygi < _bByBkts.length) _bByBkts[_bBygi].push(rows[_bBr]);\n");
            sb.append("  }\n");
            sb.append("  for (var _bBi2=0; _bBi2<_bByGrps.length; _bBi2++) {\n");
            sb.append("    _setBadge(_bByOff + _bBi2, _bByBkts[_bBi2].length);\n");
            sb.append("    _updateStatsGroup(_bByOff + _bBi2, _bByBkts[_bBi2], _sPVars);\n");
            sb.append("  }\n");
        }

        sb.append("}\n");  // close _sparkta_updateStatsBadges
        return sb.toString();
    }


    /** Old buildFilterData() -- now returns the data block. */
    String buildFilterData(DataSet data) {
        return buildDataBlock(data);
    }

    /** Old buildFilterScript() -- now returns _applyFilter script. */
    String buildFilterScript(DataSet data) {
        return buildApplyFilterScript(data);
    }

    /** buildFilterDataByPanels -- data block is shared with single-chart path. */
    String buildFilterDataByPanels(DataSet data, List<String> byGroupKeys) {
        return buildDataBlock(data);
    }

    /**
     * buildFilterScriptByPanels -- F-3: filter update for by() panel charts.
     *
     * Replaces the F-0 stub. Emits _onFilterChange that:
     *   1. Reads filter/slider state (same as single-chart)
     *   2. Calls filterRows() once for all rows passing user filters
     *   3. For each by()-panel, subsets rows to that panel's by-group,
     *      retrieves the panel's Chart.js instance via Chart.getChart(),
     *      updates its datasets using engine functions, and calls update().
     *   4. Updates stats N badges (F-2) for overall + each group.
     *
     * _updatePanelChart(panelRows, ch): emitted as a local helper to avoid
     * duplicating the scatter/bar branch for each panel.
     *
     * Uses _smeta.byGroups (added v3.5.84) to map panel index to group label.
     * Uses _smeta.chartType to select scatter vs bar/line update path.
     */
    String buildFilterScriptByPanels(DataSet data, List<String> byGroupKeys) {
        if (!data.hasFilter() && !data.hasSliders()) return "";

        StringBuilder sb = new StringBuilder();

        // Slider init (same as single-chart path -- reuse method logic)
        if (data.hasSliders()) {
            // Emit slider init block (duplicated from buildApplyFilterScript for by() path)
            sb.append("(function() {\n");
            sb.append("  var sm = (window._smeta && window._smeta.sliders) ? window._smeta.sliders : [];\n");
            sb.append("  for (var i = 0; i < sm.length; i++) {\n");
            sb.append("    var s=sm[i];\n");
            sb.append("    var lo=document.getElementById('slo_'+s.name);\n");
            sb.append("    var hi=document.getElementById('shi_'+s.name);\n");
            sb.append("    var vlo=document.getElementById('sval_lo_'+s.name);\n");
            sb.append("    var vhi=document.getElementById('sval_hi_'+s.name);\n");
            sb.append("    if(!lo||!hi)continue;\n");
            sb.append("    lo.min=s.min;lo.max=s.max;lo.step=s.step;lo.value=s.min;\n");
            sb.append("    hi.min=s.min;hi.max=s.max;hi.step=s.step;hi.value=s.max;\n");
            sb.append("    if(vlo)vlo.textContent=_fmtNum(s.min);\n");
            sb.append("    if(vhi)vhi.textContent=_fmtNum(s.max);\n");
            sb.append("    _updateSliderFill(s.name);\n");
            sb.append("  }\n");
            sb.append("})();\n");
            sb.append("function _onSliderInput(el,sname,isLo){\n");
            sb.append("  var lo=document.getElementById('slo_'+sname);\n");
            sb.append("  var hi=document.getElementById('shi_'+sname);\n");
            sb.append("  var vlo=document.getElementById('sval_lo_'+sname);\n");
            sb.append("  var vhi=document.getElementById('sval_hi_'+sname);\n");
            sb.append("  if(!lo||!hi)return;\n");
            sb.append("  if(isLo&&parseFloat(lo.value)>parseFloat(hi.value))lo.value=hi.value;\n");
            sb.append("  if(!isLo&&parseFloat(hi.value)<parseFloat(lo.value))hi.value=lo.value;\n");
            sb.append("  if(vlo)vlo.textContent=_fmtNum(parseFloat(lo.value));\n");
            sb.append("  if(vhi)vhi.textContent=_fmtNum(parseFloat(hi.value));\n");
            sb.append("  _updateSliderFill(sname);\n");
            sb.append("}\n");
            sb.append("function _updateSliderFill(sname){\n");
            sb.append("  var lo=document.getElementById('slo_'+sname);\n");
            sb.append("  var hi=document.getElementById('shi_'+sname);\n");
            sb.append("  var fill=document.getElementById('sfill_'+sname);\n");
            sb.append("  if(!lo||!hi||!fill)return;\n");
            sb.append("  var mn=parseFloat(lo.min),mx=parseFloat(lo.max),range=mx-mn||1;\n");
            sb.append("  fill.style.left=((parseFloat(lo.value)-mn)/range*100)+'%';\n");
            sb.append("  fill.style.width=((parseFloat(hi.value)-parseFloat(lo.value))/range*100)+'%';\n");
            sb.append("}\n");
            sb.append("function _fmtNum(v){return(v===Math.floor(v))?v.toFixed(0):v.toFixed(2);}\n");
        }

        // Determine x/y vars for scatter panels
        boolean isScatter = o.type.equals("scatter") || o.type.equals("bubble");
        String xVar = "null", yVar = "null", overArg = "null";
        if (isScatter && data.getNumericVariables().size() >= 2) {
            yVar = DataEmbedder.jsStr(data.getNumericVariables().get(0).getName());
            xVar = DataEmbedder.jsStr(data.getNumericVariables().get(1).getName());
        }
        if (data.hasOver()) overArg = DataEmbedder.jsStr(data.getOverVariable().getName());
        String byVarJs = data.hasBy()
            ? DataEmbedder.jsStr(data.getByVariable().getName()) : "null";

        // _updatePanelChart(panelRows, ch, panelIdx): emitted once, called per panel.
        //
        // t2j fix9a (by()+filter sweep over every chart type, verify/harness/HarnessByFilter
        // + verify/byfilter_probe.js): the old body knew three layouts (scatter, box/violin
        // single var, "bar/line" = one value per dataset) and wrote the wrong shape into
        // every other one -- multi-var bars, multi-var over() bars/lines/areas, stacked and
        // 100%-stacked bars, pie/donut, cibar (threw inside Chart.js: numbers where
        // {y,yMin,yMax} objects are expected, so the whole filter change died), ciline,
        // histogram, boxplot over()/multi-var, scatter over() when a panel lacks a group.
        // Each branch below mirrors the dataset layout DatasetBuilder emits for that type
        // on a by()-panel subset (over() groups are GLOBALLY aligned in panels, v3.5.21):
        //   bar/line/area   1 var + over : 1 dataset, one value per over group
        //                   k vars       : 1 dataset, one value per var
        //                   k vars + over: 1 dataset per var, one value per over group
        //   stack100        over: 1 dataset per GROUP, one share per var (column = var)
        //                   no over: 1 dataset per var, one share
        //   scatter/bubble  1 dataset per over group PRESENT in the panel (no padding),
        //                   then fit datasets [ci up, ci lo,] fit per group (stride)
        //   pie/donut       1 dataset, one slice per var (over()+by() is rejected by the ado)
        //   cibar           1 dataset per var, {y,yMin,yMax,n} per over group
        //   ciline          3 datasets per var [mean, upper, lower], one value per group
        //   histogram       1 dataset, one value per bin; the panel's own edges are in
        //                   _binEdges_chart_by_N (ChartRenderer.histogram, fix9a)
        //   boxplot         over: 1 dataset per var, one box per over group
        //                   no over: 1 dataset, one box per var
        //   violin          the chart's own _vFilter_N (KDE recompute), first plot var
        boolean isBoxP    = o.type.equals("boxplot");
        boolean isViolinP = o.type.equals("violin");
        boolean isCibarP  = o.type.equals("cibar");
        boolean isCilineP = o.type.equals("ciline");
        boolean isHistP   = o.type.equals("histogram");
        boolean isPieP    = o.type.equals("pie") || o.type.equals("donut");
        boolean isStack100P = o.chart.stack100;
        double _wfP = 1.5;
        try { _wfP = Double.parseDouble(o.stats.whiskerfence.trim()); } catch (Exception ignore) {}
        sb.append("function _updatePanelChart(panelRows, ch, panelIdx) {\n");
        sb.append("  if (!ch) return;\n");
        sb.append("  var dsets = ch.data.datasets;\n");
        sb.append("  var _pm = window._smeta || {};\n");
        sb.append("  var _pVars = _pm.plotVars || [];\n");
        sb.append("  var _pOv = _pm.overVar || null;\n");
        sb.append("  var _pLbls = _pm.overLabels || [];\n");
        sb.append("  var _pStat = _pm.stat || 'mean';\n");
        sb.append("  var _pSiOv = (_pOv && window._si) ? window._si[_pOv] : null;\n");
        sb.append("  var _pNG = _pOv ? _pLbls.length : 1;\n");
        // rows of over group gi inside this panel (all panel rows when there is no over())
        sb.append("  function _pGrp(gi) { if (!_pSiOv) return panelRows; var o = []; for (var i = 0; i < panelRows.length; i++) { if (_pSiOv[panelRows[i]] === gi) o.push(panelRows[i]); } return o; }\n");
        sb.append("  function _pSt(rows, v) { var a = _sAgg.getValues(rows, v); return a.length ? _sAgg.computeStat(a, _pStat) : null; }\n");
        sb.append("  var _pK = ").append(_wfP).append(";\n");
        if (isViolinP) {
            // violin: the chart's own KDE recompute. t2j fix9d: EVERY entry (group x variable)
            // -- _vFilter_N takes a (gi, vi) -> values function and walks the panel's own
            // _vOrigData (gi = global over index, matches _pGrp; -1 without over()). Before,
            // one list per group of the first plot variable only (BF40).
            sb.append("  if (!_pVars.length) return;\n");
            sb.append("  var _pvf = window['_vFilter_' + panelIdx];\n");
            sb.append("  if (typeof _pvf === 'function') _pvf(function(_gi, _vi) { return _sAgg.getValues(_gi >= 0 ? _pGrp(_gi) : panelRows, _pVars[_vi] || _pVars[0]); }, _pK);\n");
            sb.append("  return;\n");
        } else if (isBoxP) {
            // boxplot: fresh stats objects into the plugin datasets (Stata-default percentiles,
            // whiskerfence k) -- same helper as the single page (t2j)
            sb.append("  function _pPct(sorted,p){var n=sorted.length;if(n===0)return null;if(n===1)return sorted[0];");
            sb.append("var i=n*p/100,fi=Math.floor(i);if(Math.abs(i-fi)<1e-9){var k=fi;if(k<1)return sorted[0];if(k>=n)return sorted[n-1];return (sorted[k-1]+sorted[k])/2;}");
            sb.append("var c=Math.ceil(i);if(c<1)c=1;if(c>n)c=n;return sorted[c-1];}\n");
            sb.append("  function _pStats(vals){if(!vals||vals.length===0)return null;var s=vals.slice().sort(function(a,b){return a-b;});");
            sb.append("var n=s.length,sum=0;for(var i=0;i<n;i++)sum+=s[i];var q1=_pPct(s,25),q3=_pPct(s,75),median=_pPct(s,50);");
            sb.append("var iqr=q3-q1,wLo=s[0],wHi=s[n-1];for(var j=0;j<n;j++){if(s[j]>=q1-_pK*iqr){wLo=s[j];break;}}");
            sb.append("for(var j2=n-1;j2>=0;j2--){if(s[j2]<=q3+_pK*iqr){wHi=s[j2];break;}}var outs=[];");
            sb.append("for(var k2=0;k2<n;k2++){if(s[k2]<q1-_pK*iqr||s[k2]>q3+_pK*iqr)outs.push(s[k2]);}");
            sb.append("return {min:wLo,q1:q1,median:median,mean:sum/n,q3:q3,max:wHi,outliers:outs};}\n");
            sb.append("  if (_pOv) {\n");
            sb.append("    for (var _vi = 0; _vi < _pVars.length && _vi < dsets.length; _vi++) { var _d = []; for (var _gi = 0; _gi < _pNG; _gi++) _d.push(_pStats(_sAgg.getValues(_pGrp(_gi), _pVars[_vi]))); dsets[_vi].data = _d; }\n");
            sb.append("  } else if (dsets.length) {\n");
            sb.append("    var _d1 = []; for (var _vj = 0; _vj < _pVars.length; _vj++) _d1.push(_pStats(_sAgg.getValues(panelRows, _pVars[_vj]))); dsets[0].data = _d1;\n");
            sb.append("  }\n");
            sb.append("  ch.update('none');\n");
            sb.append("  return;\n");
        } else if (isScatter) {
            boolean isBubbleP = o.type.equals("bubble") && data.getNumericVariables().size() >= 3;
            if (isBubbleP) {
                // F2 (t2j): keep bubble radius on the full-data scale in by() panels too.
                Variable rvarP = data.getNumericVariables().get(2);
                double rminP = Double.MAX_VALUE, rmaxP = -Double.MAX_VALUE;
                for (Object v : rvarP.getValues()) {
                    if (v instanceof Number) { double d = ((Number) v).doubleValue(); if (d < rminP) rminP = d; if (d > rmaxP) rmaxP = d; }
                }
                double rspanP = (rmaxP - rminP == 0) ? 1 : rmaxP - rminP;
                if (rminP == Double.MAX_VALUE) { rminP = 0; rspanP = 1; }
                sb.append("  var ptG = _sAgg.buildBubblePoints(panelRows,").append(xVar).append(",").append(yVar)
                  .append(",").append(DataEmbedder.jsStr(rvarP.getName())).append(",").append(rminP).append(",").append(rspanP).append(",").append(overArg).append(");\n");
            } else {
                sb.append("  var ptG = _sAgg.buildScatterPoints(panelRows,").append(xVar).append(",").append(yVar).append(",").append(overArg).append(");\n");
            }
            // fix9a: with over(), buildScatterPoints returns one entry per GLOBAL group but the
            // panel only has datasets for the groups PRESENT in it (DatasetBuilder builds the
            // subset without padding), so index di must map through the present-group list:
            // present = groups with at least one row in the UNFILTERED panel.
            if (data.hasOver()) {
                sb.append("  var _pAll = (window['_spkPanelAll_' + panelIdx]);\n");
                sb.append("  if (!_pAll) { var _all = _sAgg.filterRows({}, {}); _pAll = []; var _bSc = (window._sc && ").append(byVarJs).append(" && window._sc[").append(byVarJs).append("]) || []; var _bSi = (window._si && ").append(byVarJs).append(" && window._si[").append(byVarJs).append("]) || []; var _bg = (_pm.byGroups || [])[panelIdx]; for (var _ai = 0; _ai < _all.length; _ai++) { var _bi = _bSi[_all[_ai]]; if (_bi !== undefined && _bSc[_bi] === _bg) _pAll.push(_all[_ai]); } window['_spkPanelAll_' + panelIdx] = _pAll; }\n");
                sb.append("  var _present = []; for (var _pg = 0; _pg < _pNG; _pg++) { var _has = false; for (var _pr = 0; _pr < _pAll.length; _pr++) { if (_pSiOv[_pAll[_pr]] === _pg) { _has = true; break; } } if (_has) _present.push(_pg); }\n");
                sb.append("  var _nSc = _present.length;\n");
                sb.append("  for (var di = 0; di < _nSc && di < dsets.length; di++) dsets[di].data = (ptG[_present[di]] || {data: []}).data;\n");
            } else {
                sb.append("  var _nSc = 1;\n");
                sb.append("  if (dsets.length) dsets[0].data = (ptG[0] || {data: []}).data;\n");
            }
            // fit line (and CI band) rebuilt from the panel's filtered rows -- same dataset
            // layout as the single page: [scatter..., (ciUp, ciLo,) fit] per group
            if (!o.chart.fit.isEmpty()) {
                String fitType = DataEmbedder.jsStr(o.chart.fit);
                String lvl = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel;
                int stride = o.chart.fitci ? 3 : 1;
                int fitOff = o.chart.fitci ? 2 : 0;
                sb.append("  var _fStride = ").append(stride).append(";\n");
                if (data.hasOver()) {
                    sb.append("  for (var _fg = 0; _fg < _nSc; _fg++) {\n");
                    sb.append("    var _fRows = _pGrp(_present[_fg]);\n");
                    sb.append("    var _fBase = _nSc + _fg * _fStride;\n");
                    if (o.chart.fitci) {
                        sb.append("    var _fCi = _sAgg.buildCiBands(_fRows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(",").append(lvl).append(");\n");
                        sb.append("    if (_fCi) { if (dsets[_fBase]) dsets[_fBase].data = _fCi.upper; if (dsets[_fBase + 1]) dsets[_fBase + 1].data = _fCi.lower; }\n");
                    }
                    sb.append("    var _fPts = _sAgg.buildFitLine(_fRows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(");\n");
                    sb.append("    if (dsets[_fBase + ").append(fitOff).append("]) dsets[_fBase + ").append(fitOff).append("].data = _fPts;\n");
                    sb.append("  }\n");
                } else {
                    sb.append("  var _fPts = _sAgg.buildFitLine(panelRows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(");\n");
                    sb.append("  if (dsets[_nSc + ").append(fitOff).append("]) dsets[_nSc + ").append(fitOff).append("].data = _fPts;\n");
                    if (o.chart.fitci) {
                        sb.append("  var _fCi = _sAgg.buildCiBands(panelRows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(",").append(lvl).append(");\n");
                        sb.append("  if (_fCi) { if (dsets[_nSc]) dsets[_nSc].data = _fCi.upper; if (dsets[_nSc + 1]) dsets[_nSc + 1].data = _fCi.lower; }\n");
                    }
                }
            }
            sb.append("  ch.update('none');\n");
            sb.append("  return;\n");
        } else if (isPieP) {
            // pie/donut in by() panels = mode 1 only (one slice per var, SUM over rows; the ado
            // rejects over()+by() for pie). Percent unless stat(sum); raw values kept on the
            // dataset (_spkRaw) for the tooltip -- fix9a: _spkPieRaw was one global shared by
            // every panel, so panel 0's tooltip showed the last panel's raw values.
            boolean usePctP = !o.stats.stat.equals("sum");
            sb.append("  var _pRaw = []; for (var _pc = 0; _pc < _pVars.length; _pc++) { var _a = _sAgg.getValues(panelRows, _pVars[_pc]); var _s = 0; for (var _q = 0; _q < _a.length; _q++) _s += _a[_q]; _pRaw.push(_s); }\n");
            sb.append("  var _pTot = 0; for (var _pt = 0; _pt < _pRaw.length; _pt++) _pTot += Math.abs(_pRaw[_pt]); if (_pTot === 0) _pTot = 1;\n");
            sb.append("  if (dsets[0]) { dsets[0].data = _pRaw.map(function(v){ return ").append(usePctP ? "100*v/_pTot" : "v").append("; }); dsets[0]._spkRaw = _pRaw.slice(); }\n");
            sb.append("  ch.update('none');\n");
            sb.append("  return;\n");
        } else if (isCibarP || isCilineP) {
            // cibar/ciline: mean +- t * se per over group from the filtered rows (n < 2 -> null
            // placeholder keeps the x position), exact t critical from the engine
            sb.append("  var _cL = _pm.cilevel || 95;\n");
            sb.append("  function _tC(df, lvl) { return (_sAgg && _sAgg.tCrit) ? _sAgg.tCrit(df, lvl) : 1.96; }\n");
            // a by()-panel cibar/ciline holds only the groups PRESENT in the panel (cibar: n >= 2)
            // as its x labels, in global order -- fill by label slot, null where the filter
            // leaves n < 2 (the slot stays, as on the single page)
            sb.append("  var _pLab = ch.data.labels || [], _pPos = {}; for (var _li = 0; _li < _pLab.length; _li++) _pPos[String(_pLab[_li])] = _li;\n");
            if (isCibarP) {
                sb.append("  for (var _vi = 0; _vi < _pVars.length && _vi < dsets.length; _vi++) {\n");
                sb.append("    var _d = []; for (var _s0 = 0; _s0 < _pLab.length; _s0++) _d.push({y:null,yMin:null,yMax:null,n:0});\n");
                sb.append("    for (var _gi = 0; _gi < _pNG; _gi++) {\n");
                sb.append("      var _k = _pPos[String(_pLbls[_gi])]; if (_k === undefined) continue;\n");
                sb.append("      var _gs = _sAgg.buildGroupStats(_pGrp(_gi), _pVars[_vi]);\n");
                sb.append("      if (!_gs || _gs.n < 2) continue;\n");
                sb.append("      var _hw = _tC(_gs.n - 1, _cL) * _gs.sd / Math.sqrt(_gs.n);\n");
                sb.append("      _d[_k] = {y:_gs.mean, yMin:_gs.mean - _hw, yMax:_gs.mean + _hw, n:_gs.n};\n");
                sb.append("    }\n");
                sb.append("    dsets[_vi].data = _d;\n");
                sb.append("  }\n");
            } else {
                sb.append("  var _stride = (dsets.length && _pVars.length) ? Math.round(dsets.length / _pVars.length) : 3;\n");
                sb.append("  for (var _vi = 0; _vi < _pVars.length; _vi++) {\n");
                sb.append("    var _mn = [], _up = [], _lo = []; for (var _s1 = 0; _s1 < _pLab.length; _s1++) { _mn.push(null); _up.push(null); _lo.push(null); }\n");
                sb.append("    for (var _gi = 0; _gi < _pNG; _gi++) {\n");
                sb.append("      var _k = _pPos[String(_pLbls[_gi])]; if (_k === undefined) continue;\n");
                sb.append("      var _gs = _sAgg.buildGroupStats(_pGrp(_gi), _pVars[_vi]);\n");
                sb.append("      if (!_gs || _gs.n < 2) continue;\n");
                sb.append("      var _hw = _tC(_gs.n - 1, _cL) * _gs.sd / Math.sqrt(_gs.n);\n");
                sb.append("      _mn[_k] = _gs.mean; _up[_k] = _gs.mean + _hw; _lo[_k] = _gs.mean - _hw;\n");
                sb.append("    }\n");
                sb.append("    var _b = _vi * _stride;\n");
                sb.append("    if (dsets[_b]) dsets[_b].data = _mn; if (dsets[_b + 1]) dsets[_b + 1].data = _up; if (dsets[_b + 2]) dsets[_b + 2].data = _lo;\n");
                sb.append("  }\n");
            }
            sb.append("  ch.update('none');\n");
            sb.append("  return;\n");
        } else if (isHistP) {
            // histogram: recount the panel's filtered values into the PANEL's own bins
            // (_binEdges_chart_by_N, emitted by ChartRenderer.histogram); density / fraction
            // normalise by the non-missing n (S6); per-bin tooltip counts refreshed too
            sb.append("  var _hE = window['_binEdges_chart_by_' + panelIdx];\n");
            sb.append("  if (!_hE || _hE.length < 2 || !dsets[0]) return;\n");
            sb.append("  var _hN = _hE.length - 1, _hMin = _hE[0], _hBW = (_hE[_hN] - _hE[0]) / _hN || 1;\n");
            sb.append("  var _hV = _sAgg.getValues(panelRows, _pVars[0]);\n");
            sb.append("  var _hC = new Array(_hN).fill(0);\n");
            sb.append("  for (var _hi = 0; _hi < _hV.length; _hi++) { var _hb = Math.min(_hN - 1, Math.max(0, Math.floor((_hV[_hi] - _hMin) / _hBW))); _hC[_hb]++; }\n");
            sb.append("  var _hT = _hV.length || 1;\n");
            String ht = o.stats.histtype == null ? "density" : o.stats.histtype;
            if (ht.equals("frequency"))     sb.append("  dsets[0].data = _hC.slice();\n");
            else if (ht.equals("fraction")) sb.append("  dsets[0].data = _hC.map(function(c){ return c / _hT; });\n");
            else                            sb.append("  dsets[0].data = _hC.map(function(c){ return c / (_hT * _hBW); });\n");
            sb.append("  var _hTc = window['_ttCounts_chart_by_' + panelIdx]; if (_hTc && _hTc.length === _hN) { for (var _hk = 0; _hk < _hN; _hk++) _hTc[_hk] = _hC[_hk]; }\n");
            sb.append("  ch.update('none');\n");
            sb.append("  return;\n");
        } else if (isStack100P) {
            // 100%-stacked (F4 layouts): over() -> one dataset per GROUP holding one share per
            // var, column total per var; no over() -> one dataset per var, one share each
            if (data.hasOver()) {
                // the panel holds one dataset per group PRESENT in it (label = group label);
                // shares are taken over the groups the panel shows, as the initial render does
                sb.append("  var _dPos = {}; for (var _dj = 0; _dj < dsets.length; _dj++) _dPos[String(dsets[_dj].label)] = _dj;\n");
                sb.append("  var _nV = _pVars.length, _raw = [], _colT = []; for (var _v0 = 0; _v0 < _nV; _v0++) { _raw.push([]); _colT.push(0); }\n");
                sb.append("  for (var _g = 0; _g < _pNG; _g++) { var _k = _dPos[String(_pLbls[_g])]; for (var _v = 0; _v < _nV; _v++) { var _s = (_k === undefined) ? null : _pSt(_pGrp(_g), _pVars[_v]); _raw[_v].push(_s === null ? NaN : _s); if (_s !== null) _colT[_v] += Math.abs(_s); } }\n");
                sb.append("  for (var _g3 = 0; _g3 < _pNG; _g3++) { var _k3 = _dPos[String(_pLbls[_g3])]; if (_k3 === undefined) continue; var _d = []; for (var _v3 = 0; _v3 < _nV; _v3++) { var _r = _raw[_v3][_g3]; _d.push(isNaN(_r) ? null : (_colT[_v3] > 0 ? _r / _colT[_v3] * 100 : 0)); } dsets[_k3].data = _d; }\n");
            } else {
                // no over(): ChartRenderer draws stackedbar100 with numDatasets (plain
                // aggregates, one dataset, one bar per var -- nothing to stack across), so the
                // filter keeps that layout. (Making the no-over case a true one-column 100%
                // stack is a renderer change, logged as a follow-up in fix9a.)
                sb.append("  var _dB = []; for (var _vi = 0; _vi < _pVars.length; _vi++) _dB.push(_pSt(panelRows, _pVars[_vi]));\n");
                sb.append("  if (dsets.length === 1) dsets[0].data = _dB; else { for (var _vk = 0; _vk < _pVars.length && _vk < dsets.length; _vk++) dsets[_vk].data = [_dB[_vk]]; }\n");
            }
            sb.append("  ch.update('none');\n");
            sb.append("  return;\n");
        } else {
            // bar / hbar / line / area (stacked or not): the three aggregate layouts above
            sb.append("  if (_pOv && _pVars.length === 1) {\n");
            sb.append("    var _dA = []; for (var _gi = 0; _gi < _pNG; _gi++) _dA.push(_pSt(_pGrp(_gi), _pVars[0]));\n");
            sb.append("    if (dsets.length === 1) dsets[0].data = _dA; else { for (var _gk = 0; _gk < _pNG && _gk < dsets.length; _gk++) dsets[_gk].data = [_dA[_gk]]; }\n");
            sb.append("  } else if (!_pOv) {\n");
            sb.append("    var _dB = []; for (var _vi = 0; _vi < _pVars.length; _vi++) _dB.push(_pSt(panelRows, _pVars[_vi]));\n");
            sb.append("    if (dsets.length === 1) dsets[0].data = _dB; else { for (var _vk = 0; _vk < _pVars.length && _vk < dsets.length; _vk++) dsets[_vk].data = [_dB[_vk]]; }\n");
            sb.append("  } else {\n");
            sb.append("    for (var _vi = 0; _vi < _pVars.length && _vi < dsets.length; _vi++) { var _dC = []; for (var _gi = 0; _gi < _pNG; _gi++) _dC.push(_pSt(_pGrp(_gi), _pVars[_vi])); dsets[_vi].data = _dC; }\n");
            sb.append("  }\n");
            sb.append("  ch.update('none');\n");
        }
        sb.append("}\n");

        // _onFilterChange: reads state, filters rows, loops panels
        sb.append("function _onFilterChange() {\n");
        sb.append("  var catState = {};\n");
        for (Variable fv : data.getFilterVariables()) {
            String fname = fv.getName();
            sb.append("  (function(fn){\n");
            sb.append("    var sel = document.getElementById('fsel_'+fn);\n");
            sb.append("    var v = sel ? sel.value : (sel&&sel.options[0]?sel.options[0].value:'__ALL__');\n");
            sb.append("    catState[fn] = (v==='__ALL__') ? null : parseInt(v,10);\n");
            sb.append("  })(").append(DataEmbedder.jsStr(fname)).append(");\n");
        }
        sb.append("  var sldState = {};\n");
        if (data.hasSliders()) {
            sb.append("  var sm=(window._smeta&&window._smeta.sliders)?window._smeta.sliders:[];\n");
            sb.append("  for(var si=0;si<sm.length;si++){\n");
            sb.append("    var sn=sm[si].name;\n");
            sb.append("    var lo=document.getElementById('slo_'+sn);\n");
            sb.append("    var hi=document.getElementById('shi_'+sn);\n");
            sb.append("    if(lo&&hi)sldState[sn]={lo:parseFloat(lo.value),hi:parseFloat(hi.value)};\n");
            sb.append("  }\n");
        }

        // Filter all rows once
        sb.append("  var rows = _sAgg.filterRows(catState, sldState);\n");

        // F-3: per-panel update loop
        sb.append("  var m = window._smeta || {};\n");
        sb.append("  var byGroups = m.byGroups || [];\n");
        sb.append("  var bySc = (window._sc && ").append(byVarJs).append(" && window._sc[").append(byVarJs).append("]) || [];\n");
        sb.append("  var bySi = (window._si && ").append(byVarJs).append(" && window._si[").append(byVarJs).append("]) || [];\n");
        sb.append("  for (var gi=0; gi<byGroups.length; gi++) {\n");
        sb.append("    var byLabel = byGroups[gi];\n");
        sb.append("    var panelRows = rows.filter(function(r){\n");
        sb.append("      var idx = bySi[r];\n");
        sb.append("      return idx !== undefined && bySc[idx] === byLabel;\n");
        sb.append("    });\n");
        sb.append("    _updatePanelChart(panelRows, Chart.getChart('chart_by_' + gi), gi);\n");
        // t2j fix3: refresh the per-panel 'n = ...' header badge to the filtered count.
        // Without this the panel header kept its build-time n while the chart below it
        // (and the global obs count) updated, so the badge looked stuck.
        sb.append("    var _pCv = document.getElementById('chart_by_' + gi);\n");
        sb.append("    var _pWrap = (_pCv && _pCv.closest) ? _pCv.closest('.chart-wrapper') : null;\n");
        sb.append("    var _pnEl = _pWrap ? _pWrap.querySelector('.panel-n') : null;\n");
        sb.append("    if (_pnEl) _pnEl.textContent = 'n = ' + panelRows.length;\n");
        sb.append("  }\n");

        // Obs count
        sb.append("  var nc = document.getElementById('_nObs');\n");
        sb.append("  if (nc) nc.textContent = rows.length + ' obs';\n");

        // F-2: update stats N badges
        sb.append("  _sparkta_updateStatsBadges(rows);\n");
        sb.append("}\n");
        sb.append("var _applyFilter = _onFilterChange;\n");
        // IO2 (t2j): with noallfilter there is no "All" option, so the dropdown starts
        // on category 0 -- but the initial chart is server-rendered from the FULL data,
        // contradicting the selection. Fire the filter once after load so the chart
        // matches the selected category from the start.
        if (o.style.noAllFilter && data.hasFilter()) {
            sb.append("if(typeof _applyFilter==='function'){setTimeout(function(){try{_applyFilter();}catch(e){}},0);}\n");
        }
        sb.append(_buildStatsBadgeHelper(data));

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the ordered category list matching DataEmbedder.buildCategoryList(). */
    private List<String> buildCategoryList(Variable fv, boolean showMissing) {
        boolean hasMissing = false;
        for (Object v : fv.getValues()) if (v == null) { hasMissing = true; break; }

        List<String> cats = new ArrayList<>();
        if (hasMissing && showMissing) cats.add(DataSet.MISSING_SENTINEL);

        List<String> nonMissing = DataSet.uniqueValues(fv, o.chart.sortgroups);
        nonMissing.remove("");
        nonMissing.remove(DataSet.MISSING_SENTINEL);
        cats.addAll(nonMissing);
        return cats;
    }

    /** Returns whether this filter variable should show missing values. */
    private boolean getShowMissing(Variable fv, DataSet data) {
        List<Variable> fvars = data.getFilterVariables();
        int idx = fvars.indexOf(fv);
        if (idx == 0) return o.showmissingFilter;
        if (idx == 1) return o.showmissingFilter2;
        return false;
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
}
