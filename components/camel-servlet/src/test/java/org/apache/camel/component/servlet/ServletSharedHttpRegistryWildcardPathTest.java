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
package org.apache.camel.component.servlet;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.http.common.DefaultHttpRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.RestConsumerContextPathMatcher;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A servlet component with an httpRegistry shared by two servlets of different names: each consumer is connected to
 * (and registers its path with) the servlet of its name only, but disconnecting it goes to both servlets. The wildcard
 * path of a consumer must stay registered while another consumer of the same path is still started.
 */
class ServletSharedHttpRegistryWildcardPathTest extends CamelTestSupport {

    // the matcher keeps the paths in static state: use paths no other test registers
    private static final String FILES = "/sharedregistry/files/*";
    private static final String DUPLICATE = "/sharedregistry/duplicate/*";

    private final DefaultHttpRegistry registry = new DefaultHttpRegistry();
    private final CamelHttpTransportServlet servletA = new CamelHttpTransportServlet();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        servletA.setServletName("a");
        registry.register(servletA);
        CamelHttpTransportServlet servletB = new CamelHttpTransportServlet();
        servletB.setServletName("b");
        registry.register(servletB);
        context.getComponent("servlet", ServletComponent.class).setHttpRegistry(registry);

        return context;
    }

    @Test
    void stoppingOneConsumerKeepsTheWildcardPathOfTheOther() throws Exception {
        assertNotNull(match(FILES, "/sharedregistry/files/a"));

        context.getRouteController().stopRoute("get");

        assertNotNull(match(FILES, "/sharedregistry/files/a"),
                "the wildcard path of the POST consumer, still started, must match");

        context.getRouteController().stopRoute("post");

        assertNull(match(FILES, "/sharedregistry/files/a"));
    }

    @Test
    void aConsumerThatFailsAsDuplicateDoesNotRemoveTheOther() throws Exception {
        String uri = "servlet:" + DUPLICATE + "?servletName=a";
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("first").setBody(constant("first"));
            }
        });
        assertNotNull(match(DUPLICATE, "/sharedregistry/duplicate/a"));

        // the same endpoint in another CamelContext with the same registry: the servlet refuses it as a duplicate, and
        // the consumer that failed to connect is disconnected again
        try (CamelContext other = new DefaultCamelContext()) {
            other.getComponent("servlet", ServletComponent.class).setHttpRegistry(registry);
            other.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from(uri).routeId("second").setBody(constant("second"));
                }
            });
            assertThrows(Exception.class, other::start);
        }

        assertNotNull(match(DUPLICATE, "/sharedregistry/duplicate/a"),
                "the wildcard path of the first consumer, still started, must match");
        assertEquals(1, servletA.getConsumers().values().stream()
                .filter(c -> c.getEndpoint().getEndpointUri().contains("duplicate")).count());

        context.getRouteController().stopRoute("first");
        assertNull(match(DUPLICATE, "/sharedregistry/duplicate/a"));
    }

    private static RestConsumerContextPathMatcher.ConsumerPath<String> match(String consumerPath, String requestPath) {
        return RestConsumerContextPathMatcher.matchBestPath("POST", requestPath,
                List.of(new WildcardPath(consumerPath)));
    }

    private record WildcardPath(String getConsumerPath) implements RestConsumerContextPathMatcher.ConsumerPath<String> {

        @Override
        public String getRestrictMethod() {
            return null;
        }

        @Override
        public String getConsumer() {
            return getConsumerPath;
        }

        @Override
        public boolean isMatchOnUriPrefix() {
            return false;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("servlet:" + FILES + "?servletName=a&httpMethodRestrict=GET").routeId("get").setBody(constant("get"));
                from("servlet:" + FILES + "?servletName=a&httpMethodRestrict=POST").routeId("post")
                        .setBody(constant("post"));
            }
        };
    }
}
