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

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class WebsocketComponentServletTest {

    private static final String PROTOCOL = "ws";
    private static final String MESSAGE = "message";
    private static final String CONNECTION_KEY = "random-connection-key";
    private static final InetSocketAddress ADDRESS = InetSocketAddress.createUnresolved("127.0.0.1", 12345);

    @Mock
    private Session session;
    @Mock
    private WebsocketConsumer consumer;
    @Mock
    private NodeSynchronization sync;
    @Mock
    private ServletUpgradeRequest request;

    private WebsocketComponentServlet websocketComponentServlet;

    private Map<String, WebSocketFactory> socketFactory;

    @Before
    public void setUp() throws Exception {
        socketFactory = new HashMap<>();
        socketFactory.put("default", new DefaultWebsocketFactory());
        websocketComponentServlet = new WebsocketComponentServlet(sync, null, socketFactory);
        when(session.getRemoteAddress()).thenReturn(ADDRESS);
    }

    @Test
    public void testGetConsumer() {
        assertNull(websocketComponentServlet.getConsumer());
        websocketComponentServlet.setConsumer(consumer);
        assertEquals(consumer, websocketComponentServlet.getConsumer());
    }

    @Test
    public void testSetConsumer() {
        testGetConsumer();
    }

    @Test
    public void testDoWebSocketConnect() {
        websocketComponentServlet.setConsumer(consumer);
        DefaultWebsocket webSocket = websocketComponentServlet.doWebSocketConnect(request, PROTOCOL);
        assertNotNull(webSocket);
        assertEquals(DefaultWebsocket.class, webSocket.getClass());
        DefaultWebsocket defaultWebsocket = webSocket;
        defaultWebsocket.setConnectionKey(CONNECTION_KEY);
        defaultWebsocket.setSession(session);
        defaultWebsocket.onMessage(MESSAGE);
        InOrder inOrder = inOrder(consumer, sync, request);
        inOrder.verify(consumer, times(1)).sendMessage(CONNECTION_KEY, MESSAGE, ADDRESS);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testDoWebSocketConnectConsumerIsNull() {
        DefaultWebsocket webSocket = websocketComponentServlet.doWebSocketConnect(request, PROTOCOL);
        assertNotNull(webSocket);
        assertEquals(DefaultWebsocket.class, webSocket.getClass());
        DefaultWebsocket defaultWebsocket = webSocket;
        defaultWebsocket.setConnectionKey(CONNECTION_KEY);
        defaultWebsocket.onMessage(MESSAGE);
        InOrder inOrder = inOrder(consumer, sync, request);
        inOrder.verifyNoMoreInteractions();
    }

}
