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
package org.apache.camel.component.micrometer.spi;

import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;
import org.apache.camel.impl.DefaultThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.ObjectHelper;

/**
 * This implements a {@link ThreadPoolFactory} and generates an Instrumented versions of ExecutorService used to
 * monitor performance of each thread using Metrics.
 */
public class InstrumentedThreadPoolFactory implements ThreadPoolFactory {

    private static final AtomicLong counter = new AtomicLong();
    private MeterRegistry meterRegistry;
    private ThreadPoolFactory threadPoolFactory;
    private String prefix = "instrumented-delegate-";

    public InstrumentedThreadPoolFactory(MeterRegistry meterRegistry) {
        this(meterRegistry, new DefaultThreadPoolFactory());
    }

    public InstrumentedThreadPoolFactory(MeterRegistry metricRegistry, ThreadPoolFactory threadPoolFactory) {
        ObjectHelper.notNull(metricRegistry, "meterRegistry", this);
        ObjectHelper.notNull(threadPoolFactory, "threadPoolFactory", this);
        this.meterRegistry = metricRegistry;
        this.threadPoolFactory = threadPoolFactory;
    }

    @Override
    public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        ExecutorService executorService = threadPoolFactory.newCachedThreadPool(threadFactory);
        return ExecutorServiceMetrics.monitor(meterRegistry, executorService, name(prefix));
    }

    @Override
    public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        ExecutorService executorService = threadPoolFactory.newThreadPool(profile, threadFactory);
        return ExecutorServiceMetrics.monitor(meterRegistry, executorService, name(profile.getId()));
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        ScheduledExecutorService executorService = threadPoolFactory.newScheduledThreadPool(profile, threadFactory);
        new ExecutorServiceMetrics(executorService, name(profile.getId()), Collections.emptySet()).bindTo(meterRegistry);
        return new TimedScheduledExecutorService(meterRegistry, executorService, name(prefix), Collections.emptySet());
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    private String name(String prefix) {
        return prefix + counter.incrementAndGet();
    }

}
