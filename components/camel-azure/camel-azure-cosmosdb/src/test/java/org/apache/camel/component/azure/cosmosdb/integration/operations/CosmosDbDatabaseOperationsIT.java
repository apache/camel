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
import com.azure.cosmos.models.CosmosDatabaseResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbClientOperations;
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
        final CosmosDatabaseResponse createdDatabase = CosmosDbClientOperations.withClient(clientWrapper)
                .createDatabase(databaseName, null)
                .block();

        assertNotNull(createdDatabase);
        assertEquals(databaseName, createdDatabase.getProperties().getId());

        // successful if response code within 2xx
        assertTrue(createdDatabase.getStatusCode() >= 200 && createdDatabase.getStatusCode() < 300);

        // test delete the created database
        final CosmosDatabaseResponse deletedDatabase = CosmosDbClientOperations.withClient(clientWrapper)
                .getDatabaseOperations(databaseName)
                .deleteDatabase(null)
                .block();

        assertNotNull(deletedDatabase);

        // successful if response code within 2xx
        assertTrue(deletedDatabase.getStatusCode() >= 200 && deletedDatabase.getStatusCode() < 300);
    }
}
