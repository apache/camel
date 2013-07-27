package org.apache.camel.component.quartz2;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.quartz.Scheduler;

public class QuartzConsumer extends DefaultConsumer {
    public QuartzConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    public QuartzEndpoint getEndpoint() {
        return (QuartzEndpoint)super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        getEndpoint().onConsumerStart(this);
    }

    @Override
    protected void doStop() throws Exception {
        getEndpoint().onConsumerStop(this);
    }
}
