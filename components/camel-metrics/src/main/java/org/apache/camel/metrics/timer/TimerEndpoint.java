package org.apache.camel.metrics.timer;

import org.apache.camel.Producer;
import org.apache.camel.metrics.AbstractMetricsEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

import com.codahale.metrics.MetricRegistry;

@UriEndpoint(scheme = "metrics:timer")
public class TimerEndpoint extends AbstractMetricsEndpoint {

    public static final String ENDPOINT_URI = "metrics:timer";

    public enum TimerAction {
        start,
        stop;
    }

    @UriParam
    private TimerAction action;

    public TimerEndpoint(MetricRegistry registry, String metricsName) {
        super(registry, metricsName);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new TimerProducer(this);
    }

    public TimerAction getAction() {
        return action;
    }

    public void setAction(TimerAction action) {
        this.action = action;
    }

    @Override
    protected String createEndpointUri() {
        return ENDPOINT_URI;
    }
}
