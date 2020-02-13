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
package org.apache.camel.model;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.spi.Metadata;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;

/**
 * Specifies that all steps after this node are processed asynchronously
 */
@Metadata(label = "eip,routing")
@XmlRootElement(name = "threads")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadsDefinition extends NoOutputDefinition<ThreadsDefinition> implements ExecutorServiceAwareDefinition<ThreadsDefinition> {

    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String poolSize;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String maxPoolSize;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Long")
    private String keepAliveTime;
    @XmlAttribute
    @Metadata(javaType = "java.util.concurrent.TimeUnit",
              enums = "NANOSECONDS,MICROSECONDS,MILLISECONDS,SECONDS,MINUTES,HOURS,DAYS")
    private String timeUnit;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Integer")
    private String maxQueueSize;
    @XmlAttribute
    @Metadata(javaType = "java.lang.Boolean")
    private String allowCoreThreadTimeOut;
    @XmlAttribute
    @Metadata(defaultValue = "Threads")
    private String threadName;
    @XmlAttribute
    @Metadata(javaType = "org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy", enums = "Abort,CallerRuns,DiscardOldest,Discard")
    private String rejectedPolicy;
    @XmlAttribute
    @Metadata(defaultValue = "true")
    private String callerRunsWhenRejected;

    public ThreadsDefinition() {
        this.threadName = "Threads";
    }

    @Override
    public String getShortName() {
        return "threads";
    }

    @Override
    public String getLabel() {
        return "threads";
    }

    @Override
    public String toString() {
        return "Threads[" + getOutputs() + "]";
    }

    /**
     * To use a custom thread pool
     */
    @Override
    public ThreadsDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * To refer to a custom thread pool or use a thread pool profile (as
     * overlay)
     */
    @Override
    public ThreadsDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * Sets the core pool size
     *
     * @param poolSize the core pool size to keep minimum in the pool
     * @return the builder
     */
    public ThreadsDefinition poolSize(int poolSize) {
        return poolSize(Integer.toString(poolSize));
    }

    /**
     * Sets the core pool size
     *
     * @param poolSize the core pool size to keep minimum in the pool
     * @return the builder
     */
    public ThreadsDefinition poolSize(String poolSize) {
        setPoolSize(poolSize);
        return this;
    }

    /**
     * Sets the maximum pool size
     *
     * @param maxPoolSize the maximum pool size
     * @return the builder
     */
    public ThreadsDefinition maxPoolSize(int maxPoolSize) {
        return maxPoolSize(Integer.toString(maxPoolSize));
    }

    /**
     * Sets the maximum pool size
     *
     * @param maxPoolSize the maximum pool size
     * @return the builder
     */
    public ThreadsDefinition maxPoolSize(String maxPoolSize) {
        setMaxPoolSize(maxPoolSize);
        return this;
    }

    /**
     * Sets the keep alive time for idle threads
     *
     * @param keepAliveTime keep alive time
     * @return the builder
     */
    public ThreadsDefinition keepAliveTime(long keepAliveTime) {
        return keepAliveTime(Long.toString(keepAliveTime));
    }

    /**
     * Sets the keep alive time for idle threads
     *
     * @param keepAliveTime keep alive time
     * @return the builder
     */
    public ThreadsDefinition keepAliveTime(String keepAliveTime) {
        setKeepAliveTime(keepAliveTime);
        return this;
    }

    /**
     * Sets the keep alive time unit. By default SECONDS is used.
     *
     * @param keepAliveTimeUnits time unit
     * @return the builder
     */
    public ThreadsDefinition timeUnit(TimeUnit keepAliveTimeUnits) {
        return timeUnit(keepAliveTimeUnits.name());
    }

    /**
     * Sets the keep alive time unit. By default SECONDS is used.
     *
     * @param keepAliveTimeUnits time unit
     * @return the builder
     */
    public ThreadsDefinition timeUnit(String keepAliveTimeUnits) {
        setTimeUnit(keepAliveTimeUnits);
        return this;
    }

    /**
     * Sets the maximum number of tasks in the work queue.
     * <p/>
     * Use <tt>-1</tt> or <tt>Integer.MAX_VALUE</tt> for an unbounded queue
     *
     * @param maxQueueSize the max queue size
     * @return the builder
     */
    public ThreadsDefinition maxQueueSize(int maxQueueSize) {
        return maxQueueSize(Integer.toString(maxQueueSize));
    }

    /**
     * Sets the maximum number of tasks in the work queue.
     * <p/>
     * Use <tt>-1</tt> or <tt>Integer.MAX_VALUE</tt> for an unbounded queue
     *
     * @param maxQueueSize the max queue size
     * @return the builder
     */
    public ThreadsDefinition maxQueueSize(String maxQueueSize) {
        setMaxQueueSize(maxQueueSize);
        return this;
    }

    /**
     * Sets the handler for tasks which cannot be executed by the thread pool.
     *
     * @param rejectedPolicy the policy for the handler
     * @return the builder
     */
    public ThreadsDefinition rejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        return rejectedPolicy(rejectedPolicy.name());
    }

    /**
     * Sets the handler for tasks which cannot be executed by the thread pool.
     *
     * @param rejectedPolicy the policy for the handler
     * @return the builder
     */
    public ThreadsDefinition rejectedPolicy(String rejectedPolicy) {
        setRejectedPolicy(rejectedPolicy);
        return this;
    }

    /**
     * Sets the thread name to use.
     *
     * @param threadName the thread name
     * @return the builder
     */
    public ThreadsDefinition threadName(String threadName) {
        setThreadName(threadName);
        return this;
    }

    /**
     * Whether or not to use as caller runs as <b>fallback</b> when a task is
     * rejected being added to the thread pool (when its full). This is only
     * used as fallback if no rejectedPolicy has been configured, or the thread
     * pool has no configured rejection handler.
     * <p/>
     * Is by default <tt>true</tt>
     *
     * @param callerRunsWhenRejected whether or not the caller should run
     * @return the builder
     */
    public ThreadsDefinition callerRunsWhenRejected(boolean callerRunsWhenRejected) {
        return callerRunsWhenRejected(Boolean.toString(callerRunsWhenRejected));
    }

    /**
     * Whether or not to use as caller runs as <b>fallback</b> when a task is
     * rejected being added to the thread pool (when its full). This is only
     * used as fallback if no rejectedPolicy has been configured, or the thread
     * pool has no configured rejection handler.
     * <p/>
     * Is by default <tt>true</tt>
     *
     * @param callerRunsWhenRejected whether or not the caller should run
     * @return the builder
     */
    public ThreadsDefinition callerRunsWhenRejected(String callerRunsWhenRejected) {
        setCallerRunsWhenRejected(callerRunsWhenRejected);
        return this;
    }

    /**
     * Whether idle core threads is allowed to timeout and therefore can shrink
     * the pool size below the core pool size
     * <p/>
     * Is by default <tt>false</tt>
     *
     * @param allowCoreThreadTimeOut <tt>true</tt> to allow timeout
     * @return the builder
     */
    public ThreadsDefinition allowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
        return allowCoreThreadTimeOut(Boolean.toString(allowCoreThreadTimeOut));
    }

    /**
     * Whether idle core threads is allowed to timeout and therefore can shrink
     * the pool size below the core pool size
     * <p/>
     * Is by default <tt>false</tt>
     *
     * @param allowCoreThreadTimeOut <tt>true</tt> to allow timeout
     * @return the builder
     */
    public ThreadsDefinition allowCoreThreadTimeOut(String allowCoreThreadTimeOut) {
        setAllowCoreThreadTimeOut(allowCoreThreadTimeOut);
        return this;
    }

    @Override
    public ExecutorService getExecutorService() {
        return executorService;
    }

    @Override
    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    @Override
    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    @Override
    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public String getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(String poolSize) {
        this.poolSize = poolSize;
    }

    public String getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(String maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public String getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(String keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public String getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(String timeUnit) {
        this.timeUnit = timeUnit;
    }

    public String getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(String maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public String getRejectedPolicy() {
        return rejectedPolicy;
    }

    public void setRejectedPolicy(String rejectedPolicy) {
        this.rejectedPolicy = rejectedPolicy;
    }

    public String getCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(String callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }

    public String getAllowCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }

    public void setAllowCoreThreadTimeOut(String allowCoreThreadTimeOut) {
        this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
    }
}
