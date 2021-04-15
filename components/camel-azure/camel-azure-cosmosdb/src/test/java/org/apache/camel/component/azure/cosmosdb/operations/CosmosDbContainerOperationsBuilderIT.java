package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.Properties;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosContainerResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CosmosDbContainerOperationsBuilderIT {

    private CosmosAsyncClientWrapper clientWrapper;
    private final String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();

    @BeforeAll
    void prepare() throws Exception {
        final Properties properties = CosmosDbTestUtils.loadAzureAccessFromJvmEnv();

        final CosmosAsyncClient client = new CosmosClientBuilder()
                .key(properties.getProperty("access_key"))
                .endpoint(properties.getProperty("endpoint"))
                .buildAsyncClient();

        clientWrapper = new CosmosAsyncClientWrapper(client);
    }

    @AfterAll
    void tearDown() {
        clientWrapper.getDatabase(databaseName).delete().block();
    }

    @Test
    void testCreateDeleteContainer() {
        final String containerId = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        // test create container
        final CosmosContainerResponse createdContainer = CosmosDbDatabaseOperationsBuilder.withClient(clientWrapper)
                .withCreateDatabaseIfNotExist(true)
                .withDatabaseName(databaseName)
                .getContainerOperationBuilder()
                .withContainerId(containerId)
                .withContainerPartitionKeyPath("/test")
                .createContainer()
                .block();

        assertNotNull(createdContainer);
        assertEquals(containerId, createdContainer.getProperties().getId());

        // successful if response code within 2xx
        assertTrue(createdContainer.getStatusCode() >= 200 && createdContainer.getStatusCode() < 300);

        // test delete container
        final CosmosContainerResponse deletedContainer = CosmosDbDatabaseOperationsBuilder.withClient(clientWrapper)
                .withDatabaseName(databaseName)
                .getContainerOperationBuilder()
                .withContainerId(containerId)
                .deleteContainer()
                .block();

        assertNotNull(deletedContainer);

        // successful if response code within 2xx
        assertTrue(deletedContainer.getStatusCode() >= 200 && deletedContainer.getStatusCode() < 300);
    }
}
