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

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ThreadPoolRejectedPolicy;

/**
 * A profile which defines thread pool settings.
 *
 * @version 
 */
public class ThreadPoolProfile {

    private String id;
    private Boolean defaultProfile;
    private Integer poolSize;
    private Integer maxPoolSize;
    private Long keepAliveTime;
    private TimeUnit timeUnit;
    private Integer maxQueueSize;
    private ThreadPoolRejectedPolicy rejectedPolicy;
    private Boolean shared;
    private Boolean daemon;
    private String threadName;

    public ThreadPoolProfile() {
    }
    
    public ThreadPoolProfile(String id) {
        this.id = id;
        this.threadName = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    /**
     * Whether this profile is the default profile (there can only be one).
     *
     * @return <tt>true</tt> if its the default profile, <tt>false</tt> otherwise
     */
    public Boolean isDefaultProfile() {
        return defaultProfile != null && defaultProfile;
    }

    /**
     * Sets whether this profile is the default profile (there can only be one).
     *
     * @param defaultProfile the option
     */
    public void setDefaultProfile(Boolean defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    /**
     * Gets the core pool size (threads to keep minimum in pool)
     *
     * @return the pool size
     */
    public Integer getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(Integer poolSize) {
        this.poolSize = poolSize;
    }

    /**
     * Gets the maximum pool size
     *
     * @return the maximum pool size
     */
    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    /**
     * Sets the core pool size (threads to keep minimum in pool)
     *
     * @param poolSize the pool size
     */
    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    /**
     * Gets the keep alive time for inactive threads
     *
     * @return the keep alive time
     */
    public Long getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * Sets the keep alive time for inactive threads
     *
     * @param keepAliveTime the keep alive time
     */
    public void setKeepAliveTime(Long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    /**
     * Sets the time unit used for keep alive time
     *
     * @param timeUnit the time unit
     */
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Gets the time unit used for keep alive time
     *
     * @return the time unit
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    /**
     * Gets the maximum number of tasks in the work queue.
     * <p/>
     * Use <tt>-1</tt> or <tt>Integer.MAX_VALUE</tt> for an unbounded queue
     *
     * @return the max queue size
     */
    public Integer getMaxQueueSize() {
        return maxQueueSize;
    }

    /**
     * Sets the maximum number of tasks in the work queue.
     * <p/>
     * Use <tt>-1</tt> or <tt>Integer.MAX_VALUE</tt> for an unbounded queue
     *
     * @param maxQueueSize the max queue size
     */
    public void setMaxQueueSize(Integer maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    /**
     * Gets the handler for tasks which cannot be executed by the thread pool.
     *
     * @return the policy for the handler
     */
    public ThreadPoolRejectedPolicy getRejectedPolicy() {
        return rejectedPolicy;
    }

    /**
     * Gets the handler for tasks which cannot be executed by the thread pool.
     *
     * @return the handler, or <tt>null</tt> if none defined
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        if (rejectedPolicy != null) {
            return rejectedPolicy.asRejectedExecutionHandler();
        }
        return null;
    }

    /**
     * Sets the handler for tasks which cannot be executed by the thread pool.
     *
     * @param rejectedPolicy  the policy for the handler
     */
    public void setRejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        this.rejectedPolicy = rejectedPolicy;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    @Override
    public String toString() {
        return "ThreadPoolProfile[" + id + ", " + defaultProfile + ", " + poolSize + ", " + maxPoolSize + ", "
                + keepAliveTime + " " + timeUnit + ", " + maxPoolSize + ", " + rejectedPolicy + "]";
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }
    
    public boolean isDaemon() {
        return daemon == null ? false : daemon;
    }

    public ThreadPoolProfile getEffectiveProfile(ThreadPoolProfile profile) {
        ThreadPoolProfile defaultProfile = this;
        ThreadPoolProfile eProfile = new ThreadPoolProfile();
        eProfile.setPoolSize(profile.getPoolSize() != null ? profile.getPoolSize() : defaultProfile.getPoolSize());
        eProfile.setMaxPoolSize(profile.getMaxPoolSize() != null ? profile.getMaxPoolSize() : defaultProfile.getMaxPoolSize());
        eProfile.setKeepAliveTime(profile.getKeepAliveTime() != null ? profile.getKeepAliveTime() : defaultProfile.getKeepAliveTime());
        eProfile.setTimeUnit(profile.getTimeUnit() != null ? profile.getTimeUnit() : defaultProfile.getTimeUnit());
        eProfile.setMaxQueueSize(profile.getMaxQueueSize() != null ? profile.getMaxQueueSize() : defaultProfile.getMaxQueueSize());
        eProfile.setRejectedPolicy(profile.getRejectedPolicy() != null ? profile.getRejectedPolicy() : defaultProfile.getRejectedPolicy());
        return eProfile;
    }

    /**
     * Overwrites each attribute that is null with the attribute from defaultProfile 
     * 
     * @param defaultProfile2
     */
    public void addDefaults(ThreadPoolProfile defaultProfile2) {
        if (defaultProfile2 == null) {
            return;
        }
        if (poolSize == null) {
            poolSize = defaultProfile2.getPoolSize();
        }
        if (maxPoolSize == null) {
            maxPoolSize = defaultProfile2.getMaxPoolSize();
        }
        if (keepAliveTime == null) {
            keepAliveTime = defaultProfile2.getKeepAliveTime();
        }
        if (timeUnit == null) {
            timeUnit = defaultProfile2.getTimeUnit();
        }
        if (maxQueueSize == null) {
            maxQueueSize = defaultProfile2.getMaxQueueSize();
        }
        if (rejectedPolicy == null) {
            rejectedPolicy = defaultProfile2.getRejectedPolicy();
        }
    }
}
