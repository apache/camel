package org.apache.camel.component.azure.eventhubs;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import io.netty.util.internal.shaded.org.jctools.queues.MessagePassingQueue;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class EventProcessorTest {

    @Test
    public void testCreateEventProcessorWithNonValidOptions() {
        final EventHubsConfiguration configuration = new EventHubsConfiguration();
        final Consumer<EventContext> onEvent = event -> {};
        final Consumer<ErrorContext> onError = error -> {};

        assertThrows(IllegalArgumentException.class, () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setBlobContainerName("testContainer");
        assertThrows(IllegalArgumentException.class, () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setBlobAccountName("testAcc");
        assertThrows(IllegalArgumentException.class, () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setBlobAccessKey("testAccess");
        assertNotNull(EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));

        configuration.setBlobContainerName(null);
        assertThrows(IllegalArgumentException.class, () -> EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError));
    }
}
