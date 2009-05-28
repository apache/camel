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
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.WaitForTaskToComplete;
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
public class ThreadsDefinition extends OutputDefinition<ProcessorDefinition> {

    @XmlTransient
    private ExecutorService executorService;
    @XmlAttribute(required = false)
    private String executorServiceRef;
    @XmlAttribute(required = false)
    private Integer poolSize;
    @XmlAttribute(required = false)
    private WaitForTaskToComplete waitForTaskToComplete = WaitForTaskToComplete.IfReplyExpected;

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        if (executorServiceRef != null) {
            executorService = routeContext.lookup(executorServiceRef, ExecutorService.class);
        }
        if (executorService == null && poolSize != null) {
            executorService = ExecutorServiceHelper.newScheduledThreadPool(poolSize, "AsyncProcessor", true);
        }
        Processor childProcessor = routeContext.createProcessor(this);

        // wrap it in a unit of work so the route that comes next is also done in a unit of work
        UnitOfWorkProcessor uow = new UnitOfWorkProcessor(childProcessor);

        return new ThreadsProcessor(uow, executorService, waitForTaskToComplete);
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

    /**
     * Setting the executor service for executing the multicasting action.
     *
     * @return the builder
     */
    public ThreadsDefinition executorService(ExecutorService executorService) {
        setExecutorService(executorService);
        return this;
    }

    /**
     * Setting the core pool size for the underlying {@link java.util.concurrent.ExecutorService}.
     *
     * @return the builder
     */
    public ThreadsDefinition poolSize(int poolSize) {
        setPoolSize(poolSize);
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
}
