package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.function.Function;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.PartitionKey;
import reactor.core.publisher.Mono;

public class CosmosDbItemOperationsBuilder {

    private final Mono<CosmosAsyncContainer> container;

    // properties
    private Object item;
    private PartitionKey itemPartitionKey;
    private CosmosItemRequestOptions itemRequestOptions;

    public CosmosDbItemOperationsBuilder(final Mono<CosmosAsyncContainer> container) {
        this.container = container;
    }

    // properties DSL
    public CosmosDbItemOperationsBuilder withItem(Object item) {
        this.item = item;
        return this;
    }

    public CosmosDbItemOperationsBuilder withItemPartitionKey(PartitionKey partitionKey) {
        this.itemPartitionKey = partitionKey;
        return this;
    }

    public CosmosDbItemOperationsBuilder withItemRequestOptions(CosmosItemRequestOptions itemRequestOptions) {
        this.itemRequestOptions = itemRequestOptions;
        return this;
    }

    // Database operations
    public Mono<CosmosItemResponse<Object>> createItem() {
        return applyToContainer(container -> container.createItem(item, itemPartitionKey, itemRequestOptions));
    }

    private <T> Mono<T> applyToContainer(final Function<CosmosAsyncContainer, Mono<T>> fn) {
        return container.flatMap(fn);
    }
}
