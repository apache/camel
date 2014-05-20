package org.apache.camel.metrics.counter;

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
        if (increment != null) {
            counter.inc(increment);
        }
        else if (decrement != null) {
            counter.dec(decrement);
        }
        else {
            counter.inc();
        }
    }
}
