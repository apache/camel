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
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.Processor;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.ThreadPoolProfileBuilder;
import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.ThreadsProcessor;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.ThreadPoolProfile;

/**
 * Represents an XML &lt;threads/&gt; element
 *
 * @version 
 */
@XmlRootElement(name = "threads")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadsDefinition extends OutputDefinition<ThreadsDefinition> implements ExecutorServiceAwareDefinition<ThreadsDefinition> {

    // TODO: Camel 3.0 Should extend NoOutputDefinition

    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute
    private String executorServiceRef;
    @XmlAttribute
    private Integer poolSize;
    @XmlAttribute
    private Integer maxPoolSize;
    @XmlAttribute
    private Long keepAliveTime;
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
    
    public ThreadsDefinition() {
        this.threadName =  "Threads";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // the threads name
        String name = getThreadName() != null ? getThreadName() : "Threads";
        // prefer any explicit configured executor service
        boolean shutdownThreadPool = ProcessorDefinitionHelper.willCreateNewThreadPool(routeContext, this, true);
        ExecutorService threadPool = ProcessorDefinitionHelper.getConfiguredExecutorService(routeContext, name, this, false);
        // if no explicit then create from the options
        if (threadPool == null) {
            ExecutorServiceManager manager = routeContext.getCamelContext().getExecutorServiceManager();
            // create the thread pool using a builder
            ThreadPoolProfile profile = new ThreadPoolProfileBuilder(name)
                    .poolSize(getPoolSize())
                    .maxPoolSize(getMaxPoolSize())
                    .keepAliveTime(getKeepAliveTime(), getTimeUnit())
                    .maxQueueSize(getMaxQueueSize())
                    .rejectedPolicy(getRejectedPolicy())
                    .build();
            threadPool = manager.newThreadPool(this, name, profile);
            shutdownThreadPool = true;
        } else {
            if (getThreadName() != null && !getThreadName().equals("Threads")) {
                throw new IllegalArgumentException("ThreadName and executorServiceRef options cannot be used together.");
            }
            if (getPoolSize() != null) {
                throw new IllegalArgumentException("PoolSize and executorServiceRef options cannot be used together.");
            }
            if (getMaxPoolSize() != null) {
                throw new IllegalArgumentException("MaxPoolSize and executorServiceRef options cannot be used together.");
            }
            if (getKeepAliveTime() != null) {
                throw new IllegalArgumentException("KeepAliveTime and executorServiceRef options cannot be used together.");
            }
            if (getMaxQueueSize() != null) {
                throw new IllegalArgumentException("MaxQueueSize and executorServiceRef options cannot be used together.");
            }
            if (getRejectedPolicy() != null) {
                throw new IllegalArgumentException("RejectedPolicy and executorServiceRef options cannot be used together.");
            }
        }

        ThreadsProcessor thread = new ThreadsProcessor(routeContext.getCamelContext(), threadPool, shutdownThreadPool);
        if (getCallerRunsWhenRejected() == null) {
            // should be true by default
            thread.setCallerRunsWhenRejected(true);
        } else {
            thread.setCallerRunsWhenRejected(getCallerRunsWhenRejected());
        }
        thread.setRejectedPolicy(resolveRejectedPolicy(routeContext));

        List<Processor> pipe = new ArrayList<Processor>(2);
        pipe.add(thread);
        pipe.add(createChildProcessor(routeContext, true));
        // wrap in nested pipeline so this appears as one processor
        // (recipient list definition does this as well)
        return new Pipeline(routeContext.getCamelContext(), pipe) {
            @Override
            public String toString() {
                return "Threads[" + getOutputs() + "]";
            }
        };
    }

    protected ThreadPoolRejectedPolicy resolveRejectedPolicy(RouteContext routeContext) {
        if (getExecutorServiceRef() != null && getRejectedPolicy() == null) {
            ThreadPoolProfile threadPoolProfile = routeContext.getCamelContext().getExecutorServiceManager().getThreadPoolProfile(getExecutorServiceRef());
            if (threadPoolProfile != null) {
                return threadPoolProfile.getRejectedPolicy();
            }
        }
        return getRejectedPolicy();
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
    public ThreadsDefinition keepAliveTime(long keepAliveTime) {
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

    public Long getKeepAliveTime() {
        return keepAliveTime;
    }

    public void setKeepAliveTime(Long keepAliveTime) {
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
