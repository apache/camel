package org.apache.camel.metrics.counter;

import static org.apache.camel.metrics.MetricsComponent.HEADER_COUNTER_DECREMENT;
import static org.apache.camel.metrics.MetricsComponent.HEADER_COUNTER_INCREMENT;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.metrics.AbstractMetricsProducer;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

public class CounterProducer extends AbstractMetricsProducer<CounterEndpoint> {

    public CounterProducer(CounterEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, CounterEndpoint endpoint, MetricRegistry registry, String metricsName) throws Exception {
        Message in = exchange.getIn();
        Counter counter = registry.counter(metricsName);
        Long increment = endpoint.getIncrement();
        Long decrement = endpoint.getDecrement();
        Long finalIncrement = getLongHeader(in, HEADER_COUNTER_INCREMENT, increment);
        Long finalDecrement = getLongHeader(in, HEADER_COUNTER_DECREMENT, decrement);
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
