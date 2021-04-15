package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosItemResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CosmosDbItemOperationsBuilderIT {

    private CosmosAsyncClientWrapper clientWrapper;
    private final String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    private final String containerId = RandomStringUtils.randomAlphabetic(5).toLowerCase();
    private final String containerPath = RandomStringUtils.randomAlphabetic(5).toLowerCase();

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
    void testItemCreateAndDelete() {
        final Map<String, Object> item = new HashMap<>();
        item.put("id", "test-id-1");
        item.put("field1", 12234);
        item.put("field2", "awesome!");

        final CosmosItemResponse<Object> response = CosmosDbDatabaseOperationsBuilder.withClient(clientWrapper)
                .withCreateDatabaseIfNotExist(true)
                .withDatabaseName(databaseName)
                .getContainerOperationBuilder()
                .withCreateContainerIfNotExist(true)
                .withContainerId(containerId)
                .withContainerPartitionKeyPath(containerPath)
                .getItemOperationBuilder()
                .withItem(item)
                .createItem()
                .block();

        System.out.println(response);
    }
}
