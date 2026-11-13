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
package org.apache.camel.support;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.camel.TimeoutMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A listener of a DefaultTimeoutMap throws an Error (not a VirtualMachineError) for an evicted entry: the other entries
 * are still evicted, and the map keeps purging.
 */
class DefaultTimeoutMapListenerThrowsErrorTest {

    private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

    @AfterEach
    void shutdown() {
        executor.shutdownNow();
    }

    @Test
    void testPurgeGoesOnAfterAListenerThrowsAnError() {
        DefaultTimeoutMap<String, Integer> map = new DefaultTimeoutMap<>(executor, 10);
        List<String> evicted = new CopyOnWriteArrayList<>();
        map.addListener((type, key, value) -> {
            if (type == TimeoutMap.Listener.Type.Evict) {
                evicted.add(key);
                if ("A".equals(key)) {
                    throw new NoClassDefFoundError("Simulated missing class");
                }
            }
        });
        map.start();
        try {
            // A and B expire in the same purge: B is still evicted after the Error of A
            map.put("A", 1, 20);
            map.put("B", 2, 20);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertTrue(evicted.containsAll(List.of("A", "B"))));

            // and the map keeps purging: an entry added later is evicted too
            map.put("C", 3, 20);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertTrue(evicted.contains("C")));
            assertEquals(0, map.size());
        } finally {
            map.stop();
        }
    }
}
