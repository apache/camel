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
package org.apache.camel.component.websocket;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebsocketRouteExampleTest extends CamelTestSupport {

    private int port;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        super.setUp();
    }

    @Test
    public void testWSHttpCall() throws Exception {
        WebsocketTestClient testClient = new WebsocketTestClient("ws://localhost:" + port + "/echo", 1);
        testClient.connect();

        testClient.sendTextMessage("Beer");
        assertTrue(testClient.await(10, TimeUnit.SECONDS));

        assertEquals(1, testClient.getReceived().size());
        assertEquals("BeerBeer", testClient.getReceived().get(0));

        testClient.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                WebsocketComponent websocketComponent = (WebsocketComponent) context.getComponent("websocket");
                websocketComponent.setPort(port);
                websocketComponent.setMinThreads(1);
                websocketComponent.setMaxThreads(25);

                // START SNIPPET: e1
                // expose a echo websocket client, that sends back an echo
                from("websocket://echo")
                        .log(">>> Message received from WebSocket Client : ${body}")
                        .transform().simple("${body}${body}")
                        // send back to the client, by sending the message to the same endpoint
                        // this is needed as by default messages is InOnly
                        // and we will by default send back to the current client using the provided connection key
                        .to("websocket://echo");
                // END SNIPPET: e1
            }
        };
    }
}
