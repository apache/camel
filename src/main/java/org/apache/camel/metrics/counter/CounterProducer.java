package org.apache.camel.metrics.counter;

import static org.apache.camel.metrics.MetricsComponent.HEADER_COUNTER_DECREMENT;
import static org.apache.camel.metrics.MetricsComponent.HEADER_COUNTER_INCREMENT;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

import com.codahale.metrics.Counter;

public class CounterProducer extends DefaultProducer {

    public CounterProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        CounterEndpoint endpoint = (CounterEndpoint) getEndpoint();
        String metricName = endpoint.getMetricsName(exchange);
        Counter counter = endpoint.getRegistry().counter(metricName);
        Long increment = endpoint.getIncrement();
        Long decrement = endpoint.getDecrement();
        Long finalIncrement = endpoint.getLongHeader(exchange, HEADER_COUNTER_INCREMENT, increment);
        Long finalDecrement = endpoint.getLongHeader(exchange, HEADER_COUNTER_DECREMENT, decrement);
        if (finalIncrement != null) {
            counter.inc(finalIncrement);
        }
        else if (finalDecrement != null) {
            counter.dec(finalDecrement);
        }
        else {
            counter.inc();
        }
    }
}
