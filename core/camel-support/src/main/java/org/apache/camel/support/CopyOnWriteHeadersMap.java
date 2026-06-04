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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.spi.HeadersMapFactory;

/**
 * A lazy copy-on-write wrapper for message headers that defers cloning until the first mutation.
 * <p>
 * This wrapper delegates all read operations (get, containsKey, size, etc.) directly to the shared headers map without
 * copying. Write operations (put, remove, clear, etc.) trigger a one-time clone of the shared map into a private copy,
 * then perform the mutation on the private copy.
 * <p>
 * After the copy-on-write is triggered, all subsequent operations (reads and writes) operate on the private copy.
 * <p>
 * This class is not thread-safe. It follows the same threading model as {@link DefaultMessage} — an Exchange is
 * typically processed by a single thread at a time.
 */
final class CopyOnWriteHeadersMap implements Map<String, Object> {

    private final HeadersMapFactory factory;
    private Map<String, Object> delegate;
    private boolean shared;

    CopyOnWriteHeadersMap(Map<String, Object> sharedHeaders, HeadersMapFactory factory) {
        this.delegate = sharedHeaders;
        this.factory = factory;
        this.shared = true;
    }

    /**
     * Ensures the headers map is writable by triggering copy-on-write if necessary.
     */
    private void ensureWritable() {
        if (shared) {
            delegate = factory.newMap(delegate);
            shared = false;
        }
    }

    Map<String, Object> getUnderlying() {
        return delegate;
    }

    // ========== Read-only operations (no COW trigger) ==========

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return delegate.get(key);
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        return delegate.getOrDefault(key, defaultValue);
    }

    @Override
    public void forEach(BiConsumer<? super String, ? super Object> action) {
        delegate.forEach(action);
    }

    @Override
    public Set<String> keySet() {
        return new CopyOnWriteKeySet();
    }

    @Override
    public Collection<Object> values() {
        return new CopyOnWriteValues();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return new CopyOnWriteEntrySet();
    }

    // ========== Write operations (trigger COW first) ==========

    @Override
    public Object put(String key, Object value) {
        ensureWritable();
        return delegate.put(key, value);
    }

    @Override
    public Object remove(Object key) {
        ensureWritable();
        return delegate.remove(key);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {
        if (m.isEmpty()) {
            return;
        }
        ensureWritable();
        delegate.putAll(m);
    }

    @Override
    public void clear() {
        if (delegate.isEmpty()) {
            return;
        }
        ensureWritable();
        delegate.clear();
    }

    @Override
    public Object putIfAbsent(String key, Object value) {
        ensureWritable();
        return delegate.putIfAbsent(key, value);
    }

    @Override
    public boolean remove(Object key, Object value) {
        ensureWritable();
        return delegate.remove(key, value);
    }

    @Override
    public boolean replace(String key, Object oldValue, Object newValue) {
        ensureWritable();
        return delegate.replace(key, oldValue, newValue);
    }

    @Override
    public Object replace(String key, Object value) {
        ensureWritable();
        return delegate.replace(key, value);
    }

    @Override
    public Object computeIfAbsent(String key, Function<? super String, ?> mappingFunction) {
        ensureWritable();
        return delegate.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public Object computeIfPresent(
            String key, BiFunction<? super String, ? super Object, ?> remappingFunction) {
        ensureWritable();
        return delegate.computeIfPresent(key, remappingFunction);
    }

    @Override
    public Object compute(
            String key, BiFunction<? super String, ? super Object, ?> remappingFunction) {
        ensureWritable();
        return delegate.compute(key, remappingFunction);
    }

    @Override
    public Object merge(
            String key, Object value, BiFunction<? super Object, ? super Object, ?> remappingFunction) {
        ensureWritable();
        return delegate.merge(key, value, remappingFunction);
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super Object, ?> function) {
        ensureWritable();
        delegate.replaceAll(function);
    }

    // ========== Object methods ==========

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    // ========== COW-aware collection view wrappers ==========

    /**
     * A COW-aware Set wrapper for keySet() that triggers copy-on-write for mutating operations. The wrapper does NOT
     * cache the view at construction time; instead, it accesses the current delegate's keySet() on each operation. This
     * ensures that after ensureWritable() clones the map, subsequent operations work on the new private copy, not the
     * old shared map.
     */
    private class CopyOnWriteKeySet implements Set<String> {

        // Read operations - no COW trigger
        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.containsKey(o);
        }

        @Override
        public Object[] toArray() {
            return delegate.keySet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.keySet().toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.keySet().containsAll(c);
        }

        // Write operations - trigger COW first
        @Override
        public boolean add(String e) {
            ensureWritable();
            return delegate.keySet().add(e);
        }

        @Override
        public boolean remove(Object o) {
            ensureWritable();
            return delegate.keySet().remove(o);
        }

        @Override
        public boolean addAll(Collection<? extends String> c) {
            if (c.isEmpty()) {
                return false;
            }
            ensureWritable();
            return delegate.keySet().addAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            if (delegate.isEmpty()) {
                return false;
            }
            ensureWritable();
            return delegate.keySet().retainAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            if (c.isEmpty() || delegate.isEmpty()) {
                return false;
            }
            ensureWritable();
            return delegate.keySet().removeAll(c);
        }

        @Override
        public void clear() {
            if (delegate.isEmpty()) {
                return;
            }
            ensureWritable();
            delegate.clear();
        }

        @Override
        public Iterator<String> iterator() {
            final Iterator<String> iter = delegate.keySet().iterator();
            return new Iterator<String>() {
                private String lastReturned;

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public String next() {
                    lastReturned = iter.next();
                    return lastReturned;
                }

                @Override
                public void remove() {
                    ensureWritable();
                    delegate.remove(lastReturned);
                }
            };
        }

        @Override
        public boolean equals(Object o) {
            return delegate.keySet().equals(o);
        }

        @Override
        public int hashCode() {
            return delegate.keySet().hashCode();
        }

        @Override
        public String toString() {
            return delegate.keySet().toString();
        }
    }

    /**
     * A COW-aware Collection wrapper for values() that triggers copy-on-write for mutating operations.
     */
    private class CopyOnWriteValues implements Collection<Object> {

        // Read operations - no COW trigger
        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.containsValue(o);
        }

        @Override
        public Object[] toArray() {
            return delegate.values().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.values().toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.values().containsAll(c);
        }

        // Write operations - trigger COW first
        @Override
        public boolean add(Object e) {
            ensureWritable();
            return delegate.values().add(e);
        }

        @Override
        public boolean remove(Object o) {
            ensureWritable();
            return delegate.values().remove(o);
        }

        @Override
        public boolean addAll(Collection<?> c) {
            if (c.isEmpty()) {
                return false;
            }
            ensureWritable();
            return delegate.values().addAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            if (delegate.isEmpty()) {
                return false;
            }
            ensureWritable();
            return delegate.values().retainAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            if (c.isEmpty() || delegate.isEmpty()) {
                return false;
            }
            ensureWritable();
            return delegate.values().removeAll(c);
        }

        @Override
        public void clear() {
            if (delegate.isEmpty()) {
                return;
            }
            ensureWritable();
            delegate.clear();
        }

        @Override
        public Iterator<Object> iterator() {
            final Iterator<Object> iter = delegate.values().iterator();
            return new Iterator<Object>() {
                private Object lastReturned;

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Object next() {
                    lastReturned = iter.next();
                    return lastReturned;
                }

                @Override
                public void remove() {
                    ensureWritable();
                    delegate.values().remove(lastReturned);
                }
            };
        }

        @Override
        public boolean equals(Object o) {
            return delegate.values().equals(o);
        }

        @Override
        public int hashCode() {
            return delegate.values().hashCode();
        }

        @Override
        public String toString() {
            return delegate.values().toString();
        }
    }

    /**
     * A COW-aware Set wrapper for entrySet() that triggers copy-on-write for mutating operations.
     */
    private class CopyOnWriteEntrySet implements Set<Entry<String, Object>> {

        // Read operations - no COW trigger
        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return delegate.entrySet().contains(o);
        }

        @Override
        public Object[] toArray() {
            return delegate.entrySet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return delegate.entrySet().toArray(a);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.entrySet().containsAll(c);
        }

        // Write operations - trigger COW first
        @Override
        public boolean add(Entry<String, Object> e) {
            ensureWritable();
            return delegate.entrySet().add(e);
        }

        @Override
        public boolean remove(Object o) {
            ensureWritable();
            return delegate.entrySet().remove(o);
        }

        @Override
        public boolean addAll(Collection<? extends Entry<String, Object>> c) {
            if (c.isEmpty()) {
                return false;
            }
            ensureWritable();
            return delegate.entrySet().addAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            if (delegate.isEmpty()) {
                return false;
            }
            ensureWritable();
            return delegate.entrySet().retainAll(c);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            if (c.isEmpty() || delegate.isEmpty()) {
                return false;
            }
            ensureWritable();
            return delegate.entrySet().removeAll(c);
        }

        @Override
        public void clear() {
            if (delegate.isEmpty()) {
                return;
            }
            ensureWritable();
            delegate.clear();
        }

        @Override
        public Iterator<Entry<String, Object>> iterator() {
            final Iterator<Entry<String, Object>> iter = delegate.entrySet().iterator();
            return new Iterator<Entry<String, Object>>() {
                private Entry<String, Object> lastReturned;

                @Override
                public boolean hasNext() {
                    return iter.hasNext();
                }

                @Override
                public Entry<String, Object> next() {
                    lastReturned = iter.next();
                    return lastReturned;
                }

                @Override
                public void remove() {
                    ensureWritable();
                    delegate.remove(lastReturned.getKey());
                }
            };
        }

        @Override
        public boolean equals(Object o) {
            return delegate.entrySet().equals(o);
        }

        @Override
        public int hashCode() {
            return delegate.entrySet().hashCode();
        }

        @Override
        public String toString() {
            return delegate.entrySet().toString();
        }
    }

}
