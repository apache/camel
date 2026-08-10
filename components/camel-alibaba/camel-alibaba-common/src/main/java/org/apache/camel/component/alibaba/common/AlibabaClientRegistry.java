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
package org.apache.camel.component.alibaba.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Simple client registry for reusing Alibaba Cloud SDK clients within a Camel context.
 */
public final class AlibabaClientRegistry {

    private final Map<String, Object> clients = new ConcurrentHashMap<>();

    public <T> T getOrCreate(String key, Supplier<T> supplier) {
        @SuppressWarnings("unchecked")
        T client = (T) clients.get(key);
        if (client != null) {
            return client;
        }
        return clients.computeIfAbsent(key, k -> supplier.get());
    }

    public void clear() {
        clients.clear();
    }
}
