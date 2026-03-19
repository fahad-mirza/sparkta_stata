{smcl}
{* sparkta.sthlp  v3.5.111  2026-03-19}{...}
{hline}
help for {cmd:sparkta}
{hline}

{p 4 4 2}
{bf:sparkta} {hline 2} Interactive self-contained HTML charts from Stata{break}
{browse "https://github.com/fahad-mirza/sparkta_stata":(View online documentation and examples)}

{marker syntax}{...}
{title:Syntax}

{p 8 16 2}
{cmd:sparkta} {varlist} {ifin} [{cmd:,} {it:options}]{p_end}

{p 4 4 2}
where {it:options} include:

{p2colset 8 52 52 2}
{p2col:{it:Option}}{it:Description}{p_end}
{p2line}
{p2col:{helpb sparkta##grouping:over(varname)}}group chart by categorical variable{p_end}
{p2col:{helpb sparkta##grouping:by(varname)}}separate panel per group{p_end}
{p2col:{helpb sparkta##grouping:filters(varlist)}}add interactive filter dropdowns{p_end}
{p2col:{helpb sparkta##grouping:sliders(varlist)}}add dual-handle range sliders{p_end}
{p2col:{helpb sparkta##types:type(charttype)}}chart type (default {cmd:bar}){p_end}
{p2col:{helpb sparkta##stat:stat(string)}}statistic to plot (default {cmd:mean}){p_end}
{p2col:{helpb sparkta##export:export(filepath)}}save HTML to file{p_end}
{p2col:{helpb sparkta##export:offline}}bundle JS inside HTML (no CDN needed){p_end}
{p2col:{helpb sparkta##axes:xtitle(s)} {it:...}}axis titles, ranges, scales, ticks{p_end}
{p2col:{helpb sparkta##appearance:title(s)} {it:...}}titles, colors, themes, fonts{p_end}
{p2col:{helpb sparkta##annotations:yline(v)} {it:...}}reference lines, bands, points, ellipses{p_end}
{p2col:{helpb sparkta##scatter_opts:fit(type)}}scatter fit line: lfit qfit lowess exp log power ma{p_end}
{p2col:{helpb sparkta##dist_opts:bins(#)} {it:...}}histogram, box plot, and violin options{p_end}
{p2col:{helpb sparkta##ci_opts:cilevel(#)} {it:...}}confidence interval options{p_end}
{p2colreset}

{p 4 4 2}
For a complete option list see {helpb sparkta##options:Options}.

{marker description}{...}
{title:Description}

{p 4 4 2}
{cmd:sparkta} converts Stata data into a self-contained interactive HTML
chart with one command. The {cmd:.html} file opens in any browser, requires
no server and no software on the recipient's end -- email it and the
interactivity travels with it.{p_end}

{p 4 4 2}
Every chart includes a collapsible statistics panel (N, mean, median, SD,
min, max, CV, sparklines) that updates live when a filter or slider changes.{p_end}

{p 4 4 2}
{bf:Requirements:} Stata 17+. Place {cmd:sparkta.jar} in your Stata
personal ado folder or working directory.

{marker examples_quick}{...}
{title:Quick start}

{p 4 4 2}
All examples use Stata's built-in {cmd:auto} dataset.{p_end}

{p 8 8 2}
{stata "sysuse auto, clear":sysuse auto, clear}

{p2colset 8 56 56 2}
{p2col:{stata "sparkta price, over(rep78)":sparkta price, over(rep78)}}mean price by repair record{p_end}
{p2col:{stata "sparkta price mpg, type(scatter)":sparkta price mpg, type(scatter)}}scatter plot{p_end}
{p2col:{stata "sparkta price, type(cibar) over(rep78)":sparkta price, type(cibar) over(rep78)}}CI bar chart{p_end}
{p2col:{stata "sparkta price, over(rep78) filters(foreign)":sparkta price, over(rep78) filters(foreign)}}with live filter{p_end}
{p2col:{stata "sparkta price, over(rep78) sliders(mpg)":sparkta price, over(rep78) sliders(mpg)}}with range slider{p_end}
{p2col:{stata "sparkta price, type(violin) over(rep78) theme(dark_neon)":sparkta price, type(violin) over(rep78) theme(dark_neon)}}violin, dark theme{p_end}
{p2col:{stata "sparkta price mpg, type(scatter) fit(lowess) fitci":sparkta price mpg, type(scatter) fit(lowess) fitci}}fit line + CI band{p_end}
{p2colreset}

{p 4 4 2}
For more examples see {helpb sparkta##examples:Examples}.

{marker also_see_quick}{...}
{title:Contents}

{p2colset 8 60 60 2}
{p2col:{helpb sparkta##varlist:Varlist}}what goes in varlist by chart type{p_end}
{p2col:{helpb sparkta##types:Chart types}}all 20+ types with descriptions{p_end}
{p2col:{helpb sparkta##themes:Themes}}background and color palettes{p_end}
{p2col:{helpb sparkta##options:Options}}complete option reference{p_end}
{p2col:{helpb sparkta##examples:Examples}}runnable examples by category{p_end}
{p2col:{helpb sparkta##stats_panel:Statistics panel}}what the panel shows and how{p_end}
{p2col:{helpb sparkta##offline:Offline mode}}bundled HTML for air-gapped use{p_end}
{p2col:{helpb sparkta##methods:Statistical methods}}formulas, references, plugins{p_end}
{p2col:{helpb sparkta##mistakes:Common mistakes}}errors most new users make{p_end}
{p2col:{helpb sparkta##limits:Known limitations}}chart type restrictions{p_end}
{p2col:{helpb sparkta##memory:Memory and large datasets}}{cmd:java_heapmax} settings{p_end}
{p2colreset}

{hline}

{marker varlist}{...}
{title:Varlist}

{p 4 4 2}
The variable(s) before the comma are what gets measured.

{p2colset 8 36 36 2}
{p2col:{bf:bar, hbar, stacked*}}one or more numeric variables; {cmd:stat()} applied per group{p_end}
{p2col:{bf:line, area, stacked*}}one or more numeric variables plotted as series{p_end}
{p2col:{bf:scatter}}{bf:y first, then x}: {cmd:sparkta price mpg, type(scatter)}{p_end}
{p2col:{bf:bubble}}{bf:y, x, size}: {cmd:sparkta price mpg weight, type(bubble)}{p_end}
{p2col:{bf:histogram}}exactly one numeric variable{p_end}
{p2col:{bf:boxplot, hbox}}one or more numeric variables{p_end}
{p2col:{bf:violin, hviolin}}one numeric variable; {cmd:over()} for groups{p_end}
{p2col:{bf:cibar, ciline}}one numeric variable; {cmd:over()} required{p_end}
{p2col:{bf:pie, donut}}one variable (slice sizes), or omit for frequency counts{p_end}
{p2colreset}

{p 4 4 2}
{bf:over() vs by() vs filters():}
{cmd:over()} puts all groups on {it:one chart}.
{cmd:by()} creates {it:separate panels}.
{cmd:filters()} lets the {it:viewer} switch groups without re-running Stata.
Combine {cmd:over()} and {cmd:by()}: {cmd:by()} makes panels, {cmd:over()} groups series inside each.

{marker types}{...}
{title:Chart types}

{p 4 4 2}
Specify with {cmd:type(}{it:charttype}{cmd:)}. Default is {cmd:bar}.

{p2colset 6 36 36 2}
{p2col:{bf:Bar / column}}  {p_end}
{p2col:{cmd: bar}}vertical bars -- mean (or chosen stat) per group{p_end}
{p2col:{cmd: hbar}}horizontal bars{p_end}
{p2col:{cmd: stackedbar}}stacked vertical bars{p_end}
{p2col:{cmd: stackedhbar}}stacked horizontal bars{p_end}
{p2col:{cmd: stackedbar100}}100% stacked bars{p_end}
{p2col:{cmd: stackedhbar100}}100% stacked horizontal{p_end}

{p2col:{bf:Line / area}}  {p_end}
{p2col:{cmd: line}}line chart{p_end}
{p2col:{cmd: area}}filled area{p_end}
{p2col:{cmd: stackedline}}stacked lines{p_end}
{p2col:{cmd: stackedarea}}stacked areas{p_end}

{p2col:{bf:Distributions}}  {p_end}
{p2col:{cmd: histogram}}frequency/density histogram{p_end}
{p2col:{cmd: boxplot}}standard box plot{p_end}
{p2col:{cmd: hbox}}horizontal box plot{p_end}
{p2col:{cmd: violin}}violin plot{p_end}
{p2col:{cmd: hviolin}}horizontal violin{p_end}

{p2col:{bf:Relationships}}  {p_end}
{p2col:{cmd: scatter}}scatter plot; add {cmd:fit()} for a fitted line{p_end}
{p2col:{cmd: bubble}}bubble chart; third variable sets bubble size{p_end}

{p2col:{bf:Proportions}}  {p_end}
{p2col:{cmd: pie}}pie chart{p_end}
{p2col:{cmd: donut}}donut chart{p_end}

{p2col:{bf:Statistical CI charts}}  {p_end}
{p2col:{cmd: cibar}}bars with 95% CI error bars; requires {cmd:over()}{p_end}
{p2col:{cmd: ciline}}line with 95% CI band; requires {cmd:over()}{p_end}
{p2colreset}

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
{cmd:colors()} always overrides any palette.
Use {bf:spaces} to separate colors: {cmd:colors(#e41a1c #377eb8 #4daf4a)}

{marker options}{...}
{title:Options}

{p 4 4 2}
Click any option group to jump to its documentation.
Chart-type-specific options are grouped under the chart type.

{p2colset 8 60 60 2}
{p2col:{it:Option group}}{it:Key options}{p_end}
{p2line}
{p2col:{bf:Applied to all chart types}}{p_end}
{p2col:{helpb sparkta##grouping:  Grouping and panels}}{cmd:over()} {cmd:by()} {cmd:filters()} {cmd:sliders()} {cmd:layout()}{p_end}
{p2col:{helpb sparkta##stat:  Aggregation}}{cmd:stat()} {cmd:sortgroups()} {cmd:nomissing} {cmd:showmissing} {cmd:nostats}{p_end}
{p2col:{helpb sparkta##axes:  Axes}}{cmd:xtitle()} {cmd:ytitle()} {cmd:xrange()} {cmd:yrange()} {cmd:xtype()} {cmd:y2()}{p_end}
{p2col:{helpb sparkta##appearance:  Appearance}}{cmd:title()} {cmd:theme()} {cmd:colors()} {cmd:opacity()} {cmd:gradient}{p_end}
{p2col:{helpb sparkta##annotations:  Ref. lines and annotations}}{cmd:yline()} {cmd:xline()} {cmd:yband()} {cmd:apoint()}{p_end}
{p2col:{helpb sparkta##export:  Export and data handling}}{cmd:export()} {cmd:offline} {cmd:download} {cmd:novaluelabels}{p_end}

{p2col:{bf:Bar and line charts}}{p_end}
{p2col:{helpb sparkta##types:  bar hbar stackedbar*}}see {helpb sparkta##appearance:Appearance}, {helpb sparkta##axes:Axes}{p_end}
{p2col:{helpb sparkta##dist_opts:  Bar appearance}}{cmd:barwidth()} {cmd:bargroupwidth()} {cmd:borderradius()} {cmd:horizontal} {cmd:stacked}{p_end}
{p2col:{helpb sparkta##dist_opts:  Line and area}}{cmd:linewidth()} {cmd:lpattern()} {cmd:smooth()} {cmd:fill} {cmd:areaopacity()} {cmd:spanmissing}{p_end}

{p2col:{bf:Scatter and bubble}}{p_end}
{p2col:{helpb sparkta##scatter_opts:  scatter bubble}}fit lines, marker labels, point styling{p_end}
{p2col:{helpb sparkta##scatter_opts:  Fit lines}}{cmd:fit(lfit|qfit|lowess|exp|log|power|ma)} {cmd:fitci}{p_end}
{p2col:{helpb sparkta##scatter_opts:  Marker labels}}{cmd:mlabel()} {cmd:mlabpos()} {cmd:mlabvposition()}{p_end}
{p2col:{helpb sparkta##dist_opts:  Point styling}}{cmd:pointsize()} {cmd:pointstyle()} {cmd:nopoints} {cmd:pointhoversize()}{p_end}

{p2col:{bf:CI charts}}{p_end}
{p2col:{helpb sparkta##ci_opts:  cibar ciline}}{cmd:cilevel()} {cmd:cibandopacity()} -- {cmd:over()} required{p_end}

{p2col:{bf:Histogram}}{p_end}
{p2col:{helpb sparkta##dist_opts:  histogram}}{cmd:bins()} {cmd:histtype(frequency|density|fraction)}{p_end}

{p2col:{bf:Box and violin}}{p_end}
{p2col:{helpb sparkta##dist_opts:  boxplot violin hbox hviolin}}{cmd:whiskerfence()} {cmd:bandwidth()} {cmd:mediancolor()} {cmd:meancolor()}{p_end}

{p2col:{bf:Pie and donut}}{p_end}
{p2col:{helpb sparkta##types:  pie donut}}{cmd:cutout()} {cmd:rotation()} {cmd:circumference()} {cmd:pielabels} {cmd:sliceborder()}{p_end}
{p2colreset}

{p 4 4 2}
{bf:Complete syntax reference (all 149 options):}

{synoptset 32 tabbed}{...}
{synopthdr}{p_end}
{synoptline}
{syntab:Essential}
{synopt:{cmd:type(}{it:charttype}{cmd:)}}chart type; see {helpb sparkta##types:Chart types}{p_end}
{synopt:{cmd:over(}{it:varname}{cmd:)}}group by variable, all groups on one chart{p_end}
{synopt:{cmd:by(}{it:varname}{cmd:)}}separate panel per group{p_end}
{synopt:{cmd:filters(}{it:varlist}{cmd:)}}add live interactive filter dropdowns{p_end}
{synopt:{cmd:sliders(}{it:varlist}{cmd:)}}add dual-handle range sliders{p_end}
{synopt:{cmd:title(}{it:string}{cmd:)}}chart title{p_end}
{synopt:{cmd:export(}{it:path}{cmd:)}}save HTML to file instead of opening browser{p_end}
{synopt:{cmd:offline}}bundle all JS inside the HTML for air-gap use{p_end}

{syntab:Grouping / panels}
{synopt:{cmd:layout(}{it:string}{cmd:)}}panel layout: {cmd:vertical} | {cmd:horizontal} | {cmd:grid}{p_end}
{synopt:{cmd:sortgroups(}{it:string}{cmd:)}}group order: {cmd:asc} | {cmd:desc}{p_end}
{synopt:{cmd:nomissing}}exclude missing values from grouping variables{p_end}
{synopt:{cmd:showmissing}}include (Missing) as an explicit group{p_end}
{synopt:{cmd:nostats}}suppress the summary statistics panel{p_end}

{syntab:Axes}
{synopt:{cmd:xtitle(}{it:string}{cmd:)}}x-axis title{p_end}
{synopt:{cmd:ytitle(}{it:string}{cmd:)}}y-axis title{p_end}
{synopt:{cmd:xrange(}{it:min max}{cmd:)}}x-axis min and max{p_end}
{synopt:{cmd:yrange(}{it:min max}{cmd:)}}y-axis min and max{p_end}
{synopt:{cmd:xtype(}{it:string}{cmd:)}}scale: {cmd:linear} | {cmd:log} | {cmd:category} | {cmd:time}{p_end}
{synopt:{cmd:ytype(}{it:string}{cmd:)}}scale: {cmd:linear} | {cmd:logarithmic}{p_end}
{synopt:{cmd:yreverse}}reverse y-axis direction{p_end}
{synopt:{cmd:xreverse}}reverse x-axis direction{p_end}
{synopt:{cmd:noticks}}hide tick marks{p_end}
{synopt:{cmd:ygrace(}{it:#}{cmd:)}}proportional padding above y-max (e.g. {cmd:0.1} = 10%){p_end}
{synopt:{cmd:xticks(}{it:list}{cmd:)}}pin x tick positions, pipe-sep{p_end}
{synopt:{cmd:yticks(}{it:list}{cmd:)}}pin y tick positions, pipe-sep{p_end}
{synopt:{cmd:xlabels(}{it:list}{cmd:)}}custom x tick labels, pipe-sep{p_end}
{synopt:{cmd:ylabels(}{it:list}{cmd:)}}custom y tick labels, pipe-sep{p_end}
{synopt:{cmd:xtickcount(}{it:#}{cmd:)}}x-axis tick count{p_end}
{synopt:{cmd:ytickcount(}{it:#}{cmd:)}}y-axis tick count{p_end}
{synopt:{cmd:xtickangle(}{it:#}{cmd:)}}x tick label rotation degrees{p_end}
{synopt:{cmd:ytickangle(}{it:#}{cmd:)}}y tick label rotation degrees{p_end}
{synopt:{cmd:y2(}{it:varlist}{cmd:)}}secondary y-axis variables{p_end}

{syntab:Chart appearance}
{synopt:{cmd:theme(}{it:string}{cmd:)}}background / palette; see {helpb sparkta##themes:Themes}{p_end}
{synopt:{cmd:colors(}{it:list}{cmd:)}}series colors, pipe-separated CSS values{p_end}
{synopt:{cmd:bgcolor(}{it:color}{cmd:)}}page background color{p_end}
{synopt:{cmd:opacity(}{it:#}{cmd:)}}fill opacity 0-1 (default 0.85){p_end}
{synopt:{cmd:gradient}}gradient fill using palette colors{p_end}
{synopt:{cmd:gradcolors(}{it:c1|c2}{cmd:)}}custom gradient start|end colors{p_end}
{synopt:{cmd:download}}add PNG download button{p_end}
{synopt:{cmd:datalabels}}show value labels on bars/slices{p_end}

{syntab:Labels / legend}
{synopt:{cmd:subtitle(}{it:string}{cmd:)}}secondary heading{p_end}
{synopt:{cmd:note(}{it:string}{cmd:)}}italic note below chart{p_end}
{synopt:{cmd:leglabels(}{it:list}{cmd:)}}rename legend entries, pipe-separated{p_end}
{synopt:{cmd:relabel(}{it:list}{cmd:)}}rename over() group labels on axis + legend{p_end}
{synopt:{cmd:legend(}{it:pos}{cmd:)}}legend position: {cmd:top}|{cmd:bottom}|{cmd:left}|{cmd:right}|{cmd:none}{p_end}
{synopt:{cmd:nolegend}}suppress legend{p_end}
{synopt:{cmd:novaluelabels}}use raw numeric codes instead of Stata value labels{p_end}

{syntab:Lines and points}
{synopt:{cmd:linewidth(}{it:#}{cmd:)}}line width px (default 2){p_end}
{synopt:{cmd:lpattern(}{it:string}{cmd:)}}line style: {cmd:solid}|{cmd:dash}|{cmd:dot}|{cmd:dashdot}{p_end}
{synopt:{cmd:lpatterns(}{it:list}{cmd:)}}per-series patterns, pipe-separated{p_end}
{synopt:{cmd:smooth(}{it:#}{cmd:)}}line smoothing 0-1{p_end}
{synopt:{cmd:nopoints}}hide point markers{p_end}
{synopt:{cmd:pointsize(}{it:#}{cmd:)}}point radius px (default 4){p_end}
{synopt:{cmd:pointstyle(}{it:string}{cmd:)}}shape: {cmd:circle}|{cmd:cross}|{cmd:rect}|{cmd:star}|{cmd:triangle}{p_end}
{synopt:{cmd:spanmissing}}connect lines across missing values{p_end}
{synopt:{cmd:stepped(}{it:string}{cmd:)}}step function: {cmd:before}|{cmd:after}|{cmd:middle}{p_end}

{syntab:Bars}
{synopt:{cmd:horizontal}}horizontal bars{p_end}
{synopt:{cmd:stacked}}stack series{p_end}
{synopt:{cmd:barwidth(}{it:#}{cmd:)}}bar width as proportion 0-1{p_end}
{synopt:{cmd:borderradius(}{it:#}{cmd:)}}bar corner radius px{p_end}

{syntab:Scatter / fit}
{synopt:{cmd:fit(}{it:type}{cmd:)}}scatter fit line: {cmd:lfit}|{cmd:qfit}|{cmd:lowess}|{cmd:exp}|{cmd:log}|{cmd:power}|{cmd:ma}{p_end}
{synopt:{cmd:fitci}}add 95% CI band to fit line (all fit types){p_end}
{synopt:{cmd:mlabel(}{it:varname}{cmd:)}}label scatter points with a variable{p_end}
{synopt:{cmd:mlabpos(}{it:#}{cmd:)}}label position 0-59 (minute-clock; default=above point, 15=right, 30=below){p_end}
{synopt:{cmd:mlabvposition(}{it:var}{cmd:)}}per-observation label position variable (0-59)ariable (0-59 numeric var){p_end}

{syntab:Statistical}
{synopt:{cmd:stat(}{it:string}{cmd:)}}statistic: {cmd:mean}|{cmd:sum}|{cmd:count}|{cmd:min}|{cmd:max}|{cmd:pct}{p_end}
{synopt:{cmd:cilevel(}{it:#}{cmd:)}}any integer 1-99 (default 95; common: 90, 95, 99){p_end}
{synopt:{cmd:whiskerfence(}{it:#}{cmd:)}}IQR multiplier for box plot whiskers (default 1.5){p_end}
{synopt:{cmd:bins(}{it:#}{cmd:)}}number of histogram bins{p_end}
{synopt:{cmd:histtype(}{it:string}{cmd:)}}histogram scale: {cmd:frequency}|{cmd:density}|{cmd:fraction}{p_end}
{synopt:{cmd:bandwidth(}{it:#}{cmd:)}}violin KDE bandwidth{p_end}
{synopt:{cmd:mediancolor(}{it:color}{cmd:)}}box/violin median marker color{p_end}
{synopt:{cmd:meancolor(}{it:color}{cmd:)}}box/violin mean marker color{p_end}

{syntab:Reference lines / annotations}
{synopt:{cmd:yline(}{it:values}{cmd:)}}horizontal/vertical reference lines, pipe-separated{p_end}
{synopt:{cmd:ylinecolor(}{it:colors}{cmd:)}}yline colors, pipe-sep{p_end}
{synopt:{cmd:xlinecolor(}{it:colors}{cmd:)}}xline colors, pipe-sep{p_end}
{synopt:{cmd:ylinelabel(}{it:texts}{cmd:)}}yline labels, pipe-sep{p_end}
{synopt:{cmd:xlinelabel(}{it:texts}{cmd:)}}xline labels, pipe-sep{p_end}
{synopt:{cmd:yband(}{it:lo hi}{cmd:)}}horizontal shaded band{p_end}
{synopt:{cmd:xband(}{it:lo hi}{cmd:)}}vertical shaded band; pipe-sep for multiple{p_end}
{synopt:{cmd:ybandcolor(}{it:colors}{cmd:)}}yband fill colors, pipe-sep{p_end}
{synopt:{cmd:xbandcolor(}{it:colors}{cmd:)}}xband fill colors, pipe-sep{p_end}
{synopt:{cmd:apoint(}{it:y x ...}{cmd:)}}annotation points: space-sep y x pairs{p_end}
{synopt:{cmd:alabelpos(}{it:coords}{cmd:)}}annotation labels: y x pos (minute-clock)te-clock), pipe-sep sets{p_end}
{synopt:{cmd:alabeltext(}{it:texts}{cmd:)}}annotation label texts, pipe-separated{p_end}
{synopt:{cmd:aellipse(}{it:coords}{cmd:)}}annotation ellipses (ymin xmin ymax xmax), pipe-sep sets{p_end}

{syntab:Tooltips / styling}
{synopt:{cmd:tooltipformat(}{it:string}{cmd:)}}number format: {cmd:currency}|{cmd:percent}|{cmd:integer}|{cmd:auto}{p_end}
{synopt:{cmd:tooltipmode(}{it:string}{cmd:)}}hover mode: {cmd:point}|{cmd:index}|{cmd:nearest}{p_end}
{synopt:{cmd:tooltipbg(}{it:color}{cmd:)}}tooltip background color{p_end}
{synopt:{cmd:titlesize(}{it:string}{cmd:)}}title font size{p_end}
{synopt:{cmd:titlecolor(}{it:color}{cmd:)}}title color{p_end}
{synopt:{cmd:xlabsize(}{it:string}{cmd:)}}x tick label font size{p_end}
{synopt:{cmd:ylabsize(}{it:string}{cmd:)}}y tick label font size{p_end}
{synopt:{cmd:animduration(}{it:#}{cmd:)}}animation duration ms (default ~1000){p_end}
{synopt:{cmd:aspect(}{it:#}{cmd:)}}width/height ratio{p_end}

{syntab:Labels and text}
{synopt:{cmd:title(}{it:string}{cmd:)}}main chart heading{p_end}
{synopt:{cmd:subtitle(}{it:string}{cmd:)}}secondary heading below title{p_end}
{synopt:{cmd:note(}{it:string}{cmd:)}}italic note below chart{p_end}
{synopt:{cmd:caption(}{it:string}{cmd:)}}small caption below note{p_end}
{synopt:{cmd:notesize(}{it:string}{cmd:)}}font size for note and caption (CSS value){p_end}
{synopt:{cmd:datalabels}}show value labels on bars/slices{p_end}

{syntab:Chart behaviour}
{synopt:{cmd:type(}{it:charttype}{cmd:)}}chart type; see {helpb sparkta##types:Chart types}{p_end}
{synopt:{cmd:horizontal}}horizontal bars (alias for {cmd:type(hbar)}){p_end}
{synopt:{cmd:stacked}}stack multiple series{p_end}
{synopt:{cmd:fill}}fill area under line (alias for {cmd:type(area)}){p_end}
{synopt:{cmd:areaopacity(}{it:#}{cmd:)}}area fill opacity 0-1 (default 0.35){p_end}
{synopt:{cmd:spanmissing}}connect lines across missing values{p_end}
{synopt:{cmd:nopoints}}hide point markers on line/area charts{p_end}
{synopt:{cmd:pointborderwidth(}{it:#}{cmd:)}}point border width px (default 1){p_end}
{synopt:{cmd:pointrotation(}{it:#}{cmd:)}}point rotation degrees (default 0){p_end}
{synopt:{cmd:pointhoversize(}{it:#}{cmd:)}}point radius on hover px (default pointsize+2){p_end}
{synopt:{cmd:gradient}}gradient fill using palette colors{p_end}
{synopt:{cmd:gradcolors(}{it:c1|c2}{cmd:)}}custom gradient: start|end color{p_end}
{synopt:{cmd:download}}PNG download button in chart header{p_end}
{synopt:{cmd:fitci}}95% CI band around scatter fit line{p_end}
{synopt:{cmd:padding(}{it:#}{cmd:)}}inner chart padding px{p_end}
{synopt:{cmd:bargroupwidth(}{it:#}{cmd:)}}bar group width proportion 0-1{p_end}

{syntab:Data and missing values}
{synopt:{cmd:nostats}}suppress the summary statistics panel{p_end}
{synopt:{cmd:novaluelabels}}use raw numeric codes instead of Stata value labels{p_end}
{synopt:{cmd:nomissing}}exclude missing values from grouping variables{p_end}

{syntab:Axis lines and grid}
{synopt:{cmd:yreverse}}reverse y-axis direction{p_end}
{synopt:{cmd:xreverse}}reverse x-axis direction{p_end}
{synopt:{cmd:noticks}}hide axis tick marks (labels remain){p_end}
{synopt:{cmd:ystart(zero)}}anchor y-axis at zero{p_end}
{synopt:{cmd:yrange(}{it:min max}{cmd:)}}y-axis min and max{p_end}
{synopt:{cmd:xstepsize(}{it:#}{cmd:)}}x-axis tick interval{p_end}
{synopt:{cmd:ystepsize(}{it:#}{cmd:)}}y-axis tick interval{p_end}
{synopt:{cmd:ytickangle(}{it:#}{cmd:)}}y tick label rotation degrees{p_end}
{synopt:{cmd:ytickcount(}{it:#}{cmd:)}}approximate y tick count{p_end}
{synopt:{cmd:yticks(}{it:list}{cmd:)}}pin y tick positions, pipe-sep{p_end}
{synopt:{cmd:xgridlines(on|off)}}vertical grid lines{p_end}
{synopt:{cmd:ygridlines(on|off)}}horizontal grid lines{p_end}
{synopt:{cmd:xborder(on|off)}}x-axis border line{p_end}
{synopt:{cmd:yborder(on|off)}}y-axis border line{p_end}
{synopt:{cmd:gridcolor(}{it:color}{cmd:)}}grid line color{p_end}
{synopt:{cmd:gridopacity(}{it:#}{cmd:)}}grid line opacity 0-1 (default 0.15){p_end}
{synopt:{cmd:xlabels(}{it:list}{cmd:)}}custom x tick labels, pipe-sep{p_end}
{synopt:{cmd:ylabels(}{it:list}{cmd:)}}custom y tick labels, pipe-sep{p_end}
{synopt:{cmd:ytitle(}{it:string}{cmd:)}}y-axis label{p_end}
{synopt:{cmd:y2range(}{it:min max}{cmd:)}}secondary y-axis min and max{p_end}
{synopt:{cmd:y2title(}{it:string}{cmd:)}}secondary y-axis label{p_end}

{syntab:Reference lines (continued)}
{synopt:{cmd:xline(}{it:values}{cmd:)}}vertical reference lines, pipe-sep{p_end}
{synopt:{cmd:xlinecolor(}{it:colors}{cmd:)}}xline colors, pipe-sep{p_end}
{synopt:{cmd:xlinelabel(}{it:texts}{cmd:)}}xline labels, pipe-sep{p_end}
{synopt:{cmd:xband(}{it:lo hi}{cmd:)}}vertical shaded band; pipe-sep for multiple{p_end}
{synopt:{cmd:xbandcolor(}{it:colors}{cmd:)}}xband fill colors, pipe-sep{p_end}

{syntab:Annotation sub-options}
{synopt:{cmd:apointcolor(}{it:colors}{cmd:)}}annotation point colors, pipe-sep{p_end}
{synopt:{cmd:apointsize(}{it:#}{cmd:)}}annotation point radius px (default 8){p_end}
{synopt:{cmd:aellipsecolor(}{it:colors}{cmd:)}}ellipse fill colors, pipe-sep{p_end}
{synopt:{cmd:aellipseborder(}{it:colors}{cmd:)}}ellipse border colors, pipe-sep{p_end}
{synopt:{cmd:alabelgap(}{it:#}{cmd:)}}annotation label offset distance px (default 15){p_end}
{synopt:{cmd:alabelfs(}{it:#}{cmd:)}}annotation label font size px (default 12){p_end}

{syntab:Legend sub-options}
{synopt:{cmd:nolegend}}suppress legend entirely{p_end}
{synopt:{cmd:legtitle(}{it:string}{cmd:)}}legend title{p_end}
{synopt:{cmd:legsize(}{it:#}{cmd:)}}legend font size px (default 10){p_end}
{synopt:{cmd:legboxheight(}{it:#}{cmd:)}}legend color swatch height px{p_end}
{synopt:{cmd:legcolor(}{it:color}{cmd:)}}legend text color{p_end}
{synopt:{cmd:legbgcolor(}{it:color}{cmd:)}}legend background color{p_end}

{syntab:Colors and background}
{synopt:{cmd:bgcolor(}{it:color}{cmd:)}}page background color{p_end}
{synopt:{cmd:plotcolor(}{it:color}{cmd:)}}chart area background color{p_end}
{synopt:{cmd:theme(}{it:string}{cmd:)}}background / palette; see {helpb sparkta##themes:Themes}{p_end}

{syntab:Font styling}
{synopt:{cmd:titlecolor(}{it:color}{cmd:)}}title text color{p_end}
{synopt:{cmd:subtitlesize(}{it:string}{cmd:)}}subtitle font size{p_end}
{synopt:{cmd:subtitlecolor(}{it:color}{cmd:)}}subtitle text color{p_end}
{synopt:{cmd:xtitlesize(}{it:string}{cmd:)}}x-axis title font size{p_end}
{synopt:{cmd:xtitlecolor(}{it:color}{cmd:)}}x-axis title color{p_end}
{synopt:{cmd:ytitlesize(}{it:string}{cmd:)}}y-axis title font size{p_end}
{synopt:{cmd:ytitlecolor(}{it:color}{cmd:)}}y-axis title color{p_end}
{synopt:{cmd:xlabcolor(}{it:color}{cmd:)}}x tick label color{p_end}
{synopt:{cmd:ylabcolor(}{it:color}{cmd:)}}y tick label color{p_end}
{synopt:{cmd:xlabsize(}{it:string}{cmd:)}}x tick label font size{p_end}
{synopt:{cmd:ylabsize(}{it:string}{cmd:)}}y tick label font size{p_end}

{syntab:Tooltip sub-options}
{synopt:{cmd:tooltipborder(}{it:color}{cmd:)}}tooltip border color{p_end}
{synopt:{cmd:tooltipfontsize(}{it:#}{cmd:)}}tooltip font size px{p_end}
{synopt:{cmd:tooltippadding(}{it:#}{cmd:)}}tooltip padding px{p_end}
{synopt:{cmd:tooltipposition(}{it:string}{cmd:)}}tooltip anchor: {cmd:average}|{cmd:nearest}{p_end}

{syntab:Animation}
{synopt:{cmd:animate(}{it:string}{cmd:)}}speed: {cmd:fast}|{cmd:slow}|{cmd:none}{p_end}
{synopt:{cmd:animdelay(}{it:#}{cmd:)}}delay before animation starts (ms){p_end}
{synopt:{cmd:easing(}{it:string}{cmd:)}}animation easing function{p_end}

{syntab:Pie and donut}
{synopt:{cmd:cutout(}{it:#}{cmd:)}}donut hole size 0-99 (default 55){p_end}
{synopt:{cmd:rotation(}{it:#}{cmd:)}}starting angle degrees (default 0=top){p_end}
{synopt:{cmd:circumference(}{it:#}{cmd:)}}arc degrees drawn (default 360=full circle){p_end}
{synopt:{cmd:sliceborder(}{it:#}{cmd:)}}border width between slices px (default 1){p_end}
{synopt:{cmd:hoveroffset(}{it:#}{cmd:)}}slice pop-out on hover px (default 8){p_end}
{synopt:{cmd:pielabels}}show percentage labels on slices{p_end}
{synopt:{cmd:meancolor(}{it:color}{cmd:)}}mean line color in box/violin plots{p_end}

{syntab:Offline and CI}
{synopt:{cmd:offline}}bundle all JS inside the HTML file{p_end}
{synopt:{cmd:cibandopacity(}{it:#}{cmd:)}}CI band fill opacity 0-1 ({cmd:ciline} only){p_end}

{syntab:Export}
{synopt:{cmd:export(}{it:path}{cmd:)}}save HTML to file; path must end in {cmd:.html}{p_end}

{synoptline}

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

{p 8 8 2}{it:Example:} {cmd:sparkta price, by(rep78) layout(grid)}{p_end}

{phang}
{opt filters(varname [, showmissing])} Adds an interactive dropdown below the
chart letting viewers filter the data by values of {it:varname} without
reloading. The chart updates live. Suboption {cmd:showmissing} adds a
{bf:(Missing)} option for observations where {it:varname} is missing.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) filter(foreign)} -- viewer can toggle between Domestic and Foreign without re-running Stata.{p_end}

{phang}
{opt filters(varname [, showmissing])} Second independent interactive filter
dropdown. Requires {cmd:filter()} to also be specified. Supports {cmd:showmissing}.

{p 8 8 2}{it:Example:} {cmd:sparkta price weight, over(rep78) filter(foreign) filter2(rep78)}{p_end}

{phang}
{opt sortgroups(string)} Controls the order of {cmd:over()} and {cmd:by()}
group labels. Options: {cmd:asc} | {cmd:desc}.
When omitted, groups are sorted ascending (numeric labels sort numerically;
string labels sort alphabetically). Filter dropdown options are always
sorted ascending and are unaffected.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) sortgroups(desc)} -- highest repair-record group shown first.{p_end}

{phang}
{opt nostats} Suppresses the summary statistics panel below the chart.

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
The CI formula is mean +/- t* x (SD/sqrt(N)) where t* uses the
t-distribution with N-1 degrees of freedom.{p_end}

{p 8 8 2}
Confidence intervals are computed as mean +/- t * SE where SE = SD / sqrt(n)
and t is the two-tailed t-critical value with n-1 degrees of freedom.
Groups with fewer than 2 observations are omitted.
For df > 120 the standard normal z-critical value is used.{p_end}

{phang}
{opt cibandopacity(#)} Opacity of the CI shaded band for {cmd:ciline} charts.
Default is {cmd:0.18}. Range 0{hline 1}1.
Example: {cmd:cibandopacity(0.05)} for a faint band; {cmd:cibandopacity(0.35)}
for a more prominent band.

{marker dist_opts}{...}
{dlgtab:Histogram}

{phang}
{opt bins(#)} Number of bins. Must be an integer of 2 or greater. If omitted,
bins are determined by Sturges' rule: {cmd:ceil(log2(n) + 1)}, clamped to [5, 50].
Start with the default; fewer bins reveal broad shape, more reveal fine structure.

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
If omitted, Silverman's rule of thumb is applied automatically:
{it:h} = 0.9 * min(SD, IQR/1.34) * n^(-1/5).{p_end}

{p 8 8 2}
Example: {cmd:bandwidth(2000)} for a price variable measured in dollars.{p_end}

{p 8 8 2}
{bf:Legend.} Both chart types display an inline canvas legend in the top-right
corner of the chart area. For {cmd:boxplot}: Median, Mean, IQR Box, Whiskers,
and Outlier symbols. For {cmd:violin}: Median, Mean, IQR Box, Whiskers, and
KDE Shape symbols.{p_end}

{p 8 8 2}
{bf:Violin animation.} Violin charts animate on load (shapes grow in from flat)
and on filter change (shapes tween smoothly to the new distribution). This
animation is driven by a custom requestAnimationFrame loop and is not affected
by the {cmd:animate()} option.{p_end}

{p 8 8 2}
{bf:Statistical formulas.} All statistics (Q1, Q3, median, mean, whiskers)
match Stata {cmd:summarize, detail} output exactly, using the formula
h = (n+1)*p/100 with linear interpolation between adjacent order statistics.

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
{p 12 12 2}{cmd:sparkta price, over(rep78) yband(4000 8000|10000 14000) ybandcolor(rgba(0,200,0,0.1)|rgba(200,0,0,0.1))} -- two differently colored bands{p_end}

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
Format: space-separated y x pairs, following Stata scattteri convention.
Example: {cmd:apoint(15000 25 20000 30)} places markers at (y=15000, x=25)
and (y=20000, x=30). Note: y comes before x, matching Stata {cmd:scattteri} convention.

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
{bf:Requires.} The annotation plugin ({cmd:chartjs-plugin-annotation@3.0.1})
is loaded from CDN automatically when any annotation option is specified.
For offline use, the plugin is bundled in {cmd:sparkta.jar}.

{dlgtab:Offline mode}

{phang}
{opt offline} Embed all JavaScript libraries directly inside the HTML file
so the output requires no internet connection to open.{p_end}

{p 8 8 2}
{bf:How it works.} By default, {cmd:sparkta} generates a compact HTML file
that loads Chart.js from a CDN when opened in a browser. With {cmd:offline},
the entire Chart.js library and all plugins are embedded inside the HTML at
generation time. The result is a single file that renders correctly on any
machine, anywhere, with no network request of any kind.{p_end}

{p 8 8 2}
{bf:Data privacy and confidentiality.} When your chart contains sensitive or
restricted data, the {cmd:offline} option gives you full control. No data ever
leaves the HTML file, no CDN is contacted when the file opens, and the chart
can be shared, archived, or presented on a machine with no internet access.
Particularly recommended for clinical, financial, or institutional data where
external network requests must be avoided.{p_end}

{p 8 8 2}
{bf:Reproducibility.} An offline HTML file is a permanent, self-contained
snapshot of both the chart rendering engine and the data at the time of
generation. It will render identically in any browser years from now,
independent of CDN availability, library version changes, or network conditions.
Suitable for inclusion in reproducibility archives and supplementary materials.{p_end}

{p 8 8 2}
{bf:File size.} The offline HTML is larger (~250{hline 1}320 KB vs ~50 KB
for an online file) but functionally identical in all other respects.{p_end}

{p 8 8 2}
{bf:Requirement.} The JS libraries must be bundled in {cmd:sparkta.jar}
at compile time. Run {cmd:fetch_js_libs.sh} (Unix/Mac) or
{cmd:fetch_js_libs.bat} (Windows) from the {cmd:java/} folder, then
recompile. See the repository README for step-by-step instructions.


{marker axes}{...}
{dlgtab:Axes}

{phang}
{opt xtitle(string)} Label for the x-axis.{p_end}

{phang}
{opt ytitle(string)} Label for the y-axis.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) xtitle(Mileage (mpg)) ytitle(Price (USD))}{p_end}

{phang}
{opt xrange(min max)} X-axis minimum and maximum. Example: {cmd:xrange(0 100)}.{p_end}

{phang}
{opt yrange(min max)} Y-axis minimum and maximum. Example: {cmd:yrange(0 10000)}.{p_end}

{phang}
{opt ystart(zero)} Force the y-axis to begin at zero.
The only accepted value is {cmd:zero}. Incompatible with {cmd:ytype(logarithmic)}.
Useful when Chart.js auto-scales to a non-zero minimum, making differences look larger than they are.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) ystart(zero)}{p_end}

{phang}
{opt xtype(string)} X-axis scale type. Options: {cmd:linear} (default) |
{cmd:logarithmic} | {cmd:category} | {cmd:time}.{p_end}

{phang}
{opt ytype(string)} Y-axis scale type. Options: {cmd:linear} (default) |
{cmd:logarithmic}.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) ytype(logarithmic)} -- compresses right-skewed price values for cleaner scatter patterns.{p_end}

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
{p2colset 12 36 38 2}
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

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) datalabels ygrace(0.15)} -- leaves room above the tallest bar so data labels are not clipped.{p_end}

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
{opt padding(#)} Inner padding of the chart area in pixels.{p_end}

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
(default) | {cmd:nearest} | {cmd:dataset} | {cmd:point} | {cmd:x} | {cmd:y}.
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
{opt legbgcolor(string)} Background color of the legend panel.
Accepts any CSS color including {cmd:rgba()} for transparency.
Example: {cmd:legbgcolor(rgba(255,255,255,0.85))}.

{marker appearance}{...}
{dlgtab:Colors and styling}

{phang}
{opt colors(string)} Space-separated list of series colors (note: spaces, not pipes).
Accepts hex codes (e.g. {cmd:#e74c3c #3498db}), CSS color names, or
{cmd:rgba(r,g,b,a)} strings. Colors cycle if more series than colors provided.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price weight, over(foreign) colors("#e74c3c #3498db")} -- two hex colors{p_end}
{p 12 12 2}{cmd:sparkta price weight, over(foreign) colors("red steelblue")} -- named CSS colors{p_end}

{phang}
{opt bgcolor(string)} Page background color.{p_end}

{phang}
{opt plotcolor(string)} Chart plot area background color.{p_end}

{phang}
{opt gridcolor(string)} Color of grid lines.{p_end}

{phang}
{opt gridopacity(#)} Opacity of grid lines, 0 to 1. Default {cmd:0.15}.{p_end}

{phang}
{opt datalabels} Show value labels on each bar or point.{p_end}

{phang}
{opt pielabels} Show value and percentage labels on pie/donut slices.

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

{dlgtab:PNG download and data handling}

{phang}
{opt download} Show a PNG download button in the top-right corner of the chart.
Clicking it saves the chart as a {cmd:.png} image file. Works in all modern browsers.{p_end}

{phang}
{opt nomissing} From v1.8.8, missing values in {cmd:over()}, {cmd:by()},
{cmd:filter()}, {cmd:filter2()}, and {it:varlist} are automatically excluded
before chart building, matching Stata convention. Specifying {cmd:nomissing}
is therefore no longer required but is accepted for backward compatibility.{p_end}

{phang}
{opt novaluelabels} Display raw numeric codes instead of value labels
for {cmd:over()} and {cmd:by()} group names.
Useful when value labels are long or when you need to display the underlying codes.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) novaluelabels} -- shows "1 2 3 4 5" instead of value label text.{p_end}


{marker export}{...}
{dlgtab:Basic options}

{phang}
{opt type(charttype)} Chart type. Default is {cmd:bar}. All accepted values
are listed in {helpb sparkta##types:Chart types} above.{p_end}

{phang}
{opt title(string)} Main heading at the top of the chart page.
Defaults to {cmd:Sparkta} when omitted.{p_end}

{phang}
{opt subtitle(string)} Secondary heading displayed below the title.{p_end}

{phang}
{opt note(string)} Italic note displayed below the chart area.{p_end}

{phang}
{opt caption(string)} Small caption text below the note.{p_end}

{phang}
{opt theme(string)} Color theme and/or background. Bare background keywords:
{cmd:default} (white), {cmd:dark} (black), {cmd:light} (gray).
Named palettes: {cmd:tab1}, {cmd:tab2}, {cmd:tab3}, {cmd:cblind1},
{cmd:viridis}, {cmd:neon}, {cmd:swift_red}.
Compound: {cmd:dark_viridis}, {cmd:light_tab1}, etc.
See {helpb sparkta##themes:Themes} for the full list.{p_end}

{phang}
{opt export(filepath)} Save chart to a file instead of opening the browser.
Path must end in {cmd:.html}. Tilde {cmd:~} expansion works on all platforms.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) export("~/Desktop/prices.html")}{p_end}

{phang}
{opt offline} Embed all JavaScript libraries inside the HTML file.
The result works with no internet connection (~280KB vs ~50KB default).
Required for institutional networks that block CDN requests.{p_end}

{phang}
{opt sliders(varlist)} Add dual-handle range sliders for one or more numeric
variables. The viewer drags the handles to restrict the plotted range
interactively. Accepts multiple space-separated variables:
{cmd:sliders(mpg price)}. Only numeric variables are accepted.{p_end}

{phang}
{opt horizontal} Render bar chart with horizontal bars.
Identical to {cmd:type(hbar)}. Provided as a convenient flag.{p_end}

{phang}
{opt stacked} Stack multiple series on top of each other. For bar charts:
series heights add up. For area charts: areas stack cumulatively.
Identical to {cmd:type(stackedbar)} or {cmd:type(stackedarea)}.{p_end}

{phang}
{opt fill} Fill the area between a line chart and the x-axis baseline.
Identical to {cmd:type(area)}.{p_end}

{phang}
{opt gradient} Apply a gradient fill to bars or area charts using the
current palette colors. Use {cmd:gradcolors(start|end)} to specify
exact start and end colors for all series.{p_end}

{phang}
{opt fitci} Add a 95% confidence interval band around the scatter fit line.
Supported for {cmd:fit(lfit)}, {cmd:fit(qfit)}, {cmd:fit(exp)},
{cmd:fit(log)}, and {cmd:fit(power)}. CI is computed in Stata before
the chart is built and recomputed when a slider changes.{p_end}

{phang}
{opt datalabels} Overlay the numeric value on each bar or pie slice.
Font size and color adapt automatically to the chart theme.{p_end}

{phang}
{opt download} Show a PNG download button in the top-right corner of
the chart. Clicking saves the current view as a {cmd:.png} image file.
Works in all modern browsers without any additional software.{p_end}

{phang}
{opt pielabels} Show percentage labels on pie and donut chart slices.
Labels appear inside each slice and show the rounded percentage share.{p_end}

{phang}
{opt nostats} Suppress the summary statistics panel below the chart.
Useful when sharing a compact chart where the stats are not needed.{p_end}

{phang}
{opt nolegend} Hide the chart legend entirely.
Equivalent to {cmd:legend(none)}.{p_end}

{phang}
{opt nopoints} Hide point markers on line and area charts.
The line is still drawn; only the circular markers at each data point
are suppressed.{p_end}

{phang}
{opt nomissing} Exclude observations with missing values in {cmd:over()},
{cmd:by()}, or {cmd:filters()} variables from all computations.
By default, missing values in grouping variables are already excluded;
this option makes the exclusion explicit and also applies to {it:varlist}.{p_end}

{phang}
{opt novaluelabels} Use raw numeric codes instead of Stata value labels
in chart groups and legends. By default, if {cmd:rep78} has labels
1=Poor 2=Fair etc., those labels appear on the chart automatically.
Use this option to show 1 2 3 4 5 instead.{p_end}

{phang}
{opt noticks} Hide axis tick marks. The tick labels (numbers/categories)
remain visible; only the short lines extending from the axis are removed.{p_end}

{phang}
{opt spanmissing} Connect line segments across missing values in the
{it:varlist} variable. By default, a missing value creates a gap in the line.{p_end}

{phang}
{opt yreverse} Reverse the y-axis so larger values appear at the bottom.
Useful for rankings (rank 1 at top), depth measures, or any variable
where lower numbers represent a better or more prominent position.

{p 8 8 2}{it:Example:} {cmd:sparkta rank, over(country) type(hbar) yreverse}{p_end}

{phang}
{opt xreverse} Reverse the x-axis so larger values appear on the left.{p_end}

{phang}
{opt offline} is documented above under {helpb sparkta##offline:Offline mode}.

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
{stata `"sparkta price, over(rep78) colors(#e41a1c|#377eb8|#4daf4a|#984ea3|#ff7f00)"':{space 2}sparkta price, over(rep78) colors(#e41a1c|#377eb8|#4daf4a|#984ea3|#ff7f00)}

{dlgtab:Offline mode}

{p 4 4 2}
For institutional networks or air-gapped systems, bundle all JS inside the file:{p_end}

{p 8 8 2}
{stata `"sparkta price, over(rep78) offline export("chart_offline.html")"':{space 2}sparkta price, over(rep78) offline export("chart_offline.html")}{p_end}

{p 4 4 2}
The resulting file is ~280KB and works with no internet connection.

{dlgtab:Export to file}

{p 8 8 2}
{stata `"sparkta price, type(cibar) over(rep78) export("prices.html")"':{space 2}sparkta price, type(cibar) over(rep78) export("prices.html")}

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
Suppress with {cmd:nostats}. For large datasets, sparklines are rendered
lazily on scroll, keeping file sizes small.

{marker offline}{...}
{title:Offline mode}

{p 4 4 2}
By default, sparkta charts load Chart.js and related libraries from the
internet when the viewer opens them (~50KB download). Add {cmd:offline}
to bundle everything inside the HTML file (~280-320KB). Offline charts
work on air-gapped systems with no network requests of any kind.{p_end}

{p 4 4 2}
Test with: {stata "sysuse auto, clear":sysuse auto, clear} then
{stata "sparkta price, over(rep78) offline":sparkta price, over(rep78) offline}.

{marker stored}{...}
{title:Stored results}

{p 4 4 2}
{cmd:sparkta} stores nothing in {cmd:e()} or {cmd:r()}. It is a display
command that produces an HTML file as a side effect.

{marker mistakes}{...}
{title:Common mistakes}

{p 4 4 2}
Read these before experimenting -- they cover the six errors that trip up
almost every new sparkta user.{p_end}

{p 4 8 4}
{bf:1. Scatter x/y reversed.}
{cmd:sparkta mpg price, type(scatter)} puts mpg on the y-axis and price on x.
If you want price on y: {cmd:sparkta price mpg, type(scatter)}.{p_end}
{pmore}
{bf:Rule: y comes first, then x.}{p_end}

{p 4 8 4}
{bf:2. Pie chart without over().}
{cmd:sparkta price, type(pie)} will error. Pie and donut always require
{cmd:over()} to know how to slice: {cmd:sparkta price, type(pie) over(rep78)}.{p_end}

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
{bf:6. Colors must use CSS format, not Stata format.}
{cmd:colors()} accepts CSS color formats only: named colors ({cmd:red}, {cmd:steelblue}),
hex codes ({cmd:#e74c3c}), or rgba values ({cmd:rgba(231,76,60,0.8)}).
Stata color names like {cmd:navy} and {cmd:maroon} work for common colors --
but Stata-specific formats like {cmd:gs8} or {cmd:%50} do not.

{marker limits}{...}
{title:Known limitations}

{p 4 4 2}
{bf:Stata 17 or later is required.} Running sparkta on Stata 16 or earlier
will produce an error before any chart is built.{p_end}

{p 4 4 2}
Pie and donut charts require exactly one numeric variable and {cmd:over()}.{p_end}

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
Without the {cmd:offline} option, the HTML file requires an internet connection
to render (Chart.js loaded from CDN). Use {cmd:offline} for air-gapped use.

{marker memory}{...}
{title:Memory and large datasets}

{p 4 4 2}
{cmd:sparkta} reads observations directly from Stata memory and is not
limited by macro string length. Practical limits depend on available Java
heap memory. Default heap sizes and suggested fixes:

{p2colset 8 28 28 2}
{p2col:{it:Stata version}}{it:Default heap  --  Suggested fix}{p_end}
{p2line}
{p2col:Stata 15{hline 1}16}384 MB  {cmd:-->}  {cmd:set java_heapmax 1024m}{p_end}
{p2col:Stata 17{hline 1}18}512 MB  {cmd:-->}  {cmd:set java_heapmax 1024m}{p_end}
{p2col:Stata 19+}4,096 MB  {cmd:-->}  Rarely needed; try {cmd:set java_heapmax 8192m}{p_end}
{p2colreset}

{p 4 4 2}
Restart Stata after changing {cmd:java_heapmax}.
Check current heap with {cmd:query java}.
Approximate dataset size guide: up to ~100K observations is comfortable on
default settings; up to ~500K is achievable with the default Stata 19+ heap.

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
t-critical values: exact 8dp table for df 1-30; linear interpolation
df 31-120; Cornish-Fisher expansion df > 120 (max error < 1e-7).{p_end}

{pmore}
Cornish, E.A. and Fisher, R.A. (1938).
{it:Revue de l{c 39}Institut International de Statistique},
5, 307-320.{p_end}

{p 4 4 2}
{bf:Histogram binning.}
Sturges' rule: k = ceil(log2(n) + 1), clamped to [5, 50].
Override with {cmd:bins(k)}.{p_end}

{pmore}
Sturges, H.A. (1926). {it:Journal of the American Statistical Association},
21(153), 65-66.{p_end}

{p 4 4 2}
{bf:Violin kernel density.}
Delegated to the chartjs-chart-boxplot plugin. Gaussian kernel with
Scott's rule: h = 1.06 x sigma x n^(-1/5). {cmd:bandwidth()} passes a
multiplier applied to the plugin's default.{p_end}

{pmore}
Scott, D.W. (1992). {it:Multivariate Density Estimation}. Wiley, New York.{p_end}

{p 4 4 2}
{bf:Box plot whiskers.}
Most extreme observation within k x IQR of the box edges (IQR = Q3 - Q1).
Default k = 1.5 (Tukey fences). Override with {cmd:whiskerfence(k)}.
Observations beyond the fences are drawn as outlier dots.{p_end}

{pmore}
Tukey, J.W. (1977). {it:Exploratory Data Analysis}. Addison-Wesley.{p_end}

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
Install: {stata `"net install sparkta, from("https://raw.githubusercontent.com/fahad-mirza/sparkta_stata/main/ado/") replace"':net install sparkta (click to reinstall)}