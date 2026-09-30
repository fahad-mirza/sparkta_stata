*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: reads e(b)/e(V) for coefplot/eventstudy -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// =========================================================================
// SUBROUTINE: sparkta_read_eresults (v3.6.0)
// Reads e(b), e(V) from stored estimation results.
// Computes SE, CI, p-values. Handles factor variable names.
// Sets c_local variables in the caller for pipe-separated Java args.
// =========================================================================
program sparkta_read_eresults
    version 17
    syntax, [CILevel(string) OMIT(string) SHOW(string) COEFLbl(string) NOBASE LEVEL2(string)]

    // -- Defaults --
    if "`cilevel'" == "" local cilevel "95"

    // -- Read matrices --
    tempname _b _V
    matrix `_b' = e(b)
    matrix `_V' = e(V)
    local _k = colsof(`_b')
    local _names : colnames `_b'
    local _eqs : coleq `_b'

    // -- Degrees of freedom: t-distribution if e(df_r) exists, z otherwise --
    local _use_t 0
    local _df 0
    capture scalar _sparkta_df = e(df_r)
    if _rc == 0 & scalar(_sparkta_df) < . {
        local _use_t 1
        local _df = scalar(_sparkta_df)
    }
    capture scalar drop _sparkta_df

    // -- CI half-width multiplier (primary) --
    local _alpha = (100 - `cilevel') / 200
    if `_use_t' {
        local _crit = invttail(`_df', `_alpha')
    }
    else {
        local _crit = invnormal(1 - `_alpha')
    }
    // -- Inner CI multiplier for levels() (v3.6.0-s7d) --
    local _crit2 0
    local _has_level2 0
    if "`level2'" != "" {
        local _has_level2 1
        local _alpha2 = (100 - `level2') / 200
        if `_use_t' {
            local _crit2 = invttail(`_df', `_alpha2')
        }
        else {
            local _crit2 = invnormal(1 - `_alpha2')
        }
    }

    // -- Sample size and dependent variable --
    local _nobs = e(N)
    local _depvar "`e(depvar)'"

    // -- Fit statistics (v3.6.0-s6c): collect available e() scalars.
    // Use capture so missing scalars (e.g. e(r2) for logit) are silently skipped.
    // Format: key=value pairs, pipe-separated, e.g. "r2=0.4231|r2_a=0.3987|F=18.42"
    // Only numeric, non-missing values are included.
    local _pe_fitstats ""
    foreach _fs_key in r2 r2_a r2_p F p ll chi2 rmse {
        capture local _fs_val = e(`_fs_key')
        if _rc == 0 & "`_fs_val'" != "" & "`_fs_val'" != "." {
            capture confirm number `_fs_val'
            if _rc == 0 {
                local _fs_fmt : display %10.4g `_fs_val'
                local _fs_fmt = strtrim("`_fs_fmt'")
                if "`_pe_fitstats'" == "" {
                    local _pe_fitstats "`_fs_key'=`_fs_fmt'"
                }
                else {
                    local _pe_fitstats "`_pe_fitstats'|`_fs_key'=`_fs_fmt'"
                }
            }
        }
    }

    // -- Mean of dependent variable (v3.6.0-s6c+): computed in-sample via e(sample).
    // Key: ymean. Uses the estimation sample so the mean matches the regression sample
    // exactly (respects if/in conditions, dropped observations, etc.).
    // capture protects against: no depvar (e.g. matrix-only commands), string depvar,
    // or e(sample) not defined (some user-written commands).
    if "`_depvar'" != "" {
        capture {
            quietly summarize `_depvar' if e(sample)
            if r(N) > 0 {
                local _ymean_fmt : display %10.4g r(mean)
                local _ymean_fmt = strtrim("`_ymean_fmt'")
                if "`_pe_fitstats'" == "" {
                    local _pe_fitstats "ymean=`_ymean_fmt'"
                }
                else {
                    local _pe_fitstats "`_pe_fitstats'|ymean=`_ymean_fmt'"
                }
            }
        }
        // If capture fails (e.g. no e(sample) marker) we simply omit ymean silently.
    }

    // -- Detect single-equation model (skip eq prefix for logit/probit etc.) --
    local _neq 0
    local _prev_eq ""
    forvalues _j = 1/`_k' {
        local _teq : word `_j' of `_eqs'
        if "`_teq'" != "`_prev_eq'" {
            local _neq = `_neq' + 1
            local _prev_eq "`_teq'"
        }
    }
    local _single_eq = (`_neq' <= 1)

    // -- Build pipe-separated output strings --
    local _out_names  ""
    local _out_coefs  ""
    local _out_lower  ""
    local _out_upper  ""
    local _out_lower2 ""
    local _out_upper2 ""
    local _out_ses    ""
    local _out_pvals  ""
    local _out_tzvals ""
    local _out_bases  ""
    local _nkept 0

    forvalues _i = 1/`_k' {
        local _cname : word `_i' of `_names'
        local _ceq   : word `_i' of `_eqs'

        // -- Extract coefficient and SE --
        local _coef = `_b'[1, `_i']
        local _se   = sqrt(`_V'[`_i', `_i'])

        // -- FILTER 1: nobase -- drop base/omitted factor levels --
        // These have b. or o. prefix in the coefficient name and coef=0, se=0
        // When nobase is set, collect them into _out_bases for table display
        local _is_base 0
        if strpos("`_cname'", "b.") > 0 & `_coef' == 0 & `_se' == 0 {
            local _is_base 1
        }
        if strpos("`_cname'", "bn.") > 0 & `_coef' == 0 & `_se' == 0 {
            local _is_base 1
        }
        if strpos("`_cname'", "o.") > 0 & `_coef' == 0 & `_se' == 0 {
            local _is_base 1
        }
        if strpos("`_cname'", "on.") > 0 & `_coef' == 0 & `_se' == 0 {
            local _is_base 1
        }
        if `_is_base' {
            // Build display name for the base variable
            local _bname "`_cname'"
            // s8n: "1b.rep78" -> "1.rep78" (was subinstr("b.","") which ate the dot -> "1rep78")
            // NOTE: Stata regexr() has NO backreferences (\1 is literal); use regexm/regexs
            if regexm("`_bname'", "^([0-9]+)[bon]+\.(.+)$") local _bname = regexs(1) + "." + regexs(2)
            // Try value label lookup
            if regexm("`_bname'", "^([0-9]+)\.(.+)$") {
                local _bfval = regexs(1)
                local _bfvar = regexs(2)
                capture {
                    local _bvlbl : label (`_bfvar') `_bfval'
                }
                if _rc == 0 & "`_bvlbl'" != "" & "`_bvlbl'" != "`_bfval'" {
                    local _bname "`_bvlbl'"
                }
                else {
                    local _bname "`_bfvar'=`_bfval'"
                }
            }
            // Append to bases list (pipe-sep)
            if "`_out_bases'" == "" {
                local _out_bases "`_bname'"
            }
            else {
                local _out_bases "`_out_bases'|`_bname'"
            }
            if "`nobase'" != "" continue
        }

        // -- FILTER 2: omit() -- drop named coefficients --
        // v3.6.0: supports glob patterns: * matches any chars, ? matches one char
        // Examples: omit(*.foreign) drops all levels of i.foreign
        //           omit(_cons *weight*) drops _cons and anything containing "weight"
        if "`omit'" != "" {
            local _is_omitted 0
            foreach _oname of local omit {
                // Check if pattern contains wildcards
                if strpos("`_oname'", "*") > 0 | strpos("`_oname'", "?") > 0 {
                    // Convert glob to Stata regex: * -> .* and ? -> .
                    local _opat = subinstr("`_oname'", ".", "\.", .)
                    local _opat = subinstr("`_opat'", "*", ".*", .)
                    local _opat = subinstr("`_opat'", "?", ".", .)
                    if regexm("`_cname'", "^`_opat'$") local _is_omitted 1
                }
                else {
                    // Exact match (original behavior)
                    if "`_cname'" == "`_oname'" local _is_omitted 1
                }
            }
            if `_is_omitted' continue
        }

        // -- FILTER 3: show() -- keep only named coefficients --
        // v3.6.0: supports glob patterns: * matches any chars, ? matches one char
        // Examples: show(mpg weight) keeps only mpg and weight (exact)
        //           show(*foreign*) keeps anything containing "foreign"
        if "`show'" != "" {
            local _is_shown 0
            foreach _sname of local show {
                if strpos("`_sname'", "*") > 0 | strpos("`_sname'", "?") > 0 {
                    local _spat = subinstr("`_sname'", ".", "\.", .)
                    local _spat = subinstr("`_spat'", "*", ".*", .)
                    local _spat = subinstr("`_spat'", "?", ".", .)
                    if regexm("`_cname'", "^`_spat'$") local _is_shown 1
                }
                else {
                    if "`_cname'" == "`_sname'" local _is_shown 1
                }
            }
            if !`_is_shown' continue
        }

        // -- Compute CI, t/z statistic, and p-value --
        local _lower = `_coef' - `_crit' * `_se'
        local _upper = `_coef' + `_crit' * `_se'
        local _lower2 = `_coef' - `_crit2' * `_se'  // inner CI (v3.6.0-s7d)
        local _upper2 = `_coef' + `_crit2' * `_se'
        if `_se' > 0 {
            local _tzval = `_coef' / `_se'
            if `_use_t' {
                local _pval = 2 * ttail(`_df', abs(`_tzval'))
            }
            else {
                local _pval = 2 * normal(-abs(`_tzval'))
            }
        }
        else {
            local _tzval = .
            local _pval = .
        }

        // -- Build display name --
        // Start with raw coefficient name, then clean up
        local _dname "`_cname'"

        // Multi-equation: prepend equation name only if >1 equation
        if !`_single_eq' & "`_ceq'" != "_" & "`_ceq'" != "" {
            local _dname "`_ceq':`_dname'"
        }

        // Clean up factor variable notation for display.
        // Strategy: extract fval and fvar FIRST using regex that handles
        // b./bn./o./on. suffixes (e.g. margins,post stores '1b.rep78'),
        // then apply c. removal and # -> x substitution to the remainder.
        // Remove c. prefix (continuous variable marker)
        local _dname = subinstr("`_dname'", "c.", "", .)
        // Replace # with " x " for interactions
        local _dname = subinstr("`_dname'", "#", " x ", .)
        // Clean up leading numeric factor level with optional b/bn/o/on suffix:
        // "1.rep78" "1b.rep78" "1bn.rep78" "1o.rep78" -> value label or varname=val
        if regexm("`_dname'", "^([0-9]+)[bon]*\.(.+)$") {
            local _fval = regexs(1)
            local _fvar = regexs(2)
            // Try to get value label for this variable and value
            capture {
                local _vlbl : label (`_fvar') `_fval'
            }
            if _rc == 0 & "`_vlbl'" != "" & "`_vlbl'" != "`_fval'" {
                local _dname "`_vlbl'"
            }
            else {
                // No value label: show as "varname=value"
                local _dname "`_fvar'=`_fval'"
            }
        }
        // Handle interaction display names with value labels
        // After # -> " x " substitution, we may have "1.foreign x mpg"
        // This needs recursive cleanup but for v1, basic cleanup is sufficient

        // Rename _cons to Constant
        if "`_cname'" == "_cons" local _dname "Constant"

        // -- FILTER 4: coeflbl() overrides --
        // Format: coeflbl(rawname "Display Label"|rawname2 "Label 2")
        if "`coeflbl'" != "" {
            // Split on pipe
            local _clbl_rest "`coeflbl'"
            while "`_clbl_rest'" != "" {
                // Get next pipe-separated entry
                gettoken _clbl_entry _clbl_rest : _clbl_rest, parse("|")
                if "`_clbl_entry'" == "|" continue
                // Parse: first word is coefname, rest is label
                local _clbl_entry = itrim("`_clbl_entry'")
                gettoken _clbl_key _clbl_val : _clbl_entry
                local _clbl_val = itrim("`_clbl_val'")
                // Strip surrounding quotes if present
                local _clbl_val = subinstr("`_clbl_val'", `"""', "", .)
                if "`_clbl_key'" == "`_cname'" & "`_clbl_val'" != "" {
                    local _dname "`_clbl_val'"
                }
            }
        }

        // -- Append to output strings --
        // Format numbers to sufficient precision
        local _coef_fmt : display %12.7g `_coef'
        local _coef_fmt = itrim("`_coef_fmt'")
        local _se_fmt : display %12.7g `_se'
        local _se_fmt = itrim("`_se_fmt'")
        local _lower_fmt : display %12.7g `_lower'
        local _lower_fmt = itrim("`_lower_fmt'")
        local _upper_fmt : display %12.7g `_upper'
        local _upper_fmt = itrim("`_upper_fmt'")
        local _pval_fmt : display %12.7g `_pval'
        local _pval_fmt = itrim("`_pval_fmt'")
        local _tz_fmt : display %12.7g `_tzval'
        local _tz_fmt = itrim("`_tz_fmt'")

        // Pipe-separate: first entry has no leading pipe
        if `_nkept' == 0 {
            local _out_names  "`_dname'"
            local _out_coefs  "`_coef_fmt'"
            local _out_lower  "`_lower_fmt'"
            local _out_upper  "`_upper_fmt'"
            if `_has_level2' {
                local _lower2_fmt : display %12.7g `_lower2'
                local _lower2_fmt = itrim("`_lower2_fmt'")
                local _upper2_fmt : display %12.7g `_upper2'
                local _upper2_fmt = itrim("`_upper2_fmt'")
                local _out_lower2 "`_lower2_fmt'"
                local _out_upper2 "`_upper2_fmt'"
            }
            local _out_ses    "`_se_fmt'"
            local _out_pvals  "`_pval_fmt'"
            local _out_tzvals "`_tz_fmt'"
        }
        else {
            local _out_names  "`_out_names'|`_dname'"
            local _out_coefs  "`_out_coefs'|`_coef_fmt'"
            local _out_lower  "`_out_lower'|`_lower_fmt'"
            local _out_upper  "`_out_upper'|`_upper_fmt'"
            if `_has_level2' {
                local _lower2_fmt : display %12.7g `_lower2'
                local _lower2_fmt = itrim("`_lower2_fmt'")
                local _upper2_fmt : display %12.7g `_upper2'
                local _upper2_fmt = itrim("`_upper2_fmt'")
                local _out_lower2 "`_out_lower2'|`_lower2_fmt'"
                local _out_upper2 "`_out_upper2'|`_upper2_fmt'"
            }
            local _out_ses    "`_out_ses'|`_se_fmt'"
            local _out_pvals  "`_out_pvals'|`_pval_fmt'"
            local _out_tzvals "`_out_tzvals'|`_tz_fmt'"
        }
        local _nkept = `_nkept' + 1
    }

    // -- Validate we have coefficients to plot --
    if `_nkept' == 0 {
        display as error "coefplot: no coefficients remain after filtering."
        display as error "  Check omit()/show()/nobase options."
        exit 198
    }

    display as text "  Post-estimation: `_nkept' coefficients from `e(cmd)'"
    display as text "  Dependent variable: `_depvar', N = `_nobs'"
    if `_use_t' {
        display as text "  CI: `cilevel'% (t-distribution, df = `_df')"
    }
    else {
        display as text "  CI: `cilevel'% (z / normal distribution)"
    }

    // -- Set c_locals in caller --
    c_local _pe_names  "`_out_names'"
    c_local _pe_coefs  "`_out_coefs'"
    c_local _pe_lower  "`_out_lower'"
    c_local _pe_upper  "`_out_upper'"
    c_local _pe_lower2 "`_out_lower2'"   // v3.6.0-s7d: inner CI bounds
    c_local _pe_upper2 "`_out_upper2'"
    c_local _pe_ses    "`_out_ses'"
    c_local _pe_pvals  "`_out_pvals'"
    c_local _pe_tzvals "`_out_tzvals'"
    c_local _pe_nobs   "`_nobs'"
    c_local _pe_depvar "`_depvar'"
    c_local _pe_bases  "`_out_bases'"
    c_local _pe_tstat  "`_use_t'"
    c_local _pe_cmd    "`e(cmd)'"
    c_local _pe_fitstats "`_pe_fitstats'"   // v3.6.0-s6c: pipe-sep key=value fit stats
end
