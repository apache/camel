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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version
 */
public class ManagedThrottlerTest extends ManagementTestSupport {

    public void testManageThrottler() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

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
        ObjectName throttlerName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"mythrottler\"");

        // use route to get the total time
        ObjectName routeName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route1\"");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        // send in 10 messages
        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());

        Long timePeriod = (Long) mbeanServer.getAttribute(throttlerName, "TimePeriodMillis");
        assertEquals(250, timePeriod.longValue());

        Long total = (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

        assertTrue("Should take at most 1.0 sec: was " + total, total < 1000);

        // change the throttler using JMX
        mbeanServer.setAttribute(throttlerName, new Attribute("MaximumRequestsPerPeriod", (long) 2));

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        // send in another 10 messages
        for (int i = 0; i < 10; i++) {
            template.sendBody("direct:start", "Message " + i);
        }

        Long period = (Long) mbeanServer.getAttribute(throttlerName, "MaximumRequestsPerPeriod");
        assertNotNull(period);
        assertEquals(2, period.longValue());

        completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());
        total = (Long) mbeanServer.getAttribute(routeName, "TotalProcessingTime");

        assertTrue("Should be around 1 sec now: was " + total, total > 1000);
    }

    public void testThrottleVisableViaJmx() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }
        if (isPlatform("windows")) {
            // windows needs more sleep to read updated jmx values so we skip as we dont want further delays in core tests
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        // get the object name for the delayer
        ObjectName throttlerName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"mythrottler2\"");

        // use route to get the total time
        ObjectName routeName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route2\"");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        getMockEndpoint("mock:end").expectedMessageCount(10);

        NotifyBuilder notifier = new NotifyBuilder(context).
                from("seda:throttleCount").whenReceived(5).create();

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:throttleCount", "Message " + i);
        }

        assertTrue(notifier.matches(2, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();

        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());
    }

    public void testThrottleAsyncVisableViaJmx() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }
        if (isPlatform("windows")) {
            // windows needs more sleep to read updated jmx values so we skip as we dont want further delays in core tests
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        // get the object name for the delayer
        ObjectName throttlerName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"mythrottler3\"");

        // use route to get the total time
        ObjectName routeName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route3\"");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        getMockEndpoint("mock:endAsync").expectedMessageCount(10);

        // we pick '5' because we are right in the middle of the number of messages
        // that have been and reduces any race conditions to minimal...
        NotifyBuilder notifier = new NotifyBuilder(context).
                from("seda:throttleCountAsync").whenReceived(5).create();

        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:throttleCountAsync", "Message " + i);
        }

        assertTrue(notifier.matches(2, TimeUnit.SECONDS));
        assertMockEndpointsSatisfied();

        Long completed = (Long) mbeanServer.getAttribute(routeName, "ExchangesCompleted");
        assertEquals(10, completed.longValue());
    }

    public void testThrottleAsyncExceptionVisableViaJmx() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }
        if (isPlatform("windows")) {
            // windows needs more sleep to read updated jmx values so we skip as we dont want further delays in core tests
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();

        // use route to get the total time
        ObjectName routeName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route4\"");

        // reset the counters
        mbeanServer.invoke(routeName, "reset", null, null);

        getMockEndpoint("mock:endAsyncException").expectedMessageCount(10);

        NotifyBuilder notifier = new NotifyBuilder(context).
                from("seda:throttleCountAsyncException").whenReceived(5).create();

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

    public void testRejectedExecution() throws Exception {
        // when delaying async, we can possibly fill up the execution queue
        //. which would through a RejectedExecutionException.. we need to make
        // sure that the delayedCount/throttledCount doesn't leak

        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        // get the object name for the delayer
        ObjectName throttlerName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"mythrottler2\"");

        // use route to get the total time
        ObjectName routeName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route2\"");

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

    public void testRejectedExecutionCallerRuns() throws Exception {
        // when delaying async, we can possibly fill up the execution queue
        //. which would through a RejectedExecutionException.. we need to make
        // sure that the delayedCount/throttledCount doesn't leak

        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

        // get the stats for the route
        MBeanServer mbeanServer = getMBeanServer();
        // get the object name for the delayer
        ObjectName throttlerName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=processors,name=\"mythrottler2\"");

        // use route to get the total time
        ObjectName routeName = ObjectName.getInstance("org.apache.camel:context=camel-1,type=routes,name=\"route2\"");

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

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final ScheduledExecutorService badService = new ScheduledThreadPoolExecutor(1) {
            @Override
            public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
                throw new RejectedExecutionException();
            }
        };

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                        .to("log:foo")
                        .throttle(10).timePeriodMillis(250).id("mythrottler")
                        .to("mock:result");

                from("seda:throttleCount")
                        .throttle(1).timePeriodMillis(250).id("mythrottler2")
                        .to("mock:end");

                from("seda:throttleCountAsync")
                        .throttle(1).asyncDelayed().timePeriodMillis(250).id("mythrottler3")
                        .to("mock:endAsync");

                from("seda:throttleCountAsyncException")
                        .throttle(1).asyncDelayed().timePeriodMillis(250).id("mythrottler4")
                        .to("mock:endAsyncException")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                throw new RuntimeException("Fail me");
                            }
                        });
                from("seda:throttleCountRejectExecutionCallerRuns")
                        .onException(RejectedExecutionException.class).to("mock:rejectedExceptionEndpoint1").end()
                        .throttle(1)
                            .timePeriodMillis(250)
                            .asyncDelayed()
                            .executorService(badService)
                            .callerRunsWhenRejected(true)
                            .id("mythrottler5")
                        .to("mock:endAsyncRejectCallerRuns");

                from("seda:throttleCountRejectExecution")
                        .onException(RejectedExecutionException.class).to("mock:rejectedExceptionEndpoint1").end()
                        .throttle(1)
                            .timePeriodMillis(250)
                            .asyncDelayed()
                            .executorService(badService)
                            .callerRunsWhenRejected(false)
                            .id("mythrottler6")
                        .to("mock:endAsyncReject");
            }
        };
    }

}
