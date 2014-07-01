package org.apache.camel.metrics.histogram;

import org.apache.camel.Producer;
import org.apache.camel.metrics.AbstractMetricsEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

import com.codahale.metrics.MetricRegistry;

@UriEndpoint(scheme = "metrics:histogram")
public class HistogramEndpoint extends AbstractMetricsEndpoint {

    public static String ENDPOINT_URI = "metrics:histogram";

    @UriParam
    private Long value;

    public HistogramEndpoint(MetricRegistry registry, String metricsName) {
        super(registry, metricsName);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HistogramProducer(this);
    }

    public Long getValue() {
        return value;
    }

    public void setValue(Long value) {
        this.value = value;
    }

    @Override
    protected String createEndpointUri() {
        return ENDPOINT_URI;
    }
}
