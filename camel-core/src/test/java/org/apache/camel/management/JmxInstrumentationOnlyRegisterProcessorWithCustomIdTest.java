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

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision$
 */
public class JmxInstrumentationOnlyRegisterProcessorWithCustomIdTest extends ContextTestSupport {

    protected String domainName = DefaultInstrumentationAgent.DEFAULT_DOMAIN;
    protected MBeanServerConnection mbsc;
    protected long sleepForConnection;

    public void testCustomId() throws Exception {
        if (System.getProperty(JmxSystemPropertyKeys.USE_PLATFORM_MBS) != null
                && !Boolean.getBoolean(JmxSystemPropertyKeys.USE_PLATFORM_MBS)) {
            assertEquals(domainName, mbsc.getDefaultDomain());
        }

        Set s = mbsc.queryNames(new ObjectName(domainName + ":type=endpoints,*"), null);
        assertEquals("Could not find 2 endpoints: " + s, 6, s.size());

        s = mbsc.queryNames(new ObjectName(domainName + ":type=context,*"), null);
        assertEquals("Could not find 1 context: " + s, 1, s.size());

        s = mbsc.queryNames(new ObjectName(domainName + ":type=processors,*"), null);
        assertEquals("Could not find 1 processor: " + s, 1, s.size());
        // should be mock foo
        ObjectName on = (ObjectName) s.iterator().next();
        assertEquals("myfoo", on.getKeyProperty("nodeid"));

        s = mbsc.queryNames(new ObjectName(domainName + ":type=routes,*"), null);
        assertEquals("Could not find 1 route: " + s, 2, s.size());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void tearDown() throws Exception {
        System.clearProperty(JmxSystemPropertyKeys.ONLY_REGISTER_PROCESSOR_WITH_CUSTOM_ID);
        releaseMBeanServers();
        mbsc = null;
        super.tearDown();
    }

    @SuppressWarnings("unchecked")
    protected void releaseMBeanServers() {
        List<MBeanServer> servers = (List<MBeanServer>) MBeanServerFactory.findMBeanServer(null);
        for (MBeanServer server : servers) {
            MBeanServerFactory.releaseMBeanServer(server);
        }
    }

    @Override
    protected void setUp() throws Exception {
        System.setProperty(JmxSystemPropertyKeys.ONLY_REGISTER_PROCESSOR_WITH_CUSTOM_ID, "true");
        releaseMBeanServers();
        super.setUp();
        Thread.sleep(sleepForConnection);
        mbsc = getMBeanConnection();
    }


    protected MBeanServerConnection getMBeanConnection() throws Exception {
        if (mbsc == null) {
            mbsc = ManagementFactory.getPlatformMBeanServer();
        }
        return mbsc;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    // sets the id of the previous node, that is the mock:foo
                    .to("mock:foo").id("myfoo")
                    .delay(10)
                    .to("mock:result");

                from("direct:other")
                    .to("mock:bar")
                    .delay(10)
                    .to("mock:other");
            }
        };
    }
}
