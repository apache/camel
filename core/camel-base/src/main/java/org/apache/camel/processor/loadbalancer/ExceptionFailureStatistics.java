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
package org.apache.camel.processor.loadbalancer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistics about exception failures for load balancers that reacts on exceptions
 */
public class ExceptionFailureStatistics {

    private final Map<Class<?>, AtomicLong> counters = new HashMap<>();
    private final AtomicLong fallbackCounter = new AtomicLong();

    public void init(List<Class<?>> exceptions) {
        if (exceptions != null) {
            for (Class<?> exception : exceptions) {
                counters.put(exception, new AtomicLong());
            }
        }
    }

    public Iterator<Class<?>> getExceptions() {
        return counters.keySet().iterator();
    }

    public long getFailureCounter(Class<?> exception) {
        AtomicLong counter = counters.get(exception);
        if (counter != null) {
            return counter.get();
        } else {
            return fallbackCounter.get();
        }
    }

    public void onHandledFailure(Exception exception) {
        Class<?> clazz = exception.getClass();

        AtomicLong counter = counters.get(clazz);
        if (counter != null) {
            counter.incrementAndGet();
        } else {
            fallbackCounter.incrementAndGet();
        }
    }

    public void reset() {
        for (AtomicLong counter : counters.values()) {
            counter.set(0);
        }
        fallbackCounter.set(0);
    }
}

