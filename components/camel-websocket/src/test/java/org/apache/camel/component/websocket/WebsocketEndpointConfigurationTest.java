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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.component.websocket.WebsocketComponent.ConnectorRef;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.Test;
import org.mockito.Mock;

public class WebsocketEndpointConfigurationTest extends CamelTestSupport {
    
    private int port;
    
    @Mock
    private Processor processor;

    @Override
    public void setUp() throws Exception {
        port = AvailablePortFinder.getNextAvailable(16330);
        super.setUp();
    }
    
    @Test
    public void testSetServletInitalparameters() throws Exception {
        String uri = "websocket://localhost:" + port + "/bar?bufferSize=65000&maxIdleTime=3000";
        WebsocketEndpoint websocketEndpoint = (WebsocketEndpoint)context.getEndpoint(uri);
        WebsocketComponent component = websocketEndpoint.getComponent();
        component.setMinThreads(1);
        component.setMaxThreads(20);
        Consumer consumer = websocketEndpoint.createConsumer(processor);
        component.connect((WebsocketProducerConsumer) consumer);
        
        assertNotNull(consumer);
        assertEquals(WebsocketConsumer.class, consumer.getClass());
        
        // just check the servlet initial parameters
        ConnectorRef conector = WebsocketComponent.getConnectors().values().iterator().next();
        
        ServletContextHandler context = (ServletContextHandler)conector.server.getHandler();
        String buffersize = context.getInitParameter("bufferSize");
        assertEquals("Get a wrong buffersize", "65000", buffersize);
        String maxIdleTime = context.getInitParameter("maxIdleTime");
        assertEquals("Get a worng maxIdleTime", "3000", maxIdleTime);
    }
    
    @Test(expected = RuntimeException.class)
    public void testSetServletNoMinThreadsNoMaxThreadsNoThreadPool() throws Exception {
        String uri = "websocket://localhost:" + port + "/bar?bufferSize=65000&maxIdleTime=3000";
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
        assertEquals("Get a wrong buffersize", "65000", buffersize);
        String maxIdleTime = context.getInitParameter("maxIdleTime");
        assertEquals("Get a worng maxIdleTime", "3000", maxIdleTime);
    }
    
    @Test
    public void testSetServletThreadPool() throws Exception {
        String uri = "websocket://localhost:" + port + "/bar?bufferSize=65000&maxIdleTime=3000";
        WebsocketEndpoint websocketEndpoint = (WebsocketEndpoint)context.getEndpoint(uri);
        WebsocketComponent component = websocketEndpoint.getComponent();
        QueuedThreadPool qtp = new QueuedThreadPool(20, 1);
        component.setThreadPool(qtp);
        Consumer consumer = websocketEndpoint.createConsumer(processor);
        component.connect((WebsocketProducerConsumer) consumer);
        assertNotNull(consumer);
        assertEquals(WebsocketConsumer.class, consumer.getClass());
        
        // just check the servlet initial parameters
        ConnectorRef conector = WebsocketComponent.getConnectors().values().iterator().next();
        
        ServletContextHandler context = (ServletContextHandler)conector.server.getHandler();
        String buffersize = context.getInitParameter("bufferSize");
        assertEquals("Get a wrong buffersize", "65000", buffersize);
        String maxIdleTime = context.getInitParameter("maxIdleTime");
        assertEquals("Get a worng maxIdleTime", "3000", maxIdleTime);
    }

}
