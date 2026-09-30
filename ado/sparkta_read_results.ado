*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: results() -- dataset or frame of estimates -> matrix for matrix mode -- internal; not a user command
*! t2j fix8y: keyword autodetect for coefplot/eventstudy columns is exact-name too (no abbreviation matches)
*! t2j fix8x: marginsplot suboptions b() se() ci() pvalue() at() factors() forwarded as a column map; frame
*!   sources are read from a COPY of the frame (the map adds columns); keyword lists shared with the reader
*! t2g fix 5: the label map (a global -- "invalid syntax" in run 53) is gone; names come from the file characteristics
*! t2g: marginsplot -> sparkta_read_results_margins (margins, saving() layout)
*! t2f fix 5: spec() option, numeric-only autodetect for statistics
*! t2f fix 4: new. results(source [, name() time() b() ci() se() pvalue()]); sources = .dta files or frame:name

// =============================================================================
// sparkta_read_results  (v3.6.0-t2f fix 4)
//   sparkta_read_results, spec(<results() content>) type(coefplot|eventstudy)
// Turns a DATASET of estimates -- one row per coefficient / event period -- into
// matrices named _sparkta_res1.. and returns them via
//   c_local matrix "_sparkta_res1 [_sparkta_res2 ...]"   c_local _results_src "<sources>"
// so the unchanged matrix() pipeline (sparkta_read_matrix, autodetect, ci()/se()
// grammar, reference-period rules) takes over. Nothing statistical happens here.
//
// Grammar:  results(source [source ...] [, name(var) time(var) b(var) ci(lo hi)
//                   se(var) pvalue(var)])
//   source   a .dta file (extension optional) or frame:<name>
//   name()   coefplot: the column holding the coefficient names
//              autodetect: parm term var varname variable name coef coefname label
//   time()   eventstudy: the column holding the relative time (integer, may be negative)
//              autodetect: ryear rel_time reltime event_time eventtime period time t rel
//   b()/ci()/se()/pvalue()  statistic columns; autodetect uses the same keyword
//              lists as matrix(): b coef estimate ... watt | se stderr ... |
//              ll lb lower lci min95 low_ci ... | ul ub upper uci max95 up_ci ... |
//              pvalue p pval p_value
// Rows with a missing name/time are dropped (lwdid's Pre_avg/Post_avg rows, blank
// lines). Row names: coefplot = the name value (spaces -> _); eventstudy = Tm#/Tp#
// so the event-time parser and the reference-period rules apply unchanged.
// Every source becomes one matrix; several sources = the multi-model layout.
// =============================================================================
program sparkta_read_results
    version 17
    // called as: sparkta_read_results, spec(<results() content>) type(t)
    // fix 5: the content is split on its FIRST comma here (the old gettoken split ran on a
    // compound-quoted argument and swallowed the suboptions)
    syntax , SPec(string asis) TYpe(string)
    local _p = strpos(`"`spec'"', ",")
    if `_p' > 0 {
        local _spec = trim(substr(`"`spec'"', 1, `_p' - 1))
        local _sub  = substr(`"`spec'"', `_p' + 1, .)
    }
    else {
        local _spec = trim(`"`spec'"')
        local _sub ""
    }
    local 0 `", `_sub'"'
    syntax , [NAme(string) TIme(string) B(string) CI(string) SE(string) Pvalue(string) ATNames(namelist) FACtors(string) AT(string)]
    if `"`_spec'"' == "" {
        display as error "results(): no source given -- results(file.dta) or results(frame:name)"
        exit 198
    }
    if !inlist("`type'", "coefplot", "eventstudy", "marginsplot") {
        display as error "results() is only valid with type(coefplot), type(eventstudy) or type(marginsplot)"
        exit 198
    }
    local _kw_b   "b coef coefficient estimate estimates beta est theta att effect watt _margin margin"
    local _kw_se  "se stderr std_err sd stde _se_margin se_margin"
    local _kw_ll  "ll lb lower lci ci_l cil low min95 lower95 low_ci low_band _ci_lb ci_lb"
    local _kw_ul  "ul ub upper uci ci_u ciu high max95 upper95 up_ci up_band _ci_ub ci_ub"
    local _kw_p   "pvalue p pval p_value pv _pvalue"
    // t2g: marginsplot -- one margins, saving() source -> kept margins results (c_local _mg_*)
    // t2j fix8x: any tidy table too -- b() se() ci() pvalue() at() factors() map its columns
    if "`type'" == "marginsplot" {
        local _mpmap `"b(`b') se(`se') ci(`ci') pvalue(`pvalue') at(`at') factors(`factors') atnames(`atnames') kwb(`_kw_b') kwse(`_kw_se') kwll(`_kw_ll') kwul(`_kw_ul') kwp(`_kw_p')"'
        local _nsrc : word count `_spec'
        if `_nsrc' != 1 {
            display as error "results() for marginsplot takes one margins, saving() source"
            exit 198
        }
        local _src : word 1 of `_spec'
        if substr("`_src'", 1, 6) == "frame:" {
            local _fr = substr("`_src'", 7, .)
            capture confirm frame `_fr'
            if _rc != 0 {
                display as error "results(): frame `_fr' not found"
                exit 111
            }
            // fix8x: work on a copy -- the column map adds variables and must not touch the user's frame
            tempname _fcopy
            frame copy `_fr' `_fcopy'
            capture noisily frame `_fcopy': sparkta_read_results_margins, `_mpmap' src(`_src')
            local _rc = _rc
            frame drop `_fcopy'
            if `_rc' != 0 exit `_rc'
        }
        else {
            local _fn "`_src'"
            capture confirm file "`_fn'"
            if _rc != 0 {
                local _fn "`_src'.dta"
                capture confirm file "`_fn'"
            }
            if _rc != 0 {
                display as error "results(): file `_src' not found (tried `_src' and `_src'.dta)"
                exit 601
            }
            preserve
            quietly use "`_fn'", clear
            sparkta_read_results_margins, `_mpmap' src(`_src')
            restore
        }
        c_local _mg_have 1
        c_local _mg_hasvs 0
        c_local _mg_hasat `_mg_hasat'
        c_local _mg_plab ""
        c_local _mg_expr ""
        c_local _mg_cmdl "margins (results())"
        c_local _results_src "`_src'"
        exit
    }
    if "`ci'" != "" {
        local _nci : word count `ci'
        if `_nci' != 2 {
            display as error "results(..., ci()) takes two variables: ci(lower upper)"
            exit 198
        }
    }
    local _kw_nm  "parm term var varname variable name coef coefname label"
    local _kw_tm  "ryear rel_time reltime event_time eventtime period time t rel"
    local _mats ""
    local _srcs ""
    local _i 0
    foreach _src of local _spec {
        local ++_i
        local _mname "_sparkta_res`_i'"
        capture matrix drop `_mname'
        local _isframe = (substr("`_src'", 1, 6) == "frame:")
        if `_isframe' {
            local _fr = substr("`_src'", 7, .)
            capture confirm frame `_fr'
            if _rc != 0 {
                display as error "results(): frame `_fr' not found"
                exit 111
            }
            frame `_fr': sparkta_read_results_build, out(`_mname') type(`type') name(`name') time(`time') b(`b') ci(`ci') se(`se') pvalue(`pvalue') ///
                kwb(`_kw_b') kwse(`_kw_se') kwll(`_kw_ll') kwul(`_kw_ul') kwp(`_kw_p') kwnm(`_kw_nm') kwtm(`_kw_tm') src(`_src')
        }
        else {
            // as typed first (tempfile names end in .tmp and save() keeps that), then with .dta
            local _fn "`_src'"
            capture confirm file "`_fn'"
            if _rc != 0 {
                local _fn "`_src'.dta"
                capture confirm file "`_fn'"
            }
            if _rc != 0 {
                display as error "results(): file `_src' not found (tried `_src' and `_src'.dta)"
                exit 601
            }
            preserve
            quietly use "`_fn'", clear
            sparkta_read_results_build, out(`_mname') type(`type') name(`name') time(`time') b(`b') ci(`ci') se(`se') pvalue(`pvalue') ///
                kwb(`_kw_b') kwse(`_kw_se') kwll(`_kw_ll') kwul(`_kw_ul') kwp(`_kw_p') kwnm(`_kw_nm') kwtm(`_kw_tm') src(`_src')
            restore
        }
        local _mats "`_mats' `_mname'"
        local _srcs "`_srcs' `_src'"
    }
    c_local matrix `"`=trim("`_mats'")'"'
    c_local _results_src `"`=trim("`_srcs'")'"'
end

// builds ONE matrix from the data in memory (or the current frame)
program sparkta_read_results_build
    version 17
    syntax , OUT(string) TYpe(string) [NAme(string) TIme(string) B(string) CI(string) SE(string) Pvalue(string) ///
        KWB(string) KWSE(string) KWLL(string) KWUL(string) KWP(string) KWNM(string) KWTM(string) SRC(string)]
    // pick a column by explicit option, else by keyword list (case-insensitive, first hit wins)
    foreach _role in b se ll ul p nm tm {
        local _pick ""
        if "`_role'" == "b"  local _pick "`b'"
        if "`_role'" == "se" local _pick "`se'"
        if "`_role'" == "ll" local _pick : word 1 of `ci'
        if "`_role'" == "ul" local _pick : word 2 of `ci'
        if "`_role'" == "p"  local _pick "`pvalue'"
        if "`_role'" == "nm" local _pick "`name'"
        if "`_role'" == "tm" local _pick "`time'"
        if "`_pick'" != "" {
            capture confirm variable `_pick'
            if _rc != 0 {
                display as error "results(): variable `_pick' not found in `src'"
                exit 111
            }
        }
        else {
            foreach _kw of local kw`_role' {
                // statistic roles need a NUMERIC column (lwdid's file has a string column named effect)
                // fix8y: exact -- confirm variable accepts abbreviations ("b" would take "beta_hat")
                if inlist("`_role'", "nm", "tm") capture confirm variable `_kw', exact
                else capture confirm numeric variable `_kw', exact
                if _rc == 0 {
                    local _pick "`_kw'"
                    continue, break
                }
            }
        }
        local _v_`_role' "`_pick'"
    }
    if "`_v_b'" == "" {
        display as error "results(): no estimate column found in `src' -- give b(varname)"
        exit 198
    }
    if "`type'" == "eventstudy" & "`_v_tm'" == "" {
        display as error "results(): no relative-time column found in `src' -- give time(varname)"
        exit 198
    }
    if "`type'" == "coefplot" & "`_v_nm'" == "" & "`_v_tm'" == "" {
        display as error "results(): no name column found in `src' -- give name(varname)"
        exit 198
    }
    tempvar _rn _keep
    if "`type'" == "eventstudy" {
        capture confirm numeric variable `_v_tm'
        if _rc != 0 {
            display as error "results(): time(`_v_tm') must be numeric"
            exit 198
        }
        quietly gen byte `_keep' = `_v_tm' < .
        quietly gen str32 `_rn' = cond(`_v_tm' < 0, "Tm" + string(abs(`_v_tm')), "Tp" + string(`_v_tm')) if `_keep'
    }
    else {
        local _nmv = cond("`_v_nm'" != "", "`_v_nm'", "`_v_tm'")
        capture confirm string variable `_nmv'
        if _rc == 0 {
            quietly gen byte `_keep' = trim(`_nmv') != ""
            quietly gen str32 `_rn' = subinstr(trim(`_nmv'), " ", "_", .) if `_keep'
        }
        else {
            quietly gen byte `_keep' = `_nmv' < .
            quietly gen str32 `_rn' = string(`_nmv') if `_keep'
        }
    }
    quietly count if `_keep'
    local _nrows = r(N)
    if `_nrows' == 0 {
        display as error "results(): `src' has no usable rows"
        exit 2000
    }
    local _cols "`_v_b'"
    local _cn   "b"
    foreach _role in se ll ul p {
        if "`_v_`_role''" != "" {
            local _cols "`_cols' `_v_`_role''"
            local _cn   "`_cn' `=cond("`_role'" == "p", "pvalue", "`_role'")'"
        }
    }
    foreach _c of local _cols {
        capture confirm numeric variable `_c'
        if _rc != 0 {
            display as error "results(): statistic column `_c' in `src' is not numeric"
            exit 198
        }
    }
    quietly mkmat `_cols' if `_keep', matrix(`out') rownames(`_rn')
    matrix colnames `out' = `_cn'
    display as text "  results(): `src' -> `_nrows' rows, columns `_cn'"
end
