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
package org.apache.camel.component.opensearch.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.opensearch.OpensearchConstants;
import org.apache.camel.util.ObjectHelper;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch._types.WaitForActiveShards;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.DeleteRequest;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.MgetRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.BulkOperationVariant;
import org.opensearch.client.opensearch.core.bulk.CreateOperation;
import org.opensearch.client.opensearch.core.bulk.DeleteOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.opensearch.client.opensearch.core.bulk.UpdateOperation;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(generateLoader = true)
public final class OpensearchActionRequestConverter {

    private static final Logger LOG = LoggerFactory.getLogger(OpensearchActionRequestConverter.class);

    private static final String OPENSEARCH_QUERY_DSL_PREFIX = "query";
    private static final String OPENSEARCH_UPDATE_DOC_PREFIX = "doc";

    private OpensearchActionRequestConverter() {
    }

    // Index requests
    private static IndexOperation.Builder<?> createIndexOperationBuilder(Object document, Exchange exchange)
            throws IOException {
        if (document instanceof IndexOperation.Builder) {
            return (IndexOperation.Builder<?>) document;
        }
        JacksonJsonpMapper mapper = createMapper();
        IndexOperation.Builder<Object> builder = new IndexOperation.Builder<>();
        if (document instanceof byte[] byteArray) {
            builder.document(JsonData.of(mapper.objectMapper().reader().readTree(byteArray), mapper).toJson());
        } else if (document instanceof InputStream inputStream) {
            builder.document(JsonData.of(mapper.objectMapper().reader().readTree(inputStream), mapper).toJson());
        } else if (document instanceof String string) {
            builder.document(JsonData.of(mapper.objectMapper().reader().readTree(new StringReader(string)), mapper).toJson());
        } else if (document instanceof Reader reader) {
            builder.document(JsonData.of(mapper.objectMapper().reader().readTree(reader), mapper).toJson());
        } else {
            builder.document(document);
        }
        return builder
                .index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class));
    }

    @Converter
    public static IndexRequest.Builder<?> toIndexRequestBuilder(Object document, Exchange exchange) throws IOException {
        if (document instanceof IndexRequest.Builder<?> builder) {
            return builder.id(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_ID, String.class));
        }
        JacksonJsonpMapper mapper = createMapper();
        IndexRequest.Builder<Object> builder = new IndexRequest.Builder<>();
        if (document instanceof byte[] byteArray) {
            builder.document(JsonData.of(mapper.objectMapper().reader().readTree(byteArray), mapper).toJson());
        } else if (document instanceof InputStream inputStream) {
            builder.document(JsonData.of(mapper.objectMapper().reader().readTree(inputStream), mapper).toJson());
        } else if (document instanceof String string) {
            builder.document(JsonData.of(mapper.objectMapper().reader().readTree(new StringReader(string)), mapper).toJson());
        } else if (document instanceof Reader reader) {
            builder.document(JsonData.of(mapper.objectMapper().reader().readTree(reader), mapper).toJson());
        } else {
            builder.document(document);
        }
        return builder
                .waitForActiveShards(
                        new WaitForActiveShards.Builder()
                                .count(exchange.getIn().getHeader(OpensearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS,
                                        Integer.class))
                                .build())
                .id(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_ID, String.class))
                .index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class));
    }

    @Converter
    public static UpdateRequest.Builder<?, ?> toUpdateRequestBuilder(Object document, Exchange exchange) throws IOException {
        if (document instanceof UpdateRequest.Builder<?, ?> builder) {
            return builder.id(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_ID, String.class));
        }
        JacksonJsonpMapper mapper = createMapper();
        UpdateRequest.Builder<?, Object> builder = new UpdateRequest.Builder<>();
        if (document instanceof byte[] byteArray) {
            document = JsonData.of(mapper.objectMapper().reader().readTree(byteArray), mapper).to(JsonNode.class);
        } else if (document instanceof InputStream inputStream) {
            document = JsonData.of(mapper.objectMapper().reader().readTree(inputStream), mapper).to(JsonNode.class);
        } else if (document instanceof String string) {
            document = JsonData.of(mapper.objectMapper().reader().readTree(new StringReader(string)), mapper)
                    .to(JsonNode.class);
        } else if (document instanceof Reader reader) {
            document = JsonData.of(mapper.objectMapper().reader().readTree(reader), mapper).to(JsonNode.class);
        } else if (document instanceof Map<?, ?> map) {
            document = mapper.objectMapper().convertValue(map, JsonNode.class);
        }

        if (document instanceof JsonNode jsonNode) {
            JsonNode parentJsonNode = jsonNode.get(OPENSEARCH_UPDATE_DOC_PREFIX);
            if (parentJsonNode != null) {
                document = parentJsonNode;
            }
            document = JsonData.of(document, mapper).toJson();
        }

        return builder
                .doc(document)
                .waitForActiveShards(
                        new WaitForActiveShards.Builder()
                                .count(exchange.getIn().getHeader(OpensearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS,
                                        Integer.class))
                                .build())
                .index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class))
                .id(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_ID, String.class));
    }

    @Converter
    public static GetRequest.Builder toGetRequestBuilder(Object document, Exchange exchange) {
        if (document instanceof GetRequest.Builder) {
            return (GetRequest.Builder) document;
        }
        if (document instanceof String) {
            return new GetRequest.Builder()
                    .index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class))
                    .id((String) document);
        }
        return null;
    }

    @Converter
    public static DeleteRequest.Builder toDeleteRequestBuilder(Object document, Exchange exchange) {
        if (document instanceof DeleteRequest.Builder) {
            return (DeleteRequest.Builder) document;
        }
        if (document instanceof String) {
            return new DeleteRequest.Builder()
                    .index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class))
                    .id((String) document);
        }
        return null;
    }

    @Converter
    public static DeleteIndexRequest.Builder toDeleteIndexRequestBuilder(Object document, Exchange exchange) {
        if (document instanceof DeleteIndexRequest.Builder) {
            return (DeleteIndexRequest.Builder) document;
        }
        if (document instanceof String) {
            return new DeleteIndexRequest.Builder()
                    .index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class));
        }
        return null;
    }

    @Converter
    public static MgetRequest.Builder toMgetRequestBuilder(Object documents, Exchange exchange) {
        if (documents instanceof MgetRequest.Builder) {
            return (MgetRequest.Builder) documents;
        }
        if (documents instanceof Iterable<?> documentIterable) {
            MgetRequest.Builder builder = new MgetRequest.Builder();
            builder.index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class));
            for (Object document : documentIterable) {
                if (document instanceof String) {
                    builder.ids((String) document);
                } else {
                    LOG.warn(
                            "Cannot convert document id of type {} into a String",
                            document == null ? "null" : document.getClass().getName());
                    return null;
                }
            }
            return builder;
        }
        return null;
    }

    @Converter
    public static SearchRequest.Builder toSearchRequestBuilder(Object queryObject, Exchange exchange) throws IOException {
        String indexName = exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class);

        if (queryObject instanceof SearchRequest.Builder) {
            SearchRequest.Builder builder = (SearchRequest.Builder) queryObject;
            if (builder.build().index().isEmpty()) {
                builder.index(indexName);
            }
            return builder;
        }
        SearchRequest.Builder builder = new SearchRequest.Builder();

        // Only set up the indexName if the message header has the
        // setting

        Integer size = exchange.getIn().getHeader(OpensearchConstants.PARAM_SIZE, Integer.class);
        Integer from = exchange.getIn().getHeader(OpensearchConstants.PARAM_FROM, Integer.class);
        if (ObjectHelper.isNotEmpty(indexName)) {
            builder.index(indexName);
        }

        if (queryObject instanceof Map<?, ?> mapQuery) {
            // Remove 'query' prefix from the query object for backward
            // compatibility with Elasticsearch
            if (mapQuery.containsKey(OPENSEARCH_QUERY_DSL_PREFIX)) {
                mapQuery = (Map<?, ?>) mapQuery.get(OPENSEARCH_QUERY_DSL_PREFIX);
            }
            queryObject = mapQuery;
        } else if (queryObject instanceof String queryString) {
            JacksonJsonpMapper mapper = createMapper();
            JsonNode jsonTextObject = mapper.objectMapper().readValue(queryString, JsonNode.class);
            JsonNode parentJsonNode = jsonTextObject.get(OPENSEARCH_QUERY_DSL_PREFIX);
            if (parentJsonNode != null) {
                queryString = parentJsonNode.toString();
            }
            mapper.objectMapper().reader().readTree(new StringReader(queryString));
            queryObject = JsonData.of(mapper.objectMapper().reader().readTree(new StringReader(queryString)), mapper).toJson();
        } else {
            // Cannot convert the queryObject into SearchRequest
            LOG.warn(
                    "Cannot convert queryObject of type {} into SearchRequest object",
                    queryObject == null ? "null" : queryObject.getClass().getName());
            return null;
        }
        if (size != null) {
            builder.size(size);
        }
        if (from != null) {
            builder.from(from);
        }

        builder.query(JsonData.of(queryObject, createMapper()).to(Query.class));

        return builder;
    }

    @Converter
    public static BulkRequest.Builder toBulkRequestBuilder(Object documents, Exchange exchange) throws IOException {
        if (documents instanceof BulkRequest.Builder) {
            return (BulkRequest.Builder) documents;
        }
        if (documents instanceof Iterable) {
            BulkRequest.Builder builder = new BulkRequest.Builder();
            builder.index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class));
            for (Object document : (List<?>) documents) {
                if (document instanceof BulkOperationVariant) {
                    builder.operations(((BulkOperationVariant) document)._toBulkOperation());
                } else if (document instanceof DeleteOperation.Builder) {
                    builder.operations(
                            new BulkOperation.Builder().delete(((DeleteOperation.Builder) document).build()).build());
                } else if (document instanceof UpdateOperation.Builder) {
                    builder.operations(
                            new BulkOperation.Builder().update(((UpdateOperation.Builder<?>) document).build()).build());
                } else if (document instanceof CreateOperation.Builder) {
                    builder.operations(
                            new BulkOperation.Builder().create(((CreateOperation.Builder<?>) document).build()).build());
                } else {
                    builder.operations(
                            new BulkOperation.Builder().index(createIndexOperationBuilder(document, exchange).build()).build());
                }
            }

            return builder;
        }

        return null;
    }

    private static JacksonJsonpMapper createMapper() {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return new JacksonJsonpMapper(objectMapper);
    }
}
