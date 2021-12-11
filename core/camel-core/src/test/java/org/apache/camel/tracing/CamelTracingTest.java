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
package org.apache.camel.tracing;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.engine.DefaultTracer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CamelTracingTest extends ContextTestSupport {

    private static final String ROUTE_ID_A = "a";
    private static final String ROUTE_ID_B = "b";
    private static final String ROUTE_ID_C = "c";
    private static final String ROUTE_ID_D = "d";

    private static RouteEnv currentRoute;

    // to disable super.setUp()
    @Override
    @BeforeEach
    public void setUp() {
    }

    @Test
    public void testAllCases() throws Exception {
        testRouteWithTracingEnabledAndLargeGroups();
        testRouteWithTracingEnabledAndThinOrDisabledGroups();
        testRouteWithTracingEnabledAndDisabledAllGroups();
        testRouteWithTracingDisabled();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        context.setTracing(true);
        return currentRoute.routeBuilder;
    }

    private void testRouteWithTracingEnabledAndLargeGroups() throws Exception {
        testRoute(RouteEnv.ROUTE_A);
    }

    private void testRouteWithTracingEnabledAndThinOrDisabledGroups() throws Exception {
        testRoute(RouteEnv.ROUTE_B);
    }

    private void testRouteWithTracingEnabledAndDisabledAllGroups() throws Exception {
        testRoute(RouteEnv.ROUTE_C);
    }

    private void testRouteWithTracingDisabled() throws Exception {
        testRoute(RouteEnv.ROUTE_D);
    }

    private void testRoute(RouteEnv routeEnv) throws Exception {
        currentRoute = routeEnv;
        RouteTracingListAppender.clearLogs();
        super.setUp();
        getMockEndpoint(routeEnv.mockEndpoint).expectedMessageCount(1);
        template.sendBody(routeEnv.startEndpoint, "test");
        assertMockEndpointsSatisfied();
        assertLogGroups(routeEnv.isTracingEnabled, routeEnv.routeId, routeEnv.groupRouteIdLength,
                routeEnv.groupRouteLabelLength);
    }

    private static RouteBuilder createRouteWithTracing(RouteEnv routeEnv) {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(routeEnv.startEndpoint)
                        .routeId(routeEnv.routeId)
                        .tracing(String.valueOf(routeEnv.isTracingEnabled), routeEnv.groupRouteIdLength,
                                routeEnv.groupRouteLabelLength)
                        .log("message: ${body}")
                        .to(routeEnv.mockEndpoint);
            }
        };
    }

    private static RouteBuilder createRouteWithTracingDefaults(RouteEnv routeEnv) {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(routeEnv.startEndpoint)
                        .routeId(routeEnv.routeId)
                        .tracing(String.valueOf(routeEnv.isTracingEnabled), DefaultTracer.GROUP_ROUTE_ID_LENGTH,
                                DefaultTracer.GROUP_LABEL_LENGTH)
                        .log("message: ${body}")
                        .to(routeEnv.mockEndpoint);
            }
        };
    }

    private static RouteBuilder createRouteWithoutTracing(RouteEnv routeEnv) {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(routeEnv.startEndpoint)
                        .routeId(routeEnv.routeId)
                        .tracing("false")
                        .log("message: ${body}")
                        .to(routeEnv.mockEndpoint);
            }
        };
    }

    private void assertLogGroups(boolean isTracingEnabled, String routeId, int group1length, int group2length) {
        if (isTracingEnabled) {
            Assertions.assertTrue(RouteTracingListAppender.getLogs().size() > 0, "Log is empty");
        } else {
            Assertions.assertEquals(0, RouteTracingListAppender.getLogs().size(), "Log is not empty");
        }
        for (String log : RouteTracingListAppender.getLogs()) {
            int positionExchange = log.indexOf("Exchange[");
            String tracing = log.substring(0, positionExchange - 1);
            if (group1length == 0 && group2length == 0) {
                Assertions.assertEquals(0, tracing.trim().length());
            } else {
                String group1expected = String.format("%s%s", routeId, repeat(" ", group1length)).substring(0, group1length);
                String group2expected = String.format(".{%d}", group2length);

                Matcher group1matcher = Pattern.compile("\\[(" + group1expected + ")\\]").matcher(tracing);
                Assertions.assertTrue(group1matcher.find(),
                        String.format("Log <%s> does not contains group 1 <%s>", tracing, group1expected));
                String group1 = group1matcher.group(1);

                Assertions.assertEquals(group1length, group1.length());

                if (group2length > 0) {
                    tracing = tracing.replace("[" + group1 + "]", "");

                    Matcher group2matcher = Pattern.compile("\\[(" + group2expected + ")\\]").matcher(tracing);
                    Assertions.assertTrue(group2matcher.find(),
                            String.format("Log <%s> does not contains group 2 <%s>", tracing, group2expected));
                    String group2 = group2matcher.group(1);

                    Assertions.assertEquals(group2length, group2.length());
                }
            }
        }
    }

    private String repeat(String symbol, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(symbol);
        }
        return sb.toString();
    }

    enum RouteEnv {
        ROUTE_A(ROUTE_ID_A, true, 150, 50,
                CamelTracingTest::createRouteWithTracing, "direct:a", "mock:a"),
        ROUTE_B(ROUTE_ID_B, true, 2, 0,
                CamelTracingTest::createRouteWithTracing, "direct:b", "mock:b"),
        ROUTE_C(ROUTE_ID_C, true,
                DefaultTracer.GROUP_ROUTE_ID_LENGTH, DefaultTracer.GROUP_LABEL_LENGTH,
                CamelTracingTest::createRouteWithTracingDefaults, "direct:c", "mock:c"),
        ROUTE_D(ROUTE_ID_D, false, 0, 0,
                CamelTracingTest::createRouteWithoutTracing, "direct:d", "mock:d");

        final String routeId;
        final boolean isTracingEnabled;
        final int groupRouteIdLength;
        final int groupRouteLabelLength;
        final RouteBuilder routeBuilder;
        final String startEndpoint;
        final String mockEndpoint;

        RouteEnv(String routeId, boolean isTracingEnabled, int groupRouteIdLength, int groupRouteLabelLength,
                 Function<RouteEnv, RouteBuilder> routeBuilderInitializer, String startEndpoint, String mockEndpoint) {
            this.routeId = routeId;
            this.isTracingEnabled = isTracingEnabled;
            this.groupRouteIdLength = groupRouteIdLength;
            this.groupRouteLabelLength = groupRouteLabelLength;
            this.routeBuilder = routeBuilderInitializer.apply(this);
            this.startEndpoint = startEndpoint;
            this.mockEndpoint = mockEndpoint;
        }
    }

}
