*! dashboard_diag.do - run with: do dashboard_diag.do
* Tests different option declaration patterns to find what Stata 19 accepts

clear all
set more off

display as text ""
display as text "=== Stata Option Parsing Diagnostics ==="
display as text "Stata `c(stata_version)' on `c(os)'"
display as text ""

* --- Test A: all lowercase ---
capture program drop _dtest
program define _dtest
    syntax , [bargroupwidth(string)]
    display as text "  bargroupwidth=`bargroupwidth'"
end
capture _dtest, bargroupwidth(0.85)
if _rc == 0 display as text "Test A PASS: [bargroupwidth(string)] works"
else         display as error "Test A FAIL rc=`_rc': [bargroupwidth(string)] broken"
program drop _dtest

* --- Test B: single uppercase first letter ---
capture program drop _dtest
program define _dtest
    syntax , [Bargroupwidth(string)]
    display as text "  bargroupwidth=`bargroupwidth'"
end
capture _dtest, bargroupwidth(0.85)
if _rc == 0 display as text "Test B PASS: [Bargroupwidth(string)] works"
else         display as error "Test B FAIL rc=`_rc': [Bargroupwidth(string)] broken"
program drop _dtest

* --- Test C: uppercase prefix only (BARgroupwidth) ---
capture program drop _dtest
program define _dtest
    syntax , [BARgroupwidth(string)]
    display as text "  bargroupwidth=`bargroupwidth'"
end
capture _dtest, bargroupwidth(0.85)
if _rc == 0 display as text "Test C PASS: [BARgroupwidth(string)] works"
else         display as error "Test C FAIL rc=`_rc': [BARgroupwidth(string)] broken"
program drop _dtest

* --- Test D: mid-word uppercase (BARGroupWidth) ---
capture program drop _dtest
program define _dtest
    syntax , [BARGroupWidth(string)]
    display as text "  bargroupwidth=`bargroupwidth'"
end
capture _dtest, bargroupwidth(0.85)
if _rc == 0 display as text "Test D PASS: [BARGroupWidth(string)] works"
else         display as error "Test D FAIL rc=`_rc': [BARGroupWidth(string)] broken"
program drop _dtest

* --- Test E: known-working baseline (BARWidth) ---
capture program drop _dtest
program define _dtest
    syntax , [BARWidth(string)]
    display as text "  barwidth=`barwidth'"
end
capture _dtest, barwidth(0.85)
if _rc == 0 display as text "Test E PASS: [BARWidth(string)] works (baseline)"
else         display as error "Test E FAIL rc=`_rc': even BARWidth broken!"
program drop _dtest

* --- Test F: short all-uppercase (BARG) ---
capture program drop _dtest
program define _dtest
    syntax , [BARG(string)]
    display as text "  barg=`barg'"
end
capture _dtest, barg(0.85)
if _rc == 0 display as text "Test F PASS: [BARG(string)] works"
else         display as error "Test F FAIL rc=`_rc': [BARG(string)] broken"
program drop _dtest

* --- Test G: numeral in value (is it the 0.85 causing issue?) ---
capture program drop _dtest
program define _dtest
    syntax , [BARGroupWidth(string)]
    display as text "  bargroupwidth=`bargroupwidth'"
end
capture _dtest, bargroupwidth(abc)
if _rc == 0 display as text "Test G PASS: BARGroupWidth(abc) works - value not the issue"
else         display as error "Test G FAIL rc=`_rc': BARGroupWidth(abc) broken"
program drop _dtest

display as text ""
display as text "Done."
