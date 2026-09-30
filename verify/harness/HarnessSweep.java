/*
 * HarnessSweep.java -- v3.6.0-t2j fix8q: post-fix8p QC sweep scenarios.
 *
 * Complements Harness.java (post-estimation + the original data cases) with the
 * chart types / paths that had no offline scenario yet: bubble (+filter, +by),
 * hbar, stacked 2-series (bar / hbar / line / area), stack100 plain, cibar plain,
 * histogram plain on a variable WITH missing (rep78 -> subtitle N check),
 * scatter over+filter (C8 candidate), pie/donut plain, hbox/hviolin, y2 line,
 * multi-line, dark bar. Reuses Harness.dataCase() so the arg layout is shared.
 *
 * Usage:  java -cp <classes>:<sfi-stub> HarnessSweep <outdir>
 * ASCII only. verify/ only -- never shipped in the jar.
 */
import java.util.ArrayList;
import java.util.List;

public class HarnessSweep {
    public static void main(String[] argv) throws Exception {
        Harness.outDir = argv[0];
        new java.io.File(Harness.outDir).mkdirs();
        List<String[]> cases = new ArrayList<>();
        String[] a;

        // --- bubble family (C8 axis lock + tooltips) ---
        cases.add(Harness.dataCase("Q-B1 bubble plain", "q_b1_bubble.html", "price weight mpg", "bubble", "", "", ""));
        a = Harness.dataCase("Q-B2 bubble + filter (axis lock)", "q_b2_bubble_filter.html", "price weight mpg", "bubble", "", "", ""); a[76] = "foreign"; cases.add(a);
        a = Harness.dataCase("Q-B3 bubble over + filter", "q_b3_bubble_over_filter.html", "price weight mpg", "bubble", "foreign", "", ""); a[76] = "rep78"; cases.add(a);
        a = Harness.dataCase("Q-B4 bubble by + filter", "q_b4_bubble_by_filter.html", "price weight mpg", "bubble", "", "foreign", ""); a[76] = "rep78"; cases.add(a);
        a = Harness.dataCase("Q-B5 bubble + filter + yrange", "q_b5_bubble_filter_yrange.html", "price weight mpg", "bubble", "", "", ""); a[76] = "foreign"; a[21] = "0"; a[22] = "20000"; cases.add(a);

        // --- scatter (C8 candidate) ---
        a = Harness.dataCase("Q-S1 scatter over + filter", "q_s1_scatter_over_filter.html", "price weight", "scatter", "foreign", "", ""); a[76] = "rep78"; cases.add(a);
        a = Harness.dataCase("Q-S2 scatter by + filter", "q_s2_scatter_by_filter.html", "price weight", "scatter", "", "foreign", ""); a[76] = "rep78"; cases.add(a);

        // --- bar family ---
        cases.add(Harness.dataCase("Q-H1 hbar over", "q_h1_hbar_over.html", "price", "hbar", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-K1 stackedbar 2 series", "q_k1_stackedbar2.html", "price mpg", "stackedbar", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-K2 stackedhbar 2 series", "q_k2_stackedhbar2.html", "price mpg", "stackedhbar", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-K3 stackedbar100 plain", "q_k3_stack100.html", "price mpg", "stackedbar100", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-K4 stackedhbar100", "q_k4_stackh100.html", "price mpg", "stackedhbar100", "rep78", "", ""));
        a = Harness.dataCase("Q-K5 stackedbar single var (C2 path)", "q_k5_stackedbar1.html", "price", "stackedbar", "rep78", "", ""); cases.add(a);
        cases.add(Harness.dataCase("Q-K6 bar 2 vars grouped", "q_k6_bar2.html", "price mpg", "bar", "rep78", "", ""));
        a = Harness.dataCase("Q-K7 bar dark", "q_k7_bar_dark.html", "price", "bar", "rep78", "", ""); a[3] = "dark"; cases.add(a);
        a = Harness.dataCase("Q-K8 bar stat(count)", "q_k8_bar_count.html", "price", "bar", "rep78", "", ""); a[35] = "count"; cases.add(a);

        // --- line family ---
        cases.add(Harness.dataCase("Q-L1 multi-line 3 vars", "q_l1_multiline.html", "price mpg weight", "line", "rep78", "", ""));
        a = Harness.dataCase("Q-L2 stackedline 2 series", "q_l2_stackedline.html", "price mpg", "line", "rep78", "", ""); a[25] = "1"; cases.add(a);
        a = Harness.dataCase("Q-L3 stackedarea 2 series", "q_l3_stackedarea.html", "price mpg", "area", "rep78", "", ""); a[25] = "1"; a[26] = "1"; cases.add(a);
        cases.add(Harness.dataCase("Q-L4 area plain", "q_l4_area.html", "price", "area", "rep78", "", ""));
        a = Harness.dataCase("Q-L5 line + y2", "q_l5_line_y2.html", "price mpg", "line", "rep78", "", ""); a[90] = "mpg"; cases.add(a);
        a = Harness.dataCase("Q-L6 line over + filter", "q_l6_line_filter.html", "price", "line", "rep78", "", ""); a[76] = "foreign"; cases.add(a);

        // --- CI family ---
        cases.add(Harness.dataCase("Q-C1 cibar over", "q_c1_cibar.html", "price", "cibar", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-C2 ciline over", "q_c2_ciline.html", "price", "ciline", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-C3 cibar 2 vars", "q_c3_cibar2.html", "price mpg", "cibar", "foreign", "", ""));

        // --- distribution family ---
        cases.add(Harness.dataCase("Q-D1 histogram rep78 (missing -> subtitle N)", "q_d1_hist_rep78.html", "rep78", "histogram", "", "", ""));
        cases.add(Harness.dataCase("Q-D2 histogram price", "q_d2_hist_price.html", "price", "histogram", "", "", ""));
        a = Harness.dataCase("Q-D3 histogram by", "q_d3_hist_by.html", "price", "histogram", "", "foreign", ""); cases.add(a);
        cases.add(Harness.dataCase("Q-D4 hboxplot over", "q_d4_hbox.html", "price", "hboxplot", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-D5 hviolin over", "q_d5_hviolin.html", "price", "hviolinplot", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-D6 boxplot over", "q_d6_box_over.html", "price", "boxplot", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-D7 violin over", "q_d7_violin_over.html", "price", "violin", "rep78", "", ""));

        // --- pie family ---
        cases.add(Harness.dataCase("Q-P1 pie over", "q_p1_pie.html", "price", "pie", "rep78", "", ""));
        cases.add(Harness.dataCase("Q-P2 donut over", "q_p2_donut.html", "price", "donut", "rep78", "", ""));

        // --- single-var subtitle N paths ---
        cases.add(Harness.dataCase("Q-N1 bar rep78 over foreign (missing in plotted var)", "q_n1_bar_rep78.html", "rep78", "bar", "foreign", "", ""));
        cases.add(Harness.dataCase("Q-N2 scatter rep78 x price", "q_n2_scatter_rep78.html", "rep78 price", "scatter", "", "", ""));

        int fail = 0;
        for (String[] c : cases) {
            int rc;
            try { rc = com.dashboard_test.DashboardBuilder.execute(c); }
            catch (Throwable t) { rc = -1; t.printStackTrace(); }
            java.io.File f = new java.io.File(c[4]);
            boolean ok = rc == 0 && f.exists() && f.length() > 1000;
            if (!ok) fail++;
            System.out.println((ok ? "  OK   " : "  FAIL ") + c[2] + "  rc=" + rc + "  -> " + f.getName());
        }
        System.out.println(fail == 0 ? "SWEEP: all " + cases.size() + " scenarios rendered" : "SWEEP: " + fail + " FAILED");
        System.exit(fail == 0 ? 0 : 1);
    }
}
