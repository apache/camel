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
package org.apache.camel.component.dynamicrouter.integration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DynamicRouterSendDynamicAwareIT {

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    CamelContext context;

    ProducerTemplate template;

    MockEndpoint mock1;

    MockEndpoint mock2;

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("directToDynamicRouter")
                        .toD("dynamic-router://test?synchronous=true");
                from("direct://subscribe-bean-expression")
                        .routeId("subscribeRouteBeanExpression")
                        .toD("dynamic-router-control://subscribe" +
                             "?subscribeChannel=${header.subscribeChannel}" +
                             "&subscriptionId=${header.subscriptionId}" +
                             "&destinationUri=${header.destinationUri}" +
                             "&priority=${header.priority}" +
                             "&predicate=${header.predicate}");
            }
        });
    }

    @BeforeEach
    void setUp() {
        this.context = contextExtension.getContext();
        this.template = context.createProducerTemplate();
        this.mock1 = contextExtension.getMockEndpoint("mock:result1", true);
        this.mock2 = contextExtension.getMockEndpoint("mock:result2", true);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    /**
     * Tests participant subscription, and that messages are received at their registered destination endpoints.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    void testSubscribeWithUriAndMultipleSubscribers() throws Exception {
        mock1.expectedMinimumMessageCount(1);
        mock2.expectedMinimumMessageCount(1);

        template.sendBodyAndHeaders("direct:subscribe-bean-expression", "",
                Map.of("subscribeChannel", "test",
                        "subscriptionId", "testSubscription1",
                        "destinationUri", mock1.getEndpointUri(),
                        "priority", "1",
                        "predicate", "${body} contains 'Message1'"));

        template.sendBodyAndHeaders("direct:subscribe-bean-expression", "",
                Map.of("subscribeChannel", "test",
                        "subscriptionId", "testSubscription2",
                        "destinationUri", mock2.getEndpointUri(),
                        "priority", "1",
                        "predicate", "${body} contains 'Message2'"));

        // Trigger events to subscribers
        template.sendBody("direct:start", "testMessage1");
        template.sendBody("direct:start", "testMessage2");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
    }
}
