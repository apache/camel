package org.apache.camel.component.quartz2;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

public class QuartzConsumer extends DefaultConsumer {
    public QuartzConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }
}
