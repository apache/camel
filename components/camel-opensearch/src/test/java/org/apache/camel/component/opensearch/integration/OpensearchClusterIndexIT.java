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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.opensearch.OpensearchConstants;
import org.apache.camel.component.opensearch.OpensearchOperation;
import org.apache.http.impl.client.BasicResponseHandler;
import org.junit.jupiter.api.Test;
import org.opensearch.client.Request;
import org.opensearch.client.opensearch.core.GetRequest;

import static org.apache.camel.test.junit5.TestSupport.assertStringContains;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpensearchClusterIndexIT extends OpensearchTestSupport {

    @Test
    void indexWithIpAndPort() throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Index);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "twitter");
        headers.put(OpensearchConstants.PARAM_INDEX_ID, "1");

        String indexId = template().requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");

        indexId = template().requestBodyAndHeaders("direct:indexWithIpAndPort", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");

        assertTrue(client.get(new GetRequest.Builder().index("twitter").id("1").build(), ObjectNode.class).found(),
                "Index id 1 must exists");
    }

    @Test
    void indexWithSnifferEnable() throws Exception {
        Map<String, String> map = createIndexedData();
        Map<String, Object> headers = new HashMap<>();
        headers.put(OpensearchConstants.PARAM_OPERATION, OpensearchOperation.Index);
        headers.put(OpensearchConstants.PARAM_INDEX_NAME, "facebook");
        headers.put(OpensearchConstants.PARAM_INDEX_ID, "4");

        String indexId = template().requestBodyAndHeaders("direct:indexWithSniffer", map, headers, String.class);
        assertNotNull(indexId, "indexId should be set");

        assertTrue(client.get(new GetRequest.Builder().index("facebook").id("4").build(), ObjectNode.class).found(),
                "Index id 4 must exists");

        final BasicResponseHandler responseHandler = new BasicResponseHandler();
        Request request = new Request("GET", "/_cluster/health?pretty");
        String body = responseHandler.handleEntity(restClient.performRequest(request).getEntity());
        assertStringContains(body, "\"number_of_data_nodes\" : 1");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:indexWithIpAndPort")
                        .to("opensearch://" + clusterName + "?operation=Index&indexName=twitter");
                from("direct:indexWithSniffer")
                        .to("opensearch://" + clusterName + "?operation=Index&indexName=twitter&enableSniffer=true");
            }
        };
    }
}
