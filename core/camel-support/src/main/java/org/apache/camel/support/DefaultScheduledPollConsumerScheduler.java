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
package org.apache.camel.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link ScheduledBatchPollingConsumer}.
 */
public class DefaultScheduledPollConsumerScheduler extends ServiceSupport implements ScheduledPollConsumerScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultScheduledPollConsumerScheduler.class);

    private CamelContext camelContext;
    private Consumer consumer;
    private ScheduledExecutorService scheduledExecutorService;
    private boolean shutdownExecutor;
    private volatile List<ScheduledFuture<?>> futures = new ArrayList<>();
    private Runnable task;
    private int concurrentTasks = 1;

    private long initialDelay = 1000;
    private long delay = 500;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private boolean useFixedDelay = true;

    public DefaultScheduledPollConsumerScheduler() {
    }

    public DefaultScheduledPollConsumerScheduler(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public long getInitialDelay() {
        return initialDelay;
    }

    public void setInitialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public boolean isUseFixedDelay() {
        return useFixedDelay;
    }

    public void setUseFixedDelay(boolean useFixedDelay) {
        this.useFixedDelay = useFixedDelay;
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }

    public void setScheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public int getConcurrentTasks() {
        return concurrentTasks;
    }

    public void setConcurrentTasks(int concurrentTasks) {
        this.concurrentTasks = concurrentTasks;
    }

    @Override
    public void onInit(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void scheduleTask(Runnable task) {
        this.task = task;
    }

    @Override
    public void unscheduleTask() {
        if (isSchedulerStarted()) {
            for (ScheduledFuture<?> future : futures) {
                future.cancel(true);
            }
            futures.clear();
        }
    }

    @Override
    public void startScheduler() {
        // only schedule task if we have not already done that
        if (futures.size() == 0) {
            if (isUseFixedDelay()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling poll (fixed delay) with initialDelay: {}, delay: {} ({}) for: {}",
                            new Object[]{getInitialDelay(), getDelay(), getTimeUnit().name().toLowerCase(Locale.ENGLISH), consumer.getEndpoint()});
                }
                for (int i = 0; i < concurrentTasks; i++) {
                    futures.add(scheduledExecutorService.scheduleWithFixedDelay(task, getInitialDelay(), getDelay(), getTimeUnit()));
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling poll (fixed rate) with initialDelay: {}, delay: {} ({}) for: {}",
                            new Object[]{getInitialDelay(), getDelay(), getTimeUnit().name().toLowerCase(Locale.ENGLISH), consumer.getEndpoint()});
                }
                for (int i = 0; i < concurrentTasks; i++) {
                    futures.add(scheduledExecutorService.scheduleAtFixedRate(task, getInitialDelay(), getDelay(), getTimeUnit()));
                }
            }
        }
    }

    @Override
    public boolean isSchedulerStarted() {
        return futures != null && futures.size() > 0;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(consumer, "Consumer", this);
        ObjectHelper.notNull(camelContext, "CamelContext", this);
        ObjectHelper.notNull(task, "Task", this);

        // if no existing executor provided, then create a new thread pool ourselves
        if (scheduledExecutorService == null) {
            // we only need one thread in the pool to schedule this task
            this.scheduledExecutorService = getCamelContext().getExecutorServiceManager()
                    .newScheduledThreadPool(consumer, consumer.getEndpoint().getEndpointUri(), concurrentTasks);
            // and we should shutdown the thread pool when no longer needed
            this.shutdownExecutor = true;
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (isSchedulerStarted()) {
            LOG.debug("This consumer is stopping, so cancelling scheduled task: {}", futures);
            for (ScheduledFuture<?> future : futures) {
                future.cancel(true);
            }
            futures.clear();
        }

        if (shutdownExecutor && scheduledExecutorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(scheduledExecutorService);
            scheduledExecutorService = null;
            futures.clear();
        }
    }

}
