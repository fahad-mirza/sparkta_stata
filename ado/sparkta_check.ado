*! sparkta_check version 3.5.42
*! Verifies sparkta installation and option parsing
*! v2.0.8: extracted to standalone sparkta_check.ado so Stata can find it
program define sparkta_check
    version 17
    display as text ""
    display as text "  === Sparkta v3.5.42 Installation Check ==="
    display as text "  Loaded from: `c(sysdir_personal)'"
    display as text ""
    display as text "  Testing option recognition..."

    capture syntax [, BARGroupwidth(string)]
    if _rc == 0 display as text "  bargroupwidth()      ... OK"
    else         display as error "  bargroupwidth()      ... FAIL (rc=`_rc')"

    capture syntax [, LEGBoxheight(string)]
    if _rc == 0 display as text "  legboxheight()       ... OK"
    else         display as error "  legboxheight()       ... FAIL (rc=`_rc')"

    capture syntax [, POINTBorderwidth(string)]
    if _rc == 0 display as text "  pointborderwidth()   ... OK"
    else         display as error "  pointborderwidth()   ... FAIL (rc=`_rc')"

    capture syntax [, LEGTitle(string)]
    if _rc == 0 display as text "  legtitle()           ... OK"
    else         display as error "  legtitle()           ... FAIL (rc=`_rc')"

    capture syntax [, AREAopacity(string)]
    if _rc == 0 display as text "  areaopacity()        ... OK"
    else         display as error "  areaopacity()        ... FAIL (rc=`_rc')"

    capture syntax [, CIBandopacity(string)]
    if _rc == 0 display as text "  cibandopacity()      ... OK"
    else         display as error "  cibandopacity()      ... FAIL (rc=`_rc')"

    capture syntax [, SORTGroups(string)]
    if _rc == 0 display as text "  sortgroups()         ... OK"
    else         display as error "  sortgroups()         ... FAIL (rc=`_rc')"

    display as text ""
    // Check sparkta.jar is locatable (same search order as sparkta.ado v3.5.42)
    local jarfound 0
    local jarfoundpath ""

    // 1. findfile -- primary: searches adopath for sparkta.ado, jar is co-located
    capture findfile "sparkta.ado"
    if !_rc {
        local _ffdir = subinstr(r(fn), "sparkta.ado", "", .)
        local _p1 `"`_ffdir'sparkta.jar"'
        capture confirm file `"`_p1'"'
        if !_rc {
            local jarfound 1
            local jarfoundpath `"`_p1'"'
        }
    }

    // 3. sysdir_personal variants (net install personal)
    if `jarfound' == 0 {
        foreach p in                                            ///
            "`c(sysdir_personal)'sparkta.jar"                  ///
            "`c(sysdir_personal)'\sparkta.jar"                 ///
            "`c(sysdir_personal)'/sparkta.jar" {
            capture confirm file "`p'"
            if _rc == 0 {
                local jarfound 1
                local jarfoundpath "`p'"
                continue, break
            }
        }
    }

    // 4. sysdir_PLUS/s/sparkta/ (SSC)
    if `jarfound' == 0 {
        foreach p in                                                ///
            "`c(sysdir_PLUS)'s\sparkta\sparkta.jar"              ///
            "`c(sysdir_PLUS)'s/sparkta/sparkta.jar" {
            capture confirm file "`p'"
            if _rc == 0 {
                local jarfound 1
                local jarfoundpath "`p'"
                continue, break
            }
        }
    }

    // 5. Working directory
    if `jarfound' == 0 {
        foreach p in "`c(pwd)'\sparkta.jar" "`c(pwd)'/sparkta.jar" {
            capture confirm file "`p'"
            if _rc == 0 {
                local jarfound 1
                local jarfoundpath "`p'"
                continue, break
            }
        }
    }

    if `jarfound' == 1 {
        display as text "  sparkta.jar found at: `jarfoundpath'"
    }
    else {
        display as error "  sparkta.jar NOT FOUND"
        display as error "  findfile sparkta.ado returned: `r(fn)'"
        display as error "  Fix: sparkta.jar must be in the same folder as sparkta.ado"
        display as error "  Reinstall: ssc install sparkta"
    }

    display as text ""
    display as text "  Check complete."
    display as text "  If any option shows FAIL: recompile from latest source"
    display as text "  and restart Stata completely (not just discard results)."
    display as text ""
end
