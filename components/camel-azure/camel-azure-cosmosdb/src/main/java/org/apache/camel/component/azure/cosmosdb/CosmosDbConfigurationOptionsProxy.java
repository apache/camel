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

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.azure.cosmos.models.CosmosContainerRequestOptions;
import com.azure.cosmos.models.CosmosDatabaseRequestOptions;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.ThroughputProperties;
import org.apache.camel.Exchange;
import org.apache.camel.util.ObjectHelper;

/**
 * A proxy class for {@link CosmosDbConfiguration} and {@link CosmosDbConstants}. Ideally this is responsible to obtain
 * the correct configurations options either from configs or exchange headers
 */
public class CosmosDbConfigurationOptionsProxy {

    private final CosmosDbConfiguration configuration;

    public CosmosDbConfigurationOptionsProxy(final CosmosDbConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getDatabaseName(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.DATABASE_NAME, configuration::getDatabaseName, String.class);
    }

    public ThroughputProperties getThroughputProperties(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.THROUGHPUT_PROPERTIES, configuration::getThroughputProperties,
                ThroughputProperties.class);
    }

    public CosmosDatabaseRequestOptions getCosmosDatabaseRequestOptions(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.DATABASE_REQUEST_OPTIONS, nullFallback(),
                CosmosDatabaseRequestOptions.class);
    }

    public CosmosQueryRequestOptions getQueryRequestOptions(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.QUERY_REQUEST_OPTIONS, configuration::getQueryRequestOptions,
                CosmosQueryRequestOptions.class);
    }

    public boolean isCreateDatabaseIfNotExist(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.CREATE_DATABASE_IF_NOT_EXIST, configuration::isCreateDatabaseIfNotExists,
                boolean.class);
    }

    public boolean isCreateContainerIfNotExist(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.CREATE_CONTAINER_IF_NOT_EXIST, configuration::isCreateContainerIfNotExists,
                boolean.class);
    }

    public CosmosDbOperationsDefinition getOperation(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.OPERATION, configuration::getOperation,
                CosmosDbOperationsDefinition.class);
    }

    public String getQuery(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.QUERY, configuration::getQuery, String.class);
    }

    public String getContainerName(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.CONTAINER_NAME, configuration::getContainerName, String.class);
    }

    public String getContainerPartitionKeyPath(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.CONTAINER_PARTITION_KEY_PATH, configuration::getContainerPartitionKeyPath,
                String.class);
    }

    public CosmosContainerRequestOptions getContainerRequestOptions(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.CONTAINER_REQUEST_OPTIONS, nullFallback(),
                CosmosContainerRequestOptions.class);
    }

    public PartitionKey getItemPartitionKey(final Exchange exchange) {
        return new PartitionKey(
                getOption(exchange, CosmosDbConstants.ITEM_PARTITION_KEY, configuration::getItemPartitionKey,
                        String.class));
    }

    public Object getItem(final Exchange exchange) {
        return exchange.getIn().getBody();
    }

    public List<Object> getItems(final Exchange exchange) {
        final Object body = exchange.getIn().getBody();

        if (ObjectHelper.isEmpty(body)) {
            throw new IllegalArgumentException("Item on the message body cannot be empty.");
        }

        List<Object> items = null;
        if (body instanceof List) {
            // noinspection unchecked
            items = (List<Object>) body;
        } else {
            items = Collections.singletonList(body);
        }

        return items;
    }

    public String getItemId(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.ITEM_ID, configuration::getItemId, String.class);
    }

    public CosmosItemRequestOptions getItemRequestOptions(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.ITEM_REQUEST_OPTIONS, nullFallback(), CosmosItemRequestOptions.class);
    }

    public CosmosDbConfiguration getConfiguration() {
        return configuration;
    }

    public <R> Supplier<R> nullFallback() {
        return () -> null;
    }

    private <R> R getOption(
            final Exchange exchange, final String headerName, final Supplier<R> fallbackFn, final Class<R> type) {
        // we first try to look if our value in exchange otherwise fallback to fallbackFn which could be either a function or constant
        return ObjectHelper.isEmpty(exchange) || ObjectHelper.isEmpty(getObjectFromHeaders(exchange, headerName, type))
                ? fallbackFn.get()
                : getObjectFromHeaders(exchange, headerName, type);
    }

    private static <T> T getObjectFromHeaders(final Exchange exchange, final String headerName, final Class<T> classType) {
        return exchange.getIn().getHeader(headerName, classType);
    }
}
