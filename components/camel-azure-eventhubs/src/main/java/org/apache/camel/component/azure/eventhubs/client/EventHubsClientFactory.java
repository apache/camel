package org.apache.camel.component.azure.eventhubs.client;

import java.util.Locale;

import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;

public final class EventHubsClientFactory {

    private static final String SERVICE_URI_SEGMENT = "servicebus.windows.net";

    private EventHubsClientFactory(){
    }

    public static EventHubProducerAsyncClient createEventHubProducerAsyncClient(final EventHubsConfiguration configuration) {
        return new EventHubClientBuilder()
                .connectionString(buildConnectionString(configuration))
                .buildAsyncProducerClient();
    }

    public static EventHubConsumerAsyncClient createEventHubConsumerAsyncClient(final EventHubsConfiguration configuration) {
        return new EventHubClientBuilder()
                .connectionString(buildConnectionString(configuration))
                .consumerGroup(configuration.getConsumerGroupName())
                .prefetchCount(configuration.getPrefetchCount())
                .buildAsyncConsumerClient();
    }

    private static String buildConnectionString(final EventHubsConfiguration configuration) {
        return String.format(Locale.ROOT, "Endpoint=sb://%s.%s/;SharedAccessKeyName=%s;SharedAccessKey=%s;EntityPath=%s",
                configuration.getNamespace(), SERVICE_URI_SEGMENT, configuration.getSharedAccessName(), configuration.getSharedAccessKey(), configuration.getEventHubName());
    }
}
