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
package org.apache.camel.component.elasticsearch;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ElasticsearchIndexTest extends ElasticsearchBaseTest {

    @Test
    public void testIndex() throws Exception {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testIndexWithReplication() throws Exception {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:indexWithReplication", map, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testIndexWithWriteConsistency() throws Exception {
        Map<String, String> map = createIndexedData();
        String indexId = template.requestBody("direct:indexWithWriteConsistency", map, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testIndexWithHeaders() throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testIndexWithIDInHeader() throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "123");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
        assertEquals("indexId should be equals to the provided id", "123", indexId);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("elasticsearch://local");
                from("direct:index").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
                from("direct:indexWithReplication").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
                from("direct:indexWithWriteConsistency").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet&consistencyLevel=ONE");
            }
        };
    }
}
