package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.function.Consumer;

import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.cosmosdb.CosmosDbConfigurationOptionsProxy;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class CosmosDbContainerOperations {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosDbContainerOperations.class);

    private final CosmosDbConfigurationOptionsProxy configurationOptionsProxy;
    private final CosmosAsyncClientWrapper client;

    public CosmosDbContainerOperations(CosmosDbConfigurationOptionsProxy configurationOptionsProxy,
                                       CosmosAsyncClientWrapper client) {
        this.configurationOptionsProxy = configurationOptionsProxy;
        this.client = client;
    }

    public boolean createContainer(final Exchange exchange, final Consumer<CosmosDatabaseResponse> resultCallback,
                                   final Consumer<Throwable> errorCallback, final AsyncCallback callback) {
        ObjectHelper.notNull(resultCallback, "resultCallback cannot be null");
        ObjectHelper.notNull(errorCallback, "errorCallback cannot be null");
        ObjectHelper.notNull(callback, "callback cannot be null");


        return false;
    }

    private void createContainerAsync(
            final String databaseName,
            final ThroughputProperties throughputProperties,
            final boolean createDatabaseIfNotExist,
            final boolean createContainerIfNotExist,
            final Consumer<CosmosDatabaseResponse> resultCallback,
            final Consumer<Throwable> errorCallback,
            final Runnable completedConsumer) {

    }
}
