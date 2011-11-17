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
package org.apache.camel.management.mbean;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

/**
 * @version 
 */
@ManagedResource(description = "Managed Scheduled Polling Consumer")
public class ManagedScheduledPollConsumer extends ManagedConsumer {
    private final ScheduledPollConsumer consumer;

    public ManagedScheduledPollConsumer(CamelContext context, ScheduledPollConsumer consumer) {
        super(context, consumer);
        this.consumer = consumer;
    }

    public ScheduledPollConsumer getConsumer() {
        return consumer;
    }

    @ManagedAttribute(description = "Scheduled Delay")
    public long getDelay() {
        return getConsumer().getDelay();
    }

    @ManagedAttribute(description = "Scheduled Delay")
    public void setDelay(long delay) {
        getConsumer().setDelay(delay);
    }

    @ManagedAttribute(description = "Scheduled Initial Delay")
    public long getInitialDelay() {
        return getConsumer().getInitialDelay();
    }

    @ManagedAttribute(description = "Scheduled Initial Delay")
    public void setInitialDelay(long initialDelay) {
        getConsumer().setInitialDelay(initialDelay);
    }

    @ManagedAttribute(description = "Scheduled Fixed Delay")
    public boolean isUseFixedDelay() {
        return getConsumer().isUseFixedDelay();
    }

    @ManagedAttribute(description = "Scheduled Fixed Delay")
    public void setUseFixedDelay(boolean useFixedDelay) {
        getConsumer().setUseFixedDelay(useFixedDelay);
    }

    @ManagedAttribute(description = "Scheduled TimeUnit")
    public String getTimeUnit() {
        return getConsumer().getTimeUnit().name();
    }

    @ManagedAttribute(description = "Scheduled TimeUnit")
    public void setTimeUnit(String timeUnit) {
        getConsumer().setTimeUnit(TimeUnit.valueOf(timeUnit));
    }
}
