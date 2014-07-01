package org.apache.camel.metrics.histogram;

import static org.apache.camel.metrics.MetricsComponent.HEADER_HISTOGRAM_VALUE;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.metrics.AbstractMetricsProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;

public class HistogramProducer extends AbstractMetricsProducer<HistogramEndpoint> {

    private static final Logger LOG = LoggerFactory.getLogger(HistogramProducer.class);

    public HistogramProducer(HistogramEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doProcess(Exchange exchange, HistogramEndpoint endpoint, MetricRegistry registry, String metricsName) throws Exception {
        Message in = exchange.getIn();
        Histogram histogram = registry.histogram(metricsName);
        Long value = endpoint.getValue();
        Long finalValue = getLongHeader(in, HEADER_HISTOGRAM_VALUE, value);
        if (finalValue != null) {
            histogram.update(finalValue);
        }
        else {
            LOG.warn("Cannot update histogram \"{}\" with null value", metricsName);
        }
    }
}
