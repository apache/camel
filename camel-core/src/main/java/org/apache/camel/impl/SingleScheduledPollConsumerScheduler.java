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
package org.apache.camel.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;

/**
 * A {@link ScheduledPollConsumerScheduler} which is <b>not</b> scheduled but uses a regular single-threaded {@link ExecutorService}
 * to execute the task when {@link #scheduleTask(org.apache.camel.Consumer, Runnable)} is invoked.
 * <p/>
 * This is used when the {@link org.apache.camel.PollingConsumer} EIP is implemented using the {@link EventDrivenPollingConsumer}
 * bridging a {@link ScheduledPollConsumer} implementation. In this case we use this single threaded regular thread pool
 * to execute the poll task on-demand, instead of using the usual scheduled thread pool which does not fit well with a
 * on-demand poll attempt.
 */
public class SingleScheduledPollConsumerScheduler extends org.apache.camel.support.ServiceSupport implements ScheduledPollConsumerScheduler {

    private final Consumer consumer;
    private CamelContext camelContext;
    private ExecutorService executorService;
    private Future future;

    public SingleScheduledPollConsumerScheduler(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void scheduleTask(Consumer consumer, Runnable task) {
        if (isSchedulerStarted()) {
            future = executorService.submit(task);
        }
    }

    @Override
    public void unscheduleTask() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    @Override
    public void startScheduler() {
        // noop
    }

    @Override
    public boolean isSchedulerStarted() {
        return executorService != null && !executorService.isShutdown();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    protected void doStart() throws Exception {
        if (executorService == null) {
            executorService = camelContext.getExecutorServiceManager().newSingleThreadExecutor(this, consumer.getEndpoint().getEndpointKey());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        if (executorService != null) {
            camelContext.getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
    }
}
