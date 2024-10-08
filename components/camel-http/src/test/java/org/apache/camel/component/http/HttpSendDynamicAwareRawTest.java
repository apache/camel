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

import org.apache.camel.BindToRegistry;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.DrinkValidationHandler;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpSendDynamicAwareRawTest extends BaseHttpTest {

    private HttpServer localServer;

    @BindToRegistry("pw")
    private String pw = "se+%ret.$";

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost").setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/moes", new DrinkValidationHandler(GET.name(), "drink=beer&password=se+%ret", null, "drink"))
                .register("/joes",
                        new DrinkValidationHandler(GET.name(), "drink=wine&password=se+%ret.$", null, "drink"))
                .create();
        localServer.start();
    }

    @Override
    public void cleanupResources() {

        if (localServer != null) {
            localServer.stop();
        }
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:moes")
                        .toD("http://localhost:" + localServer.getLocalPort()
                             + "/moes?throwExceptionOnFailure=false&drink=${header.drink}&password=RAW(se+%ret)");

                from("direct:joes")
                        .toD("http://localhost:" + localServer.getLocalPort()
                             + "/joes?throwExceptionOnFailure=false&drink=${header.drink}&password=RAW(${ref:pw})");
            }
        };
    }

    @Test
    public void testDynamicAwareRaw() {
        String out = fluentTemplate.to("direct:moes").withHeader("drink", "beer").request(String.class);
        assertEquals("Drinking beer", out);

        out = fluentTemplate.to("direct:joes").withHeader("drink", "wine").request(String.class);
        assertEquals("Drinking wine", out);

        // and there should only be one http endpoint as they are both on same host
        boolean found = context.getEndpointRegistry()
                .containsKey("http://localhost:" + localServer.getLocalPort() + "?throwExceptionOnFailure=false");
        assertTrue(found, "Should find static uri");

        // we only have 2xdirect and 1xhttp
        assertEquals(3, context.getEndpointRegistry().size());
    }

}
