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
package org.apache.camel.component.es.converter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Map;

import co.elastic.clients.elasticsearch._types.WaitForActiveShards;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.MgetRequest;
import co.elastic.clients.elasticsearch.core.MsearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.CreateOperation;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.es.ElasticsearchConstants;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(generateLoader = true)
public final class ElasticsearchActionRequestConverter {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchActionRequestConverter.class);

    private static final String ES_QUERY_DSL_PREFIX = "query";

    private ElasticsearchActionRequestConverter() {
    }

    // Index requests
    private static IndexOperation.Builder<?> createIndexOperationBuilder(Object document, Exchange exchange)
            throws IOException {
        if (document instanceof IndexOperation.Builder<?> indexOpBuilder) {
            return indexOpBuilder;
        }
        IndexOperation.Builder<Object> builder = new IndexOperation.Builder<>();
        if (document instanceof byte[] byteArray) {
            builder.document(JsonData.from(new ByteArrayInputStream(byteArray)));
        } else if (document instanceof InputStream inputStream) {
            builder.document(JsonData.from(inputStream));
        } else if (document instanceof String string) {
            builder.document(JsonData.from(new StringReader(string)));
        } else if (document instanceof Reader reader) {
            builder.document(JsonData.from(reader));
        } else if (document instanceof Map) {
            ObjectMapper objectMapper = new ObjectMapper();
            builder.document(JsonData.from(new StringReader(objectMapper.writeValueAsString(document))));
        } else {
            builder.document(document);
        }
        return builder
                .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
    }

    @Converter
    public static IndexRequest.Builder<?> toIndexRequestBuilder(Object document, Exchange exchange) throws IOException {
        if (document instanceof IndexRequest.Builder<?> indexReqBuilder) {
            // only override the id when the header is present, otherwise a caller-supplied id would be cleared
            String id = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class);
            if (id != null) {
                indexReqBuilder.id(id);
            }
            return indexReqBuilder;
        }
        if (document instanceof IndexRequest<?> indexRequest) {
            // a built request is sent as it is, as a builder body is: the producer gets a builder body without
            // converting it, so the header rules of the builder above do not apply to it either
            return indexRequest.rebuild();
        }
        IndexRequest.Builder<Object> builder = new IndexRequest.Builder<>();
        if (document instanceof byte[] byteArray) {
            builder.withJson(new ByteArrayInputStream(byteArray));
        } else if (document instanceof InputStream inputStream) {
            builder.withJson(inputStream);
        } else if (document instanceof String string) {
            builder.withJson(new StringReader(string));
        } else if (document instanceof Reader reader) {
            builder.withJson(reader);
        } else if (document instanceof Map) {
            ObjectMapper objectMapper = new ObjectMapper();
            builder.withJson(new StringReader(objectMapper.writeValueAsString(document)));
        } else if (isFile(document)) {
            try (InputStream inputStream = readFile(document, exchange)) {
                builder.withJson(inputStream);
            }
        } else {
            builder.document(document);
        }
        return builder
                .waitForActiveShards(
                        new WaitForActiveShards.Builder()
                                .count(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS,
                                        Integer.class))
                                .build())
                .id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class))
                .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
    }

    @Converter
    public static UpdateRequest.Builder<?, ?> toUpdateRequestBuilder(Object document, Exchange exchange) throws IOException {
        if (document instanceof UpdateRequest.Builder<?, ?> updateReqBuilder) {
            // only override the id when the header is present, otherwise a caller-supplied id would be cleared
            String id = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class);
            if (id != null) {
                updateReqBuilder.id(id);
            }
            return updateReqBuilder;
        }
        if (document instanceof UpdateRequest<?, ?> updateRequest) {
            // a built request is sent as it is, as a builder body is
            return updateRequest.rebuild();
        }
        UpdateRequest.Builder<?, Object> builder = new UpdateRequest.Builder<>();
        Boolean enableDocumentOnlyMode
                = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_DOCUMENT_MODE, Boolean.FALSE, Boolean.class);
        Mode mode = Boolean.TRUE.equals(enableDocumentOnlyMode) ? Mode.DOCUMENT_ONLY : Mode.DEFAULT;
        if (document instanceof byte[] byteArray) {
            mode.addDocToUpdateRequestBuilder(builder, new ByteArrayInputStream(byteArray));
        } else if (document instanceof InputStream inputStream) {
            mode.addDocToUpdateRequestBuilder(builder, inputStream);
        } else if (document instanceof String string) {
            mode.addDocToUpdateRequestBuilder(builder, new StringReader(string));
        } else if (document instanceof Reader reader) {
            mode.addDocToUpdateRequestBuilder(builder, reader);
        } else if (document instanceof Map) {
            ObjectMapper objectMapper = new ObjectMapper();
            mode.addDocToUpdateRequestBuilder(builder, new StringReader(objectMapper.writeValueAsString(document)));
        } else if (isFile(document)) {
            try (InputStream inputStream = readFile(document, exchange)) {
                mode.addDocToUpdateRequestBuilder(builder, inputStream);
            }
        } else {
            builder.doc(document);
        }

        return builder
                .waitForActiveShards(
                        new WaitForActiveShards.Builder()
                                .count(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS,
                                        Integer.class))
                                .build())
                .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
                .id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
    }

    @Converter
    public static GetRequest.Builder toGetRequestBuilder(Object document, Exchange exchange) {
        if (document instanceof GetRequest.Builder getReqBuilder) {
            return getReqBuilder;
        }
        if (document instanceof GetRequest getRequest) {
            // a built request is sent as it is, as a builder body is
            return getRequest.rebuild();
        }
        if (document instanceof String string) {
            return new GetRequest.Builder()
                    .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
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
            return deleteRequest.rebuild();
        }
        if (document instanceof String string) {
            return new DeleteRequest.Builder()
                    .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
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
            return deleteIndexRequest.rebuild();
        }
        if (document instanceof String string) {
            // the index of the header or of the indexName option, else the one named by the body
            String indexName = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class);
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
            return mgetRequest.rebuild();
        }
        if (documents instanceof Iterable<?> documentIterable) {
            MgetRequest.Builder builder = new MgetRequest.Builder();
            builder.index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
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
        String indexName = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class);

        if (queryObject instanceof SearchRequest.Builder searchReqBuilder) {
            if (searchReqBuilder.build().index().isEmpty()) {
                searchReqBuilder.index(indexName);
            }
            return searchReqBuilder;
        }
        if (queryObject instanceof SearchRequest searchRequest) {
            // a built request is sent as it is, as a builder body is
            return searchRequest.rebuild();
        }
        SearchRequest.Builder builder = new SearchRequest.Builder();

        // Only setup the indexName if the message header has the
        // setting

        Integer size = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_SIZE, Integer.class);
        Integer from = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_FROM, Integer.class);
        if (ObjectHelper.isNotEmpty(indexName)) {
            builder.index(indexName);
        }

        String queryText;
        boolean queryAlone;

        if (queryObject instanceof Map<?, ?> mapQuery) {
            ObjectMapper objectMapper = new ObjectMapper();
            queryText = objectMapper.writeValueAsString(mapQuery);
            queryAlone = mapQuery.size() == 1 && !mapQuery.containsKey(ES_QUERY_DSL_PREFIX);
        } else if (queryObject instanceof String queryString) {
            queryText = queryString;
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonTextObject = mapper.readValue(queryText, JsonNode.class);
            queryAlone = jsonTextObject != null && jsonTextObject.isObject() && jsonTextObject.size() == 1
                    && !jsonTextObject.has(ES_QUERY_DSL_PREFIX);
        } else {
            // Cannot convert the queryObject into SearchRequest
            LOG.warn(
                    "Cannot convert queryObject of type {} into SearchRequest object",
                    queryObject == null ? "null" : queryObject.getClass().getName());
            return null;
        }
        if (queryAlone) {
            // a query alone, such as {"match": {...}}: an object with a single member, other than query
            builder.query(new Query.Builder().withJson(new StringReader(queryText)).build());
        } else {
            // the body of a search request: its query, size, from, sort, aggregations, _source and the rest
            builder.withJson(new StringReader(queryText));
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
            return msearchRequest.rebuild();
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
            return bulkRequest.rebuild();
        }
        if (documents instanceof Iterable<?> documentIterable) {
            BulkRequest.Builder builder = new BulkRequest.Builder();
            builder.index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
            for (Object document : documentIterable) {
                if (document instanceof DeleteOperation.Builder deleteOpBuilder) {
                    builder.operations(
                            new BulkOperation.Builder().delete(deleteOpBuilder.build()).build());
                } else if (document instanceof UpdateOperation.Builder<?, ?> updateOpBuilder) {
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

    enum Mode {
        DEFAULT {
            @Override
            protected void addDocToUpdateRequestBuilder(UpdateRequest.Builder<?, Object> builder, InputStream in) {
                builder.withJson(in);
            }

            @Override
            protected void addDocToUpdateRequestBuilder(UpdateRequest.Builder<?, Object> builder, Reader in) {
                builder.withJson(in);
            }

        },
        DOCUMENT_ONLY {
            @Override
            protected void addDocToUpdateRequestBuilder(UpdateRequest.Builder<?, Object> builder, InputStream in) {
                builder.doc(JsonData.from(in));
            }

            @Override
            protected void addDocToUpdateRequestBuilder(UpdateRequest.Builder<?, Object> builder, Reader in) {
                builder.doc(JsonData.from(in));
            }
        };

        protected abstract void addDocToUpdateRequestBuilder(UpdateRequest.Builder<?, Object> builder, InputStream in);

        protected abstract void addDocToUpdateRequestBuilder(UpdateRequest.Builder<?, Object> builder, Reader in);
    }
}
