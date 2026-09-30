*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: matrix() mode reader (Jann grammar + autodetect) -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split)
*! t2f fix 2: autodetect also knows lwdid names (watt, low_ci/up_ci, low_band/up_band)

// =============================================================================
// SUBROUTINE: sparkta_read_matrix (v3.6.0-t2a) -- MATRIX MODE reader
//   Reads point estimates (and, when available, SE / CI / p / df) from a Stata
//   matrix and returns the SAME contract as sparkta_read_eresults, so every
//   downstream stage (Java rendering, nested CIs, keys, table, multi-model
//   layout, show()/omit()/coeflabels()/order()/coefsort(), export) is unchanged.
//
//   Grammar follows Ben Jann's coefplot matrix mode:
//     matrix(M)        first row of M = estimates, column names = coefficients
//     matrix(M[2])     row 2 = estimates (statistics in rows)
//     matrix(M[,3])    column 3 = estimates (statistics in columns)
//     ci((2 3)) | ci(2 3) | ci(ll ul) | ci(C)    lower/upper by position, name, or from
//                                                rows 1-2 of another matrix C
//     se(#|name|S)     standard errors -> CI computed at cilevel (z, or t with df())
//     df(#|name)       degrees of freedom -> t distribution
//     pvalue(#|name)   p-values (sparkta extension; the r(table) row name)
//   Sources may be r(name) / e(name) as well as regular matrices.
//   AUTODETECT (sparkta extension): when positions are not given, statistics are
//   found by ROW/COLUMN NAME -- b/coef/estimate, se/stderr, ll/lb/lower, ul/ub/upper,
//   pvalue/p, df -- so matrix(r(table)) after any estimation, margins, csdid/jwdid/
//   lwdid "estat event", etc. is a one-liner.
//   EVENT TIME (with type(eventstudy)): coefficient names T-3, T, T+2, tm3, tp1, Tm3,
//   Tp0, lead3, lag2, -3.__event__ style are parsed to integers and the series is
//   ordered by time; Pre_avg / Post_avg style summary rows are kept after the series.
// =============================================================================
program sparkta_read_matrix
    version 17
    syntax, MATrix(string) [CI(string) SE(string) DF(string) PVALue(string) CILevel(string) LEVEL2(string) ///
                            OMIT(string) SHOW(string) COEFLbl(string) EVENTstudy]
    if "`cilevel'" == "" local cilevel "95"

    // ---- 1. parse the mspec: name, name[#], name[#,], name[#,.], name[,#], name[.,#] ----
    local _spec = trim(`"`matrix'"')
    local _mname "`_spec'"
    local _sel_row ""
    local _sel_col ""
    if regexm("`_spec'", "^(.+)\[(.*)\]$") {
        local _mname = regexs(1)
        local _idx   = regexs(2)
        local _idx = subinstr("`_idx'", " ", "", .)
        if regexm("`_idx'", "^([0-9]+)(,\.?)?$")          local _sel_row = regexs(1)
        else if regexm("`_idx'", "^(\.)?,([0-9]+)$")      local _sel_col = regexs(2)
        else {
            display as error "matrix(): cannot read the index in `_spec' -- use name[#] for a row or name[,#] for a column"
            exit 198
        }
    }
    tempname M
    capture matrix `M' = `_mname'
    if _rc {
        display as error "matrix(): `_mname' is not a matrix in memory"
        if regexm("`_mname'", "^[re]\(") display as error "  (run the command that produces it immediately before sparkta; r() results are cleared by other commands)"
        exit 111
    }
    local _nr = rowsof(`M')
    local _nc = colsof(`M')
    local _rnames : rownames `M'
    local _cnames : colnames `M'

    // ---- 2. orientation: stats in ROWS (coefficients across columns) or in COLUMNS ----
    // 1 = statistics are rows (Jann default), 0 = statistics are columns
    local _stat_rows -1
    if "`_sel_row'" != "" local _stat_rows 1
    if "`_sel_col'" != "" local _stat_rows 0
    local _kw_b   "b coef coefficient estimate estimates beta est theta att effect watt"
    local _kw_se  "se stderr std_err sd stde"
    local _kw_ll  "ll lb lower lci ci_l cil low min95 lower95 low_ci low_band"
    local _kw_ul  "ul ub upper uci ci_u ciu high max95 upper95 up_ci up_band"
    local _kw_p   "pvalue p pval p_value pv"
    local _kw_df  "df"
    if `_stat_rows' == -1 {
        local _rhit 0
        local _chit 0
        foreach _n of local _rnames {
            if strpos(" `_kw_b' `_kw_se' `_kw_ll' `_kw_ul' `_kw_p' ", " " + lower("`_n'") + " ") local _rhit 1
        }
        foreach _n of local _cnames {
            if strpos(" `_kw_b' `_kw_se' `_kw_ll' `_kw_ul' `_kw_p' ", " " + lower("`_n'") + " ") local _chit 1
        }
        if `_rhit' & !`_chit'      local _stat_rows 1
        else if `_chit' & !`_rhit' local _stat_rows 0
        else if `_nr' == 1         local _stat_rows 1
        else if `_nc' == 1         local _stat_rows 0
        else                       local _stat_rows 1     // Jann: row 1 = estimates
    }
    if `_stat_rows' {
        local _statnames "`_rnames'"
        local _coefnames "`_cnames'"
        local _nstat `_nr'
        local _ncoef `_nc'
    }
    else {
        local _statnames "`_cnames'"
        local _coefnames "`_rnames'"
        local _nstat `_nc'
        local _ncoef `_nr'
    }

    // ---- 3. locate each statistic: explicit position/name, else autodetect by name ----
    // _ix_*: index within the statistic dimension (0 = absent)
    local _ix_b 0
    local _ix_se 0
    local _ix_ll 0
    local _ix_ul 0
    local _ix_p 0
    local _ix_df 0
    local _stat_i 0
    foreach _n of local _statnames {
        local ++_stat_i
        local _ln = lower("`_n'")
        if `_ix_b'  == 0 & strpos(" `_kw_b' ",  " `_ln' ") local _ix_b  `_stat_i'
        if `_ix_se' == 0 & strpos(" `_kw_se' ", " `_ln' ") local _ix_se `_stat_i'
        if `_ix_ll' == 0 & strpos(" `_kw_ll' ", " `_ln' ") local _ix_ll `_stat_i'
        if `_ix_ul' == 0 & strpos(" `_kw_ul' ", " `_ln' ") local _ix_ul `_stat_i'
        if `_ix_p'  == 0 & strpos(" `_kw_p' ",  " `_ln' ") local _ix_p  `_stat_i'
        if `_ix_df' == 0 & strpos(" `_kw_df' ", " `_ln' ") local _ix_df `_stat_i'
    }
    if "`_sel_row'" != "" local _ix_b `_sel_row'
    if "`_sel_col'" != "" local _ix_b `_sel_col'
    if `_ix_b' == 0 local _ix_b 1                              // Jann default: first row/column
    if `_ix_b' > `_nstat' {
        local _dimword = cond(`_stat_rows', "rows", "columns")
        display as error "matrix(): `_mname' has only `_nstat' `_dimword' -- cannot take estimates from position `_ix_b'"
        exit 198
    }
    // helper: resolve "#" or "name" to an index in the statistic dimension
    local _resolve_err ""
    foreach _opt in se pvalue df {
        local _v = trim("``_opt''")
        if "`_v'" == "" continue
        if "`_opt'" == "df" & regexm("`_v'", "^[0-9.]+$") {
            local _df_const `_v'          // Jann: df(#) is the degrees of freedom for all coefficients
        }
        else if regexm("`_v'", "^[0-9]+$") {
            if `_v' > `_nstat' | `_v' < 1 local _resolve_err "`_opt'(`_v') is out of range (1-`_nstat')"
            else if "`_opt'" == "se"     local _ix_se `_v'
            else                          local _ix_p  `_v'
        }
        else {
            local _j 0
            local _found 0
            foreach _n of local _statnames {
                local ++_j
                if lower("`_n'") == lower("`_v'") {
                    local _found `_j'
                }
            }
            if `_found' == 0 {
                // maybe a separate matrix (se(S) or se(e(name))): one row/column of SEs
                capture confirm matrix `_v'
                if _rc & regexm("`_v'", "^[re]\([A-Za-z_0-9]+\)$") {
                    tempname SEM
                    capture matrix `SEM' = `_v'
                    if _rc == 0 {
                        local _v "`SEM'"
                        local _rc_se 0
                    }
                }
                capture confirm matrix `_v'
                if _rc == 0 & "`_opt'" == "se" local _se_matrix "`_v'"
                else local _resolve_err "`_opt'(`_v'): no row or column named `_v' in `_mname' -- names are: `_statnames'"
            }
            else if "`_opt'" == "se"     local _ix_se `_found'
            else if "`_opt'" == "pvalue" local _ix_p  `_found'
            else                          local _ix_df `_found'
        }
        if "`_resolve_err'" != "" {
            display as error "`_resolve_err'"
            exit 198
        }
    }
    // a df(#) VALUE takes precedence over a df row found by name
    if "`_df_const'" != "" local _ix_df 0

    // ---- ci(): (2 3) | 2 3 | ll ul | C (rows/cols 1-2 of another matrix) ----
    local _ci_matrix ""
    if regexm(trim("`ci'"), "^[re]\([A-Za-z_0-9]+\)$") {
        // ci(e(ci_normal)) / ci(r(name)): a results matrix -> copy it, rows 1-2 are lower/upper
        tempname CIM
        capture matrix `CIM' = `=trim("`ci'")'
        if _rc {
            display as error "ci(): `ci' is not a matrix in memory"
            exit 111
        }
        local _ci_matrix "`CIM'"
    }
    else if "`ci'" != "" {
        local _civ = trim(subinstr(subinstr("`ci'", "(", " ", .), ")", " ", .))
        local _civ = itrim("`_civ'")
        local _nciv : word count `_civ'
        if `_nciv' == 2 {
            local _k 0
            foreach _tok of local _civ {
                local ++_k
                if regexm("`_tok'", "^[0-9]+$") local _cix `_tok'
                else {
                    local _j 0
                    local _cix 0
                    foreach _n of local _statnames {
                        local ++_j
                        if lower("`_n'") == lower("`_tok'") local _cix `_j'
                    }
                    if `_cix' == 0 {
                        display as error "ci(): no row or column named `_tok' in `_mname' -- names are: `_statnames'"
                        exit 198
                    }
                }
                if `_cix' < 1 | `_cix' > `_nstat' {
                    display as error "ci(): position `_cix' is out of range (1-`_nstat')"
                    exit 198
                }
                if `_k' == 1 local _ix_ll `_cix'
                else         local _ix_ul `_cix'
            }
        }
        else if `_nciv' == 1 {
            capture confirm matrix `_civ'
            if _rc {
                display as error "ci(): expected two positions/names (lower upper) or a matrix name -- got `ci'"
                exit 198
            }
            local _ci_matrix "`_civ'"
        }
        else {
            display as error "ci(): expected two positions/names (lower upper) or a matrix name -- got `ci'"
            exit 198
        }
    }

    // ---- 4. critical values ----
    local _use_t 0
    local _df_val 0
    if "`_df_const'" != "" {
        local _use_t 1
        local _df_val `_df_const'
    }
    local _alpha  = (100 - `cilevel') / 200
    local _has_level2 0
    local _alpha2 0
    if "`level2'" != "" {
        local _has_level2 1
        local _alpha2 = (100 - `level2') / 200
    }

    // ---- 5. what do we have? ----
    local _have_se = (`_ix_se' > 0) | ("`_se_matrix'" != "")
    local _have_ci = (`_ix_ll' > 0 & `_ix_ul' > 0) | ("`_ci_matrix'" != "")
    local _have_p  = (`_ix_p' > 0)
    local _have_df = (`_ix_df' > 0) | ("`_df_const'" != "")
    if `_ix_ll' > 0 & `_ix_ul' == 0 | `_ix_ll' == 0 & `_ix_ul' > 0 {
        display as error "matrix(): found only one confidence limit by name -- give both with ci(lower upper)"
        exit 198
    }
    local _src_desc "estimates"
    if `_have_se' local _src_desc "`_src_desc', SE"
    if `_have_ci' local _src_desc "`_src_desc', CI"
    if `_have_p'  local _src_desc "`_src_desc', p"
    if `_have_df' local _src_desc "`_src_desc', df"
    local _dimword = cond(`_stat_rows', "rows", "columns")
    display as text "  Matrix mode: `_mname' (`_nr' x `_nc'; statistics in `_dimword'; `_src_desc')"
    if !`_have_se' & !`_have_ci' display as text "  No standard errors or confidence limits in the matrix -- point estimates only (use se() or ci() to add them)"

    // ---- 6. walk the coefficients ----
    local _out_names  ""
    local _out_coefs  ""
    local _out_lower  ""
    local _out_upper  ""
    local _out_lower2 ""
    local _out_upper2 ""
    local _out_ses    ""
    local _out_pvals  ""
    local _out_tzvals ""
    local _out_times  ""
    local _nkept 0
    local _nskip 0
    local _all_names ""
    forvalues j = 1/`_ncoef' {
        local _cname : word `j' of `_coefnames'
        // strip an equation prefix ("eq:name")
        if strpos("`_cname'", ":") > 0 local _cname = substr("`_cname'", strpos("`_cname'", ":") + 1, .)
        local _all_names "`_all_names' `_cname'"
        // -- values --
        if `_stat_rows' {
            local _coef = `M'[`_ix_b', `j']
            if `_ix_se' > 0 local _se = `M'[`_ix_se', `j']
            if `_ix_ll' > 0 local _lower = `M'[`_ix_ll', `j']
            if `_ix_ul' > 0 local _upper = `M'[`_ix_ul', `j']
            if `_ix_p'  > 0 local _pval  = `M'[`_ix_p', `j']
            if `_ix_df' > 0 local _dfj   = `M'[`_ix_df', `j']
        }
        else {
            local _coef = `M'[`j', `_ix_b']
            if `_ix_se' > 0 local _se = `M'[`j', `_ix_se']
            if `_ix_ll' > 0 local _lower = `M'[`j', `_ix_ll']
            if `_ix_ul' > 0 local _upper = `M'[`j', `_ix_ul']
            if `_ix_p'  > 0 local _pval  = `M'[`j', `_ix_p']
            if `_ix_df' > 0 local _dfj   = `M'[`j', `_ix_df']
        }
        if "`_se_matrix'" != "" {
            if rowsof(`_se_matrix') == 1 local _se = `_se_matrix'[1, `j']
            else                          local _se = `_se_matrix'[`j', 1]
        }
        if "`_ci_matrix'" != "" {
            if rowsof(`_ci_matrix') >= 2 {
                local _lower = `_ci_matrix'[1, `j']
                local _upper = `_ci_matrix'[2, `j']
            }
            else {
                local _lower = `_ci_matrix'[`j', 1]
                local _upper = `_ci_matrix'[`j', 2]
            }
        }
        if `_coef' >= . {
            local ++_nskip
            continue
        }
        // -- per-coefficient df (t) --
        local _use_t_j `_use_t'
        local _df_j `_df_val'
        if `_ix_df' > 0 {
            if `_dfj' < . & `_dfj' > 0 {
                local _use_t_j 1
                local _df_j `_dfj'
            }
        }
        if `_use_t_j' {
            local _crit  = invttail(`_df_j', `_alpha')
            if `_has_level2' local _crit2 = invttail(`_df_j', `_alpha2')
        }
        else {
            local _crit  = invnormal(1 - `_alpha')
            if `_has_level2' local _crit2 = invnormal(1 - `_alpha2')
        }
        // -- derive what is missing --
        local _has_ci_j 0
        if `_have_ci' {
            if `_lower' < . & `_upper' < . local _has_ci_j 1
        }
        if `_have_se' {
            if `_se' < . & `_se' > 0 {
                if !`_has_ci_j' {
                    local _lower = `_coef' - `_crit' * `_se'
                    local _upper = `_coef' + `_crit' * `_se'
                    local _has_ci_j 1
                }
            }
            else local _se .
        }
        else if `_has_ci_j' {
            // back-solve the SE from the interval (for the table, t/z and p)
            local _se = (`_upper' - `_lower') / (2 * `_crit')
        }
        else local _se .
        if (`_have_se' | `_have_ci') & !`_has_ci_j' {
            local ++_nskip
            display as text "  note: `_cname' skipped -- no usable standard error or confidence limits"
            continue
        }
        // t/z and p
        local _tzval .
        if `_se' < . & `_se' > 0 local _tzval = `_coef' / `_se'
        if !`_have_p' {
            local _pval .
            if `_tzval' < . {
                if `_use_t_j' local _pval = 2 * ttail(`_df_j', abs(`_tzval'))
                else          local _pval = 2 * normal(-abs(`_tzval'))
            }
        }
        // inner level: needs an SE (a CI alone cannot give a second level)
        if `_has_level2' {
            if `_se' < . & `_se' > 0 {
                local _lower2 = `_coef' - `_crit2' * `_se'
                local _upper2 = `_coef' + `_crit2' * `_se'
            }
            else {
                local _lower2 `_lower'
                local _upper2 `_upper'
            }
        }
        if !`_has_ci_j' {
            local _lower `_coef'
            local _upper `_coef'
        }
        // -- omit() / show() (glob) --
        if "`omit'" != "" {
            local _is_omitted 0
            foreach _oname of local omit {
                if strpos("`_oname'", "*") > 0 | strpos("`_oname'", "?") > 0 {
                    local _opat = subinstr("`_oname'", ".", "\.", .)
                    local _opat = subinstr("`_opat'", "*", ".*", .)
                    local _opat = subinstr("`_opat'", "?", ".", .)
                    if regexm("`_cname'", "^`_opat'$") local _is_omitted 1
                }
                else if "`_cname'" == "`_oname'" local _is_omitted 1
            }
            if `_is_omitted' continue
        }
        if "`show'" != "" {
            local _is_shown 0
            foreach _sname of local show {
                if strpos("`_sname'", "*") > 0 | strpos("`_sname'", "?") > 0 {
                    local _spat = subinstr("`_sname'", ".", "\.", .)
                    local _spat = subinstr("`_spat'", "*", ".*", .)
                    local _spat = subinstr("`_spat'", "?", ".", .)
                    if regexm("`_cname'", "^`_spat'$") local _is_shown 1
                }
                else if "`_cname'" == "`_sname'" local _is_shown 1
            }
            if !`_is_shown' continue
        }
        // -- display name --
        local _dname "`_cname'"
        if "`_cname'" == "_cons" local _dname "Constant"
        if "`coeflbl'" != "" {
            capture local _vl : variable label `_cname'
            if _rc == 0 & "`_vl'" != "" local _dname "`_vl'"
        }
        local _dname = subinstr("`_dname'", "|", "/", .)
        local _dname = subinstr("`_dname'", "~", "-", .)
        // -- event time (eventstudy): parse the name to an integer; summaries get 9999+ --
        local _tm .
        if "`eventstudy'" != "" {
            local _lc = lower("`_cname'")
            if      regexm("`_lc'", "^t$")                       local _tm 0
            else if regexm("`_lc'", "^t[-]([0-9]+)$")            local _tm = -real(regexs(1))
            else if regexm("`_lc'", "^t\+([0-9]+)$")             local _tm =  real(regexs(1))
            else if regexm("`_lc'", "^t?m([0-9]+)$")             local _tm = -real(regexs(1))
            else if regexm("`_lc'", "^t?p([0-9]+)$")             local _tm =  real(regexs(1))
            else if regexm("`_lc'", "^lead_?([0-9]+)$")          local _tm = -real(regexs(1))
            else if regexm("`_lc'", "^lag_?([0-9]+)$")           local _tm =  real(regexs(1))
            else if regexm("`_lc'", "^(-?[0-9]+)b?n?o?\.")       local _tm =  real(regexs(1))
            else if regexm("`_lc'", "^(pre|post)_?avg")          local _tm = cond(regexs(1) == "pre", 9998, 9999)
            else if regexm("`_lc'", "^(-?[0-9]+)$")              local _tm =  real(regexs(1))
        }
        // t2c: summary rows (Pre_avg / Post_avg) are not periods -- keep them off the time
        // axis unless show() asks for them (csdid_plot convention); they stay in r(table)
        if "`eventstudy'" != "" & `_tm' >= 9998 & `_tm' < . & "`show'" == "" {
            local _summ_skipped "`_summ_skipped' `_cname'"
            continue
        }
        // -- format --
        local _coef_fmt : display %12.7g `_coef'
        local _coef_fmt = itrim("`_coef_fmt'")
        local _lower_fmt : display %12.7g `_lower'
        local _lower_fmt = itrim("`_lower_fmt'")
        local _upper_fmt : display %12.7g `_upper'
        local _upper_fmt = itrim("`_upper_fmt'")
        local _se_fmt ""
        if `_se' < . {
            local _se_fmt : display %12.7g `_se'
            local _se_fmt = itrim("`_se_fmt'")
        }
        local _pval_fmt ""
        if `_pval' < . {
            local _pval_fmt : display %12.7g `_pval'
            local _pval_fmt = itrim("`_pval_fmt'")
        }
        local _tz_fmt ""
        if `_tzval' < . {
            local _tz_fmt : display %12.7g `_tzval'
            local _tz_fmt = itrim("`_tz_fmt'")
        }
        if `_has_level2' {
            local _lower2_fmt : display %12.7g `_lower2'
            local _lower2_fmt = itrim("`_lower2_fmt'")
            local _upper2_fmt : display %12.7g `_upper2'
            local _upper2_fmt = itrim("`_upper2_fmt'")
        }
        if `_nkept' == 0 {
            local _out_names  "`_dname'"
            local _out_coefs  "`_coef_fmt'"
            local _out_lower  "`_lower_fmt'"
            local _out_upper  "`_upper_fmt'"
            local _out_ses    "`_se_fmt'"
            local _out_pvals  "`_pval_fmt'"
            local _out_tzvals "`_tz_fmt'"
            local _out_times  "`_tm'"
            if `_has_level2' {
                local _out_lower2 "`_lower2_fmt'"
                local _out_upper2 "`_upper2_fmt'"
            }
        }
        else {
            local _out_names  "`_out_names'|`_dname'"
            local _out_coefs  "`_out_coefs'|`_coef_fmt'"
            local _out_lower  "`_out_lower'|`_lower_fmt'"
            local _out_upper  "`_out_upper'|`_upper_fmt'"
            local _out_ses    "`_out_ses'|`_se_fmt'"
            local _out_pvals  "`_out_pvals'|`_pval_fmt'"
            local _out_tzvals "`_out_tzvals'|`_tz_fmt'"
            local _out_times  "`_out_times'|`_tm'"
            if `_has_level2' {
                local _out_lower2 "`_out_lower2'|`_lower2_fmt'"
                local _out_upper2 "`_out_upper2'|`_upper2_fmt'"
            }
        }
        local ++_nkept
    }
    if `_nskip' > 0 display as text "  `_nskip' coefficient(s) skipped (missing estimate or interval)"
    if "`_summ_skipped'" != "" display as text "  summary rows left off the time axis:`_summ_skipped' (use show(*) to include them)"
    if `_nkept' == 0 {
        display as error "matrix(): no coefficients left to plot from `_mname'"
        display as error "  Names found: `_all_names'"
        display as error "  Check show()/omit(), or the positions given in ci()/se()."
        exit 198
    }

    // ---- 7. eventstudy: order by parsed time (summaries last); unparsed names keep their order ----
    if "`eventstudy'" != "" {
        sparkta_matrix_sort_time "`_out_times'" "`_out_names'" "`_out_coefs'" "`_out_lower'" "`_out_upper'" "`_out_ses'" "`_out_pvals'" "`_out_tzvals'" "`_out_lower2'" "`_out_upper2'"
        local _out_names  "`s(names)'"
        local _out_coefs  "`s(coefs)'"
        local _out_lower  "`s(lower)'"
        local _out_upper  "`s(upper)'"
        local _out_ses    "`s(ses)'"
        local _out_pvals  "`s(pvals)'"
        local _out_tzvals "`s(tzvals)'"
        local _out_lower2 "`s(lower2)'"
        local _out_upper2 "`s(upper2)'"
        local _out_times  "`s(times)'"
        sreturn clear
    }

    // ---- 8. contract ----
    c_local _pe_names    "`_out_names'"
    c_local _pe_coefs    "`_out_coefs'"
    c_local _pe_lower    "`_out_lower'"
    c_local _pe_upper    "`_out_upper'"
    c_local _pe_lower2   "`_out_lower2'"
    c_local _pe_upper2   "`_out_upper2'"
    c_local _pe_ses      "`_out_ses'"
    c_local _pe_pvals    "`_out_pvals'"
    c_local _pe_tzvals   "`_out_tzvals'"
    c_local _pe_nobs     ""
    c_local _pe_depvar   ""
    c_local _pe_matname  "`_mname'"
    c_local _pe_bases    ""
    c_local _pe_tstat    "`_use_t'"
    c_local _pe_cmd      "matrix `_mname'"
    c_local _pe_fitstats ""
    c_local _pe_xpos     "`_out_times'"
    c_local _pe_matrix_hasci  = (`_have_se' | `_have_ci')
end
