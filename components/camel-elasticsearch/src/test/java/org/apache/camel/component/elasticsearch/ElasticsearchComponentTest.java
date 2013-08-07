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
import org.apache.camel.test.junit4.CamelTestSupport;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ElasticsearchComponentTest extends CamelTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        deleteDirectory("target/data");
    }

    @Override
    public boolean isCreateCamelContextPerClass() {
        // let's speed up the tests using the same context
        return true;
    }

    @Test
    public void testIndex() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("content", "test");
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testGet() throws Exception {
        //first, INDEX a value
        Map<String, String> map = new HashMap<String, String>();
        map.put("content", "test");
        sendBody("direct:index", map);
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull("indexId should be set", indexId);

        //now, verify GET succeeded
        GetResponse response = template.requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());
    }

    @Test
    public void testDelete() throws Exception {
        //first, INDEX a value
        Map<String, String> map = new HashMap<String, String>();
        map.put("content", "test");
        sendBody("direct:index", map);
        String indexId = template.requestBody("direct:index", map, String.class);
        assertNotNull("indexId should be set", indexId);

        //now, verify GET succeeded
        GetResponse response = template.requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());

        //now, perform DELETE
        DeleteResponse deleteResponse = template.requestBody("direct:delete", indexId, DeleteResponse.class);
        assertNotNull("response should not be null", deleteResponse);

        //now, verify GET fails to find the indexed value
        response = template.requestBody("direct:get", indexId, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNull("response source should be null", response.getSource());
    }

    @Test
    public void testIndexWithHeaders() throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("content", "test");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConfiguration.PARAM_OPERATION, ElasticsearchConfiguration.OPERATION_INDEX);
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    @Ignore("need to setup the cluster IP for this test")
    public void indexWithIp()  throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("content", "test");
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConfiguration.PARAM_OPERATION, ElasticsearchConfiguration.OPERATION_INDEX);
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:indexWithIp", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    @Ignore("need to setup the cluster IP/Port for this test")
    public void indexWithIpAndPort()  throws Exception {
        Map<String, String> map = new HashMap<String, String>();
        map.put("content", "test");
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConfiguration.PARAM_OPERATION, ElasticsearchConfiguration.OPERATION_INDEX);
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);
    }

    @Test
    public void testGetWithHeaders() throws Exception {
        //first, INDEX a value
        Map<String, String> map = new HashMap<String, String>();
        map.put("content", "test");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConfiguration.PARAM_OPERATION, ElasticsearchConfiguration.OPERATION_INDEX);
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(ElasticsearchConfiguration.PARAM_OPERATION, ElasticsearchConfiguration.OPERATION_GET_BY_ID);
        GetResponse response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());
    }

    @Test
    public void testDeleteWithHeaders() throws Exception {
        //first, INDEX a value
        Map<String, String> map = new HashMap<String, String>();
        map.put("content", "test");

        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(ElasticsearchConfiguration.PARAM_OPERATION, ElasticsearchConfiguration.OPERATION_INDEX);
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConfiguration.PARAM_INDEX_TYPE, "tweet");

        String indexId = template.requestBodyAndHeaders("direct:start", map, headers, String.class);

        //now, verify GET
        headers.put(ElasticsearchConfiguration.PARAM_OPERATION, ElasticsearchConfiguration.OPERATION_GET_BY_ID);
        GetResponse response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNotNull("response source should not be null", response.getSource());

        //now, perform DELETE
        headers.put(ElasticsearchConfiguration.PARAM_OPERATION, ElasticsearchConfiguration.OPERATION_DELETE);
        DeleteResponse deleteResponse = template.requestBodyAndHeaders("direct:start", indexId, headers, DeleteResponse.class);
        assertNotNull("response should not be null", deleteResponse);

        //now, verify GET fails to find the indexed value
        headers.put(ElasticsearchConfiguration.PARAM_OPERATION, ElasticsearchConfiguration.OPERATION_GET_BY_ID);
        response = template.requestBodyAndHeaders("direct:start", indexId, headers, GetResponse.class);
        assertNotNull("response should not be null", response);
        assertNull("response source should be null", response.getSource());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("elasticsearch://local");
                from("direct:index").to("elasticsearch://local?operation=INDEX&indexName=twitter&indexType=tweet");
                from("direct:get").to("elasticsearch://local?operation=GET_BY_ID&indexName=twitter&indexType=tweet");
                from("direct:delete").to("elasticsearch://local?operation=DELETE&indexName=twitter&indexType=tweet");
                //from("direct:indexWithIp").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost");
                //from("direct:indexWithIpAndPort").to("elasticsearch://elasticsearch?operation=INDEX&indexName=twitter&indexType=tweet&ip=localhost&port=9300");
            }
        };
    }
}
