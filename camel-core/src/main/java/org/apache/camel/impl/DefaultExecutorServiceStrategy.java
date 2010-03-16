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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @version $Revision$
 */
public class DefaultExecutorServiceStrategy extends ServiceSupport implements ExecutorServiceStrategy {

    private static final Log LOG = LogFactory.getLog(DefaultExecutorServiceStrategy.class);
    private final List<ExecutorService> executorServices = new ArrayList<ExecutorService>();
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

    public ExecutorService lookup(Object source, String executorServiceRef) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Looking up ExecutorService with ref: " + executorServiceRef);
        }
        return camelContext.getRegistry().lookup(executorServiceRef, ExecutorService.class);
    }

    public ExecutorService newCachedThreadPool(Object source, String name) {
        ExecutorService answer = ExecutorServiceHelper.newCachedThreadPool(threadNamePattern, name, true);
        onNewExecutorService(answer);
        return answer;
    }

    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, int poolSize) {
        ScheduledExecutorService answer = ExecutorServiceHelper.newScheduledThreadPool(poolSize, threadNamePattern, name, true);
        onNewExecutorService(answer);
        return answer;
    }

    public ExecutorService newFixedThreadPool(Object source, String name, int poolSize) {
        ExecutorService answer = ExecutorServiceHelper.newFixedThreadPool(poolSize, threadNamePattern, name, true);
        onNewExecutorService(answer);
        return answer;
    }

    public ExecutorService newSingleThreadExecutor(Object source, String name) {
        ExecutorService answer = ExecutorServiceHelper.newSingleThreadExecutor(threadNamePattern, name, true);
        onNewExecutorService(answer);
        return answer;
    }

    public ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize) {
        ExecutorService answer = ExecutorServiceHelper.newThreadPool(threadNamePattern, name, corePoolSize, maxPoolSize);
        onNewExecutorService(answer);
        return answer;
    }

    public ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, boolean daemon) {
        ExecutorService answer = ExecutorServiceHelper.newThreadPool(threadNamePattern, name, corePoolSize, maxPoolSize, keepAliveTime, timeUnit, daemon);
        onNewExecutorService(answer);
        return answer;
    }

    public void shutdown(ExecutorService executorService) {
        if (executorService.isShutdown()) {
            return;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Shutting down ExecutorService: " + executorService);
        }
        executorService.shutdown();
    }

    public List<Runnable> shutdownNow(ExecutorService executorService) {
        if (executorService.isShutdown()) {
            return null;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Shutting down now ExecutorService: " + executorService);
        }
        return executorService.shutdownNow();
    }

    /**
     * Callback when a new {@link java.util.concurrent.ExecutorService} have been created.
     *
     * @param executorService the created {@link java.util.concurrent.ExecutorService} 
     */
    protected void onNewExecutorService(ExecutorService executorService) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created new ExecutorService: " + executorService);
        }
        executorServices.add(executorService);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    @Override
    protected void doShutdown() throws Exception {
        // shutdown all executor services
        for (ExecutorService executorService : executorServices) {
            // only log if something goes wrong as we want to shutdown them all
            try {
                shutdownNow(executorService);
            } catch (Exception e) {
                LOG.warn("Error occurred during shutdown of ExecutorService: "
                        + executorService + ". This exception will be ignored.", e);
            }
        }
        executorServices.clear();
    }

}
