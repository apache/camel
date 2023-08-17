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

    public PooledTaskFactory() {
    }

    public PooledTaskFactory(Object source) {
        super(source);
    }

    @Override
    public PooledExchangeTask acquire() {
        return pool.poll();
    }

    public PooledExchangeTask acquire(Exchange exchange, AsyncCallback callback) {
        PooledExchangeTask task = acquire();
        if (task == null) {
            if (statisticsEnabled) {
                statistics.created.increment();
            }
            task = create(exchange, callback);
        } else {
            if (statisticsEnabled) {
                statistics.acquired.increment();
            }
        }
        try {
            task.prepare(exchange, callback);
        } catch (Exception e) {
            // if error during prepare then we need to discard this task
            if (statisticsEnabled) {
                statistics.discarded.increment();
            }
            throw e;
        }
        return task;
    }

    @Override
    public boolean release(PooledExchangeTask task) {
        try {
            task.reset();
            boolean inserted = pool.offer(task);
            if (statisticsEnabled) {
                if (inserted) {
                    statistics.released.increment();
                } else {
                    statistics.discarded.increment();
                }
            }
            return inserted;
        } catch (Exception e) {
            if (statisticsEnabled) {
                statistics.discarded.increment();
            }
            return false;
        }
    }

    @Override
    public String toString() {
        if (source != null) {
            return "PooledTaskFactory[source: " + source + ", capacity: " + getCapacity() + "]";
        } else {
            return "PooledTaskFactory[capacity: " + getCapacity() + "]";
        }
    }
}
