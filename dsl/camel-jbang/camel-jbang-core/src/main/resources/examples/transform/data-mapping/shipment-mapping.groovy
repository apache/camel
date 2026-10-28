// Maps the shop's order (the message body, parsed from JSON into a map) to the courier's shipment
// format. Runs as a Groovy script from the route: the last expression is the new message body.
def order = body

[
    shipmentRef : "SHIP-" + order.orderId.replace("ORD-", ""),
    recipient   : [customerNo: order.customer, countryCode: order.country],
    parcels     : order.lines.collect { line -> [article: line.sku, pieces: line.qty] },
    totalPieces : order.lines.sum { line -> line.qty },
    service     : order.country == "DK" ? "domestic" : "international",
    createdAt   : java.time.LocalDate.now().toString()
]
