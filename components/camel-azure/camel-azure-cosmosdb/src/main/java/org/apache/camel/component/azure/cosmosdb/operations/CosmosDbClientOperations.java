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

import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosDatabaseProperties;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.component.azure.cosmosdb.CosmosDbUtils;
import org.apache.camel.component.azure.cosmosdb.client.CosmosAsyncClientWrapper;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class CosmosDbClientOperations {

    public static final String PARAM_DATABASE_NAME = "databaseName";
    private final CosmosAsyncClientWrapper client;

    private CosmosDbClientOperations(final CosmosAsyncClientWrapper client) {
        this.client = client;
    }

    public static CosmosDbClientOperations withClient(final CosmosAsyncClientWrapper client) {
        return new CosmosDbClientOperations(client);
    }

    public Mono<CosmosDatabaseResponse> createDatabase(
            final String databaseName, final ThroughputProperties throughputProperties) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(databaseName, PARAM_DATABASE_NAME);

        return client.createDatabaseIfNotExists(databaseName, throughputProperties);
    }

    public CosmosDbDatabaseOperations createDatabaseIfNotExistAndGetDatabaseOperations(
            final String databaseName, final ThroughputProperties throughputProperties) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(databaseName, PARAM_DATABASE_NAME);

        return new CosmosDbDatabaseOperations(getAndCreateDatabaseIfNotExist(databaseName, true, throughputProperties));
    }

    public CosmosDbDatabaseOperations getDatabaseOperations(final String databaseName) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(databaseName, PARAM_DATABASE_NAME);

        return new CosmosDbDatabaseOperations(getAndCreateDatabaseIfNotExist(databaseName, false, null));
    }

    public Flux<CosmosDatabaseProperties> readAllDatabases() {
        return CosmosDbUtils.convertCosmosPagedFluxToFluxResults(client.readAllDatabases());
    }

    public Flux<CosmosDatabaseProperties> queryDatabases(
            final String query, final CosmosQueryRequestOptions queryRequestOptions) {
        CosmosDbUtils.validateIfParameterIsNotEmpty(query, "query");

        return CosmosDbUtils.convertCosmosPagedFluxToFluxResults(client.queryDatabases(query, queryRequestOptions));
    }

    private Mono<CosmosAsyncDatabase> getAndCreateDatabaseIfNotExist(
            final String databaseName, final boolean createDatabaseIfNotExist,
            final ThroughputProperties throughputProperties) {
        if (createDatabaseIfNotExist) {
            return createDatabase(databaseName, throughputProperties)
                    .map(response -> getDatabase(databaseName));
        }

        return Mono.just(getDatabase(databaseName));
    }

    private CosmosAsyncDatabase getDatabase(final String databaseName) {
        return client.getDatabase(databaseName);
    }
}
