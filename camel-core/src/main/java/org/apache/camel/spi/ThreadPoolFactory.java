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
package org.apache.camel.spi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Factory to crate {@link ExecutorService} and {@link ScheduledExecutorService} instances
 * <p/>
 * This interface allows to customize the creation of these objects to adapt Camel
 * for application servers and other environments where thread pools should
 * not be created with the JDK methods, as provided by the {@link org.apache.camel.impl.DefaultThreadPoolFactory}.
 *
 * @see ExecutorServiceManager
 */
public interface ThreadPoolFactory {

    /**
     * Creates a new cached thread pool
     * <p/>
     * The cached thread pool is a term from the JDK from the method {@link java.util.concurrent.Executors#newCachedThreadPool()}.
     * Implementators of this interface, may create a different kind of pool than the cached, or check the source code
     * of the JDK to create a pool using the same settings.
     *
     * @param threadFactory factory for creating threads
     * @return the created thread pool
     */
    ExecutorService newCachedThreadPool(ThreadFactory threadFactory);

    /**
     * Creates a new fixed thread pool
     * <p/>
     * The fixed thread pool is a term from the JDK from the method {@link java.util.concurrent.Executors#newFixedThreadPool(int)}.
     * Implementators of this interface, may create a different kind of pool than the fixed, or check the source code
     * of the JDK to create a pool using the same settings.
     *
     * @param poolSize  the number of threads in the pool
     * @param threadFactory factory for creating threads
     * @return the created thread pool
     */
    ExecutorService newFixedThreadPool(int poolSize, ThreadFactory threadFactory);

    /**
     * Creates a new scheduled thread pool
     *
     * @param corePoolSize  the core pool size
     * @param threadFactory factory for creating threads
     * @return the created thread pool
     * @throws IllegalArgumentException if parameters is not valid
     */
    ScheduledExecutorService newScheduledThreadPool(int corePoolSize, ThreadFactory threadFactory) throws IllegalArgumentException;

    /**
     * Creates a new thread pool
     *
     * @param corePoolSize             the core pool size
     * @param maxPoolSize              the maximum pool size
     * @param keepAliveTime            keep alive time
     * @param timeUnit                 keep alive time unit
     * @param maxQueueSize             the maximum number of tasks in the queue, use <tt>Integer.MAX_VALUE</tt> or <tt>-1</tt> to indicate unbounded
     * @param rejectedExecutionHandler the handler for tasks which cannot be executed by the thread pool.
     *                                 If <tt>null</tt> is provided then {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy CallerRunsPolicy} is used.
     * @param threadFactory            factory for creating threads
     * @return the created thread pool
     * @throws IllegalArgumentException if parameters is not valid
     */
    ExecutorService newThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit timeUnit,
                                  int maxQueueSize, RejectedExecutionHandler rejectedExecutionHandler,
                                  ThreadFactory threadFactory) throws IllegalArgumentException;

}
