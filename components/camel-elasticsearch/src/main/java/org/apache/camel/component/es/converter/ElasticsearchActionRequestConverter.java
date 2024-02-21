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
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import co.elastic.clients.elasticsearch._types.WaitForActiveShards;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.MgetRequest;
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
        if (document instanceof IndexOperation.Builder) {
            return (IndexOperation.Builder<?>) document;
        }
        IndexOperation.Builder<Object> builder = new IndexOperation.Builder<>();
        if (document instanceof byte[]) {
            builder.document(JsonData.from(new ByteArrayInputStream((byte[]) document)));
        } else if (document instanceof InputStream) {
            builder.document(JsonData.from((InputStream) document));
        } else if (document instanceof String) {
            builder.document(JsonData.from(new StringReader((String) document)));
        } else if (document instanceof Reader) {
            builder.document(JsonData.from((Reader) document));
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
        if (document instanceof IndexRequest.Builder) {
            IndexRequest.Builder<?> builder = (IndexRequest.Builder<?>) document;
            return builder.id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
        }
        IndexRequest.Builder<Object> builder = new IndexRequest.Builder<>();
        if (document instanceof byte[]) {
            builder.withJson(new ByteArrayInputStream((byte[]) document));
        } else if (document instanceof InputStream) {
            builder.withJson((InputStream) document);
        } else if (document instanceof String) {
            builder.withJson(new StringReader((String) document));
        } else if (document instanceof Reader) {
            builder.withJson((Reader) document);
        } else if (document instanceof Map) {
            ObjectMapper objectMapper = new ObjectMapper();
            builder.withJson(new StringReader(objectMapper.writeValueAsString(document)));
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
        if (document instanceof UpdateRequest.Builder) {
            UpdateRequest.Builder<?, ?> builder = (UpdateRequest.Builder<?, ?>) document;
            return builder.id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
        }
        UpdateRequest.Builder<?, Object> builder = new UpdateRequest.Builder<>();
        Boolean enableDocumentOnlyMode
                = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_DOCUMENT_MODE, Boolean.FALSE, Boolean.class);
        Mode mode = enableDocumentOnlyMode == Boolean.TRUE ? Mode.DOCUMENT_ONLY : Mode.DEFAULT;
        if (document instanceof byte[]) {
            mode.addDocToUpdateRequestBuilder(builder, new ByteArrayInputStream((byte[]) document));
        } else if (document instanceof InputStream) {
            mode.addDocToUpdateRequestBuilder(builder, (InputStream) document);
        } else if (document instanceof String) {
            mode.addDocToUpdateRequestBuilder(builder, new StringReader((String) document));
        } else if (document instanceof Reader) {
            mode.addDocToUpdateRequestBuilder(builder, (Reader) document);
        } else if (document instanceof Map) {
            ObjectMapper objectMapper = new ObjectMapper();
            mode.addDocToUpdateRequestBuilder(builder, new StringReader(objectMapper.writeValueAsString(document)));
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
        if (document instanceof GetRequest.Builder) {
            return (GetRequest.Builder) document;
        }
        if (document instanceof String) {
            return new GetRequest.Builder()
                    .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
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
                    .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
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
                    .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
        }
        return null;
    }

    @Converter
    public static MgetRequest.Builder toMgetRequestBuilder(Object documents, Exchange exchange) {
        if (documents instanceof MgetRequest.Builder) {
            return (MgetRequest.Builder) documents;
        }
        if (documents instanceof Iterable) {
            MgetRequest.Builder builder = new MgetRequest.Builder();
            builder.index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
            for (Object document : (List<?>) documents) {
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
        String indexName = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class);

        if (queryObject instanceof SearchRequest.Builder) {
            SearchRequest.Builder builder = (SearchRequest.Builder) queryObject;
            if (builder.build().index().isEmpty()) {
                builder.index(indexName);
            }
            return builder;
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

        if (queryObject instanceof Map<?, ?>) {
            Map<?, ?> mapQuery = (Map<?, ?>) queryObject;
            // Remove 'query' prefix from the query object for backward
            // compatibility
            if (mapQuery.containsKey(ES_QUERY_DSL_PREFIX)) {
                mapQuery = (Map<?, ?>) mapQuery.get(ES_QUERY_DSL_PREFIX);
            }
            ObjectMapper objectMapper = new ObjectMapper();
            queryText = objectMapper.writeValueAsString(mapQuery);
        } else if (queryObject instanceof String) {
            queryText = (String) queryObject;
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonTextObject = mapper.readValue(queryText, JsonNode.class);
            JsonNode parentJsonNode = jsonTextObject.get(ES_QUERY_DSL_PREFIX);
            if (parentJsonNode != null) {
                queryText = parentJsonNode.toString();
            }
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
        builder.query(new Query.Builder().withJson(new StringReader(queryText)).build());

        return builder;
    }

    @Converter
    public static BulkRequest.Builder toBulkRequestBuilder(Object documents, Exchange exchange) throws IOException {
        if (documents instanceof BulkRequest.Builder) {
            return (BulkRequest.Builder) documents;
        }
        if (documents instanceof Iterable) {
            BulkRequest.Builder builder = new BulkRequest.Builder();
            builder.index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
            for (Object document : (List<?>) documents) {
                if (document instanceof DeleteOperation.Builder) {
                    builder.operations(
                            new BulkOperation.Builder().delete(((DeleteOperation.Builder) document).build()).build());
                } else if (document instanceof UpdateOperation.Builder) {
                    builder.operations(
                            new BulkOperation.Builder().update(((UpdateOperation.Builder<?, ?>) document).build()).build());
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
