*! test2
program define test2
    version 17
    syntax , [TYPE(string)]
    local type = lower("`type'")
    local is_stack "0"
    if "`type'" == "stackedarea" {
        local type "area"
        local is_stack "1"
    }
    display "type=`type' is_stack=`is_stack'"
end
