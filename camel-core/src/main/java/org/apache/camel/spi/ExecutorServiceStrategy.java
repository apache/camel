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
 * @version $Revision$
 */
public interface ExecutorServiceStrategy {

    ExecutorService lookup(String executorServiceRef);

    ExecutorService newCachedThreadPool(String name);

    ScheduledExecutorService newScheduledThreadPool(String name, int poolSize);

    ExecutorService newFixedThreadPool(String name, int poolSize);

    ExecutorService newSingleThreadExecutor(String name);

    ExecutorService newThreadPool(String name, int corePoolSize, int maxPoolSize);

    ExecutorService newThreadPool(final String name, int corePoolSize, int maxPoolSize,
                                  long keepAliveTime, TimeUnit timeUnit, boolean daemon);

    void shutdown(ExecutorService executorService);

    List<Runnable> shutdownNow(ExecutorService executorService);

}
