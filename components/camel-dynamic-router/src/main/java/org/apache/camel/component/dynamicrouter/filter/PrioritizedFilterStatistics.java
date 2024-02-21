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
package org.apache.camel.component.dynamicrouter.filter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics for a {@link PrioritizedFilter} that tracks details that include:
 * <ul>
 * <li>the id of the filter</li>
 * <li>the number of times the filter was invoked</li>
 * <li>the first time the filter was invoked (as epoch milliseconds)</li>
 * <li>the last time the filter was invoked (as epoch milliseconds)</li>
 * </ul>
 */
public class PrioritizedFilterStatistics {

    /**
     * The id of the filter.
     */
    private final String filterId;

    /**
     * The number of times the filter was invoked.
     */
    private final AtomicLong count;

    /**
     * The first time the filter was invoked (as epoch milliseconds).
     */
    private final AtomicLong first;

    /**
     * The last time the filter was invoked (as epoch milliseconds).
     */
    private final AtomicLong last;

    /**
     * Creates a new {@link PrioritizedFilterStatistics} instance with the given filter id.
     *
     * @param filterId the id of the filter that these statistics represent
     */
    public PrioritizedFilterStatistics(String filterId) {
        this.filterId = filterId;
        this.count = new AtomicLong(0);
        this.first = new AtomicLong(0);
        this.last = new AtomicLong(0);
    }

    /**
     * Returns the id of the filter that these statistics represent.
     *
     * @return the id of the filter that these statistics represent
     */
    public String getFilterId() {
        return filterId;
    }

    /**
     * Increments the number of times the filter was invoked, and updates the first and last times the filter was
     * invoked.
     */
    public void incrementCount() {
        long now = System.currentTimeMillis();
        if (count.incrementAndGet() == 1) {
            first.compareAndSet(0L, now);
        }
        last.updateAndGet(v -> Math.max(v, now));
    }

    /**
     * Returns the number of times the filter was invoked.
     *
     * @return the number of times the filter was invoked
     */
    public long getCount() {
        return count.get();
    }

    /**
     * Returns the first time the filter was invoked (as epoch milliseconds).
     *
     * @return the first time the filter was invoked (as epoch milliseconds)
     */
    public long getFirst() {
        return first.get();
    }

    /**
     * Returns the last time the filter was invoked (as epoch milliseconds).
     *
     * @return the last time the filter was invoked (as epoch milliseconds)
     */
    public long getLast() {
        return last.get();
    }

    /**
     * Returns a string representation of this statistics object.
     *
     * @return a string representation of this statistics object
     */
    @Override
    public String toString() {
        return String.format("PrioritizedFilterStatistics [id: %s, count: %d, first: %d, last: %d]",
                getFilterId(), getCount(), getFirst(), getLast());
    }
}
