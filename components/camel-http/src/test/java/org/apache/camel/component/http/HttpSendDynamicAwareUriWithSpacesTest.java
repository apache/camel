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

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpSendDynamicAwareUriWithSpacesTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/users/*", new BasicValidationHandler("GET", null, null, "a user")).create();
        localServer.start();

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

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:usersDrink")
                        .toD("http:localhost:" + localServer.getLocalPort()
                             + "/users/${exchangeProperty.user}");
            }
        };
    }

    @Test
    public void testDynamicAware() throws Exception {
        Exchange out = fluentTemplate.to("direct:usersDrink")
                .withExchange(ExchangeBuilder.anExchange(context).withProperty("user", "joes moes").build()).send();
        assertEquals("a user", out.getMessage().getBody(String.class));

        out = fluentTemplate.to("direct:usersDrink")
                .withExchange(ExchangeBuilder.anExchange(context).withProperty("user", "moes joes").build()).send();
        assertEquals("a user", out.getMessage().getBody(String.class));

        // and there should only be one http endpoint as they are both on same host
        Map endpointMap = context.getEndpointRegistry();
        assertEquals(2, endpointMap.size());
        assertTrue(endpointMap.containsKey("http://localhost:" + localServer.getLocalPort()), "Should find static uri");
        assertTrue(endpointMap.containsKey("direct://usersDrink"), "Should find direct");
    }

}
