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
package org.apache.camel.component.jms;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Hints what type of default task executor our {@link DefaultJmsMessageListenerContainer} should use.
 *
 * @since 2.10.3
 */
public enum DefaultTaskExecutorType {

    /**
     * Use a {@link ThreadPoolTaskExecutor} as the underlying task executor for consuming messages.
     * It will be configured with these attributes:
     * <p/>
     * <li>
     *   <ul>{@code corePoolSize} = concurrentConsumers</ul>
     *   <ul>{@code queueSize} = 0 (to use the 'direct handoff' strategy for growing the thread pool,
     *       see Javadoc of {@link ThreadPoolExecutor}.</ul>
     *   <ul>{@code maxPoolSize}, default value, i.e. no upper bound (as concurrency should be limited by
     *       the endpoint's maxConcurrentConsumers, not by the thread pool).</ul>
     * </li>
     */
    ThreadPool,

    /**
     * Use a {@link SimpleAsyncTaskExecutor} as the underlying task executor for consuming messages.
     * (Legacy mode - default behaviour before version 2.10.3).
     */
    SimpleAsync
}
