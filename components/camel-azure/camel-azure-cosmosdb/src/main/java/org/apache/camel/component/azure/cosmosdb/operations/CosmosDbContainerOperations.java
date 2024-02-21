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
package org.apache.camel.component.azure.cosmosdb.operations;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.azure.cosmos.ChangeFeedProcessor;
import com.azure.cosmos.ChangeFeedProcessorBuilder;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.implementation.Utils;
import com.azure.cosmos.models.ChangeFeedProcessorOptions;
import com.azure.cosmos.models.CosmosContainerRequestOptions;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.ThroughputResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.azure.cosmosdb.CosmosDbUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CosmosDbContainerOperations {

    public static final String PARAM_PARTITION_KEY = "partitionKey";
    public static final String PARAM_ITEM_ID = "itemId";
    public static final String PARAM_ITEM = "item";
    public static final String PARAM_ITEM_TYPE = "itemType";
    public static final String PARAM_QUERY = "query";
    public static final String PARAM_LEASE_CONTAINER = "leaseContainer";
    public static final String PARAM_RESULTS_CALLBACK = "resultsCallback";
    public static final String PARAM_HOST_NAME = "hostName";
    public static final String PARAM_ITEMS = "items";
    private final Mono<CosmosAsyncContainer> container;

    // visible for testing
    public CosmosDbContainerOperations(final Mono<CosmosAsyncContainer> container) {
        this.container = container;
    }

    // operations on the container
    public Mono<CosmosAsyncContainer> getContainer() {
        return container;
    }

    public Mono<String> getContainerId() {
        return container.map(CosmosAsyncContainer::getId);
    }

    public Mono<CosmosContainerResponse> deleteContainer(final CosmosContainerRequestOptions containerRequestOptions) {
        return applyToContainer(container -> container.delete(containerRequestOptions));
    }

    public Mono<ThroughputResponse> replaceContainerThroughput(final ThroughputProperties throughputProperties) {
        return applyToContainer(container -> container.replaceThroughput(throughputProperties));
    }

    // operations on the item
    public <T> Mono<CosmosItemResponse<T>> createItem(
            final T item, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(item, PARAM_ITEM);
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, PARAM_PARTITION_KEY);

        return applyToContainer(container -> container.createItem(item, partitionKey, itemRequestOptions));
    }

    public <T> Flux<CosmosItemResponse<T>> createItems(
            final List<T> items, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(items, PARAM_ITEMS);

        return Flux.fromIterable(items)
                .flatMap(item -> createItem(item, partitionKey, itemRequestOptions));
    }

    public <T> Mono<CosmosItemResponse<T>> upsertItem(
            final T item, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(item, PARAM_ITEM);
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, PARAM_PARTITION_KEY);

        return applyToContainer(container -> container.upsertItem(item, partitionKey, itemRequestOptions));
    }

    public <T> Flux<CosmosItemResponse<T>> upsertItems(
            final List<T> items, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(items, PARAM_ITEMS);

        return Flux.fromIterable(items)
                .flatMap(item -> upsertItem(item, partitionKey, itemRequestOptions));
    }

    public Mono<CosmosItemResponse<Object>> deleteItem(
            final String itemId, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemId, PARAM_ITEM_ID);
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, PARAM_PARTITION_KEY);

        return applyToContainer(container -> container.deleteItem(itemId, partitionKey, itemRequestOptions));
    }

    public <T> Mono<CosmosItemResponse<T>> replaceItem(
            final T item, final String itemId, final PartitionKey partitionKey,
            final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(item, PARAM_ITEM);
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemId, PARAM_ITEM_ID);
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, PARAM_PARTITION_KEY);

        return applyToContainer(container -> container.replaceItem(item, itemId, partitionKey, itemRequestOptions));
    }

    public <T> Mono<CosmosItemResponse<T>> readItem(
            final String itemId, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions,
            final Class<T> itemType) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemId, PARAM_ITEM_ID);
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, PARAM_PARTITION_KEY);
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemType, PARAM_ITEM_TYPE);

        return applyToContainer(container -> container.readItem(itemId, partitionKey, itemRequestOptions, itemType));
    }

    public <T> Flux<T> readAllItems(
            final PartitionKey partitionKey, final CosmosQueryRequestOptions queryRequestOptions, final Class<T> itemType) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, PARAM_PARTITION_KEY);
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemType, PARAM_ITEM_TYPE);

        // a bug in Azure SDK, see: https://github.com/Azure/azure-sdk-for-java/issues/20743
        final CosmosQueryRequestOptions requestOptions
                = queryRequestOptions == null ? new CosmosQueryRequestOptions() : queryRequestOptions;
        return container
                .flatMapMany(container -> CosmosDbUtils.convertCosmosPagedFluxToFluxResults(
                        container.readAllItems(partitionKey, requestOptions, itemType)));
    }

    public <T> Flux<T> queryItems(
            final String query, final CosmosQueryRequestOptions queryRequestOptions, final Class<T> itemType) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(query, PARAM_QUERY);
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemType, PARAM_ITEM_TYPE);

        return container
                .flatMapMany(container -> CosmosDbUtils.convertCosmosPagedFluxToFluxResults(
                        container.queryItems(query, queryRequestOptions, itemType)));
    }

    public <T> Flux<FeedResponse<T>> queryItemsAsFeed(
            final String query, final CosmosQueryRequestOptions queryRequestOptions, final Class<T> itemType) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(query, PARAM_QUERY);
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemType, PARAM_ITEM_TYPE);

        return container
                .flatMapMany(container -> container.queryItems(query, queryRequestOptions, itemType).byPage());
    }

    public ChangeFeedProcessor captureEventsWithChangeFeed(
            final Mono<CosmosAsyncContainer> leaseContainerMono, final String hostName,
            final Consumer<List<Map<String, ?>>> resultsCallback, final ChangeFeedProcessorOptions changeFeedProcessorOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(leaseContainerMono, PARAM_LEASE_CONTAINER);
        CosmosDbUtils.validateIfParameterIsNotEmpty(resultsCallback, PARAM_RESULTS_CALLBACK);
        CosmosDbUtils.validateIfParameterIsNotEmpty(hostName, PARAM_HOST_NAME);

        final ObjectMapper mapper = Utils.getSimpleObjectMapper();

        return container.zipWith(leaseContainerMono)
                .map(tupleResults -> {
                    final CosmosAsyncContainer feedContainer = tupleResults.getT1();
                    final CosmosAsyncContainer leaseContainer = tupleResults.getT2();

                    return new ChangeFeedProcessorBuilder()
                            .feedContainer(feedContainer)
                            .leaseContainer(leaseContainer)
                            .handleChanges(jsonNodes -> {
                                final List<Map<String, ?>> events = jsonNodes.stream()
                                        .map(jsonNode -> mapper.convertValue(jsonNode,
                                                new TypeReference<Map<String, Object>>() {
                                                }))
                                        .collect(Collectors.toList());

                                // feed our callback
                                resultsCallback.accept(events);
                            })
                            .hostName(hostName)
                            .options(changeFeedProcessorOptions)
                            .buildChangeFeedProcessor();
                })
                // we will just block this instance here since we will return only a mono
                .block();
    }

    private <T> Mono<T> applyToContainer(final Function<CosmosAsyncContainer, Mono<T>> fn) {
        return container.flatMap(fn);
    }
}
