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

import java.util.function.Consumer;

import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.models.CosmosContainerProperties;
import com.azure.cosmos.models.CosmosContainerRequestOptions;
import com.azure.cosmos.models.CosmosContainerResponse;
import com.azure.cosmos.models.CosmosDatabaseProperties;
import com.azure.cosmos.models.CosmosDatabaseRequestOptions;
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

public class CosmosDbDatabaseOperations {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosDbDatabaseOperations.class);

    private final CosmosAsyncClientWrapper client;
    private final CosmosDbConfigurationOptionsProxy configurationOptionsProxy;

    public CosmosDbDatabaseOperations(CosmosDbConfigurationOptionsProxy configurationOptionsProxy,
                                      CosmosAsyncClientWrapper client) {
        this.client = client;
        this.configurationOptionsProxy = configurationOptionsProxy;
    }

    public boolean createDatabase(
            final Exchange exchange, final Consumer<CosmosDatabaseResponse> resultCallback,
            final Consumer<Throwable> errorCallback, final AsyncCallback callback) {
        ObjectHelper.notNull(resultCallback, "resultCallback cannot be null");
        ObjectHelper.notNull(errorCallback, "errorCallback cannot be null");
        ObjectHelper.notNull(callback, "callback cannot be null");

        convertMonoToCallback(createDatabaseAsync(configurationOptionsProxy.getDatabaseName(exchange),
                configurationOptionsProxy.getThroughputProperties(exchange)), resultCallback, errorCallback, callback);

        return false;
    }

    public boolean deleteDatabase(
            final Exchange exchange, final Consumer<CosmosDatabaseResponse> resultCallback,
            final Consumer<Throwable> errorCallback,
            final AsyncCallback callback) {
        ObjectHelper.notNull(resultCallback, "resultCallback cannot be null");
        ObjectHelper.notNull(errorCallback, "errorCallback cannot be null");
        ObjectHelper.notNull(callback, "callback cannot be null");

        convertMonoToCallback(deleteDatabaseAsync(configurationOptionsProxy.getDatabaseName(exchange),
                configurationOptionsProxy.getCosmosDatabaseRequestOptions(exchange)), resultCallback, errorCallback, callback);

        return false;
    }

    private Mono<CosmosDatabaseResponse> createDatabaseAsync(
            final String databaseName,
            final ThroughputProperties throughputProperties) {
        return client.createDatabaseIfNotExists(databaseName, throughputProperties);
    }

    private Mono<CosmosDatabaseResponse> deleteDatabaseAsync(
            final String databaseName,
            final CosmosDatabaseRequestOptions options) {
        return client.getDatabase(databaseName).delete(options);
    }

    private Mono<CosmosContainerResponse> createContainerAsync(
            final String databaseName,
            final String id,
            final String partitionKeyPath,
            final ThroughputProperties throughputProperties,
            final boolean createDatabaseIfNotExist) {
        return getDatabaseAsync(databaseName, throughputProperties, createDatabaseIfNotExist)
                .createContainerIfNotExists(id, partitionKeyPath, throughputProperties);
    }

    private Mono<CosmosContainerResponse> deleteContainerAsync(
            final String databaseName,
            final String id,
            final CosmosContainerRequestOptions options) {
        return client.getDatabase(databaseName)
                .getContainer(id)
                .delete(options);
    }

    private CosmosAsyncDatabase getDatabaseAsync(
            final String databaseName,
            final ThroughputProperties throughputProperties,
            final boolean createDatabaseIfNotExist) {
        if (createDatabaseIfNotExist) {
            return createDatabaseAsync(databaseName, throughputProperties)
                    .map(response -> client.getDatabase(databaseName))
                    // since client.getDatabase doesn't do any service call, we just return the async instance directly
                    .block();
        }

        return client.getDatabase(databaseName);
    }

    private CosmosAsyncContainer getContainerAsync(
            final String databaseName,
            final String id,
            final String partitionKeyPath,
            final ThroughputProperties throughputProperties,
            final boolean createDatabaseIfNotExist,
            final boolean createContainerIfNotExist) {
        if (createContainerIfNotExist) {
            return createContainerAsync(databaseName, id, partitionKeyPath, throughputProperties, createDatabaseIfNotExist)
                    .map(response -> client.getDatabase(databaseName).getContainer(id))
                    // since client.getContainer doesn't do any service call, we just return the async instance directly
                    .block();
        }

        return client.getDatabase(databaseName).getContainer(id);
    }

    private <T> void convertMonoToCallback(final Mono<T> inputMono, final Consumer<T> resultCallback, final Consumer<Throwable> errorCallback,
                                           final AsyncCallback callback) {
        inputMono.subscribe(resultCallback, errorCallback, completionHandler(callback));
    }

    private Runnable completionHandler(final AsyncCallback callback) {
        return () -> {
            // we are done from everything, so mark it as sync done
            LOG.trace("All events with exchange have been sent successfully.");
            callback.done(false);
        };
    }
}
