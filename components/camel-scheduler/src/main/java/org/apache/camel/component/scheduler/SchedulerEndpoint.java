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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultScheduledPollConsumerScheduler;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Generate messages on a schedule, by default at a fixed delay using a
 * <code>java.util.concurrent.ScheduledExecutorService</code>.
 *
 * This component is similar to the timer component, but it offers more functionality in terms of scheduling. Also this
 * component uses JDK ScheduledExecutorService. Where as the timer uses a JDK Timer.
 */
@UriEndpoint(firstVersion = "2.15.0", scheme = "scheduler", title = "Scheduler", syntax = "scheduler:name",
             remote = false, consumerOnly = true, category = { Category.CORE, Category.SCHEDULING },
             headersClass = SchedulerConstants.class)
@BodyTypes(consumerOut = @BodyType(value = Carrier.NULL, description = "No body: the message only starts the route"))
public class SchedulerEndpoint extends ScheduledPollEndpoint {

    @UriPath(description = "The name of the scheduler. The consumers of all endpoints with the same name share one thread"
                           + " pool of the component, unless the scheduler or scheduledExecutorService option is set. The"
                           + " threads of that pool are named after it, and with includeMetadata the consumer sets the"
                           + " CamelTimerName exchange property to it.")
    @Metadata(required = true)
    private String name;
    @UriParam(description = "Whether the consumer adds the time when the scheduler fired to each message. If true, it sets the"
                            + " CamelTimerName exchange property to the name, the CamelTimerFiredTime exchange property to"
                            + " that time as a java.util.Date, and the CamelMessageTimestamp header to that time in"
                            + " milliseconds since the epoch. If false, the message has none of them.")
    private boolean includeMetadata;
    @UriParam(defaultValue = "1", label = "scheduler",
              description = "The number of core threads of the thread pool that the component creates for the name. The"
                            + " consumer that starts first with a name creates the pool with its poolSize, and the consumers"
                            + " that start later with the same name share that pool. Not used when the scheduler or"
                            + " scheduledExecutorService option is set.")
    private int poolSize = 1;
    @UriParam(defaultValue = "false", label = "advanced",
              description = "Whether the consumer waits until each message is fully routed before the scheduler can fire"
                            + " again, also when the route continues on another thread. If false, the consumer uses the"
                            + " asynchronous routing engine, and the scheduler can fire again while a message that continues"
                            + " on another thread is still being routed.")
    private boolean synchronous;

    public SchedulerEndpoint(String uri, SchedulerComponent component, String remaining) {
        super(uri, component);
        this.name = remaining;
    }

    @Override
    public boolean isRemote() {
        return false;
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

    public void setName(String name) {
        this.name = name;
    }

    public boolean isIncludeMetadata() {
        return includeMetadata;
    }

    public void setIncludeMetadata(boolean includeMetadata) {
        this.includeMetadata = includeMetadata;
    }

    public int getPoolSize() {
        return poolSize;
    }

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
        // if using default scheduler then obtain thread pool from component which manages their lifecycle. A consumer
        // that is started again (stopped and started via JMX or a route policy) takes the pool again, as its stop
        // released it, and the component shut it down if it was the last user
        boolean usesComponentPool = consumer.getComponentPool() != null;
        if (usesComponentPool || consumer.getScheduler() == null && consumer.getScheduledExecutorService() == null) {
            ScheduledExecutorService scheduler = getComponent().addConsumer(consumer);
            consumer.setComponentPool(scheduler);
            consumer.setComponentPoolTaken(true);
            consumer.setScheduledExecutorService(scheduler);
            if (consumer.getScheduler() instanceof DefaultScheduledPollConsumerScheduler defaultScheduler) {
                // the scheduler the consumer created at its first start still has the pool of that start
                defaultScheduler.setScheduledExecutorService(scheduler);
            }
        }
    }

    public void onConsumerStop(SchedulerConsumer consumer) {
        // only a consumer that took a reference to the pool of the component releases one: otherwise the pool could
        // be shut down while other consumers still use it
        if (consumer.isComponentPoolTaken()) {
            consumer.setComponentPoolTaken(false);
            getComponent().removeConsumer(consumer);
        }
    }
}
