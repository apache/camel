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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.Processor;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.ThreadProcessor;
import org.apache.camel.spi.RouteContext;

/**
 * Represents an XML &lt;thread/&gt; element
 *
 * @version $Revision$
 */
@XmlRootElement(name = "thread")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadType extends ProcessorType<ProcessorType> {
    @XmlAttribute(required = false)
    private Integer coreSize = 1;
    @XmlAttribute(required = false)
    private Boolean daemon = Boolean.TRUE;
    @XmlAttribute(required = false)
    private Long keepAliveTime;
    @XmlAttribute(required = false)
    private Integer maxSize = 1;
    @XmlAttribute(required = false)
    private String name = "Thread Processor";
    @XmlAttribute(required = false)
    private Integer priority = Thread.NORM_PRIORITY;
    @XmlAttribute(required = false)
    private Long stackSize;
    @XmlElementRef
    private List<ProcessorType<?>> outputs = new ArrayList<ProcessorType<?>>();
    @XmlTransient
    private BlockingQueue<Runnable> taskQueue;
    @XmlTransient
    private ThreadGroup threadGroup;
    @XmlTransient
    private ThreadPoolExecutor executor;

    public ThreadType() {
    }

    public ThreadType(int coreSize) {
        this.coreSize = coreSize;
        this.maxSize = coreSize;
    }

    public ThreadType(ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @Override
    public List<ProcessorType<?>> getOutputs() {
        return outputs;
    }

    @Override
    public String toString() {
        return "Thread[" + name + "]";
    }

    @Override
    public String getShortName() {
        return "thread";
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        ThreadProcessor thread = new ThreadProcessor();
        thread.setExecutor(executor);
        if (coreSize != null) {
            thread.setCoreSize(coreSize);
        }
        if (daemon != null) {
            thread.setDaemon(daemon);
        }
        if (keepAliveTime != null) {
            thread.setKeepAliveTime(keepAliveTime);
        }
        if (maxSize != null) {
            thread.setMaxSize(maxSize);
        }
        thread.setName(name);
        thread.setPriority(priority);
        if (stackSize != null) {
            thread.setStackSize(stackSize);
        }
        thread.setTaskQueue(taskQueue);
        thread.setThreadGroup(threadGroup);

        // TODO: see if we can avoid creating so many nested pipelines
        ArrayList<Processor> pipe = new ArrayList<Processor>(2);
        pipe.add(thread);
        pipe.add(createOutputsProcessor(routeContext, outputs));
        return new Pipeline(pipe);
    }

    @Override
    protected void configureChild(ProcessorType output) {
        super.configureChild(output);
        if (isInheritErrorHandler()) {
            output.setErrorHandlerBuilder(getErrorHandlerBuilder());
        }
    }

    // Fluent methods
    // -----------------------------------------------------------------------
    @Override
    public ProcessorType errorHandler(ErrorHandlerBuilder errorHandlerBuilder) {
        // do not support setting error handling on thread type as its confusing and will not be used
        throw new IllegalArgumentException("Setting errorHandler on ThreadType is not supported."
            + " Instead set the errorHandler on the parent.");
    }

    public ThreadType coreSize(int coreSize) {
        setCoreSize(coreSize);
        return this;
    }

    public ThreadType daemon(boolean daemon) {
        setDaemon(daemon);
        return this;
    }

    public ThreadType keepAliveTime(long keepAliveTime) {
        setKeepAliveTime(keepAliveTime);
        return this;
    }

    public ThreadType maxSize(int maxSize) {
        setMaxSize(maxSize);
        return this;
    }

    public ThreadType name(String name) {
        setName(name);
        return this;
    }

    public ThreadType priority(int priority) {
        setPriority(priority);
        return this;
    }

    public ThreadType stackSize(long stackSize) {
        setStackSize(stackSize);
        return this;
    }

    public ThreadType taskQueue(BlockingQueue<Runnable> taskQueue) {
        setTaskQueue(taskQueue);
        return this;
    }

    public ThreadType threadGroup(ThreadGroup threadGroup) {
        setThreadGroup(threadGroup);
        return this;
    }

    public ThreadType executor(ThreadPoolExecutor executor) {
        setExecutor(executor);
        return this;
    }

    ///////////////////////////////////////////////////////////////////
    //
    // Property Accessors
    //
    ///////////////////////////////////////////////////////////////////

    public void setCoreSize(int coreSize) {
        this.coreSize = coreSize;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setStackSize(long stackSize) {
        this.stackSize = stackSize;
    }

    public void setTaskQueue(BlockingQueue<Runnable> taskQueue) {
        this.taskQueue = taskQueue;
    }

    public void setThreadGroup(ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
    }

    public ThreadPoolExecutor getExecutor() {
        return executor;
    }

    public void setExecutor(ThreadPoolExecutor executor) {
        this.executor = executor;
    }
}
