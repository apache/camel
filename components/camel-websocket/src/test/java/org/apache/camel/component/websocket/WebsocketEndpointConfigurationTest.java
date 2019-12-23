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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.component.websocket.WebsocketComponent.ConnectorRef;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assume.assumeTrue;

@RunWith(MockitoJUnitRunner.class)
public class WebsocketEndpointConfigurationTest extends CamelTestSupport {
    
    private int port;
    
    @Mock
    private Processor processor;

    @Test
    public void testSetServletInitalparameters() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        String uri = "websocket://localhost:" + port + "/bar?bufferSize=25000&maxIdleTime=3000&maxTextMessageSize=500&maxBinaryMessageSize=550";
        WebsocketEndpoint websocketEndpoint = (WebsocketEndpoint)context.getEndpoint(uri);
        WebsocketComponent component = websocketEndpoint.getComponent();
        component.setMinThreads(1);
        component.setMaxThreads(25);
        Consumer consumer = websocketEndpoint.createConsumer(processor);
        component.connect((WebsocketProducerConsumer) consumer);
        
        assertNotNull(consumer);
        assertEquals(WebsocketConsumer.class, consumer.getClass());
        
        // just check the servlet initial parameters
        ConnectorRef conector = WebsocketComponent.getConnectors().values().iterator().next();
        
        ServletContextHandler context = (ServletContextHandler)conector.server.getHandler();
        String buffersize = context.getInitParameter("bufferSize");
        assertEquals("Got a wrong buffersize", "25000", buffersize);
        String maxIdleTime = context.getInitParameter("maxIdleTime");
        assertEquals("Got a wrong maxIdleTime", "3000", maxIdleTime);
        String maxTextMessageSize = context.getInitParameter("maxTextMessageSize");
        assertEquals("Got a wrong maxTextMessageSize", "500", maxTextMessageSize);
        String maxBinaryMessageSize = context.getInitParameter("maxBinaryMessageSize");
        assertEquals("Got a wrong maxBinaryMessageSize", "550", maxBinaryMessageSize);
        
        WebSocketServletFactory factory = (WebSocketServletFactory)context.getServletContext().getAttribute(WebSocketServletFactory.class.getName());
        int factoryBufferSize = factory.getPolicy().getInputBufferSize();
        assertEquals("Got a wrong buffersize", 25000, factoryBufferSize);
        long factoryMaxIdleTime = factory.getPolicy().getIdleTimeout();
        assertEquals("Got a wrong maxIdleTime", 3000, factoryMaxIdleTime);
        int factoryMaxTextMessageSize = factory.getPolicy().getMaxTextMessageSize();
        assertEquals("Got a wrong maxTextMessageSize", 500, factoryMaxTextMessageSize);
        int factoryMaxBinaryMessageSize = factory.getPolicy().getMaxBinaryMessageSize();
        assertEquals("Got a wrong maxBinaryMessageSize", 550, factoryMaxBinaryMessageSize);
    }
    
    @Test
    public void testSetServletNoMinThreadsNoMaxThreadsNoThreadPool() throws Exception {
        assumeTrue("At lease 18 CPUs available", 1 + Runtime.getRuntime().availableProcessors() * 2 >= 19);
        port = AvailablePortFinder.getNextAvailable();
        String uri = "websocket://localhost:" + port + "/bar?bufferSize=25000&maxIdleTime=3000";
        WebsocketEndpoint websocketEndpoint = (WebsocketEndpoint)context.getEndpoint(uri);
        WebsocketComponent component = websocketEndpoint.getComponent();
        Consumer consumer = websocketEndpoint.createConsumer(processor);
        component.connect((WebsocketProducerConsumer) consumer);
        assertNotNull(consumer);
        assertEquals(WebsocketConsumer.class, consumer.getClass());
        
        // just check the servlet initial parameters
        ConnectorRef conector = WebsocketComponent.getConnectors().values().iterator().next();
        
        ServletContextHandler context = (ServletContextHandler)conector.server.getHandler();
        String buffersize = context.getInitParameter("bufferSize");
        assertEquals("Got a wrong buffersize", "25000", buffersize);
        String maxIdleTime = context.getInitParameter("maxIdleTime");
        assertEquals("Got a wrong maxIdleTime", "3000", maxIdleTime);
        
        WebSocketServletFactory factory = (WebSocketServletFactory)context.getServletContext().getAttribute(WebSocketServletFactory.class.getName());
        int factoryBufferSize = factory.getPolicy().getInputBufferSize();
        assertEquals("Got a wrong buffersize", 25000, factoryBufferSize);
        long factoryMaxIdleTime = factory.getPolicy().getIdleTimeout();
        assertEquals("Got a wrong maxIdleTime", 3000, factoryMaxIdleTime);
    }
    
    @Test
    public void testSetServletThreadPool() throws Exception {
        port = AvailablePortFinder.getNextAvailable();
        String uri = "websocket://localhost:" + port + "/bar?bufferSize=25000&maxIdleTime=3000";
        WebsocketEndpoint websocketEndpoint = (WebsocketEndpoint)context.getEndpoint(uri);
        WebsocketComponent component = websocketEndpoint.getComponent();
        QueuedThreadPool qtp = new QueuedThreadPool(25, 1);
        component.setThreadPool(qtp);
        Consumer consumer = websocketEndpoint.createConsumer(processor);
        component.connect((WebsocketProducerConsumer) consumer);
        assertNotNull(consumer);
        assertEquals(WebsocketConsumer.class, consumer.getClass());
        
        // just check the servlet initial parameters
        ConnectorRef conector = WebsocketComponent.getConnectors().values().iterator().next();
        
        ServletContextHandler context = (ServletContextHandler)conector.server.getHandler();
        String buffersize = context.getInitParameter("bufferSize");
        assertEquals("Got a wrong buffersize", "25000", buffersize);
        String maxIdleTime = context.getInitParameter("maxIdleTime");
        assertEquals("Got a wrong maxIdleTime", "3000", maxIdleTime);
        
        WebSocketServletFactory factory = (WebSocketServletFactory)context.getServletContext().getAttribute(WebSocketServletFactory.class.getName());
        int factoryBufferSize = factory.getPolicy().getInputBufferSize();
        assertEquals("Got a wrong buffersize", 25000, factoryBufferSize);
        long factoryMaxIdleTime = factory.getPolicy().getIdleTimeout();
        assertEquals("Got a wrong maxIdleTime", 3000, factoryMaxIdleTime);
    }

}
