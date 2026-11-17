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
package org.apache.camel.builder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The options of an EIP that take an expression or predicate in the Java DSL (aggregate completionSize,
 * completionTimeout and completionPredicate, onException handled and retryWhile) are given to each route created from a
 * route template, so each route uses its own template parameters.
 */
public class RouteTemplateExpressionOptionPerRouteTest extends ContextTestSupport {

    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private void addRoutes(RouteBuilder builder) throws Exception {
        context.addRoutes(builder);
        context.addTemplatedRoutes(builder);
        context.start();
    }

    private void sendSix() {
        for (int i = 1; i <= 6; i++) {
            template.sendBody("direct:a", "a" + i);
        }
        for (int i = 1; i <= 6; i++) {
            template.sendBody("direct:b", "b" + i);
        }
    }

    private static void assertGroupSize(MockEndpoint mock, int size) {
        for (Exchange exchange : mock.getReceivedExchanges()) {
            assertEquals(size, exchange.getIn().getBody(List.class).size(), "Group size on " + mock.getEndpointUri());
        }
    }

    private void assertSizeTwoOnAAndThreeOnB() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        MockEndpoint b = getMockEndpoint("mock:b");
        a.expectedMessageCount(3);
        b.expectedMessageCount(2);

        sendSix();

        MockEndpoint.assertIsSatisfied(context);
        assertGroupSize(a, 2);
        assertGroupSize(b, 3);
    }

    @Test
    public void testCompletionSizeExpression() throws Exception {
        addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("t").templateParameter("name").templateParameter("size")
                        .from("direct:{{name}}")
                        .aggregate(constant(true), new GroupedBodyAggregationStrategy())
                        .completionSize(simple("{{size}}"))
                        .to("mock:{{name}}");

                templatedRoute("t").routeId("a").parameter("name", "a").parameter("size", "2");
                templatedRoute("t").routeId("b").parameter("name", "b").parameter("size", "3");
            }
        });

        assertSizeTwoOnAAndThreeOnB();
    }

    @Test
    public void testCompletionPredicate() throws Exception {
        addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("t").templateParameter("name").templateParameter("size")
                        .from("direct:{{name}}")
                        .aggregate(constant(true), new GroupedBodyAggregationStrategy())
                        .completionPredicate(simple("${exchangeProperty.CamelAggregatedSize} == {{size}}"))
                        .to("mock:{{name}}");

                templatedRoute("t").routeId("a").parameter("name", "a").parameter("size", "2");
                templatedRoute("t").routeId("b").parameter("name", "b").parameter("size", "3");
            }
        });

        assertSizeTwoOnAAndThreeOnB();
    }

    @Test
    public void testCompletionSizeString() throws Exception {
        // the String option was already resolved for each route
        addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("t").templateParameter("name").templateParameter("size")
                        .from("direct:{{name}}")
                        .aggregate(constant(true), new GroupedBodyAggregationStrategy())
                        .completionSize("{{size}}")
                        .to("mock:{{name}}");

                templatedRoute("t").routeId("a").parameter("name", "a").parameter("size", "2");
                templatedRoute("t").routeId("b").parameter("name", "b").parameter("size", "3");
            }
        });

        assertSizeTwoOnAAndThreeOnB();
    }

    @Test
    public void testCompletionTimeoutExpression() throws Exception {
        addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("t").templateParameter("name").templateParameter("timeout")
                        .from("direct:{{name}}")
                        .aggregate(constant(true), new GroupedBodyAggregationStrategy())
                        .completionTimeout(simple("{{timeout}}")).completionTimeoutCheckerInterval(10)
                        .to("mock:{{name}}");

                templatedRoute("t").routeId("a").parameter("name", "a").parameter("timeout", "600000");
                templatedRoute("t").routeId("b").parameter("name", "b").parameter("timeout", "100");
            }
        });

        // route b times out its group after 100 millis, not after the 10 minutes of route a
        MockEndpoint b = getMockEndpoint("mock:b");
        b.expectedMessageCount(1);

        template.sendBody("direct:a", "a1");
        template.sendBody("direct:b", "b1");
        template.sendBody("direct:b", "b2");

        MockEndpoint.assertIsSatisfied(context);
        assertGroupSize(b, 2);
        assertEquals(0, getMockEndpoint("mock:a").getReceivedCounter());
    }

    @Test
    public void testOnExceptionRetryWhile() throws Exception {
        addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("t").templateParameter("name").templateParameter("retries")
                        .from("direct:{{name}}")
                        .onException(IllegalStateException.class)
                            .retryWhile(simple("${header.CamelRedeliveryCounter} <= {{retries}}")).redeliveryDelay(0)
                            .handled(true)
                            .to("mock:{{name}}")
                        .end()
                        .process(exchange -> {
                            attempts.computeIfAbsent(exchange.getFromRouteId(), k -> new AtomicInteger()).incrementAndGet();
                            throw new IllegalStateException("Forced");
                        });

                templatedRoute("t").routeId("a").parameter("name", "a").parameter("retries", "1");
                templatedRoute("t").routeId("b").parameter("name", "b").parameter("retries", "3");
            }
        });

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        template.sendBody("direct:a", "a");
        template.sendBody("direct:b", "b");

        MockEndpoint.assertIsSatisfied(context);
        // the first attempt and then the retries
        assertEquals(2, attempts.get("a").get());
        assertEquals(4, attempts.get("b").get());
    }

    @Test
    public void testOnExceptionHandled() throws Exception {
        addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("t").templateParameter("name").templateParameter("handled")
                        .from("direct:{{name}}")
                        .onException(IllegalStateException.class)
                            .handled((Predicate) simple("{{handled}}"))
                            .to("mock:{{name}}")
                        .end()
                        .throwException(IllegalStateException.class, "Forced");

                templatedRoute("t").routeId("a").parameter("name", "a").parameter("handled", "true");
                templatedRoute("t").routeId("b").parameter("name", "b").parameter("handled", "false");
            }
        });

        getMockEndpoint("mock:a").expectedMessageCount(1);
        getMockEndpoint("mock:b").expectedMessageCount(1);

        Exchange a = template.send("direct:a", e -> e.getMessage().setBody("a"));
        Exchange b = template.send("direct:b", e -> e.getMessage().setBody("b"));

        MockEndpoint.assertIsSatisfied(context);
        assertFalse(a.isFailed(), "Route a handles the exception");
        assertTrue(b.isFailed(), "Route b does not handle the exception");
    }

    @Test
    public void testTemplateKeepsTheExpressionModel() throws Exception {
        addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                routeTemplate("t").templateParameter("name").templateParameter("size")
                        .from("direct:{{name}}")
                        .aggregate(constant(true), new GroupedBodyAggregationStrategy())
                        .completionSize(simple("{{size}}"))
                        .to("mock:{{name}}");
            }
        });

        RouteTemplateDefinition template = context.getRouteTemplateDefinition("t");
        AggregateDefinition aggregate = (AggregateDefinition) template.getRoute().getOutputs().get(0);
        // the expression is the simple language with the text as given, so a copy of the template has its own
        assertEquals("simple", aggregate.getCompletionSizeExpression().getExpressionType().getLanguage());
        assertEquals("{{size}}", aggregate.getCompletionSizeExpression().getExpressionType().getExpression());
    }
}
