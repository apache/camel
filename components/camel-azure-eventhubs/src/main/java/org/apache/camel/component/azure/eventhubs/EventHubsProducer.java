package org.apache.camel.component.azure.eventhubs;

import java.util.Collections;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.DefaultProducer;
import reactor.core.publisher.Flux;

public class EventHubsProducer extends DefaultAsyncProducer {

    public EventHubsProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        EventHubProducerAsyncClient producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(getEndpoint().getConfiguration());

        producerAsyncClient
                .send(Collections.singletonList(new EventData("test")));

    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        return false;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }

    @Override
    public EventHubsEndpoint getEndpoint() {
        return (EventHubsEndpoint) super.getEndpoint();
    }
}
