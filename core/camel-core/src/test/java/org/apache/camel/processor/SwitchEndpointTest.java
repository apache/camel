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
package org.apache.camel.processor;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchEndpointTest extends ContextTestSupport {
    private final AtomicInteger initializations = new AtomicInteger();
    private final AtomicInteger evaluations = new AtomicInteger();
    private final AtomicInteger attempts = new AtomicInteger();

    @Test
    void routeEndpointDiscoveryIncludesCaseAndFallback() {
        RouteDefinition route = new RouteDefinition("direct:discovery");
        route.doSwitch(new HeaderExpression("decision")).doCase("match", "mock:destination").otherwise("mock:fallback");
        var uris = RouteDefinitionHelper.gatherAllStaticEndpointUris(context, route, false, true);
        assertTrue(uris.contains("mock://destination"), uris.toString());
        assertTrue(uris.contains("mock://fallback"), uris.toString());
    }

    @Test
    void resolvedDestinationsUseInterceptorsAndRestartWithTheRoute() throws Exception {
        getMockEndpoint("mock:intercepted").expectedMessageCount(2);
        getMockEndpoint("mock:destination").expectedBodiesReceived("first", "second");
        template.sendBody("direct:intercepted", "first");
        context.getRouteController().stopRoute("intercepted");
        context.getRouteController().startRoute("intercepted");
        template.sendBody("direct:intercepted", "second");
        assertMockEndpointsSatisfied();
    }

    @Test
    void selectedEndpointRedeliveryDoesNotRepeatSelector() throws Exception {
        getMockEndpoint("mock:delivered").expectedBodiesReceived("message");
        template.sendBody("direct:redelivery", "message");
        assertMockEndpointsSatisfied();
        assertEquals(1, evaluations.get());
        assertEquals(1, initializations.get());
        assertEquals(2, attempts.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        Properties properties = new Properties();
        properties.setProperty("switch.destination", "mock:destination");
        context.getPropertiesComponent().setInitialProperties(properties);
        return new RouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("mock:destination").to("mock:intercepted");
                from("direct:intercepted").routeId("intercepted").doSwitch(constant("match"))
                        .doCase("match", "{{switch.destination}}").otherwise("mock:fallback");
                from("direct:redelivery")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0))
                        .doSwitch(new ExpressionAdapter() {
                            @Override
                            public void init(CamelContext context) {
                                initializations.incrementAndGet();
                            }

                            @Override
                            public Object evaluate(Exchange exchange) {
                                evaluations.incrementAndGet();
                                return "match";
                            }
                        }).doCase("match", "direct:destination");
                from("direct:destination").errorHandler(noErrorHandler()).process(exchange -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("retry destination");
                    }
                }).to("mock:delivered");
            }
        };
    }
}
