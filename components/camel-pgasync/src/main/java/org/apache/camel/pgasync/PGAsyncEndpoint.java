package org.apache.camel.pgasync;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;

/**
 * Represents a pgasync endpoint.
 */
public class PGAsyncEndpoint extends DefaultEndpoint {

    public PGAsyncEndpoint() {
    }

    public PGAsyncEndpoint(String uri, PGAsyncComponent component) {
        super(uri, component);
    }

    public PGAsyncEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        return new PGAsyncProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new PGAsyncConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }
}
