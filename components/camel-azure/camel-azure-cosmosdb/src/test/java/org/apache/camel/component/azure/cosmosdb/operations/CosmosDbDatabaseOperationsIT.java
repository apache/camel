package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.Properties;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CosmosDbDatabaseOperationsIT {

    private CosmosAsyncClientWrapper clientWrapper;

    @BeforeAll
    void prepare() throws Exception {
        final Properties properties = CosmosDbTestUtils.loadAzureAccessFromJvmEnv();

        final CosmosAsyncClient client = new CosmosClientBuilder()
                .key(properties.getProperty("access_key"))
                .endpoint(properties.getProperty("endpoint"))
                .buildAsyncClient();

        clientWrapper = new CosmosAsyncClientWrapper(client);
    }

    @Test
    void testCreateDeleteDatabase() {
        final String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

        // test create database
        final CosmosDatabaseResponse createdDatabase = CosmosDbDatabaseOperations.withClient(clientWrapper)
                .withDatabaseName(databaseName)
                .createDatabase()
                .block();

        assertNotNull(createdDatabase);
        assertEquals(databaseName, createdDatabase.getProperties().getId());

        // successful if response code within 2xx
        assertTrue(createdDatabase.getStatusCode() >= 200 && createdDatabase.getStatusCode() < 300);

        // test delete the created database
        final CosmosDatabaseResponse deletedDatabase = CosmosDbDatabaseOperations.withClient(clientWrapper)
                .withDatabaseName(databaseName)
                .deleteDatabase()
                .block();

        assertNotNull(deletedDatabase);

        // successful if response code within 2xx
        assertTrue(deletedDatabase.getStatusCode() >= 200 && deletedDatabase.getStatusCode() < 300);
    }
}
