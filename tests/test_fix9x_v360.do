// =============================================================================
// test_fix9x_v360.do -- Sparkta v3.6.0-t2j fix9x smoke test (2026-09-30)
// =============================================================================
// Version: 1.1. ASCII only.
// v1.1: Mata has no fileread() (r(3499) on real Stata) -> _spk_slurp() via fopen/fread.
//
// WHAT IT COVERS
//   fix9x  coefplot with levels(# #) plus coefsort() or order(): the INNER interval
//          (e.g. 90%) must be reordered together with the coefficients. Before fix9x
//          the inner bounds stayed in estimation order, so after sorting the 90% band
//          was drawn on another coefficient's row (seen on the conference deck).
//   fix9w  default histogram bins = Stata's rule (8 bins for auto price, N = 74).
//
// HOW IT CHECKS (no browser needed): each page is read back with Mata fopen/fread.
//   The coefplot page carries   var lo=[...],hi=[...]     (outer interval)
//                               var lo2=[...],hi2=[...]   (inner interval)
//   in DISPLAY order. For every coefficient the inner interval must lie inside the
//   outer one: lo[i] <= lo2[i] and hi2[i] <= hi[i]. A misaligned band breaks this.
//   The histogram page carries labels:['3291',...] -- 9 edges = 8 bins.
//
// Requires: Stata 17+, sparkta fix9x installed (java\build.bat copies the ado files to
//           your personal ado folder; restart Stata). Expect 6/6 PASS.
// Then run test_release_v360.do: expect 210/210.
// =============================================================================
version 17
clear all
set more off

capture mkdir test_out_fix9x
local OUT "`c(pwd)'/test_out_fix9x"
local npass 0
local ntest 0

// ---- Mata helper: 1 if every inner interval lies inside the outer one ---------
capture mata: mata drop _spk9x_nested()
capture mata: mata drop _spk9x_arr()
capture mata: mata drop _spk9x_nlab()
capture mata: mata drop _spk_slurp()
mata:
// whole file as one string (Mata has no fileread(); fopen/fread is in every Stata 17+)
string scalar _spk_slurp(string scalar fn)
{
    real scalar fh, n
    string scalar s
    fh = fopen(fn, "r")
    fseek(fh, 0, 1)
    n = ftell(fh)
    fseek(fh, 0, -1)
    s = (n > 0 ? fread(fh, n) : "")
    fclose(fh)
    return(s)
}
real rowvector _spk9x_arr(string scalar s, string scalar key)
{
    real scalar a, b
    string scalar body
    string rowvector t
    a = strpos(s, key)
    if (a == 0) return(J(1, 0, .))
    body = substr(s, a + strlen(key), .)
    b = strpos(body, "]")
    body = substr(body, 1, b - 1)
    t = tokens(body, ",")
    t = select(t, t :!= ",")
    return(strtoreal(t))
}
real scalar _spk9x_nested(string scalar fn)
{
    string scalar s
    real rowvector lo, hi, lo2, hi2
    real scalar i, ok
    if (!fileexists(fn)) return(0)
    s   = _spk_slurp(fn)
    lo  = _spk9x_arr(s, "var lo=[")
    hi  = _spk9x_arr(s, "],hi=[")
    lo2 = _spk9x_arr(s, "var lo2=[")
    hi2 = _spk9x_arr(s, "],hi2=[")
    printf("{txt}    outer lo: %s\n", invtokens(strofreal(lo)))
    printf("{txt}    inner lo: %s\n", invtokens(strofreal(lo2)))
    if (cols(lo) == 0 | cols(lo) != cols(lo2) | cols(hi) != cols(hi2)) return(0)
    ok = 1
    for (i = 1; i <= cols(lo); i++) {
        if (lo[i] > lo2[i] + 1e-6 | hi2[i] > hi[i] + 1e-6) ok = 0
    }
    return(ok)
}
// number of category labels on a histogram page (first non-empty labels:[...])
real scalar _spk9x_nlab(string scalar fn)
{
    string scalar s, body
    real scalar a, b
    string rowvector t
    if (!fileexists(fn)) return(0)
    s = _spk_slurp(fn)
    a = strpos(s, "labels:['")
    if (a == 0) return(0)
    body = substr(s, a + 8, .)
    b = strpos(body, "]")
    body = substr(body, 1, b - 1)
    t = tokens(body, ",")
    t = select(t, t :!= ",")
    return(cols(t))
}
end

// ---- the model: standardised regressors so the four coefficients differ in size
sysuse auto, clear
foreach v in mpg weight length {
    egen z_`v' = std(`v')
}
regress price z_mpg z_weight z_length foreign

// T1 coefsort(abs)
local ++ntest
capture noisily sparkta, type(coefplot) levels(90 95) cistyle(band) coefsort(abs) ///
    export("`OUT'/t1_sort_abs.html")
local rc = _rc
mata: st_local("ok", strofreal(_spk9x_nested("`OUT'/t1_sort_abs.html")))
if `rc' == 0 & "`ok'" == "1" {
    local ++npass
    di as res "PASS T1 coefsort(abs): inner band inside outer band on every row"
}
else di as err "FAIL T1 coefsort(abs) (rc=`rc', nested=`ok')"

// T2 coefsort(value)
local ++ntest
capture noisily sparkta, type(coefplot) levels(90 95) coefsort(value) ///
    export("`OUT'/t2_sort_value.html")
local rc = _rc
mata: st_local("ok", strofreal(_spk9x_nested("`OUT'/t2_sort_value.html")))
if `rc' == 0 & "`ok'" == "1" {
    local ++npass
    di as res "PASS T2 coefsort(value)"
}
else di as err "FAIL T2 coefsort(value) (rc=`rc', nested=`ok')"

// T3 order()
local ++ntest
capture noisily sparkta, type(coefplot) levels(90 95) order(foreign z_length) ///
    export("`OUT'/t3_order.html")
local rc = _rc
mata: st_local("ok", strofreal(_spk9x_nested("`OUT'/t3_order.html")))
if `rc' == 0 & "`ok'" == "1" {
    local ++npass
    di as res "PASS T3 order(foreign z_length)"
}
else di as err "FAIL T3 order() (rc=`rc', nested=`ok')"

// T4 no sort (baseline, was already right)
local ++ntest
capture noisily sparkta, type(coefplot) levels(90 95) export("`OUT'/t4_nosort.html")
local rc = _rc
mata: st_local("ok", strofreal(_spk9x_nested("`OUT'/t4_nosort.html")))
if `rc' == 0 & "`ok'" == "1" {
    local ++npass
    di as res "PASS T4 no sort (baseline)"
}
else di as err "FAIL T4 baseline (rc=`rc', nested=`ok')"

// T5 eform + coefsort (inner bounds are exponentiated AND sorted)
local ++ntest
logit foreign z_mpg z_weight z_length
capture noisily sparkta, type(coefplot) eform levels(90 95) coefsort(abs) ///
    export("`OUT'/t5_eform_sort.html")
local rc = _rc
mata: st_local("ok", strofreal(_spk9x_nested("`OUT'/t5_eform_sort.html")))
if `rc' == 0 & "`ok'" == "1" {
    local ++npass
    di as res "PASS T5 eform + coefsort(abs)"
}
else di as err "FAIL T5 eform + coefsort (rc=`rc', nested=`ok')"

// T6 fix9w: default histogram bins = 8 for auto price (N = 74), like Stata
local ++ntest
sysuse auto, clear
capture noisily sparkta price, type(histogram) nostats export("`OUT'/t6_hist.html")
local rc6 = _rc
mata: st_local("nlab", strofreal(_spk9x_nlab("`OUT'/t6_hist.html")))
if `rc6' == 0 & "`nlab'" == "9" {
    // 9 bin edges = 8 bins
    local ++npass
    di as res "PASS T6 histogram: 8 bins (Stata: bin=8, width=1576.875)"
}
else di as err "FAIL T6 histogram (rc=`rc6', labels=`nlab', want 9 = 8 bins)"

di as txt _n "test_fix9x_v360.do: `npass'/`ntest' PASS -- zip test_out_fix9x/ with the log"
