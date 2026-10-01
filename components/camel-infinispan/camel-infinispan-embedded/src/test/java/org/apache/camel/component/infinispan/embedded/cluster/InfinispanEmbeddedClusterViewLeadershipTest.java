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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.cluster.CamelClusterEventListener;
import org.apache.camel.cluster.CamelClusterMember;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.component.infinispan.cluster.InfinispanClusterService;
import org.apache.camel.impl.DefaultCamelContext;
import org.infinispan.Cache;
import org.infinispan.cache.impl.AbstractDelegatingCache;
import org.infinispan.commons.CacheException;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.manager.impl.AbstractDelegatingEmbeddedCacheManager;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.infinispan.embedded.cluster.InfinispanEmbeddedClusteredTestSupport.createCache;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

public class InfinispanEmbeddedClusterViewLeadershipTest {
    private static final String VIEW_NAME = "myView";
    private static final String NODE_ID = "node";

    @Test
    public void leadershipIsGivenUpAndRefreshedAfterALeaderKeyError() throws Exception {
        try (DefaultCacheManager cacheContainer = new DefaultCacheManager()) {
            createCache(cacheContainer, VIEW_NAME);
            FailingCacheManager cacheManager = new FailingCacheManager(cacheContainer);

            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.disableJMX();
                InfinispanEmbeddedClusterService clusterService = createClusterService(cacheManager, 1000);
                context.addService(clusterService);
                context.start();

                CamelClusterView view = clusterService.getView(VIEW_NAME);
                List<Boolean> events = new CopyOnWriteArrayList<>();
                view.addEventListener((CamelClusterEventListener.Leadership) (v, leader) -> events
                        .add(v.getLocalMember().isLeader()));
                await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
                await().atMost(10, TimeUnit.SECONDS).until(() -> cacheManager.replaced.get() > 0);
                int received = events.size();

                // the next refresh of the leader key fails
                cacheManager.failNextReplace.set(true);
                await().atMost(10, TimeUnit.SECONDS).until(() -> !cacheManager.failNextReplace.get());
                int replaced = cacheManager.replaced.get();

                // the leadership is given up, and as it is still refreshed afterwards the node is the leader again
                await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    assertThat(cacheManager.replaced.get()).isGreaterThan(replaced + 1);
                    assertThat(view.getLocalMember().isLeader()).isTrue();
                });
                assertThat(events.subList(received, events.size())).containsSubsequence(false, true);
            }
        }
    }

    @Test
    public void leadershipIsKeptAfterAMembershipError() throws Exception {
        try (DefaultCacheManager cacheContainer = new DefaultCacheManager()) {
            createCache(cacheContainer, VIEW_NAME);
            FailingCacheManager cacheManager = new FailingCacheManager(cacheContainer);

            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.disableJMX();
                // a longer lifespan, so that a slow refresh does not let the leader key expire and change the leadership
                InfinispanEmbeddedClusterService clusterService = createClusterService(cacheManager, 2000);
                context.addService(clusterService);
                context.start();

                CamelClusterView view = clusterService.getView(VIEW_NAME);
                List<Boolean> events = new CopyOnWriteArrayList<>();
                view.addEventListener((CamelClusterEventListener.Leadership) (v, leader) -> events
                        .add(v.getLocalMember().isLeader()));
                await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
                int received = events.size();

                // the next refresh of the membership fails
                cacheManager.failNextMembershipPut.set(true);
                await().atMost(10, TimeUnit.SECONDS).until(() -> !cacheManager.failNextMembershipPut.get());
                int replaced = cacheManager.replaced.get();
                int membershipPut = cacheManager.membershipPut.get();

                // the leadership and the membership are still refreshed afterwards
                await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    assertThat(cacheManager.replaced.get()).isGreaterThan(replaced + 1);
                    assertThat(cacheManager.membershipPut.get()).isGreaterThan(membershipPut + 1);
                });

                // and the leadership has not been given up meanwhile
                assertThat(view.getLocalMember().isLeader()).isTrue();
                assertThat(events).hasSize(received);
            }
        }
    }

    @Test
    public void stoppingTheViewGivesUpTheLeadershipBeforeReleasingTheLeaderKey() throws Exception {
        try (DefaultCacheManager cacheContainer = new DefaultCacheManager()) {
            createCache(cacheContainer, VIEW_NAME);
            Cache<String, String> cache = cacheContainer.getCache(VIEW_NAME);

            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.disableJMX();
                InfinispanEmbeddedClusterService clusterService = createClusterService(cacheContainer, 1000);
                context.addService(clusterService);
                context.start();

                CamelClusterView view = clusterService.getView(VIEW_NAME);
                List<Boolean> events = new CopyOnWriteArrayList<>();
                List<CamelClusterMember> leaders = new CopyOnWriteArrayList<>();
                List<String> leaderKeys = new CopyOnWriteArrayList<>();
                view.addEventListener((CamelClusterEventListener.Leadership) (v, leader) -> {
                    events.add(v.getLocalMember().isLeader());
                    leaders.add(leader);
                    leaderKeys.add(String.valueOf(cache.get(InfinispanClusterService.LEADER_KEY)));
                });
                await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
                int received = events.size();

                view.stop();

                // the listeners are told that the local member is no longer the leader
                assertThat(view.getLocalMember().isLeader()).isFalse();
                assertThat(events).hasSize(received + 1);
                assertThat(events.get(received)).isFalse();
                assertThat(leaders.get(received)).isNull();

                // while the leader key was still held, so that no other member could take over the leadership yet
                assertThat(leaderKeys.get(received)).isEqualTo(NODE_ID);

                // which is released afterwards
                assertThat(cache.get(InfinispanClusterService.LEADER_KEY)).isNull();
            }
        }
    }

    private static InfinispanEmbeddedClusterService createClusterService(EmbeddedCacheManager cacheManager, long lifespan) {
        InfinispanEmbeddedClusterService clusterService = new InfinispanEmbeddedClusterService();
        clusterService.setCacheContainer(cacheManager);
        clusterService.setId(NODE_ID);
        clusterService.setLifespan(lifespan);
        clusterService.setLifespanTimeUnit(TimeUnit.MILLISECONDS);
        return clusterService;
    }

    /**
     * A cache manager whose caches count the refreshes of the leader key and of the membership, and fail the next one
     * when requested.
     */
    private static final class FailingCacheManager extends AbstractDelegatingEmbeddedCacheManager {
        private final AtomicBoolean failNextReplace = new AtomicBoolean();
        private final AtomicBoolean failNextMembershipPut = new AtomicBoolean();
        private final AtomicInteger replaced = new AtomicInteger();
        private final AtomicInteger membershipPut = new AtomicInteger();

        private FailingCacheManager(EmbeddedCacheManager cm) {
            super(cm);
        }

        @Override
        public <K, V> Cache<K, V> getCache(String cacheName) {
            Cache<K, V> cache = super.getCache(cacheName);
            return new AbstractDelegatingCache<>(cache) {
                @Override
                public boolean replace(K key, V oldValue, V value, long lifespan, TimeUnit unit) {
                    if (failNextReplace.compareAndSet(true, false)) {
                        throw new CacheException("Simulated failure while refreshing the leader key");
                    }
                    replaced.incrementAndGet();
                    return super.replace(key, oldValue, value, lifespan, unit);
                }

                @Override
                public V put(K key, V value, long lifespan, TimeUnit unit) {
                    if (NODE_ID.equals(key)) {
                        if (failNextMembershipPut.compareAndSet(true, false)) {
                            throw new CacheException("Simulated failure while refreshing the membership");
                        }
                        membershipPut.incrementAndGet();
                    }
                    return super.put(key, value, lifespan, unit);
                }
            };
        }
    }
}
