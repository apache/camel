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

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.ServicePool;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default implementation to inherit for a basic service pool.
 *
 * @version $Revision$
 */
public abstract class DefaultServicePool<Key, Service> extends ServiceSupport implements ServicePool<Key, Service> {
    protected final Log log = LogFactory.getLog(getClass());
    protected final ConcurrentHashMap<Key, BlockingQueue<Service>> pool = new ConcurrentHashMap<Key, BlockingQueue<Service>>();
    protected final int capacity;

    /**
     * The capacity, note this is per key.
     *
     * @param capacity the capacity per key.
     */
    public DefaultServicePool(int capacity) {
        this.capacity = capacity;
    }

    public synchronized Service acquireIfAbsent(Key key, Service service) {
        BlockingQueue<Service> entry = pool.get(key);
        if (entry == null) {
            entry = new ArrayBlockingQueue<Service>(capacity);
            pool.put(key, entry);
        }
        if (log.isTraceEnabled()) {
            log.trace("AddAndAcquire key: " + key + " service: " + service);
        }
        // do not add the service as we acquire it
        return service;
    }

    public synchronized Service acquire(Key key) {
        BlockingQueue<Service> services = pool.get(key);
        if (services == null || services.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("No free services in pool to acquire for key: " + key);
            }
            return null;
        }

        Service answer = services.poll();
        if (log.isTraceEnabled()) {
            log.trace("Acquire: " + key + " service: " + answer);
        }
        return answer;
    }

    public synchronized void release(Key key, Service service) {
        if (log.isTraceEnabled()) {
            log.trace("Release: " + key + " service: " + service);
        }
        BlockingQueue<Service> services = pool.get(key);
        if (services != null) {
            services.add(service);
        }
    }

    protected void doStart() throws Exception {
        log.debug("Starting service pool: " + this);
    }

    protected void doStop() throws Exception {
        log.debug("Stopping service pool: " + this);
        for (BlockingQueue<Service> entry : pool.values()) {
            Collection<Service> values = new ArrayList<Service>();
            entry.drainTo(values);
            ServiceHelper.stopServices(values);
            entry.clear();
        }
        pool.clear();
    }

}
