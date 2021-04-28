package org.apache.camel.component.azure.cosmosdb.integration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.azure.cosmos.models.PartitionKey;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.cosmosdb.CosmosDbConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CosmosDbConsumerIT extends BaseCamelCosmosDbTestSupport {

    private final static String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    private String containerName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

    @BeforeEach
    void createDatabaseContainerAndItems() {
        client.createDatabaseIfNotExists(databaseName).block();
        client.getDatabase(databaseName).createContainerIfNotExists(containerName, "/partition", null).block();

        // create testing items
        final Map<String, Object> item1 = new HashMap<>();
        item1.put("id", "test-id-1");
        item1.put("partition", "test-1");
        item1.put("field1", 12234);
        item1.put("field2", "awesome!");

        final Map<String, Object> item2 = new HashMap<>();
        item2.put("id", "test-id-2");
        item2.put("partition", "test-1");
        item2.put("field1", 6654);
        item2.put("field2", "super awesome!");

        final Map<String, Object> item3 = new HashMap<>();
        item3.put("id", "test-id-3");
        item3.put("partition", "test-2");
        item3.put("field1", 6654);
        item3.put("field2", "super super awesome!");

        client.getDatabase(databaseName).getContainer(containerName).createItem(item1, new PartitionKey("test-1"), null)
                .block();
        client.getDatabase(databaseName).getContainer(containerName).createItem(item2, new PartitionKey("test-1"), null)
                .block();
        client.getDatabase(databaseName).getContainer(containerName).createItem(item3, new PartitionKey("test-2"), null)
                .block();
    }

    @AfterEach
    void removeAllDatabases() {
        // delete all databases being used in the test after each test
        client.readAllDatabases()
                .toIterable()
                .forEach(cosmosDatabaseProperties -> client.getDatabase(cosmosDatabaseProperties.getId()).delete()
                        .block());
    }

    @Test
    void testReadAllItems() throws Exception {
        // start our test route
        context.getRouteController().startRoute("readAllItemsRoute");

        // start testing
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:readAllItems");
        mockEndpoint.expectedMessageCount(1);

        mockEndpoint.assertIsSatisfied(1000);

        final List returnedResults
                = mockEndpoint.getExchanges().get(0).getMessage().getBody(List.class);

        assertEquals(2, returnedResults.size());
    }

    @Test
    void testReadItem() throws Exception {
        // start our test route
        context.getRouteController().startRoute("readItemRoute");

        // start testing
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:readItem");
        mockEndpoint.expectedMessageCount(1);

        mockEndpoint.assertIsSatisfied(1000);

        assertNotNull(mockEndpoint.getExchanges().get(0).getMessage().getHeader(CosmosDbConstants.E_TAG));

        final Map<String, ?> returnedResults
                = mockEndpoint.getExchanges().get(0).getMessage().getBody(Map.class);

        // assert against item2
        assertEquals("test-id-2", returnedResults.get("id"));
        assertEquals("super awesome!", returnedResults.get("field2"));
    }

    @Test
    void testQueryItems() throws Exception {
        // start our test route
        context.getRouteController().startRoute("queryItemsRoute");

        // start testing
        final MockEndpoint mockEndpoint = getMockEndpoint("mock:queryItems");
        mockEndpoint.expectedMessageCount(1);

        mockEndpoint.assertIsSatisfied(1000);

        final List returnedResults
                = mockEndpoint.getExchanges().get(0).getMessage().getBody(List.class);

        assertEquals(1, returnedResults.size());

        final Map<String, ?> returnedItem = (Map<String, ?>) returnedResults.get(0);

        // assert against item3
        assertEquals("test-id-3", returnedItem.get("id"));
        assertEquals("super super awesome!", returnedItem.get("field2"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(String.format("azure-cosmosdb://%s/%s?itemPartitionKey=test-1", databaseName, containerName))
                        .routeId("readAllItemsRoute")
                        .to("mock:readAllItems")
                        .setAutoStartup("false");

                from(String.format("azure-cosmosdb://%s/%s?itemPartitionKey=test-1&itemId=test-id-2&query=SELECT", databaseName,
                        containerName))
                                .routeId("readItemRoute")
                                .to("mock:readItem")
                                .setAutoStartup("false");

                from(String.format("azure-cosmosdb://%s/%s?query=SELECT * FROM c WHERE c.id = 'test-id-3'", databaseName,
                        containerName))
                                .routeId("queryItemsRoute")
                                .to("mock:queryItems")
                                .setAutoStartup("false");
            }
        };
    }
}
