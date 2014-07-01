package org.apache.camel.metrics;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;

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
}
