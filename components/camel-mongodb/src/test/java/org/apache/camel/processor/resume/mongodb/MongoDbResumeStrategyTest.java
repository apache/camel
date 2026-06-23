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
package org.apache.camel.processor.resume.mongodb;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.component.mongodb.MongoDbResumable;
import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.support.processor.state.MemoryStateRepository;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MongoDbResumeStrategyTest {

    @Test
    public void shouldStoreAndLoadOffsetsUsingStateRepository() throws Exception {
        MemoryStateRepository repository = new MemoryStateRepository();

        MongoDbResumeStrategyConfiguration configuration = MongoDbResumeStrategyConfigurationBuilder.newBuilder()
                .withStateRepository(repository)
                .build();
        MongoDbResumeStrategy strategy = new MongoDbResumeStrategy();
        strategy.setResumeStrategyConfiguration(configuration);

        String key = "routeA/testCollection";
        BsonDocument token = BsonDocument.parse("{\"_data\":\"token-1\"}");

        strategy.updateLastOffset(MongoDbResumable.of(key, token));

        assertEquals(token.toJson(), repository.getState(key));
        assertEquals(token.toJson(), strategy.getLastOffset(key));
    }

    @Test
    public void shouldStoreAndLoadOffsetsUsingResumeCache() throws Exception {
        InMemoryStringResumeCache resumeCache = new InMemoryStringResumeCache();

        MongoDbResumeStrategyConfiguration configuration = MongoDbResumeStrategyConfigurationBuilder.newBuilder()
                .withResumeCache(resumeCache)
                .withCacheFillPolicy(Cacheable.FillPolicy.MAXIMIZING)
                .build();

        MongoDbResumeStrategy strategy = new MongoDbResumeStrategy();
        strategy.setResumeStrategyConfiguration(configuration);

        String key = "routeB/testCollection";
        BsonDocument token = BsonDocument.parse("{\"_data\":\"token-2\"}");

        strategy.updateLastOffset(MongoDbResumable.of(key, token));

        assertEquals(token.toJson(), resumeCache.get(key, String.class));
        assertEquals(token.toJson(), strategy.getLastOffset(key));
        assertNull(strategy.getLastOffset("missing"));
    }

    private static final class InMemoryStringResumeCache implements ResumeCache<String> {
        private final Map<String, Object> data = new HashMap<>();

        @Override
        public Object computeIfAbsent(String key, Function<? super String, ? super Object> mapping) {
            return data.computeIfAbsent(key, mapping::apply);
        }

        @Override
        public Object computeIfPresent(String key, BiFunction<? super String, ? super Object, ? super Object> remapping) {
            return data.computeIfPresent(key, remapping::apply);
        }

        @Override
        public boolean contains(String key, Object entry) {
            return Objects.equals(data.get(key), entry);
        }

        @Override
        public void add(String key, Object offsetValue) {
            data.put(key, offsetValue);
        }

        @Override
        public boolean isFull() {
            return false;
        }

        @Override
        public long capacity() {
            return Long.MAX_VALUE;
        }

        @Override
        public <T> T get(String key, Class<T> clazz) {
            return clazz.cast(data.get(key));
        }

        @Override
        public Object get(String key) {
            return data.get(key);
        }

        @Override
        public void forEach(BiFunction<? super String, ? super Object, Boolean> action) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!action.apply(entry.getKey(), entry.getValue())) {
                    break;
                }
            }
        }
    }
}
