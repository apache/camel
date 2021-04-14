package org.apache.camel.component.azure.cosmosdb.operations;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosContainerRequestOptions;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.ThroughputResponse;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Mono;

public class CosmosDbContainerOperationsBuilder extends CosmosDbOperationsBuilder<CosmosDbContainerOperationsBuilder> {

    // properties
    private String containerId;
    private String containerPartitionKeyPath;
    private ThroughputProperties containerThroughputProperties;
    private CosmosContainerRequestOptions containerRequestOptions;
    private boolean createContainerIfNotExist;

    protected CosmosDbContainerOperationsBuilder(CosmosAsyncClientWrapper client) {
        super(client);
    }

    public static CosmosDbContainerOperationsBuilder withClient(final CosmosAsyncClientWrapper client) {
        return new CosmosDbContainerOperationsBuilder(client);
    }

    // properties DSL
    public CosmosDbOperationsBuilder containerId(String containerId) {
        this.containerId = containerId;
        return this;
    }

    public CosmosDbOperationsBuilder containerPartitionKeyPath(String containerPartitionKeyPath) {
        this.containerPartitionKeyPath = containerPartitionKeyPath;
        return this;
    }

    public CosmosDbOperationsBuilder containerThroughputProperties(ThroughputProperties containerThroughputProperties) {
        this.containerThroughputProperties = containerThroughputProperties;
        return this;
    }

    public CosmosDbOperationsBuilder createContainerIfNotExist() {
        this.createContainerIfNotExist = true;
        return this;
    }

    public CosmosDbOperationsBuilder containerRequestOptions(CosmosContainerRequestOptions containerRequestOptions) {
        this.containerRequestOptions = containerRequestOptions;
        return this;
    }

    // operations
    public Mono<CosmosContainerResponse> createContainer() {
        validateContainerProperties();

        return getAndCreateDatabaseIfNotExist()
                .flatMap(response -> response.createContainerIfNotExists(containerId, containerPartitionKeyPath, containerThroughputProperties));
    }

    public Mono<CosmosContainerResponse> deleteContainer() {
        return getContainer().delete(containerRequestOptions);
    }

    public Mono<ThroughputResponse> replaceContainerThroughput() {
        return getContainer().replaceThroughput(containerThroughputProperties);
    }

    protected CosmosAsyncContainer getContainer() {
        validateContainerProperties();

        return getDatabase().getContainer(containerId);
    }

    protected Mono<CosmosAsyncContainer> getAndCreateContainerIfNotExist() {
        if (createContainerIfNotExist) {
            return getAndCreateDatabaseIfNotExist()
                    .map(response -> response.getContainer(containerId));
        }

        return Mono.just(getContainer());
    }

    private void validateContainerProperties() {
        if (ObjectHelper.isEmpty(containerId)) {
            throw new IllegalArgumentException("Container ID cannot be empty!");
        }
    }
}
