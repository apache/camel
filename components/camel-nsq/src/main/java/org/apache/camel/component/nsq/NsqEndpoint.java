package org.apache.camel.component.nsq;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;

import java.util.concurrent.ExecutorService;

/**
 * Represents a nsq endpoint.
 */
@UriEndpoint(firstVersion = "2.21.0", scheme = "nsq", title = "nsq", syntax="nsq:lookupServer",
             consumerClass = NsqConsumer.class, label = "messaging")
public class NsqEndpoint extends DefaultEndpoint {

    @UriParam
    private NsqConfiguration configuration;

    public NsqEndpoint(String uri, NsqComponent component, NsqConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }
    public Producer createProducer() throws Exception {
        return new NsqProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new NsqConsumer(this, processor);
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "NsqTopic[" + configuration.getTopic() + "]", configuration.getPoolSize());
    }

    public boolean isSingleton() {
        return true;
    }

    public NsqConfiguration getNsqConfiguration() {
        return configuration;
    }
}
