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
package org.apache.camel.component.rest.postman.collection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.rest.postman.model.PostmanCollection;

/**
 * Caches parsed collections for the lifetime of the component.
 * <p>
 * Without this, every endpoint built against the same collection would re-read it, and for cloud sources that means one
 * API call per endpoint at startup.
 */
public final class PostmanCollectionCache {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * Returns the cached collection for the given source, loading it if absent or stale.
     *
     * @param  source the collection source, used as part of the cache key
     * @param  apiKey the API key in play, so that two endpoints using different credentials never share an entry
     * @param  ttl    how long an entry stays fresh in milliseconds, or a negative value to cache forever
     * @param  loader supplies the collection on a miss
     * @return        the collection
     */
    public PostmanCollection get(String source, String apiKey, long ttl, Supplier<PostmanCollection> loader) {
        String key = cacheKey(source, apiKey);
        long now = System.currentTimeMillis();

        Entry entry = entries.get(key);
        if (entry != null && !entry.isStale(now, ttl)) {
            return entry.collection();
        }
        // a concurrent miss may load twice, which is harmless and cheaper than holding a lock across the load
        PostmanCollection loaded = loader.get();
        entries.put(key, new Entry(loaded, now));
        return loaded;
    }

    public void clear() {
        entries.clear();
    }

    /**
     * Builds the cache key. The API key is hashed rather than stored, so that a heap dump or a debugger view of the
     * cache cannot reveal the credential.
     */
    private static String cacheKey(String source, String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return source + "|";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return source + "|" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeCamelException("SHA-256 is not available", e);
        }
    }

    private record Entry(PostmanCollection collection, long loadedAt) {

        boolean isStale(long now, long ttl) {
            return ttl >= 0 && now - loadedAt > ttl;
        }
    }
}
