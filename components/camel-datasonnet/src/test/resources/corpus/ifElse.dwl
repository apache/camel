%dw 2.0
output application/json
---
{
    label: if (payload.score >= 90) "A" else if (payload.score >= 80) "B" else "C"
}
