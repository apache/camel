/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.azure.cosmosdb.integration.operations;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.PartitionKey;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbClientOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbContainerOperations;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CosmosDbItemOperationsIT {

    private CosmosAsyncClientWrapper clientWrapper;
    private final String databaseName = RandomStringUtils.randomAlphabetic(10).toLowerCase();
    private final String containerId = RandomStringUtils.randomAlphabetic(5).toLowerCase();

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

        final CosmosDbContainerOperations operations = CosmosDbClientOperations.withClient(clientWrapper)
                .createDatabaseIfNotExistAndGetDatabaseOperations(databaseName, null)
                .createContainerIfNotExistAndGetContainerOperations(containerId, "partition", null);

        operations.createItem(item1, new PartitionKey("test-1"), null).block();
        operations.createItem(item2, new PartitionKey("test-1"), null).block();

        final Iterable<Object> items = CosmosDbClientOperations.withClient(clientWrapper)
                .createDatabaseIfNotExistAndGetDatabaseOperations(databaseName, null)
                .createContainerIfNotExistAndGetContainerOperations(containerId, "id", null)
                .readAllItems(new PartitionKey("test-1"), null, Object.class, null)
                .toIterable();

        items.forEach(data -> System.out.println(data));
    }
}
