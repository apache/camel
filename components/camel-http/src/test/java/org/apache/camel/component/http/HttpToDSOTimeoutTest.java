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
package org.apache.camel.component.http;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;

public class HttpToDSOTimeoutTest extends BaseHttpTest {

    private HttpServer localServer;

    private String baseUrl;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/foo",
                        new BasicValidationHandler(
                                "/foo", GET.name(), null, null,
                                getExpectedContent()))
                .register("/bar",
                        new BasicValidationHandler(
                                "/bar", GET.name(), null, null,
                                getExpectedContent()))
                .register("/baz",
                        new BasicValidationHandler(
                                "/baz", GET.name(), null, null,
                                getExpectedContent()))
                .create();
        localServer.start();

        baseUrl = "http://localhost:" + localServer.getLocalPort();

        super.setUp();
    }

    @AfterEach
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Test
    public void httpTo() throws Exception {
        Exchange exchange = template.request("direct:to",
                exchange1 -> {
                });
        assertExchange(exchange);
    }

    @Test
    public void httpToD() throws Exception {
        Exchange exchange = template.request("direct:toD",
                exchange1 -> {
                });
        assertExchange(exchange);
    }

    @Test
    public void httpToDoff() throws Exception {
        Exchange exchange = template.request("direct:toDoff",
                exchange1 -> {
                });
        assertExchange(exchange);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:to")
                        .to(baseUrl + "/foo?httpClient.responseTimeout=5000");

                from("direct:toD")
                        .toD(baseUrl + "/bar?httpClient.responseTimeout=5000");

                from("direct:toDoff")
                        .toD().allowOptimisedComponents(false).uri(baseUrl + "/baz?httpClient.responseTimeout=5000");
            }
        };
    }
}
