package org.apache.camel;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents a Metrics endpoint.
 */
public class MetricsEndpoint extends DefaultEndpoint {

    public MetricsEndpoint() {
    }

    public MetricsEndpoint(String uri, MetricsComponent component) {
        super(uri, component);
    }

    public MetricsEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        return new MetricsProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new MetricsConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }
}
