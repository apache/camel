package org.apache.camel.metrics.meter;

import org.apache.camel.Exchange;
import org.apache.camel.metrics.AbstractMetricsProducer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

public class MeterProducer extends AbstractMetricsProducer<MeterEndpoint> {

    public MeterProducer(MeterEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, MeterEndpoint endpoint, MetricRegistry registry, String metricsName) throws Exception {
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
