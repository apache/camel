%dw 2.0
output application/json
---
{
    total: payload.amounts reduce ((item, acc = 0) -> acc + item)
}
