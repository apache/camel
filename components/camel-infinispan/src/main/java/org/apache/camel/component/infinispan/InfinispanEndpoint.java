package org.apache.camel.component.infinispan;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

public class InfinispanEndpoint extends DefaultEndpoint {
    private InfinispanConfiguration configuration;

    public InfinispanEndpoint() {
    }

    public InfinispanEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public InfinispanEndpoint(String uri, InfinispanComponent component, InfinispanConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        return new InfinispanProducer(this, configuration);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new InfinispanConsumer(this, processor, configuration);
    }

    public boolean isSingleton() {
        return true;
    }

}
