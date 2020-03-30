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
package org.apache.camel.management;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.api.management.JmxSystemPropertyKeys;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.awaitility.Awaitility.await;

/**
 * This test verifies JMX is enabled by default and it uses local mbean
 * server to conduct the test as connector server is not enabled by default.
 */
public class JmxInstrumentationUsingDefaultsTest extends ContextTestSupport {

    protected String domainName = DefaultManagementAgent.DEFAULT_DOMAIN;
    protected MBeanServerConnection mbsc;

    @Override
    protected boolean useJmx() {
        return true;
    }

    protected void assertDefaultDomain() throws IOException {
        if (System.getProperty(JmxSystemPropertyKeys.USE_PLATFORM_MBS) != null
                && !Boolean.getBoolean(JmxSystemPropertyKeys.USE_PLATFORM_MBS)) {
            assertEquals(domainName, mbsc.getDefaultDomain());
        }
    }

    @Test
    public void testMBeansRegistered() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        assertDefaultDomain();

        template.sendBody("direct:start", "Hello World");

        resolveMandatoryEndpoint("mock:end", MockEndpoint.class);

        Set<ObjectName> s = mbsc.queryNames(new ObjectName(domainName + ":type=endpoints,*"), null);
        assertEquals("Could not find 2 endpoints: " + s, 2, s.size());

        s = mbsc.queryNames(new ObjectName(domainName + ":type=context,*"), null);
        assertEquals("Could not find 1 context: " + s, 1, s.size());

        s = mbsc.queryNames(new ObjectName(domainName + ":type=processors,*"), null);
        assertEquals("Could not find 1 processors: " + s, 2, s.size());

        s = mbsc.queryNames(new ObjectName(domainName + ":type=consumers,*"), null);
        assertEquals("Could not find 1 consumers: " + s, 1, s.size());

        s = mbsc.queryNames(new ObjectName(domainName + ":type=producers,*"), null);
        assertEquals("Could not find 1 producers: " + s, 1, s.size());

        s = mbsc.queryNames(new ObjectName(domainName + ":type=routes,*"), null);
        assertEquals("Could not find 1 route: " + s, 1, s.size());
    }

    @Test
    public void testCounters() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:end", MockEndpoint.class);
        resultEndpoint.expectedBodiesReceived("<hello>world!</hello>");
        sendBody("direct:start", "<hello>world!</hello>");

        resultEndpoint.assertIsSatisfied();

        verifyCounter(mbsc, new ObjectName(domainName + ":type=routes,*"));
    }

    protected void verifyCounter(MBeanServerConnection beanServer, ObjectName name) throws Exception {
        Set<ObjectName> s = beanServer.queryNames(name, null);
        assertEquals("Found mbeans: " + s, 1, s.size());

        Iterator<ObjectName> iter = s.iterator();
        ObjectName pcob = iter.next();

        Long valueofNumExchanges = (Long)beanServer.getAttribute(pcob, "ExchangesTotal");
        assertNotNull("Expected attribute found. MBean registered under a "
            + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
            valueofNumExchanges);
        assertEquals(Long.valueOf(1), valueofNumExchanges);

        Long valueofNumCompleted = (Long)beanServer.getAttribute(pcob, "ExchangesCompleted");
        assertNotNull("Expected attribute found. MBean registered under a "
            + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
            valueofNumCompleted);
        assertEquals(Long.valueOf(1), valueofNumCompleted);

        Long valueofNumFailed = (Long)beanServer.getAttribute(pcob, "ExchangesFailed");
        assertNotNull("Expected attribute found. MBean registered under a "
            + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
            valueofNumFailed);
        assertEquals(Long.valueOf(0), valueofNumFailed);

        Long valueofMinProcessingTime = (Long)beanServer.getAttribute(pcob, "MinProcessingTime");
        assertNotNull("Expected attribute found. MBean registered under a "
            + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
            valueofMinProcessingTime);
        assertTrue(valueofMinProcessingTime >= 0);

        Long valueofMaxProcessingTime = (Long)beanServer.getAttribute(pcob, "MaxProcessingTime");
        assertNotNull("Expected attribute found. MBean registered under a "
            + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
            valueofMaxProcessingTime);
        assertTrue(valueofMaxProcessingTime >= 0);

        Long valueofMeanProcessingTime = (Long)beanServer.getAttribute(pcob, "MeanProcessingTime");
        assertNotNull("Expected attribute found. MBean registered under a "
            + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
            valueofMeanProcessingTime);
        assertTrue(valueofMeanProcessingTime >= valueofMinProcessingTime
            && valueofMeanProcessingTime <= valueofMaxProcessingTime);

        Long totalProcessingTime = (Long)beanServer.getAttribute(pcob, "TotalProcessingTime");
        assertNotNull("Expected attribute found. MBean registered under a "
            + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
            totalProcessingTime);
        assertTrue(totalProcessingTime >= 0);

        Long lastProcessingTime = (Long)beanServer.getAttribute(pcob, "LastProcessingTime");
        assertNotNull("Expected attribute found. MBean registered under a "
            + "'<domain>:name=Stats,*' key must be of type PerformanceCounter.class",
            lastProcessingTime);
        assertTrue(lastProcessingTime >= 0);

        assertNotNull("Expected first completion time to be available",
            beanServer.getAttribute(pcob, "FirstExchangeCompletedTimestamp"));

        assertNotNull("Expected last completion time to be available",
            beanServer.getAttribute(pcob, "LastExchangeCompletedTimestamp"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // need a little delay for fast computers being able to process
                // the exchange in 0 millis and we need to simulate a little computation time
                from("direct:start").delay(10).to("mock:end");
            }
        };
    }

    @Override
    @Before
    public void setUp() throws Exception {
        releaseMBeanServers();
        super.setUp();

        await().atMost(3, TimeUnit.SECONDS).ignoreExceptions().until(() -> {
            mbsc = getMBeanConnection();
            return true;
        });
    }

    @Override
    @After
    public void tearDown() throws Exception {
        try {
            super.tearDown();
            releaseMBeanServers();
            mbsc = null;
        } finally {
            // restore environment to original state
            // the following properties may have been set by specialization of this test class
            System.clearProperty(JmxSystemPropertyKeys.USE_PLATFORM_MBS);
            System.clearProperty(JmxSystemPropertyKeys.DOMAIN);
            System.clearProperty(JmxSystemPropertyKeys.MBEAN_DOMAIN);
        }
    }

    protected void releaseMBeanServers() {
        for (MBeanServer server : MBeanServerFactory.findMBeanServer(null)) {
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
