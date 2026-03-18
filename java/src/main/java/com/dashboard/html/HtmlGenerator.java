package com.dashboard.html;

import com.dashboard.data.*;
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
    public static final String VERSION = "3.5.108";

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
        switch (theme.toLowerCase().trim()) {
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
        String t = (o.theme == null) ? "" : o.theme.toLowerCase().trim();
        return t.equals("dark") || t.startsWith("dark_");
    }

    // paletteKey(): extracts the palette name from a theme string.
    //   "tab1"        -> "tab1"
    //   "dark_viridis"-> "viridis"
    //   "light_tab2"  -> "tab2"
    //   "dark"        -> ""  (background-only, no palette)
    //   "default"     -> ""
    private String paletteKey() {
        String t = (o.theme == null) ? "" : o.theme.toLowerCase().trim();
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
                seriesColors[i] = toRgba(parts[i], 0.85);
                seriesSolid[i]  = toRgba(parts[i], 1.0);
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

    // -- Entry point -----------------------------------------------------------

    public String build(DataSet data) {
        // v2.0.0: Initialise renderers -- each receives this HtmlGenerator for shared utilities
        this.dsb = new DatasetBuilder(o, this);
        this.cr  = new ChartRenderer(o, this, dsb);
        this.fr  = new FilterRenderer(o, this, dsb, cr);
        this.sr  = new StatsRenderer(o, this, dsb);

        boolean dark   = isDark();
        String  bgCol  = resolve(o.chart.bgcolor,   dark ? "#1a1a2e" : "#f8f9fa");
        String  plotCl = resolve(o.chart.plotcolor, dark ? "#16213e" : "#ffffff");

        String chartsHtml = data.hasBy()
            ? buildByPanels(data)
            : "<div class='chart-wrapper'>"
              + (o.chart.download ? buildDownloadButtons("mainChart") : "")
              + "<canvas id='mainChart'></canvas></div>\n";
        // When filters OR sliders active, chart must be assigned to _mainChart
        String scriptHtml = data.hasBy()
            ? buildByScripts(data)
            : cr.buildChartScript("mainChart", data, data.hasAnyFilter());

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

        // v3.5.36: debug comment so user can verify jar version and key flags
        return "<!DOCTYPE html>\n<!-- sparkta v" + VERSION + " fill=" + o.chart.fill
            + " stack=" + o.chart.stack + " type=" + o.type + " -->\n<html lang='en'>\n<head>\n"
            + "  <meta charset='UTF-8'>\n"
            + "  <meta name='viewport' content='width=device-width,initial-scale=1.0'>\n"
            + "  <title>" + escHtml(o.title) + "</title>\n"
            + ""
            + scriptTags
            + "  <style>" + buildCss(bgCol, plotCl, dark) + "</style>\n"
            + "</head>\n<body>\n<div class='container'>\n"
            // v2.6.0 Phase 1-A: apply titleSize/titleColor inline when user provides them
            + "  <h1 style='" + buildTitleStyle() + "'>" + escHtml(o.title) + "</h1>\n"
            + (o.subtitle.isEmpty() ? "" : "  <p class='subtitle2' style='" + buildSubtitleStyle() + "'>" + escHtml(o.subtitle) + "</p>\n")
            + "  <p class='subtitle'>Generated by Stata Dashboard &bull; " + new java.util.Date() + "</p>\n"
            + filterUi
            + chartsHtml
            + cr.buildNoteCaption()
            + sr.buildStatsSection(data)
            + "</div>\n"
            + "<script>\n" + pluginReg + filterData + histPre
            // v2.6.1: emit per-panel histogram preambles (_ttRanges_/_ttCounts_ for by() panels)
            + buildByHistPreambles()
            + scriptFinal + filterScript + sr.buildStatsJs()
            // v2.7.0: download function -- emitted only when download=1
            + buildDownloadJs(plotCl)
            + "</script>\n"
            + "</body>\n</html>";
    }

    // -- by() panels -----------------------------------------------------------

    private String buildByPanels(DataSet data) {
        Variable byVar = data.getByVariable();
        List<String> groups = DataSet.uniqueValues(byVar, o.chart.sortgroups);
        String cls = o.chart.layout.equals("horizontal") ? "panels-horizontal"
                   : o.chart.layout.equals("grid")       ? "panels-grid" : "panels-vertical";
        StringBuilder sb = new StringBuilder("<div class='"+cls+"'>\n");
        int idx = 0;
        for (String g : groups) {
            String canvasId = "chart_by_" + idx;
            sb.append("  <div class='panel-item'><div class='chart-wrapper'>\n")
              .append("    <p class='panel-title'>").append(escHtml(byVar.getDisplayName()+" = "+sdz(g))).append("</p>\n")
              .append(o.chart.download ? "    " + buildDownloadButtons(canvasId) + "\n" : "")
              .append("    <canvas id='").append(canvasId).append("'></canvas>\n")
              .append("  </div></div>\n");
            idx++;
        }
        if (o.chart.layout.equals("grid") && groups.size() % 2 != 0)
            sb.append("  <div class='panel-item panel-empty'></div>\n");
        return sb.append("</div>\n").toString();
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
            + "body{background:"+bg+";color:"+text+";font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;padding:2rem;}\n"
            + ".container{max-width:1200px;margin:0 auto;}\n"
            + "h1{font-size:1.8rem;color:"+heading+";margin-bottom:.15rem;}\n"
            + ".subtitle2{font-size:1.05rem;color:"+heading+";opacity:.75;margin-bottom:.2rem;}\n"
            + ".subtitle{font-size:.8rem;color:"+sub+";margin-bottom:1.5rem;}\n"
            + ".panels-vertical{display:flex;flex-direction:column;gap:1.5rem;margin-bottom:1.5rem;}\n"
            + ".panels-horizontal{display:flex;flex-direction:row;flex-wrap:nowrap;gap:1.5rem;margin-bottom:1.5rem;overflow-x:auto;}\n"
            + ".panels-horizontal .panel-item{flex:1 1 0;min-width:280px;}\n"
            + ".panels-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:1.5rem;margin-bottom:1.5rem;}\n"
            + ".panel-empty{border-radius:8px;min-height:200px;}\n"
            + ".chart-wrapper{background:"+plotCl+";border-radius:8px;border:1px solid "+border+";padding:1.5rem;margin-bottom:1.5rem;}\n"
            + ".panels-vertical .chart-wrapper,.panels-horizontal .chart-wrapper,.panels-grid .chart-wrapper{margin-bottom:0;}\n"
            + ".panel-title{font-size:.92rem;font-weight:600;color:"+heading+";margin-bottom:.6rem;padding:.35rem .6rem;background:"+accent+";border-radius:4px;}\n"
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
            // v3.5.5: content-visibility:auto skips layout+paint for off-screen group blocks.
            // contain-intrinsic-size reserves ~120px height so scrollbar is accurate before render.
            // Browser still parses the HTML; it just defers the expensive paint/layout pass
            // until each block scrolls near the viewport. Zero JS required.
            + ".grp{margin-top:.9rem;border:1px solid "+border+";border-radius:8px;overflow:hidden;"
            + "content-visibility:auto;contain-intrinsic-size:0 120px;}\n"
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
    private String fmt(double v) { return String.format("%.4f",v); }
    /** v1.5: Format stat value - uses comma separator and up to 2 decimal places. */
    String escHtml(String s) {
        if(s==null)return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    String escJs(String s) {
        if(s==null)return "";
        return s.replace("\\","\\\\").replace("'","\\'").replace("\n","\\n").replace("\r","");
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
        return "<div style='" + toolbarStyle + "'>"
            + "<span style='" + labelStyle + "'>Export:</span>"
            + "<button style='" + btnStyle + "'"
            +   " onmouseover=\"this.style.background='" + btnHover + "'\""
            +   " onmouseout=\"this.style.background='"  + btnBg    + "'\""
            +   " onclick=\"_spkDownload('" + canvasId + "','png')\">PNG</button>"
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
        return "function _spkDownload(canvasId,fmt){\n"
            + "  var canvas=document.getElementById(canvasId);\n"
            + "  if(!canvas){console.error('_spkDownload: canvas not found: '+canvasId);return;}\n"
            + "  var rawTitle='" + escJs(rawTitle) + "';\n"
            + "  var fname=rawTitle.replace(/[^A-Za-z0-9_\\-]/g,'_').replace(/_+/g,'_');\n"
            + "  if(fmt==='png'){\n"
            + "    var off=document.createElement('canvas');\n"
            + "    off.width=canvas.width; off.height=canvas.height;\n"
            + "    var ctx=off.getContext('2d');\n"
            + "    ctx.fillStyle='" + bgColor + "';\n"
            + "    ctx.fillRect(0,0,off.width,off.height);\n"
            + "    ctx.drawImage(canvas,0,0);\n"
            + "    off.toBlob(function(blob){\n"
            + "      var a=document.createElement('a');\n"
            + "      a.href=URL.createObjectURL(blob);\n"
            + "      a.download=fname+'.png';\n"
            + "      a.click();\n"
            + "      setTimeout(function(){URL.revokeObjectURL(a.href);},60000);\n"
            + "    },'image/png');\n"
            + "  }\n"
            + "}\n";
    }

    // -- Offline resource loader (v2.0.2) -----------------------------------
    /**
     * Loads a JS library bundled as a classpath resource into a String.
     * Used by buildScriptTags() when o.chart.offline=true.
     * Resource path: /com/dashboard/js/<filename>
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
        String resPath = "/com/dashboard/js/sparkta_engine.js";
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
        String resPath = "/com/dashboard/js/" + filename;
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
