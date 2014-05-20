package org.apache.camel.metrics;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

import com.codahale.metrics.MetricRegistry;

public abstract class AbstractMetricsEndpoint extends DefaultEndpoint {

    protected final MetricRegistry registry;
    protected final String metricsName;

    public AbstractMetricsEndpoint(MetricRegistry registry, String metricsName) {
        this.registry = registry;
        this.metricsName = metricsName;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("Cannot consume from " + getClass().getSimpleName() + ": " + getEndpointUri());
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    public MetricRegistry getRegistry() {
        return registry;
    }

    public String getMetricsName() {
        return metricsName;
    }

    public String getMetricsName(Exchange exchange) {
        return getStringHeader(exchange, MetricsComponent.HEADER_METRIC_NAME, metricsName);
    }

    public String getStringHeader(Exchange exchange, String header, String defaultValue) {
        Message in = exchange.getIn();
        String headerValue = in.getHeader(header, String.class);
        return ObjectHelper.isNotEmpty(headerValue) ? headerValue : defaultValue;
    }

    public Long getLongHeader(Exchange exchange, String header, Long defaultValue) {
        Message in = exchange.getIn();
        return in.getHeader(header, defaultValue, Long.class);
    }
}
