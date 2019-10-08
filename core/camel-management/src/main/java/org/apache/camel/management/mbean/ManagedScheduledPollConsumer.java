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
package org.apache.camel.management.mbean;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedSchedulePollConsumerMBean;
import org.apache.camel.support.ScheduledPollConsumer;

@ManagedResource(description = "Managed Scheduled Polling Consumer")
public class ManagedScheduledPollConsumer extends ManagedConsumer implements ManagedSchedulePollConsumerMBean {
    private final ScheduledPollConsumer consumer;

    public ManagedScheduledPollConsumer(CamelContext context, ScheduledPollConsumer consumer) {
        super(context, consumer);
        this.consumer = consumer;
    }

    @Override
    public ScheduledPollConsumer getConsumer() {
        return consumer;
    }

    @Override
    public long getDelay() {
        return getConsumer().getDelay();
    }

    @Override
    public void setDelay(long delay) {
        getConsumer().setDelay(delay);
    }

    @Override
    public long getInitialDelay() {
        return getConsumer().getInitialDelay();
    }

    @Override
    public void setInitialDelay(long initialDelay) {
        getConsumer().setInitialDelay(initialDelay);
    }

    @Override
    public boolean isUseFixedDelay() {
        return getConsumer().isUseFixedDelay();
    }

    @Override
    public void setUseFixedDelay(boolean useFixedDelay) {
        getConsumer().setUseFixedDelay(useFixedDelay);
    }

    @Override
    public String getTimeUnit() {
        return getConsumer().getTimeUnit().name();
    }

    @Override
    public void setTimeUnit(String timeUnit) {
        getConsumer().setTimeUnit(TimeUnit.valueOf(timeUnit));
    }

    @Override
    public boolean isPolling() {
        return getConsumer().isPolling();
    }

    @Override
    public boolean isSchedulerStarted() {
        return getConsumer().isSchedulerStarted();
    }

    @Override
    public void startScheduler() {
        getConsumer().startScheduler();
    }

    @Override
    public String getSchedulerClassName() {
        return getConsumer().getScheduler().getClass().getName();
    }

    @Override
    public int getBackoffMultiplier() {
        return getConsumer().getBackoffMultiplier();
    }

    @Override
    public int getBackoffIdleThreshold() {
        return getConsumer().getBackoffIdleThreshold();
    }

    @Override
    public int getBackoffErrorThreshold() {
        return getConsumer().getBackoffErrorThreshold();
    }

    @Override
    public int getBackoffCounter() {
        return getConsumer().getBackoffCounter();
    }

    @Override
    public long getRepeatCount() {
        return getConsumer().getRepeatCount();
    }
}
