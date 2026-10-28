%dw 2.0
output application/json
---
payload.items filter ((item) -> item.qty > 1)
