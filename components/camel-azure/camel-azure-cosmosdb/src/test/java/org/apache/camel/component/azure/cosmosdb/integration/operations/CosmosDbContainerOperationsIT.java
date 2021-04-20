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
import com.azure.cosmos.models.CosmosContainerResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbClientOperations;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CosmosDbContainerOperationsIT {

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
        final CosmosContainerResponse createdContainer = CosmosDbClientOperations.withClient(clientWrapper)
                .createDatabaseIfNotExistAndGetDatabaseOperations(databaseName, null)
                .createContainer(containerId, "/test", null)
                .block();

        assertNotNull(createdContainer);
        assertEquals(containerId, createdContainer.getProperties().getId());

        // successful if response code within 2xx
        assertTrue(createdContainer.getStatusCode() >= 200 && createdContainer.getStatusCode() < 300);

        // test delete container
        final CosmosContainerResponse deletedContainer = CosmosDbClientOperations.withClient(clientWrapper)
                .getDatabaseOperations(databaseName)
                .getContainerOperations(containerId)
                .deleteContainer(null)
                .block();

        assertNotNull(deletedContainer);

        // successful if response code within 2xx
        assertTrue(deletedContainer.getStatusCode() >= 200 && deletedContainer.getStatusCode() < 300);
    }
}
