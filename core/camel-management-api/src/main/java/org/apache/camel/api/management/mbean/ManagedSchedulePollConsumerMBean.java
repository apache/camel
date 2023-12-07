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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;

public interface ManagedSchedulePollConsumerMBean extends ManagedConsumerMBean {

    @ManagedAttribute(description = "Scheduled Delay")
    long getDelay();

    @ManagedAttribute(description = "Scheduled Delay")
    void setDelay(long delay);

    @ManagedAttribute(description = "Scheduled Initial Delay")
    long getInitialDelay();

    @ManagedAttribute(description = "Scheduled Initial Delay")
    void setInitialDelay(long initialDelay);

    @ManagedAttribute(description = "Scheduled Fixed Delay")
    boolean isUseFixedDelay();

    @ManagedAttribute(description = "Scheduled Fixed Delay")
    void setUseFixedDelay(boolean useFixedDelay);

    @ManagedAttribute(description = "Scheduled Greedy")
    boolean isGreedy();

    @ManagedAttribute(description = "Scheduled Greedy")
    void setGreedy(boolean greedy);

    @ManagedAttribute(description = "If the polling consumer did not poll any files, you can enable this option to send an empty message (no body) instead")
    boolean isSendEmptyMessageWhenIdle();

    @ManagedAttribute(description = "If the polling consumer did not poll any files, you can enable this option to send an empty message (no body) instead")
    void setSendEmptyMessageWhenIdle(boolean sendEmptyMessageWhenIdle);

    @ManagedAttribute(description = "The consumer logs a start/complete log line when it polls. This option allows you to configure the logging level for that.")
    String getRunningLoggingLevel();

    @ManagedAttribute(description = "The consumer logs a start/complete log line when it polls. This option allows you to configure the logging level for that.")
    void setRunningLoggingLevel(String runningLoggingLevel);

    @ManagedAttribute(description = "Scheduled TimeUnit")
    String getTimeUnit();

    @ManagedAttribute(description = "Scheduled TimeUnit")
    void setTimeUnit(String timeUnit);

    @ManagedAttribute(description = "Is the scheduler currently polling")
    boolean isPolling();

    @ManagedAttribute(description = "Is the scheduler started")
    boolean isSchedulerStarted();

    @ManagedOperation(description = "Starts the scheduler")
    void startScheduler();

    @ManagedAttribute(description = "Scheduler classname")
    String getSchedulerClassName();

    @ManagedAttribute(description = "Backoff multiplier")
    int getBackoffMultiplier();

    @ManagedAttribute(description = "Backoff idle threshold")
    int getBackoffIdleThreshold();

    @ManagedAttribute(description = "Backoff error threshold")
    int getBackoffErrorThreshold();

    @ManagedAttribute(description = "Current backoff counter")
    int getBackoffCounter();

    @ManagedAttribute(description = "Repeat count")
    long getRepeatCount();

    @ManagedAttribute(description = "Whether a first pool attempt has been done (also if the consumer has been restarted)")
    boolean isFirstPollDone();

    @ManagedAttribute(description = "Whether the consumer is ready to handle incoming traffic (used for readiness health-check)")
    boolean isConsumerReady();

    @ManagedAttribute(description = "Total number of polls run")
    long getCounter();

    @ManagedAttribute(description = "Error counter. If the counter is > 0 that means the consumer failed polling for the last N number of times."
                                    + " When the consumer is successfully again, then the error counter resets to zero.")
    long getErrorCounter();

    @ManagedAttribute(description = "Success counter. If the success is > 0 that means the consumer succeeded polling for the last N number of times."
                                    + " When the consumer is failing again, then the success counter resets to zero.")
    long getSuccessCounter();

}
