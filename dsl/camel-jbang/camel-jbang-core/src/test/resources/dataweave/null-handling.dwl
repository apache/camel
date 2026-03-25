%dw 2.0
output application/json
---
{
    name: payload.name default "Unknown",
    city: payload.address.city default "N/A",
    country: payload.address.country default "US",
    active: payload.status default "active"
}
