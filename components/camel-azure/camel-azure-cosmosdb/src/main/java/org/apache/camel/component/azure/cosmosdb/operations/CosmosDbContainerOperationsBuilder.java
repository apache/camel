package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.function.Function;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosContainerRequestOptions;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.ThroughputResponse;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Mono;

public class CosmosDbContainerOperationsBuilder {

    private final Mono<CosmosAsyncDatabase> database;

    // properties
    private String containerId;
    private String containerPartitionKeyPath;
    private ThroughputProperties containerThroughputProperties;
    private CosmosContainerRequestOptions containerRequestOptions;
    private boolean createContainerIfNotExist;

    // visible for testing
    public CosmosDbContainerOperationsBuilder(final Mono<CosmosAsyncDatabase> database) {
        this.database = database;
    }

    // properties DSL
    public CosmosDbContainerOperationsBuilder withContainerId(String containerId) {
        this.containerId = containerId;
        return this;
    }

    public CosmosDbContainerOperationsBuilder withContainerPartitionKeyPath(String containerPartitionKeyPath) {
        this.containerPartitionKeyPath = containerPartitionKeyPath;
        return this;
    }

    public CosmosDbContainerOperationsBuilder withContainerThroughputProperties(ThroughputProperties containerThroughputProperties) {
        this.containerThroughputProperties = containerThroughputProperties;
        return this;
    }

    public CosmosDbContainerOperationsBuilder withCreateContainerIfNotExist(final boolean createContainerIfNotExist) {
        this.createContainerIfNotExist = createContainerIfNotExist;
        return this;
    }

    public CosmosDbContainerOperationsBuilder withContainerRequestOptions(CosmosContainerRequestOptions containerRequestOptions) {
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
        validateContainerName();
        return getContainerAndCreateContainerIfNotExist().flatMap(fn);
    }
}
