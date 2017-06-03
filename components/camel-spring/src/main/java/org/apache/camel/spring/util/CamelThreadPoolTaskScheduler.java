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
package org.apache.camel.spring.util;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.apache.camel.CamelContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * A Camel extension of Spring's {@link ThreadPoolTaskScheduler} which uses the
 * {@link org.apache.camel.spi.ExecutorServiceManager} to create and destroy the
 * thread pool, which ensures the thread pool is also managed and consistent with
 * other usages of thread pools in Camel.
 */
public class CamelThreadPoolTaskScheduler extends ThreadPoolTaskScheduler {
    
    private static final long serialVersionUID = 1L;
    private final CamelContext camelContext;
    private final Object source;
    private final String name;

    public CamelThreadPoolTaskScheduler(CamelContext camelContext, Object source, String name) {
        this.camelContext = camelContext;
        this.source = source;
        this.name = name;
    }

    @Override
    protected ScheduledExecutorService createExecutor(int poolSize, ThreadFactory threadFactory, RejectedExecutionHandler rejectedExecutionHandler) {
        return camelContext.getExecutorServiceManager().newScheduledThreadPool(source, name, poolSize);
    }

    @Override
    public void shutdown() {
        camelContext.getExecutorServiceManager().shutdownNow(getScheduledExecutor());
    }
}
