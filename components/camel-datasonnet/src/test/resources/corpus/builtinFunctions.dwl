%dw 2.0
output application/json
---
{
    upper: upper(payload.text),
    lower: lower(payload.text),
    len: sizeOf(payload.text)
}
