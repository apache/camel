/**
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
package org.apache.camel.component.http4;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.handler.DrinkValidationHandler;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpSendDynamicAwareTest extends BaseHttpTest {

    private HttpServer localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
            setHttpProcessor(getBasicHttpProcessor()).
            setConnectionReuseStrategy(getConnectionReuseStrategy()).
            setResponseFactory(getHttpResponseFactory()).
            setExpectationVerifier(getHttpExpectationVerifier()).
            setSslContext(getSSLContext()).
            registerHandler("/moes", new DrinkValidationHandler("GET", null, null, "drink")).
            registerHandler("/joes", new DrinkValidationHandler("GET", null, null, "drink")).
            create();
        localServer.start();

        super.setUp();
    }

    @After
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
                from("direct:moes")
                    .toD("http4://localhost:" + localServer.getLocalPort() + "/moes?throwExceptionOnFailure=false&drink=${header.drink}");

                from("direct:joes")
                    .toD("http4://localhost:" + localServer.getLocalPort() + "/joes?throwExceptionOnFailure=false&drink=${header.drink}");
            }
        };
    }

    @Test
    public void testDynamicAware() throws Exception {
        String out = fluentTemplate.to("direct:moes").withHeader("drink", "beer").request(String.class);
        assertEquals("Drinking beer", out);

        out = fluentTemplate.to("direct:joes").withHeader("drink", "wine").request(String.class);
        assertEquals("Drinking wine", out);

        // and there should only be one http endpoint as they are both on same host
        boolean found = context.getEndpointMap().containsKey("http4://localhost:" + localServer.getLocalPort() + "?throwExceptionOnFailure=false");
        assertTrue("Should find static uri", found);

        // we only have 2xdirect and 1xhttp4
        assertEquals(3, context.getEndpointMap().size());
    }

}
