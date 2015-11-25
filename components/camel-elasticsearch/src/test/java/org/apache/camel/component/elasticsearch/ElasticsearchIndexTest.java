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
import org.junit.Ignore;
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

    @Test
    @Ignore("need to setup the cluster IP for this test")
    public void indexWithIp()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:indexWithIp", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    @Ignore("need to setup the cluster IP/Port for this test")
    public void indexWithIpAndPort()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    @Ignore("need to setup the cluster with multiple nodes for this test")
    public void indexWithTransportAddresses()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:indexWithTransportAddresses", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    @Ignore("need to setup the cluster with multiple nodes for this test")
    public void indexWithIpAndTransportAddresses()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");

        //should ignore transport addresses configuration
        String indexId = template.requestBodyAndHeaders("direct:indexWithIpAndTransportAddresses", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("elasticsearch://local");
                from("direct:index").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
                from("direct:indexWithReplication").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet&replicationType=SYNC");
                from("direct:indexWithWriteConsistency").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet&consistencyLevel=ONE");
                //from("direct:indexWithIp").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost");
                //from("direct:indexWithIpAndPort").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost&port=9300");
                //from("direct:indexWithTransportAddresses").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&transportAddresses=localhost:9300,localhost:9301");
                //from("direct:indexWithIpAndTransportAddresses").
                //to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost&port=9300&transportAddresses=localhost:4444,localhost:5555");
            }
        };
    }
}
