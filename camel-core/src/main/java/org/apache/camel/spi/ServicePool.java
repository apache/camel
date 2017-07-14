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
package org.apache.camel.spi;

/**
 * A service pool is like a connection pool but can pool any kind of objects.
 * <p/>
 * Services that is capable of being pooled should implement the marker interface
 * {@link org.apache.camel.ServicePoolAware}.
 * <p/>
 * Notice the capacity is <b>per key</b> which means that each key can contain at most
 * (the capacity) services. The pool can contain an unbounded number of keys.
 * <p/>
 * By default the capacity is set to 100.
 *
 * @version 
 */
@Deprecated
public interface ServicePool<Key, Service> {

    /**
     * Sets the capacity, which is capacity <b>per key</b>.
     *
     * @param capacity the capacity per key
     */
    void setCapacity(int capacity);

    /**
     * Gets the capacity per key.
     *
     * @return the capacity per key
     */
    int getCapacity();

    /**
     * Adds the given service to the pool and acquires it.
     *
     * @param key     the key
     * @param service the service
     * @return the acquired service, is newer <tt>null</tt>
     * @throws IllegalStateException if the queue is full (capacity has been reached)
     */
    Service addAndAcquire(Key key, Service service);

    /**
     * Tries to acquire the service with the given key
     *
     * @param key the key
     * @return the acquired service, or <tt>null</tt> if no free in pool
     */
    Service acquire(Key key);

    /**
     * Releases the service back to the pool
     *
     * @param key     the key
     * @param service the service
     */
    void release(Key key, Service service);

    /**
     * Returns the current size of the pool
     *
     * @return the current size of the pool
     */
    int size();

    /**
     * Purges the pool.
     */
    void purge();

}
