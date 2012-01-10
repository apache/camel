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
public interface ThreadPoolProfile {

    /**
     * Gets the id of this profile
     *
     * @return the id of this profile
     */
    String getId();

    /**
     * Whether this profile is the default profile (there can only be one).
     *
     * @return <tt>true</tt> if its the default profile, <tt>false</tt> otherwise
     */
    Boolean isDefaultProfile();

    /**
     * Sets whether this profile is the default profile (there can only be one).
     *
     * @param defaultProfile the option
     */
    void setDefaultProfile(Boolean defaultProfile);

    /**
     * Gets the core pool size (threads to keep minimum in pool)
     *
     * @return the pool size
     */
    Integer getPoolSize();

    /**
     * Sets the core pool size (threads to keep minimum in pool)
     *
     * @param poolSize the pool size
     */
    void setPoolSize(Integer poolSize);

    /**
     * Gets the maximum pool size
     *
     * @return the maximum pool size
     */
    Integer getMaxPoolSize();

    /**
     * Sets the maximum pool size
     *
     * @param maxPoolSize the maximum pool size
     */
    void setMaxPoolSize(Integer maxPoolSize);

    /**
     * Gets the keep alive time for inactive threads
     *
     * @return the keep alive time
     */
    Long getKeepAliveTime();

    /**
     * Sets the keep alive time for inactive threads
     *
     * @param keepAliveTime the keep alive time
     */
    void setKeepAliveTime(Long keepAliveTime);

    /**
     * Gets the time unit used for keep alive time
     *
     * @return the time unit
     */
    TimeUnit getTimeUnit();

    /**
     * Sets the time unit used for keep alive time
     *
     * @param timeUnit the time unit
     */
    void setTimeUnit(TimeUnit timeUnit);

    /**
     * Gets the maximum number of tasks in the work queue.
     * <p/>
     * Use <tt>-1</tt> or <tt>Integer.MAX_VALUE</tt> for an unbounded queue
     *
     * @return the max queue size
     */
    Integer getMaxQueueSize();

    /**
     * Sets the maximum number of tasks in the work queue.
     * <p/>
     * Use <tt>-1</tt> or <tt>Integer.MAX_VALUE</tt> for an unbounded queue
     *
     * @param maxQueueSize the max queue size
     */
    void setMaxQueueSize(Integer maxQueueSize);

    /**
     * Gets the handler for tasks which cannot be executed by the thread pool.
     *
     * @return the policy for the handler
     */
    ThreadPoolRejectedPolicy getRejectedPolicy();

    /**
     * Gets the handler for tasks which cannot be executed by the thread pool.
     *
     * @return the handler, or <tt>null</tt> if none defined
     */
    RejectedExecutionHandler getRejectedExecutionHandler();

    /**
     * Sets the handler for tasks which cannot be executed by the thread pool.
     *
     * @param rejectedPolicy  the policy for the handler
     */
    void setRejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy);

}
