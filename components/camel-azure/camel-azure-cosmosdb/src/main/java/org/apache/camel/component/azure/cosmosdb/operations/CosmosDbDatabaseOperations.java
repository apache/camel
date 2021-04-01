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

public class CosmosDbDatabaseOperations {

    private static final Logger LOG = LoggerFactory.getLogger(CosmosDbDatabaseOperations.class);

    private final CosmosAsyncClientWrapper client;
    private final CosmosDbConfigurationOptionsProxy configurationOptionsProxy;

    public CosmosDbDatabaseOperations(CosmosAsyncClientWrapper client,
                                      CosmosDbConfigurationOptionsProxy configurationOptionsProxy) {
        this.client = client;
        this.configurationOptionsProxy = configurationOptionsProxy;
    }

    public boolean createDatabase(
            final Exchange exchange, final Consumer<CosmosDatabaseResponse> resultCallback,
            final Consumer<Throwable> errorCallback, final AsyncCallback callback) {
        ObjectHelper.notNull(exchange, "exchange cannot be null");
        ObjectHelper.notNull(resultCallback, "resultCallback cannot be null");
        ObjectHelper.notNull(errorCallback, "errorCallback cannot be null");
        ObjectHelper.notNull(callback, "callback cannot be null");

        createDatabaseAsync(configurationOptionsProxy.getDatabaseName(exchange),
                configurationOptionsProxy.getThroughputProperties(exchange),
                configurationOptionsProxy.getCosmosDatabaseRequestOptions(exchange),
                resultCallback,
                errorCallback,
                completionHandler(callback));

        return false;
    }

    private void createDatabaseAsync(
            final String databaseName,
            final ThroughputProperties throughputProperties,
            final CosmosDatabaseRequestOptions options,
            final Consumer<CosmosDatabaseResponse> resultCallback,
            final Consumer<Throwable> errorCallback,
            final Runnable completedConsumer) {
        client.createDatabase(new CosmosDatabaseProperties(databaseName), throughputProperties, options)
                .subscribe(resultCallback, errorCallback, completedConsumer);
    }

    private Runnable completionHandler(final AsyncCallback callback) {
        return () -> {
            // we are done from everything, so mark it as sync done
            LOG.trace("All events with exchange have been sent successfully.");
            callback.done(false);
        };
    }
}
