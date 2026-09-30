#!/usr/bin/env python3
"""build_sthlp.py -- sparkta v3.6.0 help-file rebuild (2026-09-13, docs item 5; rev 2 2026-09-14: "Large data" paragraph, fix9i)

Rebuilds ado/sparkta.sthlp from three sources:
  1. NEW front matter, chart-type catalogue (data AND post-estimation types),
     post-estimation reference, export/saveas, offline/online, examples -- written here;
  2. the COMPLETE option table, generated from the syntax block of ado/sparkta.ado so
     no option can be missing (a check at the end proves every option is listed);
  3. the detailed narrative sections of the previous help file (verify/sparkta.sthlp.pre-docs),
     spliced in by marker and patched where they were stale.
Run from the package root:  python3 verify/build_sthlp.py
ASCII only. Every SMCL directive is kept on one line (a {cmd:...} that wraps over a
line break renders as a stray brace -- that was the "some {" report).
"""
import re, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADO = os.path.join(ROOT, "ado", "sparkta.ado")
OLD = os.path.join(ROOT, "verify", "sparkta.sthlp.pre-docs")
OUT = os.path.join(ROOT, "ado", "sparkta.sthlp")

# ----------------------------------------------------------------------------
# 1. options from the ado syntax block
# ----------------------------------------------------------------------------
ado_lines = open(ADO).read().split("\n")
opts = []
in_syntax = False
for l in ado_lines:
    if re.match(r"\s*syntax \[varlist", l): in_syntax = True; continue
    if in_syntax:
        m = re.match(r"\s*\[([A-Za-z0-9]+)(\([^\]]*\))?\]\s*//+\s*(.*)", l)
        if m: opts.append((m.group(1).lower(), m.group(2) or "", m.group(3).strip()))
        if re.match(r"\s*\[COEFLABels", l): break
names = [o[0] for o in opts]
assert len(opts) >= 200, len(opts)

# group -> [(name, argspec-for-display, description)]  -- descriptions curated, ASCII
G = {}
def g(group, name, arg, desc):
    assert name in names, "not in syntax block: " + name
    G.setdefault(group, []).append((name, arg, desc))

g("Essential", "type", "charttype", "chart type (default {cmd:bar}); see {helpb sparkta##types:Chart types}")
g("Essential", "over", "varname [, showmissing]", "group variable: all groups on one chart")
g("Essential", "by", "varname [, showmissing]", "panel variable: one chart per group")
g("Essential", "filters", "varlist [, showmissing]", "live dropdown filters (one per variable, up to 500 levels each)")
g("Essential", "sliders", "varlist", "dual-handle range sliders for numeric variables")
g("Essential", "stat", "statistic", "{cmd:mean} (default) | {cmd:sum} | {cmd:count} | {cmd:median} | {cmd:min} | {cmd:max}; pie/donut: {cmd:pct} | {cmd:sum}")
g("Essential", "title", "string", "chart title")
g("Essential", "export", "filename.html", "write the page to a file instead of opening the browser")
g("Essential", "saveas", "files [, suboptions]", "also write PNG / PDF / SVG through a headless browser: chart|page|table scale(#) close idle(#); see {helpb sparkta##saveas:saveas()}")

g("Post-estimation input", "matrix", "spec", "plot from a matrix: M, M[#], M[,#], r(table), or several (A B C) for a multi-model chart; see {helpb sparkta##matrixmode:matrix()}")
g("Post-estimation input", "results", "source [, suboptions]", "plot from a dataset or frame of estimates (several sources = several models); see {helpb sparkta##results:results()}")
g("Post-estimation input", "estnames", "namelist", "stored estimates ({cmd:estimates store}) for a multi-model coefplot")
g("Post-estimation input", "ci", "spec", "matrix mode: lower/upper bounds by position ci((2 3)), name ci(ll ul), or another matrix ci(C)")
g("Post-estimation input", "se", "# | name | S", "matrix mode: standard errors -> CI at {cmd:cilevel()} (z, or t with {cmd:df()})")
g("Post-estimation input", "df", "# | name", "matrix mode: degrees of freedom for a t-based CI")
g("Post-estimation input", "pvalue", "# | name", "matrix mode: p-values row/column (autodetected from r(table))")

g("Coefficient selection and labels", "show", "namelist", "keep only these coefficients (eventstudy: the lead/lag terms; {cmd:show(*)} also keeps Pre_avg/Post_avg)")
g("Coefficient selection and labels", "omit", "namelist", "drop coefficients by name, e.g. {cmd:omit(_cons 1.foreign)}")
g("Coefficient selection and labels", "keep", "namelist", "= {cmd:show()} (Jann alias)")
g("Coefficient selection and labels", "drop", "namelist", "= {cmd:omit()} (Jann alias)")
g("Coefficient selection and labels", "nocons", "", "drop _cons (the default on coefplot/eventstudy)")
g("Coefficient selection and labels", "cons", "", "keep _cons on charts that hide it by default")
g("Coefficient selection and labels", "nobase", "", "drop base and omitted factor levels (b. and o. prefixes)")
g("Coefficient selection and labels", "order", "namelist", "display order of the coefficients")
g("Coefficient selection and labels", "coefsort", "value|abs|pval|se|desc", "sort coefficients ({cmd:sort()} accepts Jann's asc|desc|b|p|se|abs on these charts)")
g("Coefficient selection and labels", "coeflabels", "entries", "display labels: coeflabels(\"mpg/Miles per gallon\") or coeflabels(mpg = \"Miles per gallon\")")
g("Coefficient selection and labels", "coeflbl", "entries", "pipe-separated form of the same: coeflbl(mpg \"MPG\"|weight \"Wt\")")
g("Coefficient selection and labels", "headings", "entries", "section headings before coefficient #, chart and table: headings(1 \"Vehicle\"|3 \"Origin\")")
g("Coefficient selection and labels", "estlabels", "L1~L2~L3", "model labels for a multi-model chart, tilde-separated")
g("Coefficient selection and labels", "plotlabels", "labels", "= {cmd:estlabels()} (Jann alias): plotlabels(\"OLS\" \"IV\")")

g("Post-estimation look", "eform", "", "exponentiate (odds/hazard ratios); reference line moves to 1")
g("Post-estimation look", "rescale", "#", "multiply estimates and CIs by #, e.g. {cmd:rescale(1000)}")
g("Post-estimation look", "cilevel", "#", "confidence level 1-99 (default 95); also for cibar/ciline")
g("Post-estimation look", "level", "#", "= {cmd:cilevel()} (Stata alias)")
g("Post-estimation look", "levels", "# #", "two nested CIs, inner first, e.g. {cmd:levels(90 95)}")
g("Post-estimation look", "noci", "", "no confidence intervals")
g("Post-estimation look", "cistyle", "style [style]", "how the CI is drawn: whisker (default) | band | bar | area; a pair such as cistyle(area whisker) on marginsplot")
g("Post-estimation look", "recastci", "rcap|rarea|rband|rbar|rline", "= {cmd:cistyle()} in Stata's {cmd:recastci()} words")
g("Post-estimation look", "coefstyle", "scatter|bar", "point markers (default) or bars for the estimates")
g("Post-estimation look", "connected", "", "join the point estimates with a line")
g("Post-estimation look", "recast", "connected|line", "= {cmd:connected} (marginsplot alias)")
g("Post-estimation look", "vertical", "", "coefficients along x, estimates on y (coefplot; the eventstudy default)")
g("Post-estimation look", "refval", "# | none", "reference line value (default 0, or 1 with {cmd:eform})")
g("Post-estimation look", "pexline", "#", "vertical reference line at an x value, e.g. {cmd:pexline(0)}")
g("Post-estimation look", "reflinewidth", "#", "reference-line width px (default 1)")
g("Post-estimation look", "cicolors", "c1|c2|...", "CI colours per model; eventstudy: {cmd:cicolors(pre|post)}")
g("Post-estimation look", "ciwidth", "#", "CI whisker/band line thickness px (default 1.5)")
g("Post-estimation look", "pointstyles", "shape shape ...", "marker shape per model/series")
g("Post-estimation look", "refperiod", "auto|none|#", "eventstudy: the omitted (reference) period, drawn as a hollow marker at 0")
g("Post-estimation look", "together", "", "eventstudy: join the post series to the reference period (default from estimation results)")
g("Post-estimation look", "separate", "", "eventstudy: leads and lags as separate series (default with {cmd:matrix()}/{cmd:results()})")

g("Publication table", "stars", "numlist", "significance thresholds high to low (default {cmd:0.10 0.05 0.01})")
g("Publication table", "nostars", "", "no stars and no star legend")
g("Publication table", "tstat", "", "t/z statistic in brackets beneath each estimate")
g("Publication table", "interval", "", "confidence interval beneath each estimate")
g("Publication table", "nofooter", "", "drop the N / R-squared / legend footer")
g("Publication table", "notable", "", "no publication table (chart only)")
g("Publication table", "indicators", "entries", "fixed-effect / indicator rows, one value per model: indicators(\"Firm FE/Yes Yes No\")")
g("Publication table", "addstats", "entries", "extra footer rows: keywords rsq arsq fstat fpval ll chi2 rmse dep_mean, or manual rows \"Label/v1 v2\"")

g("Panels and grouping", "layout", "vertical|horizontal|grid", "arrangement of {cmd:by()} panels; exports use a two-column grid unless layout(vertical) is written out")
g("Panels and grouping", "yfree", "", "each {cmd:by()} panel keeps its own value axis (default: one shared axis)")
g("Panels and grouping", "sortgroups", "asc|desc", "order of {cmd:over()}/{cmd:by()} groups")
g("Panels and grouping", "nomissing", "", "exclude missing values (already the default; accepted for old scripts)")
g("Panels and grouping", "novaluelabels", "", "raw numeric codes instead of value labels")
g("Panels and grouping", "nolabel", "", "= {cmd:novaluelabels} (Stata alias)")
g("Panels and grouping", "noallfilter", "", "no {it:All} entry in the filter dropdowns")
g("Panels and grouping", "nostats", "", "no summary statistics panel")
g("Panels and grouping", "collapsestats", "", "statistics panel starts collapsed")

g("Axes", "xtitle", "string", "x-axis title"); g("Axes", "ytitle", "string", "y-axis title")
g("Axes", "xrange", "min max", "x-axis bounds (value axes only)"); g("Axes", "yrange", "min max", "y-axis bounds (value axes only)")
g("Axes", "ystart", "zero", "anchor the y-axis at zero")
g("Axes", "xtype", "linear|log|category|time", "x-axis scale ({cmd:time} falls back to category, see {helpb sparkta##axes:Axes})")
g("Axes", "ytype", "linear|log", "y-axis scale")
g("Axes", "xreverse", "", "x runs right to left"); g("Axes", "yreverse", "", "y runs top to bottom")
g("Axes", "xtickcount", "#", "approximate x tick count"); g("Axes", "ytickcount", "#", "approximate y tick count")
g("Axes", "xstepsize", "#", "x tick interval"); g("Axes", "ystepsize", "#", "y tick interval")
g("Axes", "xticks", "v|v|...", "pin x tick positions"); g("Axes", "yticks", "v|v|...", "pin y tick positions")
g("Axes", "xlabels", "l|l|...", "custom x tick labels"); g("Axes", "ylabels", "l|l|...", "custom y tick labels")
g("Axes", "xtickangle", "#", "x tick label rotation, degrees"); g("Axes", "ytickangle", "#", "y tick label rotation, degrees")
g("Axes", "noticks", "", "hide tick marks (labels stay)")
g("Axes", "ygrace", "#", "padding above the y maximum as a fraction, e.g. {cmd:0.1}")
g("Axes", "plotmargin", "# | x y | l r b t", "cushion between data and axes, percent of the data range, like plotregion(margin()); negative allowed on post-estimation charts")
g("Axes", "xgridlines", "on|off", "vertical grid lines"); g("Axes", "ygridlines", "on|off", "horizontal grid lines")
g("Axes", "xborder", "on|off", "x-axis border line"); g("Axes", "yborder", "on|off", "y-axis border line")
g("Axes", "gridcolor", "color", "grid line colour"); g("Axes", "gridopacity", "#", "grid line opacity 0-1")
g("Axes", "y2", "varlist", "variables on a right-hand y-axis"); g("Axes", "y2title", "string", "right y-axis title"); g("Axes", "y2range", "min max", "right y-axis bounds")

g("Titles and text", "subtitle", "string", "subtitle (default: an automatic one-line description of what is plotted)")
g("Titles and text", "note", "string", "note below the chart"); g("Titles and text", "caption", "string", "caption below the note")
g("Titles and text", "notesize", "css size", "font size of note and caption, e.g. {cmd:notesize(14px)}")
g("Titles and text", "notimestamp", "", "no {it:Made with sparkta ... date} footer line")
g("Titles and text", "datalabels", "", "value labels on bars, points and slices")
g("Titles and text", "pielabels", "", "percentage labels on pie/donut slices")
g("Titles and text", "titlesize", "#", "title font size"); g("Titles and text", "titlecolor", "color", "title colour")
g("Titles and text", "subtitlesize", "#", "subtitle font size"); g("Titles and text", "subtitlecolor", "color", "subtitle colour")
g("Titles and text", "xtitlesize", "#", "x-axis title size"); g("Titles and text", "xtitlecolor", "color", "x-axis title colour")
g("Titles and text", "ytitlesize", "#", "y-axis title size"); g("Titles and text", "ytitlecolor", "color", "y-axis title colour")
g("Titles and text", "xlabsize", "#", "x tick label size"); g("Titles and text", "xlabcolor", "color", "x tick label colour")
g("Titles and text", "ylabsize", "#", "y tick label size"); g("Titles and text", "ylabcolor", "color", "y tick label colour")

g("Legend", "legend", "top|bottom|left|right|none", "legend position (default top)")
g("Legend", "nolegend", "", "= {cmd:legend(none)}")
g("Legend", "leglabels", "l|l|...", "rename legend entries in series order")
g("Legend", "relabel", "l|l|...", "rename {cmd:over()} groups on the axis AND in the legend")
g("Legend", "legtitle", "string", "legend heading"); g("Legend", "legsize", "#", "legend font size px")
g("Legend", "legboxheight", "#", "legend swatch height px"); g("Legend", "legcolor", "color", "legend text colour")
g("Legend", "legbgcolor", "color", "box drawn behind the legend entries")

g("Colours and theme", "theme", "name", "background and/or palette; see {helpb sparkta##themes:Themes}")
g("Colours and theme", "colors", "c c ...", "series colours: Stata names, hex, \"r g b\" triplets, {it:name}%50 opacity, {it:name}*0.6 intensity; see {helpb sparkta##colours:Colour grammar}")
g("Colours and theme", "mcolor", "c c ...", "= {cmd:colors()} (twoway alias)")
g("Colours and theme", "palette", "colorpalette spec", "palette from Ben Jann's {cmd:colorpalette} (SSC), e.g. {cmd:palette(okabe)}, {cmd:palette(viridis, n(6) reverse)}")
g("Colours and theme", "bgcolor", "color", "page background"); g("Colours and theme", "plotcolor", "color", "chart card background")
g("Colours and theme", "opacity", "#", "fill opacity 0-1")
g("Colours and theme", "gradient", "", "gradient fill from the palette colours")
g("Colours and theme", "gradcolors", "start|end", "custom gradient colours (per-series sets separated by a colon)")

g("Bars", "horizontal", "", "horizontal bars (= {cmd:type(hbar)})"); g("Bars", "stacked", "", "stack the series")
g("Bars", "barwidth", "#", "bar thickness 0-1 (default 0.8)"); g("Bars", "bargroupwidth", "#", "width of a bar group 0-1 (default 0.8)")
g("Bars", "borderradius", "#", "rounded bar corners px")

g("Lines and points", "fill", "", "fill under the line (= {cmd:type(area)})")
g("Lines and points", "areaopacity", "#", "area fill opacity 0-1")
g("Lines and points", "smooth", "#", "line tension 0-1 (default 0.3)")
g("Lines and points", "stepped", "before|after|middle", "step line")
g("Lines and points", "spanmissing", "", "connect the line across missing values")
g("Lines and points", "linewidth", "#", "line width px (default 2)"); g("Lines and points", "lwidth", "vthin..vthick | #", "= {cmd:linewidth()} (twoway alias)")
g("Lines and points", "lpattern", "solid|dash|dot|dashdot", "dash pattern for every series"); g("Lines and points", "lpatterns", "p|p|...", "dash pattern per series")
g("Lines and points", "pointsize", "#", "marker radius px (default 4)"); g("Lines and points", "msize", "vtiny..vhuge | #", "= {cmd:pointsize()} (twoway alias)")
g("Lines and points", "pointstyle", "shape", "marker shape: circle (default) | cross | dash | line | rect | rectRounded | star | triangle")
g("Lines and points", "msymbol", "O|D|T|S|X|+", "= {cmd:pointstyle()} in twoway letters")
g("Lines and points", "pointborderwidth", "#", "marker border px"); g("Lines and points", "pointrotation", "#", "marker rotation degrees")
g("Lines and points", "pointhoversize", "#", "marker radius on hover px"); g("Lines and points", "nopoints", "", "no markers on line/area charts")

g("Scatter and bubble", "fit", "lfit|qfit|lowess|exp|log|power|ma", "fitted line on a scatter")
g("Scatter and bubble", "fitci", "", "95% band around the fit (lfit qfit exp log power)")
g("Scatter and bubble", "mlabel", "varname [, all]", "marker labels from a variable ({cmd:all} forces every label)")
g("Scatter and bubble", "mlabpos", "0-59", "label position on a minute clock (15 right, 30 below, 45 left)")
g("Scatter and bubble", "mlabvposition", "varname", "per-observation minute-clock position")

g("Distributions", "bins", "#", "histogram bins (default: Stata's rule, see {helpb sparkta##methods:Statistical methods})")
g("Distributions", "histtype", "density|frequency|fraction", "histogram y scale")
g("Distributions", "whiskerfence", "#", "box whisker fence multiplier (default 1.5)")
g("Distributions", "bandwidth", "#", "violin kernel bandwidth (default: Stata {cmd:kdensity} rule)")
g("Distributions", "mediancolor", "color", "median marker colour"); g("Distributions", "meancolor", "color", "mean marker colour")
g("Distributions", "cibandopacity", "#", "ciline band opacity (default 0.18)")

g("Pie and donut", "cutout", "#", "donut hole percent"); g("Pie and donut", "rotation", "#", "start angle degrees")
g("Pie and donut", "circumference", "#", "arc degrees (180 = half)"); g("Pie and donut", "sliceborder", "#", "gap between slices px")
g("Pie and donut", "hoveroffset", "#", "slice pop-out on hover px")

g("Reference lines and annotations", "yline", "v|v", "horizontal reference lines"); g("Reference lines and annotations", "xline", "v|v", "vertical reference lines (value x-axes)")
g("Reference lines and annotations", "ylinecolor", "c|c", "yline colours"); g("Reference lines and annotations", "xlinecolor", "c|c", "xline colours")
g("Reference lines and annotations", "ylinelabel", "t|t", "yline labels"); g("Reference lines and annotations", "xlinelabel", "t|t", "xline labels")
g("Reference lines and annotations", "yband", "lo hi|lo hi", "horizontal shaded bands"); g("Reference lines and annotations", "xband", "lo hi|lo hi", "vertical shaded bands")
g("Reference lines and annotations", "ybandcolor", "c|c", "yband fills"); g("Reference lines and annotations", "xbandcolor", "c|c", "xband fills")
g("Reference lines and annotations", "apoint", "y x y x ...", "annotation points"); g("Reference lines and annotations", "apointcolor", "c|c", "point colours"); g("Reference lines and annotations", "apointsize", "#", "point radius px")
g("Reference lines and annotations", "alabelpos", "y x pos|...", "annotation label positions (minute clock)"); g("Reference lines and annotations", "alabeltext", "t|t", "annotation label texts")
g("Reference lines and annotations", "alabelfs", "#", "annotation label font px"); g("Reference lines and annotations", "alabelgap", "#", "label offset from the point px")
g("Reference lines and annotations", "aellipse", "ymin xmin ymax xmax|...", "annotation ellipses"); g("Reference lines and annotations", "aellipsecolor", "c|c", "ellipse fills"); g("Reference lines and annotations", "aellipseborder", "c|c", "ellipse borders")

g("Tooltip and animation", "tooltipformat", "string", "number format, e.g. {cmd:,.0f}")
g("Tooltip and animation", "tooltipmode", "index|point|nearest|x|y", "hover mode (default index; post-estimation charts use nearest)")
g("Tooltip and animation", "tooltipposition", "average|nearest", "tooltip anchor")
g("Tooltip and animation", "tooltipbg", "color", "tooltip background"); g("Tooltip and animation", "tooltipborder", "color", "tooltip border")
g("Tooltip and animation", "tooltipfontsize", "#", "tooltip font size"); g("Tooltip and animation", "tooltippadding", "#", "tooltip padding px")
g("Tooltip and animation", "animate", "none|fast|slow", "animation preset"); g("Tooltip and animation", "animduration", "#", "animation duration ms")
g("Tooltip and animation", "animdelay", "#", "animation delay ms"); g("Tooltip and animation", "easing", "name", "Chart.js easing function")
g("Tooltip and animation", "aspect", "#", "width/height ratio (default 2; pie/donut 2)"); g("Tooltip and animation", "padding", "# | t r b l", "inner chart padding px")

g("Page, export and browser", "online", "", "load Chart.js from a CDN instead of embedding it (smaller file, needs internet to view)")
g("Page, export and browser", "offline", "", "self-contained page -- the default; accepted for old scripts")
g("Page, export and browser", "notoolbar", "", "hide the PNG / SVG / PDF toolbar shown above every chart")
g("Page, export and browser", "download", "", "accepted for old scripts; does nothing (the toolbar is on by default)")
g("Page, export and browser", "browser", "path", "Edge/Chrome executable for {cmd:saveas()} when auto-detection fails (or set {cmd:global SPARKTA_BROWSER})")
g("Page, export and browser", "findbrowser", "", "diagnostic: list the browsers {cmd:saveas()} would use, then exit (no chart)")
g("Page, export and browser", "closebrowser", "", "quit the session's headless browser now (it also quits after 2 minutes idle)")

listed = {n for grp in G.values() for n, a, d in grp}
missing = [n for n in names if n not in listed]
assert not missing, "options missing from the table: %s" % missing

def synopt(name, arg, desc):
    # {cmd:}/{it:} only: {opt} with nested parentheses or quotes is not reliably parsed by the Viewer
    if arg: return "{synopt:{cmd:%s(}{it:%s}{cmd:)}}%s{p_end}" % (name, arg, desc)
    return "{synopt:{cmd:%s}}%s{p_end}" % (name, desc)

order = ["Essential", "Post-estimation input", "Coefficient selection and labels", "Post-estimation look",
         "Publication table", "Panels and grouping", "Axes", "Titles and text", "Legend", "Colours and theme",
         "Bars", "Lines and points", "Scatter and bubble", "Distributions", "Pie and donut",
         "Reference lines and annotations", "Tooltip and animation", "Page, export and browser"]
assert set(order) == set(G), set(G) ^ set(order)
table = ["{synoptset 42 tabbed}{...}", "{synopthdr}", "{synoptline}"]
for gi, grp in enumerate(order):
    if gi: table.append("")
    table.append("{syntab:%s}" % grp)
    for n, a, d in G[grp]: table.append(synopt(n, a, d))
table.append("{synoptline}")
table.append("{p 4 4 2}All %d options are listed above; the sections that follow describe them by group.{p_end}" % len(names))
TABLE = "\n".join(table)

# ----------------------------------------------------------------------------
# 2. narrative sections retained from the old help (by marker), patched
# ----------------------------------------------------------------------------
old = open(OLD).read().split("\n")
def block(start_pat, end_pat):
    s = next(i for i, l in enumerate(old) if l.startswith(start_pat))
    e = next(i for i, l in enumerate(old) if i > s and l.startswith(end_pat))
    return "\n".join(old[s:e]).rstrip() + "\n"

def patch(text, pairs):
    for a, b in pairs:
        assert a in text, "patch target not found: " + a[:60]
        text = text.replace(a, b)
    return text

grouping = block("{marker grouping}", "{marker stat}")
grouping = patch(grouping, [
 ("{opt filters(varname [, showmissing])} Adds an interactive dropdown below the\nchart letting viewers filter the data by values of {it:varname} without\nreloading.",
  "{opt filters(varlist [, showmissing])} Adds one interactive dropdown per variable\nbelow the chart, letting viewers filter the data without re-running Stata."),
 ("{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) filter(foreign)} -- viewer can toggle between Domestic and Foreign without re-running Stata.{p_end}\n\n{phang}\n{opt filters(varname [, showmissing])} Second independent interactive filter\ndropdown. Requires {cmd:filter()} to also be specified. Supports {cmd:showmissing}.\n\n{p 8 8 2}{it:Example:} {cmd:sparkta price weight, over(rep78) filter(foreign) filter2(rep78)}{p_end}",
  "{p 8 8 2}Each variable may have up to 500 distinct values. The dropdowns act together (an\nobservation must pass every filter), the statistics panel and any fit line or CI are\nrecomputed on the fly, and {cmd:by()} panels are all refreshed. {cmd:noallfilter} removes\nthe {it:All} entry.{p_end}\n\n{p 8 8 2}{it:Examples:}{p_end}\n{p 12 12 2}{cmd:sparkta price, over(rep78) filters(foreign)} -- toggle Domestic / Foreign{p_end}\n{p 12 12 2}{cmd:sparkta price weight, over(rep78) filters(foreign headroom)} -- two independent dropdowns{p_end}\n\n{phang}\n{opt sliders(varlist)} Dual-handle range sliders for numeric variables. The viewer\ndrags the handles to restrict the plotted range; everything on the page updates.\n\n{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) fit(lfit) fitci sliders(weight)}{p_end}"),
 ("{cmd:vertical} (default, stacked) | {cmd:horizontal} (side by side) |\n{cmd:grid} (2-column grid). Only applies when {cmd:by()} is used.",
  "{cmd:vertical} (default, stacked) | {cmd:horizontal} (side by side) |\n{cmd:grid} (2-column grid). Only applies when {cmd:by()} is used.\n{bf:Exports} ({cmd:saveas()} PNG/PDF and the toolbar) lay the panels two across\nwhatever is on screen, because a stacked page makes a tall narrow figure; write\n{cmd:layout(vertical)} explicitly to keep the stack in exports too."),
 ("{phang}\n{opt nostats} Suppresses the summary statistics panel below the chart.",
  "{phang}\n{opt yfree} Give every {cmd:by()} panel its own value axis. By default all panels\nshare one axis so heights are comparable.\n\n{phang}\n{opt nostats} Suppresses the summary statistics panel below the chart.\n{cmd:collapsestats} keeps it but starts it collapsed (the default on post-estimation pages)."),
])

stat = block("{marker stat}", "{marker scatter_opts}")
scatter = block("{marker scatter_opts}", "{marker ci_opts}")
ci = block("{marker ci_opts}", "{marker dist_opts}")
ci = patch(ci, [("The CI formula is mean +/- t* x (SD/sqrt(N)) where t* uses the\nt-distribution with N-1 degrees of freedom.{p_end}\n\n{p 8 8 2}\nConfidence intervals are computed as mean +/- t * SE where SE = SD / sqrt(n)\nand t is the two-tailed t-critical value with n-1 degrees of freedom.\nGroups with fewer than 2 observations are omitted.\nFor df > 120 the standard normal z-critical value is used.{p_end}",
 "Confidence intervals are mean +/- t * SE, SE = SD / sqrt(n), with t the exact\ntwo-tailed critical value for n-1 degrees of freedom ({cmd:invttail}) at any\nlevel; the same is used again when a filter or slider changes. Groups with fewer\nthan 2 observations are omitted. On post-estimation charts {cmd:cilevel()} sets the\nlevel of the drawn interval and {cmd:levels(# #)} draws two nested ones.{p_end}")])
hist = block("{marker dist_opts}", "{marker pubtable}")
hist = patch(hist, [("{opt bins(#)} Number of bins. Must be an integer of 2 or greater. If omitted,\nbins are determined by Sturges' rule: {cmd:ceil(log2(n) + 1)}, clamped to [5, 50].\nStart with the default; fewer bins reveal broad shape, more reveal fine structure.",
 "{opt bins(#)} Number of bins. Must be an integer of 2 or greater. If omitted,\nStata's own {cmd:histogram} rule is used: k = min(sqrt(N), 10*ln(N)/ln(10)),\nrounded, so the default matches {cmd:histogram varname} in Stata. Fewer bins reveal\nbroad shape, more reveal fine structure.")])
pub = block("{marker pubtable}", "{dlgtab:Box plots and violin charts}")
pub = patch(pub, [("An Export toolbar offers {bf:Copy} (Markdown by default; also LaTeX and plain\nTSV) and {bf:Download} (tidy-long CSV with columns {cmd:model, term, b, se, ll,\nul, p}; a {cmd:booktabs} + {cmd:threeparttable} LaTeX {cmd:.tex} that compiles on\nits own; and a table-only PDF).",
 "An Export toolbar offers {bf:Copy} (Markdown by default; also LaTeX and plain\nTSV) and {bf:Download} (a tidy-long CSV with the columns\n{cmd:model term b se ll ul p}; a {cmd:booktabs} + {cmd:threeparttable} LaTeX\n{cmd:.tex} file that compiles on its own; and a table-only PDF).")])
pub = patch(pub, [("{phang}\n{opt notable} Suppress the publication table entirely (the chart still renders).",
 "{phang}\n{opt notable} Suppress the publication table entirely (the chart still renders).\n\n{phang}\n{opt indicators(entries)} Indicator rows such as fixed effects, one quoted entry per\nrow: {cmd:indicators(\"Firm FE/Yes Yes No\" \"Year FE/No Yes Yes\")} -- the text before\nthe slash is the row label, the values after it are one per model, in\n{cmd:estnames()} order.\n\n{phang}\n{opt addstats(entries)} Extra footer rows. Keywords are filled from the stored\nresults: {cmd:rsq arsq fstat fpval ll chi2 rmse dep_mean}; quoted entries follow the\n{cmd:indicators()} form: {cmd:addstats(rsq dep_mean \"Sample/Full Full Restricted\")}.")])
box = block("{dlgtab:Box plots and violin charts}", "{marker annotations}")
box = patch(box, [
 ("If omitted, Silverman's rule of thumb is applied automatically:\n{it:h} = 0.9 * min(SD, IQR/1.34) * n^(-1/5).{p_end}",
  "If omitted, Stata's {cmd:kdensity} default is applied:\n{it:h} = 0.9 * min(SD, IQR/1.349) * n^(-1/5), with the IQR from Stata's default\npercentile rule, so the shape matches {cmd:kdensity varname}.{p_end}"),
 ("{bf:Legend.} Both chart types display an inline canvas legend in the top-right\ncorner of the chart area. For {cmd:boxplot}: Median, Mean, IQR Box, Whiskers,\nand Outlier symbols. For {cmd:violin}: Median, Mean, IQR Box, Whiskers, and\nKDE Shape symbols.{p_end}",
  "{bf:Key.} Both chart types carry an HTML key above the plot (never drawn over\nthe data, so it reflows at any zoom): Median, Mean, one IQR box entry, Whiskers\nand Outliers for {cmd:boxplot}; the density shapes, one IQR box entry and the\nmedian/mean markers for {cmd:violin}.{p_end}"),
 ("and on filter change (shapes tween smoothly to the new distribution).",
  "and on filter change (shapes tween smoothly to the new distribution; every violin,\nfor every plotted variable and group, is recomputed from the filtered data)."),
])
annot = block("{marker annotations}", "{dlgtab:Offline mode}")
annot = patch(annot, [
 ("Format: space-separated y x pairs, following Stata scattteri convention.", "Format: space-separated y x pairs, following Stata's {cmd:scatteri} convention."),
 ("and (y=20000, x=30). Note: y comes before x, matching Stata {cmd:scattteri} convention.", "and (y=20000, x=30). Note: y comes before x, matching Stata's {cmd:scatteri} convention."),
 ("{p 8 8 2}\n{bf:Requires.} The annotation plugin ({cmd:chartjs-plugin-annotation@3.0.1})\nis loaded from CDN automatically when any annotation option is specified.\nFor offline use, the plugin is bundled in {cmd:sparkta.jar}.",
  "{p 8 8 2}\n{bf:Requires.} The annotation plugin ({cmd:chartjs-plugin-annotation 3.0.1}) is\nembedded in the page like every other library; with {cmd:online} it is loaded from\nthe CDN instead."),
])
axes = block("{marker axes}", "{dlgtab:Chart behaviour}")
behav = block("{dlgtab:Chart behaviour}", "{dlgtab:Bar appearance}")
bars = block("{dlgtab:Bar appearance}", "{dlgtab:Points and lines}")
points = block("{dlgtab:Points and lines}", "{dlgtab:Axis utilities}")
points = patch(points, [("{p2colset 12 36 38 2}\n{p2col:{it:bar}/{it:hbar}", "{p2colset 12 40 40 2}\n{p2col:{it:bar}/{it:hbar}")])
axisutil = block("{dlgtab:Axis utilities}", "{dlgtab:Layout and animation}")
layout = block("{dlgtab:Layout and animation}", "{dlgtab:Tooltip}")
layout = patch(layout, [("{phang}\n{opt padding(#)} Inner padding of the chart area in pixels.{p_end}",
 "{phang}\n{opt padding(#)} Inner padding of the chart area in pixels ({cmd:padding(t r b l)} for four sides).{p_end}\n\n{phang}\n{opt plotmargin(spec)} Cushion between the data and the axes as a percent of the data\nrange, like Stata's {cmd:plotregion(margin())}: {cmd:plotmargin(5)} all sides,\n{cmd:plotmargin(x y)}, or {cmd:plotmargin(l r b t)}. Post-estimation charts accept\nnegative values to crop long CI tails.{p_end}")])
tooltip = block("{dlgtab:Tooltip}", "{dlgtab:Legend}")
tooltip = patch(tooltip, [("{opt tooltipmode(string)} Tooltip interaction mode. Options: {cmd:index}\n(default) | {cmd:nearest} | {cmd:dataset} | {cmd:point} | {cmd:x} | {cmd:y}.",
 "{opt tooltipmode(string)} Tooltip interaction mode. Options: {cmd:index}\n(default) | {cmd:nearest} | {cmd:point} | {cmd:x} | {cmd:y}. Post-estimation charts\nalways use {cmd:nearest} so the tooltip lands on the marker under the cursor and\nhides when the cursor leaves it.")])
legend = block("{dlgtab:Legend}", "{marker appearance}")
fonts = block("{dlgtab:Font styling}", "{dlgtab:Export toolbar and data handling}")
toolbar = block("{dlgtab:Export toolbar and data handling}", "{marker export}")
# fix9l: toolbar paragraph -- figure exports, one toolbar per by() page
toolbar = patch(toolbar, [('{opt notoolbar} Hide the export toolbar. Every chart card shows a small toolbar\nabove the plot by default (since v3.6.0): {bf:PNG} saves the chart as\na bitmap, {bf:SVG} as a scalable vector (opens in Illustrator, Inkscape, Word),\n{bf:PDF} prints the whole page with vector charts and text tables. {cmd:download}\nis still accepted for old scripts and does nothing. {cmd:saveas()} always keeps the\npage exporter, so {cmd:notoolbar} cannot be combined with it.{p_end}', "{opt notoolbar} Hide the export toolbar. Every chart shows a small toolbar above\nthe plot by default (since v3.6.0): {bf:PNG} saves the figure as a bitmap (title,\ncontext line, chart and key, at twice the screen resolution), {bf:SVG} the same\nfigure as a scalable vector (opens in Illustrator, Inkscape, Word), {bf:PDF} the same\nfigure as a vector PDF through the browser's print dialog. When a {cmd:filters()} dropdown or\n{cmd:sliders()} range is active at export time, every export carries a line\n{it:Filters: variable = value; ...; N = rows} under the context line. A {cmd:by()} page has ONE\ntoolbar above the panel grid; all three buttons export every panel, laid two across\nwith the shared key, exactly as {cmd:saveas()} does. For the whole page (statistics\npanel, tables) use the browser's own Print command; the publication table under a\npost-estimation chart has its own PDF button. {cmd:download} is still accepted for\nold scripts and does nothing. {cmd:saveas()} always keeps the page exporter, so\n{cmd:notoolbar} cannot be combined with it.{p_end}"),
  ("{opt nomissing} From v1.8.8, missing values in {cmd:over()}, {cmd:by()},\n{cmd:filter()}, {cmd:filter2()}, and {it:varlist} are automatically excluded",
 "{opt nomissing} Missing values in {cmd:over()}, {cmd:by()}, {cmd:filters()} and\n{it:varlist} are automatically excluded")])
stats_panel = block("{marker stats_panel}", "{marker offline}")
stats_panel = patch(stats_panel, [("Suppress with {cmd:nostats}. For large datasets, sparklines are rendered\nlazily on scroll, keeping file sizes small.",
 "Suppress with {cmd:nostats}; start it collapsed with {cmd:collapsestats} (post-estimation\npages do so by default). For large datasets, sparklines are rendered lazily on scroll.")])
methods = block("{marker methods}", "{marker authors}")
methods = patch(methods, [
 ("t-critical values: exact 8dp table for df 1-30; linear interpolation\ndf 31-120; Cornish-Fisher expansion df > 120 (max error < 1e-7).{p_end}\n\n{pmore}\nCornish, E.A. and Fisher, R.A. (1938).\n{it:Revue de l{c 39}Institut International de Statistique},\n5, 307-320.{p_end}",
  "The t-critical value is computed exactly (regularised incomplete beta, the same\nalgorithm in Java and in the page's JavaScript) for any level and df, matching\nStata's {cmd:invttail(df, (1-level/100)/2)} to about 1e-7.{p_end}"),
 ("{bf:Histogram binning.}\nSturges' rule: k = ceil(log2(n) + 1), clamped to [5, 50].\nOverride with {cmd:bins(k)}.{p_end}\n\n{pmore}\nSturges, H.A. (1926). {it:Journal of the American Statistical Association},\n21(153), 65-66.{p_end}",
  "{bf:Histogram binning.}\nStata's {cmd:histogram} default: k = min(sqrt(N), 10*ln(N)/ln(10)), rounded.\nOverride with {cmd:bins(k)}.{p_end}"),
 ("{bf:Violin kernel density.}\nDelegated to the chartjs-chart-boxplot plugin. Gaussian kernel with\nScott's rule: h = 1.06 x sigma x n^(-1/5). {cmd:bandwidth()} passes a\nmultiplier applied to the plugin's default.{p_end}\n\n{pmore}\nScott, D.W. (1992). {it:Multivariate Density Estimation}. Wiley, New York.{p_end}",
  "{bf:Violin kernel density.}\nComputed by sparkta (Java on first render, JavaScript after a filter change) with a\nGaussian kernel and Stata's {cmd:kdensity} default bandwidth\nh = 0.9 * min(SD, IQR/1.349) * n^(-1/5). {cmd:bandwidth(h)} sets h directly.{p_end}\n\n{pmore}\nSilverman, B.W. (1986). {it:Density Estimation for Statistics and Data Analysis}.\nChapman and Hall, London.{p_end}"),
 ("{bf:Fit lines ({cmd:fit()}).}", "{bf:Post-estimation charts.}\ncoefplot, marginsplot and eventstudy draw the numbers Stata stored: e(b)/e(V) or\nr(table) after estimation and margins, or the matrix/dataset you name. Intervals\nfrom {cmd:se()} use z, or t when {cmd:df()} is given. Nothing is re-estimated; the\nnumbers on the page and in the table are Stata's, and {cmd:eform}/{cmd:rescale()} are\napplied to estimate and bounds alike (t/z statistics stay on the original scale).{p_end}\n\n{p 4 4 2}\n{bf:Fit lines ({cmd:fit()}).}"),
])
authors = block("{marker authors}", "{marker also_see}")
memory = block("{marker memory}", "{marker methods}")
memory = patch(memory, [("{p2col:Stata 15{hline 1}16}384 MB  {cmd:-->}  {cmd:set java_heapmax 1024m}{p_end}\n", "")])
# rev 2 (2026-09-14, fix9i docs): the fix9g large-data mode, documented where users look for it.
LARGE = """default settings; up to ~500K is achievable with the default Stata 19+ heap.

{p 4 4 2}
{bf:Large data (scatter and bubble).} A scatter or bubble chart with more than 20,000 points
(counted per chart: {cmd:over()} groups are summed, each {cmd:by()} panel counts on its own)
switches to large-data mode automatically. The point cloud is drawn once onto an offscreen
bitmap and reused on every redraw, and the load animation is off; Stata prints one note,
"large-data mode: N points drawn once, no animation (hover and export unchanged)". Nothing
else changes: hover tooltips, {cmd:mlabel()}, {cmd:filters()}, {cmd:sliders()}, legend
clicks, {cmd:fit()} lines and CI bands all work as on a small chart, and there is no option
to set. {cmd:saveas()} exports of such a page embed the cloud as one image (the axes, text,
fit line and band stay vector), so a PNG, PDF or SVG of 100,000 points is written in a few
seconds and the SVG is about 0.5-1.5 MB instead of 50 MB. {cmd:fit()} on a large chart is
computed by Stata on every point and drawn from a thinned sample of the fitted values (at
most 4,000 points, the last x always kept), which is invisible at screen resolution.{p_end}
"""
memory = patch(memory, [("default settings; up to ~500K is achievable with the default Stata 19+ heap.\n", LARGE)])

# ----------------------------------------------------------------------------
# 3. new text
# ----------------------------------------------------------------------------
HEAD = r"""{smcl}
{* sparkta.sthlp  v3.6.0  2026-09-14  (rebuilt by verify/build_sthlp.py -- docs item 5, rev 2: Large data)}{...}
{hline}
help for {cmd:sparkta}
{hline}

{p 4 4 2}
{bf:sparkta} {hline 2} Interactive self-contained HTML charts, post-estimation plots and publication tables from Stata{break}
{browse "https://github.com/fahad-mirza/sparkta_stata":(Online documentation, gallery and examples)}

{marker syntax}{...}
{title:Syntax}

{p 4 4 2}Data charts (bar, line, scatter, distributions, pie, CI charts):{p_end}

{p 8 16 2}
{cmd:sparkta} [{varlist}] {ifin} [{cmd:,} {opt type(charttype)} {it:options}]{p_end}

{p 4 4 2}Post-estimation charts, after any estimation command, {cmd:margins}, or from a matrix or dataset of estimates:{p_end}

{p 8 16 2}
{cmd:sparkta} {cmd:,} {cmd:type(coefplot|marginsplot|eventstudy)} [{opt matrix(M)} | {opt results(source)} | {opt estnames(names)}] [{it:options}]{p_end}

{p 4 4 2}Utilities:{p_end}

{p2colset 8 34 34 2}
{p2col:{cmd:sparkta, findbrowser}}list the browsers {cmd:saveas()} would use{p_end}
{p2col:{cmd:sparkta, closebrowser}}quit the session's headless browser{p_end}
{p2colreset}

{p 4 4 2}
{it:options} are summarised below and listed in full under {helpb sparkta##options:Options}.

{p2colset 8 44 44 2}
{p2col:{it:Option}}{it:Description}{p_end}
{p2line}
{p2col:{helpb sparkta##types:type(charttype)}}chart type (default {cmd:bar}); 24 types{p_end}
{p2col:{helpb sparkta##grouping:over(varname)}}group the chart by a categorical variable{p_end}
{p2col:{helpb sparkta##grouping:by(varname)}}one panel per group{p_end}
{p2col:{helpb sparkta##grouping:filters(varlist)}}live filter dropdowns{p_end}
{p2col:{helpb sparkta##grouping:sliders(varlist)}}dual-handle range sliders{p_end}
{p2col:{helpb sparkta##stat:stat(string)}}statistic to plot (default {cmd:mean}){p_end}
{p2col:{helpb sparkta##export:export(filename)}}write the HTML page to a file{p_end}
{p2col:{helpb sparkta##saveas:saveas(files)}}also write PNG, PDF and/or SVG{p_end}
{p2col:{helpb sparkta##postest:matrix()} {helpb sparkta##results:results()} {helpb sparkta##postest:estnames()}}inputs of the post-estimation charts{p_end}
{p2col:{helpb sparkta##postest:cistyle()} {it:...}}CI style, nested levels, eform, sorting, labels{p_end}
{p2col:{helpb sparkta##pubtable:stars()} {it:...}}publication table beneath post-estimation charts{p_end}
{p2col:{helpb sparkta##axes:xtitle()} {it:...}}axis titles, ranges, scales, ticks{p_end}
{p2col:{helpb sparkta##appearance:theme()} {it:...}}themes, colours, fonts{p_end}
{p2col:{helpb sparkta##annotations:yline()} {it:...}}reference lines, bands, points, ellipses{p_end}
{p2col:{helpb sparkta##scatter_opts:fit()}}scatter fit line: lfit qfit lowess exp log power ma{p_end}
{p2col:{helpb sparkta##dist_opts:bins()} {it:...}}histogram, box plot and violin options{p_end}
{p2colreset}

{marker description}{...}
{title:Description}

{p 4 4 2}
{cmd:sparkta} turns Stata data, or the results of an estimation command, into a
self-contained interactive {cmd:.html} page with one command. The page opens in any
browser, needs no server, no internet connection and no software on the recipient's
side; e-mail it and the interactivity travels with it.{p_end}

{p 4 4 2}
{bf:Data charts} plot variables in memory: bar, line, area, scatter, bubble, pie,
histogram, box, violin and CI charts, with live filter dropdowns, range sliders,
{cmd:by()} panel grids and a collapsible statistics panel that updates as the viewer
filters.{p_end}

{p 4 4 2}
{bf:Post-estimation charts} plot what Stata stored: {cmd:type(coefplot)} draws
coefficients with confidence intervals (one model or several), {cmd:type(marginsplot)}
draws the results of {cmd:margins}, and {cmd:type(eventstudy)} draws leads and lags
around a reference period. Each comes with a publication table beneath the chart
(Markdown, LaTeX, CSV, PDF export) built from the same numbers. Inputs can be the last
estimation ({cmd:e(b)}/{cmd:e(V)}, {cmd:r(table)}), stored estimates, any matrix
({cmd:matrix()}) or a dataset/frame of estimates ({cmd:results()}).{p_end}

{p 4 4 2}
Every chart card has a toolbar to save it as PNG, SVG or PDF from the browser, and
{cmd:saveas()} writes those files directly from Stata.{p_end}

{p 4 4 2}
{bf:Requirements:} Stata 17 or later (the Java backend is compiled for the Java 11
runtime Stata bundles). All files, including {cmd:sparkta.jar}, are installed by
{cmd:net install} / {cmd:ssc install}; nothing else is needed. {cmd:saveas()} needs
Microsoft Edge or Google Chrome on the machine.{p_end}

{marker examples_quick}{...}
{title:Quick start}

{p 8 8 2}
{stata "sysuse auto, clear":sysuse auto, clear}

{p2colset 8 66 66 2}
{p2col:{stata "sparkta price, over(rep78)":sparkta price, over(rep78)}}mean price by repair record{p_end}
{p2col:{stata "sparkta price mpg, type(scatter) fit(lfit) fitci":sparkta price mpg, type(scatter) fit(lfit) fitci}}scatter with a fit line{p_end}
{p2col:{stata "sparkta price, type(cibar) over(rep78) filters(foreign)":sparkta price, type(cibar) over(rep78) filters(foreign)}}CI bars with a live filter{p_end}
{p2col:{stata "sparkta price, type(violin) over(rep78) theme(dark_neon)":sparkta price, type(violin) over(rep78) theme(dark_neon)}}violins, dark theme{p_end}
{p2col:{stata "regress price mpg weight foreign":regress price mpg weight foreign}}{p_end}
{p2col:{stata "sparkta, type(coefplot) levels(90 95)":sparkta, type(coefplot) levels(90 95)}}coefficient plot with nested CIs and a table{p_end}
{p2col:{stata "margins rep78":margins rep78}}{p_end}
{p2col:{stata "sparkta, type(marginsplot)":sparkta, type(marginsplot)}}the margins as a chart{p_end}
{p2colreset}

{marker also_see_quick}{...}
{title:Contents}

{p2colset 8 60 60 2}
{p2col:{helpb sparkta##types:Chart types}}every type, what it needs and what it draws{p_end}
{p2col:{helpb sparkta##varlist:Varlist}}what goes before the comma, by type{p_end}
{p2col:{helpb sparkta##options:Options}}the complete option table, grouped{p_end}
{p2col:{helpb sparkta##postest:Post-estimation charts}}coefplot, marginsplot, eventstudy, matrix(), results(){p_end}
{p2col:{helpb sparkta##pubtable:Publication table}}stars, t/z, intervals, indicator rows, export{p_end}
{p2col:{helpb sparkta##themes:Themes}} and {helpb sparkta##colours:colour grammar}{p_end}
{p2col:{helpb sparkta##saveas:saveas() and export}}PNG / PDF / SVG from Stata{p_end}
{p2col:{helpb sparkta##offline:Self-contained pages and online}}what is embedded, when to use {cmd:online}{p_end}
{p2col:{helpb sparkta##examples:Examples}}runnable examples by category{p_end}
{p2col:{helpb sparkta##stats_panel:Statistics panel}}what the panel shows{p_end}
{p2col:{helpb sparkta##methods:Statistical methods}}formulas and references{p_end}
{p2col:{helpb sparkta##mistakes:Common mistakes}} and {helpb sparkta##limits:known limitations}{p_end}
{p2col:{helpb sparkta##memory:Memory and large datasets}}{cmd:java_heapmax}; large-data mode for scatter and bubble{p_end}
{p2colreset}

{hline}

{marker types}{...}
{title:Chart types}

{p 4 4 2}
{cmd:type(}{it:charttype}{cmd:)} selects the chart. Default {cmd:bar}. The first
column is what you write; {bf:needs} says what the type requires.

{p 4 4 2}{bf:Data charts} -- plot the variables in memory (before the comma).{p_end}

{p2colset 6 22 22 2}
{p2col:{it:type}}{it:needs} -- {it:draws}{p_end}
{p2line}
{p2col:{cmd:bar}}1+ numeric vars -- vertical bars of {cmd:stat()} per variable or {cmd:over()} group{p_end}
{p2col:{cmd:hbar}}1+ numeric vars -- horizontal bars{p_end}
{p2col:{cmd:stackedbar}}2+ vars or {cmd:over()} -- stacked vertical bars{p_end}
{p2col:{cmd:stackedhbar}}2+ vars or {cmd:over()} -- stacked horizontal bars{p_end}
{p2col:{cmd:stackedbar100}}1 var + {cmd:over()} -- 100 percent stacked bars (without {cmd:over()} it draws plain bars, with a note){p_end}
{p2col:{cmd:stackedhbar100}}1 var + {cmd:over()} -- 100 percent stacked horizontal bars{p_end}
{p2col:{cmd:line}}1+ numeric vars -- lines, one per variable or group{p_end}
{p2col:{cmd:area}}1+ numeric vars -- filled lines{p_end}
{p2col:{cmd:stackedline}}2+ vars -- stacked lines{p_end}
{p2col:{cmd:stackedarea}}2+ vars -- stacked areas{p_end}
{p2col:{cmd:scatter}}y x -- points; {cmd:fit()} adds a fitted line, {cmd:mlabel()} labels{p_end}
{p2col:{cmd:bubble}}y x size -- points sized by a third variable{p_end}
{p2col:{cmd:pie}}see {helpb sparkta##varlist:Varlist} -- slices; {cmd:stat(pct)} default{p_end}
{p2col:{cmd:donut}}as pie -- ring; {cmd:cutout()} sets the hole{p_end}
{p2col:{cmd:histogram}}1 numeric var -- bins, Stata's default count; {cmd:by()} for panels ({cmd:over()} not allowed){p_end}
{p2col:{cmd:boxplot}}1+ numeric vars -- box plots, Stata percentiles, Tukey whiskers{p_end}
{p2col:{cmd:hbox}}1+ numeric vars -- horizontal box plots{p_end}
{p2col:{cmd:violin}}1+ numeric vars -- kernel densities with the inner box{p_end}
{p2col:{cmd:hviolin}}1+ numeric vars -- horizontal violins{p_end}
{p2col:{cmd:cibar}}1 var + {cmd:over()} -- bars of the mean with {cmd:cilevel()} error bars{p_end}
{p2col:{cmd:ciline}}1 var + {cmd:over()} -- line of the mean with a CI band{p_end}
{p2colreset}

{p 4 4 2}{bf:Post-estimation charts} -- no varlist; they read stored results, a matrix or a dataset.{p_end}

{p2colset 6 22 22 2}
{p2col:{it:type}}{it:input} -- {it:draws}{p_end}
{p2line}
{p2col:{cmd:coefplot}}last estimation, {cmd:estnames()}, {cmd:matrix()} or {cmd:results()} -- point estimates with CI whiskers/bands/bars, coefficients down the y-axis ({cmd:vertical} flips), a reference line at 0 (1 with {cmd:eform}), several models side by side{p_end}
{p2col:{cmd:marginsplot}}last {cmd:margins} (r(table), r(at)) or {cmd:results()} -- predicted margins over factor levels or {cmd:at()} values, one series per interaction level ({cmd:over()}), contrasts and pairwise comparisons, nested CIs, area/whisker/bar CIs{p_end}
{p2col:{cmd:eventstudy}}last estimation, {cmd:matrix()} or {cmd:results()} -- coefficients on a numeric event-time axis with a hollow marker at the reference period; leads and lags coloured apart; DiD estimators ({cmd:csdid}, {cmd:jwdid}, {cmd:lwdid}) via {cmd:matrix(r(table))} or {cmd:results()}{p_end}
{p2colreset}

{p 4 4 2}
The three share the publication table, {cmd:cistyle()}, {cmd:levels()}, {cmd:cilevel()},
{cmd:eform}, {cmd:rescale()}, the coefficient selection and label options, and the
Stata/Jann aliases ({cmd:level() keep() drop() sort() recastci() msymbol() ...}).
{cmd:by()}, {cmd:filters()} and {cmd:sliders()} do not apply to them (they draw
estimation results, not the data in memory) and are ignored with a note.
Full detail: {helpb sparkta##postest:Post-estimation charts}.{p_end}

{marker varlist}{...}
{title:Varlist}

{p 4 4 2}
The variables before the comma are what gets measured. Post-estimation types take none.

{p2colset 8 44 44 2}
{p2col:{bf:bar, hbar, stacked*}}one or more numeric variables; {cmd:stat()} is applied per variable and per {cmd:over()} group{p_end}
{p2col:{bf:line, area, stacked*}}one or more numeric variables plotted as series{p_end}
{p2col:{bf:scatter}}{bf:y first, then x}: {cmd:sparkta price mpg, type(scatter)}{p_end}
{p2col:{bf:bubble}}{bf:y, x, size}: {cmd:sparkta price mpg weight, type(bubble)}{p_end}
{p2col:{bf:histogram}}exactly one numeric variable{p_end}
{p2col:{bf:boxplot, hbox, violin, hviolin}}one or more numeric variables; {cmd:over()} for groups{p_end}
{p2col:{bf:cibar, ciline}}one numeric variable; {cmd:over()} required{p_end}
{p2col:{bf:pie, donut}}three forms: {cmd:sparkta price mpg, type(pie)} (one slice per variable total); {cmd:sparkta price, type(pie) over(rep78)} (group sums or shares); {cmd:sparkta, type(pie) over(rep78)} (frequency counts){p_end}
{p2col:{bf:coefplot, marginsplot, eventstudy}}no varlist -- a varlist is an error{p_end}
{p2colreset}

{p 4 4 2}
{bf:over() vs by() vs filters():}
{cmd:over()} puts all groups on {it:one chart}.
{cmd:by()} creates {it:separate panels}.
{cmd:filters()} lets the {it:viewer} switch groups without re-running Stata.
They combine: {cmd:by()} makes panels, {cmd:over()} groups series inside each,
{cmd:filters()}/{cmd:sliders()} refresh every panel.
"""

THEMES = block("{marker themes}", "{marker options}")
THEMES = patch(THEMES, [("{p 4 4 2}\n{cmd:colors()} always overrides any palette.\nUse {bf:spaces} to separate colors: {cmd:colors(#e41a1c #377eb8 #4daf4a)}",
 "{p 4 4 2}\n{cmd:colors()} and {cmd:palette()} override the theme palette; see\n{helpb sparkta##colours:Colour grammar}.")])

OPTIONS_HEAD = r"""{marker options}{...}
{title:Options}

{p 4 4 2}
Every option, grouped. The groups link to the detailed sections that follow.
Options marked as aliases exist so that Stata and coefplot (Ben Jann) spellings work
unchanged; give either the alias or the sparkta name, not both.

"""

POSTEST = r"""{marker postest}{...}
{title:Post-estimation charts}

{dlgtab:Where the numbers come from}

{p 4 4 2}
A post-estimation chart never re-estimates anything. The page and its table show the
numbers Stata stored, read from one of four places:{p_end}

{p2colset 8 34 34 2}
{p2col:{bf:the last estimation}}{cmd:e(b)}, {cmd:e(V)}, {cmd:e(df_r)} (t intervals when present, z otherwise). Default when nothing else is given.{p_end}
{p2col:{bf:the last {cmd:margins}}}{cmd:r(table)}, {cmd:r(at)}, {cmd:r(table_vs)} for {cmd:marginsplot}. Default for that type.{p_end}
{p2col:{opt estnames(names)}}stored estimates ({cmd:estimates store m1}) -> a multi-model coefplot, one series per model.{p_end}
{p2col:{opt matrix(spec)}}any matrix: coefficients across columns, statistics in rows (or the transpose). See below.{p_end}
{p2col:{opt results(source)}}a {cmd:.dta} file or a frame with one row per coefficient / period / margin. See below.{p_end}
{p2colreset}

{p 4 4 2}
{bf:r() does not survive sparkta.} The command runs helpers that clear {cmd:r()}. For
{cmd:type(marginsplot)} it snapshots the margins results first and restores them
afterwards, so {cmd:sparkta, type(marginsplot)} can be repeated. For anything else,
if you need {cmd:r(table)} after the chart, store it first ({cmd:matrix T = r(table)}).{p_end}

{dlgtab:coefplot}

{p 4 4 2}
{cmd:sparkta, type(coefplot)} after any estimation command draws one marker per
coefficient with its confidence interval, coefficients down the y-axis in estimation
order and a reference line at 0. {cmd:vertical} puts the coefficients along x.
{cmd:_cons} is dropped by default (as {cmd:coefplot} does); {cmd:cons} keeps it. Base and
omitted factor levels are shown as such; {cmd:nobase} drops them.{p_end}

{p 4 4 2}{bf:Choosing and labelling coefficients}{p_end}
{p 8 8 2}
{cmd:show(mpg weight)} keeps only those; {cmd:omit(_cons 1.foreign)} drops those;
{cmd:order(foreign mpg weight)} sets the order; {cmd:coefsort(value|abs|pval|se|desc)}
sorts (Jann's {cmd:sort(b|p|se|abs|asc|desc)} also works). Labels:
{cmd:coeflabels("mpg/Miles per gallon" "weight/Weight, lbs")} or Jann's form
{cmd:coeflabels(mpg = "Miles per gallon")}; {cmd:coeflbl(mpg "MPG"|weight "Wt")} is the
pipe-separated spelling. {cmd:headings(1 "Vehicle"|3 "Origin")} inserts bold group rows
before coefficient 1 and 3, on the chart and in the table. Value labels of factor
variables are used automatically.{p_end}

{p 4 4 2}{bf:Scale}{p_end}
{p 8 8 2}
{cmd:eform} exponentiates estimates and bounds (odds ratios, hazard ratios, incidence
rate ratios) and moves the reference line to 1. {cmd:rescale(1000)} multiplies
estimates and bounds. The table's t/z statistics are unaffected by either.{p_end}

{p 4 4 2}{bf:Intervals}{p_end}
{p 8 8 2}
{cmd:cilevel(90)} (or {cmd:level(90)}) sets the level. {cmd:levels(90 95)} draws two
nested intervals, inner first. {cmd:cistyle(whisker)} (default), {cmd:band},
{cmd:bar} or {cmd:area}; Stata's {cmd:recastci(rcap|rarea|rband|rbar|rline)} maps onto
these. {cmd:noci} removes them. {cmd:cicolors(navy|maroon)} colours the CI per model,
{cmd:ciwidth(2)} thickens it. {cmd:refval(#)} or {cmd:refval(none)} moves or removes
the reference line; {cmd:pexline(#)} adds a second one.{p_end}

{p 4 4 2}{bf:Several models}{p_end}
{p 8 8 2}
{cmd:estimates store m1} ... then {cmd:sparkta, type(coefplot) estnames(m1 m2 m3)}
draws the models side by side (dodged markers, one colour each), {cmd:estlabels(OLS~IV~GMM)}
(or Jann's {cmd:plotlabels("OLS" "IV" "GMM")}) names them, {cmd:pointstyles(circle rect triangle)}
gives each a shape, and the publication table gets one column per model. Several
matrices ({cmd:matrix(A B C)}) or several {cmd:results()} sources do the same.{p_end}

{p 4 4 2}{bf:Markers}{p_end}
{p 8 8 2}
Estimates with p above 0.10 are drawn hollow (a hollow bar with {cmd:coefstyle(bar)});
the key above the chart says so. {cmd:connected} joins the estimates with a line.
{cmd:coefstyle(bar)} draws bars from the reference line instead of markers.{p_end}

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:regress price mpg weight foreign}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) levels(90 95) cistyle(band)}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) show(mpg weight) coeflabels(mpg = "Miles per gallon") coefsort(abs)}{p_end}
{p 12 12 2}{cmd:logit foreign mpg weight}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) eform title("Odds ratios")}{p_end}
{p 12 12 2}{cmd:estimates store m1} ... {cmd:sparkta, type(coefplot) estnames(m1 m2 m3) estlabels(Base~Controls~Full)}{p_end}

{dlgtab:marginsplot}

{p 4 4 2}
{cmd:sparkta, type(marginsplot)} after {cmd:margins} draws what Stata's
{cmd:marginsplot} would: one point (with CI) per margin, factor levels or {cmd:at()}
values along x, the variable name as the x title, the value labels as tick labels.
The axis hugs the data like Stata's (it is not forced to include zero).
Recognised layouts:{p_end}

{p2colset 8 54 54 2}
{p2col:{cmd:margins rep78}}one series over the factor levels{p_end}
{p2col:{cmd:margins, at(mpg=(10(5)40))}}one series over a numeric x{p_end}
{p2col:{cmd:margins foreign, at(mpg=(...))} + over()}one series per level of the interaction variable named in {cmd:over()}{p_end}
{p2col:{cmd:margins rep78, contrast} / {cmd:pwcompare}}contrasts or pairwise comparisons, labelled as Stata does; add {cmd:pexline(0)}{p_end}
{p2col:{cmd:margins, dydx(*)}}average marginal effects, one per variable{p_end}
{p2colreset}

{p 4 4 2}
{cmd:cistyle(area)} draws a ribbon, {cmd:cistyle(area whisker)} a ribbon plus whiskers,
{cmd:cistyle(bar)} error bars; Stata's {cmd:recastci(rarea)} / {cmd:recast(connected)}
work too. {cmd:connected} joins the points. {cmd:levels(90 95)} nests two intervals.
{cmd:cicolors()} colours the intervals per series. {cmd:plotmargin(5 5 -20 -20)} crops
long CI tails.{p_end}

{p 4 4 2}
{bf:From a saved margins dataset.} {cmd:margins ..., saving(m.dta)} then
{cmd:sparkta, type(marginsplot) results(m)} reproduces the chart without the model in
memory. Any tidy table works when you map its columns:
{cmd:results(m, b(margin) se(se) at(mpg) factors(foreign))} -- see {helpb sparkta##results:results()}.{p_end}

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:regress price i.rep78 mpg}{p_end}
{p 12 12 2}{cmd:margins rep78}{p_end}
{p 12 12 2}{cmd:sparkta, type(marginsplot) cistyle(area whisker) connected}{p_end}
{p 12 12 2}{cmd:regress price i.foreign##c.mpg}{p_end}
{p 12 12 2}{cmd:margins foreign, at(mpg=(15 20 25 30 35))}{p_end}
{p 12 12 2}{cmd:sparkta, type(marginsplot) over(foreign) cistyle(area) levels(90 95)}{p_end}

{dlgtab:eventstudy}

{p 4 4 2}
{cmd:sparkta, type(eventstudy)} draws coefficients against relative event time: leads
(before the event) in one colour, lags (after it) in another, a hollow marker with no
interval at the reference period, and a vertical line at 0. It needs coefficients whose
names carry the period. Recognised name patterns: {cmd:lead3 lag2}, {cmd:lead_3 lag_2},
{cmd:Tm3 Tp2}, {cmd:m3 p2}, {cmd:T-3 T+2 T}, {cmd:-3.rel_time 2.rel_time} (factor
notation, base level = reference), plain integers, and {cmd:Pre_avg}/{cmd:Post_avg}
summary rows (kept off the axis unless {cmd:show(*)}).{p_end}

{p2colset 8 40 40 2}
{p2col:{bf:from a regression}}{cmd:regress y lead5 ... lead2 lag0 ... lag5 i.t i.id} then {cmd:sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5)}. Use {cmd:show()} to leave the fixed effects out.{p_end}
{p2col:{bf:from csdid / jwdid}}after {cmd:estat event}: {cmd:sparkta, type(eventstudy) matrix(r(table))}{p_end}
{p2col:{bf:from lwdid}}{cmd:lwdid ..., save(ev)} then {cmd:sparkta, type(eventstudy) results(ev)}{p_end}
{p2col:{bf:from any matrix}}rows b / se / pvalue (or ll ul), column names = periods: {cmd:sparkta, type(eventstudy) matrix(E)}{p_end}
{p2colreset}

{p 4 4 2}
{cmd:refperiod(auto)} (default) finds the omitted period (the coefficient missing
from a consecutive run, or the base level); {cmd:refperiod(-1)} names it;
{cmd:refperiod(none)} draws no reference marker. {cmd:together} joins the post series
to the reference marker (the default from estimation results, where every lead and lag
is relative to the omitted period); {cmd:separate} keeps leads and lags apart (the
default with {cmd:matrix()}/{cmd:results()}, because DiD estimators' pre-period
placebos are not relative to -1). {cmd:cicolors(pre|post)} colours the two intervals;
{cmd:colors()} slots 1-2 colour the markers; {cmd:cistyle()}, {cmd:levels()},
{cmd:connected}, {cmd:coeflabels()} and the table options apply as for coefplot.{p_end}

{marker matrixmode}{...}
{dlgtab:matrix()}

{p 4 4 2}
{opt matrix(spec)} plots from a Stata matrix using coefplot's (Ben Jann) grammar,
plus autodetection by row/column name so the common cases are one-liners:{p_end}

{p2colset 8 40 40 2}
{p2col:{cmd:matrix(M)}}row 1 = estimates, column names = coefficients{p_end}
{p2col:{cmd:matrix(M[2])}}row 2 holds the estimates (statistics in rows){p_end}
{p2col:{cmd:matrix(M[,3])}}column 3 holds the estimates (statistics in columns){p_end}
{p2col:{cmd:matrix(r(table))}}after any estimation, {cmd:margins}, {cmd:estat event} ...: b, se, ll, ul, pvalue, df found by name{p_end}
{p2col:{cmd:matrix(A B C)}}several matrices = several models{p_end}
{p2col:{cmd:ci((2 3))} {cmd:ci(2 3)} {cmd:ci(ll ul)}}lower/upper by position or name{p_end}
{p2col:{cmd:ci(C)}}rows (or columns) 1-2 of another matrix, e.g. {cmd:ci(e(ci_normal))}{p_end}
{p2col:{cmd:se(2)} {cmd:se(se)} {cmd:se(S)}}standard errors by position, name or matrix -> CI at {cmd:cilevel()}{p_end}
{p2col:{cmd:df(30)} {cmd:df(df)}}degrees of freedom -> t intervals (z without it){p_end}
{p2col:{cmd:pvalue(4)} {cmd:pvalue(p)}}p-values (used for the stars and the hollow markers){p_end}
{p2colreset}

{p 4 4 2}
Autodetected names: {cmd:b coef estimate watt} | {cmd:se stderr} |
{cmd:ll lb lower low_ci min95} | {cmd:ul ub upper up_ci max95} | {cmd:pvalue p pval} |
{cmd:df}. Sources may be {cmd:r(name)}, {cmd:e(name)} or ordinary matrices. A matrix with
estimates only draws points without intervals.{p_end}

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:regress price mpg weight foreign}{p_end}
{p 12 12 2}{cmd:matrix T = r(table)}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) matrix(T)}{p_end}
{p 12 12 2}{cmd:matrix A = (0.5, 1.2, -0.3 \ 0.1, 0.2, 0.15)}{p_end}
{p 12 12 2}{cmd:matrix colnames A = x1 x2 x3}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) matrix(A) se(2) df(40)}{p_end}

{marker results}{...}
{dlgtab:results()}

{p 4 4 2}
{opt results(source [source ...] [, suboptions])} plots from a dataset of estimates,
one row per coefficient (coefplot), per event period (eventstudy) or per margin
(marginsplot). {it:source} is a {cmd:.dta} file (extension optional) or {cmd:frame:}{it:name}.
Several sources = several models. The command reads a copy; your data and frames are
untouched.{p_end}

{p 4 4 2}{bf:coefplot and eventstudy sources} (parmest, regsave, lwdid, esttab-style tables):{p_end}

{p2colset 8 26 26 2}
{p2col:{cmd:name(var)}}coefficient names (autodetect: parm term var varname variable name coef coefname label){p_end}
{p2col:{cmd:time(var)}}relative time for eventstudy (autodetect: ryear rel_time reltime event_time eventtime period time t rel){p_end}
{p2col:{cmd:b(var)}}estimates{p_end}
{p2col:{cmd:ci(lo hi)}}lower and upper bounds{p_end}
{p2col:{cmd:se(var)}}standard errors (-> CI at {cmd:cilevel()}){p_end}
{p2col:{cmd:pvalue(var)}}p-values{p_end}
{p2colreset}

{p 8 8 2}
Autodetection matches column names EXACTLY against the same keyword lists as
{cmd:matrix()}. Rows with a missing name or time are dropped.{p_end}

{p 4 4 2}{bf:marginsplot sources}: a {cmd:margins, saving()} file is read as is. Any
other table needs the column map:{p_end}

{p2colset 8 32 32 2}
{p2col:{cmd:b(var)}}the margin{p_end}
{p2col:{cmd:se(var)} / {cmd:ci(lo hi)}}its standard error or bounds{p_end}
{p2col:{cmd:pvalue(var)}}p-value (optional){p_end}
{p2col:{cmd:at(varlist)}}the {cmd:at()} variable(s) that form the x-axis{p_end}
{p2col:{cmd:factors(varlist)}}the factor variable(s) that split the series{p_end}
{p2colreset}

{p 8 8 2}
Sparse tables are completed to a full at-by-factor grid, and the source's value and
variable labels are used for the axis and the key, so a {cmd:results()} page looks the
same whatever is in memory.{p_end}

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:parmest, saving(pe.dta, replace)} ... {cmd:sparkta, type(coefplot) results(pe) omit(_cons)}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) results(frame:res, name(parm) b(estimate) ci(min95 max95))}{p_end}
{p 12 12 2}{cmd:margins rep78, saving(mg, replace)} ... {cmd:sparkta, type(marginsplot) results(mg)}{p_end}
{p 12 12 2}{cmd:collapse (mean) margin=price (semean) se=price, by(foreign mpg_bin)}{p_end}
{p 12 12 2}{cmd:sparkta, type(marginsplot) results(frame:tab, b(margin) se(se) at(mpg_bin) factors(foreign))}{p_end}

"""

COLOURS = r"""{marker colours}{...}
{dlgtab:Colour grammar}

{p 4 4 2}
Every colour option ({cmd:colors()}, {cmd:cicolors()}, {cmd:bgcolor()}, {cmd:ylinecolor()},
{cmd:mediancolor()} ...) accepts any of these, and the ado normalises them before the
browser sees them:{p_end}

{p2colset 8 42 42 2}
{p2col:{cmd:navy maroon gs10 cranberry ...}}Stata colour names ({help colorstyle}){p_end}
{p2col:{cmd:#e69f00} or {cmd:e69f00}}hex{p_end}
{p2col:{cmd:"230 159 0"}}an r g b triplet, as {cmd:colorpalette} returns them{p_end}
{p2col:{cmd:rgb(...)} {cmd:rgba(...)} {cmd:tomato}}CSS forms and names, passed through{p_end}
{p2col:{it:name}{cmd:%50}}50 percent opacity{p_end}
{p2col:{it:name}{cmd:*0.6}}intensity: below 1 lighter, above 1 darker{p_end}
{p2colreset}

{p 4 4 2}
{cmd:colors()} is space-separated (quote a triplet); the pipe-separated options
({cmd:cicolors()}, {cmd:ylinecolor()} ...) take one colour per entry. {opt palette(spec)}
hands the spec to Ben Jann's {cmd:colorpalette} ({cmd:ssc install palettes}, requires
{cmd:colrspace}): {cmd:palette(okabe)}, {cmd:palette(viridis, n(6) reverse)},
{cmd:palette(HCL pastel)}. {cmd:theme(cblind1)} switches to the Okabe-Ito palette without
any package.{p_end}

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price weight, over(foreign) colors(navy maroon%60)}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) palette(okabe)}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) estnames(m1 m2) colors("230 159 0" "0 114 178") cicolors(gs8|gs8)}{p_end}

{phang}
{opt bgcolor(color)} Page background. {opt plotcolor(color)} chart card background.
{opt gridcolor(color)} and {opt gridopacity(#)} (default 0.15) style the grid lines.{p_end}

{phang}
{opt datalabels} Print the value on each bar, point or slice; {opt pielabels} prints the
percentage share inside pie/donut slices. Font size and colour follow the theme.{p_end}

"""

BASIC = r"""{marker export}{...}
{dlgtab:Page, title and export}

{phang}
{opt type(charttype)} Chart type. Default {cmd:bar}. See {helpb sparkta##types:Chart types}.{p_end}

{phang}
{opt title(string)} Main heading. {opt subtitle(string)} replaces the automatic
one-line description under it; {opt note(string)} and {opt caption(string)} go below
the chart; {opt notimestamp} drops the {it:Made with sparkta ...} footer line.{p_end}

{phang}
{opt export(filename)} Write the page to {it:filename} (must end in {cmd:.html};
{cmd:~} expands) instead of opening the browser. Without it the page goes to a
temporary file and opens in the default browser.{p_end}

{phang}
{opt theme(string)} Background and/or palette; see {helpb sparkta##themes:Themes}.{p_end}

{phang}
{opt online} Load Chart.js and its plugins from a CDN when the page is opened instead
of embedding them. The file is about 250 KB smaller but needs an internet connection
to display. See {helpb sparkta##offline:Self-contained pages}. {cmd:offline} is the
default and is accepted for old scripts.{p_end}

{phang}
{opt notoolbar} Hide the toolbar above every chart card (shown by default): {bf:PNG}
saves the chart as a bitmap, {bf:SVG} as a vector file (opens in Illustrator, Inkscape,
Word), {bf:PDF} prints the page with vector charts and text tables. The toolbar is
never included in exports. {cmd:download} is accepted for old scripts and does nothing.
{cmd:notoolbar} cannot be combined with {cmd:saveas()}.{p_end}

{marker saveas}{...}
{dlgtab:saveas() -- PNG, PDF and SVG from Stata}

{phang}
{cmd:saveas(}{it:files} [{cmd:,} {it:chart}|{it:page}|{it:table} {cmd:scale(}{it:#}{cmd:)} {cmd:close} {cmd:idle(}{it:#}{cmd:)}]{cmd:)} After writing the
HTML, render it in a headless browser session and save one or more files. Requires
{cmd:export()}. {it:files} are one or more paths ending in {cmd:.png}, {cmd:.pdf} or
{cmd:.svg} (quote paths with spaces).{p_end}

{p2colset 12 30 30 2}
{p2col:{cmd:chart}}(default) the chart and its key -- for {cmd:by()} pages every panel, laid two across{p_end}
{p2col:{cmd:page}}the whole page: title, chart, statistics panel, table (PNG is a full-page capture){p_end}
{p2col:{cmd:table}}the publication table only (post-estimation pages){p_end}
{p2col:{cmd:scale(#)}}PNG pixel density (default 2 = twice the CSS size; {cmd:scale(3)} for print){p_end}
{p2col:{cmd:close}}quit the session browser after this export{p_end}
{p2col:{cmd:idle(#)}}seconds the browser stays alive for the next export (default 10; it closes itself after that){p_end}
{p2colreset}

{p 8 8 2}
PDF and SVG are vector; PNG is rendered at {cmd:scale()}. The first {cmd:saveas()} of a
session starts Microsoft Edge or Google Chrome headless (about 2 s); later exports reuse
it. The browser is found automatically; {opt browser(path)} or
{cmd:global SPARKTA_BROWSER "C:\path\to\msedge.exe"} names it, {cmd:sparkta, findbrowser}
shows what would be used, {cmd:sparkta, closebrowser} quits it. Pages written for
{cmd:saveas()} are always self-contained ({cmd:online} is ignored, with a message).
Developers who build {cmd:sparkta-export.jar} with {cmd:build.bat}/{cmd:build.sh} get a
faster route (one SVG from the browser, PNG/PDF made in Java); the output is the same.{p_end}

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) export(fig1.html) saveas(fig1.png fig1.pdf)}{p_end}
{p 12 12 2}{cmd:sparkta price, type(boxplot) by(rep78) export(fig2.html) saveas(fig2.pdf)} -- panels two across{p_end}
{p 12 12 2}{cmd:sparkta price, type(boxplot) by(rep78) layout(vertical) export(fig2.html) saveas(fig2.pdf)} -- stacked{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) export(cp.html) saveas("out/coef table.pdf", table)}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) export(cp.html) saveas(cp.png, page scale(3) close)}{p_end}

"""

EXAMPLES_OLD = block("{marker examples}", "{marker stats_panel}")
EXAMPLES_OLD = patch(EXAMPLES_OLD, [
 ("{dlgtab:Offline mode}\n\n{p 4 4 2}\nFor institutional networks or air-gapped systems, bundle all JS inside the file:{p_end}\n\n{p 8 8 2}\n{stata `\"sparkta price, over(rep78) offline export(\"chart_offline.html\")\"':{space 2}sparkta price, over(rep78) offline export(\"chart_offline.html\")}{p_end}\n\n{p 4 4 2}\nThe resulting file is ~280KB and works with no internet connection.\n\n{dlgtab:Export to file}\n\n{p 8 8 2}\n{stata `\"sparkta price, type(cibar) over(rep78) export(\"prices.html\")\"':{space 2}sparkta price, type(cibar) over(rep78) export(\"prices.html\")}",
  "{dlgtab:Post-estimation}\n\n{p 8 8 2}\n{stata \"regress price mpg weight foreign\":regress price mpg weight foreign}{p_end}\n{p 8 8 2}\n{stata \"sparkta, type(coefplot) nocons levels(90 95)\":sparkta, type(coefplot) nocons levels(90 95)}{p_end}\n{p 8 8 2}\n{stata \"sparkta, type(coefplot) cistyle(band) connected coefsort(abs) tstat\":sparkta, type(coefplot) cistyle(band) connected coefsort(abs) tstat}{p_end}\n{p 8 8 2}\n{stata \"regress price i.rep78 mpg\":regress price i.rep78 mpg}{p_end}\n{p 8 8 2}\n{stata \"margins rep78\":margins rep78}{p_end}\n{p 8 8 2}\n{stata \"sparkta, type(marginsplot) cistyle(area whisker) connected\":sparkta, type(marginsplot) cistyle(area whisker) connected}{p_end}\n{p 8 8 2}\n{stata \"regress price i.foreign##c.mpg\":regress price i.foreign##c.mpg}{p_end}\n{p 8 8 2}\n{stata \"margins foreign, at(mpg=(15 20 25 30 35))\":margins foreign, at(mpg=(15 20 25 30 35))}{p_end}\n{p 8 8 2}\n{stata \"sparkta, type(marginsplot) over(foreign) cistyle(area)\":sparkta, type(marginsplot) over(foreign) cistyle(area)}{p_end}\n\n{dlgtab:Export to files}\n\n{p 8 8 2}\n{stata `\"sparkta price, type(cibar) over(rep78) export(\"prices.html\")\"':{space 2}sparkta price, type(cibar) over(rep78) export(\"prices.html\")}{p_end}\n{p 8 8 2}\n{cmd:sparkta price, type(cibar) over(rep78) export(prices.html) saveas(prices.png prices.pdf)}"),
 ("{stata `\"sparkta price, over(rep78) colors(#e41a1c|#377eb8|#4daf4a|#984ea3|#ff7f00)\"':{space 2}sparkta price, over(rep78) colors(#e41a1c|#377eb8|#4daf4a|#984ea3|#ff7f00)}",
  "{stata `\"sparkta price, over(rep78) colors(navy maroon forest_green gold%70 gs8)\"':{space 2}sparkta price, over(rep78) colors(navy maroon forest_green gold%70 gs8)}{p_end}\n{p 8 8 2}\n{stata \"sparkta price, over(rep78) palette(okabe)\":sparkta price, over(rep78) palette(okabe)}"),
])

OFFLINE = r"""{marker offline}{...}
{title:Self-contained pages and online}

{p 4 4 2}
By default every page is {bf:self-contained}: Chart.js 4.4 and the plugins (about
250-320 KB) are embedded at generation time, so the file opens on any machine with
no network request of any kind -- air-gapped systems, restricted data, archives.
A self-contained page is also a permanent snapshot: it renders the same years from
now, whatever happens to CDNs or library versions.{p_end}

{p 4 4 2}
{cmd:online} produces a compact file (about 50 KB) that loads the libraries from a
CDN when opened; it needs an internet connection to display and is refused on
networks that block CDN requests. Use it only when file size matters more than
portability. {cmd:saveas()} always writes a self-contained page.{p_end}

{p 4 4 2}
{cmd:offline} is accepted for scripts written before this was the default; it does
nothing.
"""

STORED = block("{marker stored}", "{marker mistakes}")
STORED = patch(STORED, [("{cmd:sparkta} stores nothing in {cmd:e()} or {cmd:r()}. It is a display\ncommand that produces an HTML file as a side effect.",
 "{cmd:sparkta} stores nothing in {cmd:e()} or {cmd:r()}. It is a display command\nthat produces files as a side effect. {cmd:e()} survives the call; {cmd:r()} does not\n(margins results are restored for {cmd:type(marginsplot)} only). {cmd:sparkta, findbrowser}\nsets {cmd:global SPARKTA_BROWSER_FOUND}.")])

MISTAKES = block("{marker mistakes}", "{marker limits}")
MISTAKES = patch(MISTAKES, [
 ("Read these before experimenting -- they cover the six errors that trip up\nalmost every new sparkta user.{p_end}", "The errors that trip up most new users.{p_end}"),
 ("{bf:2. Pie chart without over().}\n{cmd:sparkta price, type(pie)} will error. Pie and donut always require\n{cmd:over()} to know how to slice: {cmd:sparkta price, type(pie) over(rep78)}.{p_end}",
  "{bf:2. Pie chart with one variable and no over().}\n{cmd:sparkta price, type(pie)} will error: one variable has nothing to slice. Either\ngive several variables ({cmd:sparkta price mpg, type(pie)}), one variable plus\n{cmd:over()}, or {cmd:over()} alone for frequency counts.{p_end}"),
 ("{bf:6. Colors must use CSS format, not Stata format.}\n{cmd:colors()} accepts CSS color formats only: named colors ({cmd:red}, {cmd:steelblue}),\nhex codes ({cmd:#e74c3c}), or rgba values ({cmd:rgba(231,76,60,0.8)}).\nStata color names like {cmd:navy} and {cmd:maroon} work for common colors --\nbut Stata-specific formats like {cmd:gs8} or {cmd:%50} do not.",
  "{bf:6. Colours are space-separated, not pipe-separated, in colors().}\n{cmd:colors(navy maroon)} -- Stata names, hex, CSS names, {cmd:gs8}, {cmd:%50} opacity\nand {cmd:*0.6} intensity all work; see {helpb sparkta##colours:Colour grammar}. The\nper-entry options ({cmd:cicolors()}, {cmd:ylinecolor()}) are pipe-separated.{p_end}\n\n{p 4 8 4}\n{bf:7. A varlist with a post-estimation type.}\n{cmd:sparkta price, type(coefplot)} is an error: coefplot, marginsplot and eventstudy\nread stored results, never the data. Write {cmd:sparkta, type(coefplot)}.{p_end}\n\n{p 4 8 4}\n{bf:8. Expecting r() to survive.}\nA sparkta call clears {cmd:r()} (except the margins results it restores for\n{cmd:type(marginsplot)}). Save {cmd:r(table)} to a matrix first if you need it after the chart.{p_end}\n\n{p 4 8 4}\n{bf:9. A rebuilt jar and a version mismatch.}\nStata keeps one Java session; after updating sparkta, restart Stata. If the ado and jar\nversions differ the command stops with {it:ado/JAR version mismatch} -- reinstall all\nfiles together and restart."),
])

LIMITS = block("{marker limits}", "{marker memory}")
LIMITS = patch(LIMITS, [
 ("{p 4 4 2}\nPie and donut charts require exactly one numeric variable and {cmd:over()}.{p_end}", "{p 4 4 2}\nPie and donut charts take several variables, or one variable with {cmd:over()}, or {cmd:over()} alone (counts).{p_end}"),
 ("{p 4 4 2}\nWithout the {cmd:offline} option, the HTML file requires an internet connection\nto render (Chart.js loaded from CDN). Use {cmd:offline} for air-gapped use.",
  "{p 4 4 2}\n{cmd:by()} takes one variable. {cmd:by()}, {cmd:filters()} and {cmd:sliders()} do not apply to post-estimation types.{p_end}\n\n{p 4 4 2}\n{cmd:xtype(time)} falls back to a category axis (no date adapter is embedded). Format\ndates as labels, or plot the numeric date on a linear axis.{p_end}\n\n{p 4 4 2}\n{cmd:saveas()} needs Microsoft Edge or Google Chrome; there is no EPS writer (use PDF or SVG).{p_end}\n\n{p 4 4 2}\nPages with {cmd:online} need an internet connection to display; the default self-contained\npage does not."),
])

ALSO = r"""{marker also_see}{...}
{title:Also see}

{p 4 4 2}
Online: {browse "https://github.com/fahad-mirza/sparkta_stata":github.com/fahad-mirza/sparkta_stata}{break}
Install or update: {stata `"net install sparkta, from("https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/") replace"':net install sparkta, replace} (then restart Stata){break}
Related Stata commands: {helpb graph bar}, {helpb graph twoway}, {helpb marginsplot}, {helpb margins}, {helpb estimates}; from SSC: {helpb coefplot}, {helpb colorpalette}, {helpb estout}
"""

parts = [HEAD, THEMES, OPTIONS_HEAD, TABLE + "\n\n",
         grouping, "\n", stat, "\n", scatter, "\n", ci, "\n", hist, "\n", box, "\n",
         POSTEST, pub, "\n", COLOURS, annot, "\n", axes, "\n", behav, "\n", bars, "\n", points, "\n",
         axisutil, "\n", layout, "\n", tooltip, "\n", legend, "\n",
         "{marker appearance}{...}\n" + fonts, "\n", toolbar, "\n", BASIC, EXAMPLES_OLD, "\n",
         stats_panel, "\n", OFFLINE, "\n", STORED, "\n", MISTAKES, "\n", LIMITS, "\n", memory, "\n",
         methods, "\n", authors, "\n", ALSO]
text = "\n\n".join(p.rstrip("\n") for p in parts if p.strip()) + "\n"
text = re.sub(r"\n{3,}", "\n\n", text)

# SMCL source lines must stay short: the Viewer corrupted text past ~250 characters on one
# line (docs pass 1d: "{cmd:lwdid}) via {cmd:" lost from a 304-char {p2col:} line). Fold every
# line longer than 180 characters at a space that is OUTSIDE any {...} directive, so no
# directive ever spans a line break (the other SMCL rule).
def fold(line, limit=150):
    out = []
    while len(line) > limit:
        depth, cut = 0, -1
        for k, ch in enumerate(line):
            if ch == "{": depth += 1
            elif ch == "}": depth -= 1
            elif ch == " " and depth == 0 and k <= limit: cut = k
        if cut <= 0: break
        out.append(line[:cut]); line = line[cut + 1:]
    out.append(line)
    return out
text = "\n".join(x for l in text.split("\n") for x in fold(l))

# stale-claim guard
for bad in ["loaded from CDN automatically", "requires an internet connection\nto render", "filter2(", "scattteri",
            "Sturges", "all 149 options", "(minute-clock)te-clock", "(0-59)ariable", "Stata 15"]:
    assert bad not in text, "stale text survived: " + bad
# every SMCL directive on one line: braces balance per line
for i, l in enumerate(text.split("\n"), 1):
    if l.count("{") != l.count("}"):
        sys.exit("unbalanced braces on line %d: %s" % (i, l[:100]))
assert all(ord(c) < 128 for c in text), "non-ASCII"
open(OUT, "w").write(text)
import subprocess
rc = subprocess.call([sys.executable, os.path.join(ROOT, "verify", "smcl_check.py"), OUT])
if rc: sys.exit("smcl_check failed -- help file NOT valid")
rc = subprocess.call([sys.executable, os.path.join(ROOT, "verify", "smcl_render.py"), OUT, "--audit"])
if rc: sys.exit("smcl_render audit failed -- layout findings above")
print("wrote %s: %d lines, %d options in the table" % (OUT, text.count("\n"), len(names)))
