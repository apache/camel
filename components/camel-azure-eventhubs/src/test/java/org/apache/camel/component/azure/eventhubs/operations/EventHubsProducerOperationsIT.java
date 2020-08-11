package org.apache.camel.component.azure.eventhubs.operations;

import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.component.azure.eventhubs.EventHubsConstants;
import org.apache.camel.component.azure.eventhubs.TestUtils;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertTrue;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventHubsProducerOperationsIT extends CamelTestSupport {

    private EventHubsConfiguration configuration;

    @BeforeAll
    public void prepare() throws Exception {
        final Properties properties = TestUtils.loadAzureAccessFromJvmEnv();

        configuration = new EventHubsConfiguration();
        configuration.setConnectionString(properties.getProperty("connectionString"));
        configuration.setConsumerGroupName(EventHubClientBuilder.DEFAULT_CONSUMER_GROUP_NAME);
    }


    @Test
    public void testSendSingleEventAsAsync() {
        final EventHubProducerAsyncClient producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);
        final EventHubsProducerOperations operations = new EventHubsProducerOperations(producerAsyncClient, configuration);
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("test");

        AtomicBoolean doneFlag = new AtomicBoolean(false);

        operations.sendEvents(exchange, doneFlag::set);

        Awaitility.await()
                .untilTrue(doneFlag);

        producerAsyncClient.close();
    }

    @Test
    public void testSendEventWithSpecificPartition() {
        final EventHubProducerAsyncClient producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);
        final EventHubsProducerOperations operations = new EventHubsProducerOperations(producerAsyncClient, configuration);
        final String firstPartition = producerAsyncClient.getPartitionIds().blockLast();
        final Exchange exchange = new DefaultExchange(context);

        exchange.getIn().setHeader(EventHubsConstants.PARTITION_ID, firstPartition);
        exchange.getIn().setBody("test should be in firstPartition");

        AtomicBoolean doneFlag = new AtomicBoolean(false);

        operations.sendEvents(exchange, doneFlag::set);

        Awaitility.await()
                .untilTrue(doneFlag);

        final EventHubConsumerAsyncClient consumerAsyncClient = EventHubsClientFactory.createEventHubConsumerAsyncClient(configuration);

        final boolean eventExists = consumerAsyncClient.receiveFromPartition(firstPartition, EventPosition.earliest())
                .any(partitionEvent -> partitionEvent.getPartitionContext().getPartitionId().equals(firstPartition) && partitionEvent.getData().getBodyAsString().contains("test should be in firstPartition"))
                .block();

        assertTrue(eventExists);

        producerAsyncClient.close();
        consumerAsyncClient.close();
    }
}