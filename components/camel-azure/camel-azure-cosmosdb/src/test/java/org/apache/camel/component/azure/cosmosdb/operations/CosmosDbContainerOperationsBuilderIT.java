package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.Properties;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosContainerResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CosmosDbContainerOperationsBuilderIT {

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
    void testCreateDeleteContainer() {
        final String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final String containerId = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        // test create container
        /*final CosmosContainerResponse createdContainer = CosmosDbContainerOperationsBuilder.withClient(clientWrapper)
                .createDatabaseIfNotExist()
                .databaseName(databaseName)*/

    }
}
