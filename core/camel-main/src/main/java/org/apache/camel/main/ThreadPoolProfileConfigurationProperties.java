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

import java.util.concurrent.TimeUnit;

import org.apache.camel.spi.Configurer;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

@Configurer(bootstrap = true)
public class ThreadPoolProfileConfigurationProperties {

    private String id;
    private Integer poolSize;
    private Integer maxPoolSize;
    private Long keepAliveTime;
    private TimeUnit timeUnit;
    private Integer maxQueueSize;
    private Boolean allowCoreThreadTimeOut;
    private ThreadPoolRejectedPolicy rejectedPolicy;

    public String getId() {
        return id;
    }

    /**
     * Sets the id of this thread pool
     */
    public void setId(String id) {
        this.id = id;
    }

    public Integer getPoolSize() {
        return poolSize;
    }

    /**
     * Sets the core pool size (threads to keep minimum in pool)
     */
    public void setPoolSize(Integer poolSize) {
        this.poolSize = poolSize;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Sets the maximum pool size
     */
    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public Long getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Sets the keep alive time for inactive threads
     */
    public void setKeepAliveTime(Long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the time unit used for keep alive time
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public Integer getMaxQueueSize() {
        return maxQueueSize;
    }

    /**
     * Sets the maximum number of tasks in the work queue.
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
     * Sets whether to allow core threads to timeout
     */
    public void setAllowCoreThreadTimeOut(Boolean allowCoreThreadTimeOut) {
        this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
    }

    public ThreadPoolRejectedPolicy getRejectedPolicy() {
        return rejectedPolicy;
    }

    /**
     * Sets the handler for tasks which cannot be executed by the thread pool.
     */
    public void setRejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        this.rejectedPolicy = rejectedPolicy;
    }

}
