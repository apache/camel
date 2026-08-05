%dw 2.0
output application/json
---
{
    count: payload.count as Number,
    total: payload.total as Number,
    active: payload.active as Boolean,
    label: payload.id as String,
    created: payload.timestamp as String {format: "yyyy-MM-dd"}
}
