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
package org.apache.camel.spi;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Service;

/**
 * Factory for pooled objects or tasks.
 */
public interface PooledObjectFactory<T> extends Service, CamelContextAware {

    /**
     * Utilization statistics of the this factory.
     */
    interface Statistics {

        /**
         * Number of new exchanges created.
         */
        long getCreatedCounter();

        /**
         * Number of exchanges acquired (reused) when using pooled factory.
         */
        long getAcquiredCounter();

        /**
         * Number of exchanges released back to pool
         */
        long getReleasedCounter();

        /**
         * Number of exchanges discarded (thrown away) such as if no space in cache pool.
         */
        long getDiscardedCounter();

        /**
         * Reset the counters
         */
        void reset();

    }

    /**
     * The current number of objects in the pool
     */
    int getSize();

    /**
     * The capacity the pool uses for storing objects. The default capacity is 100.
     */
    int getCapacity();

    /**
     * The capacity the pool uses for storing objects. The default capacity is 100.
     */
    void setCapacity(int capacity);

    /**
     * Whether statistics is enabled.
     */
    boolean isStatisticsEnabled();

    /**
     * Whether statistics is enabled.
     */
    void setStatisticsEnabled(boolean statisticsEnabled);

    /**
     * Reset the statistics
     */
    void resetStatistics();

    /**
     * Purges the internal cache (if pooled)
     */
    void purge();

    /**
     * Gets the usage statistics
     *
     * @return the statistics, or null if statistics is not enabled
     */
    Statistics getStatistics();

    /**
     * Whether the factory is pooled.
     */
    boolean isPooled();

    /**
     * Acquires an object from the pool (if any)
     *
     * @return the object or <tt>null</tt> if the pool is empty
     */
    T acquire();

    /**
     * Releases the object back to the pool
     *
     * @param  t the object
     * @return   true if released into the pool, or false if something went wrong and the object was discarded
     */
    boolean release(T t);

}
