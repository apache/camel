package org.apache.camel.component.azure.eventhubs.operations;

import java.util.Collections;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.models.SendOptions;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.component.azure.eventhubs.EventHubsConfigurationOptionsProxy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class EventHubsProducerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubsProducerOperations.class);

    private final EventHubProducerAsyncClient producerAsyncClient;
    private final EventHubsConfigurationOptionsProxy configurationOptionsProxy;

    public EventHubsProducerOperations(final EventHubProducerAsyncClient producerAsyncClient, final EventHubsConfiguration configuration) {
        ObjectHelper.notNull(producerAsyncClient, "client cannot be null");

        this.producerAsyncClient = producerAsyncClient;
        configurationOptionsProxy = new EventHubsConfigurationOptionsProxy(configuration);
    }

    public void sendEvents(final Exchange exchange, final AsyncCallback callback) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");
        ObjectHelper.notNull(callback, "callback cannot be null");

        final SendOptions sendOptions = createSendOptions(configurationOptionsProxy.getPartitionKey(exchange), configurationOptionsProxy.getPartitionId(exchange));
        final Iterable<EventData> eventData = createEventData(exchange);

        sendAsyncEvents(eventData, sendOptions, exchange, callback);
    }

    private void sendAsyncEvents(final Iterable<EventData> eventData, final SendOptions sendOptions, final Exchange exchange, final AsyncCallback asyncCallback) {
        sendAsyncEventsWithSuitableMethod(eventData, sendOptions)
                .subscribe(unused -> {
                            // we finished only with one event, keep async check
                            asyncCallback.done(false);
                        },
                        error -> {
                            LOG.debug("Error processing async exchange with error:" + error.getMessage());
                            exchange.setException(error);
                            asyncCallback.done(true);
                        },
                        () -> {
                            // we are done from everything
                            LOG.debug("All events with exchange have been sent successfully.");
                            asyncCallback.done(true);
                        });

    }

    private Mono<Void> sendAsyncEventsWithSuitableMethod(final Iterable<EventData> eventData, final SendOptions sendOptions) {
        if (ObjectHelper.isEmpty(sendOptions))
            return producerAsyncClient.send(eventData);

        return producerAsyncClient.send(eventData, sendOptions);
    }

    private SendOptions createSendOptions(final String partitionKey, final String partitionId) {
        // if both are set, we don't want that
        if (ObjectHelper.isNotEmpty(partitionKey) && ObjectHelper.isNotEmpty(partitionId))
            throw new IllegalArgumentException("Both partitionKey and partitionId are set. Only one or the other can be set.");

        // if both are not set, we return null and let EventHubs handle the partition assigning
        if (ObjectHelper.isEmpty(partitionKey) && ObjectHelper.isEmpty(partitionId))
            return null;

        return new SendOptions()
                .setPartitionId(partitionId)
                .setPartitionKey(partitionKey);
    }

    private Iterable<EventData> createEventData(final Exchange exchange) {
        final byte[] data = exchange.getIn().getBody(byte[].class);

        if (ObjectHelper.isEmpty(data)) {
            throw new IllegalArgumentException(String.format("Cannot convert message body %s to byte[]. You will " +
                    "to make sure the data encoded in byte[] or add a Camel TypeConverter to convert the data to byte[]", exchange.getIn().getBody()));
        }
        // for now we only support single event
        return Collections.singletonList(new EventData(data));
    }
}
