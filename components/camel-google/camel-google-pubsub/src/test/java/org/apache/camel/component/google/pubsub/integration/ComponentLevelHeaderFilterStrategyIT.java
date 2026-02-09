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
package org.apache.camel.component.google.pubsub.integration;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.component.google.pubsub.PubsubTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration test that verifies component-level HeaderFilterStrategy configuration works correctly to filter sensitive
 * headers like Authorization and Cookie before sending to Google Pub/Sub.
 */
public class ComponentLevelHeaderFilterStrategyIT extends PubsubTestSupport {

    private static final String TOPIC_NAME = "headerFilterTopic";
    private static final String SUBSCRIPTION_NAME = "headerFilterSubscription";

    @EndpointInject("mock:receiveResult")
    private MockEndpoint receiveResult;

    @Produce("direct:from")
    private ProducerTemplate producer;

    @Override
    public void createTopicSubscription() {
        createTopicSubscriptionPair(TOPIC_NAME, SUBSCRIPTION_NAME);
    }

    @Override
    protected void addPubsubComponent(CamelContext context) {
        // Configure a custom HeaderFilterStrategy at component level
        // to filter sensitive headers like Authorization and Cookie
        DefaultHeaderFilterStrategy strategy = new DefaultHeaderFilterStrategy();
        strategy.setFilterOnMatch(true);
        strategy.getOutFilter().add("Authorization");
        strategy.getOutFilter().add("Cookie");

        GooglePubsubComponent component = new GooglePubsubComponent();
        component.setEndpoint(service.getServiceAddress());
        component.setAuthenticate(false);
        component.setHeaderFilterStrategy(strategy);

        context.addComponent("google-pubsub", component);
        context.getPropertiesComponent().setLocation("ref:prop");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:from")
                        .routeId("HeaderFilter_Send")
                        .to("google-pubsub:{{project.id}}:" + TOPIC_NAME);

                from("google-pubsub:{{project.id}}:" + SUBSCRIPTION_NAME + "?synchronousPull=true")
                        .routeId("HeaderFilter_Receive")
                        .to("mock:receiveResult");
            }
        };
    }

    @Test
    public void testSensitiveHeadersAreFiltered() throws Exception {
        Exchange exchange = new DefaultExchange(context);

        // Set sensitive headers that should be filtered (with different case variations)
        exchange.getIn().setBody("Test message with sensitive headers");
        exchange.getIn().setHeader("Authorization", "Bearer SensitiveAuthToken");
        exchange.getIn().setHeader("Cookie", "session=abc123");
        // Also test case insensitivity
        exchange.getIn().setHeader("AUTHORIZATION", "Bearer Token2");
        exchange.getIn().setHeader("cookie", "session=xyz");

        // Set a custom header that should NOT be filtered
        String customHeaderKey = "X-Custom-Header";
        String customHeaderValue = "CustomValue";
        exchange.getIn().setHeader(customHeaderKey, customHeaderValue);

        receiveResult.expectedMessageCount(1);
        receiveResult.expectedBodiesReceivedInAnyOrder(exchange.getIn().getBody());

        producer.send(exchange);

        receiveResult.assertIsSatisfied(5000);

        List<Exchange> receivedExchanges = receiveResult.getExchanges();
        assertNotNull(receivedExchanges, "Received exchanges should not be null");
        assertEquals(1, receivedExchanges.size(), "Should receive exactly one message");

        Exchange receivedExchange = receivedExchanges.get(0);

        // Verify sensitive headers are filtered out (all case variations)
        assertNull(receivedExchange.getIn().getHeader("Authorization"),
                "Authorization header should be filtered and not received");
        assertNull(receivedExchange.getIn().getHeader("AUTHORIZATION"),
                "AUTHORIZATION header should be filtered and not received");
        assertNull(receivedExchange.getIn().getHeader("Cookie"),
                "Cookie header should be filtered and not received");
        assertNull(receivedExchange.getIn().getHeader("cookie"),
                "cookie header should be filtered and not received");

        // Verify non-sensitive custom header is preserved
        assertEquals(customHeaderValue, receivedExchange.getIn().getHeader(customHeaderKey),
                "Custom header should be preserved");
    }
}
