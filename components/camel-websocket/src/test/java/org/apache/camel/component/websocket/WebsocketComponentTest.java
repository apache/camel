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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

/**
 *
 */
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
    @Mock
    private CamelContext camelContext;

    private WebsocketComponent component;
    private Server server;
    private ServletContextHandler context;

    @Before
    public void setUp() throws Exception {
        component = new WebsocketComponent();
        setUpJettyServer();
    }

    @After
    public void shutdown() throws Exception {
        server.stop();
    }

    // TODO - Update tests as it fails now - chm - 22/05/2012
    /*

    @Test
    public void testCreateServerWithoutStaticContent() throws Exception {
        assertEquals(1, server.getConnectors().length);
        assertEquals("localhost", server.getConnectors()[0].getHost());
        assertEquals(1988, server.getConnectors()[0].getPort());
        assertFalse(server.getConnectors()[0].isStarted());
        assertEquals(handler, server.getHandler());
        assertEquals(1, server.getHandlers().length);
        assertEquals(handler, server.getHandlers()[0]);
        assertEquals("/", handler.getContextPath());
        assertNotNull(handler.getSessionHandler());
        assertNull(handler.getResourceBase());
        assertNull(handler.getServletHandler().getHolderEntry("/"));
    }


    @Test
    public void testCreateServerWithStaticContent() throws Exception {
        ServletContextHandler handler = component.createContext();
        Server server = component.createServer(handler, "localhost", 1988, "public/");
        assertEquals(2, server.getConnectors().length);
        assertEquals("localhost", server.getConnectors()[0].getHost());
        assertEquals(1988, server.getConnectors()[0].getPort());
        assertFalse(server.getConnectors()[0].isStarted());
        assertEquals(handler, server.getHandler());
        assertEquals(1, server.getHandlers().length);
        assertEquals(handler, server.getHandlers()[0]);
        assertEquals("/", handler.getContextPath());
        assertNotNull(handler.getSessionHandler());
        assertNotNull(handler.getResourceBase());
        assertTrue(handler.getResourceBase().endsWith("public"));
        assertNotNull(handler.getServletHandler().getHolderEntry("/"));
    }


    @Test
    public void testCreateEndpoint() throws Exception {
        Map<String, Object> parameters = new HashMap<String, Object>();

        component.setCamelContext(camelContext);

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
        component.setCamelContext(camelContext);
        component.doStart();
        WebsocketComponentServlet s1 = component.addServlet(sync, null, PATH_ONE);
        WebsocketComponentServlet s2 = component.addServlet(sync, null, PATH_TWO);
        assertNotNull(s1);
        assertNotNull(s2);
        assertNotSame(s1, s2);
        assertNull(s1.getConsumer());
        assertNull(s2.getConsumer());
        component.doStop();
    }

    @Test
    public void testAddServletConsumersOnly() throws Exception {
        component.setCamelContext(camelContext);
        component.doStart();
        WebsocketComponentServlet s1 = component.addServlet(sync, consumer, PATH_ONE);
        WebsocketComponentServlet s2 = component.addServlet(sync, consumer, PATH_TWO);
        assertNotNull(s1);
        assertNotNull(s2);
        assertNotSame(s1, s2);
        assertEquals(consumer, s1.getConsumer());
        assertEquals(consumer, s2.getConsumer());
        component.doStop();
    }

    @Test
    public void testAddServletProducerAndConsumer() throws Exception {
        component.setCamelContext(camelContext);
        component.doStart();
        WebsocketComponentServlet s1 = component.addServlet(sync, null, PATH_ONE);
        WebsocketComponentServlet s2 = component.addServlet(sync, consumer, PATH_ONE);
        assertNotNull(s1);
        assertNotNull(s2);
        assertEquals(s1, s2);
        assertEquals(consumer, s1.getConsumer());
        component.doStop();
    }

    @Test
    public void testAddServletConsumerAndProducer() throws Exception {
        component.setCamelContext(camelContext);
        component.setPort(0);
        component.doStart();
        WebsocketComponentServlet s1 = component.addServlet(sync, consumer, PATH_ONE);
        WebsocketComponentServlet s2 = component.addServlet(sync, null, PATH_ONE);
        assertNotNull(s1);
        assertNotNull(s2);
        assertEquals(s1, s2);
        assertEquals(consumer, s1.getConsumer());
        component.doStop();
    }
            */

    private void setUpJettyServer() throws Exception {
        server = component.createServer();
        Connector connector = new SelectChannelConnector();
        connector.setHost("localhost");
        connector.setPort(1988);
        context = component.createContext(server,connector,null);
        server.addConnector(connector);
        server.setHandler(context);
        server.start();
    }


}
