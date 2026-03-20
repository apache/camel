%dw 2.0
output application/java
---
{
    items: payload.line_items map ((item) -> {
        sku: item.product_sku,
        name: item.product_name,
        quantity: item.qty as Number,
        unitPrice: item.unit_price as Number,
        lineTotal: (item.qty as Number) * (item.unit_price as Number)
    }),
    totalAmount: payload.line_items reduce ((item, acc = 0) ->
        acc + ((item.qty as Number) * (item.unit_price as Number))
    )
}
