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
package org.apache.camel.component.undertow.ws;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.component.undertow.UndertowConstants;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketTextListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.junit.Test;

public class UndertowWsProducerRouteRestartTest extends BaseUndertowTest {

    private static final String ROUTE_ID = UndertowWsProducerRouteRestartTest.class.getSimpleName();

    @Produce(uri = "direct:shop")
    private ProducerTemplate producer;

    @Test
    public void testWSSuspendResumeRoute() throws Exception {
        context.suspendRoute(ROUTE_ID);
        context.resumeRoute(ROUTE_ID);
        doTestWSHttpCall();
    }

    @Test
    public void testWSStopStartRoute() throws Exception {
        context.stopRoute(ROUTE_ID);
        context.startRoute(ROUTE_ID);
        doTestWSHttpCall();
    }

    @Test
    public void testWSRemoveAddRoute() throws Exception {
        context.removeRoute(ROUTE_ID);
        context.addRoutes(createRouteBuilder());
        context.startRoute(ROUTE_ID);
        doTestWSHttpCall();
    }

    private void doTestWSHttpCall() throws Exception {
        final List<Object> received = new ArrayList<Object>();
        final CountDownLatch latch = new CountDownLatch(1);

        AsyncHttpClient c = new DefaultAsyncHttpClient();

        WebSocket websocket = c.prepareGet("ws://localhost:" + getPort() + "/shop")
                .execute(new WebSocketUpgradeHandler.Builder().addWebSocketListener(new WebSocketTextListener() {
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

        // Send message to the direct endpoint
        producer.sendBodyAndHeader("Beer on stock at Apache Mall", UndertowConstants.SEND_TO_ALL, "true");

        assertTrue(latch.await(10, TimeUnit.SECONDS));

        assertEquals(1, received.size());
        Object r = received.get(0);
        assertTrue(r instanceof String);
        assertEquals("Beer on stock at Apache Mall", r);

        websocket.close();
        c.close();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:shop") //
                        .id(ROUTE_ID) //
                        .log(">>> Message received from Shopping center : ${body}") //
                        .to("undertow:ws://localhost:" + getPort() + "/shop");
            }
        };
    }
}
