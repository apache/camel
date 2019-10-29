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
package org.apache.camel.component.nitrite;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.FileUtil;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.NitriteBuilder;
import org.dizitart.no2.PersistentCollection;

/**
 * Represents the component that manages {@link NitriteEndpoint}.
 */
@Component("nitrite")
public class NitriteComponent extends DefaultComponent {

    private Map<String, Nitrite> databaseCache = new ConcurrentHashMap<>();
    private Map<CollectionCacheKey, PersistentCollection> collectionCache = new ConcurrentHashMap<>();

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NitriteEndpoint endpoint = new NitriteEndpoint(uri, this);
        setProperties(endpoint, parameters);
        if (endpoint.getCollection() != null && endpoint.getRepositoryClass() != null) {
            throw new IllegalArgumentException("Options collection and repositoryClass cannot be used together");
        }
        if (endpoint.getCollection() == null && endpoint.getRepositoryClass() == null) {
            throw new IllegalArgumentException("Either collection or repositoryClass option is required");
        }

        String normalizedPath = FileUtil.compactPath(remaining);
        endpoint.setDatabase(normalizedPath);
        Nitrite nitriteDatabase = databaseCache.computeIfAbsent(normalizedPath, path -> {
            NitriteBuilder builder = Nitrite.builder().compressed().filePath(path);
            if (endpoint.getUsername() == null && endpoint.getPassword() == null) {
                return builder.openOrCreate();
            } else {
                return builder.openOrCreate(endpoint.getUsername(), endpoint.getPassword());
            }
        });
        endpoint.setNitriteDatabase(nitriteDatabase);

        PersistentCollection nitriteCollection = collectionCache.computeIfAbsent(new CollectionCacheKey(endpoint), key -> {
            if (key.collection != null) {
                return key.database.getCollection(key.collection);
            } else {
                if (key.repositoryName != null) {
                    return key.database.getRepository(key.repositoryName, key.repositoryClass);
                } else if (key.repositoryClass != null) {
                    return key.database.getRepository(key.repositoryClass);
                } else {
                    throw new IllegalArgumentException("Required one of option: collection or repositoryClass");
                }
            }
        });
        endpoint.setNitriteCollection(nitriteCollection);

        return endpoint;
    }

    protected void closeDb(String path) {
        String normalized = FileUtil.compactPath(path);
        Nitrite db = databaseCache.get(normalized);
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        for (String path: databaseCache.keySet()) {
            closeDb(path);
        }
        databaseCache.clear();
        collectionCache.clear();
    }

    private static class CollectionCacheKey {
        Nitrite database;
        String collection;
        String repositoryName;
        Class<?> repositoryClass;

        CollectionCacheKey(NitriteEndpoint endpoint) {
            database = endpoint.getNitriteDatabase();
            collection = endpoint.getCollection();
            repositoryName = endpoint.getRepositoryName();
            repositoryClass = endpoint.getRepositoryClass();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CollectionCacheKey that = (CollectionCacheKey) o;
            return database.equals(that.database)
                    && Objects.equals(collection, that.collection)
                    && Objects.equals(repositoryName, that.repositoryName)
                    && Objects.equals(repositoryClass, that.repositoryClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(database, collection, repositoryName, repositoryClass);
        }
    }
}
