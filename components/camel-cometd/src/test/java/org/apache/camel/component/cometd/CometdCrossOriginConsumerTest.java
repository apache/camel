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
package org.apache.camel.component.cometd;

import javax.servlet.Filter;

import org.apache.camel.Endpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


import static org.mockito.Mockito.when;

/**
 * Unit testing for using a CometdProducer and a CometdConsumer
 */
@RunWith(MockitoJUnitRunner.class)
public class CometdCrossOriginConsumerTest extends CamelTestSupport {

    private static final String FILTER_PATH = "testFilterPath";
    private static final String ALLOWED_ORIGINS = "testAllowedOrigins";

    @Mock
    Connector connector;

    @Mock
    CometdEndpoint endpoint;

    @Test
    public void testFilterArgumentsSetOnEndpoint() throws Exception {
        // setup
        CometdComponent component = context.getComponent("cometd", CometdComponent.class);
        int port = AvailablePortFinder.getNextAvailable(23500);

        // act
        Endpoint result = component
            .createEndpoint(String.format("cometd://127.0.0.1:%s?crossOriginFilterOn=true&allowedOrigins=%s&filterPath=%s",
                 port, ALLOWED_ORIGINS, FILTER_PATH));

        // assert
        assertTrue(result instanceof CometdEndpoint);
        CometdEndpoint cometdEndpoint = (CometdEndpoint)result;
        assertTrue(cometdEndpoint.isCrossOriginFilterOn());
        assertEquals(ALLOWED_ORIGINS, cometdEndpoint.getAllowedOrigins());
        assertEquals(FILTER_PATH, cometdEndpoint.getFilterPath());
    }


    @Test
    public void testCrossOriginFilterAddedWhenOn() throws Exception {
        // setup
        CometdComponent component = context.getComponent("cometd", CometdComponent.class);
        Server server = new Server();

        when(endpoint.isCrossOriginFilterOn()).thenReturn(true);
        when(endpoint.getFilterPath()).thenReturn(FILTER_PATH);
        when(endpoint.getAllowedOrigins()).thenReturn(ALLOWED_ORIGINS);

        // act
        component.createServletForConnector(server, connector, endpoint);

        // assert
        ServletContextHandler handler = (ServletContextHandler) server.getHandler();
        assertEquals(1, handler.getServletHandler().getFilters().length);

        FilterHolder filterHolder = handler.getServletHandler().getFilters()[0];
        Filter filter = filterHolder.getFilter();
        assertTrue(filter instanceof CrossOriginFilter);
    }

    @Test
    public void testCrossOriginFilterNotAddedWhenOff() throws Exception {
        // setup
        CometdComponent component = context.getComponent("cometd", CometdComponent.class);
        Server server = new Server();

        when(endpoint.isCrossOriginFilterOn()).thenReturn(false);

        // act
        component.createServletForConnector(server, connector, endpoint);

        // assert
        ServletContextHandler handler = (ServletContextHandler) server.getHandler();
        assertEquals(0, handler.getServletHandler().getFilters().length);
    }
}

