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
package org.apache.camel.maven;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.parser.XmlRouteParser;
import org.apache.camel.parser.model.CamelEndpointDetails;
import org.apache.camel.parser.model.CamelNodeDetails;
import org.apache.camel.parser.model.CamelRouteDetails;
import org.apache.camel.parser.model.CamelSimpleExpressionDetails;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25591: the report goals read Java routes with the Java DSL parser, into the same details camel-route-parser
 * gives.
 */
class JavaRouteModelReaderTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    private static final String ORDERS = """
            package com.foo.orders;

            import org.apache.camel.builder.RouteBuilder;

            public class OrderRoute extends RouteBuilder {
                private static final String AUDIT = "seda:audit";

                @Override
                public void configure() {
                    from("direct:orders").routeId("orders")
                        .filter(simple("${header.amount} > 100"))
                            .to(AUDIT)
                        .end()
                        .choice()
                            .when(simple("${header.vip}"))
                                .setBody(simple("vip ${body}"))
                                .to("seda:vip")
                            .otherwise()
                                .log("normal")
                        .end()
                        .toD("direct:${header.next}")
                        .to(helper());

                    from("timer:tick")
                        .pollEnrich("seda:audit")
                        .to("log:tick");
                }
            }
            """;

    /** The same routes in the XML DSL, the tree camel-route-parser builds for them. */
    private static final String ORDERS_XML = """
            <routes xmlns="http://camel.apache.org/schema/spring">
              <route id="orders">
                <from uri="direct:orders"/>
                <filter>
                  <simple>${header.amount} &gt; 100</simple>
                  <to uri="seda:audit"/>
                </filter>
                <choice>
                  <when>
                    <simple>${header.vip}</simple>
                    <setBody><simple>vip ${body}</simple></setBody>
                    <to uri="seda:vip"/>
                  </when>
                  <otherwise>
                    <log message="normal"/>
                  </otherwise>
                </choice>
                <toD uri="direct:${header.next}"/>
                <to uri="direct:helper"/>
              </route>
              <route>
                <from uri="timer:tick"/>
                <pollEnrich><constant>seda:audit</constant></pollEnrich>
                <to uri="log:tick"/>
              </route>
            </routes>
            """;

    private static JavaRouteModelReader reader() {
        return JavaRouteModelReader.read(CATALOG, "src/main/java/com/foo/orders/OrderRoute.java", ORDERS);
    }

    @Test
    void routeTreesAreTheTreesOfTheXmlDsl() {
        List<CamelNodeDetails> java = reader().routeTrees();
        List<CamelNodeDetails> xml = XmlRouteParser.parseXmlRouteTree(
                new ByteArrayInputStream(ORDERS_XML.getBytes(StandardCharsets.UTF_8)), ".", "orders.xml");

        assertEquals(2, java.size());
        assertEquals(withoutLines(xml.get(0).dump(0)), withoutLines(java.get(0).dump(0)));
        assertEquals(withoutLines(xml.get(1).dump(0)), withoutLines(java.get(1).dump(0)));

        CamelNodeDetails from = java.get(0);
        assertEquals("orders", from.getRouteId());
        assertEquals("10", from.getLineNumber());
        assertEquals("com.foo.orders.OrderRoute", from.getClassName());
        assertEquals("configure", from.getMethodName());
        // the filter is the first step, on its own line
        assertEquals("11", from.getOutputs().get(0).getLineNumber());
        assertNull(java.get(1).getRouteId());
    }

    @Test
    void endpointsWithConsumersAndProducers() {
        List<CamelEndpointDetails> endpoints = new ArrayList<>();
        List<String> unparsable = new ArrayList<>();
        reader().endpoints(endpoints, unparsable);

        List<String> uris = endpoints.stream().map(CamelEndpointDetails::getEndpointUri).toList();
        assertTrue(uris.containsAll(List.of(
                "direct:orders", "seda:audit", "seda:vip", "direct:${header.next}", "timer:tick", "log:tick")), uris::toString);
        CamelEndpointDetails orders = endpoints.stream().filter(e -> "direct:orders".equals(e.getEndpointUri()))
                .findFirst().orElseThrow();
        assertTrue(orders.isConsumerOnly());
        assertFalse(orders.isProducerOnly());
        assertEquals("direct", orders.getEndpointComponentName());
        assertEquals("10", orders.getLineNumber());
        // the pollEnrich consumes from seda:audit, the to in the filter produces to it (the constant is resolved)
        assertTrue(endpoints.stream().anyMatch(e -> "seda:audit".equals(e.getEndpointUri()) && e.isConsumerOnly()));
        assertTrue(endpoints.stream().anyMatch(e -> "seda:audit".equals(e.getEndpointUri()) && e.isProducerOnly()));
        // to(helper()) is only known at runtime
        assertEquals(1, unparsable.size(), unparsable::toString);
    }

    @Test
    void simpleExpressionsAndPredicates() {
        List<CamelSimpleExpressionDetails> expressions = new ArrayList<>();
        reader().simpleExpressions(expressions);

        CamelSimpleExpressionDetails filter = find(expressions, "${header.amount} > 100");
        assertTrue(filter.isPredicate());
        assertEquals("11", filter.getLineNumber());
        assertTrue(find(expressions, "${header.vip}").isPredicate());
        CamelSimpleExpressionDetails body = find(expressions, "vip ${body}");
        assertFalse(body.isPredicate());
        assertTrue(body.isExpression());
    }

    @Test
    void routeIds() {
        List<CamelRouteDetails> routes = new ArrayList<>();
        reader().routeIds(routes);

        assertEquals(1, routes.size());
        assertEquals("orders", routes.get(0).getRouteId());
        assertEquals("10", routes.get(0).getLineNumber());
        assertEquals("com.foo.orders.OrderRoute", routes.get(0).getClassName());
    }

    @Test
    void aValueOnlyKnownAtRuntimeIsAPlaceholder() {
        JavaRouteModelReader reader = JavaRouteModelReader.read(CATALOG, "HttpRoute.java", """
                import org.apache.camel.EndpointInject;
                import org.apache.camel.builder.RouteBuilder;

                public class HttpRoute extends RouteBuilder {
                    protected int port;

                    @EndpointInject("mock:result")
                    protected Object result;

                    private String ftp = "ftp:localhost:21/inbox?delay=" + 5000 + "&passiveMode=true";

                    @Override
                    public void configure() {
                        from("jetty:http://localhost:" + port + "/sender")
                            .to("http://localhost:" + port + "/other?bridgeEndpoint=true")
                            .to(result);
                        from(ftp).to("log:ftp");
                    }
                }
                """);
        List<CamelEndpointDetails> endpoints = new ArrayList<>();
        List<String> unparsable = new ArrayList<>();
        reader.endpoints(endpoints, unparsable);

        List<String> uris = endpoints.stream().map(CamelEndpointDetails::getEndpointUri).toList();
        // as camel-route-parser did: the component and options of the uri are still validated
        assertTrue(uris.contains("jetty:http://localhost:{{port}}/sender"), uris::toString);
        assertTrue(uris.contains("http://localhost:{{port}}/other?bridgeEndpoint=true"), uris::toString);
        // the to of the injected endpoint in the route, and its field
        assertEquals(List.of("16", "7"), endpoints.stream().filter(e -> "mock:result".equals(e.getEndpointUri()))
                .map(CamelEndpointDetails::getLineNumber).toList());
        // a field with a string initializer
        assertTrue(uris.contains("ftp:localhost:21/inbox?delay={{5000}}&passiveMode=true"), uris::toString);
        assertTrue(unparsable.isEmpty(), unparsable::toString);

        assertNull(JavaRouteModelReader.withPlaceholders("?{scheme()}:foo"));
    }

    @Test
    void aClassWithoutRoutes() {
        JavaRouteModelReader reader = JavaRouteModelReader.read(CATALOG, "Util.java", """
                public class Util {
                    public static String name() {
                        return "util";
                    }
                }
                """);
        assertFalse(reader.hasRoutes());
        assertTrue(reader.routeTrees().isEmpty());
        assertEquals("Util", reader.className());
    }

    private static CamelSimpleExpressionDetails find(List<CamelSimpleExpressionDetails> list, String simple) {
        return list.stream().filter(e -> simple.equals(e.getSimple())).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no " + simple + " in " + list.stream()
                                .map(CamelSimpleExpressionDetails::getSimple).toList()));
    }

    private static String withoutLines(String dump) {
        return dump.replaceAll("(?m)^[^\\t]*\\t", "");
    }
}
