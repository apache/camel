package org.apache.camel.component.wordpress.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.apache.camel.component.wordpress.config.WordpressEndpointConfiguration;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wordpress4j.WordpressServiceProvider;

public abstract class AbstractWordpressProducer<T> extends DefaultProducer {

    protected static final Logger LOG = LoggerFactory.getLogger(WordpressPostProducer.class);

    private WordpressEndpointConfiguration configuration;

    public AbstractWordpressProducer(WordpressEndpoint endpoint) {
        super(endpoint);
        this.configuration = endpoint.getConfig();
        if (!WordpressServiceProvider.getInstance().hasAuthentication()) {
            LOG.warn("Wordpress Producer hasn't authentication. This may lead to errors during route execution. Wordpress writing operations need authentication.");
        }
    }

    public WordpressEndpointConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public WordpressEndpoint getEndpoint() {
        return (WordpressEndpoint)super.getEndpoint();
    }

    @Override
    public final void process(Exchange exchange) throws Exception {
        if (this.getConfiguration().getId() == null) {
            exchange.getOut().setBody(this.processInsert(exchange));
        } else {
            if (this.getEndpoint().getOperationDetail() == null) {
                exchange.getOut().setBody(this.processUpdate(exchange));
            } else {
                exchange.getOut().setBody(this.processDelete(exchange));
            }
        }
    }

    protected abstract T processInsert(Exchange exchange);

    protected abstract T processUpdate(Exchange exchange);

    protected abstract T processDelete(Exchange exchange);

}
