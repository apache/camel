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
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ShutdownableService;

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
 * <p/>
 * If you use the <tt>newXXX</tt> methods to create thread pools, then Camel will by default take care of
 * shutting down those created pools when {@link org.apache.camel.CamelContext} is shutting down.
 *
 * @deprecated use {@link ExecutorServiceManager} instead, will be removed in a future Camel release
 */
@Deprecated
public interface ExecutorServiceStrategy extends ShutdownableService {

    /**
     * Registers the given thread pool profile
     *
     * @param profile the profile
     */
    void registerThreadPoolProfile(ThreadPoolProfile profile);

    /**
     * Gets the thread pool profile by the given id
     *
     * @param id  id of the thread pool profile to get
     * @return the found profile, or <tt>null</tt> if not found
     */
    ThreadPoolProfile getThreadPoolProfile(String id);

    /**
     * Gets the default thread pool profile
     *
     * @return the default profile which are newer <tt>null</tt>
     */
    ThreadPoolProfile getDefaultThreadPoolProfile();

    /**
     * Sets the default thread pool profile
     *
     * @param defaultThreadPoolProfile the new default thread pool profile
     */
    void setDefaultThreadPoolProfile(ThreadPoolProfile defaultThreadPoolProfile);

    /**
     * Creates a full thread name
     *
     * @param name  name which is appended to the full thread name
     * @return the full thread name
     */
    String getThreadName(String name);

    /**
     * Gets the thread name pattern used for creating the full thread name.
     *
     * @return the pattern
     */
    String getThreadNamePattern();

    /**
     * Sets the thread name pattern used for creating the full thread name.
     * <p/>
     * The default pattern is: <tt>Camel (#camelId#) thread #counter# - #name#</tt>
     * <p/>
     * Where <tt>#camelId#</tt> is the name of the {@link org.apache.camel.CamelContext}
     * <br/>and <tt>#counter#</tt> is a unique incrementing counter.
     * <br/>and <tt>#name#</tt> is the regular thread name.
     * <br/>You can also use <tt>#longName#</tt> is the long thread name which can includes endpoint parameters etc.
     *
     * @param pattern  the pattern
     * @throws IllegalArgumentException if the pattern is invalid.
     */
    void setThreadNamePattern(String pattern) throws IllegalArgumentException;

    /**
     * Lookup a {@link java.util.concurrent.ExecutorService} from the {@link org.apache.camel.spi.Registry}
     * and from known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.
     *
     * @param source               the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name                 name which is appended to the thread name
     * @param executorServiceRef   reference to lookup
     * @return the {@link java.util.concurrent.ExecutorService} or <tt>null</tt> if not found
     */
    ExecutorService lookup(Object source, String name, String executorServiceRef);

    /**
     * Lookup a {@link java.util.concurrent.ScheduledExecutorService} from the {@link org.apache.camel.spi.Registry}
     * and from known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.
     *
     * @param source               the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name                 name which is appended to the thread name
     * @param executorServiceRef   reference to lookup
     * @return the {@link java.util.concurrent.ScheduledExecutorService} or <tt>null</tt> if not found
     */
    ScheduledExecutorService lookupScheduled(Object source, String name, String executorServiceRef);

    /**
     * Creates a new thread pool using the default thread pool profile.
     *
     * @param source      the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name        name which is appended to the thread name
     * @return the created thread pool
     */
    ExecutorService newDefaultThreadPool(Object source, String name);

    /**
     * Creates a new thread pool using based on the given profile id.
     *
     * @param source                the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name                  name which is appended to the thread name
     * @param threadPoolProfileId   id of the thread pool profile to use for creating the thread pool
     * @return the created thread pool, or <tt>null</tt> if the was no thread pool profile with that given id.
     */
    ExecutorService newThreadPool(Object source, String name, String threadPoolProfileId);

    /**
     * Creates a new cached thread pool.
     * <p/>
     * <b>Important:</b> Using cached thread pool is discouraged as they have no upper bound and can overload the JVM.
     *
     * @param source      the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name        name which is appended to the thread name
     * @return the created thread pool
     */
    ExecutorService newCachedThreadPool(Object source, String name);

    /**
     * Creates a new scheduled thread pool.
     *
     * @param source      the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name        name which is appended to the thread name
     * @param poolSize    the core pool size
     * @return the created thread pool
     */
    ScheduledExecutorService newScheduledThreadPool(Object source, String name, int poolSize);

    /**
     * Creates a new scheduled thread pool.
     * <p/>
     * Will use the pool size from the default thread pool profile
     *
     * @param source      the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name        name which is appended to the thread name
     * @return the created thread pool
     */
    ScheduledExecutorService newScheduledThreadPool(Object source, String name);

    /**
     * Creates a new fixed thread pool.
     *
     * @param source      the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name        name which is appended to the thread name
     * @param poolSize    the core pool size
     * @return the created thread pool
     */
    ExecutorService newFixedThreadPool(Object source, String name, int poolSize);

    /**
     * Creates a new single-threaded thread pool. This is often used for background threads.
     *
     * @param source      the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name        name which is appended to the thread name
     * @return the created thread pool
     */
    ExecutorService newSingleThreadExecutor(Object source, String name);

    /**
     * Creates a new synchronous thread pool, which executes the task in the caller thread (no task queue).
     *
     * @param source      the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name        name which is appended to the thread name
     * @return the created thread pool
     */
    ExecutorService newSynchronousThreadPool(Object source, String name);

    /**
     * Creates a new custom thread pool.
     * <p/>
     * Will by default use 60 seconds for keep alive time for idle threads.
     * And use {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy CallerRunsPolicy} as rejection handler
     *
     * @param source        the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name          name which is appended to the thread name
     * @param corePoolSize  the core pool size
     * @param maxPoolSize   the maximum pool size
     * @return the created thread pool
     */
    ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize);

    /**
     * Creates a new custom thread pool.
     * <p/>
     * Will by default use 60 seconds for keep alive time for idle threads.
     * And use {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy CallerRunsPolicy} as rejection handler
     *
     * @param source        the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name          name which is appended to the thread name
     * @param corePoolSize  the core pool size
     * @param maxPoolSize   the maximum pool size
     * @param maxQueueSize  the maximum number of tasks in the queue, use <tt>Integer.MAX_INT</tt> or <tt>-1</tt> to indicate unbounded
     * @return the created thread pool
     */
    ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize, int maxQueueSize);

    /**
     * Creates a new custom thread pool.
     *
     * @param source                     the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name                       name which is appended to the thread name
     * @param corePoolSize               the core pool size
     * @param maxPoolSize                the maximum pool size
     * @param keepAliveTime              keep alive time for idle threads
     * @param timeUnit                   time unit for keep alive time
     * @param maxQueueSize               the maximum number of tasks in the queue, use <tt>Integer.MAX_INT</tt> or <tt>-1</tt> to indicate unbounded
     * @param rejectedExecutionHandler   the handler for tasks which cannot be executed by the thread pool.
     *                                   If <tt>null</tt> is provided then {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy CallerRunsPolicy} is used.
     * @param daemon                     whether or not the created threads is daemon or not
     * @return the created thread pool
     */
    ExecutorService newThreadPool(Object source, String name, int corePoolSize, int maxPoolSize,
                                  long keepAliveTime, TimeUnit timeUnit, int maxQueueSize,
                                  RejectedExecutionHandler rejectedExecutionHandler, boolean daemon);

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
