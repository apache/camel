package org.apache.camel.component.azure.cosmosdb.operations;

import com.azure.cosmos.CosmosAsyncContainer;
import org.apache.camel.component.azure.cosmosdb.CosmosDbTestUtils;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.mock;

class CosmosDbContainerOperationsTest {

    @Test
    void testCreateItem() {
        final CosmosDbContainerOperations operations
                = new CosmosDbContainerOperations(Mono.just(mock(CosmosAsyncContainer.class)));

        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.createItem(null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.createItem("", null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.createItem("tes", null, null));
    }

    @Test
    void upsertItem() {
        final CosmosDbContainerOperations operations
                = new CosmosDbContainerOperations(Mono.just(mock(CosmosAsyncContainer.class)));

        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.upsertItem(null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.upsertItem("", null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.upsertItem("tes", null, null));
    }

    @Test
    void deleteItem() {
        final CosmosDbContainerOperations operations
                = new CosmosDbContainerOperations(Mono.just(mock(CosmosAsyncContainer.class)));

        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.deleteItem(null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.deleteItem("", null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.deleteItem("tes", null, null));
    }

    @Test
    void replaceItem() {
        final CosmosDbContainerOperations operations
                = new CosmosDbContainerOperations(Mono.just(mock(CosmosAsyncContainer.class)));

        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.replaceItem(null, null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.replaceItem("test", null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.replaceItem("test", "testid", null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.replaceItem("", "testid", null, null));
    }

    @Test
    void readItem() {
        final CosmosDbContainerOperations operations
                = new CosmosDbContainerOperations(Mono.just(mock(CosmosAsyncContainer.class)));

        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.readItem(null, null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.readItem("test", null, null, null));
    }

    @Test
    void readAllItems() {
        final CosmosDbContainerOperations operations
                = new CosmosDbContainerOperations(Mono.just(mock(CosmosAsyncContainer.class)));

        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.readAllItems(null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.readAllItems(null, null, Object.class));
    }

    @Test
    void queryItems() {
        final CosmosDbContainerOperations operations
                = new CosmosDbContainerOperations(Mono.just(mock(CosmosAsyncContainer.class)));

        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.queryItems(null, null, null));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.queryItems(null, null, Object.class));
        CosmosDbTestUtils.assertIllegalArgumentException(() -> operations.queryItems("", null, Object.class));
    }
}
