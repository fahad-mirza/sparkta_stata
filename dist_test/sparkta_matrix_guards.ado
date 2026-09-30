*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: matrix()/results() guards and the multi-matrix layout -- internal; not a user command
*! t2g: new -- moved out of sparkta.ado for main-program headroom; logic unchanged (values in, c_local back)

// =============================================================================
// sparkta_matrix_guards  (v3.6.0-t2g)
//   sparkta_matrix_guards, type() matrix() plotlabels() estlabels() estnames() ci() se() df() pvalue() results()
// Returns via c_local: matrix estlabels estnames _mat_list _mat_multi
// results(): the source names of a results() call -- with several sources the
// model labels are the source names, not _sparkta_res1..
// =============================================================================
program sparkta_matrix_guards
    version 17
    syntax , [TYpe(string) MATrix(string asis) PLOTLabels(string asis) ESTLabels(string asis) ESTNames(string asis) ///
        CI(string) SE(string) DF(string) Pvalue(string) RESults(string asis)]
    local matrix = trim(itrim(`"`matrix'"'))
    if `"`plotlabels'"' != "" {
        if `"`estlabels'"' != "" {
            display as error "plotlabels() and estlabels() are the same option -- specify one"
            exit 198
        }
        local estlabels `"`plotlabels'"'
    }
    if "`matrix'" != "" {
        if !inlist("`type'", "coefplot", "eventstudy") {
            display as error "matrix() is only valid with type(coefplot) or type(eventstudy)"
            if "`type'" == "marginsplot" display as error "  (marginsplot needs the margins structure and r(at); a matrix does not carry them -- use type(coefplot) matrix(r(table)))"
            exit 198
        }
        if "`estnames'" != "" {
            display as error "matrix() and estnames() cannot be combined -- give either stored models or matrices"
            exit 198
        }
    }
    else {
        foreach _mopt in ci se df pvalue {
            if "``_mopt''" != "" {
                display as error "`_mopt'() is only valid together with matrix()"
                exit 198
            }
        }
    }
    // v3.6.0-t2a: several matrices -> the multi-model layout; estnames carries the matrix names
    local _mat_list ""
    local _mat_multi 0
    if "`matrix'" != "" {
        local _mat_list "`matrix'"
        local _mat_n : word count `_mat_list'
        if `_mat_n' > 1 {
            local _mat_multi 1
            local estnames ""
            foreach _ms of local _mat_list {
                local _mn "`_ms'"
                if regexm("`_mn'", "^(.+)\[") local _mn = regexs(1)
                local _mn = subinstr(subinstr(subinstr("`_mn'", "(", "_", .), ")", "", .), " ", "", .)
                local estnames "`estnames' `_mn'"
            }
            local estnames = trim("`estnames'")
        }
    }
    // results() with several sources: label the models by source, not by _sparkta_res#
    if `_mat_multi' & `"`results'"' != "" local estnames `"`results'"'
    c_local matrix `"`matrix'"'
    c_local estlabels `"`estlabels'"'
    c_local estnames `"`estnames'"'
    c_local _mat_list "`_mat_list'"
    c_local _mat_multi `_mat_multi'
end
