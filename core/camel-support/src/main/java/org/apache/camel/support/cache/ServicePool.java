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
package org.apache.camel.support.cache;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.NonManagedService;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.support.LRUCache;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.function.ThrowingFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.requireNonNull;

/**
 * A base class for a pool for either producers or consumers used by {@link org.apache.camel.spi.ProducerCache} and
 * {@link org.apache.camel.spi.ConsumerCache}.
 */
abstract class ServicePool<S extends Service> extends ServiceSupport implements NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(ServicePool.class);

    private final ThrowingFunction<Endpoint, S, Exception> creator;
    private final Function<S, Endpoint> getEndpoint;
    private final ConcurrentMap<Endpoint, Pool<S>> pool = new ConcurrentHashMap<>();
    // the evicted single pools whose service is still in use, by service: they are no longer in the pool map, and the
    // last release of the service stops it (for multi pool then they have their own house-keeping for evictions)
    private final ConcurrentMap<S, Pool<S>> singlePoolEvicted = new ConcurrentHashMap<>();
    private final int capacity;
    private final Map<S, S> cache;

    private interface Pool<S> {
        /**
         * Acquires a service, or returns null when the pool was evicted or stopped meanwhile (single pool only)
         */
        S acquire() throws Exception;

        /**
         * Acquires the given service again if it is still the service of this pool (single pool only)
         */
        boolean reacquire(S s);

        /**
         * Releases the service, or returns false when it is not a service of this pool (single pool only)
         */
        boolean release(S s);

        int size();

        void stop();

        void evict(S s);

        void cleanUp();
    }

    protected ServicePool(ThrowingFunction<Endpoint, S, Exception> creator, Function<S, Endpoint> getEndpoint, int capacity) {
        this.creator = creator;
        this.getEndpoint = getEndpoint;
        this.capacity = capacity;
        this.cache = capacity > 0 ? LRUCacheFactory.newLRUCache(capacity, this::onEvict) : null;
    }

    /**
     * This callback is invoked by LRUCache when it evicts a service, which may be in use at the same time. Therefore,
     * the pools do not stop a service that is in use: SinglePool takes its (shared) service out of use at once, and
     * stops it when its last user releases it, and MultiPool stops an evicted service when it is idle, on acquire or
     * release.
     */
    protected void onEvict(S s) {
        Endpoint e = getEndpoint.apply(s);
        Pool<S> p = pool.get(e);
        if (p != null) {
            p.evict(s);
        } else if (e.isSingletonProducer()) {
            if (!singlePoolEvicted.containsKey(s)) {
                // service no longer in a pool (such as being released twice, or can happen during shutdown of Camel etc)
                stopAndRemove(s);
            }
            // otherwise its pool was evicted while it is in use, and stops it when it is released
        } else {
            // no pool for this endpoint (for example it was stopped, which stopped its idle services, or the
            // endpoint of the service is not the instance used as the pool key): do not stop the service here, as it
            // may be in use or idle in a live pool; a service in use is stopped when it is released without a pool
            LOG.trace("Evicted service: {} is no longer in a pool", s);
        }
    }

    /**
     * Tries to acquire the producer/consumer with the given key
     *
     * @param  endpoint the endpoint
     * @return          the acquired producer/consumer
     */
    public S acquire(Endpoint endpoint) throws Exception {
        S s = null;
        while (s == null) {
            if (!isStarted()) {
                return null;
            }
            // a single pool returns null when it was evicted or stopped meanwhile, and is then no longer in the pool
            // map, so a new pool is used
            s = getOrCreatePool(endpoint).acquire();
        }
        if (cache != null) {
            cache.putIfAbsent(s, s);
        }
        return s;
    }

    /**
     * Acquires the given singleton producer/consumer again, which was acquired before, if it is still pooled (it has
     * not been evicted or stopped meanwhile). It must be released as when it is acquired with
     * {@link #acquire(Endpoint)}.
     *
     * @param  s the producer/consumer
     * @return   true if it was acquired again, false if it is no longer pooled
     */
    boolean reacquire(S s) {
        Pool<S> p = pool.get(getEndpoint.apply(s));
        return p != null && p.reacquire(s);
    }

    /**
     * Releases the producer/consumer back to the pool
     *
     * @param endpoint the endpoint
     * @param s        the producer/consumer
     */
    public void release(Endpoint endpoint, S s) {
        Pool<S> p = pool.get(endpoint);
        if (p != null && p.release(s)) {
            return;
        }
        // the pool was evicted while this service was in use (the endpoint may have a new pool meanwhile)
        p = singlePoolEvicted.get(s);
        if (p == null || !p.release(s)) {
            stopAndRemove(s);
        }
    }

    private void stopAndRemove(S s) {
        ServicePool.stop(s);
        try {
            getEndpoint.apply(s).getCamelContext().removeService(s);
        } catch (Exception e) {
            LOG.debug("Error removing service: {}. This exception is ignored.", s, e);
        }
    }

    private Pool<S> getOrCreatePool(Endpoint endpoint) {
        // it is a pool, so we have a lot more hits, so use regular get, and then fallback to computeIfAbsent
        Pool<S> answer = pool.get(endpoint);
        if (answer == null) {
            boolean singleton = endpoint.isSingletonProducer();
            if (singleton) {
                answer = pool.computeIfAbsent(endpoint, SinglePool::new);
            } else {
                answer = pool.computeIfAbsent(endpoint, MultiplePool::new);
            }
        }
        return answer;
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
        if (cache instanceof LRUCache lru) {
            lru.cleanUp();
        }
        pool.values().forEach(Pool::cleanUp);
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
     * Whether the endpoint is (still) in use, and must therefore not be stopped when its producer is evicted: an
     * endpoint that is static in the endpoint registry (resolved when the routes were setup), that a route is consuming
     * from, or that another producer (or polling consumer) is still using, such as the producer cache of a toD in
     * another route or of a ProducerTemplate, as the pooled services are added to CamelContext.
     * <p>
     * This is intentionally broad: any {@link EndpointAware} service on the context that uses the endpoint keeps it
     * started, not only the producers and polling consumers pooled by a {@code ServicePool}, as stopping an endpoint
     * that is still in use is worse than keeping one started.
     */
    private static boolean isEndpointInUse(Endpoint endpoint) {
        CamelContext context = endpoint.getCamelContext();
        if (context == null) {
            return false;
        }
        if (context.getEndpointRegistry().isStatic(endpoint.getEndpointUri())) {
            return true;
        }
        for (Route route : context.getRoutes()) {
            if (route.getEndpoint() == endpoint) {
                return true;
            }
        }
        return context.hasService(s -> s instanceof EndpointAware ea && ea.getEndpoint() == endpoint) != null;
    }

    /**
     * Stops the service safely
     */
    private static <S extends Service> void stop(S s) {
        try {
            s.stop();
        } catch (Exception e) {
            LOG.debug("Error stopping service: {}. This exception is ignored.", s, e);
        }
    }

    /**
     * Pool used for singleton producers or consumers which are thread-safe and can be shared by multiple worker threads
     * at any given time.
     * <p>
     * The pool counts the users of its service (acquired and not yet released), so an evicted service is stopped when
     * it is no longer in use. An evicted pool is taken out of the pool map at once, so the next acquire of the endpoint
     * creates a new pool and service.
     */
    private class SinglePool implements Pool<S> {
        private final Endpoint endpoint;
        private volatile S s;
        // the acquires and the releases of s, counted apart without contention between the worker threads (the users of
        // s are the difference)
        private final LongAdder acquires = new LongAdder();
        private final LongAdder releases = new LongAdder();
        // s has been evicted: it is no longer handed out, and is stopped when it has no more users
        private volatile boolean evicted;
        private final AtomicBoolean stopped = new AtomicBoolean();

        SinglePool(Endpoint endpoint) {
            this.endpoint = requireNonNull(endpoint);
        }

        @Override
        public S acquire() throws Exception {
            if (!retain()) {
                return null;
            }
            S answer = s;
            if (answer == null) {
                boolean acquired = false;
                lock.lock();
                try {
                    answer = s;
                    if (answer == null && !stopped.get()) {
                        LOG.trace("Creating service from endpoint: {}", endpoint);
                        S tempS = creator.apply(endpoint);
                        endpoint.getCamelContext().addService(tempS, true, true);
                        s = tempS;
                        answer = tempS;
                    }
                    acquired = answer != null;
                } finally {
                    lock.unlock();
                    if (!acquired) {
                        // creating failed, or the pool was stopped meanwhile
                        unretain();
                    }
                }
            }
            LOG.trace("Acquired service: {}", answer);
            return answer;
        }

        @Override
        public boolean reacquire(S s) {
            return s == this.s && retain();
        }

        @Override
        public boolean release(S s) {
            if (s == null || s != this.s) {
                return false;
            }
            unretain();
            LOG.trace("Released service: {}", s);
            return true;
        }

        private boolean retain() {
            acquires.increment();
            if (evicted || stopped.get()) {
                // evicted or stopped meanwhile (the eviction may have counted this acquire already, so it is released)
                unretain();
                return false;
            }
            return true;
        }

        private void unretain() {
            releases.increment();
            if (evicted) {
                stopEvicted();
            }
        }

        @Override
        public int size() {
            return s != null ? 1 : 0;
        }

        @Override
        public void stop() {
            stopped.set(true);
            S toStop;
            lock.lock();
            try {
                toStop = s;
                s = null;
            } finally {
                lock.unlock();
            }
            pool.remove(endpoint, this);
            if (toStop != null) {
                singlePoolEvicted.remove(toStop, this);
            }
            doStop(toStop);
        }

        @Override
        public void evict(S s) {
            if (s != this.s || evicted) {
                // a service of an evicted pool of this endpoint, which an acquire in progress put in the cache again: its
                // pool stops it when it is no longer in use
                return;
            }
            // keep track of the evicted pool before it leaves the pool map, so the users of s can release it
            singlePoolEvicted.put(s, this);
            pool.remove(endpoint, this);
            evicted = true;
            stopEvicted();
        }

        /**
         * Stops the evicted service if it has no more users
         */
        private void stopEvicted() {
            // the releases are summed first: an acquire is counted before its release, so every release seen here has
            // its acquire seen in the sum below, and an acquire of a user that may still use s (it was counted before s
            // was evicted) is seen as well, so the counts are only equal when s has no more users
            long released = releases.sum();
            if (acquires.sum() != released || !stopped.compareAndSet(false, true)) {
                // still in use (the last user stops it), or stopped already
                return;
            }
            // under the lock that creates the services, so a service that a new pool of this endpoint creates meanwhile
            // is either seen as using the endpoint, or created after it was stopped
            lock.lock();
            try {
                S toStop = s;
                s = null;
                if (toStop != null) {
                    singlePoolEvicted.remove(toStop, this);
                }
                // stop the evicted service first, so it no longer counts as using the endpoint
                doStop(toStop);
                if (!isEndpointInUse(endpoint)) {
                    // stop the endpoint as well (such as a dynamic endpoint from toD) to free its resources
                    doStop(endpoint);
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void cleanUp() {
            // noop: an evicted service is stopped by the eviction, or by its last release
        }

        void doStop(Service s) {
            if (s != null) {
                ServicePool.stop(s);
                try {
                    endpoint.getCamelContext().removeService(s);
                } catch (Exception e) {
                    LOG.debug("Error removing service: {}. This exception is ignored.", s, e);
                }
            }
        }
    }

    /**
     * Pool used for non-singleton producers or consumers which are not thread-safe and can only be used by one worker
     * thread at any given time.
     */
    private class MultiplePool implements Pool<S> {
        private final Endpoint endpoint;
        private final BlockingQueue<S> queue;
        private final Deque<S> evicts;
        // the services created by this pool which have not been evicted, only these are kept in the queue
        private final Set<S> active;
        private volatile boolean stopped;

        MultiplePool(Endpoint endpoint) {
            this.endpoint = endpoint;
            this.queue = new ArrayBlockingQueue<>(capacity);
            this.evicts = new ConcurrentLinkedDeque<>();
            this.active = ConcurrentHashMap.newKeySet();
        }

        private void cleanupEvicts() {
            for (S evict = evicts.pollFirst(); evict != null; evict = evicts.pollFirst()) {
                doStop(evict);
            }
        }

        @Override
        public S acquire() throws Exception {
            cleanupEvicts();

            S s = queue.poll();
            if (s == null) {
                s = creator.apply(endpoint);
                s.start();
                active.add(s);
            }
            return s;
        }

        @Override
        public boolean reacquire(S s) {
            return false;
        }

        @Override
        public boolean release(S s) {
            cleanupEvicts();

            if (stopped || !active.contains(s) || !queue.offer(s)) {
                // the pool is stopped, it was evicted while in use, or there is no room so let's just stop and discard this
                active.remove(s);
                doStop(s);
            } else if ((stopped || !active.contains(s)) && queue.remove(s)) {
                // the pool was stopped, or the service evicted, after the check above, and did not take it from the queue
                active.remove(s);
                doStop(s);
            }
            return true;
        }

        @Override
        public int size() {
            return queue.size();
        }

        @Override
        public void stop() {
            stopped = true;
            ArrayList<S> list = new ArrayList<>();
            queue.drainTo(list);
            pool.remove(endpoint);
            list.forEach(this::doStop);
            cleanupEvicts();
        }

        @Override
        public void evict(S s) {
            // only an idle service can be stopped (by cleanupEvicts): take it out of the queue so it is not acquired
            // again, and a service in use is stopped when it is released
            if (active.remove(s) && queue.remove(s)) {
                evicts.add(s);
                if (stopped) {
                    // the pool was stopped meanwhile, and may have stopped its evicts already
                    cleanupEvicts();
                }
            }
            if (capacity > 0 && pool.size() > capacity) {
                // the pool is growing too large, so we need to stop (stop will remove itself from pool)
                stop();
            }
        }

        @Override
        public void cleanUp() {
            cleanupEvicts();
        }

        void doStop(Service s) {
            if (s != null) {
                ServicePool.stop(s);
                try {
                    if (endpoint != null) {
                        endpoint.getCamelContext().removeService(s);
                    }
                } catch (Exception e) {
                    LOG.debug("Error removing service: {}. This exception is ignored.", s, e);
                }
            }
        }
    }

}
