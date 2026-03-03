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
package org.apache.camel.support;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.spi.ThreadPoolFactory;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.concurrent.RejectableScheduledThreadPoolExecutor;
import org.apache.camel.util.concurrent.RejectableThreadPoolExecutor;
import org.apache.camel.util.concurrent.SizedScheduledExecutorService;
import org.apache.camel.util.concurrent.ThreadType;
import org.apache.camel.util.concurrent.ThreadFactoryTypeAware;

/**
 * Factory for thread pools that uses the JDK {@link Executors} for creating the thread pools.
 */
public class DefaultThreadPoolFactory extends ServiceSupport implements CamelContextAware, ThreadPoolFactory, StaticService {

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
        return ThreadPoolFactoryType.from(threadFactory, Integer.MAX_VALUE).newCachedThreadPool(threadFactory);
    }

    @Override
    public ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory factory) {
        // allow core thread timeout is default true if not configured
        boolean allow = profile.getAllowCoreThreadTimeOut() != null ? profile.getAllowCoreThreadTimeOut() : true;
        return newThreadPool(profile.getPoolSize(),
                profile.getMaxPoolSize(),
                profile.getKeepAliveTime(),
                profile.getTimeUnit(),
                profile.getMaxQueueSize(),
                allow,
                profile.getRejectedExecutionHandler(),
                factory);
    }

    public ExecutorService newThreadPool(
            int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, int maxQueueSize,
            boolean allowCoreThreadTimeOut,
            RejectedExecutionHandler rejectedExecutionHandler, ThreadFactory threadFactory)
            throws IllegalArgumentException {
        // the core pool size must be 0 or higher
        if (corePoolSize < 0) {
            throw new IllegalArgumentException("CorePoolSize must be >= 0, was " + corePoolSize);
        }

        // validate max >= core
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException(
                    "MaxPoolSize must be >= corePoolSize, was " + maxPoolSize + " >= " + corePoolSize);
        }
        return ThreadPoolFactoryType.from(threadFactory, corePoolSize, maxPoolSize, maxQueueSize).newThreadPool(
                corePoolSize, maxPoolSize, keepAliveTime, timeUnit, maxQueueSize, allowCoreThreadTimeOut,
                rejectedExecutionHandler, threadFactory);
    }

    @Override
    public ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
        return ThreadPoolFactoryType.from(threadFactory, profile).newScheduledThreadPool(profile, threadFactory);
    }

    private enum ThreadPoolFactoryType {
        PLATFORM {
            ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
                return Executors.newCachedThreadPool(threadFactory);
            }

            ExecutorService newThreadPool(
                    int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, int maxQueueSize,
                    boolean allowCoreThreadTimeOut,
                    RejectedExecutionHandler rejectedExecutionHandler, ThreadFactory threadFactory)
                    throws IllegalArgumentException {

                BlockingQueue<Runnable> workQueue;
                if (corePoolSize == 0 && maxQueueSize <= 0) {
                    // use a synchronous queue for direct-handover (no tasks stored on the queue)
                    workQueue = new SynchronousQueue<>();
                    // and force 1 as pool size to be able to create the thread pool by the JDK
                    corePoolSize = 1;
                    maxPoolSize = 1;
                } else if (maxQueueSize <= 0) {
                    // use a synchronous queue for direct-handover (no tasks stored on the queue)
                    workQueue = new SynchronousQueue<>();
                } else {
                    // bounded task queue to store tasks on the queue
                    workQueue = new LinkedBlockingQueue<>(maxQueueSize);
                }

                ThreadPoolExecutor answer
                        = new RejectableThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, workQueue);
                answer.setThreadFactory(threadFactory);
                answer.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
                if (rejectedExecutionHandler == null) {
                    rejectedExecutionHandler = new ThreadPoolExecutor.CallerRunsPolicy();
                }
                answer.setRejectedExecutionHandler(rejectedExecutionHandler);
                return answer;
            }

            ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
                RejectedExecutionHandler rejectedExecutionHandler = profile.getRejectedExecutionHandler();
                if (rejectedExecutionHandler == null) {
                    rejectedExecutionHandler = new ThreadPoolExecutor.CallerRunsPolicy();
                }

                ScheduledThreadPoolExecutor answer
                        = new RejectableScheduledThreadPoolExecutor(profile.getPoolSize(), threadFactory, rejectedExecutionHandler);
                answer.setRemoveOnCancelPolicy(true);

                // need to wrap the thread pool in a sized to guard against the problem that the
                // JDK created thread pool has an unbounded queue (see class javadoc), which mean
                // we could potentially keep adding tasks, and run out of memory.
                if (profile.getMaxPoolSize() > 0) {
                    return new SizedScheduledExecutorService(answer, profile.getMaxQueueSize());
                } else {
                    return answer;
                }
            }
        },
        VIRTUAL {
            @Override
            ExecutorService newCachedThreadPool(ThreadFactory threadFactory) {
                return Executors.newThreadPerTaskExecutor(threadFactory);
            }

            @Override
            ExecutorService newThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit,
                                          int maxQueueSize, boolean allowCoreThreadTimeOut,
                                          RejectedExecutionHandler rejectedExecutionHandler,
                                          ThreadFactory threadFactory) throws IllegalArgumentException {
                return Executors.newThreadPerTaskExecutor(threadFactory);
            }

            @Override
            ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory) {
                return Executors.newScheduledThreadPool(0, threadFactory);
            }
        };

        static ThreadPoolFactoryType from(ThreadFactory threadFactory, ThreadPoolProfile profile) {
            return from(threadFactory, profile.getPoolSize(), profile.getMaxPoolSize(), profile.getMaxQueueSize());
        }

        static ThreadPoolFactoryType from(ThreadFactory threadFactory, int corePoolSize, int maxPoolSize, int maxQueueSize) {
            return from(threadFactory, corePoolSize == 0 && maxQueueSize <= 0 ? 1 : maxPoolSize);
        }

        static ThreadPoolFactoryType from(ThreadFactory threadFactory, int maxPoolSize) {
            if (ThreadType.current() == ThreadType.PLATFORM) {
                return ThreadPoolFactoryType.PLATFORM;
            }
            return maxPoolSize > 1 && threadFactory instanceof ThreadFactoryTypeAware factoryTypeAware && factoryTypeAware.isVirtual() ?
                    ThreadPoolFactoryType.VIRTUAL : ThreadPoolFactoryType.PLATFORM;
        }

        abstract ExecutorService newCachedThreadPool(ThreadFactory threadFactory);

        abstract ExecutorService newThreadPool(
                int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit, int maxQueueSize,
                boolean allowCoreThreadTimeOut,
                RejectedExecutionHandler rejectedExecutionHandler, ThreadFactory threadFactory)
                throws IllegalArgumentException;

        abstract ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory);
    }
}
