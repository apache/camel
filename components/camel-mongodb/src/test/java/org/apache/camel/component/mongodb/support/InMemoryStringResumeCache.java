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
package org.apache.camel.component.mongodb.support;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.resume.cache.ResumeCache;

public class InMemoryStringResumeCache implements ResumeCache<String> {
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
