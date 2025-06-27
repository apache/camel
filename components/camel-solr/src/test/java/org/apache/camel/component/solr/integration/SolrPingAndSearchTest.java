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
package org.apache.camel.component.solr.integration;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.solr.SolrConstants;
import org.apache.camel.component.solr.SolrUtils;
import org.apache.solr.client.solrj.SolrResponse;
import org.apache.solr.client.solrj.impl.BaseHttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SolrPingAndSearchTest extends SolrTestSupport {

    @Test
    public void pingToToNonexistentInstance() {

        Exchange exchange = pingInstance("direct:start-missing");
        assertTrue(exchange.isFailed());

    }

    @Test
    public void pingToTestInfraInstance() throws InvalidPayloadException {

        // no collection specified - default collection from component
        assertFalse(pingInstance("direct:start", (String) null).isFailed());
        assertFalse(pingInstance("direct:start-sync", (String) null).isFailed());

        // collection as endpoint parameter
        // convert to SolrPingResponse
        SolrPingResponse solrPingResponse = pingInstance("direct:start-collection")
                .getMessage().getMandatoryBody(SolrPingResponse.class);
        assertEquals(0, solrPingResponse.getStatus());
        // convert to SolrResponse
        SolrResponse solrResponse = pingInstance("direct:start-collection")
                .getMessage().getMandatoryBody(SolrResponse.class);
        assertEquals("OK", solrResponse.getResponse().get("status"));
        // convert to Map
        Map<String, Object> responseMap = SolrUtils.parseAsFlatMap(solrPingResponse);
        assertEquals(0, responseMap.get("responseHeader.status"));
        assertEquals("OK", responseMap.get("status"));

        // collection as exchange header
        Map<?, ?> map = pingInstance("direct:start", SolrConstants.DEFAULT_COLLECTION)
                .getMessage().getMandatoryBody(Map.class);
        LOG.info("Solr response = {}", map);
        assertEquals("OK", map.get("status"));

        // invalid collection
        assertTrue(pingInstance("direct:start", "invalid-collection").isFailed());
        assertTrue(pingInstance("direct:start-sync", "invalid-collection").isFailed());

    }

    @Test
    public void clusterStatusToTestInfraInstance() throws InvalidPayloadException {

        Exchange responseExchange = processRequest("direct:start", new CollectionAdminRequest.ClusterStatus(), Map.of());
        Map<?, ?> map = responseExchange.getMessage().getMandatoryBody(Map.class);
        LOG.info("Solr response = {}", map);
        // clusterstatus should fail as we're not running in solrCloud mode
        assertTrue(responseExchange.isFailed());
        assertInstanceOf(CamelExchangeException.class, responseExchange.getException());
        assertInstanceOf(BaseHttpSolrClient.RemoteSolrException.class, responseExchange.getException().getCause().getCause());
        assertEquals(400,
                ((BaseHttpSolrClient.RemoteSolrException) responseExchange.getException().getCause().getCause()).code());
        assertTrue(responseExchange.getException().getCause().getCause().getMessage()
                .endsWith("Solr instance is not running in SolrCloud mode."));

    }

    @Test
    void testQueryWithFromAndSizeParameters() {

        List<Map<String, String>> content = IntStream.range(0, 4).mapToObj(i -> Map.of("content", "content" + i)).toList();
        template.requestBodyAndHeaders("direct:index", content, SolrUtils.getHeadersForCommit());

        QueryResponse responseWithSizeTwo = executeSolrQuery("direct:searchWithSizeTwo", "*:*");
        QueryResponse responseFrom3 = executeSolrQuery("direct:searchFrom3", "*:*");
        assertEquals(2, responseWithSizeTwo.getResults().size());
        assertEquals(1, responseFrom3.getResults().size());
    }

    protected RouteBuilder createRouteBuilder() {

        return new RouteBuilder() {

            @Override
            public void configure() {

                from(DEFAULT_START_ENDPOINT)
                        .to(DEFAULT_SOLR_ENDPOINT);
                // ping tests
                from("direct:start-missing")
                        .to("solr:default/missing");
                from("direct:start-sync")
                        .to("solr:default?async=false");
                from("direct:start-collection")
                        .to("solr:default?collection=" + SolrConstants.DEFAULT_COLLECTION);
                // query tests
                from("direct:index")
                        .to("solr:default?operation=insert");
                from("direct:searchWithSizeTwo")
                        .to("solr:default?operation=search&size=2");
                from("direct:searchFrom3")
                        .to("solr:default?operation=search&from=3");
            }
        };
    }

}
