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

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseProperties;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosResponse;
import com.azure.cosmos.models.ThroughputResponse;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbClientOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbContainerOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbDatabaseOperations;
import org.apache.camel.component.azure.cosmosdb.operations.CosmosDbOperationsBuilder;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class CosmosDbProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosDbProducer.class);

    private CosmosAsyncClientWrapper clientWrapper;
    private CosmosDbConfigurationOptionsProxy configurationOptionsProxy;
    private final Map<CosmosDbOperationsDefinition, BiConsumer<Exchange, AsyncCallback>> operations
            = new EnumMap<>(CosmosDbOperationsDefinition.class);

    {
        bind(CosmosDbOperationsDefinition.listDatabases, listDatabases());
        bind(CosmosDbOperationsDefinition.createDatabase, createDatabase());
        bind(CosmosDbOperationsDefinition.queryDatabases, queryDatabases());
        bind(CosmosDbOperationsDefinition.deleteDatabase, deleteDatabase());
        bind(CosmosDbOperationsDefinition.createContainer, createContainer());
        bind(CosmosDbOperationsDefinition.listContainers, listContainers());
        bind(CosmosDbOperationsDefinition.queryContainers, queryContainers());
        bind(CosmosDbOperationsDefinition.replaceDatabaseThroughput, replaceDatabaseThroughput());
        bind(CosmosDbOperationsDefinition.deleteContainer, deleteContainer());
        bind(CosmosDbOperationsDefinition.replaceContainerThroughput, replaceContainerThroughput());
        bind(CosmosDbOperationsDefinition.createItem, createItem());
        bind(CosmosDbOperationsDefinition.upsertItem, upsertItem());
        bind(CosmosDbOperationsDefinition.deleteItem, deleteItem());
        bind(CosmosDbOperationsDefinition.replaceItem, replaceItem());
        bind(CosmosDbOperationsDefinition.readItem, readItem());
        bind(CosmosDbOperationsDefinition.readAllItems, readAllItems());
        bind(CosmosDbOperationsDefinition.queryItems, queryItems());
    }

    public CosmosDbProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        this.clientWrapper = new CosmosAsyncClientWrapper(getEndpoint().getCosmosAsyncClient());
        this.configurationOptionsProxy = new CosmosDbConfigurationOptionsProxy(getConfiguration());
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        try {
            invokeOperation(configurationOptionsProxy.getOperation(exchange), exchange, callback);
            return false;
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

    private void bind(CosmosDbOperationsDefinition operation, BiConsumer<Exchange, AsyncCallback> fn) {
        operations.put(operation, fn);
    }

    /**
     * Entry method that selects the appropriate CosmosDbOperations operation and executes it
     */
    private void invokeOperation(
            final CosmosDbOperationsDefinition operation, final Exchange exchange, final AsyncCallback callback) {
        final CosmosDbOperationsDefinition operationsToInvoke;

        // we put listDatabases operation as default in case no operation has been selected
        if (ObjectHelper.isEmpty(operation)) {
            operationsToInvoke = CosmosDbOperationsDefinition.listDatabases;
        } else {
            operationsToInvoke = operation;
        }

        final BiConsumer<Exchange, AsyncCallback> fnToInvoke = operations.get(operationsToInvoke);

        if (fnToInvoke != null) {
            fnToInvoke.accept(exchange, callback);
        } else {
            throw new RuntimeCamelException("Operation not supported. Value: " + operationsToInvoke);
        }
    }

    private CosmosDbConfiguration getConfiguration() {
        return getEndpoint().getConfiguration();
    }

    private BiConsumer<Exchange, AsyncCallback> listDatabases() {
        return (exchange, callback) -> {
            final Mono<List<CosmosDatabaseProperties>> operation = CosmosDbClientOperations.withClient(clientWrapper)
                    .readAllDatabases()
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> createDatabase() {
        return (exchange, callback) -> {
            final Mono<CosmosDatabaseResponse> operation = CosmosDbClientOperations.withClient(clientWrapper)
                    .createDatabase(configurationOptionsProxy.getDatabaseName(exchange),
                            configurationOptionsProxy.getThroughputProperties(exchange));

            subscribeToMono(operation, exchange, setCosmosDatabaseResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> queryDatabases() {
        return (exchange, callback) -> {
            final Mono<List<CosmosDatabaseProperties>> operation = CosmosDbClientOperations.withClient(clientWrapper)
                    .queryDatabases(configurationOptionsProxy.getQuery(exchange),
                            configurationOptionsProxy.getQueryRequestOptions(exchange))
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> deleteDatabase() {
        return (exchange, callback) -> {
            final Mono<CosmosDatabaseResponse> operation = CosmosDbClientOperations.withClient(clientWrapper)
                    .getDatabaseOperations(configurationOptionsProxy.getDatabaseName(exchange))
                    .deleteDatabase(configurationOptionsProxy.getCosmosDatabaseRequestOptions(exchange));

            subscribeToMono(operation, exchange, setCosmosDatabaseResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> createContainer() {
        return (exchange, callback) -> {
            final Mono<CosmosContainerResponse> operation = getDatabaseOperations(exchange)
                    .createContainer(configurationOptionsProxy.getContainerName(exchange),
                            configurationOptionsProxy.getContainerPartitionKeyPath(exchange),
                            configurationOptionsProxy.getThroughputProperties(exchange),
                            configurationOptionsProxy.getIndexingPolicy(exchange));

            subscribeToMono(operation, exchange, setCosmosContainerResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> replaceDatabaseThroughput() {
        return (exchange, callback) -> {
            final Mono<ThroughputResponse> operation = getDatabaseOperations(exchange)
                    .replaceDatabaseThroughput(configurationOptionsProxy.getThroughputProperties(exchange));

            subscribeToMono(operation, exchange, setThroughputResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> listContainers() {
        return (exchange, callback) -> {
            final Mono<List<CosmosContainerProperties>> operation = getDatabaseOperations(exchange)
                    .readAllContainers(configurationOptionsProxy.getQueryRequestOptions(exchange))
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> queryContainers() {
        return (exchange, callback) -> {
            final Mono<List<CosmosContainerProperties>> operation = getDatabaseOperations(exchange)
                    .queryContainers(configurationOptionsProxy.getQuery(exchange),
                            configurationOptionsProxy.getQueryRequestOptions(exchange))
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> deleteContainer() {
        return (exchange, callback) -> {
            final Mono<CosmosContainerResponse> operation = CosmosDbClientOperations.withClient(clientWrapper)
                    .getDatabaseOperations(configurationOptionsProxy.getDatabaseName(exchange))
                    .getContainerOperations(configurationOptionsProxy.getContainerName(exchange))
                    .deleteContainer(configurationOptionsProxy.getContainerRequestOptions(exchange));

            subscribeToMono(operation, exchange, setCosmosContainerResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> replaceContainerThroughput() {
        return (exchange, callback) -> {
            final Mono<ThroughputResponse> operation = getContainerOperations(exchange)
                    .replaceContainerThroughput(configurationOptionsProxy.getThroughputProperties(exchange));

            subscribeToMono(operation, exchange, setThroughputResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> createItem() {
        return (exchange, callback) -> {
            final Mono<CosmosItemResponse<Object>> operation = getContainerOperations(exchange)
                    .createItem(configurationOptionsProxy.getItem(exchange),
                            configurationOptionsProxy.getItemPartitionKey(exchange),
                            configurationOptionsProxy.getItemRequestOptions(exchange));

            subscribeToMono(operation, exchange, setCosmosItemResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> upsertItem() {
        return (exchange, callback) -> {
            final Mono<CosmosItemResponse<Object>> operation = getContainerOperations(exchange)
                    .upsertItem(configurationOptionsProxy.getItem(exchange),
                            configurationOptionsProxy.getItemPartitionKey(exchange),
                            configurationOptionsProxy.getItemRequestOptions(exchange));

            subscribeToMono(operation, exchange, setCosmosItemResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> deleteItem() {
        return (exchange, callback) -> {
            final Mono<CosmosItemResponse<Object>> operation = getDatabaseOperations(exchange)
                    .getContainerOperations(configurationOptionsProxy.getContainerName(exchange))
                    .deleteItem(configurationOptionsProxy.getItemId(exchange),
                            configurationOptionsProxy.getItemPartitionKey(exchange),
                            configurationOptionsProxy.getItemRequestOptions(exchange));

            subscribeToMono(operation, exchange, setCosmosItemResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> replaceItem() {
        return (exchange, callback) -> {
            final Mono<CosmosItemResponse<Object>> operation = getContainerOperations(exchange)
                    .replaceItem(configurationOptionsProxy.getItem(exchange),
                            configurationOptionsProxy.getItemId(exchange),
                            configurationOptionsProxy.getItemPartitionKey(exchange),
                            configurationOptionsProxy.getItemRequestOptions(exchange));

            subscribeToMono(operation, exchange, setCosmosItemResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> readItem() {
        return (exchange, callback) -> {
            final Mono<CosmosItemResponse<Object>> operation = getContainerOperations(exchange)
                    .readItem(configurationOptionsProxy.getItemId(exchange),
                            configurationOptionsProxy.getItemPartitionKey(exchange),
                            configurationOptionsProxy.getItemRequestOptions(exchange),
                            Object.class);

            subscribeToMono(operation, exchange, setCosmosItemResponseOnExchange(exchange), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> readAllItems() {
        return (exchange, callback) -> {
            final Mono<List<Object>> operation = getContainerOperations(exchange)
                    .readAllItems(configurationOptionsProxy.getItemPartitionKey(exchange),
                            configurationOptionsProxy.getQueryRequestOptions(exchange),
                            Object.class)
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);
        };
    }

    private BiConsumer<Exchange, AsyncCallback> queryItems() {
        return (exchange, callback) -> {
            final Mono<List<Object>> operation = getContainerOperations(exchange)
                    .queryItems(configurationOptionsProxy.getQuery(exchange),
                            configurationOptionsProxy.getQueryRequestOptions(exchange),
                            Object.class)
                    .collectList();

            subscribeToMono(operation, exchange, results -> setMessageBody(exchange, results), callback);
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

    private CosmosDbContainerOperations getContainerOperations(final Exchange exchange) {
        return CosmosDbOperationsBuilder.withClient(clientWrapper)
                .withDatabaseName(configurationOptionsProxy.getDatabaseName(exchange))
                .withCreateDatabaseIfNotExist(configurationOptionsProxy.isCreateDatabaseIfNotExist(exchange))
                .withThroughputProperties(configurationOptionsProxy.getThroughputProperties(exchange))
                .withContainerName(configurationOptionsProxy.getContainerName(exchange))
                .withContainerPartitionKeyPath(configurationOptionsProxy.getContainerPartitionKeyPath(exchange))
                .withCreateContainerIfNotExist(configurationOptionsProxy.isCreateContainerIfNotExist(exchange))
                .withIndexingPolicy(configurationOptionsProxy.getIndexingPolicy(exchange))
                .buildContainerOperations();
    }

    private CosmosDbDatabaseOperations getDatabaseOperations(final Exchange exchange) {
        return CosmosDbOperationsBuilder.withClient(clientWrapper)
                .withDatabaseName(configurationOptionsProxy.getDatabaseName(exchange))
                .withCreateDatabaseIfNotExist(configurationOptionsProxy.isCreateDatabaseIfNotExist(exchange))
                .withThroughputProperties(configurationOptionsProxy.getThroughputProperties(exchange))
                .buildDatabaseOperations();
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

    private Consumer<ThroughputResponse> setThroughputResponseOnExchange(final Exchange exchange) {
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

    private <T> Consumer<CosmosItemResponse<T>> setCosmosItemResponseOnExchange(final Exchange exchange) {
        return response -> {
            setMessageHeader(exchange, CosmosDbConstants.E_TAG, response.getETag());
            setMessageHeader(exchange, CosmosDbConstants.RESPONSE_HEADERS, response.getResponseHeaders());
            setMessageHeader(exchange, CosmosDbConstants.STATUS_CODE, response.getStatusCode());
            setMessageBody(exchange, response.getItem());
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
