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
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.*;
import org.apache.camel.component.azure.cosmosdb.CosmosDbUtils;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CosmosDbDatabaseOperations {

    public static final String PARAM_CONTAINER_ID = "containerId";
    public static final String PARAM_CONTAINER_PARTITION_KEY_PATH = "containerPartitionKeyPath";
    public static final String PARAM_QUERY = "query";
    private final Mono<CosmosAsyncDatabase> database;

    public CosmosDbDatabaseOperations(final Mono<CosmosAsyncDatabase> database) {
        this.database = database;
    }

    // Database operations
    public Mono<String> getDatabaseId() {
        return database.map(CosmosAsyncDatabase::getId);
    }

    public Mono<CosmosDatabaseResponse> deleteDatabase(final CosmosDatabaseRequestOptions databaseRequestOptions) {
        return applyToDatabase(database -> database.delete(databaseRequestOptions));
    }

    public Mono<CosmosContainerResponse> createContainer(
            final String containerId, final String containerPartitionKeyPath, final ThroughputProperties throughputProperties,
            final IndexingPolicy indexingPolicy) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(containerId, PARAM_CONTAINER_ID);
        CosmosDbUtils.validateIfParameterIsNotEmpty(containerPartitionKeyPath, PARAM_CONTAINER_PARTITION_KEY_PATH);

        // containerPartitionKeyPath it needs to start with /
        final String enhancedContainerPartitionKeyPath;
        if (!containerPartitionKeyPath.startsWith("/")) {
            enhancedContainerPartitionKeyPath = "/" + containerPartitionKeyPath;
        } else {
            enhancedContainerPartitionKeyPath = containerPartitionKeyPath;
        }

        if (ObjectHelper.isNotEmpty(indexingPolicy)) {
            CosmosContainerProperties cosmosProp
                    = new CosmosContainerProperties(containerId, enhancedContainerPartitionKeyPath);
            cosmosProp.setIndexingPolicy(indexingPolicy);

            return applyToDatabase(database -> database.createContainerIfNotExists(cosmosProp,
                    throughputProperties));
        } else {
            return applyToDatabase(database -> database.createContainerIfNotExists(containerId, containerPartitionKeyPath,
                    throughputProperties));
        }
    }

    public CosmosDbContainerOperations createContainerIfNotExistAndGetContainerOperations(
            final String containerId, final String containerPartitionKeyPath, final ThroughputProperties throughputProperties,
            final IndexingPolicy indexingPolicy) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(containerId, PARAM_CONTAINER_ID);
        CosmosDbUtils.validateIfParameterIsNotEmpty(containerPartitionKeyPath, PARAM_CONTAINER_PARTITION_KEY_PATH);

        return new CosmosDbContainerOperations(
                getAndCreateContainerIfNotExist(containerId, containerPartitionKeyPath, true, throughputProperties,
                        indexingPolicy));
    }

    public CosmosDbContainerOperations getContainerOperations(final String containerId) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(containerId, PARAM_CONTAINER_ID);

        return new CosmosDbContainerOperations(getAndCreateContainerIfNotExist(containerId, null, false, null, null));
    }

    public Mono<ThroughputResponse> replaceDatabaseThroughput(final ThroughputProperties throughputProperties) {
        return applyToDatabase(database -> database.replaceThroughput(throughputProperties));
    }

    public Flux<CosmosContainerProperties> readAllContainers(
            final CosmosQueryRequestOptions queryRequestOptions) {
        return database
                .flatMapMany(database -> CosmosDbUtils
                        .convertCosmosPagedFluxToFluxResults(database.readAllContainers(queryRequestOptions)));
    }

    public Flux<CosmosContainerProperties> queryContainers(
            final String query, final CosmosQueryRequestOptions queryRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(query, PARAM_QUERY);

        return database
                .flatMapMany(database -> CosmosDbUtils
                        .convertCosmosPagedFluxToFluxResults(database.queryContainers(query, queryRequestOptions)));
    }

    private Mono<CosmosAsyncContainer> getAndCreateContainerIfNotExist(
            final String containerId, final String containerPartitionKeyPath, final boolean createContainerIfNotExist,
            final ThroughputProperties throughputProperties, final IndexingPolicy indexingPolicy) {
        if (createContainerIfNotExist) {
            return createContainer(containerId, containerPartitionKeyPath, throughputProperties, indexingPolicy)
                    .then(database)
                    .map(database -> getContainer(database, containerId));
        }

        return database
                .map(database -> getContainer(database, containerId));
    }

    private CosmosAsyncContainer getContainer(final CosmosAsyncDatabase database, final String containerId) {
        return database.getContainer(containerId);
    }

    private <T> Mono<T> applyToDatabase(final Function<CosmosAsyncDatabase, Mono<T>> fn) {
        return database.flatMap(fn);
    }
}
