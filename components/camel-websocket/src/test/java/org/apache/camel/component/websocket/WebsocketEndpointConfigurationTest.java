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

import java.time.Duration;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.component.websocket.WebsocketComponent.ConnectorRef;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.api.WebSocketPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@MockitoSettings
public class WebsocketEndpointConfigurationTest extends CamelTestSupport {

    private int port;

    @Mock
    private Processor processor;

    @Test
    public void testSetServletInitalparameters() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        String uri = "websocket://localhost:" + port
                     + "/bar?bufferSize=25000&maxIdleTime=3000&maxTextMessageSize=500&maxBinaryMessageSize=550";
        WebsocketEndpoint websocketEndpoint = (WebsocketEndpoint) context.getEndpoint(uri);
        WebsocketComponent component = websocketEndpoint.getComponent();
        component.setMinThreads(1);
        component.setMaxThreads(25);
        Consumer consumer = websocketEndpoint.createConsumer(processor);
        component.connect((WebsocketProducerConsumer) consumer);

        assertNotNull(consumer);
        assertEquals(WebsocketConsumer.class, consumer.getClass());

        // just check the servlet initial parameters
        ConnectorRef conector = WebsocketComponent.getConnectors().values().iterator().next();

        ServletContextHandler context = (ServletContextHandler) conector.server.getHandler();
        String buffersize = context.getInitParameter("bufferSize");
        assertEquals("25000", buffersize, "Got a wrong buffersize");
        String maxIdleTime = context.getInitParameter("maxIdleTime");
        assertEquals("3000", maxIdleTime, "Got a wrong maxIdleTime");
        String maxTextMessageSize = context.getInitParameter("maxTextMessageSize");
        assertEquals("500", maxTextMessageSize, "Got a wrong maxTextMessageSize");
        String maxBinaryMessageSize = context.getInitParameter("maxBinaryMessageSize");
        assertEquals("550", maxBinaryMessageSize, "Got a wrong maxBinaryMessageSize");

        WebSocketPolicy policy
                = (WebSocketPolicy) context.getServletContext().getAttribute(WebSocketPolicy.class.getName());
        int factoryBufferSize = policy.getInputBufferSize();
        assertEquals(25000, factoryBufferSize, "Got a wrong buffersize");
        Duration factoryMaxIdleTime = policy.getIdleTimeout();
        assertEquals(3000, factoryMaxIdleTime.toMillis(), "Got a wrong maxIdleTime");
        long factoryMaxTextMessageSize = policy.getMaxTextMessageSize();
        assertEquals(500L, factoryMaxTextMessageSize, "Got a wrong maxTextMessageSize");
        long factoryMaxBinaryMessageSize = policy.getMaxBinaryMessageSize();
        assertEquals(550L, factoryMaxBinaryMessageSize, "Got a wrong maxBinaryMessageSize");
    }

    @Test
    public void testSetServletNoMinThreadsNoMaxThreadsNoThreadPool() throws Exception {
        assumeTrue(1 + Runtime.getRuntime().availableProcessors() * 2 >= 19, "At lease 18 CPUs available");
        port = AvailablePortFinder.getNextAvailable();
        String uri = "websocket://localhost:" + port + "/bar?bufferSize=25000&maxIdleTime=3000";
        WebsocketEndpoint websocketEndpoint = (WebsocketEndpoint) context.getEndpoint(uri);
        WebsocketComponent component = websocketEndpoint.getComponent();
        Consumer consumer = websocketEndpoint.createConsumer(processor);
        component.connect((WebsocketProducerConsumer) consumer);
        assertNotNull(consumer);
        assertEquals(WebsocketConsumer.class, consumer.getClass());

        // just check the servlet initial parameters
        ConnectorRef conector = WebsocketComponent.getConnectors().values().iterator().next();

        ServletContextHandler context = (ServletContextHandler) conector.server.getHandler();
        String buffersize = context.getInitParameter("bufferSize");
        assertEquals("25000", buffersize, "Got a wrong buffersize");
        String maxIdleTime = context.getInitParameter("maxIdleTime");
        assertEquals("3000", maxIdleTime, "Got a wrong maxIdleTime");

        WebSocketPolicy policy
                = (WebSocketPolicy) context.getServletContext().getAttribute(WebSocketPolicy.class.getName());
        int factoryBufferSize = policy.getInputBufferSize();
        assertEquals(25000, factoryBufferSize, "Got a wrong buffersize");
        Duration factoryMaxIdleTime = policy.getIdleTimeout();
        assertEquals(3000, factoryMaxIdleTime.toMillis(), "Got a wrong maxIdleTime");
    }

    @Test
    public void testSetServletThreadPool() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        String uri = "websocket://localhost:" + port + "/bar?bufferSize=25000&maxIdleTime=3000";
        WebsocketEndpoint websocketEndpoint = (WebsocketEndpoint) context.getEndpoint(uri);
        WebsocketComponent component = websocketEndpoint.getComponent();
        QueuedThreadPool qtp = new QueuedThreadPool(25, 1);
        component.setThreadPool(qtp);
        Consumer consumer = websocketEndpoint.createConsumer(processor);
        component.connect((WebsocketProducerConsumer) consumer);
        assertNotNull(consumer);
        assertEquals(WebsocketConsumer.class, consumer.getClass());

        // just check the servlet initial parameters
        ConnectorRef conector = WebsocketComponent.getConnectors().values().iterator().next();

        ServletContextHandler context = (ServletContextHandler) conector.server.getHandler();
        String buffersize = context.getInitParameter("bufferSize");
        assertEquals("25000", buffersize, "Got a wrong buffersize");
        String maxIdleTime = context.getInitParameter("maxIdleTime");
        assertEquals("3000", maxIdleTime, "Got a wrong maxIdleTime");

        WebSocketPolicy policy
                = (WebSocketPolicy) context.getServletContext().getAttribute(WebSocketPolicy.class.getName());
        int factoryBufferSize = policy.getInputBufferSize();
        assertEquals(25000, factoryBufferSize, "Got a wrong buffersize");
        Duration factoryMaxIdleTime = policy.getIdleTimeout();
        assertEquals(3000, factoryMaxIdleTime.toMillis(), "Got a wrong maxIdleTime");
    }

}
