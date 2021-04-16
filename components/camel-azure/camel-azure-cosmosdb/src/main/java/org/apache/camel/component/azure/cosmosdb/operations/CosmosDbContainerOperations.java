package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.function.Function;

import com.azure.core.util.IterableStream;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosContainerRequestOptions;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.ThroughputResponse;
import com.azure.cosmos.util.CosmosPagedFlux;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CosmosDbContainerOperations {

    private final Mono<CosmosAsyncDatabase> database;

    // properties
    private String containerId;
    private String containerPartitionKeyPath;
    private ThroughputProperties containerThroughputProperties;
    private CosmosContainerRequestOptions containerRequestOptions;
    private boolean createContainerIfNotExist;

    // visible for testing
    public CosmosDbContainerOperations(final Mono<CosmosAsyncDatabase> database) {
        this.database = database;
    }

    // properties DSL
    public CosmosDbContainerOperations withContainerId(String containerId) {
        this.containerId = containerId;
        return this;
    }

    public CosmosDbContainerOperations withContainerPartitionKeyPath(String containerPartitionKeyPath) {
        this.containerPartitionKeyPath = containerPartitionKeyPath;
        return this;
    }

    public CosmosDbContainerOperations withContainerThroughputProperties(ThroughputProperties containerThroughputProperties) {
        this.containerThroughputProperties = containerThroughputProperties;
        return this;
    }

    public CosmosDbContainerOperations withCreateContainerIfNotExist(final boolean createContainerIfNotExist) {
        this.createContainerIfNotExist = createContainerIfNotExist;
        return this;
    }

    public CosmosDbContainerOperations withContainerRequestOptions(CosmosContainerRequestOptions containerRequestOptions) {
        this.containerRequestOptions = containerRequestOptions;
        return this;
    }

    // operations
    public Mono<CosmosContainerResponse> createContainer() {
        validateContainerName();

        // we need to check for containerPartitionKeyPath
        if (ObjectHelper.isEmpty(containerPartitionKeyPath)) {
            throw new IllegalArgumentException("containerPartitionKeyPath cannot be empty to create a new container!");
        }

        // containerPartitionKeyPath it needs to start with /
        if (!containerPartitionKeyPath.startsWith("/")) {
            containerPartitionKeyPath = "/" + containerPartitionKeyPath;
        }

        return applyToDatabase(database -> database.createContainerIfNotExists(containerId, containerPartitionKeyPath,
                containerThroughputProperties));
    }

    public Mono<CosmosContainerResponse> deleteContainer() {
        return applyToContainer(container -> container.delete(containerRequestOptions));
    }

    public Mono<ThroughputResponse> replaceContainerThroughput() {
        return applyToContainer(container -> container.replaceThroughput(containerThroughputProperties));
    }

    public Mono<CosmosItemResponse<Object>> createItem(final Object item, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        return applyToContainer(container -> container.createItem(item, partitionKey, itemRequestOptions));
    }

    public Mono<CosmosItemResponse<Object>> upsertItem(final Object item, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        return applyToContainer(container -> container.upsertItem(item, partitionKey, itemRequestOptions));
    }

    public Mono<CosmosItemResponse<Object>> deleteItem(final String itemId, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        return applyToContainer(container -> container.deleteItem(itemId, partitionKey, itemRequestOptions));
    }

    public Mono<CosmosItemResponse<Object>> replaceItem(final Object item, final String itemId, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        return applyToContainer(container -> container.replaceItem(item, itemId, partitionKey, itemRequestOptions));
    }

    public <T> Mono<CosmosItemResponse<T>> readItem(final String itemId, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions, final Class<T> itemType) {
        return applyToContainer(container -> container.readItem(itemId, partitionKey, itemRequestOptions, itemType));
    }

    public <T> Flux<T> readAllItems(final PartitionKey partitionKey, final CosmosQueryRequestOptions queryRequestOptions, final Class<T> itemType, final Integer maxResults) {
        final CosmosQueryRequestOptions requestOptions = queryRequestOptions == null ? new CosmosQueryRequestOptions() : queryRequestOptions;
        return getContainerAndCreateContainerIfNotExist()
                .flatMapMany(container -> convertCosmosPagedFluxToFluxResults(container.readAllItems(partitionKey, requestOptions, itemType), maxResults));
    }

    private <T> Flux<T> convertCosmosPagedFluxToFluxResults(final CosmosPagedFlux<T> pagedFlux, final Integer maxResults) {
        return pagedFlux.byPage(maxResults).flatMap(tFeedResponse -> {
            IterableStream<T> elements = tFeedResponse.getElements();
            if (elements == null) {
                return Flux.empty();
            }
            return Flux.fromIterable(elements);
        });
    }

    private Mono<CosmosAsyncContainer> getContainerAndCreateContainerIfNotExist() {
        validateContainerName();

        if (createContainerIfNotExist) {
            return createContainer()
                    .then(database)
                    .map(database -> database.getContainer(containerId));
        }

        return database
                .map(database -> database.getContainer(containerId));
    }

    private void validateContainerName() {
        if (ObjectHelper.isEmpty(containerId)) {
            throw new IllegalArgumentException("Container ID cannot be empty!");
        }
    }

    private <T> Mono<T> applyToDatabase(final Function<CosmosAsyncDatabase, Mono<T>> fn) {
        return database.flatMap(fn);
    }

    private <T> Mono<T> applyToContainer(final Function<CosmosAsyncContainer, Mono<T>> fn) {
        return getContainerAndCreateContainerIfNotExist().flatMap(fn);
    }
}
