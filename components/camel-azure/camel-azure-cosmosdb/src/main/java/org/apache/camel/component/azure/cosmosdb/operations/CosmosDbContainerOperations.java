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

import java.util.function.Function;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosContainerRequestOptions;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.models.ThroughputResponse;
import org.apache.camel.component.azure.cosmosdb.CosmosDbUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CosmosDbContainerOperations {

    private final Mono<CosmosAsyncContainer> container;

    // visible for testing
    public CosmosDbContainerOperations(final Mono<CosmosAsyncContainer> container) {
        this.container = container;
    }

    // operations on the container
    public Mono<CosmosContainerResponse> deleteContainer(final CosmosContainerRequestOptions containerRequestOptions) {
        return applyToContainer(container -> container.delete(containerRequestOptions));
    }

    public Mono<ThroughputResponse> replaceContainerThroughput(final ThroughputProperties throughputProperties) {
        return applyToContainer(container -> container.replaceThroughput(throughputProperties));
    }

    // operations on the item
    public Mono<CosmosItemResponse<Object>> createItem(
            final Object item, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(item, "item");
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, "partitionKey");

        return applyToContainer(container -> container.createItem(item, partitionKey, itemRequestOptions));
    }

    public Mono<CosmosItemResponse<Object>> upsertItem(
            final Object item, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(item, "item");
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, "partitionKey");

        return applyToContainer(container -> container.upsertItem(item, partitionKey, itemRequestOptions));
    }

    public Mono<CosmosItemResponse<Object>> deleteItem(
            final String itemId, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemId, "itemId");
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, "partitionKey");

        return applyToContainer(container -> container.deleteItem(itemId, partitionKey, itemRequestOptions));
    }

    public Mono<CosmosItemResponse<Object>> replaceItem(
            final Object item, final String itemId, final PartitionKey partitionKey,
            final CosmosItemRequestOptions itemRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(item, "item");
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemId, "itemId");
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, "partitionKey");

        return applyToContainer(container -> container.replaceItem(item, itemId, partitionKey, itemRequestOptions));
    }

    public <T> Mono<CosmosItemResponse<T>> readItem(
            final String itemId, final PartitionKey partitionKey, final CosmosItemRequestOptions itemRequestOptions,
            final Class<T> itemType) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemId, "itemId");
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, "partitionKey");
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemType, "itemType");

        return applyToContainer(container -> container.readItem(itemId, partitionKey, itemRequestOptions, itemType));
    }

    public <T> Flux<T> readAllItems(
            final PartitionKey partitionKey, final CosmosQueryRequestOptions queryRequestOptions, final Class<T> itemType,
            final Integer maxResults) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(partitionKey, "partitionKey");
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemType, "itemType");

        // a bug in Azure SDK, see: https://github.com/Azure/azure-sdk-for-java/issues/20743
        final CosmosQueryRequestOptions requestOptions
                = queryRequestOptions == null ? new CosmosQueryRequestOptions() : queryRequestOptions;
        return container
                .flatMapMany(container -> CosmosDbUtils.convertCosmosPagedFluxToFluxResults(
                        container.readAllItems(partitionKey, requestOptions, itemType), maxResults));
    }

    public <T> Flux<T> queryItems(
            final String query, final CosmosQueryRequestOptions queryRequestOptions, final Class<T> itemType,
            final Integer maxResults) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(query, "query");
        CosmosDbUtils.validateIfParameterIsNotEmpty(itemType, "itemType");

        return container
                .flatMapMany(container -> CosmosDbUtils.convertCosmosPagedFluxToFluxResults(
                        container.queryItems(query, queryRequestOptions, itemType), maxResults));
    }

    private <T> Mono<T> applyToContainer(final Function<CosmosAsyncContainer, Mono<T>> fn) {
        return container.flatMap(fn);
    }
}
