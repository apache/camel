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
package org.apache.camel.spring.processor.idempotent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cache.support.NoOpCache;
import org.springframework.cache.support.SimpleValueWrapper;

public class MyCache extends NoOpCache {

    private final ConcurrentMap<Object, Object> cache = new ConcurrentHashMap<>();

    public MyCache(String name) {
        super(name);
    }

    @Override
    public void put(Object key, Object value) {
        cache.put(key, value);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        Object answer = cache.putIfAbsent(key, value);
        return answer != null ? new SimpleValueWrapper(answer) : null;
    }

    @Override
    public ValueWrapper get(Object key) {
        Object answer = cache.get(key);
        return answer != null ? new SimpleValueWrapper(answer) : null;
    }
}
