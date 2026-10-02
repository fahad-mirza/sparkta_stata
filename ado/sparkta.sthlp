{smcl}
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
{cmd:sparkta} {cmd:,} {cmd:type(coefplot|marginsplot|eventstudy)} [{opt matrix(M)} | {opt results(source)} | {opt estnames(names)}]
[{it:options}]{p_end}

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
{p2col:{helpb sparkta##postest:matrix()} {helpb sparkta##results:results()} {helpb sparkta##postest:estnames()}}inputs of the post-estimation
charts{p_end}
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
{p2col:{stata "sparkta price, type(cibar) over(rep78) filters(foreign)":sparkta price, type(cibar) over(rep78) filters(foreign)}}CI bars with a live
filter{p_end}
{p2col:{stata "sparkta price, type(violin) over(rep78) theme(dark_neon)":sparkta price, type(violin) over(rep78) theme(dark_neon)}}violins, dark
theme{p_end}
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
{p2col:{cmd:coefplot}}last estimation, {cmd:estnames()}, {cmd:matrix()} or {cmd:results()} -- point estimates with CI whiskers/bands/bars,
coefficients down the y-axis ({cmd:vertical} flips), a reference line at 0 (1 with {cmd:eform}), several models side by side{p_end}
{p2col:{cmd:marginsplot}}last {cmd:margins} (r(table), r(at)) or {cmd:results()} -- predicted margins over factor levels or {cmd:at()} values, one
series per interaction level ({cmd:over()}), contrasts and pairwise comparisons, nested CIs, area/whisker/bar CIs{p_end}
{p2col:{cmd:eventstudy}}last estimation, {cmd:matrix()} or {cmd:results()} -- coefficients on a numeric event-time axis with a hollow marker at the
reference period; leads and lags coloured apart; DiD estimators ({cmd:csdid}, {cmd:jwdid}, {cmd:lwdid}) via {cmd:matrix(r(table))} or
{cmd:results()}{p_end}
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
{p2col:{bf:pie, donut}}three forms: {cmd:sparkta price mpg, type(pie)} (one slice per variable total); {cmd:sparkta price, type(pie) over(rep78)}
(group sums or shares); {cmd:sparkta, type(pie) over(rep78)} (frequency counts){p_end}
{p2col:{bf:coefplot, marginsplot, eventstudy}}no varlist -- a varlist is an error{p_end}
{p2colreset}

{p 4 4 2}
{bf:over() vs by() vs filters():}
{cmd:over()} puts all groups on {it:one chart}.
{cmd:by()} creates {it:separate panels}.
{cmd:filters()} lets the {it:viewer} switch groups without re-running Stata.
They combine: {cmd:by()} makes panels, {cmd:over()} groups series inside each,
{cmd:filters()}/{cmd:sliders()} refresh every panel.

{marker themes}{...}
{title:Themes}

{p 4 4 2}
{cmd:theme(}{it:string}{cmd:)} accepts a background keyword, a palette name,
or a compound {it:background_palette}:

{p2colset 6 46 46 2}
{p2col:{bf:Backgrounds}}{p_end}
{p2col:{cmd:default}}white page, default colors{p_end}
{p2col:{cmd:dark}}dark page background{p_end}
{p2col:{cmd:light}}light gray page background{p_end}

{p2col:{bf:Color palettes}}{p_end}
{p2col:{cmd:tab1}}Tableau 10{p_end}
{p2col:{cmd:tab2}}ColorBrewer Set1{p_end}
{p2col:{cmd:tab3}}ColorBrewer Dark2{p_end}
{p2col:{cmd:cblind1}}Okabe-Ito colorblind-safe{p_end}
{p2col:{cmd:viridis}}perceptually uniform{p_end}
{p2col:{cmd:neon}}bright saturated (best on dark){p_end}
{p2col:{cmd:swift_red}}warm earth tones{p_end}

{p2col:{bf:Compound (background + palette)}}{p_end}
{p2col:{cmd:dark_tab1}}dark background + Tableau 10{p_end}
{p2col:{cmd:dark_tab2}}dark background + ColorBrewer Set1{p_end}
{p2col:{cmd:dark_tab3}}dark background + ColorBrewer Dark2{p_end}
{p2col:{cmd:dark_cblind1}}dark background + Okabe-Ito{p_end}
{p2col:{cmd:dark_viridis}}dark background + viridis{p_end}
{p2col:{cmd:dark_neon}}dark background + neon (recommended){p_end}
{p2col:{cmd:dark_swift_red}}dark background + swift_red{p_end}
{p2col:{cmd:light_tab1}}light background + Tableau 10{p_end}
{p2col:{cmd:light_tab2}}light background + ColorBrewer Set1{p_end}
{p2col:{cmd:light_tab3}}light background + ColorBrewer Dark2{p_end}
{p2col:{cmd:light_cblind1}}light background + Okabe-Ito{p_end}
{p2col:{cmd:light_viridis}}light background + viridis{p_end}
{p2col:{cmd:light_neon}}light background + neon{p_end}
{p2col:{cmd:light_swift_red}}light background + swift_red{p_end}
{p2colreset}

{p 4 4 2}
{cmd:colors()} and {cmd:palette()} override the theme palette; see
{helpb sparkta##colours:Colour grammar}.

{marker options}{...}
{title:Options}

{p 4 4 2}
Every option, grouped. The groups link to the detailed sections that follow.
Options marked as aliases exist so that Stata and coefplot (Ben Jann) spellings work
unchanged; give either the alias or the sparkta name, not both.

{synoptset 42 tabbed}{...}
{synopthdr}
{synoptline}
{syntab:Essential}
{synopt:{cmd:type(}{it:charttype}{cmd:)}}chart type (default {cmd:bar}); see {helpb sparkta##types:Chart types}{p_end}
{synopt:{cmd:over(}{it:varname [, showmissing]}{cmd:)}}group variable: all groups on one chart{p_end}
{synopt:{cmd:by(}{it:varname [, showmissing]}{cmd:)}}panel variable: one chart per group{p_end}
{synopt:{cmd:filters(}{it:varlist [, showmissing]}{cmd:)}}live dropdown filters (one per variable, up to 500 levels each){p_end}
{synopt:{cmd:sliders(}{it:varlist}{cmd:)}}dual-handle range sliders for numeric variables{p_end}
{synopt:{cmd:stat(}{it:statistic}{cmd:)}}{cmd:mean} (default) | {cmd:sum} | {cmd:count} | {cmd:median} | {cmd:min} | {cmd:max}; pie/donut: {cmd:pct} |
{cmd:sum}{p_end}
{synopt:{cmd:title(}{it:string}{cmd:)}}chart title{p_end}
{synopt:{cmd:export(}{it:filename.html}{cmd:)}}write the page to a file instead of opening the browser{p_end}
{synopt:{cmd:saveas(}{it:files [, suboptions]}{cmd:)}}also write PNG / PDF / SVG through a headless browser: chart|page|table scale(#) close idle(#);
see {helpb sparkta##saveas:saveas()}{p_end}

{syntab:Post-estimation input}
{synopt:{cmd:matrix(}{it:spec}{cmd:)}}plot from a matrix: M, M[#], M[,#], r(table), or several (A B C) for a multi-model chart; see
{helpb sparkta##matrixmode:matrix()}{p_end}
{synopt:{cmd:results(}{it:source [, suboptions]}{cmd:)}}plot from a dataset or frame of estimates (several sources = several models); see
{helpb sparkta##results:results()}{p_end}
{synopt:{cmd:estnames(}{it:namelist}{cmd:)}}stored estimates ({cmd:estimates store}) for a multi-model coefplot{p_end}
{synopt:{cmd:ci(}{it:spec}{cmd:)}}matrix mode: lower/upper bounds by position ci((2 3)), name ci(ll ul), or another matrix ci(C){p_end}
{synopt:{cmd:se(}{it:# | name | S}{cmd:)}}matrix mode: standard errors -> CI at {cmd:cilevel()} (z, or t with {cmd:df()}){p_end}
{synopt:{cmd:df(}{it:# | name}{cmd:)}}matrix mode: degrees of freedom for a t-based CI{p_end}
{synopt:{cmd:pvalue(}{it:# | name}{cmd:)}}matrix mode: p-values row/column (autodetected from r(table)){p_end}

{syntab:Coefficient selection and labels}
{synopt:{cmd:show(}{it:namelist}{cmd:)}}keep only these coefficients (eventstudy: the lead/lag terms; {cmd:show(*)} also keeps
Pre_avg/Post_avg){p_end}
{synopt:{cmd:omit(}{it:namelist}{cmd:)}}drop coefficients by name, e.g. {cmd:omit(_cons 1.foreign)}{p_end}
{synopt:{cmd:keep(}{it:namelist}{cmd:)}}= {cmd:show()} (Jann alias){p_end}
{synopt:{cmd:drop(}{it:namelist}{cmd:)}}= {cmd:omit()} (Jann alias){p_end}
{synopt:{cmd:nocons}}drop _cons (the default on coefplot/eventstudy){p_end}
{synopt:{cmd:cons}}keep _cons on charts that hide it by default{p_end}
{synopt:{cmd:nobase}}drop base and omitted factor levels (b. and o. prefixes){p_end}
{synopt:{cmd:order(}{it:namelist}{cmd:)}}display order of the coefficients{p_end}
{synopt:{cmd:coefsort(}{it:value|abs|pval|se|desc}{cmd:)}}sort coefficients ({cmd:sort()} accepts Jann's asc|desc|b|p|se|abs on these charts){p_end}
{synopt:{cmd:coeflabels(}{it:entries}{cmd:)}}display labels: coeflabels("mpg/Miles per gallon") or coeflabels(mpg = "Miles per gallon"){p_end}
{synopt:{cmd:coeflbl(}{it:entries}{cmd:)}}pipe-separated form of the same: coeflbl(mpg "MPG"|weight "Wt"){p_end}
{synopt:{cmd:headings(}{it:entries}{cmd:)}}section headings before coefficient #, chart and table: headings(1 "Vehicle"|3 "Origin"){p_end}
{synopt:{cmd:estlabels(}{it:L1~L2~L3}{cmd:)}}model labels for a multi-model chart, tilde-separated{p_end}
{synopt:{cmd:plotlabels(}{it:labels}{cmd:)}}= {cmd:estlabels()} (Jann alias): plotlabels("OLS" "IV"){p_end}

{syntab:Post-estimation look}
{synopt:{cmd:eform}}exponentiate (odds/hazard ratios); reference line moves to 1{p_end}
{synopt:{cmd:rescale(}{it:#}{cmd:)}}multiply estimates and CIs by #, e.g. {cmd:rescale(1000)}{p_end}
{synopt:{cmd:cilevel(}{it:#}{cmd:)}}confidence level 1-99 (default 95); also for cibar/ciline{p_end}
{synopt:{cmd:level(}{it:#}{cmd:)}}= {cmd:cilevel()} (Stata alias){p_end}
{synopt:{cmd:levels(}{it:# #}{cmd:)}}two nested CIs, inner first, e.g. {cmd:levels(90 95)}{p_end}
{synopt:{cmd:noci}}no confidence intervals{p_end}
{synopt:{cmd:cistyle(}{it:style [style]}{cmd:)}}how the CI is drawn: whisker (default) | band | bar | area; a pair such as cistyle(area whisker) on
marginsplot{p_end}
{synopt:{cmd:recastci(}{it:rcap|rarea|rband|rbar|rline}{cmd:)}}= {cmd:cistyle()} in Stata's {cmd:recastci()} words{p_end}
{synopt:{cmd:coefstyle(}{it:scatter|bar}{cmd:)}}point markers (default) or bars for the estimates{p_end}
{synopt:{cmd:connected}}join the point estimates with a line{p_end}
{synopt:{cmd:recast(}{it:connected|line}{cmd:)}}= {cmd:connected} (marginsplot alias){p_end}
{synopt:{cmd:vertical}}coefficients along x, estimates on y (coefplot; the eventstudy default){p_end}
{synopt:{cmd:refval(}{it:# | none}{cmd:)}}reference line value (default 0, or 1 with {cmd:eform}){p_end}
{synopt:{cmd:pexline(}{it:#}{cmd:)}}vertical reference line at an x value, e.g. {cmd:pexline(0)}{p_end}
{synopt:{cmd:reflinewidth(}{it:#}{cmd:)}}reference-line width px (default 1){p_end}
{synopt:{cmd:cicolors(}{it:c1|c2|...}{cmd:)}}CI colours per model; eventstudy: {cmd:cicolors(pre|post)}{p_end}
{synopt:{cmd:ciwidth(}{it:#}{cmd:)}}CI whisker/band line thickness px (default 1.5){p_end}
{synopt:{cmd:pointstyles(}{it:shape shape ...}{cmd:)}}marker shape per model/series{p_end}
{synopt:{cmd:refperiod(}{it:auto|none|#}{cmd:)}}eventstudy: the omitted (reference) period, drawn as a hollow marker at 0{p_end}
{synopt:{cmd:together}}eventstudy: join the post series to the reference period (default from estimation results){p_end}
{synopt:{cmd:separate}}eventstudy: leads and lags as separate series (default with {cmd:matrix()}/{cmd:results()}){p_end}

{syntab:Publication table}
{synopt:{cmd:stars(}{it:numlist}{cmd:)}}significance thresholds high to low (default {cmd:0.10 0.05 0.01}){p_end}
{synopt:{cmd:nostars}}no stars and no star legend{p_end}
{synopt:{cmd:tstat}}t/z statistic in brackets beneath each estimate{p_end}
{synopt:{cmd:interval}}confidence interval beneath each estimate{p_end}
{synopt:{cmd:nofooter}}drop the N / R-squared / legend footer{p_end}
{synopt:{cmd:notable}}no publication table (chart only){p_end}
{synopt:{cmd:indicators(}{it:entries}{cmd:)}}fixed-effect / indicator rows, one value per model: indicators("Firm FE/Yes Yes No"){p_end}
{synopt:{cmd:addstats(}{it:entries}{cmd:)}}extra footer rows: keywords rsq arsq fstat fpval ll chi2 rmse dep_mean, or manual rows "Label/v1 v2"{p_end}

{syntab:Panels and grouping}
{synopt:{cmd:layout(}{it:vertical|horizontal|grid}{cmd:)}}arrangement of {cmd:by()} panels; exports use a two-column grid unless layout(vertical) is
written out{p_end}
{synopt:{cmd:yfree}}each {cmd:by()} panel keeps its own value axis (default: one shared axis){p_end}
{synopt:{cmd:sortgroups(}{it:asc|desc}{cmd:)}}order of {cmd:over()}/{cmd:by()} groups{p_end}
{synopt:{cmd:nomissing}}exclude missing values (already the default; accepted for old scripts){p_end}
{synopt:{cmd:novaluelabels}}raw numeric codes instead of value labels{p_end}
{synopt:{cmd:nolabel}}= {cmd:novaluelabels} (Stata alias){p_end}
{synopt:{cmd:noallfilter}}no {it:All} entry in the filter dropdowns{p_end}
{synopt:{cmd:nostats}}no summary statistics panel{p_end}
{synopt:{cmd:collapsestats}}statistics panel starts collapsed{p_end}

{syntab:Axes}
{synopt:{cmd:xtitle(}{it:string}{cmd:)}}x-axis title{p_end}
{synopt:{cmd:ytitle(}{it:string}{cmd:)}}y-axis title{p_end}
{synopt:{cmd:xrange(}{it:min max}{cmd:)}}x-axis bounds (value axes only){p_end}
{synopt:{cmd:yrange(}{it:min max}{cmd:)}}y-axis bounds (value axes only){p_end}
{synopt:{cmd:ystart(}{it:zero}{cmd:)}}anchor the y-axis at zero{p_end}
{synopt:{cmd:xtype(}{it:linear|log|category|time}{cmd:)}}x-axis scale ({cmd:time} falls back to category, see {helpb sparkta##axes:Axes}){p_end}
{synopt:{cmd:ytype(}{it:linear|log}{cmd:)}}y-axis scale{p_end}
{synopt:{cmd:xreverse}}x runs right to left{p_end}
{synopt:{cmd:yreverse}}y runs top to bottom{p_end}
{synopt:{cmd:xtickcount(}{it:#}{cmd:)}}approximate x tick count{p_end}
{synopt:{cmd:ytickcount(}{it:#}{cmd:)}}approximate y tick count{p_end}
{synopt:{cmd:xstepsize(}{it:#}{cmd:)}}x tick interval{p_end}
{synopt:{cmd:ystepsize(}{it:#}{cmd:)}}y tick interval{p_end}
{synopt:{cmd:xticks(}{it:v|v|...}{cmd:)}}pin x tick positions{p_end}
{synopt:{cmd:yticks(}{it:v|v|...}{cmd:)}}pin y tick positions{p_end}
{synopt:{cmd:xlabels(}{it:l|l|...}{cmd:)}}custom x tick labels{p_end}
{synopt:{cmd:ylabels(}{it:l|l|...}{cmd:)}}custom y tick labels{p_end}
{synopt:{cmd:xtickangle(}{it:#}{cmd:)}}x tick label rotation, degrees{p_end}
{synopt:{cmd:ytickangle(}{it:#}{cmd:)}}y tick label rotation, degrees{p_end}
{synopt:{cmd:noticks}}hide tick marks (labels stay){p_end}
{synopt:{cmd:ygrace(}{it:#}{cmd:)}}padding above the y maximum as a fraction, e.g. {cmd:0.1}{p_end}
{synopt:{cmd:plotmargin(}{it:# | x y | l r b t}{cmd:)}}cushion between data and axes, percent of the data range, like plotregion(margin()); negative
allowed on post-estimation charts{p_end}
{synopt:{cmd:xgridlines(}{it:on|off}{cmd:)}}vertical grid lines{p_end}
{synopt:{cmd:ygridlines(}{it:on|off}{cmd:)}}horizontal grid lines{p_end}
{synopt:{cmd:xborder(}{it:on|off}{cmd:)}}x-axis border line{p_end}
{synopt:{cmd:yborder(}{it:on|off}{cmd:)}}y-axis border line{p_end}
{synopt:{cmd:gridcolor(}{it:color}{cmd:)}}grid line colour{p_end}
{synopt:{cmd:gridopacity(}{it:#}{cmd:)}}grid line opacity 0-1{p_end}
{synopt:{cmd:y2(}{it:varlist}{cmd:)}}variables on a right-hand y-axis{p_end}
{synopt:{cmd:y2title(}{it:string}{cmd:)}}right y-axis title{p_end}
{synopt:{cmd:y2range(}{it:min max}{cmd:)}}right y-axis bounds{p_end}

{syntab:Titles and text}
{synopt:{cmd:subtitle(}{it:string}{cmd:)}}subtitle (default: an automatic one-line description of what is plotted){p_end}
{synopt:{cmd:note(}{it:string}{cmd:)}}note below the chart{p_end}
{synopt:{cmd:caption(}{it:string}{cmd:)}}caption below the note{p_end}
{synopt:{cmd:notesize(}{it:css size}{cmd:)}}font size of note and caption, e.g. {cmd:notesize(14px)}{p_end}
{synopt:{cmd:notimestamp}}no {it:Made with sparkta ... date} footer line{p_end}
{synopt:{cmd:datalabels}}value labels on bars, points and slices{p_end}
{synopt:{cmd:pielabels}}percentage labels on pie/donut slices{p_end}
{synopt:{cmd:titlesize(}{it:#}{cmd:)}}title font size{p_end}
{synopt:{cmd:titlecolor(}{it:color}{cmd:)}}title colour{p_end}
{synopt:{cmd:subtitlesize(}{it:#}{cmd:)}}subtitle font size{p_end}
{synopt:{cmd:subtitlecolor(}{it:color}{cmd:)}}subtitle colour{p_end}
{synopt:{cmd:xtitlesize(}{it:#}{cmd:)}}x-axis title size{p_end}
{synopt:{cmd:xtitlecolor(}{it:color}{cmd:)}}x-axis title colour{p_end}
{synopt:{cmd:ytitlesize(}{it:#}{cmd:)}}y-axis title size{p_end}
{synopt:{cmd:ytitlecolor(}{it:color}{cmd:)}}y-axis title colour{p_end}
{synopt:{cmd:xlabsize(}{it:#}{cmd:)}}x tick label size{p_end}
{synopt:{cmd:xlabcolor(}{it:color}{cmd:)}}x tick label colour{p_end}
{synopt:{cmd:ylabsize(}{it:#}{cmd:)}}y tick label size{p_end}
{synopt:{cmd:ylabcolor(}{it:color}{cmd:)}}y tick label colour{p_end}

{syntab:Legend}
{synopt:{cmd:legend(}{it:top|bottom|left|right|none}{cmd:)}}legend position (default top){p_end}
{synopt:{cmd:nolegend}}= {cmd:legend(none)}{p_end}
{synopt:{cmd:leglabels(}{it:l|l|...}{cmd:)}}rename legend entries in series order{p_end}
{synopt:{cmd:relabel(}{it:l|l|...}{cmd:)}}rename {cmd:over()} groups on the axis AND in the legend{p_end}
{synopt:{cmd:legtitle(}{it:string}{cmd:)}}legend heading{p_end}
{synopt:{cmd:legsize(}{it:#}{cmd:)}}legend font size px{p_end}
{synopt:{cmd:legboxheight(}{it:#}{cmd:)}}legend swatch height px{p_end}
{synopt:{cmd:legcolor(}{it:color}{cmd:)}}legend text colour{p_end}
{synopt:{cmd:legbgcolor(}{it:color}{cmd:)}}box drawn behind the legend entries{p_end}

{syntab:Colours and theme}
{synopt:{cmd:theme(}{it:name}{cmd:)}}background and/or palette; see {helpb sparkta##themes:Themes}{p_end}
{synopt:{cmd:colors(}{it:c c ...}{cmd:)}}series colours: Stata names, hex, "r g b" triplets, {it:name}%50 opacity, {it:name}*0.6 intensity; see
{helpb sparkta##colours:Colour grammar}{p_end}
{synopt:{cmd:mcolor(}{it:c c ...}{cmd:)}}= {cmd:colors()} (twoway alias){p_end}
{synopt:{cmd:palette(}{it:colorpalette spec}{cmd:)}}palette from Ben Jann's {cmd:colorpalette} (SSC), e.g. {cmd:palette(okabe)},
{cmd:palette(viridis, n(6) reverse)}{p_end}
{synopt:{cmd:bgcolor(}{it:color}{cmd:)}}page background{p_end}
{synopt:{cmd:plotcolor(}{it:color}{cmd:)}}chart card background{p_end}
{synopt:{cmd:opacity(}{it:#}{cmd:)}}fill opacity 0-1{p_end}
{synopt:{cmd:gradient}}gradient fill from the palette colours{p_end}
{synopt:{cmd:gradcolors(}{it:start|end}{cmd:)}}custom gradient colours (per-series sets separated by a colon){p_end}

{syntab:Bars}
{synopt:{cmd:horizontal}}horizontal bars (= {cmd:type(hbar)}){p_end}
{synopt:{cmd:stacked}}stack the series{p_end}
{synopt:{cmd:barwidth(}{it:#}{cmd:)}}bar thickness 0-1 (default 0.8){p_end}
{synopt:{cmd:bargroupwidth(}{it:#}{cmd:)}}width of a bar group 0-1 (default 0.8){p_end}
{synopt:{cmd:borderradius(}{it:#}{cmd:)}}rounded bar corners px{p_end}

{syntab:Lines and points}
{synopt:{cmd:fill}}fill under the line (= {cmd:type(area)}){p_end}
{synopt:{cmd:areaopacity(}{it:#}{cmd:)}}area fill opacity 0-1{p_end}
{synopt:{cmd:smooth(}{it:#}{cmd:)}}line tension 0-1 (default 0.3){p_end}
{synopt:{cmd:stepped(}{it:before|after|middle}{cmd:)}}step line{p_end}
{synopt:{cmd:spanmissing}}connect the line across missing values{p_end}
{synopt:{cmd:linewidth(}{it:#}{cmd:)}}line width px (default 2){p_end}
{synopt:{cmd:lwidth(}{it:vthin..vthick | #}{cmd:)}}= {cmd:linewidth()} (twoway alias){p_end}
{synopt:{cmd:lpattern(}{it:solid|dash|dot|dashdot}{cmd:)}}dash pattern for every series{p_end}
{synopt:{cmd:lpatterns(}{it:p|p|...}{cmd:)}}dash pattern per series{p_end}
{synopt:{cmd:pointsize(}{it:#}{cmd:)}}marker radius px (default 4){p_end}
{synopt:{cmd:msize(}{it:vtiny..vhuge | #}{cmd:)}}= {cmd:pointsize()} (twoway alias){p_end}
{synopt:{cmd:pointstyle(}{it:shape}{cmd:)}}marker shape: circle (default) | cross | dash | line | rect | rectRounded | star | triangle{p_end}
{synopt:{cmd:msymbol(}{it:O|D|T|S|X|+}{cmd:)}}= {cmd:pointstyle()} in twoway letters{p_end}
{synopt:{cmd:pointborderwidth(}{it:#}{cmd:)}}marker border px{p_end}
{synopt:{cmd:pointrotation(}{it:#}{cmd:)}}marker rotation degrees{p_end}
{synopt:{cmd:pointhoversize(}{it:#}{cmd:)}}marker radius on hover px{p_end}
{synopt:{cmd:nopoints}}no markers on line/area charts{p_end}

{syntab:Scatter and bubble}
{synopt:{cmd:fit(}{it:lfit|qfit|lowess|exp|log|power|ma}{cmd:)}}fitted line on a scatter{p_end}
{synopt:{cmd:fitci}}95% band around the fit (lfit qfit exp log power){p_end}
{synopt:{cmd:mlabel(}{it:varname [, all]}{cmd:)}}marker labels from a variable ({cmd:all} forces every label){p_end}
{synopt:{cmd:mlabpos(}{it:0-59}{cmd:)}}label position on a minute clock (15 right, 30 below, 45 left){p_end}
{synopt:{cmd:mlabvposition(}{it:varname}{cmd:)}}per-observation minute-clock position{p_end}

{syntab:Distributions}
{synopt:{cmd:bins(}{it:#}{cmd:)}}histogram bins (default: Stata's rule, see {helpb sparkta##methods:Statistical methods}){p_end}
{synopt:{cmd:histtype(}{it:density|frequency|fraction}{cmd:)}}histogram y scale{p_end}
{synopt:{cmd:whiskerfence(}{it:#}{cmd:)}}box whisker fence multiplier (default 1.5){p_end}
{synopt:{cmd:bandwidth(}{it:#}{cmd:)}}violin kernel bandwidth (default: Stata {cmd:kdensity} rule){p_end}
{synopt:{cmd:mediancolor(}{it:color}{cmd:)}}median marker colour{p_end}
{synopt:{cmd:meancolor(}{it:color}{cmd:)}}mean marker colour{p_end}
{synopt:{cmd:cibandopacity(}{it:#}{cmd:)}}ciline band opacity (default 0.18){p_end}

{syntab:Pie and donut}
{synopt:{cmd:cutout(}{it:#}{cmd:)}}donut hole percent{p_end}
{synopt:{cmd:rotation(}{it:#}{cmd:)}}start angle degrees{p_end}
{synopt:{cmd:circumference(}{it:#}{cmd:)}}arc degrees (180 = half){p_end}
{synopt:{cmd:sliceborder(}{it:#}{cmd:)}}gap between slices px{p_end}
{synopt:{cmd:hoveroffset(}{it:#}{cmd:)}}slice pop-out on hover px{p_end}

{syntab:Reference lines and annotations}
{synopt:{cmd:yline(}{it:v|v}{cmd:)}}horizontal reference lines{p_end}
{synopt:{cmd:xline(}{it:v|v}{cmd:)}}vertical reference lines (value x-axes){p_end}
{synopt:{cmd:ylinecolor(}{it:c|c}{cmd:)}}yline colours{p_end}
{synopt:{cmd:xlinecolor(}{it:c|c}{cmd:)}}xline colours{p_end}
{synopt:{cmd:ylinelabel(}{it:t|t}{cmd:)}}yline labels{p_end}
{synopt:{cmd:xlinelabel(}{it:t|t}{cmd:)}}xline labels{p_end}
{synopt:{cmd:yband(}{it:lo hi|lo hi}{cmd:)}}horizontal shaded bands{p_end}
{synopt:{cmd:xband(}{it:lo hi|lo hi}{cmd:)}}vertical shaded bands{p_end}
{synopt:{cmd:ybandcolor(}{it:c|c}{cmd:)}}yband fills{p_end}
{synopt:{cmd:xbandcolor(}{it:c|c}{cmd:)}}xband fills{p_end}
{synopt:{cmd:apoint(}{it:y x y x ...}{cmd:)}}annotation points{p_end}
{synopt:{cmd:apointcolor(}{it:c|c}{cmd:)}}point colours{p_end}
{synopt:{cmd:apointsize(}{it:#}{cmd:)}}point radius px{p_end}
{synopt:{cmd:alabelpos(}{it:y x pos|...}{cmd:)}}annotation label positions (minute clock){p_end}
{synopt:{cmd:alabeltext(}{it:t|t}{cmd:)}}annotation label texts{p_end}
{synopt:{cmd:alabelfs(}{it:#}{cmd:)}}annotation label font px{p_end}
{synopt:{cmd:alabelgap(}{it:#}{cmd:)}}label offset from the point px{p_end}
{synopt:{cmd:aellipse(}{it:ymin xmin ymax xmax|...}{cmd:)}}annotation ellipses{p_end}
{synopt:{cmd:aellipsecolor(}{it:c|c}{cmd:)}}ellipse fills{p_end}
{synopt:{cmd:aellipseborder(}{it:c|c}{cmd:)}}ellipse borders{p_end}

{syntab:Tooltip and animation}
{synopt:{cmd:tooltipformat(}{it:string}{cmd:)}}number format, e.g. {cmd:,.0f}{p_end}
{synopt:{cmd:tooltipmode(}{it:index|point|nearest|x|y}{cmd:)}}hover mode (default index; post-estimation charts use nearest){p_end}
{synopt:{cmd:tooltipposition(}{it:average|nearest}{cmd:)}}tooltip anchor{p_end}
{synopt:{cmd:tooltipbg(}{it:color}{cmd:)}}tooltip background{p_end}
{synopt:{cmd:tooltipborder(}{it:color}{cmd:)}}tooltip border{p_end}
{synopt:{cmd:tooltipfontsize(}{it:#}{cmd:)}}tooltip font size{p_end}
{synopt:{cmd:tooltippadding(}{it:#}{cmd:)}}tooltip padding px{p_end}
{synopt:{cmd:animate(}{it:none|fast|slow}{cmd:)}}animation preset{p_end}
{synopt:{cmd:animduration(}{it:#}{cmd:)}}animation duration ms{p_end}
{synopt:{cmd:animdelay(}{it:#}{cmd:)}}animation delay ms{p_end}
{synopt:{cmd:easing(}{it:name}{cmd:)}}Chart.js easing function{p_end}
{synopt:{cmd:aspect(}{it:#}{cmd:)}}width/height ratio (default 2; pie/donut 2){p_end}
{synopt:{cmd:padding(}{it:# | t r b l}{cmd:)}}inner chart padding px{p_end}

{syntab:Page, export and browser}
{synopt:{cmd:online}}load Chart.js from a CDN instead of embedding it (smaller file, needs internet to view){p_end}
{synopt:{cmd:offline}}self-contained page -- the default; accepted for old scripts{p_end}
{synopt:{cmd:notoolbar}}hide the PNG / SVG / PDF toolbar shown above every chart{p_end}
{synopt:{cmd:download}}accepted for old scripts; does nothing (the toolbar is on by default){p_end}
{synopt:{cmd:browser(}{it:path}{cmd:)}}Edge/Chrome executable for {cmd:saveas()} when auto-detection fails (or set
{cmd:global SPARKTA_BROWSER}){p_end}
{synopt:{cmd:findbrowser}}diagnostic: list the browsers {cmd:saveas()} would use, then exit (no chart){p_end}
{synopt:{cmd:closebrowser}}quit the session's headless browser now (it also quits after 2 minutes idle){p_end}
{synoptline}
{p 4 4 2}All 215 options are listed above; the sections that follow describe them by group.{p_end}

{marker grouping}{...}
{dlgtab:Grouping, panels, and filtering}

{phang}
{opt over(varname [, showmissing])} {bf:The most-used option in sparkta.}
Groups the chart by a categorical variable so each group becomes a separate
series on the same chart. Value labels are used automatically when present.{p_end}

{p 8 8 2}
{cmd:over()} and {cmd:by()} can be used together for most chart types.
For example, {cmd:over(rep78) by(foreign)} creates one panel per foreign value,
each showing grouped bars by repair record. The exception is {cmd:pie} and
{cmd:donut}, where combining them is not permitted.{p_end}

{p 8 8 2}
Suboption {cmd:showmissing} includes observations where {it:varname} is missing
as an explicit {bf:(Missing)} group, displayed last regardless of {cmd:sortgroups()}.
Without {cmd:showmissing}, missing observations are silently excluded.

{p 8 8 2}{it:Example:} {cmd:sparkta price weight, type(bar) over(foreign)} -- one bar per variable per origin group.{p_end}

{phang}
{opt by(varname [, showmissing])} Creates separate chart panels for each value
of {it:varname}. Suboption {cmd:showmissing} adds a {bf:(Missing)} panel
for observations where {it:varname} is missing.{p_end}

{p 8 8 2}
{cmd:by()} and {cmd:over()} can be combined for most chart types (not {cmd:pie}/{cmd:donut}).
When combined, {cmd:by()} creates the panels and {cmd:over()} groups series within each panel.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, type(bar) by(foreign) layout(grid)} -- one panel per origin, 2-column grid{p_end}
{p 12 12 2}{cmd:sparkta price, type(bar) over(rep78) by(foreign)} -- grouped bars by repair record, separate panel per origin{p_end}

{phang}
{opt layout(string)} Arrangement of {cmd:by()} panels. Options:
{cmd:vertical} (default, stacked) | {cmd:horizontal} (side by side) |
{cmd:grid} (2-column grid). Only applies when {cmd:by()} is used.
{bf:Exports} ({cmd:saveas()} PNG/PDF and the toolbar) lay the panels two across
whatever is on screen, because a stacked page makes a tall narrow figure; write
{cmd:layout(vertical)} explicitly to keep the stack in exports too.

{p 8 8 2}{it:Example:} {cmd:sparkta price, by(rep78) layout(grid)}{p_end}

{phang}
{opt filters(varlist [, showmissing])} Adds one interactive dropdown per variable
below the chart, letting viewers filter the data without re-running Stata. The chart updates live. Suboption {cmd:showmissing} adds a
{bf:(Missing)} option for observations where {it:varname} is missing.

{p 8 8 2}Each variable may have up to 500 distinct values. The dropdowns act together (an
observation must pass every filter), the statistics panel and any fit line or CI are
recomputed on the fly, and {cmd:by()} panels are all refreshed. {cmd:noallfilter} removes
the {it:All} entry.{p_end}

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) filters(foreign)} -- toggle Domestic / Foreign{p_end}
{p 12 12 2}{cmd:sparkta price weight, over(rep78) filters(foreign headroom)} -- two independent dropdowns{p_end}

{phang}
{opt sliders(varlist)} Dual-handle range sliders for numeric variables. The viewer
drags the handles to restrict the plotted range; everything on the page updates.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) fit(lfit) fitci sliders(weight)}{p_end}

{phang}
{opt sortgroups(string)} Controls the order of {cmd:over()} and {cmd:by()}
group labels. Options: {cmd:asc} | {cmd:desc}.
When omitted, groups are sorted ascending (numeric labels sort numerically;
string labels sort alphabetically). Filter dropdown options are always
sorted ascending and are unaffected.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) sortgroups(desc)} -- highest repair-record group shown first.{p_end}

{phang}
{opt yfree} Give every {cmd:by()} panel its own value axis. By default all panels
share one axis so heights are comparable.

{phang}
{opt nostats} Suppresses the summary statistics panel below the chart.
{cmd:collapsestats} keeps it but starts it collapsed (the default on post-estimation pages).

{marker stat}{...}
{dlgtab:What gets plotted -- stat()}

{phang}
{opt stat(string)} The statistic plotted on the y-axis for bar and line charts.
{bf:Default is mean} -- sparkta always shows group averages unless you say otherwise.
Options: {cmd:mean} | {cmd:sum} | {cmd:count} | {cmd:median} |
{cmd:min} | {cmd:max}. For pie/donut: {cmd:pct} (default, percentage share) |
{cmd:sum} (raw totals).{p_end}

{p 8 8 2}
When {cmd:over()} is specified, one value is computed per group.
When {cmd:over()} is omitted, one value is computed per variable in
{it:varlist} and each variable becomes a separate bar or point.{p_end}

{p 8 8 2}
{bf:Not applicable to boxplot and violin.} These chart types always show
the full distribution. Specifying {cmd:stat()} with {cmd:type(boxplot)} or
{cmd:type(violin)} produces an error (unless {cmd:stat(mean)} is given,
which is silently accepted but has no effect).

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) stat(median)} -- median price per repair-record group{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) stat(count)} -- number of cars per group{p_end}
{p 12 12 2}{cmd:sparkta price, type(pie) over(rep78) stat(sum)} -- pie slices as raw totals{p_end}

{phang}
{opt cutout(#)} Donut hole size as a percentage of the chart radius.
Default {cmd:55}. Range 0{hline 1}99.{p_end}

{phang}
{opt rotation(#)} Starting angle in degrees for pie/donut slices. Default {cmd:0}
(top). Use {cmd:-90} to start at the left.{p_end}

{phang}
{opt circumference(#)} Total arc in degrees rendered by pie/donut.
Default {cmd:360} (full circle). A semicircle ({cmd:180}) combined with
{cmd:rotation(-90)} creates a gauge-style half-donut chart.

{p 8 8 2}{it:Example -- half-donut gauge:}{p_end}
{p 12 12 2}{cmd:sparkta price, type(donut) over(rep78) circumference(180) rotation(-90)}{p_end}

{phang}
{opt sliceborder(#)} Border width between pie/donut slices in pixels. Default {cmd:1}.{p_end}

{phang}
{opt hoveroffset(#)} Distance slices pop out when hovered, in pixels. Default {cmd:8}.

{marker scatter_opts}{...}
{dlgtab:Scatter labels}

{phang}
{opt mlabel(varname)} Label each scatter point with the value of {it:varname}.
Accepts string or numeric variables. Long labels are truncated automatically.
Requires {cmd:type(scatter)} or {cmd:type(bubble)}.{p_end}

{phang}
{opt mlabpos(#)} Position of the scatter marker label, specified as a
minute-clock direction (0-59). When omitted, labels appear {bf:above} the point
(equivalent to pos = 0 on a clock face, which maps to "top" in Chart.js).
Common values: 15 = right, 30 = below, 45 = left, 0 = centered on point.{p_end}

{phang}
{opt mlabvposition(varname)} Per-observation label position. Numeric variable
with values 0-59 (minute-clock), one per row. Overrides {cmd:mlabpos()} for
individual points. Useful when labels would otherwise overlap.
Requires {cmd:mlabel()} and {cmd:type(scatter)} or {cmd:type(bubble)}.

{p 8 8 2}{it:Example -- label points by make with custom positions:}{p_end}
{p 12 12 2}{stata "sparkta price mpg, type(scatter) mlabel(make)":sparkta price mpg, type(scatter) mlabel(make)}{p_end}

{dlgtab:Fit lines and CI bands}

{phang}
{opt fit(type)} Overlay a fitted curve on a scatter chart.
Types: {cmd:lfit} (linear), {cmd:qfit} (quadratic), {cmd:lowess}
(locally weighted smoother), {cmd:exp} (exponential y=ae^bx),
{cmd:log} (logarithmic y=a+b*ln(x)), {cmd:power} (y=ax^b),
{cmd:ma} (5-point moving average). Fit lines are computed in Stata before
the chart is built and recomputed automatically when a slider changes.{p_end}

{phang}
{opt fitci} Add a 95% confidence band around the fit line.
Supported for {cmd:lfit}, {cmd:qfit}, {cmd:exp}, {cmd:log},
{cmd:power}. CI is symmetric on the modelled scale and, for
{cmd:exp} and {cmd:power}, back-transformed to the original scale
(producing an asymmetric band, wider above the line than below).

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{stata "sparkta price mpg, type(scatter) fit(lfit) fitci":sparkta price mpg, type(scatter) fit(lfit) fitci}{p_end}
{p 12 12 2}{stata "sparkta price mpg, type(scatter) fit(qfit) fitci sliders(mpg)":sparkta price mpg, type(scatter) fit(qfit) fitci sliders(mpg)}{p_end}
{p 12 12 2}{stata "sparkta price mpg, type(scatter) over(foreign) fit(lfit)":sparkta price mpg, type(scatter) over(foreign) fit(lfit)}{p_end}

{marker ci_opts}{...}
{dlgtab:CI charts}

{phang}
{opt cilevel(#)} Confidence level for {cmd:cibar} and {cmd:ciline} charts.
Default is {cmd:95}. Accepts any integer from 1 to 99.
Confidence intervals are mean +/- t * SE, SE = SD / sqrt(n), with t the exact
two-tailed critical value for n-1 degrees of freedom ({cmd:invttail}) at any
level; the same is used again when a filter or slider changes. Groups with fewer
than 2 observations are omitted. On post-estimation charts {cmd:cilevel()} sets the
level of the drawn interval and {cmd:levels(# #)} draws two nested ones.{p_end}

{phang}
{opt cibandopacity(#)} Opacity of the CI shaded band for {cmd:ciline} charts.
Default is {cmd:0.18}. Range 0{hline 1}1.
Example: {cmd:cibandopacity(0.05)} for a faint band; {cmd:cibandopacity(0.35)}
for a more prominent band.

{marker dist_opts}{...}
{dlgtab:Histogram}

{phang}
{opt bins(#)} Number of bins. Must be an integer of 2 or greater. If omitted,
Stata's own {cmd:histogram} rule is used: k = min(sqrt(N), 10*ln(N)/ln(10)),
rounded down, so the default matches {cmd:histogram varname} in Stata. Fewer bins reveal
broad shape, more reveal fine structure.

{p 8 8 2}{it:Example:} {cmd:sparkta price, type(histogram) bins(20)}{p_end}

{phang}
{opt histtype(string)} Y-axis metric.

{p2colset 12 30 30 2}
{p2col:{cmd:density}}(default) count/(n*binWidth). Area sums to 1. Matches Stata {cmd:twoway histogram} default.{p_end}
{p2col:{cmd:frequency}}Raw observation count per bin.{p_end}
{p2col:{cmd:fraction}}Proportion of observations: count / n.{p_end}
{p2colreset}

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, type(histogram) histtype(density)} -- area sums to 1, comparable across groups{p_end}
{p 12 12 2}{cmd:sparkta price, type(histogram) histtype(frequency)} -- raw counts, easiest to interpret{p_end}

{p 8 8 2}
{cmd:histogram} does not support {cmd:over()}. Use {cmd:by()} to produce
separate histograms per group.

{dlgtab:Box plots and violin charts}

{phang}
{opt whiskerfence(#)} Sets the Tukey IQR multiplier {it:k} used to compute whisker
fences. The lower fence is Q1 - {it:k}*IQR and the upper fence is Q3 + {it:k}*IQR.
Observations outside these fences are plotted as outlier dots.
Default is {cmd:1.5} (standard Tukey fence, matches Stata {cmd:graph box}).
Use a larger value (e.g. {cmd:3}) to show fewer outliers; a smaller value
(e.g. {cmd:1}) to show more.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, type(boxplot) over(rep78) whiskerfence(1.5)} -- standard Tukey (default){p_end}
{p 12 12 2}{cmd:sparkta price, type(boxplot) over(rep78) whiskerfence(3)} -- extreme-value fences, fewer outliers shown{p_end}

{phang}
{opt mediancolor(string)} Override the automatic median marker color with a
specific CSS color (hex, RGB, or named). For {cmd:boxplot} and {cmd:hbox}, the
median is shown as a horizontal line; for {cmd:violin} and {cmd:hviolin}, as a
diamond. By default, the color is chosen automatically based on the average
luminance of the fill colors: dark fills get a white marker, light fills get
a dark marker.{p_end}

{phang}
{opt meancolor(string)} Override the automatic mean marker color. The mean is
always shown as a filled circle (dot). Like {cmd:mediancolor()}, the default
is chosen from fill luminance. Use this option to set a specific color.

{p 8 8 2}{it:Example:} {cmd:sparkta price, type(boxplot) over(rep78) mediancolor(#e74c3c) meancolor(#2980b9)}{p_end}

{phang}
{opt bandwidth(#)} KDE bandwidth for {cmd:violin} and {cmd:hviolin} charts.
Controls the smoothness of the estimated density curve: larger values produce
smoother, wider shapes; smaller values produce more peaked, data-hugging shapes.
If omitted, Stata's {cmd:kdensity} default is applied:
{it:h} = 0.9 * min(SD, IQR/1.349) * n^(-1/5), with the IQR from Stata's default
percentile rule, so the shape matches {cmd:kdensity varname}.{p_end}

{p 8 8 2}
Example: {cmd:bandwidth(2000)} for a price variable measured in dollars.{p_end}

{p 8 8 2}
{bf:Key.} Both chart types carry an HTML key above the plot (never drawn over
the data, so it reflows at any zoom): Median, Mean, one IQR box entry, Whiskers
and Outliers for {cmd:boxplot}; the density shapes, one IQR box entry and the
median/mean markers for {cmd:violin}.{p_end}

{p 8 8 2}
{bf:Violin animation.} Violin charts animate on load (shapes grow in from flat)
and on filter change (shapes tween smoothly to the new distribution; every violin,
for every plotted variable and group, is recomputed from the filtered data). This
animation is driven by a custom requestAnimationFrame loop and is not affected
by the {cmd:animate()} option.{p_end}

{p 8 8 2}
{bf:Statistical formulas.} All statistics (Q1, Q3, median, mean, whiskers)
match Stata {cmd:summarize, detail} output exactly, using the formula
h = (n+1)*p/100 with linear interpolation between adjacent order statistics.

{marker postest}{...}
{title:Post-estimation charts}

{dlgtab:Where the numbers come from}

{p 4 4 2}
A post-estimation chart never re-estimates anything. The page and its table show the
numbers Stata stored, read from one of four places:{p_end}

{p2colset 8 34 34 2}
{p2col:{bf:the last estimation}}{cmd:e(b)}, {cmd:e(V)}, {cmd:e(df_r)} (t intervals when present, z otherwise). Default when nothing else is
given.{p_end}
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
{p2col:{bf:from a regression}}{cmd:regress y lead5 ... lead2 lag0 ... lag5 i.t i.id} then
{cmd:sparkta, type(eventstudy) show(lead5 lead4 lead3 lead2 lag0 lag1 lag2 lag3 lag4 lag5)}. Use {cmd:show()} to leave the fixed effects out.{p_end}
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

{marker pubtable}{...}
{dlgtab:Publication table (post-estimation)}

{pstd}
For {cmd:type(coefplot)}, {cmd:type(eventstudy)} and {cmd:type(marginsplot)},
sparkta renders a publication-quality table beneath the chart. The chart and the
table are built from one source, so they can never disagree. For coefficient
charts the on-page table is an interactive, sortable grid (click a column header
to sort; coefficients across the rows, models across the columns) with a
{cmd:[Publication | Detailed]} toggle -- Detailed is the classic estimation-output
table. For {cmd:type(marginsplot)} the rows are the margin levels / at-values and
the columns are the series.

{pstd}
An Export toolbar offers {bf:Copy} (Markdown by default; also LaTeX and plain
TSV) and {bf:Download} (a tidy-long CSV with the columns
{cmd:model term b se ll ul p}; a {cmd:booktabs} + {cmd:threeparttable} LaTeX
{cmd:.tex} file that compiles on its own; and a table-only PDF). The LaTeX carries {cmd:headings()} as bold group
rows, {cmd:indicators()} as a fixed-effect block, base levels as {cmd:(base)},
and an N / R-squared footer. For anything beyond this, use
{cmd:estimates store} + {helpb estout:esttab}.

{phang}
{opt stars(numlist)} Significance thresholds, high to low, mapped to * ** ***.
Default {cmd:stars(0.10 0.05 0.01)} (estout's default, and sparkta's existing
legend). Give one, two or three thresholds in (0,1).

{phang}
{opt nostars} Suppress the significance stars and the star legend.

{phang}
{opt tstat} Show the t or z statistic (in brackets) beneath each estimate.

{phang}
{opt interval} Show the confidence interval beneath each estimate. Named
{cmd:interval}, not {cmd:ci}, because {cmd:ci()} is the matrix-mode option.

{phang}
{opt nofooter} Drop the N / R-squared / star-legend footer.

{phang}
{opt notable} Suppress the publication table entirely (the chart still renders).

{phang}
{opt indicators(entries)} Indicator rows such as fixed effects, one quoted entry per
row: {cmd:indicators("Firm FE/Yes Yes No" "Year FE/No Yes Yes")} -- the text before
the slash is the row label, the values after it are one per model, in
{cmd:estnames()} order.

{phang}
{opt addstats(entries)} Extra footer rows. Keywords are filled from the stored
results: {cmd:rsq arsq fstat fpval ll chi2 rmse dep_mean}; quoted entries follow the
{cmd:indicators()} form: {cmd:addstats(rsq dep_mean "Sample/Full Full Restricted")}.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:regress price mpg weight foreign}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) tstat interval}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) estnames(m1 m2 m3) headings(1 "Size"|2 "Origin") indicators("Controls/No No Yes")}{p_end}
{p 12 12 2}{cmd:sparkta, type(coefplot) stars(0.05 0.01) nofooter}{p_end}

{marker colours}{...}
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

{marker annotations}{...}
{dlgtab:Reference lines and annotations}

{phang}
{opt yline(values)} Draw one or more horizontal reference lines at the given
y-axis values. Pipe-separated. Works on all chart types that have a numeric y-axis.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) yline(6165)} -- single line at the overall mean{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) yline(5000|10000) ylinecolor(red|blue) ylinelabel(Low|High)} -- two colored and labeled lines{p_end}

{phang}
{opt xline(values)} Draw vertical reference lines at the given x-axis values.
Pipe-separated. Only meaningful on charts with a numeric x-axis
({cmd:scatter}, {cmd:bubble}, {cmd:line}, {cmd:area}, {cmd:ciline}, {cmd:histogram}).
Silently ignored for bar, horizontal bar, CI bar, boxplot, and violin charts
(categorical x-axis).{p_end}

{phang}
{opt ylinecolor(colors)} Colors for each yline, pipe-separated CSS colors.
Cycles if fewer colors than lines. Default: {cmd:rgba(150,150,150,0.8)}.{p_end}

{phang}
{opt xlinecolor(colors)} Colors for each xline. Same default.{p_end}

{phang}
{opt ylinelabel(texts)} Text label for each yline, pipe-separated.
An empty entry (two consecutive pipes) suppresses the label for that line.
Example: {cmd:ylinelabel(Mean|Upper bound)}.{p_end}

{phang}
{opt xlinelabel(texts)} Text label for each xline. Same behavior.{p_end}

{phang}
{opt yband(pairs)} Draw one or more horizontal shaded bands. Each band is
specified as {cmd:lo hi} (two space-separated y values); multiple bands are
pipe-separated.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) yband(4000 8000)} -- one shaded band{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) yband(4000 8000|10000 14000) ybandcolor(rgba(0,200,0,0.1)|rgba(200,0,0,0.1))} -- two differently colored
bands{p_end}

{phang}
{opt xband(pairs)} Vertical shaded bands. Same format as {cmd:yband()}.
Suppressed on categorical x-axis charts (same rules as {cmd:xline()}).{p_end}

{phang}
{opt ybandcolor(colors)} Fill color for each yband, pipe-separated.
Default: {cmd:rgba(150,150,150,0.12)}. Cycles if fewer colors than bands.{p_end}

{phang}
{opt xbandcolor(colors)} Fill color for each xband. Same default.{p_end}

{phang}
{opt apoint(coords)} Draw annotation point markers at specific coordinates.
Format: space-separated y x pairs, following Stata's {cmd:scatteri} convention.
Example: {cmd:apoint(15000 25 20000 30)} places markers at (y=15000, x=25)
and (y=20000, x=30). Note: y comes before x, matching Stata's {cmd:scatteri} convention.

{p 8 8 2}{it:Example -- highlight two points on a scatter:}{p_end}
{p 12 12 2}{cmd:sparkta price mpg, type(scatter) apoint(4099 22 15906 12) apointcolor(red|navy)}{p_end}

{phang}
{opt apointcolor(colors)} Colors for annotation points, pipe-separated.
Cycles. Default: {cmd:rgba(255,99,132,0.8)}.{p_end}

{phang}
{opt apointsize(#)} Radius in pixels for all annotation points. Default 8.{p_end}

{phang}
{opt alabelpos(coords)} Place annotation text labels at specific coordinates.
Format: pipe-separated entries of {cmd:y x} or {cmd:y x pos}, where {it:pos}
is a minute-clock direction (0{hline 1}59) controlling where the label appears
relative to the coordinate point:{p_end}

{p2colset 12 22 22 2}
{p2col:{bf:0}}Centered on the point (Stata {cmd:mlabpos(0)} equivalent; gap ignored){p_end}
{p2col:{bf:15}}Right of point{p_end}
{p2col:{bf:30}}Below point{p_end}
{p2col:{bf:45}}Left of point{p_end}
{p2col:{bf:60}}Above point{p_end}
{p2colreset}

{p 8 8 2}
Any integer from 0 to 60 is accepted, following the minute hand exactly.
(60 wraps back to above, same direction as approaching 12 on a clock face.)
Example: {cmd:alabelpos(15000 25 15|20000 30 30)} places the first label
to the right of (15000, 25) and the second label below (20000, 30).
Must be paired with {cmd:alabeltext()}.

{p 8 8 2}{it:Example -- two labels, first to the right, second below:}{p_end}
{p 12 12 2}{cmd:sparkta price mpg, type(scatter) alabelpos(4099 22 15|15906 12 30) alabeltext(Economy|Luxury)}{p_end}

{phang}
{opt alabeltext(texts)} Text content for annotation labels, pipe-separated.
Must have the same number of entries as {cmd:alabelpos()}.
Example: {cmd:alabeltext(Recession start|Recovery peak)}.{p_end}

{phang}
{opt alabelfs(#)} Font size in pixels for all annotation labels. Default 12.
(Renamed from {cmd:alabelfontsize()} in v3.5.108 -- original 15-character name
caused {it:option not allowed} errors on Windows Stata.){p_end}

{phang}
{opt alabelgap(#)} Pixel distance from the coordinate point to the label.
Default 15. Only meaningful when the minute-clock direction in {cmd:alabelpos()}
is non-zero; direction 0 always centers the label regardless of gap.
One value applies to all labels. Example: {cmd:alabelgap(20)}.{p_end}

{phang}
{opt aellipse(quads)} Draw annotation ellipses defined by bounding boxes.
Each ellipse is specified as four space-separated values {cmd:ymin xmin ymax xmax};
multiple ellipses are pipe-separated.
Example: {cmd:aellipse(10000 20 20000 30)} draws one ellipse;
{cmd:aellipse(10000 20 20000 30|5000 10 8000 15)} draws two.

{p 8 8 2}{it:Example -- highlight a cluster on a scatter plot:}{p_end}
{p 12 12 2}{cmd:sparkta price mpg, type(scatter) aellipse(3000 25 6000 35) aellipsecolor(rgba(255,165,0,0.15)) aellipseborder(orange)}{p_end}

{phang}
{opt aellipsecolor(colors)} Fill color per ellipse, pipe-separated.
Default: {cmd:rgba(99,132,255,0.15)}.{p_end}

{phang}
{opt aellipseborder(colors)} Border color per ellipse, pipe-separated.
Default: {cmd:rgba(99,132,255,0.6)}.{p_end}

{p 8 8 2}
{bf:Chart type restrictions.} Annotations are suppressed entirely for
{cmd:pie} and {cmd:donut} charts. Vertical annotations ({cmd:xline},
{cmd:xband}) are silently suppressed for bar, horizontal bar, CI bar,
boxplot, and violin charts (categorical x-axis). All other annotation
types work on all numeric-axis chart types. For {cmd:histogram},
{cmd:xline} and {cmd:xband} use fractional bin-index interpolation to
place marks at the correct proportional position across the numeric range.{p_end}

{p 8 8 2}
{bf:Requires.} The annotation plugin ({cmd:chartjs-plugin-annotation 3.0.1}) is
embedded in the page like every other library; with {cmd:online} it is loaded from
the CDN instead.

{marker axes}{...}
{dlgtab:Axes}

{phang}
{opt xtitle(string)} Label for the x-axis.{p_end}

{phang}
{opt ytitle(string)} Label for the y-axis.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) xtitle(Mileage (mpg)) ytitle(Price (USD))}{p_end}

{phang}
{opt xrange(min max)} X-axis minimum and maximum (two numbers). Example: {cmd:xrange(0 100)}.
Applies to a {it:value} (linear or log) axis only. On a {it:category} axis -- the default
x-axis for bar, line, and other categorical charts -- min/max are category positions, not
data values, so a numeric range there is ignored (it would otherwise blank the chart).{p_end}

{phang}
{opt yrange(min max)} Y-axis minimum and maximum (two numbers). Example: {cmd:yrange(0 10000)}.
Applies to a value axis; ignored on a category axis (see {cmd:xrange()}).{p_end}

{phang}
{opt ystart(zero)} Force the y-axis to begin at zero.
The only accepted value is {cmd:zero}. Incompatible with {cmd:ytype(logarithmic)}.
Useful when Chart.js auto-scales to a non-zero minimum, making differences look larger than they are.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) ystart(zero)}{p_end}

{phang}
{opt xtype(string)} X-axis scale type. Options: {cmd:linear} (default) |
{cmd:logarithmic} | {cmd:category}. {cmd:time} is accepted for compatibility but a true
date/time scale needs a Chart.js date adapter, which is not bundled (the page is
self-contained); {cmd:xtype(time)} therefore falls back to a category axis with a note.
For time series, format your dates and pass them as the x-axis labels, or plot the numeric
date on a {cmd:linear} axis.{p_end}

{phang}
{opt ytype(string)} Y-axis scale type. Options: {cmd:linear} (default) |
{cmd:logarithmic}.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) ytype(logarithmic)} -- compresses right-skewed price values for cleaner scatter
patterns.{p_end}

{phang}
{opt y2(varlist)} One or more variables to plot on the right (secondary) y-axis.
Each variable must also appear in {it:varlist}. The right axis is independently
scaled and labelled. Works with {cmd:type(bar)} and {cmd:type(line)} charts that
include {cmd:over()}. Example: {cmd:sparkta price mpg, type(line) over(foreign) y2(mpg)}.{p_end}

{phang}
{opt y2title(string)} Label for the right y-axis.{p_end}

{phang}
{opt y2range(min max)} Explicit min and max for the right y-axis.{p_end}

{phang}
{opt xtickcount(#)} Approximate number of ticks on the x-axis.{p_end}

{phang}
{opt ytickcount(#)} Approximate number of ticks on the y-axis.{p_end}

{phang}
{opt xtickangle(#)} Rotation angle of x-axis tick labels in degrees.
Useful when category labels are long and overlap horizontally.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) xtickangle(45)}{p_end}

{phang}
{opt ytickangle(#)} Rotation angle of y-axis tick labels in degrees.{p_end}

{phang}
{opt xlabels(string)} Custom tick labels for the x-axis, pipe-separated.
Applied left-to-right across the existing tick positions.

{p 8 8 2}{it:Example -- rename repair-record codes to plain English:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) xlabels(Poor|Fair|Average|Good|Excellent)}{p_end}

{phang}
{opt ylabels(string)} Custom tick labels for the y-axis, pipe-separated.{p_end}

{phang}
{opt xstepsize(#)} Interval between x-axis ticks. Example: {cmd:xstepsize(500)}.{p_end}

{phang}
{opt ystepsize(#)} Interval between y-axis ticks.{p_end}

{phang}
{opt xgridlines(on|off)} Show or hide vertical grid lines. Default {cmd:on}.{p_end}

{phang}
{opt ygridlines(on|off)} Show or hide horizontal grid lines. Default {cmd:on}.{p_end}

{phang}
{opt xborder(on|off)} Show or hide the x-axis border line. Default {cmd:on}.{p_end}

{phang}
{opt yborder(on|off)} Show or hide the y-axis border line. Default {cmd:on}.

{dlgtab:Chart behaviour}

{phang}
{opt horizontal} Render a bar chart with horizontal bars.
Equivalent to {cmd:type(hbar)}.{p_end}

{phang}
{opt stacked} Stack multiple series on top of each other.
Applies to bar and area charts.{p_end}

{phang}
{opt fill} Fill the area between a line chart and the baseline.
Equivalent to {cmd:type(area)}.{p_end}

{phang}
{opt areaopacity(#)} Fill opacity for {cmd:type(area)} and {cmd:fill} charts.
Accepts values from 0 (invisible) to 1 (fully opaque). Default is {cmd:0.35}.
When multiple variables share an axis, lower values (0.3{hline 1}0.5) keep
both fills visible. The y-axis is automatically anchored at zero for area charts.

{p 8 8 2}{it:Example:} {cmd:sparkta price weight, type(area) over(foreign) areaopacity(0.4)}{p_end}

{phang}
{opt smooth(#)} Line smoothness (Bezier tension), 0 to 1.
{cmd:smooth(0)} produces sharp corners; {cmd:smooth(0.6)} produces flowing curves.
Default is {cmd:0.3}.

{p 8 8 2}{it:Example:} {cmd:sparkta price, type(line) over(rep78) smooth(0.6)}{p_end}

{phang}
{opt spanmissing} Connect lines across missing values instead of breaking.{p_end}

{phang}
{opt stepped(string)} Render lines as step functions.
Options: {cmd:before} | {cmd:after} | {cmd:middle}.
{cmd:before} steps up before reaching the x-value; {cmd:after} steps after;
{cmd:middle} centers the step at the midpoint between x-values.

{p 8 8 2}{it:Example:} {cmd:sparkta price, type(line) over(rep78) stepped(after)}{p_end}

{dlgtab:Bar appearance}

{phang}
{opt barwidth(#)} Proportion of available width each bar occupies, 0 to 1.
Example: {cmd:barwidth(0.6)} for narrower bars.{p_end}

{phang}
{opt bargroupwidth(#)} Proportion of available width allocated to the group
of bars when multiple series are shown, 0 to 1.
Increasing this value narrows the gaps between groups.

{p 8 8 2}{it:Example:} {cmd:sparkta price weight, over(rep78) barwidth(0.8) bargroupwidth(0.7)}{p_end}

{phang}
{opt borderradius(#)} Rounded corner radius of bars in pixels.
Values of 4{hline 1}8 give a modern look without excessive rounding.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) borderradius(6)}{p_end}

{phang}
{opt opacity(#)} Fill opacity of bars, 0 to 1. Default {cmd:0.85}.

{dlgtab:Points and lines}

{phang}
{opt pointsize(#)} Radius of point markers in pixels. Default {cmd:4}.
Use {cmd:pointsize(0)} to hide markers.{p_end}

{phang}
{opt pointstyle(string)} Shape of point markers. Options:
{cmd:circle} (default) | {cmd:cross} | {cmd:dash} | {cmd:line} |
{cmd:rect} | {cmd:rectRounded} | {cmd:star} | {cmd:triangle}.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) pointstyle(triangle) pointsize(6)}{p_end}

{phang}
{opt pointborderwidth(#)} Border width of point markers in pixels. Default {cmd:1}.{p_end}

{phang}
{opt pointrotation(#)} Rotation of point markers in degrees. Default {cmd:0}.{p_end}

{phang}
{opt linewidth(#)} Width of lines in pixels. Default {cmd:2}.{p_end}

{phang}
{opt lpattern(string)} Line dash pattern applied to all series on line and area charts.
Options: {cmd:solid} (default) | {cmd:dash} | {cmd:dot} | {cmd:dashdot}.
Example: {cmd:lpattern(dash)} draws all series as dashed lines.{p_end}

{phang}
{opt lpatterns(string)} Per-series line dash patterns, pipe-separated in series order.
Cycles if fewer patterns are supplied than series.
Accepted tokens: {cmd:solid} | {cmd:dash} | {cmd:dot} | {cmd:dashdot}.
Example: {cmd:lpatterns(solid|dash|dot)} gives the first series a solid line,
the second a dashed line, and the third a dotted line.
When both {cmd:lpattern()} and {cmd:lpatterns()} are specified, {cmd:lpatterns()}
takes precedence for each series it covers; remaining series fall back to {cmd:lpattern()}.{p_end}

{phang}
{opt nopoints} Suppress point markers on line and area charts. When specified,
{cmd:pointsize()} and {cmd:pointhoversize()} have no effect. Equivalent to
{cmd:pointsize(0)} but also suppresses the hover highlight.{p_end}

{phang}
{opt pointhoversize(#)} Point radius in pixels when the cursor hovers over a data point.
Default is {cmd:pointsize + 2}. Ignored when {cmd:nopoints} is specified.{p_end}

{phang}
{opt notesize(string)} Font size for the note and caption text below the chart.
Accepts any valid CSS font-size value, for example {cmd:notesize(1rem)},
{cmd:notesize(14px)}, or {cmd:notesize(0.9em)}. When omitted, the theme defaults are used ({cmd:.85rem} for note, {cmd:.78rem} for caption).

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) note(Source: 1978 auto data) notesize(0.8rem)}{p_end}

{phang}
{opt gradient} Apply a vertical gradient fill to area and bar charts using
automatic palette-derived colors.
On area charts, the fill runs from the full series color at the top to
transparent at the bottom, creating a clean fade effect. On bar charts,
the gradient runs from the full color at the top to 60% opacity at the
bottom, adding subtle depth.

{p 8 8 2}{it:Example:} {cmd:sparkta price, type(area) over(rep78) gradient}{p_end}

{phang}
{opt gradcolors(c1|c2)} Set custom start and end colors for the gradient,
separated by {cmd:|}. Any CSS color is accepted: hex codes, {cmd:rgba()},
or named colors such as {cmd:transparent}. Examples:
{cmd:gradcolors(#1e40af|transparent)}, {cmd:gradcolors(rgba(251,146,60,1)|rgba(251,146,60,0))}.
Specifying {cmd:gradcolors()} automatically enables the gradient fill without
needing to also specify {cmd:gradient}. {cmd:gradient} has no effect on line-only,
scatter, pie/donut, histogram, boxplot, or violin charts.

{p 8 8 2}{it:Example -- blue fade on an area chart:}{p_end}
{p 12 12 2}{cmd:sparkta price, type(area) over(rep78) gradcolors(#1e40af|transparent)}{p_end}

{phang}
{opt leglabels(list)} Rename legend entries using a pipe-separated list, applied
in dataset order. Most useful when {cmd:over()} is specified, since {cmd:over()} creates
one dataset per group, each with its own legend entry. For example, if {cmd:over(foreign)}
produces groups 0 and 1, {cmd:leglabels(Domestic|Foreign)} replaces both default labels.
You may supply fewer labels than datasets; extra datasets keep their auto-generated names.
Not applicable to pie or donut charts (their legend labels come from data values, not dataset names).

{p 8 8 2}{it:Example -- foreign=0 is "Domestic", foreign=1 is "Foreign":}{p_end}
{p 12 12 2}{cmd:sparkta price, over(foreign) leglabels(Domestic|Foreign)}{p_end}

{phang}
{opt relabel(list)} Rename {cmd:over()} group labels on {it:both} the x-axis tick
labels {it:and} the legend simultaneously, using a pipe-separated list in group order.
This mirrors Stata's {cmd:over(var, relabel(1 "A" 2 "B"))} with {cmd:asyvars showyvars}.{p_end}

{p 8 8 2}
{cmd:relabel()} takes priority over {cmd:leglabels()} when both are supplied.
You may supply fewer labels than groups; extra groups keep their auto-generated names.

{p 8 8 2}{bf:Behavior by chart type:}{p_end}
{p2colset 12 40 40 2}
{p2col:{it:bar}/{it:hbar} + {cmd:over()}}x-axis and legend both renamed (colored swatches per group){p_end}
{p2col:{it:line}/{it:area} + {cmd:over()}}legend renamed (series names){p_end}
{p2col:{it:stackedbar} + {cmd:over()}}legend renamed (segment labels){p_end}
{p2col:{it:scatter}/{it:cibar}/{it:ciline}}legend renamed (group series){p_end}
{p2col:{it:boxplot}/{it:violin} + {cmd:over()}}x-axis renamed; inline legend shows chart-element symbols, not group names{p_end}
{p2col:{it:pie}/{it:donut}}{cmd:relabel()} has no effect (slice labels come from data values){p_end}
{p2colreset}{...}

{p 8 8 2}{it:Note for multi-variable charts:} when multiple numeric variables are
combined with {cmd:over()}, {cmd:relabel()} renames the legend series entries
(variable dataset names) rather than the x-axis group tick labels.
This is a minor difference from Stata, where {cmd:relabel()} always targets
the over-group labels on the x-axis.  In practice this combination is uncommon.

{p 8 8 2}{it:Example -- label rep78 groups in plain English:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) relabel(Poor|Fair|Average|Good|Excellent)}{p_end}

{p 8 8 2}{it:Example -- with by() panels:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) by(foreign) relabel(Poor|Fair|Average|Good|Excellent)}{p_end}

{phang}
{opt xticks(list)} Pin x-axis tick positions to exact values.
Accepts a pipe-separated list of numbers. For example, {cmd:xticks(0|25|50|75|100)}
produces five evenly-spaced ticks on a 0-100 scale regardless of Chart.js auto-ranging.
Only meaningful on numeric (linear or logarithmic) x-axes; ignored on category axes.
Compatible with {cmd:noticks}: tick marks are still suppressed when both are specified,
but the tick label positions are controlled by this option.{p_end}

{phang}
{opt yticks(list)} Same as {cmd:xticks()} but for the y-axis.
Example: {cmd:yticks(0|10000|20000|30000)} for a salary chart.
All tokens must be valid numbers; non-numeric tokens produce an error.

{p 8 8 2}{it:Example -- show only round-thousand markers on the price axis:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) yticks(0|5000|10000|15000)}{p_end}

{dlgtab:Axis utilities}

{phang}
{opt yreverse} Reverse the direction of the y-axis so that values increase
downward rather than upward. Useful for rankings, depth scales, or any measure
where lower numeric values represent a "better" or "deeper" position.

{p 8 8 2}{it:Example -- rank chart where 1st place appears at top:}{p_end}
{p 12 12 2}{cmd:sparkta rank_var, over(group_var) yreverse ytitle(Rank)}{p_end}

{phang}
{opt xreverse} Reverse the direction of the x-axis so that values decrease
left to right. Applies to numeric (linear) x-axes.{p_end}

{phang}
{opt noticks} Hide the short tick mark lines on both axes while keeping
tick labels visible. Produces a cleaner, more minimal look.
Compatible with {cmd:xticks()} and {cmd:yticks()}.{p_end}

{phang}
{opt ygrace(#)} Add proportional whitespace above the y-axis maximum.
Accepts a fraction from 0 to 1: {cmd:ygrace(0.1)} extends the axis
ceiling by 10% of the data range, preventing the tallest bar or point
from touching the top of the plot area.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) datalabels ygrace(0.15)} -- leaves room above the tallest bar so data labels are not
clipped.{p_end}

{phang}
{opt animduration(#)} Set the animation duration in milliseconds directly.
Default is approximately {cmd:1000}ms (Chart.js native default).
Overrides {cmd:animate()} when both are specified.
Use when {cmd:animate(fast|normal|slow)} does not give precise enough control.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) animduration(3000)} -- 3-second animation regardless of {cmd:animate()} preset.{p_end}

{dlgtab:Layout and animation}

{phang}
{opt aspect(#)} Chart aspect ratio (width / height).
Values below 1 produce a taller chart; above 1 a wider chart.
Default is approximately 2 (wide landscape).

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) aspect(1)} -- square chart.{p_end}

{phang}
{opt padding(#)} Inner padding of the chart area in pixels ({cmd:padding(t r b l)} for four sides).{p_end}

{phang}
{opt plotmargin(spec)} Cushion between the data and the axes as a percent of the data
range, like Stata's {cmd:plotregion(margin())}: {cmd:plotmargin(5)} all sides,
{cmd:plotmargin(x y)}, or {cmd:plotmargin(l r b t)}. Post-estimation charts accept
negative values to crop long CI tails.{p_end}

{phang}
{opt animate(string)} Animation speed. Options: {cmd:fast} | {cmd:slow} | {cmd:none}.
When omitted, Chart.js uses its native default (~1000ms).
{cmd:fast} = 150ms, {cmd:slow} = 1500ms, {cmd:none} = instant.
For precise control use {cmd:animduration()} instead.{p_end}

{phang}
{opt easing(string)} Animation easing function. Accepts any Chart.js
easing name -- this is a freeform string passed directly to Chart.js,
not validated by sparkta. Common values:
{cmd:linear}, {cmd:easeInOutQuart} (default),
{cmd:easeOutBounce}, {cmd:easeInElastic}, {cmd:easeOutCirc}.
Full list: {browse "https://www.chartjs.org/docs/latest/configuration/animations.html":Chart.js animation docs}.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) animate(slow) easing(easeOutBounce)}{p_end}

{phang}
{opt animdelay(#)} Delay before animation starts in milliseconds.

{dlgtab:Tooltip}

{phang}
{opt tooltipformat(string)} Number format for tooltip values.
Accepts a d3-format string controlling decimal places and comma grouping.
The format is parsed for a decimal spec ({cmd:.Nf}) and comma ({cmd:,}).

{p2colset 12 26 26 2}
{p2col:{cmd:,.0f}}comma thousands, 0 decimal places (e.g. 12,345){p_end}
{p2col:{cmd:,.2f}}comma thousands, 2 decimal places (e.g. 12,345.67){p_end}
{p2col:{cmd:.1f}}no comma, 1 decimal place (e.g. 12345.6){p_end}
{p2col:{cmd:.0f}}no comma, rounded integer (e.g. 12346){p_end}
{p2colreset}

{p 8 8 2}When omitted, values are auto-formatted (0-4 dp, no trailing zeros,
comma thousands grouping).{p_end}

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) tooltipformat("$,.0f")} -- displays values as {cmd:$12,345}.{p_end}

{phang}
{opt tooltipmode(string)} Tooltip interaction mode. Options: {cmd:index}
(default) | {cmd:nearest} | {cmd:point} | {cmd:x} | {cmd:y}. Post-estimation charts
always use {cmd:nearest} so the tooltip lands on the marker under the cursor and
hides when the cursor leaves it.
{cmd:index} shows all series values at the hovered x-position simultaneously --
useful for comparing multiple lines at a glance.

{p 8 8 2}{it:Example:} {cmd:sparkta price weight, type(line) over(foreign) tooltipmode(index)}{p_end}

{phang}
{opt tooltipposition(string)} Tooltip placement. Options: {cmd:average}
(default) | {cmd:nearest}.{p_end}

{phang}
{opt tooltipbg(string)} Background color of the tooltip box.
Accepts any CSS color: hex ({cmd:#1a1a2e}), named ({cmd:navy}), or
{cmd:rgba()} ({cmd:rgba(0,0,0,0.9)}). Default adapts to theme.{p_end}

{phang}
{opt tooltipborder(string)} Border color of the tooltip box.
Useful for matching a chart's accent color. Example: {cmd:tooltipborder(#4e79a7)}.{p_end}

{phang}
{opt tooltipfontsize(#)} Font size of tooltip text in pixels.
Default is 13. Example: {cmd:tooltipfontsize(14)}.{p_end}

{phang}
{opt tooltippadding(#)} Internal padding inside the tooltip box in pixels.
Default is 10. Example: {cmd:tooltippadding(14)}.

{dlgtab:Legend}

{phang}
{opt legend(string)} Legend position. Options: {cmd:top} (default) |
{cmd:bottom} | {cmd:left} | {cmd:right} | {cmd:none} (hides legend).{p_end}

{phang}
{opt nolegend} Suppress the legend entirely.
Equivalent to {cmd:legend(none)}; provided as a convenient bare flag following
Stata convention (analogous to Stata's {cmd:legend(off)}).
Example: {cmd:sparkta price weight, over(rep78) nolegend}{p_end}

{phang}
{opt legtitle(string)} Title displayed at the top of the legend.{p_end}

{phang}
{opt legsize(#)} Font size of legend labels in pixels. Default {cmd:10}.{p_end}

{phang}
{opt legboxheight(#)} Height of the legend color box in pixels.{p_end}

{phang}
{opt legcolor(string)} Text color of legend labels.
Example: {cmd:legcolor(#ffffff)} for white labels on a dark legend background.{p_end}

{phang}
{opt legbgcolor(string)} Background color drawn behind the legend entries (a lightly rounded box; since v3.6.0).
Accepts any CSS color including {cmd:rgba()} for transparency.
Example: {cmd:legbgcolor(rgba(255,255,255,0.85))}.

{marker appearance}{...}
{dlgtab:Font styling}

{phang}
{opt titlesize(#)} Font size of the main title in pixels. Example: {cmd:titlesize(28)}.{p_end}

{phang}
{opt titlecolor(string)} Color of the main title text.
Example: {cmd:titlecolor(#2c3e50)}.{p_end}

{phang}
{opt subtitlesize(#)} Font size of the subtitle in pixels.{p_end}

{phang}
{opt subtitlecolor(string)} Color of the subtitle text.{p_end}

{phang}
{opt xtitlesize(#)} Font size of the x-axis title in pixels.{p_end}

{phang}
{opt xtitlecolor(string)} Color of the x-axis title text.{p_end}

{phang}
{opt ytitlesize(#)} Font size of the y-axis title in pixels.{p_end}

{phang}
{opt ytitlecolor(string)} Color of the y-axis title text.{p_end}

{phang}
{opt xlabsize(#)} Font size of x-axis tick labels in pixels.{p_end}

{phang}
{opt xlabcolor(string)} Color of x-axis tick labels.{p_end}

{phang}
{opt ylabsize(#)} Font size of y-axis tick labels in pixels.{p_end}

{phang}
{opt ylabcolor(string)} Color of y-axis tick labels.{p_end}

{p 8 8 2}
All styling options accept standard CSS colors: named ({cmd:navy}), hex
({cmd:#2c3e50}), RGB ({cmd:rgb(44,62,80)}), or RGBA ({cmd:rgba(44,62,80,0.9)}).
Font size options accept positive integers (pixels). When these options are
omitted, the theme default is used.

{dlgtab:Export toolbar and data handling}

{phang}
{opt notoolbar} Hide the export toolbar. Every chart shows a small toolbar above
the plot by default (since v3.6.0): {bf:PNG} saves the figure as a bitmap (title,
context line, chart and key, at twice the screen resolution), {bf:SVG} the same
figure as a scalable vector (opens in Illustrator, Inkscape, Word), {bf:PDF} the same
figure as a vector PDF through the browser's print dialog. When a {cmd:filters()} dropdown or
{cmd:sliders()} range is active at export time, every export carries a line
{it:Filters: variable = value; ...; N = rows} under the context line. A {cmd:by()} page has ONE
toolbar above the panel grid; all three buttons export every panel, laid two across
with the shared key, exactly as {cmd:saveas()} does. For the whole page (statistics
panel, tables) use the browser's own Print command; the publication table under a
post-estimation chart has its own PDF button. {cmd:download} is still accepted for
old scripts and does nothing. {cmd:saveas()} always keeps the page exporter, so
{cmd:notoolbar} cannot be combined with it.{p_end}

{phang}
{opt nomissing} Missing values in {cmd:over()}, {cmd:by()}, {cmd:filters()} and
{it:varlist} are automatically excluded
before chart building, matching Stata convention. Specifying {cmd:nomissing}
is therefore no longer required but is accepted for backward compatibility.{p_end}

{phang}
{opt novaluelabels} Display raw numeric codes instead of value labels
for {cmd:over()} and {cmd:by()} group names.
Useful when value labels are long or when you need to display the underlying codes.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) novaluelabels} -- shows "1 2 3 4 5" instead of value label text.{p_end}

{marker export}{...}
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
{cmd:saveas(}{it:files} [{cmd:,} {it:chart}|{it:page}|{it:table} {cmd:scale(}{it:#}{cmd:)} {cmd:close} {cmd:idle(}{it:#}{cmd:)}]{cmd:)} After writing
the
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

{marker examples}{...}
{title:Examples}

{p 4 4 2}
All examples use Stata's built-in {cmd:auto} dataset. Click any command
to run it directly.

{dlgtab:Getting started}

{p 4 4 2}
Load the data first:{p_end}

{p 8 8 2}
{stata "sysuse auto, clear":sysuse auto, clear}{p_end}

{p 4 4 2}
Your first chart -- mean price by repair record:{p_end}

{p 8 8 2}
{stata "sparkta price, over(rep78)":sparkta price, over(rep78)}{p_end}

{p 4 4 2}
Add a title and an interactive filter dropdown:{p_end}

{p 8 8 2}
{stata `"sparkta price, over(rep78) title("Car Prices") filters(foreign)"':{space 2}sparkta price, over(rep78) title("Car Prices") filters(foreign)}{p_end}

{p 4 4 2}
CI bar chart with live filter and stats panel:{p_end}

{p 8 8 2}
{stata "sparkta price, type(cibar) over(rep78) filters(foreign)":sparkta price, type(cibar) over(rep78) filters(foreign)}

{dlgtab:Scatter and fit lines}

{p 8 8 2}
{stata "sparkta price mpg, type(scatter)":sparkta price mpg, type(scatter)}{p_end}
{p 8 8 2}
{stata "sparkta price mpg, type(scatter) fit(lowess) fitci":sparkta price mpg, type(scatter) fit(lowess) fitci}{p_end}
{p 8 8 2}
{stata "sparkta price mpg, type(scatter) fit(qfit) fitci sliders(mpg)":sparkta price mpg, type(scatter) fit(qfit) fitci sliders(mpg)}{p_end}
{p 8 8 2}
{stata "sparkta price mpg, type(scatter) over(foreign) fit(lfit)":sparkta price mpg, type(scatter) over(foreign) fit(lfit)}

{dlgtab:Distributions}

{p 8 8 2}
{stata "sparkta price, type(histogram)":sparkta price, type(histogram)}{p_end}
{p 8 8 2}
{stata "sparkta price, type(boxplot) over(rep78)":sparkta price, type(boxplot) over(rep78)}{p_end}
{p 8 8 2}
{stata "sparkta price, type(violin) over(rep78) theme(dark_neon)":sparkta price, type(violin) over(rep78) theme(dark_neon)}

{dlgtab:Panels}

{p 4 4 2}
Separate chart panels by foreign, grouped by repair record within each:{p_end}

{p 8 8 2}
{stata "sparkta price, over(rep78) by(foreign)":sparkta price, over(rep78) by(foreign)}{p_end}

{p 4 4 2}
Arrange panels in a grid:{p_end}

{p 8 8 2}
{stata "sparkta price, over(rep78) by(foreign) layout(grid)":sparkta price, over(rep78) by(foreign) layout(grid)}

{dlgtab:Interactive filters and sliders}

{p 8 8 2}
{stata "sparkta price, over(rep78) filters(foreign)":sparkta price, over(rep78) filters(foreign)}{p_end}
{p 8 8 2}
{stata "sparkta price, over(rep78) sliders(mpg)":sparkta price, over(rep78) sliders(mpg)}{p_end}
{p 8 8 2}
{stata "sparkta price, type(cibar) over(rep78) filters(foreign) sliders(mpg)":sparkta price, type(cibar) over(rep78) filters(foreign) sliders(mpg)}

{dlgtab:Reference lines and annotations}

{p 8 8 2}
{stata "sparkta price, over(rep78) yline(6000)":sparkta price, over(rep78) yline(6000)}{p_end}
{p 8 8 2}
{stata `"sparkta price, over(rep78) yline(4000|8000) ylinelabel(Low|High)"':{space 2}sparkta price, over(rep78) yline(4000|8000) ylinelabel(Low|High)}{p_end}
{p 8 8 2}
{stata "sparkta price, over(rep78) yband(4000 8000)":sparkta price, over(rep78) yband(4000 8000)}

{dlgtab:Themes and colors}

{p 8 8 2}
{stata "sparkta price, over(rep78) theme(dark_viridis)":sparkta price, over(rep78) theme(dark_viridis)}{p_end}
{p 8 8 2}
{stata "sparkta price, over(rep78) theme(cblind1)":sparkta price, over(rep78) theme(cblind1)}{p_end}
{p 8 8 2}
{stata `"sparkta price, over(rep78) colors(navy maroon forest_green gold%70 gs8)"':{space 2}sparkta price, over(rep78) colors(navy maroon forest_green gold%70 gs8)}{p_end}
{p 8 8 2}
{stata "sparkta price, over(rep78) palette(okabe)":sparkta price, over(rep78) palette(okabe)}

{dlgtab:Post-estimation}

{p 8 8 2}
{stata "regress price mpg weight foreign":regress price mpg weight foreign}{p_end}
{p 8 8 2}
{stata "sparkta, type(coefplot) nocons levels(90 95)":sparkta, type(coefplot) nocons levels(90 95)}{p_end}
{p 8 8 2}
{stata "sparkta, type(coefplot) cistyle(band) connected coefsort(abs) tstat":sparkta, type(coefplot) cistyle(band) connected coefsort(abs) tstat}{p_end}
{p 8 8 2}
{stata "regress price i.rep78 mpg":regress price i.rep78 mpg}{p_end}
{p 8 8 2}
{stata "margins rep78":margins rep78}{p_end}
{p 8 8 2}
{stata "sparkta, type(marginsplot) cistyle(area whisker) connected":sparkta, type(marginsplot) cistyle(area whisker) connected}{p_end}
{p 8 8 2}
{stata "regress price i.foreign##c.mpg":regress price i.foreign##c.mpg}{p_end}
{p 8 8 2}
{stata "margins foreign, at(mpg=(15 20 25 30 35))":margins foreign, at(mpg=(15 20 25 30 35))}{p_end}
{p 8 8 2}
{stata "sparkta, type(marginsplot) over(foreign) cistyle(area)":sparkta, type(marginsplot) over(foreign) cistyle(area)}{p_end}

{dlgtab:Export to files}

{p 8 8 2}
{stata `"sparkta price, type(cibar) over(rep78) export("prices.html")"':{space 2}sparkta price, type(cibar) over(rep78) export("prices.html")}{p_end}
{p 8 8 2}
{cmd:sparkta price, type(cibar) over(rep78) export(prices.html) saveas(prices.png prices.pdf)}

{marker stats_panel}{...}
{title:Summary statistics panel}

{p 4 4 2}
Every chart includes a collapsible statistics panel showing N, mean, median,
min, max, SD, and CV per group, plus a sparkline of the distribution. The
panel updates automatically whenever a filter or slider changes.{p_end}

{p 4 4 2}
Statistics match Stata's {cmd:summarize} command exactly: mean uses
sum/N, SD uses Bessel's correction (divides by N-1), median/Q1/Q3 use
Stata's exact interpolation formula.{p_end}

{p 4 4 2}
Suppress with {cmd:nostats}; start it collapsed with {cmd:collapsestats} (post-estimation
pages do so by default). For large datasets, sparklines are rendered lazily on scroll.

{marker offline}{...}
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

{marker stored}{...}
{title:Stored results}

{p 4 4 2}
{cmd:sparkta} stores nothing in {cmd:e()} or {cmd:r()}. It is a display command
that produces files as a side effect. {cmd:e()} survives the call; {cmd:r()} does not
(margins results are restored for {cmd:type(marginsplot)} only). {cmd:sparkta, findbrowser}
sets {cmd:global SPARKTA_BROWSER_FOUND}.

{marker mistakes}{...}
{title:Common mistakes}

{p 4 4 2}
The errors that trip up most new users.{p_end}

{p 4 8 4}
{bf:1. Scatter x/y reversed.}
{cmd:sparkta mpg price, type(scatter)} puts mpg on the y-axis and price on x.
If you want price on y: {cmd:sparkta price mpg, type(scatter)}.{p_end}
{pmore}
{bf:Rule: y comes first, then x.}{p_end}

{p 4 8 4}
{bf:2. Pie chart with one variable and no over().}
{cmd:sparkta price, type(pie)} will error: one variable has nothing to slice. Either
give several variables ({cmd:sparkta price mpg, type(pie)}), one variable plus
{cmd:over()}, or {cmd:over()} alone for frequency counts.{p_end}

{p 4 8 4}
{bf:3. histogram with over().}
{cmd:sparkta price, type(histogram) over(rep78)} is not supported.
For separate histograms per group, use {cmd:by()}:
{cmd:sparkta price, type(histogram) by(foreign)}.{p_end}

{p 4 8 4}
{bf:4. Combining over() and by() with pie or donut.}
{cmd:over()} and {cmd:by()} work together for most chart types -- for example,
{cmd:sparkta price, over(rep78) by(foreign)} creates one panel per origin,
each showing grouped bars by repair record. The exception is {cmd:pie} and
{cmd:donut}: {cmd:over()} already defines the slices, so {cmd:by()} would just
repeat identical pies and is not permitted.{p_end}

{p 4 8 4}
{bf:5. Forgetting that the default stat is mean.}
{cmd:sparkta price, over(rep78)} shows {bf:mean} price per group -- not sum or count.
Add {cmd:stat(sum)}, {cmd:stat(count)}, or {cmd:stat(median)} to change this.{p_end}

{p 4 8 4}
{bf:6. Colours are space-separated, not pipe-separated, in colors().}
{cmd:colors(navy maroon)} -- Stata names, hex, CSS names, {cmd:gs8}, {cmd:%50} opacity
and {cmd:*0.6} intensity all work; see {helpb sparkta##colours:Colour grammar}. The
per-entry options ({cmd:cicolors()}, {cmd:ylinecolor()}) are pipe-separated.{p_end}

{p 4 8 4}
{bf:7. A varlist with a post-estimation type.}
{cmd:sparkta price, type(coefplot)} is an error: coefplot, marginsplot and eventstudy
read stored results, never the data. Write {cmd:sparkta, type(coefplot)}.{p_end}

{p 4 8 4}
{bf:8. Expecting r() to survive.}
A sparkta call clears {cmd:r()} (except the margins results it restores for
{cmd:type(marginsplot)}). Save {cmd:r(table)} to a matrix first if you need it after the chart.{p_end}

{p 4 8 4}
{bf:9. A rebuilt jar and a version mismatch.}
Stata keeps one Java session; after updating sparkta, restart Stata. If the ado and jar
versions differ the command stops with {it:ado/JAR version mismatch} -- reinstall all
files together and restart.

{marker limits}{...}
{title:Known limitations}

{p 4 4 2}
{bf:Stata 17 or later is required.} Running sparkta on Stata 16 or earlier
will produce an error before any chart is built.{p_end}

{p 4 4 2}
Pie and donut charts take several variables, or one variable with {cmd:over()}, or {cmd:over()} alone (counts).{p_end}

{p 4 4 2}
Bubble charts require exactly three variables: y, x, and size (in that order).{p_end}

{p 4 4 2}
{cmd:over()} and {cmd:by()} cannot be used together with {cmd:pie} or {cmd:donut}.
For all other chart types they can be combined: {cmd:by()} creates panels and
{cmd:over()} groups series within each panel.{p_end}

{p 4 4 2}
{cmd:histogram} does not support {cmd:over()}. Use {cmd:by()} for separate
histograms per group.{p_end}

{p 4 4 2}
{cmd:cibar} and {cmd:ciline} require {cmd:over()}. Groups with fewer than
2 observations are omitted from CI charts.{p_end}

{p 4 4 2}
{cmd:ytype(logarithmic)} is incompatible with {cmd:ystart(zero)}.{p_end}

{p 4 4 2}
{cmd:set java_heapmax} requires a Stata restart to take effect.{p_end}

{p 4 4 2}
{cmd:by()} takes one variable. {cmd:by()}, {cmd:filters()} and {cmd:sliders()} do not apply to post-estimation types.{p_end}

{p 4 4 2}
{cmd:xtype(time)} falls back to a category axis (no date adapter is embedded). Format
dates as labels, or plot the numeric date on a linear axis.{p_end}

{p 4 4 2}
{cmd:saveas()} needs Microsoft Edge or Google Chrome; there is no EPS writer (use PDF or SVG).{p_end}

{p 4 4 2}
Pages with {cmd:online} need an internet connection to display; the default self-contained
page does not.

{marker memory}{...}
{title:Memory and large datasets}

{p 4 4 2}
{cmd:sparkta} reads observations directly from Stata memory and is not
limited by macro string length. Practical limits depend on available Java
heap memory. Default heap sizes and suggested fixes:

{p2colset 8 28 28 2}
{p2col:{it:Stata version}}{it:Default heap  --  Suggested fix}{p_end}
{p2line}
{p2col:Stata 17{hline 1}18}512 MB  {cmd:-->}  {cmd:set java_heapmax 1024m}{p_end}
{p2col:Stata 19+}4,096 MB  {cmd:-->}  Rarely needed; try {cmd:set java_heapmax 8192m}{p_end}
{p2colreset}

{p 4 4 2}
Restart Stata after changing {cmd:java_heapmax}.
Check current heap with {cmd:query java}.
Approximate dataset size guide: up to ~100K observations is comfortable on
default settings; up to ~500K is achievable with the default Stata 19+ heap.

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

{marker methods}{...}
{title:Statistical methods}

{p 4 4 2}
All statistics match Stata's {helpb summarize} output exactly.
Both the Java rendering layer and the JavaScript engine embedded in each
HTML file use identical formulas, so the statistics panel always matches
what you see in Stata.{p_end}

{p 4 4 2}
{bf:N, Mean, SD, CV.}
N is the count of non-missing observations. Mean is sum/N. SD uses
Bessel's correction (denominator N-1). CV is |SD/Mean|, guarded against
division by zero.{p_end}

{p 4 4 2}
{bf:Median, Q1, Q3.}
Stata's interpolation formula: position h = (n+1)*p/100. When h is
non-integer, the result is linearly interpolated between the floor(h) and
ceil(h) order statistics, clamped to [1, n]. Matches {cmd:summarize, detail}.{p_end}

{p 4 4 2}
{bf:Confidence intervals ({cmd:cibar}, {cmd:ciline}).}
Mean +/- t* x (SD/sqrt(N)), where t* uses the t-distribution with N-1
degrees of freedom at the level set by {cmd:cilevel()} (default 95%).
Groups with N < 2 are omitted. Matches Stata's {helpb ci means}.
The t-critical value is computed exactly (regularised incomplete beta, the same
algorithm in Java and in the page's JavaScript) for any level and df, matching
Stata's {cmd:invttail(df, (1-level/100)/2)} to about 1e-7.{p_end}

{p 4 4 2}
{bf:Histogram binning.}
Stata's {cmd:histogram} default: k = min(sqrt(N), 10*ln(N)/ln(10)), rounded down.
Override with {cmd:bins(k)}.{p_end}

{p 4 4 2}
{bf:Violin kernel density.}
Computed by sparkta (Java on first render, JavaScript after a filter change) with a
Gaussian kernel and Stata's {cmd:kdensity} default bandwidth
h = 0.9 * min(SD, IQR/1.349) * n^(-1/5). {cmd:bandwidth(h)} sets h directly.{p_end}

{pmore}
Silverman, B.W. (1986). {it:Density Estimation for Statistics and Data Analysis}.
Chapman and Hall, London.{p_end}

{p 4 4 2}
{bf:Box plot whiskers.}
Most extreme observation within k x IQR of the box edges (IQR = Q3 - Q1).
Default k = 1.5 (Tukey fences). Override with {cmd:whiskerfence(k)}.
Observations beyond the fences are drawn as outlier dots.{p_end}

{pmore}
Tukey, J.W. (1977). {it:Exploratory Data Analysis}. Addison-Wesley.{p_end}

{p 4 4 2}
{bf:Post-estimation charts.}
coefplot, marginsplot and eventstudy draw the numbers Stata stored: e(b)/e(V) or
r(table) after estimation and margins, or the matrix/dataset you name. Intervals
from {cmd:se()} use z, or t when {cmd:df()} is given. Nothing is re-estimated; the
numbers on the page and in the table are Stata's, and {cmd:eform}/{cmd:rescale()} are
applied to estimate and bounds alike (t/z statistics stay on the original scale).{p_end}

{p 4 4 2}
{bf:Fit lines ({cmd:fit()}).}
{cmd:lfit}, {cmd:qfit}: OLS.
{cmd:exp} (y=ae^bx), {cmd:log} (y=a+b*ln(x)), {cmd:power} (y=ax^b):
OLS after log linearisation. CI for exp/power is back-transformed from
the log scale (asymmetric bands wider above the fit line).
{cmd:lowess}: locally weighted regression, tricube weights, f=0.8,
matching Stata's {helpb lowess}. {cmd:ma}: 5-point moving average.{p_end}

{p 4 4 2}
{bf:Third-party plugins (all MIT licensed).}

{p2colset 8 52 52 2}
{p2col:{cmd:Chart.js 4.4.0}}{browse "https://github.com/chartjs/Chart.js":Core rendering engine}{p_end}
{p2col:{cmd:@sgratzl/chartjs-chart-boxplot 4.4.5}}{browse "https://github.com/sgratzl/chartjs-chart-boxplot":Box and violin charts}{p_end}
{p2col:{cmd:chartjs-chart-error-bars 4.4.0}}{browse "https://github.com/sgratzl/chartjs-chart-error-bars":CI error bars}{p_end}
{p2col:{cmd:chartjs-plugin-datalabels 2.2.0}}{browse "https://github.com/chartjs/chartjs-plugin-datalabels":Value label overlays}{p_end}
{p2col:{cmd:chartjs-plugin-annotation 3.0.1}}{browse "https://github.com/chartjs/chartjs-plugin-annotation":Reference lines, bands, ellipses}{p_end}
{p2colreset}

{marker authors}{...}
{title:Authors}

{p 4 4 2}
Fahad Mirza{break}
GitHub: {browse "https://github.com/fahad-mirza/sparkta_stata":github.com/fahad-mirza/sparkta_stata}{p_end}

{p 4 4 2}
Claude (Anthropic){break}
AI assistant and co-developer. Chart engine architecture, JavaScript filtering
engine, statistical methods implementation, Java/Stata integration, and
iterative debugging across all versions.
{browse "https://www.anthropic.com":anthropic.com}

{marker also_see}{...}
{title:Also see}

{p 4 4 2}
Online: {browse "https://github.com/fahad-mirza/sparkta_stata":github.com/fahad-mirza/sparkta_stata}{break}
Install or update:
{stata `"net install sparkta, from("https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/") replace"':net install sparkta, replace}
(then restart Stata){break}
Related Stata commands: {helpb graph bar}, {helpb graph twoway}, {helpb marginsplot}, {helpb margins}, {helpb estimates}; from SSC: {helpb coefplot},
{helpb colorpalette}, {helpb estout}
