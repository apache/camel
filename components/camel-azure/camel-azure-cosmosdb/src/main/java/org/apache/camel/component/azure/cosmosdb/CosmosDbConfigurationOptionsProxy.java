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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.azure.cosmos.models.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.util.ObjectHelper;

/**
 * A proxy class for {@link CosmosDbConfiguration} and {@link CosmosDbConstants}. Ideally this is responsible to obtain
 * the correct configurations options either from configs or exchange headers
 */
public class CosmosDbConfigurationOptionsProxy {

    // parses a JSON body into the item to write; JSON with more than one value (such as JSON lines) fails
    private static final ObjectMapper JSON_MAPPER
            = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final TypeReference<Map<String, Object>> ITEM_TYPE = new TypeReference<>() {
    };

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

    public IndexingPolicy getIndexingPolicy(final Exchange exchange) {
        return getOption(exchange, CosmosDbConstants.INDEXING_POLICY, configuration::getIndexingPolicy,
                IndexingPolicy.class);
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

    /**
     * The item that createItem, upsertItem and replaceItem write. A Map or a POJO is returned as it is: the SDK
     * serializes it with Jackson. JSON as text, bytes, a stream or a file is parsed into a Map: the SDK would write a
     * String (or a file, as its path) as a JSON string and cannot serialize a stream. These operations write one item,
     * so a List of items, or JSON that is not an object, fails.
     */
    public Object getItem(final Exchange exchange) {
        final Object body = exchange.getIn().getBody();
        if (ObjectHelper.isEmpty(body)) {
            // the operation fails as the item is empty
            return body;
        }
        if (body instanceof Collection || body.getClass().isArray() && !(body instanceof byte[])) {
            throw new IllegalArgumentException(
                    "The body of the " + getOperation(exchange) + " operation must be one item, but it is a "
                                               + body.getClass().getTypeName()
                                               + ": send each item in its own exchange, for example with the Split EIP");
        }
        if (isJson(body)) {
            return parseItem(exchange, body);
        }
        return body;
    }

    /**
     * Whether the body is a payload, which holds the JSON of the item: text, bytes, a stream or a file.
     */
    private static boolean isJson(final Object body) {
        return body instanceof String || body instanceof byte[]
                || body instanceof InputStream || body instanceof Reader || body instanceof StreamCache
                || body instanceof File || body instanceof Path || body instanceof WrappedFile;
    }

    private Map<String, Object> parseItem(final Exchange exchange, final Object body) {
        final JsonNode json;
        try {
            json = readJson(exchange, body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "The body of the " + getOperation(exchange)
                                               + " operation is not valid JSON, or holds more than one JSON value",
                    e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (json == null || json.isMissingNode()) {
            // the operation fails as the item is empty
            return null;
        }
        if (json.isArray()) {
            throw new IllegalArgumentException(
                    "The body of the " + getOperation(exchange) + " operation must be one item, but it is a JSON array:"
                                               + " send each item in its own exchange, for example with the Split EIP");
        }
        if (!json.isObject()) {
            throw new IllegalArgumentException(
                    "The body of the " + getOperation(exchange) + " operation must be a JSON object, but it is a JSON "
                                               + json.getNodeType().name().toLowerCase(Locale.ROOT));
        }
        return JSON_MAPPER.convertValue(json, ITEM_TYPE);
    }

    private static JsonNode readJson(final Exchange exchange, final Object body) throws IOException {
        if (body instanceof String text) {
            return JSON_MAPPER.readTree(text);
        }
        if (body instanceof byte[] bytes) {
            return JSON_MAPPER.readTree(bytes);
        }
        if (body instanceof Reader reader) {
            return JSON_MAPPER.readTree(reader);
        }
        // a stream or a file: its content, as bytes, from which Jackson detects the encoding of the JSON
        final byte[] content = exchange.getIn().getBody(byte[].class);
        if (content == null) {
            throw new IllegalArgumentException(
                    "Cannot read the body of type " + body.getClass().getName() + " as the JSON of the item");
        }
        return JSON_MAPPER.readTree(content);
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
