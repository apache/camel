%dw 2.0
output application/json
---
{
    amount: payload.amount as Number,
    label: payload.amount as String
}
