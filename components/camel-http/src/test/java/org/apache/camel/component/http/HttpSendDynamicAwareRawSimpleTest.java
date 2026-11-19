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

import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StringHelper;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A RAW() query value that toD computes from the message is taken as literal text on the optimised http path: a
 * $simple{} in it is not evaluated.
 */
public class HttpSendDynamicAwareRawSimpleTest extends BaseHttpTest {

    private static final String PROP = "camel.test.httpRawOption." + System.nanoTime();
    private static final String PROP_VALUE = "prop-value-" + System.nanoTime();

    private final AtomicReference<String> receivedQuery = new AtomicReference<>();
    private HttpServer localServer;

    @BeforeAll
    static void setSystemProperty() {
        System.setProperty(PROP, PROP_VALUE);
    }

    @AfterAll
    static void clearSystemProperty() {
        System.clearProperty(PROP);
    }

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/dynamicAware", (request, response, context) -> {
                    receivedQuery.set(StringHelper.after(request.getRequestUri(), "?"));
                    response.setCode(200);
                })
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() throws Exception {
        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:dynamicAware")
                        .toD("http://localhost:" + localServer.getLocalPort() + "/dynamicAware?q=${header.q}");
            }
        };
    }

    @Test
    public void testRawSimpleFromMessageIsLiteral() throws Exception {
        Exchange e = fluentTemplate
                .to("direct:dynamicAware")
                .withHeader("q", "RAW($simple{sys." + PROP + "})")
                .send();

        String expected = "q=%24simple%7Bsys." + PROP + "%7D";
        assertEquals(expected, e.getIn().getHeader(Exchange.HTTP_QUERY));
        assertEquals(expected, receivedQuery.get());
    }
}
