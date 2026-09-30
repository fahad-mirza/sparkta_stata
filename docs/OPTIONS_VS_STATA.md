# Sparkta option conventions vs Stata (v3.6.0-s8s)

Principle (agreed 2026-09-07): sparkta is not a re-implementation of Stata's
graph syntax. It keeps its own flat, self-describing option names -- nothing
already released changes -- but it ACCEPTS the Stata word wherever a Stata user
would reach for it instinctively. Alias and primary resolve to one internal
path, so there is one code path to test; giving both is an error.

## Same word as Stata (no action needed)
title subtitle note caption xtitle ytitle xline yline legend lpattern over by
relabel horizontal eform levels coeflabels order

## Stata word accepted as an alias (s8s)

| Stata / Jann          | sparkta primary       | value mapping                                   |
|-----------------------|-----------------------|--------------------------------------------------|
| level(95)             | cilevel(95)           | --                                               |
| keep(varlist)         | show(varlist)         | --                                               |
| drop(varlist)         | omit(varlist)         | --                                               |
| sort(asc)             | coefsort(value)       | sparkta's sort() (= sortgroups) already exists; on post-est charts it takes Jann's keys asc|desc|b|p|se|abs |
| vertical              | (opposite of horizontal) | coefplot: vertical layout                     |
| recastci(rarea)       | cistyle(area)         | rarea/rband->area rcap->whisker rbar->bar rline/rspike->band; lists allowed |
| recast(connected)     | connected             | connected|line -> connected; scatter -> points   |
| msymbol(O)            | pointstyle(circle)    | O D T S X + p i (h-variants accepted)            |
| msize(large)          | pointsize(7.5)        | vtiny..vhuge -> px; a number is px               |
| mcolor(navy maroon)   | colors(navy maroon)   | --                                               |
| lwidth(thick)         | linewidth(3.5)        | vthin..vthick -> px; a number is px              |
| nolabel               | novaluelabels         | --                                               |
| coeflabels(mpg = "MPG") | coeflabels("mpg/MPG") | both forms                                     |
| plotregion(margin())  | plotmargin()          | flat name; same l r b t percent grammar          |

## Deliberately different (documented, not aliased)
| Stata                       | sparkta                | why                                             |
|-----------------------------|------------------------|--------------------------------------------------|
| scheme()                    | theme()                | browser themes (dark/light) are not Stata schemes |
| name()/saving()             | export()               | output is a file path, not a graph name          |
| xsize()/ysize()             | aspect()               | responsive canvas; one ratio, no inches          |
| graphregion(margin())       | padding()              | canvas padding in px                             |
| xdimension()/plotdimension()| over()                 | marginsplot dimensions map to sparkta's over()   |
| ciopts(recast())            | cistyle() ciwidth() cicolors() | flat options instead of nested suboptions |

## Not yet possible (roadmap)
| Stata                        | status                                              |
|------------------------------|-----------------------------------------------------|
| xlabel(#5) / xlabel(0(500)5000) | xtickcount() exists; numlist ticks need the s8r anchored-tick infrastructure -> planned |
| xlabel(1 "One" 2 "Two")      | relabel(One|Two) covers the common case             |
| baselevels                   | base levels are shown by default; nobase hides them |
| legend(order() label())      | leglabels() covers relabelling; ordering not yet    |

## Grammar rules for new options
1. Units follow Stata: margins and graces in percent of the data range; sizes
   accept Stata size words or px.
2. Lists inside an option are space-separated (Stata style); "+" and "|" are
   accepted where sparkta already used them.
3. A new capability that is a COMBINATION of existing values extends an
   existing option's grammar (cistyle(area whisker)) rather than adding a flag.
4. Every alias is a one-line copy into the primary local; validation and Java
   wiring live on the primary only.


## Matrix mode (v3.6.0-t2a) -- vs Ben Jann's `coefplot matrix()`

| sparkta | coefplot | Notes |
|---|---|---|
| `matrix(M)` | `matrix(M)` | first row = estimates, column names = coefficients |
| `matrix(M[2])`, `matrix(M[,3])` | same | selects the row/column of estimates; also fixes the orientation |
| `ci((2 3))`, `ci(2 3)`, `ci(ll ul)`, `ci(C)` | `ci((2 3))`, `ci(C)` | names accepted as well as positions |
| `se(2)`, `se(name)`, `se(S)` | `se(2)`, `se(S)` | CI computed at `cilevel()`/`levels()`; t with `df()` |
| `df(#)`, `df(name)` | `df(#)`, `df(mspec)` | a number is always a value, never a position |
| `pvalue(#\|name)` | -- | sparkta extension (the `r(table)` row) |
| `matrix(r(table))`, `matrix(e(ci_bc))` | -- (copy to a matrix first) | sparkta extension |
| *autodetect by row/column name* | -- (positions only) | b/coef/estimate, se, ll/lb/lower, ul/ub/upper, pvalue/p, df |
| `matrix(A B C)` + `plotlabels()` / `estlabels()` | `(matrix(A)) (matrix(B))` + `label()` / `plotlabels()` | one plot per matrix, same as `estnames()` |
| `type(eventstudy) matrix(r(table))` | -- | relative-time names parsed: `T-3 T T+1`, `tm3 tp0`, `Tm3 Tp1`, `lead3 lag2`, `-3.__event__`; `Pre_avg`/`Post_avg` kept after the series |

## Event study rendering (v3.6.0-t2d) -- vs `csdid_plot` / `coefplot ... vertical`

| sparkta `type(eventstudy)` | csdid_plot | Notes |
|---|---|---|
| x = relative time, linear, integer ticks, "Periods to treatment" | same | names parsed: `lead#/lag#`, `T-3 T T+2`, `tm3 tp0`, `Tm3 Tp1`, `-3.__event__`, integers; unparsed -> category axis |
| pre / post in two colours, legend "Pre-treatment / Post-treatment" | same | palette colours 1 and 3 |
| CI rectangles (`cistyle(bar)`, default); `whisker`, `band` | rbar | `levels()` draws the inner level darker/thicker |
| dashed null line at `refval()` (0) | yes | `pexline(-0.5)` adds a treatment line |
| several models: one marker shape per model, dodged, legend "<model> (pre|post)" | -- | `estnames()` or `matrix(A B)`; `connected` draws a thin dashed line per model |
| `Pre_avg` / `Post_avg` off the axis; `show(*)` includes them in the table | absent | |
