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
package org.apache.camel.component.infinispan.embedded.cluster;

import java.util.List;
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

import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.component.infinispan.cluster.InfinispanClusterService;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.DefaultThreadPoolFactory;
import org.infinispan.Cache;
import org.infinispan.cache.impl.AbstractDelegatingCache;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.manager.impl.AbstractDelegatingEmbeddedCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.infinispan.embedded.cluster.InfinispanEmbeddedClusteredTestSupport.createCache;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Stopping the view while the leadership thread is busy: the local member gives up the leadership at once, and the stop
 * does not wait on the lock of the leadership service.
 */
class InfinispanEmbeddedClusterViewStopTest {
    private static final String VIEW_NAME = "myView";
    private static final String NODE_ID = "node";
    private static final long SHUTDOWN_AWAIT_TERMINATION = 1000;

    private final DefaultCacheManager cacheContainer = new DefaultCacheManager();
    private final BlockingCacheManager cacheManager = new BlockingCacheManager(cacheContainer);
    private final LeadershipThreadPoolFactory pools = new LeadershipThreadPoolFactory();
    private final ExecutorService stopper = Executors.newSingleThreadExecutor();
    private final List<Boolean> events = new CopyOnWriteArrayList<>();
    private DefaultCamelContext context;
    private CamelClusterView view;

    @AfterEach
    void tearDown() throws Exception {
        cacheManager.releaseAll();
        stopper.shutdownNow();
        stopper.awaitTermination(10, TimeUnit.SECONDS);
        if (context != null) {
            context.close();
        }
        cacheContainer.close();
    }

    @Test
    void runStartedWhileStoppingDoesNotDelayTheStop() throws Exception {
        startView();
        await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
        int received = events.size();

        // the stop is held in the removal of the cache listener, while a leadership run starts (as the periodic run or
        // a run queued by an event would)
        cacheManager.blockRemoveListener = true;
        CompletableFuture<Long> stop = stopAsync();
        assertThat(cacheManager.removeListenerCalled.await(10, TimeUnit.SECONDS)).isTrue();
        CountDownLatch runStarted = new CountDownLatch(1);
        pools.runAgain(runStarted::countDown);
        assertThat(runStarted.await(10, TimeUnit.SECONDS)).isTrue();
        cacheManager.removeListenerRelease.countDown();

        // the stop does not wait for that run until the shutdown await termination runs out twice, as the run does not
        // wait for the lock of the leadership service held by the stop
        assertThat(stop.get(10, TimeUnit.SECONDS)).isLessThan(SHUTDOWN_AWAIT_TERMINATION);

        // and the leadership was given up before the stop waited for anything else
        assertThat(cacheManager.leaderWhenRemovingListener).isFalse();
        assertThat(cacheManager.eventsWhenRemovingListener).isEqualTo(events);
        assertThat(events.subList(received, events.size())).containsExactly(false);
        assertThat(cacheContainer.getCache(VIEW_NAME).get(InfinispanClusterService.LEADER_KEY)).isNull();
    }

    @Test
    void runThatAcquiresTheLeaderKeyWhileStoppingDoesNotTakeTheLeadership() throws Exception {
        // the first leadership run is held while it acquires the leader key
        cacheManager.blockPutIfAbsent = true;
        startView();
        assertThat(cacheManager.putIfAbsentCalled.await(10, TimeUnit.SECONDS)).isTrue();
        // (a new leadership listener is told the current leader when it is added)
        int received = events.size();

        CompletableFuture<Long> stop = stopAsync();
        assertThat(cacheManager.removeListenerCalled.await(10, TimeUnit.SECONDS)).isTrue();

        // the key is then acquired
        cacheManager.putIfAbsentRelease.countDown();
        stop.get(10, TimeUnit.SECONDS);

        // the stopped member did not become the leader, and the key it acquired is released
        assertThat(view.getLocalMember().isLeader()).isFalse();
        assertThat(events.subList(received, events.size())).isEmpty();
        assertThat(events).doesNotContain(true);
        assertThat(cacheContainer.getCache(VIEW_NAME).get(InfinispanClusterService.LEADER_KEY)).isNull();
    }

    private void startView() throws Exception {
        createCache(cacheContainer, VIEW_NAME);

        context = new DefaultCamelContext();
        context.disableJMX();
        context.getExecutorServiceManager().setThreadPoolFactory(pools);
        context.getExecutorServiceManager().setShutdownAwaitTermination(SHUTDOWN_AWAIT_TERMINATION);

        InfinispanEmbeddedClusterService clusterService = new InfinispanEmbeddedClusterService();
        clusterService.setCacheContainer(cacheManager);
        clusterService.setId(NODE_ID);
        // a long lifespan, so that the periodic run does not run during the test (the test runs it instead)
        clusterService.setLifespan(60);
        clusterService.setLifespanTimeUnit(TimeUnit.SECONDS);
        context.addService(clusterService);
        context.start();

        view = clusterService.getView(VIEW_NAME);
        cacheManager.view = view;
        cacheManager.events = events;
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
     * A cache manager whose caches hold the removal of the listener or the acquisition of the leader key until the test
     * releases them.
     */
    private static final class BlockingCacheManager extends AbstractDelegatingEmbeddedCacheManager {
        private final CountDownLatch removeListenerCalled = new CountDownLatch(1);
        private final CountDownLatch removeListenerRelease = new CountDownLatch(1);
        private final CountDownLatch putIfAbsentCalled = new CountDownLatch(1);
        private final CountDownLatch putIfAbsentRelease = new CountDownLatch(1);

        private volatile CamelClusterView view;
        private volatile List<Boolean> events;
        private volatile boolean blockRemoveListener;
        private volatile boolean blockPutIfAbsent;
        private volatile Boolean leaderWhenRemovingListener;
        private volatile List<Boolean> eventsWhenRemovingListener;

        private BlockingCacheManager(EmbeddedCacheManager cm) {
            super(cm);
        }

        void releaseAll() {
            removeListenerRelease.countDown();
            putIfAbsentRelease.countDown();
        }

        @Override
        public <K, V> Cache<K, V> getCache(String cacheName) {
            Cache<K, V> cache = super.getCache(cacheName);
            return new AbstractDelegatingCache<>(cache) {
                @Override
                public void removeListener(Object listener) {
                    leaderWhenRemovingListener = view.getLocalMember().isLeader();
                    eventsWhenRemovingListener = List.copyOf(events);
                    removeListenerCalled.countDown();
                    if (blockRemoveListener) {
                        waitFor(removeListenerRelease);
                    }
                    super.removeListener(listener);
                }

                @Override
                public V putIfAbsent(K key, V value, long lifespan, TimeUnit unit) {
                    if (InfinispanClusterService.LEADER_KEY.equals(key)) {
                        putIfAbsentCalled.countDown();
                        if (blockPutIfAbsent) {
                            waitFor(putIfAbsentRelease);
                        }
                    }
                    return super.putIfAbsent(key, value, lifespan, unit);
                }
            };
        }

        private static void waitFor(CountDownLatch latch) {
            try {
                latch.await(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
