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

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Consumer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.direct.DirectComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.quartz.QuartzComponent;
import org.apache.camel.support.service.ServiceHelper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimpleScheduledRoutePolicyTest {

    @Nested
    class SimpleTest1 extends NoBuilderTest {
        @Test
        public void testScheduledStartRoutePolicy() throws Exception {
            MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
            success.expectedMessageCount(1);

            context.getComponent("direct", DirectComponent.class).setBlock(false);
            context.getComponent("quartz", QuartzComponent.class)
                    .setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                    long startTime = System.currentTimeMillis() + 500;
                    policy.setRouteStartDate(new Date(startTime));
                    policy.setRouteStartRepeatCount(1);
                    policy.setRouteStartRepeatInterval(1000);

                    from("direct:start")
                            .routeId("test")
                            .routePolicy(policy)
                            .to("mock:success");
                }
            });
            context.start();
            context.getRouteController().stopRoute("test");

            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertSame(ServiceStatus.Started, context.getRouteController().getRouteStatus("test"));
            });

            template.sendBody("direct:start", "Ready or not, Here, I come");

            context.getComponent("quartz", QuartzComponent.class).stop();
            success.assertIsSatisfied();
        }
    }

    @Nested
    class SimpleTest2 extends NoBuilderTest {
        @Test
        public void testScheduledStopRoutePolicy() throws Exception {
            context.getComponent("direct", DirectComponent.class).setBlock(false);
            context.getComponent("quartz", QuartzComponent.class)
                    .setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                    long startTime = System.currentTimeMillis() + 500;
                    policy.setRouteStopDate(new Date(startTime));
                    policy.setRouteStopRepeatCount(1);
                    policy.setRouteStopRepeatInterval(1000);

                    from("direct:start")
                            .routeId("test")
                            .routePolicy(policy)
                            .to("mock:unreachable");
                }
            });
            context.start();

            // wait for route to stop
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(ServiceHelper.isStopped(context.getRoute("test").getConsumer()));
            });

            assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "Ready or not, Here, I come"));

            context.getComponent("quartz", QuartzComponent.class).stop();
        }
    }

    @Nested
    class SimpleTest3 extends NoBuilderTest {
        @Test
        public void testScheduledSuspendRoutePolicy() throws Exception {
            context.getComponent("direct", DirectComponent.class).setBlock(false);
            context.getComponent("quartz", QuartzComponent.class)
                    .setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                    long startTime = System.currentTimeMillis() + 500;
                    policy.setRouteSuspendDate(new Date(startTime));
                    policy.setRouteSuspendRepeatCount(1);
                    policy.setRouteSuspendRepeatInterval(1000);

                    from("direct:start")
                            .routeId("test")
                            .routePolicy(policy)
                            .to("mock:unreachable");
                }
            });
            context.start();

            // wait for route to suspend
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(ServiceHelper.isSuspended(context.getRoute("test").getConsumer()));
            });

            assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "Ready or not, Here, I come"));

            context.getComponent("quartz", QuartzComponent.class).stop();
        }
    }

    @Nested
    class SimpleTest4 extends NoBuilderTest {
        @Test
        public void testScheduledResumeRoutePolicy() throws Exception {
            MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
            success.expectedMessageCount(1);

            context.getComponent("direct", DirectComponent.class).setBlock(false);
            context.getComponent("quartz", QuartzComponent.class)
                    .setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                    long startTime = System.currentTimeMillis() + 1000;
                    policy.setRouteResumeDate(new Date(startTime));
                    policy.setRouteResumeRepeatCount(1);
                    policy.setRouteResumeRepeatInterval(1000);

                    from("direct:start")
                            .routeId("test")
                            .routePolicy(policy)
                            .to("mock:success");
                }
            });
            context.start();

            ServiceHelper.suspendService(context.getRoute("test").getConsumer());

            assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "Ready or not, Here, I come"),
                    "Should have thrown an exception");

            // wait for route to resume/start
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(ServiceHelper.isStarted(context.getRoute("test").getConsumer()));
            });

            template.sendBody("direct:start", "Ready or not, Here, I come");

            context.getComponent("quartz", QuartzComponent.class).stop();
            success.assertIsSatisfied();
        }
    }

    @Nested
    class SimpleTest5 extends NoBuilderTest {

        @Test
        public void testScheduledSuspendAndResumeRoutePolicy() throws Exception {
            MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
            success.expectedMessageCount(1);

            context.getComponent("direct", DirectComponent.class).setBlock(false);
            context.getComponent("quartz", QuartzComponent.class)
                    .setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                    long suspendTime = System.currentTimeMillis() + 500;
                    policy.setRouteSuspendDate(new Date(suspendTime));
                    policy.setRouteSuspendRepeatCount(0);
                    policy.setRouteSuspendRepeatInterval(1000);
                    long resumeTime = System.currentTimeMillis() + 2000;
                    policy.setRouteResumeDate(new Date(resumeTime));
                    policy.setRouteResumeRepeatCount(1);
                    policy.setRouteResumeRepeatInterval(1000);

                    from("direct:start")
                            .routeId("test")
                            .routePolicy(policy)
                            .to("mock:success");
                }
            });
            context.start();

            // wait for route to suspend
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(ServiceHelper.isSuspended(context.getRoute("test").getConsumer()));
            });

            assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "Ready or not, Here, I come"),
                    "Should have thrown an exception");

            // wait for route to resume/start
            Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertTrue(ServiceHelper.isStarted(context.getRoute("test").getConsumer()));
            });

            template.sendBody("direct:start", "Ready or not, Here, I come");

            context.getComponent("quartz", QuartzComponent.class).stop();
            success.assertIsSatisfied();
        }
    }

    @Nested
    class SimpleTest6 extends NoBuilderTest {
        @Test
        public void testScheduledSuspendAndRestartPolicy() throws Exception {
            MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
            success.expectedMessageCount(1);

            context.getComponent("direct", DirectComponent.class).setBlock(false);
            context.getComponent("quartz", QuartzComponent.class)
                    .setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                    long suspendTime = System.currentTimeMillis() + 500;
                    policy.setRouteSuspendDate(new Date(suspendTime));
                    policy.setRouteSuspendRepeatCount(0);
                    long startTime = System.currentTimeMillis() + 2000;
                    policy.setRouteStartDate(new Date(startTime));
                    policy.setRouteResumeRepeatCount(1);
                    policy.setRouteResumeRepeatInterval(1000);

                    from("direct:start")
                            .routeId("test")
                            .routePolicy(policy)
                            .to("mock:success");
                }
            });
            context.start();

            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(
                    () -> {
                        Consumer consumer = context.getRoute("test").getConsumer();
                        return ServiceHelper.isSuspended(consumer);
                    });

            assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:start", "Ready or not, Here, I come"),
                    "Should have thrown an exception");

            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(
                    () -> {
                        Consumer consumer = context.getRoute("test").getConsumer();
                        return ServiceHelper.isStarted(consumer);
                    });

            template.sendBody("direct:start", "Ready or not, Here, I come");

            context.getComponent("quartz", QuartzComponent.class).stop();
            success.assertIsSatisfied();
        }

        @Nested
        class SimpleTest7 extends NoBuilderTest {
            @Test
            public void testNoAutoStartup() throws Exception {
                MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
                success.expectedMessageCount(1);

                context.getComponent("direct", DirectComponent.class).setBlock(false);
                context.getComponent("quartz", QuartzComponent.class)
                        .setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");
                context.addRoutes(new RouteBuilder() {
                    public void configure() {
                        SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                        long startTime = System.currentTimeMillis() + 500;
                        policy.setRouteStartDate(new Date(startTime));
                        policy.setRouteStartRepeatCount(1);
                        policy.setRouteStartRepeatInterval(1000);

                        from("direct:start")
                                .routeId("test")
                                .noAutoStartup()
                                .routePolicy(policy)
                                .to("mock:success");
                    }
                });
                context.start();

                // wait for route to start
                Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                    assertTrue(ServiceHelper.isStarted(context.getRoute("test").getConsumer()));
                });

                template.sendBody("direct:start", "Ready or not, Here, I come");

                context.getComponent("quartz", QuartzComponent.class).stop();
                success.assertIsSatisfied();
            }
        }

        @Nested
        class SimpleTest8 extends NoBuilderTest {
            @Test
            public void testNoAutoStartupDynamic() throws Exception {
                MockEndpoint success = context.getEndpoint("mock:success", MockEndpoint.class);
                success.expectedMessageCount(1);

                context.getComponent("direct", DirectComponent.class).setBlock(false);
                context.getComponent("quartz", QuartzComponent.class)
                        .setPropertiesFile("org/apache/camel/routepolicy/quartz/myquartz.properties");

                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("direct:start")
                                .process(e -> context.addRoutes(new RouteBuilder() {
                                    @Override
                                    public void configure() throws Exception {
                                        SimpleScheduledRoutePolicy policy = new SimpleScheduledRoutePolicy();
                                        long startTime = System.currentTimeMillis() + 500;
                                        policy.setRouteStartDate(new Date(startTime));

                                        from("direct:dynamic")
                                                .routeId("dynamic")
                                                .noAutoStartup()
                                                .routePolicy(policy)
                                                .to("mock:success");
                                    }
                                }));
                    }
                });

                context.start();

                template.sendBody("direct:start", "Hello World");

                // wait for route to start
                Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                    assertTrue(ServiceHelper.isStarted(context.getRoute("dynamic").getConsumer()));
                });

                template.sendBody("direct:dynamic", "Ready or not, Here, I come");

                context.getComponent("quartz", QuartzComponent.class).stop();
                success.assertIsSatisfied();
            }
        }
    }
}
