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

import java.io.Serial;
import java.io.Serializable;

/**
 * A value wrapper that holds the actual value and an expiration timestamp. Used by KeyValueRepository implementations
 * that need client-side TTL management (e.g., Ehcache, JCache) because the underlying store does not support per-entry
 * TTL.
 *
 * @since 4.23
 */
public final class KeyValueTtlValue implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Object value;
    private final long expiresAt;

    public KeyValueTtlValue(Object value, long expiresAt) {
        this.value = value;
        this.expiresAt = expiresAt;
    }

    public Object value() {
        return value;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
}
