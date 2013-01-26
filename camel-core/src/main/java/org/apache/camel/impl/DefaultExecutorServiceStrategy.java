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
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.concurrent.SynchronousExecutorService;

/**
 * @deprecated use {@link org.apache.camel.spi.ExecutorServiceManager} instead, will be removed in a future Camel release
 */
@Deprecated
public class DefaultExecutorServiceStrategy extends ServiceSupport implements ExecutorServiceStrategy {

    // delegate to ExecutorServiceManager

    private final CamelContext camelContext;

    public DefaultExecutorServiceStrategy(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public void registerThreadPoolProfile(ThreadPoolProfile profile) {
        camelContext.getExecutorServiceManager().registerThreadPoolProfile(profile);
    }

    public ThreadPoolProfile getThreadPoolProfile(String id) {
        return camelContext.getExecutorServiceManager().getThreadPoolProfile(id);
    }

    public ThreadPoolProfile getDefaultThreadPoolProfile() {
        return camelContext.getExecutorServiceManager().getDefaultThreadPoolProfile();
    }

    public void setDefaultThreadPoolProfile(ThreadPoolProfile defaultThreadPoolProfile) {
        camelContext.getExecutorServiceManager().setDefaultThreadPoolProfile(defaultThreadPoolProfile);
    }

    public String getThreadName(String name) {
        return camelContext.getExecutorServiceManager().resolveThreadName(name);
    }

    public String getThreadNamePattern() {
        return camelContext.getExecutorServiceManager().getThreadNamePattern();
    }

    public void setThreadNamePattern(String pattern) throws IllegalArgumentException {
        camelContext.getExecutorServiceManager().setThreadNamePattern(pattern);
    }

    public ExecutorService lookup(Object source, String name, String executorServiceRef) {
        ExecutorService answer = camelContext.getRegistry().lookupByNameAndType(executorServiceRef, ExecutorService.class);
        if (answer == null) {
            // try to see if we got a thread pool profile with that id
            answer = newThreadPool(source, name, executorServiceRef);
        }
        return answer;
    }

    public ScheduledExecutorService lookupScheduled(Object source, String name, String executorServiceRef) {
        ScheduledExecutorService answer = camelContext.getRegistry().lookupByNameAndType(executorServiceRef, ScheduledExecutorService.class);
        if (answer == null) {
            ThreadPoolProfile profile = getThreadPoolProfile(executorServiceRef);
            if (profile != null) {
                Integer poolSize = profile.getPoolSize();
                if (poolSize == null) {
                    poolSize = getDefaultThreadPoolProfile().getPoolSize();
                }
                answer = newScheduledThreadPool(source, name, poolSize);
            }
        }
        return answer;
    }

    public ExecutorService newDefaultThreadPool(Object source, String name) {
        return camelContext.getExecutorServiceManager().newDefaultThreadPool(source, name);
    }

    public ExecutorService newThreadPool(Object source, String name, String threadPoolProfileId) {
        return camelContext.getExecutorServiceManager().newThreadPool(source, name, threadPoolProfileId);
    }

    public ExecutorService newCachedThreadPool(Object source, String name) {
        return camelContext.getExecutorServiceManager().newCachedThreadPool(source, name);
    }

    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, int poolSize) {
        return camelContext.getExecutorServiceManager().newScheduledThreadPool(source, name, poolSize);
    }

    public ScheduledExecutorService newScheduledThreadPool(Object source, String name) {
        return camelContext.getExecutorServiceManager().newDefaultScheduledThreadPool(source, name);
    }

    public ExecutorService newFixedThreadPool(Object source, String name, int poolSize) {
        return camelContext.getExecutorServiceManager().newFixedThreadPool(source, name, poolSize);
    }

    public ExecutorService newSingleThreadExecutor(Object source, String name) {
        return camelContext.getExecutorServiceManager().newSingleThreadExecutor(source, name);
    }

    public ExecutorService newSynchronousThreadPool(Object source, String name) {
        return new SynchronousExecutorService();
    }

    public ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize) {
        return camelContext.getExecutorServiceManager().newThreadPool(source, name, corePoolSize, maxPoolSize);
    }

    public ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize, int maxQueueSize) {
        // use a profile with the settings
        ThreadPoolProfile profile = new ThreadPoolProfile();
        profile.setPoolSize(corePoolSize);
        profile.setMaxPoolSize(maxPoolSize);
        profile.setMaxQueueSize(maxQueueSize);

        return camelContext.getExecutorServiceManager().newThreadPool(source, name, profile);
    }

    public ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize,
                                         long keepAliveTime, TimeUnit timeUnit, int maxQueueSize,
                                         RejectedExecutionHandler rejectedExecutionHandler, boolean daemon) {
        // use a profile with the settings
        ThreadPoolProfile profile = new ThreadPoolProfile();
        profile.setPoolSize(corePoolSize);
        profile.setMaxPoolSize(maxPoolSize);
        profile.setMaxQueueSize(maxQueueSize);
        profile.setKeepAliveTime(keepAliveTime);
        profile.setTimeUnit(timeUnit);

        // must cast to ThreadPoolExecutor to be able to set the rejected execution handler
        ThreadPoolExecutor answer = (ThreadPoolExecutor) camelContext.getExecutorServiceManager().newThreadPool(source, name, profile);
        answer.setRejectedExecutionHandler(rejectedExecutionHandler);
        return answer;
    }

    public void shutdown(ExecutorService executorService) {
        camelContext.getExecutorServiceManager().shutdown(executorService);
    }

    public List<Runnable> shutdownNow(ExecutorService executorService) {
        return camelContext.getExecutorServiceManager().shutdownNow(executorService);
    }

    protected void doStart() throws Exception {
        // noop
    }

    protected void doStop() throws Exception {
        // noop
    }
}
