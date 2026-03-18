package com.dashboard.html;

import com.dashboard.data.*;
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
            sb.append("    <option value='__ALL__'>All</option>\n");

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
            sb.append("    var v   = sel ? sel.value : '__ALL__';\n");
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
            // nScatterDs = number of scatter datasets (1 without over, nGroups with over)
            // Fit datasets always follow scatter datasets in the datasets array.
            // The engine needs to know where scatter ends and fit begins.
            sb.append("  var rows = _sAgg.filterRows(catState, sldState);\n");
            sb.append("  var ptGroups = _sAgg.buildScatterPoints(rows,").append(xVar).append(",").append(yVar).append(",").append(overArg).append(");\n");
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
                        sb.append("    var _fgCi = _sAgg.buildCiBands(_fgRows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(");\n");
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
                        sb.append("  var ciBands = _sAgg.buildCiBands(rows,").append(xVar).append(",").append(yVar).append(",").append(fitType).append(");\n");
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
        // F-2: update stats panel N badges to reflect filtered row counts.
        // querySelector('#g0 .grp-badge') finds the badge inside each group div.
        // g0=Overall, g1..gN=over groups, gN+1..=by groups (matches StatsRenderer bi order).
        sb.append("  _sparkta_updateStatsBadges(rows);\n");
        sb.append("}\n");

        sb.append("var _applyFilter = _onFilterChange;\n");
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
        if (o.chart.nostats) return "";
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

        sb.append("function _updateSpk(gi, vi, s, mn, mx) {\n");
        sb.append("  if (s === null) return;\n");
        sb.append("  var range = mx - mn || 1;\n");
        sb.append("  function px(v) { return Math.round(2 + (v - mn) / range * 106); }\n");
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
        sb.append("    if (sdEl && s.sd !== null) {\n");
        sb.append("      var cvPct = s.cv !== null ? Math.round(s.cv*100) : 0;\n");
        sb.append("      var cvBadge = document.getElementById(pfx+'cvb');\n");
        sb.append("      if (cvBadge) { cvBadge.textContent = 'CV '+cvPct+'%'; cvBadge.className = _cvClass(s.cv !== null ? s.cv : 0); }\n");
        sb.append("      sdEl.textContent = _fmtStat(s.sd) + ' '; sdEl.setAttribute('data-v', s.sd);\n");
        sb.append("      if (cvBadge) sdEl.appendChild(cvBadge);\n");
        sb.append("    }\n");
        sb.append("    var dmn = s.min !== null ? s.min : 0, dmx = s.max !== null ? s.max : 0;\n");
        sb.append("    _updateSpk(gi, vi, s, dmn, dmx);\n");
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

        // _updatePanelChart: emitted once, called per panel.
        // Handles scatter and bar/line update paths.
        sb.append("function _updatePanelChart(panelRows, ch) {\n");
        sb.append("  if (!ch) return;\n");
        sb.append("  var dsets = ch.data.datasets;\n");
        if (isScatter) {
            sb.append("  var ptG = _sAgg.buildScatterPoints(panelRows,").append(xVar).append(",").append(yVar).append(",").append(overArg).append(");\n");
            sb.append("  for (var di=0; di<ptG.length && di<dsets.length; di++) dsets[di].data = ptG[di].data;\n");
        } else {
            // Bar/line: use buildChartData() which handles all chart type variants
            // (colorByCategory single-dataset, multi-dataset over(), multi-var, etc.)
            // aggregate() alone cannot replicate all these layouts correctly.
            // buildChartData() already handles filter state internally via filterRows(),
            // so we cannot use it directly with pre-filtered panelRows.
            // Instead use a lightweight inline approach that matches the actual layout:
            //
            // The chart has ONE dataset (colorByCategory) or N datasets (over()).
            // aggregate(panelRows, plotVar, overVar) returns {labels, data} where:
            //   labels = x-axis category labels (over-group values or var names)
            //   data   = one value per category
            // For single-dataset (colorByCategory): update dsets[0].data = res.data
            // For multi-dataset over():             update dsets[gi].data = [res.data[gi]]
            // We detect which mode by checking if dsets.length === 1 vs data.length.
            sb.append("  var m = window._smeta || {};\n");
            sb.append("  var pVars = m.plotVars || [];\n");
            sb.append("  var ovVar = m.overVar || null;\n");
            sb.append("  for (var vi=0; vi<pVars.length; vi++) {\n");
            sb.append("    var res = _sAgg.aggregate(panelRows, pVars[vi], ovVar);\n");
            sb.append("    if (dsets.length === 1) {\n");
            // Single dataset (colorByCategory): replace whole data array
            sb.append("      dsets[0].data = res.data;\n");
            sb.append("    } else {\n");
            // Multi-dataset over(): one value per dataset
            sb.append("      for (var gi2=0; gi2<res.data.length && gi2<dsets.length; gi2++) {\n");
            sb.append("        dsets[gi2].data = [res.data[gi2]];\n");
            sb.append("      }\n");
            sb.append("    }\n");
            sb.append("  }\n");
        }
        sb.append("  ch.update('none');\n");
        sb.append("}\n");

        // _onFilterChange: reads state, filters rows, loops panels
        sb.append("function _onFilterChange() {\n");
        sb.append("  var catState = {};\n");
        for (Variable fv : data.getFilterVariables()) {
            String fname = fv.getName();
            sb.append("  (function(fn){\n");
            sb.append("    var sel = document.getElementById('fsel_'+fn);\n");
            sb.append("    var v = sel ? sel.value : '__ALL__';\n");
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
        sb.append("    _updatePanelChart(panelRows, Chart.getChart('chart_by_' + gi));\n");
        sb.append("  }\n");

        // Obs count
        sb.append("  var nc = document.getElementById('_nObs');\n");
        sb.append("  if (nc) nc.textContent = rows.length + ' obs';\n");

        // F-2: update stats N badges
        sb.append("  _sparkta_updateStatsBadges(rows);\n");
        sb.append("}\n");
        sb.append("var _applyFilter = _onFilterChange;\n");
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
