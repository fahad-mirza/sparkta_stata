/*
 * Harness.java -- headless driver for DashboardBuilder.execute() (v3.6.0-s8b)
 *
 * Builds a full 207-slot argument array exactly as sparkta.ado would, for a set
 * of post-estimation scenarios (coefplot / eventstudy / marginsplot), and writes
 * one HTML per scenario to the output directory. No Stata needed: post-estimation
 * types never read the dataset, so the SFI stub is sufficient.
 *
 * Usage:  java -cp <classes>:<sfi-stub> Harness <outdir>
 * Then:   node verify/js_check.js <outdir>
 *
 * Arg positions mirror project_memory.md Section 4 (v3.6.0 arg map).
 * ASCII only.
 */
import com.dashboard_test.DashboardBuilder;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Harness {
    static final int NARGS = 210;   // v3.6.0-t2h (batch 2a): + arg 209 (table opts)
    static final int LPATTERNS = 122, POINTSTYLE = 67, YLINE = 127, YLINELABEL = 131, YLINECOLOR = 129;
    static String outDir;

    /** s9g: a DATA chart through the SFI fixture (verify/sfi-stub/Data.java). */
    static String[] dataCase(String title, String file, String varlist, String type, String over, String by, String layout) {
        String[] a = base(type, title, file);
        a[0] = varlist; a[7] = over; a[8] = by; a[9] = "__touse";
        a[10] = layout;   // arg 10 = layout (vertical | horizontal | grid)
        return a;
    }

    static String[] base(String type, String title, String file) {
        String[] a = new String[NARGS];
        java.util.Arrays.fill(a, "");
        a[0]   = "";                 // varlist (none for post-est)
        a[1]   = type;
        a[2]   = title;
        a[3]   = "default";          // theme
        a[4]   = new File(outDir, file).getAbsolutePath();  // export
        a[9]   = "";                 // tousename
        // t2j fix8q: SPK_OFFLINE=1 renders every scenario self-contained (the ado default),
        // so the Playwright sweep runs without a network; unset keeps the CDN pages.
        a[88]  = System.getenv("SPK_OFFLINE") != null ? "1" : "";
        a[115] = "1";                // t2j fix8w (U21): export toolbar is the ado default now
        a[189] = outDir;             // c(pwd)
        return a;
    }

    // ---- Scenario builders -------------------------------------------------
    // Values below are the exact strings produced by test_session8.do runs
    // (auto.dta), so the harness output is comparable to the Stata output.

    static String[] mpT1() {                       // Pattern 1: margins rep78, whisker
        String[] a = base("marginsplot", "H-T1 margins rep78 (whisker)", "h_t1_mp_factor.html");
        a[175] = "5408.463|5950.863|6245.127|5930.341|6504.837";
        a[176] = "8493.102|7536.162|7086.792|6984.887|8009.899";
        a[177] = "2323.825|4365.564|5403.462|4875.796|4999.776";
        a[178] = "1|2|3|4|5"; a[5] = "Repair record 1978";   // t2j fix8w: bare levels; the ado sends the variable label as x title
        a[179] = "predict()";
        a[173] = "whisker";
        a[204] = "||||";
        a[185] = "none";            // ado: predictions get refval none (s8c); effects keep 0
        return a;
    }
    static String[] mpT3() {                       // area + connected
        String[] a = mpT1(); a[2] = "H-T3 area CI"; a[4] = new File(outDir,"h_t3_mp_area.html").getAbsolutePath();
        a[173] = "area"; a[190] = "1"; return a;
    }
    static String[] mpT4() {                       // 2 series with non-estimable nulls
        String[] a = base("marginsplot", "H-T4 rep78#foreign", "h_t4_mp_2series_nulls.html");
        a[175] = "4305.436|4741.423|5043.67|3975.127|8290.414~.|.|8724.012|9402.983|8688.867";
        a[176] = "7258.605|6247.901|5933.793|5443.167|11407.87~.|.|11316.61|10993.3|10199.34";
        a[177] = "1352.268|3234.944|4153.547|2507.087|5172.962~.|.|6131.416|7812.669|7178.392";
        a[178] = "1|2|3|4|5~1|2|3|4|5"; a[5] = "Repair record 1978";   // t2j fix8w: bare levels
        a[179] = "predict()"; a[173] = "whisker"; a[203] = "Domestic|Foreign"; a[185] = "none";
        a[204] = "||||~||||"; return a;
    }
    static String[] mpT5() { String[] a = mpT4(); a[2]="H-T5 2 series area"; a[4]=new File(outDir,"h_t5_mp_2series_area.html").getAbsolutePath(); a[173]="area"; a[190]="1"; return a; }
    static String[] mpT6() {                       // continuous at(weight)
        String[] a = base("marginsplot", "H-T6 at(weight) numeric x", "h_t6_mp_numericx.html");
        a[175] = "1801.6|2667.777|3533.953|4400.13|5266.306|6132.483|6998.659|7864.835|8731.012|9597.188|10463.36|11329.54|12195.72";
        a[176] = "3461.218|4030.373|4609.511|5209.325|5858.41|6626.642|7577.984|8655.315|9785.486|10937.63|12100.28|13268.61|14440.34";
        a[177] = "141.9823|1305.18|2458.395|3590.934|4674.202|5638.323|6419.334|7074.356|7676.538|8256.744|8826.452|9390.472|9951.098";
        a[178] = "1760|2010|2260|2510|2760|3010|3260|3510|3760|4010|4260|4510|4760";
        a[204] = a[178]; a[179] = "predict()"; a[5] = "weight"; a[173] = "area"; a[190] = "1"; a[185] = "none";
        return a;
    }
    static String[] mpT7() {                       // 2 series on numeric x
        String[] a = base("marginsplot", "H-T7 at(weight) x foreign", "h_t7_mp_numericx_2series.html");
        String x = "1760|2010|2260|2510|2760|3010|3260|3510|3760|4010|4260|4510|4760";
        a[175] = "709.6093|1575.786|2441.962|3308.139|4174.315|5040.492|5906.668|6772.845|7639.021|8505.197|9371.374|10237.55|11103.73~4382.67|5248.846|6115.023|6981.199|7847.376|8713.552|9579.728|10445.9|11312.08|12178.26|13044.43|13910.61|14776.79";
        a[176] = "2599.234|3170.66|3751.307|4348.793|4980.141|5683.583|6519.526|7504.583|8584.062|9708.768|10855.25|12013.22|13177.83~5842.958|6490.065|7185.828|7956.104|8823.117|9786.642|10824.23|11910.1|13025.78|14160.05|15306.3|16460.59|17620.47";
        a[177] = "-1180.016|-19.08879|1132.617|2267.484|3368.489|4397.4|5293.81|6041.106|6693.98|7301.627|7887.501|8461.883|9029.619~2922.381|4007.627|5044.218|6006.295|6871.634|7640.462|8335.225|8981.708|9598.38|10196.47|10782.57|11360.63|11933.1";
        a[178] = x + "~" + x; a[204] = a[178]; a[179] = "predict()"; a[5] = "weight";
        a[173] = "area"; a[190] = "1"; a[203] = "Domestic|Foreign"; return a;
    }
    static String[] mpT8() {                       // contrast with nulls + pexline(0)
        String[] a = base("marginsplot", "H-T8 contrast r.foreign@rep78", "h_t8_mp_contrast.html");
        a[175] = ".|.|3680.342|5427.856|398.453"; a[176] = ".|.|6551.651|7752.223|3688.677";
        a[177] = ".|.|809.0325|3103.489|-2891.772"; a[178] = "1|2|3|4|5"; a[5] = "Repair record 1978";   // t2j fix8w: bare levels; the ado sends the variable label as x title
        a[179] = "Contrast"; a[173] = "whisker"; a[196] = "0"; a[204] = "||||"; return a;
    }
    static String[] mpT10() {                      // nested CI levels(90 95)
        String[] a = base("marginsplot", "H-T10 nested 90/95", "h_t10_mp_nested.html");
        a[175] = "4754.886|5365.76|5849.784|6344.274|7450.064";
        a[176] = "8149.997|7089.515|6756.464|7483.883|9088.889";
        a[177] = "1359.775|3642.004|4943.104|5204.664|5811.24";
        a[197] = "1919.062|3925.964|5092.465|5392.396|6081.208";
        a[198] = "7590.71|6805.556|6607.103|7296.152|8818.92";
        a[199] = "90"; a[178] = "1|2|3|4|5"; a[5] = "Repair record 1978"; a[179] = "predict()";   // t2j fix8w: bare levels; the ado sends the variable label as x title
        a[173] = "whisker"; a[204] = "||||"; a[185] = "none"; return a;
    }
    static String[] mpT12() {                      // NEW: nested CI on numeric x (bug 1 regression)
        String[] a = mpT6(); a[2] = "H-T12 nested CI on numeric x"; a[4] = new File(outDir,"h_t12_mp_nested_numericx.html").getAbsolutePath();
        a[173] = "whisker"; a[199] = "90";
        // inner = 60% of outer half-width around v
        String[] v=a[175].split("\\|"), lo=a[177].split("\\|"), hi=a[176].split("\\|");
        StringBuilder l2=new StringBuilder(), h2=new StringBuilder();
        for (int i=0;i<v.length;i++){ double c=Double.parseDouble(v[i]), w=(Double.parseDouble(hi[i])-Double.parseDouble(lo[i]))/2*0.6;
            if(i>0){l2.append("|");h2.append("|");} l2.append(String.format(java.util.Locale.US,"%.3f",c-w)); h2.append(String.format(java.util.Locale.US,"%.3f",c+w)); }
        a[197]=l2.toString(); a[198]=h2.toString(); return a;
    }
    static String[] mpT13() {                      // NEW: nested CI with 2 series (bug 2 regression)
        String[] a = mpT4(); a[2] = "H-T13 nested CI 2 series"; a[4] = new File(outDir,"h_t13_mp_nested_2series.html").getAbsolutePath();
        a[199] = "90";
        a[197] = "1850.1|3480.2|4300.3|2750.4|5700.5~.|.|6600.6|8100.7|7450.8";
        a[198] = "6760.1|6000.2|5780.3|5200.4|10880.5~.|.|10850.6|10700.7|9930.8";
        return a;
    }
    static String[] mpT14() {                      // NEW: single point (k=1) edge case
        String[] a = base("marginsplot", "H-T14 single margin", "h_t14_mp_single.html");
        a[175]="5408.463"; a[176]="8493.102"; a[177]="2323.825"; a[178]="Overall"; a[179]="predict()"; a[173]="whisker"; a[204]=""; return a;
    }
    static String[] mpT15() {                      // NEW: all-null series (nothing estimable)
        String[] a = base("marginsplot", "H-T15 all null", "h_t15_mp_allnull.html");
        a[175]=".|.|."; a[176]=".|.|."; a[177]=".|.|."; a[178]="a|b|c"; a[179]="y"; a[173]="area"; a[204]="||"; return a;
    }
    static String[] mpT16() {                      // NEW: dark theme + levels + pexline + area
        String[] a = mpT10(); a[2]="H-T16 dark nested area pexline"; a[3]="dark_viridis";
        a[4]=new File(outDir,"h_t16_mp_dark.html").getAbsolutePath(); a[173]="area"; a[196]="6000"; return a;
    }
    static String[] cpT11() {                      // coefplot single model (margins,post)
        String[] a = base("coefplot", "H-T11 coefplot", "h_t11_cp_single.html");
        a[161]="rep78=1|rep78=2|rep78=3|rep78=4|rep78=5";
        a[162]="5408.463|5950.863|6245.127|5930.341|6504.837";
        a[163]="2323.825|4365.564|5403.462|4875.796|4999.776";
        a[164]="8493.102|7536.162|7086.792|6984.887|8009.899";
        a[165]="1543.113|793.0573|421.049|527.5441|752.9181";
        a[166]="0.001|0.000|0.000|0.000|0.000";
        a[167]="margins"; a[169]="69"; a[170]="price"; a[171]="h"; a[173]="whisker";
        a[181]="1"; a[182]="margins"; a[184]="3.50|7.50|14.83|11.24|8.64"; a[185]="0";
        return a;
    }
    static String[] cpT17() {                      // coefplot multi-model + levels + band
        String[] a = cpT11(); a[2]="H-T17 coefplot 2 models band nested"; a[4]=new File(outDir,"h_t17_cp_multi.html").getAbsolutePath();
        a[161]=a[161]+"~"+a[161]; a[162]=a[162]+"~"+"5000|6000|6100|6000|6600";
        a[163]=a[163]+"~"+"2000|4300|5300|4800|4900"; a[164]=a[164]+"~"+"8000|7700|6900|7200|8300";
        a[165]=a[165]+"~"+a[165]; a[166]=a[166]+"~"+a[166]; a[167]="Model A~Model B"; a[169]="69~69";
        a[184]=a[184]+"~"+a[184]; a[173]="band"; a[199]="90";
        a[197]="2900|4700|5500|5100|5300~2600|4800|5500|5000|5200"; a[198]="7900|7200|7000|6800|7700~7400|7200|6700|7000|8000";
        return a;
    }
    static String[] esT18() {                      // eventstudy vertical + connected + pexline
        String[] a = base("eventstudy", "H-T18 eventstudy", "h_t18_es.html");
        a[161]="t-3|t-2|t-1|t0|t+1|t+2|t+3"; a[162]="-0.1|0.05|0|0.4|0.6|0.7|0.65";
        a[163]="-0.4|-0.2|0|0.1|0.3|0.4|0.3"; a[164]="0.2|0.3|0|0.7|0.9|1.0|1.0";
        a[165]="0.15|0.12|0|0.15|0.15|0.15|0.17"; a[166]="0.5|0.7|1|0.01|0.0|0.0|0.0";
        a[167]="did"; a[169]="1200"; a[170]="y"; a[171]="v"; a[173]="band"; a[180]="t-1";
        a[181]="0"; a[182]="reghdfe"; a[184]="-0.7|0.4|.|2.7|4.0|4.7|3.8"; a[185]="0"; a[190]="1"; a[196]="0";
        return a;
    }

    // t2d: NUMERIC event study -- csdid-style single model (rectangles, pre/post colours)
    static String[] esT30() {
        String[] a = base("eventstudy", "H-T30 event study numeric (csdid style)", "h_t30_es_numeric.html");
        a[161]="Tm4|Tm3|Tm2|Tm1|Tp0|Tp1|Tp2|Tp3|Tp4"; a[162]="0.02|0.031|-0.004|-0.024|-0.020|-0.051|-0.137|-0.101|-0.12";
        a[163]="-0.01|0.003|-0.030|-0.052|-0.043|-0.083|-0.209|-0.190|-0.21"; a[164]="0.05|0.059|0.022|0.004|0.003|-0.019|-0.065|-0.012|-0.03";
        a[165]="0.015|0.014|0.013|0.014|0.011|0.016|0.035|0.045|0.046"; a[166]="0.18|0.03|0.78|0.09|0.07|0.001|0.000|0.02|0.01";
        a[167]=""; a[171]="v"; a[173]="bar"; a[181]="0"; a[182]="matrix r(table) (csdid)"; a[185]="0";
        a[207]="-4|-3|-2|-1|0|1|2|3|4";
        return a;
    }
    // t2d: two models overlaid -- shapes per model, dodged, nested levels, whiskers, connected
    static String[] esT31() {
        String[] a = esT30(); a[2]="H-T31 event study 2 models"; a[4]=new File(outDir,"h_t31_es_two_models.html").getAbsolutePath();
        a[161]=a[161]+"~"+a[161]; a[162]=a[162]+"~"+"0.01|0.02|0.00|-0.01|-0.03|-0.06|-0.10|-0.11|-0.13";
        a[163]=a[163]+"~"+"-0.02|-0.01|-0.03|-0.04|-0.06|-0.09|-0.15|-0.17|-0.20"; a[164]=a[164]+"~"+"0.04|0.05|0.03|0.02|0.00|-0.03|-0.05|-0.05|-0.06";
        a[165]=a[165]+"~"+a[165]; a[166]=a[166]+"~"+a[166]; a[167]="csdid~jwdid"; a[186]="Callaway-Sant'Anna~Wooldridge";
        a[207]=a[207]+"~"+a[207]; a[173]="whisker"; a[190]="1"; a[199]="90";
        a[197]="0.005|0.014|-0.020|-0.041|-0.034|-0.071|-0.181|-0.156|-0.176~-0.007|0.005|-0.019|-0.027|-0.043|-0.077|-0.131|-0.146|-0.170";
        a[198]="0.035|0.048|0.012|-0.007|-0.006|-0.031|-0.093|-0.046|-0.064~0.027|0.035|0.019|0.007|-0.017|-0.043|-0.069|-0.074|-0.090";
        return a;
    }

    public static void main(String[] argv) throws Exception {
        outDir = argv.length > 0 ? argv[0] : "harness_out";
        new File(outDir).mkdirs();
        List<String[]> cases = new ArrayList<>();
        cases.add(mpT1()); cases.add(mpT3()); cases.add(mpT4()); cases.add(mpT5()); cases.add(mpT6());
        cases.add(mpT7()); cases.add(mpT8()); cases.add(mpT10()); cases.add(mpT12()); cases.add(mpT13());
        cases.add(mpT14()); cases.add(mpT15()); cases.add(mpT16()); cases.add(cpT11()); cases.add(cpT17());
        cases.add(esT18());
        // ---- s9g: DATA charts through the SFI fixture (verify/sfi-stub Data.java) ----
        // t2j fix8w: legbgcolor() (spkLegendBg) and marginsplot cicolors() coverage
        { String[] a = dataCase("H-D10 line legbgcolor", "h_d10_line_legbg.html", "price mpg", "line", "rep78", "", ""); a[110] = "#fff3cd"; a[109] = "#7a5c00"; cases.add(a); }
        { String[] a = mpT4(); a[2] = "H-T39 2 series cicolors"; a[4] = new File(outDir,"h_t39_mp_cicolors.html").getAbsolutePath(); a[187] = "#e41a1c|#377eb8"; cases.add(a); }
        // t2j fix8w (U21): H-D1 is the notoolbar page (a[115]="0"); every other scenario carries the toolbar
        { String[] a = dataCase("H-D1 bar over foreign (notoolbar)", "h_d1_bar_over.html", "price", "bar", "foreign", "", ""); a[115] = "0"; cases.add(a); }
        cases.add(dataCase("H-D2 box two vars by rep78 (the crammed page)", "h_d2_box_by.html", "price mpg", "boxplot", "foreign", "rep78", "horizontal"));
        cases.add(dataCase("H-D3 bar over by grid", "h_d3_bar_over_by.html", "price", "bar", "foreign", "rep78", "grid"));
        cases.add(dataCase("H-D11 box by default layout (fix9e: exports two across)", "h_d11_box_by_vertical.html", "price mpg", "boxplot", "foreign", "rep78", ""));   // fix9e/9f: no layout() -> stacked on screen, grid in exports
        cases.add(dataCase("H-D12 box by layout(vertical) (fix9f: exports stacked)", "h_d12_box_by_keep.html", "price mpg", "boxplot", "foreign", "rep78", "vertical"));   // fix9f: explicit -> panels-keep
        cases.add(dataCase("H-D4 line over by", "h_d4_line_over_by.html", "price mpg", "line", "rep78", "foreign", ""));
        cases.add(dataCase("H-D5 violin by", "h_d5_violin_by.html", "price", "violin", "foreign", "rep78", ""));
        cases.add(dataCase("H-D6 scatter", "h_d6_scatter.html", "price weight", "scatter", "foreign", "", ""));
        { String[] a = dataCase("H-D8 line over rep78 + download", "h_d8_line_download.html", "price mpg", "line", "rep78", "", ""); a[115] = "1"; cases.add(a); }
        { String[] a = dataCase("H-D9 scatter lfit fitci + download", "h_d9_scatter_fit_download.html", "price weight", "scatter", "foreign", "", ""); a[115] = "1"; a[156] = "lfit"; a[157] = "1"; cases.add(a); }
        cases.add(esT30()); cases.add(esT31());
        // t2e: band CI split by phase + inner level + omitted base period (-1) + cicolors(pre|post)
        { String[] a = esT30(); a[2] = "H-T33 event study band + levels + base gap"; a[4] = new File(outDir,"h_t33_es_band_levels.html").getAbsolutePath();
          a[161]="lead5|lead4|lead3|lead2|lag0|lag1|lag2|lag3|lag4|lag5"; a[162]="-0.16|-0.01|0.27|0.35|0.05|-0.07|0.35|0.71|0.75|0.70";
          a[163]="-0.46|-0.30|-0.03|0.05|-0.23|-0.36|0.05|0.42|0.46|0.41"; a[164]="0.13|0.28|0.56|0.64|0.34|0.22|0.64|1.00|1.04|0.99";
          a[165]="0.15|0.15|0.15|0.15|0.15|0.15|0.15|0.15|0.15|0.15"; a[166]="0.3|0.9|0.07|0.02|0.7|0.6|0.02|0.001|0.001|0.001";
          a[182]="regress"; a[173]="band"; a[190]="1"; a[199]="90"; a[187]="#1b9e77|#d95f02";
          a[197]="-0.41|-0.25|0.02|0.10|-0.19|-0.31|0.10|0.46|0.50|0.45"; a[198]="0.09|0.23|0.51|0.59|0.29|0.17|0.59|0.96|1.00|0.95";
          a[207]="-5|-4|-3|-2|0|1|2|3|4|5"; a[208]="ref=auto;together=0"; cases.add(a); }
        // t2f: two models, base period inferred per model (-1), together = post joined to the reference, whiskers
        { String[] a = esT31(); a[2] = "H-T34 event study 2 models together + reference"; a[4] = new File(outDir,"h_t34_es_together_ref.html").getAbsolutePath();
          a[161]="Tm4|Tm3|Tm2|Tp0|Tp1|Tp2|Tp3|Tp4~Tm4|Tm3|Tm2|Tp0|Tp1|Tp2|Tp3|Tp4"; a[162]="0.02|0.031|-0.004|-0.020|-0.051|-0.137|-0.101|-0.12~0.01|0.02|0.00|-0.03|-0.06|-0.10|-0.11|-0.13";
          a[163]="-0.01|0.003|-0.030|-0.043|-0.083|-0.209|-0.190|-0.21~-0.02|-0.01|-0.03|-0.06|-0.09|-0.15|-0.17|-0.20"; a[164]="0.05|0.059|0.022|0.003|-0.019|-0.065|-0.012|-0.03~0.04|0.05|0.03|0.00|-0.03|-0.05|-0.05|-0.06";
          a[165]="0.015|0.014|0.013|0.011|0.016|0.035|0.045|0.046~0.015|0.014|0.013|0.011|0.016|0.035|0.045|0.046"; a[166]="0.18|0.03|0.78|0.07|0.001|0.000|0.02|0.01~0.18|0.03|0.78|0.07|0.001|0.000|0.02|0.01";
          a[197]=""; a[198]=""; a[199]=""; a[207]="-4|-3|-2|0|1|2|3|4~-4|-3|-2|0|1|2|3|4"; a[208]="ref=auto;together=1"; cases.add(a); }
        // t2f fix 3: lwdid-style explicit normalised row at -1 (b = lo = hi = 0) with no gap -> reference marker
        { String[] a = esT30(); a[2] = "H-T35 event study explicit zero row (lwdid)"; a[4] = new File(outDir,"h_t35_es_zero_row.html").getAbsolutePath();
          a[161]="Tm4|Tm3|Tm2|Tm1|Tp0|Tp1|Tp2|Tp3"; a[162]="-0.003|0.004|-0.001|0|-0.030|-0.063|-0.096|-0.128";
          a[163]="-0.025|-0.018|-0.022|0|-0.048|-0.084|-0.127|-0.163"; a[164]="0.019|0.026|0.020|0|-0.012|-0.042|-0.065|-0.093";
          a[165]="0.011|0.011|0.011|.|0.009|0.011|0.016|0.018"; a[166]="0.78|0.71|0.93|.|0.001|0.000|0.000|0.000";
          a[197]=""; a[198]=""; a[199]=""; a[207]="-4|-3|-2|-1|0|1|2|3"; a[208]="ref=auto;together=0"; cases.add(a); }
        { String[] a = esT31(); a[2] = "H-T32 event study 2 models + download"; a[4] = new File(outDir,"h_t32_es_download.html").getAbsolutePath(); a[115] = "1"; cases.add(a); }
        { String[] a = mpT10(); a[2] = "H-T28 nested + download button"; a[4] = new File(outDir,"h_t28_mp_download.html").getAbsolutePath(); a[115] = "1"; cases.add(a); }
        { String[] a = dataCase("H-D7 line by with dash + diamond + yline", "h_d7_line_by_styled.html", "price mpg", "line", "rep78", "foreign", "");
          a[LPATTERNS] = "solid|dash"; a[POINTSTYLE] = "rectRot"; a[YLINE] = "6000"; a[YLINELABEL] = "Mean price"; a[YLINECOLOR] = "red"; cases.add(a); }
        { String[] a = cpT11(); a[2] = "H-T30 bar coefplot + area CI"; a[4] = new File(outDir,"h_t30_cp_bar_area.html").getAbsolutePath(); a[172] = "bar"; a[173] = "area"; cases.add(a); }
        // t2j fix8x: coefstyle(bar) with the default whisker CI -- p > 0.1 bars are hollow (outline only), key "Hollow bar: p > 0.1"
        { String[] a = cpT11(); a[2] = "H-T40 bar coefplot + whisker CI (hollow bars)"; a[4] = new File(outDir,"h_t40_cp_bar_whisker.html").getAbsolutePath(); a[172] = "bar"; a[173] = "whisker";
          a[166] = "0.001|0.25|0.000|0.62|0.000"; cases.add(a); }   // coefficients 2 and 4 not significant -> hollow
        // fix8z consistency sweep: the hollow-bar encoding must read the same on every bar-coefplot variant
        { String[] a = cpT11(); a[2] = "H-T41 bar coefplot vertical + whisker"; a[4] = new File(outDir,"h_t41_cp_bar_v_whisker.html").getAbsolutePath(); a[172] = "bar"; a[173] = "whisker"; a[171] = "v";
          a[166] = "0.001|0.25|0.000|0.62|0.000"; cases.add(a); }
        { String[] a = cpT11(); a[2] = "H-T42 bar coefplot + area CI"; a[4] = new File(outDir,"h_t42_cp_bar_area_sig.html").getAbsolutePath(); a[172] = "bar"; a[173] = "area";
          a[166] = "0.001|0.25|0.000|0.62|0.000"; cases.add(a); }
        { String[] a = cpT11(); a[2] = "H-T43 bar coefplot + bar CI (dots too)"; a[4] = new File(outDir,"h_t43_cp_bar_barci.html").getAbsolutePath(); a[172] = "bar"; a[173] = "bar";
          a[166] = "0.001|0.25|0.000|0.62|0.000"; cases.add(a); }
        { String[] a = cpT11(); a[2] = "H-T44 bar coefplot + noci"; a[4] = new File(outDir,"h_t44_cp_bar_noci.html").getAbsolutePath(); a[172] = "bar"; a[173] = "whisker"; a[183] = "1";
          a[166] = "0.001|0.25|0.000|0.62|0.000"; cases.add(a); }
        { String[] a = cpT11(); a[2] = "H-T45 bar coefplot + levels(90 95)"; a[4] = new File(outDir,"h_t45_cp_bar_levels.html").getAbsolutePath(); a[172] = "bar"; a[173] = "whisker"; a[199] = "90";
          a[166] = "0.001|0.25|0.000|0.62|0.000"; cases.add(a); }
        // v3.6.0-s8v: Stata size words map to DECIMAL px (msize(large)=7.5, lwidth(thick)=3.5) and msymbol(D)=rectRot --
        // Java parsed pointsize with Integer.parseInt -> "For input string: 7.5" (rc=5101 on r_pe_cp_stata_aliases)
        { String[] a = cpT11(); a[2] = "H-T28 decimal sizes + diamond"; a[4] = new File(outDir,"h_t28_cp_decimal_sizes.html").getAbsolutePath(); a[28] = "7.5"; a[29] = "3.5"; a[67] = "rectRot"; a[171] = "v"; cases.add(a); }
        { String[] a = mpT1(); a[2] = "H-T29 marginsplot decimal sizes"; a[4] = new File(outDir,"h_t29_mp_decimal_sizes.html").getAbsolutePath(); a[28] = "7.5"; a[29] = "3.5"; a[67] = "crossRot"; cases.add(a); }
        // t2h batch 2a: publication-table options flow through arg 209
        { String[] a = cpT11(); a[2] = "H-T36 coefplot pub table (nostars, tstat, interval)"; a[4] = new File(outDir,"h_t36_cp_tblopts.html").getAbsolutePath(); a[209] = "stars=0.10 0.05 0.01|nostars=1|tstat=1|ci=1|nofooter=0|notable=0"; cases.add(a); }
        { String[] a = cpT11(); a[2] = "H-T37 coefplot notable (table suppressed)"; a[4] = new File(outDir,"h_t37_cp_notable.html").getAbsolutePath(); a[209] = "stars=0.10 0.05 0.01|nostars=0|tstat=0|ci=0|nofooter=0|notable=1"; cases.add(a); }
        // t2i hardening: hostile title + coefficient names (</script> breakout, apostrophe
        // attribute injection, < > &) must be neutralised in both HTML and inline JS.
        { String[] a = cpT11(); a[2] = "Hostile </script><img src=x onerror=alert(1)> 'title\" & <b>"; a[4] = new File(outDir,"h_t38_cp_hostile.html").getAbsolutePath();
          a[161] = "a</script>b|c'd|e<f>|g&h|caf\u00e9"; cases.add(a); }
        // t2i hardening: FILTERED data charts -- js_check runs _applyFilter() once, so
        // these exercise the per-type filter refresh paths (Astra: histogram/box/violin
        // _sAgg.getValues, ciline undeclared vars, bubble radius, pie/stack100 percent).
        // t2j: overall boxplot -- its emitted quartiles must equal Stata's default
        // summarize,detail (verify/pctile_check.js checks the exact numbers).
        { String[] a = dataCase("H-S1 boxplot price overall", "h_s1_box_overall.html", "price", "boxplot", "", "", ""); cases.add(a); }
        { String[] a = dataCase("H-F1 histogram + filter", "h_f1_hist_filter.html", "price", "histogram", "", "", ""); a[76] = "foreign"; cases.add(a); }
        { String[] a = dataCase("H-F2 boxplot over + filter", "h_f2_box_filter.html", "price", "boxplot", "rep78", "", ""); a[76] = "foreign"; cases.add(a); }
        { String[] a = dataCase("H-F3 violin over + filter", "h_f3_violin_filter.html", "price", "violin", "rep78", "", ""); a[76] = "foreign"; cases.add(a); }
        // (bubble+filter omitted from pixel coverage: Chart.js bubbles overflow the
        //  plot edge by design (s9a) and trip pixel_check --strict; bubble-on-filter
        //  radius loss is a documented known-remaining item, see project_memory t2i.)
        { String[] a = dataCase("H-F5 pie over + filter", "h_f5_pie_filter.html", "price", "pie", "rep78", "", ""); a[76] = "foreign"; cases.add(a); }
        { String[] a = dataCase("H-F6 stackedbar100 + filter", "h_f6_stack100_filter.html", "price mpg", "stackedbar100", "rep78", "", ""); a[76] = "foreign"; cases.add(a); }
        { String[] a = dataCase("H-F7 ciline over + filter", "h_f7_ciline_filter.html", "price", "ciline", "rep78", "", ""); a[76] = "foreign"; cases.add(a); }
        { String[] a = dataCase("H-F8 bar over+by+filter", "h_f8_bar_oby_filter.html", "price", "bar", "rep78", "foreign", ""); a[76] = "rep78"; cases.add(a); }  // \u00e9 = accented label (UTF-8 LaTeX robustness)
        // t2j: by()-panel violin + filter -- exercises _updatePanelChart violin branch
        // (per-panel _vFilter_N KDE recompute via _vAnimateTo). Was silently stale before.
        { String[] a = dataCase("H-F9 violin by + filter", "h_f9_violin_by_filter.html", "price", "violin", "", "foreign", ""); a[76] = "rep78"; cases.add(a); }
        // t2j F1: VALUE-LABELED over var (foreign: 0=Domestic,1=Foreign) + filter. The old
        // filter grouping compared raw _sd codes (0/1) to labels ("Domestic"/"Foreign") and
        // matched NOTHING -> both groups went empty on filter. These prove _si index grouping
        // populates both groups. filtergroup_check.js asserts >=2 non-empty groups post-filter.
        { String[] a = dataCase("H-F10 cibar over foreign + filter", "h_f10_cibar_lblover_filter.html", "price", "cibar", "foreign", "", ""); a[76] = "rep78"; cases.add(a); }
        { String[] a = dataCase("H-F11 boxplot over foreign + filter", "h_f11_box_lblover_filter.html", "price", "boxplot", "foreign", "", ""); a[76] = "rep78"; cases.add(a); }
        { String[] a = dataCase("H-F12 violin over foreign + filter", "h_f12_violin_lblover_filter.html", "price", "violin", "foreign", "", ""); a[76] = "rep78"; cases.add(a); }
        // t2j F3: pie multi-var mode (each var one summed slice) + filter exercises the
        // dedicated pie filter branch (mode1 sum). H-F5 covers mode2 (one var + over sum).
        // (mode3 frequency = no plot var + over: the SFI harness fixture does not load an
        //  over column with an empty varlist, so it can't be rendered here; the mode3
        //  filter branch mirrors the mode3 initial-render logic. Verify on a real run.)
        { String[] a = dataCase("H-F14 pie multivar + filter", "h_f14_pie_multivar_filter.html", "price mpg", "pie", "", "", ""); a[76] = "foreign"; cases.add(a); }
        // fix9d: multi-variable violins must refresh EVERY violin on a filter change (single page)
        { String[] a = dataCase("H-F15 violin 2var + filter", "h_f15_violin_2var_filter.html", "price mpg", "violin", "", "", ""); a[76] = "foreign"; cases.add(a); }
        { String[] a = dataCase("H-F16 violin 2var over + filter", "h_f16_violin_2var_over_filter.html", "price mpg", "violin", "foreign", "", ""); a[76] = "rep78"; cases.add(a); }
        // t2j X1: numeric xrange() on a bar chart's CATEGORY axis must be IGNORED, not
        // applied as category indices (which positioned the window past the last
        // category and blanked the chart). pixel_check flags a blank plot area.
        { String[] a = dataCase("H-X1 bar over + xrange on category axis", "h_x1_bar_xrange_cat.html", "price", "bar", "rep78", "", ""); a[19] = "1000"; a[20] = "20000"; cases.add(a); }
        { String[] a = mpT1(); a[2] = "H-T27 negative plotmargin"; a[4] = new File(outDir,"h_t27_mp_plotmargin_neg.html").getAbsolutePath(); a[205] = "5 5 -20 -20"; cases.add(a); }
        // v3.6.0-s8p: plotmargin(l r b t) percent
        { String[] a = mpT6(); a[2] = "H-T26 plotmargin 15 15 25 25"; a[4] = new File(outDir,"h_t26_mp_plotmargin.html").getAbsolutePath(); a[205] = "15 15 25 25"; cases.add(a); }
        // v3.6.0-s8m: connected nested ribbons, and the same with ciwhiskers overlay
        { String[] a = mpT10(); a[2] = "H-T23 nested ribbons connected"; a[4] = new File(outDir,"h_t23_mp_nested_ribbon.html").getAbsolutePath(); a[173] = "area"; a[190] = "1"; cases.add(a); }
        { String[] a = mpT10(); a[2] = "H-T24 nested ribbons + whiskers"; a[4] = new File(outDir,"h_t24_mp_nested_ribbon_whisk.html").getAbsolutePath(); a[173] = "area whisker"; a[190] = "1"; cases.add(a); }
        { String[] a = mpT10(); a[2] = "H-T25 nested rects + whiskers"; a[4] = new File(outDir,"h_t25_mp_nested_rect_whisk.html").getAbsolutePath(); a[173] = "area+whisker"; cases.add(a); }
        // v3.6.0-s8d: cistyle(bar) = bars + CI whiskers (single and grouped)
        { String[] a = mpT1(); a[2] = "H-T21 bars + CI"; a[4] = new File(outDir,"h_t21_mp_bar.html").getAbsolutePath(); a[173] = "bar"; cases.add(a); }
        { String[] a = mpT4(); a[2] = "H-T22 grouped bars + CI"; a[4] = new File(outDir,"h_t22_mp_bar_2series.html").getAbsolutePath(); a[173] = "bar"; cases.add(a); }
        // v3.6.0-s8c: multi-model with UNEQUAL coefficient counts (m1:1, m2:2, m3:3) -- the shape
        // that produced "Index 1 out of bounds for length 1" (rc=5101) in Stata
        { String[] a = cpT11(); a[2] = "H-T20 multi unequal whisker"; a[4] = new File(outDir,"h_t20_cp_multi_unequal.html").getAbsolutePath();
          a[161] = "mpg~mpg|weight~mpg|weight|foreign"; a[162] = "-238.9~-49.5|1.75~-49.5|1.75|3673";
          a[163] = "-347~-171|1.05~-171|1.05|2308";     a[164] = "-130~72|2.45~72|2.45|5037";
          a[165] = "53.1~61|0.35~61|0.35|684";           a[166] = "0.000~0.42|0.000~0.42|0.000|0.000";
          a[167] = "m1~m2~m3"; a[169] = "74~74~74"; a[173] = "whisker"; a[184] = "-4.5~-0.81|5.0~-0.81|5.0|5.37";
          a[186] = "Simple~Controls~Full"; a[197] = ""; a[198] = ""; a[199] = ""; cases.add(a); }
        // offline mode: exercises DashboardBuilder.checkOfflinePreflight + embedded libs
        { String[] a = mpT10(); a[2] = "H-T19 OFFLINE nested"; a[4] = new File(outDir,"h_t19_mp_offline.html").getAbsolutePath(); a[88] = "1"; cases.add(a); }
        int fail = 0;
        for (String[] a : cases) {
            int rc;
            try { rc = DashboardBuilder.execute(a); }
            catch (Throwable t) { rc = -1; System.out.println("  EXCEPTION " + a[2] + ": " + t); t.printStackTrace(System.out); }
            boolean ok = rc == 0 && new File(a[4]).length() > 1000;
            if (!ok) fail++;
            System.out.println((ok ? "  OK   " : "  FAIL ") + a[2] + "  rc=" + rc + "  -> " + new File(a[4]).getName());
        }
        System.out.println(fail == 0 ? "HARNESS: all " + cases.size() + " scenarios rendered" : "HARNESS: " + fail + " FAILED");
        System.exit(fail == 0 ? 0 : 1);
    }
}
