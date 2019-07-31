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
package org.apache.camel.spi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/**
 * Creates ExecutorService and ScheduledExecutorService objects that work with a thread pool for a given ThreadPoolProfile and ThreadFactory.
 * 
 * This interface allows to customize the creation of these objects to adapt camel for application servers and other environments where thread pools
 * should not be created with the jdk methods
 */
public interface ThreadPoolFactory {

    /**
     * Creates a new cached thread pool
     * <p/>
     * The cached thread pool is a term from the JDK from the method {@link java.util.concurrent.Executors#newCachedThreadPool()}.
     * Typically it will have no size limit (this is why it is handled separately
     *
     * @param threadFactory factory for creating threads
     * @return the created thread pool
     */
    ExecutorService newCachedThreadPool(ThreadFactory threadFactory);
    
    /**
     * Create a thread pool using the given thread pool profile
     * 
     * @param profile parameters of the thread pool
     * @param threadFactory factory for creating threads
     * @return the created thread pool
     */
    ExecutorService newThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory);
    
    /**
     * Create a scheduled thread pool using the given thread pool profile
     * @param profile parameters of the thread pool
     * @param threadFactory factory for creating threads
     * @return the created thread pool
     */
    ScheduledExecutorService newScheduledThreadPool(ThreadPoolProfile profile, ThreadFactory threadFactory);
}