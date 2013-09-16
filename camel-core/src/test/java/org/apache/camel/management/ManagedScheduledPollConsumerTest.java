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
import java.util.concurrent.TimeUnit;
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.RouteBuilder;

/**
 * @version 
 */
public class ManagedScheduledPollConsumerTest extends ManagementTestSupport {

    public void testScheduledPollConsumer() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        MBeanServer mbeanServer = getMBeanServer();

        Set<ObjectName> set = mbeanServer.queryNames(new ObjectName("*:type=consumers,*"), null);
        assertEquals(1, set.size());

        ObjectName on = set.iterator().next();

        assertTrue("Should be registered", mbeanServer.isRegistered(on));
        String uri = (String) mbeanServer.getAttribute(on, "EndpointUri");
        assertEquals("file://target/foo?backoffErrorThreshold=3&backoffIdleThreshold=2&backoffMultiplier=4&delay=4000", uri);

        Long delay = (Long) mbeanServer.getAttribute(on, "Delay");
        assertEquals(4000, delay.longValue());

        Long initialDelay = (Long) mbeanServer.getAttribute(on, "InitialDelay");
        assertEquals(1000, initialDelay.longValue());

        Boolean fixedDelay = (Boolean) mbeanServer.getAttribute(on, "UseFixedDelay");
        assertEquals(Boolean.TRUE, fixedDelay);

        Boolean schedulerStarted = (Boolean) mbeanServer.getAttribute(on, "SchedulerStarted");
        assertEquals(Boolean.TRUE, schedulerStarted);

        String timeUnit = (String) mbeanServer.getAttribute(on, "TimeUnit");
        assertEquals(TimeUnit.MILLISECONDS.toString(), timeUnit);

        Integer backoffMultiplier = (Integer) mbeanServer.getAttribute(on, "BackoffMultiplier");
        assertEquals(4, backoffMultiplier.longValue());

        Integer backoffCounter = (Integer) mbeanServer.getAttribute(on, "BackoffCounter");
        assertEquals(0, backoffCounter.longValue());

        Integer backoffIdleThreshold = (Integer) mbeanServer.getAttribute(on, "BackoffIdleThreshold");
        assertEquals(2, backoffIdleThreshold.longValue());

        Integer backoffErrorThreshold = (Integer) mbeanServer.getAttribute(on, "BackoffErrorThreshold");
        assertEquals(3, backoffErrorThreshold.longValue());

        String routeId = (String) mbeanServer.getAttribute(on, "RouteId");
        assertEquals("route1", routeId);

        // stop it
        mbeanServer.invoke(on, "stop", null, null);

        schedulerStarted = (Boolean) mbeanServer.getAttribute(on, "SchedulerStarted");
        assertEquals(Boolean.FALSE, schedulerStarted);

        // change delay
        mbeanServer.setAttribute(on, new Attribute("Delay", 2000));

        // start it
        mbeanServer.invoke(on, "start", null, null);

        delay = (Long) mbeanServer.getAttribute(on, "Delay");
        assertEquals(2000, delay.longValue());

        // change some options
        mbeanServer.setAttribute(on, new Attribute("UseFixedDelay", Boolean.FALSE));
        fixedDelay = (Boolean) mbeanServer.getAttribute(on, "UseFixedDelay");
        assertEquals(Boolean.FALSE, fixedDelay);

        mbeanServer.setAttribute(on, new Attribute("TimeUnit", TimeUnit.SECONDS.name()));
        timeUnit = (String) mbeanServer.getAttribute(on, "TimeUnit");
        assertEquals(TimeUnit.SECONDS.toString(), timeUnit);

        mbeanServer.setAttribute(on, new Attribute("InitialDelay", Long.valueOf("2000")));
        initialDelay = (Long) mbeanServer.getAttribute(on, "InitialDelay");
        assertEquals(2000, initialDelay.longValue());

        context.stop();
        assertFalse("Should no longer be registered", mbeanServer.isRegistered(on));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://target/foo?delay=4000&backoffMultiplier=4&backoffIdleThreshold=2&backoffErrorThreshold=3").to("mock:result");
            }
        };
    }

}