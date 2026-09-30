/*
 * HarnessByFilter.java -- v3.6.0-t2j fix9a: by()+filter sweep over EVERY data chart type.
 *
 * Roadmap item 3 (2026-09-13): "by()+filter tested on ALL plot types, not just the rare
 * ones". Every scenario here is a by() panel page with at least one filters() dropdown
 * (some add sliders()), rendered through the real DashboardBuilder on the SFI fixture
 * (30 auto-shaped rows: price mpg weight foreign rep78). verify/byfilter_probe.js then
 * drives each dropdown value in headless Chromium and compares every panel's datasets
 * against an independent recomputation from the same fixture.
 *
 * Post-estimation types (coefplot / eventstudy / marginsplot) take no by()/filters()
 * (they read matrices, not observations) and are covered by the ado error tests.
 *
 * Usage:  SPK_OFFLINE=1 java -cp <classes>:<sfi-stub> HarnessByFilter <outdir>
 * ASCII only. verify/ only -- never shipped in the jar.
 */
import java.util.ArrayList;
import java.util.List;

public class HarnessByFilter {
    // arg slots (see the javacall block in sparkta.ado): 25 stack, 35 stat, 76 filters,
    // 84 showmissing by(), 151 sliders, 156 fit, 157 fitci, 202 noallfilter
    static String[] bf(String title, String file, String vars, String type, String over, String by, String filters) {
        String[] a = Harness.dataCase(title, file, vars, type, over, by, "");
        a[76] = filters;
        return a;
    }

    public static void main(String[] argv) throws Exception {
        Harness.outDir = argv[0];
        new java.io.File(Harness.outDir).mkdirs();
        List<String[]> cases = new ArrayList<>();
        String[] a;

        // --- bar family: one var / two vars, with and without over() ---
        cases.add(bf("BF01 bar 1var by+filter",              "bf01_bar_by.html",           "price",     "bar",  "",      "foreign", "rep78"));
        cases.add(bf("BF02 bar 1var over+by+filter",         "bf02_bar_over_by.html",      "price",     "bar",  "rep78", "foreign", "mpg"));
        cases.add(bf("BF03 bar 2var by+filter",              "bf03_bar_2var_by.html",      "price mpg", "bar",  "",      "foreign", "rep78"));
        cases.add(bf("BF04 bar 2var over+by+filter",         "bf04_bar_2var_over_by.html", "price mpg", "bar",  "rep78", "foreign", "mpg"));
        cases.add(bf("BF05 hbar over+by+filter",             "bf05_hbar_over_by.html",     "price",     "hbar", "rep78", "foreign", "mpg"));
        a = bf("BF06 bar stat(sum) over+by+filter",          "bf06_bar_sum_over_by.html",  "price",     "bar",  "rep78", "foreign", "mpg"); a[35] = "sum"; cases.add(a);
        a = bf("BF07 bar stat(median) 2var by+filter",       "bf07_bar_median_2var_by.html","price mpg","bar",  "",      "foreign", "rep78"); a[35] = "median"; cases.add(a);

        // --- line / area ---
        cases.add(bf("BF08 line 2var over+by+filter",        "bf08_line_2var_over_by.html","price mpg", "line", "rep78", "foreign", "mpg"));
        cases.add(bf("BF09 line 1var by+filter",             "bf09_line_by.html",          "price",     "line", "",      "foreign", "rep78"));
        cases.add(bf("BF10 area 2var over+by+filter",        "bf10_area_2var_over_by.html","price mpg", "area", "rep78", "foreign", "mpg"));

        // --- stacked family (Java maps the aliases; stackedarea/stackedline reach Java as area/line + stack flag) ---
        cases.add(bf("BF11 stackedbar 2var over+by+filter",  "bf11_stackedbar_by.html",    "price mpg", "stackedbar",     "rep78", "foreign", "mpg"));
        cases.add(bf("BF12 stackedhbar 2var over+by+filter", "bf12_stackedhbar_by.html",   "price mpg", "stackedhbar",    "rep78", "foreign", "mpg"));
        a = bf("BF13 stackedarea 2var over+by+filter",       "bf13_stackedarea_by.html",   "price mpg", "area", "rep78", "foreign", "mpg"); a[25] = "1"; cases.add(a);
        a = bf("BF14 stackedline 2var over+by+filter",       "bf14_stackedline_by.html",   "price mpg", "line", "rep78", "foreign", "mpg"); a[25] = "1"; cases.add(a);
        cases.add(bf("BF15 stackedbar100 over+by+filter",    "bf15_stack100_by.html",      "price mpg", "stackedbar100",  "rep78", "foreign", "mpg"));
        cases.add(bf("BF16 stackedhbar100 over+by+filter",   "bf16_stackh100_by.html",     "price mpg", "stackedhbar100", "rep78", "foreign", "mpg"));

        // --- scatter / bubble ---
        cases.add(bf("BF17 scatter by+filter",               "bf17_scatter_by.html",       "price weight",     "scatter", "",      "foreign", "rep78"));
        cases.add(bf("BF18 scatter over+by+filter",          "bf18_scatter_over_by.html",  "price weight",     "scatter", "rep78", "foreign", "mpg"));
        a = bf("BF19 scatter fit(lfit) by+filter",           "bf19_scatter_fit_by.html",   "price weight",     "scatter", "",      "foreign", "rep78"); a[156] = "lfit"; cases.add(a);
        cases.add(bf("BF20 bubble by+filter",                "bf20_bubble_by.html",        "price weight mpg", "bubble",  "",      "foreign", "rep78"));

        // --- pie / donut (over()+by() is rejected by the ado, so multi-var mode only) ---
        cases.add(bf("BF21 pie 2var by+filter",              "bf21_pie_2var_by.html",      "price mpg", "pie",   "", "foreign", "rep78"));
        cases.add(bf("BF22 donut 2var by+filter",            "bf22_donut_2var_by.html",    "price mpg", "donut", "", "foreign", "rep78"));

        // --- CI charts (over() is mandatory) ---
        cases.add(bf("BF23 cibar over+by+filter",            "bf23_cibar_over_by.html",    "price", "cibar",  "rep78", "foreign", "mpg"));
        cases.add(bf("BF24 ciline over+by+filter",           "bf24_ciline_over_by.html",   "price", "ciline", "rep78", "foreign", "mpg"));

        // --- histogram ---
        cases.add(bf("BF25 histogram by+filter",             "bf25_hist_by.html",          "price", "histogram", "", "foreign", "rep78"));

        // --- box / violin, vertical and horizontal, with and without over() ---
        cases.add(bf("BF26 boxplot by+filter",               "bf26_box_by.html",           "price", "boxplot",      "",      "foreign", "rep78"));
        cases.add(bf("BF27 boxplot over+by+filter",          "bf27_box_over_by.html",      "price", "boxplot",      "rep78", "foreign", "mpg"));
        cases.add(bf("BF28 hboxplot over+by+filter",         "bf28_hbox_over_by.html",     "price", "hboxplot",     "rep78", "foreign", "mpg"));
        cases.add(bf("BF29 violin by+filter",                "bf29_violin_by.html",        "price", "violin",       "",      "foreign", "rep78"));
        cases.add(bf("BF30 violin over+by+filter",           "bf30_violin_over_by.html",   "price", "violin",       "rep78", "foreign", "mpg"));
        cases.add(bf("BF31 hviolin over+by+filter",          "bf31_hviolin_over_by.html",  "price", "hviolinplot",  "rep78", "foreign", "mpg"));
        cases.add(bf("BF32 boxplot 2var by+filter",          "bf32_box_2var_by.html",      "price mpg", "boxplot",  "",      "foreign", "rep78"));

        // --- filter mechanics: sliders, showmissing, noallfilter, filter on the by() var ---
        a = bf("BF33 bar by + sliders(weight)",              "bf33_bar_by_slider.html",    "price", "bar", "",      "foreign", "");      a[151] = "weight"; cases.add(a);
        a = bf("BF34 bar over+by + filter + slider",         "bf34_bar_over_by_fs.html",   "price", "bar", "rep78", "foreign", "mpg");   a[151] = "weight"; cases.add(a);
        cases.add(bf("BF35 bar by(rep78) filter(foreign)",   "bf35_bar_byrep78.html",      "price", "bar", "",      "rep78",   "foreign"));
        a = bf("BF36 bar by(rep78, showmissing) filter",     "bf36_bar_byrep78_sm.html",   "price", "bar", "",      "rep78",   "foreign"); a[84] = "1"; cases.add(a);
        a = bf("BF37 bar by+filter noallfilter",             "bf37_bar_by_noall.html",     "price", "bar", "",      "foreign", "rep78"); a[202] = "1"; cases.add(a);
        cases.add(bf("BF38 bar over(foreign) by(rep78) filter(rep78)", "bf38_bar_filter_is_by.html", "price", "bar", "foreign", "rep78", "rep78"));
        cases.add(bf("BF39 scatter by + two filters",        "bf39_scatter_by_2filters.html", "price weight", "scatter", "", "foreign", "rep78|mpg"));

        // --- second sweep (fix9a): the remaining layout variants ---
        cases.add(bf("BF40 violin 2var by+filter",           "bf40_violin_2var_by.html",   "price mpg", "violin", "", "foreign", "rep78"));
        cases.add(bf("BF54 violin 2var over+by+filter (fix9d)", "bf54_violin_2var_over_by.html", "price mpg", "violin", "rep78", "foreign", "mpg"));   // fix9d: group x variable entries, local groups per panel
        a = bf("BF41 bar 2var over+by stat(sum)",            "bf41_bar_2var_over_sum.html","price mpg", "bar", "rep78", "foreign", "mpg"); a[35] = "sum"; cases.add(a);
        cases.add(bf("BF42 cibar 2var over+by+filter",       "bf42_cibar_2var_over_by.html","price mpg","cibar",  "rep78", "foreign", "mpg"));
        cases.add(bf("BF43 ciline 2var over+by+filter",      "bf43_ciline_2var_over_by.html","price mpg","ciline","rep78", "foreign", "mpg"));
        a = bf("BF44 scatter over + fit(lfit) by+filter",    "bf44_scatter_over_fit_by.html","price weight","scatter","rep78","foreign","mpg"); a[156] = "lfit"; cases.add(a);
        a = bf("BF45 scatter fit(qfit) fitci by+filter",     "bf45_scatter_fitci_by.html", "price weight", "scatter", "", "foreign", "rep78"); a[156] = "qfit"; a[157] = "1"; cases.add(a);
        a = bf("BF46 histogram frequency by+filter",         "bf46_hist_freq_by.html",     "price", "histogram", "", "foreign", "rep78"); a[82] = "frequency"; cases.add(a);
        a = bf("BF47 pie 2var stat(sum) by+filter",          "bf47_pie_sum_by.html",       "price mpg", "pie", "", "foreign", "rep78"); a[35] = "sum"; cases.add(a);
        cases.add(bf("BF48 bubble over+by+filter",           "bf48_bubble_over_by.html",   "price weight mpg", "bubble", "rep78", "foreign", "mpg"));
        cases.add(bf("BF49 stackedbar100 2var no over by+filter", "bf49_stack100_noover_by.html", "price mpg", "stackedbar100", "", "foreign", "rep78"));
        cases.add(bf("BF50 line 2var no over by+filter",     "bf50_line_2var_by.html",     "price mpg", "line", "", "foreign", "rep78"));
        cases.add(bf("BF51 hbar 2var by+filter",             "bf51_hbar_2var_by.html",     "price mpg", "hbar", "", "foreign", "rep78"));
        a = bf("BF52 bar 1var over+by stat(count)",          "bf52_bar_count_over_by.html","price", "bar", "rep78", "foreign", "mpg"); a[35] = "count"; cases.add(a);
        cases.add(bf("BF53 stackedbar 2var no over by+filter","bf53_stackedbar_noover_by.html","price mpg","stackedbar", "", "foreign", "rep78"));

        int rc = 0, n = 0;
        for (String[] c : cases) {
            int r;
            // emulate the ado's markout: rep78 is an over/by/filter var in every case except BF36
            // (by(rep78, showmissing)); BF33 has no rep78 at all so the flag is harmless there
            com.stata.sfi.Data.markoutRep78 = !c[2].startsWith("BF36");
            try { r = com.dashboard_test.DashboardBuilder.execute(c); }
            catch (Throwable t) { System.out.println("  EXC " + c[2] + ": " + t); r = 1; }
            System.out.println("  " + (r == 0 ? "ok  " : "FAIL") + " " + c[2]);
            if (r != 0) rc = 1;
            n++;
        }
        System.out.println("HarnessByFilter: " + n + " scenarios, rc=" + rc);
        System.exit(rc);
    }
}
