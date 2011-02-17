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
package org.apache.camel.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.Processor;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.ThreadsProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ThreadPoolProfile;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;threads/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "threads")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadsDefinition extends OutputDefinition<ThreadsDefinition> implements ExecutorServiceAwareDefinition<ThreadsDefinition> {

    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private Integer poolSize;
    @XmlAttribute
    private Integer maxPoolSize;
    @XmlAttribute
    private Integer keepAliveTime;
    @XmlAttribute
    @XmlJavaTypeAdapter(TimeUnitAdapter.class)
    private TimeUnit timeUnit;
    @XmlAttribute
    private Integer maxQueueSize;
    @XmlAttribute
    private String threadName;
    @XmlAttribute
    private ThreadPoolRejectedPolicy rejectedPolicy;
    @XmlAttribute
    private Boolean callerRunsWhenRejected;

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // the threads name
        String name = getThreadName() != null ? getThreadName() : "Threads";

        // prefer any explicit configured executor service
        executorService = ExecutorServiceHelper.getConfiguredExecutorService(routeContext, name, this);
        // if no explicit then create from the options
        if (executorService == null) {
            ThreadPoolProfile profile = routeContext.getCamelContext().getExecutorServiceStrategy().getDefaultThreadPoolProfile();
            // use the default thread pool profile as base and then override with values
            // use a custom pool based on the settings
            int core = getPoolSize() != null ? getPoolSize() : profile.getPoolSize();
            int max = getMaxPoolSize() != null ? getMaxPoolSize() : profile.getMaxPoolSize();
            long keepAlive = getKeepAliveTime() != null ? getKeepAliveTime() : profile.getKeepAliveTime();
            int maxQueue = getMaxQueueSize() != null ? getMaxQueueSize() : profile.getMaxQueueSize();
            TimeUnit tu = getTimeUnit() != null ? getTimeUnit() : profile.getTimeUnit();
            RejectedExecutionHandler rejected = profile.getRejectedExecutionHandler();
            if (rejectedPolicy != null) {
                rejected = rejectedPolicy.asRejectedExecutionHandler();
            }

            executorService = routeContext.getCamelContext().getExecutorServiceStrategy()
                                    .newThreadPool(this, name, core, max, keepAlive, tu, maxQueue, rejected, true);
        }

        ThreadsProcessor thread = new ThreadsProcessor(routeContext.getCamelContext(), executorService);
        if (getCallerRunsWhenRejected() == null) {
            // should be true by default
            thread.setCallerRunsWhenRejected(true);
        } else {
            thread.setCallerRunsWhenRejected(getCallerRunsWhenRejected());
        }

        List<Processor> pipe = new ArrayList<Processor>(2);
        pipe.add(thread);
        pipe.add(createChildProcessor(routeContext, true));
        // wrap in nested pipeline so this appears as one processor
        return new Pipeline(routeContext.getCamelContext(), pipe);
    }

    @Override
    public String getLabel() {
        return "threads";
    }

    @Override
    public String getShortName() {
        return "threads";
    }

    @Override
    public String toString() {
        return "Threads[" + getOutputs() + "]";
    }

    public ThreadsDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    public ThreadsDefinition executorServiceRef(String executorServiceRef) {
        setExecutorServiceRef(executorServiceRef);
        return this;
    }

    /**
     * Sets the core pool size for the underlying {@link java.util.concurrent.ExecutorService}.
     *
     * @param poolSize the core pool size to keep minimum in the pool
     * @return the builder
     */
    public ThreadsDefinition poolSize(int poolSize) {
        setPoolSize(poolSize);
        return this;
    }

    /**
     * Sets the maximum pool size for the underlying {@link java.util.concurrent.ExecutorService}.
     *
     * @param maxPoolSize the maximum pool size
     * @return the builder
     */
    public ThreadsDefinition maxPoolSize(int maxPoolSize) {
        setMaxPoolSize(maxPoolSize);
        return this;
    }

    /**
     * Sets the keep alive time for idle threads
     *
     * @param keepAliveTime keep alive time
     * @return the builder
     */
    public ThreadsDefinition keepAliveTime(int keepAliveTime) {
        setKeepAliveTime(keepAliveTime);
        return this;
    }

    /**
     * Sets the keep alive time unit.
     * By default SECONDS is used.
     *
     * @param keepAliveTimeUnits time unit
     * @return the builder
     */
    public ThreadsDefinition timeUnit(TimeUnit keepAliveTimeUnits) {
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
        setMaxQueueSize(maxQueueSize);
        return this;
    }

    /**
     * Sets the handler for tasks which cannot be executed by the thread pool.
     *
     * @param rejectedPolicy  the policy for the handler
     * @return the builder
     */
    public ThreadsDefinition rejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
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
     * Whether or not the caller should run the task when it was rejected by the thread pool.
     * <p/>
     * Is by default <tt>true</tt>
     *
     * @param callerRunsWhenRejected whether or not the caller should run
     * @return the builder
     */
    public ThreadsDefinition callerRunsWhenRejected(boolean callerRunsWhenRejected) {
        setCallerRunsWhenRejected(callerRunsWhenRejected);
        return this;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public String getExecutorServiceRef() {
        return executorServiceRef;
    }

    public void setExecutorServiceRef(String executorServiceRef) {
        this.executorServiceRef = executorServiceRef;
    }

    public Integer getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(Integer poolSize) {
        this.poolSize = poolSize;
    }

    public Integer getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(Integer maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public Integer getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(Integer keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public Integer getMaxQueueSize() {
        return maxQueueSize;
    }

    public void setMaxQueueSize(Integer maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }

    public ThreadPoolRejectedPolicy getRejectedPolicy() {
        return rejectedPolicy;
    }

    public void setRejectedPolicy(ThreadPoolRejectedPolicy rejectedPolicy) {
        this.rejectedPolicy = rejectedPolicy;
    }

    public Boolean getCallerRunsWhenRejected() {
        return callerRunsWhenRejected;
    }

    public void setCallerRunsWhenRejected(Boolean callerRunsWhenRejected) {
        this.callerRunsWhenRejected = callerRunsWhenRejected;
    }
}
