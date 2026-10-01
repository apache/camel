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
package org.apache.camel.component.caffeine.processor.idempotent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CaffeineIdempotentRepositoryConcurrentAddTest {

    private static final String KEY = "message-1";

    private CaffeineIdempotentRepository repo;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        repo = new CaffeineIdempotentRepository("concurrent");
        repo.start();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        repo.stop();
    }

    @Test
    void testConcurrentAddOfSameKey() throws Exception {
        Thread[] adders = new Thread[2];
        Future<?>[] results = new Future<?>[2];

        // while the entry of the key is being computed, the key is reported as absent and a write of the key waits
        // until the computation has ended, so both calls of add below have looked for the key before either of them
        // could insert it
        repo.getCache().asMap().compute(KEY, (k, v) -> {
            for (int i = 0; i < 2; i++) {
                int index = i;
                results[i] = executor.submit(() -> {
                    adders[index] = Thread.currentThread();
                    return repo.add(KEY);
                });
            }
            await().atMost(10, TimeUnit.SECONDS).until(() -> isBlocked(adders[0]) && isBlocked(adders[1]));
            return v;
        });

        int added = 0;
        for (Future<?> result : results) {
            if ((Boolean) result.get(10, TimeUnit.SECONDS)) {
                added++;
            }
        }
        assertEquals(1, added, "Only one of the concurrent calls should add the key");
        assertTrue(repo.contains(KEY));
    }

    private static boolean isBlocked(Thread thread) {
        return thread != null && thread.getState() == Thread.State.BLOCKED;
    }
}
