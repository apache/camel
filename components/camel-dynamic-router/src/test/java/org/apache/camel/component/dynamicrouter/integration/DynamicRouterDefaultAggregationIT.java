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

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dynamicrouter.control.DynamicRouterControlMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Verifies the default aggregation behavior of the Dynamic Router: as documented, when no aggregationStrategy is
 * configured, "Camel will use the last reply as the outgoing message", so modifications made by the last matching
 * recipient (message body, exchange properties) must be visible in the calling route after the dynamic-router call.
 */
public class DynamicRouterDefaultAggregationIT {

    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    CamelContext context;

    ProducerTemplate template;

    MockEndpoint resultMock;

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("directToDynamicRouter")
                        .to("dynamic-router://test?recipientMode=allMatch")
                        .to("mock:result");

                // First recipient (priority 1): leaves its copy of the exchange untouched
                from("direct:first")
                        .routeId("firstRecipient")
                        .log("first recipient received '${body}'");

                // Second recipient (priority 2): modifies the body and sets a property on its copy
                from("direct:second")
                        .routeId("secondRecipient")
                        .setProperty("enrichedBy", constant("second"))
                        .setBody(constant("modified by second"));
            }
        });
    }

    @BeforeEach
    void setUp() {
        this.context = contextExtension.getContext();
        this.template = context.createProducerTemplate();
        this.resultMock = contextExtension.getMockEndpoint("mock:result", true);
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    /**
     * With two matching recipients in allMatch mode, the result that comes back to the calling route must be the last
     * reply: the body and the exchange property set by the second recipient.
     *
     * @throws Exception if interrupted while waiting for mocks to be satisfied
     */
    @Test
    void testDefaultAggregationUsesLastReply() throws Exception {
        subscribe("firstSubscription", "direct:first", 1);
        subscribe("secondSubscription", "direct:second", 2);

        resultMock.expectedMessageCount(1);

        template.sendBody("direct:start", "original");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);

        Exchange result = resultMock.getExchanges().get(0);
        Assertions.assertEquals("modified by second", result.getMessage().getBody(String.class));
        Assertions.assertEquals("second", result.getProperty("enrichedBy", String.class));
    }

    private void subscribe(String subscriptionId, String destinationUri, int priority) {
        DynamicRouterControlMessage controlMessage = DynamicRouterControlMessage.Builder.newBuilder()
                .subscribeChannel("test")
                .subscriptionId(subscriptionId)
                .destinationUri(destinationUri)
                .priority(priority)
                .predicate("${body} != null")
                .expressionLanguage("simple")
                .build();
        template.sendBody("dynamic-router-control:subscribe", controlMessage);
    }
}
