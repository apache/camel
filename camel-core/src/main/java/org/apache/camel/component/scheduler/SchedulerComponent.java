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
package org.apache.camel.component.scheduler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;

public class SchedulerComponent extends UriEndpointComponent {

    private final Map<String, ScheduledExecutorService> executors = new HashMap<String, ScheduledExecutorService>();
    private final Map<String, AtomicInteger> refCounts = new HashMap<String, AtomicInteger>();

    @Metadata(defaultValue = "1", label = "scheduler")
    private int concurrentTasks = 1;

    public SchedulerComponent() {
        super(SchedulerEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SchedulerEndpoint answer = new SchedulerEndpoint(uri, this, remaining);
        answer.setConcurrentTasks(getConcurrentTasks());
        setProperties(answer, parameters);
        return answer;
    }

    public int getConcurrentTasks() {
        return concurrentTasks;
    }

    /**
     * Number of threads used by the scheduling thread pool.
     * <p/>
     * Is by default using a single thread
     */
    public void setConcurrentTasks(int concurrentTasks) {
        this.concurrentTasks = concurrentTasks;
    }

    protected ScheduledExecutorService addConsumer(SchedulerConsumer consumer) {
        String name = consumer.getEndpoint().getName();
        int concurrentTasks = consumer.getEndpoint().getConcurrentTasks();

        ScheduledExecutorService answer;
        synchronized (executors) {
            answer = executors.get(name);
            if (answer == null) {
                answer = getCamelContext().getExecutorServiceManager().newScheduledThreadPool(this, "scheduler://" + name, concurrentTasks);
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
