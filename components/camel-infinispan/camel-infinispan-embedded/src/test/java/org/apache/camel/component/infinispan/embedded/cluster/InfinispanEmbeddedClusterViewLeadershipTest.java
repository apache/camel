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
import org.apache.camel.cluster.CamelClusterView;
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

    @Test
    public void leadershipIsRefreshedAfterAnError() throws Exception {
        try (DefaultCacheManager cacheContainer = new DefaultCacheManager()) {
            createCache(cacheContainer, VIEW_NAME);
            FailingCacheManager cacheManager = new FailingCacheManager(cacheContainer);

            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.disableJMX();
                InfinispanEmbeddedClusterService clusterService = createClusterService(cacheManager);
                context.addService(clusterService);
                context.start();

                CamelClusterView view = clusterService.getView(VIEW_NAME);
                await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
                await().atMost(10, TimeUnit.SECONDS).until(() -> cacheManager.replaced.get() > 0);

                // the next refresh of the leadership fails
                cacheManager.failNextReplace.set(true);
                await().atMost(10, TimeUnit.SECONDS).until(() -> !cacheManager.failNextReplace.get());
                int replaced = cacheManager.replaced.get();

                // the leadership is still refreshed afterwards, and the node is the leader again
                await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                    assertThat(cacheManager.replaced.get()).isGreaterThan(replaced + 1);
                    assertThat(view.getLocalMember().isLeader()).isTrue();
                });
            }
        }
    }

    @Test
    public void stoppingTheViewGivesUpTheLeadership() throws Exception {
        try (DefaultCacheManager cacheContainer = new DefaultCacheManager()) {
            createCache(cacheContainer, VIEW_NAME);

            try (DefaultCamelContext context = new DefaultCamelContext()) {
                context.disableJMX();
                InfinispanEmbeddedClusterService clusterService = createClusterService(cacheContainer);
                context.addService(clusterService);
                context.start();

                CamelClusterView view = clusterService.getView(VIEW_NAME);
                List<Boolean> events = new CopyOnWriteArrayList<>();
                view.addEventListener((CamelClusterEventListener.Leadership) (v, leader) -> events
                        .add(v.getLocalMember().isLeader()));
                await().atMost(10, TimeUnit.SECONDS).until(() -> view.getLocalMember().isLeader());
                int received = events.size();

                view.stop();

                // the listeners are told that the local member is no longer the leader
                assertThat(view.getLocalMember().isLeader()).isFalse();
                assertThat(events).hasSize(received + 1);
                assertThat(events.get(received)).isFalse();
            }
        }
    }

    private static InfinispanEmbeddedClusterService createClusterService(EmbeddedCacheManager cacheManager) {
        InfinispanEmbeddedClusterService clusterService = new InfinispanEmbeddedClusterService();
        clusterService.setCacheContainer(cacheManager);
        clusterService.setId("node");
        clusterService.setLifespan(1000);
        clusterService.setLifespanTimeUnit(TimeUnit.MILLISECONDS);
        return clusterService;
    }

    /**
     * A cache manager whose caches count the refreshes of the leadership, and fail the next one when requested.
     */
    private static final class FailingCacheManager extends AbstractDelegatingEmbeddedCacheManager {
        private final AtomicBoolean failNextReplace = new AtomicBoolean();
        private final AtomicInteger replaced = new AtomicInteger();

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
                        throw new CacheException("Simulated failure while refreshing the leadership");
                    }
                    replaced.incrementAndGet();
                    return super.replace(key, oldValue, value, lifespan, unit);
                }
            };
        }
    }
}
