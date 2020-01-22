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

import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.BatchConsumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.spi.ShutdownAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A useful base class for any consumer which is polling batch based
 */
public abstract class ScheduledBatchPollingConsumer extends ScheduledPollConsumer implements BatchConsumer, ShutdownAware {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledBatchPollingConsumer.class);

    protected volatile ShutdownRunningTask shutdownRunningTask;
    protected volatile int pendingExchanges;
    protected int maxMessagesPerPoll;

    public ScheduledBatchPollingConsumer(Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    public ScheduledBatchPollingConsumer(Endpoint endpoint, Processor processor, ScheduledExecutorService executor) {
        super(endpoint, processor, executor);
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        // store a reference what to do in case when shutting down and we have pending messages
        this.shutdownRunningTask = shutdownRunningTask;
        // do not defer shutdown
        return false;
    }

    @Override
    public int getPendingExchangesSize() {
        int answer;
        // only return the real pending size in case we are configured to complete all tasks
        if (ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask) {
            answer = pendingExchanges;
        } else {
            answer = 0;
        }

        if (answer == 0 && isPolling()) {
            // force at least one pending exchange if we are polling as there is a little gap
            // in the processBatch method and until an exchange gets enlisted as in-flight
            // which happens later, so we need to signal back to the shutdown strategy that
            // there is a pending exchange. When we are no longer polling, then we will return 0
            LOG.trace("Currently polling so returning 1 as pending exchanges");
            answer = 1;
        }

        return answer;
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // reset task as the state of the task is not to be preserved
        // which otherwise may cause isBatchAllowed() to return a wrong answer
        this.shutdownRunningTask = null;
    }

    @Override
    public void setMaxMessagesPerPoll(int maxMessagesPerPoll) {
        this.maxMessagesPerPoll = maxMessagesPerPoll;
    }

    /**
     * Gets the maximum number of messages as a limit to poll at each polling.
     * <p/>
     * Is default unlimited, but use 0 or negative number to disable it as unlimited.
     *
     * @return max messages to poll
     */
    public int getMaxMessagesPerPoll() {
        return maxMessagesPerPoll;
    }

    @Override
    public boolean isBatchAllowed() {
        // stop if we are not running
        boolean answer = isRunAllowed();
        if (!answer) {
            return false;
        }

        if (shutdownRunningTask == null) {
            // we are not shutting down so continue to run
            return true;
        }

        // we are shutting down so only continue if we are configured to complete all tasks
        return ShutdownRunningTask.CompleteAllTasks == shutdownRunningTask;
    }

    @Override
    protected void processEmptyMessage() throws Exception {
        Exchange exchange = getEndpoint().createExchange();
        // enrich exchange, so we send an empty message with the batch details
        exchange.setProperty(Exchange.BATCH_INDEX, 0);
        exchange.setProperty(Exchange.BATCH_SIZE, 1);
        exchange.setProperty(Exchange.BATCH_COMPLETE, true);
        LOG.debug("Sending empty message as there were no messages from polling: {}", this.getEndpoint());
        getProcessor().process(exchange);
    }
}
