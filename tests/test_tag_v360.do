*! test_tag_v360.do -- sparkta v3.6.0 release-tag smoke test (2026-09-13, rev 2)
*! rev 2: case 5 reads LINE 2 of the page (line 1 is <!DOCTYPE html>; the sparkta version
*!        comment is line 2). Rev 1 asserted on line 1 and failed for that reason only.
*! Purpose: prove the TAGGED build (version string "3.6.0", rebuilt jar, six helper ados
*!          with a new `version 17` line) still loads and renders on real Stata.
*! Run AFTER java\build.bat (or after copying ado\ + the jar into PERSONAL) and a Stata
*! restart (the JVM does not reload a rebuilt jar). Expected: 6/6 pass, no "version
*! mismatch" message, the header line prints "[sparkta v3.6.0]".
*! Output folder: test_tag_out\ next to this do-file.

version 17
clear all
set more off

capture mkdir test_tag_out
local out "test_tag_out"
local pass = 0
local fail = 0

// 1. the installed file is the tagged one
which sparkta
which sparkta_mg_vlabel
which sparkta_find_browser

// 2. plain bar chart: exercises the VersionCheck handshake (ado 3.6.0 vs JAR 3.6.0)
sysuse auto, clear
capture noisily sparkta price mpg, type(bar) export("`out'/t_bar.html")
if _rc == 0 & fileexists("`out'/t_bar.html") local ++pass
else local ++fail

// 3. one helper from each of the six files that gained `version 17`
//    sparkta_find_browser + sparkta_browser_run (saveas png through the session browser)
regress price mpg weight foreign
capture noisily sparkta, type(coefplot) export("`out'/t_coef.html") saveas("`out'/t_coef.png")
if _rc == 0 & fileexists("`out'/t_coef.png") local ++pass
else local ++fail

//    sparkta_event_times + sparkta_matrix_sort_time (event study from a matrix)
matrix E = J(3, 5, .)
matrix rownames E = b se pvalue
matrix colnames E = Tm2 Tm1 Tp0 Tp1 Tp2
matrix E[1, 1] = 0.03, -0.004, -0.020, -0.051, -0.137
matrix E[2, 1] = 0.014, 0.013,  0.011,  0.016,  0.035
matrix E[3, 1] = 0.057, 0.780,  0.067,  0.001,  0.000
capture noisily sparkta, type(eventstudy) matrix(E) export("`out'/t_es.html")
if _rc == 0 & fileexists("`out'/t_es.html") local ++pass
else local ++fail

//    sparkta_mg_vlabel + sparkta_mg_varlabel (marginsplot with factor labels)
regress price i.foreign##c.mpg
margins foreign, at(mpg=(15 25 35))
capture noisily sparkta, type(marginsplot) export("`out'/t_mp.html")
if _rc == 0 & fileexists("`out'/t_mp.html") local ++pass
else local ++fail

// 4. by() panel page with the export layout rule (fix9f) still in force
capture noisily sparkta price, type(boxplot) by(rep78) export("`out'/t_box_by.html") saveas("`out'/t_box_by.pdf")
if _rc == 0 & fileexists("`out'/t_box_by.pdf") local ++pass
else local ++fail

// 5. the page footer / HTML comment carries the release version
capture noisily {
    tempname fh
    file open `fh' using "`out'/t_bar.html", read text
    file read `fh' line
    file read `fh' line
    file close `fh'
    assert strpos(`"`line'"', "<!-- sparkta v3.6.0 ") > 0
}
if _rc == 0 local ++pass
else local ++fail

display as text _n "test_tag_v360: `pass' passed, `fail' failed (expected 6/6)"
