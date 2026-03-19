package com.dashboard_test.html;

import com.dashboard_test.data.*;
import java.util.*;

/**
 * StatsRenderer -- builds the summary statistics panel HTML, JS, and sparklines.
 * v2.0.0: Extracted from HtmlGenerator.java as part of modular refactor.
 * Responsibility: everything inside the .stats-panel div, including:
 *   - Collapsible group blocks (Overall + per over()/by() group)
 *   - 7-column stats table (N, Mean, Median, Min, Max, StdDev, Distribution)
 *   - Inline SVG sparklines with IQR box, mean/median lines, outlier dots
 *   - Column toggle JS, sort JS, show/hide JS
 */
class StatsRenderer {

    private final DashboardOptions o;
    private final HtmlGenerator gen;  // access to shared utilities
    private final DatasetBuilder   dsb;  // stat math (stats(), percentile())

    StatsRenderer(DashboardOptions o, HtmlGenerator gen, DatasetBuilder dsb) {
        this.o   = o;
        this.gen = gen;
        this.dsb = dsb;
    }

    // Delegate stat math to DatasetBuilder (single source of truth)
    private double[] stats(Variable var)                     { return dsb.stats(var); }
    private double percentile(List<Double> sorted, double p) { return dsb.percentile(sorted, p); }

    // Delegate shared utilities to HtmlGenerator
    private String escHtml(String s)      { return gen.escHtml(s); }
    private String escJs(String s)        { return gen.escJs(s); }
    private String sdz(String s)          { return gen.sdz(s); }
    private String labelColor()           { return gen.labelColor(); }

    String buildStatsSection(DataSet data) {
        // nostats option suppresses the panel entirely
        if (o.chart.nostats) return "";
        List<Variable> nv = data.getNumericVariables();
        if (nv.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='stats-panel' id='statsPanel'>\n");
        // Header row with title icon and hide button
        sb.append("  <div class='shell-header'>\n");
        sb.append("    <div class='shell-title'>\n");
        sb.append("      <div class='shell-icon'><svg viewBox='0 0 12 12'>");
        sb.append("<rect x='1' y='4' width='2' height='7'/>");
        sb.append("<rect x='5' y='2' width='2' height='9'/>");
        sb.append("<rect x='9' y='5' width='2' height='6'/>");
        sb.append("</svg></div>\n");
        sb.append("      Summary Statistics\n");
        sb.append("    </div>\n");
        sb.append("    <button class='hide-btn' id='hideBtn' onclick='toggleStatsPanel(this)'>&#9650; Hide</button>\n");
        sb.append("  </div>\n");
        // Column toggle chips
        sb.append("  <div class='col-toggles' id='chipsRow'>\n");
        sb.append("    <span class='toggle-lbl'>Columns</span>\n");
        // cols: 0=N 1=Mean 2=Median 3=Min 4=Max 5=StdDev 6=Distribution
        String[] chipLabels = {"N","Mean","Median","Min","Max","Std Dev","Distribution"};
        for (int i = 0; i < chipLabels.length; i++) {
            sb.append("    <button class='chip on' onclick='toggleStatCol(").append(i)
              .append(",this)'><span class='chip-dot'></span>").append(chipLabels[i]).append("</button>\n");
        }
        sb.append("    <button class='chip-all' onclick='toggleAllStatCols(this)'>Hide all</button>\n");
        sb.append("  </div>\n");
        // Stats body
        sb.append("  <div class='stats-body' id='statsBody'>\n");
        // Overall block - compute N from non-missing numeric values
        int totalN = 0;
        if (!nv.isEmpty()) { double[] s0 = stats(nv.get(0)); totalN = (int) s0[0]; }
        sb.append(buildStatsBlock("Overall", nv, 0, totalN));
        // v3.5.32: single gIdx counter across BOTH over() and by() passes.
        // Previously each pass had its own local bi=1, causing g1/g2 to be
        // assigned to both over-groups and by-groups -- duplicate DOM IDs.
        // toggleStatsBlock(id) uses document.getElementById which returns the
        // FIRST match, so the by-group toggles silently opened the wrong block.
        // Fix: one counter starting at 1 (0 = Overall), incremented across both passes.
        int bi = 1;
        // over() groups
        if (data.hasOver()) {
            Variable gv = data.getOverVariable();
            // v1.9.1: pass showmissingOver so (Missing) group appears in stats
            List<String> ovLabels = DataSet.uniqueValues(gv, o.chart.sortgroups, o.showmissingOver);
            List<String> ovKeys   = DataSet.uniqueGroupKeys(gv, o.chart.sortgroups, o.showmissingOver);
            for (int gi = 0; gi < ovLabels.size(); gi++) {
                String displayLbl = ovLabels.get(gi);
                // v1.9.1: empty display label for null values -> "(Missing)"
                if (displayLbl == null || displayLbl.trim().isEmpty()) displayLbl = DataSet.MISSING_SENTINEL;
                String rawKey     = ovKeys.get(gi);
                // Pass key directly -- groupIdx handles both MISSING_SENTINEL and sdz
                List<Integer> idx = groupIdx(gv, DataSet.MISSING_SENTINEL.equals(rawKey) ? rawKey : sdz(rawKey));
                sb.append(buildStatsBlock(gv.getDisplayName()+" = "+displayLbl,
                           subsetNumericVars(nv, idx), bi++, idx.size()));
            }
        }
        // by() groups -- bi continues from over() pass so no ID collision
        if (data.hasBy()) {
            Variable gv = data.getByVariable();
            // v1.9.1: pass showmissingBy so (Missing) panel appears in stats
            List<String> byLabels = DataSet.uniqueValues(gv, o.chart.sortgroups, o.showmissingBy);
            List<String> byKeys   = DataSet.uniqueGroupKeys(gv, o.chart.sortgroups, o.showmissingBy);
            for (int gi = 0; gi < byLabels.size(); gi++) {
                String displayLbl = byLabels.get(gi);
                if (displayLbl == null || displayLbl.trim().isEmpty()) displayLbl = DataSet.MISSING_SENTINEL;
                String rawKey     = byKeys.get(gi);
                List<Integer> idx = groupIdx(gv, rawKey);
                sb.append(buildStatsBlock(gv.getDisplayName()+" = "+displayLbl,
                           subsetNumericVars(nv, idx), bi++, idx.size()));
            }
        }
        // Sparkline legend emitted once here (#5: was per-group in buildStatsTable).
        // data-col='6' hides it when Distribution chip is toggled off.
        sb.append("  <div data-col='6' class='spark-legend-global'>\n");
        sb.append("    <div class='leg-item'><svg width='14' height='2'><line x1='0' y1='1' x2='14' y2='1' stroke='#f59e0b' stroke-width='2'/></svg> Mean</div>\n");
        sb.append("    <div class='leg-item'><svg width='14' height='2'><line x1='0' y1='1' x2='14' y2='1' stroke='#10b981' stroke-width='2'/></svg> Median</div>\n");
        sb.append("    <div class='leg-item'><svg width='12' height='10' style='flex-shrink:0'><rect x='1' y='1' width='10' height='8' rx='1.5' fill='none' stroke='#3b82f6' stroke-width='1.5'/></svg> IQR (25th-75th)</div>\n");
        sb.append("    <div class='leg-item'><div class='leg-dot' style='background:#4e79a7;opacity:.6'></div> Observations</div>\n");
        sb.append("    <div class='leg-item'><div class='leg-dot' style='background:#e74c3c'></div> Outliers</div>\n");
        sb.append("  </div>\n");
        sb.append("  </div>\n</div>\n");
        return sb.toString();
    }

    /**
     * v1.5: Builds one collapsible group block with heading, N badge, and stats table.
     * @param heading  display label for this group
     * @param vars     numeric variables (possibly subset to this group)
     * @param idx      zero-based table index (for unique JS IDs)
     * @param groupN   row count for this group (shown in badge)
     */
    String buildStatsBlock(String heading, List<Variable> vars, int idx, int groupN) {
        String gid = "g" + idx;
        // N badge in group header
        String badge = "<span class='grp-badge' id='sbadge_" + idx + "'>N=" + groupN + "</span>";
        StringBuilder sb = new StringBuilder();
        sb.append("    <div class='grp'>\n");
        sb.append("      <div class='grp-hdr' onclick='toggleStatsBlock(\"").append(gid).append("\",this)'>\n");
        sb.append("        <div class='grp-title'>").append(escHtml(heading)).append(" ").append(badge).append("</div>\n");
        sb.append("        <span class='grp-chev' id='chev").append(idx).append("'>&#9650;</span>\n");
        sb.append("      </div>\n");
        sb.append("      <div id='").append(gid).append("'>\n");
        sb.append(buildStatsTable(vars, idx));
        sb.append("      </div>\n    </div>\n");
        return sb.toString();
    }

    /**
     * v1.5: Builds the sortable stats table with sparkline distribution column.
     * Columns: Variable | N | Mean | Median | Min | Max | Std Dev (w/ CV badge) | Distribution (sparkline)
     * @param vars    numeric variables for this group
     * @param tblIdx  table index for doSort(tblIdx, col) JS calls
     */
    String buildStatsTable(List<Variable> vars, int tblIdx) {
        StringBuilder sb = new StringBuilder();
        sb.append("        <div class='tbl-wrap'><table class='st' id='tbl").append(tblIdx).append("'>\n");
        // thead -- sortable on cols 1-6; Distribution col not sortable
        sb.append("          <thead><tr>\n");
        sb.append("            <th>Variable</th>\n");
        // sc = sortable column class; data-col drives toggle visibility (col index 0-6)
        String[] heads = {"N","Mean","Median","Min","Max","Std Dev"};
        for (int c = 0; c < heads.length; c++) {
            sb.append("            <th class='sc' data-col='").append(c)
              .append("' onclick='doSort(").append(tblIdx).append(",").append(c+1)
              .append(")'>").append(heads[c]).append("<span class='sort-arr'></span></th>\n");
        }
        // Distribution column (col index 6) - not sortable
        sb.append("            <th class='dist-hdr' data-col='6'>Distribution</th>\n");
        sb.append("          </tr></thead>\n          <tbody>\n");
        // tbody rows -- vi = variable index within this group for F-2A cell IDs
        int vi = 0;
        for (Variable v : vars) {
            double[] s = stats(v);
            // s: [0]=n [1]=mean [2]=min [3]=max [4]=sd [5]=median [6]=q1 [7]=q3 [8]=cv
            int    n      = (int) s[0];
            double mean   = s[1];
            double min    = s[2];
            double max    = s[3];
            double sd     = s[4];
            double median = s[5];
            double q1     = s[6];
            double q3     = s[7];
            double cv     = s[8];
            // CV badge class
            String cvClass = cv < 0.15 ? "cv-low" : (cv < 0.35 ? "cv-med" : "cv-high");
            int    cvPct   = (int) Math.round(cv * 100);
            String cvBadge = "<span class='cv " + cvClass + "' id='stc_" + tblIdx + "_" + vi + "_cvb'>CV " + cvPct + "%</span>";
            // Cell ID prefix: stc_{groupIdx}_{varIdx}_{col}
            String pfx = "stc_" + tblIdx + "_" + vi + "_";
            sb.append("          <tr data-var='").append(escHtml(v.getName())).append("'>\n");
            sb.append("            <td class='var-name'>").append(escHtml(v.getDisplayName())).append("</td>\n");
            // N with nb badge -- id on the nb span for F-2A update
            sb.append("            <td data-col='0'><span class='nb' id='").append(pfx).append("n'>").append(n).append("</span></td>\n");
            // Mean
            sb.append("            <td data-col='1' data-v='").append(mean).append("' id='").append(pfx).append("m'>").append(fmtStat(mean)).append("</td>\n");
            // Median
            sb.append("            <td data-col='2' data-v='").append(median).append("' id='").append(pfx).append("md'>").append(fmtStat(median)).append("</td>\n");
            // Min
            sb.append("            <td data-col='3' data-v='").append(min).append("' id='").append(pfx).append("mn'>").append(fmtStat(min)).append("</td>\n");
            // Max
            sb.append("            <td data-col='4' data-v='").append(max).append("' id='").append(pfx).append("mx'>").append(fmtStat(max)).append("</td>\n");
            // Std Dev with CV badge
            sb.append("            <td data-col='5' data-v='").append(sd).append("' id='").append(pfx).append("sd'>").append(fmtStat(sd)).append(" ").append(cvBadge).append("</td>\n");
            // Distribution sparkline
            sb.append("            <td class='spark-cell' data-col='6'>\n");
            sb.append(buildSparkline(v, min, max, mean, median, q1, q3, tblIdx, vi));
            sb.append("            </td>\n");
            sb.append("          </tr>\n");
            vi++;
        }
        sb.append("          </tbody>\n        </table></div>\n");
        // Footer: sort-hint only. Sparkline legend moved to buildStatsSection()
        // and emitted once after all groups (#5: was duplicated per group).
        sb.append("        <div data-col='6' class='stats-footer'>\n");
        sb.append("          <div class='sort-hint'>Click any column header to sort</div>\n");
        sb.append("        </div>\n");
        return sb.toString();
    }

    /**
     * v3.5.5: Build an inline SVG sparkline for one variable.
     * Shows: gray track, hollow IQR box (blue stroke), dots (obs), mean line (amber), median line (teal).
     * Outliers (beyond 1.5*IQR from Q1/Q3) rendered in red.
     *
     * LAZY DOT RENDERING (v3.5.5):
     * The static SVG skeleton (track, IQR box, mean/median lines) is written into
     * the HTML by Java as before. The observation dot cloud is NOT written as SVG
     * markup. Instead, dot x-coordinates are stored as a compact comma-separated
     * string in two data-* attributes on a <div class='spark-ph'> placeholder:
     *   data-in  = inlier pixel x-coords (blue dots), pipe-separated from
     *   data-out = outlier pixel x-coords (red dots)
     * An IntersectionObserver in buildStatsJs() fires when each group block
     * scrolls into view and injects the <circle> elements into the SVG at that
     * point. Off-screen groups contribute ~150 bytes to the HTML instead of
     * ~40KB, reducing a 500-group file from ~40MB to ~2-3MB.
     * Stats accuracy is completely unaffected -- stats are computed from full data.
     */
    // Overload called from buildStatsTable with group/var indices for F-2A IDs.
    String buildSparkline(Variable var, double min, double max,
                                   double mean, double median, double q1, double q3,
                                   int gi, int vi) {
        final String _gi = String.valueOf(gi), _vi = String.valueOf(vi);
        java.util.function.Function<String, String> spkId =
            tag -> " id='spk_" + _gi + "_" + _vi + "_" + tag + "'";
        return _buildSparkline(var, min, max, mean, median, q1, q3, spkId);
    }

    String buildSparkline(Variable var, double min, double max,
                                   double mean, double median, double q1, double q3) {
        java.util.function.Function<String, String> spkId = tag -> "";
        return _buildSparkline(var, min, max, mean, median, q1, q3, spkId);
    }

    private String _buildSparkline(Variable var, double min, double max,
                                   double mean, double median, double q1, double q3,
                                   java.util.function.Function<String, String> spkId) {
        // SVG dimensions
        int W = 110, H = 28;
        int trackY = 14, trackH = 4;
        int trackX = 2, trackW = W - 4; // usable x range: 2 to 108 (106px)
        // IQR fence for outlier detection
        double iqr    = q3 - q1;
        double fence  = iqr * 1.5;
        double loFence = q1 - fence;
        double hiFence = q3 + fence;
        // Scale helper: maps value in [min,max] to pixel x in [trackX, trackX+trackW]
        double range  = max - min;
        double scaleX = (range > 0) ? (double) trackW / range : 0;
        java.util.function.Function<Double, Integer> px = v -> {
            if (range <= 0) return trackX + trackW / 2;
            int x = (int) Math.round(trackX + (v - min) * scaleX);
            return Math.max(trackX, Math.min(trackX + trackW, x));
        };

        // --- Compute dot pixel positions (Java side) ---
        // Cap total dots at MAX_DOTS. Outliers always included; inliers sampled.
        // Stats (mean, SD, median, IQR) are always from the full dataset.
        final int MAX_DOTS = 500;
        List<Double> vals = new ArrayList<>();
        for (Object ov : var.getValues()) if (ov instanceof Number) vals.add(((Number)ov).doubleValue());
        List<Double> outlierVals = new ArrayList<>();
        List<Double> inlierVals  = new ArrayList<>();
        for (double val : vals) {
            if (val < loFence || val > hiFence) outlierVals.add(val);
            else                                inlierVals.add(val);
        }
        // Sample inliers if over budget
        List<Double> showInliers = inlierVals;
        int inlierBudget = Math.max(0, MAX_DOTS - outlierVals.size());
        if (inlierVals.size() > inlierBudget) {
            showInliers = new ArrayList<>();
            double step = (double) inlierVals.size() / inlierBudget;
            for (int k = 0; k < inlierBudget; k++) {
                showInliers.add(inlierVals.get((int)(k * step)));
            }
        }

        // Build compact pixel-x strings for lazy injection by IntersectionObserver.
        // Format: comma-separated integers, e.g. "12,34,47,51"
        // Stored as data-in (inliers) and data-out (outliers) on the placeholder div.
        StringBuilder inPts  = new StringBuilder();
        StringBuilder outPts = new StringBuilder();
        for (double val : showInliers) {
            if (inPts.length() > 0) inPts.append(',');
            inPts.append(px.apply(val));
        }
        for (double val : outlierVals) {
            if (outPts.length() > 0) outPts.append(',');
            outPts.append(px.apply(val));
        }

        // --- Mean / median line positions ---
        int meanX = px.apply(mean);
        int medX  = px.apply(median);
        if (Math.abs(meanX - medX) < 3) {
            meanX = Math.max(trackX, meanX - 2);
            medX  = Math.min(trackX + trackW, medX + 2);
        }

        // --- Build HTML ---
        String trackFill = gen.isDark() ? "#2a3a4a" : "#e2e8f0";
        int iqrX1   = px.apply(q1);
        int iqrX2   = px.apply(q3);
        int iqrBoxW = Math.max(2, iqrX2 - iqrX1);

        StringBuilder sb = new StringBuilder();
        sb.append("              <div class='spark-wrap'>\n");
        // Min/max labels
        sb.append("                <div class='spark-labels'>\n");
        sb.append("                  <span>").append(fmtStat(max)).append("</span>\n");
        sb.append("                  <span>").append(fmtStat(min)).append("</span>\n");
        sb.append("                </div>\n");
        // SVG skeleton: track, IQR box, mean/median lines (static, written by Java)
        sb.append("                <svg class='spark-svg' width='").append(W)
          .append("' height='").append(H).append("' style='display:block;flex-shrink:0'>\n");
        // Background track
        sb.append("                  <rect x='").append(trackX).append("' y='")
          .append(trackY - trackH/2).append("' width='").append(trackW)
          .append("' height='").append(trackH).append("' rx='2' fill='")
          .append(trackFill).append("'/>\n");
        // IQR box (hollow blue stroke) -- id for F-2A JS update
        sb.append("                  <rect").append(spkId.apply("iqr")).append(" x='").append(iqrX1).append("' y='")
          .append(trackY - 6).append("' width='").append(iqrBoxW)
          .append("' height='12' rx='2' fill='none' stroke='#3b82f6' stroke-width='1.5'/>\n");
        // Mean line (amber) and Median line (teal) -- ids for F-2A JS update
        sb.append("                  <line").append(spkId.apply("mean")).append(" x1='").append(meanX).append("' y1='3' x2='")
          .append(meanX).append("' y2='").append(H-3)
          .append("' stroke='#f59e0b' stroke-width='2' stroke-linecap='round'/>\n");
        sb.append("                  <line").append(spkId.apply("med")).append(" x1='").append(medX).append("' y1='3' x2='")
          .append(medX).append("' y2='").append(H-3)
          .append("' stroke='#10b981' stroke-width='2' stroke-linecap='round'/>\n");
        sb.append("                </svg>\n");
        // Lazy placeholder: dots injected by IntersectionObserver when block enters viewport.
        // data-in = inlier x-coords (blue), data-out = outlier x-coords (red).
        // data-cy = shared cy for all circles (trackY). class 'spark-ph' triggers observer.
        sb.append("                <div class='spark-ph'")
          .append(" data-in='").append(inPts).append("'")
          .append(" data-out='").append(outPts).append("'")
          .append(" data-cy='").append(trackY).append("'></div>\n");
        sb.append("              </div>\n");
        return sb.toString();
    }

    /**
     * v1.5: JavaScript for stats panel interactions.
     * - toggleStatsPanel: show/hide entire body and chips row
     * - toggleStatsBlock: collapse individual group blocks
     * - toggleStatCol: show/hide a column by data-col index across all tables
     * - toggleAllStatCols: hide/show all columns at once
     * - doSort: client-side sort of a table by column
     */
    String buildStatsJs() {
        return "\n// Stats panel JS (v1.5)\n"
            // Toggle entire panel (body + chips)
            + "function toggleStatsPanel(btn){"
            + "var body=document.getElementById('statsBody');"
            + "var chips=document.getElementById('chipsRow');"
            + "var hiding=body.style.display!=='none';"
            + "body.style.display=hiding?'none':'';"
            + "if(chips)chips.style.display=hiding?'none':'';"
            + "btn.innerHTML=hiding?'&#9660; Show':'&#9650; Hide';}\n"
            // Toggle individual group block
            + "function toggleStatsBlock(id,hdr){"
            + "var body=document.getElementById(id);"
            + "var chev=hdr.querySelector('.grp-chev');"
            + "var hiding=body.style.display!=='none';"
            + "body.style.display=hiding?'none':'';"
            + "if(chev)chev.innerHTML=hiding?'&#9660;':'&#9650;';}\n"
            // Column visibility toggle (chips)
            + "var _statColVis=[true,true,true,true,true,true,true];\n"
            + "function toggleStatCol(ci,btn){"
            + "_statColVis[ci]=!_statColVis[ci];"
            + "btn.classList.toggle('on',_statColVis[ci]);"
            + "document.querySelectorAll('[data-col=\"'+ci+'\"]').forEach(function(c){"
            + "c.style.display=_statColVis[ci]?'':'none';});}\n"
            // Hide/show all columns
            + "function toggleAllStatCols(btn){"
            + "var anyOn=_statColVis.some(function(v){return v;});"
            + "_statColVis=_statColVis.map(function(){return !anyOn;});"
            + "document.querySelectorAll('.chip').forEach(function(c,i){c.classList.toggle('on',!anyOn);});"
            + "for(var ci=0;ci<7;ci++){"
            + "document.querySelectorAll('[data-col=\"'+ci+'\"]').forEach(function(c){"
            + "c.style.display=(!anyOn)?'':'none';});}"
            + "btn.textContent=anyOn?'Show all':'Hide all';}\n"
            // Sort handler
            + "var _sortStates={};\n"
            + "function doSort(tblIdx,col){"
            + "var tbl=document.getElementById('tbl'+tblIdx);"
            + "if(!tbl)return;"
            + "var tbody=tbl.querySelector('tbody');"
            + "var key=tblIdx+'-'+col;"
            + "var asc=_sortStates[key]!==true;"
            + "_sortStates[key]=asc;"
            + "var rows=Array.from(tbody.querySelectorAll('tr'));"
            + "rows.sort(function(a,b){"
            + "var av=parseFloat(a.cells[col].getAttribute('data-v')||a.cells[col].textContent.replace(/[^0-9.-]/g,''));"
            + "var bv=parseFloat(b.cells[col].getAttribute('data-v')||b.cells[col].textContent.replace(/[^0-9.-]/g,''));"
            + "return asc?av-bv:bv-av;});"
            + "rows.forEach(function(r){tbody.appendChild(r);});"
            + "tbl.querySelectorAll('th').forEach(function(th,i){"
            + "th.classList.remove('sorted');"
            + "var arr=th.querySelector('.sort-arr');"
            + "if(arr)arr.textContent='';"
            + "if(i===col){th.classList.add('sorted');if(arr)arr.textContent=asc?' ^':' v';}});}\n"
            // v3.5.5: Lazy sparkline dot injection via IntersectionObserver.
            // Each .spark-ph placeholder carries data-in / data-out / data-cy.
            // Observer fires when placeholder enters viewport (200px lookahead),
            // injects SVG circles once, then unobserves.
            // Graceful fallback: dots simply absent in browsers without IO support
            // (track/IQR box/mean/median lines always visible as static SVG).
            + "(function(){\n"
            + "if(!window.IntersectionObserver)return;\n"
            + "var _sparkObs=new IntersectionObserver(function(entries){\n"
            + "entries.forEach(function(e){\n"
            + "if(!e.isIntersecting)return;\n"
            + "var ph=e.target;\n"
            + "_sparkObs.unobserve(ph);\n"
            + "var svg=ph.previousElementSibling;\n"
            + "if(!svg||svg.tagName.toLowerCase()!=='svg')return;\n"
            + "var cy=ph.getAttribute('data-cy')||'14';\n"
            + "var inPts=ph.getAttribute('data-in');\n"
            + "if(inPts&&inPts.length>0){\n"
            + "inPts.split(',').forEach(function(cx){\n"
            + "var c=document.createElementNS('http://www.w3.org/2000/svg','circle');\n"
            + "c.setAttribute('cx',cx);c.setAttribute('cy',cy);\n"
            + "c.setAttribute('r','2.2');c.setAttribute('fill','#4e79a7');\n"
            + "c.setAttribute('opacity','.55');svg.appendChild(c);});}\n"
            + "var outPts=ph.getAttribute('data-out');\n"
            + "if(outPts&&outPts.length>0){\n"
            + "outPts.split(',').forEach(function(cx){\n"
            + "var c=document.createElementNS('http://www.w3.org/2000/svg','circle');\n"
            + "c.setAttribute('cx',cx);c.setAttribute('cy',cy);\n"
            + "c.setAttribute('r','2.2');c.setAttribute('fill','#e74c3c');\n"
            + "c.setAttribute('opacity','.8');svg.appendChild(c);});}\n"
            + "});},{rootMargin:'200px'});\n"
            + "document.querySelectorAll('.spark-ph').forEach(function(ph){\n"
            + "_sparkObs.observe(ph);});\n"
            + "if(window.MutationObserver){\n"
            + "new MutationObserver(function(muts){\n"
            + "muts.forEach(function(m){\n"
            + "m.addedNodes.forEach(function(n){\n"
            + "if(n.nodeType!==1)return;\n"
            + "if(n.classList&&n.classList.contains('spark-ph'))_sparkObs.observe(n);\n"
            + "n.querySelectorAll&&n.querySelectorAll('.spark-ph').forEach(function(ph){\n"
            + "_sparkObs.observe(ph);});});});\n"
            + "}).observe(document.getElementById('statsPanel')||document.body,\n"
            + "{childList:true,subtree:true});}\n"
            + "})();\n";
    }

    // -- Stats helpers ---------------------------------------------------------

    /**
     * v3.5.8: replaced O(N) linear scan with O(1) lookup via Variable.groupIndex().
     * groupIndex() builds the Map<key, List<rowIdx>> once on first call and caches it.
     * Key normalisation is identical to the previous scan: sdz(String.valueOf(v)),
     * with null -> MISSING_SENTINEL and "" -> MISSING_SENTINEL (string missing fix).
     */
    List<Integer> groupIdx(Variable gv, String gc) {
        List<Integer> rows = gv.groupIndex().get(gc);
        return rows != null ? rows : new ArrayList<>();
    }

    List<Variable> subsetNumericVars(List<Variable> vars, List<Integer> idx) {
        List<Variable> res = new ArrayList<>();
        for (Variable v : vars) {
            Variable sv = new Variable();
            sv.setName(v.getName()); sv.setLabel(v.getLabel()); sv.setNumeric(true);
            for (int i : idx) sv.addValue(v.getValues().get(i));
            res.add(sv);
        }
        return res;
    }

    /**
     * v1.5: Compute summary statistics for a variable.
     * Returns double[9]: [0]=n [1]=mean [2]=min [3]=max [4]=sd
     *                    [5]=median [6]=q1 [7]=q3 [8]=cv (as fraction, e.g. 0.48)
     */

    /**
     * v1.5.5: Percentile using Stata's formula: h = (n+1) * p/100 (1-indexed).
     * Matches output of Stata's -summarize, detail- and -centile- commands.
     * If h is an exact integer, returns that order statistic.
     * If fractional, linearly interpolates between adjacent order statistics.
     * Clamps h to [1, n] to handle p=0 and p=100 edge cases.
     */

    // -- CSS -------------------------------------------------------------------

    String fmtStat(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e12) {
            return String.format("%,.0f", v);
        }
        return String.format("%,.2f", v);
    }

}
