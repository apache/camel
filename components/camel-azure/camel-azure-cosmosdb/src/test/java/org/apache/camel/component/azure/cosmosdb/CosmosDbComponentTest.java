package org.apache.camel.component.azure.cosmosdb;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CosmosDbComponentTest extends CamelTestSupport {

    @Test
    void testCreateEndpointWithNoClientOrEndpoint() {
        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-cosmosdb://"));

        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-cosmosdb://test?databaseEndpoint=https://test.com"));

        assertThrows(ResolveEndpointFailedException.class,
                () -> context.getEndpoint("azure-cosmosdb://test?accountKey=myKey"));
    }

    @Test
    void testCreateEndpointWithConfig() throws Exception {
        final String uri = "azure-cosmosdb://mydb/myContainer";
        final String remaining = "mydb/myContainer";
        final Map<String, Object> params = new HashMap<>();
        params.put("databaseEndpoint", "https://test.com:443");
        params.put("createDatabaseIfNotExists", "true");
        params.put("accountKey", "myKey");

        final CosmosDbEndpoint endpoint = (CosmosDbEndpoint) context.getComponent("azure-cosmosdb", CosmosDbComponent.class).createEndpoint(uri, remaining, params);

        assertEquals("mydb", endpoint.getConfiguration().getDatabaseName());
        assertEquals("myContainer", endpoint.getConfiguration().getContainerName());
        assertEquals("https://test.com:443", endpoint.getConfiguration().getDatabaseEndpoint());
        assertEquals("myKey", endpoint.getConfiguration().getAccountKey());
        assertTrue(endpoint.getConfiguration().isCreateDatabaseIfNotExists());
    }

}
