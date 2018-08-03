/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.graalvm;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultThreadPoolFactory;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.concurrent.CamelThreadFactory;

public class FastExecutorServiceManager implements ExecutorServiceManager {

    private final CamelContext camelContext;
    private String threadNamePattern;
    private ThreadPoolFactory threadPoolFactory;

    public FastExecutorServiceManager(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public ThreadPoolFactory getThreadPoolFactory() {
        if (threadPoolFactory == null) {
            threadPoolFactory = new DefaultThreadPoolFactory();
        }
        return threadPoolFactory;
    }

    @Override
    public void setThreadPoolFactory(ThreadPoolFactory threadPoolFactory) {
        this.threadPoolFactory = threadPoolFactory;
    }

    @Override
    public String resolveThreadName(String name) {
        return null;
    }

    @Override
    public ThreadPoolProfile getThreadPoolProfile(String id) {
        return null;
    }

    @Override
    public void registerThreadPoolProfile(ThreadPoolProfile profile) {

    }

    @Override
    public void setDefaultThreadPoolProfile(ThreadPoolProfile defaultThreadPoolProfile) {

    }

    @Override
    public ThreadPoolProfile getDefaultThreadPoolProfile() {
        return null;
    }

    @Override
    public void setThreadNamePattern(String pattern) throws IllegalArgumentException {
        threadNamePattern = pattern;
    }

    @Override
    public String getThreadNamePattern() {
        if (threadNamePattern == null) {
            // set default name pattern which includes the camel context name
            threadNamePattern = "Camel (" + camelContext.getName() + ") thread ##counter# - #name#";
        }
        return threadNamePattern;
    }

    @Override
    public void setShutdownAwaitTermination(long timeInMillis) {

    }

    @Override
    public long getShutdownAwaitTermination() {
        return 0;
    }

    @Override
    public Thread newThread(String name, Runnable runnable) {
        return null;
    }

    @Override
    public ExecutorService newDefaultThreadPool(Object source, String name) {
        return null;
    }

    @Override
    public ScheduledExecutorService newDefaultScheduledThreadPool(Object source, String name) {
        return null;
    }

    @Override
    public ExecutorService newThreadPool(Object source, String name, ThreadPoolProfile profile) {
        String sanitizedName = URISupport.sanitizeUri(name);
        ThreadPoolProfile defaultProfile = getDefaultThreadPoolProfile();
        profile.addDefaults(defaultProfile);
        ThreadFactory threadFactory = createThreadFactory(sanitizedName, true);
        ExecutorService executorService = threadPoolFactory.newThreadPool(profile, threadFactory);
        return executorService;
    }

    protected ThreadFactory createThreadFactory(String name, boolean isDaemon) {
        return new CamelThreadFactory(threadNamePattern, name, isDaemon);
    }

    @Override
    public ExecutorService newThreadPool(Object source, String name, String profileId) {
        return null;
    }

    @Override
    public ExecutorService newThreadPool(Object source, String name, int poolSize, int maxPoolSize) {
        return null;
    }

    @Override
    public ExecutorService newSingleThreadExecutor(Object source, String name) {
        return null;
    }

    @Override
    public ExecutorService newCachedThreadPool(Object source, String name) {
        return null;
    }

    @Override
    public ExecutorService newFixedThreadPool(Object source, String name, int poolSize) {
        return null;
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, int poolSize) {
        return null;
    }

    @Override
    public ScheduledExecutorService newSingleThreadScheduledExecutor(Object source, String name) {
        return null;
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, ThreadPoolProfile profile) {
        return null;
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object source, String name, String profileId) {
        return null;
    }

    @Override
    public void shutdown(ExecutorService executorService) {

    }

    @Override
    public void shutdownGraceful(ExecutorService executorService) {

    }

    @Override
    public void shutdownGraceful(ExecutorService executorService, long shutdownAwaitTermination) {

    }

    @Override
    public List<Runnable> shutdownNow(ExecutorService executorService) {
        return null;
    }

    @Override
    public boolean awaitTermination(ExecutorService executorService, long shutdownAwaitTermination) throws InterruptedException {
        return false;
    }

    @Override
    public void shutdown() throws Exception {

    }

    @Override
    public void start() throws Exception {
    }

    @Override
    public void stop() throws Exception {

    }
}
