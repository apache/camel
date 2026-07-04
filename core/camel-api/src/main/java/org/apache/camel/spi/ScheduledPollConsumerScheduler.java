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
package org.apache.camel.spi;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Consumer;
import org.apache.camel.ShutdownableService;

/**
 * Pluggable scheduler that controls when a {@link org.apache.camel.support.ScheduledPollConsumer} wakes up to poll its
 * source.
 * <p/>
 * Polling consumers (file, FTP, mail, etc.) delegate all scheduling decisions to this SPI so that the polling trigger
 * can be replaced without changing the consumer itself. The default implementation uses a
 * {@link java.util.concurrent.ScheduledExecutorService} with a fixed or initial delay, while the {@code camel-quartz}
 * component provides an alternative that accepts CRON expressions for time-based firing.
 * <p/>
 * A scheduler is initialized via {@link #onInit(org.apache.camel.Consumer)} and then activated by calling
 * {@link #startScheduler()}, which begins firing the task registered with {@link #scheduleTask(Runnable)}. The consumer
 * task itself is responsible for calling the consumer's polling logic; the scheduler only controls the firing cadence.
 * <p/>
 * See <a href="https://camel.apache.org/manual/polling-consumer.html">Polling Consumer</a> in the Camel user manual.
 *
 * @see ExecutorServiceManager
 */
public interface ScheduledPollConsumerScheduler extends ShutdownableService, CamelContextAware {

    /**
     * Initializes this {@link ScheduledPollConsumerScheduler} with the associated {@link Consumer}.
     *
     * @param consumer the consumer.
     */
    void onInit(Consumer consumer);

    /**
     * Schedules the task to run.
     *
     * @param task the task to run.
     */
    void scheduleTask(Runnable task);

    /**
     * Attempts to unschedules the last task which was scheduled.
     * <p/>
     * An implementation may not implement this method.
     */
    void unscheduleTask();

    /**
     * Starts the scheduler.
     * <p/>
     * If the scheduler is already started, then this is a noop method call.
     */
    void startScheduler();

    /**
     * Whether the scheduler has been started.
     *
     * @return <tt>true</tt> if started, <tt>false</tt> otherwise.
     */
    boolean isSchedulerStarted();

}
