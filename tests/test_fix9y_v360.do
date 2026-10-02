// =============================================================================
// test_fix9y_v360.do -- Sparkta v3.6.0-t2j fix9y smoke test (2026-09-30)
// =============================================================================
// Version: 1.2. ASCII only.
// v1.2: T5 fix9z -- an alabelpos() label sits beside its point (position start), not on it.
// v1.1: Mata has no fileread() (r(3499) on real Stata) -> _spk_slurp() via fopen/fread.
//
// WHAT IT COVERS (four items found on the conference deck)
//   T1  scatter + fit(lowess) + filters(): the page keeps the ORIGINAL fit lines and
//       restores them when every filter is back at "All" (_spkFitInit in the page).
//   T2  by() panel bar tooltips show n and the panel share: each panel dataset carries
//       _spkN (per-bar counts) and _spkTot (panel rows).
//   T3  scatter axes get 5% headroom: the page carries grace:'5%'.
//   T4  annotation labels are not clipped at the plot edge: annotation:{clip:false.
//   The checks read each page back with Mata fopen/fread (no browser needed).
//
// BY EYE (2 minutes) -- open the pages in test_out_fix9y/:
//   t1_lowess_filter.html  pick a repair record, then All again: the lowess lines are
//                          exactly the ones you saw on open
//   t2_by_tooltip.html     hover a bar in each panel: "n = k (x% of panel N)"; pick a
//                          repair record: n and the share follow the filter
//   t4_annotation.html     the label "Most expensive car" is fully visible
//
// Requires: Stata 17+, sparkta fix9y installed (java\build.bat; RESTART Stata).
// Expect 4/4 PASS, then test_release_v360.do 210/210.
// =============================================================================
version 17
clear all
set more off

capture mkdir test_out_fix9y
local OUT "`c(pwd)'/test_out_fix9y"
local npass 0
local ntest 0

capture mata: mata drop _spk9y_has()
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
// 1 if the file exists and contains the text
real scalar _spk9y_has(string scalar fn, string scalar txt)
{
    if (!fileexists(fn)) return(0)
    return(strpos(_spk_slurp(fn), txt) > 0)
}
end

sysuse auto, clear

// T1 lowess + filter: restore code present
local ++ntest
capture noisily sparkta price mpg, type(scatter) over(foreign) fit(lowess) ///
    filters(rep78) nostats export("`OUT'/t1_lowess_filter.html")
local rc = _rc
mata: st_local("ok", strofreal(_spk9y_has("`OUT'/t1_lowess_filter.html", "_spkFitInit")))
if `rc' == 0 & "`ok'" == "1" {
    local ++npass
    di as res "PASS T1 lowess + filter: original fit kept for the All view"
}
else di as err "FAIL T1 (rc=`rc', found=`ok')"

// T2 by() panel tooltip counts
local ++ntest
capture noisily sparkta price, over(rep78) by(foreign) filters(headroom) nomissing ///
    nostats export("`OUT'/t2_by_tooltip.html")
local rc = _rc
mata: st_local("ok", strofreal(_spk9y_has("`OUT'/t2_by_tooltip.html", "_spkN:[")))
if `rc' == 0 & "`ok'" == "1" {
    local ++npass
    di as res "PASS T2 by() panels carry their own bar counts"
}
else di as err "FAIL T2 (rc=`rc', found=`ok')"

// T3 scatter headroom
local ++ntest
capture noisily sparkta price mpg, type(scatter) nostats export("`OUT'/t3_scatter.html")
local rc = _rc
mata: st_local("ok", strofreal(_spk9y_has("`OUT'/t3_scatter.html", "grace:'5%'")))
if `rc' == 0 & "`ok'" == "1" {
    local ++npass
    di as res "PASS T3 scatter axes padded 5%"
}
else di as err "FAIL T3 (rc=`rc', found=`ok')"

// T4 annotation label not clipped (Cadillac Seville: price 15906, mpg 21)
local ++ntest
capture noisily sparkta price mpg, type(scatter) nostats ///
    alabelpos(15906 21 15) alabeltext(Most expensive car) ///
    export("`OUT'/t4_annotation.html")
local rc = _rc
mata: st_local("ok", strofreal(_spk9y_has("`OUT'/t4_annotation.html", "annotation:{clip:false")))
if `rc' == 0 & "`ok'" == "1" {
    local ++npass
    di as res "PASS T4 annotation labels drawn unclipped"
}
else di as err "FAIL T4 (rc=`rc', found=`ok')"

// T5 fix9z: the pos-15 label starts right of its point instead of covering it
local ++ntest
mata: st_local("ok", strofreal(_spk9y_has("`OUT'/t4_annotation.html", "position:{x:'start',y:'center'}")))
if "`ok'" == "1" {
    local ++npass
    di as res "PASS T5 annotation label beside its point (fix9z)"
}
else di as err "FAIL T5 (found=`ok')"

di as txt _n "test_fix9y_v360.do: `npass'/`ntest' PASS -- then open the three pages listed above"
