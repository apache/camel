%dw 2.0
output application/json
---
{
    eventType: "ORDER_CREATED",
    eventId: uuid(),
    timestamp: now() as String {format: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"},
    correlationId: vars.correlationId,
    data: {
        orderId: vars.parsedOrder.orderId,
        customerEmail: vars.parsedOrder.customerEmail,
        totalAmount: vars.parsedOrder.adjustedTotal,
        currency: vars.parsedOrder.currency,
        itemCount: sizeOf(vars.parsedOrder.items),
        accountTier: vars.parsedOrder.customerData.accountTier default "STANDARD",
        shippingCountry: vars.parsedOrder.shippingAddress.country
    }
}
