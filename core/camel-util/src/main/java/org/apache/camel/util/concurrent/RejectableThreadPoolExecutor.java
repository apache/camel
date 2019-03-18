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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool executor that creates {@link RejectableFutureTask} instead of
 * {@link java.util.concurrent.FutureTask} when registering new tasks for execution.
 * <p/>
 * Instances of {@link RejectableFutureTask} are required to handle {@link ThreadPoolRejectedPolicy#Discard}
 * and {@link ThreadPoolRejectedPolicy#DiscardOldest} policies correctly, e.g. notify
 * {@link Callable} and {@link Runnable} tasks when they are rejected.
 * To be notified of rejection tasks have to implement {@link Rejectable} interface: <br/>
 * <code><pre>
 * public class RejectableTask implements Runnable, Rejectable {
 *     &#064;Override
 *     public void run() {
 *         // execute task
 *     }
 *     &#064;Override
 *     public void reject() {
 *         // do something useful on rejection
 *     }
 * }
 * </pre></code>
 * <p/>
 * If the task does not implement {@link Rejectable} interface the behavior is exactly the same as with
 * ordinary {@link ThreadPoolExecutor}.
 *
 * @see RejectableFutureTask
 * @see Rejectable
 * @see RejectableScheduledThreadPoolExecutor
 */
public class RejectableThreadPoolExecutor extends ThreadPoolExecutor {

    public RejectableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                        BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public RejectableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                        BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public RejectableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                        BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public RejectableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit,
                                        BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        if (runnable instanceof Rejectable) {
            return new RejectableFutureTask<>(runnable, value);
        } else {
            return super.newTaskFor(runnable, value);
        }
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        if (callable instanceof Rejectable) {
            return new RejectableFutureTask<>(callable);
        } else {
            return super.newTaskFor(callable);
        }
    }

    @Override
    public String toString() {
        // the thread factory often have more precise details what the thread pool is used for
        if (getThreadFactory() instanceof CamelThreadFactory) {
            String name = ((CamelThreadFactory) getThreadFactory()).getName();
            return super.toString() + "[" + name + "]";
        } else {
            return super.toString();
        }
    }

}
