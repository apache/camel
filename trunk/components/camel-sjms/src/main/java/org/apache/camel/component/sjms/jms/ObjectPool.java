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
package org.apache.camel.component.sjms.jms;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO Add Class documentation for ObjectPool
 * 
 */
public abstract class ObjectPool<T> {

    private static final int DEFAULT_POOL_SIZE = 1;
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private BlockingQueue<T> objects;
    private int maxSize = DEFAULT_POOL_SIZE;
    private AtomicInteger poolCount = new AtomicInteger();
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    public ObjectPool() {
        this(DEFAULT_POOL_SIZE);
    }

    public ObjectPool(int poolSize) {
        this.maxSize = poolSize;
    }

    public void fillPool() {
        objects = new ArrayBlockingQueue<T>(getMaxSize(), false);
        for (int i = 0; i < maxSize; i++) {
            try {
                T t = createObject();
                objects.add(t);
                poolCount.incrementAndGet();
            } catch (Exception e) {
                logger.error("Unable to create Object and add it to the pool. Reason: "
                                 + e.getLocalizedMessage(), e);
            }
        }
    }

    public void drainPool() throws Exception {
        getLock().writeLock().lock();
        try {
            while (!objects.isEmpty()) {
                T t = objects.remove();
                destroyObject(t);
            }
        } finally {
            getLock().writeLock().unlock();
        }
    }

    /**
     * Implement to create new objects of type T when the pool is initialized
     * empty.
     * 
     * @return
     * @throws Exception
     */
    protected abstract T createObject() throws Exception;

    /**
     * Clean up pool objects
     * 
     * @return
     * @throws Exception
     */
    protected abstract void destroyObject(T t) throws Exception;

    /**
     * @return
     * @throws Exception
     */
    public T borrowObject() throws Exception {
        return borrowObject(1000);
    }

    /**
     * @return
     * @throws Exception
     */
    public T borrowObject(long timeout) throws Exception {
        T t = null;
        getLock().writeLock().lock();
        try {
            t = objects.poll(timeout, TimeUnit.MILLISECONDS);
        } finally {
            getLock().writeLock().unlock();
        }
        return t;
    }

    /**
     * @param object
     * @throws Exception
     */
    public void returnObject(T object) throws Exception {
        objects.add(object);
    }

    /**
     * @return
     */
    int size() {
        return objects.size();
    }

    /**
     * Gets the ReadWriteLock value of lock for this instance of ObjectPool.
     * 
     * @return the lock
     */
    protected ReadWriteLock getLock() {
        return lock;
    }

    /**
     * Gets the int value of maxSize for this instance of ObjectPool.
     * 
     * @return the maxSize
     */
    public int getMaxSize() {
        return maxSize;
    }
}
