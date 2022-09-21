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
package org.apache.camel.component.es.integration;

import java.util.HashMap;
import java.util.Map;

import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.es.ElasticsearchConstants;
import org.apache.camel.component.es.ElasticsearchOperation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElasticsearchIndexIT extends ElasticsearchTestSupport {

    @Test
    void testIndex() {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");
    }

    @Test
    void testIndexDeleteWithBuilder() {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        boolean exists = template.requestBody("direct:exists", null, Boolean.class);
        assertTrue(exists, "index should be present");

        DeleteIndexRequest.Builder builder = new DeleteIndexRequest.Builder().index("twitter");
        Boolean status = template.requestBody("direct:deleteIndex", builder, Boolean.class);
        assertEquals(true, status, "status should be 200");

        exists = template.requestBody("direct:exists", null, Boolean.class);
        assertFalse(exists, "index should be absent");
    }

    @Test
    void testIndexDeleteWithString() {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        boolean exists = template.requestBody("direct:exists", null, Boolean.class);
        assertTrue(exists, "index should be present");

        Boolean status = template.requestBody("direct:deleteIndex", "twitter", Boolean.class);
        assertEquals(true, status, "status should be 200");

        exists = template.requestBody("direct:exists", null, Boolean.class);
        assertFalse(exists, "index should be absent");
    }

    @Test
    void testIndexWithHeaders() {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");
    }

    @Test
    void testIndexWithIDInHeader() {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "123");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");
        assertEquals("123", indexId, "indexId should be equals to the provided id");
    }

    @Test
    void testExists() {
        boolean exists = template.requestBodyAndHeader(
                "direct:exists", null, ElasticsearchConstants.PARAM_INDEX_NAME, "test_exists", Boolean.class);
        assertFalse(exists, "index should be absent");

        Map<String, String> map = createIndexedData();
        template.sendBodyAndHeader("direct:index", map, ElasticsearchConstants.PARAM_INDEX_NAME, "test_exists");

        exists = template.requestBodyAndHeader(
                "direct:exists", null, ElasticsearchConstants.PARAM_INDEX_NAME, "test_exists", Boolean.class);
        assertTrue(exists, "index should be present");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("elasticsearch://elasticsearch");
                from("direct:index")
                        .to("elasticsearch://elasticsearch?operation=Index&indexName=twitter");
                from("direct:exists")
                        .to("elasticsearch://elasticsearch?operation=Exists&indexName=twitter");
                from("direct:deleteIndex")
                        .to("elasticsearch://elasticsearch?operation=DeleteIndex&indexName=twitter");
            }
        };
    }
}
