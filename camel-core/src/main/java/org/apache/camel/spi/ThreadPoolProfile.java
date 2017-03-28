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

import java.io.Serializable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ThreadPoolRejectedPolicy;

/**
 * A profile which defines thread pool settings.
 * <p/>
 * See more details at <a href="http://camel.apache.org/threading-model.html">threading model</a>
 *
 * @version 
 */
public class ThreadPoolProfile implements Serializable, Cloneable {

    // TODO: Camel 3.0 consider moving to org.apache.camel

    private static final long serialVersionUID = 1L;

    private String id;
    private Boolean defaultProfile;
    private Integer poolSize;
    private Integer maxPoolSize;
    private Long keepAliveTime;
    private TimeUnit timeUnit;
    private Integer maxQueueSize;
    private Boolean allowCoreThreadTimeOut;
    private ThreadPoolRejectedPolicy rejectedPolicy;

    /**
     * Creates a new thread pool profile, with no id set.
     */
    public ThreadPoolProfile() {
    }

    /**
     * Creates a new thread pool profile
     *
     * @param id id of the profile
     */
    public ThreadPoolProfile(String id) {
        this.id = id;
    }

    /**
     * Gets the id of this profile
     *
     * @return the id of this profile
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the id of this profile
     *
     * @param id profile id
     */
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

    /**
     * Sets the core pool size (threads to keep minimum in pool)
     *
     * @param poolSize the pool size
     */
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
     * Sets the maximum pool size
     *
     * @param maxPoolSize the max pool size
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
     * Gets the time unit used for keep alive time
     *
     * @return the time unit
     */
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the time unit used for keep alive time
     *
     * @param timeUnit the time unit
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
     * Gets whether to allow core threads to timeout
     *
     * @return the allow core threads to timeout
     */
    public Boolean getAllowCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    /**
     * Sets whether to allow core threads to timeout
     *
     * @param allowCoreThreadTimeOut <tt>true</tt> to allow timeout
     */
    public void setAllowCoreThreadTimeOut(Boolean allowCoreThreadTimeOut) {
        this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
    }

    /**
     * Gets the policy for tasks which cannot be executed by the thread pool.
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

    /**
     * Overwrites each attribute that is null with the attribute from defaultProfile 
     * 
     * @param defaultProfile profile with default values
     */
    public void addDefaults(ThreadPoolProfile defaultProfile) {
        if (defaultProfile == null) {
            return;
        }
        if (poolSize == null) {
            poolSize = defaultProfile.getPoolSize();
        }
        if (maxPoolSize == null) {
            maxPoolSize = defaultProfile.getMaxPoolSize();
        }
        if (keepAliveTime == null) {
            keepAliveTime = defaultProfile.getKeepAliveTime();
        }
        if (timeUnit == null) {
            timeUnit = defaultProfile.getTimeUnit();
        }
        if (maxQueueSize == null) {
            maxQueueSize = defaultProfile.getMaxQueueSize();
        }
        if (allowCoreThreadTimeOut == null) {
            allowCoreThreadTimeOut = defaultProfile.getAllowCoreThreadTimeOut();
        }
        if (rejectedPolicy == null) {
            rejectedPolicy = defaultProfile.getRejectedPolicy();
        }
    }

    @Override
    public ThreadPoolProfile clone() {
        ThreadPoolProfile cloned = new ThreadPoolProfile();
        cloned.setDefaultProfile(defaultProfile);
        cloned.setId(id);
        cloned.setKeepAliveTime(keepAliveTime);
        cloned.setMaxPoolSize(maxPoolSize);
        cloned.setMaxQueueSize(maxQueueSize);
        cloned.setPoolSize(maxPoolSize);
        cloned.setAllowCoreThreadTimeOut(allowCoreThreadTimeOut);
        cloned.setRejectedPolicy(rejectedPolicy);
        cloned.setTimeUnit(timeUnit);
        return cloned;
    }

    @Override
    public String toString() {
        return "ThreadPoolProfile[" + id + " (" + defaultProfile + ") size:" + poolSize + "-" + maxPoolSize
                + ", keepAlive: " + keepAliveTime + " " + timeUnit + ", maxQueue: " + maxQueueSize
                + ", allowCoreThreadTimeOut:" + allowCoreThreadTimeOut + ", rejectedPolicy:" + rejectedPolicy + "]";
    }

}
