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

public class PrioritizedFilterStatistics {

    private final String filterId;

    private final AtomicLong count;

    private final AtomicLong first;

    private final AtomicLong last;

    public PrioritizedFilterStatistics(String filterId) {
        this.filterId = filterId;
        this.count = new AtomicLong(0);
        this.first = new AtomicLong(0);
        this.last = new AtomicLong(0);
    }

    public String getFilterId() {
        return filterId;
    }

    public void incrementCount() {
        long now = System.currentTimeMillis();
        if (count.incrementAndGet() == 1) {
            first.compareAndSet(0L, now);
        }
        last.updateAndGet(v -> Math.max(v, now));
    }

    public long getCount() {
        return count.get();
    }

    public long getFirst() {
        return first.get();
    }

    public long getLast() {
        return last.get();
    }

    @Override
    public String toString() {
        return String.format("PrioritizedFilterStatistics [id: %s, count: %d, first: %d, last: %d]",
                getFilterId(), getCount(), getFirst(), getLast());
    }
}
