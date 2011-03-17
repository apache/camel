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
package org.apache.camel.builder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.model.ThreadPoolProfileDefinition;
import org.apache.camel.util.CamelContextHelper;

/**
 * A builder to create thread pools.
 *
 * @version 
 */
public final class ThreadPoolBuilder {

    private final CamelContext camelContext;
    private ThreadPoolProfileDefinition threadPoolDefinition;

    public ThreadPoolBuilder(CamelContext camelContext) {
        this.camelContext = camelContext;
        // use the default thread profile as the base
        this.threadPoolDefinition = new ThreadPoolProfileDefinition(camelContext.getExecutorServiceStrategy().getDefaultThreadPoolProfile());
    }

    public ThreadPoolBuilder poolSize(int poolSize) {
        threadPoolDefinition.poolSize(poolSize);
        return this;
    }

    public ThreadPoolBuilder maxPoolSize(int maxPoolSize) {
        threadPoolDefinition.maxPoolSize(maxPoolSize);
        return this;
    }

    public ThreadPoolBuilder keepAliveTime(long keepAliveTime) {
        threadPoolDefinition.keepAliveTime(keepAliveTime);
        return this;
    }

    public ThreadPoolBuilder timeUnit(TimeUnit timeUnit) {
        threadPoolDefinition.timeUnit(timeUnit);
        return this;
    }

    public ThreadPoolBuilder maxQueueSize(int maxQueueSize) {
        threadPoolDefinition.maxQueueSize(maxQueueSize);
        return this;
    }

    public ThreadPoolBuilder rejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        threadPoolDefinition.rejectedPolicy(rejectedPolicy);
        return this;
    }

    /**
     * Builds the new thread pool
     *
     * @param name name which is appended to the thread name
     * @return the created thread pool
     * @throws Exception is thrown if error building the thread pool
     */
    public ExecutorService build(String name) throws Exception {
        return build(null, name);
    }

    /**
     * Builds the new thread pool
     *
     * @param source the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name   name which is appended to the thread name
     * @return the created thread pool
     * @throws Exception is thrown if error building the thread pool
     */
    public ExecutorService build(Object source, String name) throws Exception {
        int size = CamelContextHelper.parseInteger(camelContext, threadPoolDefinition.getPoolSize());
        int max = CamelContextHelper.parseInteger(camelContext, threadPoolDefinition.getMaxPoolSize());
        long keepAlive = CamelContextHelper.parseLong(camelContext, threadPoolDefinition.getKeepAliveTime());
        int queueSize = CamelContextHelper.parseInteger(camelContext, threadPoolDefinition.getMaxQueueSize());
        TimeUnit unit = threadPoolDefinition.getTimeUnit();
        RejectedExecutionHandler handler = threadPoolDefinition.getRejectedExecutionHandler();

        ExecutorService answer = camelContext.getExecutorServiceStrategy().newThreadPool(source, name,
                size, max, keepAlive, unit, queueSize, handler, true);
        return answer;
    }

}
