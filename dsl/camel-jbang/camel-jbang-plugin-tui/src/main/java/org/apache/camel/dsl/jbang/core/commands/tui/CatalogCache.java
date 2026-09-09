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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.common.CatalogLoader;

/**
 * Lazily loads and caches a {@link CamelCatalog} per Camel version. A version whose catalog fails to load is remembered
 * so the (slow) download is not retried on every render.
 */
final class CatalogCache {

    private final Map<String, CamelCatalog> cache = new ConcurrentHashMap<>();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();

    /**
     * Returns the catalog matching the Camel version of the given integration, or null if unknown or unavailable.
     */
    CamelCatalog get(IntegrationInfo info) {
        return info != null ? get(info.camelVersion) : null;
    }

    /**
     * Returns the catalog for the given Camel version, or null if the version is unknown or the catalog cannot be
     * loaded.
     */
    CamelCatalog get(String version) {
        if (version == null) {
            return null;
        }
        CamelCatalog cached = cache.get(version);
        if (cached != null) {
            return cached;
        }
        if (failed.contains(version)) {
            return null;
        }
        try {
            cached = CatalogLoader.loadCatalog(null, version, true);
            if (cached != null) {
                cache.put(version, cached);
            } else {
                failed.add(version);
            }
            return cached;
        } catch (Exception e) {
            failed.add(version);
            return null;
        }
    }
}
