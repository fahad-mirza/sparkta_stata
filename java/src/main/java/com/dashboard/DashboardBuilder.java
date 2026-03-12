package com.dashboard;

import com.stata.sfi.SFIToolkit;
import com.dashboard.data.StataDataReader;
import com.dashboard.data.DataSet;
import com.dashboard.html.HtmlGenerator;
import com.dashboard.html.DashboardOptions;
import com.dashboard.util.FileUtil;
import com.dashboard.util.BrowserLauncher;
import java.io.IOException;

public class DashboardBuilder {

    public static int execute(String[] args) {
        try {
            if (args.length < 151) {
                SFIToolkit.error("sparkta: insufficient arguments (" + args.length
                    + " received, 150 expected)\n");
                return SFIToolkit.RC_GENERAL_ERROR;
            }

            DashboardOptions o = new DashboardOptions();

            // 0-18 core
            o.varlist       = a(args,0);
            o.type          = a(args,1).toLowerCase();
            o.title         = a(args,2).isEmpty() ? "Dashboard" : a(args,2);
            o.theme         = a(args,3).isEmpty() ? "default"   : a(args,3);
            o.export        = a(args,4);
            o.axes.xtitle        = a(args,5);
            o.axes.ytitle        = a(args,6);
            o.over          = a(args,7);
            o.by            = a(args,8);
            o.tousename     = a(args,9);   // touse variable name (v1.6.0 - replaces obslist)
            o.chart.layout        = a(args,10).isEmpty() ? "vertical" : a(args,10);
            o.chart.bgcolor       = a(args,11);
            o.chart.plotcolor     = a(args,12);
            o.chart.gridcolor     = a(args,13);
            o.chart.gridopacity   = a(args,14).isEmpty() ? "0.15" : a(args,14);
            o.chart.colors        = a(args,15);
            o.note          = a(args,16);
            o.caption       = a(args,17);
            o.subtitle      = a(args,18);
            // 19-26 ranges/flags
            o.axes.xrangeMin     = a(args,19);
            o.axes.xrangeMax     = a(args,20);
            o.axes.yrangeMin     = a(args,21);
            o.axes.yrangeMax     = a(args,22);
            o.axes.yStartZero    = flag(args,23);
            o.chart.horizontal    = flag(args,24);
            o.chart.stack         = flag(args,25);
            o.chart.fill          = flag(args,26);
            // 27-34 appearance
            o.chart.smooth        = a(args,27).isEmpty() ? "0.3" : a(args,27);
            o.chart.pointsize     = a(args,28).isEmpty() ? "4"   : a(args,28);
            o.chart.linewidth     = a(args,29).isEmpty() ? "2"   : a(args,29);
            o.chart.aspect        = a(args,30);
            o.chart.animate       = a(args,31);
            o.chart.legend        = a(args,32).isEmpty() ? "top" : a(args,32);
            o.chart.datalabels    = flag(args,33);
            o.chart.tooltipfmt    = a(args,34);
            // 35-42 pie/donut
            o.stats.stat          = a(args,35).isEmpty() ? "mean" : a(args,35);
            o.stats.cutout        = a(args,36);
            o.stats.rotation      = a(args,37);
            o.stats.circumference = a(args,38);
            o.stats.sliceborder   = a(args,39);
            o.stats.hoveroffset   = a(args,40);
            o.chart.pielabels     = flag(args,41);
            o.nomissing     = flag(args,42);
            // 43-54 axes
            o.axes.xtype         = a(args,43);
            o.axes.ytype         = a(args,44);
            o.axes.xtickcount    = a(args,45);
            o.axes.ytickcount    = a(args,46);
            o.axes.xtickangle    = a(args,47);
            o.axes.ytickangle    = a(args,48);
            o.axes.xstepsize     = a(args,49);
            o.axes.ystepsize     = a(args,50);
            o.axes.xgridlines    = !a(args,51).equalsIgnoreCase("off");
            o.axes.ygridlines    = !a(args,52).equalsIgnoreCase("off");
            o.axes.xborder       = !a(args,53).equalsIgnoreCase("off");
            o.axes.yborder       = !a(args,54).equalsIgnoreCase("off");
            // 55-59 bar/layout
            o.chart.barwidth      = a(args,55);
            o.chart.bargroupwidth = a(args,56);
            o.chart.borderradius  = a(args,57);
            o.chart.opacity       = a(args,58);
            o.chart.padding       = a(args,59);
            // 60-63 animation/tooltip
            o.chart.easing        = a(args,60);
            o.chart.animdelay     = a(args,61);
            o.chart.tooltipmode   = a(args,62).isEmpty() ? "index"   : a(args,62);
            o.chart.tooltippos    = a(args,63).isEmpty() ? "average" : a(args,63);
            // 64-66 legend
            o.chart.legendtitle      = a(args,64);
            o.chart.legendsize       = a(args,65);
            o.chart.legendboxheight  = a(args,66);
            // 67-71 point/line
            o.chart.pointstyle       = a(args,67);
            o.chart.pointborderwidth = a(args,68).isEmpty() ? "1" : a(args,68);
            o.chart.pointrotation    = a(args,69).isEmpty() ? "0" : a(args,69);
            o.chart.spanmissing      = flag(args,70);
            o.chart.stepped          = a(args,71);
            o.chart.sortgroups       = a(args,72);      // 72 sort groups: "" | "asc" | "desc"
            o.chart.novaluelabels    = flag(args,73);   // 73 suppress value labels
            o.axes.xlabels          = a(args,74);      // 74 custom x-axis tick labels
            o.axes.ylabels          = a(args,75);      // 75 custom y-axis tick labels
            o.axes.filter1          = a(args,76);      // 76 filter variable 1
            o.axes.filter2          = a(args,77);      // 77 filter variable 2
            o.chart.nostats          = flag(args,78);   // 78 suppress stats panel (v1.5)
            o.stats.cilevel          = a(args,79).isEmpty() ? "95"   : a(args,79); // 79 CI level (v1.7)
            o.stats.cibandopacity    = a(args,80).isEmpty() ? "0.18" : a(args,80); // 80 CI band opacity (v1.7.2)
            o.stats.bins             = a(args,81);                                   // 81 histogram bin count (v1.8.0)
            o.stats.histtype         = a(args,82).isEmpty() ? "density" : a(args,82);// 82 histogram type (v1.8.0)
            o.showmissingOver    = flag(args,83);  // 83 showmissing for over() (v1.9.1)
            o.showmissingBy      = flag(args,84);  // 84 showmissing for by() (v1.9.1)
            o.showmissingFilter  = flag(args,85);  // 85 showmissing for filter() (v1.9.1)
            o.showmissingFilter2 = flag(args,86);  // 86 showmissing for filter2() (v1.9.1)
            o.chart.areaopacity      = a(args,87).isEmpty() ? "0.35" : a(args,87);  // 87 area fill opacity (v2.0.1)
            o.chart.offline          = flag(args,88);                               // 88 offline mode: embed JS inline (v2.0.2)
            o.chart.stack100         = flag(args,89);                               // 89 100% stacked bar (v2.2.0)
            o.axes.y2vars           = a(args,90);                                  // 90 secondary y-axis variables (v2.3.0)
            o.axes.y2title          = a(args,91);                                  // 91 secondary y-axis title (v2.3.0)
            // y2range: two values packed as "min max" string, split on space
            String y2rng = a(args,92);
            o.stats.whiskerfence = a(args,93).isEmpty() ? "1.5" : a(args,93); // 93 box/violin whisker k (v2.4.0)                                        // 92 secondary y-axis range "min max" (v2.3.0)
            o.stats.mediancolor  = a(args,94); // 94 median marker color override (v2.4.10)
            o.stats.meancolor    = a(args,95); // 95 mean marker color override (v2.4.10)
            o.stats.bandwidth    = a(args,96); // 96 KDE bandwidth for violin (v2.5.0; empty=Silverman auto)
            // -- Phase 1-A: font/color styling (args 97-110) -- v2.6.0
            o.style.titleSize      = a(args,97);   // 97 title font size
            o.style.titleColor     = a(args,98);   // 98 title color
            o.style.subtitleSize   = a(args,99);   // 99 subtitle font size
            o.style.subtitleColor  = a(args,100);  // 100 subtitle color
            o.style.xtitleSize     = a(args,101);  // 101 x-axis title font size
            o.style.xtitleColor    = a(args,102);  // 102 x-axis title color
            o.style.ytitleSize     = a(args,103);  // 103 y-axis title font size
            o.style.ytitleColor    = a(args,104);  // 104 y-axis title color
            o.style.xlabSize       = a(args,105);  // 105 x tick label font size
            o.style.xlabColor      = a(args,106);  // 106 x tick label color
            o.style.ylabSize       = a(args,107);  // 107 y tick label font size
            o.style.ylabColor      = a(args,108);  // 108 y tick label color
            o.style.legColor       = a(args,109);  // 109 legend text color
            o.style.legBgColor     = a(args,110);  // 110 legend background color
            // -- Phase 1-B: tooltip styling (args 111-114) -- v2.6.0
            o.style.tooltipBg      = a(args,111);  // 111 tooltip background color
            o.style.tooltipBorder  = a(args,112);  // 112 tooltip border color
            o.style.tooltipFontSize = a(args,113); // 113 tooltip font size
            o.style.tooltipPadding = a(args,114);  // 114 tooltip padding (px)
            o.chart.download       = flag(args,115); // 115 download PNG button (v2.7.0)
            o.axes.yreverse        = flag(args,116); // 116 reverse y-axis direction (v3.0.3)
            o.axes.xreverse        = flag(args,117); // 117 reverse x-axis direction (v3.0.3)
            o.axes.noticks         = flag(args,118); // 118 hide tick marks, keep labels (v3.0.3)
            o.axes.ygrace          = a(args,119);    // 119 y-axis grace padding e.g. 5%% (v3.0.3)
            o.chart.animduration   = a(args,120);    // 120 exact animation ms, overrides animate() (v3.0.3)
            // -- Phase 1-D: line/point style (args 121-124, v3.1.0) --
            o.chart.lpattern       = a(args,121);    // 121 line dash pattern for all series
            o.chart.lpatterns      = a(args,122);    // 122 pipe-separated per-series dash patterns
            o.chart.nopoints       = flag(args,123); // 123 suppress point markers
            o.chart.pointhoversize = a(args,124);    // 124 point hover radius px
            // -- Phase 1-E: note size + gradient (args 125-126, v3.2.0) --
            o.style.noteSize       = a(args,125);    // 125 note/caption font size (CSS value)
            o.style.gradient       = a(args,126);    // 126 gradient fill: "" | "1" | "color1|color2"
            // -- Phase 2-C: legend/tick overrides (args 137-139, v3.4.0) --
            o.chart.legendlabels   = a(args,137);    // 137 pipe-sep legend label overrides
            o.axes.xticks          = a(args,138);    // 138 pipe-sep custom x tick values
            o.axes.yticks          = a(args,139);    // 139 pipe-sep custom y tick values
            o.chart.relabel        = a(args,150);    // 150 pipe-sep relabel: renames x-axis groups AND legend (Stata relabel() equivalent, v3.5.33)
            // -- Phase 2-B: reference annotations lines/bands (args 127-136, v3.5.0) --
            o.axes.yline           = a(args,127);    // 127 pipe-sep y reference line values
            o.axes.xline           = a(args,128);    // 128 pipe-sep x reference line values
            o.axes.ylinecolor      = a(args,129);    // 129 pipe-sep colors per yline
            o.axes.xlinecolor      = a(args,130);    // 130 pipe-sep colors per xline
            o.axes.ylinelabel      = a(args,131);    // 131 pipe-sep labels per yline
            o.axes.xlinelabel      = a(args,132);    // 132 pipe-sep labels per xline
            o.axes.yband           = a(args,133);    // 133 pipe-sep "lo hi" pairs for horizontal bands
            o.axes.xband           = a(args,134);    // 134 pipe-sep "lo hi" pairs for vertical bands
            o.axes.ybandcolor      = a(args,135);    // 135 pipe-sep colors per yband
            o.axes.xbandcolor      = a(args,136);    // 136 pipe-sep colors per xband
            // -- Phase 2-B: reference annotations points/labels/ellipses (args 140-148, v3.5.0) --
            o.axes.apoint          = a(args,140);    // 140 space-sep y x pairs for annotation points
            o.axes.apointcolor     = a(args,141);    // 141 pipe-sep colors per apoint
            o.axes.apointsize      = a(args,142);    // 142 radius px for all apoints
            o.axes.alabelpos       = a(args,143);    // 143 pipe-sep "y x" coordinate pairs for labels
            o.axes.alabeltext      = a(args,144);    // 144 pipe-sep label texts
            o.axes.alabelfontsize  = a(args,145);    // 145 font size px for all labels
            o.axes.aellipse        = a(args,146);    // 146 pipe-sep "ymin xmin ymax xmax" quads
            o.axes.aellipsecolor   = a(args,147);    // 147 pipe-sep fill colors per ellipse
            o.axes.aellipseborder  = a(args,148);    // 148 pipe-sep border colors per ellipse
            o.axes.alabelgap       = a(args,149);    // 149 label offset distance px (v3.5.2)
            if (!y2rng.isEmpty()) {
                String[] y2parts = y2rng.trim().split("\\s+");
                if (y2parts.length >= 2) { o.axes.y2rangeMin = y2parts[0]; o.axes.y2rangeMax = y2parts[1]; }
            }
            // Type aliasing
            if (o.type.equals("hbar"))       { o.type = "bar";  o.chart.horizontal = true; }
            if (o.type.equals("stackedbar"))    { o.type = "bar"; o.chart.stack = true; }
            if (o.type.equals("stackedhbar"))   { o.type = "bar"; o.chart.stack = true; o.chart.horizontal = true; }  // v3.5.3
            // v2.4.0: 100% stacked variants
            if (o.type.equals("stackedbar100"))  { o.type = "bar"; o.chart.stack = true; o.chart.stack100 = true; }
            if (o.type.equals("stackedhbar100")) { o.type = "bar"; o.chart.stack = true; o.chart.stack100 = true; o.chart.horizontal = true; }
            // v2.4.7: horizontal boxplot and violin
            if (o.type.equals("hboxplot"))   { o.type = "boxplot"; o.chart.horizontal = true; }
            if (o.type.equals("hviolinplot")){ o.type = "violin";  o.chart.horizontal = true; }
            // v3.5.16: stackedline/stackedarea aliases -- were missing, fell to default (bar)
            if (o.type.equals("stackedline")) { o.type = "line"; o.chart.stack = true; }
            if (o.type.equals("stackedarea")) { o.type = "line"; o.chart.stack = true; o.chart.fill = true;
                if (!o.axes.yStartZero) o.axes.yStartZero = true; }
            if (o.type.equals("area"))       { o.type = "line"; o.chart.fill = true;
                // v2.0.1: area charts MUST start y-axis at zero so fills are
                // meaningful. Without this Chart.js auto-scales to data minimum
                // (e.g. ~2000) making the smaller fill a thin sliver inside the
                // larger fill -- visually hidden even as the top paint layer.
                if (!o.axes.yStartZero) o.axes.yStartZero = true; }

            SFIToolkit.displayln("  Reading data from Stata...");
            StataDataReader reader = new StataDataReader();
            DataSet data = reader.read(o.varlist, o.over, o.by, o.tousename,
                                       o.nomissing, o.chart.novaluelabels,
                                       o.axes.filter1, o.axes.filter2);

            if (data.isEmpty()) {
                SFIToolkit.error("sparkta: no data could be read\n");
                return SFIToolkit.RC_GENERAL_ERROR;
            }

            // v2.0.4: pre-flight check -- if offline, verify JS libs in jar before any work
            // v2.7.0: canvas2svg added to required list (needed by download option)
            if (o.chart.offline) {
                java.util.List<String> requiredList = new java.util.ArrayList<String>();
                requiredList.add("/com/dashboard/js/chartjs-4.4.0.min.js");
                requiredList.add("/com/dashboard/js/chartjs-datalabels-2.2.0.min.js");
                requiredList.add("/com/dashboard/js/chartjs-errorbars-4.4.0.min.js");
                requiredList.add("/com/dashboard/js/chartjs-boxplot-4.4.5.min.js");
                // canvas2svg always bundled (download option may be added later to offline charts)
                requiredList.add("/com/dashboard/js/canvas2svg-1.0.19.js");
                // v3.5.0: annotation lib added only when chart uses annotations (keeps bundle lean)
                // v3.5.37: delegated to o.hasAnnotations() -- single source of truth shared with
                //          HtmlGenerator.hasAnnotations(). Add new annotation types there only.
                if (o.hasAnnotations()) {
                    requiredList.add("/com/dashboard/js/chartjs-annotation-3.0.1.min.js");
                }
                java.util.List<String> missing = new java.util.ArrayList<String>();
                for (String res : requiredList) {
                    if (DashboardBuilder.class.getResourceAsStream(res) == null) {
                        missing.add(res);
                    }
                }
                if (!missing.isEmpty()) {
                    SFIToolkit.error("sparkta offline ERROR: JS libraries not bundled in jar.\n");
                    SFIToolkit.error("  Missing resources:\n");
                    for (String m : missing) SFIToolkit.error("    " + m + "\n");
                    SFIToolkit.error("  Fix:\n");
                    SFIToolkit.error("    1. Place all 5 .min.js files in:\n");
                    SFIToolkit.error("       java\\src\\main\\resources\\com\\dashboard\\js\\\n");
                    SFIToolkit.error("    2. Run build.bat to recompile\n");
                    SFIToolkit.error("    3. Reinstall sparkta.jar to your Stata ado folder\n");
                    SFIToolkit.error("  No CDN fallback -- offline means strictly offline.\n");
                    return SFIToolkit.RC_GENERAL_ERROR;
                }
            }

            // v2.0.2: report chart type and JS delivery mode
            String jsMode = o.chart.offline ? "offline (embedded JS)" : "online (CDN)";
            SFIToolkit.displayln("  Generating " + o.type + " chart  [" + jsMode + "]");
            HtmlGenerator gen = new HtmlGenerator(o);
            String html = gen.build(data);


            if (!o.export.isEmpty()) {
                // v2.0.6: resolve ~ and normalise separators before writing
                String resolvedPath = com.dashboard.util.FileUtil.resolvedPathString(o.export);
                SFIToolkit.displayln("  Exporting to: " + resolvedPath);
                try {
                    com.dashboard.util.FileUtil.writeFile(o.export, html);
                    String exportMode = o.chart.offline ? " [fully offline]" : "";
                    SFIToolkit.displayln("Dashboard exported to: " + resolvedPath + exportMode);
                } catch (IOException ex) {
                    SFIToolkit.error("sparkta export ERROR: could not write file.\n");
                    SFIToolkit.error("  Path attempted: " + resolvedPath + "\n");
                    SFIToolkit.error("  Reason: " + ex.getMessage() + "\n");
                    SFIToolkit.error("  Tips:\n");
                    SFIToolkit.error("    - Use a full path, e.g. export(C:\\Users\\name\\Downloads\\chart.html)\n");
                    SFIToolkit.error("    - Avoid ~ on Windows; use the full path instead\n");
                    SFIToolkit.error("    - Make sure the destination folder exists\n");
                    SFIToolkit.error("    - Check you have write permission to that folder\n");
                    return SFIToolkit.RC_GENERAL_ERROR;
                }
            } else {
                String tmp = FileUtil.writeTempHtml(html);
                BrowserLauncher.open(tmp);
                String readyMode = o.chart.offline
                    ? "Sparkta ready  [fully offline -- no internet needed]"
                    : "Sparkta ready  [online -- CDN]";
                SFIToolkit.displayln(readyMode);
            }
            return 0;

        } catch (Exception e) {
            SFIToolkit.error("sparkta error: " + e.getMessage() + "\n");
            return SFIToolkit.RC_GENERAL_ERROR;
        }
    }

    private static String  a(String[] a, int i) { return i < a.length ? a[i].trim() : ""; }
    private static boolean flag(String[] a, int i) { return i < a.length && a[i].trim().equals("1"); }
}
