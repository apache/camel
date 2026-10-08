%dw 2.0
output application/json
---
payload.items map ((item) -> {
    name: item.name,
    price: item.price
})
