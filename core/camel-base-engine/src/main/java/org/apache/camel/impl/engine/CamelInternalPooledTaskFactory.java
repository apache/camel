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

import org.apache.camel.support.PooledObjectFactorySupport;

/**
 * A pool for reusing {@link CamelInternalTask} to reduce object allocations.
 */
final class CamelInternalPooledTaskFactory extends PooledObjectFactorySupport<CamelInternalTask> {

    @Override
    public void setStatisticsEnabled(boolean statisticsEnabled) {
        // we do not want to capture statistics so its disabled
    }

    @Override
    public CamelInternalTask acquire() {
        return pool.poll();
    }

    @Override
    public boolean release(CamelInternalTask task) {
        task.reset();
        return pool.offer(task);
    }

    @Override
    public String toString() {
        return "CamelInternalPooledTaskFactory[capacity: " + getCapacity() + "]";
    }

}
