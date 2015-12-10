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
package org.apache.camel.spring.pollingconsumer;

import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.NonManagedService;
import org.apache.camel.spi.ScheduledPollConsumerScheduler;
import org.apache.camel.spring.util.CamelThreadPoolTaskScheduler;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/**
 * A Spring based {@link ScheduledPollConsumerScheduler} which uses a {@link CronTrigger} to define when the
 * poll should be triggered.
 */
public class SpringScheduledPollConsumerScheduler extends ServiceSupport implements ScheduledPollConsumerScheduler, NonManagedService {

    private static final Logger LOG = LoggerFactory.getLogger(SpringScheduledPollConsumerScheduler.class);
    private CamelContext camelContext;
    private Consumer consumer;
    private Runnable runnable;
    private String cron;
    private TimeZone timeZone = TimeZone.getDefault();
    private volatile CronTrigger trigger;
    private volatile ThreadPoolTaskScheduler taskScheduler;
    private boolean destroyTaskScheduler;
    private volatile ScheduledFuture<?> future;

    @Override
    public void onInit(Consumer consumer) {
        this.consumer = consumer;
    }

    @Override
    public void scheduleTask(Runnable runnable) {
        this.runnable = runnable;
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
        // we start the scheduler in doStart
    }

    @Override
    public boolean isSchedulerStarted() {
        return taskScheduler != null && !taskScheduler.getScheduledExecutor().isShutdown();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public ThreadPoolTaskScheduler getTaskScheduler() {
        return taskScheduler;
    }

    public void setTaskScheduler(ThreadPoolTaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notEmpty(cron, "cron", this);

        trigger = new CronTrigger(getCron(), getTimeZone());

        if (taskScheduler == null) {
            taskScheduler = new CamelThreadPoolTaskScheduler(getCamelContext(), consumer, consumer.getEndpoint().getEndpointUri());
            taskScheduler.afterPropertiesSet();
            destroyTaskScheduler = true;
        }

        LOG.debug("Scheduling cron trigger {}", getCron());
        future = taskScheduler.schedule(runnable, trigger);
    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            future.cancel(false);
            future = null;
        }

        if (destroyTaskScheduler) {
            taskScheduler.destroy();
            taskScheduler = null;
        }
    }
}
