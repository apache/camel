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
package org.apache.camel.component.hazelcast.policy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.RoutePolicySupport;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The leadership taken by the policy's thread against the route start, the route stop, a consumer start that fails and
 * the policy stop, with an embedded member. The other node is the test thread, which holds the cluster lock.
 */
class HazelcastRoutePolicyLeadershipTest {

    private static final String LOCK_MAP = "route-policy-locks";
    private static final String LOCK_KEY = "route-lock";

    private HazelcastInstance member;
    private CamelContext context;

    @BeforeEach
    void startMember() {
        Config config = new Config();
        config.setClusterName("route-policy-" + UUID.randomUUID());
        config.getMetricsConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getAutoDetectionConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        member = Hazelcast.newHazelcastInstance(config);
    }

    @AfterEach
    void stop() {
        if (context != null) {
            // the other node goes away first, so the stop does not wait for the tryLock of the policy's thread
            if (member != null) {
                member.getMap(LOCK_MAP).forceUnlock(LOCK_KEY);
            }
            context.stop();
        }
        if (member != null) {
            member.shutdown();
        }
    }

    @Test
    void leadershipTakenWhileTheRouteStartsIsNotLost() throws Exception {
        // another node holds the cluster lock, so the policy's own thread waits in tryLock during the test
        IMap<String, String> locks = member.getMap(LOCK_MAP);
        locks.lock(LOCK_KEY);

        TestPolicy policy = newPolicy(TimeUnit.MINUTES.toMillis(1));
        startContext(policy);

        // holds the lock that onStart on main waited for after it read the flag, and takes the leadership meanwhile.
        // The fixed onStart does not take this lock, so this checks the ordered case; the race test covers the
        // atomicity
        ReentrantLock held = (ReentrantLock) policy.lifecycleLock();
        held.lock();
        CompletableFuture<Void> routeStart;
        try {
            routeStart = CompletableFuture.runAsync(() -> {
                try {
                    context.getRouteController().startRoute("route");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            await().atMost(20, TimeUnit.SECONDS).until(() -> held.hasQueuedThreads() || routeStart.isDone());

            // meanwhile this node takes the leadership: acquireLeadership calls setLeader(true) once it holds the
            // cluster lock, which starts the consumers the policy has stopped (none yet)
            policy.setLeader(true);
        } finally {
            held.unlock();
        }
        routeStart.get(20, TimeUnit.SECONDS);

        assertTrue(policy.isLeader());
        assertTrue(ServiceHelper.isStarted(context.getRoute("route").getConsumer()),
                "The node that holds the leadership must consume");
    }

    @Test
    void leadershipTakenWhileRoutesStartNeverLeavesTheLeaderIdle() throws Exception {
        IMap<String, String> locks = member.getMap(LOCK_MAP);
        locks.lock(LOCK_KEY);

        TestPolicy policy = newPolicy(TimeUnit.MINUTES.toMillis(1));
        startContext(policy);
        // a started route: the policy does not start the consumer of a route that is stopped
        context.getRouteController().startRoute("route");
        Route route = context.getRoute("route");

        // the route start (onStart) and the leadership thread (setLeader(true)) race, no hooks
        for (int i = 0; i < 1000; i++) {
            ServiceHelper.startService(route.getConsumer());
            policy.onStop(route);
            policy.setLeader(false);

            CyclicBarrier barrier = new CyclicBarrier(2);
            long spinNanos = ThreadLocalRandom.current().nextLong(2000);
            CompletableFuture<Void> routeStart = CompletableFuture.runAsync(() -> {
                awaitBarrier(barrier);
                policy.onStart(route);
            });
            CompletableFuture<Void> leadership = CompletableFuture.runAsync(() -> {
                awaitBarrier(barrier);
                long end = System.nanoTime() + spinNanos;
                while (System.nanoTime() < end) {
                    Thread.onSpinWait();
                }
                policy.setLeader(true);
            });
            CompletableFuture.allOf(routeStart, leadership).get(20, TimeUnit.SECONDS);

            assertTrue(ServiceHelper.isStarted(route.getConsumer()),
                    "The node that holds the leadership must consume (round " + i + ")");
        }
    }

    @Test
    void stopWhileAConsumerStartsAtTakeoverDoesNotWait() throws Exception {
        // another node holds the cluster lock, so the route starts on a follower and the policy stops its consumer
        IMap<String, String> locks = member.getMap(LOCK_MAP);
        locks.lock(LOCK_KEY);

        // the consumer start at takeover blocks (a broker connect) until it is interrupted
        CountDownLatch starting = new CountDownLatch(1);
        CountDownLatch neverReleased = new CountDownLatch(1);
        TestPolicy policy = newPolicy(TimeUnit.SECONDS.toMillis(1));
        startContext(policy);
        context.getRouteController().startRoute("route");
        assertFalse(ServiceHelper.isStarted(context.getRoute("route").getConsumer()));
        policy.beforeStart = () -> {
            starting.countDown();
            try {
                neverReleased.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while starting", e);
            }
        };

        // the other node goes away: this node takes the leadership and starts the consumer
        locks.unlock(LOCK_KEY);
        assertTrue(starting.await(20, TimeUnit.SECONDS), "The consumer start did not begin");

        // the policy stops (the CamelContext shuts down) meanwhile: it must not wait for the consumer start
        CompletableFuture<Void> stop = CompletableFuture.runAsync(policy::stop);
        try {
            await().atMost(5, TimeUnit.SECONDS).until(stop::isDone);
        } finally {
            neverReleased.countDown();
        }
        stop.get(20, TimeUnit.SECONDS);
    }

    @Test
    void consumerWhoseStartFailsAtTakeoverIsStartedAgain() throws Exception {
        // another node holds the cluster lock, so the routes start on a follower and the policy stops their consumers
        IMap<String, String> locks = member.getMap(LOCK_MAP);
        locks.lock(LOCK_KEY);

        AtomicBoolean firstFails = new AtomicBoolean();
        AtomicInteger firstAttempts = new AtomicInteger();
        TestPolicy policy = newPolicy(TimeUnit.SECONDS.toMillis(1));
        policy.setRetryDelay(100);
        context = new DefaultCamelContext();
        context.addComponent("failing", new FailingStartComponent(firstFails, firstAttempts));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("failing:first").routeId("first").routePolicy(policy).to("log:first");
                from("failing:second").routeId("second").routePolicy(policy).to("log:second");
            }
        });
        context.start();
        assertFalse(ServiceHelper.isStarted(context.getRoute("first").getConsumer()));
        assertFalse(ServiceHelper.isStarted(context.getRoute("second").getConsumer()));

        // the other node goes away, and the consumer of the first route cannot start (a broker not reachable yet)
        firstFails.set(true);
        firstAttempts.set(0);
        locks.unlock(LOCK_KEY);

        // the other consumer starts, and the failed one is tried again
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertTrue(policy.isLeader(), "This node is not the leader");
                    assertTrue(ServiceHelper.isStarted(context.getRoute("second").getConsumer()),
                            "The consumer of the second route did not start");
                    assertTrue(firstAttempts.get() >= 2, "The start of the first consumer was not tried again");
                });
        assertFalse(ServiceHelper.isStarted(context.getRoute("first").getConsumer()));

        // and starts once it can
        firstFails.set(false);
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(ServiceHelper.isStarted(context.getRoute("first").getConsumer()),
                        "The leader does not consume from the first route"));
    }

    @Test
    void consumerOfARouteBeingStoppedIsNotStartedAtTakeover() throws Exception {
        IMap<String, String> locks = member.getMap(LOCK_MAP);
        locks.lock(LOCK_KEY);

        TestPolicy policy = newPolicy(TimeUnit.SECONDS.toMillis(1));
        AtomicBoolean taken = new AtomicBoolean();
        // listed before the Hazelcast policy, so its onStop runs while the route stops, before the one of the policy
        RoutePolicySupport takeoverWhileStopping = new RoutePolicySupport() {
            @Override
            public void onStop(Route route) {
                if (taken.compareAndSet(false, true)) {
                    // the other node goes away now: this node takes the leadership and starts the consumers it stopped
                    locks.unlock(LOCK_KEY);
                    await().atMost(20, TimeUnit.SECONDS).until(policy::isLeader);
                }
            }
        };
        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("route").routePolicy(takeoverWhileStopping, policy).to("log:route");
            }
        });
        context.start();
        Route route = context.getRoute("route");
        assertFalse(ServiceHelper.isStarted(route.getConsumer()));

        context.getRouteController().stopRoute("route");

        assertTrue(policy.isLeader());
        await().during(500, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(ServiceHelper.isStarted(route.getConsumer()),
                        "The consumer of the stopped route runs"));

        // and it is started with the route
        context.getRouteController().startRoute("route");
        assertTrue(ServiceHelper.isStarted(context.getRoute("route").getConsumer()));
    }

    @Test
    void consumerOfARouteBeingSuspendedIsNotStartedAtTakeover() throws Exception {
        IMap<String, String> locks = member.getMap(LOCK_MAP);
        locks.lock(LOCK_KEY);

        TestPolicy policy = newPolicy(TimeUnit.SECONDS.toMillis(1));
        AtomicBoolean taken = new AtomicBoolean();
        // listed before the Hazelcast policy, so its onSuspend runs while the route suspends, before the one of the
        // policy
        RoutePolicySupport takeoverWhileSuspending = new RoutePolicySupport() {
            @Override
            public void onSuspend(Route route) {
                if (taken.compareAndSet(false, true)) {
                    locks.unlock(LOCK_KEY);
                    await().atMost(20, TimeUnit.SECONDS).until(policy::isLeader);
                }
            }
        };
        context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start").routeId("route").routePolicy(takeoverWhileSuspending, policy).to("log:route");
            }
        });
        context.start();
        Route route = context.getRoute("route");
        assertFalse(ServiceHelper.isStarted(route.getConsumer()));

        context.getRouteController().suspendRoute("route");

        assertTrue(policy.isLeader());
        await().during(500, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertFalse(ServiceHelper.isStarted(route.getConsumer()),
                        "The consumer of the suspended route runs"));

        // and it is started when the route is resumed
        context.getRouteController().resumeRoute("route");
        assertTrue(ServiceHelper.isStarted(route.getConsumer()));
    }

    @Test
    void consumerStartedWhileItsRouteStopsIsStoppedAgain() throws Exception {
        IMap<String, String> locks = member.getMap(LOCK_MAP);
        locks.lock(LOCK_KEY);

        TestPolicy policy = newPolicy(TimeUnit.SECONDS.toMillis(1));
        startContext(policy);
        context.getRouteController().startRoute("route");
        Route route = context.getRoute("route");
        assertFalse(ServiceHelper.isStarted(route.getConsumer()));
        // the route is stopped after the policy checked it and before it starts the consumer
        policy.beforeStart = () -> {
            try {
                context.getRouteController().stopRoute("route");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };

        locks.unlock(LOCK_KEY);

        await().atMost(20, TimeUnit.SECONDS).until(policy::isLeader);
        await().during(500, TimeUnit.MILLISECONDS).atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertTrue(context.getRouteController().getRouteStatus("route").isStopped(), "The route is not stopped");
                    assertFalse(ServiceHelper.isStarted(route.getConsumer()), "The consumer of the stopped route runs");
                });
    }

    private TestPolicy newPolicy(long tryLockTimeoutMillis) {
        TestPolicy policy = new TestPolicy(member);
        policy.setLockMapName(LOCK_MAP);
        policy.setLockKey(LOCK_KEY);
        policy.setLockValue("this-node");
        policy.setTryLockTimeout(tryLockTimeoutMillis, TimeUnit.MILLISECONDS);
        return policy;
    }

    private void startContext(TestPolicy policy) throws Exception {
        if (context == null) {
            context = new DefaultCamelContext();
        }
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("route").autoStartup(false).routePolicy(policy).to("log:route");
            }
        });
        context.start();
    }

    private static void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class TestPolicy extends HazelcastRoutePolicy {
        // runs before a consumer is started by the policy (when this node takes the leadership)
        volatile Runnable beforeStart;

        TestPolicy(HazelcastInstance instance) {
            super(instance);
        }

        Lock lifecycleLock() {
            return lock;
        }

        @Override
        public void startConsumer(Consumer consumer) throws Exception {
            Runnable hook = beforeStart;
            if (hook != null) {
                beforeStart = null;
                hook.run();
            }
            super.startConsumer(consumer);
        }
    }

    // the consumer of the endpoint "first" fails to start while firstFails is set
    private static final class FailingStartComponent extends DefaultComponent {
        private final AtomicBoolean firstFails;
        private final AtomicInteger firstAttempts;

        FailingStartComponent(AtomicBoolean firstFails, AtomicInteger firstAttempts) {
            this.firstFails = firstFails;
            this.firstAttempts = firstAttempts;
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            boolean first = "first".equals(remaining);
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
                            if (first) {
                                firstAttempts.incrementAndGet();
                                if (firstFails.get()) {
                                    throw new IllegalStateException("Cannot start the consumer yet");
                                }
                            }
                            super.doStart();
                        }
                    };
                }
            };
        }
    }
}
