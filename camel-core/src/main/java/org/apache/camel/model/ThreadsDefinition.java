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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.Processor;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.builder.xml.TimeUnitAdapter;
import org.apache.camel.processor.ThreadsProcessor;
import org.apache.camel.processor.UnitOfWorkProcessor;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.concurrent.ExecutorServiceHelper;

/**
 * Represents an XML &lt;threads/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "threads")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadsDefinition extends OutputDefinition<ProcessorDefinition> implements ExecutorServiceAwareDefinition<ThreadsDefinition> {

    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute(required = false)
    private String executorServiceRef;
    @XmlAttribute(required = false)
    private Integer poolSize;
    @XmlAttribute(required = false)
    private Integer maxPoolSize;
    @XmlAttribute(required = false)
    private Integer keepAliveTime = 60;
    @XmlAttribute(required = false)
    @XmlJavaTypeAdapter(TimeUnitAdapter.class)
    private TimeUnit units = TimeUnit.SECONDS;
    @XmlAttribute(required = false)
    private String threadName;
    @XmlAttribute(required = false)
    private WaitForTaskToComplete waitForTaskToComplete = WaitForTaskToComplete.IfReplyExpected;

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        // prefer any explicit configured executor service
        executorService = ExecutorServiceHelper.getConfiguredExecutorService(routeContext, this);
        if (executorService == null) {
            // none was configured so create an executor based on the other parameters
            String name = getThreadName() != null ? getThreadName() : "Threads";
            if (poolSize == null || poolSize <= 0) {
                // use the cached thread pool
                executorService = routeContext.getCamelContext().getExecutorServiceStrategy().newCachedThreadPool(this, name);
            } else {
                // use a custom pool based on the settings
                int max = getMaxPoolSize() != null ? getMaxPoolSize() : poolSize;
                executorService = routeContext.getCamelContext().getExecutorServiceStrategy()
                                        .newThreadPool(this, name, poolSize, max, getKeepAliveTime(), getUnits(), true);
            }
        }
        Processor childProcessor = routeContext.createProcessor(this);

        // wrap it in a unit of work so the route that comes next is also done in a unit of work
        UnitOfWorkProcessor uow = new UnitOfWorkProcessor(routeContext, childProcessor);

        return new ThreadsProcessor(routeContext.getCamelContext(), uow, executorService, waitForTaskToComplete);
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

    @SuppressWarnings("unchecked")
    public ThreadsDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    @SuppressWarnings("unchecked")
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
    public ThreadsDefinition units(TimeUnit keepAliveTimeUnits) {
        setUnits(keepAliveTimeUnits);
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
     * Setting to whether to wait for async tasks to be complete before continuing original route.
     * <p/>
     * Is default <tt>IfReplyExpected</tt>
     *
     * @param wait the wait option
     * @return the builder
     */
    public ThreadsDefinition waitForTaskToComplete(WaitForTaskToComplete wait) {
        setWaitForTaskToComplete(wait);
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

    public WaitForTaskToComplete getWaitForTaskToComplete() {
        return waitForTaskToComplete;
    }

    public void setWaitForTaskToComplete(WaitForTaskToComplete waitForTaskToComplete) {
        this.waitForTaskToComplete = waitForTaskToComplete;
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

    public TimeUnit getUnits() {
        return units;
    }

    public void setUnits(TimeUnit units) {
        this.units = units;
    }

    public String getThreadName() {
        return threadName;
    }

    public void setThreadName(String threadName) {
        this.threadName = threadName;
    }
}
