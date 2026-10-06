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
package org.apache.camel.component.zookeepermaster;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The leadership events of the group against the consumer of the master endpoint, with a fake group (no ZooKeeper): the
 * CHANGED events are delivered on the group thread, DISCONNECTED on the Curator connection thread.
 */
class MasterConsumerLeadershipTest extends CamelTestSupport {

    @BindToRegistry("fakeGroupFactory")
    private final FakeGroupFactory groupFactory = new FakeGroupFactory();

    private final ControlledEndpoint delegateEndpoint = new ControlledEndpoint();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testFailedStartIsRetried() throws Exception {
        startRoute();
        masterConsumer().setRetryDelay(10);
        delegateEndpoint.failStarts.set(1);

        // elected: the start of the consumer fails (broker or server not available yet)
        groupFactory.group.fireChanged();
        assertEquals(0, delegateEndpoint.running.get());

        // the master must try again, otherwise this node holds the leadership and nobody consumes
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, delegateEndpoint.running.get(),
                "The master must start its consumer after a failed start"));
    }

    @Test
    void testDisconnectedWhileCreatingConsumer() throws Exception {
        startRoute();
        delegateEndpoint.createGate = new CountDownLatch(1);

        Thread groupThread = new Thread(groupFactory.group::fireChanged, "group");
        groupThread.start();
        assertTrue(delegateEndpoint.creating.await(20, TimeUnit.SECONDS));

        // the connection to ZooKeeper is lost: this node is no longer the master
        groupFactory.group.connected = false;
        groupFactory.group.master = false;
        groupFactory.group.fire(GroupListener.GroupEvent.DISCONNECTED);

        delegateEndpoint.createGate.countDown();
        groupThread.join(20000);
        assertEquals(0, delegateEndpoint.running.get(), "A node that lost the leadership must not consume");
    }

    @Test
    void testStoppedWhileCreatingConsumer() throws Exception {
        startRoute();
        delegateEndpoint.createGate = new CountDownLatch(1);

        Thread groupThread = new Thread(groupFactory.group::fireChanged, "group");
        groupThread.start();
        assertTrue(delegateEndpoint.creating.await(20, TimeUnit.SECONDS));

        // the start takes longer than the 5 seconds the group waits for its thread when it is closed
        context.getRouteController().stopRoute("master");

        delegateEndpoint.createGate.countDown();
        groupThread.join(20000);
        assertEquals(0, delegateEndpoint.running.get(), "A stopped route must not consume");
    }

    @Test
    void testReconnectedWhileRetryIsStarting() throws Exception {
        startRoute();
        masterConsumer().setRetryDelay(10);
        delegateEndpoint.failStarts.set(1);
        // the first start fails, the retry (on the retry thread) is held while it creates its consumer
        delegateEndpoint.gateFrom = 2;
        delegateEndpoint.createGate = new CountDownLatch(1);

        groupFactory.group.fireChanged();
        assertTrue(delegateEndpoint.creating.await(20, TimeUnit.SECONDS));

        // ZooKeeper suspends and reconnects meanwhile, and this node is elected again
        groupFactory.group.connected = false;
        groupFactory.group.master = false;
        groupFactory.group.fire(GroupListener.GroupEvent.DISCONNECTED);
        groupFactory.group.connected = true;
        groupFactory.group.master = true;
        groupFactory.group.fireChanged();

        delegateEndpoint.createGate.countDown();
        // the retry stops its consumer (it began before the disconnect), and the master must start one again
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, delegateEndpoint.running.get(),
                "A master elected again while a start was in flight must consume"));
    }

    @Test
    void testConsumerRestartedWhileCreatingConsumer() throws Exception {
        startRoute();
        CountDownLatch gate = new CountDownLatch(1);
        delegateEndpoint.createGate = gate;

        Thread groupThread = new Thread(groupFactory.group::fireChanged, "group");
        groupThread.start();
        assertTrue(delegateEndpoint.creating.await(20, TimeUnit.SECONDS));
        // only that create is slow
        delegateEndpoint.createGate = null;

        // the consumer is stopped and started again (as by JMX; a route restart creates a new consumer) while the old
        // group thread is still in the slow start, and the new group elects this node
        MasterConsumer consumer = masterConsumer();
        consumer.stop();
        consumer.start();
        groupFactory.group.fireChanged();

        gate.countDown();
        groupThread.join(20000);
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(1, delegateEndpoint.running.get(),
                "A master started again while its old start was in flight must consume"));
    }

    private MasterConsumer masterConsumer() {
        return (MasterConsumer) context.getRoute("master").getConsumer();
    }

    private void startRoute() throws Exception {
        context.addEndpoint("controlled:delegate", delegateEndpoint);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("zookeeper-master:test:controlled:delegate").routeId("master").to("mock:result");
            }
        });
        context.start();
        assertTrue(groupFactory.group != null && groupFactory.group.listeners.size() == 1);
    }

    private final class ControlledEndpoint extends DefaultEndpoint {
        final AtomicInteger failStarts = new AtomicInteger();
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger creates = new AtomicInteger();
        final CountDownLatch creating = new CountDownLatch(1);
        // the createGate holds the creates from this one on (1 = the first)
        volatile int gateFrom = 1;
        volatile CountDownLatch createGate;

        @Override
        protected String createEndpointUri() {
            return "controlled:delegate";
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            if (creates.incrementAndGet() < gateFrom) {
                return newConsumer(processor);
            }
            // read the gate before signalling, as the test may clear it right after that signal
            CountDownLatch gate = createGate;
            creating.countDown();
            if (gate != null) {
                assertTrue(gate.await(20, TimeUnit.SECONDS));
            }
            return newConsumer(processor);
        }

        private Consumer newConsumer(Processor processor) {
            return new DefaultConsumer(this, processor) {
                private boolean up;

                @Override
                protected void doStart() throws Exception {
                    if (failStarts.getAndDecrement() > 0) {
                        throw new IllegalStateException("Simulated start failure");
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
            return master;
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
