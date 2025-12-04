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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.impl.bootstrap.HttpServer;
import org.apache.hc.core5.http.impl.bootstrap.ServerBootstrap;
import org.junit.jupiter.api.Test;

public class HttpSkipControlHeadersTest extends BaseHttpTest {

    private HttpServer localServer;

    @Override
    public void setupResources() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setCanonicalHostName("localhost")
                .setHttpProcessor(getBasicHttpProcessor())
                .setConnectionReuseStrategy(getConnectionReuseStrategy())
                .setResponseFactory(getHttpResponseFactory())
                .setSslContext(getSSLContext())
                .register("/hello", (request, response, context) -> {
                    Header header = request.getFirstHeader("cheese");
                    assertNotNull(header);
                    assertEquals("Gauda", header.getValue());

                    response.setCode(HttpStatus.SC_OK);
                    response.setHeader("MyApp", "dude");
                    response.setHeader(Exchange.TO_ENDPOINT, "foo");
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

    @Test
    public void testSipControlHeaders() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .setHeader(Exchange.HTTP_PATH, constant("/bye"))
                        .to("http://localhost:" + localServer.getLocalPort() + "/hello?skipControlHeaders=true");
            }
        });
        context.start();

        Exchange out = template.request("direct:start", exchange -> {
            exchange.getIn().setHeader("cheese", "Gauda");
            exchange.getIn().setBody("This is content");
        });

        assertNotNull(out);
        assertFalse(out.isFailed(), "Should not fail");
        assertEquals("dude", out.getMessage().getHeader("MyApp"));
        assertNull(out.getMessage().getHeader(Exchange.TO_ENDPOINT));
    }
}
