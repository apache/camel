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
package org.apache.camel.component.azure.cosmosdb.client;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosDatabaseProperties;
import com.azure.cosmos.models.CosmosDatabaseRequestOptions;
import com.azure.cosmos.models.CosmosDatabaseResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.SqlQuerySpec;
import com.azure.cosmos.models.ThroughputProperties;
import com.azure.cosmos.util.CosmosPagedFlux;
import org.apache.camel.util.ObjectHelper;
import reactor.core.publisher.Mono;

public class CosmosAsyncClientWrapper {

    private final CosmosAsyncClient client;

    public CosmosAsyncClientWrapper(final CosmosAsyncClient client) {
        ObjectHelper.isNotEmpty(client);

        this.client = client;
    }

    public Mono<CosmosDatabaseResponse> createDatabaseIfNotExists(
            CosmosDatabaseProperties databaseProperties) {
        return client.createDatabaseIfNotExists(databaseProperties);
    }

    public Mono<CosmosDatabaseResponse> createDatabaseIfNotExists(String id) {
        return client.createDatabaseIfNotExists(id);
    }

    public Mono<CosmosDatabaseResponse> createDatabaseIfNotExists(
            String id,
            ThroughputProperties throughputProperties) {
        return client.createDatabaseIfNotExists(id, throughputProperties);
    }

    public Mono<CosmosDatabaseResponse> createDatabase(
            CosmosDatabaseProperties databaseProperties,
            CosmosDatabaseRequestOptions options) {
        return client.createDatabase(databaseProperties, options);
    }

    public Mono<CosmosDatabaseResponse> createDatabase(CosmosDatabaseProperties databaseProperties) {
        return client.createDatabase(databaseProperties);
    }

    public Mono<CosmosDatabaseResponse> createDatabase(String id) {
        return client.createDatabase(id);
    }

    public Mono<CosmosDatabaseResponse> createDatabase(
            CosmosDatabaseProperties databaseProperties,
            ThroughputProperties throughputProperties,
            CosmosDatabaseRequestOptions options) {
        return client.createDatabase(databaseProperties, throughputProperties, options);
    }

    public Mono<CosmosDatabaseResponse> createDatabase(
            CosmosDatabaseProperties databaseProperties,
            ThroughputProperties throughputProperties) {
        return client.createDatabase(databaseProperties, throughputProperties);
    }

    public Mono<CosmosDatabaseResponse> createDatabase(
            String id,
            ThroughputProperties throughputProperties) {
        return client.createDatabase(id, throughputProperties);
    }

    public CosmosPagedFlux<CosmosDatabaseProperties> readAllDatabases() {
        return client.readAllDatabases();
    }

    public CosmosPagedFlux<CosmosDatabaseProperties> queryDatabases(
            String query,
            CosmosQueryRequestOptions options) {
        return client.queryDatabases(query, options);
    }

    public CosmosPagedFlux<CosmosDatabaseProperties> queryDatabases(
            SqlQuerySpec querySpec,
            CosmosQueryRequestOptions options) {
        return client.queryDatabases(querySpec, options);
    }

    public CosmosAsyncDatabase getDatabase(String id) {
        return client.getDatabase(id);
    }

    public void close() {
        client.close();
    }
}
