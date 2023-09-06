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
package org.apache.camel.component.scheduler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.HealthCheckComponent;

@org.apache.camel.spi.annotations.Component("scheduler")
public class SchedulerComponent extends HealthCheckComponent {

    private final Map<String, ScheduledExecutorService> executors = new HashMap<>();
    private final Map<String, AtomicInteger> refCounts = new HashMap<>();

    @Metadata
    private boolean includeMetadata;
    @Metadata(defaultValue = "1", label = "scheduler")
    private int poolSize = 1;

    public SchedulerComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SchedulerEndpoint answer = new SchedulerEndpoint(uri, this, remaining);
        answer.setIncludeMetadata(isIncludeMetadata());
        answer.setPoolSize(getPoolSize());
        setProperties(answer, parameters);
        return answer;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    /**
     * Whether to include metadata in the exchange such as fired time, timer name, timer count etc.
     */
    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Number of core threads in the thread pool used by the scheduling thread pool.
     * <p/>
     * Is by default using a single thread
     */
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    protected ScheduledExecutorService addConsumer(SchedulerConsumer consumer) {
        String name = consumer.getEndpoint().getName();
        int poolSize = consumer.getEndpoint().getPoolSize();

        ScheduledExecutorService answer;
        synchronized (executors) {
            answer = executors.get(name);
            if (answer == null) {
                answer = getCamelContext().getExecutorServiceManager().newScheduledThreadPool(this, "scheduler://" + name,
                        poolSize);
                executors.put(name, answer);
                // store new reference counter
                refCounts.put(name, new AtomicInteger(1));
            } else {
                // increase reference counter
                AtomicInteger counter = refCounts.get(name);
                if (counter != null) {
                    counter.incrementAndGet();
                }
            }
        }
        return answer;
    }

    protected void removeConsumer(SchedulerConsumer consumer) {
        String name = consumer.getEndpoint().getName();

        synchronized (executors) {
            // decrease reference counter
            AtomicInteger counter = refCounts.get(name);
            if (counter != null && counter.decrementAndGet() <= 0) {
                refCounts.remove(name);
                // remove scheduler as its no longer in use
                ScheduledExecutorService scheduler = executors.remove(name);
                if (scheduler != null) {
                    getCamelContext().getExecutorServiceManager().shutdown(scheduler);
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        Collection<ScheduledExecutorService> collection = executors.values();
        for (ScheduledExecutorService scheduler : collection) {
            getCamelContext().getExecutorServiceManager().shutdown(scheduler);
        }
        executors.clear();
        refCounts.clear();
    }

}
