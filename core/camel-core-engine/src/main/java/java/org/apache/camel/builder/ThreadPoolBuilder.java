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
package org.apache.camel.builder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

/**
 * A builder to create thread pools.
 */
public final class ThreadPoolBuilder {

    // reuse a profile to store the settings
    private final ThreadPoolProfile profile;
    private final CamelContext context;

    public ThreadPoolBuilder(CamelContext context) {
        this.context = context;
        this.profile = new ThreadPoolProfile();
    }

    public ThreadPoolBuilder poolSize(int poolSize) {
        profile.setPoolSize(poolSize);
        return this;
    }

    public ThreadPoolBuilder maxPoolSize(int maxPoolSize) {
        profile.setMaxPoolSize(maxPoolSize);
        return this;
    }

    public ThreadPoolBuilder keepAliveTime(long keepAliveTime, TimeUnit timeUnit) {
        profile.setKeepAliveTime(keepAliveTime);
        profile.setTimeUnit(timeUnit);
        return this;
    }

    public ThreadPoolBuilder keepAliveTime(long keepAliveTime) {
        profile.setKeepAliveTime(keepAliveTime);
        return this;
    }

    public ThreadPoolBuilder maxQueueSize(int maxQueueSize) {
        profile.setMaxQueueSize(maxQueueSize);
        return this;
    }

    public ThreadPoolBuilder rejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        profile.setRejectedPolicy(rejectedPolicy);
        return this;
    }

    /**
     * Builds the new thread pool
     *
     * @return the created thread pool
     * @throws Exception is thrown if error building the thread pool
     */
    public ExecutorService build() throws Exception {
        return build(null, null);
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
     * @param source the source object, usually it should be <tt>this</tt>
     *            passed in as parameter
     * @param name name which is appended to the thread name
     * @return the created thread pool
     * @throws Exception is thrown if error building the thread pool
     */
    public ExecutorService build(Object source, String name) throws Exception {
        return context.getExecutorServiceManager().newThreadPool(source, name, profile);
    }

    /**
     * Builds the new scheduled thread pool
     *
     * @return the created scheduled thread pool
     * @throws Exception is thrown if error building the scheduled thread pool
     */
    public ScheduledExecutorService buildScheduled() throws Exception {
        return buildScheduled(null, null);
    }

    /**
     * Builds the new scheduled thread pool
     *
     * @param name name which is appended to the thread name
     * @return the created scheduled thread pool
     * @throws Exception is thrown if error building the scheduled thread pool
     */
    public ScheduledExecutorService buildScheduled(String name) throws Exception {
        return buildScheduled(null, name);
    }

    /**
     * Builds the new scheduled thread pool
     *
     * @param source the source object, usually it should be <tt>this</tt>
     *            passed in as parameter
     * @param name name which is appended to the thread name
     * @return the created scheduled thread pool
     * @throws Exception is thrown if error building the scheduled thread pool
     */
    public ScheduledExecutorService buildScheduled(Object source, String name) throws Exception {
        return context.getExecutorServiceManager().newScheduledThreadPool(source, name, profile);
    }

}
