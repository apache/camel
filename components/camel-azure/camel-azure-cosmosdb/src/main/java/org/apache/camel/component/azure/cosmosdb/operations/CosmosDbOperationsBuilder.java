package org.apache.camel.component.azure.cosmosdb.operations;

import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosDatabaseRequestOptions;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Mono;

public class CosmosDbOperationsBuilder <T extends CosmosDbOperationsBuilder<T>> {

    private final CosmosAsyncClientWrapper client;

    // properties
    private String databaseName;
    private ThroughputProperties databaseThroughputProperties;
    private CosmosDatabaseRequestOptions databaseRequestOptions;
    private boolean createDatabaseIfNotExist;

    protected CosmosDbOperationsBuilder(final CosmosAsyncClientWrapper client) {
        this.client = client;
    }

    public static CosmosDbOperationsBuilder<?> withClient(final CosmosAsyncClientWrapper client) {
        return new CosmosDbOperationsBuilder<>(client);
    }

    // properties DSL
    public T databaseName(final String databaseName) {
        this.databaseName = databaseName;
        return self();
    }

    public T databaseThroughputProperties(final ThroughputProperties throughputProperties) {
        this.databaseThroughputProperties = throughputProperties;
        return self();
    }

    public T createDatabaseIfNotExist() {
        this.createDatabaseIfNotExist = true;
        return self();
    }

    public T databaseRequestOptions(CosmosDatabaseRequestOptions databaseRequestOptions) {
        this.databaseRequestOptions = databaseRequestOptions;
        return self();
    }

    // Database operations
    public Mono<CosmosDatabaseResponse> createDatabase() {
        validateDatabaseProperties();

        return client.createDatabaseIfNotExists(databaseName, databaseThroughputProperties);
    }

    public Mono<CosmosDatabaseResponse> deleteDatabase() {
        return getDatabase().delete(databaseRequestOptions);
    }

    public Mono<CosmosAsyncDatabase> getAndCreateDatabaseIfNotExist() {
        if (createDatabaseIfNotExist) {
            return createDatabase()
                    .map(response -> getDatabase());
        }

        return Mono.just(getDatabase());
    }

    public CosmosAsyncDatabase getDatabase() {
        validateDatabaseProperties();

        return client.getDatabase(databaseName);
    }

    private void validateDatabaseProperties() {
        if (ObjectHelper.isEmpty(databaseName)) {
            throw new IllegalArgumentException("Database name cannot be empty!");
        }
    }

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }
}
