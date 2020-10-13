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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WebsocketProducerRouteRestartTest extends CamelTestSupport {

    private static final String ROUTE_ID = WebsocketProducerRouteRestartTest.class.getSimpleName();

    private static List<Object> received = new ArrayList<>();
    private static CountDownLatch latch;

    private int port;
    private Logger log = LoggerFactory.getLogger(getClass());

    @Produce("direct:shop")
    private ProducerTemplate producer;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        super.setUp();
        received.clear();
        latch = new CountDownLatch(1);
    }

    @Test
    public void testWSSuspendResumeRoute() throws Exception {
        context.getRouteController().resumeRoute(ROUTE_ID);
        context.getRouteController().resumeRoute(ROUTE_ID);
        doTestWSHttpCall();
    }

    @Test
    public void testWSStopStartRoute() throws Exception {
        context.getRouteController().stopRoute(ROUTE_ID);
        context.getRouteController().startRoute(ROUTE_ID);
        doTestWSHttpCall();
    }

    @Test
    public void testWSRemoveAddRoute() throws Exception {
        context.removeRoute(ROUTE_ID);
        context.addRoutes(createRouteBuilder());
        context.getRouteController().startRoute(ROUTE_ID);
        doTestWSHttpCall();
    }

    private void doTestWSHttpCall() throws Exception {
        AsyncHttpClient c = new DefaultAsyncHttpClient();

        WebSocket websocket = c.prepareGet("ws://localhost:" + port + "/shop").execute(
                new WebSocketUpgradeHandler.Builder()
                        .addWebSocketListener(new WebSocketListener() {
                            @Override
                            public void onOpen(WebSocket websocket) {
                            }

                            @Override
                            public void onClose(WebSocket websocket, int code, String reason) {
                            }

                            @Override
                            public void onError(Throwable t) {
                                log.warn("Unhandled exception: {}", t.getMessage(), t);
                            }

                            @Override
                            public void onBinaryFrame(byte[] payload, boolean finalFragment, int rsv) {

                            }

                            @Override
                            public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                                received.add(payload);
                                log.info("received --> " + payload);
                                latch.countDown();
                            }

                            @Override
                            public void onPingFrame(byte[] payload) {
                            }

                            @Override
                            public void onPongFrame(byte[] payload) {
                            }
                        }).build())
                .get();

        // Send message to the direct endpoint
        producer.sendBodyAndHeader("Beer on stock at Apache Mall", WebsocketConstants.SEND_TO_ALL, "true");

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(1, received.size());
        Object r = received.get(0);
        assertTrue(r instanceof String);
        assertEquals("Beer on stock at Apache Mall", r);

        websocket.sendCloseFrame();
        c.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                WebsocketComponent websocketComponent = (WebsocketComponent) context.getComponent("websocket");
                websocketComponent.setMaxThreads(25);
                websocketComponent.setMinThreads(1);
                from("direct:shop")
                        .id(ROUTE_ID)
                        .log(">>> Message received from Shopping center : ${body}")
                        .to("websocket://localhost:" + port + "/shop");
            }
        };
    }
}
