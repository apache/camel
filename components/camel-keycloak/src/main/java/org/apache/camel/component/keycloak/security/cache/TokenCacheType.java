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

package org.apache.camel.component.keycloak.security.cache;

/**
 * Enumeration of supported token cache implementations.
 */
public enum TokenCacheType {
    /**
     * Simple in-memory cache using ConcurrentHashMap. This is the default cache type with no external dependencies.
     * Suitable for basic use cases and backward compatibility.
     */
    CONCURRENT_MAP,

    /**
     * High-performance cache using Caffeine library. Provides advanced features including size-based eviction,
     * statistics, and optimized concurrent access. Requires caffeine dependency. Recommended for production use with
     * high throughput.
     */
    CAFFEINE,

    /**
     * No caching - every token will be introspected on each request. Use this for testing or when caching is not
     * desired.
     */
    NONE
}
