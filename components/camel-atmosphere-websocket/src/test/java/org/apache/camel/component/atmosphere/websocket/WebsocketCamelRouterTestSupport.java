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
package org.apache.camel.component.atmosphere.websocket;

import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class WebsocketCamelRouterTestSupport extends CamelTestSupport {
    public static final String CONTEXT = "/mycontext";
    public static final String CONTEXT_URL = "http://localhost/mycontext";
    protected static final int PORT = AvailablePortFinder.getNextAvailable();
    protected boolean startCamelContext = true;
    
    protected Server server;

    protected ServletHolder servletHolder;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        server = new Server(PORT);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        if (startCamelContext) {
            super.setUp();
        }

        servletHolder = new ServletHolder(new CamelWebSocketServlet());
        servletHolder.setName("CamelWsServlet");
        context.addServlet(servletHolder, "/*");

        server.start();
    }
    
    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (startCamelContext) {
            super.tearDown();
        }
        
        server.stop();
        server.destroy();
    }
    

}
