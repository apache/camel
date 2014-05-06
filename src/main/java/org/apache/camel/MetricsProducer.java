package org.apache.camel;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Metrics producer.
 */
public class MetricsProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsProducer.class);
    private MetricsEndpoint endpoint;

    public MetricsProducer(MetricsEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getIn().getBody());    
    }

}
