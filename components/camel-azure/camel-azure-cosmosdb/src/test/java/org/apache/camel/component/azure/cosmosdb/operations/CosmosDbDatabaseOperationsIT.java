package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosItemResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbConfiguration;
import org.apache.camel.component.azure.cosmosdb.CosmosDbConfigurationOptionsProxy;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.client.CosmosDbClientFactory;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CosmosDbDatabaseOperationsIT {

    private CosmosAsyncClientWrapper clientWrapper;
    private CosmosDbConfiguration configuration;

    @BeforeAll
    public void prepare() throws Exception {
        final Properties properties = CosmosDbTestUtils.loadAzureAccessFromJvmEnv();

        configuration = new CosmosDbConfiguration();
        configuration.setAccountKey(properties.getProperty("access_key"));
        configuration.setDatabaseEndpoint(properties.getProperty("endpoint"));

        final CosmosAsyncClient client = CosmosDbClientFactory.createCosmosAsyncClient(configuration);

        clientWrapper = new CosmosAsyncClientWrapper(client);
    }

    @Test
    void testCreateDeleteDatabase() throws InterruptedException {
        final String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        configuration.setDatabaseName(databaseName);

        final CosmosDbDatabaseOperations operations = new CosmosDbDatabaseOperations(
                new CosmosDbConfigurationOptionsProxy(configuration),
                clientWrapper);

        // create database
        final CosmosDbTestUtils.Latch createLatch = new CosmosDbTestUtils.Latch();
        operations.createDatabase(null, cosmosDatabaseResponse -> {
            assertNotNull(cosmosDatabaseResponse);
            assertEquals(databaseName, cosmosDatabaseResponse.getProperties().getId());
            // successful if response code within 2xx
            assertTrue(cosmosDatabaseResponse.getStatusCode() >= 200 && cosmosDatabaseResponse.getStatusCode() < 300);
            createLatch.done();
        }, throwable -> {
        }, doneSync -> {
        });

        createLatch.await(5000);

        // delete database
        final CosmosDbTestUtils.Latch deleteLatch = new CosmosDbTestUtils.Latch();
        operations.deleteDatabase(null, cosmosDatabaseResponse -> {
            assertNotNull(cosmosDatabaseResponse);
            // successful if response code within 2xx
            assertTrue(cosmosDatabaseResponse.getStatusCode() >= 200 && cosmosDatabaseResponse.getStatusCode() < 300);
            deleteLatch.done();
        }, throwable -> {
        }, doneSync -> {
        });

        deleteLatch.await(5000);
    }

    @Test
    void testPlay() {
        configuration.setDatabaseName("cgizcgabpz");

        final Map<String, String> data = new HashMap<>();
        //data.put("id", "my-awesome-id");
        data.put("name", "Omar");

        CosmosItemResponse<Map<String, String>> response = clientWrapper.getDatabase(configuration.getDatabaseName())
                .getContainer("test")
                .createItem(data)
                .block();

        System.out.println(response);
    }
}
