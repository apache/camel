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
package org.apache.camel.cluster;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRouteController;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.apache.camel.impl.engine.DefaultRouteController;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.apache.camel.support.cluster.ClusterServiceSelectors;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClusteredEdgeCasesTest {

    @Test
    public void testInitialDelayWhenLeadershipTakenAfterStart() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setInitialDelay(Duration.ofSeconds(3));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);

            // the route must not start before the initial delay has elapsed
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo")));
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testInitialDelayForRouteAddedAfterStart() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRouteController controller = new ClusteredRouteController();
        controller.setNamespace("my-ns");
        controller.setInitialDelay(Duration.ofMillis(300));
        context.setRouteController(controller);
        context.start();
        try {
            long added = System.nanoTime();
            // the controller creates the policy of the route, and sets the initial delay after the CamelContext
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("seda:foo").routeId("foo").to("mock:foo");
                }
            });
            assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));

            cs.getView().setLeader(true);

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
            // and the initial delay still applies to the route added after start
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - added);
            assertTrue(elapsed >= 300, "The route started after " + elapsed + " ms, before the initial delay of 300 ms");
        } finally {
            context.stop();
        }
    }

    @Test
    public void testInitialDelayForRouteAddedAfterStartOnLeader() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        context.start();
        // creates and starts the view of the namespace, so this node is already the leader when the route is added
        CamelClusterView view = cs.getView("my-ns");
        try {
            cs.getView().setLeader(true);

            ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
            policy.setInitialDelay(Duration.ofMillis(200));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
                }
            });
            // the route controller starts the route as usual
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));

            // the node loses the leadership, and takes it again later
            cs.getView().setLeader(false);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo")));
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            cs.releaseView(view);
            context.stop();
        }
    }

    @Test
    public void testRemovedRouteIdReusedByOtherRoute() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
                from("seda:bar").routeId("bar").routePolicy(policy).to("mock:bar");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));

            context.getRouteController().stopRoute("foo");
            context.removeRoute("foo");

            // a route that is not clustered with the same id
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("seda:foo2").routeId("foo").to("mock:foo2");
                }
            });
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));

            cs.getView().setLeader(false);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("bar")));
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testServiceRestartDoesNotStartReleasedView() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        try {
            assertTrue(cs.getView().isRunning());

            context.getRouteController().stopRoute("foo");
            context.removeRoute("foo");
            assertFalse(cs.getView().isRunning());

            cs.stop();
            cs.start();
            assertFalse(cs.getView().isRunning());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testClusteredRouteControllerWithSelector() throws Exception {
        TestClusterService cs1 = new TestClusterService("cs1");
        cs1.setOrder(1);
        TestClusterService cs2 = new TestClusterService("cs2");
        cs2.setOrder(2);

        CamelContext context = new DefaultCamelContext();
        context.addService(cs1);
        context.addService(cs2);

        ClusteredRouteController controller = new ClusteredRouteController();
        assertDoesNotThrow(() -> controller.setClusterServiceSelector(ClusterServiceSelectors.order()));
        controller.setNamespace("my-ns");
        context.setRouteController(controller);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").to("mock:foo");
            }
        });
        context.start();
        try {
            // the service selected by the order is used
            cs1.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testLeadershipTakenBeforeTheCamelContextStarted() throws Exception {
        // a cluster service started on its own (not by the CamelContext), whose view is already the leader
        TestClusterService cs = new TestClusterService("cs");
        cs.start();
        // creates the view
        CamelClusterView view = cs.getView("my-ns");
        CamelContext context = new DefaultCamelContext();
        try {
            cs.getView().setLeader(true);
            ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace(cs, "my-ns");
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
                }
            });
            context.start();

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
            cs.releaseView(view);
            cs.stop();
        }
    }

    @Test
    public void testLeadershipTakenWhileARouteIsAdded() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        // tells when the policy thread has tried to start the route
        CountDownLatch startTried = new CountDownLatch(1);
        context.setRouteController(new DefaultRouteController(context) {
            @Override
            public void startRoute(String routeId) throws Exception {
                super.startRoute(routeId);
                startTried.countDown();
            }
        });
        context.start();
        try {
            ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
            // listed after the clustered policy: this node becomes the leader while the route is created, before the
            // route is added to the CamelContext
            RoutePolicySupport leaderWhileAdding = new RoutePolicySupport() {
                @Override
                public void onInit(Route route) {
                    cs.getView().setLeader(true);
                    try {
                        // the policy thread tries to start the route now (the old code did); the fixed policy does
                        // not, as the route is not added yet, so this wait ends after its timeout of one second
                        startTried.await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("seda:foo").routeId("foo").routePolicy(policy, leaderWhileAdding).to("mock:foo");
                }
            });

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRouteRemovedWhileThePolicyStartsIt() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        CountDownLatch starting = new CountDownLatch(1);
        CountDownLatch removed = new CountDownLatch(1);
        // the start of the route by the policy thread waits until the route was removed
        context.setRouteController(new DefaultRouteController(context) {
            @Override
            public void startRoute(String routeId) throws Exception {
                if ("foo".equals(routeId) && starting.getCount() > 0) {
                    starting.countDown();
                    // on the policy thread: the test checks the outcome (bounded wait, the test fails anyway)
                    removed.await(20, TimeUnit.SECONDS);
                }
                super.startRoute(routeId);
            }
        });
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
                from("seda:bar").routeId("bar").routePolicy(policy).to("mock:bar");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);
            assertTrue(starting.await(10, TimeUnit.SECONDS), "The policy did not start the route");
            context.removeRoute("foo");
            removed.countDown();
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("bar")));

            // a route that is not clustered with the same id
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("seda:foo2").routeId("foo").to("mock:foo2");
                }
            });
            assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo"));

            // the policy must not stop it when this node loses the leadership
            cs.getView().setLeader(false);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("bar")));
            await().during(1, TimeUnit.SECONDS).atMost(2, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRouteThatFailsToStartIsStartedAgain() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        // the first start of the consumer of route a fails (a broker that is not reachable yet)
        AtomicInteger failures = new AtomicInteger(1);
        context.addComponent("failing", new FailingStartComponent(failures));
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setRetryDelay(Duration.ofMillis(100));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("failing:a").routeId("a").routePolicy(policy).to("mock:a");
                from("seda:b").routeId("b").routePolicy(policy).to("mock:b");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);

            // both routes run on the leader: the other one right away, the failed one once its start is tried again
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("b"));
                assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("a"));
            });
            assertEquals(-1, failures.get(), "The start of route a did not fail once");
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRetryDoesNotStartARouteOnAFollower() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        // the consumer of route a cannot start for now
        AtomicInteger failures = new AtomicInteger(Integer.MAX_VALUE);
        FailingStartComponent component = new FailingStartComponent(failures);
        context.addComponent("failing", component);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setRetryDelay(Duration.ofMillis(100));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("failing:a").routeId("a").routePolicy(policy).to("mock:a");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);
            // the leader tries again
            await().atMost(10, TimeUnit.SECONDS).until(() -> component.attempts.get() >= 2);

            // the node loses the leadership, and the consumer could start now: the retry must not start the route
            cs.getView().setLeader(false);
            await().atMost(10, TimeUnit.SECONDS).until(() -> !policy.isLeader());
            failures.set(0);
            await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("a")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testLeadershipTakenWhileTheCamelContextIsSuspended() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        try {
            context.suspend();
            // the node becomes the leader while the CamelContext is suspended (from JMX, for example)
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).until(policy::isLeader);

            context.resume();
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testLeadershipTakenWhileTheCamelContextIsSuspendedAndASlowResume() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setRetryDelay(Duration.ofMillis(100));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        // an event notifier after the one of the policy holds the resume, so the CamelContext is still starting when
        // the policy thread applies the leadership after the resumed event
        CountDownLatch neverReleased = new CountDownLatch(1);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                neverReleased.await(500, TimeUnit.MILLISECONDS);
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelEvent.CamelContextResumedEvent;
            }
        });
        try {
            context.suspend();
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).until(policy::isLeader);

            context.resume();
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRouteAddedAfterStartAndASlowResume() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setRetryDelay(Duration.ofMillis(100));
        context.start();
        // the policy is attached after the CamelContext started, so it never gets the started event
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        CountDownLatch neverReleased = new CountDownLatch(1);
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) throws Exception {
                neverReleased.await(500, TimeUnit.MILLISECONDS);
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelEvent.CamelContextResumedEvent;
            }
        });
        try {
            context.suspend();
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).until(policy::isLeader);

            context.resume();
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testLeadershipLostWhileTheCamelContextIsSuspendedWithASuspendableConsumer() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));

            context.suspend();
            cs.getView().setLeader(false);
            await().atMost(10, TimeUnit.SECONDS).until(() -> !policy.isLeader());

            context.resume();
            await().during(1, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testLeadershipLostWhileTheCamelContextIsSuspended() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        // a consumer that does not support suspension (the failing component with no failure): the suspend of the
        // CamelContext stops its route, and the resume starts it again
        context.addComponent("failing", new FailingStartComponent(new AtomicInteger()));
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("failing:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));

            context.suspend();
            // the node loses the leadership while the CamelContext is suspended
            cs.getView().setLeader(false);
            await().atMost(10, TimeUnit.SECONDS).until(() -> !policy.isLeader());

            // the resume starts the routes it stopped: this node must not keep running the route
            context.resume();
            await().during(1, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(
                    () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo")));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testRouteThatFailsToStopIsStartedAgainOnTheNextLeadership() throws Exception {
        TestClusterService cs = new TestClusterService("cs");
        CamelContext context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        // the first stop of route a fails (in a route policy, after the route stopped)
        AtomicInteger stopFailures = new AtomicInteger(1);
        RoutePolicySupport failingStop = new RoutePolicySupport() {
            @Override
            public void onStop(Route route) {
                if ("a".equals(route.getId()) && stopFailures.getAndDecrement() > 0) {
                    throw new IllegalStateException("Cannot stop route a");
                }
            }
        };
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:a").routeId("a").routePolicy(policy, failingStop).to("mock:a");
                from("seda:b").routeId("b").routePolicy(policy, failingStop).to("mock:b");
            }
        });
        context.start();
        try {
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("a"));
                assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("b"));
            });

            // the other route is stopped too
            cs.getView().setLeader(false);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("b"));
                assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("a"));
            });
            assertEquals(0, stopFailures.get(), "The stop of route a did not fail once");

            // and both are started on the next leadership
            cs.getView().setLeader(true);
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("a"));
                assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("b"));
            });
        } finally {
            context.stop();
        }
    }

    // *********************************
    // Helpers
    // *********************************

    // a consumer whose start fails as many times as failures says
    private static final class FailingStartComponent extends DefaultComponent {
        private final AtomicInteger failures;
        private final AtomicInteger attempts = new AtomicInteger();

        FailingStartComponent(AtomicInteger failures) {
            this.failures = failures;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    throw new UnsupportedOperationException("No producer");
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    return new DefaultConsumer(this, processor) {
                        @Override
                        protected void doStart() throws Exception {
                            attempts.incrementAndGet();
                            if (failures.getAndDecrement() > 0) {
                                throw new IllegalStateException("Cannot start the consumer yet");
                            }
                            super.doStart();
                        }
                    };
                }
            };
        }
    }

    private static class TestClusterView extends AbstractCamelClusterView {
        private volatile boolean leader;
        private volatile boolean running;

        TestClusterView(CamelClusterService cluster, String namespace) {
            super(cluster, namespace);
        }

        @Override
        public Optional<CamelClusterMember> getLeader() {
            return leader ? Optional.of(getLocalMember()) : Optional.empty();
        }

        @Override
        public CamelClusterMember getLocalMember() {
            return new CamelClusterMember() {
                @Override
                public boolean isLeader() {
                    return leader;
                }

                @Override
                public boolean isLocal() {
                    return true;
                }

                @Override
                public String getId() {
                    return getClusterService().getId();
                }
            };
        }

        @Override
        public List<CamelClusterMember> getMembers() {
            return Collections.emptyList();
        }

        @Override
        protected void doStart() {
            running = true;
        }

        @Override
        protected void doStop() {
            running = false;
        }

        void setLeader(boolean leader) {
            this.leader = leader;
            if (isRunAllowed()) {
                fireLeadershipChangedEvent(getLeader().orElse(null));
            }
        }

        boolean isRunning() {
            return running;
        }
    }

    private static class TestClusterService extends AbstractCamelClusterService<TestClusterView> {
        private TestClusterView view;

        TestClusterService(String id) {
            super(id);
        }

        @Override
        protected TestClusterView createView(String namespace) {
            if (view == null) {
                view = new TestClusterView(this, namespace);
            }
            return view;
        }

        TestClusterView getView() {
            return view;
        }
    }
}
