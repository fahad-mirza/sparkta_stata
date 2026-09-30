package com.dashboard_test.html;

import com.dashboard_test.data.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * HtmlGenerator -- top-level HTML document assembler.
 * v2.0.0: Refactored into modular renderer architecture.
 *
 * Responsibility: orchestrate the final HTML document by delegating to:
 *   - ChartRenderer    : chart type rendering + axis/legend/style helpers
 *   - DatasetBuilder   : dataset/label JS strings + statistical computations
 *   - FilterRenderer   : filter dropdown UI + _dashData slices + _applyFilter JS
 *   - StatsRenderer    : summary statistics panel HTML, JS, sparklines
 *
 * Also owns: shared utilities (escHtml, escJs, sdz, col, colS, toRgba),
 *            CSS generation (buildCss), and document structure (build).
 */
public class HtmlGenerator {

    // v3.5.36: single source of truth for the jar version stamped into HTML comments.
    // Update this constant with every version bump -- it is the ONLY place to change.
    public static final String VERSION = "3.6.0"; // v3.6.0 release 2026-09-13 (= build t2j fix9h); must equal the ado local sparkta_version (VersionCheck)

    private final DashboardOptions o;
    private String[] seriesColors;
    private String[] seriesSolid;

    // Renderer instances -- initialised in build() alongside DataSet
    private ChartRenderer    cr;
    private DatasetBuilder   dsb;
    private FilterRenderer   fr;
    private StatsRenderer    sr;

    private static final String[] DEFAULT_COLORS = {
        "rgba(78,121,167,0.85)",  "rgba(242,142,43,0.85)",
        "rgba(225,87,89,0.85)",   "rgba(118,183,178,0.85)",
        "rgba(89,161,79,0.85)",   "rgba(237,201,73,0.85)",
        "rgba(175,122,161,0.85)", "rgba(255,157,167,0.85)"
    };
    private static final String[] DEFAULT_SOLID = {
        "rgba(78,121,167,1)",  "rgba(242,142,43,1)",
        "rgba(225,87,89,1)",   "rgba(118,183,178,1)",
        "rgba(89,161,79,1)",   "rgba(237,201,73,1)",
        "rgba(175,122,161,1)", "rgba(255,157,167,1)"
    };

    // -- Named palette registry (Phase 2-D, v3.3.0) ----------------------------
    // Returns hex color array for a named palette, or null if not recognised.
    // Palettes piggybacked on theme() arg -- no new arg needed.
    // Sources:
    //   tab1     : Tableau 10 (Tableau Software, public)
    //   tab2     : ColorBrewer Set1 (Cynthia Brewer, colorbrewer2.org, Apache 2.0)
    //   tab3     : ColorBrewer Dark2 (Cynthia Brewer, colorbrewer2.org, Apache 2.0)
    //   cblind1  : Okabe-Ito (2008) colorblind-safe palette (Nature Methods)
    //   neon     : bright saturated palette (schemepack v1.4, MIT)
    //   swift_red: Taylor Swift Red album palette (schemepack v1.4, MIT)
    //   viridis  : perceptually-uniform palette (van der Walt & Smith, CC0)
    private static String[] namedScheme(String theme) {
        if (theme == null) return null;
        switch (theme.toLowerCase(Locale.ROOT).trim()) {
            case "tab1":
                // Tableau 10 -- 10 colors
                return new String[]{
                    "#4e79a7","#f28e2b","#e15759","#76b7b2","#59a14f",
                    "#edc948","#b07aa1","#ff9da7","#9c755f","#bab0ac"
                };
            case "tab2":
                // ColorBrewer Set1 -- 8 colors, bold qualitative
                return new String[]{
                    "#e41a1c","#377eb8","#4daf4a","#984ea3",
                    "#ff7f00","#a65628","#f781bf","#999999"
                };
            case "tab3":
                // ColorBrewer Dark2 -- 8 colors, darker qualitative
                return new String[]{
                    "#1b9e77","#d95f02","#7570b3","#e7298a",
                    "#66a61e","#e6ab02","#a6761d","#666666"
                };
            case "cblind1":
                // Okabe-Ito (2008) colorblind-safe -- 9 colors
                // Excludes pure black (last) to keep good contrast on white bg
                return new String[]{
                    "#e69f00","#56b4e9","#009e73","#f0e442",
                    "#0072b2","#d55e00","#cc79a7","#999999","#000000"
                };
            case "neon":
                // Bright saturated neons -- 12 colors, best on dark background
                return new String[]{
                    "#ff6fff","#05f4b7","#f7e62b","#01cdfe",
                    "#ff6b6b","#7fff00","#bf5fff","#ff9500",
                    "#00ffff","#ff007f","#adff2f","#ff4500"
                };
            case "swift_red":
                // Taylor Swift Red album palette -- 8 colors (reds, earth, charcoal)
                return new String[]{
                    "#9b1b30","#d4213d","#e8735a","#f4a460",
                    "#c8a882","#8b4513","#4a4a4a","#2c2c2c"
                };
            case "viridis":
                // Perceptually uniform -- 8 representative stops (van der Walt & Smith, CC0)
                return new String[]{
                    "#440154","#472d7b","#3b528b","#2c728e",
                    "#21918c","#28ae80","#5ec962","#fde725"
                };
            default:
                return null;
        }
    }

    public HtmlGenerator(DashboardOptions opts) {
        this.o = opts;
        initColors();
    }

    // -- Color helpers ---------------------------------------------------------


    // -- Theme helpers (v3.3.0+) -----------------------------------------------
    // isDark(): true for "dark" alone OR "dark_<palette>" compound themes.
    boolean isDark() {
        String t = (o.theme == null) ? "" : o.theme.toLowerCase(Locale.ROOT).trim();
        return t.equals("dark") || t.startsWith("dark_");
    }

    // paletteKey(): extracts the palette name from a theme string.
    //   "tab1"        -> "tab1"
    //   "dark_viridis"-> "viridis"
    //   "light_tab2"  -> "tab2"
    //   "dark"        -> ""  (background-only, no palette)
    //   "default"     -> ""
    private String paletteKey() {
        String t = (o.theme == null) ? "" : o.theme.toLowerCase(Locale.ROOT).trim();
        if (t.startsWith("dark_"))  return t.substring(5);
        if (t.startsWith("light_")) return t.substring(6);
        // bare palette name (not a background keyword)
        if (!t.equals("dark") && !t.equals("light") && !t.equals("default") && !t.isEmpty())
            return t;
        return "";
    }

    private void initColors() {
        // Phase 2-D (v3.3.0): check for a named palette via theme() first.
        // Supports bare palette names ("tab1") and compound themes ("dark_tab1").
        // dark/light alone are background-only -- no palette.
        // colors() always overrides the palette.
        String[] namedBase   = null;
        String[] namedSolid_ = null;
        String key = paletteKey();
        if (!key.isEmpty()) {
            String[] hex = namedScheme(key);
            // v3.6.0-s8b: sequential palettes start at their darkest stop, which is
            // invisible on a dark background (VIZ_STANDARD COL-BG). Reverse them so the
            // brightest stops are used first when the theme is dark.
            if (hex != null && isDark() && key.equals("viridis")) {
                String[] rev = new String[hex.length];
                for (int i = 0; i < hex.length; i++) rev[i] = hex[hex.length - 1 - i];
                hex = rev;
            }
            if (hex != null) {
                namedBase   = new String[hex.length];
                namedSolid_ = new String[hex.length];
                for (int i = 0; i < hex.length; i++) {
                    namedBase[i]   = toRgba(hex[i], 0.85);
                    namedSolid_[i] = toRgba(hex[i], 1.0);
                }
            }
        }
        String[] baseColors = (namedBase   != null) ? namedBase   : DEFAULT_COLORS;
        String[] baseSolid  = (namedSolid_ != null) ? namedSolid_ : DEFAULT_SOLID;

        // If user also supplied explicit colors(), those override the palette.
        if (o.chart.colors == null || o.chart.colors.isEmpty()) {
            seriesColors = baseColors; seriesSolid = baseSolid; return;
        }
        String[] parts = o.chart.colors.trim().split("\\s+");
        int len = Math.max(parts.length, baseColors.length);
        seriesColors = new String[len]; seriesSolid = new String[len];
        for (int i = 0; i < len; i++) {
            if (i < parts.length && !parts[i].isEmpty()) {
                // t2e: an explicit alpha (rgba(...) or #rrggbbaa from colour%NN in the ado) is the
                // user's fill opacity -- keep it; solid marks still use alpha 1.
                boolean userAlpha = parts[i].startsWith("rgba(") || (parts[i].startsWith("#") && parts[i].length() == 9);
                String base = parts[i].startsWith("#") && parts[i].length() == 9 ? parts[i].substring(0, 7) : parts[i];
                seriesColors[i] = userAlpha ? (parts[i].startsWith("#") ? toRgba(base, Integer.parseInt(parts[i].substring(7), 16) / 255.0) : parts[i]) : toRgba(base, 0.85);
                // t2e fix 3: an explicit alpha is the user's intent for strokes and markers too (Stata lcolor(navy%50))
                seriesSolid[i]  = userAlpha ? seriesColors[i] : toRgba(base, 1.0);
            } else {
                seriesColors[i] = baseColors[i % baseColors.length];
                seriesSolid[i]  = baseSolid[i % baseSolid.length];
            }
        }
    }

    // toRgba delegates to applyOpacity (handles rgba/rgb/hex inputs correctly)
    String toRgba(String c, double a) {
        // Use applyOpacity which correctly handles rgba(), rgb(), and # inputs,
        // including replacing the existing alpha when input is already rgba().
        // The old early-return "if startsWith rgba return c" was wrong -- it
        // ignored the requested alpha entirely when the color already had alpha.
        return applyOpacity(c.trim(), a);
    }

    int[] hexToRgb(String hex) {
        try {
            hex = hex.replace("#","");
            if (hex.length()==3) hex=""+hex.charAt(0)+hex.charAt(0)+hex.charAt(1)+hex.charAt(1)+hex.charAt(2)+hex.charAt(2);
            return new int[]{Integer.parseInt(hex.substring(0,2),16),Integer.parseInt(hex.substring(2,4),16),Integer.parseInt(hex.substring(4,6),16)};
        } catch(Exception e){return null;}
    }

    // Apply opacity override to a color string
    String applyOpacity(String color, double opacity) {
        if (opacity < 0 || opacity > 1) return color;
        int[] rgb = null;
        if (color.startsWith("#")) rgb = hexToRgb(color);
        else if (color.startsWith("rgba(")) {
            // Replace existing alpha
            String inner = color.substring(5, color.length()-1);
            String[] parts = inner.split(",");
            if (parts.length >= 3)
                return "rgba("+parts[0].trim()+","+parts[1].trim()+","+parts[2].trim()+","+opacity+")";
        } else if (color.startsWith("rgb(")) {
            String inner = color.substring(4, color.length()-1);
            return "rgba(" + inner + "," + opacity + ")";
        }
        if (rgb != null) return "rgba("+rgb[0]+","+rgb[1]+","+rgb[2]+","+opacity+")";
        return color;
    }

    String col(int i) {
        String c = seriesColors[i % seriesColors.length];
        if (!o.chart.opacity.isEmpty()) {
            try { c = applyOpacity(seriesSolid[i % seriesSolid.length], Double.parseDouble(o.chart.opacity)); }
            catch(Exception ignore){}
        }
        return c;
    }
    String colS(int i) { return seriesSolid[i % seriesSolid.length]; }

    /**
     * t2j fix8f (issue #10): darken a color for CI error bars / whiskers so the
     * interval reads more strongly than the bar/marker it sits on. Multiplies the
     * RGB channels by {@code factor} (0..1; smaller = darker). Accepts #rrggbb,
     * rgb()/rgba() (alpha preserved), or named/other strings (returned unchanged).
     */
    String darken(String color, double factor) {
        if (color == null || color.isEmpty()) return color;
        int r, g, b; String aTail = "";
        try {
            if (color.charAt(0) == '#' && color.length() == 7) {
                r = Integer.parseInt(color.substring(1, 3), 16);
                g = Integer.parseInt(color.substring(3, 5), 16);
                b = Integer.parseInt(color.substring(5, 7), 16);
            } else if (color.startsWith("rgb")) {
                String inner = color.substring(color.indexOf('(') + 1, color.indexOf(')'));
                String[] p = inner.split(",");
                r = (int) Double.parseDouble(p[0].trim());
                g = (int) Double.parseDouble(p[1].trim());
                b = (int) Double.parseDouble(p[2].trim());
                if (p.length > 3) aTail = "," + p[3].trim();
            } else {
                return color; // named or unknown -- leave as-is
            }
        } catch (Exception e) { return color; }
        r = (int) Math.round(Math.max(0, Math.min(255, r * factor)));
        g = (int) Math.round(Math.max(0, Math.min(255, g * factor)));
        b = (int) Math.round(Math.max(0, Math.min(255, b * factor)));
        return "rgba(" + r + "," + g + "," + b + (aTail.isEmpty() ? ",1" : aTail) + ")";
    }
    /** Darkened CI stroke for the i-th palette color (issue #10, factor 0.62). */
    String colCi(int i) { return darken(colS(i), 0.62); }

    /** v3.6.0-s8i: one-line context under the title. Never empty for a real chart. */
    /** s9o: print / PDF handlers on EVERY page (table-only PDF must work without download). */
    static String printJs() {
        return ""
            + "function _spkPrintPdf(){window.print();}\n"
            // fix9n (Fahad: "can the PDF, PNG and SVG show which filter is applied at the time of
            // exporting?"): one line, "Filters: <label> = <value>; <slider> = lo - hi", built from
            // the live filter bar (dropdowns not on All; sliders moved off their full range).
            // Empty when nothing is filtered. Drawn under the context line by every export route
            // (SVG / PNG headers, and a <p> inserted for the print swap).
            + "function _spkActiveFilters(){var out=[];try{\n"
            + "  Array.prototype.slice.call(document.querySelectorAll('#filterBar select[id^=fsel_]')).forEach(function(sel){if(sel.value==='__ALL__')return;var lab=sel.previousElementSibling;var ln=lab&&lab.tagName==='LABEL'?(lab.textContent||'').trim():sel.id.slice(5);var op=sel.options[sel.selectedIndex];out.push(ln+' = '+(op?(op.textContent||'').trim():sel.value));});\n"
            + "  Array.prototype.slice.call(document.querySelectorAll('#filterBar .sld-group')).forEach(function(g){var lo=g.querySelector('.sld-lo'),hi=g.querySelector('.sld-hi');if(!lo||!hi)return;var moved=(parseFloat(lo.value)>parseFloat(lo.min))||(parseFloat(hi.value)<parseFloat(hi.max));if(!moved)return;\n"
            + "    var lab=g.querySelector('.sld-label');var vl=g.querySelector('[id^=sval_lo_]'),vh=g.querySelector('[id^=sval_hi_]');out.push((lab?(lab.textContent||'').trim():g.id.slice(5))+' = '+(vl?vl.textContent:lo.value)+' - '+(vh?vh.textContent:hi.value));});\n"
            + "  if(out.length){var nc=document.getElementById('_nObs');var nt=nc?(nc.textContent||'').trim():'';if(nt)out.push('N = '+nt.replace(/\\s*obs$/,''));}\n"
            + "}catch(e){}return out.length?'Filters: '+out.join('; '):'';}\n"
            // s9p: headless drivers (sparkta saveas()) open the page with a query string:
            //   ?print=table|chart  -> body class so --print-to-pdf gets the scoped layout
            //   ?export=svg         -> run the page's own SVG exporter and hand the result back
            //                          through the DOM (<textarea id=spkSvgOut>) for --dump-dom
            + "(function(){try{var q=(location.search||'').replace(/^\\?/,'').split('&').reduce(function(m,kv){var p=kv.split('=');if(p[0])m[p[0]]=decodeURIComponent(p[1]||'');return m;},{});\n"
            + "  if(q.print==='table'||q.print==='chart')document.body.classList.add('spk-print-'+q.print+'-only');\n"
            // fix9u (decision 7): any headless ?print= run (page PNG included) drops the
            // "Made with sparkta" footer -- @media print already hid it; the on-screen PNG did not
            + "  if(q.print)document.body.classList.add('spk-print');\n"
            + "  if(q.print){var sw=function(){if(typeof _spkChartToSvg!=='function'){document.title='SPARKTA_PRINT_READY';return;}_spkSwapForPrint();document.title='SPARKTA_PRINT_READY';};\n"
            + "    if(document.readyState==='complete')setTimeout(sw,100);else window.addEventListener('load',function(){setTimeout(sw,100);});}\n"
            + "  if(q.export==='svg'){var tries=0;var go=function(){try{if(typeof _spkPageToSvg!=='function'){if(++tries<50){setTimeout(go,100);return;}throw new Error('exporter not on page (needs download or saveas)');}\n"
            + "      var svg;try{svg=_spkPageToSvg();}catch(e1){if(String(e1).indexOf('not ready')>=0&&++tries<50){setTimeout(go,100);return;}throw e1;}\n"
            + "      var ta=document.createElement('textarea');ta.id='spkSvgOut';ta.textContent=svg;document.body.appendChild(ta);document.title='SPARKTA_SVG_READY';}catch(e){var ta2=document.createElement('textarea');ta2.id='spkSvgErr';ta2.textContent=String(e);document.body.appendChild(ta2);}};\n"
            + "    if(document.readyState==='complete')setTimeout(go,50);else window.addEventListener('load',function(){setTimeout(go,50);});}\n"
            + "}catch(e){}})();\n"
            + "function _spkPrintOnly(what){document.body.classList.add('spk-print-'+what+'-only');\n"
            + "  var done=function(){document.body.classList.remove('spk-print-'+what+'-only');window.removeEventListener('afterprint',done);};\n"
            + "  window.addEventListener('afterprint',done);window.print();}\n"
            + "function _spkSwapForPrint(){\n"
            + "  try{var fl=_spkActiveFilters();if(fl&&!document.querySelector('.spk-print-filters')){var anchor=document.querySelector('.subtitle2')||document.querySelector('h1');if(anchor){var fp=document.createElement('p');fp.className='subtitle2 spk-print-filters';fp.textContent=fl;anchor.parentNode.insertBefore(fp,anchor.nextSibling);}}}catch(e){}\n"
            + "  Array.prototype.slice.call(document.querySelectorAll('canvas')).forEach(function(cv){\n"
            + "    if(cv.getAttribute('data-print-swapped'))return;\n"
            + "    if(typeof _spkChartToSvg!=='function')return;var inst=(window.Chart&&Chart.getChart)?Chart.getChart(cv):null;if(!inst)return;\n"
            // fix9k (Fahad: the PDF button printed an EMPTY chart box): the swap used an <img> with
            // a data: SVG source, which the browser decodes asynchronously -- Chrome lays out the
            // print pages as soon as the beforeprint handlers return, when the image still had a
            // natural size of 0 x 0, so the chart printed as a blank card. An INLINE <svg> is part
            // of the DOM at once (no load step); a viewBox + CSS width lets it scale to the page.
            + "    try{var svg=_spkChartToSvg(inst,[],true);var box=document.createElement('div');box.className='spk-print-svg';box.style.width='100%';\n"
            + "      box.innerHTML=svg;var el=box.querySelector('svg');if(el){var w=parseFloat(el.getAttribute('width'))||cv.offsetWidth,h=parseFloat(el.getAttribute('height'))||cv.offsetHeight;\n"
            + "        if(!el.getAttribute('viewBox'))el.setAttribute('viewBox','0 0 '+w+' '+h);el.setAttribute('preserveAspectRatio','xMidYMid meet');el.removeAttribute('width');el.removeAttribute('height');\n"
            + "        el.style.width='100%';el.style.height='auto';el.style.display='block';el.setAttribute('role','img');el.setAttribute('aria-label',cv.getAttribute('aria-label')||'chart');}\n"
            + "      cv.style.display='none';cv.setAttribute('data-print-swapped','1');cv.parentNode.insertBefore(box,cv);\n"
            + "    }catch(e){console.error('print: svg swap failed',e);}\n"
            + "  });\n"
            + "}\n"
            + "window.addEventListener('beforeprint',_spkSwapForPrint);\n"
            + "window.addEventListener('afterprint',function(){\n"
            + "  Array.prototype.slice.call(document.querySelectorAll('.spk-print-svg,.spk-print-filters')).forEach(function(b){b.parentNode.removeChild(b);});\n"
            + "  Array.prototype.slice.call(document.querySelectorAll('canvas[data-print-swapped]')).forEach(function(cv){cv.style.display='';cv.removeAttribute('data-print-swapped');});\n"
            + "});\n";
    }

    /** context line */
    String contextLine(DataSet data) {
        StringBuilder c = new StringBuilder();
        String ci = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel;
        if (o.type.equals("marginsplot")) {
            c.append("margins");
            if (!o.chart.mpXlab.isEmpty() && !o.chart.mpXlab.replace("|","").replace("~","").trim().isEmpty()) {
                String[] xl = o.chart.mpXlab.split("~",-1)[0].split("\\|",-1);
                c.append(" &bull; ").append(xl.length).append(xl.length == 1 ? " condition" : " conditions");
            }
            int nSer = 0;
            for (String sv : o.chart.mpSeries.split("\\|",-1)) if (!sv.trim().isEmpty() && !sv.trim().equals("0")) nSer++;
            if (nSer > 1) c.append(" &bull; ").append(nSer).append(" series");
            c.append(" &bull; ").append(ci).append("% CI");
            if (!o.chart.peLevels2Val.isEmpty()) c.append(" (inner ").append(o.chart.peLevels2Val).append("%)");
            return c.toString();
        }
        if (o.isPostEstimation()) {
            if (!o.chart.peCmd.isEmpty()) c.append(escHtml(o.chart.peCmd));
            if (!o.chart.peDepvar.isEmpty()) c.append(c.length() > 0 ? " on " : "").append(escHtml(o.chart.peDepvar));
            String[] nobs = o.chart.peNobs.split("~",-1);
            if (nobs.length > 1) c.append(" &bull; ").append(nobs.length).append(" models");
            else if (!o.chart.peNobs.trim().isEmpty()) {
                String nb = o.chart.peNobs.trim();
                try { nb = String.format(Locale.ROOT, "%,d", (long) Double.parseDouble(nb)); } catch (NumberFormatException ignored) {}
                c.append(" &bull; N = ").append(escHtml(nb));
            }
            c.append(" &bull; ").append(ci).append("% CI (").append(o.chart.peTstat.equals("1") ? "t" : "z").append(")");
            return c.toString();
        }
        // data charts
        String stat = o.stats.stat.isEmpty() ? "mean" : o.stats.stat;
        java.util.List<com.dashboard_test.data.Variable> nv = data.getNumericVariables();
        boolean pieLike = o.type.equals("pie") || o.type.equals("doughnut") || o.type.equals("donut");
        if (nv.isEmpty()) c.append(pieLike ? "Share of observations" : "Frequency");
        else if (pieLike) {
            c.append("Share of ");
            for (int i = 0; i < nv.size(); i++) c.append(i > 0 ? ", " : "").append(escHtml(nv.get(i).getDisplayName()));
        } else if ((o.type.equals("scatter") || o.type.equals("bubble")) && nv.size() >= 2) {
            // t2j fix8r (decision 2): stat() is not applied on scatter/bubble (raw points), so
            // "Mean of price, weight" was wrong. Stata convention (ChartRenderer.scatter): first
            // variable = y, second = x, third = bubble size.
            c.append(escHtml(nv.get(0).getDisplayName())).append(" vs ").append(escHtml(nv.get(1).getDisplayName()));
            if (o.type.equals("bubble") && nv.size() >= 3) c.append(", sized by ").append(escHtml(nv.get(2).getDisplayName()));
        } else if (o.type.equals("histogram") || o.type.equals("boxplot") || o.type.equals("violin")) {
            // t2j fix8r (decision 2): distribution charts plot the raw values, not a stat().
            // (hbox/hviolin are normalised to boxplot/violin + horizontal in DashboardBuilder.)
            c.append("Distribution of ");
            for (int i = 0; i < nv.size(); i++) c.append(i > 0 ? ", " : "").append(escHtml(nv.get(i).getDisplayName()));
        } else {
            c.append(Character.toUpperCase(stat.charAt(0))).append(stat.substring(1)).append(" of ");
            for (int i = 0; i < nv.size(); i++) c.append(i > 0 ? ", " : "").append(escHtml(nv.get(i).getDisplayName()));
        }
        if (data.hasOver()) c.append(" by ").append(escHtml(data.getOverVariable().getDisplayName()));
        if (data.getByVariable() != null) c.append(", panels by ").append(escHtml(data.getByVariable().getDisplayName()));
        // t2j fix8p (C9a): subtitle N = non-missing count of the primary plotted variable,
        // matching the summary-stats table. Previously used data.getObservationCount() (total
        // rows), so a variable with missing values showed N = 74 in the subtitle but N = 69 in
        // the table (user-reported: histogram of rep78, 5 missing). Missing numeric values are
        // stored as null. Falls back to the total row count when no numeric variable is plotted
        // (frequency / share charts).
        int _subN = data.getObservationCount();
        if (!nv.isEmpty()) {
            int _nn = 0;
            for (Object _v : nv.get(0).getValues()) if (_v != null) _nn++;
            _subN = _nn;
        }
        c.append(" &bull; N = ").append(String.format(Locale.ROOT, "%,d", _subN));
        if (o.type.contains("ci")) c.append(" &bull; ").append(ci).append("% CI");
        return c.toString();
    }

    // -- Entry point -----------------------------------------------------------

    public String build(DataSet data) {
        o._keyItems.clear();   // s9j
        // v2.0.0: Initialise renderers -- each receives this HtmlGenerator for shared utilities
        this.dsb = new DatasetBuilder(o, this);
        this.cr  = new ChartRenderer(o, this, dsb);
        this.fr  = new FilterRenderer(o, this, dsb, cr);
        this.sr  = new StatsRenderer(o, this, dsb);

        boolean dark   = isDark();
        String  bgCol  = resolve(o.chart.bgcolor,   dark ? "#1a1a2e" : "#f8f9fa");
        String  plotCl = resolve(o.chart.plotcolor, dark ? "#16213e" : "#ffffff");

        // s9j: scripts first -- renderers register key items while they emit; the HTML key reads them
        String scriptHtml = data.hasBy()
            ? buildByScripts(data)
            : cr.buildChartScript("mainChart", data, data.hasAnyFilter());
        String chartsHtml = data.hasBy()
            ? buildByPanels(data)
            : (o.isPostEstimation() ? "<div class='coefplot-wrap'>" : "")
              + "<div class='chart-wrapper'>"
              + (o.chart.download ? buildDownloadButtons("mainChart") : "")
              // t2j fix8v: box/violin single pages get the HTML shared key ABOVE the chart (the
              // by() grid already did); the on-canvas keys drawn inside the plot area are gone.
              + (isBoxOrViolinType() ? buildSharedKey(data) : "")
              + "<div class='spk-chart-box'><canvas id='mainChart' role='img' aria-label='" + escHtml(o.title) + "'></canvas></div>" + buildElementsKey() + "</div>"
              + (o.isPostEstimation() ? "</div>" : "")
              + "\n";
        // Filter UI and pre-computed data slices (empty strings when no filters used)
        String filterUi     = fr.buildFilterUi(data);
        // v2.6.2: when by()+filter() are both active, use per-panel filter data/script
        // so each panel updates independently from its own _dashData_N object.
        // For by()-only or filter()-only, existing single-path methods are used unchanged.
        String filterData;
        String filterScript;
        if (data.hasBy() && data.hasAnyFilter()) {
            List<String> byGroupKeys = DataSet.uniqueGroupKeys(
                data.getByVariable(), o.chart.sortgroups);
            filterData   = fr.buildFilterDataByPanels(data, byGroupKeys);
            filterScript = fr.buildFilterScriptByPanels(data, byGroupKeys);
        } else {
            filterData   = fr.buildFilterData(data);
            filterScript = fr.buildFilterScript(data);
        }
        // For histogram with filter: _histPreamble holds the _ttRanges_ JS var
        // declaration emitted by histogram(). It must appear before scriptFinal
        // so the tooltip callback can reference it. (v1.8.2)
        String histPre      = o._histPreamble;
        // I5/I6 (v3.6.0-s8y): shared formatter + tooltip context (over label, static
        // per-group counts, total N). The engine overwrites _spkTipN/_spkTipTotal live
        // under filters; these statics serve non-filter pages and the initial view.
        String overLbl = data.hasOver() ? escJs(data.getOverVariable().getDisplayName()) : "";
        histPre = ChartRenderer.fmtJs() + printJs()
                + "var _spkOverLabel='" + overLbl + "';"
                + "var _spkTipN0=" + (data.getByVariable() != null ? "[]" : dsb.tipNJson()) + ";"   // by(): one static array cannot serve several panels
                + "var _spkTipTotal0=" + data.getObservationCount() + ";\n"
                + histPre;
        String scriptFinal  = scriptHtml;  // _mainChart prefix already applied above

        boolean needsDL = o.chart.datalabels || o.chart.pielabels || !o.chart.mlabelVar.isEmpty();
        // cibar uses chartjs-chart-error-bars plugin for whisker rendering
        boolean needsEB = o.type.equals("cibar");
        // boxplot uses chartjs-chart-boxplot plugin; custom violin does NOT (v2.5.0)
        boolean needsBP = o.type.equals("boxplot");
        // v3.5.0: annotation plugin needed when any annotation option was supplied
        boolean needsAN = hasAnnotations();
        // canvas2svg always loaded when download=true regardless of chart type (v2.7.0)
        // v2.0.2: buildScriptTags() handles online (CDN) and offline (inline) modes
        String scriptTags = buildScriptTags(needsDL, needsEB, needsBP, needsAN);
        // s9l: canvas2svg has no CDN -- inline it from the jar whenever the export toolbar is on
        // (online or offline), so the SVG button always works from a self-contained page
        if (o.chart.download) scriptTags += "  <script>\n" + loadResource("canvas2svg-1.0.19.js") + "\n  </script>\n";
        // F-0/F-1: when filters or sliders present, inline sparkta_engine.js before chart code
        if (data.hasAnyFilter()) {
            String engJs = loadEngineJs();
            scriptTags += "  <script>\n" + engJs + "\n  </script>\n";
        }
        // Only globally register for bar/line datalabels; pie uses per-chart registration
        String pluginReg = (o.chart.datalabels || !o.chart.mlabelVar.isEmpty())
            ? "if(typeof ChartDataLabels !== 'undefined'){Chart.register(ChartDataLabels);}\n"
              + "else{console.error('chartjs-plugin-datalabels failed to load');}\n"
            : "";
        // Phase 1-C noticks: Chart.js 4 has no built-in config to hide tick marks
        // while keeping labels. Solution: globally-registered beforeDraw plugin
        // that sets scale.options.ticks.tickLength=0 on every scale before draw.
        // This overrides the internal tick mark length Chart.js reads at draw time.
        // Registered once via Chart.register() -- applies to all charts on the page. (v3.0.3)
        if (o.axes.noticks) {
            pluginReg += "var _noTicksPlugin={\n"
                + "  id:'noTicks',\n"
                + "  beforeDraw:function(chart){\n"
                + "    Object.values(chart.scales).forEach(function(s){\n"
                + "      if(s.options.grid) s.options.grid.tickLength=0;\n"
                + "    });\n"
                + "  }\n"
                + "};\n"
                + "Chart.register(_noTicksPlugin);\n";
        }
        // v2.4.1: chartjs-chart-boxplot UMD bundle does NOT auto-register with Chart.js.
        // Must explicitly call Chart.register() with all controllers and elements
        // exported by the plugin before the new Chart(...) call executes.
        // UMD global name is "ChartBoxPlot" (from package.json "global" field).
        // Register BoxPlotController + BoxAndWiskers (boxplot) and
        // ViolinController + Violin (violin) so both types work from one call.
        if (needsBP) {
            pluginReg += "if(typeof ChartBoxPlot !== 'undefined'){\n"
                + "  Chart.register(\n"
                + "    ChartBoxPlot.BoxPlotController,\n"
                + "    ChartBoxPlot.ViolinController,\n"
                + "    ChartBoxPlot.BoxAndWiskers,\n"
                + "    ChartBoxPlot.Violin\n"
                + "  );\n"
                + "} else { console.error('chartjs-chart-boxplot failed to load'); }\n";
        }
        // t2j fix8p (B-RESIZE, user-reported): at a fractional browser zoom (Chrome Ctrl+- to
        // 90%) every chart entered an "infinite collapse" -- the canvas shrank on every frame.
        // Mechanism: with responsive+maintainAspectRatio:true the canvas sits IN the layout
        // flow and its own height (width/aspect) feeds back into its container; at a
        // fractional device-pixel-ratio the rounding never converges, so each resize shrinks
        // it again. fix7's reserved scrollbar only covered the scrollbar-toggle variant.
        // FIX: a global plugin that, in afterInit (synchronous, before first paint), gives the
        // chart box a CSS aspect-ratio equal to the chart's aspect, takes the canvas OUT of
        // flow (absolute, filling the box) and flips maintainAspectRatio to false, so Chart.js
        // only ever fills a CSS-sized box whose size can never depend on the canvas -> no
        // feedback path exists. Rendered size is unchanged (box aspect == chart aspect).
        pluginReg += "var _spkAspectLock={\n"
            + "  id:'spkAspectLock',\n"
            + "  afterInit:function(chart){\n"
            + "    var cv=chart.canvas, box=cv&&cv.parentElement;\n"
            + "    if(!box||!box.classList||!box.classList.contains('spk-chart-box'))return;\n"
            + "    var ar=chart.options.aspectRatio; if(!(ar>0))ar=2;\n"
            + "    box.style.aspectRatio=String(ar);\n"
            + "    cv.style.position='absolute';cv.style.top='0';cv.style.left='0';\n"
            + "    cv.style.width='100%';cv.style.height='100%';\n"
            + "    chart.options.maintainAspectRatio=false;\n"
            + "    try{chart.resize();}catch(e){}\n"
            + "  }\n"
            + "};\n"
            + "Chart.register(_spkAspectLock);\n";
        // t2j fix8s (user-reported: chart text/markers blur next to the DOM table, and stay
        // blurred after Ctrl-+ to 250%). Chart.js draws on a bitmap sized css px x
        // devicePixelRatio; at 100% on a 1x screen that is 1 px per css px with no font
        // hinting, and on a browser zoom Chart.js re-reads the ratio only inside resize() --
        // the layout resize fires first (ratio still 1) and the separate DPR-change listener
        // did not reliably fire in Chrome, so the 1x bitmap was stretched 2.5x. FIX: (a) every
        // on-page chart renders at max(devicePixelRatio, 2) via options.devicePixelRatio, so
        // it is supersampled on 1x / fractional-zoom screens; (b) sparkta owns the zoom
        // detection: a matchMedia(resolution) listener re-armed after each change, plus a
        // window resize fallback, re-applies the ratio and calls chart.resize() directly
        // (bypassing resizeDelay). Only canvases inside .spk-chart-box are touched: the
        // saveas/download export clones (o2.devicePixelRatio=1, fake canvas) are left alone.
        // t2j fix8t (Tier A, user on a 150% display): the fix8s rule max(dpr,2) let Chrome
        // DOWNSAMPLE 2 -> 1.5 (non-integer), which smeared every hairline. New rule: render at
        // the NATIVE ratio when dpr >= 1.5 (nothing is resampled, so device-pixel snapping in
        // spkSnap below lands exactly), and at 2 below that (100% / 90% / 125%: an integer or
        // near-integer downscale, text supersampled).
        // t2j fix8u: native whenever the ratio has a small CSS-pixel grid q (see _spkQ / spkAlign
        // below: 125%, 150%, 175%, 200%, 250%, 90%, 80%) and is not exactly 1; 2 at 100% and
        // for ratios with no q <= 10 (spkSnap then snaps in units of ratio / dpr).
        // t2j fix8v: + 1e-6 on the native ratio. Chart.js sizes the bitmap as floor(css *
        // ratio); Chrome reports 110% on a 150% display as 1.649999976, so 1120 * ratio =
        // 1847.99997 floored to 1847 while the display rect is 1848 device px (resampled
        // again). The nudge makes floor land on the integer; the drawing transform is off by
        // 1e-6 (about 1/1000 px across a chart), invisible.
        pluginReg += "function _spkCrispRatio(){var d=window.devicePixelRatio||1;return (d!==1&&_spkQ(d))?d+1e-6:2;}\n"
            + "function _spkCrispOnPage(chart){var cv=chart&&chart.canvas,box=cv&&cv.parentElement;"
            + "return !!(box&&box.classList&&box.classList.contains('spk-chart-box'));}\n"
            + "var _spkCrisp={\n"
            + "  id:'spkCrisp',\n"
            + "  beforeInit:function(chart){\n"
            + "    if(!_spkCrispOnPage(chart))return;\n"
            + "    chart.options.devicePixelRatio=_spkCrispRatio();\n"
            + "  }\n"
            + "};\n"
            + "Chart.register(_spkCrisp);\n"
            + "var _spkCrispLast=window.devicePixelRatio||1;\n"
            + "function _spkCrispApply(){\n"
            + "  var r=_spkCrispRatio();_spkCrispLast=window.devicePixelRatio||1;\n"
            + "  var all=Chart.instances||{};\n"
            + "  Object.keys(all).forEach(function(k){var c=all[k];if(!_spkCrispOnPage(c))return;\n"
            + "    if(c.options.devicePixelRatio!==r||c.currentDevicePixelRatio!==r){c.options.devicePixelRatio=r;try{c.resize();}catch(e){}}\n"
            + "  });\n"
            + "}\n"
            + "function _spkCrispArm(){\n"
            + "  if(!window.matchMedia)return;\n"
            + "  var mq=window.matchMedia('(resolution: '+(window.devicePixelRatio||1)+'dppx)');\n"
            + "  var h=function(){try{mq.removeEventListener?mq.removeEventListener('change',h):mq.removeListener(h);}catch(e){}\n"
            + "    _spkCrispApply();_spkCrispArm();};\n"
            + "  try{mq.addEventListener?mq.addEventListener('change',h):mq.addListener(h);}catch(e){}\n"
            + "}\n"
            + "try{_spkCrispArm();\n"
            + "if(window.addEventListener)window.addEventListener('resize',function(){\n"
            + "  if((window.devicePixelRatio||1)!==_spkCrispLast)setTimeout(_spkCrispApply,0);\n"
            + "});}catch(e){}\n";
        // t2j fix8t (Tier A): spkSnap -- device-pixel snapping for EVERY axis-aligned stroke
        // and rect on an on-page chart, whoever draws it (Chart.js grid/borders/bars, the
        // boxplot/violin plugin, every sparkta painter: CI bars, whiskers, reflines, keys).
        // Mechanism: the chart's own 2D context gets own-property wrappers that BUFFER the
        // current path (moveTo/lineTo/rect/closePath) and replay it at stroke/fill/clip time
        // with (a) lineWidth rounded to a whole number of device pixels and (b) each
        // horizontal/vertical segment moved onto the device pixel grid (pixel centres for an
        // odd width, pixel edges for an even width or a fill). Diagonal segments, curves
        // (arc/bezier/ellipse), Path2D arguments and text are passed through untouched, and
        // nothing is snapped under a rotation/skew transform. Only canvases inside
        // .spk-chart-box are patched; export clones (canvas2svg / PNG) are never touched.
        // t2j fix8u: spkSnap now snaps to the SCREEN pixel grid, not the bitmap grid. Below
        // 150% the bitmap is 2x the screen (render ratio 2 on a 1x display), so a whole
        // bitmap pixel is half a screen pixel; snapping in units of (ratio / devicePixelRatio)
        // bitmap px (2 at 100%) lands every hairline on exactly one screen pixel after the
        // browser's integer downscale. Unit is 1 whenever the ratio is native (150%+).
        pluginReg += "function _spkSnapUnit(chart){var d=window.devicePixelRatio||1,r=(chart&&chart.currentDevicePixelRatio)||d;"
            + "var s=r/d;return (s>1&&Math.abs(s-Math.round(s))<1e-9)?Math.round(s):1;}\n"
            + "function _spkSnapInstall(ctx,chart){\n"
            + "  if(!ctx||ctx._spkSnapped||typeof ctx.getTransform!=='function')return;\n"
            + "  ctx._spkSnapped=true;\n"
            + "  var P=Object.getPrototypeOf(ctx);var buf=[];var curved=false;\n"
            + "  function raw(n){return P[n];}\n"
            + "  function grid(){var t=ctx.getTransform();if(!t||Math.abs(t.b)>1e-9||Math.abs(t.c)>1e-9||Math.abs(t.a-t.d)>1e-9||!(t.a>0))return null;\n"
            + "    var s=_spkSnapUnit(chart);return (s===1)?t:{a:t.a/s,e:t.e/s,f:t.f/s};}\n"
            + "  function devW(cssW,t){var d=cssW*t.a;var w=(cssW<=1)?Math.floor(d+0.25):Math.round(d);if(w<1)w=1;return w;}\n"
            + "  function snapC(v,t,e,off){var d=v*t.a+e;return (Math.round(d-off)+off-e)/t.a;}\n"
            + "  function replay(mode){\n"
            + "    var t=(curved||mode==='path')?null:grid();var pts=buf;buf=[];\n"
            + "    if(!t||!pts.length){pts.forEach(function(q){raw(q[0]).apply(ctx,q.slice(1));});return;}\n"
            + "    var off=0;\n"
            + "    if(mode==='stroke'){var w=devW(ctx.lineWidth,t);ctx.lineWidth=w/t.a;off=(w%2===1)?0.5:0;}\n"
            + "    var n=pts.length;var sx=[],sy=[];\n"
            + "    for(var i=0;i<n;i++){sx.push(false);sy.push(false);}\n"
            + "    for(var i=1;i<n;i++){var a=pts[i-1],b=pts[i];if(b[0]!=='lineTo'||(a[0]!=='lineTo'&&a[0]!=='moveTo'))continue;\n"
            + "      if(Math.abs(a[2]-b[2])<1e-6){sy[i-1]=sy[i]=true;if(sx[i-1]!==true)sx[i-1]='end';if(sx[i]!==true)sx[i]='end';}\n"
            + "      else if(Math.abs(a[1]-b[1])<1e-6){sx[i-1]=sx[i]=true;if(sy[i-1]!==true)sy[i-1]='end';if(sy[i]!==true)sy[i]='end';}}\n"
            + "    for(var i=0;i<n;i++){var q=pts[i];if(q[0]==='closePath'){raw('closePath').call(ctx);continue;}\n"
            + "      var x=q[1],y=q[2];\n"
            + "      if(sx[i]===true)x=snapC(x,t,t.e,off);else if(sx[i]==='end'&&mode==='fill')x=snapC(x,t,t.e,0);\n"
            + "      if(sy[i]===true)y=snapC(y,t,t.f,off);else if(sy[i]==='end'&&mode==='fill')y=snapC(y,t,t.f,0);\n"
            + "      raw(q[0]).call(ctx,x,y);}\n"
            + "  }\n"
            + "  function isObj(a){return a.length&&typeof a[0]==='object'&&a[0]!==null;}\n"
            + "  ctx.beginPath=function(){buf=[];curved=false;return raw('beginPath').call(ctx);};\n"
            + "  ctx.moveTo=function(x,y){buf.push(['moveTo',x,y]);};\n"
            + "  ctx.lineTo=function(x,y){buf.push(['lineTo',x,y]);};\n"
            + "  ctx.closePath=function(){buf.push(['closePath']);};\n"
            + "  ctx.rect=function(x,y,w,h){buf.push(['moveTo',x,y],['lineTo',x+w,y],['lineTo',x+w,y+h],['lineTo',x,y+h],['lineTo',x,y],['closePath']);};\n"
            + "  ['arc','arcTo','ellipse','bezierCurveTo','quadraticCurveTo','roundRect'].forEach(function(n){if(typeof P[n]!=='function')return;\n"
            + "    ctx[n]=function(){curved=true;replay('path');return raw(n).apply(ctx,arguments);};});\n"
            + "  ctx.stroke=function(){if(isObj(arguments)){replay('path');return raw('stroke').apply(ctx,arguments);}var lw=ctx.lineWidth;replay('stroke');var r=raw('stroke').call(ctx);ctx.lineWidth=lw;return r;};\n"
            + "  ctx.fill=function(){if(isObj(arguments)){replay('path');return raw('fill').apply(ctx,arguments);}replay('fill');return raw('fill').apply(ctx,arguments);};\n"
            + "  ctx.clip=function(){if(isObj(arguments)){replay('path');return raw('clip').apply(ctx,arguments);}replay('fill');return raw('clip').apply(ctx,arguments);};\n"
            + "  ctx.isPointInPath=function(){replay('path');return raw('isPointInPath').apply(ctx,arguments);};\n"
            + "  ctx.isPointInStroke=function(){replay('path');return raw('isPointInStroke').apply(ctx,arguments);};\n"
            // fillRect/strokeRect must NOT disturb the path in progress (canvas spec): snap
            // the rectangle directly and call the raw primitives.
            + "  ctx.fillRect=function(x,y,w,h){var t=grid();if(!t)return raw('fillRect').call(ctx,x,y,w,h);\n"
            + "    var x0=snapC(x,t,t.e,0),y0=snapC(y,t,t.f,0),x1=snapC(x+w,t,t.e,0),y1=snapC(y+h,t,t.f,0);return raw('fillRect').call(ctx,x0,y0,x1-x0,y1-y0);};\n"
            + "  ctx.strokeRect=function(x,y,w,h){var t=grid();if(!t)return raw('strokeRect').call(ctx,x,y,w,h);\n"
            + "    var lw=ctx.lineWidth,dw=devW(lw,t),off=(dw%2===1)?0.5:0;ctx.lineWidth=dw/t.a;\n"
            + "    var x0=snapC(x,t,t.e,off),y0=snapC(y,t,t.f,off),x1=snapC(x+w,t,t.e,off),y1=snapC(y+h,t,t.f,off);var r=raw('strokeRect').call(ctx,x0,y0,x1-x0,y1-y0);ctx.lineWidth=lw;return r;};\n"
            + "}\n"
            + "var _spkSnap={id:'spkSnap',afterInit:function(chart){if(_spkCrispOnPage(chart))_spkSnapInstall(chart.ctx,chart);}};\n"
            + "Chart.register(_spkSnap);\n";
        // t2j fix8u (user-reported, MEASURED: fix8t pages still blurred on a 150% display --
        // violin box, grid lines, scatter markers). ROOT CAUSE is outside the bitmap. Chrome
        // snaps a replaced element's box to whole CSS pixels FIRST and only then multiplies
        // by the device pixel ratio (isolated test at ratio 1.5: a canvas at CSS top 135 with
        // an 861-px bitmap lands at device row 202.5 -> every 1-px line is a 50/50 pair; at
        // CSS top 134 it is single-row sharp). So at 1.5 a canvas is only ever blitted 1:1
        // when its CSS top/left AND its CSS width/height are all EVEN whole CSS pixels
        // (n * 1.5 integer), and the bitmap equals css * 1.5 exactly. Chart.js sizes the
        // canvas to floor(box) css px (575 on a 1150-wide box -> 862.5 device rows, bitmap
        // floor = 862) at whatever fractional offset the layout gives (box top 115.58 css):
        // the 862-row bitmap is resampled into a rect of a different size at a half-pixel
        // offset, so spkSnap's on-grid strokes still smear (measured 225/248 grey pairs in
        // the lower half). FIX (spkAlign, part of the crisp stack): q = smallest 1..10 with
        // q * ratio integer (1.5 -> 2, 1.25 -> 4, 1.75 -> 4, 2 -> 1). Before EVERY Chart.js
        // resize of an on-page chart (instance resize() wrapped, plus afterInit and a <body>
        // ResizeObserver for reflows that move the box): shift the canvas inside its box so
        // its rounded CSS top/left are multiples of q (document coordinates, at most q-1 css
        // px), and cap it with max-width/max-height to the largest multiples of q that fit
        // the box -- Chart.js honours those caps in getMaximumSize, so chart.width/height,
        // the forced css style and the bitmap (css * ratio) all agree and the browser blits
        // 1:1. Hit-testing is untouched (display size == chart.width/height). Render ratio
        // rule is now: native whenever a q exists and the ratio is not exactly 1 (125% and
        // 175% become exact too); 2 at 100% (snap unit 2 = exact 2:1 downscale) and for
        // ratios with no small q. Export clones untouched (not in .spk-chart-box).
        // t2j fix8v: q tolerance 1e-3 (Chrome reports 110% on a 150% display as
        // 1.649999976, not 1.65) and cap 20 so that zoom step is aligned too (q=20).
        pluginReg += "function _spkQ(d){for(var q=1;q<=20;q++){if(Math.abs(q*d-Math.round(q*d))<1e-3)return q;}return 0;}\n"
            + "function _spkAlign(chart,noResize){\n"
            + "  if(!_spkCrispOnPage(chart))return;\n"
            + "  var cv=chart.canvas,box=cv.parentElement,d=window.devicePixelRatio||1,q=_spkQ(d),st=cv.style;\n"
            + "  var rb=box.getBoundingClientRect();if(!(rb.width>0&&rb.height>0))return;\n"
            + "  if(!q){if(st.left!=='0px'&&st.left!=='0')st.left='0';if(st.top!=='0px'&&st.top!=='0')st.top='0';if(st.maxWidth)st.maxWidth='';if(st.maxHeight)st.maxHeight='';return;}\n"
            + "  var L=rb.left+(window.pageXOffset||0),T=rb.top+(window.pageYOffset||0);\n"
            + "  var Lc=Math.ceil((L-0.49)/q)*q,Tc=Math.ceil((T-0.49)/q)*q;\n"
            + "  var Wc=Math.floor((L+rb.width-Lc)/q)*q,Hc=Math.floor((T+rb.height-Tc)/q)*q;\n"
            + "  if(!(Wc>0&&Hc>0))return;\n"
            + "  var nl=(Lc-L)+'px',nt=(Tc-T)+'px',mw=Wc+'px',mh=Hc+'px',chg=false;\n"
            + "  if(st.left!==nl)st.left=nl;if(st.top!==nt)st.top=nt;\n"
            + "  if(st.maxWidth!==mw){st.maxWidth=mw;chg=true;}if(st.maxHeight!==mh){st.maxHeight=mh;chg=true;}\n"
            + "  if(chg&&!noResize){try{chart.resize();}catch(e){}}\n"
            + "}\n"
            + "function _spkAlignAll(){_spkCrispApply();\n"
            + "  var all=Chart.instances||{};Object.keys(all).forEach(function(k){try{_spkAlign(all[k]);}catch(e){}});}\n"
            // t2j fix8v: self-healing guard. Whatever event a browser zoom does or does not
            // fire (matchMedia change, window resize, ResizeObserver -- their order and
            // presence differ between Chrome builds), every draw checks that the chart's ratio
            // is the wanted one and the canvas is on the grid; if not, one re-apply + re-align
            // is scheduled (never inside the draw itself). Cheap: one getBoundingClientRect.
            + "function _spkAlignOk(chart){\n"
            + "  var cv=chart.canvas,d=window.devicePixelRatio||1,rr=chart.currentDevicePixelRatio,q=_spkQ(d);\n"
            + "  if(rr!==_spkCrispRatio())return false;if(!q)return true;\n"
            + "  var r=cv.getBoundingClientRect();if(!(r.width>0))return true;\n"
            + "  var L=r.left+(window.pageXOffset||0),T=r.top+(window.pageYOffset||0);\n"
            + "  return Math.abs(Math.round(L)*d-Math.round(Math.round(L)*d))<1e-3&&Math.abs(Math.round(T)*d-Math.round(Math.round(T)*d))<1e-3\n"
            + "    &&Math.abs(Math.round(r.width)*rr-cv.width)<0.01&&Math.abs(Math.round(r.height)*rr-cv.height)<0.01;\n"
            + "}\n"
            // Budget: at most 4 heals per device-ratio value, so a state the rule cannot
            // satisfy can never turn into a redraw loop.
            + "var _spkAlignPending=false,_spkHealN=0,_spkHealDpr=0;\n"
            + "var _spkAlignPlugin={id:'spkAlign',afterInit:function(chart){\n"
            + "  if(!_spkCrispOnPage(chart)||chart._spkAligned)return;chart._spkAligned=true;\n"
            + "  var orig=chart.resize;chart.resize=function(){_spkAlign(chart,true);return orig.apply(chart,arguments);};\n"
            + "  _spkAlign(chart);\n"
            + "},beforeDraw:function(chart){\n"
            + "  if(!_spkCrispOnPage(chart)||_spkAlignPending)return;\n"
            + "  var d=window.devicePixelRatio||1;if(d!==_spkHealDpr){_spkHealDpr=d;_spkHealN=0;}if(_spkHealN>=4)return;\n"
            + "  try{if(!_spkAlignOk(chart)){_spkHealN++;_spkAlignPending=true;setTimeout(function(){_spkAlignPending=false;_spkAlignAll();},0);}}catch(e){}\n"
            + "}};\n"
            + "Chart.register(_spkAlignPlugin);\n"
            + "try{if(window.ResizeObserver&&document.body){new ResizeObserver(function(){_spkAlignAll();}).observe(document.body);}}catch(e){}\n";

        // fix9g: spkBigScatter -- large-data mode for scatter/bubble (automatic above
        // ChartRenderer.BIG_SCATTER_MIN points; the renderer flags the datasets _spkBig:true).
        // MEASURED on the 100,000-point oracle (verify/harness/HarnessBig + bigscatter_probe,
        // headless Chromium, ratio 1.5): one chart draw stroked every arc through the page ctx
        // and cost ~1.2 s (bubble ~2.5 s); the load sequence ran 6 such draws (~5 s) and EVERY
        // hover-state change re-rendered the whole chart 4 times (~5 s per mouse move), which
        // is the lag a user feels. Hit-testing was ~4 ms and is left alone.
        // HOW: the point cloud of the flagged datasets is drawn ONCE onto an offscreen canvas
        // (device-pixel size, Chart.js' own transform, Chart.js' own per-dataset clip, every
        // element drawn by its own PointElement.draw in DATA order -- same radius, colour,
        // alpha, border and overlap order, so the bitmap is pixel-identical) and blitted 1:1
        // in beforeDatasetDraw at the place Chart.js would have drawn the first big dataset
        // (draw order = last dataset first, so fit lines / CI bands keep their z-order); the
        // flagged datasets' own draw is cancelled (return false), so a hover render costs one
        // drawImage. Elements keep their data, radius and hitRadius: tooltips, mlabel() and
        // filters are untouched. Active (hovered) elements are drawn live on top in
        // afterDatasetsDraw, so the hover highlight still shows.
        // The bitmap is built in chunks of BIG_CHUNK elements: the first chunk synchronously,
        // the rest one chunk per animation frame (the page stays responsive and the cloud
        // fills in over a few frames). It is rebuilt when its signature changes -- bitmap
        // size, device ratio, chart area, axis ranges, the visible big datasets, their data
        // array identity/length, colour or radius (i.e. after a filter/slider change, a
        // resize or a legend toggle) -- never on a hover. Export clones (canvas2svg / saveas:
        // no .spk-chart-box parent, the same guard the crisp stack uses) get the cloud as one
        // raster <image> at 2x (fix9g-b, see beforeDatasetDraw); everything else in the export
        // stays vector. _spkBigState(chart) is read by verify/bigscatter_probe.js.
        pluginReg += "var _SPK_BIG_CHUNK=20000;\n"
            + "function _spkBigMetas(chart){var out=[],ms=chart.getSortedVisibleDatasetMetas();\n"
            + "  for(var i=ms.length-1;i>=0;i--){var d=chart.data.datasets[ms[i].index];if(d&&d._spkBig)out.push(ms[i]);}return out;}\n"
            + "function _spkBigClip(chart,meta){var c=meta._clip;if(!c||c.disabled)return null;var a=chart.chartArea,xs=meta.xScale,ys=meta.yScale;\n"
            + "  var n=(xs&&ys)?{left:xs.options.clip?xs.left:a.left,right:xs.options.clip?xs.right:a.right,top:ys.options.clip?ys.top:a.top,bottom:ys.options.clip?ys.bottom:a.bottom}:a;\n"
            + "  return {left:c.left===false?0:n.left-c.left,right:c.right===false?chart.width:n.right+c.right,top:c.top===false?0:n.top-c.top,bottom:c.bottom===false?chart.height:n.bottom+c.bottom};}\n"
            + "function _spkBigSig(chart,metas){var cv=chart.canvas,a=chart.chartArea||{},s=[cv.width,cv.height,chart.currentDevicePixelRatio||1,a.left,a.top,a.right,a.bottom];\n"
            + "  for(var i=0;i<metas.length;i++){var m=metas[i],d=chart.data.datasets[m.index];\n"
            + "    s.push(m.index,(m.data||[]).length,d.data?d.data.length:0,d.backgroundColor,d.borderColor,d.pointRadius,d.pointStyle,d.pointBorderWidth,\n"
            + "      m.xScale?m.xScale.min+'/'+m.xScale.max:'',m.yScale?m.yScale.min+'/'+m.yScale.max:'');}\n"
            + "  return s.join('|');}\n"
            + "function _spkBigStale(chart,st){var metas=_spkBigMetas(chart);if(!st.off||!st.refs||st.refs.length!==metas.length)return true;\n"
            + "  for(var i=0;i<metas.length;i++){if(st.refs[i]!==chart.data.datasets[metas[i].index].data)return true;}\n"
            + "  return st.sig!==_spkBigSig(chart,metas);}\n"
            + "function _spkBigStart(chart,st){\n"
            + "  var cv=chart.canvas,W=cv.width,H=cv.height,r=chart.currentDevicePixelRatio||1;\n"
            // fix9h-c: export rasters are 1x (intrinsic size == display size). Fahad's PDFs
            // showed that the fast PDF path (jsvg -> PDFBox graphics2d) draws an <image> at its
            // intrinsic pixel size through a tiling pattern (BBox 1912x954, Matrix = flip only),
            // ignoring BOTH <image width/height> (fix9g-b) and an enclosing scale transform
            // (fix9h-b) -- the only placement it honours is an unscaled image. So the raster is
            // built at the clone's own size; PNG/SVG/browser routes show the same 1x cloud.
            // fix9h-e: and page-ALIGNED. Fahad's fix9h-c PDFs had the 1x cloud the right size but
            // ~46 pt too low: the pattern's matrix is [1 0 0 -1 0 imageHeight] in PAGE space, so
            // the image's bottom-left always sits on the page origin (and it tiles with XStep =
            // its width, which made the second by() panel look almost right). So the export
            // raster is the size of the whole export page and the cloud is drawn at the chart's
            // page offset (ctx.__spkPage, set by _spkChartToSvg / _spkPageToSvg); blitted at
            // (-x,-y) it lands at page (0,0) under correct SVG semantics AND under the
            // pattern's page-origin anchoring. Chrome/rsvg/jsvg-raster: identical result.
            + "  var pg=st.export?chart.ctx.__spkPage:null;\n"
            + "  if(st.export){r=1;W=Math.round(pg?pg.w:chart.width);H=Math.round(pg?pg.h:chart.height);}\n"
            + "  if(!st.off)st.off=document.createElement('canvas');\n"
            + "  if(st.off.width!==W)st.off.width=W;if(st.off.height!==H)st.off.height=H;\n"
            + "  var ctx=st.off.getContext('2d');ctx.setTransform(1,0,0,1,0,0);ctx.clearRect(0,0,W,H);ctx.setTransform(r,0,0,r,pg?pg.x:0,pg?pg.y:0);\n"
            + "  st.metas=_spkBigMetas(chart);st.refs=st.metas.map(function(m){return chart.data.datasets[m.index].data;});st.sig=_spkBigSig(chart,st.metas);\n"
            + "  st.area=chart.chartArea;st.mi=0;st.ei=0;st.points=0;st.done=false;st.builds++;st.t0=_spkNow();\n"
            + "  _spkBigStep(chart,st);\n"
            + "}\n"
            + "function _spkNow(){return (window.performance&&performance.now)?performance.now():Date.now();}\n"
            + "function _spkBigStep(chart,st){\n"
            + "  var ctx=st.off.getContext('2d'),budget=_SPK_BIG_CHUNK,area=st.area;\n"
            + "  while(budget>0&&st.mi<st.metas.length){var meta=st.metas[st.mi],els=meta.data||[];\n"
            + "    if(st.ei===0){ctx.save();var clip=_spkBigClip(chart,meta);if(clip){ctx.beginPath();ctx.rect(clip.left,clip.top,clip.right-clip.left,clip.bottom-clip.top);ctx.clip();}}\n"
            + "    var end=Math.min(els.length,st.ei+budget);\n"
            + "    for(var i=st.ei;i<end;i++){var el=els[i];if(el.hidden)continue;\n"
            + "      if(el.active){var q=Object.create(el);q.options=meta.controller.resolveDataElementOptions(i,'default');q.draw(ctx,area);}\n"
            + "      else el.draw(ctx,area);st.points++;}\n"
            + "    budget-=end-st.ei;st.ei=end;\n"
            + "    if(st.ei>=els.length){ctx.restore();st.mi++;st.ei=0;}}\n"
            + "  if(st.mi>=st.metas.length){st.done=true;st.ms=Math.round(_spkNow()-st.t0);}\n"
            + "}\n"
            + "function _spkBigState(chart){var st=chart&&chart._spkBig;return st?{builds:st.builds,points:st.points,done:!!st.done,w:st.off?st.off.width:0,h:st.off?st.off.height:0,ms:st.ms||0}:null;}\n"
            + "var _spkBigScatter={id:'spkBigScatter',\n"
            // (an export clone is drawn once since fix9u -- _spkExportChart -- but its blit flag is
            // still never reset, so a second draw would add no second <image>)
            + "  beforeDatasetsDraw:function(chart){if(chart._spkBig&&!chart._spkBig.export)chart._spkBig.blitted=false;},\n"
            + "  beforeDatasetDraw:function(chart,args){\n"
            + "    var d=chart.data.datasets[args.meta.index];if(!d||!d._spkBig)return;\n"
            + "    if(!_spkCrispOnPage(chart)){\n"
            // fix9g-b: EXPORT CLONES (canvas2svg: _spkChartToSvg / _spkPageToSvg / the print swap)
            // draw the cloud as ONE raster image at 2x the export size, built synchronously from
            // the clone's own elements (so it sits exactly on the clone's axes); axes, text, fit
            // lines and CI bands stay vector. Fahad's real-Stata run (2026-09-14): the vector
            // clone of a 100k page emitted 200,098 paths (52 MB) and took 11-20 s, so the
            // session-browser export timed out (svg 20,029 ms at the 20 s DevTools cap, PNG
            // print swap never signalled) and a 50 MB SVG / 100k-path PDF is unusable anyway.
            + "      var se=chart._spkBig||(chart._spkBig={builds:0,export:true});\n"
            + "      if(!se.off||!se.done){_spkBigStart(chart,se);while(!se.done)_spkBigStep(chart,se);}\n"
            // fix9h-b tried a ctx.scale() around an intrinsic-size 2x image instead of <image
            // width/height>; the PDF path ignored that too (see fix9h-c in _spkBigStart).
            + "      if(!se.blitted){se.blitted=true;var pg2=chart.ctx.__spkPage;chart.ctx.drawImage(se.off,pg2?-pg2.x:0,pg2?-pg2.y:0);}\n"   // fix9h-c/e: 1x, page-aligned, no scale
            + "      return false;\n"
            + "    }\n"
            + "    var st=chart._spkBig||(chart._spkBig={builds:0});\n"
            + "    if(_spkBigStale(chart,st))_spkBigStart(chart,st);\n"
            + "    if(!st.blitted){st.blitted=true;var ctx=chart.ctx;ctx.save();ctx.setTransform(1,0,0,1,0,0);ctx.drawImage(st.off,0,0);ctx.restore();}\n"
            + "    if(!st.done&&!st.raf){st.raf=requestAnimationFrame(function(){st.raf=0;if(st.done||_spkBigStale(chart,st))return;_spkBigStep(chart,st);try{chart.draw();}catch(e){}});}\n"
            + "    return false;\n"
            + "  },\n"
            + "  afterDatasetsDraw:function(chart){\n"
            + "    if(!chart._spkBig||!_spkCrispOnPage(chart))return;var act=chart.getActiveElements?chart.getActiveElements():[];\n"
            + "    for(var i=0;i<act.length;i++){var a=act[i],d=chart.data.datasets[a.datasetIndex];if(d&&d._spkBig&&a.element&&!a.element.hidden)a.element.draw(chart.ctx,chart.chartArea);}\n"
            + "  },\n"
            + "  beforeDestroy:function(chart){if(chart._spkBig){if(chart._spkBig.raf)cancelAnimationFrame(chart._spkBig.raf);chart._spkBig=null;}}\n"
            + "};\n"
            + "Chart.register(_spkBigScatter);\n";

        // t2j fix8w: legbgcolor() was accepted and ignored (Chart.js legends have no background
        // option; the value sat in labels.backgroundColor). spkLegendBg paints a filled, lightly
        // rounded box behind the legend of any chart whose legend labels carry spkBg, then
        // redraws the legend on top (the built-in legend plugin's beforeDraw runs first).
        pluginReg += "var _spkLegendBg={id:'spkLegendBg',beforeDraw:function(chart){\n"
            + "  try{var lg=chart.legend,lo=chart.options.plugins&&chart.options.plugins.legend,bg=lo&&lo.labels&&lo.labels.spkBg;\n"
            + "  if(!lg||!bg||lo.display===false||!(lg.width>0&&lg.height>0))return;\n"
            // the box hugs the entries (union of the legend hit boxes), not the full-width legend band
            + "  var hb=lg.legendHitBoxes||[],x0=Infinity,y0=Infinity,x1=-Infinity,y1=-Infinity;\n"
            + "  hb.forEach(function(b){if(!(b.width>0))return;x0=Math.min(x0,b.left);y0=Math.min(y0,b.top);x1=Math.max(x1,b.left+b.width);y1=Math.max(y1,b.top+b.height);});\n"
            + "  if(!isFinite(x0)){x0=lg.left;y0=lg.top;x1=lg.left+lg.width;y1=lg.top+lg.height;}\n"
            + "  var ctx=chart.ctx,p=6,x=x0-p,y=y0-p,w=x1-x0+2*p,h=y1-y0+2*p,r=4;\n"
            + "  ctx.save();ctx.fillStyle=bg;ctx.beginPath();ctx.moveTo(x+r,y);ctx.lineTo(x+w-r,y);ctx.quadraticCurveTo(x+w,y,x+w,y+r);\n"
            + "  ctx.lineTo(x+w,y+h-r);ctx.quadraticCurveTo(x+w,y+h,x+w-r,y+h);ctx.lineTo(x+r,y+h);ctx.quadraticCurveTo(x,y+h,x,y+h-r);\n"
            + "  ctx.lineTo(x,y+r);ctx.quadraticCurveTo(x,y,x+r,y);ctx.closePath();ctx.fill();ctx.restore();\n"
            + "  if(typeof lg.draw==='function')lg.draw();}catch(e){}\n"
            + "}};\n"
            + "Chart.register(_spkLegendBg);\n";

        // v3.5.36: debug comment so user can verify jar version and key flags
        return "<!DOCTYPE html>\n<!-- sparkta v" + VERSION + " fill=" + o.chart.fill
            + " stack=" + o.chart.stack + " type=" + o.type + (o.chart.plotMargin.isEmpty() ? "" : " plotmargin=" + o.chart.plotMargin.replace(" ", ",")) + (o.chart.peNoci ? " noci" : "") + " -->\n<html lang='en'>\n<head>\n"
            + "  <meta charset='UTF-8'>\n"
            + "  <meta name='viewport' content='width=device-width,initial-scale=1.0'>\n"
            + "  <title>" + escHtml(o.title) + "</title>\n"
            + ""
            + scriptTags
            + "  <style>" + buildCss(bgCol, plotCl, dark) + "</style>\n"
            + "</head>\n<body>\n<div class='container'>\n"
            // v2.6.0 Phase 1-A: apply titleSize/titleColor inline when user provides them
            + "  <h1 style='" + buildTitleStyle() + "'>" + escHtml(o.title) + "</h1>\n"
            // v3.6.0-s8i: the slot under the title carries CONTEXT (what statistic, of
            // what, by what, how many obs, which CI) -- user subtitle() if given, else a
            // generated one. The build stamp moves to a footer (see below).
            + "  <p class='subtitle2' style='" + buildSubtitleStyle() + "'>" + (o.subtitle.isEmpty() ? contextLine(data) : escHtml(o.subtitle)) + "</p>\n"
            + filterUi
            + chartsHtml
            + (o.isPostEstimation() ? new PubTable(this, o).buildBlock(data) : "")
            + cr.buildNoteCaption()
            + sr.buildStatsSection(data)
            + (o.style.noTimestamp ? "" : "  <p class='subtitle' style='margin-top:1rem;font-size:.78rem;'>Made with sparkta v" + VERSION + " for Stata &bull; " + new java.util.Date() + "</p>\n")
            + "</div>\n"
            + "<script>\n" + pluginReg + filterData + histPre
            // v2.6.1: emit per-panel histogram preambles (_ttRanges_/_ttCounts_ for by() panels)
            + buildByHistPreambles()
            + scriptFinal + filterScript + sr.buildStatsJs()
            // v2.7.0: download function -- emitted only when download=1
            + buildDownloadJs(plotCl)
            // v3.6.0-s6b1: post-est table export functions (Copy/CSV/LaTeX/Customize)
            + buildTblExportJs()
            // v3.6.0-t2h (batch 2a): publication table render + Markdown/tidy-CSV/booktabs exports
            + (o.isPostEstimation() ? new PubTable(this, o).buildJs() : "")
            + "</script>\n"
            + "</body>\n</html>";
    }

    // -- by() panels -----------------------------------------------------------

    /**
     * Builds the coefficient results table for post-estimation charts (v3.6.0).
     * Shows: variable name, coefficient, SE, t/z stat, p-value, CI bounds.
     * Also shows base/omitted variables (greyed out) and model info footer.
     * Wrapped in .coefplot-wrap for consistent width with the chart.
     */
    String buildPostEstTable() {   // v3.6.0-t2h: package-private so PubTable can embed it as the "Detailed" view
        if (o.chart.peNames.isEmpty()) return "";

        boolean isMultiModel = o.chart.peNames.contains("~");
        boolean isTdist = o.chart.peTstat.equals("1");
        String statLabel = isTdist ? "t" : "z";
        String ciLevel = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel;

        boolean dark = isDark();
        String borderCol = dark ? "#2a2a4a" : "#e0e0e0";
        String headerBg  = dark ? "#1e3a5a" : "#f0f4f8";
        String textCol   = dark ? "#e0e0e0" : "#333333";
        String headCol   = dark ? "#a8d8ea" : "#2c3e50";
        String mutedCol  = dark ? "#888888" : "#666666";
        String baseCol   = dark ? "#555555" : "#aaaaaa";
        String baseBg    = dark ? "#1a1a2e" : "#fafafa";
        String sigCol    = dark ? "#7ab8e0" : "#2980b9";
        String modelBg   = dark ? "#162a4a" : "#f4f7fb";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='coefplot-wrap spk-table-block' id='spkTableBlock' style='margin-top:1rem;'>\n");
        sb.append("<table style='width:100%;border-collapse:collapse;font-size:.82rem;")
          .append("border:1px solid ").append(borderCol).append(";border-radius:6px;overflow:hidden;'>\n");

        // Header
        String thStyle = "padding:.5rem .65rem;text-align:left;font-size:.72rem;font-weight:600;"
            + "color:" + mutedCol + ";letter-spacing:.04em;text-transform:uppercase;"
            + "border-bottom:2px solid " + borderCol + ";";
        String thStyleR = thStyle.replace("text-align:left", "text-align:right");
        sb.append("<thead><tr style='background:").append(headerBg).append(";'>");
        sb.append("<th style='").append(thStyle).append("'></th>");
        sb.append("<th style='").append(thStyleR).append("'>Coef.</th>");
        sb.append("<th style='").append(thStyleR).append("'>Std. Err.</th>");
        sb.append("<th style='").append(thStyleR).append("'>").append(statLabel).append("</th>");
        sb.append("<th style='").append(thStyleR).append("'>P&gt;|").append(statLabel).append("|</th>");
        sb.append("<th style='").append(thStyleR).append("'>").append(ciLevel).append("% CI Lower</th>");
        sb.append("<th style='").append(thStyleR).append("'>").append(ciLevel).append("% CI Upper</th>");
        sb.append("</tr></thead>\n<tbody>\n");

        String tdStyle = "padding:.4rem .65rem;border-bottom:1px solid " + borderCol
            + ";color:" + textCol + ";text-align:right;";
        String tdStyleL = tdStyle.replace("text-align:right", "text-align:left")
            + "font-weight:600;color:" + headCol + ";";

        if (isMultiModel) {
            String[] mNameGrp = o.chart.peNames.split("~", -1);
            String[] mCoefGrp = o.chart.peCoefs.split("~", -1);
            String[] mSeGrp   = o.chart.peSes.split("~", -1);
            String[] mPvalGrp = o.chart.pePvals.split("~", -1);
            String[] mLoGrp   = o.chart.peLower.split("~", -1);
            String[] mHiGrp   = o.chart.peUpper.split("~", -1);
            String[] mNobsArr = o.chart.peNobs.split("~", -1);
            String[] mTzGrp   = o.chart.peTzvals.isEmpty()
                ? new String[0]
                : o.chart.peTzvals.split("~", -1);
            // v3.6.0-s6c10: prefer peEstlabels (user labels) over peEstNames (store names)
            String estLblsSrcHtml = (!o.chart.peEstlabels.isEmpty()) ? o.chart.peEstlabels : o.chart.peEstNames;
            String[] estLbls  = estLblsSrcHtml.isEmpty()
                ? new String[mNameGrp.length]
                : estLblsSrcHtml.split("~", -1);
            int nM = mNameGrp.length;

            for (int m = 0; m < nM; m++) {
                String ml = (m < estLbls.length && estLbls[m] != null && !estLbls[m].isEmpty())
                    ? estLbls[m] : "Model " + (m + 1);
                String mn = (m < mNobsArr.length) ? mNobsArr[m].trim() : "";
                String nLbl = mn.isEmpty() ? "" : " (N=" + mn + ")";
                // Model header
                sb.append("<tr style='background:").append(modelBg).append(";'>");
                sb.append("<td colspan='7' style='padding:.55rem .65rem;font-weight:700;font-size:.83rem;")
                  .append("color:").append(headCol).append(";border-bottom:1px solid ").append(borderCol)
                  .append(";border-top:").append(m > 0 ? "2px" : "0px").append(" solid ").append(borderCol).append(";'>")
                  .append(escHtml(ml)).append("<span style='font-weight:400;color:")
                  .append(mutedCol).append(";font-size:.75rem;margin-left:.5rem;'>").append(nLbl).append("</span></td></tr>\n");
                // Rows
                String[] mn2 = mNameGrp[m].split("\\|", -1);
                String[] mc  = mCoefGrp[m].split("\\|", -1);
                String[] ms  = mSeGrp[m].split("\\|", -1);
                String[] mp  = mPvalGrp[m].split("\\|", -1);
                String[] mlo = mLoGrp[m].split("\\|", -1);
                String[] mhi = mHiGrp[m].split("\\|", -1);
                // v3.6.0: pre-computed t/z values (invariant under eform/rescale)
                String[] mtz = (m < mTzGrp.length && !mTzGrp[m].isEmpty())
                    ? mTzGrp[m].split("\\|", -1) : new String[0];
                for (int i = 0; i < mn2.length; i++) {
                    sb.append("<tr>");
                    // v3.6.0-s6c27: apply display label resolution
                    sb.append("<td style='").append(tdStyleL).append("padding-left:1.5rem;'>")
                      .append(escHtml(resolveCoefLabel(mn2[i].trim()))).append("</td>");
                    sb.append("<td style='").append(tdStyle).append("'>").append(fmtNum(mc[i].trim())).append("</td>");
                    sb.append("<td style='").append(tdStyle).append("'>").append(fmtNum(ms[i].trim())).append("</td>");
                    // v3.6.0: use pre-computed t/z stat (correct under eform/rescale)
                    String tv = (i < mtz.length && !mtz[i].trim().isEmpty())
                        ? fmtTz(mtz[i].trim()) : compTstat(mc[i].trim(), ms[i].trim());
                    sb.append("<td style='").append(tdStyle).append("'>").append(tv).append("</td>");
                    String pv = mp[i].trim();
                    String st = starsFor(pv);   // v3.6.0-t2h: honour stars()/nostars
                    sb.append("<td style='").append(tdStyle).append("color:").append(st.isEmpty() ? textCol : sigCol).append(";'>")
                      .append(fmtPval(pv)).append("<span style='font-size:.68rem;'>").append(st).append("</span></td>");
                    sb.append("<td style='").append(tdStyle).append("'>").append(fmtNum(mlo[i].trim())).append("</td>");
                    sb.append("<td style='").append(tdStyle).append("'>").append(fmtNum(mhi[i].trim())).append("</td>");
                    sb.append("</tr>\n");
                }
            }
        } else {
            // Single model
            String[] names = o.chart.peNames.split("\\|", -1);
            String[] coefs = o.chart.peCoefs.split("\\|", -1);
            String[] ses   = o.chart.peSes.split("\\|", -1);
            String[] pvals = o.chart.pePvals.split("\\|", -1);
            String[] lower = o.chart.peLower.split("\\|", -1);
            String[] upper = o.chart.peUpper.split("\\|", -1);
            // v3.6.0: pre-computed t/z values (invariant under eform/rescale)
            String[] tzvals = o.chart.peTzvals.isEmpty()
                ? new String[0]
                : o.chart.peTzvals.split("\\|", -1);
            int k = names.length;
            // Base rows
            if (!o.chart.peBases.isEmpty()) {
                String[] bases = o.chart.peBases.split("\\|", -1);
                String bTd = "padding:.35rem .65rem;border-bottom:1px solid " + borderCol
                    + ";color:" + baseCol + ";text-align:right;font-style:italic;";
                String bTdL = bTd.replace("text-align:right", "text-align:left")
                    + "font-weight:500;font-style:normal;";
                for (String b : bases) {
                    sb.append("<tr style='background:").append(baseBg).append(";'>");
                    // v3.6.0-s6c27: apply display label resolution to base category row
                    sb.append("<td style='").append(bTdL).append("'>").append(escHtml(resolveCoefLabel(b.trim()))).append("</td>");
                    sb.append("<td style='").append(bTd).append("'>(base)</td>");
                    for (int c = 0; c < 5; c++) sb.append("<td style='").append(bTd).append("'></td>");
                    sb.append("</tr>\n");
                }
            }
            for (int i = 0; i < k; i++) {
                sb.append("<tr>");
                // v3.6.0-s6c27: apply display label resolution (custom > variable label > raw name)
                sb.append("<td style='").append(tdStyleL).append("'>").append(escHtml(resolveCoefLabel(names[i].trim()))).append("</td>");
                sb.append("<td style='").append(tdStyle).append("'>").append(fmtNum(coefs[i].trim())).append("</td>");
                sb.append("<td style='").append(tdStyle).append("'>").append(fmtNum(ses[i].trim())).append("</td>");
                // v3.6.0: use pre-computed t/z stat (correct under eform/rescale)
                String tv = (i < tzvals.length && !tzvals[i].trim().isEmpty())
                    ? fmtTz(tzvals[i].trim()) : compTstat(coefs[i].trim(), ses[i].trim());
                sb.append("<td style='").append(tdStyle).append("'>").append(tv).append("</td>");
                String pv = pvals[i].trim();
                String st = starsFor(pv);   // T1 (t2j): honour stars()/nostars (was hardcoded pStars)
                sb.append("<td style='").append(tdStyle).append("color:").append(st.isEmpty() ? textCol : sigCol).append(";'>")
                  .append(fmtPval(pv)).append("<span style='font-size:.68rem;'>").append(st).append("</span></td>");
                sb.append("<td style='").append(tdStyle).append("'>").append(fmtNum(lower[i].trim())).append("</td>");
                sb.append("<td style='").append(tdStyle).append("'>").append(fmtNum(upper[i].trim())).append("</td>");
                sb.append("</tr>\n");
            }
        }
        sb.append("</tbody></table>\n");
        // v3.6.0-s6b1: Export buttons row (Copy / CSV / LaTeX / Customize)
        sb.append(buildTblExportButtons());
        // Footer
        sb.append("<div style='display:flex;flex-wrap:wrap;justify-content:space-between;gap:.3rem .8rem;")
          .append("padding:.5rem .2rem;font-size:.73rem;color:").append(mutedCol).append(";'>\n");
        if (!o.chart.peCmd.isEmpty())
            sb.append("  <span>Model: ").append(escHtml(o.chart.peCmd)).append("</span>\n");
        if (!isMultiModel && !o.chart.peNobs.isEmpty())
            sb.append("  <span>N = ").append(o.chart.peNobs).append("</span>\n");
        if (!o.chart.peDepvar.isEmpty())
            sb.append("  <span>Dep. var: ").append(escHtml(o.chart.peDepvar)).append("</span>\n");
        sb.append("  <span>CI: ").append(ciLevel).append("% (")
          .append(isTdist ? "t-distribution" : "z / normal").append(")</span>\n");
        // v3.6.0-t2h: legend reflects stars() (default text unchanged); hidden under nostars
        if (!o.table.noStars) {
            String _leg = escHtml(starLegendText()).replace(", ", " &nbsp; ");
            sb.append("  <span style='font-style:italic;'>p-value: ").append(_leg).append("</span>\n");
        }
        sb.append("</div>\n</div>\n");
        // v3.6.0-s6b1: __tblData JS object for export functions
        sb.append(buildTblDataJs());
        return sb.toString();
    }

    /**
     * v3.6.0-s6b1: Export toolbar for the post-estimation table.
     * Four buttons: Copy (TSV), CSV, LaTeX, Customize.
     * Themed to match buildDownloadButtons() styling.
     * Buttons call _spkTblExport(fmt) and _spkTblCustomize() defined in buildTblExportJs().
     */
    private String buildTblExportButtons() {
        boolean dark = isDark();
        String btnBg     = dark ? "#2a2a4a" : "#f4f4f4";
        String btnColor  = dark ? "#d0d0e8" : "#444444";
        String btnBorder = dark ? "#44447a" : "#c8c8c8";
        String btnHover  = dark ? "#3a3a6a" : "#e0e0e0";
        String labelCol  = dark ? "#888899" : "#aaaaaa";
        String dividerCol = dark ? "#44447a" : "#d0d0d0";
        String toolbarStyle = "display:flex;justify-content:flex-end;align-items:center;"
            + "gap:5px;margin:6px 0 4px 0;";
        String labelStyle = "font-size:10px;color:" + labelCol
            + ";margin-right:2px;font-family:inherit;";
        String btnStyle = "background:" + btnBg + ";color:" + btnColor
            + ";border:1px solid " + btnBorder
            + ";border-radius:3px;padding:2px 9px;font-size:10px;font-weight:600;"
            + "cursor:pointer;font-family:inherit;letter-spacing:0.03em;"
            + "transition:background 0.12s,border-color 0.12s;";
        String over = " onmouseover=\"this.style.background='" + btnHover + "'\"";
        String out  = " onmouseout=\"this.style.background='"  + btnBg    + "'\"";
        // Divider: a thin vertical line separating export actions from layout controls
        String dividerStyle = "display:inline-block;width:1px;height:16px;"
            + "background:" + dividerCol + ";margin:0 3px;vertical-align:middle;";
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='spk-toolbar' style='").append(toolbarStyle).append("'>");   // t2j fix9c: class so print/PNG scopes can hide the whole row (the "Export:" label leaked)
        // -- Export group --
        sb.append("<span style='").append(labelStyle).append("'>Export:</span>");
        sb.append("<button id='_tblBtnCopy' style='").append(btnStyle).append("'")
          .append(over).append(out)
          .append(" onclick=\"_spkTblExport('copy')\">Copy</button>");
        sb.append("<button style='").append(btnStyle).append("'")
          .append(over).append(out)
          .append(" onclick=\"_spkTblExport('csv')\">CSV</button>");
        sb.append("<button style='").append(btnStyle).append("'")
          .append(over).append(out)
          .append(" onclick=\"_spkTblExport('latex')\">LaTeX</button>");
        // s9o: table-only PDF -- prints just the table block (with title/context) via the browser
        sb.append("<button style='").append(btnStyle).append("' title='Table only, as PDF (print dialog)'")
          .append(over).append(out)
          .append(" onclick=\"_spkPrintOnly('table')\">PDF</button>");
        // -- Divider --
        sb.append("<span style='").append(dividerStyle).append("'></span>");
        // -- Layout / config group --
        sb.append("<span style='").append(labelStyle).append("'>Layout:</span>");
        String activeStyle = "background:" + (dark ? "#3a3a6a" : "#d0d8e8")
            + ";color:" + btnColor
            + ";border:1px solid " + (dark ? "#6a6aaa" : "#7090b0")
            + ";border-radius:3px 0 0 3px;padding:2px 8px;font-size:10px;font-weight:600;"
            + "cursor:pointer;font-family:inherit;";
        String inactiveStyle = "background:" + btnBg
            + ";color:" + (dark ? "#888899" : "#999999")
            + ";border:1px solid " + btnBorder
            + ";border-left:none;border-radius:0 3px 3px 0;padding:2px 8px;font-size:10px;"
            + "cursor:pointer;font-family:inherit;";
        sb.append("<span style='display:inline-flex;align-items:center;' title='LaTeX layout: Compact=coef+SE only, Full=all statistics'>")
          .append("<button id='_tblBtnCompact' style='").append(activeStyle).append("'")
          .append(" onclick=\"_spkSetLatexMode(false)\">Compact</button>")
          .append("<button id='_tblBtnFull' style='").append(inactiveStyle).append("'")
          .append(" onclick=\"_spkSetLatexMode(true)\">Full</button>")
          .append("</span>");
        sb.append("<button style='").append(btnStyle).append("'")
          .append(over).append(out)
          .append(" onclick=\"_spkTblCustomize()\" title='Add indicator rows and additional stats'>")
          .append("&#9881; Customize</button>");
        sb.append("</div>\n");
        return sb.toString();
    }

    /**
     * v3.6.0-s6b1: Emits window.__tblData JS object for export functions.
     * Single normalized schema (multi-model and single-model both use models[]):
     *   window.__tblData = {
     *     isMulti: bool, statLabel: "z"|"t", ciLevel: "95",
     *     cmd: "logit", depvar: "foreign",
     *     headers: ["", "Coef.", "Std. Err.", "z", "P>|z|", "95% CI Lo", "95% CI Hi"],
     *     models: [
     *       { label: "Model 1", n: "74",
     *         rows: [{name, coef, se, tz, p, lo, hi, sig}, ...] },
     *       ...
     *     ]
     *   };
     * Numbers are pre-formatted strings to match what is rendered on screen.
     * Stars (sig) are pre-computed via pStars() so JS does not recompute.
     */
    private String buildTblDataJs() {
        if (o.chart.peNames.isEmpty()) return "";
        boolean isMultiModel = o.chart.peNames.contains("~");
        boolean isTdist = o.chart.peTstat.equals("1");
        String statLabel = isTdist ? "t" : "z";
        String ciLevel = o.stats.cilevel.isEmpty() ? "95" : o.stats.cilevel;

        StringBuilder js = new StringBuilder();
        js.append("<script>\n");
        js.append("window.__tblData = {\n");
        js.append("  isMulti: ").append(isMultiModel).append(",\n");
        js.append("  statLabel: '").append(statLabel).append("',\n");
        js.append("  ciLevel: '").append(escJs(ciLevel)).append("',\n");
        js.append("  cmd: '").append(escJs(o.chart.peCmd)).append("',\n");
        js.append("  depvar: '").append(escJs(o.chart.peDepvar)).append("',\n");
        js.append("  dist: '").append(isTdist ? "t-distribution" : "z / normal").append("',\n");
        // v3.6.0-t2h (batch 2a): publication-table controls + structural rows the
        // PubTable renderer/exports need, all from this one object so the chart,
        // on-page pub table, LaTeX, Markdown and CSV can never disagree.
        js.append("  starLegend: '").append(escJs(starLegendText())).append("',\n");
        js.append("  starLegendLatex: '").append(escJs(starLegendLatex())).append("',\n");   // T1 (t2j)
        js.append("  noStars: ").append(o.table.noStars).append(",\n");
        js.append("  showT: ").append(o.table.tstat).append(",\n");
        js.append("  showCI: ").append(o.table.ci).append(",\n");
        js.append("  noFooter: ").append(o.table.noFooter).append(",\n");
        js.append("  bases: ").append(jsBasesArray()).append(",\n");
        js.append("  headings: ").append(jsHeadingsArray()).append(",\n");
        js.append("  headers: ['', 'Coef.', 'Std. Err.', '")
          .append(statLabel).append("', 'P>|").append(statLabel).append("|', '")
          .append(escJs(ciLevel)).append("% CI Lo', '")
          .append(escJs(ciLevel)).append("% CI Hi'],\n");
        js.append("  models: [\n");

        if (isMultiModel) {
            String[] mNameGrp = o.chart.peNames.split("~", -1);
            String[] mCoefGrp = o.chart.peCoefs.split("~", -1);
            String[] mSeGrp   = o.chart.peSes.split("~", -1);
            String[] mPvalGrp = o.chart.pePvals.split("~", -1);
            String[] mLoGrp   = o.chart.peLower.split("~", -1);
            String[] mHiGrp   = o.chart.peUpper.split("~", -1);
            String[] mNobsArr = o.chart.peNobs.isEmpty()
                ? new String[mNameGrp.length] : o.chart.peNobs.split("~", -1);
            String[] mTzGrp   = o.chart.peTzvals.isEmpty()
                ? new String[0] : o.chart.peTzvals.split("~", -1);
            // v3.6.0-s6c10: use peEstlabels (user-supplied via estlabels()) when available,
            // falling back to peEstNames (est store names). Fixes CSV/LaTeX showing m1/m2
            // instead of Simple/Controls/Full when estlabels() is specified.
            String estLblsSrc = (!o.chart.peEstlabels.isEmpty()) ? o.chart.peEstlabels : o.chart.peEstNames;
            String[] estLbls  = estLblsSrc.isEmpty()
                ? new String[mNameGrp.length] : estLblsSrc.split("~", -1);
            // v3.6.0-s6c: split peModelStats by ~ to get per-model fit stat strings
            String[] mFitGrp = o.chart.peModelStats.isEmpty()
                ? new String[0] : o.chart.peModelStats.split("~", -1);
            int nM = mNameGrp.length;
            for (int m = 0; m < nM; m++) {
                String ml = (m < estLbls.length && estLbls[m] != null && !estLbls[m].isEmpty())
                    ? estLbls[m] : "Model " + (m + 1);
                String mn = (m < mNobsArr.length && mNobsArr[m] != null) ? mNobsArr[m].trim() : "";
                String fitJs = (m < mFitGrp.length) ? fitStatsJs(mFitGrp[m]) : "{}";
                js.append("    { label: '").append(escJs(ml)).append("', n: '")
                  .append(escJs(mn)).append("', stats: ").append(fitJs).append(", rows: [\n");
                String[] nm = mNameGrp[m].split("\\|", -1);
                String[] mc = mCoefGrp[m].split("\\|", -1);
                String[] ms = mSeGrp[m].split("\\|", -1);
                String[] mp = mPvalGrp[m].split("\\|", -1);
                String[] mlo = mLoGrp[m].split("\\|", -1);
                String[] mhi = mHiGrp[m].split("\\|", -1);
                String[] mtz = (m < mTzGrp.length && !mTzGrp[m].isEmpty())
                    ? mTzGrp[m].split("\\|", -1) : new String[0];
                for (int i = 0; i < nm.length; i++) {
                    String tv = (i < mtz.length && !mtz[i].trim().isEmpty())
                        ? fmtTz(mtz[i].trim()) : compTstat(mc[i].trim(), ms[i].trim());
                    String pv = mp[i].trim();
                    String st = starsFor(pv);   // v3.6.0-t2h: honour stars()/nostars
                    appendRowJs(js, nm[i].trim(), mc[i].trim(), ms[i].trim(),
                        tv, pv, mlo[i].trim(), mhi[i].trim(), st);
                    if (i < nm.length - 1) js.append(",\n"); else js.append("\n");
                }
                js.append("    ] }");
                if (m < nM - 1) js.append(",\n"); else js.append("\n");
            }
        } else {
            String[] names = o.chart.peNames.split("\\|", -1);
            String[] coefs = o.chart.peCoefs.split("\\|", -1);
            String[] ses   = o.chart.peSes.split("\\|", -1);
            String[] pvals = o.chart.pePvals.split("\\|", -1);
            String[] lower = o.chart.peLower.split("\\|", -1);
            String[] upper = o.chart.peUpper.split("\\|", -1);
            String[] tzvals = o.chart.peTzvals.isEmpty()
                ? new String[0] : o.chart.peTzvals.split("\\|", -1);
            String mLbl = "";
            String mN   = o.chart.peNobs.isEmpty() ? "" : o.chart.peNobs.trim();
            String fitJs = fitStatsJs(o.chart.peModelStats);
            js.append("    { label: '").append(escJs(mLbl)).append("', n: '")
              .append(escJs(mN)).append("', stats: ").append(fitJs).append(", rows: [\n");
            int k = names.length;
            for (int i = 0; i < k; i++) {
                String tv = (i < tzvals.length && !tzvals[i].trim().isEmpty())
                    ? fmtTz(tzvals[i].trim()) : compTstat(coefs[i].trim(), ses[i].trim());
                String pv = pvals[i].trim();
                String st = starsFor(pv);   // v3.6.0-t2h: honour stars()/nostars
                appendRowJs(js, names[i].trim(), coefs[i].trim(), ses[i].trim(),
                    tv, pv, lower[i].trim(), upper[i].trim(), st);
                if (i < k - 1) js.append(",\n"); else js.append("\n");
            }
            js.append("    ] }\n");
        }
        js.append("  ]\n");
        js.append("};\n");
        js.append("</script>\n");
        return js.toString();
    }

    /**
     * v3.6.0-s6b1: Helper to emit one row literal in the __tblData rows array.
     * All numeric values are pre-formatted to match on-screen rendering.
     * Empty / unparseable values become empty strings (no NaN tokens in JS).
     */
    private void appendRowJs(StringBuilder js, String name, String coef, String se,
                             String tz, String p, String lo, String hi, String sig) {
        js.append("      { name: '").append(escJs(name)).append("'")
          .append(", coef: '").append(escJs(fmtNumPlain(coef))).append("'")
          .append(", se: '").append(escJs(fmtNumPlain(se))).append("'")
          .append(", tz: '").append(escJs(tz)).append("'")
          .append(", p: '").append(escJs(fmtPvalPlain(p))).append("'")
          .append(", lo: '").append(escJs(fmtNumPlain(lo))).append("'")
          .append(", hi: '").append(escJs(fmtNumPlain(hi))).append("'")
          .append(", sig: '").append(escJs(sig)).append("' }");
    }

    /**
     * v3.6.0-s6c: Parse a pipe-sep key=value fit-stats string into a JS object literal.
     * Input:  "r2=0.4231|r2_a=0.3987|F=18.42|ll=-245.3"
     * Output: {r2:'0.4231', r2_a:'0.3987', F:'18.42', ll:'-245.3'}
     * Empty or null input returns {}. Malformed pairs are silently skipped.
     * Key chars are alphanumeric + underscore only (safe as JS property names).
     */
    private String fitStatsJs(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (String pair : raw.split("\\|", -1)) {
            int eq = pair.indexOf('=');
            if (eq < 1) continue;
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();
            if (key.isEmpty() || val.isEmpty()) continue;
            // Sanitise key: only alphanumeric and underscore allowed
            if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) continue;
            if (!first) sb.append(", ");
            sb.append(key).append(":'").append(escJs(val)).append("'");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /** v3.6.0-s6b1: Plain-text variant of fmtNum (no HTML escape; for JS string emission). */
    private String fmtNumPlain(String s) {
        try {
            double v = Double.parseDouble(s);
            if (Double.isNaN(v) || Double.isInfinite(v)) return "";
            return String.format(Locale.ROOT, "%.4f", v);
        } catch (NumberFormatException e) {
            return s == null ? "" : s;
        }
    }

    /** v3.6.0-s6b1: Plain-text variant of fmtPval (uses ASCII "<0.001" for export). */
    private String fmtPvalPlain(String s) {
        try {
            double p = Double.parseDouble(s);
            if (Double.isNaN(p) || Double.isInfinite(p)) return "";
            if (p < 0.001) return "<0.001";
            return String.format(Locale.ROOT, "%.3f", p);
        } catch (NumberFormatException e) {
            return s == null ? "" : s;
        }
    }

    /**
     * v3.6.0-t2h (batch 2a): JS array of base/omitted coefficient raw names for
     * __tblData.bases. Single-model coefplot only (matches the classic table,
     * which shows base rows for the single-model case). Empty array otherwise.
     */
    private String jsBasesArray() {
        if (o.chart.peBases == null || o.chart.peBases.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        String[] b = o.chart.peBases.split("\\|", -1);
        boolean first = true;
        for (String name : b) {
            String t = name.trim();
            if (t.isEmpty()) continue;
            if (!first) sb.append(", ");
            first = false;
            sb.append("'").append(escJs(t)).append("'");
        }
        return sb.append("]").toString();
    }

    /**
     * v3.6.0-t2h (batch 2a): JS array of {name, text} heading rows for
     * __tblData.headings. peHeadings format is "rawname Heading text|..." (the
     * same string ChartRenderer parses for the chart), so the pub table's group
     * headings and the chart's headings come from one source. The heading sits
     * ABOVE the row whose raw coefficient name equals `name`.
     */
    private String jsHeadingsArray() {
        if (o.chart.peHeadings == null || o.chart.peHeadings.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        String[] entries = o.chart.peHeadings.split("\\|", -1);
        boolean first = true;
        for (String entry : entries) {
            String e = entry.trim();
            if (e.isEmpty()) continue;
            int sp = e.indexOf(' ');
            if (sp < 0) continue;
            String name = e.substring(0, sp).trim();
            String text = e.substring(sp + 1).trim();
            if (name.isEmpty() || text.isEmpty()) continue;
            if (!first) sb.append(", ");
            first = false;
            sb.append("{name:'").append(escJs(name)).append("', text:'").append(escJs(text)).append("'}");
        }
        return sb.append("]").toString();
    }

    /**
     * v3.6.0: Builds the _spkCoefLabels JS object literal mapping raw coef name
     * to display label. Three-tier priority applied here in Java:
     *   1. Custom label from coeflabels() (peCustomLabels, arg 195)
     *   2. Stata variable label (peVarLabels, arg 194)
     *   3. _cons -> Constant (hardcoded fallback in _spkCoefLabel JS function)
     *   4. Raw name (no entry needed -- JS function returns nm unchanged)
     * Format of peVarLabels / peCustomLabels: "name|||label~~~name|||label"
     * Triple-char sentinels used so that any single char in labels is safe.
     */
    private String buildCoefLabelMapJs() {
        java.util.Map<String,String> varLabels    = parseTripleSepMap(o.chart.peVarLabels);
        java.util.Map<String,String> customLabels = parseTripleSepMap(o.chart.peCustomLabels);
        // Merge: custom takes priority over variable label
        java.util.Map<String,String> resolved = new java.util.LinkedHashMap<>();
        // Start with variable labels
        resolved.putAll(varLabels);
        // Override with custom labels (higher priority)
        resolved.putAll(customLabels);
        // Note: _cons is renamed to "Constant" by sparkta_read_eresults in the ado
        // before being stored in _pe_names. Java never sees "_cons" as a raw name.
        // resolveCoefLabel() handles "Constant" fallback for the chart labels path.
        if (resolved.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String,String> e : resolved.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            sb.append("'").append(escJs(e.getKey())).append("': '")
              .append(escJs(e.getValue())).append("'");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Parses a "name|||label~~~name|||label" string into a LinkedHashMap.
     * Triple-char sentinels (|||, ~~~) are used so any single character
     * in either name or label is safe. Returns empty map for empty input.
     */
    private java.util.Map<String,String> parseTripleSepMap(String raw) {
        java.util.Map<String,String> map = new java.util.LinkedHashMap<>();
        if (raw == null || raw.trim().isEmpty()) return map;
        // Split rows on ~~~
        String[] rows = raw.split("~~~", -1);
        for (String row : rows) {
            // Split on first ||| only
            int sep = row.indexOf("|||");
            if (sep <= 0) continue;
            String name  = row.substring(0, sep).trim();
            String label = row.substring(sep + 3).trim();
            if (!name.isEmpty() && !label.isEmpty()) {
                map.put(name, label);
            }
        }
        return map;
    }

    /**
     * When indicators() and/or addstats() ado options were supplied, pre-populates
     * the arrays so exports include them without the user opening the modal.
     * When both are empty, emits the same || fallback as before (modal-only mode).
     * Format of o.chart.peIndicators / peAddstats:
     *   "label1|v1|v2|v3~label2|v1|v2|v3"
     *   ~ separates rows, first token is the label, remaining are per-model values.
     * The modal reads __tblExtras on open and can tweak/extend ado-supplied values.
     */

    /**
     * v3.6.0-s6c23: Resolves a coefficient name to its display label at Java time.
     * Three-tier priority: custom > variable label > _cons->Constant > raw name.
     * Called by ChartRenderer when building the y-axis labels array so that
     * plain string literals are emitted instead of JS function calls (which would
     * be evaluated before _spkCoefLabel is defined in the page, causing blank charts).
     * Results are cached after the first call.
     */
    String resolveCoefLabel(String name) {
        if (name == null || name.isEmpty()) return name;
        if (_resolvedLabelCache == null) {
            java.util.Map<String,String> varLbls    = parseTripleSepMap(o.chart.peVarLabels);
            java.util.Map<String,String> custLbls   = parseTripleSepMap(o.chart.peCustomLabels);
            _resolvedLabelCache = new java.util.LinkedHashMap<>();
            _resolvedLabelCache.putAll(varLbls);
            _resolvedLabelCache.putAll(custLbls); // custom overrides variable label
        }
        if (_resolvedLabelCache.containsKey(name)) return _resolvedLabelCache.get(name);
        // Note: _cons is renamed to "Constant" in the ado, so it never arrives here as "_cons"
        return name;
    }
    private java.util.Map<String,String> _resolvedLabelCache = null;

    private String buildTblExtrasJs() {
        String indRaw = o.chart.peIndicators;
        String adsRaw = o.chart.peAddstats;
        if (indRaw.isEmpty() && adsRaw.isEmpty()) {
            return "window.__tblExtras = window.__tblExtras || { indicators: [], addstats: [] };\n";
        }
        // Pre-parse peModelStats into per-model stat maps for keyword resolution.
        // peModelStats format: "key=val|key=val~key=val|key=val" (tilde-sep models)
        java.util.List<java.util.Map<String,String>> modelStatMaps = new java.util.ArrayList<>();
        if (!o.chart.peModelStats.isEmpty()) {
            for (String modelBlock : o.chart.peModelStats.split("~", -1)) {
                java.util.Map<String,String> m = new java.util.LinkedHashMap<>();
                for (String kv : modelBlock.split("\\|", -1)) {
                    int eq = kv.indexOf('=');
                    if (eq > 0) m.put(kv.substring(0,eq).trim(), kv.substring(eq+1).trim());
                }
                modelStatMaps.add(m);
            }
        }
        int nModels = modelStatMaps.isEmpty()
            ? (o.chart.peNobs.isEmpty() ? 1 : o.chart.peNobs.split("~",-1).length)
            : modelStatMaps.size();

        StringBuilder sb = new StringBuilder();
        sb.append("window.__tblExtras = {\n");
        // indicators -- always manual rows (label|v1|v2...)
        sb.append("  indicators: [");
        if (!indRaw.isEmpty()) {
            String[] rows = indRaw.split("~", -1);
            for (int i = 0; i < rows.length; i++) {
                String[] parts = rows[i].split("\\|", -1);
                if (parts.length < 1) continue;
                String lbl = escJs(parts[0]);
                sb.append("\n    { label: '").append(lbl).append("', vals: [");
                for (int v = 1; v < parts.length; v++) {
                    if (v > 1) sb.append(", ");
                    sb.append("'").append(escJs(parts[v])).append("'");
                }
                sb.append("] }");
                if (i < rows.length - 1) sb.append(",");
            }
            sb.append("\n  ");
        }
        sb.append("],\n");
        // addstats -- two token types:
        //   __kw__keyword  -> auto-resolved from peModelStats, key = 'auto:statkey'
        //   label|v1|v2... -> manual row, key = 'custom'
        sb.append("  addstats: [");
        if (!adsRaw.isEmpty()) {
            String[] rows = adsRaw.split("~", -1);
            boolean first = true;
            for (String row : rows) {
                if (row.isEmpty()) continue;
                if (!first) sb.append(",");
                first = false;
                if (row.startsWith("__kw__")) {
                    // Keyword: resolve label and per-model values from peModelStats
                    String kw    = row.substring(6); // strip "__kw__"
                    String jsKey = kwToStatKey(kw);  // e.g. "rsq" -> "r2"
                    String lbl   = kwToLabel(kw);    // e.g. "rsq" -> "R-squared"
                    sb.append("\n    { label: '").append(escJs(lbl))
                      .append("', key: 'auto:").append(jsKey).append("', vals: [");
                    for (int m = 0; m < nModels; m++) {
                        if (m > 0) sb.append(", ");
                        String val = "";
                        if (m < modelStatMaps.size()) {
                            val = modelStatMaps.get(m).getOrDefault(jsKey, "");
                        }
                        sb.append("'").append(escJs(val)).append("'");
                    }
                    sb.append("] }");
                } else {
                    // Manual row: label|v1|v2...
                    String[] parts = row.split("\\|", -1);
                    if (parts.length < 1) continue;
                    String lbl = escJs(parts[0]);
                    sb.append("\n    { label: '").append(lbl)
                      .append("', key: 'custom', vals: [");
                    for (int v = 1; v < parts.length; v++) {
                        if (v > 1) sb.append(", ");
                        sb.append("'").append(escJs(parts[v])).append("'");
                    }
                    sb.append("] }");
                }
            }
            sb.append("\n  ");
        }
        sb.append("]\n};\n");
        return sb.toString();
    }

    /** Maps addstats keyword to the peModelStats key (matching FitComputer output). */
    private static String kwToStatKey(String kw) {
        switch (kw) {
            case "rsq":      return "r2";
            case "arsq":     return "r2_a";
            case "fstat":    return "F";
            case "fpval":    return "p";
            case "ll":       return "ll";
            case "chi2":     return "chi2";
            case "rmse":     return "rmse";
            case "dep_mean": return "ymean";
            default:         return kw;
        }
    }

    /** Maps addstats keyword to the display label shown in table rows. */
    private static String kwToLabel(String kw) {
        switch (kw) {
            case "rsq":      return "R-squared";
            case "arsq":     return "Adjusted R-squared";
            case "fstat":    return "F-statistic";
            case "fpval":    return "F p-value";
            case "ll":       return "Log-likelihood";
            case "chi2":     return "Chi-squared";
            case "rmse":     return "RMSE";
            case "dep_mean": return "Mean of dep. var.";
            default:         return kw;
        }
    }

    /**
     * v3.6.0-s6b1: Emits the runtime JS for table export.
     * Defines:
     *   window.__tblExtras = { indicators: [{label, vals[]}], addstats: [{label, vals[]}] }
     *   _spkTblExport(fmt)   -- 'copy' | 'csv' | 'latex'
     *   _spkTblCustomize()   -- opens indicator/addstats modal
     * Session memory only (no localStorage). Modal is built on demand and torn down on close.
     * All four formats include indicator rows and addstats rows when populated.
     */
    private String buildTblExportJs() {
        if (!o.isPostEstimation()) return "";
        if (o.chart.peNames.isEmpty()) return "";
        boolean dark = isDark();
        String modalBg   = dark ? "#1a1a2e" : "#ffffff";
        String modalBd   = dark ? "#44447a" : "#c8c8c8";
        String modalTxt  = dark ? "#e0e0e0" : "#333333";
        String inputBg   = dark ? "#2a2a4a" : "#f4f4f4";
        String inputBd   = dark ? "#44447a" : "#c8c8c8";
        String btnBg     = dark ? "#2a2a4a" : "#f4f4f4";
        String btnHover  = dark ? "#3a3a6a" : "#e0e0e0";
        String btnColor  = dark ? "#d0d0e8" : "#444444";   // v3.6.0-s6c2: needed for segmented control
        String btnBorder = dark ? "#44447a" : "#c8c8c8";   // v3.6.0-s6c2: needed for segmented control
        String overlayBg = dark ? "rgba(0,0,0,0.65)" : "rgba(0,0,0,0.45)";

        StringBuilder js = new StringBuilder();
        js.append("\n/* v3.6.0-s6b1: Post-estimation table export */\n");
        // v3.6.0-s6c: pre-populate __tblExtras from ado-supplied indicators()/addstats().
        // When both are empty, falls back to empty arrays (modal-only mode, same as before).
        // When ado args are present, the modal seeds from them and user can still tweak.
        js.append(buildTblExtrasJs());
        // v3.6.0-s6c: stat key -> display label mapping (top-level so available everywhere)
        js.append("var _statsLabels = {\n")
          .append("  r2: 'R-squared', r2_a: 'Adjusted R-squared', r2_p: 'Pseudo R-squared',\n")
          .append("  F: 'F-statistic', p: 'F p-value', ll: 'Log-likelihood',\n")
          .append("  chi2: 'Chi-squared', rmse: 'RMSE',\n")
          .append("  ymean: 'Mean of dep. var.'\n")
          .append("};\n");

        // v3.6.0-s6b2 Fix 1: _spkLatexEsc with < and > escaped LAST (after $),
        // so the $<$ and $>$ substitutions are not themselves dollar-escaped.
        // Replacement order is critical:
        //   1. backslash first (avoid double-escaping)
        //   2. & % $ # _ { } ~ ^ (LaTeX specials -- no < > yet)
        //   3. < > converted to math-mode $<$ $>$ as the very last step,
        //      after all $ signs from user input have already been escaped to \$,
        //      so these new $ signs are safe and will not be re-escaped.
        js.append("function _spkLatexEsc(s){\n")
          .append("  if(s==null) return '';\n")
          .append("  s = String(s);\n")
          .append("  s = s.replace(/\\\\/g,'\\\\textbackslash{}');\n")
          .append("  s = s.replace(/&/g,'\\\\&').replace(/%/g,'\\\\%').replace(/\\$/g,'\\\\$');\n")
          .append("  s = s.replace(/#/g,'\\\\#').replace(/_/g,'\\\\_');\n")
          .append("  s = s.replace(/\\{/g,'\\\\{').replace(/\\}/g,'\\\\}');\n")
          .append("  s = s.replace(/~/g,'\\\\textasciitilde{}').replace(/\\^/g,'\\\\textasciicircum{}');\n")
          // < and > LAST -- the $ inserted here won't be re-escaped
          .append("  s = s.replace(/</g,'$<$').replace(/>/g,'$>$');\n")
          .append("  return s;\n")
          .append("}\n");
        js.append("function _spkCsvEsc(s){\n")
          .append("  if(s==null) return '';\n")
          .append("  s = String(s);\n")
          // v3.6.0-s6c9: Formula injection protection.
          // Excel treats CSV cells starting with - + = @ as formulas even when quoted.
          // Prefix with a space to force text treatment. The space is preserved inside
          // a quoted field (RFC 4180) and is invisible in Excel text-aligned cells.
          // Applies to negative coefficients: '-5853.696*\n(3376.987)' -> ' -5853.696*...'
          .append("  if(s.charAt(0)==='-'||s.charAt(0)==='+'||s.charAt(0)==='='||s.charAt(0)==='@'){\n")
          .append("    s = ' '+s;\n")
          .append("  }\n")
          .append("  if(/[,\"\\n\\r]/.test(s)) return '\"'+s.replace(/\"/g,'\"\"')+'\"';\n")
          .append("  return s;\n")
          .append("}\n");
        js.append("function _spkLatexStar(s){\n")
          .append("  if(!s) return '';\n")
          .append("  return '\\\\sym{'+s+'}';\n")
          .append("}\n");

        js.append("function _spkTblDownload(text, fname, mime){\n")
          .append("  var blob = new Blob([text], {type: mime});\n")
          .append("  var a = document.createElement('a');\n")
          .append("  a.href = URL.createObjectURL(blob);\n")
          .append("  a.download = fname;\n")
          .append("  document.body.appendChild(a); a.click(); document.body.removeChild(a);\n")
          .append("  setTimeout(function(){ URL.revokeObjectURL(a.href); }, 60000);\n")
          .append("}\n");

        // v3.6.0-s6c6: _spkBuildFlat -- esttab-style CSV/TSV with combined coef+SE cell.
        // Column headers: plain 1, 2, 3 (Excel-safe, no parens needed).
        // Coef cell: "3.4647*** (0.6307)" -- coef+stars+SE combined, always one row per coef.
        //   Excel-safe: cell starts with digit+stars+space, parsed as text not number.
        //   Blank when coef absent from that model.
        // Compact mode: one row per coef (coef+SE combined).
        // Full mode: coef+SE combined row, then separate rows for [t], {p}, [CI lo CI hi].
        // Label row: shown only when at least one model has a non-empty label.
        // LaTeX (_spkBuildLatex) is unaffected -- still uses separate (SE) row.
        js.append("function _spkBuildFlat(sep){\n")
          .append("  var d = window.__tblData; if(!d) return '';\n")
          .append("  var ex = window.__tblExtras || {indicators:[], addstats:[]};\n")
          .append("  var full = !!window.__tblLatexFull;\n")
          .append("  var nM = d.models.length;\n")
          .append("  var esc = (sep===',') ? _spkCsvEsc\n")
          // SEC2 (t2j): the TSV path (Copy button, sep='\\t') gets the SAME formula-
          // injection guard as CSV -- a cell pasted into Excel starting with - + = @ is
          // treated as a formula even from a clipboard TSV. Prefix such cells with a
          // space to force text, then flatten tabs/newlines to spaces.
          .append("    : function(s){ if(s==null) return ''; s=String(s);\n")
          .append("        if(s.charAt(0)==='-'||s.charAt(0)==='+'||s.charAt(0)==='='||s.charAt(0)==='@') s=' '+s;\n")
          .append("        return s.replace(/[\\t\\n\\r]/g,' '); };\n")
          // Build coefficient union in first-appearance order (same as LaTeX)
          .append("  var coefOrder = []; var coefSeen = {};\n")
          .append("  for(var m=0; m<nM; m++){\n")
          .append("    var rows = d.models[m].rows;\n")
          .append("    for(var i=0; i<rows.length; i++){\n")
          .append("      var nm = rows[i].name;\n")
          .append("      if(!coefSeen[nm]){ coefSeen[nm]=true; coefOrder.push(nm); }\n")
          .append("    }\n")
          .append("  }\n")
          // Per-model name->row lookup
          .append("  var lookup = [];\n")
          .append("  for(var mm=0; mm<nM; mm++){\n")
          .append("    var map = {}; var rs = d.models[mm].rows;\n")
          .append("    for(var j=0; j<rs.length; j++) map[rs[j].name] = rs[j];\n")
          .append("    lookup.push(map);\n")
          .append("  }\n")
          .append("  var L = [];\n")
          // Header row: plain 1 2 3 (no parens -- Excel-safe)
          .append("  var hdr1 = esc('');\n")
          .append("  for(var c=0; c<nM; c++) hdr1 += sep + esc(String(c+1));\n")
          .append("  L.push(hdr1);\n")
          // Label row: only when at least one model has a label
          .append("  var anyLbl = false;\n")
          .append("  for(var c2=0; c2<nM; c2++){ if(d.models[c2].label) anyLbl=true; }\n")
          .append("  if(anyLbl){\n")
          .append("    var hdr2 = esc('');\n")
          .append("    for(var c3=0; c3<nM; c3++) hdr2 += sep + esc(d.models[c3].label||'');\n")
          .append("    L.push(hdr2);\n")
          .append("  }\n")
          // Coefficient block: one combined coef+SE row per coefficient
          .append("  for(var ci=0; ci<coefOrder.length; ci++){\n")
          .append("    var nm2 = _spkCoefLabel(coefOrder[ci]);\n")
          // Combined coef+SE cell: "3.4647*** (0.6307)" or blank
          // Starts with digit+stars so Excel treats as text, not number
          .append("    var r1 = esc(nm2);\n")
          .append("    for(var mc=0; mc<nM; mc++){\n")
          .append("      var rr = lookup[mc][coefOrder[ci]];\n")
          .append("      if(rr){\n")
          // v3.6.0-s6c7: CSV uses newline within cell (RFC 4180 quoted field);
          // TSV uses space -- clipboard paste with embedded newlines is unreliable.
          .append("        var nl = (sep===',') ? '\\n' : ' ';\n")
          .append("        var cell = rr.coef + (rr.sig||'') + (rr.se ? nl+'('+rr.se+')' : '');\n")
          .append("        r1 += sep + esc(cell);\n")
          .append("      } else {\n")
          .append("        r1 += sep + esc('');\n")
          .append("      }\n")
          .append("    }\n")
          .append("    L.push(r1);\n")
          // Full mode: additional rows for t, p, CI (compact skips these)
          .append("    if(full){\n")
          // [t/z] row
          .append("      var r3 = esc('');\n")
          .append("      for(var mz=0; mz<nM; mz++){\n")
          .append("        var rr3 = lookup[mz][coefOrder[ci]];\n")
          .append("        r3 += sep + esc(rr3 && rr3.tz ? '['+rr3.tz+']' : '');\n")
          .append("      }\n")
          .append("      L.push(r3);\n")
          // {p} row
          .append("      var r4 = esc('');\n")
          .append("      for(var mp2=0; mp2<nM; mp2++){\n")
          .append("        var rr4 = lookup[mp2][coefOrder[ci]];\n")
          .append("        r4 += sep + esc(rr4 && rr4.p ? '{'+rr4.p+'}' : '');\n")
          .append("      }\n")
          .append("      L.push(r4);\n")
          // [CI lo CI hi] row
          .append("      var r5 = esc('');\n")
          .append("      for(var mq=0; mq<nM; mq++){\n")
          .append("        var rr5 = lookup[mq][coefOrder[ci]];\n")
          .append("        r5 += sep + esc(rr5 && rr5.lo && rr5.hi ? '['+rr5.lo+' '+rr5.hi+']' : '');\n")
          .append("      }\n")
          .append("      L.push(r5);\n")
          .append("    }\n")
          .append("  }\n")
          // Indicator block (between coefs and N)
          .append("  if(ex.indicators && ex.indicators.length){\n")
          .append("    for(var k=0; k<ex.indicators.length; k++){\n")
          .append("      var ind = ex.indicators[k];\n")
          .append("      var ir = esc(ind.label||'');\n")
          .append("      for(var iv=0; iv<nM; iv++) ir += sep + esc(ind.vals[iv]||'');\n")
          .append("      L.push(ir);\n")
          .append("    }\n")
          .append("  }\n")
          // N row
          .append("  var nrow = esc('N');\n")
          .append("  for(var nn=0; nn<nM; nn++) nrow += sep + esc(d.models[nn].n||'');\n")
          .append("  L.push(nrow);\n")
          // Addstats rows
          .append("  if(ex.addstats && ex.addstats.length){\n")
          .append("    for(var a=0; a<ex.addstats.length; a++){\n")
          .append("      var ad = ex.addstats[a];\n")
          .append("      var ar = esc(ad.label||'');\n")
          .append("      for(var av=0; av<nM; av++) ar += sep + esc(ad.vals[av]||'');\n")
          .append("      L.push(ar);\n")
          .append("    }\n")
          .append("  }\n")
          // Legend -- T1 (t2j): reflect stars()/nostars (was hardcoded 10/5/1). The
          // plain-text legend from __tblData uses "p<X"; skip entirely under nostars.
          .append("  if(d.starLegend) L.push(esc('p-value: ' + d.starLegend));\n")
          .append("  return L.join('\\n') + '\\n';\n")
          .append("}\n");

        // v3.6.0-s6b2 Fix 2: compact (default) vs full layout toggle.
        // window.__tblLatexFull = false  -> compact: coef+stars row + (SE) row (2 rows per coef)
        // window.__tblLatexFull = true   -> full: 5 rows per coef (coef, SE, z/t, p, CI)
        // v3.6.0: Bake resolved coefficient display labels into the page.
        // Three-tier priority (applied in Java): custom > variable label > raw name.
        // _cons -> Constant is always applied unless custom label overrides it.
        // _spkCoefLabels object maps raw name -> display label for all names
        // that have a non-trivial label (custom, variable label, or Constant).
        // Names with no entry fall through to the raw name.
        js.append("var _spkCoefLabels = ").append(buildCoefLabelMapJs()).append(";\n");
        js.append("window.__tblLatexFull = false;\n");
        js.append("function _spkCoefLabel(nm){\n")
          .append("  if(_spkCoefLabels[nm] !== undefined) return _spkCoefLabels[nm];\n")
          // Note: _cons is renamed to Constant by ado before Java, so nm is never '_cons'
          .append("  return nm;\n")
          .append("}\n");
        js.append("function _spkBuildLatex(){\n")
          .append("  var d = window.__tblData; if(!d) return '';\n")
          .append("  var ex = window.__tblExtras || {indicators:[], addstats:[]};\n")
          .append("  var full = !!window.__tblLatexFull;\n")
          .append("  var nM = d.models.length;\n")
          .append("  var coefOrder = []; var coefSeen = {};\n")
          .append("  for(var m=0; m<nM; m++){\n")
          .append("    var rows = d.models[m].rows;\n")
          .append("    for(var i=0; i<rows.length; i++){\n")
          .append("      var nm = rows[i].name;\n")
          .append("      if(!coefSeen[nm]){ coefSeen[nm]=true; coefOrder.push(nm); }\n")
          .append("    }\n")
          .append("  }\n")
          .append("  var lookup = []; for(var mm=0; mm<nM; mm++){\n")
          .append("    var map = {}; var rs = d.models[mm].rows;\n")
          .append("    for(var j=0; j<rs.length; j++) map[rs[j].name] = rs[j];\n")
          .append("    lookup.push(map);\n")
          .append("  }\n")
          .append("  var L = [];\n")
          .append("  L.push('\\\\begin{table}[htbp]\\\\centering');\n")
          .append("  L.push('\\\\caption{Regression results}');\n")
          .append("  L.push('\\\\begin{tabular}{l*{'+nM+'}{c}}');\n")
          .append("  L.push('\\\\toprule');\n")
          // Column numbers (1) (2) ...
          .append("  var hdr1 = ''; for(var c=0; c<nM; c++) hdr1 += ' & ('+(c+1)+')';\n")
          .append("  L.push(hdr1 + ' \\\\\\\\');\n")
          // Model labels (blank row if single model with no label)
          .append("  var hdr2 = ''; var anyLbl = false;\n")
          .append("  for(var c2=0; c2<nM; c2++){\n")
          .append("    var lbl = d.models[c2].label || '';\n")
          .append("    if(lbl) anyLbl = true;\n")
          .append("    hdr2 += ' & ' + _spkLatexEsc(lbl);\n")
          .append("  }\n")
          // Only emit the label row when at least one model has a label
          .append("  if(anyLbl) L.push(hdr2 + ' \\\\\\\\');\n")
          .append("  L.push('\\\\midrule');\n")
          // Coefficient block
          .append("  for(var ci=0; ci<coefOrder.length; ci++){\n")
          .append("    var nm2 = coefOrder[ci];\n")
          // Fix 3: rename _cons, then LaTeX-escape
          .append("    var dispNm = _spkLatexEsc(_spkCoefLabel(nm2));\n")
          // Row 1: coef + stars (both compact and full)
          .append("    var r1 = dispNm;\n")
          .append("    for(var mc=0; mc<nM; mc++){\n")
          .append("      var rr = lookup[mc][nm2];\n")
          .append("      r1 += ' & ' + (rr ? (rr.coef + _spkLatexStar(rr.sig)) : '');\n")
          .append("    }\n")
          .append("    L.push(r1 + ' \\\\\\\\');\n")
          // Row 2: (SE) -- both compact and full
          .append("    var r2 = '';\n")
          .append("    for(var ms=0; ms<nM; ms++){\n")
          .append("      var rr2 = lookup[ms][nm2];\n")
          .append("      r2 += ' & ' + (rr2 && rr2.se ? '('+rr2.se+')' : '');\n")
          .append("    }\n")
          .append("    L.push(r2 + ' \\\\\\\\');\n")
          // Rows 3-5: z/t, p, CI -- full mode only
          .append("    if(full){\n")
          // Row 3: [z/t]
          .append("      var r3 = '';\n")
          .append("      for(var mz=0; mz<nM; mz++){\n")
          .append("        var rr3 = lookup[mz][nm2];\n")
          .append("        r3 += ' & ' + (rr3 && rr3.tz ? '['+rr3.tz+']' : '');\n")
          .append("      }\n")
          .append("      L.push(r3 + ' \\\\\\\\');\n")
          // Row 4: {p} -- use _spkLatexEsc on p so <0.001 -> $<$0.001 inside \{ \}
          .append("      var r4 = '';\n")
          .append("      for(var mp2=0; mp2<nM; mp2++){\n")
          .append("        var rr4 = lookup[mp2][nm2];\n")
          .append("        r4 += ' & ' + (rr4 && rr4.p ? '\\\\{'+_spkLatexEsc(rr4.p)+'\\\\}' : '');\n")
          .append("      }\n")
          .append("      L.push(r4 + ' \\\\\\\\');\n")
          // Row 5: [CI lo, CI hi]
          .append("      var r5 = '';\n")
          .append("      for(var mq=0; mq<nM; mq++){\n")
          .append("        var rr5 = lookup[mq][nm2];\n")
          .append("        r5 += ' & ' + (rr5 && rr5.lo && rr5.hi ? '['+rr5.lo+', '+rr5.hi+']' : '');\n")
          .append("      }\n")
          .append("      L.push(r5 + ' \\\\\\\\');\n")
          .append("    }\n")
          .append("  }\n")
          // Indicator block (between coefs and N)
          .append("  if(ex.indicators && ex.indicators.length){\n")
          .append("    L.push('\\\\midrule');\n")
          .append("    for(var ii=0; ii<ex.indicators.length; ii++){\n")
          .append("      var ind = ex.indicators[ii];\n")
          .append("      var ir = _spkLatexEsc(ind.label);\n")
          .append("      for(var iv=0; iv<nM; iv++){\n")
          .append("        ir += ' & ' + _spkLatexEsc(ind.vals[iv]||'');\n")
          .append("      }\n")
          .append("      L.push(ir + ' \\\\\\\\');\n")
          .append("    }\n")
          .append("  }\n")
          // N row
          .append("  L.push('\\\\midrule');\n")
          .append("  var nrow = 'N';\n")
          .append("  for(var nn=0; nn<nM; nn++) nrow += ' & ' + _spkLatexEsc(d.models[nn].n||'');\n")
          .append("  L.push(nrow + ' \\\\\\\\');\n")
          // Addstats rows (after N)
          .append("  if(ex.addstats && ex.addstats.length){\n")
          .append("    for(var aa=0; aa<ex.addstats.length; aa++){\n")
          .append("      var ad = ex.addstats[aa];\n")
          .append("      var ar = _spkLatexEsc(ad.label);\n")
          .append("      for(var av=0; av<nM; av++) ar += ' & ' + _spkLatexEsc(ad.vals[av]||'');\n")
          .append("      L.push(ar + ' \\\\\\\\');\n")
          .append("    }\n")
          .append("  }\n")
          .append("  L.push('\\\\bottomrule');\n")
          .append("  L.push('\\\\end{tabular}');\n")
          // tablenotes: legend adapts to compact vs full
          // Fix 1: star thresholds use \textless{} via _spkLatexEsc path --
          // but these are hardcoded strings so escape < directly here as $<$
          .append("  L.push('\\\\begin{tablenotes}\\\\small');\n")
          .append("  if(full){\n")
          .append("    L.push('\\\\item Coefficient (stars), (SE), [' + d.statLabel + '], \\\\{p\\\\}, [CI lo, CI hi]');\n")
          .append("  }\n")
          // T1 (t2j): p-value note reflects stars()/nostars (was hardcoded 10/5/1);
          // starLegendLatex is empty under nostars, so the note is skipped then.
          .append("  if(d.starLegendLatex) L.push('\\\\item p-value: ' + d.starLegendLatex);\n")
          .append("  L.push('\\\\end{tablenotes}');\n")
          .append("  L.push('\\\\end{table}');\n")
          .append("  return L.join('\\n') + '\\n';\n")
          .append("}\n");

        js.append("function _spkTblExport(fmt){\n")
          .append("  if(!window.__tblData){ console.warn('_spkTblExport: no __tblData'); return; }\n")
          .append("  if(fmt==='copy'){\n")
          .append("    var tsv = _spkBuildFlat('\\t');\n")
          .append("    var btn = document.getElementById('_tblBtnCopy');\n")
          .append("    var origText = btn ? btn.textContent : null;\n")
          .append("    var done = function(ok){\n")
          .append("      if(!btn) return;\n")
          .append("      btn.textContent = ok ? 'Copied!' : 'Copy failed';\n")
          .append("      setTimeout(function(){ btn.textContent = origText || 'Copy'; }, 1200);\n")
          .append("    };\n")
          .append("    if(navigator.clipboard && navigator.clipboard.writeText){\n")
          .append("      navigator.clipboard.writeText(tsv).then(function(){ done(true); }, function(){ _spkCopyFallback(tsv, done); });\n")
          .append("    } else { _spkCopyFallback(tsv, done); }\n")
          .append("  } else if(fmt==='csv'){\n")
          .append("    var csv = _spkBuildFlat(',');\n")
          .append("    _spkTblDownload(csv, 'coefficients.csv', 'text/csv;charset=utf-8');\n")
          .append("  } else if(fmt==='latex'){\n")
          .append("    var tex = _spkBuildLatex();\n")
          .append("    _spkTblDownload(tex, 'coefficients.tex', 'text/x-tex;charset=utf-8');\n")
          .append("  }\n")
          .append("}\n");
        js.append("function _spkCopyFallback(text, cb){\n")
          .append("  try{\n")
          .append("    var ta = document.createElement('textarea');\n")
          .append("    ta.value = text; ta.style.position='fixed'; ta.style.left='-9999px';\n")
          .append("    document.body.appendChild(ta); ta.select();\n")
          .append("    var ok = document.execCommand('copy');\n")
          .append("    document.body.removeChild(ta);\n")
          .append("    cb(ok);\n")
          .append("  } catch(e){ cb(false); }\n")
          .append("}\n");
        // v3.6.0-s6c2: _spkSetLatexMode(full) -- sets mode and updates segmented control.
        js.append("function _spkSetLatexMode(full){\n")
          .append("  window.__tblLatexFull = !!full;\n")
          .append("  var bc = document.getElementById('_tblBtnCompact');\n")
          .append("  var bf = document.getElementById('_tblBtnFull');\n")
          .append("  if(!bc || !bf) return;\n")
          .append("  var actBg='").append(dark ? "#3a3a6a" : "#d0d8e8").append("';\n")
          .append("  var actBd='").append(dark ? "#6a6aaa" : "#7090b0").append("';\n")
          .append("  var inaBg='").append(btnBg).append("';\n")
          .append("  var inaBd='").append(btnBorder).append("';\n")
          .append("  var inaTxt='").append(dark ? "#888899" : "#999999").append("';\n")
          .append("  var actTxt='").append(btnColor).append("';\n")
          .append("  var base='cursor:pointer;font-family:inherit;padding:2px 8px;font-size:10px;';\n")
          // Compact button (left of pair, rounded left corners)
          .append("  bc.style.cssText = base + 'border-radius:3px 0 0 3px;'\n")
          .append("    + (full ? 'background:'+inaBg+';color:'+inaTxt+';border:1px solid '+inaBd+';font-weight:400;'\n")
          .append("            : 'background:'+actBg+';color:'+actTxt+';border:1px solid '+actBd+';font-weight:600;');\n")
          // Full button (right of pair, rounded right corners, no left border to look joined)
          .append("  bf.style.cssText = base + 'border-radius:0 3px 3px 0;border-left:none;'\n")
          .append("    + (full ? 'background:'+actBg+';color:'+actTxt+';border:1px solid '+actBd+';font-weight:600;'\n")
          .append("            : 'background:'+inaBg+';color:'+inaTxt+';border:1px solid '+inaBd+';font-weight:400;');\n")
          .append("}\n");

        js.append("function _spkTblCustomize(){\n")
          .append("  var d = window.__tblData; if(!d) return;\n")
          .append("  var nM = d.models.length;\n")
          .append("  var ex = window.__tblExtras;\n")
          .append("  var ov = document.createElement('div');\n")
          .append("  ov.id = '_tblModalOv';\n")
          .append("  ov.style.cssText = 'position:fixed;inset:0;background:").append(overlayBg)
          .append(";z-index:9999;display:flex;align-items:flex-start;justify-content:center;padding:5vh 1rem;overflow:auto;';\n")
          .append("  var mb = document.createElement('div');\n")
          .append("  mb.style.cssText = 'background:").append(modalBg)
          .append(";color:").append(modalTxt)
          .append(";border:1px solid ").append(modalBd)
          .append(";border-radius:8px;padding:1.2rem 1.4rem;max-width:720px;width:100%;box-shadow:0 8px 32px rgba(0,0,0,0.4);font-family:inherit;font-size:.85rem;';\n")
          .append("  ov.appendChild(mb);\n")
          .append("  var h = document.createElement('div');\n")
          .append("  h.style.cssText = 'display:flex;justify-content:space-between;align-items:center;margin-bottom:.8rem;';\n")
          .append("  h.innerHTML = '<h3 style=\"margin:0;font-size:1rem;\">Customize Export</h3>';\n")
          .append("  var closeBtn = document.createElement('button');\n")
          .append("  closeBtn.textContent = 'Close';\n")
          .append("  closeBtn.style.cssText = 'background:").append(btnBg)
          .append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd)
          .append(";border-radius:3px;padding:3px 12px;font-size:11px;cursor:pointer;';\n")
          .append("  closeBtn.onclick = function(){ document.body.removeChild(ov); };\n")
          .append("  h.appendChild(closeBtn); mb.appendChild(h);\n")
          .append("  var help = document.createElement('p');\n")
          .append("  help.style.cssText = 'margin:0 0 .8rem 0;font-size:.78rem;opacity:.75;';\n")
          .append("  help.textContent = 'Add indicator rows (e.g., Country FE: Yes/No per model) and additional statistics (e.g., R-squared). Values apply to ' + nM + ' model column(s). Session memory only - cleared on page reload.';\n")
          .append("  mb.appendChild(help);\n")
          .append("  function makeSection(title, key, hint){\n")
          .append("    var sec = document.createElement('div');\n")
          .append("    sec.style.cssText = 'margin-bottom:1rem;';\n")
          .append("    var st = document.createElement('div');\n")
          .append("    st.style.cssText = 'font-weight:600;margin-bottom:.2rem;font-size:.82rem;';\n")
          .append("    st.textContent = title; sec.appendChild(st);\n")
          .append("    var ht = document.createElement('div');\n")
          .append("    ht.style.cssText = 'font-size:.73rem;opacity:.6;margin-bottom:.4rem;';\n")
          .append("    ht.textContent = hint; sec.appendChild(ht);\n")
          .append("    var hdrRow = document.createElement('div');\n")
          .append("    hdrRow.style.cssText = 'display:flex;gap:5px;margin-bottom:3px;align-items:center;';\n")
          .append("    var hdrLbl = document.createElement('span');\n")
          .append("    hdrLbl.textContent = 'Label';\n")
          .append("    hdrLbl.style.cssText = 'flex:0 0 140px;font-size:.70rem;font-weight:600;opacity:.55;padding-left:6px;';\n")
          .append("    hdrRow.appendChild(hdrLbl);\n")
          .append("    for(var hv=0; hv<nM; hv++){\n")
          .append("      var hdrVal = document.createElement('span');\n")
          .append("      var mHdr = d.models[hv].label ? '('+(hv+1)+') '+d.models[hv].label : '('+(hv+1)+')';\n")
          .append("      if(mHdr.length > 14) mHdr = mHdr.substring(0,13)+'...';\n")
          .append("      hdrVal.textContent = mHdr;\n")
          .append("      hdrVal.style.cssText = 'flex:1 1 60px;min-width:50px;font-size:.70rem;font-weight:600;opacity:.55;text-align:center;';\n")
          .append("      hdrRow.appendChild(hdrVal);\n")
          .append("    }\n")
          .append("    var hdrSp = document.createElement('span');\n")
          .append("    hdrSp.style.cssText = 'flex:0 0 28px;';\n")
          .append("    hdrRow.appendChild(hdrSp);\n")
          .append("    sec.appendChild(hdrRow);\n")
          .append("    var rowsWrap = document.createElement('div');\n")
          .append("    sec.appendChild(rowsWrap);\n")
          .append("    function rebuild(){\n")
          .append("      rowsWrap.innerHTML = '';\n")
          .append("      var arr = ex[key];\n")
          .append("      for(var i=0; i<arr.length; i++){ rowsWrap.appendChild(makeRow(arr[i], i)); }\n")
          .append("    }\n")
          .append("    function makeRow(item, idx){\n")
          .append("      var row = document.createElement('div');\n")
          .append("      row.style.cssText = 'display:flex;gap:5px;margin-bottom:5px;align-items:center;flex-wrap:wrap;';\n")
          .append("      var lblIn = document.createElement('input');\n")
          .append("      lblIn.value = item.label||''; lblIn.placeholder='e.g. Country FE';\n")
          .append("      lblIn.style.cssText = 'flex:0 0 140px;background:").append(inputBg).append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd).append(";border-radius:3px;padding:4px 6px;font-size:.78rem;font-family:inherit;';\n")
          .append("      lblIn.oninput = function(){ item.label = lblIn.value; };\n")
          .append("      row.appendChild(lblIn);\n")
          .append("      for(var v=0; v<nM; v++){\n")
          .append("        (function(vv){\n")
          .append("          var vIn = document.createElement('input');\n")
          .append("          vIn.value = item.vals[vv]||'';\n")
          .append("          vIn.placeholder = (key==='indicators') ? 'Yes / No' : '0.00';\n")
          .append("          vIn.style.cssText = 'flex:1 1 60px;min-width:50px;background:").append(inputBg).append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd).append(";border-radius:3px;padding:4px 6px;font-size:.78rem;font-family:inherit;text-align:center;';\n")
          .append("          vIn.oninput = function(){ item.vals[vv] = vIn.value; };\n")
          .append("          row.appendChild(vIn);\n")
          .append("        })(v);\n")
          .append("      }\n")
          .append("      var del = document.createElement('button');\n")
          .append("      del.textContent = 'X'; del.title = 'Remove row';\n")
          .append("      del.style.cssText = 'flex:0 0 28px;background:").append(btnBg).append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd).append(";border-radius:3px;padding:3px 4px;font-size:11px;cursor:pointer;';\n")
          .append("      del.onclick = function(){ ex[key].splice(idx,1); rebuild(); };\n")
          .append("      row.appendChild(del);\n")
          .append("      return row;\n")
          .append("    }\n")
          .append("    var add = document.createElement('button');\n")
          .append("    add.textContent = '+ Add row';\n")
          .append("    add.style.cssText = 'background:").append(btnBg).append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd).append(";border-radius:3px;padding:4px 12px;font-size:.78rem;cursor:pointer;margin-top:4px;';\n")
          .append("    add.onmouseover = function(){ this.style.background='").append(btnHover).append("'; };\n")
          .append("    add.onmouseout  = function(){ this.style.background='").append(btnBg).append("'; };\n")
          .append("    add.onclick = function(){\n")
          .append("      var vals = []; for(var k=0; k<nM; k++) vals.push('');\n")
          .append("      ex[key].push({ label: '', vals: vals }); rebuild();\n")
          .append("    };\n")
          .append("    sec.appendChild(add);\n")
          .append("    rebuild();\n")
          .append("    return sec;\n")
          .append("  }\n")
          .append("  mb.appendChild(makeSection('Indicator rows (between coefs and N)', 'indicators',\n")
          .append("    'Label (e.g. Country FE) then mark each model: Yes or No.'));\n")
          .append("  mb.appendChild(makeAddstatsSection());\n")
          .append("  function makeAddstatsSection(){\n")
          .append("    var sec = document.createElement('div');\n")
          .append("    sec.style.cssText = 'margin-bottom:1rem;';\n")
          .append("    var st = document.createElement('div');\n")
          .append("    st.style.cssText = 'font-weight:600;margin-bottom:.2rem;font-size:.82rem;';\n")
          .append("    st.textContent = 'Additional statistics (after N)'; sec.appendChild(st);\n")
          .append("    var ht = document.createElement('div');\n")
          .append("    ht.style.cssText = 'font-size:.73rem;opacity:.6;margin-bottom:.4rem;';\n")
          .append("    ht.textContent = 'Select a statistic -- values are filled automatically from your estimation results.'; sec.appendChild(ht);\n")
          .append("    var rowsWrap = document.createElement('div');\n")
          .append("    sec.appendChild(rowsWrap);\n")
          .append("    function rebuildStats(){\n")
          .append("      rowsWrap.innerHTML = '';\n")
          .append("      for(var i=0; i<ex.addstats.length; i++) rowsWrap.appendChild(makeStatRow(ex.addstats[i], i));\n")
          .append("    }\n")
          .append("    function makeStatRow(item, idx){\n")
          .append("      var row = document.createElement('div');\n")
          .append("      row.style.cssText = 'display:flex;gap:5px;margin-bottom:6px;align-items:center;';\n")
          .append("      var lblIn = document.createElement('input');\n")
          .append("      lblIn.value = item.label||''; lblIn.placeholder='Label (e.g. R-squared)';\n")
          .append("      lblIn.style.cssText = 'flex:0 0 170px;background:").append(inputBg).append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd).append(";border-radius:3px;padding:4px 6px;font-size:.78rem;font-family:inherit;';\n")
          .append("      lblIn.oninput = function(){ item.label = lblIn.value; };\n")
          .append("      row.appendChild(lblIn);\n")
          .append("      var sel = document.createElement('select');\n")
          .append("      sel.style.cssText = 'flex:1 1 180px;background:").append(inputBg).append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd).append(";border-radius:3px;padding:4px 6px;font-size:.78rem;font-family:inherit;cursor:pointer;';\n")
          .append("      var opts = [];\n")
          .append("      for(var sk in _statsLabels){\n")
          .append("        var avail = false;\n")
          .append("        for(var mm=0; mm<d.models.length; mm++){\n")
          .append("          if(d.models[mm].stats && d.models[mm].stats[sk] !== undefined) avail = true;\n")
          .append("        }\n")
          .append("        if(avail) opts.push({value: 'auto:'+sk, label: _statsLabels[sk]});\n")
          .append("      }\n")
          .append("      opts.push({value: 'custom', label: '-- Enter manually...' });\n")
          .append("      opts.forEach(function(op){\n")
          .append("        var o2 = document.createElement('option');\n")
          .append("        o2.value = op.value; o2.textContent = op.label;\n")
          .append("        if(item.key === op.value) o2.selected = true;\n")
          .append("        sel.appendChild(o2);\n")
          .append("      });\n")
          .append("      var valWrap = document.createElement('div');\n")
          .append("      valWrap.style.cssText = 'flex:1 1 140px;font-size:.75rem;color:").append(modalTxt).append(";opacity:.8;align-self:center;padding-left:4px;';\n")
          .append("      function refreshValDisplay(){\n")
          .append("        valWrap.innerHTML = '';\n")
          .append("        var k = sel.value;\n")
          .append("        if(k.indexOf('auto:') === 0){\n")
          .append("          var sk = k.slice(5);\n")
          .append("          var parts = [];\n")
          .append("          for(var mm=0; mm<d.models.length; mm++){\n")
          .append("            var v = (d.models[mm].stats && d.models[mm].stats[sk] !== undefined) ? d.models[mm].stats[sk] : '--';\n")
          .append("            var lbl = d.models[mm].label ? '('+(mm+1)+') '+d.models[mm].label : '('+(mm+1)+')';\n")
          .append("            if(lbl.length>10) lbl=lbl.substring(0,9)+'...';\n")
          .append("            parts.push(lbl+': '+v);\n")
          .append("          }\n")
          .append("          valWrap.textContent = parts.join(' | ');\n")
          .append("          /* Store auto values into item.vals */\n")
          .append("          item.vals = [];\n")
          .append("          for(var mn=0; mn<d.models.length; mn++){\n")
          .append("            var sv = (d.models[mn].stats && d.models[mn].stats[sk] !== undefined) ? d.models[mn].stats[sk] : '';\n")
          .append("            item.vals.push(sv);\n")
          .append("          }\n")
          .append("          item.key = k;\n")
          .append("        } else {\n")
          .append("          item.key = 'custom';\n")
          .append("          item.vals = item.vals && item.vals.length === d.models.length ? item.vals : [];\n")
          .append("          for(var mi=0; mi<d.models.length; mi++){\n")
          .append("            if(!item.vals[mi]) item.vals.push('');\n")
          .append("          }\n")
          .append("          for(var mp=0; mp<d.models.length; mp++){\n")
          .append("            (function(pp){\n")
          .append("              var ci = document.createElement('input');\n")
          .append("              ci.value = item.vals[pp]||''; ci.placeholder = '0.00';\n")
          .append("              ci.style.cssText = 'width:60px;background:").append(inputBg).append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd).append(";border-radius:3px;padding:3px 5px;font-size:.75rem;font-family:inherit;text-align:center;margin-right:3px;';\n")
          .append("              ci.oninput = function(){ item.vals[pp] = ci.value; };\n")
          .append("              valWrap.appendChild(ci);\n")
          .append("            })(mp);\n")
          .append("          }\n")
          .append("        }\n")
          .append("      }\n")
          .append("      sel.onchange = function(){ refreshValDisplay(); };\n")
          .append("      row.appendChild(sel);\n")
          .append("      row.appendChild(valWrap);\n")
          .append("      if(!item.key && sel.options && sel.options.length){\n")
          .append("        sel.value = sel.options[0].value; item.key = sel.value;\n")
          .append("      }\n")
          .append("      refreshValDisplay();\n")
          .append("      var del = document.createElement('button');\n")
          .append("      del.textContent = 'X'; del.title = 'Remove';\n")
          .append("      del.style.cssText = 'flex:0 0 28px;background:").append(btnBg).append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd).append(";border-radius:3px;padding:3px 4px;font-size:11px;cursor:pointer;';\n")
          .append("      del.onclick = function(){ ex.addstats.splice(idx,1); rebuildStats(); };\n")
          .append("      row.appendChild(del);\n")
          .append("      return row;\n")
          .append("    }\n")
          .append("    var add = document.createElement('button');\n")
          .append("    add.textContent = '+ Add statistic';\n")
          .append("    add.style.cssText = 'background:").append(btnBg).append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd).append(";border-radius:3px;padding:4px 12px;font-size:.78rem;cursor:pointer;margin-top:4px;';\n")
          .append("    add.onmouseover = function(){ this.style.background='").append(btnHover).append("'; };\n")
          .append("    add.onmouseout  = function(){ this.style.background='").append(btnBg).append("'; };\n")
          .append("    add.onclick = function(){\n")
          .append("      var vals = []; for(var k=0; k<d.models.length; k++) vals.push('');\n")
          .append("      ex.addstats.push({ label: '', key: '', vals: vals }); rebuildStats();\n")
          .append("    };\n")
          .append("    sec.appendChild(rowsWrap);\n")
          .append("    sec.appendChild(add);\n")
          .append("    rebuildStats();\n")
          .append("    return sec;\n")
          .append("  }\n")
          .append("  var footer = document.createElement('div');\n")
          .append("  footer.style.cssText = 'display:flex;gap:8px;justify-content:flex-end;margin-top:.8rem;border-top:1px solid ").append(inputBd).append(";padding-top:.8rem;';\n")
          .append("  var resetBtn = document.createElement('button');\n")
          .append("  resetBtn.textContent = 'Clear all';\n")
          .append("  resetBtn.style.cssText = 'background:").append(btnBg)
          .append(";color:").append(modalTxt).append(";border:1px solid ").append(inputBd)
          .append(";border-radius:3px;padding:5px 14px;font-size:.78rem;cursor:pointer;';\n")
          .append("  resetBtn.onclick = function(){\n")
          .append("    if(confirm('Remove all indicator rows and additional stats?')){\n")
          .append("      ex.indicators.length = 0; ex.addstats.length = 0;\n")
          .append("      document.body.removeChild(ov);\n")
          .append("      _spkTblRefreshPreview();\n")
          .append("    }\n")
          .append("  };\n")
          .append("  footer.appendChild(resetBtn);\n")
          .append("  var doneBtn = document.createElement('button');\n")
          .append("  doneBtn.textContent = 'Done';\n")
          .append("  doneBtn.style.cssText = 'background:").append(dark ? "#3a3a6a" : "#2980b9")
          .append(";color:#ffffff;border:1px solid ").append(dark ? "#5a5a9a" : "#1f6695")
          .append(";border-radius:3px;padding:5px 18px;font-size:.78rem;font-weight:600;cursor:pointer;';\n")
          .append("  doneBtn.onclick = function(){\n")
          .append("    document.body.removeChild(ov);\n")
          .append("    _spkTblRefreshPreview();\n")
          .append("  };\n")
          .append("  footer.appendChild(doneBtn);\n")
          .append("  mb.appendChild(footer);\n")
          .append("  document.body.appendChild(ov);\n")
          .append("}\n");

        js.append("function _spkTblRefreshPreview(){\n")
          .append("  var d = window.__tblData; if(!d) return;\n")
          .append("  var ex = window.__tblExtras || {indicators:[],addstats:[]};\n")
          .append("  var btn = document.getElementById('_tblBtnCopy'); if(!btn) return;\n")
          .append("  var toolbar = btn.parentNode;\n")
          .append("  var wrap = toolbar.parentNode;\n")
          .append("  var tbl = wrap.querySelector('table'); if(!tbl) return;\n")
          .append("  var tbody = tbl.querySelector('tbody'); if(!tbody) return;\n")
          .append("  var old = tbody.querySelectorAll('tr.__spkExtra'); for(var i=0; i<old.length; i++) old[i].parentNode.removeChild(old[i]);\n")
          .append("  if((!ex.indicators || !ex.indicators.length) && (!ex.addstats || !ex.addstats.length)) return;\n")
          .append("  var nCols = d.headers.length;\n")
          .append("  var border = '").append(dark ? "#2a2a4a" : "#e0e0e0").append("';\n")
          .append("  var muted = '").append(dark ? "#888888" : "#666666").append("';\n")
          .append("  var headCol = '").append(dark ? "#a8d8ea" : "#2c3e50").append("';\n")
          .append("  function makeTr(label, vals, isStat){\n")
          .append("    var tr = document.createElement('tr');\n")
          .append("    tr.className = '__spkExtra';\n")
          .append("    var td = document.createElement('td');\n")
          .append("    td.style.cssText = 'padding:.4rem .65rem;border-bottom:1px solid '+border+';color:'+(isStat?muted:headCol)+';text-align:left;font-weight:'+(isStat?'500':'600')+(isStat?';font-style:italic':'')+';';\n")
          .append("    td.textContent = label; tr.appendChild(td);\n")
          .append("    var combo = document.createElement('td');\n")
          .append("    combo.colSpan = nCols - 1;\n")
          .append("    combo.style.cssText = 'padding:.4rem .65rem;border-bottom:1px solid '+border+';color:'+muted+';text-align:right;font-size:.78rem;';\n")
          .append("    combo.textContent = vals.join('  |  ');\n")
          .append("    tr.appendChild(combo);\n")
          .append("    return tr;\n")
          .append("  }\n")
          .append("  var frag = document.createDocumentFragment();\n")
          .append("  if(ex.indicators){ for(var k=0; k<ex.indicators.length; k++){ frag.appendChild(makeTr(ex.indicators[k].label||'', ex.indicators[k].vals||[], false)); } }\n")
          .append("  if(ex.addstats){ for(var s=0; s<ex.addstats.length; s++){ frag.appendChild(makeTr(ex.addstats[s].label||'', ex.addstats[s].vals||[], true)); } }\n")
          .append("  tbody.appendChild(frag);\n")
          .append("}\n");

        return js.toString();
    }

    /** Fallback: compute t/z as coef/se when pre-computed value not available. */
    private String compTstat(String coef, String se) {
        try {
            double c = Double.parseDouble(coef);
            double s = Double.parseDouble(se);
            if (s > 0) return String.format(Locale.ROOT, "%.2f", c / s);
        } catch (NumberFormatException e) { /* blank */ }
        return "";
    }
    /** v3.6.0: Format pre-computed t/z statistic to 2 decimal places.
     *  These are computed in ado from raw b/se BEFORE eform/rescale,
     *  so they are always on the correct (log/original) scale. */
    private String fmtTz(String tz) {
        try {
            double v = Double.parseDouble(tz);
            if (Double.isNaN(v) || Double.isInfinite(v)) return "";
            return String.format(Locale.ROOT, "%.2f", v);
        } catch (NumberFormatException e) { /* blank */ }
        return "";
    }
    private String pStars(String pv) {
        // v3.6.0-s6b3: Economics journal convention (10/5/1 percent levels)
        // * p<0.10, ** p<0.05, *** p<0.01
        try {
            double p = Double.parseDouble(pv);
            if (p < 0.01)  return " ***";
            if (p < 0.05)  return " **";
            if (p < 0.10)  return " *";
        } catch (NumberFormatException e) { /* ignore */ }
        return "";
    }

    /**
     * v3.6.0-t2h (batch 2a): stars honouring the user's stars() thresholds
     * (o.table.stars, descending, default 0.10 0.05 0.01). Returns the star
     * string WITHOUT a leading space ("", "*", "**", "***"); empty when
     * nostars is set or the p-value is not numeric. When stars() is left at
     * the default this is identical to pStars().trim(), so existing output is
     * unchanged unless the user opts into stars(). Package-private: PubTable
     * reads the pre-computed sig from __tblData, so this is only used here.
     */
    String starsFor(String pv) {
        if (o.table.noStars) return "";
        try {
            double p = Double.parseDouble(pv);
            int c = 0;
            for (String t : o.table.stars) {
                try { if (p < Double.parseDouble(t)) c++; } catch (NumberFormatException e) { /* skip */ }
            }
            StringBuilder s = new StringBuilder();
            for (int i = 0; i < c; i++) s.append('*');
            return s.toString();
        } catch (NumberFormatException e) { return ""; }
    }

    /**
     * v3.6.0-t2h: plain-text star legend built from o.table.stars, e.g.
     * "* p<0.10, ** p<0.05, *** p<0.01". Used by the table footer and exports
     * so the legend always matches the stars actually drawn. ASCII only.
     */
    String starLegendText() {
        if (o.table.noStars) return "";
        String[] t = o.table.stars;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < t.length; i++) {
            if (i > 0) s.append(", ");
            for (int j = 0; j <= i; j++) s.append('*');
            s.append(" p<").append(t[i]);
        }
        return s.toString();
    }

    /**
     * T1 (t2j): LaTeX-formatted star legend from o.table.stars, e.g.
     * "\sym{*} $<$0.10, \sym{**} $<$0.05, \sym{***} $<$0.01". Used by the LaTeX
     * export so the note matches the stars() thresholds actually drawn (was a
     * hardcoded 10/5/1 legend). Empty under nostars. ASCII only.
     */
    String starLegendLatex() {
        if (o.table.noStars) return "";
        String[] t = o.table.stars;
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < t.length; i++) {
            if (i > 0) s.append(", ");
            s.append("\\sym{");
            for (int j = 0; j <= i; j++) s.append('*');
            s.append("} $<$").append(t[i]);
        }
        return s.toString();
    }

    /** Format a numeric string to 4 decimal places for the coef table. */
    private String fmtNum(String s) {
        try {
            double v = Double.parseDouble(s);
            return String.format(Locale.ROOT, "%.4f", v);
        } catch (NumberFormatException e) {
            return escHtml(s);
        }
    }

    /** Format a p-value: show <0.001 for very small values, otherwise 3 dp. */
    private String fmtPval(String s) {
        try {
            double p = Double.parseDouble(s);
            if (p < 0.001) return "&lt;0.001";
            return String.format(Locale.ROOT, "%.3f", p);
        } catch (NumberFormatException e) {
            return escHtml(s);
        }
    }

    private String buildByPanels(DataSet data) {
        Variable byVar = data.getByVariable();
        List<String> groups = DataSet.uniqueValues(byVar, o.chart.sortgroups);
        String cls = o.chart.layout.equals("horizontal") ? "panels-horizontal"
                   : o.chart.layout.equals("grid")       ? "panels-grid" : "panels-vertical";
        // fix9f: an explicit layout(vertical) is honoured in PNG/PDF exports too (panels-keep
        // opts out of the fix9e two-across export rule); the default stack exports as a grid
        if (cls.equals("panels-vertical") && o.chart.layoutExplicit) cls = "panels-vertical panels-keep";
        // s9g: one key for all panels, above the grid (per-panel keys are suppressed in panel mode)
        // fix9l ([stated] Fahad 2026-09-14: "one button for PNG SVG and PDF for by() plots"): the
        // figure is the whole grid, as saveas() already treats it, so ONE toolbar above the keys
        // exports every panel (title, context line, shared key, panels two across) through
        // _spkPageToSvg; the per-panel toolbars are gone. canvasId 'page' selects that route.
        StringBuilder sb = new StringBuilder(o.chart.download ? buildDownloadButtons("page") + "\n" : "");
        sb.append(buildSharedKey(data));
        sb.append(buildElementsKey());
        sb.append("<div class='"+cls+"'>\n");
        List<String> groupKeys = DataSet.uniqueGroupKeys(byVar, o.chart.sortgroups);
        int idx = 0;
        for (String g : groups) {
            String canvasId = "chart_by_" + idx;
            int nPanel = 0; try { nPanel = subsetByGroup(data, byVar, groupKeys.get(idx)).getObservationCount(); } catch (Exception ignored) {}
            sb.append("  <div class='panel-item'><div class='chart-wrapper'>\n")
              .append("    <p class='panel-title'>").append(escHtml(byVar.getDisplayName()+" = "+(sdz(g).isEmpty() || g.equals(DataSet.MISSING_SENTINEL) ? "(missing)" : sdz(g))))
              .append(nPanel > 0 ? "<span class='panel-n'>n = " + nPanel + "</span>" : "").append("</p>\n")
              .append("    <div class='spk-chart-box'><canvas id='").append(canvasId).append("' role='img' aria-label='").append(escHtml(o.title)).append("'></canvas></div>\n")
              .append("  </div></div>\n");
            idx++;
        }
        if (o.chart.layout.equals("grid") && groups.size() % 2 != 0)
            sb.append("  <div class='panel-item panel-empty'></div>\n");
        return sb.append("</div>\n").toString();
    }

    /** s9g: one HTML key above the by() grid, from the same series list/colours the panels use. */
    /** s9i: SVG glyph for a key entry: line with the series dash array, a point shape, a filled swatch,
     *  a hollow outlier circle, or a whisker. Dash arrays and shapes are the SAME values the datasets carry. */
    private String keyGlyph(String kind, String color, String dashJs, String pointStyle) {
        String d = dashJs == null ? "" : dashJs.replace("[", "").replace("]", "").trim();
        String da = d.isEmpty() ? "" : " stroke-dasharray='" + escHtml(d.replace(",", " ")) + "'";
        String c = escHtml(color);
        switch (kind) {
            case "line":    return "<svg width='30' height='12' aria-hidden='true'><line x1='1' y1='6' x2='29' y2='6' stroke='" + c + "' stroke-width='2.5'" + da + "/></svg>";
            case "swatch":  return "<span class='sw' style='background:" + c + "'></span>";
            case "box":     return "<svg width='18' height='14' aria-hidden='true'><rect x='1' y='1' width='16' height='12' fill='" + c + "' stroke='" + c + "' stroke-opacity='1'/></svg>";
            case "whisker": case "whisker_thick": { String sw = kind.equals("whisker_thick") ? "2.8" : "1.5";
                return "<svg width='14' height='16' aria-hidden='true'><line x1='7' y1='1' x2='7' y2='15' stroke='" + c + "' stroke-width='" + sw + "'/><line x1='3' y1='1' x2='11' y2='1' stroke='" + c + "' stroke-width='" + sw + "'/><line x1='3' y1='15' x2='11' y2='15' stroke='" + c + "' stroke-width='" + sw + "'/></svg>"; }
            case "outlier": return "<svg width='12' height='12' aria-hidden='true'><circle cx='6' cy='6' r='4' fill='none' stroke='" + c + "' stroke-width='1.5'/></svg>";
            case "hollowbox": return "<svg width='18' height='14' aria-hidden='true'><rect x='1.5' y='1.5' width='15' height='11' fill='none' stroke='" + c + "' stroke-width='1.5'/></svg>";   // t2j fix8x: hollow bar (p > 0.1)
            case "dot":     return "<span class='dot' style='background:" + c + "'></span>";
            case "point": {
                String ps = pointStyle == null || pointStyle.isEmpty() ? "circle" : pointStyle;
                String shape;
                switch (ps) {
                    case "rect": case "rectRounded": shape = "<rect x='2' y='2' width='9' height='9' fill='" + c + "'/>"; break;
                    case "rectRot": shape = "<polygon points='6.5,1 12,6.5 6.5,12 1,6.5' fill='" + c + "'/>"; break;
                    case "triangle": shape = "<polygon points='6.5,1 12,12 1,12' fill='" + c + "'/>"; break;
                    case "cross": shape = "<path d='M2 2 L11 11 M11 2 L2 11' stroke='" + c + "' stroke-width='2'/>"; break;
                    case "crossRot": shape = "<path d='M6.5 1 L6.5 12 M1 6.5 L12 6.5' stroke='" + c + "' stroke-width='2'/>"; break;
                    case "star": shape = "<path d='M6.5 1 L6.5 12 M1 6.5 L12 6.5 M2 2 L11 11 M11 2 L2 11' stroke='" + c + "' stroke-width='1.5'/>"; break;
                    case "dash": case "line": shape = "<line x1='1' y1='6.5' x2='12' y2='6.5' stroke='" + c + "' stroke-width='2'/>"; break;
                    default: shape = "<circle cx='6.5' cy='6.5' r='4.5' fill='" + c + "'/>";
                }
                return "<svg width='13' height='13' aria-hidden='true'>" + shape + "</svg>";
            }
            default: return "";
        }
    }
    private String keyItem(String role, String color, String glyph, String label) {
        return "<span class='k' data-role='" + role + "' data-color='" + escHtml(color) + "'>" + glyph + escHtml(label) + "</span>";
    }
    private String pointStyleFor(int si) {
        return o.chart.pointstyle.isEmpty() ? "circle" : o.chart.pointstyle;   // data charts: one marker style for all series
    }

    /** s9j: the "elements key" -- everything the renderer registered that is not a Chart.js
     *  dataset (CI, null/reference lines, bands, fit lines). Rendered once per page; empty -> "". */
    String buildElementsKey() {
        if (o._keyItems.isEmpty()) return "";
        java.util.LinkedHashMap<String, String[]> uniq = new java.util.LinkedHashMap<>();
        for (String[] it : o._keyItems) uniq.putIfAbsent(it[0] + "|" + it[1] + "|" + it[3], it);   // same element registered per panel -> once
        StringBuilder k = new StringBuilder("<div class='spk-shared-key spk-elements-key' role='list'>");
        for (String[] it : uniq.values()) k.append(keyItem(it[0], it[3], keyGlyph(it[2], it[3], it[4].isEmpty() ? null : it[4], it[5].isEmpty() ? null : it[5]), it[1]));
        return k.append("</div>\n").toString();
    }

    /** t2j fix8v: the chart types whose single-page key is the HTML shared key. */
    private boolean isBoxOrViolinType() {
        String t = o.type;
        return t.equals("boxplot") || t.equals("hbox") || t.equals("violin") || t.equals("hviolin");
    }

    /** s9i: one HTML key above the by() grid listing EVERY drawn element, styled from the same
     *  resolved values the panels draw with (colours, dash patterns, point shapes). */
    private String buildSharedKey(DataSet data) {
        StringBuilder k = new StringBuilder("<div class='spk-shared-key' role='list'>");
        String t = o.type;
        boolean isBox = t.equals("boxplot") || t.equals("hbox"), isViolin = t.equals("violin") || t.equals("hviolin");
        if (isBox || isViolin) {
            int nGroups = data.hasOver() ? DataSet.uniqueValues(data.getOverVariable(), o.chart.sortgroups, o.showmissingOver).size() : 1;
            String med = dsb.boxMarkerColor(nGroups, false), mea = dsb.boxMarkerColor(nGroups, true), meaBorder = dsb.markerBorderColor(mea);
            if (isViolin) k.append(keyItem("median", med, keyGlyph("point", med, null, "rectRot"), "Median"));
            else          k.append(keyItem("median", med, keyGlyph("line", med, null, null), "Median"));
            k.append("<span class='k' data-role='mean' data-color='").append(escHtml(mea)).append("'><span class='dot' style='background:").append(escHtml(mea)).append(";border:1.5px solid ").append(escHtml(meaBorder)).append("'></span>Mean</span>");
            if (isViolin) {
                // t2j fix8v: a violin's group colours are its DENSITY shapes; the IQR box inside is
                // one neutral outlined box (the plugin's own colours), and violins draw no outliers.
                String iqr = isDark() ? "rgba(255,255,255,0.85)" : "rgba(0,0,0,0.75)";
                k.append("<span class='k' data-role='box' data-color='").append(escHtml(iqr)).append("'><svg width='18' height='14' aria-hidden='true'><rect x='1.5' y='1.5' width='15' height='11' fill='").append(escHtml(isDark() ? "rgba(255,255,255,0.25)" : "rgba(0,0,0,0.22)")).append("' stroke='").append(escHtml(iqr)).append("' stroke-width='2'/></svg>IQR box</span>");
                k.append(keyItem("whisker", colS(0), keyGlyph("whisker", colS(0), null, null), "Whiskers (1.5 x IQR)"));
                k.append(keyItem("density", col(0), keyGlyph("swatch", col(0), null, null), "Density"));
                return k.append("</div>\n").toString();
            }
            // t2j fix8w (Fahad's decision): ONE "IQR box" entry, never one per group colour --
            // the group colours already sit under the x-axis labels, so listing "IQR box: 1..5"
            // repeated them. Same rule as the violin key above.
            k.append(keyItem("box", col(0), keyGlyph("box", col(0), null, null), "IQR box"));
            k.append(keyItem("whisker", colS(0), keyGlyph("whisker", colS(0), null, null), "Whiskers (1.5 x IQR)"));
            k.append(keyItem("outlier", dsb.outlierColor(), keyGlyph("outlier", dsb.outlierColor(), null, null), "Outliers"));
            return k.append("</div>\n").toString();
        }
        List<Variable> nv = data.getNumericVariables();
        boolean lineType = t.equals("line") || t.equals("area") || t.equals("ciline");
        boolean pointType = t.equals("scatter") || t.equals("bubble");
        boolean colorByCat = data.hasOver() && nv.size() <= 1 && !lineType && !pointType;
        if (colorByCat || (data.hasOver() && (pointType || (lineType && nv.size() <= 1)))) {
            List<String> gs = DataSet.uniqueValues(data.getOverVariable(), o.chart.sortgroups, o.showmissingOver);
            for (int i = 0; i < gs.size(); i++) {
                String g = lineType ? keyGlyph("line", colS(i), dsb.resolvedBorderDash(i), null) + (o.chart.nopoints ? "" : keyGlyph("point", colS(i), null, pointStyleFor(i)))
                         : pointType ? keyGlyph("point", colS(i), null, pointStyleFor(i)) : keyGlyph("swatch", col(i), null, null);
                k.append(keyItem("series", lineType || pointType ? colS(i) : col(i), g, gs.get(i)));
            }
        } else if (pointType) {
            // t2j fix3: a scatter/bubble with no over() is a SINGLE-colour series. Its
            // numeric variables are ENCODINGS of that one series -- x vs y, and for bubble a
            // third variable mapped to point SIZE (not colour). The old code looped every
            // numeric variable and drew a differently coloured dot for each, producing a
            // misleading multi-colour legend (e.g. Price/Weight/Mileage in three colours)
            // while all the plotted points were one colour. Emit ONE key entry that names
            // the encoding, matching the chart's own single dataset label.
            boolean isBubbleKey = t.equals("bubble") && nv.size() >= 3;
            String lbl = isBubbleKey
                ? nv.get(1).getDisplayName() + " vs " + nv.get(0).getDisplayName()
                    + " (size = " + nv.get(2).getDisplayName() + ")"
                : (nv.size() >= 2 ? nv.get(0).getDisplayName() + " vs " + nv.get(1).getDisplayName()
                                  : (nv.isEmpty() ? "" : nv.get(0).getDisplayName()));
            k.append(keyItem("series", colS(0), keyGlyph("point", colS(0), null, pointStyleFor(0)), lbl));
        } else {
            for (int i = 0; i < nv.size(); i++) {
                String g = lineType ? keyGlyph("line", colS(i), dsb.resolvedBorderDash(i), null) + (o.chart.nopoints ? "" : keyGlyph("point", colS(i), null, pointStyleFor(i)))
                         : keyGlyph("swatch", col(i), null, null);
                k.append(keyItem("series", lineType ? colS(i) : col(i), g, nv.get(i).getDisplayName()));
            }
        }
        return k.append("</div>\n").toString();
    }

    private String buildByScripts(DataSet data) {
        Variable byVar = data.getByVariable();
        List<String> groups    = DataSet.uniqueValues(byVar, o.chart.sortgroups);
        List<String> groupKeys = DataSet.uniqueGroupKeys(byVar, o.chart.sortgroups);
        // v2.6.1: clear preamble list before building by() panels so we start fresh.
        o._byHistPreambles.clear();
        // v3.4.1: for single-var bar + over() + by(), colorByCategory bars must use
        // globally consistent color indices so the same over-group gets the same color
        // in every panel. Store the full over-group list from the COMPLETE dataset
        // (before subsetting by by-group) so overDatasets() can look up the global
        // color index for each local group. Reset to null after loop.
        // v3.5.21: set for ALL chart types with over() so multi-var line/area by()
        // panels also get null-padding and aligned x-axis positions. (v3.5.21 fix
        // added useGlobalAlignment but buildByScripts only set _globalOverGroups
        // when nv.size()==1, so line/area by() panels were still unaligned.)
        if (data.hasOver()) {
            o._globalOverGroups = DataSet.uniqueValues(
                data.getOverVariable(), o.chart.sortgroups, o.showmissingOver);
        }
        StringBuilder sb = new StringBuilder();
        // s9g: panels share ONE value axis (facet_wrap scales="fixed") unless yfree; the
        // range is the nice floor/ceil of all values (bars from zero) so panels are
        // comparable and tick labels are round numbers, not padded extremes
        String savedMin = o.axes.yrangeMin, savedMax = o.axes.yrangeMax, savedAspect = o.chart.aspect, savedLegend = o.chart.legend;
        boolean canShare = !o.chart.yfree && o.axes.yrangeMin.isEmpty() && !o.type.equals("pie") && !o.type.equals("donut") && !o.type.equals("histogram") && !o.type.equals("stackedbar100");
        if (canShare) {
            // pass 1: build every panel with a free axis and read the extent of what it PLOTS
            // (means for bars, raw values for box/violin/scatter) from the emitted datasets
            double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
            java.util.regex.Pattern num = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?(?:[eE]-?\\d+)?");
            for (int gi = 0; gi < groups.size(); gi++) {
                o._histPreamble = "";
                String js = cr.buildChartScript("chart_by_" + gi, subsetByGroup(data, byVar, groupKeys.get(gi)));
                java.util.regex.Matcher dm = java.util.regex.Pattern.compile("data:\\[([^\\]]*)\\]").matcher(js);
                while (dm.find()) { java.util.regex.Matcher nm = num.matcher(dm.group(1)); while (nm.find()) { try { double d = Double.parseDouble(nm.group()); if (d < lo) lo = d; if (d > hi) hi = d; } catch (NumberFormatException ignored) {} } }
            }
            boolean barLike = o.type.equals("bar") || o.type.equals("hbar") || o.type.equals("stackedbar") || o.type.equals("cibar");
            if (barLike && lo > 0) lo = 0;
            // t2j fix8q (QC sweep S3): scatter/bubble data are {x:,y:[,r:]} objects, so the regex
            // above swept x (and bubble r) values into the shared y range (weight 2,000-4,300
            // widened price's axis to 0-20,000). Use the y variable's own full-data extent.
            if (o.type.equals("scatter") || o.type.equals("bubble")) {
                List<Variable> _ynv = data.getNumericVariables();
                if (_ynv.size() >= 2) { double[] _ye = _fullExtent(_ynv.get(0)); if (!Double.isNaN(_ye[0])) { lo = _ye[0]; hi = _ye[1]; } }
            }
            if (lo < hi) {
                // t2j fix8r (decision 3): target ~10 ticks like the single-chart C8 lock (fix8q),
                // so price 3,299-15,906 gives 0-16,000 for bars / 2,000-16,000 for points
                // instead of the 6-tick 0-20,000 that wasted a fifth of every panel.
                double[] nr = ChartRenderer.niceRange(lo, hi, 10);
                o.axes.yrangeMin = String.format(Locale.ROOT, "%.6g", nr[0]); o.axes.yrangeMax = String.format(Locale.ROOT, "%.6g", nr[1]); o._sharedAxis = true;
            }
        }
        if (o.chart.aspect.isEmpty()) o.chart.aspect = "1.35";   // taller panels
        o.chart.legend = "none";                                 // the key is above the grid
        // t2j fix8 (deep-dive r3): for bubble by() panels, compute the size range over the
        // FULL dataset once so every panel scales radii on the same basis the JS filter
        // recompute uses (full-data scale) -- otherwise the server scaled each panel's radii
        // to that panel's own size range and bubbles resized the first time a filter fired.
        if (o.type.equals("bubble")) {
            List<Variable> _bnv = data.getNumericVariables();
            if (_bnv.size() >= 3) {
                double _brmin = Double.MAX_VALUE, _brmax = -Double.MAX_VALUE;
                for (Object v : _bnv.get(2).getValues()) {
                    if (v instanceof Number) { double d = ((Number) v).doubleValue(); if (d < _brmin) _brmin = d; if (d > _brmax) _brmax = d; }
                }
                if (_brmin != Double.MAX_VALUE) {
                    o._bubbleRminGlobal  = _brmin;
                    o._bubbleRspanGlobal = (_brmax - _brmin == 0) ? 1 : (_brmax - _brmin);
                }
            }
        }
        // t2j fix8q (C8 for by() panels): scatter/bubble panels lock their continuous axes to
        // the FULL-data extent (x = 2nd numeric var, y = 1st) so all panels share one frame and
        // a filter never rescales it. Stored here, read by ChartRenderer.lockAxisToPanelExtent().
        if (o.type.equals("scatter") || o.type.equals("bubble")) {
            List<Variable> _pnv = data.getNumericVariables();
            if (_pnv.size() >= 2) {
                o._panelYExtent = _fullExtent(_pnv.get(0));
                o._panelXExtent = _fullExtent(_pnv.get(1));
            }
        }
        o._panelMode = true;
        int idx = 0;
        for (int gi = 0; gi < groups.size(); gi++) {
            // Reset histogram preamble so histogram() can write the panel-specific preamble.
            o._histPreamble = "";
            DataSet panel = subsetByGroup(data, byVar, groupKeys.get(gi));
            sb.append(cr.buildChartScript("chart_by_"+idx, panel));
            // If histogram() wrote a preamble (i.e. this panel is a histogram), collect it.
            // buildByPreambles() emits all collected preambles before the panel scripts. (v2.6.1)
            if (!o._histPreamble.isEmpty()) o._byHistPreambles.add(o._histPreamble);
            idx++;
        }
        o._globalOverGroups = null; // reset after panels built
        o._bubbleRminGlobal = Double.NaN; o._bubbleRspanGlobal = Double.NaN; // reset global bubble scale
        o._panelXExtent = null; o._panelYExtent = null;                        // fix8q: reset panel axis lock
        o.axes.yrangeMin = savedMin; o.axes.yrangeMax = savedMax; o.chart.aspect = savedAspect; o.chart.legend = savedLegend;
        o._panelMode = false; o._sharedAxis = false;
        return sb.toString();
    }

    /**
     * Returns all by()-panel histogram preambles (_ttRanges_ and _ttCounts_ declarations)
     * concatenated, so each panel's tooltip callbacks can reference their own arrays.
     * Populated by buildByScripts() for histogram panels; empty for other chart types. (v2.6.1)
     */
    private String buildByHistPreambles() {
        if (o._byHistPreambles.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String pre : o._byHistPreambles) sb.append(pre);
        return sb.toString();
    }

    /** t2j fix8q: full-data {min,max} of a numeric variable (missing skipped); NaN pair if empty. */
    private static double[] _fullExtent(Variable v) {
        double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
        for (Object x : v.getValues()) {
            if (x instanceof Number) { double d = ((Number) x).doubleValue(); if (d < mn) mn = d; if (d > mx) mx = d; }
        }
        return mn == Double.MAX_VALUE ? new double[]{ Double.NaN, Double.NaN } : new double[]{ mn, mx };
    }

    private DataSet subsetByGroup(DataSet data, Variable byVar, String group) {
        String gc = sdz(group);   // group is always a raw key here
        List<Integer> mi = new ArrayList<>();
        for (int i = 0; i < byVar.getValues().size(); i++) {
            Object v = byVar.getValues().get(i);
            if (gc.equals(sdz(v==null?"":String.valueOf(v)))) mi.add(i);
        }
        DataSet sub = new DataSet();
        for (Variable v : data.getVariables()) {
            Variable sv = subsetVar(v, mi);
            sub.addVariable(sv);
        }
        if (data.hasOver()) sub.setOverVariable(subsetVar(data.getOverVariable(), mi));
        return sub;
    }

    Variable subsetVar(Variable v, List<Integer> idx) {
        Variable sv = new Variable();
        sv.setName(v.getName()); sv.setLabel(v.getLabel()); sv.setNumeric(v.isNumeric());
        // Preserve value labels in subsetted variable
        for (Map.Entry<Double, String> e : v.getValueLabels().entrySet()) {
            sv.putValueLabel(e.getKey(), e.getValue());
        }
        for (int i : idx) sv.addValue(v.getValues().get(i));
        return sv;
    }

    // -- Chart dispatcher ------------------------------------------------------


    // -- CSS generation -------------------------------------------------------

    private String buildCss(String bg, String plotCl, boolean dark) {
        String text      = dark ? "#e0e0e0" : "#333333";
        String heading   = dark ? "#a8d8ea" : "#2c3e50";
        String border    = dark ? "#2a2a4a" : "#e0e0e0";
        String thBg      = dark ? "#0f3460" : "#4e79a7";
        String trHov     = dark ? "#1e2a4a" : "#f0f4f8";
        String accent    = dark ? "#1e3a5a" : "#d6e4f0";
        String sub       = "#888888";
        // chipAccent: interactive highlight color. In dark mode use a mid-blue visible on dark bg.
        String chipAccent = dark ? "#3a7abf" : "#4e79a7";
        String chipActBg  = dark ? "#1a4a7a" : "#4e79a7";
        String chipActTxt = dark ? "#a8d8ea" : "#ffffff";
        // CV badge colors: only one set, theme-resolved at generation time
        String cvLowBg  = dark ? "#14532d" : "#dcfce7";
        String cvLowTxt = dark ? "#86efac" : "#166534";
        String cvMedBg  = dark ? "#422006" : "#fef9c3";
        String cvMedTxt = dark ? "#fde68a" : "#854d0e";
        String cvHiBg   = dark ? "#450a0a" : "#fee2e2";
        String cvHiTxt  = dark ? "#fca5a5" : "#991b1b";

        return "* {box-sizing:border-box;margin:0;padding:0;}\n"
            // t2j fix7: always reserve the vertical scrollbar. On desktop browsers with
            // classic (width-taking) scrollbars, a responsive+maintainAspectRatio chart could
            // enter an infinite resize loop when zoomed below 100%: the chart height crossed
            // the viewport, a scrollbar appeared, that shrank the container width, which shrank
            // the chart, which removed the scrollbar, which widened it again -- repeating. A
            // permanently reserved scrollbar keeps the layout width constant, breaking the loop.
            + "html{overflow-y:scroll;}\n"
            + "body{background:"+bg+";color:"+text+";font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;padding:2rem;}\n"
            + ".container{max-width:1200px;margin:0 auto;}\n.spk-chart-box{position:relative;width:100%;min-width:0;}\n.spk-chart-box canvas{display:block;max-width:100%;}\n"
            + ".coefplot-wrap{max-width:800px;margin:0 auto;}\n"
            + "h1{font-size:1.8rem;color:"+heading+";margin-bottom:.15rem;}\n"
            + ".subtitle2{font-size:1.05rem;color:"+heading+";opacity:.75;margin-bottom:.2rem;}\n"
            + ".subtitle{font-size:.8rem;color:"+sub+";margin-bottom:1.5rem;}\n"
            + ".panels-vertical{display:flex;flex-direction:column;gap:1.5rem;margin-bottom:1.5rem;}\n"
            // s9g: panels WRAP into as many columns as fit (min 380px), never a scroll strip
            + ".panels-horizontal{display:grid;grid-template-columns:repeat(auto-fit,minmax(380px,1fr));gap:1.25rem;margin-bottom:1.5rem;}\n"
            + ".panels-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(380px,1fr));gap:1.25rem;margin-bottom:1.5rem;}\n"
            + ".panel-item{min-width:0;}\n"
            + ".spk-shared-key{display:flex;flex-wrap:wrap;gap:.4rem 1.25rem;align-items:center;margin:0 0 .75rem .25rem;font-size:.85rem;color:"+heading+";}\n"
            + ".spk-shared-key .k{display:inline-flex;align-items:center;gap:.4rem;}\n"
            + ".spk-shared-key .sw{display:inline-block;width:14px;height:14px;border-radius:3px;}\n"
            + ".spk-shared-key .ln{display:inline-block;width:18px;height:0;border-top:3px solid;}\n"
            + ".spk-shared-key .dot{display:inline-block;width:9px;height:9px;border-radius:50%;}\n"
            + ".spk-shared-key .box{display:inline-block;width:16px;height:12px;border:1.5px solid;border-radius:2px;}\n"
            + ".panel-empty{border-radius:8px;min-height:200px;}\n"
            + ".chart-wrapper{background:"+plotCl+";border-radius:8px;border:1px solid "+border+";padding:1.5rem;margin-bottom:1.5rem;}\n"
            + ".panels-vertical .chart-wrapper,.panels-horizontal .chart-wrapper,.panels-grid .chart-wrapper{margin-bottom:0;}\n"
            // s9g: caption, not a banner -- more plot, less chrome; n per panel
            + ".panel-title{font-size:.9rem;font-weight:600;color:"+heading+";margin:0 0 .4rem 0;padding:0 0 .3rem 0;border-bottom:1px solid "+border+";display:flex;justify-content:space-between;}\n"
            + ".panel-n{font-weight:400;opacity:.65;}\n"
            // s9m: print / PDF layout -- white page, no interactive chrome, tables unbroken
            + "@page{size:auto;margin:12mm;}\n"
            // fix9m (Fahad: the printed by() page showed the key as bare "1 2 3 4 5"): browsers drop
            // CSS background colours when printing unless the page opts in, and the key swatches
            // ARE background colours (.sw/.dot) or border colours (.ln/.box). Opt every key glyph in.
            + "@media print{body{background:#fff !important;color:#000;}.container{max-width:none;}"
            + ".spk-shared-key,.spk-shared-key *,.spk-elements-key,.spk-elements-key *,.chips-row *{-webkit-print-color-adjust:exact !important;print-color-adjust:exact !important;}"
            // fix9q (Fahad: the whole-page PDF "is cut"): the statistics table is wider than a
            // portrait page and scrolls sideways on screen (.tbl-wrap overflow-x:auto); print
            // cannot scroll, so its right-hand columns were clipped at the card edge. In print
            // the wrapper no longer clips, the table shrinks (smaller type and cell padding,
            // headers may wrap) and the sparkline column keeps its size, so every column fits.
            + ".tbl-wrap{overflow:visible !important;}table.st{font-size:.68rem;table-layout:auto;}table.st th,table.st td{padding:.26rem .38rem !important;white-space:normal !important;}"
            + ".spark-cell{padding:.2rem .3rem !important;}.stats-panel{page-break-inside:auto;}.grp{break-inside:avoid;}"
            + ".chart-wrapper,.stats-panel,.coefplot-wrap{background:#fff !important;border-color:#ccc !important;box-shadow:none !important;}"
            + "button,.chips-row,#chipsRow,.filter-bar,.spk-filters,.spk-sliders,.spk-pub-toolbar,.spk-pub-toolbar div,.spk-toolbar,input,select,.subtitle{display:none !important;}"
            + "table,.grp,.spk-chart-box,.spk-print-svg,.panel-item{break-inside:avoid;page-break-inside:avoid;}"
            // t2j fix9e: printed/exported by() pages lay the panels two across whatever the
            // on-screen layout() (Fahad, 2026-09-13: the grid reads better on paper; the SVG
            // export route already did this) -- panels-vertical joins the rule
            + ".panels-horizontal,.panels-grid,.panels-vertical:not(.panels-keep){display:grid;grid-template-columns:repeat(2,1fr) !important;gap:1.25rem;}"   // fix9f: layout(vertical) written out keeps its stack
            + "h1{font-size:18pt;}.subtitle2{font-size:11pt;}"
            // s9o: table-only / chart-only print: hide everything but the chosen block (title + context stay)
            + "body.spk-print-table-only .chart-wrapper,body.spk-print-table-only .panels-horizontal,body.spk-print-table-only .panels-grid,body.spk-print-table-only .panels-vertical,body.spk-print-table-only .spk-shared-key,body.spk-print-table-only .stats-panel,body.spk-print-table-only .note-area{display:none !important;}"
            + "body.spk-print-table-only #spkTableBlock{display:block !important;margin-top:.5rem;}"
            + "body.spk-print-chart-only #spkTableBlock,body.spk-print-chart-only .stats-panel,body.spk-print-chart-only .spk-elements-key + *{display:none !important;}"
            // t2j fix9c (Linux saveas leg): chart scope also hides the publication table
            // (.spk-pub-wrap, PubTable since U1 -- it has no #spkTableBlock id, so a coefplot
            // PDF got the table as page 2) and every toolbar row (the "Export:" label survived
            // the button-only hide in PNG and PDF); table scope hides the toolbar rows too.
            + "body.spk-print-chart-only .spk-pub-wrap,body.spk-print-chart-only .spk-toolbar,body.spk-print-table-only .spk-toolbar{display:none !important;}"
            + "body.spk-print-chart-only{padding:0;}body.spk-print-chart-only .chart-wrapper{border:none;box-shadow:none;margin:0;}}\n"
            // s9x: figure modes are also used ON SCREEN by the headless PNG run (not only @media print)
            + "body.spk-print-chart-only #spkTableBlock,body.spk-print-chart-only .stats-panel,body.spk-print-chart-only .chips-row,body.spk-print-chart-only #chipsRow,body.spk-print-chart-only button{display:none !important;}\n"
            + "body.spk-print-chart-only .spk-pub-wrap,body.spk-print-chart-only .spk-toolbar{display:none !important;}\n"   // t2j fix9c: on-screen (PNG) twin of the print rule
            + "body.spk-print-chart-only .panels-vertical:not(.panels-keep),body.spk-print-chart-only .panels-horizontal,body.spk-print-chart-only .panels-grid{display:grid;grid-template-columns:repeat(2,1fr) !important;gap:1.25rem;}\n"   // t2j fix9e: full-page PNG (chart scope) = two panels across, like the SVG route and the PDF
            + "body.spk-print-chart-only{padding:8px;margin:0;}body.spk-print-chart-only .container{max-width:none;}\n"
            + "body.spk-print .subtitle{display:none !important;}\n"   // fix9u: no build-stamp footer in headless PNG runs
            + ".note-area{margin-bottom:1.5rem;}\n"
            + ".note{font-size:" + (o.style.noteSize.isEmpty() ? ".85rem" : o.style.noteSize) + ";color:"+sub+";font-style:italic;margin-bottom:.2rem;}\n"
            + ".caption{font-size:" + (o.style.noteSize.isEmpty() ? ".78rem" : o.style.noteSize) + ";color:"+sub+";}\n"
            + ".stats-panel{border-radius:12px;border:1px solid "+border+";overflow:hidden;"
            + "box-shadow:0 1px 3px rgba(0,0,0,.06),0 4px 16px rgba(0,0,0,.04);margin-top:1.5rem;}\n"
            // Shell header
            + ".shell-header{display:flex;align-items:center;justify-content:space-between;"
            + "padding:.85rem 1.25rem;"
            + "background:linear-gradient(135deg,"+plotCl+" 0%,"+bg+" 100%);"
            + "border-bottom:1px solid "+border+";}\n"
            + ".shell-title{display:flex;align-items:center;gap:.5rem;"
            + "font-size:.88rem;font-weight:600;color:"+heading+";}\n"
            + ".shell-icon{width:20px;height:20px;background:"+thBg+";border-radius:5px;"
            + "display:flex;align-items:center;justify-content:center;flex-shrink:0;}\n"
            + ".shell-icon svg{width:12px;height:12px;fill:#fff;}\n"
            + ".hide-btn{padding:.28rem .7rem;font-size:.75rem;font-weight:500;"
            + "color:"+text+";background:"+plotCl+";border:1px solid "+border+";border-radius:6px;"
            + "cursor:pointer;transition:all .15s;}\n"
            + ".hide-btn:hover{background:"+border+";}\n"
            // Column toggle chips
            + ".col-toggles{display:flex;align-items:center;gap:.35rem;"
            + "padding:.6rem 1.25rem;background:"+bg+";border-bottom:1px solid "+border+";"
            + "flex-wrap:wrap;}\n"
            + ".toggle-lbl{font-size:.7rem;font-weight:600;color:"+sub+";"
            + "letter-spacing:.06em;text-transform:uppercase;margin-right:.1rem;}\n"
            + ".chip{display:flex;align-items:center;gap:.22rem;"
            + "padding:.22rem .6rem;font-size:.72rem;font-weight:500;"
            + "border-radius:20px;border:1.5px solid "+border+";"
            + "background:"+plotCl+";color:"+sub+";cursor:pointer;transition:all .15s;}\n"
            + ".chip:hover{border-color:"+chipAccent+";color:"+chipAccent+";}\n"
            + ".chip.on{background:"+chipActBg+";border-color:"+chipActBg+";color:"+chipActTxt+";}\n"
            + ".chip-dot{width:5px;height:5px;border-radius:50%;background:currentColor;opacity:.8;}\n"
            + ".chip-all{margin-left:auto;padding:.22rem .65rem;font-size:.72rem;font-weight:500;"
            + "border-radius:20px;border:1.5px solid "+border+";background:transparent;"
            + "color:"+sub+";cursor:pointer;transition:all .15s;}\n"
            + ".chip-all:hover{border-color:"+sub+";color:"+text+";}\n"
            // Stats body and group blocks
            + ".stats-body{padding:0 1.25rem 1.25rem;background:"+plotCl+";}\n"
            // t2j fix6 (D4): content-visibility:auto was dropped here. It deferred paint of
            // off-screen group cards, but a non-scrolled render -- print/PDF export, or a
            // full-page screenshot -- captured the reserved 120px placeholders instead of the
            // stat cards, so the panel exported blank on tall charts. The paint saving is
            // negligible for a stats panel (a handful of cards), so correctness wins.
            + ".grp{margin-top:.9rem;border:1px solid "+border+";border-radius:8px;overflow:hidden;}\n"
            + ".grp-hdr{display:flex;align-items:center;justify-content:space-between;"
            + "padding:.52rem .9rem;background:"+accent+";cursor:pointer;user-select:none;}\n"
            + ".grp-hdr:hover{filter:brightness(.97);}\n"
            + ".grp-title{display:flex;align-items:center;gap:.45rem;"
            + "font-size:.8rem;font-weight:600;color:"+heading+";}\n"
            + ".grp-badge{padding:.1rem .42rem;font-size:.67rem;font-weight:700;"
            + "background:"+thBg+";color:#fff;border-radius:10px;}\n"
            + ".grp-chev{font-size:.65rem;color:"+sub+";}\n"
            // Table styles
            + ".tbl-wrap{overflow-x:auto;}\n"
            + "table.st{width:100%;border-collapse:collapse;font-size:.83rem;}\n"
            + "table.st thead tr{background:"+bg+";border-bottom:2px solid "+border+";}\n"
            + "table.st th{padding:.5rem .85rem;text-align:right;"
            + "font-size:.7rem;font-weight:600;color:"+sub+";"
            + "letter-spacing:.05em;text-transform:uppercase;"
            + "white-space:nowrap;cursor:pointer;user-select:none;transition:color .15s;}\n"
            + "table.st th:first-child{text-align:left;cursor:default;}\n"
            + "table.st th.dist-hdr{cursor:default;}\n"
            + "table.st th:hover:not(:first-child):not(.dist-hdr){color:"+chipAccent+";}\n"
            + "table.st th.sorted{color:"+chipAccent+";}\n"
            + ".sort-arr{margin-left:.18rem;font-size:.6rem;opacity:.5;}\n"
            + "th.sorted .sort-arr{opacity:1;}\n"
            + "table.st td{padding:.44rem .85rem;text-align:right;"
            + "border-bottom:1px solid "+border+";color:"+text+";transition:background .1s;}\n"
            + "table.st td:first-child{text-align:left;font-weight:600;color:"+heading+";}\n"
            + "table.st tbody tr:last-child td{border-bottom:none;}\n"
            + "table.st tbody tr:hover td{background:"+trHov+";}\n"
            // N badge
            + ".nb{display:inline-flex;align-items:center;justify-content:center;"
            + "min-width:30px;padding:.08rem .4rem;"
            + "background:"+accent+";border-radius:4px;"
            + "font-size:.77rem;font-weight:600;color:"+heading+";}\n"
            // CV badge - single themed declaration, no duplicates
            + ".cv{display:inline-flex;align-items:center;"
            + "padding:.09rem .38rem;font-size:.67rem;font-weight:700;"
            + "border-radius:4px;margin-left:.35rem;vertical-align:middle;}\n"
            + ".cv-low{background:"+cvLowBg+";color:"+cvLowTxt+";}\n"
            + ".cv-med{background:"+cvMedBg+";color:"+cvMedTxt+";}\n"
            + ".cv-high{background:"+cvHiBg+";color:"+cvHiTxt+";}\n"
            // Sparkline cell
            + ".spark-cell{padding:.35rem .85rem !important;}\n"
            + ".spark-wrap{display:flex;align-items:center;justify-content:flex-end;gap:.4rem;}\n"
            + ".spark-labels{display:flex;flex-direction:column;align-items:flex-end;gap:1px;"
            + "font-size:.63rem;color:"+sub+";line-height:1;}\n"
            // Legend and sort hint - footer row
            + ".stats-footer{display:flex;align-items:center;justify-content:space-between;"
            + "padding:.3rem .9rem .55rem;}\n"
            + ".spark-legend-global{display:flex;flex-wrap:nowrap;align-items:center;"
            + "gap:.4rem .9rem;padding:.45rem 1rem;margin-top:.5rem;"
            + "border-top:1px solid #e0e0e0;font-size:.68rem;color:"+sub+";"
            + "justify-content:flex-end;}\n"
            + ".spark-legend{display:flex;align-items:center;gap:1rem;"
            + "font-size:.68rem;color:"+sub+";}\n"
            + ".leg-item{display:flex;align-items:center;gap:.3rem;}\n"
            + ".leg-dot{width:7px;height:7px;border-radius:50%;}\n"
            + ".sort-hint{font-size:.7rem;color:"+sub+";font-style:italic;}\n"
            // F-1: dual-handle range slider styles
            + ".sld-group{display:flex;align-items:center;gap:.6rem;flex-wrap:wrap;margin-left:.5rem;}\n"
            + ".sld-label{font-size:.82rem;font-weight:600;color:"+heading+";min-width:4rem;}\n"
            + ".sld-track-wrap{position:relative;width:180px;height:20px;}\n"
            + ".sld-thumb{position:absolute;width:100%;height:4px;top:50%;transform:translateY(-50%);"
            +   "appearance:none;-webkit-appearance:none;background:transparent;pointer-events:none;"
            +   "outline:none;margin:0;padding:0;}\n"
            + ".sld-thumb::-webkit-slider-thumb{appearance:none;-webkit-appearance:none;"
            +   "width:14px;height:14px;border-radius:50%;background:"+chipAccent+";"
            +   "border:2px solid "+plotCl+";cursor:pointer;pointer-events:all;"
            +   "box-shadow:0 1px 3px rgba(0,0,0,.25);}\n"
            + ".sld-thumb::-moz-range-thumb{width:14px;height:14px;border-radius:50%;"
            +   "background:"+chipAccent+";border:2px solid "+plotCl+";"
            +   "cursor:pointer;pointer-events:all;box-shadow:0 1px 3px rgba(0,0,0,.25);}\n"
            + ".sld-fill{position:absolute;height:4px;top:50%;transform:translateY(-50%);"
            +   "background:"+chipAccent+";border-radius:2px;pointer-events:none;}\n"
            // track background line behind the fill
            + ".sld-track-wrap::before{content:'';position:absolute;height:4px;width:100%;"
            +   "top:50%;transform:translateY(-50%);background:"+border+";"
            +   "border-radius:2px;pointer-events:none;}\n"
            + ".sld-vals{font-size:.78rem;color:"+sub+";white-space:nowrap;min-width:5rem;}\n";
    }

    // -- Utility helpers -------------------------------------------------------


    // -- Phase 1-A: inline style builders for title / subtitle (v2.6.0) --------

    /** Builds inline CSS style string for <h1> title element. */
    private String buildTitleStyle() {
        StringBuilder sb = new StringBuilder();
        if (!o.style.titleSize.isEmpty())  sb.append("font-size:").append(o.style.titleSize).append("px;");
        if (!o.style.titleColor.isEmpty()) sb.append("color:").append(o.style.titleColor).append(";");
        return sb.toString();
    }

    /** Builds inline CSS style string for subtitle <p> element. */
    private String buildSubtitleStyle() {
        StringBuilder sb = new StringBuilder();
        if (!o.style.subtitleSize.isEmpty())  sb.append("font-size:").append(o.style.subtitleSize).append("px;");
        if (!o.style.subtitleColor.isEmpty()) sb.append("color:").append(o.style.subtitleColor).append(";");
        return sb.toString();
    }

    // -- Shared utilities (used by all renderers) ------------------------------

    String animDuration() {
        // animduration() takes precedence -- exact ms value (Phase 1-C v3.0.3)
        if (!o.chart.animduration.isEmpty()) return o.chart.animduration;
        // Fallback: coarse animate(none|fast|slow) setting
        if (o.chart.animate.equals("none")) return "0";
        if (o.chart.animate.equals("slow")) return "1500";
        if (o.chart.animate.equals("fast")) return "150";
        return "400";
    }

    // -- Annotation helper (v3.5.0) -------------------------------------------
    /**
     * Returns true if any annotation option was supplied by the user.
     * Used to decide whether to load chartjs-plugin-annotation CDN/offline lib.
     * Checks all 7 annotation inputs: yline, xline, yband, xband,
     * apoint, alabelpos, aellipse.
     */
    boolean hasAnnotations() {
        // v3.5.37: delegates to DashboardOptions.hasAnnotations() -- single source of truth.
        // To add a new annotation type, update DashboardOptions.hasAnnotations() only.
        return o.hasAnnotations();
    }

    String labelColor()   { return isDark() ? "#cccccc" : "#333333"; }

    String gridCssColor() {
        String gc  = resolve(o.chart.gridcolor, isDark() ? "255,255,255" : "0,0,0"); // v3.5.37: use isDark() (compound theme fix)
        double op  = parseDouble(o.chart.gridopacity, 0.15);
        if (gc.startsWith("#")) {
            int[] rgb = hexToRgb(gc);
            if (rgb != null) return "rgba("+rgb[0]+","+rgb[1]+","+rgb[2]+","+op+")";
        }
        if (!gc.contains("(")) return "rgba("+gc+","+op+")";
        return gc;
    }

    String resolve(String v, String def) { return (v==null||v.isEmpty()) ? def : v; }
    double parseDouble(String s, double def) { try{return Double.parseDouble(s);}catch(Exception e){return def;} }
    String sdz(String s) { return (s!=null&&s.endsWith(".0")) ? s.substring(0,s.length()-2) : (s==null?"":s); }
    private String fmt(double v) { return String.format(Locale.ROOT, "%.4f",v); }
    /** v1.5: Format stat value - uses comma separator and up to 2 decimal places. */
    // v3.6.0-t2i (hardening): escape the single quote too -- attribute values in
    // this generator are single-quoted (style='...', id='...'), so an apostrophe
    // in a user title/label could otherwise inject an attribute (Astra's
    // onpointerenter finding). &#39; is safe inside both single- and double-quoted
    // attributes and in element text.
    String escHtml(String s) {
        if(s==null)return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }
    // v3.6.0-t2i (hardening): a JS string literal sits inside an inline <script>,
    // so the HTML parser still ends the script at a literal </script> regardless
    // of JS quoting. Escaping '<' to \x3C (valid JS, decodes to '<') neutralises
    // </script> breakout. U+2028/U+2029 are JS line terminators that end a string
    // literal -- escape them too. \r is dropped as before.
    String escJs(String s) {
        if(s==null)return "";
        return s.replace("\\","\\\\").replace("'","\\'")
                .replace("<","\\x3C")
                .replace("\n","\\n").replace("\r","")
                .replace(String.valueOf((char)0x2028),"\\u2028")
                .replace(String.valueOf((char)0x2029),"\\u2029");
    }

    // -- Download buttons (v2.7.0) -------------------------------------------
    /**
     * Emits the PNG + SVG download button group for a given canvas id.
     * Positioned absolute top-right inside .chart-wrapper (position:relative).
     * Buttons call _spkDownload(canvasId, 'png'|'svg') defined in buildDownloadJs().
     */
    private String buildDownloadButtons(String canvasId) {
        // v2.7.2: flow-positioned toolbar row above canvas -- replaces position:absolute overlay.
        // Sits in document flow between panel-title (or top of chart-wrapper) and canvas.
        // flex row, right-aligned with "Export:" label, theme-aware colours.
        boolean dark = isDark(); // v3.5.37: use isDark() so compound themes work
        String btnBg     = dark ? "#2a2a4a" : "#f4f4f4";
        String btnColor  = dark ? "#d0d0e8" : "#444444";
        String btnBorder = dark ? "#44447a" : "#c8c8c8";
        String btnHover  = dark ? "#3a3a6a" : "#e0e0e0";
        String toolbarStyle = "display:flex;justify-content:flex-end;align-items:center;"
            + "gap:5px;margin-bottom:6px;";
        String labelStyle = "font-size:10px;color:" + (dark ? "#888899" : "#aaaaaa")
            + ";margin-right:2px;font-family:inherit;";
        String btnStyle = "background:" + btnBg + ";color:" + btnColor
            + ";border:1px solid " + btnBorder
            + ";border-radius:3px;padding:2px 9px;font-size:10px;font-weight:600;"
            + "cursor:pointer;font-family:inherit;letter-spacing:0.03em;"
            + "transition:background 0.12s,border-color 0.12s;";
        return "<div class='spk-toolbar' style='" + toolbarStyle + "'>"   // t2j fix9c: class so print/PNG scopes can hide the whole row (the "Export:" label leaked)
            + "<span style='" + labelStyle + "'>Export:</span>"
            + "<button style='" + btnStyle + "'"
            +   " onmouseover=\"this.style.background='" + btnHover + "'\""
            +   " onmouseout=\"this.style.background='"  + btnBg    + "'\""
            +   " onclick=\"_spkDownload('" + canvasId + "','png')\">PNG</button>"
            // s9l: vector export (SVG opens in Illustrator, Inkscape, Word, LibreOffice; convert to PDF/EPS from there)
            + "<button style='" + btnStyle + "' title='Vector (SVG) -- scalable, editable'"
            +   " onmouseover=\"this.style.background='" + btnHover + "'\""
            +   " onmouseout=\"this.style.background='"  + btnBg    + "'\""
            +   " onclick=\"_spkDownload('" + canvasId + "','svg')\">SVG</button>"
            // s9m: whole-page PDF via the browser's print dialog; charts are swapped for their
            // SVG on beforeprint so the PDF is vector, tables print as real text
            // fix9k: the chart toolbar's PDF is the CHART (title, context line, chart, keys), like its
            // PNG and SVG neighbours -- the whole page (stats panel, tables) ran to several pages
            // (Fahad). Whole-page PDF stays available through the browser's own Print (Ctrl+P);
            // the publication table keeps its own PDF button (table scope).
            + "<button style='" + btnStyle + "' title='Chart as PDF (vector) via Print; use the browser Print command for the whole page'"
            +   " onmouseover=\"this.style.background='" + btnHover + "'\""
            +   " onmouseout=\"this.style.background='"  + btnBg    + "'\""
            +   " onclick=\"" + (canvasId.equals("page") ? "_spkDownload('page','pdf')" : "_spkPrintOnly('chart')") + "\">PDF</button>"
            + "</div>";
    }

    /**
     * Emits the _spkDownload(canvasId, fmt) JS function once per page.
     * PNG: canvas.toBlob -> anchor click.
     * SVG: re-draws chart into a C2S (canvas2svg) context -> serialise -> anchor click.
     *
     * SVG robustness notes (v2.7.2):
     *   - canvas2svg SVG element .width/.height return SVGAnimatedLength objects,
     *     not plain numbers. Chart.js reads ctx.canvas.width as a number; getting
     *     an object produces NaN in arithmetic -> clearRect/geometry broken -> empty SVG.
     *     Fix: after C2S construction, patch c2sCtx.canvas.width/height with
     *     Object.defineProperty so they return plain integers.
     *   - Only swap chartInst.ctx (canvas2svg has no real .canvas). v2.7.1
     *   - try/catch/finally: errors are logged to console AND alerted to user.
     *     finally guarantees ctx restore even on throw. v2.7.1
     *   - Use offsetWidth/Height for CSS-pixel viewport. v2.7.1
     */
    /**
     * Builds JS preamble that creates gradient variables (_grad0, _grad1, ...)
     * for use as backgroundColor in chart datasets when o.style.gradient=true.
     *
     * Gradient strategy:
     *   - Area charts: solid color at top (y=0) -> transparent at bottom (y=canvasHeight)
     *   - Bar charts:  solid color at top        -> 60% opacity at bottom (subtle depth)
     *   - Line-only, scatter, pie/donut, histogram, boxplot/violin: gradient skipped
     *
     * Each gradient is named _grad{i} where i is the series (dataset) index.
     * The preamble is emitted once per chart canvas before the new Chart(...) call.
     * Called from ChartRenderer.buildChartScript() when gradient=true.
     *
     * nSeries: number of datasets in the chart (one gradient per series color).
     * canvasId: the canvas element id, used to get the 2d context.
     * v3.2.1
     */
    /**
     * Builds JS for gradient fills on area/bar charts.
     *
     * Strategy (v3.2.1): define a _buildGrads(chart) function BEFORE new Chart(),
     * then call it from animation.onComplete so gradients are created after the
     * canvas has real pixel dimensions (chart.chartArea.bottom > 0).
     * The datasets reference window._grad0, _grad1, ... as backgroundColor.
     * onComplete calls chart.update('none') to repaint with real gradients.
     *
     * nSeries: number of gradient objects needed.
     * canvasId: canvas element id (used to get 2d context for createLinearGradient).
     * v3.2.1
     */
    String buildGradientPreamble(int nSeries, String canvasId, boolean colorByCategory) {
        if (o.style.gradient.isEmpty()) return "";
        boolean isArea = o.chart.fill;
        boolean isBar  = o.type.equals("bar");
        if (!isArea && !isBar) return "";

        // Parse per-series custom color pairs.
        // gradient string is either "1" (auto), "start|end" (all series same),
        // or "s0start|s0end : s1start|s1end : ..." (per-series, colon-delimited).
        // pairs[i] = {start, end} or null for auto-palette fallback.
        String[] seriesPairs_start = null;
        String[] seriesPairs_end   = null;
        if (!o.style.gradient.equals("1")) {
            // Split on colon to get per-series segments
            String[] segments = o.style.gradient.split(":", -1);
            seriesPairs_start = new String[segments.length];
            seriesPairs_end   = new String[segments.length];
            for (int si = 0; si < segments.length; si++) {
                String[] gparts = segments[si].split("\\|", -1);
                if (gparts.length == 2
                        && !gparts[0].trim().isEmpty()
                        && !gparts[1].trim().isEmpty()) {
                    seriesPairs_start[si] = gparts[0].trim();
                    seriesPairs_end[si]   = gparts[1].trim();
                }
                // if malformed, leave as null -> auto-palette for that series
            }
        }

        StringBuilder sb = new StringBuilder();
        // Define _buildGrads(chart) -- called from animation.onComplete
        sb.append("var _gradsBuilt_").append(canvasId).append("=false;\n");
        sb.append("function _buildGrads_").append(canvasId).append("(chart){\n");
        sb.append("  if(_gradsBuilt_").append(canvasId).append(")return;\n");
        sb.append("  _gradsBuilt_").append(canvasId).append("=true;\n");
        sb.append("  var _gc=document.getElementById('").append(canvasId).append("');\n");
        sb.append("  if(!_gc)return;\n");
        sb.append("  var _gctx=_gc.getContext('2d');\n");
        // Use chart.chartArea.bottom for the actual rendered plot height
        sb.append("  var _gh=(chart&&chart.chartArea)?chart.chartArea.bottom:(_gc.offsetHeight||_gc.height||300);\n");

        for (int i = 0; i < nSeries; i++) {
            // Resolve per-series pair: use pairs[i] if available, else pairs[last], else null (auto)
            String customStart = null, customEnd = null;
            if (seriesPairs_start != null && seriesPairs_start.length > 0) {
                int pi = Math.min(i, seriesPairs_start.length - 1);
                customStart = seriesPairs_start[pi];
                customEnd   = seriesPairs_end[pi];
            }
            if (customStart != null) {
                sb.append("  var _g").append(i)
                  .append("=_gctx.createLinearGradient(0,0,0,_gh);\n");
                sb.append("  _g").append(i)
                  .append(".addColorStop(0,'").append(customStart).append("');\n");
                sb.append("  _g").append(i)
                  .append(".addColorStop(1,'").append(customEnd).append("');\n");
                sb.append("  window._grad").append(i).append("=_g")
                  .append(i).append(";\n");
            } else {
                String solid = colS(i);
                int[] rgb = null;
                try {
                    if (solid.startsWith("rgba(")) {
                        String inner = solid.substring(5, solid.length()-1);
                        String[] cp = inner.split(",");
                        rgb = new int[]{ Integer.parseInt(cp[0].trim()),
                                         Integer.parseInt(cp[1].trim()),
                                         Integer.parseInt(cp[2].trim()) };
                    }
                } catch (Exception ignore) {}

                if (rgb == null) {
                    sb.append("  window._grad").append(i)
                      .append("='").append(solid).append("';\n");
                    continue;
                }
                double bottomAlpha = isArea ? 0.0 : 0.6;
                sb.append("  var _g").append(i)
                  .append("=_gctx.createLinearGradient(0,0,0,_gh);\n");
                sb.append("  _g").append(i)
                  .append(".addColorStop(0,'rgba(").append(rgb[0]).append(",")
                  .append(rgb[1]).append(",").append(rgb[2]).append(",1)');\n");
                sb.append("  _g").append(i)
                  .append(".addColorStop(1,'rgba(").append(rgb[0]).append(",")
                  .append(rgb[1]).append(",").append(rgb[2]).append(",")
                  .append(bottomAlpha).append(")');\n");
                sb.append("  window._grad").append(i).append("=_g")
                  .append(i).append(";\n");
            }
            // Assign gradient back to the dataset backgroundColor
            // colorByCategory: 1 dataset with per-bar color array -> build array of gradients
            // Normal: one dataset per series -> assign directly
            if (colorByCategory) {
                sb.append("  if(chart&&chart.data&&chart.data.datasets&&chart.data.datasets[0])")
                  .append("chart.data.datasets[0].backgroundColor[").append(i)
                  .append("]=window._grad").append(i).append(";\n");
            } else {
                sb.append("  if(chart&&chart.data&&chart.data.datasets&&chart.data.datasets[")
                  .append(i).append("])chart.data.datasets[").append(i)
                  .append("].backgroundColor=window._grad").append(i).append(";\n");
            }
        }
        // update with no animation to repaint using real gradients
        sb.append("  chart.update('none');\n");
        sb.append("}\n");
        // Pre-initialize _grad{i} to null so dataset backgroundColor references
        // are defined (not undefined) when new Chart() first parses the config.
        // onComplete will replace them with real CanvasGradient objects.
        for (int i = 0; i < nSeries; i++) {
            sb.append("window._grad").append(i).append("='rgba(0,0,0,0)';\n");
        }
        return sb.toString();
    }

    String buildDownloadJs(String plotCl) {
        if (!o.chart.download) return "";
        String rawTitle = o.title.isEmpty() ? "sparkta_chart" : o.title;
        // v3.5.108: composite chart canvas onto a background-filled offscreen canvas
        // before toBlob(). canvas.toBlob() captures only the canvas pixels, which
        // are transparent by default. The dark/light background is applied to the
        // surrounding .chart-wrapper div via CSS, not painted onto the canvas.
        // Without compositing, PNG export always shows white (transparent -> white
        // in most image viewers), even on dark theme.
        // Fix: create an offscreen canvas, fill it with the chart background color,
        // drawImage the chart canvas on top, then toBlob() the composite.
        String bgColor = escJs(plotCl);
        return
            // s9k: read every key item on the page that belongs to this chart (or to the whole
            // page when the key is shared above a by() grid)
            "function _spkKeyItems(canvas){\n"
            + "  var wrap=canvas.closest?canvas.closest('.chart-wrapper'):null;\n"
            + "  var keys=[];if(wrap)keys=Array.prototype.slice.call(wrap.querySelectorAll('.spk-shared-key'));\n"
            + "  if(!keys.length)keys=Array.prototype.slice.call(document.querySelectorAll('.spk-shared-key'));\n"
            + "  var out=[];keys.forEach(function(k){Array.prototype.slice.call(k.querySelectorAll('.k')).forEach(function(el){\n"
            + "    var svg=el.querySelector('svg'),sw=el.querySelector('.sw'),dot=el.querySelector('.dot');\n"
            + "    var kind='swatch',dash=null,shape=null;\n"
            + "    if(svg){var ln=svg.querySelector('line[stroke-dasharray]')||svg.querySelector('line');var poly=svg.querySelector('polygon'),circ=svg.querySelector('circle'),rect=svg.querySelector('rect'),path=svg.querySelector('path');\n"
            + "      if(svg.querySelectorAll('line').length>=3)kind='whisker';else if(circ&&circ.getAttribute('fill')==='none')kind='outlier';else if(rect&&rect.getAttribute('fill')==='none')kind='hollowbox';else if(ln&&!poly&&!rect&&!path)kind='line';else kind='point';\n"
            + "      var swidth=ln?parseFloat(ln.getAttribute('stroke-width')||'1.5'):1.5;\n"
            + "      if(ln&&ln.getAttribute('stroke-dasharray'))dash=ln.getAttribute('stroke-dasharray').split(/\\s+/).map(Number);\n"
            + "      if(poly)shape=poly.getAttribute('points');if(rect&&kind==='point')shape='rect';if(path&&kind==='point')shape='cross';if(circ&&kind==='point')shape='circle';\n"
            + "      if(svg.querySelectorAll('svg > line').length===2&&svg.querySelectorAll('polygon').length===0)kind='line';\n"
            + "    } else if(dot){kind='dot';} else if(sw){kind='swatch';}\n"
            + "    out.push({role:el.getAttribute('data-role')||'',color:el.getAttribute('data-color')||'#666',label:(el.textContent||'').trim(),kind:kind,dash:dash,shape:shape,border:dot?getComputedStyle(dot).borderColor:'',lw:(typeof swidth==='number'?swidth:1.5)});\n"
            + "  });});return out;\n"
            + "}\n"
            + "function _spkDrawKey(ctx,items,x,y,dpr,textColor){\n"
            + "  ctx.save();ctx.font=(11*dpr)+'px sans-serif';ctx.textBaseline='middle';\n"
            + "  items.forEach(function(it){\n"
            + "    var c=it.color||'#666';ctx.strokeStyle=c;ctx.fillStyle=c;ctx.lineWidth=2*dpr;ctx.setLineDash(it.dash?it.dash.map(function(v){return v*dpr;}):[]);\n"
            + "    var w=0;\n"
            + "    if(it.kind==='line'){ctx.beginPath();ctx.moveTo(x,y);ctx.lineTo(x+26*dpr,y);ctx.stroke();w=26*dpr;}\n"
            + "    else if(it.kind==='whisker'){ctx.setLineDash([]);ctx.lineWidth=(it.lw||1.5)*dpr;ctx.beginPath();ctx.moveTo(x+6*dpr,y-7*dpr);ctx.lineTo(x+6*dpr,y+7*dpr);ctx.moveTo(x+2*dpr,y-7*dpr);ctx.lineTo(x+10*dpr,y-7*dpr);ctx.moveTo(x+2*dpr,y+7*dpr);ctx.lineTo(x+10*dpr,y+7*dpr);ctx.stroke();w=12*dpr;}\n"
            + "    else if(it.kind==='outlier'){ctx.setLineDash([]);ctx.lineWidth=1.5*dpr;ctx.beginPath();ctx.arc(x+6*dpr,y,4*dpr,0,Math.PI*2);ctx.stroke();w=12*dpr;}\n"
            + "    else if(it.kind==='hollowbox'){ctx.setLineDash([]);ctx.lineWidth=1.5*dpr;ctx.strokeRect(x+1*dpr,y-5*dpr,15*dpr,10*dpr);w=17*dpr;}\n"
            + "    else if(it.kind==='dot'){ctx.setLineDash([]);ctx.beginPath();ctx.arc(x+6*dpr,y,4.5*dpr,0,Math.PI*2);ctx.fill();if(it.border){ctx.strokeStyle=it.border;ctx.lineWidth=1.5*dpr;ctx.stroke();}w=12*dpr;}\n"
            + "    else if(it.kind==='point'){ctx.setLineDash([]);var r=5*dpr,cx=x+6*dpr;ctx.beginPath();\n"
            + "      if(it.shape==='rect'){ctx.fillRect(cx-r,y-r,2*r,2*r);}\n"
            + "      else if(it.shape==='cross'){ctx.lineWidth=2*dpr;ctx.moveTo(cx-r,y-r);ctx.lineTo(cx+r,y+r);ctx.moveTo(cx+r,y-r);ctx.lineTo(cx-r,y+r);ctx.stroke();}\n"
            + "      else if(it.shape&&it.shape.indexOf(',')>0){var pts=it.shape.trim().split(/\\s+/).map(function(p){var q=p.split(',');return [parseFloat(q[0]),parseFloat(q[1])];});var sc=(2*r)/13;pts.forEach(function(p,i){var px=cx-r+p[0]*sc,py=y-r+p[1]*sc;if(i===0)ctx.moveTo(px,py);else ctx.lineTo(px,py);});ctx.closePath();ctx.fill();}\n"
            + "      else{ctx.arc(cx,y,r,0,Math.PI*2);ctx.fill();}\n"
            + "      w=12*dpr;}\n"
            + "    else{ctx.setLineDash([]);ctx.fillRect(x,y-6*dpr,14*dpr,12*dpr);w=14*dpr;}\n"
            + "    ctx.setLineDash([]);ctx.fillStyle=textColor;ctx.fillText(it.label,x+w+6*dpr,y);\n"
            + "    x+=w+6*dpr+ctx.measureText(it.label).width+18*dpr;\n"
            + "  });ctx.restore();\n"
            + "}\n"
            // s9l: VECTOR export. Chart.js 4 needs three canvas methods canvas2svg 1.0.19 lacks
            // (resetTransform, setLineDash/getLineDash, roundRect) plus writable canvas
            // width/height -- with those shimmed, a fresh Chart drawn into the C2S context
            // yields a true SVG (paths + text), including every sparkta plugin (they draw
            // through the same ctx). Proven headlessly on node-canvas + librsvg (s9l).
            // fix9k: noHead=true (the print swap) leaves the page title and context line out --
            // the printed page already shows them above the chart (and once per by() panel it
            // would repeat the PAGE title inside every panel). The SVG file export keeps them.
            // fix9u (decision 6): an export clone was drawn TWICE -- new Chart() with
            // responsive:false + animation:false already renders synchronously (Chart.js 4:
            // bindEvents sets attached, the constructor calls update(), render() calls draw()
            // when the animator holds nothing), and the explicit tmp.draw() that followed drew
            // everything again into a C2S context whose clearRect is a no-op. Every path was
            // emitted twice, so translucent fills (CI bands, areas, bubbles) came out darker in
            // SVG/PDF than on screen. _spkExportChart draws only when the constructor did not
            // (e.g. a plugin registered an animation, so render() went to the animator).
            + "function _spkExportChart(c2s,c2){var g=c2s.__currentElement,n0=g?g.childNodes.length:-1;var tmp=new Chart(c2s,c2);\n"
            + "  if(n0<0||(c2s.__currentElement===g&&g.childNodes.length===n0))tmp.draw();return tmp;}\n"
            + "function _spkChartToSvg(chartInst,keyItems,noHead){\n"
            + "  var cw=chartInst.canvas.offsetWidth||chartInst.width,ch=chartInst.canvas.offsetHeight||chartInst.height;\n"
            + "  var keyH=keyItems.length?28:0;\n"
            // s9x: the title and context line are HTML, not canvas -- draw them into the vector
            + "  var h1=noHead?null:document.querySelector('h1'),sub=noHead?null:document.querySelector('.subtitle2');\n"
            + "  var tTitle=h1?(h1.textContent||'').trim():'',tSub=sub?(sub.textContent||'').trim():'';\n"
            + "  var tFil=(noHead||typeof _spkActiveFilters!=='function')?'':_spkActiveFilters();\n"   // fix9n: active filters line
            + "  var headH=(tTitle?30:0)+(tSub?20:0)+(tFil?18:0)+(tTitle||tSub||tFil?10:0);\n"
            // t1j: a FIGURE has margins. On screen the card padding provides them; the export must
            // provide its own: outer margin M around everything, a background, and a little
            // layout padding inside the chart so the plot border never coincides with the edge.
            + "  var M=18;\n"
            + "  var ctx=new C2S({document:document,width:cw+2*M,height:ch+keyH+headH+2*M});\n"
            + "  ctx.fillStyle='" + (isDark() ? "#1a1a2e" : "#ffffff") + "';ctx.fillRect(0,0,cw+2*M,ch+keyH+headH+2*M);\n"
            + "  ctx.translate(M,M);\n"
            + "  if(headH){ctx.save();ctx.fillStyle='" + (isDark() ? "#e8e8f0" : "#2c3e50") + "';ctx.textBaseline='alphabetic';\n"
            + "    if(tTitle){ctx.font='bold 18px sans-serif';ctx.fillText(tTitle,0,20);}\n"
            + "    if(tSub){ctx.fillStyle='" + (isDark() ? "#a8a8c0" : "#777777") + "';ctx.font='12px sans-serif';ctx.fillText(tSub,0,tTitle?40:12);}\n"
            + "    if(tFil){ctx.fillStyle='" + (isDark() ? "#a8a8c0" : "#777777") + "';ctx.font='12px sans-serif';ctx.fillText(tFil,0,(tTitle?30:0)+(tSub?20:0)+12);}\n"
            + "    ctx.restore();ctx.translate(0,headH);}\n"
            + "  ctx.clearRect=function(){};ctx.resetTransform=function(){};\n"
            + "  if(!ctx.setLineDash){var _ld=[];ctx.setLineDash=function(d){_ld=d||[];};ctx.getLineDash=function(){return _ld;};}\n"
            + "  if(!ctx.roundRect)ctx.roundRect=function(x,y,w,h){this.rect(x,y,w,h);};\n"
            + "  var _w=cw,_h=ch;Object.defineProperty(ctx.canvas,'width',{get:function(){return _w;},set:function(v){_w=v;},configurable:true});\n"
            + "  Object.defineProperty(ctx.canvas,'height',{get:function(){return _h;},set:function(v){_h=v;},configurable:true});\n"
            + "  ctx.canvas.style={};ctx.canvas.getContext=function(){return ctx;};\n"
            + "  var cfg=chartInst.config;\n"
            + "  var deep=function(o){if(o===null||typeof o!=='object')return o;if(Array.isArray(o))return o.map(deep);var r={};for(var k in o)r[k]=(typeof o[k]==='function')?o[k]:deep(o[k]);return r;};\n"
            + "  var o2=deep(cfg.options);o2.responsive=false;o2.animation=false;o2.maintainAspectRatio=false;o2.devicePixelRatio=1;\n"
            // t1m: browsers have Path2D, so Chart.js strokes line datasets via ctx.stroke(path) --
            // which canvas2svg ignores (it strokes its own, empty, path): every line vanished from
            // browser-made SVGs (fit lines, type(line) series). A 'segment' option forces Chart.js
            // onto the direct moveTo/lineTo branch; an empty one changes nothing visually.
            + "  o2.elements=o2.elements||{};o2.elements.line=o2.elements.line||{};if(o2.elements.line.segment===undefined)o2.elements.line.segment={};\n"
            + "  (cfg.data.datasets||[]).forEach(function(d){if(d&&d.segment===undefined&&(d.type==='line'||cfg.type==='line'||d.showLine))d.segment={};});\n"
            + "  o2.layout=o2.layout||{};var lp=o2.layout.padding;if(typeof lp!=='object'||lp===null)lp={top:lp||0,right:lp||0,bottom:lp||0,left:lp||0};\n"
            + "  o2.layout.padding={top:Math.max(lp.top||0,8),right:Math.max(lp.right||0,14),bottom:Math.max(lp.bottom||0,6),left:Math.max(lp.left||0,6)};\n"
            + "  var c2={type:cfg.type,data:cfg.data,options:o2,plugins:cfg.plugins||[]};\n"
            + "  if(window.Chart&&Chart.platforms&&Chart.platforms.BasicPlatform)c2.platform=Chart.platforms.BasicPlatform;\n"
            // fix9h-e: page geometry for spkBigScatter's export raster (page-sized, page-aligned)
            + "  ctx.__spkPage={x:M,y:M+headH,w:cw+2*M,h:ch+keyH+headH+2*M};\n"
            + "  var tmp=_spkExportChart(ctx,c2);\n"   // fix9u: one draw, not two
            + "  if(keyItems.length)_spkDrawKey(ctx,keyItems,4,ch+14,1,'" + (isDark() ? "#d0d0e0" : "#333333") + "');\n"
            + "  if(headH)ctx.translate(0,-headH);ctx.translate(-M,-M);\n"
            + "  var svg=ctx.getSerializedSvg(true);try{tmp.destroy();}catch(e){}\n"
            + "  return svg;\n"
            + "}\n"
            // t1l: page-level SVG. A by() page has several canvases: draw each panel (with its
            // caption) into one figure laid out like the screen grid, shared key above.
            + "function _spkPageToSvg(){\n"
            + "  var main=document.getElementById('mainChart');\n"
            + "  if(main){var inst=(window.Chart&&Chart.getChart)?Chart.getChart(main):null;if(!inst)throw new Error('chart not ready');return _spkChartToSvg(inst,_spkKeyItems(main));}\n"
            + "  var cvs=Array.prototype.slice.call(document.querySelectorAll('canvas')).filter(function(c){return window.Chart&&Chart.getChart&&Chart.getChart(c);});\n"
            + "  if(!cvs.length)throw new Error('no chart on the page');\n"
            + "  var insts=cvs.map(function(c){return Chart.getChart(c);});\n"
            + "  var pw=cvs[0].offsetWidth||insts[0].width,ph=cvs[0].offsetHeight||insts[0].height;\n"
            + "  var cols=Math.max(1,Math.min(cvs.length,Math.floor((document.body.clientWidth||1100)/Math.max(300,pw))));if(cols<2&&cvs.length>1)cols=2;\n"
            + "  if(document.querySelector('.panels-keep'))cols=1;\n"   // fix9f: an explicit layout(vertical) exports stacked on the SVG route too
            + "  var rows=Math.ceil(cvs.length/cols),M=18,G=16,capH=22;\n"
            + "  var h1=document.querySelector('h1'),sub=document.querySelector('.subtitle2');var tTitle=h1?(h1.textContent||'').trim():'',tSub=sub?(sub.textContent||'').trim():'';\n"
            + "  var tFil=(typeof _spkActiveFilters==='function')?_spkActiveFilters():'';\n"   // fix9n
            + "  var headH=(tTitle?30:0)+(tSub?20:0)+(tFil?18:0)+(tTitle||tSub||tFil?10:0);\n"
            + "  var keyItems=_spkKeyItems(cvs[0]);var keyH=keyItems.length?28:0;\n"
            + "  var W=cols*pw+(cols-1)*G,Hh=rows*(ph+capH)+(rows-1)*G;\n"
            + "  var ctx=new C2S({document:document,width:W+2*M,height:Hh+headH+keyH+2*M});\n"
            + "  ctx.clearRect=function(){};ctx.resetTransform=function(){};if(!ctx.setLineDash){var _ld=[];ctx.setLineDash=function(d){_ld=d||[];};ctx.getLineDash=function(){return _ld;};}if(!ctx.roundRect)ctx.roundRect=function(x,y,w,h){this.rect(x,y,w,h);};\n"
            + "  var _w=W,_h=Hh;Object.defineProperty(ctx.canvas,'width',{get:function(){return _w;},set:function(v){_w=v;},configurable:true});Object.defineProperty(ctx.canvas,'height',{get:function(){return _h;},set:function(v){_h=v;},configurable:true});ctx.canvas.style={};ctx.canvas.getContext=function(){return ctx;};\n"
            + "  ctx.fillStyle='" + (isDark() ? "#1a1a2e" : "#ffffff") + "';ctx.fillRect(0,0,W+2*M,Hh+headH+keyH+2*M);ctx.translate(M,M);\n"
            + "  if(headH){ctx.save();ctx.fillStyle='" + (isDark() ? "#e8e8f0" : "#2c3e50") + "';ctx.textBaseline='alphabetic';if(tTitle){ctx.font='bold 18px sans-serif';ctx.fillText(tTitle,0,20);}if(tSub){ctx.fillStyle='" + (isDark() ? "#a8a8c0" : "#777777") + "';ctx.font='12px sans-serif';ctx.fillText(tSub,0,tTitle?40:12);}if(tFil){ctx.fillStyle='" + (isDark() ? "#a8a8c0" : "#777777") + "';ctx.font='12px sans-serif';ctx.fillText(tFil,0,(tTitle?30:0)+(tSub?20:0)+12);}ctx.restore();ctx.translate(0,headH);}\n"
            + "  if(keyItems.length){_spkDrawKey(ctx,keyItems,4,12,1,'" + (isDark() ? "#d0d0e0" : "#333333") + "');ctx.translate(0,keyH);}\n"
            + "  for(var i=0;i<insts.length;i++){var col=i%cols,row=Math.floor(i/cols);var x0=col*(pw+G),y0=row*(ph+capH+G);\n"
            + "    var cap='';var wrap=cvs[i].closest?cvs[i].closest('.chart-wrapper'):null;var pt=wrap?wrap.querySelector('.panel-title'):null;if(pt){var pn=pt.querySelector('.panel-n');cap=(pt.textContent||'').replace(pn?pn.textContent:'','').trim()+(pn?'   '+pn.textContent.trim():'');}\n"
            + "    ctx.save();ctx.translate(x0,y0);ctx.fillStyle='" + (isDark() ? "#e8e8f0" : "#2c3e50") + "';ctx.font='bold 13px sans-serif';ctx.textBaseline='alphabetic';if(cap)ctx.fillText(cap,0,14);ctx.translate(0,capH);\n"
            + "    var cfg=insts[i].config;var deep=function(o){if(o===null||typeof o!=='object')return o;if(Array.isArray(o))return o.map(deep);var r={};for(var k in o)r[k]=(typeof o[k]==='function')?o[k]:deep(o[k]);return r;};\n"
            + "    var o2=deep(cfg.options);o2.responsive=false;o2.animation=false;o2.maintainAspectRatio=false;o2.devicePixelRatio=1;o2.layout=o2.layout||{};o2.layout.padding={top:8,right:14,bottom:6,left:6};\n"
            + "    o2.elements=o2.elements||{};o2.elements.line=o2.elements.line||{};if(o2.elements.line.segment===undefined)o2.elements.line.segment={};(cfg.data.datasets||[]).forEach(function(d){if(d&&d.segment===undefined&&(d.type==='line'||cfg.type==='line'||d.showLine))d.segment={};});\n"
            + "    var sub2=new C2S({document:document,width:pw,height:ph});sub2.clearRect=function(){};sub2.resetTransform=function(){};if(!sub2.setLineDash){var _l2=[];sub2.setLineDash=function(d){_l2=d||[];};sub2.getLineDash=function(){return _l2;};}if(!sub2.roundRect)sub2.roundRect=function(x,y,w,h){this.rect(x,y,w,h);};\n"
            + "    var _pw=pw,_ph=ph;Object.defineProperty(sub2.canvas,'width',{get:function(){return _pw;},set:function(v){_pw=v;},configurable:true});Object.defineProperty(sub2.canvas,'height',{get:function(){return _ph;},set:function(v){_ph=v;},configurable:true});sub2.canvas.style={};sub2.canvas.getContext=function(){return sub2;};\n"
            + "    var c2={type:cfg.type,data:cfg.data,options:o2,plugins:cfg.plugins||[]};if(window.Chart&&Chart.platforms&&Chart.platforms.BasicPlatform)c2.platform=Chart.platforms.BasicPlatform;\n"
            + "    sub2.__spkPage={x:x0+M,y:y0+capH+M+headH+keyH,w:W+2*M,h:Hh+headH+keyH+2*M};\n"   // fix9h-e: this panel's page offset
            + "    var tmp=_spkExportChart(sub2,c2);var inner=sub2.getSerializedSvg(true);try{tmp.destroy();}catch(e){}\n"
            + "    // splice the panel's SVG into the page SVG as a nested <svg> (same coordinate system)\n"
            + "    ctx.__spkNested=ctx.__spkNested||[];ctx.__spkNested.push({x:x0+M,y:y0+capH+M+headH+keyH,svg:inner});ctx.restore();}\n"
            + "  var page=ctx.getSerializedSvg(true);\n"
            + "  var parts=(ctx.__spkNested||[]).map(function(n){return n.svg.replace(/^<svg /,'<svg x=\"'+n.x+'\" y=\"'+n.y+'\" ').replace(/<\\?xml[^>]*>/,'');});\n"
            + "  return page.replace(/<\\/svg>\\s*$/,parts.join('')+'</svg>');\n"
            + "}\n"
            + "function _spkDownload(canvasId,fmt){\n"
            + "  var rawTitle='" + escJs(rawTitle) + "';\n"
            + "  var fname=rawTitle.replace(/[^A-Za-z0-9_\\-]/g,'_').replace(/_+/g,'_');\n"
            // fix9l: 'page' = the whole by() grid (one toolbar per page). SVG is the page SVG
            // saveas() uses; PNG rasterises that SVG at 2x through an <img> (fonts, keys and the
            // large-data raster all travel inside the SVG); PDF is the chart-scope print.
            + "  if(canvasId==='page'){\n"
            + "    if(fmt==='pdf'){_spkPrintOnly('chart');return;}\n"
            + "    var psvg;try{psvg=_spkPageToSvg();}catch(e){console.error('page export failed',e);alert('Export failed: '+e.message);return;}\n"
            + "    if(fmt==='svg'){var pb=new Blob([psvg],{type:'image/svg+xml;charset=utf-8'});var pa=document.createElement('a');pa.href=URL.createObjectURL(pb);pa.download=fname+'.svg';pa.click();setTimeout(function(){URL.revokeObjectURL(pa.href);},60000);return;}\n"
            + "    var mw=psvg.match(/<svg[^>]*\\swidth=\"([\\d.]+)\"/),mh=psvg.match(/<svg[^>]*\\sheight=\"([\\d.]+)\"/);var pw0=mw?parseFloat(mw[1]):1100,ph0=mh?parseFloat(mh[1]):700;var sc=2;\n"
            + "    var im=new Image();im.onload=function(){var oc=document.createElement('canvas');oc.width=Math.round(pw0*sc);oc.height=Math.round(ph0*sc);var oc2=oc.getContext('2d');\n"
            + "      oc2.fillStyle='" + bgColor + "';oc2.fillRect(0,0,oc.width,oc.height);oc2.drawImage(im,0,0,oc.width,oc.height);\n"
            + "      oc.toBlob(function(blob){var a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=fname+'.png';a.click();setTimeout(function(){URL.revokeObjectURL(a.href);},60000);},'image/png');};\n"
            + "    im.onerror=function(){alert('PNG export failed: the page SVG did not render');};\n"
            + "    im.src='data:image/svg+xml;charset=utf-8,'+encodeURIComponent(psvg);return;\n"
            + "  }\n"
            + "  var canvas=document.getElementById(canvasId);\n"
            + "  if(!canvas){console.error('_spkDownload: canvas not found: '+canvasId);return;}\n"
            + "  if(fmt==='png'){\n"
            // s9k: compose the page keys (shared key + elements key) into the export, so the
            // PNG carries the same legend the page shows. Items are read from the DOM
            // (data-role / data-color / SVG glyphs), drawn with canvas primitives.
            + "    var keyItems=_spkKeyItems(canvas);\n"
            + "    var dpr=canvas.width/Math.max(1,canvas.offsetWidth||canvas.width);\n"
            + "    var keyH=keyItems.length?Math.ceil(28*dpr):0;\n"
            // fix9k (Fahad: "PNG export does not show the title and subtitle"): the page title and
            // context line are HTML, not canvas. Draw them above the chart exactly as the SVG
            // export does (bold 18px title, 12px context line, 18px figure margin), at the
            // canvas's own device ratio so the text is as crisp as the chart.
            + "    var h1=document.querySelector('h1'),sub=document.querySelector('.subtitle2');\n"
            + "    var tTitle=h1?(h1.textContent||'').trim():'',tSub=sub?(sub.textContent||'').trim():'';\n"
            + "    var tFil=(typeof _spkActiveFilters==='function')?_spkActiveFilters():'';\n"   // fix9n
            + "    var headH=Math.ceil(((tTitle?30:0)+(tSub?20:0)+(tFil?18:0)+(tTitle||tSub||tFil?10:0))*dpr),M=Math.ceil(18*dpr);\n"
            + "    var off=document.createElement('canvas');\n"
            + "    off.width=canvas.width+2*M; off.height=canvas.height+keyH+headH+2*M;\n"
            + "    var ctx=off.getContext('2d');\n"
            + "    ctx.fillStyle='" + bgColor + "';\n"
            + "    ctx.fillRect(0,0,off.width,off.height);\n"
            + "    if(headH){ctx.save();ctx.fillStyle='" + (isDark() ? "#e8e8f0" : "#2c3e50") + "';ctx.textBaseline='alphabetic';\n"
            + "      if(tTitle){ctx.font='bold '+Math.round(18*dpr)+'px sans-serif';ctx.fillText(tTitle,M,M+20*dpr);}\n"
            + "      if(tSub){ctx.fillStyle='" + (isDark() ? "#a8a8c0" : "#777777") + "';ctx.font=Math.round(12*dpr)+'px sans-serif';ctx.fillText(tSub,M,M+(tTitle?40:12)*dpr);}\n"
            + "      if(tFil){ctx.fillStyle='" + (isDark() ? "#a8a8c0" : "#777777") + "';ctx.font=Math.round(12*dpr)+'px sans-serif';ctx.fillText(tFil,M,M+((tTitle?30:0)+(tSub?20:0)+12)*dpr);}\n"
            + "      ctx.restore();}\n"
            + "    ctx.drawImage(canvas,M,M+headH);\n"
            + "    if(keyItems.length)_spkDrawKey(ctx,keyItems,M+4*dpr,M+headH+canvas.height+14*dpr,dpr,'" + (isDark() ? "#d0d0e0" : "#333333") + "');\n"
            + "    off.toBlob(function(blob){\n"
            + "      var a=document.createElement('a');\n"
            + "      a.href=URL.createObjectURL(blob);\n"
            + "      a.download=fname+'.png';\n"
            + "      a.click();\n"
            + "      setTimeout(function(){URL.revokeObjectURL(a.href);},60000);\n"
            + "    },'image/png');\n"
            + "  }\n"
            + "  if(fmt==='svg'){\n"
            + "    var inst=(window.Chart&&Chart.getChart)?Chart.getChart(canvas):null;\n"
            + "    if(!inst){alert('SVG export: chart instance not found');return;}\n"
            + "    try{var svg=_spkChartToSvg(inst,_spkKeyItems(canvas));\n"
            + "      var blob=new Blob([svg],{type:'image/svg+xml;charset=utf-8'});var a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=fname+'.svg';a.click();setTimeout(function(){URL.revokeObjectURL(a.href);},60000);\n"
            + "    }catch(e){console.error('SVG export failed',e);alert('SVG export failed: '+e.message);}\n"
            + "  }\n"
            + "}\n";
    }

    // -- Offline resource loader (v2.0.2) -----------------------------------
    /**
     * Loads a JS library bundled as a classpath resource into a String.
     * Used by buildScriptTags() when o.chart.offline=true.
     * Resource path: /com/dashboard_test/js/<filename>
     * Returns empty string if the resource is not found (build warning emitted).
     */
    /**
     * Loads a bundled JS library from the jar classpath.
     * Pre-flight check in DashboardBuilder guarantees the resource exists
     * before this is called. No CDN fallback -- Core Rule 10. v2.0.4
     */
    /**
     * F-0: Loads sparkta_engine.js from classpath resources (always bundled --
     * it is our own code, not a CDN lib). Used regardless of online/offline mode.
     */
    private String loadEngineJs() {
        String resPath = "/com/dashboard_test/js/sparkta_engine.js";
        try (java.io.InputStream is = getClass().getResourceAsStream(resPath)) {
            if (is == null) throw new RuntimeException(
                "sparkta_engine.js not found in jar. Run build to repackage.");
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[4096]; int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toString("UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("Error loading sparkta_engine.js: " + e.getMessage());
        }
    }

    private String loadResource(String filename) {
        String resPath = "/com/dashboard_test/js/" + filename;
        try (InputStream is = getClass().getResourceAsStream(resPath)) {
            if (is == null) {
                // Should never reach here after pre-flight check
                throw new RuntimeException("offline resource not found: " + resPath
                    + " -- this should have been caught by pre-flight check.");
            }
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = is.read(chunk)) != -1) buf.write(chunk, 0, n);
            return buf.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new RuntimeException("error reading offline resource " + resPath
                + ": " + e.getMessage());
        }
    }

    /**
     * Builds the <script> block(s) for Chart.js and any required plugins.
     * Online mode  (o.chart.offline=false): emits <script src='CDN_URL'> tags.
     * Offline mode (o.chart.offline=true):  inlines the JS from classpath resources.
     * v2.0.2 | v2.7.0: canvas2svg added for download option
     * v3.5.0: needsAN added for chartjs-plugin-annotation
     */
    private String buildScriptTags(boolean needsDL, boolean needsEB, boolean needsBP, boolean needsAN) {
        if (!o.chart.offline) {
            // Online mode -- CDN links (original behaviour)
            String extra = needsDL
                ? "  <script src='https://cdn.jsdelivr.net/npm/chartjs-plugin-datalabels@2.2.0/dist/chartjs-plugin-datalabels.min.js'></script>\n"
                : "";
            if (needsEB) {
                extra += "  <script src='https://cdn.jsdelivr.net/npm/chartjs-chart-error-bars@4.4.0/build/index.umd.min.js'></script>\n";
            }
            // v2.4.0: boxplot/violin plugin
            if (needsBP) {
                extra += "  <script src='https://cdn.jsdelivr.net/npm/@sgratzl/chartjs-chart-boxplot@4.4.5/build/index.umd.min.js'></script>\n";
            }
            // v3.5.0: annotation plugin -- auto-registers on load, no Chart.register() needed
            if (needsAN) {
                extra += "  <script src='https://cdn.jsdelivr.net/npm/chartjs-plugin-annotation@3.0.1/dist/chartjs-plugin-annotation.min.js'></script>\n";
            }
            return "  <script src='https://cdn.jsdelivr.net/npm/chart.js@4.4.0/dist/chart.umd.min.js'></script>\n"
                 + extra;
        }
        // Offline mode -- inline all required libraries from classpath.
        // Pre-flight check in DashboardBuilder guarantees all resources exist.
        // No CDN fallback -- offline means strictly offline (Core Rule 10). v2.0.4
        StringBuilder sb = new StringBuilder();
        sb.append("  <script>\n").append(loadResource("chartjs-4.4.0.min.js")).append("\n  </script>\n");
        if (needsDL) sb.append("  <script>\n").append(loadResource("chartjs-datalabels-2.2.0.min.js")).append("\n  </script>\n");
        if (needsEB) sb.append("  <script>\n").append(loadResource("chartjs-errorbars-4.4.0.min.js")).append("\n  </script>\n");
        if (needsBP) sb.append("  <script>\n").append(loadResource("chartjs-boxplot-4.4.5.min.js")).append("\n  </script>\n");
        if (needsAN) sb.append("  <script>\n").append(loadResource("chartjs-annotation-3.0.1.min.js")).append("\n  </script>\n");
        return sb.toString();
    }


}
