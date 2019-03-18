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

import org.eclipse.jetty.websocket.api.Session;
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

@RunWith(MockitoJUnitRunner.class)
public class DefaultWebsocketTest {

    private static final int CLOSE_CODE = -1;
    private static final String MESSAGE = "message";
    private static final String CONNECTION_KEY = "random-connection-key";
    private static final InetSocketAddress ADDRESS = InetSocketAddress.createUnresolved("127.0.0.1", 12345);

    @Mock
    private Session session;
    @Mock
    private WebsocketConsumer consumer;
    @Mock
    private NodeSynchronization sync;

    private DefaultWebsocket defaultWebsocket;


    @Before
    public void setUp() throws Exception {
        defaultWebsocket = new DefaultWebsocket(sync, null, consumer);
        defaultWebsocket.setConnectionKey(CONNECTION_KEY);
        when(session.getRemoteAddress()).thenReturn(ADDRESS);
    }

    @Test
    public void testOnClose() {
        defaultWebsocket.onClose(CLOSE_CODE, MESSAGE);
        InOrder inOrder = inOrder(session, consumer, sync);
        inOrder.verify(sync, times(1)).removeSocket(defaultWebsocket);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOnConnect() {
        defaultWebsocket.onConnect(session);

        InOrder inOrder = inOrder(session, consumer, sync);
        inOrder.verify(sync, times(1)).addSocket(defaultWebsocket);
        inOrder.verifyNoMoreInteractions();

        assertEquals(session, defaultWebsocket.getSession());
    }

    @Test
    public void testOnMessage() {
        defaultWebsocket.setConnectionKey(CONNECTION_KEY);
        defaultWebsocket.setSession(session);
        defaultWebsocket.onMessage(MESSAGE);
        InOrder inOrder = inOrder(session, consumer, sync);
        inOrder.verify(consumer, times(1)).sendMessage(CONNECTION_KEY, MESSAGE, ADDRESS);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOnMessageWithNullConsumer() {
        defaultWebsocket = new DefaultWebsocket(sync, null, null);
        defaultWebsocket.setConnectionKey(CONNECTION_KEY);
        defaultWebsocket.onMessage(MESSAGE);
        InOrder inOrder = inOrder(session, consumer, sync);
        inOrder.verify(consumer, times(0)).sendMessage(CONNECTION_KEY, MESSAGE, ADDRESS);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testGetConnection() {
        assertNull(defaultWebsocket.getSession());
        defaultWebsocket.onConnect(session);
        assertEquals(session, defaultWebsocket.getSession());
        defaultWebsocket.setSession(null);
        assertNull(defaultWebsocket.getSession());
        defaultWebsocket.setSession(session);
        assertEquals(session, defaultWebsocket.getSession());
    }

    @Test
    public void testSetConnection() {
        testGetConnection();
    }

    @Test
    public void testGetConnectionKey() {
        defaultWebsocket.setConnectionKey(null);
        assertNull(defaultWebsocket.getConnectionKey());
        defaultWebsocket.onConnect(session);
        assertNotNull(defaultWebsocket.getConnectionKey());
        defaultWebsocket.setConnectionKey(CONNECTION_KEY);
        assertEquals(CONNECTION_KEY, defaultWebsocket.getConnectionKey());
        defaultWebsocket.setConnectionKey(null);
        assertNull(defaultWebsocket.getConnectionKey());
    }

    @Test
    public void testSetConnectionKey() {
        testGetConnectionKey();
    }

}
