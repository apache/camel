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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.eclipse.jetty.websocket.WebSocket.Connection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class WebsocketProducerTest {

    private static final String MESSAGE = "MESSAGE";
    private static final String SESSION_KEY = "random-session-key";

    @Mock
    private WebsocketEndpoint endpoint;
    @Mock
    private WebsocketStore store;
    @Mock
    private Connection connection;
    @Mock
    private DefaultWebsocket defaultWebsocket1;
    @Mock
    private DefaultWebsocket defaultWebsocket2;
    @Mock
    private Exchange exchange;
    @Mock
    private Message inMessage;

    private IOException exception = new IOException("BAD NEWS EVERYONE!");
    private WebsocketProducer websocketProducer;
    private Collection<DefaultWebsocket> sockets;

    @Before
    public void setUp() throws Exception {
        websocketProducer = new WebsocketProducer(endpoint);
        websocketProducer.setStore(store);
        sockets = Arrays.asList(defaultWebsocket1, defaultWebsocket2);
    }

    @Test
    public void testProcessSingleMessage() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getMandatoryBody()).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(false);
        when(inMessage.getHeader(WebsocketConstants.CONNECTION_KEY, String.class)).thenReturn(SESSION_KEY);
        when(store.get(SESSION_KEY)).thenReturn(defaultWebsocket1);
        when(defaultWebsocket1.getConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);

        websocketProducer.process(exchange);

        InOrder inOrder = inOrder(endpoint, store, connection, defaultWebsocket1, defaultWebsocket2, exchange, inMessage);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.CONNECTION_KEY, String.class);
        inOrder.verify(store, times(1)).get(SESSION_KEY);
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessSingleMessageWithException() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getMandatoryBody()).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(false);
        when(inMessage.getHeader(WebsocketConstants.CONNECTION_KEY, String.class)).thenReturn(SESSION_KEY);
        when(store.get(SESSION_KEY)).thenReturn(defaultWebsocket1);
        when(defaultWebsocket1.getConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        doThrow(exception).when(connection).sendMessage(MESSAGE);

        try {
            websocketProducer.process(exchange);
            fail("Exception expected");
        } catch (IOException ioe) {
            assertEquals(exception, ioe);
        }

        InOrder inOrder = inOrder(endpoint, store, connection, defaultWebsocket1, defaultWebsocket2, exchange, inMessage);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.CONNECTION_KEY, String.class);
        inOrder.verify(store, times(1)).get(SESSION_KEY);
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMultipleMessages() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getMandatoryBody()).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(true);
        when(store.getAll()).thenReturn(sockets);
        when(defaultWebsocket1.getConnection()).thenReturn(connection);
        when(defaultWebsocket2.getConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);

        websocketProducer.process(exchange);

        InOrder inOrder = inOrder(endpoint, store, connection, defaultWebsocket1, defaultWebsocket2, exchange, inMessage);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(store, times(1)).getAll();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verify(defaultWebsocket2, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket2, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessMultipleMessagesWithException() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getMandatoryBody()).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(true);
        when(store.getAll()).thenReturn(sockets);
        when(defaultWebsocket1.getConnection()).thenReturn(connection);
        when(defaultWebsocket2.getConnection()).thenReturn(connection);
        doThrow(exception).when(connection).sendMessage(MESSAGE);
        when(connection.isOpen()).thenReturn(true);

        try {
            websocketProducer.process(exchange);
            fail("Exception expected");
        } catch (Exception e) {
            assertEquals(exception, e.getCause());
        }

        InOrder inOrder = inOrder(endpoint, store, connection, defaultWebsocket1, defaultWebsocket2, exchange, inMessage);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(store, times(1)).getAll();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verify(defaultWebsocket2, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket2, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testProcessSingleMessageNoConnectionKey() throws Exception {
        when(exchange.getIn()).thenReturn(inMessage);
        when(inMessage.getBody(String.class)).thenReturn(MESSAGE);
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(false);
        when(inMessage.getHeader(WebsocketConstants.CONNECTION_KEY, String.class)).thenReturn(null);

        try {
            websocketProducer.process(exchange);
            fail("Exception expected");
        } catch (Exception e) {
            assertEquals(IllegalArgumentException.class, e.getClass());
            assertNotNull(e.getMessage());
            assertNull(e.getCause());
        }

        InOrder inOrder = inOrder(endpoint, store, connection, defaultWebsocket1, defaultWebsocket2, exchange, inMessage);
        inOrder.verify(exchange, times(1)).getIn();
        inOrder.verify(inMessage, times(1)).getMandatoryBody();
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.CONNECTION_KEY, String.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendMessage() throws Exception {
        when(defaultWebsocket1.getConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);

        websocketProducer.sendMessage(defaultWebsocket1, MESSAGE);

        InOrder inOrder = inOrder(endpoint, store, connection, defaultWebsocket1, defaultWebsocket2, exchange, inMessage);
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendMessageConnetionIsClosed() throws Exception {
        when(defaultWebsocket1.getConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(false);

        websocketProducer.sendMessage(defaultWebsocket1, MESSAGE);

        InOrder inOrder = inOrder(endpoint, store, connection, defaultWebsocket1, defaultWebsocket2, exchange, inMessage);
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendMessageWithException() throws Exception {
        when(defaultWebsocket1.getConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);
        doThrow(exception).when(connection).sendMessage(MESSAGE);

        try {
            websocketProducer.sendMessage(defaultWebsocket1, MESSAGE);
            fail("Exception expected");
        } catch (IOException ioe) {
            assertEquals(exception, ioe);
        }

        InOrder inOrder = inOrder(endpoint, store, connection, defaultWebsocket1, defaultWebsocket2, exchange, inMessage);
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testIsSendToAllSet() {
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(true, false);
        assertTrue(websocketProducer.isSendToAllSet(inMessage));
        assertFalse(websocketProducer.isSendToAllSet(inMessage));
        InOrder inOrder = inOrder(inMessage);
        inOrder.verify(inMessage, times(2)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testIsSendToAllSetHeaderNull() {
        when(inMessage.getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class)).thenReturn(null);
        assertFalse(websocketProducer.isSendToAllSet(inMessage));
        InOrder inOrder = inOrder(inMessage);
        inOrder.verify(inMessage, times(1)).getHeader(WebsocketConstants.SEND_TO_ALL, false, Boolean.class);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendToAll() throws Exception {
        when(store.getAll()).thenReturn(sockets);
        when(defaultWebsocket1.getConnection()).thenReturn(connection);
        when(defaultWebsocket2.getConnection()).thenReturn(connection);
        when(connection.isOpen()).thenReturn(true);

        websocketProducer.sendToAll(store, MESSAGE, exchange);

        InOrder inOrder = inOrder(store, connection, defaultWebsocket1, defaultWebsocket2);
        inOrder.verify(store, times(1)).getAll();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verify(defaultWebsocket2, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket2, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSendToAllWithExcpetion() throws Exception {
        when(store.getAll()).thenReturn(sockets);
        when(defaultWebsocket1.getConnection()).thenReturn(connection);
        when(defaultWebsocket2.getConnection()).thenReturn(connection);
        doThrow(exception).when(connection).sendMessage(MESSAGE);
        when(connection.isOpen()).thenReturn(true);

        try {
            websocketProducer.sendToAll(store, MESSAGE, exchange);
            fail("Exception expected");
        } catch (Exception e) {
            assertEquals(exception, e.getCause());
        }

        InOrder inOrder = inOrder(store, connection, defaultWebsocket1, defaultWebsocket2);
        inOrder.verify(store, times(1)).getAll();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket1, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verify(defaultWebsocket2, times(1)).getConnection();
        inOrder.verify(connection, times(1)).isOpen();
        inOrder.verify(defaultWebsocket2, times(1)).getConnection();
        inOrder.verify(connection, times(1)).sendMessage(MESSAGE);
        inOrder.verifyNoMoreInteractions();
    }
}
