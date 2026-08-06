%dw 2.0
output application/json
---
{
    orderId: payload.order_id,
    customerEmail: payload.customer.email,
    customerName: payload.customer.first_name ++ " " ++ payload.customer.last_name,
    currency: payload.currency default "USD",
    orderDate: now() as String {format: "yyyy-MM-dd'T'HH:mm:ss'Z'"},
    status: "RECEIVED"
}
