%dw 2.0
output application/json
---
{
    name: payload.name,
    qty: payload.qty,
    total: payload.qty * payload.price
}
