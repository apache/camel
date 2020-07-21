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
package org.apache.camel.component.elasticsearch.converter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.elasticsearch.ElasticsearchConstants;
import org.apache.camel.util.ObjectHelper;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Converter(generateLoader = true)
public final class ElasticsearchActionRequestConverter {
    private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchActionRequestConverter.class);

    private static final String ES_QUERY_DSL_PREFIX = "query";
    private static final String PARENT = "parent";

    private ElasticsearchActionRequestConverter() {
    }

    // Update requests
    private static UpdateRequest createUpdateRequest(Object document, Exchange exchange) {
        if (document instanceof UpdateRequest) {
            return (UpdateRequest)document;
        }
        UpdateRequest updateRequest = new UpdateRequest();
        if (document instanceof byte[]) {
            updateRequest.doc((byte[])document);
        } else if (document instanceof Map) {
            updateRequest.doc((Map<String, Object>)document);
        } else if (document instanceof String) {
            updateRequest.doc((String)document, XContentFactory.xContentType((String)document));
        } else if (document instanceof XContentBuilder) {
            updateRequest.doc((XContentBuilder)document);
        } else {
            return null;
        }

        return updateRequest.waitForActiveShards(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, Integer.class))
            .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
            .id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
    }

    // Index requests
    private static IndexRequest createIndexRequest(Object document, Exchange exchange) {
        if (document instanceof IndexRequest) {
            return (IndexRequest)document;
        }
        IndexRequest indexRequest = new IndexRequest();
        if (document instanceof byte[]) {
            indexRequest.source((byte[])document, XContentFactory.xContentType((byte[])document));
        } else if (document instanceof Map) {
            indexRequest.source((Map<String, Object>)document);
        } else if (document instanceof String) {
            indexRequest.source((String)document, XContentFactory.xContentType((String)document));
        } else if (document instanceof XContentBuilder) {
            indexRequest.source((XContentBuilder)document);
        } else {
            return null;
        }

        return indexRequest.waitForActiveShards(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_WAIT_FOR_ACTIVE_SHARDS, Integer.class))
            .index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
    }

    @Converter
    public static IndexRequest toIndexRequest(Object document, Exchange exchange) {
        return createIndexRequest(document, exchange).id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
    }

    @Converter
    public static UpdateRequest toUpdateRequest(Object document, Exchange exchange) {
        return createUpdateRequest(document, exchange).id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
    }

    @Converter
    public static GetRequest toGetRequest(Object document, Exchange exchange) {
        if (document instanceof GetRequest) {
            return (GetRequest)document;
        }
        if (document instanceof String) {
            return new GetRequest(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
                    .id((String) document);
        }
        return null;
    }

    @Converter
    public static DeleteRequest toDeleteRequest(Object document, Exchange exchange) {
        if (document instanceof DeleteRequest) {
            return (DeleteRequest)document;
        }
        if (document instanceof String) {
            return new DeleteRequest().index(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
                .id((String)document);
        }
        return null;
    }

    @Converter
    public static DeleteIndexRequest toDeleteIndexRequest(Object document, Exchange exchange) {
        if (document instanceof DeleteIndexRequest) {
            return (DeleteIndexRequest)document;
        }
        if (document instanceof String) {
            String index = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class);
            return new DeleteIndexRequest(index);
        }
        return null;
    }

    @Converter
    public static SearchRequest toSearchRequest(Object queryObject, Exchange exchange) throws IOException {
        if (queryObject instanceof SearchRequest) {
            return (SearchRequest)queryObject;
        }
        SearchRequest searchRequest = new SearchRequest();

        // Only setup the indexName and indexType if the message header has the
        // setting
        String indexName = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class);
        Integer size = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_SIZE, Integer.class);
        Integer from = exchange.getIn().getHeader(ElasticsearchConstants.PARAM_FROM, Integer.class);
        if (ObjectHelper.isNotEmpty(indexName)) {
            searchRequest.indices(indexName);
        }

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        String queryText = null;

        if (queryObject instanceof Map<?, ?>) {
            Map<String, Object> mapQuery = (Map<String, Object>)queryObject;
            // Remove 'query' prefix from the query object for backward
            // compatibility
            if (mapQuery.containsKey(ES_QUERY_DSL_PREFIX)) {
                mapQuery = (Map<String, Object>)mapQuery.get(ES_QUERY_DSL_PREFIX);
            }
            try {
                XContentBuilder contentBuilder = XContentFactory.contentBuilder(XContentType.JSON);
                queryText = Strings.toString(contentBuilder.map(mapQuery));
            } catch (IOException e) {
                LOG.error("Cannot build the QueryText from the map.", e);
            }
        } else if (queryObject instanceof String) {
            queryText = (String)queryObject;
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonTextObject = mapper.readValue(queryText, JsonNode.class);
            JsonNode parentJsonNode = jsonTextObject.get(ES_QUERY_DSL_PREFIX);
            if (parentJsonNode != null) {
                queryText = parentJsonNode.toString();
            }
        } else {
            // Cannot convert the queryObject into SearchRequest
            LOG.info("Cannot convert queryObject into SearchRequest object");
            return null;
        }
        if (size != null)  {
            searchSourceBuilder.size(size);
        }
        if (from != null)  {
            searchSourceBuilder.from(from);
        }
        searchSourceBuilder.query(QueryBuilders.wrapperQuery(queryText));
        searchRequest.source(searchSourceBuilder);

        return searchRequest;
    }

    @Converter
    public static BulkRequest toBulkRequest(Object documents, Exchange exchange) {
        if (documents instanceof BulkRequest) {
            return (BulkRequest)documents;
        }
        if (documents instanceof List) {
            BulkRequest request = new BulkRequest();
            for (Object document : (List<Object>)documents) {
                request.add(createIndexRequest(document, exchange));
            }
            return request;
        }
        return null;
    }

}
