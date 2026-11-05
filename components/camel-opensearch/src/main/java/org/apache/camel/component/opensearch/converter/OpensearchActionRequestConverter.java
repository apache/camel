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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
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
import org.opensearch.client.opensearch.core.MsearchRequest;
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
        if (document instanceof IndexOperation.Builder<?> indexOpBuilder) {
            return indexOpBuilder;
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
            // only override the id when the header is present, otherwise a caller-supplied id would be cleared
            String id = exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_ID, String.class);
            if (id != null) {
                builder.id(id);
            }
            return builder;
        }
        if (document instanceof IndexRequest<?> indexRequest) {
            // a built request is sent as it is, as a builder body is: the producer gets a builder body without
            // converting it, so the header rules of the builder above do not apply to it either
            return indexRequest.toBuilder();
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
        } else if (isFile(document)) {
            try (InputStream inputStream = readFile(document, exchange)) {
                builder.document(JsonData.of(mapper.objectMapper().reader().readTree(inputStream), mapper).toJson());
            }
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
            // only override the id when the header is present, otherwise a caller-supplied id would be cleared
            String id = exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_ID, String.class);
            if (id != null) {
                builder.id(id);
            }
            return builder;
        }
        if (document instanceof UpdateRequest<?, ?> updateRequest) {
            // a built request is sent as it is, as a builder body is
            return updateRequest.toBuilder();
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
        } else if (isFile(document)) {
            try (InputStream inputStream = readFile(document, exchange)) {
                document = JsonData.of(mapper.objectMapper().reader().readTree(inputStream), mapper).to(JsonNode.class);
            }
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
        if (document instanceof GetRequest.Builder getReqBuilder) {
            return getReqBuilder;
        }
        if (document instanceof GetRequest getRequest) {
            // a built request is sent as it is, as a builder body is
            return getRequest.toBuilder();
        }
        if (document instanceof String string) {
            return new GetRequest.Builder()
                    .index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class))
                    .id(string);
        }
        return null;
    }

    @Converter
    public static DeleteRequest.Builder toDeleteRequestBuilder(Object document, Exchange exchange) {
        if (document instanceof DeleteRequest.Builder deleteReqBuilder) {
            return deleteReqBuilder;
        }
        if (document instanceof DeleteRequest deleteRequest) {
            // a built request is sent as it is, as a builder body is
            return deleteRequest.toBuilder();
        }
        if (document instanceof String string) {
            return new DeleteRequest.Builder()
                    .index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class))
                    .id(string);
        }
        return null;
    }

    @Converter
    public static DeleteIndexRequest.Builder toDeleteIndexRequestBuilder(Object document, Exchange exchange) {
        if (document instanceof DeleteIndexRequest.Builder deleteIdxReqBuilder) {
            return deleteIdxReqBuilder;
        }
        if (document instanceof DeleteIndexRequest deleteIndexRequest) {
            // a built request is sent as it is, as a builder body is
            return deleteIndexRequest.toBuilder();
        }
        if (document instanceof String string) {
            // the index of the header or of the indexName option, else the one named by the body
            String indexName = exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class);
            if (ObjectHelper.isEmpty(indexName)) {
                indexName = string;
            }
            DeleteIndexRequest.Builder builder = new DeleteIndexRequest.Builder();
            if (ObjectHelper.isNotEmpty(indexName)) {
                builder.index(indexName);
            }
            // with no name at all, the request fails when it is built, as the index is required
            return builder;
        }
        return null;
    }

    @Converter
    public static MgetRequest.Builder toMgetRequestBuilder(Object documents, Exchange exchange) {
        if (documents instanceof MgetRequest.Builder mgetReqBuilder) {
            return mgetReqBuilder;
        }
        if (documents instanceof MgetRequest mgetRequest) {
            // a built request is sent as it is, as a builder body is
            return mgetRequest.toBuilder();
        }
        if (documents instanceof Iterable<?> documentIterable) {
            MgetRequest.Builder builder = new MgetRequest.Builder();
            builder.index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class));
            for (Object document : documentIterable) {
                if (document instanceof String string) {
                    builder.ids(string);
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

        if (queryObject instanceof SearchRequest.Builder searchReqBuilder) {
            if (searchReqBuilder.build().index().isEmpty()) {
                searchReqBuilder.index(indexName);
            }
            return searchReqBuilder;
        }
        if (queryObject instanceof SearchRequest searchRequest) {
            // a built request is sent as it is, as a builder body is
            return searchRequest.toBuilder();
        }

        Integer size = exchange.getIn().getHeader(OpensearchConstants.PARAM_SIZE, Integer.class);
        Integer from = exchange.getIn().getHeader(OpensearchConstants.PARAM_FROM, Integer.class);

        JacksonJsonpMapper mapper = createMapper();
        JsonNode json;
        if (queryObject instanceof Map<?, ?> mapQuery) {
            json = mapper.objectMapper().valueToTree(mapQuery);
        } else if (queryObject instanceof String queryString) {
            json = mapper.objectMapper().readValue(queryString, JsonNode.class);
        } else {
            // Cannot convert the queryObject into SearchRequest
            LOG.warn(
                    "Cannot convert queryObject of type {} into SearchRequest object",
                    queryObject == null ? "null" : queryObject.getClass().getName());
            return null;
        }

        SearchRequest.Builder builder;
        if (json != null && json.isObject() && json.size() == 1 && !json.has(OPENSEARCH_QUERY_DSL_PREFIX)) {
            // a query alone, such as {"match": {...}}: an object with a single member, other than query
            builder = new SearchRequest.Builder().query(JsonData.of(json, mapper).to(Query.class));
        } else {
            // the body of a search request: its query, size, from, sort, aggregations, _source and the rest
            builder = JsonData.of(json, mapper).to(SearchRequest.class).toBuilder();
        }

        // Only set up the indexName if the message header has the
        // setting
        if (ObjectHelper.isNotEmpty(indexName)) {
            builder.index(indexName);
        }
        // the size and from of the headers or of the options, when set, override those of the body
        if (size != null) {
            builder.size(size);
        }
        if (from != null) {
            builder.from(from);
        }

        return builder;
    }

    @Converter
    public static MsearchRequest.Builder toMsearchRequestBuilder(Object queryObject, Exchange exchange) {
        if (queryObject instanceof MsearchRequest.Builder msearchReqBuilder) {
            return msearchReqBuilder;
        }
        if (queryObject instanceof MsearchRequest msearchRequest) {
            // a built request is sent as it is, as a builder body is
            return msearchRequest.toBuilder();
        }
        return null;
    }

    @Converter
    public static BulkRequest.Builder toBulkRequestBuilder(Object documents, Exchange exchange) throws IOException {
        if (documents instanceof BulkRequest.Builder bulkReqBuilder) {
            return bulkReqBuilder;
        }
        if (documents instanceof BulkRequest bulkRequest) {
            // a built request is sent as it is, as a builder body is
            return bulkRequest.toBuilder();
        }
        if (documents instanceof Iterable<?> documentIterable) {
            BulkRequest.Builder builder = new BulkRequest.Builder();
            builder.index(exchange.getIn().getHeader(OpensearchConstants.PARAM_INDEX_NAME, String.class));
            for (Object document : documentIterable) {
                if (document instanceof BulkOperationVariant bulkOpVariant) {
                    builder.operations(bulkOpVariant._toBulkOperation());
                } else if (document instanceof DeleteOperation.Builder deleteOpBuilder) {
                    builder.operations(
                            new BulkOperation.Builder().delete(deleteOpBuilder.build()).build());
                } else if (document instanceof UpdateOperation.Builder<?> updateOpBuilder) {
                    builder.operations(
                            new BulkOperation.Builder().update(updateOpBuilder.build()).build());
                } else if (document instanceof CreateOperation.Builder<?> createOpBuilder) {
                    builder.operations(
                            new BulkOperation.Builder().create(createOpBuilder.build()).build());
                } else {
                    builder.operations(
                            new BulkOperation.Builder().index(createIndexOperationBuilder(document, exchange).build()).build());
                }
            }

            return builder;
        }

        return null;
    }

    /**
     * Whether the document is a file, a java.io.File, a Path or the file of the file component, whose content is the
     * JSON of the document.
     */
    private static boolean isFile(Object document) {
        return document instanceof File || document instanceof Path || document instanceof WrappedFile;
    }

    /**
     * Opens the content of the file, to read as JSON.
     */
    private static InputStream readFile(Object file, Exchange exchange) {
        InputStream inputStream = exchange.getContext().getTypeConverter().convertTo(InputStream.class, exchange, file);
        if (inputStream == null) {
            throw new IllegalArgumentException(
                    "Cannot read the file of type " + file.getClass().getName() + " as the JSON of the document");
        }
        return inputStream;
    }

    private static JacksonJsonpMapper createMapper() {
        ObjectMapper objectMapper = new ObjectMapper()
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);

        return new JacksonJsonpMapper(objectMapper);
    }
}
