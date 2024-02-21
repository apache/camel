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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractInfinispanEmbeddedClusteredTest {
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    @Test
    public void test() throws Exception {
        final Logger logger = LoggerFactory.getLogger(getClass());
        final List<String> clients = IntStream.range(0, 3).mapToObj(Integer::toString).collect(Collectors.toList());
        final List<String> results = new ArrayList<>();
        final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(clients.size() * 2);
        final CountDownLatch latch = new CountDownLatch(clients.size());
        final String viewName = "myView";

        try (DefaultCacheManager cacheContainer = new DefaultCacheManager()) {
            InfinispanEmbeddedClusteredTestSupport.createCache(cacheContainer, viewName);

            for (String id : clients) {
                scheduler.submit(() -> {
                    try {
                        run(cacheContainer, viewName, id);
                        logger.debug("Node {} is shutting down", id);
                        results.add(id);
                    } catch (Exception e) {
                        logger.warn("{}", e.getMessage(), e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            scheduler.shutdownNow();

            assertThat(results).hasSameSizeAs(clients);
            assertThat(results).containsAll(clients);
        }
    }

    protected abstract void run(DefaultCacheManager cacheContainer, String namespace, String id) throws Exception;
}
