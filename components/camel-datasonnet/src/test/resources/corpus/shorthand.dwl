%dw 2.0
output application/json
---
{
    names: payload.items map upper($.name),
    lines: payload.items map { line: $$, sku: $.@sku },
    byPriceDesc: (payload.items orderBy -$.price) map $.name,
    discounted: (payload.items filter $.discount?) map $.name,
    skus: (payload.items distinctBy $.@sku) map $.@sku,
    totalQty: (payload.items map $.qty) reduce ($$ + $)
}
