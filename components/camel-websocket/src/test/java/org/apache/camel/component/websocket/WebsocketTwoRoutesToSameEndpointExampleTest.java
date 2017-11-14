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
package org.apache.camel.component.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketTextListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.junit.Test;

public class WebsocketTwoRoutesToSameEndpointExampleTest extends CamelTestSupport {

    private static List<String> received = new ArrayList<String>();
    private static CountDownLatch latch;
    private int port;

    @Override
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable(16310);
        super.setUp();
    }

    @Test
    public void testWSHttpCallEcho() throws Exception {

        // We call the route WebSocket BAR
        received.clear();
        latch = new CountDownLatch(2);

        DefaultAsyncHttpClient c = new DefaultAsyncHttpClient();

        WebSocket websocket = c.prepareGet("ws://localhost:" + port + "/bar").execute(
                new WebSocketUpgradeHandler.Builder()
                        .addWebSocketListener(new WebSocketTextListener() {
                            @Override
                            public void onMessage(String message) {
                                received.add(message);
                                log.info("received --> " + message);
                                latch.countDown();
                            }

                            @Override
                            public void onOpen(WebSocket websocket) {
                            }

                            @Override
                            public void onClose(WebSocket websocket) {
                            }

                            @Override
                            public void onError(Throwable t) {
                                t.printStackTrace();
                            }
                        }).build()).get();

        websocket.sendMessage("Beer");
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(2, received.size());

        //Cannot guarantee the order in which messages are received
        assertTrue(received.contains("The bar has Beer"));
        assertTrue(received.contains("Broadcasting to Bar"));

        websocket.close();
        c.close();
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
