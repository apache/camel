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
package org.apache.camel.management;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedServiceUrlPathTest extends ManagementTestSupport {

    private static final String JMXSERVICEURL = "service:jmx:rmi:///jndi/rmi://localhost:2113/foo/bar";
    private JMXConnector clientConnector;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // START SNIPPET: e1
        context.getManagementStrategy().getManagementAgent().setServiceUrlPath("/foo/bar");
        context.getManagementStrategy().getManagementAgent().setRegistryPort(2113);
        context.getManagementStrategy().getManagementAgent().setCreateConnector(true);
        // END SNIPPET: e1

        return context;
    }

    @Override
    protected boolean canRunOnThisPlatform() {
        // does not work well when maven surefire plugin is set to forkmode=once
        return false;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        try {
            if (clientConnector != null) {
                clientConnector.close();
            }
        } catch (Throwable e) {
            // ignore
        }
    }

    public void testConnectToJmx() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        clientConnector = JMXConnectorFactory.connect(new JMXServiceURL(JMXSERVICEURL), null);
        MBeanServerConnection mbeanServer = clientConnector.getMBeanServerConnection();

        ObjectName name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"direct://start\"");
        String uri = (String) mbeanServer.getAttribute(name, "EndpointUri");
        assertEquals("direct://start", uri);

        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"log://foo\"");
        uri = (String) mbeanServer.getAttribute(name, "EndpointUri");
        assertEquals("log://foo", uri);

        name = ObjectName.getInstance("org.apache.camel:context=camel-1,type=endpoints,name=\"mock://result\"");
        uri = (String) mbeanServer.getAttribute(name, "EndpointUri");
        assertEquals("mock://result", uri);

        String id = (String) mbeanServer.getAttribute(name, "CamelId");
        assertEquals("camel-1", id);

        Boolean singleton = (Boolean) mbeanServer.getAttribute(name, "Singleton");
        assertEquals(Boolean.TRUE, singleton);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("log:foo").to("mock:result");
            }
        };
    }

}