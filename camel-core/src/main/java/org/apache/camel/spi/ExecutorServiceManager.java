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
 * @version 
 */
public interface ExecutorServiceManager extends ShutdownableService {
    
    String resolveThreadName(String name);

    ThreadPoolProfile getThreadPoolProfile(String id);

    /**
     * Registers the given thread pool profile
     *
     * @param profile the profile
     */
    void registerThreadPoolProfile(ThreadPoolProfile profile);
    /**
     * Sets the default thread pool profile
     *
     * @param defaultThreadPoolProfile the new default thread pool profile
     */
    void setDefaultThreadPoolProfile(ThreadPoolProfile defaultThreadPoolProfile);
    
    ThreadPoolProfile getDefaultThreadPoolProfile();

    /**
     * Sets the thread name pattern used for creating the full thread name.
     * <p/>
     * The default pattern is: <tt>Camel (${camelId}) thread #${counter} - ${name}</tt>
     * <p/>
     * Where <tt>${camelId}</tt> is the name of the {@link org.apache.camel.CamelContext}
     * <br/>and <tt>${counter}</tt> is a unique incrementing counter.
     * <br/>and <tt>${name}</tt> is the regular thread name.
     * <br/>You can also use <tt>${longName}</tt> is the long thread name which can includes endpoint parameters etc.
     *
     * @param pattern  the pattern
     * @throws IllegalArgumentException if the pattern is invalid.
     */
    void setThreadNamePattern(String pattern) throws IllegalArgumentException;
    
    /**
     * Creates an executorservice with a default thread pool
     * 
     * @param ref
     * @param source
     * @return
     */
    ExecutorService getDefaultExecutorService(String ref, Object source);
    
    ExecutorService getExecutorService(ThreadPoolProfile profile, Object source);
    
    ExecutorService createExecutorService(ThreadPoolProfile profile, Object source);
    
    ScheduledExecutorService getScheduledExecutorService(String ref, Object source);
    
    ScheduledExecutorService getScheduledExecutorService(ThreadPoolProfile profile, Object source);

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

    
    ExecutorService newCachedThreadPool(Object source, String name);

    ExecutorService newSynchronousExecutorService(String string, Object source);
}
