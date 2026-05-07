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
package org.apache.camel.component.timer;

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Endpoint;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;

/**
 * The <a href="http://camel.apache.org/timer.html">Timer Component</a> is for generating message exchanges when a timer
 * fires.
 *
 * Represents the component that manages {@link TimerEndpoint}. It holds the list of {@link TimerConsumer} objects that
 * are started.
 */
@org.apache.camel.spi.annotations.Component("timer")
public class TimerComponent extends DefaultComponent {
    private final Map<String, TimerHolder> timers = new ConcurrentHashMap<>();

    @Metadata
    private boolean includeMetadata;

    public TimerComponent() {
    }

    @ManagedAttribute(description = "Include metadata")
    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    /**
     * Whether to include metadata in the exchange such as fired time, timer name, timer count etc.
     */
    @ManagedAttribute(description = "Include metadata")
    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public Timer getTimer(TimerConsumer consumer) {
        String key = consumer.getEndpoint().getTimerName();
        if (!consumer.getEndpoint().isDaemon()) {
            key = "nonDaemon:" + key;
        }

        return timers.compute(key, (k, v) -> {
            if (v == null) {
                // the timer name is also the thread name, so lets resolve a name to be used
                String name = consumer.getEndpoint().getCamelContext().getExecutorServiceManager()
                        .resolveThreadName("timer://" + consumer.getEndpoint().getTimerName());
                return new TimerHolder(new Timer(name, consumer.getEndpoint().isDaemon()));
            }
            v.refCount.incrementAndGet();
            return v;
        }).timer;
    }

    public void removeTimer(TimerConsumer consumer) {
        String key = consumer.getEndpoint().getTimerName();
        if (!consumer.getEndpoint().isDaemon()) {
            key = "nonDaemon:" + key;
        }
        timers.computeIfPresent(key, (k, v) -> {
            if (v.refCount.decrementAndGet() == 0) {
                v.timer.cancel();
                return null;
            }
            return v;
        });
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        TimerEndpoint answer = new TimerEndpoint(uri, this, remaining);
        answer.setIncludeMetadata(includeMetadata);

        // convert time from String to a java.util.Date using the supported patterns
        String time = getAndRemoveOrResolveReferenceParameter(parameters, "time", String.class);
        String pattern = getAndRemoveOrResolveReferenceParameter(parameters, "pattern", String.class);
        if (time != null) {
            SimpleDateFormat sdf;
            if (pattern != null) {
                sdf = new SimpleDateFormat(pattern);
            } else if (time.contains("T")) {
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
            } else {
                sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            }
            Date date = sdf.parse(time);
            answer.setTime(date);
            answer.setPattern(pattern);
        }

        setProperties(answer, parameters);
        return answer;
    }

    @Override
    protected void doStop() throws Exception {
        Collection<TimerHolder> collection = timers.values();
        for (TimerHolder holder : collection) {
            holder.timer.cancel();
        }
        timers.clear();
    }

    private static class TimerHolder {
        private final Timer timer;
        private final AtomicInteger refCount;

        private TimerHolder(Timer timer) {
            this.timer = timer;
            this.refCount = new AtomicInteger(1);
        }
    }
}
