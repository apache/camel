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
package org.apache.camel.component.zookeepermaster.policy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.zookeepermaster.CamelNodeState;
import org.apache.camel.component.zookeepermaster.group.Group;
import org.apache.camel.component.zookeepermaster.group.GroupListener;
import org.apache.camel.component.zookeepermaster.group.ManagedGroupFactory;
import org.apache.camel.component.zookeepermaster.group.NodeState;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.curator.framework.CuratorFramework;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The leadership events of the group against the route consumer that {@link MasterRoutePolicy} starts and stops, with a
 * fake group (no ZooKeeper): the CHANGED events are delivered on the group thread, DISCONNECTED on the Curator
 * connection thread.
 */
class MasterRoutePolicyLeadershipTest extends CamelTestSupport {

    @BindToRegistry("fakeGroupFactory")
    private final FakeGroupFactory groupFactory = new FakeGroupFactory();

    private final ControlledEndpoint endpoint = new ControlledEndpoint();
    private final MasterRoutePolicy policy = new MasterRoutePolicy();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testDisconnectedWhileElected() throws Exception {
        startRoute();
        // the group thread checks that this node is the master, and is held right after that check
        CountDownLatch masterCheck = new CountDownLatch(1);
        groupFactory.group.masterChecked = new CountDownLatch(1);
        groupFactory.group.masterCheck = masterCheck;

        Thread groupThread = new Thread(groupFactory.group::fireChanged, "group");
        groupThread.start();
        assertTrue(groupFactory.group.masterChecked.await(20, TimeUnit.SECONDS));

        // the connection to ZooKeeper is lost meanwhile: this node is no longer the master
        groupFactory.group.connected = false;
        groupFactory.group.master = false;
        groupFactory.group.fire(GroupListener.GroupEvent.DISCONNECTED);

        masterCheck.countDown();
        groupThread.join(20000);
        assertFalse(groupThread.isAlive());
        assertEquals(0, endpoint.running.get(), "A node that lost the leadership must not consume");
    }

    @Test
    void testFailedStartIsRetried() throws Exception {
        startRoute();
        policy.setRetryDelay(10);
        endpoint.failStarts.set(1);

        // elected: the start of the consumer fails (broker or server not available yet)
        groupFactory.group.fireChanged();
        assertEquals(0, endpoint.running.get());

        // the master must try again, otherwise this node holds the leadership and nobody consumes
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, endpoint.running.get(),
                "The master must start its consumer after a failed start"));
    }

    @Test
    void testDisconnectedCancelsTheRetry() throws Exception {
        startRoute();
        policy.setRetryDelay(300);
        endpoint.failStarts.set(1);

        groupFactory.group.fireChanged();
        groupFactory.group.connected = false;
        groupFactory.group.master = false;
        groupFactory.group.fire(GroupListener.GroupEvent.DISCONNECTED);
        // the group connects again, but no event comes yet: only a retry that was not cancelled would start the consumer
        groupFactory.group.connected = true;
        groupFactory.group.master = true;

        await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, endpoint.running.get(), "A disconnect must cancel the retry"));
    }

    @Test
    void testStopCancelsTheRetry() throws Exception {
        startRoute();
        policy.setRetryDelay(300);
        endpoint.failStarts.set(1);

        groupFactory.group.fireChanged();
        policy.stop();

        await().during(1, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, endpoint.running.get(), "A stopped policy must not start the consumer"));
    }

    @Test
    void testStopWhileStarting() throws Exception {
        startRoute();
        endpoint.startGate = new CountDownLatch(1);

        Thread groupThread = new Thread(groupFactory.group::fireChanged, "group");
        groupFactory.group.thread = groupThread;
        groupThread.start();
        assertTrue(endpoint.starting.await(20, TimeUnit.SECONDS));

        // the policy stops while the consumer start hangs (a broker that does not answer): the group interrupts its
        // thread when it is closed, and the stop must not wait for the start
        CompletableFuture<Void> stop = CompletableFuture.runAsync(policy::stop);
        try {
            await().atMost(5, TimeUnit.SECONDS).until(stop::isDone);
        } finally {
            // let a start that was not interrupted complete, so the test ends either way
            endpoint.startGate.countDown();
        }
        stop.get(20, TimeUnit.SECONDS);
        groupThread.join(20000);
        assertFalse(groupThread.isAlive());
        assertEquals(0, endpoint.running.get(), "A stopped policy must not consume");
    }

    private void startRoute() throws Exception {
        context.addEndpoint("controlled:in", endpoint);
        policy.setGroupName("test");
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("controlled:in").routeId("master").autoStartup(false).routePolicy(policy).to("mock:result");
            }
        });
        context.start();
        assertTrue(groupFactory.group != null && groupFactory.group.listeners.size() == 1);
    }

    private final class ControlledEndpoint extends DefaultEndpoint {
        final AtomicInteger failStarts = new AtomicInteger();
        final AtomicInteger running = new AtomicInteger();
        final CountDownLatch starting = new CountDownLatch(1);
        // when set, a start waits for it (interruptibly)
        volatile CountDownLatch startGate;

        @Override
        protected String createEndpointUri() {
            return "controlled:in";
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new DefaultConsumer(this, processor) {
                private boolean up;

                @Override
                protected void doStart() throws Exception {
                    if (failStarts.getAndDecrement() > 0) {
                        throw new IllegalStateException("Simulated start failure");
                    }
                    CountDownLatch gate = startGate;
                    if (gate != null) {
                        starting.countDown();
                        gate.await();
                    }
                    super.doStart();
                    up = true;
                    running.incrementAndGet();
                }

                @Override
                protected void doStop() throws Exception {
                    if (up) {
                        up = false;
                        running.decrementAndGet();
                    }
                    super.doStop();
                }
            };
        }
    }

    private static final class FakeGroupFactory implements ManagedGroupFactory {
        volatile FakeGroup group;

        @Override
        @SuppressWarnings("unchecked")
        public <T extends NodeState> Group<T> createGroup(String path, Class<T> clazz) {
            group = new FakeGroup();
            return (Group<T>) group;
        }

        @Override
        public <T extends NodeState> Group<T> createGroup(String path, Class<T> clazz, ThreadFactory threadFactory) {
            return createGroup(path, clazz);
        }

        @Override
        public <T extends NodeState> Group<T> createMultiGroup(String path, Class<T> clazz) {
            return createGroup(path, clazz);
        }

        @Override
        public <T extends NodeState> Group<T> createMultiGroup(String path, Class<T> clazz, ThreadFactory threadFactory) {
            return createGroup(path, clazz);
        }

        @Override
        public CuratorFramework getCurator() {
            return null;
        }

        @Override
        public void close() {
            // noop
        }
    }

    private static final class FakeGroup implements Group<CamelNodeState> {
        final List<GroupListener<CamelNodeState>> listeners = new CopyOnWriteArrayList<>();
        volatile boolean connected = true;
        volatile boolean master = true;
        // when set, the next isMaster() of the group thread signals masterChecked and waits for masterCheck after it
        // read the answer
        volatile CountDownLatch masterCheck;
        volatile CountDownLatch masterChecked;
        // the thread delivering the CHANGED events, interrupted on close as ZooKeeperGroup does (shutdownNow)
        volatile Thread thread;

        void fireChanged() {
            fire(GroupListener.GroupEvent.CHANGED);
        }

        void fire(GroupListener.GroupEvent event) {
            listeners.forEach(l -> l.groupEvent(this, event));
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void start() {
            // noop
        }

        @Override
        public void close() {
            Thread t = thread;
            if (t != null) {
                t.interrupt();
            }
            // as ZooKeeperGroup: a group closed while connected tells its listeners it is disconnected
            if (connected) {
                fire(GroupListener.GroupEvent.DISCONNECTED);
            }
            listeners.clear();
        }

        @Override
        public void add(GroupListener<CamelNodeState> listener) {
            listeners.add(listener);
        }

        @Override
        public void remove(GroupListener<CamelNodeState> listener) {
            listeners.remove(listener);
        }

        @Override
        public void update(CamelNodeState state) {
            // noop
        }

        @Override
        public Map<String, CamelNodeState> members() {
            return Collections.emptyMap();
        }

        @Override
        public boolean isMaster() {
            boolean answer = master;
            CountDownLatch gate = masterCheck;
            if (gate != null && "group".equals(Thread.currentThread().getName())) {
                masterCheck = null;
                masterChecked.countDown();
                try {
                    gate.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return answer;
        }

        @Override
        public CamelNodeState master() {
            return null;
        }

        @Override
        public List<CamelNodeState> slaves() {
            return Collections.emptyList();
        }

        @Override
        public CamelNodeState getLastState() {
            return null;
        }
    }
}
