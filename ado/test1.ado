*! test1
program define test1
    version 17
    syntax , [TYPE(string)]
    local type = lower("`type'")
    if "`type'" == "stackedarea" {
        local type "area"
        local is_stack "1"
    }
    display "type=`type' is_stack=`is_stack'"
end
