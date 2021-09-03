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

import java.io.*;
import java.net.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class WebsocketSubprotocolEndpointTest extends CamelTestSupport {
    @WebSocket
    public static class TestWebSocket {
        @OnWebSocketConnect
        public void onConnect(Session session) throws IOException {
            session.getRemote().sendString("This is not SOAP!");
            session.close();
        }
    }

    private int port;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        super.setUp();
    }

    @Test
    public void testSubprotocolAccepted() throws Exception {
        URI uri = new URI("ws://localhost:" + port + "/test");
        TestWebSocket socket = new TestWebSocket();
        WebSocketClient client = new WebSocketClient();

        ClientUpgradeRequest request = new ClientUpgradeRequest();
        request.setSubProtocols("wamp", "chat,soap");       // three proposed subprotocols split across two headers

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("This is not SOAP!");
        result.expectedHeaderReceived(WebsocketConstants.SUBPROTOCOL, "soap");

        client.start();
        client.connect(socket, uri, request).get(10, TimeUnit.SECONDS);
        client.stop();

        result.assertIsSatisfied();
    }

    @Test
    public void testSubprotocolRejected() throws Exception {
        URI uri = new URI("ws://localhost:" + port + "/test");
        TestWebSocket socket = new TestWebSocket();
        WebSocketClient client = new WebSocketClient();

        ClientUpgradeRequest request = new ClientUpgradeRequest();
        request.setSubProtocols("wamp");

        client.start();
        Future<Session> future = client.connect(socket, uri, request);

        // an exception should be thrown because the connection is rejected
        assertThrows(ExecutionException.class, () -> future.get(10, TimeUnit.SECONDS));

        client.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("websocket://localhost:" + port + "/test?subprotocol=soap")
                        .log(">>> Message received from WebSocket Client : ${body}")
                        .to("mock:result");
            }
        };
    }

}
