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

public class WebsocketTwoRoutesToSameEndpointExampleTest extends CamelTestSupport {
    private int port;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        super.setUp();
    }

    @Test
    public void testWSHttpCallEcho() throws Exception {
        WebsocketTestClient testClient = new WebsocketTestClient("ws://127.0.0.1:" + port + "/bar", 2);
        testClient.connect();

        testClient.sendTextMessage("Beer");
        assertTrue(testClient.await(10, TimeUnit.SECONDS));

        assertEquals(2, testClient.getReceived().size());

        //Cannot guarantee the order in which messages are received
        assertTrue(testClient.getReceived().contains("The bar has Beer"));
        assertTrue(testClient.getReceived().contains("Broadcasting to Bar"));

        testClient.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                WebsocketComponent websocketComponent = (WebsocketComponent) context.getComponent("websocket");
                websocketComponent.setMinThreads(1);
                websocketComponent.setMaxThreads(25);

                from("websocket://localhost:" + port + "/bar")
                        .log(">>> Message received from BAR WebSocket Client : ${body}")
                        .transform().simple("The bar has ${body}")
                        .to("websocket://localhost:" + port + "/bar");

                from("timer://foo?fixedRate=true&period=12000")
                        //Use a period which is longer then the latch await time
                        .setBody(constant("Broadcasting to Bar"))
                        .log(">>> Broadcasting message to Bar WebSocket Client")
                        .to("websocket://localhost:" + port + "/bar?sendToAll=true");
            }
        };
    }
}
