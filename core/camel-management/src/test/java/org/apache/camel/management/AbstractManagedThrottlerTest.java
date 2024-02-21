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

import java.util.concurrent.TimeUnit;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_ROUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
public abstract class AbstractManagedThrottlerTest extends ManagementTestSupport {

    protected Long runTestManageThrottler() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(10);

        // Send in a first batch of 10 messages and check that the endpoint
        // gets them.  We'll check the total time of the second and third
        // batches as it seems that there is some time required to prime
        // things, which can vary significantly... particularly on slower
        // machines.
        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        assertMockEndpointsSatisfied();

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // get the object name for the delayer
        ObjectName throttlerName
                = getCamelObjectName(TYPE_PROCESSOR, "mythrottler");

        // use route to get the total time
        ObjectName routeName = getCamelObjectName(TYPE_ROUTE, "route1");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        // send in 10 messages
        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());

        Long total = (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

        // 10 * delay (100) + tolerance (200)
        assertTrue(total < 1200, "Should take at most 1.2 sec: was " + total);

        // change the throttler using JMX
        mbeanServer.setAttribute(throttlerName, new Attribute("MaximumRequests", (long) 2));

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        // send in another 10 messages
        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        Long requests = (Long) mbeanServer.getAttribute(throttlerName, "MaximumRequests");
        assertNotNull(requests);
        assertEquals(2, requests.longValue());

        completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());
        return (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

    }

    protected void runTestThrottleVisibleViaJmx() throws Exception {
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // use route to get the total time
        ObjectName routeName = getCamelObjectName(TYPE_ROUTE, "route2");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        getMockEndpoint("mock:end").expectedMessageCount(10);

        NotifyBuilder notifier = new NotifyBuilder(context).from("seda:throttleCount").whenReceived(5).create();

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:throttleCount", "Message " + i);
        }

        assertTrue(notifier.matches(2, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();

        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());
    }

    public void runTestThrottleAsyncVisibleViaJmx() throws Exception {
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // use route to get the total time
        ObjectName routeName = getCamelObjectName(TYPE_ROUTE, "route3");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        getMockEndpoint("mock:endAsync").expectedMessageCount(10);

        // we pick '5' because we are right in the middle of the number of messages
        // that have been and reduces any race conditions to minimal...
        NotifyBuilder notifier = new NotifyBuilder(context).from("seda:throttleCountAsync").whenReceived(5).create();

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:throttleCountAsync", "Message " + i);
        }

        assertTrue(notifier.matches(2, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();

        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());
    }

    @DisabledOnOs(OS.WINDOWS)
    @Test
    public void testThrottleAsyncExceptionVisableViaJmx() throws Exception {
        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // use route to get the total time
        ObjectName routeName = getCamelObjectName(TYPE_ROUTE, "route4");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        getMockEndpoint("mock:endAsyncException").expectedMessageCount(10);

        NotifyBuilder notifier = new NotifyBuilder(context).from("seda:throttleCountAsyncException").whenReceived(5).create();

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:throttleCountAsyncException", "Message " + i);
        }

        assertTrue(notifier.matches(2, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();

        // give a sec for exception handling to finish..
        Thread.sleep(500);

        // since all exchanges ended w/ exception, they are not completed
        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(0, completed.longValue());
    }

    @Test
    public void testRejectedExecution() throws Exception {
        // when delaying async, we can possibly fill up the execution queue
        //. which would through a RejectedExecutionException.. we need to make
        // sure that the delayedCount/throttledCount doesn't leak

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // use route to get the total time
        ObjectName routeName = getCamelObjectName(TYPE_ROUTE, "route2");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        MockEndpoint mock = getMockEndpoint("mock:endAsyncReject");
        // only one message (the first one) should get through because the rest should get delayed
        mock.expectedMessageCount(1);

        MockEndpoint exceptionMock = getMockEndpoint("mock:rejectedExceptionEndpoint1");
        exceptionMock.expectedMessageCount(9);

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:throttleCountRejectExecution", "Message " + i);
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRejectedExecutionCallerRuns() throws Exception {
        // when delaying async, we can possibly fill up the execution queue
        //. which would through a RejectedExecutionException.. we need to make
        // sure that the delayedCount/throttledCount doesn't leak

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // use route to get the total time
        ObjectName routeName = getCamelObjectName(TYPE_ROUTE, "route2");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        MockEndpoint mock = getMockEndpoint("mock:endAsyncRejectCallerRuns");
        // only one message (the first one) should get through because the rest should get delayed
        mock.expectedMessageCount(10);

        MockEndpoint exceptionMock = getMockEndpoint("mock:rejectedExceptionEndpoint");
        exceptionMock.expectedMessageCount(0);

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:throttleCountRejectExecutionCallerRuns", "Message " + i);
        }

        assertMockEndpointsSatisfied();
    }
}
