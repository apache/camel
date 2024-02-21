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
package org.apache.camel.util.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A sized {@link ScheduledExecutorService} which will reject executing tasks if the task queue is full.
 * <p/>
 * The {@link ScheduledThreadPoolExecutor} which is the default implementation of the {@link ScheduledExecutorService}
 * has unbounded task queue, which mean you can keep scheduling tasks which may cause the system to run out of memory.
 * <p/>
 * This class is a wrapped for {@link ScheduledThreadPoolExecutor} to reject executing tasks if an upper limit of the
 * task queue has been reached.
 */
public class SizedScheduledExecutorService implements ScheduledExecutorService {

    private static final Logger LOG = LoggerFactory.getLogger(SizedScheduledExecutorService.class);
    public static final String QUEUE_SIZE_LIMIT_REACHED = "Task rejected due queue size limit reached";
    private final ScheduledThreadPoolExecutor delegate;
    private final long queueSize;

    /**
     * Creates a new sized {@link ScheduledExecutorService} with the given queue size as upper task limit.
     *
     * @param delegate  the delegate of the actual thread pool implementation
     * @param queueSize the upper queue size, use 0 or negative value for unlimited
     */
    public SizedScheduledExecutorService(ScheduledThreadPoolExecutor delegate, long queueSize) {
        this.delegate = delegate;
        this.queueSize = queueSize;
    }

    /**
     * Gets the wrapped {@link ScheduledThreadPoolExecutor}
     */
    public ScheduledThreadPoolExecutor getScheduledThreadPoolExecutor() {
        return delegate;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> task, long delay, TimeUnit timeUnit) {
        if (canScheduleOrExecute()) {
            return delegate.schedule(task, delay, timeUnit);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit timeUnit) {
        if (canScheduleOrExecute()) {
            return delegate.schedule(task, delay, timeUnit);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit timeUnit) {
        if (canScheduleOrExecute()) {
            return delegate.scheduleAtFixedRate(task, initialDelay, period, timeUnit);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long period, TimeUnit timeUnit) {
        if (canScheduleOrExecute()) {
            return delegate.scheduleWithFixedDelay(task, initialDelay, period, timeUnit);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit timeUnit) throws InterruptedException {
        return delegate.awaitTermination(timeout, timeUnit);
    }

    public int getActiveCount() {
        return delegate.getActiveCount();
    }

    public long getCompletedTaskCount() {
        return delegate.getCompletedTaskCount();
    }

    public int getCorePoolSize() {
        return delegate.getCorePoolSize();
    }

    public long getKeepAliveTime(TimeUnit timeUnit) {
        return delegate.getKeepAliveTime(timeUnit);
    }

    public int getLargestPoolSize() {
        return delegate.getLargestPoolSize();
    }

    public int getMaximumPoolSize() {
        return delegate.getMaximumPoolSize();
    }

    public int getPoolSize() {
        return delegate.getPoolSize();
    }

    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return delegate.getRejectedExecutionHandler();
    }

    public long getTaskCount() {
        return delegate.getTaskCount();
    }

    public ThreadFactory getThreadFactory() {
        return delegate.getThreadFactory();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        if (canScheduleOrExecute()) {
            return delegate.invokeAll(tasks);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit timeUnit)
            throws InterruptedException {
        if (canScheduleOrExecute()) {
            return delegate.invokeAll(tasks, timeout, timeUnit);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        if (canScheduleOrExecute()) {
            return delegate.invokeAny(tasks);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit timeUnit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (canScheduleOrExecute()) {
            return delegate.invokeAny(tasks, timeout, timeUnit);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    public boolean isTerminating() {
        return delegate.isTerminating();
    }

    public int prestartAllCoreThreads() {
        return delegate.prestartAllCoreThreads();
    }

    public boolean prestartCoreThread() {
        return delegate.prestartCoreThread();
    }

    public void purge() {
        delegate.purge();
    }

    public void setCorePoolSize(int corePoolSize) {
        delegate.setCorePoolSize(corePoolSize);
    }

    public void setKeepAliveTime(long keepAliveTime, TimeUnit timeUnit) {
        delegate.setKeepAliveTime(keepAliveTime, timeUnit);
    }

    public void setMaximumPoolSize(int maximumPoolSize) {
        delegate.setMaximumPoolSize(maximumPoolSize);
    }

    public void setRejectedExecutionHandler(RejectedExecutionHandler rejectedExecutionHandler) {
        delegate.setRejectedExecutionHandler(rejectedExecutionHandler);
    }

    public void setThreadFactory(ThreadFactory threadFactory) {
        delegate.setThreadFactory(threadFactory);
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (canScheduleOrExecute()) {
            return delegate.submit(task);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (canScheduleOrExecute()) {
            return delegate.submit(task);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (canScheduleOrExecute()) {
            return delegate.submit(task, result);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    @Override
    public void execute(Runnable task) {
        if (canScheduleOrExecute()) {
            delegate.execute(task);
        } else {
            throw new RejectedExecutionException(QUEUE_SIZE_LIMIT_REACHED);
        }
    }

    public void allowCoreThreadTimeOut(boolean value) {
        delegate.allowCoreThreadTimeOut(value);
    }

    public boolean allowsCoreThreadTimeOut() {
        return delegate.allowsCoreThreadTimeOut();
    }

    /**
     * Can the task be scheduled or executed?
     *
     * @return <tt>true</tt> to accept, <tt>false</tt> to not accept
     */
    protected boolean canScheduleOrExecute() {
        if (queueSize <= 0) {
            return true;
        }

        int size = delegate.getQueue().size();
        boolean answer = size < queueSize;
        if (LOG.isTraceEnabled()) {
            LOG.trace("canScheduleOrExecute {} < {} -> {}", size, queueSize, answer);
        }
        return answer;
    }

    @Override
    public String toString() {
        // the thread factory often have more precise details what the thread pool is used for
        if (delegate.getThreadFactory() instanceof CamelThreadFactory) {
            String name = ((CamelThreadFactory) delegate.getThreadFactory()).getName();
            return super.toString() + "[" + name + "]";
        } else {
            return super.toString();
        }
    }
}
