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
package org.apache.camel.component.infinispan.remote.cluster;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.component.infinispan.cluster.InfinispanClusterService;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.infinispan.client.hotrod.MetadataValue;
import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.exceptions.HotRodClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Stopping the view while the leadership thread is busy: the local member gives up the leadership at once, and the stop
 * does not wait on the lock of the leadership service.
 */
class InfinispanRemoteClusterViewStopTest {
    private static final String VIEW_NAME = "myView";
    private static final String NODE_ID = "node";
    private static final String LEADER_KEY = InfinispanClusterService.LEADER_KEY;
    private static final long SHUTDOWN_AWAIT_TERMINATION = 1000;

    private final FakeRemoteCache server = new FakeRemoteCache();
    private final LeadershipThreadPoolFactory pools = new LeadershipThreadPoolFactory();
    private final ExecutorService stopper = Executors.newSingleThreadExecutor();
    private final List<Boolean> events = new CopyOnWriteArrayList<>();
    private DefaultCamelContext context;
    private CamelClusterView view;

    @AfterEach
    void tearDown() throws Exception {
        server.releaseAll();
        stopper.shutdownNow();
        stopper.awaitTermination(10, TimeUnit.SECONDS);
        if (context != null) {
            context.close();
        }
    }

    @Test
    void runStartedWhileStoppingDoesNotDelayTheStop() throws Exception {
        startView();
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
        int received = events.size();

        // the stop is held in the removal of the client listener, while a leadership run starts (as the periodic run
        // or a run queued by an event would)
        server.blockRemoveClientListener = true;
        CompletableFuture<Long> stop = stopAsync();
        assertThat(server.removeClientListenerCalled.await(10, TimeUnit.SECONDS)).isTrue();
        CountDownLatch runStarted = new CountDownLatch(1);
        pools.runAgain(runStarted::countDown);
        assertThat(runStarted.await(10, TimeUnit.SECONDS)).isTrue();
        server.removeClientListenerRelease.countDown();

        // the stop does not wait for that run until the shutdown await termination runs out twice, as the run does not
        // wait for the lock of the leadership service held by the stop
        assertThat(stop.get(10, TimeUnit.SECONDS)).isLessThan(SHUTDOWN_AWAIT_TERMINATION);

        // and the leadership was given up before the stop waited for anything else
        assertThat(server.leaderWhenRemovingClientListener).isFalse();
        assertThat(server.eventsWhenRemovingClientListener).isEqualTo(events);
        assertThat(events.subList(received, events.size())).containsExactly(false);
        assertThat(server.value(LEADER_KEY)).isNull();
    }

    @Test
    void stopGivesUpTheLeadershipWhileARunWaitsForTheServer() throws Exception {
        startView();
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
        int received = events.size();

        // the refresh of the leader key waits for the server
        server.blockReplaceWithVersion = true;
        pools.runAgain(() -> {
        });
        assertThat(server.replaceWithVersionCalled.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Long> stop = stopAsync();

        // the listeners are told that the local member is no longer the leader while that refresh still waits
        await().atMost(10, TimeUnit.SECONDS).until(() -> !view.getLocalMember().isLeader());
        assertThat(server.replaceWithVersionReturned).isFalse();
        assertThat(events.subList(received, events.size())).containsExactly(false);

        // the stop waits for the run once, then interrupts the call that still waits for the server, which ends the run
        assertThat(stop.get(10, TimeUnit.SECONDS)).isBetween(SHUTDOWN_AWAIT_TERMINATION, 2 * SHUTDOWN_AWAIT_TERMINATION);
        assertThat(server.replaceWithVersionReturned).isTrue();

        // the leader key is still released with the version that the local member holds
        assertThat(server.value(LEADER_KEY)).isNull();
        assertThat(events.subList(received, events.size())).containsExactly(false);
    }

    @Test
    void runThatAcquiresTheLeaderKeyWhileStoppingDoesNotTakeTheLeadership() throws Exception {
        // the first leadership run waits for the server while it acquires the leader key
        server.blockPutIfAbsent = true;
        startView();
        assertThat(server.putIfAbsentCalled.await(10, TimeUnit.SECONDS)).isTrue();
        // (a new leadership listener is told the current leader when it is added)
        int received = events.size();

        CompletableFuture<Long> stop = stopAsync();
        assertThat(server.removeClientListenerCalled.await(10, TimeUnit.SECONDS)).isTrue();

        // the server then answers that the key was acquired
        server.putIfAbsentRelease.countDown();
        stop.get(10, TimeUnit.SECONDS);

        // the stopped member did not become the leader, and the key it acquired is released
        assertThat(view.getLocalMember().isLeader()).isFalse();
        assertThat(events.subList(received, events.size())).isEmpty();
        assertThat(events).doesNotContain(true);
        assertThat(server.value(LEADER_KEY)).isNull();
    }

    private void startView() throws Exception {
        context = new DefaultCamelContext();
        context.disableJMX();
        context.getExecutorServiceManager().setThreadPoolFactory(pools);
        context.getExecutorServiceManager().setShutdownAwaitTermination(SHUTDOWN_AWAIT_TERMINATION);

        InfinispanRemoteClusterService clusterService = new InfinispanRemoteClusterService();
        clusterService.setId(NODE_ID);
        clusterService.setCacheContainer(server.cacheManager());
        // a long lifespan, so that the periodic run does not run during the test (the test runs it instead)
        clusterService.setLifespan(60);
        clusterService.setLifespanTimeUnit(TimeUnit.SECONDS);
        context.addService(clusterService);
        context.start();

        view = clusterService.getView(VIEW_NAME);
        server.view = view;
        server.events = events;
        view.addEventListener((CamelClusterEventListener.Leadership) (v, leader) -> events
                .add(v.getLocalMember().isLeader()));
    }

    /**
     * Stops the view on another thread, and returns how long the stop took in millis.
     */
    private CompletableFuture<Long> stopAsync() {
        return CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            view.stop();
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        }, stopper);
    }

    /**
     * A thread pool factory that keeps the executor and the periodic task of the leadership service, so that the test
     * can run the task again on the leadership thread when it needs to.
     */
    private static final class LeadershipThreadPoolFactory extends DefaultThreadPoolFactory {
        private volatile ScheduledExecutorService leadershipExecutor;
        private volatile Runnable leadershipRun;

        @Override
        public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
            if (!profile.getId().startsWith("LeadershipService")) {
                return super.newScheduledThreadPool(profile, threadFactory);
            }
            ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1, threadFactory) {
                @Override
                public ScheduledFuture<?> scheduleAtFixedRate(
                        Runnable command, long initialDelay, long period, TimeUnit unit) {
                    leadershipRun = command;
                    return super.scheduleAtFixedRate(command, initialDelay, period, unit);
                }
            };
            leadershipExecutor = executor;
            return executor;
        }

        void runAgain(Runnable before) {
            Runnable run = leadershipRun;
            leadershipExecutor.execute(() -> {
                before.run();
                run.run();
            });
        }
    }

    /**
     * A remote cache of a single client, with the versioned entries the leadership relies on. Selected calls can wait
     * for the test. As the Hot Rod client, a call that is interrupted (or made by an interrupted thread) throws a
     * {@link HotRodClientException} and keeps the interrupt status of the thread (see
     * {@code org.infinispan.client.hotrod.impl.Util#await}).
     */
    private static final class FakeRemoteCache {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Long> versions = new HashMap<>();
        private final AtomicLong lastVersion = new AtomicLong();

        private final CountDownLatch removeClientListenerCalled = new CountDownLatch(1);
        private final CountDownLatch removeClientListenerRelease = new CountDownLatch(1);
        private final CountDownLatch replaceWithVersionCalled = new CountDownLatch(1);
        private final CountDownLatch replaceWithVersionRelease = new CountDownLatch(1);
        private final CountDownLatch putIfAbsentCalled = new CountDownLatch(1);
        private final CountDownLatch putIfAbsentRelease = new CountDownLatch(1);

        private volatile CamelClusterView view;
        private volatile List<Boolean> events;
        private volatile boolean blockRemoveClientListener;
        private volatile boolean blockReplaceWithVersion;
        private volatile boolean blockPutIfAbsent;
        private volatile boolean replaceWithVersionReturned;
        private volatile Boolean leaderWhenRemovingClientListener;
        private volatile List<Boolean> eventsWhenRemovingClientListener;

        RemoteCacheManager cacheManager() {
            RemoteCache<String, String> cache = cache();
            return new RemoteCacheManager(false) {
                @Override
                @SuppressWarnings("unchecked")
                public <K, V> RemoteCache<K, V> getCache(String cacheName) {
                    return (RemoteCache<K, V>) cache;
                }
            };
        }

        synchronized String value(String key) {
            return values.get(key);
        }

        void releaseAll() {
            removeClientListenerRelease.countDown();
            replaceWithVersionRelease.countDown();
            putIfAbsentRelease.countDown();
        }

        @SuppressWarnings("unchecked")
        private RemoteCache<String, String> cache() {
            Object[] self = new Object[1];
            self[0] = Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { RemoteCache.class },
                    (proxy, method, args) -> switch (method.getName()) {
                        case "toString" -> "FakeRemoteCache";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "withFlags" -> self[0];
                        case "addClientListener" -> null;
                        default -> call(method.getName(), args);
                    });
            return (RemoteCache<String, String>) self[0];
        }

        private Object call(String name, Object[] args) {
            if (Thread.currentThread().isInterrupted()) {
                throw new HotRodClientException(new InterruptedException());
            }
            switch (name) {
                case "removeClientListener":
                    leaderWhenRemovingClientListener = view.getLocalMember().isLeader();
                    eventsWhenRemovingClientListener = List.copyOf(events);
                    removeClientListenerCalled.countDown();
                    if (blockRemoveClientListener) {
                        waitFor(removeClientListenerRelease);
                    }
                    return null;
                case "putIfAbsent":
                    putIfAbsentCalled.countDown();
                    if (blockPutIfAbsent) {
                        waitFor(putIfAbsentRelease);
                    }
                    return putIfAbsent((String) args[0], (String) args[1]);
                case "replaceWithVersion":
                    replaceWithVersionCalled.countDown();
                    try {
                        if (blockReplaceWithVersion) {
                            waitFor(replaceWithVersionRelease);
                        }
                        return replaceWithVersion((String) args[0], (String) args[1], (Long) args[2]);
                    } finally {
                        replaceWithVersionReturned = true;
                    }
                case "put":
                    return put((String) args[0], (String) args[1]);
                case "get":
                    return value((String) args[0]);
                case "getWithMetadata":
                    return metadata((String) args[0]);
                case "removeWithVersion":
                    return removeWithVersion((String) args[0], (Long) args[1]);
                case "remove":
                    return remove((String) args[0]);
                default:
                    throw new UnsupportedOperationException(name);
            }
        }

        private static void waitFor(CountDownLatch latch) {
            try {
                latch.await(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new HotRodClientException(e);
            }
        }

        private synchronized String put(String key, String value) {
            versions.put(key, lastVersion.incrementAndGet());
            return values.put(key, value);
        }

        private synchronized String putIfAbsent(String key, String value) {
            String old = values.get(key);
            if (old == null) {
                put(key, value);
            }
            return old;
        }

        private synchronized boolean replaceWithVersion(String key, String value, long version) {
            if (!values.containsKey(key) || versions.get(key) != version) {
                return false;
            }
            put(key, value);
            return true;
        }

        private synchronized boolean removeWithVersion(String key, long version) {
            if (!values.containsKey(key) || versions.get(key) != version) {
                return false;
            }
            remove(key);
            return true;
        }

        private synchronized String remove(String key) {
            versions.remove(key);
            return values.remove(key);
        }

        @SuppressWarnings("unchecked")
        private synchronized MetadataValue<String> metadata(String key) {
            if (!values.containsKey(key)) {
                return null;
            }
            long version = versions.get(key);
            return (MetadataValue<String>) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { MetadataValue.class },
                    (proxy, method, args) -> "getVersion".equals(method.getName()) ? version : null);
        }
    }
}
