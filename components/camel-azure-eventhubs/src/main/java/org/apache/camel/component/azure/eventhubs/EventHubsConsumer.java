package org.apache.camel.component.azure.eventhubs;

import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.support.DefaultConsumer;

public class EventHubsConsumer extends DefaultConsumer {

    // we use the EventProcessorClient as recommended by Azure docs
    private EventProcessorClient processorClient;

    public EventHubsConsumer(final EventHubsEndpoint endpoint, final Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // create the client
        processorClient = EventHubsClientFactory.createEventProcessorClient(getConfiguration(),
                this::onEventListener, this::onErrorListener);

        // start the client but we will rely on the Azure Client Scheduler for thread management
        processorClient.start();
    }

    @Override
    protected void doStop() throws Exception {
        // shutdown the client
        processorClient.stop();

        // shutdown camel consumer
        stop();
    }

    public EventHubsConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    @Override
    public EventHubsEndpoint getEndpoint() {
        return (EventHubsEndpoint) super.getEndpoint();
    }

    private void onEventListener(final EventContext eventContext) {
        final Exchange exchange = getEndpoint().createAzureEventHubExchange(eventContext);

        try {
            // send message to next processor in the route
            getAsyncProcessor().process(exchange);
            // update checkpoint store to update our offsets
            eventContext.updateCheckpoint();
        } catch (Exception ex) {
            exchange.setException(ex);
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange,
                        exchange.getException());
            }
        }
    }

    private void onErrorListener(final ErrorContext errorContext) {
        final Exchange exchange = getEndpoint().createAzureEventHubExchange(errorContext);

        // log exception if an exception occurred and was not handled
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange", exchange,
                    exchange.getException());
        }
    }
}
