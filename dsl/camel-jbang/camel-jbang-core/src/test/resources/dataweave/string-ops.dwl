%dw 2.0
output application/json
---
{
    upper: upper(payload.name),
    lower: lower(payload.name),
    hasEmail: payload.email contains "@",
    parts: payload.tags splitBy ",",
    joined: payload.items joinBy "; ",
    fixed: payload.text replace "old" with "new"
}
