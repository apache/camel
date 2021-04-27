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
package org.apache.camel.component.azure.cosmosdb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseProperties;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CosmosResponse;
import com.azure.cosmos.models.ThroughputResponse;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbClientOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbDatabaseOperations;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class CosmosDbProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosDbProducer.class);

    private final CosmosAsyncClientWrapper clientWrapper;
    private final CosmosDbConfigurationOptionsProxy configurationOptionsProxy;
    private final Map<CosmosDbOperationsDefinition, BiFunction<Exchange, AsyncCallback, Boolean>> operations = new HashMap<>();

    {
        bind(CosmosDbOperationsDefinition.listDatabases, listDatabases());
        bind(CosmosDbOperationsDefinition.createDatabase, createDatabase());
        bind(CosmosDbOperationsDefinition.queryDatabases, queryDatabases());
        bind(CosmosDbOperationsDefinition.deleteDatabase, deleteDatabase());
        bind(CosmosDbOperationsDefinition.createContainer, createContainer());
        bind(CosmosDbOperationsDefinition.listContainers, listContainers());
        bind(CosmosDbOperationsDefinition.queryContainers, queryContainers());
        bind(CosmosDbOperationsDefinition.replaceDatabaseThroughput, replaceDatabaseThroughput());
    }

    public CosmosDbProducer(final Endpoint endpoint) {
        super(endpoint);
        this.clientWrapper = new CosmosAsyncClientWrapper(getCosmosAsyncClient());
        this.configurationOptionsProxy = new CosmosDbConfigurationOptionsProxy(getConfiguration());
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            return invokeOperation(configurationOptionsProxy.getOperation(exchange), exchange, callback);
        } catch (Exception e) {
            exchange.setException(e);
            callback.done(true);
            return true;
        }
    }

    @Override
    public CosmosDbEndpoint getEndpoint() {
        return (CosmosDbEndpoint) super.getEndpoint();
    }

    private void bind(CosmosDbOperationsDefinition operation, BiFunction<Exchange, AsyncCallback, Boolean> fn) {
        operations.put(operation, fn);
    }

    /**
     * Entry method that selects the appropriate CosmosDbOperations operation and executes it
     */
    private boolean invokeOperation(
            final CosmosDbOperationsDefinition operation, final Exchange exchange, final AsyncCallback callback) {
        final CosmosDbOperationsDefinition operationsToInvoke;

        // we put listDatabases operation as default in case no operation has been selected
        if (ObjectHelper.isEmpty(operation)) {
            operationsToInvoke = CosmosDbOperationsDefinition.listDatabases;
        } else {
            operationsToInvoke = operation;
        }

        final BiFunction<Exchange, AsyncCallback, Boolean> fnToInvoke = operations.get(operationsToInvoke);

        if (fnToInvoke != null) {
            return fnToInvoke.apply(exchange, callback);
        } else {
            throw new RuntimeException("Operation not supported. Value: " + operationsToInvoke);
        }
    }

    private CosmosDbConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    private CosmosAsyncClient getCosmosAsyncClient() {
        return getEndpoint().getCosmosAsyncClient();
    }

    private BiFunction<Exchange, AsyncCallback, Boolean> listDatabases() {
        return (exchange, callback) -> {
            final Mono<List<CosmosDatabaseProperties>> operation = CosmosDbClientOperations.withClient(clientWrapper)
                    .readAllDatabases()
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);

            return false;
        };
    }

    private BiFunction<Exchange, AsyncCallback, Boolean> createDatabase() {
        return (exchange, callback) -> {
            final Mono<CosmosDatabaseResponse> operation = CosmosDbClientOperations.withClient(clientWrapper)
                    .createDatabase(configurationOptionsProxy.getDatabaseName(exchange),
                            configurationOptionsProxy.getThroughputProperties(exchange));

            subscribeToMono(operation, exchange, setCosmosDatabaseResponseOnExchange(exchange), callback);

            return false;
        };
    }

    private BiFunction<Exchange, AsyncCallback, Boolean> queryDatabases() {
        return (exchange, callback) -> {
            final Mono<List<CosmosDatabaseProperties>> operation = CosmosDbClientOperations.withClient(clientWrapper)
                    .queryDatabases(configurationOptionsProxy.getQuery(exchange),
                            configurationOptionsProxy.getQueryRequestOptions(exchange))
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);

            return false;
        };
    }

    private BiFunction<Exchange, AsyncCallback, Boolean> deleteDatabase() {
        return (exchange, callback) -> {
            final Mono<CosmosDatabaseResponse> operation = CosmosDbClientOperations.withClient(clientWrapper)
                    .getDatabaseOperations(configurationOptionsProxy.getDatabaseName(exchange))
                    .deleteDatabase(configurationOptionsProxy.getCosmosDatabaseRequestOptions(exchange));

            subscribeToMono(operation, exchange, setCosmosDatabaseResponseOnExchange(exchange), callback);

            return false;
        };
    }

    private BiFunction<Exchange, AsyncCallback, Boolean> createContainer() {
        return (exchange, callback) -> {
            final Mono<CosmosContainerResponse> operation = getDatabaseOperations(exchange)
                    .createContainer(configurationOptionsProxy.getContainerName(exchange),
                            configurationOptionsProxy.getContainerPartitionKeyPath(exchange),
                            configurationOptionsProxy.getThroughputProperties(exchange));

            subscribeToMono(operation, exchange, setCosmosContainerResponseOnExchange(exchange), callback);

            return false;
        };
    }

    private BiFunction<Exchange, AsyncCallback, Boolean> replaceDatabaseThroughput() {
        return (exchange, callback) -> {
            final Mono<ThroughputResponse> operation = getDatabaseOperations(exchange)
                    .replaceDatabaseThroughput(configurationOptionsProxy.getThroughputProperties(exchange));

            subscribeToMono(operation, exchange, setThroughputResponse(exchange), callback);

            return false;
        };
    }

    private BiFunction<Exchange, AsyncCallback, Boolean> listContainers() {
        return (exchange, callback) -> {
            final Mono<List<CosmosContainerProperties>> operation = getDatabaseOperations(exchange)
                    .readAllContainers(configurationOptionsProxy.getQueryRequestOptions(exchange))
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);

            return false;
        };
    }

    private BiFunction<Exchange, AsyncCallback, Boolean> queryContainers() {
        return (exchange, callback) -> {
            final Mono<List<CosmosContainerProperties>> operation = getDatabaseOperations(exchange)
                    .queryContainers(configurationOptionsProxy.getQuery(exchange),
                            configurationOptionsProxy.getQueryRequestOptions(exchange))
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);

            return false;
        };
    }

    private <T> void subscribeToMono(
            final Mono<T> inputMono, final Exchange exchange, final Consumer<T> resultsCallback, final AsyncCallback callback) {
        inputMono
                .subscribe(resultsCallback, error -> {
                    // error but we continue
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Error processing async exchange with error: {}", error.getMessage());
                    }
                    exchange.setException(error);
                    callback.done(false);
                }, () -> {
                    // we are done from everything, so mark it as sync done
                    LOG.trace("All events with exchange have been sent successfully.");
                    callback.done(false);
                });
    }

    private CosmosDbDatabaseOperations getDatabaseOperations(final Exchange exchange) {
        final boolean createDatabaseIfNotExist = configurationOptionsProxy.isCreateDatabaseIfNotExist(exchange);

        // if we enabled this flag, we create a database first before running the operation
        if (createDatabaseIfNotExist) {
            return CosmosDbClientOperations.withClient(clientWrapper)
                    .createDatabaseIfNotExistAndGetDatabaseOperations(configurationOptionsProxy.getDatabaseName(exchange),
                            configurationOptionsProxy.getThroughputProperties(exchange));
        }

        // otherwise just return the operation without creating a database if it is not existing
        return CosmosDbClientOperations.withClient(clientWrapper)
                .getDatabaseOperations(configurationOptionsProxy.getDatabaseName(exchange));
    }

    private Consumer<CosmosDatabaseResponse> setCosmosDatabaseResponseOnExchange(final Exchange exchange) {
        return response -> {
            if (ObjectHelper.isNotEmpty(response.getProperties())) {
                setMessageHeader(exchange, CosmosDbConstants.RESOURCE_ID, response.getProperties().getResourceId());
                setMessageHeader(exchange, CosmosDbConstants.E_TAG, response.getProperties().getETag());
                setMessageHeader(exchange, CosmosDbConstants.TIMESTAMP, response.getProperties().getTimestamp());
            }
            setCommonResponseOnExchange(exchange, response);
        };
    }

    private Consumer<CosmosContainerResponse> setCosmosContainerResponseOnExchange(final Exchange exchange) {
        return response -> {
            if (ObjectHelper.isNotEmpty(response.getProperties())) {
                setMessageHeader(exchange, CosmosDbConstants.RESOURCE_ID, response.getProperties().getResourceId());
                setMessageHeader(exchange, CosmosDbConstants.E_TAG, response.getProperties().getETag());
                setMessageHeader(exchange, CosmosDbConstants.TIMESTAMP, response.getProperties().getTimestamp());
                setMessageHeader(exchange, CosmosDbConstants.DEFAULT_TIME_TO_LIVE_SECONDS,
                        response.getProperties().getDefaultTimeToLiveInSeconds());
            }
            setCommonResponseOnExchange(exchange, response);
        };
    }

    private Consumer<ThroughputResponse> setThroughputResponse(final Exchange exchange) {
        return response -> {
            if (ObjectHelper.isNotEmpty(response.getProperties())) {
                setMessageHeader(exchange, CosmosDbConstants.AUTOSCALE_MAX_THROUGHPUT,
                        response.getProperties().getAutoscaleMaxThroughput());
                setMessageHeader(exchange, CosmosDbConstants.MANUAL_THROUGHPUT, response.getProperties().getManualThroughput());
                setMessageHeader(exchange, CosmosDbConstants.E_TAG, response.getProperties().getETag());
                setMessageHeader(exchange, CosmosDbConstants.TIMESTAMP, response.getProperties().getTimestamp());
            }
            setCommonResponseOnExchange(exchange, response);
        };
    }

    private <T> void setCommonResponseOnExchange(final Exchange exchange, final CosmosResponse<T> response) {
        setMessageHeader(exchange, CosmosDbConstants.RESPONSE_HEADERS, response.getResponseHeaders());
        setMessageHeader(exchange, CosmosDbConstants.STATUS_CODE, response.getStatusCode());
    }

    private void setMessageBody(final Exchange exchange, final Object body) {
        exchange.getMessage().setBody(body);
    }

    private void setMessageHeader(final Exchange exchange, final String headerKey, final Object headerValue) {
        exchange.getMessage().setHeader(headerKey, headerValue);
    }
}
