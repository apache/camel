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
package org.apache.camel.component.http;

import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.handler.DrinkValidationHandler;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.jetty.server.Server;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HttpSendDynamicAwareTest extends BaseHttpTest {

    private static final int PORT = AvailablePortFinder.getNextAvailable();
    private Server localServer;

    @Before
    @Override
    public void setUp() throws Exception {
        localServer = new Server(PORT);
        localServer.setHandler(handlers(
            contextHandler("/moes", new DrinkValidationHandler("GET", null, null, "drink")),
            contextHandler("/joes", new DrinkValidationHandler("GET", null, null, "drink"))
        ));
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
                    .toD("http://localhost:" + PORT + "/moes?throwExceptionOnFailure=false&drink=${header.drink}");

                from("direct:joes")
                    .toD("http://localhost:" + PORT + "/joes?throwExceptionOnFailure=false&drink=${header.drink}");

                from("direct:vodka")
                    // these 2 headers should not be in use when using toD
                    .setHeader(Exchange.HTTP_PATH, constant("shouldnotcauseproblems"))
                    .setHeader(Exchange.HTTP_QUERY, constant("drink=coke"))
                    .toD("http://localhost:" + PORT + "/joes?throwExceptionOnFailure=false&drink=vodka");
            }
        };
    }

    @Test
    public void testDynamicAware() throws Exception {
        String out = fluentTemplate.to("direct:moes").withHeader("drink", "beer").request(String.class);
        assertEquals("Drinking beer", out);

        out = fluentTemplate.to("direct:joes").withHeader("drink", "wine").request(String.class);
        assertEquals("Drinking wine", out);

        out = fluentTemplate.to("direct:vodka").clearHeaders().request(String.class);
        assertEquals("Drinking vodka", out);

        // and there should only be one http endpoint as they are both on same host
        boolean found = context.getEndpointMap().containsKey("http://localhost:" + PORT + "?throwExceptionOnFailure=false");
        assertTrue("Should find static uri", found);

        // we only have 3xdirect and 1xhttp
        assertEquals(4, context.getEndpointMap().size());
    }

}
