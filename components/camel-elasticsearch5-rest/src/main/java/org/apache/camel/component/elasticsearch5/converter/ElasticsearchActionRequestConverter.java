/**
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
package org.apache.camel.component.elasticsearch5.converter;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.elasticsearch5.ElasticsearchConstants;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElasticsearchActionRequestConverter {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchActionRequestConverter.class);

    private static final String ES_QUERY_DSL_PREFIX = "query";
    private static final String PARENT = "parent";

    private ElasticsearchActionRequestConverter() {
    }

    // Update requests
    private static UpdateRequest createUpdateRequest(Object document, Exchange exchange) {
        if (document instanceof UpdateRequest) {
            return (UpdateRequest) document;
        }
        UpdateRequest updateRequest = new UpdateRequest();
        if (document instanceof byte[]) {
            updateRequest.doc((byte[]) document);
        } else if (document instanceof Map) {
            updateRequest.doc((Map<String, Object>) document);
        } else if (document instanceof String) {
            updateRequest.doc((String) document);
        } else if (document instanceof XContentBuilder) {
            updateRequest.doc((XContentBuilder) document);
        } else {
            return null;
        }

        return updateRequest
            .waitForActiveShards(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, Integer.class))
            .parent(exchange.getIn().getHeader(
                PARENT, String.class))
            .index(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
            .type(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_INDEX_TYPE, String.class))
            .id(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_INDEX_ID, String.class));
    }

    // Index requests
    private static IndexRequest createIndexRequest(Object document, Exchange exchange) {
        if (document instanceof IndexRequest) {
            return (IndexRequest) document;
        }
        IndexRequest indexRequest = new IndexRequest();
        if (document instanceof byte[]) {
            indexRequest.source((byte[]) document, XContentFactory.xContentType((byte[]) document));
        } else if (document instanceof Map) {
            indexRequest.source((Map<String, Object>) document);
        } else if (document instanceof String) {
            indexRequest.source((String) document, XContentFactory.xContentType((String) document));
        } else if (document instanceof XContentBuilder) {
            indexRequest.source((XContentBuilder) document);
        } else {
            return null;
        }

        return indexRequest
            .waitForActiveShards(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, Integer.class))
            .parent(exchange.getIn().getHeader(
                PARENT, String.class))
            .index(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
            .type(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_INDEX_TYPE, String.class));
    }

    public static IndexRequest toIndexRequest(Object document, Exchange exchange) {
        return createIndexRequest(document, exchange)
            .id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
    }

    public static UpdateRequest toUpdateRequest(Object document, Exchange exchange) {
        return createUpdateRequest(document, exchange)
            .id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
    }

    public static GetRequest toGetRequest(Object document, Exchange exchange) {
        if (document instanceof GetRequest) {
            return (GetRequest) document;
        }
        return new GetRequest(exchange.getIn().getHeader(
            ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
            .type(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_INDEX_TYPE,
                String.class)).id((String) document);
    }

    public static DeleteRequest toDeleteRequest(Object document, Exchange exchange) {
        if (document instanceof DeleteRequest) {
            return (DeleteRequest) document;
        }
        if (document instanceof String) {
            return new DeleteRequest()
                .index(exchange.getIn().getHeader(
                    ElasticsearchConstants.PARAM_INDEX_NAME,
                    String.class))
                .type(exchange.getIn().getHeader(
                    ElasticsearchConstants.PARAM_INDEX_TYPE,
                    String.class)).id((String) document);
        } else {
            throw new IllegalArgumentException("Wrong body type. Only DeleteRequest or String is allowed as a type");
        }
    }

    public static SearchRequest toSearchRequest(Object queryObject, Exchange exchange) throws IOException {
        if (queryObject instanceof SearchRequest) {
            return (SearchRequest) queryObject;
        }
        SearchRequest searchRequest = new SearchRequest(exchange.getIn()
            .getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
            .types(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_TYPE, String.class));

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        String queryText = null;

        if (queryObject instanceof Map<?, ?>) {
            Map<String, Object> mapQuery = (Map<String, Object>) queryObject;
            // Remove 'query' prefix from the query object for backward compatibility
            if (mapQuery.containsKey(ES_QUERY_DSL_PREFIX)) {
                mapQuery = (Map<String, Object>) mapQuery.get(ES_QUERY_DSL_PREFIX);
            }
            try {
                XContentBuilder contentBuilder = XContentFactory.contentBuilder(XContentType.JSON);
                queryText = contentBuilder.map(mapQuery).string();
            } catch (IOException e) {
                LOG.error(e.getMessage());
            }
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
            return null;
        }

        searchSourceBuilder.query(QueryBuilders.wrapperQuery(queryText));
        searchRequest.source(searchSourceBuilder);

        return searchRequest;
    }

    public static BulkRequest toBulkRequest(Object documents, Exchange exchange) {
        if (documents instanceof BulkRequest) {
            return (BulkRequest) documents;
        }
        if (documents instanceof List) {
            BulkRequest request = new BulkRequest();
            for (Object document : (List<Object>) documents) {
                request.add(createIndexRequest(document, exchange));
            }
            return request;
        } else {
            throw new IllegalArgumentException("Wrong body type. Only BulkRequest or List is allowed as a type");
        }
    }

}
