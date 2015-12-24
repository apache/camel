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

public class ElasticsearchClusterIndexTest extends ElasticsearchClusterBaseTest {

    @Test
    public void indexWithIp()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "1");

        String indexId = template.requestBodyAndHeaders("direct:indexWithIp", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
        
        headers.clear();
        
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "status");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "2");


        indexId = template.requestBodyAndHeaders("direct:indexWithIp", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
        
        assertEquals("Cluster must be of three nodes", runner.getNodeSize(), 3);
        assertEquals("Index id 1 must exists", true, client.prepareGet("twitter", "tweet", "1").get().isExists());
        assertEquals("Index id 2 must exists", true, client.prepareGet("twitter", "status", "2").get().isExists());
    }

    @Test
    public void indexWithIpAndPort()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "instagram");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "photo");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "3");

        String indexId = template.requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
        
        assertEquals("Cluster must be of three nodes", runner.getNodeSize(), 3);
        assertEquals("Index id 3 must exists", true, client.prepareGet("instagram", "photo", "3").get().isExists());
    }

    @Test
    public void indexWithTransportAddresses()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "facebook");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "post");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "4");

        String indexId = template.requestBodyAndHeaders("direct:indexWithTransportAddresses", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
        
        assertEquals("Cluster must be of three nodes", runner.getNodeSize(), 3);
        assertEquals("Index id 4 must exists", true, client.prepareGet("facebook", "post", "4").get().isExists());
    }

    @Test
    public void indexWithIpAndTransportAddresses()  throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchConstants.OPERATION_INDEX);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "ebay");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "search");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "5");

        //should ignore transport addresses configuration
        String indexId = template.requestBodyAndHeaders("direct:indexWithIpAndTransportAddresses", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
        
        assertEquals("Cluster must be of three nodes", runner.getNodeSize(), 3);
        assertEquals("Index id 5 must exists", true, client.prepareGet("ebay", "search", "5").get().isExists());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:indexWithIp").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost");
                from("direct:indexWithIpAndPort").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost&port=9300");
                from("direct:indexWithTransportAddresses").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&transportAddresses=localhost:9300,localhost:9301");
                from("direct:indexWithIpAndTransportAddresses").
                    to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost&port=9300&transportAddresses=localhost:4444,localhost:5555");
            }
        };
    }
}
