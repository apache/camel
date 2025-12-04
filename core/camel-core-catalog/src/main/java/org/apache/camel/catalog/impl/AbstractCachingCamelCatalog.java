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

package org.apache.camel.catalog.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public abstract class AbstractCachingCamelCatalog extends AbstractCamelCatalog {
    private final Map<String, Object> cache = new ConcurrentHashMap<>();
    private boolean caching;

    /**
     * Creates the {@link AbstractCachingCamelCatalog} without caching enabled.
     */
    protected AbstractCachingCamelCatalog() {
        this(false);
    }

    /**
     * Creates the {@link AbstractCachingCamelCatalog}
     *
     * @param caching whether to use cache
     */
    protected AbstractCachingCamelCatalog(boolean caching) {
        this.caching = caching;
    }

    /**
     * To turn caching on or off
     */
    public boolean isCaching() {
        return caching;
    }

    /**
     * To turn caching on or off
     */
    public void setCaching(boolean caching) {
        this.caching = caching;

        if (!this.caching) {
            clearCache();
        }
    }

    protected Map<String, Object> getCache() {
        return this.cache;
    }

    protected void clearCache() {
        cache.clear();
    }

    protected <T> T cache(String key, String name, Function<String, T> loader) {
        return doGetCache(key, name, loader);
    }

    protected <T> T cache(String name, Function<String, T> loader) {
        return doGetCache(name, name, loader);
    }

    @SuppressWarnings("unchecked")
    protected <T> T cache(String name, Supplier<T> loader) {
        if (caching) {
            T t = (T) cache.get(name);
            if (t == null) {
                t = loader.get();
                if (t != null) {
                    cache.put(name, t);
                }
            }
            return t;
        } else {
            return loader.get();
        }
    }

    @SuppressWarnings("unchecked")
    protected <T> T doGetCache(String key, String name, Function<String, T> loader) {
        if (caching) {
            T t = (T) cache.get(key);
            if (t == null) {
                t = loader.apply(name);
                if (t != null) {
                    cache.put(key, t);
                }
            }
            return t;
        } else {
            return loader.apply(name);
        }
    }
}
