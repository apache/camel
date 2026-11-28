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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;

@Component("scheduler")
public class SchedulerComponent extends HealthCheckComponent {

    private final Map<String, ScheduledExecutorServiceHolder> executors = new ConcurrentHashMap<>();

    @Metadata(description = "Whether the consumers add the time when the scheduler fired to each message, as the"
                            + " CamelTimerName and CamelTimerFiredTime exchange properties and the CamelMessageTimestamp"
                            + " header. The default for the includeMetadata option of each endpoint that the component"
                            + " creates.")
    private boolean includeMetadata;
    @Metadata(defaultValue = "1", label = "scheduler",
              description = "The number of core threads of the thread pool that the component creates for each scheduler"
                            + " name. The default for the poolSize option of each endpoint that the component creates.")
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

    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    protected ScheduledExecutorService addConsumer(SchedulerConsumer consumer) {
        String name = consumer.getEndpoint().getName();
        return executors.compute(name, (k, v) -> {
            if (v == null) {
                int poolSize = consumer.getEndpoint().getPoolSize();
                return new ScheduledExecutorServiceHolder(
                        getCamelContext().getExecutorServiceManager().newScheduledThreadPool(this, "scheduler://" + name,
                                poolSize));
            }
            v.refCount.incrementAndGet();
            return v;
        }).executorService;
    }

    protected void removeConsumer(SchedulerConsumer consumer) {
        String name = consumer.getEndpoint().getName();

        executors.computeIfPresent(name, (k, v) -> {
            if (v.refCount.decrementAndGet() == 0) {
                getCamelContext().getExecutorServiceManager().shutdown(v.executorService);
                return null;
            }
            return v;
        });
    }

    @Override
    protected void doStop() throws Exception {
        Collection<ScheduledExecutorServiceHolder> collection = executors.values();
        for (ScheduledExecutorServiceHolder holder : collection) {
            getCamelContext().getExecutorServiceManager().shutdown(holder.executorService);
        }
        executors.clear();
    }

    private static class ScheduledExecutorServiceHolder {
        private final ScheduledExecutorService executorService;
        private final AtomicInteger refCount;

        ScheduledExecutorServiceHolder(ScheduledExecutorService executorService) {
            this.executorService = executorService;
            this.refCount = new AtomicInteger(1);
        }
    }
}
