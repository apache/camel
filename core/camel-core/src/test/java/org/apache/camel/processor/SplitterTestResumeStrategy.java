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
package org.apache.camel.processor;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.resume.cache.ResumeCache;

/**
 * A simple in-memory {@link ResumeStrategy} for Splitter watermark tests. Wraps a {@link Map} that tests can
 * pre-populate and assert against. The cache is a live view backed by the store, so changes to the store are
 * immediately visible.
 */
class SplitterTestResumeStrategy implements ResumeStrategy {

    private final Map<String, String> store;
    private final ResumeStrategyConfiguration config;

    SplitterTestResumeStrategy(Map<String, String> store) {
        this.store = store;
        ResumeCache<Object> cache = new LiveStoreCache(store);
        ResumeStrategyConfiguration cfg = new ResumeStrategyConfiguration() {
            @Override
            public String resumeStrategyService() {
                return "test";
            }
        };
        cfg.setResumeCache(cache);
        this.config = cfg;
    }

    @Override
    public void loadCache() {
        // no-op: cache is a live view of the store
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offsetValue) {
        store.put(offsetKey.getValue().toString(), offsetValue.getValue().toString());
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset) {
        // no-op
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset, UpdateCallBack updateCallBack) {
        // no-op
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset, UpdateCallBack updateCallBack)
            throws Exception {
        updateLastOffset(offsetKey, offset);
    }

    @Override
    public void setAdapter(ResumeAdapter adapter) {
        // no-op
    }

    @Override
    public ResumeAdapter getAdapter() {
        return null;
    }

    @Override
    public void setResumeStrategyConfiguration(ResumeStrategyConfiguration resumeStrategyConfiguration) {
        // no-op
    }

    @Override
    public ResumeStrategyConfiguration getResumeStrategyConfiguration() {
        return config;
    }

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void stop() {
        // no-op
    }

    /**
     * A {@link ResumeCache} that directly delegates to the backing store map, providing a live view.
     */
    private static class LiveStoreCache implements ResumeCache<Object> {
        private final Map<String, String> store;

        LiveStoreCache(Map<String, String> store) {
            this.store = store;
        }

        @Override
        public Object get(Object key) {
            return store.get(String.valueOf(key));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Object key, Class<T> clazz) {
            Object value = get(key);
            return value != null ? clazz.cast(value) : null;
        }

        @Override
        public void add(Object key, Object offsetValue) {
            store.put(String.valueOf(key), String.valueOf(offsetValue));
        }

        @Override
        public boolean contains(Object key, Object entry) {
            Object value = get(key);
            return value != null && value.equals(entry);
        }

        @Override
        public Object computeIfAbsent(Object key, Function<? super Object, ? super Object> mapping) {
            return store.computeIfAbsent(String.valueOf(key), k -> String.valueOf(mapping.apply(k)));
        }

        @Override
        public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ? super Object> remapping) {
            return store.computeIfPresent(String.valueOf(key),
                    (k, v) -> String.valueOf(remapping.apply(k, v)));
        }

        @Override
        public boolean isFull() {
            return false;
        }

        @Override
        public long capacity() {
            return Integer.MAX_VALUE;
        }

        @Override
        public void forEach(BiFunction<? super Object, ? super Object, Boolean> action) {
            for (Map.Entry<String, String> e : store.entrySet()) {
                if (!action.apply(e.getKey(), e.getValue())) {
                    break;
                }
            }
        }
    }
}
