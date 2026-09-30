package com.dashboard_test;

import com.stata.sfi.SFIToolkit;
import com.dashboard_test.data.StataDataReader;
import com.dashboard_test.data.DataSet;
import com.dashboard_test.html.HtmlGenerator;
import com.dashboard_test.html.DashboardOptions;
import com.dashboard_test.util.FileUtil;
import com.dashboard_test.util.BrowserLauncher;
import java.io.IOException;

/**
 * DashboardBuilder -- entry point called by sparkta_test.ado via javacall.
 *
 * v3.5.81: Batch 2 refactor. execute() split into private methods:
 *   wireArgs()             all 160 arg assignments (args 0-160)
 *   aliasTypes()           type shorthand expansion
 *   loadAuxVars()          slider + mlabel variable loading
 *   checkOfflinePreflight() JS lib presence check
 *   exportOrOpen()         file write or browser open
 *   Zero behaviour change -- pure structural reorganisation.
 */
public class DashboardBuilder {

    // v3.6.0-t2i (hardening): force a locale-neutral number format for the whole
    // render. Every machine number reaches JS/JSON/LaTeX via String.format, whose
    // no-Locale overloads use the JVM default -- under de_DE/fr_FR etc. "%.4f"
    // emits "1,5000" and corrupts the generated JavaScript (Astra: 41/44 js pages
    // failed under de_DE). Setting the default to US around execute() (restored in
    // finally) makes ALL of them '.'-decimal in one place, current and future.
    public static int execute(String[] args) {
        java.util.Locale _spkPrevLocale = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.US);
            return execute0(args);
        } finally {
            java.util.Locale.setDefault(_spkPrevLocale);
        }
    }

    private static int execute0(String[] args) {
        try {
            if (args.length < 210) {
                SFIToolkit.error("sparkta: insufficient arguments (" + args.length
                    + " received, 210 expected)\n");
                return SFIToolkit.RC_GENERAL_ERROR;
            }
            // fix9g-c: Stata splits an over-long javacall argument (~5,000+ characters) into
            // several arguments, so every later slot shifts and the chart silently renders
            // garbage (a 100,000-row fit() line arrived as 140-point chunks). Refuse instead.
            if (args.length > 210) {
                int worst = 0, at = -1;
                for (int i = 0; i < args.length; i++) if (args[i] != null && args[i].length() > worst) { worst = args[i].length(); at = i; }
                SFIToolkit.error("sparkta: " + args.length + " arguments received, 210 expected -- an option value"
                    + " was too long for javacall and was split (longest argument: slot " + at + ", " + worst
                    + " characters). This is a sparkta bug: please report the command that produced it.\n");
                return SFIToolkit.RC_GENERAL_ERROR;
            }

            DashboardOptions o = new DashboardOptions();
            wireArgs(args, o);
            aliasTypes(o);

            DataSet data;
            if (o.isPostEstimation()) {
                // v3.6.0: Post-estimation types read from pipe-sep args, not Stata data.
                // Create minimal empty DataSet -- chart rendering uses o.chart.pe* fields.
                SFIToolkit.displayln("  Post-estimation chart: skipping data scan.");
                data = new DataSet();
            } else {
                SFIToolkit.displayln("  Reading data from Stata...");
                StataDataReader reader = new StataDataReader();
                data = reader.read(o.varlist, o.over, o.by, o.tousename,
                                   o.nomissing, o.chart.novaluelabels,
                                   o.axes.filterList);

                loadAuxVars(reader, o, data);

                if (data.isEmpty()) {
                    SFIToolkit.error("sparkta: no data could be read\n");
                    return SFIToolkit.RC_GENERAL_ERROR;
                }
            }

            int rc = checkOfflinePreflight(o);
            if (rc != 0) return rc;

            String jsMode = o.chart.offline ? "offline (embedded JS)" : "online (CDN)";
            SFIToolkit.displayln("  Generating " + o.type + " chart  [" + jsMode + "]");
            // v3.6.0-s8i: nested-CI subtitle removed -- mpLegend/cpCiLegend draw the CI key
            // on the chart and HtmlGenerator.contextLine() states "(inner 90%)".
            HtmlGenerator gen = new HtmlGenerator(o);
            String html = gen.build(data);

            return exportOrOpen(o, html);

        } catch (Exception e) {
            SFIToolkit.error("sparkta error: " + e.getMessage() + "\n");
            if (System.getProperty("sparkta.trace") != null) e.printStackTrace(System.out);
            return SFIToolkit.RC_GENERAL_ERROR;
        }
    }

    // -------------------------------------------------------------------------
    // wireArgs -- map all 160 positional args into DashboardOptions fields.
    // -------------------------------------------------------------------------
    private static void wireArgs(String[] args, DashboardOptions o) {

        // 0-18: identity and core
        o.varlist             = a(args,0);
        o.type                = a(args,1).toLowerCase(java.util.Locale.ROOT);
        o.title               = a(args,2).isEmpty() ? "Dashboard" : a(args,2);
        o.theme               = a(args,3).isEmpty() ? "default"   : a(args,3);
        o.export              = a(args,4);
        o.axes.xtitle         = a(args,5);
        o.axes.ytitle         = a(args,6);
        o.over                = a(args,7);
        o.by                  = a(args,8);
        o.tousename           = a(args,9);
        o.chart.layout        = a(args,10).isEmpty() ? "vertical" : a(args,10);
        o.chart.layoutExplicit = !a(args,10).isEmpty();   // fix9f: explicit layout(vertical) keeps the stack in exports
        o.chart.bgcolor       = a(args,11);
        o.chart.plotcolor     = a(args,12);
        o.chart.gridcolor     = a(args,13);
        o.chart.gridopacity   = a(args,14).isEmpty() ? "0.15" : a(args,14);
        o.chart.colors        = a(args,15);
        o.note                = a(args,16);
        o.caption             = a(args,17);
        o.subtitle            = a(args,18);

        // 19-26: axis ranges and layout flags
        o.axes.xrangeMin      = a(args,19);
        o.axes.xrangeMax      = a(args,20);
        o.axes.yrangeMin      = a(args,21);
        o.axes.yrangeMax      = a(args,22);
        o.axes.yStartZero     = flag(args,23);
        o.chart.horizontal    = flag(args,24);
        o.chart.stack         = flag(args,25);
        o.chart.fill          = flag(args,26);

        // 27-34: appearance
        o.chart.smooth        = a(args,27);   // t2j fix8 (A10): no default here -- regular lines default tension to 0.3, post-est connected lines to 0 (straight), via *Or("...") helpers; honors an explicit smooth() on post-est connectors too
        o.chart.pointsize     = a(args,28).isEmpty() ? "4"   : a(args,28);
        o.chart.linewidth     = a(args,29);   // t2j fix8 (A7): no default here -- lines/ciline default to 2, bars to 1, via DatasetBuilder.lineWidthOr(); honors an explicit linewidth() on bar outlines too
        o.chart.aspect        = a(args,30);
        o.chart.animate       = a(args,31);
        o.chart.legend        = a(args,32).isEmpty() ? "top" : a(args,32);
        o.chart.datalabels    = flag(args,33);
        o.chart.tooltipfmt    = a(args,34);

        // 35-42: pie/donut and missing
        o.stats.stat          = a(args,35).isEmpty() ? "mean" : a(args,35);
        o.stats.cutout        = a(args,36);
        o.stats.rotation      = a(args,37);
        o.stats.circumference = a(args,38);
        o.stats.sliceborder   = a(args,39);
        o.stats.hoveroffset   = a(args,40);
        o.chart.pielabels     = flag(args,41);
        o.nomissing           = flag(args,42);

        // 43-54: axis scale and tick config
        o.axes.xtype          = a(args,43);
        o.axes.ytype          = a(args,44);
        o.axes.xtickcount     = a(args,45);
        o.axes.ytickcount     = a(args,46);
        o.axes.xtickangle     = a(args,47);
        o.axes.ytickangle     = a(args,48);
        o.axes.xstepsize      = a(args,49);
        o.axes.ystepsize      = a(args,50);
        o.axes.xgridlines     = !a(args,51).equalsIgnoreCase("off");
        o.axes.ygridlines     = !a(args,52).equalsIgnoreCase("off");
        o.axes.xborder        = !a(args,53).equalsIgnoreCase("off");
        o.axes.yborder        = !a(args,54).equalsIgnoreCase("off");

        // 55-59: bar appearance
        o.chart.barwidth      = a(args,55);
        o.chart.bargroupwidth = a(args,56);
        o.chart.borderradius  = a(args,57);
        o.chart.opacity       = a(args,58);
        o.chart.padding       = a(args,59);
        o.chart.plotMargin    = a(args,205);   // v3.6.0-s8p
        o.chart.yfree         = flag(args,206);  // v3.6.0-s9g
        o.chart.peXpos        = a(args,207);     // v3.6.0-t2d
        o.chart.esOpts        = a(args,208);     // v3.6.0-t2f
        parseTableOpts(o, a(args,209));          // v3.6.0-t2h (batch 2a): publication table

        // 60-63: animation and tooltip
        o.chart.easing        = a(args,60);
        o.chart.animdelay     = a(args,61);
        o.chart.tooltipmode   = a(args,62);   // t2j fix8 (A8): no default here -- each tooltip builder keeps its own historical default when empty, so an explicit tooltipmode(index) is honored instead of coerced+remapped
        o.chart.tooltippos    = a(args,63).isEmpty() ? "average" : a(args,63);

        // 64-66: legend
        o.chart.legendtitle      = a(args,64);
        o.chart.legendsize       = a(args,65);
        o.chart.legendboxheight  = a(args,66);

        // 67-75: point/line style, sort, labels
        o.chart.pointstyle       = a(args,67);
        o.chart.pointborderwidth = a(args,68).isEmpty() ? "1" : a(args,68);
        o.chart.pointrotation    = a(args,69).isEmpty() ? "0" : a(args,69);
        o.chart.spanmissing      = flag(args,70);
        o.chart.stepped          = a(args,71);
        o.chart.sortgroups       = a(args,72);
        o.chart.novaluelabels    = flag(args,73);
        o.axes.xlabels           = a(args,74);
        o.axes.ylabels           = a(args,75);

        // 76-88: filter, stats, showmissing, area, offline
        o.axes.filterList        = a(args,76);
        { String[] fp = o.axes.filterList.split("\\|", -1);
          o.axes.filter1 = fp.length > 0 ? fp[0].trim() : "";
          o.axes.filter2 = fp.length > 1 ? fp[1].trim() : ""; }
        // arg 77: reference-line width px (t2j fix8 A13; slot was reserved/empty)
        o.chart.refLineWidth     = a(args,77);
        o.chart.nostats          = flag(args,78);
        o.stats.cilevel          = a(args,79).isEmpty() ? "95"      : a(args,79);
        o.stats.cibandopacity    = a(args,80).isEmpty() ? "0.18"    : a(args,80);
        o.stats.bins             = a(args,81);
        o.stats.histtype         = a(args,82).isEmpty() ? "density" : a(args,82);
        o.showmissingOver        = flag(args,83);
        o.showmissingBy          = flag(args,84);
        o.showmissingFilter      = flag(args,85);
        o.showmissingFilter2     = flag(args,86);
        o.chart.areaopacity      = a(args,87).isEmpty() ? "0.35" : a(args,87);
        o.chart.offline          = flag(args,88);

        // 89-96: stacked, secondary axis, box/violin
        o.chart.stack100         = flag(args,89);
        o.axes.y2vars            = a(args,90);
        o.axes.y2title           = a(args,91);
        String y2rng = a(args,92);
        if (!y2rng.isEmpty()) {
            String[] y2p = y2rng.trim().split("\\s+");
            if (y2p.length >= 2) { o.axes.y2rangeMin = y2p[0]; o.axes.y2rangeMax = y2p[1]; }
        }
        o.stats.whiskerfence     = a(args,93).isEmpty() ? "1.5" : a(args,93);
        o.stats.mediancolor      = a(args,94);
        o.stats.meancolor        = a(args,95);
        o.stats.bandwidth        = a(args,96);

        // 97-114: Phase 1-A/B font, color, tooltip styling
        o.style.titleSize        = a(args,97);
        o.style.titleColor       = a(args,98);
        o.style.subtitleSize     = a(args,99);
        o.style.subtitleColor    = a(args,100);
        o.style.xtitleSize       = a(args,101);
        o.style.xtitleColor      = a(args,102);
        o.style.ytitleSize       = a(args,103);
        o.style.ytitleColor      = a(args,104);
        o.style.xlabSize         = a(args,105);
        o.style.xlabColor        = a(args,106);
        o.style.ylabSize         = a(args,107);
        o.style.ylabColor        = a(args,108);
        o.style.legColor         = a(args,109);
        o.style.legBgColor       = a(args,110);
        o.style.tooltipBg        = a(args,111);
        o.style.tooltipBorder    = a(args,112);
        o.style.tooltipFontSize  = a(args,113);
        o.style.tooltipPadding   = a(args,114);

        // 115-126: Phase 2-A, 1-C, 1-D, 1-E
        o.chart.download         = flag(args,115);
        o.axes.yreverse          = flag(args,116);
        o.axes.xreverse          = flag(args,117);
        o.axes.noticks           = flag(args,118);
        o.axes.ygrace            = a(args,119);
        o.chart.animduration     = a(args,120);
        o.chart.lpattern         = a(args,121);
        o.chart.lpatterns        = a(args,122);
        o.chart.nopoints         = flag(args,123);
        o.chart.pointhoversize   = a(args,124);
        o.style.noteSize         = a(args,125);
        o.style.gradient         = a(args,126);

        // 127-136: Phase 2-B annotation lines/bands
        o.axes.yline             = a(args,127);
        o.axes.xline             = a(args,128);
        o.axes.ylinecolor        = a(args,129);
        o.axes.xlinecolor        = a(args,130);
        o.axes.ylinelabel        = a(args,131);
        o.axes.xlinelabel        = a(args,132);
        o.axes.yband             = a(args,133);
        o.axes.xband             = a(args,134);
        o.axes.ybandcolor        = a(args,135);
        o.axes.xbandcolor        = a(args,136);

        // 137-139: Phase 2-C legend/tick overrides
        o.chart.legendlabels     = a(args,137);
        o.axes.xticks            = a(args,138);
        o.axes.yticks            = a(args,139);

        // 140-149: Phase 2-B annotation points/labels/ellipses
        o.axes.apoint            = a(args,140);
        o.axes.apointcolor       = a(args,141);
        o.axes.apointsize        = a(args,142);
        o.axes.alabelpos         = a(args,143);
        o.axes.alabeltext        = a(args,144);
        o.axes.alabelfontsize    = a(args,145);
        o.axes.aellipse          = a(args,146);
        o.axes.aellipsecolor     = a(args,147);
        o.axes.aellipseborder    = a(args,148);
        o.axes.alabelgap         = a(args,149);

        // 150-160: relabel, sliders, mlabel, fit
        o.chart.relabel          = a(args,150);
        o.chart.sliders          = a(args,151);
        o.chart.mlabelVar        = a(args,152);
        o.chart.mlabelAll        = flag(args,153);
        o.chart.mlabpos          = a(args,154);
        o.chart.mlabvpos         = a(args,155);
        o.chart.fit              = a(args,156);
        o.chart.fitci            = flag(args,157);
        o.chart.fitLineData      = a(args,158);
        o.chart.fitCiUpper       = a(args,159);
        o.chart.fitCiLower       = a(args,160);

        // 161-179: Post-estimation charts (v3.6.0)
        o.chart.peNames          = a(args,161);
        
        o.chart.peCoefs          = a(args,162);
        o.chart.peLower          = a(args,163);
        o.chart.peUpper          = a(args,164);
        o.chart.peSes            = a(args,165);
        o.chart.pePvals          = a(args,166);
        o.chart.peEstNames       = a(args,167);
        o.chart.peHeadings       = a(args,168);
        o.chart.peNobs           = a(args,169);
        o.chart.peDepvar         = a(args,170);
        o.chart.peOrient         = a(args,171);
        o.chart.peCoefStyle      = a(args,172);
        o.chart.peCiStyle        = a(args,173);
        o.chart.pePStyles        = a(args,174);
        o.chart.mpData           = a(args,175);
        o.chart.mpUpper          = a(args,176);
        o.chart.mpLower          = a(args,177);
        o.chart.mpXlab           = a(args,178);
        o.chart.mpYlab           = a(args,179);
        o.chart.mpSeries         = a(args,203);  // v3.6.0-s8a: series labels
        o.chart.mpXpos           = a(args,204);  // v3.6.0-s8a: numeric x positions from r(at)

        // 180-184: Post-estimation table extras (v3.6.0)
        o.chart.peBases          = a(args,180);
        o.chart.peTstat          = a(args,181);
        o.chart.peCmd            = a(args,182);
        o.chart.peNoci           = flag(args,183);
        o.chart.peTzvals         = a(args,184);
        // -- Session 6 visual control (v3.6.0-s6) --
        o.chart.peRefval         = a(args,185).isEmpty() ? "0" : a(args,185);
        o.chart.peEstlabels      = a(args,186);
        o.chart.peCicolors       = a(args,187);
        o.chart.peCiwidth        = a(args,188);
        o.stataPwd              = a(args,189);   // Stata c(pwd) for relative path resolution
        o.chart.peConnected     = flag(args,190); // connected: draw line through point estimates
        o.chart.peModelStats    = a(args,191);    // v3.6.0-s6c: fit stats per model (tilde-sep pipe-sep)
        o.chart.peIndicators    = a(args,192);    // v3.6.0-s6c: indicator rows for table export
        o.chart.peAddstats      = a(args,193);    // v3.6.0-s6c: addstats rows for table export
        o.chart.peVarLabels     = a(args,194);    // v3.6.0: auto variable labels varname|||label~~~
        o.chart.peCustomLabels  = a(args,195);    // v3.6.0: custom labels from coeflabels()
        o.chart.pePexline       = a(args,196);    // v3.6.0-s7a: pexline() vertical reference for coefplot/eventstudy
        o.chart.peLevels2Lo     = a(args,197);    // v3.6.0-s7b: levels() inner CI lower bounds
        o.chart.peLevels2Hi     = a(args,198);    // v3.6.0-s7b: levels() inner CI upper bounds
        o.chart.peLevels2Val    = a(args,199);    // v3.6.0-s7b: levels() inner CI level value
        o.style.noTimestamp     = flag(args,200);  // v3.6.0-s7d: suppress timestamp subtitle
        o.style.collapseStats   = flag(args,201);  // v3.6.0-s7d: stats panel starts collapsed
        o.style.noAllFilter     = flag(args,202);  // v3.6.0-s7d: suppress All in filter dropdowns
    }

    // -------------------------------------------------------------------------
    // aliasTypes -- expand shorthand type names to canonical Chart.js types.
    // -------------------------------------------------------------------------
    private static void aliasTypes(DashboardOptions o) {
        if (o.type.equals("hbar"))           { o.type = "bar";     o.chart.horizontal = true; }
        if (o.type.equals("stackedbar"))     { o.type = "bar";     o.chart.stack = true; }
        if (o.type.equals("stackedhbar"))    { o.type = "bar";     o.chart.stack = true; o.chart.horizontal = true; }
        if (o.type.equals("stackedbar100"))  { o.type = "bar";     o.chart.stack = true; o.chart.stack100 = true; }
        if (o.type.equals("stackedhbar100")) { o.type = "bar";     o.chart.stack = true; o.chart.stack100 = true; o.chart.horizontal = true; }
        if (o.type.equals("hboxplot"))       { o.type = "boxplot"; o.chart.horizontal = true; }
        if (o.type.equals("hviolinplot"))    { o.type = "violin";  o.chart.horizontal = true; }
        if (o.type.equals("hbox"))           { o.type = "boxplot"; o.chart.horizontal = true; }
        if (o.type.equals("hviolin"))        { o.type = "violin";  o.chart.horizontal = true; }
        if (o.type.equals("stackedline"))    { o.type = "line";    o.chart.stack = true; }
        if (o.type.equals("stackedarea"))    { o.type = "line";    o.chart.stack = true; o.chart.fill = true;
            if (!o.axes.yStartZero) o.axes.yStartZero = true; }
        if (o.type.equals("area"))           { o.type = "line";    o.chart.fill = true;
            if (!o.axes.yStartZero) o.axes.yStartZero = true; }
    }

    // -------------------------------------------------------------------------
    // loadAuxVars -- load slider, mlabel, mlabvpos variables into DataSet.
    // -------------------------------------------------------------------------
    private static void loadAuxVars(StataDataReader reader,
                                     DashboardOptions o, DataSet data) {
        if (!o.chart.sliders.isEmpty()) {
            for (String sv : o.chart.sliders.split("\\|", -1)) {
                sv = sv.trim();
                if (sv.isEmpty()) continue;
                com.dashboard_test.data.Variable slv =
                    reader.readOneVar(sv, o.tousename);
                if (slv != null) data.addSliderVariable(slv);
            }
        }
        if (!o.chart.mlabelVar.isEmpty()) {
            com.dashboard_test.data.Variable mlv =
                reader.readOneVar(o.chart.mlabelVar, o.tousename);
            if (mlv != null) data.addVariable(mlv);
        }
        if (!o.chart.mlabvpos.isEmpty()) {
            com.dashboard_test.data.Variable mpv =
                reader.readOneVar(o.chart.mlabvpos, o.tousename);
            if (mpv != null) data.addVariable(mpv);
        }
    }

    // -------------------------------------------------------------------------
    // checkOfflinePreflight -- verify JS libs are bundled when offline mode.
    // Returns 0 on success, RC_GENERAL_ERROR if any lib is missing.
    // -------------------------------------------------------------------------
    private static int checkOfflinePreflight(DashboardOptions o) {
        if (!o.chart.offline) return 0;

        java.util.List<String> required = new java.util.ArrayList<String>();
        required.add("/com/dashboard_test/js/chartjs-4.4.0.min.js");
        required.add("/com/dashboard_test/js/chartjs-datalabels-2.2.0.min.js");
        required.add("/com/dashboard_test/js/chartjs-errorbars-4.4.0.min.js");
        required.add("/com/dashboard_test/js/chartjs-boxplot-4.4.5.min.js");
        required.add("/com/dashboard_test/js/canvas2svg-1.0.19.js");
        if (o.hasAnnotations()) {
            required.add("/com/dashboard_test/js/chartjs-annotation-3.0.1.min.js");
        }

        java.util.List<String> missing = new java.util.ArrayList<String>();
        for (String res : required) {
            if (DashboardBuilder.class.getResourceAsStream(res) == null)
                missing.add(res);
        }
        if (!missing.isEmpty()) {
            SFIToolkit.error("sparkta offline ERROR: JS libraries not bundled in jar.\n");
            SFIToolkit.error("  Missing resources:\n");
            for (String m : missing) SFIToolkit.error("    " + m + "\n");
            SFIToolkit.error("  Fix:\n");
            SFIToolkit.error("    1. Place all .min.js files in:\n");
            SFIToolkit.error("       java\\src\\main\\resources\\com\\dashboard_test\\js\\\n");
            SFIToolkit.error("    2. Run build.bat to recompile\n");
            SFIToolkit.error("    3. Reinstall sparkta.jar to your Stata ado folder\n");
            SFIToolkit.error("  No CDN fallback -- offline means strictly offline.\n");
            return SFIToolkit.RC_GENERAL_ERROR;
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // exportOrOpen -- write HTML to file or open in browser.
    // Returns 0 on success, RC_GENERAL_ERROR on write failure.
    // -------------------------------------------------------------------------
    private static int exportOrOpen(DashboardOptions o, String html) throws Exception {
        if (!o.export.isEmpty()) {
            // Pass Stata's c(pwd) so relative paths resolve correctly (v3.6.0-s6)
            String resolvedPath = FileUtil.resolvedPathString(o.export, o.stataPwd);
            SFIToolkit.displayln("  Exporting to: " + resolvedPath);
            try {
                FileUtil.writeFile(o.export, o.stataPwd, html);
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
    }

    // -------------------------------------------------------------------------
    private static String  a(String[] a, int i) { return i < a.length ? a[i].trim() : ""; }
    private static boolean flag(String[] a, int i) { return i < a.length && a[i].trim().equals("1"); }

    // -------------------------------------------------------------------------
    // v3.6.0-t2h (batch 2a): parse the packed publication-table option string
    // emitted by sparkta_table_opts.ado into o.table.*. Format is a pipe-sep
    // list of key=value pairs (order-independent, all optional):
    //   "stars=0.10 0.05 0.01|nostars=0|tstat=0|ci=0|nofooter=0|notable=0"
    // An empty arg leaves every default in place. Unknown keys are ignored so
    // the Java side never rejects a string the ado might extend later.
    // -------------------------------------------------------------------------
    private static void parseTableOpts(DashboardOptions o, String packed) {
        if (packed == null || packed.isEmpty()) return;
        for (String pair : packed.split("\\|", -1)) {
            int eq = pair.indexOf('=');
            if (eq < 1) continue;
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();
            switch (key) {
                case "stars":
                    if (!val.isEmpty()) {
                        String[] t = val.split("\\s+");
                        java.util.List<String> keep = new java.util.ArrayList<>();
                        for (String s : t) if (!s.isEmpty()) keep.add(s);
                        if (!keep.isEmpty()) o.table.stars = keep.toArray(new String[0]);
                    }
                    break;
                case "nostars":  o.table.noStars  = val.equals("1"); break;
                case "tstat":    o.table.tstat    = val.equals("1"); break;
                case "ci":       o.table.ci       = val.equals("1"); break;
                case "nofooter": o.table.noFooter = val.equals("1"); break;
                case "notable":  o.table.noTable  = val.equals("1"); break;
                default: /* forward-compatible: ignore unknown keys */ break;
            }
        }
    }
}
