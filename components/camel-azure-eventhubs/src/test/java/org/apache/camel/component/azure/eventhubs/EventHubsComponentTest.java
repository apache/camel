package org.apache.camel.component.azure.eventhubs;

import com.azure.messaging.eventhubs.EventHubConsumerAsyncClient;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.azure.eventhubs.client.EventHubsClientFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventHubsComponentTest extends CamelTestSupport {

    @Test
    public void testCreateEndpointWithNoEventHubsNameOrNameSpace() throws Exception {
        ResolveEndpointFailedException exception = assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint("azure-eventhubs:?connectionString=string"));
        assertTrue(exception.getMessage().contains("Namespace and eventHub name must be specified"));

        ResolveEndpointFailedException exception2 = assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint("azure-eventhubs:name?connectionString=string"));
        assertTrue(exception2.getMessage().contains("Namespace and eventHub name must be specified"));
    }

    @Test
    public void testCreateEndpointWithNoSuppliedClientsOrKeysOrConnectionString() {
        final String expectedErrorMessage = "Azure EventHubs SharedAccessName/SharedAccessKey, ConsumerAsyncClient/ProducerAsyncClient " +
                "or connectionString must be specified";

        // first case: with no client or key or connectionstring
        assertTrue(getErrorMessage("azure-eventhubs:name/hubName?").contains(expectedErrorMessage));

        // second case: connectionString set
        assertNotNull(context.getEndpoint("azure-eventhubs:name/hubName?connectionString=string"));

        // third case: either access key or access name set
        assertTrue(getErrorMessage("azure-eventhubs:name/hubName?sharedAccessName=test").contains(expectedErrorMessage));
        assertTrue(getErrorMessage("azure-eventhubs:name/hubName?sharedAccessKey=test").contains(expectedErrorMessage));
        assertNotNull(context.getEndpoint("azure-eventhubs:name/hubName?sharedAccessName=test&sharedAccessKey=test"));

        // forth case: with client set
        final EventHubsConfiguration configuration = new EventHubsConfiguration();
        configuration.setNamespace("test");
        configuration.setConsumerGroupName("testGroup");
        configuration.setSharedAccessKey("dummyKey");
        configuration.setSharedAccessName("dummyUser");

        final EventHubConsumerAsyncClient consumerAsyncClient = EventHubsClientFactory.createEventHubConsumerAsyncClient(configuration);
        final EventHubProducerAsyncClient producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);

        context.getRegistry().bind("consumerClient", consumerAsyncClient);
        context.getRegistry().bind("producerClient", producerAsyncClient);

        assertNotNull(context.getEndpoint("azure-eventhubs:name/hubName?autoDiscoverClient=false&consumerAsyncClient=#consumerClient"));
        assertNotNull(context.getEndpoint("azure-eventhubs:name/hubName?autoDiscoverClient=false&producerAsyncClient=#producerClient"));
    }

    @Test
    public void testClientAutoDiscovery() {
        final EventHubsConfiguration configuration = new EventHubsConfiguration();
        configuration.setNamespace("test");
        configuration.setConsumerGroupName("testGroup");
        configuration.setSharedAccessKey("dummyKey");
        configuration.setSharedAccessName("dummyUser");

        final EventHubConsumerAsyncClient consumerAsyncClient = EventHubsClientFactory.createEventHubConsumerAsyncClient(configuration);
        final EventHubConsumerAsyncClient consumerAsyncClient2 = EventHubsClientFactory.createEventHubConsumerAsyncClient(configuration);
        final EventHubProducerAsyncClient producerAsyncClient = EventHubsClientFactory.createEventHubProducerAsyncClient(configuration);

        // we dont allow more than one instance
        context.getRegistry().bind("consumerClient", consumerAsyncClient);
        context.getRegistry().bind("consumerClient2", consumerAsyncClient2);

        assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint("azure-eventhubs:name/hubName"));

        context.getRegistry().bind("producerClient", producerAsyncClient);

        final EventHubsEndpoint endpoint = (EventHubsEndpoint) context.getEndpoint("azure-eventhubs:name/hubName");

        assertEquals(producerAsyncClient, endpoint.getConfiguration().getProducerAsyncClient());
    }

    @Test
    public void testCreateEndpointWithConfig() {
        final String uri = "azure-eventhubs:namespace/hubName?connectionString=" +
                "Endpoint=sb://dummynamespace.servicebus.windows.net/;SharedAccessKeyName=DummyAccessKeyName;SharedAccessKey=DummyKey;EntityPath=test" +
                "&consumerGroupName=testConsumer&prefetchCount=100";

        final EventHubsEndpoint endpoint = (EventHubsEndpoint) context.getEndpoint(uri);

        assertEquals("namespace", endpoint.getConfiguration().getNamespace());
        assertEquals("hubName", endpoint.getConfiguration().getEventHubName());
        assertEquals("Endpoint=sb://dummynamespace.servicebus.windows.net/;SharedAccessKeyName=DummyAccessKeyName;SharedAccessKey=DummyKey;EntityPath=test",
                endpoint.getConfiguration().getConnectionString());
        assertEquals("testConsumer", endpoint.getConfiguration().getConsumerGroupName());
        assertEquals(100, endpoint.getConfiguration().getPrefetchCount());
        assertTrue(endpoint.getConfiguration().isAutoDiscoverClient());
    }

    private String getErrorMessage(final String uri) {
        ResolveEndpointFailedException exception = assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint(uri));
        return exception.getMessage();
    }

}