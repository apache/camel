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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.ws.WebSocket;
import org.asynchttpclient.ws.WebSocketListener;
import org.asynchttpclient.ws.WebSocketUpgradeHandler;
import org.junit.Before;
import org.junit.Test;

public class WebsocketConsumerRouteTest extends CamelTestSupport {

    protected int port;

    @Override
    @Before
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        super.setUp();
    }

    @Test
    public void testWSHttpCall() throws Exception {
        AsyncHttpClient c = new DefaultAsyncHttpClient();

        WebSocket websocket = c.prepareGet("ws://127.0.0.1:" + port + "/echo").execute(
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
                        t.printStackTrace();
                    }

                    @Override
                    public void onBinaryFrame(byte[] payload, boolean finalFragment, int rsv) {

                    }

                    @Override
                    public void onTextFrame(String payload, boolean finalFragment, int rsv) {

                    }

                    @Override
                    public void onPingFrame(byte[] payload) {

                    }

                    @Override
                    public void onPongFrame(byte[] payload) {

                    }
                }).build()).get();

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Test");

        websocket.sendTextFrame("Test");

        result.assertIsSatisfied();
        
        websocket.sendCloseFrame();
        c.close();
    }

    @Test
    public void testWSBytesHttpCall() throws Exception {
        AsyncHttpClient c = new DefaultAsyncHttpClient();

        WebSocket websocket = c.prepareGet("ws://127.0.0.1:" + port + "/echo").execute(
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
                        t.printStackTrace();
                    }

                    @Override
                    public void onBinaryFrame(byte[] payload, boolean finalFragment, int rsv) {
                    }

                    @Override
                    public void onTextFrame(String payload, boolean finalFragment, int rsv) {
                    }

                    @Override
                    public void onPingFrame(byte[] payload) {
                    }

                    @Override
                    public void onPongFrame(byte[] payload) {
                    }
                }).build()).get();

        MockEndpoint result = getMockEndpoint("mock:result");
        final byte[] testmessage = "Test".getBytes("utf-8");
        result.expectedBodiesReceived(testmessage);

        websocket.sendBinaryFrame(testmessage);

        result.assertIsSatisfied();

        websocket.sendCloseFrame();
        c.close();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                WebsocketComponent websocketComponent = (WebsocketComponent) context.getComponent("websocket");
                websocketComponent.setPort(port);
                websocketComponent.setMaxThreads(25);
                websocketComponent.setMinThreads(1);

                from("websocket://echo")
                    .log(">>> Message received from WebSocket Client : ${body}")
                    .to("mock:result");
            }
        };
    }

}
