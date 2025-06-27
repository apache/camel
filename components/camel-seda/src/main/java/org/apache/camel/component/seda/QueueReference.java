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
package org.apache.camel.component.seda;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;

/**
 * Holder for queue references.
 * <p/>
 * This is used to keep track of the usages of the queues, so we know when a queue is no longer in use, and can safely
 * be discarded.
 */
public final class QueueReference {

    private final BlockingQueue<Exchange> queue;
    private Integer size;
    private Boolean multipleConsumers;

    private final Lock lock = new ReentrantLock();
    private final List<SedaEndpoint> endpoints = new LinkedList<>();

    QueueReference(BlockingQueue<Exchange> queue, Integer size, Boolean multipleConsumers) {
        this.queue = queue;
        this.size = size;
        this.multipleConsumers = multipleConsumers;
    }

    void addReference(SedaEndpoint endpoint) {
        lock.lock();
        try {
            if (!endpoints.contains(endpoint)) {
                endpoints.add(endpoint);
                // update the multipleConsumers setting if need
                if (endpoint.isMultipleConsumers()) {
                    multipleConsumers = true;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    void removeReference(SedaEndpoint endpoint) {
        lock.lock();
        try {
            endpoints.remove(endpoint);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the reference counter
     */
    public int getCount() {
        lock.lock();
        try {
            return endpoints.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gets the queue size
     *
     * @return <tt>null</tt> if unbounded
     */
    public Integer getSize() {
        return size;
    }

    public Boolean getMultipleConsumers() {
        return multipleConsumers;
    }

    /**
     * Gets the queue
     */
    public BlockingQueue<Exchange> getQueue() {
        return queue;
    }

    public boolean hasConsumers() {
        lock.lock();
        try {
            for (SedaEndpoint endpoint : endpoints) {
                if (!endpoint.getConsumers().isEmpty()) {
                    return true;
                }
            }

            return false;
        } finally {
            lock.unlock();
        }
    }
}
