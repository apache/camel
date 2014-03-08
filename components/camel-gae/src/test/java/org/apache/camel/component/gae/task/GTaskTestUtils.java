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
package org.apache.camel.component.gae.task;

import java.io.File;

import com.google.appengine.tools.development.LocalServerEnvironment;
import com.google.appengine.tools.development.testing.LocalServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import org.apache.camel.CamelContext;
import org.apache.camel.component.gae.TestConfig;
import org.apache.camel.component.servlet.CamelHttpTransportServlet;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public final class GTaskTestUtils {

    private static CamelContext context;
    private static GTaskComponent component;

    static {
        SimpleRegistry registry = new SimpleRegistry();
        registry.put("customBinding", new GTaskBinding() { });  // subclass
        context = new DefaultCamelContext(registry);
        component = new GTaskComponent();
        component.setCamelContext(context);
    }

    private GTaskTestUtils() {
    }
    
    public static CamelContext getCamelContext() {
        return context;
    }
    
    public static Server createTestServer() {
        ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        handler.addServlet(new ServletHolder(new CamelHttpTransportServlet()), "/camel/*");
        handler.setContextPath("/");
        Server server = new Server(TestConfig.getPort());
        server.setHandler(handler);
        return server;
    }

    public static GTaskEndpoint createEndpoint(String endpointUri) throws Exception {
        return (GTaskEndpoint)component.createEndpoint(endpointUri);
    }
    
    public static LocalServiceTestHelper newLocalServiceTestHelper(LocalServiceTestConfig... configs) {
        return new QueueServiceTestHelper(configs);
    }

    private static class QueueServiceTestHelper extends LocalServiceTestHelper {

        public QueueServiceTestHelper(LocalServiceTestConfig... configs) {
            super(configs);
        }

        @Override
        protected LocalServerEnvironment newLocalServerEnvironment() {
            return new LocalServerEnvironment() {

                public File getAppDir() {
                    return new File(".");
                }

                public String getAddress() {
                    return "localhost";
                }

                public int getPort() {
                    return TestConfig.getPort();
                }

                public String getHostName() {
                    return "localhost";
                }

                public void waitForServerToStart() {
                }

                public boolean enforceApiDeadlines() {
                    return false;
                }

                public boolean simulateProductionLatencies() {
                    return false;
                }
            };
        }
    }

}
