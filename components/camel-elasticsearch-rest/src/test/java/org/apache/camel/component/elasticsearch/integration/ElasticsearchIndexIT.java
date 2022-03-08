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
package org.apache.camel.component.elasticsearch.integration;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.elasticsearch.ElasticsearchConstants;
import org.apache.camel.component.elasticsearch.ElasticsearchOperation;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ElasticsearchIndexIT extends ElasticsearchTestSupport {

    @Test
    public void testIndex() {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");
    }

    @Test
    public void testIndexDelete() {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        DeleteIndexRequest index = new DeleteIndexRequest("_all");
        Boolean status = template.requestBody("direct:deleteIndex", index, Boolean.class);
        assertEquals(true, status, "status should be 200");
    }

    @Test
    public void testIndexWithReplication() {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:indexWithReplication", map, String.class);
        assertNotNull(indexId, "indexId should be set");
    }

    @Test
    public void testIndexWithHeaders() {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");
    }

    @Test
    public void testIndexWithIDInHeader() {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "123");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");
        assertEquals("123", indexId, "indexId should be equals to the provided id");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("elasticsearch-rest://elasticsearch");
                from("direct:index")
                        .to("elasticsearch-rest://elasticsearch?operation=Index&indexName=twitter");
                from("direct:deleteIndex")
                        .to("elasticsearch-rest://elasticsearch?operation=DeleteIndex&indexName=twitter");
                from("direct:indexWithReplication")
                        .to("elasticsearch-rest://elasticsearch?operation=Index&indexName=twitter");
            }
        };
    }
}
