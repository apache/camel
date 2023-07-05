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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.resume.ResumeStrategyConfigurationBuilder;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.spi.annotations.JdkService;

/**
 * A resume strategy that keeps all the resume strategy information in memory. This is hardly useful for production
 * level implementations, but can be useful for testing the resume strategies
 */
@JdkService("transient-resume-strategy")
public class TransientResumeStrategy implements ResumeStrategy {
    private final ResumeAdapter resumeAdapter;

    public TransientResumeStrategy(ResumeAdapter resumeAdapter) {
        this.resumeAdapter = resumeAdapter;
    }

    @Override
    public void setAdapter(ResumeAdapter adapter) {
        // this is NO-OP
    }

    @Override
    public ResumeAdapter getAdapter() {
        return resumeAdapter;
    }

    @Override
    public <T extends ResumeAdapter> T getAdapter(Class<T> clazz) {
        return ResumeStrategy.super.getAdapter(clazz);
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset) {
        // this is NO-OP
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset, UpdateCallBack updateCallBack) throws Exception {
        // this is NO-OP
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset, UpdateCallBack updateCallBack) throws Exception {
        // this is NO-OP
    }

    @Override
    public void setResumeStrategyConfiguration(ResumeStrategyConfiguration resumeStrategyConfiguration) {
        // This is NO-OP
    }

    @Override
    public ResumeStrategyConfiguration getResumeStrategyConfiguration() {
        return configurationBuilder().build();
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset) {
        // this is NO-OP
    }

    @Override
    public void start() {
        // this is NO-OP
    }

    @Override
    public void stop() {
        // this is NO-OP
    }

    public static ResumeStrategyConfigurationBuilder<ResumeStrategyConfigurationBuilder<?, ?>, ResumeStrategyConfiguration> configurationBuilder() {
        return new ResumeStrategyConfigurationBuilder<>() {
            @Override
            public ResumeStrategyConfigurationBuilder<?, ?> withCacheFillPolicy(Cacheable.FillPolicy cacheFillPolicy) {
                return this;
            }

            @Override
            public ResumeStrategyConfigurationBuilder<?, ?> withResumeCache(ResumeCache<?> resumeCache) {
                return this;
            }

            @Override
            public ResumeStrategyConfiguration build() {
                return new ResumeStrategyConfiguration() {

                    @Override
                    public ResumeCache<?> getResumeCache() {
                        return createSimpleCache();
                    }

                    @Override
                    public String resumeStrategyService() {
                        return "transient-resume-strategy";
                    }
                };
            }
        };
    }

    public static ResumeCache<Object> createSimpleCache() {
        return new ResumeCache<>() {
            private final Map<Object, Object> cache = new HashMap<>();

            @Override
            public Object computeIfAbsent(Object key, Function<? super Object, ? super Object> mapping) {
                return cache.computeIfAbsent(key, mapping);
            }

            @Override
            public Object computeIfPresent(Object key, BiFunction<? super Object, ? super Object, ? super Object> remapping) {
                return cache.computeIfPresent(key, remapping);
            }

            @Override
            public boolean contains(Object key, Object entry) {
                return Objects.equals(cache.get(key), entry);
            }

            @Override
            public void add(Object key, Object offsetValue) {
                cache.put(key, offsetValue);
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
            public <T> T get(Object key, Class<T> clazz) {
                final Object o = cache.get(key);

                return clazz.cast(o);
            }

            @Override
            public Object get(Object key) {
                return cache.get(key);
            }

            @Override
            public void forEach(BiFunction<? super Object, ? super Object, Boolean> action) {
                for (Map.Entry<Object, Object> e : cache.entrySet()) {
                    if (!action.apply(e.getKey(), e.getValue())) {
                        cache.remove(e.getKey());
                    }
                }
            }
        };
    }
}
