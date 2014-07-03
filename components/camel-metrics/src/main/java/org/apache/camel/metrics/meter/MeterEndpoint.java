package org.apache.camel.metrics.meter;

import org.apache.camel.Producer;
import org.apache.camel.metrics.AbstractMetricsEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

import com.codahale.metrics.MetricRegistry;

@UriEndpoint(scheme = "metrics")
public class MeterEndpoint extends AbstractMetricsEndpoint {

    public static final String ENDPOINT_URI = "metrics:meter";

    @UriParam
    private Long mark;

    public MeterEndpoint(MetricRegistry registry, String metricsName) {
        super(registry, metricsName);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new MeterProducer(this);
    }

    public Long getMark() {
        return mark;
    }

    public void setMark(Long mark) {
        this.mark = mark;
    }

    @Override
    protected String createEndpointUri() {
        return ENDPOINT_URI;
    }
}
