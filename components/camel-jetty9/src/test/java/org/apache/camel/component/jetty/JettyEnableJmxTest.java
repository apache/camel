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
package org.apache.camel.component.jetty;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class JettyEnableJmxTest extends BaseJettyTest {
    
    private String serverUri0;
    private String serverUri1;
    private String serverUri2;
    private String serverUri3;
    private MBeanServerConnection mbsc;

    @Override
    public void tearDown() throws Exception {
        releaseMBeanServers();
        mbsc = null;
        super.tearDown();
        disableJMX();
    }

    @Override
    public void setUp() throws Exception {
        enableJMX();
        releaseMBeanServers();
        super.setUp();
        mbsc = getMBeanConnection();
    }    
    
    @Test
    public void testEnableJmxProperty() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        String expectedBody = "<html><body>foo</body></html>";
        mock.expectedBodiesReceived(expectedBody, expectedBody, expectedBody, expectedBody);
        mock.expectedHeaderReceived("x", "foo");

        template.requestBody(serverUri0 + "&x=foo", null, Object.class);
        template.requestBody(serverUri1 + "&x=foo", null, Object.class);
        template.requestBody(serverUri2 + "&x=foo", null, Object.class);
        template.requestBody(serverUri3 + "&x=foo", null, Object.class);

        assertMockEndpointsSatisfied();
        
        Set<ObjectName> s = mbsc.queryNames(new ObjectName("org.eclipse.jetty.server:type=server,*"), null);
        assertEquals("Could not find 2 Jetty Server: " + s, 2, s.size());
    }
    
    @Test
    public void testShutdown() throws Exception {
        Set<ObjectName> s = mbsc.queryNames(new ObjectName("org.eclipse.jetty.server:type=server,*"), null);
        assertEquals("Could not find 2 Jetty Server: " + s, 2, s.size());
        
        context.stop();
        
        s = mbsc.queryNames(new ObjectName("org.eclipse.jetty.server:type=server,*"), null);
        assertEquals("Could not find 0 Jetty Server: " + s, 0, s.size());
    }
    
    @Test
    public void testEndpointDisconnect() throws Exception {
        Set<ObjectName> s = mbsc.queryNames(new ObjectName("org.eclipse.jetty.server:type=server,*"), null);
        assertEquals("Could not find 2 Jetty Server: " + s, 2, s.size());
        
        context.stopRoute("route0");
        
        s = mbsc.queryNames(new ObjectName("org.eclipse.jetty.server:type=server,*"), null);
        assertEquals("Could not find 1 Jetty Server: " + s, 1, s.size());
        
        context.stopRoute("route2");
        context.stopRoute("route3");
        
        s = mbsc.queryNames(new ObjectName("org.eclipse.jetty.server:type=server,*"), null);
        assertEquals("Could not find 1 Jetty Server: " + s, 1, s.size());
        
        context.stopRoute("route1");
        
        s = mbsc.queryNames(new ObjectName("org.eclipse.jetty.server:type=server,*"), null);
        assertEquals("Could not find 0 Jetty Server: " + s, 0, s.size());
    }

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                serverUri0 = "http://localhost:" + getNextPort() + "/myservice?enableJmx=true";
                serverUri1 = "http://localhost:" + getNextPort() + "/myservice?enableJmx=true";
                serverUri2 = "http://localhost:" + getNextPort() + "/myservice?enableJmx=false";
                serverUri3 = "http://localhost:" + getNextPort() + "/myservice?enableJmx=false";

                from("jetty:" + serverUri0)
                    .routeId("route0")
                    .setBody().simple("<html><body>${in.header.x}</body></html>")
                    .to("mock:result");
                
                from("jetty:" + serverUri1)
                    .routeId("route1")
                    .setBody().simple("<html><body>${in.header.x}</body></html>")
                    .to("mock:result");
                
                from("jetty:" + serverUri2)
                    .routeId("route2")
                    .setBody().simple("<html><body>${in.header.x}</body></html>")
                    .to("mock:result");
                
                from("jetty:" + serverUri3)
                    .routeId("route3")
                    .setBody().simple("<html><body>${in.header.x}</body></html>")
                    .to("mock:result");
            }
        };
    }
    
    protected void releaseMBeanServers() {
        List<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);

        for (MBeanServer server : servers) {
            MBeanServerFactory.releaseMBeanServer(server);
        }
    }
    
    protected MBeanServerConnection getMBeanConnection() throws Exception {
        if (mbsc == null) {
            mbsc = ManagementFactory.getPlatformMBeanServer();
        }
        return mbsc;
    }
}
