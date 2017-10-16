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
package org.apache.camel.component.elasticsearch5;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.http.impl.client.BasicResponseHandler;
import org.elasticsearch.action.get.GetRequest;
import org.junit.Test;

public class ElasticsearchClusterIndexTest extends ElasticsearchClusterBaseTest {

    @Test
    public void indexWithIpAndPort() throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "tweet");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "1");

        String indexId = template.requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);

        headers.clear();

        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "status");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "2");

        indexId = template.requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);

        assertEquals("Cluster must be of three nodes", runner.getNodeSize(), 3);
        assertEquals("Index id 1 must exists", true, client.get(new GetRequest("twitter", "tweet", "1")).isExists());
        assertEquals("Index id 2 must exists", true, client.get(new GetRequest("twitter", "status", "2")).isExists());
    }

    @Test
    public void indexWithSnifferEnable() throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(ElasticsearchConstants.PARAM_OPERATION, ElasticsearchOperation.Index);
        headers.put(ElasticsearchConstants.PARAM_INDEX_NAME, "facebook");
        headers.put(ElasticsearchConstants.PARAM_INDEX_TYPE, "post");
        headers.put(ElasticsearchConstants.PARAM_INDEX_ID, "4");

        String indexId = template.requestBodyAndHeaders("direct:indexWithSniffer", map, headers, String.class);
        assertNotNull("indexId should be set", indexId);

        assertEquals("Cluster must be of three nodes", runner.getNodeSize(), 3);
        assertEquals("Index id 4 must exists", true, client.get(new GetRequest("facebook", "post", "4")).isExists());

        final BasicResponseHandler responseHandler = new BasicResponseHandler();
        String body = responseHandler.handleEntity(restclient.performRequest("GET", "/_cluster/health?pretty").getEntity());
        assertStringContains(body, "\"number_of_data_nodes\" : 3");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:indexWithIpAndPort")
                    .to("elasticsearch5-rest://" + clusterName + "?operation=Index&indexName=twitter&indexType=tweet&hostAddresses=localhost:" + ES_FIRST_NODE_TRANSPORT_PORT);
                from("direct:indexWithSniffer")
                    .to("elasticsearch5-rest://" + clusterName + "?operation=Index&indexName=twitter&indexType=tweet&enableSniffer=true&hostAddresses=localhost:" + ES_FIRST_NODE_TRANSPORT_PORT);
            }
        };
    }
}
