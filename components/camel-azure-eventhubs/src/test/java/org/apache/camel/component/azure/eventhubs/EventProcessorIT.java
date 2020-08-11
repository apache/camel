package org.apache.camel.component.azure.eventhubs;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.messaging.eventhubs.models.SendOptions;
import com.azure.storage.blob.BlobContainerAsyncClient;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.commons.lang3.RandomStringUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertTrue;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventProcessorIT {

    private EventHubsConfiguration configuration;
    private BlobContainerAsyncClient containerAsyncClient;

    @BeforeAll
    public void prepare() throws Exception {
        final Properties properties = TestUtils.loadAzureAccessFromJvmEnv();
        final String containerName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        configuration = new EventHubsConfiguration();
        configuration.setConnectionString(properties.getProperty("connectionString"));
        configuration.setConsumerGroupName(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME);
        configuration.setBlobAccessKey(properties.getProperty("blobAccessKey"));
        configuration.setBlobAccountName(properties.getProperty("blobAccountName"));

        Map<String, EventPosition> positionMap = new HashMap<>();
        positionMap.put("0", EventPosition.earliest());

        configuration.setEventPosition(positionMap);
        configuration.setBlobContainerName(containerName);

        containerAsyncClient = EventHubsClientFactory.createBlobContainerClient(configuration);

        // create test container
        containerAsyncClient.create().block();
    }

    @Test
    public void testEventProcessingWithBlobCheckpointStore() {
        final AtomicBoolean doneAsync = new AtomicBoolean(false);
        final EventHubProducerAsyncClient producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);
        final Consumer<EventContext> onEvent = eventContext -> {
            final String body = eventContext.getEventData().getBodyAsString();
            if (eventContext.getPartitionContext().getPartitionId().equals("0") && body.contains("Testing Event Consumer With BlobStore")) {
                assertTrue(true);
                doneAsync.set(true);
            }
        };
        final Consumer<ErrorContext> onError = errorContext -> {};
        final EventProcessorClient processorClient = EventHubsClientFactory.createEventProcessorClient(configuration, onEvent, onError);

        processorClient.start();

        producerAsyncClient.send(Collections.singletonList(new EventData("Testing Event Consumer With BlobStore")), new SendOptions().setPartitionId("0")).block();

        Awaitility.await()
                .timeout(30, TimeUnit.SECONDS)
                .untilTrue(doneAsync);

        processorClient.stop();
        producerAsyncClient.close();
    }

    @AfterAll
    public void tearDown() {
        containerAsyncClient.delete().block();
    }
}
