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
package org.apache.camel.component.graphql;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.BaseHttpTest;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.camel.http.base.HttpHeaderFilterStrategy;
import org.apache.camel.spi.Registry;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verify that registered 'HttpHeaderFilterStrategy' instances can be used to allow specific headers.
 */
public class GraphqlHeaderFilterStrategyTest extends BaseHttpTest {

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .register("/graphql",
                        new BasicValidationHandler(POST.name(), null, null, getExpectedContent()))
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void allowConnectionCloseHeader() {
        Exchange exchange = template.request(
                "direct:start1",
                exchange1 -> {
                    exchange1.getIn().setHeader("connection", HeaderElements.CLOSE);
                });

        assertEquals(HeaderElements.CLOSE, exchange.getMessage().getHeader("connection"));
        assertExchange(exchange);
    }

    @Test
    public void allowWarningHeader() {
        Exchange exchange2 = template.request(
                "direct:start2",
                exchange1 -> {
                    exchange1.getIn().setHeader("warning", "test warning");
                });

        assertEquals("test warning", exchange2.getMessage().getHeader("warning"));
        assertExchange(exchange2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // multiple routes to verify registration of multiple 'headerFilterStrategy' instances
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start1")
                        .to("graphql://http://localhost:" + localServer.getLocalPort()
                            + "/graphql?query={books{id name}}"
                            + "&headerFilterStrategy=#allowConnectionCloseFilter");

                from("direct:start2")
                        .to("graphql://http://localhost:" + localServer.getLocalPort()
                            + "/graphql?query={books{id name}}"
                            + "&headerFilterStrategy=#allowWarningFilter");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("allowConnectionCloseFilter", new AllowConnectionCloseHeader());
        registry.bind("allowWarningFilter", new AllowWarningHeader());
    }

    private static class AllowConnectionCloseHeader extends HttpHeaderFilterStrategy {
        @Override
        protected void initialize() {
            super.initialize();
            getOutFilter().remove("connection");
        }
    }

    private static class AllowWarningHeader extends HttpHeaderFilterStrategy {
        @Override
        protected void initialize() {
            super.initialize();
            getOutFilter().remove("warning");
        }
    }
}
