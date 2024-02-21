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

public class UndertowWsTwoRoutesTest extends BaseUndertowTest {
    @Test
    public void testWSHttpCallEcho() throws Exception {

        // We call the route WebSocket BAR
        {

            WebsocketTestClient testClient = new WebsocketTestClient("ws://localhost:" + getPort() + "/bar");
            testClient.connect();

            testClient.sendTextMessage("Beer");
            assertTrue(testClient.await(10, TimeUnit.SECONDS));

            assertEquals(1, testClient.getReceived().size());
            assertEquals("The bar has Beer", testClient.getReceived().get(0));

            testClient.close();
        }

        // We call the route WebSocket PUB
        {
            WebsocketTestClient testClient = new WebsocketTestClient("ws://localhost:" + getPort() + "/pub");
            testClient.connect();

            testClient.sendTextMessage("wine");
            assertTrue(testClient.await(10, TimeUnit.SECONDS));

            assertEquals(1, testClient.getReceived().size());
            assertEquals("The pub has wine", testClient.getReceived().get(0));

            testClient.close();
        }

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                int port = getPort();
                from("undertow:ws://localhost:" + port + "/bar")
                        .log(">>> Message received from BAR WebSocket Client : ${body}")
                        .transform().simple("The bar has ${body}")
                        .to("undertow:ws://localhost:" + port + "/bar");

                from("undertow:ws://localhost:" + port + "/pub")
                        .log(">>> Message received from PUB WebSocket Client : ${body}")
                        .transform().simple("The pub has ${body}")
                        .to("undertow:ws://localhost:" + port + "/pub");
            }
        };
    }
}
