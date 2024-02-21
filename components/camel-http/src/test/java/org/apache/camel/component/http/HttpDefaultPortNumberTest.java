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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.BasicValidationHandler;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.http.HttpMethods.GET;
import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@Disabled("We cannot run this test as default port 80 is not allows on most boxes")
public class HttpDefaultPortNumberTest extends BaseHttpTest {

    private HttpServer localServer;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy()).setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/search", new BasicValidationHandler(GET.name(), null, null, getExpectedContent())).create();
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

    @Test
    public void testHttpConnectionWithTwoRoutesAndOneWithDefaultPort() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("http://localhost/search");
                from("direct:dummy")
                        .to("http://localhost:" + localServer.getLocalPort()
                            + "/search");
            }
        });

        context.start();
        Exchange exchange = template.request("direct:start", null);

        // note: the default portnumber will appear in the error message
        assertRefused(exchange, ":80");
    }

    @Test
    public void testHttpConnectionWithTwoRoutesAndAllPortsSpecified() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("http://localhost:80/search");
                from("direct:dummy")
                        .to("http://localhost:" + localServer.getLocalPort()
                            + "/search");
            }
        });

        context.start();
        Exchange exchange = template.request("direct:start", null);

        //specifying the defaultportnumber helps
        assertRefused(exchange, ":80");
    }

    @Test
    public void testHttpConnectionRefusedStoppedServer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("http://localhost/search");
                from("direct:dummy")
                        .to("http://localhost:" + localServer.getLocalPort()
                            + "/search");
            }
        });

        context.start();
        localServer.stop();

        Exchange exchange = template.request("direct:start", null);

        assertRefused(exchange, ":80");
    }

    @Test
    public void testHttpConnectionRefusedRunningServer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("http://localhost/search");
            }
        });

        context.start();

        //server is runnning, but connecting to other port
        Exchange exchange = template.request("direct:start", null);

        assertRefused(exchange, ":80");
    }

    @Override
    public boolean isUseRouteBuilder() {
        return true;
    }

    private void assertRefused(Exchange exchange, String portExt) {
        Map<String, Object> headers = exchange.getMessage().getHeaders();
        //no http response:
        assertNull(headers.get(Exchange.HTTP_RESPONSE_CODE));
        //and got an exception:
        assertIsInstanceOf(HttpHostConnectException.class, exchange.getException());
        //with message:
        assertEquals("Connection to http://localhost" + portExt + " refused", exchange.getException().getMessage());
    }
}
