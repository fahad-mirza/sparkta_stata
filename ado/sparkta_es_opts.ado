*! sparkta version 3.6.0 2026-09-13
*! component of sparkta: validates refperiod()/together for eventstudy -- internal, installed with sparkta; not a user command
*! split out of sparkta.ado in v3.6.0-t2f (Stage 1 of the file split)
*! t2f fix 2: `separate` keyword; default together for estimation mode, separate for matrix() (DiD estimators)

// =============================================================================
// sparkta_es_opts  (v3.6.0-t2f)
//   sparkta_es_opts, type(t) [refperiod(auto|none|#)] [together|separate] [matrix(...)]
// Validates the event-study reference-period options and returns arg 208 via
// c_local _es_opts = "ref=<auto|none|#>;together=<0|1>". The options are only
// meaningful for type(eventstudy); given elsewhere they are an error.
// DEFAULT (t2f fix 2): estimation mode (regress/reghdfe -- every lead and lag is
// relative to the omitted period) draws the post series joined to the reference
// (together); matrix mode from a DiD estimator (csdid/jwdid/lwdid: pre-period
// placebos are not relative to -1) keeps leads and lags separate (Borusyak-
// Jaravel-Spiess 2021 / event_plot default). `together` / `separate` override.
// =============================================================================
program sparkta_es_opts
    version 17
    syntax , [TYpe(string) REFPeriod(string) TOGether SEParate MATrix(string)]
    if "`together'" != "" & "`separate'" != "" {
        display as error "together and separate cannot both be specified"
        exit 198
    }
    local _ref = lower(trim("`refperiod'"))
    if "`_ref'" == "" local _ref "auto"
    if "`_ref'" != "auto" & "`_ref'" != "none" {
        capture confirm integer number `_ref'
        if _rc != 0 {
            display as error "refperiod(): expected auto, none or an integer period -- got '`refperiod''"
            exit 198
        }
    }
    if ("`refperiod'" != "" | "`together'" != "" | "`separate'" != "") & "`type'" != "eventstudy" {
        display as error "refperiod(), together and separate are only valid with type(eventstudy)"
        exit 198
    }
    // default by source: matrix() (DiD estimator output) -> separate; estimation results -> together
    local _tg = cond(`"`matrix'"' == "", "1", "0")
    if "`together'" != "" local _tg 1
    if "`separate'" != "" local _tg 0
    c_local _es_opts "ref=`_ref';together=`_tg'"
end
