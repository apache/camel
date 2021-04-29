package org.apache.camel.component.azure.cosmosdb.operations;

import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;

public final class CosmosDbOperationsBuilder {

    private CosmosAsyncClientWrapper clientWrapper;
    private String databaseName;
    private boolean createDatabaseIfNotExist;
    private String containerName;
    private String containerPartitionKeyPath;
    private boolean createContainerIfNotExist;
    private ThroughputProperties throughputProperties;

    public static CosmosDbOperationsBuilder withClient(final CosmosAsyncClientWrapper clientWrapper) {
        return new CosmosDbOperationsBuilder(clientWrapper);
    }

    private CosmosDbOperationsBuilder(CosmosAsyncClientWrapper clientWrapper) {
        this.clientWrapper = clientWrapper;
    }

    public CosmosDbOperationsBuilder withDatabaseName(String databaseName) {
        this.databaseName = databaseName;
        return this;
    }

    public CosmosDbOperationsBuilder withCreateDatabaseIfNotExist(boolean createDatabaseIfNotExist) {
        this.createDatabaseIfNotExist = createDatabaseIfNotExist;
        return this;
    }

    public CosmosDbOperationsBuilder withContainerName(String containerName) {
        this.containerName = containerName;
        return this;
    }

    public CosmosDbOperationsBuilder withContainerPartitionKeyPath(String containerPartitionKeyPath) {
        this.containerPartitionKeyPath = containerPartitionKeyPath;
        return this;
    }

    public CosmosDbOperationsBuilder withCreateContainerIfNotExist(boolean createContainerIfNotExist) {
        this.createContainerIfNotExist = createContainerIfNotExist;
        return this;
    }

    public CosmosDbOperationsBuilder withThroughputProperties(ThroughputProperties throughputProperties) {
        this.throughputProperties = throughputProperties;
        return this;
    }

    public CosmosDbDatabaseOperations buildDatabaseOperations() {
        // if we enabled this flag, we create a database first before running the operation
        if (createDatabaseIfNotExist) {
            return CosmosDbClientOperations.withClient(clientWrapper)
                    .createDatabaseIfNotExistAndGetDatabaseOperations(databaseName, throughputProperties);
        }

        // otherwise just return the operation without creating a database if it is not existing
        return CosmosDbClientOperations.withClient(clientWrapper)
                .getDatabaseOperations(databaseName);
    }

    public CosmosDbContainerOperations buildContainerOperations() {
        // if we enabled this flag, we create a container first before running the operation
        if (createContainerIfNotExist) {
            return buildDatabaseOperations()
                    .createContainerIfNotExistAndGetContainerOperations(containerName,
                            containerPartitionKeyPath,
                            throughputProperties);
        }

        // otherwise just return the operation without creating a container if it is not existing
        return buildDatabaseOperations()
                .getContainerOperations(containerName);
    }
}
