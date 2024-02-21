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
 * The default scheduler for {@link ScheduledPollConsumer}.
 */
public class DefaultScheduledPollConsumerScheduler extends ServiceSupport implements ScheduledPollConsumerScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultScheduledPollConsumerScheduler.class);

    private static final int DEFAULT_INITIAL_DELAY = 1000;
    private static final int DEFAULT_DELAY = 500;

    private CamelContext camelContext;
    private Consumer consumer;
    private ScheduledExecutorService scheduledExecutorService;
    private boolean shutdownExecutor;
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private Runnable task;
    private int poolSize = 1;
    private int concurrentConsumers = 1;

    private long initialDelay = -1;
    private long delay = -1;
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

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
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

        // convert initial and delay according to whether they have been set or need to use their default value
        long currentInitialDelay;
        if (initialDelay < 0) {
            // compute the default initial delay that are millis to use current time unit
            currentInitialDelay = timeUnit.convert(DEFAULT_INITIAL_DELAY, TimeUnit.MILLISECONDS);
        } else {
            currentInitialDelay = initialDelay;
        }
        long currentDelay;
        if (delay <= 0) {
            // compute the default delay that are millis to use current time unit
            currentDelay = timeUnit.convert(DEFAULT_DELAY, TimeUnit.MILLISECONDS);
            if (currentDelay <= 0) {
                // delay must be at least 1
                currentDelay = 1;
            }
        } else {
            currentDelay = delay;
        }

        if (futures.isEmpty()) {
            if (isUseFixedDelay()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling {} consumers poll (fixed delay) with initialDelay: {}, delay: {} ({}) for: {}",
                            concurrentConsumers, currentInitialDelay, currentDelay,
                            getTimeUnit().name().toLowerCase(Locale.ENGLISH),
                            consumer.getEndpoint());
                }
                for (int i = 0; i < concurrentConsumers; i++) {
                    futures.add(scheduledExecutorService.scheduleWithFixedDelay(task, currentInitialDelay, currentDelay,
                            getTimeUnit()));
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scheduling {} consumers poll (fixed rate) with initialDelay: {}, delay: {} ({}) for: {}",
                            concurrentConsumers, currentInitialDelay, currentDelay,
                            getTimeUnit().name().toLowerCase(Locale.ENGLISH),
                            consumer.getEndpoint());
                }
                for (int i = 0; i < concurrentConsumers; i++) {
                    futures.add(scheduledExecutorService.scheduleAtFixedRate(task, currentInitialDelay, currentDelay,
                            getTimeUnit()));
                }
            }
        }
    }

    @Override
    public boolean isSchedulerStarted() {
        return futures != null && !futures.isEmpty();
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
                    .newScheduledThreadPool(consumer, consumer.getEndpoint().getEndpointUri(), poolSize);
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
