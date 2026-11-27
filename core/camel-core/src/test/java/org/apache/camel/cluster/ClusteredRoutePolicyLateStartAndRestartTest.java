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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.apache.camel.impl.engine.DefaultExecutorServiceManager;
import org.apache.camel.impl.engine.DefaultRouteController;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.cluster.AbstractCamelClusterService;
import org.apache.camel.support.cluster.AbstractCamelClusterView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route added to a running CamelContext while this node loses the leadership must not keep running, and a
 * ClusteredRoutePolicy must work again after the CamelContext was stopped and started.
 */
class ClusteredRoutePolicyLateStartAndRestartTest {

    private CamelContext context;

    @AfterEach
    void stopContext() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void routeAddedWhileLeadershipIsLostIsStopped() throws Exception {
        TestClusterService cs = new TestClusterService("node-1", true);
        // the policy thread has looked at route foo (not started yet) while it applied the loss of the leadership
        CountDownLatch fooChecked = new CountDownLatch(1);
        context = new DefaultCamelContext();
        context.setRouteController(new PolicyThreadHook(context, "foo", null, fooChecked));
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setRetryDelay(Duration.ofMillis(100));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // keeps the policy running, and is never started
                from("seda:bar").routeId("bar").autoStartup(false).routePolicy(policy).to("mock:bar");
            }
        });
        context.start();

        // runs right after the onInit of the clustered policy, which found this node to be the leader
        RoutePolicySupport loseLeadership = new RoutePolicySupport() {
            @Override
            public void onInit(Route route) {
                super.onInit(route);
                cs.getView().setLeader(false);
                try {
                    assertTrue(fooChecked.await(10, TimeUnit.SECONDS), "The policy did not apply the loss");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        };
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy, loseLeadership).to("mock:foo");
            }
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo")));
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("bar"));
    }

    @Test
    void routeStartingWhileLeadershipIsLostIsStoppedOnceStarted() throws Exception {
        TestClusterService cs = new TestClusterService("node-1", true);
        // the policy thread has seen route foo starting while it applied the loss of the leadership
        CountDownLatch fooSeenStarting = new CountDownLatch(1);
        context = new DefaultCamelContext();
        context.setRouteController(new PolicyThreadHook(context, "foo", ServiceStatus.Starting, fooSeenStarting));
        context.addService(cs);
        context.start();
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setRetryDelay(Duration.ofMillis(100));

        // its onStart runs after the one of the clustered policy, while the route is starting
        RoutePolicySupport loseLeadership = new RoutePolicySupport() {
            @Override
            public void onStart(Route route) {
                super.onStart(route);
                cs.getView().setLeader(false);
                try {
                    assertTrue(fooSeenStarting.await(10, TimeUnit.SECONDS), "The policy did not apply the loss");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
        };
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy, loseLeadership).to("mock:foo");
            }
        });

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo")));
    }

    @Test
    void leaderStartsRoutesAfterCamelContextRestart() throws Exception {
        TestClusterService cs = new TestClusterService("node-1", true);
        context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
                from("seda:baz").routeId("baz").autoStartup(false).routePolicy(policy).to("mock:baz");
            }
        });
        context.start();
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));

        context.stop();
        context.start();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        // a route that is not auto startup stays stopped
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("baz"));

        // and the leadership changes are applied again
        cs.getView().setLeader(false);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo")));
    }

    @Test
    void leadershipTakenAfterCamelContextRestartStartsRoute() throws Exception {
        TestClusterService cs = new TestClusterService("node-1", false);
        context = new DefaultCamelContext();
        context.addService(cs);
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();
        context.stop();
        context.start();
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("foo"));

        cs.getView().setLeader(true);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
    }

    @Test
    void routeStartThrowingErrorIsTriedAgain() throws Exception {
        TestClusterService cs = new TestClusterService("node-1", false);
        context = new DefaultCamelContext();
        context.addService(cs);
        AtomicInteger errors = new AtomicInteger(1);
        context.addComponent("erroring", new ErrorOnStartComponent(errors, true));
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setRetryDelay(Duration.ofMillis(100));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("erroring:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();

        cs.getView().setLeader(true);
        // on main the Error escapes the policy thread: look at the route, not at uncaught exceptions
        await().dontCatchUncaughtExceptions().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        assertEquals(0, errors.get());
    }

    @Test
    void retryTimerThatCannotBeCreatedIsCreatedOnTheNextFailure() throws Exception {
        TestClusterService cs = new TestClusterService("node-1", false);
        context = new DefaultCamelContext();
        // the first retry timer cannot be created, as when the JVM cannot create a native thread. Not with an
        // OutOfMemoryError: it would escape the policy thread and fail the tests that run at the same time
        CountDownLatch timerFailed = new CountDownLatch(1);
        context.setExecutorServiceManager(new DefaultExecutorServiceManager(context) {
            @Override
            public ScheduledExecutorService newSingleThreadScheduledExecutor(Object source, String name) {
                if (name.endsWith("Retry") && timerFailed.getCount() > 0) {
                    timerFailed.countDown();
                    throw new IllegalStateException("Simulated: unable to create the retry timer");
                }
                return super.newSingleThreadScheduledExecutor(source, name);
            }
        });
        context.addService(cs);
        AtomicInteger failures = new AtomicInteger(2);
        context.addComponent("erroring", new ErrorOnStartComponent(failures, false));
        ClusteredRoutePolicy policy = ClusteredRoutePolicy.forNamespace("my-ns");
        policy.setRetryDelay(Duration.ofMillis(100));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("erroring:foo").routeId("foo").routePolicy(policy).to("mock:foo");
            }
        });
        context.start();

        // the first start fails, and its retry cannot be scheduled
        cs.getView().setLeader(true);
        assertTrue(timerFailed.await(10, TimeUnit.SECONDS), "The policy did not try to schedule a retry");
        // the leadership is lost and taken again: the next start fails too, and its retry is scheduled (before, the
        // retry flag stayed set after the timer could not be created, so no retry ran and the route stayed stopped)
        cs.getView().setLeader(false);
        await().atMost(10, TimeUnit.SECONDS).until(() -> !policy.isLeader());
        cs.getView().setLeader(true);
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("foo")));
        assertEquals(-1, failures.get());
    }

    // *********************************
    // Helpers
    // *********************************

    /**
     * Counts down the latch when the policy thread reads the status of the route (and the status is the expected one,
     * if any).
     */
    private static final class PolicyThreadHook extends DefaultRouteController {
        private final String routeId;
        private final ServiceStatus expected;
        private final CountDownLatch latch;

        PolicyThreadHook(CamelContext context, String routeId, ServiceStatus expected, CountDownLatch latch) {
            super(context);
            this.routeId = routeId;
            this.expected = expected;
            this.latch = latch;
        }

        @Override
        public ServiceStatus getRouteStatus(String id) {
            ServiceStatus status = super.getRouteStatus(id);
            if (routeId.equals(id) && Thread.currentThread().getName().contains("ClusteredRoutePolicy")
                    && (expected == null || expected == status)) {
                latch.countDown();
            }
            return status;
        }
    }

    private static final class ErrorOnStartComponent extends DefaultComponent {
        private final AtomicInteger errors;
        private final boolean linkageError;

        ErrorOnStartComponent(AtomicInteger errors, boolean linkageError) {
            this.errors = errors;
            this.linkageError = linkageError;
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
                            if (errors.getAndDecrement() > 0) {
                                if (linkageError) {
                                    throw new NoClassDefFoundError("Simulated linkage error on start");
                                }
                                throw new IllegalStateException("Simulated failure on start");
                            }
                            super.doStart();
                        }
                    };
                }
            };
        }
    }

    private static final class TestClusterView extends AbstractCamelClusterView {
        private final AtomicBoolean leader;

        TestClusterView(CamelClusterService cluster, String namespace, AtomicBoolean leader) {
            super(cluster, namespace);
            this.leader = leader;
        }

        @Override
        public Optional<CamelClusterMember> getLeader() {
            return leader.get() ? Optional.of(getLocalMember()) : Optional.empty();
        }

        @Override
        public CamelClusterMember getLocalMember() {
            return new CamelClusterMember() {
                @Override
                public boolean isLeader() {
                    return leader.get();
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
        }

        @Override
        protected void doStop() {
        }

        void setLeader(boolean value) {
            leader.set(value);
            if (isRunAllowed()) {
                fireLeadershipChangedEvent(getLeader().orElse(null));
            }
        }
    }

    private static final class TestClusterService extends AbstractCamelClusterService<TestClusterView> {
        private final AtomicBoolean leader;
        private volatile TestClusterView view;

        TestClusterService(String id, boolean leader) {
            super(id);
            this.leader = new AtomicBoolean(leader);
        }

        @Override
        protected TestClusterView createView(String namespace) {
            if (view == null) {
                view = new TestClusterView(this, namespace, leader);
            }
            return view;
        }

        TestClusterView getView() {
            return view;
        }
    }
}
