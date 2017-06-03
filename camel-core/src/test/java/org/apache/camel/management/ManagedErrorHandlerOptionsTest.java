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

import java.util.Set;
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ManagedErrorHandlerOptionsTest extends ManagementTestSupport {

    private static int counter;

    public void testManagedErrorHandlerOptions() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        counter = 0;

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=errorhandlers,*"), null);
        assertEquals(1, set.size());
        ObjectName on = set.iterator().next();

        mbeanServer.setAttribute(on, new Attribute("MaximumRedeliveries", 3));
        Integer max = (Integer) mbeanServer.getAttribute(on, "MaximumRedeliveries");
        assertEquals(3, max.intValue());

        mbeanServer.setAttribute(on, new Attribute("MaximumRedeliveryDelay", Long.valueOf("20000")));
        Long delay = (Long) mbeanServer.getAttribute(on, "MaximumRedeliveryDelay");
        assertEquals(20000, delay.longValue());

        mbeanServer.setAttribute(on, new Attribute("RedeliveryDelay", Long.valueOf("250")));
        delay = (Long) mbeanServer.getAttribute(on, "RedeliveryDelay");
        assertEquals(250, delay.longValue());

        String camelId = (String) mbeanServer.getAttribute(on, "CamelId");
        assertEquals("camel-1", camelId);

        mbeanServer.setAttribute(on, new Attribute("BackOffMultiplier", Double.valueOf("3.5")));
        Double backoff = (Double) mbeanServer.getAttribute(on, "BackOffMultiplier");
        assertNotNull(backoff);

        mbeanServer.setAttribute(on, new Attribute("CollisionAvoidanceFactor", Double.valueOf("1.5")));
        Double cf = (Double) mbeanServer.getAttribute(on, "CollisionAvoidanceFactor");
        assertNotNull(cf);

        mbeanServer.setAttribute(on, new Attribute("CollisionAvoidancePercent", Double.valueOf("75")));
        Double cp = (Double) mbeanServer.getAttribute(on, "CollisionAvoidancePercent");
        assertNotNull(cp);

        mbeanServer.setAttribute(on, new Attribute("DelayPattern", "0:1000;5:5000"));
        String dp = (String) mbeanServer.getAttribute(on, "DelayPattern");
        assertNotNull(dp);

        mbeanServer.setAttribute(on, new Attribute("RetriesExhaustedLogLevel", "WARN"));
        String ell = (String) mbeanServer.getAttribute(on, "RetriesExhaustedLogLevel");
        assertEquals(LoggingLevel.WARN.name(), ell);

        mbeanServer.setAttribute(on, new Attribute("RetryAttemptedLogLevel", "WARN"));
        String rll = (String) mbeanServer.getAttribute(on, "RetryAttemptedLogLevel");
        assertEquals(LoggingLevel.WARN.name(), rll);

        mbeanServer.setAttribute(on, new Attribute("LogStackTrace", Boolean.TRUE));
        Boolean lst = (Boolean) mbeanServer.getAttribute(on, "LogStackTrace");
        assertEquals(true, lst.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("UseCollisionAvoidance", Boolean.TRUE));
        Boolean uca = (Boolean) mbeanServer.getAttribute(on, "UseCollisionAvoidance");
        assertEquals(true, uca.booleanValue());

        mbeanServer.setAttribute(on, new Attribute("UseExponentialBackOff", Boolean.TRUE));
        Boolean uebf = (Boolean) mbeanServer.getAttribute(on, "UseExponentialBackOff");
        assertEquals(true, uebf.booleanValue());

        Boolean ne = (Boolean) mbeanServer.getAttribute(on, "DeadLetterHandleNewException");
        assertEquals(false, ne.booleanValue());

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertEquals(3, counter);

        assertMockEndpointsSatisfied();

        // now change to 0 attempts and try again
        counter = 0;
        mock.reset();
        mock.expectedMessageCount(0);
        mbeanServer.setAttribute(on, new Attribute("MaximumRedeliveries", 0));

        try {
            template.sendBody("direct:start", "Bye World");
            fail("Should have thrown exception");
        } catch (CamelExecutionException e) {
            IllegalArgumentException cause = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertEquals("Forced", cause.getMessage());

        }

        assertEquals(1, counter);

        // and should now be 0
        max = (Integer) mbeanServer.getAttribute(on, "MaximumRedeliveries");
        assertEquals(0, max.intValue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(defaultErrorHandler().maximumRedeliveries(5));

                from("direct:start").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        counter++;
                        if (counter < 3) {
                            throw new  IllegalArgumentException("Forced");
                        }
                    }
                }).to("mock:result");
            }
        };
    }
}