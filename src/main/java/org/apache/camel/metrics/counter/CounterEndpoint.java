package org.apache.camel.metrics.counter;

import org.apache.camel.Producer;
import org.apache.camel.metrics.AbstractMetricsEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

import com.codahale.metrics.MetricRegistry;

@UriEndpoint(scheme = "metrics:counter")
public class CounterEndpoint extends AbstractMetricsEndpoint {

    public static final String ENDPOINT_URI = "metrics:counter";

    @UriParam
    private Long increment;

    @UriParam
    private Long decrement;

    public CounterEndpoint(MetricRegistry registry, String metricsName) {
        super(registry, metricsName);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new CounterProducer(this);
    }

    public Long getIncrement() {
        return increment;
    }

    public void setIncrement(Long increment) {
        this.increment = increment;
    }

    public Long getDecrement() {
        return decrement;
    }

    public void setDecrement(Long decrement) {
        this.decrement = decrement;
    }

    @Override
    protected String createEndpointUri() {
        return ENDPOINT_URI;
    }
}
