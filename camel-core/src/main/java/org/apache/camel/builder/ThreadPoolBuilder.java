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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.spi.ThreadPoolProfile;

/**
 * A builder to create thread pools.
 *
 * @version 
 */
public final class ThreadPoolBuilder {

    private ThreadPoolProfile profile;
    
    @Deprecated
    private CamelContext context;

    @Deprecated
    public ThreadPoolBuilder(CamelContext context) {
        this.context = context;
        this.profile = new ThreadPoolProfile();
    }
    
    public ThreadPoolBuilder(String name) {
        this.profile = new ThreadPoolProfile(name);
    }
    
    public ThreadPoolBuilder defaultProfile(Boolean defaultProfile) {
        profile.setDefaultProfile(defaultProfile);
        return this;
    }

    public ThreadPoolBuilder poolSize(Integer poolSize) {
        profile.setPoolSize(poolSize);
        return this;
    }

    public ThreadPoolBuilder maxPoolSize(Integer maxPoolSize) {
        profile.setMaxPoolSize(maxPoolSize);
        return this;
    }
    
    public ThreadPoolBuilder keepAliveTime(Integer keepAliveTime) {
        profile.setKeepAliveTime(keepAliveTime.longValue());
        return this;
    }

    public ThreadPoolBuilder keepAliveTime(Long keepAliveTime) {
        profile.setKeepAliveTime(keepAliveTime);
        return this;
    }
    
    public ThreadPoolBuilder keepAliveTime(Integer keepAliveTime, TimeUnit timeUnit) {
        if (keepAliveTime != null) {
            profile.setKeepAliveTime(keepAliveTime.longValue());
        }
        profile.setTimeUnit(timeUnit);
        return this;
    }
    
    public ThreadPoolBuilder keepAliveTime(Long keepAliveTime, TimeUnit timeUnit) {
        profile.setKeepAliveTime(keepAliveTime);
        profile.setTimeUnit(timeUnit);
        return this;
    }

    public ThreadPoolBuilder timeUnit(TimeUnit timeUnit) {
        profile.setTimeUnit(timeUnit);
        return this;
    }

    public ThreadPoolBuilder maxQueueSize(Integer maxQueueSize) {
        profile.setMaxQueueSize(maxQueueSize);
        return this;
    }

    public ThreadPoolBuilder rejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        profile.setRejectedPolicy(rejectedPolicy);
        return this;
    }
    
    public ThreadPoolBuilder daemon() {
        profile.setDaemon(true);
        return this;
    }
    
    public ThreadPoolBuilder daemon(Boolean daemon) {
        profile.setDaemon(daemon);
        return this;
    }
    

    public ThreadPoolBuilder threadName(String name) {
        profile.setThreadName(name);
        return this;
    }


    public ThreadPoolProfile build() {
        return this.profile;
    }

    /**
     * Builds the new thread pool
     * @deprecated use build instead and fetch the ExecutorService from the ExecutorServiceManager 
     *
     * @param name name which is appended to the thread name
     * @return the created thread pool
     * @throws Exception is thrown if error building the thread pool
     */
    @Deprecated
    public ExecutorService build(String name) throws Exception {
        return build(null, name);
    }

    /**
     * Builds the new thread pool
     * @deprecated use build instead and fetch the ExecutorService from the ExecutorServiceManager
     *
     * @param source the source object, usually it should be <tt>this</tt> passed in as parameter
     * @param name   name which is appended to the thread name
     * @return the created thread pool
     * @throws Exception is thrown if error building the thread pool
     */
    @Deprecated
    public ExecutorService build(Object source, String name) throws Exception {
        profile.setId(name);
        return context.getExecutorServiceManager().getExecutorService(profile, source);

    }


    public static ThreadPoolProfile singleThreadExecutor(String id) {
        return new ThreadPoolBuilder(id).poolSize(1).maxPoolSize(1).build();
    }
    
    public static ThreadPoolProfile fixedThreadExecutor(String id, int poolSize) {
        return new ThreadPoolBuilder(id)
            .poolSize(poolSize)
            .maxPoolSize(poolSize)
            .keepAliveTime(0L, TimeUnit.MILLISECONDS)
            .build();
    }

}
