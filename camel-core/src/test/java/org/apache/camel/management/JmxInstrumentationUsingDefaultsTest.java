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

import java.util.Iterator;
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
 * This test verifies JMX is enabled by default and it uses local mbean
 * server to conduct the test as connector server is not enabled by default.
 *
 * @version $Revision$
 *
 */
public class JmxInstrumentationUsingDefaultsTest extends ContextTestSupport {

    protected String domainName = DefaultInstrumentationAgent.DEFAULT_DOMAIN;
    protected MBeanServerConnection mbsc;
    protected long sleepForConnection;

    public void testMBeansRegistered() throws Exception {
        if (!Boolean.getBoolean(JmxSystemPropertyKeys.USE_PLATFORM_MBS)) {
            assertEquals(domainName, mbsc.getDefaultDomain());
        }

        resolveMandatoryEndpoint("mock:end", MockEndpoint.class);

        Set s = mbsc.queryNames(
                new ObjectName(domainName + ":type=endpoint,*"), null);
        assertEquals("Could not find 2 endpoints: " + s, 2, s.size());

        s = mbsc.queryNames(
                new ObjectName(domainName + ":type=context,*"), null);
        assertEquals("Could not find 1 context: " + s, 1, s.size());

        s = mbsc.queryNames(
                new ObjectName(domainName + ":type=processor,*"), null);
        assertEquals("Could not find 1 processor: " + s, 1, s.size());

        s = mbsc.queryNames(
                new ObjectName(domainName + ":type=route,*"), null);
        assertEquals("Could not find 1 route: " + s, 1, s.size());

    }

    public void testCounters() throws Exception {

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:end", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived("<hello>world!</hello>");
        sendBody("direct:start", "<hello>world!</hello>");

        resultEndpoint.assertIsSatisfied();

        verifyCounter(mbsc, new ObjectName(domainName + ":type=route,*"));
        verifyCounter(mbsc, new ObjectName(domainName + ":type=processor,*"));

    }

    protected void verifyCounter(MBeanServerConnection beanServer, ObjectName name) throws Exception {
        Set s = beanServer.queryNames(name, null);
        assertEquals("Found mbeans: " + s, 1, s.size());

        Iterator iter = s.iterator();
        ObjectName pcob = (ObjectName)iter.next();

        Long valueofNumExchanges = (Long)beanServer.getAttribute(pcob, "NumExchanges");
        assertNotNull("Expected attribute found. MBean registered under a "
                      + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
                      valueofNumExchanges);
        assertTrue(valueofNumExchanges == 1);
        Long valueofNumCompleted = (Long)beanServer.getAttribute(pcob, "NumCompleted");
        assertNotNull("Expected attribute found. MBean registered under a "
                      + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
                      valueofNumCompleted);
        assertTrue(valueofNumCompleted == 1);
        Long valueofNumFailed = (Long)beanServer.getAttribute(pcob, "NumFailed");
        assertNotNull("Expected attribute found. MBean registered under a "
                      + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
                      valueofNumFailed);
        assertTrue(valueofNumFailed == 0);
        Double valueofMinProcessingTime = (Double)beanServer.getAttribute(pcob, "MinProcessingTimeMillis");
        assertNotNull("Expected attribute found. MBean registered under a "
                      + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
                      valueofMinProcessingTime);
        assertTrue(valueofMinProcessingTime > 0);
        Double valueofMaxProcessingTime = (Double)beanServer.getAttribute(pcob, "MaxProcessingTimeMillis");
        assertNotNull("Expected attribute found. MBean registered under a "
                      + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
                      valueofMaxProcessingTime);
        assertTrue(valueofMaxProcessingTime > 0);
        Double valueofMeanProcessingTime = (Double)beanServer.getAttribute(pcob, "MeanProcessingTimeMillis");
        assertNotNull("Expected attribute found. MBean registered under a "
                      + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
                      valueofMeanProcessingTime);
        assertTrue(valueofMeanProcessingTime >= valueofMinProcessingTime
                   && valueofMeanProcessingTime <= valueofMaxProcessingTime);
        Double totalProcessingTime = (Double)beanServer.getAttribute(pcob, "TotalProcessingTimeMillis");
        assertNotNull("Expected attribute found. MBean registered under a "
                      + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
                      totalProcessingTime);
        assertTrue(totalProcessingTime > 0);

        assertNotNull("Expected first completion time to be available",
                beanServer.getAttribute(pcob, "FirstExchangeCompletionTime"));

        assertNotNull("Expected last completion time to be available",
                beanServer.getAttribute(pcob, "LastExchangeCompletionTime"));

    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").to("mock:end");
            }
        };
    }

    @Override
    protected void tearDown() throws Exception {
        releaseMBeanServers();
        mbsc = null;
        super.tearDown();
    }

    @SuppressWarnings("unchecked")
    protected void releaseMBeanServers() {
        List<MBeanServer> servers =
            (List<MBeanServer>)MBeanServerFactory.findMBeanServer(null);

        for (MBeanServer server : servers) {
            MBeanServerFactory.releaseMBeanServer(server);
        }
    }

    @Override
    protected void setUp() throws Exception {
        releaseMBeanServers();
        super.setUp();
        Thread.sleep(sleepForConnection);
        mbsc = getMBeanConnection();
    }

    @SuppressWarnings("unchecked")
    protected MBeanServerConnection getMBeanConnection() throws Exception {
        if (mbsc == null) {
            List<MBeanServer> servers =
                    (List<MBeanServer>)MBeanServerFactory.findMBeanServer(null);

            for (MBeanServer server : servers) {
                if (domainName.equals(server.getDefaultDomain())) {

                    mbsc = server;
                    break;
                }
            }
        }
        return mbsc;
    }
}
