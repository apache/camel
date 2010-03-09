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
package org.apache.camel.impl;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * @version $Revision$
 */
public class DefaultExecutorServiceStrategy implements ExecutorServiceStrategy {

    private final CamelContext camelContext;
    private String threadNamePattern = "Camel Thread ${counter} - ${name}";

    public DefaultExecutorServiceStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getThreadName(String name) {
        return ExecutorServiceHelper.getThreadName(threadNamePattern, name);
    }

    public String getThreadNamePattern() {
        return threadNamePattern;
    }

    public void setThreadNamePattern(String threadNamePattern) {
        this.threadNamePattern = threadNamePattern;
    }

    public ExecutorService lookup(String executorServiceRef) {
        return camelContext.getRegistry().lookup(executorServiceRef, ExecutorService.class);
    }

    public ExecutorService newCachedThreadPool(String name) {
        return ExecutorServiceHelper.newCachedThreadPool(getThreadName(name), true);
    }

    public ScheduledExecutorService newScheduledThreadPool(String name, int poolSize) {
        return ExecutorServiceHelper.newScheduledThreadPool(poolSize, getThreadName(name), true);
    }

    public ExecutorService newFixedThreadPool(String name, int poolSize) {
        return ExecutorServiceHelper.newFixedThreadPool(poolSize, getThreadName(name), true);
    }

    public ExecutorService newSingleThreadExecutor(String name) {
        return ExecutorServiceHelper.newSingleThreadExecutor(getThreadName(name), true);
    }

    public ExecutorService newThreadPool(String name, int corePoolSize, int maxPoolSize) {
        return ExecutorServiceHelper.newThreadPool(getThreadName(name), corePoolSize, maxPoolSize);
    }

    public ExecutorService newThreadPool(String name, int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, boolean daemon) {
        return ExecutorServiceHelper.newThreadPool(getThreadName(name), corePoolSize, maxPoolSize, keepAliveTime, timeUnit, daemon);
    }

    public void shutdown(ExecutorService executorService) {
        executorService.shutdown();
    }

    public List<Runnable> shutdownNow(ExecutorService executorService) {
        return executorService.shutdownNow();
    }
    
}
