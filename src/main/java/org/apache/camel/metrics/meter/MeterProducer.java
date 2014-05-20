package org.apache.camel.metrics.meter;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

public class MeterProducer extends DefaultProducer {

    public MeterProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        MeterEndpoint endpoint = (MeterEndpoint) getEndpoint();
        String metricsName = endpoint.getMetricsName(exchange);
        MetricRegistry registry = endpoint.getRegistry();
        Meter meter = registry.meter(metricsName);
        Long mark = endpoint.getMark();
        if (mark == null) {
            meter.mark();
        }
        else {
            meter.mark(mark);
        }
    }
}
