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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A useful base class for any consumer which is polling based
 * 
 * @version $Revision$
 */
public abstract class ScheduledPollConsumer<E extends Exchange> extends DefaultConsumer<E> implements
    Runnable {
    private static final transient Log LOG = LogFactory.getLog(ScheduledPollConsumer.class);

    private final ScheduledExecutorService executor;
    private long initialDelay = 1000;
    private long delay = 500;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private boolean useFixedDelay;
    private ScheduledFuture<?> future;

    public ScheduledPollConsumer(DefaultEndpoint<E> endpoint, Processor processor) {
        this(endpoint, processor, endpoint.getExecutorService());
    }

    public ScheduledPollConsumer(Endpoint<E> endpoint, Processor processor, ScheduledExecutorService executor) {
        super(endpoint, processor);
        this.executor = executor;
        if (executor == null) {
            throw new IllegalArgumentException("A non null ScheduledExecutorService must be provided.");
        }
    }

    /**
     * Invoked whenever we should be polled
     */
    public void run() {
        LOG.debug("Starting to poll");
        try {
            poll();
        } catch (Exception e) {
            LOG.warn("Caught: " + e, e);
        }
    }

    // Properties
    // -------------------------------------------------------------------------
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

    // Implementation methods
    // -------------------------------------------------------------------------

    /**
     * The polling method which is invoked periodically to poll this consumer
     * 
     * @throws Exception
     */
    protected abstract void poll() throws Exception;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (isUseFixedDelay()) {
            future = executor.scheduleWithFixedDelay(this, getInitialDelay(), getDelay(), getTimeUnit());
        } else {
            future = executor.scheduleAtFixedRate(this, getInitialDelay(), getDelay(), getTimeUnit());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (future != null) {
            future.cancel(false);
        }
        super.doStop();
    }
}
