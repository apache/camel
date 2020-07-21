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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

import org.apache.camel.Endpoint;
import org.apache.camel.NonManagedService;
import org.apache.camel.Service;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.function.ThrowingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A base class for a pool for either producers or consumers used by
 * {@link org.apache.camel.spi.ProducerCache} and {@link org.apache.camel.spi.ConsumerCache}.
 */
abstract class ServicePool<S extends Service> extends ServiceSupport implements NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(ServicePool.class);

    private final ThrowingFunction<Endpoint, S, Exception> creator;
    private final Function<S, Endpoint> getEndpoint;
    private final ConcurrentMap<Endpoint, Pool<S>> pool = new ConcurrentHashMap<>();
    // keep track of all singleton endpoints with a pooled producer that are evicted
    // for multi pool then they have their own house-keeping for evictions (more complex)
    private final ConcurrentMap<Endpoint, Pool<S>> singlePoolEvicted = new ConcurrentHashMap<>();
    private int capacity;
    private Map<S, S> cache;

    private interface Pool<S> {
        S acquire() throws Exception;
        void release(S s);
        int size();
        void stop();
        void evict(S s);
        void cleanUp();
    }

    public ServicePool(ThrowingFunction<Endpoint, S, Exception> creator, Function<S, Endpoint> getEndpoint, int capacity) {
        this.creator = creator;
        this.getEndpoint = getEndpoint;
        this.capacity = capacity;
        // only use a LRU cache if capacity is more than one
        // the LRU cache is a facade that handles the logic to know which producers/consumers to evict/remove
        // when we hit max capacity. Then we remove them in the associated pool ConcurrentMap instance.
        this.cache = capacity > 1 ? LRUCacheFactory.newLRUCache(capacity, this::onEvict) : null;
    }

    /**
     * This callback is invoked by LRUCache from a separate background cleanup thread.
     * Therefore we mark the entries to be evicted from this thread only,
     * and then let SinglePool and MultiPool handle the evictions (stop the producer/consumer safely)
     * when they are acquiring/releases producers/consumers. If we sop the producer/consumer from the
     * LRUCache background thread we can have a race condition with a pooled producer may have been
     * acquired at the same time its being evicted.
     */
    protected void onEvict(S s) {
        Endpoint e = getEndpoint.apply(s);
        Pool<S> p = pool.get(e);
        if (p != null) {
            p.evict(s);
        } else {
            // service no longer in a pool (such as being released twice, or can happen during shutdown of Camel etc)
            ServicePool.stop(s);
            try {
                e.getCamelContext().removeService(s);
            } catch (Exception ex) {
                LOG.debug("Error removing service: {}", s, ex);
            }
        }
    }

    /**
     * Tries to acquire the producer/consumer with the given key
     *
     * @param endpoint the endpoint
     * @return the acquired producer/consumer
     */
    public S acquire(Endpoint endpoint) throws Exception {
        if (!isStarted()) {
            return null;
        }
        S s = getOrCreatePool(endpoint).acquire();
        if (s != null && cache != null) {
            cache.putIfAbsent(s, s);
        }
        return s;
    }

    /**
     * Releases the producer/consumer back to the pool
     *
     * @param endpoint the endpoint
     * @param s the producer/consumer
     */
    public void release(Endpoint endpoint, S s) {
        Pool<S> p = pool.get(endpoint);
        if (p != null) {
            p.release(s);
        }
    }

    private Pool<S> getOrCreatePool(Endpoint endpoint) {
        return pool.computeIfAbsent(endpoint, this::createPool);
    }

    private Pool<S> createPool(Endpoint endpoint) {
        boolean singleton = endpoint.isSingletonProducer();
        if (singleton) {
            return new SinglePool(endpoint);
        } else {
            return new MultiplePool(endpoint);
        }
    }

    /**
     * Returns the current size of the pool
     */
    public int size() {
        return pool.values().stream().mapToInt(Pool::size).sum();
    }

    /**
     * Cleanup the pool (removing stale instances that should be evicted)
     */
    public void cleanUp() {
        if (cache instanceof LRUCache) {
            ((LRUCache) cache).cleanUp();
        }
        pool.values().forEach(Pool::cleanUp);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        cleanUp();

        pool.values().forEach(Pool::stop);
        pool.clear();
        if (cache != null) {
            cache.values().forEach(ServicePool::stop);
            cache.clear();
        }
        singlePoolEvicted.values().forEach(Pool::stop);
        singlePoolEvicted.clear();
    }

    /**
     * Stosp the service safely
     */
    private static <S extends Service> void stop(S s) {
        try {
            s.stop();
        } catch (Exception e) {
            LOG.debug("Error stopping service: {}", s, e);
        }
    }

    /**
     * Pool used for singleton producers or consumers which are thread-safe
     * and can be shared by multiple worker threads at any given time.
     */
    private class SinglePool implements Pool<S> {
        private final Endpoint endpoint;
        private volatile S s;

        SinglePool(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public S acquire() throws Exception {
            cleanupEvicts();

            if (s == null) {
                synchronized (this) {
                    if (s == null) {
                        LOG.trace("Creating service from endpoint: {}", endpoint);
                        S tempS = creator.apply(endpoint);
                        endpoint.getCamelContext().addService(tempS, true, true);
                        s = tempS;
                    }
                }
            }
            LOG.trace("Acquired service: {}", s);
            return s;
        }

        @Override
        public void release(S s) {
            cleanupEvicts();

            // noop
            LOG.trace("Released service: {}", s);
        }

        @Override
        public int size() {
            return s != null ? 1 : 0;
        }

        @Override
        public void stop() {
            S toStop;
            synchronized (this) {
                toStop = s;
                s = null;
            }
            doStop(toStop);
            pool.remove(endpoint);
        }

        @Override
        public void evict(S s) {
            singlePoolEvicted.putIfAbsent(endpoint, this);
        }

        @Override
        public void cleanUp() {
            cleanupEvicts();
        }

        private void cleanupEvicts() {
            singlePoolEvicted.forEach((e, p) -> {
                doStop(e);
                p.stop();
                singlePoolEvicted.remove(e);
            });
        }

        void doStop(Service s) {
            if (s != null) {
                ServicePool.stop(s);
                try {
                    endpoint.getCamelContext().removeService(s);
                } catch (Exception e) {
                    LOG.debug("Error removing service: {}", s, e);
                }
            }
        }
    }

    /**
     * Pool used for non-singleton producers or consumers which are not thread-safe
     * and can only be used by one worker thread at any given time.
     */
    private class MultiplePool implements Pool<S> {
        private final Endpoint endpoint;
        private final BlockingQueue<S> queue;
        private final List<S> evicts;

        MultiplePool(Endpoint endpoint) {
            this.endpoint = endpoint;
            this.queue = new ArrayBlockingQueue<>(capacity);
            this.evicts = new ArrayList<>();
        }

        private void cleanupEvicts() {
            if (!evicts.isEmpty()) {
                synchronized (this) {
                    if (!evicts.isEmpty()) {
                        evicts.forEach(this::doStop);
                        evicts.forEach(queue::remove);
                        evicts.clear();
                        if (queue.isEmpty()) {
                            pool.remove(endpoint);
                        }
                    }
                }
            }
        }

        @Override
        public S acquire() throws Exception {
            cleanupEvicts();

            S s = queue.poll();
            if (s == null) {
                s = creator.apply(endpoint);
                s.start();
            }
            return s;
        }

        @Override
        public void release(S s) {
            cleanupEvicts();

            if (!queue.offer(s)) {
                // there is no room so lets just stop and discard this
                doStop(s);
            }
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public void stop() {
            queue.forEach(this::doStop);
            queue.clear();
            pool.remove(endpoint);
        }

        @Override
        public void evict(S s) {
            // to be evicted
            evicts.add(s);
        }

        @Override
        public void cleanUp() {
            cleanupEvicts();
        }

        void doStop(Service s) {
            if (s != null) {
                ServicePool.stop(s);
                try {
                    endpoint.getCamelContext().removeService(s);
                } catch (Exception e) {
                    LOG.debug("Error removing service: {}", s, e);
                }
            }
        }
    }

}
