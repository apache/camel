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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.support.PooledObjectFactorySupport;

public abstract class PooledTaskFactory extends PooledObjectFactorySupport<PooledExchangeTask>
        implements PooledExchangeTaskFactory {

    @Override
    public PooledExchangeTask acquire() {
        return pool.poll();
    }

    public PooledExchangeTask acquire(Exchange exchange, AsyncCallback callback) {
        PooledExchangeTask task = acquire();
        if (task == null) {
            if (statistics.isStatisticsEnabled()) {
                statistics.created.increment();
            }
            task = create(exchange, callback);
        } else {
            if (statistics.isStatisticsEnabled()) {
                statistics.acquired.increment();
            }
        }
        task.prepare(exchange, callback);
        return task;
    }

    @Override
    public boolean release(PooledExchangeTask task) {
        try {
            task.reset();
            boolean inserted = pool.offer(task);
            if (statistics.isStatisticsEnabled()) {
                if (inserted) {
                    statistics.released.increment();
                } else {
                    statistics.discarded.increment();
                }
            }
            return inserted;
        } catch (Throwable e) {
            if (statistics.isStatisticsEnabled()) {
                statistics.discarded.increment();
            }
            return false;
        }
    }

    @Override
    public String toString() {
        return "PooledTaskFactory[capacity: " + getCapacity() + "]";
    }
}
