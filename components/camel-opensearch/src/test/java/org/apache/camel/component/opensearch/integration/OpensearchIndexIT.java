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
package org.apache.camel.component.opensearch.integration;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.opensearch.OpensearchConstants;
import org.apache.camel.component.opensearch.OpensearchOperation;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpensearchIndexIT extends OpensearchTestSupport {

    @Test
    void testIndex() {
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");
    }

    @Test
    void testIndexDeleteWithBuilder() {
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        boolean exists = template().requestBody("direct:exists", null, Boolean.class);
        assertTrue(exists, "index should be present");

        DeleteIndexRequest.Builder builder = new DeleteIndexRequest.Builder().index("twitter");
        Boolean status = template().requestBody("direct:deleteIndex", builder, Boolean.class);
        assertEquals(true, status, "status should be 200");

        exists = template().requestBody("direct:exists", null, Boolean.class);
        assertFalse(exists, "index should be absent");
    }

    @Test
    void testIndexDeleteWithString() {
        Map<String, String> map = createIndexedData();
        String indexId = template().requestBody("direct:index", map, String.class);
        assertNotNull(indexId, "indexId should be set");

        boolean exists = template().requestBody("direct:exists", null, Boolean.class);
        assertTrue(exists, "index should be present");

        Boolean status = template().requestBody("direct:deleteIndex", "twitter", Boolean.class);
        assertEquals(true, status, "status should be 200");

        exists = template().requestBody("direct:exists", null, Boolean.class);
        assertFalse(exists, "index should be absent");
    }

    @Test
    void testIndexWithHeaders() {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Index);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");

        String indexId = template().requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");
    }

    @Test
    void testIndexWithIDInHeader() {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Index);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(OpensearchConstants.PARAM_INDEX_ID, "123");

        String indexId = template().requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");
        assertEquals("123", indexId, "indexId should be equals to the provided id");
    }

    @Test
    void testExists() {
        boolean exists = template().requestBodyAndHeader(
                "direct:exists", null, OpensearchConstants.PARAM_INDEX_NAME, "test_exists", Boolean.class);
        assertFalse(exists, "index should be absent");

        Map<String, String> map = createIndexedData();
        template().sendBodyAndHeader("direct:index", map, OpensearchConstants.PARAM_INDEX_NAME, "test_exists");

        exists = template().requestBodyAndHeader(
                "direct:exists", null, OpensearchConstants.PARAM_INDEX_NAME, "test_exists", Boolean.class);
        assertTrue(exists, "index should be present");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("opensearch://opensearch");
                from("direct:index")
                        .to("opensearch://opensearch?operation=Index&indexName=twitter");
                from("direct:exists")
                        .to("opensearch://opensearch?operation=Exists&indexName=twitter");
                from("direct:deleteIndex")
                        .to("opensearch://opensearch?operation=DeleteIndex&indexName=twitter");
            }
        };
    }
}
