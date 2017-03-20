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
package org.apache.camel.opentracing;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import io.opentracing.contrib.spanmanager.SpanManager;
import io.opentracing.contrib.spanmanager.concurrent.SpanPropagatingExecutorService;

import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;

public class OpenTracingExecutorServiceManager implements ExecutorServiceManager {

    private final ExecutorServiceManager delegate;
    private final SpanManager spanManager;

    public OpenTracingExecutorServiceManager(ExecutorServiceManager delegate, SpanManager spanManager) {
        this.delegate = delegate;
        this.spanManager = spanManager;
    }

    @Override
    public void shutdown() throws Exception {
        delegate.shutdown();
    }

    @Override
    public void start() throws Exception {
        delegate.start();
    }

    @Override
    public void stop() throws Exception {
        delegate.stop();
    }

    @Override
    public boolean awaitTermination(ExecutorService arg0, long arg1) throws InterruptedException {
        return delegate.awaitTermination(arg0, arg1);
    }

    @Override
    public ThreadPoolProfile getDefaultThreadPoolProfile() {
        return delegate.getDefaultThreadPoolProfile();
    }

    @Override
    public long getShutdownAwaitTermination() {
        return delegate.getShutdownAwaitTermination();
    }

    @Override
    public String getThreadNamePattern() {
        return delegate.getThreadNamePattern();
    }

    @Override
    public ThreadPoolFactory getThreadPoolFactory() {
        return delegate.getThreadPoolFactory();
    }

    @Override
    public ThreadPoolProfile getThreadPoolProfile(String arg0) {
        return delegate.getThreadPoolProfile(arg0);
    }

    @Override
    public ExecutorService newCachedThreadPool(Object arg0, String arg1) {
        return new SpanPropagatingExecutorService(delegate.newCachedThreadPool(arg0, arg1), spanManager);
    }

    @Override
    public ScheduledExecutorService newDefaultScheduledThreadPool(Object arg0, String arg1) {
        return delegate.newDefaultScheduledThreadPool(arg0, arg1);
    }

    @Override
    public ExecutorService newDefaultThreadPool(Object arg0, String arg1) {
        return new SpanPropagatingExecutorService(delegate.newDefaultThreadPool(arg0, arg1), spanManager);
    }

    @Override
    public ExecutorService newFixedThreadPool(Object arg0, String arg1, int arg2) {
        return new SpanPropagatingExecutorService(delegate.newFixedThreadPool(arg0, arg1, arg2), spanManager);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object arg0, String arg1, int arg2) {
        return delegate.newScheduledThreadPool(arg0, arg1, arg2);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object arg0, String arg1, ThreadPoolProfile arg2) {
        return delegate.newScheduledThreadPool(arg0, arg1, arg2);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(Object arg0, String arg1, String arg2) {
        return delegate.newScheduledThreadPool(arg0, arg1, arg2);
    }

    @Override
    public ExecutorService newSingleThreadExecutor(Object arg0, String arg1) {
        return new SpanPropagatingExecutorService(delegate.newSingleThreadExecutor(arg0, arg1), spanManager);
    }

    @Override
    public ScheduledExecutorService newSingleThreadScheduledExecutor(Object arg0, String arg1) {
        return delegate.newSingleThreadScheduledExecutor(arg0, arg1);
    }

    @Override
    public Thread newThread(String arg0, Runnable arg1) {
        return delegate.newThread(arg0, arg1);
    }

    @Override
    public ExecutorService newThreadPool(Object arg0, String arg1, ThreadPoolProfile arg2) {
        return new SpanPropagatingExecutorService(delegate.newThreadPool(arg0, arg1, arg2), spanManager);
    }

    @Override
    public ExecutorService newThreadPool(Object arg0, String arg1, String arg2) {
        return new SpanPropagatingExecutorService(delegate.newThreadPool(arg0, arg1, arg2), spanManager);
    }

    @Override
    public ExecutorService newThreadPool(Object arg0, String arg1, int arg2, int arg3) {
        return new SpanPropagatingExecutorService(delegate.newThreadPool(arg0, arg1, arg2, arg3), spanManager);
    }

    @Override
    public void registerThreadPoolProfile(ThreadPoolProfile arg0) {
        delegate.registerThreadPoolProfile(arg0);
    }

    @Override
    public String resolveThreadName(String arg0) {
        return delegate.resolveThreadName(arg0);
    }

    @Override
    public void setDefaultThreadPoolProfile(ThreadPoolProfile arg0) {
        delegate.setDefaultThreadPoolProfile(arg0);
    }

    @Override
    public void setShutdownAwaitTermination(long arg0) {
        delegate.setShutdownAwaitTermination(arg0);
    }

    @Override
    public void setThreadNamePattern(String arg0) throws IllegalArgumentException {
        delegate.setThreadNamePattern(arg0);
    }

    @Override
    public void setThreadPoolFactory(ThreadPoolFactory arg0) {
        delegate.setThreadPoolFactory(arg0);
    }

    @Override
    public void shutdown(ExecutorService arg0) {
        delegate.shutdown(arg0);
    }

    @Override
    public void shutdownGraceful(ExecutorService arg0) {
        delegate.shutdownGraceful(arg0);
    }

    @Override
    public void shutdownGraceful(ExecutorService arg0, long arg1) {
        delegate.shutdownGraceful(arg0, arg1);
    }

    @Override
    public List<Runnable> shutdownNow(ExecutorService arg0) {
        return delegate.shutdownNow(arg0);
    }

}
