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
import java.util.Collections;
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
import org.apache.camel.impl.RouteContext;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.ThreadProcessor;

/**
 * Represents an XML &lt;thread/&gt; element
 * @version $Revision$
 */
@XmlRootElement(name = "thread")
@XmlAccessorType(XmlAccessType.FIELD)
public class ThreadType extends ProcessorType {
    
    @XmlAttribute
    private int coreSize = 1;
    @XmlAttribute
    private boolean daemon = true;
    @XmlAttribute
    private long keepAliveTime;
    @XmlAttribute
    private int maxSize = 1;
    @XmlAttribute
    private String name = "Thread Processor";
    @XmlAttribute
    private int priority = Thread.NORM_PRIORITY;
    @XmlAttribute
    private long stackSize;
    @XmlElementRef
    private List<ProcessorType> outputs = new ArrayList<ProcessorType>();

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
    public List getInterceptors() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List getOutputs() {
        return outputs;
    }
    
    @Override
    public String toString() {
        return "Thread[" + getLabel() + "]";
    }

    @Override
    public String getLabel() {
        return "coreSize="+coreSize;
    }

    @Override
    public Processor createProcessor(RouteContext routeContext) throws Exception {
        
        ThreadProcessor thread = new ThreadProcessor();
        thread.setExecutor(executor);
        thread.setCoreSize(coreSize);
        thread.setDaemon(daemon);
        thread.setKeepAliveTime(keepAliveTime);
        thread.setMaxSize(maxSize);
        thread.setName(name);
        thread.setPriority(priority);
        thread.setStackSize(stackSize);
        thread.setTaskQueue(taskQueue);
        thread.setThreadGroup(threadGroup);
        
        // TODO: see if we can avoid creating so many nested pipelines 
        
        ArrayList<Processor> pipe = new ArrayList<Processor>(2);
        pipe.add(thread);
        pipe.add(createOutputsProcessor(routeContext, outputs));
        return new Pipeline(pipe);
    }

    ///////////////////////////////////////////////////////////////////
    //
    // Fluent Methods
    //
    ///////////////////////////////////////////////////////////////////
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