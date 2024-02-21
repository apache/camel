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

import java.util.Properties;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.IndexingMode;
import com.azure.cosmos.models.IndexingPolicy;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbClientOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbDatabaseOperations;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "endpoint", matches = ".*",
                                 disabledReason = "Make sure you supply CosmosDB endpoint, e.g: mvn clean install -Dendpoint="),
        @EnabledIfSystemProperty(named = "accessKey", matches = ".*",
                                 disabledReason = "Make sure you supply CosmosDB accessKey, e.g: mvn clean install -DaccessKey=")
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CosmosDbDatabaseOperationsIT {
    private static final String DATABASE_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

    private CosmosAsyncClientWrapper clientWrapper;
    private CosmosDbDatabaseOperations operations;

    @BeforeAll
    void prepare() throws Exception {
        final Properties properties = CosmosDbTestUtils.loadAzureAccessFromJvmEnv();

        final CosmosAsyncClient client = new CosmosClientBuilder()
                .key(properties.getProperty("access_key"))
                .endpoint(properties.getProperty("endpoint"))
                .buildAsyncClient();

        clientWrapper = new CosmosAsyncClientWrapper(client);

        // create our testing database
        clientWrapper.createDatabase(DATABASE_NAME).block();

        operations = CosmosDbClientOperations.withClient(clientWrapper).getDatabaseOperations(DATABASE_NAME);
    }

    @AfterAll
    void tearDown() {
        clientWrapper.getDatabase(DATABASE_NAME).delete().block();
    }

    @Test
    void testCreateDeleteContainer() {
        final String containerId = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        // test create container
        final CosmosContainerResponse createdContainer = operations
                .createContainer(containerId, "/test", null, null)
                .block();

        assertNotNull(createdContainer);
        assertEquals(containerId, createdContainer.getProperties().getId());

        // successful if response code within 2xx
        assertTrue(createdContainer.getStatusCode() >= 200 && createdContainer.getStatusCode() < 300);

        // test delete container
        final CosmosContainerResponse deletedContainer = operations
                .getContainerOperations(containerId)
                .deleteContainer(null)
                .block();

        assertNotNull(deletedContainer);

        // successful if response code within 2xx
        assertTrue(deletedContainer.getStatusCode() >= 200 && deletedContainer.getStatusCode() < 300);
    }

    @Test
    void testGetContainerOperations() {
        final String containerId = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        // first try to get operations without creating the container
        operations
                .getContainerOperations(containerId)
                .getContainerId()
                .block();

        // we expect an exception since container is not existing and we don't want to create a container
        assertThrows(Exception.class, () -> clientWrapper.getDatabase(DATABASE_NAME).getContainer(containerId).read().block());

        // second we test if we want to create a container when we get container operations
        operations
                .createContainerIfNotExistAndGetContainerOperations(containerId, "/path", null,
                        new IndexingPolicy().setIndexingMode(IndexingMode.CONSISTENT))
                .getContainerId()
                .block();

        assertNotNull(clientWrapper.getDatabase(DATABASE_NAME).getContainer(containerId).read().block());
    }

    @Test
    void testQueryAndReadAllContainers() {
        // create bunch of containers
        final String prefixContainerNames = RandomStringUtils.randomAlphabetic(10).toLowerCase();
        final int expectedSize = 5;

        for (int i = 0; i < expectedSize; i++) {
            clientWrapper.getDatabase(DATABASE_NAME).createContainer(prefixContainerNames + i, "/path").block();
        }

        final long queryTotalSize = operations
                .queryContainers("SELECT * from c", null)
                .toStream()
                .count();

        final long readAllTotalSize = operations
                .readAllContainers(null)
                .toStream()
                .count();

        // assert all databases
        assertEquals(expectedSize, queryTotalSize);
        assertEquals(expectedSize, readAllTotalSize);

        // test against query single container
        final String specificContainerName = prefixContainerNames + 2;
        final String query = String.format("SELECT * from c where c.id = '%s'", specificContainerName);
        final CosmosContainerProperties singleContainer = operations
                .queryContainers(query, null)
                .blockFirst();

        assertNotNull(singleContainer);
        assertEquals(singleContainer.getId(), specificContainerName);
    }
}
