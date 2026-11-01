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
package org.apache.camel.processor.resume;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.resume.cache.ResumeCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link TransientResumeStrategy#createSimpleCache()}.
 */
class TransientResumeStrategyCacheTest {

    @Test
    void testForEachKeepsEntryWhenActionReturnsFalse() {
        ResumeCache<Object> cache = TransientResumeStrategy.createSimpleCache();
        cache.add("key1", "value1");

        // action returns false → keep entry
        cache.forEach((k, v) -> false);

        assertNotNull(cache.get("key1"), "Entry should be retained when action returns false");
        assertEquals("value1", cache.get("key1"));
    }

    @Test
    void testForEachRemovesEntryWhenActionReturnsTrue() {
        ResumeCache<Object> cache = TransientResumeStrategy.createSimpleCache();
        cache.add("key1", "value1");

        // action returns true → invalidate/remove entry
        cache.forEach((k, v) -> true);

        assertNull(cache.get("key1"), "Entry should be removed when action returns true");
    }

    @Test
    void testForEachWithMultipleEntriesNoException() {
        ResumeCache<Object> cache = TransientResumeStrategy.createSimpleCache();
        cache.add("key1", "value1");
        cache.add("key2", "value2");
        cache.add("key3", "value3");

        // This must not throw ConcurrentModificationException
        List<Object> visited = new ArrayList<>();
        cache.forEach((k, v) -> {
            visited.add(k);
            return false; // keep all entries
        });

        assertEquals(3, visited.size(), "All entries should be visited");
        assertNotNull(cache.get("key1"), "key1 should be retained");
        assertNotNull(cache.get("key2"), "key2 should be retained");
        assertNotNull(cache.get("key3"), "key3 should be retained");
    }

    @Test
    void testForEachWithMultipleEntriesRemovesAllOnTrue() {
        ResumeCache<Object> cache = TransientResumeStrategy.createSimpleCache();
        cache.add("key1", "value1");
        cache.add("key2", "value2");
        cache.add("key3", "value3");

        // action returns true → all entries invalidated; must not throw ConcurrentModificationException
        cache.forEach((k, v) -> true);

        assertNull(cache.get("key1"), "key1 should be removed");
        assertNull(cache.get("key2"), "key2 should be removed");
        assertNull(cache.get("key3"), "key3 should be removed");
    }

    @Test
    void testForEachSelectiveRemoval() {
        ResumeCache<Object> cache = TransientResumeStrategy.createSimpleCache();
        cache.add("keep", "v1");
        cache.add("remove", "v2");

        // invalidate only the "remove" key
        cache.forEach((k, v) -> "remove".equals(k));

        assertNotNull(cache.get("keep"), "keep entry should be retained");
        assertNull(cache.get("remove"), "remove entry should be deleted");
    }
}
