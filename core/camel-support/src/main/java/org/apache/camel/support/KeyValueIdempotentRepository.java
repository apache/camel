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

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * An adapter that implements {@link IdempotentRepository} by delegating to a {@link KeyValueRepository}.
 * <p/>
 * This adapter uses {@link Boolean#TRUE} as the marker value stored in the underlying key-value repository. The key
 * itself is the message identifier used for duplicate detection.
 * <p/>
 * This allows any {@link KeyValueRepository} implementation (e.g. backed by Redis, Infinispan, JDBC, etc.) to be used
 * as an idempotent repository without implementing the {@link IdempotentRepository} interface directly.
 * <p/>
 * When created with the no-arg constructor, a {@link MemoryKeyValueRepository} is used by default.
 *
 * @since 4.23
 */
@Metadata(label = "bean",
          description = "An IdempotentRepository backed by a KeyValueRepository (defaults to in-memory).",
          annotations = { "interfaceName=org.apache.camel.spi.IdempotentRepository" })
@Configurer(metadataOnly = true)
public class KeyValueIdempotentRepository extends ServiceSupport implements IdempotentRepository {

    /**
     * Prefix used to namespace idempotent entries, preventing key collisions when this adapter shares the same
     * {@link KeyValueRepository} with other adapters (e.g. {@link KeyValueAggregationRepository}).
     */
    private static final String IDEMPOTENT_PREFIX = "idempotent:";

    private KeyValueRepository repository;

    /**
     * Creates an idempotent repository adapter that defaults to an in-memory {@link MemoryKeyValueRepository}. The
     * repository is created lazily when the service starts.
     * <p/>
     * To use a custom {@link KeyValueRepository}, call {@link #setRepository(KeyValueRepository)} before starting, or
     * use the {@link #KeyValueIdempotentRepository(KeyValueRepository)} constructor.
     */
    public KeyValueIdempotentRepository() {
    }

    /**
     * Creates an idempotent repository adapter backed by the given key-value repository.
     *
     * @param repository the underlying key-value repository; must not be {@code null}
     */
    public KeyValueIdempotentRepository(KeyValueRepository repository) {
        ObjectHelper.notNull(repository, "repository");
        this.repository = repository;
    }

    /**
     * Creates a new {@link KeyValueIdempotentRepository} wrapping the given {@link KeyValueRepository}.
     *
     * @param  repository the underlying key-value repository
     * @return            the adapter
     */
    public static KeyValueIdempotentRepository keyValueIdempotentRepository(KeyValueRepository repository) {
        return new KeyValueIdempotentRepository(repository);
    }

    @Override
    public boolean add(String key) {
        // putIfAbsent returns null if the key was successfully added (not already present)
        return repository.putIfAbsent(IDEMPOTENT_PREFIX + key, Boolean.TRUE, null) == null;
    }

    @Override
    public boolean contains(String key) {
        return repository.contains(IDEMPOTENT_PREFIX + key);
    }

    @Override
    public boolean remove(String key) {
        return repository.delete(IDEMPOTENT_PREFIX + key) != null;
    }

    @Override
    public boolean confirm(String key) {
        // noop -- the key was already added
        return true;
    }

    @Override
    public void clear() {
        // Only clear entries with our prefix to avoid affecting other adapters sharing the repository
        for (String key : repository.keys()) {
            if (key.startsWith(IDEMPOTENT_PREFIX)) {
                repository.delete(key);
            }
        }
    }

    /**
     * Returns the underlying {@link KeyValueRepository}.
     */
    public KeyValueRepository getRepository() {
        return repository;
    }

    /**
     * Sets the underlying {@link KeyValueRepository}.
     *
     * @param repository the key-value repository to use
     */
    public void setRepository(KeyValueRepository repository) {
        this.repository = repository;
    }

    @Override
    protected void doStart() throws Exception {
        if (repository == null) {
            repository = new MemoryKeyValueRepository();
        }
        ServiceHelper.startService(repository);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(repository);
    }
}
