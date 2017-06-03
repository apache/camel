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
package org.apache.camel.support;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.StaticService;
import org.apache.camel.TimerListener;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link TimerListener} manager which triggers the
 * {@link org.apache.camel.TimerListener} listeners once every second.
 * <p/>
 * Also ensure when adding and remove listeners, that they are correctly removed to avoid
 * leaking memory.
 *
 * @see TimerListener
 * @see org.apache.camel.management.ManagedLoadTimer
 */
public class TimerListenerManager extends ServiceSupport implements Runnable, CamelContextAware, StaticService {

    private static final Logger LOG = LoggerFactory.getLogger(TimerListenerManager.class);
    private final Set<TimerListener> listeners = new LinkedHashSet<TimerListener>();
    private CamelContext camelContext;
    private ScheduledExecutorService executorService;
    private volatile ScheduledFuture<?> task;
    private long interval = 1000L;

    public TimerListenerManager() {
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Gets the interval in millis.
     * <p/>
     * The default interval is 1000 millis.
     *
     * @return interval in millis.
     */
    public long getInterval() {
        return interval;
    }

    /**
     * Sets the interval in millis.
     *
     * @param interval interval in millis.
     */
    public void setInterval(long interval) {
        this.interval = interval;
    }

    @Override
    public void run() {
        LOG.trace("Running scheduled TimerListener task");

        if (!isRunAllowed()) {
            LOG.debug("TimerListener task cannot run as its not allowed");
            return;
        }

        for (TimerListener listener : listeners) {
            try {
                LOG.trace("Invoking onTimer on {}", listener);
                listener.onTimer();
            } catch (Throwable e) {
                // ignore
                LOG.debug("Error occurred during onTimer for TimerListener: " + listener + ". This exception will be ignored.", e);
            }
        }
    }

    /**
     * Adds the listener.
     * <p/>
     * It may be important to implement {@link #equals(Object)} and {@link #hashCode()} for the listener
     * to ensure that we can remove the same listener again, when invoking remove.
     * 
     * @param listener listener
     */
    public void addTimerListener(TimerListener listener) {
        listeners.add(listener);
        LOG.debug("Added TimerListener: {}", listener);
    }

    /**
     * Removes the listener.
     * <p/>
     * It may be important to implement {@link #equals(Object)} and {@link #hashCode()} for the listener
     * to ensure that we can remove the same listener again, when invoking remove.
     *
     * @param listener listener.
     */
    public void removeTimerListener(TimerListener listener) {
        listeners.remove(listener);
        LOG.debug("Removed TimerListener: {}", listener);
    }

    @Override
    protected void doStart() throws Exception {
        ObjectHelper.notNull(camelContext, "camelContext", this);

        // create scheduled thread pool to trigger the task to run every interval
        executorService = camelContext.getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "ManagementLoadTask");
        task = executorService.scheduleAtFixedRate(this, interval, interval, TimeUnit.MILLISECONDS);
        LOG.debug("Started scheduled TimerListener task to run with interval {} ms", interval);
    }

    @Override
    protected void doStop() throws Exception {
        // executor service will be shutdown by CamelContext
        if (task != null) {
            task.cancel(true);
            task = null;
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        super.doShutdown();
        // shutdown thread pool when we are shutting down
        camelContext.getExecutorServiceManager().shutdownNow(executorService);
        executorService = null;
        listeners.clear();
    }
}

