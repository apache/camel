package org.apache.camel.pgasync;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The pgasync producer.
 */
public class PGAsyncProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(PGAsyncProducer.class);
    private PGAsyncEndpoint endpoint;

    public PGAsyncProducer(PGAsyncEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        System.out.println(exchange.getIn().getBody());    
    }

}
