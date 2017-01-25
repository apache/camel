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
package org.apache.camel.component.elasticsearch.converter;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.camel.Converter;
import org.apache.camel.Exchange;
import org.apache.camel.component.elasticsearch.ElasticsearchConstants;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.exists.ExistsRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.MultiGetRequest;
import org.elasticsearch.action.get.MultiGetRequest.Item;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.MultiSearchRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.common.xcontent.XContentBuilder;

@Converter
public final class ElasticsearchActionRequestConverter {

    private ElasticsearchActionRequestConverter() {
    }

    // Update requests
    private static UpdateRequest createUpdateRequest(Object document, Exchange exchange) {
        if (document instanceof UpdateRequest) {
            return (UpdateRequest)document;
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
                .consistencyLevel(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARAM_CONSISTENCY_LEVEL, WriteConsistencyLevel.class))
                .parent(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARENT, String.class))
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
            return (IndexRequest)document;
        }
        IndexRequest indexRequest = new IndexRequest();
        if (document instanceof byte[]) {
            indexRequest.source((byte[]) document);
        } else if (document instanceof Map) {
            indexRequest.source((Map<String, Object>) document);
        } else if (document instanceof String) {
            indexRequest.source((String) document);
        } else if (document instanceof XContentBuilder) {
            indexRequest.source((XContentBuilder) document);
        } else {
            return null;
        }

        return indexRequest
                .consistencyLevel(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARAM_CONSISTENCY_LEVEL, WriteConsistencyLevel.class))
                .parent(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARENT, String.class))
                .index(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
                .type(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARAM_INDEX_TYPE, String.class));
    }

    @Converter
    public static IndexRequest toIndexRequest(Object document, Exchange exchange) {
        return createIndexRequest(document, exchange)
                .id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
    }

    @Converter
    public static UpdateRequest toUpdateRequest(Object document, Exchange exchange) {
        return createUpdateRequest(document, exchange)
                .id(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_ID, String.class));
    }

    @Converter
    public static GetRequest toGetRequest(String id, Exchange exchange) {
        return new GetRequest(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
                .type(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARAM_INDEX_TYPE,
                        String.class)).id(id);
    }
    
    @Converter
    public static ExistsRequest toExistsRequest(String id, Exchange exchange) {
        return new ExistsRequest(exchange.getIn().getHeader(
                ElasticsearchConstants.PARAM_INDEX_NAME, String.class));
    }
    
    @Converter
    public static MultiGetRequest toMultiGetRequest(Object document, Exchange exchange) {
        List<Item> items = (List<Item>) document;
        MultiGetRequest multiGetRequest = new MultiGetRequest();
        Iterator<Item> it = items.iterator();
        while (it.hasNext()) {
            MultiGetRequest.Item item = it.next();
            multiGetRequest.add(item);
        }
        return multiGetRequest;
    }
    
    @Converter
    public static MultiSearchRequest toMultiSearchRequest(Object document, Exchange exchange) {
        List<SearchRequest> items = (List<SearchRequest>) document;
        MultiSearchRequest multiSearchRequest = new MultiSearchRequest();
        Iterator<SearchRequest> it = items.iterator();
        while (it.hasNext()) {
            SearchRequest item = it.next();
            multiSearchRequest.add(item);
        }
        return multiSearchRequest;
    }

    @Converter
    public static DeleteRequest toDeleteRequest(String id, Exchange exchange) {
        return new DeleteRequest()
                .index(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARAM_INDEX_NAME,
                        String.class))
                .type(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARAM_INDEX_TYPE,
                        String.class)).id(id);
    }
    
    @Converter
    public static DeleteIndexRequest toDeleteIndexRequest(String id, Exchange exchange) {
        return new DeleteIndexRequest()
                .indices(exchange.getIn().getHeader(
                        ElasticsearchConstants.PARAM_INDEX_NAME,
                        String.class));
    }

    @Converter
    public static SearchRequest toSearchRequest(Object queryObject, Exchange exchange) {
        SearchRequest searchRequest = new SearchRequest(exchange.getIn()
                                                        .getHeader(ElasticsearchConstants.PARAM_INDEX_NAME, String.class))
                                      .types(exchange.getIn().getHeader(ElasticsearchConstants.PARAM_INDEX_TYPE, String.class));
        // Setup the query object into the search request
        if (queryObject instanceof byte[]) { 
            searchRequest.source((byte[]) queryObject);
        } else if (queryObject instanceof Map) {
            searchRequest.source((Map<String, Object>) queryObject);
        } else if (queryObject instanceof String) {
            searchRequest.source((String) queryObject);
        } else if (queryObject instanceof XContentBuilder) {
            searchRequest.source((XContentBuilder) queryObject);
        } else {
            // Cannot convert the queryObject into SearchRequest
            return null;
        }                                                                          
        return searchRequest;
    }

    @Converter
    public static BulkRequest toBulkRequest(List<Object> documents,
                                            Exchange exchange) {
        BulkRequest request = new BulkRequest();
        for (Object document : documents) {
            request.add(createIndexRequest(document, exchange));
        }
        return request;
    }
}
