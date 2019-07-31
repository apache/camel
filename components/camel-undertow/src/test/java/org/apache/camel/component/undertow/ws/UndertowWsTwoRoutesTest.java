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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.junit.Test;

public class UndertowWsTwoRoutesTest extends BaseUndertowTest {

    @Test
    public void testWSHttpCallEcho() throws Exception {

        // We call the route WebSocket BAR
        {
            final List<String> received = new ArrayList<>();
            final CountDownLatch latch = new CountDownLatch(1);
            final AsyncHttpClient c = new DefaultAsyncHttpClient();
            final WebSocket websocket = c.prepareGet("ws://localhost:" + getPort() + "/bar").execute(
                new WebSocketUpgradeHandler.Builder()
                    .addWebSocketListener(new WebSocketListener() {
                        @Override
                        public void onTextFrame(String message, boolean finalFragment, int rsv) {
                            received.add(message);
                            log.info("received --> " + message);
                            latch.countDown();
                        }

                        @Override
                        public void onOpen(WebSocket websocket) {
                        }

                        @Override
                        public void onClose(WebSocket websocket, int code, String reason) {
                        }

                        @Override
                        public void onError(Throwable t) {
                            t.printStackTrace();
                        }
                    }).build()).get();

            websocket.sendTextFrame("Beer");
            assertTrue(latch.await(10, TimeUnit.SECONDS));

            assertEquals(1, received.size());
            assertEquals("The bar has Beer", received.get(0));

            websocket.sendCloseFrame();
            c.close();
        }


        // We call the route WebSocket PUB
        {
            final List<String> received = new ArrayList<>();
            final CountDownLatch latch = new CountDownLatch(1);
            final AsyncHttpClient c = new DefaultAsyncHttpClient();
            final WebSocket websocket = c.prepareGet("ws://localhost:" + getPort() + "/pub").execute(
                    new WebSocketUpgradeHandler.Builder()
                            .addWebSocketListener(new WebSocketListener() {
                                @Override
                                public void onTextFrame(String message, boolean finalFragment, int rsv) {
                                    received.add(message);
                                    log.info("received --> " + message);
                                    latch.countDown();
                                }


                                @Override
                                public void onOpen(WebSocket websocket) {
                                }

                                @Override
                                public void onClose(WebSocket websocket, int code, String reason) {
                                }

                                @Override
                                public void onError(Throwable t) {
                                    t.printStackTrace();
                                }
                            }).build()).get();

            websocket.sendTextFrame("wine");
            assertTrue(latch.await(10, TimeUnit.SECONDS));

            assertEquals(1, received.size());
            assertEquals("The pub has wine", received.get(0));

            websocket.sendCloseFrame();
            c.close();
        }

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {

                int port = getPort();
                from("undertow:ws://localhost:" + port  + "/bar")
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
