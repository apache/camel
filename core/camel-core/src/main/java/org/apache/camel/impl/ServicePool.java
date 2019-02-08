/**
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
package org.apache.camel.impl;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

import org.apache.camel.Endpoint;
import org.apache.camel.IsSingleton;
import org.apache.camel.NonManagedService;
import org.apache.camel.Service;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.function.ThrowingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A service pool is like a connection pool but can pool any kind of objects.
 * <p/>
 * Notice the capacity is <b>per key</b> which means that each key can contain at most
 * (the capacity) services. The pool can contain an unbounded number of keys.
 * <p/>
 * By default the capacity is set to 100.
 */
public class ServicePool<S extends Service> extends ServiceSupport implements NonManagedService {

    static final Logger LOG = LoggerFactory.getLogger(ServicePool.class);

    final ThrowingFunction<Endpoint, S, Exception> producer;
    final Function<S, Endpoint> getEndpoint;
    final ConcurrentHashMap<Endpoint, Pool<S>> pool = new ConcurrentHashMap<>();
    int capacity;
    Map<Key<S>, S> cache;

    interface Pool<S> {
        S acquire() throws Exception;
        void release(S s);
        int size();
        void stop();
        void evict(S s);
    }

    static class Key<S> {
        private final S s;
        public Key(S s) {
            this.s = Objects.requireNonNull(s);
        }
        @Override
        public boolean equals(Object o) {
            return o instanceof Key && ((Key) o).s == s;
        }
        @Override
        public int hashCode() {
            return s.hashCode();
        }
    }

    public ServicePool(ThrowingFunction<Endpoint, S, Exception> producer, Function<S, Endpoint> getEndpoint, int capacity) {
        this.producer = producer;
        this.getEndpoint = getEndpoint;
        this.capacity = capacity;
        this.cache = capacity > 0 ? LRUCacheFactory.newLRUCache(capacity, this::onEvict) : null;
    }

    protected void onEvict(S s) {
        Endpoint e = getEndpoint.apply(s);
        Pool<S> p = pool.get(e);
        if (p != null) {
            p.evict(s);
        }
    }

    /**
     * Tries to acquire the service with the given key
     *
     * @param endpoint the endpoint
     * @return the acquired service
     */
    public S acquire(Endpoint endpoint) throws Exception {
        if (!isStarted()) {
            return null;
        }
        S s = getPool(endpoint).acquire();
        if (s != null && cache != null) {
            cache.putIfAbsent(new Key<>(s), s);
        }
        return s;
    }

    /**
     * Releases the service back to the pool
     *
     * @param endpoint the endpoint
     * @param s the service
     */
    public void release(Endpoint endpoint, S s) {
        getPool(endpoint).release(s);
    }

    protected Pool<S> getPool(Endpoint endpoint) {
        return pool.computeIfAbsent(endpoint, this::createPool);
    }

    private Pool<S> createPool(Endpoint endpoint) {
        boolean singleton = endpoint.isSingleton();
        try {
            S s = producer.apply(endpoint);
            if (s instanceof IsSingleton) {
                singleton = ((IsSingleton) s).isSingleton();
            }
        } catch (Exception e) {
            // Ignore
        }
        if (singleton && capacity > 0) {
            return new SinglePool(endpoint);
        } else {
            return new MultiplePool(endpoint);
        }
    }

    /**
     * Returns the current size of the pool
     *
     * @return the current size of the pool
     */
    public int size() {
        return pool.values().stream().mapToInt(Pool::size).sum();
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
        pool.values().forEach(Pool::stop);
        pool.clear();
    }

    public void cleanUp() {
        if (cache instanceof LRUCache) {
            ((LRUCache) cache).cleanUp();
        }
    }

    public void resetStatistics() {
        if (cache instanceof LRUCache) {
            ((LRUCache) cache).resetStatistics();
        }
    }

    public long getEvicted() {
        if (cache instanceof LRUCache) {
            return ((LRUCache) cache).getEvicted();
        } else {
            return -1;
        }
    }

    public long getMisses() {
        if (cache instanceof LRUCache) {
            return ((LRUCache) cache).getMisses();
        } else {
            return -1;
        }
    }

    public long getHits() {
        if (cache instanceof LRUCache) {
            return ((LRUCache) cache).getHits();
        } else {
            return -1;
        }
    }

    public int getMaxCacheSize() {
        if (cache instanceof LRUCache) {
            return ((LRUCache) cache).getMaxCacheSize();
        } else {
            return -1;
        }
    }

    static <S extends Service> void stop(S s) {
        try {
            s.stop();
        } catch (Exception e) {
            LOG.debug("Error stopping service {}", s, e);
        }
    }

    private class SinglePool implements Pool<S> {
        private final Endpoint endpoint;
        private volatile S s;

        public SinglePool(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public S acquire() throws Exception {
            if (s == null) {
                synchronized (this) {
                    if (s == null) {
                        s = producer.apply(endpoint);
                        endpoint.getCamelContext().addService(s, true, true);
                    }
                }
            }
            return s;
        }

        @Override
        public void release(S s) {
        }

        @Override
        public int size() {
            return s != null ? 1 : 0;
        }

        @Override
        public void stop() {
            S toStop = null;
            synchronized (this) {
                toStop = s;
                s = null;
            }
            doStop(toStop);
        }

        @Override
        public void evict(S s) {
            synchronized (this) {
                if (this.s == s) {
                    this.s = null;
                }
            }
            doStop(s);
        }

        void doStop(S s) {
            if (s != null) {
                ServicePool.stop(s);
                try {
                    endpoint.getCamelContext().removeService(s);
                } catch (Exception e) {
                    LOG.debug("Error removing service {}", s, e);
                }
            }
        }
    }

    private class MultiplePool implements Pool<S> {
        private final Endpoint endpoint;
        private final ConcurrentLinkedQueue<S> queue = new ConcurrentLinkedQueue<>();

        public MultiplePool(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public S acquire() throws Exception {
            S s = queue.poll();
            if (s == null) {
                s = producer.apply(endpoint);
                s.start();
            }
            return s;
        }

        @Override
        public void release(S s) {
            if (queue.size() < capacity) {
                queue.add(s);
            } else {
                ServicePool.stop(s);
            }
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public void stop() {
            queue.forEach(ServicePool::stop);
            queue.clear();
        }

        @Override
        public void evict(S s) {
            queue.remove(s);
            ServicePool.stop(s);
        }
    }

}
