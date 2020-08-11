package org.apache.camel.component.azure.eventhubs;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

public class EventHubsConsumer extends DefaultConsumer {

    public EventHubsConsumer(final EventHubsEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }


}
