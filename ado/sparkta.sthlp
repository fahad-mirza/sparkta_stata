{smcl}
{* sparkta.sthlp  v3.5.108  2026-03-18}{...}
{hline}
help for {cmd:sparkta}
{hline}

{title:Title}

{p 4 4 2}
{bf:sparkta} {hline 2} Interactive self-contained HTML charts and dashboards from Stata

{title:Description}

{p 4 4 2}
{cmd:sparkta} makes your Stata data interactive -- one command produces a
self-contained {cmd:.html} file that anyone can open in a browser with no
software installed. Click a bar and see the value. Hover for a tooltip.
Use a dropdown to filter the chart in real time. Share the file by email
and the interactivity travels with it.

{p 4 4 2}
Every chart automatically includes a collapsible summary statistics table
below it (N, mean, median, SD, min, max, and a sparkline per group).
No JavaScript, no Python, no server, no installation for the recipient.

{p 4 4 2}
{bf:Requirements:} Stata 17 or later with {cmd:sparkta.jar} in your Stata
personal folder or working directory. Run {cmd:sparkta price, over(foreign)} to verify.

{p 4 8 4}
{bf:Try this now -- load Stata's built-in auto dataset and run:}

{p 8 8 2}
{cmd:sysuse auto, clear}
{cmd:sparkta price, over(rep78)}

{p 8 8 2}
A chart opens in your browser showing mean car price by repair record.
That is sparkta. Everything else in this file is about customising it.

{title:Quick Start}

{p 4 4 2}
All examples below use Stata's built-in {cmd:auto} dataset.
Type {cmd:sysuse auto, clear} first to load it.

{p2colset 6 40 40 2}
{p2col:{cmd:sparkta price, over(rep78)}}{it:Your first chart -- mean price by repair record}{p_end}
{p2col:{cmd:sparkta price, over(rep78) title(My Chart)}}{it:Add a title}{p_end}
{p2col:{cmd:sparkta price mpg, type(scatter)}}{it:Scatter plot -- price vs mileage}{p_end}
{p2col:{cmd:sparkta price, type(histogram)}}{it:Distribution of prices}{p_end}
{p2col:{cmd:sparkta price, type(cibar) over(rep78)}}{it:Mean price with 95%% confidence intervals}{p_end}
{p2col:{cmd:sparkta price, over(rep78) export("chart.html")}}{it:Save to a file instead of opening browser}{p_end}
{p2colreset}

{p 4 4 2}
{bf:Three key options that unlock most of sparkta's power:}

{p2colset 6 20 20 2}
{p2col:{cmd:over(var)}}Groups results by {it:var} -- all groups appear on {bf:one chart}
as separate series. Use this to compare groups side by side.{p_end}
{p2col:{cmd:by(var)}}Creates a {bf:separate panel} for each value of {it:var}.
Use this when you want individual charts per group rather than overlaid series.{p_end}
{p2col:{cmd:filters(varlist)}}Adds interactive dropdown filters to the HTML file so
{bf:viewers can filter} the chart themselves without re-running Stata.
Accepts one or more variables: {cmd:filters(foreign)} or {cmd:filters(foreign rep78)}.{p_end}
{p2colreset}

{p 4 4 2}
{bf:Quick rule of thumb:} want to compare groups on one chart? Use {cmd:over()}.
Want separate charts per group? Use {cmd:by()}. Want the reader to explore interactively? Use {cmd:filters()}.
You can also combine {cmd:over()} and {cmd:by()} -- {cmd:by()} makes the panels,
{cmd:over()} groups the series inside each panel (not supported for pie/donut).

{title:Common Mistakes}

{p 4 4 2}
Read these before experimenting -- they cover the six errors that trip up
almost every new sparkta user.

{p 4 8 4}
{bf:1. Scatter x/y reversed.}
{cmd:sparkta mpg price, type(scatter)} puts mpg on the y-axis and price on x.
If you want price on y: {cmd:sparkta price mpg, type(scatter)}.
{bf:Rule: y comes first, then x.}

{p 4 8 4}
{bf:2. Pie chart without over().}
{cmd:sparkta price, type(pie)} will error. Pie and donut always require
{cmd:over()} to know how to slice: {cmd:sparkta price, type(pie) over(rep78)}.

{p 4 8 4}
{bf:3. histogram with over().}
{cmd:sparkta price, type(histogram) over(rep78)} is not supported.
For separate histograms per group, use {cmd:by()}:
{cmd:sparkta price, type(histogram) by(foreign)}.

{p 4 8 4}
{bf:4. Combining over() and by() with pie or donut.}
{cmd:over()} and {cmd:by()} work together for most chart types -- for example,
{cmd:sparkta price, over(rep78) by(foreign)} creates one panel per origin,
each showing grouped bars by repair record. The exception is {cmd:pie} and
{cmd:donut}: {cmd:over()} already defines the slices, so {cmd:by()} would just
repeat identical pies and is not permitted.

{p 4 8 4}
{bf:5. Forgetting that the default stat is mean.}
{cmd:sparkta price, over(rep78)} shows {bf:mean} price per group -- not sum or count.
Add {cmd:stat(sum)}, {cmd:stat(count)}, or {cmd:stat(median)} to change this.

{p 4 8 4}
{bf:6. Colors must use CSS format, not Stata format.}
{cmd:colors()} accepts CSS color formats only: named colors ({cmd:red}, {cmd:steelblue}),
hex codes ({cmd:#e74c3c}), or rgba values ({cmd:rgba(231,76,60,0.8)}).
Stata color names like {cmd:navy} and {cmd:maroon} work for common colors --
but Stata-specific formats like {cmd:gs8} or {cmd:%50} do not.

{title:What goes in varlist?}

{p 4 4 2}
The variable(s) you list before the comma are what gets measured (plotted on the
y-axis for most chart types). How many variables you list depends on the chart:

{p2colset 6 36 36 2}
{p2col:{it:Chart type}}{it:What to list}{p_end}
{p2line}
{p2col:bar, line, area, cibar, ciline}One or more numeric variables (the y-axis values){p_end}
{p2col:scatter}{bf:y first, then x} -- e.g. {cmd:sparkta price mpg, type(scatter)}{p_end}
{p2col:bubble}{bf:y, x, then size} -- e.g. {cmd:sparkta price mpg weight, type(bubble)}{p_end}
{p2col:pie, donut}One variable only; must also specify {cmd:over()}{p_end}
{p2col:histogram}One variable only{p_end}
{p2col:boxplot, violin}One or more variables (one box/violin per variable or per group){p_end}
{p2colreset}

{p 4 4 2}
{bf:Example:} {cmd:sparkta price, over(rep78)} plots mean price per repair group.
{cmd:sparkta price weight, over(rep78)} plots mean price AND mean weight per group
as two sets of bars side by side.

{title:Chart Types}

{p 4 4 2}
{bf:Not sure which chart to use?}
Bar charts are the default and the best starting point.
Use scatter for two continuous variables.
Use histogram to see how one variable is distributed.
Use cibar when you want error bars showing statistical uncertainty.
Use boxplot or violin to show the full distribution of a variable.

{p 4 4 2}
{it:Specify the chart type with} {cmd:type()}{it:. The default is} {cmd:bar}{it:.}

{p 4 4 2}{bf:Bar and column charts}{p_end}
{p2colset 6 24 24 2}
{p2col:{cmd:bar}}{it:(default)} Compare group averages side by side -- the most common choice{p_end}
{p2col:{cmd:hbar}}Same as {cmd:bar} but horizontal -- better when group names are long{p_end}
{p2col:{cmd:stackedbar}}Stack multiple series to show part-whole relationships{p_end}
{p2col:{cmd:stackedhbar}}Stacked bars, horizontal{p_end}
{p2col:{cmd:stackedbar100}}Each column = 100%% -- shows share of total, not raw values{p_end}
{p2col:{cmd:stackedhbar100}}Same as {cmd:stackedbar100} but horizontal{p_end}
{p2colreset}

{p 4 4 2}{bf:Line and area charts}{p_end}
{p2colset 6 24 24 2}
{p2col:{cmd:line}}Connect values across groups or categories with a line{p_end}
{p2col:{cmd:area}}Filled line chart -- emphasises cumulative volume{p_end}
{p2col:{cmd:stackedarea}}Stacked filled areas -- total and composition at once{p_end}
{p2col:{cmd:stackedline}}Alias for {cmd:stackedarea}{p_end}
{p2colreset}

{p 4 4 2}{bf:Distributions and relationships}{p_end}
{p2colset 6 24 24 2}
{p2col:{cmd:scatter}}Relationship between two continuous variables ({it:list y then x}){p_end}
{p2col:{cmd:bubble}}Like scatter but point size encodes a third variable ({it:list y, x, size}){p_end}
{p2col:{cmd:histogram}}Distribution of one variable (bins + density/frequency){p_end}
{p2col:{cmd:boxplot}}Box-and-whisker: shows median, IQR, and outliers{p_end}
{p2col:{cmd:hbox}}Horizontal box-and-whisker (alias for {cmd:hboxplot}){p_end}
{p2col:{cmd:violin}}Violin: like a boxplot but also shows the density shape{p_end}
{p2col:{cmd:hviolin}}Horizontal violin (alias for {cmd:hviolinplot}){p_end}
{p2colreset}

{p 4 4 2}{bf:Proportions}{p_end}
{p2colset 6 24 24 2}
{p2col:{cmd:pie}}Pie chart -- requires one variable and {cmd:over()}{p_end}
{p2col:{cmd:donut}}Donut chart -- same as pie with a hole in the centre{p_end}
{p2colreset}

{p 4 4 2}{bf:Statistical charts with confidence intervals}{p_end}
{p2colset 6 24 24 2}
{p2col:{cmd:cibar}}Bar chart with 95%% CI whiskers -- good for hypothesis testing contexts{p_end}
{p2col:{cmd:ciline}}Line chart with shaded CI band -- good for trends with uncertainty{p_end}
{p2colreset}

{p 4 4 2}
{bf:Scatter and bubble note:} list y before x, following Stata convention.
{cmd:sparkta price mpg, type(scatter)} puts price on the y-axis and mpg on x.
For bubble, list a third variable for point size: {cmd:sparkta price mpg weight, type(bubble)}.

{p 4 4 2}
{bf:100%% stacked bars:} each column sums to 100%%, showing share of total.
The tooltip shows both the percentage and the underlying raw value.

{title:Syntax}

{p 8 16 2}
{cmd:sparkta} {varlist} {ifin} [{cmd:,} {it:options}]

{synoptset 28 tabbed}{...}
{synopthdr:Option group}
{synoptline}

{syntab:Chart type and labels}
{synopt:{cmd:type(}{it:charttype}{cmd:)}}Chart type (see {bf:Chart Types} above){p_end}
{synopt:{cmd:title(}{it:string}{cmd:)}}Main chart heading{p_end}
{synopt:{cmd:subtitle(}{it:string}{cmd:)}}Secondary heading below title{p_end}
{synopt:{cmd:note(}{it:string}{cmd:)}}Italic note below chart{p_end}
{synopt:{cmd:caption(}{it:string}{cmd:)}}Small caption below note{p_end}
{synopt:{cmd:notesize(}{it:string}{cmd:)}}Font size for note and caption text, any CSS value e.g. {cmd:1rem}, {cmd:14px}{p_end}
{synopt:{cmd:gradient}}Gradient fill on area/bar charts using auto palette colors{p_end}
{synopt:{cmd:gradcolors(}{it:c1}{cmd:|}{it:c2}{cmd:)}}Custom gradient: start color | end color (implies {cmd:gradient}){p_end}
{synopt:{cmd:leglabels(}{it:list}{cmd:)}}Rename legend entries: pipe-separated labels in dataset order, e.g. {cmd:leglabels(Domestic|Foreign)}{p_end}
{synopt:{cmd:relabel(}{it:list}{cmd:)}}Rename over() group labels on both the x-axis and legend simultaneously, e.g. {cmd:relabel(Domestic|Foreign)}{p_end}
{synopt:{cmd:yline(}{it:values}{cmd:)}}Horizontal reference lines: pipe-separated y values, e.g. {cmd:yline(50)} or {cmd:yline(10000|20000)}{p_end}
{synopt:{cmd:xline(}{it:values}{cmd:)}}Vertical reference lines (numeric x-axis only): pipe-separated x values{p_end}
{synopt:{cmd:ylinecolor(}{it:colors}{cmd:)}}Colors per yline, pipe-separated CSS colors{p_end}
{synopt:{cmd:xlinecolor(}{it:colors}{cmd:)}}Colors per xline, pipe-separated CSS colors{p_end}
{synopt:{cmd:ylinelabel(}{it:texts}{cmd:)}}Labels per yline, pipe-separated. Empty entry = no label{p_end}
{synopt:{cmd:xlinelabel(}{it:texts}{cmd:)}}Labels per xline, pipe-separated{p_end}
{synopt:{cmd:yband(}{it:pairs}{cmd:)}}Horizontal shaded bands: {cmd:yband(lo hi)} or pipe-sep for multiple: {cmd:yband(10 20|60 80)}{p_end}
{synopt:{cmd:xband(}{it:pairs}{cmd:)}}Vertical shaded bands (numeric x only): {cmd:xband(lo hi)}{p_end}
{synopt:{cmd:ybandcolor(}{it:colors}{cmd:)}}Fill colors per yband, pipe-separated{p_end}
{synopt:{cmd:xbandcolor(}{it:colors}{cmd:)}}Fill colors per xband, pipe-separated{p_end}
{synopt:{cmd:apoint(}{it:coords}{cmd:)}}Annotation points: space-separated y x pairs, e.g. {cmd:apoint(15 25 20 30)}{p_end}
{synopt:{cmd:apointcolor(}{it:colors}{cmd:)}}Colors per apoint, pipe-separated{p_end}
{synopt:{cmd:apointsize(}{it:#}{cmd:)}}Radius in px for all annotation points (default 8){p_end}
{synopt:{cmd:alabelpos(}{it:coords}{cmd:)}}Annotation label positions: pipe-sep {cmd:y x [pos]}; {it:pos} = minute-clock direction (15=right, 30=below, 45=left, 0=center){p_end}
{synopt:{cmd:alabelgap(}{it:#}{cmd:)}}Label offset distance in pixels from point (default 15); ignored when direction is 0{p_end}
{synopt:{cmd:alabeltext(}{it:texts}{cmd:)}}Annotation label texts, pipe-separated (paired with alabelpos){p_end}
{synopt:{cmd:alabelfs(}{it:#}{cmd:)}}Font size px for all annotation labels (default 12){p_end}
{synopt:{cmd:aellipse(}{it:quads}{cmd:)}}Annotation ellipses: pipe-sep {cmd:ymin xmin ymax xmax} quads, e.g. {cmd:aellipse(10 20 20 30)}{p_end}
{synopt:{cmd:aellipsecolor(}{it:colors}{cmd:)}}Fill colors per ellipse, pipe-separated{p_end}
{synopt:{cmd:aellipseborder(}{it:colors}{cmd:)}}Border colors per ellipse, pipe-separated{p_end}
{synopt:{cmd:theme(}{it:string}{cmd:)}}Background, palette, or compound theme; see {bf:Options} for full list{p_end}
{synopt:{cmd:export(}{it:filepath}{cmd:)}}Save HTML to file instead of opening browser{p_end}

{syntab:Grouping and panels}
{synopt:{cmd:over(}{it:varname}{cmd:)}}Group by categorical variable (all on one chart){p_end}
{synopt:{cmd:by(}{it:varname}{cmd:)}}Separate panel per group{p_end}
{synopt:{cmd:layout(}{it:string}{cmd:)}}Panel arrangement: {cmd:vertical} | {cmd:horizontal} | {cmd:grid}{p_end}
{synopt:{cmd:filters(}{it:varlist}{cmd:)}}One or more interactive filter dropdowns: space-separated varlist e.g. {cmd:filters(foreign rep78)}{p_end}
{synopt:{cmd:sliders(}{it:varlist}{cmd:)}}One or more dual-handle range sliders for numeric variables: space-separated varlist e.g. {cmd:sliders(price mpg)}{p_end}
{synopt:{cmd:sortgroups(}{it:string}{cmd:)}}Group order: {cmd:asc} | {cmd:desc}{p_end}
{synopt:{cmd:nostats}}Suppress the summary statistics panel{p_end}

{syntab:Axes}
{synopt:{cmd:xtitle(}{it:string}{cmd:)}}X-axis label{p_end}
{synopt:{cmd:ytitle(}{it:string}{cmd:)}}Y-axis label{p_end}
{synopt:{cmd:xrange(}{it:min max}{cmd:)}}X-axis min and max{p_end}
{synopt:{cmd:yrange(}{it:min max}{cmd:)}}Y-axis min and max{p_end}
{synopt:{cmd:ystart(zero)}}Anchor y-axis at zero{p_end}
{synopt:{cmd:xtype(}{it:string}{cmd:)}}X-axis scale: {cmd:linear} | {cmd:logarithmic} | {cmd:category} | {cmd:time}{p_end}
{synopt:{cmd:ytype(}{it:string}{cmd:)}}Y-axis scale: {cmd:linear} | {cmd:logarithmic}{p_end}
{synopt:{cmd:y2(}{it:varlist}{cmd:)}}Variables to plot on secondary (right) y-axis{p_end}
{synopt:{cmd:y2title(}{it:string}{cmd:)}}Right y-axis label{p_end}
{synopt:{cmd:y2range(}{it:min max}{cmd:)}}Right y-axis min and max{p_end}

{syntab:Axis ticks}
{synopt:{cmd:xtickcount(}{it:#}{cmd:)}}Approximate number of x-axis ticks{p_end}
{synopt:{cmd:ytickcount(}{it:#}{cmd:)}}Approximate number of y-axis ticks{p_end}
{synopt:{cmd:xtickangle(}{it:#}{cmd:)}}X-axis tick label rotation in degrees{p_end}
{synopt:{cmd:ytickangle(}{it:#}{cmd:)}}Y-axis tick label rotation in degrees{p_end}
{synopt:{cmd:xstepsize(}{it:#}{cmd:)}}Interval between x-axis ticks{p_end}
{synopt:{cmd:ystepsize(}{it:#}{cmd:)}}Interval between y-axis ticks{p_end}
{synopt:{cmd:xlabels(}{it:string}{cmd:)}}Custom x-axis labels, pipe-separated{p_end}
{synopt:{cmd:ylabels(}{it:string}{cmd:)}}Custom y-axis labels, pipe-separated{p_end}
{synopt:{cmd:xticks(}{it:list}{cmd:)}}Pin x-axis tick positions to exact values: pipe-separated numbers, e.g. {cmd:xticks(0|25|50|75|100)}{p_end}
{synopt:{cmd:yticks(}{it:list}{cmd:)}}Pin y-axis tick positions to exact values: pipe-separated numbers, e.g. {cmd:yticks(0|10|20|30)}{p_end}

{syntab:Axis lines}
{synopt:{cmd:xgridlines(on|off)}}Vertical grid lines (default {cmd:on}){p_end}
{synopt:{cmd:ygridlines(on|off)}}Horizontal grid lines (default {cmd:on}){p_end}
{synopt:{cmd:xborder(on|off)}}X-axis border line (default {cmd:on}){p_end}
{synopt:{cmd:yborder(on|off)}}Y-axis border line (default {cmd:on}){p_end}
{synopt:{cmd:yreverse}}Reverse the y-axis direction (largest values at bottom){p_end}
{synopt:{cmd:xreverse}}Reverse the x-axis direction (largest values at left){p_end}
{synopt:{cmd:noticks}}Hide axis tick marks (labels remain visible){p_end}
{synopt:{cmd:ygrace(}{it:#}{cmd:)}}Add proportional padding above the y-axis maximum, 0 to 1 (e.g. {cmd:ygrace(0.1)} adds 10%%){p_end}

{syntab:Chart behaviour}
{synopt:{cmd:horizontal}}Horizontal bars (equivalent to {cmd:type(hbar)}){p_end}
{synopt:{cmd:stacked}}Stack multiple series{p_end}
{synopt:{cmd:fill}}Fill area under line (equivalent to {cmd:type(area)}){p_end}
{synopt:{cmd:areaopacity(}{it:#}{cmd:)}}Area fill opacity, 0{hline 1}1 (default {cmd:0.75}){p_end}
{synopt:{cmd:smooth(}{it:#}{cmd:)}}Line smoothness 0{hline 1}1 (default {cmd:0.3}){p_end}
{synopt:{cmd:spanmissing}}Connect lines across missing values{p_end}
{synopt:{cmd:stepped(}{it:string}{cmd:)}}Step function: {cmd:before} | {cmd:after} | {cmd:middle}{p_end}

{syntab:Points and lines}
{synopt:{cmd:pointsize(}{it:#}{cmd:)}}Point marker radius in pixels (default {cmd:4}){p_end}
{synopt:{cmd:pointstyle(}{it:string}{cmd:)}}Point shape: {cmd:circle} | {cmd:cross} | {cmd:dash} | {cmd:line} | {cmd:rect} | {cmd:rectRounded} | {cmd:star} | {cmd:triangle}{p_end}
{synopt:{cmd:pointborderwidth(}{it:#}{cmd:)}}Point border width in pixels (default {cmd:1}){p_end}
{synopt:{cmd:pointrotation(}{it:#}{cmd:)}}Point rotation in degrees (default {cmd:0}){p_end}
{synopt:{cmd:nopoints}}Suppress point markers on line/area charts{p_end}
{synopt:{cmd:pointhoversize(}{it:#}{cmd:)}}Point radius on hover in pixels (default: pointsize+2){p_end}
{synopt:{cmd:linewidth(}{it:#}{cmd:)}}Line width in pixels (default {cmd:2}){p_end}
{synopt:{cmd:lpattern(}{it:string}{cmd:)}}Line dash pattern for all series: {cmd:solid} | {cmd:dash} | {cmd:dot} | {cmd:dashdot}{p_end}
{synopt:{cmd:lpatterns(}{it:string}{cmd:)}}Per-series dash patterns, pipe-separated: e.g. {cmd:solid|dash|dot}{p_end}

{syntab:Bars}
{synopt:{cmd:barwidth(}{it:#}{cmd:)}}Bar width as proportion 0{hline 1}1{p_end}
{synopt:{cmd:bargroupwidth(}{it:#}{cmd:)}}Group width as proportion 0{hline 1}1{p_end}
{synopt:{cmd:borderradius(}{it:#}{cmd:)}}Bar corner radius in pixels{p_end}
{synopt:{cmd:opacity(}{it:#}{cmd:)}}Bar fill opacity 0{hline 1}1 (default {cmd:0.85}){p_end}

{syntab:Layout}
{synopt:{cmd:aspect(}{it:#}{cmd:)}}Chart aspect ratio (width / height){p_end}
{synopt:{cmd:padding(}{it:#}{cmd:)}}Inner chart padding in pixels{p_end}

{syntab:Animation}
{synopt:{cmd:animate(}{it:string}{cmd:)}}Speed: {cmd:fast} | {cmd:slow} | {cmd:none} (default: Chart.js native ~1000ms){p_end}
{synopt:{cmd:easing(}{it:string}{cmd:)}}Easing function, e.g. {cmd:easeOutBounce}{p_end}
{synopt:{cmd:animdelay(}{it:#}{cmd:)}}Delay before animation in milliseconds{p_end}
{synopt:{cmd:animduration(}{it:#}{cmd:)}}Animation duration in milliseconds (overrides {cmd:animate()}){p_end}

{syntab:Tooltip}
{synopt:{cmd:tooltipformat(}{it:string}{cmd:)}}Number format, e.g. {cmd:\",.0f\"}{p_end}
{synopt:{cmd:tooltipmode(}{it:string}{cmd:)}}Interaction mode: {cmd:index} (default) | {cmd:nearest} | {cmd:point}{p_end}
{synopt:{cmd:tooltipposition(}{it:string}{cmd:)}}Placement: {cmd:nearest} | {cmd:average}{p_end}
{synopt:{cmd:tooltipbg(}{it:string}{cmd:)}}Tooltip background color (CSS color or rgba){p_end}
{synopt:{cmd:tooltipborder(}{it:string}{cmd:)}}Tooltip border color{p_end}
{synopt:{cmd:tooltipfontsize(}{it:#}{cmd:)}}Tooltip font size in pixels{p_end}
{synopt:{cmd:tooltippadding(}{it:#}{cmd:)}}Tooltip internal padding in pixels{p_end}

{syntab:Legend}
{synopt:{cmd:legend(}{it:string}{cmd:)}}Position: {cmd:top} | {cmd:bottom} | {cmd:left} | {cmd:right} | {cmd:none}{p_end}
{synopt:{cmd:nolegend}}Suppress the legend (alias for {cmd:legend(none)}){p_end}
{synopt:{cmd:legtitle(}{it:string}{cmd:)}}Legend title{p_end}
{synopt:{cmd:legsize(}{it:#}{cmd:)}}Legend font size in pixels{p_end}
{synopt:{cmd:legboxheight(}{it:#}{cmd:)}}Legend color box height in pixels{p_end}
{synopt:{cmd:legcolor(}{it:string}{cmd:)}}Legend label text color (CSS color){p_end}
{synopt:{cmd:legbgcolor(}{it:string}{cmd:)}}Legend background color (CSS color or rgba){p_end}

{syntab:Colors and styling}
{synopt:{cmd:colors(}{it:string}{cmd:)}}Space-separated hex, CSS, or rgba() colors{p_end}
{synopt:{cmd:bgcolor(}{it:string}{cmd:)}}Page background color{p_end}
{synopt:{cmd:plotcolor(}{it:string}{cmd:)}}Chart area background color{p_end}
{synopt:{cmd:gridcolor(}{it:string}{cmd:)}}Grid line color{p_end}
{synopt:{cmd:gridopacity(}{it:#}{cmd:)}}Grid line opacity 0{hline 1}1{p_end}
{synopt:{cmd:datalabels}}Show value labels on bars or points{p_end}
{synopt:{cmd:pielabels}}Show value and percentage labels on pie/donut slices{p_end}

{syntab:Title and axis font styling (v2.6.0)}
{synopt:{cmd:titlesize(}{it:#}{cmd:)}}Main title font size in pixels{p_end}
{synopt:{cmd:titlecolor(}{it:string}{cmd:)}}Main title text color (CSS color or hex){p_end}
{synopt:{cmd:subtitlesize(}{it:#}{cmd:)}}Subtitle font size in pixels{p_end}
{synopt:{cmd:subtitlecolor(}{it:string}{cmd:)}}Subtitle text color{p_end}
{synopt:{cmd:xtitlesize(}{it:#}{cmd:)}}X-axis title font size in pixels{p_end}
{synopt:{cmd:xtitlecolor(}{it:string}{cmd:)}}X-axis title text color{p_end}
{synopt:{cmd:ytitlesize(}{it:#}{cmd:)}}Y-axis title font size in pixels{p_end}
{synopt:{cmd:ytitlecolor(}{it:string}{cmd:)}}Y-axis title text color{p_end}
{synopt:{cmd:xlabsize(}{it:#}{cmd:)}}X-axis tick label font size in pixels{p_end}
{synopt:{cmd:xlabcolor(}{it:string}{cmd:)}}X-axis tick label text color{p_end}
{synopt:{cmd:ylabsize(}{it:#}{cmd:)}}Y-axis tick label font size in pixels{p_end}
{synopt:{cmd:ylabcolor(}{it:string}{cmd:)}}Y-axis tick label text color{p_end}

{syntab:Aggregation and pie/donut}
{synopt:{cmd:stat(}{it:string}{cmd:)}}Statistic: {cmd:mean} | {cmd:sum} | {cmd:count} | {cmd:median} | {cmd:min} | {cmd:max} | {cmd:pct}{p_end}
{synopt:{cmd:cutout(}{it:#}{cmd:)}}Donut hole size as % of radius (default {cmd:50}){p_end}
{synopt:{cmd:rotation(}{it:#}{cmd:)}}Starting angle for pie/donut in degrees{p_end}
{synopt:{cmd:circumference(}{it:#}{cmd:)}}Total arc in degrees (default {cmd:360}){p_end}
{synopt:{cmd:sliceborder(}{it:#}{cmd:)}}Border width between slices in pixels{p_end}
{synopt:{cmd:hoveroffset(}{it:#}{cmd:)}}Slice pop-out distance on hover in pixels{p_end}

{syntab:CI charts}
{synopt:{cmd:cilevel(}{it:#}{cmd:)}}Confidence level: {cmd:90} | {cmd:95} | {cmd:99} (default {cmd:95}){p_end}
{synopt:{cmd:cibandopacity(}{it:#}{cmd:)}}CI band opacity for {cmd:ciline}, 0{hline 1}1 (default {cmd:0.18}){p_end}

{syntab:Histogram}
{synopt:{cmd:bins(}{it:#}{cmd:)}}Number of bins (default: Sturges rule){p_end}
{synopt:{cmd:histtype(}{it:string}{cmd:)}}Y-axis metric: {cmd:density} | {cmd:frequency} | {cmd:fraction}{p_end}

{syntab:Box and violin plots}
{synopt:{cmd:whiskerfence(}{it:#}{cmd:)}}Tukey IQR multiplier for whisker fences (default {cmd:1.5}){p_end}
{synopt:{cmd:mediancolor(}{it:string}{cmd:)}}Color for median marker; overrides auto-detection{p_end}
{synopt:{cmd:meancolor(}{it:string}{cmd:)}}Color for mean marker; overrides auto-detection{p_end}
{synopt:{cmd:bandwidth(}{it:#}{cmd:)}}KDE bandwidth for violin plots (default: Silverman rule){p_end}

{syntab:Scatter fit lines (scatter and bubble only)}
{synopt:{cmd:fit(}{it:fittype}{cmd:)}}Add a fitted curve to a scatter or bubble chart.
Valid types: {cmd:lfit} {cmd:qfit} {cmd:lowess} {cmd:exp} {cmd:log} {cmd:power} {cmd:ma}
(see {bf:Scatter Fit Lines} below){p_end}
{synopt:{cmd:fitci}}Add a 95%% confidence band to {cmd:fit(lfit)} or {cmd:fit(qfit)}; requires {cmd:fit()} to be specified{p_end}
{synopt:{cmd:mlabel(}{it:varname}{cmd:)}}Label each scatter point with values of {it:varname}.
Suppress by default when N > 30; add {cmd:, all} to force display: {cmd:mlabel(make, all)}{p_end}
{synopt:{cmd:mlabpos(}{it:#}{cmd:)}}Uniform label position for all points using 0{hline 1}59 minute-clock
(0=center, 15=right, 30=below, 45=left; default=top){p_end}
{synopt:{cmd:mlabvposition(}{it:varname}{cmd:)}}Per-observation label position; values 0{hline 1}59 minute-clock;
requires {cmd:mlabel()} to also be set{p_end}

{syntab:Data handling}
{synopt:{cmd:nomissing}}Guarantee null-free data; required for {cmd:cibar}/{cmd:ciline}; no-op for most other chart types{p_end}
{synopt:{cmd:novaluelabels}}Show raw numeric codes instead of value labels{p_end}
{synopt:{cmd:download}}Show a PNG download button in the chart header{p_end}
{synopt:{cmd:offline}}Embed all JS inline -- no internet required to open the file{p_end}
{synoptline}

{title:Options}

{dlgtab:Chart Type and Labels}

{phang}
{opt type(charttype)} Specifies the chart type. Default is {cmd:bar}.
See the {bf:Chart Types} table above for all accepted values.

{phang}
{opt title(string)} Main heading displayed at the top of the page.
When omitted, the title defaults to {cmd:Sparkta}.

{phang}
{opt subtitle(string)} Secondary heading displayed below the title.

{phang}
{opt note(string)} Italic note displayed below the chart.

{phang}
{opt caption(string)} Small caption displayed below the note.

{phang}
{opt theme(string)} Color theme and named color palette.

{pmore}
{it:Background:} {cmd:default} (white) | {cmd:dark} (black) | {cmd:light} (white).

{pmore}
{it:Named palettes:} {cmd:tab1} (Tableau 10) | {cmd:tab2} (ColorBrewer Set1) |
{cmd:tab3} (ColorBrewer Dark2) | {cmd:cblind1} (Okabe-Ito colorblind-safe) |
{cmd:neon} (bright neons) | {cmd:swift_red} (Taylor Swift Red) |
{cmd:viridis} (perceptually uniform).

{pmore}
{it:Compound themes} combine a background and palette with an underscore:
{cmd:dark_viridis}, {cmd:light_tab1}, {cmd:dark_neon}, etc.
The {cmd:colors()} option always overrides any palette.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) theme(dark)}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) theme(tab1)}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) theme(dark_viridis)}{p_end}

{phang}
{opt export(filepath)} By default, {cmd:sparkta} opens your chart
directly in a browser. Use {cmd:export()} to save it to a file instead.
Path must end in {cmd:.html}. The tilde {cmd:~} shorthand works on all platforms.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) export("C:/output/price.html")}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) export("~/Desktop/price.html")}{p_end}

{dlgtab:Grouping and Panels}

{phang}
{opt over(varname [, showmissing])} {bf:The most-used option in sparkta.}
Groups the chart by a categorical variable so each group becomes a separate
series on the same chart. Value labels are used automatically when present.

{p 8 8 2}
{cmd:over()} and {cmd:by()} can be used together for most chart types.
For example, {cmd:over(rep78) by(foreign)} creates one panel per foreign value,
each showing grouped bars by repair record. The exception is {cmd:pie} and
{cmd:donut}, where combining them is not permitted.

{p 8 8 2}
Suboption {cmd:showmissing} includes observations where {it:varname} is missing
as an explicit {bf:(Missing)} group, displayed last regardless of {cmd:sortgroups()}.
Without {cmd:showmissing}, missing observations are silently excluded.

{p 8 8 2}{it:Example:} {cmd:sparkta price weight, type(bar) over(foreign)} -- one bar per variable per origin group.{p_end}

{phang}
{opt by(varname [, showmissing])} Creates separate chart panels for each value
of {it:varname}. Suboption {cmd:showmissing} adds a {bf:(Missing)} panel
for observations where {it:varname} is missing.

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
{opt filters(varlist)} Adds one or more interactive dropdown filters below the
chart. Viewers can filter the data by any combination of values without
reloading. The chart updates live. Accepts a space-separated list of one or
more categorical variables: {cmd:filters(foreign)} or {cmd:filters(foreign rep78)}.
Each variable gets its own labelled dropdown. Each variable may optionally
include {cmd:, showmissing} to add a {bf:(Missing)} entry for observations
where that variable is missing.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) filters(foreign)} -- viewer can toggle between Domestic and Foreign without re-running Stata.{p_end}

{p 8 8 2}{it:Example (multiple):} {cmd:sparkta price weight, over(rep78) filters(foreign rep78)}{p_end}

{phang}
{opt sliders(varlist)} Adds one or more dual-handle range sliders below the
chart for numeric variables. Viewers can drag the handles to restrict the
displayed data to any numeric range without reloading. The chart updates live.
Accepts a space-separated list of numeric variables: {cmd:sliders(price)} or
{cmd:sliders(price mpg)}. Each variable gets its own labelled slider showing
the current range. Can be combined with {cmd:filters()} for mixed categorical
and numeric filtering.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) sliders(mpg)} -- viewer drags the mpg slider to restrict to any fuel-economy range.{p_end}

{p 8 8 2}{it:Example (combined):} {cmd:sparkta price, over(rep78) filters(foreign) sliders(mpg weight)}{p_end}

{phang}
{opt sortgroups(string)} Controls the order of {cmd:over()} and {cmd:by()}
group labels. Options: {cmd:asc} | {cmd:desc}.
When omitted, groups are sorted ascending (numeric labels sort numerically;
string labels sort alphabetically). Filter dropdown options are always
sorted ascending and are unaffected.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) sortgroups(desc)} -- highest repair-record group shown first.{p_end}

{phang}
{opt nostats} Suppresses the summary statistics panel below the chart.

{dlgtab:Axis Options}

{phang}
{opt xtitle(string)} Label for the x-axis.

{phang}
{opt ytitle(string)} Label for the y-axis.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) xtitle(Mileage (mpg)) ytitle(Price (USD))}{p_end}

{phang}
{opt xrange(min max)} X-axis minimum and maximum. Example: {cmd:xrange(0 100)}.

{phang}
{opt yrange(min max)} Y-axis minimum and maximum. Example: {cmd:yrange(0 10000)}.

{phang}
{opt ystart(zero)} Force the y-axis to begin at zero.
The only accepted value is {cmd:zero}. Incompatible with {cmd:ytype(logarithmic)}.
Useful when Chart.js auto-scales to a non-zero minimum, making differences look larger than they are.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) ystart(zero)}{p_end}

{phang}
{opt xtype(string)} X-axis scale type. Options: {cmd:linear} (default) |
{cmd:logarithmic} | {cmd:category} | {cmd:time}.

{phang}
{opt ytype(string)} Y-axis scale type. Options: {cmd:linear} (default) |
{cmd:logarithmic}.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) ytype(logarithmic)} -- compresses right-skewed price values for cleaner scatter patterns.{p_end}

{phang}
{opt y2(varlist)} One or more variables to plot on the right (secondary) y-axis.
Each variable must also appear in {it:varlist}. The right axis is independently
scaled and labelled. Works with {cmd:type(bar)} and {cmd:type(line)} charts that
include {cmd:over()}. Example: {cmd:sparkta price mpg, type(line) over(foreign) y2(mpg)}.

{phang}
{opt y2title(string)} Label for the right y-axis.

{phang}
{opt y2range(min max)} Explicit min and max for the right y-axis.

{phang}
{opt xtickcount(#)} Approximate number of ticks on the x-axis.

{phang}
{opt ytickcount(#)} Approximate number of ticks on the y-axis.

{phang}
{opt xtickangle(#)} Rotation angle of x-axis tick labels in degrees.
Useful when category labels are long and overlap horizontally.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) xtickangle(45)}{p_end}

{phang}
{opt ytickangle(#)} Rotation angle of y-axis tick labels in degrees.

{phang}
{opt xlabels(string)} Custom tick labels for the x-axis, pipe-separated.
Applied left-to-right across the existing tick positions.

{p 8 8 2}{it:Example -- rename repair-record codes to plain English:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) xlabels(Poor|Fair|Average|Good|Excellent)}{p_end}

{phang}
{opt ylabels(string)} Custom tick labels for the y-axis, pipe-separated.

{phang}
{opt xstepsize(#)} Interval between x-axis ticks. Example: {cmd:xstepsize(500)}.

{phang}
{opt ystepsize(#)} Interval between y-axis ticks.

{phang}
{opt xgridlines(on|off)} Show or hide vertical grid lines. Default {cmd:on}.

{phang}
{opt ygridlines(on|off)} Show or hide horizontal grid lines. Default {cmd:on}.

{phang}
{opt xborder(on|off)} Show or hide the x-axis border line. Default {cmd:on}.

{phang}
{opt yborder(on|off)} Show or hide the y-axis border line. Default {cmd:on}.

{dlgtab:Chart Behaviour}

{phang}
{opt horizontal} Render a bar chart with horizontal bars.
Equivalent to {cmd:type(hbar)}.

{phang}
{opt stacked} Stack multiple series on top of each other.
Applies to bar and area charts.

{phang}
{opt fill} Fill the area between a line chart and the baseline.
Equivalent to {cmd:type(area)}.

{phang}
{opt areaopacity(#)} Fill opacity for {cmd:type(area)} and {cmd:fill} charts.
Accepts values from 0 (invisible) to 1 (fully opaque). Default is {cmd:0.75}.
When multiple variables share an axis, lower values (0.3{hline 1}0.5) keep
both fills visible. The y-axis is automatically anchored at zero for area charts.

{p 8 8 2}{it:Example:} {cmd:sparkta price weight, type(area) over(foreign) areaopacity(0.4)}{p_end}

{phang}
{opt smooth(#)} Line smoothness (Bezier tension), 0 to 1.
{cmd:smooth(0)} produces sharp corners; {cmd:smooth(0.6)} produces flowing curves.
Default is {cmd:0.3}.

{p 8 8 2}{it:Example:} {cmd:sparkta price, type(line) over(rep78) smooth(0.6)}{p_end}

{phang}
{opt spanmissing} Connect lines across missing values instead of breaking.

{phang}
{opt stepped(string)} Render lines as step functions.
Options: {cmd:before} | {cmd:after} | {cmd:middle}.
{cmd:before} steps up before reaching the x-value; {cmd:after} steps after;
{cmd:middle} centers the step at the midpoint between x-values.

{p 8 8 2}{it:Example:} {cmd:sparkta price, type(line) over(rep78) stepped(after)}{p_end}

{dlgtab:Point and Line Appearance}

{phang}
{opt pointsize(#)} Radius of point markers in pixels. Default {cmd:4}.
Use {cmd:pointsize(0)} to hide markers.

{phang}
{opt pointstyle(string)} Shape of point markers. Options:
{cmd:circle} (default) | {cmd:cross} | {cmd:dash} | {cmd:line} |
{cmd:rect} | {cmd:rectRounded} | {cmd:star} | {cmd:triangle}.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) pointstyle(triangle) pointsize(6)}{p_end}

{phang}
{opt pointborderwidth(#)} Border width of point markers in pixels. Default {cmd:1}.

{phang}
{opt pointrotation(#)} Rotation of point markers in degrees. Default {cmd:0}.

{phang}
{opt linewidth(#)} Width of lines in pixels. Default {cmd:2}.

{phang}
{opt lpattern(string)} Line dash pattern applied to all series on line and area charts.
Options: {cmd:solid} (default) | {cmd:dash} | {cmd:dot} | {cmd:dashdot}.
Example: {cmd:lpattern(dash)} draws all series as dashed lines.

{phang}
{opt lpatterns(string)} Per-series line dash patterns, pipe-separated in series order.
Cycles if fewer patterns are supplied than series.
Accepted tokens: {cmd:solid} | {cmd:dash} | {cmd:dot} | {cmd:dashdot}.
Example: {cmd:lpatterns(solid|dash|dot)} gives the first series a solid line,
the second a dashed line, and the third a dotted line.
When both {cmd:lpattern()} and {cmd:lpatterns()} are specified, {cmd:lpatterns()}
takes precedence for each series it covers; remaining series fall back to {cmd:lpattern()}.

{phang}
{opt nopoints} Suppress point markers on line and area charts. When specified,
{cmd:pointsize()} and {cmd:pointhoversize()} have no effect. Equivalent to
{cmd:pointsize(0)} but also suppresses the hover highlight.

{phang}
{opt pointhoversize(#)} Point radius in pixels when the cursor hovers over a data point.
Default is {cmd:pointsize + 2}. Ignored when {cmd:nopoints} is specified.

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
This mirrors Stata's {cmd:over(var, relabel(1 "A" 2 "B"))} with {cmd:asyvars showyvars}.

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
but the tick label positions are controlled by this option.

{phang}
{opt yticks(list)} Same as {cmd:xticks()} but for the y-axis.
Example: {cmd:yticks(0|10000|20000|30000)} for a salary chart.
All tokens must be valid numbers; non-numeric tokens produce an error.

{p 8 8 2}{it:Example -- show only round-thousand markers on the price axis:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) yticks(0|5000|10000|15000)}{p_end}

{dlgtab:Axis Utilities}

{phang}
{opt yreverse} Reverse the direction of the y-axis so that values increase
downward rather than upward. Useful for rankings, depth scales, or any measure
where lower numeric values represent a "better" or "deeper" position.

{p 8 8 2}{it:Example -- rank chart where 1st place appears at top:}{p_end}
{p 12 12 2}{cmd:sparkta rank_var, over(group_var) yreverse ytitle(Rank)}{p_end}

{phang}
{opt xreverse} Reverse the direction of the x-axis so that values decrease
left to right. Applies to numeric (linear) x-axes.

{phang}
{opt noticks} Hide the short tick mark lines on both axes while keeping
tick labels visible. Produces a cleaner, more minimal look.
Compatible with {cmd:xticks()} and {cmd:yticks()}.

{phang}
{opt ygrace(#)} Add proportional whitespace above the y-axis maximum.
Accepts a fraction from 0 to 1: {cmd:ygrace(0.1)} extends the axis
ceiling by 10%% of the data range, preventing the tallest bar or point
from touching the top of the plot area.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) datalabels ygrace(0.15)} -- leaves room above the tallest bar so data labels are not clipped.{p_end}

{phang}
{opt animduration(#)} Set the animation duration in milliseconds directly.
Overrides {cmd:animate()} when both are specified.
Use when {cmd:animate(fast|normal|slow)} does not give precise enough control.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) animduration(3000)} -- 3-second animation regardless of {cmd:animate()} preset.{p_end}

{dlgtab:Bar Appearance}

{phang}
{opt barwidth(#)} Proportion of available width each bar occupies, 0 to 1.
Example: {cmd:barwidth(0.6)} for narrower bars.

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

{dlgtab:Layout}

{phang}
{opt aspect(#)} Chart aspect ratio (width / height).
Values below 1 produce a taller chart; above 1 a wider chart.
Default is approximately 2 (wide landscape).

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) aspect(1)} -- square chart.{p_end}

{phang}
{opt padding(#)} Inner padding of the chart area in pixels.

{dlgtab:Animation}

{phang}
{opt animate(string)} Animation speed. Options: {cmd:fast} | {cmd:slow} | {cmd:none}.
When omitted, Chart.js uses its native default (~1000ms).
{cmd:fast} = 150ms, {cmd:slow} = 1500ms, {cmd:none} = instant.
For precise control use {cmd:animduration()} instead.

{phang}
{opt easing(string)} Animation easing function controlling how the chart
accelerates and decelerates. Common choices: {cmd:easeOutQuart} (smooth
deceleration, recommended) | {cmd:easeOutBounce} (bounces at finish) |
{cmd:linear} (constant speed). See Chart.js documentation for the full list.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) animate(slow) easing(easeOutBounce)}{p_end}

{phang}
{opt animdelay(#)} Delay before animation starts in milliseconds.

{dlgtab:Tooltip}

{phang}
{opt tooltipformat(string)} Number format for tooltip values using d3-format
syntax. Example: {cmd:tooltipformat(",.0f")} for comma-separated integers.
Applies to all chart types including CI bounds and histogram values.
Common format strings: {cmd:",.0f"} (comma integer) | {cmd:".1f"} (one decimal) |
{cmd:".0%%"} (percentage).

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) tooltipformat("$,.0f")} -- displays values as {cmd:$12,345}.{p_end}

{phang}
{opt tooltipmode(string)} Tooltip interaction mode. Options: {cmd:index}
(default) | {cmd:nearest} | {cmd:dataset} | {cmd:point} | {cmd:x} | {cmd:y}.
{cmd:index} shows all series values at the hovered x-position simultaneously --
useful for comparing multiple lines at a glance.

{p 8 8 2}{it:Example:} {cmd:sparkta price weight, type(line) over(foreign) tooltipmode(index)}{p_end}

{phang}
{opt tooltipposition(string)} Tooltip placement. Options: {cmd:average}
(default) | {cmd:nearest}.

{phang}
{opt tooltipbg(string)} Background color of the tooltip box.
Accepts any CSS color: hex ({cmd:#1a1a2e}), named ({cmd:navy}), or
{cmd:rgba()} ({cmd:rgba(0,0,0,0.9)}). Default adapts to theme.

{phang}
{opt tooltipborder(string)} Border color of the tooltip box.
Useful for matching a chart's accent color. Example: {cmd:tooltipborder(#4e79a7)}.

{phang}
{opt tooltipfontsize(#)} Font size of tooltip text in pixels.
Default is 13. Example: {cmd:tooltipfontsize(14)}.

{phang}
{opt tooltippadding(#)} Internal padding inside the tooltip box in pixels.
Default is 10. Example: {cmd:tooltippadding(14)}.

{dlgtab:Legend}

{phang}
{opt legend(string)} Legend position. Options: {cmd:top} (default) |
{cmd:bottom} | {cmd:left} | {cmd:right} | {cmd:none} (hides legend).

{phang}
{opt nolegend} Suppress the legend entirely.
Equivalent to {cmd:legend(none)}; provided as a convenient bare flag following
Stata convention (analogous to Stata's {cmd:legend(off)}).
Example: {cmd:sparkta price weight, over(rep78) nolegend}

{phang}
{opt legtitle(string)} Title displayed at the top of the legend.

{phang}
{opt legsize(#)} Font size of legend labels in pixels.

{phang}
{opt legboxheight(#)} Height of the legend color box in pixels.

{phang}
{opt legcolor(string)} Text color of legend labels.
Example: {cmd:legcolor(#ffffff)} for white labels on a dark legend background.

{phang}
{opt legbgcolor(string)} Background color of the legend panel.
Accepts any CSS color including {cmd:rgba()} for transparency.
Example: {cmd:legbgcolor(rgba(255,255,255,0.85))}.

{dlgtab:Colors and Styling}

{phang}
{opt colors(string)} Space-separated list of series colors. Accepts hex codes
(e.g. {cmd:"#e74c3c #3498db"}), CSS color names, or {cmd:rgba()} strings.
Colors cycle if more series than colors are provided.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price weight, over(foreign) colors("#e74c3c #3498db")} -- two hex colors{p_end}
{p 12 12 2}{cmd:sparkta price weight, over(foreign) colors("red steelblue")} -- named CSS colors{p_end}

{phang}
{opt bgcolor(string)} Page background color.

{phang}
{opt plotcolor(string)} Chart plot area background color.

{phang}
{opt gridcolor(string)} Color of grid lines.

{phang}
{opt gridopacity(#)} Opacity of grid lines, 0 to 1.

{phang}
{opt datalabels} Show value labels on each bar or point.

{phang}
{opt pielabels} Show value and percentage labels on pie/donut slices.

{dlgtab:Title and Axis Font Styling (v2.6.0)}

{phang}
{opt titlesize(#)} Font size of the main title in pixels. Example: {cmd:titlesize(28)}.

{phang}
{opt titlecolor(string)} Color of the main title text.
Example: {cmd:titlecolor(#2c3e50)}.

{phang}
{opt subtitlesize(#)} Font size of the subtitle in pixels.

{phang}
{opt subtitlecolor(string)} Color of the subtitle text.

{phang}
{opt xtitlesize(#)} Font size of the x-axis title in pixels.

{phang}
{opt xtitlecolor(string)} Color of the x-axis title text.

{phang}
{opt ytitlesize(#)} Font size of the y-axis title in pixels.

{phang}
{opt ytitlecolor(string)} Color of the y-axis title text.

{phang}
{opt xlabsize(#)} Font size of x-axis tick labels in pixels.

{phang}
{opt xlabcolor(string)} Color of x-axis tick labels.

{phang}
{opt ylabsize(#)} Font size of y-axis tick labels in pixels.

{phang}
{opt ylabcolor(string)} Color of y-axis tick labels.

{p 8 8 2}
All styling options accept standard CSS colors: named ({cmd:navy}), hex
({cmd:#2c3e50}), RGB ({cmd:rgb(44,62,80)}), or RGBA ({cmd:rgba(44,62,80,0.9)}).
Font size options accept positive integers (pixels). When these options are
omitted, the theme default is used.

{dlgtab:Aggregation and Pie/Donut}

{phang}
{opt stat(string)} The statistic plotted on the y-axis for bar and line charts.
{bf:Default is mean} -- sparkta always shows group averages unless you say otherwise.
Options: {cmd:mean} | {cmd:sum} | {cmd:count} | {cmd:median} |
{cmd:min} | {cmd:max}. For pie/donut: {cmd:pct} (default, percentage share) |
{cmd:sum} (raw totals).

{p 8 8 2}
When {cmd:over()} is specified, one value is computed per group.
When {cmd:over()} is omitted, one value is computed per variable in
{it:varlist} and each variable becomes a separate bar or point.

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
Default {cmd:50}. Range 0{hline 1}99.

{phang}
{opt rotation(#)} Starting angle in degrees for pie/donut slices. Default {cmd:0}
(top). Use {cmd:-90} to start at the left.

{phang}
{opt circumference(#)} Total arc in degrees rendered by pie/donut.
Default {cmd:360} (full circle). A semicircle ({cmd:180}) combined with
{cmd:rotation(-90)} creates a gauge-style half-donut chart.

{p 8 8 2}{it:Example -- half-donut gauge:}{p_end}
{p 12 12 2}{cmd:sparkta price, type(donut) over(rep78) circumference(180) rotation(-90)}{p_end}

{phang}
{opt sliceborder(#)} Border width between pie/donut slices in pixels.

{phang}
{opt hoveroffset(#)} Distance slices pop out when hovered, in pixels.

{dlgtab:Reference Annotations}

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
(categorical x-axis).

{phang}
{opt ylinecolor(colors)} Colors for each yline, pipe-separated CSS colors.
Cycles if fewer colors than lines. Default: {cmd:rgba(150,150,150,0.8)}.

{phang}
{opt xlinecolor(colors)} Colors for each xline. Same default.

{phang}
{opt ylinelabel(texts)} Text label for each yline, pipe-separated.
An empty entry (two consecutive pipes) suppresses the label for that line.
Example: {cmd:ylinelabel(Mean|Upper bound)}.

{phang}
{opt xlinelabel(texts)} Text label for each xline. Same behavior.

{phang}
{opt yband(pairs)} Draw one or more horizontal shaded bands. Each band is
specified as {cmd:lo hi} (two space-separated y values); multiple bands are
pipe-separated.

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) yband(4000 8000)} -- one shaded band{p_end}
{p 12 12 2}{cmd:sparkta price, over(rep78) yband(4000 8000|10000 14000) ybandcolor(rgba(0,200,0,0.1)|rgba(200,0,0,0.1))} -- two differently colored bands{p_end}

{phang}
{opt xband(pairs)} Vertical shaded bands. Same format as {cmd:yband()}.
Suppressed on categorical x-axis charts (same rules as {cmd:xline()}).

{phang}
{opt ybandcolor(colors)} Fill color for each yband, pipe-separated.
Default: {cmd:rgba(150,150,150,0.12)}. Cycles if fewer colors than bands.

{phang}
{opt xbandcolor(colors)} Fill color for each xband. Same default.

{phang}
{opt apoint(coords)} Draw annotation point markers at specific coordinates.
Format: space-separated y x pairs, following Stata scattteri convention.
Example: {cmd:apoint(15000 25 20000 30)} places markers at (y=15000, x=25)
and (y=20000, x=30). Note: y comes before x, matching Stata {cmd:scattteri} convention.

{p 8 8 2}{it:Example -- highlight two points on a scatter:}{p_end}
{p 12 12 2}{cmd:sparkta price mpg, type(scatter) apoint(4099 22 15906 12) apointcolor(red|navy)}{p_end}

{phang}
{opt apointcolor(colors)} Colors for annotation points, pipe-separated.
Cycles. Default: {cmd:rgba(255,99,132,0.8)}.

{phang}
{opt apointsize(#)} Radius in pixels for all annotation points. Default 8.

{phang}
{opt alabelpos(coords)} Place annotation text labels at specific coordinates.
Format: pipe-separated entries of {cmd:y x} or {cmd:y x pos}, where {it:pos}
is a minute-clock direction (0{hline 1}59) controlling where the label appears
relative to the coordinate point:{p_end}

{p2colset 12 18 18 2}
{p2col:{bf:0}}Centered on the point (Stata {cmd:mlabpos(0)} equivalent; gap ignored){p_end}
{p2col:{bf:15}}Right of point (default when pos omitted -- Stata {cmd:mlabpos} default){p_end}
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
Example: {cmd:alabeltext(Recession start|Recovery peak)}.

{phang}
{opt alabelfs(#)} Font size in pixels for all annotation labels. Default 12.

{phang}
{opt alabelgap(#)} Pixel distance from the coordinate point to the label.
Default 15. Only meaningful when the minute-clock direction in {cmd:alabelpos()}
is non-zero; direction 0 always centers the label regardless of gap.
One value applies to all labels. Example: {cmd:alabelgap(20)}.

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
Default: {cmd:rgba(99,132,255,0.15)}.

{phang}
{opt aellipseborder(colors)} Border color per ellipse, pipe-separated.
Default: {cmd:rgba(99,132,255,0.6)}.

{p 8 8 2}
{bf:Chart type restrictions.} Annotations are suppressed entirely for
{cmd:pie} and {cmd:donut} charts. Vertical annotations ({cmd:xline},
{cmd:xband}) are silently suppressed for bar, horizontal bar, CI bar,
boxplot, and violin charts (categorical x-axis). All other annotation
types work on all numeric-axis chart types. For {cmd:histogram},
{cmd:xline} and {cmd:xband} use fractional bin-index interpolation to
place marks at the correct proportional position across the numeric range.

{p 8 8 2}
{bf:Requires.} The annotation plugin ({cmd:chartjs-plugin-annotation@3.0.1})
is loaded from CDN automatically when any annotation option is specified.
For offline use, the plugin is bundled in {cmd:sparkta.jar}.

{dlgtab:CI Charts}

{phang}
{opt cilevel(#)} Confidence level for {cmd:cibar} and {cmd:ciline} charts.
Default is {cmd:95} (95% CI). Accepts any integer from 1 to 99.
Common values: {cmd:90} | {cmd:95} | {cmd:99}.

{p 8 8 2}
Confidence intervals are computed as mean +/- t * SE where SE = SD / sqrt(n)
and t is the two-tailed t-critical value with n-1 degrees of freedom.
Groups with fewer than 2 observations are omitted.
For df > 120 the standard normal z-critical value is used.

{phang}
{opt cibandopacity(#)} Opacity of the CI shaded band for {cmd:ciline} charts.
Default is {cmd:0.18}. Range 0{hline 1}1.
Example: {cmd:cibandopacity(0.05)} for a faint band; {cmd:cibandopacity(0.35)}
for a more prominent band.

{dlgtab:Scatter Fit Lines}

{p 4 4 2}
{cmd:fit()} adds a fitted curve to {cmd:type(scatter)} or {cmd:type(bubble)} charts.
The curve is computed by Stata before the chart is built, so it matches
Stata's own {cmd:twoway} output exactly for all fit types.
{cmd:fitci} adds a 95%% pointwise confidence band (lfit and qfit only).

{phang}
{opt fit(fittype)} Fit type. Valid values:

{p2colset 12 22 22 2}
{p2col:{cmd:lfit}}Linear fit: y = a + bx.
Computed using {cmd:regress y x} + {cmd:predict xb}.
Identical to Stata {cmd:twoway lfit}.{p_end}

{p2col:{cmd:qfit}}Quadratic fit: y = a + bx + cx{c 178}.
Computed using {cmd:regress y c.x##c.x} + {cmd:predict xb}.
Identical to Stata {cmd:twoway qfit}.{p_end}

{p2col:{cmd:lowess}}Locally weighted scatterplot smoothing (Cleveland 1979).
Computed using Stata's own {cmd:lowess} command with bandwidth f = 0.8.
Includes Stata's three bisquare robustness iterations.
Identical to Stata {cmd:twoway lowess} including kinks at tied x-values.

{p 12 12 2}
{bf:Tied x-values and kinks:} When multiple observations share the same
x value (common with integer variables like {cmd:mpg}), Stata's {cmd:lowess}
computes a separate fitted value for each observation based on its index
position within the local bandwidth window. {cmd:twoway lowess} then connects
all fitted values in x-sorted order using a stable sort (ties keep original
observation order). Sparkta replicates this exactly using {cmd:sort xvar, stable}
before emitting all N fitted values -- producing identical kinks at tied
x-values. This was verified by overlaying Sparkta's output with {cmd:twoway lowess}
and confirming pixel-exact overlap including all kinks.

{p 12 12 2}
For interactive filter/slider redraws, an approximate JS lowess is used
with a 1,500-point cap for browser performance; the initial chart render
always uses the exact Stata computation.{p_end}

{p2col:{cmd:exp}}Exponential fit: y = a{c 183}exp(bx).
Fitted by log-linear OLS: {cmd:regress ln(y) x}, then back-transformed.
No direct Stata {cmd:twoway} equivalent. Requires y > 0 for all sample
observations; observations with y <= 0 are excluded from the fit.
Note: minimises sum of squared errors in ln(y), not y (Jensen's
inequality). For visualisation this is standard practice and matches
what most software (Excel, R geom_smooth) produces for exponential fits.{p_end}

{p2col:{cmd:log}}Logarithmic fit: y = a + b{c 183}ln(x).
Fitted by OLS after log-transforming x: {cmd:regress y ln(x)}.
No direct Stata {cmd:twoway} equivalent. Requires x > 0.{p_end}

{p2col:{cmd:power}}Power fit: y = a{c 183}x{c 94}b.
Fitted by log-log OLS: {cmd:regress ln(y) ln(x)}, then back-transformed.
No direct Stata {cmd:twoway} equivalent. Requires x > 0 and y > 0.
Same Jensen's inequality caveat as {cmd:exp}.{p_end}

{p2col:{cmd:ma}}Moving average. Window size = max(3, N/10), centred.
No Stata {cmd:twoway} equivalent. Deterministic (no estimation).
Computed in Java; output is exact and identical to any correct
centred moving average implementation.{p_end}
{p2colreset}

{p 4 4 2}
{bf:Comparison with Stata twoway:}

{p2colset 12 22 44 2}
{p2col:{it:Fit type}}{it:Stata equivalent}{p_end}
{p2col:{cmd:lfit}}{cmd:twoway lfit} -- EXACT MATCH{p_end}
{p2col:{cmd:qfit}}{cmd:twoway qfit} -- EXACT MATCH{p_end}
{p2col:{cmd:lowess}}{cmd:twoway lowess} -- EXACT MATCH (initial render){p_end}
{p2col:{cmd:exp}}No equivalent -- standard log-linear OLS{p_end}
{p2col:{cmd:log}}No equivalent -- OLS after ln(x){p_end}
{p2col:{cmd:power}}No equivalent -- log-log OLS{p_end}
{p2col:{cmd:ma}}No equivalent -- centred window mean{p_end}
{p2colreset}

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) fit(lfit)} -- linear fit{p_end}
{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) fit(lowess)} -- lowess smooth{p_end}
{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) fit(lfit) fitci} -- linear fit with 95%% CI band{p_end}

{p 4 4 2}
{bf:Lowess verification code} -- confirms sparkta output is identical to
Stata {cmd:twoway lowess} including kinks at tied x-values:

{p 8 8 2}{cmd:sysuse auto, clear}{p_end}
{p 8 8 2}{cmd:tempvar stata_fit sparkta_fit}{p_end}
{p 8 8 2}{cmd:quietly lowess price mpg, generate(`stata_fit') nograph}{p_end}
{p 8 8 2}{cmd:quietly sort mpg, stable}{p_end}
{p 8 8 2}{cmd:gen double sparkta_fit = `stata_fit'}{p_end}
{p 8 8 2}{cmd:gen double abs_diff = abs(sparkta_fit - `stata_fit')}{p_end}
{p 8 8 2}{cmd:summ abs_diff}{p_end}
{p 8 8 2}{it:(max abs_diff should be 0.000 -- confirms identical computation)}{p_end}
{p 8 8 2}{cmd:twoway (line `stata_fit' mpg, sort lcolor(red)) ///}{p_end}
{p 12 12 2}{cmd:(line sparkta_fit mpg, sort lcolor(blue) lpattern(dash)), ///}{p_end}
{p 12 12 2}{cmd:title("Lowess verification") note("Lines should overlap exactly")}{p_end}
{p 8 8 2}{it:(red Stata reference and blue dashed Sparkta output overlap exactly)}{p_end}

{phang}
{opt fitci} Adds a 95%% pointwise confidence band to the fit line.
Supported only for {cmd:fit(lfit)} and {cmd:fit(qfit)}.

{p 4 4 2}
{bf:Confidence interval computation:}

{p 8 8 2}
The CI is computed as: fitted(x) +/- t * SE(x), where:

{p2colset 12 22 22 2}
{p2col:SE(x)}{cmd:predict stdp} after {cmd:regress} -- the standard error
of the predicted mean at each x value.{p_end}
{p2col:t-critical}{cmd:invttail(e(df_r), 0.025)} -- Stata's exact
t-distribution with df = n - k (n observations, k parameters).{p_end}
{p2colreset}

{p 4 4 2}
This matches {cmd:twoway lfitci} and {cmd:twoway qfitci} exactly.

{p 4 4 2}
{bf:Behaviour with filters and sliders:}

{p2colset 12 26 26 2}
{p2col:{it:Scenario}}{it:Fit line}{p_end}
{p2col:Initial render}Exact Stata computation{p_end}
{p2col:Dropdown filter}Exact Stata computation (pre-computed per filter value){p_end}
{p2col:Slider interaction}Recomputed in browser using JS OLS; t-critical
matches Stata to 6 decimal places for all df{p_end}
{p2col:lowess after slider}Approximate; 1,500-point cap for browser performance{p_end}
{p2colreset}

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) fit(lfit) fitci filters(rep78)}{p_end}
{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) fit(qfit) fitci sliders(weight)}{p_end}

{phang}
{opt mlabel(varname[, all])} Labels each scatter point with the value of {it:varname}.
Applies to {cmd:type(scatter)} and {cmd:type(bubble)} only.

{p 8 8 2}
By default, labels are suppressed when the sample has more than 30 observations
to avoid overplotting. Specify {cmd:, all} to force labels regardless of N:
{cmd:mlabel(make, all)}.

{p 8 8 2}
The label value also appears as the first line of the hover tooltip for each point.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg if rep78==3, type(scatter) mlabel(make)}{p_end}
{p 8 8 2}{it:Example:} {cmd:sparkta price mpg, type(scatter) mlabel(make, all)}{p_end}

{phang}
{opt mlabpos(#)} Uniform label position for all points using the 0{hline 1}59
minute-clock convention: 0 = centered on point, 15 = right (3 o'clock),
30 = below (6 o'clock), 45 = left (9 o'clock), values approaching 60 = above.
Default is above (top). Requires {cmd:mlabel()} to also be specified.

{p 8 8 2}
This uses the same convention as {cmd:alabelpos()} for annotation labels.
Note: the values differ from Stata's {cmd:mlabpos()} option (which uses a
1{hline 1}12 clock face) -- sparkta uses a 0{hline 1}59 minute scale for
consistency across all positioning options.

{p 8 8 2}{it:Example:} {cmd:sparkta price mpg if rep78==3, type(scatter) mlabel(make) mlabpos(15)} -- labels to the right{p_end}
{p 8 8 2}{it:Example:} {cmd:sparkta price mpg if rep78==3, type(scatter) mlabel(make) mlabpos(30)} -- labels below{p_end}

{phang}
{opt mlabvposition(varname)} Per-observation label position.
{it:varname} must be a numeric variable containing values 0{hline 1}59 (minute-clock).
Requires {cmd:mlabel()} to also be specified.
Useful when labels would otherwise overlap -- set the position variable so
crowded labels are directed away from each other.

{p 8 8 2}{it:Example:}{p_end}
{p 12 12 2}{cmd:gen mypos = 15}{p_end}
{p 12 12 2}{cmd:replace mypos = 30 if price > 8000}{p_end}
{p 12 12 2}{cmd:sparkta price mpg, type(scatter) mlabel(make, all) mlabvposition(mypos)}{p_end}

{dlgtab:Histogram}

{phang}
{opt bins(#)} Number of bins. Must be an integer of 2 or greater. If omitted,
bins are determined by Sturges' rule: {cmd:ceil(log2(n) + 1)}, clamped to [5, 50].
Start with the default; fewer bins reveal broad shape, more reveal fine structure.

{p 8 8 2}{it:Example:} {cmd:sparkta price, type(histogram) bins(20)}{p_end}

{phang}
{opt histtype(string)} Y-axis metric.

{p2colset 12 26 26 2}
{p2col:{cmd:density}}(default) count / (n * binWidth). Area sums to 1.
Matches Stata {cmd:twoway histogram} default.{p_end}
{p2col:{cmd:frequency}}Raw observation count per bin.{p_end}
{p2col:{cmd:fraction}}Proportion of observations: count / n.{p_end}
{p2colreset}

{p 8 8 2}{it:Examples:}{p_end}
{p 12 12 2}{cmd:sparkta price, type(histogram) histtype(density)} -- area sums to 1, comparable across groups{p_end}
{p 12 12 2}{cmd:sparkta price, type(histogram) histtype(frequency)} -- raw counts, easiest to interpret{p_end}

{p 8 8 2}
{cmd:histogram} does not support {cmd:over()}. Use {cmd:by()} to produce
separate histograms per group.

{dlgtab:Box and Violin Plots}

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
a dark marker.

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
{it:h} = 0.9 * min(SD, IQR/1.34) * n^(-1/5).

{p 8 8 2}
Example: {cmd:bandwidth(2000)} for a price variable measured in dollars.

{p 8 8 2}
{bf:Legend.} Both chart types display an inline canvas legend in the top-right
corner of the chart area. For {cmd:boxplot}: Median, Mean, IQR Box, Whiskers,
and Outlier symbols. For {cmd:violin}: Median, Mean, IQR Box, Whiskers, and
KDE Shape symbols.

{p 8 8 2}
{bf:Violin animation.} Violin charts animate on load (shapes grow in from flat)
and on filter change (shapes tween smoothly to the new distribution). This
animation is driven by a custom requestAnimationFrame loop and is not affected
by the {cmd:animate()} option.

{p 8 8 2}
{bf:Statistical formulas.} All statistics (Q1, Q3, median, mean, whiskers)
match Stata {cmd:summarize, detail} output exactly, using the formula
h = (n+1)*p/100 with linear interpolation between adjacent order statistics.

{dlgtab:Offline Mode}

{phang}
{opt offline} Embed all JavaScript libraries directly inside the HTML file
so the output requires no internet connection to open.

{p 8 8 2}
{bf:How it works.} By default, {cmd:sparkta} generates a compact HTML file
that loads Chart.js from a CDN when opened in a browser. With {cmd:offline},
the entire Chart.js library and all plugins are embedded inside the HTML at
generation time. The result is a single file that renders correctly on any
machine, anywhere, with no network request of any kind.

{p 8 8 2}
{bf:Data privacy and confidentiality.} When your chart contains sensitive or
restricted data, the {cmd:offline} option gives you full control. No data ever
leaves the HTML file, no CDN is contacted when the file opens, and the chart
can be shared, archived, or presented on a machine with no internet access.
Particularly recommended for clinical, financial, or institutional data where
external network requests must be avoided.

{p 8 8 2}
{bf:Reproducibility.} An offline HTML file is a permanent, self-contained
snapshot of both the chart rendering engine and the data at the time of
generation. It will render identically in any browser years from now,
independent of CDN availability, library version changes, or network conditions.
Suitable for inclusion in reproducibility archives and supplementary materials.

{p 8 8 2}
{bf:File size.} The offline HTML is larger (~250{hline 1}320 KB vs ~50 KB
for an online file) but functionally identical in all other respects.

{p 8 8 2}
{bf:Requirement.} The JS libraries must be bundled in {cmd:sparkta.jar}
at compile time. Run {cmd:fetch_js_libs.sh} (Unix/Mac) or
{cmd:fetch_js_libs.bat} (Windows) from the {cmd:java/} folder, then
recompile. See the repository README for step-by-step instructions.

{dlgtab:Data Handling}

{phang}
{opt download} Show a PNG download button in the top-right corner of the chart.
Clicking it saves the chart as a {cmd:.png} image file. Works in all modern browsers.

{phang}
{opt nomissing} Ensures observations with missing values in the outcome
variable or grouping variables are excluded before chart building.

{p 8 8 2}For most chart types (bar, line, area, scatter, histogram, boxplot,
violin), missing outcome values are already excluded silently from all
aggregation and computations -- {cmd:nomissing} has no additional visible
effect on these charts.

{p 8 8 2}The option is most meaningful for {cmd:type(cibar)} and
{cmd:type(ciline)}, where the underlying Chart.js error-bars plugin cannot
handle null data points and will fail to render if missing values reach the
chart. Specifying {cmd:nomissing} guarantees a clean, null-free dataset
reaches the renderer.

{p 8 8 2}{it:Example:} {cmd:sparkta wage, type(cibar) over(race) nomissing}
-- recommended practice for all CI charts where the outcome may have missing
values.

{phang}
{opt novaluelabels} Display raw numeric codes instead of value labels
for {cmd:over()} and {cmd:by()} group names.
Useful when value labels are long or when you need to display the underlying codes.

{p 8 8 2}{it:Example:} {cmd:sparkta price, over(rep78) novaluelabels} -- shows "1 2 3 4 5" instead of value label text.{p_end}

{title:Summary Statistics Panel}

{p 4 4 2}
Every {cmd:sparkta} chart includes a collapsible statistics panel below
the chart showing one table per group (Overall, and each {cmd:over()} or
{cmd:by()} group).

{p2colset 8 22 22 2}
{p2col:{bf:N}}Non-missing observation count{p_end}
{p2col:{bf:Mean}}Arithmetic mean{p_end}
{p2col:{bf:Median}}50th percentile (matches Stata {cmd:summarize, detail}){p_end}
{p2col:{bf:Min / Max}}Minimum and maximum{p_end}
{p2col:{bf:Std Dev}}Sample SD with N-1 denominator (matches {cmd:summarize}){p_end}
{p2col:{bf:CV badge}}Coefficient of variation: Low (<15%%) Med (15{hline 1}35%%) High (>35%%){p_end}
{p2col:{bf:Distribution}}Sparkline with IQR box, mean/median lines, outlier dots{p_end}
{p2colreset}

{p 4 4 2}
{bf:Live filter updates:} When {cmd:filters()} or {cmd:sliders()} are active,
the statistics panel updates automatically whenever the viewer changes a filter.
N, Mean, Median, Min, Max, SD, and CV all recompute on the filtered observations.
The IQR box and mean/median lines in the sparkline distribution also reposition
to reflect the filtered data. When {cmd:by()} is used, both the chart panels
and the statistics update together -- the Domestic panel shows statistics only
for filtered Domestic observations, and so on.

{p 4 4 2}
Click any column header to sort. Use chip buttons to show or hide columns.
Click a group heading to collapse or expand it.
Use {cmd:nostats} to suppress the panel entirely.

{title:Memory and Large Datasets}

{p 4 4 2}
{cmd:sparkta} reads observations directly from Stata memory and is not
limited by macro string length. Practical limits depend on available Java
heap memory. Default heap sizes and suggested fixes:

{p2colset 8 24 24 2}
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

{title:Examples}

{p 4 4 2}
All examples use the built-in {cmd:auto} dataset (1978 automobile data,
74 observations). Run {cmd:sysuse auto, clear} before any of these.
Charts open in your default browser unless {cmd:export()} is specified.

{pstd}Load example data:{p_end}
{phang2}{cmd:. sysuse auto, clear}{p_end}

{pstd}{bf:Start here -- basic bar charts}{p_end}
{phang2}{cmd:. sparkta price, type(bar) over(rep78)}{p_end}
{phang2}{cmd:. sparkta price, type(bar) over(rep78) title(Mean Price by Repair Record)}{p_end}
{phang2}{cmd:. sparkta price weight, type(bar) over(rep78) stat(mean) datalabels}{p_end}
{phang2}{cmd:. sparkta price weight, type(hbar) over(foreign)}{p_end}
{phang2}{cmd:. sparkta price weight, type(stackedbar) over(rep78)}{p_end}

{pstd}{bf:Stacked bars}{p_end}
{phang2}{cmd:. sparkta price weight, type(stackedbar) over(rep78)}{p_end}
{phang2}{cmd:. sparkta price weight, type(stackedhbar) over(foreign)}{p_end}

{pstd}{bf:100%% stacked bars -- composition across groups}{p_end}
{phang2}{cmd:. sparkta price weight, type(stackedbar100) over(rep78)}{p_end}
{phang2}{cmd:. sparkta price weight mpg, type(stackedbar100) over(foreign) stat(sum)}{p_end}

{pstd}{bf:Bar charts without over() -- one bar per variable}{p_end}
{phang2}{cmd:. sparkta price weight, type(bar)}{p_end}
{phang2}{cmd:. sparkta price weight length, type(bar) stat(median)}{p_end}

{pstd}{bf:Line and area}{p_end}
{phang2}{cmd:. sparkta price, type(area) over(rep78) smooth(0.5)}{p_end}
{phang2}{cmd:. sparkta price, type(line) over(rep78) stepped(after)}{p_end}
{phang2}{cmd:. sparkta price weight, type(stackedarea) over(foreign)}{p_end}

{pstd}{bf:Scatter and bubble (y variable listed first)}{p_end}
{phang2}{cmd:. sparkta price mpg, type(scatter)}{p_end}
{phang2}{cmd:. sparkta price mpg, type(scatter) over(foreign)}{p_end}
{phang2}{cmd:. sparkta price mpg weight, type(bubble) over(foreign)}{p_end}

{pstd}{bf:Pie and donut}{p_end}
{phang2}{cmd:. sparkta price, type(pie) over(rep78) pielabels}{p_end}
{phang2}{cmd:. sparkta price, type(donut) over(rep78) cutout(70) rotation(-90)}{p_end}
{phang2}{cmd:. sparkta price, type(donut) over(foreign) stat(sum) pielabels}{p_end}

{pstd}{bf:CI charts}{p_end}
{phang2}{cmd:. sparkta price, type(cibar) over(rep78)}{p_end}
{phang2}{cmd:. sparkta price, type(cibar) over(rep78) cilevel(90)}{p_end}
{phang2}{cmd:. sparkta price weight, type(cibar) over(foreign) cilevel(99)}{p_end}
{phang2}{cmd:. sparkta price, type(ciline) over(rep78) cibandopacity(0.15)}{p_end}
{phang2}{cmd:. sparkta price weight, type(ciline) over(foreign) theme(dark) cilevel(95)}{p_end}

{pstd}{bf:Histograms}{p_end}
{phang2}{cmd:. sparkta price, type(histogram)}{p_end}
{phang2}{cmd:. sparkta price, type(histogram) bins(15)}{p_end}
{phang2}{cmd:. sparkta price, type(histogram) histtype(frequency)}{p_end}
{phang2}{cmd:. sparkta price, type(histogram) histtype(fraction) filters(foreign)}{p_end}
{phang2}{cmd:. sparkta price, type(histogram) by(foreign) layout(grid)}{p_end}

{pstd}{bf:Panels and filters}{p_end}
{phang2}{cmd:. sparkta price weight, type(bar) by(foreign) layout(grid)}{p_end}
{phang2}{cmd:. sparkta price weight, type(bar) over(foreign) filters(rep78)}{p_end}
{phang2}{cmd:. sparkta price, type(bar) over(rep78) filters(foreign) sortgroups(desc)}{p_end}
{phang2}{cmd:. sparkta price, type(violin) over(rep78) filters(foreign)}{p_end}
{phang2}{cmd:. sparkta price mpg, type(bar) over(rep78) filters(foreign) sliders(price)}{p_end}

{pstd}{bf:Box and violin plots}{p_end}
{phang2}{cmd:. sparkta price, type(boxplot) over(rep78)}{p_end}
{phang2}{cmd:. sparkta price, type(boxplot) over(rep78) whiskerfence(3)}{p_end}
{phang2}{cmd:. sparkta price weight mpg, type(boxplot)}{p_end}
{phang2}{cmd:. sparkta price, type(hbox) over(rep78)}{p_end}
{phang2}{cmd:. sparkta price, type(violin) over(rep78)}{p_end}
{phang2}{cmd:. sparkta price, type(hviolin) over(rep78)}{p_end}
{phang2}{cmd:. sparkta price, type(violin) over(rep78) bandwidth(2000)}{p_end}
{phang2}{cmd:. sparkta price, type(boxplot) over(rep78) mediancolor(#e74c3c) meancolor(#2980b9)}{p_end}

{pstd}{bf:Secondary y-axis (dual scale)}{p_end}
{phang2}{cmd:. sparkta price mpg, type(line) over(foreign) y2(mpg) y2title("MPG")}{p_end}
{phang2}{cmd:. sparkta price weight, type(bar) over(rep78) y2(weight) y2title("Weight (lbs)")}{p_end}

{pstd}{bf:Typography and tooltip styling (v2.6.0)}{p_end}
{phang2}{cmd:. sparkta price weight, type(bar) over(foreign) ///}{p_end}
{phang2}{cmd:    titlesize(28) titlecolor(#2c3e50) ///}{p_end}
{phang2}{cmd:    xtitlesize(13) xtitlecolor(#555) ///}{p_end}
{phang2}{cmd:    xlabsize(11) xlabcolor(#888) ///}{p_end}
{phang2}{cmd:    legcolor(#333) legbgcolor(rgba(255,255,255,0.9))}{p_end}

{phang2}{cmd:. sparkta price, type(cibar) over(rep78) ///}{p_end}
{phang2}{cmd:    tooltipbg(rgba(0,0,0,0.9)) tooltipborder(#4e79a7) ///}{p_end}
{phang2}{cmd:    tooltipfontsize(13) tooltippadding(12)}{p_end}

{pstd}{bf:Offline mode -- self-contained file, no internet required}{p_end}
{phang2}{cmd:. sparkta price weight, type(bar) over(foreign) offline ///}{p_end}
{phang2}{cmd:    export("~/Downloads/chart_offline.html")}{p_end}
{phang2}{cmd:. sparkta price, type(cibar) over(rep78) cilevel(95) offline ///}{p_end}
{phang2}{cmd:    title("Confidential -- Offline Export") ///}{p_end}
{phang2}{cmd:    export("~/Desktop/ci_offline.html")}{p_end}

{pstd}{bf:Full showcase example}{p_end}
{phang2}{cmd:. sparkta price weight, type(bar) over(foreign) ///}{p_end}
{phang2}{cmd:    sortgroups(asc) filters(rep78) theme(dark) ///}{p_end}
{phang2}{cmd:    title("Price and Weight by Car Origin") ///}{p_end}
{phang2}{cmd:    subtitle("1978 Automobile Data") ///}{p_end}
{phang2}{cmd:    titlesize(26) titlecolor(#ecf0f1) ///}{p_end}
{phang2}{cmd:    colors("#4e79a7 #f28e2b") borderradius(4) barwidth(0.6) ///}{p_end}
{phang2}{cmd:    legend(bottom) legtitle("Origin") legcolor(#ecf0f1) ///}{p_end}
{phang2}{cmd:    ytitle("Mean Value") animate(fast) ///}{p_end}
{phang2}{cmd:    tooltipformat(",.0f") tooltipbg(rgba(0,0,0,0.85)) ///}{p_end}
{phang2}{cmd:    export("~/Desktop/dashboard.html")}{p_end}

{title:Utility Command}

{phang}
{cmd:. sysuse auto, clear}
{cmd:. sparkta price, over(foreign)}

{p 4 4 2}
Verifies the installation and displays the full resolved option list.
Reports the location of {cmd:sparkta.jar} if found. Run after installing
or upgrading to confirm everything is wired correctly.

{title:Known Limitations}

{p 4 4 2}
{bf:Stata 17 or later is required.} Running sparkta on Stata 16 or earlier
will produce an error before any chart is built.

{p 4 4 2}
{bf:Filter updates for histogram, boxplot/violin, CI charts, stacked charts,
pie, and donut} are not yet supported. The chart will render correctly at
page load but will not respond to filter dropdown changes for these chart
types. Bar, line, area, and scatter charts support full live filtering.

{p 4 4 2}
Pie and donut charts require exactly one numeric variable and {cmd:over()}.

{p 4 4 2}
Bubble charts require exactly three variables: y, x, and size (in that order).

{p 4 4 2}
{cmd:over()} and {cmd:by()} cannot be used together with {cmd:pie} or {cmd:donut}.
For all other chart types they can be combined: {cmd:by()} creates panels and
{cmd:over()} groups series within each panel.

{p 4 4 2}
{cmd:histogram} does not support {cmd:over()}. Use {cmd:by()} for separate
histograms per group.

{p 4 4 2}
{cmd:cibar} and {cmd:ciline} require {cmd:over()}. Groups with fewer than
2 observations are omitted from CI charts.

{p 4 4 2}
{cmd:ytype(logarithmic)} is incompatible with {cmd:ystart(zero)}.

{p 4 4 2}
{cmd:set java_heapmax} requires a Stata restart to take effect.

{p 4 4 2}
Without the {cmd:offline} option, the HTML file requires an internet connection
to render (Chart.js loaded from CDN). Use {cmd:offline} for air-gapped use.

{title:Version History}

{p2colset 6 16 16 2}
{p2col:{bf:v3.5.108}}Documentation and version consolidation. No code changes. Comprehensive
regression test suite (155 cases) confirmed all chart types, options, and filter
interactions working correctly. All tests pass.{p_end}

{p2col:{bf:v3.5.108}}Renamed {cmd:alabelfontsize()} to {cmd:alabelfs()} to resolve a
Stata option parser conflict on Windows. The original 14-character name caused
Stata to emit "option alabelfontsize() not allowed" despite the option being correctly
declared in the syntax block. Root cause unclear (possibly Stata internal limit on
option name length, or conflict with an undocumented built-in). The 8-character
replacement {cmd:alabelfs()} works correctly. Also converted ado file to CRLF
line endings for Windows compatibility. No Java recompile needed.{p_end}

{p2col:{bf:v3.5.94}}Converted {cmd:sparkta.ado} to Windows CRLF line endings.
Previous LF-only endings may have caused the Stata syntax parser to misparse
long multi-line {cmd:syntax} blocks on Windows. No code changes.{p_end}

{p2col:{bf:v3.5.93}}Comprehensive option abbreviation audit. No real conflicts found
(Stata matching requires input between min-abbrev and full option name). Several
option capitalisation adjustments applied as preventive measure.{p_end}

{p2col:{bf:v3.5.92}}Code quality pass (Opus review #1,#2,#5,#7,#10b). No behaviour
change. {cmd:buildChartDataFromRows()} added to engine (eliminates double
{cmd:filterRows()} call). T95 lookup table hoisted to IIFE scope. Sparkline legend
emitted once per panel not once per group. O(N) bucket pass in stats badge update.
Lowess weight variable renamed {cmd:w}->{cmd:wt}. sthlp: full methodology section
with statistical citations added.{p_end}

{p2colset 6 16 16 2}


{p2col:{bf:v3.5.91}}Fix {cmd:_sparkta_updateStatsBadges} never closing due to missing
closing brace: helper functions (_fmtStat, _cvClass, _updateSpk, _updateStatsGroup) were
trapped inside the outer function, making them unreachable from {cmd:_onFilterChange}.
Rewritten to emit helpers first, main function last, with an unconditional close.
Also fix: {cmd:var rows} undefined in bar/line {cmd:_onFilterChange} path -- added
{cmd:filterRows()} call before {cmd:buildChartData()} so badge update has rows in scope.{p_end}

{p2col:{bf:v3.5.89}}Fix {cmd:querySelector('#g0 .grp-badge')} targeting wrong element:
{cmd:id='g0'} is on the table content div, not the header. Badge spans now have
{cmd:id='sbadge_N'} (added to StatsRenderer) and JS uses {cmd:getElementById}.{p_end}

{p2col:{bf:v3.5.88}}Fix {cmd:spkId()} called as method instead of {cmd:spkId.apply()}:
{cmd:java.util.function.Function} requires {cmd:.apply()} syntax in Java 11.{p_end}

{p2col:{bf:v3.5.87}}F-2A: Full stats table live update on filter change. All stat cells
(N, Mean, Median, Min, Max, SD, CV badge) now update when a filter dropdown changes.
Sparkline IQR box and mean/median lines reposition to reflect filtered distribution.
New engine function {cmd:buildGroupStats(rows, plotVar)} computes all stats in O(N).
New {cmd:_updateStatsGroup(gi, rows, plotVars)} JS function updates cells by ID.
Cell IDs added to StatsRenderer: {cmd:stc_{g}_{v}_{col}}; sparkline IDs: {cmd:spk_{g}_{v}_{el}}.
Java recompile required.{p_end}

{p2col:{bf:v3.5.86}}Fix stats badge update: {cmd:querySelector('#g0 .grp-badge')} returned
null because {cmd:id='g0'} is on the content div not the header. Added {cmd:id='sbadge_N'}
to each badge span.{p_end}

{p2col:{bf:v3.5.85}}Fix bars disappearing after filter in bar+{cmd:over()}+{cmd:by()} panels:
{cmd:_updatePanelChart} was treating single-dataset colorByCategory charts as multi-dataset.
Fixed: detect {cmd:dsets.length === 1} and assign full data array directly.{p_end}

{p2col:{bf:v3.5.84}}F-2B: Stats panel N badges now update live when filter changes.
F-3: {cmd:by()} panel charts now update independently on filter change.
{cmd:_smeta} gains {cmd:byGroups} and {cmd:chartType} fields.
{cmd:buildFilterScriptByPanels()} fully implemented (was stub). Java recompile required.{p_end}

{p2col:{bf:v3.5.34}}Fix {cmd:stackedhbar100} tooltip showing 0%% share.
With {cmd:indexAxis:'y'}, Chart.js puts the numeric value in
{cmd:ctx.parsed.x} not {cmd:ctx.parsed.y}. Stack100 tooltip callback
now uses {cmd:ctx.parsed.x} when horizontal, {cmd:ctx.parsed.y} otherwise.
Java recompile required.{p_end}
{p2col:{bf:v3.5.34}}Fix {cmd:stackedhbar100} bars rendering at zero.
Chart.js 4 misdetects the category axis as linear when {cmd:stacked:true}
is also present, placing all bars at position 0. Fix: explicitly emit
{cmd:type:'category'} on the category axis when {cmd:stack100=true}.
Applies to both {cmd:stackedbar100} (x-axis) and {cmd:stackedhbar100}
(y-axis). Java recompile required.{p_end}
{p2col:{bf:v3.5.34}}Fix {cmd:stackedhbar100} tooltip showing 0%% share.
With {cmd:indexAxis:'y'}, Chart.js puts the numeric value in
{cmd:ctx.parsed.x} not {cmd:ctx.parsed.y}. Stack100 tooltip callback
now uses {cmd:ctx.parsed.x} when horizontal, {cmd:ctx.parsed.y} otherwise.
Java recompile required.{p_end}
{p2col:{bf:v3.5.56}}Documentation-only. Two updates:
(1) Clarified {cmd:nomissing} semantics: no-op for bar/line/area/scatter/histogram/
boxplot/violin; meaningful only for {cmd:cibar}/{cmd:ciline} where null data causes
a rendering failure.
(2) Updated all {cmd:filter()}/{cmd:filter2()} references in help file to
{cmd:filters()} (F-0 unlimited filter engine) and added full documentation for
{cmd:filters()} and {cmd:sliders()} including synopt entries, Options blocks,
and examples. No code change, no Java recompile needed.{p_end}
{p2col:{bf:v3.5.42}}Jar search reverted to {cmd:findfile} as primary lookup.
{cmd:c(source)} referenced in v3.5.40 does not exist in Stata -- removed.
Added {cmd:sysdir_personal} forward-slash variant for Mac/Linux and explicit
{cmd:sysdir_PLUS/s/sparkta/} fallback. Ado-only, no recompile needed.{p_end}
{p2col:{bf:v3.5.40}}Jar search: {cmd:c(source)} (the currently executing .ado path)
is now the primary lookup, replacing {cmd:findfile} as primary. {cmd:c(source)} is
unambiguous -- it always points to the running .ado regardless of adopath order.
Closes edge case where old manual copy in {cmd:personal/} could shadow
{cmd:ssc}-installed copy in {cmd:PLUS/}. Ado-only, no recompile needed.{p_end}
{p2col:{bf:v3.5.39}}Robust jar search: {cmd:findfile("sparkta.ado")} is now the
primary lookup, finding {cmd:sparkta.jar} wherever Stata installed the ado file
(SSC {cmd:PLUS/s/sparkta/}, {cmd:net install personal/}, or any adopath location).
Added {cmd:sysdir_PLUS} subfolder fallback and Mac/Linux forward-slash
{cmd:sysdir_personal} variants. Removed hardcoded {cmd:C:\ado\personal} path.
Ado-only, no recompile needed.{p_end}
{p2col:{bf:v3.5.38}}Documentation fix: {cmd:xticks()} detail block in Options section was missing
its opening {cmd:{phang}} and {cmd:{opt xticks(list)}} line -- the block began mid-sentence
("supplied as a pipe-separated list...") with no heading.
Fixed by restoring the correct opener. Sthlp-only change; no recompile needed.{p_end}
{p2col:{bf:v3.5.37}}Code-quality cleanup (no behaviour change, no recompile needed for most fixes):
A1: {cmd:hasAnnotations()} moved to {cmd:DashboardOptions} -- single source of truth used by both
{cmd:DashboardBuilder} (offline preflight) and {cmd:HtmlGenerator} (CDN inclusion).
A2: Fix grid colour and download button colours on compound dark themes (e.g. {cmd:theme(dark_viridis)})
-- {cmd:isDark()} was not called in two {cmd:HtmlGenerator} methods.
B5: {cmd:uniqueValues()}/{cmd:uniqueGroupKeys()} dedup now O(1) via {cmd:LinkedHashMap}/{cmd:LinkedHashSet}
instead of O(N^2) {cmd:ArrayList.contains()} -- matters for high-cardinality {cmd:by()} variables.
Also: dead field {cmd:_colorByCatLegend} removed; dead {cmd:readVariable} overload removed;
arg 83-86 wiring moved to correct sequential position; duplicate comment removed;
{cmd:DashboardOptions} defaults aligned with effective runtime values;
{cmd:check_build.py} now cross-checks {cmd:HtmlGenerator.VERSION} vs {cmd:sparkta.ado} version.
Java recompile required for A1, A2, B5.{p_end}
{p2col:{bf:v3.5.36}}Documentation: {cmd:relabel()} and {cmd:nolegend} added to
help file with full cross-chart behavior table and worked examples.
Ado-only, no recompile needed.{p_end}
{p2col:{bf:v3.5.36}}Fix {cmd:leglabels()} in bar+{cmd:over()}+single variable mode: per-bar colored legend items now appear with custom labels. Also fix: HTML output version comment was frozen at v3.5.21 -- now reads correct jar version via {bf:HtmlGenerator.VERSION} constant. Java recompile required.{p_end}

{p2col:{bf:v3.5.34}}Fix {cmd:relabel()} legend in colorByCategory mode (single-var
bar/hbar + {cmd:over()}): Chart.js {cmd:generateLabels} now builds N colored
swatches instead of one when {cmd:backgroundColor} is a per-bar array.
New bare flag {cmd:nolegend}: alias for {cmd:legend(none)}.
Java recompile required.{p_end}
{p2col:{bf:v3.5.33}}New option {cmd:relabel()}: renames {cmd:over()} group labels on
both the x-axis and legend simultaneously (arg 150).
Mirrors Stata's {cmd:over(var, relabel(...))} with {cmd:asyvars showyvars}.
Internal fix: local {cmd:relabel} conflicted with built-in Stata command name;
resolved by copying to private local {cmd:_rlbl} after syntax block.{p_end}
{p2col:{bf:v3.5.34}}Fix all remaining option abbreviation conflicts (ado-only).
Comprehensive audit of all 142 options. Conflicts fixed:
{cmd:FILTEr}/{cmd:FILTER2} (filter vs filter2),
{cmd:LPATTErn}/{cmd:LPATTERns} (lpattern vs lpatterns),
{cmd:ANIMDElay}/{cmd:ANIMDUration} (animdelay vs animduration),
{cmd:TOOLTIPBOrder} (tooltipbg vs tooltipborder).
Zero conflicts confirmed across all options.{p_end}
{p2col:{bf:v3.5.11}}Fix tooltip option abbreviation conflicts.
{cmd:TOOLTIPFORmat}, {cmd:TOOLTIPFONtsize}, {cmd:TOOLTIPPOSition}, and
{cmd:TOOLTIPPADding} now have distinct minimum abbreviations, resolving
the {it:option not allowed} error when using {cmd:tooltipfontsize()} or
{cmd:tooltippadding()} together with {cmd:tooltipformat()} or
{cmd:tooltipposition()}. Ado-only fix, no recompile needed.{p_end}
{p2col:{bf:v3.5.10}}Auto axis titles from variable label or name (Stata convention).
{cmd:bar}, {cmd:hbar}, {cmd:line}, {cmd:area} (and stacked variants), {cmd:boxplot},
{cmd:violin}, {cmd:hbox}, {cmd:hviolin}: when {cmd:xtitle()} or {cmd:ytitle()} are
omitted, sparkta now falls back to the variable label (if set) or variable name,
matching the behaviour of Stata's own {cmd:graph} command.
User-supplied {cmd:xtitle()}/{cmd:ytitle()} always take priority.{p_end}
{p2col:{bf:v3.5.9}}String {cmd:over()}/{cmd:by()}/{cmd:filter()} support.
{cmd:markout} now uses the {cmd:strok} option for string variables, preventing
all observations from being silently dropped when an {cmd:over()} or {cmd:by()}
variable is a string type. {cmd:filter()} and {cmd:filter2()} receive the same
fix. O(N x G) to O(N) group-index optimisation (80%% speed gain on large datasets).
Bulk column read via reflection for numeric variables (Stata 17+).
Lazy sparkline rendering with IntersectionObserver (500-group charts drop from
approx 40 MB to 2-3 MB).{p_end}
{p2col:{bf:v3.5.7}}{cmd:stackedhbar}: stacked horizontal bar chart (non-100%%).
Fills the symmetry gap between {cmd:stackedbar} and {cmd:stackedhbar100}.{p_end}
{p2col:{bf:v3.5.8}}Minute-clock label positioning: {cmd:alabelpos()} now accepts
optional third token per entry specifying direction as minutes on a clock face
(0=center, 15=right, 30=below, 45=left). New {cmd:alabelgap()} option sets offset
distance in pixels (arg 149). Matches Stata {cmd:mlabpos(0)} semantics exactly.{p_end}
{p2col:{bf:v3.5.2}}Validation fix: {cmd:apointsize()}, {cmd:pointhoversize()},
{cmd:alabelfs()} now use {cmd:real()} instead of {cmd:confirm number}
(string-typed options in ado require {cmd:real()} for numeric validation).
Min-abbrev conflicts in 13 annotation options resolved by capitalisation in
syntax block.{p_end}
{p2col:{bf:v3.5.1}}Annotation placement fix: all annotation config now correctly
placed inside {cmd:options.plugins.annotation} (was incorrectly placed at
{cmd:options.annotation}, silently ignored by Chart.js). Histogram {cmd:xline}
and {cmd:xband} fixed to use fractional bin-index interpolation for correct
placement on category axis.{p_end}
{p2col:{bf:v3.5.0}}Phase 2-B: 19 reference annotation options.
Lines: {cmd:yline()}, {cmd:xline()}, {cmd:ylinecolor()}, {cmd:xlinecolor()},
{cmd:ylinelabel()}, {cmd:xlinelabel()}.
Bands: {cmd:yband()}, {cmd:xband()}, {cmd:ybandcolor()}, {cmd:xbandcolor()}.
Points: {cmd:apoint()}, {cmd:apointcolor()}, {cmd:apointsize()}.
Labels: {cmd:alabelpos()}, {cmd:alabeltext()}, {cmd:alabelfs()}.
Ellipses: {cmd:aellipse()}, {cmd:aellipsecolor()}, {cmd:aellipseborder()}.
CDN lib 5: chartjs-plugin-annotation@3.0.1.{p_end}
{p2col:{bf:v3.4.1}}Color-consistency fix for single-var bar + {cmd:over()} + {cmd:by()}
panels: globally consistent palette indices across all by-panels.{p_end}
{p2col:{bf:v3.4.0}}Phase 2-C: {cmd:leglabels()} renames legend entries;
{cmd:xticks()} and {cmd:yticks()} pin custom axis tick positions.{p_end}
{p2col:{bf:v3.3.1}}Compound themes: {cmd:dark_palette} and {cmd:light_palette},
e.g. {cmd:dark_viridis}, {cmd:light_tab1}.{p_end}
{p2col:{bf:v3.3.0}}Phase 2-D: 7 named color palettes via {cmd:theme()}:
{cmd:tab1}, {cmd:tab2}, {cmd:tab3}, {cmd:cblind1}, {cmd:neon},
{cmd:swift_red}, {cmd:viridis}.{p_end}
{p2col:{bf:v3.2.0}}Phase 1-E: {cmd:notesize()}, {cmd:gradient}, {cmd:gradcolors()}.
Gradient fill for area and bar charts with per-series color support.{p_end}
{p2col:{bf:v3.1.0}}Phase 1-D: {cmd:lpattern()}, {cmd:lpatterns()},
{cmd:nopoints}, {cmd:pointhoversize()}.{p_end}
{p2col:{bf:v3.0.3}}Phase 1-C: {cmd:yreverse}, {cmd:xreverse}, {cmd:noticks},
{cmd:ygrace()}, {cmd:animduration()}. SVG export removed.{p_end}
{p2col:{bf:v2.7.0}}Phase 2-A: PNG download button ({cmd:export} button in chart header).{p_end}
{p2col:{bf:v2.6.1}}Histogram tooltip shows per-bin observation count.
Histogram {cmd:by()} panel tooltip fix. Boxplot multi-variable centering fix.{p_end}
{p2col:{bf:v2.6.0}}Phase 1-A/1-B: 18 font/color/tooltip styling options
({cmd:titlesize}, {cmd:titlecolor}, {cmd:subtitlesize}, {cmd:subtitlecolor},
{cmd:xtitlesize}, {cmd:xtitlecolor}, {cmd:ytitlesize}, {cmd:ytitlecolor},
{cmd:xlabsize}, {cmd:xlabcolor}, {cmd:ylabsize}, {cmd:ylabcolor},
{cmd:legcolor}, {cmd:legbgcolor}, {cmd:tooltipbg}, {cmd:tooltipborder},
{cmd:tooltipfontsize}, {cmd:tooltippadding}).{p_end}
{p2col:{bf:v2.5.1}}Violin filter animation: smooth tween on filter change,
grow-in on load.{p_end}
{p2col:{bf:v2.5.0}}Custom violin chart: pure Java KDE (Gaussian kernel, Silverman
bandwidth). Inline legend and custom tooltip.{p_end}
{p2col:{bf:v2.4.10}}Boxplot alignment, consistent median/mean colors by luminance.{p_end}
{p2col:{bf:v2.4.7}}Hollow outlier dots. {cmd:hbox} and {cmd:hviolin} aliases.{p_end}
{p2col:{bf:v2.4.0}}Boxplot and violin chart types. Tukey fences, {cmd:whiskerfence()}.{p_end}
{p2col:{bf:v2.3.0}}Secondary y-axis: {cmd:y2()}, {cmd:y2title()}, {cmd:y2range()}.{p_end}
{p2col:{bf:v2.2.0}}100%% stacked bars: {cmd:stackedbar100}, {cmd:stackedhbar100}.{p_end}
{p2col:{bf:v2.1.0}}Tooltip redesign: structured multi-line, {cmd:tooltipformat()}.{p_end}
{p2col:{bf:v2.0.8}}Package renamed from {cmd:dashboard} to {cmd:sparkta}.{p_end}
{p2col:{bf:v2.0.2}}{cmd:offline} option: fully self-contained HTML.{p_end}
{p2col:{bf:v1.8.8}}Missing values in grouping variables auto-excluded.{p_end}
{p2col:{bf:v1.8.0}}{cmd:histogram} chart type.{p_end}
{p2col:{bf:v1.7.0}}{cmd:cibar} and {cmd:ciline} with t-distribution CIs.{p_end}
{p2col:{bf:v1.6.0}}Removed ~13K observation limit. Direct Stata memory read.{p_end}
{p2col:{bf:v1.5.0}}Stats panel redesign: sortable columns, sparkline, CV badge.{p_end}
{p2col:{bf:v1.4.0}}Interactive filter dropdowns, dark theme.{p_end}
{p2colreset}


{title:Statistical Methods and Implementation Notes}

{p 4 4 2}
This section documents the statistical formulas used in sparkta, their sources,
and implementation decisions. All methods are implemented in Java
({cmd:DatasetBuilder.java}) and replicated in JavaScript ({cmd:sparkta_engine.js})
for client-side filter updates. Both implementations produce identical results
to six significant figures.

{p 4 4 2}
{bf:Summary statistics}

{p 4 4 2}
All summary statistics match Stata's {helpb summarize} output exactly.
N is the count of non-missing observations. Mean is the arithmetic mean.
Standard deviation uses Bessel's correction (denominator N-1), matching
Stata's sample SD. Coefficient of variation is |SD / Mean|, guarded
against division by zero.

{p 4 4 2}
Percentiles (median, Q1, Q3) use Stata's default definition: for a sorted
sample of size n, the h-th order statistic is at position h = (n+1)*p/100.
When h is non-integer, the result is linearly interpolated between the
floor(h) and ceil(h) order statistics, clamped to [1, n]. This matches
the formula in:

{pmore}
Stata Corp (2023). {it:Stata Base Reference Manual, Release 18}.
{it:summarize} entry, percentile definitions. StataCorp LLC, College
Station, TX. {browse "https://www.stata.com/manuals/rsummarize.pdf"}

{p 4 4 2}
{bf:Confidence intervals (cibar, ciline)}

{p 4 4 2}
Confidence intervals use the t-distribution at the user-specified level
(default 95%%). The interval is:

{p 8 8 2}
mean +/- t* x (SD / sqrt(N))

{p 4 4 2}
where t* is the critical value from the t-distribution with N-1 degrees
of freedom. Groups with N < 2 are omitted. This matches Stata's
{helpb ci means} command. The t-critical value for df 1-30 is looked up
from an exact 8-decimal-place table matching Stata's {cmd:invttail()}
function. For df > 30, a four-term Cornish-Fisher expansion is used
(maximum error < 1e-7 relative to the exact value):

{pmore}
Cornish, E.A. and Fisher, R.A. (1938). Moments and cumulants in the
specification of distributions. {it:Revue de l'Institut International de Statistique},
5, 307-320. {browse "https://doi.org/10.2307/1400905"}

{pmore}
Abramowitz, M. and Stegun, I.A. (1964). {it:Handbook of Mathematical Functions},
formula 26.7.8. National Bureau of Standards, Washington DC.

{p 4 4 2}
{bf:Histogram binning}

{p 4 4 2}
Default bin count uses Sturges' rule:

{p 8 8 2}
k = ceil(log2(n) + 1)

{p 4 4 2}
clamped to the range [5, 50]. Users can override with {cmd:bins(k)}.
The Sturges reference is:

{pmore}
Sturges, H.A. (1926). The choice of a class interval.
{it:Journal of the American Statistical Association}, 21(153), 65-66.
{browse "https://doi.org/10.1080/01621459.1926.10502161"}

{p 4 4 2}
{bf:Kernel density (violin charts)}

{p 4 4 2}
Violin charts delegate kernel density estimation to the
{cmd:@sgratzl/chartjs-chart-boxplot} plugin, which uses a Gaussian kernel
with Scott's rule for bandwidth selection:

{p 8 8 2}
h = 1.06 x sigma x n^(-1/5)

{p 4 4 2}
The {cmd:bandwidth()} option passes a multiplier applied to the plugin's
default bandwidth. The Scott reference is:

{pmore}
Scott, D.W. (1992). {it:Multivariate Density Estimation: Theory, Practice,
and Visualization}. John Wiley & Sons, New York.
{browse "https://doi.org/10.1002/9780470316849"}

{p 4 4 2}
{bf:Boxplot whiskers (boxplot, hbox)}

{p 4 4 2}
Whiskers extend to the most extreme observation within
{it:k} x IQR of the box edges, where IQR = Q3 - Q1.
The default multiplier is k = 1.5 (Tukey fences). Users can override
with {cmd:whiskerfence(k)}. Observations beyond the fences are drawn
as individual outlier dots. The Tukey fence reference is:

{pmore}
Tukey, J.W. (1977). {it:Exploratory Data Analysis}. Addison-Wesley,
Reading, MA. Chapter 2.

{p 4 4 2}
{bf:Locally weighted regression (fit(lowess))}

{p 4 4 2}
The {cmd:fit(lowess)} option fits a locally weighted scatterplot smoother
using a tricube weight function with bandwidth fraction f = 0.8.
The implementation uses weighted least squares at each evaluation point.
For observation i with predictor xi and neighborhood half-width h:

{p 8 8 2}
weight_j = (1 - |xj - xi|/h)^3 for |xj - xi|/h < 1, else 0

{p 4 4 2}
Values are evaluated at a grid of up to 200 points (or 4000/nGroups points
with {cmd:over()}) to limit output size. The smoother is applied after
{cmd:sort xvar, stable} to match Stata's {helpb lowess} command kinks
exactly. The foundational reference is:

{pmore}
Cleveland, W.S. (1979). Robust locally weighted regression and smoothing
scatterplots. {it:Journal of the American Statistical Association},
74(368), 829-836. {browse "https://doi.org/10.1080/01621459.1979.10481038"}

{p 4 4 2}
{bf:Other curve fits (lfit, qfit, exp, log, power, ma)}

{p 4 4 2}
Linear ({cmd:lfit}) and quadratic ({cmd:qfit}) fits use ordinary least
squares. Exponential ({cmd:exp}: y = ae^(bx)), logarithmic ({cmd:log}:
y = a + b*ln(x)), and power ({cmd:power}: y = ax^b) fits use OLS after
linearisation via log transformation. Moving average ({cmd:ma}) uses a
window of 5 observations by default.

{p 4 4 2}
{bf:Interactive filtering engine}

{p 4 4 2}
The JavaScript filtering engine ({cmd:sparkta_engine.js}) embedded in
each HTML file is an original implementation by the sparkta authors.
Data is stored in typed arrays (Float32Array / Int32Array) and encoded
as base64 for charts with 200 or more observations, reducing HTML file
size by approximately 40%% relative to JSON encoding. Filtering operates
in O(N) time using pre-built group-index arrays, consistent with the
approach used in the Java rendering layer.

{p 4 4 2}
{bf:Chart.js and plugins}

{p 4 4 2}
sparkta uses Chart.js 4.4.0 for all chart rendering.
The following third-party plugins are included with permission under
their respective open-source licenses (all MIT):

{p2colset 8 44 44 2}
{p2col:{cmd:@sgratzl/chartjs-chart-boxplot 4.4.5}}Boxplot and violin charts{p_end}
{p2col:{cmd:chartjs-chart-error-bars 4.4.0}}CI error bars (cibar, ciline){p_end}
{p2col:{cmd:chartjs-plugin-datalabels 2.2.0}}Data label overlays{p_end}
{p2col:{cmd:chartjs-plugin-annotation 3.0.1}}Reference lines, bands, points, ellipses{p_end}
{p2colreset}

{pmore}
Chart.js: {browse "https://github.com/chartjs/Chart.js"} (MIT license){break}
chartjs-chart-boxplot: {browse "https://github.com/sgratzl/chartjs-chart-boxplot"} (MIT){break}
chartjs-chart-error-bars: {browse "https://github.com/sgratzl/chartjs-chart-error-bars"} (MIT){break}
chartjs-plugin-datalabels: {browse "https://github.com/chartjs/chartjs-plugin-datalabels"} (MIT){break}
chartjs-plugin-annotation: {browse "https://github.com/chartjs/chartjs-plugin-annotation"} (MIT)

{title:Authors}

{pstd}
{bf:Fahad Mirza} | Author and Developer

{pmore}
{browse "https://www.linkedin.com/in/fahad-mirza/":LinkedIn}{space 3}
{browse "https://medium.com/@fahad-mirza":Medium Blog}{space 3}
{browse "https://github.com/fahad-mirza/":GitHub}

{pstd}
{bf:Claude} (Anthropic) | Editor and Co-developer

{pmore}
{cmd:sparkta} was built collaboratively between the author and Claude, which
assisted with algorithm implementation, Java rendering, debugging, and
feature development.

{title:Package Information}

{p 4 4 2}
{bf:sparkta} v3.5.108{break}
Chart rendering: Chart.js 4.4.0 ({browse "https://www.chartjs.org":chartjs.org}){break}
Boxplot/violin: @sgratzl/chartjs-chart-boxplot 4.4.5{break}
Error bars (CI charts): chartjs-chart-error-bars 4.4.0{break}
Data labels: chartjs-plugin-datalabels 2.2.0{break}
Annotations: chartjs-plugin-annotation 3.0.1{break}
Java bridge: Stata Java Plugin Interface (JPI){break}
Requires: Stata 17+ and {cmd:sparkta.jar}

{title:Also see}

{p 4 4 2}
{helpb javacall} {hline 2} Stata Java Plugin Interface{break}
{helpb summarize} {hline 2} Summary statistics{break}
{helpb query} {hline 2} Java heap information ({cmd:query java})

{hline}
{it:sparkta.sthlp  version 3.5.107  2026-03-18}
