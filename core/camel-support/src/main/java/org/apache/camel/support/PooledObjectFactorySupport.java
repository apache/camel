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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.PooledObjectFactory;
import org.apache.camel.support.service.ServiceSupport;

public abstract class PooledObjectFactorySupport<T> extends ServiceSupport implements PooledObjectFactory<T> {

    protected final UtilizationStatistics statistics = new UtilizationStatistics();

    protected CamelContext camelContext;
    protected BlockingQueue<T> pool;
    protected int capacity = 100;
    protected boolean statisticsEnabled;

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        if (isPooled()) {
            this.pool = new ArrayBlockingQueue<>(capacity);
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean isStatisticsEnabled() {
        return statisticsEnabled;
    }

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        this.statisticsEnabled = statisticsEnabled;
    }

    @Override
    public int getSize() {
        if (pool != null) {
            return pool.size();
        } else {
            return 0;
        }
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public void resetStatistics() {
        statistics.reset();
    }

    @Override
    public boolean isPooled() {
        return true;
    }

    @Override
    public void purge() {
        if (pool != null) {
            pool.clear();
        }
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        statistics.reset();
        if (pool != null) {
            pool.clear();
        }
    }

    /**
     * Represents utilization statistics
     */
    protected final class UtilizationStatistics implements PooledObjectFactory.Statistics {

        public final LongAdder created = new LongAdder();
        public final LongAdder acquired = new LongAdder();
        public final LongAdder released = new LongAdder();
        public final LongAdder discarded = new LongAdder();

        @Override
        public void reset() {
            created.reset();
            acquired.reset();
            released.reset();
            discarded.reset();
        }

        @Override
        public long getCreatedCounter() {
            return created.longValue();
        }

        @Override
        public long getAcquiredCounter() {
            return acquired.longValue();
        }

        @Override
        public long getReleasedCounter() {
            return released.longValue();
        }

        @Override
        public long getDiscardedCounter() {
            return discarded.longValue();
        }

    }

}
