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

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Strategy to create thread pools.
 * <p/>
 * This strategy is pluggable so you can plugin a custom provider, for example if you want to leverage
 * the WorkManager for a J2EE server.
 * <p/>
 * This strategy has fine grained methods for creating various thread pools, however custom strategies
 * do not have to exactly create those kind of pools. Feel free to return a shared or different kind of pool.
 * <p/>
 * However there are two types of pools: regular and scheduled.
 *
 * @version $Revision$
 */
public interface ExecutorServiceStrategy {

    /**
     * Creates a full thread name
     *
     * @param nameSuffix  suffix which is appended to the thread name
     * @return the full thread name
     */
    String getThreadName(String nameSuffix);

    /**
     * Lookup a {@link java.util.concurrent.ExecutorService} from the {@link org.apache.camel.spi.Registry}.
     *
     * @param executorServiceRef  reference to lookup
     * @return the {@link java.util.concurrent.ExecutorService} or <tt>null</tt> if not found
     */
    ExecutorService lookup(String executorServiceRef);

    /**
     * Creates a new cached thread pool.
     *
     * @param nameSuffix  suffix which is appended to the thread name
     * @return the thread pool
     */
    ExecutorService newCachedThreadPool(String nameSuffix);

    /**
     * Creates a new scheduled thread pool.
     *
     * @param nameSuffix  suffix which is appended to the thread name
     * @param poolSize    the core pool size
     * @return the thread pool
     */
    ScheduledExecutorService newScheduledThreadPool(String nameSuffix, int poolSize);

    /**
     * Creates a new fixed thread pool.
     *
     * @param nameSuffix  suffix which is appended to the thread name
     * @param poolSize    the core pool size
     * @return the thread pool
     */
    ExecutorService newFixedThreadPool(String nameSuffix, int poolSize);

    /**
     * Creates a new single-threaded thread pool. This is often used for background threads.
     *
     * @param nameSuffix  suffix which is appended to the thread name
     * @return the thread pool
     */
    ExecutorService newSingleThreadExecutor(String nameSuffix);

    /**
     * Creates a new custom thread pool.
     * <p/>
     * Will by default use 60 seconds for keep alive time for idle threads.
     *
     * @param nameSuffix    suffix which is appended to the thread name
     * @param corePoolSize  the core pool size
     * @param maxPoolSize   the maximum pool size
     * @return the thread pool
     */
    ExecutorService newThreadPool(String nameSuffix, int corePoolSize, int maxPoolSize);

    /**
     * Creates a new custom thread pool.
     *
     * @param nameSuffix    suffix which is appended to the thread name
     * @param corePoolSize  the core pool size
     * @param maxPoolSize   the maximum pool size
     * @param keepAliveTime keep alive time for idle threads
     * @param timeUnit      time unit for keep alive time
     * @param daemon        whether or not the created threads is daemon or not
     * @return the thread pool
     */
    ExecutorService newThreadPool(final String nameSuffix, int corePoolSize, int maxPoolSize,
                                  long keepAliveTime, TimeUnit timeUnit, boolean daemon);

    /**
     * Shutdown the given executor service.
     *
     * @param executorService the executor service to shutdown
     * @see java.util.concurrent.ExecutorService#shutdown()
     */
    void shutdown(ExecutorService executorService);

    /**
     * Shutdown now the given executor service.
     *
     * @param executorService the executor service to shutdown now
     * @return list of tasks that never commenced execution
     * @see java.util.concurrent.ExecutorService#shutdownNow()
     */
    List<Runnable> shutdownNow(ExecutorService executorService);

}
