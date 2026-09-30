*! sparkta version 3.6.0 2026-09-13
*! t2j fix8z: every value-label lookup goes through sparkta_mg_vlabel (results() source labels first, then the
*!   data in memory) -- helpers live in their own ado files so they auto-load
*! t2j fix8w: unlabelled factor levels on the x axis read "1", "2" (Stata's marginsplot look); the x title names the variable
*! component of sparkta: reads r(table)/r(at) for marginsplot -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// =========================================================================
// SUBROUTINE: sparkta_read_margins (v3.6.0-s8a)
// Reads r(table), r(at) from stored margins results.
// Extracts margins, CIs, x-axis labels, series (over()) groups.
// Sets c_local variables in the caller for pipe-separated Java args.
//
// c_locals set in caller:
//   _mp_data   -- pipe-sep point estimates  (arg 175)
//   _mp_upper  -- pipe-sep CI upper bounds  (arg 176)
//   _mp_lower  -- pipe-sep CI lower bounds  (arg 177)
//   _mp_xlab   -- pipe-sep x-axis labels   (arg 178)
//   _mp_ylab   -- y-axis expression string  (arg 179)
//   _mp_series -- pipe-sep series labels    (arg 203)
//   _mp_xpos   -- pipe-sep numeric x pos   (arg 204)  empty = categorical
//
// Pattern detection (see sparkta_margins_reference.md Part 3):
//   has_at    -- colnames contain _at  -> continuous x from r(at)
//   has_hash  -- colnames contain #    -> interaction: x + series split
//   has_paren -- colnames start with ( -> contrast/pairwise: use as-is
//   else      -- single factor variable: parse N.varname -> value label
// =========================================================================
program sparkta_read_margins
    version 17
    syntax [, CILevel(string) CILevel2(string) CISTyle(string) OVER(string)]

    // -- Defaults --
    if "`cilevel'" == "" local cilevel "95"

    // -- Validate r(table) present --
    capture confirm matrix r(table)
    if _rc != 0 {
        display as error "sparkta_read_margins: r(table) not found."
        display as error "  Run margins first, then sparkta, type(marginsplot)."
        exit 198
    }

    // -- Read r(table) --
    tempname _rt
    // v3.6.0-s8d: margins, pwcompare stores the pairwise comparisons in
    // r(table_vs) (stripes 2vs1.rep78 ...) and the plain margins in r(table).
    // Plot the comparisons when r(table_vs) exists.
    capture confirm matrix r(table_vs)
    if _rc == 0 {
        matrix `_rt' = r(table_vs)
        display as text "  pwcompare detected: reading r(table_vs)"
    }
    else matrix `_rt' = r(table)
    local _k = colsof(`_rt')
    // Reconstruct full factor-variable column names from colnames + coleq.
    // r(table) from margins stores factor variables as:
    //   coleq  = varname (e.g. 'rep78')  colname = level (e.g. '1')
    // We reconstruct '1.rep78' so our regex patterns work correctly.
    // For continuous at(): coleq='_at', colname='1','2',... -> '1._at'
    // For plain scalars (e.g. _cons): coleq='_' -> use colname as-is.
    local _colnames_raw : colnames `_rt'
    local _coleqs_raw   : coleq    `_rt'
    local _colnames ""
    forvalues _ci = 1/`_k' {
        local _cn_raw : word `_ci' of `_colnames_raw'
        local _eq_raw : word `_ci' of `_coleqs_raw'
        if "`_eq_raw'" != "" & "`_eq_raw'" != "_" {
            // Skip coleq if it looks like a dydx derivative variable
            // (plain variable name, no #, not _at, no factor notation).
            // dydx results have coleq=varname (e.g. "weight") and
            // colname=factor level (e.g. "1b.rep78") -- we only want colname.
            local _eq_is_factor = (regexm("`_eq_raw'", "^[0-9]") | strpos("`_eq_raw'", "#") | "`_eq_raw'" == "_at")
            if `_eq_is_factor' {
                local _full "`_cn_raw'.`_eq_raw'"
            }
            else if regexm("`_cn_raw'", "^[0-9]") & `_eq_is_factor' {
                // colname AND coleq are both factor-like -- combine
                local _full "`_cn_raw'.`_eq_raw'"
            }
            else {
                // coleq is plain varname (dydx case) -- use colname only
                local _full "`_cn_raw'"
            }
        }
        else {
            local _full "`_cn_raw'"
        }
        local _colnames "`_colnames' `_full'"
    }
    local _colnames = strtrim("`_colnames'")

    // -- Detect y-axis label from current expression (best effort) --
    local _ylab ""
    // margins stores expression description in r(expression) in newer Stata
    // v3.6.0-s8f: r(expression) is "predict()" -- uninformative. Prefer the
    // human label Stata itself prints: r(predict1_label) ("Linear prediction",
    // "Pr(foreign)"), then qualify for effects: contrasts / dy/dx.
    capture local _ylab `"`r(predict1_label)'"'
    if `"`_ylab'"' == "" capture local _ylab `"`r(expression)'"'
    if `"`_ylab'"' == "" local _ylab "Margin"
    local _mp_cl `"`r(cmdline)'"'
    // NOTE: never put `=regexs(1)' on the same line as the if -- Stata expands
    // macros before evaluating the condition, so it ran (and printed "invalid
    // number") even when the regex did not match (s8f bug: 4 lines per chart).
    local _mp_dtype ""
    foreach _dt in dydx eyex dyex eydx {
        if "`_mp_dtype'" == "" & regexm(`"`_mp_cl'"', "`_dt'\(([^)]*)\)") {
            local _mp_dtype "`_dt'"
            local _mp_dvar = regexs(1)
        }
    }
    if "`_mp_dtype'" != "" {
        local _mp_dlbl = subinstr(subinstr(subinstr(subinstr("`_mp_dtype'", "dydx", "dy/dx", .), "eyex", "ey/ex", .), "dyex", "dy/ex", .), "eydx", "ey/dx", .)
        local _ylab "`_mp_dlbl' (`_mp_dvar') on `_ylab'"
    }
    else if regexm(`"`_mp_cl'"', "pwcompare")           local _ylab "Pairwise contrast of `_ylab'"
    else if regexm(`"`_mp_cl'"', "contrast|margins +[a-z]{1,2}\.[A-Za-z_]") local _ylab "Contrast of `_ylab'"

    // -- Detect pattern --
    local _has_at    = (regexm("`_colnames'", "_at") > 0)
    local _has_hash  = (regexm("`_colnames'", "#") > 0)
    local _has_paren = (regexm("`_colnames'", "^\(") > 0)

    // Also check if ANY colname starts with (
    local _paren_any 0
    forvalues _ci = 1/`_k' {
        local _cn : word `_ci' of `_colnames'
        if substr("`_cn'", 1, 1) == "(" {
            local _paren_any 1
            continue, break
        }
    }
    if `_paren_any' local _has_paren 1
    // v3.6.0-s8b: pwcompare stripes reconstruct as "2vs1.rep78" (coleq=var, colname=NvsM)
    if regexm("`_colnames'", "(^| )[0-9]+vs[0-9]+\.") local _has_paren 1

    display as text "  sparkta_read_margins: k=`_k', has_at=`_has_at', has_hash=`_has_hash', has_paren=`_has_paren'"

    // -- Read r(at) if at() was used --
    local _rat_xvar ""
    local _rat_is_factor 0
    local _rat_xlablist ""
    local _rat_rows 0
    tempname _rat
    if `_has_at' {
        capture confirm matrix r(at)     // t2g fix 1: a missing r(at) would copy as a 1x1 missing
        if _rc == 0 matrix `_rat' = r(at)
        if _rc == 0 {
            local _rat_rows = rowsof(`_rat')
            local _rat_cols : colnames `_rat'
            // Identify x variable: pick the r(at) column with most variation
            // (r(xvars) is unreliable across Stata versions; most-varying is robust)
            local _rat_xvar : word 1 of `_rat_cols'
            local _rat_maxv 0
            local _rat_varying ""     // v3.6.0-s8b: base names of every varying at() column
            local _rat_ci 0
            foreach _rtc of local _rat_cols {
                local _rat_ci = `_rat_ci' + 1
                // v3.6.0-s8b: scan ALL rows (not just 1 vs N) so a column that
                // varies only in the middle (e.g. at(x=(0 5 0))) is still detected.
                // Range = max - min over rows with non-missing values.
                capture noisily {
                    scalar __vmin = .
                    scalar __vmax = .
                    scalar __vcnt = 0
                    forvalues _rri = 1/`_rat_rows' {
                        scalar __vr = el(`_rat', `_rri', `_rat_ci')
                        if !missing(scalar(__vr)) {
                            scalar __vcnt = scalar(__vcnt) + 1
                            if missing(scalar(__vmin)) | scalar(__vr) < scalar(__vmin) scalar __vmin = scalar(__vr)
                            if missing(scalar(__vmax)) | scalar(__vr) > scalar(__vmax) scalar __vmax = scalar(__vr)
                        }
                    }
                    local _rtc_var = scalar(__vmax) - scalar(__vmin)
                    // require the column to be fully populated AND varying
                    if scalar(__vcnt) == `_rat_rows' & !missing(`_rtc_var') & `_rtc_var' > 0 {
                        local _rtc_base "`_rtc'"
                        if regexm("`_rtc'", "^[0-9]+[bon]*\.(.+)$") local _rtc_base = regexs(1)
                        if strpos(" `_rat_varying' ", " `_rtc_base' ") == 0 local _rat_varying "`_rat_varying' `_rtc_base'"
                    }
                    if scalar(__vcnt) == `_rat_rows' & !missing(`_rtc_var') & `_rtc_var' > `_rat_maxv' {
                        local _rat_maxv `_rtc_var'
                        local _rat_xvar "`_rtc'"
                        local _rat_xcol `_rat_ci'  // numeric column index
                    }
                }
            }
            capture scalar drop __vmin __vmax __vcnt __vr
            // v3.6.0-s8b: more than one varying at() variable is not a single x-axis.
            local _rat_nvary : word count `_rat_varying'
            if `_rat_nvary' > 1 {
                display as error "marginsplot: at() varies over more than one variable (`=trim("`_rat_varying'")')."
                display as error "  sparkta plots one continuous x-axis. Use one at() variable, and put the"
                display as error "  second dimension in the margins varlist, e.g. margins foreign, at(weight=(...))."
                exit 198
            }
            // v3.6.0-s8b: factor at(), e.g. at(foreign=(0 1)) -> r(at) columns are
            // indicator columns 0b.foreign / 1.foreign. Treat as CATEGORICAL: x labels
            // are the value labels of the level whose indicator is 1 in each row.
            local _rat_is_factor 0
            local _rat_xlablist ""
            if regexm("`_rat_xvar'", "^[0-9]+[bon]*\.(.+)$") {
                local _rat_is_factor 1
                local _rat_fvar = regexs(1)
                capture local _rat_vl : value label `_rat_fvar'
                forvalues _rri = 1/`_rat_rows' {
                    local _rlev ""
                    local _rci 0
                    foreach _rtc of local _rat_cols {
                        local _rci = `_rci' + 1
                        if regexm("`_rtc'", "^([0-9]+)[bon]*\.`_rat_fvar'$") {
                            local _rk = regexs(1)
                            capture noisily scalar __vRow = el(`_rat', `_rri', `_rci')
                            if _rc == 0 & !missing(scalar(__vRow)) {
                                if scalar(__vRow) == 1 local _rlev "`_rk'"
                            }
                        }
                    }
                    local _rlab "`_rlev'"   // t2j fix8w: level only; the x title carries the variable label (was varname=level)
                    if "`_rat_vl'" != "" & "`_rlev'" != "" {
                        capture local _rlab2 : label `_rat_vl' `_rlev'
                        if _rc == 0 & "`_rlab2'" != "" local _rlab "`_rlab2'"
                    }
                    local _rlab = subinstr("`_rlab'", "|", "/", .)
                    if "`_rat_xlablist'" == "" local _rat_xlablist "`_rlab'"
                    else                       local _rat_xlablist "`_rat_xlablist'|`_rlab'"
                }
                local _rat_xvar "`_rat_fvar'"
            }
            display as text "  r(at): `_rat_rows' conditions, x-variable: `_rat_xvar'"
            // Build xpos list using el() -- identical pattern to detection loop above
            // (detection proved el(`_rat', row, col) works for this matrix)
            local _rat_xlist ""
            forvalues _ri = 1/`_rat_rows' {
                capture noisily scalar __vRow = el(`_rat', `_ri', `_rat_xcol')
                if _rc == 0 & !missing(scalar(__vRow)) {
                    local _rxvf : display %12.0g scalar(__vRow)
                    local _rxvf = trim("`_rxvf'")
                    if "`_rat_xlist'" == "" local _rat_xlist "`_rxvf'"
                    else                    local _rat_xlist "`_rat_xlist'|`_rxvf'"
                }
                else {
                    if "`_rat_xlist'" != "" local _rat_xlist "`_rat_xlist'|"
                }
            }
            if "`_rat_xlist'" == "" {
                display as text "  note: could not read at() values from r(at); x-axis will be sequential"
            }
        }
        else {
            display as text "  WARNING: _at in colnames but r(at) not found. Using sequential x."
            local _has_at 0
        }
    }

    // -- Build output strings --
    // For interaction/series: collect unique series labels
    local _series_labels ""
    local _n_series 0
    local _paren_var ""        // v3.6.0-s8b: pwcompare variable name

    // Pass 1: collect unique series labels (from # second component or over())
    if `_has_hash' {
        forvalues _ci = 1/`_k' {
            local _cn : word `_ci' of `_colnames'
            // Split on #: first part = x dimension, second part = series
            local _xpart ""
            local _spart ""
            local _rest "`_cn'"
            gettoken _xpart _rest : _rest, parse("#")
            if "`_rest'" != "" {
                // Remove leading #
                local _rest = substr("`_rest'", 2, .)
                local _spart = "`_rest'"
            }
            if "`_spart'" != "" {
                // Normalize spart: strip base/omitted notation (e.g. "0bn.foreign" -> "0.foreign")
                local _spart_norm "`_spart'"
                if regexm("`_spart'", "^([0-9]+)[bon]+\.(.+)$") {
                    local _spart_norm = regexs(1) + "." + regexs(2)
                }
                // Check if series already collected (by normalized label)
                local _found 0
                forvalues _si = 1/`_n_series' {
                    if "`_spart_norm'" == "`_slab_`_si''" local _found 1
                }
                if !`_found' {
                    local _n_series = `_n_series' + 1
                    local _slab_`_n_series' "`_spart_norm'"
                    // Try to get display label (value label or cleaned name)
                    local _slbl "`_spart_norm'"
                    if regexm("`_spart_norm'", "^([0-9]+)\.(.+)$") {
                        local _sv = regexs(1)
                        local _svr = regexs(2)
                        sparkta_mg_vlabel `_svr' `_sv'
                        local _svlbl "`_mgvl'"
                        if "`_svlbl'" != "" & "`_svlbl'" != "`_sv'" {
                            local _slbl "`_svlbl'"
                        }
                        else {
                            local _slbl "`_svr'=`_sv'"
                        }
                    }
                    local _series_labels "`_series_labels'`_slbl'|"
                }
            }
        }
    }

    // Initialise output accumulators per series (or single series if no hash)
    local _n_eff_series = max(1, `_n_series')
    forvalues _si = 1/`_n_eff_series' {
        local _out_data_`_si'  ""
        local _out_upper_`_si' ""
        local _out_lower_`_si' ""
        local _out_xlab_`_si'  ""
        local _out_xpos_`_si'  ""
        local _nkept_`_si' 0
    }

    // Pass 2: iterate columns and assign to correct series
    local _prev_xpart ""
    local _at_idx 0
    forvalues _ci = 1/`_k' {
        local _cn : word `_ci' of `_colnames'

        // Extract values from r(table)
        local _b   = `_rt'[1, `_ci']
        local _ll  = `_rt'[5, `_ci']
        local _ul  = `_rt'[6, `_ci']

        // Format
        local _bfmt  : display %12.7g `_b'
        local _bfmt  = itrim("`_bfmt'")
        local _llfmt : display %12.7g `_ll'
        local _llfmt = itrim("`_llfmt'")
        local _ulfmt : display %12.7g `_ul'
        local _ulfmt = itrim("`_ulfmt'")
        // Level2 CI: compute from se and df if cilevel2 is provided
        local _lower2_fmt ""
        local _upper2_fmt ""
        if "`cilevel2'" != "" {
            local _se  = `_rt'[2, `_ci']
            local _df  = `_rt'[7, `_ci']
            capture {
                local _alpha2 = (100 - real("`cilevel2'")) / 200
                if `_df' > 0 & !missing(`_df') {
                    local _crit2 = invttail(`_df', `_alpha2')
                }
                else {
                    local _crit2 = invnormal(1 - `_alpha2')
                }
                local _ll2 = `_b' - `_crit2' * `_se'
                local _ul2 = `_b' + `_crit2' * `_se'
                local _lower2_fmt : display %12.7g `_ll2'
                local _lower2_fmt = itrim("`_lower2_fmt'")
                local _upper2_fmt : display %12.7g `_ul2'
                local _upper2_fmt = itrim("`_upper2_fmt'")
            }
        }

        // Determine series index and x-label for this column
        local _si_cur 1
        local _xlab_cur ""
        local _xpos_cur ""

        if `_has_paren' {
            // Pattern 5/9: pairwise (pwcompare) -- colname "(2 vs 1).rep78"
            // v3.6.0-s8b: label = "2 vs 1"; variable name goes to x-title via _paren_var
            if regexm("`_cn'", "^\((.+)\)\.(.+)$") {
                local _xlab_cur = regexs(1)
                if "`_paren_var'" == "" local _paren_var = regexs(2)
            }
            else if regexm("`_cn'", "^([0-9]+)vs([0-9]+)[bon]*\.(.+)$") {   // s8f: allow base marker 2vs1bn.rep78
                local _xlab_cur = regexs(1) + " vs " + regexs(2)
                if "`_paren_var'" == "" local _paren_var = regexs(3)
            }
            else local _xlab_cur "`_cn'"
            local _si_cur 1
        }
        else if `_has_hash' {
            // Pattern 2/3: interaction -- split on # for x-part and series-part
            local _xpart ""
            local _spart ""
            local _rest_h "`_cn'"
            gettoken _xpart _rest_h : _rest_h, parse("#")
            if "`_rest_h'" != "" {
                local _rest_h = substr("`_rest_h'", 2, .)
                local _spart = "`_rest_h'"
            }
            // Build x-label from xpart (N.varname -> value label)
            local _xlab_cur = "`_xpart'"
            // If xpart is an _at index (e.g. "1._at"), get numeric xpos from pre-built list
            if `_has_at' & regexm("`_xpart'", "^([0-9]+)\._at$") {
                local _at_num2 = regexs(1)
                if "`_at_num2'" != "" & "`_rat_xlist'" != "" {
                    local _xpos_cur : word `_at_num2' of `: subinstr local _rat_xlist "|" " ", all'
                    local _xpos_cur = trim("`_xpos_cur'")
                    local _xlab_cur "`_xpos_cur'"
                }
            }
            else if regexm("`_xpart'", "^([0-9]+)[bon]*\.(.+)$") {
                local _fval = regexs(1)
                local _fvar = regexs(2)
                sparkta_mg_vlabel `_fvar' `_fval'
                local _vlbl "`_mgvl'"
                if "`_vlbl'" != "" & "`_vlbl'" != "`_fval'" {
                    local _xlab_cur "`_vlbl'"
                }
                else {
                    local _xlab_cur "`_fval'"   // t2j fix8w: level only (was varname=level)
                }
            }
            // Determine which series this belongs to
            local _spart_nm "`_spart'"
            if regexm("`_spart'", "^([0-9]+)[bon]+\.(.+)$") local _spart_nm = regexs(1) + "." + regexs(2)
            forvalues _si = 1/`_n_series' {
                if "`_spart_nm'" == "`_slab_`_si''" {
                    local _si_cur `_si'
                    continue, break
                }
            }
        }
        else if `_has_at' {
            // Pattern 4: continuous at() -- x comes from r(at)
            // Column may be "N._at" or "N._at#M.series"
            local _atpart ""
            local _spart  ""
            local _rest_a "`_cn'"
            gettoken _atpart _rest_a : _rest_a, parse("#")
            if "`_rest_a'" != "" {
                local _rest_a = substr("`_rest_a'", 2, .)
                local _spart = "`_rest_a'"
            }
            // Extract at index from "_atpart": e.g. "2._at" -> 2
            local _at_num ""
            if regexm("`_atpart'", "^([0-9]+)\._at") {
                local _at_num = regexs(1)
            }
            // Get x position from r(at) matrix
            // Get x position from pre-built xpos list (word-indexed, 1-based)
            if "`_at_num'" != "" & "`_rat_xlist'" != "" {
                local _xpos_cur : word `_at_num' of `: subinstr local _rat_xlist "|" " ", all'
                local _xpos_cur = trim("`_xpos_cur'")
                if "`_xpos_cur'" != "" & "`_xpos_cur'" != "." {
                    local _xlab_cur = "`_xpos_cur'"
                }
            }
            // Series from second # component
            if "`_spart'" != "" {
                local _si_cur 1
                forvalues _si = 1/`_n_series' {
                    if "`_spart'" == "`_slab_`_si''" {
                        local _si_cur `_si'
                        continue, break
                    }
                }
            }
        }
        else {
            // Pattern 1: single factor variable -- N.varname -> value label
            local _xlab_cur "`_cn'"
            // Handle contrast notation: r1vs0.foreign@Nbon.varname -> varname=N
            if regexm("`_cn'", "^r[0-9]+vs[0-9]+\.[^@]+@([0-9]+)[bon]*\.(.+)$") {
                local _cval = regexs(1)
                local _cvar = regexs(2)
                sparkta_mg_vlabel `_cvar' `_cval'
                local _cvlbl "`_mgvl'"
                if "`_cvlbl'" != "" & "`_cvlbl'" != "`_cval'" {
                    local _xlab_cur "`_cvlbl'"
                }
                else {
                    local _xlab_cur "`_cval'"   // t2j fix8w: level only (was varname=level)
                }
            }
            else if regexm("`_cn'", "^([0-9]+)[bon]*\.(.+)$") {
                local _fval = regexs(1)
                local _fvar = regexs(2)
                sparkta_mg_vlabel `_fvar' `_fval'
                local _vlbl "`_mgvl'"
                if "`_vlbl'" != "" & "`_vlbl'" != "`_fval'" {
                    local _xlab_cur "`_vlbl'"
                }
                else {
                    local _xlab_cur "`_fval'"   // t2j fix8w: level only (was varname=level)
                }
            }
            else if "`_cn'" == "_cons" {
                local _xlab_cur "Overall"
            }
            local _si_cur 1
        }

        // Append to correct series accumulator
        if `_nkept_`_si_cur'' == 0 {
            local _out_data_`_si_cur'  "`_bfmt'"
            local _out_upper_`_si_cur' "`_ulfmt'"
            local _out_lower_`_si_cur' "`_llfmt'"
            local _out_lower2_`_si_cur' "`_lower2_fmt'"
            local _out_upper2_`_si_cur' "`_upper2_fmt'"
            local _out_xlab_`_si_cur'  "`_xlab_cur'"
            local _out_xpos_`_si_cur'  "`_xpos_cur'"
        }
        else {
            local _out_data_`_si_cur'  "`_out_data_`_si_cur''|`_bfmt'"
            local _out_upper_`_si_cur' "`_out_upper_`_si_cur''|`_ulfmt'"
            local _out_lower_`_si_cur' "`_out_lower_`_si_cur''|`_llfmt'"
            local _out_lower2_`_si_cur' "`_out_lower2_`_si_cur''|`_lower2_fmt'"
            local _out_upper2_`_si_cur' "`_out_upper2_`_si_cur''|`_upper2_fmt'"
            local _out_xlab_`_si_cur'  "`_out_xlab_`_si_cur''|`_xlab_cur'"
            local _out_xpos_`_si_cur'  "`_out_xpos_`_si_cur''|`_xpos_cur'"
        }
        local _nkept_`_si_cur' = `_nkept_`_si_cur'' + 1
    }

    // -- Combine series into tilde-separated blocks --
    // Each series block is pipe-separated internally; series separated by ~
    local _mp_data  ""
    local _mp_upper ""
    local _mp_lower ""
    local _mp_lower2 ""
    local _mp_upper2 ""
    local _mp_xlab  ""
    local _mp_xpos  ""
    forvalues _si = 1/`_n_eff_series' {
        if `_si' == 1 {
            local _mp_data  "`_out_data_1'"
            local _mp_upper "`_out_upper_1'"
            local _mp_lower "`_out_lower_1'"
            local _mp_lower2 "`_out_lower2_1'"
            local _mp_upper2 "`_out_upper2_1'"
            local _mp_xlab  "`_out_xlab_1'"
            local _mp_xpos  "`_out_xpos_1'"
        }
        else {
            local _mp_data  "`_mp_data'~`_out_data_`_si''"
            local _mp_upper "`_mp_upper'~`_out_upper_`_si''"
            local _mp_lower "`_mp_lower'~`_out_lower_`_si''"
            local _mp_lower2 "`_mp_lower2'~`_out_lower2_`_si''"
            local _mp_upper2 "`_mp_upper2'~`_out_upper2_`_si''"
            local _mp_xlab  "`_mp_xlab'~`_out_xlab_`_si''"
            local _mp_xpos  "`_mp_xpos'~`_out_xpos_`_si''"
        }
    }
    // Override _mp_xpos from _rat_xlist when has_at: avoids per-obs word() failures
    if `_has_at' & "`_rat_xlist'" != "" {
        if `_rat_is_factor' {
            // categorical x: labels from value labels; xpos empty -> Java numericX=false
            local _mp_xlab "`_rat_xlablist'"
            local _mp_xpos ""
            forvalues _rri = 2/`_rat_rows' {
                local _mp_xpos "`_mp_xpos'|"
            }
        }
        else {
            local _mp_xpos "`_rat_xlist'"
            local _mp_xlab "`_rat_xlist'"   // v3.6.0-s8b: labels = at() values (fixes empty first label)
        }
        local _mp_xpos1 "`_mp_xpos'"
        local _mp_xlab1 "`_mp_xlab'"
        forvalues _si = 2/`_n_eff_series' {
            local _mp_xpos "`_mp_xpos'~`_mp_xpos1'"
            local _mp_xlab "`_mp_xlab'~`_mp_xlab1'"
        }
    }

    // -- Report --
    local _total_kept = 0
    forvalues _si = 1/`_n_eff_series' {
        local _total_kept = `_total_kept' + `_nkept_`_si''
    }
    display as text "  Margins read: `_total_kept' values, `_n_eff_series' series"
    if "`_rat_xvar'" != "" {
        display as text "  x-variable: `_rat_xvar' (continuous from r(at))"
    }

    // -- Set c_locals in caller (tilde-safety: tilde separates series, pipe separates items) --
    c_local _mp_data   "`_mp_data'"
    c_local _mp_upper  "`_mp_upper'"
    c_local _mp_lower  "`_mp_lower'"
    c_local _mp_xlab   "`_mp_xlab'"
    c_local _mp_ylab   "`_ylab'"
    c_local _mp_series "`_series_labels'"
    c_local _mp_xpos   "`_mp_xpos'"
    // s8n: plain factor margins (margins rep78 / rep78#foreign): x variable = the
    // factor in the first column name, so the caller can title the x-axis.
    if "`_rat_xvar'" == "" & !`_has_at' {
        local _cn1 : word 1 of `_colnames'
        if strpos("`_cn1'", "@") > 0 {
            local _cn1 = subinstr("`_cn1'", "@", " ", .)   // contrast stripe r1vs0.foreign@1.rep78 -> the @-part
            local _cn1 : word `=wordcount("`_cn1'")' of `_cn1'
        }
        local _cn1 = subinstr("`_cn1'", "#", " ", .)      // interaction 1.rep78#0.foreign -> the first part
        local _cn1 : word 1 of `_cn1'
        if regexm("`_cn1'", "^[0-9]+[bon]*\.([A-Za-z_][A-Za-z0-9_]*)$") local _rat_xvar = regexs(1)
    }
    if "`_rat_xvar'" == "" & "`_paren_var'" != "" local _rat_xvar "`_paren_var'"
    c_local _mp_xvar   "`_rat_xvar'"   // v3.6.0-s8b: continuous at() variable name (for x-title/tooltip)
    c_local _pe_lower2 "`_mp_lower2'"   // level2 inner CI lower
    c_local _pe_upper2 "`_mp_upper2'"   // level2 inner CI upper
end

