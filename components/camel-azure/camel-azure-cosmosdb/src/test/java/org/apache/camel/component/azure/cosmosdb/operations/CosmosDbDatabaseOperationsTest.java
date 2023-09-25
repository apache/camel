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

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmosDbDatabaseOperationsTest {

    @Test
    void testCreateContainer() {
        final CosmosAsyncDatabase database = mock(CosmosAsyncDatabase.class);
        final CosmosContainerResponse containerResponse = mock(CosmosContainerResponse.class);

        when(containerResponse.getProperties()).thenReturn(new CosmosContainerProperties("test-container", "/path"));
        when(database.createContainerIfNotExists(any(), any(), any())).thenReturn(Mono.just(containerResponse));

        final CosmosDbDatabaseOperations databaseOperations = new CosmosDbDatabaseOperations(Mono.just(database));

        // assert params
        CosmosDbTestUtils
                .assertIllegalArgumentException(() -> databaseOperations.createContainer(null, null, null, null));
        CosmosDbTestUtils
                .assertIllegalArgumentException(() -> databaseOperations.createContainer("", null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> databaseOperations.createContainer("", "", null, null));
        CosmosDbTestUtils
                .assertIllegalArgumentException(() -> databaseOperations.createContainer("test", "", null, null));
        CosmosDbTestUtils
                .assertIllegalArgumentException(() -> databaseOperations.createContainer("", "test", null, null));

        // assert key path
        final CosmosContainerResponse returnedContainerResponseFirstCase
                = databaseOperations.createContainer("test-container", "path", null, null).block();
        final CosmosContainerResponse returnedContainerResponseSecondCase
                = databaseOperations.createContainer("test-container", "/path", null, null).block();

        assertNotNull(returnedContainerResponseFirstCase);
        assertNotNull(returnedContainerResponseSecondCase);
        assertEquals("test-container", returnedContainerResponseFirstCase.getProperties().getId());
        assertEquals("test-container", returnedContainerResponseSecondCase.getProperties().getId());
        assertEquals("/path", returnedContainerResponseFirstCase.getProperties().getPartitionKeyDefinition().getPaths().get(0));
        assertEquals("/path",
                returnedContainerResponseSecondCase.getProperties().getPartitionKeyDefinition().getPaths().get(0));
    }

    @Test
    void createContainerIfNotExistAndGetContainerOperations() {
        final CosmosAsyncDatabase database = mock(CosmosAsyncDatabase.class);
        final CosmosAsyncContainer containerNew = mock(CosmosAsyncContainer.class);
        final CosmosAsyncContainer containerExisting = mock(CosmosAsyncContainer.class);

        when(containerNew.getId()).thenReturn("container-new");
        when(containerExisting.getId()).thenReturn("container-existing");
        when(database.getContainer("container-new")).thenReturn(containerNew);
        when(database.getContainer("container-existing")).thenReturn(containerExisting);
        when(database.createContainerIfNotExists(any(), any(), any()))
                .thenReturn(Mono.just(mock(CosmosContainerResponse.class)));

        final CosmosDbDatabaseOperations databaseOperations = new CosmosDbDatabaseOperations(Mono.just(database));

        // assert params
        CosmosDbTestUtils.assertIllegalArgumentException(
                () -> databaseOperations.createContainerIfNotExistAndGetContainerOperations(null, null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(
                () -> databaseOperations.createContainerIfNotExistAndGetContainerOperations("", null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(
                () -> databaseOperations.createContainerIfNotExistAndGetContainerOperations("", "", null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(
                () -> databaseOperations.createContainerIfNotExistAndGetContainerOperations("test", "", null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(
                () -> databaseOperations.createContainerIfNotExistAndGetContainerOperations("", "test", null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> databaseOperations.getContainerOperations(null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> databaseOperations.getContainerOperations(""));

        assertEquals("container-new", databaseOperations
                .createContainerIfNotExistAndGetContainerOperations("container-new", "/path", null, null).getContainerId()
                .block());
        assertEquals("container-existing",
                databaseOperations.getContainerOperations("container-existing").getContainerId().block());
    }

    @Test
    void testQueryContainers() {
        final CosmosAsyncDatabase database = mock(CosmosAsyncDatabase.class);

        final CosmosDbDatabaseOperations databaseOperations = new CosmosDbDatabaseOperations(Mono.just(database));

        CosmosDbTestUtils.assertIllegalArgumentException(() -> databaseOperations.queryContainers(null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> databaseOperations.queryContainers("", null));
    }
}
