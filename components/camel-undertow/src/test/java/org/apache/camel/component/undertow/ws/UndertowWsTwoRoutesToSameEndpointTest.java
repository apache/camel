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
package org.apache.camel.component.undertow.ws;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UndertowWsTwoRoutesToSameEndpointTest extends BaseUndertowTest {
    @Test
    public void testWSHttpCallEcho() throws Exception {
        WebsocketTestClient testClient = new WebsocketTestClient("ws://localhost:" + getPort() + "/bar", 2);
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
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                final int port = getPort();
                from("undertow:ws://localhost:" + port + "/bar")
                        .log(">>> Message received from BAR WebSocket Client : ${body}")
                        .transform().simple("The bar has ${body}")
                        .to("undertow:ws://localhost:" + port + "/bar");

                from("timer://foo?fixedRate=true&period=12000")
                        //Use a period which is longer then the latch await time
                        .setBody(constant("Broadcasting to Bar"))
                        .log(">>> Broadcasting message to Bar WebSocket Client")
                        .to("undertow:ws://localhost:" + port + "/bar?sendToAll=true");
            }
        };
    }
}
