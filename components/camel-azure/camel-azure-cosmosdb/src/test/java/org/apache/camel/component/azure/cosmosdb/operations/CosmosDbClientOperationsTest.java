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
package org.apache.camel.component.azure.cosmosdb.operations;

import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmosDbClientOperationsTest {

    @Test
    void testCreateDatabase() {
        final CosmosAsyncClientWrapper client = mock(CosmosAsyncClientWrapper.class);
        when(client.createDatabaseIfNotExists(any(), any())).thenReturn(Mono.just(mock(CosmosDatabaseResponse.class)));

        final CosmosDbClientOperations operations = CosmosDbClientOperations.withClient(client);

        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.createDatabase(null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.createDatabase("", null));

        assertNotNull(operations.createDatabase("test", null).block());
    }

    @Test
    void testCreateDatabaseIfNotExistAndGetDatabaseOperations() {
        final CosmosAsyncClientWrapper client = mock(CosmosAsyncClientWrapper.class);
        final CosmosAsyncDatabase databaseNew = mock(CosmosAsyncDatabase.class);
        final CosmosAsyncDatabase databaseExisting = mock(CosmosAsyncDatabase.class);

        when(databaseNew.getId()).thenReturn("test-new-database");
        when(databaseExisting.getId()).thenReturn("test-existing-database");

        // when is a new database
        when(client.getDatabase("test-new-database")).thenReturn(databaseNew);
        // when is an existing database
        when(client.getDatabase("test-existing-database")).thenReturn(databaseExisting);

        when(client.createDatabaseIfNotExists(any(), any())).thenReturn(Mono.just(mock(CosmosDatabaseResponse.class)));

        final CosmosDbClientOperations operations = CosmosDbClientOperations.withClient(client);

        CosmosDbTestUtils
                .assertIllegalArgumentException(() -> operations.createDatabaseIfNotExistAndGetDatabaseOperations(null, null));
        CosmosDbTestUtils
                .assertIllegalArgumentException(() -> operations.createDatabaseIfNotExistAndGetDatabaseOperations("", null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.getDatabaseOperations(null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.getDatabaseOperations(""));

        assertEquals("test-new-database",
                operations.createDatabaseIfNotExistAndGetDatabaseOperations("test-new-database", null).getDatabaseId().block());
        assertEquals("test-existing-database",
                operations.getDatabaseOperations("test-existing-database").getDatabaseId().block());
    }

    @Test
    void testQueryDatabases() {
        final CosmosAsyncClientWrapper client = mock(CosmosAsyncClientWrapper.class);
        final CosmosAsyncDatabase database = mock(CosmosAsyncDatabase.class);

        when(client.getDatabase("test")).thenReturn(database);

        final CosmosDbClientOperations operations = CosmosDbClientOperations.withClient(client);

        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.queryDatabases(null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.queryDatabases("", null));
    }

}
