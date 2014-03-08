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
package org.apache.camel.component.gae.http;

import java.net.URL;

import com.google.appengine.api.urlfetch.HTTPRequest;
import org.apache.camel.CamelContext;
import org.apache.camel.component.gae.TestConfig;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public final class GHttpTestUtils {

    private static CamelContext context;
    private static GHttpComponent component;

    static {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("customBinding", new GHttpBinding() { }); // subclass
        context = new DefaultCamelContext(registry);
        component = new GHttpComponent();
        component.setCamelContext(context);
    }
    
    private GHttpTestUtils() {
    }
    
    public static CamelContext getCamelContext() {
        return context;
    }

    public static Server createTestServer() {
        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        handler.addServlet(new ServletHolder(new GHttpTestServlet()), "/*");
        handler.setContextPath("/");
        Server server = new Server(TestConfig.getPort());
        server.setHandler(handler);
        return server;
    }

    public static GHttpEndpoint createEndpoint(String endpointUri) throws Exception {
        return (GHttpEndpoint)component.createEndpoint(endpointUri);
    }
    
    public static HTTPRequest createRequest() throws Exception {
        return createRequest("http://localhost:8080");
    }
    
    public static HTTPRequest createRequest(String url) throws Exception {
        return new HTTPRequest(new URL(url));
    }
    
}
