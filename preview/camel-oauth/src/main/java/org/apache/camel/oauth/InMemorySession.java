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
package org.apache.camel.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InMemorySession implements OAuthSession {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String sessionId;
    private final Map<String, Object> values = new HashMap<>();

    public InMemorySession(String sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public String getSessionId() {
        return sessionId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getValue(String key, Class<T> clazz) {
        return (Optional<T>) Optional.ofNullable(values.get(key));
    }

    @Override
    public <T> void putValue(String key, T value) {
        values.put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> removeValue(String key) {
        var maybeValue = getValue(key, Object.class);
        values.remove(key);
        return (Optional<T>) maybeValue;
    }
}
