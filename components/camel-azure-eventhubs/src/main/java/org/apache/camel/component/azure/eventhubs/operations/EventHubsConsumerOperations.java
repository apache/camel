package org.apache.camel.component.azure.eventhubs.operations;

import com.azure.messaging.eventhubs.EventProcessorClient;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.component.azure.eventhubs.EventHubsConfigurationOptionsProxy;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventHubsConsumerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(EventHubsConsumerOperations.class);

    private final EventProcessorClient processorClient;
    private final EventHubsConfigurationOptionsProxy configurationOptionsProxy;

    public EventHubsConsumerOperations(final EventProcessorClient processorClient, final EventHubsConfiguration configuration) {
        ObjectHelper.notNull(processorClient, "client cannot be null");

        this.processorClient = processorClient;
        configurationOptionsProxy = new EventHubsConfigurationOptionsProxy(configuration);
    }
}
