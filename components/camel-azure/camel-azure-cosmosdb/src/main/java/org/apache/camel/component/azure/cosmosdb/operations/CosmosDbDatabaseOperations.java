package org.apache.camel.component.azure.cosmosdb.operations;

import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosDatabaseRequestOptions;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Mono;

public class CosmosDbDatabaseOperations {

    private final CosmosAsyncClientWrapper client;

    // properties
    private String databaseName;
    private ThroughputProperties databaseThroughputProperties;
    private CosmosDatabaseRequestOptions databaseRequestOptions;
    private boolean createDatabaseIfNotExist;

    private CosmosDbDatabaseOperations(final CosmosAsyncClientWrapper client) {
        this.client = client;
    }

    public static CosmosDbDatabaseOperations withClient(final CosmosAsyncClientWrapper client) {
        return new CosmosDbDatabaseOperations(client);
    }

    // properties DSL
    public CosmosDbDatabaseOperations withDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public CosmosDbDatabaseOperations withDatabaseThroughputProperties(final ThroughputProperties throughputProperties) {
        this.databaseThroughputProperties = throughputProperties;
        return this;
    }

    public CosmosDbDatabaseOperations withCreateDatabaseIfNotExist(final boolean createDatabaseIfNotExist) {
        this.createDatabaseIfNotExist = createDatabaseIfNotExist;
        return this;
    }

    public CosmosDbDatabaseOperations withDatabaseRequestOptions(CosmosDatabaseRequestOptions databaseRequestOptions) {
        this.databaseRequestOptions = databaseRequestOptions;
        return this;
    }

    // Database operations
    public Mono<CosmosDatabaseResponse> createDatabase() {
        validateDatabaseName();

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

    // container operations
    public CosmosDbContainerOperations getContainerOperationBuilder() {
        return new CosmosDbContainerOperations(getAndCreateDatabaseIfNotExist());
    }

    private CosmosAsyncDatabase getDatabase() {
        validateDatabaseName();

        return client.getDatabase(databaseName);
    }

    private void validateDatabaseName() {
        if (ObjectHelper.isEmpty(databaseName)) {
            throw new IllegalArgumentException("Database name cannot be empty!");
        }
    }
}
