package org.apache.camel.component.wordpress.consumer;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.apache.camel.component.wordpress.config.WordpressEndpointConfiguration;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractWordpressConsumer extends ScheduledPollConsumer {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractWordpressConsumer.class);
    
    private WordpressEndpointConfiguration configuration;

    public AbstractWordpressConsumer(WordpressEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.configuration = endpoint.getConfig();
        this.initConsumer();
    }

    public AbstractWordpressConsumer(WordpressEndpoint endpoint, Processor processor, ScheduledExecutorService scheduledExecutorService) {
        super(endpoint, processor, scheduledExecutorService);
        this.configuration = endpoint.getConfig();
        this.initConsumer();
    }

    public WordpressEndpointConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean isGreedy() {
        return false;
    }
    
    private void initConsumer() {
        this.configureService(configuration);
    }

    /**
     * Should be implemented to configure the endpoint calls. Called during consumer initialization
     * 
     * @param configuration the endpoint configuration
     */
    protected void configureService(WordpressEndpointConfiguration configuration) {
        
    }
    
    @Override
    protected abstract int poll() throws Exception;

    /**
     * Message processor
     * @param result
     */
    protected final void process(final Object result) {
        Exchange exchange = getEndpoint().createExchange();
        try {
            exchange.getIn().setBody(result);
            getProcessor().process(exchange);
        } catch (Exception e) {
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
