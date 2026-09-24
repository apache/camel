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
package org.apache.camel.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.model.BeanFactoryDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that concurrent calls to {@link DefaultModel#addCustomBean} do not throw
 * {@link java.util.ConcurrentModificationException} or {@link NullPointerException}.
 */
public class DefaultModelConcurrentTest {

    @Test
    public void testConcurrentAddCustomBean() throws Exception {
        DefaultModel model = new DefaultModel(null);
        int threads = 20;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            final String name = "bean-" + i;
            futures.add(pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                BeanFactoryDefinition<?> def = new BeanFactoryDefinition<>();
                def.setName(name);
                model.addCustomBean(def);
            }));
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        for (Future<?> f : futures) {
            f.get(); // rethrows any ConcurrentModificationException from threads
        }

        assertEquals(threads, model.getCustomBeans().size());
    }

    @Test
    public void testConcurrentAddCustomBeanWithSameName() throws Exception {
        // Simulates multiple retries registering the same bean name concurrently —
        // only one entry per name must survive (the last one wins, no duplicates).
        DefaultModel model = new DefaultModel(null);
        int threads = 20;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    barrier.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                BeanFactoryDefinition<?> def = new BeanFactoryDefinition<>();
                def.setName("sharedBean");
                model.addCustomBean(def);
            }));
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        for (Future<?> f : futures) {
            f.get(); // rethrows any ConcurrentModificationException from threads
        }

        assertEquals(1, model.getCustomBeans().size());
        assertEquals("sharedBean", model.getCustomBeans().get(0).getName());
    }
}
