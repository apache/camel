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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

/**
 * A {@link KeyValueRepository} wrapper that isolates entries by prepending a namespace prefix to all keys.
 * <p/>
 * This adapter ensures that multiple components, patterns (e.g. Cache EIP, State Store, Aggregator, Idempotent
 * Consumer), or named stores sharing a single underlying {@link KeyValueRepository} operate in isolated key partitions
 * without key collisions or unintended cross-namespace mutations during operations such as {@link #clear()} or
 * {@link #keys()}.
 *
 * @since 4.24
 */
@Metadata(label = "bean",
          description = "A KeyValueRepository that partitions keys using a namespace prefix.",
          annotations = { "interfaceName=org.apache.camel.spi.KeyValueRepository" })
@Configurer(metadataOnly = true)
public class NamespacedKeyValueRepository extends ServiceSupport implements KeyValueRepository {

    private KeyValueRepository delegate;
    private String prefix;

    /**
     * Creates an uninitialized namespaced repository adapter. The delegate repository and namespace should be set
     * before starting.
     */
    public NamespacedKeyValueRepository() {
    }

    /**
     * Creates a new namespaced wrapper around the given repository.
     *
     * @param delegate  the underlying key-value repository; must not be {@code null}
     * @param namespace the namespace or prefix to isolate keys; must not be {@code null}
     */
    public NamespacedKeyValueRepository(KeyValueRepository delegate, String namespace) {
        this.delegate = ObjectHelper.notNull(delegate, "delegate");
        setNamespace(namespace);
    }

    /**
     * Creates a new {@link NamespacedKeyValueRepository} wrapping the given repository, or returns the repository if it
     * is already wrapped with the identical prefix.
     *
     * @param  repository the key-value repository to wrap
     * @param  namespace  the namespace prefix to apply
     * @return            the namespaced repository wrapper
     */
    public static KeyValueRepository namespaced(KeyValueRepository repository, String namespace) {
        if (repository == null) {
            return null;
        }
        ObjectHelper.notNull(namespace, "namespace");
        String p = namespace.endsWith(":") ? namespace : namespace + ":";
        if (repository instanceof NamespacedKeyValueRepository nkvr) {
            if (nkvr.getNamespace().equals(p)) {
                return repository;
            }
        }
        return new NamespacedKeyValueRepository(repository, p);
    }

    /**
     * Returns the underlying delegated {@link KeyValueRepository}.
     */
    public KeyValueRepository getDelegate() {
        return delegate;
    }

    /**
     * Sets the underlying delegated {@link KeyValueRepository}.
     */
    public void setDelegate(KeyValueRepository delegate) {
        this.delegate = delegate;
    }

    /**
     * Returns the namespace applied to all keys.
     */
    public String getNamespace() {
        return prefix;
    }

    /**
     * Sets the namespace prefix to isolate keys. If the namespace does not end with ':', a trailing colon is added.
     */
    public void setNamespace(String namespace) {
        ObjectHelper.notNull(namespace, "namespace");
        this.prefix = namespace.endsWith(":") ? namespace : namespace + ":";
    }

    @Override
    public Object get(String key) {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(key, "key");
        return delegate.get(prefix + key);
    }

    @Override
    public Object put(String key, Object value) {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(key, "key");
        return delegate.put(prefix + key, value);
    }

    @Override
    public Object put(String key, Object value, Duration ttl) {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(key, "key");
        return delegate.put(prefix + key, value, ttl);
    }

    @Override
    public Object delete(String key) {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(key, "key");
        return delegate.delete(prefix + key);
    }

    @Override
    public boolean contains(String key) {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(key, "key");
        return delegate.contains(prefix + key);
    }

    @Override
    public Set<String> keys() {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        Set<String> allKeys = delegate.keys();
        if (allKeys == null || allKeys.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> filtered = new LinkedHashSet<>();
        for (String k : allKeys) {
            if (k != null && k.startsWith(prefix)) {
                filtered.add(k.substring(prefix.length()));
            }
        }
        return Collections.unmodifiableSet(filtered);
    }

    /**
     * Clears all entries within this repository's namespace by deleting matching keys individually from the underlying
     * delegate repository. Note that on shared backends this operation is non-atomic and executes in O(n) relative to
     * the total number of keys in the delegate.
     */
    @Override
    public void clear() {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        Set<String> allKeys = delegate.keys();
        if (allKeys != null && !allKeys.isEmpty()) {
            List<String> toDelete = new ArrayList<>();
            for (String k : allKeys) {
                if (k != null && k.startsWith(prefix)) {
                    toDelete.add(k);
                }
            }
            for (String k : toDelete) {
                delegate.delete(k);
            }
        }
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(key, "key");
        return delegate.putIfAbsent(prefix + key, value);
    }

    @Override
    public Object putIfAbsent(String key, Object value, Duration ttl) {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(key, "key");
        return delegate.putIfAbsent(prefix + key, value, ttl);
    }

    @Override
    public boolean replace(String key, Object expectedOldValue, Object newValue, Duration ttl) {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(key, "key");
        return delegate.replace(prefix + key, expectedOldValue, newValue, ttl);
    }

    @Override
    public boolean delete(String key, Object expectedValue) {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ObjectHelper.notNull(key, "key");
        return delegate.delete(prefix + key, expectedValue);
    }

    @Override
    public int size() {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        Set<String> allKeys = delegate.keys();
        if (allKeys == null || allKeys.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String k : allKeys) {
            if (k != null && k.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(delegate, "delegate");
        ObjectHelper.notNull(prefix, "prefix");
        ServiceHelper.startService(delegate);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopService(delegate);
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownService(delegate);
    }

    @Override
    public String toString() {
        return "NamespacedKeyValueRepository[" + prefix + " -> " + delegate + "]";
    }
}
