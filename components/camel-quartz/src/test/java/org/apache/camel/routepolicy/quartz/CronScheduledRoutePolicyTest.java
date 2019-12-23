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
package org.apache.camel.routepolicy.quartz;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.SuspendableService;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class CronScheduledRoutePolicyTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testScheduledStartRoutePolicyWithTwoRoutes() throws Exception {
        MockEndpoint success1 = context.getEndpoint("mock:success1", MockEndpoint.class);
        MockEndpoint success2 = context.getEndpoint("mock:success2", MockEndpoint.class);
        success1.expectedMessageCount(1);
        success2.expectedMessageCount(1);

        context.getComponent("direct", DirectComponent.class).setBlock(false);
        context.getComponent("quartz", QuartzComponent.class).setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                policy.setRouteStartTime("*/3 * * * * ?");

                from("direct:start1")
                    .routeId("test1")
                    .routePolicy(policy)
                    .to("mock:success1");

                from("direct:start2")
                    .routeId("test2")
                    .routePolicy(policy)
                    .to("mock:success2");
            }
        });
        context.start();
        context.getRouteController().stopRoute("test1", 1000, TimeUnit.MILLISECONDS);
        context.getRouteController().stopRoute("test2", 1000, TimeUnit.MILLISECONDS);

        Thread.sleep(5000);
        assertTrue(context.getRouteController().getRouteStatus("test1") == ServiceStatus.Started);
        assertTrue(context.getRouteController().getRouteStatus("test2") == ServiceStatus.Started);
        template.sendBody("direct:start1", "Ready or not, Here, I come");
        template.sendBody("direct:start2", "Ready or not, Here, I come");

        success1.assertIsSatisfied();
        success2.assertIsSatisfied();
    }

    @Test
    public void testScheduledStopRoutePolicyWithTwoRoutes() throws Exception {
        context.getComponent("direct", DirectComponent.class).setBlock(false);
        context.getComponent("quartz", QuartzComponent.class).setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                policy.setRouteStopTime("*/3 * * * * ?");
                policy.setRouteStopGracePeriod(0);
                policy.setTimeUnit(TimeUnit.MILLISECONDS);

                from("direct:start1")
                    .routeId("test1")
                    .routePolicy(policy)
                    .to("mock:unreachable");

                from("direct:start2")
                    .routeId("test2")
                    .routePolicy(policy)
                    .to("mock:unreachable");
            }
        });
        context.start();

        Thread.sleep(5000);

        assertTrue(context.getRouteController().getRouteStatus("test1") == ServiceStatus.Stopped);
        assertTrue(context.getRouteController().getRouteStatus("test2") == ServiceStatus.Stopped);
    }

    @Test
    public void testScheduledStartRoutePolicy() throws Exception {
        MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
        success.expectedMessageCount(1);

        context.getComponent("direct", DirectComponent.class).setBlock(false);
        context.getComponent("quartz", QuartzComponent.class).setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                policy.setRouteStartTime("*/3 * * * * ?");

                from("direct:start")
                    .routeId("test")
                    .routePolicy(policy)
                    .to("mock:success");
            }
        });
        context.start();
        context.getRouteController().stopRoute("test", 1000, TimeUnit.MILLISECONDS);

        Thread.sleep(5000);
        assertTrue(context.getRouteController().getRouteStatus("test") == ServiceStatus.Started);
        template.sendBody("direct:start", "Ready or not, Here, I come");

        context.getComponent("quartz", QuartzComponent.class).stop();
        success.assertIsSatisfied();
    }

    @Test
    public void testScheduledStopRoutePolicy() throws Exception {
        context.getComponent("direct", DirectComponent.class).setBlock(false);
        context.getComponent("quartz", QuartzComponent.class).setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                policy.setRouteStopTime("*/3 * * * * ?");
                policy.setRouteStopGracePeriod(0);
                policy.setTimeUnit(TimeUnit.MILLISECONDS);

                from("direct:start")
                    .routeId("test")
                    .routePolicy(policy)
                    .to("mock:unreachable");
            }
        });
        context.start();

        Thread.sleep(5000);
        assertTrue(context.getRouteController().getRouteStatus("test") == ServiceStatus.Stopped);
    }

    @Test
    public void testScheduledStartAndStopRoutePolicy() throws Exception {
        MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
        success.expectedMessageCount(1);

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final CountDownLatch stoppedLatch = new CountDownLatch(1);

        context.getComponent("direct", DirectComponent.class).setBlock(false);
        context.getComponent("quartz", QuartzComponent.class).setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy() {

                    @Override
                    public void onStart(final Route route) {
                        super.onStart(route);

                        startedLatch.countDown();
                    }

                    @Override
                    public void onStop(final Route route) {
                        super.onStop(route);

                        stoppedLatch.countDown();
                    }
                };
                policy.setRouteStartTime("*/3 * * * * ?");
                policy.setRouteStopTime("*/6 * * * * ?");
                policy.setRouteStopGracePeriod(0);

                from("direct:start")
                        .routeId("test")
                        .routePolicy(policy)
                        .noAutoStartup()
                        .to("mock:success");
            }
        });
        context.start();

        startedLatch.await(5000, TimeUnit.SECONDS);

        ServiceStatus startedStatus = context.getRouteController().getRouteStatus("test");
        assertTrue(startedStatus == ServiceStatus.Started || startedStatus == ServiceStatus.Starting);
        template.sendBody("direct:start", "Ready or not, Here, I come");

        stoppedLatch.await(5000, TimeUnit.SECONDS);

        ServiceStatus stoppedStatus = context.getRouteController().getRouteStatus("test");
        assertTrue(stoppedStatus == ServiceStatus.Stopped || stoppedStatus == ServiceStatus.Stopping);

        success.assertIsSatisfied();
    }

    @Test
    public void testScheduledStopRoutePolicyWithExtraPolicy() throws Exception {
        final MyRoutePolicy myPolicy = new MyRoutePolicy();

        context.getComponent("direct", DirectComponent.class).setBlock(false);
        context.getComponent("quartz", QuartzComponent.class).setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                policy.setRouteStopTime("*/3 * * * * ?");
                policy.setRouteStopGracePeriod(0);
                policy.setTimeUnit(TimeUnit.MILLISECONDS);

                from("direct:start")
                    .routeId("test")
                    .routePolicy(policy, myPolicy)
                    .to("mock:unreachable");
            }
        });
        context.start();

        Thread.sleep(5000);

        assertTrue(context.getRouteController().getRouteStatus("test") == ServiceStatus.Stopped);
        assertTrue("Should have called onStart", myPolicy.isStart());
        assertTrue("Should have called onStop", myPolicy.isStop());
    }

    @Test
    public void testScheduledSuspendRoutePolicy() throws Exception {
        context.getComponent("direct", DirectComponent.class).setBlock(false);
        context.getComponent("quartz", QuartzComponent.class).setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                policy.setRouteSuspendTime("*/3 * * * * ?");

                from("direct:start")
                    .routeId("test")
                    .routePolicy(policy)
                    .to("mock:unreachable");
            }
        });
        context.start();

        Thread.sleep(5000);

        // when suspending its only the consumer that suspends
        // there is a ticket to improve this
        Consumer consumer = context.getRoute("test").getConsumer();
        SuspendableService ss = (SuspendableService) consumer;
        assertTrue("Consumer should be suspended", ss.isSuspended());
    }

    @Test
    public void testScheduledResumeRoutePolicy() throws Exception {
        MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
        success.expectedMessageCount(1);

        context.getComponent("direct", DirectComponent.class).setBlock(false);
        context.getComponent("quartz", QuartzComponent.class).setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                CronScheduledRoutePolicy policy = new CronScheduledRoutePolicy();
                policy.setRouteResumeTime("*/3 * * * * ?");

                from("direct:start")
                    .routeId("test")
                    .routePolicy(policy)
                    .to("mock:success");
            }
        });
        context.start();

        ServiceHelper.suspendService(context.getRoute("test").getConsumer());

        Thread.sleep(5000);
        assertTrue(context.getRouteController().getRouteStatus("test") == ServiceStatus.Started);

        template.sendBody("direct:start", "Ready or not, Here, I come");

        success.assertIsSatisfied();
    }

}
