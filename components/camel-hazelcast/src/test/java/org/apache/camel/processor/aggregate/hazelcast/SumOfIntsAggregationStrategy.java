package org.apache.camel.processor.aggregate.hazelcast;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
* @author Alexander Lomov
*         Date: 04.01.14
*         Time: 13:25
*/
class SumOfIntsAggregationStrategy implements AggregationStrategy {
    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        } else {
            Integer n = newExchange.getIn().getBody(Integer.class);
            Integer o = oldExchange.getIn().getBody(Integer.class);
            Integer v = (o == null ? 0 : o) + (n == null ? 0 : n);
            oldExchange.getIn().setBody(v, Integer.class);
            return oldExchange;
        }
    }
}
