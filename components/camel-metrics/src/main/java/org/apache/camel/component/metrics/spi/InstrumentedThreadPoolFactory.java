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
package org.apache.camel.component.metrics.spi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.codahale.metrics.InstrumentedExecutorService;
import com.codahale.metrics.InstrumentedScheduledExecutorService;
import com.codahale.metrics.MetricRegistry;
import org.apache.camel.impl.DefaultThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.ObjectHelper;

/**
 * This implements a {@link ThreadPoolFactory} and generates an Instrumented versions of ExecutorService used to
 * monitor performance of each thread using Metrics.
 */
public class InstrumentedThreadPoolFactory implements ThreadPoolFactory {

    private MetricRegistry metricRegistry;

    private ThreadPoolFactory threadPoolFactory;

    public InstrumentedThreadPoolFactory(MetricRegistry metricRegistry) {
        this(metricRegistry, new DefaultThreadPoolFactory());
    }

    public InstrumentedThreadPoolFactory(MetricRegistry metricRegistry, ThreadPoolFactory threadPoolFactory) {
        ObjectHelper.notNull(metricRegistry, "metricRegistry", this);
        ObjectHelper.notNull(threadPoolFactory, "threadPoolFactory", this);
        this.metricRegistry = metricRegistry;
        this.threadPoolFactory = threadPoolFactory;
    }

    @Override
    public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return new InstrumentedExecutorService(threadPoolFactory.newCachedThreadPool(threadFactory), metricRegistry);
    }

    @Override
    public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        return new InstrumentedExecutorService(threadPoolFactory.newThreadPool(profile, threadFactory), metricRegistry, profile.getId());
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        return new InstrumentedScheduledExecutorService(threadPoolFactory.newScheduledThreadPool(profile, threadFactory), metricRegistry, profile.getId());
    }

}
