package org.apache.camel.component.azure.eventhubs.operations;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.eventhubs.EventHubsConfiguration;
import org.apache.camel.component.azure.eventhubs.TestUtils;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventHubsProducerOperationsIT extends CamelTestSupport {

    private EventHubsConfiguration configuration;

    @BeforeAll
    public void prepare() throws Exception {
        final Properties properties = TestUtils.loadAzureAccessFromJvmEnv();

        configuration = new EventHubsConfiguration();
        configuration.setConnectionString(properties.getProperty("connectionString"));
    }


    @Test
    public void testSendSingleEventAsAsync() {
        final EventHubProducerAsyncClient producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);
        final EventHubsProducerOperations operations = new EventHubsProducerOperations(producerAsyncClient, configuration);
        final Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody("test");

        operations.sendEvents(exchange, System.out::println);

        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException ignored) {
        }
    }
}