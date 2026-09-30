*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: table-row helper for the export table -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split) -- logic unchanged

// =========================================================================
// SUBROUTINE: sparkta_parse_tblrows (v3.6.0-s6c15)
// Parses indicators() and addstats() option content from the raw pre-syntax
// argument string. Extracts each quoted entry, splits on / for label/values.
// Args: 1 = full raw argument string (backtick-quote protected)
// Returns via r(): _pe_indicators and _pe_addstats in tilde|pipe format.
// =========================================================================
program sparkta_parse_tblrows, rclass
    // v3.6.0-s6c18: extended to support auto-stat keywords in addstats()
    // Keywords (no quotes, no /): rsq arsq fstat fpval ll chi2 rmse dep_mean
    // Manual rows (quoted, with /): "Label/v1 v2 v3"
    // Mixed: addstats(rsq dep_mean "Sample/Full Full Restricted")
    version 17
    local rawstr `"`1'"'

    local ind_result ""
    local ads_result ""

    // Valid auto-stat keywords for addstats() -- error on unrecognised
    local valid_kws "rsq arsq fstat fpval ll chi2 rmse dep_mean"

    // Process each option block: indicators and addstats
    foreach optname in indicators addstats {
        // Find "optname(" in the raw string
        local optstart = strpos(`"`rawstr'"', "`optname'(")
        if `optstart' == 0 continue
        // Move past "optname("
        local contentstart = `optstart' + length("`optname'") + 1
        local content = substr(`"`rawstr'"', `contentstart', .)
        // Walk character by character to extract entries
        local result ""
        local pos 1
        local slen = length(`"`content'"')
        local going 1
        while `going' & `pos' <= `slen' {
            local ch = substr(`"`content'"', `pos', 1)
            // Stop at closing ) of the option
            if `"`ch'"' == ")" {
                local going 0
                continue
            }
            // Skip spaces between entries
            if `"`ch'"' == " " {
                local pos = `pos' + 1
                continue
            }
            // Found an opening quote -- extract until closing quote (manual row)
            if `"`ch'"' == char(34) {
                local pos = `pos' + 1
                local entry ""
                local inner 1
                while `inner' & `pos' <= `slen' {
                    local c2 = substr(`"`content'"', `pos', 1)
                    if `"`c2'"' == char(34) {
                        local inner 0
                    }
                    else {
                        local entry "`entry'`c2'"
                    }
                    local pos = `pos' + 1
                }
                // Validate manual entry: must have / separator
                if strpos("`entry'", "/") == 0 {
                    display as error "`optname'(): missing / separator in: `entry'"
                    display as error "  Format: `optname'(\"Label/Val1 Val2\" ...)"
                    exit 198
                }
                if strpos("`entry'", "~") > 0 {
                    display as error "`optname'(): values cannot contain ~"
                    exit 198
                }
                // Split on first / and convert spaces in values to |
                local sep = strpos("`entry'", "/")
                local lbl = strtrim(substr("`entry'", 1, `sep' - 1))
                local val = strtrim(substr("`entry'", `sep' + 1, .))
                local valp = subinstr("`val'", " ", "|", .)
                local row "`lbl'|`valp'"
                if "`result'" == "" local result "`row'"
                else local result "`result'~`row'"
            }
            else {
                // Not a quote, not a space, not ) -- must be a keyword token
                // Accumulate characters until space, ) or end
                if "`optname'" == "addstats" {
                    local kw ""
                    local kw_going 1
                    while `kw_going' & `pos' <= `slen' {
                        local kc = substr(`"`content'"', `pos', 1)
                        if `"`kc'"' == " " | `"`kc'"' == ")" | `"`kc'"' == char(34) {
                            local kw_going 0
                        }
                        else {
                            local kw "`kw'`kc'"
                            local pos = `pos' + 1
                        }
                    }
                    // Validate keyword
                    if "`kw'" != "" {
                        local kw_valid 0
                        foreach vk of local valid_kws {
                            if "`kw'" == "`vk'" local kw_valid 1
                        }
                        if !`kw_valid' {
                            display as error "addstats(): unrecognised keyword: `kw'"
                            display as error "  Valid keywords: `valid_kws'"
                            display as error "  For custom rows use quoted syntax: addstats(\"Label/v1 v2\")"
                            exit 198
                        }
                        // Emit as __kw__ row -- Java resolves values from peModelStats
                        local row "__kw__`kw'"
                        if "`result'" == "" local result "`row'"
                        else local result "`result'~`row'"
                    }
                }
                else {
                    // indicators() does not support keywords -- skip non-quote chars
                    local pos = `pos' + 1
                }
            }
        }
        if "`optname'" == "indicators" local ind_result "`result'"
        if "`optname'" == "addstats"   local ads_result "`result'"
    }

    return local _pe_indicators "`ind_result'"
    return local _pe_addstats   "`ads_result'"
end
