package org.apache.camel.metrics.meter;

import static org.apache.camel.metrics.MetricsComponent.HEADER_METER_MARK;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.metrics.AbstractMetricsProducer;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;

public class MeterProducer extends AbstractMetricsProducer<MeterEndpoint> {

    public MeterProducer(MeterEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, MeterEndpoint endpoint, MetricRegistry registry, String metricsName) throws Exception {
        Message in = exchange.getIn();
        Meter meter = registry.meter(metricsName);
        Long mark = endpoint.getMark();
        Long finalMark = getLongHeader(in, HEADER_METER_MARK, mark);
        if (finalMark == null) {
            meter.mark();
        }
        else {
            meter.mark(finalMark);
        }
    }
}
