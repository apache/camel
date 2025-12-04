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

package org.apache.camel.component.ibm.watson.discovery.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.ibm.watson.discovery.v2.model.ListCollectionsResponse;
import com.ibm.watson.discovery.v2.model.QueryResponse;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watson.discovery.WatsonDiscoveryConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration tests for Watson Discovery operations.
 */
@EnabledIfSystemProperties({
    @EnabledIfSystemProperty(
            named = "camel.ibm.watson.apiKey",
            matches = ".*",
            disabledReason = "IBM Watson API Key not provided"),
    @EnabledIfSystemProperty(
            named = "camel.ibm.watson.serviceUrl",
            matches = ".*",
            disabledReason = "IBM Watson Discovery Service URL not provided"),
    @EnabledIfSystemProperty(
            named = "camel.ibm.watson.projectId",
            matches = ".*",
            disabledReason = "IBM Watson Discovery Project ID not provided")
})
public class WatsonDiscoveryIT extends WatsonDiscoveryTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonDiscoveryIT.class);

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
    }

    @Test
    public void testListCollections() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:listCollections", "");

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        ListCollectionsResponse result = exchange.getIn().getBody(ListCollectionsResponse.class);

        assertNotNull(result);
        assertNotNull(result.getCollections());

        LOG.info("Found {} collections", result.getCollections().size());
        result.getCollections().forEach(collection -> {
            LOG.info("  Collection: {} (ID: {})", collection.getName(), collection.getCollectionId());
        });
    }

    @Test
    public void testQuery() throws Exception {
        mockResult.expectedMessageCount(1);

        final String query = "IBM Watson";

        template.sendBody("direct:query", query);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        QueryResponse result = exchange.getIn().getBody(QueryResponse.class);

        assertNotNull(result);

        LOG.info("Query returned {} results", result.getMatchingResults());
        if (result.getResults() != null && !result.getResults().isEmpty()) {
            LOG.info("First result: {}", result.getResults().get(0));
        }
    }

    @Test
    public void testQueryWithFilter() throws Exception {
        mockResult.expectedMessageCount(1);

        final String query = "cloud computing";
        final String filter = "enriched_text.entities.type:Company";

        template.send("direct:queryWithFilter", exchange -> {
            exchange.getIn().setBody(query);
            exchange.getIn().setHeader(WatsonDiscoveryConstants.FILTER, filter);
            exchange.getIn().setHeader(WatsonDiscoveryConstants.COUNT, 5);
        });

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        QueryResponse result = exchange.getIn().getBody(QueryResponse.class);

        assertNotNull(result);
        LOG.info("Filtered query returned {} results", result.getMatchingResults());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:listCollections")
                        .to(buildEndpointUri("listCollections"))
                        .to("mock:result");

                from("direct:query").to(buildEndpointUri("query")).to("mock:result");

                from("direct:queryWithFilter").to(buildEndpointUri("query")).to("mock:result");
            }
        };
    }
}
