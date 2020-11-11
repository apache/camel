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
package org.apache.camel.main;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

/**
 * Global configuration for thread pools
 */
@Configurer(bootstrap = true)
public class ThreadPoolConfigurationProperties implements BootstrapCloseable {

    private MainConfigurationProperties parent;

    // default values
    private Integer poolSize;
    private Integer maxPoolSize;
    private Long keepAliveTime;
    private TimeUnit timeUnit;
    private Integer maxQueueSize;
    private Boolean allowCoreThreadTimeOut;
    private ThreadPoolRejectedPolicy rejectedPolicy;

    // profile specific values
    private Map<String, ThreadPoolProfileConfigurationProperties> config = new HashMap<>();

    public ThreadPoolConfigurationProperties(MainConfigurationProperties parent) {
        this.parent = parent;
    }

    public MainConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        parent = null;
        config.clear();
        config = null;
    }

    public Integer getPoolSize() {
        return poolSize;
    }

    /**
     * Sets the default core pool size (threads to keep minimum in pool)
     */
    public void setPoolSize(Integer poolSize) {
        this.poolSize = poolSize;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Sets the default maximum pool size
     */
    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public Long getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Sets the default keep alive time for inactive threads
     */
    public void setKeepAliveTime(Long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the default time unit used for keep alive time
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public Integer getMaxQueueSize() {
        return maxQueueSize;
    }

    /**
     * Sets the default maximum number of tasks in the work queue.
     *
     * Use -1 or an unbounded queue
     */
    public void setMaxQueueSize(Integer maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public Boolean getAllowCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets default whether to allow core threads to timeout
     */
    public void setAllowCoreThreadTimeOut(Boolean allowCoreThreadTimeOut) {
        this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
    }

    public ThreadPoolRejectedPolicy getRejectedPolicy() {
        return rejectedPolicy;
    }

    /**
     * Sets the default handler for tasks which cannot be executed by the thread pool.
     */
    public void setRejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        this.rejectedPolicy = rejectedPolicy;
    }

    public Map<String, ThreadPoolProfileConfigurationProperties> getConfig() {
        return config;
    }

    /**
     * Adds a configuration for a specific thread pool profile (inherits default values)
     */
    public void setConfig(Map<String, ThreadPoolProfileConfigurationProperties> config) {
        this.config = config;
    }

    /**
     * Adds a configuration for a specific thread pool profile (inherits default values)
     */
    public ThreadPoolConfigurationProperties addConfig(String id, ThreadPoolProfileConfigurationProperties config) {
        this.config.put(id, config);
        return this;
    }

}
