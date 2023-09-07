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
package org.apache.camel.component.scheduler;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Generate messages in specified intervals using <code>java.util.concurrent.ScheduledExecutorService</code>.
 *
 * This component is similar to the timer component, but it offers more functionality in terms of scheduling. Also this
 * component uses JDK ScheduledExecutorService. Where as the timer uses a JDK Timer.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "scheduler", title = "Scheduler", syntax = "scheduler:name",
             consumerOnly = true, category = { Category.CORE, Category.SCHEDULING }, headersClass = SchedulerConstants.class)
public class SchedulerEndpoint extends ScheduledPollEndpoint {

    @UriPath
    @Metadata(required = true)
    private String name;
    @UriParam
    private boolean includeMetadata;
    @UriParam(defaultValue = "1", label = "scheduler")
    private int poolSize = 1;
    @UriParam(defaultValue = "false", label = "advanced",
              description = "Sets whether synchronous processing should be strictly used")
    private boolean synchronous;

    public SchedulerEndpoint(String uri, SchedulerComponent component, String remaining) {
        super(uri, component);
        this.name = remaining;
    }

    @Override
    public SchedulerComponent getComponent() {
        return (SchedulerComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Scheduler cannot be used as a producer");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SchedulerConsumer consumer = new SchedulerConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public String getName() {
        return name;
    }

    /**
     * The name of the scheduler
     */
    public void setName(String name) {
        this.name = name;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    /**
     * Whether to include metadata in the exchange such as fired time, timer name, timer count etc.
     */
    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Number of core threads in the thread pool used by the scheduling thread pool.
     * <p/>
     * Is by default using a single thread
     */
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public void onConsumerStart(SchedulerConsumer consumer) {
        // if using default scheduler then obtain thread pool from component which manages their lifecycle
        if (consumer.getScheduler() == null && consumer.getScheduledExecutorService() == null) {
            ScheduledExecutorService scheduler = getComponent().addConsumer(consumer);
            consumer.setScheduledExecutorService(scheduler);
        }
    }

    public void onConsumerStop(SchedulerConsumer consumer) {
        getComponent().removeConsumer(consumer);
    }
}
