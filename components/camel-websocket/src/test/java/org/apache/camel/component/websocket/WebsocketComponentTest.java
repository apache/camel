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

import java.util.HashMap;
import java.util.Map;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class WebsocketComponentTest {

    private static final String PATH_ONE = "foo";
    private static final String PATH_TWO = "bar";
    private static final String PATH_SPEC_ONE = "/" + PATH_ONE + "/*";

    @Mock
    private WebsocketConsumer consumer;
    @Mock
    private NodeSynchronization sync;
    @Mock
    private WebsocketComponentServlet servlet;
    @Mock
    private Map<String, WebsocketComponentServlet> servlets;
    @Mock
    private ServletContextHandler handler;

    private WebsocketComponent component;
    private WebsocketProducer producer;
    private Server server;

    @Before
    public void setUp() throws Exception {
        component = new WebsocketComponent();
        component.setCamelContext(new DefaultCamelContext());
        server = component.createServer();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("localhost");
        connector.setPort(1988);
        server.addConnector(connector);

        WebsocketEndpoint endpoint = (WebsocketEndpoint) component.createEndpoint("websocket://x");
        producer = (WebsocketProducer) endpoint.createProducer();
        component.connect(producer);

        // wire the consumer with the endpoint so that WebSocketComponent.getConnectorKey() works without throwing NPE
        when(consumer.getEndpoint()).thenReturn(endpoint);
    }

    @Test
    public void testCreateContext() throws Exception {
        ServletContextHandler handler = component.createContext(server, server.getConnectors()[0], null);
        assertNotNull(handler);
    }

    @Test
    public void testCreateServerWithoutStaticContent() throws Exception {
        ServletContextHandler handler = component.createContext(server, server.getConnectors()[0], null);
        assertEquals(1, server.getConnectors().length);
        assertEquals("localhost", ((ServerConnector) server.getConnectors()[0]).getHost());
        assertEquals(1988, ((ServerConnector) server.getConnectors()[0]).getPort());
        assertFalse(server.getConnectors()[0].isStarted());
        assertEquals(handler, server.getHandler());
        assertEquals(1, server.getHandlers().length);
        assertEquals(handler, server.getHandlers()[0]);
        assertEquals("/", handler.getContextPath());
        assertNull(handler.getSessionHandler());
        assertNull(handler.getResourceBase());
        assertNull(handler.getServletHandler().getMappedServlet("/"));
    }

    @Test
    public void testCreateServerWithStaticContent() throws Exception {
        ServletContextHandler handler = component.createContext(server, server.getConnectors()[0], null);
        Server server = component.createStaticResourcesServer(handler, "localhost", 1988, "classpath:public");
        assertEquals(1, server.getConnectors().length);
        assertEquals("localhost", ((ServerConnector) server.getConnectors()[0]).getHost());
        assertEquals(1988, ((ServerConnector) server.getConnectors()[0]).getPort());
        assertFalse(server.getConnectors()[0].isStarted());
        assertEquals(handler, server.getHandler());
        assertEquals(1, server.getHandlers().length);
        assertEquals(handler, server.getHandlers()[0]);
        assertEquals("/", handler.getContextPath());
        assertNotNull(handler.getSessionHandler());
        assertNotNull(handler.getResourceBase());
        assertTrue(handler.getResourceBase().startsWith(JettyClassPathResource.class.getName()));
        assertNotNull(handler.getServletHandler().getMappedServlet("/"));
    }

    @Test
    public void testCreateEndpoint() throws Exception {
        Map<String, Object> parameters = new HashMap<String, Object>();
        Endpoint e1 = component.createEndpoint("websocket://foo", "foo", parameters);
        Endpoint e2 = component.createEndpoint("websocket://foo", "foo", parameters);
        Endpoint e3 = component.createEndpoint("websocket://bar", "bar", parameters);
        assertNotNull(e1);
        assertNotNull(e1);
        assertNotNull(e1);
        assertEquals(e1, e2);
        assertNotSame(e1, e3);
        assertNotSame(e2, e3);
    }

    @Test
    public void testCreateServlet() throws Exception {
        component.createServlet(sync, PATH_SPEC_ONE, servlets, handler);
        InOrder inOrder = inOrder(servlet, consumer, sync, servlets, handler);
        ArgumentCaptor<WebsocketComponentServlet> servletCaptor = ArgumentCaptor.forClass(WebsocketComponentServlet.class);
        inOrder.verify(servlets, times(1)).put(eq(PATH_SPEC_ONE), servletCaptor.capture());
        ArgumentCaptor<ServletHolder> holderCaptor = ArgumentCaptor.forClass(ServletHolder.class);
        inOrder.verify(handler, times(1)).addServlet(holderCaptor.capture(), eq(PATH_SPEC_ONE));
        inOrder.verifyNoMoreInteractions();
        assertEquals(servletCaptor.getValue(), holderCaptor.getValue().getServlet());
    }

    @Test
    public void testAddServletProducersOnly() throws Exception {
        WebsocketComponentServlet s1 = component.addServlet(sync, producer, PATH_ONE);
        WebsocketComponentServlet s2 = component.addServlet(sync, producer, PATH_TWO);
        assertNotNull(s1);
        assertNotNull(s2);
        assertNotSame(s1, s2);
        assertNull(s1.getConsumer());
        assertNull(s2.getConsumer());
    }

    @Test
    public void testAddServletConsumersOnly() throws Exception {
        WebsocketComponentServlet s1 = component.addServlet(sync, consumer, PATH_ONE);
        WebsocketComponentServlet s2 = component.addServlet(sync, consumer, PATH_TWO);
        assertNotNull(s1);
        assertNotNull(s2);
        assertNotSame(s1, s2);
        assertEquals(consumer, s1.getConsumer());
        assertEquals(consumer, s2.getConsumer());
    }

    @Test
    public void testAddServletProducerAndConsumer() throws Exception {
        WebsocketComponentServlet s1 = component.addServlet(sync, producer, PATH_ONE);
        WebsocketComponentServlet s2 = component.addServlet(sync, consumer, PATH_ONE);
        assertNotNull(s1);
        assertNotNull(s2);
        assertEquals(s1, s2);
        assertEquals(consumer, s1.getConsumer());
    }

    @Test
    public void testAddServletConsumerAndProducer() throws Exception {
        WebsocketComponentServlet s1 = component.addServlet(sync, consumer, PATH_ONE);
        WebsocketComponentServlet s2 = component.addServlet(sync, producer, PATH_ONE);
        assertNotNull(s1);
        assertNotNull(s2);
        assertEquals(s1, s2);
        assertEquals(consumer, s1.getConsumer());
    }

}
